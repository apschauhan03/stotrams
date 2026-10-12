package com.stotram.repository;

import com.stotram.model.GenerationBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationBatchRepository extends JpaRepository<GenerationBatch, Long> {

    /** The most recent run of a block, for "last generated" in the admin panel. */
    Optional<GenerationBatch> findFirstByPromptTemplate_IdOrderByCreatedAtDesc(Long promptTemplateId);
}
