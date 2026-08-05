package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.response.AnalyticsOverviewResponse;
import com.capstone.su26_sep490_g2_be.dto.response.LabeledAmountItem;
import com.capstone.su26_sep490_g2_be.dto.response.MatchStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportItem;
import com.capstone.su26_sep490_g2_be.dto.response.MonthlyReportResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PlayerLeaderboardItem;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RevenueBreakdownResponse;
import com.capstone.su26_sep490_g2_be.dto.response.SocialEngagementResponse;
import com.capstone.su26_sep490_g2_be.dto.response.StatusCountItem;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentAnalyticsDetailResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentPerformanceItem;
import com.capstone.su26_sep490_g2_be.dto.response.TransactionStatsResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TrendPointResponse;
import com.capstone.su26_sep490_g2_be.service.AnalyticsService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link AnalyticsExcelServiceImpl}.
 *
 * <p>Mirrors the <b>AnalyticsExcelService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-51 (revenue and transaction reports, the export button).
 *
 * <p>The class does no arithmetic of its own: it asks {@link AnalyticsService} for the figures and
 * lays them out. What can go wrong is therefore layout, not maths — a missing sheet, a number
 * written as text so Excel cannot total it, a null rendered as 0 instead of a dash, or a
 * player-supplied name starting with {@code =} that Excel would execute as a formula. Every test
 * writes a real workbook and reads it back.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · AnalyticsExcelService — UC-51")
class AnalyticsExcelServiceImplTest {

	@Mock AnalyticsService analyticsService;

	@InjectMocks AnalyticsExcelServiceImpl service;

	private static final Long OWNER_ID = 7L;
	private static final List<Long> BRANCH_IDS = List.of(1L, 2L);
	private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant TO = Instant.parse("2026-03-31T00:00:00Z");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private AnalyticsOverviewResponse overview;
	private RevenueBreakdownResponse revenue;
	private List<TournamentPerformanceItem> tournaments;
	private List<PlayerLeaderboardItem> players;
	private SocialEngagementResponse social;
	private TransactionStatsResponse transactions;

	@BeforeEach
	void resetFixtures() {
		overview = fullOverview();
		revenue = fullRevenue();
		tournaments = new ArrayList<>(List.of(performance("Giải mùa xuân", "Chi nhánh Quận 1")));
		players = new ArrayList<>(List.of(leaderboardEntry("Nguyễn Văn A", 1200),
				leaderboardEntry("Trần Thị B", 900)));
		social = fullSocial(12);
		transactions = fullTransactions();
	}

	// ─────────────────────────── fixtures ───────────────────────────

	private static AnalyticsOverviewResponse fullOverview() {
		return AnalyticsOverviewResponse.builder()
				.totalRevenue(new BigDecimal("125000000"))
				.revenuePrevPeriod(new BigDecimal("100000000"))
				.revenueGrowthPct(25.0)
				.totalTournaments(8)
				.avgFillRatePct(78.5)
				.totalUniquePlayers(146)
				.topTournamentName("Giải mùa xuân")
				.topTournamentRevenue(new BigDecimal("45000000"))
				.topBranchName("Chi nhánh Quận 1")
				.branchCount(2)
				.build();
	}

	private static RevenueBreakdownResponse fullRevenue() {
		return RevenueBreakdownResponse.builder()
				.trend(List.of(trendPoint("2026-01", 40, new BigDecimal("40000000")),
						trendPoint("2026-02", 35, null)))
				.byBranch(List.of(labeledAmount("Chi nhánh Quận 1", 60, new BigDecimal("75000000"))))
				.byTournament(List.of(labeledAmount("Giải mùa xuân", 32, new BigDecimal("45000000"))))
				.byPaymentMethod(List.of(statusCount("PAYOS", "Chuyển khoản", 55),
						statusCount("CASH", "Tiền mặt", 20)))
				.build();
	}

