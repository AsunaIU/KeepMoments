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

_S3_SETTINGS = Settings(
    PHOTO_SOURCE="s3",
    AWS_ACCESS_KEY_ID="test_key",
    AWS_SECRET_ACCESS_KEY="test_secret",
    S3_BUCKET_NAME="test-bucket",
)

_BACKEND_SETTINGS = Settings(
    PHOTO_SOURCE="backend",
    BACKEND_BASE_URL="http://backend.test",
    BACKEND_EMAIL="ml@test",
    BACKEND_PASSWORD="secret",
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
    app.dependency_overrides[get_settings] = lambda: _S3_SETTINGS
    app.dependency_overrides[get_clip_model_dep] = lambda: _MOCK_CLIP
    with patch("app.main.get_settings", return_value=_S3_SETTINGS), \
         patch("app.main.get_clip_model", return_value=_MOCK_CLIP), \
         patch("app.main.boto3.client", return_value=MagicMock()):
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


def test_process_forwards_http_client_into_pipeline(client):
    """The /process handler injects app.state.http_client into run_pipeline."""
    with patch("app.main.run_pipeline", new_callable=AsyncMock, return_value=_FILLED) as mock_pipe:
        client.post("/process", json=_valid_payload())
    kwargs = mock_pipe.call_args.kwargs
    assert "http_client" in kwargs
    assert kwargs["http_client"] is app.state.http_client


def test_process_forwards_backend_auth_into_pipeline(client):
    """The /process handler injects app.state.backend_auth into run_pipeline."""
    with patch("app.main.run_pipeline", new_callable=AsyncMock, return_value=_FILLED) as mock_pipe:
        client.post("/process", json=_valid_payload())
    kwargs = mock_pipe.call_args.kwargs
    assert "auth" in kwargs
    # In S3 mode (fixture), backend_auth is None.
    assert kwargs["auth"] is app.state.backend_auth


def test_process_ignores_authorization_header(client):
    """Authorization header on /process is no longer used — ML uses its own service token."""
    with patch("app.main.run_pipeline", new_callable=AsyncMock, return_value=_FILLED) as mock_pipe:
        client.post(
            "/process",
            json=_valid_payload(),
            headers={"Authorization": "Bearer user-token-xyz"},
        )
    # No auth_token kwarg should be passed anymore
    assert "auth_token" not in mock_pipe.call_args.kwargs


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


# ---------------------------------------------------------------------------
# Lifespan — S3 mode (Win 5)
# ---------------------------------------------------------------------------

def test_lifespan_s3_mode_creates_client_and_executor_once():
    """In S3 mode, boto3 client and download executor are created once on startup."""
    from concurrent.futures import ThreadPoolExecutor

    app.dependency_overrides[get_settings] = lambda: _S3_SETTINGS
    fake_s3 = MagicMock(name="s3-client")
    with patch("app.main.get_settings", return_value=_S3_SETTINGS), \
         patch("app.main.get_clip_model", return_value=_MOCK_CLIP), \
         patch("app.main.boto3.client", return_value=fake_s3) as mock_boto3:
        with TestClient(app):
            assert app.state.s3_client is fake_s3
            assert isinstance(app.state.download_executor, ThreadPoolExecutor)
            assert app.state.clip_model is _MOCK_CLIP[0]
            assert app.state.http_client is not None
            # S3 mode does NOT create a backend auth client
            assert app.state.backend_auth is None
        assert mock_boto3.call_count == 1
    app.dependency_overrides.clear()


def test_lifespan_s3_mode_shuts_down_executor_on_exit():
    """The download executor is shut down when the app exits."""
    from concurrent.futures import ThreadPoolExecutor

    app.dependency_overrides[get_settings] = lambda: _S3_SETTINGS
    captured: dict = {}
    with patch("app.main.get_settings", return_value=_S3_SETTINGS), \
         patch("app.main.get_clip_model", return_value=_MOCK_CLIP), \
         patch("app.main.boto3.client", return_value=MagicMock()):
        with TestClient(app):
            captured["executor"] = app.state.download_executor
            assert isinstance(captured["executor"], ThreadPoolExecutor)

    import pytest
    with pytest.raises(RuntimeError):
        captured["executor"].submit(lambda: 1)
    app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# Lifespan — backend mode
# ---------------------------------------------------------------------------

def test_lifespan_backend_mode_skips_s3_client_and_executor():
    """In backend mode, no boto3 client or download executor is created."""
    app.dependency_overrides[get_settings] = lambda: _BACKEND_SETTINGS
    with patch("app.main.get_settings", return_value=_BACKEND_SETTINGS), \
         patch("app.main.get_clip_model", return_value=_MOCK_CLIP), \
         patch("app.main.boto3.client") as mock_boto3:
        with TestClient(app):
            assert app.state.s3_client is None
            assert app.state.download_executor is None
            assert app.state.http_client is not None
        mock_boto3.assert_not_called()
    app.dependency_overrides.clear()


def test_lifespan_backend_mode_creates_backend_auth_client():
    """In backend mode, lifespan constructs a BackendAuthClient on app.state."""
    from app.pipeline.backend_auth import BackendAuthClient

    app.dependency_overrides[get_settings] = lambda: _BACKEND_SETTINGS
    with patch("app.main.get_settings", return_value=_BACKEND_SETTINGS), \
         patch("app.main.get_clip_model", return_value=_MOCK_CLIP):
        with TestClient(app):
            assert isinstance(app.state.backend_auth, BackendAuthClient)
    app.dependency_overrides.clear()


def test_lifespan_creates_http_client_in_both_modes():
    """http_client is always created (used for caption gen too) regardless of source."""
    for settings in (_S3_SETTINGS, _BACKEND_SETTINGS):
        app.dependency_overrides[get_settings] = lambda s=settings: s
        with patch("app.main.get_settings", return_value=settings), \
             patch("app.main.get_clip_model", return_value=_MOCK_CLIP), \
             patch("app.main.boto3.client", return_value=MagicMock()):
            with TestClient(app):
                assert app.state.http_client is not None
        app.dependency_overrides.clear()


def test_lifespan_closes_http_client_on_exit():
    """The async http_client is closed when the app shuts down."""
    app.dependency_overrides[get_settings] = lambda: _BACKEND_SETTINGS
    captured: dict = {}
    with patch("app.main.get_settings", return_value=_BACKEND_SETTINGS), \
         patch("app.main.get_clip_model", return_value=_MOCK_CLIP):
        with TestClient(app):
            captured["http_client"] = app.state.http_client
    assert captured["http_client"].is_closed
    app.dependency_overrides.clear()
