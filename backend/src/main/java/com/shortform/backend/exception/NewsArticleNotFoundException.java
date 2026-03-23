package com.shortform.backend.exception;

public class NewsArticleNotFoundException extends RuntimeException {

    public NewsArticleNotFoundException(Long articleId) {
        super("기사를 찾을 수 없습니다. ID: " + articleId);
    }
}
