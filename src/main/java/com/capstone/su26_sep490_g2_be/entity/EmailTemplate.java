package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "email_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String code;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "subject_template", nullable = false, length = 500)
	private String subjectTemplate;

	@Column(name = "body_html_template", nullable = false, columnDefinition = "LONGTEXT")
	private String bodyHtmlTemplate;

	@Column(nullable = false, length = 30)
	private String category;

	@Column(nullable = false, length = 20)
	@Builder.Default
	private String scope = "GLOBAL";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(name = "is_active", nullable = false)
	@Builder.Default
	private Boolean isActive = true;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	/** JSON — mô tả danh sách placeholder khả dụng cho UI, ví dụ ["tournament.name", "user.fullName"]. */
	@Column(name = "available_variables", columnDefinition = "JSON")
	private String availableVariables;
}
