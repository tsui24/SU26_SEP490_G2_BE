package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.CreateAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateManagerAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.request.CreateStaffAccountRequest;
import com.capstone.su26_sep490_g2_be.dto.response.EmployeeAccountResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;

import java.util.List;

public interface AccountService {

	UserResponse createAccount(CreateAccountRequest request, RoleCode targetRole, RoleCode callerRole);

	EmployeeAccountResponse createManagerAccount(CreateManagerAccountRequest request);

	EmployeeAccountResponse createStaffAccount(CreateStaffAccountRequest request);
	List<EmployeeAccountResponse> getEmployees(String role, String search);

	EmployeeAccountResponse getEmployeeDetail(Long id);

	EmployeeAccountResponse deactivateEmployee(Long id);

	List<EmployeeAccountResponse> getStaffsByManager(String search);
}
