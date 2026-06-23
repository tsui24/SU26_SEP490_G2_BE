package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@Schema(description = "Giải đấu — mục trong danh sách")
public class TournamentListItemResponse {

	private Long id;
	private String name;
	private String gameType;
	private String format;
	private String formatName;
	private String participantType;
	private String status;
	private Integer maxParticipants;
	private BigDecimal entryFee;
	private Boolean isRegister;
	private Boolean configComplete;
	private Long approvedCount;
	private Instant registrationDeadline;
	private Instant startAt;
	private Instant endAt;
	private Instant createdAt;
}
