from enum import Enum

from pydantic import BaseModel, Field, model_validator


class Slot(BaseModel):
    id: str
    photo_id: str | None = None


class Page(BaseModel):
    id: str
    slots: list[Slot]


class CoverMode(str, Enum):
    photo = "photo"      # cover shows a photo
    caption = "caption"  # cover shows text


class CoverConfig(BaseModel):
    mode: CoverMode = CoverMode.caption
    photo_id: str | None = None   # mode=photo: manual photo (None → auto-select from ranked)
    text: str | None = None       # mode=caption: manual text (None → auto-generate)


class Template(BaseModel):
    id: str
    pages: list[Page]
    front_cover: CoverConfig | None = None
    back_cover: CoverConfig | None = None


class ProcessRequest(BaseModel):
    photo_ids: list[str]
    user_description: str
    min_photos: int = Field(ge=1)
    max_photos: int = Field(ge=1)
    template: Template

    @model_validator(mode="after")
    def validate_min_max(self) -> "ProcessRequest":
        if self.min_photos > self.max_photos:
            raise ValueError("min_photos must be <= max_photos")
        return self


class FilledSlot(BaseModel):
    id: str
    photo_id: str | None = None


class FilledPage(BaseModel):
    id: str
    slots: list[FilledSlot]
    caption: str | None = None


class FilledCover(BaseModel):
    mode: CoverMode
    photo_id: str | None = None   # populated when mode=photo
    text: str | None = None       # populated when mode=caption


class FilledTemplate(BaseModel):
    id: str
    pages: list[FilledPage]
    front_cover: FilledCover | None = None
    back_cover: FilledCover | None = None


class ProcessResponse(BaseModel):
    filled_template: FilledTemplate
