package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.CreateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.request.TableStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.response.BilliardTableResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.BilliardTable;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TableStatus;
import com.capstone.su26_sep490_g2_be.enums.TableType;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BilliardTableRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link BilliardTableServiceImpl}.
 *
 * <p>Mirrors the <b>BilliardTableService</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-38 (the shared table pool of a chain).
 *
 * <p>Every read and write is keyed by owner as well as by id, so the tests here are as much about
 * who may reach a row as about what the row then becomes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · BilliardTableService — UC-38")
class BilliardTableServiceImplTest {

	@Mock BilliardTableRepository tableRepository;
	@Mock BranchRepository branchRepository;
	@Mock UserRepository userRepository;

	@InjectMocks BilliardTableServiceImpl service;

	private static final Long OWNER_ID = 3L;
	private static final Long TABLE_ID = 11L;
	private static final Long BRANCH_ID = 5L;

	private static User owner() {
		return User.builder().id(OWNER_ID).email("owner@example.com").build();
	}

	private static Branch branch() {
		return Branch.builder().id(BRANCH_ID).name("Chi nhánh Quận 1").build();
	}

	private static BilliardTable table(Branch branch, TableStatus status) {
		return BilliardTable.builder()
				.id(TABLE_ID).owner(owner()).name("Bàn 1").tableNumber(1)
				.tableType(TableType.POOL).branch(branch).status(status)
				.build();
	}

	private static CreateBilliardTableRequest createRequest(String tableType, Long branchId) {
		CreateBilliardTableRequest request = new CreateBilliardTableRequest();
		request.setName("Bàn VIP A");
		request.setTableNumber(7);
		request.setTableType(tableType);
		request.setBranchId(branchId);
		return request;
	}

	private static TableStatusUpdateRequest statusRequest(String status) {
		TableStatusUpdateRequest request = new TableStatusUpdateRequest();
		request.setStatus(status);
		return request;
	}

	private void echoSave() {
		when(tableRepository.save(any(BilliardTable.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	// ══════════════════════════ createTable — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-001 · A new table starts active on the chain pool")
	void TC001_createTable_happyPath() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		echoSave();

		BilliardTableResponse response = service.createTable(OWNER_ID, createRequest("pool", null));

		assertEquals("Bàn VIP A", response.getName());
		assertEquals(7, response.getTableNumber());
		assertEquals("POOL", response.getTableType());
		assertEquals(TableStatus.ACTIVE.name(), response.getStatus());
		// No branch means the table serves the whole chain rather than one venue
		assertNull(response.getBranchId());
	}

	@Test
	@DisplayName("TC-002 · Creating a table as an account that does not exist")
	void TC002_createTable_unknownOwner() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTable(OWNER_ID, createRequest("POOL", null)));

		assertEquals(ErrorCode.AUTH_USER_NOT_FOUND, ex.getErrorCode());
		verify(tableRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-003 · An unknown table type is rejected")
	void TC003_createTable_invalidTableType() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTable(OWNER_ID, createRequest("BIDA", null)));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(tableRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-004 · A blank table type leaves the column empty")
	void TC004_createTable_blankTableType() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		echoSave();

		// The type is optional — an Owner who has not decided yet is not an error
		assertNull(service.createTable(OWNER_ID, createRequest("   ", null)).getTableType());
	}

	@Test
	@DisplayName("TC-005 · A table can be pinned to a branch the chain owns")
	void TC005_createTable_withOwnedBranch() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.of(branch()));
		echoSave();

		BilliardTableResponse response = service.createTable(OWNER_ID, createRequest("POOL", BRANCH_ID));

		assertEquals(BRANCH_ID, response.getBranchId());
		assertEquals("Chi nhánh Quận 1", response.getBranchName());
	}

