package com.betterme.prayer.controller;

import com.betterme.prayer.service.PrayerService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.betterme.prayer.dto.PrayerViews.*;

@RestController
@RequestMapping("/prayer/api")
public class PrayerApiController {
    private final PrayerService prayer;
    public PrayerApiController(PrayerService prayer) { this.prayer = prayer; }

    public record LogInput(String prayer, String date, boolean completed, String tz) {}

    @GetMapping("/day")
    public Day day(Authentication auth,
                   @RequestParam(name = "date", required = false) String date,
                   @RequestParam(name = "city", required = false) String city,
                   @RequestParam(name = "country", required = false) String country,
                   @RequestParam(name = "method", required = false) Integer method,
                   @RequestParam(name = "school", required = false) Integer school,
                   @RequestParam(name = "tz", required = false) String tz) {
        return prayer.day(auth.getName(), date, city, country, method, school, tz);
    }

    @PutMapping("/log")
    public Logged log(Authentication auth, @RequestBody LogInput input) {
        return prayer.log(auth.getName(), input.prayer(), input.date(), input.completed(), input.tz());
    }

    @GetMapping("/stats")
    public Summary stats(Authentication auth, @RequestParam(name = "tz", required = false) String tz) {
        return prayer.summary(auth.getName(), tz);
    }
}
