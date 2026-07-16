package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantMemberRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.ParticipantExcelService;
import com.capstone.su26_sep490_g2_be.util.ParticipantMemberFactory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantExcelServiceImpl implements ParticipantExcelService {

	private static final String TEMPLATE_XLSX_FILENAME = "template_import_participant.xlsx";
	private static final String TEMPLATE_CSV_FILENAME = "template_import_participant.csv";
	private static final String SHEET_NAME = "Người tham gia";
	private static final DataFormatter DATA_FORMATTER = new DataFormatter();
	private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final ParticipantRepository participantRepository;
	private final ParticipantMemberRepository participantMemberRepository;

	private boolean isDouble(Long tournamentId) {
		return tournamentRepository.findById(tournamentId)
				.map(t -> ParticipantType.DOUBLE.name().equals(t.getParticipantType()))
				.orElse(false);
	}

	@Override
	public byte[] buildImportTemplate(Long tournamentId) throws IOException {
		boolean isDouble = isDouble(tournamentId);
		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(SHEET_NAME);

			CellStyle textStyle = workbook.createCellStyle();
			textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
			// Áp dụng format Text cho các cột SĐT để Excel giữ số 0 đầu
			sheet.setDefaultColumnStyle(1, textStyle);
			if (isDouble) {
				sheet.setDefaultColumnStyle(3, textStyle);
			}

			Row header = sheet.createRow(0);
			Row sample = sheet.createRow(1);
			if (isDouble) {
				header.createCell(0).setCellValue("Tên VĐV 1");
				setPhoneCell(header.createCell(1), textStyle, "SĐT VĐV 1");
				header.createCell(2).setCellValue("Tên VĐV 2");
				setPhoneCell(header.createCell(3), textStyle, "SĐT VĐV 2");
				header.createCell(4).setCellValue("Hạt giống");

				sample.createCell(0).setCellValue("Nguyễn Văn A");
				setPhoneCell(sample.createCell(1), textStyle, "0901234567");
				sample.createCell(2).setCellValue("Trần Văn B");
				setPhoneCell(sample.createCell(3), textStyle, "0907654321");
				sample.createCell(4).setCellValue(1);

				sheet.setColumnWidth(0, 20 * 256);
				sheet.setColumnWidth(1, 18 * 256);
				sheet.setColumnWidth(2, 20 * 256);
				sheet.setColumnWidth(3, 18 * 256);
				sheet.setColumnWidth(4, 12 * 256);
			} else {
				header.createCell(0).setCellValue("Tên hiển thị");
				setPhoneCell(header.createCell(1), textStyle, "Số điện thoại");
				header.createCell(2).setCellValue("Hạt giống");

				sample.createCell(0).setCellValue("Nguyễn Văn A");
				setPhoneCell(sample.createCell(1), textStyle, "0901234567");
				sample.createCell(2).setCellValue(1);

				sheet.setColumnWidth(0, 20 * 256);
				sheet.setColumnWidth(1, 18 * 256);
				sheet.setColumnWidth(2, 12 * 256);
			}

			workbook.write(out);
			return out.toByteArray();
		}
	}

	private void setPhoneCell(Cell cell, CellStyle textStyle, String value) {
		cell.setCellStyle(textStyle);
		cell.setCellValue(value);
	}

	@Override
	public byte[] buildImportTemplateCsv(Long tournamentId) {
		// Công thức Excel ="..." để khi mở CSV, cột SĐT vẫn là text và giữ số 0 đầu
		String csv = isDouble(tournamentId)
				? "Tên VĐV 1,SĐT VĐV 1,Tên VĐV 2,SĐT VĐV 2,Hạt giống\nNguyễn Văn A,=\"0901234567\",Trần Văn B,=\"0907654321\",1\n"
				: "Tên hiển thị,Số điện thoại,Hạt giống\nNguyễn Văn A,=\"0901234567\",1\n";
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
	public ImportParticipantResultResponse importFromExcel(Long tournamentId, MultipartFile file) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!TournamentStatus.isRosterEditable(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.TOURNAMENT_ROSTER_LOCKED);
		}

		validateUploadFile(file);

		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
		}

		if (isCsvUpload(file, content)) {
			return importRows(tournament, parseCsvRows(content));
		}

		try {
			return importRows(tournament, parseExcelRows(content));
		} catch (NotOfficeXmlFileException e) {
			log.warn("Excel parse failed, retry as CSV for tournament {}: {}", tournamentId, e.getMessage());
			return importRows(tournament, parseCsvRows(content));
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Import failed for tournament {}: {}", tournamentId, e.getMessage(), e);
			throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
		}
	}

	private ImportParticipantResultResponse importRows(Tournament tournament, List<String[]> rows) {
		List<String> errors = new ArrayList<>();
		int imported = 0;
		int skipped = 0;
		int totalRows = 0;

		boolean isDouble = ParticipantType.DOUBLE.name().equals(tournament.getParticipantType());
		int seedCol = isDouble ? 4 : 2;

		Set<Integer> usedSeeds = new HashSet<>();
		participantRepository.findByTournamentIdAndStatus(tournament.getId(), ParticipantStatus.ACTIVE.getValue())
				.forEach(p -> { if (p.getSeedNo() != null) usedSeeds.add(p.getSeedNo()); });

		for (int i = 0; i < rows.size(); i++) {
			String[] cols = rows.get(i);
			if (isDataRowEmpty(cols, seedCol)) {
				continue;
			}

			totalRows++;
			int rowNo = i + 2;

			String name1 = normalizeCell(cols, 0);
			if (name1 == null || name1.isBlank()) {
				errors.add("Hàng " + rowNo + ": Tên VĐV 1 không được để trống");
				skipped++;
				continue;
			}
			if (name1.length() > 255) {
				errors.add("Hàng " + rowNo + ": Tên quá dài");
				skipped++;
				continue;
			}

			String phone1 = normalizePhone(normalizeCell(cols, 1));
			if (phone1 != null && !phone1.isBlank() && !isValidPhone(phone1)) {
				errors.add("Hàng " + rowNo + ": Số điện thoại VĐV 1 không hợp lệ");
				skipped++;
				continue;
			}

			String name2 = null;
			String phone2 = null;
			if (isDouble) {
				name2 = normalizeCell(cols, 2);
				if (name2 == null || name2.isBlank()) {
					errors.add("Hàng " + rowNo + ": Tên VĐV 2 không được để trống (giải đôi)");
					skipped++;
					continue;
				}
				if (name2.length() > 255) {
					errors.add("Hàng " + rowNo + ": Tên VĐV 2 quá dài");
					skipped++;
					continue;
				}
				phone2 = normalizePhone(normalizeCell(cols, 3));
				if (phone2 != null && !phone2.isBlank() && !isValidPhone(phone2)) {
					errors.add("Hàng " + rowNo + ": Số điện thoại VĐV 2 không hợp lệ");
					skipped++;
					continue;
				}
			}

			Integer seedNo = null;
			String seedRaw = normalizeCell(cols, seedCol);
			if (seedRaw != null && !seedRaw.isBlank()) {
				try {
					int parsed = Integer.parseInt(seedRaw.trim());
					if (parsed >= 1) {
						seedNo = parsed;
					} else {
						errors.add("Hàng " + rowNo + ": Hạt giống phải từ 1 trở lên");
						skipped++;
						continue;
					}
				} catch (NumberFormatException e) {
					errors.add("Hàng " + rowNo + ": Hạt giống phải là số nguyên");
					skipped++;
					continue;
				}
				if (usedSeeds.contains(seedNo)) {
					errors.add("Hàng " + rowNo + ": Hạt giống " + seedNo + " đã được dùng cho người khác");
					skipped++;
					continue;
				}
				usedSeeds.add(seedNo);
			}

			String displayName = isDouble
					? ParticipantMemberFactory.composeDoubleDisplayName(name1, name2)
					: name1;

			Registration registration = registrationRepository.save(Registration.builder()
					.tournament(tournament)
					.user(null)
					.registrationType(RegistrationType.MANUAL.getValue())
					.playerFullName(displayName)
					.playerPhone(phone1)
					.status(RegistrationStatus.APPROVED.getValue())
					.build());

			Participant participant = participantRepository.save(Participant.builder()
					.tournament(tournament)
					.registration(registration)
					.participantType(tournament.getParticipantType())
					.displayName(displayName)
					.seedNo(seedNo)
					.status(ParticipantStatus.ACTIVE.getValue())
					.build());

			if (isDouble) {
				participantMemberRepository.saveAll(ParticipantMemberFactory.buildDoubleMembers(
						participant, name1, phone1, null, name2, phone2));
			}
			imported++;
		}

		return ImportParticipantResultResponse.builder()
				.totalRows(totalRows)
				.imported(imported)
				.skipped(skipped)
				.errors(errors)
				.build();
	}

	private List<String[]> parseExcelRows(byte[] content) throws IOException {
		List<String[]> rows = new ArrayList<>();
		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
			Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
			if (sheet == null) {
				throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
			}
			for (Row row : sheet) {
				if (row.getRowNum() == 0) {
					continue;
				}
				rows.add(new String[] {
						getCellString(row, 0),
						getCellString(row, 1),
						getCellString(row, 2),
						getCellString(row, 3),
						getCellString(row, 4)
				});
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
			throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
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
			throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
		}

		String lower = filename.toLowerCase();
		if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
			throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
		}
	}

	private boolean isCsvUpload(MultipartFile file, byte[] content) {
		String filename = file.getOriginalFilename();
		return filename != null && filename.toLowerCase().endsWith(".csv");
	}

	private boolean isDataRowEmpty(String[] cols, int seedCol) {
		if (cols == null || cols.length == 0) {
			return true;
		}
		for (int i = 0; i <= seedCol; i++) {
			if (i < cols.length) {
				String value = normalizeCell(cols, i);
				if (value != null && !value.isBlank()) {
					return false;
				}
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

	private String normalizePhone(String phone) {
		if (phone == null) {
			return null;
		}
		phone = phone.trim();
		if (phone.startsWith("'")) {
			phone = phone.substring(1).trim();
		}
		if (phone.startsWith("=\"") && phone.endsWith("\"")) {
			phone = phone.substring(2, phone.length() - 1);
		}
		if (phone.matches("\\d+\\.0+")) {
			phone = phone.substring(0, phone.indexOf('.'));
		}
		// Excel hay đọc 0901234567 thành 901234567 (mất số 0) — tự khôi phục
		if (phone.matches("9\\d{8}")) {
			phone = "0" + phone;
		}
		return phone.isEmpty() ? null : phone;
	}

	private boolean isValidPhone(String phone) {
		return phone.matches("0\\d{9}");
	}
}
