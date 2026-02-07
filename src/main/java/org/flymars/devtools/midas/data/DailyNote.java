package org.flymars.devtools.midas.data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily work note for supplementary weekly report content
 */
public class DailyNote {
    private LocalDate date;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DailyNote() {
    }

    public DailyNote(LocalDate date, String content) {
        this.date = date;
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
