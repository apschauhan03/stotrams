package com.stotram.repository;

import com.stotram.model.PublishSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishScheduleRepository extends JpaRepository<PublishSchedule, Short> {

    default PublishSchedule getSingleton() {
        return findById(PublishSchedule.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("publish_schedule row missing (V3 migration)"));
    }
}
