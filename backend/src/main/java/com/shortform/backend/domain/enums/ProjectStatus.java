package com.shortform.backend.domain.enums;

public enum ProjectStatus {
    CREATED,        // URL 입력만 된 상태
    PARSING,        // 미디어 파싱 중
    PARSED,         // 파싱 완료, 편집 대기
    EDITING,        // 편집 중
    RENDER_PENDING, // 렌더 대기
    RENDERING,      // 렌더 중
    COMPLETED,      // 최종 완료
    FAILED          // 실패
}
