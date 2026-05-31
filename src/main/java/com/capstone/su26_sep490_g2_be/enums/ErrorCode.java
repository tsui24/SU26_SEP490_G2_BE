package com.capstone.su26_sep490_g2_be.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// Common
	COMMON_INVALID_REQUEST("COMMON_001", "Invalid request", HttpStatus.BAD_REQUEST),
	COMMON_NOT_FOUND("COMMON_002", "Resource not found", HttpStatus.NOT_FOUND),
	COMMON_INTERNAL_ERROR("COMMON_500", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
	EXTERNAL_API_ERROR("EXT_001", "External API call failed", HttpStatus.BAD_GATEWAY),
	RESOURCE_NOT_FOUND("COMMON_003", "Resource not found", HttpStatus.NOT_FOUND),
	DUPLICATE_RESOURCE("COMMON_004", "Resource already exists", HttpStatus.CONFLICT),
	INVALID_OPERATION("COMMON_005", "Operation not allowed in current state", HttpStatus.UNPROCESSABLE_ENTITY),

	// Auth
	AUTH_INVALID_CREDENTIALS("AUTH_001", "Invalid email or password", HttpStatus.UNAUTHORIZED),
	AUTH_EMAIL_ALREADY_EXISTS("AUTH_002", "Email already registered", HttpStatus.CONFLICT),
	AUTH_WRONG_OLD_PASSWORD("AUTH_003", "Current password is incorrect", HttpStatus.BAD_REQUEST),
	AUTH_INVALID_OTP("AUTH_004", "Invalid OTP code", HttpStatus.BAD_REQUEST),
	AUTH_OTP_EXPIRED("AUTH_005", "OTP has expired", HttpStatus.BAD_REQUEST),
	AUTH_ACCESS_DENIED("AUTH_006", "Access denied", HttpStatus.FORBIDDEN),
	AUTH_INVALID_TOKEN("AUTH_007", "Token invalid", HttpStatus.UNAUTHORIZED),
	AUTH_MISSING_TOKEN("AUTH_013", "Missing Token", HttpStatus.UNAUTHORIZED),
	AUTH_ACCOUNT_LOCKED("AUTH_008", "Account is locked", HttpStatus.FORBIDDEN),
	AUTH_INVALID_ROLE_ASSIGNMENT("AUTH_009", "You are not allowed to assign this role", HttpStatus.FORBIDDEN),
	AUTH_ROLE_NOT_FOUND("AUTH_010", "Role not found", HttpStatus.NOT_FOUND),
	AUTH_USER_NOT_FOUND("AUTH_011", "User not found", HttpStatus.NOT_FOUND),
	AUTH_PHONE_ALREADY_EXISTS("AUTH_012", "Phone number already registered", HttpStatus.CONFLICT),

	// Profile
	PROFILE_ALREADY_EXISTS("PROFILE_001", "Profile already exists", HttpStatus.CONFLICT),
	PROFILE_NOT_FOUND("PROFILE_002", "Profile not found", HttpStatus.NOT_FOUND),
	PROFILE_ONLY_FOR_PLAYER("PROFILE_003", "Only Player accounts can create a player profile", HttpStatus.FORBIDDEN),

	// Tournament config
	FORMAT_CODE_EXISTS("FORMAT_001", "Format code already exists", HttpStatus.CONFLICT),
	FORMAT_NOT_FOUND("FORMAT_002", "Format not found", HttpStatus.NOT_FOUND),
	FORMAT_NOT_READY("FORMAT_003", "Format default config is not ready", HttpStatus.UNPROCESSABLE_ENTITY),
	INVALID_FIELD_KEY("FORMAT_004", "Field key is not in catalog", HttpStatus.BAD_REQUEST),
	INVALID_FIELD_FOR_FORMAT("FORMAT_005", "Field does not belong to format", HttpStatus.BAD_REQUEST),
	SETUP_INCOMPLETE("FORMAT_006", "Format setup is incomplete", HttpStatus.UNPROCESSABLE_ENTITY),
	ALREADY_BOOTSTRAPPED("FORMAT_007", "Format already has default config", HttpStatus.CONFLICT),
	CONFIG_VALIDATION_FAILED("FORMAT_008", "Tournament config validation failed", HttpStatus.BAD_REQUEST),
	CONFIG_INCOMPLETE("FORMAT_009", "Tournament config is incomplete", HttpStatus.UNPROCESSABLE_ENTITY),
	INVALID_STATUS_TRANSITION("FORMAT_010", "Invalid status transition", HttpStatus.UNPROCESSABLE_ENTITY),
	GAME_TYPE_NOT_FOUND("FORMAT_011", "Game type not found", HttpStatus.NOT_FOUND);
	private final String code;
	private final String message;
	private final HttpStatus httpStatus;
}
