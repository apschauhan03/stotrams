package com.stotram.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stotram.model.Category;
import com.stotram.model.ContentItem;
import com.stotram.repository.CategoryRepository;
import com.stotram.repository.ContentItemRepository;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup, loads resources/data/initial-content.json into the DB. Idempotent:
 * categories are matched by slug, items by their unique r2_key, so restarts and
 * re-runs don't create duplicates. This stands in for the real
 * generate -> review -> publish pipeline while we develop locally.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class InitialContentLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialContentLoader.class);
    private static final String MANIFEST = "data/initial-content.json";

    private final CategoryRepository categories;
    private final ContentItemRepository items;
    private final ObjectMapper objectMapper;

    public InitialContentLoader(CategoryRepository categories,
                                ContentItemRepository items,
                                ObjectMapper objectMapper) {
        this.categories = categories;
        this.items = items;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        var resource = new ClassPathResource(MANIFEST);
        if (!resource.exists()) {
            log.info("No manifest at classpath:{} — skipping initial content load.", MANIFEST);
            return;
        }

        InitialContentManifest manifest;
        try (InputStream in = resource.getInputStream()) {
            manifest = objectMapper.readValue(in, InitialContentManifest.class);
        }

        int newCategories = 0;
        if (manifest.categories() != null) {
            for (var c : manifest.categories()) {
                if (categories.findBySlug(c.slug()).isEmpty()) {
                    categories.save(new Category(c.slug(), c.nameEn(), c.nameHi(), c.sortOrder()));
                    newCategories++;
                }
            }
        }

        int newItems = 0;
        if (manifest.items() != null) {
            for (var i : manifest.items()) {
                if (items.existsByR2Key(i.r2Key())) {
                    continue;
                }
                Category category = categories.findBySlug(i.category())
                        .orElseThrow(() -> new IllegalStateException(
                                "Manifest item references unknown category: " + i.category()));

                var item = new ContentItem();
                item.setCategory(category);
                item.setType("IMAGE");
                item.setStatus("PUBLISHED");
                item.setLanguage(i.lang());
                item.setR2Key(i.r2Key());
                item.setWidth(i.width());
                item.setHeight(i.height());
                if (i.publishDate() != null) {
                    item.setPublishDate(LocalDate.parse(i.publishDate()));
                }
                item.setPublishedAt(OffsetDateTime.now());
                items.save(item);
                newItems++;
            }
        }

        log.info("Initial content load complete: +{} categories, +{} published items.",
                newCategories, newItems);
    }
}
