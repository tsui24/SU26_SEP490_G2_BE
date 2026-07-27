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
import com.capstone.su26_sep490_g2_be.service.AnalyticsExcelService;
import com.capstone.su26_sep490_g2_be.service.AnalyticsService;
import com.capstone.su26_sep490_g2_be.util.ExcelSanitizer;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsExcelServiceImpl implements AnalyticsExcelService {

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final AnalyticsService analyticsService;

	@Override
	public byte[] buildReport(Long ownerId, Instant from, Instant to) throws IOException {
		AnalyticsOverviewResponse overview = analyticsService.buildOverview(ownerId, from, to);
		RevenueBreakdownResponse revenue = analyticsService.buildRevenueBreakdown(ownerId, from, to, "month");
		List<TournamentPerformanceItem> tournaments = analyticsService.buildTournamentPerformance(ownerId, from, to);
		List<PlayerLeaderboardItem> players = analyticsService.buildPlayerLeaderboard(ownerId, from, to);
		SocialEngagementResponse social = analyticsService.buildSocialEngagement(ownerId, from, to);
		TransactionStatsResponse transactions = analyticsService.buildTransactionStats(ownerId, from, to, "month");

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle headerStyle = headerStyle(workbook);

			writeOverviewSheet(workbook, headerStyle, overview, social, from, to);
			writeRevenueSheet(workbook, headerStyle, revenue);
			writeTransactionSheet(workbook, headerStyle, "Giao dịch", transactions);
			writeTournamentsSheet(workbook, headerStyle, tournaments);
			writePlayersSheet(workbook, headerStyle, players);

			workbook.write(out);
			return out.toByteArray();
		}
	}

	@Override
	public byte[] buildTournamentReport(Long ownerId, Long tournamentId) throws IOException {
		TournamentAnalyticsDetailResponse d = analyticsService.buildTournamentDetail(ownerId, tournamentId);

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle headerStyle = headerStyle(workbook);

			writeTournamentOverviewSheet(workbook, headerStyle, d);
			writeStatusCountSheet(workbook, headerStyle, "Đăng ký",
					d.getRegistrationStats().getByStatus(), d.getRegistrationStats().getMonthlyTrend());
			writeStatusCountSheet(workbook, headerStyle, "Trận đấu", d.getMatchStats().getByStatus(), null);
			writeTransactionSheet(workbook, headerStyle, "Giao dịch", d.getTransactionStats());

			workbook.write(out);
			return out.toByteArray();
		}
	}

	@Override
	public byte[] buildMonthlyReport(Long ownerId, YearMonth from, YearMonth to) throws IOException {
		MonthlyReportResponse report = analyticsService.buildMonthlyReport(ownerId, from, to);
		String rangeLabel = (from != null ? from : "?") + " - " + (to != null ? to : "?");

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle headerStyle = headerStyle(workbook);
			Sheet sheet = workbook.createSheet("Doanh thu theo tháng");
			int r = 0;

			header(sheet.createRow(r++), 0, "Báo cáo doanh thu theo tháng — " + rangeLabel, headerStyle);
			r++;

			Row head = sheet.createRow(r++);
			String[] cols = {"Tháng", "Doanh thu (VNĐ)", "Số giao dịch", "Giải đấu mới", "Đăng ký mới"};
			for (int i = 0; i < cols.length; i++) header(head, i, cols[i], headerStyle);

			for (MonthlyReportItem m : report.getMonths()) {
				Row row = sheet.createRow(r++);
				row.createCell(0).setCellValue(m.getMonthLabel());
				row.createCell(1).setCellValue(m.getRevenue() != null ? m.getRevenue().doubleValue() : 0);
				row.createCell(2).setCellValue(m.getTransactionCount());
				row.createCell(3).setCellValue(m.getNewTournaments());
				row.createCell(4).setCellValue(m.getNewRegistrations());
			}

			Row totalRow = sheet.createRow(r++);
			header(totalRow, 0, "Tổng cộng", headerStyle);
			header(totalRow, 1, report.getTotalRevenue() != null ? String.valueOf(report.getTotalRevenue().doubleValue()) : "0", headerStyle);
			header(totalRow, 2, String.valueOf(report.getTotalTransactions()), headerStyle);
			header(totalRow, 3, String.valueOf(report.getTotalNewTournaments()), headerStyle);
			header(totalRow, 4, String.valueOf(report.getTotalNewRegistrations()), headerStyle);

			for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 20 * 256);

			workbook.write(out);
			return out.toByteArray();
		}
	}

	private CellStyle headerStyle(XSSFWorkbook workbook) {
		Font boldFont = workbook.createFont();
		boldFont.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(boldFont);
		return style;
	}

	private Cell header(Row row, int col, String value, CellStyle style) {
		Cell cell = row.createCell(col);
		cell.setCellValue(ExcelSanitizer.sanitize(value));
		cell.setCellStyle(style);
		return cell;
	}

	private void writeOverviewSheet(XSSFWorkbook workbook, CellStyle headerStyle,
			AnalyticsOverviewResponse o, SocialEngagementResponse social, Instant from, Instant to) {
		Sheet sheet = workbook.createSheet("Tổng quan");
		int r = 0;

		Row title = sheet.createRow(r++);
		header(title, 0, "Báo cáo thống kê & phân tích", headerStyle);
		if (from != null && to != null) {
			sheet.createRow(r++).createCell(0)
					.setCellValue("Khoảng thời gian: " + DATE_FMT.format(from.atZone(ZONE)) + " - " + DATE_FMT.format(to.atZone(ZONE)));
		}
		r++;

		r = kv(sheet, r, "Tổng doanh thu (VNĐ)", o.getTotalRevenue());
		r = kv(sheet, r, "Doanh thu kỳ trước (VNĐ)", o.getRevenuePrevPeriod());
		r = kv(sheet, r, "Tăng trưởng doanh thu (%)", o.getRevenueGrowthPct());
		r = kv(sheet, r, "Tổng giải đấu", o.getTotalTournaments());
		r = kv(sheet, r, "Tỷ lệ lấp đầy trung bình (%)", o.getAvgFillRatePct());
		r = kv(sheet, r, "Tổng người chơi (duy nhất)", o.getTotalUniquePlayers());
		r = kv(sheet, r, "Giải đấu doanh thu cao nhất", o.getTopTournamentName());
		r = kv(sheet, r, "Doanh thu giải đấu quán quân (VNĐ)", o.getTopTournamentRevenue());
		r = kv(sheet, r, "Chi nhánh doanh thu cao nhất", o.getTopBranchName());
		r++;

		Row socialHeader = sheet.createRow(r++);
		header(socialHeader, 0, "Hiệu quả truyền thông Facebook", headerStyle);
		r = kv(sheet, r, "Tổng bài đăng", social.getTotalPosts());
		r = kv(sheet, r, "Tổng lượt thích", social.getTotalLikes());
		r = kv(sheet, r, "Tổng bình luận", social.getTotalComments());
		r = kv(sheet, r, "Tổng lượt chia sẻ", social.getTotalShares());
		r = kv(sheet, r, "Tổng lượt tiếp cận", social.getTotalReach());
		kv(sheet, r, "Giải đấu có bài đăng nổi bật nhất", social.getTopPostTournamentName());

		sheet.setColumnWidth(0, 36 * 256);
		sheet.setColumnWidth(1, 24 * 256);
	}

	private int kv(Sheet sheet, int rowIdx, String label, Object value) {
		Row row = sheet.createRow(rowIdx);
		row.createCell(0).setCellValue(label);
		Cell valueCell = row.createCell(1);
		if (value == null) {
			valueCell.setCellValue("—");
		} else if (value instanceof BigDecimal bd) {
			valueCell.setCellValue(bd.doubleValue());
		} else if (value instanceof Number n) {
			valueCell.setCellValue(n.doubleValue());
		} else {
			valueCell.setCellValue(ExcelSanitizer.sanitize(value.toString()));
		}
		return rowIdx + 1;
	}

	private void writeRevenueSheet(XSSFWorkbook workbook, CellStyle headerStyle, RevenueBreakdownResponse revenue) {
		Sheet sheet = workbook.createSheet("Doanh thu");
		int r = 0;

		header(sheet.createRow(r++), 0, "Xu hướng doanh thu theo thời gian", headerStyle);
		Row trendHeader = sheet.createRow(r++);
		header(trendHeader, 0, "Kỳ", headerStyle);
		header(trendHeader, 1, "Số giao dịch", headerStyle);
		header(trendHeader, 2, "Doanh thu (VNĐ)", headerStyle);
		for (TrendPointResponse p : revenue.getTrend()) {
			Row row = sheet.createRow(r++);
			row.createCell(0).setCellValue(p.getPeriod());
			row.createCell(1).setCellValue(p.getCount());
			row.createCell(2).setCellValue(p.getAmount() != null ? p.getAmount().doubleValue() : 0);
		}
		r++;

		header(sheet.createRow(r++), 0, "Doanh thu theo chi nhánh", headerStyle);
		r = writeLabeledAmountTable(sheet, r, headerStyle, revenue.getByBranch());
		r++;

		header(sheet.createRow(r++), 0, "Top 10 giải đấu theo doanh thu", headerStyle);
		r = writeLabeledAmountTable(sheet, r, headerStyle, revenue.getByTournament());
		r++;

		header(sheet.createRow(r++), 0, "Doanh thu theo phương thức thanh toán", headerStyle);
		Row methodHeader = sheet.createRow(r++);
		header(methodHeader, 0, "Phương thức", headerStyle);
		header(methodHeader, 1, "Số giao dịch", headerStyle);
		for (StatusCountItem item : revenue.getByPaymentMethod()) {
			Row row = sheet.createRow(r++);
			row.createCell(0).setCellValue(item.getLabel());
			row.createCell(1).setCellValue(item.getCount());
		}

		sheet.setColumnWidth(0, 32 * 256);
		sheet.setColumnWidth(1, 18 * 256);
		sheet.setColumnWidth(2, 20 * 256);
	}

	private int writeLabeledAmountTable(Sheet sheet, int rowIdx, CellStyle headerStyle, List<LabeledAmountItem> items) {
		Row head = sheet.createRow(rowIdx++);
		header(head, 0, "Tên", headerStyle);
		header(head, 1, "Số giao dịch", headerStyle);
		header(head, 2, "Doanh thu (VNĐ)", headerStyle);
		for (LabeledAmountItem item : items) {
			Row row = sheet.createRow(rowIdx++);
			row.createCell(0).setCellValue(item.getLabel());
			row.createCell(1).setCellValue(item.getCount());
			row.createCell(2).setCellValue(item.getAmount() != null ? item.getAmount().doubleValue() : 0);
		}
		return rowIdx;
	}

	private void writeTournamentsSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<TournamentPerformanceItem> items) {
		Sheet sheet = workbook.createSheet("Giải đấu");
		Row head = sheet.createRow(0);
		String[] cols = {"Tên giải đấu", "Chi nhánh", "Số VĐV", "Tối đa", "Tỷ lệ lấp đầy (%)",
				"Doanh thu (VNĐ)", "Tiền thưởng (VNĐ)", "Lợi nhuận (VNĐ)", "Trạng thái", "Tỷ lệ hoàn thành (%)"};
		for (int i = 0; i < cols.length; i++) header(head, i, cols[i], headerStyle);

		int r = 1;
		for (TournamentPerformanceItem item : items) {
			Row row = sheet.createRow(r++);
			row.createCell(0).setCellValue(ExcelSanitizer.sanitize(item.getName()));
			row.createCell(1).setCellValue(ExcelSanitizer.sanitize(item.getBranchName()));
			row.createCell(2).setCellValue(item.getParticipants());
			row.createCell(3).setCellValue(item.getMaxParticipants() != null ? item.getMaxParticipants() : 0);
			row.createCell(4).setCellValue(item.getFillRatePct() != null ? item.getFillRatePct() : 0);
			row.createCell(5).setCellValue(item.getRevenue() != null ? item.getRevenue().doubleValue() : 0);
			row.createCell(6).setCellValue(item.getPrizePool() != null ? item.getPrizePool().doubleValue() : 0);
			row.createCell(7).setCellValue(item.getNetProfit() != null ? item.getNetProfit().doubleValue() : 0);
			row.createCell(8).setCellValue(item.getStatusLabel());
			row.createCell(9).setCellValue(item.getCompletionRatePct() != null ? item.getCompletionRatePct() : 0);
		}
		for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 22 * 256);
	}

	private void writeTransactionSheet(XSSFWorkbook workbook, CellStyle headerStyle, String sheetName, TransactionStatsResponse t) {
		Sheet sheet = workbook.createSheet(sheetName);
		int r = 0;

		r = kv(sheet, r, "Tổng số giao dịch", t.getTotalTransactions());
		r = kv(sheet, r, "Thành công", t.getSuccessCount());
		r = kv(sheet, r, "Chờ xử lý", t.getPendingCount());
		r = kv(sheet, r, "Thất bại", t.getFailedCount());
		r = kv(sheet, r, "Đã hủy", t.getCancelledCount());
		r = kv(sheet, r, "Tỷ lệ thành công (%)", t.getSuccessRatePct());
		r = kv(sheet, r, "Tổng giá trị thành công (VNĐ)", t.getTotalAmount());
		r = kv(sheet, r, "Giá trị TB / giao dịch (VNĐ)", t.getAvgTransactionValue());
		r = kv(sheet, r, "Thời gian xử lý TB (phút)", t.getAvgConversionMinutes());
		r++;

		Row methodHeader = sheet.createRow(r++);
		header(methodHeader, 0, "Phương thức", headerStyle);
		header(methodHeader, 1, "Số giao dịch", headerStyle);
		for (StatusCountItem item : t.getByMethod()) {
			Row row = sheet.createRow(r++);
			row.createCell(0).setCellValue(item.getLabel());
			row.createCell(1).setCellValue(item.getCount());
		}
		r++;

		Row trendHeader = sheet.createRow(r++);
		header(trendHeader, 0, "Kỳ", headerStyle);
		header(trendHeader, 1, "Số giao dịch thành công", headerStyle);
		header(trendHeader, 2, "Doanh thu (VNĐ)", headerStyle);
		for (TrendPointResponse p : t.getTrend()) {
			Row row = sheet.createRow(r++);
			row.createCell(0).setCellValue(p.getPeriod());
			row.createCell(1).setCellValue(p.getCount());
			row.createCell(2).setCellValue(p.getAmount() != null ? p.getAmount().doubleValue() : 0);
		}

		sheet.setColumnWidth(0, 32 * 256);
		sheet.setColumnWidth(1, 20 * 256);
		sheet.setColumnWidth(2, 20 * 256);
	}

	private void writeTournamentOverviewSheet(XSSFWorkbook workbook, CellStyle headerStyle, TournamentAnalyticsDetailResponse d) {
		Sheet sheet = workbook.createSheet("Tổng quan");
		int r = 0;

		header(sheet.createRow(r++), 0, d.getName(), headerStyle);
		r++;

		r = kv(sheet, r, "Chi nhánh", d.getBranchName());
		r = kv(sheet, r, "Loại bi", d.getGameTypeLabel());
		r = kv(sheet, r, "Thể thức", d.getFormatLabel());
		r = kv(sheet, r, "Trạng thái", d.getStatusLabel());
		r = kv(sheet, r, "Phí đăng ký (VNĐ)", d.getEntryFee());
		r = kv(sheet, r, "Tiền thưởng (VNĐ)", d.getPrizePool());
		r = kv(sheet, r, "Số VĐV tối đa", d.getMaxParticipants());
		r = kv(sheet, r, "Ngày bắt đầu", d.getStartAt() != null ? DATE_FMT.format(d.getStartAt().atZone(ZONE)) : "—");
		r = kv(sheet, r, "Ngày kết thúc", d.getEndAt() != null ? DATE_FMT.format(d.getEndAt().atZone(ZONE)) : "—");
		r = kv(sheet, r, "Tỷ lệ lấp đầy (%)", d.getFillRatePct());
		r++;

		r = kv(sheet, r, "Tổng doanh thu (VNĐ)", d.getTransactionStats().getTotalAmount());
		r = kv(sheet, r, "Lợi nhuận (VNĐ)", d.getNetProfit());
		r = kv(sheet, r, "Tổng số VĐV đang thi đấu", d.getParticipantStats().getActive());
		r = kv(sheet, r, "Trận đã hoàn thành / tổng số trận", d.getMatchStats().getCompleted() + " / " + d.getMatchStats().getTotal());
		r++;

		if (d.getSocial() != null && d.getSocial().getTotalPosts() > 0) {
			header(sheet.createRow(r++), 0, "Truyền thông Facebook", headerStyle);
			r = kv(sheet, r, "Số bài đăng", d.getSocial().getTotalPosts());
			r = kv(sheet, r, "Lượt thích", d.getSocial().getTotalLikes());
			r = kv(sheet, r, "Bình luận", d.getSocial().getTotalComments());
			r = kv(sheet, r, "Chia sẻ", d.getSocial().getTotalShares());
			kv(sheet, r, "Lượt tiếp cận", d.getSocial().getTotalReach());
		}

		sheet.setColumnWidth(0, 36 * 256);
		sheet.setColumnWidth(1, 24 * 256);
	}

	private void writeStatusCountSheet(XSSFWorkbook workbook, CellStyle headerStyle, String sheetName,
			List<StatusCountItem> byStatus, List<TrendPointResponse> trend) {
		Sheet sheet = workbook.createSheet(sheetName);
		int r = 0;

		Row statusHeader = sheet.createRow(r++);
		header(statusHeader, 0, "Trạng thái", headerStyle);
		header(statusHeader, 1, "Số lượng", headerStyle);
		for (StatusCountItem item : byStatus) {
			Row row = sheet.createRow(r++);
			row.createCell(0).setCellValue(item.getLabel());
			row.createCell(1).setCellValue(item.getCount());
		}

		if (trend != null && !trend.isEmpty()) {
			r++;
			Row trendHeader = sheet.createRow(r++);
			header(trendHeader, 0, "Kỳ", headerStyle);
			header(trendHeader, 1, "Số lượng", headerStyle);
			for (TrendPointResponse p : trend) {
				Row row = sheet.createRow(r++);
				row.createCell(0).setCellValue(p.getPeriod());
				row.createCell(1).setCellValue(p.getCount());
			}
		}

		sheet.setColumnWidth(0, 28 * 256);
		sheet.setColumnWidth(1, 18 * 256);
	}

	private void writePlayersSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<PlayerLeaderboardItem> items) {
		Sheet sheet = workbook.createSheet("Cơ thủ");
		Row head = sheet.createRow(0);
		String[] cols = {"Hạng", "Tên cơ thủ", "Số giải đã đấu", "Số lần vô địch", "Số lần vào top 3",
				"Tổng tiền thưởng (VNĐ)", "Tổng điểm"};
		for (int i = 0; i < cols.length; i++) header(head, i, cols[i], headerStyle);

		int r = 1;
		int rank = 1;
		for (PlayerLeaderboardItem item : items) {
			Row row = sheet.createRow(r++);
			row.createCell(0).setCellValue(rank++);
			row.createCell(1).setCellValue(ExcelSanitizer.sanitize(item.getPlayerName()));
			row.createCell(2).setCellValue(item.getTournamentsPlayed());
			row.createCell(3).setCellValue(item.getChampionCount());
			row.createCell(4).setCellValue(item.getTop3Count());
			row.createCell(5).setCellValue(item.getTotalPrizeAmount() != null ? item.getTotalPrizeAmount().doubleValue() : 0);
			row.createCell(6).setCellValue(item.getTotalPoints());
		}
		for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 20 * 256);
	}
}
