from pydantic import BaseModel, Field, model_validator


class Slot(BaseModel):
    id: str
    photo_id: str | None = None


class Page(BaseModel):
    id: str
    slots: list[Slot]


class Template(BaseModel):
    id: str
    pages: list[Page]


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


class FilledTemplate(BaseModel):
    id: str
    pages: list[FilledPage]


class ProcessResponse(BaseModel):
    filled_template: FilledTemplate
