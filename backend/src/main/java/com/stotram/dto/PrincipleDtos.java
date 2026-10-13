package com.stotram.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Request/response shapes for /admin/principle (the principle prompt). */
public final class PrincipleDtos {

    private PrincipleDtos() {
    }

    /**
     * GET/PUT/DELETE /admin/principle. {@code model} is the text model that writes the
     * prompts; {@code isDefault} says the text is the built-in default master prompt.
     */
    public record PrincipleResponse(String prompt, OffsetDateTime updatedAt, String model, boolean isDefault) {
    }

    /** PUT /admin/principle body. */
    public record PrincipleRequest(String prompt) {
    }

    /**
     * POST /admin/principle/write body. {@code category} is a slug and {@code date} an
     * optional ISO day (yyyy-MM-dd) the images are for; both are context for the model.
     * {@code checkWeb} also looks the date's panchang up on the web (costs a little).
     */
    public record WriteRequest(String prompt, Integer count, String category, String date, Boolean checkWeb) {
    }

    /**
     * POST /admin/principle/write response: drafts to review — nothing saved yet.
     * {@code calendar} is null when no date was given. {@code researchSummary} and
     * {@code recommendation} are what the master prompt asked for ("" if nothing).
     */
    public record WriteResponse(List<Draft> drafts, String model, CalendarCheck calendar,
                                String researchSummary, String recommendation) {
    }

    /**
     * The date's panchang as computed on the server and, optionally, as found on the web —
     * shown side by side so the admin can see whether they agree.
     *
     * @param web      null when the web wasn't checked or the lookup failed ({@code webError})
     * @param tithiMatches null when there's nothing to compare
     */
    public record CalendarCheck(
            String date,
            String weekday,
            CalendarFacts computed,
            CalendarFacts web,
            String webError,
            Boolean tithiMatches,
            Boolean monthMatches) {
    }

    /**
     * One source's view of the day. {@code tithi} like "Shukla Chaturthi"; for the computed
     * side {@code tithiEnds} and {@code navratri} are filled, for the web side
     * {@code festivals}, {@code sources} and {@code notes}.
     */
    public record CalendarFacts(
            Integer tithiNumber,
            String tithi,
            String month,
            String tithiEnds,
            String navratri,
            List<String> festivals,
            List<String> sources,
            String notes) {
    }

    public record Draft(String name, String prompt) {
    }

    /**
     * POST /admin/blocks/bulk body: shared settings + one name/prompt per block.
     * {@code targetDate} (yyyy-MM-dd, optional) is the date the drafts were written for.
     */
    public record BulkBlocksRequest(
            String category,
            String language,
            String model,
            Integer imageCount,
            Boolean active,
            String targetDate,
            List<Draft> items) {
    }
}
