package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;

import java.util.List;

public interface OwnerTournamentService {

	PageResponse<TournamentListItemResponse> listTournaments(
			Long userId,
			boolean filterByOwner,
			String status,
			String search,
			String gameType,
			String participantType,
			Boolean isRegister,
			Long branchId,
			int page,
			int size);

	OwnerFormatListResponse listFormats();

	OwnerGameTypeListResponse listGameTypes();

	OwnerRegistrationFormTemplateListResponse listRegistrationFormTemplates();

	RegistrationFormPreviewResponse previewRegistrationFormTemplate(Long templateId);

	CreateTournamentResponse createTournament(Long userId, CreateTournamentRequest request);

	UpdateTournamentResponse updateTournament(Long userId, Long tournamentId, UpdateTournamentRequest request,
	                                        boolean enforceOwnership);

	TournamentDetailResponse getTournament(Long userId, Long tournamentId, boolean enforceOwnership);

	TournamentConfigFormResponse getConfigForm(Long userId, Long tournamentId, boolean enforceOwnership);

	/**
	 * {@code sePhaseSizePreview} (DOUBLE_ELIMINATION) — ghi đè tạm thời giá trị {@code se_phase_size}
	 * ĐÃ LƯU khi lọc "Số ván thắng theo vòng đấu" chỉ còn đúng vòng giải thật sự đấu. Cho phép Owner
	 * xem trước danh sách vòng đổi ngay khi gõ số vào ô "Số người vào vòng loại trực tiếp" ở wizard
	 * bước 2, mà KHÔNG cần lưu config trước — trước đây danh sách chỉ đổi sau khi bấm lưu, vì
	 * {@code getConfigForm} chỉ đọc được giá trị đã lưu trong DB. {@code null} = dùng giá trị đã lưu
	 * (hoặc mặc định 8) như cũ.
	 */
	TournamentConfigFormResponse getConfigForm(Long userId, Long tournamentId, boolean enforceOwnership,
			Integer sePhaseSizePreview);

	SaveTournamentConfigResponse saveConfig(Long userId, Long tournamentId, SaveTournamentConfigRequest request,
	                                        boolean enforceOwnership);

	TournamentConfigResolvedResponse getResolvedConfig(Long userId, Long tournamentId, boolean enforceOwnership);

	TournamentConfigValidateResponse validateConfig(Long userId, Long tournamentId, boolean enforceOwnership);

	PatchTournamentStatusResponse patchStatus(Long userId, Long tournamentId, PatchTournamentStatusRequest request,
	                                          boolean enforceOwnership);

	PatchTournamentVisibilityResponse updateVisibility(Long userId, Long tournamentId,
	                                          PatchTournamentVisibilityRequest request, boolean enforceOwnership);

	List<TournamentStatusHistoryResponse> getStatusHistory(Long userId, Long tournamentId, boolean enforceOwnership);

	RegistrationFormPreviewResponse getTournamentRegistrationForm(
			Long userId, Long tournamentId, boolean enforceOwnership);

	PageResponse<TournamentListItemResponse> listPlayerTournaments(String status, String search, int page, int size);

	TournamentDetailResponse getPlayerTournamentDetail(Long tournamentId);
}
