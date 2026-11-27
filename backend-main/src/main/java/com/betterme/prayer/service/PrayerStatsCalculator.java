package com.betterme.prayer.service;

import com.betterme.prayer.model.PrayerName;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import static com.betterme.prayer.dto.PrayerViews.*;

/**
 * Pure statistics logic (no Spring/DB) so it is easy to unit test.
 *   percentage(d) = completed(d) / 5 * 100
 *   7-day rate    = sum of the 7 days ending today / 35;  30-day rate = sum of 30 days / 150
 *   streak        = consecutive 5/5 days, counted back from today if today is 5/5, otherwise from yesterday
 *                   (an unfinished today doesn't zero it). Looks only inside the 30-day window.
 * Unlogged days are "not logged", never "proven missed".
 */
public final class PrayerStatsCalculator {
    public static final int PER_DAY = PrayerName.values().length;
    public static final int WEEK = 7;
    public static final int MONTH = 30;

    private PrayerStatsCalculator() {}

    public static Summary summarize(LocalDate today, Map<LocalDate, Set<PrayerName>> byDate) {
        LocalDate monthStart = today.minusDays(MONTH - 1L);
        int month = 0;
        for (int i = 0; i < MONTH; i++) month += count(byDate, monthStart.plusDays(i));

        List<DayStat> week = new ArrayList<>();
        int weekTotal = 0;
        for (int i = WEEK - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            int c = count(byDate, d);
            weekTotal += c;
            Set<PrayerName> done = byDate.getOrDefault(d, Set.of());
            List<Boolean> beads = new ArrayList<>();
            for (PrayerName p : PrayerName.values()) beads.add(done.contains(p));
            week.add(new DayStat(d.toString(), d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    c, percent(c, PER_DAY), d.equals(today), List.copyOf(beads)));
        }

        LocalDate cursor = count(byDate, today) == PER_DAY ? today : today.minusDays(1);
        int streak = 0;
        boolean capped = false;
        while (true) {
            if (cursor.isBefore(monthStart)) { capped = streak > 0; break; }
            if (count(byDate, cursor) != PER_DAY) break;
            streak++;
            cursor = cursor.minusDays(1);
        }
        return new Summary(weekTotal, percent(weekTotal, WEEK * PER_DAY), month, percent(month, MONTH * PER_DAY),
                streak, capped, month > 0, List.copyOf(week));
    }

    public static int percent(int part, int total) { return total == 0 ? 0 : (int) Math.round(part * 100.0 / total); }

    private static int count(Map<LocalDate, Set<PrayerName>> byDate, LocalDate d) { return byDate.getOrDefault(d, Set.of()).size(); }
}
