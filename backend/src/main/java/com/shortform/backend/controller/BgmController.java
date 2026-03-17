package com.shortform.backend.controller;

import com.shortform.backend.dto.response.ApiResponse;
import com.shortform.backend.dto.response.BgmTrackResponse;
import com.shortform.backend.repository.BgmTrackRepository;
import com.shortform.backend.service.TempFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bgm")
public class BgmController {

    private final BgmTrackRepository bgmTrackRepository;
    private final TempFileService tempFileService;

    public BgmController(BgmTrackRepository bgmTrackRepository, TempFileService tempFileService) {
        this.bgmTrackRepository = bgmTrackRepository;
        this.tempFileService = tempFileService;
    }

    /**
     * GET /api/bgm
     * 전체 BGM 목록 (트렌딩 우선 정렬)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BgmTrackResponse>>> getBgmList() {
        List<BgmTrackResponse> list = bgmTrackRepository
                .findByIsActiveTrueOrderByIsTrendingDescNameAsc()
                .stream()
                .map(BgmTrackResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * GET /api/bgm?category=신나는
     * 카테고리별 BGM 목록
     */
    @GetMapping(params = "category")
    public ResponseEntity<ApiResponse<List<BgmTrackResponse>>> getBgmByCategory(
            @RequestParam String category) {
        List<BgmTrackResponse> list = bgmTrackRepository
                .findByCategoryAndIsActiveTrue(category)
                .stream()
                .map(BgmTrackResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * GET /api/admin/temp/size
     * Temp 폴더 용량 조회 (관리용)
     */
    @GetMapping("/admin/temp/size")
    public ResponseEntity<ApiResponse<String>> getTempSize() {
        long bytes = tempFileService.calculateTempDirSize();
        String size = formatBytes(bytes);
        return ResponseEntity.ok(ApiResponse.ok("Temp 폴더 용량: " + size, size));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
