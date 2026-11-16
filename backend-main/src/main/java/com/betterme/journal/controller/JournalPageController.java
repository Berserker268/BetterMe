package com.betterme.journal.controller;

import com.betterme.journal.dto.JournalEntryForm;
import com.betterme.journal.model.JournalEntry;
import com.betterme.journal.service.JournalService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Controller
public class JournalPageController {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");

    private final JournalService journalService;

    public JournalPageController(JournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping("/journal")
    public String journalPage(Authentication authentication, Model model) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Optional<JournalEntry> todayEntry = journalService.getEntry(authentication.getName(), today);
        Optional<JournalEntry> yesterdayEntry = journalService.getEntry(authentication.getName(), yesterday);

        if (!model.containsAttribute("journalEntryForm")) {
            model.addAttribute("journalEntryForm", journalService.toForm(todayEntry));
        }

        addPageAttributes(model, today, yesterday, todayEntry, yesterdayEntry);
        return "journal";
    }

    @PostMapping("/journal")
    public String saveTodayEntry(
            @Valid @ModelAttribute("journalEntryForm") JournalEntryForm form,
            BindingResult result,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        if (result.hasErrors()) {
            addPageAttributes(
                    model,
                    today,
                    yesterday,
                    journalService.getEntry(authentication.getName(), today),
                    journalService.getEntry(authentication.getName(), yesterday)
            );
            return "journal";
        }

        journalService.saveTodayEntry(authentication.getName(), form);
        redirectAttributes.addFlashAttribute("successMessage", "Today's reflection has been saved.");

        return "redirect:/journal";
    }

    @PostMapping("/journal/delete/{id}")
    public String deleteEntry(@PathVariable("id") Long id,
                              @RequestParam(name = "history", defaultValue = "false") boolean history,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        journalService.deleteEntry(authentication.getName(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Journal entry deleted.");
        return history ? "redirect:/journal?history=true" : "redirect:/journal";
    }

    @GetMapping("/journal/history")
    public String history(Authentication authentication, Model model,
                          @RequestParam(name = "page", defaultValue = "0") int page,
                          @RequestParam(name = "date", required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        model.addAttribute("historyPage", journalService.getHistory(authentication.getName(), date, page));
        model.addAttribute("historyDate", date);
        return "fragments/journal-history :: entries";
    }

    private void addPageAttributes(Model model,
                                   LocalDate today,
                                   LocalDate yesterday,
                                   Optional<JournalEntry> todayEntry,
                                   Optional<JournalEntry> yesterdayEntry) {
        model.addAttribute("today", today);
        model.addAttribute("yesterday", yesterday);
        model.addAttribute("todayLabel", today.format(DISPLAY_DATE));
        model.addAttribute("yesterdayLabel", yesterday.format(DISPLAY_DATE));
        model.addAttribute("todayEntry", todayEntry.orElse(null));
        model.addAttribute("yesterdayEntry", yesterdayEntry.orElse(null));
        model.addAttribute("moods", JournalService.MOODS);
    }
}
