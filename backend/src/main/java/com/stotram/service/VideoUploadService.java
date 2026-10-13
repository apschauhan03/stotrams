package com.stotram.service;

import com.stotram.model.Category;
import com.stotram.model.ContentItem;
import com.stotram.repository.CategoryRepository;
import com.stotram.repository.ContentItemRepository;
import com.stotram.service.generation.ImageStorage;
import com.stotram.service.publish.PublishService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manual video upload from the admin panel (there is no video generation yet).
 *
 * <p>An uploaded video becomes a {@code VIDEO} content item in the review queue
 * (GENERATED), exactly like a generated image: it still has to be approved and published
 * before the public site shows it. Videos are published as uploaded — the watermark is
 * image-only for now (stamping video needs ffmpeg).
 *
 * <p>Stored under {@code videos/<category>/<date>/<uuid>.<ext>}, through the same storage
 * seam as images, so the move to R2 covers videos too.
 */
@Service
public class VideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadService.class);

    /** Formats every phone can play and share. Content type -> file extension. */
    private static final Map<String, String> FORMATS = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/quicktime", "mov");
    private static final Set<String> LANGUAGES = Set.of("hi", "en");
    private static final int MAX_DIMENSION = 8192;

    private final ContentItemRepository items;
    private final CategoryRepository categories;
    private final ImageStorage storage;

    public VideoUploadService(ContentItemRepository items, CategoryRepository categories, ImageStorage storage) {
        this.items = items;
        this.categories = categories;
        this.storage = storage;
    }

    /**
     * Saves the file and creates the candidate. {@code width}/{@code height} come from the
     * browser (it reads them from the video before uploading); null if it couldn't.
     */
    @Transactional
    public ContentItem upload(MultipartFile file, String categorySlug, String language,
                              Integer width, Integer height) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a video file to upload");
        }
        String ext = FORMATS.get(file.getContentType());
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Upload an MP4, WebM or MOV video (got " + file.getContentType() + ")");
        }
        Category category = categories.findBySlug(categorySlug == null ? "" : categorySlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: " + categorySlug));
        if (!LANGUAGES.contains(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language must be hi or en");
        }

        LocalDate today = LocalDate.now(PublishService.FEED_ZONE);
        String key = "videos/%s/%s/%s.%s".formatted(category.getSlug(), today, UUID.randomUUID(), ext);
        try {
            storage.store(key, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }

        ContentItem item = new ContentItem();
        item.setCategory(category);
        item.setType("VIDEO");
        item.setStatus("GENERATED");
        item.setLanguage(language);
        item.setR2Key(key);
        item.setWidth(validDimension(width));
        item.setHeight(validDimension(height));
        item.setPublishDate(today);
        ContentItem saved = items.save(item);
        log.info("Uploaded video #{} ({} bytes) at {}", saved.getId(), file.getSize(), key);
        return saved;
    }

    private static Integer validDimension(Integer value) {
        return value != null && value > 0 && value <= MAX_DIMENSION ? value : null;
    }
}
