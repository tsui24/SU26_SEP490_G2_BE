package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.BranchManager;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BranchManagerRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link BranchAccessServiceImpl}.
 *
 * <p>Mirrors the <b>BranchAccessService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-39.3 BR-03, UC-08.1, UC-38 BR-02/BR-03.
 *
 * <p>This class answers "may this actor touch this branch" for every branch-scoped feature in
 * the system, so its branches are covered exhaustively.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · BranchAccessService — UC-38, UC-39")
class BranchAccessServiceImplTest {

	@Mock BranchRepository branchRepository;
	@Mock BranchManagerRepository branchManagerRepository;

	@InjectMocks BranchAccessServiceImpl accessService;

	private static final Long OWNER_ID = 100L;

	private static User owner() {
		return User.builder().id(OWNER_ID).role(Role.builder().code("OWNER").build()).build();
	}

	private static User actorWithRole(String roleCode) {
		return User.builder().id(300L).role(Role.builder().code(roleCode).build()).build();
	}

	/** A manager holding chain-wide access under the given owner. */
	private static User chainWideManager(User theOwner) {
		return User.builder()
				.id(200L)
				.role(Role.builder().code("MANAGER").build())
				.manageAllBranches(true)
				.owner(theOwner)
				.build();
	}

	/** A manager scoped to individually assigned branches. */
	private static User scopedManager() {
		return User.builder()
				.id(200L)
				.role(Role.builder().code("MANAGER").build())
				.manageAllBranches(false)
				.owner(owner())
				.build();
	}

	private static Branch branch(Long id, BranchStatus status, User theOwner) {
		return Branch.builder().id(id).name("Chi nhánh " + id).status(status).owner(theOwner).build();
	}

	private static BranchManager assignment(Long branchId) {
		return BranchManager.builder().branch(branch(branchId, BranchStatus.ACTIVE, owner())).build();
	}

	/** Stubs the assignment table so the manager is scoped to the given branch ids. */
	private void stubAssignments(Long... branchIds) {
		when(branchManagerRepository.findByManagerId(200L))
				.thenReturn(java.util.Arrays.stream(branchIds).map(BranchAccessServiceImplTest::assignment).toList());
	}

	// ══════════════════════════ isChainWideManager ══════════════════════════

	@Test
	@DisplayName("TC-001 · Manager granted chain-wide access")
	void TC001_isChainWideManager_true() {
		assertTrue(accessService.isChainWideManager(chainWideManager(owner())));
	}

	@Test
	@DisplayName("TC-002 · Manager scoped to specific branches")
	void TC002_isChainWideManager_false() {
		assertFalse(accessService.isChainWideManager(scopedManager()));
	}

	@Test
	@DisplayName("TC-003 · Flag left null is read as no chain-wide access")
	void TC003_isChainWideManager_nullFlag() {
		User legacy = User.builder().id(200L).manageAllBranches(null).build();

		// Defaulting a null to true here would hand a legacy manager the whole chain
		assertFalse(accessService.isChainWideManager(legacy));
	}

	// ══════════════════════════ getAccessibleBranchIds ══════════════════════════

	@Test
	@DisplayName("TC-004 · Chain-wide manager reaches every branch of their Owner")
	void TC004_getAccessibleBranchIds_chainWide() {
		User manager = chainWideManager(owner());
		when(branchRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(
				branch(1L, BranchStatus.ACTIVE, owner()),
				branch(2L, BranchStatus.ACTIVE, owner())));

		List<Long> ids = accessService.getAccessibleBranchIds(manager);

		assertEquals(List.of(1L, 2L), ids);
		// Read from the Owner, not from the assignment table
		verifyNoInteractions(branchManagerRepository);
	}

	@Test
	@DisplayName("TC-005 · Chain-wide manager with no Owner attached")
	void TC005_getAccessibleBranchIds_chainWideWithoutOwner() {
		User manager = User.builder().id(200L).manageAllBranches(true).owner(null).build();

		List<Long> ids = accessService.getAccessibleBranchIds(manager);

		// Failing closed: neither an exception nor a wildcard over every branch
		assertTrue(ids.isEmpty());
		verifyNoInteractions(branchRepository);
	}

	@Test
	@DisplayName("TC-006 · Branch-scoped manager reaches only assigned branches")
	void TC006_getAccessibleBranchIds_scoped() {
		stubAssignments(3L, 4L);

		List<Long> ids = accessService.getAccessibleBranchIds(scopedManager());

		assertEquals(List.of(3L, 4L), ids);
		verify(branchRepository, never()).findByOwnerId(anyLong());
	}

