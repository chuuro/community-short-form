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

# ─── 숏폼 자막 상수 ────────────────────────────────────────────────────────
MAX_SCREEN_CHARS = 22  # 한 화면(2줄) 최대 글자수 — 한 줄당 ~11자 × 2줄
MAX_LINE_CHARS = 11    # 한 줄 최대 글자수 (ASS \N 줄바꿈 기준)
SUBTITLE_FONTSIZE = 60  # 1080×1920 기준 읽기 좋은 크기
TITLE_FONTSIZE = 78     # 첫 화면 제목
TITLE_DURATION = 3.5    # 제목 표시 시간(초)

# 자연스러운 문장 끝맺음 부호 (이 뒤에서 화면 분리 우선)
_SENTENCE_END = frozenset("?!.~…。")
# 보조 분리 부호 (문장 끝보다 낮은 우선순위)
_CLAUSE_END = frozenset(",，、")
# 이 글자수 미만의 화면은 이전 화면에 이어 붙임 (예: "합니다.", "거예요" 단독 방지)
MIN_SCREEN_CHARS = 8


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
    """폰트 디렉토리에서 사용 가능한 폰트 이름을 우선순위 순으로 반환"""
    # 1순위: Jua (디즈니풍 귀여운 폰트)
    jua_path = os.path.join(fonts_dir, "Jua-Regular.ttf")
    if os.path.isfile(jua_path):
        try:
            r = subprocess.run(
                ["fc-query", "--format", "%{family}", jua_path],
                capture_output=True, text=True, timeout=5,
            )
            if r.returncode == 0 and r.stdout.strip():
                name = r.stdout.strip().split(",")[0].strip()
                logger.info("디즈니풍 폰트 감지: %s", name)
                return name
        except Exception:
            pass
        return "Jua"  # fontconfig 없으면 직접 이름 반환

    # 2순위: NotoSansCJKkr
    noto_path = os.path.join(fonts_dir, "NotoSansCJKkr-Regular.otf")
    if os.path.isfile(noto_path):
        try:
            r = subprocess.run(
                ["fc-query", "--format", "%{family}", noto_path],
                capture_output=True, text=True, timeout=5,
            )
            if r.returncode == 0 and r.stdout.strip():
                return r.stdout.strip().split(",")[0].strip()
        except Exception:
            pass
    return "Noto Sans CJK KR"


def _split_text_to_shortform_lines(text: str, max_chars: int = MAX_LINE_CHARS) -> list[str]:
    """
    자막 텍스트를 숏폼 2줄 스타일로 분할합니다.

    전략 (우선순위 순):
      1. 문장 끝 부호(?, !, ., ~, …) 뒤에서 화면 분리
      2. 어절(공백) 경계에서 max_chars 이하로 분리
      3. 위 둘 다 불가시 강제 글자 수 분리

    각 화면은 최대 2줄(ASS \\N 사용), 줄당 max_chars 이하.
    """
    text = text.strip()
    if not text:
        return [""]

    # ── 1단계: 문장 부호 기준으로 1차 분리 ──────────────────────────
    sentences = _split_by_sentence_boundary(text)

    screens: list[str] = []

    for sentence in sentences:
        sentence = sentence.strip()
        if not sentence:
            continue

        # 이 문장이 2줄(22자) 이내면 그대로 한 화면
        if len(sentence) <= max_chars * 2:
            # 2줄 포맷: 11자 넘으면 ASS \N 으로 줄바꿈
            screens.append(_wrap_two_lines(sentence, max_chars))
        else:
            # 문장이 길면 어절 단위로 22자씩 분리
            chunks = _chunk_by_words(sentence, max_chars * 2)
            for chunk in chunks:
                screens.append(_wrap_two_lines(chunk, max_chars))

    # ── 너무 짧은 화면 병합 (~합니다, ~거예요 단독 방지) ──────────────
    screens = _merge_short_screens(screens, MIN_SCREEN_CHARS, max_chars)
    return screens if screens else [text]


