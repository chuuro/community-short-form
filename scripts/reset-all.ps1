# Reset script: data + Docker prune
# Run: .\scripts\reset-all.ps1
# Skip prompt: .\scripts\reset-all.ps1 -Confirm

param([switch]$Confirm)

$projectRoot = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $projectRoot "data"

Write-Host "=== Community Shortform Reset ===" -ForegroundColor Yellow
Write-Host "Will delete: data/, Docker unused resources"
Write-Host ""

if (-not $Confirm) {
    $ans = Read-Host "Continue? (y/N)"
    if ($ans -ne "y" -and $ans -ne "Y") {
        Write-Host "Cancelled"
        exit 0
    }
}

Write-Host "[1/4] Stopping Docker Compose..." -ForegroundColor Cyan
Set-Location $projectRoot
docker compose down 2>$null

Write-Host "[2/4] Deleting data folder..." -ForegroundColor Cyan
if (Test-Path $dataDir) {
    Remove-Item -Path $dataDir -Recurse -Force
    Write-Host "  data/ deleted"
} else {
    Write-Host "  data/ not found (skip)"
}

Write-Host "[3/4] Docker prune (images, containers, volumes)..." -ForegroundColor Cyan
docker system prune -a --volumes -f 2>$null
Write-Host "  Done"

Write-Host "[4/4] Cleaning backend temp..." -ForegroundColor Cyan
$backendTemp = Join-Path $projectRoot "backend\temp"
if (Test-Path $backendTemp) {
    Remove-Item -Path (Join-Path $backendTemp "*") -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  backend/temp cleaned"
}

Write-Host ""
Write-Host "=== Reset complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Restart: docker compose up -d"
Write-Host "Backend: cd backend, mvn spring-boot:run"
Write-Host "Frontend: cd frontend, npm run dev"
Write-Host "Worker: cd worker, celery -A celery_app worker --loglevel=info --pool=solo"
Write-Host "Consumer: cd worker, python consumer.py"
Write-Host ""
Write-Host "Docker WSL disk still large? Docker Desktop menu -> Docker -> Troubleshoot -> Clean / Purge data"
