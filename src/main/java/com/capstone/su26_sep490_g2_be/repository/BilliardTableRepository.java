package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.BilliardTable;
import com.capstone.su26_sep490_g2_be.enums.TableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BilliardTableRepository extends JpaRepository<BilliardTable, Long>, JpaSpecificationExecutor<BilliardTable> {

	Optional<BilliardTable> findByIdAndOwnerId(Long id, Long ownerId);

	@EntityGraph(attributePaths = { "owner", "branch" })
	List<BilliardTable> findByOwnerIdAndStatus(Long ownerId, TableStatus status);

	@Override
	@EntityGraph(attributePaths = { "owner", "branch" })
	Page<BilliardTable> findAll(Specification<BilliardTable> spec, Pageable pageable);
}
