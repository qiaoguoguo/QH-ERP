package com.qherp.api.system.init;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.audit.SystemAuditLogRepository;
import com.qherp.api.system.permission.SystemPermissionType;
import com.qherp.api.system.permission.SystemPermissionRepository;
import com.qherp.api.system.permission.SystemRolePermissionRepository;
import com.qherp.api.system.role.SystemRoleStatus;
import com.qherp.api.system.role.SystemRoleRepository;
import com.qherp.api.system.user.SystemUserStatus;
import com.qherp.api.system.user.SystemUserRepository;
import com.qherp.api.system.user.SystemUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "qherp.test.context=account-permission")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountPermissionInitializerTests extends PostgresIntegrationTest {

	private static final Map<String, String> DOCUMENTED_PERMISSIONS = Map.ofEntries(
			Map.entry("system:user:view", "查看用户"),
			Map.entry("system:user:create", "创建用户"),
			Map.entry("system:user:update", "更新、启用、停用用户"),
			Map.entry("system:user:reset-password", "重置用户密码"),
			Map.entry("system:role:view", "查看角色"),
			Map.entry("system:role:create", "创建角色"),
			Map.entry("system:role:update", "更新、启用、停用角色"),
			Map.entry("system:role:assign-permission", "分配角色权限"),
			Map.entry("system:permission:view", "查看权限树"),
			Map.entry("system:audit:view", "查看账号权限审计"));

	private static final List<String> MASTER_DATA_MENU_PERMISSIONS = List.of("master", "master:unit",
			"master:warehouse", "master:supplier", "master:customer", "material", "master:material-category",
			"master:material");

	private static final List<String> INVENTORY_MENU_PERMISSIONS = List.of("inventory");

	private static final List<String> QUALITY_MENU_PERMISSIONS = List.of("quality");

	private static final List<String> PROCUREMENT_MENU_PERMISSIONS = List.of("procurement");

	private static final List<String> SALES_MENU_PERMISSIONS = List.of("sales", "sales:project");

	private static final List<String> PRODUCTION_MENU_PERMISSIONS = List.of("production");

	private static final List<String> COST_MENU_PERMISSIONS = List.of("cost");

	private static final List<String> FINANCE_MENU_PERMISSIONS = List.of("finance");

	private static final List<String> REPORT_MENU_PERMISSIONS = List.of("report");

	private static final List<String> REVERSAL_MENU_PERMISSIONS = List.of("reversal");

	private static final List<ExpectedActionPermission> MASTER_DATA_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("master:unit:view", "master:unit", "GET", "/api/admin/master/units/**"),
			new ExpectedActionPermission("master:unit:create", "master:unit", "POST", "/api/admin/master/units"),
			new ExpectedActionPermission("master:unit:update", "master:unit", "PUT", "/api/admin/master/units/**"),
			new ExpectedActionPermission("master:warehouse:view", "master:warehouse", "GET",
					"/api/admin/master/warehouses/**"),
			new ExpectedActionPermission("master:warehouse:create", "master:warehouse", "POST",
					"/api/admin/master/warehouses"),
			new ExpectedActionPermission("master:warehouse:update", "master:warehouse", "PUT",
					"/api/admin/master/warehouses/**"),
			new ExpectedActionPermission("master:supplier:view", "master:supplier", "GET",
					"/api/admin/master/suppliers/**"),
			new ExpectedActionPermission("master:supplier:create", "master:supplier", "POST",
					"/api/admin/master/suppliers"),
			new ExpectedActionPermission("master:supplier:update", "master:supplier", "PUT",
					"/api/admin/master/suppliers/**"),
			new ExpectedActionPermission("master:customer:view", "master:customer", "GET",
					"/api/admin/master/customers/**"),
			new ExpectedActionPermission("master:customer:create", "master:customer", "POST",
					"/api/admin/master/customers"),
			new ExpectedActionPermission("master:customer:update", "master:customer", "PUT",
					"/api/admin/master/customers/**"),
			new ExpectedActionPermission("master:material-category:view", "master:material-category", "GET",
					"/api/admin/master/material-categories/**"),
			new ExpectedActionPermission("master:material-category:create", "master:material-category", "POST",
					"/api/admin/master/material-categories"),
			new ExpectedActionPermission("master:material-category:update", "master:material-category", "PUT",
					"/api/admin/master/material-categories/**"),
			new ExpectedActionPermission("master:material:view", "master:material", "GET",
					"/api/admin/master/materials/**"),
			new ExpectedActionPermission("master:material:create", "master:material", "POST",
					"/api/admin/master/materials"),
			new ExpectedActionPermission("master:material:update", "master:material", "PUT",
					"/api/admin/master/materials/**"));

	private static final List<ExpectedActionPermission> INVENTORY_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("inventory:balance:view", "inventory", "GET",
					"/api/admin/inventory/balances"),
			new ExpectedActionPermission("inventory:reservation:view", "inventory", "GET",
					"/api/admin/inventory/reservations/**"),
			new ExpectedActionPermission("inventory:movement:view", "inventory", "GET",
					"/api/admin/inventory/movements"),
			new ExpectedActionPermission("inventory:batch:view", "inventory", "GET",
					"/api/admin/inventory/batches/**"),
			new ExpectedActionPermission("inventory:serial:view", "inventory", "GET",
					"/api/admin/inventory/serials/**"),
			new ExpectedActionPermission("inventory:trace:view", "inventory", "GET",
					"/api/admin/inventory/traces/**"),
			new ExpectedActionPermission("inventory:document:view", "inventory", "GET",
					"/api/admin/inventory/documents/**"),
			new ExpectedActionPermission("inventory:document:create", "inventory", "POST",
					"/api/admin/inventory/documents"),
			new ExpectedActionPermission("inventory:document:update", "inventory", "PUT",
					"/api/admin/inventory/documents/{id}"),
			new ExpectedActionPermission("inventory:document:post", "inventory", "PUT",
					"/api/admin/inventory/documents/{id}/post"));

	private static final List<ExpectedActionPermission> QUALITY_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("quality:inspection:view", "quality", "GET",
					"/api/admin/quality/inspections/**"),
			new ExpectedActionPermission("quality:inspection:process", "quality", "POST",
					"/api/admin/quality/inspections/{id}/process"),
			new ExpectedActionPermission("quality:status:freeze", "quality", "POST",
					"/api/admin/inventory/quality-transfers/freeze"),
			new ExpectedActionPermission("quality:status:unfreeze", "quality", "POST",
					"/api/admin/inventory/quality-transfers/unfreeze"));

	private static final List<ExpectedActionPermission> PROCUREMENT_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("procurement:order:view", "procurement", "GET",
					"/api/admin/procurement/orders/**"),
			new ExpectedActionPermission("procurement:order:create", "procurement", "POST",
					"/api/admin/procurement/orders"),
			new ExpectedActionPermission("procurement:order:update", "procurement", "PUT",
					"/api/admin/procurement/orders/{id}"),
			new ExpectedActionPermission("procurement:order:confirm", "procurement", "PUT",
					"/api/admin/procurement/orders/{id}/confirm"),
			new ExpectedActionPermission("procurement:order:cancel", "procurement", "PUT",
					"/api/admin/procurement/orders/{id}/cancel"),
			new ExpectedActionPermission("procurement:order:close", "procurement", "PUT",
					"/api/admin/procurement/orders/{id}/close"),
			new ExpectedActionPermission("procurement:receipt:view", "procurement", "GET",
					"/api/admin/procurement/receipts/**"),
			new ExpectedActionPermission("procurement:receipt:create", "procurement", "POST",
					"/api/admin/procurement/orders/{id}/receipts"),
			new ExpectedActionPermission("procurement:receipt:update", "procurement", "PUT",
					"/api/admin/procurement/receipts/{id}"),
			new ExpectedActionPermission("procurement:receipt:post", "procurement", "PUT",
					"/api/admin/procurement/receipts/{id}/post"),
			new ExpectedActionPermission("procurement:return:view", "procurement", "GET",
					"/api/admin/procurement/returns/**"),
			new ExpectedActionPermission("procurement:return:create", "procurement", "POST",
					"/api/admin/procurement/returns"),
			new ExpectedActionPermission("procurement:return:update", "procurement", "PUT",
					"/api/admin/procurement/returns/{id}"),
			new ExpectedActionPermission("procurement:return:post", "procurement", "PUT",
					"/api/admin/procurement/returns/{id}/post"),
			new ExpectedActionPermission("procurement:return:cancel", "procurement", "PUT",
					"/api/admin/procurement/returns/{id}/cancel"));

	private static final List<ExpectedActionPermission> SALES_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("sales:order:view", "sales", "GET", "/api/admin/sales/orders/**"),
			new ExpectedActionPermission("sales:order:create", "sales", "POST", "/api/admin/sales/orders"),
			new ExpectedActionPermission("sales:order:update", "sales", "PUT", "/api/admin/sales/orders/{id}"),
			new ExpectedActionPermission("sales:order:confirm", "sales", "PUT",
					"/api/admin/sales/orders/{id}/confirm"),
			new ExpectedActionPermission("sales:order:cancel", "sales", "PUT",
					"/api/admin/sales/orders/{id}/cancel"),
			new ExpectedActionPermission("sales:order:close", "sales", "PUT",
					"/api/admin/sales/orders/{id}/close"),
			new ExpectedActionPermission("sales:project:view", "sales:project", "GET",
					"/api/admin/sales-projects/**"),
			new ExpectedActionPermission("sales:project:create", "sales:project", "POST",
					"/api/admin/sales-projects"),
			new ExpectedActionPermission("sales:project:update", "sales:project", "PUT",
					"/api/admin/sales-projects/{id}"),
			new ExpectedActionPermission("sales:project:activate", "sales:project", "PUT",
					"/api/admin/sales-projects/{id}/activate"),
			new ExpectedActionPermission("sales:project:close", "sales:project", "PUT",
					"/api/admin/sales-projects/{id}/close"),
			new ExpectedActionPermission("sales:project:cancel", "sales:project", "PUT",
					"/api/admin/sales-projects/{id}/cancel"),
			new ExpectedActionPermission("sales:contract:view", "sales:project", "GET",
					"/api/admin/sales-project-contracts/**"),
			new ExpectedActionPermission("sales:contract:create", "sales:project", "POST",
					"/api/admin/sales-projects/{id}/contracts"),
			new ExpectedActionPermission("sales:contract:update", "sales:project", "PUT",
					"/api/admin/sales-project-contracts/{id}"),
			new ExpectedActionPermission("sales:contract:activate", "sales:project", "PUT",
					"/api/admin/sales-project-contracts/{id}/activate"),
			new ExpectedActionPermission("sales:contract:close", "sales:project", "PUT",
					"/api/admin/sales-project-contracts/{id}/close"),
			new ExpectedActionPermission("sales:contract:terminate", "sales:project", "PUT",
					"/api/admin/sales-project-contracts/{id}/terminate"),
			new ExpectedActionPermission("sales:contract:cancel", "sales:project", "PUT",
					"/api/admin/sales-project-contracts/{id}/cancel"),
			new ExpectedActionPermission("sales:shipment:view", "sales", "GET",
					"/api/admin/sales/shipments/**"),
			new ExpectedActionPermission("sales:shipment:create", "sales", "POST",
					"/api/admin/sales/orders/{id}/shipments"),
			new ExpectedActionPermission("sales:shipment:update", "sales", "PUT",
					"/api/admin/sales/shipments/{id}"),
			new ExpectedActionPermission("sales:shipment:post", "sales", "PUT",
					"/api/admin/sales/shipments/{id}/post"),
			new ExpectedActionPermission("sales:return:view", "sales", "GET", "/api/admin/sales/returns/**"),
			new ExpectedActionPermission("sales:return:create", "sales", "POST", "/api/admin/sales/returns"),
			new ExpectedActionPermission("sales:return:update", "sales", "PUT", "/api/admin/sales/returns/{id}"),
			new ExpectedActionPermission("sales:return:post", "sales", "PUT",
					"/api/admin/sales/returns/{id}/post"),
			new ExpectedActionPermission("sales:return:cancel", "sales", "PUT",
					"/api/admin/sales/returns/{id}/cancel"));

	private static final List<ExpectedActionPermission> PRODUCTION_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("production:work-order:view", "production", "GET",
					"/api/admin/production/work-orders/**"),
			new ExpectedActionPermission("production:work-order:create", "production", "POST",
					"/api/admin/production/work-orders"),
			new ExpectedActionPermission("production:work-order:update", "production", "PUT",
					"/api/admin/production/work-orders/{id}"),
			new ExpectedActionPermission("production:work-order:release", "production", "PUT",
					"/api/admin/production/work-orders/{id}/release"),
			new ExpectedActionPermission("production:work-order:complete", "production", "PUT",
					"/api/admin/production/work-orders/{id}/complete"),
			new ExpectedActionPermission("production:work-order:cancel", "production", "PUT",
					"/api/admin/production/work-orders/{id}/cancel"),
			new ExpectedActionPermission("production:issue:view", "production", "GET",
					"/api/admin/production/work-orders/{id}/material-issues/**"),
			new ExpectedActionPermission("production:issue:create", "production", "POST",
					"/api/admin/production/work-orders/{id}/material-issues"),
			new ExpectedActionPermission("production:issue:update", "production", "PUT",
					"/api/admin/production/work-orders/{id}/material-issues/{issueId}"),
			new ExpectedActionPermission("production:issue:post", "production", "PUT",
					"/api/admin/production/work-orders/{id}/material-issues/{issueId}/post"),
			new ExpectedActionPermission("production:report:view", "production", "GET",
					"/api/admin/production/work-orders/{id}/reports/**"),
			new ExpectedActionPermission("production:report:create", "production", "POST",
					"/api/admin/production/work-orders/{id}/reports"),
			new ExpectedActionPermission("production:report:update", "production", "PUT",
					"/api/admin/production/work-orders/{id}/reports/{reportId}"),
			new ExpectedActionPermission("production:report:post", "production", "PUT",
					"/api/admin/production/work-orders/{id}/reports/{reportId}/post"),
			new ExpectedActionPermission("production:receipt:view", "production", "GET",
					"/api/admin/production/work-orders/{id}/completion-receipts/**"),
			new ExpectedActionPermission("production:receipt:create", "production", "POST",
					"/api/admin/production/work-orders/{id}/completion-receipts"),
			new ExpectedActionPermission("production:receipt:update", "production", "PUT",
					"/api/admin/production/work-orders/{id}/completion-receipts/{receiptId}"),
			new ExpectedActionPermission("production:receipt:post", "production", "PUT",
					"/api/admin/production/work-orders/{id}/completion-receipts/{receiptId}/post"),
			new ExpectedActionPermission("production:material-return:view", "production", "GET",
					"/api/admin/production/material-returns/**"),
			new ExpectedActionPermission("production:material-return:create", "production", "POST",
					"/api/admin/production/material-returns"),
			new ExpectedActionPermission("production:material-return:update", "production", "PUT",
					"/api/admin/production/material-returns/{id}"),
			new ExpectedActionPermission("production:material-return:post", "production", "PUT",
					"/api/admin/production/material-returns/{id}/post"),
			new ExpectedActionPermission("production:material-return:cancel", "production", "PUT",
					"/api/admin/production/material-returns/{id}/cancel"),
			new ExpectedActionPermission("production:material-supplement:view", "production", "GET",
					"/api/admin/production/material-supplements/**"),
			new ExpectedActionPermission("production:material-supplement:create", "production", "POST",
					"/api/admin/production/material-supplements"),
			new ExpectedActionPermission("production:material-supplement:update", "production", "PUT",
					"/api/admin/production/material-supplements/{id}"),
			new ExpectedActionPermission("production:material-supplement:post", "production", "PUT",
					"/api/admin/production/material-supplements/{id}/post"),
			new ExpectedActionPermission("production:material-supplement:cancel", "production", "PUT",
					"/api/admin/production/material-supplements/{id}/cancel"));

	private static final List<ExpectedActionPermission> COST_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("cost:record:view", "cost", "GET", "/api/admin/cost/**"),
			new ExpectedActionPermission("cost:record:create", "cost", "POST", "/api/admin/cost/records"),
			new ExpectedActionPermission("cost:record:update", "cost", "PUT", "/api/admin/cost/records/{id}"));

	private static final List<ExpectedActionPermission> FINANCE_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("finance:receivable:view", "finance", "GET",
					"/api/admin/finance/receivables/**"),
			new ExpectedActionPermission("finance:receivable:create", "finance", "POST",
					"/api/admin/finance/receivables"),
			new ExpectedActionPermission("finance:receivable:update", "finance", "PUT",
					"/api/admin/finance/receivables/{id}"),
			new ExpectedActionPermission("finance:receivable:confirm", "finance", "PUT",
					"/api/admin/finance/receivables/{id}/confirm"),
			new ExpectedActionPermission("finance:receivable:cancel", "finance", "PUT",
					"/api/admin/finance/receivables/{id}/cancel"),
			new ExpectedActionPermission("finance:receivable:close", "finance", "PUT",
					"/api/admin/finance/receivables/{id}/close"),
			new ExpectedActionPermission("finance:receipt:view", "finance", "GET",
					"/api/admin/finance/receipts/**"),
			new ExpectedActionPermission("finance:receipt:create", "finance", "POST",
					"/api/admin/finance/receivables/{id}/receipts"),
			new ExpectedActionPermission("finance:receipt:update", "finance", "PUT",
					"/api/admin/finance/receipts/{id}"),
			new ExpectedActionPermission("finance:receipt:post", "finance", "PUT",
					"/api/admin/finance/receipts/{id}/post"),
			new ExpectedActionPermission("finance:receipt:cancel", "finance", "PUT",
					"/api/admin/finance/receipts/{id}/cancel"),
			new ExpectedActionPermission("finance:payable:view", "finance", "GET",
					"/api/admin/finance/payables/**"),
			new ExpectedActionPermission("finance:payable:create", "finance", "POST",
					"/api/admin/finance/payables"),
			new ExpectedActionPermission("finance:payable:update", "finance", "PUT",
					"/api/admin/finance/payables/{id}"),
			new ExpectedActionPermission("finance:payable:confirm", "finance", "PUT",
					"/api/admin/finance/payables/{id}/confirm"),
			new ExpectedActionPermission("finance:payable:cancel", "finance", "PUT",
					"/api/admin/finance/payables/{id}/cancel"),
			new ExpectedActionPermission("finance:payable:close", "finance", "PUT",
					"/api/admin/finance/payables/{id}/close"),
			new ExpectedActionPermission("finance:payment:view", "finance", "GET",
					"/api/admin/finance/payments/**"),
			new ExpectedActionPermission("finance:payment:create", "finance", "POST",
					"/api/admin/finance/payables/{id}/payments"),
			new ExpectedActionPermission("finance:payment:update", "finance", "PUT",
					"/api/admin/finance/payments/{id}"),
			new ExpectedActionPermission("finance:payment:post", "finance", "PUT",
					"/api/admin/finance/payments/{id}/post"),
			new ExpectedActionPermission("finance:payment:cancel", "finance", "PUT",
					"/api/admin/finance/payments/{id}/cancel"),
			new ExpectedActionPermission("finance:settlement-adjustment:view", "finance", "GET",
					"/api/admin/finance/settlement-adjustments/**"),
			new ExpectedActionPermission("finance:settlement-adjustment:create", "finance", "POST",
					"/api/admin/finance/settlement-adjustments"),
			new ExpectedActionPermission("finance:settlement-adjustment:update", "finance", "PUT",
					"/api/admin/finance/settlement-adjustments/{id}"),
			new ExpectedActionPermission("finance:settlement-adjustment:post", "finance", "PUT",
					"/api/admin/finance/settlement-adjustments/{id}/post"),
			new ExpectedActionPermission("finance:settlement-adjustment:cancel", "finance", "PUT",
					"/api/admin/finance/settlement-adjustments/{id}/cancel"));

	private static final List<ExpectedActionPermission> REPORT_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("report:overview:view", "report", "GET", "/api/admin/reports/overview"),
			new ExpectedActionPermission("report:sales:view", "report", "GET",
					"/api/admin/reports/sales-summary/**"),
			new ExpectedActionPermission("report:procurement:view", "report", "GET",
					"/api/admin/reports/procurement-summary/**"),
			new ExpectedActionPermission("report:inventory:view", "report", "GET",
					"/api/admin/reports/inventory-stock-flow/**"),
			new ExpectedActionPermission("report:production:view", "report", "GET",
					"/api/admin/reports/production-execution/**"),
			new ExpectedActionPermission("report:cost:view", "report", "GET",
					"/api/admin/reports/cost-collection/**"),
			new ExpectedActionPermission("report:settlement:view", "report", "GET",
					"/api/admin/reports/settlement-summary/**"),
			new ExpectedActionPermission("report:exception:view", "report", "GET",
					"/api/admin/reports/exceptions/**"));

	private static final Map<String, String> REPORT_ROUTE_PATHS = Map.ofEntries(
			Map.entry("report:overview:view", "/reports/overview"),
			Map.entry("report:sales:view", "/reports/sales"),
			Map.entry("report:procurement:view", "/reports/procurement"),
			Map.entry("report:inventory:view", "/reports/inventory"),
			Map.entry("report:production:view", "/reports/production"),
			Map.entry("report:cost:view", "/reports/cost"),
			Map.entry("report:settlement:view", "/reports/settlement"),
			Map.entry("report:exception:view", "/reports/exceptions"));

	private static final List<ExpectedActionPermission> REVERSAL_ACTION_PERMISSIONS = List
		.of(new ExpectedActionPermission("business:reversal:view", "reversal", "GET", "/api/admin/reversal-traces"));

	@Autowired
	private AccountPermissionInitializer initializer;

	@Autowired
	private SystemUserRepository userRepository;

	@Autowired
	private SystemRoleRepository roleRepository;

	@Autowired
	private SystemPermissionRepository permissionRepository;

	@Autowired
	private SystemUserRoleRepository userRoleRepository;

	@Autowired
	private SystemRolePermissionRepository rolePermissionRepository;

	@Autowired
	private SystemAuditLogRepository auditLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void initializesAdminRoleAndPermissionsIdempotently() {
		assertThat(userRepository.countByUsername("admin")).isOne();
		assertThat(roleRepository.countByCode("SYSTEM_ADMIN")).isOne();
		assertDocumentedPermissionsInitializedAndAssigned();

		initializer.initialize();

		assertThat(userRepository.countByUsername("admin")).isOne();
		assertThat(roleRepository.countByCode("SYSTEM_ADMIN")).isOne();
		assertDocumentedPermissionsInitializedAndAssigned();

		var admin = userRepository.findByUsername("admin").orElseThrow();
		var systemAdmin = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();

		assertThat(admin.getStatus()).isEqualTo(SystemUserStatus.ENABLED);
		assertThat(admin.getPhone()).isNull();
		assertThat(admin.getEmail()).isNull();
		assertThat(passwordEncoder.matches("Qherp@2026!", admin.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("Admin@123456", admin.getPasswordHash())).isFalse();
		assertThat(systemAdmin.getStatus()).isEqualTo(SystemRoleStatus.ENABLED);
		assertThat(systemAdmin.getDescription()).isNull();
		assertThat(userRoleRepository.existsByUserIdAndRoleId(admin.getId(), systemAdmin.getId())).isTrue();
	}

	@Test
	void inventoryBalanceApiIsSeededOnlyUnderBalancePermission() {
		assertThat(this.permissionRepository.countByCode("inventory:availability:view")).isZero();

		List<String> balanceApiPermissionCodes = this.jdbcTemplate.queryForList("""
				select code
				from sys_permission
				where api_method = 'GET'
				and api_path = '/api/admin/inventory/balances'
				order by code
				""", String.class);

		assertThat(balanceApiPermissionCodes).containsExactly("inventory:balance:view");
	}

	@Test
	void accountPermissionSchemaContainsContractFieldsAndAuditLogTable() {
		assertThat(auditLogRepository.count()).isZero();

		var userColumns = this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = 'sys_user'
				""", String.class);
		var roleColumns = this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = 'sys_role'
				""", String.class);
		var auditColumns = this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = 'sys_audit_log'
				""", String.class);

		assertThat(userColumns).contains("phone", "email");
		assertThat(roleColumns).contains("description");
		assertThat(auditColumns).contains("id", "operator_user_id", "operator_username", "action", "target_type",
				"target_id", "target_summary", "request_method", "request_path", "ip_address", "result",
				"error_code", "created_at");

		var materialIndexes = this.jdbcTemplate.queryForList("""
				select indexname
				from pg_indexes
				where schemaname = 'public'
				and tablename = 'mst_material'
				""", String.class);
		assertThat(materialIndexes).contains("idx_mst_material_category_unit", "idx_mst_material_status",
				"idx_mst_material_unit_status", "idx_mst_material_tracking_method");
		assertThat(columns("mst_material")).contains("tracking_method");
	}

	@Test
	void inventorySchemaContainsContractTablesAndIndexes() {
		var balanceColumns = columns("inv_stock_balance");
		var documentColumns = columns("inv_inventory_document");
		var documentLineColumns = columns("inv_inventory_document_line");
		var movementColumns = columns("inv_stock_movement");
		var reservationColumns = columns("inv_stock_reservation");
		var batchColumns = columns("inv_batch");
		var serialColumns = columns("inv_serial");
		var allocationColumns = columns("inv_stock_tracking_allocation");

		assertThat(balanceColumns).contains("id", "warehouse_id", "material_id", "unit_id", "quantity_on_hand",
				"locked_quantity", "created_at", "updated_at", "version", "quality_status", "batch_id",
				"serial_id");
		assertThat(documentColumns).contains("id", "document_no", "document_type", "status", "business_date",
				"reason", "remark", "created_by", "created_at", "updated_by", "updated_at", "posted_by",
				"posted_at", "version");
		assertThat(documentLineColumns).contains("id", "document_id", "line_no", "warehouse_id", "material_id",
				"unit_id", "quantity", "adjustment_direction", "before_quantity", "after_quantity", "remark",
				"created_at", "updated_at");
		assertThat(movementColumns).contains("id", "movement_no", "movement_type", "direction", "warehouse_id",
				"material_id", "unit_id", "quantity", "before_quantity", "after_quantity", "source_type",
				"source_id", "source_line_id", "business_date", "reason", "remark", "operator_name",
				"occurred_at", "quality_status", "batch_id", "serial_id");
		assertThat(reservationColumns).contains("id", "reservation_no", "reservation_type", "status",
				"warehouse_id", "material_id", "unit_id", "quality_status", "quantity", "released_quantity",
				"consumed_quantity", "source_type", "source_id", "source_line_id", "source_document_no",
				"business_date", "reason", "batch_id", "serial_id", "parent_reservation_id");
		assertThat(batchColumns).contains("id", "material_id", "batch_no", "source_type", "source_id",
				"source_line_id", "business_date", "remark", "created_by", "created_at", "updated_by",
				"updated_at", "version");
		assertThat(serialColumns).contains("id", "material_id", "serial_no", "batch_id", "source_type",
				"source_id", "source_line_id", "warehouse_id", "quality_status", "stock_status",
				"last_movement_id", "business_date", "remark", "created_by", "created_at", "updated_by",
				"updated_at", "version");
		assertThat(allocationColumns).contains("id", "allocation_type", "document_type", "document_id",
				"document_line_id", "source_type", "source_id", "source_line_id", "warehouse_id", "material_id",
				"unit_id", "quality_status", "batch_id", "serial_id", "quantity", "movement_id",
				"reservation_id", "remark", "created_by", "created_at", "updated_by", "updated_at", "version");

		assertThat(indexes("inv_stock_balance")).contains("uk_inv_stock_balance_untracked",
				"uk_inv_stock_balance_batch", "uk_inv_stock_balance_serial",
				"idx_inv_stock_balance_warehouse", "idx_inv_stock_balance_material",
				"idx_inv_stock_balance_quality_status", "idx_inv_stock_balance_batch",
				"idx_inv_stock_balance_serial");
		assertThat(indexes("inv_stock_movement")).contains("uk_inv_stock_movement_no",
				"uk_inv_stock_movement_source_untracked", "uk_inv_stock_movement_source_batch",
				"uk_inv_stock_movement_source_serial", "uk_inv_stock_movement_opening_once",
				"idx_inv_stock_movement_business_date", "idx_inv_stock_movement_warehouse_material",
				"idx_inv_stock_movement_quality_status", "idx_inv_stock_movement_batch",
				"idx_inv_stock_movement_serial");
		assertThat(indexes("inv_stock_reservation")).contains("uk_inv_stock_reservation_active_source_untracked",
				"uk_inv_stock_reservation_active_source_batch",
				"uk_inv_stock_reservation_active_source_serial", "idx_inv_stock_reservation_batch",
				"idx_inv_stock_reservation_serial", "idx_inv_stock_reservation_parent");
		assertThat(indexes("inv_batch")).contains("uk_inv_batch_material_no", "idx_inv_batch_material",
				"idx_inv_batch_source");
		assertThat(indexes("inv_serial")).contains("uk_inv_serial_material_no", "idx_inv_serial_material",
				"idx_inv_serial_batch", "idx_inv_serial_stock_status", "idx_inv_serial_source");
		assertThat(indexes("inv_stock_tracking_allocation")).contains("idx_inv_stock_tracking_allocation_document",
				"idx_inv_stock_tracking_allocation_source", "idx_inv_stock_tracking_allocation_batch",
				"idx_inv_stock_tracking_allocation_serial");
		assertThat(indexes("inv_inventory_document")).contains("uk_inv_inventory_document_no",
				"idx_inv_inventory_document_business_date");
	}

	@Test
	void productionSchemaContainsContractTablesAndIndexes() {
		var workOrderColumns = columns("mfg_work_order");
		var workOrderMaterialColumns = columns("mfg_work_order_material");
		var materialIssueColumns = columns("mfg_material_issue");
		var materialIssueLineColumns = columns("mfg_material_issue_line");
		var workReportColumns = columns("mfg_work_report");
		var completionReceiptColumns = columns("mfg_completion_receipt");

		assertThat(workOrderColumns).contains("id", "work_order_no", "product_material_id", "bom_id",
				"planned_quantity", "reported_quantity", "qualified_quantity", "defective_quantity",
				"received_quantity", "issue_warehouse_id", "receipt_warehouse_id", "planned_start_date",
				"planned_finish_date", "status", "remark", "created_by", "created_at", "updated_by", "updated_at",
				"released_by", "released_at", "completed_by", "completed_at", "cancelled_by", "cancelled_at",
				"version");
		assertThat(workOrderMaterialColumns).contains("id", "work_order_id", "line_no", "bom_item_id",
				"material_id", "unit_id", "required_quantity", "issued_quantity", "loss_rate", "remark",
				"created_at", "updated_at", "version");
		assertThat(materialIssueColumns).contains("id", "issue_no", "work_order_id", "status", "business_date",
				"reason", "remark", "created_by", "created_at", "updated_by", "updated_at", "posted_by",
				"posted_at", "version");
		assertThat(materialIssueLineColumns).contains("id", "issue_id", "work_order_material_id", "line_no",
				"warehouse_id", "material_id", "unit_id", "quantity", "before_quantity", "after_quantity",
				"remark", "created_at", "updated_at");
		assertThat(workReportColumns).contains("id", "report_no", "work_order_id", "status", "business_date",
				"qualified_quantity", "defective_quantity", "reporter_name", "remark", "created_by", "created_at",
				"updated_by", "updated_at", "posted_by", "posted_at", "version");
		assertThat(completionReceiptColumns).contains("id", "receipt_no", "work_order_id", "status",
				"business_date", "receipt_warehouse_id", "quantity", "before_quantity", "after_quantity",
				"remark", "created_by", "created_at", "updated_by", "updated_at", "posted_by", "posted_at",
				"version");

		assertThat(indexes("mfg_work_order")).contains("uk_mfg_work_order_no", "idx_mfg_work_order_status",
				"idx_mfg_work_order_product");
		assertThat(indexes("mfg_work_order_material")).contains("uk_mfg_work_order_material_line",
				"uk_mfg_work_order_material_bom_item", "idx_mfg_work_order_material_order");
		assertThat(indexes("mfg_material_issue")).contains("uk_mfg_material_issue_no",
				"idx_mfg_material_issue_order");
		assertThat(indexes("mfg_work_report")).contains("uk_mfg_work_report_no", "idx_mfg_work_report_order");
		assertThat(indexes("mfg_completion_receipt")).contains("uk_mfg_completion_receipt_no",
				"idx_mfg_completion_receipt_order");
	}

	@Test
	void procurementSchemaContainsContractTablesAndIndexes() {
		var orderColumns = columns("proc_purchase_order");
		var orderLineColumns = columns("proc_purchase_order_line");
		var receiptColumns = columns("proc_purchase_receipt");
		var receiptLineColumns = columns("proc_purchase_receipt_line");

		assertThat(orderColumns).contains("id", "order_no", "supplier_id", "order_date", "expected_arrival_date",
				"status", "remark", "created_by", "created_at", "updated_by", "updated_at", "confirmed_by",
				"confirmed_at", "cancelled_by", "cancelled_at", "closed_by", "closed_at", "version");
		assertThat(orderLineColumns).contains("id", "order_id", "line_no", "material_id", "unit_id", "quantity",
				"received_quantity", "unit_price", "expected_arrival_date", "remark", "created_at", "updated_at",
				"version");
		assertThat(receiptColumns).contains("id", "receipt_no", "order_id", "supplier_id", "warehouse_id",
				"business_date", "status", "remark", "created_by", "created_at", "updated_by", "updated_at",
				"posted_by", "posted_at", "version");
		assertThat(receiptLineColumns).contains("id", "receipt_id", "line_no", "order_line_id", "material_id",
				"unit_id", "ordered_quantity", "received_quantity_before", "remaining_quantity_before",
				"quantity", "before_quantity", "after_quantity", "remark", "created_at", "updated_at");

		assertThat(indexes("proc_purchase_order")).contains("uk_proc_purchase_order_no",
				"idx_proc_purchase_order_supplier", "idx_proc_purchase_order_status_date");
		assertThat(indexes("proc_purchase_order_line")).contains("uk_proc_purchase_order_line_no",
				"uk_proc_purchase_order_line_material", "idx_proc_purchase_order_line_order");
		assertThat(indexes("proc_purchase_receipt")).contains("uk_proc_purchase_receipt_no",
				"idx_proc_purchase_receipt_order", "idx_proc_purchase_receipt_status_date");
		assertThat(indexes("proc_purchase_receipt_line")).contains("uk_proc_purchase_receipt_line_no",
				"uk_proc_purchase_receipt_line_order_line", "idx_proc_purchase_receipt_line_receipt");
	}

	@Test
	void salesSchemaContainsContractTablesIndexesAndInventoryMovementType() {
		var orderColumns = columns("sal_sales_order");
		var orderLineColumns = columns("sal_sales_order_line");
		var shipmentColumns = columns("sal_sales_shipment");
		var shipmentLineColumns = columns("sal_sales_shipment_line");

		assertThat(orderColumns).contains("id", "order_no", "customer_id", "order_date", "expected_ship_date",
				"status", "remark", "created_by", "created_at", "updated_by", "updated_at", "confirmed_by",
				"confirmed_at", "cancelled_by", "cancelled_at", "closed_by", "closed_at", "version");
		assertThat(orderLineColumns).contains("id", "order_id", "line_no", "material_id", "unit_id", "quantity",
				"shipped_quantity", "unit_price", "expected_ship_date", "remark", "created_at", "updated_at",
				"version");
		assertThat(shipmentColumns).contains("id", "shipment_no", "order_id", "customer_id", "warehouse_id",
				"business_date", "status", "remark", "created_by", "created_at", "updated_by", "updated_at",
				"posted_by", "posted_at", "version");
		assertThat(shipmentLineColumns).contains("id", "shipment_id", "line_no", "order_line_id", "material_id",
				"unit_id", "ordered_quantity", "shipped_quantity_before", "remaining_quantity_before", "quantity",
				"before_quantity", "after_quantity", "remark", "created_at", "updated_at");

		assertThat(indexes("sal_sales_order")).contains("uk_sal_sales_order_no", "idx_sal_sales_order_customer",
				"idx_sal_sales_order_status_date", "idx_sal_sales_order_expected_date");
		assertThat(indexes("sal_sales_order_line")).contains("uk_sal_sales_order_line_no",
				"uk_sal_sales_order_line_material", "idx_sal_sales_order_line_order",
				"idx_sal_sales_order_line_material");
		assertThat(indexes("sal_sales_shipment")).contains("uk_sal_sales_shipment_no",
				"idx_sal_sales_shipment_order", "idx_sal_sales_shipment_customer",
				"idx_sal_sales_shipment_warehouse", "idx_sal_sales_shipment_status_date");
		assertThat(indexes("sal_sales_shipment_line")).contains("uk_sal_sales_shipment_line_no",
				"uk_sal_sales_shipment_line_order_line", "idx_sal_sales_shipment_line_shipment",
				"idx_sal_sales_shipment_line_order_line_ref");

		String movementTypeConstraint = this.jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = 'inv_stock_movement'
				and c.conname = 'ck_inv_stock_movement_type'
				""", String.class);
		assertThat(movementTypeConstraint).contains("OPENING", "ADJUSTMENT_INCREASE", "ADJUSTMENT_DECREASE",
				"PRODUCTION_ISSUE", "PRODUCTION_RECEIPT", "PURCHASE_RECEIPT", "SALES_SHIPMENT");
	}

	@Test
	void salesStatusEnumsContainContractValues() throws Exception {
		assertThat(enumConstants("com.qherp.api.system.sales.SalesOrderStatus")).containsExactly("DRAFT",
				"CONFIRMED", "PARTIALLY_SHIPPED", "SHIPPED", "CLOSED", "CANCELLED");
		assertThat(enumConstants("com.qherp.api.system.sales.SalesShipmentStatus")).containsExactly("DRAFT",
				"POSTED");
	}

	@Test
	void costSchemaContainsContractTableAndIndexes() {
		var costRecordColumns = columns("mfg_cost_record");

		assertThat(costRecordColumns).contains("id", "record_no", "work_order_id", "product_material_id",
				"cost_type", "source_type", "source_document_type", "source_document_no", "source_document_id",
				"source_line_id", "work_order_material_id", "material_id", "unit_id", "quantity", "unit_price",
				"amount", "basis_type", "business_date", "status", "remark", "recorded_by", "recorded_at",
				"created_by", "created_at", "updated_by", "updated_at", "version");

		assertThat(indexes("mfg_cost_record")).contains("uk_mfg_cost_record_no",
				"uk_mfg_cost_record_source_line", "uk_mfg_cost_record_source_document",
				"uk_mfg_cost_record_output_trace", "idx_mfg_cost_record_work_order",
				"idx_mfg_cost_record_product", "idx_mfg_cost_record_business_date",
				"idx_mfg_cost_record_cost_type");
	}

	@Test
	void financialSettlementSchemaContainsContractTablesConstraintsAndIndexes() {
		var receivableColumns = columns("fin_receivable");
		var receivableSourceColumns = columns("fin_receivable_source");
		var receiptColumns = columns("fin_receipt");
		var receiptAllocationColumns = columns("fin_receipt_allocation");
		var payableColumns = columns("fin_payable");
		var payableSourceColumns = columns("fin_payable_source");
		var paymentColumns = columns("fin_payment");
		var paymentAllocationColumns = columns("fin_payment_allocation");

		assertThat(receivableColumns).contains("id", "receivable_no", "customer_id", "source_type", "source_id",
				"source_no", "business_date", "due_date", "total_amount", "received_amount", "adjusted_amount",
				"unreceived_amount", "status", "remark", "created_by", "created_at", "updated_by", "updated_at",
				"confirmed_by", "confirmed_at", "closed_by", "closed_at", "cancelled_by", "cancelled_at",
				"version");
		assertThat(receivableSourceColumns).contains("id", "receivable_id", "source_type", "source_id",
				"source_no", "source_line_id", "source_line_no", "source_amount");
		assertThat(receiptColumns).contains("id", "receipt_no", "customer_id", "receipt_date", "amount",
				"method", "status", "remark", "created_by", "created_at", "updated_by", "updated_at",
				"posted_by", "posted_at", "cancelled_by", "cancelled_at", "version");
		assertThat(receiptAllocationColumns).contains("id", "receipt_id", "receivable_id", "allocated_amount");
		assertThat(payableColumns).contains("id", "payable_no", "supplier_id", "source_type", "source_id",
				"source_no", "business_date", "due_date", "total_amount", "paid_amount", "adjusted_amount", "unpaid_amount",
				"status", "remark", "created_by", "created_at", "updated_by", "updated_at", "confirmed_by",
				"confirmed_at", "closed_by", "closed_at", "cancelled_by", "cancelled_at", "version");
		assertThat(payableSourceColumns).contains("id", "payable_id", "source_type", "source_id", "source_no",
				"source_line_id", "source_line_no", "source_amount");
		assertThat(paymentColumns).contains("id", "payment_no", "supplier_id", "payment_date", "amount",
				"method", "status", "remark", "created_by", "created_at", "updated_by", "updated_at",
				"posted_by", "posted_at", "cancelled_by", "cancelled_at", "version");
		assertThat(paymentAllocationColumns).contains("id", "payment_id", "payable_id", "allocated_amount");

		assertThat(indexes("fin_receivable")).contains("uk_fin_receivable_no", "idx_fin_receivable_customer",
				"idx_fin_receivable_status_date", "idx_fin_receivable_due_date", "idx_fin_receivable_source_no");
		assertThat(indexes("fin_receivable_source")).contains("uk_fin_receivable_source_line",
				"idx_fin_receivable_source_receivable", "idx_fin_receivable_source_source");
		assertThat(indexes("fin_receipt")).contains("uk_fin_receipt_no", "idx_fin_receipt_customer",
				"idx_fin_receipt_status_date");
		assertThat(indexes("fin_receipt_allocation")).contains("uk_fin_receipt_allocation_receipt",
				"idx_fin_receipt_allocation_receivable");
		assertThat(indexes("fin_payable")).contains("uk_fin_payable_no", "idx_fin_payable_supplier",
				"idx_fin_payable_status_date", "idx_fin_payable_due_date", "idx_fin_payable_source_no");
		assertThat(indexes("fin_payable_source")).contains("uk_fin_payable_source_line",
				"idx_fin_payable_source_payable", "idx_fin_payable_source_source");
		assertThat(indexes("fin_payment")).contains("uk_fin_payment_no", "idx_fin_payment_supplier",
				"idx_fin_payment_status_date");
		assertThat(indexes("fin_payment_allocation")).contains("uk_fin_payment_allocation_payment",
				"idx_fin_payment_allocation_payable");

		assertThat(constraint("fin_receivable", "ck_fin_receivable_status")).contains("DRAFT", "CONFIRMED",
				"PARTIALLY_RECEIVED", "RECEIVED", "CLOSED", "CANCELLED");
		assertThat(constraint("fin_receipt", "ck_fin_receipt_status")).contains("DRAFT", "POSTED",
				"CANCELLED");
		assertThat(constraint("fin_payable", "ck_fin_payable_status")).contains("DRAFT", "CONFIRMED",
				"PARTIALLY_PAID", "PAID", "CLOSED", "CANCELLED");
		assertThat(constraint("fin_payment", "ck_fin_payment_status")).contains("DRAFT", "POSTED",
				"CANCELLED");
		assertThat(constraint("fin_receivable", "ck_fin_receivable_amount_balance")).contains("total_amount",
				"received_amount", "adjusted_amount", "unreceived_amount");
		assertThat(constraint("fin_payable", "ck_fin_payable_amount_balance")).contains("total_amount",
				"paid_amount", "adjusted_amount", "unpaid_amount");
	}

	@Test
	void reversalSchemaContainsContractTablesConstraintsIndexesAndEnums() throws Exception {
		assertThat(columns("biz_reversal_link")).contains("id", "source_type", "source_id", "source_line_id",
				"reverse_type", "reverse_id", "reverse_line_id", "business_date", "quantity", "amount", "created_by",
				"created_at");
		assertThat(columns("sal_sales_return")).contains("id", "return_no", "customer_id", "source_shipment_id",
				"source_shipment_no", "warehouse_id", "business_date", "status", "total_amount",
				"client_request_id", "remark", "created_by", "created_at", "updated_by", "updated_at", "posted_by",
				"posted_at", "cancelled_by", "cancelled_at", "version");
		assertThat(columns("sal_sales_return_line")).contains("id", "return_id", "source_shipment_line_id",
				"sales_order_line_id", "material_id", "unit_id", "line_no", "returned_quantity_before",
				"returnable_quantity_before", "quantity", "unit_price", "amount", "reason", "stock_movement_id");
		assertThat(columns("proc_purchase_return")).contains("id", "return_no", "supplier_id", "source_receipt_id",
				"source_receipt_no", "warehouse_id", "business_date", "status", "total_amount",
				"client_request_id", "remark", "created_by", "created_at", "updated_by", "updated_at", "posted_by",
				"posted_at", "cancelled_by", "cancelled_at", "version");
		assertThat(columns("proc_purchase_return_line")).contains("id", "return_id", "source_receipt_line_id",
				"purchase_order_line_id", "material_id", "unit_id", "line_no", "returned_quantity_before",
				"returnable_quantity_before", "quantity", "unit_price", "amount", "reason", "stock_movement_id");
		assertThat(columns("mfg_material_return")).contains("id", "return_no", "work_order_id", "source_issue_id",
				"warehouse_id", "business_date", "status", "client_request_id", "remark", "created_by",
				"created_at", "updated_by", "updated_at", "posted_by", "posted_at", "cancelled_by",
				"cancelled_at", "version");
		assertThat(columns("mfg_material_return_line")).contains("id", "return_id", "source_issue_line_id",
				"work_order_material_id", "material_id", "unit_id", "line_no", "returned_quantity_before",
				"returnable_quantity_before", "quantity", "reason", "stock_movement_id", "cost_record_id");
		assertThat(columns("mfg_material_supplement")).contains("id", "supplement_no", "work_order_id",
				"warehouse_id", "business_date", "status", "client_request_id", "remark", "created_by",
				"created_at", "updated_by", "updated_at", "posted_by", "posted_at", "cancelled_by",
				"cancelled_at", "version");
		assertThat(columns("mfg_material_supplement_line")).contains("id", "supplement_id", "work_order_material_id",
				"material_id", "unit_id", "line_no", "issued_quantity_before", "supplemented_quantity_before",
				"available_stock_quantity_before", "quantity", "reason", "stock_movement_id", "cost_record_id");
		assertThat(columns("fin_settlement_adjustment")).contains("id", "adjustment_no", "settlement_side",
				"adjustment_type", "source_type", "source_id", "target_id", "business_date", "amount", "status",
				"remark", "client_request_id", "created_by", "created_at", "updated_by", "updated_at", "posted_by",
				"posted_at", "cancelled_by", "cancelled_at", "version");

		assertThat(indexes("biz_reversal_link")).contains("uk_biz_reversal_link_reverse_line",
				"uk_biz_reversal_link_source_reverse", "idx_biz_reversal_link_source",
				"idx_biz_reversal_link_reverse");
		assertThat(indexes("sal_sales_return")).contains("uk_sal_sales_return_no",
				"uk_sal_sales_return_client_request", "idx_sal_sales_return_customer",
				"idx_sal_sales_return_status_date", "idx_sal_sales_return_source");
		assertThat(indexes("proc_purchase_return")).contains("uk_proc_purchase_return_no",
				"uk_proc_purchase_return_client_request", "idx_proc_purchase_return_supplier",
				"idx_proc_purchase_return_status_date", "idx_proc_purchase_return_source");
		assertThat(indexes("mfg_material_return")).contains("uk_mfg_material_return_no",
				"uk_mfg_material_return_client_request", "idx_mfg_material_return_work_order",
				"idx_mfg_material_return_status_date", "idx_mfg_material_return_source");
		assertThat(indexes("mfg_material_supplement")).contains("uk_mfg_material_supplement_no",
				"uk_mfg_material_supplement_client_request", "idx_mfg_material_supplement_work_order",
				"idx_mfg_material_supplement_status_date", "idx_mfg_material_supplement_warehouse");
		assertThat(indexes("fin_settlement_adjustment")).contains("uk_fin_settlement_adjustment_no",
				"uk_fin_settlement_adjustment_client_request", "idx_fin_settlement_adjustment_target",
				"idx_fin_settlement_adjustment_source", "idx_fin_settlement_adjustment_status_date");

		assertThat(constraint("biz_reversal_link", "ck_biz_reversal_link_has_quantity_or_amount"))
			.contains("quantity", "amount");
		assertThat(constraint("sal_sales_return", "ck_sal_sales_return_status")).contains("DRAFT", "POSTED",
				"CANCELLED");
		assertThat(constraint("fin_settlement_adjustment", "ck_fin_settlement_adjustment_type"))
			.contains("RETURN_OFFSET", "REFUND", "PAYMENT_OFFSET");
		assertThat(constraint("inv_stock_movement", "ck_inv_stock_movement_type")).contains("SALES_RETURN_IN",
				"PURCHASE_RETURN_OUT", "PRODUCTION_MATERIAL_RETURN_IN", "PRODUCTION_MATERIAL_SUPPLEMENT_OUT",
				"BUSINESS_REVERSAL");
		assertThat(constraint("mfg_cost_record", "ck_mfg_cost_record_source_document_type"))
			.contains("PRODUCTION_MATERIAL_RETURN", "PRODUCTION_MATERIAL_SUPPLEMENT");

		assertThat(enumConstants("com.qherp.api.system.inventory.InventoryMovementType")).contains("SALES_RETURN_IN",
				"PURCHASE_RETURN_OUT", "PRODUCTION_MATERIAL_RETURN_IN", "PRODUCTION_MATERIAL_SUPPLEMENT_OUT",
				"BUSINESS_REVERSAL");
		assertThat(enumConstants("com.qherp.api.system.inventory.InventoryTrackingMethod"))
			.containsExactly("NONE", "BATCH", "SERIAL");
		assertThat(enumConstants("com.qherp.api.system.inventory.InventoryTrackingAllocationType"))
			.containsExactly("INBOUND", "OUTBOUND", "QUALITY_TRANSFER", "SOURCE_INHERIT");
		assertThat(enumConstants("com.qherp.api.system.cost.CostSourceDocumentType"))
			.contains("PRODUCTION_MATERIAL_RETURN", "PRODUCTION_MATERIAL_SUPPLEMENT");
		assertThat(enumConstants("com.qherp.api.system.reversal.ReversalDocumentStatus")).containsExactly("DRAFT",
				"POSTED", "CANCELLED");
		assertThat(enumConstants("com.qherp.api.system.reversal.ReversalSourceType")).contains("SALES_SHIPMENT",
				"SALES_SHIPMENT_LINE", "PURCHASE_RECEIPT", "PURCHASE_RECEIPT_LINE",
				"PRODUCTION_MATERIAL_ISSUE", "PRODUCTION_MATERIAL_ISSUE_LINE", "RECEIVABLE", "RECEIPT",
				"PAYABLE", "PAYMENT", "COST_RECORD", "SALES_RETURN", "PURCHASE_RETURN",
				"PRODUCTION_MATERIAL_RETURN", "PRODUCTION_MATERIAL_SUPPLEMENT", "SETTLEMENT_ADJUSTMENT");
	}

	@Test
	void financialSettlementStatusEnumsContainContractValues() throws Exception {
		assertThat(enumConstants("com.qherp.api.system.finance.ReceivableStatus")).containsExactly("DRAFT",
				"CONFIRMED", "PARTIALLY_RECEIVED", "RECEIVED", "CLOSED", "CANCELLED");
		assertThat(enumConstants("com.qherp.api.system.finance.ReceiptStatus")).containsExactly("DRAFT", "POSTED",
				"CANCELLED");
		assertThat(enumConstants("com.qherp.api.system.finance.PayableStatus")).containsExactly("DRAFT",
				"CONFIRMED", "PARTIALLY_PAID", "PAID", "CLOSED", "CANCELLED");
		assertThat(enumConstants("com.qherp.api.system.finance.PaymentStatus")).containsExactly("DRAFT", "POSTED",
				"CANCELLED");
	}

	@Test
	void inventoryErrorCodesAreRegistered() {
		assertThat(ApiErrorCode.INVENTORY_DOCUMENT_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(ApiErrorCode.INVENTORY_DOCUMENT_TYPE_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_DOCUMENT_STATUS_INVALID.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_DOCUMENT_POSTED_IMMUTABLE.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_DOCUMENT_EMPTY_LINES.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_DOCUMENT_DUPLICATE_LINE.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_QUANTITY_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_STOCK_NOT_ENOUGH.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_WAREHOUSE_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_MATERIAL_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_UNIT_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_OPENING_EXISTS.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_DUPLICATE_POST.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_MOVEMENT_SOURCE_DUPLICATED.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_TRACKING_METHOD_IMMUTABLE.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_BATCH_REQUIRED.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_SERIAL_REQUIRED.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_SERIAL_DUPLICATED.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_TRACKING_QUANTITY_MISMATCH.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.INVENTORY_TRACKING_STOCK_NOT_ENOUGH.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_TRACKING_NOT_AVAILABLE.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.INVENTORY_TRACKING_SOURCE_MISMATCH.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void productionErrorCodesAreRegistered() {
		assertThat(ApiErrorCode.PRODUCTION_WORK_ORDER_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(ApiErrorCode.PRODUCTION_WORK_ORDER_STATUS_INVALID.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_PRODUCT_MATERIAL_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_BOM_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_BOM_EMPTY_ITEMS.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_WAREHOUSE_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_MATERIAL_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_UNIT_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_QUANTITY_INVALID.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_ISSUE_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(ApiErrorCode.PRODUCTION_ISSUE_EMPTY_LINES.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ApiErrorCode.PRODUCTION_ISSUE_EXCEEDS_REQUIRED.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_STOCK_NOT_ENOUGH.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_REPORT_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(ApiErrorCode.PRODUCTION_REPORT_EXCEEDS_PLAN.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_RECEIPT_NOT_FOUND.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(ApiErrorCode.PRODUCTION_RECEIPT_EXCEEDS_REPORTED.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_DOCUMENT_POSTED_IMMUTABLE.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_DUPLICATE_POST.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ApiErrorCode.PRODUCTION_MOVEMENT_SOURCE_DUPLICATED.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void procurementErrorCodesAreRegistered() {
		assertErrorCode("PROCUREMENT_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("PROCUREMENT_RECEIPT_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("PROCUREMENT_ORDER_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_RECEIPT_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_ORDER_EMPTY_LINES", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_RECEIPT_EMPTY_LINES", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_SUPPLIER_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_WAREHOUSE_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_MATERIAL_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_UNIT_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_QUANTITY_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_UNIT_PRICE_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("PROCUREMENT_ORDER_DUPLICATE_LINE", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_RECEIPT_DUPLICATE_LINE", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_RECEIPT_EXCEEDS_ORDER", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_RECEIPT_POSTED_IMMUTABLE", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_DUPLICATE_POST", HttpStatus.CONFLICT);
		assertErrorCode("PROCUREMENT_MOVEMENT_SOURCE_DUPLICATED", HttpStatus.CONFLICT);
	}

	@Test
	void salesErrorCodesAreRegistered() {
		assertErrorCode("SALES_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("SALES_SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("SALES_ORDER_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("SALES_SHIPMENT_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("SALES_ORDER_EMPTY_LINES", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_SHIPMENT_EMPTY_LINES", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_CUSTOMER_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_WAREHOUSE_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_MATERIAL_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_MATERIAL_NOT_SELLABLE", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_UNIT_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_QUANTITY_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_UNIT_PRICE_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("SALES_ORDER_DUPLICATE_LINE", HttpStatus.CONFLICT);
		assertErrorCode("SALES_SHIPMENT_DUPLICATE_LINE", HttpStatus.CONFLICT);
		assertErrorCode("SALES_SHIPMENT_EXCEEDS_ORDER", HttpStatus.CONFLICT);
		assertErrorCode("SALES_SHIPMENT_LINE_SOURCE_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("SALES_STOCK_NOT_ENOUGH", HttpStatus.CONFLICT);
		assertErrorCode("SALES_SHIPMENT_POSTED_IMMUTABLE", HttpStatus.CONFLICT);
		assertErrorCode("SALES_DUPLICATE_POST", HttpStatus.CONFLICT);
		assertErrorCode("SALES_MOVEMENT_SOURCE_DUPLICATED", HttpStatus.CONFLICT);
	}

	@Test
	void costErrorCodesAreRegistered() {
		assertErrorCode("COST_RECORD_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("COST_WORK_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("COST_WORK_ORDER_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("COST_SOURCE_DOCUMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("COST_SOURCE_DOCUMENT_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("COST_SOURCE_DUPLICATED", HttpStatus.CONFLICT);
		assertErrorCode("COST_TYPE_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("COST_BASIS_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("COST_QUANTITY_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("COST_AMOUNT_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("COST_GENERATED_RECORD_IMMUTABLE", HttpStatus.CONFLICT);
	}

	@Test
	void financeErrorCodesAreRegistered() {
		assertErrorCode("FINANCE_RECEIVABLE_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("FINANCE_RECEIPT_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("FINANCE_PAYABLE_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("FINANCE_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("FINANCE_SOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("FINANCE_SOURCE_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("FINANCE_SOURCE_DUPLICATED", HttpStatus.CONFLICT);
		assertErrorCode("FINANCE_AMOUNT_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("FINANCE_ALLOCATION_EXCEEDS_BALANCE", HttpStatus.CONFLICT);
		assertErrorCode("FINANCE_STATUS_NOT_ALLOWED", HttpStatus.CONFLICT);
		assertErrorCode("FINANCE_POSTED_IMMUTABLE", HttpStatus.CONFLICT);
		assertErrorCode("FINANCE_CONCURRENT_MODIFICATION", HttpStatus.CONFLICT);
		assertErrorCode("FINANCE_DUE_DATE_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("FINANCE_METHOD_INVALID", HttpStatus.BAD_REQUEST);
	}

	@Test
	void reportErrorCodesAreRegistered() {
		assertErrorCode("REPORT_DATE_RANGE_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("REPORT_PARAMETER_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("REPORT_TRACE_KEY_INVALID", HttpStatus.BAD_REQUEST);
	}

	@Test
	void reversalErrorCodesAreRegistered() {
		assertErrorCode("REVERSAL_SOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
		assertErrorCode("REVERSAL_SOURCE_STATUS_INVALID", HttpStatus.CONFLICT);
		assertErrorCode("REVERSAL_STATUS_NOT_ALLOWED", HttpStatus.CONFLICT);
		assertErrorCode("REVERSAL_QUANTITY_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("REVERSAL_AMOUNT_INVALID", HttpStatus.BAD_REQUEST);
		assertErrorCode("REVERSAL_QUANTITY_EXCEEDS_AVAILABLE", HttpStatus.CONFLICT);
		assertErrorCode("REVERSAL_AMOUNT_EXCEEDS_AVAILABLE", HttpStatus.CONFLICT);
		assertErrorCode("REVERSAL_STOCK_INSUFFICIENT", HttpStatus.CONFLICT);
		assertErrorCode("REVERSAL_DUPLICATED", HttpStatus.CONFLICT);
		assertErrorCode("REVERSAL_POSTED_IMMUTABLE", HttpStatus.CONFLICT);
		assertErrorCode("REVERSAL_TRACE_RESTRICTED", HttpStatus.FORBIDDEN);
		assertErrorCode("REVERSAL_CONCURRENT_MODIFICATION", HttpStatus.CONFLICT);
	}

	@Test
	void initializesPermissionTreeAndApiMetadata() {
		var systemMenu = permissionRepository.findByCode("system").orElseThrow();
		var userMenu = permissionRepository.findByCode("system:user").orElseThrow();
		var userCreate = permissionRepository.findByCode("system:user:create").orElseThrow();
		var permissionView = permissionRepository.findByCode("system:permission:view").orElseThrow();
		var auditView = permissionRepository.findByCode("system:audit:view").orElseThrow();

		assertThat(systemMenu.getType()).isEqualTo(SystemPermissionType.MENU);
		assertThat(systemMenu.getParentId()).isNull();
		assertThat(systemMenu.getRoutePath()).isEqualTo("/system");
		assertThat(systemMenu.getApiMethod()).isNull();
		assertThat(systemMenu.getApiPath()).isNull();

		assertThat(userMenu.getType()).isEqualTo(SystemPermissionType.MENU);
		assertThat(userMenu.getParentId()).isEqualTo(systemMenu.getId());
		assertThat(userMenu.getRoutePath()).isEqualTo("/system/users");

		assertThat(userCreate.getType()).isEqualTo(SystemPermissionType.ACTION);
		assertThat(userCreate.getParentId()).isEqualTo(userMenu.getId());
		assertThat(userCreate.getRoutePath()).isEqualTo("/system/users");
		assertThat(userCreate.getApiMethod()).isEqualTo("POST");
		assertThat(userCreate.getApiPath()).isEqualTo("/api/admin/users");

		assertThat(permissionView.getType()).isEqualTo(SystemPermissionType.ACTION);
		assertThat(permissionView.getApiMethod()).isEqualTo("GET");
		assertThat(permissionView.getApiPath()).isEqualTo("/api/admin/permissions/tree");

		assertThat(auditView.getType()).isEqualTo(SystemPermissionType.ACTION);
		assertThat(auditView.getApiMethod()).isEqualTo("GET");
		assertThat(auditView.getApiPath()).isEqualTo("/api/admin/audit-logs");

		MASTER_DATA_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		MASTER_DATA_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		INVENTORY_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		INVENTORY_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		QUALITY_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		QUALITY_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		PROCUREMENT_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		PROCUREMENT_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		SALES_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		SALES_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		PRODUCTION_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		PRODUCTION_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		COST_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		COST_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		FINANCE_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		FINANCE_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});

		REPORT_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		REPORT_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});
		REPORT_ROUTE_PATHS.forEach((code, routePath) -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getRoutePath()).as(code).isEqualTo(routePath);
		});
		REVERSAL_MENU_PERMISSIONS.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(permission.getApiMethod()).as(code).isNull();
			assertThat(permission.getApiPath()).as(code).isNull();
		});

		REVERSAL_ACTION_PERMISSIONS.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();

			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
		});
	}

	@Test
	void initializesBusinessPeriodPermissionsAndAssignsThemToSystemAdmin() {
		var systemAdmin = this.roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
		List<ExpectedActionPermission> actions = List.of(
				new ExpectedActionPermission("system:business-period:view", "system:business-period", "GET",
						"/api/admin/system/business-periods/**"),
				new ExpectedActionPermission("system:business-period:create", "system:business-period", "POST",
						"/api/admin/system/business-periods"),
				new ExpectedActionPermission("system:business-period:update", "system:business-period", "PUT",
						"/api/admin/system/business-periods/{id}"),
				new ExpectedActionPermission("system:business-period:lock", "system:business-period", "POST",
						"/api/admin/system/business-periods/{id}/lock"),
				new ExpectedActionPermission("system:business-period:unlock", "system:business-period", "POST",
						"/api/admin/system/business-periods/{id}/unlock"));

		var menu = this.permissionRepository.findByCode("system:business-period").orElseThrow();
		assertThat(menu.getType()).isEqualTo(SystemPermissionType.MENU);
		actions.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(permission.getParentId()).isEqualTo(menu.getId());
			assertThat(permission.getApiMethod()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).isEqualTo(expected.apiPath());
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId()))
				.isTrue();
		});
	}

	@Test
	void initializesStage021PermissionsAndAssignsThemToSystemAdmin() {
		var systemAdmin = this.roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
		List<String> menus = List.of("master:unit-conversion", "master:coding-rule");
		List<ExpectedActionPermission> actions = List.of(
				new ExpectedActionPermission("master:unit-conversion:view", "master:unit-conversion", "GET",
						"/api/admin/master/unit-conversions/**"),
				new ExpectedActionPermission("master:unit-conversion:create", "master:unit-conversion", "POST",
						"/api/admin/master/unit-conversions"),
				new ExpectedActionPermission("master:unit-conversion:update", "master:unit-conversion", "PUT",
						"/api/admin/master/unit-conversions/{id}"),
				new ExpectedActionPermission("master:unit-conversion:enable", "master:unit-conversion", "PUT",
						"/api/admin/master/unit-conversions/{id}/enable"),
				new ExpectedActionPermission("master:unit-conversion:disable", "master:unit-conversion", "PUT",
						"/api/admin/master/unit-conversions/{id}/disable"),
				new ExpectedActionPermission("master:coding-rule:view", "master:coding-rule", "GET",
						"/api/admin/coding-rules/**"),
				new ExpectedActionPermission("master:coding-rule:create", "master:coding-rule", "POST",
						"/api/admin/coding-rules"),
				new ExpectedActionPermission("master:coding-rule:update", "master:coding-rule", "PUT",
						"/api/admin/coding-rules/{id}"),
				new ExpectedActionPermission("master:coding-rule:enable", "master:coding-rule", "PUT",
						"/api/admin/coding-rules/{id}/enable"),
				new ExpectedActionPermission("master:coding-rule:disable", "master:coding-rule", "PUT",
						"/api/admin/coding-rules/{id}/disable"),
				new ExpectedActionPermission("master:coding-rule:generate", "master:coding-rule", "POST",
						"/api/admin/coding-rules/generate"),
				new ExpectedActionPermission("master:material-cost:view", "master:material", "GET",
						"/api/admin/master/materials/**"),
				new ExpectedActionPermission("master:material-cost:update", "master:material", "PUT",
						"/api/admin/master/materials/{id}"),
				new ExpectedActionPermission("master:customer-settlement:view", "master:customer", "GET",
						"/api/admin/master/customers/{id}/settlement-tax"),
				new ExpectedActionPermission("master:customer-settlement:update", "master:customer", "PUT",
						"/api/admin/master/customers/{id}/settlement-tax"),
				new ExpectedActionPermission("master:customer-settlement:sensitive-view", "master:customer", null,
						null),
				new ExpectedActionPermission("master:customer-settlement:sensitive-update", "master:customer", null,
						null),
				new ExpectedActionPermission("master:supplier-settlement:view", "master:supplier", "GET",
						"/api/admin/master/suppliers/{id}/settlement-tax"),
				new ExpectedActionPermission("master:supplier-settlement:update", "master:supplier", "PUT",
						"/api/admin/master/suppliers/{id}/settlement-tax"),
				new ExpectedActionPermission("master:supplier-settlement:sensitive-view", "master:supplier", null,
						null),
				new ExpectedActionPermission("master:supplier-settlement:sensitive-update", "master:supplier", null,
						null),
				new ExpectedActionPermission("material:bom-eco:view", "material:bom", "GET",
						"/api/admin/bom-engineering-changes/**"),
				new ExpectedActionPermission("material:bom-eco:create", "material:bom", "POST",
						"/api/admin/bom-engineering-changes"),
				new ExpectedActionPermission("material:bom-eco:update", "material:bom", "PUT",
						"/api/admin/bom-engineering-changes/{id}"),
				new ExpectedActionPermission("material:bom-eco:apply", "material:bom", "PUT",
						"/api/admin/bom-engineering-changes/{id}/apply"),
				new ExpectedActionPermission("material:bom-eco:cancel", "material:bom", "PUT",
						"/api/admin/bom-engineering-changes/{id}/cancel"),
				new ExpectedActionPermission("material:substitute:view", "material:bom", "GET",
						"/api/admin/material-substitutes/**"),
				new ExpectedActionPermission("material:substitute:create", "material:bom", "POST",
						"/api/admin/material-substitutes"),
				new ExpectedActionPermission("material:substitute:update", "material:bom", "PUT",
						"/api/admin/material-substitutes/{id}"),
				new ExpectedActionPermission("material:substitute:enable", "material:bom", "PUT",
						"/api/admin/material-substitutes/{id}/enable"),
				new ExpectedActionPermission("material:substitute:disable", "material:bom", "PUT",
						"/api/admin/material-substitutes/{id}/disable"));

		menus.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId()))
				.as(code)
				.isTrue();
		});
		actions.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();
			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId()))
				.as(expected.code())
				.isTrue();
		});
	}

	@Test
	void initializesStage022PlatformPermissionsAndAssignsThemToSystemAdmin() {
		var systemAdmin = this.roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
		List<String> menus = List.of("platform", "platform:approval");
		List<ExpectedActionPermission> actions = List.of(
				new ExpectedActionPermission("platform:approval:view", "platform:approval", "GET",
						"/api/admin/approvals/**"),
				new ExpectedActionPermission("platform:approval:cancel", "platform:approval", "POST",
						"/api/admin/approvals/{id}/cancel"),
				new ExpectedActionPermission("platform:todo:view", "platform:approval", "GET",
						"/api/admin/approval-tasks/**"),
				new ExpectedActionPermission("platform:message:view", "platform", "GET", "/api/admin/messages/**"),
				new ExpectedActionPermission("platform:message:read", "platform", "PUT", "/api/admin/messages/**"),
				new ExpectedActionPermission("platform:attachment:view", "platform", "GET",
						"/api/admin/attachments/**"),
				new ExpectedActionPermission("platform:attachment:upload", "platform", "POST",
						"/api/admin/attachments"),
				new ExpectedActionPermission("platform:attachment:download", "platform", "GET",
						"/api/admin/attachments/{id}/download"),
				new ExpectedActionPermission("platform:attachment:delete", "platform", "PUT",
						"/api/admin/attachments/{id}/delete"),
				new ExpectedActionPermission("platform:document-task:view", "platform", "GET",
						"/api/admin/document-tasks/**"),
				new ExpectedActionPermission("platform:document-task:view-all", "platform", null, null),
				new ExpectedActionPermission("platform:document-task:cancel", "platform", "POST",
						"/api/admin/document-tasks/{id}/cancel"),
				new ExpectedActionPermission("platform:document-task:download", "platform", "GET",
						"/api/admin/document-tasks/{id}/download"),
				new ExpectedActionPermission("platform:print:generate", "platform", null, "/api/admin/print-**"));

		menus.forEach(code -> {
			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(permission.getType()).as(code).isEqualTo(SystemPermissionType.MENU);
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId()))
				.as(code)
				.isTrue();
		});
		actions.forEach(expected -> {
			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			var parent = this.permissionRepository.findByCode(expected.parentCode()).orElseThrow();
			assertThat(permission.getType()).as(expected.code()).isEqualTo(SystemPermissionType.ACTION);
			assertThat(permission.getParentId()).as(expected.code()).isEqualTo(parent.getId());
			assertThat(permission.getApiMethod()).as(expected.code()).isEqualTo(expected.apiMethod());
			assertThat(permission.getApiPath()).as(expected.code()).isEqualTo(expected.apiPath());
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId()))
				.as(expected.code())
				.isTrue();
		});
	}

	private void assertDocumentedPermissionsInitializedAndAssigned() {
		var systemAdmin = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();

		DOCUMENTED_PERMISSIONS.forEach((code, name) -> {
			assertThat(permissionRepository.countByCode(code)).as(code).isOne();

			var permission = permissionRepository.findByCode(code).orElseThrow();
			assertThat(rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId()))
				.as(code)
				.isTrue();
		});

		MASTER_DATA_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		MASTER_DATA_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		INVENTORY_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		INVENTORY_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		QUALITY_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		QUALITY_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		PROCUREMENT_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		PROCUREMENT_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		SALES_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		SALES_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		PRODUCTION_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		PRODUCTION_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		COST_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		COST_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		FINANCE_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		FINANCE_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		REPORT_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		REPORT_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
		REVERSAL_MENU_PERMISSIONS.forEach(code -> {
			assertThat(this.permissionRepository.countByCode(code)).as(code).isOne();

			var permission = this.permissionRepository.findByCode(code).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(code)
				.isTrue();
		});
		REVERSAL_ACTION_PERMISSIONS.forEach(expected -> {
			assertThat(this.permissionRepository.countByCode(expected.code())).as(expected.code()).isOne();

			var permission = this.permissionRepository.findByCode(expected.code()).orElseThrow();
			assertThat(this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(),
					permission.getId()))
				.as(expected.code())
				.isTrue();
		});
	}

	private void assertErrorCode(String code, HttpStatus expectedStatus) {
		assertThat(ApiErrorCode.valueOf(code).httpStatus()).isEqualTo(expectedStatus);
	}

	private List<String> enumConstants(String className) throws ClassNotFoundException {
		Class<?> enumClass = Class.forName(className);
		return Arrays.stream(enumClass.getEnumConstants()).map(Object::toString).toList();
	}

	private List<String> columns(String tableName) {
		return this.jdbcTemplate.queryForList("""
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				and table_name = ?
				""", String.class, tableName);
	}

	private List<String> indexes(String tableName) {
		return this.jdbcTemplate.queryForList("""
				select indexname
				from pg_indexes
				where schemaname = 'public'
				and tablename = ?
				""", String.class, tableName);
	}

	private String constraint(String tableName, String constraintName) {
		return this.jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = ?
				and c.conname = ?
				""", String.class, tableName, constraintName);
	}

	private record ExpectedActionPermission(String code, String parentCode, String apiMethod, String apiPath) {
	}

}
