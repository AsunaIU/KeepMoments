from app.schemas import FilledPage, FilledSlot, FilledTemplate, Orientation, Template


def count_template_slots(template: Template) -> int:
    return sum(len(page.slots) for page in template.pages)


def fill_template(
    template: Template,
    ranked_photos: list[str],
    orientations: dict[str, Orientation] | None = None,
) -> FilledTemplate:
    if not orientations:
        photo_iter = iter(ranked_photos)
        filled_pages = []
        for page in template.pages:
            filled_slots = []
            for slot in page.slots:
                photo_id = next(photo_iter, None)
                filled_slots.append(FilledSlot(id=slot.id, photo_id=photo_id))
            filled_pages.append(FilledPage(id=page.id, slots=filled_slots))
        return FilledTemplate(id=template.id, pages=filled_pages)

    available = list(ranked_photos)
    filled_pages = []
    for page in template.pages:
        filled_slots = []
        for slot in page.slots:
            chosen = None
            req = slot.required_orientation
            if req is not None:
                for i, pid in enumerate(available):
                    if orientations.get(pid) == req:
                        chosen = available.pop(i)
                        break
            if chosen is None:
                chosen = available.pop(0) if available else None
            filled_slots.append(FilledSlot(id=slot.id, photo_id=chosen))
        filled_pages.append(FilledPage(id=page.id, slots=filled_slots))

    return FilledTemplate(id=template.id, pages=filled_pages)
