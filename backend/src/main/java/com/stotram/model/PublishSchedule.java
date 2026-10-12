package com.stotram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** When approved items get published. Exactly one row (id = 1); see V3 migration. */
@Entity
@Table(name = "publish_schedule")
@Getter
@Setter
@NoArgsConstructor
public class PublishSchedule {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private boolean enabled;

    /** Spring 6-field cron, evaluated in Asia/Kolkata. */
    @Column(nullable = false)
    private String cron;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_run_count")
    private Integer lastRunCount;

    /** SCHEDULE or MANUAL. */
    @Column(name = "last_run_trigger")
    private String lastRunTrigger;
}
