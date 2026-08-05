package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.Participant;

import java.util.List;

public interface ParticipantService {

	Participant create(Participant participant);

	Participant getById(Long id);

	List<Participant> getByTournament(Long tournamentId);

	List<Participant> getActiveByTournament(Long tournamentId);

	Participant updateStatus(Long id, String status);

}
