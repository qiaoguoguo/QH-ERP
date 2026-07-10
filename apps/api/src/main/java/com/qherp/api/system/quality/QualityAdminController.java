package com.qherp.api.system.quality;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
public class QualityAdminController {

	private final QualityAdminService qualityAdminService;

	public QualityAdminController(QualityAdminService qualityAdminService) {
		this.qualityAdminService = qualityAdminService;
	}

	@GetMapping("/quality/inspections")
	public ApiResponse<PageResponse<QualityAdminService.QualityInspectionResponse>> inspections(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) String status, @RequestParam(required = false) String qualityStatus,
			@RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Long materialId,
			@RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.qualityAdminService.inspections(keyword, sourceType, status, qualityStatus,
				warehouseId, materialId, dateFrom, dateTo, page, pageSize));
	}

	@GetMapping("/quality/inspections/{id}")
	public ApiResponse<QualityAdminService.QualityInspectionResponse> inspection(@PathVariable Long id) {
		return ApiResponse.ok(this.qualityAdminService.inspection(id));
	}

	@PostMapping("/quality/inspections/{id}/process")
	public ApiResponse<QualityAdminService.QualityInspectionResponse> processInspection(@PathVariable Long id,
			@Valid @RequestBody QualityAdminService.QualityInspectionProcessRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.qualityAdminService.processInspection(id, request, currentUser, servletRequest));
	}

	@PostMapping("/inventory/quality-transfers/freeze")
	public ApiResponse<QualityAdminService.QualityStatusTransferResponse> freeze(
			@Valid @RequestBody QualityAdminService.QualityStatusTransferRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.qualityAdminService.freeze(request, currentUser, servletRequest));
	}

	@PostMapping("/inventory/quality-transfers/unfreeze")
	public ApiResponse<QualityAdminService.QualityStatusTransferResponse> unfreeze(
			@Valid @RequestBody QualityAdminService.QualityStatusTransferRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.qualityAdminService.unfreeze(request, currentUser, servletRequest));
	}

}
