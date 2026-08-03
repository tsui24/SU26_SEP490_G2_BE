package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.dto.request.BranchStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.response.BranchListItemResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BranchResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.BranchStatus;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BranchAccessService;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link BranchServiceImpl}.
 *
 * <p>Mirrors the <b>BranchService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-39 and UC-40 (FT-09, Wave 1).
 *
 * <p>Rows TC-028 to TC-031 of that sheet cover the audit logging UC-39 requires. They are
 * marked Blocked and have no method here: {@code BranchServiceImpl} has no audit collaborator
 * and neither does the controller, so there is nothing to assert against yet (DEF-W1-06).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · BranchService — UC-39, UC-40")
class BranchServiceImplTest {

	@Mock BranchRepository branchRepository;
	@Mock UserRepository userRepository;
	@Mock BranchAccessService branchAccessService;
	@Mock MinioStorageService minioStorageService;
	@Mock MinioProperties minioProperties;

	@InjectMocks BranchServiceImpl branchService;

	private static final Long OWNER_ID = 100L;
	private static final Long BRANCH_ID = 7L;

	private static User owner() {
		return User.builder().id(OWNER_ID).email("owner@example.com").build();
	}

	private static Branch branch(BranchStatus status, String imageKeysJson) {
		return Branch.builder()
				.id(BRANCH_ID)
				.name("Chi nhánh 1").address("12 Láng Hạ")
				.phone("0901234567").description("Cơ sở chính")
				.status(status)
				.owner(owner())
				.imageKeys(imageKeysJson)
				.build();
	}

	/** Infrastructure touched only while building a response. */
	private void stubStorage() {
		lenient().when(minioProperties.getBucket()).thenReturn("btms");
	}

	private static CreateBranchRequest createRequest(List<String> images) {
		CreateBranchRequest r = new CreateBranchRequest();
		r.setName("Chi nhánh 1");
		r.setAddress("12 Láng Hạ");
		r.setPhone("0901234567");
		r.setImages(images);
		return r;
	}

