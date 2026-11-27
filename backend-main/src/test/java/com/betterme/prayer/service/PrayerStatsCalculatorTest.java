package com.betterme.prayer.service;

import com.betterme.prayer.model.PrayerName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static com.betterme.prayer.dto.PrayerViews.Summary;
import static org.junit.jupiter.api.Assertions.*;

class PrayerStatsCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private static Set<PrayerName> first(int n) {
        Set<PrayerName> s = EnumSet.noneOf(PrayerName.class);
        PrayerName[] all = PrayerName.values();
        for (int i = 0; i < n; i++) s.add(all[i]);
        return s;
    }

    @Test
    void emptyHistoryIsZeroAndNotFlaggedAsLogged() {
        Summary s = PrayerStatsCalculator.summarize(TODAY, Map.of());
        assertEquals(0, s.weekCompleted());
        assertEquals(0, s.streak());
        assertFalse(s.hasAnyLogs());
        assertEquals(7, s.week().size());
    }

    @Test
    void threeOfFiveIsSixtyPercentForTheDay() {
        Summary s = PrayerStatsCalculator.summarize(TODAY, Map.of(TODAY, first(3)));
        var today = s.week().get(6);
        assertTrue(today.today());
        assertEquals(3, today.completed());
        assertEquals(60, today.percent());
        assertEquals(9, s.weekPercent()); // 3 / 35
    }

    @Test
    void sevenFullDaysIsFullWeekAndSevenDayStreak() {
        Map<LocalDate, Set<PrayerName>> m = new HashMap<>();
        for (int i = 0; i < 7; i++) m.put(TODAY.minusDays(i), first(5));
        Summary s = PrayerStatsCalculator.summarize(TODAY, m);
        assertEquals(100, s.weekPercent());
        assertEquals(7, s.streak());
        assertFalse(s.streakCapped());
    }

    @Test
    void unfinishedTodayDoesNotBreakYesterdaysStreak() {
        Map<LocalDate, Set<PrayerName>> m = new HashMap<>();
        m.put(TODAY, first(2));
        m.put(TODAY.minusDays(1), first(5));
        m.put(TODAY.minusDays(2), first(5));
        assertEquals(2, PrayerStatsCalculator.summarize(TODAY, m).streak());
    }

    @Test
    void gapEndsTheStreak() {
        Map<LocalDate, Set<PrayerName>> m = new HashMap<>();
        m.put(TODAY, first(5));
        m.put(TODAY.minusDays(1), first(4));
        m.put(TODAY.minusDays(2), first(5));
        assertEquals(1, PrayerStatsCalculator.summarize(TODAY, m).streak());
    }

    @Test
    void thirtyFullDaysIsCapped() {
        Map<LocalDate, Set<PrayerName>> m = new HashMap<>();
        for (int i = 0; i < 40; i++) m.put(TODAY.minusDays(i), first(5));
        Summary s = PrayerStatsCalculator.summarize(TODAY, m);
        assertEquals(30, s.streak());
        assertTrue(s.streakCapped());
        assertEquals(100, s.monthPercent());
    }
}
