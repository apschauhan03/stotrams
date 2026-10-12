package com.stotram.service.generation;

import java.util.List;
import java.util.Optional;

/**
 * A source of generated images. One implementation per vendor.
 *
 * <p>This is the seam that keeps the pipeline vendor-neutral: adding Bedrock,
 * Gemini, or anything else is a new {@code @Component} implementing this and
 * nothing else changes. Which one runs is data, not config —
 * {@code prompt_template.model} names the provider per category, so good-morning
 * and a festival category can target different models without a deploy.
 *
 * <p>Implementations are called from a bounded background pool, never from a
 * request thread, so blocking here is expected and fine.
 */
public interface ImageGenerator {

    /** Matches the {@code provider:} prefix of {@code prompt_template.model}. */
    String providerId();

    /** Name shown in the admin panel's model dropdown, e.g. "OpenAI". */
    default String displayName() {
        return providerId();
    }

    /**
     * Model ids offered in the admin dropdown, each becoming {@code provider:model}. Empty
     * means the provider has no model choice and is selected by its id alone (e.g. "fake").
     */
    default List<String> models() {
        return List.of();
    }

    /** Whether running this provider costs money (the panel asks for confirmation if so). */
    default boolean paid() {
        return true;
    }

    /**
     * Why this provider can't run right now (e.g. no API key), or empty when it can.
     * Checked before a batch starts, so the admin gets a clear refusal instead of a batch
     * whose every image fails in the background.
     */
    default Optional<String> unavailableReason() {
        return Optional.empty();
    }

    /** Whether {@link #edit} is implemented (prompt-based editing of an existing image). */
    default boolean supportsEdit() {
        return false;
    }

    /**
     * Returns a new image: {@code request.image()} changed as {@code request.instruction()}
     * says. Blocking. The source is never modified.
     *
     * @throws ImageGenerationException if the provider failed, declined, or can't edit
     */
    default GeneratedImage edit(ImageEditRequest request) {
        throw new ImageGenerationException("Provider '" + providerId() + "' can't edit images");
    }

    /**
     * Generates a single image. Blocking.
     *
     * @throws ImageGenerationException if the provider failed or declined the
     *         prompt. One failure must not sink the rest of the batch, so callers
     *         catch this per image rather than per batch.
     */
    GeneratedImage generate(ImageRequest request);
}
