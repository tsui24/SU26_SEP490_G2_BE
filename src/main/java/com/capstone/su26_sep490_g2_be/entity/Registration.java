package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "registrations",
		uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	/** Null với đăng ký MANUAL (import Excel) — người chơi walk-in chưa có tài khoản hệ thống. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = true)
	private User user;

	@Column(name = "registration_type", length = 30, nullable = false)
	private String registrationType;

	@Column(name = "player_full_name", nullable = false)
	private String playerFullName;

	@Column(name = "player_phone", length = 20)
	private String playerPhone;

	@Column(length = 50, nullable = false)
	@Builder.Default
	private String status = "PENDING_PAYMENT";

	@Column(columnDefinition = "TEXT")
	private String note;

	@Column(name = "rejected_reason", columnDefinition = "TEXT")
	private String rejectedReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approved_by")
	private User approvedBy;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "rejected_at")
	private Instant rejectedAt;
}
