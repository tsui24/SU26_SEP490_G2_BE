package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — /api/v1/branches/** công khai, không cần đăng nhập, chỉ trả branch ACTIVE. */
class PublicBranchControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired
	private BranchRepository branchRepository;

	@Test
	void listBranches_withoutToken_includesSeededActiveBranches() throws Exception {
		mockMvc.perform(get("/api/v1/branches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(2));
	}

	@Test
	void getBranch_seededActiveBranch_ok() throws Exception {
		Long branchId = branchRepository.findByOwnerId(userIdOf(TestAccounts.OWNER_EMAIL))
				.get(0).getId();

		mockMvc.perform(get("/api/v1/branches/{id}", branchId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(branchId));
	}

	@Test
	void listBranches_searchByName_filtersResult() throws Exception {
		mockMvc.perform(get("/api/v1/branches").param("search", "Cầu Giấy"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(1));
	}

	@Test
	void getBranch_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/branches/{id}", 999_999_999L))
				.andExpect(status().isNotFound());
	}
}
