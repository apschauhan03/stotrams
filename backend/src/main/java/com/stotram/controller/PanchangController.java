package com.stotram.controller;

import com.stotram.dto.PanchangResponse;
import com.stotram.service.prompting.Panchang;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public: the day's panchang for the home page's "आज का पंचांग" box. Computed on the server
 * ({@link Panchang}) — free and instant, no web lookup, so it's fine for every visitor.
 * Returns codes and times; the site turns them into Hindi or English.
 */
@RestController
@RequestMapping("/api/panchang")
public class PanchangController {

    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    /** {@code date} (yyyy-MM-dd) defaults to today in India. */
    @GetMapping
    public ResponseEntity<PanchangResponse> panchang(@RequestParam(required = false) String date) {
        LocalDate day = parse(date);
        Panchang.Day p = Panchang.forDate(day);
        var body = new PanchangResponse(
                day.toString(),
                day.getDayOfWeek().getValue(),
                p.tithiNumber(),
                p.tithiEnds().toOffsetDateTime().toString(),
                p.tithiNumber() % 30 + 1,
                p.purnimantaMonthIndex(),
                p.amantaMonthIndex(),
                p.adhik(),
                p.vikramSamvat(),
                p.sunrise().toOffsetDateTime().toString(),
                p.sunset().toOffsetDateTime().toString(),
                p.navratriKind(),
                "New Delhi");
        // The same day always gives the same answer; an hour of caching is plenty.
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(body);
    }

    private static LocalDate parse(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(Panchang.IST);
        }
        try {
            LocalDate d = LocalDate.parse(date.trim());
            if (d.isBefore(EARLIEST) || d.isAfter(LATEST)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must be between 1900 and 2100");
            }
            return d;
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must look like 2026-10-14");
        }
    }
}
