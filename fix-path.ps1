# 시스템 PATH 복구 스크립트 - 관리자 권한으로 실행 필요
$currentPath = [System.Environment]::GetEnvironmentVariable("PATH", "Machine")

Write-Host "=== 현재 시스템 PATH ===" -ForegroundColor Yellow
Write-Host $currentPath
Write-Host ""

# 추가해야 할 기본 Windows 경로들
$missingPaths = @(
    "C:\Windows\System32",
    "C:\Windows",
    "C:\Windows\System32\wbem",
    "C:\Windows\System32\WindowsPowerShell\v1.0"
)

$pathArray = $currentPath -split ";" | Where-Object { $_ -ne "" }

foreach ($path in $missingPaths) {
    $alreadyExists = $pathArray | Where-Object { $_ -ieq $path }
    if (-not $alreadyExists) {
        Write-Host "추가: $path" -ForegroundColor Green
        $pathArray = @($path) + $pathArray
    } else {
        Write-Host "이미 존재: $path" -ForegroundColor Gray
    }
}

$newPath = $pathArray -join ";"
[System.Environment]::SetEnvironmentVariable("PATH", $newPath, "Machine")

Write-Host ""
Write-Host "=== 복구 완료! ===" -ForegroundColor Green
Write-Host "새 시스템 PATH:" -ForegroundColor Yellow
Write-Host $newPath
Write-Host ""
Write-Host "PowerShell을 새로 열고 'wsl --status' 를 실행해보세요." -ForegroundColor Cyan
Read-Host "Enter 키를 눌러 종료"
