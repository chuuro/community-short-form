package com.shortform.backend.dto.response;

import com.shortform.backend.domain.entity.BgmTrack;

public class BgmTrackResponse {

    private Long id;
    private String name;
    private String category;
    private String previewUrl;
    private Double durationSeconds;
    private boolean isTrending;

    private BgmTrackResponse(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.category = builder.category;
        this.previewUrl = builder.previewUrl;
        this.durationSeconds = builder.durationSeconds;
        this.isTrending = builder.isTrending;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String name;
        private String category;
        private String previewUrl;
        private Double durationSeconds;
        private boolean isTrending;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder previewUrl(String previewUrl) { this.previewUrl = previewUrl; return this; }
        public Builder durationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; return this; }
        public Builder isTrending(boolean isTrending) { this.isTrending = isTrending; return this; }
        public BgmTrackResponse build() { return new BgmTrackResponse(this); }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getPreviewUrl() { return previewUrl; }
    public Double getDurationSeconds() { return durationSeconds; }
    public boolean isTrending() { return isTrending; }

    public static BgmTrackResponse from(BgmTrack bgmTrack) {
        return BgmTrackResponse.builder()
                .id(bgmTrack.getId())
                .name(bgmTrack.getName())
                .category(bgmTrack.getCategory())
                .previewUrl(bgmTrack.getPreviewUrl())
                .durationSeconds(bgmTrack.getDurationSeconds())
                .isTrending(bgmTrack.isTrending())
                .build();
    }
}
