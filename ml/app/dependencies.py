from concurrent.futures import Executor
from typing import Any

import boto3
from botocore.config import Config
from fastapi import Request

from app.config import Settings


def get_clip_model_dep(request: Request):
    return request.app.state.clip_model, request.app.state.clip_preprocess


def get_s3_client(request: Request):
    return request.app.state.s3_client


def get_download_executor(request: Request) -> Executor | None:
    return request.app.state.download_executor


def get_http_client(request: Request) -> Any:
    return request.app.state.http_client


def build_s3_client(settings: Settings):
    """Construct a boto3 S3 client. Called once at app startup (lifespan)."""
    return boto3.client(
        "s3",
        region_name=settings.AWS_REGION,
        aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
        aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
        endpoint_url=settings.S3_ENDPOINT_URL,
        config=Config(signature_version="s3v4"),
    )
