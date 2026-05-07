from unittest.mock import MagicMock, patch

import numpy as np
import torch

from app.pipeline.reranker import rerank_by_text


def _make_directional_embeddings() -> dict[str, np.ndarray]:
    """Three embeddings pointing in distinct directions (first 3 basis vectors)."""
    return {
        "high": np.array([1.0] + [0.0] * 511, dtype=np.float32),
        "mid":  np.array([0.5] + [0.0] * 511, dtype=np.float32),
        "low":  np.array([0.1] + [0.0] * 511, dtype=np.float32),
    }


def _make_text_tensor_pointing_at_dim0() -> torch.Tensor:
    """Text embedding that aligns perfectly with dimension 0 → dot(high)=1, dot(mid)=0.5."""
    t = torch.zeros(1, 512)
    t[0, 0] = 1.0  # after L2-norm inside the function: still [1, 0, 0, ...]
    return t


def test_empty_description_returns_original_order():
    embeddings = _make_directional_embeddings()
    photo_ids = ["low", "high", "mid"]
    result = rerank_by_text(photo_ids, embeddings, "", MagicMock())
    assert result == ["low", "high", "mid"]


def test_whitespace_only_description_returns_original_order():
    embeddings = _make_directional_embeddings()
    photo_ids = ["a", "b"]
    result = rerank_by_text(photo_ids, embeddings, "   \t\n  ", MagicMock())
    assert result == ["a", "b"]


def test_reranks_by_cosine_similarity():
    embeddings = _make_directional_embeddings()
    photo_ids = ["low", "mid", "high"]  # deliberately wrong order

    mock_model = MagicMock()
    mock_model.encode_text.return_value = _make_text_tensor_pointing_at_dim0()

    with patch("app.pipeline.reranker.clip.tokenize") as mock_tokenize:
        mock_tokenize.return_value = MagicMock()
        result = rerank_by_text(photo_ids, embeddings, "romantic sunset", mock_model)

    assert result == ["high", "mid", "low"]


def test_photo_missing_from_embeddings_sorted_last():
    embeddings = {"present": np.array([1.0] + [0.0] * 511, dtype=np.float32)}
    photo_ids = ["present", "missing"]

    mock_model = MagicMock()
    mock_model.encode_text.return_value = _make_text_tensor_pointing_at_dim0()

    with patch("app.pipeline.reranker.clip.tokenize") as mock_tokenize:
        mock_tokenize.return_value = MagicMock()
        result = rerank_by_text(photo_ids, embeddings, "some description", mock_model)

    # "present" has similarity 1.0; "missing" gets 0.0
    assert result == ["present", "missing"]


def test_clip_exception_returns_original_order():
    embeddings = _make_directional_embeddings()
    photo_ids = ["low", "high", "mid"]

    mock_model = MagicMock()
    mock_model.encode_text.side_effect = RuntimeError("CLIP failed")

    with patch("app.pipeline.reranker.clip.tokenize") as mock_tokenize:
        mock_tokenize.return_value = MagicMock()
        result = rerank_by_text(photo_ids, embeddings, "some text", mock_model)

    assert result == ["low", "high", "mid"]


def test_single_photo_returns_list_with_that_photo():
    embeddings = {"only": np.array([1.0] + [0.0] * 511, dtype=np.float32)}
    mock_model = MagicMock()
    mock_model.encode_text.return_value = _make_text_tensor_pointing_at_dim0()

    with patch("app.pipeline.reranker.clip.tokenize") as mock_tokenize:
        mock_tokenize.return_value = MagicMock()
        result = rerank_by_text(["only"], embeddings, "description", mock_model)

    assert result == ["only"]
