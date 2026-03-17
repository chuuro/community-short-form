package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    List<Template> findByIsActiveTrueOrderByNameAsc();
}
