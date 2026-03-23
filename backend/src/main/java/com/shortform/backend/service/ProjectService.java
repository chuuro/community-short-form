package com.shortform.backend.service;

import com.shortform.backend.config.AppProperties;
import com.shortform.backend.domain.entity.*;
import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.MediaType;
import com.shortform.backend.domain.enums.OutputPlatform;
import com.shortform.backend.domain.enums.ProjectStatus;
import com.shortform.backend.dto.request.CreateProjectRequest;
import com.shortform.backend.dto.request.ScriptGenerateRequest;
import com.shortform.backend.dto.request.UpdateSubtitleRequest;
import com.shortform.backend.dto.response.*;
import com.shortform.backend.exception.ProjectNotFoundException;
import com.shortform.backend.repository.*;
import com.shortform.backend.service.parser.CommunityParser;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final MediaItemRepository mediaItemRepository;
    private final SubtitleRepository subtitleRepository;
    private final BgmTrackRepository bgmTrackRepository;
    private final CommunityParserService communityParserService;
    private final MediaService mediaService;
    private final OpenAIService openAIService;
    private final AppProperties appProperties;
    private final MinioService minioService;

    public ProjectService(ProjectRepository projectRepository,
                          MediaItemRepository mediaItemRepository,
                          SubtitleRepository subtitleRepository,
                          BgmTrackRepository bgmTrackRepository,
                          CommunityParserService communityParserService,
                          MediaService mediaService,
                          OpenAIService openAIService,
                          AppProperties appProperties,
                          MinioService minioService) {
        this.projectRepository = projectRepository;
        this.mediaItemRepository = mediaItemRepository;
        this.subtitleRepository = subtitleRepository;
        this.bgmTrackRepository = bgmTrackRepository;
        this.communityParserService = communityParserService;
        this.mediaService = mediaService;
        this.openAIService = openAIService;
        this.appProperties = appProperties;
        this.minioService = minioService;
    }

    // 1. 프로젝트 생성 (URL 입력)
    public ProjectResponse createProject(CreateProjectRequest request) {
        CommunityType type = communityParserService.detectCommunityType(request.getCommunityUrl());

        Project project = Project.builder()
                .communityUrl(request.getCommunityUrl())
                .communityType(type)
                .outputPlatform(request.getOutputPlatform())
                .build();

        return ProjectResponse.from(projectRepository.save(project));
    }

    // 2. 미디어 파싱 시작 (비동기)
    @Async
    public void parseProject(Long projectId) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        project.updateStatus(ProjectStatus.PARSING);
        projectRepository.save(project);

        try {
            CommunityParser.ParsedPost parsedPost =
                    communityParserService.parse(project.getCommunityUrl());

            // 프로젝트 제목/설명 업데이트
            updateProjectMetadata(project, parsedPost);

            // 미디어 다운로드 및 저장
            mediaService.saveAndDownloadMedia(
                    projectId, parsedPost.mediaList(), parsedPost.popularComments());

            // OpenAI로 대본 생성 (키 없거나 실패해도 파싱 자체는 완료)
            List<MediaItem> mediaItems =
                    mediaItemRepository.findByProjectIdOrderByOrderIndexAsc(projectId);
            try {
                openAIService.generateScript(project, mediaItems);
            } catch (Exception aiEx) {
                log.warn("OpenAI 대본 생성 실패 (파싱은 계속 진행): {}", aiEx.getMessage());
            }

            project.updateStatus(ProjectStatus.PARSED);
            projectRepository.save(project);

            log.info("프로젝트 파싱 완료: {}", projectId);
        } catch (Exception e) {
            log.error("프로젝트 파싱 실패: {}", projectId, e);
            project.updateStatus(ProjectStatus.FAILED);
            projectRepository.save(project);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // KNOWLEDGE 타입: AI 스크립트 + DALL-E 3 이미지 프로젝트 생성
    // ─────────────────────────────────────────────────────────────────

    /**
     * KNOWLEDGE 프로젝트를 즉시 생성하고 반환합니다.
     * 비동기 스크립트 생성은 컨트롤러에서 별도 호출합니다.
     */
    public ProjectResponse createKnowledgeProject(ScriptGenerateRequest request) {
        OutputPlatform platform = request.getOutputPlatform() != null
                ? request.getOutputPlatform()
                : OutputPlatform.YOUTUBE_SHORTS;

        Project project = Project.builder()
                .communityUrl("knowledge://" + request.getTopic())
                .communityType(CommunityType.KNOWLEDGE)
                .title(request.getTopic())
                .outputPlatform(platform)
                .status(ProjectStatus.PARSING)
                .build();

        return ProjectResponse.from(projectRepository.save(project));
    }

    /**
     * GPT-4o 스크립트 생성 + DALL-E 3 이미지 생성을 비동기로 수행합니다.
     * 각 씬마다: narration → Subtitle, dalle_prompt → DALL-E 생성 → MinIO 저장 → MediaItem
     */
    @Async
    @Transactional
    public void generateKnowledgeScriptAsync(Long projectId, String topic, int sceneCount) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        try {
            log.info("Knowledge 스크립트 생성 시작: projectId={}, topic='{}', scenes={}",
                    projectId, topic, sceneCount);

            // 1. GPT로 씬 스크립트 JSON 배열 생성
            JsonNode scenes = openAIService.generateKnowledgeScript(topic, sceneCount);
            if (scenes == null || scenes.isEmpty()) {
                throw new RuntimeException("GPT 스크립트 응답이 비어있습니다.");
            }

            // 2. 각 씬: DALL-E 이미지 생성 → MinIO 업로드 → MediaItem + Subtitle 저장
            for (int i = 0; i < scenes.size(); i++) {
                JsonNode scene = scenes.get(i);
                String narration  = scene.path("narration").asText("");
                String dallePrompt = scene.path("dalle_prompt").asText("");

                // DALL-E 3 이미지 생성 (9:16, 1024x1792)
                String imageMinioKey = null;
                String presignedUrl  = null;
                try {
                    String dalleUrl = openAIService.generateDalle3Image(dallePrompt);
                    if (dalleUrl != null) {
                        imageMinioKey = "media/" + projectId + "/scene_" + i + ".png";
                        minioService.uploadFromUrl(dalleUrl, imageMinioKey);
                        presignedUrl = minioService.getPresignedUrl(imageMinioKey);
                    }
                } catch (Exception e) {
                    log.warn("씬 {} DALL-E/MinIO 처리 실패 (건너뜀): {}", i + 1, e.getMessage());
                }

                // MediaItem 저장
                // sourceUrl에 "minio:" 접두사 → 워커가 MinIO 직접 다운로드
                String dallePromptTrimmed = dallePrompt.length() > 2000
                        ? dallePrompt.substring(0, 2000) : dallePrompt;
                MediaItem mediaItem = MediaItem.builder()
                        .project(project)
                        .mediaType(MediaType.IMAGE)
                        .sourceUrl(imageMinioKey != null ? "minio:" + imageMinioKey : null)
                        .localPath(presignedUrl)
                        .thumbnailUrl(presignedUrl)
                        .orderIndex(i)
                        .altText(dallePromptTrimmed)
                        .isIncluded(true)
                        .width(1024)
                        .height(1792)
                        .build();
                mediaItemRepository.save(mediaItem);

                // Subtitle 저장 (타임스탬프는 TTS 렌더 시 Whisper로 재정렬)
                double startTime = i * 5.0;
                Subtitle subtitle = Subtitle.builder()
                        .project(project)
                        .originalContent(narration)
                        .content(narration)
                        .startTime(startTime)
                        .endTime(startTime + 5.0)
                        .orderIndex(i)
                        .build();
                subtitleRepository.save(subtitle);

                log.info("씬 {}/{} 처리 완료: narration={}", i + 1, scenes.size(),
                        narration.length() > 30 ? narration.substring(0, 30) + "…" : narration);
            }

            project.updateStatus(ProjectStatus.PARSED);
            projectRepository.save(project);
            log.info("Knowledge 프로젝트 생성 완료: projectId={}, totalScenes={}", projectId, scenes.size());

        } catch (Exception e) {
            log.error("Knowledge 스크립트 생성 실패: projectId={}, error={}", projectId, e.getMessage(), e);
            project.updateStatus(ProjectStatus.FAILED);
            projectRepository.save(project);
        }
    }

    // 3. 자막 수정
    public List<SubtitleResponse> updateSubtitles(Long projectId,
                                                   UpdateSubtitleRequest request) {
        for (UpdateSubtitleRequest.SubtitleUpdate update : request.getSubtitles()) {
            Subtitle subtitle = subtitleRepository.findById(update.getSubtitleId())
                    .orElseThrow(() -> new RuntimeException("자막을 찾을 수 없습니다."));

            if (update.getContent() != null) {
                subtitle.updateContent(update.getContent());
            }
            if (update.getStartTime() != null || update.getEndTime() != null) {
                subtitle.updateTiming(update.getStartTime(), update.getEndTime());
            }
            if (update.getStyleJson() != null) {
                subtitle.updateStyle(update.getStyleJson());
            }
        }

        return subtitleRepository.findByProjectIdOrderByOrderIndexAsc(projectId)
                .stream()
                .map(SubtitleResponse::from)
                .toList();
    }

    // 4. 자막 원본으로 되돌리기
    public SubtitleResponse resetSubtitle(Long subtitleId) {
        Subtitle subtitle = subtitleRepository.findById(subtitleId)
                .orElseThrow(() -> new RuntimeException("자막을 찾을 수 없습니다."));
        subtitle.resetToOriginal();
        return SubtitleResponse.from(subtitle);
    }

    // 5. BGM 변경
    public ProjectResponse updateBgm(Long projectId, Long bgmTrackId) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        BgmTrack bgmTrack = bgmTrackRepository.findById(bgmTrackId)
                .orElseThrow(() -> new RuntimeException("BGM을 찾을 수 없습니다."));

        project.updateBgmTrack(bgmTrack);
        return ProjectResponse.from(projectRepository.save(project));
    }

    // 6. 프로젝트 목록 조회 (히스토리)
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjects() {
        return projectRepository.findAllActive(
                org.springframework.data.domain.PageRequest.of(0, 50,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
        ).stream().map(ProjectResponse::from).collect(java.util.stream.Collectors.toList());
    }

    // 7. 프로젝트 상세 조회
    @Transactional(readOnly = true)
    public ParseResultResponse getProjectDetail(Long projectId) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        List<MediaItemResponse> mediaItems =
                mediaItemRepository.findByProjectIdOrderByOrderIndexAsc(projectId)
                        .stream().map(MediaItemResponse::from).toList();

        List<SubtitleResponse> subtitles =
                subtitleRepository.findByProjectIdOrderByOrderIndexAsc(projectId)
                        .stream().map(SubtitleResponse::from).toList();

        List<String> warnings = new ArrayList<>();
        long lowQualityCount = mediaItems.stream().filter(MediaItemResponse::isLowQuality).count();
        if (lowQualityCount > 0) {
            warnings.add(lowQualityCount + "개의 저화질 미디어가 포함되어 있습니다.");
        }

        return ParseResultResponse.builder()
                .projectId(projectId)
                .status(project.getStatus())
                .communityUrl(project.getCommunityUrl())
                .communityType(project.getCommunityType())
                .outputPlatform(project.getOutputPlatform())
                .title(project.getTitle())
                .description(project.getDescription())
                .thumbnailUrl(project.getThumbnailUrl())
                .videoCount((int) mediaItems.stream()
                        .filter(m -> m.getMediaType() == com.shortform.backend.domain.enums.MediaType.VIDEO && !m.isGif()).count())
                .imageCount((int) mediaItems.stream()
                        .filter(m -> m.getMediaType() == com.shortform.backend.domain.enums.MediaType.IMAGE && !m.isGif()).count())
                .textCount((int) mediaItems.stream()
                        .filter(m -> m.getMediaType() == com.shortform.backend.domain.enums.MediaType.TEXT).count())
                .gifCount((int) mediaItems.stream().filter(MediaItemResponse::isGif).count())
                .popularCommentCount((int) mediaItems.stream()
                        .filter(MediaItemResponse::isPopularComment).count())
                .lowQualityCount((int) lowQualityCount)
                .outputFilePath(minioService.getPlayableUrl(project.getOutputFilePath()))
                .mediaItems(mediaItems)
                .subtitles(subtitles)
                .warnings(warnings)
                .build();
    }

    // 8. 브라우저 재생용 Presigned URL (minio:9000 → localhost:9000)
    @Transactional(readOnly = true)
    public String getPlayableOutputUrl(Long projectId) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        String path = project.getOutputFilePath();
        return minioService.getPlayableUrl(path);
    }

    // 9. Soft Delete
    public void deleteProject(Long projectId) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        project.softDelete();
        projectRepository.save(project);
        log.info("프로젝트 Soft Delete: {}", projectId);
    }

    private void updateProjectMetadata(Project project, CommunityParser.ParsedPost parsedPost) {
        project.updateMetadata(parsedPost.title(), parsedPost.description(), parsedPost.thumbnailUrl());
        projectRepository.save(project);
    }
}
