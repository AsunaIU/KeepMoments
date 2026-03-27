def select_photos(
    clusters: dict[int, list[str]],
    quality_scores: dict[str, float],
    n_select: int,
) -> list[str]:
    # Sort each cluster by quality descending
    sorted_clusters = [
        sorted(photo_ids, key=lambda pid: quality_scores.get(pid, 0.0), reverse=True)
        for photo_ids in clusters.values()
    ]

    selected: list[str] = []
    round_idx = 0

    while len(selected) < n_select:
        made_progress = False
        for cluster in sorted_clusters:
            if round_idx < len(cluster):
                selected.append(cluster[round_idx])
                made_progress = True
                if len(selected) >= n_select:
                    break
        if not made_progress:
            # All clusters exhausted
            break
        round_idx += 1

    return selected
