package com.capstone.su26_sep490_g2_be.service;

import java.io.IOException;
import java.time.Instant;
import java.time.YearMonth;

public interface AnalyticsExcelService {

	/** Gộp Tổng quan/Doanh thu/Giải đấu/Cơ thủ vào 1 workbook 4 sheet, dùng chung dữ liệu với các API on-screen. */
	byte[] buildReport(Long ownerId, Instant from, Instant to) throws IOException;

	/** Báo cáo chi tiết riêng cho 1 giải đấu — dùng chung dữ liệu với API drill-down on-screen. */
	byte[] buildTournamentReport(Long ownerId, Long tournamentId) throws IOException;

	/** Báo cáo doanh thu theo tháng trong khoảng [from,to] tùy chọn, dùng chung dữ liệu với API monthly-report. */
	byte[] buildMonthlyReport(Long ownerId, YearMonth from, YearMonth to) throws IOException;
}
