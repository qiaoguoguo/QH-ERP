package com.qherp.api.system.bom;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.master.UnitConversionAdminService;
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
@RequestMapping("/api/admin/bom-engineering-changes")
public class BomEngineeringChangeAdminController {

	private final BomEngineeringChangeAdminService bomEngineeringChangeAdminService;

	public BomEngineeringChangeAdminController(BomEngineeringChangeAdminService bomEngineeringChangeAdminService) {
		this.bomEngineeringChangeAdminService = bomEngineeringChangeAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<BomEngineeringChangeAdminService.EngineeringChangeRecord>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long parentMaterialId, @RequestParam(required = false) Long sourceBomId,
			@RequestParam(required = false) Long targetBomId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.list(keyword, status, parentMaterialId,
				sourceBomId, targetBomId, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<BomEngineeringChangeAdminService.EngineeringChangeRecord> get(@PathVariable Long id) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<BomEngineeringChangeAdminService.EngineeringChangeRecord> create(
			@Valid @RequestBody BomEngineeringChangeAdminService.EngineeringChangeRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<BomEngineeringChangeAdminService.EngineeringChangeRecord> update(@PathVariable Long id,
			@Valid @RequestBody BomEngineeringChangeAdminService.EngineeringChangeRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/apply")
	public ApiResponse<BomEngineeringChangeAdminService.EngineeringChangeRecord> apply(@PathVariable Long id,
			@Valid @RequestBody BomEngineeringChangeAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.apply(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/cancel")
	public ApiResponse<BomEngineeringChangeAdminService.EngineeringChangeRecord> cancel(@PathVariable Long id,
			@Valid @RequestBody BomEngineeringChangeAdminService.CancelRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.cancel(id, request, currentUser, servletRequest));
	}

	@GetMapping("/source-bom-candidates")
	public ApiResponse<UnitConversionAdminService.CandidatePage> sourceBomCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long parentMaterialId,
			@RequestParam(required = false) String selectedIds, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.sourceBomCandidates(keyword, parentMaterialId,
				selectedIds, page, pageSize));
	}

	@GetMapping("/target-bom-candidates")
	public ApiResponse<UnitConversionAdminService.CandidatePage> targetBomCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long sourceBomId,
			@RequestParam(required = false) String selectedIds, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.bomEngineeringChangeAdminService.targetBomCandidates(keyword, sourceBomId,
				selectedIds, page, pageSize));
	}

}
