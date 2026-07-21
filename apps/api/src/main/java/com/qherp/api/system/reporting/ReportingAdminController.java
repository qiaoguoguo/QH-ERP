package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportingAdminController {

	private final ReportingAdminService reportingAdminService;

	private final ReportingStage033Service reportingStage033Service;

	public ReportingAdminController(ReportingAdminService reportingAdminService,
			ReportingStage033Service reportingStage033Service) {
		this.reportingAdminService = reportingAdminService;
		this.reportingStage033Service = reportingStage033Service;
	}

	@GetMapping("/overview")
	public ApiResponse<ReportingAdminService.OverviewResponse> overview(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.overview(parameters));
	}

	@GetMapping("/sales-summary")
	public ApiResponse<ReportingAdminService.ReportPageResponse<Object>> salesSummary(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.salesSummary(parameters));
	}

	@GetMapping("/sales-summary/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> salesTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.salesTraces(parameters));
	}

	@GetMapping("/procurement-summary")
	public ApiResponse<ReportingAdminService.ReportPageResponse<Object>> procurementSummary(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.procurementSummary(parameters));
	}

	@GetMapping("/procurement-summary/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> procurementTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.procurementTraces(parameters));
	}

	@GetMapping("/inventory-stock-flow")
	public ApiResponse<ReportingAdminService.ReportPageResponse<Object>> inventoryStockFlow(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.inventoryStockFlow(parameters));
	}

	@GetMapping("/inventory-stock-flow/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> inventoryStockFlowTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.inventoryStockFlowTraces(parameters));
	}

	@GetMapping("/production-execution")
	public ApiResponse<ReportingAdminService.ReportPageResponse<Object>> productionExecution(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.productionExecution(parameters));
	}

	@GetMapping("/production-execution/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> productionExecutionTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.productionExecutionTraces(parameters));
	}

	@GetMapping("/cost-collection")
	public ApiResponse<ReportingAdminService.ReportPageResponse<Object>> costCollection(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.costCollection(parameters));
	}

	@GetMapping("/cost-collection/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> costCollectionTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.costCollectionTraces(parameters));
	}

	@GetMapping("/settlement-summary")
	public ApiResponse<ReportingAdminService.ReportPageResponse<Object>> settlementSummary(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.settlementSummary(parameters));
	}

	@GetMapping("/settlement-summary/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> settlementSummaryTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.settlementSummaryTraces(parameters));
	}

	@GetMapping("/exceptions")
	public ApiResponse<ReportingAdminService.ReportPageResponse<Object>> exceptions(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.exceptions(parameters));
	}

	@GetMapping("/exceptions/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> exceptionTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingAdminService.exceptionTraces(parameters));
	}

	@GetMapping("/operating-finance-overview")
	public ApiResponse<Object> operatingFinanceOverview(@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.operatingFinanceOverview(parameters));
	}

	@GetMapping("/project-profit")
	public ApiResponse<Object> projectProfit(@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.projectProfit(parameters));
	}

	@GetMapping("/project-profit/{projectId}")
	public ApiResponse<Object> projectProfitDetail(@PathVariable Long projectId,
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.projectProfitDetail(projectId, parameters));
	}

	@GetMapping("/project-profit/{projectId}/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> projectProfitTraces(
			@PathVariable Long projectId, @RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.projectProfitTraces(projectId, parameters));
	}

	@GetMapping("/contract-collections")
	public ApiResponse<Object> contractCollections(@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.contractCollections(parameters));
	}

	@GetMapping("/contract-collections/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> contractCollectionTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.contractCollectionTraces(parameters));
	}

	@GetMapping("/procurement-variances")
	public ApiResponse<Object> procurementVariances(@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.procurementVariances(parameters));
	}

	@GetMapping("/procurement-variances/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> procurementVarianceTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.procurementVarianceTraces(parameters));
	}

	@GetMapping("/inventory-capital")
	public ApiResponse<Object> inventoryCapital(@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.inventoryCapital(parameters));
	}

	@GetMapping("/inventory-capital/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> inventoryCapitalTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.inventoryCapitalTraces(parameters));
	}

	@GetMapping("/receivable-payable")
	public ApiResponse<Object> receivablePayable(@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.receivablePayable(parameters));
	}

	@GetMapping("/receivable-payable/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> receivablePayableTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.receivablePayableTraces(parameters));
	}

	@GetMapping("/operating-accounting-reconciliation")
	public ApiResponse<Object> operatingAccountingReconciliation(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.operatingAccountingReconciliation(parameters));
	}

	@GetMapping("/operating-accounting-reconciliation/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> operatingAccountingTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.operatingAccountingTraces(parameters));
	}

	@GetMapping("/financial-summary")
	public ApiResponse<Object> financialSummary(@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.financialSummary(parameters));
	}

	@GetMapping("/financial-summary/traces")
	public ApiResponse<PageResponse<ReportingAdminService.TraceSourceResponse>> financialSummaryTraces(
			@RequestParam MultiValueMap<String, String> parameters) {
		return ApiResponse.ok(this.reportingStage033Service.financialSummaryTraces(parameters));
	}

}