	@Test
	@DisplayName("TC-006 · A branch belonging to another chain is refused")
	void TC006_createTable_foreignBranch() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner()));
		when(branchRepository.findByIdAndOwnerId(BRANCH_ID, OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.createTable(OWNER_ID, createRequest("POOL", BRANCH_ID)));

		// The lookup is keyed by owner as well as by id, so a guessed branch id leads nowhere
		assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
		verify(tableRepository, never()).save(any());
	}

	// ══════════════════════════ updateTable — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-007 · An omitted field keeps the value it already had")
	void TC007_updateTable_partialUpdate() {
		BilliardTable existing = table(branch(), TableStatus.ACTIVE);
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		echoSave();

		UpdateBilliardTableRequest request = new UpdateBilliardTableRequest();
		request.setName("Bàn 1 (đã sửa)");

		BilliardTableResponse response = service.updateTable(OWNER_ID, TABLE_ID, request);

		assertEquals("Bàn 1 (đã sửa)", response.getName());
		// Everything the Owner did not send survives — this is a patch, not a replace
		assertEquals(1, response.getTableNumber());
		assertEquals("POOL", response.getTableType());
		assertEquals(BRANCH_ID, response.getBranchId());
	}

	@Test
	@DisplayName("TC-008 · Updating a table that belongs to another chain")
	void TC008_updateTable_notOwned() {
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTable(OWNER_ID, TABLE_ID, new UpdateBilliardTableRequest()));

		// Not-found rather than forbidden: the Owner is not told the id exists at all
		assertEquals(ErrorCode.TABLE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-009 · Clearing the branch returns the table to the chain pool")
	void TC009_updateTable_clearBranch() {
		BilliardTable existing = table(branch(), TableStatus.ACTIVE);
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		echoSave();

		UpdateBilliardTableRequest request = new UpdateBilliardTableRequest();
		request.setClearBranch(true);
		request.setBranchId(BRANCH_ID);

		BilliardTableResponse response = service.updateTable(OWNER_ID, TABLE_ID, request);

		// clearBranch wins over branchId, so sending both cannot leave the caller guessing
		assertNull(response.getBranchId());
		verify(branchRepository, never()).findByIdAndOwnerId(any(), any());
	}

	@Test
	@DisplayName("TC-010 · Moving a table to another branch of the same chain")
	void TC010_updateTable_moveBranch() {
		BilliardTable existing = table(null, TableStatus.ACTIVE);
		Branch target = Branch.builder().id(9L).name("Chi nhánh Quận 7").build();
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		when(branchRepository.findByIdAndOwnerId(9L, OWNER_ID)).thenReturn(Optional.of(target));
		echoSave();

		UpdateBilliardTableRequest request = new UpdateBilliardTableRequest();
		request.setBranchId(9L);

		BilliardTableResponse response = service.updateTable(OWNER_ID, TABLE_ID, request);

		assertEquals(9L, response.getBranchId());
		assertEquals("Chi nhánh Quận 7", response.getBranchName());
	}

	@Test
	@DisplayName("TC-011 · An unknown table type on update is rejected before anything is written")
	void TC011_updateTable_invalidTableType() {
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID))
				.thenReturn(Optional.of(table(null, TableStatus.ACTIVE)));

		UpdateBilliardTableRequest request = new UpdateBilliardTableRequest();
		request.setTableType("BIDA");

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTable(OWNER_ID, TABLE_ID, request));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(tableRepository, never()).save(any());
	}

	// ══════════════════════════ updateStatus — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-012 · Taking a table out of service")
	void TC012_updateStatus_deactivate() {
		BilliardTable existing = table(null, TableStatus.ACTIVE);
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID)).thenReturn(Optional.of(existing));
		echoSave();

		BilliardTableResponse response = service.updateStatus(OWNER_ID, TABLE_ID, statusRequest(" inactive "));

		// The value is trimmed and upper-cased, so the client may send either form
		assertEquals(TableStatus.INACTIVE.name(), response.getStatus());
		verify(tableRepository).save(existing);
	}

	@Test
	@DisplayName("TC-013 · A status outside the two allowed values is rejected")
	void TC013_updateStatus_invalidStatus() {
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID))
				.thenReturn(Optional.of(table(null, TableStatus.ACTIVE)));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateStatus(OWNER_ID, TABLE_ID, statusRequest("MAINTENANCE")));

		assertEquals(ErrorCode.COMMON_INVALID_REQUEST, ex.getErrorCode());
		verify(tableRepository, never()).save(any());
	}

	// ══════════════════════════ reads — UC-38 ══════════════════════════

	@Test
	@DisplayName("TC-014 · Opening a table of another chain")
	void TC014_getTableForOwner_notOwned() {
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.getTableForOwner(OWNER_ID, TABLE_ID));

		assertEquals(ErrorCode.TABLE_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("TC-015 · A table with no branch and no type still renders")
	void TC015_getTableForOwner_sparseRow() {
		BilliardTable sparse = BilliardTable.builder()
				.id(TABLE_ID).owner(owner()).name("Bàn góc").status(TableStatus.ACTIVE)
				.build();
		when(tableRepository.findByIdAndOwnerId(TABLE_ID, OWNER_ID)).thenReturn(Optional.of(sparse));

		BilliardTableResponse response = service.getTableForOwner(OWNER_ID, TABLE_ID);

		// The two optional columns come back as null rather than taking the mapper down
		assertNull(response.getTableType());
		assertNull(response.getBranchId());
		assertNull(response.getBranchName());
		assertEquals("Bàn góc", response.getName());
	}

	@Test
	@DisplayName("TC-016 · The scheduling screen only offers tables in service")
	void TC016_listActiveForOwner() {
		when(tableRepository.findByOwnerIdAndStatus(OWNER_ID, TableStatus.ACTIVE))
				.thenReturn(List.of(table(branch(), TableStatus.ACTIVE)));

		List<BilliardTableResponse> tables = service.listActiveForOwner(OWNER_ID);

		// A table under repair must not be offered when a match is assigned a table
		assertEquals(1, tables.size());
		assertEquals(TableStatus.ACTIVE.name(), tables.get(0).getStatus());
	}

	@Test
	@DisplayName("TC-017 · The listing is scoped to the owner and sorted newest first")
	void TC017_listTablesForOwner_ownerScopeAndSort() {
		when(tableRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(table(branch(), TableStatus.ACTIVE))));

		PageResponse<BilliardTableResponse> response =
				service.listTablesForOwner(OWNER_ID, null, null, null, 0, 20);

		assertEquals(1, response.getContent().size());
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		ArgumentCaptor<Specification> spec = ArgumentCaptor.forClass(Specification.class);
		verify(tableRepository).findAll(spec.capture(), pageable.capture());
		assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getValue().getSort());

		Root<BilliardTable> root = mock(Root.class, RETURNS_DEEP_STUBS);
		CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
		spec.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
		// The owner predicate is unconditional — there is no way to ask for another chain's tables
		verify(cb, times(1)).equal(any(Expression.class), eq((Object) OWNER_ID));
	}

	@Test
	@DisplayName("TC-018 · Status, branch and search narrow the listing together")
	void TC018_listTablesForOwner_allFilters() {
		when(tableRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.listTablesForOwner(OWNER_ID, "  VIP  ", " inactive ", BRANCH_ID, 0, 20);

		ArgumentCaptor<Specification> spec = ArgumentCaptor.forClass(Specification.class);
		verify(tableRepository).findAll(spec.capture(), any(Pageable.class));
		CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
		spec.getValue().toPredicate(mock(Root.class, RETURNS_DEEP_STUBS), mock(CriteriaQuery.class), cb);

		verify(cb).equal(any(Expression.class), eq((Object) TableStatus.INACTIVE));
		verify(cb).equal(any(Expression.class), eq((Object) BRANCH_ID));
		// The search term is trimmed and lower-cased on both sides of the comparison
		verify(cb).like(any(Expression.class), eq("%vip%"));
	}

	@Test
	@DisplayName("TC-019 · A blank search term is not treated as a filter")
	void TC019_listTablesForOwner_blankSearchIgnored() {
		when(tableRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.listTablesForOwner(OWNER_ID, "   ", "   ", null, 0, 20);

		ArgumentCaptor<Specification> spec = ArgumentCaptor.forClass(Specification.class);
		verify(tableRepository).findAll(spec.capture(), any(Pageable.class));
		CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
		spec.getValue().toPredicate(mock(Root.class, RETURNS_DEEP_STUBS), mock(CriteriaQuery.class), cb);

		// Only the owner predicate survives; a whitespace box on the screen filters nothing
		verify(cb, never()).like(any(Expression.class), any(String.class));
		verify(cb, times(1)).equal(any(Expression.class), any(Object.class));
	}

	@Test
	@DisplayName("TC-020 · Nonsense paging values fall back to the first page of ten")
	void TC020_listTablesForOwner_pagingGuards() {
		when(tableRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.listTablesForOwner(OWNER_ID, null, null, null, -3, 0);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(tableRepository).findAll(any(Specification.class), pageable.capture());
		// PageRequest.of throws on a negative page or a zero size, so the guard is what keeps a
		// malformed query string from turning into a 500
		assertEquals(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")), pageable.getValue());
	}
}
