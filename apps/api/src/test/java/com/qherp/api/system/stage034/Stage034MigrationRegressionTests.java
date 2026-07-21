package com.qherp.api.system.stage034;

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
class Stage034MigrationRegressionTests {

	private static final Map<String, Integer> HISTORICAL_CHECKSUMS = Map.of(
			"29", 774334682,
			"30", 2130342893,
			"31", -2074547591,
			"32", 249406902,
			"33", 612501943,
			"34", -629066235,
			"35", -82801719);

	private static final List<String> DATA_REPAIR_TABLES = List.of(
			"platform_data_repair_request",
			"platform_data_repair_change",
			"platform_data_repair_event",
			"platform_data_repair_check",
			"platform_action_idempotency");

	private static final List<String> IMPORT_AND_BATCH_TABLES = List.of(
			"platform_import_adapter_definition",
			"platform_batch_tool_definition",
			"platform_batch_operation",
			"platform_batch_operation_item",
			"platform_batch_operation_error");

	private static final List<String> DATA_REPAIR_ADAPTERS = List.of(
			"MATERIAL_PROFILE_CORRECTION_V1",
			"CUSTOMER_PROFILE_CORRECTION_V1",
			"SUPPLIER_PROFILE_CORRECTION_V1");

	private static final List<String> HISTORY_IMPORT_ADAPTERS = List.of(
			"CUSTOMER_MASTER_V1",
			"SUPPLIER_MASTER_V1",
			"MATERIAL_MASTER_V1",
			"BOM_DRAFT_V1",
			"SALES_PROJECT_DRAFT_V1");

	private static final List<String> BATCH_TOOLS = List.of(
			"CUSTOMER_STATUS_CHANGE_V1",
			"SUPPLIER_STATUS_CHANGE_V1",
			"MATERIAL_STATUS_CHANGE_V1",
			"FIXED_DOCUMENT_BATCH_PRINT_V1");

	private static final List<String> NEW_PRINT_TEMPLATES = List.of(
			"SALES_ORDER_V1",
			"SALES_SHIPMENT_V1",
			"PROCUREMENT_RECEIPT_V1",
			"INVENTORY_TRANSFER_V1",
			"PRODUCTION_WORK_ORDER_V1",
			"PRODUCTION_MATERIAL_ISSUE_V1",
			"PRODUCTION_COMPLETION_RECEIPT_V1",
			"SALES_INVOICE_V1",
			"PURCHASE_INVOICE_V1",
			"ACCOUNTING_VOUCHER_V1");

	private static final List<String> LEGACY_PRINT_TEMPLATES = List.of(
			"CONTRACT_ACTIVATION_APPROVAL_V1",
			"BOM_ECO_APPLICATION_APPROVAL_V1",
			"PROCUREMENT_ORDER_V1",
			"SALES_QUOTE_V1");

	private static final List<String> PLATFORM_PERMISSIONS = List.of(
			"platform:data-repair:view",
			"platform:data-repair:create",
			"platform:data-repair:update",
			"platform:data-repair:submit",
			"platform:data-repair:approve",
			"platform:data-repair:execute",
			"platform:data-repair:verify",
			"platform:data-repair:cancel",
			"platform:history-import:view",
			"platform:history-import:create",
			"platform:history-import:confirm",
			"platform:history-import:cancel",
			"platform:batch-tool:view",
			"platform:batch-tool:preview",
			"platform:batch-tool:execute",
			"platform:delivery-asset:view");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeEach
	void cleanDatabase() {
		Flyway.configure()
			.dataSource(dataSource())
			.locations("classpath:db/migration")
			.cleanDisabled(false)
			.load()
			.clean();
	}

