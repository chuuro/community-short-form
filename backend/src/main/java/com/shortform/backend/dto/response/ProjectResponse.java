package com.shortform.backend.dto.response;

import com.shortform.backend.domain.entity.Project;
import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.OutputPlatform;
import com.shortform.backend.domain.enums.ProjectStatus;

import java.time.LocalDateTime;

public class ProjectResponse {

    private Long id;
    private String communityUrl;
    private CommunityType communityType;
    private String title;
    private String description;
    private ProjectStatus status;
    private OutputPlatform outputPlatform;
    private String thumbnailUrl;
    private String outputFilePath;
    private String previewFilePath;
    private BgmTrackResponse bgmTrack;
    private int mediaItemCount;
    private int subtitleCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ProjectResponse(Builder builder) {
        this.id = builder.id;
        this.communityUrl = builder.communityUrl;
        this.communityType = builder.communityType;
        this.title = builder.title;
        this.description = builder.description;
        this.status = builder.status;
        this.outputPlatform = builder.outputPlatform;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.outputFilePath = builder.outputFilePath;
        this.previewFilePath = builder.previewFilePath;
        this.bgmTrack = builder.bgmTrack;
        this.mediaItemCount = builder.mediaItemCount;
        this.subtitleCount = builder.subtitleCount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String communityUrl;
        private CommunityType communityType;
        private String title;
        private String description;
        private ProjectStatus status;
        private OutputPlatform outputPlatform;
        private String thumbnailUrl;
        private String outputFilePath;
        private String previewFilePath;
        private BgmTrackResponse bgmTrack;
        private int mediaItemCount;
        private int subtitleCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder communityUrl(String communityUrl) { this.communityUrl = communityUrl; return this; }
        public Builder communityType(CommunityType communityType) { this.communityType = communityType; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder status(ProjectStatus status) { this.status = status; return this; }
        public Builder outputPlatform(OutputPlatform outputPlatform) { this.outputPlatform = outputPlatform; return this; }
        public Builder thumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public Builder outputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; return this; }
        public Builder previewFilePath(String previewFilePath) { this.previewFilePath = previewFilePath; return this; }
        public Builder bgmTrack(BgmTrackResponse bgmTrack) { this.bgmTrack = bgmTrack; return this; }
        public Builder mediaItemCount(int mediaItemCount) { this.mediaItemCount = mediaItemCount; return this; }
        public Builder subtitleCount(int subtitleCount) { this.subtitleCount = subtitleCount; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public ProjectResponse build() { return new ProjectResponse(this); }
    }

    public Long getId() { return id; }
    public String getCommunityUrl() { return communityUrl; }
    public CommunityType getCommunityType() { return communityType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }
    public OutputPlatform getOutputPlatform() { return outputPlatform; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getOutputFilePath() { return outputFilePath; }
    public String getPreviewFilePath() { return previewFilePath; }
    public BgmTrackResponse getBgmTrack() { return bgmTrack; }
    public int getMediaItemCount() { return mediaItemCount; }
    public int getSubtitleCount() { return subtitleCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .communityUrl(project.getCommunityUrl())
                .communityType(project.getCommunityType())
                .title(project.getTitle())
                .description(project.getDescription())
                .status(project.getStatus())
                .outputPlatform(project.getOutputPlatform())
                .thumbnailUrl(project.getThumbnailUrl())
                .outputFilePath(project.getOutputFilePath())
                .previewFilePath(project.getPreviewFilePath())
                .bgmTrack(project.getBgmTrack() != null
                        ? BgmTrackResponse.from(project.getBgmTrack()) : null)
                .mediaItemCount(project.getMediaItems().size())
                .subtitleCount(project.getSubtitles().size())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
