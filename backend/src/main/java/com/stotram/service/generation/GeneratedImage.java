package com.stotram.service.generation;

import java.math.BigDecimal;

/**
 * The bytes a provider returned, plus what they cost.
 *
 * <p>{@code width}/{@code height} are what the provider actually produced, which
 * may not be what was asked for — image APIs accept a fixed set of aspect ratios
 * rather than arbitrary dimensions. The resize to final dimensions happens later,
 * alongside watermarking.
 */
public record GeneratedImage(
        byte[] bytes,
        String contentType,
        int width,
        int height,
        BigDecimal costUsd) {
}
