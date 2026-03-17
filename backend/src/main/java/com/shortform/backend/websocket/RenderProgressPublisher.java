package com.shortform.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RenderProgressPublisher {

    private static final Logger log = LoggerFactory.getLogger(RenderProgressPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RenderProgressPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Frontend 구독 경로: /topic/render/{projectId}
    public void publishProgress(Long projectId, Long renderJobId,
                                 int progress, String status) {
        String destination = "/topic/render/" + projectId;

        Map<String, Object> payload = Map.of(
                "renderJobId", renderJobId,
                "projectId", projectId,
                "progress", progress,
                "status", status
        );

        messagingTemplate.convertAndSend(destination, payload);
        log.debug("렌더 진행 Push: projectId={}, progress={}%, status={}",
                projectId, progress, status);
    }

    // 파싱 진행 상태 Push
    public void publishParseStatus(Long projectId, String status, String message) {
        String destination = "/topic/parse/" + projectId;

        Map<String, Object> payload = Map.of(
                "projectId", projectId,
                "status", status,
                "message", message
        );

        messagingTemplate.convertAndSend(destination, payload);
    }
}
