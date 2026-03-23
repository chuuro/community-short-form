#!/bin/sh
# 한글 자막용 Noto Sans CJK KR 폰트 다운로드 (로컬 Worker 실행 시 필요)
FONTS_DIR="$(dirname "$0")/../fonts"
FONT_PATH="$FONTS_DIR/NotoSansCJKkr-Regular.otf"
URL="https://github.com/notofonts/noto-cjk/raw/main/Sans/OTF/Korean/NotoSansCJKkr-Regular.otf"

[ -f "$FONT_PATH" ] && echo "폰트가 이미 있습니다: $FONT_PATH" && exit 0
mkdir -p "$FONTS_DIR"
echo "한글 폰트 다운로드 중... (약 16MB)"
curl -sL -o "$FONT_PATH" "$URL" || wget -q -O "$FONT_PATH" "$URL" || { echo "다운로드 실패"; exit 1; }
echo "완료: $FONT_PATH"
