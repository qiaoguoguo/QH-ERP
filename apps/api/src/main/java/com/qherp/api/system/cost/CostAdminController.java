package com.qherp.api.system.cost;

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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/cost")
public class CostAdminController {

	private final CostAdminService costAdminService;

	public CostAdminController(CostAdminService costAdminService) {
		this.costAdminService = costAdminService;
	}

	@GetMapping("/records")
	public ApiResponse<PageResponse<CostAdminService.CostRecordSummaryResponse>> records(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long workOrderId,
			@RequestParam(required = false) Long productMaterialId, @RequestParam(required = false) String costType,
			@RequestParam(required = false) String sourceType,
			@RequestParam(required = false) String sourceDocumentType,
			@RequestParam(required = false) String sourceDocumentNo,
			@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.costAdminService.records(keyword, workOrderId, productMaterialId, costType,
				sourceType, sourceDocumentType, sourceDocumentNo, dateFrom, dateTo, page, pageSize));
	}

	@GetMapping("/records/{id}")
	public ApiResponse<CostAdminService.CostRecordDetailResponse> record(@PathVariable Long id) {
		return ApiResponse.ok(this.costAdminService.record(id));
	}

	@PostMapping("/records")
	public ApiResponse<CostAdminService.CostRecordDetailResponse> createRecord(
			@RequestBody CostAdminService.CostRecordPayload request, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.costAdminService.createRecord(request, currentUser, servletRequest));
	}

	@PutMapping("/records/{id}")
	public ApiResponse<CostAdminService.CostRecordDetailResponse> updateRecord(@PathVariable Long id,
			@RequestBody CostAdminService.CostRecordPayload request, @AuthenticationPrincipal CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.costAdminService.updateRecord(id, request, currentUser, servletRequest));
	}

	@GetMapping("/work-orders/{workOrderId}/summary")
	public ApiResponse<CostAdminService.WorkOrderCostSummaryResponse> workOrderSummary(
			@PathVariable Long workOrderId) {
		return ApiResponse.ok(this.costAdminService.workOrderSummary(workOrderId));
	}

}
