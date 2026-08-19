package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — Thanh toán qua PayOS. GB-15: không có endpoint DELETE payment (đã xác nhận bằng cách không
 * test — controller không khai báo route nào như vậy). Checkout/webhook thật gọi PayOS ra ngoài nên
 * chỉ test nhánh chặn trước khi gọi (auth, ownership) + webhook luôn trả 200 kể cả chữ ký sai
 * (đúng hành vi cố ý — không để lộ thông tin cho kẻ tấn công dò webhook).
 */
class PaymentControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void getMyPayments_asPlayer_ok() throws Exception {
		mockMvc.perform(get("/api/v1/player/payments")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getMyPayments_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/player/payments"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void checkout_unknownRegistration_rejected404() throws Exception {
		mockMvc.perform(post("/api/v1/player/registrations/{id}/checkout", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void checkout_asOwner_rejected403() throws Exception {
		// GB-04: /player/** chỉ role PLAYER — Owner (dù trả tiền cho ai đó) cũng không gọi được endpoint này.
		mockMvc.perform(post("/api/v1/player/registrations/{id}/checkout", 1L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void confirmReturn_unknownOrderCode_rejected() throws Exception {
		mockMvc.perform(post("/api/v1/player/payments/confirm-return")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL))
						.param("orderCode", "999999999"))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void webhook_invalidSignature_stillReturns200WithErrorZero() throws Exception {
		// PayOSService.verifyWebhookSignature() sẽ trả false với payload rác — vẫn 200 để không lộ
		// tín hiệu phân biệt request hợp lệ/không hợp lệ cho bên ngoài dò webhook.
		String rawBody = "{\"code\":\"00\",\"data\":{\"orderCode\":123456}}";

		mockMvc.perform(post("/api/v1/payments/payos/webhook")
						.contentType(MediaType.APPLICATION_JSON)
						.content(rawBody))
				.andExpect(status().isOk())
				.andExpect(content().string("{\"error\":0}"));
	}

	@Test
	void getPaymentsByRegistration_unknownId_asManager_rejected() throws Exception {
		mockMvc.perform(get("/api/v1/manager/registrations/{id}/payments", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void getPaymentsByRegistration_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/registrations/{id}/payments", 1L)
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}
}
