package com.stotram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per pipeline run (scheduled or manual), with cost tracking. */
@Entity
@Table(name = "generation_batch")
@Getter
@Setter
@NoArgsConstructor
public class GenerationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "prompt_template_id")
    private PromptTemplate promptTemplate;

    @ManyToOne
    @JoinColumn(name = "admin_user_id")
    private AdminUser adminUser;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    /** Per-batch cost in USD, visible from day one. */
    @Column(name = "cost_usd")
    private BigDecimal costUsd;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
