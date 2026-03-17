package com.shortform.backend.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortform.backend.config.AppProperties;
import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class RedditParser implements CommunityParser {

    private static final Logger log = LoggerFactory.getLogger(RedditParser.class);

    private final AppProperties appProperties;
    private final WebClient.Builder webClientBuilder;

    public RedditParser(AppProperties appProperties, WebClient.Builder webClientBuilder) {
        this.appProperties = appProperties;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public CommunityType getSupportedType() {
        return CommunityType.REDDIT;
    }

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("reddit.com");
    }

    @Override
    public ParsedPost parse(String url) {
        // Reddit JSON API: URL 뒤에 .json 붙이면 JSON 반환
        String jsonUrl = normalizeRedditUrl(url) + ".json?limit=10";

        WebClient client = webClientBuilder.baseUrl("https://www.reddit.com")
                .defaultHeader("User-Agent", appProperties.getReddit().getUserAgent())
                .build();

        try {
            JsonNode response = client.get()
                    .uri(jsonUrl.replace("https://www.reddit.com", ""))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return extractPost(response);
        } catch (Exception e) {
            log.error("Reddit 파싱 실패: {}", url, e);
            throw new RuntimeException("Reddit 게시글을 파싱할 수 없습니다: " + e.getMessage());
        }
    }

    private ParsedPost extractPost(JsonNode response) {
        JsonNode postData = response.get(0).get("data").get("children").get(0).get("data");
        JsonNode commentsData = response.get(1).get("data").get("children");

        String title = postData.path("title").asText();
        String selfText = postData.path("selftext").asText();
        String thumbnail = postData.path("thumbnail").asText();

        List<ParsedMedia> mediaList = extractMedia(postData);
        List<String> popularComments = extractPopularComments(commentsData, 5);

        return new ParsedPost(title, selfText, thumbnail, mediaList, popularComments);
    }

    private List<ParsedMedia> extractMedia(JsonNode postData) {
        List<ParsedMedia> mediaList = new ArrayList<>();
        int orderIndex = 0;

        // 동영상 추출
        if (postData.has("media") && !postData.get("media").isNull()) {
            JsonNode media = postData.get("media");
            if (media.has("reddit_video")) {
                String videoUrl = media.get("reddit_video").path("fallback_url").asText();
                mediaList.add(new ParsedMedia(videoUrl, MediaType.VIDEO, title(postData), orderIndex++));
            }
        }

        // 이미지 갤러리 추출
        if (postData.has("gallery_data")) {
            JsonNode items = postData.path("gallery_data").path("items");
            JsonNode metadata = postData.path("media_metadata");

            for (JsonNode item : items) {
                String mediaId = item.path("media_id").asText();
                JsonNode mediaMeta = metadata.path(mediaId);
                String imageUrl = mediaMeta.path("s").path("u").asText().replace("&amp;", "&");
                if (!imageUrl.isEmpty()) {
                    mediaList.add(new ParsedMedia(imageUrl, MediaType.IMAGE, null, orderIndex++));
                }
            }
        }

        // 단일 이미지
        if (mediaList.isEmpty() && postData.has("url")) {
            String url = postData.get("url").asText();
            if (url.matches(".*\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$")) {
                mediaList.add(new ParsedMedia(url, MediaType.IMAGE, null, orderIndex));
            }
        }

        return mediaList;
    }

    private List<String> extractPopularComments(JsonNode commentsData, int limit) {
        List<String> comments = new ArrayList<>();
        if (commentsData == null) return comments;

        for (JsonNode comment : commentsData) {
            JsonNode data = comment.path("data");
            String body = data.path("body").asText();
            if (!body.isEmpty() && !body.equals("[deleted]") && !body.equals("[removed]")) {
                comments.add(body);
                if (comments.size() >= limit) break;
            }
        }
        return comments;
    }

    private String normalizeRedditUrl(String url) {
        return url.replaceAll("/$", "");
    }

    private String title(JsonNode postData) {
        return postData.path("title").asText();
    }
}
