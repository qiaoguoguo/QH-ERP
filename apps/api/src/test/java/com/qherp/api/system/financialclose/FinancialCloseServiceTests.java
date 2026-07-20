package com.qherp.api.system.financialclose;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=financial-close-service")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinancialCloseServiceTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void 缺少030业务月结时关闭必须阻断且不产生半关闭状态() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureLedgerInitialized(admin);

		JsonNode check = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/checks",
				Map.of("idempotencyKey", "032-block-close-check-" + periodId), admin));
		assertThat(check.get("status").asText()).isEqualTo("BLOCKED");
		assertThat(recursiveValues(check, "checkCode")).contains("BUSINESS_PERIOD_CLOSED",
				"BANK_RECONCILIATIONS_CONFIRMED", "TAX_SUMMARIES_CONFIRMED", "PROFIT_LOSS_TRANSFER_POSTED");

		assertError(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/check-runs/" + check.get("id").longValue() + "/close",
				Map.of("version", check.get("version").longValue(), "reason", "缺少业务月结不得关闭",
						"idempotencyKey", "032-block-close-" + periodId),
				admin), HttpStatus.CONFLICT, "FIN_CLOSE_NOT_READY");
		assertThat(accountingPeriodStatus(periodId)).isEqualTo("OPEN");
		assertThat(currentClosedRunCount(periodId)).isZero();
		assertThat(snapshotCount(periodId)).isZero();
	}

	@Test
	void 关闭成功后所有GL写路径失败且反结账审批最终恢复开放并保留历史() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureLedgerInitialized(admin);
		insertClosedBusinessPeriod("2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
		seedConfirmedBankTaxAndPostedProfitLoss(periodId, "2026-07");

		JsonNode check = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/checks",
				Map.of("idempotencyKey", "032-ready-close-check-" + periodId), admin));
		assertThat(check.get("status").asText()).isEqualTo("READY");
		JsonNode closed = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/check-runs/" + check.get("id").longValue() + "/close",
				Map.of("version", check.get("version").longValue(), "reason", "完成财务结账",
						"idempotencyKey", "032-close-ok-" + periodId),
				admin));
		assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
		assertThat(accountingPeriodStatus(periodId)).isEqualTo("CLOSED");
		assertThat(currentClosedRunCount(periodId)).isOne();
		assertThat(snapshotCount(periodId)).isOne();

		assertError(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload("2026-07-16", "关闭期间不得新增凭证", "10.00", "032-gl-closed-"),
				admin), HttpStatus.CONFLICT, "FIN_CLOSE_PERIOD_CLOSED");

		JsonNode reopenRequest = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/close-runs/" + closed.get("id").longValue() + "/reopen-requests",
				Map.of("version", closed.get("version").longValue(), "reason", "需要补记调整凭证",
						"idempotencyKey", "032-reopen-request-" + closed.get("id").longValue()),
				admin));
		assertThat(reopenRequest.get("status").asText()).isEqualTo("SUBMITTED");
		long taskId = approvalTaskId(reopenRequest.get("approvalInstanceId").longValue());
		long taskVersion = approvalTaskVersion(taskId);
		assertError(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion, "comment", "申请人不得自审", "idempotencyKey",
						"032-reopen-self-" + taskId),
				admin), HttpStatus.FORBIDDEN, "APPROVAL_SELF_ACTION_FORBIDDEN");

		AuthenticatedSession approver = createUserAndLogin("032-reopen-approver-", "032_REOPEN_APPROVER_",
				List.of("platform:todo:view", "financial-close:period:view", "financial-close:period:reopen"));
		JsonNode approved = data(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion, "comment", "通过并反结账", "idempotencyKey",
						"032-reopen-approve-" + taskId),
				approver));
		assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
		assertThat(accountingPeriodStatus(periodId)).isEqualTo("OPEN");
		assertThat(currentClosedRunCount(periodId)).isZero();
		assertThat(closeRunStatus(closed.get("id").longValue())).isEqualTo("REOPENED");
		assertThat(snapshotCount(periodId)).isOne();
	}

	@Test
	void 损益结转必须按损益余额生成031草稿并对相同来源保持幂等() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureLedgerInitialized(admin);
		postGeneralVoucher(admin, "2026-07-20", "确认收入形成损益余额", "300.00", accountId("1002"),
				accountId("6001"));

		JsonNode preview = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/profit-loss-transfers/preview",
				Map.of("idempotencyKey", "032-pl-preview-" + periodId), admin));
		assertThat(preview.get("status").asText()).isEqualTo("PREVIEW");
		assertThat(preview.get("sourceFingerprint").asText()).hasSize(64);
		assertThat(lineByAccount(preview, "6001").get("debitAmount").asText()).isEqualTo("300.00");
		assertThat(lineByAccount(preview, "4103").get("creditAmount").asText()).isEqualTo("300.00");

		JsonNode generated = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/profit-loss-transfers",
				Map.of("sourceFingerprint", preview.get("sourceFingerprint").asText(), "reason", "生成损益结转草稿",
						"idempotencyKey", "032-pl-generate-" + periodId),
				admin));
		assertThat(generated.get("status").asText()).isEqualTo("DRAFT");
		assertThat(generated.get("voucherId").isNull()).isFalse();
		assertThat(glVoucherSourceType(generated.get("voucherId").longValue())).isEqualTo("PROFIT_LOSS_CARRYFORWARD");
		JsonNode replay = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/profit-loss-transfers",
				Map.of("sourceFingerprint", preview.get("sourceFingerprint").asText(), "reason", "生成损益结转草稿",
						"idempotencyKey", "032-pl-generate-" + periodId),
				admin));
		assertThat(replay.get("id").longValue()).isEqualTo(generated.get("id").longValue());
	}

	private JsonNode postGeneralVoucher(AuthenticatedSession admin, String voucherDate, String summary, String amount,
			long debitAccountId, long creditAccountId) throws Exception {
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload(voucherDate, summary, amount, "032-gl-post-", debitAccountId, creditAccountId),
				admin));
		JsonNode submitted = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/" + draft.get("id").longValue() + "/submit",
				Map.of("version", draft.get("version").longValue(), "reason", "提交记账", "idempotencyKey",
						"032-gl-submit-" + draft.get("id").longValue()),
				admin));
		AuthenticatedSession approver = createUserAndLogin("032-gl-approver-", "032_GL_APPROVER_",
				List.of("platform:todo:view", "gl:voucher:view", "gl:voucher:approve-post", "gl:amount:view",
						"gl:source:view"));
		long taskId = approvalTaskId(submitted.get("approvalSummary").get("id").longValue());
		long taskVersion = approvalTaskVersion(taskId);
		data(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion, "comment", "通过并记账", "idempotencyKey",
						"032-gl-approve-" + taskId),
				approver));
		return data(get("/api/admin/gl/vouchers/" + draft.get("id").longValue(), admin));
	}

	private Map<String, Object> manualVoucherPayload(String voucherDate, String summary, String amount,
			String keyPrefix) {
		return manualVoucherPayload(voucherDate, summary, amount, keyPrefix, accountId("1002"), accountId("4001"));
	}

	private Map<String, Object> manualVoucherPayload(String voucherDate, String summary, String amount,
			String keyPrefix, long debitAccountId, long creditAccountId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("voucherType", "GENERAL");
		payload.put("voucherDate", voucherDate);
		payload.put("summary", summary);
		payload.put("version", 0);
		payload.put("idempotencyKey", keyPrefix + SEQUENCE.incrementAndGet());
		payload.put("lines", List.of(Map.of("lineNo", 1, "summary", "借方", "accountId", debitAccountId,
				"debitAmount", amount, "creditAmount", "0.00"), Map.of("lineNo", 2, "summary", "贷方",
				"accountId", creditAccountId, "debitAmount", "0.00", "creditAmount", amount)));
		return payload;
	}

	private void insertClosedBusinessPeriod(String periodCode, LocalDate startDate, LocalDate endDate) {
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
				select ?, 'BASIC', '032 银行', 'CNY', id, ?, '0001', '****0001', 'ENABLED',
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
		LocalDate start = LocalDate.parse(periodCode + "-01");
		LocalDate end = start.plusMonths(1).minusDays(1);
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
				""", String.class, start, end, start, end, start, end, periodId);
		return FinancialCloseSupport.sha256("TAX|" + periodId + "|" + periodCode + "|" + source);
	}

	private long ensureLedgerInitialized(AuthenticatedSession admin) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2026-07", "idempotencyKey", "032-service-gl-init"), admin);
		if (response.getStatusCode() != HttpStatus.OK) {
			assertThat(code(response)).isEqualTo("GL_LEDGER_ALREADY_INITIALIZED");
		}
		return this.jdbcTemplate.queryForObject("""
				select id
				from gl_accounting_period
				where period_code = '2026-07'
				""", Long.class);
	}

	private String accountingPeriodStatus(long periodId) {
		return this.jdbcTemplate.queryForObject("select status from gl_accounting_period where id = ?", String.class,
				periodId);
	}

	private long currentClosedRunCount(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_close_run
				where period_id = ?
				and status = 'CLOSED'
				""", Long.class, periodId);
	}

	private long snapshotCount(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_close_snapshot
				where period_id = ?
				""", Long.class, periodId);
	}

	private String closeRunStatus(long closeRunId) {
		return this.jdbcTemplate.queryForObject("select status from fin_close_run where id = ?", String.class,
				closeRunId);
	}

	private long approvalTaskId(long instanceId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from platform_approval_task
				where instance_id = ?
				order by id
				limit 1
				""", Long.class, instanceId);
	}

	private long approvalTaskVersion(long taskId) {
		return this.jdbcTemplate.queryForObject("select version from platform_approval_task where id = ?",
				Long.class, taskId);
	}

	private long accountId(String code) {
		return this.jdbcTemplate.queryForObject("select id from gl_account where code = ?", Long.class, code);
	}

	private String glVoucherSourceType(long voucherId) {
		return this.jdbcTemplate.queryForObject("select source_type from gl_voucher where id = ?", String.class,
				voucherId);
	}

	private JsonNode lineByAccount(JsonNode preview, String accountCode) {
		for (JsonNode line : preview.get("lines")) {
			if (accountCode.equals(line.get("accountCode").asText())) {
				return line;
			}
		}
		throw new AssertionError("未找到科目：" + accountCode);
	}

	private List<String> recursiveValues(JsonNode node, String fieldName) {
		java.util.ArrayList<String> values = new java.util.ArrayList<>();
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

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, rolePrefix + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), username);
		this.jdbcTemplate.update("insert into sys_user_role (user_id, role_id, created_by, created_at) values (?, ?, 'test', now())",
				userId, roleId);
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
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		try {
			JsonNode data = data(response);
			return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session == null ? null : session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
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
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode body = this.objectMapper.readTree(response.getBody());
		assertThat(body.get("code").asText()).isEqualTo("OK");
		return body.get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String expectedCode) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(expectedCode);
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

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

}
