package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "registration_field_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationFieldValue {

	@EmbeddedId
	private RegistrationFieldValueId id;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("registrationId")
	@JoinColumn(name = "registration_id")
	private Registration registration;

	@ManyToOne(fetch = FetchType.LAZY)
	@MapsId("fieldKey")
	@JoinColumn(name = "field_key")
	private RegistrationFieldDefinition fieldDefinition;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String value;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