def _split_by_sentence_boundary(text: str) -> list[str]:
    """
    문장 끝 부호(?, !, ., ~)를 기준으로 텍스트를 분리합니다.
    부호는 앞 문장에 포함됩니다. e.g. "알고 계셨나요? 최근" → ["알고 계셨나요?", "최근"]
    """
    result: list[str] = []
    current: list[str] = []

    i = 0
    while i < len(text):
        ch = text[i]
        current.append(ch)

        # 문장 끝 부호 감지
        if ch in _SENTENCE_END:
            # 공백 포함해서 다음까지 같이 끊기
            seg = "".join(current).strip()
            if seg:
                result.append(seg)
            current = []
        i += 1

    # 남은 텍스트
    tail = "".join(current).strip()
    if tail:
        result.append(tail)

    # 분리된 게 없으면 원본 그대로 반환
    return result if result else [text]


def _wrap_two_lines(text: str, max_per_line: int = MAX_LINE_CHARS) -> str:
    """
    텍스트를 최대 2줄로 감싸 ASS \\N 줄바꿈 포함 문자열로 반환합니다.
    - 한 줄이면 그대로 반환
    - 두 줄이면 균등 분할 후 \\N 삽입
    """
    text = text.strip()
    if len(text) <= max_per_line:
        return text

    # 어절 경계에서 가장 균형 잡힌 분할점 찾기
    words = text.split()
    if len(words) == 1:
        # 공백 없는 긴 단어: 정중앙 분할
        mid = len(text) // 2
        return text[:mid] + "\n" + text[mid:]

    best_split = 1
    best_diff = float("inf")
    acc = 0
    for wi, w in enumerate(words):
        acc += len(w) + (1 if wi > 0 else 0)
        if wi < len(words) - 1:
            remaining = len(text) - acc
            diff = abs(acc - remaining)
            if diff < best_diff:
                best_diff = diff
                best_split = wi + 1

    line1 = " ".join(words[:best_split])
    line2 = " ".join(words[best_split:])
    return line1 + "\n" + line2


def _chunk_by_words(text: str, max_chars: int) -> list[str]:
    """어절 경계에서 max_chars 이하로 텍스트를 청크 분할합니다."""
    words = text.split()
    chunks: list[str] = []
    current: list[str] = []
    current_len = 0

    for word in words:
        wlen = len(word)
        new_len = current_len + (1 if current else 0) + wlen
        if current and new_len > max_chars:
            chunks.append(" ".join(current))
            current = [word]
            current_len = wlen
        else:
            current.append(word)
            current_len = new_len

    if current:
        chunks.append(" ".join(current))

    # 공백 없는 초장문 처리
    result: list[str] = []
    for chunk in chunks:
        if len(chunk) <= max_chars:
            result.append(chunk)
        else:
            for i in range(0, len(chunk), max_chars):
                result.append(chunk[i:i + max_chars])

    return result if result else [text]


def _merge_short_screens(
    screens: list[str],
    min_chars: int = MIN_SCREEN_CHARS,
    max_per_line: int = MAX_LINE_CHARS,
) -> list[str]:
    """
    글자 수가 너무 적은 화면(~합니다, ~거예요, ~할까요? 등)을
    바로 앞 화면에 이어 붙여 자연스러운 흐름을 만듭니다.

    병합 조건:
      - 이전 화면이 존재
      - 현재 화면의 순수 글자 수(\\N 제외)가 min_chars 미만
      - 병합 결과가 max_per_line * 3 이하 (초과시 그냥 두기)
    """
    if len(screens) <= 1:
        return screens

    result: list[str] = []
    for screen in screens:
        plain = screen.replace("\n", " ").strip()
        if result and len(plain) < min_chars:
            # 이전 화면과 합치기
            prev_plain = result[-1].replace("\n", " ").strip()
            merged = prev_plain + " " + plain
            if len(merged) <= max_per_line * 3:
                result[-1] = _wrap_two_lines(merged, max_per_line)
                continue
        result.append(screen)

    return result if result else screens


