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
			@Valid @RequestBody ReversalAdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.postPurchaseReturn(id, request, currentUser, servletRequest));
	}

	@PutMapping("/procurement/returns/{id}/cancel")
	public ApiResponse<ReversalAdminService.PurchaseReturnDetailResponse> cancelProcurementReturn(@PathVariable Long id,
			@Valid @RequestBody ReversalAdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.cancelPurchaseReturn(id, request, currentUser, servletRequest));
	}

	@GetMapping("/production/material-return-sources")
	public ApiResponse<PageResponse<ReversalAdminService.ProductionMaterialReturnSourceResponse>> materialReturnSources(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long workOrderId,
			@RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.materialReturnSources(keyword, workOrderId, warehouseId,
				dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/production/material-returns")
	public ApiResponse<PageResponse<ReversalAdminService.ProductionMaterialReturnSummaryResponse>> materialReturns(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long workOrderId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.materialReturns(keyword, workOrderId, warehouseId, status,
				dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/production/material-returns/{id}")
	public ApiResponse<ReversalAdminService.ProductionMaterialReturnDetailResponse> materialReturn(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.materialReturn(id, currentUser));
	}

	@PostMapping("/production/material-returns")
	public ApiResponse<ReversalAdminService.ProductionMaterialReturnDetailResponse> createMaterialReturn(
			@Valid @RequestBody ReversalAdminService.ProductionMaterialReturnRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.createMaterialReturn(request, currentUser, servletRequest));
	}

	@PutMapping("/production/material-returns/{id}")
	public ApiResponse<ReversalAdminService.ProductionMaterialReturnDetailResponse> updateMaterialReturn(
			@PathVariable Long id, @Valid @RequestBody ReversalAdminService.ProductionMaterialReturnRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.updateMaterialReturn(id, request, currentUser, servletRequest));
	}

	@PutMapping("/production/material-returns/{id}/post")
	public ApiResponse<ReversalAdminService.ProductionMaterialReturnDetailResponse> postMaterialReturn(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.postMaterialReturn(id, currentUser, servletRequest));
	}

	@PutMapping("/production/material-returns/{id}/cancel")
	public ApiResponse<ReversalAdminService.ProductionMaterialReturnDetailResponse> cancelMaterialReturn(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.cancelMaterialReturn(id, currentUser, servletRequest));
	}

	@GetMapping("/production/material-supplement-sources")
	public ApiResponse<PageResponse<ReversalAdminService.ProductionMaterialSupplementSourceResponse>> materialSupplementSources(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long workOrderId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.materialSupplementSources(keyword, workOrderId, warehouseId,
				page, pageSize, currentUser));
	}

	@GetMapping("/production/material-supplements")
	public ApiResponse<PageResponse<ReversalAdminService.ProductionMaterialSupplementSummaryResponse>> materialSupplements(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long workOrderId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.materialSupplements(keyword, workOrderId, warehouseId,
				status, dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/production/material-supplements/{id}")
	public ApiResponse<ReversalAdminService.ProductionMaterialSupplementDetailResponse> materialSupplement(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.materialSupplement(id, currentUser));
	}

	@PostMapping("/production/material-supplements")
	public ApiResponse<ReversalAdminService.ProductionMaterialSupplementDetailResponse> createMaterialSupplement(
			@Valid @RequestBody ReversalAdminService.ProductionMaterialSupplementRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.createMaterialSupplement(request, currentUser, servletRequest));
	}

	@PutMapping("/production/material-supplements/{id}")
	public ApiResponse<ReversalAdminService.ProductionMaterialSupplementDetailResponse> updateMaterialSupplement(
			@PathVariable Long id,
			@Valid @RequestBody ReversalAdminService.ProductionMaterialSupplementRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse
			.ok(this.reversalAdminService.updateMaterialSupplement(id, request, currentUser, servletRequest));
	}

	@PutMapping("/production/material-supplements/{id}/post")
	public ApiResponse<ReversalAdminService.ProductionMaterialSupplementDetailResponse> postMaterialSupplement(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.postMaterialSupplement(id, currentUser, servletRequest));
	}

	@PutMapping("/production/material-supplements/{id}/cancel")
	public ApiResponse<ReversalAdminService.ProductionMaterialSupplementDetailResponse> cancelMaterialSupplement(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.cancelMaterialSupplement(id, currentUser, servletRequest));
	}

	@GetMapping("/finance/settlement-adjustment-sources")
	public ApiResponse<PageResponse<ReversalAdminService.SettlementAdjustmentSourceResponse>> settlementAdjustmentSources(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String settlementSide,
			@RequestParam(required = false) String sourceType, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.settlementAdjustmentSources(keyword, settlementSide,
				sourceType, customerId, supplierId, dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/finance/settlement-adjustments")
	public ApiResponse<PageResponse<ReversalAdminService.SettlementAdjustmentSummaryResponse>> settlementAdjustments(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String settlementSide,
			@RequestParam(required = false) String adjustmentType, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.settlementAdjustments(keyword, settlementSide,
				adjustmentType, sourceType, status, dateFrom, dateTo, page, pageSize, currentUser));
	}

	@GetMapping("/finance/settlement-adjustments/{id}")
	public ApiResponse<ReversalAdminService.SettlementAdjustmentDetailResponse> settlementAdjustment(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.reversalAdminService.settlementAdjustment(id, currentUser));
	}

	@PostMapping("/finance/settlement-adjustments")
	public ApiResponse<ReversalAdminService.SettlementAdjustmentDetailResponse> createSettlementAdjustment(
			@Valid @RequestBody ReversalAdminService.SettlementAdjustmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse
			.ok(this.reversalAdminService.createSettlementAdjustment(request, currentUser, servletRequest));
	}

	@PutMapping("/finance/settlement-adjustments/{id}")
	public ApiResponse<ReversalAdminService.SettlementAdjustmentDetailResponse> updateSettlementAdjustment(
			@PathVariable Long id, @Valid @RequestBody ReversalAdminService.SettlementAdjustmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse
			.ok(this.reversalAdminService.updateSettlementAdjustment(id, request, currentUser, servletRequest));
	}

	@PutMapping("/finance/settlement-adjustments/{id}/post")
	public ApiResponse<ReversalAdminService.SettlementAdjustmentDetailResponse> postSettlementAdjustment(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.postSettlementAdjustment(id, currentUser, servletRequest));
	}

	@PutMapping("/finance/settlement-adjustments/{id}/cancel")
	public ApiResponse<ReversalAdminService.SettlementAdjustmentDetailResponse> cancelSettlementAdjustment(
			@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.reversalAdminService.cancelSettlementAdjustment(id, currentUser, servletRequest));
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
