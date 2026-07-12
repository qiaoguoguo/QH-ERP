package com.qherp.api.system.salesproject;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class SalesProjectContractAdminController {

	private final SalesProjectContractService salesProjectContractService;

	public SalesProjectContractAdminController(SalesProjectContractService salesProjectContractService) {
		this.salesProjectContractService = salesProjectContractService;
	}

	@GetMapping("/api/admin/sales-projects/{projectId}/contracts")
	public ApiResponse<PageResponse<SalesProjectContractService.ContractResponse>> contracts(@PathVariable Long projectId,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String contractType,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedDateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesProjectContractService.listByProject(projectId, keyword, contractType, status,
				signedDateFrom, signedDateTo, page, pageSize));
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
