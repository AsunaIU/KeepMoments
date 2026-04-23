import asyncio
import logging
from functools import partial

from fastapi import HTTPException

from app.config import Settings
from app.schemas import FilledTemplate, ProcessRequest
from app.pipeline.s3_loader import download_photos
from app.pipeline.embeddings import extract_embeddings
from app.pipeline.clustering import cluster_photos
from app.pipeline.quality import score_quality
from app.pipeline.selector import select_photos
from app.pipeline.reranker import rerank_by_text
from app.pipeline.template_filler import count_template_slots, fill_template
from app.pipeline.caption_generator import generate_captions, _OPENROUTER_BASE_URL
from app.pipeline.cover_filler import fill_covers

logger = logging.getLogger(__name__)


async def run_pipeline(
    request: ProcessRequest, settings: Settings, s3_client, clip_model, clip_preprocess
) -> FilledTemplate:
    loop = asyncio.get_event_loop()
    fn = partial(_run_pipeline_sync, request, settings, s3_client, clip_model, clip_preprocess)
    return await loop.run_in_executor(None, fn)


def _run_pipeline_sync(
    request: ProcessRequest, settings: Settings, s3_client, clip_model, clip_preprocess
) -> FilledTemplate:
    # 1. Download photos from S3
    photo_bytes = download_photos(request.photo_ids, settings.S3_BUCKET_NAME, s3_client)

    if not photo_bytes:
        raise HTTPException(status_code=503, detail="No photos could be downloaded from S3")

    if len(photo_bytes) < request.min_photos:
        raise HTTPException(
            status_code=422,
            detail=f"Only {len(photo_bytes)} photos available, but min_photos={request.min_photos}",
        )

    # 2. Count template slots
    n_slots = count_template_slots(request.template)
    if n_slots == 0:
        raise HTTPException(status_code=422, detail="Template has no slots to fill")

    # 3. Determine how many photos to select
    n_select = max(request.min_photos, min(n_slots, request.max_photos))
    n_select = min(n_select, len(photo_bytes))

    # 4. Extract CLIP embeddings
    embeddings = extract_embeddings(photo_bytes, clip_model, clip_preprocess)

    # 5. Cluster
    n_clusters = min(len(embeddings), n_select)
    clusters = cluster_photos(embeddings, n_clusters, settings.KMEANS_RANDOM_STATE)

    # 6. Score quality
    quality_scores = score_quality(photo_bytes)

    # 7. Select best n_select photos via round-robin across clusters
    selected = select_photos(clusters, quality_scores, n_select)

    # 8. Re-rank by CLIP text similarity with user description
    ranked = rerank_by_text(selected, embeddings, request.user_description, clip_model)

    # 9. Fill template slots in document order
    filled = fill_template(request.template, ranked)

    # 10. Generate per-page captions (skipped if no caption API key is configured)
    if settings.OPENROUTER_API_KEY:
        filled = generate_captions(
            filled_template=filled,
            photo_bytes=photo_bytes,
            user_description=request.user_description,
            api_key=settings.OPENROUTER_API_KEY,
            model=settings.OPENROUTER_MODEL,
            base_url=_OPENROUTER_BASE_URL,
        )
    elif settings.ANTHROPIC_API_KEY:
        filled = generate_captions(
            filled_template=filled,
            photo_bytes=photo_bytes,
            user_description=request.user_description,
            api_key=settings.ANTHROPIC_API_KEY,
            model=settings.ANTHROPIC_MODEL,
        )

    # 11. Fill covers (optional, skipped if template has no covers)
    if request.template.front_cover is not None or request.template.back_cover is not None:
        if settings.OPENROUTER_API_KEY:
            caption_api_key = settings.OPENROUTER_API_KEY
            caption_model = settings.OPENROUTER_MODEL
            caption_base_url = _OPENROUTER_BASE_URL
        elif settings.ANTHROPIC_API_KEY:
            caption_api_key = settings.ANTHROPIC_API_KEY
            caption_model = settings.ANTHROPIC_MODEL
            caption_base_url = None
        else:
            caption_api_key = None
            caption_model = None
            caption_base_url = None

        filled = fill_covers(
            filled_template=filled,
            front_config=request.template.front_cover,
            back_config=request.template.back_cover,
            ranked_photos=ranked,
            photo_bytes=photo_bytes,
            user_description=request.user_description,
            api_key=caption_api_key,
            model=caption_model,
            base_url=caption_base_url,
        )

    return filled
