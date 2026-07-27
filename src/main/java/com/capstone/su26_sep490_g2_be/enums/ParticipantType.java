package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipantType {

	SINGLE("Đơn"),
	DOUBLE("Đôi");
	// TEAM (thi đấu đội) tạm thời bỏ ở giai đoạn này — pipeline đăng ký/participant/Excel import
	// chưa từng cài đặt xử lý cho loại này (chỉ rẽ nhánh SINGLE/DOUBLE), chọn TEAM sẽ âm thầm
	// bị xử lý sai làm cá nhân thay vì báo lỗi.

	private final String displayName;

	public String getValue() {
		return name();
	}
}
