package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RegistrationFieldValueId implements Serializable {

	@Column(name = "registration_id")
	private Long registrationId;

	@Column(name = "field_key", length = 80)
	private String fieldKey;
}
