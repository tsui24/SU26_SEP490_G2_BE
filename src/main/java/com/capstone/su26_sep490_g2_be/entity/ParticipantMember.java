package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "participant_members",
		uniqueConstraints = @UniqueConstraint(columnNames = {"participant_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "participant_id", nullable = false)
	private Participant participant;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	@Column(length = 20)
	private String phone;

	@Column(length = 30, nullable = false)
	private String role;
}
