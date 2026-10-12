package com.stotram.repository;

import com.stotram.model.PromptTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    /** The blocks shown in the admin panel, in display order. */
    List<PromptTemplate> findByArchivedFalseOrderBySortOrderAscIdAsc();

    /** What "Generate all" runs. */
    List<PromptTemplate> findByArchivedFalseAndActiveTrueOrderBySortOrderAscIdAsc();

    @Query("select coalesce(max(p.sortOrder), 0) from PromptTemplate p")
    int maxSortOrder();
}
