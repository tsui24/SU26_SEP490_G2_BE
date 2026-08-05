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


	@Column(name = "config_snapshot_json", columnDefinition = "JSON")
	private String configSnapshotJson;

	/** Optimistic lock — tránh 2 request PUT /config cùng lúc ghi đè âm thầm lên nhau. */
	@Version
	@Column(name = "version")
	private Long version;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
