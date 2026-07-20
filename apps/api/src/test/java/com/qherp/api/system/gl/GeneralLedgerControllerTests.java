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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=general-ledger-controller")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeneralLedgerControllerTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

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
	void v33MigrationCreatesCoreTablesPermissionsApprovalSceneAndSeeds() {
		assertThat(existingTableCount(GL_TABLES)).isEqualTo(GL_TABLES.size());
		assertThat(permissionCount(GL_PERMISSIONS)).isEqualTo(GL_PERMISSIONS.size());
		assertThat(systemAdminPermissionCount(GL_PERMISSIONS)).isEqualTo(GL_PERMISSIONS.size());
		assertThat(approvalSceneCount("GL_VOUCHER_POST", "GL_VOUCHER")).isOne();
		assertThat(accountNames("1001", "1002", "1122", "2221.01", "2221.02", "4001", "6001"))
			.containsExactly("库存现金", "银行存款", "应收账款", "应交增值税-进项税额", "应交增值税-销项税额", "实收资本", "主营业务收入");
		assertThat(auxDimensionCodes()).containsExactly("CUSTOMER", "SUPPLIER", "PROJECT");
	}

	@Test
	@Order(2)
	void ledgerInitializationIsIdempotentAndOnlyCreatesSequentialOpenPeriods() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		JsonNode initialized = data(exchange(HttpMethod.POST, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2026-07", "idempotencyKey", "gl-init-202607"), admin));
		assertThat(initialized.get("code").asText()).isEqualTo("MAIN");
		assertThat(initialized.get("currency").asText()).isEqualTo("CNY");
		assertThat(initialized.get("initialized").booleanValue()).isTrue();
		assertThat(initialized.get("startPeriodCode").asText()).isEqualTo("2026-07");

		JsonNode repeated = data(exchange(HttpMethod.POST, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2026-07", "idempotencyKey", "gl-init-202607"), admin));
		assertThat(repeated.get("startPeriodCode").asText()).isEqualTo("2026-07");
		assertThat(repeated.get("version").longValue()).isEqualTo(initialized.get("version").longValue());

		assertError(exchange(HttpMethod.POST, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2026-06", "idempotencyKey", "gl-init-conflict"), admin),
				HttpStatus.CONFLICT, "GL_LEDGER_ALREADY_INITIALIZED");

		JsonNode nextPeriod = data(exchange(HttpMethod.POST, "/api/admin/gl/accounting-periods",
				Map.of("idempotencyKey", "gl-period-202608"), admin));
		assertThat(nextPeriod.get("periodCode").asText()).isEqualTo("2026-08");
		assertThat(nextPeriod.get("status").asText()).isEqualTo("OPEN");

		JsonNode periods = data(get("/api/admin/gl/accounting-periods?page=1&pageSize=7", admin));
		assertThat(periods.get("pageSize").intValue()).isEqualTo(10);
		assertThat(periodCodes(periods.get("items"))).contains("2026-07", "2026-08");
	}

	@Test
	@Order(4)
	void manualVoucherUsesApprovalToPostContinuousImmutableLedgerAndReverseDraft() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		AuthenticatedSession approver = createUserAndLogin("gl-approver-", "GL_APPROVER_",
				List.of("platform:todo:view", "gl:voucher:view", "gl:voucher:approve-post", "gl:amount:view",
						"gl:source:view"));

		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload("GENERAL", "2026-07-15", "实收资本存入银行", "100.00", "gl-manual-create-"),
				admin));
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("voucherNo").isNull()).isTrue();
		assertThat(draft.get("debitTotal").asText()).isEqualTo("100.00");
		assertThat(draft.get("creditTotal").asText()).isEqualTo("100.00");

		JsonNode submitted = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/" + draft.get("id").longValue() + "/submit",
				Map.of("version", draft.get("version").longValue(), "reason", "提交正式记账审批", "idempotencyKey",
						"gl-submit-" + draft.get("id").longValue()),
				admin));
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		long taskId = approvalTaskId(submitted.get("approvalSummary").get("id").longValue());
		long taskVersion = approvalTaskVersion(taskId);

		assertError(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion, "comment", "本人不能审批本人提交凭证", "idempotencyKey",
						"gl-self-approve-" + taskId),
				admin), HttpStatus.FORBIDDEN, "GL_APPROVAL_SELF_FORBIDDEN");

		JsonNode approved = data(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion, "comment", "通过并记账", "idempotencyKey", "gl-approve-" + taskId),
				approver));
		assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

		JsonNode posted = data(get("/api/admin/gl/vouchers/" + draft.get("id").longValue(), admin));
		assertThat(posted.get("status").asText()).isEqualTo("POSTED");
		assertThat(posted.get("voucherNo").asText()).matches("记-202607-\\d{4}");
		assertThat(posted.get("allowedActions").toString()).doesNotContain("UPDATE");
		assertThat(ledgerEntryCount(posted.get("id").longValue())).isEqualTo(2);

		JsonNode trial = data(get("/api/admin/gl/trial-balance?periodCode=2026-07", admin));
		assertThat(trial.get("balanced").booleanValue()).isTrue();
		assertThat(new BigDecimal(trial.get("periodDebit").asText())).isGreaterThanOrEqualTo(new BigDecimal("100.00"));
		assertThat(new BigDecimal(trial.get("periodCredit").asText())).isGreaterThanOrEqualTo(new BigDecimal("100.00"));

		assertError(exchange(HttpMethod.PUT, "/api/admin/gl/vouchers/" + posted.get("id").longValue(),
				manualVoucherPayload("GENERAL", "2026-07-16", "试图修改已记账凭证", "100.00",
						"gl-posted-update-"),
				admin), HttpStatus.CONFLICT, "GL_POSTED_IMMUTABLE");

		JsonNode reversal = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/" + posted.get("id").longValue() + "/reversals",
				Map.of("version", posted.get("version").longValue(), "reason", "冲销测试", "idempotencyKey",
						"gl-reversal-" + posted.get("id").longValue()),
				admin));
		assertThat(reversal.get("status").asText()).isEqualTo("DRAFT");
		assertThat(reversal.get("sourceType").asText()).isEqualTo("REVERSAL");
		assertThat(reversal.get("lines").get(0).get("creditAmount").asText()).isEqualTo("100.00");
		assertThat(reversal.get("lines").get(1).get("debitAmount").asText()).isEqualTo("100.00");
	}

	@Test
	@Order(5)
	void financeDraftConversionRereadsSalesFactsReservesSourceAndMasksRestrictedFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		FinanceDraftFixture fixture = insertReadySalesVoucherDraft();

		JsonNode glDraft = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/from-finance-draft/" + fixture.draftId(),
				Map.of("version", fixture.draftVersion(), "idempotencyKey", "gl-convert-sales-" + fixture.draftId()),
				admin));
		assertThat(glDraft.get("sourceType").asText()).isEqualTo("FIN_VOUCHER_DRAFT");
		assertThat(glDraft.get("sourceId").longValue()).isEqualTo(fixture.draftId());
		assertThat(glDraft.get("sourceNo").asText()).startsWith("FIN-VD-");
		assertThat(glDraft.get("sourceOriginalType").asText()).isEqualTo("SALES_INVOICE");
		assertThat(glDraft.get("sourceOriginalNo").asText()).isEqualTo(fixture.invoiceNo());
		assertThat(lineByFact(glDraft, "SALES_RECEIVABLE").get("debitAmount").asText()).isEqualTo("113.00");
		assertThat(lineByFact(glDraft, "SALES_REVENUE").get("creditAmount").asText()).isEqualTo("100.00");
		JsonNode outputVatLine = lineByFact(glDraft, "OUTPUT_VAT");
		assertThat(outputVatLine.get("creditAmount").asText()).isEqualTo("13.00");
		assertThat(outputVatLine.get("sourceType").asText()).isEqualTo("SALES_INVOICE");
		assertThat(outputVatLine.get("sourceId").longValue()).isEqualTo(glDraft.get("sourceOriginalId").longValue());
		assertThat(outputVatLine.get("sourceNo").asText()).isEqualTo(fixture.invoiceNo());
		assertThat(finVoucherDraftFormalNo(fixture.draftId())).isNull();

		JsonNode replay = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/from-finance-draft/" + fixture.draftId(),
				Map.of("version", fixture.draftVersion(), "idempotencyKey", "gl-convert-sales-" + fixture.draftId()),
				admin));
		assertThat(replay.get("id").longValue()).isEqualTo(glDraft.get("id").longValue());

		assertError(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/from-finance-draft/" + fixture.draftId(),
				Map.of("version", fixture.draftVersion(), "idempotencyKey",
						"gl-convert-sales-conflict-" + fixture.draftId()),
				admin), HttpStatus.CONFLICT, "GL_SOURCE_ALREADY_ACCOUNTED");

		AuthenticatedSession restricted = createUserAndLogin("gl-restricted-", "GL_RESTRICTED_",
				List.of("gl:voucher:view"));
		JsonNode masked = data(get("/api/admin/gl/vouchers/" + glDraft.get("id").longValue(), restricted));
		assertThat(masked.get("amountVisible").booleanValue()).isFalse();
		assertThat(masked.get("debitTotal").isNull()).isTrue();
		assertThat(masked.get("creditTotal").isNull()).isTrue();
		assertThat(masked.get("sourceVisible").booleanValue()).isFalse();
		assertThat(masked.get("sourceId").isNull()).isTrue();
		assertThat(masked.get("sourceNo").isNull()).isTrue();
		assertThat(masked.get("lines").get(0).get("debitAmount").isNull()).isTrue();
		assertThat(masked.get("lines").get(0).get("sourceRoute").isNull()).isTrue();

		JsonNode hiddenKeywordPage = data(get("/api/admin/gl/vouchers?keyword="
				+ glDraft.get("sourceNo").asText() + "&page=1&pageSize=10", restricted));
		assertThat(hiddenKeywordPage.get("total").longValue()).isZero();

		JsonNode sourceFiltered = data(get("/api/admin/gl/vouchers?sourceType=FIN_VOUCHER_DRAFT&sourceId="
				+ fixture.draftId() + "&page=1&pageSize=10", admin));
		assertThat(sourceFiltered.get("total").longValue()).isEqualTo(1L);
		assertThat(sourceFiltered.get("items").get(0).get("id").longValue()).isEqualTo(glDraft.get("id").longValue());

		JsonNode sourceClaims = data(get("/api/admin/gl/source-claims?sourceType=SALES_INVOICE&sourceId="
				+ glDraft.get("sourceOriginalId").longValue() + "&page=1&pageSize=10", admin));
		assertThat(sourceClaims.get("total").longValue()).isEqualTo(1L);
		assertThat(sourceClaims.get("items").get(0).get("voucherId").longValue()).isEqualTo(glDraft.get("id").longValue());
		assertError(get("/api/admin/gl/source-claims?sourceType=SALES_INVOICE&page=1&pageSize=10", restricted),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	@Order(6)
	void voucherLifecycleUsesFrozenStringActionsAndWithdrawsSubmittedVoucher() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);

		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload("GENERAL", "2026-07-17", "撤回状态机测试", "12.00", "gl-withdraw-create-"),
				admin));
		assertThat(textValues(draft.get("allowedActions"))).contains("UPDATE", "SUBMIT", "CANCEL");
		for (JsonNode action : draft.get("allowedActions")) {
			assertThat(action.isTextual()).isTrue();
		}

		JsonNode submitted = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/" + draft.get("id").longValue() + "/submit",
				Map.of("version", draft.get("version").longValue(), "reason", "提交后撤回", "idempotencyKey",
						"gl-withdraw-submit-" + draft.get("id").longValue()),
				admin));
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(textValues(submitted.get("allowedActions"))).contains("WITHDRAW");

		JsonNode withdrawn = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/" + draft.get("id").longValue() + "/withdraw",
				Map.of("version", submitted.get("version").longValue(), "reason", "撤回重填", "idempotencyKey",
						"gl-withdraw-" + draft.get("id").longValue()),
				admin));
		assertThat(withdrawn.get("status").asText()).isEqualTo("DRAFT");
		assertThat(textValues(withdrawn.get("allowedActions"))).contains("SUBMIT", "CANCEL");

		JsonNode replay = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/" + draft.get("id").longValue() + "/withdraw",
				Map.of("version", submitted.get("version").longValue(), "reason", "撤回重填", "idempotencyKey",
						"gl-withdraw-" + draft.get("id").longValue()),
				admin));
		assertThat(replay.get("id").longValue()).isEqualTo(draft.get("id").longValue());
		assertThat(replay.get("status").asText()).isEqualTo("DRAFT");
	}

	@Test
	@Order(3)
	void openingVoucherIsBlockedAfterAnyGeneralVoucherPosted() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);

		JsonNode openingDraft = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload("OPENING", "2026-07-01", "普通记账后不得提交期初", "20.00",
						"gl-opening-before-general-"),
				admin));
		postGeneralVoucher(admin, "2026-07-18", "阻断期初的普通凭证", "33.00");

		assertError(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + openingDraft.get("id").longValue()
				+ "/submit", Map.of("version", openingDraft.get("version").longValue(), "reason", "提交期初",
				"idempotencyKey", "gl-opening-submit-blocked-" + openingDraft.get("id").longValue()), admin),
				HttpStatus.CONFLICT, "GL_PERIOD_NOT_OPEN");
		assertError(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload("OPENING", "2026-07-01", "普通记账后不得新增期初", "21.00",
						"gl-opening-after-general-"),
				admin), HttpStatus.CONFLICT, "GL_PERIOD_NOT_OPEN");
	}

	@Test
	@Order(7)
	void referencedAccountsAreProtectedAndExposeFrozenActionState() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode account = data(get("/api/admin/gl/accounts/" + accountId("1122"), admin));
		assertThat(account.get("level").intValue()).isEqualTo(1);
		assertThat(account.get("referenced").booleanValue()).isTrue();
		assertThat(textValues(account.get("allowedActions"))).contains("UPDATE");
		assertThat(account.get("actionDisabledReasons").get("DISABLE").asText()).contains("制证规则");

		Map<String, Object> illegalUpdate = new LinkedHashMap<>();
		illegalUpdate.put("parentId", null);
		illegalUpdate.put("code", "1122");
		illegalUpdate.put("name", "应收账款");
		illegalUpdate.put("category", "ASSET");
		illegalUpdate.put("balanceDirection", "DEBIT");
		illegalUpdate.put("postable", false);
		illegalUpdate.put("enabled", true);
		illegalUpdate.put("version", account.get("version").longValue());
		illegalUpdate.put("idempotencyKey", "gl-account-lock-" + SEQUENCE.incrementAndGet());
		illegalUpdate.put("auxiliaryRequirements", List.of(
				Map.of("dimensionCode", "CUSTOMER", "requirementType", "OPTIONAL")));
		assertError(exchange(HttpMethod.PUT, "/api/admin/gl/accounts/" + accountId("1122"), illegalUpdate, admin),
				HttpStatus.CONFLICT, "GL_ACCOUNT_LOCKED");
		assertError(exchange(HttpMethod.POST, "/api/admin/gl/accounts/" + accountId("1122") + "/disable",
				Map.of("version", account.get("version").longValue(), "reason", "活动规则引用", "idempotencyKey",
						"gl-account-disable-" + SEQUENCE.incrementAndGet()),
				admin), HttpStatus.CONFLICT, "GL_ACCOUNT_LOCKED");
	}

	@Test
	@Order(8)
	void auxiliaryDimensionsCustomItemsAndCandidatesAreMaintainable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		String code = "CUSTOM_DIM_" + SEQUENCE.incrementAndGet();

		JsonNode dimension = data(exchange(HttpMethod.POST, "/api/admin/gl/aux-dimensions",
				Map.of("code", code, "name", "自定义辅助", "enabled", true, "idempotencyKey",
						"gl-aux-dim-" + code),
				admin));
		assertThat(dimension.get("dimensionType").asText()).isEqualTo("CUSTOM");
		assertThat(textValues(dimension.get("allowedActions"))).contains("UPDATE", "DISABLE");

		JsonNode item = null;
		for (int index = 1; index <= 12; index++) {
			item = data(exchange(HttpMethod.POST,
					"/api/admin/gl/aux-dimensions/" + dimension.get("id").longValue() + "/items",
					Map.of("code", "ITEM-" + code + "-" + "%02d".formatted(index), "name", "自定义项目" + index,
							"enabled", true, "idempotencyKey", "gl-aux-item-" + code + "-" + index),
					admin));
		}
		assertThat(item).isNotNull();
		assertThat(item.get("objectCode").asText()).isEqualTo("ITEM-" + code + "-12");

		JsonNode items = data(get("/api/admin/gl/aux-dimensions/" + dimension.get("id").longValue()
				+ "/items?page=1&pageSize=10", admin));
		assertThat(items.get("total").longValue()).isEqualTo(12L);
		JsonNode candidates = data(get("/api/admin/gl/aux-dimensions/" + code + "/candidates?keyword=missing"
				+ "&selectedIds=" + item.get("objectId").longValue() + "&page=1&pageSize=10", admin));
		assertThat(candidates.get("items").get(0).get("objectId").longValue()).isEqualTo(item.get("objectId").longValue());
		JsonNode candidatesPage2 = data(get("/api/admin/gl/aux-dimensions/" + code
				+ "/candidates?keyword=ITEM-" + code + "&page=2&pageSize=10", admin));
		assertThat(candidatesPage2.get("page").intValue()).isEqualTo(2);
		assertThat(candidatesPage2.get("items").size()).isGreaterThan(0);

		JsonNode disabled = data(exchange(HttpMethod.PUT,
				"/api/admin/gl/aux-dimensions/" + dimension.get("id").longValue() + "/items/"
						+ item.get("objectId").longValue(),
				Map.of("code", "ITEM-" + code, "name", "自定义项目停用", "enabled", false, "version",
						item.get("version").longValue(), "idempotencyKey", "gl-aux-item-disable-" + code),
				admin));
		assertThat(disabled.get("enabled").booleanValue()).isFalse();
	}

	@Test
	@Order(9)
	void postingRulesSupportDetailsNewVersionValidationActivationAndDisable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		long activeRuleId = this.jdbcTemplate.queryForObject("""
				select id
				from gl_posting_rule
				where source_type = 'SALES_INVOICE'
				and source_variant = 'DEFAULT'
				and status = 'ACTIVE'
				""", Long.class);

		JsonNode activeRule = data(get("/api/admin/gl/posting-rules/" + activeRuleId, admin));
		assertThat(activeRule.get("versionNo").intValue()).isEqualTo(1);
		assertThat(activeRule.get("lineCount").intValue()).isGreaterThanOrEqualTo(3);
		assertThat(activeRule.get("lines").size()).isGreaterThanOrEqualTo(3);

		JsonNode draftVersion = data(exchange(HttpMethod.POST,
				"/api/admin/gl/posting-rules/" + activeRuleId + "/new-version",
				Map.of("version", activeRule.get("version").longValue(), "reason", "复制新版本", "idempotencyKey",
						"gl-rule-new-version-" + activeRuleId),
				admin));
		assertThat(draftVersion.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draftVersion.get("versionNo").intValue()).isEqualTo(2);

		JsonNode validated = data(exchange(HttpMethod.POST,
				"/api/admin/gl/posting-rules/" + draftVersion.get("id").longValue() + "/validate",
				Map.of("version", draftVersion.get("version").longValue(), "idempotencyKey",
						"gl-rule-validate-" + draftVersion.get("id").longValue()),
				admin));
		assertThat(validated.get("validationStatus").asText()).isEqualTo("VALID");
		assertThat(validated.get("validationSummary").get("balanced").booleanValue()).isTrue();

		JsonNode activated = data(exchange(HttpMethod.POST,
				"/api/admin/gl/posting-rules/" + draftVersion.get("id").longValue() + "/activate",
				Map.of("version", validated.get("version").longValue(), "idempotencyKey",
						"gl-rule-activate-" + draftVersion.get("id").longValue()),
				admin));
		assertThat(activated.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(activeRuleCount("SALES_INVOICE", "DEFAULT")).isOne();

		JsonNode disabled = data(exchange(HttpMethod.POST,
				"/api/admin/gl/posting-rules/" + activated.get("id").longValue() + "/disable",
				Map.of("version", activated.get("version").longValue(), "reason", "停用测试", "idempotencyKey",
						"gl-rule-disable-" + activated.get("id").longValue()),
				admin));
		assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
		assertThat(activeRuleCount("SALES_INVOICE", "DEFAULT")).isZero();
	}

	@Test
	@Order(10)
	void periodsCandidatesLedgersAndTrialBalanceExposeFrozenDtosAndFilters() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode posted = postGeneralVoucher(admin, "2026-07-19", "账簿 DTO 测试", "44.00");

		JsonNode periods = data(get("/api/admin/gl/accounting-periods?periodCode=2026-07&page=1&pageSize=10", admin));
		assertThat(periods.get("items").get(0).get("voucherCount").longValue()).isGreaterThanOrEqualTo(1L);
		assertThat(periods.get("items").get(0).get("lastPostedAt").isNull()).isFalse();

		JsonNode accountCandidates = data(get("/api/admin/gl/accounts/candidates?keyword=不存在&selectedIds="
				+ accountId("1002") + "&page=1&pageSize=10", admin));
		assertThat(accountCandidates.get("items").get(0).get("accountCode").asText()).isEqualTo("1002");
		JsonNode accountCandidatesPage2 = data(get("/api/admin/gl/accounts/candidates?keyword=1&page=2&pageSize=10",
				admin));
		assertThat(accountCandidatesPage2.get("page").intValue()).isEqualTo(2);
		assertThat(accountCandidatesPage2.get("items").size()).isGreaterThan(0);

		JsonNode general = data(get("/api/admin/gl/ledgers/general?periodCode=2026-07&accountKeyword=1002"
				+ "&level=1&page=1&pageSize=10", admin));
		assertThat(general.get("items").get(0).get("balanceDirection").asText()).isEqualTo("DEBIT");
		assertThat(general.get("items").get(0).get("periodCode").asText()).isEqualTo("2026-07");

		JsonNode detail = data(get("/api/admin/gl/ledgers/detail?periodCode=2026-07&accountKeyword=1002&voucherNo="
				+ posted.get("voucherNo").asText() + "&page=1&pageSize=10", admin));
		assertThat(detail.get("items").get(0).get("runningBalance").isNull()).isFalse();
		assertThat(detail.get("items").get(0).get("balanceDirection").asText()).isEqualTo("DEBIT");
		assertThat(detail.get("items").get(0).get("sourceSummary").isNull()).isTrue();

		JsonNode trial = data(get("/api/admin/gl/trial-balance?periodCode=2026-07", admin));
		assertThat(trial.get("openingDebitTotal").isNull()).isFalse();
		assertThat(trial.get("periodDebitTotal").isNull()).isFalse();
		assertThat(trial.get("endingDebitTotal").isNull()).isFalse();
		assertThat(trial.get("differenceAmount").isNull()).isFalse();
		assertThat(trial.get("differences").isArray()).isTrue();
	}

	@Test
	@Order(11)
	void highRiskVoucherActionsReplayIdempotentlyAndConflictOnFingerprintMismatch() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ensureLedgerInitialized(admin);
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload("GENERAL", "2026-07-20", "取消幂等测试", "15.00", "gl-cancel-create-"),
				admin));
		JsonNode cancelled = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + draft.get("id").longValue()
				+ "/cancel", Map.of("version", draft.get("version").longValue(), "reason", "取消", "idempotencyKey",
				"gl-cancel-action-" + draft.get("id").longValue()), admin));
		JsonNode cancelReplay = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + draft.get("id").longValue()
				+ "/cancel", Map.of("version", draft.get("version").longValue(), "reason", "取消", "idempotencyKey",
				"gl-cancel-action-" + draft.get("id").longValue()), admin));
		assertThat(cancelReplay.get("status").asText()).isEqualTo(cancelled.get("status").asText());
		assertError(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + draft.get("id").longValue() + "/cancel",
				Map.of("version", draft.get("version").longValue(), "reason", "不同原因", "idempotencyKey",
						"gl-cancel-action-" + draft.get("id").longValue()),
				admin), HttpStatus.CONFLICT, "GL_IDEMPOTENCY_CONFLICT");

		JsonNode posted = postGeneralVoucher(admin, "2026-07-21", "冲销幂等测试", "16.00");
		JsonNode reversal = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + posted.get("id").longValue()
				+ "/reversals", Map.of("version", posted.get("version").longValue(), "reason", "冲销幂等",
				"idempotencyKey", "gl-reversal-action-" + posted.get("id").longValue()), admin));
		JsonNode reversalReplay = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + posted.get("id").longValue()
				+ "/reversals", Map.of("version", posted.get("version").longValue(), "reason", "冲销幂等",
				"idempotencyKey", "gl-reversal-action-" + posted.get("id").longValue()), admin));
		assertThat(reversalReplay.get("id").longValue()).isEqualTo(reversal.get("id").longValue());
		assertError(exchange(HttpMethod.POST, "/api/admin/gl/vouchers/" + posted.get("id").longValue() + "/reversals",
				Map.of("version", posted.get("version").longValue(), "reason", "冲销幂等冲突", "idempotencyKey",
						"gl-reversal-action-" + posted.get("id").longValue()),
				admin), HttpStatus.CONFLICT, "GL_IDEMPOTENCY_CONFLICT");
	}

	private JsonNode postGeneralVoucher(AuthenticatedSession admin, String voucherDate, String summary, String amount)
			throws Exception {
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/gl/vouchers",
				manualVoucherPayload("GENERAL", voucherDate, summary, amount, "gl-post-helper-"), admin));
		JsonNode submitted = data(exchange(HttpMethod.POST,
				"/api/admin/gl/vouchers/" + draft.get("id").longValue() + "/submit",
				Map.of("version", draft.get("version").longValue(), "reason", "提交并记账", "idempotencyKey",
						"gl-post-helper-submit-" + draft.get("id").longValue()),
				admin));
		AuthenticatedSession approver = createUserAndLogin("gl-helper-approver-", "GL_HELPER_APPROVER_",
				List.of("platform:todo:view", "gl:voucher:view", "gl:voucher:approve-post", "gl:amount:view",
						"gl:source:view"));
		long taskId = approvalTaskId(submitted.get("approvalSummary").get("id").longValue());
		long taskVersion = approvalTaskVersion(taskId);
		data(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + taskId + "/approve",
				Map.of("version", taskVersion, "comment", "通过并记账", "idempotencyKey",
						"gl-post-helper-approve-" + taskId),
				approver));
		return data(get("/api/admin/gl/vouchers/" + draft.get("id").longValue(), admin));
	}

	private List<String> textValues(JsonNode array) {
		java.util.ArrayList<String> values = new java.util.ArrayList<>();
		for (JsonNode item : array) {
			values.add(item.asText());
		}
		return values;
	}

	private long activeRuleCount(String sourceType, String sourceVariant) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from gl_posting_rule
				where source_type = ?
				and source_variant = ?
				and status = 'ACTIVE'
				""", Long.class, sourceType, sourceVariant);
		return count == null ? 0 : count;
	}

	private void ensureLedgerInitialized(AuthenticatedSession admin) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/gl/ledger/initialize",
				Map.of("startYearMonth", "2026-07", "idempotencyKey", "gl-init-shared"), admin);
		if (response.getStatusCode() == HttpStatus.OK) {
			return;
		}
		assertThat(code(response)).isEqualTo("GL_LEDGER_ALREADY_INITIALIZED");
	}

	private Map<String, Object> manualVoucherPayload(String voucherType, String voucherDate, String summary,
			String amount, String keyPrefix) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("voucherType", voucherType);
		payload.put("voucherDate", voucherDate);
		payload.put("summary", summary);
		payload.put("version", 0);
		payload.put("idempotencyKey", keyPrefix + SEQUENCE.incrementAndGet());
		payload.put("lines", List.of(
				Map.of("lineNo", 1, "summary", "银行存款", "accountId", accountId("1002"), "debitAmount", amount,
						"creditAmount", "0.00"),
				Map.of("lineNo", 2, "summary", "实收资本", "accountId", accountId("4001"), "debitAmount", "0.00",
						"creditAmount", amount)));
		return payload;
	}

	private FinanceDraftFixture insertReadySalesVoucherDraft() {
		int suffix = SEQUENCE.incrementAndGet();
		Long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "GL-CUS-" + suffix, "GL 客户" + suffix);
		String invoiceNo = "GL-SI-" + suffix;
		Long invoiceId = this.jdbcTemplate.queryForObject("""
				insert into fin_sales_invoice (
					invoice_no, customer_id, ownership_type, source_type, source_id, source_no, invoice_date,
					due_date, invoice_type, currency, tax_excluded_amount, tax_amount, tax_included_amount,
					status, party_snapshot, source_snapshot, created_by, created_at, updated_by, updated_at,
					confirmed_by, confirmed_at, version
				)
				values (?, ?, 'PUBLIC', 'SALES_SHIPMENT', ?, ?, '2026-07-12', '2026-08-11', 'SPECIAL_VAT',
					'CNY', 100.00, 13.00, 113.00, 'CONFIRMED', '{}'::jsonb, '{}'::jsonb,
					'test', now(), 'test', now(), 'test', now(), 3)
				returning id
				""", Long.class, invoiceNo, customerId, suffix, "GL-SHIP-" + suffix);
		Long draftId = this.jdbcTemplate.queryForObject("""
				insert into fin_voucher_draft (
					draft_no, source_type, source_id, status, business_date, summary, party_type, party_id,
					party_name, ownership_type, debit_amount, credit_amount, generation_version,
					created_by, created_at, updated_by, updated_at, ready_by, ready_at, version
				)
				values (?, 'SALES_INVOICE', ?, 'READY', '2026-07-12', ?, 'CUSTOMER', ?, ?,
					'PUBLIC', 113.00, 113.00, 3, 'test', now(), 'test', now(), 'test', now(), 1)
				returning id
				""", Long.class, "FIN-VD-" + suffix, invoiceId, "销售发票 " + invoiceNo, customerId,
				"GL 客户" + suffix);
		this.jdbcTemplate.update("""
				insert into fin_voucher_draft_line (
					draft_id, line_no, direction, business_category, amount, source_type, source_id, created_at
				)
				values (?, 1, 'DEBIT', 'RECEIVABLE_DRAFT', 113.00, 'SALES_INVOICE', ?, now()),
				       (?, 2, 'CREDIT', 'SALES_INCOME_DRAFT', 113.00, 'SALES_INVOICE', ?, now())
				""", draftId, invoiceId, draftId, invoiceId);
		return new FinanceDraftFixture(draftId, 1L, invoiceNo);
	}

	private JsonNode lineByFact(JsonNode voucher, String factCode) {
		for (JsonNode line : voucher.get("lines")) {
			if (factCode.equals(line.get("normalizedFactCode").asText())) {
				return line;
			}
		}
		throw new AssertionError("未找到分录事实：" + factCode);
	}

	private long accountId(String code) {
		return this.jdbcTemplate.queryForObject("select id from gl_account where code = ?", Long.class, code);
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
		return this.jdbcTemplate.queryForObject("select version from platform_approval_task where id = ?", Long.class,
				taskId);
	}

	private long ledgerEntryCount(long voucherId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from gl_ledger_entry where voucher_id = ?",
				Long.class, voucherId);
		return count == null ? 0 : count;
	}

	private String finVoucherDraftFormalNo(long draftId) {
		return this.jdbcTemplate.queryForObject("select formal_voucher_no from fin_voucher_draft where id = ?",
				String.class, draftId);
	}

	private List<String> periodCodes(JsonNode items) {
		java.util.ArrayList<String> result = new java.util.ArrayList<>();
		for (JsonNode item : items) {
			result.add(item.get("periodCode").asText());
		}
		return result;
	}

	private long existingTableCount(List<String> tableNames) {
		String[] names = tableNames.stream().map((name) -> "public." + name).toArray(String[]::new);
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from unnest(?::text[]) as t(name)
				where to_regclass(t.name) is not null
				""", Long.class, (Object) names);
		return count == null ? 0 : count;
	}

	private long permissionCount(List<String> permissionCodes) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_permission
				where code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private long systemAdminPermissionCount(List<String> permissionCodes) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role r
				join sys_role_permission rp on rp.role_id = r.id
				join sys_permission p on p.id = rp.permission_id
				where r.code = 'SYSTEM_ADMIN'
				and p.code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private long approvalSceneCount(String sceneCode, String objectType) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_definition d
				join platform_approval_definition_step s on s.definition_id = d.id
				where d.scene_code = ?
				and d.business_object_type = ?
				and s.candidate_permission_code = 'gl:voucher:approve-post'
				""", Long.class, sceneCode, objectType);
		return count == null ? 0 : count;
	}

	private List<String> accountNames(String... codes) {
		return this.jdbcTemplate.queryForList("""
				select name
				from gl_account
				where code = any (?::text[])
				order by array_position(?::text[], code)
				""", String.class, (Object) codes, (Object) codes);
	}

	private List<String> auxDimensionCodes() {
		return this.jdbcTemplate.queryForList("""
				select code
				from gl_aux_dimension
				where dimension_type = 'SYSTEM'
				order by sort_order asc
				""", String.class);
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		Long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, rolePrefix + suffix);
		Long userId = this.jdbcTemplate.queryForObject("""
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
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode body = this.objectMapper.readTree(response.getBody());
		assertThat(body.get("code").asText()).isEqualTo("OK");
		return body.get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String expectedCode) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
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

	private record FinanceDraftFixture(Long draftId, Long draftVersion, String invoiceNo) {
	}

}
