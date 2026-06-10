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
	private List<FieldValueItem> fieldValues;

	@Getter
	@Builder
	public static class FieldValueItem {
		private String fieldKey;
		private String label;
		private String value;
	}
}
