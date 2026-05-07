import pytest

from app.pipeline.selector import select_photos


def test_round_robin_interleaves_two_clusters():
    clusters = {
        0: ["A", "B", "C"],
        1: ["D", "E"],
    }
    quality = {"A": 0.9, "B": 0.7, "C": 0.5, "D": 0.8, "E": 0.6}
    result = select_photos(clusters, quality, n_select=4)
    # Within cluster: A(0.9)>B>C, D(0.8)>E
    # Round 0: A, D  Round 1: B, E
    assert result == ["A", "D", "B", "E"]


def test_quality_sorting_within_cluster():
    clusters = {0: ["low", "high", "mid"]}
    quality = {"low": 0.1, "high": 0.9, "mid": 0.5}
    result = select_photos(clusters, quality, n_select=3)
    assert result == ["high", "mid", "low"]


def test_n_select_greater_than_total_returns_all():
    clusters = {0: ["a", "b"], 1: ["c"]}
    quality = {"a": 0.5, "b": 0.4, "c": 0.6}
    result = select_photos(clusters, quality, n_select=100)
    assert set(result) == {"a", "b", "c"}
    assert len(result) == 3


def test_n_select_one_returns_best_from_first_cluster():
    clusters = {0: ["x", "y"], 1: ["z"]}
    quality = {"x": 0.8, "y": 0.3, "z": 0.9}
    result = select_photos(clusters, quality, n_select=1)
    assert len(result) == 1
    assert result[0] == "x"  # first cluster, highest quality


def test_uneven_clusters_exhausts_smaller_first():
    clusters = {
        0: ["A", "B", "C"],
        1: ["D"],  # only one element
    }
    quality = {"A": 0.9, "B": 0.7, "C": 0.5, "D": 0.8}
    result = select_photos(clusters, quality, n_select=4)
    # Round 0: A, D  Round 1: B (D exhausted)  Round 2: C
    assert result == ["A", "D", "B", "C"]


def test_single_cluster_selects_by_quality():
    clusters = {0: ["p3", "p1", "p2"]}
    quality = {"p1": 0.9, "p2": 0.5, "p3": 0.1}
    result = select_photos(clusters, quality, n_select=3)
    assert result == ["p1", "p2", "p3"]


def test_n_select_zero_returns_empty():
    clusters = {0: ["a", "b"]}
    quality = {"a": 1.0, "b": 0.5}
    result = select_photos(clusters, quality, n_select=0)
    assert result == []


def test_missing_quality_score_defaults_to_zero():
    # Photo without a quality score → treated as 0.0
    clusters = {0: ["known", "unknown"]}
    quality = {"known": 0.5}  # "unknown" has no entry
    result = select_photos(clusters, quality, n_select=2)
    # "known" (0.5) > "unknown" (0.0 default)
    assert result[0] == "known"
    assert result[1] == "unknown"
