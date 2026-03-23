"""
yt-dlp 기반 YouTube 다운로드 서비스

지원:
- YouTube 단일 영상 다운로드 (최대 1080p MP4)
- 썸네일 추출
- 진행률 콜백 지원
"""

import os
from pathlib import Path
from typing import Callable

import yt_dlp

from utils.logger import get_logger

logger = get_logger("downloader")


class ProgressLogger:
    """yt-dlp 진행률을 로거로 전달"""

    def __init__(self, on_progress: Callable[[int], None] | None = None):
        self._on_progress = on_progress

    def debug(self, msg):
        if msg.startswith("[download]"):
            logger.debug(msg)

    def info(self, msg):
        logger.info(msg)

    def warning(self, msg):
        logger.warning(msg)

    def error(self, msg):
        logger.error(msg)


def _progress_hook(on_progress: Callable[[int], None] | None = None):
    """yt-dlp progress hook 팩토리"""

    def hook(d: dict):
        if d["status"] == "downloading" and on_progress:
            total = d.get("total_bytes") or d.get("total_bytes_estimate", 0)
            downloaded = d.get("downloaded_bytes", 0)
            if total > 0:
                pct = int(downloaded / total * 100)
                on_progress(pct)
        elif d["status"] == "finished":
            logger.info("다운로드 완료: %s", d.get("filename", ""))

    return hook


def download_youtube(
    url: str,
    output_dir: str,
    on_progress: Callable[[int], None] | None = None,
    max_height: int = 1080,
) -> str:
    """
    YouTube 영상을 다운로드합니다.

    Args:
        url:         YouTube URL (watch?v=...)
        output_dir:  출력 폴더
        on_progress: 다운로드 진행률 콜백 (0~100)
        max_height:  최대 해상도 높이 (기본 1080)

    Returns:
        다운로드된 파일의 절대 경로 (.mp4)

    Raises:
        RuntimeError: 다운로드 실패 시
    """
    output_dir = str(Path(output_dir).resolve())
    os.makedirs(output_dir, exist_ok=True)

    output_template = os.path.join(output_dir, "%(id)s.%(ext)s")

    ydl_opts = {
        # MP4 선호, 최대 max_height 이하 화질
        "format": f"bestvideo[height<={max_height}][ext=mp4]+bestaudio[ext=m4a]/best[height<={max_height}][ext=mp4]/best",
        "outtmpl": output_template,
        "merge_output_format": "mp4",
        # 자막은 별도로 처리하므로 비활성
        "writesubtitles": False,
        "writeautomaticsub": False,
        # 메타데이터
        "writethumbnail": False,
        "quiet": True,
        "no_warnings": False,
        "logger": ProgressLogger(on_progress),
        "progress_hooks": [_progress_hook(on_progress)],
        # 네트워크
        "socket_timeout": 30,
        "retries": 3,
        # FFmpeg 후처리 (merge)
        "postprocessors": [
            {
                "key": "FFmpegVideoConvertor",
                "preferedformat": "mp4",
            }
        ],
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)

            # 1) 후처리 후 실제 저장 경로 (병합/변환 포함)
            filename = None
            requested = info.get("requested_downloads")
            if requested and isinstance(requested, list) and requested[0].get("filepath"):
                filename = requested[0]["filepath"]

            # 2) fallback: prepare_filename 사용
            if not filename or not os.path.exists(filename):
                filename = ydl.prepare_filename(info)

            # 3) 확장자 .mp4 로 병합됐을 경우 처리
            if not os.path.exists(filename):
                base = os.path.splitext(filename)[0]
                for ext in [".mp4", ".mkv", ".webm"]:
                    candidate = base + ext
                    if os.path.exists(candidate):
                        filename = candidate
                        break

            # 4) 폴더에서 가장 최근 영상 파일 탐색
            if not filename or not os.path.exists(filename):
                video_exts = {".mp4", ".mkv", ".webm", ".avi", ".mov"}
                candidates = sorted(
                    [f for f in Path(output_dir).iterdir()
                     if f.suffix.lower() in video_exts],
                    key=lambda f: f.stat().st_mtime,
                    reverse=True,
                )
                if candidates:
                    filename = str(candidates[0])

            if not filename or not os.path.exists(filename):
                raise RuntimeError(f"다운로드 파일을 찾을 수 없음: {filename}")

            file_size_mb = os.path.getsize(filename) / (1024 * 1024)
            logger.info(
                "YouTube 다운로드 완료: %s (%.1f MB)",
                os.path.basename(filename),
                file_size_mb,
            )
            return filename

    except yt_dlp.utils.DownloadError as e:
        logger.error("yt-dlp 다운로드 실패: %s — %s", url, e)
        raise RuntimeError(f"YouTube 다운로드 실패: {e}") from e


def download_media_file(url: str, output_dir: str, filename: str | None = None) -> str:
    """
    일반 HTTP 미디어 파일(이미지, 영상) 다운로드

    Args:
        url:        직접 다운로드 URL
        output_dir: 저장 폴더
        filename:   저장 파일명 (None이면 URL에서 추출)

    Returns:
        저장된 파일의 절대 경로
    """
    import httpx

    output_dir = str(Path(output_dir).resolve())
    os.makedirs(output_dir, exist_ok=True)

    if filename is None:
        filename = url.split("?")[0].split("/")[-1] or "media_file"

    output_path = os.path.join(output_dir, filename)

    try:
        with httpx.Client(timeout=60.0, follow_redirects=True) as client:
            with client.stream("GET", url, headers={"User-Agent": "Mozilla/5.0"}) as resp:
                resp.raise_for_status()
                with open(output_path, "wb") as f:
                    for chunk in resp.iter_bytes(chunk_size=8192):
                        f.write(chunk)

        logger.info("미디어 다운로드 완료: %s", output_path)
        return output_path

    except Exception as e:
        logger.error("미디어 다운로드 실패: %s — %s", url, e)
        raise RuntimeError(f"미디어 다운로드 실패: {e}") from e
