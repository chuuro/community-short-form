# API 통합 테스트 결과

## 테스트 실행 방법

```bash
# Backend가 localhost:8080에서 실행 중이어야 함
python scripts/run_api_tests.py
```

## 테스트 케이스

| # | 테스트 | 설명 |
|---|--------|------|
| 0 | Backend 연결 | GET /api/news-articles |
| 1 | 뉴스 목록 | GET /api/news-articles?page=0&size=20 |
| 2 | 기사 상세 | GET /api/news-articles/{id} |
| 3 | 멀티미디어 검색 | POST /api/news-articles/{id}/fetch-multimedia |
| 4 | 미디어 목록 | GET /api/news-articles/{id}/media |
| 5 | 미디어 선택 | PUT /api/news-articles/{id}/media/selection |
| 6 | 렌더 요청 | POST /api/news-articles/{id}/render |
| 7 | 프로젝트 목록 | GET /api/projects |
| E1 | 기사 없음 | GET /api/news-articles/99999 → 404 |
| E2 | 잘못된 상태 | FETCHED에서 fetch-multimedia → 400 |
| E3 | 미디어 빈 배열 | MULTIMEDIA_READY 전 media → [] |
| E4 | 빈 선택 저장 | PUT selection [] → 200 |
| E5 | 미디어 없이 렌더 | 선택 해제 후 render → 400 |

## API 키 필요 시점

- **PEXELS_API_KEY**: fetch-multimedia 시 이미지/영상 검색 (없으면 0건)
- **NEWS_API_KEY**, **OPENAI_API_KEY**: 뉴스 수집·메타데이터 (Backend 시작 시)

## 수정된 버그

1. **fetch-multimedia URL 오타**: `/fetch-multimedia` 경로 누락 → 수정
2. **미디어 없이 렌더 허용**: 선택 해제 후에도 202 반환 → requestRender에서 selectedMedia 검증 추가
3. **기사 없음 500**: RuntimeException → NewsArticleNotFoundException으로 404 반환
