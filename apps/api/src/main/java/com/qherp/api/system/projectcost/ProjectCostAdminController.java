package com.qherp.api.system.projectcost;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cost")
public class ProjectCostAdminController {

	private final ProjectCostCalculationService calculationService;

	private final ProjectCostQueryService queryService;

	private final ProjectCostAdjustmentService adjustmentService;

	public ProjectCostAdminController(ProjectCostCalculationService calculationService,
			ProjectCostQueryService queryService, ProjectCostAdjustmentService adjustmentService) {
		this.calculationService = calculationService;
		this.queryService = queryService;
		this.adjustmentService = adjustmentService;
	}

	@GetMapping("/project-costs")
	public ApiResponse<PageResponse<ProjectCostQueryService.CalculationResponse>> calculations(
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.calculations(projectId, status, page, pageSize, currentUser));
	}

	@GetMapping("/project-costs/projects/{projectId}")
	public ApiResponse<ProjectCostQueryService.CalculationResponse> projectCalculation(@PathVariable Long projectId,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.projectCalculation(projectId, currentUser));
	}

	@PostMapping("/project-costs/projects/{projectId}/calculations")
	public ApiResponse<ProjectCostQueryService.CalculationResponse> calculate(@PathVariable Long projectId,
			@RequestBody ProjectCostCalculationService.CalculationRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.calculationService.calculate(projectId, request, currentUser, servletRequest));
	}

	@GetMapping("/project-cost-calculations/{id}")
	public ApiResponse<ProjectCostQueryService.CalculationResponse> calculation(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.calculation(id, currentUser));
	}

	@GetMapping("/project-cost-calculations/{id}/sources")
	public ApiResponse<PageResponse<ProjectCostQueryService.SourceLineResponse>> sources(@PathVariable Long id,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.sources(id, page, pageSize, currentUser));
	}

	@GetMapping("/project-cost-calculations/{id}/entries")
	public ApiResponse<PageResponse<ProjectCostQueryService.EntryResponse>> entries(@PathVariable Long id,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int pageSize) {
		return ApiResponse.ok(this.queryService.entries(id, page, pageSize));
	}

	@GetMapping("/project-cost-calculations/{id}/variances")
	public ApiResponse<PageResponse<ProjectCostQueryService.VarianceResponse>> calculationVariances(
			@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "100") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.variances(id, page, pageSize, currentUser));
	}

	@GetMapping("/project-cost-variances")
	public ApiResponse<PageResponse<ProjectCostQueryService.VarianceResponse>> variances(
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) String severity,
			@RequestParam(required = false) String type, @RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean sourceRestricted, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.queryService.variances(projectId, severity, type, status, sourceRestricted, page,
				pageSize, currentUser));
	}

	@PutMapping("/project-cost-calculations/{id}/recalculate")
	public ApiResponse<ProjectCostQueryService.CalculationResponse> recalculate(@PathVariable Long id,
			@RequestBody ProjectCostCalculationService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.calculationService.recalculate(id, request, currentUser, servletRequest));
	}

	@PutMapping("/project-cost-calculations/{id}/confirm")
	public ApiResponse<ProjectCostQueryService.CalculationResponse> confirm(@PathVariable Long id,
			@RequestBody ProjectCostCalculationService.ConfirmRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.calculationService.confirm(id, request, currentUser, servletRequest));
	}

	@PutMapping("/project-cost-calculations/{id}/cancel")
	public ApiResponse<ProjectCostQueryService.CalculationResponse> cancelCalculation(@PathVariable Long id,
			@RequestBody ProjectCostCalculationService.VersionedActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.calculationService.cancel(id, request, currentUser, servletRequest));
	}

	@GetMapping("/project-cost-adjustments/candidates/public-expenses")
	public ApiResponse<PageResponse<ProjectCostAdjustmentService.PublicExpenseCandidateResponse>> publicExpenses(
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.adjustmentService.publicExpenseCandidates(keyword, page, pageSize, currentUser));
	}

	@GetMapping("/project-cost-adjustments")
	public ApiResponse<PageResponse<ProjectCostAdjustmentService.AdjustmentResponse>> adjustments(
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.adjustmentService.list(status, page, pageSize, currentUser));
	}

	@PostMapping("/project-cost-adjustments")
	public ApiResponse<ProjectCostAdjustmentService.AdjustmentResponse> createAdjustment(
			@RequestBody ProjectCostAdjustmentService.AdjustmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.adjustmentService.create(request, currentUser, servletRequest));
	}

	@GetMapping("/project-cost-adjustments/{id}")
	public ApiResponse<ProjectCostAdjustmentService.AdjustmentResponse> adjustment(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.adjustmentService.get(id, currentUser));
	}

	@PutMapping("/project-cost-adjustments/{id}")
	public ApiResponse<ProjectCostAdjustmentService.AdjustmentResponse> updateAdjustment(@PathVariable Long id,
			@RequestBody ProjectCostAdjustmentService.AdjustmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.adjustmentService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/project-cost-adjustments/{id}/submit")
	public ApiResponse<ProjectCostAdjustmentService.AdjustmentResponse> submitAdjustment(@PathVariable Long id,
			@RequestBody ProjectCostAdjustmentService.SubmitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.adjustmentService.submit(id, request, currentUser, servletRequest));
	}

	@PutMapping("/project-cost-adjustments/{id}/cancel")
	public ApiResponse<ProjectCostAdjustmentService.AdjustmentResponse> cancelAdjustment(@PathVariable Long id,
			@RequestBody ProjectCostAdjustmentService.VersionedRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.adjustmentService.cancel(id, request, currentUser, servletRequest));
	}

}
