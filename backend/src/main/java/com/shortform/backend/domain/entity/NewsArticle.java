package com.shortform.backend.domain.entity;

import com.shortform.backend.domain.enums.NewsArticleStatus;
import jakarta.persistence.*;

/**
 * NewsAPI에서 수집한 뉴스 기사 및 OpenAI 메타데이터
 */
@Entity
@Table(name = "news_articles", indexes = {
    @Index(name = "idx_news_article_url", columnList = "url", unique = true),
    @Index(name = "idx_news_article_status", columnList = "status"),
    @Index(name = "idx_news_article_created", columnList = "createdAt")
})
public class NewsArticle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── NewsAPI 원본 필드 ─────────────────────────────────────
    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 2048)
    private String urlToImage;

    @Column(length = 100)
    private String sourceName;

    @Column(length = 100)
    private String sourceId;

    @Column(length = 200)
    private String author;

    private java.time.Instant publishedAt;

    // ─── OpenAI 메타데이터 (METADATA_READY 이후 채워짐) ───────
    /** 숏폼 대본 (줄바꿈 구분) */
    @Column(columnDefinition = "TEXT")
    private String script;

    /** 한글 번역된 제목 */
    @Column(length = 500)
    private String translatedTitle;

    /** 한글 번역된 본문 요약 */
    @Column(columnDefinition = "TEXT")
    private String translatedContent;

    /** 썸네일 검색 키워드 (JSON 배열 문자열) */
    @Column(length = 1000)
    private String thumbnailKeywords;

    /** 이미지 검색 키워드 (JSON 배열, script 구간별) */
    @Column(columnDefinition = "TEXT")
    private String imageSearchKeywords;

    /** 동영상 검색 키워드 (JSON 배열, script 구간별) */
    @Column(columnDefinition = "TEXT")
    private String videoSearchKeywords;

    /** 예상 영상 길이 (초) */
    private Double estimatedDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsArticleStatus status;

    @Column(length = 1000)
    private String errorMessage;

    /** News → Project 변환 시 생성된 프로젝트 ID */
    private Long projectId;

    protected NewsArticle() {}

    private NewsArticle(Builder builder) {
        this.url = builder.url;
        this.title = builder.title;
        this.description = builder.description;
        this.content = builder.content;
        this.urlToImage = builder.urlToImage;
        this.sourceName = builder.sourceName;
        this.sourceId = builder.sourceId;
        this.author = builder.author;
        this.publishedAt = builder.publishedAt;
        this.script = builder.script;
        this.translatedTitle = builder.translatedTitle;
        this.translatedContent = builder.translatedContent;
        this.thumbnailKeywords = builder.thumbnailKeywords;
        this.imageSearchKeywords = builder.imageSearchKeywords;
        this.videoSearchKeywords = builder.videoSearchKeywords;
        this.estimatedDurationSeconds = builder.estimatedDurationSeconds;
        this.status = builder.status;
        this.errorMessage = builder.errorMessage;
        this.projectId = builder.projectId;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String url;
        private String title;
        private String description;
        private String content;
        private String urlToImage;
        private String sourceName;
        private String sourceId;
        private String author;
        private java.time.Instant publishedAt;
        private String script;
        private String translatedTitle;
        private String translatedContent;
        private String thumbnailKeywords;
        private String imageSearchKeywords;
        private String videoSearchKeywords;
        private Double estimatedDurationSeconds;
        private NewsArticleStatus status = NewsArticleStatus.FETCHED;
        private String errorMessage;
        private Long projectId;

        public Builder url(String url) { this.url = url; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder urlToImage(String urlToImage) { this.urlToImage = urlToImage; return this; }
        public Builder sourceName(String sourceName) { this.sourceName = sourceName; return this; }
        public Builder sourceId(String sourceId) { this.sourceId = sourceId; return this; }
        public Builder author(String author) { this.author = author; return this; }
        public Builder publishedAt(java.time.Instant publishedAt) { this.publishedAt = publishedAt; return this; }
        public Builder script(String script) { this.script = script; return this; }
        public Builder translatedTitle(String translatedTitle) { this.translatedTitle = translatedTitle; return this; }
        public Builder translatedContent(String translatedContent) { this.translatedContent = translatedContent; return this; }
        public Builder thumbnailKeywords(String thumbnailKeywords) { this.thumbnailKeywords = thumbnailKeywords; return this; }
        public Builder imageSearchKeywords(String imageSearchKeywords) { this.imageSearchKeywords = imageSearchKeywords; return this; }
        public Builder videoSearchKeywords(String videoSearchKeywords) { this.videoSearchKeywords = videoSearchKeywords; return this; }
        public Builder estimatedDurationSeconds(Double estimatedDurationSeconds) { this.estimatedDurationSeconds = estimatedDurationSeconds; return this; }
        public Builder status(NewsArticleStatus status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder projectId(Long projectId) { this.projectId = projectId; return this; }
        public NewsArticle build() { return new NewsArticle(this); }
    }

    // Getters
    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getContent() { return content; }
    public String getUrlToImage() { return urlToImage; }
    public String getSourceName() { return sourceName; }
    public String getSourceId() { return sourceId; }
    public String getAuthor() { return author; }
    public java.time.Instant getPublishedAt() { return publishedAt; }
    public String getScript() { return script; }
    public String getTranslatedTitle() { return translatedTitle; }
    public String getTranslatedContent() { return translatedContent; }
    public String getThumbnailKeywords() { return thumbnailKeywords; }
    public String getImageSearchKeywords() { return imageSearchKeywords; }
    public String getVideoSearchKeywords() { return videoSearchKeywords; }
    public Double getEstimatedDurationSeconds() { return estimatedDurationSeconds; }
    public NewsArticleStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Long getProjectId() { return projectId; }

    public void updateMetadata(String script, String translatedTitle, String translatedContent,
                               String thumbnailKeywords, String imageSearchKeywords, String videoSearchKeywords,
                               Double estimatedDurationSeconds) {
        this.script = script;
        this.translatedTitle = translatedTitle;
        this.translatedContent = translatedContent;
        this.thumbnailKeywords = thumbnailKeywords;
        this.imageSearchKeywords = imageSearchKeywords;
        this.videoSearchKeywords = videoSearchKeywords;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
    }

    public void updateStatus(NewsArticleStatus status) {
        this.status = status;
    }

    public void updateStatus(NewsArticleStatus status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public void updateKeywords(String imageSearchKeywords, String videoSearchKeywords) {
        this.imageSearchKeywords = imageSearchKeywords;
        this.videoSearchKeywords = videoSearchKeywords;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
