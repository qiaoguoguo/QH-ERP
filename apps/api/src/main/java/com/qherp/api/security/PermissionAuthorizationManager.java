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

		if (!hasAnyPermission(currentUser, permissionCode)) {
			this.accessDeniedHandler.handle(request, response, new AccessDeniedException("缺少权限：" + permissionCode));
			return;
		}

		filterChain.doFilter(request, response);
	}

	private String permissionCode(HttpServletRequest request) {
		String method = request.getMethod();
		String path = requestPath(request);

		if ("GET".equals(method) && "/api/admin/reversal-traces".equals(path)) {
			return "business:reversal:view";
		}

		String platformPermissionCode = platformPermissionCode(method, path);
		if (platformPermissionCode != null) {
			return platformPermissionCode;
		}

		String businessPeriodPermissionCode = businessPeriodPermissionCode(method, path);
		if (businessPeriodPermissionCode != null) {
			return businessPeriodPermissionCode;
		}

		String stage021MasterPermissionCode = stage021MasterPermissionCode(method, path);
		if (stage021MasterPermissionCode != null) {
			return stage021MasterPermissionCode;
		}

		String masterDataPermissionCode = masterDataPermissionCode(method, path);
		if (masterDataPermissionCode != null) {
			return masterDataPermissionCode;
		}

		String bomPermissionCode = bomPermissionCode(method, path);
		if (bomPermissionCode != null) {
			return bomPermissionCode;
		}

		String bomEngineeringChangePermissionCode = bomEngineeringChangePermissionCode(method, path);
		if (bomEngineeringChangePermissionCode != null) {
			return bomEngineeringChangePermissionCode;
		}

		String materialSubstitutePermissionCode = materialSubstitutePermissionCode(method, path);
		if (materialSubstitutePermissionCode != null) {
			return materialSubstitutePermissionCode;
		}

		String inventoryPermissionCode = inventoryPermissionCode(method, path);
		if (inventoryPermissionCode != null) {
			return inventoryPermissionCode;
		}

		String qualityPermissionCode = qualityPermissionCode(method, path);
		if (qualityPermissionCode != null) {
			return qualityPermissionCode;
		}

		String procurementPermissionCode = procurementPermissionCode(method, path);
		if (procurementPermissionCode != null) {
			return procurementPermissionCode;
		}

		String salesPermissionCode = salesPermissionCode(method, path);
		if (salesPermissionCode != null) {
			return salesPermissionCode;
		}

		String salesProjectPermissionCode = salesProjectPermissionCode(method, path);
		if (salesProjectPermissionCode != null) {
			return salesProjectPermissionCode;
		}

		String productionPermissionCode = productionPermissionCode(method, path);
		if (productionPermissionCode != null) {
			return productionPermissionCode;
		}

		String costPermissionCode = costPermissionCode(method, path);
		if (costPermissionCode != null) {
			return costPermissionCode;
		}

		String financePermissionCode = financePermissionCode(method, path);
		if (financePermissionCode != null) {
			return financePermissionCode;
		}

		String reportPermissionCode = reportPermissionCode(method, path);
		if (reportPermissionCode != null) {
			return reportPermissionCode;
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

	private String platformPermissionCode(String method, String path) {
		if ("POST".equals(method)
				&& path.matches(Pattern.quote("/api/admin/approvals/sales-project-contract-activation") + "/\\d+/submit")) {
			return "sales:contract:activate";
		}
		if ("POST".equals(method)
				&& path.matches(Pattern.quote("/api/admin/approvals/bom-eco-application") + "/\\d+/submit")) {
			return "material:bom-eco:apply";
		}
		if ("GET".equals(method) && matchesBasePath(path, "/api/admin/approvals")) {
			return "platform:approval:view";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote("/api/admin/approvals") + "/\\d+/cancel")) {
			return "platform:approval:cancel";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote("/api/admin/approvals") + "/\\d+/withdraw")) {
			return "platform:approval:view";
		}
		if ("GET".equals(method) && matchesBasePath(path, "/api/admin/approval-tasks")) {
			return "platform:todo:view";
		}
		if ("POST".equals(method)
				&& path.matches(Pattern.quote("/api/admin/approval-tasks") + "/\\d+/(approve|reject)")) {
			return "platform:todo:view";
		}
		if ("GET".equals(method) && matchesBasePath(path, "/api/admin/messages")) {
			return "platform:message:view";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote("/api/admin/messages") + "/\\d+/read")) {
			return "platform:message:read";
		}
		if ("PUT".equals(method) && "/api/admin/messages/read-all".equals(path)) {
			return "platform:message:read";
		}
		if (matchesBasePath(path, "/api/admin/attachments")) {
			if ("POST".equals(method) && "/api/admin/attachments".equals(path)) {
				return "platform:attachment:upload";
			}
			if ("GET".equals(method) && path.matches(Pattern.quote("/api/admin/attachments") + "/\\d+/download")) {
				return "platform:attachment:download";
			}
			if ("PUT".equals(method) && path.matches(Pattern.quote("/api/admin/attachments") + "/\\d+/delete")) {
				return "platform:attachment:delete";
			}
			if ("GET".equals(method)) {
				return "platform:attachment:view";
			}
		}
		if ("POST".equals(method) && matchesBasePath(path, "/api/admin/imports/materials")) {
			return "master:material:import";
		}
		if ("POST".equals(method) && matchesBasePath(path, "/api/admin/imports/bom-drafts")) {
			return "material:bom:import";
		}
		if ("GET".equals(method) && "/api/admin/import-templates/materials".equals(path)) {
			return "master:material:import";
		}
		if ("GET".equals(method) && "/api/admin/import-templates/bom-drafts".equals(path)) {
			return "material:bom:import";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote("/api/admin/imports") + "/\\d+/confirm")) {
			return "platform:document-task:view";
		}
		if ("POST".equals(method) && "/api/admin/exports/materials".equals(path)) {
			return "master:material:export";
		}
		if ("POST".equals(method) && matchesBasePath(path, "/api/admin/exports/bom-drafts")) {
			return "material:bom:export";
		}
		if (matchesBasePath(path, "/api/admin/document-tasks")) {
			if ("GET".equals(method) && path.matches(Pattern.quote("/api/admin/document-tasks") + "/\\d+/download")) {
				return "platform:document-task:download";
			}
			if ("POST".equals(method) && path.matches(Pattern.quote("/api/admin/document-tasks") + "/\\d+/cancel")) {
				return "platform:document-task:cancel";
			}
			if ("GET".equals(method)) {
				return "platform:document-task:view";
			}
		}
		if ("GET".equals(method) && "/api/admin/print-templates".equals(path)) {
			return "platform:print:generate";
		}
		if ("GET".equals(method) && path.matches(Pattern.quote("/api/admin/print-previews") + "/\\d+")) {
			return "platform:print:generate";
		}
		if ("POST".equals(method) && "/api/admin/print-tasks".equals(path)) {
			return "platform:print:generate";
		}
		return null;
	}

	private String businessPeriodPermissionCode(String method, String path) {
		String basePath = "/api/admin/system/business-periods";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		if ("GET".equals(method)) {
			return "system:business-period:view";
		}
		if ("POST".equals(method) && (basePath.equals(path) || (basePath + "/generate-monthly").equals(path))) {
			return "system:business-period:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, basePath)) {
			return "system:business-period:update";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/lock")) {
			return "system:business-period:lock";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/unlock")) {
			return "system:business-period:unlock";
		}
		return null;
	}

	private String reportPermissionCode(String method, String path) {
		String basePath = "/api/admin/reports";
		if (!"GET".equals(method) || !matchesBasePath(path, basePath)) {
			return null;
		}
		if ("/api/admin/reports/overview".equals(path)) {
			return "report:overview:view";
		}
		if (matchesReportEndpoint(path, "sales-summary")) {
			return "report:sales:view";
		}
		if (matchesReportEndpoint(path, "procurement-summary")) {
			return "report:procurement:view";
		}
		if (matchesReportEndpoint(path, "inventory-stock-flow")) {
			return "report:inventory:view";
		}
		if (matchesReportEndpoint(path, "production-execution")) {
			return "report:production:view";
		}
		if (matchesReportEndpoint(path, "cost-collection")) {
			return "report:cost:view";
		}
		if (matchesReportEndpoint(path, "settlement-summary")) {
			return "report:settlement:view";
		}
		if (matchesReportEndpoint(path, "exceptions")) {
			return "report:exception:view";
		}
		return null;
	}

	private String financePermissionCode(String method, String path) {
		String basePath = "/api/admin/finance";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		String receivablePath = "/api/admin/finance/receivables";
		if ("GET".equals(method) && "/api/admin/finance/receivable-sources".equals(path)) {
			return "finance:receivable:create";
		}
		if ("GET".equals(method) && (receivablePath.equals(path) || matchesIdPath(path, receivablePath))) {
			return "finance:receivable:view";
		}
		if ("POST".equals(method) && receivablePath.equals(path)) {
			return "finance:receivable:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, receivablePath)) {
			return "finance:receivable:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(receivablePath) + "/\\d+/confirm")) {
			return "finance:receivable:confirm";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(receivablePath) + "/\\d+/cancel")) {
			return "finance:receivable:cancel";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(receivablePath) + "/\\d+/close")) {
			return "finance:receivable:close";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(receivablePath) + "/\\d+/receipts")) {
			return "finance:receipt:create";
		}

		String receiptPath = "/api/admin/finance/receipts";
		if ("GET".equals(method) && (receiptPath.equals(path) || matchesIdPath(path, receiptPath))) {
			return "finance:receipt:view";
		}
		if ("PUT".equals(method) && matchesIdPath(path, receiptPath)) {
			return "finance:receipt:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(receiptPath) + "/\\d+/post")) {
			return "finance:receipt:post";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(receiptPath) + "/\\d+/cancel")) {
			return "finance:receipt:cancel";
		}

		String payablePath = "/api/admin/finance/payables";
		if ("GET".equals(method) && "/api/admin/finance/payable-sources".equals(path)) {
			return "finance:payable:create";
		}
		if ("GET".equals(method) && (payablePath.equals(path) || matchesIdPath(path, payablePath))) {
			return "finance:payable:view";
		}
		if ("POST".equals(method) && payablePath.equals(path)) {
			return "finance:payable:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, payablePath)) {
			return "finance:payable:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(payablePath) + "/\\d+/confirm")) {
			return "finance:payable:confirm";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(payablePath) + "/\\d+/cancel")) {
			return "finance:payable:cancel";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(payablePath) + "/\\d+/close")) {
			return "finance:payable:close";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(payablePath) + "/\\d+/payments")) {
			return "finance:payment:create";
		}

		String paymentPath = "/api/admin/finance/payments";
		if ("GET".equals(method) && (paymentPath.equals(path) || matchesIdPath(path, paymentPath))) {
			return "finance:payment:view";
		}
		if ("PUT".equals(method) && matchesIdPath(path, paymentPath)) {
			return "finance:payment:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(paymentPath) + "/\\d+/post")) {
			return "finance:payment:post";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(paymentPath) + "/\\d+/cancel")) {
			return "finance:payment:cancel";
		}
		if ("GET".equals(method) && "/api/admin/finance/settlement-adjustment-sources".equals(path)) {
			return "finance:settlement-adjustment:create";
		}
		String settlementAdjustmentPath = "/api/admin/finance/settlement-adjustments";
		String settlementAdjustmentPermissionCode = documentPermissionCode(method, path, settlementAdjustmentPath,
				"finance:settlement-adjustment");
		if (settlementAdjustmentPermissionCode != null) {
			return settlementAdjustmentPermissionCode;
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
		String receiptPermissionCode = productionDocumentPermissionCode(method, path, "completion-receipts",
				"production:receipt");
		if (receiptPermissionCode != null) {
			return receiptPermissionCode;
		}
		if ("GET".equals(method) && "/api/admin/production/material-return-sources".equals(path)) {
			return "production:material-return:create";
		}
		String materialReturnPermissionCode = documentPermissionCode(method, path,
				"/api/admin/production/material-returns", "production:material-return");
		if (materialReturnPermissionCode != null) {
			return materialReturnPermissionCode;
		}
		if ("GET".equals(method) && "/api/admin/production/material-supplement-sources".equals(path)) {
			return "production:material-supplement:create";
		}
		return documentPermissionCode(method, path, "/api/admin/production/material-supplements",
				"production:material-supplement");
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
		if ("GET".equals(method) && "/api/admin/procurement/return-sources".equals(path)) {
			return "procurement:return:create";
		}
		String returnPermissionCode = documentPermissionCode(method, path, "/api/admin/procurement/returns",
				"procurement:return");
		if (returnPermissionCode != null) {
			return returnPermissionCode;
		}
		return null;
	}

	private String salesPermissionCode(String method, String path) {
		String basePath = "/api/admin/sales";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		String orderPath = "/api/admin/sales/orders";
		if ("GET".equals(method) && matchesBasePath(path, orderPath)) {
			return "sales:order:view";
		}
		if ("POST".equals(method) && orderPath.equals(path)) {
			return "sales:order:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, orderPath)) {
			return "sales:order:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/confirm")) {
			return "sales:order:confirm";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/cancel")) {
			return "sales:order:cancel";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/close")) {
			return "sales:order:close";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(orderPath) + "/\\d+/shipments")) {
			return "sales:shipment:create";
		}

		String shipmentPath = "/api/admin/sales/shipments";
		if ("GET".equals(method) && matchesBasePath(path, shipmentPath)) {
			return "sales:shipment:view";
		}
		if ("PUT".equals(method) && matchesIdPath(path, shipmentPath)) {
			return "sales:shipment:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(shipmentPath) + "/\\d+/post")) {
			return "sales:shipment:post";
		}
		if ("GET".equals(method) && "/api/admin/sales/return-sources".equals(path)) {
			return "sales:return:create";
		}
		String returnPermissionCode = documentPermissionCode(method, path, "/api/admin/sales/returns",
				"sales:return");
		if (returnPermissionCode != null) {
			return returnPermissionCode;
		}
		return null;
	}

	private String salesProjectPermissionCode(String method, String path) {
		String projectPath = "/api/admin/sales-projects";
		String contractPath = "/api/admin/sales-project-contracts";
		if (!matchesBasePath(path, projectPath) && !matchesBasePath(path, contractPath)) {
			return null;
		}
		if ("GET".equals(method) && "/api/admin/sales-projects/owner-candidates".equals(path)) {
			return "sales:project:view|sales:project:create|sales:project:update";
		}
		if ("GET".equals(method) && "/api/admin/sales-projects/order-link-candidates".equals(path)) {
			return "sales:order:create|sales:order:update";
		}
		if ("GET".equals(method) && path.matches(Pattern.quote(projectPath) + "/\\d+/contracts")) {
			return "sales:contract:view";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(projectPath) + "/\\d+/contracts")) {
			return "sales:contract:create";
		}
		if ("GET".equals(method) && path.matches(Pattern.quote(projectPath) + "/\\d+/sales-orders")) {
			return "sales:project:view";
		}
		if ("GET".equals(method) && (projectPath.equals(path) || matchesIdPath(path, projectPath))) {
			return "sales:project:view";
		}
		if ("POST".equals(method) && projectPath.equals(path)) {
			return "sales:project:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, projectPath)) {
			return "sales:project:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(projectPath) + "/\\d+/activate")) {
			return "sales:project:activate";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(projectPath) + "/\\d+/close")) {
			return "sales:project:close";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(projectPath) + "/\\d+/cancel")) {
			return "sales:project:cancel";
		}
		if ("GET".equals(method) && matchesIdPath(path, contractPath)) {
			return "sales:contract:view";
		}
		if ("PUT".equals(method) && matchesIdPath(path, contractPath)) {
			return "sales:contract:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(contractPath) + "/\\d+/activate")) {
			return "sales:contract:activate";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(contractPath) + "/\\d+/close")) {
			return "sales:contract:close";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(contractPath) + "/\\d+/terminate")) {
			return "sales:contract:terminate";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(contractPath) + "/\\d+/cancel")) {
			return "sales:contract:cancel";
		}
		return null;
	}

	private String documentPermissionCode(String method, String path, String basePath, String permissionPrefix) {
		if ("GET".equals(method) && (basePath.equals(path) || matchesIdPath(path, basePath))) {
			return permissionPrefix + ":view";
		}
		if ("POST".equals(method) && basePath.equals(path)) {
			return permissionPrefix + ":create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, basePath)) {
			return permissionPrefix + ":update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/post")) {
			return permissionPrefix + ":post";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/cancel")) {
			return permissionPrefix + ":cancel";
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
		if ("GET".equals(method) && (basePath.equals(path) || matchesIdPath(path, basePath)
				|| (basePath + "/material-candidates").equals(path) || (basePath + "/unit-candidates").equals(path))) {
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

	private String stage021MasterPermissionCode(String method, String path) {
		String unitConversionPath = "/api/admin/master/unit-conversions";
		if (matchesBasePath(path, unitConversionPath)) {
			if ("GET".equals(method) && (unitConversionPath.equals(path) || matchesIdPath(path, unitConversionPath)
					|| (unitConversionPath + "/material-candidates").equals(path)
					|| (unitConversionPath + "/unit-candidates").equals(path))) {
				return "master:unit-conversion:view";
			}
			if ("POST".equals(method) && (unitConversionPath + "/convert").equals(path)) {
				return "master:unit-conversion:view";
			}
			if ("POST".equals(method) && unitConversionPath.equals(path)) {
				return "master:unit-conversion:create";
			}
			if ("PUT".equals(method) && matchesIdPath(path, unitConversionPath)) {
				return "master:unit-conversion:update";
			}
			if ("PUT".equals(method) && path.matches(Pattern.quote(unitConversionPath) + "/\\d+/enable")) {
				return "master:unit-conversion:enable";
			}
			if ("PUT".equals(method) && path.matches(Pattern.quote(unitConversionPath) + "/\\d+/disable")) {
				return "master:unit-conversion:disable";
			}
			return null;
		}

		String codingRulePath = "/api/admin/coding-rules";
		if (matchesBasePath(path, codingRulePath)) {
			if ("GET".equals(method) && (codingRulePath.equals(path) || matchesIdPath(path, codingRulePath))) {
				return "master:coding-rule:view";
			}
			if ("POST".equals(method) && codingRulePath.equals(path)) {
				return "master:coding-rule:create";
			}
			if ("PUT".equals(method) && matchesIdPath(path, codingRulePath)) {
				return "master:coding-rule:update";
			}
			if ("PUT".equals(method) && path.matches(Pattern.quote(codingRulePath) + "/\\d+/enable")) {
				return "master:coding-rule:enable";
			}
			if ("PUT".equals(method) && path.matches(Pattern.quote(codingRulePath) + "/\\d+/disable")) {
				return "master:coding-rule:disable";
			}
			if ("POST".equals(method) && (codingRulePath + "/generate").equals(path)) {
				return "master:coding-rule:generate";
			}
			return null;
		}

		if ("GET".equals(method) && path.matches("/api/admin/master/customers/\\d+/settlement-tax")) {
			return "master:customer-settlement:view";
		}
		if ("PUT".equals(method) && path.matches("/api/admin/master/customers/\\d+/settlement-tax")) {
			return "master:customer-settlement:update";
		}
		if ("GET".equals(method) && path.matches("/api/admin/master/suppliers/\\d+/settlement-tax")) {
			return "master:supplier-settlement:view";
		}
		if ("PUT".equals(method) && path.matches("/api/admin/master/suppliers/\\d+/settlement-tax")) {
			return "master:supplier-settlement:update";
		}
		return null;
	}

	private String bomEngineeringChangePermissionCode(String method, String path) {
		String basePath = "/api/admin/bom-engineering-changes";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		if ("GET".equals(method) && (basePath.equals(path) || matchesIdPath(path, basePath)
				|| (basePath + "/source-bom-candidates").equals(path)
				|| (basePath + "/target-bom-candidates").equals(path))) {
			return "material:bom-eco:view";
		}
		if ("POST".equals(method) && basePath.equals(path)) {
			return "material:bom-eco:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, basePath)) {
			return "material:bom-eco:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/apply")) {
			return "material:bom-eco:apply";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/cancel")) {
			return "material:bom-eco:cancel";
		}
		return null;
	}

	private String materialSubstitutePermissionCode(String method, String path) {
		String basePath = "/api/admin/material-substitutes";
		if (!matchesBasePath(path, basePath)) {
			return null;
		}
		if ("GET".equals(method) && (basePath.equals(path) || matchesIdPath(path, basePath)
				|| (basePath + "/material-candidates").equals(path) || (basePath + "/bom-candidates").equals(path))) {
			return "material:substitute:view";
		}
		if ("POST".equals(method) && basePath.equals(path)) {
			return "material:substitute:create";
		}
		if ("PUT".equals(method) && matchesIdPath(path, basePath)) {
			return "material:substitute:update";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/enable")) {
			return "material:substitute:enable";
		}
		if ("PUT".equals(method) && path.matches(Pattern.quote(basePath) + "/\\d+/disable")) {
			return "material:substitute:disable";
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
		String reservationPath = "/api/admin/inventory/reservations";
		if ("GET".equals(method) && (reservationPath.equals(path) || matchesIdPath(path, reservationPath))) {
			return "inventory:reservation:view";
		}
		if ("GET".equals(method) && "/api/admin/inventory/movements".equals(path)) {
			return "inventory:movement:view";
		}
		String batchPath = "/api/admin/inventory/batches";
		if ("GET".equals(method) && (batchPath.equals(path) || matchesIdPath(path, batchPath))) {
			return "inventory:batch:view";
		}
		String serialPath = "/api/admin/inventory/serials";
		if ("GET".equals(method) && (serialPath.equals(path) || matchesIdPath(path, serialPath))) {
			return "inventory:serial:view";
		}
		String tracePath = "/api/admin/inventory/traces";
		if ("GET".equals(method)
				&& (path.matches(Pattern.quote(tracePath) + "/batches/\\d+")
						|| path.matches(Pattern.quote(tracePath) + "/serials/\\d+"))) {
			return "inventory:trace:view";
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

	private String qualityPermissionCode(String method, String path) {
		String inspectionPath = "/api/admin/quality/inspections";
		if ("GET".equals(method) && (inspectionPath.equals(path) || matchesIdPath(path, inspectionPath))) {
			return "quality:inspection:view";
		}
		if ("POST".equals(method) && path.matches(Pattern.quote(inspectionPath) + "/\\d+/process")) {
			return "quality:inspection:process";
		}
		if ("POST".equals(method) && "/api/admin/inventory/quality-transfers/freeze".equals(path)) {
			return "quality:status:freeze";
		}
		if ("POST".equals(method) && "/api/admin/inventory/quality-transfers/unfreeze".equals(path)) {
			return "quality:status:unfreeze";
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

	private boolean matchesReportEndpoint(String path, String reportSegment) {
		String endpointPath = "/api/admin/reports/" + reportSegment;
		return endpointPath.equals(path) || (endpointPath + "/traces").equals(path);
	}

	private boolean matchesIdPath(String path, String basePath) {
		return path.matches(Pattern.quote(basePath) + "/\\d+");
	}

	private boolean matchesStatusPath(String path, String basePath) {
		return path.matches(Pattern.quote(basePath) + "/\\d+/(enable|disable)");
	}

	private boolean hasAnyPermission(CurrentUser currentUser, String permissionCode) {
		for (String code : permissionCode.split("\\|")) {
			if (currentUser.permissions().contains(code)) {
				return true;
			}
		}
		return false;
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
