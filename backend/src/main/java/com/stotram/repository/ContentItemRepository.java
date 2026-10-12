package com.stotram.repository;

import com.stotram.model.ContentItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {

    /** Admin review queue: items in a given status (e.g. GENERATED), newest first. */
    List<ContentItem> findByStatusOrderByIdDesc(String status);

    /**
     * Public feed: published items for an optional category/language, newest first.
     * A null filter means "any".
     */
    @Query("""
            select c from ContentItem c
            where c.status = 'PUBLISHED'
              and (:category is null or c.category.slug = :category)
              and (:lang is null or c.language = :lang)
            order by c.publishDate desc, c.id desc
            """)
    Page<ContentItem> findPublished(@Param("category") String category,
                                    @Param("lang") String lang,
                                    Pageable pageable);
}
