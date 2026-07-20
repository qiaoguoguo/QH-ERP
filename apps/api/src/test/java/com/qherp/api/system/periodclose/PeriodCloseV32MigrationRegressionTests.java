package com.qherp.api.system.periodclose;

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
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PeriodCloseV32MigrationRegressionTests {

	private static final int EXPECTED_V29_CHECKSUM = 774334682;

	private static final int EXPECTED_V30_CHECKSUM = 2130342893;

	private static final int EXPECTED_V31_CHECKSUM = -2074547591;

	private static final int EXPECTED_V32_CHECKSUM = 249406902;

	private static final List<String> PERIOD_CLOSE_TABLES = List.of("biz_period_close_run",
			"biz_period_close_check_run", "biz_period_close_check_item", "biz_period_snapshot",
			"biz_period_inventory_snapshot", "biz_period_inventory_summary", "biz_period_wip_snapshot",
			"biz_period_project_cost_snapshot", "biz_period_report_snapshot",
			"biz_period_close_action_idempotency", "biz_period_close_audit");

	private static final List<String> PERIOD_CLOSE_PERMISSIONS = List.of("system:business-period-close:view",
			"system:business-period-close:check", "system:business-period-close:close",
			"system:business-period-close:reopen", "system:business-period-close:snapshot-view");

	private static final List<String> RUN_STATUSES = List.of("PENDING_CHECK", "BLOCKED", "READY", "CLOSED",
			"REOPENED");

	private static final List<String> CHECK_SEVERITIES = List.of("BLOCKING", "WARNING");

	private static final List<String> REPORT_CODES = List.of("OVERVIEW", "SALES_SUMMARY", "PROCUREMENT_SUMMARY",
			"INVENTORY_STOCK_FLOW", "PRODUCTION_EXECUTION", "COST_COLLECTION", "SETTLEMENT_SUMMARY",
			"EXCEPTIONS");

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
	void v31前迁v32只追加业务月结模型并保持既有迁移校验和() {
		migrate("31");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("31");
		Map<String, Integer> v31Checksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(v31Checksums);
		Map<String, Long> upstreamCounts = upstreamCounts(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("33");
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.get("32")).isEqualTo(EXPECTED_V32_CHECKSUM);
		assertThat(latestChecksums.entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 31)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v31Checksums);
		assertThat(upstreamCounts(jdbcTemplate)).isEqualTo(upstreamCounts);
		assertPeriodCloseSchema(jdbcTemplate);
	}

	@Test
	void v1到v32空库迁移一次性完成并初始化月结权限和约束() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("33");
		Map<String, Integer> checksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(checksums);
		assertThat(checksums.get("32")).isEqualTo(EXPECTED_V32_CHECKSUM);
		assertPeriodCloseSchema(jdbcTemplate);
	}

	private void assertHistoricalChecksums(Map<String, Integer> checksums) {
		assertThat(checksums.get("29")).isEqualTo(EXPECTED_V29_CHECKSUM);
		assertThat(checksums.get("30")).isEqualTo(EXPECTED_V30_CHECKSUM);
		assertThat(checksums.get("31")).isEqualTo(EXPECTED_V31_CHECKSUM);
	}

	private void assertPeriodCloseSchema(JdbcTemplate jdbcTemplate) {
		for (String table : PERIOD_CLOSE_TABLES) {
			assertThat(tableExists(jdbcTemplate, table)).as(table).isTrue();
		}
		assertThat(permissionCount(jdbcTemplate, PERIOD_CLOSE_PERMISSIONS)).isEqualTo(PERIOD_CLOSE_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, PERIOD_CLOSE_PERMISSIONS))
			.isEqualTo(PERIOD_CLOSE_PERMISSIONS.size());
		assertThat(permissionCount(jdbcTemplate, List.of("system:business-period-close:amount-view"))).isZero();
		assertThat(permissionRoutePath(jdbcTemplate, "system")).isEqualTo("/system");
		assertThat(permissionRoutePath(jdbcTemplate, "system:business-period-close")).isEqualTo("/period-close/runs");
		assertThat(permissionParentCode(jdbcTemplate, "system:business-period-close")).isEqualTo("system");

		assertColumns(jdbcTemplate, "biz_period_close_run", List.of("period_id", "revision_no", "status",
				"latest_check_run_id", "snapshot_id", "source_fingerprint", "blocking_count", "warning_count",
				"closed_by", "close_reason", "reopened_by", "reopen_reason", "version"));
		assertColumns(jdbcTemplate, "biz_period_close_check_item", List.of("check_run_id", "check_code", "domain",
				"severity", "source_restricted", "object_type", "object_id", "object_no", "title", "description",
				"suggestion", "source_route"));
		assertColumns(jdbcTemplate, "biz_period_snapshot", List.of("run_id", "period_id", "revision_no",
				"schema_version", "source_check_run_id", "source_fingerprint", "generated_by", "generated_at"));
		assertColumns(jdbcTemplate, "biz_period_report_snapshot", List.of("snapshot_id", "report_code",
				"schema_version", "result_json", "source_count", "fingerprint"));
		assertColumns(jdbcTemplate, "biz_period_close_action_idempotency",
				List.of("action", "resource_type", "resource_id", "idempotency_key", "request_fingerprint"));
		assertColumns(jdbcTemplate, "biz_period_close_audit",
				List.of("period_id", "run_id", "action", "result", "reason", "created_at"));

		assertThat(allConstraintDefinitions(jdbcTemplate, "biz_period_close_run"))
			.contains(RUN_STATUSES.toArray(String[]::new));
		assertThat(allConstraintDefinitions(jdbcTemplate, "biz_period_close_check_item"))
			.contains(CHECK_SEVERITIES.toArray(String[]::new));
		assertThat(allConstraintDefinitions(jdbcTemplate, "biz_period_report_snapshot"))
			.contains(REPORT_CODES.toArray(String[]::new));
		assertThat(anyIndexContains(jdbcTemplate, "biz_period_close_run", "unique", "period_id", "closed")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "biz_period_snapshot", "unique", "period_id", "revision_no"))
			.isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "biz_period_close_action_idempotency", "unique",
				"action", "resource_id", "idempotency_key")).isTrue();
	}

	private Map<String, Long> upstreamCounts(JdbcTemplate jdbcTemplate) {
		return Map.of("biz_business_period", tableCount(jdbcTemplate, "biz_business_period"), "inv_value_movement",
				tableCount(jdbcTemplate, "inv_value_movement"), "inv_stock_balance",
				tableCount(jdbcTemplate, "inv_stock_balance"), "prj_cost_calculation",
				tableCount(jdbcTemplate, "prj_cost_calculation"), "prj_cost_source_line",
				tableCount(jdbcTemplate, "prj_cost_source_line"), "mfg_cost_record",
				tableCount(jdbcTemplate, "mfg_cost_record"));
	}

	private void migrate(String target) {
		var configuration = Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		configuration.load().migrate();
	}

	private DataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(postgres.getDriverClassName());
		dataSource.setUrl(postgres.getJdbcUrl());
		dataSource.setUsername(postgres.getUsername());
		dataSource.setPassword(postgres.getPassword());
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
				""", (rs, rowNum) -> Map.entry(rs.getString("version"), rs.getInt("checksum")))
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private long tableCount(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
	}

	private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from information_schema.tables
				where table_schema = 'public'
				and table_name = ?
				""", Integer.class, tableName);
		return count != null && count > 0;
	}

	private void assertColumns(JdbcTemplate jdbcTemplate, String tableName, List<String> columnNames) {
		for (String columnName : columnNames) {
			assertThat(columnExists(jdbcTemplate, tableName, columnName)).as(tableName + "." + columnName).isTrue();
		}
	}

	private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from information_schema.columns
				where table_schema = 'public'
				and table_name = ?
				and column_name = ?
				""", Integer.class, tableName, columnName);
		return count != null && count > 0;
	}

	private int permissionCount(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		String placeholders = String.join(",", permissionCodes.stream().map((ignored) -> "?").toList());
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from sys_permission
				where code in (%s)
				""".formatted(placeholders), Integer.class, permissionCodes.toArray());
		return count == null ? 0 : count;
	}

	private int systemAdminPermissionCount(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		String placeholders = String.join(",", permissionCodes.stream().map((ignored) -> "?").toList());
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role_permission rp
				join sys_role r on r.id = rp.role_id
				join sys_permission p on p.id = rp.permission_id
				where r.code = 'SYSTEM_ADMIN'
				and p.code in (%s)
				""".formatted(placeholders), Integer.class, permissionCodes.toArray());
		return count == null ? 0 : count;
	}

	private String permissionRoutePath(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				select route_path
				from sys_permission
				where code = ?
				""", String.class, code);
	}

	private String permissionParentCode(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				select parent.code
				from sys_permission child
				join sys_permission parent on parent.id = child.parent_id
				where child.code = ?
				""", String.class, code);
	}

	private String allConstraintDefinitions(JdbcTemplate jdbcTemplate, String tableName) {
		return String.join("\n", jdbcTemplate.query("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = ?
				""", (rs, rowNum) -> rs.getString(1), tableName));
	}

	private boolean anyIndexContains(JdbcTemplate jdbcTemplate, String tableName, String... needles) {
		List<String> indexDefinitions = jdbcTemplate.query("""
				select lower(indexdef)
				from pg_indexes
				where schemaname = 'public'
				and tablename = ?
				""", (rs, rowNum) -> rs.getString(1), tableName);
		return indexDefinitions.stream()
			.anyMatch((indexDefinition) -> List.of(needles)
				.stream()
				.allMatch((needle) -> indexDefinition.contains(needle.toLowerCase())));
	}

}
