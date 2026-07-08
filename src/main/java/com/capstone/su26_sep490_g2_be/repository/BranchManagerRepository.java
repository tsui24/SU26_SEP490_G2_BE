package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.BranchManager;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchManagerRepository extends JpaRepository<BranchManager, Long> {

	boolean existsByBranchIdAndManagerId(Long branchId, Long managerId);

	List<BranchManager> findByManagerId(Long managerId);

	List<BranchManager> findByBranchId(Long branchId);

	void deleteByManagerId(Long managerId);

	void deleteByBranchIdAndManagerId(Long branchId, Long managerId);
}
