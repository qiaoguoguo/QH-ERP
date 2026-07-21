package com.qherp.api.system.gl;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GeneralLedgerV33MigrationRegressionTests {

	private static final int EXPECTED_V29_CHECKSUM = 774334682;

	private static final int EXPECTED_V30_CHECKSUM = 2130342893;

	private static final int EXPECTED_V31_CHECKSUM = -2074547591;

	private static final int EXPECTED_V32_CHECKSUM = 249406902;

	private static final int EXPECTED_V33_CHECKSUM = 612501943;

	private static final int EXPECTED_V34_CHECKSUM = -629066235;

	private static final List<String> GL_TABLES = List.of("gl_ledger", "gl_accounting_period", "gl_account",
			"gl_aux_dimension", "gl_aux_item", "gl_account_aux_requirement", "gl_posting_rule",
			"gl_posting_rule_line", "gl_posting_rule_line_aux_map", "gl_voucher", "gl_voucher_line",
			"gl_voucher_line_auxiliary", "gl_voucher_source_claim", "gl_voucher_number_sequence",
			"gl_ledger_entry", "gl_account_period_total", "gl_voucher_reversal_link", "gl_action_idempotency",
			"gl_audit_event");

	private static final List<String> GL_PERMISSIONS = List.of("gl:account:view", "gl:account:create",
			"gl:account:update", "gl:account:disable", "gl:auxiliary:view", "gl:auxiliary:manage",
			"gl:period:view", "gl:period:initialize", "gl:period:create", "gl:rule:view", "gl:rule:manage",
			"gl:voucher:view", "gl:voucher:create", "gl:voucher:update", "gl:voucher:convert",
			"gl:voucher:submit", "gl:voucher:cancel", "gl:voucher:reverse", "gl:voucher:approve-post",
			"gl:ledger:view", "gl:balance:view", "gl:amount:view", "gl:source:view");

	private static final List<String> ACCOUNT_CODES = List.of("1001", "1002", "1122", "1123", "1401",
			"1403", "1405", "1408", "1601", "2202", "2203", "2221", "2221.01", "2221.02", "4001",
			"5001", "5101", "6001", "6051", "6401", "6601", "6602", "6603", "6301", "6711");

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
	void v32前迁v33只追加总账模型不回填上游会计事实且保持既有校验和() {
		migrate("32");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("32");
		Map<String, Integer> v32Checksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(v32Checksums);
		long readyDraftId = insertReadyVoucherDraft(jdbcTemplate);
		Map<String, Object> draftBefore = voucherDraftRow(jdbcTemplate, readyDraftId);
		Map<String, Long> upstreamCounts = upstreamCounts(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("35");
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.get("33")).isEqualTo(EXPECTED_V33_CHECKSUM);
		assertThat(latestChecksums.get("34")).isEqualTo(EXPECTED_V34_CHECKSUM);
		assertThat(latestChecksums.entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 32)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v32Checksums);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertThat(upstreamCounts(jdbcTemplate)).isEqualTo(upstreamCounts);
		assertThat(voucherDraftRow(jdbcTemplate, readyDraftId)).isEqualTo(draftBefore);
		assertThat(tableCount(jdbcTemplate, "gl_voucher")).isZero();
		assertThat(tableCount(jdbcTemplate, "gl_ledger_entry")).isZero();
		assertThat(tableCount(jdbcTemplate, "gl_voucher_source_claim")).isZero();
		assertGeneralLedgerSchema(jdbcTemplate);
	}

	@Test
	void v1到v33空库迁移一次性完成并初始化总账权限模板约束和审批场景() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("35");
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.get("33")).isEqualTo(EXPECTED_V33_CHECKSUM);
		assertThat(latestChecksums.get("34")).isEqualTo(EXPECTED_V34_CHECKSUM);
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertGeneralLedgerSchema(jdbcTemplate);
	}

	private void assertHistoricalChecksums(Map<String, Integer> checksums) {
		assertThat(checksums.get("29")).isEqualTo(EXPECTED_V29_CHECKSUM);
		assertThat(checksums.get("30")).isEqualTo(EXPECTED_V30_CHECKSUM);
		assertThat(checksums.get("31")).isEqualTo(EXPECTED_V31_CHECKSUM);
		assertThat(checksums.get("32")).isEqualTo(EXPECTED_V32_CHECKSUM);
	}

	private void assertGeneralLedgerSchema(JdbcTemplate jdbcTemplate) {
		for (String table : GL_TABLES) {
			assertThat(tableExists(jdbcTemplate, table)).as(table).isTrue();
		}
		assertThat(permissionCount(jdbcTemplate, GL_PERMISSIONS)).isEqualTo(GL_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, GL_PERMISSIONS)).isEqualTo(GL_PERMISSIONS.size());
		assertThat(permissionRoutePath(jdbcTemplate, "gl")).isEqualTo("/gl");
		assertThat(approvalDefinitionExists(jdbcTemplate, "GL_VOUCHER_POST", "gl:voucher:approve-post")).isTrue();

		assertThat(tableCount(jdbcTemplate, "gl_ledger")).isOne();
		assertThat(jdbcTemplate.queryForObject("""
				select code
				from gl_ledger
				""", String.class)).isEqualTo("MAIN");
		assertThat(jdbcTemplate.queryForObject("""
				select currency
				from gl_ledger
				""", String.class)).isEqualTo("CNY");
		assertThat(accountCodeCount(jdbcTemplate, ACCOUNT_CODES)).isEqualTo(ACCOUNT_CODES.size());
		assertThat(auxDimensionCount(jdbcTemplate, List.of("CUSTOMER", "SUPPLIER", "PROJECT"))).isEqualTo(3);

		assertColumns(jdbcTemplate, "gl_ledger", List.of("code", "currency", "initialized", "start_period_id",
				"start_year_month", "created_by", "created_at", "updated_by", "updated_at", "version"));
		assertColumns(jdbcTemplate, "gl_accounting_period", List.of("ledger_id", "period_code", "start_date",
				"end_date", "status", "created_by", "created_at", "updated_by", "updated_at", "version"));
		assertColumns(jdbcTemplate, "gl_account", List.of("ledger_id", "code", "name", "category",
				"balance_direction", "parent_id", "level_no", "postable", "enabled", "template_source",
				"version"));
		assertColumns(jdbcTemplate, "gl_aux_dimension", List.of("code", "name", "dimension_type",
				"object_source", "enabled", "sort_order", "version"));
		assertColumns(jdbcTemplate, "gl_account_aux_requirement",
				List.of("account_id", "dimension_id", "requirement"));
		assertColumns(jdbcTemplate, "gl_posting_rule", List.of("source_type", "source_variant", "rule_version",
				"status", "effective_from", "effective_to", "version"));
		assertColumns(jdbcTemplate, "gl_posting_rule_line", List.of("rule_id", "line_no",
				"normalized_fact_code", "direction", "account_id", "summary_template"));
		assertColumns(jdbcTemplate, "gl_posting_rule_line_aux_map",
				List.of("rule_line_id", "dimension_id", "mapping_type", "fixed_aux_item_id"));
		assertColumns(jdbcTemplate, "gl_voucher", List.of("ledger_id", "accounting_period_id", "draft_no",
				"voucher_type", "source_type", "source_id", "voucher_word", "voucher_no", "voucher_number",
				"voucher_date", "status", "summary", "currency", "debit_total", "credit_total",
				"approval_instance_id", "version"));
		assertColumns(jdbcTemplate, "gl_voucher_line", List.of("voucher_id", "line_no", "summary",
				"account_id", "account_code", "account_name", "debit_amount", "credit_amount",
				"normalized_fact_code", "source_type", "source_id", "source_route"));
		assertColumns(jdbcTemplate, "gl_voucher_line_auxiliary", List.of("voucher_line_id", "dimension_id",
				"dimension_code", "object_type", "object_id", "object_code", "object_name"));
		assertColumns(jdbcTemplate, "gl_voucher_source_claim", List.of("voucher_id", "source_type", "source_id",
				"status", "source_fingerprint", "source_version"));
		assertColumns(jdbcTemplate, "gl_voucher_number_sequence",
				List.of("ledger_id", "period_id", "voucher_word", "last_number"));
		assertColumns(jdbcTemplate, "gl_ledger_entry", List.of("ledger_id", "period_id", "voucher_id",
				"voucher_line_id", "account_id", "account_code", "voucher_no", "voucher_date",
				"debit_amount", "credit_amount"));
		assertColumns(jdbcTemplate, "gl_account_period_total", List.of("ledger_id", "period_id",
				"account_id", "opening_debit", "opening_credit", "period_debit", "period_credit",
				"ending_debit", "ending_credit"));
		assertColumns(jdbcTemplate, "gl_voucher_reversal_link",
				List.of("original_voucher_id", "reversal_voucher_id", "status", "reason"));
		assertColumns(jdbcTemplate, "gl_action_idempotency", List.of("operator_user_id", "action",
				"resource_type", "resource_id", "idempotency_key", "request_fingerprint"));
		assertColumns(jdbcTemplate, "gl_audit_event",
				List.of("action", "result", "resource_type", "resource_id", "error_code", "created_at"));

		assertThat(allConstraintDefinitions(jdbcTemplate, "gl_account")).contains("ASSET", "LIABILITY", "COMMON",
				"EQUITY", "COST", "PROFIT_LOSS", "DEBIT", "CREDIT");
		assertThat(allConstraintDefinitions(jdbcTemplate, "gl_account_aux_requirement")).contains("REQUIRED",
				"OPTIONAL");
		assertThat(allConstraintDefinitions(jdbcTemplate, "gl_voucher")).contains("GENERAL", "OPENING", "DRAFT",
				"SUBMITTED", "POSTED", "CANCELLED")
			.doesNotContain("APPROVED");
		assertThat(allConstraintDefinitions(jdbcTemplate, "gl_voucher_line")).contains("debit_amount",
				"credit_amount");
		assertThat(allConstraintDefinitions(jdbcTemplate, "gl_voucher_source_claim")).contains("RESERVED",
				"POSTED", "RELEASED");
		assertThat(allConstraintDefinitions(jdbcTemplate, "gl_posting_rule")).contains("DRAFT", "ACTIVE",
				"SUPERSEDED", "DISABLED");

		assertThat(anyIndexContains(jdbcTemplate, "gl_ledger", "unique", "code")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "gl_accounting_period", "unique", "ledger_id",
				"period_code")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "gl_account", "unique", "ledger_id", "code")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "gl_posting_rule", "unique", "source_type",
				"source_variant", "active")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "gl_voucher", "unique", "ledger_id", "accounting_period_id",
				"voucher_word", "voucher_number")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "gl_voucher_source_claim", "unique", "source_type",
				"source_id", "reserved", "posted")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "gl_voucher_reversal_link", "unique", "original_voucher_id",
				"active")).isTrue();

		assertThat(triggerDefinitions(jdbcTemplate, "gl_voucher")).containsIgnoringCase("posted");
		assertThat(triggerDefinitions(jdbcTemplate, "gl_voucher_line")).containsIgnoringCase("posted");
		assertThat(triggerDefinitions(jdbcTemplate, "gl_ledger_entry")).containsIgnoringCase("immutable");
	}

	private long insertReadyVoucherDraft(JdbcTemplate jdbcTemplate) {
		long customerId = jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values ('031_V32_CUS', '031 V32 客户', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class);
		long sourceId = jdbcTemplate.queryForObject("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, source_type, source_id, source_no, invoice_date,
					due_date, external_invoice_no, invoice_type, currency, tax_excluded_amount, tax_amount,
					tax_included_amount, status, party_snapshot, source_snapshot, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at, version
				)
				values ('031-V32-SI', ?, 'PUBLIC', 'SALES_SHIPMENT', 31001, '031-V32-SH',
					date '2026-07-15', date '2026-08-15', '031-V32-EXT', 'SPECIAL_VAT', 'CNY',
					100.00, 13.00, 113.00, 'CONFIRMED', '{}'::jsonb, '{}'::jsonb, '031 V32 前迁来源',
					'test', now(), 'test', now(), 'test', now(), 7)
				returning id
				""", Long.class, customerId);
		long draftId = jdbcTemplate.queryForObject("""
				insert into fin_voucher_draft (
					draft_no, source_type, source_id, status, business_date, summary, party_type, party_id,
					party_name, ownership_type, debit_amount, credit_amount, generation_version, idempotency_key,
					request_fingerprint, created_by, created_at, updated_by, updated_at, ready_by, ready_at, version
				)
				values ('031-V32-VD', 'SALES_INVOICE', ?, 'READY', date '2026-07-15',
					'031 V32 READY 草稿不得被 V33 回填', 'CUSTOMER', ?, '031 V32 客户', 'PUBLIC',
					113.00, 113.00, 7, '031-v32-ready', '031-v32-fingerprint',
					'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, sourceId, customerId);
		jdbcTemplate.update("""
				insert into fin_voucher_draft_line (
					draft_id, line_no, direction, business_category, amount, source_type, source_id, created_at
				)
				values (?, 1, 'DEBIT', 'RECEIVABLE', 113.00, 'SALES_INVOICE', ?, now()),
				       (?, 2, 'CREDIT', 'REVENUE', 113.00, 'SALES_INVOICE', ?, now())
				""", draftId, sourceId, draftId, sourceId);
		return draftId;
	}

	private Map<String, Object> voucherDraftRow(JdbcTemplate jdbcTemplate, long id) {
		return jdbcTemplate.queryForMap("""
				select source_type, source_id, status, business_date, debit_amount, credit_amount,
				       generation_version, formal_voucher_no, posting_status, version
				from fin_voucher_draft
				where id = ?
				""", id);
	}

	private Map<String, Long> upstreamCounts(JdbcTemplate jdbcTemplate) {
		return Map.of("fin_voucher_draft", tableCount(jdbcTemplate, "fin_voucher_draft"),
				"fin_sales_invoice", tableCount(jdbcTemplate, "fin_sales_invoice"),
				"fin_purchase_invoice", tableCount(jdbcTemplate, "fin_purchase_invoice"),
				"fin_expense", tableCount(jdbcTemplate, "fin_expense"),
				"prj_cost_calculation", tableCount(jdbcTemplate, "prj_cost_calculation"),
				"biz_period_close_run", tableCount(jdbcTemplate, "biz_period_close_run"));
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

	private void assertColumns(JdbcTemplate jdbcTemplate, String tableName, List<String> columnNames) {
		for (String columnName : columnNames) {
			assertThat(columnExists(jdbcTemplate, tableName, columnName)).as(tableName + "." + columnName).isTrue();
		}
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

	private long permissionCount(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from sys_permission
				where code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private long systemAdminPermissionCount(JdbcTemplate jdbcTemplate, List<String> permissionCodes) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role_permission rp
				join sys_role r on r.id = rp.role_id
				join sys_permission p on p.id = rp.permission_id
				where r.code = 'SYSTEM_ADMIN'
				and p.code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private String permissionRoutePath(JdbcTemplate jdbcTemplate, String code) {
		return jdbcTemplate.queryForObject("""
				select route_path
				from sys_permission
				where code = ?
				""", String.class, code);
	}

	private long accountCodeCount(JdbcTemplate jdbcTemplate, List<String> accountCodes) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from gl_account
				where code = any (?::text[])
				""", Long.class, (Object) accountCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private long auxDimensionCount(JdbcTemplate jdbcTemplate, List<String> dimensionCodes) {
		Long count = jdbcTemplate.queryForObject("""
				select count(*)
				from gl_aux_dimension
				where code = any (?::text[])
				""", Long.class, (Object) dimensionCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private boolean approvalDefinitionExists(JdbcTemplate jdbcTemplate, String sceneCode, String permissionCode) {
		Boolean exists = jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from platform_approval_definition d
					join platform_approval_definition_step s on s.definition_id = d.id
					where d.scene_code = ?
					and d.status = 'ENABLED'
					and s.candidate_permission_code = ?
				)
				""", Boolean.class, sceneCode, permissionCode);
		return Boolean.TRUE.equals(exists);
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

	private String triggerDefinitions(JdbcTemplate jdbcTemplate, String tableName) {
		return String.join("\n", jdbcTemplate.query("""
				select lower(pg_get_triggerdef(t.oid))
				from pg_trigger t
				join pg_class c on c.oid = t.tgrelid
				where c.relname = ?
				and not t.tgisinternal
				""", (rs, rowNum) -> rs.getString(1), tableName));
	}

}
