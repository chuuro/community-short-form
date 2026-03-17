package com.shortform.backend.dto.request;

import java.util.List;

public class UpdateSubtitleRequest {

    private List<SubtitleUpdate> subtitles;

    public UpdateSubtitleRequest() {}

    public List<SubtitleUpdate> getSubtitles() { return subtitles; }
    public void setSubtitles(List<SubtitleUpdate> subtitles) { this.subtitles = subtitles; }

    public static class SubtitleUpdate {
        private Long subtitleId;
        private String content;
        private Double startTime;
        private Double endTime;
        private String styleJson;

        public SubtitleUpdate() {}

        public Long getSubtitleId() { return subtitleId; }
        public void setSubtitleId(Long subtitleId) { this.subtitleId = subtitleId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Double getStartTime() { return startTime; }
        public void setStartTime(Double startTime) { this.startTime = startTime; }

        public Double getEndTime() { return endTime; }
        public void setEndTime(Double endTime) { this.endTime = endTime; }

        public String getStyleJson() { return styleJson; }
        public void setStyleJson(String styleJson) { this.styleJson = styleJson; }
    }
}
