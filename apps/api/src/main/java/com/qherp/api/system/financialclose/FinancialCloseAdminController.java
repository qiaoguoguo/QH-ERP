package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin")
public class FinancialCloseAdminController {

	private final FinancialCloseQueryService queryService;

	private final FinancialCloseCheckService checkService;

	private final FinancialCloseService closeService;

	private final ProfitLossTransferService profitLossTransferService;

	private final BankReconciliationService bankReconciliationService;

	private final TaxFoundationService taxFoundationService;

	public FinancialCloseAdminController(FinancialCloseQueryService queryService,
			FinancialCloseCheckService checkService, FinancialCloseService closeService,
			ProfitLossTransferService profitLossTransferService, BankReconciliationService bankReconciliationService,
			TaxFoundationService taxFoundationService) {
		this.queryService = queryService;
		this.checkService = checkService;
		this.closeService = closeService;
		this.profitLossTransferService = profitLossTransferService;
		this.bankReconciliationService = bankReconciliationService;
		this.taxFoundationService = taxFoundationService;
	}

	@GetMapping("/financial-closes/periods")
	public ApiResponse<PageResponse<Map<String, Object>>> periods(
			@RequestParam(required = false) String periodCode, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.periods(periodCode, page, pageSize, currentUser));
	}

	@GetMapping("/financial-closes/periods/{id}")
	public ApiResponse<Map<String, Object>> period(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.period(id, currentUser));
	}

	@PostMapping("/financial-closes/periods/{id}/checks")
	public ApiResponse<Map<String, Object>> check(@PathVariable Long id,
			@RequestBody FinancialCloseModels.CheckRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.checkService.runCheck(id, request, currentUser));
	}

	@GetMapping("/financial-closes/check-runs/{id}")
	public ApiResponse<Map<String, Object>> checkRun(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.checkRun(id, currentUser));
	}

	@PostMapping("/financial-closes/check-runs/{id}/close")
	public ApiResponse<Map<String, Object>> close(@PathVariable Long id,
			@RequestBody FinancialCloseModels.CloseRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.closeService.close(id, request, currentUser));
	}

	@GetMapping("/financial-closes/close-runs/{id}")
	public ApiResponse<Map<String, Object>> closeRun(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.closeRun(id, currentUser));
	}

	@PostMapping("/financial-closes/close-runs/{id}/reopen-requests")
	public ApiResponse<Map<String, Object>> reopenRequest(@PathVariable Long id,
			@RequestBody FinancialCloseModels.ReopenRequest request, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.closeService.submitReopenRequest(id, request, currentUser, servletRequest));
	}

	@GetMapping("/financial-closes/reopen-requests/{id}")
	public ApiResponse<Map<String, Object>> reopenRequest(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.reopenRequest(id, currentUser));
	}

	@GetMapping("/financial-closes/periods/{id}/profit-loss-transfers")
	public ApiResponse<PageResponse<Map<String, Object>>> profitLossTransfers(@PathVariable Long id,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.profitLossTransfers(id, page, pageSize, currentUser));
	}

	@PostMapping("/financial-closes/periods/{id}/profit-loss-transfers/preview")
	public ApiResponse<Map<String, Object>> previewProfitLoss(@PathVariable Long id,
			@RequestBody FinancialCloseModels.CheckRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.profitLossTransferService.preview(id, request, currentUser));
	}

	@PostMapping("/financial-closes/periods/{id}/profit-loss-transfers")
	public ApiResponse<Map<String, Object>> generateProfitLoss(@PathVariable Long id,
			@RequestBody FinancialCloseModels.ProfitLossGenerateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.profitLossTransferService.generate(id, request, currentUser));
	}

	@GetMapping("/bank-accounts")
	public ApiResponse<PageResponse<Map<String, Object>>> bankAccounts(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.accounts(page, pageSize, currentUser));
	}

	@PostMapping("/bank-accounts")
	public ApiResponse<Map<String, Object>> createBankAccount(
			@RequestBody FinancialCloseModels.BankAccountRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.createAccount(request, currentUser));
	}

	@GetMapping("/bank-accounts/{id}")
	public ApiResponse<Map<String, Object>> bankAccount(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.account(id, currentUser));
	}

	@PutMapping("/bank-accounts/{id}")
	public ApiResponse<Map<String, Object>> updateBankAccount(@PathVariable Long id,
			@RequestBody FinancialCloseModels.BankAccountRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.updateAccount(id, request, currentUser));
	}

	@PostMapping("/bank-accounts/{id}/disable")
	public ApiResponse<Map<String, Object>> disableBankAccount(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.disableAccount(id, request, currentUser));
	}

	@GetMapping("/bank-statements")
	public ApiResponse<PageResponse<Map<String, Object>>> bankStatements(
			@RequestParam(required = false) Long bankAccountId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.statementLines(bankAccountId, page, pageSize,
				currentUser));
	}

	@PostMapping("/bank-statements")
	public ApiResponse<Map<String, Object>> importBankStatement(
			@RequestBody FinancialCloseModels.BankStatementRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.importStatementLine(request, currentUser));
	}

	@PostMapping("/bank-statements/import-preview")
	public ApiResponse<Map<String, Object>> previewBankStatementImport(
			@RequestBody FinancialCloseModels.BankStatementImportRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.previewStatementImport(request, currentUser));
	}

	@PostMapping("/bank-statements/import-confirm")
	public ApiResponse<Map<String, Object>> confirmBankStatementImport(
			@RequestBody FinancialCloseModels.BankStatementImportRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.confirmStatementImport(request, currentUser));
	}

	@GetMapping("/bank-statements/{id}")
	public ApiResponse<Map<String, Object>> bankStatement(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.statementLine(id, currentUser));
	}

	@PostMapping("/bank-statement-lines/{id}/ignore")
	public ApiResponse<Map<String, Object>> ignoreBankStatementLine(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.ignoreStatementLine(id, request, currentUser));
	}

	@GetMapping("/bank-reconciliations")
	public ApiResponse<PageResponse<Map<String, Object>>> bankReconciliations(
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.reconciliations(page, pageSize, currentUser));
	}

	@PostMapping("/bank-reconciliations")
	public ApiResponse<Map<String, Object>> createBankReconciliation(
			@RequestBody FinancialCloseModels.BankReconciliationRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.createReconciliation(request, currentUser));
	}

	@GetMapping("/bank-reconciliations/{id}")
	public ApiResponse<Map<String, Object>> bankReconciliation(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.reconciliation(id, currentUser));
	}

	@GetMapping("/bank-reconciliations/{id}/candidates")
	public ApiResponse<Map<String, Object>> bankReconciliationCandidates(@PathVariable Long id,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.candidates(id, page, pageSize, currentUser));
	}

	@PostMapping("/bank-reconciliations/{id}/matches")
	public ApiResponse<Map<String, Object>> matchBankReconciliation(@PathVariable Long id,
			@RequestBody FinancialCloseModels.BankMatchRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.match(id, request, currentUser));
	}

	@DeleteMapping("/bank-reconciliations/{id}/matches")
	public ApiResponse<Map<String, Object>> cancelBankReconciliationMatch(@PathVariable Long id,
			@RequestParam String matchGroupNo, @RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.cancelMatch(id, matchGroupNo, request, currentUser));
	}

	@PostMapping("/bank-reconciliations/{id}/exceptions")
	public ApiResponse<Map<String, Object>> classifyBankReconciliationException(@PathVariable Long id,
			@RequestBody FinancialCloseModels.BankExceptionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.classifyException(id, request, currentUser));
	}

	@PostMapping("/bank-reconciliations/{id}/calculate")
	public ApiResponse<Map<String, Object>> calculateBankReconciliation(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.calculate(id, request, currentUser));
	}

	@PostMapping("/bank-reconciliations/{id}/confirm")
	public ApiResponse<Map<String, Object>> confirmBankReconciliation(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.confirmReconciliation(id, request, currentUser));
	}

	@PostMapping("/bank-reconciliations/{id}/reopen")
	public ApiResponse<Map<String, Object>> reopenBankReconciliation(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.bankReconciliationService.reopenReconciliation(id, request, currentUser));
	}

	@GetMapping("/tax-profiles/current")
	public ApiResponse<Map<String, Object>> currentTaxProfile() {
		return ApiResponse.ok(this.taxFoundationService.currentProfile());
	}

	@PutMapping("/tax-profiles/current")
	public ApiResponse<Map<String, Object>> upsertTaxProfile(
			@RequestBody FinancialCloseModels.TaxProfileRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.upsertCurrentProfile(request, currentUser));
	}

	@GetMapping("/tax-rate-rules")
	public ApiResponse<PageResponse<Map<String, Object>>> taxRateRules(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.taxFoundationService.rateRules(page, pageSize));
	}

	@PostMapping("/tax-rate-rules")
	public ApiResponse<Map<String, Object>> createTaxRateRule(
			@RequestBody FinancialCloseModels.TaxRateRuleRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.createRateRule(request, currentUser));
	}

	@GetMapping("/tax-invoice-types")
	public ApiResponse<PageResponse<Map<String, Object>>> taxInvoiceTypes(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.ok(this.taxFoundationService.invoiceTypes(page, pageSize));
	}

	@PostMapping("/tax-invoice-types")
	public ApiResponse<Map<String, Object>> createTaxInvoiceType(
			@RequestBody FinancialCloseModels.TaxInvoiceTypeRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.createInvoiceType(request, currentUser));
	}

	@GetMapping("/tax-summaries")
	public ApiResponse<PageResponse<Map<String, Object>>> taxSummaries(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.summaries(page, pageSize, currentUser));
	}

	@PostMapping("/tax-summaries")
	public ApiResponse<Map<String, Object>> createTaxSummary(
			@RequestBody FinancialCloseModels.TaxSummaryCreateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.createSummary(request, currentUser));
	}

	@GetMapping("/tax-summaries/{id}")
	public ApiResponse<Map<String, Object>> taxSummary(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.summary(id, currentUser));
	}

	@PostMapping("/tax-summaries/{id}/calculate")
	public ApiResponse<Map<String, Object>> calculateTaxSummary(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.calculateSummary(id, request, currentUser));
	}

	@PostMapping("/tax-summaries/{id}/adjustments")
	public ApiResponse<Map<String, Object>> adjustTaxSummary(@PathVariable Long id,
			@RequestBody FinancialCloseModels.TaxAdjustmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.adjustSummary(id, request, currentUser));
	}

	@PostMapping("/tax-summaries/{id}/confirm")
	public ApiResponse<Map<String, Object>> confirmTaxSummary(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.confirmSummary(id, request, currentUser));
	}

	@PostMapping("/tax-summaries/{id}/voucher-drafts")
	public ApiResponse<Map<String, Object>> generateTaxVoucher(@PathVariable Long id,
			@RequestBody FinancialCloseModels.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.generateVoucherDraft(id, request, currentUser));
	}

	@GetMapping("/tax-payments")
	public ApiResponse<PageResponse<Map<String, Object>>> taxPayments(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.payments(page, pageSize, currentUser));
	}

	@PostMapping("/tax-payments")
	public ApiResponse<Map<String, Object>> recordTaxPayment(
			@RequestBody FinancialCloseModels.TaxPaymentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.recordPayment(request, currentUser));
	}

	@PostMapping("/tax-payments/{id}/corrections")
	public ApiResponse<Map<String, Object>> correctTaxPayment(@PathVariable Long id,
			@RequestBody FinancialCloseModels.TaxPaymentCorrectionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.taxFoundationService.correctPayment(id, request, currentUser));
	}

}
