package com.betterme.prayer.model;

import java.util.Optional;

/** The five obligatory prayers. name() is what PrayerLog.prayerName stores; apiKey is Aladhan's key. */
public enum PrayerName {
    FAJR("Fajr"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha");

    private final String label;

    PrayerName(String label) { this.label = label; }

    public String label() { return label; }
    public String apiKey() { return label; }

    public static Optional<PrayerName> parse(String value) {
        if (value == null) return Optional.empty();
        for (PrayerName p : values()) if (p.name().equalsIgnoreCase(value.strip())) return Optional.of(p);
        return Optional.empty();
    }
}
