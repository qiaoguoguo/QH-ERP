package com.qherp.api.system.reporting;

import com.qherp.api.common.PageResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.OperatingAccountingReconciliationItemResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.OperatingAccountingReconciliationSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
class OperatingAccountingReportQueryService extends ReportingStage033QuerySupport {

	OperatingAccountingReportQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		super(jdbcTemplate, objectMapper);
	}

	Object operatingAccountingReconciliation(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, true);
		rejectUnsupportedSnapshot(query);
		PeriodState state = periodState(query);
		BigDecimal publicUnassigned = publicUnassignedAmount(query);
		boolean amountVisible = operatingAccountingAmountVisible();
		boolean sourceVisible = operatingAccountingSourceVisible();
		List<ProjectCostRow> rows = reconciliationProjectRows(query).stream()
			.filter((row) -> matchesStatusFilters(row, query, publicUnassigned, state, amountVisible))
			.toList();
		String freshness = freshnessStatus(query, rows);
		List<OperatingAccountingReconciliationItemResponse> items = rows.stream()
			.map((row) -> reconciliationItem(query, row, publicUnassigned, state, amountVisible, sourceVisible,
					freshness))
			.toList();
		OperatingAccountingReconciliationSummaryResponse summary = reconciliationSummary(items, publicUnassigned,
				amountVisible, state, query, freshness);
		return pageOf(summary, items, query);
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> operatingAccountingTraces(
			MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseTraceQuery(parameters);
		validateTraceKey(query.traceKey(), "operating-accounting");
		TraceKeyParts parts = traceKeyParts(query.traceKey(), "operating-accounting");
		if (!"PROJECT".equals(parts.type())) {
			throw new com.qherp.api.common.BusinessException(com.qherp.api.common.ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return tracePage(operatingAccountingTraceRows(parts.sourceId(), query), query);
	}

	private OperatingAccountingReconciliationItemResponse reconciliationItem(OperatingFinanceQuery query,
			ProjectCostRow row, BigDecimal publicUnassigned, PeriodState state, boolean amountVisible,
			boolean sourceVisible, String freshness) {
		ProjectAccountingAmounts accounting = accountingAmounts(query, row.projectId());
		BigDecimal operatingProfit = grossProfit(row);
		BigDecimal difference = accounting.available() ? operatingProfit.subtract(accounting.profit()) : null;
		String reconciliationStatus = reconciliationStatus(difference, publicUnassigned, accounting.available(),
				amountVisible);
		return new OperatingAccountingReconciliationItemResponse(row.projectId(), row.projectNo(), row.projectName(),
				visibleAmount(row.shipmentRevenue(), amountVisible), visibleAmount(row.projectCostTotal(), amountVisible),
				visibleAmount(operatingProfit, amountVisible),
				accounting.available() ? visibleAmount(accounting.revenue(), amountVisible) : null,
				accounting.available() ? visibleAmount(accounting.cost(), amountVisible) : null,
				accounting.available() ? visibleAmount(accounting.profit(), amountVisible) : null,
				visibleAmount(publicUnassigned, amountVisible),
				visibleAmount(difference, amountVisible), reconciliationStatus, state.finalityStatus(),
				accounting.available() ? "管理口径与会计口径按本期发生额对照" : "无会计事实",
				sourceVisible ? row.sourceCount() + accounting.sourceCount() : 0,
				sourceVisible ? "operating-accounting:PROJECT:" + row.projectId() : null);
	}

	private OperatingAccountingReconciliationSummaryResponse reconciliationSummary(
			List<OperatingAccountingReconciliationItemResponse> items, BigDecimal publicUnassigned,
			boolean amountVisible, PeriodState state, OperatingFinanceQuery query, String freshness) {
		if (!amountVisible) {
			return new OperatingAccountingReconciliationSummaryResponse(null, null, null, null, 0, RESTRICTED,
					state.finalityStatus(), query.analysisMode(), freshness);
		}
		BigDecimal operatingProfit = items.stream()
			.map((item) -> decimal(item.operatingProfitAmount()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal accountingProfit = items.stream()
			.map((item) -> decimal(item.accountingProfitAmount()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal difference = items.stream()
			.map((item) -> decimal(item.differenceAmount()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		boolean unavailable = UNAVAILABLE.equals(state.finalityStatus())
				|| items.stream().anyMatch((item) -> UNAVAILABLE.equals(item.reconciliationStatus()));
		return new OperatingAccountingReconciliationSummaryResponse(amount(operatingProfit),
				unavailable ? null : amount(accountingProfit), visibleAmount(publicUnassigned, amountVisible),
				unavailable ? null : amount(difference),
				items.stream().mapToInt(OperatingAccountingReconciliationItemResponse::sourceCount).sum(),
				unavailable ? UNAVAILABLE : "DIFFERENT", state.finalityStatus(), query.analysisMode(), freshness);
	}

	private boolean matchesStatusFilters(ProjectCostRow row, OperatingFinanceQuery query, BigDecimal publicUnassigned,
			PeriodState state, boolean amountVisible) {
		if (hasText(query.completenessStatus())
				&& !query.completenessStatus().equalsIgnoreCase(row.completenessStatus())) {
			return false;
		}
		ProjectAccountingAmounts accounting = accountingAmounts(query, row.projectId());
		BigDecimal difference = accounting.available() ? grossProfit(row).subtract(accounting.profit()) : null;
		String reconciliationStatus = reconciliationStatus(difference, publicUnassigned, accounting.available(),
				amountVisible);
		if (hasText(query.reconciliationStatus())
				&& !query.reconciliationStatus().equalsIgnoreCase(reconciliationStatus)) {
			return false;
		}
		return !hasText(query.finalityStatus()) || query.finalityStatus().equalsIgnoreCase(state.finalityStatus());
	}

	private List<ReportingAdminService.TraceSourceResponse> operatingAccountingTraceRows(long projectId,
			OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new java.util.ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select id, calculation_no, cutoff_date, status, project_cost_total
				from prj_cost_calculation
				where project_id = ?
				and status in ('CALCULATED', 'CONFIRMED')
				and cutoff_date between ? and ?
				order by cutoff_date desc, id desc
				""", (rs, rowNum) -> trace("PROJECT_COST_CALCULATION", rs.getLong("id"),
				rs.getString("calculation_no"), null, rs.getObject("cutoff_date", LocalDate.class),
				rs.getString("status"), null, rs.getBigDecimal("project_cost_total"), "cost:project-cost:view",
				"project-cost-calculation-detail", routeParams("id", rs.getLong("id"))), projectId, query.dateFrom(),
				query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select e.id, e.voucher_no, e.voucher_line_id, e.voucher_date, v.status as source_status,
				       e.debit_amount + e.credit_amount as amount
				from gl_ledger_entry e
				join gl_voucher v on v.id = e.voucher_id
				join gl_accounting_period p on p.id = e.period_id
				where p.period_code = ?
				and v.status = 'POSTED'
				and exists (
					select 1
					from jsonb_array_elements(e.auxiliary_snapshot) aux
					where aux ->> 'dimensionCode' = 'PROJECT'
					and aux ->> 'objectId' = ?
				)
				order by e.voucher_date, e.id
				""", (rs, rowNum) -> trace("GL_LEDGER_ENTRY", rs.getLong("id"), rs.getString("voucher_no"),
				rs.getLong("voucher_line_id"), rs.getObject("voucher_date", LocalDate.class),
				rs.getString("source_status"), null, rs.getBigDecimal("amount"), "gl:ledger:view",
				"gl-ledger-entry", routeParams("id", rs.getLong("id"))), query.periodCode(),
				String.valueOf(projectId)));
		if (traces.isEmpty()) {
			throw new com.qherp.api.common.BusinessException(com.qherp.api.common.ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

}
