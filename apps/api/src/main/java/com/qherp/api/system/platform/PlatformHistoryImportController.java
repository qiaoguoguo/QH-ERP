package com.qherp.api.system.platform;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/platform")
public class PlatformHistoryImportController {

	private final PlatformHistoryImportService historyImportService;

	public PlatformHistoryImportController(PlatformHistoryImportService historyImportService) {
		this.historyImportService = historyImportService;
	}

	@GetMapping("/history-import-adapters")
	public ApiResponse<List<PlatformHistoryImportService.HistoryImportAdapterRecord>> adapters(
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.historyImportService.adapters(currentUser));
	}

	@GetMapping("/history-import-adapters/{code}/template")
	public ResponseEntity<byte[]> template(@PathVariable String code,
			@AuthenticationPrincipal CurrentUser currentUser) {
		PlatformHistoryImportService.TemplateFile template = this.historyImportService.template(code, currentUser);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(template.filename(), StandardCharsets.UTF_8).build()
						.toString())
			.contentType(MediaType.parseMediaType(template.contentType()))
			.body(template.content());
	}

	@GetMapping("/history-imports")
	public ApiResponse<PageResponse<PlatformHistoryImportService.HistoryImportRecord>> list(
			@RequestParam(required = false) String status, @RequestParam(required = false) String adapterCode,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.historyImportService.list(status, adapterCode, keyword, page, pageSize,
				currentUser));
	}

	@GetMapping("/history-imports/{id}")
	public ApiResponse<PlatformHistoryImportService.HistoryImportRecord> get(@PathVariable Long id,
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.historyImportService.get(id, currentUser));
	}

	@PostMapping("/history-imports/{code}")
	public ApiResponse<PlatformHistoryImportService.HistoryImportRecord> upload(@PathVariable String code,
			@RequestParam("file") MultipartFile file,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.historyImportService.upload(code, file, idempotencyKey, currentUser,
				servletRequest));
	}

	@PostMapping("/history-imports/{id}/confirm")
	public ApiResponse<PlatformHistoryImportService.HistoryImportRecord> confirm(@PathVariable Long id,
			@Valid @RequestBody PlatformHistoryImportService.ConfirmHistoryImportRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.historyImportService.confirm(id, request, currentUser, servletRequest));
	}

	@PostMapping("/history-imports/{id}/cancel")
	public ApiResponse<PlatformHistoryImportService.HistoryImportRecord> cancel(@PathVariable Long id,
			@Valid @RequestBody PlatformHistoryImportService.CancelHistoryImportRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.historyImportService.cancel(id, request, currentUser, servletRequest));
	}

}
