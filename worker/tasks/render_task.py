"""
Celery 렌더 파이프라인 태스크

전체 흐름:
  1. 백엔드에서 프로젝트 미디어/자막 조회
  2. YouTube URL → yt-dlp 다운로드
  3. 이미지/영상 파일 수집
  4. 자막 없으면 Whisper STT 실행
  5. FFmpeg으로 9:16 숏폼 영상 생성
  6. MinIO 업로드
  7. 완료/실패 콜백 전송
"""

import os
import shutil
import traceback
from pathlib import Path

from celery_app import app
from config import settings
from services import backend_api, downloader, editor, storage, transcriber, tts
from utils.logger import get_logger

logger = get_logger("render_task")


@app.task(
    bind=True,
    name="tasks.process_render",
    max_retries=3,
    default_retry_delay=30,
    acks_late=True,
)
def process_render(
    self,
    jobId: str,
    projectId: int,
    outputPlatform: str = "YOUTUBE_SHORTS",
    isPreview: bool = False,
    tempDir: str | None = None,
    outputDir: str | None = None,
    bgmTrackId: int | None = None,
    includeWatermark: bool = False,
    renderJobId: int | None = None,
    **kwargs,
):
    """
    숏폼 영상 렌더링 Celery 태스크

    Args:
        jobId:            RenderJob.workerJobId (UUID) — 콜백에 사용
        projectId:        프로젝트 ID
        outputPlatform:   YOUTUBE_SHORTS | TIKTOK | INSTAGRAM_REELS
        isPreview:        True면 10초 미리보기
        tempDir:          임시 파일 폴더 (None이면 settings.worker_temp_dir/{projectId})
        outputDir:        출력 파일 폴더 (None이면 settings.worker_output_dir)
        bgmTrackId:       BGM 트랙 ID (미구현 — 향후 MinIO에서 로드)
        includeWatermark: 워터마크 포함
        renderJobId:      백엔드 RenderJob ID (로깅용)
    """
    # ─── 경로 설정 ──────────────────────────────────────────────
    work_dir = tempDir or os.path.join(settings.worker_temp_dir, str(projectId))
    out_dir = outputDir or settings.worker_output_dir
    os.makedirs(work_dir, exist_ok=True)
    os.makedirs(out_dir, exist_ok=True)

    logger.info(
        "렌더 시작 | jobId=%s, projectId=%d, platform=%s, preview=%s",
        jobId, projectId, outputPlatform, isPreview,
    )

    try:
        # ─── Step 1: 시작 콜백 ──────────────────────────────────
        backend_api.send_callback(jobId, 5, "PROCESSING")

        # ─── Step 2: 프로젝트 데이터 조회 ───────────────────────
        logger.info("[1/6] 프로젝트 데이터 조회 중...")
        project = backend_api.get_project(projectId)
        media_items = project.get("mediaItems", [])
        subtitles = project.get("subtitles", [])

        if not media_items:
            raise RuntimeError("미디어 아이템이 없어 렌더링할 수 없습니다.")

        backend_api.send_callback(jobId, 12, "PROCESSING")

        # ─── Step 3: 미디어 파일 수집/다운로드 ─────────────────
        logger.info("[2/6] 미디어 파일 수집 중 (%d개)...", len(media_items))
        media_files = _collect_media_files(media_items, work_dir, jobId)

        if not media_files:
            raise RuntimeError("수집된 미디어 파일이 없습니다.")

        backend_api.send_callback(jobId, 35, "PROCESSING")

        # ─── Step 4: 자막 생성 (백엔드: ASS 한글폰트 명시, Whisper: SRT) ───
        srt_path = None
        if subtitles:
            logger.info("[3/6] 백엔드 자막 → ASS 변환 중 (%d개, 한글폰트)...", len(subtitles))
            sub_path = os.path.join(work_dir, f"subtitles_{jobId[:8]}.ass")
            fonts_dir = os.environ.get("WORKER_FONTS_DIR") or "/app/fonts"
            if not os.path.isdir(fonts_dir) or not os.listdir(fonts_dir):
                fonts_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "fonts")
            transcriber.subtitles_to_ass(subtitles, sub_path, fonts_dir=fonts_dir)
            srt_path = sub_path  # editor에서 .ass면 ass 필터 사용
            backend_api.send_callback(jobId, 45, "PROCESSING")

        elif any(m.get("type") == "VIDEO" for m in media_files):
            # 자막 없고 영상 있으면 Whisper STT
            logger.info("[3/6] Whisper STT 실행 중...")
            first_video = next(m for m in media_files if m.get("type") == "VIDEO")
            try:
                srt_path = transcriber.transcribe_to_srt(
                    first_video["path"], work_dir
                )
            except Exception as e:
                logger.warning("Whisper STT 실패 (자막 없이 계속): %s", e)

            backend_api.send_callback(jobId, 50, "PROCESSING")

        # ─── Step 4b: NEWS 프로젝트 TTS 생성 ────────────────────
        tts_audio_path = None
        community_type = project.get("communityType", "")
        if community_type == "NEWS" and subtitles:
            script_text = " ".join(s.get("content", "") for s in subtitles)
            if script_text.strip():
                logger.info("[3b/6] Edge TTS 실행 중 (뉴스 내레이션)...")
                try:
                    tts_audio_path = tts.text_to_speech(
                        script_text,
                        os.path.join(work_dir, f"tts_{jobId[:8]}.mp3"),
                    )
                    backend_api.send_callback(jobId, 48, "PROCESSING")
                except Exception as e:
                    logger.warning("TTS 실패 (자막만 사용): %s", e)

        # ─── Step 5: FFmpeg 숏폼 영상 생성 ─────────────────────
        logger.info(
            "[4/6] 영상 생성 중... (platform=%s, preview=%s)",
            outputPlatform, isPreview,
        )

        # BGM 파일 경로 (향후 MinIO에서 로드, 현재는 스킵)
        bgm_path = _resolve_bgm_path(bgmTrackId, work_dir)

        output_video = editor.create_shortform(
            media_files=media_files,
            srt_path=srt_path,
            output_dir=out_dir,
            output_platform=outputPlatform,
            is_preview=isPreview,
            bgm_path=bgm_path,
            tts_audio_path=tts_audio_path,
            include_watermark=includeWatermark,
            job_id=jobId,
            temp_dir=work_dir,
        )

        backend_api.send_callback(jobId, 80, "PROCESSING")

        # ─── Step 6: MinIO 업로드 ────────────────────────────────
        logger.info("[5/6] MinIO 업로드 중...")
        try:
            object_name = storage.upload_render(
                file_path=output_video,
                project_id=projectId,
                job_id=jobId,
                is_preview=isPreview,
            )
            # Presigned URL 생성 (백엔드/프론트엔드에서 접근 가능)
            presigned_url = storage.get_presigned_url(object_name, expires_hours=48)
            output_file_path = presigned_url
        except Exception as e:
            logger.warning("MinIO 업로드 실패 (로컬 경로 사용): %s", e)
            output_file_path = output_video

        backend_api.send_callback(jobId, 95, "PROCESSING")

        # ─── Step 7: 완료 콜백 ──────────────────────────────────
        logger.info("[6/6] 렌더 완료 | output=%s", output_file_path)
        backend_api.send_callback(
            jobId, 100, "COMPLETED",
            output_file_path=output_file_path,
        )

        return {"status": "COMPLETED", "outputFilePath": output_file_path}

    except Exception as exc:
        error_msg = str(exc)
        logger.error(
            "렌더 실패 | jobId=%s, error=%s\n%s",
            jobId, error_msg, traceback.format_exc(),
        )

        # Celery 재시도 여부 판단
        if self.request.retries < self.max_retries:
            backend_api.send_callback(
                jobId, 0, "PROCESSING",
                error_message=f"재시도 중 ({self.request.retries + 1}/{self.max_retries}): {error_msg}",
            )
            raise self.retry(exc=exc, countdown=30 * (self.request.retries + 1))
        else:
            backend_api.send_callback(jobId, 0, "FAILED", error_message=error_msg)
            return {"status": "FAILED", "error": error_msg}

    finally:
        # 임시 작업 폴더 정리 (최종 출력물 제외)
        _cleanup_work_dir(work_dir, out_dir)


