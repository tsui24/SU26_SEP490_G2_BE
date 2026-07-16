package com.capstone.su26_sep490_g2_be.service;

import lombok.Builder;

/**
 * Sự kiện phát ra khi giải đấu chuyển sang OPEN_FOR_REGISTRATION.
 * Listener (Facebook, v.v.) lắng nghe để tự động đăng bài truyền thông.
 */
@Builder
public record TournamentPublishedEvent(
		Long tournamentId
) {
}
