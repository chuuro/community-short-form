package com.shortform.backend.controller;

import com.shortform.backend.domain.entity.NewsArticle;
import com.shortform.backend.domain.entity.NewsArticleMedia;
import com.shortform.backend.dto.request.AddMediaRequest;
import com.shortform.backend.dto.request.RenderRequest;
import com.shortform.backend.dto.request.UpdateKeywordsRequest;
import com.shortform.backend.dto.request.UpdateMediaExposureRequest;
import com.shortform.backend.dto.response.ApiResponse;
import com.shortform.backend.dto.response.NewsArticleMediaResponse;
import com.shortform.backend.dto.response.NewsArticleResponse;
import com.shortform.backend.dto.response.RenderJobResponse;
import com.shortform.backend.service.MinioService;
import com.shortform.backend.service.NewsArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/news-articles")
public class NewsArticleController {

    private final NewsArticleService newsArticleService;
    private final MinioService minioService;

    public NewsArticleController(NewsArticleService newsArticleService, MinioService minioService) {
        this.newsArticleService = newsArticleService;
        this.minioService = minioService;
    }

    /**
     * 뉴스 기사 목록 (페이지네이션)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<NewsArticle> articles = newsArticleService.listArticles(page, size);
        List<NewsArticleResponse> responses = articles.stream()
                .map(NewsArticleResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    /**
     * 뉴스 기사 상세
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> get(@PathVariable Long id) {
        NewsArticle article = newsArticleService.getArticle(id);
        return ResponseEntity.ok(ApiResponse.ok(NewsArticleResponse.from(article)));
    }

    /**
     * 수동 메타데이터 추출 (FETCHED 상태 기사에 대해)
     */
    @PostMapping("/{id}/extract-metadata")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> extractMetadata(@PathVariable Long id) {
        NewsArticle article = newsArticleService.extractMetadata(id);
        return ResponseEntity.ok(ApiResponse.ok(NewsArticleResponse.from(article)));
    }

    /**
     * 멀티미디어 검색 트리거 (METADATA_READY 상태 기사에 대해)
     */
    @PostMapping("/{id}/fetch-multimedia")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> fetchMultimedia(@PathVariable Long id) {
        NewsArticle article = newsArticleService.fetchMultimedia(id);
        return ResponseEntity.ok(ApiResponse.ok(NewsArticleResponse.from(article)));
    }

    /**
     * 뉴스 기사 미디어 목록 (MULTIMEDIA_READY 이후)
     * minio: 접두사는 Presigned URL로 변환하여 반환
     */
    @GetMapping("/{id}/media")
    public ResponseEntity<ApiResponse<List<NewsArticleMediaResponse>>> getMedia(@PathVariable Long id) {
        List<NewsArticleMedia> media = newsArticleService.getMedia(id);
        List<NewsArticleMediaResponse> responses = media.stream()
                .map(m -> {
                    NewsArticleMediaResponse r = NewsArticleMediaResponse.from(m);
                    if (r.getSourceUrl() != null && r.getSourceUrl().startsWith("minio:")) {
                        String objectKey = r.getSourceUrl().substring(6);
                        String presigned = minioService.getPresignedUrl(objectKey);
                        if (presigned != null) {
                            r.setSourceUrl(presigned);
                        }
                    }
                    return r;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    /**
     * 미디어 선택 업데이트 (렌더 시 사용할 항목)
     */
    @PutMapping("/{id}/media/selection")
    public ResponseEntity<ApiResponse<Void>> updateMediaSelection(
            @PathVariable Long id,
            @RequestBody List<Long> selectedMediaIds) {
        newsArticleService.updateMediaSelection(id, selectedMediaIds);
        return ResponseEntity.ok(ApiResponse.ok("선택이 저장되었습니다.", null));
    }

    /**
     * 검색 키워드 업데이트 (이미지/영상 검색어 추가·수정)
     */
    @PutMapping("/{id}/keywords")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> updateKeywords(
            @PathVariable Long id,
            @RequestBody UpdateKeywordsRequest request) {
        NewsArticle article = newsArticleService.updateKeywords(id, request);
        return ResponseEntity.ok(ApiResponse.ok(NewsArticleResponse.from(article)));
    }

    /**
     * 멀티미디어 파일 업로드 (이미지/영상)
     * MinIO에 저장 후 NewsArticleMedia 추가
     */
    @PostMapping(value = "/{id}/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsArticleMediaResponse>> uploadMedia(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("mediaType") String mediaType) {
        NewsArticleMedia media = newsArticleService.addMediaFromUpload(id, file, mediaType, minioService);
        NewsArticleMediaResponse r = NewsArticleMediaResponse.from(media);
        if (r.getSourceUrl() != null && r.getSourceUrl().startsWith("minio:")) {
            String presigned = minioService.getPresignedUrl(r.getSourceUrl().substring(6));
            if (presigned != null) r.setSourceUrl(presigned);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(r));
    }

    /**
     * 멀티미디어 수동 추가 (URL로 이미지/영상 추가)
     */
    @PostMapping("/{id}/media")
    public ResponseEntity<ApiResponse<NewsArticleMediaResponse>> addMedia(
            @PathVariable Long id,
            @RequestBody AddMediaRequest request) {
        NewsArticleMedia media = newsArticleService.addMedia(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(NewsArticleMediaResponse.from(media)));
    }

    /**
     * 미디어 노출 시간 일괄 업데이트
     */
    @PutMapping("/{id}/media/exposure")
    public ResponseEntity<ApiResponse<Void>> updateMediaExposure(
            @PathVariable Long id,
            @RequestBody UpdateMediaExposureRequest request) {
        newsArticleService.updateMediaExposure(id, request);
        return ResponseEntity.ok(ApiResponse.ok("노출 시간이 저장되었습니다.", null));
    }

    /**
     * 숏폼 제작 렌더 요청 (MULTIMEDIA_READY 상태에서)
     * NewsArticle → Project 변환 후 Worker에 렌더 큐 발행
     */
    @PostMapping("/{id}/render")
    public ResponseEntity<ApiResponse<RenderJobResponse>> requestRender(
            @PathVariable Long id,
            @RequestBody(required = false) RenderRequest request) {
        RenderJobResponse job = newsArticleService.requestRender(id, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("렌더 작업이 시작되었습니다.", job));
    }
}
