package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.LabeledAmountItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TrendPointResponse;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.TournamentFinanceEntry;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.FinanceEntryType;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentFinanceEntryRepository;
import com.capstone.su26_sep490_g2_be.service.AnalyticsExcelService;
import com.capstone.su26_sep490_g2_be.service.AnalyticsService;
import com.capstone.su26_sep490_g2_be.util.ExcelSanitizer;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsExcelServiceImpl implements AnalyticsExcelService {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	/** Màu chủ đạo khớp thương hiệu FE (indigo-600 #4F46E5 dùng cho biểu đồ doanh thu). */
	private static final byte[] BRAND_INDIGO = rgb(0x4F, 0x46, 0xE5);
	private static final byte[] BRAND_INDIGO_LIGHT = rgb(0x63, 0x66, 0xF1);
	private static final byte[] LABEL_FILL = rgb(0xEE, 0xF0, 0xFC);
	private static final byte[] BORDER_GRAY = rgb(0xD1, 0xD5, 0xDB);

	private final AnalyticsService analyticsService;
	private final MatchRepository matchRepository;
	private final TournamentFinanceEntryRepository financeEntryRepository;

	private static byte[] rgb(int r, int g, int b) {
		return new byte[]{(byte) r, (byte) g, (byte) b};
	}

	@Override
	public byte[] buildReport(Long ownerId, Instant from, Instant to, List<Long> branchIds) throws IOException {
		AnalyticsOverviewResponse overview = analyticsService.buildOverview(ownerId, from, to, branchIds, null, null);
		RevenueBreakdownResponse revenue = analyticsService.buildRevenueBreakdown(ownerId, from, to, "month", branchIds, null, null);
		List<TournamentPerformanceItem> tournaments = analyticsService.buildTournamentPerformance(ownerId, from, to, branchIds, null, null);
		List<PlayerLeaderboardItem> players = analyticsService.buildPlayerLeaderboard(ownerId, from, to, branchIds, null, null, null, null, null, null);
		SocialEngagementResponse social = analyticsService.buildSocialEngagement(ownerId, from, to, branchIds);
		TransactionStatsResponse transactions = analyticsService.buildTransactionStats(ownerId, from, to, "month", branchIds, null, null);

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ReportStyles st = buildStyles(workbook);

			writeOverviewSheet(workbook, st, overview, social, from, to);
			writeRevenueSheet(workbook, st, revenue);
			writeTransactionSheet(workbook, st, "Giao dịch", transactions);
			writeTournamentsSheet(workbook, st, tournaments);
			writePlayersSheet(workbook, st, players);

			workbook.write(out);
			return out.toByteArray();
		}
	}

	/** open-in-view=false (application.yml) — Match/TournamentFinanceEntry trả về từ repository ở
	 * đây có quan hệ LAZY (vd TournamentFinanceEntry.createdBy → User.profile), phải đọc trong lúc
	 * session còn mở thì writeFinanceEntriesSheet/writeMatchesSheet mới truy cập được, không thì
	 * LazyInitializationException ngay khi ghi sheet. */
	@Override
	@Transactional(readOnly = true)
	public byte[] buildTournamentReport(Long ownerId, Long tournamentId, List<Long> branchIds) throws IOException {
		TournamentAnalyticsDetailResponse d = analyticsService.buildTournamentDetail(ownerId, tournamentId, branchIds);

		// Quyền truy cập giải đã được analyticsService.buildTournamentDetail() xác thực ở trên (ném lỗi
		// nếu không có quyền) — an toàn để truy vấn trực tiếp theo tournamentId bên dưới, không cần
		// kiểm tra lại.
		List<Match> matches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournamentId).stream()
				.sorted(Comparator
						.comparing((Match m) -> m.getStage() != null && m.getStage().getOrderNo() != null ? m.getStage().getOrderNo() : 0)
						.thenComparing(m -> m.getRoundNo() != null ? m.getRoundNo() : 0)
						.thenComparing(m -> m.getPositionNo() != null ? m.getPositionNo() : 0))
				.toList();
		List<TournamentFinanceEntry> financeEntries = financeEntryRepository.findByTournamentIdOrderByOccurredAtDescIdDesc(tournamentId);

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ReportStyles st = buildStyles(workbook);

			writeTournamentOverviewSheet(workbook, st, d);
			writeStatusCountSheet(workbook, st, "Đăng ký",
					d.getRegistrationStats().getByStatus(), d.getRegistrationStats().getMonthlyTrend());
			writeMatchesSheet(workbook, st, d, matches);
			writeFinanceEntriesSheet(workbook, st, financeEntries);
			writeTransactionSheet(workbook, st, "Giao dịch", d.getTransactionStats());

			workbook.write(out);
			return out.toByteArray();
		}
	}

	@Override
	public byte[] buildMonthlyReport(Long ownerId, YearMonth from, YearMonth to, List<Long> branchIds) throws IOException {
		MonthlyReportResponse report = analyticsService.buildMonthlyReport(ownerId, from, to, branchIds);
		String rangeLabel = (from != null ? from : "?") + " - " + (to != null ? to : "?");

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ReportStyles st = buildStyles(workbook);
			XSSFSheet sheet = workbook.createSheet("Doanh thu theo tháng");
			sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
			int r = 0;

			r = banner(sheet, r, "Báo cáo doanh thu theo tháng — " + rangeLabel, st, 4);
			r++;

			Row head = sheet.createRow(r++);
			String[] cols = {"Tháng", "Doanh thu (VNĐ)", "Số giao dịch", "Giải đấu mới", "Đăng ký mới"};
			tableHeaderRow(head, st, cols);

			for (MonthlyReportItem m : report.getMonths()) {
				Row row = sheet.createRow(r++);
				put(row, 0, m.getMonthLabel(), st.text());
				put(row, 1, m.getRevenue(), st.money());
				put(row, 2, m.getTransactionCount(), st.number());
				put(row, 3, m.getNewTournaments(), st.number());
				put(row, 4, m.getNewRegistrations(), st.number());
			}

			Row totalRow = sheet.createRow(r++);
			put(totalRow, 0, "Tổng cộng", st.tableHeader());
			put(totalRow, 1, report.getTotalRevenue(), st.tableHeaderMoney());
			put(totalRow, 2, report.getTotalTransactions(), st.tableHeader());
			put(totalRow, 3, report.getTotalNewTournaments(), st.tableHeader());
			put(totalRow, 4, report.getTotalNewRegistrations(), st.tableHeader());

			sheet.createFreezePane(0, head.getRowNum() + 1);
			int[] widths = {18, 20, 16, 16, 16};
			for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, widths[i] * 256);

			workbook.write(out);
			return out.toByteArray();
		}
	}

	/* ══════════════════════════════════════════════════════════════════
	 *  Style palette — dùng chung cho mọi sheet để báo cáo đồng bộ 1 giao diện.
	 * ══════════════════════════════════════════════════════════════════ */

	private record ReportStyles(
			CellStyle title,
			CellStyle section,
			CellStyle tableHeader,
			CellStyle tableHeaderMoney,
			CellStyle label,
			CellStyle text,
			CellStyle number,
			CellStyle money
	) {}

	private ReportStyles buildStyles(XSSFWorkbook wb) {
		Font whiteBold = wb.createFont();
		whiteBold.setBold(true);
		whiteBold.setColor(IndexedColors.WHITE.getIndex());
		whiteBold.setFontHeightInPoints((short) 11);

		Font titleFont = wb.createFont();
		titleFont.setBold(true);
		titleFont.setColor(IndexedColors.WHITE.getIndex());
		titleFont.setFontHeightInPoints((short) 15);

		org.apache.poi.xssf.usermodel.XSSFFont sectionFont = wb.createFont();
		sectionFont.setBold(true);
		sectionFont.setFontHeightInPoints((short) 12);
		// Màu RGB tùy ý cho chữ tiêu đề mục — phải set qua XSSFColor, setColor(short) chỉ nhận
		// bảng màu indexed cũ (65 màu cố định), không map được RGB tùy ý sang đó.
		sectionFont.setColor(new XSSFColor(BRAND_INDIGO, null));

		Font labelFont = wb.createFont();
		labelFont.setBold(true);
		labelFont.setFontHeightInPoints((short) 11);

		XSSFCellStyle title = wb.createCellStyle();
		title.setFont(titleFont);
		title.setFillForegroundColor(new XSSFColor(BRAND_INDIGO, null));
		title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		title.setAlignment(HorizontalAlignment.LEFT);
		title.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

		XSSFCellStyle section = wb.createCellStyle();
		section.setFont(sectionFont);
		section.setBorderBottom(BorderStyle.MEDIUM);
		section.setBottomBorderColor(new XSSFColor(BRAND_INDIGO, null));

		XSSFCellStyle tableHeader = wb.createCellStyle();
		tableHeader.setFont(whiteBold);
		tableHeader.setFillForegroundColor(new XSSFColor(BRAND_INDIGO_LIGHT, null));
		tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		tableHeader.setAlignment(HorizontalAlignment.CENTER);
		borderAll(tableHeader);

		XSSFCellStyle tableHeaderMoney = wb.createCellStyle();
		tableHeaderMoney.cloneStyleFrom(tableHeader);
		tableHeaderMoney.setAlignment(HorizontalAlignment.RIGHT);
		tableHeaderMoney.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

		XSSFCellStyle label = wb.createCellStyle();
		label.setFont(labelFont);
		label.setFillForegroundColor(new XSSFColor(LABEL_FILL, null));
		label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		borderAll(label);

		XSSFCellStyle text = wb.createCellStyle();
		borderAll(text);
		text.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

		XSSFCellStyle number = wb.createCellStyle();
		number.cloneStyleFrom(text);
		number.setAlignment(HorizontalAlignment.RIGHT);

		XSSFCellStyle money = wb.createCellStyle();
		money.cloneStyleFrom(number);
		money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

		return new ReportStyles(title, section, tableHeader, tableHeaderMoney, label, text, number, money);
	}

	private void borderAll(XSSFCellStyle style) {
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		XSSFColor c = new XSSFColor(BORDER_GRAY, null);
		style.setTopBorderColor(c);
		style.setBottomBorderColor(c);
		style.setLeftBorderColor(c);
		style.setRightBorderColor(c);
	}

	/** Banner tiêu đề sheet — nền indigo, chữ trắng, merge ngang cho nổi bật. Trả về dòng kế tiếp. */
	private int banner(Sheet sheet, int rowIdx, String text, ReportStyles st, int mergeToCol) {
		Row row = sheet.createRow(rowIdx);
		row.setHeightInPoints(24);
		put(row, 0, text, st.title());
		for (int i = 1; i <= mergeToCol; i++) put(row, i, "", st.title());
		sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, mergeToCol));
		return rowIdx + 1;
	}

	/** Tiêu đề mục con (không merge) — chữ đậm màu indigo, viền dưới. */
	private int sectionTitle(Sheet sheet, int rowIdx, String text, ReportStyles st) {
		Row row = sheet.createRow(rowIdx);
		row.setHeightInPoints(18);
		put(row, 0, text, st.section());
		return rowIdx + 1;
	}

	private void tableHeaderRow(Row row, ReportStyles st, String... cols) {
		for (int i = 0; i < cols.length; i++) put(row, i, cols[i], st.tableHeader());
	}

	private Cell put(Row row, int col, Object value, CellStyle style) {
		Cell cell = row.createCell(col);
		if (value == null) {
			cell.setCellValue("—");
		} else if (value instanceof BigDecimal bd) {
			cell.setCellValue(bd.doubleValue());
		} else if (value instanceof Number n) {
			cell.setCellValue(n.doubleValue());
		} else {
			cell.setCellValue(ExcelSanitizer.sanitize(value.toString()));
		}
		if (style != null) cell.setCellStyle(style);
		return cell;
	}

	/** Dòng nhãn/giá trị (label bên trái tô nền, value bên phải) — value dùng style money nếu là BigDecimal. */
	private int kv(Sheet sheet, int rowIdx, String label, Object value, ReportStyles st) {
		Row row = sheet.createRow(rowIdx);
		put(row, 0, label, st.label());
		CellStyle valueStyle = value instanceof BigDecimal ? st.money()
				: value instanceof Number ? st.number() : st.text();
		put(row, 1, value, valueStyle);
		return rowIdx + 1;
	}

	/* ══════════════════════════════════════════════════════════════════
	 *  Báo cáo tổng hợp nhiều giải (buildReport)
	 * ══════════════════════════════════════════════════════════════════ */

	private void writeOverviewSheet(XSSFWorkbook workbook, ReportStyles st,
			AnalyticsOverviewResponse o, SocialEngagementResponse social, Instant from, Instant to) {
		XSSFSheet sheet = workbook.createSheet("Tổng quan");
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		int r = 0;

		r = banner(sheet, r, "Báo cáo thống kê & phân tích", st, 3);
		if (from != null && to != null) {
			Row rangeRow = sheet.createRow(r++);
			put(rangeRow, 0, "Khoảng thời gian: " + DATE_FMT.format(from.atZone(ZONE)) + " - " + DATE_FMT.format(to.atZone(ZONE)), st.text());
		}
		r++;

		r = kv(sheet, r, "Tổng doanh thu (VNĐ)", o.getTotalRevenue(), st);
		r = kv(sheet, r, "Doanh thu kỳ trước (VNĐ)", o.getRevenuePrevPeriod(), st);
		r = kv(sheet, r, "Tăng trưởng doanh thu (%)", o.getRevenueGrowthPct(), st);
		r = kv(sheet, r, "Tổng giải đấu", o.getTotalTournaments(), st);
		r = kv(sheet, r, "Tỷ lệ lấp đầy trung bình (%)", o.getAvgFillRatePct(), st);
		r = kv(sheet, r, "Tổng người chơi (duy nhất)", o.getTotalUniquePlayers(), st);
		r = kv(sheet, r, "Giải đấu doanh thu cao nhất", o.getTopTournamentName(), st);
		r = kv(sheet, r, "Doanh thu giải đấu quán quân (VNĐ)", o.getTopTournamentRevenue(), st);
		r = kv(sheet, r, "Chi nhánh doanh thu cao nhất", o.getTopBranchName(), st);
		r++;

		r = sectionTitle(sheet, r, "Hiệu quả truyền thông Facebook", st);
		r = kv(sheet, r, "Tổng bài đăng", social.getTotalPosts(), st);
		r = kv(sheet, r, "Tổng lượt thích", social.getTotalLikes(), st);
		r = kv(sheet, r, "Tổng bình luận", social.getTotalComments(), st);
		r = kv(sheet, r, "Tổng lượt chia sẻ", social.getTotalShares(), st);
		r = kv(sheet, r, "Tổng lượt tiếp cận", social.getTotalReach(), st);
		kv(sheet, r, "Giải đấu có bài đăng nổi bật nhất", social.getTopPostTournamentName(), st);

		sheet.setColumnWidth(0, 36 * 256);
		sheet.setColumnWidth(1, 28 * 256);
	}

	private void writeRevenueSheet(XSSFWorkbook workbook, ReportStyles st, RevenueBreakdownResponse revenue) {
		XSSFSheet sheet = workbook.createSheet("Doanh thu");
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		int r = 0;

		r = banner(sheet, r, "Doanh thu", st, 2);
		r++;
		r = sectionTitle(sheet, r, "Xu hướng doanh thu theo thời gian", st);
		Row trendHeader = sheet.createRow(r++);
		tableHeaderRow(trendHeader, st, "Kỳ", "Số giao dịch", "Doanh thu (VNĐ)");
		for (TrendPointResponse p : revenue.getTrend()) {
			Row row = sheet.createRow(r++);
			put(row, 0, p.getPeriod(), st.text());
			put(row, 1, p.getCount(), st.number());
			put(row, 2, p.getAmount(), st.money());
		}
		r++;

		r = sectionTitle(sheet, r, "Doanh thu theo chi nhánh", st);
		r = writeLabeledAmountTable(sheet, r, st, revenue.getByBranch());
		r++;

		r = sectionTitle(sheet, r, "Top 10 giải đấu theo doanh thu", st);
		r = writeLabeledAmountTable(sheet, r, st, revenue.getByTournament());
		r++;

		r = sectionTitle(sheet, r, "Doanh thu theo phương thức thanh toán", st);
		Row methodHeader = sheet.createRow(r++);
		tableHeaderRow(methodHeader, st, "Phương thức", "Số giao dịch");
		for (StatusCountItem item : revenue.getByPaymentMethod()) {
			Row row = sheet.createRow(r++);
			put(row, 0, item.getLabel(), st.text());
			put(row, 1, item.getCount(), st.number());
		}

		sheet.setColumnWidth(0, 32 * 256);
		sheet.setColumnWidth(1, 18 * 256);
		sheet.setColumnWidth(2, 20 * 256);
	}

	private int writeLabeledAmountTable(Sheet sheet, int rowIdx, ReportStyles st, List<LabeledAmountItem> items) {
		Row head = sheet.createRow(rowIdx++);
		tableHeaderRow(head, st, "Tên", "Số giao dịch", "Doanh thu (VNĐ)");
		for (LabeledAmountItem item : items) {
			Row row = sheet.createRow(rowIdx++);
			put(row, 0, item.getLabel(), st.text());
			put(row, 1, item.getCount(), st.number());
			put(row, 2, item.getAmount(), st.money());
		}
		return rowIdx;
	}

	private void writeTournamentsSheet(XSSFWorkbook workbook, ReportStyles st, List<TournamentPerformanceItem> items) {
		XSSFSheet sheet = workbook.createSheet("Giải đấu");
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		String[] cols = {"Tên giải đấu", "Chi nhánh", "Số VĐV", "Tối đa", "Tỷ lệ lấp đầy (%)",
				"Doanh thu (VNĐ)", "Thu khác (VNĐ)", "Tiền thưởng (VNĐ)", "Chi phí (VNĐ)", "Lợi nhuận (VNĐ)",
				"Trạng thái", "Tỷ lệ hoàn thành (%)"};
		Row head = sheet.createRow(0);
		tableHeaderRow(head, st, cols);

		int r = 1;
		for (TournamentPerformanceItem item : items) {
			Row row = sheet.createRow(r++);
			put(row, 0, item.getName(), st.text());
			put(row, 1, item.getBranchName(), st.text());
			put(row, 2, item.getParticipants(), st.number());
			put(row, 3, item.getMaxParticipants(), st.number());
			put(row, 4, item.getFillRatePct(), st.number());
			put(row, 5, item.getRevenue(), st.money());
			put(row, 6, item.getOtherIncome(), st.money());
			put(row, 7, item.getPrizePool(), st.money());
			put(row, 8, item.getExpense(), st.money());
			put(row, 9, item.getNetProfit(), st.money());
			put(row, 10, item.getStatusLabel(), st.text());
			put(row, 11, item.getCompletionRatePct(), st.number());
		}
		sheet.createFreezePane(0, 1);
		for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 20 * 256);
		sheet.setColumnWidth(0, 30 * 256);
	}

	private void writeTransactionSheet(XSSFWorkbook workbook, ReportStyles st, String sheetName, TransactionStatsResponse t) {
		XSSFSheet sheet = workbook.createSheet(sheetName);
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		int r = 0;

		r = banner(sheet, r, sheetName, st, 2);
		r++;
		r = kv(sheet, r, "Tổng số giao dịch", t.getTotalTransactions(), st);
		r = kv(sheet, r, "Thành công", t.getSuccessCount(), st);
		r = kv(sheet, r, "Chờ xử lý", t.getPendingCount(), st);
		r = kv(sheet, r, "Thất bại", t.getFailedCount(), st);
		r = kv(sheet, r, "Đã hủy", t.getCancelledCount(), st);
		r = kv(sheet, r, "Tỷ lệ thành công (%)", t.getSuccessRatePct(), st);
		r = kv(sheet, r, "Tổng giá trị thành công (VNĐ)", t.getTotalAmount(), st);
		r = kv(sheet, r, "Giá trị TB / giao dịch (VNĐ)", t.getAvgTransactionValue(), st);
		r = kv(sheet, r, "Thời gian xử lý TB (phút)", t.getAvgConversionMinutes(), st);
		r++;

		r = sectionTitle(sheet, r, "Theo phương thức thanh toán", st);
		Row methodHeader = sheet.createRow(r++);
		tableHeaderRow(methodHeader, st, "Phương thức", "Số giao dịch");
		for (StatusCountItem item : t.getByMethod()) {
			Row row = sheet.createRow(r++);
			put(row, 0, item.getLabel(), st.text());
			put(row, 1, item.getCount(), st.number());
		}
		r++;

		r = sectionTitle(sheet, r, "Xu hướng theo kỳ", st);
		Row trendHeader = sheet.createRow(r++);
		tableHeaderRow(trendHeader, st, "Kỳ", "Số giao dịch thành công", "Doanh thu (VNĐ)");
		for (TrendPointResponse p : t.getTrend()) {
			Row row = sheet.createRow(r++);
			put(row, 0, p.getPeriod(), st.text());
			put(row, 1, p.getCount(), st.number());
			put(row, 2, p.getAmount(), st.money());
		}

		sheet.setColumnWidth(0, 32 * 256);
		sheet.setColumnWidth(1, 22 * 256);
		sheet.setColumnWidth(2, 20 * 256);
	}

	private void writePlayersSheet(XSSFWorkbook workbook, ReportStyles st, List<PlayerLeaderboardItem> items) {
		XSSFSheet sheet = workbook.createSheet("Cơ thủ");
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		String[] cols = {"Hạng", "Tên cơ thủ", "Số giải đã đấu", "Số lần vô địch", "Số lần vào top 3",
				"Tổng tiền thưởng (VNĐ)", "Tổng điểm"};
		Row head = sheet.createRow(0);
		tableHeaderRow(head, st, cols);

		int r = 1;
		int rank = 1;
		for (PlayerLeaderboardItem item : items) {
			Row row = sheet.createRow(r++);
			put(row, 0, rank++, st.number());
			put(row, 1, item.getPlayerName(), st.text());
			put(row, 2, item.getTournamentsPlayed(), st.number());
			put(row, 3, item.getChampionCount(), st.number());
			put(row, 4, item.getTop3Count(), st.number());
			put(row, 5, item.getTotalPrizeAmount(), st.money());
			put(row, 6, item.getTotalPoints(), st.number());
		}
		sheet.createFreezePane(0, 1);
		for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 20 * 256);
		sheet.setColumnWidth(1, 26 * 256);
	}

	/* ══════════════════════════════════════════════════════════════════
	 *  Báo cáo 1 giải đấu (buildTournamentReport)
	 * ══════════════════════════════════════════════════════════════════ */

	private void writeTournamentOverviewSheet(XSSFWorkbook workbook, ReportStyles st, TournamentAnalyticsDetailResponse d) {
		XSSFSheet sheet = workbook.createSheet("Tổng quan");
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		int r = 0;

		r = banner(sheet, r, d.getName(), st, 3);
		r++;

		r = kv(sheet, r, "Chi nhánh", d.getBranchName(), st);
		r = kv(sheet, r, "Loại bi", d.getGameTypeLabel(), st);
		r = kv(sheet, r, "Thể thức", d.getFormatLabel(), st);
		r = kv(sheet, r, "Trạng thái", d.getStatusLabel(), st);
		r = kv(sheet, r, "Phí đăng ký (VNĐ)", d.getEntryFee(), st);
		r = kv(sheet, r, "Tiền thưởng (VNĐ)", d.getPrizePool(), st);
		r = kv(sheet, r, "Số VĐV tối đa", d.getMaxParticipants(), st);
		r = kv(sheet, r, "Ngày bắt đầu", d.getStartAt() != null ? DATE_FMT.format(d.getStartAt().atZone(ZONE)) : "—", st);
		r = kv(sheet, r, "Ngày kết thúc", d.getEndAt() != null ? DATE_FMT.format(d.getEndAt().atZone(ZONE)) : "—", st);
		r = kv(sheet, r, "Tỷ lệ lấp đầy (%)", d.getFillRatePct(), st);
		r++;

		r = sectionTitle(sheet, r, "Tài chính", st);
		r = kv(sheet, r, "Doanh thu đăng ký (VNĐ)", d.getTransactionStats().getTotalAmount(), st);
		r = kv(sheet, r, "Thu khác (VNĐ)", d.getOtherIncome(), st);
		r = kv(sheet, r, "Tiền thưởng (VNĐ)", d.getPrizePool(), st);
		r = kv(sheet, r, "Chi phí phát sinh (VNĐ)", d.getExpense(), st);
		r = kv(sheet, r, "Lợi nhuận (VNĐ)", d.getNetProfit(), st);
		r++;

		r = sectionTitle(sheet, r, "Thi đấu", st);
		r = kv(sheet, r, "Tổng số VĐV đang thi đấu", d.getParticipantStats().getActive(), st);
		r = kv(sheet, r, "Trận đã hoàn thành / tổng số trận", d.getMatchStats().getCompleted() + " / " + d.getMatchStats().getTotal(), st);
		r++;

		if (d.getSocial() != null && d.getSocial().getTotalPosts() > 0) {
			r = sectionTitle(sheet, r, "Truyền thông Facebook", st);
			r = kv(sheet, r, "Số bài đăng", d.getSocial().getTotalPosts(), st);
			r = kv(sheet, r, "Lượt thích", d.getSocial().getTotalLikes(), st);
			r = kv(sheet, r, "Bình luận", d.getSocial().getTotalComments(), st);
			r = kv(sheet, r, "Chia sẻ", d.getSocial().getTotalShares(), st);
			kv(sheet, r, "Lượt tiếp cận", d.getSocial().getTotalReach(), st);
		}

		sheet.setColumnWidth(0, 34 * 256);
		sheet.setColumnWidth(1, 26 * 256);
	}

	private void writeStatusCountSheet(XSSFWorkbook workbook, ReportStyles st, String sheetName,
			List<StatusCountItem> byStatus, List<TrendPointResponse> trend) {
		XSSFSheet sheet = workbook.createSheet(sheetName);
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		int r = 0;

		r = banner(sheet, r, sheetName, st, 1);
		r++;
		Row statusHeader = sheet.createRow(r++);
		tableHeaderRow(statusHeader, st, "Trạng thái", "Số lượng");
		for (StatusCountItem item : byStatus) {
			Row row = sheet.createRow(r++);
			put(row, 0, item.getLabel(), st.text());
			put(row, 1, item.getCount(), st.number());
		}

		if (trend != null && !trend.isEmpty()) {
			r++;
			r = sectionTitle(sheet, r, "Xu hướng theo kỳ", st);
			Row trendHeader = sheet.createRow(r++);
			tableHeaderRow(trendHeader, st, "Kỳ", "Số lượng");
			for (TrendPointResponse p : trend) {
				Row row = sheet.createRow(r++);
				put(row, 0, p.getPeriod(), st.text());
				put(row, 1, p.getCount(), st.number());
			}
		}

		sheet.setColumnWidth(0, 28 * 256);
		sheet.setColumnWidth(1, 18 * 256);
	}

	/** Danh sách từng trận đấu — không suy diễn nhãn "Tứ kết/Bán kết" (dễ sai với nhánh
	 * Thắng/Thua của loại kép, xem BracketDiagram.jsx FE), chỉ ghi Nhánh + số Vòng thô. */
	private void writeMatchesSheet(XSSFWorkbook workbook, ReportStyles st, TournamentAnalyticsDetailResponse d, List<Match> matches) {
		XSSFSheet sheet = workbook.createSheet("Trận đấu");
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		int r = 0;

		r = banner(sheet, r, "Trận đấu", st, 3);
		r++;
		r = kv(sheet, r, "Tổng số trận", d.getMatchStats().getTotal(), st);
		r = kv(sheet, r, "Đã hoàn thành", d.getMatchStats().getCompleted(), st);
		r = kv(sheet, r, "Đang đấu", d.getMatchStats().getInProgress(), st);
		r = kv(sheet, r, "Chờ đấu", d.getMatchStats().getPending(), st);
		r++;

		r = sectionTitle(sheet, r, "Danh sách trận đấu", st);
		String[] cols = {"STT", "Nhánh", "Vòng", "Mã trận", "Người chơi 1", "Tỉ số 1",
				"Người chơi 2", "Tỉ số 2", "Người thắng", "Trạng thái", "Bàn", "Giờ dự kiến"};
		Row head = sheet.createRow(r++);
		tableHeaderRow(head, st, cols);
		int headerRowIdx = head.getRowNum();

		int stt = 1;
		for (Match m : matches) {
			Row row = sheet.createRow(r++);
			put(row, 0, stt++, st.number());
			put(row, 1, m.getStage() != null ? m.getStage().getName() : "—", st.text());
			put(row, 2, m.getRoundNo(), st.number());
			put(row, 3, m.getMatchCode() != null ? m.getMatchCode() : "—", st.text());
			put(row, 4, participantName(m.getPlayer1()), st.text());
			put(row, 5, m.getPlayer1Score(), st.number());
			put(row, 6, participantName(m.getPlayer2()), st.text());
			put(row, 7, m.getPlayer2Score(), st.number());
			put(row, 8, participantName(m.getWinner()), st.text());
			put(row, 9, matchStatusLabel(m.getStatus()), st.text());
			put(row, 10, m.getTableNo(), st.number());
			put(row, 11, m.getScheduledAt() != null ? DATETIME_FMT.format(m.getScheduledAt().atZone(ZONE)) : "—", st.text());
		}

		sheet.createFreezePane(0, headerRowIdx + 1);
		int[] widths = {6, 16, 8, 12, 20, 8, 20, 8, 20, 14, 6, 18};
		for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
	}

	private String participantName(Participant p) {
		return p != null && p.getDisplayName() != null ? p.getDisplayName() : "TBD";
	}

	private String matchStatusLabel(String status) {
		try {
			return MatchStatus.valueOf(status).getDisplayName();
		} catch (IllegalArgumentException | NullPointerException e) {
			return status != null ? status : "—";
		}
	}

	/** Danh sách từng khoản thu/chi tự ghi nhận ngoài tiền đăng ký (xem TournamentFinanceEntry) —
	 * số tiền ghi có dấu (+/-) để cộng dồn trực tiếp được trong Excel. */
	private void writeFinanceEntriesSheet(XSSFWorkbook workbook, ReportStyles st, List<TournamentFinanceEntry> entries) {
		XSSFSheet sheet = workbook.createSheet("Thu chi");
		sheet.setTabColor(new XSSFColor(BRAND_INDIGO, null));
		int r = 0;

		BigDecimal totalIncome = BigDecimal.ZERO;
		BigDecimal totalExpense = BigDecimal.ZERO;
		for (TournamentFinanceEntry e : entries) {
			if (FinanceEntryType.INCOME.getValue().equals(e.getEntryType())) {
				totalIncome = totalIncome.add(e.getAmount());
			} else {
				totalExpense = totalExpense.add(e.getAmount());
			}
		}

		r = banner(sheet, r, "Thu chi ngoài tiền đăng ký", st, 3);
		r++;
		r = kv(sheet, r, "Tổng thu (VNĐ)", totalIncome, st);
		r = kv(sheet, r, "Tổng chi (VNĐ)", totalExpense, st);
		r = kv(sheet, r, "Chênh lệch (VNĐ)", totalIncome.subtract(totalExpense), st);
		r++;

		if (entries.isEmpty()) {
			kv(sheet, r, "Ghi chú", "Chưa có khoản thu/chi nào được ghi nhận cho giải này", st);
			sheet.setColumnWidth(0, 30 * 256);
			sheet.setColumnWidth(1, 24 * 256);
			return;
		}

		r = sectionTitle(sheet, r, "Danh sách khoản thu/chi", st);
		String[] cols = {"STT", "Loại", "Nội dung", "Ngày", "Số tiền (VNĐ)", "Ghi chú", "Người tạo"};
		Row head = sheet.createRow(r++);
		tableHeaderRow(head, st, cols);
		int headerRowIdx = head.getRowNum();

		int stt = 1;
		for (TournamentFinanceEntry e : entries) {
			boolean isIncome = FinanceEntryType.INCOME.getValue().equals(e.getEntryType());
			Row row = sheet.createRow(r++);
			put(row, 0, stt++, st.number());
			put(row, 1, isIncome ? FinanceEntryType.INCOME.getDisplayName() : FinanceEntryType.EXPENSE.getDisplayName(), st.text());
			put(row, 2, e.getLabel(), st.text());
			put(row, 3, e.getOccurredAt() != null ? DATE_FMT.format(e.getOccurredAt().atZone(ZONE)) : "—", st.text());
			put(row, 4, isIncome ? e.getAmount() : e.getAmount().negate(), st.money());
			put(row, 5, e.getNote() != null ? e.getNote() : "—", st.text());
			put(row, 6, financeCreatorName(e), st.text());
		}

		sheet.createFreezePane(0, headerRowIdx + 1);
		int[] widths = {6, 12, 30, 14, 16, 30, 22};
		for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
	}

	private String financeCreatorName(TournamentFinanceEntry e) {
		User createdBy = e.getCreatedBy();
		if (createdBy == null) return "—";
		return createdBy.getProfile() != null && createdBy.getProfile().getFullName() != null
				&& !createdBy.getProfile().getFullName().isBlank()
				? createdBy.getProfile().getFullName()
				: createdBy.getEmail();
	}
}