	// ══════════════════════════ canManagerAccessBranch ══════════════════════════

	@Test
	@DisplayName("TC-007 · Branch inside the manager scope")
	void TC007_canManagerAccessBranch_inScope() {
		stubAssignments(3L, 4L);

		assertTrue(accessService.canManagerAccessBranch(scopedManager(), 3L));
	}

	@Test
	@DisplayName("TC-008 · Branch outside the manager scope")
	void TC008_canManagerAccessBranch_outOfScope() {
		stubAssignments(3L, 4L);

		assertFalse(accessService.canManagerAccessBranch(scopedManager(), 9L));
	}

	@Test
	@DisplayName("TC-009 · Branch id omitted")
	void TC009_canManagerAccessBranch_nullBranchId() {
		assertFalse(accessService.canManagerAccessBranch(scopedManager(), null));

		// Without the guard, List.contains(null) would answer for a branch that does not exist
		verifyNoInteractions(branchManagerRepository);
		verifyNoInteractions(branchRepository);
	}

	// ══════════════════════ canManagerCreateTournamentAt ══════════════════════

	@Test
	@DisplayName("TC-010 · Creating at an assigned, active branch")
	void TC010_canManagerCreateTournamentAt_activeBranch() {
		stubAssignments(3L);
		when(branchRepository.findById(3L)).thenReturn(Optional.of(branch(3L, BranchStatus.ACTIVE, owner())));

		assertTrue(accessService.canManagerCreateTournamentAt(scopedManager(), 3L));
	}

	@Test
	@DisplayName("TC-011 · Creating at an assigned but deactivated branch")
	void TC011_canManagerCreateTournamentAt_inactiveBranch() {
		stubAssignments(3L);
		when(branchRepository.findById(3L)).thenReturn(Optional.of(branch(3L, BranchStatus.INACTIVE, owner())));

		// Being assigned is not enough — UC-39.3 BR-03 keeps INACTIVE branches out of
		// operational assignment lists
		assertFalse(accessService.canManagerCreateTournamentAt(scopedManager(), 3L));
	}