	private static BranchStatusUpdateRequest statusRequest(String status) {
		BranchStatusUpdateRequest r = new BranchStatusUpdateRequest();
		r.setStatus(status);
		return r;
	}

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Pageable> capturePageable(Page<Branch> result) {
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		when(branchRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(result);
		return captor;
	}

	// ══════════════════════════ createBranch — UC-39.2 ══════════════════════════

	@Test
	@DisplayName("TC-001 · Owner creates a branch, which starts active")
	void TC001_createBranch_startsActive() {
		stubStorage();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		BranchResponse response = branchService.createBranch(OWNER_ID, createRequest(List.of()));

		ArgumentCaptor<Branch> saved = ArgumentCaptor.forClass(Branch.class);
		verify(branchRepository, times(1)).save(saved.capture());
		assertEquals(BranchStatus.ACTIVE, saved.getValue().getStatus());
		assertEquals(OWNER_ID, saved.getValue().getOwner().getId());
		assertEquals("ACTIVE", response.getStatus());
	}

	@Test
	@DisplayName("TC-002 · Calling Owner does not exist")
	void TC002_createBranch_ownerNotFound() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.createBranch(OWNER_ID, createRequest(List.of())));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
		verify(branchRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-003 · Creating a branch with no images")
	void TC003_createBranch_nullImages() {
		stubStorage();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		branchService.createBranch(OWNER_ID, createRequest(null));

		ArgumentCaptor<Branch> saved = ArgumentCaptor.forClass(Branch.class);
		verify(branchRepository).save(saved.capture());
		// A literal "null" in this column would break every later read of the gallery
		assertEquals("[]", saved.getValue().getImageKeys());
	}

	@Test
	@DisplayName("TC-004 · Blank image keys are discarded")
	void TC004_createBranch_blankImageKeysFiltered() {
		stubStorage();
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		branchService.createBranch(OWNER_ID, createRequest(List.of("avatars/a.jpg", "", "   ")));

		ArgumentCaptor<Branch> saved = ArgumentCaptor.forClass(Branch.class);
		verify(branchRepository).save(saved.capture());
		List<String> keys = JsonParseUtil.parseStringList(saved.getValue().getImageKeys());
		assertEquals(List.of("avatars/a.jpg"), keys);
	}

	// ══════════════════════════ updateBranch — UC-39.2 ══════════════════════════

	@Test
	@DisplayName("TC-005 · Updating only the name leaves everything else alone")
	void TC005_updateBranch_partialUpdate() {
		stubStorage();
		Branch existing = branch(BranchStatus.ACTIVE, "[]");
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		UpdateBranchRequest request = new UpdateBranchRequest();
		request.setName("Chi nhánh Trung tâm");

		branchService.updateBranch(OWNER_ID, BRANCH_ID, request);

		assertEquals("Chi nhánh Trung tâm", existing.getName());
		assertEquals("12 Láng Hạ", existing.getAddress());
		assertEquals("0901234567", existing.getPhone());
		assertEquals("Cơ sở chính", existing.getDescription());
	}

	@Test
	@DisplayName("TC-006 · A field sent as null is left untouched")
	void TC006_updateBranch_nullFieldsIgnored() {
		stubStorage();
		Branch existing = branch(BranchStatus.ACTIVE, "[\"avatars/a.jpg\"]");
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		branchService.updateBranch(OWNER_ID, BRANCH_ID, new UpdateBranchRequest());

		// Without these guards an edit form posting one field would blank the rest
		assertEquals("Chi nhánh 1", existing.getName());
		assertEquals("12 Láng Hạ", existing.getAddress());
		assertEquals("0901234567", existing.getPhone());
		assertEquals("[\"avatars/a.jpg\"]", existing.getImageKeys());
	}

	@Test
	@DisplayName("TC-007 · Replacing the image gallery")
	void TC007_updateBranch_replacesGallery() {
		stubStorage();
		Branch existing = branch(BranchStatus.ACTIVE, "[\"avatars/old.jpg\"]");
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		UpdateBranchRequest request = new UpdateBranchRequest();
		request.setImages(List.of("branches/1.jpg", "branches/2.jpg"));

		branchService.updateBranch(OWNER_ID, BRANCH_ID, request);

		// A replacement, not an append
		assertEquals(List.of("branches/1.jpg", "branches/2.jpg"),
				JsonParseUtil.parseStringList(existing.getImageKeys()));
	}

	@Test
	@DisplayName("TC-008 · Editing a branch belonging to another Owner")
	void TC008_updateBranch_foreignBranch_rejected() {
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.updateBranch(OWNER_ID, BRANCH_ID, new UpdateBranchRequest()));

		// Answering "not found" rather than "forbidden" also hides whether the branch exists
		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
		verify(branchRepository, never()).save(any());
	}

	// ══════════════════════════ updateStatus — UC-39.3 ══════════════════════════

	@Test
	@DisplayName("TC-009 · Deactivating a branch")
	void TC009_updateStatus_deactivate() {
		stubStorage();
		Branch existing = branch(BranchStatus.ACTIVE, "[]");
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		BranchResponse response = branchService.updateStatus(OWNER_ID, BRANCH_ID, statusRequest("INACTIVE"));

		assertEquals(BranchStatus.INACTIVE, existing.getStatus());
		assertEquals("INACTIVE", response.getStatus());
	}

	@Test
	@DisplayName("TC-010 · Reactivating a branch")
	void TC010_updateStatus_reactivate() {
		stubStorage();
		Branch existing = branch(BranchStatus.INACTIVE, "[]");
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		branchService.updateStatus(OWNER_ID, BRANCH_ID, statusRequest("ACTIVE"));

		assertEquals(BranchStatus.ACTIVE, existing.getStatus());
	}

	@Test
	@DisplayName("TC-011 · Status accepted in lower case and with padding")
	void TC011_updateStatus_normalisesInput() {
		stubStorage();
		Branch existing = branch(BranchStatus.ACTIVE, "[]");
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

		branchService.updateStatus(OWNER_ID, BRANCH_ID, statusRequest("  inactive  "));

		assertEquals(BranchStatus.INACTIVE, existing.getStatus());
	}

