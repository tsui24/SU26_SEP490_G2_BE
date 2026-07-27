package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchManagerRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchAccessServiceImpl implements BranchAccessService {

	private final BranchRepository branchRepository;
	private final BranchManagerRepository branchManagerRepository;

	@Override
	public boolean isChainWideManager(User manager) {
		return Boolean.TRUE.equals(manager.getManageAllBranches());
	}

	@Override
	public List<Long> getAccessibleBranchIds(User manager) {
		if (isChainWideManager(manager)) {
			Long ownerId = manager.getOwner() != null ? manager.getOwner().getId() : null;
			if (ownerId == null) {
				return List.of();
			}
			return branchRepository.findByOwnerId(ownerId).stream().map(Branch::getId).toList();
		}
		return branchManagerRepository.findByManagerId(manager.getId()).stream()
				.map(bm -> bm.getBranch().getId())
				.toList();
	}

	@Override
	public boolean canManagerAccessBranch(User manager, Long branchId) {
		if (branchId == null) {
			return false;
		}
		return getAccessibleBranchIds(manager).contains(branchId);
	}

	@Override
	public boolean canManagerCreateTournamentAt(User manager, Long branchId) {
		if (!canManagerAccessBranch(manager, branchId)) {
			return false;
		}
		return branchRepository.findById(branchId)
				.map(branch -> branch.getStatus() == BranchStatus.ACTIVE)
				.orElse(false);
	}

	@Override
	public boolean canActorAccessBranch(User actor, Long branchId) {
		String roleCode = actor.getRole().getCode();
		if ("OWNER".equals(roleCode)) {
			return true;
		}
		if ("MANAGER".equals(roleCode)) {
			return canManagerAccessBranch(actor, branchId);
		}
		return false;
	}

	@Override
	public List<Branch> getAccessibleBranches(User manager) {
		List<Long> accessibleIds = getAccessibleBranchIds(manager);
		if (accessibleIds.isEmpty()) {
			return List.of();
		}
		return branchRepository.findByIdInAndStatus(accessibleIds, BranchStatus.ACTIVE);
	}
}
