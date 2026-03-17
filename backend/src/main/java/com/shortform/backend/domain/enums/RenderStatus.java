package com.shortform.backend.domain.enums;

public enum RenderStatus {
    PENDING,        // 큐 대기
    PROCESSING,     // 렌더링 중
    COMPLETED,      // 완료
    FAILED,         // 실패
    RETRY           // 재시도 중
}
