package com.betterme.prayer;

import com.betterme.prayer.dto.PrayerViews.Day;
import com.betterme.prayer.model.PrayerName;
import com.betterme.prayer.repository.PrayerLogRepository;
import com.betterme.prayer.service.PrayerService;
import com.betterme.prayer.service.PrayerTimeService;
import com.betterme.user.User;
import com.betterme.user.UserRepository;
import com.betterme.wisdom.DailyQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Uses a disposable in-memory database, never the team's Supabase database. Aladhan is mocked.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prayer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.config.import=", "app.remember-me-key=prayer-test-only-key"
})
@AutoConfigureMockMvc
class PrayerIntegrationTest {
    static final String TZ = "UTC";

    @Autowired PrayerService prayer;
    @Autowired UserRepository users;
    @Autowired PrayerLogRepository logs;
    @Autowired MockMvc mvc;
    @MockitoBean PrayerTimeService times;
    @MockitoBean DailyQuoteService wisdom;
    User a, b;

    @BeforeEach
    void setup() {
        logs.deleteAll();
        users.deleteAll();
        a = makeUser("Alice", "alice@test.local");
        b = makeUser("Bob", "bob@test.local");
        when(times.defaultCity()).thenReturn("Dhaka");
        when(times.defaultCountry()).thenReturn("Bangladesh");
        when(times.defaultMethod()).thenReturn(1);
        when(times.defaultSchool()).thenReturn(1);
        when(times.defaultZone()).thenReturn(TZ);
        when(times.timetable(any(LocalDate.class), anyString(), anyString(), anyInt(), anyInt())).thenReturn(sampleTimes());
    }

