# 테스트 진행 흐름

## 1. 현재 서버 상태 확인

### 인프라 (Docker)

| 서비스 | 포트 | 상태 확인 |
|--------|------|-----------|
| PostgreSQL | 5432 | `docker ps` → shortform-postgres |
| Redis | 6379 | shortform-redis |
| RabbitMQ | 5672, 15672 | shortform-rabbitmq (관리 UI: http://localhost:15672) |
| MinIO | 9000, 9001 | shortform-minio (콘솔: http://localhost:9001) |
| **Worker** | - | shortform-worker (별도 기동 필요) |

### 애플리케이션

| 서비스 | 포트 | 실행 방법 |
|--------|------|-----------|
| Backend | 8080 | `cd backend && mvn spring-boot:run` |
| Frontend | 3000 | `cd frontend && npm run dev` |
| Worker | - | `docker-compose up -d worker` 또는 로컬 `celery -A celery_app worker -l info` |

---

## 2. 서버 기동 순서

```bash
# 1. 인프라 (이미 실행 중이면 스킵)
docker-compose up -d postgres redis rabbitmq minio

# 2. Backend
cd backend
mvn spring-boot:run

# 3. Worker (렌더 처리용)
docker-compose up -d worker
# 또는 로컬: cd worker && celery -A celery_app worker -l info

# 4. Frontend
cd frontend
npm run dev
```

---

## 3. 테스트 흐름 (뉴스 → 숏츠)

### Step 1: 뉴스 기사 확인

1. http://localhost:3000/news-articles 접속
2. 목록에서 기사 클릭 → 상세 페이지

**상태별 의미**
- `METADATA_READY`: 메타데이터 추출 완료, 멀티미디어 검색 가능
- `MULTIMEDIA_FETCHING`: Pexels 검색 중
- `MULTIMEDIA_READY`: 미디어 준비 완료, 편집·렌더 가능

### Step 2: 멀티미디어 검색

1. `METADATA_READY` 기사에서 **멀티미디어 검색** 버튼 클릭
2. Pexels API로 이미지/영상 검색 (PEXELS_API_KEY 필요)
3. 완료 후 `MULTIMEDIA_READY`로 전환

### Step 3: 미디어 선택

1. 검색된 미디어 그리드에서 사용할 항목 클릭 (선택/해제)
2. 체크된 항목만 최종 영상에 사용됨

### Step 4: 최종 산출물 생성

1. **최종 산출물 생성** 버튼 클릭
2. Backend → NewsArticle → Project 변환
3. RabbitMQ로 Worker에 렌더 작업 전달
4. Worker: Edge TTS + 미디어 + 자막 → FFmpeg 합성 → MinIO 업로드
5. 완료 시 비디오 플레이어에 출력 영상 표시

### Step 5: 결과 확인

- 상태가 `RENDERED`로 변경
- **최종 산출물** 섹션에 비디오 플레이어 표시
- 재생하여 TTS 음성 + 자막 + 미디어 확인

---

## 4. API 테스트 (자동)

```bash
# Backend 실행 중일 때
python scripts/run_api_tests.py
```

---

## 5. 환경 변수 (.env)

### Backend (backend/.env)

| 변수 | 용도 |
|------|------|
| OPENAI_API_KEY | 뉴스 메타데이터 추출 |
| NEWS_API_KEY | 뉴스 수집 |
| PEXELS_API_KEY | 이미지/영상 검색 |
| DB_USERNAME, DB_PASSWORD | PostgreSQL |

### Worker (worker/.env 또는 docker-compose)

| 변수 | 용도 |
|------|------|
| BACKEND_URL | Backend API 주소 |
| RABBITMQ_URL | 메시지 큐 |
| REDIS_URL | Celery 브로커 |
| MINIO_* | 출력 영상 저장 |

---

## 6. 트러블슈팅

| 증상 | 확인 |
|------|------|
| 멀티미디어 0건 | PEXELS_API_KEY 설정 여부 |
| 렌더 요청 후 진행 없음 | Worker 실행 여부, RabbitMQ 연결 |
| 비디오 재생 안 됨 | MinIO presigned URL, CORS |
| 404/500 에러 | Backend 로그, DB 연결 |
