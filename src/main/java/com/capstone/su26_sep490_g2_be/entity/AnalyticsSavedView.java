package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Báo cáo tùy chỉnh Owner/Manager lưu lại từ tab "Khám phá" (chọn dimension/metric/filter/loại biểu đồ).
 * Thuộc về đúng người tạo (user_id) — Owner và Manager có view riêng, không dùng chung.
 */
@Entity
@Table(name = "analytics_saved_views")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSavedView {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 120)
	private String name;

	/** JSON hóa nguyên trạng AnalyticsQueryRequest — nạp lại là chạy được ngay. */
	@Column(name = "config_json", columnDefinition = "TEXT", nullable = false)
	private String configJson;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
