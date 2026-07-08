package com.capstone.su26_sep490_g2_be.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class MailAsyncConfig {

	private final MailProperties mailProperties;

	@Bean("mailTaskExecutor")
	public TaskExecutor mailTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(mailProperties.getAsync().getCorePoolSize());
		executor.setMaxPoolSize(mailProperties.getAsync().getMaxPoolSize());
		executor.setQueueCapacity(mailProperties.getAsync().getQueueCapacity());
		executor.setThreadNamePrefix("mail-async-");
		executor.initialize();
		return executor;
	}
}
