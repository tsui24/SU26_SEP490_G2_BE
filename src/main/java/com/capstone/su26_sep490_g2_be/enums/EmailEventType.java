package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum EmailEventType {

	USER_REGISTERED("Người dùng đăng ký tài khoản mới"),
	REGISTRATION_SUBMITTED("Đăng ký mới được gửi"),
	REGISTRATION_APPROVED("Đăng ký được duyệt"),
	REGISTRATION_REJECTED("Đăng ký bị từ chối"),
	REGISTRATION_CANCELLED("Đăng ký bị hủy"),
	PAYMENT_SUCCESS("Thanh toán thành công"),
	PAYMENT_FAILED("Thanh toán thất bại"),
	TOURNAMENT_REGISTRATION_OPEN("Giải đấu mở đăng ký"),
	TOURNAMENT_REGISTRATION_CLOSING_SOON("Sắp đóng đăng ký"),
	TOURNAMENT_DRAW_COMPLETED("Đã bốc thăm xong"),
	TOURNAMENT_STATUS_CHANGED("Trạng thái giải đấu thay đổi"),
	MATCH_SCHEDULED_REMINDER("Nhắc lịch thi đấu"),
	MATCH_STARTING_SOON("Trận đấu sắp bắt đầu"),
	MATCH_COMPLETED("Trận đấu đã kết thúc"),
	MATCH_REFEREE_ASSIGNED("Được phân công điều hành trận"),
	PARTICIPANT_WITHDRAWN("Vận động viên rút lui"),
	STAFF_ACCOUNT_CREATED("Tài khoản nhân viên được tạo"),
	MANAGER_ACCOUNT_CREATED("Tài khoản quản lý được tạo"),
	CUSTOM_MANUAL_SEND("Gửi thủ công");

	private final String displayName;

	public String getValue() {
		return name();
	}

	/**
	 * Sự kiện KHÔNG gắn với 1 giải đấu cụ thể (tài khoản người dùng/nhân sự) — chỉ Admin quản lý
	 * ở phạm vi toàn hệ thống, không hiển thị trong danh sách rule của Owner/Manager theo giải đấu.
	 */
	private static final Set<EmailEventType> ACCOUNT_SCOPED_EVENTS =
			Set.of(USER_REGISTERED, STAFF_ACCOUNT_CREATED, MANAGER_ACCOUNT_CREATED);

	public boolean isTournamentScoped() {
		return !ACCOUNT_SCOPED_EVENTS.contains(this);
	}
}
