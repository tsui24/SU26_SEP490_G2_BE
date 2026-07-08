package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tournament_status_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@Column(name = "from_status", length = 50, nullable = false)
	private String fromStatus;

	@Column(name = "to_status", length = 50, nullable = false)
	private String toStatus;

	@Column(name = "change_type", length = 20, nullable = false)
	private String changeType;

	/** Null khi thay đổi do hệ thống (job tự động / cảnh báo) thực hiện. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private User changedBy;

	@Column(columnDefinition = "TEXT")
	private String note;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
