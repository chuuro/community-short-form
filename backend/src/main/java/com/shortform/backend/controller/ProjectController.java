package com.shortform.backend.controller;

import com.shortform.backend.dto.request.CreateProjectRequest;
import com.shortform.backend.dto.request.UpdateSubtitleRequest;
import com.shortform.backend.dto.response.*;
import com.shortform.backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * POST /api/projects
     * URL 입력 → 프로젝트 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("프로젝트가 생성되었습니다.", projectService.createProject(request)));
    }

    /**
     * POST /api/projects/{id}/parse
     * 미디어 파싱 시작 (비동기)
     */
    @PostMapping("/{id}/parse")
    public ResponseEntity<ApiResponse<Void>> startParsing(@PathVariable Long id) {
        projectService.parseProject(id);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("파싱을 시작했습니다. WebSocket으로 진행 상태를 확인하세요.", null));
    }

    /**
     * GET /api/projects
     * 프로젝트 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjects() {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProjects()));
    }

    /**
     * GET /api/projects/{id}
     * 프로젝트 상세 (미디어 + 자막 포함)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ParseResultResponse>> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProjectDetail(id)));
    }

    /**
     * PUT /api/projects/{id}/subtitles
     * 자막 수정
     */
    @PutMapping("/{id}/subtitles")
    public ResponseEntity<ApiResponse<List<SubtitleResponse>>> updateSubtitles(
            @PathVariable Long id,
            @RequestBody UpdateSubtitleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.updateSubtitles(id, request)));
    }

    /**
     * POST /api/projects/{id}/subtitles/{subtitleId}/reset
     * 자막 원본으로 되돌리기
     */
    @PostMapping("/{id}/subtitles/{subtitleId}/reset")
    public ResponseEntity<ApiResponse<SubtitleResponse>> resetSubtitle(
            @PathVariable Long id,
            @PathVariable Long subtitleId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.resetSubtitle(subtitleId)));
    }

    /**
     * PATCH /api/projects/{id}/bgm
     * BGM 변경
     */
    @PatchMapping("/{id}/bgm")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateBgm(
            @PathVariable Long id,
            @RequestParam Long bgmTrackId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.updateBgm(id, bgmTrackId)));
    }

    /**
     * DELETE /api/projects/{id}
     * Soft Delete + Temp 파일 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok(ApiResponse.ok("프로젝트가 삭제되었습니다.", null));
    }
}
