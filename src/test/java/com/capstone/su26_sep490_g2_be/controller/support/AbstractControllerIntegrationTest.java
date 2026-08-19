package com.capstone.su26_sep490_g2_be.controller.support;

import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base cho mọi L2 Controller test: dựng context Spring thật và request qua MockMvc, tức là đi
 * đúng chuỗi thật {@code JwtAuthenticationFilter -> SecurityConfig -> Controller -> Service -> DB}
 * — không mock Security, không mock Service.
 *
 * <p>Dự án chưa có H2/Testcontainers trong pom.xml (xem BTMS-Tournament-Config-API.md /
 * pom.xml) — L2 nối thẳng MySQL theo {@code application-dev.yml}, giống hệt
 * {@code ProgressiveRoundRobinOddPlayoffTest} đã có sẵn trước khi bộ test này được thêm.
 * {@code @Transactional} tự rollback sau mỗi test nên không để rác trong DB dev cục bộ lẫn MySQL
 * service container của CI ({@code .github/workflows/deploy.yml}).
 *
 * <p>Token JWT sinh trực tiếp qua {@link JwtUtil} cho tài khoản đã có sẵn trong
 * {@code DataInitializer} (xem {@link TestAccounts}) thay vì gọi {@code POST /auth/login} ở mỗi
 * test — vẫn là JWT thật, vẫn đi qua đúng {@code JwtAuthenticationFilter}/{@code SecurityConfig}
 * thật, chỉ bỏ qua bước nhập mật khẩu cho nhanh. Luồng login thật (kiểm tra mật khẩu, GB-01,
 * GB-02...) được test riêng trong {@code AuthControllerL2Test}.
 *
 * <p>{@code objectMapper} KHÔNG {@code @Autowired} — context thật của app không có bean
 * {@code ObjectMapper} nào để inject (xác nhận bằng chạy thật: {@code NoSuchBeanDefinitionException}
 * ở toàn bộ 331 test khi lần đầu chạy full suite trên MySQL local). Không phải lỗi test — production
 * code cũng chưa từng {@code @Autowired ObjectMapper} ở đâu cả, mọi nơi cần JSON đều tự
 * {@code new ObjectMapper()} thủ công ({@code PaymentController}, {@code PayOSServiceImpl},
 * {@code JwtAuthenticationFilter}...). Khởi tạo trực tiếp ở đây để giống hệt cách production code
 * đang làm, thay vì dựa vào bean không tồn tại.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractControllerIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	protected final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	protected JwtUtil jwtUtil;

	@Autowired
	protected UserRepository userRepository;

	/** Giá trị dùng thẳng cho header {@code Authorization}, vd {@code .header("Authorization", bearerToken(email))}. */
	protected String bearerToken(String seedEmail) {
		User user = seedUser(seedEmail);
		return "Bearer " + jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().getCode());
	}

	protected User seedUser(String seedEmail) {
		return userRepository.findByEmail(seedEmail)
				.orElseThrow(() -> new IllegalStateException(
						"Seed account không tồn tại: " + seedEmail
								+ " — DataInitializer phải chạy trước (SPRING_PROFILES_ACTIVE=dev, DB rỗng ở lần khởi động đầu)."));
	}

	protected Long userIdOf(String seedEmail) {
		return seedUser(seedEmail).getId();
	}

	protected String toJson(Object body) throws Exception {
		return objectMapper.writeValueAsString(body);
	}
}
