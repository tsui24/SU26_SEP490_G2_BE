package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateEmployeeAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;

public interface AccountService {

	UserResponse createAccount(CreateAccountRequest request, RoleCode targetRole, RoleCode callerRole);

	EmployeeAccountResponse createManagerAccount(CreateManagerAccountRequest request, Long ownerId);

	EmployeeAccountResponse createStaffAccount(CreateStaffAccountRequest request, Long ownerId);

	/** ownerId scope các employee (Manager/Staff) thuộc đúng owner này. */
	PageResponse<EmployeeAccountResponse> getEmployees(Long ownerId, String role, String search, int page, int size);

	/** ownerId null = không giới hạn (dùng cho Admin); khác null thì employee phải thuộc đúng owner đó. */
	EmployeeAccountResponse getEmployeeDetail(Long ownerId, Long id);

	/** ownerId null = không giới hạn (dùng cho Admin); khác null thì employee phải thuộc đúng owner đó. */
	EmployeeAccountResponse deactivateEmployee(Long ownerId, Long id);
	PageResponse<EmployeeAccountResponse> getUsers(String role, String search, int page, int size) ;
	PageResponse<EmployeeAccountResponse> getStaffsByManager(Long ownerId, String search, int page, int size);

	/** Owner cập nhật hồ sơ Manager/Staff; với Manager có thể đổi luôn phạm vi quản lý chi nhánh. */
	EmployeeAccountResponse updateEmployee(Long ownerId, Long employeeId, UpdateEmployeeAccountRequest request);
}