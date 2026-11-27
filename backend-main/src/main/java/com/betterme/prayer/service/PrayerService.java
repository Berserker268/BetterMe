package com.betterme.prayer.service;

import com.betterme.prayer.model.PrayerLog;
import com.betterme.prayer.model.PrayerName;
import com.betterme.prayer.repository.PrayerLogRepository;
import com.betterme.user.User;
import com.betterme.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.betterme.prayer.dto.PrayerViews.*;

@Service
@Transactional(readOnly = true)
public class PrayerService {
    private static final int MAX_DAYS_BACK = 365;
    private static final int MAX_DAYS_AHEAD = 7; // viewing times only; logging a future day is refused
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH);
    /** Subset of Aladhan calculation methods offered in the UI. Verify ids at aladhan.com/calculation-methods. */
    private static final Map<Integer, String> METHODS = new LinkedHashMap<>();
    static {
        METHODS.put(1, "University of Islamic Sciences, Karachi");
        METHODS.put(2, "Islamic Society of North America (ISNA)");
        METHODS.put(3, "Muslim World League");
        METHODS.put(4, "Umm Al-Qura University, Makkah");
        METHODS.put(5, "Egyptian General Authority of Survey");
        METHODS.put(13, "Diyanet Isleri Baskanligi, Turkey");
        METHODS.put(15, "Moonsighting Committee Worldwide");
    }

    private final UserRepository users;
    private final PrayerLogRepository logs;
    private final PrayerTimeService times;
    private final EntityManager entityManager;

    public PrayerService(UserRepository users, PrayerLogRepository logs, PrayerTimeService times, EntityManager entityManager) {
        this.users = users;
        this.logs = logs;
        this.times = times;
        this.entityManager = entityManager;
    }

    public Defaults defaults() {
        List<Method> methods = new ArrayList<>();
        METHODS.forEach((id, name) -> methods.add(new Method(id, name)));
        return new Defaults(times.defaultCity(), times.defaultCountry(), times.defaultMethod(), times.defaultSchool(), methods);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Day day(String email, String date, String city, String country, Integer method, Integer school, String tz) {
        User viewer = user(email);
        ZoneId zone = zone(tz);
        LocalDate today = LocalDate.now(zone);
        LocalDate selected = parseDate(date, today);
        if (selected.isAfter(today.plusDays(MAX_DAYS_AHEAD)) || selected.isBefore(today.minusDays(MAX_DAYS_BACK)))
            throw bad("Choose a date from the last year up to a week ahead.");
        String cityValue = place(city, times.defaultCity(), "City");
        String countryValue = place(country, times.defaultCountry(), "Country");
        int methodValue = method == null ? times.defaultMethod() : method;
        int schoolValue = school == null ? times.defaultSchool() : school;
        if (!METHODS.containsKey(methodValue)) throw bad("Choose one of the listed calculation methods.");
        if (schoolValue != 0 && schoolValue != 1) throw bad("Choose a valid Asr setting.");

        PrayerTimeService.Timetable table = times.timetable(selected, cityValue, countryValue, methodValue, schoolValue);
        Set<PrayerName> done = doneOn(viewer.getId(), selected);
        PrayerName next = selected.equals(today) ? nextPrayer(table) : null;

        List<Item> items = new ArrayList<>();
        for (PrayerName p : PrayerName.values()) {
            LocalTime t = table.times().get(p);
            items.add(new Item(p.name(), p.label(), t == null ? null : clock(t), done.contains(p), p == next));
        }
        String methodName = table.methodName() != null ? table.methodName() : METHODS.get(methodValue);
        return new Day(selected.toString(), LONG_DATE.format(selected), selected.equals(today), selected.isAfter(today),
                selected.minusDays(1).toString(),
                selected.isBefore(today.plusDays(MAX_DAYS_AHEAD)) ? selected.plusDays(1).toString() : null,
                cityValue, countryValue, methodValue, schoolValue,
                new Timings(table.available(), table.message(), table.timezone(), methodName),
                items, done.size(), PrayerStatsCalculator.percent(done.size(), PrayerStatsCalculator.PER_DAY));
    }

    /** Idempotent: marking twice keeps one row; unmarking something not marked does nothing. */
    @Transactional
    public Logged log(String email, String prayerKey, String date, boolean completed, String tz) {
        User viewer = user(email);
        PrayerName prayer = PrayerName.parse(prayerKey).orElseThrow(() -> bad("Choose one of the five prayers."));
        LocalDate today = LocalDate.now(zone(tz));
        LocalDate day = parseDate(date, today);
        if (day.isAfter(today)) throw bad("You can't log a prayer for a future date.");
        if (day.isBefore(today.minusDays(MAX_DAYS_BACK))) throw bad("That date is too far in the past.");
        // Serialise this user's writes (same idea as requestFriend) so a double-click can't create duplicates.
        entityManager.find(User.class, viewer.getId(), LockModeType.PESSIMISTIC_WRITE);
        boolean exists = logs.existsByUserIdAndPrayerDateAndPrayerName(viewer.getId(), day, prayer.name());
        if (completed && !exists) {
            PrayerLog row = new PrayerLog();
            row.setUser(viewer);
            row.setPrayerName(prayer.name());
            row.setPrayerDate(day);
            logs.save(row);
        } else if (!completed && exists) logs.deleteOne(viewer.getId(), day, prayer.name());
        logs.flush();
        int count = (int) logs.countByUserIdAndPrayerDate(viewer.getId(), day);
        return new Logged(prayer.name(), completed, count, PrayerStatsCalculator.percent(count, PrayerStatsCalculator.PER_DAY));
    }

    public Summary summary(String email, String tz) {
        User viewer = user(email);
        LocalDate today = LocalDate.now(zone(tz));
        Map<LocalDate, Set<PrayerName>> byDate = new HashMap<>();
        for (PrayerLog row : logs.inRange(viewer.getId(), today.minusDays(PrayerStatsCalculator.MONTH - 1L), today))
            PrayerName.parse(row.getPrayerName()).ifPresent(p ->
                    byDate.computeIfAbsent(row.getPrayerDate(), d -> EnumSet.noneOf(PrayerName.class)).add(p));
        return PrayerStatsCalculator.summarize(today, byDate);
    }

    private static String clock(LocalTime t) { return DISPLAY_TIME.format(t) + (t.getHour() < 12 ? " AM" : " PM"); }

    private Set<PrayerName> doneOn(Long userId, LocalDate date) {
        Set<PrayerName> done = EnumSet.noneOf(PrayerName.class);
        for (String name : logs.namesForDay(userId, date)) PrayerName.parse(name).ifPresent(done::add);
        return done;
    }

    private PrayerName nextPrayer(PrayerTimeService.Timetable table) {
        if (!table.available() || table.timezone() == null) return null;
        try {
            LocalTime now = LocalTime.now(ZoneId.of(table.timezone()));
            for (PrayerName p : PrayerName.values()) {
                LocalTime t = table.times().get(p);
                if (t != null && t.isAfter(now)) return p;
            }
        } catch (DateTimeException ignored) { }
        return null;
    }

    private ZoneId zone(String tz) {
        try {
            if (tz != null && !tz.isBlank()) return ZoneId.of(tz.strip());
        } catch (DateTimeException ignored) { }
        return ZoneId.of(times.defaultZone());
    }

    private LocalDate parseDate(String value, LocalDate today) {
        if (value == null || value.isBlank()) return today;
        try { return LocalDate.parse(value.strip()); }
        catch (DateTimeException e) { throw bad("Use a valid date."); }
    }

    private String place(String value, String fallback, String label) {
        if (value == null || value.isBlank()) return fallback;
        String v = value.strip();
        if (v.length() > 80) throw bad(label + " must be under 80 characters.");
        return v;
    }

    private User user(String email) { return users.findByEmail(email).orElseThrow(this::missing); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found or unavailable to you."); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
