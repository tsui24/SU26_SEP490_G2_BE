package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {

	Optional<NewsPost> findBySlug(String slug);

	boolean existsBySlug(String slug);

	Page<NewsPost> findByStatus(String status, Pageable pageable);

	Page<NewsPost> findByCategoryIdAndStatus(Long categoryId, String status, Pageable pageable);
}
