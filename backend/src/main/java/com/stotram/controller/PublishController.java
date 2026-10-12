package com.stotram.controller;

import com.stotram.dto.PublishResponse;
import com.stotram.dto.PublishScheduleRequest;
import com.stotram.dto.PublishScheduleResponse;
import com.stotram.model.PublishSchedule;
import com.stotram.service.publish.PublishScheduler;
import com.stotram.service.publish.PublishService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Putting approved content live: immediately ("Publish now") or on the configurable
 * daily schedule. Login-protected by SecurityConfig like the rest of /admin.
 */
@RestController
@RequestMapping("/admin/publish")
public class PublishController {

    private final PublishService publishService;
    private final PublishScheduler scheduler;

    public PublishController(PublishService publishService, PublishScheduler scheduler) {
        this.publishService = publishService;
        this.scheduler = scheduler;
    }

    /** Publish every APPROVED item now. */
    @PostMapping
    public PublishResponse publishNow() {
        PublishService.Result result = publishService.publishApproved(PublishService.Trigger.MANUAL);
        return new PublishResponse(result.published(), result.failed(), result.at());
    }

    @GetMapping("/schedule")
    public PublishScheduleResponse schedule() {
        return toResponse(scheduler.current());
    }

    @PutMapping("/schedule")
    public PublishScheduleResponse updateSchedule(@RequestBody(required = false) PublishScheduleRequest body) {
        if (body == null || body.enabled() == null || body.cron() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both 'enabled' and 'cron' are required");
        }
        return toResponse(scheduler.update(body.enabled(), body.cron()));
    }

    private PublishScheduleResponse toResponse(PublishSchedule s) {
        return new PublishScheduleResponse(
                s.isEnabled(),
                s.getCron(),
                PublishService.FEED_ZONE.getId(),
                scheduler.nextRun(s).map(next -> next.toOffsetDateTime()).orElse(null),
                s.getLastRunAt(),
                s.getLastRunCount(),
                s.getLastRunTrigger());
    }
}
