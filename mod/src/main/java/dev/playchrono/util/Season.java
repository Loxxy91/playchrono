package dev.playchrono.util;

import java.time.LocalDate;

/**
 * Seasonal events, every year, from the local date (same rules as web/icons.js season()):
 * New Year 30 Dec - 2 Jan (wins over Christmas on the 30th), Christmas 10 - 29 Dec, Halloween 10 Oct - 2 Nov,
 * Easter on Easter Sunday.
 */
public final class Season {
    private Season() {}

    /** "xmas", "newyear", "easter", "halloween" or null. */
    public static String of(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if ((m == 12 && day >= 30) || (m == 1 && day <= 2)) return "newyear";
        if (m == 12 && day >= 10) return "xmas";
        if ((m == 10 && day >= 10) || (m == 11 && day <= 2)) return "halloween";
        return d.equals(easter(d.getYear())) ? "easter" : null;
    }

    /** One id per occurrence of an event ("newyear-2026" covers 30 Dec 2026 - 2 Jan 2027). */
    public static String eventId(String season, LocalDate d) {
        return season + "-" + (d.getMonthValue() == 1 ? d.getYear() - 1 : d.getYear());
    }

    /** The year to wish a happy new year for: next year in December, this year in January. */
    public static int greetingYear(LocalDate d) {
        return d.getMonthValue() == 12 ? d.getYear() + 1 : d.getYear();
    }

    /** Easter Sunday, Gregorian calendar (anonymous algorithm). */
    public static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        return LocalDate.of(y, (h + l - 7 * m + 114) / 31, ((h + l - 7 * m + 114) % 31) + 1);
    }
}
