package com.qherp.api.system.procurement;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.platform.PlatformApprovalService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/admin/procurement")
public class ProcurementSourcingAdminController {

	private final ProcurementSourcingService sourcingService;

	public ProcurementSourcingAdminController(ProcurementSourcingService sourcingService) {
		this.sourcingService = sourcingService;
	}

	@GetMapping("/inquiries")
	public ApiResponse<PageResponse<Map<String, Object>>> inquiries(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String procurementMode, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.sourcingService.inquiries(keyword, procurementMode, projectId, status, page,
				pageSize));
	}

	@PostMapping("/inquiries")
	public ApiResponse<Map<String, Object>> createInquiry(
			@Valid @RequestBody ProcurementSourcingService.PurchaseInquiryRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.createInquiry(request, currentUser, servletRequest));
	}

	@GetMapping("/inquiries/{id}")
	public ApiResponse<Map<String, Object>> inquiry(@PathVariable Long id) {
		return ApiResponse.ok(this.sourcingService.inquiry(id));
	}

	@PutMapping("/inquiries/{id}")
	public ApiResponse<Map<String, Object>> updateInquiry(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.PurchaseInquiryRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.updateInquiry(id, request, currentUser, servletRequest));
	}

	@PutMapping("/inquiries/{id}/release")
	public ApiResponse<Map<String, Object>> releaseInquiry(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.releaseInquiry(id, request, currentUser, servletRequest));
	}

	@PutMapping("/inquiries/{id}/complete")
	public ApiResponse<Map<String, Object>> completeInquiry(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.completeInquiry(id, request, currentUser, servletRequest));
	}

	@PutMapping("/inquiries/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelInquiry(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.cancelInquiry(id, request, currentUser, servletRequest));
	}

	@PostMapping("/inquiries/{inquiryId}/quotes")
	public ApiResponse<Map<String, Object>> createQuote(@PathVariable Long inquiryId,
			@Valid @RequestBody ProcurementSourcingService.SupplierQuoteRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.createQuote(inquiryId, request, currentUser, servletRequest));
	}

	@GetMapping("/inquiries/{inquiryId}/quotes")
	public ApiResponse<PageResponse<Map<String, Object>>> quotes(@PathVariable Long inquiryId,
			@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.sourcingService.quotes(inquiryId, supplierId, status, page, pageSize));
	}

	@GetMapping("/inquiries/{inquiryId}/quotes/{quoteId}")
	public ApiResponse<Map<String, Object>> quote(@PathVariable Long inquiryId, @PathVariable Long quoteId) {
		return ApiResponse.ok(this.sourcingService.quote(inquiryId, quoteId));
	}

	@PutMapping("/inquiries/{inquiryId}/quotes/{quoteId}")
	public ApiResponse<Map<String, Object>> updateQuote(@PathVariable Long inquiryId, @PathVariable Long quoteId,
			@Valid @RequestBody ProcurementSourcingService.SupplierQuoteRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.updateQuote(inquiryId, quoteId, request, currentUser,
				servletRequest));
	}

	@PutMapping("/inquiries/{inquiryId}/quotes/{quoteId}/select")
	public ApiResponse<Map<String, Object>> selectQuote(@PathVariable Long inquiryId, @PathVariable Long quoteId,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.selectQuote(inquiryId, quoteId, request, currentUser,
				servletRequest));
	}

	@PutMapping("/inquiries/{inquiryId}/quotes/{quoteId}/cancel")
	public ApiResponse<Map<String, Object>> cancelQuote(@PathVariable Long inquiryId, @PathVariable Long quoteId,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.cancelQuote(inquiryId, quoteId, request, currentUser,
				servletRequest));
	}

	@PostMapping("/inquiries/{id}/award")
	public ApiResponse<Map<String, Object>> awardInquiry(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.PriceAwardRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.awardInquiry(id, request, currentUser, servletRequest));
	}

	@GetMapping("/price-agreements")
	public ApiResponse<PageResponse<Map<String, Object>>> priceAgreements(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long supplierId,
			@RequestParam(required = false) Long materialId, @RequestParam(required = false) String procurementMode,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.sourcingService.priceAgreements(keyword, supplierId, materialId,
				procurementMode, projectId, status, page, pageSize));
	}

	@PostMapping("/price-agreements")
	public ApiResponse<Map<String, Object>> createPriceAgreement(
			@Valid @RequestBody ProcurementSourcingService.PriceAgreementRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.createPriceAgreement(request, currentUser, servletRequest));
	}

	@GetMapping("/price-agreements/{id}")
	public ApiResponse<Map<String, Object>> priceAgreement(@PathVariable Long id) {
		return ApiResponse.ok(this.sourcingService.priceAgreement(id));
	}

	@PutMapping("/price-agreements/{id}")
	public ApiResponse<Map<String, Object>> updatePriceAgreement(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.PriceAgreementRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.updatePriceAgreement(id, request, currentUser, servletRequest));
	}

	@PostMapping("/price-agreements/{id}/submit-activation")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitPriceAgreementActivation(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(
				this.sourcingService.submitPriceAgreementActivation(id, request, currentUser, servletRequest));
	}

	@PutMapping("/price-agreements/{id}/disable")
	public ApiResponse<Map<String, Object>> disablePriceAgreement(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.disablePriceAgreement(id, request, currentUser, servletRequest));
	}

	@PutMapping("/price-agreements/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancelPriceAgreement(@PathVariable Long id,
			@Valid @RequestBody ProcurementSourcingService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.sourcingService.cancelPriceAgreement(id, request, currentUser, servletRequest));
	}

	@GetMapping("/effective-supplies")
	public ApiResponse<PageResponse<Map<String, Object>>> effectiveSupplies(
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Long materialId,
			@RequestParam(required = false) Long supplierId, @RequestParam(required = false) String procurementMode,
			@RequestParam(required = false) Boolean countedOnly, @RequestParam(required = false) String status,
			@RequestParam(required = false) java.time.LocalDate expectedDateFrom,
			@RequestParam(required = false) java.time.LocalDate expectedDateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.sourcingService.effectiveSupplies(projectId, materialId, supplierId,
				procurementMode, countedOnly, status, expectedDateFrom, expectedDateTo, page, pageSize, currentUser));
	}

}
