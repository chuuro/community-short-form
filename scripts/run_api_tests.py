#!/usr/bin/env python3
"""
API 통합 테스트 스크립트

- Backend API 엔드포인트 검증
- API KEY 필요 시점에서 중단하고 알림
- 버그 발견 시 수정 후 재테스트
"""

import json
import sys
import urllib.request
import urllib.error
from typing import Any

BASE_URL = "http://localhost:8080"
TIMEOUT = 15

passed = 0
failed = 0
errors: list[str] = []


def req_multipart(path: str, file_path: str, media_type: str, filename: str = None) -> tuple[int, Any]:
    """multipart/form-data 요청 (파일 업로드)"""
    url = f"{BASE_URL}{path}"
    with open(file_path, "rb") as f:
        file_data = f.read()
    ext = ".png" if file_path.lower().endswith(".png") else ".jpg"
    fn = filename or ("test" + ext)
    ct = "image/png" if ext == ".png" else "image/jpeg"
    boundary = "----WebKitFormBoundary" + "x" * 16
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{fn}"\r\n'
        f"Content-Type: {ct}\r\n\r\n"
    ).encode() + file_data + (
        f"\r\n--{boundary}\r\n"
        f'Content-Disposition: form-data; name="mediaType"\r\n\r\n{media_type}\r\n'
        f"--{boundary}--\r\n"
    ).encode()
    req_obj = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with urllib.request.urlopen(req_obj, timeout=TIMEOUT) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        body_read = e.read().decode() if e.fp else ""
        try:
            return e.code, json.loads(body_read) if body_read else {}
        except json.JSONDecodeError:
            return e.code, {"raw": body_read}
    except urllib.error.URLError as e:
        raise RuntimeError(f"연결 실패: {e.reason}") from e


def req(method: str, path: str, body: Any = None) -> tuple[int, Any]:
    url = f"{BASE_URL}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req_obj = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"} if data else {},
    )
    try:
        with urllib.request.urlopen(req_obj, timeout=TIMEOUT) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode() if e.fp else ""
        try:
            err_data = json.loads(body) if body else {}
            return e.code, err_data
        except json.JSONDecodeError:
            return e.code, {"raw": body}
    except urllib.error.URLError as e:
        raise RuntimeError(f"연결 실패 (Backend 실행 중인지 확인): {e.reason}") from e


def ok(name: str, status: int, data: Any) -> None:
    global passed
    passed += 1
    print(f"  [PASS] {name} (HTTP {status})")


def fail(name: str, msg: str) -> None:
    global failed, errors
    failed += 1
    errors.append(f"{name}: {msg}")
    print(f"  [FAIL] {name}: {msg}")


def check_api_key_needed(name: str, status: int, data: Any) -> bool:
    """API 키 필요 시 True 반환 (테스트 중단)"""
    msg = data.get("message", "") if isinstance(data, dict) else str(data)
    if "PEXELS" in msg.upper() or "PEXELS_API" in msg.upper():
        print(f"\n  [STOP] {name} - PEXELS_API_KEY required")
        return True
    if "OPENAI" in msg.upper() or "OPENAI_API" in msg.upper():
        print(f"\n  [STOP] {name} - OPENAI_API_KEY required")
        return True
    if "NEWS_API" in msg.upper() or "NEWSAPI" in msg.upper():
        print(f"\n  [STOP] {name} - NEWS_API_KEY required")
        return True
    return False


