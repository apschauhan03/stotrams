package com.stotram.service.generation;

import com.stotram.model.Category;
import com.stotram.model.ContentItem;
import com.stotram.model.GenerationBatch;
import com.stotram.model.PromptTemplate;
import com.stotram.repository.CategoryRepository;
import com.stotram.repository.ContentItemRepository;
import com.stotram.repository.GenerationBatchRepository;
import com.stotram.repository.PromptTemplateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs a generation batch: N images for one category, in parallel, off the request thread.
 *
 * <p>The shape is deliberate. {@link #startBatch} does only the cheap synchronous work —
 * validate, open a {@code generation_batch} row — then returns the batch id so the caller
 * can answer 202 immediately. The actual generation fans out onto {@code imageExecutor}
 * and finishes long after the HTTP response is gone; progress is observed through
 * {@code GET /admin/candidates}, where rows appear as they land.
 *
 * <p>Each image is its own unit of work and its own transaction. Image 13 failing must not
 * roll back the twelve that already succeeded, so there is no batch-wide transaction here.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    /** Guard-rail: a typo in {@code count} spends real money against a real provider. */
    private static final int MAX_COUNT = 50;

    private final CategoryRepository categories;
    private final PromptTemplateRepository prompts;
    private final GenerationBatchRepository batches;
    private final ContentItemRepository items;
    private final ImageStorage storage;
    private final Executor imageExecutor;
    private final Map<String, ImageGenerator> generators;
    private final int width;
    private final int height;

    public GenerationService(CategoryRepository categories,
                             PromptTemplateRepository prompts,
                             GenerationBatchRepository batches,
                             ContentItemRepository items,
                             ImageStorage storage,
                             @Qualifier("imageExecutor") Executor imageExecutor,
                             List<ImageGenerator> generators,
                             @Value("${app.generation.width:1080}") int width,
                             @Value("${app.generation.height:1350}") int height) {
        this.categories = categories;
        this.prompts = prompts;
        this.batches = batches;
        this.items = items;
        this.storage = storage;
        this.imageExecutor = imageExecutor;
        this.width = width;
        this.height = height;
        // Spring hands us every ImageGenerator on the classpath; index them by id so
        // prompt_template.model can select one per category.
        this.generators = generators.stream()
                .collect(java.util.stream.Collectors.toMap(ImageGenerator::providerId, Function.identity()));
        log.info("Image providers available: {}", this.generators.keySet());
    }

    /**
     * Opens a batch and starts generating. Returns as soon as the batch row exists —
     * the images are still being made when this returns.
     */
    public GenerationBatch startBatch(String categorySlug, int count, String language) {
        if (count < 1 || count > MAX_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "count must be between 1 and " + MAX_COUNT);
        }

        Category category = categories.findBySlug(categorySlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No category with slug " + categorySlug));

        PromptTemplate template = prompts.findByCategory_SlugAndActiveTrue(categorySlug).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No active prompt template for category " + categorySlug));

        ImageGenerator generator = resolveGenerator(template.getModel());

        GenerationBatch batch = new GenerationBatch();
        batch.setPromptTemplate(template);
        batch.setRequestedCount(count);
        batches.save(batch);

        log.info("Batch {}: generating {} image(s) for '{}' via provider '{}'",
                batch.getId(), count, categorySlug, generator.providerId());

        fanOut(batch.getId(), category.getId(), template, generator, count, language);
        return batch;
    }

    /** Submits one task per image and tallies the batch when the last one lands. */
    private void fanOut(Long batchId, Long categoryId, PromptTemplate template,
                        ImageGenerator generator, int count, String language) {

        String prompt = template.getTemplateText();
        String model = modelIdOf(template.getModel());

        List<CompletableFuture<BigDecimal>> tasks = IntStream.range(0, count)
                .mapToObj(i -> CompletableFuture
                        .supplyAsync(
                                () -> generateOne(batchId, categoryId, generator, prompt, model, language),
                                imageExecutor)          // never the common ForkJoinPool
                        .exceptionally(ex -> {
                            // One image died. Log it, return null, let the rest finish.
                            log.warn("Batch {}: image {}/{} failed: {}",
                                    batchId, i + 1, count, ex.getMessage());
                            return null;
                        }))
                .toList();

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, error) -> finalizeBatch(batchId, count, tasks));
    }

    /**
     * Generates, stores, and records a single image. Runs on {@code imageExecutor}.
     *
     * @return what this image cost, for the batch tally
     */
    private BigDecimal generateOne(Long batchId, Long categoryId, ImageGenerator generator,
                                   String prompt, String model, String language) {

        GeneratedImage generated = generator.generate(new ImageRequest(prompt, model, width, height));

        Category category = categories.findById(categoryId).orElseThrow();
        LocalDate today = LocalDate.now();
        String key = "images/%s/%s/%s.%s".formatted(
                category.getSlug(), today, UUID.randomUUID(), extensionOf(generated.contentType()));

        storage.store(key, generated.bytes());

        ContentItem item = new ContentItem();
        item.setBatch(batches.findById(batchId).orElseThrow());
        item.setCategory(category);
        item.setType("IMAGE");
        item.setStatus("GENERATED");          // the human-review gate; nothing is public yet
        item.setLanguage(language);
        item.setR2Key(key);
        item.setWidth(generated.width());
        item.setHeight(generated.height());
        item.setPublishDate(today);
        items.save(item);

        log.debug("Batch {}: stored {}", batchId, key);
        return generated.costUsd() == null ? BigDecimal.ZERO : generated.costUsd();
    }

    /** Sums cost across the batch once every image has settled. */
    private void finalizeBatch(Long batchId, int requested, List<CompletableFuture<BigDecimal>> tasks) {
        List<BigDecimal> costs = tasks.stream()
                .map(task -> task.getNow(null))     // safe: allOf has completed
                .filter(Objects::nonNull)
                .toList();

        BigDecimal total = costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        batches.findById(batchId).ifPresent(batch -> {
            batch.setCostUsd(total);
            batches.save(batch);
        });

        log.info("Batch {} complete: {}/{} image(s) generated, cost ${}",
                batchId, costs.size(), requested, total);
    }

    /**
     * {@code prompt_template.model} carries {@code provider:model-id}, e.g.
     * {@code bedrock:amazon.nova-canvas-v1:0}. Split on the first colon only — vendor model
     * ids contain colons of their own. A bare value like {@code fake} is both.
     */
    private ImageGenerator resolveGenerator(String modelColumn) {
        String providerId = providerIdOf(modelColumn);
        ImageGenerator generator = generators.get(providerId);
        if (generator == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No image provider '%s' (available: %s)".formatted(providerId, generators.keySet()));
        }
        return generator;
    }

    private String providerIdOf(String modelColumn) {
        if (modelColumn == null || modelColumn.isBlank()) {
            return "fake";
        }
        int colon = modelColumn.indexOf(':');
        return colon < 0 ? modelColumn.trim() : modelColumn.substring(0, colon).trim();
    }

    private String modelIdOf(String modelColumn) {
        if (modelColumn == null || modelColumn.isBlank()) {
            return "fake";
        }
        int colon = modelColumn.indexOf(':');
        return colon < 0 ? modelColumn.trim() : modelColumn.substring(colon + 1).trim();
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }
}
