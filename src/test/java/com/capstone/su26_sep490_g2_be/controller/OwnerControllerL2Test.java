package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateEmployeeAccountRequest;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — UC-07 biến thể Owner: tạo/quản lý Manager + Staff toàn chuỗi. */
class OwnerControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void createManager_manageAllBranches_created201() throws Exception {
		CreateManagerAccountRequest req = new CreateManagerAccountRequest();
		req.setEmail("new-manager-" + System.nanoTime() + "@gmail.com");
		req.setPassword("Passw0rd");
		req.setFullName("Quản lý L2 test");
		req.setManageAllBranches(true);

		mockMvc.perform(post("/api/v1/owner/accounts/manager")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.email").value(req.getEmail()));
	}

	@Test
	void createManager_asManager_rejected403() throws Exception {
		CreateManagerAccountRequest req = new CreateManagerAccountRequest();
		req.setEmail("blocked-manager-" + System.nanoTime() + "@gmail.com");
		req.setPassword("Passw0rd");
		req.setFullName("Blocked");

		mockMvc.perform(post("/api/v1/owner/accounts/manager")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isForbidden());
	}

	@Test
	void createStaff_validPayload_created201() throws Exception {
		CreateStaffAccountRequest req = new CreateStaffAccountRequest();
		req.setEmail("owner-created-staff-" + System.nanoTime() + "@gmail.com");
		req.setPassword("Passw0rd");
		req.setFullName("Nhân viên do Owner tạo");

		mockMvc.perform(post("/api/v1/owner/accounts/staff")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated());
	}

	@Test
	void createStaff_duplicateEmail_rejected409() throws Exception {
		CreateStaffAccountRequest req = new CreateStaffAccountRequest();
		req.setEmail(TestAccounts.STAFF1_EMAIL);
		req.setPassword("Passw0rd");
		req.setFullName("Trùng email");

		mockMvc.perform(post("/api/v1/owner/accounts/staff")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void getEmployees_asOwner_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/employees")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.param("role", "MANAGER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getEmployeeDetail_ownEmployee_ok() throws Exception {
		mockMvc.perform(get("/api/v1/owner/employees/{id}", userIdOf(TestAccounts.MANAGER1_EMAIL))
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(TestAccounts.MANAGER1_EMAIL));
	}

	@Test
	void getEmployeeDetail_notMyEmployee_rejected() throws Exception {
		// Player không phải nhân viên của Owner này -> 404/403 (không đủ điều kiện để trả về).
		mockMvc.perform(get("/api/v1/owner/employees/{id}", userIdOf(TestAccounts.PLAYER1_EMAIL))
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void updateEmployee_staffFullName_ok() throws Exception {
		UpdateEmployeeAccountRequest req = new UpdateEmployeeAccountRequest();
		req.setFullName("Trần Văn Trọng (updated)");

		mockMvc.perform(put("/api/v1/owner/employees/{id}", userIdOf(TestAccounts.STAFF4_EMAIL))
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fullName").value("Trần Văn Trọng (updated)"));
	}

	@Test
	void deactivateThenReactivate_employee_flipsStatus() throws Exception {
		String ownerAuth = bearerToken(TestAccounts.OWNER_EMAIL);
		Long staffId = userIdOf(TestAccounts.STAFF2_EMAIL);

		mockMvc.perform(put("/api/v1/owner/employees/{id}/deactivate", staffId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());
		User locked = userRepository.findById(staffId).orElseThrow();
		assertEquals(UserStatus.LOCKED, locked.getStatus());

		mockMvc.perform(put("/api/v1/owner/employees/{id}/reactivate", staffId)
						.header("Authorization", ownerAuth))
				.andExpect(status().isOk());
		assertEquals(UserStatus.ACTIVE, userRepository.findById(staffId).orElseThrow().getStatus());
	}
}
