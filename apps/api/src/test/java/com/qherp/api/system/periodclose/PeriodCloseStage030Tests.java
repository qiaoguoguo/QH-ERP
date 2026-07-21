package com.qherp.api.system.periodclose;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
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
		properties = "qherp.test.context=stage030-period-close")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PeriodCloseStage030Tests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final List<String> PERIOD_CLOSE_TABLES = List.of("biz_period_close_run",
			"biz_period_close_check_run", "biz_period_close_check_item", "biz_period_snapshot",
			"biz_period_inventory_snapshot", "biz_period_inventory_summary", "biz_period_wip_snapshot",
			"biz_period_project_cost_snapshot", "biz_period_report_snapshot",
			"biz_period_close_action_idempotency", "biz_period_close_audit");

	private static final List<String> STAGE_033_BUSINESS_SNAPSHOT_REPORT_CODES = List.of("PROJECT_PROFIT",
			"CONTRACT_COLLECTION", "PROCUREMENT_VARIANCE", "INVENTORY_CAPITAL", "RECEIVABLE_PAYABLE");

	private static final List<String> STAGE_033_NON_SNAPSHOT_REPORT_CODES = List.of(
			"OPERATING_ACCOUNTING_RECONCILIATION", "FINANCIAL_SUMMARY");

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void clean030StocktakeFixtures() {
		this.jdbcTemplate.update("delete from inv_stocktake where stocktake_no like '030-%'");
	}

	@Test
	void 历史手工锁期不补认快照且关闭前期间被锁必须失败无半写() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long manualLockedPeriodId = createPeriod(admin, "2051-01", LocalDate.of(2051, 1, 1),
				LocalDate.of(2051, 1, 31));
		assertOk(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + manualLockedPeriodId + "/lock",
				Map.of("reason", "030 历史手工锁期基线"), admin));

		JsonNode summary = data(get("/api/admin/period-closes/periods/" + manualLockedPeriodId, admin));
		assertThat(summary.get("periodStatus").asText()).isEqualTo("LOCKED");
		assertThat(summary.get("status").asText()).isEqualTo("MANUAL_LOCKED_WITHOUT_SNAPSHOT");
		assertThat(summary.get("currentRunId").isNull()).isTrue();
		assertThat(summary.get("history")).hasSize(0);
		assertThat(actionCodes(summary.get("allowedActions"))).doesNotContain("CHECK");
		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", manualLockedPeriodId, "idempotencyKey",
						"030-locked-check-" + manualLockedPeriodId),
				admin), HttpStatus.BAD_REQUEST, "PERIOD_CLOSE_PERIOD_INVALID");
		assertThat(currentClosedRunCount(manualLockedPeriodId)).isZero();
		assertThat(snapshotCount(manualLockedPeriodId)).isZero();
		assertThat(periodStatus(manualLockedPeriodId)).isEqualTo("LOCKED");

		long periodId = createPeriod(admin, "2051-06", LocalDate.of(2051, 6, 1), LocalDate.of(2051, 6, 30));
		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-pre-lock-check-" + periodId), admin));
		assertThat(check.get("status").asText()).isEqualTo("READY");
		assertOk(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + periodId + "/lock",
				Map.of("reason", "关闭前外部锁期"), admin));
		ResponseEntity<String> close = exchange(HttpMethod.POST,
				"/api/admin/period-closes/" + check.get("id").longValue() + "/close",
				closePayload(check, "030-pre-lock-close-" + periodId, true, "期间已锁不得关闭"), admin);
		assertError(close, HttpStatus.BAD_REQUEST, "PERIOD_CLOSE_PERIOD_INVALID");
		assertThat(currentClosedRunCount(periodId)).isZero();
		assertThat(snapshotCount(periodId)).isZero();
		assertThat(periodStatus(periodId)).isEqualTo("LOCKED");
	}

	@Test
	void 列表检查结果和阻断筛选必须基于最新检查失败关闭() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long readyPeriodId = createPeriod(admin, "030-FILTER-READY", LocalDate.of(2098, 1, 1),
				LocalDate.of(2098, 1, 31));
		JsonNode ready = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", readyPeriodId, "idempotencyKey", "030-filter-ready-" + readyPeriodId), admin));
		assertThat(ready.get("status").asText()).isEqualTo("READY");

		long blockedPeriodId = createPeriod(admin, "030-FILTER-BLOCK", LocalDate.of(2099, 1, 1),
				LocalDate.of(2099, 1, 31));
		insertOpenStocktake("030-FILTER-BLOCK-STK", LocalDate.of(2099, 1, 15));
		JsonNode blocked = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", blockedPeriodId, "idempotencyKey", "030-filter-block-" + blockedPeriodId),
				admin));
		assertThat(blocked.get("status").asText()).isEqualTo("BLOCKED");

		JsonNode blockingResultPage = data(get(
				"/api/admin/period-closes?periodCode=030-FILTER&page=1&pageSize=20&checkResult=BLOCKING",
				admin));
		assertThat(periodCodes(blockingResultPage)).containsExactly("030-FILTER-BLOCK");

		JsonNode hasBlockingPage = data(get(
				"/api/admin/period-closes?periodCode=030-FILTER&page=1&pageSize=20&hasBlocking=true",
				admin));
		assertThat(periodCodes(hasBlockingPage)).containsExactly("030-FILTER-BLOCK");

		JsonNode noBlockingPage = data(get(
				"/api/admin/period-closes?periodCode=030-FILTER&page=1&pageSize=20&hasBlocking=false",
				admin));
		assertThat(periodCodes(noBlockingPage)).containsExactly("030-FILTER-READY");
	}

	@Test
	void 同一幂等键并发检查必须返回同一运行且只保存一次检查结果() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2052-01", LocalDate.of(2052, 1, 1), LocalDate.of(2052, 1, 31));
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> first = executor.submit(() -> {
				await(start);
				return exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
						Map.of("periodId", periodId, "idempotencyKey", "030-same-check-" + periodId), admin);
			});
			Future<ResponseEntity<String>> second = executor.submit(() -> {
				await(start);
				return exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
						Map.of("periodId", periodId, "idempotencyKey", "030-same-check-" + periodId), admin);
			});
			start.countDown();
			List<ResponseEntity<String>> responses = List.of(first.get(), second.get());
			assertThat(responses).allSatisfy(this::assertOkUnchecked);
			JsonNode firstRun = data(responses.get(0));
			JsonNode secondRun = data(responses.get(1));
			assertThat(secondRun.get("id").longValue()).isEqualTo(firstRun.get("id").longValue());
			assertThat(checkRunCount(firstRun.get("id").longValue())).isOne();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 同一幂等键并发关闭必须全部重放为同一关闭结果() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2052-02", LocalDate.of(2052, 2, 1), LocalDate.of(2052, 2, 29));
		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-same-close-check-" + periodId), admin));
		Map<String, Object> payload = closePayload(check, "030-same-close-" + periodId, true, "同 key 并发关闭");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> first = executor.submit(() -> {
				await(start);
				return exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue()
						+ "/close", payload, admin);
			});
			Future<ResponseEntity<String>> second = executor.submit(() -> {
				await(start);
				return exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue()
						+ "/close", payload, admin);
			});
			start.countDown();
			List<ResponseEntity<String>> responses = List.of(first.get(), second.get());
			assertThat(responses).allSatisfy(this::assertOkUnchecked);
			JsonNode firstClose = data(responses.get(0));
			JsonNode secondClose = data(responses.get(1));
			assertThat(secondClose.get("id").longValue()).isEqualTo(firstClose.get("id").longValue());
			assertThat(currentClosedRunCount(periodId)).isOne();
			assertThat(snapshotCount(periodId)).isOne();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 同一幂等键并发重开必须全部重放为同一重开结果() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode closed = closeReadyPeriod(admin, "2052-03", LocalDate.of(2052, 3, 1),
				LocalDate.of(2052, 3, 31));
		long runId = closed.get("id").longValue();
		Map<String, Object> payload = Map.of("version", closed.get("version").longValue(), "idempotencyKey",
				"030-same-reopen-" + runId, "reason", "同 key 并发重开");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> first = executor.submit(() -> {
				await(start);
				return exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/reopen", payload, admin);
			});
			Future<ResponseEntity<String>> second = executor.submit(() -> {
				await(start);
				return exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/reopen", payload, admin);
			});
			start.countDown();
			List<ResponseEntity<String>> responses = List.of(first.get(), second.get());
			assertThat(responses).allSatisfy(this::assertOkUnchecked);
			JsonNode firstReopen = data(responses.get(0));
			JsonNode secondReopen = data(responses.get(1));
			assertThat(secondReopen.get("id").longValue()).isEqualTo(firstReopen.get("id").longValue());
			assertThat(firstReopen.get("status").asText()).isEqualTo("REOPENED");
			assertThat(currentClosedRunCount(firstReopen.get("periodId").longValue())).isZero();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 警告未确认不得关闭且成功关闭后重开保留旧快照再次关闭生成新版本() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2051-02", LocalDate.of(2051, 2, 1), LocalDate.of(2051, 2, 28));
		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-open-check-" + periodId), admin));
		assertThat(check.get("status").asText()).isIn("READY", "BLOCKED");
		assertThat(recursiveValues(check, "checkCode")).doesNotContain("PERIOD_NOT_OPEN");

		ResponseEntity<String> warningRejected = exchange(HttpMethod.POST,
				"/api/admin/period-closes/" + check.get("id").longValue() + "/close",
				closePayload(check, "030-warning-rejected-" + periodId, false, "未确认警告关闭"), admin);
		assertError(warningRejected, HttpStatus.CONFLICT, "PERIOD_CLOSE_WARNING_ACK_REQUIRED");
		assertThat(periodStatus(periodId)).isEqualTo("OPEN");
		assertThat(snapshotCount(periodId)).isZero();

		JsonNode closed = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue()
				+ "/close", closePayload(check, "030-close-ok-" + periodId, true, "确认警告并生成月结快照"), admin));
		assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
		long closedRunId = closed.get("id").longValue();
		assertThat(periodStatus(periodId)).isEqualTo("LOCKED");
		assertThat(currentClosedRunCount(periodId)).isOne();
		assertSnapshotComplete(closedRunId, periodId);
		String snapshotBeforeReopen = snapshotFingerprint(closedRunId);

		JsonNode reopened = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + closedRunId + "/reopen",
				Map.of("version", closed.get("version").longValue(), "idempotencyKey",
						"030-reopen-" + closedRunId, "reason", "030 验证重开保留历史快照"),
				admin));
		assertThat(reopened.get("status").asText()).isEqualTo("REOPENED");
		assertThat(periodStatus(periodId)).isEqualTo("OPEN");
		assertThat(currentClosedRunCount(periodId)).isZero();
		assertThat(snapshotFingerprint(closedRunId)).isEqualTo(snapshotBeforeReopen);

		JsonNode secondCheck = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-recheck-" + periodId), admin));
		JsonNode secondClosed = data(exchange(HttpMethod.POST,
				"/api/admin/period-closes/" + secondCheck.get("id").longValue() + "/close",
				closePayload(secondCheck, "030-close-second-" + periodId, true, "重开后再次月结生成新版本"), admin));
		assertThat(secondClosed.get("status").asText()).isEqualTo("CLOSED");
		assertThat(secondClosed.get("id").longValue()).isNotEqualTo(closedRunId);
		assertThat(currentClosedRunCount(periodId)).isOne();
		assertThat(maxRunVersion(periodId)).isGreaterThanOrEqualTo(2);
		assertThat(snapshotFingerprint(closedRunId)).isEqualTo(snapshotBeforeReopen);
	}

	@Test
	void 当前CLOSED期间必须拒绝016普通解锁只能通过030重开() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode closed = closeReadyPeriod(admin, "2052-04", LocalDate.of(2052, 4, 1),
				LocalDate.of(2052, 4, 30));
		long periodId = closed.get("periodId").longValue();

		ResponseEntity<String> unlock = exchange(HttpMethod.POST,
				"/api/admin/system/business-periods/" + periodId + "/unlock",
				Map.of("reason", "不能绕过 030 重开直接解锁"), admin);

		assertThat(unlock.getStatusCode()).as(unlock.getBody()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(code(unlock)).isIn("PERIOD_CLOSE_ACTION_NOT_ALLOWED", "BUSINESS_PERIOD_STATUS_INVALID");
		assertThat(periodStatus(periodId)).isEqualTo("LOCKED");
		assertThat(currentClosedRunCount(periodId)).isOne();
	}

	@Test
	void 关闭幂等重放版本冲突和来源变化必须稳定409且不重复当前关闭() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2051-03", LocalDate.of(2051, 3, 1), LocalDate.of(2051, 3, 31));
		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-idem-check-" + periodId), admin));
		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue() + "/close",
				closePayload(check, "030-stale-version-" + periodId, true, "关闭前旧版本校验",
						check.get("version").longValue() - 1, check.get("sourceFingerprint").asText()),
				admin), HttpStatus.CONFLICT, "PERIOD_CLOSE_VERSION_CONFLICT");
		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue() + "/close",
				closePayload(check, "030-source-change-" + periodId, true, "关闭前来源变化校验",
						check.get("version").longValue(), differentFingerprint(check.get("sourceFingerprint").asText())),
				admin), HttpStatus.CONFLICT, "PERIOD_CLOSE_SOURCE_CHANGED");
		Map<String, Object> closePayload = closePayload(check, "030-idem-close-" + periodId, true, "幂等关闭");
		JsonNode firstClose = data(exchange(HttpMethod.POST,
				"/api/admin/period-closes/" + check.get("id").longValue() + "/close", closePayload, admin));
		assertThat(firstClose.get("status").asText()).isEqualTo("CLOSED");

		JsonNode replay = data(exchange(HttpMethod.POST,
				"/api/admin/period-closes/" + check.get("id").longValue() + "/close", closePayload, admin));
		assertThat(replay.get("id").longValue()).isEqualTo(firstClose.get("id").longValue());
		assertThat(currentClosedRunCount(periodId)).isOne();
		assertThat(snapshotCount(periodId)).isOne();

		Map<String, Object> fingerprintConflict = Map.of("version", check.get("version").longValue(),
				"sourceFingerprint", differentFingerprint(check.get("sourceFingerprint").asText()),
				"warningAcknowledged", true, "reason", "相同幂等键不同来源", "idempotencyKey",
				"030-idem-close-" + periodId);
		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue() + "/close",
				fingerprintConflict, admin), HttpStatus.CONFLICT, "PERIOD_CLOSE_IDEMPOTENCY_CONFLICT");
		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue() + "/close",
				closePayload(check, "030-duplicate-close-" + periodId, true, "已关闭后重复关闭"), admin),
				HttpStatus.CONFLICT, "PERIOD_CLOSE_ACTION_NOT_ALLOWED");
		assertThat(currentClosedRunCount(periodId)).isOne();
		assertThat(snapshotCount(periodId)).isOne();
	}

	@Test
	void 失败和冲突路径必须写月结审计并保留错误码证据() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2052-05", LocalDate.of(2052, 5, 1), LocalDate.of(2052, 5, 31));
		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-audit-check-" + periodId), admin));
		long runId = check.get("id").longValue();

		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				closePayload(check, "030-audit-warning-" + periodId, false, "未确认警告"), admin),
				HttpStatus.CONFLICT, "PERIOD_CLOSE_WARNING_ACK_REQUIRED");
		assertThat(auditCount(runId, "CLOSE", "FAILURE", "CONFLICT")).isGreaterThanOrEqualTo(1);
		assertThat(auditErrorCodes(runId)).contains("PERIOD_CLOSE_WARNING_ACK_REQUIRED");

		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				closePayload(check, "030-audit-source-" + periodId, true, "来源变化",
						check.get("version").longValue(), differentFingerprint(check.get("sourceFingerprint").asText())),
				admin), HttpStatus.CONFLICT, "PERIOD_CLOSE_SOURCE_CHANGED");
		assertThat(auditErrorCodes(runId)).contains("PERIOD_CLOSE_SOURCE_CHANGED");
	}

	@Test
	void 检查项分页接口必须返回来源字段且按权限脱敏() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2052-06", LocalDate.of(2052, 6, 1), LocalDate.of(2052, 6, 30));
		insertOpenStocktake("030-CHECK-ITEM-SRC", LocalDate.of(2052, 6, 15));
		JsonNode blocked = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-check-items-" + periodId), admin));
		assertThat(blocked.get("status").asText()).isEqualTo("BLOCKED");
		long runId = blocked.get("id").longValue();
		long checkRunId = blocked.get("latestCheckRunId").longValue();

		ResponseEntity<String> itemsResponse = get("/api/admin/period-closes/" + runId + "/checks/" + checkRunId
				+ "/items?page=1&pageSize=10", admin);
		assertOk(itemsResponse);
		JsonNode items = data(itemsResponse);

		assertThat(items.get("pageSize").intValue()).isEqualTo(10);
		assertThat(items.get("items")).hasSizeGreaterThanOrEqualTo(1);
		JsonNode firstItem = items.get("items").get(0);
		assertThat(firstItem.get("checkCode").asText()).isEqualTo("INVENTORY_PROCESS_OPEN");
		assertThat(firstItem.get("objectType").asText()).isNotBlank();
		assertThat(firstItem.get("objectNo").asText()).isEqualTo("030-CHECK-ITEM-SRC");
		assertThat(firstItem.get("sourceRoute").toString()).contains("030-CHECK-ITEM-SRC");

		AuthenticatedSession snapshotOnly = createUserAndLogin("030-check-items-mask-", "030_CHECK_ITEMS_MASK_",
				List.of("system:business-period-close:view"));
		ResponseEntity<String> maskedResponse = get("/api/admin/period-closes/" + runId + "/checks/" + checkRunId
				+ "/items?page=1&pageSize=10", snapshotOnly);
		assertOk(maskedResponse);
		JsonNode maskedItems = data(maskedResponse);
		JsonNode maskedItem = maskedItems.get("items").get(0);
		assertThat(maskedItem.get("sourceVisible").booleanValue()).isFalse();
		assertThat(maskedItem.get("objectNo").isNull()).isTrue();
		assertThat(maskedItem.get("sourceRoute").isNull()).isTrue();
		assertThat(maskedItem.get("restrictedReason").asText()).contains("脱敏");
	}

	@Test
	void 运行详情必须返回历史版本和审计摘要() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode closed = closeReadyPeriod(admin, "2052-07", LocalDate.of(2052, 7, 1),
				LocalDate.of(2052, 7, 31));
		long runId = closed.get("id").longValue();

		JsonNode detail = data(get("/api/admin/period-closes/" + runId, admin));

		assertThat(detail.has("historyVersions")).isTrue();
		assertThat(detail.get("historyVersions").isArray()).isTrue();
		assertThat(detail.get("historyVersions").size()).isGreaterThanOrEqualTo(1);
		assertThat(recursiveValues(detail.get("historyVersions"), "status")).contains("CLOSED");
		assertThat(detail.has("auditSummary")).isTrue();
		assertThat(detail.get("auditSummary").isArray()).isTrue();
		assertThat(detail.get("auditSummary").size()).isGreaterThanOrEqualTo(1);
		assertThat(recursiveValues(detail.get("auditSummary"), "action")).contains("CLOSE");
	}

	@Test
	void 快照总览和报表快照必须返回冻结DTO字段() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		JsonNode closed = closeReadyPeriod(admin, "2052-08", LocalDate.of(2052, 8, 1),
				LocalDate.of(2052, 8, 31));
		long runId = closed.get("id").longValue();

		JsonNode snapshot = data(get("/api/admin/period-closes/" + runId + "/snapshot", admin));
		assertThat(snapshot.has("periodCode")).as(snapshot.toString()).isTrue();
		assertThat(snapshot.get("periodCode").asText()).isEqualTo("2052-08");
		assertThat(snapshot.has("startDate")).as(snapshot.toString()).isTrue();
		assertThat(snapshot.get("startDate").asText()).isEqualTo("2052-08-01");
		assertThat(snapshot.has("endDate")).as(snapshot.toString()).isTrue();
		assertThat(snapshot.get("endDate").asText()).isEqualTo("2052-08-31");
		assertThat(snapshot.has("sourceFingerprint")).as(snapshot.toString()).isTrue();
		assertThat(snapshot.get("sourceFingerprint").asText()).isEqualTo(closed.get("sourceFingerprint").asText());
		assertThat(snapshot.has("partitions")).as(snapshot.toString()).isTrue();
		assertThat(snapshot.get("partitions").isArray()).isTrue();
		assertThat(snapshot.get("partitions").size()).isGreaterThanOrEqualTo(4);
		assertThat(recursiveValues(snapshot.get("partitions"), "code")).contains("INVENTORY", "WIP",
				"PROJECT_COST", "REPORTS");
		assertThat(stringValues(snapshot.get("reportCodes")))
			.contains(STAGE_033_BUSINESS_SNAPSHOT_REPORT_CODES.toArray(String[]::new))
			.doesNotContain(STAGE_033_NON_SNAPSHOT_REPORT_CODES.toArray(String[]::new));

		JsonNode report = data(get("/api/admin/period-closes/" + runId + "/snapshot/reports/OVERVIEW", admin));
		assertThat(report.has("reportCode")).as(report.toString()).isTrue();
		assertThat(report.get("reportCode").asText()).isEqualTo("OVERVIEW");
		assertThat(report.has("reportName")).as(report.toString()).isTrue();
		assertThat(report.get("reportName").asText()).isEqualTo("经营概览");
		assertThat(report.has("sourceFingerprint")).as(report.toString()).isTrue();
		assertThat(report.get("sourceFingerprint").asText()).isNotBlank();
		assertThat(report.has("generatedAt")).as(report.toString()).isTrue();
		assertThat(report.get("generatedAt").asText()).isNotBlank();
		assertThat(report.has("result")).as(report.toString()).isTrue();
		assertThat(report.get("result").isObject()).isTrue();
		assertThat(report.has("resultJson")).isFalse();

		JsonNode projectProfit = data(get("/api/admin/period-closes/" + runId
				+ "/snapshot/reports/PROJECT_PROFIT", admin));
		assertThat(projectProfit.get("reportCode").asText()).isEqualTo("PROJECT_PROFIT");
		assertThat(projectProfit.get("reportName").asText()).isEqualTo("项目利润分析");
		assertThat(projectProfit.get("result").isObject()).isTrue();
		assertThat(projectProfit.has("resultJson")).isFalse();
	}

	@Test
	void 三十三业务快照必须权限无关完整冻结且不受一百条截断() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate startDate = LocalDate.of(2052, 9, 1);
		LocalDate endDate = LocalDate.of(2052, 9, 30);
		long periodId = createPeriod(admin, "2052-09", startDate, endDate);
		insertProjectProfitSnapshotRows("2052-09", endDate, 105);
		AuthenticatedSession closerWithoutAmounts = createUserAndLogin("030-033-snapshot-closer-",
				"030_033_SNAPSHOT_CLOSER_", List.of("system:business-period-close:view",
						"system:business-period-close:check", "system:business-period-close:close",
						"system:business-period-close:snapshot-view"));

		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-033-full-check-" + periodId),
				closerWithoutAmounts));
		JsonNode closed = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue()
				+ "/close", closePayload(check, "030-033-full-close-" + periodId, true, "033 完整业务快照"),
				closerWithoutAmounts));

		JsonNode projectProfit = reportSnapshotPayload(closed.get("snapshotId").longValue(), "PROJECT_PROFIT");
		assertThat(projectProfit.get("summary").get("analysisMode").asText()).isEqualTo("BUSINESS_SNAPSHOT");
		assertThat(projectProfit.get("summary").get("shipmentRevenue").asText()).isEqualTo("1050.00");
		assertThat(projectProfit.get("summary").get("projectCostTotal").asText()).isEqualTo("105.00");
		assertThat(projectProfit.get("summary").get("amountVisible").booleanValue()).isTrue();
		assertThat(projectProfit.get("items")).hasSize(105);
		assertThat(projectProfit.get("items").get(0).get("shipmentRevenue").isNull()).isFalse();
	}

	@Test
	void 并发关闭同一期间必须只有一个成功且失败请求不产生半写() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2051-05", LocalDate.of(2051, 5, 1), LocalDate.of(2051, 5, 31));
		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-race-check-" + periodId), admin));
		long runId = check.get("id").longValue();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> first = executor.submit(() -> exchange(HttpMethod.POST,
					"/api/admin/period-closes/" + runId + "/close",
					closePayload(check, "030-race-close-a-" + periodId, true, "并发关闭 A"), admin));
			Future<ResponseEntity<String>> second = executor.submit(() -> exchange(HttpMethod.POST,
					"/api/admin/period-closes/" + runId + "/close",
					closePayload(check, "030-race-close-b-" + periodId, true, "并发关闭 B"), admin));
			List<ResponseEntity<String>> responses = List.of(first.get(), second.get());
			assertThat(responses.stream().filter((response) -> response.getStatusCode() == HttpStatus.OK).count())
				.isOne();
			assertThat(responses.stream().filter((response) -> response.getStatusCode() == HttpStatus.CONFLICT).count())
				.isOne();
			for (ResponseEntity<String> response : responses) {
				if (response.getStatusCode() == HttpStatus.CONFLICT) {
					assertThat(code(response)).isIn("PERIOD_CLOSE_VERSION_CONFLICT",
							"PERIOD_CLOSE_ALREADY_CLOSED", "PERIOD_CLOSE_SOURCE_CHANGED",
							"PERIOD_CLOSE_ACTION_NOT_ALLOWED");
				}
			}
			assertThat(periodStatus(periodId)).isEqualTo("LOCKED");
			assertThat(currentClosedRunCount(periodId)).isOne();
			assertThat(snapshotCount(periodId)).isOne();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 月结权限与金额来源权限必须后端失败关闭并脱敏() throws Exception {
		requireV32Schema();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = createPeriod(admin, "2051-04", LocalDate.of(2051, 4, 1), LocalDate.of(2051, 4, 30));
		AuthenticatedSession noPeriodClosePermission = createUserAndLogin("030-no-close-", "030_NO_CLOSE_",
				List.of());
		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-no-permission-check-" + periodId),
				noPeriodClosePermission), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-mask-check-" + periodId), admin));
		JsonNode closed = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue()
				+ "/close", closePayload(check, "030-mask-close-" + periodId, true, "生成权限脱敏快照"), admin));
		insertSensitiveSnapshotRows(closed.get("snapshotId").longValue(), periodId);

		AuthenticatedSession snapshotOnly = createUserAndLogin("030-snapshot-only-", "030_SNAPSHOT_ONLY_",
				List.of("system:business-period-close:view", "system:business-period-close:snapshot-view"));
		JsonNode inventory = data(get("/api/admin/period-closes/" + closed.get("id").longValue()
				+ "/snapshot/inventory", snapshotOnly)).get("items").get(0);
		assertThat(inventory.get("amountVisible").booleanValue()).isFalse();
		assertThat(inventory.get("endingAmount").isNull()).isTrue();
		assertThat(inventory.get("unitCost").isNull()).isTrue();
		JsonNode projectCost = data(get("/api/admin/period-closes/" + closed.get("id").longValue()
				+ "/snapshot/project-costs", snapshotOnly)).get("items").get(0);
		assertThat(projectCost.get("amountVisible").booleanValue()).isFalse();
		assertThat(projectCost.get("projectCostTotal").isNull()).isTrue();
		assertThat(projectCost.get("shipmentGrossMargin").isNull()).isTrue();
	}

	private void requireV32Schema() {
		for (String table : PERIOD_CLOSE_TABLES) {
			assertThat(tableExists(table)).as("V32 必须创建 " + table).isTrue();
		}
	}

	private long createPeriod(AuthenticatedSession session, String code, LocalDate startDate, LocalDate endDate)
			throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/system/business-periods",
				Map.of("periodCode", code, "periodName", code + "业务月结测试期间", "startDate", startDate.toString(),
						"endDate", endDate.toString()),
				session);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private JsonNode closeReadyPeriod(AuthenticatedSession admin, String code, LocalDate startDate, LocalDate endDate)
			throws Exception {
		long periodId = createPeriod(admin, code, startDate, endDate);
		JsonNode check = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "030-close-ready-check-" + periodId), admin));
		assertThat(check.get("status").asText()).isEqualTo("READY");
		return data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + check.get("id").longValue() + "/close",
				closePayload(check, "030-close-ready-close-" + periodId, true, "生成月结快照"), admin));
	}

	private Map<String, Object> closePayload(JsonNode check, String idempotencyKey, boolean warningAcknowledged,
			String reason) {
		return closePayload(check, idempotencyKey, warningAcknowledged, reason, check.get("version").longValue(),
				check.get("sourceFingerprint").asText());
	}

	private Map<String, Object> closePayload(JsonNode check, String idempotencyKey, boolean warningAcknowledged,
			String reason, long version, String sourceFingerprint) {
		return Map.of("version", version, "sourceFingerprint", sourceFingerprint, "warningAcknowledged",
				warningAcknowledged, "reason", reason, "idempotencyKey", idempotencyKey);
	}

	private String differentFingerprint(String fingerprint) {
		assertThat(fingerprint).hasSize(64);
		char replacement = fingerprint.charAt(0) == '0' ? '1' : '0';
		return replacement + fingerprint.substring(1);
	}

	private void assertSnapshotComplete(long runId, long periodId) {
		Long snapshotId = this.jdbcTemplate.queryForObject("""
				select id
				from biz_period_snapshot
				where run_id = ?
				and period_id = ?
				""", Long.class, runId, periodId);
		assertThat(snapshotId).isNotNull();
		for (String table : List.of("biz_period_inventory_snapshot", "biz_period_inventory_summary",
				"biz_period_wip_snapshot", "biz_period_project_cost_snapshot")) {
			assertThat(this.jdbcTemplate.queryForObject("select count(*) from " + table + " where snapshot_id = ?",
					Long.class, snapshotId)).as(table).isNotNull();
		}
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(distinct report_code)
				from biz_period_report_snapshot
				where snapshot_id = ?
				""", Long.class, snapshotId)).isEqualTo(13L);
		assertThat(snapshotReportCodes(snapshotId))
			.contains(STAGE_033_BUSINESS_SNAPSHOT_REPORT_CODES.toArray(String[]::new))
			.doesNotContain(STAGE_033_NON_SNAPSHOT_REPORT_CODES.toArray(String[]::new));
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_audit
				where run_id = ?
				and action = 'CLOSE'
				and result = 'SUCCESS'
				""", Long.class, runId)).isOne();
	}

	private String snapshotFingerprint(long runId) {
		return this.jdbcTemplate.queryForObject("""
				select concat(s.id, ':', s.revision_no, ':', s.source_fingerprint, ':',
					coalesce(string_agg(r.report_code || '=' || r.fingerprint, ',' order by r.report_code), ''))
				from biz_period_snapshot s
				left join biz_period_report_snapshot r on r.snapshot_id = s.id
				where s.run_id = ?
				group by s.id, s.revision_no, s.source_fingerprint
				""", String.class, runId);
	}

	private List<String> snapshotReportCodes(long snapshotId) {
		return this.jdbcTemplate.queryForList("""
				select report_code
				from biz_period_report_snapshot
				where snapshot_id = ?
				order by report_code
				""", String.class, snapshotId);
	}

	private JsonNode reportSnapshotPayload(long snapshotId, String reportCode) throws Exception {
		String json = this.jdbcTemplate.queryForObject("""
				select result_json::text
				from biz_period_report_snapshot
				where snapshot_id = ?
				and report_code = ?
				""", String.class, snapshotId, reportCode);
		return this.objectMapper.readTree(json);
	}

	private void insertProjectProfitSnapshotRows(String periodCode, LocalDate cutoffDate, int count) {
		int suffix = SEQUENCE.incrementAndGet();
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "030033C" + suffix, "030-033快照客户" + suffix);
		for (int index = 1; index <= count; index++) {
			long projectId = this.jdbcTemplate.queryForObject("""
					insert into sal_project (
						project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
						status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, date '2052-01-01', date '2052-12-31', 'ACTIVE', 10.00, 1.00,
						'test', now(), 'test', now())
					returning id
					""", Long.class, "030-033-SNAP-" + suffix + "-" + index, "030-033快照项目" + index,
					customerId, adminUserId());
			this.jdbcTemplate.update("""
					insert into prj_cost_calculation (
						project_id, calculation_no, cutoff_date, status, is_current, source_fingerprint,
						project_cost_total, wip_cost, finished_cost, delivered_cost, direct_project_cost,
						shipment_revenue, invoice_revenue, target_revenue, shipment_gross_margin,
						invoice_gross_margin, target_gross_margin, shipment_gross_margin_rate,
						invoice_gross_margin_rate, target_gross_margin_rate, margin_completeness,
						created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
					)
					values (?, ?, ?, 'CONFIRMED', true, ?, 1.00, 0.00, 0.00, 1.00, 0.00,
						10.00, 10.00, 10.00, 9.00, 9.00, 9.00, 0.900000, 0.900000, 0.900000,
						'COMPLETE', 'test', now(), 'test', now(), 'test', now())
					""", projectId, "030-033-PCC-" + suffix + "-" + index, cutoffDate,
					"030-033-fp-" + periodCode + "-" + index);
			this.jdbcTemplate.update("""
					insert into prj_cost_source_line (
						calculation_id, project_id, cost_category, cost_stage, entry_type, source_type, source_id,
						source_no, source_status, business_date, quantity, unit_cost, source_amount,
						calculated_amount, source_fingerprint
					)
					select id, project_id, 'MATERIAL', 'DELIVERED', 'SOURCE_TO_WIP', 'SALES_SHIPMENT', ?,
						?, 'ACTUAL', ?, 1.000000, 1.000000, 1.00, 1.00, source_fingerprint
					from prj_cost_calculation
					where calculation_no = ?
					""", projectId, "030-033-SRC-" + suffix + "-" + index, cutoffDate,
					"030-033-PCC-" + suffix + "-" + index);
		}
	}

	private void insertOpenStocktake(String stocktakeNo, LocalDate businessDate) {
		this.jdbcTemplate.update("""
				insert into inv_stocktake (
					stocktake_no, business_date, scope_type, reason, status, idempotency_key,
					created_by_user_id, created_by_username, updated_by_username
				)
				values (?, ?, 'WAREHOUSE', '030 阻断检查来源夹具', 'COUNTING', ?, ?, 'admin', 'admin')
				""", stocktakeNo, businessDate, stocktakeNo + "-idem", adminUserId());
	}

	private long adminUserId() {
		return this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'", Long.class);
	}

	private long checkRunCount(long runId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_check_run
				where run_id = ?
				""", Long.class, runId);
	}

	private long auditCount(long runId, String action, String... results) {
		String placeholders = String.join(",", List.of(results).stream().map((ignored) -> "?").toList());
		List<Object> args = new ArrayList<>();
		args.add(runId);
		args.add(action);
		args.addAll(List.of(results));
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_audit
				where run_id = ?
				and action = ?
				and result in (%s)
				""".formatted(placeholders), Long.class, args.toArray());
	}

	private List<String> auditErrorCodes(long runId) {
		return this.jdbcTemplate.queryForList("""
				select error_code
				from biz_period_close_audit
				where run_id = ?
				and error_code is not null
				order by id
				""", String.class, runId);
	}

	private void insertSensitiveSnapshotRows(long snapshotId, long periodId) {
		this.jdbcTemplate.update("""
				insert into biz_period_inventory_snapshot (
					snapshot_id, warehouse_id, warehouse_name, material_id, material_code, material_name,
					quality_status, ownership_type, ending_quantity, locked_quantity, available_quantity,
					valuation_state, unit_cost, ending_amount, in_quantity, out_quantity, adjustment_quantity,
					fingerprint
				)
				values (?, 1, '030 脱敏仓库', 1, 'MAT-030-SNAP', '030 脱敏物料', 'QUALIFIED', 'PUBLIC',
					1.000000, 0, 1.000000, 'VALUED', 10.000000, 10.00, 1.000000, 0, 0, '030-inv-mask')
				""", snapshotId);
		this.jdbcTemplate.update("""
				insert into biz_period_project_cost_snapshot (
					snapshot_id, project_id, project_no, project_name, calculation_id, calculation_no,
					source_fingerprint, freshness_status, completeness_status, project_cost_total,
					wip_cost, finished_cost, delivered_cost, direct_project_cost, shipment_revenue,
					shipment_gross_margin, blocking_variance_count, warning_variance_count, fingerprint
				)
				values (?, ?, 'PRJ-030-SNAP', '030 脱敏项目', 1, 'PCC-030-SNAP', '030-pc-mask', 'CURRENT',
					'COMPLETE', 88.00, 0, 0, 88.00, 0, 100.00, 12.00, 0, 0, '030-pc-mask')
				""", snapshotId, periodId);
	}

	private long currentClosedRunCount(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_run
				where period_id = ?
				and status = 'CLOSED'
				""", Long.class, periodId);
	}

	private List<String> actionCodes(JsonNode actions) {
		List<String> codes = new ArrayList<>();
		for (JsonNode action : actions) {
			codes.add(action.asText());
		}
		return codes;
	}

	private List<String> periodCodes(JsonNode page) {
		List<String> codes = new ArrayList<>();
		page.get("items").forEach((item) -> codes.add(item.get("periodCode").asText()));
		return codes;
	}

	private List<String> stringValues(JsonNode valuesNode) {
		List<String> values = new ArrayList<>();
		valuesNode.forEach((value) -> values.add(value.asText()));
		return values;
	}

	private long snapshotCount(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_snapshot
				where period_id = ?
				""", Long.class, periodId);
	}

	private int maxRunVersion(long periodId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(max(revision_no), 0)
				from biz_period_close_run
				where period_id = ?
				""", Integer.class, periodId);
	}

	private String periodStatus(long periodId) {
		return this.jdbcTemplate.queryForObject("select status from biz_business_period where id = ?", String.class,
				periodId);
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

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "030 月结测试角色" + suffix, "030 月结测试角色");
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

	private void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
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
