package com.stotram.controller;

import com.stotram.dto.CandidateResponse;
import com.stotram.dto.EditRequest;
import com.stotram.service.AdminReviewService;
import com.stotram.service.generation.ImageEditService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API: the review queue. Generation lives in {@link BlockController}, publishing in
 * {@link PublishController}. Login-protected by SecurityConfig.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminReviewService reviewService;
    private final ImageEditService editService;

    public AdminController(AdminReviewService reviewService, ImageEditService editService) {
        this.reviewService = reviewService;
        this.editService = editService;
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
     * Edit an image with a prompt. Waits for the provider (~30–60 s) and returns the NEW
     * candidate — the source is left untouched. Costs money with a real provider.
     */
    @PostMapping("/candidates/{id}/edit")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateResponse edit(@PathVariable Long id, @RequestBody(required = false) EditRequest body) {
        var copy = editService.edit(id, body == null ? null : body.prompt(), body == null ? null : body.model());
        return reviewService.toCandidate(copy);
    }
}
