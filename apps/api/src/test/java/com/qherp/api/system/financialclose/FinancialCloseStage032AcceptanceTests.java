package com.qherp.api.system.financialclose;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=stage032-financial-close")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FinancialCloseStage032AcceptanceTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final List<String> FINANCIAL_CLOSE_TABLES = List.of("fin_close_run",
			"fin_close_check_run", "fin_close_check_item", "fin_close_snapshot",
			"fin_close_reopen_request", "fin_close_profit_loss_transfer",
			"fin_close_action_idempotency", "fin_close_audit_event", "fin_bank_account",
			"fin_bank_statement", "fin_bank_statement_line", "fin_bank_reconciliation_run",
			"fin_bank_reconciliation_match", "fin_bank_reconciliation_exception", "fin_tax_profile",
			"fin_tax_rate_rule", "fin_tax_invoice_type", "fin_tax_period_summary",
			"fin_tax_summary_line", "fin_tax_adjustment", "fin_tax_payment_record");

	private static final List<String> FINANCIAL_CLOSE_PERMISSIONS = List.of(
			"financial-close:period:view", "financial-close:period:check",
			"financial-close:period:close", "financial-close:period:reopen",
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

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	@Order(1)
	void v34迁移必须创建032表权限审批科目和验证器门禁() {
		requireV34Schema();
		assertThat(permissionCodes()).containsExactlyInAnyOrderElementsOf(FINANCIAL_CLOSE_PERMISSIONS);
		assertThat(systemAdminFinancialClosePermissionCount()).isGreaterThanOrEqualTo(FINANCIAL_CLOSE_PERMISSIONS.size());
		assertThat(financialReopenApprovalDefinitionSummary()).isEqualTo("definitions=1;steps=1");
		assertThat(accountCodes()).contains("4103", "2221.03", "2221.04", "2221.05", "2221.06",
				"6403", "6801");
		assertThat(immutableTriggerCount()).isGreaterThanOrEqualTo(4L);
		assertThat(latestSuccessfulFlywayVersion()).isEqualTo("34");
		assertThat(historicalChecksum("29")).isEqualTo(774334682);
		assertThat(historicalChecksum("30")).isEqualTo(2130342893);
		assertThat(historicalChecksum("31")).isEqualTo(-2074547591);
		assertThat(historicalChecksum("32")).isEqualTo(249406902);
		assertThat(historicalChecksum("33")).isEqualTo(612501943);
		assertThat(historicalChecksum("34")).isEqualTo(-177563574);
	}

	@Test
	@Order(2)
	void 业务月结未关闭时财务检查必须阻断且关闭请求不能产生半状态() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureOpenAccountingPeriod(admin, "2096-01");
		Map<String, String> upstreamBefore = upstreamFactsSummary();

		JsonNode check = data(post(admin, "/api/admin/financial-closes/periods/" + periodId + "/checks",
				Map.of("idempotencyKey", "032-check-business-open-" + periodId)));
		assertThat(check.get("status").asText()).isEqualTo("BLOCKED");
		assertThat(recursiveValues(check, "checkCode")).contains("BUSINESS_PERIOD_CLOSED",
				"BANK_RECONCILIATIONS_CONFIRMED", "TAX_SUMMARIES_CONFIRMED",
				"PROFIT_LOSS_TRANSFER_POSTED");
		assertThat(check.get("sourceFingerprint").asText()).isNotBlank();

		assertError(post(admin, "/api/admin/financial-closes/check-runs/" + check.get("id").longValue()
				+ "/close", Map.of("version", check.get("version").longValue(), "idempotencyKey",
						"032-close-blocked-" + periodId, "reason", "业务月结未关闭不得财务结账")),
				HttpStatus.CONFLICT, "FIN_CLOSE_NOT_READY");
		assertThat(accountingPeriodStatus(periodId)).isEqualTo("OPEN");
		assertThat(currentClosedRunCount(periodId)).isZero();
		assertThat(upstreamFactsSummary()).isEqualTo(upstreamBefore);
	}

	@Test
	@Order(3)
	void 并发财务关闭只能一个成功且关闭后gl写入和反结账自审都必须失败关闭() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = seedReadyFinancialClosePeriod(admin, "2096-02");
		JsonNode check = data(post(admin, "/api/admin/financial-closes/periods/" + periodId + "/checks",
				Map.of("idempotencyKey", "032-check-ready-" + periodId)));
		assertThat(check.get("status").asText()).isEqualTo("READY");

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<ResponseEntity<String>> first = executor.submit(() -> {
				await(start);
				return post(admin, "/api/admin/financial-closes/check-runs/" + check.get("id").longValue()
						+ "/close", closePayload(check, "032-close-race-a-" + periodId));
			});
			Future<ResponseEntity<String>> second = executor.submit(() -> {
				await(start);
				return post(admin, "/api/admin/financial-closes/check-runs/" + check.get("id").longValue()
						+ "/close", closePayload(check, "032-close-race-b-" + periodId));
			});
			start.countDown();
			List<ResponseEntity<String>> responses = List.of(first.get(), second.get());
			assertThat(responses.stream().filter((response) -> response.getStatusCode() == HttpStatus.OK).count())
				.isOne();
			assertThat(responses.stream().filter((response) -> response.getStatusCode() == HttpStatus.CONFLICT).count())
				.isOne();
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(accountingPeriodStatus(periodId)).isEqualTo("CLOSED");
		assertThat(currentClosedRunCount(periodId)).isOne();

		assertError(post(admin, "/api/admin/gl/vouchers",
				voucherPayload(periodId, LocalDate.of(2096, 2, 10), "032 已关闭期间不得制证", "1.00")),
				HttpStatus.CONFLICT, "FIN_CLOSE_PERIOD_CLOSED");
		ClosedRun closedRun = currentClosedRun(periodId);
		JsonNode reopen = data(post(admin,
				"/api/admin/financial-closes/close-runs/" + closedRun.id()
						+ "/reopen-requests",
				Map.of("version", closedRun.version(), "idempotencyKey",
						"032-reopen-self-" + closedRun.id(), "reason", "验证反结账双人审批")));
		assertThat(reopen.get("status").asText()).isEqualTo("SUBMITTED");
		long taskId = approvalTaskIdForReopenRequest(reopen.get("id").longValue());
		assertError(post(admin, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion(taskId), "idempotencyKey", "032-self-approve-" + taskId,
						"comment", "申请人不得自审")),
				HttpStatus.FORBIDDEN, "APPROVAL_SELF_ACTION_FORBIDDEN");
	}

	@Test
	@Order(4)
	void 损益结转银行对账和税务汇总必须只创建031草稿并保留来源边界() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureOpenAccountingPeriod(admin, "2096-03");
		Map<String, String> upstreamBefore = upstreamFactsSummary();

		JsonNode transfer = data(post(admin,
				"/api/admin/financial-closes/periods/" + periodId + "/profit-loss-transfers",
				Map.of("idempotencyKey", "032-profit-loss-" + periodId)));
		assertThat(transfer.get("status").asText()).isIn("DRAFT", "ZERO_BALANCE");
		if (transfer.hasNonNull("voucherId")) {
			assertThat(voucherSourceType(transfer.get("voucherId").longValue()))
				.isEqualTo("PROFIT_LOSS_CARRYFORWARD");
			assertThat(ledgerEntryCount(transfer.get("voucherId").longValue())).isZero();
		}

		JsonNode bank = data(post(admin, "/api/admin/bank-accounts",
				Map.of("accountName", "032 对账基本户", "accountType", "BASIC", "bankName", "032 测试银行",
						"currency", "CNY", "glAccountId", accountId("1002"), "openedOn", "2096-03-01",
						"accountNo", "6222020960300001234", "idempotencyKey", "032-bank-account")));
		assertThat(bank.get("accountMasked").asText()).doesNotContain("6222020960300001234");
		assertThat(bankRawAccountLeaks()).isZero();

		JsonNode tax = data(post(admin, "/api/admin/tax-summaries",
				Map.of("periodCode", "2096-03", "taxType", "VAT", "idempotencyKey",
						"032-tax-summary-" + periodId)));
		assertThat(recursiveValues(tax, "disclaimer"))
			.contains("本结果为 ERP 基础汇总或估算，不是正式纳税申报结果，不代替税务专业判断。");
		assertThat(tax.toString()).doesNotContain("报送成功", "已申报", "税控同步");
		assertThat(upstreamFactsSummary()).isEqualTo(upstreamBefore);
	}

	@Test
	@Order(5)
	void 金额来源和银行敏感权限必须后端脱敏且动态对象规则不能写死18个对象() throws Exception {
		requireV34Schema();
		AuthenticatedSession limited = createUserAndLogin("032-limited-", "032_LIMITED_",
				List.of("financial-close:period:view", "financial-close:bank-account:view",
						"financial-close:bank-reconciliation:view", "financial-close:tax-summary:view"));
		JsonNode periods = data(get(limited, "/api/admin/financial-closes/periods?page=1&pageSize=10"));
		assertRecursiveNull(periods, List.of("amount", "debitAmount", "creditAmount", "sourceId", "sourceNo",
				"sourceFingerprint", "accountNo"));
		JsonNode bankAccounts = data(get(limited, "/api/admin/bank-accounts?page=1&pageSize=10"));
		assertThat(bankAccounts.toString()).doesNotContain("6222020960300001234");
		JsonNode taxSummaries = data(get(limited, "/api/admin/tax-summaries?page=1&pageSize=10"));
		assertRecursiveNull(taxSummaries, List.of("outputVat", "inputVat", "vatPayable",
				"sourceId", "sourceNo"));
		ensureAvailableFileObjects();
		assertThat(availableFileObjectCount()).isGreaterThanOrEqualTo(8L);
		assertThat(validatorObjectRuleDoesNotFixEighteen()).isTrue();
	}

	private void requireV34Schema() {
		for (String table : FINANCIAL_CLOSE_TABLES) {
			assertThat(tableExists(table)).as("V34 必须创建 " + table).isTrue();
		}
	}

	private long ensureOpenAccountingPeriod(AuthenticatedSession admin, String periodCode) throws Exception {
		JsonNode existing = data(get(admin, "/api/admin/gl/accounting-periods?periodCode=" + periodCode
				+ "&page=1&pageSize=20"));
		if (existing.get("items").size() > 0) {
			return existing.get("items").get(0).get("id").longValue();
		}
		ensureLedgerInitialized(admin, periodCode.substring(0, 7));
		ResponseEntity<String> response = post(admin, "/api/admin/gl/accounting-periods",
				Map.of("periodCode", periodCode, "idempotencyKey", "032-period-" + periodCode));
		if (response.getStatusCode() == HttpStatus.CONFLICT) {
			JsonNode refreshed = data(get(admin, "/api/admin/gl/accounting-periods?periodCode=" + periodCode
					+ "&page=1&pageSize=20"));
			return refreshed.get("items").get(0).get("id").longValue();
		}
		return data(response).get("id").longValue();
	}

	private void ensureLedgerInitialized(AuthenticatedSession admin, String startYearMonth) throws Exception {
		ResponseEntity<String> response = post(admin, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", startYearMonth, "idempotencyKey", "032-ledger-init"));
		assertThat(response.getStatusCode()).as(response.getBody()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
	}

	private long seedReadyFinancialClosePeriod(AuthenticatedSession admin, String periodCode) throws Exception {
		long periodId = ensureOpenAccountingPeriod(admin, periodCode);
		// 只为独立验收构造技术前置；业务状态机仍通过 032 API 完成。
		insertClosedBusinessPeriod(periodCode);
		seedConfirmedBankTaxAndPostedProfitLoss(periodId, periodCode);
		return periodId;
	}

	private void insertClosedBusinessPeriod(String periodCode) {
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		LocalDate endDate = startDate.plusMonths(1).minusDays(1);
		long businessPeriodId = this.jdbcTemplate.queryForObject("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, locked_by, locked_at, lock_reason,
					created_at, updated_at
				)
				values (?, ?, ?, ?, 'LOCKED', 'test', now(), '032 财务关闭前置', now(), now())
				returning id
				""", Long.class, periodCode, periodCode + " 业务月结", startDate, endDate);
		this.jdbcTemplate.update("""
				insert into biz_period_close_run (
					period_id, revision_no, status, latest_check_run_id, snapshot_id, source_fingerprint,
					blocking_count, warning_count, closed_by, closed_at, close_reason, created_by, created_at,
					updated_by, updated_at, version
				)
				values (?, 1, 'CLOSED', null, null, ?, 0, 0, 'test', now(), '032 财务关闭前置',
					'test', now(), 'test', now(), 0)
				""", businessPeriodId, "032-business-" + periodCode);
	}

	private void seedConfirmedBankTaxAndPostedProfitLoss(long periodId, String periodCode) {
		long bankAccountId = this.jdbcTemplate.queryForObject("""
				insert into fin_bank_account (
					account_name, account_type, bank_name, currency, gl_account_id, account_fingerprint,
					account_last4, account_masked, status, opened_on, created_by, updated_by
				)
				select ?, 'BASIC', '032 验收银行', 'CNY', id, ?, '0001', '****0001', 'ENABLED',
					?::date, 'test', 'test'
				from gl_account
				where code = '1002'
				returning id
				""", Long.class, "032 对账闭环账户" + SEQUENCE.incrementAndGet(), "032-bank-ready-" + periodCode,
				periodCode + "-01");
		this.jdbcTemplate.update("""
				insert into fin_bank_reconciliation_run (
					period_id, bank_account_id, status, statement_balance, ledger_balance, difference_amount,
					source_fingerprint, confirmed_by, confirmed_at, created_by, updated_by
				)
				values (?, ?, 'CONFIRMED', 0, 0, 0, ?, 'test', now(), 'test', 'test')
				""", periodId, bankAccountId, bankSourceFingerprint(periodId, bankAccountId));
		this.jdbcTemplate.update("""
				insert into fin_tax_profile (
					taxpayer_type, credit_code, vat_periodicity, income_tax_rate, urban_maintenance_rate,
					effective_from, current_flag, created_by, updated_by
				)
				values ('GENERAL', ?, 'MONTHLY', 0.2500, 0.0700, ?::date, true, 'test', 'test')
				on conflict (current_flag) where current_flag = true do update
				set credit_code = excluded.credit_code,
				    updated_at = now()
				""", "913200000000032TEST", periodCode + "-01");
		this.jdbcTemplate.update("""
				insert into fin_tax_period_summary (
					period_id, period_code, tax_type, status, source_fingerprint, disclaimer,
					current_flag, created_by, updated_by
				)
				values (?, ?, 'VAT', 'CONFIRMED', ?, ?, true, 'test', 'test')
				""", periodId, periodCode, taxSourceFingerprint(periodId, periodCode),
				FinancialCloseSupport.TAX_DISCLAIMER);
		this.jdbcTemplate.update("""
				insert into fin_close_profit_loss_transfer (
					ledger_id, period_id, status, source_fingerprint, debit_total, credit_total,
					line_json, reason, created_by, updated_by
				)
				select ledger_id, id, 'POSTED', ?, 0, 0, '[]'::jsonb, '032 零额损益结转前置',
					'test', 'test'
				from gl_accounting_period
				where id = ?
				""", "032-pl-ready-" + periodCode, periodId);
	}

	private String bankSourceFingerprint(long periodId, long bankAccountId) {
		String source = this.jdbcTemplate.queryForObject("""
				select coalesce(string_agg(source_key, ',' order by source_key), '')
				from (
					select 'B|' || l.id || '|' || l.version || '|' || l.posting_date || '|' || l.direction || '|' || l.amount as source_key
					from fin_bank_statement_line l
					join gl_accounting_period p on p.id = ?
					where l.bank_account_id = ?
					and l.posting_date between p.start_date and p.end_date
					and l.status <> 'IGNORED'
					union all
					select 'G|' || e.id || '|' || e.voucher_date || '|' || e.debit_amount || '|' || e.credit_amount as source_key
					from gl_ledger_entry e
					join fin_bank_account a on a.id = ?
					where e.period_id = ?
					and e.account_id = a.gl_account_id
				) source
				""", String.class, periodId, bankAccountId, bankAccountId, periodId);
		return FinancialCloseSupport.sha256("BANK_RECON|" + periodId + "|" + bankAccountId + "|" + source);
	}

	private String taxSourceFingerprint(long periodId, String periodCode) {
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		LocalDate endDate = startDate.plusMonths(1).minusDays(1);
		String source = this.jdbcTemplate.queryForObject("""
				select coalesce(string_agg(source_key, ',' order by source_key), '')
				from (
					select 'S|' || id || '|' || version || '|' || tax_amount as source_key
					from fin_sales_invoice
					where status = 'CONFIRMED'
					and invoice_date between ? and ?
					union all
					select 'P|' || id || '|' || version || '|' || tax_amount as source_key
					from fin_purchase_invoice
					where status = 'CONFIRMED'
					and invoice_date between ? and ?
					union all
					select 'E|' || id || '|' || version || '|' || tax_amount as source_key
					from fin_expense
					where status = 'CONFIRMED'
					and expense_date between ? and ?
					union all
					select 'G|' || e.id || '|' || e.voucher_date || '|' || e.debit_amount || '|' || e.credit_amount as source_key
					from gl_ledger_entry e
					join gl_account a on a.id = e.account_id
					where e.period_id = ?
					and a.category = 'PROFIT_LOSS'
				) source
				""", String.class, startDate, endDate, startDate, endDate, startDate, endDate, periodId);
		return FinancialCloseSupport.sha256("TAX|" + periodId + "|" + periodCode + "|" + source);
	}

	private Map<String, Object> closePayload(JsonNode check, String idempotencyKey) {
		return Map.of("version", check.get("version").longValue(), "idempotencyKey", idempotencyKey,
				"reason", "032 验证原子财务关闭");
	}

	private Map<String, Object> voucherPayload(long periodId, LocalDate date, String summary, String amount) {
		long bankAccount = accountId("1002");
		long capitalAccount = accountId("4001");
		return Map.of("voucherType", "GENERAL", "voucherDate", date.toString(), "summary", summary,
				"accountingPeriodId", periodId, "lines",
				List.of(Map.of("lineNo", 1, "summary", summary + " 借方", "accountId", bankAccount,
						"debitAmount", amount, "creditAmount", "0.00", "auxiliaries", List.of()),
						Map.of("lineNo", 2, "summary", summary + " 贷方", "accountId", capitalAccount,
								"debitAmount", "0.00", "creditAmount", amount, "auxiliaries", List.of())),
				"idempotencyKey", "032-closed-voucher-" + periodId + "-" + SEQUENCE.incrementAndGet());
	}

	private List<String> permissionCodes() {
		return this.jdbcTemplate.queryForList("""
				select code
				from sys_permission
				where code like 'financial-close:%'
				and type = 'ACTION'
				order by code
				""", String.class);
	}

	private List<String> accountCodes() {
		return this.jdbcTemplate.queryForList("""
				select code
				from gl_account
				where code in ('4103', '2221.03', '2221.04', '2221.05', '2221.06', '6403', '6801')
				order by code
				""", String.class);
	}

	private long systemAdminFinancialClosePermissionCount() {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role_permission rp
				join sys_role r on r.id = rp.role_id
				join sys_permission p on p.id = rp.permission_id
				where r.code = 'SYSTEM_ADMIN'
				and p.code like 'financial-close:%'
				and p.type = 'ACTION'
				""", Long.class);
	}

	private String financialReopenApprovalDefinitionSummary() {
		return this.jdbcTemplate.queryForObject("""
				select concat('definitions=', count(distinct d.id), ';steps=',
					count(s.id) filter (where s.candidate_permission_code = 'financial-close:period:reopen'))
				from platform_approval_definition d
				left join platform_approval_definition_step s on s.definition_id = d.id
				where d.scene_code = 'FINANCIAL_PERIOD_REOPEN'
				and d.status = 'ENABLED'
				""", String.class);
	}

	private long immutableTriggerCount() {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from pg_trigger t
				join pg_class c on c.oid = t.tgrelid
				where not t.tgisinternal
				and c.relname in ('fin_close_snapshot', 'fin_close_reopen_request',
					'fin_bank_reconciliation_run', 'fin_tax_period_summary')
				and t.tgname like '%immutable%'
				""", Long.class);
	}

	private String latestSuccessfulFlywayVersion() {
		return this.jdbcTemplate.queryForObject("""
				select version
				from flyway_schema_history
				where success and version ~ '^[0-9]+$'
				order by version::int desc
				limit 1
				""", String.class);
	}

	private int historicalChecksum(String version) {
		return this.jdbcTemplate.queryForObject("""
				select checksum
				from flyway_schema_history
				where success and version = ?
				""", Integer.class, version);
	}

	private boolean tableExists(String tableName) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from information_schema.tables
					where table_schema = 'public'
					and table_name = ?
				)
				""", Boolean.class, tableName);
		return Boolean.TRUE.equals(exists);
	}

	private Map<String, String> upstreamFactsSummary() {
		return Map.of("finance", rowSummary("""
				select id, status, updated_at
				from fin_voucher_draft
				"""), "periodclose", rowSummary("""
				select id, status, updated_at
				from biz_period_close_run
				"""), "projectcost", rowSummary("""
				select id, status, updated_at
				from prj_cost_calculation
				"""), "generalLedger", rowSummary("""
				select id, status, updated_at
				from gl_voucher
				"""));
	}

	private String rowSummary(String sql) {
		return this.jdbcTemplate.queryForObject("""
				select md5(coalesce(string_agg(row_to_json(t)::text, '|' order by row_to_json(t)::text), ''))
				from (%s) t
				""".formatted(sql), String.class);
	}

	private String accountingPeriodStatus(long periodId) {
		return this.jdbcTemplate.queryForObject("select status from gl_accounting_period where id = ?",
				String.class, periodId);
	}

	private long currentClosedRunCount(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_close_run
				where period_id = ?
				and status = 'CLOSED'
				""", Long.class, periodId);
	}

	private ClosedRun currentClosedRun(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				select id, version
				from fin_close_run
				where period_id = ?
				and status = 'CLOSED'
				order by id desc
				limit 1
				""", (rs, rowNum) -> new ClosedRun(rs.getLong("id"), rs.getLong("version")), periodId);
	}

	private long approvalTaskIdForReopenRequest(long requestId) {
		return this.jdbcTemplate.queryForObject("""
				select t.id
				from platform_approval_task t
				join platform_approval_instance i on i.id = t.instance_id
				join fin_close_reopen_request r on r.approval_instance_id = i.id
				where r.id = ?
				and t.status = 'PENDING'
				order by t.id desc
				limit 1
				""", Long.class, requestId);
	}

	private long taskVersion(long taskId) {
		return this.jdbcTemplate.queryForObject("select version from platform_approval_task where id = ?",
				Long.class, taskId);
	}

	private String voucherSourceType(long voucherId) {
		return this.jdbcTemplate.queryForObject("select source_type from gl_voucher where id = ?",
				String.class, voucherId);
	}

	private long ledgerEntryCount(long voucherId) {
		return this.jdbcTemplate.queryForObject("select count(*) from gl_ledger_entry where voucher_id = ?",
				Long.class, voucherId);
	}

	private long bankRawAccountLeaks() {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_bank_account
				where account_last4 <> ''
				and (account_masked ~ '[0-9]{12,}' or account_fingerprint ~ '6222020960300001234')
				""", Long.class);
	}

	private long accountId(String code) {
		return this.jdbcTemplate.queryForObject("select id from gl_account where code = ?", Long.class, code);
	}

	private long availableFileObjectCount() {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_file_object
				where status = 'AVAILABLE'
				""", Long.class);
	}

	private void ensureAvailableFileObjects() {
		long adminUserId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		this.jdbcTemplate.update("""
				insert into platform_file_object (
					bucket, object_key, original_filename, content_type, size_bytes, sha256, etag, file_usage,
					status, created_by_user_id, created_by_username
				)
				select 'qherp-test-stage032', 'stage032/object-' || gs || '.txt',
					'stage032-object-' || gs || '.txt', 'text/plain', 32 + gs,
					lpad(to_hex(gs), 64, '0'), 'stage032-etag-' || gs, 'ACCEPTANCE',
					'AVAILABLE', ?, 'admin'
				from generate_series(1, 8) gs
				on conflict (bucket, object_key) do nothing
				""", adminUserId);
	}

	private boolean validatorObjectRuleDoesNotFixEighteen() throws Exception {
		java.nio.file.Path workingDirectory = java.nio.file.Path.of(System.getProperty("user.dir"));
		java.nio.file.Path sqlPath = workingDirectory.resolve("../../tools/demo-data/sql/validate-demo-data.sql")
			.normalize();
		java.nio.file.Path validatorPath = workingDirectory.resolve("../../tools/demo-data/validate-demo-data.ps1")
			.normalize();
		if (!java.nio.file.Files.exists(sqlPath)) {
			sqlPath = workingDirectory.resolve("tools/demo-data/sql/validate-demo-data.sql").normalize();
		}
		if (!java.nio.file.Files.exists(validatorPath)) {
			validatorPath = workingDirectory.resolve("tools/demo-data/validate-demo-data.ps1").normalize();
		}
		String sql = java.nio.file.Files.readString(sqlPath);
		String validator = java.nio.file.Files.readString(validatorPath);
		return sql.contains("FILE_OBJECTS_AVAILABLE_MIN_8")
				&& validator.contains("MINIO_BUCKET_OBJECTS_MIN_8")
				&& validator.contains("bucket == database available and >= 8")
				&& !sql.contains("count(*) = 18")
				&& !validator.contains("MINIO_BUCKET_OBJECTS_18");
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "032 财务结账测试角色" + suffix, "032 财务结账测试角色");
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), username);
		this.jdbcTemplate.update("""
				insert into sys_user_role (user_id, role_id, created_by, created_at)
				values (?, ?, 'test', now())
				""", userId, roleId);
		for (String permissionCode : permissionCodes) {
			this.jdbcTemplate.update("""
					insert into sys_role_permission (role_id, permission_id, created_by, created_at)
					select ?, id, 'test', now()
					from sys_permission
					where code = ?
					""", roleId, permissionCode);
		}
		return login(username, ADMIN_PASSWORD);
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf),
				String.class);
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				String.class);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.POST,
				entity(body, session.sessionCookie(), session.csrfSession()), String.class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (csrf != null) {
			headers.add(csrf.headerName(), csrf.token());
		}
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertOk(response);
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
	}

	private String sessionCookie(ResponseEntity<String> response) {
		return response.getHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE)
			.stream()
			.filter((cookie) -> cookie.startsWith("JSESSIONID="))
			.findFirst()
			.map((cookie) -> cookie.split(";", 2)[0])
			.orElseThrow();
	}

	private List<String> recursiveValues(JsonNode node, String fieldName) {
		List<String> values = new ArrayList<>();
		collectValues(node, fieldName, values);
		return values;
	}

	private void collectValues(JsonNode node, String fieldName, List<String> values) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isObject()) {
			node.properties().forEach((entry) -> {
				if (entry.getKey().equals(fieldName) && !entry.getValue().isNull()) {
					values.add(entry.getValue().asText());
				}
				collectValues(entry.getValue(), fieldName, values);
			});
		}
		else if (node.isArray()) {
			node.forEach((item) -> collectValues(item, fieldName, values));
		}
	}

	private void assertRecursiveNull(JsonNode node, List<String> fieldNames) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isObject()) {
			node.properties().forEach((entry) -> {
				if (fieldNames.contains(entry.getKey())) {
					assertThat(entry.getValue().isNull()).as(entry.getKey()).isTrue();
				}
				assertRecursiveNull(entry.getValue(), fieldNames);
			});
		}
		else if (node.isArray()) {
			node.forEach((item) -> assertRecursiveNull(item, fieldNames));
		}
	}

	private void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

	private record ClosedRun(long id, long version) {
	}

}
