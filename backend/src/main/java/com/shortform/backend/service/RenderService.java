package com.shortform.backend.service;

import com.shortform.backend.config.AppProperties;
import com.shortform.backend.domain.entity.Project;
import com.shortform.backend.domain.entity.RenderJob;
import com.shortform.backend.domain.enums.ProjectStatus;
import com.shortform.backend.domain.enums.RenderStatus;
import com.shortform.backend.dto.request.RenderRequest;
import com.shortform.backend.dto.response.RenderJobResponse;
import com.shortform.backend.exception.ProjectNotFoundException;
import com.shortform.backend.repository.ProjectRepository;
import com.shortform.backend.repository.RenderJobRepository;
import com.shortform.backend.websocket.RenderProgressPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class RenderService {

    private static final Logger log = LoggerFactory.getLogger(RenderService.class);

    private final RenderJobRepository renderJobRepository;
    private final ProjectRepository projectRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RenderProgressPublisher progressPublisher;
    private final AppProperties appProperties;

    public RenderService(RenderJobRepository renderJobRepository,
                         ProjectRepository projectRepository,
                         RabbitTemplate rabbitTemplate,
                         RenderProgressPublisher progressPublisher,
                         AppProperties appProperties) {
        this.renderJobRepository = renderJobRepository;
        this.projectRepository = projectRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.progressPublisher = progressPublisher;
        this.appProperties = appProperties;
    }

    // 렌더 작업 요청 (미리보기 or 최종)
    public RenderJobResponse requestRender(Long projectId, RenderRequest request) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        RenderJob renderJob = RenderJob.builder()
                .project(project)
                .isPreview(request.isPreview())
                .build();

        RenderJob saved = renderJobRepository.save(renderJob);

        String workerJobId = UUID.randomUUID().toString();
        saved.assignWorkerJobId(workerJobId);

        // RabbitMQ에 렌더 작업 메시지 발행
        Map<String, Object> message = buildRenderMessage(project, saved, request);
        String routingKey = request.isPreview() ? "preview" : "render";
        String exchange = appProperties.getRabbitmq().getExchange();

        rabbitTemplate.convertAndSend(exchange, routingKey, message);

        project.updateStatus(request.isPreview()
                ? ProjectStatus.RENDERING : ProjectStatus.RENDER_PENDING);
        projectRepository.save(project);

        log.info("렌더 작업 큐 발행: jobId={}, preview={}", workerJobId, request.isPreview());
        return RenderJobResponse.from(saved);
    }

    // Worker에서 진행 상태 업데이트 (RabbitMQ 수신)
    public void updateRenderProgress(String workerJobId, int progress, String status) {
        RenderJob job = renderJobRepository.findByWorkerJobId(workerJobId)
                .orElse(null);
        if (job == null) {
            log.warn("렌더 작업을 찾을 수 없음: {}", workerJobId);
            return;
        }

        job.updateProgress(progress);

        if ("COMPLETED".equals(status)) {
            job.updateStatus(RenderStatus.COMPLETED);
            job.getProject().updateStatus(ProjectStatus.COMPLETED);
        } else if ("FAILED".equals(status)) {
            if (job.canRetry()) {
                job.incrementRetry();
                // 재시도 메시지 재발행
                requeue(job);
            } else {
                job.fail("최대 재시도 횟수 초과");
                job.getProject().updateStatus(ProjectStatus.FAILED);
            }
        } else {
            job.updateStatus(RenderStatus.PROCESSING);
        }

        renderJobRepository.save(job);

        // WebSocket으로 Frontend에 진행률 Push
        progressPublisher.publishProgress(job.getProject().getId(), job.getId(), progress, status);
    }

    @Transactional(readOnly = true)
    public RenderJobResponse getRenderJobStatus(Long renderJobId) {
        RenderJob job = renderJobRepository.findById(renderJobId)
                .orElseThrow(() -> new RuntimeException("렌더 작업을 찾을 수 없습니다."));
        return RenderJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<RenderJobResponse> getProjectRenderHistory(Long projectId) {
        return renderJobRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(RenderJobResponse::from)
                .toList();
    }

    private Map<String, Object> buildRenderMessage(Project project,
                                                    RenderJob job,
                                                    RenderRequest request) {
        Map<String, Object> message = new HashMap<>();
        message.put("jobId", job.getWorkerJobId());
        message.put("renderJobId", job.getId());
        message.put("projectId", project.getId());
        message.put("outputPlatform", project.getOutputPlatform().name());
        message.put("isPreview", job.isPreview());
        message.put("tempDir", appProperties.getStorage().getTempDir()
                               + "/" + project.getId());
        message.put("outputDir", appProperties.getStorage().getOutputDir());
        message.put("bgmTrackId", request.getBgmTrackId());
        message.put("includeWatermark", request.isIncludeWatermark());
        return message;
    }

    private void requeue(RenderJob job) {
        Map<String, Object> message = new HashMap<>();
        message.put("jobId", job.getWorkerJobId());
        message.put("renderJobId", job.getId());
        message.put("projectId", job.getProject().getId());
        message.put("retry", true);

        String exchange = appProperties.getRabbitmq().getExchange();
        String routingKey = job.isPreview() ? "preview" : "render";
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info("렌더 재시도 큐 발행: jobId={}, retry={}", job.getWorkerJobId(), job.getRetryCount());
    }
}
