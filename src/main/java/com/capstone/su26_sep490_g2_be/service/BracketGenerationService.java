package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.DrawResultResponse;

public interface BracketGenerationService {

    DrawResultResponse generate(Long tournamentId);
}
