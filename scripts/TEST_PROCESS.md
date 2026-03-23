# 테스트 프로세스 (순서대로 진행)

## 사전 준비: 전체 초기화

```powershell
cd d:\community-shortform
.\scripts\reset-all.ps1
```

- output/ 폴더 삭제 (DB, Redis, MinIO 등, reset-all은 data/ 사용)
- Docker 미사용 리소스 정리 (C드라이브 Docker WSL 용량 확보)

---

## 1단계: 인프라 기동

```powershell
cd d:\community-shortform
docker compose up -d
```

**확인**: `docker ps` → postgres, redis, rabbitmq, minio (healthy)

---

## 2단계: Backend 기동

**API 키 설정** (NewsAPI, OpenAI 사용 시):

```powershell
cd d:\community-shortform\backend
copy .env.example .env
# .env 파일을 열어 OPENAI_API_KEY, NEWS_API_KEY 등 실제 키 입력
```

```powershell
mvn spring-boot:run
```

**확인**: `Started BackendApplication` 로그, http://localhost:8080/api/projects → `{"data":[]}`

---

## 3단계: Frontend 기동

```powershell
cd d:\community-shortform\frontend
npm run dev
```

**확인**: http://localhost:3000 접속, 빈 프로젝트 목록

---

## 4단계: Worker 기동

### Docker 사용 (권장, 한글 자막 포함)
```powershell
docker compose up -d --build worker
```

### 로컬 실행 (2개 터미널, 한글 폰트 사전 다운로드 필요)
```powershell
cd d:\community-shortform\worker
.\scripts\download-font.ps1   # 최초 1회: 한글 폰트 다운로드
```

**터미널 A - Celery Worker:**
```powershell
cd d:\community-shortform\worker
.\venv\Scripts\activate
$env:PYTHONPATH = "d:\community-shortform\worker"
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
celery -A celery_app worker --loglevel=info --pool=solo
```

**터미널 B - RabbitMQ Consumer:**
```powershell
cd d:\community-shortform\worker
.\venv\Scripts\activate
$env:PYTHONPATH = "d:\community-shortform\worker"
python consumer.py
```

**확인**: Celery `celery@xxx ready`, Consumer `구독 완료`

---

## 5단계: 파싱 테스트

### 5-1. YouTube URL

1. http://localhost:3000 접속
2. URL 입력: `https://www.youtube.com/watch?v=dQw4w9WgXcQ`
3. 제출 → 스피너 → 프로젝트 상세로 이동
4. **확인**: 미디어 1개(VIDEO), 썸네일 표시

### 5-2. Reddit URL (개별 게시글)

1. URL 입력: `https://www.reddit.com/r/movies/comments/1rwb609/dune_part_three_official_teaser/`
2. 제출 → 파싱 완료 대기
3. **확인**: 미디어 여러 개 (VIDEO, IMAGE 등)

---

## 6단계: 렌더 테스트

1. 프로젝트 상세 페이지에서 **미리보기 렌더** 버튼 클릭
2. 렌더링 중 배너 표시
3. 2~4분 대기 (YouTube 다운로드 + Whisper + FFmpeg)
4. **확인**: 상태 COMPLETED, 영상 플레이어/다운로드 링크 표시

---

## 7단계: 결과물 확인

| 위치 | 확인 방법 |
|------|-----------|
| MinIO | http://localhost:9001 → shortform 버킷 → renders/ |
| 로컬 | `d:\community-shortform\output\news-articles\minio\` |
| 프론트 | 프로젝트 상세 → 렌더 완료 시 영상 재생 |

---

## Docker WSL 디스크(C드라이브) 용량 정리

`reset-all.ps1` 실행 후에도 C드라이브가 부족하면:

- **Docker Desktop** 창을 연 상태에서
- 상단 메뉴 **Docker** (또는 **Help**) → **Troubleshoot** → **Clean / Purge data**
- ※ Settings 안에 있지 않음. Docker Desktop 메인 창의 상단 메뉴

---

## 트러블슈팅

| 증상 | 확인 |
|------|------|
| 파싱 실패 | URL 형식 확인 (Reddit은 /comments/ 포함) |
| 렌더 안 됨 | Worker(Celery + Consumer) 실행 여부 |
| ffmpeg 오류 | `ffmpeg -version` 확인, PATH에 ffmpeg 포함 |
| C드라이브 부족 | `.\scripts\reset-all.ps1` 후 `docker system prune -a --volumes` |
| Backend 터미널 한글 깨짐 | `$env:JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"` 후 `mvn spring-boot:run` 또는 `chcp 65001` (UTF-8 코드페이지) |
