package com.shortform.backend.domain.enums;

/**
 * 뉴스 기사 처리 상태
 */
public enum NewsArticleStatus {
    /** NewsAPI에서 수집 완료 */
    FETCHED,
    /** OpenAI 메타데이터 추출 중 */
    METADATA_EXTRACTING,
    /** 메타데이터 추출 완료 (프론트 표시 가능) */
    METADATA_READY,
    /** 멀티미디어 검색 중 */
    MULTIMEDIA_FETCHING,
    /** 멀티미디어 준비 완료 (미리보기 가능) */
    MULTIMEDIA_READY,
    /** 최종 렌더 요청됨 */
    RENDER_REQUESTED,
    /** 렌더 완료 */
    RENDERED,
    /** 처리 실패 */
    FAILED
}
