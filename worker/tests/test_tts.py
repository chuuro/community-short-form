"""Edge TTS 서비스 단위 테스트 (API 키 불필요)"""
import os
import tempfile
import sys

# worker 루트를 path에 추가
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from services import tts


def test_text_to_speech_basic():
    """기본 TTS 생성 테스트"""
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, "test.mp3")
        result = tts.text_to_speech("안녕하세요. 테스트입니다.", out)
        assert os.path.exists(result)
        assert result.endswith(".mp3")
        assert os.path.getsize(result) > 0
    print("[PASS] test_text_to_speech_basic")


def test_text_to_speech_empty_raises():
    """빈 텍스트 시 ValueError"""
    try:
        tts.text_to_speech("", "/tmp/out.mp3")
        assert False, "Expected ValueError"
    except ValueError as e:
        assert "비어" in str(e) or "empty" in str(e).lower()
    print("[PASS] test_text_to_speech_empty_raises")


if __name__ == "__main__":
    test_text_to_speech_basic()
    test_text_to_speech_empty_raises()
    print("\nAll TTS tests passed.")
