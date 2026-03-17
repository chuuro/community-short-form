package com.shortform.backend.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bgm_tracks")
public class BgmTrack extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    // BGM 카테고리 (예: 신나는, 감성적, 웅장한, 코믹, 잔잔한)
    @Column(length = 100)
    private String category;

    @Column(nullable = false, length = 1000)
    private String filePath;

    @Column(length = 1000)
    private String previewUrl;

    private Double durationSeconds;

    // 숏폼에서 자주 사용되는 유명 BGM 여부
    private boolean isTrending;

    private boolean isActive;

    // 라이선스 정보 (무료 라이선스 확보된 음원만 사용)
    @Column(length = 500)
    private String licenseInfo;

    protected BgmTrack() {}

    private BgmTrack(Builder builder) {
        this.name = builder.name;
        this.category = builder.category;
        this.filePath = builder.filePath;
        this.previewUrl = builder.previewUrl;
        this.durationSeconds = builder.durationSeconds;
        this.isTrending = builder.isTrending;
        this.isActive = builder.isActive;
        this.licenseInfo = builder.licenseInfo;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String category;
        private String filePath;
        private String previewUrl;
        private Double durationSeconds;
        private boolean isTrending = false;
        private boolean isActive = true;
        private String licenseInfo;

        public Builder name(String name) { this.name = name; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder previewUrl(String previewUrl) { this.previewUrl = previewUrl; return this; }
        public Builder durationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; return this; }
        public Builder isTrending(boolean isTrending) { this.isTrending = isTrending; return this; }
        public Builder isActive(boolean isActive) { this.isActive = isActive; return this; }
        public Builder licenseInfo(String licenseInfo) { this.licenseInfo = licenseInfo; return this; }
        public BgmTrack build() { return new BgmTrack(this); }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getFilePath() { return filePath; }
    public String getPreviewUrl() { return previewUrl; }
    public Double getDurationSeconds() { return durationSeconds; }
    public boolean isTrending() { return isTrending; }
    public boolean isActive() { return isActive; }
    public String getLicenseInfo() { return licenseInfo; }
}
