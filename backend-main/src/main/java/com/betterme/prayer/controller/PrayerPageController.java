package com.betterme.prayer.controller;

import com.betterme.prayer.service.PrayerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PrayerPageController {
    private final PrayerService prayer;
    public PrayerPageController(PrayerService prayer) { this.prayer = prayer; }

    @GetMapping("/prayer")
    public String page(Model model) {
        model.addAttribute("prayerDefaults", prayer.defaults());
        return "prayer";
    }
}
