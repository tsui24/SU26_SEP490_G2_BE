package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "registration_id")
	private Registration registration;

	@Column(name = "participant_type", length = 30, nullable = false)
	private String participantType;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "seed_no")
	private Integer seedNo;

	@Column(length = 30, nullable = false)
	@Builder.Default
	private String status = "ACTIVE";
}
