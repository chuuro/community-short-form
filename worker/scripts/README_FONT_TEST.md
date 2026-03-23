# 한글 자막 폰트 테스트

## 수정 사항 (한글 tofu 해결)

1. **fontconfig 등록**: `/app/fonts`를 fontconfig에 등록 (`/etc/fonts/conf.d/99-app-fonts.conf`)
2. **fc-query 폰트 이름**: ASS 생성 시 `fc-query`로 실제 폰트 이름 조회
3. **FFmpeg 로그**: INFO 레벨로 전체 명령 출력

## 수동 테스트

```powershell
# Worker 컨테이너에서 한글 자막 테스트
docker run --rm -v D:/community-shortform/output/news-articles/worker-temp:/app/temp community-shortform-worker python /app/scripts/test_korean_subtitle.py
```

성공 시 `test_korean_out.mp4` 생성. 영상에서 "한글 테스트입니다. 가나다라마바사" 확인.

## 재빌드 및 적용

```powershell
docker compose build worker --no-cache
docker compose up -d
```

**중요**: 기존 렌더 영상은 변경되지 않음. **새로 렌더**해야 한글이 적용됨.
