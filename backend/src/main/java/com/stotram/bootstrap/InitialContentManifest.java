package com.stotram.bootstrap;

import java.util.List;

/** Shape of resources/data/initial-content.json. */
public record InitialContentManifest(List<Category> categories, List<Item> items) {

    public record Category(String slug, String nameEn, String nameHi, int sortOrder) {
    }

    public record Item(
            String r2Key,
            String category,
            String lang,
            Integer width,
            Integer height,
            String publishDate) {
    }
}
