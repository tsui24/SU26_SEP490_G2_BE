package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.dto.request.BranchStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBranchRequest;
import com.capstone.su26_sep490_g2_be.dto.response.BranchImageResponse;
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
import com.capstone.su26_sep490_g2_be.service.BranchService;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.util.AvatarUrlResolver;
import com.capstone.su26_sep490_g2_be.util.JsonParseUtil;
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
public class BranchServiceImpl implements BranchService {

	private final BranchRepository branchRepository;
	private final UserRepository userRepository;
	private final BranchAccessService branchAccessService;
	private final MinioStorageService minioStorageService;
	private final MinioProperties minioProperties;

	@Override
	@Transactional
	public BranchResponse createBranch(Long ownerId, CreateBranchRequest request) {
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		Branch branch = Branch.builder()
				.name(request.getName())
				.address(request.getAddress())
				.phone(request.getPhone())
				.description(request.getDescription())
				.status(BranchStatus.ACTIVE)
				.owner(owner)
				.imageKeys(JsonParseUtil.toJson(normalizeImageKeys(request.getImages())))
				.build();
		branch = branchRepository.save(branch);
		return toResponse(branch);
	}

	@Override
	@Transactional
	public BranchResponse updateBranch(Long ownerId, Long branchId, UpdateBranchRequest request) {
		Branch branch = loadOwnedBranch(ownerId, branchId);
		if (request.getName() != null) {
			branch.setName(request.getName());
		}
		if (request.getAddress() != null) {
			branch.setAddress(request.getAddress());
		}
		if (request.getPhone() != null) {
			branch.setPhone(request.getPhone());
		}
		if (request.getDescription() != null) {
			branch.setDescription(request.getDescription());
		}
		if (request.getImages() != null) {
			branch.setImageKeys(JsonParseUtil.toJson(normalizeImageKeys(request.getImages())));
		}
		branch = branchRepository.save(branch);
		return toResponse(branch);
	}

	@Override
	@Transactional
	public BranchResponse updateStatus(Long ownerId, Long branchId, BranchStatusUpdateRequest request) {
		Branch branch = loadOwnedBranch(ownerId, branchId);
		BranchStatus status;
		try {
			status = BranchStatus.valueOf(request.getStatus().trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "Trạng thái phải là ACTIVE hoặc INACTIVE");
		}
		branch.setStatus(status);
		branch = branchRepository.save(branch);
		return toResponse(branch);
	}

	@Override
	@Transactional(readOnly = true)
	public BranchResponse getBranchForOwner(Long ownerId, Long branchId) {
		return toResponse(loadOwnedBranch(ownerId, branchId));
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<BranchListItemResponse> listBranchesForOwner(
			Long ownerId, String search, String status, int page, int size) {
		String searchParam = (search == null || search.isBlank()) ? null : search.trim();
		String statusParam = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();
		if (size < 1) size = 10;
		if (page < 0) page = 0;

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Specification<Branch> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("owner").get("id"), ownerId));
			if (statusParam != null) {
				predicates.add(cb.equal(root.get("status"), BranchStatus.valueOf(statusParam)));
			}
			if (searchParam != null) {
				String like = "%" + searchParam.toLowerCase() + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("name")), like),
						cb.like(cb.lower(root.get("address")), like)));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
		Page<Branch> branchPage = branchRepository.findAll(spec, pageable);
		return PageResponse.of(branchPage, this::toListItem);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<BranchListItemResponse> listAccessibleBranches(User manager, int page, int size) {
		List<BranchListItemResponse> items = branchAccessService.getAccessibleBranches(manager).stream()
				.map(this::toListItem)
				.toList();
		return PageResponse.of(items, page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public BranchResponse getAccessibleBranch(User manager, Long branchId) {
		if (!branchAccessService.canManagerAccessBranch(manager, branchId)) {
			throw new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED);
		}
		Branch branch = branchRepository.findById(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
		return toResponse(branch);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<BranchListItemResponse> listPublicBranches(String search, int page, int size) {
		String searchParam = (search == null || search.isBlank()) ? null : search.trim();
		if (size < 1) size = 10;
		if (page < 0) page = 0;

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Specification<Branch> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("status"), BranchStatus.ACTIVE));
			if (searchParam != null) {
				String like = "%" + searchParam.toLowerCase() + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("name")), like),
						cb.like(cb.lower(root.get("address")), like)));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
		Page<Branch> branchPage = branchRepository.findAll(spec, pageable);
		return PageResponse.of(branchPage, this::toListItem);
	}

	@Override
	@Transactional(readOnly = true)
	public BranchResponse getPublicBranch(Long branchId) {
		Branch branch = branchRepository.findById(branchId)
				.filter(b -> b.getStatus() == BranchStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
		return toResponse(branch);
	}

	private Branch loadOwnedBranch(Long ownerId, Long branchId) {
		return branchRepository.findByIdAndOwnerId(branchId, ownerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND));
	}

	/** Chuẩn hóa từng object key/URL do FE gửi lên trước khi lưu (bỏ qua giá trị rỗng). */
	private List<String> normalizeImageKeys(List<String> images) {
		if (images == null) {
			return List.of();
		}
		return images.stream()
				.map(key -> AvatarUrlResolver.normalizeForStorage(key, minioProperties.getBucket()))
				.filter(key -> key != null && !key.isBlank())
				.toList();
	}

	private List<BranchImageResponse> resolveImages(Branch branch) {
		List<String> keys = JsonParseUtil.parseStringList(branch.getImageKeys());
		if (keys == null || keys.isEmpty()) {
			return List.of();
		}
		return keys.stream()
				.map(key -> BranchImageResponse.builder()
						.key(key)
						.url(AvatarUrlResolver.resolveForResponse(key, minioStorageService, minioProperties.getBucket()))
						.build())
				.toList();
	}

	private BranchListItemResponse toListItem(Branch branch) {
		List<String> keys = JsonParseUtil.parseStringList(branch.getImageKeys());
		String thumbnail = (keys == null || keys.isEmpty())
				? null
				: AvatarUrlResolver.resolveForResponse(keys.get(0), minioStorageService, minioProperties.getBucket());

		return BranchListItemResponse.builder()
				.id(branch.getId())
				.name(branch.getName())
				.address(branch.getAddress())
				.phone(branch.getPhone())
				.description(branch.getDescription())
				.status(branch.getStatus().name())
				.thumbnailUrl(thumbnail)
				.build();
	}

	private BranchResponse toResponse(Branch branch) {
		return BranchResponse.builder()
				.id(branch.getId())
				.name(branch.getName())
				.address(branch.getAddress())
				.phone(branch.getPhone())
				.description(branch.getDescription())
				.status(branch.getStatus().name())
				.images(resolveImages(branch))
				.createdAt(branch.getCreatedAt())
				.updatedAt(branch.getUpdatedAt())
				.build();
	}
}
