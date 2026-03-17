package com.shortform.backend.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "subtitles")
public class Subtitle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // OpenAI가 생성한 원본 대본 (불변 보존용)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalContent;

    // 실제 사용될 자막 (수정 가능)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Double startTime;
    private Double endTime;

    @Column(nullable = false)
    private Integer orderIndex;

    // 자막 스타일 (JSON 문자열로 저장: 폰트, 크기, 색상, 위치)
    @Column(columnDefinition = "TEXT")
    private String styleJson;

    // 사용자가 수정했는지 여부
    private boolean isEdited;

    protected Subtitle() {}

    private Subtitle(Builder builder) {
        this.project = builder.project;
        this.originalContent = builder.originalContent;
        this.content = builder.content;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.orderIndex = builder.orderIndex;
        this.styleJson = builder.styleJson;
        this.isEdited = builder.isEdited;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Project project;
        private String originalContent;
        private String content;
        private Double startTime;
        private Double endTime;
        private Integer orderIndex = 0;
        private String styleJson;
        private boolean isEdited = false;

        public Builder project(Project project) { this.project = project; return this; }
        public Builder originalContent(String originalContent) { this.originalContent = originalContent; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder startTime(Double startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Double endTime) { this.endTime = endTime; return this; }
        public Builder orderIndex(Integer orderIndex) { this.orderIndex = orderIndex; return this; }
        public Builder styleJson(String styleJson) { this.styleJson = styleJson; return this; }
        public Builder isEdited(boolean isEdited) { this.isEdited = isEdited; return this; }
        public Subtitle build() { return new Subtitle(this); }
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getOriginalContent() { return originalContent; }
    public String getContent() { return content; }
    public Double getStartTime() { return startTime; }
    public Double getEndTime() { return endTime; }
    public Integer getOrderIndex() { return orderIndex; }
    public String getStyleJson() { return styleJson; }
    public boolean isEdited() { return isEdited; }

    public void updateContent(String content) {
        this.content = content;
        this.isEdited = true;
    }

    public void updateStyle(String styleJson) {
        this.styleJson = styleJson;
    }

    public void updateTiming(Double startTime, Double endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void resetToOriginal() {
        this.content = this.originalContent;
        this.isEdited = false;
    }
}
