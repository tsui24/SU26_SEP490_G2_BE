package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.TournamentFinanceEntryRequest;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentFinanceEntryResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentFinanceSummaryResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentFinanceEntry;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.FinanceEntryType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentFinanceEntryRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.TournamentFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TournamentFinanceServiceImpl implements TournamentFinanceService {

	private final TournamentFinanceEntryRepository financeEntryRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;
	private final BranchAccessService branchAccessService;

	@Override
	@Transactional(readOnly = true)
	public TournamentFinanceSummaryResponse getSummary(Long tournamentId, Long actorUserId) {
		Tournament tournament = loadTournament(tournamentId);
		assertAccess(actorUserId, tournament);

		List<TournamentFinanceEntry> entries =
				financeEntryRepository.findByTournamentIdOrderByOccurredAtDescIdDesc(tournamentId);
		return toSummary(tournamentId, entries);
	}

	@Override
	@Transactional
	public TournamentFinanceEntryResponse create(Long tournamentId, Long actorUserId, TournamentFinanceEntryRequest request) {
		Tournament tournament = loadTournament(tournamentId);
		assertAccess(actorUserId, tournament);
		assertMutable(tournament);
		User actor = userRepository.findById(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		TournamentFinanceEntry entry = TournamentFinanceEntry.builder()
				.tournament(tournament)
				.entryType(parseEntryType(request.getEntryType()).getValue())
				.label(request.getLabel().trim())
				.amount(request.getAmount())
				.note(request.getNote())
				.occurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now())
				.createdBy(actor)
				.build();
		return toResponse(financeEntryRepository.save(entry));
	}

	@Override
	@Transactional
	public TournamentFinanceEntryResponse update(Long tournamentId, Long entryId, Long actorUserId, TournamentFinanceEntryRequest request) {
		Tournament tournament = loadTournament(tournamentId);
		assertAccess(actorUserId, tournament);
		assertMutable(tournament);
		TournamentFinanceEntry entry = loadEntry(tournamentId, entryId);

		entry.setEntryType(parseEntryType(request.getEntryType()).getValue());
		entry.setLabel(request.getLabel().trim());
		entry.setAmount(request.getAmount());
		entry.setNote(request.getNote());
		entry.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : entry.getOccurredAt());
		return toResponse(financeEntryRepository.save(entry));
	}

	@Override
	@Transactional
	public void delete(Long tournamentId, Long entryId, Long actorUserId) {
		Tournament tournament = loadTournament(tournamentId);
		assertAccess(actorUserId, tournament);
		assertMutable(tournament);
		TournamentFinanceEntry entry = loadEntry(tournamentId, entryId);
		financeEntryRepository.delete(entry);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, FinanceTotals> sumByTournamentIds(List<Long> tournamentIds) {
		if (tournamentIds == null || tournamentIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, BigDecimal[]> raw = new HashMap<>();
		for (Object[] row : financeEntryRepository.sumAmountsByTournamentIds(tournamentIds)) {
			Long tid = (Long) row[0];
			String type = (String) row[1];
			BigDecimal sum = (BigDecimal) row[2];
			BigDecimal[] slot = raw.computeIfAbsent(tid, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
			if (FinanceEntryType.INCOME.getValue().equals(type)) {
				slot[0] = sum;
			} else if (FinanceEntryType.EXPENSE.getValue().equals(type)) {
				slot[1] = sum;
			}
		}
		Map<Long, FinanceTotals> result = new HashMap<>();
		raw.forEach((tid, slot) -> result.put(tid, new FinanceTotals(slot[0], slot[1])));
		return result;
	}

	private Tournament loadTournament(Long tournamentId) {
		return tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	/** entry phải thuộc đúng giải trên URL — chặn dò entryId sang giải khác không có quyền. */
	private TournamentFinanceEntry loadEntry(Long tournamentId, Long entryId) {
		TournamentFinanceEntry entry = financeEntryRepository.findById(entryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!entry.getTournament().getId().equals(tournamentId)) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}
		return entry;
	}

	/** Chốt sổ thu/chi cùng lúc giải đóng — giải Hoàn thành/Đã hủy không cho thêm/sửa/xóa nữa, để
	 * số liệu khớp với báo cáo Analytics đã tính (net profit) tại thời điểm giải kết thúc. Các
	 * trạng thái khác (kể cả DRAFT) vẫn cho ghi nhận thoải mái vì chi phí thực tế (thuê bàn, in ấn,
	 * tài trợ...) có thể phát sinh/xác nhận trước hoặc trong lúc giải diễn ra. */
	private void assertMutable(Tournament tournament) {
		String status = tournament.getStatus();
		if (TournamentStatus.COMPLETED.getValue().equals(status)
				|| TournamentStatus.CANCELLED.getValue().equals(status)) {
			throw new BusinessException(ErrorCode.TOURNAMENT_FINANCE_LOCKED);
		}
	}

	private void assertAccess(Long actorUserId, Tournament tournament) {
		if (actorUserId == null) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		User actor = userRepository.findById(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		Branch branch = tournament.getBranch();
		Long branchId = branch != null ? branch.getId() : null;
		if (!branchAccessService.canActorAccessBranch(actor, branchId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
	}

	private FinanceEntryType parseEntryType(String raw) {
		try {
			return FinanceEntryType.valueOf(raw == null ? "" : raw.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST);
		}
	}

	private TournamentFinanceSummaryResponse toSummary(Long tournamentId, List<TournamentFinanceEntry> entries) {
		BigDecimal totalIncome = BigDecimal.ZERO;
		BigDecimal totalExpense = BigDecimal.ZERO;
		List<TournamentFinanceEntryResponse> items = new java.util.ArrayList<>();
		for (TournamentFinanceEntry e : entries) {
			if (FinanceEntryType.INCOME.getValue().equals(e.getEntryType())) {
				totalIncome = totalIncome.add(e.getAmount());
			} else {
				totalExpense = totalExpense.add(e.getAmount());
			}
			items.add(toResponse(e));
		}
		return TournamentFinanceSummaryResponse.builder()
				.tournamentId(tournamentId)
				.totalIncome(totalIncome)
				.totalExpense(totalExpense)
				.netAmount(totalIncome.subtract(totalExpense))
				.entries(items)
				.build();
	}

	private TournamentFinanceEntryResponse toResponse(TournamentFinanceEntry e) {
		User createdBy = e.getCreatedBy();
		String createdByName = null;
		if (createdBy != null) {
			createdByName = createdBy.getProfile() != null && createdBy.getProfile().getFullName() != null
					&& !createdBy.getProfile().getFullName().isBlank()
					? createdBy.getProfile().getFullName()
					: createdBy.getEmail();
		}
		FinanceEntryType type = FinanceEntryType.valueOf(e.getEntryType());
		return TournamentFinanceEntryResponse.builder()
				.id(e.getId())
				.entryType(e.getEntryType())
				.entryTypeLabel(type.getDisplayName())
				.label(e.getLabel())
				.amount(e.getAmount())
				.note(e.getNote())
				.occurredAt(e.getOccurredAt())
				.createdByUserId(createdBy != null ? createdBy.getId() : null)
				.createdByName(createdByName)
				.createdAt(e.getCreatedAt())
				.build();
	}
}
