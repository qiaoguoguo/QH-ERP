package com.qherp.api.system.projectcost;

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
class ProjectCostV31MigrationRegressionTests {

	private static final String LATEST_MIGRATION_VERSION = "36";

	private static final int EXPECTED_V35_CHECKSUM = -82801719;

	private static final int EXPECTED_V36_CHECKSUM = 1030907058;

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private static final List<String> PROJECT_COST_TABLES = List.of("prj_cost_calculation",
			"prj_cost_source_line", "prj_cost_entry", "prj_cost_entry_line", "prj_cost_adjustment",
			"prj_cost_adjustment_line", "prj_cost_variance", "prj_cost_action_idempotency");

	private static final List<String> PROJECT_COST_PERMISSIONS = List.of("cost:project-cost:view",
			"cost:project-cost:source-view", "cost:project-cost:amount-view",
			"cost:project-cost:calculate", "cost:project-cost:confirm", "cost:project-cost:cancel",
			"cost:project-cost-adjustment:view", "cost:project-cost-adjustment:create",
			"cost:project-cost-adjustment:update", "cost:project-cost-adjustment:submit",
			"cost:project-cost-adjustment:cancel", "cost:project-cost-variance:view");

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
	void v30前迁v31只追加项目成本核算模型并保持既有迁移校验和() {
		migrate("30");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("30");
		Map<String, Integer> v30Checksums = migrationChecksums(jdbcTemplate);
		assertThat(v30Checksums.get("29")).isEqualTo(774334682);
		assertThat(v30Checksums.get("30")).isEqualTo(2130342893);
		Map<String, Long> upstreamCounts = upstreamCounts(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertThat(migrationChecksums(jdbcTemplate).entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 30)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v30Checksums);
		assertThat(upstreamCounts(jdbcTemplate)).isEqualTo(upstreamCounts);
		for (String table : PROJECT_COST_TABLES) {
			assertThat(tableExists(jdbcTemplate, table)).as(table).isTrue();
		}
		assertThat(permissionCount(jdbcTemplate, PROJECT_COST_PERMISSIONS)).isEqualTo(PROJECT_COST_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, PROJECT_COST_PERMISSIONS))
			.isEqualTo(PROJECT_COST_PERMISSIONS.size());
		assertThat(approvalDefinitionExists(jdbcTemplate, "PROJECT_COST_ADJUSTMENT_CONFIRM")).isTrue();
		assertThat(indexExists(jdbcTemplate, "uk_prj_cost_calculation_active_project")).isTrue();
		assertThat(indexExists(jdbcTemplate, "uk_prj_cost_source_line_source")).isTrue();
		assertThat(indexExists(jdbcTemplate, "uk_prj_cost_adjustment_idempotency")).isTrue();
		assertThat(constraint(jdbcTemplate, "prj_cost_entry", "ck_prj_cost_entry_type"))
			.contains("SOURCE_TO_WIP", "WIP_TO_FINISHED", "FINISHED_TO_DELIVERED", "PROJECT_DIRECT",
					"PROJECT_ADJUSTMENT", "COST_VARIANCE");
		assertThat(constraint(jdbcTemplate, "prj_cost_adjustment", "ck_prj_cost_adjustment_type"))
			.contains("PROJECT_ADJUSTMENT", "PUBLIC_EXPENSE_ALLOCATION", "VARIANCE_SETTLEMENT")
			.doesNotContain("MANUAL_ADJUSTMENT");
		assertThat(constraint(jdbcTemplate, "prj_cost_adjustment_line", "ck_prj_cost_adjustment_line_category"))
			.contains("ADJUSTMENT");
		assertThat(constraint(jdbcTemplate, "prj_cost_source_line", "ck_prj_cost_source_line_status"))
			.contains("ACTUAL", "PROVISIONAL", "UNPRICED", "ADJUSTED", "RESTRICTED", "EXCLUDED");
		assertThat(constraint(jdbcTemplate, "prj_cost_variance", "ck_prj_cost_variance_severity"))
			.contains("INFO", "WARNING", "BLOCKING")
			.doesNotContain("ERROR");
		assertThat(constraint(jdbcTemplate, "prj_cost_variance", "ck_prj_cost_variance_status"))
			.contains("OPEN", "RESOLVED", "SUPERSEDED")
			.doesNotContain("CLOSED");
	}

	@Test
	void v1到v31空库迁移一次性完成并初始化项目成本权限和约束() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		Map<String, Integer> checksums = migrationChecksums(jdbcTemplate);
		assertThat(checksums.get("29")).isEqualTo(774334682);
		assertThat(checksums.get("30")).isEqualTo(2130342893);
		assertThat(checksums.get("31")).isEqualTo(-2074547591);
		assertThat(checksums.get("32")).isEqualTo(249406902);
		assertThat(checksums.get("33")).isEqualTo(612501943);
		assertThat(checksums.get("34")).isEqualTo(-629066235);
		assertThat(checksums.get("35")).isEqualTo(EXPECTED_V35_CHECKSUM);
		assertThat(checksums.get("36")).isEqualTo(EXPECTED_V36_CHECKSUM);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		for (String table : PROJECT_COST_TABLES) {
			assertThat(tableExists(jdbcTemplate, table)).as(table).isTrue();
		}
		assertThat(permissionCount(jdbcTemplate, PROJECT_COST_PERMISSIONS)).isEqualTo(PROJECT_COST_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, PROJECT_COST_PERMISSIONS))
			.isEqualTo(PROJECT_COST_PERMISSIONS.size());
		assertThat(constraint(jdbcTemplate, "prj_cost_variance", "ck_prj_cost_variance_severity"))
			.contains("BLOCKING");
		assertThat(constraint(jdbcTemplate, "prj_cost_variance", "ck_prj_cost_variance_status"))
			.contains("RESOLVED", "SUPERSEDED");
	}

	private Map<String, Long> upstreamCounts(JdbcTemplate jdbcTemplate) {
		return Map.of("inv_value_movement", tableCount(jdbcTemplate, "inv_value_movement"), "mfg_cost_record",
				tableCount(jdbcTemplate, "mfg_cost_record"), "fin_purchase_invoice",
				tableCount(jdbcTemplate, "fin_purchase_invoice"), "fin_expense",
				tableCount(jdbcTemplate, "fin_expense"));
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

	private void assertCurrentMigrationChecksums(JdbcTemplate jdbcTemplate) {
		assertThat(migrationChecksum(jdbcTemplate, "29")).isEqualTo(774334682);
		assertThat(migrationChecksum(jdbcTemplate, "30")).isEqualTo(2130342893);
		assertThat(migrationChecksum(jdbcTemplate, "31")).isEqualTo(-2074547591);
		assertThat(migrationChecksum(jdbcTemplate, "32")).isEqualTo(249406902);
		assertThat(migrationChecksum(jdbcTemplate, "33")).isEqualTo(612501943);
		assertThat(migrationChecksum(jdbcTemplate, "34")).isEqualTo(-629066235);
		assertThat(migrationChecksum(jdbcTemplate, "35")).isEqualTo(EXPECTED_V35_CHECKSUM);
		assertThat(migrationChecksum(jdbcTemplate, "36")).isEqualTo(EXPECTED_V36_CHECKSUM);
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

	private boolean approvalDefinitionExists(JdbcTemplate jdbcTemplate, String sceneCode) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_definition d
				join platform_approval_definition_step s on s.definition_id = d.id
				where d.scene_code = ?
				and d.status = 'ENABLED'
				and s.candidate_permission_code = 'cost:project-cost-adjustment:submit'
				""", Integer.class, sceneCode);
		return count != null && count > 0;
	}

	private boolean indexExists(JdbcTemplate jdbcTemplate, String indexName) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from pg_indexes
				where schemaname = 'public'
				and indexname = ?
				""", Integer.class, indexName);
		return count != null && count > 0;
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

}
