package com.stotram.controller;

import com.stotram.dto.FeedResponse;
import com.stotram.service.FeedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only feed. See README section 4. */
@RestController
@RequestMapping("/api")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/feed")
    public FeedResponse feed(
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page) {
        return feedService.feed(lang, category, Math.max(page, 0));
    }
}
