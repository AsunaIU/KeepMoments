import io
import logging
import threading
from typing import Any

import clip
import numpy as np
import torch
from PIL import Image

logger = logging.getLogger(__name__)

_model_cache: dict[str, tuple[Any, Any]] = {}
_model_lock = threading.Lock()


def get_clip_model(model_name: str = "ViT-B/32") -> tuple[Any, Any]:
    if model_name not in _model_cache:
        with _model_lock:
            if model_name not in _model_cache:
                logger.info("Loading CLIP model: %s", model_name)
                model, preprocess = clip.load(model_name, device="cpu")
                model.eval()
                _model_cache[model_name] = (model, preprocess)
    return _model_cache[model_name]


def extract_embeddings(
    photo_bytes: dict[str, bytes],
    model: Any,
    preprocess: Any,
    device: str = "cpu",
) -> dict[str, np.ndarray]:
    ids = list(photo_bytes.keys())
    images = []
    valid_ids = []

    for pid in ids:
        try:
            img = Image.open(io.BytesIO(photo_bytes[pid])).convert("RGB")
            images.append(preprocess(img))
            valid_ids.append(pid)
        except Exception as exc:
            logger.warning("Failed to preprocess photo %s: %s", pid, exc)

    if not images:
        return {}

    batch = torch.stack(images).to(device)
    with torch.no_grad():
        features = model.encode_image(batch)
        features = features / features.norm(dim=-1, keepdim=True)

    return {pid: features[i].cpu().numpy() for i, pid in enumerate(valid_ids)}
