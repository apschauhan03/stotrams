package com.stotram.service.generation;

/**
 * A single image failed to generate. Expected and survivable: providers rate-limit,
 * time out, and decline prompts on their own safety filters. The batch logs it,
 * counts it, and carries on with the remaining images.
 */
public class ImageGenerationException extends RuntimeException {

    public ImageGenerationException(String message) {
        super(message);
    }

    public ImageGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
