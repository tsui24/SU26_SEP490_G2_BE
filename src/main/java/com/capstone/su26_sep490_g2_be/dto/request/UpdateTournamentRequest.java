package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Schema(description = "Cập nhật thông tin cơ bản giải đấu")
public class UpdateTournamentRequest {

	@Size(max = 255)
	private String name;

	private String description;

	@Schema(description = "Đổi thể thức — chỉ khi status=DRAFT")
	private String format;

	@Min(2)
	private Integer maxParticipants;

	@DecimalMin("0")
	private BigDecimal entryFee;

	@DecimalMin("0")
	private BigDecimal prizePool;

	private String prizeDescription;

	private Instant registrationDeadline;

	private Instant startAt;

	private Instant endAt;

	@Schema(description = "Cho phép người chơi đăng ký")
	private Boolean isRegister;

	@Schema(description = "Template form đăng ký — bắt buộc khi isRegister=true")
	private Long registrationFormTemplateId;
}
