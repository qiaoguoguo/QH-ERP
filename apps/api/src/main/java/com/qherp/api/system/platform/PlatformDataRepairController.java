package com.qherp.api.system.platform;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/platform")
public class PlatformDataRepairController {

	private final PlatformDataRepairService dataRepairService;

	public PlatformDataRepairController(PlatformDataRepairService dataRepairService) {
		this.dataRepairService = dataRepairService;
	}

	@GetMapping("/data-repair-adapters")
	public ApiResponse<List<PlatformDataRepairService.DataRepairAdapterRecord>> adapters(
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.dataRepairService.adapters(currentUser));
	}

	@GetMapping("/data-repairs")
	public ApiResponse<PageResponse<PlatformDataRepairService.DataRepairRecord>> list(
			@RequestParam(required = false) String status, @RequestParam(required = false) String targetObjectType,
			@RequestParam(required = false) String adapterCode, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.dataRepairService.list(status, targetObjectType, adapterCode, keyword, page,
				pageSize, currentUser));
	}

	@PostMapping("/data-repairs")
	public ApiResponse<PlatformDataRepairService.DataRepairRecord> create(
			@Valid @RequestBody PlatformDataRepairService.DataRepairCreateRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.dataRepairService.create(request, idempotencyKey, currentUser, servletRequest));
	}

	@GetMapping("/data-repairs/{id}")
	public ApiResponse<PlatformDataRepairService.DataRepairRecord> get(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.dataRepairService.get(id, currentUser));
	}

	@PutMapping("/data-repairs/{id}")
	public ApiResponse<PlatformDataRepairService.DataRepairRecord> update(@PathVariable Long id,
			@Valid @RequestBody PlatformDataRepairService.DataRepairUpdateRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.dataRepairService.updateDraft(id, request, currentUser, servletRequest));
	}

	@PostMapping("/data-repairs/{id}/submit")
	public ApiResponse<PlatformDataRepairService.DataRepairRecord> submit(@PathVariable Long id,
			@Valid @RequestBody PlatformDataRepairService.DataRepairActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.dataRepairService.submit(id, request, currentUser, servletRequest));
	}

	@PostMapping("/data-repairs/{id}/execute")
	public ApiResponse<PlatformDataRepairService.DataRepairRecord> execute(@PathVariable Long id,
			@Valid @RequestBody PlatformDataRepairService.DataRepairActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.dataRepairService.execute(id, request, currentUser, servletRequest));
	}

	@PostMapping("/data-repairs/{id}/verify")
	public ApiResponse<PlatformDataRepairService.DataRepairRecord> verify(@PathVariable Long id,
			@Valid @RequestBody PlatformDataRepairService.DataRepairActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.dataRepairService.verify(id, request, currentUser, servletRequest));
	}

	@PostMapping("/data-repairs/{id}/cancel")
	public ApiResponse<PlatformDataRepairService.DataRepairRecord> cancel(@PathVariable Long id,
			@Valid @RequestBody PlatformDataRepairService.DataRepairActionRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.dataRepairService.cancel(id, request, currentUser, servletRequest));
	}

}
