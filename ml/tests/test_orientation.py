"""Tests for orientation detection.

Win 2: detect_orientation now accepts decoded PIL.Image (not bytes).
Decode failures are caught upstream in image_cache.decode_images.
"""
from app.pipeline.orientation import detect_orientation
from app.schemas import Orientation
from tests.conftest import make_landscape_image, make_pil_image, make_portrait_image


def test_landscape_wider_than_tall():
    result = detect_orientation({"p1": make_landscape_image(width=80, height=40)})
    assert result == {"p1": Orientation.landscape}


def test_portrait_taller_than_wide():
    result = detect_orientation({"p1": make_portrait_image(width=40, height=80)})
    assert result == {"p1": Orientation.portrait}


def test_square_classified_as_landscape():
    result = detect_orientation({"p1": make_pil_image(width=64, height=64)})
    assert result == {"p1": Orientation.landscape}


def test_multiple_mixed_photos():
    result = detect_orientation({
        "portrait1": make_portrait_image(),
        "landscape1": make_landscape_image(),
        "portrait2": make_portrait_image(width=30, height=90),
    })
    assert result["portrait1"] == Orientation.portrait
    assert result["landscape1"] == Orientation.landscape
    assert result["portrait2"] == Orientation.portrait


def test_empty_input_returns_empty_dict():
    assert detect_orientation({}) == {}


def test_single_portrait_photo():
    result = detect_orientation({"p": make_portrait_image()})
    assert result == {"p": Orientation.portrait}


def test_single_landscape_photo():
    result = detect_orientation({"p": make_landscape_image()})
    assert result == {"p": Orientation.landscape}
