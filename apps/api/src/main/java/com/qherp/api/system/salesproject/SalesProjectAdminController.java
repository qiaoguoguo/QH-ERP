package com.qherp.api.system.salesproject;

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
@RequestMapping("/api/admin/sales-projects")
public class SalesProjectAdminController {

	private final SalesProjectAdminService salesProjectAdminService;

	public SalesProjectAdminController(SalesProjectAdminService salesProjectAdminService) {
		this.salesProjectAdminService = salesProjectAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<SalesProjectAdminService.ProjectResponse>> projects(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) String status, @RequestParam(required = false) Long ownerUserId,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.salesProjectAdminService.projects(keyword, customerId, status, ownerUserId, page,
				pageSize, currentUser));
	}

	@GetMapping("/{id}")
	public ApiResponse<SalesProjectAdminService.ProjectResponse> project(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.salesProjectAdminService.get(id, currentUser));
	}

	@GetMapping("/{id}/fulfillment")
	public ApiResponse<SalesProjectAdminService.SalesFulfillmentResponse> salesFulfillment(@PathVariable Long id,
			@RequestParam(defaultValue = "false") boolean includeLegacy,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.salesProjectAdminService.salesFulfillment(id, includeLegacy, currentUser));
	}

	@PostMapping("/{id}/close-sales-fulfillment")
	public ApiResponse<SalesProjectAdminService.SalesFulfillmentResponse> closeSalesFulfillment(@PathVariable Long id,
			@Valid @RequestBody SalesProjectAdminService.FulfillmentCloseRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectAdminService.closeSalesFulfillment(id, request, currentUser,
				servletRequest));
	}

	@PostMapping
	public ApiResponse<SalesProjectAdminService.ProjectResponse> createProject(
			@Valid @RequestBody SalesProjectAdminService.ProjectCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<SalesProjectAdminService.ProjectResponse> updateProject(@PathVariable Long id,
			@Valid @RequestBody SalesProjectAdminService.ProjectUpdateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectAdminService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/activate")
	public ApiResponse<SalesProjectAdminService.ProjectResponse> activateProject(@PathVariable Long id,
			@Valid @RequestBody SalesProjectAdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectAdminService.activate(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/close")
	public ApiResponse<SalesProjectAdminService.ProjectResponse> closeProject(@PathVariable Long id,
			@Valid @RequestBody SalesProjectAdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectAdminService.close(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/cancel")
	public ApiResponse<SalesProjectAdminService.ProjectResponse> cancelProject(@PathVariable Long id,
			@Valid @RequestBody SalesProjectAdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectAdminService.cancel(id, request, currentUser, servletRequest));
	}

	@GetMapping("/owner-candidates")
	public ApiResponse<PageResponse<SalesProjectAdminService.OwnerCandidateResponse>> ownerCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesProjectAdminService.ownerCandidates(keyword, page, pageSize));
	}

}