	@Test
	void v35RepresentativeDatabaseMigratesToV36WithOnlyPlatformGovernanceDefinitions() {
		migrate("35");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		Map<String, Integer> v35Checksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(v35Checksums);
		Map<String, Long> upstreamCounts = upstreamCounts(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("36");
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 35)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v35Checksums);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertThat(upstreamCounts(jdbcTemplate)).isEqualTo(upstreamCounts);
		assertStage034SchemaAndSeeds(jdbcTemplate);
	}

	@Test
	void v1ToV36EmptyDatabaseInitializesFixedGovernanceCatalogsAndAdminPermissions() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("36");
		assertHistoricalChecksums(migrationChecksums(jdbcTemplate));
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertStage034SchemaAndSeeds(jdbcTemplate);
	}

	private void assertStage034SchemaAndSeeds(JdbcTemplate jdbcTemplate) {
		for (String tableName : DATA_REPAIR_TABLES) {
			assertThat(tableExists(jdbcTemplate, tableName)).as(tableName).isTrue();
		}
		for (String tableName : IMPORT_AND_BATCH_TABLES) {
			assertThat(tableExists(jdbcTemplate, tableName)).as(tableName).isTrue();
		}
		assertThat(columnExists(jdbcTemplate, "sys_audit_log", "detail_json")).isTrue();
		assertThat(attachmentConstraint(jdbcTemplate)).contains("DATA_REPAIR_REQUEST");
		assertThat(approvalDefinitionExists(jdbcTemplate, "PLATFORM_DATA_REPAIR_EXECUTION",
				"DATA_REPAIR_REQUEST")).isTrue();
		assertThat(approvalStepExists(jdbcTemplate, "PLATFORM_DATA_REPAIR_EXECUTION",
				"platform:data-repair:approve")).isTrue();
		assertThat(rowCount(jdbcTemplate, "platform_data_repair_adapter_definition", "adapter_code",
				DATA_REPAIR_ADAPTERS)).isEqualTo(DATA_REPAIR_ADAPTERS.size());
		assertThat(rowCount(jdbcTemplate, "platform_import_adapter_definition", "adapter_code",
				HISTORY_IMPORT_ADAPTERS)).isEqualTo(HISTORY_IMPORT_ADAPTERS.size());
		assertThat(rowCount(jdbcTemplate, "platform_batch_tool_definition", "tool_code", BATCH_TOOLS))
			.isEqualTo(BATCH_TOOLS.size());
		assertThat(printTemplateCount(jdbcTemplate, NEW_PRINT_TEMPLATES)).isEqualTo(NEW_PRINT_TEMPLATES.size());
		assertThat(printTemplateCount(jdbcTemplate, LEGACY_PRINT_TEMPLATES)).isEqualTo(LEGACY_PRINT_TEMPLATES.size());
		assertThat(permissionCount(jdbcTemplate, PLATFORM_PERMISSIONS)).isEqualTo(PLATFORM_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, PLATFORM_PERMISSIONS))
			.isEqualTo(PLATFORM_PERMISSIONS.size());
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

	private void assertHistoricalChecksums(Map<String, Integer> checksums) {
		HISTORICAL_CHECKSUMS.forEach((version, checksum) ->
				assertThat(checksums.get(version)).as("V" + version + " checksum").isEqualTo(checksum));
	}

	private long failedMigrationCount(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where success = false",
				Long.class);
	}

	private Map<String, Long> upstreamCounts(JdbcTemplate jdbcTemplate) {
		return Map.of(
				"mst_customer", tableCount(jdbcTemplate, "mst_customer"),
				"mst_supplier", tableCount(jdbcTemplate, "mst_supplier"),
				"mst_material", tableCount(jdbcTemplate, "mst_material"),
				"mfg_bom", tableCount(jdbcTemplate, "mfg_bom"),
				"sal_project", tableCount(jdbcTemplate, "sal_project"),
				"gl_voucher", tableCount(jdbcTemplate, "gl_voucher"),
				"fin_close_run", tableCount(jdbcTemplate, "fin_close_run"));
	}

	private long tableCount(JdbcTemplate jdbcTemplate, String tableName) {
		return jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
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

	private String attachmentConstraint(JdbcTemplate jdbcTemplate) {
		return String.join("\n", jdbcTemplate.query("""
				select pg_get_constraintdef(c.oid)
				from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = 'platform_business_attachment'
				  and c.conname = 'ck_platform_business_attachment_object'
				""", (rs, rowNum) -> rs.getString(1)));
	}

	private boolean approvalDefinitionExists(JdbcTemplate jdbcTemplate, String sceneCode, String objectType) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from platform_approval_definition
				where scene_code = ?
				  and business_object_type = ?
				  and status = 'ENABLED'
				""", Boolean.class, sceneCode, objectType);
	}

	private boolean approvalStepExists(JdbcTemplate jdbcTemplate, String sceneCode, String permissionCode) {
		return jdbcTemplate.queryForObject("""
				select count(*) > 0
				from platform_approval_definition_step s
				join platform_approval_definition d on d.id = s.definition_id
				where d.scene_code = ?
				  and s.candidate_permission_code = ?
				""", Boolean.class, sceneCode, permissionCode);
	}

	private long rowCount(JdbcTemplate jdbcTemplate, String tableName, String codeColumn, List<String> codes) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from %s
				where %s = any (?::text[])
				  and status = 'ENABLED'
				""".formatted(tableName, codeColumn), Long.class, (Object) codes.toArray(String[]::new));
	}

	private long printTemplateCount(JdbcTemplate jdbcTemplate, List<String> templateCodes) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from platform_print_template
				where template_code = any (?::text[])
				  and status = 'ENABLED'
				""", Long.class, (Object) templateCodes.toArray(String[]::new));
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

}
