package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    @Query("""
        SELECT p FROM Participant p
        LEFT JOIN FETCH p.tournament
        LEFT JOIN FETCH p.registration r
        LEFT JOIN FETCH r.user
        WHERE p.tournament.id = :tournamentId
        ORDER BY p.id ASC
        """)
    List<Participant> findByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("""
        SELECT p FROM Participant p
        LEFT JOIN FETCH p.tournament
        LEFT JOIN FETCH p.registration r
        LEFT JOIN FETCH r.user
        WHERE p.tournament.id = :tournamentId AND p.status = :status
        """)
    List<Participant> findByTournamentIdAndStatus(
            @Param("tournamentId") Long tournamentId,
            @Param("status") String status);

    @Query("""
        SELECT p FROM Participant p
        LEFT JOIN FETCH p.tournament
        LEFT JOIN FETCH p.registration r
        LEFT JOIN FETCH r.user
        WHERE p.id = :id
        """)
    Optional<Participant> findByIdWithDetails(@Param("id") Long id);

    long countByTournamentIdAndStatus(Long tournamentId, String status);

    @Query("""
        SELECT p.tournament.id AS tournamentId, COUNT(p) AS total
        FROM Participant p
        WHERE p.tournament.id IN :tournamentIds AND p.status = :status
        GROUP BY p.tournament.id
        """)
    List<TournamentParticipantCount> countGroupedByTournamentIdInAndStatus(
            @Param("tournamentIds") List<Long> tournamentIds,
            @Param("status") String status);

    interface TournamentParticipantCount {
        Long getTournamentId();
        Long getTotal();
    }

    boolean existsByTournamentIdAndStatusAndSeedNo(Long tournamentId, String status, Integer seedNo);

    boolean existsByRegistrationId(Long registrationId);

    Optional<Participant> findByRegistrationId(Long registrationId);

    @Query("""
        SELECT p FROM Participant p
        LEFT JOIN FETCH p.tournament
        LEFT JOIN FETCH p.registration r
        LEFT JOIN FETCH r.user
        WHERE p.tournament.id IN :tournamentIds
        """)
    List<Participant> findByTournamentIdIn(@Param("tournamentIds") List<Long> tournamentIds);

    @Query("""
        SELECT p FROM Participant p
        LEFT JOIN FETCH p.tournament
        LEFT JOIN FETCH p.registration r
        LEFT JOIN FETCH r.user
        WHERE r.user.id = :userId
        """)
    List<Participant> findByRegistrationUserId(@Param("userId") Long userId);
}
