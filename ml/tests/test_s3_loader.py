from unittest.mock import MagicMock

from app.pipeline.s3_loader import download_photos


def _make_s3_client(responses: dict[str, bytes | Exception]) -> MagicMock:
    """Build a mock S3 client whose get_object returns or raises per photo_id."""

    def get_object(Bucket, Key):
        result = responses[Key]
        if isinstance(result, Exception):
            raise result
        body = MagicMock()
        body.read.return_value = result
        return {"Body": body}

    client = MagicMock()
    client.get_object.side_effect = get_object
    return client


def test_all_downloads_succeed():
    data = {"p1": b"bytes1", "p2": b"bytes2", "p3": b"bytes3"}
    client = _make_s3_client(data)
    result = download_photos(list(data.keys()), "my-bucket", client)
    assert result == data


def test_partial_failure_skips_failed_photo():
    responses = {
        "p1": b"ok",
        "p2": Exception("access denied"),
        "p3": b"also ok",
    }
    client = _make_s3_client(responses)
    result = download_photos(["p1", "p2", "p3"], "bucket", client)
    assert set(result.keys()) == {"p1", "p3"}
    assert result["p1"] == b"ok"
    assert result["p3"] == b"also ok"


def test_all_downloads_fail_returns_empty():
    responses = {
        "p1": Exception("error"),
        "p2": Exception("error"),
    }
    client = _make_s3_client(responses)
    result = download_photos(["p1", "p2"], "bucket", client)
    assert result == {}


def test_empty_photo_ids_returns_empty():
    client = MagicMock()
    result = download_photos([], "bucket", client)
    assert result == {}
    client.get_object.assert_not_called()


def test_correct_bucket_is_used():
    client = _make_s3_client({"photo_key": b"data"})
    download_photos(["photo_key"], "correct-bucket", client)
    client.get_object.assert_called_once_with(Bucket="correct-bucket", Key="photo_key")


def test_single_photo_success():
    client = _make_s3_client({"only": b"single"})
    result = download_photos(["only"], "b", client)
    assert result == {"only": b"single"}
