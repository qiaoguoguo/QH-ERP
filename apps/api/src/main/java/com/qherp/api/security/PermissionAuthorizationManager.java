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
import java.util.List;
import java.util.regex.Pattern;

public class PermissionAuthorizationManager extends OncePerRequestFilter {

	private static final List<MasterDataPermissionMapping> MASTER_DATA_PERMISSION_MAPPINGS = List.of(
			new MasterDataPermissionMapping("/api/admin/master/units", "master:unit"),
			new MasterDataPermissionMapping("/api/admin/master/warehouses", "master:warehouse"),
			new MasterDataPermissionMapping("/api/admin/master/suppliers", "master:supplier"),
			new MasterDataPermissionMapping("/api/admin/master/customers", "master:customer"),
			new MasterDataPermissionMapping("/api/admin/master/material-categories", "master:material-category"),
			new MasterDataPermissionMapping("/api/admin/master/materials", "master:material"));

	private final ApiAccessDeniedHandler accessDeniedHandler;

	public PermissionAuthorizationManager(ApiAccessDeniedHandler accessDeniedHandler) {
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String permissionCode = permissionCode(request);
		if (permissionCode == null) {
			if (isAdminPath(request)) {
				this.accessDeniedHandler.handle(request, response,
						new AccessDeniedException("管理接口未配置权限映射"));
				return;
			}
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

		String masterDataPermissionCode = masterDataPermissionCode(method, path);
		if (masterDataPermissionCode != null) {
			return masterDataPermissionCode;
		}

		String bomPermissionCode = bomPermissionCode(method, path);
		if (bomPermissionCode != null) {
			return bomPermissionCode;
		}

		String inventoryPermissionCode = inventoryPermissionCode(method, path);
		if (inventoryPermissionCode != null) {
			return inventoryPermissionCode;
		}

		String procurementPermissionCode = procurementPermissionCode(method, path);
		if (procurementPermissionCode != null) {
			return procurementPermissionCode;
		}

		String productionPermissionCode = productionPermissionCode(method, path);
		if (productionPermissionCode != null) {
			return productionPermissionCode;
		}

		String costPermissionCode = costPermissionCode(method, path);
		if (costPermissionCode != null) {
			return costPermissionCode;
		}

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

	private String costPermissionCode(String method, String path) {
		String basePath = "/api/admin/cost";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		String recordsPath = "/api/admin/cost/records";
		if ("GET".equals(method) && (recordsPath.equals(path) || matchesIdPath(path, recordsPath))) {
			return "cost:record:view";
		}
		if ("GET".equals(method)
				&& path.matches(Pattern.quote("/api/admin/cost/work-orders") + "/\\d+/summary")) {
			return "cost:record:view";
		}
		if ("POST".equals(method) && recordsPath.equals(path)) {
			return "cost:record:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, recordsPath)) {
			return "cost:record:update";
		}
		return null;
	}

	private String productionPermissionCode(String method, String path) {
		String basePath = "/api/admin/production";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		String workOrderPath = "/api/admin/production/work-orders";
		if ("GET".equals(method) && (workOrderPath.equals(path) || matchesIdPath(path, workOrderPath))) {
			return "production:work-order:view";
		}
		if ("POST".equals(method) && workOrderPath.equals(path)) {
			return "production:work-order:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, workOrderPath)) {
			return "production:work-order:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(workOrderPath) + "/\\d+/release")) {
			return "production:work-order:release";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(workOrderPath) + "/\\d+/complete")) {
			return "production:work-order:complete";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(workOrderPath) + "/\\d+/cancel")) {
			return "production:work-order:cancel";
		}

		String issuePermissionCode = productionDocumentPermissionCode(method, path, "material-issues",
				"production:issue");
		if (issuePermissionCode != null) {
			return issuePermissionCode;
		}
		String reportPermissionCode = productionDocumentPermissionCode(method, path, "reports", "production:report");
		if (reportPermissionCode != null) {
			return reportPermissionCode;
		}
		return productionDocumentPermissionCode(method, path, "completion-receipts", "production:receipt");
	}

	private String procurementPermissionCode(String method, String path) {
		String basePath = "/api/admin/procurement";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		String orderPath = "/api/admin/procurement/orders";
		if ("GET".equals(method) && (orderPath.equals(path) || matchesIdPath(path, orderPath))) {
			return "procurement:order:view";
		}
		if ("POST".equals(method) && orderPath.equals(path)) {
			return "procurement:order:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, orderPath)) {
			return "procurement:order:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/confirm")) {
			return "procurement:order:confirm";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/cancel")) {
			return "procurement:order:cancel";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/close")) {
			return "procurement:order:close";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/receipts")) {
			return "procurement:receipt:create";
		}

