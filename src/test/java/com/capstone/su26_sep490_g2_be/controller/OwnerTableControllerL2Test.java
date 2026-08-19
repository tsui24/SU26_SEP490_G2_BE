package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.request.TableStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBilliardTableRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — Owner quản lý pool bàn dùng chung của chuỗi. */
class OwnerTableControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void listActiveTables_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tables/active")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void listTables_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tables")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void listTables_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tables")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void createTable_validPayload_created201() throws Exception {
		CreateBilliardTableRequest req = new CreateBilliardTableRequest();
		req.setName("Bàn L2 Test " + System.nanoTime());
		req.setTableNumber(99);
		req.setTableType("POOL");

		mockMvc.perform(post("/api/v1/owner/tables")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value(req.getName()));
	}

	@Test
	void createTable_blankName_rejected400() throws Exception {
		CreateBilliardTableRequest req = new CreateBilliardTableRequest();
		req.setName("");

		mockMvc.perform(post("/api/v1/owner/tables")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	private Long createTable(String ownerAuth) throws Exception {
		CreateBilliardTableRequest req = new CreateBilliardTableRequest();
		req.setName("Bàn L2 CRUD " + System.nanoTime());
		req.setTableType("POOL");
		String body = mockMvc.perform(post("/api/v1/owner/tables")
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("data").path("id").asLong();
	}

	@Test
	void getTable_ownTable_ok() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long tableId = createTable(ownerAuth);

		mockMvc.perform(get("/api/v1/owner/tables/{id}", tableId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(tableId));
	}

	@Test
	void updateTable_ownTable_updatesName() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long tableId = createTable(ownerAuth);

		UpdateBilliardTableRequest update = new UpdateBilliardTableRequest();
		update.setName("Bàn L2 CRUD (updated)");

		mockMvc.perform(put("/api/v1/owner/tables/{id}", tableId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Bàn L2 CRUD (updated)"));
	}

	@Test
	void updateStatus_toInactive_ok() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long tableId = createTable(ownerAuth);

		TableStatusUpdateRequest req = new TableStatusUpdateRequest();
		req.setStatus("INACTIVE");

		mockMvc.perform(patch("/api/v1/owner/tables/{id}/status", tableId)
						.header("Authorization", ownerAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("INACTIVE"));
	}

	@Test
	void downloadImportTemplate_asOwner_returnsXlsx() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tables/import-template")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk());
	}

	@Test
	void downloadImportTemplate_asPlayer_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/owner/tables/import-template")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isForbidden());
	}
}
