package com.stotram.service.generation;

/**
 * Where generated image bytes go.
 *
 * <p>The same seam idea as {@link ImageGenerator}: the thing we know we will swap
 * sits behind an interface. {@code LocalImageStorage} writes into
 * {@code resources/static} today; an R2 implementation uploads to the bucket later.
 *
 * <p>The {@code key} is deliberately the same string as {@code content_item.r2_key},
 * and it is assigned once at generation time and never changes. Local storage
 * treats it as a path under the static directory; R2 treats it as an object key in
 * the bucket. That is what makes the move to a CDN a config change rather than a
 * data migration — see the note on the local implementation.
 */
public interface ImageStorage {

    /**
     * Writes the bytes at {@code key}, overwriting any existing object.
     *
     * @throws ImageStorageException if the write failed
     */
    void store(String key, byte[] bytes);

    /**
     * Reads the bytes at {@code key} (the publish step reads the clean original to stamp
     * the watermark onto it).
     *
     * @throws ImageStorageException if the object is missing or unreadable
     */
    byte[] read(String key);
}
