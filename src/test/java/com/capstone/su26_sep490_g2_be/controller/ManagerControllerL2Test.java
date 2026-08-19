package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L2 — UC-07 biến thể Manager: quản lý Staff trong (các) chi nhánh được cấp quyền (GB-05). */
class ManagerControllerL2Test extends AbstractControllerIntegrationTest {

	@Autowired
	private BranchRepository branchRepository;

	/** Manager tạo Staff bắt buộc chỉ định branchId trong phạm vi được gán (AccountServiceImpl — BRANCH_ACCESS_DENIED nếu thiếu/ngoài phạm vi). */
	private Long branch1Id() {
		return branchRepository.findByOwnerId(userIdOf(TestAccounts.OWNER_EMAIL)).stream()
				.filter(b -> b.getName().contains("Thủ Đức"))
				.findFirst().orElseThrow().getId();
	}

	@Test
	void getStaffs_asManager1_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/accounts/staffs")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray());
	}

	@Test
	void getStaffs_asStaff_rejected403() throws Exception {
		mockMvc.perform(get("/api/v1/manager/accounts/staffs")
						.header("Authorization", bearerToken(TestAccounts.STAFF1_EMAIL)))
				.andExpect(status().isForbidden());
	}

	@Test
	void createStaff_validPayload_created201() throws Exception {
		CreateStaffAccountRequest req = new CreateStaffAccountRequest();
		req.setEmail("new-staff-" + System.nanoTime() + "@gmail.com");
		req.setPassword("Passw0rd");
		req.setFullName("Nhân viên L2 test");
		req.setBranchId(branch1Id());

		mockMvc.perform(post("/api/v1/manager/accounts/staff")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.email").value(req.getEmail()));
	}

	@Test
	void createStaff_duplicateEmail_rejected409() throws Exception {
		CreateStaffAccountRequest req = new CreateStaffAccountRequest();
		req.setEmail(TestAccounts.STAFF1_EMAIL); // đã seed sẵn
		req.setPassword("Passw0rd");
		req.setFullName("Trùng email");
		req.setBranchId(branch1Id());

		mockMvc.perform(post("/api/v1/manager/accounts/staff")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isConflict());
	}

	@Test
	void createStaff_blankFullName_rejected400() throws Exception {
		CreateStaffAccountRequest req = new CreateStaffAccountRequest();
		req.setEmail("blank-fullname-" + System.nanoTime() + "@gmail.com");
		req.setPassword("Passw0rd");
		req.setFullName("");

		mockMvc.perform(post("/api/v1/manager/accounts/staff")
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL))
						.contentType(MediaType.APPLICATION_JSON)
						.content(toJson(req)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getEmployeeDetail_ownBranchStaff_ok() throws Exception {
		mockMvc.perform(get("/api/v1/manager/employees/{id}", userIdOf(TestAccounts.STAFF1_EMAIL))
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(TestAccounts.STAFF1_EMAIL));
	}

	@Test
	void getEmployeeDetail_otherManagerBranchStaff_rejected() throws Exception {
		// GB-05: staff1 thuộc chi nhánh của manager1 — manager2 không được xem chi tiết.
		mockMvc.perform(get("/api/v1/manager/employees/{id}", userIdOf(TestAccounts.STAFF1_EMAIL))
						.header("Authorization", bearerToken(TestAccounts.MANAGER2_EMAIL)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void deactivateThenReactivate_ownBranchStaff_flipsStatus() throws Exception {
		String managerAuth = bearerToken(TestAccounts.MANAGER1_EMAIL);
		Long staffId = userIdOf(TestAccounts.STAFF3_EMAIL);

		mockMvc.perform(put("/api/v1/manager/accounts/staffs/{id}/deactivate", staffId)
						.header("Authorization", managerAuth))
				.andExpect(status().isOk());
		assertEquals(UserStatus.LOCKED, userRepository.findById(staffId).orElseThrow().getStatus());

		mockMvc.perform(put("/api/v1/manager/accounts/staffs/{id}/reactivate", staffId)
						.header("Authorization", managerAuth))
				.andExpect(status().isOk());
		assertEquals(UserStatus.ACTIVE, userRepository.findById(staffId).orElseThrow().getStatus());
	}

	@Test
	void deactivate_otherManagerBranchStaff_rejected() throws Exception {
		mockMvc.perform(put("/api/v1/manager/accounts/staffs/{id}/deactivate", userIdOf(TestAccounts.STAFF2_EMAIL))
						.header("Authorization", bearerToken(TestAccounts.MANAGER1_EMAIL)))
				.andExpect(status().is4xxClientError());
	}
}