def _split_subtitles_for_shortform(
    subtitles: list[dict],
    max_chars: int = MAX_LINE_CHARS,
) -> list[dict]:
    """
    자막 목록을 숏폼용 화면 단위로 분할합니다.
    - 각 화면은 최대 2줄 (ASS \\N 줄바꿈 포함 단일 Dialogue 이벤트)
    - 문장 부호 기준 우선 분리 → 자막량이 균형 잡힘
    - 각 원본 자막의 타이밍을 글자 비율로 재배분
    """
    result: list[dict] = []
    for sub in subtitles:
        text = sub.get("content", "").strip()
        start_t = float(sub.get("startTime", 0))
        end_t = float(sub.get("endTime", 0))
        duration = max(0.2, end_t - start_t)

        screens = _split_text_to_shortform_lines(text, max_chars)

        if len(screens) == 1:
            # 단일 화면: 원본 타이밍 그대로 (\\N 포함 가능)
            result.append({
                "content":   screens[0],
                "startTime": start_t,
                "endTime":   end_t,
            })
            continue

        # 여러 화면: 글자 수 비율로 타이밍 배분
        # \n 제거한 순수 글자 수로 비율 계산
        plain_lens = [len(s.replace("\n", "")) for s in screens]
        total_chars = sum(plain_lens) or 1
        cursor = start_t
        for i, (screen, plen) in enumerate(zip(screens, plain_lens)):
            is_last = (i == len(screens) - 1)
            if is_last:
                screen_end = end_t
            else:
                ratio = plen / total_chars
                screen_end = round(cursor + duration * ratio, 3)
            result.append({
                "content":   screen,
                "startTime": round(cursor, 3),
                "endTime":   screen_end,
            })
            cursor = screen_end

    return result


