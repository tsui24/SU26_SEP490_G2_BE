package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — GB-05: Manager chỉ xem được (các) chi nhánh mình được BranchManager gán, không xem được
 * chi nhánh của Manager khác trong cùng chuỗi (DataInitializer: manager1 -> branch1, manager2 -> branch2).
 */
class ManagerBranchControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listBranches_asManager1_returnsOnlyOwnBranch() throws Exception {
		mockMvc.perform(get("/api/v1/manager/branches")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].name", org.hamcrest.Matchers.containsString("Thủ Đức")));
	}

	@Test
	void listBranches_asManager2_returnsOnlyOwnBranch() throws Exception {
		mockMvc.perform(get("/api/v1/manager/branches")
						.header("Authorization", bearerToken(TestAccounts.MANAGER2_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].name", org.hamcrest.Matchers.containsString("Cầu Giấy")));
	}

	@Test
	void listBranches_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/manager/branches"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getBranch_manager2AccessingManager1Branch_rejected() throws Exception {
		String branch1Body = mockMvc.perform(get("/api/v1/manager/branches")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andReturn().getResponse().getContentAsString();
		Long branch1Id = objectMapper.readTree(branch1Body).path("data").path("content").get(0).path("id").asLong();

		// GB-05: chi nhánh của manager1 không được lộ cho manager2 dù cùng chuỗi Owner.
		mockMvc.perform(get("/api/v1/manager/branches/{id}", branch1Id)
						.header("Authorization", bearerToken(TestAccounts.MANAGER2_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void getBranch_ownBranch_ok() throws Exception {
		String branch1Body = mockMvc.perform(get("/api/v1/manager/branches")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andReturn().getResponse().getContentAsString();
		Long branch1Id = objectMapper.readTree(branch1Body).path("data").path("content").get(0).path("id").asLong();

		mockMvc.perform(get("/api/v1/manager/branches/{id}", branch1Id)
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(branch1Id));
	}
}
