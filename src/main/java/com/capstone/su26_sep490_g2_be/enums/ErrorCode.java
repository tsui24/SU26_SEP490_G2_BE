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

	// Registration form template
	REG_FIELD_KEY_EXISTS("REG_FORM_001", "Registration field key already exists", HttpStatus.CONFLICT),
	REG_FIELD_NOT_FOUND("REG_FORM_002", "Registration field not found", HttpStatus.NOT_FOUND),
	REG_FIELD_IN_USE("REG_FORM_003", "Registration field is used in templates", HttpStatus.CONFLICT),
	REG_TEMPLATE_CODE_EXISTS("REG_FORM_004", "Registration form template code already exists", HttpStatus.CONFLICT),
	REG_TEMPLATE_NOT_FOUND("REG_FORM_005", "Registration form template not found", HttpStatus.NOT_FOUND),
	REG_TEMPLATE_INACTIVE("REG_FORM_006", "Registration form template is inactive", HttpStatus.UNPROCESSABLE_ENTITY),
	REG_TEMPLATE_INCOMPLETE("REG_FORM_007", "Registration form template has no fields", HttpStatus.UNPROCESSABLE_ENTITY),
	REG_TEMPLATE_REQUIRED("REG_FORM_008", "Registration form template is required when registration is enabled", HttpStatus.BAD_REQUEST),
	REG_FORM_VALIDATION_FAILED("REG_FORM_009", "Registration form validation failed", HttpStatus.BAD_REQUEST),
	REGISTRATION_NOT_OPEN("REG_FORM_010", "Tournament is not open for registration", HttpStatus.UNPROCESSABLE_ENTITY),
	REGISTRATION_ALREADY_EXISTS("REG_FORM_011", "You have already registered for this tournament", HttpStatus.CONFLICT),

	// Tournament participation
	TOURNAMENT_FULL("TOURNAMENT_001", "Giải đấu đã đủ số người tham gia", HttpStatus.CONFLICT),
	PARTICIPANT_INVALID_EXCEL("TOURNAMENT_002", "File không hợp lệ. Vui lòng tải mẫu từ nút \"Tải template\" hoặc upload file .xlsx / .csv", HttpStatus.BAD_REQUEST),
	TOURNAMENT_DATE_INVALID("TOURNAMENT_003", "Ngày tháng giải đấu không hợp lệ", HttpStatus.BAD_REQUEST),

	// Payment
	PAYMENT_CREATE_FAILED("PAYMENT_001", "Tạo đơn thanh toán thất bại", HttpStatus.BAD_GATEWAY),
	PAYMENT_NOT_FOUND("PAYMENT_002", "Không tìm thấy thông tin thanh toán", HttpStatus.NOT_FOUND),
	PAYMENT_ALREADY_PAID("PAYMENT_003", "Đăng ký này đã được thanh toán", HttpStatus.CONFLICT),
	PAYMENT_NOT_REQUIRED("PAYMENT_004", "Giải đấu này miễn phí — không cần thanh toán", HttpStatus.BAD_REQUEST),
	PAYMENT_INVALID_SIGNATURE("PAYMENT_005", "Chữ ký thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),

	// Storage (MinIO)
	STORAGE_UPLOAD_FAILED("STORAGE_001", "Tải file lên thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
	STORAGE_DOWNLOAD_FAILED("STORAGE_002", "Tải file xuống thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
	STORAGE_OBJECT_NOT_FOUND("STORAGE_003", "Không tìm thấy file trong kho lưu trữ", HttpStatus.NOT_FOUND),
	STORAGE_INVALID_FILE("STORAGE_004", "Loại file hoặc kích thước không hợp lệ", HttpStatus.BAD_REQUEST),

	// Branch
	BRANCH_NOT_FOUND("BRANCH_001", "Không tìm thấy chi nhánh", HttpStatus.NOT_FOUND),
	BRANCH_ACCESS_DENIED("BRANCH_002", "Bạn không có quyền truy cập chi nhánh này", HttpStatus.FORBIDDEN),
	BRANCH_INACTIVE("BRANCH_003", "Chi nhánh đang ngừng hoạt động", HttpStatus.UNPROCESSABLE_ENTITY),
	BRANCH_REQUIRED("BRANCH_005", "Vui lòng chọn chi nhánh tổ chức", HttpStatus.BAD_REQUEST),

	// Email notification
	EMAIL_TEMPLATE_NOT_FOUND("EMAIL_001", "Không tìm thấy mẫu email", HttpStatus.NOT_FOUND),
	EMAIL_TEMPLATE_CODE_EXISTS("EMAIL_002", "Mã mẫu email đã tồn tại", HttpStatus.CONFLICT),
	EMAIL_TEMPLATE_INACTIVE("EMAIL_003", "Mẫu email đang bị vô hiệu hoá", HttpStatus.UNPROCESSABLE_ENTITY),
	EMAIL_RULE_NOT_FOUND("EMAIL_004", "Không tìm thấy quy tắc tự động", HttpStatus.NOT_FOUND),
	EMAIL_RULE_CODE_EXISTS("EMAIL_005", "Mã quy tắc tự động đã tồn tại", HttpStatus.CONFLICT),
	EMAIL_RENDER_FAILED("EMAIL_006", "Không thể render nội dung email", HttpStatus.UNPROCESSABLE_ENTITY),
	EMAIL_SEND_FAILED("EMAIL_007", "Gửi email thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
	EMAIL_RECIPIENT_EMPTY("EMAIL_008", "Danh sách người nhận trống", HttpStatus.BAD_REQUEST),
	EMAIL_AUTOMATION_DISABLED("EMAIL_009", "Quy tắc tự động đang tắt", HttpStatus.UNPROCESSABLE_ENTITY),
	EMAIL_LOG_NOT_FOUND("EMAIL_010", "Không tìm thấy nhật ký gửi email", HttpStatus.NOT_FOUND);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;
}
