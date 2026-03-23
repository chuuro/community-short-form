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


# ─────────────────────────────────────────────────────────────────
# TTS 자막 타임스탬프 정렬 (핵심: 싱크 문제 해결)
# ─────────────────────────────────────────────────────────────────

def _get_audio_duration_local(path: str) -> float:
    """ffprobe로 오디오/영상 길이(초) 조회"""
    try:
        result = subprocess.run(
            [
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                path,
            ],
            capture_output=True, text=True, timeout=10,
        )
        if result.returncode == 0 and result.stdout.strip():
            return float(result.stdout.strip())
    except Exception:
        pass
    return 0.0


def _align_subtitles_to_words(
    subtitles: list[dict],
    words: list[dict],
) -> list[dict]:
    """
    원본 자막 텍스트의 단어 수 비율로 Whisper 단어 타임스탬프를 정밀 분배합니다.

    알고리즘:
    - 각 자막 줄의 어절(공백 기준) 수를 계산
    - 전체 단어 스트림에서 비율에 맞게 단어 구간을 할당
    - 할당된 구간의 첫/마지막 단어 시간을 자막 start/end로 사용
    """
    if not words or not subtitles:
        return subtitles

    total_words = len(words)
    subtitle_word_counts = [max(1, len(s.get("content", "").split())) for s in subtitles]
    total_sub_words = sum(subtitle_word_counts)

    aligned = []
    word_ptr = 0

    for i, (sub, sw_count) in enumerate(zip(subtitles, subtitle_word_counts)):
        is_last = (i == len(subtitles) - 1)

        # 이 자막에 할당할 단어 수
        allocated = (total_words - word_ptr) if is_last else max(1, round(sw_count / total_sub_words * total_words))

        start_idx = min(word_ptr, total_words - 1)
        end_idx   = min(word_ptr + allocated, total_words)

        start_time = words[start_idx]["start"]
        end_time   = words[end_idx - 1]["end"] if end_idx > 0 else words[-1]["end"]

        # 이전 자막 끝보다 앞서는 경우 보정 (최소 50ms 갭)
        if aligned and start_time <= aligned[-1]["endTime"]:
            start_time = round(aligned[-1]["endTime"] + 0.05, 3)
        # end_time이 start_time보다 작은 엣지케이스 보정
        if end_time <= start_time:
            end_time = round(start_time + 0.5, 3)

        aligned.append({
            "content":   sub.get("content", ""),
            "startTime": round(start_time, 3),
            "endTime":   round(end_time,   3),
        })

        word_ptr = end_idx

    return aligned


def align_subtitles_with_tts(
    tts_audio_path: str,
    subtitles: list[dict],
    output_path: str,
    language: str = "ko",
    fonts_dir: str | None = None,
) -> str:
    """
    TTS 오디오를 Whisper로 재전사(word_timestamps=True)하여
    각 자막 줄의 실제 발화 시간을 계산한 뒤 ASS 파일을 생성합니다.

    흐름:
      TTS(mp3) → Whisper STT(word-level) → 단어 타임스탬프 정렬 → ASS

    Args:
        tts_audio_path: TTS로 생성된 오디오 파일 경로
        subtitles:      원본 자막 목록 [{"content": ..., "startTime": ..., "endTime": ...}, ...]
        output_path:    생성할 ASS 파일 경로
        language:       Whisper 언어 코드 (기본: "ko")
        fonts_dir:      한글 폰트 폴더 경로

    Returns:
        생성된 ASS 파일 경로
    """
    if not os.path.exists(tts_audio_path):
        raise FileNotFoundError(f"TTS 오디오 파일 없음: {tts_audio_path}")

    if not subtitles:
        logger.warning("자막이 없어 빈 ASS 파일 생성")
        return subtitles_to_ass([], output_path, fonts_dir=fonts_dir)

    logger.info(
        "TTS 자막 타임스탬프 정렬 시작: %s (%d개 자막)",
        os.path.basename(tts_audio_path), len(subtitles),
    )

    model = _get_model()
    import whisper

    try:
        result = model.transcribe(
            tts_audio_path,
            language=language,
            word_timestamps=True,
            verbose=False,
            fp16=False,
        )
    except Exception as e:
        logger.error("Whisper 전사 실패: %s — 원본 자막 타임스탬프 사용", e)
        return subtitles_to_ass(subtitles, output_path, fonts_dir=fonts_dir)

    segments = result.get("segments", [])

    # 단어 단위 타임스탬프 추출
    words: list[dict] = []
    for seg in segments:
        seg_words = seg.get("words", [])
        if seg_words:
            for w in seg_words:
                word_text = w.get("word", "").strip()
                if word_text:
                    words.append({
                        "word":  word_text,
                        "start": float(w.get("start", seg["start"])),
                        "end":   float(w.get("end",   seg["end"])),
                    })
        else:
            # word_timestamps 미지원 모델 → segment 단위로 폴백
            seg_text = seg.get("text", "").strip()
            if seg_text:
                words.append({
                    "word":  seg_text,
                    "start": float(seg["start"]),
                    "end":   float(seg["end"]),
                })

    if not words:
        # Whisper 결과 없음 → TTS 총 길이를 자막 수로 균등 분배
        logger.warning("Whisper 단어 결과 없음 — TTS 길이 기반 균등 분배 적용")
        total_dur = _get_audio_duration_local(tts_audio_path)
        if total_dur > 0:
            per_sub = total_dur / len(subtitles)
            fallback = [
                {
                    "content":   sub.get("content", ""),
                    "startTime": round(i * per_sub, 3),
                    "endTime":   round((i + 1) * per_sub - 0.05, 3),
                }
                for i, sub in enumerate(subtitles)
            ]
            return subtitles_to_ass(fallback, output_path, fonts_dir=fonts_dir)
        return subtitles_to_ass(subtitles, output_path, fonts_dir=fonts_dir)

    # 단어-자막 정렬
    aligned = _align_subtitles_to_words(subtitles, words)

    tts_total = aligned[-1]["endTime"] if aligned else 0
    logger.info(
        "TTS 자막 정렬 완료: %d개 자막, 총 %.1f초 (Whisper 단어 %d개)",
        len(aligned), tts_total, len(words),
    )

    return subtitles_to_ass(aligned, output_path, fonts_dir=fonts_dir)