	private static TransactionStatsResponse fullTransactions() {
		return TransactionStatsResponse.builder()
				.totalTransactions(80).successCount(75).pendingCount(2).failedCount(2).cancelledCount(1)
				.successRatePct(93.75)
				.totalAmount(new BigDecimal("125000000"))
				.avgTransactionValue(new BigDecimal("1666666.67"))
				.avgConversionMinutes(4.5)
				.byStatus(List.of(statusCount("SUCCESS", "Thành công", 75)))
				.byMethod(List.of(statusCount("PAYOS", "Chuyển khoản", 55)))
				.trend(List.of(trendPoint("2026-01", 40, new BigDecimal("40000000"))))
				.build();
	}

	private static SocialEngagementResponse fullSocial(long posts) {
		return SocialEngagementResponse.builder()
				.totalPosts(posts).totalLikes(340).totalComments(58).totalShares(21).totalReach(12500)
				.topPostTournamentName("Giải mùa xuân").topPostReach(4200)
				.build();
	}

	private static TrendPointResponse trendPoint(String period, long count, BigDecimal amount) {
		return TrendPointResponse.builder().period(period).count(count).amount(amount).build();
	}

	private static LabeledAmountItem labeledAmount(String label, long count, BigDecimal amount) {
		return LabeledAmountItem.builder().id(1L).label(label).count(count).amount(amount).build();
	}

	private static StatusCountItem statusCount(String status, String label, long count) {
		return StatusCountItem.builder().status(status).label(label).count(count).build();
	}

	private static TournamentPerformanceItem performance(String name, String branchName) {
		return TournamentPerformanceItem.builder()
				.id(1L).name(name).branchName(branchName)
				.participants(24).maxParticipants(32).fillRatePct(75.0)
				.revenue(new BigDecimal("45000000")).prizePool(new BigDecimal("20000000"))
				.netProfit(new BigDecimal("25000000"))
				.status("COMPLETED").statusLabel("Đã kết thúc").completionRatePct(100.0)
				.build();
	}

	private static PlayerLeaderboardItem leaderboardEntry(String name, long points) {
		return PlayerLeaderboardItem.builder()
				.userId(1L).playerName(name).tournamentsPlayed(6).championCount(2).top3Count(4)
				.totalPrizeAmount(new BigDecimal("18000000")).totalPoints(points)
				.build();
	}

	private static TournamentAnalyticsDetailResponse detail(SocialEngagementResponse social,
	                                                        Instant startAt, Instant endAt) {
		return TournamentAnalyticsDetailResponse.builder()
				.id(1L).name("Giải mùa xuân").branchName("Chi nhánh Quận 1")
				.gameTypeLabel("9-Ball").formatLabel("Loại trực tiếp").statusLabel("Đã kết thúc")
				.entryFee(new BigDecimal("300000")).prizePool(new BigDecimal("20000000"))
				.netProfit(new BigDecimal("25000000"))
				.maxParticipants(32).startAt(startAt).endAt(endAt).fillRatePct(75.0)
				.transactionStats(fullTransactions())
				.registrationStats(RegistrationStatsResponse.builder()
						.total(30).pending(2).approved(24).rejected(3).cancelled(1)
						.byStatus(List.of(statusCount("APPROVED", "Đã duyệt", 24)))
						.monthlyTrend(List.of(trendPoint("2026-01", 18, null)))
						.build())
				.participantStats(ParticipantStatsResponse.builder()
						.total(24).active(24).withdrawn(0)
						.byStatus(List.of(statusCount("ACTIVE", "Đang thi đấu", 24)))
						.build())
				.matchStats(MatchStatsResponse.builder()
						.total(31).completed(31).inProgress(0).pending(0).completionRate(100.0)
						.byStatus(List.of(statusCount("COMPLETED", "Đã kết thúc", 31)))
						.build())
				.social(social)
				.build();
	}

	// ─────────────────────────── stubbing and workbook helpers ───────────────────────────

	private void stubReportSources() {
		when(analyticsService.buildOverview(any(), any(), any(), any())).thenReturn(overview);
		when(analyticsService.buildRevenueBreakdown(any(), any(), any(), anyString(), any())).thenReturn(revenue);
		when(analyticsService.buildTournamentPerformance(any(), any(), any(), any())).thenReturn(tournaments);
		when(analyticsService.buildPlayerLeaderboard(any(), any(), any(), any())).thenReturn(players);
		when(analyticsService.buildSocialEngagement(any(), any(), any(), any())).thenReturn(social);
		when(analyticsService.buildTransactionStats(any(), any(), any(), anyString(), any())).thenReturn(transactions);
	}

