package com.stotram.service.generation;

/**
 * Edit an existing image according to an instruction ("remove the text at the bottom").
 *
 * @param image       the source image bytes (the clean original, never a watermarked copy)
 * @param contentType e.g. image/png
 * @param instruction what to change, in plain words
 * @param model       provider-specific model id (the part after {@code provider:})
 */
public record ImageEditRequest(byte[] image, String contentType, String instruction, String model) {
}
