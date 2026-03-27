from collections import defaultdict

import numpy as np
from sklearn.cluster import KMeans


def cluster_photos(
    embeddings: dict[str, np.ndarray],
    n_clusters: int,
    random_state: int = 42,
) -> dict[int, list[str]]:
    ids = list(embeddings.keys())
    matrix = np.stack([embeddings[pid] for pid in ids])

    labels = KMeans(
        n_clusters=n_clusters,
        random_state=random_state,
        n_init="auto",
    ).fit_predict(matrix)

    clusters: dict[int, list[str]] = defaultdict(list)
    for pid, label in zip(ids, labels):
        clusters[int(label)].append(pid)

    return dict(clusters)
