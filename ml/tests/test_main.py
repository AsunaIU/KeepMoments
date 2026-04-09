from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.dependencies import get_clip_model_dep
from app.main import app
from app.schemas import FilledPage, FilledSlot, FilledTemplate
from tests.conftest import make_template


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

_SETTINGS = Settings(
    AWS_ACCESS_KEY_ID="test_key",
    AWS_SECRET_ACCESS_KEY="test_secret",
    S3_BUCKET_NAME="test-bucket",
)

_FILLED = FilledTemplate(
    id="template_1",
    pages=[
        FilledPage(
            id="page_0",
            slots=[
                FilledSlot(id="slot_0_0", photo_id="p1"),
                FilledSlot(id="slot_0_1", photo_id="p2"),
                FilledSlot(id="slot_0_2", photo_id="p3"),
            ],
        )
    ],
)


_MOCK_CLIP = (MagicMock(), MagicMock())


@pytest.fixture
def client():
    app.dependency_overrides[get_settings] = lambda: _SETTINGS
    app.dependency_overrides[get_clip_model_dep] = lambda: _MOCK_CLIP
    with patch("app.main.get_clip_model", return_value=_MOCK_CLIP):
        with TestClient(app) as c:
            yield c
    app.dependency_overrides.clear()


def _valid_payload(min_photos: int = 1, max_photos: int = 5) -> dict:
    t = make_template(n_pages=1, slots_per_page=3)
    return {
        "photo_ids": ["p1", "p2", "p3"],
        "user_description": "a sunny beach",
        "min_photos": min_photos,
        "max_photos": max_photos,
        "template": t.model_dump(),
    }


# ---------------------------------------------------------------------------
# /health
# ---------------------------------------------------------------------------

def test_health_returns_ok(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


# ---------------------------------------------------------------------------
# POST /process — success
# ---------------------------------------------------------------------------

def test_process_valid_request_returns_200(client):
    with patch("app.main.run_pipeline", new_callable=AsyncMock, return_value=_FILLED):
        resp = client.post("/process", json=_valid_payload())
    assert resp.status_code == 200
    body = resp.json()
    assert "filled_template" in body
    assert body["filled_template"]["id"] == "template_1"


def test_process_response_contains_photo_ids(client):
    with patch("app.main.run_pipeline", new_callable=AsyncMock, return_value=_FILLED):
        resp = client.post("/process", json=_valid_payload())
    slots = resp.json()["filled_template"]["pages"][0]["slots"]
    assert slots[0]["photo_id"] == "p1"
    assert slots[1]["photo_id"] == "p2"


# ---------------------------------------------------------------------------
# POST /process — validation errors
# ---------------------------------------------------------------------------

def test_process_invalid_json_returns_422(client):
    resp = client.post("/process", content=b"not json at all", headers={"Content-Type": "application/json"})
    assert resp.status_code == 422


def test_process_min_greater_than_max_returns_422(client):
    payload = _valid_payload(min_photos=5, max_photos=2)
    resp = client.post("/process", json=payload)
    assert resp.status_code == 422


def test_process_missing_required_field_returns_422(client):
    payload = _valid_payload()
    del payload["photo_ids"]
    resp = client.post("/process", json=payload)
    assert resp.status_code == 422


# ---------------------------------------------------------------------------
# POST /process — pipeline errors propagated
# ---------------------------------------------------------------------------

def test_process_pipeline_503_propagated(client):
    with patch(
        "app.main.run_pipeline",
        new_callable=AsyncMock,
        side_effect=HTTPException(status_code=503, detail="No photos"),
    ):
        resp = client.post("/process", json=_valid_payload())
    assert resp.status_code == 503


def test_process_pipeline_422_propagated(client):
    with patch(
        "app.main.run_pipeline",
        new_callable=AsyncMock,
        side_effect=HTTPException(status_code=422, detail="No slots"),
    ):
        resp = client.post("/process", json=_valid_payload())
    assert resp.status_code == 422
