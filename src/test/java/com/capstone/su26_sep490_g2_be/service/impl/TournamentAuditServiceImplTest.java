package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.TournamentStatusHistoryResponse;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentStatusHistory;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.TournamentAuditChangeType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.TournamentStatusHistoryRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link TournamentAuditServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentAuditService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-55 (audit trail of tournament status changes).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentAuditService — UC-55")
class TournamentAuditServiceImplTest {

	@Mock TournamentStatusHistoryRepository historyRepository;
	@Mock UserRepository userRepository;

	@InjectMocks TournamentAuditServiceImpl service;

	private static final Long TOURNAMENT_ID = 600L;
	private static final Long USER_ID = 12L;

	private static Tournament tournament(String status) {
		return Tournament.builder().id(TOURNAMENT_ID).name("Summer Open 2026").status(status).build();
	}

	private static User userWithName(String fullName) {
		return User.builder().id(USER_ID).email("owner@btms.vn")
				.profile(UserProfile.builder().fullName(fullName).build())
				.build();
	}

	private ArgumentCaptor<TournamentStatusHistory> captureSavedEntry() {
		ArgumentCaptor<TournamentStatusHistory> captor = ArgumentCaptor.forClass(TournamentStatusHistory.class);
		verify(historyRepository).save(captor.capture());
		return captor;
	}

	// ══════════════════════════ recordChange ══════════════════════════

