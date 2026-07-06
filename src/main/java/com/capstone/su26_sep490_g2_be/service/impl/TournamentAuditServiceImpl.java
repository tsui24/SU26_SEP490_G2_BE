package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.TournamentStatusHistoryResponse;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentStatusHistory;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.TournamentAuditChangeType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.TournamentStatusHistoryRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TournamentAuditServiceImpl implements TournamentAuditService {

	private final TournamentStatusHistoryRepository historyRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public void recordChange(Tournament tournament, String fromStatus, String toStatus, Long changedByUserId, String note) {
		User changedBy = changedByUserId != null ? userRepository.findById(changedByUserId).orElse(null) : null;

		historyRepository.save(TournamentStatusHistory.builder()
				.tournament(tournament)
				.fromStatus(fromStatus)
				.toStatus(toStatus)
				.changeType(changedByUserId != null
						? TournamentAuditChangeType.MANUAL.getValue()
						: TournamentAuditChangeType.AUTO.getValue())
				.changedBy(changedBy)
				.note(note)
				.build());
	}

	@Override
	@Transactional
	public void recordWarning(Tournament tournament, String note) {
		String currentStatus = tournament.getStatus();
		historyRepository.save(TournamentStatusHistory.builder()
				.tournament(tournament)
				.fromStatus(currentStatus)
				.toStatus(currentStatus)
				.changeType(TournamentAuditChangeType.WARNING.getValue())
				.changedBy(null)
				.note(note)
				.build());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasExistingWarning(Long tournamentId) {
		return historyRepository.existsByTournamentIdAndChangeType(
				tournamentId, TournamentAuditChangeType.WARNING.getValue());
	}

	@Override
	@Transactional(readOnly = true)
	public List<TournamentStatusHistoryResponse> getHistory(Long tournamentId) {
		return historyRepository.findByTournamentIdOrderByCreatedAtDesc(tournamentId).stream()
				.map(this::toResponse)
				.toList();
	}

	private TournamentStatusHistoryResponse toResponse(TournamentStatusHistory h) {
		User changedBy = h.getChangedBy();
		String changedByName = "Hệ thống";
		if (changedBy != null) {
			changedByName = changedBy.getProfile() != null && changedBy.getProfile().getFullName() != null
					? changedBy.getProfile().getFullName()
					: changedBy.getEmail();
		}

		return TournamentStatusHistoryResponse.builder()
				.id(h.getId())
				.fromStatus(h.getFromStatus())
				.fromStatusLabel(statusLabel(h.getFromStatus()))
				.toStatus(h.getToStatus())
				.toStatusLabel(statusLabel(h.getToStatus()))
				.changeType(h.getChangeType())
				.changeTypeLabel(changeTypeLabel(h.getChangeType()))
				.changedByUserId(changedBy != null ? changedBy.getId() : null)
				.changedByName(changedByName)
				.note(h.getNote())
				.createdAt(h.getCreatedAt())
				.build();
	}

	private String statusLabel(String status) {
		try {
			return TournamentStatus.valueOf(status).getDisplayName();
		} catch (IllegalArgumentException | NullPointerException e) {
			return status;
		}
	}

	private String changeTypeLabel(String changeType) {
		try {
			return TournamentAuditChangeType.valueOf(changeType).getDisplayName();
		} catch (IllegalArgumentException | NullPointerException e) {
			return changeType;
		}
	}
}
