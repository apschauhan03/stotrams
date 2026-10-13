package com.stotram.service.prompting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Looks a date's panchang up on the web (OpenAI Responses API with the {@code web_search}
 * tool): tithi, paksha, month, festivals, with the pages it used. It runs on its own,
 * without seeing {@link Panchang}'s answer, so the two are independent and can be compared —
 * a disagreement is something for the admin to check, not something to paper over.
 *
 * <p>The answer is forced into a strict JSON schema whose tithi is a number (1–30) and whose
 * month is one of {@link Panchang#MONTHS}, so comparing needs no fuzzy spelling matches
 * ("Ashvin" / "Ashwina" / "Ashwin").
 */
@Service
public class CalendarLookup {

    private static final Logger log = LoggerFactory.getLogger(CalendarLookup.class);

    /** What the web said. {@code tithiNumber} 0 / {@code month} "unknown" = not found. */
    public record Result(int tithiNumber, String month, List<String> festivals, List<String> sources, String notes) {
    }

    /** Thrown with a message fit to show the admin. */
    public static class LookupException extends RuntimeException {
        LookupException(String message) {
            super(message);
        }
    }

    private static final String INSTRUCTIONS = """
            You look up the Hindu panchang for one date, for New Delhi, India (IST). Search the
            web — prefer established panchang sites (Drik Panchang, ProKerala, etc.) — and
            report only what the pages say for THAT date; do not work it out from memory.

            - tithi_number: the tithi prevailing at SUNRISE in New Delhi, as 1–30:
              1–15 = Shukla Pratipada … Purnima (15), 16–30 = Krishna Pratipada … Amavasya (30).
              0 if you could not find it.
            - month_purnimanta: the Hindu month in the purnimanta (North Indian) system,
              or "unknown".
            - festivals: festivals, vrats, jayantis and observances on that date (short names,
              e.g. "Sharad Navratri Day 4 (Maa Kushmanda)", "Karwa Chauth"). Empty if none.
            - sources: the URLs you used.
            - notes: one or two sentences, e.g. if sources disagreed. Empty if nothing to add.
            """;

    private final RestClient client;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public CalendarLookup(ObjectMapper json,
                          @Value("${app.generation.openai.api-key:}") String apiKey,
                          @Value("${app.generation.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                          @Value("${app.prompting.model:gpt-5.4-mini}") String model,
                          @Value("${app.prompting.web-search:true}") boolean enabled) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.enabled = enabled;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofMinutes(2));   // searching takes a while
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Why a lookup can't run right now, or null if it can. */
    public String unavailableReason() {
        if (!enabled) return "Web search is turned off on the server (app.prompting.web-search).";
        if (apiKey.isBlank()) return "OpenAI key is not set on the server.";
        return null;
    }

    public Result lookup(LocalDate date) {
        String reason = unavailableReason();
        if (reason != null) {
            throw new LookupException(reason);
        }
        String day = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH));
        Map<String, Object> body = Map.of(
                "model", model,
                "tools", List.of(Map.of("type", "web_search")),
                "instructions", INSTRUCTIONS,
                "input", "Date: " + day + " (" + date + ").",
                "text", Map.of("format", schema()));

        JsonNode response;
        try {
            response = client.post()
                    .uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String detail = openAiMessage(e.getResponseBodyAsString());
            log.warn("Calendar lookup: OpenAI answered {}: {}", e.getStatusCode(), detail);
            throw new LookupException("OpenAI error: " + detail);
        } catch (RuntimeException e) {
            log.warn("Calendar lookup: OpenAI call failed", e);
            throw new LookupException("Could not reach OpenAI: " + e.getMessage());
        }

        String text = outputText(response);
        if (text == null) {
            throw new LookupException("The web lookup returned no answer");
        }
        try {
            JsonNode r = json.readTree(text);
            Result result = new Result(
                    r.path("tithi_number").asInt(0),
                    r.path("month_purnimanta").asText("unknown"),
                    strings(r.path("festivals")),
                    strings(r.path("sources")),
                    r.path("notes").asText(""));
            log.info("Calendar lookup for {}: tithi {}, month {}, {} festival(s), {} source(s)", date,
                    result.tithiNumber(), result.month(), result.festivals().size(), result.sources().size());
            return result;
        } catch (Exception e) {
            throw new LookupException("The web lookup's answer could not be read");
        }
    }

    /** The Responses API puts the text in output[].content[] items of type output_text. */
    private static String outputText(JsonNode response) {
        if (response == null) return null;
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode part : item.path("content")) {
                if ("output_text".equals(part.path("type").asText())) {
                    return part.path("text").asText();
                }
            }
        }
        return null;
    }

    private static Map<String, Object> schema() {
        List<Object> months = new ArrayList<>(List.of(Panchang.MONTHS));
        months.add("unknown");
        return Map.of(
                "type", "json_schema",
                "name", "panchang",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("tithi_number", "month_purnimanta", "festivals", "sources", "notes"),
                        "properties", Map.of(
                                "tithi_number", Map.of("type", "integer",
                                        "enum", IntStream.rangeClosed(0, 30).boxed().toList()),
                                "month_purnimanta", Map.of("type", "string", "enum", months),
                                "festivals", Map.of("type", "array", "items", Map.of("type", "string")),
                                "sources", Map.of("type", "array", "items", Map.of("type", "string")),
                                "notes", Map.of("type", "string"))));
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            String s = n.asText("").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private String openAiMessage(String body) {
        try {
            String m = json.readTree(body).path("error").path("message").asText();
            return m.isBlank() ? body : m;
        } catch (Exception e) {
            return body;
        }
    }
}
