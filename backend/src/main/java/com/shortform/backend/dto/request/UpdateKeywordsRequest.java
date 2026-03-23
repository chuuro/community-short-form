package com.shortform.backend.dto.request;

import java.util.List;

public class UpdateKeywordsRequest {
    private List<KeywordItem> imageSearchKeywords;
    private List<KeywordItem> videoSearchKeywords;

    public List<KeywordItem> getImageSearchKeywords() { return imageSearchKeywords; }
    public void setImageSearchKeywords(List<KeywordItem> imageSearchKeywords) { this.imageSearchKeywords = imageSearchKeywords; }
    public List<KeywordItem> getVideoSearchKeywords() { return videoSearchKeywords; }
    public void setVideoSearchKeywords(List<KeywordItem> videoSearchKeywords) { this.videoSearchKeywords = videoSearchKeywords; }
}
