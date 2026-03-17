package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.RenderJob;
import com.shortform.backend.domain.enums.RenderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RenderJobRepository extends JpaRepository<RenderJob, Long> {

    List<RenderJob> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<RenderJob> findTopByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<RenderJob> findByWorkerJobId(String workerJobId);

    List<RenderJob> findByStatus(RenderStatus status);
}
