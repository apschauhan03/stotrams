package com.stotram.service.publish;

import com.stotram.model.PublishSchedule;
import com.stotram.repository.PublishScheduleRepository;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs {@link PublishService#publishApproved} on the cron stored in {@code publish_schedule}.
 *
 * <p>Not {@code @Scheduled}: that fixes the schedule at startup, and this one is edited from
 * the admin panel. Instead the job is registered on a {@link TaskScheduler} and, when the
 * schedule changes, the old registration is cancelled and a new one made — no restart.
 *
 * <p>Single-server assumption: with two app instances, both would publish. Fine today (one
 * VPS); needs a DB lock (e.g. ShedLock) if that ever changes.
 */
@Component
public class PublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishScheduler.class);

    private final TaskScheduler taskScheduler;
    private final PublishService publishService;
    private final PublishScheduleRepository schedules;

    private ScheduledFuture<?> job;

    public PublishScheduler(@Qualifier("publishTaskScheduler") TaskScheduler taskScheduler,
                            PublishService publishService,
                            PublishScheduleRepository schedules) {
        this.taskScheduler = taskScheduler;
        this.publishService = publishService;
        this.schedules = schedules;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        register(schedules.getSingleton());
    }

    public PublishSchedule current() {
        return schedules.getSingleton();
    }

    /** Saves a new schedule and re-registers the job immediately. */
    @Transactional
    public PublishSchedule update(boolean enabled, String cron) {
        String expression = cron == null ? "" : cron.trim();
        if (!CronExpression.isValidExpression(expression)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not a valid cron expression (6 fields: sec min hour day month weekday): " + expression);
        }
        PublishSchedule schedule = schedules.getSingleton();
        schedule.setEnabled(enabled);
        schedule.setCron(expression);
        register(schedule);
        return schedule;
    }

    /** When the job fires next, or empty when it's switched off. */
    public Optional<ZonedDateTime> nextRun(PublishSchedule schedule) {
        if (!schedule.isEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                CronExpression.parse(schedule.getCron()).next(ZonedDateTime.now(PublishService.FEED_ZONE)));
    }

    private synchronized void register(PublishSchedule schedule) {
        if (job != null) {
            job.cancel(false);      // let a run that's already in progress finish
            job = null;
        }
        if (!schedule.isEnabled()) {
            log.info("Auto-publish is off");
            return;
        }
        job = taskScheduler.schedule(this::runScheduled, new CronTrigger(schedule.getCron(), PublishService.FEED_ZONE));
        log.info("Auto-publish scheduled: '{}' ({}), next run {}",
                schedule.getCron(), PublishService.FEED_ZONE, nextRun(schedule).orElse(null));
    }

    private void runScheduled() {
        try {
            publishService.publishApproved(PublishService.Trigger.SCHEDULE);
        } catch (RuntimeException e) {
            // Never let one failure kill the schedule; the next tick tries again.
            log.error("Scheduled publish failed", e);
        }
    }
}
