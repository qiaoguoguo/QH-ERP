package com.qherp.api.system.permission;

import com.qherp.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions")
public class PermissionAdminController {

	private final PermissionAdminService permissionAdminService;

	public PermissionAdminController(PermissionAdminService permissionAdminService) {
		this.permissionAdminService = permissionAdminService;
	}

	@GetMapping("/tree")
	public ApiResponse<List<PermissionAdminService.PermissionNode>> tree() {
		return ApiResponse.ok(this.permissionAdminService.tree());
	}

}
