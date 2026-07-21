package com.qherp.api.system.inventory;

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
class InventoryV22MigrationRegressionTests {

	private static final String LATEST_MIGRATION_VERSION = "36";

	private static final int EXPECTED_V35_CHECKSUM = -82801719;

	private static final int EXPECTED_V36_CHECKSUM = 1030907058;

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
	void 空库迁移到最新版本必须保留v22包含成本层的余额唯一索引() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertBalanceIndexesContainCostLayer(jdbcTemplate);
	}

	@Test
	void v19v20v21升级到最新版本必须保留存量并保留成本层余额身份() {
		for (String target : List.of("19", "20", "21")) {
			Flyway.configure()
				.dataSource(dataSource())
				.locations("classpath:db/migration")
				.cleanDisabled(false)
				.load()
				.clean();
			migrate(target);
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
			long before = count(jdbcTemplate, "inv_stock_balance");

			migrate(null);

			assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
			assertCurrentMigrationChecksums(jdbcTemplate);
			assertThat(count(jdbcTemplate, "inv_stock_balance")).isEqualTo(before);
			assertBalanceIndexesContainCostLayer(jdbcTemplate);
		}
	}

	private void assertBalanceIndexesContainCostLayer(JdbcTemplate jdbcTemplate) {
		for (String indexName : List.of("uk_inv_stock_balance_untracked", "uk_inv_stock_balance_batch",
				"uk_inv_stock_balance_serial")) {
			String definition = jdbcTemplate.queryForObject("""
					select pg_get_indexdef(indexrelid)
					from pg_index
					where indexrelid = ?::regclass
					""", String.class, indexName);
			assertThat(definition).contains("cost_layer_id");
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
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		dataSource.setDriverClassName("org.postgresql.Driver");
		return dataSource;
	}

	private long count(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
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

	private String currentFlywayVersion(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("""
				select version
				from flyway_schema_history
				where success = true
				order by installed_rank desc
				limit 1
				""", String.class);
	}
}
