package com.qherp.api.system.periodclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodLockService;
import com.qherp.api.system.period.BusinessPeriodOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=period-close-admin-controller")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PeriodCloseAdminControllerTests extends PostgresIntegrationTest {

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

	@Autowired
	private BusinessPeriodGuard businessPeriodGuard;

	@Autowired
	private BusinessPeriodLockService businessPeriodLockService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void 月结检查关闭重开必须原子锁期幂等并保留历史快照() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = insertPeriod("2050-01", "OPEN");

		JsonNode checked = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "pc-check-" + periodId), admin));
		assertThat(checked.get("periodId").longValue()).isEqualTo(periodId);
		assertThat(checked.get("status").asText()).isEqualTo("READY");
		assertThat(checked.get("revisionNo").intValue()).isOne();
		assertThat(checked.get("blockingCount").intValue()).isZero();
		assertThat(checked.get("warningCount").intValue()).isGreaterThanOrEqualTo(1);
		assertThat(actionCodes(checked.get("allowedActions"))).contains("CLOSE");
		String readyFingerprint = checked.get("sourceFingerprint").asText();
		long runId = checked.get("id").longValue();

		assertError(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				Map.of("version", checked.get("version").longValue(), "sourceFingerprint", readyFingerprint,
						"warningAcknowledged", false, "idempotencyKey", "pc-close-no-ack-" + runId),
				admin), HttpStatus.CONFLICT, "PERIOD_CLOSE_WARNING_ACK_REQUIRED");
		assertThat(auditCount(runId, "CLOSE", "FAILURE", "PERIOD_CLOSE_WARNING_ACK_REQUIRED")).isOne();

		Map<String, Object> closePayload = Map.of("version", checked.get("version").longValue(),
				"sourceFingerprint", readyFingerprint, "warningAcknowledged", true, "reason",
				"确认无活动首期业务月结", "idempotencyKey", "pc-close-" + runId);
		JsonNode closed = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				closePayload, admin));
		assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
		assertThat(closed.get("snapshotId").isNull()).isFalse();
		assertThat(this.jdbcTemplate.queryForObject("select status from biz_business_period where id = ?",
				String.class, periodId)).isEqualTo("LOCKED");
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_period_report_snapshot where snapshot_id = ?",
				Long.class, closed.get("snapshotId").longValue())).isEqualTo(8L);
		assertThatThrownBy(() -> this.businessPeriodGuard.assertWritable(LocalDate.of(2050, 1, 15),
				BusinessPeriodOperation.POST, "PERIOD_CLOSE_TEST", runId))
			.isInstanceOfSatisfying(BusinessException.class,
					exception -> assertThat(exception.errorCode()).isEqualTo(ApiErrorCode.BUSINESS_PERIOD_LOCKED));

		JsonNode repeatedClose = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				closePayload, admin));
		assertThat(repeatedClose.get("id").longValue()).isEqualTo(runId);
		assertThat(repeatedClose.get("status").asText()).isEqualTo("CLOSED");

		JsonNode snapshot = data(get("/api/admin/period-closes/" + runId + "/snapshot", admin));
		assertThat(snapshot.get("runId").longValue()).isEqualTo(runId);
		assertThat(snapshot.get("status").asText()).isEqualTo("CLOSED");
		assertThat(snapshot.get("reportCodes")).hasSize(8);

		JsonNode reopened = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/reopen",
				Map.of("version", closed.get("version").longValue(), "reason", "补录开放期业务后重新月结",
						"idempotencyKey", "pc-reopen-" + runId),
				admin));
		assertThat(reopened.get("status").asText()).isEqualTo("REOPENED");
		assertThat(this.jdbcTemplate.queryForObject("select status from biz_business_period where id = ?",
				String.class, periodId)).isEqualTo("OPEN");
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_period_snapshot where run_id = ?",
				Long.class, runId)).isOne();

		JsonNode secondRun = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "pc-check-second-" + periodId), admin));
		assertThat(secondRun.get("id").longValue()).isNotEqualTo(runId);
		assertThat(secondRun.get("revisionNo").intValue()).isEqualTo(2);
	}

	@Test
	void 手工锁定期间不得自动补认为030月结快照() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = insertPeriod("2050-02", "LOCKED");

		JsonNode summary = data(get("/api/admin/period-closes/periods/" + periodId, admin));

		assertThat(summary.get("periodStatus").asText()).isEqualTo("LOCKED");
		assertThat(summary.get("status").asText()).isEqualTo("MANUAL_LOCKED_WITHOUT_SNAPSHOT");
		assertThat(summary.get("currentRunId").isNull()).isTrue();
		assertThat(summary.get("history")).hasSize(0);
		assertThat(actionCodes(summary.get("allowedActions"))).doesNotContain("CHECK");
		assertThat(summary.get("actionDisabledReasons").get("CHECK").asText()).contains("手工锁定");
	}

	@Test
	void 列表必须应用检查结果和阻断筛选并强制分页档位() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long warningPeriodId = insertPeriod("2050-05", "OPEN");
		long blockedPeriodId = insertPeriod("2050-06", "OPEN");
		insertOpenStocktake(blockedPeriodId);

		data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", warningPeriodId, "idempotencyKey", "pc-list-warning-" + warningPeriodId), admin));
		data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", blockedPeriodId, "idempotencyKey", "pc-list-blocked-" + blockedPeriodId), admin));
		cancelPeriodCloseStocktakes();

		JsonNode blockingPage = data(get("/api/admin/period-closes?checkResult=BLOCKING&hasBlocking=true"
				+ "&startDate=2050-06-01&endDate=2050-06-30&page=1&pageSize=7", admin));
		assertThat(blockingPage.get("pageSize").intValue()).isEqualTo(10);
		assertThat(blockingPage.get("items")).hasSize(1);
		assertThat(blockingPage.get("items").get(0).get("periodId").longValue()).isEqualTo(blockedPeriodId);

		JsonNode noBlockingPage = data(get("/api/admin/period-closes?hasBlocking=false"
				+ "&startDate=2050-05-01&endDate=2050-06-30&page=1&pageSize=20", admin));
		assertThat(periodIds(noBlockingPage.get("items"))).contains(warningPeriodId).doesNotContain(blockedPeriodId);
	}

	@Test
	void 检查运行历史和检查项必须使用独立接口并返回可追溯来源() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = insertPeriod("2050-07", "OPEN");
		insertOpenStocktake(periodId);
		insertProjectCostActivity(periodId);

		JsonNode checked = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "pc-items-" + periodId), admin));
		cancelPeriodCloseStocktakes();
		long runId = checked.get("id").longValue();
		long checkRunId = checked.get("latestCheckRunId").longValue();

		JsonNode history = data(get("/api/admin/period-closes/" + runId + "/checks", admin));
		assertThat(history.get("items")).hasSize(1);
		assertThat(history.get("items").get(0).has("items")).isFalse();

		JsonNode itemsPage = data(get("/api/admin/period-closes/" + runId + "/checks/" + checkRunId
				+ "/items?page=1&pageSize=10", admin));
		assertThat(itemsPage.get("items").size()).isGreaterThanOrEqualTo(2);
		JsonNode inventoryItem = firstItemByCode(itemsPage.get("items"), "INVENTORY_PROCESS_OPEN");
		assertThat(inventoryItem.get("objectType").asText()).isEqualTo("INVENTORY_STOCKTAKE");
		assertThat(inventoryItem.get("objectId").isNull()).isFalse();
		assertThat(inventoryItem.get("objectNo").asText()).startsWith("PC-STK-");
		assertThat(inventoryItem.get("sourceRoute").isObject()).isTrue();
		JsonNode projectItem = firstItemByCode(itemsPage.get("items"), "PROJECT_COST_MISSING_AT_CUTOFF");
		assertThat(projectItem.get("objectType").asText()).isEqualTo("PROJECT");
		assertThat(projectItem.get("objectId").isNull()).isFalse();
		assertThat(projectItem.get("objectNo").asText()).startsWith("PC-PRJ-");
	}

	@Test
	void 运行详情快照总览和报表快照必须返回冻结契约字段() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = insertPeriod("2050-08", "OPEN");
		JsonNode checked = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "pc-dto-check-" + periodId), admin));
		long runId = checked.get("id").longValue();
		JsonNode closed = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				Map.of("version", checked.get("version").longValue(), "sourceFingerprint",
						checked.get("sourceFingerprint").asText(), "warningAcknowledged", true, "reason",
						"冻结 DTO 契约", "idempotencyKey", "pc-dto-close-" + runId),
				admin));

		JsonNode detail = data(get("/api/admin/period-closes/" + runId, admin));
		assertThat(detail.get("historyVersions")).hasSize(1);
		assertThat(detail.get("auditSummary").size()).isGreaterThanOrEqualTo(2);

		JsonNode snapshot = data(get("/api/admin/period-closes/" + runId + "/snapshot", admin));
		assertThat(snapshot.get("periodCode").asText()).isEqualTo("2050-08");
		assertThat(snapshot.get("startDate").asText()).isEqualTo("2050-08-01");
		assertThat(snapshot.get("endDate").asText()).isEqualTo("2050-08-31");
		assertThat(snapshot.get("sourceFingerprint").asText()).isEqualTo(closed.get("sourceFingerprint").asText());
		assertThat(snapshot.get("partitions")).hasSize(4);

		JsonNode report = data(get("/api/admin/period-closes/" + runId + "/snapshot/reports/OVERVIEW", admin));
		assertThat(report.get("reportName").asText()).isEqualTo("经营概览");
		assertThat(report.get("result").isObject()).isTrue();
		assertThat(report.has("resultJson")).isFalse();
		assertThat(report.get("sourceFingerprint").asText()).isNotBlank();
		assertThat(report.get("generatedAt").asText()).isNotBlank();
	}

	@Test
	void 普通016解锁当前已关闭月结期间必须失败并提示走030重开() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = insertPeriod("2050-09", "OPEN");
		JsonNode checked = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "pc-unlock-check-" + periodId), admin));
		long runId = checked.get("id").longValue();
		data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				Map.of("version", checked.get("version").longValue(), "sourceFingerprint",
						checked.get("sourceFingerprint").asText(), "warningAcknowledged", true, "reason",
						"关闭后禁止普通解锁", "idempotencyKey", "pc-unlock-close-" + runId),
				admin));

		assertError(exchange(HttpMethod.POST, "/api/admin/system/business-periods/" + periodId + "/unlock",
				Map.of("reason", "试图绕过 030 重开"), admin), HttpStatus.CONFLICT,
				"PERIOD_CLOSE_ACTION_NOT_ALLOWED");
		assertThat(this.jdbcTemplate.queryForObject("select status from biz_business_period where id = ?",
				String.class, periodId)).isEqualTo("LOCKED");
	}

	@Test
	void 同一幂等键并发检查关闭和重开必须返回原结果且不产生重复业务写入() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = insertPeriod("2050-10", "OPEN");

		List<JsonNode> concurrentChecks = concurrentRequests(() -> exchange(HttpMethod.POST,
				"/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "pc-concurrent-check-" + periodId), admin));
		assertThat(concurrentChecks).hasSize(2);
		long runId = concurrentChecks.get(0).get("id").longValue();
		assertThat(concurrentChecks.get(1).get("id").longValue()).isEqualTo(runId);
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_period_close_check_run where run_id = ?",
				Long.class, runId)).isOne();

		JsonNode checked = data(get("/api/admin/period-closes/" + runId, admin));
		Map<String, Object> closePayload = Map.of("version", checked.get("version").longValue(),
				"sourceFingerprint", checked.get("sourceFingerprint").asText(), "warningAcknowledged", true,
				"reason", "并发关闭同键重放", "idempotencyKey", "pc-concurrent-close-" + runId);
		List<JsonNode> concurrentCloses = concurrentRequests(() -> exchange(HttpMethod.POST,
				"/api/admin/period-closes/" + runId + "/close", closePayload, admin));
		assertThat(concurrentCloses).hasSize(2);
		assertThat(concurrentCloses).allSatisfy((node) -> assertThat(node.get("status").asText()).isEqualTo("CLOSED"));
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from biz_period_snapshot where run_id = ?",
				Long.class, runId)).isOne();

		JsonNode closed = data(get("/api/admin/period-closes/" + runId, admin));
		Map<String, Object> reopenPayload = Map.of("version", closed.get("version").longValue(), "reason",
				"并发重开同键重放", "idempotencyKey", "pc-concurrent-reopen-" + runId);
		List<JsonNode> concurrentReopens = concurrentRequests(() -> exchange(HttpMethod.POST,
				"/api/admin/period-closes/" + runId + "/reopen", reopenPayload, admin));
		assertThat(concurrentReopens).hasSize(2);
		assertThat(concurrentReopens).allSatisfy((node) -> assertThat(node.get("status").asText()).isEqualTo("REOPENED"));
		assertThat(auditCount(runId, "CHECK", "REPLAY", null)).isGreaterThanOrEqualTo(1);
		assertThat(auditCount(runId, "CLOSE", "REPLAY", null)).isGreaterThanOrEqualTo(1);
		assertThat(auditCount(runId, "REOPEN", "REPLAY", null)).isGreaterThanOrEqualTo(1);
	}

	@Test
	void 快照查询必须叠加库存和项目成本金额权限并且无月结权限失败关闭() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long periodId = insertPeriod("2050-03", "OPEN");
		JsonNode checked = data(exchange(HttpMethod.POST, "/api/admin/period-closes/checks",
				Map.of("periodId", periodId, "idempotencyKey", "pc-mask-check-" + periodId), admin));
		long runId = checked.get("id").longValue();
		JsonNode closed = data(exchange(HttpMethod.POST, "/api/admin/period-closes/" + runId + "/close",
				Map.of("version", checked.get("version").longValue(), "sourceFingerprint",
						checked.get("sourceFingerprint").asText(), "warningAcknowledged", true, "reason",
						"生成脱敏快照", "idempotencyKey", "pc-mask-close-" + runId),
				admin));
		insertSensitiveSnapshotRows(closed.get("snapshotId").longValue(), periodId);

		AuthenticatedSession snapshotOnly = createUserAndLogin("pc-snapshot-only-", "PC_SNAPSHOT_ONLY_",
				List.of("system:business-period-close:view", "system:business-period-close:snapshot-view"));
		JsonNode inventory = data(get("/api/admin/period-closes/" + runId + "/snapshot/inventory", snapshotOnly))
			.get("items")
			.get(0);
		assertThat(inventory.get("amountVisible").booleanValue()).isFalse();
		assertThat(inventory.get("endingAmount").isNull()).isTrue();
		assertThat(inventory.get("unitCost").isNull()).isTrue();
		JsonNode projectCost = data(get("/api/admin/period-closes/" + runId + "/snapshot/project-costs",
				snapshotOnly)).get("items").get(0);
		assertThat(projectCost.get("amountVisible").booleanValue()).isFalse();
		assertThat(projectCost.get("projectCostTotal").isNull()).isTrue();
		assertThat(projectCost.get("shipmentGrossMargin").isNull()).isTrue();

		AuthenticatedSession noPeriodClose = createUserAndLogin("pc-no-period-close-", "PC_NO_PERIOD_CLOSE_",
				List.of());
		assertError(get("/api/admin/period-closes", noPeriodClose), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	void 期间写入守卫必须与月结关闭共用事务级锁并在提交后看到锁期() throws Exception {
		long periodId = insertPeriod("2050-04", "OPEN");
		LocalDate businessDate = this.jdbcTemplate.queryForObject("""
				select start_date + 14
				from biz_business_period
				where id = ?
				""", LocalDate.class, periodId);
		TransactionTemplate transactionTemplate = new TransactionTemplate(this.transactionManager);
		CountDownLatch lockHeld = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Void> closeLikeTransaction = executor.submit(() -> {
				transactionTemplate.executeWithoutResult((status) -> {
					this.businessPeriodLockService.acquireForPeriodId(periodId);
					lockHeld.countDown();
					sleepBriefly();
					this.jdbcTemplate.update("""
							update biz_business_period
							set status = 'LOCKED', locked_by = 'test', locked_at = now(),
							    lock_reason = '并发月结锁期', updated_at = now()
							where id = ?
							""", periodId);
				});
				return null;
			});
			assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Throwable> guardedWrite = executor.submit(() -> {
				try {
					this.businessPeriodGuard.assertWritable(businessDate, BusinessPeriodOperation.POST,
							"PERIOD_CLOSE_RACE_TEST", periodId);
					return null;
				}
				catch (Throwable throwable) {
					return throwable;
				}
			});
			closeLikeTransaction.get(5, TimeUnit.SECONDS);
			Throwable throwable = guardedWrite.get(5, TimeUnit.SECONDS);
			assertThat(throwable).isInstanceOfSatisfying(BusinessException.class,
					exception -> assertThat(exception.errorCode()).isEqualTo(ApiErrorCode.BUSINESS_PERIOD_LOCKED));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private long insertPeriod(String periodCode, String status) {
		LocalDate start = java.time.YearMonth.parse(periodCode).atDay(1);
		LocalDate end = start.plusMonths(1).minusDays(1);
		return this.jdbcTemplate.queryForObject("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, locked_by, locked_at,
					lock_reason, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, case when ? = 'LOCKED' then 'test' else null end,
					case when ? = 'LOCKED' then now() else null end,
					case when ? = 'LOCKED' then '016 手工锁定' else null end, ?, ?)
				returning id
				""", Long.class, periodCode, periodCode + "期间", start, end, status, status, status, status,
				OffsetDateTime.now(), OffsetDateTime.now());
	}

	private void insertSensitiveSnapshotRows(long snapshotId, long periodId) {
		this.jdbcTemplate.update("""
				insert into biz_period_inventory_snapshot (
					snapshot_id, warehouse_id, warehouse_name, material_id, material_code, material_name,
					quality_status, ownership_type, ending_quantity, locked_quantity, available_quantity,
					valuation_state, unit_cost, ending_amount, in_quantity, out_quantity, adjustment_quantity,
					fingerprint
				)
				values (?, 1, '快照仓库', 1, 'MAT-SNAP', '快照物料', 'QUALIFIED', 'PUBLIC',
					1.000000, 0, 1.000000, 'VALUED', 10.000000, 10.00, 1.000000, 0, 0, 'inv-mask')
				""", snapshotId);
		this.jdbcTemplate.update("""
				insert into biz_period_project_cost_snapshot (
					snapshot_id, project_id, project_no, project_name, calculation_id, calculation_no,
					source_fingerprint, freshness_status, completeness_status, project_cost_total,
					wip_cost, finished_cost, delivered_cost, direct_project_cost, shipment_revenue,
					shipment_gross_margin, blocking_variance_count, warning_variance_count, fingerprint
				)
				values (?, ?, 'PRJ-SNAP', '快照项目', 1, 'PCC-SNAP', 'pc-mask', 'CURRENT', 'COMPLETE',
					88.00, 0, 0, 88.00, 0, 100.00, 12.00, 0, 0, 'pc-mask')
				""", snapshotId, periodId);
	}

	private void insertOpenStocktake(long periodId) {
		int suffix = SEQUENCE.incrementAndGet();
		ReferenceData reference = ensureReferenceData(suffix);
		LocalDate businessDate = this.jdbcTemplate.queryForObject("select end_date from biz_business_period where id = ?",
				LocalDate.class, periodId);
		this.jdbcTemplate.update("""
				insert into inv_stocktake (
					stocktake_no, business_date, scope_type, warehouse_id, reason, status, idempotency_key,
					created_by_user_id, created_by_username, updated_by_username
				)
				values (?, ?, 'WAREHOUSE', ?, '030 未终态盘点', 'COUNTING', ?, 1, 'admin', 'admin')
				""", "PC-STK-" + suffix, businessDate, reference.warehouseId(), "PC-STK-IDEMP-" + suffix);
	}

	private void insertProjectCostActivity(long periodId) {
		int suffix = SEQUENCE.incrementAndGet();
		ReferenceData reference = ensureReferenceData(suffix);
		String code = "PC-" + suffix + "-";
		LocalDate businessDate = this.jdbcTemplate.queryForObject("select end_date from biz_business_period where id = ?",
				LocalDate.class, periodId);
		Long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "CUS", code + "客户");
		Long projectId = this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at,
					activated_by, activated_at
				)
				values (?, ?, ?, 1, ?, ?, 'ACTIVE', 1000.00, 0, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "PC-PRJ-" + suffix, code + "项目", customerId, businessDate.minusDays(1),
				businessDate);
		Long movementId = this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, operator_name, occurred_at, quality_status, ownership_type, project_id,
					valuation_state, valuation_method, unit_cost, inventory_amount
				)
				values (?, 'ADJUSTMENT_INCREASE', 'IN', ?, ?, ?, 1.000000, 0, 1.000000,
					'PERIOD_CLOSE_TEST', ?, ?, ?, '030 项目成本来源', 'admin', now(), 'QUALIFIED', 'PROJECT', ?,
					'VALUED', 'MANUAL', 1.000000, 1.00)
				returning id
				""", Long.class, code + "MV", reference.warehouseId(), reference.materialId(), reference.unitId(),
				suffix, suffix, businessDate, projectId);
		this.jdbcTemplate.update("""
				insert into inv_value_movement (
					stock_movement_id, movement_no, movement_type, direction, warehouse_id, material_id,
					ownership_type, project_id, quantity, unit_cost, inventory_amount, valuation_method,
					valuation_state, source_type, source_id, source_line_id, business_date
				)
				values (?, ?, 'ADJUSTMENT_INCREASE', 'IN', ?, ?, 'PROJECT', ?, 1.000000, 1.000000,
					1.00, 'MANUAL', 'VALUED', 'PERIOD_CLOSE_TEST', ?, ?, ?)
				""", movementId, code + "VM", reference.warehouseId(), reference.materialId(), projectId, suffix,
				suffix, businessDate);
	}

	private void cancelPeriodCloseStocktakes() {
		this.jdbcTemplate.update("""
				update inv_stocktake
				set status = 'CANCELLED',
				    cancelled_at = now(),
				    updated_by_username = 'admin',
				    updated_at = now()
				where stocktake_no like 'PC-STK-%'
				and status in ('COUNTING', 'RECONCILED', 'SUBMITTED')
				""");
	}

	private ReferenceData ensureReferenceData(int suffix) {
		String code = "PC-REF-" + suffix + "-";
		Long unitId = this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 2, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "UNIT", code + "单位");
		Long warehouseId = this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'RAW', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "WH", code + "仓库");
		Long categoryId = this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 1, 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "CAT", code + "分类");
		Long materialId = this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, created_by, created_at, updated_by, updated_at)
				values (?, ?, '030', 'RAW', 'PURCHASED', ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "MAT", code + "物料", categoryId, unitId);
		return new ReferenceData(warehouseId, materialId, unitId);
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

	private List<String> actionCodes(JsonNode actions) {
		java.util.ArrayList<String> codes = new java.util.ArrayList<>();
		if (actions == null || actions.isNull()) {
			return codes;
		}
		for (JsonNode action : actions) {
			codes.add(action.asText());
		}
		return codes;
	}

	private List<Long> periodIds(JsonNode items) {
		java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
		for (JsonNode item : items) {
			ids.add(item.get("periodId").longValue());
		}
		return ids;
	}

	private JsonNode firstItemByCode(JsonNode items, String code) {
		for (JsonNode item : items) {
			if (code.equals(item.get("checkCode").asText())) {
				return item;
			}
		}
		throw new AssertionError("未找到检查项：" + code);
	}

	private long auditCount(long runId, String action, String result, String errorCode) {
		String errorPredicate = errorCode == null ? "and error_code is null" : "and error_code = ?";
		if (errorCode == null) {
			return this.jdbcTemplate.queryForObject("""
					select count(*) from biz_period_close_audit
					where run_id = ? and action = ? and result = ? %s
					""".formatted(errorPredicate), Long.class, runId, action, result);
		}
		return this.jdbcTemplate.queryForObject("""
				select count(*) from biz_period_close_audit
				where run_id = ? and action = ? and result = ? %s
				""".formatted(errorPredicate), Long.class, runId, action, result, errorCode);
	}

	private List<JsonNode> concurrentRequests(CheckedRequest request) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<JsonNode> first = executor.submit(() -> {
				assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
				return data(request.exchange());
			});
			Future<JsonNode> second = executor.submit(() -> {
				assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
				return data(request.exchange());
			});
			start.countDown();
			return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private void sleepBriefly() {
		try {
			Thread.sleep(300);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
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

	private record ReferenceData(Long warehouseId, Long materialId, Long unitId) {
	}

	@FunctionalInterface
	private interface CheckedRequest {

		ResponseEntity<String> exchange() throws Exception;

	}

}
