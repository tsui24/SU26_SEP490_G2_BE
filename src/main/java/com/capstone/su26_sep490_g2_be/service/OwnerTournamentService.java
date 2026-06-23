package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;

public interface OwnerTournamentService {

	PageResponse<TournamentListItemResponse> listTournaments(
			Long userId,
			boolean filterByOwner,
			String status,
			String search,
			int page,
			int size);

	OwnerFormatListResponse listFormats();

	OwnerGameTypeListResponse listGameTypes();

	OwnerRegistrationFormTemplateListResponse listRegistrationFormTemplates();

	CreateTournamentResponse createTournament(Long userId, CreateTournamentRequest request);

	UpdateTournamentResponse updateTournament(Long userId, Long tournamentId, UpdateTournamentRequest request,
	                                        boolean enforceOwnership);

	TournamentDetailResponse getTournament(Long userId, Long tournamentId, boolean enforceOwnership);

	TournamentConfigFormResponse getConfigForm(Long userId, Long tournamentId, boolean enforceOwnership);

	SaveTournamentConfigResponse saveConfig(Long userId, Long tournamentId, SaveTournamentConfigRequest request,
	                                        boolean enforceOwnership);

	TournamentConfigResolvedResponse getResolvedConfig(Long userId, Long tournamentId, boolean enforceOwnership);

	TournamentConfigValidateResponse validateConfig(Long userId, Long tournamentId, boolean enforceOwnership);

	PatchTournamentStatusResponse patchStatus(Long userId, Long tournamentId, PatchTournamentStatusRequest request,
	                                          boolean enforceOwnership);

	RegistrationFormPreviewResponse getTournamentRegistrationForm(
			Long userId, Long tournamentId, boolean enforceOwnership);

	PageResponse<TournamentListItemResponse> listPlayerTournaments(String status, String search, int page, int size);

	TournamentDetailResponse getPlayerTournamentDetail(Long tournamentId);
}
