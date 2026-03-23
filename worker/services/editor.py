"""
FFmpeg 기반 숏폼 영상 편집 서비스

지원 기능:
- 영상: 9:16(1080×1920) 크롭/스케일
- 이미지: 슬라이드쇼 (Ken Burns zoompan 효과)
- 자막: SRT → ASS 변환 후 burn-in
- BGM: 원본 오디오 80% + BGM 20% 믹싱
- 미리보기: 10초, 480×854 저해상도
- 워터마크: 텍스트 오버레이
"""

import os
import subprocess
import uuid
from pathlib import Path

from utils.logger import get_logger

logger = get_logger("editor")

# 숏폼 플랫폼별 해상도 (모두 9:16)
PLATFORM_RESOLUTION = {
    "YOUTUBE_SHORTS": (1080, 1920),
    "TIKTOK": (1080, 1920),
    "INSTAGRAM_REELS": (1080, 1920),
}

PREVIEW_RESOLUTION = (480, 854)
PREVIEW_DURATION = 10  # 초
IMAGE_SLIDE_DURATION = 4.0  # 이미지 1장당 노출 시간(초)


def create_shortform(
    media_files: list[dict],
    srt_path: str | None,
    output_dir: str,
    output_platform: str = "YOUTUBE_SHORTS",
    is_preview: bool = False,
    bgm_path: str | None = None,
    tts_audio_path: str | None = None,
    include_watermark: bool = False,
    job_id: str | None = None,
    temp_dir: str | None = None,
) -> str:
    """
    숏폼 영상을 생성합니다.

    Args:
        media_files:      미디어 파일 목록
                          [{"type": "VIDEO"|"IMAGE"|"GIF", "path": "...", "duration": float}, ...]
        srt_path:         자막 SRT 파일 경로 (None이면 자막 없음)
        output_dir:       출력 폴더
        output_platform:  출력 플랫폼 (YOUTUBE_SHORTS / TIKTOK / INSTAGRAM_REELS)
        is_preview:       True면 10초 저해상도 미리보기
        bgm_path:         배경음악 파일 경로 (None이면 BGM 없음)
        tts_audio_path:   TTS 오디오 파일 (뉴스 등, 제공 시 비디오 오디오 대체)
        include_watermark: 워터마크 포함 여부
        job_id:           작업 식별자 (파일명에 사용)

    Returns:
        생성된 MP4 파일의 절대 경로

    Raises:
        RuntimeError: FFmpeg 실행 실패 시
    """
    output_dir = str(Path(output_dir).resolve())
    os.makedirs(output_dir, exist_ok=True)

    # 중간 파일용 temp 디렉토리 (기본: output_dir)
    work_temp = str(Path(temp_dir).resolve()) if temp_dir else output_dir
    os.makedirs(work_temp, exist_ok=True)

    w, h = PREVIEW_RESOLUTION if is_preview else PLATFORM_RESOLUTION.get(
        output_platform, (1080, 1920)
    )

    prefix = "preview" if is_preview else "output"
    out_name = f"{prefix}_{job_id or uuid.uuid4().hex[:8]}.mp4"
    output_path = os.path.join(output_dir, out_name)

    # 미디어 분류
    video_files = [m for m in media_files if m.get("type") in ("VIDEO", "GIF")]
    image_files = [m for m in media_files if m.get("type") == "IMAGE"]

    if not video_files and not image_files:
        raise RuntimeError("편집할 미디어 파일이 없습니다.")

    if video_files:
        # 영상이 있으면 영상 기반 편집
        result_path = _edit_video(
            video_files=video_files,
            image_files=image_files,
            srt_path=srt_path,
            bgm_path=bgm_path,
            tts_audio_path=tts_audio_path,
            output_path=output_path,
            temp_dir=work_temp,
            width=w, height=h,
            is_preview=is_preview,
            include_watermark=include_watermark,
        )
    else:
        # 이미지만 있으면 슬라이드쇼
        result_path = _create_slideshow(
            image_files=image_files,
            srt_path=srt_path,
            bgm_path=bgm_path,
            tts_audio_path=tts_audio_path,
            output_path=output_path,
            temp_dir=work_temp,
            width=w, height=h,
            is_preview=is_preview,
            include_watermark=include_watermark,
        )

    file_size_mb = os.path.getsize(result_path) / (1024 * 1024)
    logger.info("영상 생성 완료: %s (%.1f MB)", os.path.basename(result_path), file_size_mb)
    return result_path


