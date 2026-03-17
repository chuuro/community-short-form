package com.shortform.backend.dto.response;

import com.shortform.backend.domain.entity.Subtitle;

public class SubtitleResponse {

    private Long id;
    private String originalContent;
    private String content;
    private Double startTime;
    private Double endTime;
    private int orderIndex;
    private String styleJson;
    private boolean isEdited;

    private SubtitleResponse(Builder builder) {
        this.id = builder.id;
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
        private Long id;
        private String originalContent;
        private String content;
        private Double startTime;
        private Double endTime;
        private int orderIndex;
        private String styleJson;
        private boolean isEdited;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder originalContent(String originalContent) { this.originalContent = originalContent; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder startTime(Double startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Double endTime) { this.endTime = endTime; return this; }
        public Builder orderIndex(int orderIndex) { this.orderIndex = orderIndex; return this; }
        public Builder styleJson(String styleJson) { this.styleJson = styleJson; return this; }
        public Builder isEdited(boolean isEdited) { this.isEdited = isEdited; return this; }
        public SubtitleResponse build() { return new SubtitleResponse(this); }
    }

    public Long getId() { return id; }
    public String getOriginalContent() { return originalContent; }
    public String getContent() { return content; }
    public Double getStartTime() { return startTime; }
    public Double getEndTime() { return endTime; }
    public int getOrderIndex() { return orderIndex; }
    public String getStyleJson() { return styleJson; }
    public boolean isEdited() { return isEdited; }

    public static SubtitleResponse from(Subtitle subtitle) {
        return SubtitleResponse.builder()
                .id(subtitle.getId())
                .originalContent(subtitle.getOriginalContent())
                .content(subtitle.getContent())
                .startTime(subtitle.getStartTime())
                .endTime(subtitle.getEndTime())
                .orderIndex(subtitle.getOrderIndex())
                .styleJson(subtitle.getStyleJson())
                .isEdited(subtitle.isEdited())
                .build();
    }
}