	@Test
	@DisplayName("TC-001 · A change made by a person is recorded as a manual one")
	void TC001_recordChange_manual() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithName("Trần Văn Owner")));

		service.recordChange(t, TournamentStatus.DRAFT.getValue(),
				TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), USER_ID, "Cập nhật trạng thái thủ công");

		TournamentStatusHistory saved = captureSavedEntry().getValue();
		assertEquals(TournamentAuditChangeType.MANUAL.getValue(), saved.getChangeType());
		assertEquals(USER_ID, saved.getChangedBy().getId());
		assertEquals(TournamentStatus.DRAFT.getValue(), saved.getFromStatus());
		assertEquals(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), saved.getToStatus());
		assertEquals("Cập nhật trạng thái thủ công", saved.getNote());
	}

	@Test
	@DisplayName("TC-002 · A change made without an actor is recorded as automatic")
	void TC002_recordChange_automatic() {
		Tournament t = tournament(TournamentStatus.OPEN_FOR_REGISTRATION.getValue());

		service.recordChange(t, TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
				TournamentStatus.REGISTRATION_CLOSED.getValue(), null, "Quá hạn đăng ký");

		TournamentStatusHistory saved = captureSavedEntry().getValue();
		assertEquals(TournamentAuditChangeType.AUTO.getValue(), saved.getChangeType());
		assertNull(saved.getChangedBy());
		verify(userRepository, never()).findById(any());
	}

	@Test
	@DisplayName("TC-003 · A change survives an actor whose account has since been removed")
	void TC003_recordChange_actorNoLongerExists() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue());
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

		service.recordChange(t, TournamentStatus.DRAFT.getValue(),
				TournamentStatus.CANCELLED.getValue(), USER_ID, "Hủy giải");

		TournamentStatusHistory saved = captureSavedEntry().getValue();
		assertNull(saved.getChangedBy());
		assertEquals(TournamentAuditChangeType.MANUAL.getValue(), saved.getChangeType(),
				"the entry still describes a human action even though the account is gone");
	}

	// ══════════════════════════ recordWarning ══════════════════════════

	@Test
	@DisplayName("TC-004 · A warning records the status without changing it")
	void TC004_recordWarning_keepsStatusOnBothSides() {
		Tournament t = tournament(TournamentStatus.REGISTRATION_CLOSED.getValue());

		service.recordWarning(t, "Quá giờ bắt đầu nhưng bracket chưa sẵn sàng");

		TournamentStatusHistory saved = captureSavedEntry().getValue();
		assertEquals(TournamentAuditChangeType.WARNING.getValue(), saved.getChangeType());
		assertEquals(saved.getFromStatus(), saved.getToStatus());
		assertEquals(TournamentStatus.REGISTRATION_CLOSED.getValue(), saved.getFromStatus());
		assertNull(saved.getChangedBy());
	}

	@Test
	@DisplayName("TC-005 · A tournament already warned about is not warned about again")
	void TC005_hasExistingWarning_true() {
		when(historyRepository.existsByTournamentIdAndChangeType(
				TOURNAMENT_ID, TournamentAuditChangeType.WARNING.getValue())).thenReturn(true);

		assertTrue(service.hasExistingWarning(TOURNAMENT_ID));
	}

	@Test
	@DisplayName("TC-006 · A tournament with no warning yet reports none")
	void TC006_hasExistingWarning_false() {
		when(historyRepository.existsByTournamentIdAndChangeType(
				TOURNAMENT_ID, TournamentAuditChangeType.WARNING.getValue())).thenReturn(false);

		assertFalse(service.hasExistingWarning(TOURNAMENT_ID));
	}

	// ══════════════════════════ getHistory ══════════════════════════

	private static TournamentStatusHistory entry(String from, String to, String changeType, User by) {
		return TournamentStatusHistory.builder()
				.id(1L).tournament(tournament(to))
				.fromStatus(from).toStatus(to).changeType(changeType)
				.changedBy(by).note("ghi chú").createdAt(Instant.now())
				.build();
	}

	@Test
	@DisplayName("TC-007 · History entries carry readable labels for status and change type")
	void TC007_getHistory_labelsAreResolved() {
		when(historyRepository.findByTournamentIdOrderByCreatedAtDesc(TOURNAMENT_ID)).thenReturn(List.of(
				entry(TournamentStatus.DRAFT.getValue(), TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
						TournamentAuditChangeType.MANUAL.getValue(), userWithName("Trần Văn Owner"))));

		TournamentStatusHistoryResponse response = service.getHistory(TOURNAMENT_ID).get(0);

		assertEquals("Nháp", response.getFromStatusLabel());
		assertEquals("Mở đăng ký", response.getToStatusLabel());
		assertEquals("Thao tác thủ công", response.getChangeTypeLabel());
		assertEquals("Trần Văn Owner", response.getChangedByName());
		assertEquals(USER_ID, response.getChangedByUserId());
	}

	@Test
	@DisplayName("TC-008 · An automatic entry is attributed to the system")
	void TC008_getHistory_automaticEntryAttributedToSystem() {
		when(historyRepository.findByTournamentIdOrderByCreatedAtDesc(TOURNAMENT_ID)).thenReturn(List.of(
				entry(TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
						TournamentStatus.REGISTRATION_CLOSED.getValue(),
						TournamentAuditChangeType.AUTO.getValue(), null)));

		TournamentStatusHistoryResponse response = service.getHistory(TOURNAMENT_ID).get(0);

		assertEquals("Hệ thống", response.getChangedByName());
		assertNull(response.getChangedByUserId());
	}

	@Test
	@DisplayName("TC-009 · An actor with no display name falls back to their email")
	void TC009_getHistory_fallsBackToEmail() {
		User noProfile = User.builder().id(USER_ID).email("owner@btms.vn").build();
		when(historyRepository.findByTournamentIdOrderByCreatedAtDesc(TOURNAMENT_ID)).thenReturn(List.of(
				entry(TournamentStatus.DRAFT.getValue(), TournamentStatus.CANCELLED.getValue(),
						TournamentAuditChangeType.MANUAL.getValue(), noProfile)));

		assertEquals("owner@btms.vn", service.getHistory(TOURNAMENT_ID).get(0).getChangedByName());
	}

	@Test
	@DisplayName("TC-010 · A status the enum does not know is shown as it was stored")
	void TC010_getHistory_unknownStatusLabelPassesThrough() {
		when(historyRepository.findByTournamentIdOrderByCreatedAtDesc(TOURNAMENT_ID)).thenReturn(List.of(
				entry("LEGACY_STATUS", TournamentStatus.CANCELLED.getValue(),
						"LEGACY_TYPE", null)));

		TournamentStatusHistoryResponse response = service.getHistory(TOURNAMENT_ID).get(0);

		assertEquals("LEGACY_STATUS", response.getFromStatusLabel(),
				"an old row must still render rather than break the history page");
		assertEquals("LEGACY_TYPE", response.getChangeTypeLabel());
	}

	@Test
	@DisplayName("TC-011 · A status stored as null is shown as null rather than crashing")
	void TC011_getHistory_nullStatusLabel() {
		when(historyRepository.findByTournamentIdOrderByCreatedAtDesc(TOURNAMENT_ID)).thenReturn(List.of(
				entry(null, TournamentStatus.DRAFT.getValue(), TournamentAuditChangeType.AUTO.getValue(), null)));

		assertNull(service.getHistory(TOURNAMENT_ID).get(0).getFromStatusLabel());
	}

	@Test
	@DisplayName("TC-012 · A tournament with no history yet returns an empty trail")
	void TC012_getHistory_empty() {
		when(historyRepository.findByTournamentIdOrderByCreatedAtDesc(TOURNAMENT_ID)).thenReturn(List.of());

		assertTrue(service.getHistory(TOURNAMENT_ID).isEmpty());
	}
}
