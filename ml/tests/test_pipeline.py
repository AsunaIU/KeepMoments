from contextlib import ExitStack
from unittest.mock import MagicMock, patch

import numpy as np
import pytest
from fastapi import HTTPException

from app.pipeline import _run_pipeline_sync, run_pipeline
from app.schemas import FilledPage, FilledTemplate
from tests.conftest import make_process_request


# ---------------------------------------------------------------------------
# Shared data
# ---------------------------------------------------------------------------

_PHOTO_BYTES = {"p1": b"bytes1", "p2": b"bytes2"}
_EMBEDDINGS = {k: np.zeros(512, dtype=np.float32) for k in _PHOTO_BYTES}
_FILLED = FilledTemplate(id="template_1", pages=[FilledPage(id="page_0", slots=[])])


def _make_settings():
    return MagicMock(
        AWS_REGION="us-east-1",
        AWS_ACCESS_KEY_ID="k",
        AWS_SECRET_ACCESS_KEY="s",
        S3_BUCKET_NAME="b",
        CLIP_MODEL_NAME="ViT-B/32",
        KMEANS_RANDOM_STATE=42,
    )


def _pipeline_patch_stack(overrides: dict | None = None):
    """
    Return (ExitStack, mocks_dict) with all pipeline deps mocked.
    The stack must be used as a context manager to ensure cleanup.
    """
    defaults = {
        "app.pipeline.download_photos": MagicMock(return_value=_PHOTO_BYTES),
        "app.pipeline.extract_embeddings": MagicMock(return_value=_EMBEDDINGS),
        "app.pipeline.cluster_photos": MagicMock(return_value={0: list(_PHOTO_BYTES)}),
        "app.pipeline.score_quality": MagicMock(return_value={"p1": 0.8, "p2": 0.6}),
        "app.pipeline.select_photos": MagicMock(return_value=list(_PHOTO_BYTES)),
        "app.pipeline.rerank_by_text": MagicMock(return_value=list(_PHOTO_BYTES)),
        "app.pipeline.count_template_slots": MagicMock(return_value=2),
        "app.pipeline.fill_template": MagicMock(return_value=_FILLED),
    }
    if overrides:
        defaults.update(overrides)

    stack = ExitStack()
    mocks = {}
    for target, new_val in defaults.items():
        attr = target.split(".")[-1]
        mocks[attr] = stack.enter_context(patch(target, new_val))
    return stack, mocks


# ---------------------------------------------------------------------------
# _run_pipeline_sync tests
# ---------------------------------------------------------------------------

def test_happy_path_returns_filled_template():
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=1, max_photos=5)
    stack, _ = _pipeline_patch_stack()
    with stack:
        result = _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    assert result is _FILLED


def test_no_photos_downloaded_raises_503():
    request = make_process_request(min_photos=1)
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["download_photos"].return_value = {}
        with pytest.raises(HTTPException) as exc_info:
            _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    assert exc_info.value.status_code == 503


def test_fewer_photos_than_min_raises_422():
    # Only 2 photos available but min_photos=5
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=5, max_photos=10)
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["download_photos"].return_value = {"p1": b"x", "p2": b"y"}
        with pytest.raises(HTTPException) as exc_info:
            _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    assert exc_info.value.status_code == 422
    assert "min_photos" in exc_info.value.detail


def test_no_template_slots_raises_422():
    request = make_process_request(min_photos=1)
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["count_template_slots"].return_value = 0
        with pytest.raises(HTTPException) as exc_info:
            _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    assert exc_info.value.status_code == 422


def test_n_select_capped_at_available_photos():
    """n_select = min(max(min, min(slots, max)), available)."""
    # min=1, max=100, slots=50, available=3  → n_select = min(50, 3) = 3
    available = {"p1": b"x", "p2": b"y", "p3": b"z"}
    request = make_process_request(
        photo_ids=list(available), min_photos=1, max_photos=100, slots_per_page=50
    )
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["download_photos"].return_value = available
        mocks["extract_embeddings"].return_value = {k: np.zeros(512) for k in available}
        mocks["cluster_photos"].return_value = {0: list(available)}
        mocks["count_template_slots"].return_value = 50
        _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
        pos_args, _ = mocks["select_photos"].call_args
        n_select_used = pos_args[2]
    assert n_select_used == 3


def test_fill_template_receives_reranked_photos():
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=1, max_photos=5)
    reranked = ["p2", "p1"]
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["rerank_by_text"].return_value = reranked
        _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
        pos_args, _ = mocks["fill_template"].call_args
    assert pos_args[1] == reranked


def test_download_called_with_correct_bucket():
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=1, max_photos=5)
    settings = _make_settings()
    settings.S3_BUCKET_NAME = "expected-bucket"
    stack, mocks = _pipeline_patch_stack()
    with stack:
        _run_pipeline_sync(request, settings, MagicMock(), MagicMock(), MagicMock())
        _, call_kwargs = mocks["download_photos"].call_args
        bucket_used = call_kwargs.get("bucket") or mocks["download_photos"].call_args[0][1]
    assert bucket_used == "expected-bucket"


# ---------------------------------------------------------------------------
# run_pipeline async wrapper
# ---------------------------------------------------------------------------

async def test_run_pipeline_async_wrapper(mock_settings):
    request = make_process_request()
    mock_s3 = MagicMock()
    mock_model = MagicMock()
    mock_preprocess = MagicMock()
    with patch("app.pipeline._run_pipeline_sync", return_value=_FILLED) as mock_sync:
        result = await run_pipeline(request, mock_settings, mock_s3, mock_model, mock_preprocess)
    assert result is _FILLED
    mock_sync.assert_called_once_with(request, mock_settings, mock_s3, mock_model, mock_preprocess)
