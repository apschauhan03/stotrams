package com.stotram.service;

import com.stotram.dto.FeedResponse;
import com.stotram.model.ContentItem;
import com.stotram.repository.ContentItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class FeedService {

    private final ContentItemRepository repository;
    private final String mediaBaseUrl;
    private final int pageSize;

    public FeedService(ContentItemRepository repository,
                       @Value("${app.media.base-url}") String mediaBaseUrl,
                       @Value("${app.feed.page-size}") int pageSize) {
        this.repository = repository;
        this.mediaBaseUrl = stripTrailingSlash(mediaBaseUrl);
        this.pageSize = pageSize;
    }

    public FeedResponse feed(String lang, String category, int page) {
        Page<ContentItem> result = repository.findPublished(
                blankToNull(category), blankToNull(lang), PageRequest.of(page, pageSize));

        var items = result.getContent().stream().map(this::toItem).toList();
        Integer nextPage = result.hasNext() ? page + 1 : null;
        return new FeedResponse(items, nextPage);
    }

    private FeedResponse.FeedItem toItem(ContentItem c) {
        return new FeedResponse.FeedItem(
                c.getId(),
                c.getType().toLowerCase(),               // contract uses lowercase "image"
                mediaBaseUrl + "/" + c.getR2Key(),        // CDN later = swap base url only
                c.getWidth(),
                c.getHeight(),
                c.getCategory().getSlug(),
                c.getLanguage(),
                c.getPublishDate() == null ? null : c.getPublishDate().toString());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
