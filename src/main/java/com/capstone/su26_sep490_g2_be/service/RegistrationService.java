package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.CheckoutResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRegistrationResponse;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegistrationService {

	Registration register(Long tournamentId, Long userId, Registration registration);

	TournamentRegistrationResponse submitRegistration(
			Long tournamentId, Long userId, SubmitTournamentRegistrationRequest request);

	TournamentRegistrationResponse getRegistrationDetail(Long registrationId, Long requestingUserId, boolean isStaff);

	PageResponse<TournamentRegistrationResponse> getMyRegistrations(Long userId, int page, int size);

	PageResponse<TournamentRegistrationResponse> getTournamentRegistrations(
			Long tournamentId, String status, int page, int size);

	Registration getById(Long id);

	Page<Registration> getByTournament(Long tournamentId, Pageable pageable);

	Page<Registration> getByTournamentAndStatus(Long tournamentId, String status, Pageable pageable);

	Page<Registration> getByUser(Long userId, Pageable pageable);

	TournamentRegistrationResponse approve(Long registrationId, Long approvedByUserId);

	TournamentRegistrationResponse reject(Long registrationId, RejectRegistrationRequest request);

	void cancel(Long registrationId, Long requestingUserId);

	TournamentRegistrationResponse getMyRegistrationForTournament(Long tournamentId, Long userId);

	CheckoutResponse checkout(Long registrationId, Long userId);

	void markAsPaid(long orderCode, String transactionRef);
}
