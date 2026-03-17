package com.shortform.backend.domain.entity;

import com.shortform.backend.domain.enums.OutputPlatform;
import jakarta.persistence.*;

@Entity
@Table(name = "templates")
public class Template extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private OutputPlatform outputPlatform;

    // 자막 스타일 기본값 (JSON)
    @Column(columnDefinition = "TEXT")
    private String subtitleStyleJson;

    // 전환 효과 설정 (JSON)
    @Column(columnDefinition = "TEXT")
    private String transitionJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_bgm_id")
    private BgmTrack defaultBgmTrack;

    private boolean isActive;

    protected Template() {}

    private Template(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.outputPlatform = builder.outputPlatform;
        this.subtitleStyleJson = builder.subtitleStyleJson;
        this.transitionJson = builder.transitionJson;
        this.defaultBgmTrack = builder.defaultBgmTrack;
        this.isActive = builder.isActive;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String description;
        private OutputPlatform outputPlatform;
        private String subtitleStyleJson;
        private String transitionJson;
        private BgmTrack defaultBgmTrack;
        private boolean isActive = true;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder outputPlatform(OutputPlatform outputPlatform) { this.outputPlatform = outputPlatform; return this; }
        public Builder subtitleStyleJson(String subtitleStyleJson) { this.subtitleStyleJson = subtitleStyleJson; return this; }
        public Builder transitionJson(String transitionJson) { this.transitionJson = transitionJson; return this; }
        public Builder defaultBgmTrack(BgmTrack defaultBgmTrack) { this.defaultBgmTrack = defaultBgmTrack; return this; }
        public Builder isActive(boolean isActive) { this.isActive = isActive; return this; }
        public Template build() { return new Template(this); }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public OutputPlatform getOutputPlatform() { return outputPlatform; }
    public String getSubtitleStyleJson() { return subtitleStyleJson; }
    public String getTransitionJson() { return transitionJson; }
    public BgmTrack getDefaultBgmTrack() { return defaultBgmTrack; }
    public boolean isActive() { return isActive; }
}
