package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@Schema(description = "Chi tiết giải đấu")
public class TournamentDetailResponse {

	private Long id;
	private String name;
	private String description;
	private String gameType;
	private String format;
	private String formatName;
	private String participantType;
	private String status;
	private Integer maxParticipants;
	private BigDecimal entryFee;
	private BigDecimal prizePool;
	private String prizeDescription;
	private Instant registrationDeadline;
	private Instant startAt;
	private Instant endAt;
	private Boolean configComplete;
	private Boolean isRegister;
	private Boolean isPublicRatio;
	private Boolean isShowTournament;
	private Long approvedCount;
	private Integer remainingSlots;
	private Long registrationFormTemplateId;
	private String registrationFormTemplateCode;
	private String registrationFormTemplateName;
	private ConfigSummary configSummary;
	private String thumbnailUrl;
	private String bannerUrl;
	private BranchVenueResponse venue;
	@Getter
	@Builder
	public static class ConfigSummary {

		private String seedingMethod;
		private Integer bracketSize;
		private Boolean thirdPlaceMatch;
		private String breakRule;
		private Integer finalRaceTo;
	}
}
