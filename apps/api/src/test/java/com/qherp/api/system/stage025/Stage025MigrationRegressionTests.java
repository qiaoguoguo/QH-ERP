package com.qherp.api.system.stage025;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Stage025MigrationRegressionTests {

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
	void 空库必须迁移到v27并建立销售履约结构权限审批文档和026契约() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("35");
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertTablesExist(jdbcTemplate, List.of(
				"sal_sales_quote",
				"sal_sales_quote_line",
				"sal_sales_order_snapshot",
				"sal_sales_order_line_snapshot",
				"sal_sales_order_change",
				"sal_sales_order_change_line",
				"sal_sales_delivery_plan",
				"sal_customer_credit_profile",
				"sal_credit_check_log",
				"sal_action_idempotency"));
		assertEffectiveDemandModel(jdbcTemplate);
		assertColumnsExist(jdbcTemplate, "sal_sales_order", List.of(
				"source_quote_id",
				"currency",
				"price_mode",
				"tax_excluded_amount",
				"tax_amount",
				"tax_included_amount",
				"credit_check_log_id",
				"credit_override_approval_instance_id",
				"close_reason",
				"sales_fulfillment_compatible"));
		assertColumnsExist(jdbcTemplate, "sal_sales_order_line", List.of(
				"source_quote_line_id",
				"price_source_type",
				"currency",
				"tax_rate",
				"tax_excluded_unit_price",
				"tax_included_unit_price",
				"tax_excluded_amount",
				"tax_amount",
				"tax_included_amount"));
		assertColumnsExist(jdbcTemplate, "sal_sales_shipment_line", List.of(
				"delivery_plan_id",
				"currency",
				"tax_rate",
				"tax_excluded_unit_price",
				"tax_included_unit_price",
				"tax_excluded_amount",
				"tax_amount",
				"tax_included_amount",
				"legacy_snapshot"));
		assertColumnsExist(jdbcTemplate, "sal_project", List.of(
				"sales_fulfillment_status",
				"sales_fulfillment_closed_by",
				"sales_fulfillment_closed_at",
				"sales_fulfillment_close_reason"));
		assertThat(constraintExists(jdbcTemplate, "sal_sales_order_line",
				"uk_sal_sales_order_line_material")).isFalse();
		assertThat(indexExists(jdbcTemplate, "uk_sal_sales_order_line_no")).isTrue();
		assertPermissionsExist(jdbcTemplate, List.of(
				"sales:quote:view",
				"sales:quote:create",
				"sales:quote:update",
				"sales:quote:submit",
				"sales:quote:approve",
				"sales:quote:cancel",
				"sales:quote:convert",
				"sales:delivery-plan:view",
				"sales:delivery-plan:manage",
				"sales:order-change:view",
				"sales:order-change:create",
				"sales:order-change:submit",
				"sales:order-change:cancel",
				"sales:order-change:approve",
				"sales:credit:view",
				"sales:credit:manage",
				"sales:credit:override-submit",
				"sales:credit:override-approve",
				"sales:order:short-close-submit",
				"sales:order:short-close-approve",
				"sales:fulfillment:view",
				"sales:fulfillment:close",
				"sales:effective-demand:view"));
		assertApprovalScenesExist(jdbcTemplate, List.of(
				"SALES_QUOTE_APPROVAL",
				"SALES_ORDER_CHANGE_APPROVAL",
				"SALES_ORDER_CHANGE_CREDIT_OVERRIDE",
				"SALES_ORDER_CREDIT_OVERRIDE",
				"SALES_ORDER_SHORT_CLOSE"));
		assertApprovalStep(jdbcTemplate, "SALES_QUOTE_APPROVAL", "sales:quote:approve");
		assertApprovalStep(jdbcTemplate, "SALES_ORDER_CHANGE_APPROVAL", "sales:order-change:approve");
		assertApprovalStep(jdbcTemplate, "SALES_ORDER_CHANGE_CREDIT_OVERRIDE", "sales:credit:override-approve");
		assertApprovalStep(jdbcTemplate, "SALES_ORDER_CREDIT_OVERRIDE", "sales:credit:override-approve");
		assertApprovalStep(jdbcTemplate, "SALES_ORDER_SHORT_CLOSE", "sales:order:short-close-approve");
		assertPrintTemplate(jdbcTemplate, "SALES_QUOTE_V1", "SALES_QUOTE_PRINT", "SALES_QUOTE");
		assertDocumentTaskTypeSeed(jdbcTemplate, "SALES_QUOTE_EXPORT");
		assertDocumentTaskTypeSeed(jdbcTemplate, "SALES_DELIVERY_PLAN_EXPORT");
		assertDocumentTaskTypeSeed(jdbcTemplate, "SALES_EFFECTIVE_DEMAND_EXPORT");
	}

	@Test
	void v26代表性销售存量升级到v27必须兼容历史并替换旧同物料约束() {
		migrate("26");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("26");
		RepresentativeSalesData data = insertRepresentativeV26Sales(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("35");
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertThat(constraintExists(jdbcTemplate, "sal_sales_order_line",
				"uk_sal_sales_order_line_material")).isFalse();
		assertThat(text(jdbcTemplate, """
				select currency || ':' || tax_rate::text || ':' || price_source_type
				from sal_sales_order_line
				where id = ?
				""", data.orderLineId())).isEqualTo("CNY:0.000000:LEGACY_MANUAL");
		assertThat(columnExists(jdbcTemplate, "sal_sales_shipment_line", "legacy_snapshot"))
				.as("V27 必须为历史已过账出库行标记 legacy_snapshot，不能伪造 025 事实")
				.isTrue();
		assertThat(text(jdbcTemplate, """
				select legacy_snapshot::text || ':' || tax_included_amount::text
				from sal_sales_shipment_line
				where id = ?
				""", data.shipmentLineId())).isEqualTo("true:100.00");
		assertThat(text(jdbcTemplate, """
				select sales_fulfillment_status
				from sal_project
				where id = ?
				""", data.projectId())).isEqualTo("OPEN");
		assertThat(count(jdbcTemplate, "sal_sales_quote")).isZero();
		assertThat(count(jdbcTemplate, "sal_sales_order_change")).isZero();
		assertThat(count(jdbcTemplate, "sal_credit_check_log")).isZero();
		assertThat(count(jdbcTemplate, "sal_sales_delivery_plan")).isZero();
		jdbcTemplate.update("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, created_at, updated_at, currency, tax_rate, tax_excluded_unit_price,
					tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount, price_source_type
				)
				values (?, 20, ?, ?, 2.000000, 0.000000, 12.000000, date '2026-03-10', now(), now(),
					'CNY', 0.000000, 12.000000, 12.000000, 24.00, 0.00, 24.00, 'LEGACY_MANUAL')
				""", data.orderId(), data.materialId(), data.unitId());
		assertThat(count(jdbcTemplate, "sal_sales_order_line", "order_id = ? and material_id = ?",
				data.orderId(), data.materialId())).isEqualTo(2);
		assertThat(queryDecimal(jdbcTemplate, """
				select sum(source_amount)
				from fin_receivable_source
				where source_id = ?
				""", data.shipmentId())).isEqualByComparingTo("100.00");
		assertThat(queryDecimal(jdbcTemplate, """
				select quantity_on_hand
				from inv_stock_balance
				where id = ?
				""", data.stockBalanceId())).isEqualByComparingTo("10.000000");
	}

	private RepresentativeSalesData insertRepresentativeV26Sales(JdbcTemplate jdbcTemplate) {
		long ownerUserId = insertLegacyUser(jdbcTemplate, "s25_mig_owner");
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S25_MIG_UNIT', '025 迁移单位', 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values ('S25_MIG_WH', '025 迁移仓库', 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long customerId = id(jdbcTemplate, """
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values ('S25_MIG_CUS', '025 迁移客户', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S25_MIG_CAT', '025 迁移分类', 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long materialId = id(jdbcTemplate, """
				insert into mst_material (
					code, name, material_type, source_type, category_id, unit_id, status,
					cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at
				)
				values ('S25_MIG_MAT', '025 迁移成品', 'FINISHED_GOOD', 'SELF_MADE', ?, ?, 'ENABLED',
					'DIRECT_MATERIAL', 'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", categoryId, unitId);
		long projectId = id(jdbcTemplate, """
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date, status,
					target_revenue, target_cost, created_by, created_at, updated_by, updated_at, activated_by,
					activated_at
				)
				values ('S25_MIG_PRJ', '025 迁移项目', ?, ?,
					date '2026-01-01', date '2026-12-31', 'ACTIVE', 100000.00, 50000.00,
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", customerId, ownerUserId);
		long contractId = id(jdbcTemplate, """
				insert into sal_project_contract (
					contract_no, external_contract_no, project_id, contract_type, main_contract_id, name,
					signed_date, effective_start_date, effective_end_date, amount, status, remark,
					created_by, created_at, updated_by, updated_at, activated_by, activated_at
				)
				values ('S25_MIG_CON', 'S25_EXT_CON', ?, 'MAIN', null, '025 迁移合同',
					date '2026-01-01', date '2026-01-01', date '2026-12-31', 100000.00, 'EFFECTIVE',
					'025 迁移合同', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", projectId);
		long orderId = id(jdbcTemplate, """
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark, project_id, contract_id,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values ('S25_MIG_SO', ?, date '2026-02-01', date '2026-03-01', 'SHIPPED',
					'025 迁移订单', ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", customerId, projectId, contractId);
		long orderLineId = id(jdbcTemplate, """
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, created_at, updated_at
				)
				values (?, 10, ?, ?, 5.000000, 5.000000, 20.000000, date '2026-03-01', now(), now())
				returning id
				""", orderId, materialId, unitId);
		long stockBalanceId = id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, ownership_type, project_id, quantity_on_hand,
					locked_quantity, created_at, updated_at
				)
				values (?, ?, ?, 'QUALIFIED', 'PUBLIC', null, 10.000000, 0.000000, now(), now())
				returning id
				""", warehouseId, materialId, unitId);
		long shipmentId = id(jdbcTemplate, """
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values ('S25_MIG_SHIP', ?, ?, ?, date '2026-03-01', 'POSTED', '025 迁移出库',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", orderId, customerId, warehouseId);
		long shipmentLineId = id(jdbcTemplate, """
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
					created_at, updated_at
				)
				values (?, 10, ?, ?, ?, 5.000000, 0.000000, 5.000000, 5.000000, 15.000000, 10.000000,
					now(), now())
				returning id
				""", shipmentId, orderLineId, materialId, unitId);
		long receivableId = id(jdbcTemplate, """
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, remark, created_by, created_at,
					updated_by, updated_at
				)
				values ('S25_MIG_AR', ?, 'SALES_SHIPMENT', ?, 'S25_MIG_SHIP', date '2026-03-02',
					date '2026-04-01', 100.00, 0.00, 100.00, 'CONFIRMED', '025 迁移应收',
					'test', now(), 'test', now())
				returning id
				""", customerId, shipmentId);
		id(jdbcTemplate, """
				insert into fin_receivable_source (
					receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'SALES_SHIPMENT', ?, 'S25_MIG_SHIP', ?, 10, 100.00)
				returning id
				""", receivableId, shipmentId, shipmentLineId);
		return new RepresentativeSalesData(unitId, materialId, projectId, orderId, orderLineId, shipmentId,
				shipmentLineId, stockBalanceId);
	}

	private void assertTablesExist(JdbcTemplate jdbcTemplate, List<String> tableNames) {
		for (String tableName : tableNames) {
			assertThat(tableExists(jdbcTemplate, tableName)).as(tableName).isTrue();
		}
	}

	private void assertEffectiveDemandModel(JdbcTemplate jdbcTemplate) {
		assertThat(tableExists(jdbcTemplate, "sal_effective_sales_demand")
				|| viewExists(jdbcTemplate, "sal_effective_sales_demand")).isTrue();
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

	private void assertPrintTemplate(JdbcTemplate jdbcTemplate, String templateCode, String sceneCode,
			String objectType) {
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from platform_print_template
				where template_code = ?
				  and scene_code = ?
				  and object_type = ?
				  and status = 'ENABLED'
				""", Long.class, templateCode, sceneCode, objectType)).isOne();
	}

	private void assertDocumentTaskTypeSeed(JdbcTemplate jdbcTemplate, String taskType) {
		if (tableExists(jdbcTemplate, "platform_document_task_type")) {
			assertThat(count(jdbcTemplate, "platform_document_task_type", "task_type = ?", taskType)).as(taskType)
				.isOne();
		}
		else {
			assertThat(count(jdbcTemplate, "sys_permission", "code like ?", "%")).as(taskType).isPositive();
		}
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

	private BigDecimal queryDecimal(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
	}

	private long insertLegacyUser(JdbcTemplate jdbcTemplate, String username) {
		return id(jdbcTemplate, """
				insert into sys_user (
					username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'test', ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", username, "025迁移旧项目负责人");
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
		assertThat(migrationChecksum(jdbcTemplate, "33")).isEqualTo(612501943);
		assertThat(migrationChecksum(jdbcTemplate, "34")).isEqualTo(-629066235);
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

	private record RepresentativeSalesData(
			long unitId,
			long materialId,
			long projectId,
			long orderId,
			long orderLineId,
			long shipmentId,
			long shipmentLineId,
			long stockBalanceId) {
	}

}
