# 이슈 분석 및 개선 로드맵

## 문제별 분석

---

### 문제1) Worker 진행 상태 확인 + 웹 실시간 진행률

| 항목 | 내용 |
|------|------|
| **현재 상태** | Backend `RenderProgressPublisher`가 WebSocket `/topic/render/{projectId}`로 진행률 Push. **NewsArticle 페이지는 WebSocket 미사용**, 2.5초 폴링만 사용 |
| **난이도** | ⭐⭐ (쉬움) |
| **구현 가능성** | ✅ 높음 |
| **작업** | NewsArticle 상세 페이지에서 `connectWebSocket(projectId, onMessage)` 연결 → `progress` 표시 (프로젝트 페이지와 동일 패턴) |
| **참고** | `frontend/src/lib/websocket.ts`, `frontend/src/app/page.tsx` (파싱 진행용 WebSocket 사용 예시) |

---

### 문제2) 최종 산출물 영상 재생 안 됨

| 항목 | 내용 |
|------|------|
| **현재 상태** | `outputFilePath`가 `minio:9000` Presigned URL → 브라우저에서 접근 불가. `MINIO_PUBLIC_ENDPOINT` 수정으로 **신규 렌더**는 `host.docker.internal:9000` 사용. **기존 RENDERED 기사**는 DB에 이전 URL 저장 |
| **난이도** | ⭐⭐ (쉬움) |
| **구현 가능성** | ✅ 높음 |
| **작업** | Backend에 `GET /api/projects/{id}/output-url` 또는 Project 조회 시 `outputFilePath`가 `minio:` 포함이면 MinIO presigned URL 재발급 (localhost 기준) 후 반환. 또는 Frontend에서 URL에 `minio:9000`이 있으면 `localhost:9000`로 치환 시도 (서명 불일치 가능성 있음 → Backend 재발급이 안전) |

---

### 문제3) 한글 자막 인코딩 깨짐

| 항목 | 내용 |
|------|------|
| **현재 상태** | FFmpeg `subtitles` 필터 + libass. 기본 폰트가 한글 미지원 가능성. SRT는 UTF-8로 저장됨 |
| **난이도** | ⭐⭐⭐ (중간) |
| **구현 가능성** | ✅ 높음 |
| **작업** | 1) Worker Dockerfile에 `fonts-noto-cjk` 또는 한글 폰트 설치. 2) `force_style`에 `FontName=Noto Sans CJK KR` 등 지정. 3) SRT/ASS 인코딩 UTF-8 명시 확인 |
| **참고** | `worker/services/editor.py` 374행 `force_style` |

---

### 문제4) 이미지/영상 검색어 추가·수정

| 항목 | 내용 |
|------|------|
| **현재 상태** | `imageSearchKeywords`, `videoSearchKeywords`는 OpenAI 메타데이터에서만 설정. API/UI 수정 불가 |
| **난이도** | ⭐⭐ (쉬움) |
| **구현 가능성** | ✅ 높음 |
| **작업** | 1) Backend: `PUT /api/news-articles/{id}/keywords` (imageSearchKeywords, videoSearchKeywords JSON 배열). 2) Frontend: 키워드 영역에 편집 UI (태그 입력/삭제). 3) 수정 후 `fetch-multimedia` 재실행 시 새 키워드로 Pexels 검색 |

---

### 문제5) 멀티미디어 수동 추가 없음

| 항목 | 내용 |
|------|------|
| **현재 상태** | URL 입력으로 미디어 직접 추가 기능 없음. Pexels 검색 결과만 사용 |
| **난이도** | ⭐⭐⭐ (중간) |
| **구현 가능성** | ✅ 높음 |
| **작업** | 1) Backend: `POST /api/news-articles/{id}/media` (sourceUrl, mediaType, thumbnailUrl 등). 2) Frontend: "URL로 추가" 입력창 + 제출. 3) NewsArticleMedia 엔티티에 수동 추가 플래그 또는 source 구분 |

---

### 문제6) Pexels 연관성 부족 / 대안

