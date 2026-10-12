package com.stotram.dto;

import java.util.List;

/** What the block editor may choose from, so the panel never hard-codes it. */
public record BlockOptionsResponse(
        List<CategoryOption> categories,
        List<String> languages,
        List<ModelOption> models,
        int maxImageCount,
        int maxTotalPerRun,
        String defaultEditModel) {

    public record CategoryOption(String slug, String name) {
    }

    /**
     * One entry in the image-model dropdown. {@code value} is what a block stores
     * ({@code openai:gpt-image-2}, {@code fake}); {@code available} is false when the
     * provider can't run right now, with the reason in {@code unavailableReason}.
     */
    public record ModelOption(
            String value, String label, boolean paid, boolean available, String unavailableReason, boolean canEdit) {
    }
}
