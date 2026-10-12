package com.stotram.dto;

/**
 * Answer to POST /admin/blocks/{id}/generate (and one per block for /generate-all).
 * Returned immediately, while the images are still being generated — poll
 * {@code GET /admin/candidates?status=GENERATED} to watch them land.
 */
public record GenerateResponse(
        Long batchId,
        Long blockId,
        String blockName,
        String category,
        int requestedCount,
        String message) {
}
