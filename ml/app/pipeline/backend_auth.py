import asyncio
import logging
from typing import Any

logger = logging.getLogger(__name__)

_DEFAULT_TIMEOUT = 30.0


class BackendAuthClient:
    """Lazy login + cached access token for backend service-to-service calls.

    Performs ``POST {base_url}/api/v1/auth/login`` with ``{"email", "password"}``
    on first use, caches the returned ``access_token``, and reuses it.
    Callers handle 401 by invoking ``invalidate()`` and retrying.
    """

    def __init__(
        self,
        base_url: str,
        email: str,
        password: str,
        http_client: Any,
        timeout: float = _DEFAULT_TIMEOUT,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._email = email
        self._password = password
        self._client = http_client
        self._timeout = timeout
        self._token: str | None = None
        self._lock = asyncio.Lock()

    async def get_token(self) -> str:
        if self._token is not None:
            return self._token
        async with self._lock:
            if self._token is not None:  # another coroutine logged in while we waited
                return self._token
            self._token = await self._login()
            return self._token

    async def invalidate(self) -> None:
        async with self._lock:
            self._token = None

    async def _login(self) -> str:
        url = f"{self._base_url}/api/v1/auth/login"
        try:
            resp = await self._client.post(
                url,
                json={"email": self._email, "password": self._password},
                timeout=self._timeout,
            )
        except Exception as exc:
            raise RuntimeError(f"Backend login request failed: {exc}") from exc
        if resp.status_code // 100 != 2:
            raise RuntimeError(
                f"Backend login returned status {resp.status_code}"
            )
        try:
            data = resp.json()
        except Exception as exc:
            raise RuntimeError(f"Backend login response is not JSON: {exc}") from exc
        token = data.get("access_token") if isinstance(data, dict) else None
        if not token:
            raise RuntimeError("Backend login response missing access_token")
        return token
