1. 프로젝트 소개
# Community Short-Form 🎬
커뮤니티 글을 AI가 자동으로 숏폼 영상으로 변환해주는 서비스

2. 핵심 기능 
- 📝 커뮤니티 글 → 스크립트 자동 생성 (Gemini 2.5 Flash)
- 🖼️ 장면별 AI 이미지 생성 (nano-banana-pro-preview)
- 🎙️ TTS 음성 합성 + Whisper 자막 싱크 정렬
- 🎬 FFmpeg 자동 영상 편집 (Jua 폰트, 디즈니풍 자막)
- 📡 WebSocket 실시간 진행 상황 알림

3. 시스템 아키텍처
[Frontend Next.js] → [Spring Boot API] → [RabbitMQ]
                                              ↓
                                    [Celery Worker]
                                    FFmpeg / Whisper / TTS
                                              ↓
                                    [MinIO Storage]

4. 기술 스택
Backend  	  Spring Boot 3, Java 21
Frontend    Next.js 14, TypeScript, Tailwind
Worker	    Python 3.11, Celery, FFmpeg, Whisper
AI	        Gemini 2.5 Flash, nano-banana-pro-preview, TTS
Infra	      Docker Compose, RabbitMQ, Redis, MinIO

5. how to use
# 환경 변수 설정
cp backend/.env.example backend/.env

# 전체 실행
docker compose up -d

