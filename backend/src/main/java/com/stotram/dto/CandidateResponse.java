package com.stotram.dto;

/**
 * One image in the admin review queue. For an edited copy, {@code editedFromId} and
 * {@code editPrompt} say which image it came from and what was asked.
 */
public record CandidateResponse(
        Long id,
        String url,
        String status,
        String category,
        String lang,
        Integer width,
        Integer height,
        Long editedFromId,
        String editPrompt) {
}
