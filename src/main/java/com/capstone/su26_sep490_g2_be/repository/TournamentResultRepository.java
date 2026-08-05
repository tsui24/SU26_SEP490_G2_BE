package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.TournamentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TournamentResultRepository extends JpaRepository<TournamentResult, Long> {

	@Query("""
		SELECT tr FROM TournamentResult tr
		LEFT JOIN FETCH tr.participant p
		LEFT JOIN FETCH p.registration r
		LEFT JOIN FETCH r.user
		WHERE tr.tournament.id = :tournamentId
		ORDER BY tr.finalRank ASC
		""")
	List<TournamentResult> findByTournamentIdOrderByFinalRankAsc(@Param("tournamentId") Long tournamentId);

	Optional<TournamentResult> findByTournamentIdAndParticipantId(Long tournamentId, Long participantId);

	boolean existsByTournamentIdAndParticipantId(Long tournamentId, Long participantId);

	@Query("""
		SELECT tr FROM TournamentResult tr
		LEFT JOIN FETCH tr.participant p
		LEFT JOIN FETCH p.registration r
		LEFT JOIN FETCH r.user
		WHERE tr.tournament.id IN :tournamentIds
		""")
	List<TournamentResult> findByTournamentIdIn(@Param("tournamentIds") List<Long> tournamentIds);

	@Query("""
		SELECT tr FROM TournamentResult tr
		JOIN FETCH tr.tournament
		WHERE tr.participant.id IN (
		    SELECT p.id FROM Participant p
		    JOIN p.registration r
		    WHERE r.user.id = :userId
		)
		ORDER BY tr.finalRank ASC
		""")
	List<TournamentResult> findByParticipantRegistrationUserId(@Param("userId") Long userId);

	/**
	 * Bảng xếp hạng điểm tích lũy — gộp toàn bộ kết quả của một cơ thủ trong khoảng thời gian.
	 *
	 * <p>Chỉ tính giải đã {@code COMPLETED} và participant {@code SINGLE}: giải đôi bị loại vì
	 * đồng đội (PARTNER) luôn được tạo với {@code user = null}, không có tài khoản để cộng điểm.
	 *
	 * <p><b>Chỉ cơ thủ ĐĂNG KÝ ONLINE mới lên bảng xếp hạng.</b> {@code JOIN p.registration r
	 * JOIN r.user u} loại bỏ participant do BQT thêm tay hoặc import Excel, vì hai luồng đó tạo
	 * {@code Registration} với {@code user = null} (xem {@code ParticipantController} và
	 * {@code ParticipantExcelServiceImpl}) — không có tài khoản thì không biết cộng điểm cho ai.
	 * Đây là giới hạn đã được chấp nhận, không phải bug: muốn đổi thì phải cho phép gắn tài khoản
	 * lúc thêm participant thủ công.
	 *
	 * <p>Mốc thời gian ưu tiên {@code tournament.endAt} (ngày giải kết thúc — đúng nghiệp vụ),
	 * fallback {@code recordedAt} khi giải không khai ngày kết thúc.
	 */
	@Query("""
		SELECT u.id                          AS userId,
		       pr.fullName                   AS profileFullName,
		       pr.displayName                AS profileDisplayName,
		       MAX(p.displayName)            AS participantName,
		       pr.avatarUrl                  AS avatarUrl,
		       COALESCE(SUM(tr.pointsEarned), 0) AS totalPoints,
		       COUNT(tr.id)                  AS tournamentsPlayed,
		       COALESCE(SUM(CASE WHEN tr.finalRank = 1 THEN 1 ELSE 0 END), 0) AS championCount,
		       COALESCE(SUM(CASE WHEN tr.finalRank <= 3 THEN 1 ELSE 0 END), 0) AS top3Count,
		       COALESCE(SUM(tr.prizeAmount), 0) AS totalPrizeAmount
		FROM TournamentResult tr
		    JOIN tr.participant p
		    JOIN tr.tournament t
		    JOIN p.registration r
		    JOIN r.user u
		    LEFT JOIN u.profile pr
		WHERE t.status = 'COMPLETED'
		  AND p.participantType = 'SINGLE'
		  AND COALESCE(t.endAt, tr.recordedAt) >= :from
		  AND COALESCE(t.endAt, tr.recordedAt) < :to
		GROUP BY u.id, pr.fullName, pr.displayName, pr.avatarUrl
		ORDER BY COALESCE(SUM(tr.pointsEarned), 0) DESC,
		         COALESCE(SUM(tr.prizeAmount), 0) DESC,
		         COUNT(tr.id) ASC
		""")
	List<LeaderboardRow> aggregateLeaderboard(@Param("from") Instant from, @Param("to") Instant to);

	/**
	 * Projection cho {@link #aggregateLeaderboard}.
	 *
	 * <p>Tên hiển thị tách làm 3 nguồn để service tự chọn cái đầu tiên có giá trị — không gộp
	 * bằng COALESCE trong JPQL vì trộn cột GROUP BY với hàm aggregate dễ lỗi tùy dialect.
	 */
	interface LeaderboardRow {
		Long getUserId();

		String getProfileFullName();

		String getProfileDisplayName();

		String getParticipantName();

		String getAvatarUrl();

		Long getTotalPoints();

		Long getTournamentsPlayed();

		Long getChampionCount();

		Long getTop3Count();

		BigDecimal getTotalPrizeAmount();
	}

	/** Toàn bộ kết quả của các giải đã COMPLETED — dùng cho backfill tính lại điểm. */
	@Query("""
		SELECT tr FROM TournamentResult tr
		JOIN FETCH tr.tournament t
		WHERE t.status = 'COMPLETED'
		""")
	List<TournamentResult> findAllOfCompletedTournaments();
}
