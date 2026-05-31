package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.NewsTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {

	Optional<NewsTag> findBySlug(String slug);

	boolean existsBySlug(String slug);
}
