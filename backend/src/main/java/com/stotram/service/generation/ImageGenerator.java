package com.stotram.service.generation;

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

    /**
     * Generates a single image. Blocking.
     *
     * @throws ImageGenerationException if the provider failed or declined the
     *         prompt. One failure must not sink the rest of the batch, so callers
     *         catch this per image rather than per batch.
     */
    GeneratedImage generate(ImageRequest request);
}
