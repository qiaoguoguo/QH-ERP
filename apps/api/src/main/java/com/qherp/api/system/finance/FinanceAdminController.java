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

@RestController
@RequestMapping("/api/admin/finance")
public class FinanceAdminController {

	private final FinanceAdminService financeAdminService;

	public FinanceAdminController(FinanceAdminService financeAdminService) {
		this.financeAdminService = financeAdminService;
	}

	@GetMapping("/receivable-sources")
	public ApiResponse<PageResponse<FinanceAdminService.ReceivableCandidateSourceResponse>> receivableSources(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) Boolean settlementGenerated, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeAdminService.receivableSources(keyword, customerId, dateFrom, dateTo,
				settlementGenerated, page, pageSize));
	}

	@GetMapping("/receivables")
	public ApiResponse<PageResponse<FinanceAdminService.ReceivableSummaryResponse>> receivables(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
			@RequestParam(required = false) String sourceNo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeAdminService.receivables(keyword, customerId, status, dateFrom, dateTo,
				dueDateFrom, dueDateTo, sourceNo, page, pageSize));
	}

	@GetMapping("/receivables/{id}")
	public ApiResponse<FinanceAdminService.ReceivableDetailResponse> receivable(@PathVariable Long id) {
		return ApiResponse.ok(this.financeAdminService.receivable(id));
	}

	@GetMapping("/receivables/{id}/sources")
	public ApiResponse<PageResponse<FinanceAdminService.ReceivableSourceRecord>> receivableSources(
			@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeAdminService.receivableSources(id, page, pageSize));
	}

	@PostMapping("/receivables")
	public ApiResponse<FinanceAdminService.ReceivableDetailResponse> createReceivable(
			@Valid @RequestBody FinanceAdminService.ReceivableCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.createReceivable(request, currentUser, servletRequest));
	}

	@PutMapping("/receivables/{id}")
	public ApiResponse<FinanceAdminService.ReceivableDetailResponse> updateReceivable(@PathVariable Long id,
			@Valid @RequestBody FinanceAdminService.ReceivableUpdateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.updateReceivable(id, request, currentUser, servletRequest));
	}

	@PutMapping("/receivables/{id}/confirm")
	public ApiResponse<FinanceAdminService.ReceivableDetailResponse> confirmReceivable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.confirmReceivable(id, currentUser, servletRequest));
	}

	@PutMapping("/receivables/{id}/cancel")
	public ApiResponse<FinanceAdminService.ReceivableDetailResponse> cancelReceivable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.cancelReceivable(id, currentUser, servletRequest));
	}

	@PutMapping("/receivables/{id}/close")
	public ApiResponse<FinanceAdminService.ReceivableDetailResponse> closeReceivable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.closeReceivable(id, currentUser, servletRequest));
	}

	@GetMapping("/receipts")
	public ApiResponse<PageResponse<FinanceAdminService.ReceiptSummaryResponse>> receipts(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) Long receivableId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeAdminService.receipts(keyword, customerId, status, dateFrom, dateTo,
				receivableId, page, pageSize));
	}

	@PostMapping("/receivables/{id}/receipts")
	public ApiResponse<FinanceAdminService.ReceiptDetailResponse> createReceipt(@PathVariable Long id,
			@Valid @RequestBody FinanceAdminService.ReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.createReceipt(id, request, currentUser, servletRequest));
	}

	@GetMapping("/receipts/{id}")
	public ApiResponse<FinanceAdminService.ReceiptDetailResponse> receipt(@PathVariable Long id) {
		return ApiResponse.ok(this.financeAdminService.receipt(id));
	}

	@PutMapping("/receipts/{id}")
	public ApiResponse<FinanceAdminService.ReceiptDetailResponse> updateReceipt(@PathVariable Long id,
			@Valid @RequestBody FinanceAdminService.ReceiptRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.updateReceipt(id, request, currentUser, servletRequest));
	}

	@PutMapping("/receipts/{id}/post")
	public ApiResponse<FinanceAdminService.ReceiptDetailResponse> postReceipt(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.postReceipt(id, currentUser, servletRequest));
	}

	@PutMapping("/receipts/{id}/cancel")
	public ApiResponse<FinanceAdminService.ReceiptDetailResponse> cancelReceipt(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.cancelReceipt(id, currentUser, servletRequest));
	}

	@GetMapping("/payable-sources")
	public ApiResponse<PageResponse<FinanceAdminService.PayableCandidateSourceResponse>> payableSources(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) Boolean settlementGenerated, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeAdminService.payableSources(keyword, supplierId, dateFrom, dateTo,
				settlementGenerated, page, pageSize));
	}

	@GetMapping("/payables")
	public ApiResponse<PageResponse<FinanceAdminService.PayableSummaryResponse>> payables(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
			@RequestParam(required = false) String sourceNo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeAdminService.payables(keyword, supplierId, status, dateFrom, dateTo,
				dueDateFrom, dueDateTo, sourceNo, page, pageSize));
	}

	@GetMapping("/payables/{id}")
	public ApiResponse<FinanceAdminService.PayableDetailResponse> payable(@PathVariable Long id) {
		return ApiResponse.ok(this.financeAdminService.payable(id));
	}

	@PostMapping("/payables")
	public ApiResponse<FinanceAdminService.PayableDetailResponse> createPayable(
			@Valid @RequestBody FinanceAdminService.PayableCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.createPayable(request, currentUser, servletRequest));
	}

	@PutMapping("/payables/{id}")
	public ApiResponse<FinanceAdminService.PayableDetailResponse> updatePayable(@PathVariable Long id,
			@Valid @RequestBody FinanceAdminService.PayableUpdateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.updatePayable(id, request, currentUser, servletRequest));
	}

	@PutMapping("/payables/{id}/confirm")
	public ApiResponse<FinanceAdminService.PayableDetailResponse> confirmPayable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.confirmPayable(id, currentUser, servletRequest));
	}

	@PutMapping("/payables/{id}/cancel")
	public ApiResponse<FinanceAdminService.PayableDetailResponse> cancelPayable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.cancelPayable(id, currentUser, servletRequest));
	}

	@PutMapping("/payables/{id}/close")
	public ApiResponse<FinanceAdminService.PayableDetailResponse> closePayable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.closePayable(id, currentUser, servletRequest));
	}

	@GetMapping("/payments")
	public ApiResponse<PageResponse<FinanceAdminService.PaymentSummaryResponse>> payments(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) Long payableId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.financeAdminService.payments(keyword, supplierId, status, dateFrom, dateTo,
				payableId, page, pageSize));
	}

	@PostMapping("/payables/{id}/payments")
	public ApiResponse<FinanceAdminService.PaymentDetailResponse> createPayment(@PathVariable Long id,
			@Valid @RequestBody FinanceAdminService.PaymentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.createPayment(id, request, currentUser, servletRequest));
	}

	@GetMapping("/payments/{id}")
	public ApiResponse<FinanceAdminService.PaymentDetailResponse> payment(@PathVariable Long id) {
		return ApiResponse.ok(this.financeAdminService.payment(id));
	}

	@PutMapping("/payments/{id}")
	public ApiResponse<FinanceAdminService.PaymentDetailResponse> updatePayment(@PathVariable Long id,
			@Valid @RequestBody FinanceAdminService.PaymentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.updatePayment(id, request, currentUser, servletRequest));
	}

	@PutMapping("/payments/{id}/post")
	public ApiResponse<FinanceAdminService.PaymentDetailResponse> postPayment(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.postPayment(id, currentUser, servletRequest));
	}

	@PutMapping("/payments/{id}/cancel")
	public ApiResponse<FinanceAdminService.PaymentDetailResponse> cancelPayment(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.financeAdminService.cancelPayment(id, currentUser, servletRequest));
	}

}
