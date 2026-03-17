package com.shortform.backend.dto.response;

import java.util.List;

public class ParseResultResponse {

    private Long projectId;
    private String title;
    private int videoCount;
    private int imageCount;
    private int popularCommentCount;
    private int lowQualityCount;
    private List<MediaItemResponse> mediaItems;
    private List<SubtitleResponse> subtitles;
    private List<String> warnings;

    private ParseResultResponse(Builder builder) {
        this.projectId = builder.projectId;
        this.title = builder.title;
        this.videoCount = builder.videoCount;
        this.imageCount = builder.imageCount;
        this.popularCommentCount = builder.popularCommentCount;
        this.lowQualityCount = builder.lowQualityCount;
        this.mediaItems = builder.mediaItems;
        this.subtitles = builder.subtitles;
        this.warnings = builder.warnings;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long projectId;
        private String title;
        private int videoCount;
        private int imageCount;
        private int popularCommentCount;
        private int lowQualityCount;
        private List<MediaItemResponse> mediaItems;
        private List<SubtitleResponse> subtitles;
        private List<String> warnings;

        public Builder projectId(Long projectId) { this.projectId = projectId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder videoCount(int videoCount) { this.videoCount = videoCount; return this; }
        public Builder imageCount(int imageCount) { this.imageCount = imageCount; return this; }
        public Builder popularCommentCount(int popularCommentCount) { this.popularCommentCount = popularCommentCount; return this; }
        public Builder lowQualityCount(int lowQualityCount) { this.lowQualityCount = lowQualityCount; return this; }
        public Builder mediaItems(List<MediaItemResponse> mediaItems) { this.mediaItems = mediaItems; return this; }
        public Builder subtitles(List<SubtitleResponse> subtitles) { this.subtitles = subtitles; return this; }
        public Builder warnings(List<String> warnings) { this.warnings = warnings; return this; }
        public ParseResultResponse build() { return new ParseResultResponse(this); }
    }

    public Long getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public int getVideoCount() { return videoCount; }
    public int getImageCount() { return imageCount; }
    public int getPopularCommentCount() { return popularCommentCount; }
    public int getLowQualityCount() { return lowQualityCount; }
    public List<MediaItemResponse> getMediaItems() { return mediaItems; }
    public List<SubtitleResponse> getSubtitles() { return subtitles; }
    public List<String> getWarnings() { return warnings; }
}
