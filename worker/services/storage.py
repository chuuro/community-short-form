"""
MinIO 오브젝트 스토리지 클라이언트

버킷 구조:
    shortform/
    ├── renders/{projectId}/output_{jobId}.mp4     # 최종 영상
    ├── renders/{projectId}/preview_{jobId}.mp4    # 미리보기
    └── media/{projectId}/{filename}               # 원본 미디어
"""

import os
from pathlib import Path

from minio import Minio
from minio.error import S3Error

from config import settings
from utils.logger import get_logger

logger = get_logger("storage")

_client: Minio | None = None


def _get_client() -> Minio:
    """MinIO 클라이언트 싱글턴"""
    global _client
    if _client is None:
        _client = Minio(
            endpoint=settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_secure,
        )
        _ensure_bucket(_client, settings.minio_bucket)
    return _client


def _ensure_bucket(client: Minio, bucket: str) -> None:
    """버킷이 없으면 생성"""
    try:
        if not client.bucket_exists(bucket):
            client.make_bucket(bucket)
            logger.info("MinIO 버킷 생성: %s", bucket)
        else:
            logger.debug("MinIO 버킷 확인: %s", bucket)
    except S3Error as e:
        logger.error("MinIO 버킷 설정 실패: %s", e)
        raise


def upload_render(
    file_path: str,
    project_id: int,
    job_id: str,
    is_preview: bool = False,
) -> str:
    """
    렌더링 결과물을 MinIO에 업로드합니다.

    Args:
        file_path:   업로드할 로컬 파일 경로
        project_id:  프로젝트 ID
        job_id:      렌더 작업 ID (workerJobId)
        is_preview:  True면 preview/ 경로에 저장

    Returns:
        MinIO 오브젝트 경로 (예: renders/1/output_abc123.mp4)
    """
    client = _get_client()

    filename = Path(file_path).name
    prefix = "preview" if is_preview else "output"
    object_name = f"renders/{project_id}/{prefix}_{job_id[:8]}.mp4"

    file_size = os.path.getsize(file_path)
    content_type = "video/mp4"

    logger.info(
        "MinIO 업로드 시작: %s → %s (%.1f MB)",
        filename,
        object_name,
        file_size / (1024 * 1024),
    )

    try:
        client.fput_object(
            bucket_name=settings.minio_bucket,
            object_name=object_name,
            file_path=file_path,
            content_type=content_type,
        )
        logger.info("MinIO 업로드 완료: %s", object_name)
        return object_name

    except S3Error as e:
        logger.error("MinIO 업로드 실패: %s — %s", object_name, e)
        raise RuntimeError(f"MinIO 업로드 실패: {e}") from e


def upload_media(file_path: str, project_id: int) -> str:
    """
    원본 미디어 파일을 MinIO에 업로드합니다.

    Returns:
        MinIO 오브젝트 경로
    """
    client = _get_client()
    filename = Path(file_path).name
    object_name = f"media/{project_id}/{filename}"

    ext = Path(file_path).suffix.lower()
    content_type_map = {
        ".mp4": "video/mp4",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".gif": "image/gif",
        ".webp": "image/webp",
        ".mp3": "audio/mpeg",
        ".wav": "audio/wav",
    }
    content_type = content_type_map.get(ext, "application/octet-stream")

    try:
        client.fput_object(
            bucket_name=settings.minio_bucket,
            object_name=object_name,
            file_path=file_path,
            content_type=content_type,
        )
        logger.debug("미디어 업로드 완료: %s", object_name)
        return object_name

    except S3Error as e:
        logger.error("미디어 업로드 실패: %s — %s", object_name, e)
        raise


def download_file(object_name: str, output_path: str) -> str:
    """
    MinIO에서 파일을 다운로드합니다.

    Args:
        object_name:  MinIO 오브젝트 경로
        output_path:  저장할 로컬 파일 경로

    Returns:
        저장된 로컬 파일 경로
    """
    client = _get_client()
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)

    try:
        client.fget_object(
            bucket_name=settings.minio_bucket,
            object_name=object_name,
            file_path=output_path,
        )
        logger.debug("MinIO 다운로드 완료: %s → %s", object_name, output_path)
        return output_path

    except S3Error as e:
        logger.error("MinIO 다운로드 실패: %s — %s", object_name, e)
        raise RuntimeError(f"MinIO 다운로드 실패: {e}") from e


def get_presigned_url(object_name: str, expires_hours: int = 24) -> str:
    """
    파일의 임시 접근 URL을 생성합니다 (프론트엔드 다운로드용).
    Docker 내부에서는 minio_public_endpoint를 사용해 브라우저 접근 가능한 URL 생성.

    Returns:
        Presigned URL (expires_hours 후 만료)
    """
    from datetime import timedelta

    endpoint = settings.minio_public_endpoint or settings.minio_endpoint
    client = Minio(
        endpoint=endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )
    try:
        url = client.presigned_get_object(
            bucket_name=settings.minio_bucket,
            object_name=object_name,
            expires=timedelta(hours=expires_hours),
        )
        return url
    except S3Error as e:
        logger.error("Presigned URL 생성 실패: %s — %s", object_name, e)
        raise


def object_exists(object_name: str) -> bool:
    """오브젝트 존재 여부 확인"""
    client = _get_client()
    try:
        client.stat_object(settings.minio_bucket, object_name)
        return True
    except S3Error:
        return False
