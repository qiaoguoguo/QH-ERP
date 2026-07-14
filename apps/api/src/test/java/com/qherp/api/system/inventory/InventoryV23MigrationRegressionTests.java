package com.qherp.api.system.inventory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class InventoryV23MigrationRegressionTests {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeEach
	void 清理迁移数据库() {
		clean();
	}

	@Test
	void v1v19v20v21v22升级到v23必须补齐预留成本层结构() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("23");
		assertReservationCostLayerSchema(jdbcTemplate);

		for (String target : List.of("19", "20", "21", "22")) {
			clean();
			migrate(target);
			jdbcTemplate = new JdbcTemplate(dataSource());
			assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(target);

			migrate(null);

			assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("23");
			assertReservationCostLayerSchema(jdbcTemplate);
		}
	}

	@Test
	void v22公共预留升级到v23必须保持空成本层() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		ReservationSeed seed = insertReservationSeed(jdbcTemplate, "PUB");
		insertPublicBalance(jdbcTemplate, seed, "4.000000", "32.00", "8.000000", "1.000000");
		long projectLayerId = insertProjectLayerAndBalance(jdbcTemplate, seed, "5.000000", "50.00", "10.000000");
		setProjectBalanceLocked(jdbcTemplate, seed, projectLayerId, "1.000000");
		long reservationId = insertReservation(jdbcTemplate, seed, "PUBLIC", null, null, "ACTIVE");

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("23");
		assertThat(queryLong(jdbcTemplate, "select cost_layer_id from inv_stock_reservation where id = ?",
				reservationId)).isNull();
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", reservationId)).isEqualTo("PUBLIC:NULL");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where warehouse_id = ?
				  and material_id = ?
				  and ownership_type = 'PUBLIC'
				  and project_id is null
				  and cost_layer_id is null
				""", seed.warehouseId(), seed.materialId())).isEqualByComparingTo("1.000000");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where warehouse_id = ?
				  and material_id = ?
				  and ownership_type = 'PROJECT'
				  and project_id = ?
				  and cost_layer_id = ?
				""", seed.warehouseId(), seed.materialId(), seed.projectId(), projectLayerId))
			.isEqualByComparingTo("0.000000");
	}

	@Test
	void v22批次追踪空追踪活动预留升级到v23必须解释为父级聚合且不写批次行锁() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		ReservationSeed seed = insertReservationSeed(jdbcTemplate, "BAGG", "BATCH");
		long batchA = insertBatchBalance(jdbcTemplate, seed, "V23-BAGG-A", "3.000000", "1.000000");
		long batchB = insertBatchBalance(jdbcTemplate, seed, "V23-BAGG-B", "4.000000", "2.000000");
		long reservationId = insertReservation(jdbcTemplate, seed, "PUBLIC", null, null, "ACTIVE");

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("23");
		assertThat(queryText(jdbcTemplate, """
				select coalesce(parent_reservation_id::text, 'NULL') || ':' || coalesce(batch_id::text, 'NULL') || ':'
					|| coalesce(serial_id::text, 'NULL') || ':' || coalesce(cost_layer_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", reservationId)).isEqualTo("NULL:NULL:NULL:NULL");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where batch_id = ?
				""", batchA)).isEqualByComparingTo("0.000000");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where batch_id = ?
				""", batchB)).isEqualByComparingTo("0.000000");
		assertThat(queryDecimal(jdbcTemplate, """
				select quantity - released_quantity - consumed_quantity
				from inv_stock_reservation
				where id = ?
				""", reservationId)).isEqualByComparingTo("1.000000");
	}

	@Test
	void v22唯一项目预留升级到v23必须回填唯一正余额成本层() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		ReservationSeed seed = insertReservationSeed(jdbcTemplate, "ONE");
		long layerId = insertProjectLayerAndBalance(jdbcTemplate, seed, "5.000000", "50.00", "10.000000");
		long reservationId = insertReservation(jdbcTemplate, seed, "PROJECT", seed.projectId(), null, "ACTIVE");

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("23");
		assertThat(queryLong(jdbcTemplate, "select cost_layer_id from inv_stock_reservation where id = ?",
				reservationId)).isEqualTo(layerId);
	}

	@Test
	void v22项目预留存在多个候选成本层升级到v23必须失败() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		ReservationSeed seed = insertReservationSeed(jdbcTemplate, "AMB");
		insertProjectLayerAndBalance(jdbcTemplate, seed, "5.000000", "50.00", "10.000000");
		insertProjectLayerAndBalance(jdbcTemplate, seed, "7.000000", "84.00", "12.000000");
		insertReservation(jdbcTemplate, seed, "PROJECT", seed.projectId(), null, "ACTIVE");

		assertThatThrownBy(() -> migrate(null))
			.isInstanceOf(FlywayException.class)
			.hasMessageContaining("active project reservation cost layer is ambiguous");
	}

	private void assertReservationCostLayerSchema(JdbcTemplate jdbcTemplate) {
		assertThat(columnExists(jdbcTemplate, "inv_stock_reservation", "cost_layer_id")).isTrue();
		assertThat(constraintExists(jdbcTemplate, "inv_stock_reservation", "fk_inv_stock_reservation_cost_layer"))
			.isTrue();
		assertThat(indexExists(jdbcTemplate, "idx_inv_stock_reservation_cost_layer")).isTrue();
		assertThat(indexExists(jdbcTemplate, "idx_inv_stock_reservation_identity")).isTrue();
	}

	private ReservationSeed insertReservationSeed(JdbcTemplate jdbcTemplate, String prefix) {
		return insertReservationSeed(jdbcTemplate, prefix, "NONE");
	}

	private ReservationSeed insertReservationSeed(JdbcTemplate jdbcTemplate, String prefix, String trackingMethod) {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "V23_UNIT_" + prefix + suffix, "V23迁移单位" + prefix + suffix);
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "V23_WH_" + prefix + suffix, "V23迁移仓库" + prefix + suffix);
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "V23_CAT_" + prefix + suffix, "V23迁移分类" + prefix + suffix);
		long materialId = id(jdbcTemplate, """
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					tracking_method, cost_category, inventory_valuation_category, inventory_value_enabled,
					project_cost_enabled, cost_remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'V23规格', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED',
					?, 'DIRECT_MATERIAL', 'VALUATED_MATERIAL', true, true, 'V23迁移物料',
					'test', now(), 'test', now())
				returning id
				""", "V23_MAT_" + prefix + suffix, "V23迁移物料" + prefix + suffix, categoryId, unitId,
				trackingMethod);
		long customerId = id(jdbcTemplate, """
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "V23_CUS_" + prefix + suffix, "V23迁移客户" + prefix + suffix);
		long ownerId = id(jdbcTemplate, """
				insert into sys_user (
					username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'V23迁移测试密码', ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "v23_owner_" + prefix.toLowerCase() + suffix, "V23迁移负责人" + prefix + suffix);
		long projectId = id(jdbcTemplate, """
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 'ACTIVE', 10000.00, 5000.00, 'test', now(), 'test', now())
				returning id
				""", "V23_PRJ_" + prefix + suffix, "V23迁移项目" + prefix + suffix, customerId, ownerId,
				LocalDate.now(), LocalDate.now().plusDays(30));
		return new ReservationSeed(warehouseId, materialId, unitId, projectId);
	}

	private long insertBatchBalance(JdbcTemplate jdbcTemplate, ReservationSeed seed, String batchNo, String quantity,
			String lockedQuantity) {
		long batchId = id(jdbcTemplate, """
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, created_by,
					created_at, updated_by, updated_at
				)
				values (?, ?, 'V23_MIGRATION_BATCH', ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", seed.materialId(), batchNo, 7_000_000L + SEQUENCE.incrementAndGet(),
				7_100_000L + SEQUENCE.incrementAndGet(), LocalDate.now());
		jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, valuation_state, inventory_amount, average_unit_cost, batch_id, created_at,
					updated_at
				)
				values (?, ?, ?, ?, ?, 'QUALIFIED', 'PUBLIC', 'VALUED', 10.00, 10.000000, ?, now(), now())
				""", seed.warehouseId(), seed.materialId(), seed.unitId(), new BigDecimal(quantity),
				new BigDecimal(lockedQuantity), batchId);
		return batchId;
	}

	private long insertProjectLayerAndBalance(JdbcTemplate jdbcTemplate, ReservationSeed seed, String quantity,
			String amount, String unitCost) {
		int suffix = SEQUENCE.incrementAndGet();
		long layerId = id(jdbcTemplate, """
				insert into inv_project_cost_layer (
					project_id, material_id, source_type, source_id, source_line_id, original_quantity,
					original_amount, remaining_quantity, remaining_amount, unit_cost, status
				)
				values (?, ?, 'V23_MIGRATION_TEST', ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
				returning id
				""", seed.projectId(), seed.materialId(), 5_000_000L + suffix, 5_100_000L + suffix,
				new BigDecimal(quantity), new BigDecimal(amount), new BigDecimal(quantity), new BigDecimal(amount),
				new BigDecimal(unitCost));
		jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, project_id, valuation_state, inventory_amount, average_unit_cost,
					cost_layer_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, 'QUALIFIED', 'PROJECT', ?, 'VALUED', ?, ?, ?, now(), now())
				""", seed.warehouseId(), seed.materialId(), seed.unitId(), new BigDecimal(quantity), seed.projectId(),
				new BigDecimal(amount), new BigDecimal(unitCost), layerId);
		return layerId;
	}

	private void insertPublicBalance(JdbcTemplate jdbcTemplate, ReservationSeed seed, String quantity, String amount,
			String unitCost, String lockedQuantity) {
		jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, valuation_state, inventory_amount, average_unit_cost, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 'QUALIFIED', 'PUBLIC', 'VALUED', ?, ?, now(), now())
				""", seed.warehouseId(), seed.materialId(), seed.unitId(), new BigDecimal(quantity),
				new BigDecimal(lockedQuantity), new BigDecimal(amount), new BigDecimal(unitCost));
	}

	private void setProjectBalanceLocked(JdbcTemplate jdbcTemplate, ReservationSeed seed, long costLayerId,
			String lockedQuantity) {
		jdbcTemplate.update("""
				update inv_stock_balance
				set locked_quantity = ?
				where warehouse_id = ?
				  and material_id = ?
				  and ownership_type = 'PROJECT'
				  and project_id = ?
				  and cost_layer_id = ?
				""", new BigDecimal(lockedQuantity), seed.warehouseId(), seed.materialId(), seed.projectId(),
				costLayerId);
	}

	private long insertReservation(JdbcTemplate jdbcTemplate, ReservationSeed seed, String ownershipType, Long projectId,
			Long costLayerId, String status) {
		int suffix = SEQUENCE.incrementAndGet();
		String columns = columnExists(jdbcTemplate, "inv_stock_reservation", "cost_layer_id") ? ", cost_layer_id" : "";
		String valuePlaceholder = columns.isEmpty() ? "" : ", ?";
		List<Object> args = new java.util.ArrayList<>(List.of("V23-RSV-" + suffix, seed.warehouseId(),
				seed.materialId(), seed.unitId(), 6_000_000L + suffix, 6_100_000L + suffix,
				"V23-SRC-" + suffix, LocalDate.now(), ownershipType));
		args.add(projectId);
		if (!columns.isEmpty()) {
			args.add(costLayerId);
		}
		return id(jdbcTemplate, """
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id, quality_status,
					quantity, released_quantity, consumed_quantity, source_type, source_id, source_line_id,
					source_document_no, business_date, reason, created_by, created_at, updated_by, updated_at,
					ownership_type, project_id%s
				)
				values (?, 'RESERVATION', '%s', ?, ?, ?, 'QUALIFIED', 1.000000, 0, 0,
					'V23_MIGRATION_TEST', ?, ?, ?, ?, 'V23迁移预留', 'test', now(), 'test', now(), ?, ?%s)
				returning id
				""".formatted(columns, status, valuePlaceholder), args.toArray());
	}

	private void clean() {
		Flyway.configure()
			.dataSource(dataSource())
			.locations("classpath:db/migration")
			.cleanDisabled(false)
			.load()
			.clean();
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
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		dataSource.setDriverClassName("org.postgresql.Driver");
		return dataSource;
	}

	private long id(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
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

	private Long queryLong(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}

	private BigDecimal queryDecimal(JdbcTemplate jdbcTemplate, String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
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

	private record ReservationSeed(long warehouseId, long materialId, long unitId, long projectId) {
	}

}
