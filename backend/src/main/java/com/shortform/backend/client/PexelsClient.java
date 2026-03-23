package com.shortform.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortform.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pexels API 클라이언트
 * - Photos: GET /v1/search?query=...
 * - Videos: GET /v1/videos/search?query=...
 */
@Component
public class PexelsClient {

    private static final Logger log = LoggerFactory.getLogger(PexelsClient.class);

    private final WebClient webClient;
    private final AppProperties appProperties;

    public PexelsClient(WebClient.Builder webClientBuilder, AppProperties appProperties) {
        this.appProperties = appProperties;
        String apiKey = appProperties.getPexels().getApiKey();
        this.webClient = webClientBuilder
                .baseUrl(appProperties.getPexels().getBaseUrl())
                .defaultHeader("Authorization", apiKey != null ? apiKey : "")
                .build();
    }

    /**
     * 이미지 검색 (구간별 키워드)
     *
     * @param keywords script 구간별 검색 키워드
     * @return orderIndex → [PhotoDto, ...]
     */
    public List<List<PhotoDto>> searchPhotos(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();

        String apiKey = appProperties.getPexels().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Pexels API 키가 설정되지 않아 이미지 검색을 건너뜁니다. (PEXELS_API_KEY)");
            return List.of();
        }

        int perPage = appProperties.getPexels().getPhotosPerKeyword();
        List<List<PhotoDto>> result = new ArrayList<>();

        for (int i = 0; i < keywords.size(); i++) {
            String q = keywords.get(i);
            if (q == null || q.isBlank()) {
                result.add(List.of());
                continue;
            }
            List<PhotoDto> photos = searchPhotosOne(q, perPage);
            result.add(photos);
        }
        return result;
    }

    /**
     * 영상 검색 (구간별 키워드)
     *
     * @param keywords script 구간별 검색 키워드
     * @return orderIndex → [VideoDto, ...]
     */
    public List<List<VideoDto>> searchVideos(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();

        String apiKey = appProperties.getPexels().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Pexels API 키가 설정되지 않아 영상 검색을 건너뜁니다. (PEXELS_API_KEY)");
            return List.of();
        }

        int perPage = appProperties.getPexels().getVideosPerKeyword();
        List<List<VideoDto>> result = new ArrayList<>();

        for (String q : keywords) {
            if (q == null || q.isBlank()) {
                result.add(List.of());
                continue;
            }
            List<VideoDto> videos = searchVideosOne(q, perPage);
            result.add(videos);
        }
        return result;
    }

    private List<PhotoDto> searchPhotosOne(String query, int perPage) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            JsonNode root = webClient.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("query", encoded)
                            .queryParam("per_page", Math.min(perPage, 80))
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null) return List.of();

            JsonNode photos = root.path("photos");
            List<PhotoDto> list = new ArrayList<>();
            for (JsonNode p : photos) {
                JsonNode src = p.path("src");
                String url = src.path("medium").asText(null);
                if (url == null) url = src.path("original").asText(null);
                if (url == null) continue;

                list.add(new PhotoDto(
                        p.path("id").asLong(),
                        url,
                        p.path("url").asText(null),
                        p.path("width").asInt(0),
                        p.path("height").asInt(0),
                        p.path("photographer").asText(null),
                        p.path("photographer_url").asText(null),
                        p.path("alt").asText(null)
                ));
            }
            return list;
        } catch (Exception e) {
            log.warn("Pexels 이미지 검색 실패: query={}, error={}", query, e.getMessage());
            return List.of();
        }
    }

    private List<VideoDto> searchVideosOne(String query, int perPage) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            JsonNode root = webClient.get()
                    .uri(uri -> uri.path("/videos/search")
                            .queryParam("query", encoded)
                            .queryParam("per_page", Math.min(perPage, 80))
                            .queryParam("orientation", "portrait")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null) return List.of();

            JsonNode videos = root.path("videos");
            List<VideoDto> list = new ArrayList<>();
            for (JsonNode v : videos) {
                String videoUrl = pickBestVideoLink(v);
                if (videoUrl == null) continue;

                int duration = v.path("duration").asInt(0);
                String thumb = v.path("image").asText(null);
                if (thumb == null && v.has("video_pictures")) {
                    JsonNode pics = v.path("video_pictures");
                    if (pics.isArray() && pics.size() > 0) {
                        thumb = pics.get(0).path("picture").asText(null);
                    }
                }

                JsonNode user = v.path("user");
                list.add(new VideoDto(
                        v.path("id").asLong(),
                        videoUrl,
                        v.path("url").asText(null),
                        v.path("width").asInt(0),
                        v.path("height").asInt(0),
                        duration > 0 ? (double) duration : null,
                        thumb,
                        user.path("name").asText(null),
                        user.path("url").asText(null)
                ));
            }
            return list;
        } catch (Exception e) {
            log.warn("Pexels 영상 검색 실패: query={}, error={}", query, e.getMessage());
            return List.of();
        }
    }

    /** portrait 9:16에 맞는 비디오 링크 선택 (hd > sd) */
    private String pickBestVideoLink(JsonNode v) {
        JsonNode files = v.path("video_files");
        if (!files.isArray()) return null;

        String best = null;
        int bestHeight = 0;
        for (JsonNode f : files) {
            String link = f.path("link").asText(null);
            if (link == null) continue;
            int w = f.path("width").asInt(0);
            int h = f.path("height").asInt(0);
            // portrait (세로) 선호, 1080x1920 근처
            if (h >= w && h > bestHeight && h <= 1920) {
                best = link;
                bestHeight = h;
            }
        }
        if (best != null) return best;
        // portrait 없으면 첫 번째
        return files.size() > 0 ? files.get(0).path("link").asText(null) : null;
    }

    public record PhotoDto(
            long pexelsId,
            String sourceUrl,
            String pageUrl,
            int width,
            int height,
            String photographerName,
            String photographerUrl,
            String alt
    ) {}

    public record VideoDto(
            long pexelsId,
            String sourceUrl,
            String pageUrl,
            int width,
            int height,
            Double durationSeconds,
            String thumbnailUrl,
            String photographerName,
            String photographerUrl
    ) {}
}
