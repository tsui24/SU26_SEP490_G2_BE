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
	CONCURRENT_UPDATE_CONFLICT("COMMON_006",
			"Dữ liệu vừa được người khác cập nhật — vui lòng tải lại và thử lại", HttpStatus.CONFLICT),
	COMMON_METHOD_NOT_ALLOWED("COMMON_007", "Phương thức HTTP không được hỗ trợ cho endpoint này", HttpStatus.METHOD_NOT_ALLOWED),
	COMMON_MALFORMED_BODY("COMMON_008", "Dữ liệu gửi lên không đúng định dạng", HttpStatus.BAD_REQUEST),

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
	AUTH_OTP_TOO_MANY_ATTEMPTS("AUTH_014", "Nhập sai OTP quá nhiều lần — vui lòng yêu cầu mã mới", HttpStatus.TOO_MANY_REQUESTS),
	AUTH_OTP_RESEND_TOO_SOON("AUTH_015", "Vui lòng đợi ít phút trước khi yêu cầu gửi lại OTP", HttpStatus.TOO_MANY_REQUESTS),

	// Profile
	PROFILE_ALREADY_EXISTS("PROFILE_001", "Hồ sơ đã tồn tại", HttpStatus.CONFLICT),
	PROFILE_NOT_FOUND("PROFILE_002", "Không tìm thấy hồ sơ", HttpStatus.NOT_FOUND),
	PROFILE_ONLY_FOR_PLAYER("PROFILE_003", "Chỉ tài khoản Cơ thủ mới được tạo hồ sơ player", HttpStatus.FORBIDDEN),

	// Tournament config
	FORMAT_CODE_EXISTS("FORMAT_001", "Mã thể thức đã tồn tại", HttpStatus.CONFLICT),
	FORMAT_NOT_FOUND("FORMAT_002", "Không tìm thấy thể thức", HttpStatus.NOT_FOUND),
	FORMAT_NOT_READY("FORMAT_003", "Cấu hình mặc định của thể thức chưa sẵn sàng", HttpStatus.UNPROCESSABLE_ENTITY),
	FORMAT_IN_USE_CANNOT_EDIT("FORMAT_004",
			"Thể thức đang được dùng bởi giải đấu chưa kết thúc/hủy — không thể sửa cấu hình mặc định để tránh thay đổi giữa chừng",
			HttpStatus.CONFLICT),
	INVALID_FIELD_KEY("FORMAT_004", "Field key không có trong catalog", HttpStatus.BAD_REQUEST),
	INVALID_FIELD_FOR_FORMAT("FORMAT_005", "Field không thuộc thể thức này", HttpStatus.BAD_REQUEST),
	SETUP_INCOMPLETE("FORMAT_006", "Thiết lập thể thức chưa hoàn tất", HttpStatus.UNPROCESSABLE_ENTITY),
	ALREADY_BOOTSTRAPPED("FORMAT_007", "Thể thức đã có cấu hình mặc định", HttpStatus.CONFLICT),
	CONFIG_VALIDATION_FAILED("FORMAT_008", "Xác thực cấu hình giải thất bại", HttpStatus.BAD_REQUEST),
	CONFIG_INCOMPLETE("FORMAT_009", "Cấu hình giải chưa đầy đủ", HttpStatus.UNPROCESSABLE_ENTITY),
	INVALID_STATUS_TRANSITION("FORMAT_010", "Chuyển trạng thái giải không hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY),
	GAME_TYPE_NOT_FOUND("FORMAT_011", "Không tìm thấy loại bi", HttpStatus.NOT_FOUND),
	FORMAT_NOT_SUPPORTED_FOR_DRAW("FORMAT_012",
			"Thể thức này chưa được hỗ trợ bốc thăm — liên hệ quản trị viên", HttpStatus.UNPROCESSABLE_ENTITY),

	// Employee Management
	EMPLOYEE_NOT_FOUND("EMPLOYEE_001", "Không tìm thấy nhân viên", HttpStatus.NOT_FOUND),
	INVALID_EMPLOYEE_ROLE("EMPLOYEE_002", "Người dùng không phải Staff hoặc Manager", HttpStatus.BAD_REQUEST),

	// Registration form template
	REG_FIELD_KEY_EXISTS("REG_FORM_001", "Mã trường đăng ký đã tồn tại", HttpStatus.CONFLICT),
	REG_FIELD_NOT_FOUND("REG_FORM_002", "Không tìm thấy trường đăng ký", HttpStatus.NOT_FOUND),
	REG_FIELD_IN_USE("REG_FORM_003", "Trường đăng ký đang được sử dụng trong template", HttpStatus.CONFLICT),
	REG_TEMPLATE_CODE_EXISTS("REG_FORM_004", "Mã template form đăng ký đã tồn tại", HttpStatus.CONFLICT),
	REG_TEMPLATE_NOT_FOUND("REG_FORM_005", "Không tìm thấy template form đăng ký", HttpStatus.NOT_FOUND),
	REG_TEMPLATE_INACTIVE("REG_FORM_006", "Template form đăng ký đang bị tắt", HttpStatus.UNPROCESSABLE_ENTITY),
	REG_TEMPLATE_INCOMPLETE("REG_FORM_007", "Template form đăng ký chưa có trường nào", HttpStatus.UNPROCESSABLE_ENTITY),
	REG_TEMPLATE_REQUIRED("REG_FORM_008", "Bắt buộc chọn template form đăng ký khi bật đăng ký online", HttpStatus.BAD_REQUEST),
	REG_FORM_VALIDATION_FAILED("REG_FORM_009", "Dữ liệu form đăng ký không hợp lệ", HttpStatus.BAD_REQUEST),
	REGISTRATION_NOT_OPEN("REG_FORM_010", "Giải đấu chưa mở đăng ký", HttpStatus.UNPROCESSABLE_ENTITY),
	REGISTRATION_ALREADY_EXISTS("REG_FORM_011", "Bạn đã đăng ký giải đấu này rồi", HttpStatus.CONFLICT),

	// Match operations
	MATCH_NOT_ASSIGNED("MATCH_001", "Bạn không được phân công làm trọng tài trận này", HttpStatus.FORBIDDEN),
	INVALID_SCORE("MATCH_002", "Điểm số không hợp lệ", HttpStatus.BAD_REQUEST),
	MATCH_NOT_IN_PROGRESS("MATCH_003", "Trận không ở trạng thái đang đấu", HttpStatus.CONFLICT),
	MATCH_SCORE_LOCKED("MATCH_004", "Đã có người đủ điểm, không thể cộng thêm", HttpStatus.CONFLICT),
	MATCH_SCORE_OUT_OF_RANGE("MATCH_005", "Điểm phải nằm trong khoảng 0 đến race-to", HttpStatus.BAD_REQUEST),
	MATCH_WINNER_MUST_BE_RACE_LEADER("MATCH_006", "Người thắng phải là cơ thủ đã đạt điểm thắng", HttpStatus.BAD_REQUEST),
	MATCH_WINNER_NOT_IN_MATCH("MATCH_007", "Người thắng phải thuộc trận này", HttpStatus.BAD_REQUEST),
	MATCH_SCHEDULE_BEFORE_FEEDER("MATCH_008", "Không thể xếp trận này trước khi các trận vòng trước (tứ kết/bán kết…) kết thúc", HttpStatus.CONFLICT),
	MATCH_SCHEDULE_BEFORE_START("MATCH_010", "Không thể xếp giờ thi đấu trước thời gian bắt đầu giải", HttpStatus.BAD_REQUEST),
	REFEREE_NOT_IN_BRANCH("MATCH_011", "Trọng tài phải thuộc chi nhánh tổ chức giải", HttpStatus.BAD_REQUEST),
	/* MATCH_012 (REFEREE_BUSY_ONGOING) và MATCH_013 (REFEREE_TIME_CONFLICT) đã được gỡ:
	 * một trọng tài giờ được phép phụ trách nhiều trận cùng lúc. Đừng dùng lại hai mã này
	 * cho lỗi khác — client cũ có thể vẫn đang đối chiếu theo mã. */
	MATCH_TABLE_TIME_CONFLICT("MATCH_009", "Bàn này đã có trận khác thi đấu trong khung giờ đó — hãy chọn bàn/giờ khác", HttpStatus.CONFLICT),
	MATCH_EARLY_END_NOT_CONFIRMED("MATCH_014", "Chưa ai đạt điểm race-to — cần xác nhận kết thúc sớm (bỏ cuộc/chấn thương)", HttpStatus.CONFLICT),
	MATCH_SCORE_BOTH_REACHED_TARGET("MATCH_015", "Không thể cả hai cơ thủ cùng đạt điểm race-to — chỉ một người thắng ván cuối. Sửa lại tỷ số, hoặc dùng Walkover nếu có cơ thủ bỏ cuộc", HttpStatus.CONFLICT),

	// Tournament participation
	TOURNAMENT_FULL("TOURNAMENT_001", "Giải đấu đã đủ số người tham gia", HttpStatus.CONFLICT),
	PARTICIPANT_INVALID_EXCEL("TOURNAMENT_002", "File không hợp lệ. Vui lòng tải mẫu từ nút \"Tải template\" hoặc upload file .xlsx / .csv", HttpStatus.BAD_REQUEST),
	TOURNAMENT_DATE_INVALID("TOURNAMENT_003", "Ngày tháng giải đấu không hợp lệ", HttpStatus.BAD_REQUEST),
	TOURNAMENT_ROSTER_LOCKED("TOURNAMENT_004", "Giải đã lên bracket — không thể thêm/sửa người tham gia", HttpStatus.CONFLICT),
	TOURNAMENT_MATCHES_NOT_FINISHED("TOURNAMENT_005", "Vẫn còn trận đấu chưa kết thúc — hãy hoàn thành tất cả trận trước khi kết thúc giải", HttpStatus.CONFLICT),
	PARTICIPANT_SEED_DUPLICATE("TOURNAMENT_006", "Số hạt giống này đã được gán cho người tham gia khác", HttpStatus.CONFLICT),
	TOURNAMENT_SEED_COUNT_INSUFFICIENT("TOURNAMENT_007", "Số người đã được gán hạt giống chưa đủ theo cấu hình — hãy gán thêm hạt giống hoặc giảm số lượng cấu hình trước khi bốc thăm", HttpStatus.CONFLICT),
	PARTICIPANT_PARTNER_REQUIRED("TOURNAMENT_008", "Giải đôi yêu cầu nhập đủ thông tin cả 2 thành viên", HttpStatus.BAD_REQUEST),
	PROGRESSIVE_STAGE_NOT_FINISHED("TOURNAMENT_009", "Vẫn còn trận của giai đoạn hiện tại chưa kết thúc — hãy hoàn thành tất cả trước khi chuyển giai đoạn", HttpStatus.CONFLICT),
	PROGRESSIVE_CONFIG_INVALID("TOURNAMENT_010", "Cấu hình vòng tròn loại dần không hợp lệ", HttpStatus.BAD_REQUEST),
	DRAW_SWAP_BOTH_SLOTS_EMPTY("TOURNAMENT_011", "Không thể đổi chỗ hai ô đều đang trống", HttpStatus.BAD_REQUEST),
	DRAW_BRACKET_HAS_EMPTY_MATCH("TOURNAMENT_012", "Bracket có trận vòng 1 không còn cơ thủ nào — hãy sắp xếp lại trước khi xác nhận", HttpStatus.CONFLICT),
	PARTICIPANT_SEED_NOT_CONTIGUOUS("TOURNAMENT_013", "Số hạt giống đang gán không liên tục — phải đủ từ 1 đến hết số người đã seed, không được để trống ở giữa", HttpStatus.CONFLICT),
	PARTICIPANT_SEED_OUT_OF_RANGE("TOURNAMENT_016", "Số hạt giống vượt quá số người tối đa của giải", HttpStatus.BAD_REQUEST),
	TOURNAMENT_NOT_ENOUGH_PARTICIPANTS("TOURNAMENT_014", "Cần tối thiểu 2 người tham gia mới có thể bốc thăm", HttpStatus.CONFLICT),
	DRAW_SWAP_INVALID_SLOT("TOURNAMENT_015", "Tên ô không hợp lệ — chỉ nhận player1 hoặc player2", HttpStatus.BAD_REQUEST),

	// Payment
	PAYMENT_CREATE_FAILED("PAYMENT_001", "Tạo đơn thanh toán thất bại", HttpStatus.BAD_GATEWAY),
	PAYMENT_NOT_FOUND("PAYMENT_002", "Không tìm thấy thông tin thanh toán", HttpStatus.NOT_FOUND),
	PAYMENT_ALREADY_PAID("PAYMENT_003", "Đăng ký này đã được thanh toán", HttpStatus.CONFLICT),
	PAYMENT_NOT_REQUIRED("PAYMENT_004", "Giải đấu này miễn phí — không cần thanh toán", HttpStatus.BAD_REQUEST),
	PAYMENT_INVALID_SIGNATURE("PAYMENT_005", "Chữ ký thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
	REGISTRATION_PAYMENT_NOT_CONFIRMED("PAYMENT_006", "Đăng ký này chưa xác nhận thanh toán — không thể duyệt", HttpStatus.CONFLICT),

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

	// Billiard table pool
	TABLE_NOT_FOUND("TABLE_001", "Không tìm thấy bàn", HttpStatus.NOT_FOUND),
	TABLE_ACCESS_DENIED("TABLE_002", "Bàn không thuộc quyền quản lý của bạn", HttpStatus.FORBIDDEN),
	TABLE_INVALID_IMPORT_FILE("TABLE_003", "File không hợp lệ. Vui lòng tải mẫu từ nút \"Tải template\" hoặc upload file .xlsx / .csv", HttpStatus.BAD_REQUEST),

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
	EMAIL_LOG_NOT_FOUND("EMAIL_010", "Không tìm thấy nhật ký gửi email", HttpStatus.NOT_FOUND),
	EMAIL_REGISTRATION_ID_REQUIRED("EMAIL_011", "Cần chọn 1 đăng ký cụ thể khi gửi tới Người đăng ký", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;
}
