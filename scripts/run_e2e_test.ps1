# E2E 테스트: 뉴스 -> 멀티미디어 -> 렌더 -> 최종 산출물
# 사전: docker compose up -d, backend (mvn spring-boot:run) 실행 중

$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

Write-Host "=== E2E Test: News -> Render -> Final Output ===" -ForegroundColor Cyan
Write-Host ""

# 1. 뉴스 목록
Write-Host "[1] GET news-articles..." -ForegroundColor Yellow
$list = Invoke-RestMethod -Uri "$base/api/news-articles?page=0&size=20" -Method Get
$articles = $list.data
if (-not $articles) {
    Write-Host "  No articles. Wait for backend startup fetch." -ForegroundColor Red
    exit 1
}
$articleId = ($articles | Where-Object { $_.status -eq "METADATA_READY" } | Select-Object -First 1).id
if (-not $articleId) { $articleId = $articles[0].id }
Write-Host "  Article ID: $articleId, Status: $(($articles | Where-Object { $_.id -eq $articleId }).status)" -ForegroundColor Green

# 2. fetch-multimedia (METADATA_READY일 때)
$art = $articles | Where-Object { $_.id -eq $articleId }
if ($art.status -eq "METADATA_READY") {
    Write-Host "[2] POST fetch-multimedia..." -ForegroundColor Yellow
    $r = Invoke-RestMethod -Uri "$base/api/news-articles/$articleId/fetch-multimedia" -Method Post -ContentType "application/json"
    Write-Host "  -> $($r.data.status)" -ForegroundColor Green
    Start-Sleep -Seconds 2
}

# 3. 미디어 목록 및 선택
Write-Host "[3] GET media, PUT selection..." -ForegroundColor Yellow
$media = (Invoke-RestMethod -Uri "$base/api/news-articles/$articleId/media" -Method Get).data
$selected = ($media | Where-Object { $_.selected } | ForEach-Object { $_.id })
if (-not $selected) { $selected = ($media | Select-Object -First 3 | ForEach-Object { $_.id }) }
Invoke-RestMethod -Uri "$base/api/news-articles/$articleId/media/selection" -Method Put -Body ($selected | ConvertTo-Json) -ContentType "application/json" | Out-Null
Write-Host "  Selected: $($selected.Count) media" -ForegroundColor Green

# 4. 렌더 요청
Write-Host "[4] POST render..." -ForegroundColor Yellow
$render = Invoke-RestMethod -Uri "$base/api/news-articles/$articleId/render" -Method Post -ContentType "application/json"
$jobId = $render.data.id
Write-Host "  RenderJob ID: $jobId" -ForegroundColor Green

# 5. 폴링
Write-Host "[5] Polling render status (max 5 min)..." -ForegroundColor Yellow
$max = 30
for ($i = 0; $i -lt $max; $i++) {
    $status = (Invoke-RestMethod -Uri "$base/api/render/$jobId/status" -Method Get).data
    Write-Host "  [$($i+1)/$max] $($status.status)" -ForegroundColor Gray
    if ($status.status -eq "COMPLETED") {
        Write-Host ""
        Write-Host "=== SUCCESS ===" -ForegroundColor Green
        Write-Host "Output URL: $($status.outputFilePath)" -ForegroundColor Cyan
        if ($status.outputFilePath -match "localhost|host\.docker\.internal") {
            Write-Host "  (Browser-accessible)" -ForegroundColor Green
        } else {
            Write-Host "  (May need localhost - check Worker MINIO_PUBLIC_ENDPOINT)" -ForegroundColor Yellow
        }
        exit 0
    }
    if ($status.status -eq "FAILED") {
        Write-Host "  Error: $($status.errorMessage)" -ForegroundColor Red
        exit 1
    }
    Start-Sleep -Seconds 10
}
Write-Host "  Timeout" -ForegroundColor Red
exit 1
