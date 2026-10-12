package com.stotram.dto;

/** Body of POST/PUT /admin/blocks. {@code category} is a category slug. */
public record BlockRequest(
        String name,
        String category,
        String language,
        String prompt,
        String model,
        Integer imageCount,
        Boolean active) {
}
