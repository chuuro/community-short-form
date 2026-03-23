# 프로젝트 루트 기준 data 디렉토리 생성 (docker-compose 볼륨용)
$base = Join-Path (Split-Path -Parent $PSScriptRoot) "data"
$dirs = @(
    "$base\postgres",
    "$base\redis",
    "$base\rabbitmq",
    "$base\minio",
    "$base\worker-temp",
    "$base\worker-output"
)

foreach ($d in $dirs) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
        Write-Host "Created: $d"
    }
}
Write-Host "Done. Output directories ready."
