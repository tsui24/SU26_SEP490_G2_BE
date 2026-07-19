package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.CreateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.request.TableStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.response.BilliardTableResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.BilliardTable;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.TableStatus;
import com.capstone.su26_sep490_g2_be.enums.TableType;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.BilliardTableRepository;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.BilliardTableService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BilliardTableServiceImpl implements BilliardTableService {

	private final BilliardTableRepository tableRepository;
	private final BranchRepository branchRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public BilliardTableResponse createTable(Long ownerId, CreateBilliardTableRequest request) {
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		BilliardTable table = BilliardTable.builder()
				.owner(owner)
				.name(request.getName())
				.tableNumber(request.getTableNumber())
				.tableType(parseTableType(request.getTableType()))
				.branch(resolveOwnedBranch(ownerId, request.getBranchId()))
				.status(TableStatus.ACTIVE)
				.build();
		table = tableRepository.save(table);
		return toResponse(table);
	}

	@Override
	@Transactional
	public BilliardTableResponse updateTable(Long ownerId, Long tableId, UpdateBilliardTableRequest request) {
		BilliardTable table = loadOwnedTable(ownerId, tableId);
		if (request.getName() != null) {
			table.setName(request.getName());
		}
		if (request.getTableNumber() != null) {
			table.setTableNumber(request.getTableNumber());
		}
		if (request.getTableType() != null) {
			table.setTableType(parseTableType(request.getTableType()));
		}
		if (Boolean.TRUE.equals(request.getClearBranch())) {
			table.setBranch(null);
		} else if (request.getBranchId() != null) {
			table.setBranch(resolveOwnedBranch(ownerId, request.getBranchId()));
		}
		table = tableRepository.save(table);
		return toResponse(table);
	}

	@Override
	@Transactional
	public BilliardTableResponse updateStatus(Long ownerId, Long tableId, TableStatusUpdateRequest request) {
		BilliardTable table = loadOwnedTable(ownerId, tableId);
		TableStatus status;
		try {
			status = TableStatus.valueOf(request.getStatus().trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Trạng thái phải là ACTIVE hoặc INACTIVE");
		}
		table.setStatus(status);
		table = tableRepository.save(table);
		return toResponse(table);
	}

	@Override
	@Transactional(readOnly = true)
	public BilliardTableResponse getTableForOwner(Long ownerId, Long tableId) {
		return toResponse(loadOwnedTable(ownerId, tableId));
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<BilliardTableResponse> listTablesForOwner(
			Long ownerId, String search, String status, Long branchId, int page, int size) {
		String searchParam = (search == null || search.isBlank()) ? null : search.trim();
		String statusParam = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();
		if (size < 1) size = 10;
		if (page < 0) page = 0;

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Specification<BilliardTable> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("owner").get("id"), ownerId));
			if (statusParam != null) {
				predicates.add(cb.equal(root.get("status"), TableStatus.valueOf(statusParam)));
			}
			if (branchId != null) {
				predicates.add(cb.equal(root.get("branch").get("id"), branchId));
			}
			if (searchParam != null) {
				predicates.add(cb.like(cb.lower(root.get("name")), "%" + searchParam.toLowerCase() + "%"));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
		Page<BilliardTable> tablePage = tableRepository.findAll(spec, pageable);
		return PageResponse.of(tablePage, this::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public List<BilliardTableResponse> listActiveForOwner(Long ownerId) {
		return tableRepository.findByOwnerIdAndStatus(ownerId, TableStatus.ACTIVE).stream()
				.map(this::toResponse)
				.toList();
	}

	private BilliardTable loadOwnedTable(Long ownerId, Long tableId) {
		return tableRepository.findByIdAndOwnerId(tableId, ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TABLE_NOT_FOUND));
	}

	private Branch resolveOwnedBranch(Long ownerId, Long branchId) {
		if (branchId == null) {
			return null;
		}
		return branchRepository.findByIdAndOwnerId(branchId, ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
	}

	private TableType parseTableType(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return TableType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Loại bàn không hợp lệ");
		}
	}

	private BilliardTableResponse toResponse(BilliardTable table) {
		Branch branch = table.getBranch();
		return BilliardTableResponse.builder()
				.id(table.getId())
				.name(table.getName())
				.tableNumber(table.getTableNumber())
				.tableType(table.getTableType() != null ? table.getTableType().name() : null)
				.status(table.getStatus().name())
				.branchId(branch != null ? branch.getId() : null)
				.branchName(branch != null ? branch.getName() : null)
				.createdAt(table.getCreatedAt())
				.updatedAt(table.getUpdatedAt())
				.build();
	}
}
