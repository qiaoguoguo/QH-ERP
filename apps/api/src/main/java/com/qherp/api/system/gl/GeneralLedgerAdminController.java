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
			@RequestParam(required = false) String category, @RequestParam(required = false) Boolean enabled,
			@RequestParam(required = false) Boolean postable,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.accounts(keyword, category, enabled, postable, page, pageSize));
	}

	@GetMapping("/accounts/candidates")
	public ApiResponse<PageResponse<Map<String, Object>>> accountCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String selectedIds,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.accountCandidates(keyword, selectedIds, page, pageSize));
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
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean enabled,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.auxDimensions(keyword, enabled, page, pageSize));
	}

	@PostMapping("/aux-dimensions")
	public ApiResponse<Map<String, Object>> createAuxDimension(
			@RequestBody GeneralLedgerSetupService.AuxDimensionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.createAuxDimension(request, currentUser));
	}

	@PutMapping("/aux-dimensions/{id}")
	public ApiResponse<Map<String, Object>> updateAuxDimension(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.AuxDimensionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.updateAuxDimension(id, request, currentUser));
	}

	@GetMapping("/aux-dimensions/{id}/items")
	public ApiResponse<PageResponse<Map<String, Object>>> auxItems(@PathVariable Long id,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.auxItems(id, keyword, page, pageSize));
	}

	@PostMapping("/aux-dimensions/{id}/items")
	public ApiResponse<Map<String, Object>> createAuxItem(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.AuxItemRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.createAuxItem(id, request, currentUser));
	}

	@PutMapping("/aux-dimensions/{id}/items/{itemId}")
	public ApiResponse<Map<String, Object>> updateAuxItem(@PathVariable Long id, @PathVariable Long itemId,
			@RequestBody GeneralLedgerSetupService.AuxItemRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.updateAuxItem(id, itemId, request, currentUser));
	}

	@GetMapping("/aux-dimensions/{dimensionCode}/candidates")
	public ApiResponse<PageResponse<Map<String, Object>>> auxiliaryCandidates(@PathVariable String dimensionCode,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String selectedIds,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.auxiliaryCandidates(dimensionCode, keyword, selectedIds, page,
				pageSize, currentUser));
	}

	@GetMapping("/posting-rules")
	public ApiResponse<PageResponse<Map<String, Object>>> postingRules(
			@RequestParam(required = false) String sourceType, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.setupService.postingRules(sourceType, status, page, pageSize));
	}

	@GetMapping("/posting-rules/{id}")
	public ApiResponse<Map<String, Object>> postingRule(@PathVariable Long id) {
		return ApiResponse.ok(this.setupService.postingRule(id));
	}

	@PostMapping("/posting-rules")
	public ApiResponse<Map<String, Object>> createPostingRule(
			@RequestBody GeneralLedgerSetupService.PostingRuleRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.createPostingRule(request, currentUser));
	}

	@PostMapping("/posting-rules/{id}/new-version")
	public ApiResponse<Map<String, Object>> newPostingRuleVersion(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.newPostingRuleVersion(id, request, currentUser));
	}

	@PutMapping("/posting-rules/{id}")
	public ApiResponse<Map<String, Object>> updatePostingRule(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.PostingRuleRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.updatePostingRule(id, request, currentUser));
	}

	@PostMapping("/posting-rules/{id}/validate")
	public ApiResponse<Map<String, Object>> validatePostingRule(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.validatePostingRule(id, request, currentUser));
	}

	@PostMapping("/posting-rules/{id}/activate")
	public ApiResponse<Map<String, Object>> activatePostingRule(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.activatePostingRule(id, request, currentUser));
	}

	@PostMapping("/posting-rules/{id}/disable")
	public ApiResponse<Map<String, Object>> disablePostingRule(@PathVariable Long id,
			@RequestBody GeneralLedgerSetupService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.setupService.disablePostingRule(id, request, currentUser));
	}

	@GetMapping("/vouchers")
	public ApiResponse<PageResponse<Map<String, Object>>> vouchers(@RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long sourceId, @RequestParam(required = false) String periodCode,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.vouchers(status, keyword, sourceType, sourceId, periodCode, page, pageSize,
				currentUser));
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

	@PostMapping("/vouchers/{id}/withdraw")
	public ApiResponse<Map<String, Object>> withdraw(@PathVariable Long id,
			@RequestBody GeneralLedgerVoucherService.ActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.voucherService.withdraw(id, request, currentUser, servletRequest));
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
			@RequestParam(required = false) String accountKeyword, @RequestParam(required = false) Long accountId,
			@RequestParam(required = false) String accountCodeFrom,
			@RequestParam(required = false) String accountCodeTo,
			@RequestParam(required = false) Integer level, @RequestParam(required = false) String auxiliaryKeyword,
			@RequestParam(required = false) String sourceType,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.generalLedger(periodCode, accountKeyword, accountId, accountCodeFrom,
				accountCodeTo, level, auxiliaryKeyword, sourceType, page, pageSize, currentUser));
	}

	@GetMapping("/ledgers/detail")
	public ApiResponse<PageResponse<Map<String, Object>>> detailLedger(@RequestParam String periodCode,
			@RequestParam(required = false) String voucherNo, @RequestParam(required = false) String accountKeyword,
			@RequestParam(required = false) Long accountId, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long sourceId, @RequestParam(required = false) String auxiliaryKeyword,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.detailLedger(periodCode, voucherNo, accountKeyword, accountId,
				sourceType, sourceId, auxiliaryKeyword, page, pageSize, currentUser));
	}

	@GetMapping("/account-balances")
	public ApiResponse<PageResponse<Map<String, Object>>> accountBalances(@RequestParam String periodCode,
			@RequestParam(required = false) String accountKeyword, @RequestParam(required = false) Long accountId,
			@RequestParam(required = false) Integer level, @RequestParam(required = false) String auxiliaryKeyword,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.accountBalances(periodCode, accountKeyword, accountId, level,
				auxiliaryKeyword, page, pageSize, currentUser));
	}

	@GetMapping("/trial-balance")
	public ApiResponse<Map<String, Object>> trialBalance(@RequestParam String periodCode,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.trialBalance(periodCode, currentUser));
	}

	@GetMapping("/source-claims")
	public ApiResponse<PageResponse<Map<String, Object>>> sourceClaims(
			@RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long sourceId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ignoredDate,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.sourceClaims(sourceType, sourceId, page, pageSize, currentUser));
	}

}
