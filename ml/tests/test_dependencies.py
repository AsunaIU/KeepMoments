"""Tests for dependency injection helpers.

Win 5: S3 client and ThreadPoolExecutor must be created once at startup
(stored on app.state) and reused across requests rather than rebuilt per call.
"""
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import MagicMock

from app.dependencies import (
    get_clip_model_dep,
    get_download_executor,
    get_s3_client,
)


def _fake_request_with_state(**state_attrs):
    """Build a fake FastAPI Request whose .app.state has the given attrs."""
    state = MagicMock(spec=list(state_attrs.keys()))
    for k, v in state_attrs.items():
        setattr(state, k, v)
    request = MagicMock()
    request.app.state = state
    return request


def test_get_s3_client_returns_app_state_singleton():
    fake_client = MagicMock(name="boto3-client")
    request = _fake_request_with_state(s3_client=fake_client)
    assert get_s3_client(request) is fake_client


def test_get_s3_client_returns_same_instance_across_calls():
    fake_client = MagicMock()
    request = _fake_request_with_state(s3_client=fake_client)
    assert get_s3_client(request) is get_s3_client(request)


def test_get_download_executor_returns_app_state_singleton():
    pool = ThreadPoolExecutor(max_workers=1)
    try:
        request = _fake_request_with_state(download_executor=pool)
        assert get_download_executor(request) is pool
    finally:
        pool.shutdown(wait=True)


def test_get_clip_model_dep_unchanged():
    """Existing CLIP DI behaviour must not regress."""
    model, preprocess = MagicMock(), MagicMock()
    request = _fake_request_with_state(clip_model=model, clip_preprocess=preprocess)
    m, p = get_clip_model_dep(request)
    assert m is model and p is preprocess
