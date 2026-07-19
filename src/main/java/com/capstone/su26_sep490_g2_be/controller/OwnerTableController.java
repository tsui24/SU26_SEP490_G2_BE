package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.request.CreateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.request.TableStatusUpdateRequest;
import com.capstone.su26_sep490_g2_be.dto.request.UpdateBilliardTableRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.BilliardTableResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ImportTableResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.BilliardTableExcelService;
import com.capstone.su26_sep490_g2_be.service.BilliardTableService;
import com.capstone.su26_sep490_g2_be.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Tag(name = "Owner — Tables", description = "Owner quản lý pool bàn dùng chung của chuỗi — requires OWNER role")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/owner/tables")
@RequiredArgsConstructor
public class OwnerTableController {

	private final BilliardTableService tableService;
	private final BilliardTableExcelService tableExcelService;

	@Operation(summary = "Tải mẫu import bàn hàng loạt", description = "Mặc định trả file .xlsx. Thêm ?format=csv nếu cần CSV.")
	@GetMapping("/import-template")
	public ResponseEntity<byte[]> downloadImportTemplate(
			@RequestParam(value = "format", defaultValue = "xlsx") String format) {
		boolean asCsv = "csv".equalsIgnoreCase(format);
		try {
			byte[] bytes = asCsv ? tableExcelService.buildImportTemplateCsv() : tableExcelService.buildImportTemplate();
			String filename = asCsv ? tableExcelService.getTemplateCsvFilename() : tableExcelService.getTemplateFilename();
			MediaType mediaType = asCsv
					? MediaType.parseMediaType("text/csv; charset=UTF-8")
					: MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
					.header(HttpHeaders.CACHE_CONTROL, "no-store")
					.contentType(mediaType)
					.body(bytes);
		} catch (IOException e) {
			log.error("Failed to build table import template: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	@Operation(summary = "Import bàn hàng loạt từ Excel/CSV")
	@PostMapping("/import-excel")
	public ResponseEntity<ApiResponse<ImportTableResultResponse>> importTables(
			Authentication authentication, @RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(ApiResponse.success(
				tableExcelService.importFromExcel(SecurityUtil.extractUserId(authentication), file)));
	}

	@Operation(summary = "Danh sách bàn đang hoạt động", description = "Không phân trang — dùng để chọn bàn khi gán lịch thi đấu")
	@GetMapping("/active")
	public ResponseEntity<ApiResponse<List<BilliardTableResponse>>> listActiveTables(Authentication authentication) {
		return ResponseEntity.ok(ApiResponse.success(
				tableService.listActiveForOwner(SecurityUtil.extractUserId(authentication))));
	}

	@Operation(summary = "Danh sách bàn")
	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<BilliardTableResponse>>> listTables(
			Authentication authentication,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Long branchId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(ApiResponse.success(tableService.listTablesForOwner(
				SecurityUtil.extractUserId(authentication), search, status, branchId, page, size)));
	}

	@Operation(summary = "Tạo bàn")
	@PostMapping
	public ResponseEntity<ApiResponse<BilliardTableResponse>> createTable(
			Authentication authentication,
			@Valid @RequestBody CreateBilliardTableRequest request) {
		BilliardTableResponse response = tableService.createTable(SecurityUtil.extractUserId(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã tạo bàn", response));
	}

	@Operation(summary = "Chi tiết bàn")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<BilliardTableResponse>> getTable(
			Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(
				tableService.getTableForOwner(SecurityUtil.extractUserId(authentication), id)));
	}

	@Operation(summary = "Cập nhật bàn")
	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<BilliardTableResponse>> updateTable(
			Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody UpdateBilliardTableRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				tableService.updateTable(SecurityUtil.extractUserId(authentication), id, request)));
	}

	@Operation(summary = "Đổi trạng thái bàn", description = "ACTIVE / INACTIVE")
	@PatchMapping("/{id}/status")
	public ResponseEntity<ApiResponse<BilliardTableResponse>> updateStatus(
			Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody TableStatusUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				tableService.updateStatus(SecurityUtil.extractUserId(authentication), id, request)));
	}
}
