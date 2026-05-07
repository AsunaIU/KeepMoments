from contextlib import ExitStack
from unittest.mock import AsyncMock, MagicMock, patch

import numpy as np
import pytest
from fastapi import HTTPException

from app.pipeline import _PipelineState, _run_pipeline_sync, run_pipeline
from app.schemas import FilledPage, FilledTemplate
from tests.conftest import make_pil_image, make_process_request


# ---------------------------------------------------------------------------
# Shared data
# ---------------------------------------------------------------------------

_PHOTO_BYTES = {"p1": b"bytes1", "p2": b"bytes2"}
_IMAGES = {"p1": make_pil_image(), "p2": make_pil_image()}
_EMBEDDINGS = {k: np.zeros(512, dtype=np.float32) for k in _IMAGES}
_FILLED = FilledTemplate(id="template_1", pages=[FilledPage(id="page_0", slots=[])])


def _make_settings():
    return MagicMock(
        AWS_REGION="us-east-1",
        AWS_ACCESS_KEY_ID="k",
        AWS_SECRET_ACCESS_KEY="s",
        S3_BUCKET_NAME="b",
        CLIP_MODEL_NAME="ViT-B/32",
        KMEANS_RANDOM_STATE=42,
        ANTHROPIC_API_KEY=None,
        OPENROUTER_API_KEY=None,
    )


def _pipeline_patch_stack(overrides: dict | None = None):
    defaults = {
        "app.pipeline.download_photos": MagicMock(return_value=dict(_PHOTO_BYTES)),
        "app.pipeline.decode_images": MagicMock(return_value=dict(_IMAGES)),
        "app.pipeline.extract_embeddings": MagicMock(return_value=_EMBEDDINGS),
        "app.pipeline.cluster_photos": MagicMock(return_value={0: list(_IMAGES)}),
        "app.pipeline.score_quality": MagicMock(return_value={"p1": 0.8, "p2": 0.6}),
        "app.pipeline.select_photos": MagicMock(return_value=list(_IMAGES)),
        "app.pipeline.rerank_by_text": MagicMock(return_value=list(_IMAGES)),
        "app.pipeline.count_template_slots": MagicMock(return_value=2),
        "app.pipeline.fill_template": MagicMock(return_value=_FILLED),
        "app.pipeline.detect_orientation": MagicMock(return_value={}),
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

def test_happy_path_returns_pipeline_state():
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=1, max_photos=5)
    stack, _ = _pipeline_patch_stack()
    with stack:
        result = _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    assert isinstance(result, _PipelineState)
    assert result.filled is _FILLED


def test_no_photos_decoded_raises_503():
    request = make_process_request(min_photos=1)
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["decode_images"].return_value = {}
        with pytest.raises(HTTPException) as exc_info:
            _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    assert exc_info.value.status_code == 503


def test_fewer_photos_than_min_raises_422():
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=5, max_photos=10)
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["decode_images"].return_value = {"p1": make_pil_image(), "p2": make_pil_image()}
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
    available_imgs = {p: make_pil_image() for p in ["p1", "p2", "p3"]}
    request = make_process_request(
        photo_ids=list(available_imgs), min_photos=1, max_photos=100, slots_per_page=50
    )
    stack, mocks = _pipeline_patch_stack()
    with stack:
        mocks["decode_images"].return_value = available_imgs
        mocks["extract_embeddings"].return_value = {k: np.zeros(512) for k in available_imgs}
        mocks["cluster_photos"].return_value = {0: list(available_imgs)}
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
        args, kwargs = mocks["download_photos"].call_args
    bucket_used = args[1] if len(args) > 1 else kwargs.get("bucket")
    assert bucket_used == "expected-bucket"


def test_decode_images_called_with_downloaded_bytes():
    """decode_images should be called once with the dict produced by download_photos."""
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=1, max_photos=5)
    captured: dict = {}

    def capture_decode(photo_bytes):
        # Snapshot keys before pipeline clears the dict to free memory.
        captured["keys"] = set(photo_bytes.keys())
        return dict(_IMAGES)

    overrides = {"app.pipeline.decode_images": MagicMock(side_effect=capture_decode)}
    stack, _ = _pipeline_patch_stack(overrides)
    with stack:
        _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    assert captured["keys"] == set(_PHOTO_BYTES)


def test_detect_orientation_called_with_decoded_images():
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=1, max_photos=5)
    stack, mocks = _pipeline_patch_stack()
    with stack:
        _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    args, _ = mocks["detect_orientation"].call_args
    assert set(args[0].keys()) == set(_IMAGES.keys())


def test_fill_template_receives_orientations_kwarg():
    request = make_process_request(photo_ids=["p1", "p2"], min_photos=1, max_photos=5)
    fake_orientations = {"p1": "landscape", "p2": "portrait"}
    stack, mocks = _pipeline_patch_stack(
        {"app.pipeline.detect_orientation": MagicMock(return_value=fake_orientations)}
    )
    with stack:
        _run_pipeline_sync(request, _make_settings(), MagicMock(), MagicMock(), MagicMock())
    _, call_kwargs = mocks["fill_template"].call_args
    assert call_kwargs.get("orientations") == fake_orientations


