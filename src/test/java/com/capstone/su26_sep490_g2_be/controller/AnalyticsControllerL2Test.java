package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.AnalyticsQueryRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SavedViewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — Analytics là controller lớn nhất hệ thống (~35 endpoint Owner/Manager song song cùng khuôn
 * mẫu). Test đại diện theo nhóm chức năng (overview/revenue/query/saved-view/export) thay vì lặp
 * lại 1 khuôn mẫu 35 lần — trọng tâm là role-guard GB-04 và scope Owner-vs-Manager của GB-05
 * (owner.getId() dùng làm khóa lọc dữ liệu, Manager không tự chọn được owner khác).
 */
class AnalyticsControllerL2Test extends AbstractControllerIntegrationTest {

	// ── Owner ────────────────────────────────────────────────────────────

	@Test
	void ownerOverview_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/analytics/overview")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void ownerOverview_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/analytics/overview")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void ownerOverview_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/owner/analytics/overview"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerRevenue_withBranchFilter_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/analytics/revenue")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.param("granularity", "month"))
				.andExpect(status().isOk());
	}

	@Test
	void ownerTournaments_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/analytics/tournaments")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void ownerPlayers_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/analytics/players")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.param("sortBy", "PRIZE"))
				.andExpect(status().isOk());
	}

	@Test
	void ownerQuery_validDimensionAndMetric_ok() throws Exception {
		AnalyticsQueryRequest req = new AnalyticsQueryRequest();
		req.setDimensions(List.of("BRANCH"));
		req.setMetrics(List.of("REVENUE"));

		mockMvc.perform(post("/api/v1/owner/analytics/query")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void ownerQuery_emptyDimensionsAndMetrics_rejected400() throws Exception {
		// Không @NotEmpty ở DTO — validate thủ công trong AnalyticsServiceImpl#runQuery.
		AnalyticsQueryRequest req = new AnalyticsQueryRequest();

		mockMvc.perform(post("/api/v1/owner/analytics/query")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerSavedView_createListDelete_fullLifecycle() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);

		SavedViewRequest req = new SavedViewRequest();
		req.setName("L2 Test View " + System.nanoTime());
		AnalyticsQueryRequest config = new AnalyticsQueryRequest();
		config.setDimensions(List.of("BRANCH"));
		config.setMetrics(List.of("REVENUE"));
		req.setConfig(config);

		String body = mockMvc.perform(post("/api/v1/owner/analytics/views")
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Long viewId = objectMapper.readTree(body).path("data").path("id").asLong();

		mockMvc.perform(get("/api/v1/owner/analytics/views")
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());

		mockMvc.perform(delete("/api/v1/owner/analytics/views/{id}", viewId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());
	}

	@Test
	void ownerSavedView_blankName_rejected400() throws Exception {
		SavedViewRequest req = new SavedViewRequest();
		req.setName("");
		req.setConfig(new AnalyticsQueryRequest());

		mockMvc.perform(post("/api/v1/owner/analytics/views")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerExport_returnsExcelAttachment() throws Exception {
		mockMvc.perform(get("/api/v1/owner/analytics/export")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("attachment")));
	}

	// ── Manager ─────────────────────────────────────────────────────────

	@Test
	void managerOverview_asManager_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/analytics/overview")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void managerOverview_asOwner_rejected403() throws Exception {
		// GB-04: /api/v1/manager/** chỉ role MANAGER, kể cả Owner của chính chuỗi đó cũng không gọi được.
		mockMvc.perform(get("/api/v1/manager/analytics/overview")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void managerTransactionsList_scopedToOwnBranches_ok() throws Exception {
		// GB-05: Manager chỉ thấy dữ liệu (các) chi nhánh được cấp quyền — branchAccessService tự áp,
		// không cần Manager truyền branchId. Test chỉ xác nhận endpoint hoạt động cho cả 2 manager của
		// 2 chi nhánh khác nhau, không lỗi cross-owner.
		mockMvc.perform(get("/api/v1/manager/analytics/transactions/list")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/manager/analytics/transactions/list")
						.header("Authorization", bearerToken(TestAccounts.MANAGER2_EMAIL)))
				.andExpect(status().isOk());
	}
}
