package com.shortform.backend.dto.request;

import com.shortform.backend.domain.enums.OutputPlatform;

public class RenderRequest {

    private OutputPlatform outputPlatform;
    private Long bgmTrackId;

    // true = 저화질 미리보기 렌더, false = 최종 고화질 렌더
    private boolean isPreview = false;

    // 워터마크 삽입 여부
    private boolean includeWatermark = false;

    public RenderRequest() {}

    public OutputPlatform getOutputPlatform() { return outputPlatform; }
    public void setOutputPlatform(OutputPlatform outputPlatform) { this.outputPlatform = outputPlatform; }

    public Long getBgmTrackId() { return bgmTrackId; }
    public void setBgmTrackId(Long bgmTrackId) { this.bgmTrackId = bgmTrackId; }

    public boolean isPreview() { return isPreview; }
    public void setPreview(boolean isPreview) { this.isPreview = isPreview; }

    public boolean isIncludeWatermark() { return includeWatermark; }
    public void setIncludeWatermark(boolean includeWatermark) { this.includeWatermark = includeWatermark; }
}
