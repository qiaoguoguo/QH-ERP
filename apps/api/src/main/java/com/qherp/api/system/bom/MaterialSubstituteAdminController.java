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
@RequestMapping("/api/admin/material-substitutes")
public class MaterialSubstituteAdminController {

	private final MaterialSubstituteAdminService materialSubstituteAdminService;

	public MaterialSubstituteAdminController(MaterialSubstituteAdminService materialSubstituteAdminService) {
		this.materialSubstituteAdminService = materialSubstituteAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<MaterialSubstituteAdminService.MaterialSubstituteRecord>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long mainMaterialId,
			@RequestParam(required = false) Long substituteMaterialId, @RequestParam(required = false) String scopeType,
			@RequestParam(required = false) Long scopeId, @RequestParam(required = false) LocalDate effectiveDate,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.materialSubstituteAdminService.list(keyword, status, mainMaterialId,
				substituteMaterialId, scopeType, scopeId, effectiveDate, page, pageSize));
	}

	@GetMapping("/material-candidates")
	public ApiResponse<com.qherp.api.system.master.UnitConversionAdminService.CandidatePage> materialCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String selectedIds,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.materialSubstituteAdminService.materialCandidates(keyword, status, selectedIds,
				page, pageSize));
	}

	@GetMapping("/bom-candidates")
	public ApiResponse<com.qherp.api.system.master.UnitConversionAdminService.CandidatePage> bomCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long parentMaterialId,
			@RequestParam(required = false) String selectedIds, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.materialSubstituteAdminService.bomCandidates(keyword, parentMaterialId,
				selectedIds, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<MaterialSubstituteAdminService.MaterialSubstituteRecord> get(@PathVariable Long id) {
		return ApiResponse.ok(this.materialSubstituteAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<MaterialSubstituteAdminService.MaterialSubstituteRecord> create(
			@Valid @RequestBody MaterialSubstituteAdminService.MaterialSubstituteRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialSubstituteAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<MaterialSubstituteAdminService.MaterialSubstituteRecord> update(@PathVariable Long id,
			@Valid @RequestBody MaterialSubstituteAdminService.MaterialSubstituteRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialSubstituteAdminService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<MaterialSubstituteAdminService.MaterialSubstituteRecord> enable(@PathVariable Long id,
			@RequestBody(required = false) MaterialSubstituteAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialSubstituteAdminService.enable(id, request == null
				? new MaterialSubstituteAdminService.VersionRequest(null) : request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<MaterialSubstituteAdminService.MaterialSubstituteRecord> disable(@PathVariable Long id,
			@RequestBody(required = false) MaterialSubstituteAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.materialSubstituteAdminService.disable(id, request == null
				? new MaterialSubstituteAdminService.VersionRequest(null) : request, currentUser, servletRequest));
	}

}
