import io

import numpy as np
import pytest
from PIL import Image

from app.config import Settings
from app.schemas import Page, ProcessRequest, Slot, Template


# ---------------------------------------------------------------------------
# Image helpers
# ---------------------------------------------------------------------------

def make_image_bytes(color: tuple = (128, 128, 128), width: int = 64, height: int = 64) -> bytes:
    """Solid-color PNG image bytes."""
    img = Image.new("RGB", (width, height), color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def make_checkerboard_image_bytes(width: int = 64, height: int = 64) -> bytes:
    """Black-and-white checkerboard — high Laplacian variance (sharp)."""
    arr = np.zeros((height, width, 3), dtype=np.uint8)
    for y in range(height):
        for x in range(width):
            if (x + y) % 2 == 0:
                arr[y, x] = [255, 255, 255]
    buf = io.BytesIO()
    Image.fromarray(arr).save(buf, format="PNG")
    return buf.getvalue()


# ---------------------------------------------------------------------------
# Schema helpers
# ---------------------------------------------------------------------------

def make_template(n_pages: int = 1, slots_per_page: int = 3) -> Template:
    pages = [
        Page(
            id=f"page_{p}",
            slots=[Slot(id=f"slot_{p}_{s}") for s in range(slots_per_page)],
        )
        for p in range(n_pages)
    ]
    return Template(id="template_1", pages=pages)


def make_process_request(
    photo_ids: list[str] | None = None,
    min_photos: int = 1,
    max_photos: int = 10,
    user_description: str = "test description",
    n_pages: int = 1,
    slots_per_page: int = 3,
) -> ProcessRequest:
    if photo_ids is None:
        photo_ids = [f"photo_{i}" for i in range(5)]
    return ProcessRequest(
        photo_ids=photo_ids,
        user_description=user_description,
        min_photos=min_photos,
        max_photos=max_photos,
        template=make_template(n_pages, slots_per_page),
    )


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_settings() -> Settings:
    return Settings(
        AWS_ACCESS_KEY_ID="test_key",
        AWS_SECRET_ACCESS_KEY="test_secret",
        S3_BUCKET_NAME="test-bucket",
    )


@pytest.fixture
def dummy_embeddings() -> dict[str, np.ndarray]:
    """Five L2-normalised 512-dim float32 embeddings."""
    rng = np.random.RandomState(42)
    vecs: dict[str, np.ndarray] = {}
    for i in range(5):
        v = rng.randn(512).astype(np.float32)
        v /= np.linalg.norm(v)
        vecs[f"photo_{i}"] = v
    return vecs
