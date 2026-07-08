package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "email_automation_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAutomationRule extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String code;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "event_type", nullable = false, length = 50)
	private String eventType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "template_id", nullable = false)
	private EmailTemplate template;

	@Column(nullable = false, length = 20)
	@Builder.Default
	private String scope = "GLOBAL";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id")
	private Tournament tournament;

	@Column(name = "recipient_type", nullable = false, length = 30)
	private String recipientType;

	@Column(name = "is_enabled", nullable = false)
	@Builder.Default
	private Boolean isEnabled = true;

	@Column(name = "delay_minutes", nullable = false)
	@Builder.Default
	private Integer delayMinutes = 0;

	/** JSON — điều kiện lọc tuỳ chọn, ví dụ {"tournamentFormat": "DOUBLE_ELIMINATION"}. */
	@Column(columnDefinition = "JSON")
	private String conditions;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;
}
