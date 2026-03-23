package com.shortform.backend.dto.request;

import java.util.List;

public class UpdateMediaExposureRequest {
    private List<MediaExposureItem> items;

    public List<MediaExposureItem> getItems() { return items; }
    public void setItems(List<MediaExposureItem> items) { this.items = items; }

    public static class MediaExposureItem {
        private Long mediaId;
        private Double exposureDurationSeconds;

        public Long getMediaId() { return mediaId; }
        public void setMediaId(Long mediaId) { this.mediaId = mediaId; }
        public Double getExposureDurationSeconds() { return exposureDurationSeconds; }
        public void setExposureDurationSeconds(Double exposureDurationSeconds) { this.exposureDurationSeconds = exposureDurationSeconds; }
    }
}
