package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Schema(description = "Kết quả đăng ký giải đấu")
public class TournamentRegistrationResponse {

	private Long id;
	private Long tournamentId;
	private String tournamentName;
	private Long userId;
	private String registrationType;
	private String playerFullName;
	private String playerPhone;
	private String status;
	private String note;
	private Instant createdAt;
	/** Giải có thu phí (entryFee > 0) hay không — quyết định có cần chờ thanh toán trước khi duyệt. */
	private Boolean paymentRequired;
	/** Đã có payment SUCCESS gắn với đăng ký này chưa. Luôn true nếu paymentRequired = false. */
	private Boolean paymentConfirmed;
	private List<FieldValueItem> fieldValues;

	@Getter
	@Builder
	public static class FieldValueItem {
		private String fieldKey;
		private String label;
		private String value;
	}
}
