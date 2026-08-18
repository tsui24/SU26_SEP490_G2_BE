package com.capstone.su26_sep490_g2_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Schema(description = "Thêm/sửa 1 khoản thu hoặc chi của giải đấu")
public class TournamentFinanceEntryRequest {

	@NotBlank(message = "Loại khoản thu/chi không được để trống")
	private String entryType;

	@NotBlank(message = "Nội dung không được để trống")
	@Size(max = 255, message = "Nội dung tối đa 255 ký tự")
	private String label;

	@NotNull(message = "Số tiền không được để trống")
	@DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
	private BigDecimal amount;

	@Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
	private String note;

	/** Ngày phát sinh — bỏ trống thì lấy thời điểm nhập liệu (now()). */
	private Instant occurredAt;
}
