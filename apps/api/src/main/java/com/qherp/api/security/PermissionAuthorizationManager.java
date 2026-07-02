package com.qherp.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class PermissionAuthorizationManager extends OncePerRequestFilter {

	private final ApiAccessDeniedHandler accessDeniedHandler;

	public PermissionAuthorizationManager(ApiAccessDeniedHandler accessDeniedHandler) {
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String permissionCode = permissionCode(request);
		if (permissionCode == null) {
			filterChain.doFilter(request, response);
			return;
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
			filterChain.doFilter(request, response);
			return;
		}

		if (!currentUser.permissions().contains(permissionCode)) {
			this.accessDeniedHandler.handle(request, response, new AccessDeniedException("缺少权限：" + permissionCode));
			return;
		}

		filterChain.doFilter(request, response);
	}

	private String permissionCode(HttpServletRequest request) {
		String method = request.getMethod();
		String path = requestPath(request);

		if ("GET".equals(method) && ("/api/admin/users".equals(path) || path.matches("/api/admin/users/\\d+"))) {
			return "system:user:view";
		}
		if ("POST".equals(method) && "/api/admin/users".equals(path)) {
			return "system:user:create";
		}
		if ("PUT".equals(method) && path.matches("/api/admin/users/\\d+/password")) {
			return "system:user:reset-password";
		}
		if ("PUT".equals(method)
				&& (path.matches("/api/admin/users/\\d+") || path.matches("/api/admin/users/\\d+/(enable|disable)"))) {
			return "system:user:update";
		}

		if ("GET".equals(method) && ("/api/admin/roles".equals(path) || path.matches("/api/admin/roles/\\d+"))) {
			return "system:role:view";
		}
		if ("POST".equals(method) && "/api/admin/roles".equals(path)) {
			return "system:role:create";
		}
		if ("PUT".equals(method) && path.matches("/api/admin/roles/\\d+/permissions")) {
			return "system:role:assign-permission";
		}
		if ("PUT".equals(method)
				&& (path.matches("/api/admin/roles/\\d+") || path.matches("/api/admin/roles/\\d+/(enable|disable)"))) {
			return "system:role:update";
		}

		if ("GET".equals(method) && "/api/admin/permissions/tree".equals(path)) {
			return "system:permission:view";
		}
		if ("GET".equals(method) && "/api/admin/audit-logs".equals(path)) {
			return "system:audit:view";
		}

		return null;
	}

	private String requestPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
			return path.substring(contextPath.length());
		}
		return path;
	}

}
