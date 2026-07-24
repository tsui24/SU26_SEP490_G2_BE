package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AssignMatchRequest {

    /** null = bỏ gán trọng tài */
    private Long assignedStaffId;

    /** Legacy fallback — số bàn tự do, không có FK. Ưu tiên dùng {@link #tableId} khi có. */
    @Min(1)
    private Integer tableNo;

    /** true = bỏ gán trọng tài */
    private Boolean clearAssignedStaff;

    /** Bàn lấy từ pool bàn của Owner — ưu tiên hơn {@link #tableNo}. */
    private Long tableId;

    /** true = bỏ gán bàn (xoá cả tableId lẫn tableNo) */
    private Boolean clearTable;

    private Instant scheduledAt;

    /** true = bỏ giờ thi đấu */
    private Boolean clearScheduledAt;

    /** true = trả trận về chế độ tự động xếp lịch (bỏ khóa, auto được phép xếp lại bàn/giờ). */
    private Boolean resetToAuto;

    /** true = bỏ qua cảnh báo trùng bàn/giờ (owner đã xác nhận vẫn lưu). */
    private Boolean ignoreTableConflict;
}
