import pytest

from app.pipeline.quality import score_quality
from tests.conftest import make_checkerboard_image_bytes, make_image_bytes


def test_scores_are_in_unit_range():
    photo_bytes = {
        "dark": make_image_bytes(color=(0, 0, 0)),
        "mid": make_image_bytes(color=(128, 128, 128)),
        "bright": make_image_bytes(color=(255, 255, 255)),
        "sharp": make_checkerboard_image_bytes(),
    }
    scores = score_quality(photo_bytes)
    for pid, score in scores.items():
        assert 0.0 <= score <= 1.0, f"{pid}: score {score} out of [0, 1]"


def test_single_image_sharpness_normalized_to_zero():
    # With a single image s_min == s_max → s_range = 1.0 (fallback)
    # norm_sharpness = (sharpness - sharpness) / 1.0 = 0
    # score = 0.4 * exposure
    img = make_image_bytes(color=(128, 128, 128))  # ~0.5 brightness → exposure ≈ 1.0
    scores = score_quality({"only": img})
    # exposure ≈ 1 - |128/255 - 0.5| * 2 ≈ 1 - 0.004 ≈ 0.996
    assert 0.38 <= scores["only"] <= 0.42


def test_corrupt_bytes_score_is_zero():
    scores = score_quality({"bad": b"not_an_image"})
    assert scores["bad"] == pytest.approx(0.0)


def test_very_dark_image_has_low_exposure():
    photo_bytes = {
        "dark": make_image_bytes(color=(0, 0, 0)),
        "mid": make_image_bytes(color=(128, 128, 128)),
    }
    scores = score_quality(photo_bytes)
    # Both images have very low sharpness (uniform), so scores dominated by exposure
    # dark: brightness=0 → exposure=0 → score ≈ 0
    # mid: brightness≈0.5 → exposure≈1 → score ≈ 0.4
    assert scores["mid"] > scores["dark"]


def test_very_bright_image_has_low_exposure():
    photo_bytes = {
        "bright": make_image_bytes(color=(255, 255, 255)),
        "mid": make_image_bytes(color=(128, 128, 128)),
    }
    scores = score_quality(photo_bytes)
    assert scores["mid"] > scores["bright"]


def test_medium_brightness_maximises_exposure():
    # exposure = 1 - |mean/255 - 0.5| * 2  → peaks at mean=0.5 (brightness=128)
    photo_bytes = {"mid": make_image_bytes(color=(128, 128, 128))}
    scores = score_quality(photo_bytes)
    # norm_sharpness=0, exposure≈1, score ≈ 0.4
    assert scores["mid"] > 0.35


def test_all_identical_images_have_equal_scores():
    img = make_image_bytes(color=(100, 100, 100))
    scores = score_quality({"a": img, "b": img, "c": img})
    vals = list(scores.values())
    assert vals[0] == pytest.approx(vals[1])
    assert vals[1] == pytest.approx(vals[2])


def test_sharper_image_scores_higher_than_blurry():
    # Both images have similar medium brightness (exposure ≈ 1)
    # but sharp has high Laplacian variance, uniform has zero
    sharp = make_checkerboard_image_bytes()
    blurry = make_image_bytes(color=(128, 128, 128))
    scores = score_quality({"sharp": sharp, "blurry": blurry})
    assert scores["sharp"] > scores["blurry"]


def test_mixed_valid_and_corrupt():
    photo_bytes = {
        "good": make_image_bytes(),
        "bad": b"garbage",
    }
    scores = score_quality(photo_bytes)
    assert set(scores.keys()) == {"good", "bad"}
    assert scores["bad"] == pytest.approx(0.0)
    assert scores["good"] >= 0.0
