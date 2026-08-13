package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
		name = "participants",
		indexes = {
				@Index(name = "idx_participants_tournament_status", columnList = "tournament_id, status")
		},
		uniqueConstraints = {
				// NULL không tính trùng (InnoDB) — chỉ chặn 2 participant CÙNG giải trùng seed_no thật.
				// Bắt buộc phải có ràng buộc này: bản MANUAL/seedNo cũ đã bị gỡ đúng vì thiếu nó, dữ
				// liệu seed bị trùng lộn xộn không ai phát hiện ra cho tới khi bốc thăm.
				@UniqueConstraint(name = "uq_participants_tournament_seed", columnNames = {"tournament_id", "seed_no"})
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

	/**
	 * Số hạt giống BQT tự nhập (1 = mạnh nhất) — chỉ dùng khi giải chọn
	 * {@link com.capstone.su26_sep490_g2_be.enums.SeedingMethod#SEED}. Null = chưa xếp hạt giống,
	 * người này được xáo ngẫu nhiên và xếp SAU toàn bộ nhóm đã có hạt giống lúc bốc thăm — cho
	 * phép seed một phần (VD chỉ 8/32 người có hạt giống thật).
	 */
	@Column(name = "seed_no")
	private Integer seedNo;

	@Column(length = 30, nullable = false)
	@Builder.Default
	private String status = "ACTIVE";

	@Column(name = "avtar_url", length = 1000)
	private String avtarUrl;
}
