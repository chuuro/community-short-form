# 데이터 정리 스크립트 (수동 실행)
# 용량 부족 시 data/ 폴더 내 오래된 파일 정리

param(
    [switch]$DryRun,      # 실제 삭제 없이 대상만 출력
    [int]$WorkerTempDays = 7,   # worker-temp N일 지난 폴더 삭제
    [int]$WorkerOutputDays = 7  # worker-output N일 지난 파일 삭제
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $projectRoot "data"

if (-not (Test-Path $dataDir)) {
    Write-Host "data 폴더가 없습니다: $dataDir"
    exit 0
}

Write-Host "=== 현재 data 용량 ==="
$size = (Get-ChildItem -Path $dataDir -Recurse -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
Write-Host ("총 용량: {0:N2} MB" -f ($size / 1MB))

$cutoff = (Get-Date).AddDays(-$WorkerTempDays)
$deleted = 0

# worker-temp: projectId 폴더 중 N일 지난 것
$workerTemp = Join-Path $dataDir "worker-temp"
if (Test-Path $workerTemp) {
    Get-ChildItem $workerTemp -Directory | Where-Object { $_.LastWriteTime -lt $cutoff } | ForEach-Object {
        Write-Host "삭제 대상: $($_.FullName)"
        if (-not $DryRun) {
            Remove-Item $_.FullName -Recurse -Force
            $deleted++
        }
    }
}

# worker-output: N일 지난 mp4
$workerOutput = Join-Path $dataDir "worker-output"
if (Test-Path $workerOutput) {
    Get-ChildItem $workerOutput -Filter "*.mp4" | Where-Object { $_.LastWriteTime -lt $cutoff } | ForEach-Object {
        Write-Host "삭제 대상: $($_.FullName)"
        if (-not $DryRun) {
            Remove-Item $_.FullName -Force
            $deleted++
        }
    }
}

if ($DryRun) {
    Write-Host "`n[DryRun] 실제 삭제 없음. -DryRun 제거 후 실행하면 삭제됩니다."
} else {
    Write-Host "`n삭제 완료: $deleted 개"
}
