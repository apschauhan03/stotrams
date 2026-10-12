package com.stotram.service.publish;

import com.stotram.model.AppSetting;
import com.stotram.repository.AppSettingRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, keeps every live image on the current watermark design:
 * <ul>
 *   <li>if {@link Watermarker#VERSION} changed since the last run, re-renders <b>all</b> live
 *       images from their clean originals (e.g. after moving the mark to the top-right);
 *   <li>otherwise only stamps live items that were never watermarked.
 * </ul>
 * The rendered version is remembered in {@code app_setting} only when every image succeeded,
 * so a partial failure is retried on the next start.
 */
@Component
public class WatermarkBackfill {

    static final String SETTING = "watermark_version";

    private final PublishService publishService;
    private final AppSettingRepository settings;

    public WatermarkBackfill(PublishService publishService, AppSettingRepository settings) {
        this.publishService = publishService;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        String rendered = settings.findById(SETTING).map(AppSetting::getValue).orElse(null);
        boolean designChanged = !Watermarker.VERSION.equals(rendered);
        boolean allOk = publishService.watermarkLiveItems(designChanged);
        if (designChanged && allOk) {
            AppSetting setting = settings.findById(SETTING).orElseGet(() -> new AppSetting(SETTING, Watermarker.VERSION));
            setting.setValue(Watermarker.VERSION);
            settings.save(setting);
        }
    }
}
