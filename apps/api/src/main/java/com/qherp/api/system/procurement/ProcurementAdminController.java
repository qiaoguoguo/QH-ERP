package com.qherp.api.system.procurement;

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
@RequestMapping("/api/admin/procurement")
public class ProcurementAdminController {

	private final ProcurementAdminService procurementAdminService;

	public ProcurementAdminController(ProcurementAdminService procurementAdminService) {
		this.procurementAdminService = procurementAdminService;
	}

	@GetMapping("/orders")
	public ApiResponse<PageResponse<ProcurementAdminService.PurchaseOrderSummaryResponse>> orders(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) String status, @RequestParam(required = false) LocalDate dateFrom,
			@RequestParam(required = false) LocalDate dateTo,
			@RequestParam(required = false) LocalDate expectedDateFrom,
			@RequestParam(required = false) LocalDate expectedDateTo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.procurementAdminService.orders(keyword, supplierId, status, dateFrom, dateTo,
				expectedDateFrom, expectedDateTo, page, pageSize));
	}

	@GetMapping("/orders/{id}")
	public ApiResponse<ProcurementAdminService.PurchaseOrderDetailResponse> order(@PathVariable Long id) {
		return ApiResponse.ok(this.procurementAdminService.order(id));
	}

	@PostMapping("/orders")
	public ApiResponse<ProcurementAdminService.PurchaseOrderDetailResponse> createOrder(
			@Valid @RequestBody ProcurementAdminService.PurchaseOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.createOrder(request, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}")
	public ApiResponse<ProcurementAdminService.PurchaseOrderDetailResponse> updateOrder(@PathVariable Long id,
			@Valid @RequestBody ProcurementAdminService.PurchaseOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.updateOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}/confirm")
	public ApiResponse<ProcurementAdminService.PurchaseOrderDetailResponse> confirmOrder(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.confirmOrder(id, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}/cancel")
	public ApiResponse<ProcurementAdminService.PurchaseOrderDetailResponse> cancelOrder(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.cancelOrder(id, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}/close")
	public ApiResponse<ProcurementAdminService.PurchaseOrderDetailResponse> closeOrder(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.closeOrder(id, currentUser, servletRequest));
	}

	@GetMapping("/receipts")
	public ApiResponse<PageResponse<ProcurementAdminService.PurchaseReceiptSummaryResponse>> receipts(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String status,
			@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
			@RequestParam(required = false) Long orderId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.procurementAdminService.receipts(keyword, supplierId, warehouseId, status, dateFrom,
				dateTo, orderId, page, pageSize));
	}

	@PostMapping("/orders/{id}/receipts")
	public ApiResponse<ProcurementAdminService.PurchaseReceiptDetailResponse> createReceipt(@PathVariable Long id,
			@Valid @RequestBody ProcurementAdminService.PurchaseReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.createReceipt(id, request, currentUser, servletRequest));
	}

	@GetMapping("/receipts/{id}")
	public ApiResponse<ProcurementAdminService.PurchaseReceiptDetailResponse> receipt(@PathVariable Long id) {
		return ApiResponse.ok(this.procurementAdminService.receipt(id));
	}

	@PutMapping("/receipts/{id}")
	public ApiResponse<ProcurementAdminService.PurchaseReceiptDetailResponse> updateReceipt(@PathVariable Long id,
			@Valid @RequestBody ProcurementAdminService.PurchaseReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.updateReceipt(id, request, currentUser, servletRequest));
	}

	@PutMapping("/receipts/{id}/post")
	public ApiResponse<ProcurementAdminService.PurchaseReceiptDetailResponse> postReceipt(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.procurementAdminService.postReceipt(id, currentUser, servletRequest));
	}

}
