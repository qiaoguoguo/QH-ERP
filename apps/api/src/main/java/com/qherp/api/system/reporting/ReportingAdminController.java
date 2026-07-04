package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportingAdminController {

	private final ReportingAdminService reportingAdminService;

	public ReportingAdminController(ReportingAdminService reportingAdminService) {
		this.reportingAdminService = reportingAdminService;
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

}
