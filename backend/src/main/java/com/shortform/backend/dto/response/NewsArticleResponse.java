package com.shortform.backend.dto.response;

import com.shortform.backend.domain.entity.NewsArticle;
import com.shortform.backend.domain.enums.NewsArticleStatus;

import java.time.Instant;

public class NewsArticleResponse {

    private Long id;
    private String url;
    private String title;
    private String description;
    private String content;
    private String urlToImage;
    private String sourceName;
    private String author;
    private Instant publishedAt;

    private String script;
    private String translatedTitle;
    private String translatedContent;
    private String thumbnailKeywords;
    private String imageSearchKeywords;
    private String videoSearchKeywords;
    private Double estimatedDurationSeconds;

    private NewsArticleStatus status;
    private String errorMessage;
    private Long projectId;

    public static NewsArticleResponse from(NewsArticle article) {
        NewsArticleResponse r = new NewsArticleResponse();
        r.id = article.getId();
        r.url = article.getUrl();
        r.title = article.getTitle();
        r.description = article.getDescription();
        r.content = article.getContent();
        r.urlToImage = article.getUrlToImage();
        r.sourceName = article.getSourceName();
        r.author = article.getAuthor();
        r.publishedAt = article.getPublishedAt();

        r.script = article.getScript();
        r.translatedTitle = article.getTranslatedTitle();
        r.translatedContent = article.getTranslatedContent();
        r.thumbnailKeywords = article.getThumbnailKeywords();
        r.imageSearchKeywords = article.getImageSearchKeywords();
        r.videoSearchKeywords = article.getVideoSearchKeywords();
        r.estimatedDurationSeconds = article.getEstimatedDurationSeconds();

        r.status = article.getStatus();
        r.errorMessage = article.getErrorMessage();
        r.projectId = article.getProjectId();
        return r;
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getContent() { return content; }
    public String getUrlToImage() { return urlToImage; }
    public String getSourceName() { return sourceName; }
    public String getAuthor() { return author; }
    public Instant getPublishedAt() { return publishedAt; }

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
}
