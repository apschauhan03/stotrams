package com.stotram.repository;

import com.stotram.model.PromptTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    List<PromptTemplate> findByCategory_SlugAndActiveTrue(String slug);
}
