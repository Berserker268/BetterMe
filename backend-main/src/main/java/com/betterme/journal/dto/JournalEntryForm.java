package com.betterme.journal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class JournalEntryForm {

    @Size(max = 120, message = "Title cannot exceed 120 characters")
    private String title;

    @NotBlank(message = "Write at least something before saving today's reflection")
    @Size(max = 5000, message = "Reflection cannot exceed 5000 characters")
    private String text;

    private String mood = "Calm";

    private boolean privateNote;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public boolean isPrivateNote() {
        return privateNote;
    }

    public void setPrivateNote(boolean privateNote) {
        this.privateNote = privateNote;
    }
}