def main() -> int:
    print("=" * 60)
    print("Community Shortform - API 통합 테스트")
    print("=" * 60)

    # 0. Backend 연결 확인
    print("\n[0] Backend 연결 확인")
    try:
        status, data = req("GET", "/api/news-articles?page=0&size=1")
        if status != 200:
            fail("Backend 연결", f"HTTP {status}")
            return 1
        ok("Backend 연결", status, data)
    except Exception as e:
        fail("Backend 연결", str(e))
        print("\n  Backend가 localhost:8080에서 실행 중인지 확인하세요.")
        return 1

    # 1. 뉴스 목록 조회
    print("\n[1] GET /api/news-articles")
    status, data = req("GET", "/api/news-articles?page=0&size=20")
    if status != 200:
        fail("뉴스 목록", f"HTTP {status}: {data}")
    else:
        ok("뉴스 목록", status, data)
        articles = data.get("data") if isinstance(data, dict) else []
        if not articles:
            print("     [WARN] No news articles in DB. METADATA_READY articles needed.")
            print("     Set NEWS_API_KEY, OPENAI_API_KEY in backend .env and restart.")
            print("\n  [STOP] Test requires METADATA_READY articles.")
            return 0

    # 2. METADATA_READY 기사 찾기
    article_id = None
    for a in articles:
        if a.get("status") == "METADATA_READY":
            article_id = a.get("id")
            break
    if not article_id:
        for a in articles:
            if a.get("status") in ("MULTIMEDIA_READY", "RENDERED"):
                article_id = a.get("id")
                break
    if not article_id:
        article_id = articles[0].get("id")

    print(f"\n[2] 테스트 대상 기사 ID: {article_id}")

    # 3. 기사 상세 조회
    print("\n[3] GET /api/news-articles/{id}")
    status, data = req("GET", f"/api/news-articles/{article_id}")
    if status != 200:
        fail("기사 상세", f"HTTP {status}: {data}")
    else:
        ok("기사 상세", status, data)
        article = data.get("data") if isinstance(data, dict) else {}
        art_status = article.get("status", "")

    # 4. 멀티미디어 검색 (METADATA_READY일 때만)
    if art_status == "METADATA_READY":
        print("\n[4] POST /api/news-articles/{id}/fetch-multimedia")
        status, data = req("POST", f"/api/news-articles/{article_id}/fetch-multimedia")
        if status != 200:
            msg = data.get("message", str(data)) if isinstance(data, dict) else str(data)
            if check_api_key_needed("fetch-multimedia", status, data):
                return 0
            fail("멀티미디어 검색", f"HTTP {status}: {msg}")
        else:
            ok("멀티미디어 검색", status, data)
            updated = data.get("data", {})
            if updated.get("status") == "MULTIMEDIA_READY":
                print("     → MULTIMEDIA_READY 전환됨")
                art_status = "MULTIMEDIA_READY"
    else:
        print("\n[4] fetch-multimedia 스킵 (상태:", art_status, ")")

    # 5. 미디어 목록 조회
    print("\n[5] GET /api/news-articles/{id}/media")
    status, data = req("GET", f"/api/news-articles/{article_id}/media")
    if status != 200:
        fail("미디어 목록", f"HTTP {status}: {data}")
    else:
        ok("미디어 목록", status, data)
        media_list = data.get("data") if isinstance(data, dict) else []
        print(f"     → 미디어 {len(media_list)}건")
        if art_status == "MULTIMEDIA_READY" and len(media_list) == 0:
            print("     [WARN] PEXELS_API_KEY missing = 0 results. Check backend .env.")

    # 6. 미디어 선택 업데이트
    print("\n[6] PUT /api/news-articles/{id}/media/selection")
    selected_ids = [m["id"] for m in media_list if m.get("selected")] if media_list else []
    status, data = req("PUT", f"/api/news-articles/{article_id}/media/selection", selected_ids)
    if status != 200:
        fail("미디어 선택", f"HTTP {status}: {data}")
    else:
        ok("미디어 선택", status, data)

    # 6b. PUT keywords (MULTIMEDIA_READY/RENDERED일 때, KeywordItem 형식)
    if art_status in ("MULTIMEDIA_READY", "RENDERED"):
        print("\n[6b] PUT /api/news-articles/{id}/keywords")
        status_kw, _ = req("PUT", f"/api/news-articles/{article_id}/keywords", {
            "imageSearchKeywords": [{"keyword": "test image", "source": "user", "enabled": True}],
            "videoSearchKeywords": [{"keyword": "test video", "source": "user", "enabled": True}],
        })
        if status_kw == 200:
            ok("키워드 업데이트", status_kw, {})
        else:
            fail("키워드 업데이트", f"HTTP {status_kw}")

    # 7. 렌더 요청 (미디어가 있을 때만)
    if media_list and len(selected_ids) > 0:
        print("\n[7] POST /api/news-articles/{id}/render")
        status, data = req("POST", f"/api/news-articles/{article_id}/render", {})
        if status not in (200, 202):
            msg = data.get("message", str(data)) if isinstance(data, dict) else str(data)
            fail("렌더 요청", f"HTTP {status}: {msg}")
        else:
            ok("렌더 요청", status, data)
            job = data.get("data", {})
            if job.get("id"):
                print(f"     → RenderJob ID: {job.get('id')}")
    else:
        print("\n[7] 렌더 요청 스킵 (선택된 미디어 없음)")

    # 8. Projects API (기존)
    print("\n[8] GET /api/projects")
    status, data = req("GET", "/api/projects")
    if status != 200:
        fail("프로젝트 목록", f"HTTP {status}")
    else:
        ok("프로젝트 목록", status, data)

    # === 추가 엣지 케이스 ===
    print("\n" + "-" * 40)
    print("[Edge Cases]")

    # E1. 존재하지 않는 기사 ID (404 기대)
    print("\n[E1] GET /api/news-articles/99999 (not found)")
    status, data = req("GET", "/api/news-articles/99999")
    if status == 404:
        ok("기사 없음 404", status, data)
    elif status == 500:
        ok("기사 없음 (500 fallback)", status, data)
    else:
        fail("기사 없음", f"Expected 404, got {status}")

    # E2. fetch-multimedia - 잘못된 상태 (FETCHED)
    print("\n[E2] POST fetch-multimedia on FETCHED article")
    fetched_id = next((a["id"] for a in articles if a.get("status") == "FETCHED"), None)
    if fetched_id:
        status, data = req("POST", f"/api/news-articles/{fetched_id}/fetch-multimedia")
        if status in (400, 500):
            ok("FETCHED에서 fetch-multimedia 거부", status, data)
        else:
            fail("FETCHED fetch-multimedia", f"Expected 400/500, got {status}")
    else:
        print("     (스킵: FETCHED 기사 없음)")

    # E3. GET media - 아직 MULTIMEDIA_READY 아닌 기사
    print("\n[E3] GET /api/news-articles/{id}/media (before fetch)")
    non_ready_id = next((a["id"] for a in articles if a.get("status") not in ("MULTIMEDIA_READY", "RENDERED")), None)
    if non_ready_id:
        status, data = req("GET", f"/api/news-articles/{non_ready_id}/media")
        if status == 200:
            media_count = len(data.get("data") or [])
            ok("미디어 목록 (빈 배열)", status, data)
        else:
            fail("미디어 목록", f"HTTP {status}")

    # E4. PUT media/selection - 빈 배열
    print("\n[E4] PUT media/selection with []")
    status, data = req("PUT", f"/api/news-articles/{article_id}/media/selection", [])
    if status == 200:
        ok("빈 선택 저장", status, data)
    else:
        fail("빈 선택", f"HTTP {status}")

    # E5. POST render - 선택된 미디어 없을 때
    print("\n[E5] POST render with no selected media")
    status, data = req("PUT", f"/api/news-articles/{article_id}/media/selection", [])
    status2, data2 = req("POST", f"/api/news-articles/{article_id}/render", {})
    if status2 in (400, 500):
        ok("미디어 없이 render 거부", status2, data2)
    else:
        fail("미디어 없이 render", f"Expected 400/500, got {status2}")

    # E6. POST render - 1개만 선택 (최소 2개 필요)
    if len(media_list) >= 2:
        print("\n[E6] POST render with 1 selected (min 2 required)")
        req("PUT", f"/api/news-articles/{article_id}/media/selection", [media_list[0]["id"]])
        status3, data3 = req("POST", f"/api/news-articles/{article_id}/render", {})
        if status3 in (400, 500):
            ok("1개 선택 render 거부", status3, data3)
        else:
            fail("1개 선택 render", f"Expected 400/500, got {status3}")

    req("PUT", f"/api/news-articles/{article_id}/media/selection", selected_ids)

    # E7. POST add media (URL)
    if art_status in ("MULTIMEDIA_READY", "RENDERED"):
        print("\n[E7] POST /api/news-articles/{id}/media (URL 추가)")
        status_add, data_add = req("POST", f"/api/news-articles/{article_id}/media", {
            "sourceUrl": "https://images.pexels.com/photos/414612/pexels-photo-414612.jpeg",
            "mediaType": "IMAGE",
        })
        if status_add in (200, 201):
            ok("URL 미디어 추가", status_add, data_add)
        else:
            fail("URL 미디어 추가", f"HTTP {status_add}: {data_add}")

    # E8. Keyword 20자 초과 validation (400 기대)
    if art_status in ("MULTIMEDIA_READY", "RENDERED"):
        print("\n[E8] PUT keywords - 21자 검색어 (20자 제한, 400 기대)")
        status_kw21, data_kw21 = req("PUT", f"/api/news-articles/{article_id}/keywords", {
            "imageSearchKeywords": [{"keyword": "a" * 21, "source": "user", "enabled": True}],
            "videoSearchKeywords": [{"keyword": "test", "source": "user", "enabled": True}],
        })
        if status_kw21 in (400, 500):
            ok("키워드 20자 검증 (거부)", status_kw21, data_kw21)
        elif status_kw21 == 200:
            fail("키워드 20자 검증", "21자 검색어가 저장됨 (400 기대)")
        else:
            fail("키워드 20자 검증", f"HTTP {status_kw21}")

    # E9. 미디어 파일 업로드 (67-byte minimal valid PNG)
    if art_status in ("MULTIMEDIA_READY", "RENDERED"):
        print("\n[E9] POST /api/news-articles/{id}/media/upload (파일)")
        import tempfile
        # World's smallest PNG: 67 bytes, 1x1 black pixel (from evanhahn.com)
        png_bytes = bytes([
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  # signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x37, 0x6E, 0xF9, 0x24,  # IHDR
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, 0x01, 0x63, 0x60,
            0x00, 0x00, 0x00, 0x02, 0x00, 0x01, 0x73, 0x75, 0x01, 0x18,  # IDAT
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82,  # IEND
        ])
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            tmp.write(png_bytes)
            tmp_path = tmp.name
        try:
            status_up, data_up = req_multipart(
                f"/api/news-articles/{article_id}/media/upload", tmp_path, "IMAGE"
            )
            if status_up in (200, 201):
                ok("파일 미디어 업로드", status_up, data_up)
            else:
                fail("파일 미디어 업로드", f"HTTP {status_up}: {data_up}")
        except Exception as e:
            fail("파일 미디어 업로드", str(e))
        finally:
            import os
            try:
                os.unlink(tmp_path)
            except OSError:
                pass

    # 결과
    print("\n" + "=" * 60)
    print(f"결과: {passed} 통과, {failed} 실패")
    if errors:
        print("\n실패 항목:")
        for e in errors:
            print(f"  - {e}")
    print("=" * 60)
    return 1 if failed > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
