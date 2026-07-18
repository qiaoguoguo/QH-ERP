package com.qherp.api.system.stage023;

import com.qherp.api.system.inventory.InventoryMovementType;
import org.flywaydb.core.Flyway;
import org.assertj.core.api.SoftAssertions;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void 空库必须从v1迁移到v25并建立计价结构权限审批种子预留成本层身份和价值流水长度() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
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
		assertColumnsExist(jdbcTemplate, "inv_warehouse_transfer_line", List.of(
				"source_cost_layer_id"));
		assertColumnsExist(jdbcTemplate, "inv_stock_movement", List.of(
				"ownership_type",
				"project_id",
				"valuation_method",
				"unit_cost",
				"inventory_amount",
				"value_movement_id",
				"cost_layer_id"));
		assertColumnsExist(jdbcTemplate, "inv_stock_reservation", List.of(
				"ownership_type",
				"project_id",
				"cost_layer_id"));
		assertThat(constraintExists(jdbcTemplate, "inv_stock_balance", "ck_inv_stock_balance_ownership"))
			.isTrue();
		assertThat(indexExists(jdbcTemplate, "idx_inv_value_movement_source")).isTrue();
		assertThat(indexExists(jdbcTemplate, "idx_inv_project_cost_layer_project_material")).isTrue();
		assertThat(indexExists(jdbcTemplate, "idx_inv_warehouse_transfer_line_source_layer")).isTrue();
		assertThat(constraintExists(jdbcTemplate, "inv_warehouse_transfer_line",
				"fk_inv_warehouse_transfer_line_source_layer")).isTrue();
		assertStockBalanceUniqueIndexesIncludeCostLayer(jdbcTemplate);
		assertStocktakeVarianceColumnsNullable(jdbcTemplate);
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);

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
	void v19代表性存量升级到v25必须保留数量质量追踪预留并标记历史未估值() {
		migrate("19");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("19");
		RepresentativeStockData data = insertRepresentativeV19Stock(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		assertStockBalanceUniqueIndexesIncludeCostLayer(jdbcTemplate);
		assertStocktakeVarianceColumnsNullable(jdbcTemplate);
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);
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

	@Test
	void v20代表性存量升级到v25必须保留既有计价状态并具备成本层预留身份和价值流水长度() {
		migrate("20");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("20");
		RepresentativeStockData data = insertRepresentativeV20Stock(jdbcTemplate);
		long oldDraftLineId = insertLegacyStocktakeDraft(jdbcTemplate, data.valuedBalanceId());

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		assertThat(columnExists(jdbcTemplate, "inv_warehouse_transfer_line", "source_cost_layer_id")).isTrue();
		assertStockBalanceUniqueIndexesIncludeCostLayer(jdbcTemplate);
		assertStocktakeVarianceColumnsNullable(jdbcTemplate);
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);
		assertLegacyStocktakeDraftVarianceFieldsNull(jdbcTemplate, oldDraftLineId);
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
		assertThat(queryDecimal(jdbcTemplate, """
				select quantity
				from inv_public_valuation_pool
				where material_id = (
					select material_id from inv_stock_balance where id = ?
				)
				""", data.valuedBalanceId())).isEqualByComparingTo("12.000000");
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':' || valuation_state
				from inv_stock_movement
				where id = ?
				""", data.movementId())).isEqualTo("PUBLIC:NULL:LEGACY_UNVALUED");
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':'
					|| coalesce(cost_layer_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", data.reservationId())).isEqualTo("PUBLIC:NULL:NULL");
		assertThat(count(jdbcTemplate, "inv_project_cost_layer")).isZero();
		assertThat(count(jdbcTemplate, "inv_value_movement")).isZero();
	}

	@Test
	void v21代表性存量升级到v25必须保留既有计价状态并补齐成本层余额预留身份和价值流水长度() {
		migrate("21");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("21");
		RepresentativeStockData data = insertRepresentativeV20Stock(jdbcTemplate);
		long oldDraftLineId = insertLegacyStocktakeDraft(jdbcTemplate, data.valuedBalanceId());

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		assertStockBalanceUniqueIndexesIncludeCostLayer(jdbcTemplate);
		assertStocktakeVarianceColumnsNullable(jdbcTemplate);
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);
		assertLegacyStocktakeDraftVarianceFieldsNull(jdbcTemplate, oldDraftLineId);
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
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':' || valuation_state
				from inv_stock_movement
				where id = ?
				""", data.movementId())).isEqualTo("PUBLIC:NULL:LEGACY_UNVALUED");
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':'
					|| coalesce(cost_layer_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", data.reservationId())).isEqualTo("PUBLIC:NULL:NULL");
		assertThat(count(jdbcTemplate, "inv_project_cost_layer")).isZero();
		assertThat(count(jdbcTemplate, "inv_value_movement")).isZero();
	}

	@Test
	void v22到v25必须为预留补齐成本层身份并兼容公共唯一项目历史项目空层和价值流水长度() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		ReservationLayerMigrationData data = insertV22ReservationLayerData(jdbcTemplate, false);
		long oldDraftLineId = insertLegacyStocktakeDraftForReservation(jdbcTemplate, data.publicReservationId());

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		assertColumnsExist(jdbcTemplate, "inv_stock_reservation", List.of("cost_layer_id"));
		assertStocktakeVarianceColumnsNullable(jdbcTemplate);
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);
		assertLegacyStocktakeDraftVarianceFieldsNull(jdbcTemplate, oldDraftLineId);
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':'
					|| coalesce(cost_layer_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", data.publicReservationId())).isEqualTo("PUBLIC:NULL:NULL");
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':'
					|| coalesce(cost_layer_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", data.activeProjectReservationId()))
			.isEqualTo("PROJECT:" + data.projectId() + ":" + data.costLayerId());
		assertThat(queryText(jdbcTemplate, """
				select status || ':' || ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':'
					|| coalesce(cost_layer_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", data.historyProjectReservationId())).isEqualTo("RELEASED:PROJECT:" + data.projectId()
					+ ":NULL");
	}

	@Test
	void v22到v25仅公共活动预留必须重建公共锁定且清除项目层旧污染锁定() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		PublicReservationMigrationData data = insertV22PublicOnlyReservationData(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);
		assertThat(queryText(jdbcTemplate, """
				select ownership_type || ':' || coalesce(project_id::text, 'NULL') || ':'
					|| coalesce(cost_layer_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", data.publicReservationId())).isEqualTo("PUBLIC:NULL:NULL");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where id = ?
				""", data.publicBalanceId())).isEqualByComparingTo("1.000000");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where id = ?
				""", data.projectBalanceId())).isEqualByComparingTo("0.000000");
	}

	@Test
	void v22到v25追踪物料空批次活动预留必须保留父级聚合且不锁具体批次余额() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		TrackingAggregateReservationMigrationData data = insertV22TrackingAggregateReservationData(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);
		assertThat(queryText(jdbcTemplate, """
				select coalesce(parent_reservation_id::text, 'NULL') || ':'
					|| coalesce(batch_id::text, 'NULL') || ':' || coalesce(serial_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", data.publicReservationId())).isEqualTo("NULL:NULL:NULL");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where id = ?
				""", data.batchABalanceId())).isEqualByComparingTo("0.000000");
		assertThat(queryDecimal(jdbcTemplate, """
				select locked_quantity
				from inv_stock_balance
				where id = ?
				""", data.batchBBalanceId())).isEqualByComparingTo("0.000000");
	}

	@Test
	void 旧v23升级到v25必须经v24新增盘点估值字段并经v25扩展价值流水类型长度() {
		migrate("23");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("23");
		assertStocktakeVarianceColumnsAbsent(jdbcTemplate);
		PublicReservationMigrationData data = insertV22PublicOnlyReservationData(jdbcTemplate);
		long oldDraftLineId = insertLegacyStocktakeDraftForReservation(jdbcTemplate, data.publicReservationId());

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		assertStocktakeVarianceColumnsNullable(jdbcTemplate);
		assertValueMovementTypeLengthAllowsAllEnums(jdbcTemplate);
		assertLegacyStocktakeDraftVarianceFieldsNull(jdbcTemplate, oldDraftLineId);
	}

	@Test
	void v22到v25遇到活动项目预留空层且存在多个候选成本层必须迁移失败() {
		migrate("22");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		insertV22ReservationLayerData(jdbcTemplate, true);

		assertThatThrownBy(() -> migrate(null))
			.as("ACTIVE PROJECT 预留缺 costLayerId 且同维度存在多个成本层时必须拒绝升级，避免迁移后浮动锁定")
			.isInstanceOf(Exception.class)
			.hasMessageContaining("active project reservation cost layer is ambiguous");
	}

	private ReservationLayerMigrationData insertV22ReservationLayerData(JdbcTemplate jdbcTemplate,
			boolean ambiguousProjectLayer) {
		int suffix = (int) count(jdbcTemplate, "mst_unit") + 2300;
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_UNIT_" + suffix, "023迁移V23单位");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_WH_" + suffix, "023迁移V23仓库");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_CAT_" + suffix, "023迁移V23分类");
		long materialId = insertMaterial(jdbcTemplate, "S23_MIG23_VAL_" + suffix, "VALUATED_MATERIAL", true,
				categoryId, unitId, "NONE");
		long customerId = id(jdbcTemplate, """
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_CUS_" + suffix, "023迁移V23客户");
		long ownerUserId = id(jdbcTemplate, """
				insert into sys_user (
					username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'test', ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "s23_mig23_owner_" + suffix, "023迁移V23负责人");
		long projectId = id(jdbcTemplate, """
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, date '2026-01-01', date '2026-12-31', 'ACTIVE', 100000.00, 60000.00,
					'test', now(), 'test', now())
				returning id
				""", "S23-MIG23-PRJ-" + suffix, "023迁移V23项目", customerId, ownerUserId);
		id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, ownership_type, valuation_state, inventory_amount, average_unit_cost,
					created_at, updated_at
				)
				values (?, ?, ?, 5.000000, 0.000000, 'QUALIFIED', 'PUBLIC', 'VALUED',
					50.00, 10.000000, now(), now())
				returning id
				""", warehouseId, materialId, unitId);
		long costLayerId = id(jdbcTemplate, """
				insert into inv_project_cost_layer (
					project_id, material_id, source_type, source_id, source_line_id, original_quantity,
					original_amount, remaining_quantity, remaining_amount, unit_cost, status,
					created_at, updated_at
				)
				values (?, ?, 'MIGRATION_TEST', 1, 1, 3.000000, 30.00, 3.000000, 30.00, 10.000000, 'ACTIVE',
					now(), now())
				returning id
				""", projectId, materialId);
		id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, ownership_type, project_id, valuation_state, inventory_amount,
					average_unit_cost, cost_layer_id, created_at, updated_at
				)
				values (?, ?, ?, 3.000000, 1.000000, 'QUALIFIED', 'PROJECT', ?, 'VALUED',
					30.00, 10.000000, ?, now(), now())
				returning id
				""", warehouseId, materialId, unitId, projectId, costLayerId);
		if (ambiguousProjectLayer) {
			long secondLayerId = id(jdbcTemplate, """
					insert into inv_project_cost_layer (
						project_id, material_id, source_type, source_id, source_line_id, original_quantity,
						original_amount, remaining_quantity, remaining_amount, unit_cost, status,
						created_at, updated_at
					)
					values (?, ?, 'MIGRATION_TEST', 2, 2, 2.000000, 24.00, 2.000000, 24.00, 12.000000,
						'ACTIVE', now(), now())
					returning id
					""", projectId, materialId);
			id(jdbcTemplate, """
					insert into inv_stock_balance (
						warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
						quality_status, ownership_type, project_id, valuation_state, inventory_amount,
						average_unit_cost, cost_layer_id, created_at, updated_at
					)
					values (?, ?, ?, 2.000000, 0.000000, 'QUALIFIED', 'PROJECT', ?, 'VALUED',
						24.00, 12.000000, ?, now(), now())
					returning id
					""", warehouseId, materialId, unitId, projectId, secondLayerId);
		}
		long publicReservationId = insertV22Reservation(jdbcTemplate, "S23-MIG23-PUBLIC-" + suffix,
				"PUBLIC", null, "ACTIVE", warehouseId, materialId, unitId, 10);
		long activeProjectReservationId = insertV22Reservation(jdbcTemplate, "S23-MIG23-PROJECT-" + suffix,
				"PROJECT", projectId, "ACTIVE", warehouseId, materialId, unitId, 20);
		long historyProjectReservationId = insertV22Reservation(jdbcTemplate, "S23-MIG23-HISTORY-" + suffix,
				"PROJECT", projectId, "RELEASED", warehouseId, materialId, unitId, 30);
		return new ReservationLayerMigrationData(projectId, costLayerId, publicReservationId,
				activeProjectReservationId, historyProjectReservationId);
	}

	private PublicReservationMigrationData insertV22PublicOnlyReservationData(JdbcTemplate jdbcTemplate) {
		int suffix = (int) count(jdbcTemplate, "mst_unit") + 2400;
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_PUB_UNIT_" + suffix, "023迁移V23公共预留单位");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_PUB_WH_" + suffix, "023迁移V23公共预留仓库");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_PUB_CAT_" + suffix, "023迁移V23公共预留分类");
		long materialId = insertMaterial(jdbcTemplate, "S23_MIG23_PUB_VAL_" + suffix, "VALUATED_MATERIAL", true,
				categoryId, unitId, "NONE");
		long publicBalanceId = id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, ownership_type, valuation_state, inventory_amount, average_unit_cost,
					created_at, updated_at
				)
				values (?, ?, ?, 5.000000, 0.000000, 'QUALIFIED', 'PUBLIC', 'VALUED',
					50.00, 10.000000, now(), now())
				returning id
				""", warehouseId, materialId, unitId);
		long customerId = id(jdbcTemplate, """
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_PUB_CUS_" + suffix, "023迁移V23公共预留客户");
		long ownerUserId = id(jdbcTemplate, """
				insert into sys_user (
					username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'test', ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "s23_mig23_pub_owner_" + suffix, "023迁移V23公共预留负责人");
		long projectId = id(jdbcTemplate, """
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, date '2026-01-01', date '2026-12-31', 'ACTIVE', 100000.00, 60000.00,
					'test', now(), 'test', now())
				returning id
				""", "S23-MIG23-PUB-PRJ-" + suffix, "023迁移V23公共预留项目", customerId, ownerUserId);
		long costLayerId = id(jdbcTemplate, """
				insert into inv_project_cost_layer (
					project_id, material_id, source_type, source_id, source_line_id, original_quantity,
					original_amount, remaining_quantity, remaining_amount, unit_cost, status,
					created_at, updated_at
				)
				values (?, ?, 'MIGRATION_TEST', 1, 1, 3.000000, 30.00, 3.000000, 30.00, 10.000000,
					'ACTIVE', now(), now())
				returning id
				""", projectId, materialId);
		long projectBalanceId = id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, ownership_type, project_id, valuation_state, inventory_amount,
					average_unit_cost, cost_layer_id, created_at, updated_at
				)
				values (?, ?, ?, 3.000000, 1.000000, 'QUALIFIED', 'PROJECT', ?, 'VALUED',
					30.00, 10.000000, ?, now(), now())
				returning id
				""", warehouseId, materialId, unitId, projectId, costLayerId);
		long publicReservationId = insertV22Reservation(jdbcTemplate, "S23-MIG23-PUB-RES-" + suffix,
				"PUBLIC", null, "ACTIVE", warehouseId, materialId, unitId, 40);
		return new PublicReservationMigrationData(publicReservationId, publicBalanceId, projectBalanceId);
	}

	private TrackingAggregateReservationMigrationData insertV22TrackingAggregateReservationData(
			JdbcTemplate jdbcTemplate) {
		int suffix = (int) count(jdbcTemplate, "mst_unit") + 2500;
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_AGG_UNIT_" + suffix, "023迁移V23聚合预留单位");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_AGG_WH_" + suffix, "023迁移V23聚合预留仓库");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", "S23_MIG23_AGG_CAT_" + suffix, "023迁移V23聚合预留分类");
		long materialId = insertMaterial(jdbcTemplate, "S23_MIG23_AGG_VAL_" + suffix, "VALUATED_MATERIAL", true,
				categoryId, unitId, "BATCH");
		long batchAId = id(jdbcTemplate, """
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'MIGRATION_TEST', 1, 1, date '2026-01-10', '023迁移V23聚合批次A',
					'test', now(), 'test', now())
				returning id
				""", materialId, "S23-MIG23-AGG-A-" + suffix);
		long batchBId = id(jdbcTemplate, """
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'MIGRATION_TEST', 1, 2, date '2026-01-10', '023迁移V23聚合批次B',
					'test', now(), 'test', now())
				returning id
				""", materialId, "S23-MIG23-AGG-B-" + suffix);
		long publicPoolId = id(jdbcTemplate, """
				insert into inv_public_valuation_pool (
					material_id, quantity, amount, average_unit_cost, valuation_state
				)
				values (?, 8.000000, 80.00, 10.000000, 'VALUED')
				returning id
				""", materialId);
		long batchABalanceId = insertV22PublicBatchBalance(jdbcTemplate, warehouseId, materialId, unitId,
				batchAId, publicPoolId);
		long batchBBalanceId = insertV22PublicBatchBalance(jdbcTemplate, warehouseId, materialId, unitId,
				batchBId, publicPoolId);
		long publicReservationId = insertV22Reservation(jdbcTemplate, "S23-MIG23-AGG-RES-" + suffix,
				"PUBLIC", null, "ACTIVE", warehouseId, materialId, unitId, 50);
		return new TrackingAggregateReservationMigrationData(publicReservationId, batchABalanceId, batchBBalanceId);
	}

	private long insertV22PublicBatchBalance(JdbcTemplate jdbcTemplate, long warehouseId, long materialId,
			long unitId, long batchId, long publicPoolId) {
		return id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, batch_id, ownership_type, valuation_state, inventory_amount,
					average_unit_cost, public_pool_id, created_at, updated_at
				)
				values (?, ?, ?, 4.000000, 1.000000, 'QUALIFIED', ?, 'PUBLIC', 'VALUED',
					40.00, 10.000000, ?, now(), now())
				returning id
				""", warehouseId, materialId, unitId, batchId, publicPoolId);
	}

	private long insertV22Reservation(JdbcTemplate jdbcTemplate, String reservationNo, String ownershipType,
			Long projectId, String status, long warehouseId, long materialId, long unitId, long sourceLineId) {
		return id(jdbcTemplate, """
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id,
					quality_status, quantity, released_quantity, consumed_quantity, source_type,
					source_id, source_line_id, source_document_no, business_date, reason,
					created_by, created_at, updated_by, updated_at, ownership_type, project_id
				)
				values (?, 'RESERVATION', ?, ?, ?, ?, 'QUALIFIED', 1.000000,
					case when ? = 'RELEASED' then 1.000000 else 0.000000 end,
					0.000000, 'STAGE023_MIGRATION', 23, ?, ?, date '2026-01-11',
					'023迁移V23预留', 'test', now(), 'test', now(), ?, ?)
				returning id
				""", reservationNo, status, warehouseId, materialId, unitId, status, sourceLineId,
				reservationNo, ownershipType, projectId);
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

	private RepresentativeStockData insertRepresentativeV20Stock(JdbcTemplate jdbcTemplate) {
		long unitId = id(jdbcTemplate, """
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S23_MIG20_UNIT', '023迁移V20单位', 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long warehouseId = id(jdbcTemplate, """
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values ('S23_MIG20_WH', '023迁移V20仓库', 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""");
		long categoryId = id(jdbcTemplate, """
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values ('S23_MIG20_CAT', '023迁移V20分类', 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""");
		long valuedMaterialId = insertMaterial(jdbcTemplate, "S23_MIG20_VAL", "VALUATED_MATERIAL", true,
				categoryId, unitId, "BATCH");
		long nonValuedMaterialId = insertMaterial(jdbcTemplate, "S23_MIG20_NVAL", "NON_VALUATED_CONSUMABLE", false,
				categoryId, unitId, "NONE");
		long batchId = id(jdbcTemplate, """
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, 'S23-MIG20-BATCH-001', 'MIGRATION_TEST', 1, 1, date '2026-01-10',
					'023迁移V20批次', 'test', now(), 'test', now())
				returning id
				""", valuedMaterialId);
		long valuedBalanceId = id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, batch_id, ownership_type, valuation_state, inventory_amount,
					average_unit_cost, created_at, updated_at
				)
				values (?, ?, ?, 12.000000, 2.000000, 'FROZEN', ?, 'PUBLIC', 'LEGACY_UNVALUED',
					null, null, now(), now())
				returning id
				""", warehouseId, valuedMaterialId, unitId, batchId);
		long nonValuedBalanceId = id(jdbcTemplate, """
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity,
					quality_status, ownership_type, valuation_state, created_at, updated_at
				)
				values (?, ?, ?, 7.000000, 0.000000, 'QUALIFIED', 'PUBLIC', 'NON_VALUED', now(), now())
				returning id
				""", warehouseId, nonValuedMaterialId, unitId);
		id(jdbcTemplate, """
				insert into inv_public_valuation_pool (
					material_id, quantity, amount, average_unit_cost, valuation_state
				)
				values (?, 12.000000, null, null, 'LEGACY_UNVALUED')
				returning id
				""", valuedMaterialId);
		long movementId = id(jdbcTemplate, """
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, operator_name, occurred_at, quality_status, batch_id, ownership_type, valuation_state
				)
				values ('S23-MIG20-MOV-001', 'OPENING', 'IN', ?, ?, ?, 12.000000, 0.000000, 12.000000,
					'MIGRATION_TEST', 1, 1, date '2026-01-10', '023迁移V20历史流水', 'test', now(),
					'FROZEN', ?, 'PUBLIC', 'LEGACY_UNVALUED')
				returning id
				""", warehouseId, valuedMaterialId, unitId, batchId);
		long reservationId = id(jdbcTemplate, """
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id,
					quality_status, quantity, released_quantity, consumed_quantity, source_type,
					source_id, source_line_id, source_document_no, business_date, reason,
					created_by, created_at, updated_by, updated_at, batch_id, ownership_type
				)
				values ('S23-MIG20-RES-001', 'RESERVATION', 'ACTIVE', ?, ?, ?, 'FROZEN',
					3.000000, 0.000000, 0.000000, 'SALES_ORDER', 1, 1, 'S23-SO-020',
					date '2026-01-11', '023迁移V20预留', 'test', now(), 'test', now(), ?, 'PUBLIC')
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

	private long insertLegacyStocktakeDraftForReservation(JdbcTemplate jdbcTemplate, long reservationId) {
		Long balanceId = jdbcTemplate.queryForObject("""
				select b.id
				from inv_stock_reservation r
				join inv_stock_balance b on b.warehouse_id = r.warehouse_id
					and b.material_id = r.material_id
					and b.unit_id = r.unit_id
					and b.quality_status = r.quality_status
					and b.ownership_type = r.ownership_type
					and b.project_id is not distinct from r.project_id
				where r.id = ?
				order by b.id
				limit 1
				""", Long.class, reservationId);
		return insertLegacyStocktakeDraft(jdbcTemplate, balanceId);
	}

	private long insertLegacyStocktakeDraft(JdbcTemplate jdbcTemplate, long balanceId) {
		long suffix = count(jdbcTemplate, "inv_stocktake") + 1;
		long createdByUserId = insertLegacyUser(jdbcTemplate, "s23_mig_stocktake_owner_" + suffix);
		long stocktakeId = id(jdbcTemplate, """
				insert into inv_stocktake (
					stocktake_no, business_date, scope_type, warehouse_id, material_id, reason, status,
					idempotency_key, created_by_user_id, created_by_username, updated_by_username
				)
				select ?, current_date, 'WAREHOUSE', warehouse_id, null, '023迁移旧盘点行兼容', 'COUNTING',
					?, ?, 'test', 'test'
				from inv_stock_balance
				where id = ?
				returning id
				""", "S23-MIG-STK-" + suffix, "S23-MIG-STK-IDEMP-" + suffix, createdByUserId, balanceId);
		return id(jdbcTemplate, """
				insert into inv_stocktake_line (
					stocktake_id, balance_id, line_no, warehouse_id, material_id, unit_id, quality_status,
					ownership_type, project_id, batch_id, serial_id, book_quantity, counted_quantity,
					variance_quantity
				)
				select ?, id, 1, warehouse_id, material_id, unit_id, quality_status, ownership_type, project_id,
					batch_id, serial_id, quantity_on_hand, null, null
				from inv_stock_balance
				where id = ?
				returning id
				""", stocktakeId, balanceId);
	}

	private long insertLegacyUser(JdbcTemplate jdbcTemplate, String username) {
		return id(jdbcTemplate, """
				insert into sys_user (
					username, password_hash, display_name, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'test', ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", username, "023迁移旧盘点创建人");
	}

	private void assertStocktakeVarianceColumnsNullable(JdbcTemplate jdbcTemplate) {
		assertColumnsExist(jdbcTemplate, "inv_stocktake_line", List.of(
				"variance_unit_cost",
				"variance_reason"));
		SoftAssertions.assertSoftly((softly) -> {
			for (String columnName : List.of("variance_unit_cost", "variance_reason")) {
				softly.assertThat(queryText(jdbcTemplate, """
						select is_nullable
						from information_schema.columns
						where table_schema = 'public'
						  and table_name = 'inv_stocktake_line'
						  and column_name = ?
						""", columnName)).as(columnName + " 必须允许旧盘点行为空").isEqualTo("YES");
			}
			softly.assertThat(constraintExists(jdbcTemplate, "inv_stocktake_line",
					"ck_inv_stocktake_line_variance_unit_cost"))
				.as("盘点行盘盈单价必须有非负数据库约束")
				.isTrue();
			softly.assertThat(indexExists(jdbcTemplate, "idx_inv_stocktake_line_stocktake_order"))
				.as("盘点行分页必须有稳定顺序索引")
				.isTrue();
		});
	}

	private void assertValueMovementTypeLengthAllowsAllEnums(JdbcTemplate jdbcTemplate) {
		assertThat(columnLength(jdbcTemplate, "inv_value_movement", "movement_type"))
			.as("价值流水 movement_type 必须容纳最长库存移动枚举")
			.isGreaterThanOrEqualTo(longestMovementTypeLength());
	}

	private int longestMovementTypeLength() {
		int max = 0;
		for (InventoryMovementType type : InventoryMovementType.values()) {
			max = Math.max(max, type.name().length());
		}
		return max;
	}

	private void assertStocktakeVarianceColumnsAbsent(JdbcTemplate jdbcTemplate) {
		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(columnExists(jdbcTemplate, "inv_stocktake_line", "variance_unit_cost"))
				.as("旧 V23 不应包含盘盈单价字段")
				.isFalse();
			softly.assertThat(columnExists(jdbcTemplate, "inv_stocktake_line", "variance_reason"))
				.as("旧 V23 不应包含盘盈原因字段")
				.isFalse();
			softly.assertThat(constraintExists(jdbcTemplate, "inv_stocktake_line",
					"ck_inv_stocktake_line_variance_unit_cost"))
				.as("旧 V23 不应包含 V24 非负约束")
				.isFalse();
			softly.assertThat(indexExists(jdbcTemplate, "idx_inv_stocktake_line_stocktake_order"))
				.as("旧 V23 不应包含 V24 盘点行分页索引")
				.isFalse();
		});
	}

	private void assertLegacyStocktakeDraftVarianceFieldsNull(JdbcTemplate jdbcTemplate, long lineId) {
		assertThat(queryDecimal(jdbcTemplate, """
				select variance_unit_cost
				from inv_stocktake_line
				where id = ?
				""", lineId)).isNull();
		assertThat(queryText(jdbcTemplate, """
				select variance_reason
				from inv_stocktake_line
				where id = ?
				""", lineId)).isNull();
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

	private void assertStockBalanceUniqueIndexesIncludeCostLayer(JdbcTemplate jdbcTemplate) {
		SoftAssertions.assertSoftly((softly) -> {
			assertIndexDefinitionContains(softly, jdbcTemplate, "uk_inv_stock_balance_untracked", "cost_layer_id");
			assertIndexDefinitionContains(softly, jdbcTemplate, "uk_inv_stock_balance_batch", "cost_layer_id");
			assertIndexDefinitionContains(softly, jdbcTemplate, "uk_inv_stock_balance_serial", "cost_layer_id");
		});
	}

	private void assertIndexDefinitionContains(SoftAssertions softly, JdbcTemplate jdbcTemplate, String indexName,
			String expectedColumn) {
		String indexDefinition = jdbcTemplate.queryForObject("""
				select indexdef
				from pg_indexes
				where schemaname = 'public'
				  and indexname = ?
				""", String.class, indexName);
		softly.assertThat(indexDefinition == null ? "" : indexDefinition.toLowerCase())
			.as(indexName + " 必须包含 " + expectedColumn)
			.contains(expectedColumn);
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

	private Integer columnLength(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		return jdbcTemplate.queryForObject("""
				select character_maximum_length
				from information_schema.columns
				where table_schema = 'public'
				  and table_name = ?
				  and column_name = ?
				""", Integer.class, tableName, columnName);
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

	private record ReservationLayerMigrationData(
			long projectId,
			long costLayerId,
			long publicReservationId,
			long activeProjectReservationId,
			long historyProjectReservationId) {
	}

	private record PublicReservationMigrationData(
			long publicReservationId,
			long publicBalanceId,
			long projectBalanceId) {
	}

	private record TrackingAggregateReservationMigrationData(
			long publicReservationId,
			long batchABalanceId,
			long batchBBalanceId) {
	}

}
