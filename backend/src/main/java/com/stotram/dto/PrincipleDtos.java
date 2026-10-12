package com.stotram.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Request/response shapes for /admin/principle (the principle prompt). */
public final class PrincipleDtos {

    private PrincipleDtos() {
    }

    /** GET/PUT /admin/principle. {@code model} is the text model that writes the prompts. */
    public record PrincipleResponse(String prompt, OffsetDateTime updatedAt, String model) {
    }

    /** PUT /admin/principle body. */
    public record PrincipleRequest(String prompt) {
    }

    /** POST /admin/principle/write body. {@code category} is a slug, used as context. */
    public record WriteRequest(String prompt, Integer count, String category) {
    }

    /** POST /admin/principle/write response: drafts to review — nothing saved yet. */
    public record WriteResponse(List<Draft> drafts, String model) {
    }

    public record Draft(String name, String prompt) {
    }

    /** POST /admin/blocks/bulk body: shared settings + one name/prompt per block. */
    public record BulkBlocksRequest(
            String category,
            String language,
            String model,
            Integer imageCount,
            Boolean active,
            List<Draft> items) {
    }
}
