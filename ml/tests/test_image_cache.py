"""Tests for image_cache.decode_images — single-pass decode of photo bytes."""
from PIL import Image

from app.pipeline.image_cache import decode_images
from tests.conftest import make_image_bytes


def test_decodes_all_valid_images():
    photo_bytes = {f"p{i}": make_image_bytes() for i in range(3)}
    images = decode_images(photo_bytes)
    assert set(images.keys()) == set(photo_bytes.keys())
    for img in images.values():
        assert isinstance(img, Image.Image)
        assert img.mode == "RGB"


def test_skips_corrupt_bytes_with_warning(caplog):
    photo_bytes = {"good": make_image_bytes(), "bad": b"not an image"}
    with caplog.at_level("WARNING"):
        images = decode_images(photo_bytes)
    assert "good" in images
    assert "bad" not in images
    assert any("bad" in rec.message for rec in caplog.records)


def test_empty_input_returns_empty():
    assert decode_images({}) == {}


def test_all_corrupt_returns_empty():
    images = decode_images({"a": b"x", "b": b"y"})
    assert images == {}


def test_decoded_image_preserves_dimensions():
    photo_bytes = {"p": make_image_bytes(width=80, height=40)}
    images = decode_images(photo_bytes)
    assert images["p"].size == (80, 40)


def test_rgba_input_converted_to_rgb():
    import io
    img = Image.new("RGBA", (32, 32), (255, 0, 0, 128))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    photo_bytes = {"p": buf.getvalue()}
    images = decode_images(photo_bytes)
    assert images["p"].mode == "RGB"


def test_decode_is_eager_not_lazy():
    """Image.load() must be called so byte buffer can be released by caller."""
    photo_bytes = {"p": make_image_bytes()}
    images = decode_images(photo_bytes)
    # If decode were lazy, accessing .getpixel without the bytes would fail.
    # We simulate buffer release by ensuring .load() has been called via .im not None.
    assert images["p"].im is not None
