package com.shortform.backend.dto.request;

import com.shortform.backend.domain.enums.OutputPlatform;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ScriptGenerateRequest {

    /** 숏폼 영상 주제 (예: "커피의 효능", "Python의 장점") */
    @NotBlank(message = "주제를 입력해주세요.")
    @Size(max = 200, message = "주제는 200자 이내로 입력해주세요.")
    private String topic;

    /** 씬(장면) 수 — 기본 5, 최대 10 */
    @Min(value = 2, message = "씬은 최소 2개 이상이어야 합니다.")
    @Max(value = 10, message = "씬은 최대 10개까지 설정할 수 있습니다.")
    private int sceneCount = 5;

    /** 출력 플랫폼 — 기본 YOUTUBE_SHORTS */
    private OutputPlatform outputPlatform = OutputPlatform.YOUTUBE_SHORTS;

    public ScriptGenerateRequest() {}

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public int getSceneCount() { return sceneCount; }
    public void setSceneCount(int sceneCount) { this.sceneCount = sceneCount; }

    public OutputPlatform getOutputPlatform() { return outputPlatform; }
    public void setOutputPlatform(OutputPlatform outputPlatform) { this.outputPlatform = outputPlatform; }
}
