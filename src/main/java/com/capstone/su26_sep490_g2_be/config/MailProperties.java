package com.capstone.su26_sep490_g2_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {
	private String fromAddress;
	private String fromName = "Giải đấu Bi-a BTMS";
	private String supportEmail = "support@btms.vn";
	private String appName = "BTMS";
	private String frontendBaseUrl = "https://biliardtournament.cloud";
	/**
	 * Chặn SMTP/push trong lúc CommandLineRunner seed chạy. Seeder đi qua service thật nên
	 * publish hàng chục MailDomainEvent — nếu không chặn thì mỗi lần restart spam hộp thư.
	 * Tắt bằng {@code MAIL_SUPPRESS_DURING_STARTUP=false} khi cố ý test mail lúc seed.
	 */
	private boolean suppressDuringStartup = true;
	private Async async = new Async();

	/** Trang chi tiết giải công khai trên FE — `{frontendBaseUrl}/event/{id}`. */
	public String tournamentPublicUrl(Long tournamentId) {
		if (tournamentId == null) {
			return null;
		}
		String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base + "/event/" + tournamentId;
	}

	@Getter
	@Setter
	public static class Async {
		private int corePoolSize = 2;
		private int maxPoolSize = 5;
		private int queueCapacity = 100;
	}
}
