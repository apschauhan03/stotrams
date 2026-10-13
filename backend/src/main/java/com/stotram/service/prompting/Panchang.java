package com.stotram.service.prompting;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Computes the Hindu lunar calendar for a day — tithi, paksha, month — from the positions of
 * the Sun and Moon, so the prompt writer is <i>told</i> the tithi instead of recalling it
 * (text models get tithi dates wrong; they move every year).
 *
 * <p>Method: low-precision Meeus formulas (Astronomical Algorithms ch. 25 and 47, the main
 * Moon terms), good to about 0.01°, i.e. tithi end times to within a few minutes. Tithi is
 * read at sunrise in New Delhi (udaya tithi, the usual North Indian panchang convention).
 * Month names use the sidereal Sun at the new moon that opens the month (Lahiri ayanamsa),
 * in both systems: amanta (month ends at new moon) and purnimanta (ends at full moon, North India).
 *
 * <p>Festivals are deliberately not computed here: given the right tithi and month the text
 * model names them reliably, and a hand-made festival table would be a second source of errors.
 */
public final class Panchang {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final double DELHI_LAT = 28.6139;
    private static final double DELHI_LON = 77.2090;
    /** Terrestrial Time minus UT, close enough for 2020s dates (affects nothing at minute level). */
    private static final double DELTA_T_DAYS = 69.0 / 86400.0;
    private static final double SYNODIC_RATE = 12.190749; // mean elongation gain, degrees per day

    private static final String[] TITHIS = {
            "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami",
            "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi"};
    /** Month names as this class spells them (also the enum the web lookup must answer in). */
    public static final String[] MONTHS = {
            "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
            "Ashwin", "Kartik", "Margashirsha", "Pausha", "Magha", "Phalguna"};
    /** The nine forms of Maa Durga, one per Navratri day. */
    private static final String[] NAVADURGA = {
            "Maa Shailputri", "Maa Brahmacharini", "Maa Chandraghanta", "Maa Kushmanda", "Maa Skandamata",
            "Maa Katyayani", "Maa Kalaratri", "Maa Mahagauri", "Maa Siddhidatri"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH);

    /**
     * @param tithiNumber   1–30 (1–15 Shukla, 16–30 Krishna; 15 = Purnima, 30 = Amavasya)
     * @param tithiEnds     when the sunrise tithi ends
     * @param amantaMonth   month name, amanta system ("Adhik " prefix in a leap month)
     * @param purnimantaMonth month name, purnimanta system
     * @param amantaMonthIndex / purnimantaMonthIndex  0 = Chaitra … 11 = Phalguna
     * @param adhik         a leap month (no sankranti in it)
     * @param vikramSamvat  the Vikram Samvat year (starts at Chaitra Shukla Pratipada)
     */
    public record Day(LocalDate date,
                      ZonedDateTime sunrise,
                      ZonedDateTime sunset,
                      int tithiNumber,
                      String tithi,
                      String paksha,
                      ZonedDateTime tithiEnds,
                      String nextTithi,
                      String amantaMonth,
                      String purnimantaMonth,
                      int amantaMonthIndex,
                      int purnimantaMonthIndex,
                      boolean adhik,
                      int vikramSamvat) {

        /** One paragraph of facts for the prompt writer. */
        public String describe() {
            StringBuilder s = new StringBuilder()
                    .append("Hindu calendar for this date (computed astronomically for New Delhi; trust these over memory): ")
                    .append("at sunrise (").append(sunrise.format(TIME)).append(" IST) the tithi is ")
                    .append(paksha).append(' ').append(tithi)
                    .append(", ending ").append(tithiEnds.format(TIME)).append(" IST (then ").append(nextTithi).append("). ")
                    .append("Month: ").append(purnimantaMonth).append(" (purnimanta, North Indian)");
            if (!purnimantaMonth.equals(amantaMonth)) {
                s.append(", ").append(amantaMonth).append(" (amanta)");
            }
            s.append('.');
            String navratri = navratri();
            if (navratri != null) {
                s.append(' ').append(navratri)
                        .append(": the day of ").append(NAVADURGA[tithiNumber - 1])
                        .append(", the main occasion and deity of the day")
                        .append(" (by tithi; when a tithi is skipped or repeats, panchangs can shift the day count by one).");
            }
            s.append(" A tithi name on its own (e.g. Chaturthi, Ekadashi) is not a festival name:")
                    .append(" never call the day \"Ganesh Chaturthi\" or similar unless that festival is stated here.");
            return s.toString();
        }

        /**
         * "Sharad Navratri day 4", or null. Navratri is Shukla Pratipada–Navami of Chaitra
         * (spring) and Ashwin (Sharad).
         */
        public String navratri() {
            String kind = navratriKind();
            return kind == null ? null
                    : "%s Navratri day %d".formatted(kind.equals("sharad") ? "Sharad" : "Chaitra", tithiNumber);
        }

        /** "sharad", "chaitra", or null when it isn't Navratri (day = tithiNumber). */
        public String navratriKind() {
            if (tithiNumber > 9 || adhik) {
                return null;
            }
            return switch (amantaMonthIndex) {
                case 6 -> "sharad";    // Ashwin
                case 0 -> "chaitra";
                default -> null;
            };
        }

        /** "15 Oct, 1:13 AM" — when the sunrise tithi ends, IST. */
        public String tithiEndsText() {
            return tithiEnds.format(TIME);
        }
    }

