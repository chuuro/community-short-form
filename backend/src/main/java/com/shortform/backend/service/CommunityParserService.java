package com.shortform.backend.service;

import com.shortform.backend.domain.enums.CommunityType;
import com.shortform.backend.service.parser.CommunityParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommunityParserService {

    private static final Logger log = LoggerFactory.getLogger(CommunityParserService.class);

    private final List<CommunityParser> parsers;

    public CommunityParserService(List<CommunityParser> parsers) {
        this.parsers = parsers;
    }

    public CommunityParser.ParsedPost parse(String url) {
        CommunityParser parser = findParser(url);
        log.info("URL 파싱 시작: {} (파서: {})", url, parser.getSupportedType());
        return parser.parse(url);
    }

    public CommunityType detectCommunityType(String url) {
        return parsers.stream()
                .filter(p -> p.supports(url))
                .map(CommunityParser::getSupportedType)
                .findFirst()
                .orElse(CommunityType.UNKNOWN);
    }

    private CommunityParser findParser(String url) {
        return parsers.stream()
                .filter(p -> p.supports(url))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "지원하지 않는 URL 형식입니다.\n" +
                        "• Reddit: https://www.reddit.com/r/{subreddit}/comments/{id}/...\n" +
                        "• YouTube: https://www.youtube.com/watch?v=...\n" +
                        "검색 URL, 서브레딧 목록, 홈피드 URL은 지원하지 않습니다."));
    }
}
