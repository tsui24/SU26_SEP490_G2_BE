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

	/** Số bàn thi đấu của giải — auto-scheduler gán bàn 1..tableCount. */
	@Column(name = "table_count")
	@Builder.Default
	private Integer tableCount = 1;

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
	@Builder.Default
	private Boolean isShowTournament = false;

	@Column(name = "is_public_ratio")
	@Builder.Default
	private Boolean isPublicRatio = false;

	@Column(name = "is_register")
	@Builder.Default
	private Boolean isRegister = false;

	@Column(name = "registration_form_template_id")
	private Long registrationFormTemplateId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "registration_form_template_id", insertable = false, updatable = false)
	private RegistrationFormTemplate registrationFormTemplate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id")
	private Branch branch;

	@Column(name = "venue_name")
	private String venueName;

	@Column(name = "venue_address", length = 500)
	private String venueAddress;
}
