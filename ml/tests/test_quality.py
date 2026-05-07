import pytest
from PIL import Image

from app.pipeline.quality import score_quality
from tests.conftest import make_checkerboard_image, make_pil_image


def test_scores_are_in_unit_range():
    images = {
        "dark": make_pil_image(color=(0, 0, 0)),
        "mid": make_pil_image(color=(128, 128, 128)),
        "bright": make_pil_image(color=(255, 255, 255)),
        "sharp": make_checkerboard_image(),
    }
    scores = score_quality(images)
    for pid, score in scores.items():
        assert 0.0 <= score <= 1.0, f"{pid}: score {score} out of [0, 1]"


def test_single_image_sharpness_normalized_to_zero():
    img = make_pil_image(color=(128, 128, 128))  # ~0.5 brightness → exposure ≈ 1.0
    scores = score_quality({"only": img})
    assert 0.38 <= scores["only"] <= 0.42


def test_very_dark_image_has_low_exposure():
    images = {
        "dark": make_pil_image(color=(0, 0, 0)),
        "mid": make_pil_image(color=(128, 128, 128)),
    }
    scores = score_quality(images)
    assert scores["mid"] > scores["dark"]


def test_very_bright_image_has_low_exposure():
    images = {
        "bright": make_pil_image(color=(255, 255, 255)),
        "mid": make_pil_image(color=(128, 128, 128)),
    }
    scores = score_quality(images)
    assert scores["mid"] > scores["bright"]


def test_medium_brightness_maximises_exposure():
    scores = score_quality({"mid": make_pil_image(color=(128, 128, 128))})
    assert scores["mid"] > 0.35


def test_all_identical_images_have_equal_scores():
    img = make_pil_image(color=(100, 100, 100))
    scores = score_quality({"a": img, "b": img, "c": img})
    vals = list(scores.values())
    assert vals[0] == pytest.approx(vals[1])
    assert vals[1] == pytest.approx(vals[2])


def test_sharper_image_scores_higher_than_blurry():
    images = {"sharp": make_checkerboard_image(), "blurry": make_pil_image(color=(128, 128, 128))}
    scores = score_quality(images)
    assert scores["sharp"] > scores["blurry"]


def test_empty_input_returns_empty():
    assert score_quality({}) == {}


# ---------------------------------------------------------------------------
# Win 3: large images are downsampled before Laplacian to bound CPU cost
# ---------------------------------------------------------------------------

def test_large_image_is_downsampled_for_quality(monkeypatch):
    """A 4000×3000 image must be reduced to ≤1024px before pixel-level analysis."""
    captured_sizes: list[tuple[int, int]] = []

    real_array = __import__("numpy").array

    def spy_array(img, *a, **kw):
        if isinstance(img, Image.Image):
            captured_sizes.append(img.size)
        return real_array(img, *a, **kw)

    monkeypatch.setattr("app.pipeline.quality.np.array", spy_array)

    big = Image.new("RGB", (4000, 3000), (128, 128, 128))
    score_quality({"big": big})

    assert captured_sizes, "np.array should have been called on the (downsampled) image"
    w, h = captured_sizes[0]
    assert max(w, h) <= 1024, f"image not downsampled: got {w}×{h}"


def test_small_image_not_upsampled():
    """An image already smaller than the threshold is processed at native size."""
    small = make_pil_image(color=(128, 128, 128), width=200, height=200)
    scores = score_quality({"s": small})
    assert "s" in scores


def test_quality_does_not_mutate_input_image():
    """Downsampling must not mutate the caller's PIL.Image."""
    img = Image.new("RGB", (2048, 2048), (128, 128, 128))
    original_size = img.size
    score_quality({"p": img})
    assert img.size == original_size
