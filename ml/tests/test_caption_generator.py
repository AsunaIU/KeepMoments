"""Tests for caption_generator.

Win 2: functions accept dict[str, PIL.Image] instead of dict[str, bytes].
Win 1: functions are async and per-page calls run concurrently.
"""
import asyncio
import base64
import io
import time
from unittest.mock import AsyncMock, MagicMock, patch

from PIL import Image

from app.pipeline.caption_generator import (
    _OPENROUTER_BASE_URL,
    _caption_single_page,
    _encode_image,
    generate_captions,
    generate_cover_caption,
)
from app.schemas import FilledPage, FilledSlot, FilledTemplate
from tests.conftest import make_pil_image


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _anthropic_response(text: str) -> MagicMock:
    resp = MagicMock()
    resp.content = [MagicMock(text=text)]
    return resp


def _openrouter_response(text: str) -> MagicMock:
    resp = MagicMock()
    resp.json.return_value = {"choices": [{"message": {"content": text}}]}
    resp.raise_for_status = MagicMock()
    return resp


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


def _make_anthropic_mock(create_return=None, create_side_effect=None) -> MagicMock:
    client = MagicMock()
    client.messages.create = AsyncMock(
        return_value=create_return,
        side_effect=create_side_effect,
    )
    return client


def _make_httpx_mock(post_return=None, post_side_effect=None) -> MagicMock:
    client = MagicMock()
    client.post = AsyncMock(return_value=post_return, side_effect=post_side_effect)
    client.aclose = AsyncMock()
    return client


# ---------------------------------------------------------------------------
# _encode_image — now takes PIL.Image (Win 2)
# ---------------------------------------------------------------------------

def test_encode_image_returns_base64_jpeg():
    b64, media_type = _encode_image(make_pil_image())
    assert media_type == "image/jpeg"
    raw = base64.standard_b64decode(b64)
    assert raw[:2] == b"\xff\xd8"  # JPEG magic bytes


def test_encode_image_large_image_is_resized():
    big = make_pil_image(width=1024, height=768)
    b64, _ = _encode_image(big)
    img = Image.open(io.BytesIO(base64.standard_b64decode(b64)))
    assert max(img.size) <= 512


def test_encode_image_does_not_mutate_original():
    """Encoding for caption must not shrink the cached PIL.Image used elsewhere."""
    img = make_pil_image(width=1024, height=768)
    _encode_image(img)
    assert img.size == (1024, 768)


# ---------------------------------------------------------------------------
# _caption_single_page (async)
# ---------------------------------------------------------------------------

_PAGE = FilledPage(id="page_0", slots=[FilledSlot(id="slot_0", photo_id="photo_A")])
_IMAGES = {"photo_A": make_pil_image()}

_BASE_KWARGS = dict(
    page_index=0,
    total_pages=2,
    images=_IMAGES,
    user_description="summer wedding",
    api_key="test-key",
    model="claude-haiku-4-5-20251001",
)


async def test_caption_single_page_anthropic_happy_path():
    client = _make_anthropic_mock(create_return=_anthropic_response("  A sunny wedding day.  "))
    result = await _caption_single_page(_PAGE, **_BASE_KWARGS, base_url=None, client=client)
    assert result == "A sunny wedding day."


async def test_caption_single_page_no_photo_ids_returns_none():
    page = FilledPage(id="page_0", slots=[FilledSlot(id="slot_0", photo_id=None)])
    client = _make_anthropic_mock()
    result = await _caption_single_page(page, **_BASE_KWARGS, base_url=None, client=client)
    client.messages.create.assert_not_called()
    assert result is None


async def test_caption_single_page_missing_image_skips():
    page = FilledPage(
        id="page_0",
        slots=[
            FilledSlot(id="slot_0", photo_id="photo_MISSING"),
            FilledSlot(id="slot_1", photo_id="photo_A"),
        ],
    )
    client = _make_anthropic_mock(create_return=_anthropic_response("Caption."))
    result = await _caption_single_page(page, **_BASE_KWARGS, base_url=None, client=client)
    assert result == "Caption."
    content = client.messages.create.call_args.kwargs["messages"][0]["content"]
    assert len([b for b in content if b["type"] == "image"]) == 1


async def test_caption_single_page_all_photos_missing_returns_none():
    client = _make_anthropic_mock()
    kwargs = {**_BASE_KWARGS, "images": {}}
    result = await _caption_single_page(_PAGE, **kwargs, base_url=None, client=client)
    client.messages.create.assert_not_called()
    assert result is None


async def test_caption_single_page_anthropic_api_error_returns_none():
    client = _make_anthropic_mock(create_side_effect=RuntimeError("connection failed"))
    result = await _caption_single_page(_PAGE, **_BASE_KWARGS, base_url=None, client=client)
    assert result is None


