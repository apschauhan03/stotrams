package com.stotram.service.generation;

/** Writing an image to storage failed. Treated like a generation failure: that image is lost, the batch continues. */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
