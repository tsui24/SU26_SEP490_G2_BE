package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Địa chỉ nhận thông báo đẩy của một thiết bị — server cần token này để gọi Expo Push Service,
 * vì bản thân JWT không giúp gửi được gì tới máy đang đóng app.
 *
 * Cùng một người có thể đăng nhập trên nhiều máy nên quan hệ là một-nhiều. Ngược lại, một máy
 * cho người khác mượn rồi đăng nhập tài khoản khác thì Expo vẫn cấp đúng token cũ — vì vậy
 * {@code expoToken} là unique và bản ghi được gán lại cho người mới thay vì tạo thêm dòng,
 * nếu không người trước sẽ tiếp tục nhận thông báo trên máy không còn là của mình.
 */
@Entity
@Table(name = "device_tokens", indexes = {
		@Index(name = "idx_device_tokens_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** Chuỗi dạng ExponentPushToken[...] do Expo cấp cho app trên đúng máy này. */
	@Column(name = "expo_token", nullable = false, unique = true, length = 255)
	private String expoToken;

	/** "android" | "ios" — chỉ để tra cứu khi cần biết thông báo hỏng ở nền tảng nào. */
	@Column(nullable = false, length = 20)
	private String platform;

	/** Cập nhật mỗi lần app đăng ký lại, dùng để dọn token của máy đã lâu không dùng. */
	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;
}
