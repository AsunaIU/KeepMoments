import logging
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError

logger = logging.getLogger(__name__)

_DEFAULT_TIMEOUT = 30.0  # seconds per photo


def download_photos(
    photo_ids: list[str],
    bucket: str,
    s3_client,
    timeout: float = _DEFAULT_TIMEOUT,
) -> dict[str, bytes]:
    def fetch(photo_id: str) -> tuple[str, bytes | None]:
        try:
            obj = s3_client.get_object(Bucket=bucket, Key=photo_id)
            return photo_id, obj["Body"].read()
        except Exception as exc:
            logger.warning("Failed to download photo %s: %s", photo_id, exc)
            return photo_id, None

    results: dict[str, bytes] = {}
    with ThreadPoolExecutor() as pool:
        futures = {pool.submit(fetch, pid): pid for pid in photo_ids}
        for future, pid in futures.items():
            try:
                photo_id, data = future.result(timeout=timeout)
                if data is not None:
                    results[photo_id] = data
            except FuturesTimeoutError:
                logger.warning(
                    "Timeout downloading photo %s after %.1f seconds", pid, timeout
                )
    return results
