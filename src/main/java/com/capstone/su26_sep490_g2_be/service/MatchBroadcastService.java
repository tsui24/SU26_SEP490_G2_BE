package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.MatchResponse;
import com.capstone.su26_sep490_g2_be.dto.response.MatchUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchBroadcastService {

	private final SimpMessagingTemplate messagingTemplate;

	/**
	 * Cập nhật một trận — topic: /topic/tournament/{tournamentId}/matches
	 */
	public void broadcastMatchUpdate(MatchResponse matchResponse) {
		if (matchResponse == null || matchResponse.getTournamentId() == null) {
			return;
		}
		Long tournamentId = matchResponse.getTournamentId();
		String dest = matchDestination(tournamentId);
		MatchUpdateMessage payload = MatchUpdateMessage.builder()
				.type("MATCH_UPDATE")
				.match(matchResponse)
				.build();
		send(dest, payload, "match " + matchResponse.getId());
	}

	public void broadcastMatchUpdates(List<MatchResponse> matches) {
		if (matches == null) {
			return;
		}
		matches.stream()
				.filter(m -> m != null && m.getTournamentId() != null)
				.forEach(this::broadcastMatchUpdate);
	}

	/**
	 * Đồng bộ toàn bộ bracket — topic: /topic/tournament/{tournamentId}/bracket
	 */
	public void broadcastBracketSync(Long tournamentId, List<MatchResponse> matches) {
		if (tournamentId == null || matches == null) {
			return;
		}
		String dest = bracketDestination(tournamentId);
		MatchUpdateMessage payload = MatchUpdateMessage.builder()
				.type("BRACKET_SYNC")
				.matches(matches)
				.build();
		send(dest, payload, "bracket tournament " + tournamentId + " (" + matches.size() + " matches)");
	}

	private String matchDestination(Long tournamentId) {
		return "/topic/tournament/" + tournamentId + "/matches";
	}

	private String bracketDestination(Long tournamentId) {
		return "/topic/tournament/" + tournamentId + "/bracket";
	}

	private void send(String destination, MatchUpdateMessage payload, String label) {
		try {
			messagingTemplate.convertAndSend(destination, payload);
			log.info("WebSocket {} → {}", label, destination);
		} catch (Exception e) {
			log.warn("WebSocket broadcast failed ({}): {}", label, e.getMessage());
		}
	}
}
