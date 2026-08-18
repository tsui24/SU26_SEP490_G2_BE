package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tournament_finance_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentFinanceEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	/** {@link com.capstone.su26_sep490_g2_be.enums.FinanceEntryType} — INCOME/EXPENSE. */
	@Column(name = "entry_type", length = 10, nullable = false)
	private String entryType;

	/** Nội dung tự do — BQT nhập gì cũng được (tiền tài trợ, tiền thuê bàn thêm...),
	 * hệ thống không có danh mục thu/chi cố định để liệt kê hết. */
	@Column(name = "label", length = 255, nullable = false)
	private String label;

	/** Luôn dương — dấu +/- suy ra từ entryType, không lưu số âm. */
	@Column(name = "amount", precision = 15, scale = 2, nullable = false)
	private BigDecimal amount;

	@Column(name = "note", length = 500)
	private String note;

	/** Ngày phát sinh khoản thu/chi — có thể khác ngày nhập liệu. */
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
