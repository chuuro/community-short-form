# 한글 자막용 Noto Sans CJK KR 폰트 다운로드 (로컬 Worker 실행 시 필요)
$fontsDir = Join-Path $PSScriptRoot ".." "fonts"
$fontPath = Join-Path $fontsDir "NotoSansCJKkr-Regular.otf"
$url = "https://github.com/notofonts/noto-cjk/raw/main/Sans/OTF/Korean/NotoSansCJKkr-Regular.otf"

if (Test-Path $fontPath) {
    Write-Host "폰트가 이미 있습니다: $fontPath"
    exit 0
}

New-Item -ItemType Directory -Force -Path $fontsDir | Out-Null
Write-Host "한글 폰트 다운로드 중... (약 16MB)"
try {
    Invoke-WebRequest -Uri $url -OutFile $fontPath -UseBasicParsing
    Write-Host "완료: $fontPath"
} catch {
    Write-Error "다운로드 실패: $_"
    exit 1
}
