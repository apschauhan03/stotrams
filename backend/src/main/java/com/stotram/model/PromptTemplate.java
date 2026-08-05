package com.stotram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generation prompt per category, editable from the admin panel (not in code). */
@Entity
@Table(name = "prompt_template")
@Getter
@Setter
@NoArgsConstructor
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "template_text", nullable = false, columnDefinition = "text")
    private String templateText;

    /** Which image model this prompt targets. */
    @Column(name = "model")
    private String model;

    @Column(nullable = false)
    private boolean active = true;
}
