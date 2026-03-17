package com.shortform.backend.controller;

import com.shortform.backend.dto.request.UpdateMediaRequest;
import com.shortform.backend.dto.response.ApiResponse;
import com.shortform.backend.dto.response.MediaItemResponse;
import com.shortform.backend.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * GET /api/projects/{projectId}/media
     * 프로젝트의 미디어 아이템 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MediaItemResponse>>> getMediaItems(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.ok(mediaService.getMediaItems(projectId)));
    }

    /**
     * PUT /api/projects/{projectId}/media
     * 타임라인 순서 및 노출 시간 일괄 업데이트
     */
    @PutMapping
    public ResponseEntity<ApiResponse<List<MediaItemResponse>>> updateMediaItems(
            @PathVariable Long projectId,
            @RequestBody UpdateMediaRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("미디어 순서/시간이 업데이트되었습니다.",
                               mediaService.updateMediaItems(projectId, request)));
    }
}
