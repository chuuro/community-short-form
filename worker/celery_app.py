from celery import Celery
from config import settings

app = Celery(
    "shortform_worker",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["tasks.render_task"],
)

app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Seoul",
    enable_utc=True,
    # 태스크 재시도 설정
    task_acks_late=True,
    task_reject_on_worker_lost=True,
    worker_prefetch_multiplier=1,
    # 결과 만료 (24시간)
    result_expires=86400,
    # 태스크 타임아웃 (1시간)
    task_soft_time_limit=3600,
    task_time_limit=3660,
)
