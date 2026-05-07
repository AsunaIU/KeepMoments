import logging
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager

import boto3
from botocore.config import Config
from fastapi import Depends, FastAPI

from app.config import Settings, get_settings
from app.dependencies import (
    get_clip_model_dep,
    get_download_executor,
    get_s3_client,
)
from app.pipeline import run_pipeline
from app.pipeline.embeddings import get_clip_model
from app.schemas import ProcessRequest, ProcessResponse

_DOWNLOAD_POOL_SIZE = 16


def _configure_logging(level: str) -> None:
    logging.basicConfig(level=getattr(logging, level.upper(), logging.INFO), force=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    _configure_logging(settings.LOG_LEVEL)

    model, preprocess = get_clip_model(settings.CLIP_MODEL_NAME)
    app.state.clip_model = model
    app.state.clip_preprocess = preprocess

    app.state.s3_client = boto3.client(
        "s3",
        region_name=settings.AWS_REGION,
        aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
        aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
        endpoint_url=settings.S3_ENDPOINT_URL,
        config=Config(signature_version="s3v4"),
    )

    app.state.download_executor = ThreadPoolExecutor(
        max_workers=_DOWNLOAD_POOL_SIZE,
        thread_name_prefix="s3-download",
    )

    try:
        yield
    finally:
        app.state.download_executor.shutdown(wait=True)


app = FastAPI(title="KeepMoments ML Service", version="0.1.0", lifespan=lifespan)


@app.post("/process", response_model=ProcessResponse)
async def process(
    request: ProcessRequest,
    settings: Settings = Depends(get_settings),
    s3_client=Depends(get_s3_client),
    clip=Depends(get_clip_model_dep),
    download_executor=Depends(get_download_executor),
) -> ProcessResponse:
    clip_model, clip_preprocess = clip
    filled = await run_pipeline(
        request, settings, s3_client, clip_model, clip_preprocess,
        download_executor=download_executor,
    )
    return ProcessResponse(filled_template=filled)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
