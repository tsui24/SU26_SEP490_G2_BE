package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stage_id", nullable = false)
	private TournamentStage stage;

	@Column(name = "bracket_type", length = 30, nullable = false)
	private String bracketType;

	@Column(name = "round_no", nullable = false)
	private Integer roundNo;

	@Column(name = "position_no", nullable = false)
	private Integer positionNo;

	@Column(name = "match_code", length = 50)
	private String matchCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "player1_participant_id")
	private Participant player1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "player2_participant_id")
	private Participant player2;

	@Column(name = "player1_score", nullable = false)
	@Builder.Default
	private Integer player1Score = 0;

	@Column(name = "player2_score", nullable = false)
	@Builder.Default
	private Integer player2Score = 0;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "winner_participant_id")
	private Participant winner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "loser_participant_id")
	private Participant loser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "next_match_win_id")
	private Match nextMatchWin;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "next_match_lose_id")
	private Match nextMatchLose;

	@Column(name = "win_slot", length = 20)
	private String winSlot;

	@Column(name = "lose_slot", length = 20)
	private String loseSlot;

	@Column(name = "scheduled_at")
	private Instant scheduledAt;

	@Column(name = "race_to", nullable = false)
	private Integer raceTo;

	@Column(length = 30, nullable = false)
	@Builder.Default
	private String status = "PENDING";

	@Column(name = "is_bye", nullable = false)
	@Builder.Default
	private Boolean isBye = false;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_staff_id")
	private User assignedStaff;

	/** Số bàn tự do — legacy fallback, giữ đồng bộ từ {@code table.tableNumber} khi gán qua pool. */
	@Column(name = "table_no")
	private Integer tableNo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "table_id")
	private BilliardTable table;

	/** Giờ kết thúc dự kiến — auto-scheduler tính theo race-to + loại bi. */
	@Column(name = "estimated_end_at")
	private Instant estimatedEndAt;

	/** true = owner đã gán bàn/giờ thủ công → auto-scheduler bỏ qua, không ghi đè. */
	@Column(name = "schedule_locked", nullable = false)
	@Builder.Default
	private Boolean scheduleLocked = false;
}
