import base64
import io
from unittest.mock import MagicMock, patch

import pytest
from PIL import Image

import app.pipeline.caption_generator as mod
from app.pipeline.caption_generator import (
    _OPENROUTER_BASE_URL,
    _caption_single_page,
    _encode_image,
    generate_captions,
    generate_cover_caption,
)
from app.schemas import FilledPage, FilledSlot, FilledTemplate


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _png_bytes(width: int = 64, height: int = 64, color=(100, 150, 200)) -> bytes:
    img = Image.new("RGB", (width, height), color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _rgba_png_bytes() -> bytes:
    img = Image.new("RGBA", (32, 32), (100, 150, 200, 128))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _make_filled_template(n_pages: int = 2, slots_per_page: int = 1) -> FilledTemplate:
    pages = [
        FilledPage(
            id=f"page_{i}",
            slots=[FilledSlot(id=f"slot_{i}_{j}", photo_id=f"photo_{i}_{j}")
                   for j in range(slots_per_page)],
        )
        for i in range(n_pages)
    ]
    return FilledTemplate(id="template_1", pages=pages)


def _anthropic_response(text: str) -> MagicMock:
    resp = MagicMock()
    resp.content = [MagicMock(text=text)]
    return resp


def _openrouter_response(text: str) -> MagicMock:
    resp = MagicMock()
    resp.json.return_value = {"choices": [{"message": {"content": text}}]}
    return resp


# ---------------------------------------------------------------------------
# Fixture: reset module-level singletons between tests
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def reset_clients():
    mod._anthropic_client = None
    mod._httpx_client = None
    yield
    mod._anthropic_client = None
    mod._httpx_client = None


# ---------------------------------------------------------------------------
# _encode_image tests
# ---------------------------------------------------------------------------

def test_encode_image_returns_base64_jpeg():
    b64, media_type = _encode_image(_png_bytes())
    assert media_type == "image/jpeg"
    raw = base64.standard_b64decode(b64)
    assert raw[:2] == b"\xff\xd8"  # JPEG magic bytes


def test_encode_image_rgba_converts_to_rgb():
    b64, _ = _encode_image(_rgba_png_bytes())
    raw = base64.standard_b64decode(b64)
    img = Image.open(io.BytesIO(raw))
    assert img.mode == "RGB"


def test_encode_image_large_image_is_resized():
    b64, _ = _encode_image(_png_bytes(width=1024, height=768))
    raw = base64.standard_b64decode(b64)
    img = Image.open(io.BytesIO(raw))
    assert max(img.size) <= 512


def test_encode_image_invalid_bytes_raises():
    with pytest.raises(Exception):
        _encode_image(b"not an image")


# ---------------------------------------------------------------------------
# _caption_single_page — Anthropic backend (base_url=None)
# ---------------------------------------------------------------------------

_PAGE = FilledPage(id="page_0", slots=[FilledSlot(id="slot_0", photo_id="photo_A")])
_PHOTO_BYTES = {"photo_A": _png_bytes()}

_ANTHROPIC_KWARGS = dict(
    page_index=0,
    total_pages=2,
    photo_bytes=_PHOTO_BYTES,
    user_description="summer wedding",
    api_key="test-key",
    model="claude-haiku-4-5-20251001",
    base_url=None,
)


def test_caption_single_page_anthropic_happy_path():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.return_value = _anthropic_response("  A sunny wedding day.  ")
        result = _caption_single_page(page=_PAGE, **_ANTHROPIC_KWARGS)
    assert result == "A sunny wedding day."


def test_caption_single_page_no_photo_ids_returns_none():
    page = FilledPage(id="page_0", slots=[FilledSlot(id="slot_0", photo_id=None)])
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        result = _caption_single_page(page=page, **_ANTHROPIC_KWARGS)
    mock_get.assert_not_called()
    assert result is None


def test_caption_single_page_missing_photo_bytes_skips_photo():
    page = FilledPage(
        id="page_0",
        slots=[
            FilledSlot(id="slot_0", photo_id="photo_MISSING"),
            FilledSlot(id="slot_1", photo_id="photo_A"),
        ],
    )
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.return_value = _anthropic_response("Caption.")
        result = _caption_single_page(page=page, **_ANTHROPIC_KWARGS)
    assert result == "Caption."
    content = mock_get.return_value.messages.create.call_args.kwargs["messages"][0]["content"]
    assert len([b for b in content if b["type"] == "image"]) == 1


def test_caption_single_page_all_photos_missing_returns_none():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        result = _caption_single_page(page=_PAGE, **{**_ANTHROPIC_KWARGS, "photo_bytes": {}})
    mock_get.return_value.messages.create.assert_not_called()
    assert result is None


def test_caption_single_page_encode_failure_returns_none():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        with patch("app.pipeline.caption_generator._encode_image", side_effect=ValueError("bad")):
            result = _caption_single_page(page=_PAGE, **_ANTHROPIC_KWARGS)
    mock_get.return_value.messages.create.assert_not_called()
    assert result is None


def test_caption_single_page_api_error_returns_none():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.side_effect = RuntimeError("connection failed")
        result = _caption_single_page(page=_PAGE, **_ANTHROPIC_KWARGS)
    assert result is None


def test_caption_single_page_anthropic_content_order():
    """Images come before the text prompt in the Anthropic content list."""
    page = FilledPage(
        id="page_0",
        slots=[
            FilledSlot(id="slot_0", photo_id="photo_A"),
            FilledSlot(id="slot_1", photo_id="photo_B"),
        ],
    )
    photo_bytes = {"photo_A": _png_bytes(color=(255, 0, 0)), "photo_B": _png_bytes(color=(0, 255, 0))}
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.return_value = _anthropic_response("Two photos.")
        _caption_single_page(
            page=page,
            page_index=0,
            total_pages=1,
            photo_bytes=photo_bytes,
            user_description="family reunion",
            api_key="test-key",
            model="claude-haiku-4-5-20251001",
            base_url=None,
        )
    content = mock_get.return_value.messages.create.call_args.kwargs["messages"][0]["content"]
    assert content[0]["type"] == "image"
    assert content[1]["type"] == "image"
    assert content[2]["type"] == "text"


# ---------------------------------------------------------------------------
# _caption_single_page — OpenRouter backend (base_url set)
# ---------------------------------------------------------------------------

_OPENROUTER_KWARGS = dict(
    page_index=0,
    total_pages=2,
    photo_bytes=_PHOTO_BYTES,
    user_description="summer wedding",
    api_key="or-test-key",
    model="google/gemini-flash-1.5",
    base_url=_OPENROUTER_BASE_URL,
)


def test_caption_single_page_openrouter_happy_path():
    with patch("app.pipeline.caption_generator._get_httpx_client") as mock_get:
        mock_get.return_value.post.return_value = _openrouter_response("A sunny wedding day.")
        result = _caption_single_page(page=_PAGE, **_OPENROUTER_KWARGS)
    assert result == "A sunny wedding day."


def test_caption_single_page_openrouter_uses_image_url_format():
    """OpenRouter messages use image_url blocks, not Anthropic's image+source format."""
    with patch("app.pipeline.caption_generator._get_httpx_client") as mock_get:
        mock_get.return_value.post.return_value = _openrouter_response("Caption.")
        _caption_single_page(page=_PAGE, **_OPENROUTER_KWARGS)
    call_kwargs = mock_get.return_value.post.call_args.kwargs
    content = call_kwargs["json"]["messages"][0]["content"]
    image_blocks = [b for b in content if b["type"] == "image_url"]
    assert len(image_blocks) == 1
    assert image_blocks[0]["image_url"]["url"].startswith("data:image/jpeg;base64,")


def test_caption_single_page_openrouter_posts_to_correct_url():
    with patch("app.pipeline.caption_generator._get_httpx_client") as mock_get:
        mock_get.return_value.post.return_value = _openrouter_response("Caption.")
        _caption_single_page(page=_PAGE, **_OPENROUTER_KWARGS)
    url = mock_get.return_value.post.call_args.args[0]
    assert url == f"{_OPENROUTER_BASE_URL}/chat/completions"


def test_caption_single_page_openrouter_api_error_returns_none():
    with patch("app.pipeline.caption_generator._get_httpx_client") as mock_get:
        mock_get.return_value.post.side_effect = RuntimeError("timeout")
        result = _caption_single_page(page=_PAGE, **_OPENROUTER_KWARGS)
    assert result is None


# ---------------------------------------------------------------------------
# generate_captions tests
# ---------------------------------------------------------------------------

def test_generate_captions_anthropic_happy_path():
    template = _make_filled_template(n_pages=2)
    photo_bytes = {f"photo_{i}_0": _png_bytes() for i in range(2)}
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.return_value = _anthropic_response("A caption.")
        result = generate_captions(template, photo_bytes, "theme", api_key="key", model="claude-haiku-4-5-20251001")
    assert result.pages[0].caption == "A caption."
    assert result.pages[1].caption == "A caption."


def test_generate_captions_openrouter_happy_path():
    template = _make_filled_template(n_pages=2)
    photo_bytes = {f"photo_{i}_0": _png_bytes() for i in range(2)}
    with patch("app.pipeline.caption_generator._get_httpx_client") as mock_get:
        mock_get.return_value.post.return_value = _openrouter_response("A caption.")
        result = generate_captions(
            template, photo_bytes, "theme",
            api_key="or-key", model="google/gemini-flash-1.5", base_url=_OPENROUTER_BASE_URL,
        )
    assert result.pages[0].caption == "A caption."
    assert result.pages[1].caption == "A caption."


def test_generate_captions_partial_failure():
    template = _make_filled_template(n_pages=2)
    photo_bytes = {f"photo_{i}_0": _png_bytes() for i in range(2)}
    responses = [_anthropic_response("Good caption."), Exception("API down")]
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.side_effect = responses
        result = generate_captions(template, photo_bytes, "theme", api_key="key", model="claude-haiku-4-5-20251001")
    assert result.pages[0].caption == "Good caption."
    assert result.pages[1].caption is None


def test_generate_captions_empty_pages():
    template = FilledTemplate(id="t1", pages=[])
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        result = generate_captions(template, {}, "theme", api_key="key", model="m")
    mock_get.assert_not_called()
    assert result.pages == []


def test_generate_captions_returns_new_object():
    template = _make_filled_template(n_pages=1)
    photo_bytes = {"photo_0_0": _png_bytes()}
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.return_value = _anthropic_response("Caption.")
        result = generate_captions(template, photo_bytes, "theme", api_key="key", model="m")
    assert result is not template
    assert template.pages[0].caption is None  # original not mutated


# ---------------------------------------------------------------------------
# generate_cover_caption tests
# ---------------------------------------------------------------------------

_RANKED = ["photo_0", "photo_1", "photo_2", "photo_3", "photo_4", "photo_5"]
_COVER_PHOTO_BYTES = {pid: _png_bytes() for pid in _RANKED}


def test_cover_caption_anthropic_happy_path():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.return_value = _anthropic_response("  Summer Love  ")
        result = generate_cover_caption(
            _RANKED, _COVER_PHOTO_BYTES, "summer wedding", api_key="key", model="m"
        )
    assert result == "Summer Love"


def test_cover_caption_openrouter_happy_path():
    with patch("app.pipeline.caption_generator._get_httpx_client") as mock_get:
        mock_get.return_value.post.return_value = _openrouter_response("Summer Love")
        result = generate_cover_caption(
            _RANKED, _COVER_PHOTO_BYTES, "summer wedding",
            api_key="or-key", model="google/gemini-flash-1.5", base_url=_OPENROUTER_BASE_URL,
        )
    assert result == "Summer Love"


def test_cover_caption_limits_to_max_photos():
    """Only first max_photos images should be sent."""
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.return_value = _anthropic_response("Title.")
        generate_cover_caption(
            _RANKED, _COVER_PHOTO_BYTES, "theme",
            api_key="key", model="m", max_photos=3,
        )
    content = mock_get.return_value.messages.create.call_args.kwargs["messages"][0]["content"]
    image_blocks = [b for b in content if b["type"] == "image"]
    assert len(image_blocks) == 3


def test_cover_caption_empty_ranked_returns_none():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        result = generate_cover_caption([], {}, "theme", api_key="key", model="m")
    mock_get.assert_not_called()
    assert result is None


def test_cover_caption_all_photos_missing_returns_none():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        result = generate_cover_caption(_RANKED, {}, "theme", api_key="key", model="m")
    mock_get.return_value.messages.create.assert_not_called()
    assert result is None


def test_cover_caption_api_failure_returns_none():
    with patch("app.pipeline.caption_generator._get_anthropic_client") as mock_get:
        mock_get.return_value.messages.create.side_effect = RuntimeError("API down")
        result = generate_cover_caption(
            _RANKED, _COVER_PHOTO_BYTES, "theme", api_key="key", model="m"
        )
    assert result is None
