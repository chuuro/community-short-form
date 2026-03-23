package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.NewsArticleMedia;
import com.shortform.backend.domain.enums.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsArticleMediaRepository extends JpaRepository<NewsArticleMedia, Long> {

    List<NewsArticleMedia> findByNewsArticleIdOrderByOrderIndexAsc(Long newsArticleId);

    List<NewsArticleMedia> findByNewsArticleIdAndIsSelectedTrueOrderByOrderIndexAsc(Long newsArticleId);

    List<NewsArticleMedia> findByNewsArticleIdAndMediaTypeOrderByOrderIndexAsc(Long newsArticleId, MediaType mediaType);

    @Modifying
    @Query("DELETE FROM NewsArticleMedia m WHERE m.newsArticle.id = :articleId")
    void deleteByNewsArticleId(@Param("articleId") Long articleId);
}
