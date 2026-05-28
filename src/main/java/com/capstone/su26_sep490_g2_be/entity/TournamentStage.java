package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tournament_stages",
		uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "order_no"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentStage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@Column(nullable = false)
	private String name;

	@Column(name = "stage_type", length = 50, nullable = false)
	private String stageType;

	@Column(name = "order_no", nullable = false)
	private Integer orderNo;

	@Column(length = 30, nullable = false)
	@Builder.Default
	private String status = "PENDING";

	@Column(name = "pe_round_no")
	private Integer peRoundNo;

	@Column(name = "pe_active_count")
	private Integer peActiveCount;

	@Column(name = "pe_eliminate_count")
	private Integer peEliminateCount;
}
