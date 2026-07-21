package com.qherp.api.system.reporting;

import com.qherp.api.system.reporting.ReportingStage033Service.OperatingFinanceOverviewResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ContractCollectionSummaryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.InventoryCapitalSummaryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ProcurementVarianceSummaryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ProjectProfitSummaryResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ReceivablePayableSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@Transactional(readOnly = true)
class OperatingFinanceOverviewQueryService extends ReportingStage033QuerySupport {

	private final ProjectProfitReportQueryService projectProfitReportQueryService;

	private final ContractCollectionReportQueryService contractCollectionReportQueryService;

	private final ProcurementVarianceReportQueryService procurementVarianceReportQueryService;

	private final InventoryCapitalReportQueryService inventoryCapitalReportQueryService;

	private final ReceivablePayableReportQueryService receivablePayableReportQueryService;

	OperatingFinanceOverviewQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			ProjectProfitReportQueryService projectProfitReportQueryService,
			ContractCollectionReportQueryService contractCollectionReportQueryService,
			ProcurementVarianceReportQueryService procurementVarianceReportQueryService,
			InventoryCapitalReportQueryService inventoryCapitalReportQueryService,
			ReceivablePayableReportQueryService receivablePayableReportQueryService) {
		super(jdbcTemplate, objectMapper);
		this.projectProfitReportQueryService = projectProfitReportQueryService;
		this.contractCollectionReportQueryService = contractCollectionReportQueryService;
		this.procurementVarianceReportQueryService = procurementVarianceReportQueryService;
		this.inventoryCapitalReportQueryService = inventoryCapitalReportQueryService;
		this.receivablePayableReportQueryService = receivablePayableReportQueryService;
	}

	Object operatingFinanceOverview(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, false);
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			PeriodState state = periodState(query);
			return new OperatingFinanceOverviewResponse(query.periodCode(), BUSINESS_SNAPSHOT,
					state.businessPeriodStatus(), state.accountingPeriodStatus(), state.financialCloseStatus(),
					UNAVAILABLE, "LEGACY_NOT_INCLUDED", null, null, null, null, null, null,
					"经营财务分析总览不进入业务月结快照", 0, reportEntries());
		}
		PeriodState state = periodState(query);
		ProjectProfitSummaryResponse projectProfit = this.projectProfitReportQueryService.summaryForOverview(query,
				state);
		ContractCollectionSummaryResponse contractCollection = this.contractCollectionReportQueryService
			.summaryForOverview(query);
		ProcurementVarianceSummaryResponse procurementVariance = this.procurementVarianceReportQueryService
			.summaryForOverview(query);
		InventoryCapitalSummaryResponse inventoryCapital = this.inventoryCapitalReportQueryService
			.summaryForOverview(query);
		ReceivablePayableSummaryResponse receivablePayable = this.receivablePayableReportQueryService
			.summaryForOverview(query);
		String overviewFreshness = List
			.of(projectProfit.freshnessStatus(), contractCollection.freshnessStatus(),
					procurementVariance.freshnessStatus(), inventoryCapital.freshnessStatus(),
					receivablePayable.freshnessStatus())
			.contains(STALE) ? STALE : freshnessStatus(query);
		return new OperatingFinanceOverviewResponse(query.periodCode(), LIVE, state.businessPeriodStatus(),
				state.accountingPeriodStatus(), state.financialCloseStatus(), state.finalityStatus(), overviewFreshness,
				projectProfit.operatingGrossProfitAmount(), contractCollection.unreceivedAmount(),
				procurementVariance.matchVarianceAmount(), inventoryCapital.amount(), receivablePayable.balanceAmount(),
				projectProfit.differenceAmount(), projectProfit.amountVisible() ? null : "缺少上游金额权限",
				projectProfit.sourceCount() + contractCollection.sourceCount() + procurementVariance.sourceCount()
						+ inventoryCapital.sourceCount() + receivablePayable.sourceCount(),
				reportEntries());
	}

}
