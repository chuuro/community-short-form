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
        // 검색/홈/서브레딧 목록 URL 제외 → 개별 게시글 URL만 허용
        return url != null
                && url.contains("reddit.com")
                && url.contains("/comments/");
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

        // 포스트 썸네일
        String postThumbnail = resolveThumbnail(postData);

        // 1. Reddit 직접 업로드 영상 (or GIF)
        if (postData.has("media") && !postData.get("media").isNull()) {
            JsonNode media = postData.get("media");
            if (media.has("reddit_video")) {
                JsonNode rv = media.get("reddit_video");
                String videoUrl = rv.path("fallback_url").asText();
                boolean isGif = rv.path("is_gif").asBoolean(false);
                int width = rv.path("width").asInt(0);
                int height = rv.path("height").asInt(0);
                double duration = rv.path("duration").asDouble(0);
                mediaList.add(new ParsedMedia(
                        videoUrl, isGif ? MediaType.IMAGE : MediaType.VIDEO,
                        title(postData), orderIndex++,
                        postThumbnail, isGif,
                        width > 0 ? width : null,
                        height > 0 ? height : null,
                        duration > 0 ? duration : null));
            }
        }

        // 2. preview 영상 (preview.reddit_video_preview)
        if (mediaList.isEmpty() && postData.has("preview")) {
            JsonNode preview = postData.get("preview");
            if (preview.has("reddit_video_preview")) {
                JsonNode rvp = preview.get("reddit_video_preview");
                String videoUrl = rvp.path("fallback_url").asText();
                boolean isGif = rvp.path("is_gif").asBoolean(false);
                int width = rvp.path("width").asInt(0);
                int height = rvp.path("height").asInt(0);
                double duration = rvp.path("duration").asDouble(0);
                if (!videoUrl.isEmpty()) {
                    mediaList.add(new ParsedMedia(
                            videoUrl, isGif ? MediaType.IMAGE : MediaType.VIDEO,
                            title(postData), orderIndex++,
                            postThumbnail, isGif,
                            width > 0 ? width : null,
                            height > 0 ? height : null,
                            duration > 0 ? duration : null));
                }
            }
        }

        // 3. YouTube 임베드 포스트 (YouTube 링크 공유)
        if (mediaList.isEmpty()) {
            String domain = postData.path("domain").asText("");
            String destUrl = postData.path("url_overridden_by_dest").asText("");
            if (domain.contains("youtube.com") || domain.contains("youtu.be")
                    || destUrl.contains("youtube.com") || destUrl.contains("youtu.be")) {
                log.info("YouTube 임베드 감지: {}", destUrl);
                mediaList.add(new ParsedMedia(
                        destUrl, MediaType.VIDEO, title(postData), orderIndex++,
                        postThumbnail, false, null, null, null));
            }
        }

        // 4. 이미지 갤러리
        if (postData.has("gallery_data")) {
            JsonNode items = postData.path("gallery_data").path("items");
            JsonNode metadata = postData.path("media_metadata");

            for (JsonNode item : items) {
                String mediaId = item.path("media_id").asText();
                JsonNode mediaMeta = metadata.path(mediaId);
                String mimeType = mediaMeta.path("m").asText("");
                boolean isGif = mimeType.contains("gif");

                // 최고화질 URL (s 노드)
                String imageUrl = mediaMeta.path("s").path("u").asText().replace("&amp;", "&");
                if (imageUrl.isEmpty()) {
                    imageUrl = mediaMeta.path("s").path("gif").asText().replace("&amp;", "&");
                }
                int width = mediaMeta.path("s").path("x").asInt(0);
                int height = mediaMeta.path("s").path("y").asInt(0);

                // 썸네일: p 배열의 첫 번째 항목
                String thumb = null;
                JsonNode pList = mediaMeta.path("p");
                if (pList.isArray() && pList.size() > 0) {
                    thumb = pList.get(0).path("u").asText().replace("&amp;", "&");
                }

                if (!imageUrl.isEmpty()) {
                    mediaList.add(new ParsedMedia(
                            imageUrl, MediaType.IMAGE, null, orderIndex++,
                            thumb, isGif,
                            width > 0 ? width : null,
                            height > 0 ? height : null,
                            null));
                }
            }
        }

        // 5. 단일 이미지 / GIF
        if (mediaList.isEmpty() && postData.has("url")) {
            String url = postData.get("url").asText();
            String cleanUrl = url.split("\\?")[0].toLowerCase();
            if (cleanUrl.matches(".*\\.(jpg|jpeg|png|gif|webp)$")) {
                boolean isGif = cleanUrl.endsWith(".gif");
                // preview에서 크기 추출
                Integer w = null, h = null;
                String thumb = postThumbnail;
                JsonNode preview = postData.path("preview").path("images");
                if (preview.isArray() && preview.size() > 0) {
                    JsonNode source = preview.get(0).path("source");
                    int pw = source.path("width").asInt(0);
                    int ph = source.path("height").asInt(0);
                    if (pw > 0) { w = pw; h = ph; }
                    JsonNode pList = preview.get(0).path("resolutions");
                    if (pList.isArray() && pList.size() > 0) {
                        thumb = pList.get(0).path("url").asText().replace("&amp;", "&");
                    }
                }
                mediaList.add(new ParsedMedia(
                        url, MediaType.IMAGE, null, orderIndex,
                        thumb, isGif, w, h, null));
            }
        }

        return mediaList;
    }

    /** 포스트 썸네일 URL 해석 */
    private String resolveThumbnail(JsonNode postData) {
        // preview.images[0].resolutions 배열의 중간 크기 썸네일
        JsonNode images = postData.path("preview").path("images");
        if (images.isArray() && images.size() > 0) {
            JsonNode resolutions = images.get(0).path("resolutions");
            if (resolutions.isArray() && resolutions.size() > 0) {
                // 중간 크기 (배열 가운데)
                int idx = Math.min(2, resolutions.size() - 1);
                String u = resolutions.get(idx).path("url").asText().replace("&amp;", "&");
                if (!u.isEmpty()) return u;
            }
            // fallback: source
            String src = images.get(0).path("source").path("url").asText().replace("&amp;", "&");
            if (!src.isEmpty()) return src;
        }
        // Reddit 기본 thumbnail (URL이 http로 시작할 때만)
        String thumb = postData.path("thumbnail").asText("");
        return thumb.startsWith("http") ? thumb : null;
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
