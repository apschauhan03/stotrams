package com.stotram.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A generation block as shown in the admin panel. {@code targetDate} null = any day. */
public record BlockResponse(
        Long id,
        String name,
        String category,
        String language,
        String prompt,
        String model,
        int imageCount,
        boolean active,
        LocalDate targetDate,
        int sortOrder,
        OffsetDateTime lastGeneratedAt,
        OffsetDateTime updatedAt) {
}
