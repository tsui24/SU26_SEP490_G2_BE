package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.RegisterDeviceTokenRequest;
import com.capstone.su26_sep490_g2_be.dto.response.NotificationResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;

import java.time.Instant;

/**
 * Hộp thông báo trong app và danh bạ thiết bị nhận thông báo đẩy.
 *
 * Không có bảng thông báo riêng: danh sách được dựng lại từ {@code email_send_logs} — mỗi email
 * hệ thống đã gửi cho người dùng chính là một thông báo. Nhờ vậy hai kênh luôn nói cùng một
 * chuyện, và không phải bảo trì thêm một nguồn sự thật thứ hai.
 *
 * Hệ quả cần biết: sự kiện nào không có rule email đang bật thì cũng không thành thông báo.
 */
public interface NotificationService {

	/** Thông báo của người đang đăng nhập, mới nhất trước. */
	PageResponse<NotificationResponse> listMine(Long userId, int page, int size);

	/**
	 * Số thông báo mới hơn mốc đã đọc.
	 *
	 * Mốc do chính thiết bị giữ và gửi lên, vì trạng thái đã đọc được lưu ở máy chứ không có
	 * cột nào trong DB. {@code after} null thì đếm tất cả.
	 */
	long countUnread(Long userId, Instant after);

	/** Ghi nhận thiết bị để gửi thông báo đẩy. Gọi lại nhiều lần với cùng token là an toàn. */
	void registerDevice(Long userId, RegisterDeviceTokenRequest request);

	/** Gỡ thiết bị khỏi danh bạ, dùng khi đăng xuất. */
	void unregisterDevice(Long userId, String expoToken);
}
