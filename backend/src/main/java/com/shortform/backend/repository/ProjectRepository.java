package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.Project;
import com.shortform.backend.domain.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Soft Delete 필터링: 삭제되지 않은 프로젝트만 조회
    @Query("SELECT p FROM Project p WHERE p.isDeleted = false ORDER BY p.createdAt DESC")
    Page<Project> findAllActive(Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.isDeleted = false")
    Optional<Project> findActiveById(@Param("id") Long id);

    @Query("SELECT p FROM Project p WHERE p.status = :status AND p.isDeleted = false")
    Page<Project> findByStatus(@Param("status") ProjectStatus status, Pageable pageable);
}
