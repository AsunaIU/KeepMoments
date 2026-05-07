from app.pipeline.orientation import detect_orientation
from app.schemas import Orientation
from tests.conftest import make_landscape_image_bytes, make_portrait_image_bytes


def test_landscape_wider_than_tall():
    data = make_landscape_image_bytes(width=80, height=40)
    result = detect_orientation({"p1": data})
    assert result == {"p1": Orientation.landscape}


def test_portrait_taller_than_wide():
    data = make_portrait_image_bytes(width=40, height=80)
    result = detect_orientation({"p1": data})
    assert result == {"p1": Orientation.portrait}


def test_square_classified_as_landscape():
    from tests.conftest import make_image_bytes
    data = make_image_bytes(width=64, height=64)
    result = detect_orientation({"p1": data})
    assert result == {"p1": Orientation.landscape}


def test_multiple_mixed_photos():
    result = detect_orientation({
        "portrait1": make_portrait_image_bytes(),
        "landscape1": make_landscape_image_bytes(),
        "portrait2": make_portrait_image_bytes(width=30, height=90),
    })
    assert result["portrait1"] == Orientation.portrait
    assert result["landscape1"] == Orientation.landscape
    assert result["portrait2"] == Orientation.portrait


def test_empty_input_returns_empty_dict():
    assert detect_orientation({}) == {}


def test_corrupt_bytes_skipped():
    result = detect_orientation({"bad": b"not_an_image"})
    assert result == {}


def test_corrupt_photo_skipped_valid_returned():
    valid = make_landscape_image_bytes()
    result = detect_orientation({"bad": b"garbage", "good": valid})
    assert "bad" not in result
    assert result["good"] == Orientation.landscape


def test_single_portrait_photo():
    data = make_portrait_image_bytes()
    result = detect_orientation({"p": data})
    assert len(result) == 1
    assert result["p"] == Orientation.portrait


def test_single_landscape_photo():
    data = make_landscape_image_bytes()
    result = detect_orientation({"p": data})
    assert len(result) == 1
    assert result["p"] == Orientation.landscape
