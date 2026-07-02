package com.qherp.api.system.master;

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
@RequestMapping("/api/admin/master/warehouses")
public class WarehouseAdminController {

	private static final MasterDataAdminService.Resource RESOURCE = MasterDataAdminService.Resource.WAREHOUSE;

	private final MasterDataAdminService masterDataAdminService;

	public WarehouseAdminController(MasterDataAdminService masterDataAdminService) {
		this.masterDataAdminService = masterDataAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<MasterDataAdminService.WarehouseResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.masterDataAdminService.listWarehouses(RESOURCE, keyword, status, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<MasterDataAdminService.WarehouseResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.masterDataAdminService.getWarehouse(RESOURCE, id));
	}

	@PostMapping
	public ApiResponse<MasterDataAdminService.WarehouseResponse> create(
			@Valid @RequestBody MasterDataAdminService.WarehouseRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.createWarehouse(RESOURCE, request, currentUser,
				servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<MasterDataAdminService.WarehouseResponse> update(@PathVariable Long id,
			@Valid @RequestBody MasterDataAdminService.WarehouseRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.updateWarehouse(RESOURCE, id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<MasterDataAdminService.WarehouseResponse> enable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.enableWarehouse(RESOURCE, id, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<MasterDataAdminService.WarehouseResponse> disable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.disableWarehouse(RESOURCE, id, currentUser, servletRequest));
	}

}
