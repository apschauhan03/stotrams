package com.stotram.service.publish;

import com.stotram.model.ContentItem;
import com.stotram.model.PublishSchedule;
import com.stotram.repository.ContentItemRepository;
import com.stotram.repository.PublishScheduleRepository;
import com.stotram.service.generation.ImageStorage;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves every APPROVED item into the public feed. Called by the scheduled job and by the
 * admin's "Publish now" button — same code path either way.
 *
 * <p>Every published image (not video, for now) carries the Stotrams watermark: the clean original is read,
 * stamped by {@link Watermarker}, saved as a JPEG under {@code images/published/...}, and
 * {@code r2_key} is pointed at that copy ({@code original_key} keeps the clean one). An item
 * whose watermarking fails is <b>not</b> published — it stays APPROVED and is reported, so
 * nothing ever goes live without the mark.
 *
 * <p>An item's {@code publish_date} becomes the day it goes live (not the day it was
 * generated), so the feed shows it as today's content.
 */
@Service
public class PublishService {

    /** "Today" for the feed is an Indian day, whatever timezone the server runs in. */
    public static final ZoneId FEED_ZONE = ZoneId.of("Asia/Kolkata");

    private static final Logger log = LoggerFactory.getLogger(PublishService.class);

    public enum Trigger { SCHEDULE, MANUAL }

    public record Result(int published, int failed, Trigger trigger, OffsetDateTime at) {
    }

    private final ContentItemRepository items;
    private final PublishScheduleRepository schedules;
    private final ImageStorage storage;
    private final Watermarker watermarker;

    public PublishService(ContentItemRepository items,
                          PublishScheduleRepository schedules,
                          ImageStorage storage,
                          Watermarker watermarker) {
        this.items = items;
        this.schedules = schedules;
        this.storage = storage;
        this.watermarker = watermarker;
    }

    @Transactional
    public Result publishApproved(Trigger trigger) {
        OffsetDateTime now = OffsetDateTime.now(FEED_ZONE);
        LocalDate today = now.toLocalDate();

        int published = 0;
        int failed = 0;
        for (ContentItem item : items.findByStatusOrderByIdDesc("APPROVED")) {
            // Videos go live as uploaded: the watermark is image-only until we stamp video with ffmpeg.
            if (!isVideo(item) && !watermark(item)) {
                failed++;
                continue;                       // stays APPROVED: never live without the mark
            }
            item.setStatus("PUBLISHED");
            item.setPublishDate(today);
            item.setPublishedAt(now);
            published++;
        }

        PublishSchedule schedule = schedules.getSingleton();
        schedule.setLastRunAt(now);
        schedule.setLastRunCount(published);
        schedule.setLastRunTrigger(trigger.name());

        log.info("Publish ({}): {} item(s) now live for {}{}", trigger, published, today,
                failed > 0 ? ", " + failed + " left in APPROVED because watermarking failed" : "");
        return new Result(published, failed, trigger, now);
    }

    /**
     * Watermarks live items: only those never processed ({@code all = false}), or every live
     * item ({@code all = true}, after the watermark design changed). Always renders from the
     * clean original, so re-running is safe.
     *
     * @return true if every item it attempted succeeded
     */
    @Transactional
    public boolean watermarkLiveItems(boolean all) {
        List<ContentItem> todo = items.findByStatusOrderByIdDesc("PUBLISHED").stream()
                .filter(item -> !isVideo(item))
                .filter(item -> all || item.getOriginalKey() == null)
                .toList();
        int done = (int) todo.stream().filter(this::watermark).count();
        if (!todo.isEmpty()) {
            log.info("Watermarked {}/{} live item(s){}", done, todo.size(), all ? " (new watermark design)" : "");
        }
        return done == todo.size();
    }

    private static boolean isVideo(ContentItem item) {
        return "VIDEO".equals(item.getType());
    }

    /**
     * Renders the watermarked JPEG from the clean original and points the item at it.
     * Always starts from the original, so republishing never stamps a mark on a mark.
     *
     * @return false (and logs why) if it couldn't be done
     */
    private boolean watermark(ContentItem item) {
        String original = item.getOriginalKey() != null ? item.getOriginalKey() : item.getR2Key();
        try {
            Watermarker.Result result = watermarker.apply(storage.read(original));
            String key = publishedKeyFor(original);
            storage.store(key, result.jpeg());
            item.setOriginalKey(original);
            item.setR2Key(key);
            item.setWidth(result.width());
            item.setHeight(result.height());
            return true;
        } catch (RuntimeException e) {
            log.error("Could not watermark item {} ({})", item.getId(), original, e);
            return false;
        }
    }

    /**
     * images/good-morning/2026-10-13/x.png -> images/published/v2-top-right/good-morning/2026-10-13/x.jpg
     * (the watermark version in the path gives each design its own, cache-safe URL).
     */
    static String publishedKeyFor(String originalKey) {
        String rest = originalKey.startsWith("images/") ? originalKey.substring("images/".length()) : originalKey;
        int dot = rest.lastIndexOf('.');
        String base = dot > rest.lastIndexOf('/') ? rest.substring(0, dot) : rest;
        return "images/published/" + Watermarker.VERSION + "/" + base + ".jpg";
    }
}
