#!/usr/bin/env python3
"""
한글 자막 렌더링 테스트 - 폰트/FFmpeg 검증
실행: docker run --rm -v D:/community-shortform/output/news-articles/worker-temp:/app/temp community-shortform-worker python /app/scripts/test_korean_subtitle.py
"""
import os
import subprocess
import sys

def main():
    work_dir = os.environ.get("WORKER_TEMP_DIR", "/app/temp")
    os.makedirs(work_dir, exist_ok=True)
    
    # 1. 폰트 디렉토리 확인
    fonts_dir = os.environ.get("WORKER_FONTS_DIR", "/app/fonts")
    print(f"[1] fonts_dir={fonts_dir} exists={os.path.isdir(fonts_dir)}")
    if os.path.isdir(fonts_dir):
        for f in os.listdir(fonts_dir):
            print(f"    - {f}")
    
    # 2. fc-list로 폰트 이름 확인
    try:
        r = subprocess.run(["fc-list", ":lang=ko", "family"], capture_output=True, text=True, timeout=5)
        print(f"[2] fc-list :lang=ko (first 5):")
        for line in (r.stdout or "").strip().split("\n")[:5]:
            print(f"    {line}")
    except Exception as e:
        print(f"[2] fc-list error: {e}")
    
    # 3. 테스트 ASS 파일 생성
    ass_path = os.path.join(work_dir, "test_korean.ass")
    ass_content = """[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Noto Sans CJK KR,28,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,1,2,40,40,60,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,한글 테스트입니다. 가나다라마바사
"""
    with open(ass_path, "w", encoding="utf-8") as f:
        f.write(ass_content)
    print(f"[3] ASS created: {ass_path}")
    
    # 4. FFmpeg 테스트 (1x1 검은색 영상 + 자막)
    out_path = os.path.join(work_dir, "test_korean_out.mp4")
    safe_ass = ass_path.replace("\\", "/").replace(":", "\\:")
    vf = f"ass='{safe_ass}':fontsdir='{fonts_dir}':shaping=complex"
    cmd = [
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", "color=c=black:s=1080x1920:d=5",
        "-vf", vf,
        "-c:v", "libx264", "-preset", "ultrafast",
        "-t", "5",
        out_path
    ]
    print(f"[4] FFmpeg: {' '.join(cmd)}")
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if r.returncode != 0:
        print(f"    FAILED: {r.stderr[-500:]}")
        return 1
    print(f"    OK: {out_path} ({os.path.getsize(out_path)} bytes)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
