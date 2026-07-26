package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacebookPostRepository extends JpaRepository<FacebookPost, Long> {

	@EntityGraph(attributePaths = { "tournament" })
	List<FacebookPost> findByTournamentIdOrderByPostedAtDesc(Long tournamentId);

	@EntityGraph(attributePaths = { "tournament" })
	List<FacebookPost> findByTournamentIdIn(List<Long> tournamentIds);

	@EntityGraph(attributePaths = { "tournament" })
	Page<FacebookPost> findAllByOrderByPostedAtDesc(Pageable pageable);

	Optional<FacebookPost> findByFacebookPostId(String facebookPostId);
}
