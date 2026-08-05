package com.stotram.dto;

/** One image awaiting review in the admin panel. */
public record CandidateResponse(
        Long id,
        String url,
        String status,
        String category,
        String lang,
        Integer width,
        Integer height) {
}
