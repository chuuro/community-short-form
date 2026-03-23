package com.shortform.backend.controller;

import com.shortform.backend.dto.request.ScriptGenerateRequest;
import com.shortform.backend.dto.response.ApiResponse;
import com.shortform.backend.dto.response.ProjectResponse;
import com.shortform.backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/script/generate
 *
 * AI 지식 숏폼 스크립트 생성 엔드포인트.
 * topic + sceneCount를 받아 GPT-4o 스크립트 생성 + DALL-E 3 이미지 생성을 비동기로 시작하고
 * 즉시 projectId를 반환합니다. 클라이언트는 GET /api/projects/{id}로 상태를 폴링합니다.
 *
 * 처리 흐름:
 *   1. KNOWLEDGE 프로젝트 생성 (status=PARSING)
 *   2. 비동기: GPT → 씬 JSON + DALL-E 3 → MinIO 업로드 → MediaItem/Subtitle 저장
 *   3. 완료 시 status=PARSED → 렌더 요청 가능
 */
@RestController
@RequestMapping("/api/script")
public class ScriptController {

    private final ProjectService projectService;

    public ScriptController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * AI 지식 숏폼 스크립트 생성 요청
     *
     * @param request { topic: "커피의 효능", sceneCount: 5, outputPlatform: "YOUTUBE_SHORTS" }
     * @return 202 Accepted + ProjectResponse (id, status=PARSING)
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ProjectResponse>> generateScript(
            @Valid @RequestBody ScriptGenerateRequest request) {

        // 1. 프로젝트 즉시 생성 (PARSING 상태)
        ProjectResponse project = projectService.createKnowledgeProject(request);

        // 2. 비동기 스크립트 + 이미지 생성 시작 (Spring AOP @Async를 통해 프록시 호출)
        projectService.generateKnowledgeScriptAsync(project.getId(), request.getTopic(), request.getSceneCount());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(
                        "AI 스크립트 생성을 시작했습니다. GET /api/projects/" + project.getId() + " 로 진행 상태를 확인하세요.",
                        project));
    }
}
