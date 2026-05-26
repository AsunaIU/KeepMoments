import asyncio
import json

import httpx
import pytest

from app.pipeline.backend_auth import BackendAuthClient


_BASE_URL = "http://backend.test"
_EMAIL = "ml@test"
_PASSWORD = "secret"


def _make_login_client(
    responses: list[httpx.Response | Exception],
    captured: list[httpx.Request] | None = None,
) -> httpx.AsyncClient:
    """AsyncClient whose POSTs to /api/v1/auth/login replay `responses` in order."""

    iterator = iter(responses)

    def handler(request: httpx.Request) -> httpx.Response:
        if captured is not None:
            captured.append(request)
        assert request.url.path == "/api/v1/auth/login", (
            f"unexpected path: {request.url.path}"
        )
        assert request.method == "POST"
        result = next(iterator)
        if isinstance(result, Exception):
            raise result
        return result

    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


def _ok_token(token: str = "tok-123") -> httpx.Response:
    return httpx.Response(
        200,
        json={"access_token": token, "token_type": "Bearer", "expires_in": 3600},
    )


# ---------------------------------------------------------------------------
# Login request shape
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_login_sends_correct_request():
    captured: list[httpx.Request] = []
    client = _make_login_client([_ok_token()], captured=captured)
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        await auth.get_token()
    finally:
        await client.aclose()

    assert len(captured) == 1
    req = captured[0]
    assert str(req.url) == f"{_BASE_URL}/api/v1/auth/login"
    assert req.method == "POST"
    body = json.loads(req.content.decode())
    assert body == {"email": _EMAIL, "password": _PASSWORD}


@pytest.mark.asyncio
async def test_login_returns_access_token():
    client = _make_login_client([_ok_token("abc.def")])
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        token = await auth.get_token()
    finally:
        await client.aclose()
    assert token == "abc.def"


@pytest.mark.asyncio
async def test_login_handles_trailing_slash_in_base_url():
    captured: list[httpx.Request] = []
    client = _make_login_client([_ok_token()], captured=captured)
    auth = BackendAuthClient(_BASE_URL + "/", _EMAIL, _PASSWORD, client)
    try:
        await auth.get_token()
    finally:
        await client.aclose()
    assert str(captured[0].url) == f"{_BASE_URL}/api/v1/auth/login"


# ---------------------------------------------------------------------------
# Caching
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_get_token_caches_after_first_login():
    captured: list[httpx.Request] = []
    client = _make_login_client([_ok_token("first")], captured=captured)
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        t1 = await auth.get_token()
        t2 = await auth.get_token()
        t3 = await auth.get_token()
    finally:
        await client.aclose()
    assert t1 == t2 == t3 == "first"
    assert len(captured) == 1  # only one HTTP login call


@pytest.mark.asyncio
async def test_invalidate_forces_relogin():
    captured: list[httpx.Request] = []
    client = _make_login_client(
        [_ok_token("first"), _ok_token("second")], captured=captured
    )
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        t1 = await auth.get_token()
        await auth.invalidate()
        t2 = await auth.get_token()
    finally:
        await client.aclose()
    assert t1 == "first"
    assert t2 == "second"
    assert len(captured) == 2


@pytest.mark.asyncio
async def test_concurrent_get_token_logs_in_once():
    captured: list[httpx.Request] = []
    client = _make_login_client([_ok_token("once")], captured=captured)
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        tokens = await asyncio.gather(
            auth.get_token(), auth.get_token(), auth.get_token()
        )
    finally:
        await client.aclose()
    assert tokens == ["once", "once", "once"]
    assert len(captured) == 1


# ---------------------------------------------------------------------------
# Error cases
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
@pytest.mark.parametrize("status", [400, 401, 403, 500, 502])
async def test_login_raises_on_non_2xx(status: int):
    client = _make_login_client([httpx.Response(status)])
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        with pytest.raises(RuntimeError):
            await auth.get_token()
    finally:
        await client.aclose()


@pytest.mark.asyncio
async def test_login_raises_when_access_token_missing():
    client = _make_login_client([httpx.Response(200, json={"foo": "bar"})])
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        with pytest.raises(RuntimeError):
            await auth.get_token()
    finally:
        await client.aclose()


@pytest.mark.asyncio
async def test_login_raises_when_access_token_empty():
    client = _make_login_client([httpx.Response(200, json={"access_token": ""})])
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        with pytest.raises(RuntimeError):
            await auth.get_token()
    finally:
        await client.aclose()


@pytest.mark.asyncio
async def test_login_does_not_cache_on_failure():
    """A failed login must not poison subsequent attempts."""
    captured: list[httpx.Request] = []
    client = _make_login_client(
        [httpx.Response(500), _ok_token("after-recovery")], captured=captured
    )
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        with pytest.raises(RuntimeError):
            await auth.get_token()
        # Next call should retry — not stuck in a failed state
        token = await auth.get_token()
    finally:
        await client.aclose()
    assert token == "after-recovery"
    assert len(captured) == 2


# ---------------------------------------------------------------------------
# Client lifecycle
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_does_not_close_provided_client():
    client = _make_login_client([_ok_token()])
    auth = BackendAuthClient(_BASE_URL, _EMAIL, _PASSWORD, client)
    try:
        await auth.get_token()
        assert not client.is_closed
    finally:
        await client.aclose()
