package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Schema(description = "Tạo giải đấu — wizard bước 1")
public class CreateTournamentRequest {

	@NotBlank(message = "Tên giải không được để trống")
	@Size(max = 255, message = "Tên giải tối đa 255 ký tự")
	@Schema(description = "Tên giải", example = "CLB Bi-a FPT — Mở rộng 9-Ball 2026")
	private String name;

	@Schema(description = "Mô tả giải")
	private String description;

	@Size(max = 1000, message = "URL thumbnail tối đa 1000 ký tự")
	@Schema(description = "Ảnh đại diện giải")
	private String thumbnailUrl;

	@Size(max = 1000, message = "URL banner tối đa 1000 ký tự")
	@Schema(description = "Ảnh banner giải")
	private String bannerUrl;

	@NotBlank(message = "Mã loại bi không được để trống")
	@Schema(description = "Mã loại bi", example = "9_BALL")
	private String gameType;

	@NotBlank(message = "Mã thể thức không được để trống")
	@Schema(description = "Mã thể thức", example = "SINGLE_ELIMINATION")
	private String format;

	@NotBlank(message = "Loại người tham gia không được để trống")
	@Schema(description = "Loại người tham gia", example = "SINGLE")
	private String participantType;

	@NotNull(message = "Số người tham gia tối đa không được để trống")
	@Min(value = 2, message = "Số người tham gia tối đa phải từ 2 trở lên")
	@Schema(description = "Số người tham gia tối đa", example = "16")
	private Integer maxParticipants;

	@DecimalMin(value = "0", message = "Phí đăng ký không được âm")
	@Schema(description = "Phí đăng ký", example = "200000")
	private BigDecimal entryFee;

	@DecimalMin(value = "0", message = "Tổng giải thưởng không được âm")
	@Schema(description = "Tổng giải thưởng", example = "8000000")
	private BigDecimal prizePool;

	@Schema(description = "Mô tả giải thưởng")
	private String prizeDescription;

	@Schema(description = "Hạn đăng ký")
	private Instant registrationDeadline;

	@Schema(description = "Thời gian bắt đầu")
	private Instant startAt;

	@Schema(description = "Thời gian kết thúc")
	private Instant endAt;

	@Schema(description = "Cho phép người chơi đăng ký", example = "true")
	private Boolean isRegister;

	@Schema(description = "Template form đăng ký — bắt buộc khi isRegister=true")
	private Long registrationFormTemplateId;

	@NotNull(message = "Vui lòng chọn chi nhánh tổ chức")
	@Schema(description = "Chi nhánh tổ chức giải", example = "1")
	private Long branchId;
}
