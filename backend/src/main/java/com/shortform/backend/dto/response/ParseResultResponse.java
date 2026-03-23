package com.shortform.backend.dto.response;

import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.OutputPlatform;
import com.shortform.backend.domain.enums.ProjectStatus;

import java.util.List;

public class ParseResultResponse {

    private Long projectId;
    private String status;
    private String communityUrl;
    private String communityType;
    private String outputPlatform;
    private String title;
    private String description;
    private String thumbnailUrl;
    private int videoCount;
    private int imageCount;
    private int textCount;
    private int gifCount;
    private int popularCommentCount;
    private int lowQualityCount;
    private String outputFilePath;
    private List<MediaItemResponse> mediaItems;
    private List<SubtitleResponse> subtitles;
    private List<String> warnings;

    private ParseResultResponse(Builder builder) {
        this.projectId = builder.projectId;
        this.status = builder.status;
        this.communityUrl = builder.communityUrl;
        this.communityType = builder.communityType;
        this.outputPlatform = builder.outputPlatform;
        this.title = builder.title;
        this.description = builder.description;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.videoCount = builder.videoCount;
        this.imageCount = builder.imageCount;
        this.textCount = builder.textCount;
        this.gifCount = builder.gifCount;
        this.popularCommentCount = builder.popularCommentCount;
        this.lowQualityCount = builder.lowQualityCount;
        this.outputFilePath = builder.outputFilePath;
        this.mediaItems = builder.mediaItems;
        this.subtitles = builder.subtitles;
        this.warnings = builder.warnings;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long projectId;
        private String status;
        private String communityUrl;
        private String communityType;
        private String outputPlatform;
        private String title;
        private String description;
        private String thumbnailUrl;
        private int videoCount;
        private int imageCount;
        private int textCount;
        private int gifCount;
        private int popularCommentCount;
        private int lowQualityCount;
        private String outputFilePath;
        private List<MediaItemResponse> mediaItems;
        private List<SubtitleResponse> subtitles;
        private List<String> warnings;

        public Builder projectId(Long projectId) { this.projectId = projectId; return this; }
        public Builder status(ProjectStatus status) { this.status = status != null ? status.name() : null; return this; }
        public Builder communityUrl(String communityUrl) { this.communityUrl = communityUrl; return this; }
        public Builder communityType(CommunityType type) { this.communityType = type != null ? type.name() : null; return this; }
        public Builder outputPlatform(OutputPlatform platform) { this.outputPlatform = platform != null ? platform.name() : null; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder thumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public Builder videoCount(int videoCount) { this.videoCount = videoCount; return this; }
        public Builder imageCount(int imageCount) { this.imageCount = imageCount; return this; }
        public Builder textCount(int textCount) { this.textCount = textCount; return this; }
        public Builder gifCount(int gifCount) { this.gifCount = gifCount; return this; }
        public Builder popularCommentCount(int popularCommentCount) { this.popularCommentCount = popularCommentCount; return this; }
        public Builder lowQualityCount(int lowQualityCount) { this.lowQualityCount = lowQualityCount; return this; }
        public Builder outputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; return this; }
        public Builder mediaItems(List<MediaItemResponse> mediaItems) { this.mediaItems = mediaItems; return this; }
        public Builder subtitles(List<SubtitleResponse> subtitles) { this.subtitles = subtitles; return this; }
        public Builder warnings(List<String> warnings) { this.warnings = warnings; return this; }
        public ParseResultResponse build() { return new ParseResultResponse(this); }
    }

    public Long getProjectId() { return projectId; }
    public String getStatus() { return status; }
    public String getCommunityUrl() { return communityUrl; }
    public String getCommunityType() { return communityType; }
    public String getOutputPlatform() { return outputPlatform; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getVideoCount() { return videoCount; }
    public int getImageCount() { return imageCount; }
    public int getTextCount() { return textCount; }
    public int getGifCount() { return gifCount; }
    public int getPopularCommentCount() { return popularCommentCount; }
    public int getLowQualityCount() { return lowQualityCount; }
    public String getOutputFilePath() { return outputFilePath; }
    public List<MediaItemResponse> getMediaItems() { return mediaItems; }
    public List<SubtitleResponse> getSubtitles() { return subtitles; }
    public List<String> getWarnings() { return warnings; }
}
