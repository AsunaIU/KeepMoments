import boto3
from botocore.config import Config
from fastapi import Depends

from app.config import Settings, get_settings


def get_s3_client(settings: Settings = Depends(get_settings)):
    return boto3.client(
        "s3",
        region_name=settings.AWS_REGION,
        aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
        aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
        endpoint_url=settings.S3_ENDPOINT_URL,
        config=Config(signature_version="s3v4"),
    )
