package com.shortform.backend.service;

import com.shortform.backend.config.AppProperties;
import com.shortform.backend.domain.entity.*;
import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.ProjectStatus;
import com.shortform.backend.dto.request.CreateProjectRequest;
import com.shortform.backend.dto.request.UpdateSubtitleRequest;
import com.shortform.backend.dto.response.*;
import com.shortform.backend.exception.ProjectNotFoundException;
import com.shortform.backend.repository.*;
import com.shortform.backend.service.parser.CommunityParser;
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

    public ProjectService(ProjectRepository projectRepository,
                          MediaItemRepository mediaItemRepository,
                          SubtitleRepository subtitleRepository,
                          BgmTrackRepository bgmTrackRepository,
                          CommunityParserService communityParserService,
                          MediaService mediaService,
                          OpenAIService openAIService,
                          AppProperties appProperties) {
        this.projectRepository = projectRepository;
        this.mediaItemRepository = mediaItemRepository;
        this.subtitleRepository = subtitleRepository;
        this.bgmTrackRepository = bgmTrackRepository;
        this.communityParserService = communityParserService;
        this.mediaService = mediaService;
        this.openAIService = openAIService;
        this.appProperties = appProperties;
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
                .title(project.getTitle())
                .videoCount((int) mediaItems.stream()
                        .filter(m -> m.getMediaType().name().equals("VIDEO")).count())
                .imageCount((int) mediaItems.stream()
                        .filter(m -> m.getMediaType().name().equals("IMAGE")).count())
                .popularCommentCount((int) mediaItems.stream()
                        .filter(MediaItemResponse::isPopularComment).count())
                .lowQualityCount((int) lowQualityCount)
                .mediaItems(mediaItems)
                .subtitles(subtitles)
                .warnings(warnings)
                .build();
    }

    // 8. Soft Delete
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