async def test_caption_single_page_anthropic_content_order():
    page = FilledPage(
        id="page_0",
        slots=[
            FilledSlot(id="slot_0", photo_id="photo_A"),
            FilledSlot(id="slot_1", photo_id="photo_B"),
        ],
    )
    images = {"photo_A": make_pil_image(color=(255, 0, 0)), "photo_B": make_pil_image(color=(0, 255, 0))}
    client = _make_anthropic_mock(create_return=_anthropic_response("Two photos."))
    await _caption_single_page(
        page,
        page_index=0,
        total_pages=1,
        images=images,
        user_description="family reunion",
        api_key="k",
        model="m",
        base_url=None,
        client=client,
    )
    content = client.messages.create.call_args.kwargs["messages"][0]["content"]
    assert content[0]["type"] == "image"
    assert content[1]["type"] == "image"
    assert content[2]["type"] == "text"


# OpenRouter backend
_OR_KWARGS = dict(
    page_index=0,
    total_pages=2,
    images=_IMAGES,
    user_description="summer wedding",
    api_key="or-key",
    model="google/gemini-flash-1.5",
)


async def test_caption_single_page_openrouter_happy_path():
    client = _make_httpx_mock(post_return=_openrouter_response("A sunny wedding day."))
    result = await _caption_single_page(
        _PAGE, **_OR_KWARGS, base_url=_OPENROUTER_BASE_URL, client=client,
    )
    assert result == "A sunny wedding day."


async def test_caption_single_page_openrouter_uses_image_url_format():
    client = _make_httpx_mock(post_return=_openrouter_response("Caption."))
    await _caption_single_page(_PAGE, **_OR_KWARGS, base_url=_OPENROUTER_BASE_URL, client=client)
    content = client.post.call_args.kwargs["json"]["messages"][0]["content"]
    image_blocks = [b for b in content if b["type"] == "image_url"]
    assert len(image_blocks) == 1
    assert image_blocks[0]["image_url"]["url"].startswith("data:image/jpeg;base64,")


async def test_caption_single_page_openrouter_posts_to_correct_url():
    client = _make_httpx_mock(post_return=_openrouter_response("Caption."))
    await _caption_single_page(_PAGE, **_OR_KWARGS, base_url=_OPENROUTER_BASE_URL, client=client)
    url = client.post.call_args.args[0]
    assert url == f"{_OPENROUTER_BASE_URL}/chat/completions"


async def test_caption_single_page_openrouter_api_error_returns_none():
    client = _make_httpx_mock(post_side_effect=RuntimeError("timeout"))
    result = await _caption_single_page(_PAGE, **_OR_KWARGS, base_url=_OPENROUTER_BASE_URL, client=client)
    assert result is None


# ---------------------------------------------------------------------------
# generate_captions
# ---------------------------------------------------------------------------

async def test_generate_captions_anthropic_happy_path():
    template = _make_filled_template(n_pages=2)
    images = {f"photo_{i}_0": make_pil_image() for i in range(2)}
    client = _make_anthropic_mock(create_return=_anthropic_response("A caption."))

    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        result = await generate_captions(template, images, "theme", api_key="k", model="m")

    assert result.pages[0].caption == "A caption."
    assert result.pages[1].caption == "A caption."


async def test_generate_captions_openrouter_happy_path():
    template = _make_filled_template(n_pages=2)
    images = {f"photo_{i}_0": make_pil_image() for i in range(2)}
    client = _make_httpx_mock(post_return=_openrouter_response("A caption."))

    with patch("app.pipeline.caption_generator._build_httpx_client", return_value=client):
        result = await generate_captions(
            template, images, "theme",
            api_key="ok", model="m", base_url=_OPENROUTER_BASE_URL,
        )
    assert result.pages[0].caption == "A caption."
    assert result.pages[1].caption == "A caption."
    client.aclose.assert_awaited_once()


async def test_generate_captions_partial_failure():
    template = _make_filled_template(n_pages=2)
    images = {f"photo_{i}_0": make_pil_image() for i in range(2)}
    client = _make_anthropic_mock(
        create_side_effect=[_anthropic_response("Good caption."), RuntimeError("API down")]
    )

    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        result = await generate_captions(template, images, "theme", api_key="k", model="m")

    captions = [p.caption for p in result.pages]
    assert "Good caption." in captions
    assert None in captions


async def test_generate_captions_empty_pages():
    template = FilledTemplate(id="t1", pages=[])
    with patch("app.pipeline.caption_generator._build_anthropic_client") as mock_build:
        result = await generate_captions(template, {}, "theme", api_key="k", model="m")
    mock_build.assert_not_called()
    assert result.pages == []


