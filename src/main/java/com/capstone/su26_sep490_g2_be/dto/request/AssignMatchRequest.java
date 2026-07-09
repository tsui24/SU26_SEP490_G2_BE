package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignMatchRequest {

    /** null = bỏ gán trọng tài */
    private Long assignedStaffId;

    @Min(1)
    private Integer tableNo;

    /** true = bỏ gán trọng tài */
    private Boolean clearAssignedStaff;
}
