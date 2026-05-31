package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.MatchScoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchScoreEventRepository extends JpaRepository<MatchScoreEvent, Long> {

	List<MatchScoreEvent> findByMatchIdOrderByCreatedAtAsc(Long matchId);
}
