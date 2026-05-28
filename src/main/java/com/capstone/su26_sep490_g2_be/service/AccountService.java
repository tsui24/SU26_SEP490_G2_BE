package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import org.springframework.data.domain.Page;

public interface AccountService {

	UserResponse createAccount(CreateAccountRequest request, RoleCode targetRole, RoleCode callerRole);

	EmployeeAccountResponse createManagerAccount(CreateManagerAccountRequest request);

	EmployeeAccountResponse createStaffAccount(CreateStaffAccountRequest request);

	Page<EmployeeAccountResponse> getEmployees(String role, String search, int page, int size);

	EmployeeAccountResponse getEmployeeDetail(Long id);

	EmployeeAccountResponse deactivateEmployee(Long id);

	Page<EmployeeAccountResponse> getStaffsByManager(String search, int page, int size);
}