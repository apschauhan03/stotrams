package com.stotram.controller;

import com.stotram.dto.CandidateResponse;
import com.stotram.service.AdminReviewService;
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

    public AdminController(AdminReviewService reviewService) {
        this.reviewService = reviewService;
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
     * Kick off a generation batch. Stubbed: needs the AI image client
     * (deferred with the pipeline). Endpoint exists so the frontend can wire to it.
     */
    @PostMapping("/generate")
    public ResponseEntity<String> generate(
            @RequestParam String category,
            @RequestParam(defaultValue = "25") int count) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body("generate not implemented yet: needs the AI image client");
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
