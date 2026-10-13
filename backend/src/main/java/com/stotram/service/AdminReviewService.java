package com.stotram.service;

import com.stotram.dto.CandidateResponse;
import com.stotram.model.ContentItem;
import com.stotram.repository.ContentItemRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The human-review step: list generated candidates and approve/reject them.
 * This is the non-negotiable gate before anything is published (README section 2).
 *
 * <p>Approving queues an item for publishing (PublishService puts it live, on schedule or
 * via "Publish now"). Rejecting works from any state — including PUBLISHED, which takes the
 * item down from the public feed at once.
 */
@Service
public class AdminReviewService {

    private final ContentItemRepository repository;
    private final String mediaBaseUrl;

    public AdminReviewService(ContentItemRepository repository,
                              @Value("${app.media.base-url}") String mediaBaseUrl) {
        this.repository = repository;
        this.mediaBaseUrl = mediaBaseUrl.endsWith("/")
                ? mediaBaseUrl.substring(0, mediaBaseUrl.length() - 1)
                : mediaBaseUrl;
    }

    /** One review tab: items in {@code status} of one media {@code type} (IMAGE or VIDEO). */
    public List<CandidateResponse> candidates(String status, String type) {
        return repository.findByStatusAndTypeOrderByIdDesc(status, type).stream()
                .map(this::toCandidate)
                .toList();
    }

    @Transactional
    public CandidateResponse approve(Long id) {
        ContentItem item = require(id);
        if ("PUBLISHED".equals(item.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item " + id + " is already live");
        }
        item.setStatus("APPROVED");
        return toCandidate(item);
    }

    @Transactional
    public CandidateResponse reject(Long id) {
        ContentItem item = require(id);
        item.setStatus("REJECTED");
        item.setPublishedAt(null);      // taken down, if it was live
        return toCandidate(item);
    }

    private ContentItem require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No content item with id " + id));
    }

    public CandidateResponse toCandidate(ContentItem c) {
        return new CandidateResponse(
                c.getId(),
                c.getType().toLowerCase(),
                mediaBaseUrl + "/" + c.getR2Key(),
                c.getStatus(),
                c.getCategory().getSlug(),
                c.getLanguage(),
                c.getWidth(),
                c.getHeight(),
                c.getEditedFromId(),
                c.getEditPrompt());
    }
}