| 항목 | 내용 |
|------|------|
| **현재 상태** | Pexels API만 사용. 뉴스 키워드(Iran regime, Tulsi Gabbard 등)와 연관성 낮음 |
| **난이도** | ⭐⭐⭐⭐ (어려움) |
| **구현 가능성** | ⚠️ 중간 (API 비용·제한 고려) |
| **이미지 대안** | 1) **Google Custom Search API** (이미지) – 유료, 연관성 높음. 2) **Bing Image Search API** – 무료 티어 있음. 3) **Unsplash API** – 무료, 품질 좋으나 검색 품질 제한. 4) **SerpAPI** – Google/Bing 스크래핑, 유료 |
| **영상 대안** | 1) **Pixabay Video** – 무료, Pexels와 유사. 2) **Coverr** – 무료 스톡 영상. 3) **YouTube 검색** – 연관성 높으나 저작권·다운로드 이슈. 4) **키워드 개선** – OpenAI에 "검색에 적합한 구체적 영어 키워드" 강조, Pexels 품질 향상 |
| **권장** | 단기: 키워드 프롬프트 개선 + 문제4(수동 추가)로 부족분 보완. 중장기: Bing/Google API 검토 |

---

### 문제7) 용어·UX 개선

| 항목 | 내용 |
|------|------|
| **현재 상태** | "최종 산출물 생성" 버튼. 미디어 1개만 선택해도 제작 가능 |
| **난이도** | ⭐ (매우 쉬움) |
| **구현 가능성** | ✅ 높음 |
| **작업** | 1) "최종 산출물 생성" → "숏폼 제작". 2) 선택된 미디어 < 2개일 때 버튼 `disabled`. 3) 각 미디어 카드에 `durationSeconds` 표시 (VIDEO만, ffprobe 또는 Pexels 메타데이터) |
| **참고** | NewsArticleMedia에 duration 필드 있는지 확인. 없으면 Pexels 응답 또는 다운로드 후 ffprobe로 채움 |

---

### 문제8) 멀티미디어 노출 시간 설정

| 항목 | 내용 |
|------|------|
| **현재 상태** | MediaItem에 `exposureStartTime`, `exposureEndTime` 존재. Worker/editor는 `duration`만 사용, exposure 미반영 |
| **난이도** | ⭐⭐⭐ (중간) |
| **구현 가능성** | ✅ 높음 |
| **작업** | 1) Frontend: 미디어별 노출 시간 입력 (초). script 총 길이보다 짧거나 같게 제한. 2) Backend: MediaItem exposure 업데이트 API. 3) Worker: `create_shortform`에 전달하는 `media_files`에 `duration` 대신 `exposureEndTime - exposureStartTime` 사용. 4) NewsArticleMedia → Project 변환 시 exposure 매핑 |
| **참고** | `MediaItem.exposureStartTime`, `exposureEndTime`. `worker/services/editor.py`의 `media_files[].duration` |

---

## 적용 순서 권장

| 순서 | 문제 | 이유 |
|------|------|------|
| 1 | **문제7** (용어·UX) | 변경 범위 작고, 즉시 체감 |
| 2 | **문제2** (영상 재생) | 핵심 기능, 사용성 직결 |
| 3 | **문제1** (실시간 진행률) | 인프라 이미 있음, Frontend만 수정 |
| 4 | **문제4** (검색어 편집) | Pexels 품질 개선에 직접 기여 |
| 5 | **문제3** (한글 자막) | 출력 품질 개선 |
| 6 | **문제5** (수동 추가) | Pexels 부족분 보완 |
| 7 | **문제8** (노출 시간) | 세밀한 편집 가능 |
| 8 | **문제6** (Pexels 대안) | 장기 과제, API·비용 검토 필요 |

---

## 프로젝트 개선 방향

### 단기 (1–2주)
- 위 1~5번 이슈 해결
- 테스트·문서 정리 (TEST_PROCESS, TEST_E2E_RESULT)

### 중기 (1개월)
- 문제6: 이미지/영상 검색 품질 (키워드 개선, Bing 등 검토)
- 문제8: 노출 시간 설정
- 에러 핸들링·재시도 로직 강화

### 장기
- 멀티 테넌트·인증
- 배치 렌더·큐 관리 UI
- 모니터링·알림

---

## TODO 체크리스트

- [ ] 문제7: "숏폼 제작" 용어, 미디어 2개 이상 필수, duration 표시
- [ ] 문제2: RENDERED 기사 output URL 재발급 또는 프록시
- [ ] 문제1: NewsArticle 페이지 WebSocket 진행률 연동
- [ ] 문제4: 키워드 편집 API + UI
- [ ] 문제3: 한글 폰트 + FFmpeg force_style
- [ ] 문제5: 멀티미디어 URL 수동 추가
- [ ] 문제8: 노출 시간 설정 UI + Worker 반영
- [ ] 문제6: Pexels 대안 검토 (Bing, 키워드 개선)
- [ ] reset-all.ps1: output/ 폴더 삭제 옵션 추가
- [ ] PROJECT_STATUS.md: 최신 이슈·개선 사항 반영
