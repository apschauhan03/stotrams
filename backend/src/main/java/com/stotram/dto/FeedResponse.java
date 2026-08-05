package com.stotram.dto;

import java.util.List;

/** Public feed contract. See README section 4. */
public record FeedResponse(List<FeedItem> items, Integer nextPage) {

    public record FeedItem(
            Long id,
            String type,
            String url,
            Integer width,
            Integer height,
            String category,
            String lang,
            String date) {
    }
}
