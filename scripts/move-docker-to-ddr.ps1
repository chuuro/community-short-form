# Docker Desktop 데이터(vhdx)를 D드라이브로 이동
# wsl\disk, wsl\main 폴더를 D로 옮기고 junction으로 연결합니다.
# 실행 전 Docker Desktop을 수동으로 종료(Quit)해 주세요.

$ErrorActionPreference = "Stop"

$SourceWsl = "$env:LOCALAPPDATA\Docker\wsl"
$TargetWsl = "D:\Docker\wsl"
$DataVhdx = "$SourceWsl\disk\docker_data.vhdx"
$MainVhdx = "$SourceWsl\main\ext4.vhdx"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Docker data to D drive" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Source: $SourceWsl" -ForegroundColor Gray
Write-Host "Target: $TargetWsl" -ForegroundColor Gray
Write-Host ""

# Check if already moved (junction exists)
$sourceItem = Get-Item $SourceWsl -ErrorAction SilentlyContinue
if ($sourceItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
    Write-Host "Already moved. wsl is junction -> $TargetWsl" -ForegroundColor Green
    exit 0
}

# Check vhdx exists
if (-not (Test-Path $DataVhdx)) {
    Write-Host "docker_data.vhdx not found: $DataVhdx" -ForegroundColor Yellow
    exit 0
}

$size = (Get-Item $DataVhdx).Length / 1GB
Write-Host "docker_data.vhdx size: $([math]::Round($size, 2)) GB" -ForegroundColor Cyan
Write-Host ""

# Docker Desktop check
$dockerProc = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
if ($dockerProc) {
    Write-Host "Quit Docker Desktop first (tray icon -> Quit)." -ForegroundColor Red
    exit 1
}

# 1. WSL shutdown
Write-Host "[1/5] WSL shutdown..." -ForegroundColor Yellow
wsl --shutdown
Start-Sleep -Seconds 5

# 2. Create target dir
Write-Host "[2/5] Create target: $TargetWsl" -ForegroundColor Yellow
if (Test-Path $TargetWsl) {
    Write-Host "Target exists. Remove or use different path." -ForegroundColor Red
    exit 1
}
New-Item -ItemType Directory -Path $TargetWsl -Force | Out-Null

# 3. Move wsl folder to D
Write-Host "[3/5] Moving wsl folder to D... (may take a while)" -ForegroundColor Yellow
Move-Item -Path $SourceWsl -Destination $TargetWsl -Force
Write-Host "Move done." -ForegroundColor Green

# 4. Create junction (C -> D)
Write-Host "[4/5] Create junction..." -ForegroundColor Yellow
$dockerDir = "$env:LOCALAPPDATA\Docker"
if (-not (Test-Path $dockerDir)) { New-Item -ItemType Directory -Path $dockerDir -Force | Out-Null }
cmd /c mklink /J "$SourceWsl" "$TargetWsl"
Write-Host "Junction created." -ForegroundColor Green

# 5. Done
Write-Host "[5/5] Done." -ForegroundColor Green
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "Move complete!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "New location: $TargetWsl" -ForegroundColor Gray
Write-Host "  - disk\docker_data.vhdx" -ForegroundColor Gray
Write-Host "  - main\ext4.vhdx" -ForegroundColor Gray
Write-Host ""
Write-Host "Start Docker Desktop to verify." -ForegroundColor Cyan
Write-Host ""
