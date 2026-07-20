package com.qherp.api.system.stage024;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Stage024MigrationRegressionTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeEach
	void 清理迁移数据库() {
		Flyway.configure()
			.dataSource(dataSource())
			.locations("classpath:db/migration")
			.cleanDisabled(false)
			.load()
			.clean();
	}

	@Test
	void 空库必须迁移到v26并建立采购深化结构权限审批打印和供给契约() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("33");
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertTablesExist(jdbcTemplate, List.of(
				"proc_purchase_requisition",
				"proc_purchase_requisition_line",
				"proc_purchase_inquiry",
				"proc_purchase_inquiry_line",
				"proc_supplier_quote",
				"proc_supplier_quote_line",
				"proc_price_agreement",
				"proc_price_agreement_line",
				"proc_purchase_order_schedule",
				"proc_purchase_order_change",
				"proc_purchase_price_selection"));
		assertEffectiveSupplyModel(jdbcTemplate);
		assertColumnsExist(jdbcTemplate, "proc_purchase_order", List.of(
				"purchase_mode",
				"project_id",
				"currency",
				"exception_approval_instance_id",
				"public_direct_reason",
				"exception_reason",
				"close_reason"));
		assertColumnsExist(jdbcTemplate, "proc_purchase_order_line", List.of(
				"source_requisition_line_id",
				"source_quote_line_id",
				"price_agreement_line_id",
				"price_source_type",
				"tax_rate",
				"tax_excluded_unit_price",
				"tax_included_unit_price",
				"tax_excluded_amount",
				"tax_included_amount"));
		assertColumnsExist(jdbcTemplate, "proc_purchase_receipt_line", List.of(
				"purchase_mode",
				"project_id",
				"value_movement_id",
				"cost_layer_id"));
		assertColumnsExist(jdbcTemplate, "proc_purchase_return", List.of(
				"purchase_mode",
				"project_id"));
		assertColumnsExist(jdbcTemplate, "proc_purchase_return_line", List.of(
				"purchase_mode",
				"project_id",
				"source_value_movement_id",
				"source_cost_layer_id",
				"value_movement_id"));
		assertThat(constraintExists(jdbcTemplate, "proc_purchase_order_line",
				"uk_proc_purchase_order_line_material")).isFalse();
		assertThat(indexExists(jdbcTemplate, "uk_proc_purchase_order_schedule_no")).isTrue();
		assertPermissionsExist(jdbcTemplate, List.of(
				"procurement:requisition:view",
				"procurement:requisition:create",
				"procurement:requisition:update",
				"procurement:requisition:submit",
				"procurement:requisition:approve",
				"procurement:inquiry:view",
				"procurement:inquiry:create",
				"procurement:inquiry:update",
				"procurement:quote:view",
				"procurement:quote:create",
				"procurement:quote:import",
				"procurement:price-agreement:view",
				"procurement:price-agreement:create",
				"procurement:price-agreement:update",
				"procurement:price-agreement:submit",
				"procurement:price-agreement:approve",
				"procurement:order:exception-submit",
				"procurement:order:exception-approve",
				"procurement:order:public-direct",
				"procurement:order:print",
				"procurement:supply:view"));
		assertApprovalScenesExist(jdbcTemplate, List.of(
				"PROCUREMENT_REQUISITION_APPROVAL",
				"PROCUREMENT_PRICE_AGREEMENT_ACTIVATION",
				"PROCUREMENT_ORDER_EXCEPTION_CONFIRM"));
		assertApprovalStep(jdbcTemplate, "PROCUREMENT_REQUISITION_APPROVAL", "procurement:requisition:approve");
		assertApprovalStep(jdbcTemplate, "PROCUREMENT_PRICE_AGREEMENT_ACTIVATION",
				"procurement:price-agreement:approve");
		assertApprovalStep(jdbcTemplate, "PROCUREMENT_ORDER_EXCEPTION_CONFIRM",
				"procurement:order:exception-approve");
		assertPrintTemplate(jdbcTemplate, "PROCUREMENT_ORDER_V1", "PROCUREMENT_ORDER");
	}

	@Test
	void v25代表性存量升级到v26必须默认公共采购替换旧唯一约束并保持种子幂等() {
		migrate("25");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("25");
		RepresentativePurchaseData data = insertRepresentativeV25Purchase(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("33");
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertThat(text(jdbcTemplate, """
				select purchase_mode || ':' || coalesce(project_id::text, 'NULL')
				from proc_purchase_order
				where id = ?
				""", data.orderId())).isEqualTo("PUBLIC:NULL");
		assertThat(text(jdbcTemplate, """
				select currency
				from proc_purchase_order
				where id = ?
				""", data.orderId())).isEqualTo("CNY");
		assertThat(text(jdbcTemplate, """
				select coalesce(source_requisition_line_id::text, 'NULL') || ':' || price_source_type
				from proc_purchase_order_line
				where id = ?
				""", data.orderLineId())).isEqualTo("NULL:MANUAL");
		assertThat(text(jdbcTemplate, """
				select purchase_mode || ':' || coalesce(project_id::text, 'NULL')
				from proc_purchase_receipt_line
				where id = ?
				""", data.receiptLineId())).isEqualTo("PUBLIC:NULL");
		assertThat(constraintExists(jdbcTemplate, "proc_purchase_order_line",
				"uk_proc_purchase_order_line_material")).isFalse();
		assertThat(indexExists(jdbcTemplate, "uk_proc_purchase_order_schedule_no")).isTrue();
		long requisitionId = id(jdbcTemplate, """
				insert into proc_purchase_requisition (
					requisition_no, purchase_mode, required_date, status, purpose, created_by, created_at,
					updated_by, updated_at
				)
				values ('S24_MIG_REQ_001', 'PUBLIC', date '2026-02-01', 'APPROVED', '迁移同物料来源测试',
					'test', now(), 'test', now())
				returning id
				""");
		long requisitionLineId = id(jdbcTemplate, """
				insert into proc_purchase_requisition_line (
					requisition_id, line_no, material_id, unit_id, quantity, ordered_quantity, required_date,
					purpose, created_at, updated_at
				)
				values (?, 10, ?, ?, 3.000000, 0.000000, date '2026-02-01', '迁移同物料来源测试',
					now(), now())
				returning id
				""", requisitionId, data.materialId(), data.unitId());
		jdbcTemplate.update("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					expected_arrival_date, created_at, updated_at, source_requisition_line_id, price_source_type,
					tax_rate, tax_included_unit_price, tax_excluded_unit_price, tax_included_amount,
					tax_excluded_amount
				)
				values (?, 20, ?, ?, 3.000000, 0.000000, 11.000000, date '2026-02-20', now(), now(),
					?, 'MANUAL', 0.130000, 12.430000, 11.000000, 37.29, 33.00)
				""", data.orderId(), data.materialId(), data.unitId(), requisitionLineId);
		assertThat(count(jdbcTemplate, "proc_purchase_order_line", "order_id = ? and material_id = ?",
				data.orderId(), data.materialId())).isEqualTo(2);
		assertThat(count(jdbcTemplate, "platform_approval_instance")).isZero();
		assertThat(count(jdbcTemplate, "platform_approval_task")).isZero();
		long permissionSeedCount = count(jdbcTemplate, "sys_permission", "code like 'procurement:%'");
		long approvalSeedCount = count(jdbcTemplate, "platform_approval_definition",
				"scene_code like 'PROCUREMENT_%'");
		long printSeedCount = count(jdbcTemplate, "platform_print_template",
				"template_code = 'PROCUREMENT_ORDER_V1'");

		migrate(null);

		assertThat(count(jdbcTemplate, "sys_permission", "code like 'procurement:%'")).isEqualTo(permissionSeedCount);
		assertThat(count(jdbcTemplate, "platform_approval_definition", "scene_code like 'PROCUREMENT_%'"))
			.isEqualTo(approvalSeedCount);
		assertThat(count(jdbcTemplate, "platform_print_template", "template_code = 'PROCUREMENT_ORDER_V1'"))
			.isEqualTo(printSeedCount);
		assertThat(count(jdbcTemplate, "proc_purchase_order", "id = ?", data.orderId())).isOne();
		assertThat(count(jdbcTemplate, "proc_purchase_receipt_line", "id = ?", data.receiptLineId())).isOne();
	}

	private RepresentativePurchaseData insertRepresentativeV25Purchase(JdbcTemplate jdbcTemplate) {
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S24_MIG_UNIT', '024 迁移单位', 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values ('S24_MIG_WH', '024 迁移仓库', 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long supplierId = id(jdbcTemplate, """
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values ('S24_MIG_SUP', '024 迁移供应商', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S24_MIG_CAT', '024 迁移分类', 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long materialId = id(jdbcTemplate, """
				insert into mst_material (
					code, name, material_type, source_type, category_id, unit_id, status,
					cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at
				)
				values ('S24_MIG_MAT', '024 迁移物料', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED',
					'DIRECT_MATERIAL', 'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", categoryId, unitId);
		long orderId = id(jdbcTemplate, """
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values ('S24_MIG_PO_001', ?, date '2026-01-10', date '2026-01-20', 'CONFIRMED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", supplierId);
		long orderLineId = id(jdbcTemplate, """
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity,
					unit_price, expected_arrival_date, created_at, updated_at
				)
				values (?, 10, ?, ?, 5.000000, 5.000000, 10.000000, date '2026-01-20', now(), now())
				returning id
				""", orderId, materialId, unitId);
		long receiptId = id(jdbcTemplate, """
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values ('S24_MIG_PR_001', ?, ?, ?, date '2026-01-21', 'POSTED',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", orderId, supplierId, warehouseId);
		long receiptLineId = id(jdbcTemplate, """
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity,
					before_quantity, after_quantity, created_at, updated_at
				)
				values (?, 10, ?, ?, ?, 5.000000, 0.000000, 5.000000, 5.000000,
					0.000000, 5.000000, now(), now())
				returning id
				""", receiptId, orderLineId, materialId, unitId);
		return new RepresentativePurchaseData(unitId, materialId, orderId, orderLineId, receiptId, receiptLineId);
	}

	private void assertTablesExist(JdbcTemplate jdbcTemplate, List<String> tableNames) {
		for (String tableName : tableNames) {
			assertThat(tableExists(jdbcTemplate, tableName)).as(tableName).isTrue();
		}
	}

	private void assertEffectiveSupplyModel(JdbcTemplate jdbcTemplate) {
		assertThat(tableExists(jdbcTemplate, "proc_effective_purchase_supply")
				|| viewExists(jdbcTemplate, "proc_effective_purchase_supply")).isTrue();
	}

	private void assertColumnsExist(JdbcTemplate jdbcTemplate, String tableName, List<String> columnNames) {
		for (String columnName : columnNames) {
			assertThat(columnExists(jdbcTemplate, tableName, columnName)).as(tableName + "." + columnName).isTrue();
		}
	}

	private void assertPermissionsExist(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		for (String permissionCode : permissionCodes) {
			assertThat(count(jdbcTemplate, "sys_permission", "code = ?", permissionCode)).as(permissionCode).isOne();
		}
	}

	private void assertApprovalScenesExist(JdbcTemplate jdbcTemplate, List<String> sceneCodes) {
		for (String sceneCode : sceneCodes) {
			assertThat(count(jdbcTemplate, "platform_approval_definition",
					"scene_code = ? and status = 'ENABLED'", sceneCode)).as(sceneCode).isOne();
		}
	}

	private void assertApprovalStep(JdbcTemplate jdbcTemplate, String sceneCode, String permissionCode) {
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_definition_step s
				join platform_approval_definition d on d.id = s.definition_id
				where d.scene_code = ?
				  and s.step_no = 1
				  and s.candidate_permission_code = ?
				""", Long.class, sceneCode, permissionCode)).isOne();
	}

	private void assertPrintTemplate(JdbcTemplate jdbcTemplate, String templateCode, String objectType) {
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from platform_print_template
				where template_code = ?
				  and object_type = ?
				  and status = 'ENABLED'
				""", Long.class, templateCode, objectType)).isOne();
	}

	private void migrate(String target) {
		var configuration = Flyway.configure()
			.dataSource(dataSource())
			.locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		configuration.load().migrate();
	}

	private DataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		dataSource.setDriverClassName("org.postgresql.Driver");
		return dataSource;
	}

	private long id(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}

	private long count(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
	}

	private long count(JdbcTemplate jdbcTemplate, String tableName, String whereSql, Object... args) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereSql,
				Long.class, args);
	}

	private String text(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}

	private String currentFlywayVersion(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("""
				select version
				from flyway_schema_history
				where success = true
				order by installed_rank desc
				limit 1
				""", String.class);
	}

	private void assertCurrentMigrationChecksums(JdbcTemplate jdbcTemplate) {
		assertThat(migrationChecksum(jdbcTemplate, "29")).isEqualTo(774334682);
		assertThat(migrationChecksum(jdbcTemplate, "30")).isEqualTo(2130342893);
		assertThat(migrationChecksum(jdbcTemplate, "31")).isEqualTo(-2074547591);
		assertThat(migrationChecksum(jdbcTemplate, "32")).isEqualTo(249406902);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
	}

	private Integer migrationChecksum(JdbcTemplate jdbcTemplate, String version) {
		return jdbcTemplate.queryForObject("""
				select checksum
				from flyway_schema_history
				where success = true
				  and version = ?
				""", Integer.class, version);
	}

	private long failedMigrationCount(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from flyway_schema_history
				where success = false
				""", Long.class);
	}

	private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from information_schema.tables
				where table_schema = 'public'
				  and table_name = ?
				""", Boolean.class, tableName);
	}

	private boolean viewExists(JdbcTemplate jdbcTemplate, String viewName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from information_schema.views
				where table_schema = 'public'
				  and table_name = ?
				""", Boolean.class, viewName);
	}

	private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from information_schema.columns
				where table_schema = 'public'
				  and table_name = ?
				  and column_name = ?
				""", Boolean.class, tableName, columnName);
	}

	private boolean constraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = ?
				  and c.conname = ?
				""", Boolean.class, tableName, constraintName);
	}

	private boolean indexExists(JdbcTemplate jdbcTemplate, String indexName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from pg_indexes
				where schemaname = 'public'
				  and indexname = ?
				""", Boolean.class, indexName);
	}

	private record RepresentativePurchaseData(
			long unitId,
			long materialId,
			long orderId,
			long orderLineId,
			long receiptId,
			long receiptLineId) {
	}

}
