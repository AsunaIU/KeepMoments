import io
import logging

from PIL import Image

from app.schemas import Orientation

logger = logging.getLogger(__name__)


def detect_orientation(photo_bytes: dict[str, bytes]) -> dict[str, Orientation]:
    result: dict[str, Orientation] = {}
    for photo_id, data in photo_bytes.items():
        try:
            img = Image.open(io.BytesIO(data))
            width, height = img.size
            result[photo_id] = Orientation.landscape if width >= height else Orientation.portrait
        except Exception:
            logger.warning("Could not determine orientation for %s", photo_id)
    return result
