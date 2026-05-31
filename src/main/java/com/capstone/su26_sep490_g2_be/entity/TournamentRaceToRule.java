package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tournament_race_to_rules",
		uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "round_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentRaceToRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@Column(name = "round_key", length = 50, nullable = false)
	private String roundKey;

	@Column(name = "bracket_phase", length = 30, nullable = false)
	private String bracketPhase;

	@Column(name = "race_to", nullable = false)
	private Integer raceTo;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
