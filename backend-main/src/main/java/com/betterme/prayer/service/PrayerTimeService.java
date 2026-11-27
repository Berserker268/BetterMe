package com.betterme.prayer.service;

import com.betterme.prayer.model.PrayerName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Aladhan client (RestClient, like DailyQuoteService). On ANY failure it returns unavailable - never invented times. */
@Service
public class PrayerTimeService {
    private static final Logger log = LoggerFactory.getLogger(PrayerTimeService.class);
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})"); // Aladhan may append e.g. " (+06)"
    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final int CACHE_MAX = 500;

    public record Timetable(boolean available, String message, Map<PrayerName, LocalTime> times,
                            String timezone, String methodName) {
        public static Timetable ok(Map<PrayerName, LocalTime> times, String tz, String method) {
            return new Timetable(true, null, Map.copyOf(times), tz, method);
        }
        public static Timetable unavailable(String message) { return new Timetable(false, message, Map.of(), null, null); }
    }
    private record Cached(Timetable value, Instant expiresAt) {}

    private final RestClient client;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final String defaultCity, defaultCountry, defaultZone;
    private final int defaultMethod, defaultSchool;

    public PrayerTimeService(@Value("${betterme.prayer.api-base-url:https://api.aladhan.com}") String baseUrl,
                             @Value("${betterme.prayer.default-city:Dhaka}") String defaultCity,
                             @Value("${betterme.prayer.default-country:Bangladesh}") String defaultCountry,
                             @Value("${betterme.prayer.default-method:1}") int defaultMethod,
                             @Value("${betterme.prayer.default-school:1}") int defaultSchool,
                             @Value("${betterme.prayer.default-zone:Asia/Dhaka}") String defaultZone) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.defaultCity = defaultCity;
        this.defaultCountry = defaultCountry;
        this.defaultMethod = defaultMethod;
        this.defaultSchool = defaultSchool;
        this.defaultZone = defaultZone;
    }

    public String defaultCity() { return defaultCity; }
    public String defaultCountry() { return defaultCountry; }
    public int defaultMethod() { return defaultMethod; }
    public int defaultSchool() { return defaultSchool; }
    public String defaultZone() { return defaultZone; }

    /** @param school 0 = standard Asr, 1 = Hanafi Asr */
    public Timetable timetable(LocalDate date, String city, String country, int method, int school) {
        String key = date + "|" + city.toLowerCase() + "|" + country.toLowerCase() + "|" + method + "|" + school;
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(Instant.now())) return hit.value();
        try {
            Map<String, Object> body = client.get()
                    .uri(b -> b.path("/v1/timingsByCity/{date}")
                            .queryParam("city", "{city}").queryParam("country", "{country}")
                            .queryParam("method", "{method}").queryParam("school", "{school}")
                            .build(API_DATE.format(date), city, country, method, school))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            Timetable parsed = parse(body);
            if (parsed.available()) {
                if (cache.size() >= CACHE_MAX) cache.clear();
                cache.put(key, new Cached(parsed, Instant.now().plus(CACHE_TTL)));
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Aladhan lookup failed for {}, {} on {}: {}", city, country, date, e.toString());
            return Timetable.unavailable("Prayer times unavailable right now. Check the city and country, then try again.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null; }

    /** Package-private so the parsing rules can be unit tested without the network. */
    Timetable parse(Map<String, Object> root) {
        if (root == null) return Timetable.unavailable("Prayer times unavailable: empty response.");
        if (root.get("code") instanceof Number n && n.intValue() != 200)
            return Timetable.unavailable("Prayer times unavailable: that city and country weren't found.");
        Map<String, Object> data = map(root.get("data"));
        Map<String, Object> timings = data == null ? null : map(data.get("timings"));
        if (timings == null) return Timetable.unavailable("Prayer times unavailable: unexpected response.");

        Map<PrayerName, LocalTime> times = new EnumMap<>(PrayerName.class);
        for (PrayerName p : PrayerName.values()) {
            if (!(timings.get(p.apiKey()) instanceof String raw))
                return Timetable.unavailable("Prayer times unavailable: " + p.label() + " was missing.");
            Matcher m = TIME.matcher(raw);
            if (!m.find()) return Timetable.unavailable("Prayer times unavailable: unreadable " + p.label() + " time.");
            int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
            if (h > 23 || min > 59) return Timetable.unavailable("Prayer times unavailable: invalid " + p.label() + " time.");
            times.put(p, LocalTime.of(h, min));
        }
        Map<String, Object> meta = map(data.get("meta"));
        String tz = meta != null && meta.get("timezone") instanceof String s ? s : null;
        Map<String, Object> method = meta == null ? null : map(meta.get("method"));
        String methodName = method != null && method.get("name") instanceof String s ? s : null;
        return Timetable.ok(times, tz, methodName);
    }
}
