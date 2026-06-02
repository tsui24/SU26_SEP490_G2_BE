package com.capstone.su26_sep490_g2_be.exception;

import com.capstone.su26_sep490_g2_be.dto.response.ConfigValidationDetailResponse;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import lombok.Getter;

import java.util.List;

@Getter
public class ConfigValidationException extends BusinessException {

	private final List<ConfigValidationDetailResponse> details;

	public ConfigValidationException(ErrorCode errorCode, List<ConfigValidationDetailResponse> details) {
		super(errorCode);
		this.details = details;
	}
}
