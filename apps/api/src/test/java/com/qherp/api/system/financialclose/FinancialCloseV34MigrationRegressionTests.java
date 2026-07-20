package com.qherp.api.system.financialclose;

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
class FinancialCloseV34MigrationRegressionTests {

	private static final int EXPECTED_V29_CHECKSUM = 774334682;

	private static final int EXPECTED_V30_CHECKSUM = 2130342893;

	private static final int EXPECTED_V31_CHECKSUM = -2074547591;

	private static final int EXPECTED_V32_CHECKSUM = 249406902;

	private static final int EXPECTED_V33_CHECKSUM = 612501943;

	private static final List<String> FINANCIAL_CLOSE_TABLES = List.of("fin_close_run",
			"fin_close_check_run", "fin_close_check_item", "fin_close_snapshot",
			"fin_close_reopen_request", "fin_close_profit_loss_transfer",
			"fin_close_action_idempotency", "fin_close_audit_event", "fin_bank_account",
			"fin_bank_statement", "fin_bank_statement_line", "fin_bank_reconciliation_run",
			"fin_bank_reconciliation_match", "fin_bank_reconciliation_exception", "fin_tax_profile",
			"fin_tax_rate_rule", "fin_tax_invoice_type", "fin_tax_period_summary",
			"fin_tax_summary_line", "fin_tax_adjustment", "fin_tax_payment_record");

	private static final List<String> FINANCIAL_CLOSE_PERMISSIONS = List.of("financial-close:period:view",
			"financial-close:period:check", "financial-close:period:close", "financial-close:period:reopen",
			"financial-close:profit-loss:view", "financial-close:profit-loss:generate",
			"financial-close:bank-account:view", "financial-close:bank-account:manage",
			"financial-close:bank-reconciliation:view", "financial-close:bank-reconciliation:import",
			"financial-close:bank-reconciliation:match", "financial-close:bank-reconciliation:confirm",
			"financial-close:bank-reconciliation:reopen", "financial-close:tax-profile:view",
			"financial-close:tax-profile:manage", "financial-close:tax-summary:view",
			"financial-close:tax-summary:calculate", "financial-close:tax-summary:confirm",
			"financial-close:tax-summary:generate-voucher", "financial-close:tax-payment:view",
			"financial-close:tax-payment:manage", "financial-close:amount:view",
			"financial-close:source:view", "financial-close:bank-sensitive:view");

	private static final List<String> V34_ACCOUNT_CODES = List.of("4103", "2221.03", "2221.04",
			"2221.05", "2221.06", "6403", "6801");

	private static final Map<String, String> V34_ACCOUNT_NAMES = Map.of("2221.03", "未交增值税",
			"2221.04", "应交城市维护建设税", "2221.05", "应交教育费附加", "2221.06", "应交企业所得税");

	private static final List<String> V34_TAX_RATE_CODES = List.of("VAT_13", "VAT_9", "VAT_6",
			"VAT_0", "SIMPLIFIED_3", "URBAN_7", "URBAN_5", "URBAN_1");

