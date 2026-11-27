package com.betterme.prayer.repository;

import com.betterme.prayer.model.PrayerLog;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.List;

public interface PrayerLogRepository extends JpaRepository<PrayerLog, Long> {
    boolean existsByUserIdAndPrayerDateAndPrayerName(Long userId, LocalDate date, String prayerName);
    long countByUserIdAndPrayerDate(Long userId, LocalDate date);

    @Query("select l.prayerName from PrayerLog l where l.user.id = :userId and l.prayerDate = :date")
    List<String> namesForDay(Long userId, LocalDate date);

    @Query("select l from PrayerLog l where l.user.id = :userId and l.prayerDate between :from and :to")
    List<PrayerLog> inRange(Long userId, LocalDate from, LocalDate to);

    @Modifying
    @Query("delete from PrayerLog l where l.user.id = :userId and l.prayerDate = :date and l.prayerName = :prayerName")
    void deleteOne(Long userId, LocalDate date, String prayerName);
}
