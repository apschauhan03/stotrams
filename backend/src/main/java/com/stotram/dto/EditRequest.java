package com.stotram.dto;

/** POST /admin/candidates/{id}/edit body. {@code model} is optional (default: the edit default). */
public record EditRequest(String prompt, String model) {
}
