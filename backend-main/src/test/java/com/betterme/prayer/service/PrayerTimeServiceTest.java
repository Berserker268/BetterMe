package com.betterme.prayer.service;

import com.betterme.prayer.model.PrayerName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** No Spring, no network: checks how Aladhan's JSON is turned into times (or into "unavailable"). */
class PrayerTimeServiceTest {
    // Port 1 refuses connections immediately, so lookups fail fast and offline.
    private final PrayerTimeService service = new PrayerTimeService("http://127.0.0.1:1", "Dhaka", "Bangladesh", 1, 1, "Asia/Dhaka");

    private Map<String, Object> timings() {
        Map<String, Object> t = new HashMap<>();
        t.put("Fajr", "05:01 (+06)");
        t.put("Dhuhr", "11:56");
        t.put("Asr", "15:04 (+06)");
        t.put("Maghrib", "17:12");
        t.put("Isha", "18:33");
        return t;
    }

    private Map<String, Object> response(Object code, Map<String, Object> timings) {
        Map<String, Object> meta = Map.of("timezone", "Asia/Dhaka", "method", Map.of("id", 1, "name", "Karachi"));
        Map<String, Object> data = new HashMap<>();
        if (timings != null) data.put("timings", timings);
        data.put("meta", meta);
        Map<String, Object> root = new HashMap<>();
        root.put("code", code);
        root.put("data", data);
        return root;
    }

    @Test
    void parsesTimesAndStripsExtraTextAfterTheClock() {
        var t = service.parse(response(200, timings()));
        assertTrue(t.available());
        assertEquals(LocalTime.of(5, 1), t.times().get(PrayerName.FAJR));
        assertEquals(LocalTime.of(15, 4), t.times().get(PrayerName.ASR));
        assertEquals(LocalTime.of(17, 12), t.times().get(PrayerName.MAGHRIB));
        assertEquals("Asia/Dhaka", t.timezone());
        assertEquals("Karachi", t.methodName());
    }

    @Test
    void missingPrayerMakesEverythingUnavailable() {
        Map<String, Object> t = timings();
        t.remove("Isha");
        var result = service.parse(response(200, t));
        assertFalse(result.available());
        assertTrue(result.times().isEmpty());
        assertTrue(result.message().contains("Isha"));
    }

    @Test
    void badCodesAndGarbageAreUnavailableNotGuessed() {
        assertFalse(service.parse(response(400, timings())).available());
        assertFalse(service.parse(null).available());
        assertFalse(service.parse(response(200, null)).available());
        Map<String, Object> garbage = timings();
        garbage.put("Fajr", "soon");
        assertFalse(service.parse(response(200, garbage)).available());
        Map<String, Object> outOfRange = timings();
        outOfRange.put("Fajr", "25:00");
        assertFalse(service.parse(response(200, outOfRange)).available());
        Map<String, Object> notText = timings();
        notText.put("Fajr", 5);
        assertFalse(service.parse(response(200, notText)).available());
    }

    @Test
    void unreachableServiceGivesUnavailableWithNoTimes() {
        var result = service.timetable(LocalDate.of(2026, 11, 27), "Dhaka", "Bangladesh", 1, 1);
        assertFalse(result.available());
        assertTrue(result.times().isEmpty());
        assertNotNull(result.message());
    }
}
