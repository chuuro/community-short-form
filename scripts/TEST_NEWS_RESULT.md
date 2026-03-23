# NewsAPI + OpenAI 메타데이터 추출 테스트 결과

**테스트 일시**: 2026-03-19  
**환경**: Docker (PostgreSQL, Redis, RabbitMQ, MinIO) + Backend

---

## 1. 테스트 요약

| 항목 | 결과 |
|------|------|
| **NewsAPI 수집** | ✅ 성공 (8건 수신, 3건 신규 저장) |
| **OpenAI 메타데이터 추출** | ✅ 성공 (3건 모두 METADATA_READY) |
| **API 응답** | ✅ 정상 |
| **기존 API (projects)** | ✅ 정상 동작 |

---

## 2. 백엔드 시작 로그

```
NewsAPI 수집 완료: 8건
뉴스 기사 저장: 1 - Venezuela beats Team USA to claim World Baseball Classic - BBC
뉴스 기사 저장: 2 - Japan's Takaichi Visits Trump as Hormuz Warship Standoff Simmers - Bloomberg.com
뉴스 기사 저장: 3 - About 90 ships cross the Strait of Hormuz as Iran exports millions of barrels of oil despite the war - AP News
뉴스 메타데이터 추출 완료: 3
뉴스 메타데이터 추출 완료: 2
뉴스 메타데이터 추출 완료: 1
NewsAPI 수집 완료: 3건 신규 저장, 메타데이터 추출 비동기 진행 중
```

---

## 3. API 테스트 결과

### GET /api/news-articles?page=0&size=5

- **상태**: 200 OK
- **데이터**: 3건 반환
- **상태값**: 모두 `METADATA_READY`

### GET /api/news-articles/1 (BBC - 베네수엘라 야구)

| 필드 | 값 |
|------|-----|
| title | Venezuela beats Team USA to claim World Baseball Classic - BBC |
| sourceName | BBC News |
| status | METADATA_READY |
| script | (한글 숏폼 대본, 줄바꿈 구분) |
| translatedTitle | (한글 번역 제목) |
| translatedContent | (한글 번역 본문) |
| imageSearchKeywords | ["Venezuela Baseball","Team USA Defeat","WBC Final"] |
| videoSearchKeywords | ["Venezuela Victory","USA vs Venezuela Baseball","WBC Miami Final"] |
| estimatedDurationSeconds | 45.0 |

---

## 4. 수집된 기사 목록

| ID | 제목 | 소스 | 상태 |
|----|------|------|------|
| 1 | Venezuela beats Team USA to claim World Baseball Classic | BBC News | METADATA_READY |
| 2 | Japan's Takaichi Visits Trump as Hormuz Warship Standoff Simmers | Bloomberg | METADATA_READY |
| 3 | About 90 ships cross the Strait of Hormuz as Iran exports... | Google News | METADATA_READY |

---

## 5. 참고 사항

- **한글 인코딩**: PowerShell 콘솔에서 한글(script, translatedTitle 등)이 깨져 보일 수 있음. DB 및 API JSON은 UTF-8로 정상 저장됨.
- **중복 체크**: 동일 URL 기사는 재저장하지 않음.
- **시작 시 수집**: `fetch-limit-on-startup: 3`으로 최대 3건만 신규 저장.
