import pytest
from pydantic import ValidationError

from app.config import Settings, get_settings


_SETTINGS_ENV_VARS = (
    "PHOTO_SOURCE",
    "BACKEND_BASE_URL",
    "BACKEND_TIMEOUT",
    "BACKEND_EMAIL",
    "BACKEND_PASSWORD",
    "AWS_ACCESS_KEY_ID",
    "AWS_SECRET_ACCESS_KEY",
    "AWS_REGION",
    "S3_BUCKET_NAME",
    "S3_ENDPOINT_URL",
    "CLIP_MODEL_NAME",
    "KMEANS_RANDOM_STATE",
    "LOG_LEVEL",
    "ANTHROPIC_API_KEY",
    "ANTHROPIC_MODEL",
    "OPENROUTER_API_KEY",
    "OPENROUTER_MODEL",
)


@pytest.fixture(autouse=True)
def isolate_env(monkeypatch):
    for var in _SETTINGS_ENV_VARS:
        monkeypatch.delenv(var, raising=False)
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


def test_required_fields_via_constructor():
    s = Settings(
        PHOTO_SOURCE="s3",
        AWS_ACCESS_KEY_ID="my_key",
        AWS_SECRET_ACCESS_KEY="my_secret",
        S3_BUCKET_NAME="my-bucket",
    )
    assert s.AWS_ACCESS_KEY_ID == "my_key"
    assert s.AWS_SECRET_ACCESS_KEY == "my_secret"
    assert s.S3_BUCKET_NAME == "my-bucket"


def test_default_values():
    s = Settings(
        PHOTO_SOURCE="s3",
        AWS_ACCESS_KEY_ID="k",
        AWS_SECRET_ACCESS_KEY="s",
        S3_BUCKET_NAME="b",
    )
    assert s.AWS_REGION == "us-east-1"
    assert s.CLIP_MODEL_NAME == "ViT-B/32"
    assert s.KMEANS_RANDOM_STATE == 42
    assert s.LOG_LEVEL == "INFO"
    assert s.S3_ENDPOINT_URL is None


def test_custom_values_override_defaults():
    s = Settings(
        PHOTO_SOURCE="s3",
        AWS_ACCESS_KEY_ID="k",
        AWS_SECRET_ACCESS_KEY="s",
        S3_BUCKET_NAME="b",
        AWS_REGION="eu-west-1",
        CLIP_MODEL_NAME="ViT-L/14",
        KMEANS_RANDOM_STATE=99,
        S3_ENDPOINT_URL="http://localhost:9000",
    )
    assert s.AWS_REGION == "eu-west-1"
    assert s.CLIP_MODEL_NAME == "ViT-L/14"
    assert s.KMEANS_RANDOM_STATE == 99
    assert s.S3_ENDPOINT_URL == "http://localhost:9000"


def test_env_var_overrides_default(monkeypatch):
    monkeypatch.setenv("PHOTO_SOURCE", "s3")
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "env_key")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "env_secret")
    monkeypatch.setenv("S3_BUCKET_NAME", "env_bucket")
    monkeypatch.setenv("AWS_REGION", "ap-southeast-1")
    s = Settings()
    assert s.AWS_REGION == "ap-southeast-1"
    assert s.AWS_ACCESS_KEY_ID == "env_key"


def test_get_settings_returns_same_instance(monkeypatch):
    monkeypatch.setenv("PHOTO_SOURCE", "s3")
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "k")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "s")
    monkeypatch.setenv("S3_BUCKET_NAME", "b")
    s1 = get_settings()
    s2 = get_settings()
    assert s1 is s2


# ---------------------------------------------------------------------------
# PHOTO_SOURCE validator
# ---------------------------------------------------------------------------

def test_photo_source_defaults_to_backend():
    s = Settings(
        BACKEND_BASE_URL="http://backend.test",
        BACKEND_EMAIL="ml@test",
        BACKEND_PASSWORD="secret",
    )
    assert s.PHOTO_SOURCE == "backend"


def test_backend_mode_happy_path():
    s = Settings(
        PHOTO_SOURCE="backend",
        BACKEND_BASE_URL="http://backend:8000",
        BACKEND_EMAIL="ml@test",
        BACKEND_PASSWORD="secret",
        BACKEND_TIMEOUT=15.0,
    )
    assert s.PHOTO_SOURCE == "backend"
    assert s.BACKEND_BASE_URL == "http://backend:8000"
    assert s.BACKEND_EMAIL == "ml@test"
    assert s.BACKEND_PASSWORD == "secret"
    assert s.BACKEND_TIMEOUT == 15.0


def test_backend_mode_without_url_raises():
    with pytest.raises(ValidationError) as exc_info:
        Settings(
            PHOTO_SOURCE="backend",
            BACKEND_EMAIL="ml@test",
            BACKEND_PASSWORD="secret",
        )
    assert "BACKEND_BASE_URL" in str(exc_info.value)


def test_backend_mode_without_credentials_raises():
    with pytest.raises(ValidationError) as exc_info:
        Settings(
            PHOTO_SOURCE="backend",
            BACKEND_BASE_URL="http://backend:8000",
        )
    msg = str(exc_info.value)
    assert "BACKEND_EMAIL" in msg
    assert "BACKEND_PASSWORD" in msg


def test_backend_mode_with_partial_credentials_raises():
    with pytest.raises(ValidationError) as exc_info:
        Settings(
            PHOTO_SOURCE="backend",
            BACKEND_BASE_URL="http://backend:8000",
            BACKEND_EMAIL="ml@test",
        )
    assert "BACKEND_PASSWORD" in str(exc_info.value)


def test_s3_mode_without_credentials_raises():
    with pytest.raises(ValidationError) as exc_info:
        Settings(PHOTO_SOURCE="s3")
    msg = str(exc_info.value)
    assert "AWS_ACCESS_KEY_ID" in msg
    assert "AWS_SECRET_ACCESS_KEY" in msg
    assert "S3_BUCKET_NAME" in msg


def test_s3_mode_with_partial_credentials_raises():
    with pytest.raises(ValidationError) as exc_info:
        Settings(
            PHOTO_SOURCE="s3",
            AWS_ACCESS_KEY_ID="k",
            AWS_SECRET_ACCESS_KEY="s",
        )
    assert "S3_BUCKET_NAME" in str(exc_info.value)


def test_invalid_photo_source_raises():
    with pytest.raises(ValidationError):
        Settings(PHOTO_SOURCE="ftp")


def test_backend_timeout_defaults_to_30_seconds():
    s = Settings(
        BACKEND_BASE_URL="http://b",
        BACKEND_EMAIL="ml@test",
        BACKEND_PASSWORD="secret",
    )
    assert s.BACKEND_TIMEOUT == 30.0
