package com.stotram.dto;

import java.time.OffsetDateTime;

/**
 * POST /admin/publish: how many approved items just went live, and how many stayed in
 * APPROVED because watermarking failed (see the server log for why).
 */
public record PublishResponse(int published, int failed, OffsetDateTime at) {
}
