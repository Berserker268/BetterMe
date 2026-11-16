package com.betterme.journal.repository;

import com.betterme.journal.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdAndUserId(Long id, Long userId);

    Page<JournalEntry> findByUserId(Long userId, Pageable pageable);

    Page<JournalEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate, Pageable pageable);

    Optional<JournalEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);
}
