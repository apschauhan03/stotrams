package com.stotram.service.generation.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stotram.service.generation.GeneratedImage;
import com.stotram.service.generation.ImageGenerationException;
import com.stotram.service.generation.ImageGenerator;
import com.stotram.service.generation.ImageRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Generates images through OpenAI's images API.
 *
 * <p>Selected per category by {@code prompt_template.model} — set it to
 * {@code openai:gpt-image-2} (the part after the first colon is the model id passed
 * through to the API, so {@code gpt-image-1.5}, {@code gpt-image-1-mini} etc. work
 * without a code change).
 *
 * <p>The API key comes from the {@code OPENAI_API_KEY} environment variable and is never
 * written to a file in this repo — the repository is public, and a key committed to it
 * would be scraped within minutes.
 */
@Component
public class OpenAiImageGenerator implements ImageGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiImageGenerator.class);

    private final RestClient client;
    private final String apiKey;
    private final String size;
    private final String quality;
    private final String outputFormat;
    private final BigDecimal costPerImage;

    public OpenAiImageGenerator(
            @Value("${app.generation.openai.api-key:}") String apiKey,
            @Value("${app.generation.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.generation.openai.size:1024x1536}") String size,
            @Value("${app.generation.openai.quality:medium}") String quality,
            @Value("${app.generation.openai.output-format:png}") String outputFormat,
            @Value("${app.generation.openai.cost-per-image:0}") BigDecimal costPerImage) {

        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.size = size;
        this.quality = quality;
        this.outputFormat = outputFormat;
        this.costPerImage = costPerImage;

        // Image generation is slow — a high-quality render can take a minute or more — so the
        // read timeout is generous. The connect timeout stays short: failing to reach the API
        // at all should surface immediately rather than tie up a pool thread.
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofMinutes(3));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        if (this.apiKey.isBlank()) {
            log.warn("OpenAI provider registered without a key. Set OPENAI_API_KEY to use it; "
                    + "batches targeting 'openai' will fail until then.");
        }
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public GeneratedImage generate(ImageRequest request) {
        if (apiKey.isBlank()) {
            throw new ImageGenerationException(
                    "OPENAI_API_KEY is not set — export it and restart, or point the category's "
                            + "prompt_template.model at another provider.");
        }

        // No response_format here: the gpt-image-* models reject it outright
        // ("Unknown parameter: 'response_format'") and always return base64 inline.
        // It only applies to the older DALL·E models.
        Map<String, Object> body = Map.of(
                "model", request.model(),
                "prompt", request.prompt(),
                "size", size,
                "quality", quality,
                "output_format", outputFormat,
                "n", 1);

        ImagesResponse response;
        try {
            response = client.post()
                    .uri("/images/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        // Surface OpenAI's own message — "billing hard limit reached" and
                        // "organization must be verified" are the two that actually happen,
                        // and a bare status code sends you hunting for no reason.
                        String detail = new String(res.getBody().readAllBytes()).trim();
                        throw new ImageGenerationException(
                                "OpenAI returned %s: %s".formatted(res.getStatusCode(), detail));
                    })
                    .body(ImagesResponse.class);
        } catch (ImageGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageGenerationException("OpenAI request failed: " + e.getMessage(), e);
        }

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new ImageGenerationException("OpenAI returned no image data");
        }

        String b64 = response.data().get(0).b64Json();
        if (b64 == null || b64.isBlank()) {
            throw new ImageGenerationException("OpenAI returned no b64_json payload");
        }

        byte[] bytes = Base64.getDecoder().decode(b64);
        int[] dims = parseSize();

        return new GeneratedImage(
                bytes, "image/" + normalisedFormat(), dims[0], dims[1], costPerImage);
    }

    /**
     * The API echoes no dimensions, but it honours the requested size, so parse it back.
     * Note this is the size OpenAI produced, not the app's target 1080x1350 — the resize
     * happens later, alongside watermarking.
     */
    private int[] parseSize() {
        try {
            String[] parts = size.toLowerCase().split("x");
            return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (RuntimeException e) {
            throw new ImageGenerationException("Unparseable app.generation.openai.size: " + size, e);
        }
    }

    private String normalisedFormat() {
        return "jpg".equalsIgnoreCase(outputFormat) ? "jpeg" : outputFormat.toLowerCase();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImagesResponse(List<ImageData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImageData(
            @JsonProperty("b64_json") String b64Json,
            @JsonProperty("revised_prompt") String revisedPrompt) {
    }
}
