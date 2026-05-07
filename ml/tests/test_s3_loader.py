from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError
from unittest.mock import MagicMock, patch

from app.pipeline.s3_loader import download_photos


def _make_s3_client(responses: dict[str, bytes | Exception]) -> MagicMock:
    """Build a mock S3 client whose get_object returns or raises per photo_id."""

    def get_object(Bucket, Key):
        result = responses[Key]
        if isinstance(result, Exception):
            raise result
        body = MagicMock()
        body.read.return_value = result
        return {"Body": body}

    client = MagicMock()
    client.get_object.side_effect = get_object
    return client


def test_all_downloads_succeed():
    data = {"p1": b"bytes1", "p2": b"bytes2", "p3": b"bytes3"}
    client = _make_s3_client(data)
    result = download_photos(list(data.keys()), "my-bucket", client)
    assert result == data


def test_partial_failure_skips_failed_photo():
    responses = {
        "p1": b"ok",
        "p2": Exception("access denied"),
        "p3": b"also ok",
    }
    client = _make_s3_client(responses)
    result = download_photos(["p1", "p2", "p3"], "bucket", client)
    assert set(result.keys()) == {"p1", "p3"}
    assert result["p1"] == b"ok"
    assert result["p3"] == b"also ok"


def test_all_downloads_fail_returns_empty():
    responses = {
        "p1": Exception("error"),
        "p2": Exception("error"),
    }
    client = _make_s3_client(responses)
    result = download_photos(["p1", "p2"], "bucket", client)
    assert result == {}


def test_empty_photo_ids_returns_empty():
    client = MagicMock()
    result = download_photos([], "bucket", client)
    assert result == {}
    client.get_object.assert_not_called()


def test_correct_bucket_is_used():
    client = _make_s3_client({"photo_key": b"data"})
    download_photos(["photo_key"], "correct-bucket", client)
    client.get_object.assert_called_once_with(Bucket="correct-bucket", Key="photo_key")


def test_single_photo_success():
    client = _make_s3_client({"only": b"single"})
    result = download_photos(["only"], "b", client)
    assert result == {"only": b"single"}


def _make_mock_executor(future_results: dict[str, object]):
    """Build a mock ThreadPoolExecutor whose futures return or raise per photo_id."""

    def submit_side_effect(fn, pid):
        mock_future = MagicMock()
        outcome = future_results[pid]
        if isinstance(outcome, BaseException):
            mock_future.result.side_effect = outcome
        else:
            mock_future.result.return_value = outcome
        return mock_future

    executor = MagicMock()
    executor.__enter__ = MagicMock(return_value=executor)
    executor.__exit__ = MagicMock(return_value=False)
    executor.submit.side_effect = submit_side_effect
    return executor


def test_timeout_skips_slow_photo():
    """A photo that exceeds timeout is skipped; others are returned."""
    future_results = {
        "p1": ("p1", b"ok"),
        "p2": FuturesTimeoutError(),
        "p3": ("p3", b"also ok"),
    }
    mock_executor = _make_mock_executor(future_results)
    client = MagicMock()

    with patch("app.pipeline.s3_loader.ThreadPoolExecutor", return_value=mock_executor):
        result = download_photos(["p1", "p2", "p3"], "bucket", client, timeout=1.0)

    assert result == {"p1": b"ok", "p3": b"also ok"}


def test_all_timeout_returns_empty():
    """All photos timing out returns an empty dict."""
    future_results = {
        "p1": FuturesTimeoutError(),
        "p2": FuturesTimeoutError(),
    }
    mock_executor = _make_mock_executor(future_results)
    client = MagicMock()

    with patch("app.pipeline.s3_loader.ThreadPoolExecutor", return_value=mock_executor):
        result = download_photos(["p1", "p2"], "bucket", client, timeout=1.0)

    assert result == {}


def test_custom_timeout_passed_to_future():
    """The timeout value is forwarded to future.result."""
    captured_futures: list[MagicMock] = []

    def submit_side_effect(fn, pid):
        mock_future = MagicMock()
        mock_future.result.return_value = (pid, b"data")
        captured_futures.append(mock_future)
        return mock_future

    executor = MagicMock()
    executor.__enter__ = MagicMock(return_value=executor)
    executor.__exit__ = MagicMock(return_value=False)
    executor.submit.side_effect = submit_side_effect
    client = MagicMock()

    with patch("app.pipeline.s3_loader.ThreadPoolExecutor", return_value=executor):
        download_photos(["p1"], "bucket", client, timeout=5.0)

    assert len(captured_futures) == 1
    captured_futures[0].result.assert_called_once_with(timeout=5.0)


# ---------------------------------------------------------------------------
# Shared executor (Win 5) — caller may supply a long-lived ThreadPoolExecutor
# ---------------------------------------------------------------------------

def test_uses_provided_executor_without_constructing_new_one():
    """When a caller supplies an executor, no new ThreadPoolExecutor is built."""
    client = _make_s3_client({"p1": b"data"})
    shared = ThreadPoolExecutor(max_workers=2)
    try:
        with patch("app.pipeline.s3_loader.ThreadPoolExecutor") as mock_ctor:
            result = download_photos(["p1"], "bucket", client, executor=shared)
        assert result == {"p1": b"data"}
        mock_ctor.assert_not_called()
    finally:
        shared.shutdown(wait=True)


def test_provided_executor_is_not_shutdown():
    """Caller-owned executor must outlive the call (no implicit shutdown)."""
    client = _make_s3_client({"p1": b"data1", "p2": b"data2"})
    shared = ThreadPoolExecutor(max_workers=2)
    try:
        download_photos(["p1", "p2"], "bucket", client, executor=shared)
        # Submitting again should still work — shared pool wasn't shutdown
        future = shared.submit(lambda: 42)
        assert future.result(timeout=1.0) == 42
    finally:
        shared.shutdown(wait=True)


def test_no_executor_falls_back_to_internal_pool():
    """Backwards compatibility: omitting executor still works."""
    client = _make_s3_client({"p1": b"x"})
    result = download_photos(["p1"], "bucket", client)
    assert result == {"p1": b"x"}
