package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Admin xem nhật ký gửi email toàn hệ thống (nguồn dữ liệu cho NotificationController của user). */
class AdminEmailLogControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void search_asAdmin_noFilters_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/logs")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void search_withDateRange_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/logs")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.param("fromDate", "2026-01-01")
						.param("toDate", "2026-12-31"))
				.andExpect(status().isOk());
	}

	@Test
	void search_malformedDate_doesNotCrashAsSuccess() throws Exception {
		// fromDate được parse thủ công bằng LocalDate.parse (AdminEmailLogController#parseFrom), không
		// qua Bean Validation — chuỗi sai định dạng ném DateTimeParseException, rơi vào handler chung
		// (GlobalExceptionHandler#handleUnexpected) nên trả 500 chứ KHÔNG phải 400. Ghi lại đúng hành vi
		// thật hiện tại; nếu sau này thêm validate riêng cho tham số ngày thì cập nhật lại thành 400.
		mockMvc.perform(get("/api/v1/admin/email/logs")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.param("fromDate", "not-a-date"))
				.andExpect(status().is5xxServerError());
	}

	@Test
	void search_asOwner_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/logs")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void getDetail_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/logs/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void getDetail_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/logs/{id}", 1L))
				.andExpect(status().isUnauthorized());
	}
}
