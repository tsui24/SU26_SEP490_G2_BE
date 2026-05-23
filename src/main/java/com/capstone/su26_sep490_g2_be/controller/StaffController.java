package com.capstone.su26_sep490_g2_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Staff", description = "Staff / Referee APIs — requires STAFF role")
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

	// APIs cho nhân viên / trọng tài sẽ thêm tại đây
}
