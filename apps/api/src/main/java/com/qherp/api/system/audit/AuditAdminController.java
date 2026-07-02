package com.qherp.api.system.audit;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditAdminController {

	private final AuditService auditService;

	public AuditAdminController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<PageResponse<AuditService.AuditLogResponse>> list(
			@RequestParam(required = false) String operatorKeyword, @RequestParam(required = false) String targetType,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endAt,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.auditService.list(operatorKeyword, targetType, action, startAt, endAt, page, pageSize));
	}

}
