package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BilliardTableResponse;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.service.BilliardTableService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Manager — Tables", description = "Manager xem pool bàn của chuỗi để gán lịch — requires MANAGER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/manager/tables")
@RequiredArgsConstructor
public class ManagerTableController {

	private final BilliardTableService tableService;
	private final SecurityUtil securityUtil;

	@Operation(summary = "Danh sách bàn đang hoạt động", description = "Chỉ đọc — dùng để chọn bàn khi gán lịch thi đấu")
	@GetMapping
	public ResponseEntity<ApiResponse<List<BilliardTableResponse>>> listActiveTables(Authentication authentication) {
		User manager = securityUtil.resolveCurrentUser(authentication);
		Long ownerId = manager.getOwner() != null ? manager.getOwner().getId() : null;
		List<BilliardTableResponse> tables = ownerId != null
				? tableService.listActiveForOwner(ownerId)
				: List.of();
		return ResponseEntity.ok(ApiResponse.success(tables));
	}
}