async def test_generate_captions_returns_new_object():
    template = _make_filled_template(n_pages=1)
    images = {"photo_0_0": make_pil_image()}
    client = _make_anthropic_mock(create_return=_anthropic_response("Caption."))

    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        result = await generate_captions(template, images, "theme", api_key="k", model="m")

    assert result is not template
    assert template.pages[0].caption is None  # original not mutated


async def test_generate_captions_runs_pages_in_parallel():
    """Win 1: per-page calls run concurrently rather than sequentially."""
    n_pages = 4
    template = _make_filled_template(n_pages=n_pages)
    images = {f"photo_{i}_0": make_pil_image() for i in range(n_pages)}

    delay = 0.1

    async def slow_create(*args, **kwargs):
        await asyncio.sleep(delay)
        return _anthropic_response("Caption")

    client = MagicMock()
    client.messages.create = AsyncMock(side_effect=slow_create)

    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        start = time.monotonic()
        result = await generate_captions(template, images, "theme", api_key="k", model="m")
        elapsed = time.monotonic() - start

    # 4 parallel calls @ delay each must complete near `delay`, far below n_pages × delay.
    assert elapsed < delay * n_pages * 0.6, f"Expected parallel ≈{delay}s, got {elapsed}s"
    assert all(p.caption == "Caption" for p in result.pages)


async def test_generate_captions_respects_concurrency_limit():
    """Semaphore bounds the number of in-flight LLM calls."""
    n_pages = 10
    limit = 3
    template = _make_filled_template(n_pages=n_pages)
    images = {f"photo_{i}_0": make_pil_image() for i in range(n_pages)}

    in_flight = 0
    peak = 0

    async def tracked_create(*args, **kwargs):
        nonlocal in_flight, peak
        in_flight += 1
        peak = max(peak, in_flight)
        await asyncio.sleep(0.02)
        in_flight -= 1
        return _anthropic_response("Caption")

    client = MagicMock()
    client.messages.create = AsyncMock(side_effect=tracked_create)

    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        await generate_captions(
            template, images, "theme", api_key="k", model="m", max_concurrent=limit,
        )

    assert peak <= limit, f"Concurrency exceeded limit: peaked at {peak}"


# ---------------------------------------------------------------------------
# generate_cover_caption
# ---------------------------------------------------------------------------

_RANKED = ["photo_0", "photo_1", "photo_2", "photo_3", "photo_4", "photo_5"]
_COVER_IMAGES = {pid: make_pil_image() for pid in _RANKED}


async def test_cover_caption_anthropic_happy_path():
    client = _make_anthropic_mock(create_return=_anthropic_response("  Summer Love  "))
    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        result = await generate_cover_caption(
            _RANKED, _COVER_IMAGES, "summer wedding", api_key="k", model="m",
        )
    assert result == "Summer Love"


async def test_cover_caption_openrouter_happy_path():
    client = _make_httpx_mock(post_return=_openrouter_response("Summer Love"))
    with patch("app.pipeline.caption_generator._build_httpx_client", return_value=client):
        result = await generate_cover_caption(
            _RANKED, _COVER_IMAGES, "summer wedding",
            api_key="ok", model="m", base_url=_OPENROUTER_BASE_URL,
        )
    assert result == "Summer Love"
    client.aclose.assert_awaited_once()


async def test_cover_caption_limits_to_max_photos():
    client = _make_anthropic_mock(create_return=_anthropic_response("Title."))
    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        await generate_cover_caption(
            _RANKED, _COVER_IMAGES, "theme", api_key="k", model="m", max_photos=3,
        )
    content = client.messages.create.call_args.kwargs["messages"][0]["content"]
    image_blocks = [b for b in content if b["type"] == "image"]
    assert len(image_blocks) == 3


async def test_cover_caption_empty_ranked_returns_none():
    with patch("app.pipeline.caption_generator._build_anthropic_client") as mock_build:
        result = await generate_cover_caption([], {}, "theme", api_key="k", model="m")
    mock_build.assert_not_called()
    assert result is None


async def test_cover_caption_all_photos_missing_returns_none():
    with patch("app.pipeline.caption_generator._build_anthropic_client") as mock_build:
        result = await generate_cover_caption(_RANKED, {}, "theme", api_key="k", model="m")
    mock_build.assert_not_called()
    assert result is None


async def test_cover_caption_api_failure_returns_none():
    client = _make_anthropic_mock(create_side_effect=RuntimeError("API down"))
    with patch("app.pipeline.caption_generator._build_anthropic_client", return_value=client):
        result = await generate_cover_caption(
            _RANKED, _COVER_IMAGES, "theme", api_key="k", model="m",
        )
    assert result is None
