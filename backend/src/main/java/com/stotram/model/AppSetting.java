package com.stotram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One admin-editable text setting (see V5 migration). */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {

    public static final String PRINCIPLE_PROMPT = "principle_prompt";

    @Id
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
