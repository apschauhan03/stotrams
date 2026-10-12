package com.stotram.controller;

import com.stotram.dto.BlockOptionsResponse;
import com.stotram.dto.BlockRequest;
import com.stotram.dto.BlockResponse;
import com.stotram.dto.GenerateResponse;
import com.stotram.dto.PrincipleDtos;
import com.stotram.model.GenerationBatch;
import com.stotram.service.BlockService;
import com.stotram.service.generation.GenerationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generation blocks: the prompts, managed from the admin panel, and running them.
 * Generating returns 202 at once; images land in the review queue as they finish.
 * Login-protected by SecurityConfig like the rest of /admin.
 */
@RestController
@RequestMapping("/admin/blocks")
public class BlockController {

    private final BlockService blocks;
    private final GenerationService generation;

    public BlockController(BlockService blocks, GenerationService generation) {
        this.blocks = blocks;
        this.generation = generation;
    }

    @GetMapping
    public List<BlockResponse> list() {
        return blocks.list();
    }

    @GetMapping("/options")
    public BlockOptionsResponse options() {
        return blocks.options();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockResponse create(@RequestBody(required = false) BlockRequest body) {
        return blocks.create(body);
    }

    /** Create many blocks at once — the principle-prompt drafts, with shared settings. */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<BlockResponse> createMany(@RequestBody(required = false) PrincipleDtos.BulkBlocksRequest body) {
        return blocks.createMany(body);
    }

    @PutMapping("/{id}")
    public BlockResponse update(@PathVariable Long id, @RequestBody(required = false) BlockRequest body) {
        return blocks.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        blocks.delete(id);
    }

    /** Generate this block's images now. */
    @PostMapping("/{id}/generate")
    public ResponseEntity<GenerateResponse> generate(@PathVariable Long id) {
        return ResponseEntity.accepted().body(toResponse(generation.startBlock(id)));
    }

    /** Generate every active block now. */
    @PostMapping("/generate-all")
    public ResponseEntity<List<GenerateResponse>> generateAll() {
        return ResponseEntity.accepted().body(generation.startAllActive().stream().map(this::toResponse).toList());
    }

    private GenerateResponse toResponse(GenerationBatch batch) {
        var block = batch.getPromptTemplate();
        return new GenerateResponse(
                batch.getId(),
                block.getId(),
                block.getName(),
                block.getCategory().getSlug(),
                batch.getRequestedCount(),
                "Generation started. Images land in the review queue as they finish.");
    }
}
