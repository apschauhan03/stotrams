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

    /** {@code prompt} is what the image model gets: the prompt plus its negative prompt, if any. */
    public record Draft(String name, String prompt) {
    }

    /**
     * Drafts plus the extras a master prompt may ask for: a research summary of the date and
     * a closing recommendation (empty strings when the model gave none).
     */
    public record Result(List<Draft> drafts, String researchSummary, String recommendation) {
    }

    /** One parsed reply. */
    private record Reply(List<Draft> drafts, String researchSummary, String recommendation) {
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

            How to answer (the reply is JSON, whatever format the principle describes):
            - prompts[].name: the option's concept name. prompts[].prompt: its complete,
              self-contained image prompt. prompts[].negative_prompt: its negative prompt, or
              "" if the principle doesn't ask for one.
            - research_summary: the research summary the principle asks for, as short plain
              text (or "" if it asks for none). recommendation: the closing recommendation the
              principle asks for (or "").
            - You cannot browse the web. When the principle says to research, the research has
              been done for you: rely on the date and calendar facts given below the principle,
              and cite only sources listed there. Never invent a source, tithi or festival.

            Rules for every prompt:
            - Self-contained: the image model sees only that one prompt, nothing else.
            - 40 to 120 words, concrete and visual (subject, setting, light, mood, style).
              If the principle asks for more detail, go longer, but never over 350 words.
            - Accurate, respectful Hindu iconography: correct number of arms, attributes,
              weapons, mudras and vahana; traditional dress and ornaments.
            - Vertical portrait composition suited to a phone screen.
            - No gore, no politics, no real or famous people, no brand logos.
            - Never draw the app's name, a logo or a watermark: the Stotrams watermark is
              stamped on afterwards, in the top-right corner — keep that corner free of
              important detail and of any text (centre any top heading instead).
            - Text in the image: follow the principle. If it asks for text (e.g. a Hindi
              greeting), NEVER write "no text" anywhere in that prompt — end it with "no
              watermark, no other text". If it asks for no text, end with "no text, no watermark".
            - Write negative_prompt in English (the image model follows English best), even
              when the image text is Hindi.

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
                            "required", List.of("research_summary", "prompts", "recommendation"),
                            "properties", Map.of(
                                    "research_summary", Map.of("type", "string"),
                                    "recommendation", Map.of("type", "string"),
                                    "prompts", Map.of(
                                            "type", "array",
                                            "items", Map.of(
                                                    "type", "object",
                                                    "additionalProperties", false,
                                                    "required", List.of("name", "prompt", "negative_prompt"),
                                                    "properties", Map.of(
                                                            "name", Map.of("type", "string"),
                                                            "prompt", Map.of("type", "string"),
                                                            "negative_prompt", Map.of("type", "string"))))))));

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
        factory.setReadTimeout(Duration.ofMinutes(8));   // 50 long, detailed prompts take a while
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public String model() {
        return model;
    }

    /**
     * @param context extra facts for the model, e.g. which category the blocks are for
     */
    public Result write(String principle, int count, String context) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "OpenAI key is not set on the server, so prompts can't be written.");
        }
        Reply first = request(principle, count, context, Set.of());
        List<Draft> drafts = new ArrayList<>(first.drafts());
        if (drafts.size() < count) {
            Set<String> taken = new LinkedHashSet<>();
            drafts.forEach(d -> taken.add(d.name()));
            log.info("Prompt writer returned {}/{}; asking for the remaining {}", drafts.size(), count,
                    count - drafts.size());
            drafts.addAll(request(principle, count - drafts.size(), context, taken).drafts());
        }
        List<Draft> result = drafts.stream().limit(count).toList();
        log.info("Prompt writer ({}): {} prompt(s) written for {} requested", model, result.size(), count);
        return new Result(result, first.researchSummary(), first.recommendation());
    }

    private Reply request(String principle, int count, String context, Set<String> alreadyWritten) {
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
            JsonNode reply = json.readTree(message.path("content").asText());
            List<Draft> drafts = new ArrayList<>();
            for (JsonNode p : reply.path("prompts")) {
                String name = clip(p.path("name").asText(), MAX_NAME);
                String prompt = withNegative(p.path("prompt").asText(""), p.path("negative_prompt").asText(""));
                if (!name.isEmpty() && prompt.length() >= 10) {
                    drafts.add(new Draft(name, prompt));
                }
            }
            return new Reply(drafts, reply.path("research_summary").asText("").trim(),
                    reply.path("recommendation").asText("").trim());
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

    /**
     * gpt-image has no negative-prompt input, so the negative prompt rides along at the end
     * as "Avoid: ...". The whole thing still fits a block's prompt limit.
     */
    private static String withNegative(String prompt, String negative) {
        String p = prompt.trim();
        String n = negative.trim();
        return clip(n.isEmpty() ? p : p + "\n\nAvoid: " + n, MAX_PROMPT);
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() <= max ? t : t.substring(0, max).trim();
    }
}
