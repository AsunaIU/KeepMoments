"""Tests for cover_filler — written before implementation (TDD)."""
from unittest.mock import patch

import pytest

from app.schemas import (
    CoverConfig,
    CoverMode,
    FilledCover,
    FilledPage,
    FilledSlot,
    FilledTemplate,
)
from app.pipeline.cover_filler import _fill_single_cover, fill_covers


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _template(n_pages: int = 2) -> FilledTemplate:
    pages = [
        FilledPage(
            id=f"page_{i}",
            slots=[FilledSlot(id=f"slot_{i}_0", photo_id=f"photo_{i}")],
            caption=f"Caption {i}",
        )
        for i in range(n_pages)
    ]
    return FilledTemplate(id="t1", pages=pages)


_RANKED = ["photo_0", "photo_1", "photo_2"]
_PHOTO_BYTES: dict[str, bytes] = {k: b"bytes" for k in _RANKED}

_BASE_KWARGS = dict(
    ranked_photos=_RANKED,
    photo_bytes=_PHOTO_BYTES,
    user_description="summer wedding",
    api_key=None,
    model=None,
    base_url=None,
)


# ---------------------------------------------------------------------------
# Group A: _fill_single_cover — photo mode
# ---------------------------------------------------------------------------

def test_photo_mode_manual_photo_id():
    config = CoverConfig(mode=CoverMode.photo, photo_id="manual_photo")
    result = _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    assert result.mode == CoverMode.photo
    assert result.photo_id == "manual_photo"
    assert result.text is None


def test_photo_mode_auto_front_uses_first_ranked():
    config = CoverConfig(mode=CoverMode.photo)
    result = _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    assert result.photo_id == _RANKED[0]


def test_photo_mode_auto_back_uses_last_ranked():
    config = CoverConfig(mode=CoverMode.photo)
    result = _fill_single_cover(config, **_BASE_KWARGS, is_front=False)
    assert result.photo_id == _RANKED[-1]


def test_photo_mode_auto_empty_ranked_returns_none():
    config = CoverConfig(mode=CoverMode.photo)
    result = _fill_single_cover(
        config, ranked_photos=[], photo_bytes={}, user_description="x",
        api_key=None, model=None, base_url=None, is_front=True,
    )
    assert result.photo_id is None


# ---------------------------------------------------------------------------
# Group B: _fill_single_cover — caption mode
# ---------------------------------------------------------------------------

def test_caption_mode_manual_text():
    config = CoverConfig(mode=CoverMode.caption, text="My Wedding Album")
    result = _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    assert result.mode == CoverMode.caption
    assert result.text == "My Wedding Album"
    assert result.photo_id is None


def test_caption_mode_manual_text_no_api_call():
    config = CoverConfig(mode=CoverMode.caption, text="Manual")
    with patch("app.pipeline.cover_filler.generate_cover_caption") as mock_gen:
        _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    mock_gen.assert_not_called()


def test_caption_mode_auto_generate_calls_generator():
    config = CoverConfig(mode=CoverMode.caption)
    with patch("app.pipeline.cover_filler.generate_cover_caption", return_value="Generated title") as mock_gen:
        result = _fill_single_cover(
            config,
            ranked_photos=_RANKED,
            photo_bytes=_PHOTO_BYTES,
            user_description="summer wedding",
            api_key="test-key",
            model="model-x",
            base_url=None,
            is_front=True,
        )
    mock_gen.assert_called_once_with(
        ranked_photos=_RANKED,
        photo_bytes=_PHOTO_BYTES,
        user_description="summer wedding",
        api_key="test-key",
        model="model-x",
        base_url=None,
    )
    assert result.text == "Generated title"


def test_caption_mode_auto_no_api_key_returns_none_text():
    config = CoverConfig(mode=CoverMode.caption)
    with patch("app.pipeline.cover_filler.generate_cover_caption") as mock_gen:
        result = _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    mock_gen.assert_not_called()
    assert result.text is None
    assert result.mode == CoverMode.caption


# ---------------------------------------------------------------------------
# Group C: fill_covers
# ---------------------------------------------------------------------------

def test_fill_covers_front_only():
    template = _template()
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    result = fill_covers(template, front_config=front, back_config=None, **_BASE_KWARGS)
    assert result.front_cover is not None
    assert result.front_cover.photo_id == "f"
    assert result.back_cover is None


def test_fill_covers_back_only():
    template = _template()
    back = CoverConfig(mode=CoverMode.caption, text="The End")
    result = fill_covers(template, front_config=None, back_config=back, **_BASE_KWARGS)
    assert result.front_cover is None
    assert result.back_cover is not None
    assert result.back_cover.text == "The End"


def test_fill_covers_both():
    template = _template()
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    back = CoverConfig(mode=CoverMode.caption, text="The End")
    result = fill_covers(template, front_config=front, back_config=back, **_BASE_KWARGS)
    assert result.front_cover is not None
    assert result.back_cover is not None


def test_fill_covers_neither():
    template = _template()
    result = fill_covers(template, front_config=None, back_config=None, **_BASE_KWARGS)
    assert result.front_cover is None
    assert result.back_cover is None


def test_fill_covers_preserves_pages_and_captions():
    template = _template(n_pages=3)
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    result = fill_covers(template, front_config=front, back_config=None, **_BASE_KWARGS)
    assert len(result.pages) == 3
    for i, page in enumerate(result.pages):
        assert page.caption == f"Caption {i}"
        assert page.slots[0].photo_id == f"photo_{i}"


def test_fill_covers_returns_new_object():
    template = _template()
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    result = fill_covers(template, front_config=front, back_config=None, **_BASE_KWARGS)
    assert result is not template
    assert template.front_cover is None  # original not mutated
