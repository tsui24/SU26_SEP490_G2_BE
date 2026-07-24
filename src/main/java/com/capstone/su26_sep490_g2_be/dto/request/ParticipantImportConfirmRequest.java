package com.capstone.su26_sep490_g2_be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ParticipantImportConfirmRequest {

    @NotEmpty(message = "Danh sách dòng import không được để trống")
    @Valid
    private List<ParticipantImportRowRequest> rows;
}
