package com.qherp.api.system.inventory;

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
@RequestMapping("/api/admin/inventory")
public class InventoryAdminController {

	private final InventoryAdminService inventoryAdminService;

	private final InventoryStage023AdminService stage023AdminService;

	private final InventoryTraceService inventoryTraceService;

	public InventoryAdminController(InventoryAdminService inventoryAdminService,
			InventoryStage023AdminService stage023AdminService, InventoryTraceService inventoryTraceService) {
		this.inventoryAdminService = inventoryAdminService;
		this.stage023AdminService = stage023AdminService;
		this.inventoryTraceService = inventoryTraceService;
	}

	@GetMapping("/balances")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryBalanceResponse>> balances(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String materialType,
			@RequestParam(required = false) String qualityStatus,
			@RequestParam(required = false) String trackingMethod, @RequestParam(required = false) Long batchId,
			@RequestParam(required = false) String batchNo, @RequestParam(required = false) Long serialId,
			@RequestParam(required = false) String serialNo,
			@RequestParam(required = false) String ownershipType, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) String valuationState,
			@RequestParam(defaultValue = "false") boolean onlyPositive,
			@RequestParam(defaultValue = "false") boolean includeZero,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.inventoryAdminService.balances(keyword, warehouseId, materialId, materialType,
				qualityStatus, trackingMethod, batchId, batchNo, serialId, serialNo, ownershipType, projectId,
				valuationState, onlyPositive, includeZero, page, pageSize, currentUser));
	}

	@GetMapping("/movements")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryMovementResponse>> movements(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String movementType,
			@RequestParam(required = false) String direction, @RequestParam(required = false) LocalDate dateFrom,
			@RequestParam(required = false) LocalDate dateTo, @RequestParam(required = false) String qualityStatus,
			@RequestParam(required = false) String trackingMethod, @RequestParam(required = false) Long batchId,
			@RequestParam(required = false) String batchNo, @RequestParam(required = false) Long serialId,
			@RequestParam(required = false) String serialNo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.inventoryAdminService.movements(keyword, warehouseId, materialId, movementType,
				direction, dateFrom, dateTo, qualityStatus, trackingMethod, batchId, batchNo, serialId, serialNo, page,
				pageSize, currentUser));
	}

	@GetMapping("/cost-layers")
	public ApiResponse<PageResponse<java.util.Map<String, Object>>> costLayers(
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Long materialId,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.costLayers(projectId, materialId, page, pageSize,
				currentUser));
	}

	@GetMapping("/reconciliations")
	public ApiResponse<java.util.Map<String, Object>> reconciliation(@RequestParam(required = false) Long materialId,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.reconciliation(materialId, currentUser));
	}

	@GetMapping("/batches")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryBatchSummaryResponse>> batches(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long materialId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String qualityStatus,
			@RequestParam(required = false) String batchNo, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long sourceId,
			@RequestParam(defaultValue = "false") boolean onlyAvailable, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.batches(keyword, materialId, warehouseId, qualityStatus,
				batchNo, sourceType, sourceId, onlyAvailable, page, pageSize));
	}

	@GetMapping("/batches/{id}")
	public ApiResponse<InventoryAdminService.InventoryBatchDetailResponse> batch(@PathVariable Long id) {
		return ApiResponse.ok(this.inventoryAdminService.batch(id));
	}

	@GetMapping("/serials")
	public ApiResponse<PageResponse<InventoryAdminService.InventorySerialSummaryResponse>> serials(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long materialId,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) String qualityStatus,
			@RequestParam(required = false) String serialNo, @RequestParam(required = false) Long batchId,
			@RequestParam(required = false) String sourceType, @RequestParam(required = false) Long sourceId,
			@RequestParam(defaultValue = "false") boolean onlyAvailable, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.serials(keyword, materialId, warehouseId, qualityStatus,
				serialNo, batchId, sourceType, sourceId, onlyAvailable, page, pageSize));
	}

	@GetMapping("/serials/{id}")
	public ApiResponse<InventoryAdminService.InventorySerialDetailResponse> serial(@PathVariable Long id) {
		return ApiResponse.ok(this.inventoryAdminService.serial(id));
	}

	@GetMapping("/traces/batches/{id}")
	public ApiResponse<InventoryAdminService.InventoryTraceDetailResponse> batchTrace(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.inventoryTraceService.batchTrace(id, currentUser));
	}

	@GetMapping("/traces/serials/{id}")
	public ApiResponse<InventoryAdminService.InventoryTraceDetailResponse> serialTrace(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.inventoryTraceService.serialTrace(id, currentUser));
	}

	@GetMapping("/reservations")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryReservationResponse>> reservations(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String reservationType,
			@RequestParam(required = false) String status, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long sourceId, @RequestParam(required = false) Long sourceLineId,
			@RequestParam(required = false) LocalDate businessDateFrom,
			@RequestParam(required = false) LocalDate businessDateTo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.reservations(keyword, warehouseId, materialId,
				reservationType, status, sourceType, sourceId, sourceLineId, businessDateFrom, businessDateTo, page,
				pageSize));
	}

	@GetMapping("/reservations/{id}")
	public ApiResponse<InventoryAdminService.InventoryReservationResponse> reservation(@PathVariable Long id) {
		return ApiResponse.ok(this.inventoryAdminService.reservation(id));
	}

	@GetMapping("/documents")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryDocumentSummaryResponse>> documents(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String documentType,
			@RequestParam(required = false) String status, @RequestParam(required = false) LocalDate dateFrom,
			@RequestParam(required = false) LocalDate dateTo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.documents(keyword, documentType, status, dateFrom, dateTo,
				page, pageSize));
	}

	@GetMapping("/documents/{id}")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> document(@PathVariable Long id) {
		return ApiResponse.ok(this.inventoryAdminService.document(id));
	}

	@PostMapping("/documents")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> createDocument(
			@Valid @RequestBody InventoryAdminService.InventoryDocumentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.inventoryAdminService.createDocument(request, currentUser, servletRequest));
	}

	@PutMapping("/documents/{id}")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> updateDocument(@PathVariable Long id,
			@Valid @RequestBody InventoryAdminService.InventoryDocumentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.inventoryAdminService.updateDocument(id, request, currentUser, servletRequest));
	}

	@PutMapping("/documents/{id}/post")
	public ApiResponse<InventoryAdminService.InventoryDocumentDetailResponse> postDocument(@PathVariable Long id,
			@RequestBody(required = false) InventoryAdminService.InventoryDocumentPostRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.inventoryAdminService.postDocument(id, request, currentUser, servletRequest));
	}

	@PostMapping("/warehouse-transfers")
	public ApiResponse<java.util.Map<String, Object>> createWarehouseTransfer(
			@Valid @RequestBody InventoryStage023AdminService.WarehouseTransferRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.createWarehouseTransfer(request, currentUser));
	}

	@GetMapping("/warehouse-transfers")
	public ApiResponse<PageResponse<java.util.Map<String, Object>>> warehouseTransfers(
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.stage023AdminService.warehouseTransfers(status, page, pageSize));
	}

	@GetMapping("/warehouse-transfers/{id}")
	public ApiResponse<java.util.Map<String, Object>> warehouseTransfer(@PathVariable Long id) {
		return ApiResponse.ok(this.stage023AdminService.warehouseTransfer(id));
	}

	@PutMapping("/warehouse-transfers/{id}")
	public ApiResponse<java.util.Map<String, Object>> updateWarehouseTransfer(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.WarehouseTransferRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.updateWarehouseTransfer(id, request, currentUser));
	}

	@PutMapping("/warehouse-transfers/{id}/post")
	public ApiResponse<java.util.Map<String, Object>> postWarehouseTransfer(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.postWarehouseTransfer(id, request, currentUser));
	}

	@PutMapping("/warehouse-transfers/{id}/cancel")
	public ApiResponse<java.util.Map<String, Object>> cancelWarehouseTransfer(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.cancelWarehouseTransfer(id, request, currentUser));
	}

	@PostMapping("/ownership-conversions")
	public ApiResponse<java.util.Map<String, Object>> createOwnershipConversion(
			@Valid @RequestBody InventoryStage023AdminService.OwnershipConversionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.createOwnershipConversion(request, currentUser));
	}

	@GetMapping("/ownership-conversions")
	public ApiResponse<PageResponse<java.util.Map<String, Object>>> ownershipConversions(
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.stage023AdminService.ownershipConversions(status, page, pageSize));
	}

	@GetMapping("/ownership-conversions/{id}")
	public ApiResponse<java.util.Map<String, Object>> ownershipConversion(@PathVariable Long id) {
		return ApiResponse.ok(this.stage023AdminService.ownershipConversion(id));
	}

	@PutMapping("/ownership-conversions/{id}")
	public ApiResponse<java.util.Map<String, Object>> updateOwnershipConversion(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.OwnershipConversionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.updateOwnershipConversion(id, request, currentUser));
	}

	@PutMapping({ "/ownership-conversions/{id}/submit", "/ownership-conversions/{id}/submit-approval" })
	public ApiResponse<java.util.Map<String, Object>> submitOwnershipConversion(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.stage023AdminService.submitOwnershipConversion(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/ownership-conversions/{id}/withdraw")
	public ApiResponse<java.util.Map<String, Object>> withdrawOwnershipConversion(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.stage023AdminService.withdrawOwnershipConversion(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/ownership-conversions/{id}/cancel")
	public ApiResponse<java.util.Map<String, Object>> cancelOwnershipConversion(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.cancelOwnershipConversion(id, request, currentUser));
	}

	@PostMapping("/stocktakes")
	public ApiResponse<java.util.Map<String, Object>> createStocktake(
			@Valid @RequestBody InventoryStage023AdminService.StocktakeRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.createStocktake(request, currentUser));
	}

	@GetMapping("/stocktakes")
	public ApiResponse<PageResponse<java.util.Map<String, Object>>> stocktakes(
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.stage023AdminService.stocktakes(status, page, pageSize));
	}

	@GetMapping("/stocktakes/{id}")
	public ApiResponse<java.util.Map<String, Object>> stocktake(@PathVariable Long id) {
		return ApiResponse.ok(this.stage023AdminService.stocktake(id));
	}

	@PutMapping("/stocktakes/{id}/start")
	public ApiResponse<java.util.Map<String, Object>> startStocktake(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.startStocktake(id, request, currentUser));
	}

	@PutMapping("/stocktakes/{id}/lines")
	public ApiResponse<java.util.Map<String, Object>> updateStocktakeLines(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.StocktakeLineUpdateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.updateStocktakeLines(id, request, currentUser));
	}

	@PutMapping({ "/stocktakes/{id}/confirm-variance", "/stocktakes/{id}/reconcile" })
	public ApiResponse<java.util.Map<String, Object>> confirmStocktakeVariance(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.confirmStocktakeVariance(id, request, currentUser));
	}

	@PutMapping({ "/stocktakes/{id}/submit", "/stocktakes/{id}/submit-approval" })
	public ApiResponse<java.util.Map<String, Object>> submitStocktake(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.stage023AdminService.submitStocktake(id, request, currentUser, servletRequest));
	}

	@PutMapping("/stocktakes/{id}/complete-zero-variance")
	public ApiResponse<java.util.Map<String, Object>> completeZeroVarianceStocktake(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.completeZeroVarianceStocktake(id, request, currentUser));
	}

	@PutMapping("/stocktakes/{id}/cancel")
	public ApiResponse<java.util.Map<String, Object>> cancelStocktake(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.cancelStocktake(id, request, currentUser));
	}

	@GetMapping("/valuation-adjustments")
	public ApiResponse<PageResponse<java.util.Map<String, Object>>> valuationAdjustments(
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.stage023AdminService.valuationAdjustments(status, page, pageSize));
	}

	@GetMapping("/valuation-adjustments/{id}")
	public ApiResponse<java.util.Map<String, Object>> valuationAdjustment(@PathVariable Long id) {
		return ApiResponse.ok(this.stage023AdminService.valuationAdjustment(id));
	}

	@PostMapping("/valuation-adjustments")
	public ApiResponse<java.util.Map<String, Object>> createValuationAdjustment(
			@Valid @RequestBody InventoryStage023AdminService.ValuationAdjustmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.createValuationAdjustment(request, currentUser));
	}

	@PutMapping("/valuation-adjustments/{id}")
	public ApiResponse<java.util.Map<String, Object>> updateValuationAdjustment(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.ValuationAdjustmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.updateValuationAdjustment(id, request, currentUser));
	}

	@PutMapping({ "/valuation-adjustments/{id}/submit", "/valuation-adjustments/{id}/submit-approval" })
	public ApiResponse<java.util.Map<String, Object>> submitValuationAdjustment(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.stage023AdminService.submitValuationAdjustment(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/valuation-adjustments/{id}/withdraw")
	public ApiResponse<java.util.Map<String, Object>> withdrawValuationAdjustment(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.stage023AdminService.withdrawValuationAdjustment(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/valuation-adjustments/{id}/cancel")
	public ApiResponse<java.util.Map<String, Object>> cancelValuationAdjustment(@PathVariable Long id,
			@Valid @RequestBody InventoryStage023AdminService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.stage023AdminService.cancelValuationAdjustment(id, request, currentUser));
	}

}
