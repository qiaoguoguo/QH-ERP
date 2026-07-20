package com.qherp.api.system.gl;

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
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=stage031-general-ledger")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeneralLedgerStage031AcceptanceTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final String START_PERIOD = "2091-01";

	private static final LocalDate START_DATE = LocalDate.of(2091, 1, 1);

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final List<String> GL_TABLES = List.of("gl_ledger", "gl_accounting_period", "gl_account",
			"gl_aux_dimension", "gl_aux_item", "gl_account_aux_requirement", "gl_posting_rule",
			"gl_posting_rule_line", "gl_posting_rule_line_aux_map", "gl_voucher", "gl_voucher_line",
			"gl_voucher_line_auxiliary", "gl_voucher_source_claim", "gl_voucher_number_sequence",
			"gl_ledger_entry", "gl_account_period_total", "gl_voucher_reversal_link", "gl_action_idempotency",
			"gl_audit_event");

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
	void 账簿初始化必须单账簿人民币期间顺序且同起始月幂等不同起始月冲突() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		JsonNode ledger = ensureLedgerInitialized(admin);
		JsonNode repeated = data(post(admin, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", START_PERIOD, "idempotencyKey", "031-ledger-init-main")));
		assertThat(repeated.get("id").longValue()).isEqualTo(ledger.get("id").longValue());
		assertThat(ledger.get("code").asText()).isEqualTo("MAIN");
		assertThat(ledger.get("currency").asText()).isEqualTo("CNY");
		assertThat(tableCount("gl_ledger")).isOne();

		assertError(post(admin, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2090-12", "idempotencyKey", "031-ledger-init-conflict")),
				HttpStatus.CONFLICT, "GL_LEDGER_ALREADY_INITIALIZED");

		JsonNode periods = data(get(admin, "/api/admin/gl/accounting-periods?periodCode=" + START_PERIOD
				+ "&page=1&pageSize=20"));
		assertThat(periodCodes(periods)).contains(START_PERIOD);
		JsonNode firstPeriod = findItem(periods.get("items"), "periodCode", START_PERIOD);
		assertThat(firstPeriod.get("status").asText()).isEqualTo("OPEN");
		assertThat(firstPeriod.get("startDate").asText()).isEqualTo("2091-01-01");
		assertThat(firstPeriod.get("endDate").asText()).isEqualTo("2091-01-31");

		JsonNode nextPeriod = data(post(admin, "/api/admin/gl/accounting-periods",
				Map.of("periodCode", "2091-02", "idempotencyKey", "031-period-next-209102")));
		assertThat(nextPeriod.get("periodCode").asText()).isEqualTo("2091-02");
		assertThat(nextPeriod.get("status").asText()).isEqualTo("OPEN");
	}

	@Test
	@Order(2)
	void 科目辅助核算必须校验父子末级启用必填辅助和多余辅助() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode customerAuxParent = data(post(admin, "/api/admin/gl/accounts",
				accountPayload(null, "1131.AUXP" + SEQUENCE.incrementAndGet(), "031 客户辅助测试父科目",
						"ASSET", "DEBIT", false, List.of())));
		long parentReceivable = customerAuxParent.get("id").longValue();
		long bankAccount = accountId(admin, "1002");
		long revenueAccount = accountId(admin, "6001");
		long customerId = insertCustomer("031_AUX_CUS_" + SEQUENCE.incrementAndGet());
		JsonNode child = data(post(admin, "/api/admin/gl/accounts",
				accountPayload(parentReceivable, customerAuxParent.get("code").asText() + ".01",
						"031 客户辅助应收",
						"ASSET", "DEBIT", true,
						List.of(Map.of("dimensionCode", "CUSTOMER", "requirementType", "REQUIRED")))));
		long customerAccount = child.get("id").longValue();
		assertThat(child.get("parentId").longValue()).isEqualTo(parentReceivable);
		assertThat(child.get("postable").booleanValue()).isTrue();
		assertThat(child.get("auxiliaryRequirements").toString()).contains("CUSTOMER", "REQUIRED");

		assertError(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", START_DATE.plusDays(2), "031 缺少客户辅助",
						List.of(line(1, customerAccount, "031 缺少客户辅助", "10.00", null, List.of()),
								line(2, revenueAccount, "031 缺少客户辅助", null, "10.00", List.of())))),
				HttpStatus.BAD_REQUEST, "GL_AUXILIARY_REQUIRED");

		JsonNode voucher = data(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", START_DATE.plusDays(3), "031 客户辅助完整",
						List.of(line(1, customerAccount, "031 客户辅助完整", "10.00", null,
								List.of(systemAuxiliary("CUSTOMER", customerId))),
								line(2, revenueAccount, "031 客户辅助完整", null, "10.00", List.of())))));
		assertThat(voucher.get("status").asText()).isEqualTo("DRAFT");
		assertThat(recursiveValues(voucher, "dimensionCode")).contains("CUSTOMER");

		assertError(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", START_DATE.plusDays(4), "031 银行科目不得带客户辅助",
						List.of(line(1, bankAccount, "031 银行科目多余辅助", "8.00", null,
								List.of(systemAuxiliary("CUSTOMER", customerId))),
								line(2, revenueAccount, "031 银行科目多余辅助", null, "8.00", List.of())))),
				HttpStatus.BAD_REQUEST, "GL_AUXILIARY_NOT_ALLOWED");
	}

	@Test
	@Order(3)
	void 期初凭证必须走审批只计期初且普通凭证记账后拒绝新增期初() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		long bankAccount = accountId(admin, "1002");
		long equityAccount = accountId(admin, "4001");
		JsonNode opening = data(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("OPENING", START_DATE, "031 启用期初",
						List.of(line(1, bankAccount, "031 期初银行", "50.00", null, List.of()),
								line(2, equityAccount, "031 期初权益", null, "50.00", List.of())))));
		assertThat(opening.get("voucherType").asText()).isEqualTo("OPENING");
		assertThat(opening.get("status").asText()).isEqualTo("DRAFT");

		JsonNode postedOpening = submitAndApprove(admin, opening, "031-opening");
		assertThat(postedOpening.get("status").asText()).isEqualTo("POSTED");
		JsonNode trialBalance = data(get(admin, "/api/admin/gl/trial-balance?periodCode=" + START_PERIOD));
		assertThat(trialBalance.get("opening").get("balanced").booleanValue()).isTrue();
		assertThat(trialBalance.get("period").get("balanced").booleanValue()).isTrue();
		assertThat(trialBalance.get("ending").get("balanced").booleanValue()).isTrue();

		ResponseEntity<String> secondOpening = post(admin, "/api/admin/gl/vouchers",
				voucherPayload("OPENING", START_DATE, "031 第二张期初应拒绝",
						List.of(line(1, bankAccount, "031 第二期初", "1.00", null, List.of()),
								line(2, equityAccount, "031 第二期初", null, "1.00", List.of()))));
		assertThat(secondOpening.getStatusCode()).as(secondOpening.getBody()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(code(secondOpening)).isNotEqualTo("OK");
	}

	@Test
	@Order(4)
	void 手工凭证必须校验借贷平衡开放期间和提交版本() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		long bankAccount = accountId(admin, "1002");
		long revenueAccount = accountId(admin, "6001");

		assertError(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", START_DATE.plusDays(5), "031 借贷不平",
						List.of(line(1, bankAccount, "031 借贷不平", "11.00", null, List.of()),
								line(2, revenueAccount, "031 借贷不平", null, "10.00", List.of())))),
				HttpStatus.BAD_REQUEST, "GL_VOUCHER_UNBALANCED");

		assertError(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", LocalDate.of(2090, 12, 31), "031 不在开放期间",
						List.of(line(1, bankAccount, "031 非开放期间", "10.00", null, List.of()),
								line(2, revenueAccount, "031 非开放期间", null, "10.00", List.of())))),
				HttpStatus.CONFLICT, "GL_PERIOD_NOT_OPEN");

		JsonNode voucher = createManualDraft(admin, "031 有效草稿", "12.00");
		assertThat(voucher.get("status").asText()).isEqualTo("DRAFT");
		assertThat(voucher.get("voucherNo").isNull()).isTrue();
		assertThat(actionCodes(voucher.get("allowedActions"))).contains("SUBMIT", "CANCEL");
		assertError(post(admin, "/api/admin/gl/vouchers/" + voucher.get("id").longValue() + "/submit",
				actionPayload(voucher.get("version").longValue() - 1, "031-submit-stale-" + voucher.get("id"))),
				HttpStatus.CONFLICT, "GL_VERSION_CONFLICT");
	}

	@Test
	@Order(5)
	void 最终审批必须双人控制连续编号原子写入总账明细余额和三组试算() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		long beforeSequence = voucherSequence(START_PERIOD);
		JsonNode first = createManualDraft(admin, "031 连续编号一", "21.00");
		JsonNode second = createManualDraft(admin, "031 连续编号二", "22.00");

		JsonNode submitted = data(post(admin, "/api/admin/gl/vouchers/" + first.get("id").longValue() + "/submit",
				actionPayload(first.get("version").longValue(), "031-submit-" + first.get("id"))));
		long firstTaskId = approvalTaskIdForVoucher(first.get("id").longValue());
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(submitted.get("approvalSummary").get("sceneCode").asText()).isEqualTo("GL_VOUCHER_POST");
		assertError(post(admin, "/api/admin/approval-tasks/" + firstTaskId + "/approve",
				approvalPayload(taskVersion(firstTaskId), "031-self-approve-" + firstTaskId)),
				HttpStatus.FORBIDDEN, "GL_APPROVAL_SELF_FORBIDDEN");

		AuthenticatedSession approver = createUserAndLogin("031-approver-", "031_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"gl:voucher:view", "gl:voucher:approve-post"));
		JsonNode postedFirst = approveVoucher(approver, first.get("id").longValue(), firstTaskId,
				"031-approve-first-");
		assertThat(postedFirst.get("status").asText()).isEqualTo("POSTED");
		assertThat(postedFirst.get("voucherNo").asText()).matches("记-209101-\\d{4}");
		assertThat(voucherNumber(postedFirst)).isEqualTo(beforeSequence + 1);
		assertPostedLedgerFacts(postedFirst.get("id").longValue(), "21.00");

		JsonNode submittedSecond = data(post(admin,
				"/api/admin/gl/vouchers/" + second.get("id").longValue() + "/submit",
				actionPayload(second.get("version").longValue(), "031-submit-" + second.get("id"))));
		long secondTaskId = approvalTaskIdForVoucher(second.get("id").longValue());
		assertThat(submittedSecond.get("status").asText()).isEqualTo("SUBMITTED");
		JsonNode postedSecond = approveVoucher(approver, second.get("id").longValue(), secondTaskId,
				"031-approve-second-");
		assertThat(voucherNumber(postedSecond)).isEqualTo(beforeSequence + 2);
		assertPostedLedgerFacts(postedSecond.get("id").longValue(), "22.00");

		JsonNode generalLedger = data(get(admin,
				"/api/admin/gl/ledgers/general?periodCode=" + START_PERIOD + "&page=1&pageSize=20"));
		assertThat(generalLedger.get("items").size()).isGreaterThanOrEqualTo(2);
		JsonNode detailLedger = data(get(admin, "/api/admin/gl/ledgers/detail?periodCode=" + START_PERIOD
				+ "&voucherNo=" + postedFirst.get("voucherNo").asText() + "&page=1&pageSize=20"));
		assertThat(detailLedger.get("items").size()).isEqualTo(2);
		JsonNode balances = data(get(admin, "/api/admin/gl/account-balances?periodCode=" + START_PERIOD
				+ "&page=1&pageSize=20"));
		assertThat(balances.get("items").size()).isGreaterThanOrEqualTo(2);
		JsonNode trialBalance = data(get(admin, "/api/admin/gl/trial-balance?periodCode=" + START_PERIOD));
		assertThat(trialBalance.get("opening").get("balanced").booleanValue()).isTrue();
		assertThat(trialBalance.get("period").get("balanced").booleanValue()).isTrue();
		assertThat(trialBalance.get("ending").get("balanced").booleanValue()).isTrue();
	}

	@Test
	@Order(6)
	void 审批回调失败不得消耗正式号不得半写账簿并必须写错误审计() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode failureParent = data(post(admin, "/api/admin/gl/accounts",
				accountPayload(null, "1131.FAIL" + SEQUENCE.incrementAndGet(), "031 审批失败测试父科目",
						"ASSET", "DEBIT", false, List.of())));
		long parentAsset = failureParent.get("id").longValue();
		long revenueAccount = accountId(admin, "6001");
		JsonNode temporaryAccount = data(post(admin, "/api/admin/gl/accounts",
				accountPayload(parentAsset, failureParent.get("code").asText() + ".01", "031 审批失败临时科目",
						"ASSET", "DEBIT", true, List.of())));
		long temporaryAccountId = temporaryAccount.get("id").longValue();
		JsonNode voucher = data(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", START_DATE.plusDays(8), "031 审批回调失败",
						List.of(line(1, temporaryAccountId, "031 回调失败", "18.00", null, List.of()),
								line(2, revenueAccount, "031 回调失败", null, "18.00", List.of())))));
		JsonNode submitted = data(post(admin, "/api/admin/gl/vouchers/" + voucher.get("id").longValue()
				+ "/submit", actionPayload(voucher.get("version").longValue(), "031-submit-fail-"
						+ voucher.get("id"))));
		long taskId = approvalTaskIdForVoucher(voucher.get("id").longValue());
		long beforeSequence = voucherSequence(START_PERIOD);
		this.jdbcTemplate.update("update gl_account set enabled = false, updated_at = now() where id = ?",
				temporaryAccountId);
		AuthenticatedSession approver = createUserAndLogin("031-fail-approver-", "031_FAIL_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"gl:voucher:view", "gl:voucher:approve-post"));

		assertError(post(approver, "/api/admin/approval-tasks/" + taskId + "/approve",
				approvalPayload(taskVersion(taskId), "031-approve-fail-" + taskId)),
				HttpStatus.CONFLICT, "GL_ACCOUNT_DISABLED");

		assertThat(voucherStatus(voucher.get("id").longValue())).isEqualTo("SUBMITTED");
		assertThat(voucherSequence(START_PERIOD)).isEqualTo(beforeSequence);
		assertThat(ledgerEntryCount(voucher.get("id").longValue())).isZero();
		assertThat(auditErrorCount("GL_ACCOUNT_DISABLED", voucher.get("id").longValue())).isGreaterThanOrEqualTo(1);
		assertThat(submitted.get("approvalSummary").get("status").asText()).isEqualTo("SUBMITTED");
	}

	@Test
	@Order(7)
	void posted凭证分录和账簿数据库守卫必须禁止修改删除和补写() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode posted = createAndPostManualVoucher(admin, "031 不可变守卫", "31.00");
		long voucherId = posted.get("id").longValue();
		long lineId = voucherLineId(voucherId);
		long ledgerEntryId = ledgerEntryId(voucherId);

		assertThatThrownBy(() -> this.jdbcTemplate.update("update gl_voucher set summary = '031 篡改' where id = ?",
				voucherId)).as("POSTED gl_voucher 不可更新").isInstanceOf(Exception.class);
		assertThatThrownBy(() -> this.jdbcTemplate.update("update gl_voucher_line set summary = '031 篡改' where id = ?",
				lineId)).as("POSTED gl_voucher_line 不可更新").isInstanceOf(Exception.class);
		assertThatThrownBy(() -> this.jdbcTemplate.update("delete from gl_voucher_line where id = ?", lineId))
			.as("POSTED gl_voucher_line 不可删除")
			.isInstanceOf(Exception.class);
		assertThatThrownBy(() -> this.jdbcTemplate.update("update gl_ledger_entry set debit_amount = 999 where id = ?",
				ledgerEntryId)).as("gl_ledger_entry 不可更新").isInstanceOf(Exception.class);
		assertThatThrownBy(() -> this.jdbcTemplate.update("delete from gl_ledger_entry where id = ?", ledgerEntryId))
			.as("gl_ledger_entry 不可删除")
			.isInstanceOf(Exception.class);
		assertThat(voucherStatus(voucherId)).isEqualTo("POSTED");
		assertThat(ledgerEntryCount(voucherId)).isEqualTo(2);
	}

	@Test
	@Order(8)
	void 冲销必须新建反向草稿重新审批且原凭证不变只允许一个有效冲销() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode posted = createAndPostManualVoucher(admin, "031 冲销原凭证", "41.00");
		long originalId = posted.get("id").longValue();
		String beforeOriginal = rowSummary("select * from gl_voucher where id = " + originalId);

		JsonNode reversal = data(post(admin, "/api/admin/gl/vouchers/" + originalId + "/reversals",
				reversalPayload(posted.get("version").longValue(), START_DATE.plusDays(12), "031 冲销原因",
						"031-reversal-" + originalId)));
		assertThat(reversal.get("status").asText()).isEqualTo("DRAFT");
		assertThat(reversal.get("sourceType").asText()).isEqualTo("REVERSAL");
		assertDecimal(reversal, "debitTotal", "41.00");
		assertDecimal(reversal, "creditTotal", "41.00");
		assertThat(reversal.get("reversalSummary").get("originalVoucherId").longValue()).isEqualTo(originalId);
		assertThat(recursiveValues(reversal.get("lines"), "debitAmount")).contains("41.00");
		assertThat(recursiveValues(reversal.get("lines"), "creditAmount")).contains("41.00");
		assertError(post(admin, "/api/admin/gl/vouchers/" + originalId + "/reversals",
				reversalPayload(posted.get("version").longValue(), START_DATE.plusDays(12), "031 重复冲销",
						"031-reversal-duplicate-" + originalId)),
				HttpStatus.CONFLICT, "GL_REVERSAL_ALREADY_EXISTS");

		JsonNode postedReversal = submitAndApprove(admin, reversal, "031-reversal-post");
		assertThat(postedReversal.get("status").asText()).isEqualTo("POSTED");
		assertThat(rowSummary("select * from gl_voucher where id = " + originalId)).isEqualTo(beforeOriginal);
		assertThat(reversalLinkCount(originalId)).isOne();
		assertThat(ledgerEntryCount(originalId)).isEqualTo(2);
		assertThat(ledgerEntryCount(postedReversal.get("id").longValue())).isEqualTo(2);
	}

	@Test
	@Order(9)
	void 六类028Ready草稿转换必须重读来源不复制分类建议且不回写028草稿() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		List<FinanceDraftFixture> drafts = List.of(insertReadyFinanceDraft("SALES_INVOICE", "113.00"),
				insertReadyFinanceDraft("PURCHASE_INVOICE", "90.40"), insertReadyFinanceDraft("EXPENSE", "45.20"),
				insertReadyFinanceDraft("RECEIPT", "60.00"), insertReadyFinanceDraft("PAYMENT", "70.00"),
				insertReadyFinanceDraft("SETTLEMENT_ALLOCATION", "30.00"));

		for (FinanceDraftFixture draft : drafts) {
			String beforeDraft = financeVoucherDraftSummary(draft.draftId());
			JsonNode voucher = convertFinanceDraft(admin, draft, "031-convert-" + draft.draftId());
			assertThat(voucher.get("status").asText()).isEqualTo("DRAFT");
			assertThat(voucher.get("sourceType").asText()).isEqualTo("FIN_VOUCHER_DRAFT");
			assertThat(voucher.get("sourceId").longValue()).isEqualTo(draft.draftId());
			assertDecimal(voucher, "debitTotal", draft.amount());
			assertDecimal(voucher, "creditTotal", draft.amount());
			assertThat(voucher.get("lines").size()).isGreaterThanOrEqualTo(2);
			assertThat(recursiveValues(voucher.get("lines"), "accountCode")).allSatisfy((code) ->
					assertThat(code).as(draft.sourceType()).isNotBlank());
			assertThat(recursiveValues(voucher.get("lines"), "normalizedFactCode")).allSatisfy((code) ->
					assertThat(code).as(draft.sourceType()).isNotBlank());
			assertThat(recursiveValues(voucher.get("lines"), "businessCategory")).isEmpty();
			assertThat(activeSourceClaimCount(draft.sourceType(), draft.sourceId())).isOne();
			assertThat(financeVoucherDraftSummary(draft.draftId())).isEqualTo(beforeDraft);
		}
	}

	@Test
	@Order(10)
	void 来源幂等重复并发状态和版本变化必须稳定失败且不产生重复占用() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		FinanceDraftFixture draft = insertReadyFinanceDraft("RECEIPT", "32.00");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> first = executor.submit(() -> {
				await(start);
				return post(admin, "/api/admin/gl/vouchers/from-finance-draft/" + draft.draftId(),
						convertPayload(draft.version(), "031-concurrent-convert-" + draft.draftId()));
			});
			Future<ResponseEntity<String>> second = executor.submit(() -> {
				await(start);
				return post(admin, "/api/admin/gl/vouchers/from-finance-draft/" + draft.draftId(),
						convertPayload(draft.version(), "031-concurrent-convert-" + draft.draftId()));
			});
			start.countDown();
			List<ResponseEntity<String>> responses = List.of(first.get(), second.get());
			assertThat(responses).allSatisfy(this::assertOkUnchecked);
			JsonNode firstVoucher = data(responses.get(0));
			JsonNode secondVoucher = data(responses.get(1));
			assertThat(secondVoucher.get("id").longValue()).isEqualTo(firstVoucher.get("id").longValue());
			assertThat(activeSourceClaimCount(draft.sourceType(), draft.sourceId())).isOne();
			assertError(post(admin, "/api/admin/gl/vouchers/from-finance-draft/" + draft.draftId(),
					convertPayload(draft.version(), "031-convert-duplicate-" + draft.draftId())),
					HttpStatus.CONFLICT, "GL_SOURCE_ALREADY_ACCOUNTED");

			this.jdbcTemplate.update("update fin_receipt set status = 'CANCELLED', version = version + 1 where id = ?",
					draft.sourceId());
			assertError(post(admin, "/api/admin/gl/vouchers/" + firstVoucher.get("id").longValue()
					+ "/refresh-source", actionPayload(firstVoucher.get("version").longValue(),
							"031-refresh-not-ready-" + firstVoucher.get("id"))),
					HttpStatus.CONFLICT, "GL_SOURCE_NOT_READY");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	@Order(11)
	void 权限必须失败关闭且金额来源脱敏贯穿凭证账簿试算错误和审计() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		FinanceDraftFixture draft = insertReadyFinanceDraft("SALES_INVOICE", "113.00");
		JsonNode voucher = convertFinanceDraft(admin, draft, "031-mask-convert-" + draft.draftId());
		AuthenticatedSession restricted = createUserAndLogin("031-restricted-", "031_RESTRICTED_",
				List.of("gl:voucher:view", "gl:ledger:view", "gl:balance:view"));

		assertError(post(restricted, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", START_DATE.plusDays(15), "031 无创建权限",
						List.of(line(1, accountId(admin, "1002"), "031 无创建权限", "1.00", null, List.of()),
								line(2, accountId(admin, "6001"), "031 无创建权限", null, "1.00", List.of())))),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		JsonNode restrictedVoucher = data(get(restricted, "/api/admin/gl/vouchers/" + voucher.get("id").longValue()));
		assertRecursiveNull(restrictedVoucher, List.of("debitTotal", "creditTotal", "debitAmount",
				"creditAmount", "sourceId", "sourceNo", "sourceRoute"));
		assertThat(restrictedVoucher.toString()).doesNotContain("031-SALES_INVOICE");

		JsonNode generalLedger = data(get(restricted,
				"/api/admin/gl/ledgers/general?periodCode=" + START_PERIOD + "&page=1&pageSize=20"));
		assertRecursiveNull(generalLedger, List.of("openingDebit", "openingCredit", "periodDebit",
				"periodCredit", "endingDebit", "endingCredit"));
		assertThat(recursiveValues(generalLedger, "restricted")).contains("true");
		JsonNode trialBalance = data(get(restricted, "/api/admin/gl/trial-balance?periodCode=" + START_PERIOD));
		assertRecursiveNull(trialBalance, List.of("debitTotal", "creditTotal", "differenceAmount"));
		assertThat(permissionDeniedAuditCount("POST", "/api/admin/gl/vouchers")).isGreaterThanOrEqualTo(1);
	}

	@Test
	@Order(12)
	void 项目成本和业务月结不得作为制证来源且不得回写029和030() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		FinanceDraftFixture projectCost = insertUnsupportedReadyDraft("PROJECT_COST_CALCULATION");
		FinanceDraftFixture periodClose = insertUnsupportedReadyDraft("PERIOD_CLOSE_RUN");
		Map<String, Long> upstreamBefore = Map.of("prj_cost_calculation", tableCount("prj_cost_calculation"),
				"biz_period_close_run", tableCount("biz_period_close_run"),
				"biz_period_snapshot", tableCount("biz_period_snapshot"));

		assertError(post(admin, "/api/admin/gl/vouchers/from-finance-draft/" + projectCost.draftId(),
				convertPayload(projectCost.version(), "031-project-cost-rejected-" + projectCost.draftId())),
				HttpStatus.CONFLICT, "GL_RULE_MISSING");
		assertError(post(admin, "/api/admin/gl/vouchers/from-finance-draft/" + periodClose.draftId(),
				convertPayload(periodClose.version(), "031-period-close-rejected-" + periodClose.draftId())),
				HttpStatus.CONFLICT, "GL_RULE_MISSING");
		assertThat(Map.of("prj_cost_calculation", tableCount("prj_cost_calculation"),
				"biz_period_close_run", tableCount("biz_period_close_run"),
				"biz_period_snapshot", tableCount("biz_period_snapshot"))).isEqualTo(upstreamBefore);
		assertThat(activeSourceClaimCount(projectCost.sourceType(), projectCost.sourceId())).isZero();
		assertThat(activeSourceClaimCount(periodClose.sourceType(), periodClose.sourceId())).isZero();
	}

	@Test
	@Order(13)
	void 汇总缓存与不可变分录不一致时试算必须失败关闭() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode posted = createAndPostManualVoucher(admin, "031 试算篡改", "23.00");
		long accountId = this.jdbcTemplate.queryForObject("""
				select account_id
				from gl_ledger_entry
				where voucher_id = ?
				order by id
				limit 1
				""", Long.class, posted.get("id").longValue());
		Map<String, Object> before = this.jdbcTemplate.queryForMap("""
				select *
				from gl_account_period_total
				where account_id = ?
				and period_id = (
					select id from gl_accounting_period where period_code = ?
				)
				""", accountId, START_PERIOD);
		try {
			this.jdbcTemplate.update("""
					update gl_account_period_total
					set period_debit = period_debit + 1.00
					where account_id = ?
					and period_id = (
						select id from gl_accounting_period where period_code = ?
					)
					""", accountId, START_PERIOD);
			assertError(get(admin, "/api/admin/gl/trial-balance?periodCode=" + START_PERIOD),
					HttpStatus.CONFLICT, "GL_TRIAL_BALANCE_MISMATCH");
		}
		finally {
			restorePeriodTotal(before);
		}
	}

	@Test
	@Order(14)
	void 普通凭证已记账后新建和提交期初必须稳定阻断() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		createAndPostManualVoucher(admin, "031 普通过账后期初阻断", "33.00");
		long bankAccount = accountId(admin, "1002");
		long equityAccount = accountId(admin, "4001");

		assertError(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("OPENING", START_DATE, "031 普通过账后不得新建期初",
						List.of(line(1, bankAccount, "031 新建期初阻断", "1.00", null, List.of()),
								line(2, equityAccount, "031 新建期初阻断", null, "1.00", List.of())))),
				HttpStatus.CONFLICT, "GL_PERIOD_NOT_OPEN");

		long legacyOpeningId = insertDraftVoucherBySql("OPENING", START_DATE, "031 历史遗留期初提交阻断",
				"2.00");
		assertError(post(admin, "/api/admin/gl/vouchers/" + legacyOpeningId + "/submit",
				actionPayload(0, "031-opening-after-general-submit-" + legacyOpeningId)), HttpStatus.CONFLICT,
				"GL_PERIOD_NOT_OPEN");
	}

	@Test
	@Order(15)
	void 来源查询必须按sourceType和sourceId精确回链且不误命中不相关凭证() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		FinanceDraftFixture firstDraft = insertReadyFinanceDraft("RECEIPT", "51.00");
		FinanceDraftFixture secondDraft = insertReadyFinanceDraft("PAYMENT", "52.00");
		JsonNode firstVoucher = convertFinanceDraft(admin, firstDraft, "031-source-link-first-"
				+ firstDraft.draftId());
		JsonNode secondVoucher = convertFinanceDraft(admin, secondDraft, "031-source-link-second-"
				+ secondDraft.draftId());

		JsonNode linked = data(get(admin, "/api/admin/gl/vouchers?sourceType=FIN_VOUCHER_DRAFT&sourceId="
				+ firstDraft.draftId() + "&page=1&pageSize=20"));
		assertThat(linked.get("total").longValue()).isOne();
		assertThat(linked.get("items").get(0).get("id").longValue()).isEqualTo(firstVoucher.get("id").longValue());
		assertThat(linked.toString()).doesNotContain(secondVoucher.get("draftNo").asText());

		JsonNode claims = data(get(admin, "/api/admin/gl/source-claims?sourceType=" + firstDraft.sourceType()
				+ "&sourceId=" + firstDraft.sourceId() + "&page=1&pageSize=20"));
		assertThat(claims.get("total").longValue()).isOne();
		assertThat(claims.get("items").get(0).get("voucherId").longValue()).isEqualTo(firstVoucher.get("id")
			.longValue());
	}

	@Test
	@Order(16)
	void 无来源权限时关键词分页列表详情审批审计候选和错误文本均不得反推来源() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		FinanceDraftFixture draft = insertReadyFinanceDraft("SALES_INVOICE", "113.00");
		JsonNode voucher = convertFinanceDraft(admin, draft, "031-source-mask-convert-" + draft.draftId());
		JsonNode submitted = data(post(admin, "/api/admin/gl/vouchers/" + voucher.get("id").longValue()
				+ "/submit", actionPayload(voucher.get("version").longValue(), "031-source-mask-submit-"
						+ voucher.get("id").longValue())));
		String sourceNo = submitted.get("sourceOriginalNo").asText();
		String draftNo = submitted.get("draftNo").asText();
		AuthenticatedSession restricted = createUserAndLogin("031-source-hidden-", "031_SOURCE_HIDDEN_",
				List.of("gl:voucher:view", "gl:ledger:view", "gl:balance:view", "gl:auxiliary:view",
						"platform:approval:view", "platform:todo:view", "system:audit:view"));

		JsonNode keywordResult = data(get(restricted, "/api/admin/gl/vouchers?keyword=" + sourceNo
				+ "&page=1&pageSize=20"));
		JsonNode paged = data(get(restricted, "/api/admin/gl/vouchers?page=1&pageSize=1"));
		JsonNode detail = data(get(restricted, "/api/admin/gl/vouchers/" + voucher.get("id").longValue()));
		JsonNode approval = data(get(restricted, "/api/admin/approvals/" + approvalInstanceId(voucher.get("id")
			.longValue())));
		JsonNode audit = data(get(restricted, "/api/admin/audit-logs?targetType=GL_VOUCHER&page=1&pageSize=20"));
		JsonNode candidates = data(get(restricted, "/api/admin/gl/aux-dimensions/CUSTOMER/candidates?page=1"
				+ "&pageSize=20"));
		ResponseEntity<String> duplicateError = post(restricted,
				"/api/admin/gl/vouchers/from-finance-draft/" + draft.draftId(),
				convertPayload(draft.version(), "031-source-hidden-duplicate-" + draft.draftId()));

		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(keywordResult.get("total").longValue()).as("source keyword must not match").isZero();
		softly.assertThat(paged.toString()).as("paged list").doesNotContain(sourceNo, draft.sourceType());
		softly.assertThat(detail.toString()).as("detail").doesNotContain(sourceNo, draft.sourceType());
		softly.assertThat(approval.toString()).as("approval").doesNotContain(sourceNo, draft.sourceType());
		softly.assertThat(audit.toString()).as("audit").doesNotContain(sourceNo, draftNo, draft.sourceType());
		softly.assertThat(candidates.toString()).as("candidate identifiers").doesNotContain("sourceId",
				"objectId", "objectCode", "objectName");
		softly.assertThat(duplicateError.getBody()).as("error text").doesNotContain(sourceNo, draftNo,
				draft.sourceType());
		softly.assertAll();
		assertRecursiveNull(detail, sourceSensitiveFields());
		assertRecursiveNull(paged, sourceSensitiveFields());
	}

	@Test
	@Order(17)
	void 审批拒绝撤回重复越权和过期任务必须失败关闭并把凭证退回草稿() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		AuthenticatedSession approver = createUserAndLogin("031-terminal-approver-", "031_TERMINAL_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"gl:voucher:view", "gl:voucher:approve-post"));

		JsonNode rejectDraft = createManualDraft(admin, "031 审批驳回回草稿", "61.00");
		JsonNode rejectSubmitted = data(post(admin, "/api/admin/gl/vouchers/" + rejectDraft.get("id").longValue()
				+ "/submit", actionPayload(rejectDraft.get("version").longValue(), "031-reject-submit-"
						+ rejectDraft.get("id").longValue())));
		long rejectTaskId = approvalTaskIdForVoucher(rejectDraft.get("id").longValue());
		JsonNode rejected = data(post(approver, "/api/admin/approval-tasks/" + rejectTaskId + "/reject",
				approvalPayload(taskVersion(rejectTaskId), "031-reject-" + rejectTaskId)));
		assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
		assertThat(voucherStatus(rejectDraft.get("id").longValue())).isEqualTo("DRAFT");
		assertError(post(approver, "/api/admin/approval-tasks/" + rejectTaskId + "/reject",
				approvalPayload(taskVersion(rejectTaskId), "031-reject-repeat-" + rejectTaskId)),
				HttpStatus.CONFLICT, "APPROVAL_STATUS_INVALID");

		JsonNode withdrawDraft = createManualDraft(admin, "031 审批撤回回草稿", "62.00");
		JsonNode withdrawSubmitted = data(post(admin, "/api/admin/gl/vouchers/" + withdrawDraft.get("id")
			.longValue() + "/submit", actionPayload(withdrawDraft.get("version").longValue(), "031-withdraw-submit-"
					+ withdrawDraft.get("id").longValue())));
		long withdrawInstanceId = withdrawSubmitted.get("approvalSummary").get("id").longValue();
		JsonNode withdrawn = data(post(admin, "/api/admin/approvals/" + withdrawInstanceId + "/withdraw",
				approvalPayload(withdrawSubmitted.get("approvalSummary").get("version").longValue(),
						"031-withdraw-" + withdrawInstanceId)));
		assertThat(withdrawn.get("status").asText()).isEqualTo("WITHDRAWN");
		assertThat(voucherStatus(withdrawDraft.get("id").longValue())).isEqualTo("DRAFT");
		assertError(post(admin, "/api/admin/approvals/" + withdrawInstanceId + "/withdraw",
				approvalPayload(withdrawn.get("version").longValue(), "031-withdraw-repeat-"
						+ withdrawInstanceId)), HttpStatus.CONFLICT, "APPROVAL_STATUS_INVALID");

		JsonNode forbiddenDraft = createManualDraft(admin, "031 审批越权拒绝", "63.00");
		data(post(admin, "/api/admin/gl/vouchers/" + forbiddenDraft.get("id").longValue() + "/submit",
				actionPayload(forbiddenDraft.get("version").longValue(), "031-forbidden-submit-"
						+ forbiddenDraft.get("id").longValue())));
		long forbiddenTaskId = approvalTaskIdForVoucher(forbiddenDraft.get("id").longValue());
		AuthenticatedSession noApprover = createUserAndLogin("031-no-approve-", "031_NO_APPROVE_",
				List.of("platform:approval:view", "platform:todo:view", "gl:voucher:view"));
		assertError(post(noApprover, "/api/admin/approval-tasks/" + forbiddenTaskId + "/reject",
				approvalPayload(taskVersion(forbiddenTaskId), "031-forbidden-reject-" + forbiddenTaskId)),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		JsonNode expiredDraft = createManualDraft(admin, "031 审批过期拒绝", "64.00");
		data(post(admin, "/api/admin/gl/vouchers/" + expiredDraft.get("id").longValue() + "/submit",
				actionPayload(expiredDraft.get("version").longValue(), "031-expired-submit-"
						+ expiredDraft.get("id").longValue())));
		long expiredTaskId = approvalTaskIdForVoucher(expiredDraft.get("id").longValue());
		this.jdbcTemplate.update("""
				update platform_approval_instance
				set status = 'CANCELLED', completed_by_username = 'system', completed_at = now(),
				    completed_comment = '031 模拟待处理任务过期终止', updated_at = now(), version = version + 1
				where id = (
					select instance_id from platform_approval_task where id = ?
				)
				""", expiredTaskId);
		this.jdbcTemplate.update("""
				update platform_approval_task
				set updated_at = now(), version = version + 1
				where id = ?
				""", expiredTaskId);
		assertError(post(approver, "/api/admin/approval-tasks/" + expiredTaskId + "/approve",
				approvalPayload(taskVersion(expiredTaskId), "031-expired-approve-" + expiredTaskId)),
				HttpStatus.CONFLICT, "APPROVAL_STATUS_INVALID");
		assertThat(voucherStatus(expiredDraft.get("id").longValue())).isEqualTo("SUBMITTED");
	}

	@Test
	@Order(18)
	void 两个不同待审批凭证并发最终审批时正式号必须连续唯一且不跳号() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode first = createManualDraft(admin, "031 并发审批一", "71.00");
		JsonNode second = createManualDraft(admin, "031 并发审批二", "72.00");
		data(post(admin, "/api/admin/gl/vouchers/" + first.get("id").longValue() + "/submit",
				actionPayload(first.get("version").longValue(), "031-concurrent-post-submit-"
						+ first.get("id").longValue())));
		data(post(admin, "/api/admin/gl/vouchers/" + second.get("id").longValue() + "/submit",
				actionPayload(second.get("version").longValue(), "031-concurrent-post-submit-"
						+ second.get("id").longValue())));
		long firstTaskId = approvalTaskIdForVoucher(first.get("id").longValue());
		long secondTaskId = approvalTaskIdForVoucher(second.get("id").longValue());
		long beforeSequence = voucherSequence(START_PERIOD);
		AuthenticatedSession approver = createUserAndLogin("031-concurrent-approver-", "031_CONCURRENT_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"gl:voucher:view", "gl:voucher:approve-post"));
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> firstApprove = executor.submit(() -> {
				await(start);
				return post(approver, "/api/admin/approval-tasks/" + firstTaskId + "/approve",
						approvalPayload(taskVersion(firstTaskId), "031-concurrent-approve-" + firstTaskId));
			});
			Future<ResponseEntity<String>> secondApprove = executor.submit(() -> {
				await(start);
				return post(approver, "/api/admin/approval-tasks/" + secondTaskId + "/approve",
						approvalPayload(taskVersion(secondTaskId), "031-concurrent-approve-" + secondTaskId));
			});
			start.countDown();
			List<ResponseEntity<String>> responses = List.of(firstApprove.get(), secondApprove.get());
			assertThat(responses).allSatisfy(this::assertOkUnchecked);
		}
		finally {
			executor.shutdownNow();
		}
		JsonNode postedFirst = data(get(admin, "/api/admin/gl/vouchers/" + first.get("id").longValue()));
		JsonNode postedSecond = data(get(admin, "/api/admin/gl/vouchers/" + second.get("id").longValue()));
		Set<Long> numbers = new HashSet<>(List.of(voucherNumber(postedFirst), voucherNumber(postedSecond)));
		assertThat(numbers).containsExactlyInAnyOrder(beforeSequence + 1, beforeSequence + 2);
		assertThat(voucherSequence(START_PERIOD)).isEqualTo(beforeSequence + 2);
	}

	@Test
	@Order(19)
	void 取消刷新来源和冲销使用相同幂等键必须稳定重放同一结果() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);

		JsonNode cancelDraft = createManualDraft(admin, "031 取消幂等", "81.00");
		Map<String, Object> cancelPayload = actionPayload(cancelDraft.get("version").longValue(),
				"031-cancel-idempotent-" + cancelDraft.get("id").longValue());
		JsonNode cancelled = data(post(admin, "/api/admin/gl/vouchers/" + cancelDraft.get("id").longValue()
				+ "/cancel", cancelPayload));
		JsonNode cancelledAgain = data(post(admin, "/api/admin/gl/vouchers/" + cancelDraft.get("id").longValue()
				+ "/cancel", cancelPayload));
		assertThat(cancelledAgain.get("id").longValue()).isEqualTo(cancelled.get("id").longValue());
		assertThat(cancelledAgain.get("status").asText()).isEqualTo("CANCELLED");

		FinanceDraftFixture draft = insertReadyFinanceDraft("EXPENSE", "45.20");
		JsonNode voucher = convertFinanceDraft(admin, draft, "031-refresh-idempotent-convert-" + draft.draftId());
		Map<String, Object> refreshPayload = actionPayload(voucher.get("version").longValue(),
				"031-refresh-idempotent-" + voucher.get("id").longValue());
		JsonNode refreshed = data(post(admin, "/api/admin/gl/vouchers/" + voucher.get("id").longValue()
				+ "/refresh-source", refreshPayload));
		JsonNode refreshedAgain = data(post(admin, "/api/admin/gl/vouchers/" + voucher.get("id").longValue()
				+ "/refresh-source", refreshPayload));
		assertThat(refreshedAgain.get("id").longValue()).isEqualTo(refreshed.get("id").longValue());
		assertThat(refreshedAgain.get("version").longValue()).isEqualTo(refreshed.get("version").longValue());

		JsonNode posted = createAndPostManualVoucher(admin, "031 冲销幂等", "82.00");
		Map<String, Object> reversePayload = reversalPayload(posted.get("version").longValue(),
				START_DATE.plusDays(18), "031 冲销幂等原因", "031-reverse-idempotent-" + posted.get("id")
					.longValue());
		JsonNode reversal = data(post(admin, "/api/admin/gl/vouchers/" + posted.get("id").longValue()
				+ "/reversals", reversePayload));
		JsonNode reversalAgain = data(post(admin, "/api/admin/gl/vouchers/" + posted.get("id").longValue()
				+ "/reversals", reversePayload));
		assertThat(reversalAgain.get("id").longValue()).isEqualTo(reversal.get("id").longValue());
		assertThat(reversalLinkCount(posted.get("id").longValue())).isOne();
	}

	@Test
	@Order(20)
	void 科目和辅助候选池不得受主列表分页或前一百条截断且已选必须回显() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		int suffix = SEQUENCE.incrementAndGet();
		String accountParentCode = "1131.CAND" + suffix;
		String accountPrefix = accountParentCode + ".";
		String auxDimensionCode = "ACPOOL" + suffix;
		String auxItemPrefix = "AUXCAND" + suffix + "-";
		long accountParentId = insertCandidateAccountParentBySql(accountParentCode, "031 科目候选池父科目 " + suffix);
		long selectedAccountId = 0;
		String selectedAccountCode = null;
		long lateAccountId = 0;
		String lateAccountCode = null;
		long auxDimensionId = insertCustomAuxDimensionBySql(auxDimensionCode, "031 候选池维度 " + suffix);
		long selectedAuxItemId = 0;
		String selectedAuxItemCode = null;
		long lateAuxItemId = 0;
		String lateAuxItemCode = null;
		for (int index = 1; index <= 105; index++) {
			String serial = "%03d".formatted(index);
			String accountCode = accountPrefix + serial;
			long accountId = insertCandidateAccountBySql(accountParentId, accountCode,
					"031 科目候选池 第" + serial + "项");
			String auxItemCode = auxItemPrefix + serial;
			long auxItemId = insertCustomAuxItemBySql(auxDimensionId, auxItemCode,
					"031 辅助候选池 第" + serial + "项");
			if (index == 103) {
				selectedAccountId = accountId;
				selectedAccountCode = accountCode;
				selectedAuxItemId = auxItemId;
				selectedAuxItemCode = auxItemCode;
			}
			if (index == 105) {
				lateAccountId = accountId;
				lateAccountCode = accountCode;
				lateAuxItemId = auxItemId;
				lateAuxItemCode = auxItemCode;
			}
		}

		JsonNode firstAccountPage = data(get(admin, "/api/admin/gl/accounts?keyword=" + accountPrefix
				+ "&page=1&pageSize=5"));
		assertThat(firstAccountPage.get("total").longValue()).isGreaterThan(100);
		assertThat(firstAccountPage.get("pageSize").intValue()).isEqualTo(10);
		assertThat(firstAccountPage.get("items").size()).isEqualTo(10);
		assertThat(itemsContainLong(firstAccountPage.get("items"), "id", lateAccountId)).isFalse();
		JsonNode firstAccountCandidates = data(get(admin, "/api/admin/gl/accounts/candidates?keyword="
				+ accountPrefix + "&page=1&pageSize=5"));
		assertThat(firstAccountCandidates.get("total").longValue()).isGreaterThan(100);
		assertThat(firstAccountCandidates.get("pageSize").intValue()).isEqualTo(5);
		assertThat(firstAccountCandidates.get("items").size()).isEqualTo(5);
		JsonNode lateAccountCandidates = data(get(admin, "/api/admin/gl/accounts/candidates?keyword="
				+ lateAccountCode + "&page=1&pageSize=5"));
		assertThat(lateAccountCandidates.get("total").longValue()).isOne();
		assertThat(itemsContainLong(lateAccountCandidates.get("items"), "accountId", lateAccountId)).isTrue();
		assertThat(itemsContainText(lateAccountCandidates.get("items"), "accountCode", lateAccountCode)).isTrue();
		JsonNode selectedAccountCandidates = data(get(admin, "/api/admin/gl/accounts/candidates?keyword="
				+ "NO_MATCH_031&selectedIds=" + selectedAccountId + "&page=1&pageSize=5"));
		assertThat(selectedAccountCandidates.get("total").longValue()).isOne();
		assertThat(itemsContainLong(selectedAccountCandidates.get("items"), "accountId", selectedAccountId))
			.isTrue();
		assertThat(itemsContainText(selectedAccountCandidates.get("items"), "accountCode", selectedAccountCode))
			.isTrue();

		JsonNode firstAuxPage = data(get(admin, "/api/admin/gl/aux-dimensions/" + auxDimensionId
				+ "/items?keyword=" + auxItemPrefix + "&page=1&pageSize=5"));
		assertThat(firstAuxPage.get("total").longValue()).isGreaterThan(100);
		assertThat(firstAuxPage.get("pageSize").intValue()).isEqualTo(10);
		assertThat(firstAuxPage.get("items").size()).isEqualTo(10);
		assertThat(itemsContainLong(firstAuxPage.get("items"), "id", lateAuxItemId)).isFalse();
		JsonNode firstAuxCandidates = data(get(admin, "/api/admin/gl/aux-dimensions/" + auxDimensionCode
				+ "/candidates?keyword=" + auxItemPrefix + "&page=1&pageSize=5"));
		assertThat(firstAuxCandidates.get("total").longValue()).isGreaterThan(100);
		assertThat(firstAuxCandidates.get("pageSize").intValue()).isEqualTo(5);
		assertThat(firstAuxCandidates.get("items").size()).isEqualTo(5);
		JsonNode lateAuxCandidates = data(get(admin, "/api/admin/gl/aux-dimensions/" + auxDimensionCode
				+ "/candidates?keyword=" + lateAuxItemCode + "&page=1&pageSize=5"));
		assertThat(lateAuxCandidates.get("total").longValue()).isOne();
		assertThat(itemsContainLong(lateAuxCandidates.get("items"), "auxItemId", lateAuxItemId)).isTrue();
		assertThat(itemsContainText(lateAuxCandidates.get("items"), "objectCode", lateAuxItemCode)).isTrue();
		JsonNode selectedAuxCandidates = data(get(admin, "/api/admin/gl/aux-dimensions/" + auxDimensionCode
				+ "/candidates?keyword=NO_MATCH_031&selectedIds=" + selectedAuxItemId + "&page=1&pageSize=5"));
		assertThat(selectedAuxCandidates.get("total").longValue()).isOne();
		assertThat(itemsContainLong(selectedAuxCandidates.get("items"), "auxItemId", selectedAuxItemId)).isTrue();
		assertThat(itemsContainText(selectedAuxCandidates.get("items"), "objectCode", selectedAuxItemCode))
			.isTrue();
	}

	@Test
	@Order(21)
	void 自定义辅助维度项目和制证规则版本预览激活停用必须走真实接口且预览不占用来源() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		int suffix = SEQUENCE.incrementAndGet();
		String dimensionCode = "WORKSHOP_" + suffix;
		JsonNode dimension = data(post(admin, "/api/admin/gl/aux-dimensions", Map.of("code", dimensionCode,
				"name", "031 自定义车间", "dimensionType", "CUSTOM", "enabled", true, "version", 0,
				"idempotencyKey", "031-aux-dimension-" + suffix)));
		JsonNode item = data(post(admin, "/api/admin/gl/aux-dimensions/" + dimension.get("id").longValue()
				+ "/items", Map.of("code", "WS-" + suffix, "name", "031 装配车间", "enabled", true, "version", 0,
						"idempotencyKey", "031-aux-item-" + suffix)));
		JsonNode candidates = data(get(admin, "/api/admin/gl/aux-dimensions/" + dimensionCode
				+ "/candidates?keyword=WS-" + suffix + "&page=1&pageSize=20"));
		assertThat(candidates.get("total").longValue()).isOne();
		JsonNode dimensions = data(get(admin, "/api/admin/gl/aux-dimensions?enabled=true&page=1&pageSize=20"));
		assertThat(itemsContainText(dimensions.get("items"), "code", "CUSTOMER")).isTrue();
		assertThat(itemsContainText(dimensions.get("items"), "code", "SUPPLIER")).isTrue();
		assertThat(itemsContainText(dimensions.get("items"), "code", "PROJECT")).isTrue();
		assertThat(itemsContainText(dimensions.get("items"), "code", dimensionCode)).isTrue();
		long bankAccount = accountId(admin, "1002");
		JsonNode customAccount = data(post(admin, "/api/admin/gl/accounts",
				accountPayload(bankAccount, "1002.WS" + suffix, "031 车间辅助银行", "ASSET", "DEBIT", true,
						List.of(Map.of("dimensionCode", dimensionCode, "requirementType", "REQUIRED")))));
		JsonNode customVoucher = data(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", START_DATE.plusDays(19), "031 自定义辅助凭证",
						List.of(line(1, customAccount.get("id").longValue(), "031 自定义辅助借方", "9.00", null,
								List.of(Map.of("dimensionCode", dimensionCode, "auxItemId", item.get("id")
									.longValue()))),
								line(2, accountId(admin, "6001"), "031 自定义辅助贷方", null, "9.00", List.of())))));
		assertThat(recursiveValues(customVoucher, "dimensionCode")).contains(dimensionCode);

		JsonNode rules = data(get(admin, "/api/admin/gl/posting-rules?page=1&pageSize=50"));
		JsonNode activeSalesRule = findItem(rules.get("items"), "sourceType", "SALES_INVOICE");
		JsonNode ruleDetail = data(get(admin, "/api/admin/gl/posting-rules/" + activeSalesRule.get("id")
			.longValue()));
		assertThat(ruleDetail.get("lines").size()).isGreaterThanOrEqualTo(2);
		FinanceDraftFixture draft = insertReadyFinanceDraft("SALES_INVOICE", "113.00");
		long beforeVoucherCount = tableCount("gl_voucher");
		long beforeClaimCount = activeSourceClaimCount(draft.sourceType(), draft.sourceId());
		JsonNode preview = data(post(admin, "/api/admin/gl/posting-rules/" + activeSalesRule.get("id")
			.longValue() + "/validate", Map.of("sourceType", draft.sourceType(), "sourceId", draft.sourceId(),
					"sourceVersion", draft.version(), "idempotencyKey", "031-rule-preview-" + draft.draftId())));
		assertThat(preview.get("validationStatus").asText()).isEqualTo("VALID");
		assertThat(tableCount("gl_voucher")).isEqualTo(beforeVoucherCount);
		assertThat(activeSourceClaimCount(draft.sourceType(), draft.sourceId())).isEqualTo(beforeClaimCount);
		assertInvalidPostingRuleScenarios(admin, activeSalesRule.get("id").longValue(), suffix);
		JsonNode newVersion = data(post(admin, "/api/admin/gl/posting-rules/" + activeSalesRule.get("id")
			.longValue() + "/new-version", actionPayload(activeSalesRule.get("version").longValue(),
					"031-rule-new-version-" + activeSalesRule.get("id").longValue())));
		JsonNode activated = data(post(admin, "/api/admin/gl/posting-rules/" + newVersion.get("id").longValue()
				+ "/activate", actionPayload(newVersion.get("version").longValue(), "031-rule-activate-"
						+ newVersion.get("id").longValue())));
		assertThat(activated.get("status").asText()).isEqualTo("ACTIVE");
		JsonNode disposableRule = data(post(admin, "/api/admin/gl/posting-rules",
				Map.of("sourceType", "GL_TEST_SOURCE_" + suffix, "sourceVariant", "DEFAULT",
						"name", "031 可停用测试规则", "description", "031 停用动作测试",
						"effectiveFrom", START_DATE.toString(), "version", 0,
						"idempotencyKey", "031-rule-disposable-" + suffix,
						"lines", List.of(
								Map.of("lineNo", 1, "normalizedFactCode", "TEST_DEBIT", "direction",
										"DEBIT", "accountId", accountId(admin, "1002"),
										"summaryTemplate", "031 停用测试借方", "auxiliaryMappings", List.of()),
								Map.of("lineNo", 2, "normalizedFactCode", "TEST_CREDIT", "direction",
										"CREDIT", "accountId", accountId(admin, "6001"),
										"summaryTemplate", "031 停用测试贷方", "auxiliaryMappings", List.of())))));
		JsonNode disabled = data(post(admin, "/api/admin/gl/posting-rules/" + disposableRule.get("id").longValue()
				+ "/disable", actionPayload(disposableRule.get("version").longValue(),
						"031-rule-disable-" + disposableRule.get("id").longValue())));
		assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
	}

	@Test
	@Order(22)
	void 已引用科目必须保护关键字段停用辅助要求并返回禁用原因() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode posted = createAndPostManualVoucher(admin, "031 科目引用保护", "91.00");
		long referencedAccount = this.jdbcTemplate.queryForObject("""
				select account_id
				from gl_ledger_entry
				where voucher_id = ?
				order by id
				limit 1
				""", Long.class, posted.get("id").longValue());
		JsonNode account = data(get(admin, "/api/admin/gl/accounts/" + referencedAccount));
		assertThat(account.get("referenced").booleanValue()).isTrue();
		assertThat(actionCodes(account.get("allowedActions"))).doesNotContain("DISABLE");
		assertThat(account.get("actionDisabledReasons").toString()).contains("已引用");

		assertError(exchange(admin, HttpMethod.PUT, "/api/admin/gl/accounts/" + referencedAccount,
				accountPayload(null, account.get("code").asText(), "031 已引用科目改名", account.get("category")
					.asText(), account.get("balanceDirection").asText(), false, List.of())),
				HttpStatus.CONFLICT, "GL_ACCOUNT_LOCKED");
		assertError(post(admin, "/api/admin/gl/accounts/" + referencedAccount + "/disable", Map.of()),
				HttpStatus.CONFLICT, "GL_ACCOUNT_LOCKED");
	}

	@Test
	@Order(23)
	void 明细账必须返回运行余额来源过滤契约且试算差异DTO必须可定位科目() throws Exception {
		requireV33Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		FinanceDraftFixture draft = insertReadyFinanceDraft("SALES_INVOICE", "113.00");
		JsonNode voucher = convertFinanceDraft(admin, draft, "031-ledger-dto-convert-" + draft.draftId());
		JsonNode posted = submitAndApprove(admin, voucher, "031-ledger-dto");
		posted = data(get(admin, "/api/admin/gl/vouchers/" + posted.get("id").longValue()));
		String voucherNo = posted.get("voucherNo").asText();
		String customerCode = salesInvoiceCustomerCode(draft.sourceId());
		String febSummary = "031-FEB-FILTER-" + SEQUENCE.incrementAndGet();
		long bankAccount = accountId(admin, "1002");
		long revenueAccount = accountId(admin, "6001");
		JsonNode febVoucher = data(post(admin, "/api/admin/gl/vouchers",
				voucherPayload("GENERAL", LocalDate.of(2091, 2, 2), febSummary,
						List.of(line(1, bankAccount, febSummary, "7.00", null, List.of()),
								line(2, revenueAccount, febSummary, null, "7.00", List.of())))));
		JsonNode febOnly = data(get(admin, "/api/admin/gl/vouchers?periodCode=2091-02&keyword="
				+ febSummary + "&page=1&pageSize=20"));
		assertThat(febOnly.get("total").longValue()).isOne();
		assertThat(febOnly.get("items").get(0).get("id").longValue()).isEqualTo(febVoucher.get("id").longValue());
		assertThat(febOnly.get("items").get(0).get("accountingPeriodCode").asText()).isEqualTo("2091-02");
		JsonNode janOnly = data(get(admin, "/api/admin/gl/vouchers?periodCode=" + START_PERIOD + "&keyword="
				+ febSummary + "&page=1&pageSize=20"));
		assertThat(janOnly.get("total").longValue()).isZero();
		JsonNode detail = data(get(admin, "/api/admin/gl/ledgers/detail?periodCode=" + START_PERIOD
				+ "&voucherNo=" + voucherNo + "&sourceType=" + draft.sourceType() + "&sourceId=" + draft.sourceId()
				+ "&page=1&pageSize=20"));
		assertThat(posted.get("lines").size()).isEqualTo(3);
		for (JsonNode line : posted.get("lines")) {
			assertThat(line.get("sourceVisible").booleanValue()).isTrue();
			assertThat(line.get("sourceType").asText()).isEqualTo(draft.sourceType());
			assertThat(line.get("sourceId").longValue()).isEqualTo(draft.sourceId());
			assertThat(line.hasNonNull("sourceNo")).isTrue();
			assertThat(line.get("sourceRoute").get("sourceType").asText()).isEqualTo(draft.sourceType());
			assertThat(line.get("sourceRoute").get("sourceId").longValue()).isEqualTo(draft.sourceId());
		}
		assertThat(detail.get("total").longValue()).isEqualTo(3);
		for (JsonNode item : detail.get("items")) {
			assertThat(item.hasNonNull("runningBalance")).as(item.toString()).isTrue();
			assertThat(item.get("sourceVisible").booleanValue()).isTrue();
			assertThat(item.get("sourceType").asText()).isEqualTo(draft.sourceType());
			assertThat(item.get("sourceId").longValue()).isEqualTo(draft.sourceId());
			assertThat(item.hasNonNull("sourceNo")).isTrue();
		}
		JsonNode generalByRange = data(get(admin, "/api/admin/gl/ledgers/general?periodCode=" + START_PERIOD
				+ "&accountCodeFrom=1122&accountCodeTo=1122&page=1&pageSize=20"));
		assertThat(generalByRange.get("total").longValue()).isGreaterThanOrEqualTo(1L);
		for (JsonNode item : generalByRange.get("items")) {
			assertThat(item.get("accountCode").asText()).isEqualTo("1122");
		}
		JsonNode generalByLevel = data(get(admin, "/api/admin/gl/ledgers/general?periodCode=" + START_PERIOD
				+ "&accountCodeFrom=2221&accountCodeTo=2221.99&level=2&page=1&pageSize=20"));
		assertThat(generalByLevel.get("total").longValue()).isGreaterThanOrEqualTo(1L);
		for (JsonNode item : generalByLevel.get("items")) {
			assertThat(item.get("accountCode").asText()).startsWith("2221.");
		}
		JsonNode generalByAuxiliary = data(get(admin, "/api/admin/gl/ledgers/general?periodCode=" + START_PERIOD
				+ "&accountKeyword=1122&auxiliaryKeyword=" + customerCode + "&page=1&pageSize=20"));
		assertThat(generalByAuxiliary.get("total").longValue()).isGreaterThanOrEqualTo(1L);
		for (JsonNode item : generalByAuxiliary.get("items")) {
			assertThat(item.get("accountCode").asText()).isEqualTo("1122");
		}
		JsonNode generalBySource = data(get(admin, "/api/admin/gl/ledgers/general?periodCode=" + START_PERIOD
				+ "&accountKeyword=6001&sourceType=" + draft.sourceType() + "&page=1&pageSize=20"));
		assertThat(generalBySource.get("total").longValue()).isGreaterThanOrEqualTo(1L);
		for (JsonNode item : generalBySource.get("items")) {
			assertThat(item.get("accountCode").asText()).isEqualTo("6001");
		}
		JsonNode unrelatedSource = data(get(admin, "/api/admin/gl/ledgers/general?periodCode=" + START_PERIOD
				+ "&accountKeyword=6001&sourceType=PURCHASE_INVOICE&page=1&pageSize=20"));
		assertThat(unrelatedSource.get("total").longValue()).isZero();
		JsonNode detailByAuxiliary = data(get(admin, "/api/admin/gl/ledgers/detail?periodCode=" + START_PERIOD
				+ "&voucherNo=" + voucherNo + "&auxiliaryKeyword=" + customerCode + "&page=1&pageSize=20"));
		assertThat(detailByAuxiliary.get("total").longValue()).isOne();
		assertThat(detailByAuxiliary.get("items").get(0).get("accountCode").asText()).isEqualTo("1122");
		JsonNode balancesByAuxiliary = data(get(admin, "/api/admin/gl/account-balances?periodCode=" + START_PERIOD
				+ "&accountKeyword=1122&auxiliaryKeyword=" + customerCode + "&page=1&pageSize=20"));
		assertThat(balancesByAuxiliary.get("total").longValue()).isGreaterThanOrEqualTo(1L);
		for (JsonNode item : balancesByAuxiliary.get("items")) {
			assertThat(item.get("accountCode").asText()).isEqualTo("1122");
		}
		JsonNode trialBalance = data(get(admin, "/api/admin/gl/trial-balance?periodCode=" + START_PERIOD));
		assertThat(trialBalance.get("groupDifferences").size()).isEqualTo(3);
		assertThat(recursiveValues(trialBalance.get("groupDifferences"), "groupCode")).contains("OPENING",
				"PERIOD", "ENDING");
		assertThat(trialBalance.get("differences").size()).isGreaterThan(0);
		JsonNode firstDifference = trialBalance.get("differences").get(0);
		assertThat(firstDifference.hasNonNull("accountCode")).isTrue();
		assertThat(firstDifference.hasNonNull("accountName")).isTrue();
		assertThat(firstDifference.hasNonNull("differenceAmount")).isTrue();

		long accountId = this.jdbcTemplate.queryForObject("""
				select account_id
				from gl_ledger_entry
				where voucher_id = ?
				order by id
				limit 1
				""", Long.class, posted.get("id").longValue());
		Map<String, Object> before = this.jdbcTemplate.queryForMap("""
				select *
				from gl_account_period_total
				where account_id = ?
				and period_id = (
					select id from gl_accounting_period where period_code = ?
				)
				""", accountId, START_PERIOD);
		try {
			this.jdbcTemplate.update("""
					update gl_account_period_total
					set period_debit = period_debit + 3.00
					where account_id = ?
					and period_id = (
						select id from gl_accounting_period where period_code = ?
					)
					""", accountId, START_PERIOD);
			ResponseEntity<String> mismatch = get(admin, "/api/admin/gl/trial-balance?periodCode="
					+ START_PERIOD);
			assertThat(mismatch.getStatusCode()).as(mismatch.getBody()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(mismatch.getBody()).contains("GL_TRIAL_BALANCE_MISMATCH", "accountCode",
					"expectedDebit", "actualDebit");
		}
		finally {
			restorePeriodTotal(before);
		}
	}

	private void requireV33Schema() {
		for (String table : GL_TABLES) {
			assertThat(tableExists(table)).as("V33 必须创建 " + table).isTrue();
		}
	}

	private JsonNode ensureLedgerInitialized(AuthenticatedSession session) throws Exception {
		return data(post(session, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", START_PERIOD, "idempotencyKey", "031-ledger-init-main")));
	}

	private JsonNode createManualDraft(AuthenticatedSession session, String summary, String amount) throws Exception {
		long bankAccount = accountId(session, "1002");
		long revenueAccount = accountId(session, "6001");
		return data(post(session, "/api/admin/gl/vouchers", voucherPayload("GENERAL",
				START_DATE.plusDays(SEQUENCE.incrementAndGet() % 20 + 1), summary,
				List.of(line(1, bankAccount, summary, amount, null, List.of()),
						line(2, revenueAccount, summary, null, amount, List.of())))));
	}

	private JsonNode createAndPostManualVoucher(AuthenticatedSession admin, String summary, String amount)
			throws Exception {
		return submitAndApprove(admin, createManualDraft(admin, summary, amount), summary);
	}

	private JsonNode submitAndApprove(AuthenticatedSession submitter, JsonNode voucher, String keyPrefix)
			throws Exception {
		JsonNode submitted = data(post(submitter, "/api/admin/gl/vouchers/" + voucher.get("id").longValue()
				+ "/submit", actionPayload(voucher.get("version").longValue(), keyPrefix + "-submit-"
						+ voucher.get("id").longValue())));
		long taskId = approvalTaskIdForVoucher(voucher.get("id").longValue());
		AuthenticatedSession approver = createUserAndLogin(keyPrefix + "-approver-", keyPrefix.toUpperCase()
				.replaceAll("[^A-Z0-9_]", "_") + "_APPROVER_", List.of("platform:approval:view",
						"platform:todo:view", "platform:message:view", "gl:voucher:view",
						"gl:voucher:approve-post"));
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		return approveVoucher(approver, voucher.get("id").longValue(), taskId, keyPrefix + "-approve-");
	}

	private JsonNode approveVoucher(AuthenticatedSession approver, long voucherId, long taskId, String keyPrefix)
			throws Exception {
		ResponseEntity<String> approved = post(approver, "/api/admin/approval-tasks/" + taskId + "/approve",
				approvalPayload(taskVersion(taskId), keyPrefix + taskId));
		assertOk(approved);
		return data(get(approver, "/api/admin/gl/vouchers/" + voucherId));
	}

	private JsonNode convertFinanceDraft(AuthenticatedSession session, FinanceDraftFixture draft, String key)
			throws Exception {
		return data(post(session, "/api/admin/gl/vouchers/from-finance-draft/" + draft.draftId(),
				convertPayload(draft.version(), key)));
	}

	private void assertInvalidPostingRuleScenarios(AuthenticatedSession admin, long activeSalesRuleId, int suffix)
			throws Exception {
		JsonNode missingFactsRule = createPostingRule(admin, "SALES_INVOICE", "DEFAULT", "031 缺失事实规则 " + suffix,
				List.of(ruleLine(1, "SALES_RECEIVABLE", "DEBIT", accountId(admin, "1122"), "031 缺失事实借方",
						List.of(ruleAuxiliary("CUSTOMER", "SOURCE_CUSTOMER")))));
		assertRuleInvalidOnValidateAndActivate(admin, missingFactsRule, "031-rule-missing-facts-" + suffix);

		long unbalancedSalesInvoiceId = insertSalesInvoiceSource(SEQUENCE.incrementAndGet());
		this.jdbcTemplate.execute("alter table fin_sales_invoice drop constraint ck_fin_sales_invoice_amount");
		try {
			this.jdbcTemplate.update("""
					update fin_sales_invoice
					set tax_amount = 14.00, version = version + 1
					where id = ?
					""", unbalancedSalesInvoiceId);
			assertError(post(admin, "/api/admin/gl/posting-rules/" + activeSalesRuleId + "/validate",
					Map.of("sourceType", "SALES_INVOICE", "sourceId", unbalancedSalesInvoiceId,
							"sourceVersion", sourceVersion("SALES_INVOICE", unbalancedSalesInvoiceId),
							"idempotencyKey", "031-rule-unbalanced-preview-" + unbalancedSalesInvoiceId)),
					HttpStatus.BAD_REQUEST, "GL_RULE_INVALID");
		}
		finally {
			this.jdbcTemplate.update("""
					update fin_sales_invoice
					set tax_amount = 13.00, tax_included_amount = 113.00, version = version + 1
					where id = ?
					""", unbalancedSalesInvoiceId);
			this.jdbcTemplate.execute("""
					alter table fin_sales_invoice
					add constraint ck_fin_sales_invoice_amount check (
						tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
						and tax_excluded_amount + tax_amount = tax_included_amount
					)
					""");
		}

		JsonNode nonLeafRule = createPostingRule(admin, "SALES_INVOICE", "DEFAULT", "031 非叶子规则 " + suffix,
				List.of(ruleLine(1, "SALES_RECEIVABLE", "DEBIT", accountId(admin, "1122"), "031 非叶子应收",
						List.of(ruleAuxiliary("CUSTOMER", "SOURCE_CUSTOMER"))),
						ruleLine(2, "SALES_REVENUE", "CREDIT", accountId(admin, "6001"), "031 非叶子收入",
								List.of()),
						ruleLine(3, "OUTPUT_VAT", "CREDIT", accountId(admin, "2221"), "031 非叶子销项税",
								List.of())));
		assertRuleInvalidOnValidateAndActivate(admin, nonLeafRule, "031-rule-non-leaf-" + suffix);

		long disabledAccountId = insertRuleAccountBySql("6301.DIS" + suffix, "031 停用规则科目 " + suffix,
				"PROFIT_LOSS", "CREDIT", true, false);
		JsonNode disabledRule = createPostingRule(admin, "SALES_INVOICE", "DEFAULT", "031 停用科目规则 " + suffix,
				List.of(ruleLine(1, "SALES_RECEIVABLE", "DEBIT", accountId(admin, "1122"), "031 停用规则应收",
						List.of(ruleAuxiliary("CUSTOMER", "SOURCE_CUSTOMER"))),
						ruleLine(2, "SALES_REVENUE", "CREDIT", disabledAccountId, "031 停用规则收入",
								List.of()),
						ruleLine(3, "OUTPUT_VAT", "CREDIT", accountId(admin, "2221.02"), "031 停用规则销项税",
								List.of())));
		assertRuleInvalidOnValidateAndActivate(admin, disabledRule, "031-rule-disabled-account-" + suffix);

		JsonNode impossibleAuxiliaryRule = createPostingRule(admin, "SALES_INVOICE", "DEFAULT",
				"031 辅助不可满足规则 " + suffix,
				List.of(ruleLine(1, "SALES_RECEIVABLE", "DEBIT", accountId(admin, "1122"), "031 辅助错误应收",
						List.of(ruleAuxiliary("CUSTOMER", "SOURCE_SUPPLIER"))),
						ruleLine(2, "SALES_REVENUE", "CREDIT", accountId(admin, "6001"), "031 辅助错误收入",
								List.of()),
						ruleLine(3, "OUTPUT_VAT", "CREDIT", accountId(admin, "2221.02"), "031 辅助错误销项税",
								List.of())));
		assertRuleInvalidOnValidateAndActivate(admin, impossibleAuxiliaryRule,
				"031-rule-impossible-auxiliary-" + suffix);
	}

	private JsonNode createPostingRule(AuthenticatedSession session, String sourceType, String sourceVariant,
			String name, List<Map<String, Object>> lines) throws Exception {
		return data(post(session, "/api/admin/gl/posting-rules",
				Map.of("sourceType", sourceType, "sourceVariant", sourceVariant, "name", name,
						"effectiveFrom", START_DATE.toString(), "idempotencyKey", "031-rule-"
								+ SEQUENCE.incrementAndGet(), "lines", lines)));
	}

	private Map<String, Object> ruleLine(int lineNo, String factCode, String direction, long accountId,
			String summary, List<Map<String, Object>> auxiliaryMappings) {
		return Map.of("lineNo", lineNo, "normalizedFactCode", factCode, "direction", direction, "accountId",
				accountId, "summaryTemplate", summary, "auxiliaryMappings", auxiliaryMappings);
	}

	private Map<String, Object> ruleAuxiliary(String dimensionCode, String mappingType) {
		return Map.of("dimensionCode", dimensionCode, "mappingType", mappingType);
	}

	private void assertRuleInvalidOnValidateAndActivate(AuthenticatedSession admin, JsonNode rule, String key)
			throws Exception {
		assertError(post(admin, "/api/admin/gl/posting-rules/" + rule.get("id").longValue() + "/validate",
				actionPayload(rule.get("version").longValue(), key + "-validate")), HttpStatus.BAD_REQUEST,
				"GL_RULE_INVALID");
		assertError(post(admin, "/api/admin/gl/posting-rules/" + rule.get("id").longValue() + "/activate",
				actionPayload(rule.get("version").longValue(), key + "-activate")), HttpStatus.BAD_REQUEST,
				"GL_RULE_INVALID");
	}

	private Map<String, Object> voucherPayload(String voucherType, LocalDate voucherDate, String summary,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("voucherType", voucherType);
		payload.put("voucherDate", voucherDate.toString());
		payload.put("summary", summary);
		payload.put("currency", "CNY");
		payload.put("version", 0);
		payload.put("idempotencyKey", "031-voucher-" + SEQUENCE.incrementAndGet());
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> line(int lineNo, long accountId, String summary, String debitAmount,
			String creditAmount, List<Map<String, Object>> auxiliaryItems) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("accountId", accountId);
		line.put("summary", summary);
		line.put("debitAmount", debitAmount == null ? "0.00" : debitAmount);
		line.put("creditAmount", creditAmount == null ? "0.00" : creditAmount);
		line.put("auxiliaryItems", auxiliaryItems);
		return line;
	}

	private Map<String, Object> accountPayload(Long parentId, String code, String name, String category,
			String direction, boolean postable, List<Map<String, Object>> auxiliaryRequirements) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("parentId", parentId);
		payload.put("code", code);
		payload.put("name", name);
		payload.put("category", category);
		payload.put("balanceDirection", direction);
		payload.put("postable", postable);
		payload.put("enabled", true);
		payload.put("auxiliaryRequirements", auxiliaryRequirements);
		payload.put("version", 0);
		payload.put("idempotencyKey", "031-account-" + SEQUENCE.incrementAndGet());
		return payload;
	}

	private Map<String, Object> actionPayload(long version, String idempotencyKey) {
		return Map.of("version", version, "idempotencyKey", idempotencyKey);
	}

	private Map<String, Object> approvalPayload(long version, String idempotencyKey) {
		return Map.of("version", version, "comment", "031 测试审批通过", "idempotencyKey", idempotencyKey);
	}

	private Map<String, Object> reversalPayload(long version, LocalDate voucherDate, String reason,
			String idempotencyKey) {
		return Map.of("version", version, "voucherDate", voucherDate.toString(), "reason", reason,
				"idempotencyKey", idempotencyKey);
	}

	private Map<String, Object> convertPayload(long version, String idempotencyKey) {
		return Map.of("version", version, "idempotencyKey", idempotencyKey);
	}

	private Map<String, Object> systemAuxiliary(String dimensionCode, long sourceId) {
		return Map.of("dimensionCode", dimensionCode, "sourceId", sourceId);
	}

	private long accountId(AuthenticatedSession session, String code) throws Exception {
		JsonNode accounts = data(get(session, "/api/admin/gl/accounts?keyword=" + code + "&page=1&pageSize=20"));
		for (JsonNode account : accounts.get("items")) {
			if (code.equals(account.get("code").asText())) {
				return account.get("id").longValue();
			}
		}
		throw new AssertionError("未找到科目 " + code);
	}

	private long insertCandidateAccountParentBySql(String code, String name) {
		long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'",
				Long.class);
		return this.jdbcTemplate.queryForObject("""
				insert into gl_account (
					ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable,
					enabled, template_source, created_by, created_at, updated_by, updated_at
				)
				values (?, null, ?, ?, 'ASSET', 'DEBIT', 1, false, false, true, 'USER_DEFINED',
					'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, code, name);
	}

	private long insertCandidateAccountBySql(long parentId, String code, String name) {
		long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'",
				Long.class);
		return this.jdbcTemplate.queryForObject("""
				insert into gl_account (
					ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable,
					enabled, template_source, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 'ASSET', 'DEBIT', 2, true, true, true, 'USER_DEFINED',
					'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, parentId, code, name);
	}

	private long insertCustomAuxDimensionBySql(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into gl_aux_dimension (
					code, name, dimension_type, object_source, system_defined, enabled, sort_order, created_by,
					created_at, updated_by, updated_at
				)
				values (?, ?, 'CUSTOM', null, false, true, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, 9000 + SEQUENCE.incrementAndGet());
	}

	private long insertCustomAuxItemBySql(long dimensionId, String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into gl_aux_item (
					dimension_id, code, name, enabled, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, dimensionId, code, name);
	}

	private long insertRuleAccountBySql(String code, String name, String category, String direction, boolean postable,
			boolean enabled) {
		long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'",
				Long.class);
		return this.jdbcTemplate.queryForObject("""
				insert into gl_account (
					ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable,
					enabled, template_source, created_by, created_at, updated_by, updated_at
				)
				values (?, null, ?, ?, ?, ?, 1, true, ?, ?, 'USER_DEFINED', 'test', now(), 'test', now())
				returning id
				""", Long.class, ledgerId, code, name, category, direction, postable, enabled);
	}

	private boolean itemsContainLong(JsonNode items, String fieldName, long expected) {
		for (JsonNode item : items) {
			if (item.hasNonNull(fieldName) && item.get(fieldName).longValue() == expected) {
				return true;
			}
		}
		return false;
	}

	private boolean itemsContainText(JsonNode items, String fieldName, String expected) {
		for (JsonNode item : items) {
			if (item.hasNonNull(fieldName) && expected.equals(item.get(fieldName).asText())) {
				return true;
			}
		}
		return false;
	}

	private long insertCustomer(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "客户");
	}

	private long insertSupplier(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "供应商");
	}

	private long insertUnit(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 2, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "单位");
	}

	private long insertWarehouse(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (
					code, name, warehouse_type, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'FINISHED_GOODS', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "仓库");
	}

	private long insertMaterial(int suffix, long unitId) {
		long categoryId = this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (
					code, name, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", Long.class, "031_CAT_" + suffix, "031 核销物料分类 " + suffix);
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '031 规格', 'FINISHED_GOOD', 'MANUFACTURED', ?, ?, 'ENABLED',
					'FINISHED_GOOD', 'VALUATED_MATERIAL', true, false, 'test', now(), 'test', now())
				returning id
				""", Long.class, "031_MAT_" + suffix, "031 核销物料 " + suffix, categoryId, unitId);
	}

	private long insertPostedSalesShipment(long customerId, int suffix, String amount) {
		long unitId = insertUnit("031_UNIT_" + suffix);
		long warehouseId = insertWarehouse("031_WH_" + suffix);
		long materialId = insertMaterial(suffix, unitId);
		BigDecimal quantity = new BigDecimal("1.000000");
		BigDecimal unitPrice = new BigDecimal(amount);
		BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);
		BigDecimal totalAmount = unitPrice.setScale(2);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', '031 核销销售订单', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "031-SO-" + suffix, customerId, START_DATE.plusDays(6), START_DATE.plusDays(30));
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, '031 核销销售订单行', 0, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId, quantity, quantity, unitPrice,
				START_DATE.plusDays(30), unitPrice, unitPrice, totalAmount, taxAmount, totalAmount);
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', '031 核销销售出库', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "031-SH-" + suffix, orderId, customerId, warehouseId, START_DATE.plusDays(6));
	}

	private FinanceDraftFixture insertReadyFinanceDraft(String sourceType, String amount) {
		int suffix = SEQUENCE.incrementAndGet();
		long sourceId = switch (sourceType) {
			case "SALES_INVOICE" -> insertSalesInvoiceSource(suffix);
			case "PURCHASE_INVOICE" -> insertPurchaseInvoiceSource(suffix);
			case "EXPENSE" -> insertExpenseSource(suffix);
			case "RECEIPT" -> insertReceiptSource(suffix, amount);
			case "PAYMENT" -> insertPaymentSource(suffix, amount);
			case "SETTLEMENT_ALLOCATION" -> insertSettlementAllocationSource(suffix, amount);
			default -> throw new IllegalArgumentException(sourceType);
		};
		long version = sourceVersion(sourceType, sourceId);
		long draftId = this.jdbcTemplate.queryForObject("""
				insert into fin_voucher_draft (
					draft_no, source_type, source_id, status, business_date, summary, party_type, party_id,
					party_name, ownership_type, debit_amount, credit_amount, generation_version, idempotency_key,
					request_fingerprint, created_by, created_at, updated_by, updated_at, ready_by, ready_at, version
				)
				values (?, ?, ?, 'READY', ?, ?, ?, ?, ?, 'PUBLIC', ?::numeric, ?::numeric, ?, ?, ?,
					'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-VD-" + sourceType + "-" + suffix, sourceType, sourceId, START_DATE.plusDays(6),
				"031 " + sourceType + " READY 草稿", partyType(sourceType), partyId(sourceType, sourceId),
				"031 " + sourceType + " 伙伴", amount, amount, version, "031-draft-" + sourceType + "-" + suffix,
				"031-fingerprint-" + sourceType + "-" + suffix);
		this.jdbcTemplate.update("""
				insert into fin_voucher_draft_line (
					draft_id, line_no, direction, business_category, amount, source_type, source_id, created_at
				)
				values (?, 1, 'DEBIT', '031_DEBIT_CATEGORY_SHOULD_NOT_BE_COPIED', ?::numeric, ?, ?, now()),
				       (?, 2, 'CREDIT', '031_CREDIT_CATEGORY_SHOULD_NOT_BE_COPIED', ?::numeric, ?, ?, now())
				""", draftId, amount, sourceType, sourceId, draftId, amount, sourceType, sourceId);
		return new FinanceDraftFixture(draftId, sourceType, sourceId, amount, version);
	}

	private FinanceDraftFixture insertUnsupportedReadyDraft(String sourceType) {
		int suffix = SEQUENCE.incrementAndGet();
		long sourceId = 310000L + suffix;
		long draftId = this.jdbcTemplate.queryForObject("""
				insert into fin_voucher_draft (
					draft_no, source_type, source_id, status, business_date, summary, ownership_type,
					debit_amount, credit_amount, generation_version, idempotency_key, request_fingerprint,
					created_by, created_at, updated_by, updated_at, ready_by, ready_at, version
				)
				values (?, ?, ?, 'READY', ?, ?, 'PUBLIC', 1.00, 1.00, 1, ?, ?,
					'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-UNSUPPORTED-" + sourceType + "-" + suffix, sourceType, sourceId,
				START_DATE.plusDays(6), "031 不支持来源", "031-unsupported-" + sourceType + "-" + suffix,
				"031-unsupported-fp-" + suffix);
		return new FinanceDraftFixture(draftId, sourceType, sourceId, "1.00", 1);
	}

	private long insertSalesInvoiceSource(int suffix) {
		long customerId = insertCustomer("031_SI_CUS_" + suffix);
		return this.jdbcTemplate.queryForObject("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, source_type, source_id, source_no, invoice_date,
					due_date, external_invoice_no, invoice_type, currency, tax_excluded_amount, tax_amount,
					tax_included_amount, status, party_snapshot, source_snapshot, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at, version
				)
				values (?, ?, 'PUBLIC', 'SALES_SHIPMENT', ?, ?, ?, ?, ?, 'SPECIAL_VAT', 'CNY',
					100.00, 13.00, 113.00, 'CONFIRMED', '{}'::jsonb, '{}'::jsonb, '031 销售发票来源',
					'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-SALES_INVOICE-" + suffix, customerId, 310000L + suffix,
				"031-SH-" + suffix, START_DATE.plusDays(6), START_DATE.plusDays(30), "031-SI-EXT-" + suffix);
	}

	private long insertPurchaseInvoiceSource(int suffix) {
		long supplierId = insertSupplier("031_PI_SUP_" + suffix);
		return this.jdbcTemplate.queryForObject("""
				insert into fin_purchase_invoice (
					invoice_no, supplier_id, settlement_kind, ownership_type, source_type, source_id, source_no,
					invoice_date, due_date, supplier_invoice_no, invoice_type, currency, match_status,
					tax_excluded_amount, tax_amount, tax_included_amount, status, party_snapshot, source_snapshot,
					remark, created_by, created_at, updated_by, updated_at, matched_by, matched_at,
					confirmed_by, confirmed_at, version
				)
				values (?, ?, 'STANDARD_PURCHASE', 'PUBLIC', 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?,
					'SPECIAL_VAT', 'CNY', 'MATCHED', 80.00, 10.40, 90.40, 'CONFIRMED',
					'{}'::jsonb, '{}'::jsonb, '031 采购发票来源', 'test', now(), 'test', now(),
					'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-PURCHASE_INVOICE-" + suffix, supplierId, 320000L + suffix,
				"031-PR-" + suffix, START_DATE.plusDays(6), START_DATE.plusDays(30),
				"031-PI-EXT-" + suffix);
	}

	private long insertExpenseSource(int suffix) {
		long supplierId = insertSupplier("031_EXP_SUP_" + suffix);
		return this.jdbcTemplate.queryForObject("""
				insert into fin_expense (
					expense_no, supplier_id, ownership_type, expense_date, due_date, invoice_type, currency,
					tax_excluded_amount, tax_amount, tax_included_amount, status, party_snapshot, source_snapshot,
					remark, created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at, version
				)
				values (?, ?, 'PUBLIC', ?, ?, 'GENERAL_VAT', 'CNY', 40.00, 5.20, 45.20,
					'CONFIRMED', '{}'::jsonb, '{}'::jsonb, '031 费用来源', 'test', now(),
					'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-EXPENSE-" + suffix, supplierId, START_DATE.plusDays(6),
				START_DATE.plusDays(30));
	}

	private long insertReceiptSource(int suffix, String amount) {
		long customerId = insertCustomer("031_RC_CUS_" + suffix);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status, remark, created_by,
					created_at, updated_by, updated_at, posted_by, posted_at, version
				)
				values (?, ?, ?, ?::numeric, 'BANK_TRANSFER', 'POSTED', '031 收款来源',
					'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-RECEIPT-" + suffix, customerId, START_DATE.plusDays(6), amount);
		this.jdbcTemplate.update("""
				insert into fin_receipt_balance (
					receipt_id, customer_id, ownership_type, original_amount, allocated_amount, available_amount,
					status, updated_at
				)
				values (?, ?, 'PUBLIC', ?::numeric, 0.00, ?::numeric, 'POSTED', now())
				""", receiptId, customerId, amount, amount);
		return receiptId;
	}

	private long insertPaymentSource(int suffix, String amount) {
		long supplierId = insertSupplier("031_PM_SUP_" + suffix);
		long paymentId = this.jdbcTemplate.queryForObject("""
				insert into fin_payment (
					payment_no, supplier_id, payment_date, amount, method, status, remark, created_by,
					created_at, updated_by, updated_at, posted_by, posted_at, version
				)
				values (?, ?, ?, ?::numeric, 'BANK_TRANSFER', 'POSTED', '031 付款来源',
					'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-PAYMENT-" + suffix, supplierId, START_DATE.plusDays(6), amount);
		this.jdbcTemplate.update("""
				insert into fin_payment_balance (
					payment_id, supplier_id, ownership_type, original_amount, allocated_amount, available_amount,
					status, updated_at
				)
				values (?, ?, 'PUBLIC', ?::numeric, 0.00, ?::numeric, 'POSTED', now())
				""", paymentId, supplierId, amount, amount);
		return paymentId;
	}

	private long insertSettlementAllocationSource(int suffix, String amount) {
		long receiptId = insertReceiptSource(suffix + 1000, amount);
		long customerId = this.jdbcTemplate.queryForObject("select customer_id from fin_receipt where id = ?",
				Long.class, receiptId);
		long shipmentId = insertPostedSalesShipment(customerId, suffix, amount);
		long receivableId = this.jdbcTemplate.queryForObject("""
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at, version
				)
				values (?, ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?::numeric, ?::numeric, 0.00, 'RECEIVED',
					'031 核销目标应收', 'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-AR-" + suffix, customerId, shipmentId, "031-AR-SRC-" + suffix,
				START_DATE.plusDays(6), START_DATE.plusDays(30), amount, amount);
		long allocationId = this.jdbcTemplate.queryForObject("""
				insert into fin_settlement_allocation (
					allocation_no, settlement_side, cash_source_type, cash_source_id, party_id, ownership_type,
					business_date, total_amount, status, idempotency_key, request_fingerprint, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at, version
				)
				values (?, 'RECEIVABLE', 'RECEIPT', ?, ?, 'PUBLIC', ?, ?::numeric, 'POSTED',
					?, ?, '031 核销来源', 'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "031-SETTLEMENT_ALLOCATION-" + suffix, receiptId, customerId,
				START_DATE.plusDays(6), amount, "031-alloc-" + suffix, "031-alloc-fp-" + suffix);
		this.jdbcTemplate.update("""
				insert into fin_settlement_allocation_line (
					allocation_id, line_no, target_type, target_id, amount, created_at
				)
				values (?, 1, 'RECEIVABLE', ?, ?::numeric, now())
				""", allocationId, receivableId, amount);
		return allocationId;
	}

	private String partyType(String sourceType) {
		return switch (sourceType) {
			case "SALES_INVOICE", "RECEIPT", "SETTLEMENT_ALLOCATION" -> "CUSTOMER";
			case "PURCHASE_INVOICE", "EXPENSE", "PAYMENT" -> "SUPPLIER";
			default -> null;
		};
	}

	private Long partyId(String sourceType, long sourceId) {
		return switch (sourceType) {
			case "SALES_INVOICE" -> this.jdbcTemplate.queryForObject(
					"select customer_id from fin_sales_invoice where id = ?", Long.class, sourceId);
			case "RECEIPT" -> this.jdbcTemplate.queryForObject("select customer_id from fin_receipt where id = ?",
					Long.class, sourceId);
			case "SETTLEMENT_ALLOCATION" -> this.jdbcTemplate.queryForObject(
					"select party_id from fin_settlement_allocation where id = ?", Long.class, sourceId);
			case "PURCHASE_INVOICE" -> this.jdbcTemplate.queryForObject(
					"select supplier_id from fin_purchase_invoice where id = ?", Long.class, sourceId);
			case "EXPENSE" -> this.jdbcTemplate.queryForObject("select supplier_id from fin_expense where id = ?",
					Long.class, sourceId);
			case "PAYMENT" -> this.jdbcTemplate.queryForObject("select supplier_id from fin_payment where id = ?",
					Long.class, sourceId);
			default -> null;
		};
	}

	private String salesInvoiceCustomerCode(long sourceId) {
		return this.jdbcTemplate.queryForObject("""
				select c.code
				from fin_sales_invoice i
				join mst_customer c on c.id = i.customer_id
				where i.id = ?
				""", String.class, sourceId);
	}

	private long sourceVersion(String sourceType, long sourceId) {
		String tableName = switch (sourceType) {
			case "SALES_INVOICE" -> "fin_sales_invoice";
			case "PURCHASE_INVOICE" -> "fin_purchase_invoice";
			case "EXPENSE" -> "fin_expense";
			case "RECEIPT" -> "fin_receipt";
			case "PAYMENT" -> "fin_payment";
			case "SETTLEMENT_ALLOCATION" -> "fin_settlement_allocation";
			default -> throw new IllegalArgumentException(sourceType);
		};
		return this.jdbcTemplate.queryForObject("select version from " + tableName + " where id = ?", Long.class,
				sourceId);
	}

	private String financeVoucherDraftSummary(long draftId) {
		return rowSummary("""
				select id, source_type, source_id, status, business_date, debit_amount, credit_amount,
				       generation_version, formal_voucher_no, posting_status, version
				from fin_voucher_draft
				where id = %d
				""".formatted(draftId));
	}

	private long activeSourceClaimCount(String sourceType, long sourceId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher_source_claim
				where source_type = ?
				and source_id = ?
				and status in ('RESERVED', 'POSTED')
				""", Long.class, sourceType, sourceId);
	}

	private void assertPostedLedgerFacts(long voucherId, String amount) {
		assertThat(ledgerEntryCount(voucherId)).isEqualTo(2);
		BigDecimal debit = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(debit_amount), 0)
				from gl_ledger_entry
				where voucher_id = ?
				""", BigDecimal.class, voucherId);
		BigDecimal credit = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(credit_amount), 0)
				from gl_ledger_entry
				where voucher_id = ?
				""", BigDecimal.class, voucherId);
		assertThat(debit).isEqualByComparingTo(amount);
		assertThat(credit).isEqualByComparingTo(amount);
	}

	private long approvalTaskIdForVoucher(long voucherId) {
		return this.jdbcTemplate.queryForObject("""
				select t.id
				from platform_approval_task t
				join platform_approval_instance i on i.id = t.instance_id
				join gl_voucher v on v.approval_instance_id = i.id
				where v.id = ?
				and t.status = 'PENDING'
				order by t.id desc
				limit 1
				""", Long.class, voucherId);
	}

	private long taskVersion(long taskId) {
		return this.jdbcTemplate.queryForObject("select version from platform_approval_task where id = ?",
				Long.class, taskId);
	}

	private long approvalInstanceId(long voucherId) {
		return this.jdbcTemplate.queryForObject("""
				select approval_instance_id
				from gl_voucher
				where id = ?
				""", Long.class, voucherId);
	}

	private String voucherStatus(long voucherId) {
		return this.jdbcTemplate.queryForObject("select status from gl_voucher where id = ?", String.class,
				voucherId);
	}

	private long voucherSequence(String periodCode) {
		Long value = this.jdbcTemplate.queryForObject("""
				select coalesce(max(s.last_number), 0)
				from gl_voucher_number_sequence s
				join gl_accounting_period p on p.id = s.period_id
				where p.period_code = ?
				and s.voucher_word = '记'
				""", Long.class, periodCode);
		return value == null ? 0 : value;
	}

	private long voucherNumber(JsonNode voucher) {
		String voucherNo = voucher.get("voucherNo").asText();
		return Long.parseLong(voucherNo.substring(voucherNo.lastIndexOf('-') + 1));
	}

	private long voucherLineId(long voucherId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from gl_voucher_line
				where voucher_id = ?
				order by line_no
				limit 1
				""", Long.class, voucherId);
	}

	private long ledgerEntryId(long voucherId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from gl_ledger_entry
				where voucher_id = ?
				order by id
				limit 1
				""", Long.class, voucherId);
	}

	private long ledgerEntryCount(long voucherId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_ledger_entry
				where voucher_id = ?
				""", Long.class, voucherId);
	}

	private long reversalLinkCount(long originalVoucherId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_voucher_reversal_link
				where original_voucher_id = ?
				and status in ('DRAFT', 'POSTED')
				""", Long.class, originalVoucherId);
	}

	private long auditErrorCount(String errorCode, long voucherId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_audit_event
				where resource_type = 'GL_VOUCHER'
				and resource_id = ?
				and error_code = ?
				""", Long.class, Long.toString(voucherId), errorCode);
	}

	private long permissionDeniedAuditCount(String method, String path) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = 'PERMISSION_DENIED'
				and request_method = ?
				and request_path = ?
				and result = 'FAILURE'
				and error_code = 'AUTH_FORBIDDEN'
				""", Long.class, method, path);
	}

	private void restorePeriodTotal(Map<String, Object> row) {
		this.jdbcTemplate.update("""
				update gl_account_period_total
				set opening_debit = ?::numeric,
				    opening_credit = ?::numeric,
				    period_debit = ?::numeric,
				    period_credit = ?::numeric,
				    ending_debit = ?::numeric,
				    ending_credit = ?::numeric
				where id = ?
				""", row.get("opening_debit"), row.get("opening_credit"), row.get("period_debit"),
				row.get("period_credit"), row.get("ending_debit"), row.get("ending_credit"), row.get("id"));
	}

	private long tableCount(String tableName) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
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

	private String rowSummary(String sql) {
		return this.jdbcTemplate.queryForObject("""
				select md5(coalesce(string_agg(row_to_json(t)::text, '|' order by row_to_json(t)::text), ''))
				from (%s) t
				""".formatted(sql), String.class);
	}

	private List<String> periodCodes(JsonNode page) {
		List<String> codes = new ArrayList<>();
		for (JsonNode item : page.get("items")) {
			codes.add(item.get("periodCode").asText());
		}
		return codes;
	}

	private JsonNode findItem(JsonNode items, String field, String value) {
		for (JsonNode item : items) {
			if (value.equals(item.get(field).asText())) {
				return item;
			}
		}
		throw new AssertionError("未找到 " + field + "=" + value);
	}

	private List<String> actionCodes(JsonNode actions) {
		List<String> codes = new ArrayList<>();
		for (JsonNode action : actions) {
			if (action.isTextual()) {
				codes.add(action.asText());
			}
			else if (action.has("code")) {
				codes.add(action.get("code").asText());
			}
		}
		return codes;
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

	private List<String> sourceSensitiveFields() {
		return List.of("sourceId", "sourceNo", "sourceVersion", "sourceFingerprint", "sourceOriginalId",
				"sourceOriginalNo", "sourceOriginalVersion", "sourceOriginalFingerprint", "businessSourceId",
				"businessSourceNo", "sourceRoute", "objectId", "objectCode", "objectName", "auxItemId");
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertThat(new BigDecimal(node.get(field).asText())).isEqualByComparingTo(expected);
	}

	private long insertDraftVoucherBySql(String voucherType, LocalDate voucherDate, String summary, String amount) {
		long ledgerId = this.jdbcTemplate.queryForObject("select id from gl_ledger where code = 'MAIN'",
				Long.class);
		long periodId = this.jdbcTemplate.queryForObject("""
				select id
				from gl_accounting_period
				where period_code = ?
				""", Long.class, START_PERIOD);
		int suffix = SEQUENCE.incrementAndGet();
		long voucherId = this.jdbcTemplate.queryForObject("""
				insert into gl_voucher (
					ledger_id, accounting_period_id, draft_no, voucher_type, voucher_date, status, summary,
					source_type, source_payload, currency, debit_total, credit_total, idempotency_key,
					request_fingerprint, created_by, created_at, updated_by, updated_at, version
				)
				values (?, ?, ?, ?, ?, 'DRAFT', ?, 'MANUAL', '{}'::jsonb, 'CNY', ?::numeric, ?::numeric,
					?, ?, 'test', now(), 'test', now(), 0)
				returning id
				""", Long.class, ledgerId, periodId, "031-SQL-DRAFT-" + suffix, voucherType, voucherDate,
				summary, amount, amount, "031-sql-draft-" + suffix, "031-sql-draft-fp-" + suffix);
		insertSqlVoucherLine(voucherId, 1, "1002", summary + " 借方", amount, "0.00");
		insertSqlVoucherLine(voucherId, 2, "4001", summary + " 贷方", "0.00", amount);
		return voucherId;
	}

	private void insertSqlVoucherLine(long voucherId, int lineNo, String accountCode, String summary,
			String debitAmount, String creditAmount) {
		this.jdbcTemplate.update("""
				insert into gl_voucher_line (
					voucher_id, line_no, summary, account_id, account_code, account_name, account_category,
					account_balance_direction, debit_amount, credit_amount, created_at
				)
				select ?, ?, ?, id, code, name, category, balance_direction, ?::numeric, ?::numeric, now()
				from gl_account
				where code = ?
				""", voucherId, lineNo, summary, debitAmount, creditAmount, accountCode);
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
				""", Long.class, rolePrefix + suffix, "031 总账测试角色" + suffix, "031 总账测试角色");
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
		return exchange(session, HttpMethod.POST, path, body);
	}

	private ResponseEntity<String> exchange(AuthenticatedSession session, HttpMethod method, String path, Object body) {
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

	private void assertOkUnchecked(ResponseEntity<String> response) {
		try {
			assertOk(response);
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
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

	private record FinanceDraftFixture(long draftId, String sourceType, long sourceId, String amount, long version) {
	}

}
