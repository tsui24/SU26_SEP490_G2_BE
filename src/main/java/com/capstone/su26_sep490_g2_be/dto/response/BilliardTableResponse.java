package com.capstone.su26_sep490_g2_be.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Thông tin bàn trong pool bàn của chuỗi")
public class BilliardTableResponse {

	private Long id;
	private String name;
	private Integer tableNumber;
	private String tableType;
	private String status;
	private Long branchId;
	private String branchName;
	private Instant createdAt;
	private Instant updatedAt;
}
