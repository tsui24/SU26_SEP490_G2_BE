package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "registration_form_template_fields",
		uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "field_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationFormTemplateField {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "template_id", nullable = false)
	private Long templateId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "template_id", insertable = false, updatable = false)
	private RegistrationFormTemplate template;

	@Column(name = "field_key", length = 80, nullable = false)
	private String fieldKey;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "field_key", insertable = false, updatable = false)
	private RegistrationFieldDefinition fieldDefinition;

	@Column(name = "label_override")
	private String labelOverride;

	@Column(name = "description_override", columnDefinition = "TEXT")
	private String descriptionOverride;

	@Column(length = 200)
	private String placeholder;

	@Column(name = "validation_regex", length = 500)
	private String validationRegex;

	@Column(name = "default_value", length = 500)
	private String defaultValue;

	@Column(name = "is_required", nullable = false)
	@Builder.Default
	private Boolean isRequired = true;

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
