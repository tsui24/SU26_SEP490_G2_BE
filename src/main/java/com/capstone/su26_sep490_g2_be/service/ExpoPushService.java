package com.capstone.su26_sep490_g2_be.service;

import java.util.List;
import java.util.Map;

/**
 * Gửi thông báo đẩy tới thiết bị qua Expo Push Service.
 *
 * Không dính dáng gì tới JWT: server tự xưng danh với Expo, còn địa chỉ đến là push token đã lưu
 * trong {@code device_tokens}. Nhờ vậy thông báo vẫn tới máy kể cả khi phiên đăng nhập đã hết hạn.
 */
public interface ExpoPushService {

	/**
	 * Đẩy một thông báo tới mọi thiết bị của những người này. Người không có thiết bị nào đăng ký
	 * thì lặng lẽ bỏ qua.
	 *
	 * @param data dữ liệu kèm theo để app biết mở màn nào khi người dùng bấm vào
	 */
	void sendToUsers(List<Long> userIds, String title, String body, Map<String, Object> data);
}
