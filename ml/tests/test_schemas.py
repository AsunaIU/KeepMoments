import pytest
from pydantic import ValidationError

from app.schemas import FilledSlot, FilledTemplate, ProcessRequest, Slot, Template
from tests.conftest import make_process_request, make_template


def test_valid_request():
    req = make_process_request(min_photos=1, max_photos=5)
    assert req.min_photos == 1
    assert req.max_photos == 5
    assert len(req.photo_ids) == 5


def test_min_greater_than_max_raises():
    with pytest.raises(ValidationError, match="min_photos must be <= max_photos"):
        ProcessRequest(
            photo_ids=["p1"],
            user_description="test",
            min_photos=5,
            max_photos=3,
            template=make_template(),
        )


def test_min_equals_max_is_valid():
    req = ProcessRequest(
        photo_ids=["p1"],
        user_description="test",
        min_photos=3,
        max_photos=3,
        template=make_template(),
    )
    assert req.min_photos == req.max_photos == 3


def test_min_photos_zero_raises():
    with pytest.raises(ValidationError):
        ProcessRequest(
            photo_ids=["p1"],
            user_description="test",
            min_photos=0,
            max_photos=5,
            template=make_template(),
        )


def test_max_photos_zero_raises():
    with pytest.raises(ValidationError):
        ProcessRequest(
            photo_ids=["p1"],
            user_description="test",
            min_photos=1,
            max_photos=0,
            template=make_template(),
        )


def test_slot_default_photo_id_is_none():
    slot = Slot(id="s1")
    assert slot.photo_id is None


def test_slot_with_photo_id():
    slot = Slot(id="s1", photo_id="photo_abc")
    assert slot.photo_id == "photo_abc"


def test_template_serialization_roundtrip():
    t = make_template(n_pages=2, slots_per_page=3)
    restored = Template.model_validate(t.model_dump())
    assert restored.id == t.id
    assert len(restored.pages) == 2
    assert len(restored.pages[0].slots) == 3
    assert restored.pages[1].slots[2].id == "slot_1_2"


def test_empty_photo_ids_allowed():
    req = ProcessRequest(
        photo_ids=[],
        user_description="test",
        min_photos=1,
        max_photos=5,
        template=make_template(),
    )
    assert req.photo_ids == []


def test_template_no_pages_is_valid():
    t = Template(id="t1", pages=[])
    assert t.pages == []


def test_filled_template_slot_none_by_default():
    slot = FilledSlot(id="s1")
    assert slot.photo_id is None


def test_filled_template_structure():
    ft = FilledTemplate(
        id="t1",
        pages=[],
    )
    assert ft.id == "t1"
    assert ft.pages == []
