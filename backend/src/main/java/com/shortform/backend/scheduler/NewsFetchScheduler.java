package com.shortform.backend.scheduler;

import com.shortform.backend.service.NewsArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 백엔드 시작 시 NewsAPI 수집 실행 (최대 3건, 중복 제외)
 */
@Component
public class NewsFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsFetchScheduler.class);

    private final NewsArticleService newsArticleService;

    public NewsFetchScheduler(NewsArticleService newsArticleService) {
        this.newsArticleService = newsArticleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            int saved = newsArticleService.fetchAndSaveOnStartup();
            if (saved > 0) {
                log.info("NewsAPI 수집 완료: {}건 신규 저장, 메타데이터 추출 비동기 진행 중", saved);
            }
        } catch (Exception e) {
            log.warn("NewsAPI 수집 실패 (무시): {}", e.getMessage());
        }
    }
}
