# ─── Stage 1: 빌드/의존성 설치 ───────────────────────────────────
FROM python:3.11-slim AS builder

WORKDIR /app

# 시스템 패키지 (FFmpeg, 빌드 도구)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    git \
    wget \
    curl \
    build-essential \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# pip 업그레이드
RUN pip install --upgrade pip --no-cache-dir

# 의존성 먼저 설치 (레이어 캐시 최적화)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Whisper 모델 사전 다운로드 (base 모델, ~74MB)
# 빌드 시 다운로드하면 컨테이너 시작이 빠름
ARG WHISPER_MODEL=base
RUN python -c "import whisper; whisper.load_model('${WHISPER_MODEL}')" || true


# ─── Stage 2: 실행 이미지 ────────────────────────────────────────
FROM python:3.11-slim

WORKDIR /app

# FFmpeg + 한글 자막 폰트 (Noto Sans CJK KR + Jua 디즈니풍 귀여운 폰트)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    libsndfile1 \
    fontconfig \
    wget \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/fonts \
    && wget -q -O /app/fonts/NotoSansCJKkr-Regular.otf \
       "https://github.com/notofonts/noto-cjk/raw/main/Sans/OTF/Korean/NotoSansCJKkr-Regular.otf" \
    && printf '%s\n' '<?xml version="1.0"?>' '<!DOCTYPE fontconfig SYSTEM "fonts.dtd">' '<fontconfig><dir>/app/fonts</dir></fontconfig>' > /etc/fonts/conf.d/99-app-fonts.conf \
    && fc-cache -fv
# Jua 폰트는 COPY fonts/ ./fonts/ 에서 로컬 파일로 가져옴 (Dockerfile 빌드 시 네트워크 불필요)

# builder에서 설치된 패키지 복사
COPY --from=builder /usr/local/lib/python3.11 /usr/local/lib/python3.11
COPY --from=builder /usr/local/bin /usr/local/bin
COPY --from=builder /root/.cache/whisper /root/.cache/whisper

# 소스 코드 복사 (디렉토리 단위로 분리 → 변경된 디렉토리만 재빌드)
COPY requirements.txt celery_app.py config.py consumer.py ./
COPY services/ ./services/
COPY tasks/ ./tasks/
COPY utils/ ./utils/
COPY scripts/ ./scripts/
COPY fonts/ ./fonts/

# 임시/출력 폴더 생성
RUN mkdir -p /app/temp /app/output /app/bgm

# 환경변수 기본값
ENV PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    WORKER_TEMP_DIR=/app/temp \
    WORKER_OUTPUT_DIR=/app/output

# 헬스체크 (Celery worker 상태)
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD celery -A celery_app inspect ping -d "celery@$$HOSTNAME" || exit 1

# 기본 커맨드: consumer + celery worker 동시 실행
CMD ["sh", "-c", "celery -A celery_app worker --loglevel=info --concurrency=${WORKER_CONCURRENCY:-2} & python consumer.py"]
