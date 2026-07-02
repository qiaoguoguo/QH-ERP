package com.qherp.api.system.user;

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
@RequestMapping("/api/admin/users")
public class UserAdminController {

	private final UserAdminService userAdminService;

	private final CurrentUserService currentUserService;

	public UserAdminController(UserAdminService userAdminService, CurrentUserService currentUserService) {
		this.userAdminService = userAdminService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	public ApiResponse<PageResponse<UserAdminService.UserResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.userAdminService.list(keyword, status, page, pageSize));
	}

	@PostMapping
	public ApiResponse<UserAdminService.UserResponse> create(
			@Valid @RequestBody UserAdminService.CreateUserRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.userAdminService.create(request, currentUser(), servletRequest));
	}

	@GetMapping("/{id}")
	public ApiResponse<UserAdminService.UserResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.userAdminService.get(id));
	}

	@PutMapping("/{id}")
	public ApiResponse<UserAdminService.UserResponse> update(@PathVariable Long id,
			@Valid @RequestBody UserAdminService.UpdateUserRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.userAdminService.update(id, request, currentUser(), servletRequest));
	}

	@PutMapping("/{id}/password")
	public ApiResponse<UserAdminService.UserResponse> resetPassword(@PathVariable Long id,
			@Valid @RequestBody UserAdminService.ResetPasswordRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.userAdminService.resetPassword(id, request, currentUser(), servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<UserAdminService.UserResponse> enable(@PathVariable Long id, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.userAdminService.enable(id, currentUser(), servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<UserAdminService.UserResponse> disable(@PathVariable Long id, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.userAdminService.disable(id, currentUser(), servletRequest));
	}

	private CurrentUser currentUser() {
		return this.currentUserService.requireCurrentUser();
	}

}
