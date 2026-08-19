package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2 — /api/v1/storage/** không khoá theo role (mọi role đã đăng nhập đều gọi được qua
 * {@code anyRequest().authenticated()}), chỉ khoá bằng JWT. MinIO không chạy trong môi trường
 * dev/CI mặc định (xem {@code .github/workflows/deploy.yml}: chỉ truyền placeholder credentials) —
 * nên L2 chỉ kiểm tra lớp Security (JwtAuthenticationFilter) đứng trước MinioStorageService, KHÔNG
 * test happy-path upload/download thật (phụ thuộc MinIO chạy thật, thuộc phạm vi test thủ công/L3
 * trên môi trường có MinIO).
 */
class StorageControllerL2Test extends AbstractControllerIntegrationTest {

	@Test
	void uploadImage_withoutToken_rejected401() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});

		mockMvc.perform(multipart("/api/v1/storage/images").file(file))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void uploadImage_missingFilePart_rejected400() throws Exception {
		mockMvc.perform(multipart("/api/v1/storage/images")
						.header("Authorization", bearerToken(TestAccounts.OWNER_EMAIL)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getImageUrl_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/storage/images/url").param("objectKey", "avatars/x.jpg"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getImageUrl_missingObjectKeyParam_rejected400() throws Exception {
		mockMvc.perform(get("/api/v1/storage/images/url")
						.header("Authorization", bearerToken(TestAccounts.PLAYER1_EMAIL)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void downloadImage_withoutToken_rejected401() throws Exception {
		mockMvc.perform(get("/api/v1/storage/images/download").param("objectKey", "avatars/x.jpg"))
				.andExpect(status().isUnauthorized());
	}
}
