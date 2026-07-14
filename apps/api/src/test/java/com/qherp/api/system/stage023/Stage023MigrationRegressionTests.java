package com.qherp.api.system.stage023;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Stage023MigrationRegressionTests {

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
	void 空库必须从v1迁移到v20并建立计价结构权限和审批种子() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("20");
		assertTablesExist(jdbcTemplate, List.of(
				"inv_public_valuation_pool",
				"inv_value_movement",
				"inv_project_cost_layer",
				"inv_cost_layer_allocation",
				"inv_warehouse_transfer",
				"inv_warehouse_transfer_line",
				"inv_ownership_conversion",
				"inv_ownership_conversion_line",
				"inv_stocktake",
				"inv_stocktake_line",
				"inv_stocktake_range_lock",
				"inv_valuation_adjustment",
				"inv_valuation_adjustment_line"));
		assertColumnsExist(jdbcTemplate, "inv_stock_balance", List.of(
				"ownership_type",
				"project_id",
				"valuation_state",
				"inventory_amount",
				"average_unit_cost",
				"cost_layer_id",
				"public_pool_id"));
		assertColumnsExist(jdbcTemplate, "inv_stock_movement", List.of(
				"ownership_type",
				"project_id",
				"valuation_method",
				"unit_cost",
				"inventory_amount",
				"value_movement_id",
				"cost_layer_id"));
		assertThat(constraintExists(jdbcTemplate, "inv_stock_balance", "ck_inv_stock_balance_ownership"))
			.isTrue();
		assertThat(indexExists(jdbcTemplate, "idx_inv_value_movement_source")).isTrue();
		assertThat(indexExists(jdbcTemplate, "idx_inv_project_cost_layer_project_material")).isTrue();

		assertPermissionsExist(jdbcTemplate, List.of(
				"inventory:valuation:view",
				"inventory:cost-layer:view",
				"inventory:reconciliation:view",
				"inventory:warehouse-transfer:view",
				"inventory:warehouse-transfer:create",
				"inventory:warehouse-transfer:update",
				"inventory:warehouse-transfer:post",
				"inventory:warehouse-transfer:cancel",
				"inventory:ownership-conversion:view",
				"inventory:ownership-conversion:create",
				"inventory:ownership-conversion:update",
				"inventory:ownership-conversion:submit",
				"inventory:ownership-conversion:cancel",
				"inventory:stocktake:view",
				"inventory:stocktake:create",
				"inventory:stocktake:update",
				"inventory:stocktake:submit",
				"inventory:stocktake:cancel",
				"inventory:valuation-adjustment:view",
				"inventory:valuation-adjustment:create",
				"inventory:valuation-adjustment:update",
				"inventory:valuation-adjustment:submit",
				"inventory:valuation-adjustment:cancel"));
		assertApprovalScenesExist(jdbcTemplate, List.of(
				"INVENTORY_OWNERSHIP_CONVERSION_POST",
				"INVENTORY_STOCKTAKE_VARIANCE_POST",
				"INVENTORY_VALUATION_ADJUSTMENT_POST"));
	}

	@Test
	void v19代表性存量升级到v20必须保留数量质量追踪预留并标记历史未估值() {
		migrate("19");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("19");
		RepresentativeStockData data = insertRepresentativeV19Stock(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("20");
		assertThat(count(jdbcTemplate, "platform_approval_instance")).isZero();
		assertThat(count(jdbcTemplate, "platform_approval_task")).isZero();
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':' || valuation_state
				from inv_stock_balance
				where id = ?
				""", data.valuedBalanceId())).isEqualTo("PUBLIC:NULL:LEGACY_UNVALUED");
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':' || valuation_state
				from inv_stock_balance
				where id = ?
				""", data.nonValuedBalanceId())).isEqualTo("PUBLIC:NULL:NON_VALUED");
		assertThat(queryDecimal(jdbcTemplate, """
				select quantity_on_hand
				from inv_stock_balance
				where id = ?
				""", data.valuedBalanceId())).isEqualByComparingTo("12.000000");
		assertThat(queryText(jdbcTemplate, """
				select quality_status || ':' || coalesce(batch_id::text, 'NULL')
				from inv_stock_balance
				where id = ?
				""", data.valuedBalanceId())).isEqualTo("FROZEN:" + data.batchId());
		assertThat(queryDecimal(jdbcTemplate, """
				select quantity - released_quantity - consumed_quantity
				from inv_stock_reservation
				where id = ?
				""", data.reservationId())).isEqualByComparingTo("3.000000");
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':' || valuation_state
				from inv_stock_movement
				where id = ?
				""", data.movementId())).isEqualTo("PUBLIC:NULL:LEGACY_UNVALUED");
		assertThat(queryDecimal(jdbcTemplate, """
				select inventory_amount
				from inv_stock_balance
				where id = ?
				""", data.valuedBalanceId())).isNull();
		assertThat(count(jdbcTemplate, "inv_project_cost_layer")).isZero();
		assertThat(count(jdbcTemplate, "inv_value_movement")).isZero();
	}

	private RepresentativeStockData insertRepresentativeV19Stock(JdbcTemplate jdbcTemplate) {
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S23_MIG_UNIT', '023迁移单位', 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values ('S23_MIG_WH', '023迁移仓库', 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S23_MIG_CAT', '023迁移分类', 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long valuedMaterialId = insertMaterial(jdbcTemplate, "S23_MIG_VAL", "VALUATED_MATERIAL", true, categoryId,
				unitId, "BATCH");
		long nonValuedMaterialId = insertMaterial(jdbcTemplate, "S23_MIG_NVAL", "NON_VALUATED_CONSUMABLE", false,
				categoryId, unitId, "NONE");
		long batchId = id(jdbcTemplate, """
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, 'S23-MIG-BATCH-001', 'MIGRATION_TEST', 1, 1, date '2026-01-10', '023迁移批次',
					'test', now(), 'test', now())
				returning id
				""", valuedMaterialId);
		long valuedBalanceId = id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, batch_id, created_at, updated_at
				)
				values (?, ?, ?, 12.000000, 2.000000, 'FROZEN', ?, now(), now())
				returning id
				""", warehouseId, valuedMaterialId, unitId, batchId);
		long nonValuedBalanceId = id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, created_at, updated_at
				)
				values (?, ?, ?, 7.000000, 0.000000, 'QUALIFIED', now(), now())
				returning id
				""", warehouseId, nonValuedMaterialId, unitId);
		long movementId = id(jdbcTemplate, """
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, operator_name, occurred_at, quality_status, batch_id
				)
				values ('S23-MIG-MOV-001', 'OPENING', 'IN', ?, ?, ?, 12.000000, 0.000000, 12.000000,
					'MIGRATION_TEST', 1, 1, date '2026-01-10', '023迁移历史流水', 'test', now(), 'FROZEN', ?)
				returning id
				""", warehouseId, valuedMaterialId, unitId, batchId);
		long reservationId = id(jdbcTemplate, """
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id,
					quality_status, quantity, released_quantity, consumed_quantity, source_type,
					source_id, source_line_id, source_document_no, business_date, reason,
					created_by, created_at, updated_by, updated_at, batch_id
				)
				values ('S23-MIG-RES-001', 'RESERVATION', 'ACTIVE', ?, ?, ?, 'FROZEN',
					3.000000, 0.000000, 0.000000, 'SALES_ORDER', 1, 1, 'S23-SO-001',
					date '2026-01-11', '023迁移预留', 'test', now(), 'test', now(), ?)
				returning id
				""", warehouseId, valuedMaterialId, unitId, batchId);
		return new RepresentativeStockData(valuedBalanceId, nonValuedBalanceId, movementId, reservationId, batchId);
	}

	private long insertMaterial(JdbcTemplate jdbcTemplate, String code, String valuationCategory, boolean valued,
			long categoryId, long unitId, String trackingMethod) {
		return id(jdbcTemplate, """
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					tracking_method, cost_category, inventory_valuation_category, inventory_value_enabled,
					project_cost_enabled, cost_remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '023迁移规格', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED',
					?, 'DIRECT_MATERIAL', ?, ?, ?, '023迁移物料', 'test', now(), 'test', now())
				returning id
				""", code, code + "物料", categoryId, unitId, trackingMethod, valuationCategory, valued, valued);
	}

	private void assertTablesExist(JdbcTemplate jdbcTemplate, List<String> tableNames) {
		for (String tableName : tableNames) {
			assertThat(tableExists(jdbcTemplate, tableName)).as("缺少表 " + tableName).isTrue();
		}
	}

	private void assertColumnsExist(JdbcTemplate jdbcTemplate, String tableName, List<String> columnNames) {
		for (String columnName : columnNames) {
			assertThat(columnExists(jdbcTemplate, tableName, columnName))
				.as("缺少字段 " + tableName + "." + columnName)
				.isTrue();
		}
	}

	private void assertPermissionsExist(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		for (String permissionCode : permissionCodes) {
			assertThat(count(jdbcTemplate, "sys_permission", "code = ?", permissionCode))
				.as("缺少权限种子 " + permissionCode)
				.isOne();
		}
	}

	private void assertApprovalScenesExist(JdbcTemplate jdbcTemplate, List<String> sceneCodes) {
		for (String sceneCode : sceneCodes) {
			assertThat(count(jdbcTemplate, "platform_approval_definition", "scene_code = ? and status = 'ENABLED'",
					sceneCode)).as("缺少审批场景 " + sceneCode).isOne();
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

	private String currentFlywayVersion(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("""
				select version
				from flyway_schema_history
				where success = true
				order by installed_rank desc
				limit 1
				""", String.class);
	}

	private String queryText(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}

	private BigDecimal queryDecimal(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
	}

	private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from information_schema.tables
				where table_schema = 'public'
				  and table_name = ?
				""", Boolean.class, tableName);
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
				from information_schema.table_constraints
				where table_schema = 'public'
				  and table_name = ?
				  and constraint_name = ?
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

	private record RepresentativeStockData(
			long valuedBalanceId,
			long nonValuedBalanceId,
			long movementId,
			long reservationId,
			long batchId) {
	}

}
