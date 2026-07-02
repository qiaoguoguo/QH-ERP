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
@RequestMapping("/api/admin/master/materials")
public class MaterialAdminController {

	private final MaterialAdminService materialAdminService;

	public MaterialAdminController(MaterialAdminService materialAdminService) {
		this.materialAdminService = materialAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<MaterialAdminService.MaterialResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) String materialType,
			@RequestParam(required = false) String sourceType, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.materialAdminService.list(keyword, status, categoryId, materialType, sourceType, page,
				pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<MaterialAdminService.MaterialResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.materialAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<MaterialAdminService.MaterialResponse> create(
			@Valid @RequestBody MaterialAdminService.MaterialRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<MaterialAdminService.MaterialResponse> update(@PathVariable Long id,
			@Valid @RequestBody MaterialAdminService.MaterialRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialAdminService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<MaterialAdminService.MaterialResponse> enable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialAdminService.enable(id, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<MaterialAdminService.MaterialResponse> disable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialAdminService.disable(id, currentUser, servletRequest));
	}

}
