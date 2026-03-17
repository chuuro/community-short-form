package com.shortform.backend.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(Long projectId) {
        super("프로젝트를 찾을 수 없습니다. ID: " + projectId);
    }
}
