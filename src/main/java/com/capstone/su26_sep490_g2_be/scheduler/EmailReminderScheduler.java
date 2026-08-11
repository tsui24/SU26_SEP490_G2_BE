package com.capstone.su26_sep490_g2_be.scheduler;

import com.capstone.su26_sep490_g2_be.component.metrics.SchedulerHealthTracker;
import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.impl.MailContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Nhắc lịch qua email — cả 2 job dùng cửa sổ thời gian đúng bằng chu kỳ chạy (fixedRate) để mỗi
 * tournament/match chỉ rơi vào đúng 1 lần quét, không cần bảng đánh dấu "đã gửi" riêng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailReminderScheduler {

	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final MatchRepository matchRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final MailContextBuilder mailContextBuilder;
	private final SchedulerHealthTracker schedulerHealthTracker;

	/** Nhắc trước 24h khi giải sắp đóng đăng ký — quét cửa sổ 1h, chạy mỗi giờ. */
	@Scheduled(fixedRate = 60 * 60 * 1000)
	@Transactional
	public void sendRegistrationClosingSoonReminders() {
		schedulerHealthTracker.runTracked("sendRegistrationClosingSoonReminders", () -> {
			Instant now = Instant.now();
			List<Tournament> tournaments = tournamentRepository.findByStatusAndRegistrationDeadlineBetween(
					TournamentStatus.OPEN_FOR_REGISTRATION.getValue(),
					now.plus(23, ChronoUnit.HOURS), now.plus(24, ChronoUnit.HOURS));

			for (Tournament tournament : tournaments) {
				List<Registration> registrations = registrationRepository
						.findByTournamentId(tournament.getId(), Pageable.unpaged()).getContent();
				List<MailRecipient> recipients = registrations.stream()
						.filter(r -> !RegistrationStatus.CANCELLED.getValue().equals(r.getStatus())
								&& !RegistrationStatus.REJECTED.getValue().equals(r.getStatus()))
						.map(Registration::getUser)
						.filter(Objects::nonNull)
						.filter(u -> u.getEmail() != null)
						.distinct()
						.map(u -> new MailRecipient(u.getId(), u.getEmail()))
						.toList();
				if (recipients.isEmpty()) {
					continue;
				}

				Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
				mailContextBuilder.putTournament(variables, tournament);
				eventPublisher.publishEvent(MailDomainEvent.builder()
						.eventType(EmailEventType.TOURNAMENT_REGISTRATION_CLOSING_SOON)
						.tournamentId(tournament.getId())
						.variables(variables)
						.explicitRecipients(recipients)
						.entityKey("TOURNAMENT-CLOSING-SOON-" + tournament.getId())
						.build());
				log.info("Queued registration-closing-soon reminder for tournament id={}", tournament.getId());
			}
		});
	}

	/** Nhắc trận đấu sắp diễn ra — quét cửa sổ 30 phút (từ +30' đến +60'), chạy mỗi 30 phút. */
	@Scheduled(fixedRate = 30 * 60 * 1000)
	@Transactional
	public void sendMatchStartingSoonReminders() {
		schedulerHealthTracker.runTracked("sendMatchStartingSoonReminders", () -> {
			Instant now = Instant.now();
			List<Match> matches = matchRepository.findByStatusAndScheduledAtBetween(
					MatchStatus.PENDING.getValue(), now.plus(30, ChronoUnit.MINUTES), now.plus(60, ChronoUnit.MINUTES));

			for (Match match : matches) {
				List<MailRecipient> recipients = Stream.of(match.getPlayer1(), match.getPlayer2())
						.filter(Objects::nonNull)
						.map(p -> p.getRegistration() != null ? p.getRegistration().getUser() : null)
						.filter(Objects::nonNull)
						.filter(u -> u.getEmail() != null)
						.distinct()
						.map(u -> new MailRecipient(u.getId(), u.getEmail()))
						.toList();
				if (recipients.isEmpty()) {
					continue;
				}

				Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
				mailContextBuilder.putMatch(variables, match);
				eventPublisher.publishEvent(MailDomainEvent.builder()
						.eventType(EmailEventType.MATCH_SCHEDULED_REMINDER)
						.tournamentId(match.getTournament().getId())
						.variables(variables)
						.explicitRecipients(recipients)
						.entityKey("MATCH-REMINDER-" + match.getId())
						.build());
				log.info("Queued match-starting-soon reminder for match id={}", match.getId());
			}
		});
	}
}
