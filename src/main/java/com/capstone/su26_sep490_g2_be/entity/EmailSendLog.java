package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "email_send_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSendLog extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "template_id")
	private EmailTemplate template;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rule_id")
	private EmailAutomationRule rule;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id")
	private Tournament tournament;

	@Column(name = "trigger_type", nullable = false, length = 20)
	private String triggerType;

	@Column(name = "recipient_email", nullable = false, length = 255)
	private String recipientEmail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipient_user_id")
	private User recipientUser;

	@Column(name = "subject_rendered", nullable = false, length = 500)
	private String subjectRendered;

	@Column(name = "body_rendered", columnDefinition = "LONGTEXT")
	private String bodyRendered;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "sent_at")
	private Instant sentAt;

	/** Chống gửi trùng cho cùng 1 sự kiện + entity trong khoảng thời gian ngắn. */
	@Column(name = "idempotency_key", length = 150)
	private String idempotencyKey;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;
}
