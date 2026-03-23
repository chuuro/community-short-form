package com.shortform.backend.controller;

import com.shortform.backend.dto.request.RenderRequest;
import com.shortform.backend.dto.response.ApiResponse;
import com.shortform.backend.dto.response.RenderJobResponse;
import com.shortform.backend.service.RenderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class RenderController {

    private final RenderService renderService;

    public RenderController(RenderService renderService) {
        this.renderService = renderService;
    }

    /**
     * POST /api/projects/{projectId}/render
     * 렌더 작업 시작 (미리보기 or 최종)
     */
    @PostMapping("/api/projects/{projectId}/render")
    public ResponseEntity<ApiResponse<RenderJobResponse>> startRender(
            @PathVariable Long projectId,
            @RequestBody RenderRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("렌더 작업이 시작되었습니다.",
                                     renderService.requestRender(projectId, request)));
    }

    /**
     * GET /api/projects/{projectId}/render/history
     * 렌더 작업 이력 조회
     */
    @GetMapping("/api/projects/{projectId}/render/history")
    public ResponseEntity<ApiResponse<List<RenderJobResponse>>> getRenderHistory(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(
                ApiResponse.ok(renderService.getProjectRenderHistory(projectId)));
    }

    /**
     * GET /api/render/{renderJobId}/status
     * 특정 렌더 작업 상태 조회
     */
    @GetMapping("/api/render/{renderJobId}/status")
    public ResponseEntity<ApiResponse<RenderJobResponse>> getRenderStatus(
            @PathVariable Long renderJobId) {
        return ResponseEntity.ok(ApiResponse.ok(renderService.getRenderJobStatus(renderJobId)));
    }

    /**
     * POST /api/render/callback
     * Worker에서 렌더 진행 상태 콜백 수신
     * payload: { jobId, progress, status, outputFilePath?, errorMessage? }
     */
    @PostMapping("/api/render/callback")
    public ResponseEntity<Void> receiveRenderCallback(
            @RequestBody Map<String, Object> payload) {
        String workerJobId = (String) payload.get("jobId");
        int progress = ((Number) payload.getOrDefault("progress", 0)).intValue();
        String status = (String) payload.getOrDefault("status", "PROCESSING");
        String outputFilePath = (String) payload.get("outputFilePath");
        String errorMessage = (String) payload.get("errorMessage");

        renderService.updateRenderProgress(workerJobId, progress, status, outputFilePath, errorMessage);
        return ResponseEntity.ok().build();
    }
}
