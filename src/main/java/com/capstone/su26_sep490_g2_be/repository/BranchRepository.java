package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long>, JpaSpecificationExecutor<Branch> {

	Page<Branch> findByOwnerId(Long ownerId, Pageable pageable);

	Optional<Branch> findByIdAndOwnerId(Long id, Long ownerId);

	List<Branch> findByOwnerId(Long ownerId);

	List<Branch> findByIdInAndStatus(List<Long> ids, BranchStatus status);

	List<Branch> findByOwnerIdAndStatus(Long ownerId, BranchStatus status);

	long countByStatus(BranchStatus status);
}
