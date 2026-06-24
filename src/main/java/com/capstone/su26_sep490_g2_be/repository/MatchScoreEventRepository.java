package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchScoreEventRepository extends JpaRepository<MatchScoreEvent, Long> {

    @Query("""
        SELECT e FROM MatchScoreEvent e
        LEFT JOIN FETCH e.createdBy
        WHERE e.match.id = :matchId
        ORDER BY e.createdAt ASC
        """)
    List<MatchScoreEvent> findByMatchIdOrderByCreatedAtAsc(@Param("matchId") Long matchId);
}
