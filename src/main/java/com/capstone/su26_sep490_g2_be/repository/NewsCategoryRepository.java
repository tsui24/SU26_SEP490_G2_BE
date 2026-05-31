package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsCategoryRepository extends JpaRepository<NewsCategory, Long> {

	Optional<NewsCategory> findBySlug(String slug);

	boolean existsBySlug(String slug);

	List<NewsCategory> findByStatus(String status);
}
