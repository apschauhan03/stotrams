package com.stotram.controller;

import com.stotram.dto.PrincipleDtos.Draft;
import com.stotram.dto.PrincipleDtos.PrincipleRequest;
import com.stotram.dto.PrincipleDtos.PrincipleResponse;
import com.stotram.dto.PrincipleDtos.WriteRequest;
import com.stotram.dto.PrincipleDtos.WriteResponse;
import com.stotram.model.AppSetting;
import com.stotram.model.Category;
import com.stotram.repository.AppSettingRepository;
import com.stotram.repository.CategoryRepository;
import com.stotram.service.prompting.DateContext;
import com.stotram.service.prompting.PromptWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The principle prompt: one saved master instruction that an LLM expands into many block
 * prompts. Writing returns drafts only; the admin reviews them and creates the blocks with
 * POST /admin/blocks/bulk. Login-protected by SecurityConfig like the rest of /admin.
 */
@RestController
@RequestMapping("/admin/principle")
public class PrincipleController {

    private static final int MIN_PRINCIPLE = 10;
    private static final int MAX_PRINCIPLE = 30_000;

    private final AppSettingRepository settings;
    private final CategoryRepository categories;
    private final PromptWriter writer;
    private final DateContext dateContext;
    private final String defaultPrompt;

    public PrincipleController(AppSettingRepository settings, CategoryRepository categories,
                               PromptWriter writer, DateContext dateContext,
                               @Value("classpath:prompts/master-prompt.txt") Resource defaultPrompt) throws IOException {
        this.settings = settings;
        this.categories = categories;
        this.writer = writer;
        this.dateContext = dateContext;
        this.defaultPrompt = defaultPrompt.getContentAsString(StandardCharsets.UTF_8).trim();
    }

    /** The saved master prompt, or the built-in default (prompts/master-prompt.txt) if none is saved. */
    @GetMapping
    public PrincipleResponse get() {
        return settings.findById(AppSetting.PRINCIPLE_PROMPT)
                .map(s -> new PrincipleResponse(s.getValue(), s.getUpdatedAt(), writer.model(),
                        s.getValue().equals(defaultPrompt)))
                .orElse(new PrincipleResponse(defaultPrompt, null, writer.model(), true));
    }

    @PutMapping
    @Transactional
    public PrincipleResponse save(@RequestBody(required = false) PrincipleRequest body) {
        AppSetting saved = store(body == null ? null : body.prompt());
        return new PrincipleResponse(saved.getValue(), saved.getUpdatedAt(), writer.model(),
                saved.getValue().equals(defaultPrompt));
    }

    /** Forgets the saved master prompt, so the built-in default is used again. */
    @DeleteMapping
    @Transactional
    public PrincipleResponse reset() {
        settings.deleteById(AppSetting.PRINCIPLE_PROMPT);
        return new PrincipleResponse(defaultPrompt, null, writer.model(), true);
    }

    /** Saves the principle, then asks the text model for {@code count} draft prompts. */
    @PostMapping("/write")
    public WriteResponse write(@RequestBody(required = false) WriteRequest body) {
        if (body == null) {
            throw badRequest("Request body is required");
        }
        int count = body.count() == null ? 0 : body.count();
        if (count < 1 || count > PromptWriter.MAX_COUNT) {
            throw badRequest("Number of prompts must be between 1 and " + PromptWriter.MAX_COUNT);
        }
        LocalDate date = parseDate(body.date());           // before saving: a bad date changes nothing
        String principle = fillPlaceholders(store(body.prompt()).getValue(), date, count);
        String context = categories.findBySlug(body.category() == null ? "" : body.category().trim())
                .map(PrincipleController::describe)
                .orElse("Category: any.");
        DateContext.Result dated = date == null ? null : dateContext.build(date, Boolean.TRUE.equals(body.checkWeb()));
        if (dated != null) {
            context += "\n" + dated.context();
        }

        PromptWriter.Result written = writer.write(principle, count, context);
        var drafts = written.drafts().stream()
                .map(d -> new Draft(d.name(), d.prompt()))
                .toList();
        return new WriteResponse(drafts, writer.model(), dated == null ? null : dated.check(),
                written.researchSummary(), written.recommendation());
    }

    /**
     * The saved master prompt may say {{TARGET_DATE}} and {{NUMBER_OF_PROMPTS}}; they're
     * filled in for this run only (the saved text keeps the placeholders). Spacing and case
     * inside the braces don't matter. With no date, {{TARGET_DATE}} says so plainly.
     */
    static String fillPlaceholders(String principle, LocalDate date, int count) {
        return TARGET_DATE.matcher(principle)
                .replaceAll(Matcher.quoteReplacement(date == null ? "(no date given)" : date.format(DateContext.PROMPT_DATE)))
                .transform(s -> NUMBER_OF_PROMPTS.matcher(s).replaceAll(String.valueOf(count)));
    }

    private static final Pattern TARGET_DATE = Pattern.compile("\\{\\{\\s*TARGET_DATE\\s*}}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_OF_PROMPTS =
            Pattern.compile("\\{\\{\\s*NUMBER_OF_PROMPTS\\s*}}", Pattern.CASE_INSENSITIVE);

    private AppSetting store(String prompt) {
        String text = prompt == null ? "" : prompt.trim();
        if (text.length() < MIN_PRINCIPLE || text.length() > MAX_PRINCIPLE) {
            throw badRequest("Principle prompt must be %d–%d characters".formatted(MIN_PRINCIPLE, MAX_PRINCIPLE));
        }
        AppSetting setting = settings.findById(AppSetting.PRINCIPLE_PROMPT)
                .orElseGet(() -> new AppSetting(AppSetting.PRINCIPLE_PROMPT, text));
        setting.setValue(text);
        return settings.save(setting);
    }

    private static String describe(Category c) {
        String when = switch (c.getSlug()) {
            case "good-morning" -> " — images people send as a good-morning greeting, so morning light and freshness";
            case "good-night" -> " — images people send as a good-night greeting, so evening/night and calm";
            default -> "";
        };
        return "Category: " + c.getNameEn() + when + ".";
    }

    /** Blank = no date given. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw badRequest("Date must look like 2026-10-14");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
