package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.ImportTableResultResponse;
import com.capstone.su26_sep490_g2_be.entity.BilliardTable;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TableStatus;
import com.capstone.su26_sep490_g2_be.enums.TableType;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BilliardTableRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link BilliardTableExcelServiceImpl}.
 *
 * <p>Mirrors the <b>BilliardTableExcelService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-38 (the shared table pool of a chain).
 *
 * <p>The import is partial by design: a bad row is reported and skipped rather than rolling the
 * whole upload back, so most of these tests are about which row survives and what the Owner is
 * told about the ones that did not.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · BilliardTableExcelService — UC-38")
class BilliardTableExcelServiceImplTest {

	@Mock BilliardTableRepository tableRepository;
	@Mock BranchRepository branchRepository;
	@Mock UserRepository userRepository;

	@InjectMocks BilliardTableExcelServiceImpl service;

	private static final Long OWNER_ID = 3L;
	private static final String HEADER =
			"Tên bàn,Số hiển thị,Loại bàn (POOL/CAROM/SNOOKER/OTHER),Chi nhánh (để trống = dùng chung cả chuỗi)\n";

	private static User owner() {
		return User.builder().id(OWNER_ID).email("owner@example.com").build();
	}

	private static Branch branch(long id, String name) {
		return Branch.builder().id(id).name(name).build();
	}

	private static MockMultipartFile csvUpload(String body) {
		return new MockMultipartFile("file", "tables.csv", "text/csv",
				("﻿" + HEADER + body).getBytes(StandardCharsets.UTF_8));
	}

