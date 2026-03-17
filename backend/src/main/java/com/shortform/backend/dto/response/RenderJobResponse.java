package com.shortform.backend.dto.response;

import com.shortform.backend.domain.entity.RenderJob;
import com.shortform.backend.domain.enums.RenderStatus;

import java.time.LocalDateTime;

public class RenderJobResponse {

    private Long id;
    private Long projectId;
    private RenderStatus status;
    private boolean isPreview;
    private int progress;
    private String outputFilePath;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private RenderJobResponse(Builder builder) {
        this.id = builder.id;
        this.projectId = builder.projectId;
        this.status = builder.status;
        this.isPreview = builder.isPreview;
        this.progress = builder.progress;
        this.outputFilePath = builder.outputFilePath;
        this.errorMessage = builder.errorMessage;
        this.retryCount = builder.retryCount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private Long projectId;
        private RenderStatus status;
        private boolean isPreview;
        private int progress;
        private String outputFilePath;
        private String errorMessage;
        private int retryCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder projectId(Long projectId) { this.projectId = projectId; return this; }
        public Builder status(RenderStatus status) { this.status = status; return this; }
        public Builder isPreview(boolean isPreview) { this.isPreview = isPreview; return this; }
        public Builder progress(int progress) { this.progress = progress; return this; }
        public Builder outputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public RenderJobResponse build() { return new RenderJobResponse(this); }
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public RenderStatus getStatus() { return status; }
    public boolean isPreview() { return isPreview; }
    public int getProgress() { return progress; }
    public String getOutputFilePath() { return outputFilePath; }
    public String getErrorMessage() { return errorMessage; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static RenderJobResponse from(RenderJob job) {
        return RenderJobResponse.builder()
                .id(job.getId())
                .projectId(job.getProject().getId())
                .status(job.getStatus())
                .isPreview(job.isPreview())
                .progress(job.getProgress())
                .outputFilePath(job.getOutputFilePath())
                .errorMessage(job.getErrorMessage())
                .retryCount(job.getRetryCount())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
