# Docker SIGBUS 오류 해결 가이드

## 시도한 조치 (완료)

### 1. daemon.json 초기화
- **위치**: `C:\Users\<사용자>\.docker\daemon.json`
- **조치**: 내용을 `{}`로 초기화 후, 전체 `.docker` 폴더 백업 및 새로 생성
- **백업**: `C:\Users\<사용자>\.docker.backup.<타임스탬프>` 로 보관됨

### 2. WSL docker-desktop 배포판 재생성
- **명령**: `wsl --unregister docker-desktop`
- **효과**: 손상된 WSL 배포판 제거, Docker Desktop 재시작 시 새로 생성

### 3. WSL2 완전 종료
- **명령**: `wsl --shutdown`
- **효과**: WSL 메모리/상태 초기화

---

## 추가 시도 방법

### 방법 A: Docker Desktop GUI에서 Reset (권장)

1. Docker Desktop 실행
2. 상단 **Troubleshoot** (렌치 아이콘) 클릭
3. **Reset to factory defaults** 선택
4. 확인 후 Docker Desktop이 재시작될 때까지 대기 (5~10분)

> ⚠️ 모든 컨테이너, 이미지, 볼륨이 삭제됩니다. 필요한 데이터는 미리 백업하세요.

### 방법 B: Docker Desktop 재설치

1. **제거**: 설정 → 앱 → Docker Desktop → 제거
2. 아래 경로 수동 삭제:
   - `C:\Users\<사용자>\.docker`
   - `C:\Users\<사용자>\AppData\Roaming\Docker`
   - `C:\Users\<사용자>\AppData\Local\Docker`
3. [Docker Desktop 다운로드](https://www.docker.com/products/docker-desktop/) 후 재설치

### 방법 C: WSL2 업데이트

```powershell
wsl --update
wsl --shutdown
```

이후 Docker Desktop 재시작

### 방법 D: Windows 기능 확인

SIGBUS는 메모리/디스크 접근 오류로, 다음을 확인하세요:

- **가상화**: 작업 관리자 → 성능 → CPU → 가상화: 사용됨
- **디스크**: 디스크 오류 검사 (chkdsk)
- **메모리**: 메모리 진단 도구 실행

---

## 백업 복원

원래 `.docker` 설정으로 되돌리려면:

```powershell
# 새 .docker 삭제
Remove-Item -Recurse -Force "$env:USERPROFILE\.docker" -ErrorAction SilentlyContinue

# 백업에서 복원 (타임스탬프는 실제 폴더명에 맞게 변경)
$backup = Get-ChildItem "$env:USERPROFILE\.docker.backup.*" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Rename-Item $backup.FullName "$env:USERPROFILE\.docker"
```

---

## 참고: SIGBUS 원인

- **daemon.json** 파싱 시 Go 런타임 메모리 접근 오류
- WSL2 가상 디스크 또는 메모리 매핑 손상
- Docker Desktop / WSL2 버전 호환성 이슈

여전히 해결되지 않으면 [Docker for Windows 이슈 트래커](https://github.com/docker/for-win/issues)에 증상과 로그를 첨부해 문의하세요.
