package com.shortform.backend.domain.entity;

import com.shortform.backend.domain.enums.MediaType;
import jakarta.persistence.*;

/**
 * NewsArticle에 연결된 Pexels 검색 결과 (이미지/영상)
 * - script 구간별 orderIndex로 매핑
 * - 사용자가 선택한 항목만 isSelected=true
 */
@Entity
@Table(name = "news_article_media", indexes = {
    @Index(name = "idx_news_article_media_article", columnList = "news_article_id"),
    @Index(name = "idx_news_article_media_order", columnList = "news_article_id, order_index")
})
public class NewsArticleMedia extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_article_id", nullable = false)
    private NewsArticle newsArticle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    /** Pexels 원본 URL (이미지: src.medium, 영상: video_files link) */
    @Column(nullable = false, length = 2048)
    private String sourceUrl;

    @Column(length = 1000)
    private String localPath;

    private Integer width;
    private Integer height;
    private Double durationSeconds;

    @Column(length = 2048)
    private String thumbnailUrl;

    /** script 구간 순서 (0-based) */
    @Column(nullable = false)
    private Integer orderIndex;

    /** 사용자가 최종 선택한 항목 (렌더 시 사용) */
    @Column(nullable = false)
    private boolean isSelected = true;

    @Column(length = 500)
    private String searchKeyword;

    /** Pexels attribution */
    @Column(length = 500)
    private String photographerName;

    @Column(length = 2048)
    private String photographerUrl;

    /** 노출 시간(초). null이면 기본값 사용 (VIDEO: full, IMAGE: 4.0) */
    private Double exposureDurationSeconds;

    protected NewsArticleMedia() {}

    private NewsArticleMedia(Builder builder) {
        this.newsArticle = builder.newsArticle;
        this.mediaType = builder.mediaType;
        this.sourceUrl = builder.sourceUrl;
        this.localPath = builder.localPath;
        this.width = builder.width;
        this.height = builder.height;
        this.durationSeconds = builder.durationSeconds;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.orderIndex = builder.orderIndex;
        this.isSelected = builder.isSelected;
        this.searchKeyword = builder.searchKeyword;
        this.photographerName = builder.photographerName;
        this.photographerUrl = builder.photographerUrl;
        this.exposureDurationSeconds = builder.exposureDurationSeconds;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private NewsArticle newsArticle;
        private MediaType mediaType;
        private String sourceUrl;
        private String localPath;
        private Integer width;
        private Integer height;
        private Double durationSeconds;
        private String thumbnailUrl;
        private Integer orderIndex = 0;
        private boolean isSelected = true;
        private String searchKeyword;
        private String photographerName;
        private String photographerUrl;
        private Double exposureDurationSeconds;

        public Builder newsArticle(NewsArticle newsArticle) { this.newsArticle = newsArticle; return this; }
        public Builder mediaType(MediaType mediaType) { this.mediaType = mediaType; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder localPath(String localPath) { this.localPath = localPath; return this; }
        public Builder width(Integer width) { this.width = width; return this; }
        public Builder height(Integer height) { this.height = height; return this; }
        public Builder durationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; return this; }
        public Builder thumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public Builder orderIndex(Integer orderIndex) { this.orderIndex = orderIndex; return this; }
        public Builder isSelected(boolean isSelected) { this.isSelected = isSelected; return this; }
        public Builder searchKeyword(String searchKeyword) { this.searchKeyword = searchKeyword; return this; }
        public Builder photographerName(String photographerName) { this.photographerName = photographerName; return this; }
        public Builder photographerUrl(String photographerUrl) { this.photographerUrl = photographerUrl; return this; }
        public Builder exposureDurationSeconds(Double exposureDurationSeconds) { this.exposureDurationSeconds = exposureDurationSeconds; return this; }
        public NewsArticleMedia build() { return new NewsArticleMedia(this); }
    }

    public Long getId() { return id; }
    public NewsArticle getNewsArticle() { return newsArticle; }
    public MediaType getMediaType() { return mediaType; }
    public String getSourceUrl() { return sourceUrl; }
    public String getLocalPath() { return localPath; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Double getDurationSeconds() { return durationSeconds; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Integer getOrderIndex() { return orderIndex; }
    public boolean isSelected() { return isSelected; }
    public String getSearchKeyword() { return searchKeyword; }
    public String getPhotographerName() { return photographerName; }
    public String getPhotographerUrl() { return photographerUrl; }
    public Double getExposureDurationSeconds() { return exposureDurationSeconds; }

    public void setSelected(boolean selected) { this.isSelected = selected; }
    public void setExposureDurationSeconds(Double exposureDurationSeconds) { this.exposureDurationSeconds = exposureDurationSeconds; }
}
