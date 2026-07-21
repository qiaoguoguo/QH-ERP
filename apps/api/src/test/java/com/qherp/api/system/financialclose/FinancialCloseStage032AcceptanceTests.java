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

	private static final String LATEST_MIGRATION_VERSION = "36";

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final int EXPECTED_V35_CHECKSUM = -82801719;

	private static final int EXPECTED_V36_CHECKSUM = 1030907058;

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

	private static final List<String> MANDATORY_FINANCIAL_CLOSE_CHECKS = List.of(
			"PREVIOUS_PERIOD_CLOSED", "BUSINESS_PERIOD_CLOSED", "NO_INCOMPLETE_VOUCHERS",
			"TRIAL_BALANCE_BALANCED", "BANK_RECONCILIATIONS_CONFIRMED", "TAX_SUMMARIES_CONFIRMED",
			"TAX_VOUCHERS_POSTED", "PROFIT_LOSS_TRANSFER_POSTED", "NO_SOURCE_CHANGES");

	private static final List<String> FROZEN_BANK_EXCEPTION_TYPES = List.of("BANK_ONLY_CREDIT",
			"BANK_ONLY_DEBIT", "BOOK_ONLY_DEBIT", "BOOK_ONLY_CREDIT");

	private static final Map<String, String> EXPECTED_TAX_ACCOUNT_NAMES = Map.of("2221.03", "未交增值税",
			"2221.04", "应交城市维护建设税", "2221.05", "应交教育费附加", "2221.06", "应交企业所得税",
			"4103", "本年利润", "6403", "税金及附加", "6801", "所得税费用");

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
	void v34迁移必须创建032表权限审批科目和验证器门禁() throws Exception {
		requireV34Schema();
		assertThat(permissionCodes()).containsExactlyInAnyOrderElementsOf(FINANCIAL_CLOSE_PERMISSIONS);
		assertThat(systemAdminFinancialClosePermissionCount()).isGreaterThanOrEqualTo(FINANCIAL_CLOSE_PERMISSIONS.size());
		assertThat(financialReopenApprovalDefinitionSummary()).isEqualTo("definitions=1;steps=1");
		assertThat(taxAccountNames()).containsExactlyInAnyOrderEntriesOf(EXPECTED_TAX_ACCOUNT_NAMES);
		assertThat(bankExceptionTypes()).containsExactlyInAnyOrderElementsOf(FROZEN_BANK_EXCEPTION_TYPES);
		assertThat(frontendFinancialCloseRoutesAreFrozen()).isTrue();
		assertThat(immutableTriggerCount()).isGreaterThanOrEqualTo(4L);
		assertThat(latestSuccessfulFlywayVersion()).isEqualTo(LATEST_MIGRATION_VERSION);
		assertThat(historicalChecksum("29")).isEqualTo(774334682);
		assertThat(historicalChecksum("30")).isEqualTo(2130342893);
		assertThat(historicalChecksum("31")).isEqualTo(-2074547591);
		assertThat(historicalChecksum("32")).isEqualTo(249406902);
		assertThat(historicalChecksum("33")).isEqualTo(612501943);
		assertThat(historicalChecksum("34")).isEqualTo(-629066235);
		assertThat(historicalChecksum("35")).isEqualTo(EXPECTED_V35_CHECKSUM);
		assertThat(historicalChecksum("36")).isEqualTo(EXPECTED_V36_CHECKSUM);
		assertThat(bankStatementPermissionRoutes()).containsExactlyInAnyOrder(
				"financial-close:bank-reconciliation:view|/gl/bank-statements|GET|/api/admin/bank-statements/**,/api/admin/bank-reconciliations/**",
				"financial-close:bank-reconciliation:import|/gl/bank-statements|POST|/api/admin/bank-statements/**,/api/admin/bank-statement-lines/**");
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
		assertMandatoryCheckCodes(check);
		assertCheckFailed(check, "BUSINESS_PERIOD_CLOSED");
		assertThat(check.get("sourceFingerprint").asText()).isNotBlank();
		JsonNode storedCheck = data(get(admin, "/api/admin/financial-closes/check-runs/" + check.get("id").longValue()));
		assertThat(storedCheck.get("checkItems")).hasSize(MANDATORY_FINANCIAL_CLOSE_CHECKS.size());
		assertMandatoryCheckCodes(storedCheck);
		JsonNode refreshedPeriod = data(get(admin, "/api/admin/financial-closes/periods/" + periodId));
		assertThat(refreshedPeriod.get("latestCheckRunId").longValue()).isEqualTo(check.get("id").longValue());

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
		assertMandatoryCheckCodes(check);

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

	@Test
	@Order(6)
	void 财务关闭强制检查必须覆盖九项集合并阻断上期未关未完成凭证试算不平和税费凭证未记账() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		ensureOpenAccountingPeriod(admin, "2096-04");
		long previousOpenPeriodId = seedReadyFinancialClosePeriod(admin, "2096-05", false);
		JsonNode previousOpenCheck = runCheck(admin, previousOpenPeriodId, "032-check-previous-open-");
		assertThat(previousOpenCheck.get("status").asText()).isEqualTo("BLOCKED");
		assertMandatoryCheckCodes(previousOpenCheck);
		assertCheckFailed(previousOpenCheck, "PREVIOUS_PERIOD_CLOSED");

		long incompleteVoucherPeriodId = seedReadyFinancialClosePeriod(admin, "2096-06");
		data(post(admin, "/api/admin/gl/vouchers", voucherPayload(incompleteVoucherPeriodId,
				LocalDate.of(2096, 6, 10), "032 未完成凭证阻断", "6.00")));
		JsonNode incompleteVoucherCheck = runCheck(admin, incompleteVoucherPeriodId,
				"032-check-incomplete-voucher-");
		assertThat(incompleteVoucherCheck.get("status").asText()).isEqualTo("BLOCKED");
		assertMandatoryCheckCodes(incompleteVoucherCheck);
		assertCheckFailed(incompleteVoucherCheck, "NO_INCOMPLETE_VOUCHERS");

		long unbalancedPeriodId = seedReadyFinancialClosePeriod(admin, "2096-07");
		breakTrialBalanceCache(unbalancedPeriodId);
		JsonNode unbalancedCheck = runCheck(admin, unbalancedPeriodId, "032-check-trial-unbalanced-");
		assertThat(unbalancedCheck.get("status").asText()).isEqualTo("BLOCKED");
		assertMandatoryCheckCodes(unbalancedCheck);
		assertCheckFailed(unbalancedCheck, "TRIAL_BALANCE_BALANCED");

		long taxVoucherPeriodId = seedReadyFinancialClosePeriod(admin, "2096-08");
		seedDraftTaxVoucher(taxVoucherPeriodId, "2096-08");
		JsonNode taxVoucherCheck = runCheck(admin, taxVoucherPeriodId, "032-check-tax-voucher-draft-");
		assertThat(taxVoucherCheck.get("status").asText()).isEqualTo("BLOCKED");
		assertMandatoryCheckCodes(taxVoucherCheck);
		assertCheckFailed(taxVoucherCheck, "TAX_VOUCHERS_POSTED");
	}

	@Test
	@Order(7)
	void 关闭事务必须复检来源和检查项不得用过期ready运行完成关闭() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = seedReadyFinancialClosePeriod(admin, "2096-09");
		JsonNode check = runCheck(admin, periodId, "032-check-ready-before-source-change-");
		assertThat(check.get("status").asText()).isEqualTo("READY");
		assertMandatoryCheckCodes(check);

		data(post(admin, "/api/admin/gl/vouchers", voucherPayload(periodId, LocalDate.of(2096, 9, 12),
				"032 READY 后新增草稿凭证", "9.00")));

		assertConflictCodeIn(post(admin, "/api/admin/financial-closes/check-runs/" + check.get("id").longValue()
				+ "/close", closePayload(check, "032-close-after-source-change-" + periodId)),
				List.of("FIN_CLOSE_STALE", "FIN_CLOSE_NOT_READY"));
		assertThat(accountingPeriodStatus(periodId)).isEqualTo("OPEN");
		assertThat(currentClosedRunCount(periodId)).isZero();

		JsonNode refreshed = runCheck(admin, periodId, "032-check-after-source-change-");
		assertThat(refreshed.get("status").asText()).isEqualTo("BLOCKED");
		assertMandatoryCheckCodes(refreshed);
		assertThat(refreshed.get("sourceFingerprint").asText()).isNotEqualTo(check.get("sourceFingerprint").asText());
		assertCheckFailed(refreshed, "NO_INCOMPLETE_VOUCHERS");
	}

	@Test
	@Order(8)
	void 银行账户必须支持1002子树并拒绝非末级非借方和历史换绑() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long childAccountId = ensureBankAccountChild("1002.01", "032 银行存款基本户", "ASSET", "DEBIT",
				true, true, true);
		long nonLeafAccountId = ensureBankAccountChild("1002.88", "032 银行存款非末级", "ASSET", "DEBIT",
				false, true, true);
		long nonDebitAccountId = ensureBankAccountChild("1002.99", "032 银行存款贷方方向", "ASSET", "CREDIT",
				true, true, true);

		JsonNode bankAccount = data(post(admin, "/api/admin/bank-accounts",
				bankAccountPayload("032 子树银行账户", childAccountId, "6222020960300004321")));
		assertThat(bankAccount.get("glAccountId").longValue()).isEqualTo(childAccountId);
		assertThat(bankAccount.get("glAccountCode").asText()).isEqualTo("1002.01");
		assertThat(bankAccount.get("accountMasked").asText()).isEqualTo("****4321");
		assertThat(bankAccount.toString()).doesNotContain("6222020960300004321");
		JsonNode persistedBankAccount = data(get(admin, "/api/admin/bank-accounts/"
				+ bankAccount.get("id").longValue()));
		assertThat(persistedBankAccount.get("glAccountId").longValue()).isEqualTo(childAccountId);

		assertError(post(admin, "/api/admin/bank-accounts",
				bankAccountPayload("032 非末级银行账户", nonLeafAccountId, "6222020960300004322")),
				HttpStatus.BAD_REQUEST, "GL_ACCOUNT_NOT_LEAF");
		assertError(post(admin, "/api/admin/bank-accounts",
				bankAccountPayload("032 非借方银行账户", nonDebitAccountId, "6222020960300004323")),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		data(post(admin, "/api/admin/bank-statements", Map.of("bankAccountId", bankAccount.get("id").longValue(),
				"transactionDate", "2096-10-01", "postingDate", "2096-10-01", "direction", "CREDIT",
				"amount", "18.00", "counterpartyName", "032 往来单位", "summary", "032 历史换绑保护流水",
				"bankTransactionId", "032-HISTORY-" + SEQUENCE.incrementAndGet(), "referenceNo",
				"032-HISTORY-REF", "idempotencyKey", "032-history-statement-" + SEQUENCE.incrementAndGet())));
		long targetChildAccountId = ensureBankAccountChild("1002.02", "032 银行存款一般户", "ASSET", "DEBIT",
				true, true, true);
		assertError(put(admin, "/api/admin/bank-accounts/" + bankAccount.get("id").longValue(),
				bankAccountPayload("032 子树银行账户换绑", targetChildAccountId, "6222020960300004321",
						bankAccount.get("version").longValue())),
				HttpStatus.CONFLICT, "FIN_CLOSE_CONFLICT");
	}

	@Test
	@Order(9)
	void 税务基础必须冻结DTO税率票种所得税基础项和税款脱敏标识() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		data(put(admin, "/api/admin/tax-profiles/current", taxProfilePayload()));

		JsonNode profile = data(get(admin, "/api/admin/tax-profiles/current"));
		assertThat(profile.hasNonNull("unifiedSocialCreditCodeMasked")).isTrue();
		assertThat(profile.hasNonNull("urbanMaintenanceRate")).isTrue();
		assertThat(profile.has("creditCode")).as("税务 DTO 不得暴露未脱敏统一社会信用代码字段").isFalse();

		assertThat(taxRateRuleCodes()).contains("VAT_13", "VAT_9", "VAT_6", "VAT_0", "SIMPLIFIED_3",
				"INCOME_25", "URBAN_7", "URBAN_5", "URBAN_1");
		assertThat(taxInvoiceTypeNames()).contains("数电专票", "数电普票", "纸质专票", "纸质普票");
		assertThat(taxAdjustmentTypes()).containsExactly("OUTPUT_INCREASE", "OUTPUT_DECREASE",
				"INPUT_INCREASE", "INPUT_DECREASE");

		long periodId = ensureOpenAccountingPeriod(admin, "2096-12");
		long summaryId = seedConfirmedTaxSummary(periodId, "2096-12", "INCOME_TAX");
		long paymentBankAccountId = seedBankAccountDirect("032 税款缴纳账户", "6222020960300009876");
		long paymentId = seedTaxPayment(summaryId, "INCOME_TAX", paymentBankAccountId);
		AuthenticatedSession limited = createUserAndLogin("032-tax-limited-", "032_TAX_LIMITED_",
				List.of("financial-close:tax-payment:view"));
		JsonNode payments = data(get(limited, "/api/admin/tax-payments?page=1&pageSize=10"));
		JsonNode payment = pageItemById(payments, paymentId);
		assertThat(payment.get("amount").isNull()).isTrue();
		assertThat(payment.get("referenceNo").isNull()).isTrue();
		assertThat(payment.get("amountVisible").booleanValue()).isFalse();
		assertThat(payment.get("sourceVisible").booleanValue()).isFalse();
		assertThat(payment.get("bankSensitiveVisible").booleanValue()).isFalse();
		assertThat(payment.hasNonNull("accountMasked")).isTrue();
		assertThat(payment.toString()).doesNotContain("6222020960300009876");

		long actionPeriodId = ensureOpenAccountingPeriod(admin, "2097-02");
		JsonNode draftSummary = data(post(admin, "/api/admin/tax-summaries",
				Map.of("periodCode", "2097-02", "taxType", "VAT", "idempotencyKey",
						"032-tax-action-summary-" + SEQUENCE.incrementAndGet())));
		JsonNode calculatedSummary = data(post(admin, "/api/admin/tax-summaries/"
				+ draftSummary.get("id").longValue() + "/calculate",
				Map.of("version", draftSummary.get("version").longValue(), "idempotencyKey",
						"032-tax-action-calculate-" + SEQUENCE.incrementAndGet())));
		assertThat(textArray(calculatedSummary.get("allowedActions"))).containsExactlyInAnyOrder("CALCULATE",
				"ADJUST", "CONFIRM", "GENERATE_VOUCHER");
		assertThat(stringMap(calculatedSummary.get("actionDisabledReasons"))).isEmpty();

		AuthenticatedSession taxViewer = createUserAndLogin("032-tax-viewer-", "032_TAX_VIEWER_",
				List.of("financial-close:tax-summary:view"));
		JsonNode noPermissionSummary = data(get(taxViewer, "/api/admin/tax-summaries/"
				+ calculatedSummary.get("id").longValue()));
		assertThat(textArray(noPermissionSummary.get("allowedActions"))).isEmpty();
		assertThat(stringMap(noPermissionSummary.get("actionDisabledReasons")))
			.containsEntry("CALCULATE", "无权执行税务汇总动作")
			.containsEntry("ADJUST", "无权执行税务汇总动作")
			.containsEntry("CONFIRM", "无权执行税务汇总动作")
			.containsEntry("GENERATE_VOUCHER", "无权执行税务汇总动作");

		JsonNode confirmedSummary = data(post(admin, "/api/admin/tax-summaries/"
				+ calculatedSummary.get("id").longValue() + "/confirm",
				Map.of("version", calculatedSummary.get("version").longValue(), "reason", "确认税额基础汇总",
						"idempotencyKey", "032-tax-action-confirm-" + SEQUENCE.incrementAndGet())));
		makeTaxSummaryStale(confirmedSummary.get("id").longValue());
		JsonNode staleSummary = data(get(admin, "/api/admin/tax-summaries/" + confirmedSummary.get("id").longValue()));
		assertThat(staleSummary.get("stale").booleanValue()).isTrue();
		assertThat(textArray(staleSummary.get("allowedActions"))).isEmpty();
		assertThat(stringMap(staleSummary.get("actionDisabledReasons")))
			.containsEntry("CALCULATE", "来源已变化，请创建新版本")
			.containsEntry("CONFIRM", "来源已变化，请创建新版本");

		long voucherSummaryId = seedCalculatedTaxSummaryWithVoucher(actionPeriodId, "2097-02");
		JsonNode voucherSummary = data(get(admin, "/api/admin/tax-summaries/" + voucherSummaryId));
		assertThat(textArray(voucherSummary.get("allowedActions"))).containsExactly("CONFIRM");
		assertThat(stringMap(voucherSummary.get("actionDisabledReasons")))
			.containsEntry("CALCULATE", "已有凭证草稿，禁止再次修改或生成")
			.containsEntry("ADJUST", "已有凭证草稿，禁止再次修改或生成")
			.containsEntry("GENERATE_VOUCHER", "已有凭证草稿，禁止再次修改或生成");

		long idempotencyPeriodId = ensureOpenAccountingPeriod(admin, "2097-03");
		long idempotencySummaryId = seedConfirmedTaxSummary(idempotencyPeriodId, "2097-03", "VAT");
		long idempotencyBankAccountId = seedBankAccountDirect("032 税款幂等账户", "6222020970300005678");
		long paymentVoucherId = seedPostedTaxPaymentVoucher(idempotencyPeriodId, idempotencySummaryId,
				"032-TAX-PAY-IDEMP-" + SEQUENCE.incrementAndGet());
		String paymentReference = "032-TAX-PAY-IDEMP-REF-" + SEQUENCE.incrementAndGet();
		Map<String, Object> paymentPayload = taxPaymentPayload(idempotencySummaryId, paymentVoucherId,
				idempotencyBankAccountId, paymentReference, "12.34", "032-tax-payment-idem");
		JsonNode firstPayment = data(post(admin, "/api/admin/tax-payments", paymentPayload));
		JsonNode repeatedPayment = data(post(admin, "/api/admin/tax-payments", paymentPayload));
		assertThat(repeatedPayment.get("id").longValue()).isEqualTo(firstPayment.get("id").longValue());
		assertThat(taxPaymentCountByReference(paymentReference)).isOne();
		Map<String, Object> conflictingPaymentPayload = new java.util.LinkedHashMap<>(paymentPayload);
		conflictingPaymentPayload.put("amount", "13.34");
		conflictingPaymentPayload.put("referenceNo", paymentReference + "-DIFF");
		assertError(post(admin, "/api/admin/tax-payments", conflictingPaymentPayload),
				HttpStatus.CONFLICT, "FIN_CLOSE_CONFLICT");
		assertThat(taxPaymentCountBySummary(idempotencySummaryId)).isOne();
	}

	@Test
	@Order(10)
	void 权限失败动作状态和反结账审批追溯必须形成独立验收门禁() throws Exception {
		requireV34Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = seedReadyFinancialClosePeriod(admin, "2097-01");
		JsonNode period = data(get(admin, "/api/admin/financial-closes/periods/" + periodId));
		assertThat(period.has("allowedActions")).isTrue();
		assertThat(period.has("actionDisabledReasons")).isTrue();

		AuthenticatedSession viewer = createUserAndLogin("032-period-viewer-", "032_PERIOD_VIEWER_",
				List.of("financial-close:period:view"));
		assertError(post(viewer, "/api/admin/financial-closes/periods/" + periodId + "/checks",
				Map.of("idempotencyKey", "032-viewer-check-denied-" + periodId)),
				HttpStatus.FORBIDDEN, "FIN_PERMISSION_DENIED");

		JsonNode check = runCheck(admin, periodId, "032-check-reopen-trace-");
		JsonNode glPeriodAfterCheck = pageItemById(data(get(admin,
				"/api/admin/gl/accounting-periods?periodCode=2097-01&page=1&pageSize=10")), periodId);
		assertThat(glPeriodAfterCheck.get("financialCloseStatus").asText()).isEqualTo("READY");
		assertThat(glPeriodAfterCheck.get("latestFinancialCloseCheckRunId").longValue())
			.isEqualTo(check.get("id").longValue());
		assertThat(glPeriodAfterCheck.get("latestCheckRunId").longValue()).isEqualTo(check.get("id").longValue());
		assertThat(glPeriodAfterCheck.get("financialCloseDisabledReason").isNull()).isTrue();
		data(post(admin, "/api/admin/financial-closes/check-runs/" + check.get("id").longValue()
				+ "/close", closePayload(check, "032-close-reopen-trace-" + periodId)));
		JsonNode glPeriodAfterClose = pageItemById(data(get(admin,
				"/api/admin/gl/accounting-periods?periodCode=2097-01&page=1&pageSize=10")), periodId);
		assertThat(glPeriodAfterClose.get("financialCloseStatus").asText()).isEqualTo("CLOSED");
		assertThat(glPeriodAfterClose.get("latestFinancialCloseCheckRunId").longValue())
			.isEqualTo(check.get("id").longValue());
		assertThat(glPeriodAfterClose.get("financialCloseDisabledReason").asText()).isEqualTo("财务期间已关闭");
		ClosedRun closedRun = currentClosedRun(periodId);
		JsonNode reopen = data(post(admin, "/api/admin/financial-closes/close-runs/" + closedRun.id()
				+ "/reopen-requests", Map.of("version", closedRun.version(), "idempotencyKey",
						"032-reopen-trace-" + closedRun.id(), "reason", "验证审批对象追溯")));
		ApprovalTrace approvalTrace = approvalTrace(reopen.get("approvalInstanceId").longValue());
		assertThat(approvalTrace.sceneCode()).isEqualTo("FINANCIAL_PERIOD_REOPEN");
		assertThat(approvalTrace.businessObjectType()).isEqualTo("FIN_CLOSE_REOPEN_REQUEST");
		assertThat(approvalTrace.businessObjectId()).isEqualTo(reopen.get("id").longValue());
		assertThat(reopen.get("closeRunId").longValue()).isEqualTo(closedRun.id());
		assertThat(reopen.get("periodCode").asText()).isEqualTo("2097-01");
	}

	private void requireV34Schema() {
		for (String table : FINANCIAL_CLOSE_TABLES) {
			assertThat(tableExists(table)).as("V34 必须创建 " + table).isTrue();
		}
	}

	private long ensureOpenAccountingPeriod(AuthenticatedSession admin, String periodCode) throws Exception {
		Long existingId = accountingPeriodId(periodCode);
		if (existingId != null) {
			return existingId;
		}
		ensureLedgerInitialized(admin, periodCode.substring(0, 7));
		ResponseEntity<String> response = post(admin, "/api/admin/gl/accounting-periods",
				Map.of("periodCode", periodCode, "idempotencyKey", "032-period-" + periodCode));
		if (response.getStatusCode() == HttpStatus.CONFLICT) {
			Long refreshedId = accountingPeriodId(periodCode);
			if (refreshedId != null) {
				return refreshedId;
			}
			return insertOpenAccountingPeriod(periodCode);
		}
		if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
			return insertOpenAccountingPeriod(periodCode);
		}
		return data(response).get("id").longValue();
	}

	private Long accountingPeriodId(String periodCode) {
		return this.jdbcTemplate.query("""
				select id
				from gl_accounting_period
				where period_code = ?
				order by id desc
				limit 1
				""", (rs) -> rs.next() ? rs.getLong("id") : null, periodCode);
	}

	private long insertOpenAccountingPeriod(String periodCode) {
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
				""", Long.class, ledgerId, periodCode, startDate, startDate.withDayOfMonth(startDate.lengthOfMonth()));
	}

	private void ensureLedgerInitialized(AuthenticatedSession admin, String startYearMonth) throws Exception {
		ResponseEntity<String> response = post(admin, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", startYearMonth, "idempotencyKey", "032-ledger-init"));
		assertThat(response.getStatusCode()).as(response.getBody()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
	}

	private long seedReadyFinancialClosePeriod(AuthenticatedSession admin, String periodCode) throws Exception {
		return seedReadyFinancialClosePeriod(admin, periodCode, true);
	}

	private long seedReadyFinancialClosePeriod(AuthenticatedSession admin, String periodCode,
			boolean closePreviousAccountingPeriods) throws Exception {
		long periodId = ensureOpenAccountingPeriod(admin, periodCode);
		// 只为独立验收构造技术前置；业务状态机仍通过 032 API 完成。
		if (closePreviousAccountingPeriods) {
			closePreviousAccountingPeriods(periodCode);
		}
		insertClosedBusinessPeriod(periodCode);
		seedConfirmedBankTaxAndPostedProfitLoss(periodId, periodCode);
		return periodId;
	}

	private void closePreviousAccountingPeriods(String periodCode) {
		LocalDate startDate = LocalDate.parse(periodCode + "-01");
		this.jdbcTemplate.update("""
				update gl_accounting_period
				set status = 'CLOSED', updated_by = 'test', updated_at = now(), version = version + 1
				where end_date < ?
				and status <> 'CLOSED'
				""", startDate);
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
		this.jdbcTemplate.update("""
				update fin_bank_account
				set status = 'DISABLED', updated_by = 'test', updated_at = now(), version = version + 1
				where status = 'ENABLED'
				""");
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

	private JsonNode runCheck(AuthenticatedSession admin, long periodId, String idempotencyPrefix) throws Exception {
		return data(post(admin, "/api/admin/financial-closes/periods/" + periodId + "/checks",
				Map.of("idempotencyKey", idempotencyPrefix + periodId + "-" + SEQUENCE.incrementAndGet())));
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

	private Map<String, Object> bankAccountPayload(String accountName, long glAccountId, String accountNo) {
		return bankAccountPayload(accountName, glAccountId, accountNo, null);
	}

	private Map<String, Object> taxProfilePayload() {
		Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("taxpayerType", "GENERAL");
		payload.put("creditCode", "913200000000032X9");
		payload.put("taxAuthority", "032 主管税务机关");
		payload.put("vatPeriodicity", "MONTHLY");
		payload.put("incomeTaxRate", "0.25");
		payload.put("urbanMaintenanceRate", "0.07");
		payload.put("educationSurchargeRate", "0.03");
		payload.put("localEducationSurchargeRate", "0.02");
		payload.put("incomeAdjustmentIncrease", "10.00");
		payload.put("incomeAdjustmentDecrease", "2.00");
		payload.put("lossDeduction", "1.00");
		payload.put("prepaidIncomeTax", "3.00");
		payload.put("effectiveFrom", "2096-01-01");
		payload.put("version", 0);
		payload.put("idempotencyKey", "032-tax-profile-contract");
		return payload;
	}

	private Map<String, Object> bankAccountPayload(String accountName, long glAccountId, String accountNo,
			Long version) {
		Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("accountName", accountName);
		payload.put("accountType", "BASIC");
		payload.put("bankName", "032 验收银行");
		payload.put("currency", "CNY");
		payload.put("glAccountId", glAccountId);
		payload.put("openedOn", "2096-10-01");
		payload.put("accountNo", accountNo);
		payload.put("idempotencyKey", "032-bank-account-" + SEQUENCE.incrementAndGet());
		if (version != null) {
			payload.put("version", version);
		}
		return payload;
	}

	private long ensureBankAccountChild(String code, String name, String category, String balanceDirection,
			boolean leaf, boolean postable, boolean enabled) {
		Long parentId = accountId("1002");
		this.jdbcTemplate.update("update gl_account set is_leaf = false where id = ?", parentId);
		return this.jdbcTemplate.queryForObject("""
				insert into gl_account (
					ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable,
					enabled, template_source, created_by, created_at, updated_by, updated_at
				)
				select ledger_id, id, ?, ?, ?, ?, level_no + 1, ?, ?, ?, 'TEST_STAGE032',
					'test', now(), 'test', now()
				from gl_account
				where id = ?
				on conflict (ledger_id, code) do update
				set name = excluded.name,
				    category = excluded.category,
				    balance_direction = excluded.balance_direction,
				    is_leaf = excluded.is_leaf,
				    postable = excluded.postable,
				    enabled = excluded.enabled,
				    updated_at = now()
				returning id
				""", Long.class, code, name, category, balanceDirection, leaf, postable, enabled, parentId);
	}

	private void breakTrialBalanceCache(long periodId) {
		int voucherNumber = 900000 + SEQUENCE.incrementAndGet();
		String voucherNo = "记-032-UNBALANCED-" + periodId;
		Long voucherId = this.jdbcTemplate.queryForObject("""
				insert into gl_voucher (
					ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status, summary,
					source_type, source_fingerprint, source_version, source_payload, currency, debit_total,
					credit_total, created_by, created_at, updated_by, updated_at
				)
				select ledger_id, id, ?, 'GENERAL', end_date, 'DRAFT', '032 试算不平技术样例',
					'MANUAL', ?, 0, '{}'::jsonb, 'CNY', 3.21, 0, 'test', now(), 'test', now()
				from gl_accounting_period
				where id = ?
				returning id
				""", Long.class, "032-UNBALANCED-" + periodId + "-" + SEQUENCE.incrementAndGet(),
				"032-unbalanced-" + periodId, periodId);
		Long lineId = this.jdbcTemplate.queryForObject("""
				insert into gl_voucher_line (
					voucher_id, line_no, summary, account_id, account_code, account_name, account_category,
					account_balance_direction, debit_amount, credit_amount, created_at
				)
				select ?, 1, '032 试算不平借方行', a.id, a.code, a.name, a.category, a.balance_direction,
					3.21, 0, now()
				from gl_account a
				where a.code = '1002'
				returning id
				""", Long.class, voucherId);
		this.jdbcTemplate.update("""
				update gl_voucher
				set status = 'POSTED',
				    voucher_word = '记',
				    voucher_number = ?,
				    voucher_no = ?,
				    posted_by = 'test',
				    posted_at = now(),
				    updated_at = now()
				where id = ?
				""", voucherNumber, voucherNo, voucherId);
		this.jdbcTemplate.update("""
				insert into gl_ledger_entry (
					ledger_id, period_id, voucher_id, voucher_line_id, voucher_date, voucher_no, voucher_word,
					voucher_number, line_no, summary, account_id, account_code, account_name, balance_direction,
					voucher_type, debit_amount, credit_amount, auxiliary_snapshot, source_type, source_route,
					posted_by, posted_at, created_at
				)
				select p.ledger_id, p.id, ?, ?, p.end_date, ?, '记', ?, 1, '032 试算不平借方分录',
					a.id, a.code, a.name, a.balance_direction, 'GENERAL', 3.21, 0, '[]'::jsonb, 'MANUAL',
					'{}'::jsonb, 'test', now(), now()
				from gl_accounting_period p
				join gl_account a on a.ledger_id = p.ledger_id and a.code = '1002'
				where p.id = ?
				""", voucherId, lineId, voucherNo, voucherNumber, periodId);
	}

	private void seedDraftTaxVoucher(long periodId, String periodCode) {
		Long voucherId = this.jdbcTemplate.queryForObject("""
				insert into gl_voucher (
					ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status, summary,
					source_type, source_id, source_fingerprint, source_version, source_payload, currency,
					debit_total, credit_total, created_by, created_at, updated_by, updated_at
				)
				select ledger_id, id, ?, 'GENERAL', end_date, 'DRAFT', ?, 'TAX_SUMMARY', ?, ?, 0,
					'{}'::jsonb, 'CNY', 0, 0, 'test', now(), 'test', now()
				from gl_accounting_period
				where id = ?
				returning id
				""", Long.class, "032-TAX-DRAFT-" + periodCode + "-" + SEQUENCE.incrementAndGet(),
				periodCode + " 税费计提未记账草稿", null, "032-tax-draft-" + periodCode, periodId);
		this.jdbcTemplate.update("""
				insert into fin_tax_period_summary (
					period_id, period_code, tax_type, status, source_fingerprint, vat_payable,
					urban_maintenance_tax, income_tax_estimated, disclaimer, current_flag, voucher_id,
					created_by, updated_by
				)
				values (?, ?, 'VAT', 'CONFIRMED', ?, 12.34, 0, 0, ?, true, ?, 'test', 'test')
				""", periodId, periodCode, "032-tax-voucher-unposted-" + periodCode,
				FinancialCloseSupport.TAX_DISCLAIMER, voucherId);
	}

	private List<String> taxRateRuleCodes() {
		return this.jdbcTemplate.queryForList("""
				select rate_code
				from fin_tax_rate_rule
				where status = 'ENABLED'
				order by rate_code
				""", String.class);
	}

	private List<String> taxInvoiceTypeNames() {
		return this.jdbcTemplate.queryForList("""
				select name
				from fin_tax_invoice_type
				where status = 'ENABLED'
				order by code
				""", String.class);
	}

	private List<String> taxAdjustmentTypes() {
		String definition = this.jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(oid)
				from pg_constraint
				where conname = 'ck_fin_tax_adjustment_type'
				""", String.class);
		return List.of("OUTPUT_INCREASE", "OUTPUT_DECREASE", "INPUT_INCREASE", "INPUT_DECREASE")
			.stream()
			.filter(definition::contains)
			.toList();
	}

	private long seedConfirmedTaxSummary(long periodId, String periodCode, String taxType) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_tax_period_summary (
					period_id, period_code, tax_type, status, source_fingerprint, output_vat, input_vat,
					transfer_out_vat, adjustment_amount, opening_credit_vat, vat_payable,
					urban_maintenance_tax, ending_credit_vat, income_tax_estimated, disclaimer, current_flag,
					created_by, updated_by
				)
				values (?, ?, ?, 'CONFIRMED', ?, 0, 0, 0, 0, 0, 0, 0, 0, 12.34, ?, true, 'test', 'test')
				returning id
				""", Long.class, periodId, periodCode, taxType, "032-tax-payment-" + periodCode,
				FinancialCloseSupport.TAX_DISCLAIMER);
	}

	private long seedBankAccountDirect(String accountName, String accountNo) {
		String last4 = accountNo.substring(accountNo.length() - 4);
		return this.jdbcTemplate.queryForObject("""
				insert into fin_bank_account (
					account_name, account_type, bank_name, currency, gl_account_id, account_fingerprint,
					account_last4, account_masked, status, opened_on, created_by, updated_by
				)
				values (?, 'BASIC', '032 验收银行', 'CNY', ?, ?, ?, ?, 'ENABLED', date '2096-12-01',
					'test', 'test')
				returning id
				""", Long.class, accountName, accountId("1002"),
				FinancialCloseSupport.sha256("BANK_ACCOUNT|" + accountNo), last4, "****" + last4);
	}

	private long seedTaxPayment(long summaryId, String taxType, long bankAccountId) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_tax_payment_record (
					summary_id, tax_type, payment_date, amount, payment_method, reference_no, bank_account_id,
					reason, status, created_by, updated_by
				)
				values (?, ?, date '2096-12-20', 12.34, 'BANK', '032-TAX-PAY-REF', ?,
					'032 税款脱敏验收', 'RECORDED', 'test', 'test')
				returning id
				""", Long.class, summaryId, taxType, bankAccountId);
	}

	private void makeTaxSummaryStale(long summaryId) {
		String periodCode = this.jdbcTemplate.queryForObject("""
				select period_code
				from fin_tax_period_summary
				where id = ?
				""", String.class, summaryId);
		int suffix = SEQUENCE.incrementAndGet();
		LocalDate invoiceDate = LocalDate.parse(periodCode + "-12");
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "032-STALE-CUS-" + suffix, "032 失效客户" + suffix);
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
				""", "032-STALE-SI-" + suffix, customerId, 97000 + suffix,
				"032-STALE-SHIP-" + suffix, invoiceDate, invoiceDate.plusMonths(1));
	}

	private long seedCalculatedTaxSummaryWithVoucher(long periodId, String periodCode) {
		long summaryId = this.jdbcTemplate.queryForObject("""
				insert into fin_tax_period_summary (
					period_id, period_code, tax_type, status, source_fingerprint, output_vat, input_vat,
					vat_payable, urban_maintenance_tax, disclaimer, current_flag, created_by, updated_by
				)
				values (?, ?, 'VAT', 'CALCULATED', ?, 100.00, 20.00, 80.00, 5.60, ?, true, 'test', 'test')
				returning id
				""", Long.class, periodId, periodCode, taxSourceFingerprint(periodId, periodCode),
				FinancialCloseSupport.TAX_DISCLAIMER);
		long voucherId = seedPostedTaxPaymentVoucher(periodId, summaryId,
				"032-TAX-SUMMARY-VOUCHER-" + SEQUENCE.incrementAndGet());
		this.jdbcTemplate.update("""
				update fin_tax_period_summary
				set voucher_id = ?, updated_by = 'test', updated_at = now(), version = version + 1
				where id = ?
				""", voucherId, summaryId);
		return summaryId;
	}

	private long seedPostedTaxPaymentVoucher(long periodId, long sourceId, String voucherNo) {
		int voucherNumber = 910000 + SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into gl_voucher (
					ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status, summary,
					source_type, source_id, source_fingerprint, source_version, source_payload, currency,
					debit_total, credit_total, voucher_word, voucher_number, voucher_no, posted_by, posted_at,
					created_by, created_at, updated_by, updated_at
				)
				select ledger_id, id, ?, 'GENERAL', end_date, 'POSTED', ?,
					'TAX_SUMMARY', ?, ?, 0, '{}'::jsonb, 'CNY',
					0, 0, '记', ?, ?, 'test', now(), 'test', now(), 'test', now()
				from gl_accounting_period
				where id = ?
				returning id
				""", Long.class, "032-TAX-PAY-DRAFT-" + SEQUENCE.incrementAndGet(), voucherNo,
				sourceId, "032-tax-payment-voucher-" + sourceId + "-" + voucherNo, voucherNumber, voucherNo,
				periodId);
	}

	private Map<String, Object> taxPaymentPayload(long summaryId, long voucherId, long bankAccountId,
			String referenceNo, String amount, String idempotencyKey) {
		Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("summaryId", summaryId);
		payload.put("taxType", "VAT");
		payload.put("paymentDate", "2097-03-20");
		payload.put("amount", amount);
		payload.put("paymentMethod", "BANK");
		payload.put("referenceNo", referenceNo);
		payload.put("voucherId", voucherId);
		payload.put("bankAccountId", bankAccountId);
		payload.put("reason", "记录税款缴纳");
		payload.put("idempotencyKey", idempotencyKey);
		return payload;
	}

	private long taxPaymentCountByReference(String referenceNo) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_tax_payment_record
				where reference_no = ?
				""", Long.class, referenceNo);
	}

	private long taxPaymentCountBySummary(long summaryId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_tax_payment_record
				where summary_id = ?
				""", Long.class, summaryId);
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

	private List<String> bankStatementPermissionRoutes() {
		return this.jdbcTemplate.queryForList("""
				select code || '|' || route_path || '|' || coalesce(api_method, '') || '|' || coalesce(api_path, '')
				from sys_permission
				where code in ('financial-close:bank-reconciliation:view',
					'financial-close:bank-reconciliation:import')
				and type = 'ACTION'
				order by code
				""", String.class);
	}

	private Map<String, String> taxAccountNames() {
		return this.jdbcTemplate.query("""
				select code, name
				from gl_account
				where code in ('4103', '2221.03', '2221.04', '2221.05', '2221.06', '6403', '6801')
				""", (rs) -> {
			Map<String, String> result = new java.util.LinkedHashMap<>();
			while (rs.next()) {
				result.put(rs.getString("code"), rs.getString("name"));
			}
			return result;
		});
	}

	private List<String> bankExceptionTypes() {
		String definition = this.jdbcTemplate.queryForObject("""
				select pg_get_constraintdef(oid)
				from pg_constraint
				where conname = 'ck_fin_bank_reconciliation_exception_type'
				""", String.class);
		return FROZEN_BANK_EXCEPTION_TYPES.stream().filter(definition::contains).toList();
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

	private ApprovalTrace approvalTrace(long approvalInstanceId) {
		return this.jdbcTemplate.queryForObject("""
				select scene_code, business_object_type, business_object_id
				from platform_approval_instance
				where id = ?
				""", (rs, rowNum) -> new ApprovalTrace(rs.getString("scene_code"),
				rs.getString("business_object_type"), rs.getLong("business_object_id")), approvalInstanceId);
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

	private boolean frontendFinancialCloseRoutesAreFrozen() throws Exception {
		java.nio.file.Path workingDirectory = java.nio.file.Path.of(System.getProperty("user.dir"));
		java.nio.file.Path menuPath = workingDirectory.resolve("../../apps/web/src/navigation/financialCloseMenu.ts")
			.normalize();
		java.nio.file.Path routePath = workingDirectory.resolve("../../apps/web/src/router/modules/financialCloseRoutes.ts")
			.normalize();
		if (!java.nio.file.Files.exists(menuPath)) {
			menuPath = workingDirectory.resolve("apps/web/src/navigation/financialCloseMenu.ts").normalize();
		}
		if (!java.nio.file.Files.exists(routePath)) {
			routePath = workingDirectory.resolve("apps/web/src/router/modules/financialCloseRoutes.ts").normalize();
		}
		String menu = java.nio.file.Files.readString(menuPath);
		String routes = java.nio.file.Files.readString(routePath);
		List<String> expectedPaths = List.of("/gl/financial-close", "/gl/profit-loss-carryforward",
				"/gl/bank-accounts", "/gl/bank-statements", "/gl/bank-reconciliation", "/gl/tax-settings",
				"/gl/tax-summary", "/gl/tax-payments");
		return expectedPaths.stream().allMatch((path) -> menu.contains(path) && routes.contains(path))
				&& routes.contains("/gl/financial-close/:runId");
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

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.PUT,
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

	private void assertConflictCodeIn(ResponseEntity<String> response, List<String> expectedCodes) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(code(response)).isIn(expectedCodes);
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

	private void assertMandatoryCheckCodes(JsonNode check) {
		assertThat(recursiveValues(check, "checkCode")).containsExactlyInAnyOrderElementsOf(
				MANDATORY_FINANCIAL_CLOSE_CHECKS);
	}

	private void assertCheckFailed(JsonNode check, String checkCode) {
		List<JsonNode> items = new ArrayList<>();
		collectObjectsWithFieldValue(check, "checkCode", checkCode, items);
		assertThat(items).as("必须存在检查项 " + checkCode).hasSize(1);
		assertThat(items.get(0).get("passed").booleanValue()).as(checkCode + " 必须失败").isFalse();
	}

	private void assertCheckPassed(JsonNode check, String checkCode) {
		List<JsonNode> items = new ArrayList<>();
		collectObjectsWithFieldValue(check, "checkCode", checkCode, items);
		assertThat(items).as("必须存在检查项 " + checkCode).hasSize(1);
		assertThat(items.get(0).get("passed").booleanValue()).as(checkCode + " 必须通过").isTrue();
	}

	private void collectObjectsWithFieldValue(JsonNode node, String fieldName, String expectedValue,
			List<JsonNode> values) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isObject()) {
			if (node.has(fieldName) && expectedValue.equals(node.get(fieldName).asText())) {
				values.add(node);
			}
			node.forEach((child) -> collectObjectsWithFieldValue(child, fieldName, expectedValue, values));
		}
		else if (node.isArray()) {
			node.forEach((child) -> collectObjectsWithFieldValue(child, fieldName, expectedValue, values));
		}
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

	private List<String> textArray(JsonNode node) {
		List<String> values = new ArrayList<>();
		if (node != null && node.isArray()) {
			node.forEach((item) -> values.add(item.asText()));
		}
		return values;
	}

	private Map<String, String> stringMap(JsonNode node) {
		Map<String, String> values = new java.util.LinkedHashMap<>();
		if (node != null && node.isObject()) {
			node.properties().forEach((entry) -> values.put(entry.getKey(), entry.getValue().asText()));
		}
		return values;
	}

	private JsonNode pageItemById(JsonNode page, long id) {
		JsonNode items = page.get("items");
		assertThat(items).as("分页结果必须包含 items").isNotNull();
		for (JsonNode item : items) {
			if (item.has("id") && item.get("id").longValue() == id) {
				return item;
			}
		}
		throw new AssertionError("分页结果缺少 ID " + id);
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

	private record ApprovalTrace(String sceneCode, String businessObjectType, long businessObjectId) {
	}

}
