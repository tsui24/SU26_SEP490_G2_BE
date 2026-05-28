package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tournament_format_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentFormatDefinition {

	@Id
	@Column(length = 50)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "handler_key", length = 100, nullable = false, unique = true)
	private String handlerKey;

	@Column(name = "schema_version", length = 20)
	@Builder.Default
	private String schemaVersion = "1.0";

	@Column(name = "is_active", nullable = false)
	@Builder.Default
	private Boolean isActive = true;

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
