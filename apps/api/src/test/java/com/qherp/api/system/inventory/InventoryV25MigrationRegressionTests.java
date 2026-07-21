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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class InventoryV25MigrationRegressionTests {

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
	void v24升级到v25必须扩展价值流水类型长度() {
		migrate("24");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("24");
		assertThat(columnLength(jdbcTemplate, "inv_value_movement", "movement_type")).isEqualTo(32);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertMovementTypeColumnsAllowLongEnums(jdbcTemplate);
	}

	@Test
	void 空库迁移到v25必须允许最长库存移动枚举写入价值流水() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo(LATEST_MIGRATION_VERSION);
		assertCurrentMigrationChecksums(jdbcTemplate);
		assertMovementTypeColumnsAllowLongEnums(jdbcTemplate);
	}

	private void assertMovementTypeColumnsAllowLongEnums(JdbcTemplate jdbcTemplate) {
		int longestMovementTypeLength = longestMovementTypeLength();
		assertThat(columnLength(jdbcTemplate, "inv_stock_movement", "movement_type"))
			.isGreaterThanOrEqualTo(longestMovementTypeLength);
		assertThat(columnLength(jdbcTemplate, "inv_value_movement", "movement_type"))
			.isGreaterThanOrEqualTo(longestMovementTypeLength);
	}

	private int longestMovementTypeLength() {
		int max = 0;
		for (InventoryMovementType type : InventoryMovementType.values()) {
			max = Math.max(max, type.name().length());
		}
		return max;
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

	private Integer columnLength(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		return jdbcTemplate.queryForObject("""
				select character_maximum_length
				from information_schema.columns
				where table_schema = 'public'
				  and table_name = ?
				  and column_name = ?
				""", Integer.class, tableName, columnName);
	}
}
