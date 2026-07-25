package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long>, JpaSpecificationExecutor<NewsPost> {

	Optional<NewsPost> findBySlug(String slug);

	boolean existsBySlug(String slug);

	@Override
	@EntityGraph(attributePaths = { "category" })
	Page<NewsPost> findAll(Specification<NewsPost> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "category" })
	Page<NewsPost> findByDeletedFalse(Pageable pageable);

	List<NewsPost> findByTags_Id(Long tagId);
}
