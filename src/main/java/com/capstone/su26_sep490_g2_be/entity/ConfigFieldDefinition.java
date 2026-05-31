package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "config_field_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigFieldDefinition {

	@Id
	@Column(name = "field_key", length = 80)
	private String fieldKey;

	@Column(nullable = false)
	private String label;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "data_type", length = 20, nullable = false)
	private String dataType;

	@Column(name = "field_scope", length = 30, nullable = false)
	private String fieldScope;

	@Column(name = "enum_options", columnDefinition = "JSON")
	private String enumOptions;

	@Column(name = "ui_component", length = 30, nullable = false)
	private String uiComponent;

	@Column(name = "min_value")
	private Integer minValue;

	@Column(name = "max_value")
	private Integer maxValue;

	@Column(name = "is_active", nullable = false)
	@Builder.Default
	private Boolean isActive = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
