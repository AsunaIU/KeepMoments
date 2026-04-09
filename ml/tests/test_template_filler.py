from app.pipeline.template_filler import count_template_slots, fill_template
from app.schemas import Page, Slot, Template
from tests.conftest import make_template


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