    private Panchang() {
    }

    public static Day forDate(LocalDate date) {
        ZonedDateTime sunrise = sunrise(date);
        double jd = julianDay(sunrise.toInstant());
        double elongation = elongation(jd);
        int index = (int) Math.floor(elongation / 12.0);          // 0..29
        int number = index + 1;
        double end = solveElongation(jd + ((index + 1) * 12.0 - elongation) / SYNODIC_RATE, (index + 1) * 12.0);

        // The month runs from the new moon before this day to the next one.
        double prevNewMoon = solveElongation(jd - elongation / SYNODIC_RATE, 0.0);
        double nextNewMoon = solveElongation(prevNewMoon + 29.53, 0.0);
        int rashiStart = rashi(prevNewMoon);
        int rashiEnd = rashi(nextNewMoon);
        int amantaIndex = (rashiStart + 1) % 12;
        boolean adhik = rashiStart == rashiEnd;   // no sankranti in the month: a leap month
        String amanta = (adhik ? "Adhik " : "") + MONTHS[amantaIndex];
        // Purnimanta months start at full moon, so the Krishna paksha belongs to the NEXT
        // amanta month, named from the Sun at the next new moon.
        int purnimantaIndex = number <= 15 ? amantaIndex : (rashiEnd + 1) % 12;
        String purnimanta = number <= 15 ? amanta : MONTHS[purnimantaIndex];
        // The samvat turns at Chaitra Shukla Pratipada, so Pausha–Phalguna early in the
        // Gregorian year still belong to the previous one.
        int samvat = date.getYear() + (date.getMonthValue() <= 4 && amantaIndex >= 9 ? 56 : 57);

        return new Day(date, sunrise, sunTime(date, false), number, tithiName(number),
                number <= 15 ? "Shukla" : "Krishna", toIst(end), tithiName(number % 30 + 1),
                amanta, purnimanta, amantaIndex, purnimantaIndex, adhik, samvat);
    }

    /** 1–30 -> "Chaturthi", "Purnima", "Amavasya", ... (paksha not included). */
    public static String tithiName(int number) {
        if (number == 15) return "Purnima";
        if (number == 30) return "Amavasya";
        return TITHIS[(number - 1) % 15];
    }

    // ---- Astronomy ---------------------------------------------------------------------

    /** Moon minus Sun longitude, 0–360°. Nutation cancels in the difference. */
    static double elongation(double jdUt) {
        double t = (jdUt + DELTA_T_DAYS - 2451545.0) / 36525.0;
        return norm(moonLongitude(t) - sunLongitude(t));
    }

    /** Finds the moment near {@code guessJd} when the elongation equals {@code target}°. */
    private static double solveElongation(double guessJd, double target) {
        double jd = guessJd;
        for (int i = 0; i < 20; i++) {
            double diff = norm(elongation(jd) - target + 180.0) - 180.0; // -180..180
            jd -= diff / SYNODIC_RATE;
            if (Math.abs(diff) < 1e-6) break;
        }
        return jd;
    }

    /** Sidereal (Lahiri) sign of the Sun, 0 = Mesha. */
    private static int rashi(double jdUt) {
        double t = (jdUt + DELTA_T_DAYS - 2451545.0) / 36525.0;
        double ayanamsa = 23.853 + 1.3964 * t;   // 23°51' at J2000, ~50.3"/year
        return (int) Math.floor(norm(sunLongitude(t) - ayanamsa) / 30.0);
    }

    /** Apparent geocentric longitude of the Sun (Meeus ch. 25, low precision). */
    static double sunLongitude(double t) {
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = rad(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * m)
                + 0.000289 * Math.sin(3 * m);
        return norm(l0 + c - 0.00569);   // aberration
    }

