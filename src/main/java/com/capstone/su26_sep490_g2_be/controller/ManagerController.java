package com.capstone.su26_sep490_g2_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Manager", description = "Manager APIs — requires MANAGER role")
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerController {

	// APIs quản lý cơ sở sẽ thêm tại đây
}
