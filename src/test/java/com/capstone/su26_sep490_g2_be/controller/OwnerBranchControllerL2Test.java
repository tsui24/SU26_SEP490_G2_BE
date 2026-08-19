package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.BranchStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBranchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — UC "Manage Branches" (Owner). GB-05: Owner khác không thấy chi nhánh của Owner này. */
class OwnerBranchControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listBranches_asOwner_includesSeededTwoBranches() throws Exception {
		mockMvc.perform(get("/api/v1/owner/branches")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(2));
	}

	@Test
	void listBranches_asManager_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/branches")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void createBranch_validPayload_created201() throws Exception {
		CreateBranchRequest req = new CreateBranchRequest();
		req.setName("L2 Test Branch " + System.nanoTime());
		req.setAddress("123 Test Street");

		mockMvc.perform(post("/api/v1/owner/branches")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value(req.getName()));
	}

	@Test
	void createBranch_blankAddress_rejected400() throws Exception {
		CreateBranchRequest req = new CreateBranchRequest();
		req.setName("Thiếu địa chỉ");
		req.setAddress("");

		mockMvc.perform(post("/api/v1/owner/branches")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createBranch_withoutToken_rejected401() throws Exception {
		CreateBranchRequest req = new CreateBranchRequest();
		req.setName("x");
		req.setAddress("x");

		mockMvc.perform(post("/api/v1/owner/branches")
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isUnauthorized());
	}

	private Long createBranch(String ownerAuth) throws Exception {
		CreateBranchRequest req = new CreateBranchRequest();
		req.setName("L2 CRUD Branch " + System.nanoTime());
		req.setAddress("456 Test Ave");
		String body = mockMvc.perform(post("/api/v1/owner/branches")
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("data").path("id").asLong();
	}

	@Test
	void getBranch_ownBranch_ok() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long branchId = createBranch(ownerAuth);

		mockMvc.perform(get("/api/v1/owner/branches/{id}", branchId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(branchId));
	}

	@Test
	void getBranch_unknownId_rejected404() throws Exception {
		mockMvc.perform(get("/api/v1/owner/branches/{id}", 999_999_999L)
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateBranch_ownBranch_updatesName() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long branchId = createBranch(ownerAuth);

		UpdateBranchRequest update = new UpdateBranchRequest();
		update.setName("L2 CRUD Branch (updated)");

		mockMvc.perform(put("/api/v1/owner/branches/{id}", branchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("L2 CRUD Branch (updated)"));
	}

	@Test
	void updateStatus_toInactive_thenBack() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long branchId = createBranch(ownerAuth);

		BranchStatusUpdateRequest req = new BranchStatusUpdateRequest();
		req.setStatus("INACTIVE");
		mockMvc.perform(patch("/api/v1/owner/branches/{id}/status", branchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));

		req.setStatus("ACTIVE");
		mockMvc.perform(patch("/api/v1/owner/branches/{id}/status", branchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	void updateStatus_missingStatus_rejected400() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long branchId = createBranch(ownerAuth);

		BranchStatusUpdateRequest req = new BranchStatusUpdateRequest();

		mockMvc.perform(patch("/api/v1/owner/branches/{id}/status", branchId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}
}
