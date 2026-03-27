import logging
from typing import Any

import clip
import numpy as np
import torch

logger = logging.getLogger(__name__)


def rerank_by_text(
    photo_ids: list[str],
    embeddings: dict[str, np.ndarray],
    user_description: str,
    model: Any,
    device: str = "cpu",
) -> list[str]:
    if not user_description.strip():
        return photo_ids

    try:
        tokens = clip.tokenize([user_description]).to(device)
        with torch.no_grad():
            text_features = model.encode_text(tokens)
            text_features = text_features / text_features.norm(dim=-1, keepdim=True)

        text_vec = text_features[0].cpu().numpy()

        similarities = {
            pid: float(np.dot(text_vec, embeddings[pid]))
            for pid in photo_ids
            if pid in embeddings
        }

        return sorted(photo_ids, key=lambda pid: similarities.get(pid, 0.0), reverse=True)
    except Exception as exc:
        logger.warning("Re-ranking failed, returning original order: %s", exc)
        return photo_ids
