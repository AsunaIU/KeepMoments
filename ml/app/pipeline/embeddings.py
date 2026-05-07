import logging
import threading
from typing import Any

import clip
import numpy as np
import torch
from PIL import Image

logger = logging.getLogger(__name__)

_model_cache: dict[str, tuple[Any, Any, str]] = {}
_model_lock = threading.Lock()

_DEFAULT_BATCH_SIZE = 64


def _select_device() -> str:
    """Pick CUDA when available; CPU otherwise.

    MPS is intentionally excluded — the upstream CLIP repo's image preprocess
    relies on torchvision ops that fall back to CPU on MPS, so explicit MPS
    routing offers no speedup and risks subtle bugs.
    """
    if torch.cuda.is_available():
        return "cuda"
    return "cpu"


def get_clip_model(model_name: str = "ViT-B/32") -> tuple[Any, Any]:
    """Load CLIP once per model_name. Returns (model, preprocess)."""
    if model_name not in _model_cache:
        with _model_lock:
            if model_name not in _model_cache:
                device = _select_device()
                logger.info("Loading CLIP model %s on device=%s", model_name, device)
                model, preprocess = clip.load(model_name, device=device)
                model.eval()
                _model_cache[model_name] = (model, preprocess, device)
    model, preprocess, _ = _model_cache[model_name]
    return model, preprocess


def _model_device(model: Any) -> str:
    """Return device of an already-loaded CLIP model (or 'cpu' if undetectable)."""
    try:
        device = next(model.parameters()).device
        if isinstance(device, torch.device):
            return str(device)
    except (StopIteration, AttributeError, TypeError):
        pass
    return "cpu"


def extract_embeddings(
    images: dict[str, Image.Image],
    model: Any,
    preprocess: Any,
    batch_size: int = _DEFAULT_BATCH_SIZE,
) -> dict[str, np.ndarray]:
    """Compute L2-normalized CLIP embeddings.

    Inputs are already-decoded PIL.Image objects (see image_cache.decode_images).
    Large inputs are split into chunks of ``batch_size`` to bound peak memory.
    """
    tensors: list[torch.Tensor] = []
    valid_ids: list[str] = []

    for pid, img in images.items():
        try:
            tensors.append(preprocess(img))
            valid_ids.append(pid)
        except Exception as exc:
            logger.warning("Failed to preprocess photo %s: %s", pid, exc)

    if not tensors:
        return {}

    device = _model_device(model)
    out: dict[str, np.ndarray] = {}

    for start in range(0, len(tensors), batch_size):
        chunk_tensors = tensors[start:start + batch_size]
        chunk_ids = valid_ids[start:start + batch_size]
        batch = torch.stack(chunk_tensors).to(device)
        with torch.no_grad():
            features = model.encode_image(batch)
            features = features / features.norm(dim=-1, keepdim=True)
        features = features.cpu().numpy()
        for i, pid in enumerate(chunk_ids):
            out[pid] = features[i]

    return out
