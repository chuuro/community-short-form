package com.shortform.backend.service.parser;

import com.shortform.backend.domain.enums.CommunityType;

import java.util.List;

public interface CommunityParser {

    CommunityType getSupportedType();

    boolean supports(String url);

    ParsedPost parse(String url);

    record ParsedPost(
            String title,
            String description,
            String thumbnailUrl,
            List<ParsedMedia> mediaList,
            List<String> popularComments
    ) {}

    record ParsedMedia(
            String sourceUrl,
            com.shortform.backend.domain.enums.MediaType mediaType,
            String altText,
            int orderIndex
    ) {}
}
