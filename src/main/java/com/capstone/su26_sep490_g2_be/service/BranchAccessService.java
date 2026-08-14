package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.User;

import java.util.List;

/**
 * Trung tâm hóa toàn bộ logic phân quyền Manager–Branch.
 * Mọi nơi cần kiểm tra quyền của Manager đối với chi nhánh phải gọi qua service này.
 */
public interface BranchAccessService {

	/** Manager có quyền truy cập branch này? */
	boolean canManagerAccessBranch(User manager, Long branchId);

	/** Danh sách branchId manager được phép thao tác. */
	List<Long> getAccessibleBranchIds(User manager);

	/** Manager có quyền tạo tournament tại branch? */
	boolean canManagerCreateTournamentAt(User manager, Long branchId);

	/** Lấy danh sách Branch entity manager truy cập được (chỉ ACTIVE). */
	List<Branch> getAccessibleBranches(User manager);

	/** Manager có quyền toàn chuỗi? */
	boolean isChainWideManager(User manager);

	/**
	 * Kiểm tra quyền chung cho mọi actor (Owner hoặc Manager) đối với 1 chi nhánh — dùng ở mọi nơi
	 * cần enforce "chi nhánh nào được cấp quyền thì chỉ thao tác/nhìn thấy chi nhánh đó". Mỗi chi
	 * nhánh thuộc đúng 1 Owner ({@code Branch.owner}) — Owner chỉ được phép trên (các) chi nhánh do
	 * chính mình sở hữu, không phải toàn hệ thống; Manager bị giới hạn theo
	 * {@link #canManagerAccessBranch}.
	 */
	boolean canActorAccessBranch(User actor, Long branchId);

	/**
	 * Bộ lọc chi nhánh cho các trang thống kê/phân tích của Owner — không truyền branchId trả về
	 * null (không lọc, xem toàn chuỗi); truyền branchId thì branch đó phải thuộc sở hữu của ownerId,
	 * nếu không ném {@link com.capstone.su26_sep490_g2_be.enums.ErrorCode#BRANCH_ACCESS_DENIED}.
	 */
	List<Long> resolveOwnerBranchFilter(Long ownerId, Long branchId);

	/**
	 * Bộ lọc chi nhánh cho các trang thống kê/phân tích của Manager — luôn giới hạn về đúng (các)
	 * chi nhánh manager được cấp quyền, không truyền branchId thì lấy toàn bộ danh sách đó; truyền
	 * branchId thì chi nhánh đó phải nằm trong danh sách được cấp quyền.
	 */
	List<Long> resolveManagerBranchFilter(User manager, Long branchId);
}
