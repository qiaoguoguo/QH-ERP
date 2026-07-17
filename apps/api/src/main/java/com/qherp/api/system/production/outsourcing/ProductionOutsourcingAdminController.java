package com.qherp.api.system.production.outsourcing;

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
@RequestMapping("/api/admin/production/outsourcing-orders")
public class ProductionOutsourcingAdminController {

	private final ProductionOutsourcingService outsourcingService;

	public ProductionOutsourcingAdminController(ProductionOutsourcingService outsourcingService) {
		this.outsourcingService = outsourcingService;
	}

	@GetMapping
	public ApiResponse<PageResponse<ProductionOutsourcingService.OutsourcingOrderSummaryResponse>> orders(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) Long supplierId, @RequestParam(required = false) Long productMaterialId,
			@RequestParam(required = false) String status, @RequestParam(required = false) LocalDate plannedDateFrom,
			@RequestParam(required = false) LocalDate plannedDateTo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.outsourcingService.orders(keyword, projectId, supplierId, productMaterialId, status,
				plannedDateFrom, plannedDateTo, page, pageSize, currentUser));
	}

	@GetMapping("/{id}")
	public ApiResponse<ProductionOutsourcingService.OutsourcingOrderDetailResponse> order(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.outsourcingService.order(id, currentUser));
	}

	@PostMapping
	public ApiResponse<ProductionOutsourcingService.OutsourcingOrderDetailResponse> createOrder(
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.createOrder(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<ProductionOutsourcingService.OutsourcingOrderDetailResponse> updateOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.updateOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/release")
	public ApiResponse<ProductionOutsourcingService.OutsourcingOrderDetailResponse> releaseOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.releaseOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/close")
	public ApiResponse<ProductionOutsourcingService.OutsourcingOrderDetailResponse> closeOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.closeOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/cancel")
	public ApiResponse<ProductionOutsourcingService.OutsourcingOrderDetailResponse> cancelOrder(@PathVariable Long id,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.cancelOrder(id, request, currentUser, servletRequest));
	}

	@PostMapping("/{orderId}/material-issues")
	public ApiResponse<ProductionOutsourcingService.OutsourcingIssueDetailResponse> createIssue(
			@PathVariable Long orderId, @Valid @RequestBody ProductionOutsourcingService.OutsourcingIssueRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.createIssue(orderId, request, currentUser, servletRequest));
	}

	@GetMapping("/{orderId}/material-issues")
	public ApiResponse<PageResponse<ProductionOutsourcingService.OutsourcingDocumentSummaryResponse>> issues(
			@PathVariable Long orderId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.outsourcingService.issues(orderId, page, pageSize, currentUser));
	}

	@GetMapping("/{orderId}/material-issues/{id}")
	public ApiResponse<ProductionOutsourcingService.OutsourcingIssueDetailResponse> issue(@PathVariable Long orderId,
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.outsourcingService.issue(orderId, id, currentUser));
	}

	@PutMapping("/{orderId}/material-issues/{id}")
	public ApiResponse<ProductionOutsourcingService.OutsourcingIssueDetailResponse> updateIssue(
			@PathVariable Long orderId, @PathVariable Long id,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingIssueRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.updateIssue(orderId, id, request, currentUser, servletRequest));
	}

	@PutMapping("/{orderId}/material-issues/{id}/post")
	public ApiResponse<ProductionOutsourcingService.OutsourcingIssueDetailResponse> postIssue(
			@PathVariable Long orderId, @PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingActionRequest request,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.postIssue(orderId, id, request, currentUser, servletRequest));
	}

	@PutMapping("/{orderId}/material-issues/{id}/cancel")
	public ApiResponse<ProductionOutsourcingService.OutsourcingIssueDetailResponse> cancelIssue(
			@PathVariable Long orderId, @PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingActionRequest request,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.cancelIssue(orderId, id, request, currentUser, servletRequest));
	}

	@PostMapping("/{orderId}/receipts")
	public ApiResponse<ProductionOutsourcingService.OutsourcingReceiptResponse> createReceipt(
			@PathVariable Long orderId,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.createReceipt(orderId, request, currentUser, servletRequest));
	}

	@GetMapping("/{orderId}/receipts")
	public ApiResponse<PageResponse<ProductionOutsourcingService.OutsourcingDocumentSummaryResponse>> receipts(
			@PathVariable Long orderId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.outsourcingService.receipts(orderId, page, pageSize, currentUser));
	}

	@GetMapping("/{orderId}/receipts/{id}")
	public ApiResponse<ProductionOutsourcingService.OutsourcingReceiptResponse> receipt(@PathVariable Long orderId,
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.outsourcingService.receipt(orderId, id, currentUser));
	}

	@PutMapping("/{orderId}/receipts/{id}")
	public ApiResponse<ProductionOutsourcingService.OutsourcingReceiptResponse> updateReceipt(
			@PathVariable Long orderId, @PathVariable Long id,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.updateReceipt(orderId, id, request, currentUser, servletRequest));
	}

	@PutMapping("/{orderId}/receipts/{id}/post")
	public ApiResponse<ProductionOutsourcingService.OutsourcingReceiptResponse> postReceipt(
			@PathVariable Long orderId, @PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingActionRequest request,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.postReceipt(orderId, id, request, currentUser, servletRequest));
	}

	@PutMapping("/{orderId}/receipts/{id}/cancel")
	public ApiResponse<ProductionOutsourcingService.OutsourcingReceiptResponse> cancelReceipt(
			@PathVariable Long orderId, @PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody ProductionOutsourcingService.OutsourcingActionRequest request,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.outsourcingService.cancelReceipt(orderId, id, request, currentUser, servletRequest));
	}

}
