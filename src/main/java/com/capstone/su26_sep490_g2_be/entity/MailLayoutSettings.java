package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cấu hình 1 dòng duy nhất — header/footer HTML dùng chung cho mọi email, chỉnh được qua
 * Admin UI thay vì hardcode trong code. Nội dung có thể chứa placeholder {{system.*}}, được
 * render bằng cùng engine với bodyHtmlTemplate của {@link EmailTemplate}.
 */
@Entity
@Table(name = "mail_layout_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailLayoutSettings extends BaseEntity {

	public static final String DEFAULT_HEADER_HTML = """
			<div style="font-size:22px;font-weight:700;color:#ffffff;letter-spacing:.5px;">&#127920; {{system.appName}}</div>
			<div style="font-size:13px;color:#c6bff5;margin-top:4px;">Giải đấu Bi-a</div>""";

	public static final String DEFAULT_FOOTER_HTML =
			"&copy; {{system.currentYear}} {{system.appName}} &middot; Mọi thắc mắc vui lòng liên hệ "
			+ "<a href=\"mailto:{{system.supportEmail}}\" style=\"color:#5b3fd6;text-decoration:none;\">{{system.supportEmail}}</a>";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "header_html", nullable = false, columnDefinition = "LONGTEXT")
	private String headerHtml;

	@Column(name = "footer_html", nullable = false, columnDefinition = "LONGTEXT")
	private String footerHtml;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by")
	private User updatedBy;
}