def _edit_video(
    video_files: list[dict],
    image_files: list[dict],
    srt_path: str | None,
    bgm_path: str | None,
    tts_audio_path: str | None,
    output_path: str,
    temp_dir: str,
    width: int,
    height: int,
    is_preview: bool,
    include_watermark: bool,
) -> str:
    """영상 기반 숏폼 편집 (FFmpeg concat + filter_complex)"""

    # 1단계: 각 영상을 9:16으로 정규화 (duration 있으면 해당 길이만 사용)
    normalized = []
    for i, vf in enumerate(video_files):
        norm_path = os.path.join(temp_dir, f"norm_{i}.mp4")
        duration = vf.get("duration")
        _normalize_video(vf["path"], norm_path, width, height, duration)
        normalized.append(norm_path)

    # 2단계: 이미지를 짧은 영상으로 변환 후 합치기
    if image_files:
        for i, img in enumerate(image_files):
            img_vid_path = os.path.join(temp_dir, f"img_vid_{i}.mp4")
            duration = img.get("duration", IMAGE_SLIDE_DURATION)
            _image_to_video(img["path"], img_vid_path, width, height, duration)
            normalized.append(img_vid_path)

    # 3단계: 모든 클립 concat (temp_dir 사용)
    concat_path = os.path.join(temp_dir, "concat.mp4")
    _concat_videos(normalized, concat_path)

    # 4단계: 필터 적용 (자막, BGM, TTS, 워터마크, 미리보기 트리밍)
    final_path = _apply_filters(
        input_path=concat_path,
        output_path=output_path,
        srt_path=srt_path,
        bgm_path=bgm_path,
        tts_audio_path=tts_audio_path,
        include_watermark=include_watermark,
        max_duration=PREVIEW_DURATION if is_preview else None,
        width=width,
        height=height,
    )

    # 임시 파일 정리
    for f in normalized + [concat_path]:
        try:
            os.remove(f)
        except OSError:
            pass

    return final_path


def _normalize_video(
    input_path: str,
    output_path: str,
    width: int,
    height: int,
    duration: float | None = None,
) -> None:
    """영상을 target 해상도(9:16)로 스케일+크롭. duration 있으면 해당 길이만 사용"""
    # scale: 짧은 변을 target에 맞추고, crop으로 초과분 제거
    vf = (
        f"scale={width}:{height}:force_original_aspect_ratio=increase,"
        f"crop={width}:{height},"
        f"setsar=1"
    )
    cmd = [
        "ffmpeg", "-y",
        "-i", input_path,
        "-vf", vf,
        "-c:v", "libx264",
        "-preset", "fast",
        "-crf", "23",
        "-c:a", "aac",
        "-b:a", "128k",
        "-movflags", "+faststart",
    ]
    if duration is not None and duration > 0:
        cmd.extend(["-t", str(duration)])
    cmd.append(output_path)
    _run_ffmpeg(cmd, f"normalize: {os.path.basename(input_path)}")


def _image_to_video(
    image_path: str,
    output_path: str,
    width: int,
    height: int,
    duration: float,
) -> None:
    """이미지를 Ken Burns zoompan 효과가 있는 영상으로 변환"""
    fps = 30
    total_frames = int(fps * duration)
    # zoompan: 천천히 확대
    vf = (
        f"scale={width * 2}:{height * 2},"
        f"zoompan=z='min(zoom+0.002,1.15)':d={total_frames}:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s={width}x{height}:fps={fps},"
        f"setsar=1"
    )
    cmd = [
        "ffmpeg", "-y",
        "-loop", "1",
        "-i", image_path,
        "-vf", vf,
        "-t", str(duration),
        "-c:v", "libx264",
        "-preset", "fast",
        "-crf", "23",
        "-pix_fmt", "yuv420p",
        "-an",
        output_path,
    ]
    _run_ffmpeg(cmd, f"image→video: {os.path.basename(image_path)}")


