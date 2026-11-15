package com.betterme.journal.service;

import com.betterme.journal.dto.JournalEntryForm;
import com.betterme.journal.model.JournalEntry;
import com.betterme.journal.repository.JournalEntryRepository;
import com.betterme.user.User;
import com.betterme.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class JournalService {

    public static final List<String> MOODS = List.of(
            "Great",
            "Calm",
            "Thoughtful",
            "Tired",
            "Motivated"
    );

    private final JournalEntryRepository journalEntryRepository;
    private final UserRepository userRepository;

    public JournalService(JournalEntryRepository journalEntryRepository,
                          UserRepository userRepository) {
        this.journalEntryRepository = journalEntryRepository;
        this.userRepository = userRepository;
    }

    public Optional<JournalEntry> getEntry(String email, LocalDate date) {
        User user = getUser(email);
        return journalEntryRepository.findByUserIdAndEntryDate(user.getId(), date);
    }

    public JournalEntry saveTodayEntry(String email, JournalEntryForm form) {
        User user = getUser(email);
        LocalDate today = LocalDate.now();
        String text = clean(form.getText());

        if (text.isBlank()) {
            throw new IllegalArgumentException("Reflection text is required");
        }

        JournalEntry entry = journalEntryRepository
                .findByUserIdAndEntryDate(user.getId(), today)
                .orElseGet(JournalEntry::new);

        entry.setUser(user);
        entry.setEntryDate(today);
        entry.setTitle(resolveTitle(form.getTitle()));
        entry.setText(text);
        entry.setMood(resolveMood(form.getMood()));
        entry.setPrivateNote(form.isPrivateNote());

        return journalEntryRepository.save(entry);
    }

    public JournalEntryForm toForm(Optional<JournalEntry> entry) {
        JournalEntryForm form = new JournalEntryForm();

        entry.ifPresent(journalEntry -> {
            form.setTitle(journalEntry.getTitle());
            form.setText(journalEntry.getText());
            form.setMood(journalEntry.getMood());
            form.setPrivateNote(journalEntry.isPrivateNote());
        });

        return form;
    }

    private String resolveTitle(String title) {
        String cleanedTitle = clean(title);
        return cleanedTitle.isBlank() ? "Today's Reflection" : cleanedTitle;
    }

    private String resolveMood(String mood) {
        String cleanedMood = clean(mood);
        return MOODS.contains(cleanedMood) ? cleanedMood : "Calm";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
