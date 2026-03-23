package com.shortform.backend.service;

import com.shortform.backend.config.AppProperties;
import com.shortform.backend.domain.entity.MediaItem;
import com.shortform.backend.domain.entity.Project;
import com.shortform.backend.domain.enums.MediaType;
import com.shortform.backend.dto.request.UpdateMediaRequest;
import com.shortform.backend.dto.response.MediaItemResponse;
import com.shortform.backend.exception.ProjectNotFoundException;
import com.shortform.backend.repository.MediaItemRepository;
import com.shortform.backend.repository.ProjectRepository;
import com.shortform.backend.service.parser.CommunityParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaItemRepository mediaItemRepository;
    private final ProjectRepository projectRepository;
    private final AppProperties appProperties;

    public MediaService(MediaItemRepository mediaItemRepository,
                        ProjectRepository projectRepository,
                        AppProperties appProperties) {
        this.mediaItemRepository = mediaItemRepository;
        this.projectRepository = projectRepository;
        this.appProperties = appProperties;
    }

    // 파싱된 미디어 목록을 DB에 저장하고 로컬 다운로드
    public List<MediaItemResponse> saveAndDownloadMedia(Long projectId,
                                                         List<CommunityParser.ParsedMedia> parsedMediaList,
                                                         List<String> popularComments) {
        Project project = projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        // 미디어 아이템 저장
        for (CommunityParser.ParsedMedia parsed : parsedMediaList) {
            MediaItem item = MediaItem.builder()
                    .project(project)
                    .mediaType(parsed.mediaType())
                    .sourceUrl(parsed.sourceUrl())
                    .altText(parsed.altText())
                    .orderIndex(parsed.orderIndex())
                    .thumbnailUrl(parsed.thumbnailUrl())
                    .isGif(parsed.isGif())
                    .width(parsed.width())
                    .height(parsed.height())
                    .durationSeconds(parsed.durationSeconds())
                    .build();

            MediaItem saved = mediaItemRepository.save(item);
            downloadMediaFile(saved);
        }

        // 인기 댓글을 TEXT 타입으로 저장
        int commentOrder = parsedMediaList.size();
        for (String comment : popularComments) {
            MediaItem commentItem = MediaItem.builder()
                    .project(project)
                    .mediaType(MediaType.TEXT)
                    .altText(comment)
                    .orderIndex(commentOrder++)
                    .isPopularComment(true)
                    .build();
            mediaItemRepository.save(commentItem);
        }

        return mediaItemRepository.findByProjectIdOrderByOrderIndexAsc(projectId)
                .stream()
                .map(MediaItemResponse::from)
                .toList();
    }

    // 타임라인 순서 및 노출 시간 일괄 업데이트
    public List<MediaItemResponse> updateMediaItems(Long projectId,
                                                     UpdateMediaRequest request) {
        for (UpdateMediaRequest.MediaItemUpdate update : request.getItems()) {
            MediaItem item = mediaItemRepository.findById(update.getMediaItemId())
                    .orElseThrow(() -> new RuntimeException("미디어 아이템을 찾을 수 없습니다."));

            if (update.getOrderIndex() != null) {
                item.updateOrder(update.getOrderIndex());
            }
            if (update.getExposureStartTime() != null || update.getExposureEndTime() != null) {
                item.updateExposureTime(update.getExposureStartTime(), update.getExposureEndTime());
            }
            if (update.getIsIncluded() != null && update.getIsIncluded() != item.isIncluded()) {
                item.toggleIncluded();
            }
        }

        return mediaItemRepository.findByProjectIdOrderByOrderIndexAsc(projectId)
                .stream()
                .map(MediaItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> getMediaItems(Long projectId) {
        return mediaItemRepository.findByProjectIdOrderByOrderIndexAsc(projectId)
                .stream()
                .map(MediaItemResponse::from)
                .toList();
    }

    private void downloadMediaFile(MediaItem item) {
        if (item.getSourceUrl() == null || item.getSourceUrl().isEmpty()) return;
        // TEXT 타입(댓글)은 다운로드 불필요
        if (item.getMediaType() == MediaType.TEXT) return;
        // YouTube URL은 yt-dlp(Worker)가 처리 → 직접 다운로드 스킵
        if (item.getSourceUrl().contains("youtube.com") || item.getSourceUrl().contains("youtu.be")) {
            log.info("YouTube URL은 Worker에서 yt-dlp로 처리 예정: {}", item.getSourceUrl());
            return;
        }

        try {
            Path tempDir = Paths.get(appProperties.getStorage().getTempDir(),
                                     String.valueOf(item.getProject().getId()));
            Files.createDirectories(tempDir);

            String extension = getExtension(item.getSourceUrl(), item.getMediaType());
            String fileName = UUID.randomUUID() + extension;
            Path filePath = tempDir.resolve(fileName);

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(item.getSourceUrl()))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                Files.write(filePath, response.body());
                item.updateLocalPath(filePath.toString());

                long fileSize = Files.size(filePath);
                // 저화질 경고: 이미지 100KB 미만
                if (item.getMediaType() == MediaType.IMAGE && fileSize < 100_000) {
                    item.markAsLowQuality();
                }
                log.info("미디어 다운로드 완료: {}", filePath);
            } else {
                log.warn("미디어 다운로드 실패 (HTTP {}): {}", response.statusCode(), item.getSourceUrl());
            }
        } catch (IOException | InterruptedException e) {
            log.error("미디어 다운로드 오류: {}", item.getSourceUrl(), e);
        }
    }

    private String getExtension(String url, MediaType mediaType) {
        String cleanUrl = url.split("\\?")[0];
        int dotIndex = cleanUrl.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = cleanUrl.substring(dotIndex);
            if (ext.length() <= 5) return ext;
        }
        return switch (mediaType) {
            case VIDEO -> ".mp4";
            case IMAGE -> ".jpg";
            case AUDIO -> ".mp3";
            case TEXT -> ".txt";
        };
    }
}
