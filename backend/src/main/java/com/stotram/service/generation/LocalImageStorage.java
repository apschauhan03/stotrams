package com.stotram.service.generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes images to the local filesystem under {@code resources/static}: a key of
 * {@code images/good-morning/2026-08-07/x.png} lands at
 * {@code static/images/good-morning/2026-08-07/x.png} and is reachable at
 * {@code http://localhost:9303/images/good-morning/2026-08-07/x.png} — served from this same
 * directory by {@code LocalMediaConfig}, so new images are visible immediately.
 *
 * <p><b>TODO: this needs to be a CDN.</b> Replace this class with an {@code R2ImageStorage}
 * that uploads the same bytes to the Cloudflare R2 bucket under the <i>same key</i>, then
 * point {@code app.media.base-url} at the CDN domain. Because feed URLs are always built
 * as {@code base-url + r2_key}, nothing else changes — no code touching the feed, no data
 * migration, no re-keying of existing rows. The directory here maps to the bucket and the
 * key maps to the object key, one for one.
 *
 * <p>Known limitation, deliberately accepted for now: writing into {@code src/main/resources}
 * works when running from source but not from a packaged jar, where resources are read-only.
 * That gets resolved with the move to R2 — by then nothing writes to the local disk at all.
 */
@Component
public class LocalImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);

    private final Path root;

    public LocalImageStorage(
            @Value("${app.media.local-dir:src/main/resources/static}") String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
    }

    @Override
    public byte[] read(String key) {
        Path source = resolve(key);
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new ImageStorageException("Could not read image for key " + key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new ImageStorageException("Key escapes the storage root: " + key, null);
        }
        return path;
    }

    @Override
    public void store(String key, byte[] bytes) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            log.debug("Stored {} ({} bytes) at {}", key, bytes.length, target);
        } catch (IOException e) {
            throw new ImageStorageException("Could not write image for key " + key, e);
        }
    }
}
