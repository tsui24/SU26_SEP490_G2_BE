package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.Tournament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TournamentService {

	Tournament create(Tournament tournament);

	Tournament getById(Long id);

	Page<Tournament> listByStatus(String status, Pageable pageable);

	Tournament update(Long id, Tournament tournament);

	void updateStatus(Long id, String newStatus);

	void delete(Long id);
}
