package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.ParticipantImportConfirmRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantImportPreviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantImportPreviewRowResponse;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.TournamentConfig;
import com.capstone.su26_sep490_g2_be.enums.BilliardRank;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationType;
import com.capstone.su26_sep490_g2_be.enums.SeedingMethod;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantMemberRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentConfigRepository;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantExcelServiceImpl implements ParticipantExcelService {

	private static final String TEMPLATE_XLSX_FILENAME = "template_import_participant.xlsx";
	private static final String TEMPLATE_CSV_FILENAME = "template_import_participant.csv";
	private static final String SHEET_NAME = "Người tham gia";
	/** Nhãn cột hạng — dùng chung cho template XLSX và CSV để hai bản không lệch nhau. */
	private static final String RANK_COLUMN_HEADER = "Hạng (CN / A-L, bỏ trống nếu chưa rõ)";
	/** Nhãn cột hạt giống — chỉ xuất hiện khi giải chọn seedingMethod = SEED. */
	private static final String SEED_COLUMN_HEADER = "Hạt giống (số nguyên dương, 1 = mạnh nhất, bỏ trống nếu không xếp)";
	/** Số cột tối đa cần đọc từ file: DOUBLE = 4 tên/SĐT + hạng + hạt giống. */
	private static final int MAX_IMPORT_COLUMNS = 6;
	private static final DataFormatter DATA_FORMATTER = new DataFormatter();
	private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

	private final TournamentRepository tournamentRepository;
	private final TournamentConfigRepository tournamentConfigRepository;
	private final RegistrationRepository registrationRepository;
	private final ParticipantRepository participantRepository;
	private final ParticipantMemberRepository participantMemberRepository;

	private boolean isDouble(Long tournamentId) {
		return tournamentRepository.findById(tournamentId)
				.map(t -> ParticipantType.DOUBLE.name().equals(t.getParticipantType()))
				.orElse(false);
	}

	/**
	 * Chỉ hiện cột "Hạt giống" trong template/preview khi giải đang chọn phương thức xếp hạt
	 * giống {@link SeedingMethod#SEED} — chọn RANDOM/RANK thì cột này vô nghĩa, ẩn đi cho đỡ rối.
	 */
	private boolean hasSeedColumn(Long tournamentId) {
		return tournamentConfigRepository.findById(tournamentId)
				.map(TournamentConfig::getSeedingMethod)
				.map(SeedingMethod.SEED.name()::equals)
				.orElse(false);
	}

	@Override
	public byte[] buildImportTemplate(Long tournamentId) throws IOException {
		boolean isDouble = isDouble(tournamentId);
		boolean hasSeed = hasSeedColumn(tournamentId);
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
				header.createCell(4).setCellValue(RANK_COLUMN_HEADER);

				sample.createCell(0).setCellValue("Nguyễn Văn A");
				setPhoneCell(sample.createCell(1), textStyle, "0901234567");
				sample.createCell(2).setCellValue("Trần Văn B");
				setPhoneCell(sample.createCell(3), textStyle, "0907654321");
				sample.createCell(4).setCellValue("B");

				sheet.setColumnWidth(0, 20 * 256);
				sheet.setColumnWidth(1, 18 * 256);
				sheet.setColumnWidth(2, 20 * 256);
				sheet.setColumnWidth(3, 18 * 256);
				sheet.setColumnWidth(4, 26 * 256);

				if (hasSeed) {
					header.createCell(5).setCellValue(SEED_COLUMN_HEADER);
					sample.createCell(5).setCellValue(1);
					sheet.setColumnWidth(5, 30 * 256);
				}
			} else {
				header.createCell(0).setCellValue("Tên hiển thị");
				setPhoneCell(header.createCell(1), textStyle, "Số điện thoại");
				header.createCell(2).setCellValue(RANK_COLUMN_HEADER);

				sample.createCell(0).setCellValue("Nguyễn Văn A");
				setPhoneCell(sample.createCell(1), textStyle, "0901234567");
				sample.createCell(2).setCellValue("B");

				sheet.setColumnWidth(0, 20 * 256);
				sheet.setColumnWidth(1, 18 * 256);
				sheet.setColumnWidth(2, 26 * 256);

				if (hasSeed) {
					header.createCell(3).setCellValue(SEED_COLUMN_HEADER);
					sample.createCell(3).setCellValue(1);
					sheet.setColumnWidth(3, 30 * 256);
				}
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
		boolean isDouble = isDouble(tournamentId);
		boolean hasSeed = hasSeedColumn(tournamentId);
		// Công thức Excel ="..." để khi mở CSV, cột SĐT vẫn là text và giữ số 0 đầu
		String csv;
		if (isDouble) {
			csv = "Tên VĐV 1,SĐT VĐV 1,Tên VĐV 2,SĐT VĐV 2," + RANK_COLUMN_HEADER
					+ (hasSeed ? "," + SEED_COLUMN_HEADER : "")
					+ "\nNguyễn Văn A,=\"0901234567\",Trần Văn B,=\"0907654321\",B"
					+ (hasSeed ? ",1" : "") + "\n";
		} else {
			csv = "Tên hiển thị,Số điện thoại," + RANK_COLUMN_HEADER
					+ (hasSeed ? "," + SEED_COLUMN_HEADER : "")
					+ "\nNguyễn Văn A,=\"0901234567\",B" + (hasSeed ? ",1" : "") + "\n";
		}
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
	public ParticipantImportPreviewResponse previewFromExcel(Long tournamentId, MultipartFile file) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!TournamentStatus.isRosterEditable(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.TOURNAMENT_ROSTER_LOCKED);
		}
		assertNotFull(tournament);

		validateUploadFile(file);

		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
		}

		List<String[]> rows;
		if (isCsvUpload(file, content)) {
			rows = parseCsvRows(content);
		} else {
			try {
				rows = parseExcelRows(content);
			} catch (NotOfficeXmlFileException e) {
				log.warn("Excel parse failed, retry as CSV for tournament {}: {}", tournamentId, e.getMessage());
				rows = parseCsvRows(content);
			} catch (BusinessException e) {
				throw e;
			} catch (Exception e) {
				log.error("Preview parse failed for tournament {}: {}", tournamentId, e.getMessage(), e);
				throw new BusinessException(ErrorCode.PARTICIPANT_INVALID_EXCEL);
			}
		}

		return buildPreviewResponse(validateRows(tournament, rows));
	}

	@Override
	@Transactional
	public ImportParticipantResultResponse confirmImport(Long tournamentId, ParticipantImportConfirmRequest request) {
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!TournamentStatus.isRosterEditable(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.TOURNAMENT_ROSTER_LOCKED);
		}
		assertNotFull(tournament);

		boolean isDouble = ParticipantType.DOUBLE.name().equals(tournament.getParticipantType());
		List<String[]> rows = request.getRows().stream()
				.map(r -> {
					String rank = r.getBilliardRank();
					String seed = r.getSeedNo() != null ? String.valueOf(r.getSeedNo()) : null;
					// Mảng phải đủ MAX_IMPORT_COLUMNS và ĐÚNG vị trí cột theo SINGLE/DOUBLE,
					// vì validateRows đọc lại theo chỉ số cột.
					return isDouble
							? new String[] {r.getName1(), r.getPhone1(), r.getName2(), r.getPhone2(), rank, seed}
							: new String[] {r.getName1(), r.getPhone1(), rank, seed, null, null};
				})
				.toList();

		// Re-validate against current DB state (not the client-supplied preview) to guard against
		// races, e.g. another admin importing/adding a participant with the same seed in the meantime.
		return persistRows(tournament, validateRows(tournament, rows));
	}

	private ParticipantImportPreviewResponse buildPreviewResponse(List<ParsedRow> parsedRows) {
		List<ParticipantImportPreviewRowResponse> rows = parsedRows.stream()
				.map(p -> ParticipantImportPreviewRowResponse.builder()
						.rowNo(p.rowNo)
						.name1(p.name1)
						.phone1(p.phone1)
						.name2(p.name2)
						.phone2(p.phone2)
						.billiardRank(p.billiardRank)
						.seedNo(p.seedNo)
						.valid(p.valid)
						.error(p.error)
						.build())
				.toList();

		int validCount = (int) parsedRows.stream().filter(p -> p.valid).count();

		return ParticipantImportPreviewResponse.builder()
				.totalRows(parsedRows.size())
				.validCount(validCount)
				.invalidCount(parsedRows.size() - validCount)
				.rows(rows)
				.build();
	}

	private static final class ParsedRow {
		final int rowNo;
		final String name1;
		final String phone1;
		final String name2;
		final String phone2;
		/** {@code BilliardRank.name()}; dòng lỗi để null vì không được lưu. */
		final String billiardRank;
		/** Số hạt giống (null nếu không xếp hoặc dòng lỗi). */
		final Integer seedNo;
		final boolean valid;
		final String error;

		private ParsedRow(int rowNo, String name1, String phone1, String name2, String phone2,
				String billiardRank, Integer seedNo, boolean valid, String error) {
			this.rowNo = rowNo;
			this.name1 = name1;
			this.phone1 = phone1;
			this.name2 = name2;
			this.phone2 = phone2;
			this.billiardRank = billiardRank;
			this.seedNo = seedNo;
			this.valid = valid;
			this.error = error;
		}

		static ParsedRow ok(int rowNo, String name1, String phone1, String name2, String phone2,
				String billiardRank, Integer seedNo) {
			return new ParsedRow(rowNo, name1, phone1, name2, phone2, billiardRank, seedNo, true, null);
		}

		/** Giữ nguyên chữ ký cũ — dòng lỗi không cần mang hạng/hạt giống, thông báo lỗi đã nêu rõ giá trị sai. */
		static ParsedRow invalid(int rowNo, String name1, String phone1, String name2, String phone2, String error) {
			return new ParsedRow(rowNo, name1, phone1, name2, phone2, null, null, false, error);
		}
	}

	/** Validates rows without persisting anything. Reused by both preview and confirm. */
	private List<ParsedRow> validateRows(Tournament tournament, List<String[]> rows) {
		List<ParsedRow> result = new ArrayList<>();

		boolean isDouble = ParticipantType.DOUBLE.name().equals(tournament.getParticipantType());
		boolean hasSeed = hasSeedColumn(tournament.getId());
		int rankCol = isDouble ? 4 : 2;
		int seedCol = isDouble ? 5 : 3;

		List<Participant> activeParticipants = participantRepository
				.findByTournamentIdAndStatus(tournament.getId(), ParticipantStatus.ACTIVE.getValue());

		// Chặn trùng seedNo: vừa với người đã có trong giải, vừa GIỮA các dòng trong CHÍNH file này
		// (file tự trùng thì không request nào bắt được ở tầng DB vì chưa ai được lưu).
		Set<Integer> existingSeedNos = activeParticipants.stream()
				.map(Participant::getSeedNo).filter(Objects::nonNull).collect(Collectors.toSet());
		Set<Integer> seenInBatch = new HashSet<>();

		Integer maxParticipants = tournament.getMaxParticipants();
		int remainingSlots = maxParticipants == null
				? Integer.MAX_VALUE
				: maxParticipants - activeParticipants.size();

		for (int i = 0; i < rows.size(); i++) {
			String[] cols = rows.get(i);
			if (isDataRowEmpty(cols, rankCol)) {
				continue;
			}

			int rowNo = i + 2;

			String name1 = normalizeCell(cols, 0);
			String phone1 = normalizePhone(normalizeCell(cols, 1));
			String name2 = isDouble ? normalizeCell(cols, 2) : null;
			String phone2 = isDouble ? normalizePhone(normalizeCell(cols, 3)) : null;

			if (name1 == null || name1.isBlank()) {
				result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
						"Tên VĐV 1 không được để trống"));
				continue;
			}
			if (name1.length() > 255) {
				result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2, "Tên quá dài"));
				continue;
			}
			if (phone1 != null && !phone1.isBlank() && !isValidPhone(phone1)) {
				result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
						"Số điện thoại VĐV 1 không hợp lệ"));
				continue;
			}

			if (isDouble) {
				if (name2 == null || name2.isBlank()) {
					result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
							"Tên VĐV 2 không được để trống (giải đôi)"));
					continue;
				}
				if (name2.length() > 255) {
					result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2, "Tên VĐV 2 quá dài"));
					continue;
				}
				if (phone2 != null && !phone2.isBlank() && !isValidPhone(phone2)) {
					result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
							"Số điện thoại VĐV 2 không hợp lệ"));
					continue;
				}
			}

			if (remainingSlots <= 0) {
				result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
						"Giải đã đủ " + maxParticipants + " người tham gia, không thể thêm nữa"));
				continue;
			}

			// Hạng cơ thủ — để trống là hợp lệ (UNKNOWN), nhưng gõ sai thì báo lỗi thay vì
			// âm thầm quy về UNKNOWN, tránh việc BQT tưởng đã nhập hạng mà thực ra không có.
			String rankRaw = normalizeCell(cols, rankCol);
			String billiardRank = BilliardRank.UNKNOWN.name();
			if (rankRaw != null && !rankRaw.isBlank()) {
				if (!BilliardRank.isValid(rankRaw)) {
					result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
							"Hạng \"" + rankRaw.trim() + "\" không hợp lệ — chỉ nhận CN, A đến L (bỏ trống nếu chưa rõ)"));
					continue;
				}
				// Chuẩn hoá về name() để DB thống nhất với luồng hồ sơ (VD "CN" → "CHAMPION")
				billiardRank = BilliardRank.fromNullable(rankRaw).name();
			}

			// Hạt giống — chỉ đọc/validate khi giải đang chọn seedingMethod = SEED; nếu không thì
			// cột này (nếu có trong file) bị bỏ qua, tránh gán nhầm giá trị vô nghĩa với PP khác.
			Integer seedNo = null;
			if (hasSeed) {
				String seedRaw = normalizeCell(cols, seedCol);
				if (seedRaw != null && !seedRaw.isBlank()) {
					try {
						seedNo = Integer.parseInt(seedRaw.trim());
					} catch (NumberFormatException e) {
						result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
								"Hạt giống \"" + seedRaw.trim() + "\" không hợp lệ — chỉ nhận số nguyên dương"));
						continue;
					}
					if (seedNo < 1) {
						result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
								"Số hạt giống phải từ 1 trở lên"));
						continue;
					}
					if (maxParticipants != null && seedNo > maxParticipants) {
						result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
								"Số hạt giống " + seedNo + " vượt quá số người tối đa của giải (" + maxParticipants + ")"));
						continue;
					}
					if (existingSeedNos.contains(seedNo) || !seenInBatch.add(seedNo)) {
						result.add(ParsedRow.invalid(rowNo, name1, phone1, name2, phone2,
								"Số hạt giống " + seedNo + " đã được gán cho người tham gia khác"));
						continue;
					}
				}
			}

			remainingSlots--;
			result.add(ParsedRow.ok(rowNo, name1, phone1, name2, phone2, billiardRank, seedNo));
		}

		return result;
	}

	/** Persists already-validated rows. Invalid rows are counted as skipped with their reason. */
	private ImportParticipantResultResponse persistRows(Tournament tournament, List<ParsedRow> parsedRows) {
		List<String> errors = new ArrayList<>();
		int imported = 0;
		int skipped = 0;

		boolean isDouble = ParticipantType.DOUBLE.name().equals(tournament.getParticipantType());

		for (ParsedRow row : parsedRows) {
			if (!row.valid) {
				errors.add("Hàng " + row.rowNo + ": " + row.error);
				skipped++;
				continue;
			}

			String displayName = isDouble
					? ParticipantMemberFactory.composeDoubleDisplayName(row.name1, row.name2)
					: row.name1;

			Registration registration = registrationRepository.save(Registration.builder()
					.tournament(tournament)
					.user(null)
					.registrationType(RegistrationType.MANUAL.getValue())
					.playerFullName(displayName)
					.playerPhone(row.phone1)
					.status(RegistrationStatus.APPROVED.getValue())
					.build());

			Participant participant = participantRepository.save(Participant.builder()
					.tournament(tournament)
					.registration(registration)
					.participantType(tournament.getParticipantType())
					.displayName(displayName)
					.billiardRank(row.billiardRank)
					.seedNo(row.seedNo)
					.status(ParticipantStatus.ACTIVE.getValue())
					.build());

			if (isDouble) {
				participantMemberRepository.saveAll(ParticipantMemberFactory.buildDoubleMembers(
						participant, row.name1, row.phone1, null, row.name2, row.phone2));
			}
			imported++;
		}

		return ImportParticipantResultResponse.builder()
				.totalRows(parsedRows.size())
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
				String[] cols = new String[MAX_IMPORT_COLUMNS];
				for (int c = 0; c < MAX_IMPORT_COLUMNS; c++) {
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

	/** Chặn ngay từ đầu nếu giải đã đủ số người — nhất quán với "Thêm thủ công". */
	private void assertNotFull(Tournament tournament) {
		if (tournament.getMaxParticipants() == null) {
			return;
		}
		long activeCount = participantRepository.countByTournamentIdAndStatus(
				tournament.getId(), ParticipantStatus.ACTIVE.getValue());
		if (activeCount >= tournament.getMaxParticipants()) {
			throw new BusinessException(ErrorCode.TOURNAMENT_FULL);
		}
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

	private boolean isDataRowEmpty(String[] cols, int lastCol) {
		if (cols == null || cols.length == 0) {
			return true;
		}
		for (int i = 0; i <= lastCol; i++) {
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
