package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.PlayerYearlySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerYearlySummaryRepository extends JpaRepository<PlayerYearlySummary, Long> {

	Optional<PlayerYearlySummary> findByUserIdAndYear(Long userId, Integer year);

	List<PlayerYearlySummary> findByUserIdOrderByYearDesc(Long userId);
}
