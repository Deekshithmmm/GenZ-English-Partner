package com.genzenglish.partner.practice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnRepository extends JpaRepository<Turn, UUID> {

    List<Turn> findBySessionIdOrderBySeqAsc(UUID sessionId);

    Optional<Turn> findBySessionIdAndSeq(UUID sessionId, int seq);

    int countBySessionId(UUID sessionId);
}
