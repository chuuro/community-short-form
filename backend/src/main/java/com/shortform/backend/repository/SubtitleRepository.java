package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.Subtitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubtitleRepository extends JpaRepository<Subtitle, Long> {

    List<Subtitle> findByProjectIdOrderByOrderIndexAsc(Long projectId);

    @Modifying
    @Query("DELETE FROM Subtitle s WHERE s.project.id = :projectId")
    void deleteAllByProjectId(@Param("projectId") Long projectId);
}
