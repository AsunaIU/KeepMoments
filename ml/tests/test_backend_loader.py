from unittest.mock import AsyncMock

import httpx
import pytest

from app.pipeline.backend_loader import download_photos_from_backend


_BASE_URL = "http://backend.test"


def _make_mock_client(
    responses: dict[str, bytes | int | Exception | list],
    captured: list[httpx.Request] | None = None,
) -> httpx.AsyncClient:
    """Build an AsyncClient backed by MockTransport.

    `responses` maps photo_id → one of:
      - bytes  → single 200 OK with that body
      - int    → single response with that status
      - Exception → raised on every call
      - list[...] → sequenced per-call results (each entry: bytes | int | Exception)
    `captured` (optional) appends every incoming request for URL/headers inspection.
    """

    iterators: dict[str, object] = {}
    for pid, val in responses.items():
        if isinstance(val, list):
            iterators[pid] = iter(val)

    def handler(request: httpx.Request) -> httpx.Response:
        if captured is not None:
            captured.append(request)
        path = request.url.path
        prefix = "/api/v1/photos/"
        suffix = "/file"
        assert path.startswith(prefix) and path.endswith(suffix), (
            f"unexpected path: {path}"
        )
        photo_id = path[len(prefix) : -len(suffix)]
        if photo_id in iterators:
            result = next(iterators[photo_id])
        else:
            result = responses[photo_id]
        if isinstance(result, Exception):
            raise result
        if isinstance(result, int):
            return httpx.Response(result)
        return httpx.Response(200, content=result)

    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


def _make_auth(token: str = "tok") -> AsyncMock:
    """Mock BackendAuthClient stub: get_token returns `token`, invalidate is a no-op."""
    auth = AsyncMock()
    auth.get_token = AsyncMock(return_value=token)
    auth.invalidate = AsyncMock()
    return auth


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


# ---------------------------------------------------------------------------
# Auth client integration
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_uses_auth_client_token_in_authorization_header():
    captured: list[httpx.Request] = []
    client = _make_mock_client({"p1": b"x", "p2": b"y"}, captured=captured)
    auth = _make_auth(token="service-token-42")
    try:
        result = await download_photos_from_backend(
            ["p1", "p2"], _BASE_URL, client=client, auth=auth
        )
    finally:
        await client.aclose()
    assert result == {"p1": b"x", "p2": b"y"}
    assert len(captured) == 2
    for req in captured:
        assert req.headers.get("authorization") == "Bearer service-token-42"
    # One get_token per fetch (no caching in the loader — auth client handles it).
    assert auth.get_token.await_count == 2
    auth.invalidate.assert_not_awaited()


@pytest.mark.asyncio
async def test_no_authorization_header_without_auth_client():
    captured: list[httpx.Request] = []
    client = _make_mock_client({"p1": b"x"}, captured=captured)
    try:
        await download_photos_from_backend(["p1"], _BASE_URL, client=client)
    finally:
        await client.aclose()
    assert captured[0].headers.get("authorization") is None


@pytest.mark.asyncio
async def test_retries_once_on_401_with_fresh_token():
    """On 401, invalidate the cached token, re-fetch, retry exactly once."""
    captured: list[httpx.Request] = []
    client = _make_mock_client({"p1": [401, b"recovered"]}, captured=captured)

    auth = AsyncMock()
    auth.get_token = AsyncMock(side_effect=["stale", "fresh"])
    auth.invalidate = AsyncMock()

    try:
        result = await download_photos_from_backend(
            ["p1"], _BASE_URL, client=client, auth=auth
        )
    finally:
        await client.aclose()

    assert result == {"p1": b"recovered"}
    assert len(captured) == 2
    assert captured[0].headers.get("authorization") == "Bearer stale"
    assert captured[1].headers.get("authorization") == "Bearer fresh"
    assert auth.invalidate.await_count == 1
    assert auth.get_token.await_count == 2


@pytest.mark.asyncio
async def test_persistent_401_skips_photo():
    """If even the retry returns 401, the photo is skipped (no infinite loop)."""
    client = _make_mock_client({"p1": [401, 401]})

    auth = AsyncMock()
    auth.get_token = AsyncMock(side_effect=["t1", "t2"])
    auth.invalidate = AsyncMock()

    try:
        result = await download_photos_from_backend(
            ["p1"], _BASE_URL, client=client, auth=auth
        )
    finally:
        await client.aclose()

    assert result == {}
    assert auth.invalidate.await_count == 1
    assert auth.get_token.await_count == 2


@pytest.mark.asyncio
async def test_login_failure_skips_photo_without_blocking_others():
    """If get_token raises, that photo is skipped; remaining photos still fetched."""
    captured: list[httpx.Request] = []
    client = _make_mock_client({"p1": b"ok", "p2": b"ok2"}, captured=captured)

    auth = AsyncMock()
    auth.get_token = AsyncMock(side_effect=RuntimeError("login broken"))
    auth.invalidate = AsyncMock()

    try:
        result = await download_photos_from_backend(
            ["p1", "p2"], _BASE_URL, client=client, auth=auth
        )
    finally:
        await client.aclose()

    # Login fails for every photo → all skipped, no HTTP requests sent
    assert result == {}
    assert captured == []
