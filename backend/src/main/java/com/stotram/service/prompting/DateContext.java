package com.stotram.service.prompting;

import com.stotram.dto.PrincipleDtos.CalendarCheck;
import com.stotram.dto.PrincipleDtos.CalendarFacts;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Everything the prompt writer is told about the date the images are for, plus the same
 * facts for the admin to check:
 * <ol>
 *   <li>the weekday (and its Hindi name, which the model links to the day's deity);
 *   <li>the panchang computed by {@link Panchang} — always, it's free and exact;
 *   <li>optionally the panchang as found on the web by {@link CalendarLookup}, run
 *       independently. Its festivals are passed on (Panchang doesn't name festivals); its
 *       tithi and month are compared with the computed ones and any disagreement is shown
 *       to the admin and mentioned to the model.
 * </ol>
 * A failed web lookup never blocks writing prompts: the computed facts are enough.
 */
@Service
public class DateContext {

    /** The master prompt's date format: {{TARGET_DATE}} becomes e.g. 14-10-2026. */
    public static final DateTimeFormatter PROMPT_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public record Result(String context, CalendarCheck check) {
    }

    /** Weekday names as said in Hindi. */
    private static final Map<DayOfWeek, String> VAAR = Map.of(
            DayOfWeek.MONDAY, "Somvar",
            DayOfWeek.TUESDAY, "Mangalvar",
            DayOfWeek.WEDNESDAY, "Budhvar",
            DayOfWeek.THURSDAY, "Guruvar",
            DayOfWeek.FRIDAY, "Shukravar",
            DayOfWeek.SATURDAY, "Shanivar",
            DayOfWeek.SUNDAY, "Ravivar");

    private final CalendarLookup lookup;

    public DateContext(CalendarLookup lookup) {
        this.lookup = lookup;
    }

    public Result build(LocalDate date, boolean checkWeb) {
        String weekday = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        Panchang.Day day = Panchang.forDate(date);
        CalendarFacts computed = new CalendarFacts(day.tithiNumber(), day.paksha() + " " + day.tithi(),
                day.purnimantaMonth(), day.tithiEndsText(), day.navratri(), List.of(), List.of(), null);

        CalendarFacts web = null;
        String webError = null;
        Boolean tithiMatches = null;
        Boolean monthMatches = null;
        if (checkWeb) {
            try {
                CalendarLookup.Result found = lookup.lookup(date);
                web = new CalendarFacts(
                        found.tithiNumber() == 0 ? null : found.tithiNumber(),
                        found.tithiNumber() == 0 ? null : paksha(found.tithiNumber()) + " " + Panchang.tithiName(found.tithiNumber()),
                        "unknown".equals(found.month()) ? null : found.month(),
                        null, null, found.festivals(), found.sources(), found.notes().isBlank() ? null : found.notes());
                tithiMatches = web.tithiNumber() == null ? null : web.tithiNumber() == day.tithiNumber();
                monthMatches = web.month() == null ? null : web.month().equals(day.purnimantaMonth());
            } catch (CalendarLookup.LookupException e) {
                webError = e.getMessage();
            }
        }

        StringBuilder context = new StringBuilder()
                .append("Date the images are for: ").append(weekday).append(", ")
                .append(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)))
                .append(" (").append(date.format(PROMPT_DATE)).append(", ")
                .append(VAAR.get(date.getDayOfWeek())).append("). ")
                .append("Use it: the tithi's and any festival's significance first, then the weekday's traditional deity.\n")
                .append(day.describe());
        if (web != null && !web.festivals().isEmpty()) {
            context.append("\nFestivals and observances on this date (from a web search of panchang sites): ")
                    .append(String.join("; ", web.festivals())).append('.');
        }
        if (web != null && !web.sources().isEmpty()) {
            context.append("\nSources checked (cite only these): ").append(String.join(" , ", web.sources()));
        } else {
            context.append("\nNo websites were checked this time: do not cite any website. Give the source as "
                    + "\"Panchang computed astronomically (Stotrams server)\".");
        }
        if (Boolean.FALSE.equals(tithiMatches) || Boolean.FALSE.equals(monthMatches)) {
            context.append("\nNote: a web source gave ").append(web.tithi() == null ? "" : web.tithi() + ", ")
                    .append(web.month() == null ? "" : web.month() + " month")
                    .append(" — it disagrees with the computed calendar above; prefer the computed one.");
        }
        if (web == null || web.festivals().isEmpty()) {
            context.append("\nOnly name a festival you are confident falls on this date; if unsure, keep to the tithi, weekday and season.");
        }

        CalendarCheck check = new CalendarCheck(date.toString(), weekday, computed, web, webError, tithiMatches, monthMatches);
        return new Result(context.toString(), check);
    }

    private static String paksha(int tithiNumber) {
        return tithiNumber <= 15 ? "Shukla" : "Krishna";
    }
}