	private static final List<String> V34_TAX_INVOICE_TYPES = List.of("E_DIGITAL_SPECIAL",
			"E_DIGITAL_NORMAL", "PAPER_SPECIAL", "PAPER_NORMAL");

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
	void v33前迁v34只追加财务结账资金税务模型并保持历史校验和与上游事实() {
		migrate("33");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
		Map<String, Integer> v33Checksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(v33Checksums);
		Map<String, Long> upstreamCounts = upstreamCounts(jdbcTemplate);

		migrate(null);

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("34");
		Map<String, Integer> latestChecksums = migrationChecksums(jdbcTemplate);
		assertHistoricalChecksums(latestChecksums);
		assertThat(latestChecksums.entrySet()
			.stream()
			.filter((entry) -> Integer.parseInt(entry.getKey()) <= 33)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))).isEqualTo(v33Checksums);
		assertThat(latestChecksums.get("34")).isNotNull();
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertThat(upstreamCounts(jdbcTemplate)).isEqualTo(upstreamCounts);
		assertFinancialCloseSchema(jdbcTemplate);
	}

	@Test
	void v1到v34空库迁移一次完成并初始化032权限审批科目与不可变约束() {
		migrate(null);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

		assertThat(currentFlywayVersion(jdbcTemplate)).isEqualTo("34");
		assertHistoricalChecksums(migrationChecksums(jdbcTemplate));
		assertThat(failedMigrationCount(jdbcTemplate)).isZero();
		assertFinancialCloseSchema(jdbcTemplate);
	}

	private void assertHistoricalChecksums(Map<String, Integer> checksums) {
		assertThat(checksums.get("29")).isEqualTo(EXPECTED_V29_CHECKSUM);
		assertThat(checksums.get("30")).isEqualTo(EXPECTED_V30_CHECKSUM);
		assertThat(checksums.get("31")).isEqualTo(EXPECTED_V31_CHECKSUM);
		assertThat(checksums.get("32")).isEqualTo(EXPECTED_V32_CHECKSUM);
		assertThat(checksums.get("33")).isEqualTo(EXPECTED_V33_CHECKSUM);
	}

	private void assertFinancialCloseSchema(JdbcTemplate jdbcTemplate) {
		for (String table : FINANCIAL_CLOSE_TABLES) {
			assertThat(tableExists(jdbcTemplate, table)).as(table).isTrue();
		}
		assertThat(permissionCount(jdbcTemplate, FINANCIAL_CLOSE_PERMISSIONS))
			.isEqualTo(FINANCIAL_CLOSE_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(jdbcTemplate, FINANCIAL_CLOSE_PERMISSIONS))
			.isEqualTo(FINANCIAL_CLOSE_PERMISSIONS.size());
		assertThat(approvalDefinitionExists(jdbcTemplate, "FINANCIAL_PERIOD_REOPEN",
				"financial-close:period:reopen")).isTrue();
		assertThat(accountCodeCount(jdbcTemplate, V34_ACCOUNT_CODES)).isEqualTo(V34_ACCOUNT_CODES.size());
		assertThat(accountNames(jdbcTemplate, V34_ACCOUNT_NAMES.keySet().stream().toList()))
			.containsAllEntriesOf(V34_ACCOUNT_NAMES);
		assertThat(taxRateCodeCount(jdbcTemplate, V34_TAX_RATE_CODES)).isEqualTo(V34_TAX_RATE_CODES.size());
		assertThat(taxInvoiceTypeCount(jdbcTemplate, V34_TAX_INVOICE_TYPES))
			.isEqualTo(V34_TAX_INVOICE_TYPES.size());
		assertThat(financialCloseRoutePaths(jdbcTemplate)).allSatisfy((routePath) -> assertThat(routePath)
			.startsWith("/gl"));
		assertThat(allConstraintDefinitions(jdbcTemplate, "gl_voucher"))
			.contains("PROFIT_LOSS_CARRYFORWARD", "TAX_SUMMARY");
		assertThat(allConstraintDefinitions(jdbcTemplate, "fin_close_check_run"))
			.contains("CHECKING", "BLOCKED", "READY", "STALE", "CONSUMED", "FAILED");
		assertThat(allConstraintDefinitions(jdbcTemplate, "fin_close_run")).contains("CLOSED", "REOPENED");
		assertThat(allConstraintDefinitions(jdbcTemplate, "fin_bank_statement_line"))
			.contains("UNMATCHED", "PARTIALLY_MATCHED", "MATCHED", "IGNORED", "CREDIT", "DEBIT");
		assertThat(allConstraintDefinitions(jdbcTemplate, "fin_bank_reconciliation_exception"))
			.contains("BANK_ONLY_CREDIT", "BANK_ONLY_DEBIT", "BOOK_ONLY_DEBIT", "BOOK_ONLY_CREDIT")
			.doesNotContain("'LEDGER_ONLY'")
			.doesNotContain("'AMOUNT_DIFFERENCE'")
			.doesNotContain("'DATE_DIFFERENCE'");
		assertThat(allConstraintDefinitions(jdbcTemplate, "fin_tax_period_summary"))
			.contains("DRAFT", "CALCULATED", "CONFIRMED");
		assertThat(anyIndexContains(jdbcTemplate, "fin_close_check_run", "unique", "period_id", "ready"))
			.isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "fin_close_run", "unique", "period_id", "closed")).isTrue();
		assertThat(anyIndexContains(jdbcTemplate, "fin_bank_account", "unique", "account_fingerprint")).isTrue();
		assertThat(triggerDefinitions(jdbcTemplate, "fin_close_snapshot")).containsIgnoringCase("immutable");
		assertThat(triggerDefinitions(jdbcTemplate, "fin_tax_period_summary")).containsIgnoringCase("immutable");
		assertThat(triggerDefinitions(jdbcTemplate, "fin_bank_reconciliation_run")).containsIgnoringCase("immutable");
	}

	private Map<String, Long> upstreamCounts(JdbcTemplate jdbcTemplate) {
		return Map.of("fin_voucher_draft", tableCount(jdbcTemplate, "fin_voucher_draft"),
				"fin_sales_invoice", tableCount(jdbcTemplate, "fin_sales_invoice"),
				"fin_purchase_invoice", tableCount(jdbcTemplate, "fin_purchase_invoice"),
				"fin_expense", tableCount(jdbcTemplate, "fin_expense"),
				"biz_period_close_run", tableCount(jdbcTemplate, "biz_period_close_run"),
				"gl_voucher", tableCount(jdbcTemplate, "gl_voucher"),
				"gl_ledger_entry", tableCount(jdbcTemplate, "gl_ledger_entry"));
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

	private long accountCodeCount(JdbcTemplate jdbcTemplate, List<String> accountCodes) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from gl_account
				where code = any (?::text[])
				""", Long.class, (Object) accountCodes.toArray(String[]::new));
	}

	private Map<String, String> accountNames(JdbcTemplate jdbcTemplate, List<String> accountCodes) {
		return jdbcTemplate.query("""
				select code, name
				from gl_account
				where code = any (?::text[])
				""", (rs, rowNum) -> Map.entry(rs.getString("code"), rs.getString("name")),
				(Object) accountCodes.toArray(String[]::new))
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private long taxRateCodeCount(JdbcTemplate jdbcTemplate, List<String> rateCodes) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from fin_tax_rate_rule
				where rate_code = any (?::text[])
				""", Long.class, (Object) rateCodes.toArray(String[]::new));
	}

	private long taxInvoiceTypeCount(JdbcTemplate jdbcTemplate, List<String> codes) {
		return jdbcTemplate.queryForObject("""
				select count(*)
				from fin_tax_invoice_type
				where code = any (?::text[])
				""", Long.class, (Object) codes.toArray(String[]::new));
	}

	private List<String> financialCloseRoutePaths(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.query("""
				select route_path
				from sys_permission
				where code = 'financial-close'
				   or code like 'financial-close:%'
				order by code
				""", (rs, rowNum) -> rs.getString("route_path"))
			.stream()
			.filter((routePath) -> routePath != null && !routePath.isBlank())
			.toList();
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
