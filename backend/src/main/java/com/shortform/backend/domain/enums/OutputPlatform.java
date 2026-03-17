package com.shortform.backend.domain.enums;

public enum OutputPlatform {
    YOUTUBE_SHORTS("YouTube Shorts", 1080, 1920, 60),
    TIKTOK("TikTok", 1080, 1920, 600),
    INSTAGRAM_REELS("Instagram Reels", 1080, 1920, 90),
    GENERAL("General", 1080, 1920, 600);

    private final String displayName;
    private final int width;
    private final int height;
    private final int maxDurationSeconds;

    OutputPlatform(String displayName, int width, int height, int maxDurationSeconds) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public String getDisplayName() { return displayName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getMaxDurationSeconds() { return maxDurationSeconds; }
}
