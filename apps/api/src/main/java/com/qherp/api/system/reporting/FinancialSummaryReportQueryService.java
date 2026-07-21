package com.qherp.api.system.reporting;

import com.qherp.api.common.PageResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.FinancialSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
class FinancialSummaryReportQueryService extends ReportingStage033QuerySupport {

	FinancialSummaryReportQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		super(jdbcTemplate, objectMapper);
	}

	Object financialSummary(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, false);
		rejectUnsupportedSnapshot(query);
		PeriodState state = periodState(query);
		return financialSummaryLive(query, state);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> financialSummaryTraces(
			MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseTraceQuery(parameters);
		validateFinancialTraceKey(query);
		if (!financialSummarySourceVisible()) {
			return emptyTracePage(query);
		}
		return tracePage(financialSummaryTraceRows(query), query);
	}

	private FinancialSummaryResponse financialSummaryLive(OperatingFinanceQuery query, PeriodState state) {
		FinancialAmounts amounts = financialAmounts(query);
		boolean visible = financialSummaryAmountVisible();
		boolean sourceVisible = financialSummarySourceVisible();
		String bankStatus = bankReconciliationStatus(state.accountingPeriodId());
		String taxStatus = taxSummaryStatus(state.accountingPeriodId());
		return new FinancialSummaryResponse(query.periodCode(), query.analysisMode(), state.finalityStatus(),
				state.businessPeriodStatus(), state.accountingPeriodStatus(), state.financialCloseStatus(),
				visible ? amount(amounts.revenue()) : null, visible ? amount(amounts.mainCost()) : null,
				visible ? amount(amounts.periodExpense()) : null, visible ? amount(amounts.otherProfitLoss()) : null,
				visible ? amount(amounts.incomeTaxExpense()) : null, visible ? amount(amounts.operatingResult()) : null,
				visible ? amount(amounts.assetBalance()) : null, visible ? amount(amounts.liabilityBalance()) : null,
				visible ? amount(amounts.equityBalance()) : null,
				amounts.sourceCount() == 0 ? UNAVAILABLE : amounts.balanced() ? "MATCHED" : "DIFFERENT",
				bankStatus, taxStatus,
				sourceVisible ? amounts.sourceCount() + closeSourceCount(state.accountingPeriodId())
						+ bankSourceCount(state.accountingPeriodId()) + taxSourceCount(state.accountingPeriodId()) : 0,
				sourceVisible ? "financial-summary:PERIOD:" + query.periodCode() : null, false, "不是法定财务报表");
	}

	private FinancialAmounts financialAmounts(OperatingFinanceQuery query) {
		return this.jdbcTemplate.query("""
				with recursive account_scope(account_id, root_code) as (
					select id, code
					from gl_account
					where code in ('6001', '6051', '6401', '6601', '6602', '6603', '6301', '6711', '6801')
					union all
					select child.id, parent.root_code
					from gl_account child
					join account_scope parent on parent.account_id = child.parent_id
				),
				scoped as (
					select e.*, a.category, account_scope.root_code
					from gl_ledger_entry e
					join gl_account a on a.id = e.account_id
					left join account_scope on account_scope.account_id = e.account_id
					join gl_voucher v on v.id = e.voucher_id
					join gl_accounting_period p on p.id = e.period_id
					where p.period_code = ?
					and v.status = 'POSTED'
				)
				select
					coalesce(sum(case when root_code in ('6001', '6051') then credit_amount - debit_amount else 0 end), 0) as revenue,
					coalesce(sum(case when root_code = '6401' then debit_amount - credit_amount else 0 end), 0) as main_cost,
					coalesce(sum(case when root_code in ('6601', '6602', '6603') then debit_amount - credit_amount else 0 end), 0) as period_expense,
					coalesce(sum(case when root_code = '6301' then credit_amount - debit_amount
						when root_code = '6711' then debit_amount - credit_amount else 0 end), 0) as other_profit_loss,
					coalesce(sum(case when root_code = '6801' then debit_amount - credit_amount else 0 end), 0) as income_tax_expense,
					coalesce(sum(case when category = 'ASSET' then debit_amount - credit_amount else 0 end), 0) as asset_balance,
					coalesce(sum(case when category = 'LIABILITY' then credit_amount - debit_amount else 0 end), 0) as liability_balance,
					coalesce(sum(case when category = 'EQUITY' then credit_amount - debit_amount else 0 end), 0) as equity_balance,
					coalesce(sum(debit_amount), 0) = coalesce(sum(credit_amount), 0) as balanced,
					count(*) as source_count
				from scoped
				""", (rs, rowNum) -> {
			BigDecimal revenue = rs.getBigDecimal("revenue");
			BigDecimal mainCost = rs.getBigDecimal("main_cost");
			BigDecimal periodExpense = rs.getBigDecimal("period_expense");
			BigDecimal otherProfitLoss = rs.getBigDecimal("other_profit_loss");
			BigDecimal incomeTaxExpense = rs.getBigDecimal("income_tax_expense");
			BigDecimal operatingResult = revenue.subtract(mainCost).subtract(periodExpense).add(otherProfitLoss)
				.subtract(incomeTaxExpense);
			return new FinancialAmounts(revenue, mainCost, periodExpense, otherProfitLoss, incomeTaxExpense,
					operatingResult, rs.getBigDecimal("asset_balance"), rs.getBigDecimal("liability_balance"),
					rs.getBigDecimal("equity_balance"), rs.getBoolean("balanced"), rs.getInt("source_count"));
		}, query.periodCode()).stream().findFirst().orElseGet(FinancialAmounts::empty);
	}

	private String bankReconciliationStatus(Long accountingPeriodId) {
		if (accountingPeriodId == null) {
			return UNAVAILABLE;
		}
		List<String> rows = this.jdbcTemplate.queryForList("""
				select status
				from fin_bank_reconciliation_run
				where period_id = ?
				order by created_at desc, id desc
				limit 1
				""", String.class, accountingPeriodId);
		return rows.isEmpty() ? UNAVAILABLE : rows.getFirst();
	}

	private String taxSummaryStatus(Long accountingPeriodId) {
		if (accountingPeriodId == null) {
			return UNAVAILABLE;
		}
		List<String> rows = this.jdbcTemplate.queryForList("""
				select status
				from fin_tax_period_summary
				where period_id = ?
				order by updated_at desc, id desc
				limit 1
				""", String.class, accountingPeriodId);
		return rows.isEmpty() ? UNAVAILABLE : rows.getFirst();
	}

	private int closeSourceCount(Long accountingPeriodId) {
		return countByPeriod("fin_close_run", accountingPeriodId);
	}

	private int bankSourceCount(Long accountingPeriodId) {
		return countByPeriod("fin_bank_reconciliation_run", accountingPeriodId);
	}

	private int taxSourceCount(Long accountingPeriodId) {
		return countByPeriod("fin_tax_period_summary", accountingPeriodId);
	}

	private int countByPeriod(String tableName, Long accountingPeriodId) {
		if (accountingPeriodId == null) {
			return 0;
		}
		Long count = this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where period_id = ?",
				Long.class, accountingPeriodId);
		return count == null ? 0 : count.intValue();
	}

	private List<ReportingAdminService.TraceSourceResponse> financialSummaryTraceRows(OperatingFinanceQuery query) {
		PeriodState state = periodState(query);
		List<ReportingAdminService.TraceSourceResponse> traces = new ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select e.id, e.voucher_no, e.voucher_line_id, e.voucher_date, v.status as source_status,
				       e.debit_amount + e.credit_amount as amount
				from gl_ledger_entry e
				join gl_voucher v on v.id = e.voucher_id
				join gl_accounting_period p on p.id = e.period_id
				where p.period_code = ?
				and v.status = 'POSTED'
				order by e.voucher_date, e.id
				""", (rs, rowNum) -> trace("GL_LEDGER_ENTRY", rs.getLong("id"), rs.getString("voucher_no"),
				rs.getLong("voucher_line_id"), rs.getObject("voucher_date", LocalDate.class),
				rs.getString("source_status"), null, rs.getBigDecimal("amount"), "gl:ledger:view",
				"gl-ledger-entry", routeParams("id", rs.getLong("id"))), query.periodCode()));
		if (state.accountingPeriodId() != null) {
			traces.addAll(this.jdbcTemplate.query("""
					select id, closed_at::date as business_date, status, source_fingerprint
					from fin_close_run
					where period_id = ?
					order by close_version desc, id desc
					""", (rs, rowNum) -> trace("FIN_CLOSE_RUN", rs.getLong("id"),
					rs.getString("source_fingerprint"), null, rs.getObject("business_date", LocalDate.class),
					rs.getString("status"), null, null, "financial-close:period:view", "financial-close-run",
					routeParams("id", rs.getLong("id"))), state.accountingPeriodId()));
			traces.addAll(this.jdbcTemplate.query("""
					select id, created_at::date as business_date, status, source_fingerprint
					from fin_bank_reconciliation_run
					where period_id = ?
					order by created_at desc, id desc
					""", (rs, rowNum) -> trace("BANK_RECONCILIATION_RUN", rs.getLong("id"),
					rs.getString("source_fingerprint"), null, rs.getObject("business_date", LocalDate.class),
					rs.getString("status"), null, null, "financial-close:bank-reconciliation:view",
					"bank-reconciliation-run", routeParams("id", rs.getLong("id"))), state.accountingPeriodId()));
			traces.addAll(this.jdbcTemplate.query("""
					select id, updated_at::date as business_date, status, source_fingerprint
					from fin_tax_period_summary
					where period_id = ?
					order by updated_at desc, id desc
					""", (rs, rowNum) -> trace("TAX_PERIOD_SUMMARY", rs.getLong("id"),
					rs.getString("source_fingerprint"), null, rs.getObject("business_date", LocalDate.class),
					rs.getString("status"), null, null, "financial-close:tax-summary:view", "tax-period-summary",
					routeParams("id", rs.getLong("id"))), state.accountingPeriodId()));
		}
		return traces;
	}

	private void validateFinancialTraceKey(OperatingFinanceQuery query) {
		validateTraceKey(query.traceKey(), "financial-summary");
		String[] parts = query.traceKey().split(":");
		if (parts.length != 3 || !"PERIOD".equals(parts[1]) || !query.periodCode().equals(parts[2])) {
			throw new com.qherp.api.common.BusinessException(
					com.qherp.api.common.ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
	}

	private record FinancialAmounts(BigDecimal revenue, BigDecimal mainCost, BigDecimal periodExpense,
			BigDecimal otherProfitLoss, BigDecimal incomeTaxExpense, BigDecimal operatingResult,
			BigDecimal assetBalance, BigDecimal liabilityBalance, BigDecimal equityBalance, boolean balanced,
			int sourceCount) {

		static FinancialAmounts empty() {
			return new FinancialAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, 0);
		}

	}

}
