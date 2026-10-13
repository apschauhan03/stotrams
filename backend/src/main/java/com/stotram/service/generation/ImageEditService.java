package com.stotram.service.generation;

import com.stotram.model.ContentItem;
import com.stotram.repository.ContentItemRepository;
import com.stotram.repository.GenerationBatchRepository;
import com.stotram.service.publish.PublishService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Prompt-based editing: "remove the text at the bottom", "make the sky more golden".
 *
 * <p>An edit never touches the source. The provider's output becomes a <b>new</b> candidate
 * in the review queue (GENERATED), linked back via {@code edited_from_id}, so the admin
 * compares and approves whichever is better — and it's watermarked at publish like any
 * other. Edits always start from the clean original, never a watermarked copy.
 *
 * <p>Synchronous on purpose (~30–60 s): the admin is waiting on the result and should see
 * success or the provider's error right away. The provider call still runs on the bounded
 * {@code imageExecutor}, so edits share the generation concurrency/spend ceiling.
 */
@Service
public class ImageEditService {

    private static final Logger log = LoggerFactory.getLogger(ImageEditService.class);

    private static final int MIN_INSTRUCTION = 3;
    private static final int MAX_INSTRUCTION = 2000;
    private static final long TIMEOUT_MINUTES = 5;

    private final ContentItemRepository items;
    private final GenerationBatchRepository batches;
    private final ImageStorage storage;
    private final GenerationService generation;
    private final Executor imageExecutor;
    private final String defaultModel;

    public ImageEditService(ContentItemRepository items,
                            GenerationBatchRepository batches,
                            ImageStorage storage,
                            GenerationService generation,
                            @Qualifier("imageExecutor") Executor imageExecutor,
                            @Value("${app.generation.edit-default-model:openai:gpt-image-1-mini}") String defaultModel) {
        this.items = items;
        this.batches = batches;
        this.storage = storage;
        this.generation = generation;
        this.imageExecutor = imageExecutor;
        this.defaultModel = defaultModel;
    }

    public String defaultModel() {
        return defaultModel;
    }

    /** Edits item {@code sourceId} and returns the new candidate. */
    public ContentItem edit(Long sourceId, String instruction, String model) {
        String text = instruction == null ? "" : instruction.trim();
        if (text.length() < MIN_INSTRUCTION || text.length() > MAX_INSTRUCTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Describe the change (%d–%d characters)".formatted(MIN_INSTRUCTION, MAX_INSTRUCTION));
        }
        ContentItem source = items.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No content item with id " + sourceId));
        if (!"IMAGE".equals(source.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only images can be edited with a prompt");
        }

        String modelValue = model == null || model.isBlank() ? defaultModel : model.trim();
        ImageGenerator generator = generation.generatorFor(modelValue);
        if (!generator.supportsEdit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model " + modelValue + " can't edit images");
        }
        generator.unavailableReason().ifPresent(reason -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Can't edit right now: " + reason);
        });

        String sourceKey = source.getOriginalKey() != null ? source.getOriginalKey() : source.getR2Key();
        byte[] sourceBytes = storage.read(sourceKey);
        var request = new ImageEditRequest(sourceBytes, contentTypeOf(sourceKey), text,
                GenerationService.modelIdOf(modelValue));

        log.info("Editing #{} via {}: \"{}\"", sourceId, modelValue, text);
        GeneratedImage result;
        try {
            result = CompletableFuture.supplyAsync(() -> generator.edit(request), imageExecutor)
                    .get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            String reason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.warn("Edit of #{} failed: {}", sourceId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Edit failed: " + reason);
        } catch (TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Edit took too long; try again");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Edit interrupted");
        }

        LocalDate today = LocalDate.now(PublishService.FEED_ZONE);
        String key = "images/%s/%s/%s.%s".formatted(source.getCategory().getSlug(), today, UUID.randomUUID(),
                result.contentType().endsWith("jpeg") ? "jpg" : result.contentType().substring(6));
        storage.store(key, result.bytes());

        ContentItem copy = new ContentItem();
        copy.setBatch(source.getBatch());
        copy.setCategory(source.getCategory());
        copy.setType("IMAGE");
        copy.setStatus("GENERATED");             // back through human review, like any image
        copy.setLanguage(source.getLanguage());
        copy.setR2Key(key);
        copy.setWidth(result.width());
        copy.setHeight(result.height());
        copy.setPublishDate(today);
        copy.setEditedFromId(source.getId());
        copy.setEditPrompt(text);
        items.save(copy);

        BigDecimal cost = result.costUsd() == null ? BigDecimal.ZERO : result.costUsd();
        if (source.getBatch() != null && cost.signum() > 0) {
            batches.findById(source.getBatch().getId()).ifPresent(batch -> {
                batch.setCostUsd((batch.getCostUsd() == null ? BigDecimal.ZERO : batch.getCostUsd()).add(cost));
                batches.save(batch);
            });
        }
        log.info("Edit of #{} done -> new candidate #{}", sourceId, copy.getId());
        return copy;
    }

    private static String contentTypeOf(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".jpg") || k.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (k.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
}
