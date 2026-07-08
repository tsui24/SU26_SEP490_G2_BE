package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardStatsResponse {
    private TournamentStatsResponse tournaments;
    private RegistrationStatsResponse registrations;
    private ParticipantStatsResponse participants;
    private RevenueStatsResponse revenue;
    private MatchStatsResponse matches;
}
