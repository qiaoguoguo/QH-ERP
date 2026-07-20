package com.qherp.api.security;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.ApiResponse;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	private static final String ACTION_PERMISSION_DENIED = "PERMISSION_DENIED";

	private static final String TARGET_TYPE_API_PERMISSION = "API_PERMISSION";

	private final ObjectMapper objectMapper;

	private final AuditService auditService;

	public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
		this(objectMapper, null);
	}

	@Autowired
	public ApiAccessDeniedHandler(ObjectMapper objectMapper, AuditService auditService) {
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		recordPermissionDeniedAudit(request);
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ApiErrorCode errorCode = financialClosePath(request) ? ApiErrorCode.FIN_PERMISSION_DENIED
				: ApiErrorCode.AUTH_FORBIDDEN;
		this.objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode,
				errorCode.message(), List.of(), null));
	}

	private void recordPermissionDeniedAudit(HttpServletRequest request) {
		if (!isAdminPath(request)) {
			return;
		}
		if (this.auditService == null) {
			return;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
			return;
		}
		String path = requestPath(request);
		try {
			this.auditService.recordFailure(currentUser, ACTION_PERMISSION_DENIED, TARGET_TYPE_API_PERMISSION, null,
					request.getMethod() + " " + path, ApiErrorCode.AUTH_FORBIDDEN.name(), request);
		}
		catch (RuntimeException ignored) {
			// 审计暂时不可写时，权限拒绝响应仍必须保持稳定。
		}
	}

	private boolean isAdminPath(HttpServletRequest request) {
		String path = requestPath(request);
		return path.equals("/api/admin") || path.startsWith("/api/admin/");
	}

	private boolean financialClosePath(HttpServletRequest request) {
		String path = requestPath(request);
		return path.startsWith("/api/admin/financial-closes")
				|| path.startsWith("/api/admin/bank-accounts")
				|| path.startsWith("/api/admin/bank-statements")
				|| path.startsWith("/api/admin/bank-statement-lines")
				|| path.startsWith("/api/admin/bank-reconciliations")
				|| path.startsWith("/api/admin/tax-profiles")
				|| path.startsWith("/api/admin/tax-rate-rules")
				|| path.startsWith("/api/admin/tax-invoice-types")
				|| path.startsWith("/api/admin/tax-summaries")
				|| path.startsWith("/api/admin/tax-payments");
	}

	private String requestPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

}
