# E2E 테스트 결과: 뉴스 → 최종 산출물

**테스트 일시**: 2026-03-19  
**환경**: Docker (PostgreSQL, Redis, RabbitMQ, MinIO, Worker) + Backend

---

## 1. 테스트 요약

| 단계 | 결과 |
|------|------|
| **뉴스 수집** | ✅ NewsAPI 3건, METADATA_READY |
| **멀티미디어 검색** | ✅ Pexels 15건 (기사 3) |
| **미디어 선택** | ✅ |
| **렌더 요청** | ✅ RenderJob 생성 |
| **Worker 처리** | ✅ COMPLETED (Job 1) |
| **최종 산출물** | ✅ MinIO 업로드, Presigned URL |

---

## 2. 수정 사항

### Presigned URL (브라우저 접근)

- **문제**: Worker(Docker)가 생성한 Presigned URL이 `minio:9000` 사용 → 브라우저에서 접근 불가
- **해결**: `MINIO_PUBLIC_ENDPOINT=host.docker.internal:9000` 추가
- **파일**: `worker/config.py`, `worker/services/storage.py`, `docker-compose.yml`

### Redis 포트 (Windows)

- **문제**: 6379 포트 권한 오류
- **해결**: `16379:6379` 매핑, Backend `.env` REDIS_PORT=16379

---

## 3. 실행 방법

```powershell
# 1. 인프라
docker compose up -d

# 2. Backend
cd backend; mvn spring-boot:run

# 3. API 테스트 (뉴스→멀티미디어→렌더)
python scripts/run_api_tests.py

# 4. E2E PowerShell
.\scripts\run_e2e_test.ps1
```

---

## 4. 최종 산출물 확인

- **MinIO 콘솔**: http://localhost:9001 → shortform 버킷 → renders/
- **로컬 볼륨**: `D:\community-shortform\output\news-articles\minio\`
- **프론트엔드**: http://localhost:3000/news-articles/{id} → RENDERED 시 비디오 플레이어
