package com.stotram.dto;

/**
 * Body of POST/PUT /admin/blocks. {@code category} is a category slug; {@code targetDate}
 * an optional ISO day (yyyy-MM-dd) the images are for.
 */
public record BlockRequest(
        String name,
        String category,
        String language,
        String prompt,
        String model,
        Integer imageCount,
        Boolean active,
        String targetDate) {
}
