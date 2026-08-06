package com.stotram.controller;

import com.stotram.dto.CandidateResponse;
import com.stotram.dto.GenerateResponse;
import com.stotram.model.GenerationBatch;
import com.stotram.service.AdminReviewService;
import com.stotram.service.generation.GenerationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API: the generate -> review -> publish workflow (README section 4).
 * Will sit behind Cloudflare Access + Spring Security later.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminReviewService reviewService;
    private final GenerationService generationService;

    public AdminController(AdminReviewService reviewService, GenerationService generationService) {
        this.reviewService = reviewService;
        this.generationService = generationService;
    }

    /** Thumbnails awaiting review; defaults to the GENERATED queue. */
    @GetMapping("/candidates")
    public List<CandidateResponse> candidates(
            @RequestParam(defaultValue = "GENERATED") String status) {
        return reviewService.candidates(status);
    }

    @PostMapping("/candidates/{id}/approve")
    public CandidateResponse approve(@PathVariable Long id) {
        return reviewService.approve(id);
    }

    @PostMapping("/candidates/{id}/reject")
    public CandidateResponse reject(@PathVariable Long id) {
        return reviewService.reject(id);
    }

    /**
     * Kick off a generation batch. Returns 202 as soon as the batch row exists — the images
     * are generated on a background pool and land in the review queue as they finish, so
     * poll {@code GET /candidates?status=GENERATED} to watch progress.
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(
            @RequestParam String category,
            @RequestParam(defaultValue = "25") int count,
            @RequestParam(defaultValue = "hi") String lang) {

        GenerationBatch batch = generationService.startBatch(category, count, lang);

        return ResponseEntity.accepted().body(new GenerateResponse(
                batch.getId(),
                category,
                count,
                "Generation started. Poll GET /admin/candidates?status=GENERATED to watch images land."));
    }

    /**
     * Publish all APPROVED items. Stubbed: needs watermark + WebP + R2 upload
     * + feed rebuild (deferred until we set up media/CDN).
     */
    @PostMapping("/publish")
    public ResponseEntity<String> publish() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body("publish not implemented yet: needs watermark + WebP + R2 upload");
    }
}
