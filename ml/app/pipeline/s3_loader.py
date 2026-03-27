import logging
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger(__name__)


def download_photos(
    photo_ids: list[str],
    bucket: str,
    s3_client,
) -> dict[str, bytes]:
    def fetch(photo_id: str) -> tuple[str, bytes | None]:
        try:
            obj = s3_client.get_object(Bucket=bucket, Key=photo_id)
            return photo_id, obj["Body"].read()
        except Exception as exc:
            logger.warning("Failed to download photo %s: %s", photo_id, exc)
            return photo_id, None

    with ThreadPoolExecutor() as pool:
        results = list(pool.map(fetch, photo_ids))

    return {pid: data for pid, data in results if data is not None}
