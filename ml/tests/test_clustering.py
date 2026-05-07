import numpy as np
import pytest

from app.pipeline.clustering import cluster_photos


def _make_embeddings(n: int, seed: int = 0) -> dict[str, np.ndarray]:
    rng = np.random.RandomState(seed)
    vecs = {}
    for i in range(n):
        v = rng.randn(512).astype(np.float32)
        v /= np.linalg.norm(v)
        vecs[f"photo_{i}"] = v
    return vecs


def test_all_photo_ids_present_in_clusters():
    embeddings = _make_embeddings(10)
    clusters = cluster_photos(embeddings, n_clusters=3)
    all_ids = {pid for group in clusters.values() for pid in group}
    assert all_ids == set(embeddings.keys())


def test_single_cluster_contains_all_photos():
    embeddings = _make_embeddings(5)
    clusters = cluster_photos(embeddings, n_clusters=1)
    assert len(clusters) == 1
    assert set(clusters[0]) == set(embeddings.keys())


def test_n_clusters_equals_n_photos_one_per_cluster():
    n = 6
    embeddings = _make_embeddings(n)
    clusters = cluster_photos(embeddings, n_clusters=n)
    # Each cluster should have exactly one photo
    assert len(clusters) == n
    for group in clusters.values():
        assert len(group) == 1


def test_deterministic_with_same_random_state():
    embeddings = _make_embeddings(8)
    c1 = cluster_photos(embeddings, n_clusters=3, random_state=42)
    c2 = cluster_photos(embeddings, n_clusters=3, random_state=42)
    assert c1 == c2


def test_different_random_states_may_differ():
    embeddings = _make_embeddings(20, seed=7)
    c1 = cluster_photos(embeddings, n_clusters=4, random_state=0)
    c2 = cluster_photos(embeddings, n_clusters=4, random_state=999)
    # Not guaranteed to differ, but should be valid regardless
    for clusters in (c1, c2):
        all_ids = {pid for group in clusters.values() for pid in group}
        assert all_ids == set(embeddings.keys())


def test_return_type_is_dict_of_lists():
    embeddings = _make_embeddings(5)
    clusters = cluster_photos(embeddings, n_clusters=2)
    assert isinstance(clusters, dict)
    for key, value in clusters.items():
        assert isinstance(key, int)
        assert isinstance(value, list)
