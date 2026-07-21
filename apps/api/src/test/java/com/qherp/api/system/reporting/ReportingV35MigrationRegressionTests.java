package com.qherp.api.system.reporting;

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
class ReportingV35MigrationRegressionTests {

	private static final String LATEST_MIGRATION_VERSION = "36";

	private static final int EXPECTED_V35_CHECKSUM = -82801719;

	private static final int EXPECTED_V36_CHECKSUM = 1030907058;

	private static final List<String> REPORT_033_PERMISSIONS = List.of("report:operating-finance:view",
			"report:project-profit:view", "report:contract-collection:view",
			"report:procurement-variance:view", "report:inventory-capital:view",
			"report:receivable-payable:view", "report:operating-accounting:view",
			"report:financial-summary:view");

	private static final List<String> REPORT_033_SNAPSHOT_CODES = List.of("PROJECT_PROFIT",
			"CONTRACT_COLLECTION", "PROCUREMENT_VARIANCE", "INVENTORY_CAPITAL", "RECEIVABLE_PAYABLE");

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
	void v34前迁v35只追加033报表权限快照代码和索引且保持历史校验和() {
		migrate("34");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		Map<String, Integer> v34Checksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(v34Checksums);
		Map<String, Long> upstreamCounts = upstreamCounts(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 34)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v34Checksums);
		assertThat(latestChecksums.get("35")).isEqualTo(EXPECTED_V35_CHECKSUM);
		assertThat(latestChecksums.get("36")).isEqualTo(EXPECTED_V36_CHECKSUM);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertThat(upstreamCounts(jdbcTemplate)).isEqualTo(upstreamCounts);
		assertReporting033Schema(jdbcTemplate);
	}

	@Test
	void v1到v35空库迁移一次完成并初始化033权限和五个经营快照代码() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.get("35")).isEqualTo(EXPECTED_V35_CHECKSUM);
		assertThat(latestChecksums.get("36")).isEqualTo(EXPECTED_V36_CHECKSUM);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertReporting033Schema(jdbcTemplate);
	}

	private void assertHistoricalChecksums(Map<String, Integer> checksums) {
		assertThat(checksums.get("29")).isEqualTo(774334682);
		assertThat(checksums.get("30")).isEqualTo(2130342893);
		assertThat(checksums.get("31")).isEqualTo(-2074547591);
		assertThat(checksums.get("32")).isEqualTo(249406902);
		assertThat(checksums.get("33")).isEqualTo(612501943);
		assertThat(checksums.get("34")).isEqualTo(-629066235);
	}

	private void assertReporting033Schema(JdbcTemplate jdbcTemplate) {
		assertThat(permissionCount(jdbcTemplate, REPORT_033_PERMISSIONS)).isEqualTo(REPORT_033_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, REPORT_033_PERMISSIONS))
			.isEqualTo(REPORT_033_PERMISSIONS.size());
		assertThat(reportRoutePaths(jdbcTemplate)).contains("/reports/project-profit",
				"/reports/contract-collection", "/reports/procurement-variance", "/reports/inventory-capital",
				"/reports/receivable-payable", "/reports/operating-accounting-reconciliation",
				"/reports/financial-summary");
		String reportSnapshotConstraint = constraint(jdbcTemplate, "biz_period_report_snapshot",
				"ck_biz_period_report_snapshot_code");
		assertThat(reportSnapshotConstraint).contains(REPORT_033_SNAPSHOT_CODES.toArray(String[]::new));
		assertThat(reportSnapshotConstraint).doesNotContain("OPERATING_ACCOUNTING_RECONCILIATION",
				"FINANCIAL_SUMMARY");
		assertThat(anyIndexContains(jdbcTemplate, "prj_cost_calculation", "project_id", "is_current")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "gl_ledger_entry", "period_id", "account_code")).isTrue();
	}

	private Map<String, Long> upstreamCounts(JdbcTemplate jdbcTemplate) {
		return Map.of("prj_cost_calculation", tableCount(jdbcTemplate, "prj_cost_calculation"),
				"biz_period_report_snapshot", tableCount(jdbcTemplate, "biz_period_report_snapshot"),
				"gl_ledger_entry", tableCount(jdbcTemplate, "gl_ledger_entry"),
				"fin_close_run", tableCount(jdbcTemplate, "fin_close_run"));
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

	private long failedMigrationCount(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where success = false",
				Long.class);
	}

	private long tableCount(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
	}

	private long permissionCount(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from sys_permission
				where code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
	}

	private long systemAdminPermissionCount(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role_permission rp
				join sys_role r on r.id = rp.role_id
				join sys_permission p on p.id = rp.permission_id
				where r.code = 'SYSTEM_ADMIN'
				and p.code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
	}

	private List<String> reportRoutePaths(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.query("""
				select route_path
				from sys_permission
				where code = any (?::text[])
				order by code
				""", (rs, rowNum) -> rs.getString("route_path"), (Object) REPORT_033_PERMISSIONS.toArray(String[]::new));
	}

	private String constraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
		return jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = ?
				and c.conname = ?
				""", String.class, tableName, constraintName);
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
