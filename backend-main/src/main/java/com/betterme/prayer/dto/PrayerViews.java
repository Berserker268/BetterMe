package com.betterme.prayer.dto;

import java.util.List;

public final class PrayerViews {
    private PrayerViews() {}
    public record Method(int id, String name) {}
    public record Defaults(String city, String country, int method, int school, List<Method> methods) {}
    /** available=false means NO times (never made-up ones); message explains why. */
    public record Timings(boolean available, String message, String timezone, String methodName) {}
    public record Item(String key, String label, String time, boolean done, boolean next) {}
    public record Day(String date, String dateLabel, boolean today, boolean future, String prevDate, String nextDate,
                      String city, String country, int method, int school, Timings timings,
                      List<Item> prayers, int doneCount, int percent) {}
    public record Logged(String prayer, boolean completed, int doneCount, int percent) {}
    /** beads is FAJR..ISHA order, true = marked. */
    public record DayStat(String date, String label, int completed, int percent, boolean today, List<Boolean> beads) {}
    /** All windows end today (inclusive); today may be unfinished. */
    public record Summary(int weekCompleted, int weekPercent, int monthCompleted, int monthPercent,
                          int streak, boolean streakCapped, boolean hasAnyLogs, List<DayStat> week) {}
}
