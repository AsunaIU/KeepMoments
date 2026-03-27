import io
import logging

import cv2
import numpy as np
from PIL import Image

logger = logging.getLogger(__name__)


def score_quality(photo_bytes: dict[str, bytes]) -> dict[str, float]:
    raw_scores: dict[str, dict[str, float]] = {}

    for pid, data in photo_bytes.items():
        try:
            img = Image.open(io.BytesIO(data)).convert("RGB")
            arr = np.array(img)
            gray = cv2.cvtColor(arr, cv2.COLOR_RGB2GRAY)

            sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
            mean_brightness = gray.mean() / 255.0
            exposure = 1.0 - abs(mean_brightness - 0.5) * 2.0

            raw_scores[pid] = {"sharpness": sharpness, "exposure": exposure}
        except Exception as exc:
            logger.warning("Failed to score quality for photo %s: %s", pid, exc)
            raw_scores[pid] = {"sharpness": 0.0, "exposure": 0.0}

    # Min-max normalize sharpness across all photos
    sharpness_values = [v["sharpness"] for v in raw_scores.values()]
    s_min = min(sharpness_values)
    s_max = max(sharpness_values)
    s_range = s_max - s_min if s_max > s_min else 1.0

    scores: dict[str, float] = {}
    for pid, metrics in raw_scores.items():
        norm_sharpness = (metrics["sharpness"] - s_min) / s_range
        scores[pid] = 0.6 * norm_sharpness + 0.4 * metrics["exposure"]

    return scores