def subtitles_to_ass(
    subtitles: list[dict],
    output_path: str,
    font_name: str | None = None,
    font_size: int = SUBTITLE_FONTSIZE,
    fonts_dir: str | None = None,
    title_text: str | None = None,
    title_duration: float = TITLE_DURATION,
    split_lines: bool = True,
    max_line_chars: int = MAX_LINE_CHARS,
) -> str:
    """
    백엔드 자막을 디즈니풍 ASS 형식으로 변환

    - Jua 폰트 (디즈니 귀여운 스타일) 우선 사용, 없으면 NotoSansCJK 폴백
    - 글자 배경 없음, 굵은 외곽선만 (숏폼 스탠다드)
    - 자막을 max_line_chars 이하로 자동 분할
    - title_text 있으면 첫 화면에 제목 표시
    """
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)

    if font_name is None and fonts_dir:
        font_name = _discover_korean_font_name(fonts_dir)
    if font_name is None:
        font_name = "Jua"

    # ── Jua 폰트 파일 경로 (ASS fontsdir 옵션용) ──────────────────────────
    jua_file = ""
    if fonts_dir:
        candidate = os.path.join(fonts_dir, "Jua-Regular.ttf")
        if os.path.isfile(candidate):
            jua_file = candidate.replace("\\", "/")

    # ── ASS 헤더 ─────────────────────────────────────────────────────────
    # BorderStyle=1: 윤곽선+그림자 (배경 박스 없음)
    # BackColour=&HFF000000: 완전 투명 (그림자 없음)
    # Alignment=2: 하단 중앙
    header = f"""[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 0
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,{font_name},{font_size},&H00FFFFFF,&H000000FF,&H00000000,&HFF000000,-1,0,0,0,100,100,1,0,1,4,0,2,60,60,120,1
Style: Title,{font_name},{TITLE_FONTSIZE},&H00FFFFFF,&H000000FF,&H00330000,&HFF000000,-1,0,0,0,100,100,2,0,1,5,0,5,80,80,200,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    # ── 이스케이프 ────────────────────────────────────────────────────────
    def _ass_escape(t: str) -> str:
        return t.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("\n", "\\N")

    # ── 자막 분할 ─────────────────────────────────────────────────────────
    work_subs = subtitles
    if split_lines:
        work_subs = _split_subtitles_for_shortform(subtitles, max_line_chars)

    # ── 이벤트 생성 ───────────────────────────────────────────────────────
    events: list[str] = []

    # 제목 카드 (첫 화면)
    if title_text:
        title_end = _seconds_to_ass_time(title_duration)
        safe_title = _ass_escape(title_text.strip())
        events.append(f"Dialogue: 0,0:00:00.00,{title_end},Title,,0,0,0,,{safe_title}")

    # 일반 자막
    for sub in work_subs:
        start = _seconds_to_ass_time(float(sub.get("startTime", 0)))
        end = _seconds_to_ass_time(float(sub.get("endTime", 0)))
        text = _ass_escape(sub.get("content", "").strip())
        if text:
            events.append(f"Dialogue: 0,{start},{end},Default,,0,0,0,,{text}")

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(header + "\n".join(events))

    logger.info(
        "ASS 생성 완료 (Font=%s, Size=%d, Lines=%d%s): %s",
        font_name, font_size, len(work_subs),
        f", Title='{title_text[:20]}'" if title_text else "",
        output_path,
    )
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
    title_text: str | None = None,
) -> str:
    """
    TTS 오디오를 Whisper로 재전사(word_timestamps=True)하여
    각 자막 줄의 실제 발화 시간을 계산한 뒤 디즈니풍 ASS 파일을 생성합니다.

    싱크 개선 전략:
      1. 원본 자막을 짧은 줄(≤12자)로 미리 분할 → Whisper 단어 할당 단위 ↑
      2. Whisper word_timestamps=True 로 단어별 정확한 시간 획득
      3. 짧은 줄 단위로 단어 타임스탬프 정밀 배분
      4. 결과를 디즈니풍 ASS로 저장
    """
    if not os.path.exists(tts_audio_path):
        raise FileNotFoundError(f"TTS 오디오 파일 없음: {tts_audio_path}")

    if not subtitles:
        logger.warning("자막이 없어 빈 ASS 파일 생성")
        return subtitles_to_ass([], output_path, fonts_dir=fonts_dir, title_text=title_text)

    logger.info(
        "TTS 자막 타임스탬프 정렬 시작: %s (%d개 자막)",
        os.path.basename(tts_audio_path), len(subtitles),
    )

    # ── 1단계: 자막을 짧은 줄로 미리 분할 (sync 핵심 개선) ──────────────
    # 짧은 줄이 많을수록 Whisper 단어를 각 줄에 더 정밀하게 배분 가능
    pre_split_subs = _split_subtitles_for_shortform(subtitles, MAX_LINE_CHARS)
    logger.info("자막 분할: %d개 → %d개 (짧은 줄)", len(subtitles), len(pre_split_subs))

    # ── 2단계: Whisper word-level 전사 ────────────────────────────────────
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
        return subtitles_to_ass(subtitles, output_path, fonts_dir=fonts_dir,
                                title_text=title_text, split_lines=True)

    segments = result.get("segments", [])

    # ── 3단계: 전체 단어 타임스탬프 수집 ─────────────────────────────────
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
            seg_text = seg.get("text", "").strip()
            if seg_text:
                words.append({
                    "word":  seg_text,
                    "start": float(seg["start"]),
                    "end":   float(seg["end"]),
                })

    if not words:
        # Whisper 결과 없음 → TTS 길이 기반 균등 분배
        logger.warning("Whisper 단어 결과 없음 — TTS 길이 기반 균등 분배 적용")
        total_dur = _get_audio_duration_local(tts_audio_path)
        if total_dur > 0:
            fallback = []
            for i, sub in enumerate(pre_split_subs):
                per = total_dur / len(pre_split_subs)
                fallback.append({
                    "content":   sub.get("content", ""),
                    "startTime": round(i * per, 3),
                    "endTime":   round((i + 1) * per - 0.05, 3),
                })
            return subtitles_to_ass(fallback, output_path, fonts_dir=fonts_dir,
                                    title_text=title_text, split_lines=False)
        return subtitles_to_ass(subtitles, output_path, fonts_dir=fonts_dir,
                                title_text=title_text, split_lines=True)

    # ── 4단계: 분할된 짧은 줄들에 단어 타임스탬프 배분 ───────────────────
    aligned = _align_subtitles_to_words(pre_split_subs, words)

    tts_total = aligned[-1]["endTime"] if aligned else 0
    logger.info(
        "TTS 자막 정렬 완료: %d개 줄(분할 후), 총 %.1f초 (Whisper 단어 %d개)",
        len(aligned), tts_total, len(words),
    )

    # split_lines=False: 이미 분할된 상태
    return subtitles_to_ass(aligned, output_path, fonts_dir=fonts_dir,
                            title_text=title_text, split_lines=False)
