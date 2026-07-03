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

	private static final List<String> PRODUCTION_MENU_PERMISSIONS = List.of("production");

	private static final List<String> COST_MENU_PERMISSIONS = List.of("cost");

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
			new ExpectedActionPermission("inventory:movement:view", "inventory", "GET",
					"/api/admin/inventory/movements"),
			new ExpectedActionPermission("inventory:document:view", "inventory", "GET",
					"/api/admin/inventory/documents/**"),
			new ExpectedActionPermission("inventory:document:create", "inventory", "POST",
					"/api/admin/inventory/documents"),
			new ExpectedActionPermission("inventory:document:update", "inventory", "PUT",
					"/api/admin/inventory/documents/{id}"),
			new ExpectedActionPermission("inventory:document:post", "inventory", "PUT",
					"/api/admin/inventory/documents/{id}/post"));

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
					"/api/admin/production/work-orders/{id}/completion-receipts/{receiptId}/post"));

	private static final List<ExpectedActionPermission> COST_ACTION_PERMISSIONS = List.of(
			new ExpectedActionPermission("cost:record:view", "cost", "GET", "/api/admin/cost/**"),
			new ExpectedActionPermission("cost:record:create", "cost", "POST", "/api/admin/cost/records"),
			new ExpectedActionPermission("cost:record:update", "cost", "PUT", "/api/admin/cost/records/{id}"));

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
				"idx_mst_material_unit_status");
	}

	@Test
	void inventorySchemaContainsContractTablesAndIndexes() {
		var balanceColumns = columns("inv_stock_balance");
		var documentColumns = columns("inv_inventory_document");
		var documentLineColumns = columns("inv_inventory_document_line");
		var movementColumns = columns("inv_stock_movement");

		assertThat(balanceColumns).contains("id", "warehouse_id", "material_id", "unit_id", "quantity_on_hand",
				"locked_quantity", "created_at", "updated_at", "version");
		assertThat(documentColumns).contains("id", "document_no", "document_type", "status", "business_date",
				"reason", "remark", "created_by", "created_at", "updated_by", "updated_at", "posted_by",
				"posted_at", "version");
		assertThat(documentLineColumns).contains("id", "document_id", "line_no", "warehouse_id", "material_id",
				"unit_id", "quantity", "adjustment_direction", "before_quantity", "after_quantity", "remark",
				"created_at", "updated_at");
		assertThat(movementColumns).contains("id", "movement_no", "movement_type", "direction", "warehouse_id",
				"material_id", "unit_id", "quantity", "before_quantity", "after_quantity", "source_type",
				"source_id", "source_line_id", "business_date", "reason", "remark", "operator_name",
				"occurred_at");

		assertThat(indexes("inv_stock_balance")).contains("uk_inv_stock_balance_warehouse_material",
				"idx_inv_stock_balance_warehouse", "idx_inv_stock_balance_material");
		assertThat(indexes("inv_stock_movement")).contains("uk_inv_stock_movement_no",
				"uk_inv_stock_movement_source", "uk_inv_stock_movement_opening_once",
				"idx_inv_stock_movement_business_date", "idx_inv_stock_movement_warehouse_material");
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
	}

	private void assertErrorCode(String code, HttpStatus expectedStatus) {
		assertThat(ApiErrorCode.valueOf(code).httpStatus()).isEqualTo(expectedStatus);
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

	private record ExpectedActionPermission(String code, String parentCode, String apiMethod, String apiPath) {
	}

}
