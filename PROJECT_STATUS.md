# Community Shortform - 프로젝트 진행 현황

## 기본 프로세스

```
[1] URL 입력 (Frontend)
       ↓
[2] 프로젝트 생성 (Backend) → DB projects
       ↓
[3] 파싱 시작 (Backend) → RabbitMQ 메시지 발행
       ↓
[4] 커뮤니티 크롤링 (Backend)
    - Reddit: 개별 게시글 (/r/xxx/comments/yyy)
    - YouTube: oEmbed + 영상 메타데이터
       ↓
[5] 미디어/자막 추출 → DB media_items, subtitles
       ↓
[6] Frontend 미디어 시각화 (VIDEO, IMAGE, GIF, TEXT)
       ↓
[7] 렌더 버튼 클릭 → Backend Render API → RabbitMQ
       ↓
[8] Worker (Consumer) 메시지 수신 → Celery 태스크 디스패치
       ↓
[9] Worker 파이프라인:
    - yt-dlp로 YouTube 다운로드 (또는 HTTP 미디어)
    - Whisper STT로 자막 생성 (백엔드 자막 없을 때)
    - FFmpeg으로 9:16 숏폼 편집
    - MinIO 업로드
       ↓
[10] 콜백 → Backend → 프로젝트 상태 COMPLETED
```

---

## 현재 구현 완료 항목

### Backend (Spring Boot 3)
- [x] 프로젝트 CRUD, 파싱 API
- [x] Reddit / YouTube 파서 (개별 게시글)
- [x] **NewsAPI 연동** (해외 뉴스 수집, OpenAI 메타데이터 추출)
- [x] 미디어 분류 (VIDEO, IMAGE, GIF, TEXT)
- [x] RabbitMQ 렌더 큐 발행
- [x] Render API (시작, 이력, 콜백)
- [x] WebSocket 파싱 진행 상태
- [x] CORS, Security (API permitAll)

### Frontend (Next.js 14)
- [x] URL 입력, 프로젝트 목록
- [x] 파싱 스피너, 폴링
- [x] 프로젝트 상세 (미디어/자막 탭)
- [x] 미디어 타입 필터 (VIDEO, IMAGE, GIF, TEXT)
- [x] YouTube 임베드, 이미지/GIF/텍스트 카드
- [x] **렌더 버튼** (미리보기 / 최종)
- [x] 렌더링 진행 배너, 폴링

### Worker (Python / Celery)
- [x] RabbitMQ Consumer (pika) → Celery 디스패치
- [x] yt-dlp YouTube 다운로드
- [x] HTTP 미디어 다운로드
- [x] Whisper STT → SRT
- [x] FFmpeg 숏폼 편집 (9:16, 자막, BGM 믹싱)
- [x] MinIO 업로드
- [x] Backend 콜백 (진행률, 완료/실패)

### 인프라 (Docker Compose)
- [x] PostgreSQL, Redis, RabbitMQ, MinIO
- [x] Worker 컨테이너 (Dockerfile)

---

## 실행 방법

### 1. 인프라 기동
```bash
cd d:\community-shortform
docker compose up -d
```

### 2. Backend
```bash
cd backend
mvn spring-boot:run
```

### 3. Frontend
```bash
cd frontend
npm run dev
```

### 4. Worker (로컬)
```bash
cd worker
# 가상환경 활성화
.\venv\Scripts\activate
# ffmpeg PATH 확인 (winget install Gyan.FFmpeg)
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# 터미널 1: Celery Worker
celery -A celery_app worker --loglevel=info --pool=solo

# 터미널 2: RabbitMQ Consumer
python consumer.py
```

### 5. Worker (Docker)
```bash
docker compose up -d --build worker
```

---

## 지원 URL

| 플랫폼 | 형식 | 예시 |
|--------|------|------|
| Reddit | 개별 게시글 | `https://www.reddit.com/r/movies/comments/xxx/title/` |
| YouTube | watch | `https://www.youtube.com/watch?v=xxx` |

※ Reddit 검색 URL (`/search/?q=...`)은 미지원

---

## 결과물 저장 위치

