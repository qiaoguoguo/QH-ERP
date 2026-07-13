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
@RequestMapping("/api/admin/coding-rules")
public class CodingRuleAdminController {

	private final CodingRuleAdminService codingRuleAdminService;

	public CodingRuleAdminController(CodingRuleAdminService codingRuleAdminService) {
		this.codingRuleAdminService = codingRuleAdminService;
	}

	@GetMapping
	public ApiResponse<PageResponse<CodingRuleAdminService.CodingRuleRecord>> list(
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String objectType,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.codingRuleAdminService.list(keyword, objectType, status, page, pageSize));
	}

	@GetMapping("/{id}")
	public ApiResponse<CodingRuleAdminService.CodingRuleRecord> get(@PathVariable Long id) {
		return ApiResponse.ok(this.codingRuleAdminService.get(id));
	}

	@PostMapping
	public ApiResponse<CodingRuleAdminService.CodingRuleRecord> create(
			@Valid @RequestBody CodingRuleAdminService.CodingRulePayload request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.codingRuleAdminService.create(request, currentUser, servletRequest));
	}

	@PutMapping("/{id}")
	public ApiResponse<CodingRuleAdminService.CodingRuleRecord> update(@PathVariable Long id,
			@Valid @RequestBody CodingRuleAdminService.CodingRulePayload request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.codingRuleAdminService.update(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/enable")
	public ApiResponse<CodingRuleAdminService.CodingRuleRecord> enable(@PathVariable Long id,
			@Valid @RequestBody CodingRuleAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.codingRuleAdminService.enable(id, request, currentUser, servletRequest));
	}

	@PutMapping("/{id}/disable")
	public ApiResponse<CodingRuleAdminService.CodingRuleRecord> disable(@PathVariable Long id,
			@Valid @RequestBody CodingRuleAdminService.VersionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.codingRuleAdminService.disable(id, request, currentUser, servletRequest));
	}

	@PostMapping("/generate")
	public ApiResponse<CodingRuleAdminService.GeneratedCode> generate(
			@Valid @RequestBody CodingRuleAdminService.GenerateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.codingRuleAdminService.generate(request, currentUser, servletRequest));
	}

}
