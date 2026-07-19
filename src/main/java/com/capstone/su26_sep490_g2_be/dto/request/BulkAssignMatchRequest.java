package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class BulkAssignMatchRequest {

    @NotEmpty(message = "Vui lòng chọn ít nhất một trận đấu")
    private List<Long> matchIds;

    private Long assignedStaffId;

    @Min(1)
    private Integer tableNo;

    private Boolean clearAssignedStaff;

    private Long tableId;

    private Boolean clearTable;

    private Instant scheduledAt;

    private Boolean clearScheduledAt;
}
