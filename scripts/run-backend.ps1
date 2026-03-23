# Backend 실행 (한글 터미널 출력을 위해 UTF-8 인코딩 설정)
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
Set-Location $PSScriptRoot\..\backend
mvn spring-boot:run
