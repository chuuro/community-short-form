# Docker ext4.vhdx 삭제 스크립트 (C드라이브 용량 확보)
# 주의: Docker의 모든 데이터(이미지, 컨테이너, 볼륨)가 삭제됩니다.
# 실행 후 Docker Desktop을 다시 시작하면 새로 초기화됩니다.

$vhdxPath = "$env:LOCALAPPDATA\Docker\wsl\data\ext4.vhdx"

Write-Host "============================================" -ForegroundColor Yellow
Write-Host "Docker ext4.vhdx 삭제" -ForegroundColor Yellow
Write-Host "경로: $vhdxPath" -ForegroundColor Gray
Write-Host ""
Write-Host "주의: 모든 Docker 데이터가 삭제됩니다!" -ForegroundColor Red
Write-Host "  - 이미지, 컨테이너, 볼륨 모두 삭제" -ForegroundColor Red
Write-Host "  - docker-compose up 시 이미지 재다운로드 필요" -ForegroundColor Red
Write-Host "============================================" -ForegroundColor Yellow
Write-Host ""

if (-not (Test-Path $vhdxPath)) {
    Write-Host "파일이 없습니다: $vhdxPath" -ForegroundColor Gray
    exit 0
}

$size = (Get-Item $vhdxPath).Length / 1GB
Write-Host "현재 크기: $([math]::Round($size, 2)) GB" -ForegroundColor Cyan
Write-Host ""

# 1. Docker Desktop 종료
Write-Host "[1/4] Docker Desktop 종료 중..." -ForegroundColor Yellow
Stop-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

# 2. WSL 종료
Write-Host "[2/4] WSL 종료 중..." -ForegroundColor Yellow
wsl --shutdown
Start-Sleep -Seconds 5

# 3. vhdx 삭제
Write-Host "[3/4] ext4.vhdx 삭제 중..." -ForegroundColor Yellow
try {
    Remove-Item $vhdxPath -Force -ErrorAction Stop
    Write-Host "[4/4] 삭제 완료. C드라이브에 $([math]::Round($size, 2)) GB 확보됨." -ForegroundColor Green
} catch {
    Write-Host "삭제 실패: $_" -ForegroundColor Red
    Write-Host "Docker Desktop이 완전히 종료되었는지 확인 후 수동으로 삭제하세요." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Docker Desktop을 다시 실행하면 새 vhdx가 생성됩니다." -ForegroundColor Gray
