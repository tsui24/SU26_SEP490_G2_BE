package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.EmailAutomationRule;
import com.capstone.su26_sep490_g2_be.entity.EmailTemplate;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.EmailRecipientType;
import com.capstone.su26_sep490_g2_be.enums.EmailScope;
import com.capstone.su26_sep490_g2_be.enums.EmailTemplateCategory;
import com.capstone.su26_sep490_g2_be.repository.EmailAutomationRuleRepository;
import com.capstone.su26_sep490_g2_be.repository.EmailTemplateRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed mẫu email + quy tắc tự động mặc định.
 * Chạy sau tất cả seeder khác (@Order 1-4).
 * Idempotent — kiểm tra tồn tại theo code trước khi insert.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class EmailTemplateSeedInitializer implements CommandLineRunner {

	private static final String DEFAULT_ADMIN_EMAIL = "admin@gmail.com";

	private final EmailTemplateRepository templateRepository;
	private final EmailAutomationRuleRepository ruleRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public void run(String... args) {
		User admin = userRepository.findByEmail(DEFAULT_ADMIN_EMAIL).orElse(null);

		seedTemplate(admin, "AUTH_OTP", "Mã OTP xác thực",
				EmailTemplateCategory.SYSTEM,
				"Mã OTP đặt lại mật khẩu - {{system.appName}}",
				"""
				<p>Xin chào,</p>
				<p>Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu. Nhập mã bên dưới để tiếp tục:</p>
				<div style="text-align:center;margin:24px 0;">
				  <span style="display:inline-block;background:#f2f0fd;color:#010851;font-size:28px;font-weight:700;letter-spacing:8px;padding:14px 24px;border-radius:8px;">{{custom.otp}}</span>
				</div>
				<p style="color:#8a8fa3;font-size:13px;">Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.</p>
				""",
				"[\"custom.otp\", \"system.appName\"]");

		EmailTemplate regApproved = seedTemplate(admin, "REGISTRATION_APPROVED", "Đăng ký được duyệt",
				EmailTemplateCategory.TRANSACTIONAL,
				"Đăng ký giải \"{{tournament.name}}\" đã được duyệt",
				"""
				<p>Xin chào {{registration.playerFullName}},</p>
				<p>Đăng ký của bạn cho giải đấu <b>{{tournament.name}}</b> đã được</p>
				<p><span style="display:inline-block;background:#e7f7ec;color:#1d8a4a;font-weight:700;font-size:13px;padding:6px 14px;border-radius:999px;">✔ ĐÃ DUYỆT</span></p>
				<p>Thời gian bắt đầu: <b>{{tournament.startAt}}</b></p>
				<p>Hẹn gặp bạn tại giải đấu!</p>
				""",
				"[\"registration.playerFullName\", \"tournament.name\", \"tournament.startAt\"]");

		EmailTemplate regRejected = seedTemplate(admin, "REGISTRATION_REJECTED", "Đăng ký bị từ chối",
				EmailTemplateCategory.TRANSACTIONAL,
				"Đăng ký giải \"{{tournament.name}}\" không được chấp nhận",
				"""
				<p>Xin chào {{registration.playerFullName}},</p>
				<p>Rất tiếc, đăng ký của bạn cho giải đấu <b>{{tournament.name}}</b> không được chấp nhận.</p>
				<p><span style="display:inline-block;background:#fdeceb;color:#c0392b;font-weight:700;font-size:13px;padding:6px 14px;border-radius:999px;">✘ TỪ CHỐI</span></p>
				<p>Vui lòng liên hệ <a href="mailto:{{system.supportEmail}}">{{system.supportEmail}}</a> nếu bạn cần hỗ trợ thêm.</p>
				""",
				"[\"registration.playerFullName\", \"tournament.name\", \"system.supportEmail\"]");

		EmailTemplate paymentSuccess = seedTemplate(admin, "PAYMENT_SUCCESS", "Thanh toán thành công",
				EmailTemplateCategory.TRANSACTIONAL,
				"Xác nhận thanh toán giải \"{{tournament.name}}\"",
				"""
				<p>Xin chào {{registration.playerFullName}},</p>
				<p>Chúng tôi đã nhận được thanh toán của bạn cho giải đấu <b>{{tournament.name}}</b>.</p>
				<div style="text-align:center;margin:24px 0;">
				  <span style="display:inline-block;background:#e7f7ec;color:#1d8a4a;font-size:24px;font-weight:700;padding:12px 24px;border-radius:8px;">{{payment.amount}}</span>
				</div>
				<p>Cảm ơn bạn đã đăng ký tham dự!</p>
				""",
				"[\"registration.playerFullName\", \"tournament.name\", \"payment.amount\"]");

		EmailTemplate drawDone = seedTemplate(admin, "TOURNAMENT_DRAW_DONE", "Đã bốc thăm xong",
				EmailTemplateCategory.TOURNAMENT,
				"Giải \"{{tournament.name}}\" đã bốc thăm xong",
				"""
				<p>Xin chào {{user.fullName}},</p>
				<p>Giải đấu <b>{{tournament.name}}</b> đã hoàn tất bốc thăm bảng đấu.</p>
				<p>Xem lịch thi đấu của bạn trong hệ thống để biết thêm chi tiết.</p>
				""",
				"[\"user.fullName\", \"tournament.name\"]");

		EmailTemplate matchReminder = seedTemplate(admin, "MATCH_REMINDER", "Nhắc lịch thi đấu",
				EmailTemplateCategory.TOURNAMENT,
				"Nhắc lịch: trận đấu của bạn sắp diễn ra tại \"{{tournament.name}}\"",
				"""
				<p>Xin chào {{user.fullName}},</p>
				<p>Trận đấu vòng <b>{{match.roundNo}}</b> tại giải <b>{{tournament.name}}</b> sắp diễn ra:</p>
				<div style="text-align:center;margin:24px 0;font-size:17px;font-weight:700;color:#010851;">
				  {{match.player1Name}} <span style="color:#9A7AF1;">vs</span> {{match.player2Name}}
				</div>
				<p>Vui lòng có mặt đúng giờ.</p>
				""",
				"[\"user.fullName\", \"match.roundNo\", \"match.player1Name\", \"match.player2Name\", \"tournament.name\"]");

		EmailTemplate refereeAssigned = seedTemplate(admin, "MATCH_REFEREE_ASSIGNED", "Phân công điều hành trận",
				EmailTemplateCategory.TRANSACTIONAL,
				"Bạn được phân công điều hành một trận đấu — {{tournament.name}}",
				"""
				<p>Xin chào,</p>
				<p>Bạn vừa được phân công làm <b>trọng tài</b> điều hành một trận đấu thuộc giải
				<b>{{tournament.name}}</b>.</p>
				<p>Cặp đấu: <b>{{match.player1Name}}</b> vs <b>{{match.player2Name}}</b> (Vòng {{match.roundNo}}).</p>
				<p>Vui lòng đăng nhập ứng dụng, vào mục <b>"Trận của tôi"</b> để theo dõi và điều hành trận đấu.</p>
				""",
				"[\"tournament.name\", \"match.player1Name\", \"match.player2Name\", \"match.roundNo\"]");

		seedTemplate(admin, "MANUAL_BROADCAST", "Thông báo thủ công",
				EmailTemplateCategory.MARKETING,
				"{{custom.subject}}",
				"""
				<p>Xin chào {{user.fullName}},</p>
				<div>{{custom.message}}</div>
				<p>— {{system.appName}}</p>
				""",
				"[\"user.fullName\", \"custom.subject\", \"custom.message\", \"system.appName\"]");

		seedRule(admin, "AUTO_REGISTRATION_APPROVED", "Gửi email khi duyệt đăng ký",
				EmailEventType.REGISTRATION_APPROVED, regApproved, EmailRecipientType.REGISTRATION_USER, true);
		seedRule(admin, "AUTO_REGISTRATION_REJECTED", "Gửi email khi từ chối đăng ký",
				EmailEventType.REGISTRATION_REJECTED, regRejected, EmailRecipientType.REGISTRATION_USER, true);
		seedRule(admin, "AUTO_PAYMENT_SUCCESS", "Gửi email khi thanh toán thành công",
				EmailEventType.PAYMENT_SUCCESS, paymentSuccess, EmailRecipientType.REGISTRATION_USER, true);
		seedRule(admin, "AUTO_TOURNAMENT_DRAW_DONE", "Gửi email khi bốc thăm xong",
				EmailEventType.TOURNAMENT_DRAW_COMPLETED, drawDone, EmailRecipientType.ALL_PARTICIPANTS, true);
		seedRule(admin, "AUTO_MATCH_REMINDER", "Nhắc lịch thi đấu (job định kỳ)",
				EmailEventType.MATCH_SCHEDULED_REMINDER, matchReminder, EmailRecipientType.MATCH_PLAYERS, false);
		seedRule(admin, "AUTO_MATCH_REFEREE_ASSIGNED", "Gửi email khi phân công trọng tài",
				EmailEventType.MATCH_REFEREE_ASSIGNED, refereeAssigned, EmailRecipientType.CUSTOM_LIST, true);

		log.info("Email template + automation rule seed completed");
	}

	private EmailTemplate seedTemplate(User admin, String code, String name, EmailTemplateCategory category,
			String subjectTemplate, String bodyHtmlTemplate, String availableVariablesJson) {
		return templateRepository.findByCode(code).orElseGet(() -> {
			EmailTemplate template = templateRepository.save(EmailTemplate.builder()
					.code(code)
					.name(name)
					.category(category.getValue())
					.scope(EmailScope.GLOBAL.getValue())
					.subjectTemplate(subjectTemplate)
					.bodyHtmlTemplate(bodyHtmlTemplate)
					.availableVariables(availableVariablesJson)
					.isActive(true)
					.createdBy(admin)
					.build());
			log.info("Seeded email template: {}", code);
			return template;
		});
	}

	private void seedRule(User admin, String code, String name, EmailEventType eventType,
			EmailTemplate template, EmailRecipientType recipientType, boolean enabled) {
		if (ruleRepository.existsByCode(code)) {
			return;
		}
		ruleRepository.save(EmailAutomationRule.builder()
				.code(code)
				.name(name)
				.eventType(eventType.getValue())
				.template(template)
				.scope(EmailScope.GLOBAL.getValue())
				.recipientType(recipientType.getValue())
				.isEnabled(enabled)
				.delayMinutes(0)
				.createdBy(admin)
				.build());
		log.info("Seeded automation rule: {}", code);
	}
}