    /** Geocentric longitude of the Moon (Meeus ch. 47, main periodic terms). */
    static double moonLongitude(double t) {
        double lp = 218.3164477 + 481267.88123421 * t;
        double d = rad(297.8501921 + 445267.1114034 * t);
        double m = rad(357.5291092 + 35999.0502909 * t);
        double mp = rad(134.9633964 + 477198.8675055 * t);
        double f = rad(93.2720950 + 483202.0175233 * t);
        double a1 = rad(119.75 + 131.849 * t);
        double a2 = rad(53.09 + 479264.290 * t);
        double e = 1 - 0.002516 * t;

        double sum = 6288774 * Math.sin(mp)
                + 1274027 * Math.sin(2 * d - mp)
                + 658314 * Math.sin(2 * d)
                + 213618 * Math.sin(2 * mp)
                - 185116 * e * Math.sin(m)
                - 114332 * Math.sin(2 * f)
                + 58793 * Math.sin(2 * d - 2 * mp)
                + 57066 * e * Math.sin(2 * d - m - mp)
                + 53322 * Math.sin(2 * d + mp)
                + 45758 * e * Math.sin(2 * d - m)
                - 40923 * e * Math.sin(m - mp)
                - 34720 * Math.sin(d)
                - 30383 * e * Math.sin(m + mp)
                + 15327 * Math.sin(2 * d - 2 * f)
                - 12528 * Math.sin(mp + 2 * f)
                + 10980 * Math.sin(mp - 2 * f)
                + 10675 * Math.sin(4 * d - mp)
                + 10034 * Math.sin(3 * mp)
                + 8548 * Math.sin(4 * d - 2 * mp)
                - 7888 * e * Math.sin(2 * d + m - mp)
                - 6766 * e * Math.sin(2 * d + m)
                - 5163 * Math.sin(d - mp)
                + 4987 * e * Math.sin(d + m)
                + 4036 * e * Math.sin(2 * d - m + mp)
                + 3994 * Math.sin(2 * d + 2 * mp)
                + 3861 * Math.sin(4 * d)
                + 3665 * Math.sin(2 * d - 3 * mp)
                - 2689 * e * Math.sin(m - 2 * mp)
                - 2602 * Math.sin(2 * d - mp + 2 * f)
                + 2390 * e * Math.sin(2 * d - m - 2 * mp)
                - 2348 * Math.sin(d + mp)
                + 2236 * e * e * Math.sin(2 * d - 2 * m)
                - 2120 * e * Math.sin(m + 2 * mp)
                - 2069 * e * e * Math.sin(2 * m)
                + 3958 * Math.sin(a1)
                + 1962 * Math.sin(rad(lp) - f)
                + 318 * Math.sin(a2);
        return norm(lp + sum / 1_000_000.0);
    }

    static ZonedDateTime sunrise(LocalDate date) {
        return sunTime(date, true);
    }

    /** Sunrise or sunset in New Delhi on {@code date} (NOAA solar-position method, ±1–2 minutes). */
    static ZonedDateTime sunTime(LocalDate date, boolean rising) {
        double jdNoon = julianDay(date.atTime(12, 0).atZone(IST).toInstant());
        double t = (jdNoon - 2451545.0) / 36525.0;
        double lambda = rad(sunLongitude(t));
        double epsilon = rad(23.439291 - 0.0130042 * t);
        double decl = Math.asin(Math.sin(epsilon) * Math.sin(lambda));
        // Equation of time, minutes.
        double l0 = rad(norm(280.46646 + 36000.76983 * t));
        double m = rad(357.52911 + 35999.05029 * t);
        double ecc = 0.016708634 - 0.000042037 * t;
        double y = Math.pow(Math.tan(epsilon / 2), 2);
        double eot = 4 * Math.toDegrees(y * Math.sin(2 * l0) - 2 * ecc * Math.sin(m)
                + 4 * ecc * y * Math.sin(m) * Math.cos(2 * l0)
                - 0.5 * y * y * Math.sin(4 * l0) - 1.25 * ecc * ecc * Math.sin(2 * m));
        double lat = rad(DELHI_LAT);
        double cosH = (Math.cos(rad(90.833)) - Math.sin(lat) * Math.sin(decl)) / (Math.cos(lat) * Math.cos(decl));
        double hourAngle = Math.toDegrees(Math.acos(cosH));
        double minutesUtc = 720 - 4 * (DELHI_LON + (rising ? hourAngle : -hourAngle)) - eot;
        return date.atStartOfDay(ZoneId.of("UTC"))
                .plus(Duration.ofSeconds(Math.round(minutesUtc * 60)))
                .withZoneSameInstant(IST);
    }

    private static double julianDay(Instant instant) {
        return instant.getEpochSecond() / 86400.0 + instant.getNano() / 86_400e9 + 2440587.5;
    }

    private static ZonedDateTime toIst(double jdUt) {
        long millis = Math.round((jdUt - 2440587.5) * 86_400_000.0);
        return Instant.ofEpochMilli(millis).atZone(IST);
    }

    private static double norm(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    private static double rad(double degrees) {
        return Math.toRadians(degrees);
    }
}
