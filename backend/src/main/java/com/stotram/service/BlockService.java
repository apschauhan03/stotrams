package com.stotram.service;

import com.stotram.dto.BlockOptionsResponse;
import com.stotram.dto.BlockRequest;
import com.stotram.dto.BlockResponse;
import com.stotram.dto.PrincipleDtos;
import com.stotram.model.Category;
import com.stotram.model.GenerationBatch;
import com.stotram.model.PromptTemplate;
import com.stotram.repository.CategoryRepository;
import com.stotram.repository.GenerationBatchRepository;
import com.stotram.repository.PromptTemplateRepository;
import com.stotram.service.generation.GenerationService;
import com.stotram.service.generation.ImageEditService;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Create / edit / delete generation blocks from the admin panel. A block is a
 * {@link PromptTemplate} row: name, category, language, prompt, image model, image count.
 */
@Service
public class BlockService {

    private static final Set<String> LANGUAGES = Set.of("hi", "en");
    private static final int MAX_NAME = 128;
    private static final int MIN_PROMPT = 10;
    private static final int MAX_PROMPT = 4000;
    private static final int MAX_BULK = 50;

    private final PromptTemplateRepository blocks;
    private final CategoryRepository categories;
    private final GenerationBatchRepository batches;
    private final GenerationService generation;
    private final ImageEditService editService;

    public BlockService(PromptTemplateRepository blocks,
                        CategoryRepository categories,
                        GenerationBatchRepository batches,
                        GenerationService generation,
                        ImageEditService editService) {
        this.blocks = blocks;
        this.categories = categories;
        this.batches = batches;
        this.generation = generation;
        this.editService = editService;
    }

    public List<BlockResponse> list() {
        return blocks.findByArchivedFalseOrderBySortOrderAscIdAsc().stream().map(this::toResponse).toList();
    }

    public BlockOptionsResponse options() {
        var categoryOptions = categories.findAll().stream()
                .filter(Category::isActive)
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(c -> new BlockOptionsResponse.CategoryOption(c.getSlug(), c.getNameEn()))
                .toList();
        return new BlockOptionsResponse(
                categoryOptions,
                LANGUAGES.stream().sorted().toList(),
                generation.modelOptions(),
                GenerationService.MAX_COUNT,
                GenerationService.MAX_TOTAL,
                editService.defaultModel());
    }

    @Transactional
    public BlockResponse create(BlockRequest request) {
        PromptTemplate block = new PromptTemplate();
        apply(block, request);
        block.setSortOrder(blocks.maxSortOrder() + 1);   // new blocks go to the end
        return toResponse(blocks.save(block));
    }

    /**
     * Creates many blocks with shared settings in one go (the principle-prompt flow).
     * All or nothing: one invalid item rejects the whole request.
     */
    @Transactional
    public List<BlockResponse> createMany(PrincipleDtos.BulkBlocksRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw badRequest("No blocks to create");
        }
        if (request.items().size() > MAX_BULK) {
            throw badRequest("At most " + MAX_BULK + " blocks at once");
        }
        int nextOrder = blocks.maxSortOrder() + 1;
        List<BlockResponse> created = new java.util.ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            var item = request.items().get(i);
            PromptTemplate block = new PromptTemplate();
            try {
                apply(block, new BlockRequest(item.name(), request.category(), request.language(), item.prompt(),
                        request.model(), request.imageCount(), request.active()));
            } catch (ResponseStatusException e) {
                throw badRequest("Block " + (i + 1) + ": " + e.getReason());
            }
            block.setSortOrder(nextOrder++);
            created.add(toResponse(blocks.save(block)));
        }
        return created;
    }

    @Transactional
    public BlockResponse update(Long id, BlockRequest request) {
        PromptTemplate block = require(id);
        apply(block, request);
        return toResponse(blocks.save(block));
    }

    /** Deleting archives: past batches keep pointing at the block for cost history. */
    @Transactional
    public void delete(Long id) {
        PromptTemplate block = require(id);
        block.setArchived(true);
        block.setActive(false);
    }

    private void apply(PromptTemplate block, BlockRequest r) {
        if (r == null) {
            throw badRequest("Request body is required");
        }
        String name = trim(r.name());
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw badRequest("Name is required (up to " + MAX_NAME + " characters)");
        }
        Category category = categories.findBySlug(trim(r.category()))
                .filter(Category::isActive)
                .orElseThrow(() -> badRequest("Unknown category: " + r.category()));
        String language = trim(r.language());
        if (!LANGUAGES.contains(language)) {
            throw badRequest("Language must be one of " + LANGUAGES);
        }
        String prompt = trim(r.prompt());
        if (prompt.length() < MIN_PROMPT || prompt.length() > MAX_PROMPT) {
            throw badRequest("Prompt must be %d–%d characters".formatted(MIN_PROMPT, MAX_PROMPT));
        }
        String model = trim(r.model());
        generation.validateModel(model);
        Integer count = r.imageCount();
        if (count == null || count < 1 || count > GenerationService.MAX_COUNT) {
            throw badRequest("Image count must be between 1 and " + GenerationService.MAX_COUNT);
        }

        block.setName(name);
        block.setCategory(category);
        block.setLanguage(language);
        block.setTemplateText(prompt);
        block.setModel(model);
        block.setImageCount(count);
        block.setActive(r.active() == null || r.active());
    }

    private PromptTemplate require(Long id) {
        return blocks.findById(id)
                .filter(b -> !b.isArchived())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No block with id " + id));
    }

    private BlockResponse toResponse(PromptTemplate b) {
        var lastRun = batches.findFirstByPromptTemplate_IdOrderByCreatedAtDesc(b.getId())
                .map(GenerationBatch::getCreatedAt)
                .orElse(null);
        return new BlockResponse(
                b.getId(),
                b.getName(),
                b.getCategory().getSlug(),
                b.getLanguage(),
                b.getTemplateText(),
                b.getModel(),
                b.getImageCount(),
                b.isActive(),
                b.getSortOrder(),
                lastRun,
                b.getUpdatedAt());
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
