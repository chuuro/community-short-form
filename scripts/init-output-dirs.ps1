# D드라이브 output 디렉토리 생성 (docker-compose 볼륨용)
$base = "D:\community-shortform\output"
$dirs = @(
    "$base\postgres",
    "$base\redis",
    "$base\rabbitmq",
    "$base\news-articles\minio",
    "$base\news-articles\worker-temp",
    "$base\news-articles\worker-output"
)

foreach ($d in $dirs) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
        Write-Host "Created: $d"
    }
}
Write-Host "Done. Output directories ready."
