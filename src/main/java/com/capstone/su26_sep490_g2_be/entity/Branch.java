package com.capstone.su26_sep490_g2_be.entity;

import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, length = 500)
	private String address;

	@Column(length = 20)
	private String phone;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private BranchStatus status = BranchStatus.ACTIVE;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	/**
	 * Danh sách MinIO object key của ảnh chi nhánh, lưu dạng JSON — theo cùng cách
	 * {@code GameTypeDefinition.compatibleTableTypes} lưu list, không cần entity con.
	 */
	@Column(name = "image_keys", columnDefinition = "JSON")
	private String imageKeys;
}
