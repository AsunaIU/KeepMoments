"""Decode photo bytes once and reuse PIL.Image across pipeline stages.

Decoding is the costliest part of every image-touching step (orientation,
embeddings, quality, caption encoding). Doing it once and caching saves
~3-4× CPU on the per-image work.
"""
import io
import logging

from PIL import Image

logger = logging.getLogger(__name__)


def decode_images(photo_bytes: dict[str, bytes]) -> dict[str, Image.Image]:
    """Eagerly decode each photo to an RGB PIL.Image. Failed photos are skipped."""
    result: dict[str, Image.Image] = {}
    for pid, data in photo_bytes.items():
        try:
            img = Image.open(io.BytesIO(data))
            if img.mode != "RGB":
                img = img.convert("RGB")
            img.load()
            result[pid] = img
        except Exception as exc:
            logger.warning("Failed to decode photo %s: %s", pid, exc)
    return result
