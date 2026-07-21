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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/platform")
public class PlatformBatchToolController {

	private final PlatformBatchToolService batchToolService;

	public PlatformBatchToolController(PlatformBatchToolService batchToolService) {
		this.batchToolService = batchToolService;
	}

	@GetMapping("/batch-tools")
	public ApiResponse<List<PlatformBatchToolService.BatchToolRecord>> tools(
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.batchToolService.tools(currentUser));
	}

	@PostMapping("/batch-tools/{code}/preview")
	public ApiResponse<PlatformBatchToolService.BatchOperationRecord> preview(@PathVariable String code,
			@Valid @RequestBody PlatformBatchToolService.BatchPreviewRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.batchToolService.preview(code, request, currentUser, servletRequest));
	}

	@GetMapping("/batch-operations")
	public ApiResponse<PageResponse<PlatformBatchToolService.BatchOperationRecord>> list(
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.batchToolService.list(status, page, pageSize, currentUser));
	}

	@GetMapping("/batch-operations/{id}")
	public ApiResponse<PlatformBatchToolService.BatchOperationRecord> get(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.batchToolService.get(id, currentUser));
	}

	@PostMapping("/batch-operations/{id}/execute")
	public ApiResponse<PlatformBatchToolService.BatchOperationRecord> execute(@PathVariable Long id,
			@Valid @RequestBody PlatformBatchToolService.BatchExecuteRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.batchToolService.execute(id, request, currentUser, servletRequest));
	}

}
