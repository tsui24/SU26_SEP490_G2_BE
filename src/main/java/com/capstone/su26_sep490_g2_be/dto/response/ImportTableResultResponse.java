package com.capstone.su26_sep490_g2_be.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ImportTableResultResponse {
    private int totalRows;
    private int imported;
    private int skipped;
    private List<String> errors;
}
