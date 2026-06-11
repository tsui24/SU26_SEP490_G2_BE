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

	@Size(max = 255, message = "Tên giải tối đa 255 ký tự")
	private String name;

	private String description;

	@Schema(description = "Đổi thể thức — chỉ khi status=DRAFT")
	private String format;

	@Min(value = 2, message = "Số người tham gia tối đa phải từ 2 trở lên")
	private Integer maxParticipants;

	@DecimalMin(value = "0", message = "Phí đăng ký không được âm")
	private BigDecimal entryFee;

	@DecimalMin(value = "0", message = "Tổng giải thưởng không được âm")
	private BigDecimal prizePool;

	private String prizeDescription;

	private Instant registrationDeadline;

	private Instant startAt;

	private Instant endAt;
}
