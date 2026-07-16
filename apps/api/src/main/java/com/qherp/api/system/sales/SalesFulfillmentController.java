package com.qherp.api.system.sales;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.platform.PlatformApprovalService;
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
public class SalesFulfillmentController {

	private final SalesQuoteService salesQuoteService;

	private final SalesFulfillmentService salesFulfillmentService;

	public SalesFulfillmentController(SalesQuoteService salesQuoteService,
			SalesFulfillmentService salesFulfillmentService) {
		this.salesQuoteService = salesQuoteService;
		this.salesFulfillmentService = salesFulfillmentService;
	}

	@PostMapping("/quotes")
	public ApiResponse<SalesQuoteService.SalesQuoteDetailResponse> createQuote(
			@Valid @RequestBody SalesQuoteService.QuoteRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesQuoteService.create(request, currentUser, servletRequest));
	}

	@GetMapping("/quotes")
	public ApiResponse<PageResponse<SalesQuoteService.SalesQuoteSummaryResponse>> quotes(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long projectId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String approvalStatus,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesQuoteService.quotes(keyword, customerId, projectId, status,
				approvalStatus, validFrom, validTo, page, pageSize));
	}

	@GetMapping("/quotes/{id}")
	public ApiResponse<SalesQuoteService.SalesQuoteDetailResponse> quote(@PathVariable Long id) {
		return ApiResponse.ok(this.salesQuoteService.quote(id));
	}

	@PutMapping("/quotes/{id}")
	public ApiResponse<SalesQuoteService.SalesQuoteDetailResponse> updateQuote(@PathVariable Long id,
			@Valid @RequestBody SalesQuoteService.QuoteRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesQuoteService.update(id, request, currentUser, servletRequest));
	}

	@PostMapping("/quotes/{id}/submit-approval")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitQuoteApproval(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesQuoteService.submitApproval(id, request, currentUser, servletRequest));
	}

	@PostMapping("/quotes/{id}/cancel")
	public ApiResponse<SalesQuoteService.SalesQuoteDetailResponse> cancelQuote(@PathVariable Long id,
			@Valid @RequestBody SalesQuoteService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesQuoteService.cancel(id, request, currentUser, servletRequest));
	}

	@PostMapping("/quotes/{id}/convert-order")
	public ApiResponse<SalesQuoteService.ConvertedOrderResponse> convertQuoteToOrder(@PathVariable Long id,
			@Valid @RequestBody SalesQuoteService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesQuoteService.convertToOrder(id, request, currentUser, servletRequest));
	}

	@PostMapping("/quotes/{id}/convert-contract")
	public ApiResponse<SalesQuoteService.ConvertedContractResponse> convertQuoteToContract(@PathVariable Long id,
			@Valid @RequestBody SalesQuoteService.ConvertContractRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesQuoteService.convertToContract(id, request, currentUser, servletRequest));
	}

	@PostMapping("/credit-profiles")
	public ApiResponse<SalesFulfillmentService.CreditProfileResponse> createCreditProfile(
			@Valid @RequestBody SalesFulfillmentService.CreditProfileRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.salesFulfillmentService.createCreditProfile(request, currentUser));
	}

	@GetMapping("/credit-profiles")
	public ApiResponse<PageResponse<SalesFulfillmentService.CreditProfileResponse>> creditProfiles(
			@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesFulfillmentService.creditProfiles(customerId, keyword, page, pageSize));
	}

	@GetMapping("/credit-profiles/{customerId}")
	public ApiResponse<SalesFulfillmentService.CreditProfileResponse> creditProfile(@PathVariable Long customerId) {
		return ApiResponse.ok(this.salesFulfillmentService.creditProfile(customerId));
	}

	@PutMapping("/credit-profiles/{customerId}")
	public ApiResponse<SalesFulfillmentService.CreditProfileResponse> updateCreditProfile(
			@PathVariable Long customerId,
			@Valid @RequestBody SalesFulfillmentService.CreditProfileRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.salesFulfillmentService.updateCreditProfile(customerId, request, currentUser));
	}

	@GetMapping("/customers/{id}/credit-exposure")
	public ApiResponse<SalesFulfillmentService.CreditExposureResponse> creditExposure(@PathVariable Long id) {
		return ApiResponse.ok(this.salesFulfillmentService.creditExposure(id));
	}

	@GetMapping("/delivery-plans")
	public ApiResponse<PageResponse<SalesFulfillmentService.DeliveryPlanResponse>> deliveryPlans(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long projectId,
			@RequestParam(required = false) Long contractId,
			@RequestParam(required = false) Long orderId,
			@RequestParam(required = false) Long materialId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateTo,
			@RequestParam(required = false) Boolean countedOnly,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesFulfillmentService.deliveryPlans(keyword, customerId, projectId, contractId,
				orderId, materialId, status, expectedDateFrom, expectedDateTo, countedOnly, page, pageSize));
	}

	@GetMapping("/effective-demands")
	public ApiResponse<PageResponse<SalesFulfillmentService.EffectiveDemandResponse>> effectiveDemands(
			@RequestParam(required = false) Long projectId,
			@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long contractId,
			@RequestParam(required = false) Long orderId,
			@RequestParam(required = false) Long materialId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateTo,
			@RequestParam(required = false) Boolean countedOnly,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesFulfillmentService.effectiveDemands(projectId, customerId, contractId,
				orderId, materialId, status, expectedDateFrom, expectedDateTo, countedOnly, page, pageSize));
	}

	@PostMapping("/orders/{id}/submit-credit-override")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitCreditOverride(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.submitCreditOverride(id, request, currentUser,
				servletRequest));
	}

	@PostMapping("/orders/{id}/changes")
	public ApiResponse<SalesFulfillmentService.OrderChangeResponse> createOrderChange(@PathVariable Long id,
			@Valid @RequestBody SalesFulfillmentService.OrderChangeRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.createOrderChange(id, request, currentUser, servletRequest));
	}

	@GetMapping("/orders/{id}/changes")
	public ApiResponse<PageResponse<SalesFulfillmentService.OrderChangeResponse>> orderChanges(
			@PathVariable Long id,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesFulfillmentService.orderChanges(id, status, page, pageSize));
	}

	@GetMapping("/order-changes/{id}")
	public ApiResponse<SalesFulfillmentService.OrderChangeResponse> orderChange(@PathVariable Long id) {
		return ApiResponse.ok(this.salesFulfillmentService.orderChange(id));
	}

	@PutMapping("/order-changes/{id}")
	public ApiResponse<SalesFulfillmentService.OrderChangeResponse> updateOrderChange(@PathVariable Long id,
			@Valid @RequestBody SalesFulfillmentService.OrderChangeRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.updateOrderChange(id, request, currentUser,
				servletRequest));
	}

	@PostMapping("/order-changes/{id}/submit-approval")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitOrderChangeApproval(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.submitOrderChangeApproval(id, request, currentUser,
				servletRequest));
	}

	@PostMapping("/order-changes/{id}/cancel")
	public ApiResponse<SalesFulfillmentService.OrderChangeResponse> cancelOrderChange(@PathVariable Long id,
			@Valid @RequestBody SalesQuoteService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.cancelOrderChange(id, request, currentUser,
				servletRequest));
	}

	@PostMapping("/orders/{id}/submit-short-close")
	public ApiResponse<PlatformApprovalService.ApprovalInstanceRecord> submitShortClose(@PathVariable Long id,
			@Valid @RequestBody PlatformApprovalService.ApprovalSubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.submitShortClose(id, request, currentUser, servletRequest));
	}

	@GetMapping("/orders/{id}/delivery-plans")
	public ApiResponse<PageResponse<SalesFulfillmentService.DeliveryPlanResponse>> orderDeliveryPlans(
			@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesFulfillmentService.orderDeliveryPlans(id, page, pageSize));
	}

	@PutMapping("/orders/{id}/delivery-plans")
	public ApiResponse<SalesFulfillmentService.DeliveryPlanListResponse> replaceOrderDeliveryPlans(
			@PathVariable Long id,
			@Valid @RequestBody SalesFulfillmentService.DeliveryPlanReplaceRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.replaceOrderDeliveryPlans(id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/orders/{id}/delivery-plans/{planId}/close")
	public ApiResponse<SalesFulfillmentService.DeliveryPlanResponse> closeDeliveryPlan(@PathVariable Long id,
			@PathVariable Long planId, @Valid @RequestBody SalesQuoteService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.salesFulfillmentService.closeDeliveryPlan(id, planId, request, currentUser,
				servletRequest));
	}

}
