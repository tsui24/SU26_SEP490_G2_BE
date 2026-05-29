package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;

public interface AccountService {

	UserResponse createAccount(CreateAccountRequest request, RoleCode targetRole, RoleCode callerRole);

	EmployeeAccountResponse createManagerAccount(CreateManagerAccountRequest request);

	EmployeeAccountResponse createStaffAccount(CreateStaffAccountRequest request);

	PageResponse<EmployeeAccountResponse> getEmployees(String role, String search, int page, int size);

	EmployeeAccountResponse getEmployeeDetail(Long id);

	EmployeeAccountResponse deactivateEmployee(Long id);
	PageResponse<EmployeeAccountResponse> getUsers(String role, String search, int page, int size) ;
	PageResponse<EmployeeAccountResponse> getStaffsByManager(String search, int page, int size);
}