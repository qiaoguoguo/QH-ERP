package com.qherp.api.system.period;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.List;

@RestController
@RequestMapping("/api/admin/system/business-periods")
public class BusinessPeriodAdminController {
	private final BusinessPeriodAdminService service;
	public BusinessPeriodAdminController(BusinessPeriodAdminService service) { this.service = service; }
	@GetMapping public ApiResponse<PageResponse<BusinessPeriodAdminService.BusinessPeriodRecord>> list(
			@RequestParam(required = false) String periodCode,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) { return ApiResponse.ok(this.service.list(periodCode, status, startDate, endDate, page, pageSize)); }
	@GetMapping("/resolve") public ApiResponse<BusinessPeriodAdminService.BusinessPeriodResolveResponse> resolve(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) { return ApiResponse.ok(this.service.resolve(businessDate)); }
	@PostMapping public ApiResponse<BusinessPeriodAdminService.BusinessPeriodRecord> create(@RequestBody BusinessPeriodAdminService.BusinessPeriodRequest request, @AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) { return ApiResponse.ok(this.service.create(request, user, servletRequest)); }
	@PutMapping("/{id}") public ApiResponse<BusinessPeriodAdminService.BusinessPeriodRecord> update(@PathVariable Long id, @RequestBody BusinessPeriodAdminService.BusinessPeriodRequest request, @AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) { return ApiResponse.ok(this.service.update(id, request, user, servletRequest)); }
	@PostMapping("/generate-monthly") public ApiResponse<List<BusinessPeriodAdminService.BusinessPeriodRecord>> generateMonthly(@RequestBody BusinessPeriodAdminService.GenerateMonthlyRequest request, @AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) { return ApiResponse.ok(this.service.generateMonthly(request, user, servletRequest)); }
	@PostMapping("/{id}/lock") public ApiResponse<BusinessPeriodAdminService.BusinessPeriodRecord> lock(@PathVariable Long id, @RequestBody BusinessPeriodAdminService.ReasonRequest request, @AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) { return ApiResponse.ok(this.service.lock(id, request, user, servletRequest)); }
	@PostMapping("/{id}/unlock") public ApiResponse<BusinessPeriodAdminService.BusinessPeriodRecord> unlock(@PathVariable Long id, @RequestBody BusinessPeriodAdminService.ReasonRequest request, @AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) { return ApiResponse.ok(this.service.unlock(id, request, user, servletRequest)); }
}
