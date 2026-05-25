import asyncio
import logging
from dataclasses import dataclass
from functools import partial
from typing import Any

from PIL import Image
from fastapi import HTTPException

from app.config import Settings
from app.schemas import FilledTemplate, ProcessRequest
from app.pipeline.backend_loader import download_photos_from_backend
from app.pipeline.caption_generator import _OPENROUTER_BASE_URL, generate_captions
from app.pipeline.clustering import cluster_photos
from app.pipeline.cover_filler import fill_covers
from app.pipeline.embeddings import extract_embeddings
from app.pipeline.image_cache import decode_images
from app.pipeline.orientation import detect_orientation
from app.pipeline.quality import score_quality
from app.pipeline.reranker import rerank_by_text
from app.pipeline.s3_loader import download_photos
from app.pipeline.selector import select_photos
from app.pipeline.template_filler import count_template_slots, fill_template

logger = logging.getLogger(__name__)


@dataclass
class _PipelineState:
    """Intermediate state produced by the sync part of the pipeline (steps 1-9)."""
    images: dict[str, Image.Image]
    ranked: list[str]
    filled: FilledTemplate


def _resolve_caption_backend(settings: Settings) -> tuple[str | None, str | None, str | None]:
    """Pick (api_key, model, base_url) for caption/cover LLM calls. None if disabled."""
    if settings.OPENROUTER_API_KEY:
        return settings.OPENROUTER_API_KEY, settings.OPENROUTER_MODEL, _OPENROUTER_BASE_URL
    if settings.ANTHROPIC_API_KEY:
        return settings.ANTHROPIC_API_KEY, settings.ANTHROPIC_MODEL, None
    return None, None, None


async def _fetch_photo_bytes(
    request: ProcessRequest,
    settings: Settings,
    s3_client,
    http_client: Any | None,
    download_executor,
    auth_token: str | None = None,
) -> dict[str, bytes]:
    """Dispatch to the configured photo source and return raw bytes per photo_id."""
    if settings.PHOTO_SOURCE == "backend":
        return await download_photos_from_backend(
            request.photo_ids,
            settings.BACKEND_BASE_URL,
            client=http_client,
            timeout=settings.BACKEND_TIMEOUT,
            auth_token=auth_token,
        )

    loop = asyncio.get_event_loop()
    fn = partial(
        download_photos,
        request.photo_ids,
        settings.S3_BUCKET_NAME,
        s3_client,
        executor=download_executor,
    )
    return await loop.run_in_executor(None, fn)


async def run_pipeline(
    request: ProcessRequest,
    settings: Settings,
    s3_client,
    clip_model,
    clip_preprocess,
    download_executor=None,
    http_client: Any | None = None,
    auth_token: str | None = None,
) -> FilledTemplate:
    photo_bytes = await _fetch_photo_bytes(
        request, settings, s3_client, http_client, download_executor,
        auth_token=auth_token,
    )

    loop = asyncio.get_event_loop()
    fn = partial(
        _run_pipeline_sync,
        request, settings, photo_bytes, clip_model, clip_preprocess,
    )
    state = await loop.run_in_executor(None, fn)

    api_key, model, base_url = _resolve_caption_backend(settings)
    filled = state.filled

    if api_key:
        filled = await generate_captions(
            filled_template=filled,
            images=state.images,
            user_description=request.user_description,
            api_key=api_key,
            model=model,
            base_url=base_url,
        )

    if request.template.front_cover is not None or request.template.back_cover is not None:
        filled = await fill_covers(
            filled_template=filled,
            front_config=request.template.front_cover,
            back_config=request.template.back_cover,
            ranked_photos=state.ranked,
            images=state.images,
            user_description=request.user_description,
            api_key=api_key,
            model=model,
            base_url=base_url,
        )

    return filled


def _run_pipeline_sync(
    request: ProcessRequest,
    settings: Settings,
    photo_bytes: dict[str, bytes],
    clip_model,
    clip_preprocess,
) -> _PipelineState:
    # 1a. Decode each photo to PIL.Image once (Win 2 — single-pass decode)
    images = decode_images(photo_bytes)
    # Release encoded bytes — subsequent steps work on decoded images.
    photo_bytes.clear()

    if not images:
        raise HTTPException(status_code=503, detail="No photos could be downloaded")

    if len(images) < request.min_photos:
        raise HTTPException(
            status_code=422,
            detail=f"Only {len(images)} photos available, but min_photos={request.min_photos}",
        )

    # 1b. Detect photo orientations
    orientations = detect_orientation(images)

    # 2. Count template slots
    n_slots = count_template_slots(request.template)
    if n_slots == 0:
        raise HTTPException(status_code=422, detail="Template has no slots to fill")

    # 3. Determine how many photos to select
    n_select = max(request.min_photos, min(n_slots, request.max_photos))
    n_select = min(n_select, len(images))

    # 4. Extract CLIP embeddings (chunked internally)
    embeddings = extract_embeddings(images, clip_model, clip_preprocess)

    # 5. Cluster
    n_clusters = min(len(embeddings), n_select)
    clusters = cluster_photos(embeddings, n_clusters, settings.KMEANS_RANDOM_STATE)

    # 6. Score quality
    quality_scores = score_quality(images)

    # 7. Round-robin select across clusters
    selected = select_photos(clusters, quality_scores, n_select)

    # 8. Re-rank by CLIP text similarity with user description
    ranked = rerank_by_text(selected, embeddings, request.user_description, clip_model)

    # 9. Fill template slots in document order
    filled = fill_template(request.template, ranked, orientations=orientations)

    return _PipelineState(images=images, ranked=ranked, filled=filled)
