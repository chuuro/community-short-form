from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Backend API
    backend_url: str = "http://localhost:8080"

    # RabbitMQ
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"
    rabbitmq_render_queue: str = "render.queue"
    rabbitmq_preview_queue: str = "preview.queue"
    rabbitmq_exchange: str = "shortform.exchange"

    # Redis (Celery broker)
    redis_url: str = "redis://localhost:6379/0"

    # MinIO
    minio_endpoint: str = "localhost:9000"
    minio_public_endpoint: str | None = None  # Presigned URL용 (Docker: host.docker.internal:9000)
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin123"  # Docker Compose 기본값과 동일
    minio_bucket: str = "shortform"
    minio_secure: bool = False

    # Whisper
    whisper_model: str = "base"

    # Worker
    worker_temp_dir: str = "./temp"
    worker_output_dir: str = "./output"
    worker_concurrency: int = 2

    # Logging
    log_level: str = "INFO"


settings = Settings()
