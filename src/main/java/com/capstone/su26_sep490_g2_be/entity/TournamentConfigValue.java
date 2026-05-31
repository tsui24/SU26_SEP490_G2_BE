package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tournament_config_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentConfigValue {

	@EmbeddedId
	private TournamentConfigValueId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("tournamentId")
	@JoinColumn(name = "tournament_id")
	private Tournament tournament;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("fieldKey")
	@JoinColumn(name = "field_key")
	private ConfigFieldDefinition fieldDefinition;

	@Column(name = "value", length = 500, nullable = false)
	private String value;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
