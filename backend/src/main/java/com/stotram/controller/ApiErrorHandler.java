package com.stotram.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns our own {@link ResponseStatusException}s into {@code {"message": "..."}} so the
 * admin panel can show the reason ("not a valid cron expression", "count must be between 1
 * and 50", ...). Spring's default error body hides it. Other exceptions are untouched, so
 * internal error details still never leak.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}
