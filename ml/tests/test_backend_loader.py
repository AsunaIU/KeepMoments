import httpx
import pytest

from app.pipeline.backend_loader import download_photos_from_backend


_BASE_URL = "http://backend.test"


def _make_mock_client(
    responses: dict[str, bytes | int | Exception],
    captured: list[httpx.Request] | None = None,
) -> httpx.AsyncClient:
    """Build an AsyncClient backed by MockTransport.

    `responses` maps photo_id → bytes (200 OK), int (status code), or Exception (raised).
    `captured` (optional) appends every incoming request for URL/headers inspection.
    """

    def handler(request: httpx.Request) -> httpx.Response:
        if captured is not None:
            captured.append(request)
        # URL form: {base}/api/v1/photos/{id}/file
        path = request.url.path
        prefix = "/api/v1/photos/"
        suffix = "/file"
        assert path.startswith(prefix) and path.endswith(suffix), (
            f"unexpected path: {path}"
        )
        photo_id = path[len(prefix) : -len(suffix)]
        result = responses[photo_id]
        if isinstance(result, Exception):
            raise result
        if isinstance(result, int):
            return httpx.Response(result)
        return httpx.Response(200, content=result)

    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


# ---------------------------------------------------------------------------
# Happy paths
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_all_downloads_succeed():
    data = {"p1": b"bytes1", "p2": b"bytes2", "p3": b"bytes3"}
    client = _make_mock_client(data)
    try:
        result = await download_photos_from_backend(list(data.keys()), _BASE_URL, client=client)
    finally:
        await client.aclose()
    assert result == data


@pytest.mark.asyncio
async def test_empty_photo_ids_returns_empty():
    captured: list[httpx.Request] = []
    client = _make_mock_client({}, captured=captured)
    try:
        result = await download_photos_from_backend([], _BASE_URL, client=client)
    finally:
        await client.aclose()
    assert result == {}
    assert captured == []


# ---------------------------------------------------------------------------
# Failure handling
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_partial_failure_skips_failed_photo():
    responses = {
        "p1": b"ok",
        "p2": 404,
        "p3": 500,
        "p4": b"also ok",
    }
    client = _make_mock_client(responses)
    try:
        result = await download_photos_from_backend(
            ["p1", "p2", "p3", "p4"], _BASE_URL, client=client
        )
    finally:
        await client.aclose()
    assert set(result.keys()) == {"p1", "p4"}
    assert result["p1"] == b"ok"
    assert result["p4"] == b"also ok"


@pytest.mark.asyncio
async def test_network_error_is_skipped():
    responses = {
        "p1": b"ok",
        "p2": httpx.ConnectError("connection refused"),
        "p3": b"ok2",
    }
    client = _make_mock_client(responses)
    try:
        result = await download_photos_from_backend(
            ["p1", "p2", "p3"], _BASE_URL, client=client
        )
    finally:
        await client.aclose()
    assert result == {"p1": b"ok", "p3": b"ok2"}


@pytest.mark.asyncio
async def test_all_downloads_fail_returns_empty():
    responses = {"p1": 500, "p2": 500}
    client = _make_mock_client(responses)
    try:
        result = await download_photos_from_backend(["p1", "p2"], _BASE_URL, client=client)
    finally:
        await client.aclose()
    assert result == {}


@pytest.mark.asyncio
async def test_timeout_skips_slow_photo():
    responses = {
        "p1": b"ok",
        "p2": httpx.ReadTimeout("timed out"),
        "p3": b"also ok",
    }
    client = _make_mock_client(responses)
    try:
        result = await download_photos_from_backend(
            ["p1", "p2", "p3"], _BASE_URL, client=client, timeout=1.0
        )
    finally:
        await client.aclose()
    assert result == {"p1": b"ok", "p3": b"also ok"}


# ---------------------------------------------------------------------------
# URL construction
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_correct_url_is_used():
    captured: list[httpx.Request] = []
    client = _make_mock_client({"abc-123": b"x"}, captured=captured)
    try:
        await download_photos_from_backend(["abc-123"], _BASE_URL, client=client)
    finally:
        await client.aclose()
    assert len(captured) == 1
    assert str(captured[0].url) == f"{_BASE_URL}/api/v1/photos/abc-123/file"
    assert captured[0].method == "GET"


@pytest.mark.asyncio
async def test_trailing_slash_in_base_url_is_handled():
    captured: list[httpx.Request] = []
    client = _make_mock_client({"p1": b"x"}, captured=captured)
    try:
        await download_photos_from_backend(
            ["p1"], _BASE_URL + "/", client=client
        )
    finally:
        await client.aclose()
    assert str(captured[0].url) == f"{_BASE_URL}/api/v1/photos/p1/file"


# ---------------------------------------------------------------------------
# Client lifecycle
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_uses_provided_client_without_closing_it():
    """Caller-owned client must outlive the call (no implicit aclose)."""
    client = _make_mock_client({"p1": b"data1", "p2": b"data2"})
    try:
        result = await download_photos_from_backend(
            ["p1", "p2"], _BASE_URL, client=client
        )
        assert result == {"p1": b"data1", "p2": b"data2"}
        # Re-use must still work — client wasn't closed
        assert not client.is_closed
        result2 = await download_photos_from_backend(["p1"], _BASE_URL, client=client)
        assert result2 == {"p1": b"data1"}
    finally:
        await client.aclose()


@pytest.mark.asyncio
async def test_internal_client_is_closed_when_not_provided(monkeypatch):
    """When no client is passed, the internally created one is closed afterwards."""
    created: list[httpx.AsyncClient] = []
    real_async_client = httpx.AsyncClient

    transport = httpx.MockTransport(lambda req: httpx.Response(200, content=b"x"))

    def factory(*args, **kwargs):
        kwargs.setdefault("transport", transport)
        client = real_async_client(*args, **kwargs)
        created.append(client)
        return client

    monkeypatch.setattr(httpx, "AsyncClient", factory)

    result = await download_photos_from_backend(["p1"], _BASE_URL)
    assert result == {"p1": b"x"}
    assert len(created) == 1
    assert created[0].is_closed