def _concat_videos(video_paths: list[str], output_path: str) -> None:
    """FFmpeg concat demuxer로 영상 이어붙이기"""
    # concat list 파일은 출력 경로와 분리된 이름 사용 (확장자 혼선 방지)
    temp_list = os.path.join(os.path.dirname(os.path.abspath(output_path)), "concat_list.txt")
    with open(temp_list, "w", encoding="utf-8") as f:
        for vp in video_paths:
            # FFmpeg concat demuxer는 경로를 list 파일 기준으로 해석하므로 절대경로 사용
            abs_path = os.path.abspath(vp).replace("\\", "/")
            f.write(f"file '{abs_path}'\n")

    cmd = [
        "ffmpeg", "-y",
        "-f", "concat",
        "-safe", "0",
        "-i", temp_list,
        "-c", "copy",
        output_path,
    ]
    try:
        _run_ffmpeg(cmd, "concat")
    finally:
        try:
            os.remove(temp_list)
        except OSError:
            pass


def _create_slideshow(
    image_files: list[dict],
    srt_path: str | None,
    bgm_path: str | None,
    tts_audio_path: str | None,
    output_path: str,
    temp_dir: str,
    width: int,
    height: int,
    is_preview: bool,
    include_watermark: bool,
) -> str:
    """이미지 슬라이드쇼 생성"""
    # TTS 있으면 총 길이에 맞춰 이미지당 노출 시간 계산
    slide_duration = IMAGE_SLIDE_DURATION
    if tts_audio_path and os.path.exists(tts_audio_path):
        tts_duration = _get_audio_duration(tts_audio_path)
        if tts_duration > 0 and image_files:
            slide_duration = tts_duration / len(image_files)
            slide_duration = max(1.0, min(slide_duration, 10.0))  # 1~10초 범위

    # 각 이미지를 영상으로 변환
    clips = []
    for i, img in enumerate(image_files):
        clip_path = os.path.join(temp_dir, f"slide_{i}.mp4")
        _image_to_video(img["path"], clip_path, width, height, slide_duration)
        clips.append(clip_path)

    # concat
    concat_path = os.path.join(temp_dir, "slideshow.mp4")
    _concat_videos(clips, concat_path)

    # 필터 적용
    final_path = _apply_filters(
        input_path=concat_path,
        output_path=output_path,
        srt_path=srt_path,
        bgm_path=bgm_path,
        tts_audio_path=tts_audio_path,
        include_watermark=include_watermark,
        max_duration=PREVIEW_DURATION if is_preview else None,
        width=width,
        height=height,
    )

    for f in clips + [concat_path]:
        try:
            os.remove(f)
        except OSError:
            pass

    return final_path


