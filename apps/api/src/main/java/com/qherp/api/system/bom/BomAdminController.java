package com.qherp.api.system.bom;

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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/boms")
public class BomAdminController {

	private final BomAdminService bomAdminService;

	public BomAdminController(BomAdminService bomAdminService) {
		this.bomAdminService = bomAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<BomAdminService.BomSummaryResponse>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long parentMaterialId,
			@RequestParam(required = false) LocalDate effectiveDate,
			@RequestParam(required = false) Boolean includeHistory, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.bomAdminService.list(keyword, status, parentMaterialId, effectiveDate,
				includeHistory, page, pageSize));
	}

	@GetMapping("/material-candidates")
	public ApiResponse<com.qherp.api.system.master.UnitConversionAdminService.CandidatePage> materialCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String selectedIds,
			@RequestParam(required = false) String materialType, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.bomAdminService.materialCandidates(keyword, materialType, status, selectedIds,
				page, pageSize));
	}

	@GetMapping("/unit-candidates")
	public ApiResponse<com.qherp.api.system.master.UnitConversionAdminService.CandidatePage> unitCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String selectedIds,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.bomAdminService.unitCandidates(keyword, status, selectedIds, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<BomAdminService.BomDetailResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(this.bomAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<BomAdminService.BomDetailResponse> create(
			@Valid @RequestBody BomAdminService.BomRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<BomAdminService.BomDetailResponse> update(@PathVariable Long id,
			@Valid @RequestBody BomAdminService.BomRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.update(id, request, currentUser, servletRequest));
	}

	@PostMapping("/{id}/copy")
	public ApiResponse<BomAdminService.BomDetailResponse> copy(@PathVariable Long id,
			@Valid @RequestBody BomAdminService.BomCopyRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.copy(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<BomAdminService.BomDetailResponse> enable(@PathVariable Long id,
			@RequestBody(required = false) BomAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.enable(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<BomAdminService.BomDetailResponse> disable(@PathVariable Long id,
			@RequestBody(required = false) BomAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomAdminService.disable(id, request, currentUser, servletRequest));
	}

}
