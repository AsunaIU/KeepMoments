"""Per-page caption generation, parallelized across pages.

Two backends, selected by the caller via ``base_url``:
- ``base_url=None`` → Anthropic SDK (AsyncAnthropic)
- ``base_url=str``  → OpenRouter / OpenAI-compatible (httpx.AsyncClient)

Per-page LLM calls are issued concurrently via asyncio.gather + Semaphore.
For an album of N pages and per-call latency ~Lms, total caption time drops
from N×L (sequential) to roughly L (parallel, capped by ``max_concurrent``).
"""
import asyncio
import base64
import logging
from io import BytesIO
from typing import Any

from PIL import Image

from app.schemas import FilledPage, FilledTemplate

logger = logging.getLogger(__name__)

_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
_DEFAULT_MAX_CONCURRENT = 5
_HTTP_TIMEOUT_S = 30.0


# ---------------------------------------------------------------------------
# Client construction (separate functions so tests can patch precisely)
# ---------------------------------------------------------------------------

def _build_anthropic_client(api_key: str) -> Any:
    import anthropic  # noqa: PLC0415 — lazy import; package is optional
    return anthropic.AsyncAnthropic(api_key=api_key)


def _build_httpx_client() -> Any:
    import httpx  # noqa: PLC0415 — lazy import
    return httpx.AsyncClient(timeout=_HTTP_TIMEOUT_S)


# ---------------------------------------------------------------------------
# Image encoding (Win 2: takes already-decoded PIL.Image)
# ---------------------------------------------------------------------------

def _encode_image(image: Image.Image) -> tuple[str, str]:
    """Return (base64-encoded JPEG ≤512px, media_type). Does not mutate input."""
    work = image.copy()
    work.thumbnail((512, 512))
    buf = BytesIO()
    work.save(buf, format="JPEG", quality=85)
    return base64.standard_b64encode(buf.getvalue()).decode(), "image/jpeg"


def _build_prompt(page_id: str, page_index: int, total_pages: int, user_description: str) -> str:
    return (
        f"You are writing captions for a photo album.\n"
        f"Overall theme: {user_description}\n"
        f"This is page {page_index + 1} of {total_pages} (id: {page_id}).\n\n"
        f"Look at the photo(s) on this page and write a single short caption "
        f"(1-2 sentences, max 25 words) that captures the mood in the context "
        f"of the album theme. Respond with only the caption text, no quotes, no preamble."
    )


# ---------------------------------------------------------------------------
# Backend calls
# ---------------------------------------------------------------------------

async def _call_anthropic(
    client: Any, model: str, encoded_images: list[tuple[str, str]], prompt_text: str,
) -> str:
    content: list[dict[str, Any]] = [
        {"type": "image", "source": {"type": "base64", "media_type": mt, "data": b64}}
        for b64, mt in encoded_images
    ]
    content.append({"type": "text", "text": prompt_text})
    response = await client.messages.create(
        model=model,
        max_tokens=60,
        messages=[{"role": "user", "content": content}],
    )
    return response.content[0].text.strip()


async def _call_openrouter(
    client: Any, api_key: str, base_url: str, model: str,
    encoded_images: list[tuple[str, str]], prompt_text: str,
) -> str:
    content: list[dict[str, Any]] = [
        {"type": "image_url", "image_url": {"url": f"data:{mt};base64,{b64}"}}
        for b64, mt in encoded_images
    ]
    content.append({"type": "text", "text": prompt_text})
    resp = await client.post(
        f"{base_url}/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={"model": model, "messages": [{"role": "user", "content": content}], "max_tokens": 60},
    )
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"].strip()


# ---------------------------------------------------------------------------
# Per-page caption
# ---------------------------------------------------------------------------

