package com.stotram.service.prompting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Expands the admin's <b>principle prompt</b> into N distinct image prompts with an OpenAI
 * text model, one per generation block. Drafts only — nothing is saved here; the admin
 * reviews them and then creates the blocks.
 *
 * <p>Uses Chat Completions with a strict JSON schema, so the reply always parses into
 * {@code {prompts: [{name, prompt}]}}. Models don't reliably hit an exact count, so a short
 * reply gets one follow-up request for the remainder (told which names exist, to avoid
 * repeats), and a long one is trimmed.
 */
@Service
public class PromptWriter {

    private static final Logger log = LoggerFactory.getLogger(PromptWriter.class);

    public static final int MAX_COUNT = 50;
    private static final int MAX_NAME = 128;
    private static final int MAX_PROMPT = 4000;

    public record Draft(String name, String prompt) {
    }

    /** House rules every generated prompt follows, whatever the principle says. */
    private static final String SYSTEM = """
            You write prompts for an AI image model (OpenAI gpt-image) for Stotrams, a free
            devotional image app for Indian families. Its audience is mostly older people who
            share these images on WhatsApp as morning/night greetings, so images must be
            beautiful, warm, respectful and instantly recognisable.

            The admin gives you a PRINCIPLE (their instructions) and a COUNT. Write exactly
            COUNT image prompts that follow the principle and are clearly different from each
            other — vary the deity or subject, scene, composition, lighting, colours, time of
            day and symbols as far as the principle allows. No two should look alike.

            Rules for every prompt:
            - Self-contained: the image model sees only that one prompt, nothing else.
            - 40 to 120 words, concrete and visual (subject, setting, light, mood, style).
            - Accurate, respectful Hindu iconography: correct number of arms, attributes,
              weapons, mudras and vahana; traditional dress and ornaments.
            - Vertical portrait composition suited to a phone screen.
            - No gore, no politics, no real or famous people, no brand logos.
            - Never draw the app's name, a logo or a watermark: the Stotrams watermark is
              stamped on afterwards, in the top-right corner — keep that corner free of
              important detail and of any text.
            - Text in the image: follow the principle. If it does not ask for text, end the
              prompt with "no text, no watermark".

            Also give each prompt a short, specific name (max 60 characters) for the admin's
            list, e.g. "Navratri Day 3 - Maa Chandraghanta at dawn".
            """;

    private static final Map<String, Object> RESPONSE_FORMAT = Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                    "name", "block_prompts",
                    "strict", true,
                    "schema", Map.of(
                            "type", "object",
                            "additionalProperties", false,
                            "required", List.of("prompts"),
                            "properties", Map.of(
                                    "prompts", Map.of(
                                            "type", "array",
                                            "items", Map.of(
                                                    "type", "object",
                                                    "additionalProperties", false,
                                                    "required", List.of("name", "prompt"),
                                                    "properties", Map.of(
                                                            "name", Map.of("type", "string"),
                                                            "prompt", Map.of("type", "string"))))))));

    private final RestClient client;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    public PromptWriter(ObjectMapper json,
                        @Value("${app.generation.openai.api-key:}") String apiKey,
                        @Value("${app.generation.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                        @Value("${app.prompting.model:gpt-5.4-mini}") String model) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofMinutes(3));   // 50 prompts can take a while
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public String model() {
        return model;
    }

    /**
     * @param context extra facts for the model, e.g. which category the blocks are for
     */
    public List<Draft> write(String principle, int count, String context) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "OpenAI key is not set on the server, so prompts can't be written.");
        }
        List<Draft> drafts = new ArrayList<>(request(principle, count, context, Set.of()));
        if (drafts.size() < count) {
            Set<String> taken = new LinkedHashSet<>();
            drafts.forEach(d -> taken.add(d.name()));
            log.info("Prompt writer returned {}/{}; asking for the remaining {}", drafts.size(), count,
                    count - drafts.size());
            drafts.addAll(request(principle, count - drafts.size(), context, taken));
        }
        List<Draft> result = drafts.stream().limit(count).toList();
        log.info("Prompt writer ({}): {} prompt(s) written for {} requested", model, result.size(), count);
        return result;
    }

    private List<Draft> request(String principle, int count, String context, Set<String> alreadyWritten) {
        StringBuilder user = new StringBuilder()
                .append("PRINCIPLE (from the admin):\n<<<\n").append(principle).append("\n>>>\n\n")
                .append(context).append("\n\n")
                .append("COUNT: ").append(count).append('\n');
        if (!alreadyWritten.isEmpty()) {
            user.append("\nThese are already written — write ").append(count)
                    .append(" MORE that differ from all of them:\n");
            alreadyWritten.forEach(n -> user.append("- ").append(n).append('\n'));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM),
                        Map.of("role", "user", "content", user.toString())),
                "response_format", RESPONSE_FORMAT);

        JsonNode response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String detail = openAiMessage(e.getResponseBodyAsString());
            log.warn("Prompt writer: OpenAI answered {}: {}", e.getStatusCode(), detail);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI error: " + detail);
        } catch (RuntimeException e) {
            log.warn("Prompt writer: OpenAI call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach OpenAI: " + e.getMessage());
        }

        JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
        if (message == null || message.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned no answer");
        }
        if (message.hasNonNull("refusal")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The model declined this principle: " + message.get("refusal").asText());
        }
        try {
            JsonNode prompts = json.readTree(message.path("content").asText()).path("prompts");
            List<Draft> drafts = new ArrayList<>();
            for (JsonNode p : prompts) {
                String name = clip(p.path("name").asText(), MAX_NAME);
                String prompt = clip(p.path("prompt").asText(), MAX_PROMPT);
                if (!name.isEmpty() && prompt.length() >= 10) {
                    drafts.add(new Draft(name, prompt));
                }
            }
            return drafts;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI's answer could not be read");
        }
    }

    private String openAiMessage(String body) {
        try {
            String m = json.readTree(body).path("error").path("message").asText();
            return m.isBlank() ? body : m;
        } catch (Exception e) {
            return body;
        }
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() <= max ? t : t.substring(0, max).trim();
    }
}
