package com.stotram.dto;

import java.time.OffsetDateTime;

/** A generation block as shown in the admin panel. */
public record BlockResponse(
        Long id,
        String name,
        String category,
        String language,
        String prompt,
        String model,
        int imageCount,
        boolean active,
        int sortOrder,
        OffsetDateTime lastGeneratedAt,
        OffsetDateTime updatedAt) {
}
