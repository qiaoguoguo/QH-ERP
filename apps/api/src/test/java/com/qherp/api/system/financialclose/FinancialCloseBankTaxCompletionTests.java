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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=financial-close-bank-tax-completion")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinancialCloseBankTaxCompletionTests extends PostgresIntegrationTest {

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
	void 银行对账必须支持多对多匹配取消未达分类零差额确认和确认后不可变() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureLedgerInitialized(admin);
		long bankAccountId = createBankAccount(admin, "6222 8888 0000 1321");
		postGeneralVoucher(admin, "2026-07-03", "银行入账一", "60.00", accountId("1002"), accountId("6001"));
		postGeneralVoucher(admin, "2026-07-04", "银行入账二", "40.00", accountId("1002"), accountId("6001"));
		postGeneralVoucher(admin, "2026-07-05", "银行未达账面", "5.00", accountId("1002"), accountId("6001"));
		long ledgerOne = ledgerEntryId("银行入账一", "1002");
		long ledgerTwo = ledgerEntryId("银行入账二", "1002");
		long ledgerOnly = ledgerEntryId("银行未达账面", "1002");
		long statementOne = bankStatement(admin, bankAccountId, "2026-07-03", "CREDIT", "70.00", "032-BANK-M1");
		long statementTwo = bankStatement(admin, bankAccountId, "2026-07-04", "CREDIT", "30.00", "032-BANK-M2");
		long bankOnly = bankStatement(admin, bankAccountId, "2026-07-05", "CREDIT", "5.00", "032-BANK-U1");

		JsonNode run = data(exchange(HttpMethod.POST, "/api/admin/bank-reconciliations",
				Map.of("periodId", periodId, "bankAccountId", bankAccountId, "idempotencyKey", "032-bank-recon"),
				admin));
		JsonNode candidates = data(get("/api/admin/bank-reconciliations/" + run.get("id").longValue()
				+ "/candidates?page=1&pageSize=50", admin));
		assertThat(candidates.get("statementLines").size()).isGreaterThanOrEqualTo(3);
		assertThat(candidates.get("ledgerEntries").size()).isGreaterThanOrEqualTo(3);

		assertError(exchange(HttpMethod.POST, "/api/admin/bank-reconciliations/" + run.get("id").longValue()
				+ "/matches", Map.of("version", run.get("version").longValue(), "matchGroupNo", "032-BAD",
						"matches", List.of(Map.of("statementLineId", statementOne, "ledgerEntryId", ledgerOne,
								"amount", "70.00")),
						"idempotencyKey", "032-bank-bad-match"), admin), HttpStatus.BAD_REQUEST,
				"FIN_BANK_MATCH_AMOUNT_INVALID");

		JsonNode matched = data(exchange(HttpMethod.POST, "/api/admin/bank-reconciliations/"
				+ run.get("id").longValue() + "/matches", Map.of("version", run.get("version").longValue(),
						"matchGroupNo", "032-G1", "matches",
						List.of(Map.of("statementLineId", statementOne, "ledgerEntryId", ledgerOne, "amount",
								"60.00"), Map.of("statementLineId", statementOne, "ledgerEntryId", ledgerTwo,
										"amount", "10.00"), Map.of("statementLineId", statementTwo,
												"ledgerEntryId", ledgerTwo, "amount", "30.00")),
						"idempotencyKey", "032-bank-good-match"), admin));
		assertThat(matched.get("status").asText()).isEqualTo("RECONCILING");
		JsonNode cancelled = data(exchange(HttpMethod.DELETE,
				"/api/admin/bank-reconciliations/" + run.get("id").longValue() + "/matches?matchGroupNo=032-G1",
				Map.of("version", matched.get("version").longValue(), "idempotencyKey", "032-bank-cancel-match"),
				admin));
		assertThat(cancelled.get("matchedAmount").asText()).isEqualTo("0.00");

		JsonNode rematched = data(exchange(HttpMethod.POST, "/api/admin/bank-reconciliations/"
				+ run.get("id").longValue() + "/matches", Map.of("version", cancelled.get("version").longValue(),
						"matchGroupNo", "032-G1", "matches",
						List.of(Map.of("statementLineId", statementOne, "ledgerEntryId", ledgerOne, "amount",
								"60.00"), Map.of("statementLineId", statementOne, "ledgerEntryId", ledgerTwo,
										"amount", "10.00"), Map.of("statementLineId", statementTwo,
												"ledgerEntryId", ledgerTwo, "amount", "30.00")),
						"idempotencyKey", "032-bank-rematch"), admin));
		JsonNode withExceptions = data(exchange(HttpMethod.POST,
				"/api/admin/bank-reconciliations/" + run.get("id").longValue() + "/exceptions",
				Map.of("version", rematched.get("version").longValue(), "exceptionType", "BANK_ONLY",
						"statementLineId", bankOnly, "amount", "5.00", "reason", "银行已入账账面未达",
						"idempotencyKey", "032-bank-only"),
				admin));
		withExceptions = data(exchange(HttpMethod.POST,
				"/api/admin/bank-reconciliations/" + run.get("id").longValue() + "/exceptions",
				Map.of("version", withExceptions.get("version").longValue(), "exceptionType", "LEDGER_ONLY",
						"ledgerEntryId", ledgerOnly, "amount", "5.00", "reason", "账面已记银行未达",
						"idempotencyKey", "032-ledger-only"),
				admin));
		for (String type : List.of("AMOUNT_DIFFERENCE", "DATE_DIFFERENCE")) {
			withExceptions = data(exchange(HttpMethod.POST,
					"/api/admin/bank-reconciliations/" + run.get("id").longValue() + "/exceptions",
					Map.of("version", withExceptions.get("version").longValue(), "exceptionType", type,
							"amount", "0.00", "reason", type + " 已解释",
							"idempotencyKey", "032-" + type),
					admin));
		}
		assertThat(textValues(withExceptions.get("exceptions"), "exceptionType"))
			.contains("BANK_ONLY", "LEDGER_ONLY", "AMOUNT_DIFFERENCE", "DATE_DIFFERENCE");

		JsonNode calculated = data(exchange(HttpMethod.POST,
				"/api/admin/bank-reconciliations/" + run.get("id").longValue() + "/calculate",
				Map.of("version", withExceptions.get("version").longValue(), "idempotencyKey", "032-bank-calc"),
				admin));
		assertThat(calculated.get("bankEndingBalance").asText()).isEqualTo("105.00");
		assertThat(calculated.get("glEndingBalance").asText()).isEqualTo("105.00");
		assertThat(calculated.get("adjustedBankBalance").asText()).isEqualTo("110.00");
		assertThat(calculated.get("adjustedBookBalance").asText()).isEqualTo("110.00");
		assertThat(calculated.get("difference").asText()).isEqualTo("0.00");
		assertThat(calculated.get("unclassifiedCount").intValue()).isZero();

		JsonNode confirmed = data(exchange(HttpMethod.POST,
				"/api/admin/bank-reconciliations/" + run.get("id").longValue() + "/confirm",
				Map.of("version", calculated.get("version").longValue(), "reason", "确认对账",
						"idempotencyKey", "032-bank-confirm"),
				admin));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertError(exchange(HttpMethod.POST, "/api/admin/bank-reconciliations/" + run.get("id").longValue()
				+ "/matches", Map.of("version", confirmed.get("version").longValue(), "matchGroupNo",
						"032-IMMUTABLE", "matches", List.of(Map.of("statementLineId", statementOne,
								"ledgerEntryId", ledgerOne, "amount", "1.00")),
						"idempotencyKey", "032-bank-after-confirm"), admin), HttpStatus.CONFLICT,
				"FIN_CLOSE_CONFLICT");
		assertThat(financialCloseAuditCount()).isGreaterThanOrEqualTo(7L);
	}

	@Test
	void 税务基础必须支持有效期配置调整来源失效凭证草稿和缴纳台账更正() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureLedgerInitialized(admin);
		postGeneralVoucher(admin, "2026-07-08", "所得税估算利润", "100.00", accountId("1001"), accountId("6001"));
		insertConfirmedTaxSources();

		JsonNode profile = data(exchange(HttpMethod.PUT, "/api/admin/tax-profiles/current",
				Map.of("taxpayerType", "GENERAL", "creditCode", "9132000000001321X1", "taxAuthority",
						"032 主管税务机关", "vatPeriodicity", "MONTHLY", "incomeTaxRate", "0.25",
						"urbanMaintenanceRate", "0.07", "effectiveFrom", "2026-01-01", "version", 0,
						"idempotencyKey", "032-tax-profile-full"),
				admin));
		assertThat(profile.get("current").booleanValue()).isTrue();
		JsonNode rateRule = data(exchange(HttpMethod.POST, "/api/admin/tax-rate-rules",
				Map.of("taxType", "VAT", "rateCode", "VAT_13_STAGE032", "rateValue", "0.13",
						"effectiveFrom", "2026-01-01", "idempotencyKey", "032-tax-rate"),
				admin));
		assertThat(rateRule.get("status").asText()).isEqualTo("ENABLED");
		JsonNode invoiceType = data(exchange(HttpMethod.POST, "/api/admin/tax-invoice-types",
				Map.of("code", "SPECIAL_VAT_STAGE032", "name", "032 专票", "direction", "OUTPUT",
						"deductible", false, "idempotencyKey", "032-tax-invoice-type"),
				admin));
		assertThat(invoiceType.get("status").asText()).isEqualTo("ENABLED");

		JsonNode summary = data(exchange(HttpMethod.POST, "/api/admin/tax-summaries",
				Map.of("periodCode", "2026-07", "taxType", "VAT", "idempotencyKey", "032-tax-summary-full"),
				admin));
		JsonNode calculated = data(exchange(HttpMethod.POST,
				"/api/admin/tax-summaries/" + summary.get("id").longValue() + "/calculate",
				Map.of("version", summary.get("version").longValue(), "idempotencyKey", "032-tax-calc-full"),
				admin));
		assertThat(calculated.get("outputVat").asText()).isEqualTo("13.00");
		assertThat(calculated.get("inputVat").asText()).isEqualTo("6.00");
		assertThat(calculated.get("vatPayable").asText()).isEqualTo("7.00");
		assertThat(calculated.get("urbanMaintenanceTax").asText()).isEqualTo("0.49");
		assertThat(calculated.get("incomeTaxEstimated").asText()).isEqualTo("25.00");
		assertThat(calculated.get("disclaimer").asText())
			.isEqualTo("本结果为 ERP 基础汇总或估算，不是正式纳税申报结果，不代替税务专业判断。");

		JsonNode adjusted = data(exchange(HttpMethod.POST,
				"/api/admin/tax-summaries/" + summary.get("id").longValue() + "/adjustments",
				Map.of("version", calculated.get("version").longValue(), "adjustmentType", "OUTPUT_INCREASE",
						"amount", "1.00", "reason", "销项补调", "idempotencyKey", "032-tax-adjust"),
				admin));
		assertThat(adjusted.get("adjustmentAmount").asText()).isEqualTo("1.00");
		assertThat(adjusted.get("vatPayable").asText()).isEqualTo("8.00");

		JsonNode voucherDraft = data(exchange(HttpMethod.POST,
				"/api/admin/tax-summaries/" + summary.get("id").longValue() + "/voucher-drafts",
				Map.of("version", adjusted.get("version").longValue(), "reason", "生成税费凭证",
						"idempotencyKey", "032-tax-voucher"),
				admin));
		long voucherId = voucherDraft.get("voucherId").longValue();
		assertThat(glVoucherSourceType(voucherId)).isEqualTo("TAX_SUMMARY");
		JsonNode confirmed = data(exchange(HttpMethod.POST,
				"/api/admin/tax-summaries/" + summary.get("id").longValue() + "/confirm",
				Map.of("version", voucherDraft.get("version").longValue(), "reason", "确认税务汇总",
						"idempotencyKey", "032-tax-confirm"),
				admin));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

		postVoucherById(admin, voucherId);
		JsonNode payment = data(exchange(HttpMethod.POST, "/api/admin/tax-payments",
				Map.of("summaryId", summary.get("id").longValue(), "taxType", "VAT", "paymentDate",
						"2026-07-28", "amount", "8.00", "voucherId", voucherId, "reason", "登记已缴税款",
						"idempotencyKey", "032-tax-payment"),
				admin));
		assertThat(payment.get("status").asText()).isEqualTo("RECORDED");
		JsonNode correction = data(exchange(HttpMethod.POST,
				"/api/admin/tax-payments/" + payment.get("id").longValue() + "/corrections",
				Map.of("amount", "-1.00", "reason", "缴纳台账更正", "idempotencyKey", "032-tax-payment-correction"),
				admin));
		assertThat(correction.get("correctionOfId").longValue()).isEqualTo(payment.get("id").longValue());

		this.jdbcTemplate.update("""
				update fin_sales_invoice
				set tax_amount = 14.00, tax_included_amount = 114.00, updated_at = now(), version = version + 1
				where invoice_no like '032-TAX-SI-%'
				""");
		JsonNode stale = data(get("/api/admin/tax-summaries/" + summary.get("id").longValue(), admin));
		assertThat(stale.get("stale").booleanValue()).isTrue();
		assertThat(stale.get("current").booleanValue()).isFalse();
		JsonNode next = data(exchange(HttpMethod.POST, "/api/admin/tax-summaries",
				Map.of("periodCode", "2026-07", "taxType", "VAT", "idempotencyKey", "032-tax-summary-new"),
				admin));
		assertThat(next.get("id").longValue()).isNotEqualTo(summary.get("id").longValue());
		assertThat(next.get("status").asText()).isEqualTo("DRAFT");
		assertThat(periodId).isPositive();
		assertThat(financialCloseAuditCount()).isGreaterThanOrEqualTo(10L);
	}

	@Test
	void 损益结转必须支持一步生成零余额并写入审计() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureLedgerInitialized(admin);

		JsonNode zero = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/profit-loss-transfers",
				Map.of("idempotencyKey", "032-pl-zero-step"), admin));
		assertThat(zero.get("status").asText()).isEqualTo("ZERO_BALANCE");
		assertThat(zero.get("voucherId").isNull()).isTrue();
		assertThat(financialCloseAuditCount()).isGreaterThanOrEqualTo(1L);
	}

	private long createBankAccount(AuthenticatedSession admin, String accountNo) throws Exception {
		return data(exchange(HttpMethod.POST, "/api/admin/bank-accounts",
				Map.of("accountName", "032 对账户", "accountType", "BASIC", "bankName", "032 银行",
						"currency", "CNY", "glAccountId", accountId("1002"), "openedOn", "2026-07-01",
						"accountNo", accountNo, "idempotencyKey", "032-bank-account-" + SEQUENCE.incrementAndGet()),
				admin)).get("id").longValue();
	}

	private long bankStatement(AuthenticatedSession admin, long bankAccountId, String date, String direction,
			String amount, String transactionId) throws Exception {
		return data(exchange(HttpMethod.POST, "/api/admin/bank-statements",
				Map.of("bankAccountId", bankAccountId, "transactionDate", date, "postingDate", date, "direction",
						direction, "amount", amount, "counterpartyName", "032 往来方", "summary", transactionId,
						"bankTransactionId", transactionId, "referenceNo", transactionId,
						"idempotencyKey", transactionId),
				admin)).get("id").longValue();
	}

	private void insertConfirmedTaxSources() {
		int suffix = SEQUENCE.incrementAndGet();
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "032-TAX-CUS-FULL-" + suffix, "032 税务客户" + suffix);
		long supplierId = this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "032-TAX-SUP-FULL-" + suffix, "032 税务供应商" + suffix);
		this.jdbcTemplate.update("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, source_type, source_id, source_no, invoice_date,
					due_date, invoice_type, currency, tax_excluded_amount, tax_amount, tax_included_amount,
					status, party_snapshot, source_snapshot, created_by, created_at, updated_by, updated_at,
					confirmed_by, confirmed_at, version
				)
				values (?, ?, 'PUBLIC', 'SALES_SHIPMENT', ?, ?, date '2026-07-12', date '2026-08-12',
					'SPECIAL_VAT', 'CNY', 100.00, 13.00, 113.00, 'CONFIRMED', '{}'::jsonb, '{}'::jsonb,
					'test', now(), 'test', now(), 'test', now(), 1)
				""", "032-TAX-SI-" + suffix, customerId, 52000 + suffix, "032-TAX-SHIP-" + suffix);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice (
					invoice_no, supplier_id, settlement_kind, ownership_type, source_type, source_id, source_no,
					invoice_date, due_date, invoice_type, currency, tax_excluded_amount, tax_amount,
					tax_included_amount, match_status, status, party_snapshot, source_snapshot, created_by,
					created_at, updated_by, updated_at, matched_by, matched_at, confirmed_by, confirmed_at, version
				)
				values (?, ?, 'STANDARD_PURCHASE', 'PUBLIC', 'PURCHASE_RECEIPT', ?, ?, date '2026-07-13',
					date '2026-08-13', 'SPECIAL_VAT', 'CNY', 50.00, 6.00, 56.00, 'MATCHED', 'CONFIRMED',
					'{}'::jsonb, '{}'::jsonb, 'test', now(), 'test', now(), 'test', now(), 'test', now(), 1)
				""", "032-TAX-PI-" + suffix, supplierId, 53000 + suffix, "032-TAX-REC-" + suffix);
	}

	private JsonNode postGeneralVoucher(AuthenticatedSession admin, String voucherDate, String summary, String amount,
			long debitAccountId, long creditAccountId) throws Exception {
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload(voucherDate, summary, amount, debitAccountId, creditAccountId), admin));
		return postVoucherById(admin, draft.get("id").longValue());
	}

	private JsonNode postVoucherById(AuthenticatedSession admin, long voucherId) throws Exception {
		JsonNode current = data(get("/api/admin/gl/vouchers/" + voucherId, admin));
		JsonNode submitted = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + voucherId + "/submit",
				Map.of("version", current.get("version").longValue(), "reason", "提交记账",
						"idempotencyKey", "032-gl-submit-" + voucherId + "-" + SEQUENCE.incrementAndGet()),
				admin));
		AuthenticatedSession approver = createUserAndLogin("032-gl-approver-", "032_GL_APPROVER_",
				List.of("platform:todo:view", "gl:voucher:view", "gl:voucher:approve-post", "gl:amount:view",
						"gl:source:view"));
		long taskId = approvalTaskId(submitted.get("approvalSummary").get("id").longValue());
		long taskVersion = approvalTaskVersion(taskId);
		data(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion, "comment", "通过并记账",
						"idempotencyKey", "032-gl-approve-" + taskId),
				approver));
		return data(get("/api/admin/gl/vouchers/" + voucherId, admin));
	}

	private Map<String, Object> manualVoucherPayload(String voucherDate, String summary, String amount,
			long debitAccountId, long creditAccountId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("voucherType", "GENERAL");
		payload.put("voucherDate", voucherDate);
		payload.put("summary", summary);
		payload.put("version", 0);
		payload.put("idempotencyKey", "032-gl-" + SEQUENCE.incrementAndGet());
		payload.put("lines", List.of(Map.of("lineNo", 1, "summary", summary, "accountId", debitAccountId,
				"debitAmount", amount, "creditAmount", "0.00"), Map.of("lineNo", 2, "summary", summary,
				"accountId", creditAccountId, "debitAmount", "0.00", "creditAmount", amount)));
		return payload;
	}

	private long ensureLedgerInitialized(AuthenticatedSession admin) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2026-07", "idempotencyKey", "032-completion-gl-init"), admin);
		if (response.getStatusCode() != HttpStatus.OK) {
			assertThat(code(response)).isEqualTo("GL_LEDGER_ALREADY_INITIALIZED");
		}
		return this.jdbcTemplate.queryForObject("""
				select id
				from gl_accounting_period
				where period_code = '2026-07'
				""", Long.class);
	}

	private long ledgerEntryId(String summary, String accountCode) {
		return this.jdbcTemplate.queryForObject("""
				select e.id
				from gl_ledger_entry e
				where e.summary = ?
				and e.account_code = ?
				order by e.id desc
				limit 1
				""", Long.class, summary, accountCode);
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

	private long financialCloseAuditCount() {
		return this.jdbcTemplate.queryForObject("select count(*) from fin_close_audit_event", Long.class);
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
		this.jdbcTemplate.update(
				"insert into sys_user_role (user_id, role_id, created_by, created_at) values (?, ?, 'test', now())",
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

	private List<String> textValues(JsonNode array, String fieldName) {
		java.util.ArrayList<String> values = new java.util.ArrayList<>();
		for (JsonNode item : array) {
			values.add(item.get(fieldName).asText());
		}
		return values;
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