	/** A genuine .xlsx workbook, so the POI reading path runs rather than being stubbed. */
	private static MockMultipartFile xlsxUpload(Object[][] rows) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Bàn");
			for (int r = 0; r < rows.length; r++) {
				Row row = sheet.createRow(r);
				for (int c = 0; c < rows[r].length; c++) {
					Object value = rows[r][c];
					if (value instanceof Number n) {
						row.createCell(c).setCellValue(n.doubleValue());
					} else if (value != null) {
						row.createCell(c).setCellValue(value.toString());
					}
				}
			}
			workbook.write(out);
			return new MockMultipartFile("file", "tables.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
		}
	}

	private void givenOwnerWithBranches(Branch... branches) {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		lenient().when(branchRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(branches));
	}

	private List<BilliardTable> savedTables() {
		ArgumentCaptor<BilliardTable> captor = ArgumentCaptor.forClass(BilliardTable.class);
		verify(tableRepository, times(1)).save(captor.capture());
		return captor.getAllValues();
	}

	// ══════════════════════════ the templates — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-001 · The spreadsheet template ships a header and one filled-in example")
	void TC001_buildImportTemplate_headerAndSample() throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.buildImportTemplate()))) {
			Sheet sheet = workbook.getSheet("Bàn");
			assertEquals("Tên bàn", sheet.getRow(0).getCell(0).getStringCellValue());
			assertEquals("Loại bàn (POOL/CAROM/SNOOKER/OTHER)", sheet.getRow(0).getCell(2).getStringCellValue());
			// The sample row doubles as the format documentation for the Owner
			assertEquals("Bàn 1", sheet.getRow(1).getCell(0).getStringCellValue());
			assertEquals(1d, sheet.getRow(1).getCell(1).getNumericCellValue());
			assertEquals("POOL", sheet.getRow(1).getCell(2).getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-002 · The CSV template opens with a byte order mark")
	void TC002_buildImportTemplateCsv_utf8Bom() {
		byte[] csv = service.buildImportTemplateCsv();

		assertEquals((byte) 0xEF, csv[0]);
		assertEquals((byte) 0xBB, csv[1]);
		assertEquals((byte) 0xBF, csv[2]);
		String text = new String(csv, StandardCharsets.UTF_8);
		// Without the mark Excel opens the file in the system code page and mangles "Tên bàn"
		assertTrue(text.contains("Tên bàn"));
		assertTrue(text.contains("Bàn 1,1,POOL,"));
	}

	@Test
	@DisplayName("TC-003 · The two templates are downloaded under their own file names")
	void TC003_templateFilenames() {
		assertEquals("template_import_table.xlsx", service.getTemplateFilename());
		assertEquals("template_import_table.csv", service.getTemplateCsvFilename());
	}

	// ══════════════════════════ upload validation — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-004 · Importing as an account that does not exist")
	void TC004_import_unknownOwner() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,POOL,\n")));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
		verify(tableRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-005 · An empty upload is rejected before it is parsed")
	void TC005_import_emptyFile() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		MockMultipartFile empty = new MockMultipartFile("file", "tables.csv", "text/csv", new byte[0]);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.importFromExcel(OWNER_ID, empty));

		assertEquals(ErrorCode.STORAGE_INVALID_FILE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-006 · An upload with no file name is rejected")
	void TC006_import_missingFilename() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		MockMultipartFile unnamed = new MockMultipartFile("file", "", "text/csv",
				"Bàn 1,1,POOL,\n".getBytes(StandardCharsets.UTF_8));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.importFromExcel(OWNER_ID, unnamed));

		// The extension is the only thing that decides which parser runs, so it has to be there
		assertEquals(ErrorCode.TABLE_INVALID_IMPORT_FILE, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-007 · A file that is neither a spreadsheet nor a CSV is rejected")
	void TC007_import_unsupportedExtension() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		MockMultipartFile pdf = new MockMultipartFile("file", "tables.pdf", "application/pdf",
				"%PDF-1.4".getBytes(StandardCharsets.UTF_8));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.importFromExcel(OWNER_ID, pdf));

		assertEquals(ErrorCode.TABLE_INVALID_IMPORT_FILE, ex.getErrorCode());
	}

	// ══════════════════════════ row handling — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-008 · A valid row becomes an active table on the chain")
	void TC008_import_validRow() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,POOL,\n"));

		assertEquals(1, result.getTotalRows());
		assertEquals(1, result.getImported());
		assertEquals(0, result.getSkipped());
		assertTrue(result.getErrors().isEmpty());
		BilliardTable saved = savedTables().get(0);
		assertEquals("Bàn 1", saved.getName());
		assertEquals(1, saved.getTableNumber());
		assertEquals(TableType.POOL, saved.getTableType());
		assertEquals(TableStatus.ACTIVE, saved.getStatus());
		// An empty branch column means the table belongs to the whole chain
		assertNull(saved.getBranch());
	}

	@Test
	@DisplayName("TC-009 · A row with no table name is reported and skipped")
	void TC009_import_missingName() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload(",1,POOL,\n"));

		assertEquals(1, result.getTotalRows());
		assertEquals(0, result.getImported());
		assertEquals(1, result.getSkipped());
		// The row number counts the header, so it matches what the Owner sees in Excel
		assertEquals("Hàng 2: Tên bàn không được để trống", result.getErrors().get(0));
		verify(tableRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-010 · A table name over a hundred characters is rejected")
	void TC010_import_nameTooLong() {
		givenOwnerWithBranches();
		String tooLong = "B".repeat(101);

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload(tooLong + ",1,POOL,\n"));

		assertEquals(1, result.getSkipped());
		assertTrue(result.getErrors().get(0).contains("tối đa 100 ký tự"));
	}

	@Test
	@DisplayName("TC-011 · A table name of exactly a hundred characters is accepted")
	void TC011_import_nameAtLimit() {
		givenOwnerWithBranches();

		ImportTableResultResponse result =
				service.importFromExcel(OWNER_ID, csvUpload("B".repeat(100) + ",1,POOL,\n"));

		// The limit is inclusive — the boundary belongs to the valid partition
		assertEquals(1, result.getImported());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	@DisplayName("TC-012 · A display number that is not an integer is reported")
	void TC012_import_nonNumericTableNumber() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,một,POOL,\n"));

		assertEquals(1, result.getSkipped());
		assertEquals("Hàng 2: Số hiển thị phải là số nguyên", result.getErrors().get(0));
	}

	@Test
	@DisplayName("TC-013 · A display number below one is reported")
	void TC013_import_tableNumberBelowOne() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,0,POOL,\n"));

		// Zero is the boundary: the number is what staff call the table out loud
		assertEquals(1, result.getSkipped());
		assertEquals("Hàng 2: Số hiển thị phải từ 1 trở lên", result.getErrors().get(0));
	}

	@Test
	@DisplayName("TC-014 · An omitted display number is allowed")
	void TC014_import_tableNumberOptional() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload("Bàn góc,,POOL,\n"));

		assertEquals(1, result.getImported());
		// A table without a number is still a table; only a wrong number is an error
		assertNull(savedTables().get(0).getTableNumber());
	}

	@Test
	@DisplayName("TC-015 · An unknown table type is reported")
	void TC015_import_invalidTableType() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,BIDA,\n"));

		assertEquals(1, result.getSkipped());
		assertTrue(result.getErrors().get(0).contains("Loại bàn không hợp lệ"));
	}

	@Test
	@DisplayName("TC-016 · A table type given by its Vietnamese label is accepted")
	void TC016_import_tableTypeByDisplayName() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,khác,\n"));

		// The template lists the codes, but an Owner filling it in by hand writes the label
		assertEquals(1, result.getImported());
		assertEquals(TableType.OTHER, savedTables().get(0).getTableType());
	}

	@Test
	@DisplayName("TC-017 · An omitted table type is allowed")
	void TC017_import_tableTypeOptional() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,,\n"));

		assertEquals(1, result.getImported());
		assertNull(savedTables().get(0).getTableType());
	}

	@Test
	@DisplayName("TC-018 · A branch the chain does not own is reported")
	void TC018_import_unknownBranch() {
		givenOwnerWithBranches(branch(1L, "Chi nhánh Quận 1"));

		ImportTableResultResponse result =
				service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,POOL,Chi nhánh Quận 9\n"));

		assertEquals(1, result.getSkipped());
		// Only the Owner's own branches are candidates, so a typo cannot attach a table elsewhere
		assertEquals("Hàng 2: Không tìm thấy chi nhánh \"Chi nhánh Quận 9\"", result.getErrors().get(0));
		verify(tableRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-019 · A branch name is matched regardless of case")
	void TC019_import_branchMatchedCaseInsensitively() {
		Branch quan1 = branch(1L, "Chi nhánh Quận 1");
		givenOwnerWithBranches(quan1);

		ImportTableResultResponse result =
				service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,POOL,chi nhánh quận 1\n"));

		assertEquals(1, result.getImported());
		assertEquals(quan1, savedTables().get(0).getBranch());
	}

	@Test
	@DisplayName("TC-020 · Blank lines are ignored and never counted")
	void TC020_import_blankRowsIgnored() {
		givenOwnerWithBranches();

		ImportTableResultResponse result =
				service.importFromExcel(OWNER_ID, csvUpload("Bàn 1,1,POOL,\n   ,,,\n"));

		// A trailing row of separators is what Excel leaves behind; it is not a failed row
		assertEquals(1, result.getTotalRows());
		assertEquals(1, result.getImported());
		assertEquals(0, result.getSkipped());
	}

	@Test
	@DisplayName("TC-021 · A partly wrong file imports the good rows and lists the rest")
	void TC021_import_partialSuccess() {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID,
				csvUpload("Bàn 1,1,POOL,\n,2,POOL,\nBàn 3,x,POOL,\n"));

		assertEquals(3, result.getTotalRows());
		assertEquals(1, result.getImported());
		assertEquals(2, result.getSkipped());
		// The import is deliberately partial: one bad row must not cost the Owner the other fifty
		assertEquals(2, result.getErrors().size());
		assertTrue(result.getErrors().get(0).startsWith("Hàng 3:"));
		assertTrue(result.getErrors().get(1).startsWith("Hàng 4:"));
	}

	// ══════════════════════════ the spreadsheet path — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-022 · A real spreadsheet is read straight through")
	void TC022_import_readsXlsx() throws IOException {
		givenOwnerWithBranches(branch(1L, "Chi nhánh Quận 1"));

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, xlsxUpload(new Object[][] {
				{"Tên bàn", "Số hiển thị", "Loại bàn", "Chi nhánh"},
				{"Bàn VIP", 5, "CAROM", "Chi nhánh Quận 1"}}));

		assertEquals(1, result.getImported());
		BilliardTable saved = savedTables().get(0);
		assertEquals("Bàn VIP", saved.getName());
		// The number arrives as a numeric cell and still has to read back as the integer 5
		assertEquals(5, saved.getTableNumber());
		assertEquals(TableType.CAROM, saved.getTableType());
	}

	@Test
	@DisplayName("TC-023 · A spreadsheet whose first row is the only row imports nothing")
	void TC023_import_headerOnlyXlsx() throws IOException {
		givenOwnerWithBranches();

		ImportTableResultResponse result = service.importFromExcel(OWNER_ID, xlsxUpload(new Object[][] {
				{"Tên bàn", "Số hiển thị", "Loại bàn", "Chi nhánh"}}));

		// The header is always skipped, so a template downloaded and re-uploaded untouched is a no-op
		assertEquals(0, result.getTotalRows());
		assertEquals(0, result.getImported());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	@DisplayName("TC-024 · A CSV renamed as a spreadsheet is reported as an unreadable file")
	void TC024_import_csvContentUnderXlsxName() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		MockMultipartFile mislabelled = new MockMultipartFile("file", "tables.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				(HEADER + "Bàn 1,1,POOL,\n").getBytes(StandardCharsets.UTF_8));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.importFromExcel(OWNER_ID, mislabelled));

		// The CSV retry only catches the one POI failure it was written for; plain text under an
		// .xlsx name fails the magic-number check instead and is reported as unreadable
		assertEquals(ErrorCode.TABLE_INVALID_IMPORT_FILE, ex.getErrorCode());
	}
}
