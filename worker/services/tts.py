"""
Edge TTS 기반 텍스트 음성 변환 서비스

- 무료, API 키 불필요
- 한국어: ko-KR-SunHiNeural (여성), ko-KR-InJoonNeural (남성)
"""

import asyncio
import os
from pathlib import Path

from utils.logger import get_logger

logger = get_logger("tts")

# 한국어 여성 음성 (기본)
DEFAULT_VOICE = "ko-KR-SunHiNeural"


def text_to_speech(
    text: str,
    output_path: str,
    voice: str = DEFAULT_VOICE,
) -> str:
    """
    텍스트를 Edge TTS로 음성 파일로 변환합니다.

    Args:
        text:        TTS할 텍스트 (전체 스크립트)
        output_path: 출력 MP3 파일 경로
        voice:       Edge TTS 음성 (기본: ko-KR-SunHiNeural)

    Returns:
        생성된 오디오 파일의 절대 경로

    Raises:
        RuntimeError: TTS 생성 실패 시
    """
    text = (text or "").strip()
    if not text:
        raise ValueError("TTS할 텍스트가 비어 있습니다.")

    output_path = str(Path(output_path).resolve())
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)

    async def _run():
        import edge_tts

        communicate = edge_tts.Communicate(text, voice)
        await communicate.save(output_path)

    try:
        asyncio.run(_run())
        duration = _get_audio_duration(output_path)
        logger.info("Edge TTS 완료: %s (%.1f초)", os.path.basename(output_path), duration)
        return output_path
    except Exception as e:
        logger.error("Edge TTS 실패: %s", e, exc_info=True)
        raise RuntimeError(f"TTS 생성 실패: {e}") from e


def _get_audio_duration(path: str) -> float:
    """ffprobe로 오디오 길이(초) 조회"""
    import subprocess

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
    except Exception as e:
        logger.warning("오디오 길이 조회 실패: %s", e)
    return 0.0
