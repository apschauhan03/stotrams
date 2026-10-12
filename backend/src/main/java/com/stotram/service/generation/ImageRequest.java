package com.stotram.service.generation;

/**
 * One image to generate. Provider-agnostic on purpose: anything a specific
 * vendor needs beyond this (guidance scale, seed, negative prompt) belongs in
 * that provider's own config, not here.
 */
public record ImageRequest(String prompt, String model, int width, int height) {
}
