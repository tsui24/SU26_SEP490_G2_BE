package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ParticipantImportPreviewResponse {
    private int totalRows;
    private int validCount;
    private int invalidCount;
    private List<ParticipantImportPreviewRowResponse> rows;
}
