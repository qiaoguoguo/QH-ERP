package com.qherp.api.system.finance;

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
import java.util.Map;

@RestController
@RequestMapping("/api/admin/finance")
public class FinanceStage028Controller {

	private final FinanceStage028Service financeStage028Service;

	public FinanceStage028Controller(FinanceStage028Service financeStage028Service) {
		this.financeStage028Service = financeStage028Service;
	}

	@GetMapping("/sales-invoices")
	public ApiResponse<PageResponse<Map<String, Object>>> salesInvoices(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long customerId, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String settlementStatus,
			@RequestParam(required = false) String invoiceType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateTo,
			@RequestParam(required = false) String externalInvoiceNo,
			@RequestParam(required = false) String sourceShipmentNo, @AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.salesInvoices(keyword, customerId, projectId, status,
				settlementStatus, invoiceType, invoiceDateFrom, invoiceDateTo, externalInvoiceNo, sourceShipmentNo,
				currentUser, page, pageSize));
	}

	@GetMapping("/sales-invoices/candidates")
	public ApiResponse<PageResponse<Map<String, Object>>> salesInvoiceCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long sourceId,
			@RequestParam(required = false) Long customerId, @RequestParam(required = false) String ownershipType,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) String contractNo,
			@RequestParam(required = false) String orderNo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shipmentDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shipmentDateTo,
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.salesInvoiceCandidates(keyword, sourceId, customerId,
				ownershipType, projectId, contractNo, orderNo, shipmentDateFrom, shipmentDateTo, currentUser, page,
				pageSize));
	}

	@PostMapping("/sales-invoices")
	public ApiResponse<Map<String, Object>> createSalesInvoice(
			@Valid @RequestBody FinanceStage028Service.SalesInvoiceCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.createSalesInvoice(request, currentUser, servletRequest));
	}

	@GetMapping("/sales-invoices/{id}")
	public ApiResponse<Map<String, Object>> salesInvoice(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.salesInvoice(id, currentUser));
	}

	@PutMapping("/sales-invoices/{id}")
	public ApiResponse<Map<String, Object>> updateSalesInvoice(@PathVariable Long id,
			@Valid @RequestBody FinanceStage028Service.SalesInvoiceCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.updateSalesInvoice(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/sales-invoices/{id}/confirm")
	public ApiResponse<Map<String, Object>> confirmSalesInvoice(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.confirmSalesInvoice(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/sales-invoices/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelSalesInvoice(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.cancelSalesInvoice(id, request, currentUser,
				servletRequest));
	}

	@GetMapping("/purchase-invoices")
	public ApiResponse<PageResponse<Map<String, Object>>> purchaseInvoices(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) String matchStatus, @RequestParam(required = false) String settlementStatus,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDateTo,
			@AuthenticationPrincipal CurrentUser currentUser, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.purchaseInvoices(keyword, supplierId, sourceType, status,
				matchStatus, settlementStatus, invoiceDateFrom, invoiceDateTo, currentUser, page, pageSize));
	}

	@GetMapping("/purchase-invoices/candidates")
	public ApiResponse<PageResponse<Map<String, Object>>> purchaseInvoiceCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long sourceId, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) String ownershipType, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateTo,
			@AuthenticationPrincipal CurrentUser currentUser, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.purchaseInvoiceCandidates(keyword, sourceType, sourceId,
				supplierId, ownershipType, projectId, businessDateFrom, businessDateTo, currentUser, page, pageSize));
	}

	@PostMapping("/purchase-invoices")
	public ApiResponse<Map<String, Object>> createPurchaseInvoice(
			@Valid @RequestBody FinanceStage028Service.PurchaseInvoiceCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.createPurchaseInvoice(request, currentUser, servletRequest));
	}

	@GetMapping("/purchase-invoices/{id}")
	public ApiResponse<Map<String, Object>> purchaseInvoice(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.purchaseInvoice(id, currentUser));
	}

	@PutMapping("/purchase-invoices/{id}")
	public ApiResponse<Map<String, Object>> updatePurchaseInvoice(@PathVariable Long id,
			@Valid @RequestBody FinanceStage028Service.PurchaseInvoiceCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.updatePurchaseInvoice(id, request, currentUser,
				servletRequest));
	}

	@GetMapping("/purchase-invoices/{id}/matching")
	public ApiResponse<Map<String, Object>> purchaseInvoiceMatching(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.purchaseInvoiceMatching(id, currentUser));
	}

	@PutMapping("/purchase-invoices/{id}/match")
	public ApiResponse<Map<String, Object>> matchPurchaseInvoice(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.matchPurchaseInvoice(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/purchase-invoices/{id}/confirm")
	public ApiResponse<Map<String, Object>> confirmPurchaseInvoice(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.confirmPurchaseInvoice(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/purchase-invoices/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelPurchaseInvoice(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.cancelPurchaseInvoice(id, request, currentUser,
				servletRequest));
	}

	@GetMapping("/expenses")
	public ApiResponse<PageResponse<Map<String, Object>>> expenses(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long supplierId, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String ownershipType, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) String sourceType, @RequestParam(required = false) String status,
			@RequestParam(required = false) String settlementStatus,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateTo,
			@RequestParam(required = false) Boolean costRestricted, @AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.expenses(keyword, supplierId, categoryId, ownershipType,
				projectId, sourceType, status, settlementStatus, businessDateFrom, businessDateTo, costRestricted,
				currentUser, page, pageSize));
	}

	@GetMapping("/expenses/categories")
	public ApiResponse<PageResponse<Map<String, Object>>> expenseCategories(
			@RequestParam(required = false) String keyword, @AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.expenseCategories(keyword, currentUser, page, pageSize));
	}

	@GetMapping("/expenses/source-candidates")
	public ApiResponse<PageResponse<Map<String, Object>>> expenseSourceCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String ownershipType,
			@RequestParam(required = false) Long projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateTo,
			@AuthenticationPrincipal CurrentUser currentUser, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.expenseSourceCandidates(keyword, sourceType, currentUser,
				supplierId, ownershipType, projectId, businessDateFrom, businessDateTo, page, pageSize));
	}

	@PostMapping("/expenses")
	public ApiResponse<Map<String, Object>> createExpense(
			@Valid @RequestBody FinanceStage028Service.ExpenseCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.createExpense(request, currentUser, servletRequest));
	}

	@GetMapping("/expenses/{id}")
	public ApiResponse<Map<String, Object>> expense(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.expense(id, currentUser));
	}

	@PutMapping("/expenses/{id}")
	public ApiResponse<Map<String, Object>> updateExpense(@PathVariable Long id,
			@Valid @RequestBody FinanceStage028Service.ExpenseCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.updateExpense(id, request, currentUser, servletRequest));
	}

	@PutMapping("/expenses/{id}/confirm")
	public ApiResponse<Map<String, Object>> confirmExpense(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.confirmExpense(id, request, currentUser, servletRequest));
	}

	@PutMapping("/expenses/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelExpense(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.cancelExpense(id, request, currentUser, servletRequest));
	}

	@GetMapping("/advance-receipts")
	public ApiResponse<PageResponse<Map<String, Object>>> advanceReceipts(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) String ownershipType, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String settlementStatus,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateTo,
			@RequestParam(required = false) Boolean availableOnly, @AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.advanceReceipts(keyword, customerId, ownershipType, projectId,
				status, settlementStatus, businessDateFrom, businessDateTo, availableOnly, currentUser, page, pageSize));
	}

	@PostMapping("/advance-receipts")
	public ApiResponse<Map<String, Object>> createAdvanceReceipt(
			@Valid @RequestBody FinanceStage028Service.AdvanceReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.createAdvanceReceipt(request, currentUser, servletRequest));
	}

	@GetMapping("/advance-receipts/{id}")
	public ApiResponse<Map<String, Object>> advanceReceipt(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.advanceReceipt(id, currentUser));
	}

	@PutMapping("/advance-receipts/{id}")
	public ApiResponse<Map<String, Object>> updateAdvanceReceipt(@PathVariable Long id,
			@Valid @RequestBody FinanceStage028Service.AdvanceReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.updateAdvanceReceipt(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/advance-receipts/{id}/post")
	public ApiResponse<Map<String, Object>> postAdvanceReceipt(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.postAdvanceReceipt(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/advance-receipts/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelAdvanceReceipt(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.cancelAdvanceReceipt(id, request, currentUser,
				servletRequest));
	}

	@GetMapping("/prepayments")
	public ApiResponse<PageResponse<Map<String, Object>>> prepayments(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String ownershipType,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String settlementStatus,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateTo,
			@RequestParam(required = false) Boolean availableOnly, @AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.prepayments(keyword, supplierId, ownershipType, projectId,
				status, settlementStatus, businessDateFrom, businessDateTo, availableOnly, currentUser, page, pageSize));
	}

	@PostMapping("/prepayments")
	public ApiResponse<Map<String, Object>> createPrepayment(
			@Valid @RequestBody FinanceStage028Service.PrepaymentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.createPrepayment(request, currentUser, servletRequest));
	}

	@GetMapping("/prepayments/{id}")
	public ApiResponse<Map<String, Object>> prepayment(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.prepayment(id, currentUser));
	}

	@PutMapping("/prepayments/{id}")
	public ApiResponse<Map<String, Object>> updatePrepayment(@PathVariable Long id,
			@Valid @RequestBody FinanceStage028Service.PrepaymentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.updatePrepayment(id, request, currentUser, servletRequest));
	}

	@PutMapping("/prepayments/{id}/post")
	public ApiResponse<Map<String, Object>> postPrepayment(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.postPrepayment(id, request, currentUser, servletRequest));
	}

	@PutMapping("/prepayments/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelPrepayment(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.cancelPrepayment(id, request, currentUser,
				servletRequest));
	}

	@GetMapping("/settlement-workbench/funds")
	public ApiResponse<PageResponse<Map<String, Object>>> settlementFunds(@RequestParam String direction,
			@RequestParam(required = false) String fundType, @RequestParam(required = false) Long fundId,
			@RequestParam(required = false) Long partnerId, @RequestParam(required = false) String ownershipType,
			@RequestParam(required = false) Long projectId, @AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.settlementFunds(direction, fundType, fundId, partnerId,
				ownershipType, projectId, currentUser, page, pageSize));
	}

	@GetMapping("/settlement-workbench/targets")
	public ApiResponse<PageResponse<Map<String, Object>>> settlementTargets(@RequestParam String direction,
			@RequestParam(required = false) String targetType, @RequestParam(required = false) Long partnerId,
			@RequestParam(required = false) String ownershipType, @RequestParam(required = false) Long projectId,
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.settlementTargets(direction, targetType, partnerId,
				ownershipType, projectId, currentUser, page, pageSize));
	}

	@PostMapping("/settlement-workbench/allocations")
	public ApiResponse<Map<String, Object>> createSettlementAllocation(
			@Valid @RequestBody FinanceStage028Service.SettlementAllocationRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.createSettlementAllocation(request, currentUser,
				servletRequest));
	}

	@GetMapping("/settlement-workbench/allocations")
	public ApiResponse<PageResponse<Map<String, Object>>> settlementAllocations(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String direction,
			@RequestParam(required = false) String status, @RequestParam(required = false) Long partnerId,
			@RequestParam(required = false) String ownershipType, @RequestParam(required = false) Long projectId,
			@AuthenticationPrincipal CurrentUser currentUser, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.settlementAllocations(keyword, direction, status,
				partnerId, ownershipType, projectId, currentUser, page, pageSize));
	}

	@GetMapping("/settlement-workbench/allocations/{id}")
	public ApiResponse<Map<String, Object>> settlementAllocation(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.settlementAllocation(id, currentUser));
	}

	@PutMapping("/settlement-workbench/allocations/{id}/post")
	public ApiResponse<Map<String, Object>> postSettlementAllocation(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.postSettlementAllocation(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/settlement-workbench/allocations/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelSettlementAllocation(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.cancelSettlementAllocation(id, request, currentUser,
				servletRequest));
	}

	@GetMapping("/voucher-drafts")
	public ApiResponse<PageResponse<Map<String, Object>>> voucherDrafts(@RequestParam(required = false) String status,
			@RequestParam(required = false) String sourceType, @RequestParam(required = false) Long sourceId,
			@RequestParam(required = false) String sourceNo, @RequestParam(required = false) Long partnerId,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Boolean balanced,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDateTo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate generatedAtFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate generatedAtTo,
			@RequestParam(required = false) String keyword,
			@AuthenticationPrincipal CurrentUser currentUser, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeStage028Service.voucherDrafts(status, sourceType, sourceId, sourceNo,
				partnerId, projectId, balanced, businessDateFrom, businessDateTo, generatedAtFrom, generatedAtTo,
				keyword, currentUser, page, pageSize));
	}

	@PostMapping("/voucher-drafts/generate")
	public ApiResponse<Map<String, Object>> generateVoucherDraft(
			@Valid @RequestBody FinanceStage028Service.VoucherDraftGenerateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.generateVoucherDraft(request, currentUser, servletRequest));
	}

	@GetMapping("/voucher-drafts/{id}")
	public ApiResponse<Map<String, Object>> voucherDraft(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.financeStage028Service.voucherDraft(id, currentUser));
	}

	@PutMapping("/voucher-drafts/{id}/ready")
	public ApiResponse<Map<String, Object>> readyVoucherDraft(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.readyVoucherDraft(id, request, currentUser, servletRequest));
	}

	@PutMapping("/voucher-drafts/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelVoucherDraft(@PathVariable Long id,
			@RequestBody(required = false) FinanceStage028Service.FinanceActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeStage028Service.cancelVoucherDraft(id, request, currentUser,
				servletRequest));
	}

}
