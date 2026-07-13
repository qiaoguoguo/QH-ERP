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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/master/unit-conversions")
public class UnitConversionAdminController {

	private final UnitConversionAdminService unitConversionAdminService;

	public UnitConversionAdminController(UnitConversionAdminService unitConversionAdminService) {
		this.unitConversionAdminService = unitConversionAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<UnitConversionAdminService.UnitConversionRecord>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Long materialId,
			@RequestParam(required = false) Long businessUnitId, @RequestParam(required = false) String status,
			@RequestParam(required = false) LocalDate effectiveDate, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.unitConversionAdminService.list(keyword, materialId, businessUnitId, status,
				effectiveDate, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<UnitConversionAdminService.UnitConversionRecord> get(@PathVariable Long id) {
		return ApiResponse.ok(this.unitConversionAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<UnitConversionAdminService.UnitConversionRecord> create(
			@Valid @RequestBody UnitConversionAdminService.UnitConversionPayload request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.unitConversionAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<UnitConversionAdminService.UnitConversionRecord> update(@PathVariable Long id,
			@Valid @RequestBody UnitConversionAdminService.UnitConversionPayload request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.unitConversionAdminService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<UnitConversionAdminService.UnitConversionRecord> enable(@PathVariable Long id,
			@Valid @RequestBody UnitConversionAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.unitConversionAdminService.enable(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<UnitConversionAdminService.UnitConversionRecord> disable(@PathVariable Long id,
			@Valid @RequestBody UnitConversionAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.unitConversionAdminService.disable(id, request, currentUser, servletRequest));
	}

	@PostMapping("/convert")
	public ApiResponse<UnitConversionAdminService.ConversionResult> convert(
			@Valid @RequestBody UnitConversionAdminService.ConversionRequest request) {
		return ApiResponse.ok(this.unitConversionAdminService.convert(request));
	}

	@GetMapping("/material-candidates")
	public ApiResponse<UnitConversionAdminService.CandidatePage> materialCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String selectedIds,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.unitConversionAdminService.materialCandidates(keyword, status, selectedIds, page,
				pageSize));
	}

	@GetMapping("/unit-candidates")
	public ApiResponse<UnitConversionAdminService.CandidatePage> unitCandidates(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String selectedIds,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.unitConversionAdminService.unitCandidates(keyword, status, selectedIds, page,
				pageSize));
	}

}
