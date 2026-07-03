package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStageStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentStageRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import com.capstone.su26_sep490_g2_be.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Sinh bracket + kết quả ngẫu nhiên cho giải mẫu
 * "Giải Vô Địch 9-Ball Toàn Quốc 2026" (64 cơ thủ, single elimination → chung kết).
 *
 * Chạy sau DataInitializer (@Order(1)) và BracketSeedInitializer (@Order(2)).
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class NationalChampionshipMatchSeedInitializer implements CommandLineRunner {

	private static final String TOURNAMENT_NAME = "Giải Vô Địch 9-Ball Toàn Quốc 2026";
	private static final Random RANDOM = new Random(2026L);

	private final TournamentRepository tournamentRepository;
	private final RegistrationRepository registrationRepository;
	private final ParticipantRepository participantRepository;
	private final MatchRepository matchRepository;
	private final TournamentStageRepository stageRepository;
	private final UserRepository userRepository;
	private final BracketGenerationService bracketGenerationService;
	private final MatchService matchService;

	@Override
	@Transactional
	public void run(String... args) {
		Tournament tournament = tournamentRepository.findAll().stream()
				.filter(t -> TOURNAMENT_NAME.equals(t.getName()))
				.findFirst()
				.orElse(null);
		if (tournament == null) {
			log.debug("NationalChampionshipMatchSeedInitializer: chưa có giải '{}' — bỏ qua.", TOURNAMENT_NAME);
			return;
		}

		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) {
			log.warn("NationalChampionshipMatchSeedInitializer: owner@gmail.com chưa tồn tại — bỏ qua.");
			return;
		}

		List<Match> existing = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournament.getId());
		if (!existing.isEmpty() && allMatchesFinished(existing)) {
			log.info("NationalChampionshipMatchSeedInitializer: '{}' đã có đủ kết quả trận đấu — bỏ qua.",
					TOURNAMENT_NAME);
			ensureTournamentCompleted(tournament);
			return;
		}

		syncParticipantsFromRegistrations(tournament);

		long activeCount = participantRepository.countByTournamentIdAndStatus(tournament.getId(), ParticipantStatus.ACTIVE.getValue());
		if (activeCount < 2) {
			log.warn("NationalChampionshipMatchSeedInitializer: '{}' chưa đủ người tham gia ({}).",
					TOURNAMENT_NAME, activeCount);
			return;
		}

		prepareBracket(tournament);
		bracketGenerationService.confirmDraw(tournament.getId());

		tournament.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(tournament);

		int completed = simulateAllMatches(tournament, owner);
		finishTournament(tournament);

		log.info("NationalChampionshipMatchSeedInitializer: '{}' — đã mô phỏng {} trận (tổng {} người).",
				TOURNAMENT_NAME, completed, activeCount);
	}

	private void syncParticipantsFromRegistrations(Tournament tournament) {
		List<Registration> approved = registrationRepository
				.findByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.APPROVED.getValue(), Pageable.unpaged())
				.getContent();

		for (Registration reg : approved) {
			if (participantRepository.existsByRegistrationId(reg.getId())) {
				continue;
			}
			participantRepository.save(Participant.builder()
					.tournament(tournament)
					.registration(reg)
					.participantType(tournament.getParticipantType())
					.displayName(reg.getPlayerFullName())
					.status(ParticipantStatus.ACTIVE.getValue())
					.build());
		}
	}

	private void prepareBracket(Tournament tournament) {
		List<Match> existing = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournament.getId());
		if (!existing.isEmpty()) {
			tournament.setStatus(TournamentStatus.DRAW_PREVIEW.getValue());
			tournamentRepository.save(tournament);
		} else {
			tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED.getValue());
			tournamentRepository.save(tournament);
		}
		bracketGenerationService.generate(tournament.getId());
	}

	private int simulateAllMatches(Tournament tournament, User owner) {
		int completed = 0;
		int guard = 0;
		boolean progress;

		Instant scheduleBase = tournament.getStartAt() != null
				? tournament.getStartAt()
				: Instant.now().minus(30, ChronoUnit.DAYS);

		do {
			progress = false;
			guard++;
			if (guard > 200) {
				log.warn("NationalChampionshipMatchSeedInitializer: dừng sau {} vòng — có thể bracket chưa đủ người.",
						guard);
				break;
			}

			List<Match> matches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournament.getId());
			matches.sort(Comparator
					.comparing(Match::getRoundNo)
					.thenComparing(Match::getPositionNo));

			for (Match match : matches) {
				if (isFinished(match)) {
					continue;
				}
				if (match.getPlayer1() == null || match.getPlayer2() == null) {
					continue;
				}

				Participant winner = RANDOM.nextBoolean() ? match.getPlayer1() : match.getPlayer2();
				Participant loser = winner.getId().equals(match.getPlayer1().getId())
						? match.getPlayer2()
						: match.getPlayer1();

				int raceTo = match.getRaceTo() != null && match.getRaceTo() > 0 ? match.getRaceTo() : 7;
				int loserScore = RANDOM.nextInt(raceTo);

				if (winner.getId().equals(match.getPlayer1().getId())) {
					match.setPlayer1Score(raceTo);
					match.setPlayer2Score(loserScore);
				} else {
					match.setPlayer1Score(loserScore);
					match.setPlayer2Score(raceTo);
				}

				if (match.getScheduledAt() == null) {
					int roundOffset = match.getRoundNo() != null ? match.getRoundNo() : 1;
					match.setScheduledAt(scheduleBase.plus(roundOffset, ChronoUnit.HOURS));
				}

				matchRepository.save(match);
				matchService.completeMatch(match.getId(), winner.getId(), owner.getId());
				completed++;
				progress = true;
			}
		} while (progress);

		return completed;
	}

	private void finishTournament(Tournament tournament) {
		tournament.setStatus(TournamentStatus.COMPLETED.getValue());
		tournamentRepository.save(tournament);

		stageRepository.findByTournamentIdOrderByOrderNoAsc(tournament.getId())
				.forEach(stage -> {
					stage.setStatus(TournamentStageStatus.COMPLETED.getValue());
					stageRepository.save(stage);
				});

		List<Match> matches = matchRepository.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournament.getId());
		Match championFinal = matches.stream()
				.filter(m -> m.getRoundNo() != null && m.getPositionNo() != null)
				.filter(m -> m.getRoundNo().equals(maxRound(matches)) && m.getPositionNo() == 1)
				.findFirst()
				.orElse(null);

		if (championFinal != null && championFinal.getWinner() != null) {
			log.info("NationalChampionshipMatchSeedInitializer: Vô địch '{}' — {}",
					TOURNAMENT_NAME, championFinal.getWinner().getDisplayName());
		}
	}

	private void ensureTournamentCompleted(Tournament tournament) {
		if (!TournamentStatus.COMPLETED.getValue().equals(tournament.getStatus())) {
			tournament.setStatus(TournamentStatus.COMPLETED.getValue());
			tournamentRepository.save(tournament);
		}
		stageRepository.findByTournamentIdOrderByOrderNoAsc(tournament.getId())
				.forEach(stage -> {
					if (!TournamentStageStatus.COMPLETED.getValue().equals(stage.getStatus())) {
						stage.setStatus(TournamentStageStatus.COMPLETED.getValue());
						stageRepository.save(stage);
					}
				});
	}

	private static boolean allMatchesFinished(List<Match> matches) {
		return matches.stream().allMatch(NationalChampionshipMatchSeedInitializer::isFinished);
	}

	private static boolean isFinished(Match match) {
		String status = match.getStatus();
		return MatchStatus.COMPLETED.getValue().equals(status)
				|| MatchStatus.BYE.getValue().equals(status)
				|| MatchStatus.WALKOVER.getValue().equals(status);
	}

	private static int maxRound(List<Match> matches) {
		return matches.stream()
				.map(Match::getRoundNo)
				.filter(r -> r != null)
				.max(Integer::compareTo)
				.orElse(1);
	}
}
