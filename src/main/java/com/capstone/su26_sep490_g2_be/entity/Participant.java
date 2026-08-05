package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
		name = "participants",
		indexes = {
				@Index(name = "idx_participants_tournament_status", columnList = "tournament_id, status")
		})
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

	/**
	 * Ảnh chụp {@link com.capstone.su26_sep490_g2_be.enums.BilliardRank} tại thời điểm tạo
	 * participant. Cố ý KHÔNG đọc trực tiếp từ hồ sơ cơ thủ lúc bốc thăm: cơ thủ đổi hạng giữa
	 * chừng không được phép làm đổi bracket của giải đang diễn ra.
	 *
	 * <p>Null / giá trị lạ được coi là {@code UNKNOWN}.
	 */
	@Column(name = "billiard_rank", length = 20)
	private String billiardRank;

	@Column(length = 30, nullable = false)
	@Builder.Default
	private String status = "ACTIVE";

	@Column(name = "avtar_url", length = 1000)
	private String avtarUrl;
}