	@Test
	@DisplayName("TC-012 · Unrecognised status value")
	void TC012_updateStatus_invalidValue() {
		Branch existing = branch(BranchStatus.ACTIVE, "[]");
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(existing));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.updateStatus(OWNER_ID, BRANCH_ID, statusRequest("DELETED")));

		// The enum parse is wrapped so IllegalArgumentException never escapes as a 500
		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		assertEquals(BranchStatus.ACTIVE, existing.getStatus());
		verify(branchRepository, never()).save(any());
	}

	// ══════════════════════════ getBranchForOwner — UC-39.1 ══════════════════════════

	@Test
	@DisplayName("TC-013 · Owner opens one of their own branches")
	void TC013_getBranchForOwner_happyPath() {
		stubStorage();
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID))
				.thenReturn(Optional.of(branch(BranchStatus.ACTIVE, "[]")));

		BranchResponse response = branchService.getBranchForOwner(OWNER_ID, BRANCH_ID);

		assertEquals(BRANCH_ID, response.getId());
		assertEquals("Chi nhánh 1", response.getName());
		assertEquals("12 Láng Hạ", response.getAddress());
		assertEquals("ACTIVE", response.getStatus());
		assertNotNull(response.getImages());
	}

	@Test
	@DisplayName("TC-014 · Opening a branch belonging to another Owner")
	void TC014_getBranchForOwner_foreignBranch_rejected() {
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.getBranchForOwner(OWNER_ID, BRANCH_ID));

		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════ listBranchesForOwner — UC-39.1 ══════════════════════

	@Test
	@DisplayName("TC-015 · Newest branches come first")
	void TC015_listBranchesForOwner_sortedByCreatedAtDesc() {
		stubStorage();
		ArgumentCaptor<Pageable> pageable =
				capturePageable(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		branchService.listBranchesForOwner(OWNER_ID, null, null, 0, 10);

		verify(branchRepository).findAll(any(Specification.class), pageable.capture());
		Sort.Order order = pageable.getValue().getSort().getOrderFor("createdAt");
		assertNotNull(order);
		assertTrue(order.isDescending());
		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(10, pageable.getValue().getPageSize());
	}

	@Test
	@DisplayName("TC-016 · A page size below one falls back to ten")
	void TC016_listBranchesForOwner_sizeFallback() {
		stubStorage();
		ArgumentCaptor<Pageable> pageable =
				capturePageable(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		branchService.listBranchesForOwner(OWNER_ID, null, null, 0, 0);

		verify(branchRepository).findAll(any(Specification.class), pageable.capture());
		// PageRequest.of would throw on a size of zero
		assertEquals(10, pageable.getValue().getPageSize());
	}

	@Test
	@DisplayName("TC-017 · A negative page index falls back to zero")
	void TC017_listBranchesForOwner_pageFallback() {
		stubStorage();
		ArgumentCaptor<Pageable> pageable =
				capturePageable(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		branchService.listBranchesForOwner(OWNER_ID, null, null, -5, 10);

		verify(branchRepository).findAll(any(Specification.class), pageable.capture());
		assertEquals(0, pageable.getValue().getPageNumber());
	}

	@Test
	@DisplayName("TC-018 · Blank search and status are treated as no filter")
	void TC018_listBranchesForOwner_blankFiltersIgnored() {
		stubStorage();
		capturePageable(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		PageResponse<BranchListItemResponse> response =
				branchService.listBranchesForOwner(OWNER_ID, "   ", "   ", 0, 10);

		// A blank status reaching BranchStatus.valueOf would raise IllegalArgumentException
		assertTrue(response.getContent().isEmpty());
	}

	@Test
	@DisplayName("TC-019 · Owner has no branches yet")
	void TC019_listBranchesForOwner_emptyResult() {
		stubStorage();
		capturePageable(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		PageResponse<BranchListItemResponse> response =
				branchService.listBranchesForOwner(OWNER_ID, null, null, 0, 10);

		assertTrue(response.getContent().isEmpty());
	}

	// ══════════════════════ getAccessibleBranch — Manager scope ══════════════════════

	@Test
	@DisplayName("TC-020 · Manager opens a branch inside their scope")
	void TC020_getAccessibleBranch_inScope() {
		stubStorage();
		User manager = User.builder().id(200L).build();
		when(branchAccessService.canManagerAccessBranch(manager, BRANCH_ID)).thenReturn(true);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BranchStatus.ACTIVE, "[]")));

		BranchResponse response = branchService.getAccessibleBranch(manager, BRANCH_ID);

		assertEquals(BRANCH_ID, response.getId());
	}

	@Test
	@DisplayName("TC-021 · Manager opens a branch outside their scope")
	void TC021_getAccessibleBranch_outOfScope() {
		User manager = User.builder().id(200L).build();
		when(branchAccessService.canManagerAccessBranch(manager, BRANCH_ID)).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.getAccessibleBranch(manager, BRANCH_ID));

		assertEquals(ErrorCode.BRANCH_ACCESS_DENIED, ex.getErrorCode());
		// The permission check must run BEFORE the load, or the lookup itself leaks existence
		verify(branchRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("TC-022 · Permitted branch has since been deleted")
	void TC022_getAccessibleBranch_branchGone() {
		User manager = User.builder().id(200L).build();
		when(branchAccessService.canManagerAccessBranch(manager, BRANCH_ID)).thenReturn(true);
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.getAccessibleBranch(manager, BRANCH_ID));

		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
	}

	// ══════════════════════════ Public views — UC-40 ══════════════════════════

	@Test
	@DisplayName("TC-023 · The public list is restricted to active branches")
	void TC023_listPublicBranches_activeOnly() {
		stubStorage();
		capturePageable(new PageImpl<>(List.of(branch(BranchStatus.ACTIVE, "[]")), PageRequest.of(0, 10), 1));

		PageResponse<BranchListItemResponse> response = branchService.listPublicBranches(null, 0, 10);

		assertEquals(1, response.getContent().size());
		assertEquals("ACTIVE", response.getContent().get(0).getStatus());
		verify(branchRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-024 · Invalid paging values fall back to defaults")
	void TC024_listPublicBranches_pagingFallback() {
		stubStorage();
		ArgumentCaptor<Pageable> pageable =
				capturePageable(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

		branchService.listPublicBranches(null, -1, 0);

		verify(branchRepository).findAll(any(Specification.class), pageable.capture());
		// This endpoint is public, so the query string is entirely attacker-controlled
		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(10, pageable.getValue().getPageSize());
	}

	@Test
	@DisplayName("TC-025 · Opening an active branch publicly")
	void TC025_getPublicBranch_active() {
		stubStorage();
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BranchStatus.ACTIVE, "[]")));

		BranchResponse response = branchService.getPublicBranch(BRANCH_ID);

		assertEquals("ACTIVE", response.getStatus());
	}

	@Test
	@DisplayName("TC-026 · Opening an inactive branch publicly")
	void TC026_getPublicBranch_inactiveHidden() {
		when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BranchStatus.INACTIVE, "[]")));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.getPublicBranch(BRANCH_ID));

		// The deep link stops working the moment the Owner deactivates the branch, and an
		// inactive branch is indistinguishable from a missing one
		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-027 · Opening a branch that does not exist")
	void TC027_getPublicBranch_notFound() {
		when(branchRepository.findById(9999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> branchService.getPublicBranch(9999L));

		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
	}

	// ═════════ listAccessibleBranches — added after the first run to close a coverage gap ═════════

	@Test
	@DisplayName("TC-032 · Manager lists the branches they may access")
	void TC032_listAccessibleBranches_returnsScopedList() {
		stubStorage();
		User manager = User.builder().id(200L).build();
		when(branchAccessService.getAccessibleBranches(manager))
				.thenReturn(List.of(branch(BranchStatus.ACTIVE, "[]"), branch(BranchStatus.INACTIVE, "[]")));

		PageResponse<BranchListItemResponse> response =
				branchService.listAccessibleBranches(manager, 0, 10);

		// The scope decision belongs to BranchAccessService; this method only maps and pages
		assertEquals(2, response.getContent().size());
		verify(branchRepository, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	@DisplayName("TC-033 · Client-side paging over the accessible list")
	void TC033_listAccessibleBranches_pagesInMemory() {
		stubStorage();
		User manager = User.builder().id(200L).build();
		when(branchAccessService.getAccessibleBranches(manager)).thenReturn(List.of(
				branch(BranchStatus.ACTIVE, "[]"),
				branch(BranchStatus.ACTIVE, "[]"),
				branch(BranchStatus.ACTIVE, "[]")));

		PageResponse<BranchListItemResponse> response =
				branchService.listAccessibleBranches(manager, 1, 2);

		assertEquals(1, response.getContent().size());
		assertEquals(3, response.getTotalElements());
	}
}