    User makeUser(String name, String email) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash("test-only");
        return users.save(user);
    }

    PrayerTimeService.Timetable sampleTimes() {
        Map<PrayerName, LocalTime> t = new EnumMap<>(PrayerName.class);
        t.put(PrayerName.FAJR, LocalTime.of(5, 1));
        t.put(PrayerName.DHUHR, LocalTime.of(11, 56));
        t.put(PrayerName.ASR, LocalTime.of(15, 4));
        t.put(PrayerName.MAGHRIB, LocalTime.of(17, 12));
        t.put(PrayerName.ISHA, LocalTime.of(18, 33));
        return PrayerTimeService.Timetable.ok(t, "UTC", "Test method");
    }

    LocalDate today() { return LocalDate.now(ZoneId.of(TZ)); }

    void mark(User who, String prayerKey, LocalDate date, boolean completed) {
        prayer.log(who.getEmail(), prayerKey, date.toString(), completed, TZ);
    }

    Day day(User who, LocalDate date) {
        return prayer.day(who.getEmail(), date.toString(), "Dhaka", "Bangladesh", 1, 1, TZ);
    }

    @Test
    void markingIsIdempotentAndUnmarkingRemovesTheRow() {
        var first = prayer.log(a.getEmail(), "fajr", today().toString(), true, TZ);
        assertEquals(1, first.doneCount());
        assertEquals(20, first.percent());
        assertEquals(1, prayer.log(a.getEmail(), "FAJR", today().toString(), true, TZ).doneCount());
        assertEquals(1, logs.count());
        assertEquals(0, prayer.log(a.getEmail(), "FAJR", today().toString(), false, TZ).doneCount());
        assertEquals(0, logs.count());
        assertEquals(0, prayer.log(a.getEmail(), "FAJR", today().toString(), false, TZ).doneCount()); // no error
    }

    @Test
    void progressMatchesKnownCases() {
        assertEquals(0, day(a, today()).doneCount());
        mark(a, "FAJR", today(), true);
        mark(a, "DHUHR", today(), true);
        mark(a, "ASR", today(), true);
        Day three = day(a, today());
        assertEquals(3, three.doneCount());
        assertEquals(60, three.percent());
        mark(a, "MAGHRIB", today(), true);
        mark(a, "ISHA", today(), true);
        Day five = day(a, today());
        assertEquals(5, five.doneCount());
        assertEquals(100, five.percent());
        assertTrue(five.prayers().stream().allMatch(p -> p.done()));
        assertEquals("5:01 AM", five.prayers().getFirst().time());
    }

    @Test
    void usersNeverSeeOrChangeEachOthersLogs() {
        mark(a, "FAJR", today(), true);
        mark(a, "DHUHR", today(), true);
        mark(a, "ASR", today(), true);
        assertEquals(0, day(b, today()).doneCount());
        assertEquals(0, prayer.summary(b.getEmail(), TZ).weekCompleted());
        mark(b, "FAJR", today(), false); // Bob "unmarking" must not touch Alice's row
        assertEquals(3, day(a, today()).doneCount());
        assertEquals(3, prayer.summary(a.getEmail(), TZ).weekCompleted());
        assertEquals(3, logs.count());
    }

    @Test
    void rejectsFutureDistantPastAndInvalidInput() {
        assertThrows(ResponseStatusException.class, () -> mark(a, "FAJR", today().plusDays(1), true));
        assertThrows(ResponseStatusException.class, () -> mark(a, "FAJR", today().minusDays(400), true));
        assertThrows(ResponseStatusException.class, () -> mark(a, "ZUHR", today(), true));
        assertThrows(ResponseStatusException.class, () -> prayer.log(a.getEmail(), "FAJR", "not-a-date", true, TZ));
        assertThrows(ResponseStatusException.class,
                () -> prayer.day(a.getEmail(), null, "Dhaka", "Bangladesh", 999, 1, TZ));
        assertThrows(ResponseStatusException.class,
                () -> prayer.day(a.getEmail(), null, "Dhaka", "Bangladesh", 1, 7, TZ));
        assertEquals(0, logs.count());
    }

    @Test
    void pastDaysAppearInTheSevenDayTrend() {
        LocalDate yesterday = today().minusDays(1);
        for (PrayerName p : PrayerName.values()) mark(a, p.name(), yesterday, true);
        var summary = prayer.summary(a.getEmail(), TZ);
        assertEquals(7, summary.week().size());
        assertEquals(5, summary.week().get(5).completed()); // index 6 is today
        assertEquals(0, summary.week().get(6).completed());
        assertEquals(1, summary.streak());
        assertTrue(summary.hasAnyLogs());
    }

    @Test
    void loggingStillWorksWhenPrayerTimesAreUnavailable() {
        when(times.timetable(any(LocalDate.class), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(PrayerTimeService.Timetable.unavailable("Prayer times unavailable right now."));
        Day d = day(a, today());
        assertFalse(d.timings().available());
        assertTrue(d.prayers().stream().allMatch(p -> p.time() == null)); // never a made-up time
        mark(a, "FAJR", today(), true);
        assertEquals(1, day(a, today()).doneCount());
    }

    @Test
    void simultaneousMarksKeepOneRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 6; i++) jobs.add(pool.submit(() -> mark(a, "ASR", today(), true)));
            for (Future<?> job : jobs) job.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, logs.count());
    }

    @Test
    void pageAndApiRequireLogin() throws Exception {
        mvc.perform(get("/prayer")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/prayer/api/day")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/prayer/api/stats")).andExpect(status().is3xxRedirection());
    }

    @Test
    void pageRendersForSignedInUser() throws Exception {
        mvc.perform(get("/prayer").with(user(a.getEmail())))
                .andExpect(status().isOk())
                .andExpect(view().name("prayer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("csrf-token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Karachi")));
    }

    @Test
    void apiLogNeedsCsrfAndReturnsTheNewCount() throws Exception {
        String body = "{\"prayer\":\"FAJR\",\"date\":\"\",\"completed\":true,\"tz\":\"UTC\"}";
        mvc.perform(put("/prayer/api/log").with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/prayer/api/log").with(user(a.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneCount").value(1))
                .andExpect(jsonPath("$.percent").value(20));
        mvc.perform(get("/prayer/api/day").param("tz", TZ).with(user(a.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prayers.length()").value(5))
                .andExpect(jsonPath("$.doneCount").value(1));
        mvc.perform(get("/prayer/api/day").param("tz", TZ).with(user(b.getEmail())))
                .andExpect(jsonPath("$.doneCount").value(0));
    }

    @Test
    void apiReportsFriendlyJsonErrors() throws Exception {
        String body = "{\"prayer\":\"NOPE\",\"date\":\"\",\"completed\":true,\"tz\":\"UTC\"}";
        mvc.perform(put("/prayer/api/log").with(user(a.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose one of the five prayers."));
    }
}
