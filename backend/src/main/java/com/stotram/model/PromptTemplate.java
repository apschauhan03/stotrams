package com.stotram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A generation <b>block</b> (admin-facing name): a named prompt with its category, language,
 * image model and how many images one run makes. Managed from the admin panel via
 * /admin/blocks — never edited in code or by hand-written SQL.
 */
@Entity
@Table(name = "prompt_template")
@Getter
@Setter
@NoArgsConstructor
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** hi, en — recorded on every image this block generates. */
    @Column(nullable = false)
    private String language = "hi";

    @Column(name = "template_text", nullable = false, columnDefinition = "text")
    private String templateText;

    /** {@code provider:model-id}, e.g. {@code openai:gpt-image-2}, or {@code fake}. */
    @Column(name = "model")
    private String model;

    /** Images per run of this block (1–50). */
    @Column(name = "image_count", nullable = false)
    private int imageCount = 10;

    /** Active blocks are included in "Generate all". Paused ones are kept but skipped. */
    @Column(nullable = false)
    private boolean active = true;

    /** Deleted from the panel. Kept because past batches reference it. */
    @Column(nullable = false)
    private boolean archived;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
