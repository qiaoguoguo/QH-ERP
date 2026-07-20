package com.qherp.api.system.financialclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
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
public class FinancialCloseCheckService {

	private static final String TARGET_PERIOD = "FIN_CLOSE_PERIOD";

	private final JdbcTemplate jdbcTemplate;

	private final FinancialCloseQueryService queryService;

	private final FinancialCloseAuditService auditService;

	public FinancialCloseCheckService(JdbcTemplate jdbcTemplate, FinancialCloseQueryService queryService,
			FinancialCloseAuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.queryService = queryService;
		this.auditService = auditService;
	}

	@Transactional
	public Map<String, Object> runCheck(Long periodId, FinancialCloseModels.CheckRequest request,
			CurrentUser operator) {
		PeriodRow period = lockPeriod(periodId);
		String key = request == null ? null : FinancialCloseSupport.text(request.idempotencyKey());
		String requestFingerprint = FinancialCloseSupport.sha256("CHECK|" + periodId + "|" + period.version());
		Long existing = idempotentResult("CHECK", TARGET_PERIOD, periodId, key, requestFingerprint, operator);
		if (existing != null) {
			return this.queryService.checkRun(existing, operator);
		}
		if (!"OPEN".equals(period.status())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_PERIOD_CLOSED);
		}
		this.jdbcTemplate.update("""
				update fin_close_check_run
				set status = 'STALE'
				where period_id = ?
				and status = 'READY'
				""", periodId);
		BusinessCloseRun businessCloseRun = businessCloseRun(period.periodCode());
		List<CheckItem> items = checkItems(period, businessCloseRun);
		int blockingCount = (int) items.stream().filter((item) -> !item.passed()).count();
		boolean ready = blockingCount == 0;
		String sourceFingerprint = sourceFingerprint(period, businessCloseRun);
		Long checkRunId = this.jdbcTemplate.queryForObject("""
				insert into fin_close_check_run (
					ledger_id, period_id, status, close_version, source_fingerprint, blocking_count, warning_count,
					created_by, created_at, completed_at
				)
				values (?, ?, ?, 0, ?, ?, 0, ?, ?, ?)
				returning id
				""", Long.class, period.ledgerId(), period.id(), ready ? "READY" : "BLOCKED", sourceFingerprint,
				blockingCount, operator.username(), OffsetDateTime.now(), OffsetDateTime.now());
		for (CheckItem item : items) {
			insertCheckItem(checkRunId, item);
		}
		recordAction("CHECK", TARGET_PERIOD, periodId, period.version(), key, requestFingerprint, "FIN_CLOSE_CHECK_RUN",
				checkRunId, 0L, operator);
		this.auditService.success(operator, "FIN_CLOSE_CHECK", "FIN_CLOSE_CHECK_RUN", checkRunId);
		return this.queryService.checkRun(checkRunId, operator);
	}

	@Transactional(readOnly = true)
	public BusinessCloseRun businessCloseRun(String periodCode) {
		return this.jdbcTemplate.query("""
				select r.id, r.revision_no, r.source_fingerprint, r.version
				from biz_business_period p
				join biz_period_close_run r on r.period_id = p.id
				where p.period_code = ?
				and p.status = 'LOCKED'
				and r.status = 'CLOSED'
				order by r.revision_no desc, r.id desc
				limit 1
				""", (rs, rowNum) -> new BusinessCloseRun(rs.getLong("id"), rs.getInt("revision_no"),
				rs.getString("source_fingerprint"), rs.getLong("version")), periodCode).stream().findFirst()
			.orElse(null);
	}

	@Transactional(readOnly = true)
	public String currentSourceFingerprint(Long periodId) {
		PeriodRow period = period(periodId);
		return sourceFingerprint(period, businessCloseRun(period.periodCode()));
	}

	@Transactional(readOnly = true)
	public void assertReadyForClose(Long periodId, String expectedSourceFingerprint) {
		PeriodRow period = period(periodId);
		BusinessCloseRun businessCloseRun = businessCloseRun(period.periodCode());
		List<CheckItem> items = checkItems(period, businessCloseRun);
		if (items.stream().anyMatch((item) -> !item.passed())) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_NOT_READY);
		}
		String currentFingerprint = sourceFingerprint(period, businessCloseRun);
		if (!currentFingerprint.equals(expectedSourceFingerprint)) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_STALE);
		}
	}

	@Transactional(readOnly = true)
	public PeriodRow period(Long periodId) {
		return this.jdbcTemplate.query("""
				select p.id, p.ledger_id, p.period_code, p.start_date, p.end_date, p.status, p.version
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				where l.code = 'MAIN'
				and p.id = ?
				""", (rs, rowNum) -> new PeriodRow(rs.getLong("id"), rs.getLong("ledger_id"),
				rs.getString("period_code"), rs.getObject("start_date", LocalDate.class),
				rs.getObject("end_date", LocalDate.class), rs.getString("status"), rs.getLong("version")), periodId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	private PeriodRow lockPeriod(Long periodId) {
		return this.jdbcTemplate.query("""
				select p.id, p.ledger_id, p.period_code, p.start_date, p.end_date, p.status, p.version
				from gl_accounting_period p
				join gl_ledger l on l.id = p.ledger_id
				where l.code = 'MAIN'
				and p.id = ?
				for update of p
				""", (rs, rowNum) -> new PeriodRow(rs.getLong("id"), rs.getLong("ledger_id"),
				rs.getString("period_code"), rs.getObject("start_date", LocalDate.class),
				rs.getObject("end_date", LocalDate.class), rs.getString("status"), rs.getLong("version")), periodId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.GL_PERIOD_NOT_FOUND));
	}

	private CheckItem businessPeriodItem(PeriodRow period, BusinessCloseRun businessCloseRun) {
		if (businessCloseRun == null) {
			return new CheckItem("BUSINESS_PERIOD_CLOSED", false, "MISSING", "CLOSED",
					"030 业务月结必须先完成，财务结账只读取该结果。", "BIZ_PERIOD_CLOSE_RUN", null,
					period.periodCode(), true);
		}
		return new CheckItem("BUSINESS_PERIOD_CLOSED", true, "CLOSED", "CLOSED", "030 业务月结已完成。",
				"BIZ_PERIOD_CLOSE_RUN", businessCloseRun.id(), period.periodCode(), false);
	}

	private List<CheckItem> checkItems(PeriodRow period, BusinessCloseRun businessCloseRun) {
		CheckItem bank = bankReconciliationItem(period);
		CheckItem tax = taxSummaryItem(period);
		CheckItem profitLoss = profitLossTransferItem(period);
		return List.of(previousPeriodItem(period), businessPeriodItem(period, businessCloseRun),
				noIncompleteVouchersItem(period), trialBalanceItem(period), bank, tax, taxVoucherPostedItem(period),
				profitLoss, noSourceChangesItem(period, businessCloseRun, bank, tax, profitLoss));
	}

	private CheckItem previousPeriodItem(PeriodRow period) {
		PreviousPeriodRow previous = previousPeriod(period);
		if (previous == null) {
			return new CheckItem("PREVIOUS_PERIOD_CLOSED", true, "FIRST_PERIOD", "CLOSED_OR_FIRST_PERIOD",
					"当前期间为启用首月，无需检查上一会计期间。", "GL_ACCOUNTING_PERIOD", period.id(),
					period.periodCode(), false);
		}
		boolean passed = "CLOSED".equals(previous.status());
		return new CheckItem("PREVIOUS_PERIOD_CLOSED", passed, previous.periodCode() + ":" + previous.status(),
				"CLOSED", passed ? "紧邻上月会计期间已关闭。" : "紧邻上月会计期间未关闭，不能执行本期财务结账。",
				"GL_ACCOUNTING_PERIOD", previous.id(), previous.periodCode(), true);
	}

	private CheckItem noIncompleteVouchersItem(PeriodRow period) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher
				where accounting_period_id = ?
				and status in ('DRAFT', 'SUBMITTED')
				""", Long.class, period.id());
		Long sourceDrafts = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_voucher_draft
				where business_date between ? and ?
				and status in ('READY', 'CONVERTING')
				""", Long.class, period.startDate(), period.endDate());
		long total = (count == null ? 0 : count) + (sourceDrafts == null ? 0 : sourceDrafts);
		return new CheckItem("NO_INCOMPLETE_VOUCHERS", total == 0, String.valueOf(total), "0",
				total == 0 ? "当期不存在未完成凭证、冲销或来源转换草稿。" : "当期存在未完成凭证、冲销或来源转换草稿。",
				"GL_VOUCHER", null, period.periodCode(), true);
	}

	private CheckItem trialBalanceItem(PeriodRow period) {
		BigDecimal debit = queryAmount("""
				select coalesce(sum(debit_amount), 0)
				from gl_ledger_entry
				where period_id = ?
				""", period.id());
		BigDecimal credit = queryAmount("""
				select coalesce(sum(credit_amount), 0)
				from gl_ledger_entry
				where period_id = ?
				""", period.id());
		boolean passed = debit.compareTo(credit) == 0;
		return new CheckItem("TRIAL_BALANCE_BALANCED", passed,
				"借方=" + FinancialCloseSupport.decimal(debit) + ";贷方=" + FinancialCloseSupport.decimal(credit),
				"借贷相等", passed ? "当期试算平衡。" : "当期试算不平衡，不能关闭财务期间。",
				"GL_LEDGER_ENTRY", null, period.periodCode(), true);
	}

	private CheckItem bankReconciliationItem(PeriodRow period) {
		List<BankAccountCheckRow> accounts = bankAccounts(period);
		if (accounts.isEmpty()) {
			return new CheckItem("BANK_RECONCILIATIONS_CONFIRMED", false, "NO_ENABLED_ACCOUNT",
					"CONFIRMED_AND_BALANCED", "至少需要一个当期启用银行账户，并完成当前版本银行对账确认。", "FIN_BANK_ACCOUNT",
					null, period.periodCode(), true);
		}
		List<String> failures = new ArrayList<>();
		Long sourceId = null;
		int passed = 0;
		for (BankAccountCheckRow account : accounts) {
			BankRunCheckRow run = latestBankRun(period.id(), account.id());
			if (run == null) {
				failures.add(account.id() + ":MISSING");
				continue;
			}
			sourceId = sourceId == null ? run.id() : sourceId;
			String currentFingerprint = bankSourceFingerprint(period.id(), account.id());
			if ("CONFIRMED".equals(run.status()) && run.differenceAmount().compareTo(BigDecimal.ZERO) == 0
					&& currentFingerprint.equals(run.sourceFingerprint())) {
				passed++;
			}
			else {
				failures.add(account.id() + ":" + run.status());
			}
		}
		boolean allPassed = passed == accounts.size();
		return new CheckItem("BANK_RECONCILIATIONS_CONFIRMED", allPassed, allPassed ? "CONFIRMED" : failures.toString(),
				"CONFIRMED_AND_BALANCED", allPassed ? "当期启用银行账户均已完成当前版本零差额对账确认。"
						: "存在未确认、未平衡或来源已变化的银行对账。",
				"FIN_BANK_RECONCILIATION_RUN", sourceId, period.periodCode(), true);
	}

	private CheckItem taxSummaryItem(PeriodRow period) {
		Long profileId = currentTaxProfileId();
		if (profileId == null) {
			return new CheckItem("TAX_SUMMARIES_CONFIRMED", false, "NO_PROFILE", "CONFIRMED_CURRENT",
					"税务档案未配置，不能确认当期税务汇总。", "FIN_TAX_PROFILE", null, period.periodCode(), true);
		}
		TaxSummaryCheckRow summary = latestTaxSummary(period.id(), "VAT");
		if (summary == null) {
			return new CheckItem("TAX_SUMMARIES_CONFIRMED", false, "MISSING", "CONFIRMED_CURRENT",
					"当期增值税基础汇总必须确认且未失效。", "FIN_TAX_PERIOD_SUMMARY", null, period.periodCode(), true);
		}
		boolean current = summary.currentFlag()
				&& taxSourceFingerprint(period).equals(summary.sourceFingerprint());
		boolean passed = "CONFIRMED".equals(summary.status()) && current;
		return new CheckItem("TAX_SUMMARIES_CONFIRMED", passed,
				passed ? "CONFIRMED_CURRENT" : summary.status() + (current ? "" : "_STALE"), "CONFIRMED_CURRENT",
				passed ? "当期税务汇总已确认且来源未变化。" : "当期税务汇总未确认或来源已变化。",
				"FIN_TAX_PERIOD_SUMMARY", summary.id(), period.periodCode(), true);
	}

	private CheckItem taxVoucherPostedItem(PeriodRow period) {
		TaxVoucherCheckRow summary = latestTaxVoucherSummary(period.id(), "VAT");
		if (summary == null) {
			return new CheckItem("TAX_VOUCHERS_POSTED", true, "NO_SUMMARY", "POSTED_OR_NOT_REQUIRED",
					"当期尚无已选择生成的税费凭证。", "FIN_TAX_PERIOD_SUMMARY", null, period.periodCode(), true);
		}
		boolean requiresVoucher = summary.vatPayable().compareTo(BigDecimal.ZERO) > 0
				|| summary.urbanMaintenanceTax().compareTo(BigDecimal.ZERO) > 0
				|| summary.educationSurchargeTax().compareTo(BigDecimal.ZERO) > 0
				|| summary.localEducationSurchargeTax().compareTo(BigDecimal.ZERO) > 0
				|| summary.incomeTaxEstimated().compareTo(BigDecimal.ZERO) > 0;
		boolean posted = !requiresVoucher || ("POSTED".equals(summary.voucherStatus()) && summary.voucherId() != null);
		return new CheckItem("TAX_VOUCHERS_POSTED", posted,
				requiresVoucher ? String.valueOf(summary.voucherStatus()) : "NOT_REQUIRED",
				"POSTED_OR_NOT_REQUIRED", posted ? "已选择生成的税费计提凭证均已记账。"
						: "税费计提凭证尚未记账，不能关闭财务期间。",
				"FIN_TAX_PERIOD_SUMMARY", summary.id(), period.periodCode(), true);
	}

	private CheckItem profitLossTransferItem(PeriodRow period) {
		BigDecimal imbalance = profitLossImbalance(period.id());
		if (imbalance.compareTo(BigDecimal.ZERO) == 0) {
			return new CheckItem("PROFIT_LOSS_TRANSFER_POSTED", true, "ZERO_BALANCE", "POSTED_OR_ZERO_BALANCE",
					"损益科目已无期末余额，原本无余额时按零额通过。", "FIN_CLOSE_PROFIT_LOSS_TRANSFER", null,
					period.periodCode(), true);
		}
		ProfitLossTransferCheckRow transfer = latestProfitLossTransfer(period.id());
		boolean posted = transfer != null
				&& ("POSTED".equals(transfer.status()) || "POSTED".equals(transfer.voucherStatus()));
		return new CheckItem("PROFIT_LOSS_TRANSFER_POSTED", posted, posted ? "POSTED" : "NOT_POSTED",
				"POSTED_OR_ZERO_BALANCE", posted ? "最新损益结转凭证已通过 031 记账。"
						: "存在损益余额，必须先生成并记账期末损益结转凭证。",
				"FIN_CLOSE_PROFIT_LOSS_TRANSFER", transfer == null ? null : transfer.id(), period.periodCode(), true);
	}

	private CheckItem noSourceChangesItem(PeriodRow period, BusinessCloseRun businessCloseRun, CheckItem bank,
			CheckItem tax, CheckItem profitLoss) {
		boolean passed = bank.passed() && tax.passed() && profitLoss.passed();
		return new CheckItem("NO_SOURCE_CHANGES", passed,
				sourceFingerprint(period, businessCloseRun), "CURRENT_SOURCE_FINGERPRINT",
				passed ? "当前来源指纹与检查、对账、税务和结转版本一致。" : "存在对账、税务或结转来源变化。",
				"FIN_CLOSE_CHECK_RUN", null, period.periodCode(), true);
	}

	private void insertCheckItem(Long checkRunId, CheckItem item) {
		this.jdbcTemplate.update("""
				insert into fin_close_check_item (
					check_run_id, check_code, severity, passed, actual_value, expected_value, conclusion,
					source_type, source_id, source_no, source_restricted
				)
				values (?, ?, 'BLOCKING', ?, ?, ?, ?, ?, ?, ?, ?)
				""", checkRunId, item.checkCode(), item.passed(), item.actualValue(), item.expectedValue(),
				item.conclusion(), item.sourceType(), item.sourceId(), item.sourceNo(), item.sourceRestricted());
	}

	private String sourceFingerprint(PeriodRow period, BusinessCloseRun businessCloseRun) {
		Long voucherCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher
				where accounting_period_id = ?
				and status <> 'CANCELLED'
				""", Long.class, period.id());
		Long postedCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_ledger_entry
				where period_id = ?
				""", Long.class, period.id());
		BigDecimal debit = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(debit_amount), 0)
				from gl_ledger_entry
				where period_id = ?
				""", BigDecimal.class, period.id());
		BigDecimal credit = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(credit_amount), 0)
				from gl_ledger_entry
				where period_id = ?
				""", BigDecimal.class, period.id());
		return FinancialCloseSupport.sha256("FIN_CLOSE|" + period.id() + "|" + period.periodCode() + "|"
				+ period.version() + "|" + (businessCloseRun == null ? "NO_BIZ_CLOSE" : businessCloseRun.id()) + "|"
				+ (businessCloseRun == null ? "" : businessCloseRun.sourceFingerprint()) + "|"
				+ (voucherCount == null ? 0 : voucherCount) + "|" + (postedCount == null ? 0 : postedCount) + "|"
				+ FinancialCloseSupport.decimal(debit) + "|" + FinancialCloseSupport.decimal(credit) + "|"
				+ bankStateFingerprint(period) + "|" + taxStateFingerprint(period) + "|"
				+ profitLossStateFingerprint(period));
	}

	private List<BankAccountCheckRow> bankAccounts(PeriodRow period) {
		return this.jdbcTemplate.query("""
				select id, version
				from fin_bank_account
				where status = 'ENABLED'
				and (opened_on is null or opened_on <= ?)
				order by id
				""", (rs, rowNum) -> new BankAccountCheckRow(rs.getLong("id"), rs.getLong("version")),
				period.endDate());
	}

	private PreviousPeriodRow previousPeriod(PeriodRow period) {
		return this.jdbcTemplate.query("""
				select id, period_code, status
				from gl_accounting_period
				where ledger_id = ?
				and end_date < ?
				order by end_date desc, id desc
				limit 1
				""", (rs, rowNum) -> new PreviousPeriodRow(rs.getLong("id"), rs.getString("period_code"),
				rs.getString("status")), period.ledgerId(), period.startDate()).stream().findFirst().orElse(null);
	}

	private BankRunCheckRow latestBankRun(Long periodId, Long bankAccountId) {
		return this.jdbcTemplate.query("""
				select id, status, source_fingerprint, difference_amount, version
				from fin_bank_reconciliation_run
				where period_id = ?
				and bank_account_id = ?
				order by created_at desc, id desc
				limit 1
				""", (rs, rowNum) -> new BankRunCheckRow(rs.getLong("id"), rs.getString("status"),
				rs.getString("source_fingerprint"), FinancialCloseSupport.amount(rs.getBigDecimal("difference_amount")),
				rs.getLong("version")), periodId, bankAccountId).stream().findFirst().orElse(null);
	}

	private Long currentTaxProfileId() {
		return this.jdbcTemplate.query("""
				select id
				from fin_tax_profile
				where current_flag = true
				order by id desc
				limit 1
				""", (rs, rowNum) -> rs.getLong("id")).stream().findFirst().orElse(null);
	}

	private TaxSummaryCheckRow latestTaxSummary(Long periodId, String taxType) {
		return this.jdbcTemplate.query("""
				select id, status, source_fingerprint, current_flag, version
				from fin_tax_period_summary
				where period_id = ?
				and tax_type = ?
				and current_flag = true
				order by id desc
				limit 1
				""", (rs, rowNum) -> new TaxSummaryCheckRow(rs.getLong("id"), rs.getString("status"),
				rs.getString("source_fingerprint"), rs.getBoolean("current_flag"), rs.getLong("version")), periodId,
				taxType).stream().findFirst().orElse(null);
	}

	private TaxVoucherCheckRow latestTaxVoucherSummary(Long periodId, String taxType) {
		return this.jdbcTemplate.query("""
				select s.id, s.voucher_id, coalesce(v.status, 'NONE') as voucher_status,
				       s.vat_payable, s.urban_maintenance_tax, s.education_surcharge_tax,
				       s.local_education_surcharge_tax, s.income_tax_estimated
				from fin_tax_period_summary s
				left join gl_voucher v on v.id = s.voucher_id
				where s.period_id = ?
				and s.tax_type = ?
				and s.current_flag = true
				and s.status = 'CONFIRMED'
				order by s.id desc
				limit 1
				""", (rs, rowNum) -> new TaxVoucherCheckRow(rs.getLong("id"),
				FinancialCloseSupport.nullableLong(rs, "voucher_id"), rs.getString("voucher_status"),
				FinancialCloseSupport.amount(rs.getBigDecimal("vat_payable")),
				FinancialCloseSupport.amount(rs.getBigDecimal("urban_maintenance_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("local_education_surcharge_tax")),
				FinancialCloseSupport.amount(rs.getBigDecimal("income_tax_estimated"))), periodId, taxType)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private ProfitLossTransferCheckRow latestProfitLossTransfer(Long periodId) {
		return this.jdbcTemplate.query("""
				select t.id, t.status, coalesce(v.status, t.voucher_status) as voucher_status, t.version
				from fin_close_profit_loss_transfer t
				left join gl_voucher v on v.id = t.voucher_id
				where t.period_id = ?
				order by t.created_at desc, t.id desc
				limit 1
				""", (rs, rowNum) -> new ProfitLossTransferCheckRow(rs.getLong("id"), rs.getString("status"),
				rs.getString("voucher_status"), rs.getLong("version")), periodId).stream().findFirst().orElse(null);
	}

	private BigDecimal profitLossImbalance(Long periodId) {
		return queryAmount("""
				select coalesce(sum(abs(balance)), 0)
				from (
					select sum(e.debit_amount - e.credit_amount) as balance
					from gl_ledger_entry e
					join gl_account a on a.id = e.account_id
					where e.period_id = ?
					and a.category = 'PROFIT_LOSS'
					group by e.account_id
					having sum(e.debit_amount) <> sum(e.credit_amount)
				) balances
				""", periodId);
	}

	private BigDecimal queryAmount(String sql, Object... args) {
		return FinancialCloseSupport.amount(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, args));
	}

	private String bankStateFingerprint(PeriodRow period) {
		List<String> states = new ArrayList<>();
		for (BankAccountCheckRow account : bankAccounts(period)) {
			BankRunCheckRow run = latestBankRun(period.id(), account.id());
			states.add(account.id() + ":" + account.version() + ":" + (run == null ? "NO_RUN"
					: run.id() + ":" + run.status() + ":" + run.version() + ":" + run.sourceFingerprint() + ":"
							+ bankSourceFingerprint(period.id(), account.id()) + ":" + run.differenceAmount()));
		}
		return String.join("|", states);
	}

	private String taxStateFingerprint(PeriodRow period) {
		Long profileId = currentTaxProfileId();
		TaxSummaryCheckRow vat = latestTaxSummary(period.id(), "VAT");
		return "PROFILE|" + (profileId == null ? "NO_PROFILE" : profileId) + "|VAT|"
				+ (vat == null ? "NO_SUMMARY" : vat.id() + ":" + vat.status() + ":" + vat.version() + ":"
						+ vat.sourceFingerprint() + ":" + vat.currentFlag())
				+ "|CURRENT|" + taxSourceFingerprint(period);
	}

	private String profitLossStateFingerprint(PeriodRow period) {
		ProfitLossTransferCheckRow transfer = latestProfitLossTransfer(period.id());
		return "IMBALANCE|" + profitLossImbalance(period.id()) + "|TRANSFER|"
				+ (transfer == null ? "NO_TRANSFER" : transfer.id() + ":" + transfer.status() + ":"
						+ transfer.voucherStatus() + ":" + transfer.version());
	}

	private String bankSourceFingerprint(Long periodId, Long bankAccountId) {
		String source = this.jdbcTemplate.queryForObject("""
				select coalesce(string_agg(source_key, ',' order by source_key), '')
				from (
					select 'B|' || l.id || '|' || l.version || '|' || l.posting_date || '|' || l.direction || '|' || l.amount as source_key
					from fin_bank_statement_line l
					join gl_accounting_period p on p.id = ?
					where l.bank_account_id = ?
					and l.posting_date between p.start_date and p.end_date
					and l.status <> 'IGNORED'
					union all
					select 'G|' || e.id || '|' || e.voucher_date || '|' || e.debit_amount || '|' || e.credit_amount as source_key
					from gl_ledger_entry e
					join fin_bank_account a on a.id = ?
					where e.period_id = ?
					and e.account_id = a.gl_account_id
				) source
				""", String.class, periodId, bankAccountId, bankAccountId, periodId);
		return FinancialCloseSupport.sha256("BANK_RECON|" + periodId + "|" + bankAccountId + "|" + source);
	}

	private String taxSourceFingerprint(PeriodRow period) {
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

	private Long idempotentResult(String action, String resourceType, Long resourceId, String key, String fingerprint,
			CurrentUser operator) {
		if (key == null || key.isBlank()) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
		return this.jdbcTemplate.query("""
				select request_fingerprint, result_resource_id
				from fin_close_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and coalesce(resource_id, 0) = coalesce(?, 0)
				and idempotency_key = ?
				""", (rs, rowNum) -> {
			if (!fingerprint.equals(rs.getString("request_fingerprint"))) {
				throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
			}
			return rs.getLong("result_resource_id");
		}, operator.id(), action, resourceType, resourceId, key).stream().findFirst().orElse(null);
	}

	private void recordAction(String action, String resourceType, Long resourceId, Long resourceVersion, String key,
			String fingerprint, String resultType, Long resultId, Long resultVersion, CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into fin_close_action_idempotency (
						operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
						idempotency_key, request_fingerprint, result_resource_type, result_resource_id, result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), operator.username(), action, resourceType, resourceId, resourceVersion, key,
					fingerprint, resultType, resultId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.FIN_CLOSE_CONFLICT);
		}
	}

	public record PeriodRow(Long id, Long ledgerId, String periodCode, LocalDate startDate, LocalDate endDate,
			String status, Long version) {
	}

	public record BusinessCloseRun(Long id, Integer revisionNo, String sourceFingerprint, Long version) {
	}

	private record CheckItem(String checkCode, boolean passed, String actualValue, String expectedValue,
			String conclusion, String sourceType, Long sourceId, String sourceNo, boolean sourceRestricted) {
	}

	private record PreviousPeriodRow(Long id, String periodCode, String status) {
	}

	private record BankAccountCheckRow(Long id, Long version) {
	}

	private record BankRunCheckRow(Long id, String status, String sourceFingerprint, BigDecimal differenceAmount,
			Long version) {
	}

	private record TaxSummaryCheckRow(Long id, String status, String sourceFingerprint, boolean currentFlag,
			Long version) {
	}

	private record TaxVoucherCheckRow(Long id, Long voucherId, String voucherStatus, BigDecimal vatPayable,
			BigDecimal urbanMaintenanceTax, BigDecimal educationSurchargeTax, BigDecimal localEducationSurchargeTax,
			BigDecimal incomeTaxEstimated) {
	}

	private record ProfitLossTransferCheckRow(Long id, String status, String voucherStatus, Long version) {
	}

}