		String receiptPath = "/api/admin/procurement/receipts";
		if ("GET".equals(method) && (receiptPath.equals(path) || matchesIdPath(path, receiptPath))) {
			return "procurement:receipt:view";
		}
		if ("PUT".equals(method) && matchesIdPath(path, receiptPath)) {
			return "procurement:receipt:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(receiptPath) + "/\\d+/post")) {
			return "procurement:receipt:post";
		}
		return null;
	}

	private String productionDocumentPermissionCode(String method, String path, String resourceSegment,
			String permissionPrefix) {
		String collectionPattern = Pattern.quote("/api/admin/production/work-orders") + "/\\d+/"
				+ Pattern.quote(resourceSegment);
		if ("GET".equals(method) && (path.matches(collectionPattern) || path.matches(collectionPattern + "/\\d+"))) {
			return permissionPrefix + ":view";
		}
		if ("POST".equals(method) && path.matches(collectionPattern)) {
			return permissionPrefix + ":create";
		}
		if ("PUT".equals(method) && path.matches(collectionPattern + "/\\d+")) {
			return permissionPrefix + ":update";
		}
		if ("PUT".equals(method) && path.matches(collectionPattern + "/\\d+/post")) {
			return permissionPrefix + ":post";
		}
		return null;
	}

	private String bomPermissionCode(String method, String path) {
		String basePath = "/api/admin/boms";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		if ("GET".equals(method) && (basePath.equals(path) || matchesIdPath(path, basePath))) {
			return "material:bom:view";
		}
		if ("POST".equals(method) && basePath.equals(path)) {
			return "material:bom:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, basePath)) {
			return "material:bom:update";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/copy")) {
			return "material:bom:copy";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/enable")) {
			return "material:bom:enable";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/disable")) {
			return "material:bom:disable";
		}
		return null;
	}

	private String inventoryPermissionCode(String method, String path) {
		String basePath = "/api/admin/inventory";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		if ("GET".equals(method) && "/api/admin/inventory/balances".equals(path)) {
			return "inventory:balance:view";
		}
		if ("GET".equals(method) && "/api/admin/inventory/movements".equals(path)) {
			return "inventory:movement:view";
		}
		String documentPath = "/api/admin/inventory/documents";
		if ("GET".equals(method) && (documentPath.equals(path) || matchesIdPath(path, documentPath))) {
			return "inventory:document:view";
		}
		if ("POST".equals(method) && documentPath.equals(path)) {
			return "inventory:document:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, documentPath)) {
			return "inventory:document:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(documentPath) + "/\\d+/post")) {
			return "inventory:document:post";
		}
		return null;
	}

	private String masterDataPermissionCode(String method, String path) {
		for (MasterDataPermissionMapping mapping : MASTER_DATA_PERMISSION_MAPPINGS) {
			if (!matchesBasePath(path, mapping.basePath())) {
				continue;
			}
			if ("GET".equals(method) && (mapping.basePath().equals(path) || matchesIdPath(path, mapping.basePath()))) {
				return mapping.permissionPrefix() + ":view";
			}
			if ("POST".equals(method) && mapping.basePath().equals(path)) {
				return mapping.permissionPrefix() + ":create";
			}
			if ("PUT".equals(method)
					&& (matchesIdPath(path, mapping.basePath()) || matchesStatusPath(path, mapping.basePath()))) {
				return mapping.permissionPrefix() + ":update";
			}
			return null;
		}
		return null;
	}

	private boolean matchesBasePath(String path, String basePath) {
		return basePath.equals(path) || path.startsWith(basePath + "/");
	}

	private boolean matchesIdPath(String path, String basePath) {
		return path.matches(Pattern.quote(basePath) + "/\\d+");
	}

	private boolean matchesStatusPath(String path, String basePath) {
		return path.matches(Pattern.quote(basePath) + "/\\d+/(enable|disable)");
	}

	private boolean isAdminPath(HttpServletRequest request) {
		String path = requestPath(request);
		return "/api/admin".equals(path) || path.startsWith("/api/admin/");
	}

	private String requestPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
			return path.substring(contextPath.length());
		}
		return path;
	}

	private record MasterDataPermissionMapping(String basePath, String permissionPrefix) {
	}

}
