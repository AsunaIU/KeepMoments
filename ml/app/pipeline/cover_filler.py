"""Fill front/back covers of a photobook template."""
import logging

from app.pipeline.caption_generator import generate_cover_caption
from app.schemas import CoverConfig, CoverMode, FilledCover, FilledTemplate

logger = logging.getLogger(__name__)


def _fill_single_cover(
    config: CoverConfig,
    ranked_photos: list[str],
    photo_bytes: dict[str, bytes],
    user_description: str,
    api_key: str | None,
    model: str | None,
    base_url: str | None,
    is_front: bool,
) -> FilledCover:
    if config.mode == CoverMode.photo:
        if config.photo_id is not None:
            return FilledCover(mode=config.mode, photo_id=config.photo_id)
        # Auto-select: front → best (first ranked), back → last ranked
        photo_id = None
        if ranked_photos:
            photo_id = ranked_photos[0] if is_front else ranked_photos[-1]
        return FilledCover(mode=config.mode, photo_id=photo_id)

    # caption mode
    if config.text is not None:
        return FilledCover(mode=config.mode, text=config.text)

    # Auto-generate caption
    text = None
    if api_key and model:
        text = generate_cover_caption(
            ranked_photos=ranked_photos,
            photo_bytes=photo_bytes,
            user_description=user_description,
            api_key=api_key,
            model=model,
            base_url=base_url,
        )
    return FilledCover(mode=config.mode, text=text)


def fill_covers(
    filled_template: FilledTemplate,
    front_config: CoverConfig | None,
    back_config: CoverConfig | None,
    ranked_photos: list[str],
    photo_bytes: dict[str, bytes],
    user_description: str,
    api_key: str | None = None,
    model: str | None = None,
    base_url: str | None = None,
) -> FilledTemplate:
    """Return a new FilledTemplate with covers attached; input not mutated."""
    front = (
        _fill_single_cover(
            config=front_config,
            ranked_photos=ranked_photos,
            photo_bytes=photo_bytes,
            user_description=user_description,
            api_key=api_key,
            model=model,
            base_url=base_url,
            is_front=True,
        )
        if front_config is not None else None
    )
    back = (
        _fill_single_cover(
            config=back_config,
            ranked_photos=ranked_photos,
            photo_bytes=photo_bytes,
            user_description=user_description,
            api_key=api_key,
            model=model,
            base_url=base_url,
            is_front=False,
        )
        if back_config is not None else None
    )
    return FilledTemplate(
        id=filled_template.id,
        pages=filled_template.pages,
        front_cover=front,
        back_cover=back,
    )
