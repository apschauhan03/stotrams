package com.stotram.dto;

/** PUT /admin/publish/schedule body. {@code cron} is Spring 6-field, Asia/Kolkata. */
public record PublishScheduleRequest(Boolean enabled, String cron) {
}
