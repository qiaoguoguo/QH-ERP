package com.qherp.api.system.permission;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.security.PermissionAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task4-permission-authorization")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PermissionAuthorizationTests extends PostgresIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void permissionTreeReturnsMenuAndActionNodesAndAuditLogCanBeQueried() {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");

		ResponseEntity<String> tree = get("/api/admin/permissions/tree", admin);
		assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(tree.getBody()).contains("\"code\":\"system\"");
		assertThat(tree.getBody()).contains("\"type\":\"MENU\"");
		assertThat(tree.getBody()).contains("\"code\":\"system:user:view\"");
		assertThat(tree.getBody()).contains("\"children\"");

		ResponseEntity<String> audit = get("/api/admin/audit-logs?page=1&pageSize=20", admin);
		assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(audit.getBody()).contains("\"items\"");
	}

	@Test
	void auditLogQueryFiltersByOperatorKeywordAndCreatedAtRange() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		insertAuditLog("admin", "TASK4_AUDIT_FILTER", "TASK4_AUDIT", now.minusMinutes(10));
		insertAuditLog("other-operator", "TASK4_AUDIT_FILTER", "TASK4_AUDIT", now.minusMinutes(10));
		insertAuditLog("admin", "TASK4_AUDIT_FILTER", "TASK4_AUDIT", now.minusDays(2));

		ResponseEntity<String> audit = get("/api/admin/audit-logs?operatorKeyword=adm&targetType=TASK4_AUDIT"
				+ "&action=TASK4_AUDIT_FILTER&startAt=" + now.minusHours(1) + "&endAt=" + now.plusHours(1)
				+ "&page=1&pageSize=20", admin);

		assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(audit);
		assertThat(data.get("total").longValue()).isOne();
		JsonNode item = data.get("items").get(0);
		assertThat(item.get("operatorUsername").asText()).isEqualTo("admin");
		assertThat(item.get("action").asText()).isEqualTo("TASK4_AUDIT_FILTER");
		assertThat(item.get("targetType").asText()).isEqualTo("TASK4_AUDIT");
	}

	@Test
	void permissionChangesAndRoleDisabledAffectAuthorizationImmediately() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		long roleId = createRole("LIMITED_ROLE", "受限角色", admin);
		long userId = createUser("limited-user", List.of(roleId), admin);
		AuthenticatedSession limited = login("limited-user", "Qherp@2026!");

		ResponseEntity<String> noPermission = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "should-forbid", "displayName", "应拒绝", "initialPassword", "Qherp@2026!",
						"status", "ENABLED", "roleIds", List.of()),
				limited);
		assertThat(noPermission.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(noPermission.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");

		exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/permissions",
				Map.of("permissionIds", List.of(permissionId("system:user:create"))), admin);
		ResponseEntity<String> allowed = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "allowed-by-role", "displayName", "授权成功", "initialPassword", "Qherp@2026!",
						"status", "ENABLED", "roleIds", List.of()),
				limited);
		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);

		exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/disable", Map.of(), admin);
		ResponseEntity<String> forbiddenAfterRoleDisabled = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", "blocked-by-role", "displayName", "角色停用", "initialPassword", "Qherp@2026!",
						"status", "ENABLED", "roleIds", List.of()),
				limited);
		assertThat(forbiddenAfterRoleDisabled.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		exchange(HttpMethod.PUT, "/api/admin/users/" + userId + "/disable", Map.of(), admin);
		ResponseEntity<String> unauthorizedAfterUserDisabled = get("/api/admin/permissions/tree", limited);
		assertThat(unauthorizedAfterUserDisabled.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unauthorizedAfterUserDisabled.getBody()).contains("\"code\":\"AUTH_UNAUTHORIZED\"");
	}

	@Test
	void unmappedAdminPathIsForbiddenAndPathVariableRequestStillMatchesPermission() throws Exception {
		AuthenticatedSession admin = login("admin", "Qherp@2026!");
		ResponseEntity<String> unmapped = get("/api/admin/unmapped-path", admin);
		assertThat(unmapped.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(unmapped.getBody()).contains("\"code\":\"AUTH_FORBIDDEN\"");

		long roleId = createRole("USER_VIEW_ONLY_ROLE", "用户查看角色", admin);
		exchange(HttpMethod.PUT, "/api/admin/roles/" + roleId + "/permissions",
				Map.of("permissionIds", List.of(permissionId("system:user:view"))), admin);
		long targetUserId = createUser("path-variable-target", List.of(), admin);
		createUser("path-variable-viewer", List.of(roleId), admin);
		AuthenticatedSession viewer = login("path-variable-viewer", "Qherp@2026!");

		ResponseEntity<String> detail = get("/api/admin/users/" + targetUserId, viewer);

		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detail.getBody()).contains("\"username\":\"path-variable-target\"");
	}

	@Test
	void inventoryAdminPathsMapToInventoryPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/balances", "inventory:balance:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/reservations", "inventory:reservation:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/reservations/1", "inventory:reservation:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/movements", "inventory:movement:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/documents", "inventory:document:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/documents/1", "inventory:document:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/inventory/documents", "inventory:document:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/inventory/documents/1", "inventory:document:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/inventory/documents/1/post", "inventory:document:post");
	}

	@Test
	void qualityAdminPathsMapToQualityPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/quality/inspections", "quality:inspection:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/quality/inspections/1", "quality:inspection:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/quality/inspections/1/process",
				"quality:inspection:process");
		assertPermissionCode(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				"quality:status:freeze");
		assertPermissionCode(HttpMethod.POST, "/api/admin/inventory/quality-transfers/unfreeze",
				"quality:status:unfreeze");
	}

	@Test
	void procurementAdminPathsMapToProcurementPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/procurement/orders", "procurement:order:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/procurement/orders/1", "procurement:order:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/procurement/orders", "procurement:order:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/orders/1", "procurement:order:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/orders/1/confirm",
				"procurement:order:confirm");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/orders/1/cancel",
				"procurement:order:cancel");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/orders/1/close", "procurement:order:close");
		assertPermissionCode(HttpMethod.GET, "/api/admin/procurement/receipts", "procurement:receipt:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/procurement/receipts/2", "procurement:receipt:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/procurement/orders/1/receipts",
				"procurement:receipt:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/receipts/2", "procurement:receipt:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/receipts/2/post",
				"procurement:receipt:post");
		assertPermissionCode(HttpMethod.GET, "/api/admin/procurement/return-sources",
				"procurement:return:create");
		assertPermissionCode(HttpMethod.GET, "/api/admin/procurement/returns", "procurement:return:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/procurement/returns/1", "procurement:return:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/procurement/returns", "procurement:return:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/returns/1", "procurement:return:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/returns/1/post",
				"procurement:return:post");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/procurement/returns/1/cancel",
				"procurement:return:cancel");
	}

	@Test
	void salesAdminPathsMapToSalesPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/sales/orders", "sales:order:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/sales/orders/1", "sales:order:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/sales/orders", "sales:order:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/orders/1", "sales:order:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/orders/1/confirm", "sales:order:confirm");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/orders/1/cancel", "sales:order:cancel");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/orders/1/close", "sales:order:close");
		assertPermissionCode(HttpMethod.GET, "/api/admin/sales/shipments", "sales:shipment:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/sales/shipments/2", "sales:shipment:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/sales/orders/1/shipments", "sales:shipment:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/shipments/2", "sales:shipment:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/shipments/2/post", "sales:shipment:post");
		assertPermissionCode(HttpMethod.GET, "/api/admin/sales/return-sources", "sales:return:create");
		assertPermissionCode(HttpMethod.GET, "/api/admin/sales/returns", "sales:return:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/sales/returns/1", "sales:return:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/sales/returns", "sales:return:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/returns/1", "sales:return:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/returns/1/post", "sales:return:post");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/sales/returns/1/cancel", "sales:return:cancel");
	}

	@Test
	void productionAdminPathsMapToProductionPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/work-orders", "production:work-order:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/work-orders/1", "production:work-order:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/production/work-orders", "production:work-order:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1", "production:work-order:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1/release",
				"production:work-order:release");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1/complete",
				"production:work-order:complete");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1/cancel",
				"production:work-order:cancel");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/work-orders/1/material-issues",
				"production:issue:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/work-orders/1/material-issues/2",
				"production:issue:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/production/work-orders/1/material-issues",
				"production:issue:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1/material-issues/2",
				"production:issue:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1/material-issues/2/post",
				"production:issue:post");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/work-orders/1/reports",
				"production:report:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/production/work-orders/1/reports",
				"production:report:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1/reports/2/post",
				"production:report:post");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/work-orders/1/completion-receipts",
				"production:receipt:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/production/work-orders/1/completion-receipts",
				"production:receipt:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/work-orders/1/completion-receipts/2/post",
				"production:receipt:post");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/material-return-sources",
				"production:material-return:create");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/material-returns",
				"production:material-return:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/material-returns/1",
				"production:material-return:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/production/material-returns",
				"production:material-return:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/material-returns/1",
				"production:material-return:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/material-returns/1/post",
				"production:material-return:post");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/material-returns/1/cancel",
				"production:material-return:cancel");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/material-supplement-sources",
				"production:material-supplement:create");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/material-supplements",
				"production:material-supplement:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/production/material-supplements/1",
				"production:material-supplement:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/production/material-supplements",
				"production:material-supplement:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/material-supplements/1",
				"production:material-supplement:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/material-supplements/1/post",
				"production:material-supplement:post");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/production/material-supplements/1/cancel",
				"production:material-supplement:cancel");
	}

	@Test
	void costAdminPathsMapToCostPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/cost/records", "cost:record:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/cost/records/1", "cost:record:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/cost/work-orders/1/summary", "cost:record:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/cost/records", "cost:record:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/cost/records/1", "cost:record:update");
	}

	@Test
	void financeAdminPathsMapToFinancePermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/receivable-sources",
				"finance:receivable:create");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/payable-sources", "finance:payable:create");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/receivables", "finance:receivable:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/receivables/1", "finance:receivable:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/finance/receivables", "finance:receivable:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/receivables/1", "finance:receivable:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/receivables/1/confirm",
				"finance:receivable:confirm");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/receivables/1/cancel",
				"finance:receivable:cancel");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/receivables/1/close",
				"finance:receivable:close");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/receipts", "finance:receipt:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/receipts/1", "finance:receipt:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/finance/receivables/1/receipts",
				"finance:receipt:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/receipts/1", "finance:receipt:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/receipts/1/post", "finance:receipt:post");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/receipts/1/cancel", "finance:receipt:cancel");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/payables", "finance:payable:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/payables/1", "finance:payable:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/finance/payables", "finance:payable:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/payables/1", "finance:payable:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/payables/1/confirm",
				"finance:payable:confirm");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/payables/1/cancel", "finance:payable:cancel");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/payables/1/close", "finance:payable:close");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/payments", "finance:payment:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/payments/1", "finance:payment:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/finance/payables/1/payments",
				"finance:payment:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/payments/1", "finance:payment:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/payments/1/post", "finance:payment:post");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/payments/1/cancel", "finance:payment:cancel");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/settlement-adjustment-sources",
				"finance:settlement-adjustment:create");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/settlement-adjustments",
				"finance:settlement-adjustment:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/finance/settlement-adjustments/1",
				"finance:settlement-adjustment:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/finance/settlement-adjustments",
				"finance:settlement-adjustment:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/settlement-adjustments/1",
				"finance:settlement-adjustment:update");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/settlement-adjustments/1/post",
				"finance:settlement-adjustment:post");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/finance/settlement-adjustments/1/cancel",
				"finance:settlement-adjustment:cancel");
	}

	@Test
	void reversalTraceAdminPathMapsToReversalPermissionCode() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/reversal-traces", "business:reversal:view");
	}

	@Test
	void inventoryTrackingAdminPathsMapToTrackingPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/batches", "inventory:batch:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/batches/1", "inventory:batch:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/serials", "inventory:serial:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/serials/1", "inventory:serial:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/traces/batches/1", "inventory:trace:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/inventory/traces/serials/1", "inventory:trace:view");
	}

	@Test
	void reportAdminPathsMapToReportPermissionCodes() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/overview", "report:overview:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/sales-summary", "report:sales:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/sales-summary/traces", "report:sales:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/procurement-summary", "report:procurement:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/procurement-summary/traces",
				"report:procurement:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/inventory-stock-flow", "report:inventory:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/inventory-stock-flow/traces",
				"report:inventory:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/production-execution", "report:production:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/production-execution/traces",
				"report:production:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/cost-collection", "report:cost:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/cost-collection/traces", "report:cost:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/settlement-summary", "report:settlement:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/settlement-summary/traces",
				"report:settlement:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/exceptions", "report:exception:view");
		assertPermissionCode(HttpMethod.GET, "/api/admin/reports/exceptions/traces", "report:exception:view");
	}

	private long createRole(String code, String name, AuthenticatedSession session) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/roles",
				Map.of("code", code, "name", name, "description", "测试", "status", "ENABLED"), session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long createUser(String username, List<Long> roleIds, AuthenticatedSession session) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", username, "displayName", username, "initialPassword", "Qherp@2026!", "status",
						"ENABLED", "roleIds", roleIds),
				session);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return data(response).get("id").longValue();
	}

	private long permissionId(String code) {
		return this.jdbcTemplate.queryForObject("select id from sys_permission where code = ?", Long.class, code);
	}

	private void insertAuditLog(String operatorUsername, String action, String targetType, OffsetDateTime createdAt) {
		this.jdbcTemplate.update("""
				insert into sys_audit_log (
					operator_username, action, target_type, target_id, target_summary,
					request_method, request_path, ip_address, result, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", operatorUsername, action, targetType, "task4-audit-filter", "审计过滤测试", "GET",
				"/api/admin/audit-logs", "127.0.0.1", "SUCCESS", createdAt);
	}

	@Test
	void businessPeriodEndpointsMapToDedicatedPermissions() {
		assertPermissionCode(HttpMethod.GET, "/api/admin/system/business-periods", "system:business-period:view");
		assertPermissionCode(HttpMethod.POST, "/api/admin/system/business-periods", "system:business-period:create");
		assertPermissionCode(HttpMethod.POST, "/api/admin/system/business-periods/generate-monthly",
				"system:business-period:create");
		assertPermissionCode(HttpMethod.PUT, "/api/admin/system/business-periods/1", "system:business-period:update");
		assertPermissionCode(HttpMethod.POST, "/api/admin/system/business-periods/1/lock", "system:business-period:lock");
		assertPermissionCode(HttpMethod.POST, "/api/admin/system/business-periods/1/unlock", "system:business-period:unlock");
	}

	private void assertPermissionCode(HttpMethod method, String path, String expectedPermissionCode) {
		var authorizationManager = new PermissionAuthorizationManager(null);
		var request = new MockHttpServletRequest(method.name(), path);

		String permissionCode = ReflectionTestUtils.invokeMethod(authorizationManager, "permissionCode", request);

		assertThat(permissionCode).isEqualTo(expectedPermissionCode);
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (csrf != null) {
			headers.add(csrf.headerName(), csrf.token());
		}
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String sessionCookie(ResponseEntity<String> response) {
		return response.getHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE)
			.stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID="))
			.findFirst()
			.map((cookie) -> cookie.split(";", 2)[0])
			.orElseThrow();
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
