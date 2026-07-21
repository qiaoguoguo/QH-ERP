package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.AccountingBasisResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.AccountingEntryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.CostStageEntryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ManagementBasisResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ProjectProfitDetailResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ProjectProfitItemResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ProjectProfitSummaryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.RevenueEntryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.VarianceReasonResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
class ProjectProfitReportQueryService extends ReportingStage033QuerySupport {

	ProjectProfitReportQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		super(jdbcTemplate, objectMapper);
	}

	Object projectProfit(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, true);
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			return projectProfitSnapshot(query);
		}
		return projectProfitLive(query);
	}

	Object captureSnapshot(String periodCode, LocalDate dateFrom, LocalDate dateTo) {
		return projectProfitLive(captureQuery(periodCode, dateFrom, dateTo));
	}

	Object projectProfitDetail(Long projectId, MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, false).withProjectId(projectId);
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			return projectProfitSnapshotDetail(projectId, query);
		}
		PeriodState state = periodState(query);
		List<ProjectCostRow> rows = projectCostRows(query);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_SOURCE_INCOMPLETE);
		}
		ProjectCostRow row = rows.getFirst();
		ProjectAccountingAmounts accounting = accountingAmounts(query, row.projectId());
		BigDecimal publicUnassigned = publicUnassignedAmount(query);
		return projectProfitDetail(query, row, accounting, publicUnassigned, state, projectProfitAmountVisible(),
				projectProfitAccountingAmountVisible(), projectProfitSourceVisible(), freshnessStatus(query, rows));
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> projectProfitTraces(Long projectId,
			MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseTraceQuery(parameters).withProjectId(projectId);
		validateTraceKey(query.traceKey(), "project-profit");
		return tracePage(projectProfitTraceRows(projectId, query), query);
	}

	ProjectProfitSummaryResponse summaryForOverview(OperatingFinanceQuery query, PeriodState state) {
		List<ProjectCostRow> rows = projectCostRows(query);
		return projectProfitSummary(rows, query, state, projectProfitSourceVisible(), freshnessStatus(query, rows));
	}

	private ReportingAdminService.ReportPageResponse<Object> projectProfitSnapshot(OperatingFinanceQuery query) {
		return snapshotPage("PROJECT_PROFIT", query, (item) -> matchesProjectSnapshot(item, query),
				(items) -> projectProfitSnapshotSummary(items, query, projectProfitAmountVisible(),
						projectProfitAccountingAmountVisible(), projectProfitSourceVisible()),
				(item) -> projectProfitSnapshotItem(item, projectProfitAmountVisible(),
						projectProfitAccountingAmountVisible(), projectProfitSourceVisible()));
	}

	private ReportingAdminService.ReportPageResponse<Object> projectProfitLive(OperatingFinanceQuery query) {
		PeriodState state = periodState(query);
		BigDecimal publicUnassigned = publicUnassignedAmount(query);
		boolean managementVisible = projectProfitAmountVisible();
		boolean accountingVisible = projectProfitAccountingAmountVisible();
		boolean sourceVisible = projectProfitSourceVisible();
		List<ProjectCostRow> costRows = projectCostRows(query).stream()
			.filter((row) -> matchesProjectLiveFilters(query, row, publicUnassigned, state, managementVisible,
					accountingVisible))
			.toList();
		String freshness = freshnessStatus(query, costRows);
		List<ProjectProfitItemResponse> items = costRows.stream()
			.map((row) -> projectProfitItem(query, row, publicUnassigned, state, managementVisible, accountingVisible,
					sourceVisible, freshness))
			.toList();
		ProjectProfitSummaryResponse summary = projectProfitSummary(costRows, query, state, sourceVisible, freshness);
		return pageOf(summary, items, query);
	}

	private ProjectProfitSummaryResponse projectProfitSummary(List<ProjectCostRow> rows, OperatingFinanceQuery query,
			PeriodState state, boolean sourceVisible, String freshness) {
		boolean visible = projectProfitAmountVisible();
		boolean accountingVisible = projectProfitAccountingAmountVisible();
		BigDecimal shipmentRevenue = sum(rows, ProjectCostRow::shipmentRevenue);
		BigDecimal invoiceRevenue = sum(rows, ProjectCostRow::invoiceRevenue);
		BigDecimal targetRevenue = sum(rows, ProjectCostRow::targetRevenue);
		BigDecimal projectCost = sum(rows, ProjectCostRow::projectCostTotal);
		BigDecimal grossProfit = shipmentRevenue.subtract(projectCost);
		BigDecimal accountingRevenue = BigDecimal.ZERO;
		BigDecimal accountingCost = BigDecimal.ZERO;
		BigDecimal accountingProfit = BigDecimal.ZERO;
		BigDecimal difference = BigDecimal.ZERO;
		int accountingSourceCount = 0;
		for (ProjectCostRow row : rows) {
			ProjectAccountingAmounts accounting = accountingAmounts(query, row.projectId());
			if (accounting.available()) {
				accountingRevenue = accountingRevenue.add(accounting.revenue());
				accountingCost = accountingCost.add(accounting.cost());
				accountingProfit = accountingProfit.add(accounting.profit());
				difference = difference.add(grossProfit(row).subtract(accounting.profit()));
				accountingSourceCount += accounting.sourceCount();
			}
		}
		boolean accountingAvailable = accountingSourceCount > 0;
		BigDecimal publicUnassigned = publicUnassignedAmount(query);
		String reconciliationStatus = rows.isEmpty() || !accountingVisible || !accountingAvailable ? UNAVAILABLE
				: (difference.compareTo(BigDecimal.ZERO) == 0 && publicUnassigned.compareTo(BigDecimal.ZERO) == 0
						? "MATCHED" : "DIFFERENT");
		if (!visible) {
			return new ProjectProfitSummaryResponse(rows.size(), null, null, null, null, null, null, null, null, 0,
					false, RESTRICTED, freshness, RESTRICTED, state.finalityStatus(), "缺少上游金额权限", null, null, null, null,
					null, null, null, null, query.analysisMode(), null, null);
		}
		return new ProjectProfitSummaryResponse(rows.size(), amount(shipmentRevenue), amount(invoiceRevenue),
				amount(targetRevenue), amount(projectCost), amount(grossProfit),
				accountingVisible && accountingAvailable ? amount(accountingProfit) : null,
				accountingVisible && accountingAvailable ? amount(difference) : null,
				accountingVisible ? amount(publicUnassigned) : null,
				sourceVisible
						? rows.stream().mapToInt(ProjectCostRow::sourceCount).sum()
								+ (accountingVisible ? accountingSourceCount : 0)
						: 0,
				true,
				rows.stream().anyMatch((row) -> !"COMPLETE".equals(row.completenessStatus())) ? "INCOMPLETE"
						: (rows.isEmpty() ? UNAVAILABLE : "COMPLETE"),
				freshness, reconciliationStatus, state.finalityStatus(), null, amount(shipmentRevenue),
				amount(projectCost), amount(grossProfit),
				accountingVisible && accountingAvailable ? amount(accountingProfit) : null,
				accountingVisible && accountingAvailable ? amount(difference) : null, amount(invoiceRevenue),
				amount(targetRevenue), accountingVisible ? amount(publicUnassigned) : null, query.analysisMode(),
				accountingVisible && accountingAvailable ? amount(accountingRevenue) : null,
				accountingVisible && accountingAvailable ? amount(accountingCost) : null);
	}

	private ProjectProfitItemResponse projectProfitItem(OperatingFinanceQuery query, ProjectCostRow row,
			BigDecimal publicUnassigned, PeriodState state, boolean amountVisible, boolean accountingVisible,
			boolean sourceVisible, String freshness) {
		ProjectAccountingAmounts accounting = accountingAmounts(query, row.projectId());
		BigDecimal grossProfit = grossProfit(row);
		BigDecimal difference = accounting.available() ? grossProfit.subtract(accounting.profit()) : null;
		String reconciliationStatus = accountingVisible
				? reconciliationStatus(difference, publicUnassigned, accounting.available(), amountVisible)
				: UNAVAILABLE;
		if (!amountVisible) {
			return new ProjectProfitItemResponse(row.projectId(), row.projectNo(), row.projectName(),
					row.customerName(), null, null, null, null, null, null, null, null, null,
					row.completenessStatus(), freshness, RESTRICTED, state.finalityStatus(), 0, null,
					false, "缺少上游金额权限", null, null, null, null, null,
					null, null, null, null, null);
		}
		return new ProjectProfitItemResponse(row.projectId(), row.projectNo(), row.projectName(), row.customerName(),
				amount(row.shipmentRevenue()), amount(row.invoiceRevenue()), amount(row.targetRevenue()),
				amount(row.projectCostTotal()), amount(grossProfit), percentage(grossProfit, row.shipmentRevenue()),
				accountingVisible && accounting.available() ? amount(accounting.revenue()) : null,
				accountingVisible && accounting.available() ? amount(accounting.cost()) : null,
				accountingVisible && accounting.available() ? amount(accounting.profit()) : null,
				row.completenessStatus(), freshness, reconciliationStatus, state.finalityStatus(),
				sourceVisible ? row.sourceCount() + (accountingVisible && accounting.available() ? accounting.sourceCount() : 0)
						: 0,
				sourceVisible ? "project-profit:PROJECT:" + row.projectId() : null, true, null,
				accountingVisible && accounting.available() ? amount(difference) : null,
				amount(row.shipmentRevenue()), amount(row.invoiceRevenue()), amount(row.targetRevenue()),
				amount(row.projectCostTotal()), amount(grossProfit), percentage(grossProfit, row.shipmentRevenue()),
				accountingVisible && accounting.available() ? amount(accounting.revenue()) : null,
				accountingVisible && accounting.available() ? amount(accounting.cost()) : null,
				accountingVisible && accounting.available() ? amount(accounting.profit()) : null);
	}

	private ProjectProfitDetailResponse projectProfitDetail(OperatingFinanceQuery query, ProjectCostRow row,
			ProjectAccountingAmounts accounting, BigDecimal publicUnassigned, PeriodState state,
			boolean amountVisible, boolean accountingVisible, boolean sourceVisible, String freshness) {
		BigDecimal grossProfit = grossProfit(row);
		BigDecimal difference = accounting.available() ? grossProfit.subtract(accounting.profit()) : null;
		ProjectProfitItemResponse base = projectProfitItem(query, row, publicUnassigned, state, amountVisible,
				accountingVisible, sourceVisible, freshness);
		List<CostStageEntryResponse> costStageEntries = List.of(
				new CostStageEntryResponse("WIP", visibleAmount(row.wipCost(), amountVisible), row.completenessStatus()),
				new CostStageEntryResponse("FINISHED", visibleAmount(row.finishedCost(), amountVisible),
						row.completenessStatus()),
				new CostStageEntryResponse("DELIVERED", visibleAmount(row.deliveredCost(), amountVisible),
						row.completenessStatus()),
				new CostStageEntryResponse("DIRECT_PROJECT", visibleAmount(row.directProjectCost(), amountVisible),
						row.completenessStatus()));
		List<RevenueEntryResponse> revenueEntries = List.of(
				new RevenueEntryResponse("SHIPMENT", visibleAmount(row.shipmentRevenue(), amountVisible),
						"发货经营收入"),
				new RevenueEntryResponse("INVOICE", visibleAmount(row.invoiceRevenue(), amountVisible), "开票辅助收入"),
				new RevenueEntryResponse("TARGET", visibleAmount(row.targetRevenue(), amountVisible), "项目目标收入"));
		List<AccountingEntryResponse> accountingEntries = amountVisible && accountingVisible && accounting.available()
				? List.of(new AccountingEntryResponse(null, "项目收入", amount(accounting.revenue()), "PROJECT辅助收入"),
						new AccountingEntryResponse(null, "项目成本", amount(accounting.cost()), "PROJECT辅助成本"),
						new AccountingEntryResponse(null, "项目利润", amount(accounting.profit()), "本期发生额"))
				: List.of();
		List<VarianceReasonResponse> varianceReasons = List.of(new VarianceReasonResponse(
				accounting.available() ? base.reconciliationStatus() : "NO_ACCOUNTING_FACT",
				accounting.available() ? "管理口径与会计口径存在差异" : "无会计项目辅助事实",
				visibleAmount(difference, amountVisible && accountingVisible)));
		return new ProjectProfitDetailResponse(base.projectId(), base.projectNo(), base.projectName(),
				base.customerName(), base.shipmentRevenueAmount(), base.invoiceRevenueAmount(),
				base.targetRevenueAmount(), base.projectCostAmount(), base.operatingGrossProfitAmount(),
				base.operatingGrossProfitRate(), base.accountingRevenueAmount(), base.accountingCostAmount(),
				base.accountingProfitAmount(), base.completenessStatus(), base.freshnessStatus(),
				base.reconciliationStatus(), base.finalityStatus(), base.sourceCount(), base.traceKey(),
				base.amountVisible(), base.restrictedReason(), base.differenceAmount(), base.shipmentRevenue(),
				base.invoiceRevenue(), base.targetRevenue(), base.projectCostTotal(), base.shipmentGrossMargin(),
				base.shipmentGrossMarginRate(), base.accountingRevenue(), base.accountingCost(),
				base.accountingProfit(), new ManagementBasisResponse(visibleAmount(row.shipmentRevenue(), amountVisible),
						visibleAmount(row.invoiceRevenue(), amountVisible), visibleAmount(row.targetRevenue(), amountVisible),
						visibleAmount(row.projectCostTotal(), amountVisible), visibleAmount(grossProfit, amountVisible),
						percentageIfVisible(grossProfit, row.shipmentRevenue(), amountVisible),
						row.completenessStatus()),
				new AccountingBasisResponse(accounting.available()
						? visibleAmount(accounting.revenue(), amountVisible && accountingVisible) : null,
						accounting.available() ? visibleAmount(accounting.cost(), amountVisible && accountingVisible)
								: null,
						accounting.available() ? visibleAmount(accounting.profit(), amountVisible && accountingVisible)
								: null,
						visibleAmount(publicUnassigned, amountVisible && accountingVisible), state.finalityStatus()),
				costStageEntries, revenueEntries, accountingEntries, varianceReasons);
	}

	private boolean matchesProjectLiveFilters(OperatingFinanceQuery query, ProjectCostRow row,
			BigDecimal publicUnassigned, PeriodState state, boolean amountVisible, boolean accountingVisible) {
		if (hasText(query.completenessStatus())
				&& !query.completenessStatus().equalsIgnoreCase(row.completenessStatus())) {
			return false;
		}
		if (hasText(query.finalityStatus()) && !query.finalityStatus().equalsIgnoreCase(state.finalityStatus())) {
			return false;
		}
		if (!hasText(query.reconciliationStatus())) {
			return true;
		}
		ProjectAccountingAmounts accounting = accountingAmounts(query, row.projectId());
		BigDecimal difference = accounting.available() ? grossProfit(row).subtract(accounting.profit()) : null;
		String reconciliationStatus = accountingVisible
				? reconciliationStatus(difference, publicUnassigned, accounting.available(), amountVisible)
				: UNAVAILABLE;
		return query.reconciliationStatus().equalsIgnoreCase(reconciliationStatus);
	}

	private ProjectProfitDetailResponse projectProfitSnapshotDetail(Long projectId, OperatingFinanceQuery query) {
		JsonNode snapshot = snapshotResult(query.periodCode(), "PROJECT_PROFIT");
		JsonNode items = snapshot.get("items");
		if (items != null && items.isArray()) {
			for (JsonNode item : items) {
				if (projectId.equals(snapshotLong(item, "projectId")) && matchesProjectSnapshot(item, query)) {
					return projectProfitSnapshotDetail(item, query);
				}
			}
		}
		throw new BusinessException(ApiErrorCode.REPORT_SOURCE_INCOMPLETE);
	}

	private ProjectProfitDetailResponse projectProfitSnapshotDetail(JsonNode item, OperatingFinanceQuery query) {
		boolean amountVisible = projectProfitAmountVisible();
		boolean accountingVisible = projectProfitAccountingAmountVisible();
		boolean sourceVisible = projectProfitSourceVisible();
		boolean accountingFactsVisible = amountVisible && accountingVisible;
		String shipmentRevenue = snapshotVisible(item, "shipmentRevenue", amountVisible);
		String invoiceRevenue = snapshotVisible(item, "invoiceRevenue", amountVisible);
		String targetRevenue = snapshotVisible(item, "targetRevenue", amountVisible);
		String projectCost = snapshotVisible(item, "projectCostTotal", amountVisible);
		String grossProfit = snapshotVisible(item, "shipmentGrossMargin", amountVisible);
		String grossMarginRate = snapshotVisible(item, "shipmentGrossMarginRate", amountVisible);
		String accountingRevenue = snapshotVisible(item, "accountingRevenue", accountingFactsVisible);
		String accountingCost = snapshotVisible(item, "accountingCost", accountingFactsVisible);
		String accountingProfit = snapshotVisible(item, "accountingProfit", accountingFactsVisible);
		String difference = snapshotVisible(item, "differenceAmount", accountingFactsVisible);
		String completenessStatus = snapshotText(item, "completenessStatus");
		String finalityStatus = snapshotText(item, "finalityStatus");
		String reconciliationStatus = amountVisible
				? (accountingVisible ? snapshotText(item, "reconciliationStatus") : UNAVAILABLE) : RESTRICTED;
		String restrictedReason = amountVisible ? null : "缺少上游金额权限";
		List<CostStageEntryResponse> costStageEntries = List
			.of(new CostStageEntryResponse("TOTAL", projectCost, completenessStatus));
		List<RevenueEntryResponse> revenueEntries = List.of(
				new RevenueEntryResponse("SHIPMENT", shipmentRevenue, "发货经营收入"),
				new RevenueEntryResponse("INVOICE", invoiceRevenue, "开票辅助收入"),
				new RevenueEntryResponse("TARGET", targetRevenue, "项目目标收入"));
		List<AccountingEntryResponse> accountingEntries = accountingFactsVisible && accountingProfit != null
				? List.of(new AccountingEntryResponse(null, "项目收入", accountingRevenue, "PROJECT辅助收入"),
						new AccountingEntryResponse(null, "项目成本", accountingCost, "PROJECT辅助成本"),
						new AccountingEntryResponse(null, "项目利润", accountingProfit, "本期发生额"))
				: List.of();
		List<VarianceReasonResponse> varianceReasons = List.of(new VarianceReasonResponse(reconciliationStatus,
				"冻结快照管理口径与会计口径对照", difference));
		return new ProjectProfitDetailResponse(snapshotLong(item, "projectId"), snapshotText(item, "projectNo"),
				snapshotText(item, "projectName"), snapshotText(item, "customerName"),
				snapshotVisible(item, "shipmentRevenueAmount", amountVisible),
				snapshotVisible(item, "invoiceRevenueAmount", amountVisible),
				snapshotVisible(item, "targetRevenueAmount", amountVisible),
				snapshotVisible(item, "projectCostAmount", amountVisible),
				snapshotVisible(item, "operatingGrossProfitAmount", amountVisible),
				snapshotVisible(item, "operatingGrossProfitRate", amountVisible),
				snapshotVisible(item, "accountingRevenueAmount", accountingFactsVisible),
				snapshotVisible(item, "accountingCostAmount", accountingFactsVisible),
				snapshotVisible(item, "accountingProfitAmount", accountingFactsVisible), completenessStatus, FROZEN,
				reconciliationStatus, finalityStatus, sourceVisible ? snapshotInt(item, "sourceCount") : 0,
				sourceVisible ? snapshotText(item, "traceKey") : null, amountVisible, restrictedReason, difference,
				shipmentRevenue, invoiceRevenue, targetRevenue, projectCost, grossProfit, grossMarginRate,
				accountingRevenue, accountingCost, accountingProfit,
				new ManagementBasisResponse(shipmentRevenue, invoiceRevenue, targetRevenue, projectCost, grossProfit,
						grossMarginRate, completenessStatus),
				new AccountingBasisResponse(accountingRevenue, accountingCost, accountingProfit,
						accountingFactsVisible ? "0.00" : null, finalityStatus),
				costStageEntries, revenueEntries, accountingEntries, varianceReasons);
	}

	private String snapshotVisible(JsonNode item, String fieldName, boolean visible) {
		return visible ? snapshotText(item, fieldName) : null;
	}

	private boolean matchesProjectSnapshot(JsonNode item, OperatingFinanceQuery query) {
		Long projectId = snapshotLong(item, "projectId");
		if (query.projectId() != null && !query.projectId().equals(projectId)) {
			return false;
		}
		if (!anyContainsIgnoreCase(query.keyword(), snapshotText(item, "projectNo"), snapshotText(item, "projectName"),
				snapshotText(item, "customerName"))) {
			return false;
		}
		if (hasText(query.completenessStatus())
				&& !query.completenessStatus().equalsIgnoreCase(snapshotText(item, "completenessStatus"))) {
			return false;
		}
		if (hasText(query.reconciliationStatus())
				&& !query.reconciliationStatus().equalsIgnoreCase(snapshotText(item, "reconciliationStatus"))) {
			return false;
		}
		return !hasText(query.finalityStatus())
				|| query.finalityStatus().equalsIgnoreCase(snapshotText(item, "finalityStatus"));
	}

	private Map<String, Object> projectProfitSnapshotSummary(List<JsonNode> items, OperatingFinanceQuery query,
			boolean visible, boolean accountingVisible, boolean sourceVisible) {
		BigDecimal shipmentRevenue = sumDecimal(items, "shipmentRevenue");
		BigDecimal invoiceRevenue = sumDecimal(items, "invoiceRevenue");
		BigDecimal targetRevenue = sumDecimal(items, "targetRevenue");
		BigDecimal projectCost = sumDecimal(items, "projectCostTotal");
		BigDecimal grossProfit = sumDecimal(items, "shipmentGrossMargin");
		BigDecimal accountingRevenue = sumDecimal(items, "accountingRevenue");
		BigDecimal accountingCost = sumDecimal(items, "accountingCost");
		BigDecimal accountingProfit = sumDecimal(items, "accountingProfit");
		BigDecimal difference = sumDecimal(items, "differenceAmount");
		boolean accountingAvailable = items.stream().anyMatch((item) -> snapshotDecimal(item, "accountingProfit") != null);
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("projectCount", items.size());
		summary.put("shipmentRevenueAmount", visible ? amount(shipmentRevenue) : null);
		summary.put("invoiceRevenueAmount", visible ? amount(invoiceRevenue) : null);
		summary.put("targetRevenueAmount", visible ? amount(targetRevenue) : null);
		summary.put("projectCostAmount", visible ? amount(projectCost) : null);
		summary.put("operatingGrossProfitAmount", visible ? amount(grossProfit) : null);
		summary.put("accountingProfitAmount", visible && accountingVisible && accountingAvailable ? amount(accountingProfit) : null);
		summary.put("differenceAmount", visible && accountingVisible && accountingAvailable ? amount(difference) : null);
		summary.put("publicUnallocatedAmount", visible && accountingVisible ? "0.00" : null);
		summary.put("sourceCount", sourceVisible ? items.stream().mapToInt((item) -> snapshotInt(item, "sourceCount")).sum()
				: 0);
		summary.put("amountVisible", visible);
		summary.put("completenessStatus",
				items.isEmpty() ? UNAVAILABLE
						: items.stream().anyMatch((item) -> !"COMPLETE".equals(snapshotText(item, "completenessStatus")))
								? "INCOMPLETE" : "COMPLETE");
		summary.put("freshnessStatus", FROZEN);
		summary.put("reconciliationStatus",
				visible ? (accountingVisible && accountingAvailable ? "DIFFERENT" : UNAVAILABLE) : RESTRICTED);
		summary.put("finalityStatus", items.isEmpty() ? UNAVAILABLE : snapshotText(items.getFirst(), "finalityStatus"));
		summary.put("restrictedReason", visible ? null : "缺少上游金额权限");
		summary.put("shipmentRevenue", visible ? amount(shipmentRevenue) : null);
		summary.put("projectCostTotal", visible ? amount(projectCost) : null);
		summary.put("shipmentGrossMargin", visible ? amount(grossProfit) : null);
		summary.put("accountingProfit", visible && accountingVisible && accountingAvailable ? amount(accountingProfit) : null);
		summary.put("difference", visible && accountingVisible && accountingAvailable ? amount(difference) : null);
		summary.put("invoiceRevenue", visible ? amount(invoiceRevenue) : null);
		summary.put("targetRevenue", visible ? amount(targetRevenue) : null);
		summary.put("unassignedAccountingAmount", visible && accountingVisible ? "0.00" : null);
		summary.put("analysisMode", query.analysisMode());
		summary.put("accountingRevenue", visible && accountingVisible && accountingAvailable ? amount(accountingRevenue) : null);
		summary.put("accountingCost", visible && accountingVisible && accountingAvailable ? amount(accountingCost) : null);
		return summary;
	}

	private Map<String, Object> projectProfitSnapshotItem(JsonNode item, boolean visible, boolean accountingVisible,
			boolean sourceVisible) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("projectId", snapshotLong(item, "projectId"));
		result.put("projectNo", snapshotText(item, "projectNo"));
		result.put("projectName", snapshotText(item, "projectName"));
		result.put("customerName", snapshotText(item, "customerName"));
		for (String field : List.of("shipmentRevenueAmount", "invoiceRevenueAmount", "targetRevenueAmount",
				"projectCostAmount", "operatingGrossProfitAmount", "operatingGrossProfitRate", "shipmentRevenue",
				"invoiceRevenue", "targetRevenue", "projectCostTotal", "shipmentGrossMargin",
				"shipmentGrossMarginRate")) {
			result.put(field, visible ? snapshotText(item, field) : null);
		}
		for (String field : List.of("accountingRevenueAmount", "accountingCostAmount", "accountingProfitAmount",
				"differenceAmount", "accountingRevenue", "accountingCost", "accountingProfit")) {
			result.put(field, visible && accountingVisible ? snapshotText(item, field) : null);
		}
		result.put("completenessStatus", snapshotText(item, "completenessStatus"));
		result.put("freshnessStatus", FROZEN);
		result.put("reconciliationStatus",
				visible ? (accountingVisible ? snapshotText(item, "reconciliationStatus") : UNAVAILABLE) : RESTRICTED);
		result.put("finalityStatus", snapshotText(item, "finalityStatus"));
		result.put("sourceCount", sourceVisible ? snapshotInt(item, "sourceCount") : 0);
		result.put("traceKey", sourceVisible ? snapshotText(item, "traceKey") : null);
		result.put("amountVisible", visible);
		result.put("restrictedReason", visible ? null : "缺少上游金额权限");
		return result;
	}

	private List<ReportingAdminService.TraceSourceResponse> projectProfitTraceRows(long projectId,
			OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new java.util.ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select id, calculation_no, cutoff_date, status, project_cost_total
				from prj_cost_calculation
				where project_id = ?
				and cutoff_date between ? and ?
				and status in ('CALCULATED', 'CONFIRMED')
				order by cutoff_date desc, id desc
				""", (rs, rowNum) -> trace("PROJECT_COST_CALCULATION", rs.getLong("id"),
				rs.getString("calculation_no"), null, rs.getObject("cutoff_date", LocalDate.class),
				rs.getString("status"), null, rs.getBigDecimal("project_cost_total"), "cost:project-cost:view",
				"project-cost-calculation-detail", routeParams("id", rs.getLong("id"))), projectId, query.dateFrom(),
				query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select id, source_no, business_date, source_status, quantity, calculated_amount
				from prj_cost_source_line
				where project_id = ?
				and business_date between ? and ?
				order by business_date, id
				""", (rs, rowNum) -> trace("PROJECT_COST_SOURCE_LINE", rs.getLong("id"),
				rs.getString("source_no"), null, rs.getObject("business_date", LocalDate.class),
				rs.getString("source_status"), rs.getBigDecimal("quantity"), rs.getBigDecimal("calculated_amount"),
				"cost:project-cost:view", "project-cost-source-line", routeParams("id", rs.getLong("id"))),
				projectId, query.dateFrom(), query.dateTo()));
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
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

}
