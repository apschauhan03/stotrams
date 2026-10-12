package com.stotram.dto;

/**
 * Answer to POST /admin/generate. Returned immediately, while the images are still
 * being generated — poll {@code GET /admin/candidates?status=GENERATED} to watch them land.
 */
public record GenerateResponse(
        Long batchId,
        String category,
        int requestedCount,
        String message) {
}