async def _caption_single_page(
    page: FilledPage,
    *,
    page_index: int,
    total_pages: int,
    images: dict[str, Image.Image],
    user_description: str,
    api_key: str,
    model: str,
    base_url: str | None,
    client: Any,
) -> str | None:
    page_photo_ids = [s.photo_id for s in page.slots if s.photo_id is not None]
    if not page_photo_ids:
        return None

    try:
        encoded_images: list[tuple[str, str]] = []
        for photo_id in page_photo_ids:
            img = images.get(photo_id)
            if img is None:
                continue
            try:
                encoded_images.append(_encode_image(img))
            except Exception as exc:
                logger.warning("Could not encode photo %s: %s", photo_id, exc)

        if not encoded_images:
            logger.warning("Page %s: all photos failed to encode; caption skipped.", page.id)
            return None

        prompt_text = _build_prompt(page.id, page_index, total_pages, user_description)

        if base_url is None:
            return await _call_anthropic(client, model, encoded_images, prompt_text)
        return await _call_openrouter(client, api_key, base_url, model, encoded_images, prompt_text)

    except Exception as exc:
        logger.warning("Caption generation failed for page %s: %s", page.id, exc)
        return None


# ---------------------------------------------------------------------------
# Top-level entry points
# ---------------------------------------------------------------------------

async def generate_captions(
    filled_template: FilledTemplate,
    images: dict[str, Image.Image],
    user_description: str,
    api_key: str,
    model: str,
    base_url: str | None = None,
    max_concurrent: int = _DEFAULT_MAX_CONCURRENT,
) -> FilledTemplate:
    """Attach captions to each page in parallel. Returns a new FilledTemplate."""
    if not filled_template.pages:
        return filled_template

    is_anthropic = base_url is None
    client = _build_anthropic_client(api_key) if is_anthropic else _build_httpx_client()

    sem = asyncio.Semaphore(max_concurrent)
    total_pages = len(filled_template.pages)

    async def task(idx: int, page: FilledPage) -> str | None:
        async with sem:
            return await _caption_single_page(
                page,
                page_index=idx,
                total_pages=total_pages,
                images=images,
                user_description=user_description,
                api_key=api_key,
                model=model,
                base_url=base_url,
                client=client,
            )

    try:
        captions = await asyncio.gather(
            *(task(i, p) for i, p in enumerate(filled_template.pages))
        )
    finally:
        if not is_anthropic:
            await client.aclose()

    return FilledTemplate(
        id=filled_template.id,
        pages=[
            FilledPage(id=p.id, slots=p.slots, caption=c)
            for p, c in zip(filled_template.pages, captions)
        ],
        front_cover=filled_template.front_cover,
        back_cover=filled_template.back_cover,
    )


async def generate_cover_caption(
    ranked_photos: list[str],
    images: dict[str, Image.Image],
    user_description: str,
    api_key: str,
    model: str,
    base_url: str | None = None,
    max_photos: int = 5,
) -> str | None:
    """Generate a title for the album cover from a representative sample of photos."""
    if not ranked_photos:
        return None

    encoded_images: list[tuple[str, str]] = []
    for photo_id in ranked_photos[:max_photos]:
        img = images.get(photo_id)
        if img is None:
            continue
        try:
            encoded_images.append(_encode_image(img))
        except Exception as exc:
            logger.warning("Could not encode photo %s for cover caption: %s", photo_id, exc)

    if not encoded_images:
        return None

    prompt_text = (
        f"You are creating a title for a photo album.\n"
        f"Overall theme: {user_description}\n\n"
        f"Look at these representative photos and write a short, evocative album title "
        f"(max 10 words). Respond with only the title text, no quotes, no preamble."
    )

    is_anthropic = base_url is None
    client = _build_anthropic_client(api_key) if is_anthropic else _build_httpx_client()

    try:
        if is_anthropic:
            return await _call_anthropic(client, model, encoded_images, prompt_text)
        return await _call_openrouter(client, api_key, base_url, model, encoded_images, prompt_text)
    except Exception as exc:
        logger.warning("Cover caption generation failed: %s", exc)
        return None
    finally:
        if not is_anthropic:
            await client.aclose()
