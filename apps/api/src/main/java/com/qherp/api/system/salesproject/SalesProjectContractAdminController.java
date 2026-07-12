package com.qherp.api.system.salesproject;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SalesProjectContractAdminController {

	private final SalesProjectContractService salesProjectContractService;

	public SalesProjectContractAdminController(SalesProjectContractService salesProjectContractService) {
		this.salesProjectContractService = salesProjectContractService;
	}

	@GetMapping("/api/admin/sales-projects/{projectId}/contracts")
	public ApiResponse<List<SalesProjectContractService.ContractResponse>> contracts(@PathVariable Long projectId) {
		return ApiResponse.ok(this.salesProjectContractService.listByProject(projectId));
	}

	@PostMapping("/api/admin/sales-projects/{projectId}/contracts")
	public ApiResponse<SalesProjectContractService.ContractResponse> createContract(@PathVariable Long projectId,
			@Valid @RequestBody SalesProjectContractService.ContractCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectContractService.create(projectId, request, currentUser, servletRequest));
	}

	@GetMapping("/api/admin/sales-project-contracts/{id}")
	public ApiResponse<SalesProjectContractService.ContractResponse> contract(@PathVariable Long id) {
		return ApiResponse.ok(this.salesProjectContractService.get(id));
	}

	@PutMapping("/api/admin/sales-project-contracts/{id}")
	public ApiResponse<SalesProjectContractService.ContractResponse> updateContract(@PathVariable Long id,
			@Valid @RequestBody SalesProjectContractService.ContractUpdateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectContractService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/api/admin/sales-project-contracts/{id}/activate")
	public ApiResponse<SalesProjectContractService.ContractResponse> activateContract(@PathVariable Long id,
			@Valid @RequestBody SalesProjectContractService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectContractService.activate(id, request, currentUser, servletRequest));
	}

	@PutMapping("/api/admin/sales-project-contracts/{id}/close")
	public ApiResponse<SalesProjectContractService.ContractResponse> closeContract(@PathVariable Long id,
			@Valid @RequestBody SalesProjectContractService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectContractService.close(id, request, currentUser, servletRequest));
	}

	@PutMapping("/api/admin/sales-project-contracts/{id}/terminate")
	public ApiResponse<SalesProjectContractService.ContractResponse> terminateContract(@PathVariable Long id,
			@Valid @RequestBody SalesProjectContractService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectContractService.terminate(id, request, currentUser, servletRequest));
	}

	@PutMapping("/api/admin/sales-project-contracts/{id}/cancel")
	public ApiResponse<SalesProjectContractService.ContractResponse> cancelContract(@PathVariable Long id,
			@Valid @RequestBody SalesProjectContractService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesProjectContractService.cancel(id, request, currentUser, servletRequest));
	}

}
