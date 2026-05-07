from PIL import Image

from app.schemas import Orientation


def detect_orientation(images: dict[str, Image.Image]) -> dict[str, Orientation]:
    """Classify each (already-decoded) image as landscape or portrait. Squares → landscape."""
    result: dict[str, Orientation] = {}
    for photo_id, img in images.items():
        width, height = img.size
        result[photo_id] = Orientation.landscape if width >= height else Orientation.portrait
    return result
