package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tournaments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tournament extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "thumbnail_url", length = 1000)
	private String thumbnailUrl;

	@Column(name = "banner_url", length = 1000)
	private String bannerUrl;

	@Column(name = "game_type", length = 50, nullable = false)
	private String gameType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "game_type", insertable = false, updatable = false)
	private GameTypeDefinition gameTypeDefinition;

	@Column(name = "participant_type", length = 30, nullable = false)
	private String participantType;

	@Column(length = 50, nullable = false)
	private String format;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "format", insertable = false, updatable = false)
	private TournamentFormatDefinition formatDefinition;

	@Column(length = 50, nullable = false)
	@Builder.Default
	private String status = "DRAFT";

	@Column(name = "max_participants", nullable = false)
	private Integer maxParticipants;

	@Column(name = "entry_fee", precision = 15, scale = 2)
	@Builder.Default
	private BigDecimal entryFee = BigDecimal.ZERO;

	@Column(name = "prize_pool", precision = 15, scale = 2)
	private BigDecimal prizePool;

	@Column(name = "prize_description", columnDefinition = "TEXT")
	private String prizeDescription;

	@Column(name = "registration_deadline")
	private Instant registrationDeadline;

	@Column(name = "start_at")
	private Instant startAt;

	@Column(name = "end_at")
	private Instant endAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "is_show_tournament")
	private boolean isShowTournament;

	@Column(name = "is_public_ratio")
	private boolean isPublicRatio;

	@Column(name = "is_register")
	private boolean isRegister;
}
