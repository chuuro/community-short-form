package com.shortform.backend.domain.entity;

import com.shortform.backend.domain.enums.RenderStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "render_jobs")
public class RenderJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RenderStatus status;

    // 미리보기 렌더 여부
    private boolean isPreview;

    // 진행률 0~100
    private Integer progress;

    @Column(length = 1000)
    private String outputFilePath;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // 최대 3회 재시도
    private Integer retryCount;

    // Worker에서 처리 중인 메시지 ID
    @Column(length = 200)
    private String workerJobId;

    protected RenderJob() {}

    private RenderJob(Builder builder) {
        this.project = builder.project;
        this.status = builder.status;
        this.isPreview = builder.isPreview;
        this.progress = builder.progress;
        this.outputFilePath = builder.outputFilePath;
        this.errorMessage = builder.errorMessage;
        this.retryCount = builder.retryCount;
        this.workerJobId = builder.workerJobId;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Project project;
        private RenderStatus status = RenderStatus.PENDING;
        private boolean isPreview = false;
        private Integer progress = 0;
        private String outputFilePath;
        private String errorMessage;
        private Integer retryCount = 0;
        private String workerJobId;

        public Builder project(Project project) { this.project = project; return this; }
        public Builder status(RenderStatus status) { this.status = status; return this; }
        public Builder isPreview(boolean isPreview) { this.isPreview = isPreview; return this; }
        public Builder progress(Integer progress) { this.progress = progress; return this; }
        public Builder outputFilePath(String outputFilePath) { this.outputFilePath = outputFilePath; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder retryCount(Integer retryCount) { this.retryCount = retryCount; return this; }
        public Builder workerJobId(String workerJobId) { this.workerJobId = workerJobId; return this; }
        public RenderJob build() { return new RenderJob(this); }
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public RenderStatus getStatus() { return status; }
    public boolean isPreview() { return isPreview; }
    public Integer getProgress() { return progress; }
    public String getOutputFilePath() { return outputFilePath; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getRetryCount() { return retryCount; }
    public String getWorkerJobId() { return workerJobId; }

    public void updateStatus(RenderStatus status) {
        this.status = status;
    }

    public void updateProgress(int progress) {
        this.progress = progress;
    }

    public void complete(String outputFilePath) {
        this.status = RenderStatus.COMPLETED;
        this.progress = 100;
        this.outputFilePath = outputFilePath;
    }

    public void fail(String errorMessage) {
        this.status = RenderStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void incrementRetry() {
        this.retryCount++;
        this.status = RenderStatus.RETRY;
    }

    public void assignWorkerJobId(String workerJobId) {
        this.workerJobId = workerJobId;
    }

    public boolean canRetry() {
        return this.retryCount < 3;
    }
}
