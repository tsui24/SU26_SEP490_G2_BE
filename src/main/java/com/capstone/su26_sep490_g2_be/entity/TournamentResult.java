package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tournament_results",
		uniqueConstraints = {
				@UniqueConstraint(columnNames = {"tournament_id", "participant_id"}),
				@UniqueConstraint(columnNames = {"tournament_id", "final_rank"})
		})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "participant_id", nullable = false)
	private Participant participant;

	@Column(name = "final_rank", nullable = false)
	private Integer finalRank;

	@Column(name = "prize_amount", precision = 15, scale = 2)
	@Builder.Default
	private BigDecimal prizeAmount = BigDecimal.ZERO;

	@Column(name = "points_earned", nullable = false)
	@Builder.Default
	private Integer pointsEarned = 0;

	@Column(columnDefinition = "TEXT")
	private String note;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recorded_by")
	private User recordedBy;
}