> **로컬**: 프로젝트가 `D:\community-shortform`에 있으면 `./data/` = `D:\community-shortform\data\` (D드라이브)  
> **서버**: 프로젝트 루트 기준 `./data/` 동일 (Linux: `/home/user/community-shortform/data/`)

### 1. Docker 데이터 (./data/)
| 경로 | 용도 |
|------|------|
| `data/postgres/` | PostgreSQL DB 파일 |
| `data/redis/` | Redis AOF |
| `data/rabbitmq/` | RabbitMQ 메시지 |
| `data/minio/` | MinIO 오브젝트 (렌더 결과물) |
| `data/worker-temp/` | Worker 임시 파일 |
| `data/worker-output/` | Worker 최종 MP4 (MinIO 업로드 전) |

### 2. MinIO 오브젝트 키 (data/minio 내부)
| 키 | 용도 |
|------|------|
| `shortform/renders/{projectId}/preview_{jobId}.mp4` | 미리보기 (10초, 480×854) |
| `shortform/renders/{projectId}/output_{jobId}.mp4` | 최종 영상 (1080×1920) |

- MinIO 콘솔: http://localhost:9001 (minioadmin / minioadmin123)
- 완료 시 `outputFilePath` = MinIO Presigned URL (48시간 유효)

### 3. Backend 로컬
| 경로 | 용도 |
|------|------|
| `backend/temp/{projectId}/` | Backend가 직접 다운로드한 미디어 (Reddit 이미지 등) |

---

## 데이터 관리 (쌓이는 데이터 정리)

### 1. MinIO 렌더 결과물
- **수동**: MinIO 콘솔(9001) → shortform 버킷 → renders/ 폴더에서 오래된 항목 삭제
- **자동(향후)**: MinIO Lifecycle 규칙으로 N일 지난 객체 자동 삭제

### 2. Worker 임시/출력 (data/worker-temp, data/worker-output)
- Worker는 렌더 완료 후 work_dir 정리 (temp 파일 삭제)
- `worker-output`은 MinIO 업로드 후에도 로컬에 남을 수 있음 → 주기적 삭제
- **수동**: `data/worker-temp/`, `data/worker-output/` 폴더 비우기

### 3. Backend temp (backend/temp/)
- Reddit 이미지 등 다운로드 파일
- **수동**: `backend/temp/` 하위 폴더 삭제

### 4. DB (PostgreSQL)
- Soft Delete된 프로젝트는 DB에 남음
- **수동**: 주기적으로 `DELETE FROM projects WHERE deleted_at < NOW() - INTERVAL '90 days'` 등
- **향후**: Admin API 또는 배치 작업으로 자동 정리

### 5. 정리 스크립트 (Windows)
```powershell
# 7일 지난 worker 임시/출력 파일 삭제 (실제 삭제)
.\scripts\cleanup.ps1

# 삭제 대상만 확인 (실제 삭제 안 함)
.\scripts\cleanup.ps1 -DryRun
```

### 6. 용량 확인
```powershell
# data 폴더 용량
Get-ChildItem -Path "d:\community-shortform\data" -Recurse | Measure-Object -Property Length -Sum
```

---

## 전체 초기화 및 테스트

- **초기화**: `.\scripts\reset-all.ps1` (data 삭제 + Docker prune)
- **테스트 순서**: `scripts/TEST_PROCESS.md` 참고

---

## ⚠️ 볼륨 경로 변경 시 (기존 Docker 사용자)

`./data/` 로 변경 후 **첫 실행**:
1. `docker compose down` 으로 기존 컨테이너 중지
2. `docker compose up -d` 로 재시작
3. `./data/` 폴더가 새로 생성되며 **빈 상태**로 시작

기존 Docker named volume에 있던 DB/Redis/MinIO 데이터는 **새 경로로 자동 이전되지 않습니다**. 필요하면 수동 백업 후 복원해야 합니다.

---

## NewsAPI 플로우 (신규)

```
[1] 백엔드 시작 시 NewsAPI Top Headlines 수집 (최대 3건, 중복 제외)
       ↓
[2] OpenAI 메타데이터 추출 (비동기)
    - script (숏폼 대본)
    - translatedTitle, translatedContent (한글 번역)
    - thumbnailKeywords, imageSearchKeywords, videoSearchKeywords
    - estimatedDurationSeconds
       ↓
[3] GET /api/news-articles → 메타데이터 완료된 기사 목록
[4] GET /api/news-articles/{id} → 상세
[5] POST /api/news-articles/{id}/extract-metadata → 수동 메타데이터 추출 (FETCHED 상태)
```

※ 멀티미디어 검색, 미리보기, 최종 렌더는 추후 구현 예정

---

## 환경 변수 (주요)

| 서비스 | 변수 | 기본값 |
|--------|------|--------|
| Backend | OPENAI_API_KEY | (선택) GPT 자막 생성용 |
| Backend | NEWS_API_KEY | (선택) NewsAPI.org API 키 |
| Worker | MINIO_SECRET_KEY | minioadmin123 |
| Worker | BACKEND_URL | http://localhost:8080 |

---

## 개선사항 및 향후 작업

### 우선순위 높음
1. **Worker 안정성**
   - Windows에서 Celery `prefork` 대신 `solo`/`threads` 사용 (로그 표시)
   - Whisper 처리 시 StackOverflowException 대응 (Python 재귀 한도, 또는 smaller 모델)
   - yt-dlp JavaScript 런타임 경고 → deno/node 설치로 일부 포맷 누락 방지

2. **렌더 결과 표시**
   - 프로젝트 상세에 `outputFilePath`(Presigned URL) 표시
   - 완료 시 영상 플레이어/다운로드 버튼 추가

3. **에러 처리**
   - 파싱/렌더 실패 시 사용자 친화적 메시지
   - Worker 재시도 횟수 초과 시 프론트엔드 알림

### 우선순위 중간
4. **자막 수정 UI**
   - 수정 버튼 클릭 시 편집 모드
   - PUT /api/projects/{id}/subtitles 연동

5. **BGM 선택**
   - BGM 트랙 목록 API 연동
   - Select 박스로 BGM 선택 후 렌더

6. **미디어 노출 시간/순서**
   - 드래그 앤 드롭으로 순서 변경
   - exposureStartTime, exposureEndTime 편집

### 우선순위 낮음
7. **플랫폼 확장**
   - Reddit 검색 결과 파싱
   - 기타 커뮤니티 (Twitter/X, 인스타 등) 파서

8. **성능/운영**
   - Redis 캐시 활용 (프로젝트 목록 등)
   - 로그 수준 조정, 모니터링

9. **보안**
   - API 인증 (JWT 등)
   - Rate limiting
