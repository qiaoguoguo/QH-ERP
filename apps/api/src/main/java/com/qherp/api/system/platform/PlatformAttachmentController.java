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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/attachments")
public class PlatformAttachmentController {

	private final PlatformAttachmentService attachmentService;

	public PlatformAttachmentController(PlatformAttachmentService attachmentService) {
		this.attachmentService = attachmentService;
	}

	@GetMapping
	public ApiResponse<PageResponse<PlatformAttachmentService.AttachmentRecord>> list(
			@RequestParam String objectType, @RequestParam Long objectId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize, @AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.attachmentService.list(objectType, objectId, page, pageSize, currentUser));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<PlatformAttachmentService.AttachmentRecord> upload(@RequestParam String objectType,
			@RequestParam Long objectId, @RequestParam(required = false) String description,
			@RequestParam(required = false) String idempotencyKey, @RequestParam MultipartFile file,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) throws IOException {
		String contentType = contentType(file, servletRequest);
		return ApiResponse.ok(this.attachmentService.upload(
				new PlatformAttachmentService.AttachmentUpload(objectType, objectId, file.getOriginalFilename(),
						contentType, file.getBytes(), description, idempotencyKey),
				currentUser, servletRequest));
	}

	@GetMapping("/{id}/download")
	public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
		PlatformAttachmentService.DownloadedFile file = this.attachmentService.download(id, currentUser);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment()
						.filename(file.originalFilename(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.contentType(MediaType.parseMediaType(file.contentType()))
			.body(file.content());
	}

	@PutMapping("/{id}/delete")
	public ApiResponse<PlatformAttachmentService.AttachmentRecord> delete(@PathVariable Long id,
			@Valid @RequestBody PlatformAttachmentService.DeleteAttachmentRequest request,
			@AuthenticationPrincipal CurrentUser currentUser, HttpServletRequest servletRequest) {
		return ApiResponse.ok(this.attachmentService.delete(id, request, currentUser, servletRequest));
	}

	private String contentType(MultipartFile file, HttpServletRequest request) {
		String header = request.getHeader("X-QHERP-Test-Content-Type");
		if (header != null && !header.isBlank()) {
			return header;
		}
		String contentType = file.getContentType();
		return contentType == null || contentType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
	}

}
