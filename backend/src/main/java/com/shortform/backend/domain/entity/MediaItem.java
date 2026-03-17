package com.shortform.backend.domain.entity;

import com.shortform.backend.domain.enums.MediaType;
import jakarta.persistence.*;

@Entity
@Table(name = "media_items")
public class MediaItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    @Column(length = 2048)
    private String sourceUrl;

    @Column(length = 1000)
    private String localPath;

    private Integer width;
    private Integer height;
    private Double durationSeconds;
    private Long fileSizeBytes;

    private boolean isLowQuality;

    @Column(nullable = false)
    private Integer orderIndex;

    private Double exposureStartTime;
    private Double exposureEndTime;

    @Column(nullable = false)
    private boolean isIncluded;

    private boolean isPopularComment;

    @Column(length = 2000)
    private String altText;

    protected MediaItem() {}

    private MediaItem(Builder builder) {
        this.project = builder.project;
        this.mediaType = builder.mediaType;
        this.sourceUrl = builder.sourceUrl;
        this.localPath = builder.localPath;
        this.width = builder.width;
        this.height = builder.height;
        this.durationSeconds = builder.durationSeconds;
        this.fileSizeBytes = builder.fileSizeBytes;
        this.isLowQuality = builder.isLowQuality;
        this.orderIndex = builder.orderIndex;
        this.exposureStartTime = builder.exposureStartTime;
        this.exposureEndTime = builder.exposureEndTime;
        this.isIncluded = builder.isIncluded;
        this.isPopularComment = builder.isPopularComment;
        this.altText = builder.altText;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Project project;
        private MediaType mediaType;
        private String sourceUrl;
        private String localPath;
        private Integer width;
        private Integer height;
        private Double durationSeconds;
        private Long fileSizeBytes;
        private boolean isLowQuality = false;
        private Integer orderIndex = 0;
        private Double exposureStartTime;
        private Double exposureEndTime;
        private boolean isIncluded = true;
        private boolean isPopularComment = false;
        private String altText;

        public Builder project(Project project) { this.project = project; return this; }
        public Builder mediaType(MediaType mediaType) { this.mediaType = mediaType; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder localPath(String localPath) { this.localPath = localPath; return this; }
        public Builder width(Integer width) { this.width = width; return this; }
        public Builder height(Integer height) { this.height = height; return this; }
        public Builder durationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; return this; }
        public Builder fileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; return this; }
        public Builder isLowQuality(boolean isLowQuality) { this.isLowQuality = isLowQuality; return this; }
        public Builder orderIndex(Integer orderIndex) { this.orderIndex = orderIndex; return this; }
        public Builder exposureStartTime(Double exposureStartTime) { this.exposureStartTime = exposureStartTime; return this; }
        public Builder exposureEndTime(Double exposureEndTime) { this.exposureEndTime = exposureEndTime; return this; }
        public Builder isIncluded(boolean isIncluded) { this.isIncluded = isIncluded; return this; }
        public Builder isPopularComment(boolean isPopularComment) { this.isPopularComment = isPopularComment; return this; }
        public Builder altText(String altText) { this.altText = altText; return this; }
        public MediaItem build() { return new MediaItem(this); }
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public MediaType getMediaType() { return mediaType; }
    public String getSourceUrl() { return sourceUrl; }
    public String getLocalPath() { return localPath; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Double getDurationSeconds() { return durationSeconds; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public boolean isLowQuality() { return isLowQuality; }
    public Integer getOrderIndex() { return orderIndex; }
    public Double getExposureStartTime() { return exposureStartTime; }
    public Double getExposureEndTime() { return exposureEndTime; }
    public boolean isIncluded() { return isIncluded; }
    public boolean isPopularComment() { return isPopularComment; }
    public String getAltText() { return altText; }

    public void updateOrder(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public void updateExposureTime(Double startTime, Double endTime) {
        this.exposureStartTime = startTime;
        this.exposureEndTime = endTime;
    }

    public void toggleIncluded() {
        this.isIncluded = !this.isIncluded;
    }

    public void updateLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public void markAsLowQuality() {
        this.isLowQuality = true;
    }
}
