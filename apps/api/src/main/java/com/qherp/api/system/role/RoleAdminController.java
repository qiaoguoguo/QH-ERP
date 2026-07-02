package com.qherp.api.system.role;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleAdminController {

	private final RoleAdminService roleAdminService;

	private final CurrentUserService currentUserService;

	public RoleAdminController(RoleAdminService roleAdminService, CurrentUserService currentUserService) {
		this.roleAdminService = roleAdminService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	public ApiResponse<PageResponse<RoleAdminService.RoleResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.roleAdminService.list(keyword, status, page, pageSize));
	}

	@PostMapping
	public ApiResponse<RoleAdminService.RoleResponse> create(
			@Valid @RequestBody RoleAdminService.CreateRoleRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.roleAdminService.create(request, currentUser(), servletRequest));
	}

	@GetMapping("/{id}")
	public ApiResponse<RoleAdminService.RoleResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.roleAdminService.get(id));
	}

	@PutMapping("/{id}")
	public ApiResponse<RoleAdminService.RoleResponse> update(@PathVariable Long id,
			@Valid @RequestBody RoleAdminService.UpdateRoleRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.roleAdminService.update(id, request, currentUser(), servletRequest));
	}

	@PutMapping("/{id}/permissions")
	public ApiResponse<RoleAdminService.RoleResponse> savePermissions(@PathVariable Long id,
			@RequestBody RoleAdminService.SaveRolePermissionsRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.roleAdminService.savePermissions(id, request, currentUser(), servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<RoleAdminService.RoleResponse> enable(@PathVariable Long id, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.roleAdminService.enable(id, currentUser(), servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<RoleAdminService.RoleResponse> disable(@PathVariable Long id, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.roleAdminService.disable(id, currentUser(), servletRequest));
	}

	private CurrentUser currentUser() {
		return this.currentUserService.requireCurrentUser();
	}

}
