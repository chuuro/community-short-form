package com.shortform.backend.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class YoutubeParser implements CommunityParser {

    private static final Logger log = LoggerFactory.getLogger(YoutubeParser.class);

    private static final Pattern VIDEO_ID_PATTERN =
            Pattern.compile("(?:v=|youtu\\.be/)([a-zA-Z0-9_-]{11})");

    private final WebClient.Builder webClientBuilder;

    public YoutubeParser(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public CommunityType getSupportedType() {
        return CommunityType.YOUTUBE;
    }

    @Override
    public boolean supports(String url) {
        return url != null && (url.contains("youtube.com") || url.contains("youtu.be"));
    }

    @Override
    public ParsedPost parse(String url) {
        String videoId = extractVideoId(url);
        if (videoId == null) {
            throw new RuntimeException("유효하지 않은 YouTube URL입니다.");
        }

        // YouTube oEmbed API로 기본 메타데이터 조회 (API 키 불필요)
        String oEmbedUrl = "https://www.youtube.com/oembed?url=" +
                           "https://www.youtube.com/watch?v=" + videoId + "&format=json";

        try {
            WebClient client = webClientBuilder.build();
            JsonNode oEmbed = client.get()
                    .uri(oEmbedUrl)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String title = oEmbed != null ? oEmbed.path("title").asText() : "YouTube Video";
            String thumbnailUrl = oEmbed != null ? oEmbed.path("thumbnail_url").asText() : null;

            List<ParsedMedia> mediaList = new ArrayList<>();
            mediaList.add(new ParsedMedia(
                    "https://www.youtube.com/watch?v=" + videoId,
                    MediaType.VIDEO,
                    title,
                    0
            ));

            return new ParsedPost(title, "", thumbnailUrl, mediaList, List.of());
        } catch (Exception e) {
            log.error("YouTube 파싱 실패: {}", url, e);
            throw new RuntimeException("YouTube 영상을 파싱할 수 없습니다: " + e.getMessage());
        }
    }

    private String extractVideoId(String url) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
}
