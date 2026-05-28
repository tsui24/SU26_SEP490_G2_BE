package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "player_yearly_summaries",
		uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerYearlySummary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private Integer year;

	@Column(name = "tournaments_played", nullable = false)
	@Builder.Default
	private Integer tournamentsPlayed = 0;

	@Column(name = "champion_count", nullable = false)
	@Builder.Default
	private Integer championCount = 0;

	@Column(name = "runner_up_count", nullable = false)
	@Builder.Default
	private Integer runnerUpCount = 0;

	@Column(name = "top3_count", nullable = false)
	@Builder.Default
	private Integer top3Count = 0;

	@Column(name = "total_prize_amount", precision = 15, scale = 2, nullable = false)
	@Builder.Default
	private BigDecimal totalPrizeAmount = BigDecimal.ZERO;

	@Column(name = "total_points", nullable = false)
	@Builder.Default
	private Integer totalPoints = 0;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
