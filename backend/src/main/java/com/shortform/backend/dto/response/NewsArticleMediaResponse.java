package com.shortform.backend.dto.response;

import com.shortform.backend.domain.entity.NewsArticleMedia;
import com.shortform.backend.domain.enums.MediaType;

public class NewsArticleMediaResponse {

    private Long id;
    private MediaType mediaType;
    private String sourceUrl;
    private String thumbnailUrl;
    private int orderIndex;
    private boolean selected;
    private String searchKeyword;
    private Integer width;
    private Integer height;
    private Double durationSeconds;
    private Double exposureDurationSeconds;
    private String photographerName;
    private String photographerUrl;

    public Long getId() { return id; }
    public MediaType getMediaType() { return mediaType; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getOrderIndex() { return orderIndex; }
    public boolean isSelected() { return selected; }
    public String getSearchKeyword() { return searchKeyword; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Double getDurationSeconds() { return durationSeconds; }
    public String getPhotographerName() { return photographerName; }
    public String getPhotographerUrl() { return photographerUrl; }

    public static NewsArticleMediaResponse from(NewsArticleMedia m) {
        NewsArticleMediaResponse r = new NewsArticleMediaResponse();
        r.id = m.getId();
        r.mediaType = m.getMediaType();
        r.sourceUrl = m.getSourceUrl();
        r.thumbnailUrl = m.getThumbnailUrl();
        r.orderIndex = m.getOrderIndex();
        r.selected = m.isSelected();
        r.searchKeyword = m.getSearchKeyword();
        r.width = m.getWidth();
        r.height = m.getHeight();
        r.durationSeconds = m.getDurationSeconds();
        r.exposureDurationSeconds = m.getExposureDurationSeconds();
        r.photographerName = m.getPhotographerName();
        r.photographerUrl = m.getPhotographerUrl();
        return r;
    }
}
