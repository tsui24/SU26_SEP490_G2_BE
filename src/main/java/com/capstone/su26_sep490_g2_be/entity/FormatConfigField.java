package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "format_config_fields",
		uniqueConstraints = @UniqueConstraint(columnNames = {"format_code", "field_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormatConfigField {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "format_code", length = 50, nullable = false)
	private String formatCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "format_code", insertable = false, updatable = false)
	private TournamentFormatDefinition format;

	@Column(name = "field_key", length = 80, nullable = false)
	private String fieldKey;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "field_key", insertable = false, updatable = false)
	private ConfigFieldDefinition fieldDefinition;

	@Column(name = "default_value", length = 500, nullable = false)
	private String defaultValue;

	@Column(name = "is_required", nullable = false)
	@Builder.Default
	private Boolean isRequired = true;

	@Column(name = "is_visible_to_owner", nullable = false)
	@Builder.Default
	private Boolean isVisibleToOwner = true;

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
