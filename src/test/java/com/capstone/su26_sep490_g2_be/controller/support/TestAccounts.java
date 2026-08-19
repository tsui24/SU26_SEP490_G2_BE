package com.capstone.su26_sep490_g2_be.controller.support;

/**
 * Email/password của các tài khoản seed sẵn trong {@code DataInitializer#seedAccounts()}.
 * KHÔNG tạo tài khoản mới trong DataInitializer khi thêm test — thêm hằng số ở đây và tái sử dụng,
 * để không có 2 bộ test seed trùng email khác nhau.
 */
public final class TestAccounts {

	private TestAccounts() {
	}

	public static final String ADMIN_EMAIL = "admin@gmail.com";
	public static final String ADMIN_PASSWORD = "admin1";

	public static final String OWNER_EMAIL = "owner@gmail.com";
	public static final String OWNER_PASSWORD = "owner123";

	/** Quản lý chi nhánh Thủ Đức (branch1). */
	public static final String MANAGER1_EMAIL = "manager@gmail.com";
	public static final String MANAGER1_PASSWORD = "manager123";

	/** Quản lý chi nhánh Cầu Giấy (branch2) — dùng để test cách ly dữ liệu giữa 2 branch (GB-05). */
	public static final String MANAGER2_EMAIL = "manager2@gmail.com";
	public static final String MANAGER2_PASSWORD = "manager123";

	/** Lễ tân/trọng tài chi nhánh Thủ Đức. */
	public static final String STAFF1_EMAIL = "staff1@gmail.com";
	public static final String STAFF1_PASSWORD = "staff123";

	/** Lễ tân/trọng tài chi nhánh Cầu Giấy. */
	public static final String STAFF2_EMAIL = "staff2@gmail.com";
	public static final String STAFF2_PASSWORD = "staff123";

	public static final String STAFF3_EMAIL = "staff3@gmail.com";
	public static final String STAFF4_EMAIL = "staff4@gmail.com";

	public static final String PLAYER1_EMAIL = "player1@gmail.com";
	public static final String PLAYER1_PASSWORD = "player123";

	public static final String PLAYER2_EMAIL = "player2@gmail.com";
	public static final String PLAYER2_PASSWORD = "player123";

	public static final String PLAYER3_EMAIL = "player3@gmail.com";
	public static final String PLAYER4_EMAIL = "player4@gmail.com";
	public static final String PLAYER5_EMAIL = "player5@gmail.com";
	public static final String PLAYER6_EMAIL = "player6@gmail.com";
	public static final String PLAYER7_EMAIL = "player7@gmail.com";
	public static final String PLAYER8_EMAIL = "player8@gmail.com";
	public static final String PLAYER9_EMAIL = "player9@gmail.com";
	public static final String PLAYER10_EMAIL = "player10@gmail.com";

	public static final String COMMON_PASSWORD_WRONG = "wrong-password-xyz";
}
