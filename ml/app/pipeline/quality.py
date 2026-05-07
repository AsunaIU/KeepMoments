import logging

import cv2
import numpy as np
from PIL import Image

logger = logging.getLogger(__name__)

_QUALITY_MAX_DIM = 1024  # downsample longer side before pixel-level analysis


def _quality_image(img: Image.Image) -> Image.Image:
    """Return a copy of ``img`` whose longer side is at most ``_QUALITY_MAX_DIM``.

    Full-resolution Laplacian on a 24MP photo is wasteful — the variance metric
    is essentially scale-invariant for blur detection. We never mutate the input.
    """
    w, h = img.size
    if max(w, h) <= _QUALITY_MAX_DIM:
        return img
    work = img.copy()
    work.thumbnail((_QUALITY_MAX_DIM, _QUALITY_MAX_DIM))
    return work


def score_quality(images: dict[str, Image.Image]) -> dict[str, float]:
    """Score each image on sharpness (Laplacian variance) and exposure (brightness).

    Inputs are already-decoded PIL.Image objects (see image_cache.decode_images).
    """
    raw_scores: dict[str, dict[str, float]] = {}

    for pid, img in images.items():
        try:
            small = _quality_image(img)
            arr = np.array(small)
            gray = cv2.cvtColor(arr, cv2.COLOR_RGB2GRAY)

            sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
            mean_brightness = gray.mean() / 255.0
            exposure = 1.0 - abs(mean_brightness - 0.5) * 2.0

            raw_scores[pid] = {"sharpness": sharpness, "exposure": exposure}
        except Exception as exc:
            logger.warning("Failed to score quality for photo %s: %s", pid, exc)
            raw_scores[pid] = {"sharpness": 0.0, "exposure": 0.0}

    if not raw_scores:
        return {}

    sharpness_values = [v["sharpness"] for v in raw_scores.values()]
    s_min = min(sharpness_values)
    s_max = max(sharpness_values)
    s_range = s_max - s_min if s_max > s_min else 1.0

    return {
        pid: 0.6 * (m["sharpness"] - s_min) / s_range + 0.4 * m["exposure"]
        for pid, m in raw_scores.items()
    }