# ---------------------------------------------------------------------------
# run_pipeline async wrapper
# ---------------------------------------------------------------------------

async def test_run_pipeline_async_wrapper(mock_settings):
    request = make_process_request()
    state = _PipelineState(images=_IMAGES, ranked=list(_IMAGES), filled=_FILLED)
    with patch("app.pipeline._run_pipeline_sync", return_value=state) as mock_sync:
        result = await run_pipeline(request, mock_settings, MagicMock(), MagicMock(), MagicMock())
    assert result is _FILLED
    mock_sync.assert_called_once()


async def test_run_pipeline_forwards_download_executor(mock_settings):
    request = make_process_request()
    state = _PipelineState(images=_IMAGES, ranked=list(_IMAGES), filled=_FILLED)
    fake_executor = MagicMock(name="executor")
    with patch("app.pipeline._run_pipeline_sync", return_value=state) as mock_sync:
        await run_pipeline(
            request, mock_settings, MagicMock(), MagicMock(), MagicMock(),
            download_executor=fake_executor,
        )
    args, _ = mock_sync.call_args
    assert args[-1] is fake_executor


async def test_run_pipeline_calls_generate_captions_when_api_key_set():
    settings = _make_settings()
    settings.ANTHROPIC_API_KEY = "test-key"
    settings.ANTHROPIC_MODEL = "claude-haiku-4-5-20251001"

    request = make_process_request()
    state = _PipelineState(images=_IMAGES, ranked=list(_IMAGES), filled=_FILLED)

    with patch("app.pipeline._run_pipeline_sync", return_value=state), \
         patch("app.pipeline.generate_captions", new_callable=AsyncMock, return_value=_FILLED) as mock_cap:
        await run_pipeline(request, settings, MagicMock(), MagicMock(), MagicMock())

    mock_cap.assert_awaited_once()
    kwargs = mock_cap.call_args.kwargs
    assert kwargs["api_key"] == "test-key"
    assert kwargs["base_url"] is None  # Anthropic backend


async def test_run_pipeline_skips_captions_without_api_key():
    settings = _make_settings()
    request = make_process_request()
    state = _PipelineState(images=_IMAGES, ranked=list(_IMAGES), filled=_FILLED)

    with patch("app.pipeline._run_pipeline_sync", return_value=state), \
         patch("app.pipeline.generate_captions", new_callable=AsyncMock) as mock_cap:
        await run_pipeline(request, settings, MagicMock(), MagicMock(), MagicMock())

    mock_cap.assert_not_called()


async def test_run_pipeline_openrouter_takes_priority_over_anthropic():
    settings = _make_settings()
    settings.ANTHROPIC_API_KEY = "anth"
    settings.ANTHROPIC_MODEL = "claude"
    settings.OPENROUTER_API_KEY = "ork"
    settings.OPENROUTER_MODEL = "gpt"

    request = make_process_request()
    state = _PipelineState(images=_IMAGES, ranked=list(_IMAGES), filled=_FILLED)

    with patch("app.pipeline._run_pipeline_sync", return_value=state), \
         patch("app.pipeline.generate_captions", new_callable=AsyncMock, return_value=_FILLED) as mock_cap:
        await run_pipeline(request, settings, MagicMock(), MagicMock(), MagicMock())

    kwargs = mock_cap.call_args.kwargs
    assert kwargs["api_key"] == "ork"
    assert kwargs["base_url"] is not None  # OpenRouter base_url


async def test_run_pipeline_calls_fill_covers_when_template_has_covers():
    from app.schemas import CoverConfig, CoverMode

    settings = _make_settings()
    request = make_process_request()
    request.template.front_cover = CoverConfig(mode=CoverMode.photo)

    state = _PipelineState(images=_IMAGES, ranked=list(_IMAGES), filled=_FILLED)

    with patch("app.pipeline._run_pipeline_sync", return_value=state), \
         patch("app.pipeline.fill_covers", new_callable=AsyncMock, return_value=_FILLED) as mock_cov:
        await run_pipeline(request, settings, MagicMock(), MagicMock(), MagicMock())

    mock_cov.assert_awaited_once()


async def test_run_pipeline_skips_fill_covers_when_no_covers():
    settings = _make_settings()
    request = make_process_request()
    state = _PipelineState(images=_IMAGES, ranked=list(_IMAGES), filled=_FILLED)

    with patch("app.pipeline._run_pipeline_sync", return_value=state), \
         patch("app.pipeline.fill_covers", new_callable=AsyncMock) as mock_cov:
        await run_pipeline(request, settings, MagicMock(), MagicMock(), MagicMock())

    mock_cov.assert_not_called()
