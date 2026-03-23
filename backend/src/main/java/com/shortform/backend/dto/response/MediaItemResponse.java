package com.shortform.backend.dto.response;

import com.shortform.backend.domain.entity.MediaItem;
import com.shortform.backend.domain.enums.MediaType;

import java.time.LocalDateTime;

public class MediaItemResponse {

    private Long id;
    private MediaType mediaType;
    private String sourceUrl;
    private String localPath;
    private Integer width;
    private Integer height;
    private Double durationSeconds;
    private Long fileSizeBytes;
    private boolean lowQuality;
    private boolean gif;
    private String thumbnailUrl;
    private int orderIndex;
    private Double exposureStartTime;
    private Double exposureEndTime;
    private boolean included;
    private boolean popularComment;
    private String altText;
    private LocalDateTime createdAt;

    private MediaItemResponse(Builder builder) {
        this.id = builder.id;
        this.mediaType = builder.mediaType;
        this.sourceUrl = builder.sourceUrl;
        this.localPath = builder.localPath;
        this.width = builder.width;
        this.height = builder.height;
        this.durationSeconds = builder.durationSeconds;
        this.fileSizeBytes = builder.fileSizeBytes;
        this.lowQuality = builder.lowQuality;
        this.gif = builder.gif;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.orderIndex = builder.orderIndex;
        this.exposureStartTime = builder.exposureStartTime;
        this.exposureEndTime = builder.exposureEndTime;
        this.included = builder.included;
        this.popularComment = builder.popularComment;
        this.altText = builder.altText;
        this.createdAt = builder.createdAt;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private MediaType mediaType;
        private String sourceUrl;
        private String localPath;
        private Integer width;
        private Integer height;
        private Double durationSeconds;
        private Long fileSizeBytes;
        private boolean lowQuality;
        private boolean gif;
        private String thumbnailUrl;
        private int orderIndex;
        private Double exposureStartTime;
        private Double exposureEndTime;
        private boolean included;
        private boolean popularComment;
        private String altText;
        private LocalDateTime createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder mediaType(MediaType mediaType) { this.mediaType = mediaType; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder localPath(String localPath) { this.localPath = localPath; return this; }
        public Builder width(Integer width) { this.width = width; return this; }
        public Builder height(Integer height) { this.height = height; return this; }
        public Builder durationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; return this; }
        public Builder fileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; return this; }
        public Builder lowQuality(boolean lowQuality) { this.lowQuality = lowQuality; return this; }
        public Builder gif(boolean gif) { this.gif = gif; return this; }
        public Builder thumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public Builder orderIndex(int orderIndex) { this.orderIndex = orderIndex; return this; }
        public Builder exposureStartTime(Double exposureStartTime) { this.exposureStartTime = exposureStartTime; return this; }
        public Builder exposureEndTime(Double exposureEndTime) { this.exposureEndTime = exposureEndTime; return this; }
        public Builder included(boolean included) { this.included = included; return this; }
        public Builder popularComment(boolean popularComment) { this.popularComment = popularComment; return this; }
        public Builder altText(String altText) { this.altText = altText; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public MediaItemResponse build() { return new MediaItemResponse(this); }
    }

    public Long getId() { return id; }
    public MediaType getMediaType() { return mediaType; }
    public String getSourceUrl() { return sourceUrl; }
    public String getLocalPath() { return localPath; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Double getDurationSeconds() { return durationSeconds; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public boolean isLowQuality() { return lowQuality; }
    public boolean isGif() { return gif; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getOrderIndex() { return orderIndex; }
    public Double getExposureStartTime() { return exposureStartTime; }
    public Double getExposureEndTime() { return exposureEndTime; }
    public boolean isIncluded() { return included; }
    public boolean isPopularComment() { return popularComment; }
    public String getAltText() { return altText; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public static MediaItemResponse from(MediaItem item) {
        return MediaItemResponse.builder()
                .id(item.getId())
                .mediaType(item.getMediaType())
                .sourceUrl(item.getSourceUrl())
                .localPath(item.getLocalPath())
                .width(item.getWidth())
                .height(item.getHeight())
                .durationSeconds(item.getDurationSeconds())
                .fileSizeBytes(item.getFileSizeBytes())
                .lowQuality(item.isLowQuality())
                .gif(item.isGif())
                .thumbnailUrl(item.getThumbnailUrl())
                .orderIndex(item.getOrderIndex())
                .exposureStartTime(item.getExposureStartTime())
                .exposureEndTime(item.getExposureEndTime())
                .included(item.isIncluded())
                .popularComment(item.isPopularComment())
                .altText(item.getAltText())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
