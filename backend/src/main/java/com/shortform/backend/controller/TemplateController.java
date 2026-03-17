package com.shortform.backend.controller;

import com.shortform.backend.domain.entity.Template;
import com.shortform.backend.dto.response.ApiResponse;
import com.shortform.backend.repository.TemplateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRepository templateRepository;

    public TemplateController(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * GET /api/templates
     * 편집 템플릿 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTemplates() {
        List<Map<String, Object>> templates = templateRepository.findByIsActiveTrueOrderByNameAsc()
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(templates));
    }

    private Map<String, Object> toMap(Template t) {
        return Map.of(
                "id", t.getId(),
                "name", t.getName(),
                "description", t.getDescription() != null ? t.getDescription() : "",
                "outputPlatform", t.getOutputPlatform() != null ? t.getOutputPlatform().name() : "",
                "subtitleStyleJson", t.getSubtitleStyleJson() != null ? t.getSubtitleStyleJson() : "{}"
        );
    }
}
