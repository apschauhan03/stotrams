package com.stotram.dto;

/**
 * GET /api/panchang: one day's panchang as codes and times, for the site to show in Hindi
 * or English.
 *
 * @param weekday              1 = Monday … 7 = Sunday
 * @param tithi                1–30: 1–15 Shukla (15 = Purnima), 16–30 Krishna (30 = Amavasya), at sunrise
 * @param tithiEndsAt          when that tithi ends (ISO, +05:30)
 * @param nextTithi            the tithi after it, same numbering
 * @param month                purnimanta (North Indian) month, 0 = Chaitra … 11 = Phalguna
 * @param amantaMonth          the same in the amanta system
 * @param adhik                a leap (adhik) month
 * @param navratri             "sharad", "chaitra", or null; the day is {@code tithi}
 * @param place                whose sunrise the tithi is read at
 */
public record PanchangResponse(
        String date,
        int weekday,
        int tithi,
        String tithiEndsAt,
        int nextTithi,
        int month,
        int amantaMonth,
        boolean adhik,
        int vikramSamvat,
        String sunrise,
        String sunset,
        String navratri,
        String place) {
}
