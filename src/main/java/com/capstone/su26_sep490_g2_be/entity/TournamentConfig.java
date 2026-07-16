package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tournament_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentConfig {

	@Id
	@Column(name = "tournament_id")
	private Long tournamentId;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "tournament_id")
	private Tournament tournament;

	@Column(name = "format_code", length = 50)
	private String formatCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "format_code", insertable = false, updatable = false)
	private TournamentFormatDefinition formatDefinition;

	@Column(name = "seeding_method", length = 30, nullable = false)
	@Builder.Default
	private String seedingMethod = "RANDOM";

	/** Số người dự kiến được gán hạt giống — chỉ có ý nghĩa khi seedingMethod != RANDOM. */
	@Column(name = "seed_count")
	private Integer seedCount;

	@Column(name = "config_snapshot_json", columnDefinition = "JSON")
	private String configSnapshotJson;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
