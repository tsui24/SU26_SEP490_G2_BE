package com.capstone.su26_sep490_g2_be.entity;

import com.capstone.su26_sep490_g2_be.enums.TableStatus;
import com.capstone.su26_sep490_g2_be.enums.TableType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "billiard_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BilliardTable extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	/** Gợi ý chi nhánh sử dụng — chỉ là tag, không phải ràng buộc occupancy. Null = dùng chung cả chuỗi. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id")
	private Branch branch;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "table_number")
	private Integer tableNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "table_type", length = 20)
	private TableType tableType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private TableStatus status = TableStatus.ACTIVE;
}
