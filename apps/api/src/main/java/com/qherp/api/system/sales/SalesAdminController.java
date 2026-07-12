package com.qherp.api.system.sales;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/admin/sales")
public class SalesAdminController {

	private final SalesAdminService salesAdminService;

	public SalesAdminController(SalesAdminService salesAdminService) {
		this.salesAdminService = salesAdminService;
	}

	@GetMapping("/orders")
	public ApiResponse<PageResponse<SalesAdminService.SalesOrderSummaryResponse>> orders(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateTo,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Long contractId,
			@RequestParam(required = false) Boolean projectLinked,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesAdminService.orders(keyword, customerId, status, dateFrom, dateTo,
				expectedDateFrom, expectedDateTo, projectId, contractId, projectLinked, page, pageSize));
	}

	@GetMapping("/orders/{id}")
	public ApiResponse<SalesAdminService.SalesOrderDetailResponse> order(@PathVariable Long id) {
		return ApiResponse.ok(this.salesAdminService.order(id));
	}

	@PostMapping("/orders")
	public ApiResponse<SalesAdminService.SalesOrderDetailResponse> createOrder(
			@Valid @RequestBody SalesAdminService.SalesOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.createOrder(request, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}")
	public ApiResponse<SalesAdminService.SalesOrderDetailResponse> updateOrder(@PathVariable Long id,
			@Valid @RequestBody SalesAdminService.SalesOrderRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.updateOrder(id, request, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}/confirm")
	public ApiResponse<SalesAdminService.SalesOrderDetailResponse> confirmOrder(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.confirmOrder(id, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}/cancel")
	public ApiResponse<SalesAdminService.SalesOrderDetailResponse> cancelOrder(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.cancelOrder(id, currentUser, servletRequest));
	}

	@PutMapping("/orders/{id}/close")
	public ApiResponse<SalesAdminService.SalesOrderDetailResponse> closeOrder(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.closeOrder(id, currentUser, servletRequest));
	}

	@GetMapping("/shipments")
	public ApiResponse<PageResponse<SalesAdminService.SalesShipmentSummaryResponse>> shipments(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) Long orderId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesAdminService.shipments(keyword, customerId, warehouseId, status, dateFrom,
				dateTo, orderId, page, pageSize));
	}

	@PostMapping("/orders/{id}/shipments")
	public ApiResponse<SalesAdminService.SalesShipmentDetailResponse> createShipment(@PathVariable Long id,
			@Valid @RequestBody SalesAdminService.SalesShipmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.createShipment(id, request, currentUser, servletRequest));
	}

	@GetMapping("/shipments/{id}")
	public ApiResponse<SalesAdminService.SalesShipmentDetailResponse> shipment(@PathVariable Long id) {
		return ApiResponse.ok(this.salesAdminService.shipment(id));
	}

	@PutMapping("/shipments/{id}")
	public ApiResponse<SalesAdminService.SalesShipmentDetailResponse> updateShipment(@PathVariable Long id,
			@Valid @RequestBody SalesAdminService.SalesShipmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.updateShipment(id, request, currentUser, servletRequest));
	}

	@PutMapping("/shipments/{id}/post")
	public ApiResponse<SalesAdminService.SalesShipmentDetailResponse> postShipment(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesAdminService.postShipment(id, currentUser, servletRequest));
	}

}
