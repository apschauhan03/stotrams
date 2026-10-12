package com.stotram.dto;

import java.time.OffsetDateTime;

/** GET/PUT /admin/publish/schedule: the auto-publish job and what it did last. */
public record PublishScheduleResponse(
        boolean enabled,
        String cron,
        String zone,
        OffsetDateTime nextRunAt,
        OffsetDateTime lastRunAt,
        Integer lastRunCount,
        String lastRunTrigger) {
}