	private static XSSFWorkbook open(byte[] bytes) throws IOException {
		return new XSSFWorkbook(new ByteArrayInputStream(bytes));
	}

	private static List<String> sheetNames(XSSFWorkbook workbook) {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
			names.add(workbook.getSheetName(i));
		}
		return names;
	}

	/** The row whose first cell reads exactly {@code label}, or null when there is none. */
	private static Row rowLabelled(Sheet sheet, String label) {
		for (Row row : sheet) {
			Cell first = row.getCell(0);
			if (first != null && first.getCellType() == CellType.STRING
					&& label.equals(first.getStringCellValue())) {
				return row;
			}
		}
		return null;
	}

	private static Cell valueOf(Sheet sheet, String label) {
		Row row = rowLabelled(sheet, label);
		assertNotNull(row, "no row labelled " + label);
		return row.getCell(1);
	}

	private static String text(Sheet sheet, int rowIdx, int col) {
		Row row = sheet.getRow(rowIdx);
		if (row == null || row.getCell(col) == null) {
			return null;
		}
		return row.getCell(col).getStringCellValue();
	}

	private static boolean hasRowStartingWith(Sheet sheet, String prefix) {
		for (Row row : sheet) {
			Cell first = row.getCell(0);
			if (first != null && first.getCellType() == CellType.STRING
					&& first.getStringCellValue().startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	// ══════════════════════════ buildReport — UC-51 ══════════════════════════

	@Test
	@DisplayName("TC-001 · The owner report carries one sheet per analytics section")
	void TC001_buildReport_sheetLayout() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			assertEquals(List.of("Tổng quan", "Doanh thu", "Giao dịch", "Giải đấu", "Cơ thủ"),
					sheetNames(workbook));
		}
		// Both breakdowns are asked for at monthly granularity, matching the range the screen shows
		verify(analyticsService).buildRevenueBreakdown(eq(OWNER_ID), eq(FROM), eq(TO), eq("month"), eq(BRANCH_IDS));
		verify(analyticsService).buildTransactionStats(eq(OWNER_ID), eq(FROM), eq(TO), eq("month"), eq(BRANCH_IDS));
	}

	@Test
	@DisplayName("TC-002 · A bounded range prints the period under the report title")
	void TC002_buildReport_periodLine() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Tổng quan");
			String expected = "Khoảng thời gian: "
					+ DATE_FMT.format(FROM.atZone(ZoneId.systemDefault())) + " - "
					+ DATE_FMT.format(TO.atZone(ZoneId.systemDefault()));
			assertEquals("Báo cáo thống kê & phân tích", text(sheet, 0, 0));
			assertEquals(expected, text(sheet, 1, 0));
		}
	}

	@Test
	@DisplayName("TC-003 · An open-ended range leaves the period line out")
	void TC003_buildReport_noPeriodLine() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, null, TO, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Tổng quan");
			// Printing "null - 31/03/2026" would be worse than printing nothing
			assertFalse(hasRowStartingWith(sheet, "Khoảng thời gian"));
			assertEquals("Báo cáo thống kê & phân tích", text(sheet, 0, 0));
		}
	}

	@Test
	@DisplayName("TC-004 · Monetary figures are written as numbers, not as text")
	void TC004_buildReport_amountsAreNumeric() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Cell total = valueOf(workbook.getSheet("Tổng quan"), "Tổng doanh thu (VNĐ)");
			// Written as text the Owner could not sum or chart the column in Excel
			assertEquals(CellType.NUMERIC, total.getCellType());
			assertEquals(125_000_000d, total.getNumericCellValue());
			assertEquals(146d, valueOf(workbook.getSheet("Tổng quan"), "Tổng người chơi (duy nhất)")
					.getNumericCellValue());
		}
	}

	@Test
	@DisplayName("TC-005 · A missing figure prints an em dash rather than a zero")
	void TC005_buildReport_nullRendersAsDash() throws IOException {
		overview = AnalyticsOverviewResponse.builder()
				.totalRevenue(null).revenuePrevPeriod(null).revenueGrowthPct(null)
				.totalTournaments(0).avgFillRatePct(null).totalUniquePlayers(0)
				.topTournamentName(null).topTournamentRevenue(null).topBranchName(null)
				.build();
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Cell topTournament = valueOf(workbook.getSheet("Tổng quan"), "Giải đấu doanh thu cao nhất");
			// A zero would read as "no revenue"; the dash says "nothing to report yet"
			assertEquals(CellType.STRING, topTournament.getCellType());
			assertEquals("—", topTournament.getStringCellValue());
			assertEquals("—", valueOf(workbook.getSheet("Tổng quan"), "Tăng trưởng doanh thu (%)")
					.getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-006 · A tournament name that opens with an equals sign is neutralised")
	void TC006_buildReport_formulaInjectionEscaped() throws IOException {
		tournaments = List.of(performance("=HYPERLINK(\"http://evil\",\"click\")", "@Chi nhánh"));
		overview = AnalyticsOverviewResponse.builder()
				.totalRevenue(BigDecimal.ZERO).totalTournaments(1)
				.topTournamentName("+Giải bơm giá").topBranchName("-Chi nhánh")
				.build();
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Row row = workbook.getSheet("Giải đấu").getRow(1);
			// Excel executes a cell that opens with = + - or @; the leading quote makes it inert
			assertTrue(row.getCell(0).getStringCellValue().startsWith("'="));
			assertTrue(row.getCell(1).getStringCellValue().startsWith("'@"));
			assertTrue(valueOf(workbook.getSheet("Tổng quan"), "Giải đấu doanh thu cao nhất")
					.getStringCellValue().startsWith("'+"));
			assertTrue(valueOf(workbook.getSheet("Tổng quan"), "Chi nhánh doanh thu cao nhất")
					.getStringCellValue().startsWith("'-"));
		}
	}

	@Test
	@DisplayName("TC-007 · The revenue sheet stacks the trend and the three breakdowns")
	void TC007_buildReport_revenueSheetSections() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Doanh thu");
			assertEquals("Xu hướng doanh thu theo thời gian", text(sheet, 0, 0));
			assertNotNull(rowLabelled(sheet, "Doanh thu theo chi nhánh"));
			assertNotNull(rowLabelled(sheet, "Top 10 giải đấu theo doanh thu"));
			assertNotNull(rowLabelled(sheet, "Doanh thu theo phương thức thanh toán"));
			// Each breakdown keeps its own header row, so the sections stay readable when stacked
			assertNotNull(rowLabelled(sheet, "Chi nhánh Quận 1"));
			assertEquals(55d, valueOf(sheet, "Chuyển khoản").getNumericCellValue());
		}
	}

	@Test
	@DisplayName("TC-008 · A trend period with no revenue is written as zero")
	void TC008_buildReport_nullTrendAmountIsZero() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Doanh thu");
			Row february = rowLabelled(sheet, "2026-02");
			assertNotNull(february);
			// Inside a numeric trend column a dash would break the chart, so zero is the right blank
			assertEquals(0d, february.getCell(2).getNumericCellValue());
			assertEquals(35d, february.getCell(1).getNumericCellValue());
		}
	}

	@Test
	@DisplayName("TC-009 · The tournament sheet writes one row under a ten-column header")
	void TC009_buildReport_tournamentsSheet() throws IOException {
		tournaments = List.of(TournamentPerformanceItem.builder()
				.id(1L).name("Giải mở rộng").branchName("Chi nhánh Quận 3")
				.participants(12).maxParticipants(null).fillRatePct(null)
				.revenue(null).prizePool(null).netProfit(null)
				.statusLabel("Đang diễn ra").completionRatePct(null)
				.build());
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Giải đấu");
			assertEquals("Tên giải đấu", text(sheet, 0, 0));
			assertEquals("Tỷ lệ hoàn thành (%)", text(sheet, 0, 9));
			assertEquals(1, sheet.getLastRowNum());
			Row row = sheet.getRow(1);
			assertEquals(12d, row.getCell(2).getNumericCellValue());
			// An uncapped tournament has no fill rate, and the numeric columns fall back to zero
			assertEquals(0d, row.getCell(3).getNumericCellValue());
			assertEquals(0d, row.getCell(5).getNumericCellValue());
			assertEquals("Đang diễn ra", row.getCell(8).getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-010 · The leaderboard is numbered from one in the order it arrives")
	void TC010_buildReport_playersSheetRanked() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Cơ thủ");
			assertEquals("Hạng", text(sheet, 0, 0));
			assertEquals(1d, sheet.getRow(1).getCell(0).getNumericCellValue());
			assertEquals("Nguyễn Văn A", sheet.getRow(1).getCell(1).getStringCellValue());
			// The rank is positional — the service has already sorted the list
			assertEquals(2d, sheet.getRow(2).getCell(0).getNumericCellValue());
			assertEquals(900d, sheet.getRow(2).getCell(6).getNumericCellValue());
		}
	}

	@Test
	@DisplayName("TC-011 · An owner with no activity still gets a complete workbook")
	void TC011_buildReport_emptyAnalytics() throws IOException {
		overview = AnalyticsOverviewResponse.builder().build();
		revenue = RevenueBreakdownResponse.builder()
				.trend(List.of()).byBranch(List.of()).byTournament(List.of()).byPaymentMethod(List.of())
				.build();
		tournaments = List.of();
		players = List.of();
		social = SocialEngagementResponse.builder().build();
		transactions = TransactionStatsResponse.builder()
				.byStatus(List.of()).byMethod(List.of()).trend(List.of())
				.build();
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			// Empty is not an error — the Owner gets the headers and empty tables, not a failure
			assertEquals(5, workbook.getNumberOfSheets());
			assertEquals(0, workbook.getSheet("Giải đấu").getLastRowNum());
			assertEquals(0, workbook.getSheet("Cơ thủ").getLastRowNum());
			assertEquals("—", valueOf(workbook.getSheet("Giao dịch"), "Tổng giá trị thành công (VNĐ)")
					.getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-012 · The Facebook block is appended to the overview sheet")
	void TC012_buildReport_socialBlock() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, TO, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Tổng quan");
			assertNotNull(rowLabelled(sheet, "Hiệu quả truyền thông Facebook"));
			assertEquals(12d, valueOf(sheet, "Tổng bài đăng").getNumericCellValue());
			assertEquals(12500d, valueOf(sheet, "Tổng lượt tiếp cận").getNumericCellValue());
			assertEquals("Giải mùa xuân",
					valueOf(sheet, "Giải đấu có bài đăng nổi bật nhất").getStringCellValue());
		}
	}

	// ══════════════════════════ buildTournamentReport — UC-51 ══════════════════════════

	@Test
	@DisplayName("TC-013 · The per-tournament report carries four sheets")
	void TC013_buildTournamentReport_sheetLayout() throws IOException {
		when(analyticsService.buildTournamentDetail(any(), any(), any()))
				.thenReturn(detail(fullSocial(4), FROM, TO));

		try (XSSFWorkbook workbook = open(service.buildTournamentReport(OWNER_ID, 1L, BRANCH_IDS))) {
			assertEquals(List.of("Tổng quan", "Đăng ký", "Trận đấu", "Giao dịch"), sheetNames(workbook));
			assertEquals("Giải mùa xuân", text(workbook.getSheet("Tổng quan"), 0, 0));
		}
		verify(analyticsService).buildTournamentDetail(OWNER_ID, 1L, BRANCH_IDS);
	}

	@Test
	@DisplayName("TC-014 · The registration sheet appends its monthly trend, the match sheet does not")
	void TC014_buildTournamentReport_trendOnlyWhereSupplied() throws IOException {
		when(analyticsService.buildTournamentDetail(any(), any(), any()))
				.thenReturn(detail(fullSocial(4), FROM, TO));

		try (XSSFWorkbook workbook = open(service.buildTournamentReport(OWNER_ID, 1L, BRANCH_IDS))) {
			Sheet registrations = workbook.getSheet("Đăng ký");
			assertEquals(24d, valueOf(registrations, "Đã duyệt").getNumericCellValue());
			assertNotNull(rowLabelled(registrations, "2026-01"));
			// Matches carry no monthly trend, so that block is skipped rather than left empty
			Sheet matches = workbook.getSheet("Trận đấu");
			assertEquals(31d, valueOf(matches, "Đã kết thúc").getNumericCellValue());
			assertEquals(1, matches.getLastRowNum());
		}
	}

	@Test
	@DisplayName("TC-015 · A tournament with at least one post gets the Facebook block")
	void TC015_buildTournamentReport_socialBlockPresent() throws IOException {
		when(analyticsService.buildTournamentDetail(any(), any(), any()))
				.thenReturn(detail(fullSocial(4), FROM, TO));

		try (XSSFWorkbook workbook = open(service.buildTournamentReport(OWNER_ID, 1L, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Tổng quan");
			assertNotNull(rowLabelled(sheet, "Truyền thông Facebook"));
			assertEquals(4d, valueOf(sheet, "Số bài đăng").getNumericCellValue());
			assertEquals("31 / 31",
					valueOf(sheet, "Trận đã hoàn thành / tổng số trận").getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-016 · A tournament that was never posted about skips the Facebook block")
	void TC016_buildTournamentReport_noSocialBlock() throws IOException {
		when(analyticsService.buildTournamentDetail(any(), any(), any()))
				.thenReturn(detail(fullSocial(0), FROM, TO));

		try (XSSFWorkbook workbook = open(service.buildTournamentReport(OWNER_ID, 1L, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Tổng quan");
			// A block of five zeroes would read as a failed campaign rather than no campaign
			assertNull(rowLabelled(sheet, "Truyền thông Facebook"));
			assertNull(rowLabelled(sheet, "Số bài đăng"));
		}
	}

	@Test
	@DisplayName("TC-017 · A tournament with no social data at all skips the block too")
	void TC017_buildTournamentReport_nullSocial() throws IOException {
		when(analyticsService.buildTournamentDetail(any(), any(), any()))
				.thenReturn(detail(null, FROM, TO));

		try (XSSFWorkbook workbook = open(service.buildTournamentReport(OWNER_ID, 1L, BRANCH_IDS))) {
			// The null check is the short-circuiting half of the compound condition
			assertNull(rowLabelled(workbook.getSheet("Tổng quan"), "Truyền thông Facebook"));
		}
	}

	@Test
	@DisplayName("TC-018 · An undated tournament prints a dash for both dates")
	void TC018_buildTournamentReport_nullDates() throws IOException {
		when(analyticsService.buildTournamentDetail(any(), any(), any()))
				.thenReturn(detail(fullSocial(4), null, null));

		try (XSSFWorkbook workbook = open(service.buildTournamentReport(OWNER_ID, 1L, BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Tổng quan");
			assertEquals("—", valueOf(sheet, "Ngày bắt đầu").getStringCellValue());
			assertEquals("—", valueOf(sheet, "Ngày kết thúc").getStringCellValue());
		}
	}

	// ══════════════════════════ buildMonthlyReport — UC-51 ══════════════════════════

	@Test
	@DisplayName("TC-019 · The monthly report ends with a totals row")
	void TC019_buildMonthlyReport_totalsRow() throws IOException {
		when(analyticsService.buildMonthlyReport(any(), any(), any(), any())).thenReturn(
				MonthlyReportResponse.builder()
						.totalRevenue(new BigDecimal("75000000"))
						.totalTransactions(70).totalNewTournaments(3).totalNewRegistrations(120)
						.months(List.of(
								monthlyItem("01/2026", new BigDecimal("40000000"), 40, 2, 70),
								monthlyItem("02/2026", new BigDecimal("35000000"), 30, 1, 50)))
						.build());

		try (XSSFWorkbook workbook = open(service.buildMonthlyReport(
				OWNER_ID, YearMonth.of(2026, 1), YearMonth.of(2026, 2), BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Doanh thu theo tháng");
			assertEquals("Báo cáo doanh thu theo tháng — 2026-01 - 2026-02", text(sheet, 0, 0));
			assertEquals("Tháng", text(sheet, 2, 0));
			assertEquals(40_000_000d, sheet.getRow(3).getCell(1).getNumericCellValue());
			Row totals = rowLabelled(sheet, "Tổng cộng");
			assertNotNull(totals);
			// The totals row is written through the header helper, so it is text and bold
			assertEquals("7.5E7", totals.getCell(1).getStringCellValue());
			assertEquals("70", totals.getCell(2).getStringCellValue());
			assertEquals("120", totals.getCell(4).getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-020 · A month with no revenue counts as zero, not as a blank")
	void TC020_buildMonthlyReport_nullRevenue() throws IOException {
		when(analyticsService.buildMonthlyReport(any(), any(), any(), any())).thenReturn(
				MonthlyReportResponse.builder()
						.totalRevenue(null).totalTransactions(0).totalNewTournaments(0).totalNewRegistrations(0)
						.months(List.of(monthlyItem("01/2026", null, 0, 0, 0)))
						.build());

		try (XSSFWorkbook workbook = open(service.buildMonthlyReport(
				OWNER_ID, YearMonth.of(2026, 1), YearMonth.of(2026, 1), BRANCH_IDS))) {
			Sheet sheet = workbook.getSheet("Doanh thu theo tháng");
			assertEquals(0d, sheet.getRow(3).getCell(1).getNumericCellValue());
			assertEquals("0", rowLabelled(sheet, "Tổng cộng").getCell(1).getStringCellValue());
		}
	}

	@Test
	@DisplayName("TC-021 · A one-sided month range still prints a title")
	void TC021_buildMonthlyReport_openEndedRange() throws IOException {
		when(analyticsService.buildMonthlyReport(any(), any(), any(), any())).thenReturn(
				MonthlyReportResponse.builder()
						.totalRevenue(BigDecimal.ZERO).months(List.of())
						.build());

		try (XSSFWorkbook workbook = open(service.buildMonthlyReport(
				OWNER_ID, YearMonth.of(2026, 1), null, BRANCH_IDS))) {
			// The missing bound is spelled out rather than printed as "null"
			assertEquals("Báo cáo doanh thu theo tháng — 2026-01 - ?",
					text(workbook.getSheet("Doanh thu theo tháng"), 0, 0));
		}
	}

	// ══════════════════ the remaining half-open conditions ══════════════════

	@Test
	@DisplayName("TC-022 · An empty registration trend adds no trend block either")
	void TC022_buildTournamentReport_emptyTrendOmitted() throws IOException {
		TournamentAnalyticsDetailResponse base = detail(fullSocial(4), FROM, TO);
		when(analyticsService.buildTournamentDetail(any(), any(), any())).thenReturn(
				TournamentAnalyticsDetailResponse.builder()
						.id(base.getId()).name(base.getName()).branchName(base.getBranchName())
						.statusLabel(base.getStatusLabel()).maxParticipants(base.getMaxParticipants())
						.startAt(FROM).endAt(TO)
						.transactionStats(fullTransactions())
						.registrationStats(RegistrationStatsResponse.builder()
								.total(30).approved(24)
								.byStatus(List.of(statusCount("APPROVED", "Đã duyệt", 24)))
								.monthlyTrend(List.of())
								.build())
						.participantStats(base.getParticipantStats())
						.matchStats(base.getMatchStats())
						.social(fullSocial(4))
						.build());

		try (XSSFWorkbook workbook = open(service.buildTournamentReport(OWNER_ID, 1L, BRANCH_IDS))) {
			Sheet registrations = workbook.getSheet("Đăng ký");
			// An empty list and a null list say the same thing to the reader: no chart to draw
			assertEquals(1, registrations.getLastRowNum());
			assertEquals(24d, valueOf(registrations, "Đã duyệt").getNumericCellValue());
		}
	}

	@Test
	@DisplayName("TC-023 · A range with a start but no end leaves the period line out too")
	void TC023_buildReport_missingUpperBound() throws IOException {
		stubReportSources();

		try (XSSFWorkbook workbook = open(service.buildReport(OWNER_ID, FROM, null, BRANCH_IDS))) {
			// Both halves of the condition have to hold, so a half-open range prints nothing
			assertFalse(hasRowStartingWith(workbook.getSheet("Tổng quan"), "Khoảng thời gian"));
		}
	}

	private static MonthlyReportItem monthlyItem(String label, BigDecimal revenue,
	                                             long transactions, long tournaments, long registrations) {
		return MonthlyReportItem.builder()
				.year(2026).month(1).monthLabel(label).revenue(revenue)
				.transactionCount(transactions).newTournaments(tournaments).newRegistrations(registrations)
				.build();
	}
}
