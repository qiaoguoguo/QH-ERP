package com.qherp.api.system.quality;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
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
		properties = "qherp.test.context=task17-quality-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QualityAdminControllerTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private InventoryPostingService inventoryPostingService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void processInspectionSplitsPendingInventoryAndWritesAudit() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 7, 10);
		long inspectionId = pendingInspection(fixture, "10.000000", businessDate);

		ResponseEntity<String> processed = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process",
				Map.of("businessDate", businessDate.toString(), "qualifiedQuantity", "7.000000",
						"rejectedQuantity", "2.000000", "frozenQuantity", "1.000000", "reason", "来料检验完成",
						"remark", "抽检拆分"),
				admin);

		assertOk(processed);
		JsonNode data = data(processed);
		assertThat(data.get("id").longValue()).isEqualTo(inspectionId);
		assertThat(data.get("status").asText()).isEqualTo("COMPLETED");
		assertDecimal(data, "inspectionQuantity", "10.000000");
		assertDecimal(data, "qualifiedQuantity", "7.000000");
		assertDecimal(data, "rejectedQuantity", "2.000000");
		assertDecimal(data, "frozenQuantity", "1.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.PENDING_INSPECTION), "0.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "7.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.REJECTED), "2.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.FROZEN), "1.000000");
		assertThat(movementCount("QUALITY_INSPECTION", inspectionId)).isEqualTo(6);
		assertThat(auditCount("QUALITY_INSPECTION_PROCESS", "QUALITY_INSPECTION", inspectionId)).isOne();
	}

	@Test
	void processInspectionKeepsBatchIdentityWhenSplittingQualityStatuses() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		LocalDate businessDate = LocalDate.of(2091, 7, 20);
		long batchId = insertBatch(fixture, "QI-BATCH-" + SEQUENCE.incrementAndGet(), businessDate);
		insertTrackedBalance(fixture, InventoryQualityStatus.PENDING_INSPECTION, "5.000000", batchId, null);
		long inspectionId = insertPendingInspection(fixture, "5.000000", businessDate);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", businessDate.toString());
		payload.put("qualifiedQuantity", "3.000000");
		payload.put("rejectedQuantity", "2.000000");
		payload.put("frozenQuantity", "0.000000");
		payload.put("reason", "批次质量拆分");
		payload.put("trackingAllocations",
				List.of(trackingAllocation(batchId, null, "3.000000", "QUALIFIED"),
						trackingAllocation(batchId, null, "2.000000", "REJECTED")));

		assertOk(exchange(HttpMethod.POST, "/api/admin/quality/inspections/" + inspectionId + "/process", payload,
				admin));

		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.PENDING_INSPECTION, batchId, null),
				"0.000000");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.QUALIFIED, batchId, null), "3.000000");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.REJECTED, batchId, null), "2.000000");
		assertThat(trackedMovementCount("QUALITY_INSPECTION", inspectionId, batchId, null)).isEqualTo(4);
	}

	@Test
	void pendingInspectionDetailReturnsSourceTrackingAllocationsAndProcessesMultipleBatchStatuses()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		LocalDate businessDate = LocalDate.of(2091, 7, 23);
		long sourceId = 1_706_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 1_716_000L + SEQUENCE.incrementAndGet();
		long firstBatchId = insertBatch(fixture, "QI-SRC-BATCH-A-" + SEQUENCE.incrementAndGet(), businessDate);
		long secondBatchId = insertBatch(fixture, "QI-SRC-BATCH-B-" + SEQUENCE.incrementAndGet(), businessDate);
		insertTrackedBalance(fixture, InventoryQualityStatus.PENDING_INSPECTION, "2.000000", firstBatchId, null);
		insertTrackedBalance(fixture, InventoryQualityStatus.PENDING_INSPECTION, "3.000000", secondBatchId, null);
		long firstSourceAllocationId = insertSourceTrackingAllocation(fixture, "PURCHASE_RECEIPT", sourceId,
				sourceLineId, firstBatchId, null, "2.000000");
		long secondSourceAllocationId = insertSourceTrackingAllocation(fixture, "PURCHASE_RECEIPT", sourceId,
				sourceLineId, secondBatchId, null, "3.000000");
		long inspectionId = insertPendingInspectionFromSource(fixture, sourceId, sourceLineId, "5.000000",
				businessDate);

		ResponseEntity<String> detail = get("/api/admin/quality/inspections/" + inspectionId, admin);
		assertOk(detail);
		JsonNode allocations = data(detail).get("trackingAllocations");
		assertThat(allocations.size()).isEqualTo(2);
		JsonNode firstAllocation = allocationByBatchId(allocations, firstBatchId);
		JsonNode secondAllocation = allocationByBatchId(allocations, secondBatchId);
		assertThat(firstAllocation.get("sourceAllocationId").longValue()).isEqualTo(firstSourceAllocationId);
		assertThat(secondAllocation.get("sourceAllocationId").longValue()).isEqualTo(secondSourceAllocationId);
		assertDecimal(firstAllocation, "quantity", "2.000000");
		assertDecimal(secondAllocation, "quantity", "3.000000");

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", businessDate.toString());
		payload.put("qualifiedQuantity", "2.000000");
		payload.put("rejectedQuantity", "3.000000");
		payload.put("frozenQuantity", "0.000000");
		payload.put("reason", "来源批次质量拆分");
		payload.put("trackingAllocations",
				List.of(trackingAllocation(firstBatchId, null, "2.000000", "QUALIFIED"),
						trackingAllocation(secondBatchId, null, "3.000000", "REJECTED")));

		assertOk(exchange(HttpMethod.POST, "/api/admin/quality/inspections/" + inspectionId + "/process", payload,
				admin));
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.QUALIFIED, firstBatchId, null),
				"2.000000");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.REJECTED, secondBatchId, null),
				"3.000000");
	}

	@Test
	void processInspectionMovesSerialsAsSingleQualityIdentities() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "SERIAL");
		LocalDate businessDate = LocalDate.of(2091, 7, 21);
		long firstSerialId = insertSerial(fixture, "QI-SN-A-" + SEQUENCE.incrementAndGet(), businessDate,
				InventoryQualityStatus.PENDING_INSPECTION);
		long secondSerialId = insertSerial(fixture, "QI-SN-B-" + SEQUENCE.incrementAndGet(), businessDate,
				InventoryQualityStatus.PENDING_INSPECTION);
		insertTrackedBalance(fixture, InventoryQualityStatus.PENDING_INSPECTION, "1.000000", null, firstSerialId);
		insertTrackedBalance(fixture, InventoryQualityStatus.PENDING_INSPECTION, "1.000000", null, secondSerialId);
		long inspectionId = insertPendingInspection(fixture, "2.000000", businessDate);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", businessDate.toString());
		payload.put("qualifiedQuantity", "1.000000");
		payload.put("rejectedQuantity", "1.000000");
		payload.put("frozenQuantity", "0.000000");
		payload.put("reason", "序列质量逐件确认");
		payload.put("trackingAllocations",
				List.of(trackingAllocation(null, firstSerialId, "1.000000", "QUALIFIED"),
						trackingAllocation(null, secondSerialId, "1.000000", "REJECTED")));

		assertOk(exchange(HttpMethod.POST, "/api/admin/quality/inspections/" + inspectionId + "/process", payload,
				admin));

		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.QUALIFIED, null, firstSerialId),
				"1.000000");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.REJECTED, null, secondSerialId),
				"1.000000");
		assertThat(serialQualityStatus(firstSerialId)).isEqualTo("QUALIFIED");
		assertThat(serialQualityStatus(secondSerialId)).isEqualTo("REJECTED");
	}

	@Test
	void freezeAndUnfreezeKeepBatchIdentityAndAffectAvailableQuantity() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		LocalDate businessDate = LocalDate.of(2091, 9, 20);
		long batchId = insertBatch(fixture, "QI-FREEZE-BATCH-" + SEQUENCE.incrementAndGet(), businessDate);
		insertTrackedBalance(fixture, InventoryQualityStatus.QUALIFIED, "3.000000", batchId, null);

		Map<String, Object> freezePayload = qualityTransferPayload(fixture, "1.000000", businessDate, "批次冻结");
		freezePayload.put("trackingAllocations",
				List.of(trackingAllocation(batchId, null, "1.000000", "FROZEN")));
		assertOk(exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze", freezePayload, admin));

		JsonNode frozenBalance = firstItem(get("/api/admin/inventory/balances?trackingMethod=BATCH&batchId="
				+ batchId, admin));
		assertDecimal(frozenBalance, "availableQuantity", "2.000000");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.FROZEN, batchId, null), "1.000000");

		Map<String, Object> unfreezePayload = qualityTransferPayload(fixture, "1.000000", businessDate, "批次解冻");
		unfreezePayload.put("trackingAllocations",
				List.of(trackingAllocation(batchId, null, "1.000000", "QUALIFIED")));
		assertOk(exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/unfreeze", unfreezePayload, admin));

		JsonNode unfrozenBalance = firstItem(get("/api/admin/inventory/balances?trackingMethod=BATCH&batchId="
				+ batchId, admin));
		assertDecimal(unfrozenBalance, "availableQuantity", "3.000000");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.FROZEN, batchId, null), "0.000000");
	}

	@Test
	void freezeAndUnfreezeDefaultTrackingAllocationQualityStatusWhenOmitted() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		LocalDate businessDate = LocalDate.of(2091, 9, 21);
		long batchId = insertBatch(fixture, "QI-DEFAULT-QS-BATCH-" + SEQUENCE.incrementAndGet(), businessDate);
		insertTrackedBalance(fixture, InventoryQualityStatus.QUALIFIED, "2.000000", batchId, null);

		Map<String, Object> freezeAllocation = trackingAllocation(batchId, null, "1.000000", "FROZEN");
		freezeAllocation.remove("qualityStatus");
		Map<String, Object> freezePayload = qualityTransferPayload(fixture, "1.000000", businessDate, "默认冻结状态");
		freezePayload.put("trackingAllocations", List.of(freezeAllocation));
		assertOk(exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze", freezePayload, admin));
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.FROZEN, batchId, null), "1.000000");

		Map<String, Object> unfreezeAllocation = trackingAllocation(batchId, null, "1.000000", "QUALIFIED");
		unfreezeAllocation.remove("qualityStatus");
		Map<String, Object> unfreezePayload = qualityTransferPayload(fixture, "1.000000", businessDate, "默认解冻状态");
		unfreezePayload.put("trackingAllocations", List.of(unfreezeAllocation));
		assertOk(exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/unfreeze", unfreezePayload, admin));
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.QUALIFIED, batchId, null), "2.000000");
	}

	@Test
	void freezeTrackedBatchReservedQuantityReturnsTrackingUnavailable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		LocalDate businessDate = LocalDate.of(2091, 9, 22);
		long batchId = insertBatch(fixture, "QI-RSV-BATCH-" + SEQUENCE.incrementAndGet(), businessDate);
		insertTrackedBalance(fixture, InventoryQualityStatus.QUALIFIED, "3.000000", batchId, null);
		insertReservation(fixture, "RESERVATION", "SALES_ORDER", "2.000000");

		Map<String, Object> payload = qualityTransferPayload(fixture, "2.000000", businessDate, "追踪冻结预留不足");
		payload.put("trackingAllocations", List.of(trackingAllocation(batchId, null, "2.000000", "FROZEN")));
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				payload, admin);

		assertError(response, HttpStatus.CONFLICT, "INVENTORY_TRACKING_NOT_AVAILABLE");
	}

	@Test
	void processInspectionAggregatesBatchAllocationsBeforeTransfer() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		LocalDate businessDate = LocalDate.of(2091, 7, 22);
		long batchId = insertBatch(fixture, "QI-OVER-BATCH-" + SEQUENCE.incrementAndGet(), businessDate);
		insertTrackedBalance(fixture, InventoryQualityStatus.PENDING_INSPECTION, "5.000000", batchId, null);
		long inspectionId = insertPendingInspection(fixture, "6.000000", businessDate);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", businessDate.toString());
		payload.put("qualifiedQuantity", "6.000000");
		payload.put("rejectedQuantity", "0.000000");
		payload.put("frozenQuantity", "0.000000");
		payload.put("reason", "批次聚合校验");
		payload.put("trackingAllocations",
				List.of(trackingAllocation(batchId, null, "3.000000", "QUALIFIED"),
						trackingAllocation(batchId, null, "3.000000", "QUALIFIED")));

		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process", payload, admin);

		assertError(response, HttpStatus.CONFLICT, "INVENTORY_TRACKING_STOCK_NOT_ENOUGH");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.PENDING_INSPECTION, batchId, null),
				"5.000000");
		assertDecimal(trackingBalanceQuantity(fixture, InventoryQualityStatus.QUALIFIED, batchId, null), "0.000000");
		assertThat(trackedMovementCount("QUALITY_INSPECTION", inspectionId, batchId, null)).isZero();
		assertThat(trackingAllocationCount("QUALITY_INSPECTION", inspectionId)).isZero();
	}

	@Test
	void processInspectionRejectsQuantityMismatchWithoutPartialWrites() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 7, 11);
		long inspectionId = pendingInspection(fixture, "10.000000", businessDate);

		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process",
				Map.of("businessDate", businessDate.toString(), "qualifiedQuantity", "6.000000",
						"rejectedQuantity", "1.000000", "frozenQuantity", "1.000000", "reason", "数量不守恒"),
				admin);

		assertError(response, HttpStatus.BAD_REQUEST, "QUALITY_INSPECTION_QUANTITY_MISMATCH");
		assertThat(inspectionStatus(inspectionId)).isEqualTo("PENDING");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.PENDING_INSPECTION), "10.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "0.000000");
		assertThat(movementCount("QUALITY_INSPECTION", inspectionId)).isZero();
		assertThat(auditCount("QUALITY_INSPECTION_PROCESS", "QUALITY_INSPECTION", inspectionId)).isZero();
	}

	@Test
	void lockedPeriodRejectsInspectionProcessingAndKeepsPendingState() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 8, 12);
		long inspectionId = pendingInspection(fixture, "5.000000", businessDate);
		lockPeriod(businessDate);

		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process",
				Map.of("businessDate", businessDate.toString(), "qualifiedQuantity", "5.000000",
						"rejectedQuantity", "0.000000", "frozenQuantity", "0.000000", "reason", "锁定期间"),
				admin);

		assertError(response, HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertThat(inspectionStatus(inspectionId)).isEqualTo("PENDING");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.PENDING_INSPECTION), "5.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "0.000000");
		assertThat(movementCount("QUALITY_INSPECTION", inspectionId)).isZero();
	}

	@Test
	void processInspectionWritesQualityMovementsWithRequestBusinessDate() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate sourceDate = LocalDate.of(2092, 1, 12);
		LocalDate processDate = LocalDate.of(2092, 2, 12);
		long inspectionId = pendingInspection(fixture, "5.000000", sourceDate);
		lockPeriod(sourceDate);

		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process",
				Map.of("businessDate", processDate.toString(), "qualifiedQuantity", "5.000000",
						"rejectedQuantity", "0.000000", "frozenQuantity", "0.000000", "reason", "按处理日期写流水"),
				admin);

		assertOk(response);
		assertThat(movementBusinessDates("QUALITY_INSPECTION", inspectionId)).containsExactly(processDate);
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.PENDING_INSPECTION), "0.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "5.000000");
	}

	@Test
	void freezeAndUnfreezeMoveQualifiedInventoryWithPeriodGuard() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 9, 13);
		postInventory(fixture, "5.000000", InventoryQualityStatus.QUALIFIED, "QUALITY_FREEZE_TEST");

		ResponseEntity<String> frozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				qualityTransferPayload(fixture, "3.000000", businessDate, "质量冻结"), admin);

		assertOk(frozen);
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "2.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.FROZEN), "3.000000");

		ResponseEntity<String> unfrozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/unfreeze",
				qualityTransferPayload(fixture, "1.000000", businessDate, "质量解冻"), admin);

		assertOk(unfrozen);
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "3.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.FROZEN), "2.000000");

		lockPeriod(businessDate.plusMonths(1));
		ResponseEntity<String> locked = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				qualityTransferPayload(fixture, "1.000000", businessDate.plusMonths(1), "锁定冻结"), admin);
		assertError(locked, HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "3.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.FROZEN), "2.000000");
	}

	@Test
	void freezeRejectsReservedQualifiedInventory() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		postInventory(fixture, "5.000000", InventoryQualityStatus.QUALIFIED, "QUALITY_FREEZE_RESERVED_TEST");
		insertReservation(fixture, "RESERVATION", "SALES_ORDER", "4.000000");

		ResponseEntity<String> frozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				qualityTransferPayload(fixture, "2.000000", LocalDate.of(2091, 9, 16), "冻结已预留库存"), admin);

		assertError(frozen, HttpStatus.CONFLICT, "INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "5.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.FROZEN), "0.000000");
	}

	@Test
	void freezeAndUnfreezeUsePersistentSourceKeysOutsideLegacyJvmRange() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 9, 14);
		postInventory(fixture, "6.000000", InventoryQualityStatus.QUALIFIED, "QUALITY_PERSISTENT_KEY_TEST");
		seedLegacyQualityStatusTransferSourceLineCollisions(fixture);

		ResponseEntity<String> frozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				qualityTransferPayload(fixture, "2.000000", businessDate, "持久来源键冻结"), admin);
		ResponseEntity<String> unfrozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/unfreeze",
				qualityTransferPayload(fixture, "1.000000", businessDate, "持久来源键解冻"), admin);

		assertOk(frozen);
		assertOk(unfrozen);
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "5.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.FROZEN), "1.000000");
		List<Long> sourceIds = qualityStatusTransferSourceIds(businessDate);
		List<Long> sourceLineIds = qualityStatusTransferSourceLineIds(businessDate);
		assertThat(sourceIds).hasSize(2).doesNotHaveDuplicates();
		assertThat(sourceLineIds).hasSize(4).doesNotHaveDuplicates();
		assertThat(sourceLineIds).allMatch((sourceLineId) -> sourceLineId > 50_000_000_000L);
	}

	@Test
	void processInspectionRollsBackWhenLaterQualityTransferFailsAfterFirstTransfer() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 9, 15);
		long inspectionId = pendingInspection(fixture, "5.000000", businessDate);
		seedBlockingQualityInspectionMovement(fixture, inspectionId, InventoryQualityStatus.REJECTED);

		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process",
				Map.of("businessDate", businessDate.toString(), "qualifiedQuantity", "3.000000",
						"rejectedQuantity", "2.000000", "frozenQuantity", "0.000000", "reason", "模拟后续转移失败"),
				admin);

		assertError(response, HttpStatus.CONFLICT, "INVENTORY_MOVEMENT_SOURCE_DUPLICATED");
		assertThat(inspectionStatus(inspectionId)).isEqualTo("PENDING");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.PENDING_INSPECTION), "5.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.QUALIFIED), "0.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.REJECTED), "0.000000");
		assertDecimal(balanceQuantity(fixture, InventoryQualityStatus.FROZEN), "0.000000");
		assertThat(movementCount("QUALITY_INSPECTION", inspectionId)).isZero();
		assertThat(auditCount("QUALITY_INSPECTION_PROCESS", "QUALITY_INSPECTION", inspectionId)).isZero();
	}

	@Test
	void qualityInspectionListAndDetailExposeFrontendContractFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 10, 14);
		long inspectionId = pendingInspection(fixture, "4.000000", businessDate);

		ResponseEntity<String> list = get("/api/admin/quality/inspections?status=PENDING&page=1&pageSize=20", admin);
		assertOk(list);
		JsonNode item = firstItem(list);
		assertThat(item.get("id").longValue()).isEqualTo(inspectionId);
		assertThat(item.get("inspectionNo").asText()).startsWith("QI-");
		assertThat(item.get("sourceType").asText()).isEqualTo("PURCHASE_RECEIPT");
		assertThat(item.get("sourceTypeName").asText()).isEqualTo("采购入库");
		assertThat(item.get("status").asText()).isEqualTo("PENDING");
		assertThat(item.get("statusName").asText()).isEqualTo("待处理");
		assertThat(item.get("qualityStatus").asText()).isEqualTo("PENDING_INSPECTION");
		assertThat(item.get("qualityStatusName").asText()).isEqualTo("待检");
		assertDecimal(item, "inspectionQuantity", "4.000000");

		ResponseEntity<String> detail = get("/api/admin/quality/inspections/" + inspectionId, admin);
		assertOk(detail);
		JsonNode data = data(detail);
		assertThat(data.get("createdBy").asText()).isEqualTo("test");
		assertThat(data.hasNonNull("createdAt")).isTrue();
		assertThat(data.hasNonNull("updatedAt")).isTrue();
		assertThat(data.get("currentQualityStatus").asText()).isEqualTo("PENDING_INSPECTION");
		assertThat(data.get("currentQualityStatusName").asText()).isEqualTo("待检");
	}

	@Test
	void qualityInspectionListReturnsProductionCompletionReceiptNo() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 10, 15);
		String receiptNo = "MCR-QI-" + SEQUENCE.incrementAndGet();
		long inspectionId = productionCompletionInspection(fixture, receiptNo, "3.000000", businessDate);

		ResponseEntity<String> list = get(
				"/api/admin/quality/inspections?sourceType=PRODUCTION_COMPLETION&page=1&pageSize=20", admin);

		assertOk(list);
		JsonNode item = itemByInspectionId(data(list).get("items"), inspectionId);
		assertThat(item.get("sourceType").asText()).isEqualTo("PRODUCTION_COMPLETION");
		assertThat(item.get("sourceDocumentNo").asText()).isEqualTo(receiptNo);
	}

	@Test
	void pendingProductionCompletionInspectionDetailReturnsCompletionReceiptTrackingAllocations()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		LocalDate businessDate = LocalDate.of(2091, 10, 18);
		String receiptNo = "MCR-QI-TRACK-" + SEQUENCE.incrementAndGet();
		long inspectionId = productionCompletionInspection(fixture, receiptNo, "3.000000", businessDate);
		long receiptId = inspectionSourceId(inspectionId);
		long batchId = insertBatch(fixture, "QI-MCR-BATCH-" + SEQUENCE.incrementAndGet(), businessDate);
		insertTrackedBalance(fixture, InventoryQualityStatus.PENDING_INSPECTION, "3.000000", batchId, null);
		long sourceAllocationId = insertSourceTrackingAllocation(fixture, "PRODUCTION_COMPLETION_RECEIPT",
				receiptId, receiptId, batchId, null, "3.000000");

		ResponseEntity<String> detail = get("/api/admin/quality/inspections/" + inspectionId, admin);

		assertOk(detail);
		JsonNode allocations = data(detail).get("trackingAllocations");
		assertThat(allocations.size()).isOne();
		JsonNode allocation = allocations.get(0);
		assertThat(allocation.get("sourceAllocationId").longValue()).isEqualTo(sourceAllocationId);
		assertThat(allocation.get("batchId").longValue()).isEqualTo(batchId);
		assertDecimal(allocation, "quantity", "3.000000");
	}

	@Test
	void qualityInspectionListFiltersByWarehouseAndMaterial() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture matched = fixture();
		QualityFixture otherWarehouse = fixture();
		long otherMaterialId = insertMaterial("QI_MAT_FILTER_" + SEQUENCE.incrementAndGet(), "质检过滤物料",
				insertMaterialCategory("QI_CAT_FILTER_" + SEQUENCE.incrementAndGet()), matched.unitId());
		QualityFixture otherMaterial = new QualityFixture(matched.unitId(), matched.warehouseId(), otherMaterialId);
		LocalDate businessDate = LocalDate.of(2091, 10, 16);
		long matchedId = pendingInspection(matched, "4.000000", businessDate);
		long otherWarehouseId = pendingInspection(otherWarehouse, "5.000000", businessDate);
		long otherMaterialInspectionId = pendingInspection(otherMaterial, "6.000000", businessDate);

		ResponseEntity<String> response = get("/api/admin/quality/inspections?warehouseId=" + matched.warehouseId()
				+ "&materialId=" + matched.materialId() + "&page=1&pageSize=100", admin);

		assertOk(response);
		JsonNode items = data(response).get("items");
		assertThat(containsInspectionId(items, matchedId)).isTrue();
		assertThat(containsInspectionId(items, otherWarehouseId)).isFalse();
		assertThat(containsInspectionId(items, otherMaterialInspectionId)).isFalse();
		JsonNode matchedItem = itemByInspectionId(items, matchedId);
		assertThat(matchedItem.hasNonNull("version")).isTrue();
		assertThat(matchedItem.get("version").intValue()).isZero();
	}

	@Test
	void qualityInspectionListFiltersByBusinessDateContractParams() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		long beforeId = pendingInspection(fixture, "2.000000", LocalDate.of(2091, 10, 9));
		long matchedId = pendingInspection(fixture, "3.000000", LocalDate.of(2091, 10, 10));
		long afterId = pendingInspection(fixture, "4.000000", LocalDate.of(2091, 10, 11));

		ResponseEntity<String> response = get(
				"/api/admin/quality/inspections?businessDateFrom=2091-10-10&businessDateTo=2091-10-10&page=1&pageSize=100",
				admin);

		assertOk(response);
		JsonNode items = data(response).get("items");
		assertThat(containsInspectionId(items, matchedId)).isTrue();
		assertThat(containsInspectionId(items, beforeId)).isFalse();
		assertThat(containsInspectionId(items, afterId)).isFalse();
	}

	@Test
	void qualityInspectionDetailAndProcessResponseExposeVersion() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 10, 17);
		long inspectionId = pendingInspection(fixture, "3.000000", businessDate);

		ResponseEntity<String> detail = get("/api/admin/quality/inspections/" + inspectionId, admin);
		assertOk(detail);
		JsonNode detailData = data(detail);
		assertThat(detailData.hasNonNull("version")).isTrue();
		assertThat(detailData.get("version").intValue()).isZero();

		ResponseEntity<String> processed = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process",
				Map.of("businessDate", businessDate.toString(), "qualifiedQuantity", "3.000000",
						"rejectedQuantity", "0.000000", "frozenQuantity", "0.000000", "reason", "版本递增"),
				admin);

		assertOk(processed);
		JsonNode processedData = data(processed);
		assertThat(processedData.hasNonNull("version")).isTrue();
		assertThat(processedData.get("version").intValue()).isOne();
	}

	@Test
	void qualityInspectionProcessingRequiresDedicatedPermission() throws Exception {
		QualityFixture fixture = fixture();
		LocalDate businessDate = LocalDate.of(2091, 11, 15);
		long inspectionId = pendingInspection(fixture, "2.000000", businessDate);
		AuthenticatedSession viewer = createQualityUserAndLogin("quality-viewer-", "Q_VIEW_",
				List.of("quality:inspection:view"));

		assertOk(get("/api/admin/quality/inspections", viewer));
		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/quality/inspections/" + inspectionId + "/process",
				Map.of("businessDate", businessDate.toString(), "qualifiedQuantity", "2.000000",
						"rejectedQuantity", "0.000000", "frozenQuantity", "0.000000", "reason", "权限校验"),
				viewer);

		assertError(response, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertThat(inspectionStatus(inspectionId)).isEqualTo("PENDING");
	}

	private Map<String, Object> qualityTransferPayload(QualityFixture fixture, String quantity, LocalDate businessDate,
			String reason) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", fixture.warehouseId());
		payload.put("materialId", fixture.materialId());
		payload.put("unitId", fixture.unitId());
		payload.put("businessDate", businessDate.toString());
		payload.put("quantity", quantity);
		payload.put("reason", reason);
		payload.put("remark", reason + "备注");
		return payload;
	}

	private Map<String, Object> trackingAllocation(Long batchId, Long serialId, String quantity,
			String qualityStatus) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		if (batchId != null) {
			allocation.put("batchId", batchId);
		}
		if (serialId != null) {
			allocation.put("serialId", serialId);
		}
		allocation.put("quantity", quantity);
		allocation.put("qualityStatus", qualityStatus);
		return allocation;
	}

	private long pendingInspection(QualityFixture fixture, String quantity, LocalDate businessDate) {
		long sourceId = 1_700_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 1_710_000L + SEQUENCE.incrementAndGet();
		postInventory(fixture, quantity, InventoryQualityStatus.PENDING_INSPECTION, "QUALITY_PENDING_TEST", sourceId,
				sourceLineId, businessDate);
		return this.jdbcTemplate.queryForObject("""
				insert into qua_quality_inspection (
					inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id,
					business_date, inspection_quantity, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'test', now(), 'test', now())
				returning id
				""", Long.class, "QI-TEST-" + SEQUENCE.incrementAndGet(), sourceId, sourceLineId, fixture.warehouseId(),
				fixture.materialId(), fixture.unitId(), businessDate, new BigDecimal(quantity));
	}

	private long insertPendingInspection(QualityFixture fixture, String quantity, LocalDate businessDate) {
		long sourceId = 1_705_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 1_715_000L + SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into qua_quality_inspection (
					inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id,
					business_date, inspection_quantity, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'test', now(), 'test', now())
				returning id
				""", Long.class, "QI-TRACK-" + SEQUENCE.incrementAndGet(), sourceId, sourceLineId,
				fixture.warehouseId(), fixture.materialId(), fixture.unitId(), businessDate,
				new BigDecimal(quantity));
	}

	private long insertPendingInspectionFromSource(QualityFixture fixture, long sourceId, long sourceLineId,
			String quantity, LocalDate businessDate) {
		return this.jdbcTemplate.queryForObject("""
				insert into qua_quality_inspection (
					inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id,
					business_date, inspection_quantity, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'test', now(), 'test', now())
				returning id
				""", Long.class, "QI-SRC-TRACK-" + SEQUENCE.incrementAndGet(), sourceId, sourceLineId,
				fixture.warehouseId(), fixture.materialId(), fixture.unitId(), businessDate,
				new BigDecimal(quantity));
	}

	private long productionCompletionInspection(QualityFixture fixture, String receiptNo, String quantity,
			LocalDate businessDate) {
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
					effective_from, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 1.000000, ?, 'ENABLED', ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, "QI-BOM-" + SEQUENCE.incrementAndGet(), fixture.materialId(),
				"V" + SEQUENCE.incrementAndGet(), "质量完工来源测试 BOM", fixture.unitId(), businessDate);
		long workOrderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, created_by, created_at,
					updated_by, updated_at
				)
				values (?, ?, ?, 3.000000, ?, ?, ?, ?, 'RELEASED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "QI-WO-" + SEQUENCE.incrementAndGet(), fixture.materialId(), bomId,
				fixture.warehouseId(), fixture.warehouseId(), businessDate.minusDays(1), businessDate.plusDays(1));
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into mfg_completion_receipt (
					receipt_no, work_order_id, status, business_date, receipt_warehouse_id, quantity, created_by,
					created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, 'POSTED', ?, ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, receiptNo, workOrderId, businessDate, fixture.warehouseId(), new BigDecimal(quantity));
		postInventory(fixture, quantity, InventoryQualityStatus.PENDING_INSPECTION, "PRODUCTION_COMPLETION", receiptId,
				receiptId, businessDate);
		return this.jdbcTemplate.queryForObject("""
				insert into qua_quality_inspection (
					inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id,
					business_date, inspection_quantity, status, created_by, created_at, updated_by, updated_at
				)
				values (?, 'PRODUCTION_COMPLETION', ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'test', now(), 'test', now())
				returning id
				""", Long.class, "QI-TEST-" + SEQUENCE.incrementAndGet(), receiptId, receiptId, fixture.warehouseId(),
				fixture.materialId(), fixture.unitId(), businessDate, new BigDecimal(quantity));
	}

	private void postInventory(QualityFixture fixture, String quantity, InventoryQualityStatus qualityStatus,
			String sourceType) {
		long sourceId = 1_720_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 1_730_000L + SEQUENCE.incrementAndGet();
		postInventory(fixture, quantity, qualityStatus, sourceType, sourceId, sourceLineId, LocalDate.now());
	}

	private void postInventory(QualityFixture fixture, String quantity, InventoryQualityStatus qualityStatus,
			String sourceType, long sourceId, long sourceLineId, LocalDate businessDate) {
		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, fixture.warehouseId(),
				fixture.materialId(), fixture.unitId(), new BigDecimal(quantity), qualityStatus, sourceType, sourceId,
				sourceLineId, businessDate, "质量状态测试入库", "质量状态测试入库", "tester"));
	}

	private long insertBatch(QualityFixture fixture, String batchNo, LocalDate businessDate) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'QUALITY_TEST', ?, ?, ?, '质量追踪测试', 'tester', now(), 'tester', now())
				returning id
				""", Long.class, fixture.materialId(), batchNo, 1_760_000L + SEQUENCE.incrementAndGet(),
				1_770_000L + SEQUENCE.incrementAndGet(), businessDate);
	}

	private long insertSerial(QualityFixture fixture, String serialNo, LocalDate businessDate,
			InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_serial (
					material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status,
					stock_status, business_date, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'QUALITY_TEST', ?, ?, ?, ?, 'IN_STOCK', ?, '质量追踪测试',
					'tester', now(), 'tester', now())
				returning id
				""", Long.class, fixture.materialId(), serialNo, 1_780_000L + SEQUENCE.incrementAndGet(),
				1_790_000L + SEQUENCE.incrementAndGet(), fixture.warehouseId(), qualityStatus.name(),
				businessDate);
	}

	private void insertTrackedBalance(QualityFixture fixture, InventoryQualityStatus qualityStatus, String quantity,
			Long batchId, Long serialId) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					batch_id, serial_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, ?, ?, ?, now(), now())
				""", fixture.warehouseId(), fixture.materialId(), fixture.unitId(), new BigDecimal(quantity),
				qualityStatus.name(), batchId, serialId);
	}

	private long insertSourceTrackingAllocation(QualityFixture fixture, String documentType, long documentId,
			long documentLineId, Long batchId, Long serialId, String quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_tracking_allocation (
					allocation_type, document_type, document_id, document_line_id, source_type, source_id,
					source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, serial_id,
					quantity, created_by, created_at, updated_by, updated_at
				)
				values ('INBOUND', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_INSPECTION', ?, ?, ?, 'test', now(), 'test',
					now())
				returning id
				""", Long.class, documentType, documentId, documentLineId, documentType, documentId, documentLineId,
				fixture.warehouseId(), fixture.materialId(), fixture.unitId(), batchId, serialId,
				new BigDecimal(quantity));
	}

	private long insertReservation(QualityFixture fixture, String reservationType, String sourceType, String quantity) {
		long sourceId = 1_740_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 1_750_000L + SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id, quality_status,
					quantity, released_quantity, consumed_quantity, source_type, source_id, source_line_id,
					source_document_no, business_date, reason, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ACTIVE', ?, ?, ?, 'QUALIFIED', ?, 0, 0, ?, ?, ?, ?, ?, ?, 'tester', now(), 'tester',
					now())
				returning id
				""", Long.class, "QI-RSV-" + SEQUENCE.incrementAndGet(), reservationType, fixture.warehouseId(),
				fixture.materialId(), fixture.unitId(), new BigDecimal(quantity), sourceType, sourceId, sourceLineId,
				sourceType + "-" + sourceId, LocalDate.now(), "质量冻结预留约束测试");
	}

	private void setTrackingMethod(long materialId, String trackingMethod) {
		this.jdbcTemplate.update("update mst_material set tracking_method = ?, updated_at = now() where id = ?",
				trackingMethod, materialId);
	}

	private void seedLegacyQualityStatusTransferSourceLineCollisions(QualityFixture fixture) {
		this.jdbcTemplate.update("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at, quality_status
				)
				select 'LEGACY-QST-' || g, 'QUALITY_STATUS_TRANSFER', 'OUT', ?, ?, ?, 1.000000,
				       1.000000, 0.000000, 'QUALITY_STATUS_TRANSFER', g, g * 10 + 1, ?,
				       '旧内存来源键占位', null, 'legacy', now(), 'QUALIFIED'
				from generate_series(2000000001::bigint, 2000000200::bigint) as g
				where not exists (
					select 1
					from inv_stock_movement mv
					where mv.source_type = 'QUALITY_STATUS_TRANSFER'
					and mv.source_line_id = g * 10 + 1
					and mv.batch_id is null
					and mv.serial_id is null
				)
				""", fixture.warehouseId(), fixture.materialId(), fixture.unitId(), LocalDate.of(2090, 1, 1));
	}

	private void seedBlockingQualityInspectionMovement(QualityFixture fixture, long inspectionId,
			InventoryQualityStatus targetStatus) {
		long baseSourceLineId = Math.addExact(Math.multiplyExact(inspectionId, 100L), targetStatus.ordinal());
		long blockingSourceLineId = Math.addExact(Math.multiplyExact(baseSourceLineId, 10L),
				InventoryQualityStatus.PENDING_INSPECTION.ordinal());
		this.jdbcTemplate.update("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at, quality_status
				)
				values (?, 'QUALITY_STATUS_TRANSFER', 'OUT', ?, ?, ?, 1.000000, 1.000000, 0.000000,
					'QUALITY_INSPECTION', ?, ?, ?, '阻断质量确认第二段转移', null, 'tester', now(),
					'PENDING_INSPECTION')
				""", "QI-BLOCK-" + SEQUENCE.incrementAndGet(), fixture.warehouseId(), fixture.materialId(),
				fixture.unitId(), 9_900_000L + SEQUENCE.incrementAndGet(), blockingSourceLineId,
				LocalDate.of(2090, 1, 2));
	}

	private QualityFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("QI_UNIT_" + suffix, "个");
		long warehouseId = insertWarehouse("QI_WH_" + suffix, "质检仓");
		long categoryId = insertMaterialCategory("QI_CAT_" + suffix);
		long materialId = insertMaterial("QI_MAT_" + suffix, "质检物料", categoryId, unitId);
		return new QualityFixture(unitId, warehouseId, materialId);
	}

	private long insertUnit(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertWarehouse(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "质检分类" + code);
	}

	private long insertMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, material_type, source_type, category_id, unit_id, status,
					created_by, created_at, updated_by, updated_at)
				values (?, ?, 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private AuthenticatedSession createQualityUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "质量测试角色", "质量测试角色");
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
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

	private BigDecimal balanceQuantity(QualityFixture fixture, InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				""", BigDecimal.class, fixture.warehouseId(), fixture.materialId(), qualityStatus.name());
	}

	private BigDecimal trackingBalanceQuantity(QualityFixture fixture, InventoryQualityStatus qualityStatus,
			Long batchId, Long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				""", BigDecimal.class, fixture.warehouseId(), fixture.materialId(), qualityStatus.name(), batchId,
				serialId);
	}

	private long movementCount(String sourceType, long sourceId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and source_id = ?
				""", Long.class, sourceType, sourceId);
	}

	private long trackedMovementCount(String sourceType, long sourceId, Long batchId, Long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and source_id = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				""", Long.class, sourceType, sourceId, batchId, serialId);
	}

	private long trackingAllocationCount(String documentType, long documentId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_tracking_allocation
				where document_type = ?
				and document_id = ?
				""", Long.class, documentType, documentId);
	}

	private String serialQualityStatus(long serialId) {
		return this.jdbcTemplate.queryForObject("select quality_status from inv_serial where id = ?", String.class,
				serialId);
	}

	private List<LocalDate> movementBusinessDates(String sourceType, long sourceId) {
		return this.jdbcTemplate.query("""
				select distinct business_date
				from inv_stock_movement
				where source_type = ?
				and source_id = ?
				order by business_date
				""", (rs, rowNum) -> rs.getObject("business_date", LocalDate.class), sourceType, sourceId);
	}

	private List<Long> qualityStatusTransferSourceIds(LocalDate businessDate) {
		return this.jdbcTemplate.query("""
				select distinct source_id
				from inv_stock_movement
				where source_type = 'QUALITY_STATUS_TRANSFER'
				and business_date = ?
				order by source_id
				""", (rs, rowNum) -> rs.getLong("source_id"), businessDate);
	}

	private List<Long> qualityStatusTransferSourceLineIds(LocalDate businessDate) {
		return this.jdbcTemplate.query("""
				select source_line_id
				from inv_stock_movement
				where source_type = 'QUALITY_STATUS_TRANSFER'
				and business_date = ?
				order by source_line_id
				""", (rs, rowNum) -> rs.getLong("source_line_id"), businessDate);
	}

	private long auditCount(String action, String targetType, long targetId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				""", Long.class, action, targetType, Long.toString(targetId));
	}

	private String inspectionStatus(long inspectionId) {
		return this.jdbcTemplate.queryForObject("select status from qua_quality_inspection where id = ?",
				String.class, inspectionId);
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at) values (?, ?, ?, ?, 'LOCKED', now(), now())",
				"QI-LOCK-" + SEQUENCE.incrementAndGet(), "质量锁定期间", date.withDayOfMonth(1),
				date.withDayOfMonth(date.lengthOfMonth()));
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session == null ? null : session.sessionCookie(), null), String.class);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, Object body, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
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
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private JsonNode firstItem(ResponseEntity<String> response) throws Exception {
		JsonNode items = data(response).get("items");
		assertThat(items.size()).isGreaterThan(0);
		return items.get(0);
	}

	private boolean containsInspectionId(JsonNode items, long inspectionId) {
		for (JsonNode item : items) {
			if (item.get("id").longValue() == inspectionId) {
				return true;
			}
		}
		return false;
	}

	private JsonNode itemByInspectionId(JsonNode items, long inspectionId) {
		for (JsonNode item : items) {
			if (item.get("id").longValue() == inspectionId) {
				return item;
			}
		}
		throw new AssertionError("质量确认记录未返回: " + inspectionId);
	}

	private long inspectionSourceId(long inspectionId) {
		return this.jdbcTemplate.queryForObject(
				"select source_id from qua_quality_inspection where id = ?", Long.class, inspectionId);
	}

	private JsonNode allocationByBatchId(JsonNode allocations, long batchId) {
		for (JsonNode allocation : allocations) {
			if (allocation.hasNonNull("batchId") && allocation.get("batchId").longValue() == batchId) {
				return allocation;
			}
		}
		throw new AssertionError("质量确认追踪分配缺少批次: " + batchId);
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertDecimal(new BigDecimal(node.get(field).asText()), expected);
	}

	private void assertDecimal(BigDecimal actual, String expected) {
		assertThat(actual.compareTo(new BigDecimal(expected))).isZero();
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

	private record QualityFixture(long unitId, long warehouseId, long materialId) {
	}

}
