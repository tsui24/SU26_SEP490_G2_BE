package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Profile Player")
public class PlayerProfileResponse {

	private Long userId;
	private String fullName;
	private String displayName;
	private String avatarUrl;
	private LocalDate dateOfBirth;
	private String gender;
	private String billiardRank;
	private String bio;
}
