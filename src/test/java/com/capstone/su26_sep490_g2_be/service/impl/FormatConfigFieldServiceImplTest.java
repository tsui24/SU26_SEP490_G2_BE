package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FormatConfigFieldRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link FormatConfigFieldServiceImpl}.
 *
 * <p>Mirrors the <b>FormatConfigFieldService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-13 (default format settings), BR-03 on uniqueness.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · FormatConfigFieldService — UC-13")
class FormatConfigFieldServiceImplTest {

	@Mock FormatConfigFieldRepository repository;

	@InjectMocks FormatConfigFieldServiceImpl service;

	private static final String FORMAT = "SINGLE_ELIM";
	private static final String FIELD = "bracket_size";

	private static FormatConfigField field(String key, String defaultValue, boolean visible) {
		return FormatConfigField.builder()
				.id(1L).formatCode(FORMAT).fieldKey(key)
				.defaultValue(defaultValue).isRequired(true).isVisibleToOwner(visible)
				.build();
	}

	@Test
	@DisplayName("TC-001 · Listing every config field assigned to a format")
	void TC001_getByFormat_returnsAll() {
		when(repository.findByFormatCodeOrderByIdAsc(FORMAT))
				.thenReturn(List.of(field(FIELD, "32", true), field("third_place", "true", false)));

		assertEquals(2, service.getByFormat(FORMAT).size());
	}

	@Test
	@DisplayName("TC-002 · Only Owner-visible fields reach the tournament form")
	void TC002_getVisibleByFormat_filtersHiddenFields() {
		when(repository.findByFormatCodeAndIsVisibleToOwnerTrueOrderByIdAsc(FORMAT))
				.thenReturn(List.of(field(FIELD, "32", true)));

		assertEquals(1, service.getVisibleByFormat(FORMAT).size());
		// UC-13 Request Fields: visibleToOwner decides what the Owner sees when creating a
		// tournament, so an internal-only field must not leak into that form
		verify(repository, never()).findByFormatCodeOrderByIdAsc(FORMAT);
	}

	@Test
	@DisplayName("TC-003 · Opening one config field of a format")
	void TC003_getByFormatAndFieldKey_happyPath() {
		when(repository.findByFormatCodeAndFieldKey(FORMAT, FIELD))
				.thenReturn(Optional.of(field(FIELD, "32", true)));

		assertEquals("32", service.getByFormatAndFieldKey(FORMAT, FIELD).getDefaultValue());
	}

	@Test
	@DisplayName("TC-004 · Field not assigned to this format")
	void TC004_getByFormatAndFieldKey_notFound() {
		when(repository.findByFormatCodeAndFieldKey(FORMAT, "no_such")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getByFormatAndFieldKey(FORMAT, "no_such"));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-005 · Upserting a field the format does not hold yet inserts it")
	void TC005_upsert_insertsWhenAbsent() {
		FormatConfigField incoming = field(FIELD, "32", true);
		when(repository.findByFormatCodeAndFieldKey(FORMAT, FIELD)).thenReturn(Optional.empty());
		when(repository.save(incoming)).thenReturn(incoming);

		assertSame(incoming, service.upsert(incoming));
	}

	@Test
	@DisplayName("TC-006 · Upserting a field the format already holds updates it in place")
	void TC006_upsert_updatesWhenPresent() {
		FormatConfigField existing = field(FIELD, "16", true);
		FormatConfigField incoming = field(FIELD, "32", false);
		when(repository.findByFormatCodeAndFieldKey(FORMAT, FIELD)).thenReturn(Optional.of(existing));
		when(repository.save(existing)).thenReturn(existing);

		FormatConfigField result = service.upsert(incoming);

		// UC-13 BR-03 makes the format-plus-field pair unique, so this must never insert a
		// second row for the same pair
		assertSame(existing, result);
		assertEquals("32", existing.getDefaultValue());
		assertEquals(false, existing.getIsVisibleToOwner());
		verify(repository, never()).save(incoming);
	}

	@Test
	@DisplayName("TC-007 · Deleting an assigned config field")
	void TC007_delete_happyPath() {
		FormatConfigField existing = field(FIELD, "32", true);
		when(repository.findById(1L)).thenReturn(Optional.of(existing));

		service.delete(1L);

		verify(repository).delete(existing);
	}

	@Test
	@DisplayName("TC-008 · Deleting a config field that does not exist")
	void TC008_delete_notFound() {
		when(repository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(9999L));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
		verify(repository, never()).delete(any(FormatConfigField.class));
	}
}
