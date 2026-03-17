package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.MediaItem;
import com.shortform.backend.domain.enums.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    List<MediaItem> findByProjectIdOrderByOrderIndexAsc(Long projectId);

    List<MediaItem> findByProjectIdAndMediaTypeOrderByOrderIndexAsc(Long projectId, MediaType mediaType);

    List<MediaItem> findByProjectIdAndIsIncludedTrueOrderByOrderIndexAsc(Long projectId);

    @Modifying
    @Query("DELETE FROM MediaItem m WHERE m.project.id = :projectId")
    void deleteAllByProjectId(@Param("projectId") Long projectId);
}
