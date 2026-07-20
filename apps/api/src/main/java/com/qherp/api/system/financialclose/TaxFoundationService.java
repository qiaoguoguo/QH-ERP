package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.gl.GeneralLedgerVoucherService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TaxFoundationService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final JdbcTemplate jdbcTemplate;

	private final GeneralLedgerVoucherService voucherService;

	private final FinancialCloseAuditService auditService;

	public TaxFoundationService(JdbcTemplate jdbcTemplate, GeneralLedgerVoucherService voucherService,
			FinancialCloseAuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.voucherService = voucherService;
		this.auditService = auditService;
	}

	@Transactional
	public Map<String, Object> upsertCurrentProfile(FinancialCloseModels.TaxProfileRequest request,
			CurrentUser operator) {
		requireRequest(request);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_PROFILE_UPSERT|"
				+ profileFingerprint(request));
		Long existingByKey = idempotentResult("FIN_TAX_PROFILE_UPSERT", "FIN_TAX_PROFILE_CURRENT", 0L,
				key, requestFingerprint, operator);
		if (existingByKey != null) {
			return profile(existingByKey, operator);
		}
		Long existing = currentProfileId();
		if (existing == null) {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into fin_tax_profile (
						taxpayer_type, credit_code, tax_authority, vat_periodicity, income_tax_rate,
						urban_maintenance_rate, education_surcharge_rate, local_education_surcharge_rate,
						income_adjustment_increase, income_adjustment_decrease, loss_deduction, prepaid_income_tax,
						effective_from, current_flag, created_by, updated_by
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
					returning id
					""", Long.class, normalized(request.taxpayerType(), "GENERAL"),
					FinancialCloseSupport.requiredText(request.creditCode(), ApiErrorCode.VALIDATION_ERROR),
					FinancialCloseSupport.text(request.taxAuthority()), normalized(request.vatPeriodicity(), "MONTHLY"),
					rate(request.incomeTaxRate()), rate(request.urbanMaintenanceRate()),
					rate(request.educationSurchargeRate()), rate(request.localEducationSurchargeRate()),
					amount(request.incomeAdjustmentIncrease()), amount(request.incomeAdjustmentDecrease()),
					amount(request.lossDeduction()), amount(request.prepaidIncomeTax()),
					request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom(), operator.username(),
					operator.username());
			recordIdempotency("FIN_TAX_PROFILE_UPSERT", "FIN_TAX_PROFILE_CURRENT", 0L, null, key,
					requestFingerprint, "FIN_TAX_PROFILE", id, 0L, operator);
			this.auditService.success(operator, "FIN_TAX_PROFILE_UPSERT", "FIN_TAX_PROFILE", id);
			return profile(id, operator);
		}
		Long actualVersion = this.jdbcTemplate.queryForObject("select version from fin_tax_profile where id = ?",
				Long.class, existing);
		if (request.version() != null && !request.version().equals(actualVersion)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		this.jdbcTemplate.update("""
				update fin_tax_profile
				set taxpayer_type = ?, credit_code = ?, tax_authority = ?, vat_periodicity = ?, income_tax_rate = ?,
				    urban_maintenance_rate = ?, education_surcharge_rate = ?, local_education_surcharge_rate = ?,
				    income_adjustment_increase = ?, income_adjustment_decrease = ?, loss_deduction = ?,
				    prepaid_income_tax = ?, effective_from = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", normalized(request.taxpayerType(), "GENERAL"),
				FinancialCloseSupport.requiredText(request.creditCode(), ApiErrorCode.VALIDATION_ERROR),
				FinancialCloseSupport.text(request.taxAuthority()), normalized(request.vatPeriodicity(), "MONTHLY"),
				rate(request.incomeTaxRate()), rate(request.urbanMaintenanceRate()),
				rate(request.educationSurchargeRate()), rate(request.localEducationSurchargeRate()),
				amount(request.incomeAdjustmentIncrease()), amount(request.incomeAdjustmentDecrease()),
				amount(request.lossDeduction()), amount(request.prepaidIncomeTax()),
				request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom(), operator.username(),
				OffsetDateTime.now(), existing);
		Long version = this.jdbcTemplate.queryForObject("select version from fin_tax_profile where id = ?",
				Long.class, existing);
		recordIdempotency("FIN_TAX_PROFILE_UPSERT", "FIN_TAX_PROFILE_CURRENT", 0L, actualVersion, key,
				requestFingerprint, "FIN_TAX_PROFILE", existing, version, operator);
		this.auditService.success(operator, "FIN_TAX_PROFILE_UPSERT", "FIN_TAX_PROFILE", existing);
		return profile(existing, operator);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> currentProfile(CurrentUser currentUser) {
		Long id = currentProfileId();
		if (id == null) {
			return Map.of();
		}
		return profile(id, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> rateRules(int page, int pageSize) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_tax_rate_rule", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from fin_tax_rate_rule
				order by tax_type, rate_code
				limit ? offset ?
				""", (rs, rowNum) -> rateRule(rs.getLong("id")), safePageSize,
				FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> createRateRule(FinancialCloseModels.TaxRateRuleRequest request, CurrentUser operator) {
		requireRequest(request);
		String taxType = normalized(request.taxType(), "VAT");
		String rateCode = FinancialCloseSupport.requiredText(request.rateCode(), ApiErrorCode.VALIDATION_ERROR)
			.toUpperCase();
		BigDecimal rateValue = rate(request.rateValue());
		LocalDate effectiveFrom = request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom();
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_RATE_RULE_UPSERT|" + taxType + "|"
				+ rateCode + "|" + decimalKey(rateValue) + "|" + effectiveFrom + "|" + request.effectiveTo());
		Long existingByKey = idempotentResult("FIN_TAX_RATE_RULE_UPSERT", "FIN_TAX_RATE_RULE", null, key,
				requestFingerprint, operator);
		if (existingByKey != null) {
			return rateRule(existingByKey);
		}
		Long id = this.jdbcTemplate.queryForObject("""
				insert into fin_tax_rate_rule (
					tax_type, rate_code, rate_value, effective_from, effective_to, status, created_by, updated_by
				)
				values (?, ?, ?, ?, ?, 'ENABLED', ?, ?)
				on conflict (tax_type, rate_code, effective_from) do update
				set rate_value = excluded.rate_value,
				    effective_to = excluded.effective_to,
				    status = 'ENABLED',
				    updated_by = excluded.updated_by,
				    updated_at = now(),
				    version = fin_tax_rate_rule.version + 1
				returning id
				""", Long.class, taxType, rateCode, rateValue, effectiveFrom, request.effectiveTo(),
				operator.username(), operator.username());
		Long version = this.jdbcTemplate.queryForObject("select version from fin_tax_rate_rule where id = ?",
				Long.class, id);
		recordIdempotency("FIN_TAX_RATE_RULE_UPSERT", "FIN_TAX_RATE_RULE", null, null, key,
				requestFingerprint, "FIN_TAX_RATE_RULE", id, version, operator);
		this.auditService.success(operator, "FIN_TAX_RATE_RULE_UPSERT", "FIN_TAX_RATE_RULE", id);
		return rateRule(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> invoiceTypes(int page, int pageSize) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_tax_invoice_type", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from fin_tax_invoice_type
				order by code
				limit ? offset ?
				""", (rs, rowNum) -> invoiceType(rs.getLong("id")), safePageSize,
				FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> createInvoiceType(FinancialCloseModels.TaxInvoiceTypeRequest request,
			CurrentUser operator) {
		requireRequest(request);
		String code = FinancialCloseSupport.requiredText(request.code(), ApiErrorCode.VALIDATION_ERROR).toUpperCase();
		String direction = FinancialCloseSupport.requiredText(request.direction(), ApiErrorCode.VALIDATION_ERROR)
			.toUpperCase();
		if (!List.of("OUTPUT", "INPUT").contains(direction)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String name = FinancialCloseSupport.requiredText(request.name(), ApiErrorCode.VALIDATION_ERROR);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_INVOICE_TYPE_UPSERT|" + code + "|"
				+ name + "|" + direction + "|" + Boolean.TRUE.equals(request.deductible()));
		Long existingByKey = idempotentResult("FIN_TAX_INVOICE_TYPE_UPSERT", "FIN_TAX_INVOICE_TYPE", null,
				key, requestFingerprint, operator);
		if (existingByKey != null) {
			return invoiceType(existingByKey);
		}
		Long id = this.jdbcTemplate.queryForObject("""
				insert into fin_tax_invoice_type (
					code, name, direction, deductible, status, created_by, updated_by
				)
				values (?, ?, ?, ?, 'ENABLED', ?, ?)
				on conflict (code) do update
				set name = excluded.name,
				    direction = excluded.direction,
				    deductible = excluded.deductible,
				    status = 'ENABLED',
				    updated_by = excluded.updated_by,
				    updated_at = now(),
				    version = fin_tax_invoice_type.version + 1
				returning id
				""", Long.class, code, name, direction, Boolean.TRUE.equals(request.deductible()),
				operator.username(), operator.username());
		Long version = this.jdbcTemplate.queryForObject("select version from fin_tax_invoice_type where id = ?",
				Long.class, id);
		recordIdempotency("FIN_TAX_INVOICE_TYPE_UPSERT", "FIN_TAX_INVOICE_TYPE", null, null, key,
				requestFingerprint, "FIN_TAX_INVOICE_TYPE", id, version, operator);
		this.auditService.success(operator, "FIN_TAX_INVOICE_TYPE_UPSERT", "FIN_TAX_INVOICE_TYPE", id);
		return invoiceType(id);
	}

	@Transactional
	public Map<String, Object> createSummary(FinancialCloseModels.TaxSummaryCreateRequest request,
			CurrentUser operator) {
		requireRequest(request);
		Period period = periodByCode(request.periodCode());
		String taxType = normalized(request.taxType(), "VAT");
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_SUMMARY_CREATE|" + period.id()
				+ "|" + period.periodCode() + "|" + taxType);
		Long existingByKey = idempotentResult("FIN_TAX_SUMMARY_CREATE", "GL_ACCOUNTING_PERIOD", period.id(),
				key, requestFingerprint, operator);
		if (existingByKey != null) {
			return summary(existingByKey, operator);
		}
		Map<String, Object> existing = currentSummary(period.id(), taxType, operator);
		if (existing != null) {
			recordIdempotency("FIN_TAX_SUMMARY_CREATE", "GL_ACCOUNTING_PERIOD", period.id(), null, key,
					requestFingerprint, "FIN_TAX_PERIOD_SUMMARY", ((Number) existing.get("id")).longValue(),
					((Number) existing.get("version")).longValue(), operator);
			return existing;
		}
		Long id = this.jdbcTemplate.queryForObject("""
				insert into fin_tax_period_summary (
					period_id, period_code, tax_type, status, source_fingerprint, disclaimer, idempotency_key,
					created_by, updated_by
				)
				values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)
				returning id
				""", Long.class, period.id(), period.periodCode(), taxType, sourceFingerprint(period),
				FinancialCloseSupport.TAX_DISCLAIMER, request.idempotencyKey(), operator.username(),
				operator.username());
		recordIdempotency("FIN_TAX_SUMMARY_CREATE", "GL_ACCOUNTING_PERIOD", period.id(), null, key,
				requestFingerprint, "FIN_TAX_PERIOD_SUMMARY", id, 0L, operator);
		this.auditService.success(operator, "FIN_TAX_SUMMARY_CREATE", "FIN_TAX_PERIOD_SUMMARY", id);
		return summary(id, operator);
	}

	@Transactional
	public Map<String, Object> calculateSummary(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		requireRequest(request);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_SUMMARY_CALCULATE|" + id + "|"
				+ request.version());
		Long existing = idempotentResult("FIN_TAX_SUMMARY_CALCULATE", "FIN_TAX_PERIOD_SUMMARY", id, key,
				requestFingerprint, operator);
		if (existing != null) {
			return summary(existing, operator);
		}
		SummaryRow row = lockSummary(id);
		requireMutable(row);
		requireNoVoucher(row);
		requireVersion(row.version(), request == null ? null : request.version());
		Period period = periodById(row.periodId());
		TaxAmounts amounts = amounts(period, totalAdjustments(id));
		String fingerprint = sourceFingerprint(period);
		this.jdbcTemplate.update("delete from fin_tax_summary_line where summary_id = ?", id);
		insertTaxLines(id, period);
		updateSummaryAmounts(id, "CALCULATED", fingerprint, amounts, operator);
		Map<String, Object> result = summary(id, operator);
		recordIdempotency("FIN_TAX_SUMMARY_CALCULATE", "FIN_TAX_PERIOD_SUMMARY", id, row.version(), key,
				requestFingerprint, "FIN_TAX_PERIOD_SUMMARY", id, ((Number) result.get("version")).longValue(),
				operator);
		this.auditService.success(operator, "FIN_TAX_SUMMARY_CALCULATE", "FIN_TAX_PERIOD_SUMMARY", id);
		return result;
	}

	@Transactional
	public Map<String, Object> adjustSummary(Long id, FinancialCloseModels.TaxAdjustmentRequest request,
			CurrentUser operator) {
		requireRequest(request);
		if (request.summaryId() != null && !request.summaryId().equals(id)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		String type = FinancialCloseSupport.requiredText(request.adjustmentType(), ApiErrorCode.VALIDATION_ERROR)
			.toUpperCase();
		if (!List.of("OUTPUT_INCREASE", "OUTPUT_DECREASE", "INPUT_INCREASE", "INPUT_DECREASE").contains(type)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		BigDecimal amount = FinancialCloseSupport.amount(request.amount());
		if (!FinancialCloseSupport.positive(amount)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String reason = FinancialCloseSupport.requiredChineseReason(request.reason(), ApiErrorCode.VALIDATION_ERROR);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_SUMMARY_ADJUST|" + id + "|"
				+ request.version() + "|" + type + "|" + FinancialCloseSupport.decimal(amount) + "|" + reason);
		Long existing = idempotentResult("FIN_TAX_SUMMARY_ADJUST", "FIN_TAX_PERIOD_SUMMARY", id, key,
				requestFingerprint, operator);
		if (existing != null) {
			return summary(existing, operator);
		}
		SummaryRow row = lockSummary(id);
		requireMutable(row);
		requireNoVoucher(row);
		requireVersion(row.version(), request.version());
		this.jdbcTemplate.update("""
				insert into fin_tax_adjustment (
					summary_id, adjustment_type, amount, reason, created_by
				)
				values (?, ?, ?, ?, ?)
				""", id, type, amount, reason, operator.username());
		Period period = periodById(row.periodId());
		updateSummaryAmounts(id, row.status(), row.sourceFingerprint(), amounts(period, totalAdjustments(id)),
				operator);
		Map<String, Object> result = summary(id, operator);
		recordIdempotency("FIN_TAX_SUMMARY_ADJUST", "FIN_TAX_PERIOD_SUMMARY", id, row.version(), key,
				requestFingerprint, "FIN_TAX_PERIOD_SUMMARY", id, ((Number) result.get("version")).longValue(),
				operator);
		this.auditService.success(operator, "FIN_TAX_SUMMARY_ADJUST", "FIN_TAX_PERIOD_SUMMARY", id);
		return result;
	}

	@Transactional
	public Map<String, Object> confirmSummary(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		requireRequest(request);
		String reason = FinancialCloseSupport.requiredChineseReason(request.reason(), ApiErrorCode.VALIDATION_ERROR);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_SUMMARY_CONFIRM|" + id + "|"
				+ request.version() + "|" + reason);
		Long existing = idempotentResult("FIN_TAX_SUMMARY_CONFIRM", "FIN_TAX_PERIOD_SUMMARY", id, key,
				requestFingerprint, operator);
		if (existing != null) {
			return summary(existing, operator);
		}
		SummaryRow row = lockSummary(id);
		requireMutable(row);
		requireVersion(row.version(), request == null ? null : request.version());
		if (!"CALCULATED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY);
		}
		String fingerprint = sourceFingerprint(periodById(row.periodId()));
		if (!fingerprint.equals(row.sourceFingerprint())) {
			throw new BusinessException(ApiErrorCode.FIN_TAX_SOURCE_CHANGED);
		}
		this.jdbcTemplate.update("""
				update fin_tax_period_summary
				set status = 'CONFIRMED', updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), id);
		Map<String, Object> result = summary(id, operator);
		recordIdempotency("FIN_TAX_SUMMARY_CONFIRM", "FIN_TAX_PERIOD_SUMMARY", id, row.version(), key,
				requestFingerprint, "FIN_TAX_PERIOD_SUMMARY", id, ((Number) result.get("version")).longValue(),
				operator);
		this.auditService.success(operator, "FIN_TAX_SUMMARY_CONFIRM", "FIN_TAX_PERIOD_SUMMARY", id);
		return result;
	}

	@Transactional
	public Map<String, Object> generateVoucherDraft(Long id, FinancialCloseModels.VersionedActionRequest request,
			CurrentUser operator) {
		requireRequest(request);
		String reason = FinancialCloseSupport.requiredChineseReason(request.reason(), ApiErrorCode.VALIDATION_ERROR);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_SUMMARY_GENERATE_VOUCHER|" + id
				+ "|" + request.version() + "|" + reason);
		Long existing = idempotentResult("FIN_TAX_SUMMARY_GENERATE_VOUCHER", "FIN_TAX_PERIOD_SUMMARY", id, key,
				requestFingerprint, operator);
		if (existing != null) {
			return summary(existing, operator);
		}
		SummaryRow row = lockSummary(id);
		requireMutable(row);
		requireNoVoucher(row);
		requireVersion(row.version(), request == null ? null : request.version());
		if (!sourceFingerprint(periodById(row.periodId())).equals(row.sourceFingerprint())) {
			throw new BusinessException(ApiErrorCode.FIN_TAX_SOURCE_CHANGED);
		}
		List<GeneralLedgerVoucherService.VoucherLineRequest> lines = voucherLines(row);
		if (lines.isEmpty()) {
			Map<String, Object> result = summary(id, operator);
			recordIdempotency("FIN_TAX_SUMMARY_GENERATE_VOUCHER", "FIN_TAX_PERIOD_SUMMARY", id,
					row.version(), key, requestFingerprint, "FIN_TAX_PERIOD_SUMMARY", id,
					((Number) result.get("version")).longValue(), operator);
			return result;
		}
		Period period = periodById(row.periodId());
		Map<String, Object> voucher = this.voucherService.createSystemDraft("TAX_SUMMARY", id,
				"TAX-" + period.periodCode() + "-" + id, row.sourceFingerprint(), row.version(), period.endDate(),
				period.periodCode() + " 税务基础汇总凭证", lines, request.idempotencyKey(), operator);
		this.jdbcTemplate.update("""
				update fin_tax_period_summary
				set voucher_id = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ((Number) voucher.get("id")).longValue(), operator.username(), OffsetDateTime.now(), id);
		Map<String, Object> result = summary(id, operator);
		recordIdempotency("FIN_TAX_SUMMARY_GENERATE_VOUCHER", "FIN_TAX_PERIOD_SUMMARY", id, row.version(),
				key, requestFingerprint, "FIN_TAX_PERIOD_SUMMARY", id,
				((Number) result.get("version")).longValue(), operator);
		this.auditService.success(operator, "FIN_TAX_SUMMARY_GENERATE_VOUCHER", "FIN_TAX_PERIOD_SUMMARY", id);
		return result;
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> summaries(int page, int pageSize, CurrentUser currentUser) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_tax_period_summary", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from fin_tax_period_summary
				order by period_code desc, id desc
				limit ? offset ?
				""", (rs, rowNum) -> summary(rs.getLong("id"), currentUser), safePageSize,
				FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> summary(Long id, CurrentUser currentUser) {
		SummaryView view = this.jdbcTemplate.query("""
				select id, period_id, period_code, tax_type, status, source_fingerprint, output_vat, input_vat,
				       transfer_out_vat, adjustment_amount, opening_credit_vat, vat_payable,
				       urban_maintenance_tax, education_surcharge_tax, local_education_surcharge_tax,
				       additional_tax_total, ending_credit_vat, income_adjustment_increase,
				       income_adjustment_decrease, loss_deduction, prepaid_income_tax, income_tax_estimated,
				       income_tax_payable, disclaimer, stale, current_flag, voucher_id, version
				from fin_tax_period_summary
				where id = ?
				""", (rs, rowNum) -> new SummaryView(rs.getLong("id"), rs.getLong("period_id"),
				rs.getString("period_code"), rs.getString("tax_type"), rs.getString("status"),
				rs.getString("source_fingerprint"), FinancialCloseSupport.amount(rs.getBigDecimal("output_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("input_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("transfer_out_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("adjustment_amount")),
				FinancialCloseSupport.amount(rs.getBigDecimal("opening_credit_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("vat_payable")),
				FinancialCloseSupport.amount(rs.getBigDecimal("urban_maintenance_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("local_education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("additional_tax_total")),
				FinancialCloseSupport.amount(rs.getBigDecimal("ending_credit_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_adjustment_increase")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_adjustment_decrease")),
				FinancialCloseSupport.amount(rs.getBigDecimal("loss_deduction")),
				FinancialCloseSupport.amount(rs.getBigDecimal("prepaid_income_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_tax_estimated")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_tax_payable")), rs.getString("disclaimer"),
				rs.getBoolean("stale"), rs.getBoolean("current_flag"), FinancialCloseSupport.nullableLong(rs,
						"voucher_id"),
				rs.getLong("version")), id).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
		return summary(view, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> payments(int page, int pageSize, CurrentUser currentUser) {
		int safePageSize = FinancialCloseSupport.listLimit(pageSize);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_tax_payment_record", Long.class);
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select id
				from fin_tax_payment_record
				order by payment_date desc, id desc
				limit ? offset ?
				""", (rs, rowNum) -> payment(rs.getLong("id"), currentUser), safePageSize,
				FinancialCloseSupport.offset(page, safePageSize));
		return PageResponse.of(items, page, safePageSize, total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> recordPayment(FinancialCloseModels.TaxPaymentRequest request, CurrentUser operator) {
		requireRequest(request);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_PAYMENT_RECORD|"
				+ paymentFingerprint(request));
		Long existingByKey = idempotentResult("FIN_TAX_PAYMENT_RECORD", "FIN_TAX_PERIOD_SUMMARY",
				request.summaryId(), key, requestFingerprint, operator);
		if (existingByKey != null) {
			return payment(existingByKey, operator);
		}
		SummaryRow row = lockSummary(request.summaryId());
		if (!"CONFIRMED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY);
		}
		BigDecimal amount = FinancialCloseSupport.amount(request.amount());
		if (!FinancialCloseSupport.positive(amount)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validatePaymentSource(request.voucherId(), request.paymentId());
		Long id = this.jdbcTemplate.queryForObject("""
				insert into fin_tax_payment_record (
					summary_id, tax_type, payment_date, amount, payment_method, reference_no, voucher_id,
					payment_id, bank_account_id, reason, status, created_by, updated_by
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECORDED', ?, ?)
				returning id
				""", Long.class, request.summaryId(), normalized(request.taxType(), row.taxType()),
				request.paymentDate() == null ? LocalDate.now() : request.paymentDate(), amount,
				FinancialCloseSupport.text(request.paymentMethod()), FinancialCloseSupport.text(request.referenceNo()),
				request.voucherId(), request.paymentId(), request.bankAccountId(),
				FinancialCloseSupport.requiredChineseReason(request.reason(), ApiErrorCode.VALIDATION_ERROR),
				operator.username(), operator.username());
		recordIdempotency("FIN_TAX_PAYMENT_RECORD", "FIN_TAX_PERIOD_SUMMARY", request.summaryId(), row.version(),
				key, requestFingerprint, "FIN_TAX_PAYMENT_RECORD", id, 0L, operator);
		this.auditService.success(operator, "FIN_TAX_PAYMENT_RECORD", "FIN_TAX_PAYMENT_RECORD", id);
		return payment(id, operator);
	}

	@Transactional
	public Map<String, Object> correctPayment(Long id, FinancialCloseModels.TaxPaymentCorrectionRequest request,
			CurrentUser operator) {
		requireRequest(request);
		BigDecimal amount = FinancialCloseSupport.amount(request.amount());
		if (amount.compareTo(ZERO) == 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String reason = FinancialCloseSupport.requiredChineseReason(request.reason(), ApiErrorCode.VALIDATION_ERROR);
		String key = idempotencyKey(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("FIN_TAX_PAYMENT_CORRECT|" + id + "|"
				+ FinancialCloseSupport.decimal(amount) + "|" + reason);
		Long existingByKey = idempotentResult("FIN_TAX_PAYMENT_CORRECT", "FIN_TAX_PAYMENT_RECORD", id, key,
				requestFingerprint, operator);
		if (existingByKey != null) {
			return payment(existingByKey, operator);
		}
		PaymentRow original = lockPayment(id);
		Long correctionId = this.jdbcTemplate.queryForObject("""
				insert into fin_tax_payment_record (
					summary_id, tax_type, payment_date, amount, reason, correction_of_id, status, created_by,
					updated_by
				)
				values (?, ?, ?, ?, ?, ?, 'CORRECTED', ?, ?)
				returning id
				""", Long.class, original.summaryId(), original.taxType(), LocalDate.now(), amount,
				reason, original.id(), operator.username(), operator.username());
		recordIdempotency("FIN_TAX_PAYMENT_CORRECT", "FIN_TAX_PAYMENT_RECORD", id, original.version(), key,
				requestFingerprint, "FIN_TAX_PAYMENT_RECORD", correctionId, 0L, operator);
		this.auditService.success(operator, "FIN_TAX_PAYMENT_CORRECT", "FIN_TAX_PAYMENT_RECORD", correctionId);
		return payment(correctionId, operator);
	}

	private Long currentProfileId() {
		return this.jdbcTemplate.query("""
				select id
				from fin_tax_profile
				where current_flag = true
				order by id desc
				limit 1
				""", (rs, rowNum) -> rs.getLong("id")).stream().findFirst().orElse(null);
	}

	private Map<String, Object> profile(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select id, taxpayer_type, credit_code, tax_authority, vat_periodicity, income_tax_rate,
				       urban_maintenance_rate, education_surcharge_rate, local_education_surcharge_rate,
				       income_adjustment_increase, income_adjustment_decrease, loss_deduction, prepaid_income_tax,
				       effective_from, current_flag, version
				from fin_tax_profile
				where id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("taxpayerType", rs.getString("taxpayer_type"));
			map.put("unifiedSocialCreditCodeMasked", FinancialCloseSupport.maskedCreditCode(
					rs.getString("credit_code"), currentUser));
			map.put("taxAuthority", rs.getString("tax_authority"));
			map.put("vatPeriodicity", rs.getString("vat_periodicity"));
			map.put("incomeTaxRate", rs.getBigDecimal("income_tax_rate").toPlainString());
			map.put("urbanMaintenanceRate", rs.getBigDecimal("urban_maintenance_rate").toPlainString());
			map.put("educationSurchargeRate", rs.getBigDecimal("education_surcharge_rate").toPlainString());
			map.put("localEducationSurchargeRate",
					rs.getBigDecimal("local_education_surcharge_rate").toPlainString());
			map.put("incomeAdjustmentIncrease", FinancialCloseSupport.visibleDecimal(
					rs.getBigDecimal("income_adjustment_increase"), currentUser));
			map.put("incomeAdjustmentDecrease", FinancialCloseSupport.visibleDecimal(
					rs.getBigDecimal("income_adjustment_decrease"), currentUser));
			map.put("lossDeduction", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("loss_deduction"),
					currentUser));
			map.put("prepaidIncomeTax", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("prepaid_income_tax"),
					currentUser));
			map.put("effectiveFrom", rs.getObject("effective_from", LocalDate.class));
			map.put("current", rs.getBoolean("current_flag"));
			FinancialCloseSupport.putVisibility(map, currentUser);
			map.put("version", rs.getLong("version"));
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private Map<String, Object> rateRule(Long id) {
		return this.jdbcTemplate.query("""
				select id, tax_type, rate_code, rate_value, effective_from, effective_to, status, version
				from fin_tax_rate_rule
				where id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("taxType", rs.getString("tax_type"));
			map.put("rateCode", rs.getString("rate_code"));
			map.put("rateValue", rs.getBigDecimal("rate_value").toPlainString());
			map.put("effectiveFrom", rs.getObject("effective_from", LocalDate.class));
			map.put("effectiveTo", rs.getObject("effective_to", LocalDate.class));
			map.put("status", rs.getString("status"));
			map.put("version", rs.getLong("version"));
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private Map<String, Object> invoiceType(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, name, direction, deductible, status, version
				from fin_tax_invoice_type
				where id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("code", rs.getString("code"));
			map.put("name", rs.getString("name"));
			map.put("direction", rs.getString("direction"));
			map.put("deductible", rs.getBoolean("deductible"));
			map.put("status", rs.getString("status"));
			map.put("version", rs.getLong("version"));
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private Map<String, Object> currentSummary(Long periodId, String taxType, CurrentUser currentUser) {
		List<SummaryView> rows = this.jdbcTemplate.query("""
				select id, period_id, period_code, tax_type, status, source_fingerprint, output_vat, input_vat,
				       transfer_out_vat, adjustment_amount, opening_credit_vat, vat_payable,
				       urban_maintenance_tax, education_surcharge_tax, local_education_surcharge_tax,
				       additional_tax_total, ending_credit_vat, income_adjustment_increase,
				       income_adjustment_decrease, loss_deduction, prepaid_income_tax, income_tax_estimated,
				       income_tax_payable, disclaimer, stale, current_flag, voucher_id, version
				from fin_tax_period_summary
				where period_id = ?
				and tax_type = ?
				and current_flag = true
				order by id desc
				""", (rs, rowNum) -> new SummaryView(rs.getLong("id"), rs.getLong("period_id"),
				rs.getString("period_code"), rs.getString("tax_type"), rs.getString("status"),
				rs.getString("source_fingerprint"), FinancialCloseSupport.amount(rs.getBigDecimal("output_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("input_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("transfer_out_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("adjustment_amount")),
				FinancialCloseSupport.amount(rs.getBigDecimal("opening_credit_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("vat_payable")),
				FinancialCloseSupport.amount(rs.getBigDecimal("urban_maintenance_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("local_education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("additional_tax_total")),
				FinancialCloseSupport.amount(rs.getBigDecimal("ending_credit_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_adjustment_increase")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_adjustment_decrease")),
				FinancialCloseSupport.amount(rs.getBigDecimal("loss_deduction")),
				FinancialCloseSupport.amount(rs.getBigDecimal("prepaid_income_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_tax_estimated")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_tax_payable")), rs.getString("disclaimer"),
				rs.getBoolean("stale"), rs.getBoolean("current_flag"), FinancialCloseSupport.nullableLong(rs,
						"voucher_id"),
				rs.getLong("version")), periodId, taxType);
		for (SummaryView row : rows) {
			if (!isStale(row)) {
				return summary(row, currentUser);
			}
		}
		return null;
	}

	private SummaryRow lockSummary(Long id) {
		return this.jdbcTemplate.query("""
				select id, period_id, tax_type, status, source_fingerprint, output_vat, input_vat,
				       adjustment_amount, vat_payable, urban_maintenance_tax, education_surcharge_tax,
				       local_education_surcharge_tax, additional_tax_total, income_tax_estimated,
				       income_tax_payable, voucher_id, version
				from fin_tax_period_summary
				where id = ?
				for update
				""", (rs, rowNum) -> new SummaryRow(rs.getLong("id"), rs.getLong("period_id"),
				rs.getString("tax_type"), rs.getString("status"), rs.getString("source_fingerprint"),
				FinancialCloseSupport.amount(rs.getBigDecimal("output_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("input_vat")),
				FinancialCloseSupport.amount(rs.getBigDecimal("adjustment_amount")),
				FinancialCloseSupport.amount(rs.getBigDecimal("vat_payable")),
				FinancialCloseSupport.amount(rs.getBigDecimal("urban_maintenance_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("local_education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("additional_tax_total")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_tax_estimated")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_tax_payable")),
				FinancialCloseSupport.nullableLong(rs, "voucher_id"), rs.getLong("version")), id).stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private PaymentRow lockPayment(Long id) {
		return this.jdbcTemplate.query("""
				select id, summary_id, tax_type, amount, status, version
				from fin_tax_payment_record
				where id = ?
				for update
				""", (rs, rowNum) -> new PaymentRow(rs.getLong("id"), rs.getLong("summary_id"),
				rs.getString("tax_type"), FinancialCloseSupport.amount(rs.getBigDecimal("amount")),
				rs.getString("status"), rs.getLong("version")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private Map<String, Object> payment(Long id, CurrentUser currentUser) {
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		return this.jdbcTemplate.query("""
				select p.id, p.summary_id, p.tax_type, p.payment_date, p.amount, p.payment_method, p.reference_no,
				       p.voucher_id, p.payment_id, p.bank_account_id, a.account_name, a.bank_name,
				       a.account_last4, a.account_masked, p.reason, p.correction_of_id, p.status, p.created_by,
				       p.created_at, p.version
				from fin_tax_payment_record p
				left join fin_bank_account a on a.id = p.bank_account_id
				where p.id = ?
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("summaryId", rs.getLong("summary_id"));
			map.put("taxType", rs.getString("tax_type"));
			map.put("paymentDate", rs.getObject("payment_date", LocalDate.class));
			map.put("amount", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("amount"), amountVisible));
			map.put("paymentMethod", rs.getString("payment_method"));
			map.put("referenceNo", sourceVisible ? rs.getString("reference_no") : null);
			map.put("voucherId", FinancialCloseSupport.nullableLong(rs, "voucher_id"));
			map.put("paymentId", FinancialCloseSupport.nullableLong(rs, "payment_id"));
			map.put("bankAccountId", FinancialCloseSupport.nullableLong(rs, "bank_account_id"));
			map.put("bankAccountName", rs.getString("account_name"));
			map.put("bankName", rs.getString("bank_name"));
			boolean sensitiveVisible = FinancialCloseSupport.bankSensitiveVisible(currentUser);
			map.put("accountLast4", sensitiveVisible ? rs.getString("account_last4") : null);
			map.put("accountMasked", rs.getString("account_masked") == null ? null
					: FinancialCloseSupport.visibleBankMask(rs.getString("account_masked"), currentUser));
			map.put("reason", rs.getString("reason"));
			map.put("correctionOfId", FinancialCloseSupport.nullableLong(rs, "correction_of_id"));
			map.put("status", rs.getString("status"));
			map.put("createdBy", rs.getString("created_by"));
			map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			FinancialCloseSupport.putVisibility(map, currentUser);
			map.put("version", rs.getLong("version"));
			return map;
		}, id).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
	}

	private Map<String, Object> summary(SummaryView view, CurrentUser currentUser) {
		boolean stale = isStale(view);
		boolean amountVisible = FinancialCloseSupport.amountVisible(currentUser);
		boolean sourceVisible = FinancialCloseSupport.sourceVisible(currentUser);
		Map<String, Object> map = FinancialCloseSupport.map();
		map.put("id", view.id());
		map.put("periodId", view.periodId());
		map.put("periodCode", view.periodCode());
		map.put("taxType", view.taxType());
		map.put("status", view.status());
		map.put("sourceFingerprint", sourceVisible ? view.sourceFingerprint() : null);
		map.put("outputVat", FinancialCloseSupport.visibleDecimal(view.outputVat(), amountVisible));
		map.put("inputVat", FinancialCloseSupport.visibleDecimal(view.inputVat(), amountVisible));
		map.put("transferOutVat", FinancialCloseSupport.visibleDecimal(view.transferOutVat(), amountVisible));
		map.put("adjustmentAmount", FinancialCloseSupport.visibleDecimal(view.adjustmentAmount(), amountVisible));
		map.put("openingCreditVat", FinancialCloseSupport.visibleDecimal(view.openingCreditVat(), amountVisible));
		map.put("vatPayable", FinancialCloseSupport.visibleDecimal(view.vatPayable(), amountVisible));
		map.put("urbanMaintenanceTax", FinancialCloseSupport.visibleDecimal(view.urbanMaintenanceTax(),
				amountVisible));
		map.put("educationSurchargeTax", FinancialCloseSupport.visibleDecimal(view.educationSurchargeTax(),
				amountVisible));
		map.put("localEducationSurchargeTax", FinancialCloseSupport.visibleDecimal(
				view.localEducationSurchargeTax(), amountVisible));
		map.put("additionalTaxTotal", FinancialCloseSupport.visibleDecimal(view.additionalTaxTotal(),
				amountVisible));
		map.put("endingCreditVat", FinancialCloseSupport.visibleDecimal(view.endingCreditVat(), amountVisible));
		map.put("incomeAdjustmentIncrease", FinancialCloseSupport.visibleDecimal(view.incomeAdjustmentIncrease(),
				amountVisible));
		map.put("incomeAdjustmentDecrease", FinancialCloseSupport.visibleDecimal(view.incomeAdjustmentDecrease(),
				amountVisible));
		map.put("lossDeduction", FinancialCloseSupport.visibleDecimal(view.lossDeduction(), amountVisible));
		map.put("prepaidIncomeTax", FinancialCloseSupport.visibleDecimal(view.prepaidIncomeTax(), amountVisible));
		map.put("incomeTaxEstimated", FinancialCloseSupport.visibleDecimal(view.incomeTaxEstimated(),
				amountVisible));
		map.put("incomeTaxPayable", FinancialCloseSupport.visibleDecimal(view.incomeTaxPayable(), amountVisible));
		map.put("disclaimer", view.disclaimer());
		map.put("stale", stale);
		map.put("current", view.currentFlag() && !stale);
		map.put("voucherId", view.voucherId());
		FinancialCloseSupport.putVisibility(map, currentUser);
		map.put("version", view.version());
		map.put("allowedActions", summaryActions(view, stale, currentUser));
		map.put("actionDisabledReasons", summaryDisabledReasons(view, stale, currentUser));
		map.put("lines", summaryLines(view.id(), amountVisible, sourceVisible));
		return map;
	}

	private List<String> summaryActions(SummaryView view, boolean stale, CurrentUser currentUser) {
		boolean current = view.currentFlag() && !stale;
		boolean hasVoucher = view.voucherId() != null;
		List<String> actions = new ArrayList<>();
		if (current && !hasVoucher && List.of("DRAFT", "CALCULATED").contains(view.status())
				&& FinancialCloseSupport.hasPermission(currentUser, "financial-close:tax-summary:calculate")) {
			actions.add("CALCULATE");
			actions.add("ADJUST");
		}
		if (current && "CALCULATED".equals(view.status())
				&& FinancialCloseSupport.hasPermission(currentUser, "financial-close:tax-summary:confirm")) {
			actions.add("CONFIRM");
		}
		if (current && !hasVoucher && "CALCULATED".equals(view.status())
				&& FinancialCloseSupport.hasPermission(currentUser,
						"financial-close:tax-summary:generate-voucher")) {
			actions.add("GENERATE_VOUCHER");
		}
		return actions;
	}

	private Map<String, String> summaryDisabledReasons(SummaryView view, boolean stale, CurrentUser currentUser) {
		Map<String, String> reasons = new java.util.LinkedHashMap<>();
		putTaxSummaryDisabledReason(reasons, "CALCULATE",
				taxSummaryDisabledReason(view, stale, currentUser, "financial-close:tax-summary:calculate",
						true, false));
		putTaxSummaryDisabledReason(reasons, "ADJUST",
				taxSummaryDisabledReason(view, stale, currentUser, "financial-close:tax-summary:calculate",
						true, false));
		putTaxSummaryDisabledReason(reasons, "CONFIRM",
				taxSummaryDisabledReason(view, stale, currentUser, "financial-close:tax-summary:confirm",
						false, true));
		putTaxSummaryDisabledReason(reasons, "GENERATE_VOUCHER",
				taxSummaryDisabledReason(view, stale, currentUser,
						"financial-close:tax-summary:generate-voucher", true, true));
		return reasons;
	}

	private void putTaxSummaryDisabledReason(Map<String, String> reasons, String action, String reason) {
		if (reason != null) {
			reasons.put(action, reason);
		}
	}

	private String taxSummaryDisabledReason(SummaryView view, boolean stale, CurrentUser currentUser,
			String permission, boolean mutableRequired, boolean calculatedRequired) {
		if (!FinancialCloseSupport.hasPermission(currentUser, permission)) {
			return "无权执行税务汇总动作";
		}
		if (stale) {
			return "来源已变化，请创建新版本";
		}
		if (!view.currentFlag()) {
			return "已存在更新版本";
		}
		if (mutableRequired && view.voucherId() != null) {
			return "已有凭证草稿，禁止再次修改或生成";
		}
		if (mutableRequired && "CONFIRMED".equals(view.status())) {
			return "已确认税务汇总不可修改";
		}
		if (calculatedRequired && !"CALCULATED".equals(view.status())) {
			return "仅已计算税务汇总可执行";
		}
		if (!calculatedRequired && !List.of("DRAFT", "CALCULATED").contains(view.status())) {
			return "当前税务汇总状态不可执行";
		}
		return null;
	}

	private boolean isStale(SummaryView view) {
		return "CONFIRMED".equals(view.status())
				&& !sourceFingerprint(periodById(view.periodId())).equals(view.sourceFingerprint());
	}

	private void updateSummaryAmounts(Long id, String status, String fingerprint, TaxAmounts amounts,
			CurrentUser operator) {
		this.jdbcTemplate.update("""
				update fin_tax_period_summary
				set status = ?, source_fingerprint = ?, output_vat = ?, input_vat = ?,
				    transfer_out_vat = 0, adjustment_amount = ?, opening_credit_vat = 0, vat_payable = ?,
				    urban_maintenance_tax = ?, education_surcharge_tax = ?, local_education_surcharge_tax = ?,
				    additional_tax_total = ?, ending_credit_vat = ?, income_adjustment_increase = ?,
				    income_adjustment_decrease = ?, loss_deduction = ?, prepaid_income_tax = ?,
				    income_tax_estimated = ?, income_tax_payable = ?, disclaimer = ?, stale = false,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", status, fingerprint, amounts.outputVat(), amounts.inputVat(), amounts.adjustmentAmount(),
				amounts.vatPayable(), amounts.urbanMaintenanceTax(), amounts.educationSurchargeTax(),
				amounts.localEducationSurchargeTax(), amounts.additionalTaxTotal(), amounts.endingCreditVat(),
				amounts.incomeAdjustmentIncrease(), amounts.incomeAdjustmentDecrease(), amounts.lossDeduction(),
				amounts.prepaidIncomeTax(), amounts.incomeTaxEstimated(), amounts.incomeTaxPayable(),
				FinancialCloseSupport.TAX_DISCLAIMER, operator.username(), OffsetDateTime.now(), id);
	}

	private List<GeneralLedgerVoucherService.VoucherLineRequest> voucherLines(SummaryRow row) {
		List<GeneralLedgerVoucherService.VoucherLineRequest> lines = new ArrayList<>();
		int lineNo = 1;
		if (row.vatPayable().compareTo(ZERO) > 0) {
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, accountId("2221.02"),
					"增值税转出未交", row.vatPayable(), ZERO, List.of()));
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, accountId("2221.03"),
					"增值税转出未交", ZERO, row.vatPayable(), List.of()));
		}
		if (row.urbanMaintenanceTax().compareTo(ZERO) > 0) {
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, accountId("6403"),
					"城市维护建设税计提", row.urbanMaintenanceTax(), ZERO, List.of()));
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, accountId("2221.04"),
					"城市维护建设税计提", ZERO, row.urbanMaintenanceTax(), List.of()));
		}
		BigDecimal educationSurcharge = FinancialCloseSupport.amount(row.educationSurchargeTax()
			.add(row.localEducationSurchargeTax()));
		if (educationSurcharge.compareTo(ZERO) > 0) {
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, accountId("6403"),
					"教育费附加计提", educationSurcharge, ZERO, List.of()));
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, accountId("2221.05"),
					"教育费附加计提", ZERO, educationSurcharge, List.of()));
		}
		if (row.incomeTaxPayable().compareTo(ZERO) > 0) {
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo++, accountId("6801"),
					"企业所得税估算计提", row.incomeTaxPayable(), ZERO, List.of()));
			lines.add(new GeneralLedgerVoucherService.VoucherLineRequest(lineNo, accountId("2221.06"),
					"企业所得税估算计提", ZERO, row.incomeTaxPayable(), List.of()));
		}
		return lines;
	}

	private Period periodByCode(String periodCode) {
		return this.jdbcTemplate.query("""
				select id, period_code, start_date, end_date
				from gl_accounting_period
				where period_code = ?
				""", (rs, rowNum) -> new Period(rs.getLong("id"), rs.getString("period_code"),
				rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)), periodCode)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	private Period periodById(Long periodId) {
		return this.jdbcTemplate.query("""
				select id, period_code, start_date, end_date
				from gl_accounting_period
				where id = ?
				""", (rs, rowNum) -> new Period(rs.getLong("id"), rs.getString("period_code"),
				rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)), periodId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	private TaxAmounts amounts(Period period, BigDecimal adjustment) {
		BigDecimal outputVat = queryAmount("""
				select coalesce(sum(tax_amount), 0)
				from fin_sales_invoice
				where status = 'CONFIRMED'
				and invoice_date between ? and ?
				""", period.startDate(), period.endDate());
		BigDecimal inputVat = queryAmount("""
				select coalesce(sum(tax_amount), 0)
				from fin_purchase_invoice
				where status = 'CONFIRMED'
				and invoice_date between ? and ?
				""", period.startDate(), period.endDate()).add(queryAmount("""
				select coalesce(sum(tax_amount), 0)
				from fin_expense
				where status = 'CONFIRMED'
				and expense_date between ? and ?
				""", period.startDate(), period.endDate()));
		BigDecimal net = FinancialCloseSupport.amount(outputVat.subtract(inputVat).add(adjustment));
		BigDecimal payable = net.compareTo(ZERO) > 0 ? net : ZERO;
		BigDecimal credit = net.compareTo(ZERO) < 0 ? net.abs() : ZERO;
		BigDecimal urban = FinancialCloseSupport.amount(payable.multiply(profileRate("urban_maintenance_rate")));
		BigDecimal education = FinancialCloseSupport.amount(payable.multiply(profileRate("education_surcharge_rate")));
		BigDecimal localEducation = FinancialCloseSupport.amount(payable.multiply(profileRate(
				"local_education_surcharge_rate")));
		BigDecimal additional = FinancialCloseSupport.amount(urban.add(education).add(localEducation));
		BigDecimal incomeIncrease = profileAmount("income_adjustment_increase");
		BigDecimal incomeDecrease = profileAmount("income_adjustment_decrease");
		BigDecimal lossDeduction = profileAmount("loss_deduction");
		BigDecimal prepaidIncomeTax = profileAmount("prepaid_income_tax");
		BigDecimal profit = queryAmount("""
				select coalesce(sum(e.credit_amount - e.debit_amount), 0)
				from gl_ledger_entry e
				join gl_account a on a.id = e.account_id
				where e.period_id = ?
				and a.category = 'PROFIT_LOSS'
				and a.code <> '6801'
				""", period.id());
		BigDecimal taxableIncome = FinancialCloseSupport.amount(profit.add(incomeIncrease)
			.subtract(incomeDecrease)
			.subtract(lossDeduction));
		if (taxableIncome.compareTo(ZERO) < 0) {
			taxableIncome = ZERO;
		}
		BigDecimal estimated = FinancialCloseSupport.amount(taxableIncome.multiply(profileRate("income_tax_rate")));
		BigDecimal income = FinancialCloseSupport.amount(estimated.subtract(prepaidIncomeTax));
		if (income.compareTo(ZERO) < 0) {
			income = ZERO;
		}
		return new TaxAmounts(FinancialCloseSupport.amount(outputVat), FinancialCloseSupport.amount(inputVat),
				FinancialCloseSupport.amount(adjustment), FinancialCloseSupport.amount(payable),
				FinancialCloseSupport.amount(urban), education, localEducation, additional,
				FinancialCloseSupport.amount(credit), incomeIncrease, incomeDecrease, lossDeduction,
				prepaidIncomeTax, income, income);
	}

	private BigDecimal totalAdjustments(Long summaryId) {
		return queryAmount("""
				select coalesce(sum(case adjustment_type
					when 'OUTPUT_INCREASE' then amount
					when 'INPUT_DECREASE' then amount
					when 'OUTPUT_DECREASE' then -amount
					when 'INPUT_INCREASE' then -amount
					else 0
				end), 0)
				from fin_tax_adjustment
				where summary_id = ?
				""", summaryId);
	}

	private String sourceFingerprint(Period period) {
		String source = this.jdbcTemplate.queryForObject("""
				select coalesce(string_agg(source_key, ',' order by source_key), '')
				from (
					select 'S|' || id || '|' || version || '|' || tax_amount as source_key
					from fin_sales_invoice
					where status = 'CONFIRMED'
					and invoice_date between ? and ?
					union all
					select 'P|' || id || '|' || version || '|' || tax_amount as source_key
					from fin_purchase_invoice
					where status = 'CONFIRMED'
					and invoice_date between ? and ?
					union all
					select 'E|' || id || '|' || version || '|' || tax_amount as source_key
					from fin_expense
					where status = 'CONFIRMED'
					and expense_date between ? and ?
					union all
					select 'G|' || e.id || '|' || e.voucher_date || '|' || e.debit_amount || '|' || e.credit_amount as source_key
					from gl_ledger_entry e
					join gl_account a on a.id = e.account_id
					where e.period_id = ?
					and a.category = 'PROFIT_LOSS'
					and a.code <> '6801'
				) source
				""", String.class, period.startDate(), period.endDate(), period.startDate(), period.endDate(),
				period.startDate(), period.endDate(), period.id());
		return FinancialCloseSupport.sha256("TAX|" + period.id() + "|" + period.periodCode() + "|" + source);
	}

	private void insertTaxLines(Long summaryId, Period period) {
		this.jdbcTemplate.update("""
				insert into fin_tax_summary_line (
					summary_id, source_type, source_id, source_no, direction, amount, tax_amount, line_json
				)
				select ?, 'SALES_INVOICE', id, invoice_no, 'OUTPUT', tax_excluded_amount, tax_amount,
				       jsonb_build_object('invoiceDate', invoice_date)
				from fin_sales_invoice
				where status = 'CONFIRMED'
				and invoice_date between ? and ?
				""", summaryId, period.startDate(), period.endDate());
		this.jdbcTemplate.update("""
				insert into fin_tax_summary_line (
					summary_id, source_type, source_id, source_no, direction, amount, tax_amount, line_json
				)
				select ?, 'PURCHASE_INVOICE', id, invoice_no, 'INPUT', tax_excluded_amount, tax_amount,
				       jsonb_build_object('invoiceDate', invoice_date)
				from fin_purchase_invoice
				where status = 'CONFIRMED'
				and invoice_date between ? and ?
				""", summaryId, period.startDate(), period.endDate());
	}

	private List<Map<String, Object>> summaryLines(Long summaryId, boolean amountVisible, boolean sourceVisible) {
		return this.jdbcTemplate.query("""
				select id, source_type, source_id, source_no, direction, amount, tax_amount
				from fin_tax_summary_line
				where summary_id = ?
				order by id
				""", (rs, rowNum) -> {
			Map<String, Object> map = FinancialCloseSupport.map();
			map.put("id", rs.getLong("id"));
			map.put("sourceType", sourceVisible ? rs.getString("source_type") : null);
			map.put("sourceId", sourceVisible ? rs.getLong("source_id") : null);
			map.put("sourceNo", sourceVisible ? rs.getString("source_no") : null);
			map.put("direction", rs.getString("direction"));
			map.put("amount", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("amount"), amountVisible));
			map.put("taxAmount", FinancialCloseSupport.visibleDecimal(rs.getBigDecimal("tax_amount"),
					amountVisible));
			return map;
		}, summaryId);
	}

	private void validatePaymentSource(Long voucherId, Long paymentId) {
		if (voucherId == null && paymentId == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (voucherId != null) {
			String status = this.jdbcTemplate.query("""
					select status
					from gl_voucher
					where id = ?
					""", (rs, rowNum) -> rs.getString("status"), voucherId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
			if (!"POSTED".equals(status)) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY);
			}
		}
		if (paymentId != null) {
			String status = this.jdbcTemplate.query("""
					select status
					from fin_payment
					where id = ?
					""", (rs, rowNum) -> rs.getString("status"), paymentId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT));
			if (!"POSTED".equals(status)) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY);
			}
		}
	}

	private BigDecimal profileRate(String columnName) {
		Long id = currentProfileId();
		if (id == null) {
			return BigDecimal.ZERO;
		}
		return this.jdbcTemplate.queryForObject("select " + columnName + " from fin_tax_profile where id = ?",
				BigDecimal.class, id);
	}

	private BigDecimal profileAmount(String columnName) {
		Long id = currentProfileId();
		if (id == null) {
			return ZERO;
		}
		return FinancialCloseSupport.amount(this.jdbcTemplate.queryForObject("select " + columnName
				+ " from fin_tax_profile where id = ?", BigDecimal.class, id));
	}

	private BigDecimal queryAmount(String sql, Object... args) {
		return FinancialCloseSupport.amount(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, args));
	}

	private String normalized(String value, String fallback) {
		String text = FinancialCloseSupport.text(value);
		return text == null || text.isBlank() ? fallback : text.toUpperCase();
	}

	private BigDecimal rate(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private BigDecimal amount(BigDecimal value) {
		return FinancialCloseSupport.amount(value);
	}

	private Long accountId(String code) {
		return this.jdbcTemplate.queryForObject("select id from gl_account where code = ?", Long.class, code);
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private void requireMutable(SummaryRow row) {
		if ("CONFIRMED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private void requireNoVoucher(SummaryRow row) {
		if (row.voucherId() != null) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	private void requireRequest(Object request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String idempotencyKey(String idempotencyKey) {
		String key = FinancialCloseSupport.requiredText(idempotencyKey, ApiErrorCode.VALIDATION_ERROR);
		if (key.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return key;
	}

	private Long idempotentResult(String action, String resourceType, Long resourceId, String key,
			String requestFingerprint, CurrentUser operator) {
		return this.jdbcTemplate.query("""
				select request_fingerprint, result_resource_id
				from fin_close_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and coalesce(resource_id, 0) = coalesce(?, 0)
				and idempotency_key = ?
				""", (rs, rowNum) -> {
			if (!requestFingerprint.equals(rs.getString("request_fingerprint"))) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
			}
			return rs.getLong("result_resource_id");
		}, operator.id(), action, resourceType, resourceId, key).stream().findFirst().orElse(null);
	}

	private void recordIdempotency(String action, String resourceType, Long resourceId, Long resourceVersion,
			String key, String requestFingerprint, String resultResourceType, Long resultResourceId,
			Long resultVersion, CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into fin_close_action_idempotency (
						operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
						idempotency_key, request_fingerprint, result_resource_type, result_resource_id,
						result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), operator.username(), action, resourceType, resourceId, resourceVersion,
					key, requestFingerprint, resultResourceType, resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			idempotentResult(action, resourceType, resourceId, key, requestFingerprint, operator);
		}
	}

	private String profileFingerprint(FinancialCloseModels.TaxProfileRequest request) {
		LocalDate effectiveFrom = request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom();
		return normalized(request.taxpayerType(), "GENERAL") + "|" + FinancialCloseSupport.requiredText(
				request.creditCode(), ApiErrorCode.VALIDATION_ERROR) + "|" + FinancialCloseSupport.text(
						request.taxAuthority()) + "|" + normalized(request.vatPeriodicity(), "MONTHLY") + "|"
				+ decimalKey(rate(request.incomeTaxRate())) + "|" + decimalKey(rate(request.urbanMaintenanceRate()))
				+ "|" + decimalKey(rate(request.educationSurchargeRate())) + "|"
				+ decimalKey(rate(request.localEducationSurchargeRate())) + "|"
				+ FinancialCloseSupport.decimal(amount(request.incomeAdjustmentIncrease())) + "|"
				+ FinancialCloseSupport.decimal(amount(request.incomeAdjustmentDecrease())) + "|"
				+ FinancialCloseSupport.decimal(amount(request.lossDeduction())) + "|"
				+ FinancialCloseSupport.decimal(amount(request.prepaidIncomeTax())) + "|" + effectiveFrom + "|"
				+ request.version();
	}

	private String paymentFingerprint(FinancialCloseModels.TaxPaymentRequest request) {
		return request.summaryId() + "|" + normalized(request.taxType(), "") + "|" + request.paymentDate() + "|"
				+ FinancialCloseSupport.decimal(FinancialCloseSupport.amount(request.amount())) + "|"
				+ FinancialCloseSupport.text(request.paymentMethod()) + "|"
				+ FinancialCloseSupport.text(request.referenceNo()) + "|" + request.voucherId() + "|"
				+ request.paymentId() + "|" + request.bankAccountId() + "|"
				+ FinancialCloseSupport.requiredChineseReason(request.reason(), ApiErrorCode.VALIDATION_ERROR);
	}

	private String decimalKey(BigDecimal value) {
		return value == null ? "0" : value.stripTrailingZeros().toPlainString();
	}

	private record Period(Long id, String periodCode, LocalDate startDate, LocalDate endDate) {
	}

	private record SummaryRow(Long id, Long periodId, String taxType, String status, String sourceFingerprint,
			BigDecimal outputVat, BigDecimal inputVat, BigDecimal adjustmentAmount, BigDecimal vatPayable,
			BigDecimal urbanMaintenanceTax, BigDecimal educationSurchargeTax, BigDecimal localEducationSurchargeTax,
			BigDecimal additionalTaxTotal, BigDecimal incomeTaxEstimated, BigDecimal incomeTaxPayable,
			Long voucherId, Long version) {
	}

	private record SummaryView(Long id, Long periodId, String periodCode, String taxType, String status,
			String sourceFingerprint, BigDecimal outputVat, BigDecimal inputVat, BigDecimal transferOutVat,
			BigDecimal adjustmentAmount, BigDecimal openingCreditVat, BigDecimal vatPayable,
			BigDecimal urbanMaintenanceTax, BigDecimal educationSurchargeTax,
			BigDecimal localEducationSurchargeTax, BigDecimal additionalTaxTotal, BigDecimal endingCreditVat,
			BigDecimal incomeAdjustmentIncrease, BigDecimal incomeAdjustmentDecrease, BigDecimal lossDeduction,
			BigDecimal prepaidIncomeTax, BigDecimal incomeTaxEstimated, BigDecimal incomeTaxPayable,
			String disclaimer, boolean stale, boolean currentFlag, Long voucherId, Long version) {
	}

	private record TaxAmounts(BigDecimal outputVat, BigDecimal inputVat, BigDecimal adjustmentAmount,
			BigDecimal vatPayable, BigDecimal urbanMaintenanceTax, BigDecimal educationSurchargeTax,
			BigDecimal localEducationSurchargeTax, BigDecimal additionalTaxTotal, BigDecimal endingCreditVat,
			BigDecimal incomeAdjustmentIncrease, BigDecimal incomeAdjustmentDecrease, BigDecimal lossDeduction,
			BigDecimal prepaidIncomeTax, BigDecimal incomeTaxEstimated, BigDecimal incomeTaxPayable) {
	}

	private record PaymentRow(Long id, Long summaryId, String taxType, BigDecimal amount, String status,
			Long version) {
	}

}
