package com.qherp.api.system.reversal;

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

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
public class ReversalAdminController {

	private final ReversalAdminService reversalAdminService;

	public ReversalAdminController(ReversalAdminService reversalAdminService) {
		this.reversalAdminService = reversalAdminService;
	}

	@GetMapping("/sales/return-sources")
	public ApiResponse<PageResponse<ReversalAdminService.SalesReturnSourceResponse>> salesReturnSources(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.salesReturnSources(keyword, customerId, warehouseId, dateFrom,
				dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/sales/returns")
	public ApiResponse<PageResponse<ReversalAdminService.SalesReturnSummaryResponse>> salesReturns(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.salesReturns(keyword, customerId, warehouseId, status,
				dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/sales/returns/{id}")
	public ApiResponse<ReversalAdminService.SalesReturnDetailResponse> salesReturn(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.salesReturn(id, currentUser));
	}

	@PostMapping("/sales/returns")
	public ApiResponse<ReversalAdminService.SalesReturnDetailResponse> createSalesReturn(
			@Valid @RequestBody ReversalAdminService.SalesReturnRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.createSalesReturn(request, currentUser, servletRequest));
	}

	@PutMapping("/sales/returns/{id}")
	public ApiResponse<ReversalAdminService.SalesReturnDetailResponse> updateSalesReturn(@PathVariable Long id,
			@Valid @RequestBody ReversalAdminService.SalesReturnRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.updateSalesReturn(id, request, currentUser, servletRequest));
	}

	@PutMapping("/sales/returns/{id}/post")
	public ApiResponse<ReversalAdminService.SalesReturnDetailResponse> postSalesReturn(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.postSalesReturn(id, currentUser, servletRequest));
	}

	@PutMapping("/sales/returns/{id}/cancel")
	public ApiResponse<ReversalAdminService.SalesReturnDetailResponse> cancelSalesReturn(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.cancelSalesReturn(id, currentUser, servletRequest));
	}

	@GetMapping("/procurement/return-sources")
	public ApiResponse<PageResponse<ReversalAdminService.PurchaseReturnSourceResponse>> procurementReturnSources(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.purchaseReturnSources(keyword, supplierId, warehouseId,
				dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/procurement/returns")
	public ApiResponse<PageResponse<ReversalAdminService.PurchaseReturnSummaryResponse>> procurementReturns(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.purchaseReturns(keyword, supplierId, warehouseId, status,
				dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/procurement/returns/{id}")
	public ApiResponse<ReversalAdminService.PurchaseReturnDetailResponse> procurementReturn(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.purchaseReturn(id, currentUser));
	}

	@PostMapping("/procurement/returns")
	public ApiResponse<ReversalAdminService.PurchaseReturnDetailResponse> createProcurementReturn(
			@Valid @RequestBody ReversalAdminService.PurchaseReturnRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.createPurchaseReturn(request, currentUser, servletRequest));
	}

	@PutMapping("/procurement/returns/{id}")
	public ApiResponse<ReversalAdminService.PurchaseReturnDetailResponse> updateProcurementReturn(@PathVariable Long id,
			@Valid @RequestBody ReversalAdminService.PurchaseReturnRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.updatePurchaseReturn(id, request, currentUser, servletRequest));
	}

	@PutMapping("/procurement/returns/{id}/post")
	public ApiResponse<ReversalAdminService.PurchaseReturnDetailResponse> postProcurementReturn(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.postPurchaseReturn(id, currentUser, servletRequest));
	}

	@PutMapping("/procurement/returns/{id}/cancel")
	public ApiResponse<ReversalAdminService.PurchaseReturnDetailResponse> cancelProcurementReturn(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.cancelPurchaseReturn(id, currentUser, servletRequest));
	}

	@GetMapping("/production/material-return-sources")
	public ApiResponse<PageResponse<Object>> materialReturnSources(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.reversalAdminService.emptyPage(page, pageSize));
	}

	@GetMapping("/production/material-returns")
	public ApiResponse<PageResponse<Object>> materialReturns(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.reversalAdminService.emptyPage(page, pageSize));
	}

	@GetMapping("/production/material-returns/{id}")
	public ApiResponse<Object> materialReturn(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PostMapping("/production/material-returns")
	public ApiResponse<Object> createMaterialReturn(@RequestBody(required = false) Map<String, Object> request) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/production/material-returns/{id}")
	public ApiResponse<Object> updateMaterialReturn(@PathVariable Long id,
			@RequestBody(required = false) Map<String, Object> request) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/production/material-returns/{id}/post")
	public ApiResponse<Object> postMaterialReturn(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/production/material-returns/{id}/cancel")
	public ApiResponse<Object> cancelMaterialReturn(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@GetMapping("/production/material-supplement-sources")
	public ApiResponse<PageResponse<Object>> materialSupplementSources(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.reversalAdminService.emptyPage(page, pageSize));
	}

	@GetMapping("/production/material-supplements")
	public ApiResponse<PageResponse<Object>> materialSupplements(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.reversalAdminService.emptyPage(page, pageSize));
	}

	@GetMapping("/production/material-supplements/{id}")
	public ApiResponse<Object> materialSupplement(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PostMapping("/production/material-supplements")
	public ApiResponse<Object> createMaterialSupplement(@RequestBody(required = false) Map<String, Object> request) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/production/material-supplements/{id}")
	public ApiResponse<Object> updateMaterialSupplement(@PathVariable Long id,
			@RequestBody(required = false) Map<String, Object> request) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/production/material-supplements/{id}/post")
	public ApiResponse<Object> postMaterialSupplement(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/production/material-supplements/{id}/cancel")
	public ApiResponse<Object> cancelMaterialSupplement(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@GetMapping("/finance/settlement-adjustment-sources")
	public ApiResponse<PageResponse<Object>> settlementAdjustmentSources(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.reversalAdminService.emptyPage(page, pageSize));
	}

	@GetMapping("/finance/settlement-adjustments")
	public ApiResponse<PageResponse<Object>> settlementAdjustments(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.reversalAdminService.emptyPage(page, pageSize));
	}

	@GetMapping("/finance/settlement-adjustments/{id}")
	public ApiResponse<Object> settlementAdjustment(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PostMapping("/finance/settlement-adjustments")
	public ApiResponse<Object> createSettlementAdjustment(@RequestBody(required = false) Map<String, Object> request) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/finance/settlement-adjustments/{id}")
	public ApiResponse<Object> updateSettlementAdjustment(@PathVariable Long id,
			@RequestBody(required = false) Map<String, Object> request) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/finance/settlement-adjustments/{id}/post")
	public ApiResponse<Object> postSettlementAdjustment(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@PutMapping("/finance/settlement-adjustments/{id}/cancel")
	public ApiResponse<Object> cancelSettlementAdjustment(@PathVariable Long id) {
		return ApiResponse.ok(this.reversalAdminService.sourceNotFound());
	}

	@GetMapping("/reversal-traces")
	public ApiResponse<List<ReversalAdminService.ReversalTraceRecord>> reversalTraces(
			@RequestParam(required = false) String sourceType, @RequestParam(required = false) Long sourceId,
			@RequestParam(required = false) Long sourceLineId, @RequestParam(required = false) String direction,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.traces(sourceType, sourceId, sourceLineId, direction,
				currentUser));
	}

}
