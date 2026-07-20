package com.qherp.api.system.financialclose;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinancialCloseModels {

	private FinancialCloseModels() {
	}

	public record CheckRequest(String idempotencyKey) {
	}

	public record CloseRequest(Long version, String reason, String idempotencyKey) {
	}

	public record ReopenRequest(Long version, String reason, String idempotencyKey) {
	}

	public record ProfitLossGenerateRequest(String sourceFingerprint, String reason, String idempotencyKey) {
	}

	public record BankAccountRequest(String accountName, String accountType, String bankName, String currency,
			Long glAccountId, LocalDate openedOn, String accountNo, Long version, String disabledReason,
			String idempotencyKey) {
	}

	public record BankStatementRequest(Long bankAccountId, LocalDate transactionDate, LocalDate postingDate,
			String direction, BigDecimal amount, String counterpartyName, String summary, String bankTransactionId,
			String referenceNo, String idempotencyKey) {
	}

	public record BankReconciliationRequest(Long periodId, Long bankAccountId, String idempotencyKey) {
	}

	public record BankMatchRequest(Long version, String matchGroupNo, List<BankMatchLineRequest> matches,
			String idempotencyKey) {
	}

	public record BankMatchLineRequest(Long statementLineId, Long ledgerEntryId, BigDecimal amount) {
	}

	public record BankExceptionRequest(Long version, String exceptionType, Long statementLineId, Long ledgerEntryId,
			BigDecimal amount, String reason, String idempotencyKey) {
	}

	public record VersionedActionRequest(Long version, String reason, String idempotencyKey) {
	}

	public record TaxProfileRequest(String taxpayerType, String creditCode, String taxAuthority,
			String vatPeriodicity, BigDecimal incomeTaxRate, BigDecimal urbanMaintenanceRate, LocalDate effectiveFrom,
			Long version, String idempotencyKey) {
	}

	public record TaxRateRuleRequest(String taxType, String rateCode, BigDecimal rateValue, LocalDate effectiveFrom,
			LocalDate effectiveTo, String idempotencyKey) {
	}

	public record TaxInvoiceTypeRequest(String code, String name, String direction, Boolean deductible,
			String idempotencyKey) {
	}

	public record TaxSummaryCreateRequest(@NotNull String periodCode, String taxType, String idempotencyKey) {
	}

	public record TaxAdjustmentRequest(Long summaryId, Long version, String adjustmentType, BigDecimal amount,
			String reason, String idempotencyKey) {
	}

	public record TaxPaymentRequest(Long summaryId, String taxType, LocalDate paymentDate, BigDecimal amount,
			String paymentMethod, String referenceNo, Long voucherId, Long paymentId, Long bankAccountId,
			String reason, String idempotencyKey) {
	}

	public record TaxPaymentCorrectionRequest(BigDecimal amount, String reason, String idempotencyKey) {
	}

}
