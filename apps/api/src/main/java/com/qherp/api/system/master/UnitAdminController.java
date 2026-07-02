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
@RequestMapping("/api/admin/master/units")
public class UnitAdminController {

	private static final MasterDataAdminService.Resource RESOURCE = MasterDataAdminService.Resource.UNIT;

	private final MasterDataAdminService masterDataAdminService;

	public UnitAdminController(MasterDataAdminService masterDataAdminService) {
		this.masterDataAdminService = masterDataAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<MasterDataAdminService.UnitResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.masterDataAdminService.listUnits(RESOURCE, keyword, status, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<MasterDataAdminService.UnitResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.masterDataAdminService.getUnit(RESOURCE, id));
	}

	@PostMapping
	public ApiResponse<MasterDataAdminService.UnitResponse> create(
			@Valid @RequestBody MasterDataAdminService.UnitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.createUnit(RESOURCE, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<MasterDataAdminService.UnitResponse> update(@PathVariable Long id,
			@Valid @RequestBody MasterDataAdminService.UnitRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.updateUnit(RESOURCE, id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<MasterDataAdminService.UnitResponse> enable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.enableUnit(RESOURCE, id, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<MasterDataAdminService.UnitResponse> disable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.disableUnit(RESOURCE, id, currentUser, servletRequest));
	}

}
