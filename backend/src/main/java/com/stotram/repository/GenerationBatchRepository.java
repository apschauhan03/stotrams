package com.stotram.repository;

import com.stotram.model.GenerationBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationBatchRepository extends JpaRepository<GenerationBatch, Long> {
}
