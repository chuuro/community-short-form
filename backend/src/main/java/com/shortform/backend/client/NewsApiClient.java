package com.shortform.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortform.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * NewsAPI.org 클라이언트
 * - Top Headlines: /v2/top-headlines
 * - Everything: /v2/everything
 */
@Component
public class NewsApiClient {

    private static final Logger log = LoggerFactory.getLogger(NewsApiClient.class);

    private final WebClient webClient;

    public NewsApiClient(WebClient.Builder webClientBuilder, AppProperties appProperties) {
        String baseUrl = appProperties.getNewsApi().getBaseUrl();
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "CommunityShortform/1.0")
                .build();
    }

    /**
     * Top Headlines 조회 (해당 국가/언어의 최신 뉴스)
     *
     * @param apiKey  NewsAPI API 키
     * @param country 2-letter country code (us, kr, gb 등), null이면 전체
     * @param pageSize 최대 100
     */
    public List<NewsArticleDto> fetchTopHeadlines(String apiKey, String country, int pageSize) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("NewsAPI 키가 설정되지 않아 뉴스 수집을 건너뜁니다. (NEWS_API_KEY 환경변수)");
            return List.of();
        }

        var uri = "/top-headlines?apiKey=" + apiKey + "&pageSize=" + Math.min(pageSize, 100);
        if (country != null && !country.isBlank()) {
            uri += "&country=" + country;
        }

        return fetchArticles(uri);
    }

    /**
     * Everything 검색 (키워드 기반)
     */
    public List<NewsArticleDto> fetchEverything(String apiKey, String query, String language, int pageSize) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("NewsAPI 키가 설정되지 않아 뉴스 수집을 건너뜁니다.");
            return List.of();
        }

        var uri = "/everything?apiKey=" + apiKey + "&pageSize=" + Math.min(pageSize, 100)
                + "&sortBy=publishedAt";
        if (query != null && !query.isBlank()) {
            uri += "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        }
        if (language != null && !language.isBlank()) {
            uri += "&language=" + language;
        }

        return fetchArticles(uri);
    }

    private List<NewsArticleDto> fetchArticles(String uri) {
        try {
            JsonNode root = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null || !"ok".equals(root.path("status").asText(""))) {
                String msg = root != null ? root.path("message").asText("Unknown error") : "No response";
                log.error("NewsAPI 오류: {}", msg);
                return List.of();
            }

            JsonNode articles = root.path("articles");
            List<NewsArticleDto> result = new ArrayList<>();
            for (JsonNode a : articles) {
                String url = a.path("url").asText(null);
                if (url == null || "null".equals(url)) continue;

                NewsArticleDto dto = new NewsArticleDto(
                        url,
                        a.path("title").asText(""),
                        a.path("description").asText(null),
                        a.path("content").asText(null),
                        a.path("urlToImage").asText(null),
                        a.path("source").path("name").asText(null),
                        a.path("source").path("id").asText(null),
                        a.path("author").asText(null),
                        parsePublishedAt(a.path("publishedAt").asText(null))
                );
                result.add(dto);
            }
            log.info("NewsAPI 수집 완료: {}건", result.size());
            return result;
        } catch (Exception e) {
            log.error("NewsAPI 요청 실패", e);
            return List.of();
        }
    }

    private static Instant parsePublishedAt(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Instant.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    public record NewsArticleDto(
            String url,
            String title,
            String description,
            String content,
            String urlToImage,
            String sourceName,
            String sourceId,
            String author,
            Instant publishedAt
    ) {}
}
