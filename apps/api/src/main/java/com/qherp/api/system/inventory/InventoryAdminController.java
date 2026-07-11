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

	public InventoryAdminController(InventoryAdminService inventoryAdminService) {
		this.inventoryAdminService = inventoryAdminService;
	}

	@GetMapping("/balances")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryBalanceResponse>> balances(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String materialType,
			@RequestParam(required = false) String qualityStatus, @RequestParam(defaultValue = "false") boolean onlyPositive,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.balances(keyword, warehouseId, materialId, materialType,
				qualityStatus, onlyPositive, page, pageSize));
	}

	@GetMapping("/movements")
	public ApiResponse<PageResponse<InventoryAdminService.InventoryMovementResponse>> movements(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long warehouseId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String movementType,
			@RequestParam(required = false) String direction, @RequestParam(required = false) LocalDate dateFrom,
			@RequestParam(required = false) LocalDate dateTo, @RequestParam(required = false) String qualityStatus,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.inventoryAdminService.movements(keyword, warehouseId, materialId, movementType,
				direction, dateFrom, dateTo, qualityStatus, page, pageSize));
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
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.inventoryAdminService.postDocument(id, currentUser, servletRequest));
	}

}
