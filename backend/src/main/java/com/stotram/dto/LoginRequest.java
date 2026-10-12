package com.stotram.dto;

/** Body of POST /admin/login. */
public record LoginRequest(String email, String password) {
}
