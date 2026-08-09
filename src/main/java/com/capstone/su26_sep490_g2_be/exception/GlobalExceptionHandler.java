package com.capstone.su26_sep490_g2_be.exception;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
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
		// ex.getMessage() luôn đã đúng (constructor 1-arg set = errorCode.getMessage(), constructor
		// 2-arg set = detailMessage) — dùng errorCode.getMessage() ở đây sẽ làm mất mọi detail message
		// mà nghiệp vụ cố tình truyền vào BusinessException(ErrorCode, String).
		return ResponseEntity
				.status(errorCode.getHttpStatus())
				.body(ApiResponse.<Void>builder()
						.success(false)
						.code(errorCode.getCode())
						.message(ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage())
						.build());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
		log.warn("Optimistic lock conflict: {}", ex.getMessage());
		return ResponseEntity
				.status(ErrorCode.CONCURRENT_UPDATE_CONFLICT.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.CONCURRENT_UPDATE_CONFLICT));
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

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
		return ResponseEntity
				.badRequest()
				.body(ApiResponse.<Void>builder()
						.success(false)
						.code(ErrorCode.COMMON_INVALID_REQUEST.getCode())
						.message("Thiếu tham số bắt buộc: " + ex.getParameterName())
						.build());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
		return ResponseEntity
				.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return ResponseEntity
				.status(ErrorCode.COMMON_METHOD_NOT_ALLOWED.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.COMMON_METHOD_NOT_ALLOWED));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleMalformedBody(HttpMessageNotReadableException ex) {
		return ResponseEntity
				.status(ErrorCode.COMMON_MALFORMED_BODY.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.COMMON_MALFORMED_BODY));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		log.error("Unhandled exception: {}", ex.getMessage(), ex);
		return ResponseEntity
				.status(ErrorCode.COMMON_INTERNAL_ERROR.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.COMMON_INTERNAL_ERROR));
	}
}