	@Test
	@DisplayName("TC-012 · Creating at a branch outside the scope")
	void TC012_canManagerCreateTournamentAt_outOfScope() {
		stubAssignments(3L);

		assertFalse(accessService.canManagerCreateTournamentAt(scopedManager(), 9L));

		// The scope check short-circuits, so the branch is never even loaded
		verify(branchRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-013 · Assigned branch has since been deleted")
	void TC013_canManagerCreateTournamentAt_branchGone() {
		stubAssignments(3L);
		when(branchRepository.findById(3L)).thenReturn(Optional.empty());

		assertFalse(accessService.canManagerCreateTournamentAt(scopedManager(), 3L));
	}

	// ══════════════════════════ canActorAccessBranch ══════════════════════════

	@Test
	@DisplayName("TC-014 · An Owner reaches any branch")
	void TC014_canActorAccessBranch_owner() {
		assertTrue(accessService.canActorAccessBranch(owner(), 9999L));

		// UC-38 BR-03 gives an Owner system-wide visibility
		verifyNoInteractions(branchManagerRepository);
	}

	@Test
	@DisplayName("TC-015 · A Manager is held to their scope")
	void TC015_canActorAccessBranch_manager() {
		stubAssignments(3L);
		User manager = scopedManager();

		assertTrue(accessService.canActorAccessBranch(manager, 3L));
		assertFalse(accessService.canActorAccessBranch(manager, 9L));
	}

	@Test
	@DisplayName("TC-016 · Every other role is refused")
	void TC016_canActorAccessBranch_otherRolesRefused() {
		// ADMIN is refused too: branch scope is an Owner and Manager concept
		assertFalse(accessService.canActorAccessBranch(actorWithRole("STAFF"), 3L));
		assertFalse(accessService.canActorAccessBranch(actorWithRole("PLAYER"), 3L));
		assertFalse(accessService.canActorAccessBranch(actorWithRole("ADMIN"), 3L));
	}

	// ══════════════════════════ getAccessibleBranches ══════════════════════════

	@Test
	@DisplayName("TC-017 · Only active branches come back")
	void TC017_getAccessibleBranches_activeOnly() {
		stubAssignments(3L, 4L);
		when(branchRepository.findByIdInAndStatus(List.of(3L, 4L), BranchStatus.ACTIVE))
				.thenReturn(List.of(branch(3L, BranchStatus.ACTIVE, owner())));

		List<Branch> branches = accessService.getAccessibleBranches(scopedManager());

		assertEquals(1, branches.size());
		assertEquals(3L, branches.get(0).getId());
	}

	@Test
	@DisplayName("TC-018 · Manager holds no assignment yet")
	void TC018_getAccessibleBranches_emptyScope() {
		when(branchManagerRepository.findByManagerId(200L)).thenReturn(List.of());

		assertTrue(accessService.getAccessibleBranches(scopedManager()).isEmpty());

		// Avoids a query with an empty IN clause
		verify(branchRepository, never()).findByIdInAndStatus(any(), any());
	}

	// ══════════════════════ resolveOwnerBranchFilter ══════════════════════

	@Test
	@DisplayName("TC-019 · No branch requested means no branch filter")
	void TC019_resolveOwnerBranchFilter_nullMeansNoFilter() {
		// null means "do not filter"; an empty list would match nothing and blank the dashboard
		assertNull(accessService.resolveOwnerBranchFilter(OWNER_ID, null));

		verifyNoInteractions(branchRepository);
	}

	@Test
	@DisplayName("TC-020 · Filtering on a branch the Owner owns")
	void TC020_resolveOwnerBranchFilter_ownedBranch() {
		when(branchRepository.findById(3L)).thenReturn(Optional.of(branch(3L, BranchStatus.ACTIVE, owner())));

		assertEquals(List.of(3L), accessService.resolveOwnerBranchFilter(OWNER_ID, 3L));
	}

	@Test
	@DisplayName("TC-021 · Filtering on a branch owned by someone else")
	void TC021_resolveOwnerBranchFilter_foreignBranch() {
		User otherOwner = User.builder().id(999L).build();
		when(branchRepository.findById(3L)).thenReturn(Optional.of(branch(3L, BranchStatus.ACTIVE, otherOwner)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accessService.resolveOwnerBranchFilter(OWNER_ID, 3L));

		// Stops an Owner reading another chain by editing the branchId query parameter
		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-022 · Branch has no owner recorded")
	void TC022_resolveOwnerBranchFilter_orphanBranch() {
		when(branchRepository.findById(3L)).thenReturn(Optional.of(branch(3L, BranchStatus.ACTIVE, null)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accessService.resolveOwnerBranchFilter(OWNER_ID, 3L));

		// Orphaned rows fail closed rather than raising a NullPointerException
		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-023 · Branch does not exist")
	void TC023_resolveOwnerBranchFilter_notFound() {
		when(branchRepository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accessService.resolveOwnerBranchFilter(OWNER_ID, 9999L));

		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════ resolveManagerBranchFilter ══════════════════════

	@Test
	@DisplayName("TC-024 · No branch requested means the whole manager scope")
	void TC024_resolveManagerBranchFilter_nullMeansWholeScope() {
		stubAssignments(3L, 4L);

		// Deliberately unlike the Owner path: a Manager always has a scope, so no branch
		// parameter means their whole scope rather than everything
		assertEquals(List.of(3L, 4L), accessService.resolveManagerBranchFilter(scopedManager(), null));
	}

	@Test
	@DisplayName("TC-025 · Filtering on a branch inside the scope")
	void TC025_resolveManagerBranchFilter_inScope() {
		stubAssignments(3L, 4L);

		assertEquals(List.of(3L), accessService.resolveManagerBranchFilter(scopedManager(), 3L));
	}

	@Test
	@DisplayName("TC-026 · Filtering on a branch outside the scope")
	void TC026_resolveManagerBranchFilter_outOfScope() {
		stubAssignments(3L, 4L);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> accessService.resolveManagerBranchFilter(scopedManager(), 9L));

		// Throwing rather than quietly narrowing makes the refusal visible to the caller
		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-027 · Manager with an empty scope requests everything")
	void TC027_resolveManagerBranchFilter_emptyScope() {
		when(branchManagerRepository.findByManagerId(200L)).thenReturn(List.of());

		// The contrast with TC-019 is the point: null means no filter, empty means no matches.
		// UC-38 AF-04 requires zeroed statistics for a manager holding no branches.
		assertTrue(accessService.resolveManagerBranchFilter(scopedManager(), null).isEmpty());
	}
}
