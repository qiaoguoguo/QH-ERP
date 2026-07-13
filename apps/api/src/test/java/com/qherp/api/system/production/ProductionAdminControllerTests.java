package com.qherp.api.system.production;

import com.qherp.api.support.PostgresIntegrationTest;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=production-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductionAdminControllerTests extends PostgresIntegrationTest {

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
	void adminCanRunProductionExecutionLifecycle() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "30.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "20.000000");

		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "5.000000");

		ResponseEntity<String> released = releaseWorkOrder(admin, workOrderId);
		assertOk(released);
		JsonNode releasedData = data(released);
		assertThat(releasedData.get("status").asText()).isEqualTo("RELEASED");
		assertThat(releasedData.get("materials").size()).isEqualTo(2);
		JsonNode rawRequirement = workOrderMaterial(releasedData, fixture.rawMaterialId());
		JsonNode auxiliaryRequirement = workOrderMaterial(releasedData, fixture.auxiliaryMaterialId());
		assertDecimal(rawRequirement, "requiredQuantity", "10.000000");
		assertDecimal(auxiliaryRequirement, "requiredQuantity", "5.000000");

		long issueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("生产领料",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"10.000000"),
						materialIssueLine(2, auxiliaryRequirement.get("id").longValue(), fixture.issueWarehouseId(),
								"5.000000"))));
		ResponseEntity<String> postedIssue = postMaterialIssue(admin, workOrderId, issueId);
		assertOk(postedIssue);
		JsonNode postedIssueData = data(postedIssue);
		JsonNode postedRawIssueLine = materialIssueLine(postedIssueData, fixture.rawMaterialId());
		assertDecimal(postedRawIssueLine, "beforeQuantity", "30.000000");
		assertDecimal(postedRawIssueLine, "afterQuantity", "20.000000");
		assertThat(movementCountBySource("PRODUCTION_MATERIAL_ISSUE", postedRawIssueLine.get("id").longValue()))
			.isOne();
		assertDecimal(balanceQuantity(fixture.issueWarehouseId(), fixture.rawMaterialId()), "20.000000");
		JsonNode issueMovement = firstItem(get("/api/admin/inventory/movements?movementType=PRODUCTION_ISSUE"
				+ "&sourceType=PRODUCTION_MATERIAL_ISSUE&materialId=" + fixture.rawMaterialId(), admin));
		assertThat(issueMovement.get("movementType").asText()).isEqualTo("PRODUCTION_ISSUE");
		assertThat(issueMovement.get("sourceType").asText()).isEqualTo("PRODUCTION_MATERIAL_ISSUE");
		assertDecimal(issueMovement, "quantity", "10.000000");

		long reportId = createReportId(admin, workOrderId, workReportPayload("5.000000", "0.000000"));
		assertOk(postReport(admin, workOrderId, reportId));
		JsonNode reported = data(getWorkOrder(admin, workOrderId));
		assertDecimal(reported, "reportedQuantity", "5.000000");
		assertDecimal(reported, "qualifiedQuantity", "5.000000");

		long receiptId = createCompletionReceiptId(admin, workOrderId,
				completionReceiptPayload(fixture.receiptWarehouseId(), "5.000000"));
		ResponseEntity<String> postedReceipt = postCompletionReceipt(admin, workOrderId, receiptId);
		assertOk(postedReceipt);
		JsonNode postedReceiptData = data(postedReceipt);
		assertDecimal(postedReceiptData, "beforeQuantity", "0");
		assertDecimal(postedReceiptData, "afterQuantity", "5.000000");
		assertThat(movementCountBySource("PRODUCTION_COMPLETION_RECEIPT", receiptId)).isOne();
		assertDecimal(balanceQuantity(fixture.receiptWarehouseId(), fixture.productMaterialId()), "5.000000");
		JsonNode receiptMovement = firstItem(get("/api/admin/inventory/movements?movementType=PRODUCTION_RECEIPT"
				+ "&sourceType=PRODUCTION_COMPLETION_RECEIPT&materialId=" + fixture.productMaterialId(), admin));
		assertThat(receiptMovement.get("movementType").asText()).isEqualTo("PRODUCTION_RECEIPT");
		assertThat(receiptMovement.get("sourceType").asText()).isEqualTo("PRODUCTION_COMPLETION_RECEIPT");
		assertDecimal(receiptMovement, "quantity", "5.000000");
		assertThat(movementQualityStatus("PRODUCTION_COMPLETION_RECEIPT", receiptId))
			.isEqualTo(InventoryQualityStatus.PENDING_INSPECTION.name());
		assertDecimal(balanceQuantity(fixture.receiptWarehouseId(), fixture.productMaterialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "5.000000");
		assertThat(qualityInspectionCount("PRODUCTION_COMPLETION", receiptId, receiptId, "PENDING")).isOne();

		ResponseEntity<String> completed = completeWorkOrder(admin, workOrderId);
		assertOk(completed);
		assertThat(data(completed).get("status").asText()).isEqualTo("COMPLETED");
	}

	@Test
	void completionReceiptBatchAllocationCreatesFinishedGoodsTrackingFacts() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		setTrackingMethod(fixture.productMaterialId(), "BATCH");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "10.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "5.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "2.000000");
		long reportId = createReportId(admin, workOrderId, workReportPayload("2.000000", "0.000000"));
		assertOk(postReport(admin, workOrderId, reportId));

		Map<String, Object> receiptPayload = completionReceiptPayload(fixture.receiptWarehouseId(), "2.000000");
		receiptPayload.put("trackingAllocations",
				List.of(trackingAllocation("MFG-BATCH-" + SEQUENCE.incrementAndGet(), "2.000000")));
		long receiptId = createCompletionReceiptId(admin, workOrderId, receiptPayload);
		JsonNode draftReceipt = data(getCompletionReceipt(admin, workOrderId, receiptId));
		assertThat(draftReceipt.get("trackingMethod").asText()).isEqualTo("BATCH");
		assertThat(draftReceipt.get("trackingMethodName").asText()).isEqualTo("批次管理");
		assertThat(draftReceipt.get("trackingAllocations").size()).isOne();
		JsonNode allocation = draftReceipt.get("trackingAllocations").get(0);
		assertThat(allocation.get("allocationId").isNumber()).isTrue();
		assertThat(allocation.get("trackingMethod").asText()).isEqualTo("BATCH");
		assertThat(allocation.get("documentType").asText()).isEqualTo("PRODUCTION_COMPLETION_RECEIPT");
		assertThat(allocation.get("documentId").longValue()).isEqualTo(receiptId);
		assertThat(allocation.get("documentLineId").longValue()).isEqualTo(receiptId);
		assertThat(allocation.get("sourceDocumentNo").asText()).isEqualTo(draftReceipt.get("receiptNo").asText());

		assertOk(postCompletionReceipt(admin, workOrderId, receiptId));
		long batchId = batchId(fixture.productMaterialId(),
				draftReceipt.get("trackingAllocations").get(0).get("batchNo").asText());

		assertDecimal(trackingBalanceQuantity(fixture.receiptWarehouseId(), fixture.productMaterialId(),
				InventoryQualityStatus.PENDING_INSPECTION, batchId, null), "2.000000");
		assertThat(movementCountBySource("PRODUCTION_COMPLETION_RECEIPT", receiptId)).isOne();
		assertThat(trackingAllocationMovementCount("PRODUCTION_COMPLETION_RECEIPT", receiptId, receiptId)).isOne();
		assertThat(batchSourceId(batchId)).isEqualTo(receiptId);
		assertThat(batchSourceWorkOrderId(batchId)).isEqualTo(workOrderId);
		JsonNode receiptMovement = movementBySource(data(getWorkOrder(admin, workOrderId)).get("movements"),
				"PRODUCTION_COMPLETION_RECEIPT");
		assertThat(receiptMovement.get("batchId").longValue()).isEqualTo(batchId);
		assertThat(receiptMovement.get("batchNo").asText()).isEqualTo(draftReceipt.get("trackingAllocations").get(0).get("batchNo").asText());
	}

	@Test
	void completionReceiptSerialAllocationsCreateFinishedGoodsSerialFacts() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		setTrackingMethod(fixture.productMaterialId(), "SERIAL");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "10.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "5.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "2.000000");
		long reportId = createReportId(admin, workOrderId, workReportPayload("2.000000", "0.000000"));
		assertOk(postReport(admin, workOrderId, reportId));

		Map<String, Object> receiptPayload = completionReceiptPayload(fixture.receiptWarehouseId(), "2.000000");
		receiptPayload.put("trackingAllocations",
				List.of(serialAllocation("MFG-SN-A-" + SEQUENCE.incrementAndGet(), "1.000000"),
						serialAllocation("MFG-SN-B-" + SEQUENCE.incrementAndGet(), "1.000000")));
		long receiptId = createCompletionReceiptId(admin, workOrderId, receiptPayload);

		assertOk(postCompletionReceipt(admin, workOrderId, receiptId));

		assertThat(serialCountByCompletion(receiptId)).isEqualTo(2);
		assertThat(movementCountBySource("PRODUCTION_COMPLETION_RECEIPT", receiptId)).isEqualTo(2);
		assertThat(trackingAllocationMovementCount("PRODUCTION_COMPLETION_RECEIPT", receiptId, receiptId)).isEqualTo(2);
		assertDecimal(balanceQuantity(fixture.receiptWarehouseId(), fixture.productMaterialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "2.000000");
		assertThat(serialSourceWorkOrderIds(receiptId)).containsOnly(workOrderId);
		JsonNode receiptMovement = movementBySource(data(getWorkOrder(admin, workOrderId)).get("movements"),
				"PRODUCTION_COMPLETION_RECEIPT");
		assertThat(receiptMovement.get("serialId").isNumber()).isTrue();
		assertThat(receiptMovement.get("serialNo").asText()).startsWith("MFG-SN-");
	}

	@Test
	void completionReceiptSerialDraftUpdateCanReusePreviousCancelledSerial() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		setTrackingMethod(fixture.productMaterialId(), "SERIAL");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "10.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "5.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "1.000000");
		long reportId = createReportId(admin, workOrderId, workReportPayload("1.000000", "0.000000"));
		assertOk(postReport(admin, workOrderId, reportId));
		String firstSerialNo = "MFG-SN-REUSE-A-" + SEQUENCE.incrementAndGet();
		String secondSerialNo = "MFG-SN-REUSE-B-" + SEQUENCE.incrementAndGet();

		Map<String, Object> firstPayload = completionReceiptPayload(fixture.receiptWarehouseId(), "1.000000");
		firstPayload.put("trackingAllocations", List.of(serialAllocation(firstSerialNo, "1.000000")));
		long receiptId = createCompletionReceiptId(admin, workOrderId, firstPayload);

		Map<String, Object> secondPayload = completionReceiptPayload(fixture.receiptWarehouseId(), "1.000000");
		secondPayload.put("trackingAllocations", List.of(serialAllocation(secondSerialNo, "1.000000")));
		assertOk(updateCompletionReceipt(admin, workOrderId, receiptId, secondPayload));

		Map<String, Object> reusedPayload = completionReceiptPayload(fixture.receiptWarehouseId(), "1.000000");
		reusedPayload.put("trackingAllocations", List.of(serialAllocation(firstSerialNo, "1.000000")));
		ResponseEntity<String> reused = updateCompletionReceipt(admin, workOrderId, receiptId, reusedPayload);

		assertOk(reused);
		assertThat(data(reused).get("trackingAllocations").get(0).get("serialNo").asText()).isEqualTo(firstSerialNo);
	}

	@Test
	void workOrderReleaseReservesMaterialsAndIssueConsumesReservation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "30.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "10.000000");

		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "5.000000");
		JsonNode released = data(releaseWorkOrder(admin, workOrderId));
		JsonNode rawRequirement = workOrderMaterial(released, fixture.rawMaterialId());

		JsonNode reservedBalance = firstItem(get("/api/admin/inventory/balances?warehouseId="
				+ fixture.issueWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin));
		assertDecimal(reservedBalance, "reservedQuantity", "10.000000");
		assertDecimal(reservedBalance, "occupiedQuantity", "0.000000");
		assertDecimal(reservedBalance, "availableQuantity", "20.000000");
		assertDecimal(rawRequirement, "reservedQuantity", "0.000000");
		assertDecimal(rawRequirement, "occupiedQuantity", "0.000000");
		assertDecimal(rawRequirement, "availableToPromiseQuantity", "30.000000");
		JsonNode reservation = firstReservationForSourceLine(get(
				"/api/admin/inventory/reservations?reservationType=RESERVATION&sourceType=PRODUCTION_WORK_ORDER&sourceId="
						+ workOrderId,
				admin), rawRequirement.get("id").longValue());
		assertDecimal(reservation, "remainingQuantity", "10.000000");

		long issueId = createMaterialIssueId(admin, workOrderId,
				materialIssuePayload("018 生产领料预留消耗",
						List.of(materialIssueLine(1, rawRequirement.get("id").longValue(),
								fixture.issueWarehouseId(), "4.000000"))));
		assertOk(postMaterialIssue(admin, workOrderId, issueId));

		JsonNode consumedBalance = firstItem(get("/api/admin/inventory/balances?warehouseId="
				+ fixture.issueWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin));
		assertDecimal(consumedBalance, "bookQuantity", "26.000000");
		assertDecimal(consumedBalance, "reservedQuantity", "6.000000");
		assertDecimal(consumedBalance, "occupiedQuantity", "0.000000");
		assertDecimal(consumedBalance, "availableQuantity", "20.000000");
	}

	@Test
	void workOrderMaterialsAggregateTrackedBalancesWithoutDuplicatingRequirementLine() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		setTrackingMethod(fixture.rawMaterialId(), "BATCH");
		seedBatchStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(),
				"MFG-AGG-A-" + SEQUENCE.incrementAndGet(), "2.000000");
		seedBatchStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(),
				"MFG-AGG-B-" + SEQUENCE.incrementAndGet(), "3.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "5.000000");

		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "1.000000");
		JsonNode released = data(releaseWorkOrder(admin, workOrderId));
		int rawLineCount = 0;
		for (JsonNode material : released.get("materials")) {
			if (material.get("materialId").longValue() == fixture.rawMaterialId()) {
				rawLineCount++;
			}
		}

		assertThat(rawLineCount).isOne();
		JsonNode rawRequirement = workOrderMaterial(released, fixture.rawMaterialId());
		assertDecimal(rawRequirement, "quantityOnHand", "5.000000");
		assertDecimal(rawRequirement, "availableQuantity", "5.000000");
	}

	@Test
	void materialIssueBatchAllocationPostsTrackingMovementAndConsumesReservation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		setTrackingMethod(fixture.rawMaterialId(), "BATCH");
		TrackedBatch batch = seedBatchStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(),
				"MFG-ISS-BATCH-" + SEQUENCE.incrementAndGet(), "10.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "5.000000");

		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "2.000000");
		JsonNode released = data(releaseWorkOrder(admin, workOrderId));
		JsonNode rawRequirement = workOrderMaterial(released, fixture.rawMaterialId());

		Map<String, Object> line = materialIssueLine(1, rawRequirement.get("id").longValue(),
				fixture.issueWarehouseId(), "4.000000");
		line.put("trackingAllocations", List.of(batchAllocation(batch.batchId(), "4.000000")));
		long issueId = createMaterialIssueId(admin, workOrderId,
				materialIssuePayload("批次生产领料", List.of(line)));

		ResponseEntity<String> posted = postMaterialIssue(admin, workOrderId, issueId);
		assertOk(posted);
		JsonNode postedLine = materialIssueLine(data(posted), fixture.rawMaterialId());
		long issueLineId = postedLine.get("id").longValue();

		assertThat(postedLine.get("trackingAllocations").get(0).get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(trackingBalanceQuantity(fixture.issueWarehouseId(), fixture.rawMaterialId(),
				InventoryQualityStatus.QUALIFIED, batch.batchId(), null), "6.000000");
		assertThat(trackingAllocationMovementCount("PRODUCTION_MATERIAL_ISSUE", issueId, issueLineId)).isOne();
		assertThat(trackedMovementCount("PRODUCTION_MATERIAL_ISSUE", issueLineId, batch.batchId())).isOne();
		assertDecimal(productionReservationRemainingForLine(rawRequirement.get("id").longValue()), "0.000000");
	}

	@Test
	void workOrderReleaseRejectsWhenIssueWarehouseNetAvailableIsInsufficient() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "2.000000");

		assertError(releaseWorkOrder(admin, workOrderId), HttpStatus.CONFLICT, "INVENTORY_AVAILABLE_NOT_ENOUGH");
		assertThat(data(getWorkOrder(admin, workOrderId)).get("status").asText()).isEqualTo("DRAFT");
		assertDecimal(productionReservationRemaining(workOrderId), "0");
	}

	@Test
	void materialIssueRejectsWarehouseDifferentFromWorkOrderIssueWarehouse() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		long otherWarehouseId = createWarehouse(admin, "MFG_OTHER_" + SEQUENCE.incrementAndGet(), "生产其他仓");
		seedStock(otherWarehouseId, fixture.rawMaterialId(), fixture.unitId(), "10.000000",
				InventoryQualityStatus.QUALIFIED);
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "2.000000");
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());
		long issueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("跨仓领料拒绝",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), otherWarehouseId,
						"1.000000"))));

		assertError(postMaterialIssue(admin, workOrderId, issueId), HttpStatus.CONFLICT,
				"PRODUCTION_ISSUE_WAREHOUSE_MISMATCH");
		assertDecimal(productionReservationRemaining(workOrderId), "6.000000");
	}

	@Test
	void workOrderCancellationAndCompletionReleaseRemainingReservations() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture cancelFixture = fixture(admin);
		createOpeningStock(admin, cancelFixture.issueWarehouseId(), cancelFixture.rawMaterialId(), "10.000000");
		long cancelWorkOrderId = createAndReleaseWorkOrder(admin, cancelFixture, "2.000000");
		assertDecimal(productionReservationRemaining(cancelWorkOrderId), "6.000000");
		assertOk(cancelWorkOrder(admin, cancelWorkOrderId));
		assertDecimal(productionReservationRemaining(cancelWorkOrderId), "0");

		ProductionFixture completeFixture = fixture(admin);
		createOpeningStock(admin, completeFixture.issueWarehouseId(), completeFixture.rawMaterialId(), "10.000000");
		long completeWorkOrderId = createWorkOrder(admin, completeFixture.productMaterialId(), completeFixture.bomId(),
				completeFixture.issueWarehouseId(), completeFixture.receiptWarehouseId(), "2.000000");
		createOpeningStock(admin, completeFixture.issueWarehouseId(), completeFixture.auxiliaryMaterialId(),
				"2.000000");
		assertOk(releaseWorkOrder(admin, completeWorkOrderId));
		assertDecimal(productionReservationRemaining(completeWorkOrderId), "6.000000");
		this.jdbcTemplate.update("""
				update mfg_work_order
				set reported_quantity = planned_quantity,
				    qualified_quantity = planned_quantity,
				    received_quantity = planned_quantity,
				    updated_at = now()
				where id = ?
				""", completeWorkOrderId);
		assertOk(completeWorkOrder(admin, completeWorkOrderId));
		assertDecimal(productionReservationRemaining(completeWorkOrderId), "0");
	}

	@Test
	void materialIssueRejectsWhenOnlyNonQualifiedStockCanCoverRequestedQuantity() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		ensureReleaseStock(fixture, "2.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "2.000000");
		seedStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(), "1.000000",
				InventoryQualityStatus.QUALIFIED);
		seedStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(), "3.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());
		long issueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("合格库存不足领料",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"4.000000"))));

		assertError(postMaterialIssue(admin, workOrderId, issueId), HttpStatus.CONFLICT,
				"INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH");
		assertDecimal(balanceQuantity(fixture.issueWarehouseId(), fixture.rawMaterialId(),
				InventoryQualityStatus.QUALIFIED), "1.000000");
		assertDecimal(balanceQuantity(fixture.issueWarehouseId(), fixture.rawMaterialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "3.000000");
		assertThat(productionIssueMovementCount(fixture.rawMaterialId())).isZero();
	}

	@Test
	void workOrderMaterialsExposeQualifiedIssueCandidateFieldsWhenOnlyPendingStockExists() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		ensureReleaseStock(fixture, "2.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "2.000000");
		seedStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(), "0.000000",
				InventoryQualityStatus.QUALIFIED);
		seedStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(), "4.000000",
				InventoryQualityStatus.PENDING_INSPECTION);

		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());

		assertThat(rawRequirement.get("qualityStatus").asText()).isEqualTo(InventoryQualityStatus.QUALIFIED.name());
		assertThat(rawRequirement.get("qualityStatusName").asText()).isEqualTo("合格");
		assertDecimal(rawRequirement, "quantityOnHand", "0.000000");
		assertDecimal(rawRequirement, "availableQuantity", "0.000000");
		assertThat(rawRequirement.get("selectable").booleanValue()).isFalse();
		assertThat(rawRequirement.get("disabledReasonCode").asText()).isEqualTo("QUALIFIED_BALANCE_NOT_ENOUGH");
		assertThat(rawRequirement.get("disabledReason").asText()).isEqualTo("合格可用库存不足");
		assertDecimal(rawRequirement, "maxSelectableQuantity", "0.000000");
	}

	@Test
	void lockedPeriodRejectsProductionMaterialIssuePosting() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "5.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "1.000000");
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());
		LocalDate date = LocalDate.of(2093, 7, 10);
		Map<String, Object> payload = new LinkedHashMap<>(materialIssuePayload("期间锁定领料",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"1.000000"))));
		payload.put("businessDate", date.toString());
		long issueId = createMaterialIssueId(admin, workOrderId, payload);
		lockPeriod(date);
		assertError(postMaterialIssue(admin, workOrderId, issueId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	@Test
	void createWorkOrderRejectsFutureAndHistoricalBomEffectiveDates() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		ProductionFixture futureFixture = fixture(admin);
		LocalDate plannedStart = LocalDate.now();
		setBomEffectiveRange(futureFixture.bomId(), plannedStart.plusDays(1), null);
		assertError(createWorkOrder(admin,
				workOrderPayload(futureFixture.productMaterialId(), futureFixture.bomId(),
						futureFixture.issueWarehouseId(), futureFixture.receiptWarehouseId(), "1.000000",
						plannedStart, plannedStart.plusDays(1))),
				HttpStatus.CONFLICT, "PRODUCTION_BOM_EFFECTIVE_DATE_INVALID");

		ProductionFixture historicalFixture = fixture(admin);
		setBomEffectiveRange(historicalFixture.bomId(), plannedStart.minusDays(10), plannedStart.minusDays(1));
		assertError(createWorkOrder(admin,
				workOrderPayload(historicalFixture.productMaterialId(), historicalFixture.bomId(),
						historicalFixture.issueWarehouseId(), historicalFixture.receiptWarehouseId(), "1.000000",
						plannedStart, plannedStart.plusDays(1))),
				HttpStatus.CONFLICT, "PRODUCTION_BOM_EFFECTIVE_DATE_INVALID");
	}

	@Test
	void createWorkOrderAllowsBomEffectiveDateBoundariesAndOpenRanges() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate plannedStart = LocalDate.of(2094, 7, 13);

		ProductionFixture startBoundaryFixture = fixture(admin);
		setBomEffectiveRange(startBoundaryFixture.bomId(), plannedStart, plannedStart.plusDays(10));
		assertOk(createWorkOrder(admin,
				workOrderPayload(startBoundaryFixture.productMaterialId(), startBoundaryFixture.bomId(),
						startBoundaryFixture.issueWarehouseId(), startBoundaryFixture.receiptWarehouseId(),
						"1.000000", plannedStart, plannedStart.plusDays(1))));

		ProductionFixture endBoundaryFixture = fixture(admin);
		setBomEffectiveRange(endBoundaryFixture.bomId(), plannedStart.minusDays(10), plannedStart);
		assertOk(createWorkOrder(admin,
				workOrderPayload(endBoundaryFixture.productMaterialId(), endBoundaryFixture.bomId(),
						endBoundaryFixture.issueWarehouseId(), endBoundaryFixture.receiptWarehouseId(), "1.000000",
						plannedStart, plannedStart.plusDays(1))));

		ProductionFixture openStartFixture = fixture(admin);
		setBomEffectiveRange(openStartFixture.bomId(), null, plannedStart);
		assertOk(createWorkOrder(admin,
				workOrderPayload(openStartFixture.productMaterialId(), openStartFixture.bomId(),
						openStartFixture.issueWarehouseId(), openStartFixture.receiptWarehouseId(), "1.000000",
						plannedStart, plannedStart.plusDays(1))));

		ProductionFixture openEndFixture = fixture(admin);
		setBomEffectiveRange(openEndFixture.bomId(), plannedStart, null);
		assertOk(createWorkOrder(admin,
				workOrderPayload(openEndFixture.productMaterialId(), openEndFixture.bomId(),
						openEndFixture.issueWarehouseId(), openEndFixture.receiptWarehouseId(), "1.000000",
						plannedStart, plannedStart.plusDays(1))));
	}

	@Test
	void updateDraftWorkOrderRejectsBomOutsideChangedPlannedStartDate() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		LocalDate firstStart = LocalDate.of(2095, 1, 10);
		setBomEffectiveRange(fixture.bomId(), firstStart.minusDays(1), firstStart.plusDays(1));
		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "1.000000", firstStart,
				firstStart.plusDays(1));

		LocalDate changedStart = firstStart.plusDays(5);
		assertError(updateWorkOrder(admin, workOrderId,
				workOrderPayload(fixture.productMaterialId(), fixture.bomId(), fixture.issueWarehouseId(),
						fixture.receiptWarehouseId(), "1.000000", changedStart, changedStart.plusDays(1))),
				HttpStatus.CONFLICT, "PRODUCTION_BOM_EFFECTIVE_DATE_INVALID");
	}

	@Test
	void releaseWorkOrderRevalidatesBomEffectiveDateBeforeMaterialSnapshot() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		LocalDate plannedStart = LocalDate.of(2096, 3, 12);
		setBomEffectiveRange(fixture.bomId(), plannedStart.minusDays(1), plannedStart.plusDays(1));
		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "1.000000", plannedStart,
				plannedStart.plusDays(1));
		ensureReleaseStock(fixture, "1.000000");

		setBomEffectiveRange(fixture.bomId(), plannedStart.minusDays(10), plannedStart.minusDays(1));

		assertError(releaseWorkOrder(admin, workOrderId), HttpStatus.CONFLICT,
				"PRODUCTION_BOM_EFFECTIVE_DATE_INVALID");
		assertThat(workOrderMaterialCount(workOrderId)).isZero();
	}

	@Test
	void businessRulesReturnControlledProductionErrors() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		ProductionFixture invalidBomFixture = fixture(admin);
		long invalidBomWorkOrderId = createWorkOrder(admin, invalidBomFixture.productMaterialId(),
				invalidBomFixture.bomId(), invalidBomFixture.issueWarehouseId(),
				invalidBomFixture.receiptWarehouseId(), "5.000000");
		assertOk(exchange(HttpMethod.PUT, "/api/admin/boms/" + invalidBomFixture.bomId() + "/disable",
				Map.of("version", bomVersion(admin, invalidBomFixture.bomId())), admin));
		assertError(releaseWorkOrder(admin, invalidBomWorkOrderId), HttpStatus.BAD_REQUEST, "PRODUCTION_BOM_INVALID");

		ProductionFixture stockFixture = fixture(admin);
		createOpeningStock(admin, stockFixture.issueWarehouseId(), stockFixture.rawMaterialId(), "3.000000");
		long stockWorkOrderId = createAndReleaseWorkOrder(admin, stockFixture, "5.000000");
		seedStock(stockFixture.issueWarehouseId(), stockFixture.rawMaterialId(), stockFixture.unitId(), "3.000000",
				InventoryQualityStatus.QUALIFIED);
		JsonNode stockRawRequirement = workOrderMaterial(data(getWorkOrder(admin, stockWorkOrderId)),
				stockFixture.rawMaterialId());
		long stockIssueId = createMaterialIssueId(admin, stockWorkOrderId, materialIssuePayload("库存不足领料",
				List.of(materialIssueLine(1, stockRawRequirement.get("id").longValue(),
						stockFixture.issueWarehouseId(), "5.000000"))));
		BigDecimal stockBefore = balanceQuantity(stockFixture.issueWarehouseId(), stockFixture.rawMaterialId());
		long stockMovementCountBefore = productionIssueMovementCount(stockFixture.rawMaterialId());
		assertError(postMaterialIssue(admin, stockWorkOrderId, stockIssueId), HttpStatus.CONFLICT,
				"PRODUCTION_STOCK_NOT_ENOUGH");
		assertDecimal(balanceQuantity(stockFixture.issueWarehouseId(), stockFixture.rawMaterialId()),
				stockBefore.toPlainString());
		assertThat(productionIssueMovementCount(stockFixture.rawMaterialId())).isEqualTo(stockMovementCountBefore);

		ProductionFixture overIssueFixture = fixture(admin);
		createOpeningStock(admin, overIssueFixture.issueWarehouseId(), overIssueFixture.rawMaterialId(), "30.000000");
		long overIssueWorkOrderId = createAndReleaseWorkOrder(admin, overIssueFixture, "5.000000");
		JsonNode overIssueRawRequirement = workOrderMaterial(data(getWorkOrder(admin, overIssueWorkOrderId)),
				overIssueFixture.rawMaterialId());
		assertError(createMaterialIssue(admin, overIssueWorkOrderId, materialIssuePayload("超领",
				List.of(materialIssueLine(1, overIssueRawRequirement.get("id").longValue(),
						overIssueFixture.issueWarehouseId(), "10.000001")))), HttpStatus.CONFLICT,
				"PRODUCTION_ISSUE_EXCEEDS_REQUIRED");

		ProductionFixture overReportFixture = fixture(admin);
		long overReportWorkOrderId = createAndReleaseWorkOrder(admin, overReportFixture, "5.000000");
		assertError(createReport(admin, overReportWorkOrderId, workReportPayload("5.000001", "0.000000")),
				HttpStatus.CONFLICT, "PRODUCTION_REPORT_EXCEEDS_PLAN");

		ProductionFixture overReceiptFixture = fixture(admin);
		long overReceiptWorkOrderId = createAndReleaseWorkOrder(admin, overReceiptFixture, "5.000000");
		long partialReportId = createReportId(admin, overReceiptWorkOrderId, workReportPayload("2.000000", "0.000000"));
		assertOk(postReport(admin, overReceiptWorkOrderId, partialReportId));
		assertError(createCompletionReceipt(admin, overReceiptWorkOrderId,
				completionReceiptPayload(overReceiptFixture.receiptWarehouseId(), "2.000001")), HttpStatus.CONFLICT,
				"PRODUCTION_RECEIPT_EXCEEDS_REPORTED");

		ProductionFixture postedFixture = fixture(admin);
		createOpeningStock(admin, postedFixture.issueWarehouseId(), postedFixture.rawMaterialId(), "30.000000");
		long postedWorkOrderId = createAndReleaseWorkOrder(admin, postedFixture, "5.000000");
		JsonNode postedRawRequirement = workOrderMaterial(data(getWorkOrder(admin, postedWorkOrderId)),
				postedFixture.rawMaterialId());
		long postedIssueId = createMaterialIssueId(admin, postedWorkOrderId, materialIssuePayload("已过账领料",
				List.of(materialIssueLine(1, postedRawRequirement.get("id").longValue(),
						postedFixture.issueWarehouseId(), "1.000000"))));
		assertOk(postMaterialIssue(admin, postedWorkOrderId, postedIssueId));
		assertError(updateMaterialIssue(admin, postedWorkOrderId, postedIssueId, materialIssuePayload("已过账修改",
				List.of(materialIssueLine(1, postedRawRequirement.get("id").longValue(),
						postedFixture.issueWarehouseId(), "1.000000")))), HttpStatus.CONFLICT,
				"PRODUCTION_DOCUMENT_POSTED_IMMUTABLE");
		assertProductionDuplicatePost(postMaterialIssue(admin, postedWorkOrderId, postedIssueId));
		assertError(cancelWorkOrder(admin, postedWorkOrderId), HttpStatus.CONFLICT,
				"PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS");

		ProductionFixture draftIssueFixture = fixture(admin);
		long draftIssueWorkOrderId = createAndReleaseWorkOrder(admin, draftIssueFixture, "5.000000");
		JsonNode draftIssueRawRequirement = workOrderMaterial(data(getWorkOrder(admin, draftIssueWorkOrderId)),
				draftIssueFixture.rawMaterialId());
		createMaterialIssueId(admin, draftIssueWorkOrderId, materialIssuePayload("草稿领料",
				List.of(materialIssueLine(1, draftIssueRawRequirement.get("id").longValue(),
						draftIssueFixture.issueWarehouseId(), "1.000000"))));
		assertError(cancelWorkOrder(admin, draftIssueWorkOrderId), HttpStatus.CONFLICT,
				"PRODUCTION_WORK_ORDER_STATUS_INVALID");

		ProductionFixture draftReportFixture = fixture(admin);
		long draftReportWorkOrderId = createAndReleaseWorkOrder(admin, draftReportFixture, "5.000000");
		createReportId(admin, draftReportWorkOrderId, workReportPayload("1.000000", "0.000000"));
		assertError(cancelWorkOrder(admin, draftReportWorkOrderId), HttpStatus.CONFLICT,
				"PRODUCTION_WORK_ORDER_STATUS_INVALID");

		ProductionFixture draftReceiptFixture = fixture(admin);
		long draftReceiptWorkOrderId = createAndReleaseWorkOrder(admin, draftReceiptFixture, "5.000000");
		setQualifiedQuantityForDraftReceiptCheck(draftReceiptWorkOrderId, "1.000000");
		createCompletionReceiptId(admin, draftReceiptWorkOrderId,
				completionReceiptPayload(draftReceiptFixture.receiptWarehouseId(), "1.000000"));
		assertError(cancelWorkOrder(admin, draftReceiptWorkOrderId), HttpStatus.CONFLICT,
				"PRODUCTION_WORK_ORDER_STATUS_INVALID");
	}

	@Test
	void authorizationRulesProtectProductionExecution() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "30.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());

		AuthenticatedSession noProduction = createProductionUserAndLogin("prod_none", "prod_none_role", List.of());
		assertForbidden(get("/api/admin/production/work-orders", noProduction));

		AuthenticatedSession readOnly = createProductionUserAndLogin("prod_read", "prod_read_role", List.of(
				"production:work-order:view", "production:issue:view", "production:report:view",
				"production:receipt:view"));
		assertOk(get("/api/admin/production/work-orders", readOnly));
		assertOk(getWorkOrder(readOnly, workOrderId));
		assertForbidden(createWorkOrder(readOnly, workOrderPayload(fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "1.000000")));
		assertForbidden(releaseWorkOrder(readOnly, workOrderId));
		assertForbidden(createMaterialIssue(readOnly, workOrderId, materialIssuePayload("只读领料",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"1.000000")))));
		assertForbidden(createReport(readOnly, workOrderId, workReportPayload("1.000000", "0.000000")));
		assertForbidden(createCompletionReceipt(readOnly, workOrderId,
				completionReceiptPayload(fixture.receiptWarehouseId(), "1.000000")));

		AuthenticatedSession warehouseUser = createProductionUserAndLogin("prod_wh", "prod_wh_role", List.of(
				"production:work-order:view", "production:issue:view", "production:issue:create",
				"production:issue:update", "production:issue:post", "production:receipt:view",
				"production:receipt:create", "production:receipt:update", "production:receipt:post"));
		long warehouseDraftWorkOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "1.000000");
		assertForbidden(releaseWorkOrder(warehouseUser, warehouseDraftWorkOrderId));
		long warehouseIssueId = createMaterialIssueId(warehouseUser, workOrderId, materialIssuePayload("仓库领料",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"1.000000"))));
		assertOk(postMaterialIssue(warehouseUser, workOrderId, warehouseIssueId));
		long adminReportId = createReportId(admin, workOrderId, workReportPayload("1.000000", "0.000000"));
		assertOk(postReport(admin, workOrderId, adminReportId));
		long warehouseReceiptId = createCompletionReceiptId(warehouseUser, workOrderId,
				completionReceiptPayload(fixture.receiptWarehouseId(), "1.000000"));
		assertOk(postCompletionReceipt(warehouseUser, workOrderId, warehouseReceiptId));

		ProductionFixture productionFixture = fixture(admin);
		createOpeningStock(admin, productionFixture.issueWarehouseId(), productionFixture.rawMaterialId(), "10.000000");
		long productionWorkOrderId = createAndReleaseWorkOrder(admin, productionFixture, "2.000000");
		JsonNode productionRawRequirement = workOrderMaterial(data(getWorkOrder(admin, productionWorkOrderId)),
				productionFixture.rawMaterialId());
		AuthenticatedSession productionUser = createProductionUserAndLogin("prod_user", "prod_user_role", List.of(
				"production:work-order:view", "production:report:view", "production:report:create",
				"production:report:update", "production:report:post"));
		long productionReportId = createReportId(productionUser, productionWorkOrderId,
				workReportPayload("1.000000", "0.000000"));
		assertOk(postReport(productionUser, productionWorkOrderId, productionReportId));
		long draftIssueId = createMaterialIssueId(admin, productionWorkOrderId, materialIssuePayload("生产角色禁过账领料",
				List.of(materialIssueLine(1, productionRawRequirement.get("id").longValue(),
						productionFixture.issueWarehouseId(), "1.000000"))));
		assertForbidden(postMaterialIssue(productionUser, productionWorkOrderId, draftIssueId));
		long draftReceiptId = createCompletionReceiptId(admin, productionWorkOrderId,
				completionReceiptPayload(productionFixture.receiptWarehouseId(), "1.000000"));
		assertForbidden(postCompletionReceipt(productionUser, productionWorkOrderId, draftReceiptId));

		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/production/work-orders",
				workOrderPayload(fixture.productMaterialId(), fixture.bomId(), fixture.issueWarehouseId(),
						fixture.receiptWarehouseId(), "1.000000"), admin));
	}

	@Test
	void concurrentMaterialIssuePostingKeepsStockNonNegative() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession secondAdminSession = login("admin", ADMIN_PASSWORD);
		ProductionFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "5.000000");
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), "5.000000");
		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), "5.000000");
		assertError(releaseWorkOrder(admin, workOrderId), HttpStatus.CONFLICT, "INVENTORY_AVAILABLE_NOT_ENOUGH");
		seedStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(), "10.000000",
				InventoryQualityStatus.QUALIFIED);
		assertOk(releaseWorkOrder(admin, workOrderId));
		seedStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(), "5.000000",
				InventoryQualityStatus.QUALIFIED);
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());
		long firstIssueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("并发领料一",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"4.000000"))));
		long secondIssueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("并发领料二",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"4.000000"))));
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		try {
			Future<ResponseEntity<String>> first = executorService.submit(
					() -> postMaterialIssueAfterStart(workOrderId, firstIssueId, admin, start));
			Future<ResponseEntity<String>> second = executorService.submit(
					() -> postMaterialIssueAfterStart(workOrderId, secondIssueId, secondAdminSession, start));
			start.countDown();

			ResponseEntity<String> firstResponse = response(first);
			ResponseEntity<String> secondResponse = response(second);
			List<String> codes = List.of(code(firstResponse), code(secondResponse));
			assertThat(codes).contains("OK");
			assertThat(codes).contains("PRODUCTION_STOCK_NOT_ENOUGH");

			BigDecimal finalBalance = balanceQuantity(fixture.issueWarehouseId(), fixture.rawMaterialId());
			assertThat(finalBalance.compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0);
			assertDecimal(finalBalance, "1.000000");
			BigDecimal issuedQuantity = productionIssueMovementQuantity(fixture.rawMaterialId());
			assertDecimal(issuedQuantity, "4.000000");
			assertDecimal(new BigDecimal("5.000000").subtract(issuedQuantity), finalBalance.toPlainString());
		}
		finally {
			executorService.shutdownNow();
		}
	}

	private ProductionFixture fixture(AuthenticatedSession admin) throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		String suffix = "P" + sequence;
		long unitId = createUnit(admin, "MFG_UNIT_" + suffix, "生产单位" + suffix);
		long issueWarehouseId = createWarehouse(admin, "MFG_ISSUE_" + suffix, "生产领料仓" + suffix);
		long receiptWarehouseId = createWarehouse(admin, "MFG_RECEIPT_" + suffix, "生产入库仓" + suffix);
		long categoryId = createCategory(admin, "MFG_CAT_" + suffix, "生产分类" + suffix);
		long productMaterialId = createMaterial(admin, "MFG_PRODUCT_" + suffix, "生产成品" + suffix,
				"FINISHED_GOOD", "SELF_MADE", categoryId, unitId, "ENABLED");
		long rawMaterialId = createMaterial(admin, "MFG_RAW_" + suffix, "生产原料" + suffix, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId, "ENABLED");
		long auxiliaryMaterialId = createMaterial(admin, "MFG_AUX_" + suffix, "生产辅料" + suffix, "AUXILIARY",
				"PURCHASED", categoryId, unitId, "ENABLED");
		long bomId = createBom(admin, productMaterialId, unitId, rawMaterialId, auxiliaryMaterialId);
		return new ProductionFixture(unitId, issueWarehouseId, receiptWarehouseId, categoryId, productMaterialId,
				rawMaterialId, auxiliaryMaterialId, bomId);
	}

	private long createUnit(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", code, "name", name, "precisionScale", 6, "sortOrder", 10, "status", "ENABLED"),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createWarehouse(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/warehouses",
				Map.of("code", code, "name", name, "warehouseType", "NORMAL", "sortOrder", 10, "status", "ENABLED"),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createCategory(AuthenticatedSession admin, String code, String name) throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/material-categories",
				Map.of("code", code, "name", name, "sortOrder", 10, "status", "ENABLED"), admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createMaterial(AuthenticatedSession admin, String code, String name, String materialType,
			String sourceType, long categoryId, long unitId, String status) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", code);
		body.put("name", name);
		body.put("specification", "测试规格");
		body.put("materialType", materialType);
		body.put("sourceType", sourceType);
		body.put("categoryId", categoryId);
		body.put("unitId", unitId);
		body.put("status", status);
		body.put("safeStock", "0");
		body.put("sortOrder", 10);
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/materials", body, admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createBom(AuthenticatedSession admin, long productId, long unitId, long rawMaterialId,
			long auxiliaryId) throws Exception {
		JsonNode bom = createDraftBom(admin, productId, unitId, rawMaterialId, auxiliaryId);
		long bomId = bom.get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/boms/" + bomId + "/enable",
				Map.of("version", bom.get("version").longValue()), admin));
		return bomId;
	}

	private JsonNode createDraftBom(AuthenticatedSession admin, long productId, long unitId, long rawMaterialId,
			long auxiliaryId) throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("bomCode", "BOM_PROD_" + sequence);
		body.put("parentMaterialId", productId);
		body.put("versionCode", "V" + sequence);
		body.put("name", "生产 BOM " + sequence);
		body.put("baseQuantity", "1.000000");
		body.put("baseUnitId", unitId);
		body.put("effectiveFrom", LocalDate.now().toString());
		body.put("items", List.of(bomItem(1, rawMaterialId, unitId, "2.000000"),
				bomItem(2, auxiliaryId, unitId, "1.000000")));
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/boms", body, admin);
		assertOk(response);
		return data(response);
	}

	private long bomVersion(AuthenticatedSession admin, long bomId) throws Exception {
		return data(exchange(HttpMethod.GET, "/api/admin/boms/" + bomId, null, admin)).get("version").longValue();
	}

	private void setBomEffectiveRange(long bomId, LocalDate effectiveFrom, LocalDate effectiveTo) {
		this.jdbcTemplate.update("""
				update mfg_bom
				set effective_from = ?, effective_to = ?, updated_at = now(), version = version + 1
				where id = ?
				""", effectiveFrom, effectiveTo, bomId);
	}

	private Map<String, Object> bomItem(int lineNo, long materialId, long unitId, String quantity) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("lineNo", lineNo);
		item.put("childMaterialId", materialId);
		item.put("unitId", unitId);
		item.put("quantity", quantity);
		item.put("lossRate", "0.000000");
		return item;
	}

	private long createOpeningStock(AuthenticatedSession admin, long warehouseId, long materialId, String quantity)
			throws Exception {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("warehouseId", warehouseId);
		line.put("materialId", materialId);
		line.put("quantity", quantity);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("documentType", "OPENING");
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", "生产测试期初");
		body.put("remark", "生产执行集成测试");
		body.put("lines", List.of(line));
		ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/admin/inventory/documents", body, admin);
		assertOk(created);
		long documentId = data(created).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + documentId + "/post", null, admin));
		return documentId;
	}

	private void seedStock(long warehouseId, long materialId, long unitId, String quantity,
			InventoryQualityStatus qualityStatus) {
		int updated = this.jdbcTemplate.update("""
				update inv_stock_balance
				set unit_id = ?, quantity_on_hand = ?, updated_at = now(), version = version + 1
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is null
				and serial_id is null
				""", unitId, new BigDecimal(quantity), warehouseId, materialId, qualityStatus.name());
		if (updated == 0) {
			this.jdbcTemplate.update("""
					insert into inv_stock_balance (
						warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at,
						quality_status
					)
					values (?, ?, ?, ?, 0, now(), now(), ?)
					""", warehouseId, materialId, unitId, new BigDecimal(quantity), qualityStatus.name());
		}
	}

	private void ensureReleaseStock(ProductionFixture fixture, String plannedQuantity) {
		BigDecimal planned = new BigDecimal(plannedQuantity);
		ensureQualifiedStock(fixture.issueWarehouseId(), fixture.rawMaterialId(), fixture.unitId(),
				planned.multiply(new BigDecimal("2.000000")));
		ensureQualifiedStock(fixture.issueWarehouseId(), fixture.auxiliaryMaterialId(), fixture.unitId(), planned);
	}

	private void ensureQualifiedStock(long warehouseId, long materialId, long unitId, BigDecimal requiredQuantity) {
		BigDecimal currentQuantity = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				""", BigDecimal.class, warehouseId, materialId);
		if (currentQuantity != null && currentQuantity.compareTo(requiredQuantity) >= 0) {
			return;
		}
		seedStock(warehouseId, materialId, unitId, requiredQuantity.setScale(6).toPlainString(),
				InventoryQualityStatus.QUALIFIED);
	}

	private long createWorkOrder(AuthenticatedSession admin, long productId, long bomId, long issueWarehouseId,
			long receiptWarehouseId, String plannedQuantity) throws Exception {
		ResponseEntity<String> response = createWorkOrder(admin,
				workOrderPayload(productId, bomId, issueWarehouseId, receiptWarehouseId, plannedQuantity));
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createWorkOrder(AuthenticatedSession admin, long productId, long bomId, long issueWarehouseId,
			long receiptWarehouseId, String plannedQuantity, LocalDate plannedStartDate, LocalDate plannedFinishDate)
			throws Exception {
		ResponseEntity<String> response = createWorkOrder(admin,
				workOrderPayload(productId, bomId, issueWarehouseId, receiptWarehouseId, plannedQuantity,
						plannedStartDate, plannedFinishDate));
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private ResponseEntity<String> createWorkOrder(AuthenticatedSession session, Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/production/work-orders", body, session);
	}

	private ResponseEntity<String> updateWorkOrder(AuthenticatedSession session, long workOrderId,
			Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/production/work-orders/" + workOrderId, body, session);
	}

	private Map<String, Object> workOrderPayload(long productId, long bomId, long issueWarehouseId,
			long receiptWarehouseId, String plannedQuantity) {
		return workOrderPayload(productId, bomId, issueWarehouseId, receiptWarehouseId, plannedQuantity,
				LocalDate.now(), LocalDate.now().plusDays(1));
	}

	private Map<String, Object> workOrderPayload(long productId, long bomId, long issueWarehouseId,
			long receiptWarehouseId, String plannedQuantity, LocalDate plannedStartDate,
			LocalDate plannedFinishDate) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("productMaterialId", productId);
		body.put("bomId", bomId);
		body.put("plannedQuantity", plannedQuantity);
		body.put("issueWarehouseId", issueWarehouseId);
		body.put("receiptWarehouseId", receiptWarehouseId);
		body.put("plannedStartDate", plannedStartDate.toString());
		body.put("plannedFinishDate", plannedFinishDate.toString());
		body.put("remark", "生产执行测试工单");
		return body;
	}

	private long createAndReleaseWorkOrder(AuthenticatedSession admin, ProductionFixture fixture, String plannedQuantity)
			throws Exception {
		ensureReleaseStock(fixture, plannedQuantity);
		long workOrderId = createWorkOrder(admin, fixture.productMaterialId(), fixture.bomId(),
				fixture.issueWarehouseId(), fixture.receiptWarehouseId(), plannedQuantity);
		assertOk(releaseWorkOrder(admin, workOrderId));
		return workOrderId;
	}

	private ResponseEntity<String> getWorkOrder(AuthenticatedSession session, long workOrderId) {
		return get("/api/admin/production/work-orders/" + workOrderId, session);
	}

	private ResponseEntity<String> releaseWorkOrder(AuthenticatedSession session, long workOrderId) {
		return exchange(HttpMethod.PUT, "/api/admin/production/work-orders/" + workOrderId + "/release", null,
				session);
	}

	private ResponseEntity<String> completeWorkOrder(AuthenticatedSession session, long workOrderId) {
		return exchange(HttpMethod.PUT, "/api/admin/production/work-orders/" + workOrderId + "/complete", null,
				session);
	}

	private ResponseEntity<String> cancelWorkOrder(AuthenticatedSession session, long workOrderId) {
		return exchange(HttpMethod.PUT, "/api/admin/production/work-orders/" + workOrderId + "/cancel", null,
				session);
	}

	private ResponseEntity<String> createMaterialIssue(AuthenticatedSession session, long workOrderId,
			Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/production/work-orders/" + workOrderId + "/material-issues",
				body, session);
	}

	private long createMaterialIssueId(AuthenticatedSession session, long workOrderId, Map<String, Object> body)
			throws Exception {
		ResponseEntity<String> response = createMaterialIssue(session, workOrderId, body);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private ResponseEntity<String> updateMaterialIssue(AuthenticatedSession session, long workOrderId, long issueId,
			Map<String, Object> body) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/material-issues/" + issueId, body, session);
	}

	private ResponseEntity<String> postMaterialIssue(AuthenticatedSession session, long workOrderId, long issueId) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/material-issues/" + issueId + "/post", null,
				session);
	}

	private ResponseEntity<String> createReport(AuthenticatedSession session, long workOrderId,
			Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/production/work-orders/" + workOrderId + "/reports", body,
				session);
	}

	private long createReportId(AuthenticatedSession session, long workOrderId, Map<String, Object> body)
			throws Exception {
		ResponseEntity<String> response = createReport(session, workOrderId, body);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private ResponseEntity<String> postReport(AuthenticatedSession session, long workOrderId, long reportId) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/reports/" + reportId + "/post", null, session);
	}

	private ResponseEntity<String> createCompletionReceipt(AuthenticatedSession session, long workOrderId,
			Map<String, Object> body) {
		return exchange(HttpMethod.POST,
				"/api/admin/production/work-orders/" + workOrderId + "/completion-receipts", body, session);
	}

	private long createCompletionReceiptId(AuthenticatedSession session, long workOrderId, Map<String, Object> body)
			throws Exception {
		ResponseEntity<String> response = createCompletionReceipt(session, workOrderId, body);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private ResponseEntity<String> postCompletionReceipt(AuthenticatedSession session, long workOrderId,
			long receiptId) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/completion-receipts/" + receiptId + "/post",
				null, session);
	}

	private ResponseEntity<String> updateCompletionReceipt(AuthenticatedSession session, long workOrderId,
			long receiptId, Map<String, Object> body) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/completion-receipts/" + receiptId, body,
				session);
	}

	private ResponseEntity<String> getCompletionReceipt(AuthenticatedSession session, long workOrderId,
			long receiptId) {
		return get("/api/admin/production/work-orders/" + workOrderId + "/completion-receipts/" + receiptId,
				session);
	}

	private Map<String, Object> materialIssuePayload(String reason, List<Map<String, Object>> lines) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", reason);
		body.put("remark", reason + "备注");
		body.put("lines", lines);
		return body;
	}

	private Map<String, Object> materialIssueLine(int lineNo, long workOrderMaterialId, long warehouseId,
			String quantity) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("workOrderMaterialId", workOrderMaterialId);
		line.put("warehouseId", warehouseId);
		line.put("quantity", quantity);
		line.put("remark", "生产领料行");
		return line;
	}

	private Map<String, Object> workReportPayload(String qualifiedQuantity, String defectiveQuantity) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("qualifiedQuantity", qualifiedQuantity);
		body.put("defectiveQuantity", defectiveQuantity);
		body.put("reporterName", "测试报工员");
		body.put("remark", "生产报工测试");
		return body;
	}

	private Map<String, Object> completionReceiptPayload(long receiptWarehouseId, String quantity) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("receiptWarehouseId", receiptWarehouseId);
		body.put("quantity", quantity);
		body.put("remark", "完工入库测试");
		return body;
	}

	private Map<String, Object> trackingAllocation(String batchNo, String quantity) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("batchNo", batchNo);
		allocation.put("quantity", quantity);
		return allocation;
	}

	private Map<String, Object> serialAllocation(String serialNo, String quantity) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("serialNo", serialNo);
		allocation.put("quantity", quantity);
		return allocation;
	}

	private Map<String, Object> batchAllocation(long batchId, String quantity) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("batchId", batchId);
		allocation.put("quantity", quantity);
		return allocation;
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at) values (?, ?, ?, ?, 'LOCKED', now(), now())",
				"LOCK-PROD-" + date, "锁定期间", date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
	}

	private JsonNode workOrderMaterial(JsonNode workOrder, long materialId) {
		JsonNode materials = workOrder.get("materials");
		for (int i = 0; i < materials.size(); i++) {
			JsonNode material = materials.get(i);
			if (material.get("materialId").longValue() == materialId) {
				return material;
			}
		}
		throw new AssertionError("未找到工单用料：" + materialId);
	}

	private long workOrderMaterialCount(long workOrderId) {
		Long count = this.jdbcTemplate.queryForObject(
				"select count(*) from mfg_work_order_material where work_order_id = ?", Long.class, workOrderId);
		return count == null ? 0L : count;
	}

	private JsonNode materialIssueLine(JsonNode issue, long materialId) {
		JsonNode lines = issue.get("lines");
		for (int i = 0; i < lines.size(); i++) {
			JsonNode line = lines.get(i);
			if (line.get("materialId").longValue() == materialId) {
				return line;
			}
		}
		throw new AssertionError("未找到生产领料行：" + materialId);
	}

	private BigDecimal balanceQuantity(long warehouseId, long materialId) throws Exception {
		ResponseEntity<String> response = get(
				"/api/admin/inventory/balances?warehouseId=" + warehouseId + "&materialId=" + materialId,
				login("admin", ADMIN_PASSWORD));
		assertOk(response);
		JsonNode items = data(response).get("items");
		if (items.size() == 0) {
			return BigDecimal.ZERO;
		}
		return decimal(items.get(0), "quantityOnHand");
	}

	private BigDecimal balanceQuantity(long warehouseId, long materialId, InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				""", BigDecimal.class, warehouseId, materialId, qualityStatus.name());
	}

	private BigDecimal trackingBalanceQuantity(long warehouseId, long materialId,
			InventoryQualityStatus qualityStatus, Long batchId, Long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				""", BigDecimal.class, warehouseId, materialId, qualityStatus.name(), batchId, serialId);
	}

	private void setTrackingMethod(long materialId, String trackingMethod) {
		this.jdbcTemplate.update("update mst_material set tracking_method = ?, updated_at = now() where id = ?",
				trackingMethod, materialId);
	}

	private TrackedBatch seedBatchStock(long warehouseId, long materialId, long unitId, String batchNo,
			String quantity) {
		long batchId = this.jdbcTemplate.queryForObject("""
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'TEST', ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, materialId, batchNo, 7_200_000L + SEQUENCE.incrementAndGet(),
				7_210_000L + SEQUENCE.incrementAndGet(), LocalDate.now());
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, quantity_on_hand, locked_quantity,
					batch_id, created_at, updated_at
				)
				values (?, ?, ?, 'QUALIFIED', ?, 0, ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity), batchId);
		return new TrackedBatch(batchId, batchNo);
	}

	private long batchId(long materialId, String batchNo) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from inv_batch
				where material_id = ?
				and batch_no = ?
				""", Long.class, materialId, batchNo);
	}

	private long batchSourceId(long batchId) {
		return this.jdbcTemplate.queryForObject("select source_id from inv_batch where id = ?", Long.class, batchId);
	}

	private long batchSourceWorkOrderId(long batchId) {
		return this.jdbcTemplate.queryForObject("""
				select r.work_order_id
				from inv_batch b
				join mfg_completion_receipt r on r.id = b.source_id
				where b.id = ?
				and b.source_type = 'PRODUCTION_COMPLETION_RECEIPT'
				""", Long.class, batchId);
	}

	private List<Long> serialSourceWorkOrderIds(long receiptId) {
		return this.jdbcTemplate.query("""
				select distinct r.work_order_id
				from inv_serial s
				join mfg_completion_receipt r on r.id = s.source_id
				where s.source_type = 'PRODUCTION_COMPLETION_RECEIPT'
				and s.source_id = ?
				order by r.work_order_id
				""", (rs, rowNum) -> rs.getLong("work_order_id"), receiptId);
	}

	private JsonNode movementBySource(JsonNode movements, String sourceType) {
		for (JsonNode movement : movements) {
			if (sourceType.equals(movement.get("sourceType").asText())) {
				return movement;
			}
		}
		throw new AssertionError("未找到生产流水：" + sourceType);
	}

	private String movementQualityStatus(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select quality_status
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", String.class, sourceType, sourceLineId);
	}

	private BigDecimal productionReservationRemaining(long workOrderId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity - released_quantity - consumed_quantity), 0)
				from inv_stock_reservation
				where reservation_type = 'RESERVATION'
				and source_type = 'PRODUCTION_WORK_ORDER'
				and source_id = ?
				and status = 'ACTIVE'
				""", BigDecimal.class, workOrderId);
	}

	private BigDecimal productionReservationRemainingForLine(long workOrderMaterialId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity - released_quantity - consumed_quantity), 0)
				from inv_stock_reservation
				where reservation_type = 'RESERVATION'
				and source_type = 'PRODUCTION_WORK_ORDER'
				and source_line_id = ?
				and status = 'ACTIVE'
				""", BigDecimal.class, workOrderMaterialId);
	}

	private long qualityInspectionCount(String sourceType, long sourceId, long sourceLineId, String status) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from qua_quality_inspection
				where source_type = ?
				and source_id = ?
				and source_line_id = ?
				and status = ?
				""", Long.class, sourceType, sourceId, sourceLineId, status);
	}

	private long productionIssueMovementCount(long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where movement_type = 'PRODUCTION_ISSUE'
				and source_type = 'PRODUCTION_MATERIAL_ISSUE'
				and material_id = ?
				""", Long.class, materialId);
	}

	private long movementCountBySource(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", Long.class, sourceType, sourceLineId);
	}

	private long trackingAllocationMovementCount(String documentType, long documentId, long documentLineId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_tracking_allocation
				where document_type = ?
				and document_id = ?
				and document_line_id = ?
				and movement_id is not null
				""", Long.class, documentType, documentId, documentLineId);
	}

	private long trackedMovementCount(String sourceType, long sourceLineId, long batchId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				and batch_id = ?
				""", Long.class, sourceType, sourceLineId, batchId);
	}

	private long serialCountByCompletion(long receiptId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_serial
				where source_type = 'PRODUCTION_COMPLETION_RECEIPT'
				and source_id = ?
				and source_line_id = ?
				and stock_status = 'IN_STOCK'
				""", Long.class, receiptId, receiptId);
	}

	private BigDecimal productionIssueMovementQuantity(long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity), 0)
				from inv_stock_movement
				where movement_type = 'PRODUCTION_ISSUE'
				and source_type = 'PRODUCTION_MATERIAL_ISSUE'
				and material_id = ?
				""", BigDecimal.class, materialId);
	}

	private void setQualifiedQuantityForDraftReceiptCheck(long workOrderId, String quantity) {
		this.jdbcTemplate.update("""
				update mfg_work_order
				set qualified_quantity = ?,
				    reported_quantity = ?,
				    updated_at = now()
				where id = ?
				""", new BigDecimal(quantity), new BigDecimal(quantity), workOrderId);
	}

	private AuthenticatedSession createProductionUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int sequence = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + "_" + sequence;
		String roleCode = rolePrefix + "_" + sequence;
		String password = "Qherp@2026!";
		Long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, roleCode, "生产测试角色" + sequence, "生产测试角色" + sequence);
		Long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, display_name, password_hash, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, "生产测试用户" + sequence, this.passwordEncoder.encode(password));
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
		return login(username, password);
	}

	private ResponseEntity<String> postMaterialIssueAfterStart(long workOrderId, long issueId,
			AuthenticatedSession session, CountDownLatch start) throws InterruptedException {
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		return postMaterialIssue(session, workOrderId, issueId);
	}

	private ResponseEntity<String> response(Future<ResponseEntity<String>> future)
			throws InterruptedException, ExecutionException, TimeoutException {
		return future.get(10, TimeUnit.SECONDS);
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

	private ResponseEntity<String> exchangeWithoutCsrf(HttpMethod method, String path, Object body,
			AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), null), String.class);
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

	private JsonNode firstReservationForSourceLine(ResponseEntity<String> response, long sourceLineId) throws Exception {
		JsonNode items = data(response).get("items");
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			if (item.get("sourceLineId").longValue() == sourceLineId) {
				return item;
			}
		}
		throw new AssertionError("未找到来源行预留：" + sourceLineId);
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

	private void assertForbidden(ResponseEntity<String> response) throws Exception {
		assertError(response, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	private void assertProductionDuplicatePost(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(code(response)).isIn("PRODUCTION_DUPLICATE_POST", "PRODUCTION_MOVEMENT_SOURCE_DUPLICATED");
	}

	private BigDecimal decimal(JsonNode node, String field) {
		return new BigDecimal(node.get(field).asText());
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertDecimal(decimal(node, field), expected);
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

	private record ProductionFixture(long unitId, long issueWarehouseId, long receiptWarehouseId, long categoryId,
			long productMaterialId, long rawMaterialId, long auxiliaryMaterialId, long bomId) {
	}

	private record TrackedBatch(long batchId, String batchNo) {
	}

}
