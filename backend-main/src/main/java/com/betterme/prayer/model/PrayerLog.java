package com.betterme.prayer.model;

import com.betterme.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row = "this user marked this prayer as performed for this calendar day".
 * No row means "not logged", never "missed".
 * UML fields: prayerName, prayedAt. Extension needing team sign-off: prayerDate (the day it counts toward).
 */
@Entity
@Table(name = "prayer_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_prayer_log_day", columnNames = {"user_id", "prayer_date", "prayer_name"}),
        indexes = @Index(name = "idx_prayer_log_user_date", columnList = "user_id,prayer_date"))
public class PrayerLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(name = "prayer_name", nullable = false, length = 16)
    private String prayerName;
    @Column(name = "prayer_date", nullable = false)
    private LocalDate prayerDate;
    @Column(name = "prayed_at", nullable = false)
    private Instant prayedAt = Instant.now();

    public PrayerLog() {}

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPrayerName() { return prayerName; }
    public void setPrayerName(String prayerName) { this.prayerName = prayerName; }
    public LocalDate getPrayerDate() { return prayerDate; }
    public void setPrayerDate(LocalDate prayerDate) { this.prayerDate = prayerDate; }
    public Instant getPrayedAt() { return prayedAt; }
}
