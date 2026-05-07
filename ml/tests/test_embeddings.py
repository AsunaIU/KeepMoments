from unittest.mock import MagicMock, patch

import numpy as np
import pytest
import torch

import app.pipeline.embeddings as emb_module
from app.pipeline.embeddings import extract_embeddings, get_clip_model
from tests.conftest import make_pil_image


@pytest.fixture(autouse=True)
def clear_model_cache():
    emb_module._model_cache.clear()
    yield
    emb_module._model_cache.clear()


def _make_clip_mocks(n_images: int = 1, embed_dim: int = 512):
    """Return (mock_model, mock_preprocess) that produce sensible tensors."""
    mock_preprocess = MagicMock(side_effect=lambda img: torch.zeros(3, 224, 224))
    mock_model = MagicMock()
    # encode_image returns random unit vectors
    features = torch.randn(n_images, embed_dim)
    features = features / features.norm(dim=-1, keepdim=True)
    mock_model.encode_image.return_value = features
    return mock_model, mock_preprocess


@patch("app.pipeline.embeddings.clip.load")
@patch("app.pipeline.embeddings._select_device", return_value="cpu")
def test_get_clip_model_loads_and_returns(mock_device, mock_load):
    mock_model, mock_preprocess = _make_clip_mocks()
    mock_load.return_value = (mock_model, mock_preprocess)

    model, preprocess = get_clip_model("ViT-B/32")

    mock_load.assert_called_once_with("ViT-B/32", device="cpu")
    mock_model.eval.assert_called_once()
    assert model is mock_model
    assert preprocess is mock_preprocess


@patch("app.pipeline.embeddings.clip.load")
@patch("app.pipeline.embeddings.torch.cuda.is_available", return_value=True)
def test_get_clip_model_uses_cuda_when_available(mock_cuda, mock_load):
    mock_model, mock_preprocess = _make_clip_mocks()
    mock_load.return_value = (mock_model, mock_preprocess)

    get_clip_model("ViT-B/32")
    mock_load.assert_called_once_with("ViT-B/32", device="cuda")


@patch("app.pipeline.embeddings.clip.load")
@patch("app.pipeline.embeddings.torch.cuda.is_available", return_value=False)
def test_get_clip_model_falls_back_to_cpu(mock_cuda, mock_load):
    mock_model, mock_preprocess = _make_clip_mocks()
    mock_load.return_value = (mock_model, mock_preprocess)

    get_clip_model("ViT-B/32")
    args = mock_load.call_args
    assert args.kwargs.get("device") == "cpu" or args.args[1] == "cpu"


@patch("app.pipeline.embeddings.clip.load")
def test_get_clip_model_cached_on_second_call(mock_load):
    mock_model, mock_preprocess = _make_clip_mocks()
    mock_load.return_value = (mock_model, mock_preprocess)

    m1, p1 = get_clip_model("ViT-B/32")
    m2, p2 = get_clip_model("ViT-B/32")

    assert m1 is m2
    assert p1 is p2
    mock_load.assert_called_once()  # only one actual load


@patch("app.pipeline.embeddings.clip.load")
def test_get_clip_model_separate_cache_per_name(mock_load):
    m_a, p_a = _make_clip_mocks()
    m_b, p_b = _make_clip_mocks()
    mock_load.side_effect = [(m_a, p_a), (m_b, p_b)]

    get_clip_model("ViT-B/32")
    get_clip_model("ViT-B/16")

    assert mock_load.call_count == 2
    assert emb_module._model_cache["ViT-B/32"][0] is m_a
    assert emb_module._model_cache["ViT-B/16"][0] is m_b


def test_extract_embeddings_valid_images():
    n = 3
    mock_model, mock_preprocess = _make_clip_mocks(n_images=n)

    images = {f"p{i}": make_pil_image() for i in range(n)}
    result = extract_embeddings(images, mock_model, mock_preprocess)

    assert set(result.keys()) == set(images.keys())
    for pid, vec in result.items():
        assert isinstance(vec, np.ndarray)
        assert vec.shape == (512,)


def test_extract_embeddings_l2_normalized():
    n = 4
    mock_model, mock_preprocess = _make_clip_mocks(n_images=n)

    images = {f"p{i}": make_pil_image() for i in range(n)}
    result = extract_embeddings(images, mock_model, mock_preprocess)

    for vec in result.values():
        norm = float(np.linalg.norm(vec))
        assert abs(norm - 1.0) < 1e-5, f"Expected unit norm, got {norm}"


def test_extract_embeddings_preprocess_failure_skipped():
    """If preprocess raises for one image, that image is dropped, others succeed."""
    mock_model, mock_preprocess = _make_clip_mocks(n_images=1)

    def preprocess(img):
        # Fail on small images, succeed on normal ones.
        if img.size == (10, 10):
            raise ValueError("too small")
        return torch.zeros(3, 224, 224)

    mock_preprocess.side_effect = preprocess
    images = {"good": make_pil_image(), "bad": make_pil_image(width=10, height=10)}
    result = extract_embeddings(images, mock_model, mock_preprocess)
    assert "good" in result
    assert "bad" not in result


def test_extract_embeddings_empty_input_returns_empty():
    mock_model, mock_preprocess = _make_clip_mocks()
    result = extract_embeddings({}, mock_model, mock_preprocess)
    assert result == {}
    mock_model.encode_image.assert_not_called()


def test_extract_embeddings_chunks_large_batches():
    """Win 4: input larger than batch_size is split across multiple forward passes."""
    n_total = 70
    batch_size = 32
    images = {f"p{i}": make_pil_image() for i in range(n_total)}

    mock_preprocess = MagicMock(side_effect=lambda img: torch.zeros(3, 224, 224))
    mock_model = MagicMock()

    def encode_image(batch_tensor):
        b = batch_tensor.shape[0]
        feats = torch.randn(b, 512)
        feats = feats / feats.norm(dim=-1, keepdim=True)
        return feats

    mock_model.encode_image.side_effect = encode_image

    result = extract_embeddings(images, mock_model, mock_preprocess, batch_size=batch_size)

    # 70 items @ batch_size 32 → 3 forward passes (32 + 32 + 6)
    assert mock_model.encode_image.call_count == 3
    assert len(result) == n_total
    for vec in result.values():
        assert vec.shape == (512,)


def test_extract_embeddings_default_batch_size_one_pass_for_small_input():
    images = {f"p{i}": make_pil_image() for i in range(5)}
    mock_model, mock_preprocess = _make_clip_mocks(n_images=5)
    extract_embeddings(images, mock_model, mock_preprocess)
    assert mock_model.encode_image.call_count == 1
