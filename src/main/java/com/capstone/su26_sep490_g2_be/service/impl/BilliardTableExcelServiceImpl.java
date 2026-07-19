package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.ImportTableResultResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.BilliardTable;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TableStatus;
import com.capstone.su26_sep490_g2_be.enums.TableType;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BilliardTableRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BilliardTableExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BilliardTableExcelServiceImpl implements BilliardTableExcelService {

	private static final String TEMPLATE_XLSX_FILENAME = "template_import_table.xlsx";
	private static final String TEMPLATE_CSV_FILENAME = "template_import_table.csv";
	private static final String SHEET_NAME = "Bàn";
	private static final DataFormatter DATA_FORMATTER = new DataFormatter();
	private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
	private static final int COL_COUNT = 4; // name, tableNumber, tableType, branchName

	private final BilliardTableRepository tableRepository;
	private final BranchRepository branchRepository;
	private final UserRepository userRepository;

	@Override
	public byte[] buildImportTemplate() throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(SHEET_NAME);

			Row header = sheet.createRow(0);
			header.createCell(0).setCellValue("Tên bàn");
			header.createCell(1).setCellValue("Số hiển thị");
			header.createCell(2).setCellValue("Loại bàn (POOL/CAROM/SNOOKER/OTHER)");
			header.createCell(3).setCellValue("Chi nhánh (để trống = dùng chung cả chuỗi)");

			Row sample = sheet.createRow(1);
			sample.createCell(0).setCellValue("Bàn 1");
			sample.createCell(1).setCellValue(1);
			sample.createCell(2).setCellValue("POOL");
			sample.createCell(3).setCellValue("");

			sheet.setColumnWidth(0, 20 * 256);
			sheet.setColumnWidth(1, 14 * 256);
			sheet.setColumnWidth(2, 28 * 256);
			sheet.setColumnWidth(3, 28 * 256);

			workbook.write(out);
			return out.toByteArray();
		}
	}

	@Override
	public byte[] buildImportTemplateCsv() {
		String csv = "Tên bàn,Số hiển thị,Loại bàn (POOL/CAROM/SNOOKER/OTHER),Chi nhánh (để trống = dùng chung cả chuỗi)\n"
				+ "Bàn 1,1,POOL,\n";
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			out.write(UTF8_BOM);
			out.write(csv.getBytes(StandardCharsets.UTF_8));
			return out.toByteArray();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	@Override
	public String getTemplateFilename() {
		return TEMPLATE_XLSX_FILENAME;
	}

	@Override
	public String getTemplateCsvFilename() {
		return TEMPLATE_CSV_FILENAME;
	}

	@Override
	@Transactional
	public ImportTableResultResponse importFromExcel(Long ownerId, MultipartFile file) {
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		validateUploadFile(file);

		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.TABLE_INVALID_IMPORT_FILE);
		}

		if (isCsvUpload(file)) {
			return importRows(owner, parseCsvRows(content));
		}

		try {
			return importRows(owner, parseExcelRows(content));
		} catch (NotOfficeXmlFileException e) {
			log.warn("Excel parse failed, retry as CSV for owner {}: {}", ownerId, e.getMessage());
			return importRows(owner, parseCsvRows(content));
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Table import failed for owner {}: {}", ownerId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.TABLE_INVALID_IMPORT_FILE);
		}
	}

	private ImportTableResultResponse importRows(User owner, List<String[]> rows) {
		List<String> errors = new ArrayList<>();
		int imported = 0;
		int skipped = 0;
		int totalRows = 0;

		List<Branch> ownerBranches = branchRepository.findByOwnerId(owner.getId());

		for (int i = 0; i < rows.size(); i++) {
			String[] cols = rows.get(i);
			if (isDataRowEmpty(cols)) {
				continue;
			}

			totalRows++;
			int rowNo = i + 2;

			String name = normalizeCell(cols, 0);
			if (name == null) {
				errors.add("Hàng " + rowNo + ": Tên bàn không được để trống");
				skipped++;
				continue;
			}
			if (name.length() > 100) {
				errors.add("Hàng " + rowNo + ": Tên bàn tối đa 100 ký tự");
				skipped++;
				continue;
			}

			Integer tableNumber = null;
			String numRaw = normalizeCell(cols, 1);
			if (numRaw != null) {
				try {
					int parsed = Integer.parseInt(numRaw.trim());
					if (parsed < 1) {
						errors.add("Hàng " + rowNo + ": Số hiển thị phải từ 1 trở lên");
						skipped++;
						continue;
					}
					tableNumber = parsed;
				} catch (NumberFormatException e) {
					errors.add("Hàng " + rowNo + ": Số hiển thị phải là số nguyên");
					skipped++;
					continue;
				}
			}

			TableType tableType = null;
			String typeRaw = normalizeCell(cols, 2);
			if (typeRaw != null) {
				tableType = matchTableType(typeRaw);
				if (tableType == null) {
					errors.add("Hàng " + rowNo + ": Loại bàn không hợp lệ (POOL/CAROM/SNOOKER/OTHER)");
					skipped++;
					continue;
				}
			}

			Branch branch = null;
			String branchRaw = normalizeCell(cols, 3);
			if (branchRaw != null) {
				branch = ownerBranches.stream()
						.filter(b -> b.getName().equalsIgnoreCase(branchRaw))
						.findFirst().orElse(null);
				if (branch == null) {
					errors.add("Hàng " + rowNo + ": Không tìm thấy chi nhánh \"" + branchRaw + "\"");
					skipped++;
					continue;
				}
			}

			tableRepository.save(BilliardTable.builder()
					.owner(owner)
					.name(name)
					.tableNumber(tableNumber)
					.tableType(tableType)
					.branch(branch)
					.status(TableStatus.ACTIVE)
					.build());
			imported++;
		}

		return ImportTableResultResponse.builder()
				.totalRows(totalRows)
				.imported(imported)
				.skipped(skipped)
				.errors(errors)
				.build();
	}

	private TableType matchTableType(String raw) {
		for (TableType t : TableType.values()) {
			if (t.name().equalsIgnoreCase(raw) || t.getDisplayName().equalsIgnoreCase(raw)) {
				return t;
			}
		}
		return null;
	}

	private List<String[]> parseExcelRows(byte[] content) throws IOException {
		List<String[]> rows = new ArrayList<>();
		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
			Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
			if (sheet == null) {
				throw new BusinessException(ErrorCode.TABLE_INVALID_IMPORT_FILE);
			}
			for (Row row : sheet) {
				if (row.getRowNum() == 0) {
					continue;
				}
				String[] cols = new String[COL_COUNT];
				for (int c = 0; c < COL_COUNT; c++) {
					cols[c] = getCellString(row, c);
				}
				rows.add(cols);
			}
		}
		return rows;
	}

	private List<String[]> parseCsvRows(byte[] content) {
		List<String[]> rows = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				skipBom(new ByteArrayInputStream(content)), StandardCharsets.UTF_8))) {
			String line;
			boolean headerSkipped = false;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				if (!headerSkipped) {
					headerSkipped = true;
					continue;
				}
				rows.add(parseCsvLine(line));
			}
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.TABLE_INVALID_IMPORT_FILE);
		}
		return rows;
	}

	private ByteArrayInputStream skipBom(ByteArrayInputStream in) throws IOException {
		in.mark(3);
		byte[] bom = new byte[3];
		int read = in.read(bom);
		if (read == 3 && bom[0] == UTF8_BOM[0] && bom[1] == UTF8_BOM[1] && bom[2] == UTF8_BOM[2]) {
			return in;
		}
		in.reset();
		return in;
	}

	private String[] parseCsvLine(String line) {
		List<String> cols = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
			} else if (c == ',' && !inQuotes) {
				cols.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		cols.add(current.toString());
		return cols.toArray(String[]::new);
	}

	private void validateUploadFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.STORAGE_INVALID_FILE);
		}
		String filename = file.getOriginalFilename();
		if (filename == null || filename.isBlank()) {
			throw new BusinessException(ErrorCode.TABLE_INVALID_IMPORT_FILE);
		}
		String lower = filename.toLowerCase();
		if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
			throw new BusinessException(ErrorCode.TABLE_INVALID_IMPORT_FILE);
		}
	}

	private boolean isCsvUpload(MultipartFile file) {
		String filename = file.getOriginalFilename();
		return filename != null && filename.toLowerCase().endsWith(".csv");
	}

	private boolean isDataRowEmpty(String[] cols) {
		if (cols == null || cols.length == 0) {
			return true;
		}
		for (int i = 0; i < COL_COUNT && i < cols.length; i++) {
			if (normalizeCell(cols, i) != null) {
				return false;
			}
		}
		return true;
	}

	private String normalizeCell(String[] cols, int index) {
		if (cols == null || index >= cols.length || cols[index] == null) {
			return null;
		}
		String value = cols[index].trim();
		return value.isEmpty() ? null : value;
	}

	private String getCellString(Row row, int col) {
		Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
		if (cell == null) {
			return null;
		}
		String value = DATA_FORMATTER.formatCellValue(cell).trim();
		return value.isEmpty() ? null : value;
	}
}
