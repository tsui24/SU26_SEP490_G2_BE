package com.capstone.su26_sep490_g2_be.exception;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
		ErrorCode errorCode = ex.getErrorCode();
		if (ex instanceof ConfigValidationException validationEx) {
			return ResponseEntity
					.status(errorCode.getHttpStatus())
					.body(ApiResponse.<Void>builder()
							.success(false)
							.code(errorCode.getCode())
							.message(errorCode.getMessage())
							.details(validationEx.getDetails())
							.build());
		}
		return ResponseEntity
				.status(errorCode.getHttpStatus())
				.body(ApiResponse.error(errorCode));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
		String details = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining("; "));
		return ResponseEntity
				.badRequest()
				.body(ApiResponse.<Void>builder()
						.success(false)
						.code(ErrorCode.COMMON_INVALID_REQUEST.getCode())
						.message(details.isBlank()
								? ErrorCode.COMMON_INVALID_REQUEST.getMessage()
								: details)
						.build());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		return ResponseEntity
				.status(ErrorCode.COMMON_INTERNAL_ERROR.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.COMMON_INTERNAL_ERROR));
	}
}