def _collect_media_files(
    media_items: list[dict],
    work_dir: str,
    job_id: str,
) -> list[dict]:
    """
    백엔드 미디어 아이템 목록에서 실제 파일을 수집합니다.

    - YouTube URL → yt-dlp 다운로드
    - localPath 있음 → 직접 사용
    - sourceUrl 있음 (이미지 등) → HTTP 다운로드

    Returns:
        [{"type": "VIDEO"|"IMAGE"|"GIF", "path": "...", "order": 0}, ...]
    """
    collected = []

    # orderIndex 기준 정렬, TEXT 타입 제외
    items = sorted(
        [m for m in media_items if m.get("mediaType") in ("VIDEO", "IMAGE")],
        key=lambda m: m.get("orderIndex", 0),
    )

    for item in items:
        media_type = item.get("mediaType", "IMAGE")
        is_gif = item.get("gif", False)
        source_url = item.get("sourceUrl") or ""
        local_path = item.get("localPath") or ""
        item_id = item.get("id", 0)

        # 노출 시간: exposureEndTime - exposureStartTime, 없으면 durationSeconds(VIDEO) 또는 4.0(IMAGE)
        exp_start = item.get("exposureStartTime")
        exp_end = item.get("exposureEndTime")
        if exp_start is not None and exp_end is not None and exp_end > exp_start:
            duration_sec = exp_end - exp_start
        elif media_type == "VIDEO" and item.get("durationSeconds"):
            duration_sec = float(item["durationSeconds"])
        else:
            duration_sec = 4.0  # IMAGE 기본

        file_type = "GIF" if is_gif else media_type

        try:
            if _is_youtube_url(source_url):
                # YouTube → yt-dlp 다운로드
                logger.info("YouTube 다운로드: %s", source_url)
                local = downloader.download_youtube(source_url, work_dir)
                collected.append({
                    "type": "VIDEO",
                    "path": local,
                    "order": item.get("orderIndex", 0),
                    "duration": duration_sec,
                })

            elif local_path and os.path.exists(local_path):
                # 이미 로컬에 있는 파일
                collected.append({
                    "type": file_type,
                    "path": local_path,
                    "order": item.get("orderIndex", 0),
                    "duration": duration_sec,
                })

            elif source_url.startswith("minio:"):
                # MinIO 오브젝트 경로 (백엔드 파일 업로드)
                object_key = source_url[6:]
                ext = _guess_extension(object_key, media_type)
                filename = f"media_{item_id}{ext}"
                local = os.path.join(work_dir, filename)
                storage.download_file(object_key, local)
                collected.append({
                    "type": file_type,
                    "path": local,
                    "order": item.get("orderIndex", 0),
                    "duration": duration_sec,
                })

            elif source_url:
                # HTTP 직접 다운로드
                ext = _guess_extension(source_url, media_type)
                filename = f"media_{item_id}{ext}"
                local = downloader.download_media_file(source_url, work_dir, filename)
                collected.append({
                    "type": file_type,
                    "path": local,
                    "order": item.get("orderIndex", 0),
                    "duration": duration_sec,
                })

            else:
                logger.warning("미디어 파일을 가져올 수 없음: itemId=%d", item_id)

        except Exception as e:
            logger.error("미디어 수집 실패: itemId=%d, error=%s", item_id, e)
            # 개별 실패는 건너뛰고 계속 진행

    # order 기준 재정렬
    collected.sort(key=lambda m: m.get("order", 0))
    logger.info("미디어 수집 완료: %d/%d개", len(collected), len(items))
    return collected


