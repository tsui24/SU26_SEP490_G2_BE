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
	private String frontendBaseUrl = "http://localhost:3000";
	private Async async = new Async();

	@Getter
	@Setter
	public static class Async {
		private int corePoolSize = 2;
		private int maxPoolSize = 5;
		private int queueCapacity = 100;
	}
}
