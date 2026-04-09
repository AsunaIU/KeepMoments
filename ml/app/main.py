import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from app.config import Settings, get_settings
from app.dependencies import get_clip_model_dep, get_s3_client
from app.pipeline import run_pipeline
from app.pipeline.embeddings import get_clip_model
from app.schemas import ProcessRequest, ProcessResponse

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    model, preprocess = get_clip_model(settings.CLIP_MODEL_NAME)
    app.state.clip_model = model
    app.state.clip_preprocess = preprocess
    yield


app = FastAPI(title="KeepMoments ML Service", version="0.1.0", lifespan=lifespan)


@app.post("/process", response_model=ProcessResponse)
async def process(
    request: ProcessRequest,
    settings: Settings = Depends(get_settings),
    s3_client=Depends(get_s3_client),
    clip=Depends(get_clip_model_dep),
) -> ProcessResponse:
    clip_model, clip_preprocess = clip
    filled = await run_pipeline(request, settings, s3_client, clip_model, clip_preprocess)
    return ProcessResponse(filled_template=filled)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
