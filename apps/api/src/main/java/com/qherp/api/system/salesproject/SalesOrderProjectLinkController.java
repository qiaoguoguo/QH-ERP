package com.qherp.api.system.salesproject;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SalesOrderProjectLinkController {

	private final SalesOrderProjectLinkService salesOrderProjectLinkService;

	public SalesOrderProjectLinkController(SalesOrderProjectLinkService salesOrderProjectLinkService) {
		this.salesOrderProjectLinkService = salesOrderProjectLinkService;
	}

	@GetMapping("/api/admin/sales-projects/order-link-candidates")
	public ApiResponse<PageResponse<SalesOrderProjectLinkService.OrderLinkCandidateResponse>> orderLinkCandidates(
			@RequestParam(required = false) Long customerId, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.salesOrderProjectLinkService.listOrderLinkCandidates(customerId, keyword, page,
				pageSize));
	}

	@GetMapping("/api/admin/sales-projects/{projectId}/sales-orders")
	public ApiResponse<PageResponse<SalesOrderProjectLinkService.ProjectSalesOrderResponse>> projectSalesOrders(
			@PathVariable Long projectId, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.salesOrderProjectLinkService.listProjectSalesOrders(projectId, keyword, status,
				page, pageSize, currentUser));
	}

}
