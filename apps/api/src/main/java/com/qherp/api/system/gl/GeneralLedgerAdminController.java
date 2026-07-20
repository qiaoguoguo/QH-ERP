package com.qherp.api.system.gl;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/admin/gl")
public class GeneralLedgerAdminController {

	private final GeneralLedgerSetupService setupService;

	private final GeneralLedgerVoucherService voucherService;

	private final GeneralLedgerQueryService queryService;

	public GeneralLedgerAdminController(GeneralLedgerSetupService setupService,
			GeneralLedgerVoucherService voucherService, GeneralLedgerQueryService queryService) {
		this.setupService = setupService;
		this.voucherService = voucherService;
		this.queryService = queryService;
	}

	@GetMapping("/ledger")
	public ApiResponse<Map<String, Object>> ledger() {
		return ApiResponse.ok(this.setupService.ledger());
	}

	@PostMapping("/ledger/initialize")
	public ApiResponse<Map<String, Object>> initialize(
			@RequestBody GeneralLedgerSetupService.LedgerInitializeRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.initialize(request, currentUser));
	}

	@GetMapping("/accounting-periods")
	public ApiResponse<PageResponse<Map<String, Object>>> periods(@RequestParam(required = false) String periodCode,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.periods(periodCode, page, pageSize));
	}

	@PostMapping("/accounting-periods")
	public ApiResponse<Map<String, Object>> createPeriod(
			@RequestBody(required = false) GeneralLedgerSetupService.PeriodCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		GeneralLedgerSetupService.PeriodCreateRequest effective = request == null
				? new GeneralLedgerSetupService.PeriodCreateRequest(null, null) : request;
		return ApiResponse.ok(this.setupService.createPeriod(effective, currentUser));
	}

	@GetMapping("/accounts")
	public ApiResponse<PageResponse<Map<String, Object>>> accounts(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.accounts(keyword, page, pageSize));
	}

	@PostMapping("/accounts")
	public ApiResponse<Map<String, Object>> createAccount(
			@RequestBody GeneralLedgerSetupService.AccountRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.createAccount(request, currentUser));
	}

	@GetMapping("/accounts/{id}")
	public ApiResponse<Map<String, Object>> account(@PathVariable Long id) {
		return ApiResponse.ok(this.setupService.account(id, true));
	}

	@PutMapping("/accounts/{id}")
	public ApiResponse<Map<String, Object>> updateAccount(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.AccountRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.updateAccount(id, request, currentUser));
	}

	@PostMapping("/accounts/{id}/disable")
	public ApiResponse<Map<String, Object>> disableAccount(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.disableAccount(id, currentUser));
	}

	@GetMapping("/aux-dimensions")
	public ApiResponse<PageResponse<Map<String, Object>>> auxDimensions(
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.auxDimensions(page, pageSize));
	}

	@GetMapping("/aux-dimensions/{dimensionCode}/candidates")
	public ApiResponse<PageResponse<Map<String, Object>>> auxiliaryCandidates(@PathVariable String dimensionCode,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.auxiliaryCandidates(dimensionCode, keyword, page, pageSize));
	}

	@GetMapping("/posting-rules")
	public ApiResponse<PageResponse<Map<String, Object>>> postingRules(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.postingRules(page, pageSize));
	}

	@GetMapping("/vouchers")
	public ApiResponse<PageResponse<Map<String, Object>>> vouchers(@RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.vouchers(status, keyword, page, pageSize, currentUser));
	}

	@PostMapping("/vouchers")
	public ApiResponse<Map<String, Object>> createVoucher(
			@RequestBody GeneralLedgerVoucherService.VoucherRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.create(request, currentUser, servletRequest));
	}

	@GetMapping("/vouchers/{id}")
	public ApiResponse<Map<String, Object>> voucher(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.voucher(id, currentUser));
	}

	@PutMapping("/vouchers/{id}")
	public ApiResponse<Map<String, Object>> updateVoucher(@PathVariable Long id,
			@RequestBody GeneralLedgerVoucherService.VoucherRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.update(id, request, currentUser, servletRequest));
	}

	@PostMapping("/vouchers/from-finance-draft/{draftId}")
	public ApiResponse<Map<String, Object>> convertFinanceDraft(@PathVariable Long draftId,
			@RequestBody GeneralLedgerVoucherService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.convertFinanceDraft(draftId, request, currentUser, servletRequest));
	}

	@PostMapping("/vouchers/{id}/submit")
	public ApiResponse<Map<String, Object>> submit(@PathVariable Long id,
			@RequestBody GeneralLedgerVoucherService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.submit(id, request, currentUser, servletRequest));
	}

	@PostMapping("/vouchers/{id}/cancel")
	public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id,
			@RequestBody GeneralLedgerVoucherService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.cancel(id, request, currentUser, servletRequest));
	}

	@PostMapping("/vouchers/{id}/refresh-source")
	public ApiResponse<Map<String, Object>> refreshSource(@PathVariable Long id,
			@RequestBody GeneralLedgerVoucherService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.refreshSource(id, request, currentUser, servletRequest));
	}

	@PostMapping("/vouchers/{id}/reversals")
	public ApiResponse<Map<String, Object>> reverse(@PathVariable Long id,
			@RequestBody GeneralLedgerVoucherService.ReversalRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.reverse(id, request, currentUser, servletRequest));
	}

	@GetMapping("/ledgers/general")
	public ApiResponse<PageResponse<Map<String, Object>>> generalLedger(@RequestParam String periodCode,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.generalLedger(periodCode, page, pageSize, currentUser));
	}

	@GetMapping("/ledgers/detail")
	public ApiResponse<PageResponse<Map<String, Object>>> detailLedger(@RequestParam String periodCode,
			@RequestParam(required = false) String voucherNo, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.detailLedger(periodCode, voucherNo, page, pageSize, currentUser));
	}

	@GetMapping("/account-balances")
	public ApiResponse<PageResponse<Map<String, Object>>> accountBalances(@RequestParam String periodCode,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.accountBalances(periodCode, page, pageSize, currentUser));
	}

	@GetMapping("/trial-balance")
	public ApiResponse<Map<String, Object>> trialBalance(@RequestParam String periodCode,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.trialBalance(periodCode, currentUser));
	}

	@GetMapping("/source-claims")
	public ApiResponse<PageResponse<Map<String, Object>>> sourceClaims(
			@RequestParam(required = false) String sourceType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ignoredDate,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(PageResponse.of(java.util.List.of(), page, GeneralLedgerSupport.limit(pageSize), 0));
	}

}
