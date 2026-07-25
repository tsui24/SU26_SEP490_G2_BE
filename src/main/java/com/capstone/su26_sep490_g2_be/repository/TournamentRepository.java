package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Tournament;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TournamentRepository extends JpaRepository<Tournament, Long>,
        JpaSpecificationExecutor<Tournament> {

    @Override
    @EntityGraph(attributePaths = { "branch", "createdBy" })
    List<Tournament> findAll();

    @Override
    @EntityGraph(attributePaths = { "branch", "createdBy" })
    Page<Tournament> findAll(org.springframework.data.jpa.domain.Specification<Tournament> spec, Pageable pageable);

    Page<Tournament> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = { "branch", "createdBy" })
    List<Tournament> findByCreatedById(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tournament t WHERE t.id = :id")
    Optional<Tournament> findByIdWithLock(@Param("id") Long id);

    List<Tournament> findByStatusAndRegistrationDeadlineBefore(String status, Instant deadline);

    List<Tournament> findByStatusInAndStartAtBefore(List<String> statuses, Instant startAt);

    List<Tournament> findByStatusAndRegistrationDeadlineBetween(String status, Instant from, Instant to);
}
