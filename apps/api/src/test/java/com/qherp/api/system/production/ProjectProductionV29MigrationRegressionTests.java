package com.qherp.api.system.production;

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
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ProjectProductionV29MigrationRegressionTests {

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
	void v28升级到v29必须保留历史生产和026快照并启用027约束() {
		migrate("28");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("28");
		Map<String, Integer> v28Checksums = migrationChecksums(jdbcTemplate);
		HistoricalSeed seed = insertHistoricalProductionAndMrp(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("34");
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertThat(migrationChecksums(jdbcTemplate).entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 28)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v28Checksums);
		Map<String, Object> workOrder = jdbcTemplate.queryForMap("""
				select ownership_type, project_id, source_mrp_run_id, source_mrp_suggestion_id,
				       source_mrp_requirement_line_id, status, planned_quantity, bom_id
				from mfg_work_order
				where id = ?
				""", seed.workOrderId());
		assertThat(workOrder.get("ownership_type")).isEqualTo("PUBLIC");
		assertThat(workOrder.get("project_id")).isNull();
		assertThat(workOrder.get("source_mrp_run_id")).isNull();
		assertThat(workOrder.get("source_mrp_suggestion_id")).isNull();
		assertThat(workOrder.get("source_mrp_requirement_line_id")).isNull();
		assertThat(workOrder.get("status")).isEqualTo("RELEASED");
		assertThat((BigDecimal) workOrder.get("planned_quantity")).isEqualByComparingTo("3.000000");
		assertThat(((Number) workOrder.get("bom_id")).longValue()).isEqualTo(seed.bomId());
		assertThat(jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_work_order_material
				where work_order_id = ?
				""", Long.class, seed.workOrderId())).isOne();
		assertThat(jdbcTemplate.queryForObject("""
				select source_snapshot::text
				from mrp_calculation_run
				where id = ?
				""", String.class, seed.mrpRunId())).contains("027-V28-SNAPSHOT");
		assertThat(jdbcTemplate.queryForObject("""
				select status
				from mrp_suggestion
				where id = ?
				""", String.class, seed.mrpSuggestionId())).isEqualTo("CONFIRMED");
		assertThat(constraintDefinition(jdbcTemplate, "ck_mfg_outsourcing_order_status"))
			.contains("IN_PROGRESS")
			.doesNotContain("ISSUED")
			.doesNotContain("PARTIALLY_RECEIVED");
		assertThat(tableExists(jdbcTemplate, "mfg_action_idempotency")).isTrue();
		jdbcTemplate.update("""
				insert into mfg_action_idempotency (
					operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
					idempotency_key, request_fingerprint, result_resource_type, result_resource_id, result_version
				)
				values (1, 'admin', 'TEST', 'MFG_WORK_ORDER', ?, 0, 'V29-MIG-IDEM', 'abc',
					'MFG_WORK_ORDER', ?, 0)
				""", seed.workOrderId(), seed.workOrderId());
		assertThatThrownBy(() -> jdbcTemplate.update("""
				insert into mfg_action_idempotency (
					operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
					idempotency_key, request_fingerprint, result_resource_type, result_resource_id, result_version
				)
				values (1, 'admin', 'TEST', 'MFG_WORK_ORDER', ?, 0, 'V29-MIG-IDEM', 'abc',
					'MFG_WORK_ORDER', ?, 0)
				""", seed.workOrderId(), seed.workOrderId())).hasMessageContaining("uk_mfg_action_idempotency");
	}

	@Test
	void 空库v1到v29迁移必须包含027生产外协结构() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("34");
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertThat(tableExists(jdbcTemplate, "mfg_outsourcing_order")).isTrue();
		assertThat(tableExists(jdbcTemplate, "mfg_action_idempotency")).isTrue();
		assertThat(columnExists(jdbcTemplate, "mfg_work_order", "ownership_type")).isTrue();
	}

	private HistoricalSeed insertHistoricalProductionAndMrp(JdbcTemplate jdbcTemplate) {
		long unitId = jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values ('V29-U', 'V29单位', 6, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", Long.class);
		long issueWarehouseId = insertWarehouse(jdbcTemplate, "V29-IW");
		long receiptWarehouseId = insertWarehouse(jdbcTemplate, "V29-RW");
		long categoryId = jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values ('V29-CAT', 'V29分类', 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", Long.class);
		long productId = insertMaterial(jdbcTemplate, "V29-FG", "FINISHED_GOOD", "SELF_MADE", categoryId, unitId);
		long rawId = insertMaterial(jdbcTemplate, "V29-RM", "RAW_MATERIAL", "PURCHASED", categoryId, unitId);
		long bomId = jdbcTemplate.queryForObject("""
				insert into mfg_bom (bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id,
					status, effective_from, created_by, created_at, updated_by, updated_at, enabled_by, enabled_at)
				values ('V29-BOM', ?, 'V1', 'V29历史BOM', 1.000000, ?, 'ENABLED', ?, 'test', now(), 'test',
					now(), 'test', now())
				returning id
				""", Long.class, productId, unitId, LocalDate.now().minusDays(10));
		long bomItemId = jdbcTemplate.queryForObject("""
				insert into mfg_bom_item (bom_id, line_no, child_material_id, unit_id, quantity, loss_rate,
					business_unit_id, business_quantity, base_unit_id, base_quantity, quantity_basis,
					created_at, updated_at)
				values (?, 1, ?, ?, 2.000000, 0.000000, ?, 2.000000, ?, 2.000000, 'BASE_UNIT', now(), now())
				returning id
				""", Long.class, bomId, rawId, unitId, unitId, unitId);
		long workOrderId = jdbcTemplate.queryForObject("""
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity,
					qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark, created_by,
					created_at, updated_by, updated_at, released_by, released_at
				)
				values ('V29-WO', ?, ?, 3.000000, 0.000000, 0.000000, 0.000000, 0.000000, ?, ?, ?, ?,
					'RELEASED', 'V28历史工单', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, productId, bomId, issueWarehouseId, receiptWarehouseId, LocalDate.now(),
				LocalDate.now().plusDays(2));
		long workOrderMaterialId = jdbcTemplate.queryForObject("""
				insert into mfg_work_order_material (
					work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity, issued_quantity,
					loss_rate, remark, created_at, updated_at, business_unit_id, business_quantity, base_unit_id,
					base_required_quantity, quantity_basis
				)
				values (?, 1, ?, ?, ?, 6.000000, 0.000000, 0.000000, 'V28历史用料', now(), now(),
					?, 6.000000, ?, 6.000000, 'BASE_UNIT')
				returning id
				""", Long.class, workOrderId, bomItemId, rawId, unitId, unitId, unitId);
		long issueId = jdbcTemplate.queryForObject("""
				insert into mfg_material_issue (issue_no, work_order_id, status, business_date, reason, remark,
					created_by, created_at, updated_by, updated_at)
				values ('V29-MI', ?, 'DRAFT', ?, 'V28历史领料', '迁移回归', 'test', now(), 'test', now())
				returning id
				""", Long.class, workOrderId, LocalDate.now());
		jdbcTemplate.update("""
				insert into mfg_material_issue_line (
					issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, remark, created_at, updated_at
				)
				values (?, ?, 1, ?, ?, ?, 1.000000, 10.000000, 9.000000, 'V28历史领料行', now(), now())
				""", issueId, workOrderMaterialId, issueWarehouseId, rawId, unitId);
		jdbcTemplate.update("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, quality_status, operator_name, occurred_at
				)
				values ('V29-MV', 'PRODUCTION_ISSUE', 'OUT', ?, ?, ?, 1.000000, 10.000000, 9.000000,
					'PRODUCTION_MATERIAL_ISSUE', ?, ?, ?, 'V28历史库存流水', 'QUALIFIED', 'test', now())
				""", issueWarehouseId, rawId, unitId, issueId, issueId, LocalDate.now());
		long runId = jdbcTemplate.queryForObject("""
				insert into mrp_calculation_run (
					run_no, scope_type, demand_date_to, include_public_demand, scope_hash, request_fingerprint,
					source_snapshot, status, calculated_at, idempotency_key, created_by_username, created_at,
					updated_by, updated_at
				)
				values ('V29-MRP', 'GLOBAL', ?, true, 'scope-v29', 'request-v29',
					'{"marker":"027-V28-SNAPSHOT"}'::jsonb, 'COMPLETED', now(), 'V29-MRP-IDEM',
					'test', now(), 'test', now())
				returning id
				""", Long.class, LocalDate.now().plusDays(7));
		long requirementLineId = jdbcTemplate.queryForObject("""
				insert into mrp_requirement_line (
					run_id, line_no, demand_source_type, demand_type, material_id, unit_id, demand_date,
					required_quantity, covered_quantity, shortage_quantity, source_snapshot
				)
				values (?, 1, 'TEST', 'SALES_DEMAND', ?, ?, ?, 3.000000, 0.000000, 3.000000,
					'{"marker":"027-V28-SNAPSHOT"}'::jsonb)
				returning id
				""", Long.class, runId, productId, unitId, LocalDate.now().plusDays(7));
		long suggestionId = jdbcTemplate.queryForObject("""
				insert into mrp_suggestion (
					run_id, requirement_line_id, suggestion_type, status, material_id, unit_id, ownership_type,
					required_date, suggested_quantity, material_source_type, conversion_allowed, reason,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'PRODUCTION_ORDER', 'CONFIRMED', ?, ?, 'PUBLIC', ?, 3.000000, 'SELF_MADE',
					true, 'V28历史生产建议', 'test', now(), 'test', now())
				returning id
				""", Long.class, runId, requirementLineId, productId, unitId, LocalDate.now().plusDays(7));
		return new HistoricalSeed(workOrderId, bomId, runId, suggestionId);
	}

	private long insertWarehouse(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "仓库");
	}

	private long insertMaterial(JdbcTemplate jdbcTemplate, String code, String materialType, String sourceType,
			long categoryId, long unitId) {
		return jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, material_type, source_type, category_id, unit_id, status,
					created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, ?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "物料", materialType, sourceType, categoryId, unitId);
	}

	private void migrate(String target) {
		var configuration = Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration");
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

	private String currentFlywayVersion(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("""
				select version
				from flyway_schema_history
				where success = true
				order by installed_rank desc
				limit 1
				""", String.class);
	}

	private Map<String, Integer> migrationChecksums(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.query("""
				select version, checksum
				from flyway_schema_history
				where success = true
				  and version is not null
				order by installed_rank
				""", (rs, rowNum) -> Map.entry(rs.getString("version"), rs.getInt("checksum")))
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
		Boolean exists = jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from information_schema.tables
					where table_schema = 'public'
					  and table_name = ?
				)
				""", Boolean.class, tableName);
		return Boolean.TRUE.equals(exists);
	}

	private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		Boolean exists = jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from information_schema.columns
					where table_schema = 'public'
					  and table_name = ?
					  and column_name = ?
				)
				""", Boolean.class, tableName, columnName);
		return Boolean.TRUE.equals(exists);
	}

	private String constraintDefinition(JdbcTemplate jdbcTemplate, String constraintName) {
		return jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				where c.conname = ?
				""", String.class, constraintName);
	}

	private record HistoricalSeed(long workOrderId, long bomId, long mrpRunId, long mrpSuggestionId) {
	}
}
