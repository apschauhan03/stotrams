package com.stotram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "content_item")
@Getter
@Setter
@NoArgsConstructor
public class ContentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The pipeline run that produced this item; null for seeded/manual items. */
    @ManyToOne
    @JoinColumn(name = "batch_id")
    private GenerationBatch batch;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** IMAGE now; VIDEO later. */
    @Column(nullable = false)
    private String type = "IMAGE";

    /** GENERATED -> APPROVED -> PUBLISHED (or REJECTED). */
    @Column(nullable = false)
    private String status = "GENERATED";

    @Column(nullable = false)
    private String language;

    /** Object key; public url = {media.base-url}/{r2Key}. */
    @Column(name = "r2_key", nullable = false, unique = true)
    private String r2Key;

    private Integer width;

    private Integer height;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
