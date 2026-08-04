package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.ParticipantImportConfirmRequest;
import com.capstone.su26_sep490_g2_be.dto.request.ParticipantImportRowRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantImportPreviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantImportPreviewRowResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantMemberRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link ParticipantExcelServiceImpl}.
 *
 * <p>Mirrors the <b>ParticipantExcelService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-28 (import a roster from a spreadsheet).
 *
 * <p>The file itself is real: templates are read back with POI and uploads are handed in as
 * genuine byte arrays, because most of the rules here are about what a spreadsheet does to a
 * phone number — Excel drops the leading zero of 0901234567 and stores 1 as 1.0.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · ParticipantExcelService — UC-28")
class ParticipantExcelServiceImplTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock RegistrationRepository registrationRepository;
	@Mock ParticipantRepository participantRepository;
	@Mock ParticipantMemberRepository participantMemberRepository;

	@InjectMocks ParticipantExcelServiceImpl service;

	private static final Long TOURNAMENT_ID = 800L;

	private static Tournament tournament(String status, String participantType, Integer maxParticipants) {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.status(status).participantType(participantType).maxParticipants(maxParticipants)
				.build();
	}

	private void givenImportableTournament(Tournament t, List<Participant> activeParticipants) {
		lenient().when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		lenient().when(participantRepository.countByTournamentIdAndStatus(
				TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue())).thenReturn((long) activeParticipants.size());
		lenient().when(participantRepository.findByTournamentIdAndStatus(
				TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue())).thenReturn(activeParticipants);
	}

	private static MockMultipartFile csvUpload(String body) {
		return new MockMultipartFile("file", "roster.csv", "text/csv",
				("﻿" + body).getBytes(StandardCharsets.UTF_8));
	}

	/** A genuine .xlsx workbook, so the POI parsing path is exercised rather than stubbed. */
	private static MockMultipartFile xlsxUpload(String[][] rows) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Người tham gia");
			for (int r = 0; r < rows.length; r++) {
				Row row = sheet.createRow(r);
				for (int c = 0; c < rows[r].length; c++) {
					if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
				}
			}
			workbook.write(out);
			return new MockMultipartFile("file", "roster.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
		}
	}

	private static ParticipantImportConfirmRequest confirmRequest(ParticipantImportRowRequest... rows) {
		ParticipantImportConfirmRequest request = new ParticipantImportConfirmRequest();
		request.setRows(List.of(rows));
		return request;
	}

	private static ParticipantImportRowRequest row(String name1, String phone1, Integer seedNo) {
		ParticipantImportRowRequest row = new ParticipantImportRowRequest();
		row.setName1(name1);
		row.setPhone1(phone1);
		row.setSeedNo(seedNo);
		return row;
	}

	private static ParticipantImportRowRequest doubleRow(String name1, String phone1,
														 String name2, String phone2, Integer seedNo) {
		ParticipantImportRowRequest row = row(name1, phone1, seedNo);
		row.setName2(name2);
		row.setPhone2(phone2);
		return row;
	}

	private void givenPersistenceEchoes() {
		lenient().when(registrationRepository.save(any(Registration.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	// ══════════════════════════ templates ══════════════════════════

	@Test
	@DisplayName("TC-001 · The template of a singles tournament asks for three columns")
	void TC001_buildImportTemplate_singles() throws IOException {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16)));

		byte[] bytes = service.buildImportTemplate(TOURNAMENT_ID);

		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
			Row header = workbook.getSheetAt(0).getRow(0);
			assertEquals("Tên hiển thị", header.getCell(0).getStringCellValue());
			assertEquals("Số điện thoại", header.getCell(1).getStringCellValue());
			assertEquals("Hạt giống", header.getCell(2).getStringCellValue());
			assertNull(header.getCell(3));
		}
	}

	@Test
	@DisplayName("TC-002 · The template of a doubles tournament asks for both players")
	void TC002_buildImportTemplate_doubles() throws IOException {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.DRAFT.getValue(), "DOUBLE", 16)));

		byte[] bytes = service.buildImportTemplate(TOURNAMENT_ID);

		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
			Row header = workbook.getSheetAt(0).getRow(0);
			assertEquals("Tên VĐV 1", header.getCell(0).getStringCellValue());
			assertEquals("Tên VĐV 2", header.getCell(2).getStringCellValue());
			assertEquals("Hạt giống", header.getCell(4).getStringCellValue());
			assertEquals("0901234567", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue(),
					"the sample phone keeps its leading zero because the column is formatted as text");
		}
	}

	@Test
	@DisplayName("TC-003 · A tournament that cannot be found still yields a singles template")
	void TC003_buildImportTemplate_unknownTournament() throws IOException {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		byte[] bytes = service.buildImportTemplate(TOURNAMENT_ID);

		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
			assertEquals("Tên hiển thị", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-004 · The CSV template opens correctly in Excel")
	void TC004_buildImportTemplateCsv_singles() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16)));

		byte[] bytes = service.buildImportTemplateCsv(TOURNAMENT_ID);

		assertEquals((byte) 0xEF, bytes[0], "a UTF-8 BOM is what makes Excel read Vietnamese correctly");
		String csv = new String(bytes, StandardCharsets.UTF_8);
		assertTrue(csv.contains("Tên hiển thị,Số điện thoại,Hạt giống"));
		assertTrue(csv.contains("=\"0901234567\""),
				"the formula wrapper is what stops Excel dropping the leading zero");
	}

	@Test
	@DisplayName("TC-005 · The CSV template of a doubles tournament carries both players")
	void TC005_buildImportTemplateCsv_doubles() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.DRAFT.getValue(), "DOUBLE", 16)));

		String csv = new String(service.buildImportTemplateCsv(TOURNAMENT_ID), StandardCharsets.UTF_8);

		assertTrue(csv.contains("Tên VĐV 1,SĐT VĐV 1,Tên VĐV 2,SĐT VĐV 2,Hạt giống"));
	}

	@Test
	@DisplayName("TC-006 · Both templates are offered under a recognisable filename")
	void TC006_templateFilenames() {
		assertEquals("template_import_participant.xlsx", service.getTemplateFilename());
		assertEquals("template_import_participant.csv", service.getTemplateCsvFilename());
	}

	// ══════════════════════════ preview — guards ══════════════════════════

	@Test
	@DisplayName("TC-007 · Importing into a tournament that does not exist")
	void TC007_preview_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.previewFromExcel(TOURNAMENT_ID, csvUpload("Tên,SĐT,Hạt giống\n")));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-008 · No roster may be imported once the bracket exists")
	void TC008_preview_rosterLocked() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.DRAW_DONE.getValue(), "SINGLE", 16)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.previewFromExcel(TOURNAMENT_ID, csvUpload("Tên,SĐT,Hạt giống\n")));

		assertEquals(ErrorCode.TOURNAMENT_ROSTER_LOCKED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-009 · A tournament already at capacity refuses the whole file")
	void TC009_preview_tournamentFull() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 2);
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
		when(participantRepository.countByTournamentIdAndStatus(
				TOURNAMENT_ID, ParticipantStatus.ACTIVE.getValue())).thenReturn(2L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.previewFromExcel(TOURNAMENT_ID, csvUpload("Tên,SĐT,Hạt giống\n")));

		assertEquals(ErrorCode.TOURNAMENT_FULL, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-010 · An empty upload is refused")
	void TC010_preview_emptyFile() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());
		MockMultipartFile empty = new MockMultipartFile("file", "roster.csv", "text/csv", new byte[0]);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.previewFromExcel(TOURNAMENT_ID, empty));

		assertEquals(ErrorCode.STORAGE_INVALID_FILE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-011 · A file of the wrong type is refused")
	void TC011_preview_wrongExtension() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());
		MockMultipartFile pdf = new MockMultipartFile("file", "roster.pdf", "application/pdf",
				"not a spreadsheet".getBytes(StandardCharsets.UTF_8));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.previewFromExcel(TOURNAMENT_ID, pdf));

		assertEquals(ErrorCode.PARTICIPANT_INVALID_EXCEL, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-012 · A file with no name at all is refused")
	void TC012_preview_missingFilename() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());
		MockMultipartFile unnamed = new MockMultipartFile("file", "", "text/csv",
				"a,b,c".getBytes(StandardCharsets.UTF_8));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.previewFromExcel(TOURNAMENT_ID, unnamed));

		assertEquals(ErrorCode.PARTICIPANT_INVALID_EXCEL, ex.getErrorCode());
	}

	// ══════════════════════════ preview — row rules ══════════════════════════

	@Test
	@DisplayName("TC-013 · A clean file previews every row as importable")
	void TC013_preview_allRowsValid() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\n"
						+ "Nguyễn Văn A,0901234567,1\n"
						+ "Trần Thị B,0907654321,2\n"));

		assertEquals(2, preview.getTotalRows());
		assertEquals(2, preview.getValidCount());
		assertEquals(0, preview.getInvalidCount());
		assertEquals(2, preview.getRows().get(0).getRowNo(), "row numbers point back at the spreadsheet");
	}

	@Test
	@DisplayName("TC-014 · A row with no name cannot be imported")
	void TC014_preview_missingName() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\n,0901234567,1\n"));

		assertEquals(1, preview.getInvalidCount());
		assertEquals("Tên VĐV 1 không được để trống", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-015 · A name longer than the column allows is refused")
	void TC015_preview_nameTooLong() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\n" + "A".repeat(256) + ",0901234567,1\n"));

		assertEquals("Tên quá dài", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-016 · A phone number that is not a Vietnamese mobile is refused")
	void TC016_preview_invalidPhone() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,12345,1\n"));

		assertEquals("Số điện thoại VĐV 1 không hợp lệ", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-017 · A row with no phone number at all is still importable")
	void TC017_preview_phoneOptional() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,,1\n"));

		assertTrue(preview.getRows().get(0).isValid(), "a phone number is optional for a walk-in entry");
	}

	@Test
	@DisplayName("TC-018 · Excel dropping the leading zero of a phone number is repaired")
	void TC018_preview_restoresLeadingZero() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,901234567,1\n"));

		assertTrue(preview.getRows().get(0).isValid());
		assertEquals("0901234567", preview.getRows().get(0).getPhone1());
	}

	@Test
	@DisplayName("TC-019 · A phone number Excel stored as a decimal is repaired")
	void TC019_preview_stripsDecimalTail() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,901234567.0,1\n"));

		assertEquals("0901234567", preview.getRows().get(0).getPhone1());
	}

	@Test
	@DisplayName("TC-020 · The formula wrapper of our own template is unwrapped on the way back in")
	void TC020_preview_unwrapsFormulaPhone() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,=\"0901234567\",1\n"));

		// DEF-W3-01 — fails today. buildImportTemplateCsv writes ="0901234567" so Excel keeps the
		// leading zero, and normalizePhone does handle that wrapper. But parseCsvLine strips the
		// quotes first, leaving =0901234567, which the ="…" guard no longer matches. The template
		// this service hands out therefore cannot be uploaded back unless Excel rewrites it first.
		assertEquals("0901234567", preview.getRows().get(0).getPhone1(),
				"a file downloaded from us and uploaded back unchanged must import cleanly");
		assertTrue(preview.getRows().get(0).isValid());
	}

	@Test
	@DisplayName("TC-021 · A seed that is not a whole number is refused")
	void TC021_preview_seedNotANumber() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,0901234567,một\n"));

		assertEquals("Hạt giống phải là số nguyên", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-022 · Seeding starts at one")
	void TC022_preview_seedBelowOne() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,0901234567,0\n"));

		assertEquals("Hạt giống phải từ 1 trở lên", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-023 · The same seed cannot be used twice within one file")
	void TC023_preview_duplicateSeedInFile() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\n"
						+ "Nguyễn Văn A,0901234567,1\n"
						+ "Trần Thị B,0907654321,1\n"));

		assertEquals(1, preview.getValidCount());
		assertEquals("Hạt giống 1 đã được dùng cho người khác", preview.getRows().get(1).getError());
	}

	@Test
	@DisplayName("TC-024 · A seed already taken by an existing player is refused")
	void TC024_preview_seedClashesWithExistingPlayer() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of(
				Participant.builder().id(1L).displayName("Lê Văn C").seedNo(1)
						.status(ParticipantStatus.ACTIVE.getValue()).build()));

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,0901234567,1\n"));

		assertEquals("Hạt giống 1 đã được dùng cho người khác", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-025 · Rows beyond the remaining places are refused one by one")
	void TC025_preview_stopsAtRemainingCapacity() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 2), List.of(
				Participant.builder().id(1L).displayName("Lê Văn C")
						.status(ParticipantStatus.ACTIVE.getValue()).build()));

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\n"
						+ "Nguyễn Văn A,0901234567,\n"
						+ "Trần Thị B,0907654321,\n"));

		assertEquals(1, preview.getValidCount(), "one place was left, so only the first row fits");
		assertTrue(preview.getRows().get(1).getError().contains("2 người tham gia"));
	}

	@Test
	@DisplayName("TC-026 · Blank rows left over in the sheet are ignored")
	void TC026_preview_skipsBlankRows() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên hiển thị,Số điện thoại,Hạt giống\n"
						+ "Nguyễn Văn A,0901234567,1\n"
						+ ",,\n"
						+ "Trần Thị B,0907654321,2\n"));

		assertEquals(2, preview.getTotalRows(), "a trailing empty row is not an error, it is just empty");
	}

	@Test
	@DisplayName("TC-027 · A doubles file needs both names on every row")
	void TC027_preview_doublesNeedsSecondName() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "DOUBLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên VĐV 1,SĐT VĐV 1,Tên VĐV 2,SĐT VĐV 2,Hạt giống\nNguyễn Văn A,0901234567,,,1\n"));

		assertEquals("Tên VĐV 2 không được để trống (giải đôi)", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-028 · The second player's phone number is validated too")
	void TC028_preview_doublesValidatesSecondPhone() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "DOUBLE", 16), List.of());

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, csvUpload(
				"Tên VĐV 1,SĐT VĐV 1,Tên VĐV 2,SĐT VĐV 2,Hạt giống\n"
						+ "Nguyễn Văn A,0901234567,Trần Thị B,12345,1\n"));

		assertEquals("Số điện thoại VĐV 2 không hợp lệ", preview.getRows().get(0).getError());
	}

	@Test
	@DisplayName("TC-029 · A real spreadsheet is read straight through")
	void TC029_preview_readsXlsx() throws IOException {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());
		MockMultipartFile upload = xlsxUpload(new String[][] {
				{"Tên hiển thị", "Số điện thoại", "Hạt giống"},
				{"Nguyễn Văn A", "0901234567", "1"},
				{"Trần Thị B", "0907654321", "2"}});

		ParticipantImportPreviewResponse preview = service.previewFromExcel(TOURNAMENT_ID, upload);

		assertEquals(2, preview.getValidCount());
		assertEquals("Nguyễn Văn A", preview.getRows().get(0).getName1());
	}

	@Test
	@DisplayName("TC-030 · A CSV renamed as a spreadsheet is reported as an unreadable file")
	void TC030_preview_csvContentUnderXlsxName() {
		givenImportableTournament(tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16), List.of());
		MockMultipartFile mislabelled = new MockMultipartFile("file", "roster.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				"Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,0901234567,1\n"
						.getBytes(StandardCharsets.UTF_8));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.previewFromExcel(TOURNAMENT_ID, mislabelled));

		assertEquals(ErrorCode.PARTICIPANT_INVALID_EXCEL, ex.getErrorCode(),
				"the CSV retry only covers the one POI failure it was written for, and the message "
						+ "tells the organiser to download the template again");
	}

	// ══════════════════════════ confirmImport ══════════════════════════

	@Test
	@DisplayName("TC-031 · Confirming an import into a tournament that does not exist")
	void TC031_confirm_tournamentNotFound() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.confirmImport(TOURNAMENT_ID, confirmRequest(row("Nguyễn Văn A", "0901234567", 1))));

		assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-032 · Nothing may be imported once the bracket exists")
	void TC032_confirm_rosterLocked() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(
				tournament(TournamentStatus.DRAW_DONE.getValue(), "SINGLE", 16)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.confirmImport(TOURNAMENT_ID, confirmRequest(row("Nguyễn Văn A", "0901234567", 1))));

		assertEquals(ErrorCode.TOURNAMENT_ROSTER_LOCKED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-033 · An imported player becomes an approved manual entry and an active participant")
	void TC033_confirm_singlesCreatesRegistrationAndParticipant() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16);
		givenImportableTournament(t, List.of());
		givenPersistenceEchoes();

		ImportParticipantResultResponse result = service.confirmImport(TOURNAMENT_ID,
				confirmRequest(row("Nguyễn Văn A", "0901234567", 3)));

		assertEquals(1, result.getImported());
		assertEquals(0, result.getSkipped());
		ArgumentCaptor<Registration> registration = ArgumentCaptor.forClass(Registration.class);
		verify(registrationRepository).save(registration.capture());
		assertEquals(RegistrationType.MANUAL.getValue(), registration.getValue().getRegistrationType());
		assertEquals(RegistrationStatus.APPROVED.getValue(), registration.getValue().getStatus());
		assertNull(registration.getValue().getUser(), "an imported player has no account of their own");
		ArgumentCaptor<Participant> participant = ArgumentCaptor.forClass(Participant.class);
		verify(participantRepository).save(participant.capture());
		assertEquals("Nguyễn Văn A", participant.getValue().getDisplayName());
		assertEquals(3, participant.getValue().getSeedNo());
		assertEquals(ParticipantStatus.ACTIVE.getValue(), participant.getValue().getStatus());
	}

	@Test
	@DisplayName("TC-034 · An imported pair becomes one participant with both members recorded")
	void TC034_confirm_doublesCreatesMembers() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue(), "DOUBLE", 16);
		givenImportableTournament(t, List.of());
		givenPersistenceEchoes();

		ImportParticipantResultResponse result = service.confirmImport(TOURNAMENT_ID, confirmRequest(
				doubleRow("Nguyễn Văn A", "0901234567", "Trần Thị B", "0907654321", 1)));

		assertEquals(1, result.getImported());
		ArgumentCaptor<Participant> participant = ArgumentCaptor.forClass(Participant.class);
		verify(participantRepository).save(participant.capture());
		assertEquals("Nguyễn A/Trần B", participant.getValue().getDisplayName(),
				"three-word names are shortened so the pair fits a bracket cell");
		verify(participantMemberRepository).saveAll(any());
	}

	@Test
	@DisplayName("TC-035 · An invalid row is skipped with its reason rather than failing the import")
	void TC035_confirm_invalidRowIsSkipped() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16);
		givenImportableTournament(t, List.of());
		givenPersistenceEchoes();

		ImportParticipantResultResponse result = service.confirmImport(TOURNAMENT_ID, confirmRequest(
				row("Nguyễn Văn A", "0901234567", 1),
				row("", "0907654321", 2)));

		assertEquals(1, result.getImported());
		assertEquals(1, result.getSkipped());
		assertEquals(1, result.getErrors().size());
		assertTrue(result.getErrors().get(0).contains("Tên VĐV 1 không được để trống"));
	}

	@Test
	@DisplayName("TC-036 · The file is re-checked against the database, not against the preview")
	void TC036_confirm_revalidatesAgainstCurrentState() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16);
		// Somebody else took seed 1 between the preview and the confirm
		givenImportableTournament(t, List.of(
				Participant.builder().id(1L).displayName("Lê Văn C").seedNo(1)
						.status(ParticipantStatus.ACTIVE.getValue()).build()));
		givenPersistenceEchoes();

		ImportParticipantResultResponse result = service.confirmImport(TOURNAMENT_ID,
				confirmRequest(row("Nguyễn Văn A", "0901234567", 1)));

		assertEquals(0, result.getImported());
		assertEquals(1, result.getSkipped());
		assertTrue(result.getErrors().get(0).contains("Hạt giống 1"),
				"trusting the client-side preview would let two players share a seed");
		verify(participantRepository, never()).save(any(Participant.class));
	}

	@Test
	@DisplayName("TC-037 · An import of nothing reports nothing")
	void TC037_confirm_emptyRequest() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16);
		givenImportableTournament(t, List.of());

		ImportParticipantResultResponse result = service.confirmImport(TOURNAMENT_ID, confirmRequest());

		assertEquals(0, result.getTotalRows());
		assertEquals(0, result.getImported());
		verify(registrationRepository, never()).save(any(Registration.class));
	}

	@Test
	@DisplayName("TC-038 · A row with no seed is imported unseeded")
	void TC038_confirm_seedIsOptional() {
		Tournament t = tournament(TournamentStatus.DRAFT.getValue(), "SINGLE", 16);
		givenImportableTournament(t, List.of());
		givenPersistenceEchoes();

		service.confirmImport(TOURNAMENT_ID, confirmRequest(row("Nguyễn Văn A", "0901234567", null)));

		ArgumentCaptor<Participant> participant = ArgumentCaptor.forClass(Participant.class);
		verify(participantRepository).save(participant.capture());
		assertNull(participant.getValue().getSeedNo(), "an unseeded player is drawn at random, which is normal");
	}
}
