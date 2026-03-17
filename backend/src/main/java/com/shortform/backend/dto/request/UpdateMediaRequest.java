package com.shortform.backend.dto.request;

import java.util.List;

public class UpdateMediaRequest {

    // 타임라인 순서 및 노출 시간 일괄 업데이트
    private List<MediaItemUpdate> items;

    public UpdateMediaRequest() {}

    public List<MediaItemUpdate> getItems() { return items; }
    public void setItems(List<MediaItemUpdate> items) { this.items = items; }

    public static class MediaItemUpdate {
        private Long mediaItemId;
        private Integer orderIndex;
        private Double exposureStartTime;
        private Double exposureEndTime;
        private Boolean isIncluded;

        public MediaItemUpdate() {}

        public Long getMediaItemId() { return mediaItemId; }
        public void setMediaItemId(Long mediaItemId) { this.mediaItemId = mediaItemId; }

        public Integer getOrderIndex() { return orderIndex; }
        public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

        public Double getExposureStartTime() { return exposureStartTime; }
        public void setExposureStartTime(Double exposureStartTime) { this.exposureStartTime = exposureStartTime; }

        public Double getExposureEndTime() { return exposureEndTime; }
        public void setExposureEndTime(Double exposureEndTime) { this.exposureEndTime = exposureEndTime; }

        public Boolean getIsIncluded() { return isIncluded; }
        public void setIsIncluded(Boolean isIncluded) { this.isIncluded = isIncluded; }
    }
}
