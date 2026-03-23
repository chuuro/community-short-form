"""
Whisper STT (Speech-to-Text) 서비스

영상/오디오 파일에서 음성을 인식해 SRT 자막 파일을 생성합니다.
"""

import os
import subprocess
from pathlib import Path

from config import settings
from utils.logger import get_logger

logger = get_logger("transcriber")

_whisper_model = None


def _get_model():
    """Whisper 모델 싱글턴 (지연 로드)"""
    global _whisper_model
    if _whisper_model is None:
        import whisper

        logger.info("Whisper 모델 로드 중: %s", settings.whisper_model)
        _whisper_model = whisper.load_model(settings.whisper_model)
        logger.info("Whisper 모델 로드 완료")
    return _whisper_model


def transcribe_to_srt(
    video_path: str,
    output_dir: str | None = None,
    language: str | None = None,
) -> str:
    """
    영상/오디오에서 Whisper STT 실행 후 SRT 파일 생성

    Args:
        video_path:  입력 영상/오디오 파일 경로
        output_dir:  SRT 파일 저장 폴더 (None이면 video_path와 같은 폴더)
        language:    언어 코드 (None이면 자동 감지, 'ko', 'en' 등)

    Returns:
        생성된 SRT 파일의 절대 경로

    Raises:
        RuntimeError: 전사 실패 시
    """
    video_path = str(Path(video_path).resolve())
    if not os.path.exists(video_path):
        raise FileNotFoundError(f"영상 파일을 찾을 수 없음: {video_path}")

    if output_dir is None:
        output_dir = str(Path(video_path).parent)

    os.makedirs(output_dir, exist_ok=True)

    stem = Path(video_path).stem
    srt_path = os.path.join(output_dir, f"{stem}.srt")

    logger.info("Whisper 전사 시작: %s", os.path.basename(video_path))

    try:
        model = _get_model()
        import whisper

        result = model.transcribe(
            video_path,
            language=language,
            verbose=False,
            word_timestamps=False,
            fp16=False,
        )

        segments = result.get("segments", [])
        detected_lang = result.get("language", "unknown")
        logger.info(
            "전사 완료 | 언어=%s, 세그먼트=%d개",
            detected_lang,
            len(segments),
        )

        if not segments:
            logger.warning("전사 결과가 없습니다 (무음 또는 음성 없음)")
            # 빈 SRT 생성
            with open(srt_path, "w", encoding="utf-8") as f:
                f.write("")
            return srt_path

        # SRT 형식으로 저장
        srt_content = _segments_to_srt(segments)
        with open(srt_path, "w", encoding="utf-8") as f:
            f.write(srt_content)

        logger.info("SRT 저장 완료: %s", srt_path)
        return srt_path

    except Exception as e:
        logger.error("Whisper 전사 실패: %s — %s", video_path, e, exc_info=True)
        raise RuntimeError(f"Whisper 전사 실패: {e}") from e


def _segments_to_srt(segments: list[dict]) -> str:
    """Whisper 세그먼트 목록을 SRT 포맷 문자열로 변환"""
    lines = []
    for i, seg in enumerate(segments, start=1):
        start = _seconds_to_srt_time(seg["start"])
        end = _seconds_to_srt_time(seg["end"])
        text = seg["text"].strip()
        lines.append(f"{i}\n{start} --> {end}\n{text}\n")
    return "\n".join(lines)


def _seconds_to_srt_time(seconds: float) -> str:
    """초(float)를 SRT 타임코드 형식(HH:MM:SS,mmm)으로 변환"""
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    millis = int((seconds - int(seconds)) * 1000)
    return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"


