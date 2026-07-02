package com.qherp.api.system.master;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/master/material-categories")
public class MaterialCategoryAdminController {

	private final MaterialCategoryAdminService materialCategoryAdminService;

	public MaterialCategoryAdminController(MaterialCategoryAdminService materialCategoryAdminService) {
		this.materialCategoryAdminService = materialCategoryAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<MaterialCategoryAdminService.CategoryResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.materialCategoryAdminService.list(keyword, status, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<MaterialCategoryAdminService.CategoryResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.materialCategoryAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<MaterialCategoryAdminService.CategoryResponse> create(
			@Valid @RequestBody MaterialCategoryAdminService.CategoryRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialCategoryAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<MaterialCategoryAdminService.CategoryResponse> update(@PathVariable Long id,
			@Valid @RequestBody MaterialCategoryAdminService.CategoryRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialCategoryAdminService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<MaterialCategoryAdminService.CategoryResponse> enable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialCategoryAdminService.enable(id, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<MaterialCategoryAdminService.CategoryResponse> disable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialCategoryAdminService.disable(id, currentUser, servletRequest));
	}

}
