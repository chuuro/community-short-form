package com.shortform.backend.dto.request;

public class KeywordItem {
    private String keyword;
    private String source;  // "openai" | "user"
    private boolean enabled = true;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