def _is_youtube_url(url: str) -> bool:
    return bool(url) and ("youtube.com" in url or "youtu.be" in url)


def _guess_extension(url: str, media_type: str) -> str:
    clean = url.split("?")[0].lower()
    for ext in (".mp4", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".mov"):
        if clean.endswith(ext):
            return ext
    return ".mp4" if media_type == "VIDEO" else ".jpg"


def _resolve_bgm_path(bgm_track_id: int | None, work_dir: str) -> str | None:
    """BGM 파일 경로 반환 (MinIO 연동은 향후 구현)"""
    if bgm_track_id is None:
        return None

    # 로컬 BGM 폴더에서 검색
    bgm_dir = settings.worker_temp_dir.replace("temp", "bgm")
    for ext in (".mp3", ".wav", ".m4a"):
        candidate = os.path.join(bgm_dir, f"bgm_{bgm_track_id}{ext}")
        if os.path.exists(candidate):
            return candidate

    logger.warning("BGM 파일을 찾을 수 없음: bgmTrackId=%d", bgm_track_id)
    return None


def _cleanup_work_dir(work_dir: str, output_dir: str) -> None:
    """작업 임시 폴더에서 출력 파일을 제외하고 정리"""
    if not os.path.exists(work_dir):
        return

    output_dir = str(Path(output_dir).resolve())
    work_dir = str(Path(work_dir).resolve())

    # work_dir과 output_dir이 같으면 정리 스킵
    if work_dir == output_dir or output_dir.startswith(work_dir):
        return

    try:
        shutil.rmtree(work_dir, ignore_errors=True)
        logger.debug("임시 폴더 정리 완료: %s", work_dir)
    except Exception as e:
        logger.warning("임시 폴더 정리 실패: %s — %s", work_dir, e)
