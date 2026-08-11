package com.capstone.su26_sep490_g2_be.scheduler;

import com.capstone.su26_sep490_g2_be.component.metrics.SchedulerHealthTracker;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.TournamentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tự động đóng đăng ký khi quá hạn registrationDeadline (giữ nguyên logic mở/đóng thủ công qua
 * patchStatus — job này chỉ xử lý phần tự động theo ngày). Ngoài ra cảnh báo (không đổi trạng
 * thái) khi đã quá hạn startAt mà bracket chưa sẵn sàng, để admin/owner tự xử lý thủ công.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentAutoStatusScheduler {

	private static final List<String> START_NOT_READY_STATUSES = List.of(
			TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
			TournamentStatus.REGISTRATION_CLOSED.getValue(),
			TournamentStatus.DRAW_PREVIEW.getValue());

	private static final DateTimeFormatter DATE_FORMATTER =
			DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.systemDefault());

	private final TournamentRepository tournamentRepository;
	private final TournamentAuditService tournamentAuditService;
	private final SchedulerHealthTracker schedulerHealthTracker;

	@Scheduled(fixedRate = 5 * 60 * 1000)
	@Transactional
	public void autoCloseExpiredRegistrations() {
		schedulerHealthTracker.runTracked("autoCloseExpiredRegistrations", () -> {
			Instant now = Instant.now();
			List<Tournament> due = tournamentRepository.findByStatusAndRegistrationDeadlineBefore(
					TournamentStatus.OPEN_FOR_REGISTRATION.getValue(), now);

			for (Tournament tournament : due) {
				String previousStatus = tournament.getStatus();
				tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED.getValue());
				tournamentRepository.save(tournament);
				tournamentAuditService.recordChange(tournament, previousStatus,
						TournamentStatus.REGISTRATION_CLOSED.getValue(), null,
						"Hệ thống tự động đóng đăng ký do đã quá hạn đăng ký (" +
								DATE_FORMATTER.format(tournament.getRegistrationDeadline()) + ")");
				log.info("Auto-closed registration for tournament id={} ('{}')", tournament.getId(), tournament.getName());
			}
		});
	}

	@Scheduled(fixedRate = 5 * 60 * 1000)
	@Transactional
	public void warnOverdueTournamentStart() {
		schedulerHealthTracker.runTracked("warnOverdueTournamentStart", () -> {
			Instant now = Instant.now();
			List<Tournament> overdue = tournamentRepository.findByStatusInAndStartAtBefore(
					START_NOT_READY_STATUSES, now);

			for (Tournament tournament : overdue) {
				if (tournamentAuditService.hasExistingWarning(tournament.getId())) {
					continue;
				}
				tournamentAuditService.recordWarning(tournament,
						"Đã quá hạn bắt đầu thi đấu (" + DATE_FORMATTER.format(tournament.getStartAt()) +
								") nhưng bracket chưa sẵn sàng — cần xử lý thủ công.");
				log.warn("Tournament id={} ('{}') is past its startAt but bracket is not ready",
						tournament.getId(), tournament.getName());
			}
		});
	}
}
