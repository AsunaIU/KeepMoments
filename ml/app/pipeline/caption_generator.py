"""Per-page caption generation.

Supports two backends selected by the caller via `base_url`:
- base_url=None  → Anthropic SDK (direct API)
- base_url=str   → OpenRouter (or any OpenAI-compatible endpoint) via httpx
"""
import base64
import logging
from io import BytesIO
from typing import Any

from PIL import Image

from app.schemas import FilledPage, FilledTemplate

logger = logging.getLogger(__name__)

_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"

_anthropic_client: Any = None
_httpx_client: Any = None


def _get_anthropic_client(api_key: str) -> Any:
    global _anthropic_client
    if _anthropic_client is None:
        import anthropic  # noqa: PLC0415 — lazy import, package is optional
        _anthropic_client = anthropic.Anthropic(api_key=api_key)
    return _anthropic_client


def _get_httpx_client() -> Any:
    global _httpx_client
    if _httpx_client is None:
        import httpx  # noqa: PLC0415 — lazy import
        _httpx_client = httpx.Client(timeout=30.0)
    return _httpx_client


def _encode_image(image_bytes: bytes) -> tuple[str, str]:
    """Convert any PIL-readable image to JPEG ≤512px, return (base64_data, media_type)."""
    img = Image.open(BytesIO(image_bytes)).convert("RGB")
    img.thumbnail((512, 512))
    buf = BytesIO()
    img.save(buf, format="JPEG", quality=85)
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


def _call_anthropic(
    client: Any,
    model: str,
    encoded_images: list[tuple[str, str]],
    prompt_text: str,
) -> str:
    content: list[dict[str, Any]] = [
        {"type": "image", "source": {"type": "base64", "media_type": mt, "data": b64}}
        for b64, mt in encoded_images
    ]
    content.append({"type": "text", "text": prompt_text})
    response = client.messages.create(
        model=model,
        max_tokens=60,
        messages=[{"role": "user", "content": content}],
    )
    return response.content[0].text.strip()


def _call_openrouter(
    client: Any,
    api_key: str,
    base_url: str,
    model: str,
    encoded_images: list[tuple[str, str]],
    prompt_text: str,
) -> str:
    content: list[dict[str, Any]] = [
        {"type": "image_url", "image_url": {"url": f"data:{mt};base64,{b64}"}}
        for b64, mt in encoded_images
    ]
    content.append({"type": "text", "text": prompt_text})
    resp = client.post(
        f"{base_url}/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={"model": model, "messages": [{"role": "user", "content": content}], "max_tokens": 60},
    )
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"].strip()


def _caption_single_page(
    page: FilledPage,
    page_index: int,
    total_pages: int,
    photo_bytes: dict[str, bytes],
    user_description: str,
    api_key: str,
    model: str,
    base_url: str | None,
) -> str | None:
    page_photo_ids = [s.photo_id for s in page.slots if s.photo_id is not None]
    if not page_photo_ids:
        return None

    try:
        encoded_images: list[tuple[str, str]] = []
        for photo_id in page_photo_ids:
            raw = photo_bytes.get(photo_id)
            if raw is None:
                continue
            try:
                encoded_images.append(_encode_image(raw))
            except Exception as exc:
                logger.warning("Could not encode photo %s: %s", photo_id, exc)

        if not encoded_images:
            logger.warning("Page %s: all photos failed to encode; caption skipped.", page.id)
            return None

        prompt_text = _build_prompt(page.id, page_index, total_pages, user_description)

        if base_url is None:
            caption = _call_anthropic(_get_anthropic_client(api_key), model, encoded_images, prompt_text)
        else:
            caption = _call_openrouter(_get_httpx_client(), api_key, base_url, model, encoded_images, prompt_text)

        logger.debug("Caption for page %s: %r", page.id, caption)
        return caption

    except Exception as exc:
        logger.warning("Caption generation failed for page %s: %s", page.id, exc)
        return None


def generate_captions(
    filled_template: FilledTemplate,
    photo_bytes: dict[str, bytes],
    user_description: str,
    api_key: str,
    model: str,
    base_url: str | None = None,
) -> FilledTemplate:
    """Attach captions to each page. Returns a new FilledTemplate; input not mutated.

    base_url=None  → Anthropic direct API
    base_url=str   → OpenRouter or any OpenAI-compatible endpoint
    """
    total_pages = len(filled_template.pages)
    updated_pages = []

    for page_index, page in enumerate(filled_template.pages):
        caption = _caption_single_page(
            page=page,
            page_index=page_index,
            total_pages=total_pages,
            photo_bytes=photo_bytes,
            user_description=user_description,
            api_key=api_key,
            model=model,
            base_url=base_url,
        )
        updated_pages.append(FilledPage(id=page.id, slots=page.slots, caption=caption))

    return FilledTemplate(id=filled_template.id, pages=updated_pages)


def generate_cover_caption(
    ranked_photos: list[str],
    photo_bytes: dict[str, bytes],
    user_description: str,
    api_key: str,
    model: str,
    base_url: str | None = None,
    max_photos: int = 5,
) -> str | None:
    """Generate a title for the album cover using a representative sample of photos.

    base_url=None  → Anthropic direct API
    base_url=str   → OpenRouter or any OpenAI-compatible endpoint
    """
    if not ranked_photos:
        return None

    encoded_images: list[tuple[str, str]] = []
    for photo_id in ranked_photos[:max_photos]:
        raw = photo_bytes.get(photo_id)
        if raw is None:
            continue
        try:
            encoded_images.append(_encode_image(raw))
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

    try:
        if base_url is None:
            return _call_anthropic(_get_anthropic_client(api_key), model, encoded_images, prompt_text)
        else:
            return _call_openrouter(_get_httpx_client(), api_key, base_url, model, encoded_images, prompt_text)
    except Exception as exc:
        logger.warning("Cover caption generation failed: %s", exc)
        return None
