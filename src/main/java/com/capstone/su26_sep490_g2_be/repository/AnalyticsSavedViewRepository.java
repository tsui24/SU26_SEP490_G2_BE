package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.AnalyticsSavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalyticsSavedViewRepository extends JpaRepository<AnalyticsSavedView, Long> {

	List<AnalyticsSavedView> findByUser_IdOrderByCreatedAtDesc(Long userId);

	Optional<AnalyticsSavedView> findByIdAndUser_Id(Long id, Long userId);
}
