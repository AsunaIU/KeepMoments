import pytest

from app.config import Settings, get_settings


@pytest.fixture(autouse=True)
def clear_cache():
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


def test_required_fields_via_constructor():
    s = Settings(
        AWS_ACCESS_KEY_ID="my_key",
        AWS_SECRET_ACCESS_KEY="my_secret",
        S3_BUCKET_NAME="my-bucket",
    )
    assert s.AWS_ACCESS_KEY_ID == "my_key"
    assert s.AWS_SECRET_ACCESS_KEY == "my_secret"
    assert s.S3_BUCKET_NAME == "my-bucket"


def test_default_values():
    s = Settings(
        AWS_ACCESS_KEY_ID="k",
        AWS_SECRET_ACCESS_KEY="s",
        S3_BUCKET_NAME="b",
        _env_file=None,
    )
    assert s.AWS_REGION == "us-east-1"
    assert s.CLIP_MODEL_NAME == "ViT-B/32"
    assert s.KMEANS_RANDOM_STATE == 42
    assert s.LOG_LEVEL == "INFO"
    assert s.S3_ENDPOINT_URL is None


def test_custom_values_override_defaults():
    s = Settings(
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
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "env_key")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "env_secret")
    monkeypatch.setenv("S3_BUCKET_NAME", "env_bucket")
    monkeypatch.setenv("AWS_REGION", "ap-southeast-1")
    s = Settings()
    assert s.AWS_REGION == "ap-southeast-1"
    assert s.AWS_ACCESS_KEY_ID == "env_key"


def test_get_settings_returns_same_instance(monkeypatch):
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "k")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "s")
    monkeypatch.setenv("S3_BUCKET_NAME", "b")
    s1 = get_settings()
    s2 = get_settings()
    assert s1 is s2
