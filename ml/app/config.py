from functools import lru_cache
from typing import Literal

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    PHOTO_SOURCE: Literal["backend", "s3"] = "backend"

    BACKEND_BASE_URL: str | None = None
    BACKEND_TIMEOUT: float = 30.0

    AWS_ACCESS_KEY_ID: str | None = None
    AWS_SECRET_ACCESS_KEY: str | None = None
    AWS_REGION: str = "us-east-1"
    S3_BUCKET_NAME: str | None = None
    S3_ENDPOINT_URL: str | None = None  # set to http://minio:9000 for local

    CLIP_MODEL_NAME: str = "ViT-B/32"
    KMEANS_RANDOM_STATE: int = 42
    LOG_LEVEL: str = "INFO"

    ANTHROPIC_API_KEY: str | None = None  # absent → caption generation skipped
    ANTHROPIC_MODEL: str = "claude-haiku-4-5-20251001"

    OPENROUTER_API_KEY: str | None = None  # if set, takes priority over Anthropic
    OPENROUTER_MODEL: str = "google/gemini-flash-1.5"

    @model_validator(mode="after")
    def _validate_photo_source(self) -> "Settings":
        if self.PHOTO_SOURCE == "backend":
            if not self.BACKEND_BASE_URL:
                raise ValueError("BACKEND_BASE_URL is required when PHOTO_SOURCE=backend")
        else:  # "s3"
            missing = [
                name for name, val in (
                    ("AWS_ACCESS_KEY_ID", self.AWS_ACCESS_KEY_ID),
                    ("AWS_SECRET_ACCESS_KEY", self.AWS_SECRET_ACCESS_KEY),
                    ("S3_BUCKET_NAME", self.S3_BUCKET_NAME),
                ) if not val
            ]
            if missing:
                raise ValueError(
                    f"PHOTO_SOURCE=s3 requires {', '.join(missing)}"
                )
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
