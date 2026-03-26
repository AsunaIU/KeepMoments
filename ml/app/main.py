import logging

from fastapi import Depends, FastAPI

from app.config import Settings, get_settings
from app.pipeline import run_pipeline
from app.schemas import ProcessRequest, ProcessResponse

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="KeepMoments ML Service", version="0.1.0")


@app.post("/process", response_model=ProcessResponse)
async def process(
    request: ProcessRequest,
    settings: Settings = Depends(get_settings),
) -> ProcessResponse:
    filled = await run_pipeline(request, settings)
    return ProcessResponse(filled_template=filled)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
