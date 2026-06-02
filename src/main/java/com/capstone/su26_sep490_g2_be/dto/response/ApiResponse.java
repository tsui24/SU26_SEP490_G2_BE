package com.capstone.su26_sep490_g2_be.dto.response;

import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response wrapper chung cho API")
public class ApiResponse<T> {

	@Schema(description = "Thành công hay không")
	private boolean success;

	@Schema(description = "Mã lỗi hoặc mã nghiệp vụ")
	private String code;

	@Schema(description = "Thông báo")
	private String message;

	@Schema(description = "Dữ liệu trả về")
	private T data;

	@Schema(description = "Chi tiết lỗi validation (nếu có)")
	private java.util.List<ConfigValidationDetailResponse> details;

	public static <T> ApiResponse<T> success(T data) {
		return ApiResponse.<T>builder()
				.success(true)
				.message("OK")
				.data(data)
				.build();
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return ApiResponse.<T>builder()
				.success(true)
				.message(message)
				.data(data)
				.build();
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode) {
		return ApiResponse.<T>builder()
				.success(false)
				.code(errorCode.getCode())
				.message(errorCode.getMessage())
				.build();
	}
}
