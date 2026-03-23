package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.NewsArticle;
import com.shortform.backend.domain.enums.NewsArticleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByUrl(String url);

    Optional<NewsArticle> findByUrl(String url);

    List<NewsArticle> findByStatusOrderByCreatedAtDesc(NewsArticleStatus status, Pageable pageable);

    List<NewsArticle> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<NewsArticle> findByProjectId(Long projectId);
}
