package com.stotram.service.generation;

import com.stotram.dto.BlockOptionsResponse;
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
 * Runs a generation batch: one block's N images, in parallel, off the request thread.
 *
 * <p>The shape is deliberate. {@link #startBlock} does only the cheap synchronous work —
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

    /** Guard-rail: a typo in a block's image count spends real money against a real provider. */
    public static final int MAX_COUNT = 50;

    /** Guard-rail for "Generate all": the most images one click may request in total. */
    public static final int MAX_TOTAL = 200;

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
     * Runs one block: opens a batch for {@code block.imageCount} images and starts generating.
     * Returns as soon as the batch row exists — the images are still being made.
     */
    public GenerationBatch startBlock(Long blockId) {
        PromptTemplate block = prompts.findById(blockId)
                .filter(b -> !b.isArchived())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No block with id " + blockId));
        return start(block);
    }

    /** Runs every active block. Refused as a whole if the total is over {@link #MAX_TOTAL}. */
    public List<GenerationBatch> startAllActive() {
        List<PromptTemplate> blocks = prompts.findByArchivedFalseAndActiveTrueOrderBySortOrderAscIdAsc();
        if (blocks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No active blocks to generate");
        }
        int total = blocks.stream().mapToInt(PromptTemplate::getImageCount).sum();
        if (total > MAX_TOTAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Active blocks add up to %d images; one run may make at most %d".formatted(total, MAX_TOTAL));
        }
        // Check every block's provider first, so one that can't run fails the whole request
        // before any money is spent on the others.
        blocks.forEach(b -> requireReady(b, resolveGenerator(b.getModel())));
        return blocks.stream().map(this::start).toList();
    }

    /**
     * Every model a block can pick, for the admin dropdown: paid providers first (the real
     * thing), free test providers last. A provider without a model list is one entry.
     */
    public List<BlockOptionsResponse.ModelOption> modelOptions() {
        return generators.values().stream()
                .sorted(java.util.Comparator.comparing((ImageGenerator g) -> !g.paid())
                        .thenComparing(ImageGenerator::providerId))
                .flatMap(g -> {
                    String reason = g.unavailableReason().orElse(null);
                    if (g.models().isEmpty()) {
                        return java.util.stream.Stream.of(new BlockOptionsResponse.ModelOption(
                                g.providerId(), g.displayName(), g.paid(), reason == null, reason, g.supportsEdit()));
                    }
                    return g.models().stream().map(m -> new BlockOptionsResponse.ModelOption(
                            g.providerId() + ":" + m, g.displayName() + " · " + m, g.paid(), reason == null, reason,
                            g.supportsEdit()));
                })
                .toList();
    }

    /** The provider a {@code provider:model} value selects; 400 if none is installed. */
    public ImageGenerator generatorFor(String model) {
        validateModel(model);
        return resolveGenerator(model);
    }

    /** Throws 400 when {@code model} names no installed provider. */
    public void validateModel(String model) {
        try {
            resolveGenerator(model);
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getReason());
        }
    }

    private GenerationBatch start(PromptTemplate block) {
        int count = block.getImageCount();
        if (count < 1 || count > MAX_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Image count must be between 1 and " + MAX_COUNT);
        }
        ImageGenerator generator = resolveGenerator(block.getModel());
        requireReady(block, generator);

        GenerationBatch batch = new GenerationBatch();
        batch.setPromptTemplate(block);
        batch.setRequestedCount(count);
        batches.save(batch);

        log.info("Batch {}: generating {} image(s) for block '{}' ({}) via provider '{}'",
                batch.getId(), count, block.getName(), block.getCategory().getSlug(), generator.providerId());

        fanOut(batch.getId(), block.getCategory().getId(), block, generator, count, block.getLanguage());
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

    /** Refuses (409) to start a block whose provider can't run, e.g. OpenAI with no key. */
    private static void requireReady(PromptTemplate block, ImageGenerator generator) {
        generator.unavailableReason().ifPresent(reason -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Block \"%s\" can't run: %s".formatted(block.getName(), reason));
        });
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

    static String modelIdOf(String modelColumn) {
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
