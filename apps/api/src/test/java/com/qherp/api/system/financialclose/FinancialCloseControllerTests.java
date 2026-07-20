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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=financial-close-controller")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinancialCloseControllerTests extends PostgresIntegrationTest {

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
	void 固定财务关闭API必须通过权限映射并返回检查DTO和后端脱敏字段() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = ensureLedgerInitialized(admin);

		JsonNode periods = data(get("/api/admin/financial-closes/periods?page=1&pageSize=20", admin));
		assertThat(periods.get("pageSize").intValue()).isEqualTo(20);
		assertThat(periodCodes(periods.get("items"))).contains("2026-07");
		JsonNode item = periodItem(periods.get("items"), periodId);
		assertThat(item.get("status").asText()).isEqualTo("OPEN");
		assertThat(item.get("amountVisible").booleanValue()).isTrue();
		assertThat(item.get("sourceVisible").booleanValue()).isTrue();
		assertThat(textValues(item.get("allowedActions"))).contains("CHECK");

		JsonNode check = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/checks",
				Map.of("idempotencyKey", "032-check-blocked-" + periodId), admin));
		assertThat(check.get("status").asText()).isEqualTo("BLOCKED");
		assertThat(check.get("sourceFingerprint").asText()).hasSize(64);
		assertThat(recursiveValues(check, "checkCode")).contains("BUSINESS_PERIOD_CLOSED",
				"BANK_RECONCILIATIONS_CONFIRMED", "TAX_SUMMARIES_CONFIRMED", "PROFIT_LOSS_TRANSFER_POSTED");
		assertThat(check.get("closeVersion").intValue()).isZero();

		AuthenticatedSession restricted = createUserAndLogin("032-restricted-", "032_RESTRICTED_",
				List.of("financial-close:period:view"));
		JsonNode masked = data(get("/api/admin/financial-closes/check-runs/" + check.get("id").longValue(),
				restricted));
		assertVisibilityFlags(masked, false, false, false);
		assertNullFields(masked, List.of("sourceFingerprint"));
		assertThat(recursiveValues(masked, "sourceId")).isEmpty();

		AuthenticatedSession noPermission = createUserAndLogin("032-no-perm-", "032_NO_PERM_", List.of());
		assertError(get("/api/admin/financial-closes/periods", noPermission), HttpStatus.FORBIDDEN,
				"AUTH_FORBIDDEN");
	}

	@Test
	void 银行和税务固定API必须使用字符串金额不可恢复账号和非申报免责声明() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		long bankAccountId = createBankAccount(admin, "6222 8888 0000 0321");
		JsonNode bankAccount = data(get("/api/admin/bank-accounts/" + bankAccountId, admin));
		assertThat(bankAccount.get("bankSensitiveVisible").booleanValue()).isTrue();
		assertThat(bankAccount.has("accountNo")).isFalse();
		assertThat(bankAccount.get("accountLast4").asText()).isEqualTo("0321");
		assertThat(bankAccount.get("accountMasked").asText()).contains("0321");

		assertError(exchange(HttpMethod.POST, "/api/admin/bank-accounts", bankAccountPayload("6222888800000321",
				"032-bank-dup"), admin), HttpStatus.CONFLICT, "FIN_CLOSE_CONFLICT");

		JsonNode firstLine = data(exchange(HttpMethod.POST, "/api/admin/bank-statements",
				Map.of("bankAccountId", bankAccountId, "transactionDate", "2026-07-10", "postingDate",
						"2026-07-10", "direction", "CREDIT", "amount", "200.00", "counterpartyName",
						"032 客户", "summary", "银行流水", "bankTransactionId", "032-BANK-TXN-1", "referenceNo",
						"032-REF-1", "idempotencyKey", "032-bank-line-1"),
				admin));
		JsonNode duplicateLine = data(exchange(HttpMethod.POST, "/api/admin/bank-statements",
				Map.of("bankAccountId", bankAccountId, "transactionDate", "2026-07-10", "postingDate",
						"2026-07-10", "direction", "CREDIT", "amount", "200.00", "counterpartyName",
						"032 客户", "summary", "重复导入", "bankTransactionId", "032-BANK-TXN-1", "referenceNo",
						"032-REF-1", "idempotencyKey", "032-bank-line-dup"),
				admin));
		assertThat(duplicateLine.get("id").longValue()).isEqualTo(firstLine.get("id").longValue());
		assertThat(firstLine.get("amount").asText()).isEqualTo("200.00");

		insertConfirmedTaxSources();
		JsonNode profile = data(exchange(HttpMethod.PUT, "/api/admin/tax-profiles/current",
				Map.of("taxpayerType", "GENERAL", "creditCode", "9132000000000032X1", "taxAuthority",
						"032 主管税务机关", "vatPeriodicity", "MONTHLY", "incomeTaxRate", "0.25",
						"urbanMaintenanceRate", "0.07", "effectiveFrom", "2026-01-01", "version", 0,
						"idempotencyKey", "032-tax-profile"),
				admin));
		assertThat(profile.get("creditCode").asText()).isEqualTo("9132000000000032X1");

		JsonNode summary = data(exchange(HttpMethod.POST, "/api/admin/tax-summaries",
				Map.of("periodCode", "2026-07", "taxType", "VAT", "idempotencyKey", "032-tax-summary"), admin));
		JsonNode calculated = data(exchange(HttpMethod.POST,
				"/api/admin/tax-summaries/" + summary.get("id").longValue() + "/calculate",
				Map.of("version", summary.get("version").longValue(), "idempotencyKey", "032-tax-calc"),
				admin));
		assertThat(calculated.get("status").asText()).isEqualTo("CALCULATED");
		assertThat(calculated.get("disclaimer").asText()).contains("不是正式纳税申报结果");
		assertThat(calculated.get("outputVat").asText()).isEqualTo("13.00");
		assertThat(calculated.get("inputVat").asText()).isEqualTo("6.00");
		assertThat(calculated.get("vatPayable").asText()).isEqualTo("7.00");
	}

	@Test
	void 缺少金额权限时税务汇总和缴纳台账必须统一后端脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		String periodCode = "2026-08";
		ensureAccountingPeriod(periodCode);
		insertConfirmedTaxSources(periodCode);
		JsonNode summary = data(exchange(HttpMethod.POST, "/api/admin/tax-summaries",
				Map.of("periodCode", periodCode, "taxType", "VAT",
						"idempotencyKey", "032-tax-mask-summary-" + SEQUENCE.incrementAndGet()), admin));
		JsonNode calculated = data(exchange(HttpMethod.POST,
				"/api/admin/tax-summaries/" + summary.get("id").longValue() + "/calculate",
				Map.of("version", summary.get("version").longValue(),
						"idempotencyKey", "032-tax-mask-calc-" + SEQUENCE.incrementAndGet()),
				admin));
		JsonNode adjusted = data(exchange(HttpMethod.POST,
				"/api/admin/tax-summaries/" + summary.get("id").longValue() + "/adjustments",
				Map.of("version", calculated.get("version").longValue(), "adjustmentType", "OUTPUT_INCREASE",
						"amount", "2.00", "reason", "脱敏补调",
						"idempotencyKey", "032-tax-mask-adjust-" + SEQUENCE.incrementAndGet()),
				admin));
		long paymentId = this.jdbcTemplate.queryForObject("""
				insert into fin_tax_payment_record (
					summary_id, tax_type, payment_date, amount, payment_method, reference_no, status,
					created_by, updated_by
				)
				values (?, 'VAT', date '2026-08-28', 9.00, 'BANK', '032-TAX-MASK-PAY', 'RECORDED',
					'test', 'test')
				returning id
				""", Long.class, summary.get("id").longValue());
		assertThat(adjusted.get("adjustmentAmount").asText()).isEqualTo("2.00");
		assertThat(paymentId).isPositive();

		AuthenticatedSession taxViewer = createUserAndLogin("032-tax-viewer-", "032_TAX_VIEWER_",
				List.of("financial-close:tax-summary:view", "financial-close:tax-payment:view"));
		JsonNode summaries = data(get("/api/admin/tax-summaries?page=1&pageSize=50", taxViewer));
		assertTaxSummaryAmountsMasked(itemById(summaries.get("items"), summary.get("id").longValue()));
		JsonNode detail = data(get("/api/admin/tax-summaries/" + summary.get("id").longValue(), taxViewer));
		assertTaxSummaryAmountsMasked(detail);
		JsonNode payments = data(get("/api/admin/tax-payments?page=1&pageSize=50", taxViewer));
		assertTaxPaymentAmountsMasked(itemById(payments.get("items"), paymentId));
	}

	@Test
	void 缺少金额来源和银行敏感权限时财务关闭损益银行DTO必须统一脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		String periodCode = "2026-09";
		long periodId = ensureAccountingPeriod(periodCode);
		long bankAccountId = createBankAccount(admin, "6222 8888 0000 3099");
		JsonNode statement = data(exchange(HttpMethod.POST, "/api/admin/bank-statements",
				Map.of("bankAccountId", bankAccountId, "transactionDate", "2026-09-10", "postingDate",
						"2026-09-10", "direction", "CREDIT", "amount", "120.00", "counterpartyName",
						"032 客户", "summary", "脱敏银行流水", "bankTransactionId", "032-BANK-MASK-TXN",
						"referenceNo", "032-BANK-MASK-REF",
						"idempotencyKey", "032-bank-mask-line-" + SEQUENCE.incrementAndGet()),
				admin));
		JsonNode run = data(exchange(HttpMethod.POST, "/api/admin/bank-reconciliations",
				Map.of("periodId", periodId, "bankAccountId", bankAccountId,
						"idempotencyKey", "032-bank-mask-recon-" + SEQUENCE.incrementAndGet()),
				admin));
		long transferId = insertProfitLossTransfer(periodId);

		AuthenticatedSession viewer = createUserAndLogin("032-sensitive-viewer-", "032_SENSITIVE_VIEWER_",
				List.of("financial-close:period:view", "financial-close:profit-loss:view",
						"financial-close:profit-loss:generate", "financial-close:bank-account:view",
						"financial-close:bank-reconciliation:view"));

		JsonNode bankAccount = data(get("/api/admin/bank-accounts/" + bankAccountId, viewer));
		assertVisibilityFlags(bankAccount, false, false, false);
		assertNullFields(bankAccount, List.of("accountLast4"));

		JsonNode statements = data(get("/api/admin/bank-statements?bankAccountId=" + bankAccountId
				+ "&page=1&pageSize=10", viewer));
		JsonNode statementLine = itemById(statements.get("items"), statement.get("id").longValue());
		assertVisibilityFlags(statementLine, false, false, false);
		assertNullFields(statementLine, List.of("amount", "bankTransactionId", "referenceNo"));

		JsonNode reconciliation = data(get("/api/admin/bank-reconciliations/" + run.get("id").longValue(), viewer));
		assertVisibilityFlags(reconciliation, false, false, false);
		assertNullFields(reconciliation, List.of("statementBalance", "ledgerBalance", "differenceAmount",
				"bankEndingBalance", "glEndingBalance", "adjustedBankBalance", "adjustedBookBalance",
				"difference", "matchedAmount", "sourceFingerprint"));

		JsonNode candidates = data(get("/api/admin/bank-reconciliations/" + run.get("id").longValue()
				+ "/candidates?page=1&pageSize=10", viewer));
		assertVisibilityFlags(candidates, false, false, false);
		JsonNode statementCandidate = itemById(candidates.get("statementLines"), statement.get("id").longValue());
		assertNullFields(statementCandidate, List.of("amount", "matchedAmount", "remainingAmount",
				"bankTransactionId", "referenceNo"));

		JsonNode preview = data(exchange(HttpMethod.POST,
				"/api/admin/financial-closes/periods/" + periodId + "/profit-loss-transfers/preview",
				Map.of("idempotencyKey", "032-pl-mask-preview-" + SEQUENCE.incrementAndGet()), viewer));
		assertVisibilityFlags(preview, false, false, false);
		assertNullFields(preview, List.of("sourceFingerprint", "debitTotal", "creditTotal"));

		JsonNode transfers = data(get("/api/admin/financial-closes/periods/" + periodId
				+ "/profit-loss-transfers?page=1&pageSize=10", viewer));
		JsonNode transfer = itemById(transfers.get("items"), transferId);
		assertVisibilityFlags(transfer, false, false, false);
		assertNullFields(transfer, List.of("sourceFingerprint", "debitTotal", "creditTotal", "lineJson"));
	}

	private long createBankAccount(AuthenticatedSession admin, String accountNo) throws Exception {
		return data(exchange(HttpMethod.POST, "/api/admin/bank-accounts",
				bankAccountPayload(accountNo, "032-bank-create-" + SEQUENCE.incrementAndGet()), admin)).get("id")
			.longValue();
	}

	private Map<String, Object> bankAccountPayload(String accountNo, String key) {
		return Map.of("accountName", "032 基本户", "accountType", "BASIC", "bankName", "032 银行",
				"currency", "CNY", "glAccountId", accountId("1002"), "openedOn", "2026-07-01",
				"accountNo", accountNo, "idempotencyKey", key);
	}

	private void insertConfirmedTaxSources() {
		insertConfirmedTaxSources("2026-07");
	}

	private void insertConfirmedTaxSources(String periodCode) {
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate invoiceDate = LocalDate.parse(periodCode + "-12");
		LocalDate dueDate = invoiceDate.plusMonths(1);
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "032-TAX-CUS-" + suffix, "032 税务客户" + suffix);
		long supplierId = this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "032-TAX-SUP-" + suffix, "032 税务供应商" + suffix);
		this.jdbcTemplate.update("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, source_type, source_id, source_no, invoice_date,
					due_date, invoice_type, currency, tax_excluded_amount, tax_amount, tax_included_amount,
					status, party_snapshot, source_snapshot, created_by, created_at, updated_by, updated_at,
					confirmed_by, confirmed_at, version
				)
				values (?, ?, 'PUBLIC', 'SALES_SHIPMENT', ?, ?, ?, ?,
					'SPECIAL_VAT', 'CNY', 100.00, 13.00, 113.00, 'CONFIRMED', '{}'::jsonb, '{}'::jsonb,
					'test', now(), 'test', now(), 'test', now(), 1)
				""", "032-TAX-SI-" + suffix, customerId, 32000 + suffix, "032-TAX-SHIP-" + suffix,
				invoiceDate, dueDate);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice (
					invoice_no, supplier_id, settlement_kind, ownership_type, source_type, source_id, source_no,
					invoice_date, due_date, invoice_type, currency, tax_excluded_amount, tax_amount,
					tax_included_amount, match_status, status, party_snapshot, source_snapshot, created_by,
					created_at, updated_by, updated_at, confirmed_by, confirmed_at, version
				)
				values (?, ?, 'STANDARD_PURCHASE', 'PUBLIC', 'PURCHASE_RECEIPT', ?, ?, ?, ?,
					'SPECIAL_VAT', 'CNY', 50.00, 6.00, 56.00, 'MATCHED', 'CONFIRMED',
					'{}'::jsonb, '{}'::jsonb, 'test', now(), 'test', now(), 'test', now(), 1)
				""", "032-TAX-PI-" + suffix, supplierId, 33000 + suffix, "032-TAX-REC-" + suffix,
				invoiceDate.plusDays(1), dueDate.plusDays(1));
	}

	private long ensureLedgerInitialized(AuthenticatedSession admin) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2026-07", "idempotencyKey", "032-gl-init"), admin);
		if (response.getStatusCode() != HttpStatus.OK) {
			assertThat(code(response)).isEqualTo("GL_LEDGER_ALREADY_INITIALIZED");
		}
		return this.jdbcTemplate.queryForObject("""
				select id
				from gl_accounting_period
				where period_code = '2026-07'
				""", Long.class);
	}

	private long ensureAccountingPeriod(String periodCode) {
		Long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'",
				Long.class);
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		return this.jdbcTemplate.queryForObject("""
				insert into gl_accounting_period (
					ledger_id, period_code, start_date, end_date, status, created_by, created_at, updated_by,
					updated_at
				)
				values (?, ?, ?, ?, 'OPEN', 'test', now(), 'test', now())
				on conflict (ledger_id, period_code) do update set period_code = excluded.period_code
				returning id
				""", Long.class, ledgerId, periodCode, startDate,
				startDate.withDayOfMonth(startDate.lengthOfMonth()));
	}

	private long insertProfitLossTransfer(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_close_profit_loss_transfer (
					ledger_id, period_id, status, source_fingerprint, debit_total, credit_total, line_json,
					reason, idempotency_key, request_fingerprint, created_by, updated_by
				)
				select id, ?, 'DRAFT', ?, 10.00, 10.00,
					'[{"lineNo":1,"debitAmount":"10.00","creditAmount":"0.00"}]'::jsonb,
					'脱敏测试', ?, ?, 'test', 'test'
				from gl_ledger
				where code = 'MAIN'
				returning id
				""", Long.class, periodId, "032-pl-mask-fingerprint-" + SEQUENCE.incrementAndGet(),
				"032-pl-mask-" + SEQUENCE.incrementAndGet(),
				"032-pl-mask-request-" + SEQUENCE.incrementAndGet());
	}

	private long accountId(String code) {
		return this.jdbcTemplate.queryForObject("select id from gl_account where code = ?", Long.class, code);
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

	private List<String> periodCodes(JsonNode items) {
		java.util.ArrayList<String> result = new java.util.ArrayList<>();
		for (JsonNode item : items) {
			result.add(item.get("periodCode").asText());
		}
		return result;
	}

	private JsonNode periodItem(JsonNode items, long periodId) {
		for (JsonNode item : items) {
			if (item.get("id").longValue() == periodId) {
				return item;
			}
		}
		throw new AssertionError("未找到期间：" + periodId);
	}

	private JsonNode itemById(JsonNode items, long id) {
		for (JsonNode item : items) {
			if (item.get("id").longValue() == id) {
				return item;
			}
		}
		throw new AssertionError("未找到记录：" + id);
	}

	private void assertTaxSummaryAmountsMasked(JsonNode summary) {
		assertVisibilityFlags(summary, false, false, false);
		assertNullFields(summary, List.of("outputVat", "inputVat", "transferOutVat", "adjustmentAmount",
				"openingCreditVat", "vatPayable", "urbanMaintenanceTax", "endingCreditVat",
				"incomeTaxEstimated", "sourceFingerprint"));
		assertThat(summary.get("lines").size()).isPositive();
		for (JsonNode line : summary.get("lines")) {
			assertNullFields(line, List.of("sourceId", "sourceNo", "amount", "taxAmount"));
		}
	}

	private void assertTaxPaymentAmountsMasked(JsonNode payment) {
		assertVisibilityFlags(payment, false, false, false);
		assertNullFields(payment, List.of("amount", "referenceNo"));
	}

	private void assertVisibilityFlags(JsonNode node, boolean amountVisible, boolean sourceVisible,
			boolean bankSensitiveVisible) {
		assertThat(node.has("amountVisible")).as("amountVisible").isTrue();
		assertThat(node.get("amountVisible").booleanValue()).as("amountVisible").isEqualTo(amountVisible);
		assertThat(node.has("sourceVisible")).as("sourceVisible").isTrue();
		assertThat(node.get("sourceVisible").booleanValue()).as("sourceVisible").isEqualTo(sourceVisible);
		assertThat(node.has("bankSensitiveVisible")).as("bankSensitiveVisible").isTrue();
		assertThat(node.get("bankSensitiveVisible").booleanValue()).as("bankSensitiveVisible")
			.isEqualTo(bankSensitiveVisible);
	}

	private void assertNullFields(JsonNode node, List<String> fieldNames) {
		for (String fieldName : fieldNames) {
			if (node.has(fieldName)) {
				assertThat(node.get(fieldName).isNull()).as(fieldName).isTrue();
			}
		}
	}

	private List<String> textValues(JsonNode array) {
		java.util.ArrayList<String> values = new java.util.ArrayList<>();
		for (JsonNode item : array) {
			values.add(item.asText());
		}
		return values;
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
