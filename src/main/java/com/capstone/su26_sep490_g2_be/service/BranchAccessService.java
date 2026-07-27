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
	 * cần enforce "chi nhánh nào được cấp quyền thì chỉ thao tác/nhìn thấy chi nhánh đó". Hệ thống
	 * chỉ có 1 chuỗi (nhiều chi nhánh, không phải nhiều chuỗi độc lập) nên Owner luôn được phép trên
	 * toàn chuỗi; chỉ Manager mới bị giới hạn theo {@link #canManagerAccessBranch}.
	 */
	boolean canActorAccessBranch(User actor, Long branchId);
}
