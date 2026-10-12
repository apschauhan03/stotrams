package com.stotram.dto;

/** The logged-in admin, returned by POST /admin/login and GET /admin/me. */
public record AdminSessionResponse(String email) {
}
