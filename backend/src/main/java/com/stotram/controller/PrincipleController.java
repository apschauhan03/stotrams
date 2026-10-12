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
import com.stotram.service.prompting.PromptWriter;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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
    private static final int MAX_PRINCIPLE = 8000;

    private final AppSettingRepository settings;
    private final CategoryRepository categories;
    private final PromptWriter writer;

    public PrincipleController(AppSettingRepository settings, CategoryRepository categories, PromptWriter writer) {
        this.settings = settings;
        this.categories = categories;
        this.writer = writer;
    }

    @GetMapping
    public PrincipleResponse get() {
        return settings.findById(AppSetting.PRINCIPLE_PROMPT)
                .map(s -> new PrincipleResponse(s.getValue(), s.getUpdatedAt(), writer.model()))
                .orElse(new PrincipleResponse("", null, writer.model()));
    }

    @PutMapping
    @Transactional
    public PrincipleResponse save(@RequestBody(required = false) PrincipleRequest body) {
        AppSetting saved = store(body == null ? null : body.prompt());
        return new PrincipleResponse(saved.getValue(), saved.getUpdatedAt(), writer.model());
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
        String principle = store(body.prompt()).getValue();
        String context = categories.findBySlug(body.category() == null ? "" : body.category().trim())
                .map(PrincipleController::describe)
                .orElse("Category: any.");

        var drafts = writer.write(principle, count, context).stream()
                .map(d -> new Draft(d.name(), d.prompt()))
                .toList();
        return new WriteResponse(drafts, writer.model());
    }

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

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
