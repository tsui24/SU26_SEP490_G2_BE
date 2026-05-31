package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentStageRepository extends JpaRepository<TournamentStage, Long> {

	List<TournamentStage> findByTournamentIdOrderByOrderNoAsc(Long tournamentId);
}
