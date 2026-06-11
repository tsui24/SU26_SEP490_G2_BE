package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// Common
	COMMON_INVALID_REQUEST("COMMON_001", "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
	COMMON_NOT_FOUND("COMMON_002", "Không tìm thấy tài nguyên", HttpStatus.NOT_FOUND),
	COMMON_INTERNAL_ERROR("COMMON_500", "Lỗi hệ thống nội bộ", HttpStatus.INTERNAL_SERVER_ERROR),
	EXTERNAL_API_ERROR("EXT_001", "Gọi API bên ngoài thất bại", HttpStatus.BAD_GATEWAY),
	RESOURCE_NOT_FOUND("COMMON_003", "Không tìm thấy tài nguyên", HttpStatus.NOT_FOUND),
	DUPLICATE_RESOURCE("COMMON_004", "Tài nguyên đã tồn tại", HttpStatus.CONFLICT),
	INVALID_OPERATION("COMMON_005", "Thao tác không được phép ở trạng thái hiện tại", HttpStatus.UNPROCESSABLE_ENTITY),

	// Auth
	AUTH_INVALID_CREDENTIALS("AUTH_001", "Email hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
	AUTH_EMAIL_ALREADY_EXISTS("AUTH_002", "Email đã được đăng ký", HttpStatus.CONFLICT),
	AUTH_WRONG_OLD_PASSWORD("AUTH_003", "Mật khẩu hiện tại không đúng", HttpStatus.BAD_REQUEST),
	AUTH_INVALID_OTP("AUTH_004", "Mã OTP không hợp lệ", HttpStatus.BAD_REQUEST),
	AUTH_OTP_EXPIRED("AUTH_005", "Mã OTP đã hết hạn", HttpStatus.BAD_REQUEST),
	AUTH_ACCESS_DENIED("AUTH_006", "Không có quyền truy cập", HttpStatus.FORBIDDEN),
	AUTH_INVALID_TOKEN("AUTH_007", "Token không hợp lệ", HttpStatus.UNAUTHORIZED),
	AUTH_MISSING_TOKEN("AUTH_013", "Thiếu token xác thực", HttpStatus.UNAUTHORIZED),
	AUTH_ACCOUNT_LOCKED("AUTH_008", "Tài khoản đã bị khóa", HttpStatus.FORBIDDEN),
	AUTH_INVALID_ROLE_ASSIGNMENT("AUTH_009", "Bạn không được phép gán vai trò này", HttpStatus.FORBIDDEN),
	AUTH_ROLE_NOT_FOUND("AUTH_010", "Không tìm thấy vai trò", HttpStatus.NOT_FOUND),
	AUTH_USER_NOT_FOUND("AUTH_011", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
	AUTH_PHONE_ALREADY_EXISTS("AUTH_012", "Số điện thoại đã được đăng ký", HttpStatus.CONFLICT),

	// Profile
	PROFILE_ALREADY_EXISTS("PROFILE_001", "Hồ sơ đã tồn tại", HttpStatus.CONFLICT),
	PROFILE_NOT_FOUND("PROFILE_002", "Không tìm thấy hồ sơ", HttpStatus.NOT_FOUND),
	PROFILE_ONLY_FOR_PLAYER("PROFILE_003", "Chỉ tài khoản Cơ thủ mới được tạo hồ sơ player", HttpStatus.FORBIDDEN),

	// Tournament config
	FORMAT_CODE_EXISTS("FORMAT_001", "Mã thể thức đã tồn tại", HttpStatus.CONFLICT),
	FORMAT_NOT_FOUND("FORMAT_002", "Không tìm thấy thể thức", HttpStatus.NOT_FOUND),
	FORMAT_NOT_READY("FORMAT_003", "Cấu hình mặc định của thể thức chưa sẵn sàng", HttpStatus.UNPROCESSABLE_ENTITY),
	INVALID_FIELD_KEY("FORMAT_004", "Field key không có trong catalog", HttpStatus.BAD_REQUEST),
	INVALID_FIELD_FOR_FORMAT("FORMAT_005", "Field không thuộc thể thức này", HttpStatus.BAD_REQUEST),
	SETUP_INCOMPLETE("FORMAT_006", "Thiết lập thể thức chưa hoàn tất", HttpStatus.UNPROCESSABLE_ENTITY),
	ALREADY_BOOTSTRAPPED("FORMAT_007", "Thể thức đã có cấu hình mặc định", HttpStatus.CONFLICT),
	CONFIG_VALIDATION_FAILED("FORMAT_008", "Xác thực cấu hình giải thất bại", HttpStatus.BAD_REQUEST),
	CONFIG_INCOMPLETE("FORMAT_009", "Cấu hình giải chưa đầy đủ", HttpStatus.UNPROCESSABLE_ENTITY),
	INVALID_STATUS_TRANSITION("FORMAT_010", "Chuyển trạng thái giải không hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY),
	GAME_TYPE_NOT_FOUND("FORMAT_011", "Không tìm thấy loại bi", HttpStatus.NOT_FOUND),

	// Employee Management
	EMPLOYEE_NOT_FOUND("EMPLOYEE_001", "Không tìm thấy nhân viên", HttpStatus.NOT_FOUND),
	INVALID_EMPLOYEE_ROLE("EMPLOYEE_002", "Người dùng không phải Staff hoặc Manager", HttpStatus.BAD_REQUEST),

	// Storage (MinIO)
	STORAGE_UPLOAD_FAILED("STORAGE_001", "Tải file lên thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
	STORAGE_DOWNLOAD_FAILED("STORAGE_002", "Tải file xuống thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
	STORAGE_OBJECT_NOT_FOUND("STORAGE_003", "Không tìm thấy file trong kho lưu trữ", HttpStatus.NOT_FOUND),
	STORAGE_INVALID_FILE("STORAGE_004", "Loại file hoặc kích thước không hợp lệ", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;
}
