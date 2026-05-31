package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "format_race_to_rules",
		uniqueConstraints = @UniqueConstraint(columnNames = {"format_code", "round_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormatRaceToRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "format_code", length = 50, nullable = false)
	private String formatCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "format_code", insertable = false, updatable = false)
	private TournamentFormatDefinition format;

	@Column(name = "round_key", length = 50, nullable = false)
	private String roundKey;

	@Column(length = 255)
	private String label;

	@Column(name = "bracket_phase", length = 30, nullable = false)
	private String bracketPhase;

	@Column(name = "race_to", nullable = false)
	private Integer raceTo;

	@Column(name = "sort_order", nullable = false)
	@Builder.Default
	private Integer sortOrder = 0;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
