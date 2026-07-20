package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.MailLayoutSettingsRequest;
import com.capstone.su26_sep490_g2_be.dto.response.MailLayoutSettingsResponse;

public interface MailLayoutSettingsService {

	/** Trả về cấu hình hiện tại — tự tạo dòng mặc định nếu chưa có. */
	MailLayoutSettingsResponse getSettings();

	MailLayoutSettingsResponse updateSettings(Long userId, MailLayoutSettingsRequest request);
}
