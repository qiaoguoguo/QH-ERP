package com.qherp.api.system.production;

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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/production")
public class ProductionAdminController {

	private final ProductionAdminService productionAdminService;

	public ProductionAdminController(ProductionAdminService productionAdminService) {
		this.productionAdminService = productionAdminService;
	}

	@GetMapping("/work-orders")
	public ApiResponse<PageResponse<ProductionAdminService.WorkOrderSummaryResponse>> workOrders(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long productMaterialId, @RequestParam(required = false) LocalDate dateFrom,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) String ownershipType,
			@RequestParam(required = false) Long sourceMrpSuggestionId,
			@RequestParam(required = false) LocalDate dateTo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.productionAdminService.workOrders(keyword, status, productMaterialId, dateFrom,
				dateTo, projectId, ownershipType, sourceMrpSuggestionId, page, pageSize, currentUser));
	}

	@GetMapping("/work-orders/{id}")
	public ApiResponse<ProductionAdminService.WorkOrderDetailResponse> workOrder(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.productionAdminService.workOrder(id, currentUser));
	}

	@PostMapping("/work-orders")
	public ApiResponse<ProductionAdminService.WorkOrderDetailResponse> createWorkOrder(
			@Valid @RequestBody ProductionAdminService.WorkOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.createWorkOrder(request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{id}")
	public ApiResponse<ProductionAdminService.WorkOrderDetailResponse> updateWorkOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionAdminService.WorkOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.updateWorkOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{id}/release")
	public ApiResponse<ProductionAdminService.WorkOrderDetailResponse> releaseWorkOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionAdminService.ProductionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.releaseWorkOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{id}/complete")
	public ApiResponse<ProductionAdminService.WorkOrderDetailResponse> completeWorkOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionAdminService.ProductionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.completeWorkOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{id}/cancel")
	public ApiResponse<ProductionAdminService.WorkOrderDetailResponse> cancelWorkOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionAdminService.ProductionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.cancelWorkOrder(id, request, currentUser, servletRequest));
	}

	@GetMapping("/work-orders/{workOrderId}/material-issues")
	public ApiResponse<PageResponse<ProductionAdminService.MaterialIssueSummaryResponse>> materialIssues(
			@PathVariable Long workOrderId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.productionAdminService.materialIssues(workOrderId, page, pageSize));
	}

	@GetMapping("/work-orders/{workOrderId}/material-issues/{id}")
	public ApiResponse<ProductionAdminService.MaterialIssueDetailResponse> materialIssue(
			@PathVariable Long workOrderId, @PathVariable Long id) {
		return ApiResponse.ok(this.productionAdminService.materialIssue(workOrderId, id));
	}

	@PostMapping("/work-orders/{workOrderId}/material-issues")
	public ApiResponse<ProductionAdminService.MaterialIssueDetailResponse> createMaterialIssue(
			@PathVariable Long workOrderId,
			@Valid @RequestBody ProductionAdminService.MaterialIssueRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(
				this.productionAdminService.createMaterialIssue(workOrderId, request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{workOrderId}/material-issues/{id}")
	public ApiResponse<ProductionAdminService.MaterialIssueDetailResponse> updateMaterialIssue(
			@PathVariable Long workOrderId, @PathVariable Long id,
			@Valid @RequestBody ProductionAdminService.MaterialIssueRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.updateMaterialIssue(workOrderId, id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/work-orders/{workOrderId}/material-issues/{id}/post")
	public ApiResponse<ProductionAdminService.MaterialIssueDetailResponse> postMaterialIssue(
			@PathVariable Long workOrderId, @PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody ProductionAdminService.ProductionActionRequest request,
			HttpServletRequest servletRequest) {
		return ApiResponse
			.ok(this.productionAdminService.postMaterialIssue(workOrderId, id, request, currentUser, servletRequest));
	}

	@GetMapping("/work-orders/{workOrderId}/reports")
	public ApiResponse<PageResponse<ProductionAdminService.WorkReportResponse>> reports(@PathVariable Long workOrderId,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.productionAdminService.reports(workOrderId, page, pageSize));
	}

	@GetMapping("/work-orders/{workOrderId}/reports/{id}")
	public ApiResponse<ProductionAdminService.WorkReportResponse> report(@PathVariable Long workOrderId,
			@PathVariable Long id) {
		return ApiResponse.ok(this.productionAdminService.report(workOrderId, id));
	}

	@PostMapping("/work-orders/{workOrderId}/reports")
	public ApiResponse<ProductionAdminService.WorkReportResponse> createReport(@PathVariable Long workOrderId,
			@Valid @RequestBody ProductionAdminService.WorkReportRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse
			.ok(this.productionAdminService.createReport(workOrderId, request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{workOrderId}/reports/{id}")
	public ApiResponse<ProductionAdminService.WorkReportResponse> updateReport(@PathVariable Long workOrderId,
			@PathVariable Long id, @Valid @RequestBody ProductionAdminService.WorkReportRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse
			.ok(this.productionAdminService.updateReport(workOrderId, id, request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{workOrderId}/reports/{id}/post")
	public ApiResponse<ProductionAdminService.WorkReportResponse> postReport(@PathVariable Long workOrderId,
			@PathVariable Long id, @Valid @RequestBody ProductionAdminService.ProductionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.postReport(workOrderId, id, request, currentUser,
				servletRequest));
	}

	@GetMapping("/work-orders/{workOrderId}/completion-receipts")
	public ApiResponse<PageResponse<ProductionAdminService.CompletionReceiptResponse>> completionReceipts(
			@PathVariable Long workOrderId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.productionAdminService.completionReceipts(workOrderId, page, pageSize));
	}

	@GetMapping("/work-orders/{workOrderId}/completion-receipts/{id}")
	public ApiResponse<ProductionAdminService.CompletionReceiptResponse> completionReceipt(
			@PathVariable Long workOrderId, @PathVariable Long id) {
		return ApiResponse.ok(this.productionAdminService.completionReceipt(workOrderId, id));
	}

	@PostMapping("/work-orders/{workOrderId}/completion-receipts")
	public ApiResponse<ProductionAdminService.CompletionReceiptResponse> createCompletionReceipt(
			@PathVariable Long workOrderId,
			@Valid @RequestBody ProductionAdminService.CompletionReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(
				this.productionAdminService.createCompletionReceipt(workOrderId, request, currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{workOrderId}/completion-receipts/{id}")
	public ApiResponse<ProductionAdminService.CompletionReceiptResponse> updateCompletionReceipt(
			@PathVariable Long workOrderId, @PathVariable Long id,
			@Valid @RequestBody ProductionAdminService.CompletionReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.productionAdminService.updateCompletionReceipt(workOrderId, id, request,
				currentUser, servletRequest));
	}

	@PutMapping("/work-orders/{workOrderId}/completion-receipts/{id}/post")
	public ApiResponse<ProductionAdminService.CompletionReceiptResponse> postCompletionReceipt(
			@PathVariable Long workOrderId, @PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody ProductionAdminService.ProductionActionRequest request,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(
				this.productionAdminService.postCompletionReceipt(workOrderId, id, request, currentUser, servletRequest));
	}

}
