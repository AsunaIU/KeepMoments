from app.pipeline.template_filler import count_template_slots, fill_template
from app.schemas import Orientation, Page, Slot, Template
from tests.conftest import make_template, make_template_with_orientations


def test_count_slots_zero_pages():
    t = Template(id="t1", pages=[])
    assert count_template_slots(t) == 0


def test_count_slots_single_page():
    t = make_template(n_pages=1, slots_per_page=4)
    assert count_template_slots(t) == 4


def test_count_slots_multi_page():
    t = make_template(n_pages=3, slots_per_page=2)
    assert count_template_slots(t) == 6


def test_count_slots_mixed_pages():
    t = Template(
        id="t1",
        pages=[
            Page(id="p0", slots=[Slot(id="s0"), Slot(id="s1")]),
            Page(id="p1", slots=[Slot(id="s2")]),
            Page(id="p2", slots=[Slot(id="s3"), Slot(id="s4"), Slot(id="s5")]),
        ],
    )
    assert count_template_slots(t) == 6


def test_fill_exact_match():
    t = make_template(n_pages=1, slots_per_page=3)
    result = fill_template(t, ["a", "b", "c"])
    slots = result.pages[0].slots
    assert [s.photo_id for s in slots] == ["a", "b", "c"]


def test_fill_more_photos_than_slots_extra_ignored():
    t = make_template(n_pages=1, slots_per_page=2)
    result = fill_template(t, ["a", "b", "c", "d", "e"])
    slots = result.pages[0].slots
    assert [s.photo_id for s in slots] == ["a", "b"]


def test_fill_fewer_photos_than_slots_remainder_is_none():
    t = make_template(n_pages=1, slots_per_page=5)
    result = fill_template(t, ["x", "y"])
    slots = result.pages[0].slots
    assert [s.photo_id for s in slots] == ["x", "y", None, None, None]


def test_fill_empty_photos_all_slots_none():
    t = make_template(n_pages=2, slots_per_page=2)
    result = fill_template(t, [])
    for page in result.pages:
        for slot in page.slots:
            assert slot.photo_id is None


def test_fill_preserves_page_and_slot_ids():
    t = make_template(n_pages=2, slots_per_page=2)
    result = fill_template(t, ["p1", "p2", "p3", "p4"])
    assert result.id == t.id
    for orig_page, filled_page in zip(t.pages, result.pages):
        assert filled_page.id == orig_page.id
        for orig_slot, filled_slot in zip(orig_page.slots, filled_page.slots):
            assert filled_slot.id == orig_slot.id


def test_fill_multi_page_sequential_order():
    t = make_template(n_pages=2, slots_per_page=2)
    result = fill_template(t, ["A", "B", "C", "D"])
    assert result.pages[0].slots[0].photo_id == "A"
    assert result.pages[0].slots[1].photo_id == "B"
    assert result.pages[1].slots[0].photo_id == "C"
    assert result.pages[1].slots[1].photo_id == "D"


# ---------------------------------------------------------------------------
# Orientation-aware filling tests
# ---------------------------------------------------------------------------

_PORT = Orientation.portrait
_LAND = Orientation.landscape


def _orient(photo_ids: list[str], orientations: list[Orientation]) -> dict[str, Orientation]:
    return dict(zip(photo_ids, orientations))


def test_fill_no_orientation_backward_compatible():
    t = make_template(n_pages=1, slots_per_page=3)
    result = fill_template(t, ["a", "b", "c"], orientations=None)
    assert [s.photo_id for s in result.pages[0].slots] == ["a", "b", "c"]


def test_fill_empty_orientations_backward_compatible():
    t = make_template(n_pages=1, slots_per_page=3)
    result = fill_template(t, ["a", "b", "c"], orientations={})
    assert [s.photo_id for s in result.pages[0].slots] == ["a", "b", "c"]


def test_fill_landscape_slot_picks_landscape_photo():
    t = make_template_with_orientations(["landscape"])
    photos = ["portrait_p", "landscape_p"]
    orientations = _orient(photos, [_PORT, _LAND])
    result = fill_template(t, photos, orientations=orientations)
    assert result.pages[0].slots[0].photo_id == "landscape_p"


def test_fill_portrait_slot_picks_portrait_photo():
    t = make_template_with_orientations(["portrait"])
    photos = ["landscape_p", "portrait_p"]
    orientations = _orient(photos, [_LAND, _PORT])
    result = fill_template(t, photos, orientations=orientations)
    assert result.pages[0].slots[0].photo_id == "portrait_p"


def test_fill_falls_back_when_no_matching_orientation():
    t = make_template_with_orientations(["landscape", "landscape"])
    photos = ["landscape_p", "portrait_p"]
    orientations = _orient(photos, [_LAND, _PORT])
    result = fill_template(t, photos, orientations=orientations)
    slots = result.pages[0].slots
    assert slots[0].photo_id == "landscape_p"
    assert slots[1].photo_id == "portrait_p"  # fallback


def test_fill_preserves_rank_within_orientation():
    t = make_template_with_orientations(["landscape"])
    photos = ["L1", "L2", "L3"]
    orientations = _orient(photos, [_LAND, _LAND, _LAND])
    result = fill_template(t, photos, orientations=orientations)
    assert result.pages[0].slots[0].photo_id == "L1"  # best-ranked landscape


def test_fill_none_required_picks_best_available():
    t = make_template_with_orientations([None])
    photos = ["portrait_p", "landscape_p"]
    orientations = _orient(photos, [_PORT, _LAND])
    result = fill_template(t, photos, orientations=orientations)
    assert result.pages[0].slots[0].photo_id == "portrait_p"  # first in ranked list


def test_fill_mixed_slots_multi_page():
    from app.schemas import Page
    t = Template(
        id="t1",
        pages=[
            Page(id="p0", slots=[Slot(id="s0", required_orientation=_PORT)]),
            Page(id="p1", slots=[Slot(id="s1", required_orientation=_LAND)]),
        ],
    )
    photos = ["land", "port"]
    orientations = {"land": _LAND, "port": _PORT}
    result = fill_template(t, photos, orientations=orientations)
    assert result.pages[0].slots[0].photo_id == "port"
    assert result.pages[1].slots[0].photo_id == "land"


def test_fill_exhausted_available_gives_none():
    t = make_template_with_orientations(["landscape", "landscape"])
    photos = ["L1"]
    orientations = {"L1": _LAND}
    result = fill_template(t, photos, orientations=orientations)
    slots = result.pages[0].slots
    assert slots[0].photo_id == "L1"
    assert slots[1].photo_id is None


def test_fill_all_none_required_with_orientations_dict():
    t = make_template_with_orientations([None, None, None])
    photos = ["a", "b", "c"]
    orientations = _orient(photos, [_PORT, _LAND, _PORT])
    result = fill_template(t, photos, orientations=orientations)
    assert [s.photo_id for s in result.pages[0].slots] == ["a", "b", "c"]
