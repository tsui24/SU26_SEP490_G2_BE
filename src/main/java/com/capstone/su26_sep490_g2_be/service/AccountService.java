package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateEmployeeAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;

import java.util.List;

public interface AccountService {

	UserResponse createAccount(CreateAccountRequest request, RoleCode targetRole, RoleCode callerRole);

	EmployeeAccountResponse createManagerAccount(CreateManagerAccountRequest request, Long ownerId);

	EmployeeAccountResponse createStaffAccount(CreateStaffAccountRequest request, Long ownerId);

	/**
	 * Dùng khi người gọi là Manager: {@code managerAllowedBranchIds} là danh sách chi nhánh Manager
	 * được cấp quyền (qua BranchAccessService) — request.branchId phải nằm trong danh sách này,
	 * nếu không Manager có thể tạo Staff ở chi nhánh mình không quản lý dù cùng Owner.
	 */
	EmployeeAccountResponse createStaffAccount(
			CreateStaffAccountRequest request, Long ownerId, List<Long> managerAllowedBranchIds);

	/** ownerId scope các employee (Manager/Staff) thuộc đúng owner này. */
	PageResponse<EmployeeAccountResponse> getEmployees(Long ownerId, String role, String search, int page, int size);

	/** ownerId null = không giới hạn (dùng cho Admin); khác null thì employee phải thuộc đúng owner đó. */
	EmployeeAccountResponse getEmployeeDetail(Long ownerId, Long id);

	/** ownerId null = không giới hạn (dùng cho Admin); khác null thì employee phải thuộc đúng owner đó. */
	EmployeeAccountResponse deactivateEmployee(Long ownerId, Long id);

	/** Mở khóa lại tài khoản đã bị deactivateEmployee — cùng phạm vi quyền với deactivateEmployee. */
	EmployeeAccountResponse reactivateEmployee(Long ownerId, Long id);
	PageResponse<EmployeeAccountResponse> getUsers(String role, String search, int page, int size) ;
	PageResponse<EmployeeAccountResponse> getStaffsByManager(Long ownerId, String search, int page, int size);

	/** Manager chỉ xem được STAFF thuộc các chi nhánh mình được cấp quyền (không phải cả chuỗi của Owner). */
	PageResponse<EmployeeAccountResponse> getStaffsByManagerBranches(
			List<Long> branchIds, String search, int page, int size);

	/** Manager chỉ xem được chi tiết STAFF thuộc chi nhánh mình được cấp quyền. */
	EmployeeAccountResponse getStaffDetailForManager(List<Long> branchIds, Long id);

	/** Manager chỉ vô hiệu hóa được STAFF thuộc chi nhánh mình được cấp quyền. */
	EmployeeAccountResponse deactivateStaffForManager(List<Long> branchIds, Long id);

	/** Mở khóa lại STAFF đã bị deactivateStaffForManager — cùng phạm vi quyền. */
	EmployeeAccountResponse reactivateStaffForManager(List<Long> branchIds, Long id);

	/** Owner cập nhật hồ sơ Manager/Staff; với Manager có thể đổi luôn phạm vi quản lý chi nhánh. */
	EmployeeAccountResponse updateEmployee(Long ownerId, Long employeeId, UpdateEmployeeAccountRequest request);
}