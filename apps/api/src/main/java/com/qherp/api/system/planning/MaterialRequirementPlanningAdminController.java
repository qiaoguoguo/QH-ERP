package com.qherp.api.system.planning;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/planning")
public class MaterialRequirementPlanningAdminController {

	private final MaterialRequirementPlanningService planningService;

	public MaterialRequirementPlanningAdminController(MaterialRequirementPlanningService planningService) {
		this.planningService = planningService;
	}

	@PostMapping("/material-requirement-runs")
	public ApiResponse<MaterialRequirementPlanningService.RunResponse> calculate(
			@Valid @RequestBody MaterialRequirementPlanningService.RunRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.planningService.calculate(request, currentUser, servletRequest));
	}

	@GetMapping("/material-requirement-runs")
	public ApiResponse<PageResponse<MaterialRequirementPlanningService.RunResponse>> list(
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) Long contractId, @RequestParam(required = false) Long orderId,
			@RequestParam(required = false) Long materialId,
			@RequestParam(required = false) java.time.LocalDate requiredDateTo,
			@RequestParam(required = false) String status, @RequestParam(required = false) Boolean expired,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.planningService.list(
				new MaterialRequirementPlanningService.RunListFilter(projectId, customerId, contractId, orderId,
						materialId, requiredDateTo, status, expired),
				page, pageSize, currentUser));
	}

	@GetMapping("/material-requirement-runs/{id}")
	public ApiResponse<MaterialRequirementPlanningService.RunResponse> detail(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.planningService.detail(id, currentUser));
	}

	@PostMapping("/material-requirement-runs/{id}/recalculate")
	public ApiResponse<MaterialRequirementPlanningService.RunResponse> recalculate(@PathVariable Long id,
			@Valid @RequestBody MaterialRequirementPlanningService.RunRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.planningService.recalculate(id, request, currentUser, servletRequest));
	}

	@GetMapping("/material-requirement-runs/{id}/requirements")
	public ApiResponse<PageResponse<MaterialRequirementPlanningService.RequirementLineResponse>> requirements(
			@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Long materialId,
			@RequestParam(required = false) Boolean shortageOnly,
			@RequestParam(required = false) String coverageStatus, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.planningService.requirements(id,
				new MaterialRequirementPlanningService.RequirementLineFilter(materialId, shortageOnly, coverageStatus),
				page, pageSize, currentUser));
	}

	@GetMapping("/material-requirement-runs/{id}/allocations")
	public ApiResponse<PageResponse<MaterialRequirementPlanningService.SupplyAllocationResponse>> allocations(
			@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Long requirementLineId,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.planningService.allocations(id,
				new MaterialRequirementPlanningService.AllocationFilter(requirementLineId), page, pageSize,
				currentUser));
	}

	@GetMapping("/material-requirement-runs/{id}/suggestions")
	public ApiResponse<PageResponse<MaterialRequirementPlanningService.SuggestionResponse>> suggestions(
			@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String status,
			@RequestParam(required = false) String suggestionType, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.planningService.suggestions(id,
				new MaterialRequirementPlanningService.SuggestionFilter(status, suggestionType), page, pageSize,
				currentUser));
	}

	@GetMapping("/material-requirement-runs/{id}/substitute-hints")
	public ApiResponse<PageResponse<MaterialRequirementPlanningService.SubstituteHintResponse>> substituteHints(
			@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) Long requirementLineId,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.planningService.substituteHints(id,
				new MaterialRequirementPlanningService.SubstituteHintFilter(requirementLineId), page, pageSize,
				currentUser));
	}

	@PutMapping("/material-requirement-suggestions/{id}/confirm")
	public ApiResponse<MaterialRequirementPlanningService.SuggestionResponse> confirmSuggestion(@PathVariable Long id,
			@Valid @RequestBody MaterialRequirementPlanningService.SuggestionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.planningService.confirmSuggestion(id, request, currentUser, servletRequest));
	}

	@PutMapping("/material-requirement-suggestions/{id}/dismiss")
	public ApiResponse<MaterialRequirementPlanningService.SuggestionResponse> dismissSuggestion(@PathVariable Long id,
			@Valid @RequestBody MaterialRequirementPlanningService.SuggestionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.planningService.dismissSuggestion(id, request, currentUser, servletRequest));
	}

	@PostMapping("/material-requirement-suggestions/{id}/convert-requisition")
	public ApiResponse<MaterialRequirementPlanningService.SuggestionResponse> convertToRequisition(
			@PathVariable Long id, @Valid @RequestBody MaterialRequirementPlanningService.SuggestionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.planningService.convertToRequisition(id, request, currentUser, servletRequest));
	}

	@PostMapping("/material-requirement-suggestions/{id}/convert-work-order")
	public ApiResponse<MaterialRequirementPlanningService.SuggestionConversionResponse> convertToWorkOrder(
			@PathVariable Long id, @Valid @RequestBody MaterialRequirementPlanningService.SuggestionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.planningService.convertToWorkOrder(id, request, currentUser, servletRequest));
	}

	@PostMapping("/material-requirement-suggestions/{id}/convert-outsourcing-order")
	public ApiResponse<MaterialRequirementPlanningService.SuggestionConversionResponse> convertToOutsourcingOrder(
			@PathVariable Long id, @Valid @RequestBody MaterialRequirementPlanningService.SuggestionActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.planningService.convertToOutsourcingOrder(id, request, currentUser, servletRequest));
	}

}
