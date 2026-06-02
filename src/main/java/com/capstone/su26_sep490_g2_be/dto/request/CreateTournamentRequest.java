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

	@NotBlank
	@Size(max = 255)
	@Schema(description = "Tên giải", example = "CLB Bi-a FPT — Mở rộng 9-Ball 2026")
	private String name;

	@Schema(description = "Mô tả giải")
	private String description;

	@NotBlank
	@Schema(description = "Mã loại bi", example = "9_BALL")
	private String gameType;

	@NotBlank
	@Schema(description = "Mã thể thức", example = "SINGLE_ELIMINATION")
	private String format;

	@NotBlank
	@Schema(description = "Loại người tham gia", example = "SINGLE")
	private String participantType;

	@NotNull
	@Min(2)
	@Schema(description = "Số người tham gia tối đa", example = "16")
	private Integer maxParticipants;

	@DecimalMin("0")
	@Schema(description = "Phí đăng ký", example = "200000")
	private BigDecimal entryFee;

	@DecimalMin("0")
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
}
