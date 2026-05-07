"""Tests for cover_filler.

Win 2: takes dict[str, PIL.Image] instead of bytes.
Win 1: cover_filler is async (generate_cover_caption became awaitable).
"""
from unittest.mock import AsyncMock, patch

from app.pipeline.cover_filler import _fill_single_cover, fill_covers
from app.schemas import (
    CoverConfig,
    CoverMode,
    FilledPage,
    FilledSlot,
    FilledTemplate,
)
from tests.conftest import make_pil_image


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
_IMAGES: dict = {k: make_pil_image() for k in _RANKED}

_BASE_KWARGS = dict(
    ranked_photos=_RANKED,
    images=_IMAGES,
    user_description="summer wedding",
    api_key=None,
    model=None,
    base_url=None,
)


# ---------------------------------------------------------------------------
# Group A: _fill_single_cover — photo mode
# ---------------------------------------------------------------------------

async def test_photo_mode_manual_photo_id():
    config = CoverConfig(mode=CoverMode.photo, photo_id="manual_photo")
    result = await _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    assert result.mode == CoverMode.photo
    assert result.photo_id == "manual_photo"
    assert result.text is None


async def test_photo_mode_auto_front_uses_first_ranked():
    config = CoverConfig(mode=CoverMode.photo)
    result = await _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    assert result.photo_id == _RANKED[0]


async def test_photo_mode_auto_back_uses_last_ranked():
    config = CoverConfig(mode=CoverMode.photo)
    result = await _fill_single_cover(config, **_BASE_KWARGS, is_front=False)
    assert result.photo_id == _RANKED[-1]


async def test_photo_mode_auto_empty_ranked_returns_none():
    config = CoverConfig(mode=CoverMode.photo)
    result = await _fill_single_cover(
        config, ranked_photos=[], images={}, user_description="x",
        api_key=None, model=None, base_url=None, is_front=True,
    )
    assert result.photo_id is None


# ---------------------------------------------------------------------------
# Group B: _fill_single_cover — caption mode
# ---------------------------------------------------------------------------

async def test_caption_mode_manual_text():
    config = CoverConfig(mode=CoverMode.caption, text="My Wedding Album")
    result = await _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    assert result.mode == CoverMode.caption
    assert result.text == "My Wedding Album"
    assert result.photo_id is None


async def test_caption_mode_manual_text_no_api_call():
    config = CoverConfig(mode=CoverMode.caption, text="Manual")
    with patch("app.pipeline.cover_filler.generate_cover_caption", new_callable=AsyncMock) as mock_gen:
        await _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    mock_gen.assert_not_called()


async def test_caption_mode_auto_generate_calls_generator():
    config = CoverConfig(mode=CoverMode.caption)
    with patch(
        "app.pipeline.cover_filler.generate_cover_caption",
        new_callable=AsyncMock,
        return_value="Generated title",
    ) as mock_gen:
        result = await _fill_single_cover(
            config,
            ranked_photos=_RANKED,
            images=_IMAGES,
            user_description="summer wedding",
            api_key="test-key",
            model="model-x",
            base_url=None,
            is_front=True,
        )
    mock_gen.assert_awaited_once_with(
        ranked_photos=_RANKED,
        images=_IMAGES,
        user_description="summer wedding",
        api_key="test-key",
        model="model-x",
        base_url=None,
    )
    assert result.text == "Generated title"


async def test_caption_mode_auto_no_api_key_returns_none_text():
    config = CoverConfig(mode=CoverMode.caption)
    with patch("app.pipeline.cover_filler.generate_cover_caption", new_callable=AsyncMock) as mock_gen:
        result = await _fill_single_cover(config, **_BASE_KWARGS, is_front=True)
    mock_gen.assert_not_called()
    assert result.text is None
    assert result.mode == CoverMode.caption


# ---------------------------------------------------------------------------
# Group C: fill_covers
# ---------------------------------------------------------------------------

async def test_fill_covers_front_only():
    template = _template()
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    result = await fill_covers(template, front_config=front, back_config=None, **_BASE_KWARGS)
    assert result.front_cover is not None
    assert result.front_cover.photo_id == "f"
    assert result.back_cover is None


async def test_fill_covers_back_only():
    template = _template()
    back = CoverConfig(mode=CoverMode.caption, text="The End")
    result = await fill_covers(template, front_config=None, back_config=back, **_BASE_KWARGS)
    assert result.front_cover is None
    assert result.back_cover is not None
    assert result.back_cover.text == "The End"


async def test_fill_covers_both():
    template = _template()
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    back = CoverConfig(mode=CoverMode.caption, text="The End")
    result = await fill_covers(template, front_config=front, back_config=back, **_BASE_KWARGS)
    assert result.front_cover is not None
    assert result.back_cover is not None


async def test_fill_covers_neither():
    template = _template()
    result = await fill_covers(template, front_config=None, back_config=None, **_BASE_KWARGS)
    assert result.front_cover is None
    assert result.back_cover is None


async def test_fill_covers_preserves_pages_and_captions():
    template = _template(n_pages=3)
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    result = await fill_covers(template, front_config=front, back_config=None, **_BASE_KWARGS)
    assert len(result.pages) == 3
    for i, page in enumerate(result.pages):
        assert page.caption == f"Caption {i}"
        assert page.slots[0].photo_id == f"photo_{i}"


async def test_fill_covers_returns_new_object():
    template = _template()
    front = CoverConfig(mode=CoverMode.photo, photo_id="f")
    result = await fill_covers(template, front_config=front, back_config=None, **_BASE_KWARGS)
    assert result is not template
    assert template.front_cover is None  # original not mutated
