package com.genzenglish.partner.practice;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

    Optional<PracticeSession> findByIdAndUserId(UUID id, UUID userId);

    List<PracticeSession> findByUserIdAndTypeAndStatus(UUID userId, SessionType type, SessionStatus status);

    List<PracticeSession> findByUserIdAndStatusAndTypeIn(UUID userId, SessionStatus status,
            Collection<SessionType> types);

    /** Completed practice sessions credited to one of the learner's local days. */
    List<PracticeSession> findByUserIdAndStatusAndLocalDate(UUID userId, SessionStatus status, LocalDate localDate);

    /** Every day the learner completed practice, newest first: the input for streaks. */
    @Query("""
            select distinct s.localDate from PracticeSession s
            where s.userId = :userId and s.status = :status and s.localDate is not null
            order by s.localDate desc""")
    List<LocalDate> findPracticeDays(UUID userId, SessionStatus status);

    @Query("""
            select coalesce(sum(s.xp), 0) from PracticeSession s
            where s.userId = :userId and s.status = :status""")
    long sumXp(UUID userId, SessionStatus status);
}
