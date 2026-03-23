package com.shortform.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortform.backend.client.NewsApiClient;
import com.shortform.backend.client.PexelsClient;
import com.shortform.backend.config.AppProperties;
import com.shortform.backend.domain.entity.NewsArticle;
import com.shortform.backend.domain.entity.NewsArticleMedia;
import com.shortform.backend.domain.enums.MediaType;
import com.shortform.backend.domain.enums.NewsArticleStatus;
import com.shortform.backend.domain.entity.MediaItem;
import com.shortform.backend.domain.entity.Project;
import com.shortform.backend.domain.entity.Subtitle;
import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.OutputPlatform;
import com.shortform.backend.domain.enums.ProjectStatus;
import com.shortform.backend.dto.request.AddMediaRequest;
import com.shortform.backend.dto.request.RenderRequest;
import com.shortform.backend.dto.request.UpdateKeywordsRequest;
import com.shortform.backend.dto.request.UpdateMediaExposureRequest;
import com.shortform.backend.dto.response.RenderJobResponse;
import com.shortform.backend.repository.MediaItemRepository;
import com.shortform.backend.repository.NewsArticleMediaRepository;
import com.shortform.backend.exception.NewsArticleNotFoundException;
import com.shortform.backend.repository.NewsArticleRepository;
import com.shortform.backend.repository.ProjectRepository;
import com.shortform.backend.repository.SubtitleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NewsArticleService {

    private static final Logger log = LoggerFactory.getLogger(NewsArticleService.class);

    private final NewsArticleRepository newsArticleRepository;
    private final NewsArticleMediaRepository newsArticleMediaRepository;
    private final ProjectRepository projectRepository;
    private final MediaItemRepository mediaItemRepository;
    private final SubtitleRepository subtitleRepository;
    private final NewsApiClient newsApiClient;
    private final PexelsClient pexelsClient;
    private final OpenAIService openAIService;
    private final RenderService renderService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public NewsArticleService(NewsArticleRepository newsArticleRepository,
                             NewsArticleMediaRepository newsArticleMediaRepository,
                             ProjectRepository projectRepository,
                             MediaItemRepository mediaItemRepository,
                             SubtitleRepository subtitleRepository,
                             NewsApiClient newsApiClient,
                             PexelsClient pexelsClient,
                             OpenAIService openAIService,
                             RenderService renderService,
                             AppProperties appProperties,
                             ObjectMapper objectMapper) {
        this.newsArticleRepository = newsArticleRepository;
        this.newsArticleMediaRepository = newsArticleMediaRepository;
        this.projectRepository = projectRepository;
        this.mediaItemRepository = mediaItemRepository;
        this.subtitleRepository = subtitleRepository;
        this.newsApiClient = newsApiClient;
        this.pexelsClient = pexelsClient;
        this.openAIService = openAIService;
        this.renderService = renderService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * NewsAPI에서 기사 수집 + 중복 체크
     * 최대 fetchLimitOnStartup 건만 신규 저장
     */
    @Transactional
    public int fetchAndSaveOnStartup() {
        String apiKey = appProperties.getNewsApi().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("NewsAPI 키 미설정 - 뉴스 수집 스킵");
            return 0;
        }

        int limit = appProperties.getNewsApi().getFetchLimitOnStartup();
        List<NewsApiClient.NewsArticleDto> dtos = newsApiClient.fetchTopHeadlines(
                apiKey, "us", limit * 3  // 여유있게 가져와서 중복 제외 후 limit만 저장
        );

        AtomicInteger saved = new AtomicInteger(0);
        for (NewsApiClient.NewsArticleDto dto : dtos) {
            if (saved.get() >= limit) break;
            if (newsArticleRepository.existsByUrl(dto.url())) continue;

            NewsArticle article = NewsArticle.builder()
                    .url(dto.url())
                    .title(dto.title())
                    .description(dto.description())
                    .content(dto.content())
                    .urlToImage(dto.urlToImage())
                    .sourceName(dto.sourceName())
                    .sourceId(dto.sourceId())
                    .author(dto.author())
                    .publishedAt(dto.publishedAt())
                    .status(NewsArticleStatus.FETCHED)
                    .build();

            newsArticleRepository.save(article);
            saved.incrementAndGet();
            log.info("뉴스 기사 저장: {} - {}", article.getId(), dto.title());
        }

        int count = saved.get();
        if (count > 0) {
            // 비동기로 메타데이터 추출 시작
            extractMetadataForFetchedArticles();
        }
        return count;
    }

    /**
     * FETCHED 상태 기사들에 대해 OpenAI 메타데이터 추출 (비동기)
     */
    @Async
    @Transactional
    public void extractMetadataForFetchedArticles() {
        List<NewsArticle> fetched = newsArticleRepository
                .findByStatusOrderByCreatedAtDesc(NewsArticleStatus.FETCHED,
                        org.springframework.data.domain.PageRequest.of(0, 10));

        for (NewsArticle article : fetched) {
            try {
                article.updateStatus(NewsArticleStatus.METADATA_EXTRACTING);
                newsArticleRepository.save(article);

                JsonNode metadata = openAIService.extractNewsMetadata(
                        article.getTitle(),
                        article.getDescription(),
                        article.getContent()
                );

                if (metadata != null) {
                    article.updateMetadata(
                            getText(metadata, "script"),
                            getText(metadata, "translatedTitle"),
                            getText(metadata, "translatedContent"),
                            toJsonArray(metadata, "thumbnailKeywords"),
                            toJsonArray(metadata, "imageSearchKeywords"),
                            toJsonArray(metadata, "videoSearchKeywords"),
                            getDouble(metadata, "estimatedDurationSeconds")
                    );
                    article.updateStatus(NewsArticleStatus.METADATA_READY);
                } else {
                    article.updateStatus(NewsArticleStatus.FAILED, "OpenAI 메타데이터 추출 실패");
                }
                newsArticleRepository.save(article);
                log.info("뉴스 메타데이터 추출 완료: {}", article.getId());
            } catch (Exception e) {
                log.error("뉴스 메타데이터 추출 실패: articleId={}", article.getId(), e);
                article.updateStatus(NewsArticleStatus.FAILED, e.getMessage());
                newsArticleRepository.save(article);
            }
        }
    }

    /**
     * 특정 기사 메타데이터 추출 (수동 트리거)
     */
    @Transactional
    public NewsArticle extractMetadata(Long articleId) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new NewsArticleNotFoundException(articleId));

        if (article.getStatus() != NewsArticleStatus.FETCHED) {
            throw new IllegalStateException("FETCHED 상태의 기사만 메타데이터 추출 가능합니다. 현재: " + article.getStatus());
        }

        article.updateStatus(NewsArticleStatus.METADATA_EXTRACTING);
        newsArticleRepository.save(article);

        try {
            JsonNode metadata = openAIService.extractNewsMetadata(
                    article.getTitle(),
                    article.getDescription(),
                    article.getContent()
            );

            if (metadata != null) {
                article.updateMetadata(
                        getText(metadata, "script"),
                        getText(metadata, "translatedTitle"),
                        getText(metadata, "translatedContent"),
                        toJsonArray(metadata, "thumbnailKeywords"),
                        toJsonArray(metadata, "imageSearchKeywords"),
                        toJsonArray(metadata, "videoSearchKeywords"),
                        getDouble(metadata, "estimatedDurationSeconds")
                );
                article.updateStatus(NewsArticleStatus.METADATA_READY);
            } else {
                article.updateStatus(NewsArticleStatus.FAILED, "OpenAI 메타데이터 추출 실패");
            }
        } catch (Exception e) {
            log.error("메타데이터 추출 실패: articleId={}", articleId, e);
            article.updateStatus(NewsArticleStatus.FAILED, e.getMessage());
        }
        return newsArticleRepository.save(article);
    }

    @Transactional(readOnly = true)
    public List<NewsArticle> listArticles(int page, int size) {
        return newsArticleRepository.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(page, size)
        );
    }

    @Transactional(readOnly = true)
    public NewsArticle getArticle(Long id) {
        return newsArticleRepository.findById(id)
                .orElseThrow(() -> new NewsArticleNotFoundException(id));
    }

    /**
     * 멀티미디어 검색 트리거 (METADATA_READY → MULTIMEDIA_READY)
     * Pexels API로 이미지/영상 검색 후 NewsArticleMedia 저장
     */
    @Transactional
    public NewsArticle fetchMultimedia(Long id) {
        NewsArticle article = newsArticleRepository.findById(id)
                .orElseThrow(() -> new NewsArticleNotFoundException(id));

        // RENDERED는 재검색 불가 (이미 제작 완료)
        if (article.getStatus() == NewsArticleStatus.RENDERED) {
            log.info("렌더 완료된 기사는 재검색 불가: articleId={}", id);
            return article;
        }

        if (article.getStatus() != NewsArticleStatus.METADATA_READY
                && article.getStatus() != NewsArticleStatus.MULTIMEDIA_READY) {
            throw new IllegalStateException(
                    "METADATA_READY 상태의 기사만 멀티미디어 검색 가능합니다. 현재: " + article.getStatus());
        }

        article.updateStatus(NewsArticleStatus.MULTIMEDIA_FETCHING);
        newsArticleRepository.saveAndFlush(article);

        try {
            List<String> imageKeywords = parseJsonKeywordsForFetch(article.getImageSearchKeywords());
            List<String> videoKeywords = parseJsonKeywordsForFetch(article.getVideoSearchKeywords());

            // 기존 미디어 삭제 (재검색 시)
            newsArticleMediaRepository.deleteByNewsArticleId(id);

            int orderIndex = 0;
            int maxSegments = Math.max(
                    imageKeywords != null ? imageKeywords.size() : 0,
                    videoKeywords != null ? videoKeywords.size() : 0
            );
            if (maxSegments == 0) {
                maxSegments = 1;
                imageKeywords = List.of(article.getTranslatedTitle() != null ? article.getTranslatedTitle() : "news");
                videoKeywords = List.of(article.getTranslatedTitle() != null ? article.getTranslatedTitle() : "news");
            }

            // 이미지 검색
            if (imageKeywords != null && !imageKeywords.isEmpty()) {
                List<List<PexelsClient.PhotoDto>> photoResults = pexelsClient.searchPhotos(imageKeywords);
                for (int seg = 0; seg < photoResults.size(); seg++) {
                    String keyword = seg < imageKeywords.size() ? imageKeywords.get(seg) : null;
                    for (PexelsClient.PhotoDto p : photoResults.get(seg)) {
                        NewsArticleMedia m = NewsArticleMedia.builder()
                                .newsArticle(article)
                                .mediaType(MediaType.IMAGE)
                                .sourceUrl(p.sourceUrl())
                                .width(p.width())
                                .height(p.height())
                                .thumbnailUrl(p.sourceUrl())
                                .orderIndex(seg)
                                .isSelected(false)
                                .searchKeyword(keyword)
                                .photographerName(p.photographerName())
                                .photographerUrl(p.photographerUrl())
                                .build();
                        newsArticleMediaRepository.save(m);
                    }
                }
            }

            // 영상 검색
            if (videoKeywords != null && !videoKeywords.isEmpty()) {
                List<List<PexelsClient.VideoDto>> videoResults = pexelsClient.searchVideos(videoKeywords);
                for (int seg = 0; seg < videoResults.size(); seg++) {
                    String keyword = seg < videoKeywords.size() ? videoKeywords.get(seg) : null;
                    for (PexelsClient.VideoDto v : videoResults.get(seg)) {
                        NewsArticleMedia m = NewsArticleMedia.builder()
                                .newsArticle(article)
                                .mediaType(MediaType.VIDEO)
                                .sourceUrl(v.sourceUrl())
                                .width(v.width())
                                .height(v.height())
                                .durationSeconds(v.durationSeconds())
                                .thumbnailUrl(v.thumbnailUrl())
                                .orderIndex(seg)
                                .isSelected(false)
                                .searchKeyword(keyword)
                                .photographerName(v.photographerName())
                                .photographerUrl(v.photographerUrl())
                                .build();
                        newsArticleMediaRepository.save(m);
                    }
                }
            }

            // 기본 선택: 각 orderIndex별 1개씩 선택 (영상 우선, 없으면 이미지)
            var allMedia = newsArticleMediaRepository.findByNewsArticleIdOrderByOrderIndexAsc(id);
            for (int seg = 0; seg < maxSegments; seg++) {
                int segFinal = seg;
                var inSegment = allMedia.stream().filter(m -> m.getOrderIndex() == segFinal).toList();
                var video = inSegment.stream().filter(m -> m.getMediaType() == MediaType.VIDEO).findFirst();
                var image = inSegment.stream().filter(m -> m.getMediaType() == MediaType.IMAGE).findFirst();
                NewsArticleMedia toSelect = video.orElse(image.orElse(null));
                if (toSelect != null) {
                    toSelect.setSelected(true);
                    newsArticleMediaRepository.save(toSelect);
                }
            }

            article.updateStatus(NewsArticleStatus.MULTIMEDIA_READY);
            newsArticleRepository.saveAndFlush(article);
            log.info("멀티미디어 검색 완료: articleId={}, 미디어 {}건", id, newsArticleMediaRepository.findByNewsArticleIdOrderByOrderIndexAsc(id).size());
        } catch (Exception e) {
            log.error("멀티미디어 검색 실패: articleId={}", id, e);
            article.updateStatus(NewsArticleStatus.FAILED, e.getMessage());
            newsArticleRepository.saveAndFlush(article);
        }
        return article;
    }

    @Transactional(readOnly = true)
    public List<NewsArticleMedia> getMedia(Long articleId) {
        return newsArticleMediaRepository.findByNewsArticleIdOrderByOrderIndexAsc(articleId);
    }

    /**
     * 파일 업로드로 멀티미디어 추가 (MinIO 저장)
     */
    @Transactional
    public NewsArticleMedia addMediaFromUpload(Long articleId, MultipartFile file,
                                               String mediaType, MinioService minioService) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new NewsArticleNotFoundException(articleId));
        if (article.getStatus() != NewsArticleStatus.MULTIMEDIA_READY
                && article.getStatus() != NewsArticleStatus.RENDER_REQUESTED
                && article.getStatus() != NewsArticleStatus.RENDERED) {
            throw new IllegalStateException("MULTIMEDIA_READY 상태에서만 수동 추가 가능합니다.");
        }
        String objectKey = minioService.uploadMediaFile(file, articleId);
        MediaType type = "VIDEO".equalsIgnoreCase(mediaType) ? MediaType.VIDEO : MediaType.IMAGE;
        int maxOrderIndex = 0;
        for (NewsArticleMedia m : newsArticleMediaRepository.findByNewsArticleIdOrderByOrderIndexAsc(articleId)) {
            if (m.getOrderIndex() != null && m.getOrderIndex() > maxOrderIndex) maxOrderIndex = m.getOrderIndex();
        }
        String sourceUrl = "minio:" + objectKey;
        NewsArticleMedia media = NewsArticleMedia.builder()
                .newsArticle(article)
                .mediaType(type)
                .sourceUrl(sourceUrl)
                .orderIndex(maxOrderIndex + 1)
                .isSelected(false)
                .searchKeyword("(파일 업로드)")
                .build();
        return newsArticleMediaRepository.save(media);
    }

    /**
     * 사용자가 선택한 미디어로 업데이트 (렌더 시 사용할 항목)
     */
    @Transactional
    public void updateMediaSelection(Long articleId, List<Long> selectedMediaIds) {
        var all = newsArticleMediaRepository.findByNewsArticleIdOrderByOrderIndexAsc(articleId);
        var idSet = selectedMediaIds != null ? new java.util.HashSet<>(selectedMediaIds) : new java.util.HashSet<Long>();
        for (NewsArticleMedia m : all) {
            m.setSelected(idSet.contains(m.getId()));
            newsArticleMediaRepository.save(m);
        }
    }

    /**
     * 검색 키워드 업데이트 (이미지/영상 검색어 추가·수정)
     */
    @Transactional
    public NewsArticle updateKeywords(Long articleId, UpdateKeywordsRequest request) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new NewsArticleNotFoundException(articleId));
        if (article.getStatus() != NewsArticleStatus.METADATA_READY
                && article.getStatus() != NewsArticleStatus.MULTIMEDIA_READY
                && article.getStatus() != NewsArticleStatus.RENDER_REQUESTED
                && article.getStatus() != NewsArticleStatus.RENDERED) {
            throw new IllegalStateException(
                    "METADATA_READY 또는 MULTIMEDIA_READY 상태에서만 키워드 수정 가능합니다.");
        }
        if (request.getImageSearchKeywords() != null) {
            for (var item : request.getImageSearchKeywords()) {
                if (item != null && "user".equalsIgnoreCase(item.getSource())
                        && item.getKeyword() != null && item.getKeyword().length() > 20) {
                    throw new IllegalArgumentException("사용자 추가 검색어는 20자 이내로 입력해 주세요.");
                }
            }
        }
        if (request.getVideoSearchKeywords() != null) {
            for (var item : request.getVideoSearchKeywords()) {
                if (item != null && "user".equalsIgnoreCase(item.getSource())
                        && item.getKeyword() != null && item.getKeyword().length() > 20) {
                    throw new IllegalArgumentException("사용자 추가 검색어는 20자 이내로 입력해 주세요.");
                }
            }
        }
        String imageJson = request.getImageSearchKeywords() != null
                ? objectMapper.valueToTree(request.getImageSearchKeywords()).toString() : null;
        String videoJson = request.getVideoSearchKeywords() != null
                ? objectMapper.valueToTree(request.getVideoSearchKeywords()).toString() : null;
        article.updateKeywords(imageJson, videoJson);
        return newsArticleRepository.save(article);
    }

    /**
     * 멀티미디어 수동 추가 (URL로 이미지/영상 추가)
     */
    @Transactional
    public NewsArticleMedia addMedia(Long articleId, AddMediaRequest request) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new NewsArticleNotFoundException(articleId));
        if (article.getStatus() != NewsArticleStatus.MULTIMEDIA_READY
                && article.getStatus() != NewsArticleStatus.RENDER_REQUESTED
                && article.getStatus() != NewsArticleStatus.RENDERED) {
            throw new IllegalStateException(
                    "MULTIMEDIA_READY 상태에서만 수동 추가 가능합니다.");
        }
        if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
            throw new IllegalArgumentException("sourceUrl은 필수입니다.");
        }
        MediaType type = "VIDEO".equalsIgnoreCase(request.getMediaType()) ? MediaType.VIDEO : MediaType.IMAGE;
        int maxOrderIndex = 0;
        for (NewsArticleMedia m : newsArticleMediaRepository.findByNewsArticleIdOrderByOrderIndexAsc(articleId)) {
            if (m.getOrderIndex() != null && m.getOrderIndex() > maxOrderIndex) maxOrderIndex = m.getOrderIndex();
        }
        NewsArticleMedia media = NewsArticleMedia.builder()
                .newsArticle(article)
                .mediaType(type)
                .sourceUrl(request.getSourceUrl().trim())
                .thumbnailUrl(request.getThumbnailUrl())
                .orderIndex(maxOrderIndex + 1)
                .isSelected(false)
                .searchKeyword("(수동 추가)")
                .build();
        return newsArticleMediaRepository.save(media);
    }

    /**
     * 미디어 노출 시간 일괄 업데이트
     */
    @Transactional
    public void updateMediaExposure(Long articleId, UpdateMediaExposureRequest request) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new NewsArticleNotFoundException(articleId));
        if (article.getStatus() != NewsArticleStatus.MULTIMEDIA_READY
                && article.getStatus() != NewsArticleStatus.RENDER_REQUESTED
                && article.getStatus() != NewsArticleStatus.RENDERED) {
            throw new IllegalStateException(
                    "MULTIMEDIA_READY 상태에서만 노출 시간 수정 가능합니다.");
        }
        Double scriptDuration = article.getEstimatedDurationSeconds() != null
                ? article.getEstimatedDurationSeconds() : 60.0;
        var allMedia = newsArticleMediaRepository.findByNewsArticleIdOrderByOrderIndexAsc(articleId);
        var mediaById = new java.util.HashMap<Long, NewsArticleMedia>();
        for (NewsArticleMedia m : allMedia) {
            mediaById.put(m.getId(), m);
        }
        if (request.getItems() != null) {
            for (UpdateMediaExposureRequest.MediaExposureItem item : request.getItems()) {
                if (item.getMediaId() == null) continue;
                NewsArticleMedia m = mediaById.get(item.getMediaId());
                if (m == null) continue;
                Double sec = item.getExposureDurationSeconds();
                if (sec != null && sec > 0 && sec <= scriptDuration) {
                    m.setExposureDurationSeconds(sec);
                    newsArticleMediaRepository.save(m);
                }
            }
        }
    }

    /**
     * NewsArticle → Project 변환 후 렌더 요청
     * MULTIMEDIA_READY 상태에서만 호출 가능
     */
    @Transactional
    public RenderJobResponse requestRender(Long articleId, RenderRequest request) {
        NewsArticle article = newsArticleRepository.findById(articleId)
                .orElseThrow(() -> new NewsArticleNotFoundException(articleId));

        if (article.getStatus() != NewsArticleStatus.MULTIMEDIA_READY
                && article.getStatus() != NewsArticleStatus.RENDER_REQUESTED
                && article.getStatus() != NewsArticleStatus.RENDERED) {
            throw new IllegalStateException(
                    "MULTIMEDIA_READY 상태의 기사만 렌더 요청 가능합니다. 현재: " + article.getStatus());
        }

        List<NewsArticleMedia> selectedMedia =
                newsArticleMediaRepository.findByNewsArticleIdAndIsSelectedTrueOrderByOrderIndexAsc(articleId);
        if (selectedMedia.isEmpty()) {
            throw new IllegalStateException("선택된 미디어가 없습니다. 미디어를 선택한 후 렌더를 요청하세요.");
        }
        if (selectedMedia.size() < 2) {
            throw new IllegalStateException("멀티미디어를 최소 2개 이상 선택해 주세요.");
        }

        Project project;
        if (article.getProjectId() != null) {
            project = projectRepository.findActiveById(article.getProjectId())
                    .orElse(null);
        } else {
            project = null;
        }

        if (project == null) {
            project = createProjectFromArticle(article);
            article.setProjectId(project.getId());
            article.updateStatus(NewsArticleStatus.RENDER_REQUESTED);
            newsArticleRepository.save(article);
        }

        if (request == null) request = new RenderRequest();
        if (request.getOutputPlatform() == null) request.setOutputPlatform(OutputPlatform.YOUTUBE_SHORTS);
        request.setPreview(false);  // 최종 산출물

        return renderService.requestRender(project.getId(), request);
    }

    /**
     * NewsArticle + NewsArticleMedia → Project, MediaItem, Subtitle 생성
     */
    @Transactional
    public Project createProjectFromArticle(NewsArticle article) {
        List<NewsArticleMedia> selectedMedia =
                newsArticleMediaRepository.findByNewsArticleIdAndIsSelectedTrueOrderByOrderIndexAsc(article.getId());

        if (selectedMedia.isEmpty()) {
            throw new IllegalStateException("선택된 미디어가 없습니다. 미디어를 선택한 후 렌더를 요청하세요.");
        }

        Project project = Project.builder()
                .communityUrl(article.getUrl())
                .communityType(CommunityType.NEWS)
                .title(article.getTranslatedTitle() != null ? article.getTranslatedTitle() : article.getTitle())
                .description(article.getTranslatedContent())
                .thumbnailUrl(article.getUrlToImage())
                .status(ProjectStatus.PARSED)
                .outputPlatform(OutputPlatform.YOUTUBE_SHORTS)
                .build();
        project = projectRepository.save(project);

        int orderIndex = 0;
        for (NewsArticleMedia m : selectedMedia) {
            Double exposureSec = m.getExposureDurationSeconds();
            Double startTime = 0.0;
            Double endTime;
            if (exposureSec != null && exposureSec > 0) {
                endTime = exposureSec;
            } else if (m.getMediaType() == MediaType.VIDEO && m.getDurationSeconds() != null && m.getDurationSeconds() > 0) {
                endTime = m.getDurationSeconds();
            } else {
                endTime = 4.0;  // IMAGE 기본 4초
            }
            MediaItem item = MediaItem.builder()
                    .project(project)
                    .mediaType(m.getMediaType())
                    .sourceUrl(m.getSourceUrl())
                    .thumbnailUrl(m.getThumbnailUrl())
                    .width(m.getWidth())
                    .height(m.getHeight())
                    .durationSeconds(m.getDurationSeconds())
                    .orderIndex(orderIndex++)
                    .exposureStartTime(startTime)
                    .exposureEndTime(endTime)
                    .build();
            mediaItemRepository.save(item);
        }

        // script → subtitles (줄바꿈 구분, 예상 시간으로 start/end 추정)
        String script = article.getScript();
        if (script != null && !script.isBlank()) {
            String[] lines = script.split("\\r?\\n");
            double estimatedTotal = article.getEstimatedDurationSeconds() != null
                    ? article.getEstimatedDurationSeconds() : 35.0;
            double secPerLine = lines.length > 0 ? estimatedTotal / lines.length : 3.0;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                double start = i * secPerLine;
                double end = start + secPerLine;
                Subtitle sub = Subtitle.builder()
                        .project(project)
                        .originalContent(line)
                        .content(line)
                        .startTime(start)
                        .endTime(end)
                        .orderIndex(i)
                        .build();
                subtitleRepository.save(sub);
            }
        }

        log.info("NewsArticle → Project 변환 완료: articleId={}, projectId={}", article.getId(), project.getId());
        return project;
    }

    /** 멀티미디어 검색 시 사용할 키워드 추출 (enabled=true만, 신규/레거시 포맷 지원) */
    private List<String> parseJsonKeywordsForFetch(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) return List.of();
            List<String> list = new ArrayList<>();
            for (JsonNode n : node) {
                if (n.isObject()) {
                    boolean enabled = !n.has("enabled") || n.path("enabled").asBoolean(true);
                    if (!enabled) continue;
                    String k = n.path("keyword").asText(null);
                    if (k != null && !k.isBlank()) list.add(k.trim());
                } else if (n.isTextual()) {
                    String s = n.asText();
                    if (s != null && !s.isBlank()) list.add(s.trim());
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("JSON 키워드 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseJsonStringArray(String json) {
        return parseJsonKeywordsForFetch(json);
    }

    private String getText(JsonNode node, String key) {
        JsonNode n = node.path(key);
        return n.isMissingNode() || n.isNull() ? null : n.asText(null);
    }

    private Double getDouble(JsonNode node, String key) {
        JsonNode n = node.path(key);
        if (n.isMissingNode() || n.isNull()) return null;
        return n.isNumber() ? n.asDouble() : null;
    }

    private String toJsonArray(JsonNode node, String key) {
        JsonNode n = node.path(key);
        if (n.isMissingNode() || n.isNull()) return null;
        if (n.isArray()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode elem : n) {
                Map<String, Object> item = new LinkedHashMap<>();
                if (elem.isObject() && elem.has("keyword")) {
                    item.put("keyword", elem.path("keyword").asText());
                    item.put("source", elem.has("source") ? elem.path("source").asText() : "openai");
                    item.put("enabled", !elem.has("enabled") || elem.path("enabled").asBoolean(true));
                } else if (elem.isTextual()) {
                    item.put("keyword", elem.asText().trim());
                    item.put("source", "openai");
                    item.put("enabled", true);
                }
                if (!item.isEmpty()) items.add(item);
            }
            try {
                return objectMapper.writeValueAsString(items);
            } catch (Exception e) {
                return n.toString();
            }
        }
        if (n.isTextual()) return n.asText();
        return null;
    }
}
