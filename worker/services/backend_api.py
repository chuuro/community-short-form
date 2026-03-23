"""
백엔드 REST API 클라이언트

- GET  /api/projects/{id}      → 프로젝트 미디어/자막 조회
- POST /api/render/callback    → 렌더 진행률/완료/실패 콜백
"""

from typing import Any

import httpx

from config import settings
from utils.logger import get_logger

logger = get_logger("backend_api")

_client = httpx.Client(
    base_url=settings.backend_url,
    timeout=30.0,
    headers={"Content-Type": "application/json"},
)


def get_project(project_id: int) -> dict[str, Any]:
    """
    프로젝트 상세 정보 조회 (미디어 목록, 자막 포함)

    Returns:
        ParseResultResponse 구조의 dict:
        {
          "projectId": 1,
          "status": "PARSED",
          "title": "...",
          "communityType": "REDDIT",
          "mediaItems": [...],
          "subtitles": [...],
          ...
        }
    """
    url = f"/api/projects/{project_id}"
    try:
        response = _client.get(url)
        response.raise_for_status()
        data = response.json()
        if data.get("success"):
            return data["data"]
        raise RuntimeError(f"백엔드 오류: {data.get('message')}")
    except httpx.HTTPError as e:
        logger.error("프로젝트 조회 실패: projectId=%s, error=%s", project_id, e)
        raise


def send_callback(
    job_id: str,
    progress: int,
    status: str,
    output_file_path: str | None = None,
    error_message: str | None = None,
) -> None:
    """
    렌더 진행 상태 콜백 전송

    Args:
        job_id:           RenderJob.workerJobId (UUID)
        progress:         0~100 진행률
        status:           "PROCESSING" | "COMPLETED" | "FAILED"
        output_file_path: 완료 시 출력 파일 경로 (MinIO URL 또는 로컬 경로)
        error_message:    실패 시 에러 메시지
    """
    payload: dict[str, Any] = {
        "jobId": job_id,
        "progress": progress,
        "status": status,
    }
    if output_file_path:
        payload["outputFilePath"] = output_file_path
    if error_message:
        payload["errorMessage"] = error_message

    try:
        response = _client.post("/api/render/callback", json=payload)
        response.raise_for_status()
        logger.debug(
            "콜백 전송 | jobId=%s, status=%s, progress=%d%%",
            job_id, status, progress,
        )
    except httpx.HTTPError as e:
        logger.warning(
            "콜백 전송 실패 (무시): jobId=%s, error=%s", job_id, e
        )
