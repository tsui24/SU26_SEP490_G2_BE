package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.TournamentFormatDefinition;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.TournamentFormatDefinitionRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link TournamentFormatServiceImpl}.
 *
 * <p>Mirrors the <b>TournamentFormatService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-12 (FT-07, Wave 1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · TournamentFormatService — UC-12")
class TournamentFormatServiceImplTest {

	@Mock TournamentFormatDefinitionRepository repository;

	@InjectMocks TournamentFormatServiceImpl formatService;

	private static final String CODE = "SINGLE_ELIM";
	private static final String HANDLER = "single-elimination";

	private static TournamentFormatDefinition format(String code, String handlerKey, boolean active) {
		return TournamentFormatDefinition.builder()
				.code(code).name("Single Elimination")
				.handlerKey(handlerKey)
				.schemaVersion("1.0")
				.isActive(active)
				.build();
	}

	// ══════════════════ getActiveFormats / getAllFormats — UC-12.2 ══════════════════

	@Test
	@DisplayName("TC-001 · Only active formats are offered for selection")
	void TC001_getActiveFormats_returnsActiveOnly() {
		when(repository.findByIsActiveTrueOrderByCreatedAtAsc())
				.thenReturn(List.of(format(CODE, HANDLER, true), format("ROUND_ROBIN", "round-robin", true)));

		List<TournamentFormatDefinition> result = formatService.getActiveFormats();

		assertEquals(2, result.size());
		verify(repository).findByIsActiveTrueOrderByCreatedAtAsc();
		verify(repository, never()).findAll();
	}

	@Test
	@DisplayName("TC-002 · No active format exists")
	void TC002_getActiveFormats_emptyResult() {
		when(repository.findByIsActiveTrueOrderByCreatedAtAsc()).thenReturn(List.of());

		assertTrue(formatService.getActiveFormats().isEmpty());
	}

	@Test
	@DisplayName("TC-003 · The admin list includes inactive entries")
	void TC003_getAllFormats_includesInactive() {
		when(repository.findAll())
				.thenReturn(List.of(format(CODE, HANDLER, true), format("DOUBLE_ELIM", "double-elimination", false)));

		List<TournamentFormatDefinition> result = formatService.getAllFormats();

		assertEquals(2, result.size());
		assertTrue(result.stream().anyMatch(f -> !f.getIsActive()));
	}

	// ══════════════════════════ getByCode — UC-12.2 ══════════════════════════

	@Test
	@DisplayName("TC-004 · Opens a format by its code")
	void TC004_getByCode_happyPath() {
		when(repository.findById(CODE)).thenReturn(Optional.of(format(CODE, HANDLER, true)));

		TournamentFormatDefinition result = formatService.getByCode(CODE);

		assertEquals(CODE, result.getCode());
		assertEquals(HANDLER, result.getHandlerKey());
	}

	@Test
	@DisplayName("TC-005 · Code does not exist")
	void TC005_getByCode_notFound() {
		when(repository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formatService.getByCode("NO_SUCH"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ create — UC-12.1 ══════════════════════════

	@Test
	@DisplayName("TC-006 · Creating a format with a fresh code")
	void TC006_create_happyPath() {
		TournamentFormatDefinition fresh = format(CODE, HANDLER, true);
		when(repository.save(any(TournamentFormatDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		TournamentFormatDefinition saved = formatService.create(fresh);

		assertEquals(CODE, saved.getCode());
		// UC-12 BR-04: a newly created format is active by default
		assertTrue(saved.getIsActive());
		verify(repository, times(1)).save(fresh);
	}

	/**
	 * DEF-W1-02 — EXPECTED TO FAIL.
	 *
	 * <p>UC-12.1 BR-01 makes the code unique and AF-01 requires a duplicate to be refused, but
	 * {@code create} calls {@code repository.save()} with no existence check. JPA {@code save()}
	 * on an existing primary key issues an UPDATE, so a duplicate code silently overwrites the
	 * previous format. Same root cause as DEF-W1-01 on {@code GameTypeServiceImpl}.
	 */
	@Test
	@DisplayName("TC-007 · Duplicate code must be rejected")
	void TC007_create_duplicateCode_rejected() {
		lenient().when(repository.existsById(CODE)).thenReturn(true);

		assertThrows(BusinessException.class,
				() -> formatService.create(format(CODE, "another-handler", true)),
				"UC-12.1 AF-01 requires a duplicate format code to be refused");

		verify(repository, never()).save(any());
	}

	/**
	 * DEF-W1-03 — EXPECTED TO FAIL.
	 *
	 * <p>UC-12.1 BR-02 requires the handler key to be unique. The repository already declares
	 * {@code existsByHandlerKey} for exactly this purpose, but the service never calls it. Two
	 * formats sharing a handler key leaves the engine unable to tell which bracket logic to run.
	 */
	@Test
	@DisplayName("TC-008 · Duplicate handler key must be rejected")
	void TC008_create_duplicateHandlerKey_rejected() {
		lenient().when(repository.existsByHandlerKey(HANDLER)).thenReturn(true);

		assertThrows(BusinessException.class,
				() -> formatService.create(format("DOUBLE_ELIM", HANDLER, true)),
				"UC-12.1 AF-02 requires a handler key already in use to be refused");

		verify(repository, never()).save(any());
	}

	// ══════════════════════════ update — UC-12.3 ══════════════════════════

	@Test
	@DisplayName("TC-009 · Editable attributes are saved")
	void TC009_update_savesEditableAttributes() {
		TournamentFormatDefinition existing = format(CODE, HANDLER, true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		when(repository.save(any(TournamentFormatDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		TournamentFormatDefinition payload = TournamentFormatDefinition.builder()
				.name("Single Elim").description("Knockout").handlerKey("se-v2").build();

		TournamentFormatDefinition result = formatService.update(CODE, payload);

		assertEquals("Single Elim", result.getName());
		assertEquals("Knockout", result.getDescription());
		assertEquals("se-v2", result.getHandlerKey());
		assertSame(existing, result);
	}

	@Test
	@DisplayName("TC-010 · The code itself stays immutable")
	void TC010_update_codeIsImmutable() {
		TournamentFormatDefinition existing = format(CODE, HANDLER, true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		when(repository.save(any(TournamentFormatDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		TournamentFormatDefinition payload = TournamentFormatDefinition.builder()
				.code("DOUBLE_ELIM").name("Renamed").handlerKey(HANDLER).build();

		assertEquals(CODE, formatService.update(CODE, payload).getCode());
	}

	/**
	 * DEF-W1-03 — EXPECTED TO FAIL, update path.
	 *
	 * <p>The repository declares {@code existsByHandlerKeyAndCodeNot} precisely for this check,
	 * which makes the intent unmistakable, yet {@code update} never calls it.
	 */
	@Test
	@DisplayName("TC-011 · Handler key must stay unique on update")
	void TC011_update_duplicateHandlerKey_rejected() {
		TournamentFormatDefinition existing = format(CODE, HANDLER, true);
		lenient().when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		lenient().when(repository.existsByHandlerKeyAndCodeNot("double-elimination", CODE)).thenReturn(true);

		TournamentFormatDefinition payload = TournamentFormatDefinition.builder()
				.name("Single Elimination").handlerKey("double-elimination").build();

		assertThrows(BusinessException.class,
				() -> formatService.update(CODE, payload),
				"UC-12.3 AF-02 requires a handler key already used by another format to be refused");

		assertEquals(HANDLER, existing.getHandlerKey());
	}

	@Test
	@DisplayName("TC-012 · Omitting the schema version keeps the stored one")
	void TC012_update_nullSchemaVersionKeepsStored() {
		TournamentFormatDefinition existing = format(CODE, HANDLER, true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		when(repository.save(any(TournamentFormatDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		TournamentFormatDefinition payload = TournamentFormatDefinition.builder()
				.name("Single Elimination").handlerKey(HANDLER).schemaVersion(null).build();

		// Unlike the other fields, this one is guarded by a null check
		assertEquals("1.0", formatService.update(CODE, payload).getSchemaVersion());
	}

	@Test
	@DisplayName("TC-013 · Supplying a schema version replaces the stored one")
	void TC013_update_schemaVersionReplaced() {
		TournamentFormatDefinition existing = format(CODE, HANDLER, true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));
		when(repository.save(any(TournamentFormatDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

		TournamentFormatDefinition payload = TournamentFormatDefinition.builder()
				.name("Single Elimination").handlerKey(HANDLER).schemaVersion("2.0").build();

		assertEquals("2.0", formatService.update(CODE, payload).getSchemaVersion());
	}

	@Test
	@DisplayName("TC-014 · Updating a format that does not exist")
	void TC014_update_notFound() {
		when(repository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formatService.update("NO_SUCH", format("NO_SUCH", "x", true)));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).save(any());
	}

	// ══════════════════════════ setActive — UC-12.4 ══════════════════════════

	@Test
	@DisplayName("TC-015 · Deactivating keeps the record")
	void TC015_setActive_deactivateKeepsRecord() {
		TournamentFormatDefinition existing = format(CODE, HANDLER, true);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));

		formatService.setActive(CODE, false);

		ArgumentCaptor<TournamentFormatDefinition> saved =
				ArgumentCaptor.forClass(TournamentFormatDefinition.class);
		verify(repository, times(1)).save(saved.capture());
		assertFalse(saved.getValue().getIsActive());
		verify(repository, never()).delete(any(TournamentFormatDefinition.class));
		verify(repository, never()).deleteById(any());
	}

	@Test
	@DisplayName("TC-016 · Reactivating a disabled format")
	void TC016_setActive_reactivate() {
		TournamentFormatDefinition existing = format(CODE, HANDLER, false);
		when(repository.findById(CODE)).thenReturn(Optional.of(existing));

		formatService.setActive(CODE, true);

		assertTrue(existing.getIsActive());
		verify(repository, times(1)).save(existing);
	}

	@Test
	@DisplayName("TC-017 · Toggling a format that does not exist")
	void TC017_setActive_notFound() {
		when(repository.findById("NO_SUCH")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> formatService.setActive("NO_SUCH", false));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).save(any());
	}
}
