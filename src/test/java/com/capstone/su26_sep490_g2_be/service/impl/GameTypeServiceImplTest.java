package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.GameTypeDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.GameTypeDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link GameTypeServiceImpl}.
 *
 * <p>Mirrors the <b>GameTypeService</b> sheet in Report 5.1_UnitTests_L1.xlsx one row per test.
 * Spec source: UCS Report 3.1 — UC-11 (FT-06, Wave 1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · GameTypeService — UC-11")
class GameTypeServiceImplTest {

	@Mock GameTypeDefinitionRepository repository;

	@InjectMocks GameTypeServiceImpl gameTypeService;

	private static final String CODE = "9_BALL";

	private static GameTypeDefinition gameType(String code, String name, boolean active) {
		return GameTypeDefinition.builder()
				.code(code).name(name)
				.defaultRaceTo(7)
				.isActive(active)
				.sortOrder(5)
				.build();
	}

	// ══════════════════ getActiveGameTypes / getAllGameTypes — UC-11.2 ══════════════════

	@Test
	@DisplayName("TC-001 · Only active game types are offered for selection")
	void TC001_getActiveGameTypes_returnsActiveOnly() {
		when(repository.findByIsActiveTrueOrderByCreatedAtAsc())
				.thenReturn(List.of(gameType(CODE, "9-Ball", true), gameType("10_BALL", "10-Ball", true)));

		List<GameTypeDefinition> result = gameTypeService.getActiveGameTypes();

		assertEquals(2, result.size());
		// A deactivated game type leaking in here would be selectable when creating a tournament
		verify(repository).findByIsActiveTrueOrderByCreatedAtAsc();
		verify(repository, never()).findAll();
	}

	@Test
	@DisplayName("TC-002 · No active game type exists")
	void TC002_getActiveGameTypes_emptyResult() {
		when(repository.findByIsActiveTrueOrderByCreatedAtAsc()).thenReturn(List.of());

		assertTrue(gameTypeService.getActiveGameTypes().isEmpty());
	}

	@Test
	@DisplayName("TC-003 · The admin list includes inactive entries")
	void TC003_getAllGameTypes_includesInactive() {
		when(repository.findAll())
				.thenReturn(List.of(gameType(CODE, "9-Ball", true), gameType("SNOOKER", "Snooker", false)));

		List<GameTypeDefinition> result = gameTypeService.getAllGameTypes();

		assertEquals(2, result.size());
		assertTrue(result.stream().anyMatch(g -> !g.getIsActive()),
				"An Admin has to see disabled game types in order to re-enable them");
	}

	// ══════════════════════════ getByCode — UC-11.2 ══════════════════════════

	@Test
	@DisplayName("TC-004 · Opens a game type by its code")
	void TC004_getByCode_happyPath() {
		when(repository.findById(CODE)).thenReturn(Optional.of(gameType(CODE, "9-Ball", true)));

		GameTypeDefinition result = gameTypeService.getByCode(CODE);

		assertEquals(CODE, result.getCode());
		assertEquals("9-Ball", result.getName());
		assertEquals(7, result.getDefaultRaceTo());
	}

	@Test
	@DisplayName("TC-005 · Code does not exist")
	void TC005_getByCode_notFound() {
		when(repository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> gameTypeService.getByCode("NO_SUCH"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ create — UC-11.1 ══════════════════════════

	@Test
	@DisplayName("TC-006 · Creating a game type with a fresh code")
	void TC006_create_happyPath() {
		GameTypeDefinition fresh = gameType(CODE, "9-Ball", true);
		when(repository.save(any(GameTypeDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		GameTypeDefinition saved = gameTypeService.create(fresh);

		assertEquals(CODE, saved.getCode());
		assertTrue(saved.getIsActive());
		verify(repository, times(1)).save(fresh);
	}

	/**
	 * DEF-W1-01 — EXPECTED TO FAIL.
	 *
	 * <p>UC-11.1 BR-01 makes the code unique and AF-01 requires a duplicate to be refused.
	 * {@code create} calls {@code repository.save()} with no existence check, and JPA
	 * {@code save()} on an existing primary key issues an UPDATE — so a duplicate code silently
	 * OVERWRITES the previous game type. Compare {@code ConfigFieldDefinitionServiceImpl.create},
	 * which does guard with {@code existsById}. This is data loss, not a cosmetic gap.
	 */
	@Test
	@DisplayName("TC-007 · Duplicate code must be rejected")
	void TC007_create_duplicateCode_rejected() {
		when(repository.existsById(CODE)).thenReturn(true);

		assertThrows(BusinessException.class,
				() -> gameTypeService.create(gameType(CODE, "Nine Ball Renamed", true)),
				"UC-11.1 AF-01 requires a duplicate game type code to be refused");

		verify(repository, never()).save(any());
	}

	// ══════════════════════════ update — UC-11.3 ══════════════════════════

	@Test
	@DisplayName("TC-008 · Editable attributes are saved")
	void TC008_update_savesEditableAttributes() {
		GameTypeDefinition existing = gameType(CODE, "9-Ball", true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		when(repository.save(any(GameTypeDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		GameTypeDefinition payload = GameTypeDefinition.builder()
				.name("Nine Ball").description("Rotation game").defaultRaceTo(9).build();

		GameTypeDefinition result = gameTypeService.update(CODE, payload);

		assertEquals("Nine Ball", result.getName());
		assertEquals("Rotation game", result.getDescription());
		assertEquals(9, result.getDefaultRaceTo());
		// The loaded entity is mutated in place rather than replaced
		assertSame(existing, result);
	}

	@Test
	@DisplayName("TC-009 · The code itself stays immutable")
	void TC009_update_codeIsImmutable() {
		GameTypeDefinition existing = gameType(CODE, "9-Ball", true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		when(repository.save(any(GameTypeDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		GameTypeDefinition payload = GameTypeDefinition.builder()
				.code("10_BALL").name("Renamed").build();

		GameTypeDefinition result = gameTypeService.update(CODE, payload);

		// The code is the primary key — honouring a change here would orphan every tournament
		// already pointing at it
		assertEquals(CODE, result.getCode());
	}

	@Test
	@DisplayName("TC-010 · Updating a game type that does not exist")
	void TC010_update_notFound() {
		when(repository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> gameTypeService.update("NO_SUCH", gameType("NO_SUCH", "x", true)));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("TC-011 · Sort order is not part of the update contract")
	void TC011_update_doesNotTouchSortOrder() {
		GameTypeDefinition existing = gameType(CODE, "9-Ball", true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		when(repository.save(any(GameTypeDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		GameTypeDefinition payload = GameTypeDefinition.builder().name("Nine Ball").sortOrder(99).build();

		GameTypeDefinition result = gameTypeService.update(CODE, payload);

		// UC-11.3 lists sort order as editable but the implementation never copies it (DEF-W1-04).
		// This asserts the real contract so the row reflects what the code actually promises.
		assertEquals(5, result.getSortOrder());
	}

	// ══════════════════════════ setActive — UC-11.4 ══════════════════════════

	@Test
	@DisplayName("TC-012 · Deactivating keeps the record")
	void TC012_setActive_deactivateKeepsRecord() {
		GameTypeDefinition existing = gameType(CODE, "9-Ball", true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));

		gameTypeService.setActive(CODE, false);

		ArgumentCaptor<GameTypeDefinition> saved = ArgumentCaptor.forClass(GameTypeDefinition.class);
		verify(repository, times(1)).save(saved.capture());
		assertFalse(saved.getValue().getIsActive());
		// BR-01: game types are never hard-deleted
		verify(repository, never()).delete(any(GameTypeDefinition.class));
		verify(repository, never()).deleteById(any());
	}

	@Test
	@DisplayName("TC-013 · Reactivating a disabled game type")
	void TC013_setActive_reactivate() {
		GameTypeDefinition existing = gameType(CODE, "9-Ball", false);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));

		gameTypeService.setActive(CODE, true);

		assertTrue(existing.getIsActive());
		verify(repository, times(1)).save(existing);
	}

	@Test
	@DisplayName("TC-014 · Toggling a game type that does not exist")
	void TC014_setActive_notFound() {
		when(repository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> gameTypeService.setActive("NO_SUCH", false));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).save(any());
	}
}
