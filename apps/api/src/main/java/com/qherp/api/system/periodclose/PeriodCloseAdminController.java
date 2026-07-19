package com.qherp.api.system.periodclose;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/period-closes")
public class PeriodCloseAdminController {

	private final PeriodCloseService service;

	public PeriodCloseAdminController(PeriodCloseService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<PageResponse<PeriodCloseService.PeriodSummaryResponse>> list(
			@RequestParam(required = false) String periodCode,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String closeStatus,
			@RequestParam(required = false) String checkResult,
			@RequestParam(required = false) Boolean hasBlocking,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		String statusFilter = status == null ? closeStatus : status;
		return ApiResponse.ok(this.service.list(periodCode, statusFilter, checkResult, hasBlocking, startDate,
				endDate, page, pageSize, currentUser));
	}

	@GetMapping("/periods/{periodId}")
	public ApiResponse<PeriodCloseService.PeriodSummaryResponse> period(@PathVariable Long periodId,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.period(periodId, currentUser));
	}

	@PostMapping("/checks")
	public ApiResponse<PeriodCloseService.RunResponse> check(
			@RequestBody PeriodCloseService.CheckRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.service.check(request, currentUser, servletRequest));
	}

	@GetMapping("/{runId}")
	public ApiResponse<PeriodCloseService.RunResponse> run(@PathVariable Long runId,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.run(runId, currentUser));
	}

	@GetMapping("/{runId}/checks")
	public ApiResponse<PageResponse<PeriodCloseService.CheckRunResponse>> checks(@PathVariable Long runId,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.checks(runId, page, pageSize, currentUser));
	}

	@GetMapping("/{runId}/checks/{checkRunId}/items")
	public ApiResponse<PageResponse<PeriodCloseService.CheckItemResponse>> checkItems(@PathVariable Long runId,
			@PathVariable Long checkRunId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.checkItems(runId, checkRunId, page, pageSize, currentUser));
	}

	@PostMapping("/{runId}/close")
	public ApiResponse<PeriodCloseService.RunResponse> close(@PathVariable Long runId,
			@RequestBody PeriodCloseService.CloseRequest request, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.service.close(runId, request, currentUser, servletRequest));
	}

	@PostMapping("/{runId}/reopen")
	public ApiResponse<PeriodCloseService.RunResponse> reopen(@PathVariable Long runId,
			@RequestBody PeriodCloseService.ReopenRequest request, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.service.reopen(runId, request, currentUser, servletRequest));
	}

	@GetMapping("/{runId}/snapshot")
	public ApiResponse<PeriodCloseService.SnapshotOverviewResponse> snapshot(@PathVariable Long runId,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.snapshot(runId, currentUser));
	}

	@GetMapping("/{runId}/snapshot/inventory")
	public ApiResponse<PageResponse<PeriodCloseService.InventorySnapshotResponse>> inventorySnapshot(
			@PathVariable Long runId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.inventorySnapshot(runId, page, pageSize, currentUser));
	}

	@GetMapping("/{runId}/snapshot/wip")
	public ApiResponse<PageResponse<PeriodCloseService.WipSnapshotResponse>> wipSnapshot(@PathVariable Long runId,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.wipSnapshot(runId, page, pageSize, currentUser));
	}

	@GetMapping("/{runId}/snapshot/project-costs")
	public ApiResponse<PageResponse<PeriodCloseService.ProjectCostSnapshotResponse>> projectCostSnapshot(
			@PathVariable Long runId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.projectCostSnapshot(runId, page, pageSize, currentUser));
	}

	@GetMapping("/{runId}/snapshot/reports/{reportCode}")
	public ApiResponse<PeriodCloseService.ReportSnapshotResponse> reportSnapshot(@PathVariable Long runId,
			@PathVariable String reportCode, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.service.reportSnapshot(runId, reportCode, currentUser));
	}

}
