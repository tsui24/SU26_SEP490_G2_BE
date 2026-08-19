package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.MailLayoutSettingsRequest;
import com.capstone.su26_sep490_g2_be.dto.request.MailLayoutTestSendRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — Admin cấu hình header/footer chung cho mọi email hệ thống.
 *
 * <p>{@code POST /test-send} thực sự gửi SMTP thật (EmailService) — L2 chỉ kiểm tra nhánh bị chặn
 * TRƯỚC khi tới bước gửi (thiếu quyền, email sai định dạng) để không phụ thuộc MAIL_USERNAME/
 * MAIL_PASSWORD thật khi chạy trên máy dev/CI.
 */
class AdminMailLayoutSettingsControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void getSettings_asAdmin_ok() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/layout")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL)))
				.andExpect(status().isOk());
	}

	@Test
	void getSettings_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/admin/email/layout")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateSettings_blankHeader_rejected400() throws Exception {
		MailLayoutSettingsRequest req = new MailLayoutSettingsRequest();
		req.setHeaderHtml("");
		req.setFooterHtml("<p>footer</p>");

		mockMvc.perform(put("/api/v1/admin/email/layout")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateSettings_asOwner_rejected403() throws Exception {
		MailLayoutSettingsRequest req = new MailLayoutSettingsRequest();
		req.setHeaderHtml("<p>header</p>");
		req.setFooterHtml("<p>footer</p>");

		mockMvc.perform(put("/api/v1/admin/email/layout")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden());
	}

	@Test
	void sendTest_invalidEmailFormat_rejected400_beforeSendingAnything() throws Exception {
		MailLayoutTestSendRequest req = new MailLayoutTestSendRequest();
		req.setEmail("not-an-email");

		mockMvc.perform(post("/api/v1/admin/email/layout/test-send")
						.header("Authorization", bearerToken(TestAccounts.ADMIN_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sendTest_withoutToken_rejected401() throws Exception {
		MailLayoutTestSendRequest req = new MailLayoutTestSendRequest();
		req.setEmail("someone@gmail.com");

		mockMvc.perform(post("/api/v1/admin/email/layout/test-send")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}
}
