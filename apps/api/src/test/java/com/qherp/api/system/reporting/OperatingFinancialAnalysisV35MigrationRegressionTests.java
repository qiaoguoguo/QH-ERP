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
class OperatingFinancialAnalysisV35MigrationRegressionTests {

	private static final int EXPECTED_V29_CHECKSUM = 774334682;

	private static final int EXPECTED_V30_CHECKSUM = 2130342893;

	private static final int EXPECTED_V31_CHECKSUM = -2074547591;

	private static final int EXPECTED_V32_CHECKSUM = 249406902;

	private static final int EXPECTED_V33_CHECKSUM = 612501943;

	private static final int EXPECTED_V34_CHECKSUM = -629066235;

	private static final List<String> OPERATING_FINANCE_PERMISSIONS = List.of(
			"report:operating-finance:view", "report:project-profit:view",
			"report:contract-collection:view", "report:procurement-variance:view",
			"report:inventory-capital:view", "report:receivable-payable:view",
			"report:operating-accounting:view", "report:financial-summary:view");

	private static final List<String> OPERATING_FINANCE_ROUTE_PATHS = List.of(
			"/reports/overview", "/reports/project-profit", "/reports/contract-collection",
			"/reports/procurement-variance", "/reports/inventory-capital", "/reports/receivable-payable",
			"/reports/operating-accounting-reconciliation", "/reports/financial-summary");

	private static final List<String> SNAPSHOT_REPORT_CODES = List.of("PROJECT_PROFIT",
			"CONTRACT_COLLECTION", "PROCUREMENT_VARIANCE", "INVENTORY_CAPITAL",
			"RECEIVABLE_PAYABLE");

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
	void v34前迁v35只追加033报表权限菜单和快照代码并保持014与上游事实() {
		migrate("34");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		Map<String, Integer> v34Checksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(v34Checksums);
		Map<String, Long> upstreamCounts = upstreamCounts(jdbcTemplate);
		Map<String, String> legacyReportRoutes = reportPermissionRoutes(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("35");
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 34)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v34Checksums);
		assertThat(latestChecksums.get("35")).as("V35 checksum 必须由 Flyway 记录，冻结后再替换为精确值").isNotNull();
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertThat(upstreamCounts(jdbcTemplate)).isEqualTo(upstreamCounts);
		assertThat(reportPermissionRoutes(jdbcTemplate)).containsAllEntriesOf(legacyReportRoutes);
		assertOperatingFinanceSeeds(jdbcTemplate);
	}

	@Test
	void v1到v35空库迁移一次完成并初始化033报表契约() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("35");
		assertHistoricalChecksums(migrationChecksums(jdbcTemplate));
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertOperatingFinanceSeeds(jdbcTemplate);
	}

	private void assertHistoricalChecksums(Map<String, Integer> checksums) {
		assertThat(checksums.get("29")).isEqualTo(EXPECTED_V29_CHECKSUM);
		assertThat(checksums.get("30")).isEqualTo(EXPECTED_V30_CHECKSUM);
		assertThat(checksums.get("31")).isEqualTo(EXPECTED_V31_CHECKSUM);
		assertThat(checksums.get("32")).isEqualTo(EXPECTED_V32_CHECKSUM);
		assertThat(checksums.get("33")).isEqualTo(EXPECTED_V33_CHECKSUM);
		assertThat(checksums.get("34")).isEqualTo(EXPECTED_V34_CHECKSUM);
	}

	private void assertOperatingFinanceSeeds(JdbcTemplate jdbcTemplate) {
		assertThat(permissionCount(jdbcTemplate, OPERATING_FINANCE_PERMISSIONS))
			.isEqualTo(OPERATING_FINANCE_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, OPERATING_FINANCE_PERMISSIONS))
			.isEqualTo(OPERATING_FINANCE_PERMISSIONS.size());
		assertThat(routePaths(jdbcTemplate, OPERATING_FINANCE_PERMISSIONS))
			.containsExactlyInAnyOrderElementsOf(OPERATING_FINANCE_ROUTE_PATHS);
		assertThat(routePaths(jdbcTemplate, OPERATING_FINANCE_PERMISSIONS))
			.allSatisfy((path) -> assertThat(path).startsWith("/reports"));
		String snapshotConstraint = allConstraintDefinitions(jdbcTemplate, "biz_period_report_snapshot");
		assertThat(snapshotConstraint).contains(SNAPSHOT_REPORT_CODES.toArray(String[]::new));
		assertThat(snapshotConstraint)
			.doesNotContain("OPERATING_ACCOUNTING_RECONCILIATION")
			.doesNotContain("FINANCIAL_SUMMARY");
	}

	private Map<String, Long> upstreamCounts(JdbcTemplate jdbcTemplate) {
		return Map.of("prj_cost_calculation", tableCount(jdbcTemplate, "prj_cost_calculation"),
				"biz_period_report_snapshot", tableCount(jdbcTemplate, "biz_period_report_snapshot"),
				"gl_voucher", tableCount(jdbcTemplate, "gl_voucher"),
				"gl_ledger_entry", tableCount(jdbcTemplate, "gl_ledger_entry"),
				"fin_close_run", tableCount(jdbcTemplate, "fin_close_run"),
				"fin_bank_statement", tableCount(jdbcTemplate, "fin_bank_statement"),
				"fin_tax_period_summary", tableCount(jdbcTemplate, "fin_tax_period_summary"));
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
				and type = 'ACTION'
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

	private List<String> routePaths(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		return jdbcTemplate.query("""
				select route_path
				from sys_permission
				where code = any (?::text[])
				order by code
				""", (rs, rowNum) -> rs.getString("route_path"),
				(Object) permissionCodes.toArray(String[]::new));
	}

	private Map<String, String> reportPermissionRoutes(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.query("""
				select code, route_path
				from sys_permission
				where code in (
					'report:overview:view', 'report:sales:view', 'report:procurement:view',
					'report:inventory:view', 'report:production:view', 'report:cost:view',
					'report:settlement:view', 'report:exception:view'
				)
				""", (rs, rowNum) -> Map.entry(rs.getString("code"), rs.getString("route_path")))
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private String allConstraintDefinitions(JdbcTemplate jdbcTemplate, String tableName) {
		return String.join("\n", jdbcTemplate.query("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = ?
				""", (rs, rowNum) -> rs.getString(1), tableName));
	}

}
