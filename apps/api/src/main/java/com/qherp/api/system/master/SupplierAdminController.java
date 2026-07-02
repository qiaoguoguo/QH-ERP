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
@RequestMapping("/api/admin/master/suppliers")
public class SupplierAdminController {

	private static final MasterDataAdminService.Resource RESOURCE = MasterDataAdminService.Resource.SUPPLIER;

	private final MasterDataAdminService masterDataAdminService;

	public SupplierAdminController(MasterDataAdminService masterDataAdminService) {
		this.masterDataAdminService = masterDataAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<MasterDataAdminService.PartnerResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.masterDataAdminService.listPartners(RESOURCE, keyword, status, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<MasterDataAdminService.PartnerResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.masterDataAdminService.getPartner(RESOURCE, id));
	}

	@PostMapping
	public ApiResponse<MasterDataAdminService.PartnerResponse> create(
			@Valid @RequestBody MasterDataAdminService.PartnerRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.createPartner(RESOURCE, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<MasterDataAdminService.PartnerResponse> update(@PathVariable Long id,
			@Valid @RequestBody MasterDataAdminService.PartnerRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.updatePartner(RESOURCE, id, request, currentUser,
				servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<MasterDataAdminService.PartnerResponse> enable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.enablePartner(RESOURCE, id, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<MasterDataAdminService.PartnerResponse> disable(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.masterDataAdminService.disablePartner(RESOURCE, id, currentUser, servletRequest));
	}

}
