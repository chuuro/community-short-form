package com.shortform.backend.dto.request;

import com.shortform.backend.domain.enums.OutputPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateProjectRequest {

    @NotBlank(message = "커뮤니티 URL을 입력해주세요.")
    @Pattern(regexp = "^https?://.*", message = "올바른 URL 형식이 아닙니다.")
    private String communityUrl;

    private OutputPlatform outputPlatform = OutputPlatform.YOUTUBE_SHORTS;

    private Long templateId;

    public CreateProjectRequest() {}

    public String getCommunityUrl() { return communityUrl; }
    public void setCommunityUrl(String communityUrl) { this.communityUrl = communityUrl; }

    public OutputPlatform getOutputPlatform() { return outputPlatform; }
    public void setOutputPlatform(OutputPlatform outputPlatform) { this.outputPlatform = outputPlatform; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
}
