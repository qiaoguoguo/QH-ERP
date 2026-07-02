package com.qherp.api.system.audit;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditAdminController {

	private final AuditService auditService;

	public AuditAdminController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<PageResponse<AuditService.AuditLogResponse>> list(
			@RequestParam(required = false) String action, @RequestParam(required = false) String targetType,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(this.auditService.list(action, targetType, page, pageSize));
	}

}
