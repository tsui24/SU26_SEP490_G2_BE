package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Envelope WebSocket — FE subscribe {@code /topic/tournament/{id}/matches} hoặc {@code /bracket}.
 */
@Getter
@Builder
public class MatchUpdateMessage {

	/** MATCH_UPDATE | BRACKET_SYNC */
	private String type;

	private MatchResponse match;

	private List<MatchResponse> matches;
}
