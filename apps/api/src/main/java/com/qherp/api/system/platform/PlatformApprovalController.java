package com.qherp.api.system.platform;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class PlatformApprovalController {

	private final PlatformApprovalService approvalService;

	public PlatformApprovalController(PlatformApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@PostMapping("/approvals/sales-project-contract-activation/{contractId}/submit")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitContractActivation(
			@PathVariable Long contractId,
			@Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(
				this.approvalService.submitContractActivation(contractId, request, currentUser, servletRequest));
	}

	@PostMapping("/approvals/bom-eco-application/{ecoId}/submit")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitEcoApplication(@PathVariable Long ecoId,
			@Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.approvalService.submitEcoApplication(ecoId, request, currentUser, servletRequest));
	}

	@GetMapping("/approvals/{id}")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> get(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.approvalService.get(id, currentUser));
	}

	@GetMapping("/approval-tasks")
	public ApiResponse<PageResponse<PlatformApprovalService.ApprovalTaskRecord>> listTasks(
			@RequestParam(required = false, defaultValue = "TODO") String scope,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.approvalService.listTasks(scope, page, pageSize, currentUser));
	}

	@PostMapping("/approval-tasks/{id}/approve")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> approve(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.approvalService.approve(id, request, currentUser, servletRequest));
	}

	@PostMapping("/approval-tasks/{id}/reject")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> reject(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.approvalService.reject(id, request, currentUser, servletRequest));
	}

	@PostMapping("/approvals/{id}/withdraw")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> withdraw(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.approvalService.withdraw(id, request, currentUser, servletRequest));
	}

	@PostMapping("/approvals/{id}/cancel")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> cancel(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.approvalService.cancel(id, request, currentUser, servletRequest));
	}

}
