package com.capstone.su26_sep490_g2_be.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chặn gửi mail/push từ DataInitializer và các seeder khác. Listener mail chạy
 * {@code @Async} AFTER_COMMIT nên sự kiện seed có thể còn nằm trong queue sau
 * {@link ApplicationReadyEvent} — phải vứt queue đó trước khi mở khóa gửi thật.
 */
@Slf4j
@Component
public class StartupMailGuard {

	private final MailProperties mailProperties;
	private final TaskExecutor mailTaskExecutor;
	private final AtomicBoolean suppress = new AtomicBoolean(true);

	public StartupMailGuard(MailProperties mailProperties,
			@Qualifier("mailTaskExecutor") TaskExecutor mailTaskExecutor) {
		this.mailProperties = mailProperties;
		this.mailTaskExecutor = mailTaskExecutor;
	}

	public boolean isSuppressed() {
		return mailProperties.isSuppressDuringStartup() && suppress.get();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		if (!mailProperties.isSuppressDuringStartup()) {
			suppress.set(false);
			return;
		}

		int dropped = 0;
		if (mailTaskExecutor instanceof ThreadPoolTaskExecutor pool) {
			ThreadPoolExecutor raw = pool.getThreadPoolExecutor();
			dropped = raw.getQueue().size();
			raw.getQueue().clear();
			int spins = 0;
			while (raw.getActiveCount() > 0 && spins++ < 50) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		suppress.set(false);
		log.info("Mail/push đã mở lại sau seed (đã bỏ {} task còn treo trong hàng đợi startup)", dropped);
	}
}
