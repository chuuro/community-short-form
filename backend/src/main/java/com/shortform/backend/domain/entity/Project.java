package com.shortform.backend.domain.entity;

import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.domain.enums.OutputPlatform;
import com.shortform.backend.domain.enums.ProjectStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String communityUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommunityType communityType;

    @Column(length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    private OutputPlatform outputPlatform;

    @Column(length = 500)
    private String thumbnailUrl;

    @Column(length = 1000)
    private String outputFilePath;

    @Column(length = 1000)
    private String previewFilePath;

    // Soft Delete
    @Column(nullable = false)
    private boolean isDeleted;

    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MediaItem> mediaItems;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Subtitle> subtitles;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RenderJob> renderJobs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bgm_track_id")
    private BgmTrack bgmTrack;

    protected Project() {}

    private Project(Builder builder) {
        this.communityUrl = builder.communityUrl;
        this.communityType = builder.communityType;
        this.title = builder.title;
        this.description = builder.description;
        this.status = builder.status;
        this.outputPlatform = builder.outputPlatform;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.outputFilePath = builder.outputFilePath;
        this.previewFilePath = builder.previewFilePath;
        this.isDeleted = builder.isDeleted;
        this.deletedAt = builder.deletedAt;
        this.mediaItems = builder.mediaItems;
        this.subtitles = builder.subtitles;
        this.renderJobs = builder.renderJobs;
        this.bgmTrack = builder.bgmTrack;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String communityUrl;
        private CommunityType communityType;
        private String title;
        private String description;
        private ProjectStatus status = ProjectStatus.CREATED;
        private OutputPlatform outputPlatform = OutputPlatform.YOUTUBE_SHORTS;
        private String thumbnailUrl;
        private String outputFilePath;
        private String previewFilePath;
        private boolean isDeleted = false;
        private LocalDateTime deletedAt;
        private List<MediaItem> mediaItems = new ArrayList<>();
        private List<Subtitle> subtitles = new ArrayList<>();
        private List<RenderJob> renderJobs = new ArrayList<>();
        private BgmTrack bgmTrack;

        public Builder communityUrl(String communityUrl) { this.communityUrl = communityUrl; return this; }
        public Builder communityType(CommunityType communityType) { this.communityType = communityType; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder status(ProjectStatus status) { this.status = status; return this; }
        public Builder outputPlatform(OutputPlatform outputPlatform) { this.outputPlatform = outputPlatform; return this; }
        public Builder thumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public Builder outputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; return this; }
        public Builder previewFilePath(String previewFilePath) { this.previewFilePath = previewFilePath; return this; }
        public Builder isDeleted(boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public Builder deletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; return this; }
        public Builder mediaItems(List<MediaItem> mediaItems) { this.mediaItems = mediaItems; return this; }
        public Builder subtitles(List<Subtitle> subtitles) { this.subtitles = subtitles; return this; }
        public Builder renderJobs(List<RenderJob> renderJobs) { this.renderJobs = renderJobs; return this; }
        public Builder bgmTrack(BgmTrack bgmTrack) { this.bgmTrack = bgmTrack; return this; }
        public Project build() { return new Project(this); }
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
    public boolean isDeleted() { return isDeleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public List<MediaItem> getMediaItems() { return mediaItems; }
    public List<Subtitle> getSubtitles() { return subtitles; }
    public List<RenderJob> getRenderJobs() { return renderJobs; }
    public BgmTrack getBgmTrack() { return bgmTrack; }

    public void updateStatus(ProjectStatus status) {
        this.status = status;
    }

    public void updateOutputPlatform(OutputPlatform platform) {
        this.outputPlatform = platform;
    }

    public void updateBgmTrack(BgmTrack bgmTrack) {
        this.bgmTrack = bgmTrack;
    }

    public void updateOutputFilePath(String path) {
        this.outputFilePath = path;
    }

    public void updatePreviewFilePath(String path) {
        this.previewFilePath = path;
    }

    public void updateMetadata(String title, String description, String thumbnailUrl) {
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.status = ProjectStatus.FAILED;
    }
}
