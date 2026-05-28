package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "match_score_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchScoreEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "match_id", nullable = false)
	private Match match;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "scored_by_participant_id")
	private Participant scoredByParticipant;

	@Column(name = "player1_score_after", nullable = false)
	private Integer player1ScoreAfter;

	@Column(name = "player2_score_after", nullable = false)
	private Integer player2ScoreAfter;

	@Column(name = "event_type", length = 30, nullable = false)
	private String eventType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
