import asyncio
import logging
from typing import Any

logger = logging.getLogger(__name__)

_DEFAULT_TIMEOUT = 30.0


async def download_photos_from_backend(
    photo_ids: list[str],
    base_url: str,
    client: Any | None = None,
    timeout: float = _DEFAULT_TIMEOUT,
    auth_token: str | None = None,
) -> dict[str, bytes]:
    """Fetch photos via GET {base_url}/api/v1/photos/{id}/file/ in parallel.

    Mirrors download_photos() semantics: per-photo failures are logged and skipped.
    If ``client`` (httpx.AsyncClient) is supplied, it is reused and not closed.
    Otherwise a temporary client is created and closed before returning.
    If ``auth_token`` is supplied, it is sent as ``Authorization: Bearer <token>``.
    """
    import httpx  # lazy, matches caption_generator pattern

    owns_client = client is None
    if client is None:
        client = httpx.AsyncClient(timeout=timeout)

    base = base_url.rstrip("/")
    headers = {"Authorization": f"Bearer {auth_token}"} if auth_token else None

    async def fetch(pid: str) -> tuple[str, bytes | None]:
        url = f"{base}/api/v1/photos/{pid}/file"
        try:
            resp = await client.get(url, timeout=timeout, headers=headers)
            resp.raise_for_status()
            return pid, resp.content
        except Exception as exc:
            logger.warning("Failed to download photo %s from backend: %s", pid, exc)
            return pid, None

    try:
        results = await asyncio.gather(*(fetch(pid) for pid in photo_ids))
    finally:
        if owns_client:
            await client.aclose()

    return {pid: data for pid, data in results if data is not None}
