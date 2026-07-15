package com.qherp.api.system.procurement;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.platform.PlatformApprovalService;
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
@RequestMapping("/api/admin/procurement/requisitions")
public class ProcurementRequisitionAdminController {

	private final ProcurementRequisitionService requisitionService;

	public ProcurementRequisitionAdminController(ProcurementRequisitionService requisitionService) {
		this.requisitionService = requisitionService;
	}

	@GetMapping
	public ApiResponse<PageResponse<ProcurementRequisitionService.PurchaseRequisitionSummaryResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String procurementMode,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String approvalStatus,
			@RequestParam(required = false) java.time.LocalDate requiredDateFrom,
			@RequestParam(required = false) java.time.LocalDate requiredDateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.requisitionService.list(keyword, procurementMode, projectId, status,
				approvalStatus, requiredDateFrom, requiredDateTo, page, pageSize));
	}

	@PostMapping
	public ApiResponse<ProcurementRequisitionService.PurchaseRequisitionDetailResponse> create(
			@Valid @RequestBody ProcurementRequisitionService.PurchaseRequisitionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.requisitionService.create(request, currentUser, servletRequest));
	}

	@GetMapping("/{id}")
	public ApiResponse<ProcurementRequisitionService.PurchaseRequisitionDetailResponse> detail(@PathVariable Long id) {
		return ApiResponse.ok(this.requisitionService.detail(id));
	}

	@PutMapping("/{id}")
	public ApiResponse<ProcurementRequisitionService.PurchaseRequisitionDetailResponse> update(@PathVariable Long id,
			@Valid @RequestBody ProcurementRequisitionService.PurchaseRequisitionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.requisitionService.update(id, request, currentUser, servletRequest));
	}

	@PostMapping("/{id}/submit-approval")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitApproval(
			@PathVariable Long id, @Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.requisitionService.submitApproval(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/cancel")
	public ApiResponse<ProcurementRequisitionService.PurchaseRequisitionDetailResponse> cancel(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.requisitionService.cancel(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/close")
	public ApiResponse<ProcurementRequisitionService.PurchaseRequisitionDetailResponse> close(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.requisitionService.close(id, request, currentUser, servletRequest));
	}

}
