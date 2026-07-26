package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.BranchManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BranchManagerRepository extends JpaRepository<BranchManager, Long> {

	boolean existsByBranchIdAndManagerId(Long branchId, Long managerId);

	List<BranchManager> findByManagerId(Long managerId);

	@Query("SELECT bm FROM BranchManager bm LEFT JOIN FETCH bm.branch WHERE bm.manager.id IN :managerIds")
	List<BranchManager> findByManagerIdIn(@Param("managerIds") List<Long> managerIds);

	List<BranchManager> findByBranchId(Long branchId);

	void deleteByManagerId(Long managerId);

	void deleteByBranchIdAndManagerId(Long branchId, Long managerId);
}