def _apply_filters(
    input_path: str,
    output_path: str,
    srt_path: str | None,
    bgm_path: str | None,
    tts_audio_path: str | None = None,
    include_watermark: bool = False,
    max_duration: float | None = None,
    width: int = 1080,
    height: int = 1920,
) -> str:
    """자막, BGM, TTS, 워터마크 적용"""

    inputs = ["-i", input_path]
    audio_input_idx = 1

    # TTS 또는 BGM 오디오 입력
    if tts_audio_path and os.path.exists(tts_audio_path):
        inputs += ["-i", tts_audio_path]
        tts_idx = audio_input_idx
        audio_input_idx += 1
    else:
        tts_idx = None

    if bgm_path and os.path.exists(bgm_path):
        inputs += ["-i", bgm_path]
        bgm_idx = audio_input_idx
    else:
        bgm_idx = None

    filter_parts = []
    # 오디오: TTS 있으면 TTS 사용 (비디오 오디오 무시), BGM 있으면 믹싱
    if tts_idx is not None:
        audio_label = f"[{tts_idx}:a]"
        if bgm_idx is not None:
            filter_parts.append(
                f"[{tts_idx}:a]volume=0.85[va];"
                f"[{bgm_idx}:a]volume=0.15[bgm];"
                "[va][bgm]amix=inputs=2:duration=first[aout]"
            )
            audio_label = "[aout]"
    elif bgm_idx is not None:
        # TTS 없고 BGM만: 원본 80%, BGM 20%
        filter_parts.append(
            "[0:a]volume=0.8[va];"
            f"[{bgm_idx}:a]volume=0.2[bgm];"
            "[va][bgm]amix=inputs=2:duration=first[aout]"
        )
        audio_label = "[aout]"
    else:
        audio_label = "[0:a]"

    # 비디오 필터 체인
    vf_parts = ["setsar=1"]

    # 자막 burn-in (한글 tofu 방지: fontconfig에 /app/fonts 등록됨)
    if srt_path and os.path.exists(srt_path):
        safe_path = srt_path.replace("\\", "/").replace(":", "\\:")
        fonts_dir = os.environ.get("WORKER_FONTS_DIR") or "/app/fonts"
        if not os.path.isdir(fonts_dir) or not os.listdir(fonts_dir):
            fonts_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "fonts")
        if not os.path.isdir(fonts_dir):
            fonts_dir = "/usr/share/fonts/opentype/noto"
        fonts_dir = fonts_dir.replace("\\", "/")
        if safe_path.lower().endswith(".ass"):
            vf_parts.append(f"ass='{safe_path}':fontsdir='{fonts_dir}':shaping=complex")
        else:
            vf_parts.append(
                f"subtitles='{safe_path}':fontsdir='{fonts_dir}':force_style='Fontname=Noto Sans CJK KR,FontSize=24,PrimaryColour=&HFFFFFF&,"
                f"OutlineColour=&H000000&,Outline=2,Alignment=2'"
            )

    # 워터마크
    if include_watermark:
        vf_parts.append(
            "drawtext=text='ShortForm AI':fontcolor=white@0.5:fontsize=24:"
            "x=w-tw-20:y=h-th-20"
        )

    cmd = ["ffmpeg", "-y"]
    cmd += inputs

    if max_duration:
        cmd += ["-t", str(max_duration)]

    # 비디오 필터
    vf = ",".join(vf_parts)
    cmd += ["-vf", vf]

    # 오디오 필터 (TTS/BGM 있으면 filter_complex)
    if filter_parts:
        cmd += ["-filter_complex", ";".join(filter_parts), "-map", "0:v", "-map", audio_label]
    elif tts_idx is not None and not filter_parts:
        # TTS만 있고 BGM 없음: 비디오 + TTS 오디오
        cmd += ["-map", "0:v", "-map", f"{tts_idx}:a"]

    cmd += [
        "-c:v", "libx264",
        "-preset", "fast",
        "-crf", "23",
        "-c:a", "aac",
        "-b:a", "128k",
        "-movflags", "+faststart",
        "-shortest",
        output_path,
    ]

    _run_ffmpeg(cmd, "apply_filters")
    return output_path


def _get_audio_duration(path: str) -> float:
    """ffprobe로 오디오 길이(초) 조회"""
    try:
        result = subprocess.run(
            [
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                path,
            ],
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode == 0 and result.stdout.strip():
            return float(result.stdout.strip())
    except Exception:
        pass
    return 0.0


def _run_ffmpeg(cmd: list[str], label: str = "") -> None:
    """FFmpeg 명령 실행"""
    logger.info("FFmpeg [%s]: %s", label, " ".join(cmd))

    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )

    if result.returncode != 0:
        error_tail = result.stderr[-1000:] if result.stderr else "(no stderr)"
        logger.error("FFmpeg 실패 [%s]:\n%s", label, error_tail)
        raise RuntimeError(f"FFmpeg 오류 [{label}]: {error_tail}")

    logger.debug("FFmpeg 완료 [%s]", label)
