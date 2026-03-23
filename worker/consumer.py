"""
RabbitMQ 소비자 (진입점)

Spring Boot가 shortform.exchange → render.queue / preview.queue 로 발행한
순수 JSON 메시지를 pika로 수신하고, Celery 태스크로 디스패치합니다.

실행:
    python consumer.py
"""

import json
import signal
import sys
import time

import pika
import pika.exceptions

from celery_app import app as celery_app
from config import settings
from utils.logger import get_logger

logger = get_logger("consumer")


def on_render_message(channel, method, properties, body):
    """render.queue 메시지 핸들러"""
    try:
        payload = json.loads(body.decode("utf-8"))
        job_id = payload.get("jobId", "unknown")
        project_id = payload.get("projectId")
        is_preview = payload.get("isPreview", False)

        logger.info(
            "렌더 메시지 수신 | jobId=%s, projectId=%s, preview=%s",
            job_id, project_id, is_preview,
        )

        # Celery 태스크 디스패치 (Redis 브로커를 통해)
        celery_app.send_task(
            "tasks.process_render",
            kwargs=payload,
        )

        # 메시지 수신 확인
        channel.basic_ack(delivery_tag=method.delivery_tag)
        logger.info("태스크 디스패치 완료 | jobId=%s", job_id)

    except json.JSONDecodeError as e:
        logger.error("메시지 JSON 파싱 오류: %s | body=%s", e, body)
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
    except Exception as e:
        logger.error("메시지 처리 오류: %s", e, exc_info=True)
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)


def create_connection() -> pika.BlockingConnection:
    """RabbitMQ 연결 생성 (재시도 포함)"""
    params = pika.URLParameters(settings.rabbitmq_url)
    params.heartbeat = 60
    params.blocked_connection_timeout = 300
    return pika.BlockingConnection(params)


def setup_channel(connection: pika.BlockingConnection) -> pika.channel.Channel:
    """채널 및 Exchange/Queue 설정"""
    channel = connection.channel()

    # Spring Boot와 동일한 Exchange 선언 (durable)
    channel.exchange_declare(
        exchange=settings.rabbitmq_exchange,
        exchange_type="direct",
        durable=True,
    )

    # render.queue
    channel.queue_declare(queue=settings.rabbitmq_render_queue, durable=True)
    channel.queue_bind(
        queue=settings.rabbitmq_render_queue,
        exchange=settings.rabbitmq_exchange,
        routing_key="render",
    )

    # preview.queue (render와 동일한 핸들러 사용)
    channel.queue_declare(queue=settings.rabbitmq_preview_queue, durable=True)
    channel.queue_bind(
        queue=settings.rabbitmq_preview_queue,
        exchange=settings.rabbitmq_exchange,
        routing_key="preview",
    )

    # 한 번에 1개씩 처리 (공정한 분배)
    channel.basic_qos(prefetch_count=1)

    channel.basic_consume(
        queue=settings.rabbitmq_render_queue,
        on_message_callback=on_render_message,
    )
    channel.basic_consume(
        queue=settings.rabbitmq_preview_queue,
        on_message_callback=on_render_message,
    )

    return channel


def start_consuming():
    """메인 소비 루프 (자동 재연결 포함)"""
    retry_delay = 5

    def handle_shutdown(signum, frame):
        logger.info("종료 신호 수신, 소비자를 중지합니다...")
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_shutdown)
    signal.signal(signal.SIGTERM, handle_shutdown)

    while True:
        connection = None
        try:
            logger.info("RabbitMQ 연결 중: %s", settings.rabbitmq_url)
            connection = create_connection()
            channel = setup_channel(connection)

            logger.info(
                "소비 시작 | queues: %s, %s",
                settings.rabbitmq_render_queue,
                settings.rabbitmq_preview_queue,
            )
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            logger.warning("RabbitMQ 연결 실패: %s — %d초 후 재시도", e, retry_delay)
            time.sleep(retry_delay)
            retry_delay = min(retry_delay * 2, 60)

        except KeyboardInterrupt:
            logger.info("소비자 중지")
            break

        except Exception as e:
            logger.error("예상치 못한 오류: %s", e, exc_info=True)
            time.sleep(retry_delay)

        finally:
            if connection and not connection.is_closed:
                try:
                    connection.close()
                except Exception:
                    pass


if __name__ == "__main__":
    start_consuming()
