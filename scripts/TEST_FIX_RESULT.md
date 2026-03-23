# 문제 1, 2, 3 수정 결과 보고

**테스트 일시**: 2026-03-19  
**수정 범위**: 문제1(진행률), 문제2(영상 재생), 문제3(한글 자막)

---

## 1. 수정 사항 요약

### 문제1) Worker 진행 상태 + 웹 실시간 진행률

| 항목 | 내용 |
|------|------|
| **수정** | NewsArticle 상세 페이지에 WebSocket 연동 + 진행률 바 |
| **파일** | `frontend/src/app/news-articles/[id]/page.tsx` |
| **동작** | 렌더 요청 시 `connectWebSocket(projectId)` 구독 → `progress` % 실시간 표시 |
| **UI** | "렌더링 중... X%" + 프로그레스 바 |

### 문제2) 최종 산출물 영상 재생 안 됨

| 항목 | 내용 |
|------|------|
| **원인** | Presigned URL이 `minio:9000` 사용 → 브라우저 접근 불가 |
| **수정** | Backend `GET /api/projects/{id}/output-url` 추가 → localhost 기준 Presigned URL 재발급 |
| **파일** | `backend`: MinioService, ProjectController, ProjectService, pom.xml |
| **파일** | `frontend`: outputUrl 로드 시 `getOutputUrl(projectId)` 사용 |

### 문제3) 한글 자막 인코딩 깨짐

| 항목 | 내용 |
|------|------|
| **수정** | Worker Dockerfile에 `fonts-noto-cjk`, `fontconfig` 설치 + `fc-cache -fv` |
| **수정** | editor.py: `fontsdir` 추가, `Fontname`(ASS 포맷), FontSize 24 |
| **파일** | `worker/Dockerfile`, `worker/services/editor.py` |
| **참고** | Worker 이미지 재빌드 필요: `docker compose build worker` |

---

## 2. 테스트 결과

### API 테스트

```
python scripts/run_api_tests.py
→ 12 통과, 0 실패
```

### output-url 엔드포인트

```
GET /api/projects/1/output-url
→ http://localhost:9000/shortform/renders/1/output_ec944748.mp4?X-Amz-...
→ 브라우저 재생 가능
```

### 진행률 UI

- 렌더 요청 시 WebSocket 구독
- `progress` 0~100% 실시간 표시
- 완료 시 output-url로 영상 URL 로드

---

## 3. 확인 방법

### 문제1 (진행률)

1. http://localhost:3000/news-articles/{id} 접속
2. 멀티미디어 선택 후 "최종 산출물 생성" 클릭
3. "렌더링 중... X%" 프로그레스 바 확인

### 문제2 (영상 재생)

1. RENDERED 상태 기사 상세 페이지 접속
2. 최종 산출물 비디오 플레이어에서 재생 확인
3. (기존 minio:9000 URL은 output-url로 자동 교체)

### 문제3 (한글 자막)

1. Worker 재빌드: `docker compose build worker`
2. Worker 재시작: `docker compose up -d worker`
3. 새로 렌더한 영상에서 한글 자막 표시 확인

---

## 4. 추가 참고

- **Backend .env**: `REDIS_PORT=16379` (Windows 포트 충돌 회피)
- **Worker**: `MINIO_PUBLIC_ENDPOINT=host.docker.internal:9000` (신규 렌더용)
