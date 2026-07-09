package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.Match;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.entity.UserProfile;
import com.capstone.su26_sep490_g2_be.enums.MatchStatus;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.repository.MatchRepository;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BracketGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Seed dữ liệu để test Module 7 — Live Scoring.
 *
 * <p>Chạy sau BracketSeedInitializer (@Order 2). Idempotent.
 *
 * <p>Staff1 được gán trận trên nhiều giải (hôm nay / ngày mai) để test lọc ngày + tìm tên giải.
 *
 * <p>Tài khoản test:
 * <ul>
 *   <li>manager@gmail.com / manager123</li>
 *   <li>staff1@gmail.com / staff123</li>
 *   <li>staff2@gmail.com / staff123</li>
 * </ul>
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class LiveScoringSeedInitializer implements CommandLineRunner {

	public static final String DEMO_TOURNAMENT_NAME =
			"BTMS [Test] Double Elimination — 8 Cơ Thủ (8-Ball)";
	public static final String TOURNAMENT_9BALL =
			"BTMS [Test] Double Elimination — 10 Cơ Thủ (9-Ball)";
	public static final String TOURNAMENT_ROUND_ROBIN =
			"BTMS [Test] Vòng Tròn — 8 Cơ Thủ (Top 4 Playoff)";
	public static final String TOURNAMENT_CUT_SE =
			"BTMS [Test] Nhánh Thắng/Thua — 16 Cơ Thủ → Last 4 (8-Ball, tỉ lệ 1/4)";

	public static final String MANAGER_EMAIL = "manager@gmail.com";
	public static final String STAFF1_EMAIL = "staff1@gmail.com";
	public static final String STAFF2_EMAIL = "staff2@gmail.com";
	public static final String DEMO_PASSWORD = "staff123";
	public static final String MANAGER_PASSWORD = "manager123";

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final TournamentRepository tournamentRepository;
	private final MatchRepository matchRepository;
	private final BracketGenerationService bracketGenerationService;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(String... args) {
		User owner = userRepository.findByEmail("owner@gmail.com").orElse(null);
		if (owner == null) {
			log.debug("LiveScoringSeedInitializer: chưa có owner — bỏ qua.");
			return;
		}

		seedEmployee(
				MANAGER_EMAIL, MANAGER_PASSWORD, RoleCode.MANAGER,
				"Nguyễn Minh Quản", "Quản lý giải");
		User staff1 = seedEmployee(
				STAFF1_EMAIL, DEMO_PASSWORD, RoleCode.STAFF,
				"Trần Văn Trọng", "Trọng tài Trần");
		User staff2 = seedEmployee(
				STAFF2_EMAIL, DEMO_PASSWORD, RoleCode.STAFF,
				"Lê Thị Mai", "TT Mai");

		Tournament demo = findTournament(DEMO_TOURNAMENT_NAME);
		if (demo == null) {
			log.warn("LiveScoringSeedInitializer: chưa có giải '{}' — bỏ qua.",
					DEMO_TOURNAMENT_NAME);
			return;
		}

		List<Match> demoMatches = ensureBracket(demo, owner);

		boolean alreadyAssigned = demoMatches.stream().anyMatch(m -> m.getAssignedStaff() != null);
		if (!alreadyAssigned) {
			assignDemoTables(demo, demoMatches, staff1, staff2);
		} else {
			log.info("LiveScoringSeedInitializer: giải demo đã có phân công — bỏ qua gán bàn 1–4.");
		}

		enrichSchedules(demo, staff1, staff2);
		seedExtraTournamentAssignments(owner, staff1);

		log.info("""
				══════════════════════════════════════════════════════════
				 Live Scoring — dữ liệu test đã sẵn sàng
				──────────────────────────────────────────────────────────
				 Giải chính: {} (id={})
				 Thêm giải: 9-Ball · Vòng tròn · DE→Last4 (staff1)
				 Lịch: hôm nay + ngày mai (Asia/Ho_Chi_Minh)
				──────────────────────────────────────────────────────────
				 Manager : {} / {}
				 Staff 1 : {} / {}  (nhiều giải)
				 Staff 2 : {} / {}  (demo bàn 3–4)
				──────────────────────────────────────────────────────────
				 API: GET /api/v1/staff/matches?tournamentName=Vòng
				══════════════════════════════════════════════════════════
				""",
				DEMO_TOURNAMENT_NAME, demo.getId(),
				MANAGER_EMAIL, MANAGER_PASSWORD,
				STAFF1_EMAIL, DEMO_PASSWORD,
				STAFF2_EMAIL, DEMO_PASSWORD);
	}

	private void assignDemoTables(Tournament tournament, List<Match> matches,
			User staff1, User staff2) {
		tournament.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(tournament);

		List<Match> readyR1 = readyWinnersR1(matches);
		if (readyR1.size() < 4) {
			log.warn("LiveScoringSeedInitializer: không đủ trận W-R1 có đủ 2 cơ thủ ({}).",
					readyR1.size());
			return;
		}

		setupMatch(readyR1.get(0), staff1, 1, MatchStatus.PENDING.getValue(), 0, 0);
		setupMatch(readyR1.get(1), staff1, 2, MatchStatus.IN_PROGRESS.getValue(), 2, 1);
		setupMatch(readyR1.get(2), staff2, 3, MatchStatus.IN_PROGRESS.getValue(), 3, 2);
		setupMatch(readyR1.get(3), staff2, 4, MatchStatus.IN_PROGRESS.getValue(), 5, 4);

		int tableNo = 5;
		for (int i = 4; i < readyR1.size() && tableNo <= 8; i++, tableNo++) {
			Match m = readyR1.get(i);
			m.setTableNo(tableNo);
			m.setAssignedStaff(null);
			matchRepository.save(m);
		}
	}

	/** Gán giờ hôm nay / ngày mai cho trận đã phân công trên giải demo. */
	private void enrichSchedules(Tournament demo, User staff1, User staff2) {
		List<Match> assigned = matchRepository
				.findByTournamentIdOrderByRoundNoAscPositionNoAsc(demo.getId())
				.stream()
				.filter(m -> m.getAssignedStaff() != null)
				.sorted(Comparator
						.comparing((Match m) -> m.getTableNo() == null ? 999 : m.getTableNo())
						.thenComparing(Match::getId))
				.toList();

		if (assigned.isEmpty()) return;

		LocalDate today = LocalDate.now(VN);
		LocalDate tomorrow = today.plusDays(1);

		for (Match m : assigned) {
			Integer table = m.getTableNo();
			if (table == null) continue;
			boolean forStaff1 = staff1.getId().equals(m.getAssignedStaff().getId());
			if (forStaff1) {
				if (table == 1) {
					m.setScheduledAt(atVn(today, 14, 30));
				} else if (table == 2) {
					m.setScheduledAt(atVn(today, 10, 0));
				}
			} else if (staff2.getId().equals(m.getAssignedStaff().getId())) {
				if (table == 3) {
					m.setScheduledAt(atVn(today, 15, 0));
				} else if (table == 4) {
					m.setScheduledAt(atVn(tomorrow, 9, 30));
				}
			}
			matchRepository.save(m);
		}
	}

	/**
	 * Gán thêm trận cho staff1 trên các giải khác (hôm nay + ngày mai)
	 * để test tìm kiếm tên giải / lọc ngày.
	 */
	private void seedExtraTournamentAssignments(User owner, User staff1) {
		assignFromTournament(owner, staff1, TOURNAMENT_9BALL, 11,
				MatchStatus.PENDING.getValue(), 0, 0,
				atVn(LocalDate.now(VN), 16, 0));

		assignFromTournament(owner, staff1, TOURNAMENT_ROUND_ROBIN, 12,
				MatchStatus.PENDING.getValue(), 0, 0,
				atVn(LocalDate.now(VN).plusDays(1), 10, 0));

		assignFromTournament(owner, staff1, TOURNAMENT_CUT_SE, 13,
				MatchStatus.IN_PROGRESS.getValue(), 1, 0,
				atVn(LocalDate.now(VN), 11, 30));

		assignFromTournament(owner, staff1, TOURNAMENT_9BALL, 14,
				MatchStatus.COMPLETED.getValue(), 5, 3,
				atVn(LocalDate.now(VN).minusDays(1), 18, 0));
	}

	private void assignFromTournament(User owner, User staff, String tournamentName,
			int tableNo, String status, int score1, int score2,
			java.time.Instant scheduledAt) {
		Tournament t = findTournament(tournamentName);
		if (t == null) {
			log.warn("LiveScoringSeedInitializer: chưa có giải '{}' — bỏ qua gán thêm.",
					tournamentName);
			return;
		}

		List<Match> matches = ensureBracket(t, owner);

		// Idempotent: đã có trận của staff này trên giải → chỉ refresh schedule/status nếu cùng bàn
		boolean already = matches.stream().anyMatch(m ->
				m.getAssignedStaff() != null
						&& staff.getId().equals(m.getAssignedStaff().getId())
						&& tableNo == (m.getTableNo() == null ? -1 : m.getTableNo()));
		if (already) {
			matches.stream()
					.filter(m -> m.getAssignedStaff() != null
							&& staff.getId().equals(m.getAssignedStaff().getId())
							&& Integer.valueOf(tableNo).equals(m.getTableNo()))
					.findFirst()
					.ifPresent(m -> {
						m.setScheduledAt(scheduledAt);
						m.setStatus(status);
						m.setPlayer1Score(score1);
						m.setPlayer2Score(score2);
						matchRepository.save(m);
					});
			return;
		}

		Match pick = matches.stream()
				.filter(this::hasBothPlayers)
				.filter(m -> m.getAssignedStaff() == null)
				.sorted(Comparator
						.comparing((Match m) -> m.getRoundNo() == null ? 99 : m.getRoundNo())
						.thenComparing(m -> m.getPositionNo() == null ? 99 : m.getPositionNo()))
				.findFirst()
				.orElse(null);

		if (pick == null) {
			log.warn("LiveScoringSeedInitializer: không còn trận trống trên '{}'.", tournamentName);
			return;
		}

		t.setStatus(TournamentStatus.IN_PROGRESS.getValue());
		tournamentRepository.save(t);

		pick.setAssignedStaff(staff);
		pick.setTableNo(tableNo);
		pick.setStatus(status);
		pick.setPlayer1Score(score1);
		pick.setPlayer2Score(score2);
		pick.setScheduledAt(scheduledAt);
		if (MatchStatus.COMPLETED.getValue().equals(status) && pick.getPlayer1() != null) {
			pick.setWinner(pick.getPlayer1());
			pick.setLoser(pick.getPlayer2());
		}
		matchRepository.save(pick);
		log.info("LiveScoringSeedInitializer: gán {} bàn {} → {} ({})",
				staff.getEmail(), tableNo, tournamentName, status);
	}

	private List<Match> ensureBracket(Tournament tournament, User owner) {
		List<Match> matches = matchRepository
				.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournament.getId());
		if (!matches.isEmpty()) return matches;

		bracketGenerationService.generate(tournament.getId(), owner.getId());
		bracketGenerationService.confirmDraw(tournament.getId(), owner.getId());
		matches = matchRepository
				.findByTournamentIdOrderByRoundNoAscPositionNoAsc(tournament.getId());
		log.info("LiveScoringSeedInitializer: đã bốc thăm '{}' ({} trận).",
				tournament.getName(), matches.size());
		return matches;
	}

	private List<Match> readyWinnersR1(List<Match> matches) {
		return matches.stream()
				.filter(this::hasBothPlayers)
				.filter(m -> "WINNERS".equals(m.getBracketType())
						|| "GROUP".equals(m.getBracketType())
						|| m.getBracketType() == null
						|| m.getBracketType().startsWith("W"))
				.filter(m -> m.getRoundNo() != null && m.getRoundNo() == 1)
				.sorted(Comparator.comparing(Match::getPositionNo))
				.toList();
	}

	private Tournament findTournament(String name) {
		return tournamentRepository.findAll().stream()
				.filter(t -> name.equals(t.getName()))
				.findFirst()
				.orElse(null);
	}

	private java.time.Instant atVn(LocalDate date, int hour, int minute) {
		return ZonedDateTime.of(date, LocalTime.of(hour, minute), VN).toInstant();
	}

	private User seedEmployee(String email, String rawPassword, RoleCode roleCode,
			String fullName, String displayName) {
		return userRepository.findByEmail(email).orElseGet(() -> {
			Role role = roleRepository.findByCode(roleCode.getCode())
					.orElseThrow(() -> new IllegalStateException("Role not found: " + roleCode));
			User user = User.builder()
					.email(email)
					.passwordHash(passwordEncoder.encode(rawPassword))
					.role(role)
					.status(UserStatus.ACTIVE)
					.build();
			UserProfile profile = UserProfile.builder()
					.user(user)
					.fullName(fullName)
					.displayName(displayName)
					.build();
			user.setProfile(profile);
			userRepository.save(user);
			log.info("LiveScoringSeedInitializer: tạo tài khoản {} ({})", email, roleCode.getCode());
			return user;
		});
	}

	private void setupMatch(Match match, User staff, int tableNo,
			String status, int score1, int score2) {
		match.setAssignedStaff(staff);
		match.setTableNo(tableNo);
		match.setStatus(status);
		match.setPlayer1Score(score1);
		match.setPlayer2Score(score2);
		matchRepository.save(match);
	}

	private boolean hasBothPlayers(Match m) {
		return m.getPlayer1() != null && m.getPlayer2() != null
				&& !Boolean.TRUE.equals(m.getIsBye());
	}
}