def subtitles_to_srt(
    subtitles: list[dict],
    output_path: str,
) -> str:
    """
    백엔드에서 받은 SubtitleResponse 목록을 SRT 파일로 변환

    Args:
        subtitles:   백엔드 자막 목록 [{"content": "...", "startTime": 1.0, "endTime": 3.5}, ...]
        output_path: 저장할 SRT 파일 경로

    Returns:
        저장된 SRT 파일 경로
    """
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)

    lines = []
    for i, sub in enumerate(subtitles, start=1):
        start = _seconds_to_srt_time(float(sub.get("startTime", 0)))
        end = _seconds_to_srt_time(float(sub.get("endTime", 0)))
        text = sub.get("content", "").strip()
        if text:
            lines.append(f"{i}\n{start} --> {end}\n{text}\n")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    logger.info("SRT 생성 완료 (%d개): %s", len(lines), output_path)
    return output_path


def _seconds_to_ass_time(seconds: float) -> str:
    """초(float)를 ASS 타임코드 형식(H:MM:SS.cc)으로 변환"""
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    centisecs = int((seconds - int(seconds)) * 100)
    return f"{hours}:{minutes:02d}:{secs:02d}.{centisecs:02d}"


def _discover_korean_font_name(fonts_dir: str) -> str:
    """fc-query로 한글 폰트 실제 이름 조회"""
    try:
        font_file = os.path.join(fonts_dir, "NotoSansCJKkr-Regular.otf")
        if os.path.isfile(font_file):
            r = subprocess.run(
                ["fc-query", "--format", "%{family}", font_file],
                capture_output=True, text=True, timeout=5,
            )
            if r.returncode == 0 and r.stdout.strip():
                return r.stdout.strip().split(",")[0].strip()
    except Exception:
        pass
    return "Noto Sans CJK KR"


def subtitles_to_ass(
    subtitles: list[dict],
    output_path: str,
    font_name: str | None = None,
    font_size: int = 28,
    fonts_dir: str | None = None,
) -> str:
    """
    백엔드 자막을 ASS 형식으로 변환 (한글 폰트 명시, tofu 방지)
    """
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    if font_name is None and fonts_dir:
        font_name = _discover_korean_font_name(fonts_dir)
    if font_name is None:
        font_name = "Noto Sans CJK KR"

    # ASS 헤더: 한글 폰트 명시
    header = f"""[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 0

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,{font_name},{font_size},&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,1,2,40,40,60,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    def _ass_escape(t: str) -> str:
        """ASS 특수문자 이스케이프: \\ -> \\\\, { -> \\{, } -> \\}, 줄바꿈 -> \\N"""
        return t.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("\n", "\\N")

    events = []
    for sub in subtitles:
        start = _seconds_to_ass_time(float(sub.get("startTime", 0)))
        end = _seconds_to_ass_time(float(sub.get("endTime", 0)))
        text = _ass_escape(sub.get("content", "").strip())
        if text:
            events.append(f"Dialogue: 0,{start},{end},Default,,0,0,0,,{text}")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(header + "\n".join(events))

    logger.info("ASS 생성 완료 (%d개, Font=%s): %s", len(events), font_name, output_path)
    return output_path


def extract_audio(video_path: str, output_dir: str | None = None) -> str:
    """
    영상에서 오디오만 추출 (WAV 16kHz, mono — Whisper 최적화)

    Args:
        video_path:  입력 영상 파일
        output_dir:  저장 폴더

    Returns:
        추출된 WAV 파일 경로
    """
    video_path = str(Path(video_path).resolve())
    if output_dir is None:
        output_dir = str(Path(video_path).parent)

    stem = Path(video_path).stem
    audio_path = os.path.join(output_dir, f"{stem}_audio.wav")

    cmd = [
        "ffmpeg", "-y",
        "-i", video_path,
        "-vn",
        "-acodec", "pcm_s16le",
        "-ar", "16000",
        "-ac", "1",
        audio_path,
    ]

    logger.info("오디오 추출 중: %s", os.path.basename(video_path))
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        raise RuntimeError(f"오디오 추출 실패: {result.stderr}")

    logger.info("오디오 추출 완료: %s", audio_path)
    return audio_path
