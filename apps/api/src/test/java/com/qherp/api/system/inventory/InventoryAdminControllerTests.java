package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.platform.PlatformAttachmentService;
import com.qherp.api.system.procurement.ProcurementAdminService;
import com.qherp.api.system.production.ProductionAdminService;
import com.qherp.api.system.quality.QualityAdminService;
import com.qherp.api.system.reversal.ReversalAdminService;
import com.qherp.api.system.sales.SalesAdminService;
import com.qherp.api.system.user.SystemUserStatus;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=task7-inventory-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryAdminControllerTests extends PostgresIntegrationTest {

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
	private InventoryStage023AdminService stage023AdminService;

	@Autowired
	private InventoryAvailabilityService inventoryAvailabilityService;

	@Autowired
	private ProcurementAdminService procurementAdminService;

	@Autowired
	private ProductionAdminService productionAdminService;

	@Autowired
	private QualityAdminService qualityAdminService;

	@Autowired
	private ReversalAdminService reversalAdminService;

	@Autowired
	private SalesAdminService salesAdminService;

	@Autowired
	private PlatformAttachmentService attachmentService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void openingDocumentCreatesUpdatesPostsAndWritesBalanceMovementAndAudit() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		ResponseEntity<String> created = createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), null, "10.000000",
						"期初库存", "创建备注", "创建行备注"));
		assertOk(created);
		long documentId = data(created).get("id").longValue();

		ResponseEntity<String> updated = updateDocument(admin, documentId,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"12.500000", "期初库存更新", "更新备注", "更新行备注"));
		assertOk(updated);
		JsonNode updatedData = data(updated);
		long lineId = updatedData.get("lines").get(0).get("id").longValue();
		assertThat(updatedData.get("status").asText()).isEqualTo("DRAFT");

		ResponseEntity<String> posted = postDocument(admin, documentId);
		assertOk(posted);
		JsonNode postedData = data(posted);
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertDecimal(postedData.get("lines").get(0), "beforeQuantity", "0");
		assertDecimal(postedData.get("lines").get(0), "afterQuantity", "12.500000");

		ResponseEntity<String> balances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin);
		assertOk(balances);
		JsonNode balance = firstItem(balances);
		assertDecimal(balance, "quantityOnHand", "12.500000");
		assertThat(decimal(balance, "availableQuantity").compareTo(decimal(balance, "quantityOnHand"))).isZero();

		ResponseEntity<String> movements = get("/api/admin/inventory/movements?materialId="
				+ fixture.rawMaterialId(), admin);
		assertOk(movements);
		JsonNode movement = firstItem(movements);
		assertThat(movement.get("movementType").asText()).isEqualTo("OPENING");
		assertThat(movement.get("direction").asText()).isEqualTo("IN");
		assertThat(movement.get("sourceLineId").longValue()).isEqualTo(lineId);
		assertDecimal(movement, "beforeQuantity", "0");
		assertDecimal(movement, "quantity", "12.500000");
		assertDecimal(movement, "afterQuantity", "12.500000");
		assertThat(movementCountBySource("INVENTORY_DOCUMENT", lineId)).isOne();

		assertAuditLog("INVENTORY_DOCUMENT_CREATE", documentId);
		assertAuditLog("INVENTORY_DOCUMENT_UPDATE", documentId);
		assertAuditLog("INVENTORY_DOCUMENT_POST", documentId);
	}

	@Test
	void valuedInventoryDocumentMaintainsPublicMovingAverageAndMasksCostWithoutPermission() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());

		JsonNode firstIn = data(createDocument(admin,
				documentPayload("OPENING", "023 公共库存首笔估值入库", null,
						List.of(pricedLine(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
								"100.000000", null, "10.000000")))));
		assertThat(firstIn.get("version")).as("023 库存单据创建响应必须携带 version").isNotNull();
		assertOk(postDocument(admin, firstIn.get("id").longValue(), firstIn.get("version").longValue()));

		JsonNode secondIn = data(createDocument(admin,
				documentPayload("ADJUSTMENT", "023 公共库存第二笔估值入库", null,
						List.of(pricedLine(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
								"50.000000", "INCREASE", "13.000000")))));
		assertOk(postDocument(admin, secondIn.get("id").longValue(), secondIn.get("version").longValue()));

		JsonNode afterInbound = firstItem(get("/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.rawMaterialId() + "&onlyPositive=true", admin));
		assertThat(afterInbound.get("costVisible").booleanValue()).isTrue();
		assertThat(afterInbound.get("ownershipType").asText()).isEqualTo("PUBLIC");
		assertThat(afterInbound.get("projectId").isNull()).isTrue();
		assertDecimal(afterInbound, "quantityOnHand", "150.000000");
		assertDecimal(afterInbound, "inventoryAmount", "1650.00");
		assertDecimal(afterInbound, "averageUnitCost", "11.000000");
		assertThat(afterInbound.get("costLayerCount").longValue()).isZero();

		AuthenticatedSession costHidden = createInventoryUserAndLogin("inventory-cost-hidden-", "INV_COST_HIDDEN_",
				"库存成本脱敏", List.of("inventory:balance:view", "inventory:movement:view"));
		JsonNode hiddenBalance = firstItem(get("/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.rawMaterialId() + "&onlyPositive=true", costHidden));
		assertThat(hiddenBalance.get("costVisible").booleanValue()).isFalse();
		assertThat(hiddenBalance.get("inventoryAmount").isNull()).isTrue();
		assertThat(hiddenBalance.get("averageUnitCost").isNull()).isTrue();

		JsonNode out = data(createDocument(admin,
				documentPayload("ADJUSTMENT", "023 公共库存估值出库", null,
						List.of(pricedLine(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
								"80.000000", "DECREASE", null)))));
		assertOk(postDocument(admin, out.get("id").longValue(), out.get("version").longValue()));

		JsonNode afterOutbound = firstItem(get("/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.rawMaterialId() + "&onlyPositive=true", admin));
		assertDecimal(afterOutbound, "quantityOnHand", "70.000000");
		assertDecimal(afterOutbound, "inventoryAmount", "770.00");
		assertDecimal(afterOutbound, "averageUnitCost", "11.000000");
		JsonNode movement = firstItem(get("/api/admin/inventory/movements?materialId=" + fixture.rawMaterialId()
				+ "&movementType=ADJUSTMENT_DECREASE", admin));
		assertThat(movement.get("valuationMethod").asText()).isEqualTo("MOVING_WEIGHTED_AVERAGE");
		assertDecimal(movement, "unitCost", "11.000000");
		assertDecimal(movement, "inventoryAmount", "880.00");
	}

	@Test
	void publicInventoryBalanceAmountsFollowCurrentCompanyMaterialAverageAcrossWarehouses() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());

		JsonNode rawOpening = data(createDocument(admin,
				documentPayload("OPENING", "023 公共池原料仓估值入库", null,
						List.of(pricedLine(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
								"100.000000", null, "10.000000")))));
		assertOk(postDocument(admin, rawOpening.get("id").longValue(), rawOpening.get("version").longValue()));
		JsonNode finishedOpening = data(createDocument(admin,
				documentPayload("OPENING", "023 公共池成品仓估值入库", null,
						List.of(pricedLine(1, fixture.finishedWarehouseId(), fixture.rawMaterialId(),
								fixture.kgUnitId(), "10.000000", null, "10.000000")))));
		assertOk(postDocument(admin, finishedOpening.get("id").longValue(), finishedOpening.get("version").longValue()));

		JsonNode avgChange = data(createDocument(admin,
				documentPayload("ADJUSTMENT", "023 公共池平均价变化", null,
						List.of(pricedLine(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
								"10.000000", "INCREASE", "20.000000")))));
		assertOk(postDocument(admin, avgChange.get("id").longValue(), avgChange.get("version").longValue()));

		JsonNode rawBalance = firstItem(get("/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin));
		JsonNode finishedBalance = firstItem(get("/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId="
				+ fixture.finishedWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin));
		assertDecimal(rawBalance, "averageUnitCost", "10.833333");
		assertDecimal(finishedBalance, "averageUnitCost", "10.833333");
		assertDecimal(finishedBalance, "quantityOnHand", "10.000000");
		assertDecimal(finishedBalance, "inventoryAmount", "108.33");
		assertDecimal(rawBalance, "inventoryAmount", "1191.67");
	}

	@Test
	void publicPoolTailDifferenceOnlyAbsorbsWhenCompanyMaterialPoolIsFullyDepleted() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1.000000",
				"10.000000", InventoryQualityStatus.QUALIFIED, "023 公共池原料仓入库");
		postValuedInventory(fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				"10.000000", InventoryQualityStatus.QUALIFIED, "023 公共池成品仓入库");

		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_DECREASE, InventoryDirection.OUT, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("1.000000"),
				InventoryQualityStatus.QUALIFIED, "INVENTORY_DOCUMENT", 2_800_000L + SEQUENCE.incrementAndGet(),
				2_810_000L + SEQUENCE.incrementAndGet(), LocalDate.now(), "023 跨仓单仓出清", null, "tester"));

		PublicPoolFact pool = publicPool(fixture.rawMaterialId());
		assertDecimal(pool.quantity(), "2.000000");
		assertDecimal(pool.amount(), "20.00");
		assertDecimal(balanceAmount(fixture.rawWarehouseId(), fixture.rawMaterialId(), InventoryQualityStatus.QUALIFIED),
				"0.00");
		assertDecimal(balanceAmount(fixture.finishedWarehouseId(), fixture.rawMaterialId(),
				InventoryQualityStatus.QUALIFIED), "20.00");
	}

	@Test
	void procurementReceiptPostsValuedInventoryWithOrderUntaxedUnitPrice() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		CurrentUser operator = backendOperator();
		long supplierId = insertSupplier("023 估值供应商");

		PurchaseReceiptFact receipt = createPostedPurchaseReceipt(fixture, supplierId, "6.000000", "12.340000",
				"023-采购入库单价");

		ValueMovementFact value = valueMovement("PURCHASE_RECEIPT", receipt.receiptId(), receipt.receiptLineId());
		assertDecimal(value.unitCost(), "12.340000");
		assertDecimal(value.inventoryAmount(), "74.04");
		assertThat(value.valuationMethod()).isEqualTo("SOURCE_UNIT_PRICE");
		PublicPoolFact pool = publicPool(fixture.rawMaterialId());
		assertDecimal(pool.quantity(), "6.000000");
		assertDecimal(pool.amount(), "74.04");
		assertThat(operator.username()).isEqualTo("admin");
	}

	@Test
	void purchaseReturnRestoresOriginalReceiptCostInsteadOfCurrentMovingAverage() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long supplierId = insertSupplier("023 退货供应商");
		PurchaseReceiptFact first = createPostedPurchaseReceipt(fixture, supplierId, "10.000000", "10.000000",
				"023-首笔采购");
		createPostedPurchaseReceipt(fixture, supplierId, "10.000000", "20.000000", "023-拉高均价采购");
		insertConfirmedPayable(first, "100.00");
		CurrentUser operator = backendOperator();

		ReversalAdminService.PurchaseReturnDetailResponse created = this.reversalAdminService.createPurchaseReturn(
				new ReversalAdminService.PurchaseReturnRequest(first.receiptId(), LocalDate.now(),
						"PR-ORIGINAL-" + SEQUENCE.incrementAndGet(), "原价退回",
						List.of(new ReversalAdminService.PurchaseReturnLineRequest(null, first.receiptLineId(),
								new BigDecimal("5.000000"), "退首笔采购", "PENDING_INSPECTION", List.of()))),
				operator, request());
		ReversalAdminService.PurchaseReturnDetailResponse posted = this.reversalAdminService
			.postPurchaseReturn(created.id(), operator, request());

		PublicPoolFact pool = publicPool(fixture.rawMaterialId());
		assertDecimal(pool.quantity(), "15.000000");
		assertDecimal(pool.amount(), "250.00");
		ValueMovementFact returnValue = valueMovement("PURCHASE_RETURN", posted.id(),
				posted.lines().getFirst().id());
		assertDecimal(returnValue.unitCost(), "10.000000");
		assertDecimal(returnValue.inventoryAmount(), "50.00");
		assertThat(returnValue.valuationMethod()).isEqualTo("ORIGINAL_VALUE_REVERSAL");
	}

	@Test
	void productionIssueWritesInventoryOutboundAmountIntoWorkOrderCostRecord() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "10.000000",
				"10.000000", InventoryQualityStatus.QUALIFIED, "023-生产领料前库存");
		ProductionFixture production = productionFixture(fixture);
		CurrentUser operator = backendOperator();

		ProductionAdminService.MaterialIssueDetailResponse issue = this.productionAdminService.createMaterialIssue(
				production.workOrderId(),
				new ProductionAdminService.MaterialIssueRequest(LocalDate.now(), "023 生产领料", "真实库存金额写入成本",
						List.of(new ProductionAdminService.MaterialIssueLineRequest(1,
								production.workOrderMaterialId(), fixture.rawWarehouseId(), new BigDecimal("3.000000"),
								"领用三件", List.of()))),
				operator, request());
		ProductionAdminService.MaterialIssueDetailResponse posted = this.productionAdminService
			.postMaterialIssue(production.workOrderId(), issue.id(), operator, request());

		CostRecordFact cost = materialIssueCostRecord(posted.lines().getFirst().id());
		assertDecimal(cost.unitPrice(), "10.000000");
		assertDecimal(cost.amount(), "30.00");
		assertThat(cost.basisType()).isEqualTo("MANUAL_UNIT_PRICE_QUANTITY");
	}

	@Test
	void productionCompletionReceiptUsesManualProvisionalUnitCostWhenPublicAverageIsMissing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.finishedMaterialId());
		ProductionFixture production = productionFixture(fixture);
		this.jdbcTemplate.update("""
				update mfg_work_order
				set status = 'IN_PROGRESS', reported_quantity = 5.000000, qualified_quantity = 5.000000
				where id = ?
				""", production.workOrderId());

		ResponseEntity<String> createdResponse = exchange(HttpMethod.POST,
				"/api/admin/production/work-orders/" + production.workOrderId() + "/completion-receipts",
				completionReceiptPayload(fixture.finishedWarehouseId(), "5.000000", "9.000000"), admin);
		assertOk(createdResponse);
		JsonNode created = data(createdResponse);
		ResponseEntity<String> postedResponse = exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + production.workOrderId() + "/completion-receipts/"
						+ created.get("id").longValue() + "/post",
				null, admin);
		assertOk(postedResponse);
		JsonNode posted = data(postedResponse);
		assertDecimal(posted, "unitCost", "9.000000");
		assertThat(posted.get("valuationState").asText()).isEqualTo("MANUAL_PROVISIONAL");
		ValueMovementFact value = valueMovement("PRODUCTION_COMPLETION_RECEIPT", posted.get("id").longValue(),
				posted.get("id").longValue());
		assertDecimal(value.unitCost(), "9.000000");
		assertDecimal(value.inventoryAmount(), "45.00");
	}

	@Test
	void salesShipmentPostsMovingAverageOutboundValueThroughSalesService() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.finishedMaterialId());
		postValuedInventory(fixture.finishedWarehouseId(), fixture.finishedMaterialId(), fixture.eachUnitId(), "10.000000",
				"8.000000", InventoryQualityStatus.QUALIFIED, "023-销售出库前库存");
		CurrentUser operator = backendOperator();
		long customerId = insertCustomer("023 估值客户");

		SalesAdminService.SalesOrderDetailResponse order = this.salesAdminService.createOrder(
				new SalesAdminService.SalesOrderRequest(customerId, LocalDate.now(), LocalDate.now().plusDays(3),
						"023 销售估值订单", null, null, null,
						List.of(new SalesAdminService.SalesOrderLineRequest(1, fixture.finishedMaterialId(),
								fixture.eachUnitId(), new BigDecimal("4.000000"), new BigDecimal("20.000000"),
								fixture.finishedWarehouseId(), LocalDate.now().plusDays(3), "销售估值行"))),
				operator, request());
		SalesAdminService.SalesOrderDetailResponse confirmed = this.salesAdminService.confirmOrder(order.id(),
				operator, request());
		SalesAdminService.SalesShipmentDetailResponse shipment = this.salesAdminService.createShipment(confirmed.id(),
				new SalesAdminService.SalesShipmentRequest(fixture.finishedWarehouseId(), LocalDate.now(), "023 销售出库估值",
						List.of(new SalesAdminService.SalesShipmentLineRequest(1,
								confirmed.lines().getFirst().id(), fixture.finishedMaterialId(), fixture.eachUnitId(),
								new BigDecimal("4.000000"), "销售出库估值", List.of()))),
				operator, request());
		SalesAdminService.SalesShipmentDetailResponse posted = this.salesAdminService.postShipment(shipment.id(),
				operator, request());

		ValueMovementFact value = valueMovement("SALES_SHIPMENT", posted.id(), posted.lines().getFirst().id());
		assertDecimal(value.unitCost(), "8.000000");
		assertDecimal(value.inventoryAmount(), "32.00");
		assertThat(value.valuationMethod()).isEqualTo("MOVING_WEIGHTED_AVERAGE");
		PublicPoolFact pool = publicPool(fixture.finishedMaterialId());
		assertDecimal(pool.quantity(), "6.000000");
		assertDecimal(pool.amount(), "48.00");
	}

	@Test
	void qualityFreezePreservesPublicPoolValueAndBalanceAmounts() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "10.000000",
				"7.000000", InventoryQualityStatus.QUALIFIED, "023-质量转换前库存");
		CurrentUser operator = backendOperator();

		this.qualityAdminService.freeze(new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "4.000000", "冻结保值", "冻结保值",
				null, null, null, List.of()), operator, request());

		PublicPoolFact pool = publicPool(fixture.rawMaterialId());
		assertDecimal(pool.quantity(), "10.000000");
		assertDecimal(pool.amount(), "70.00");
		assertDecimal(balanceAmount(fixture.rawWarehouseId(), fixture.rawMaterialId(), InventoryQualityStatus.QUALIFIED),
				"42.00");
		assertDecimal(balanceAmount(fixture.rawWarehouseId(), fixture.rawMaterialId(), InventoryQualityStatus.FROZEN),
				"28.00");
	}

	@Test
	void valuationAdjustmentUsesApprovalAndInventoryAttachmentPermissionBoundary() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession approver = createInventoryUserAndLogin("valuation-approver-", "VAL_APPROVER_",
				"估值审批", List.of("platform:approval:view", "platform:todo:view", "platform:approval:approve",
						"inventory:valuation:view", "inventory:valuation-adjustment:post-approve"));
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		insertLegacyUnvaluedPublicStock(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
				"5.000000");

		ResponseEntity<String> createdResponse = exchange(HttpMethod.POST,
				"/api/admin/inventory/valuation-adjustments",
				valuationAdjustmentPayload(fixture.rawMaterialId(), "5.000000", "11.000000", "55.00"), admin);
		assertOk(createdResponse);
		JsonNode created = data(createdResponse);
		long adjustmentId = created.get("id").longValue();
		assertThat(created.get("status").asText()).isEqualTo("DRAFT");
		assertThatThrownBy(() -> this.attachmentService.list("INVENTORY_VALUATION_ADJUSTMENT", adjustmentId, 1, 20,
				userWithPermissions("inventory:balance:view")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode()).isEqualTo(ApiErrorCode.AUTH_FORBIDDEN));
		assertThat(this.attachmentService.list("INVENTORY_VALUATION_ADJUSTMENT", adjustmentId, 1, 20,
				userWithPermissions("inventory:valuation:view")).total()).isZero();

		ResponseEntity<String> submittedResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/valuation-adjustments/" + adjustmentId + "/submit",
				Map.of("version", created.get("version").longValue(), "reason", "期初估值审批",
						"idempotencyKey", "VAL-SUBMIT-" + adjustmentId),
				admin);
		assertOk(submittedResponse);
		JsonNode submitted = data(submittedResponse);
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		long approvalId = submitted.get("approvalSummary").get("id").longValue();

		JsonNode task = firstItem(get("/api/admin/approval-tasks?scope=TODO", approver));
		assertThat(task.get("id").longValue()).isEqualTo(approvalId);
		ResponseEntity<String> approvedResponse = exchange(HttpMethod.POST,
				"/api/admin/approval-tasks/" + task.get("taskId").longValue() + "/approve",
				Map.of("version", task.get("version").longValue(), "comment", "同意期初估值",
						"idempotencyKey", "VAL-APPROVE-" + adjustmentId),
				approver);
		assertOk(approvedResponse);

		ResponseEntity<String> detailResponse = get("/api/admin/inventory/valuation-adjustments/" + adjustmentId,
				admin);
		assertOk(detailResponse);
		assertThat(data(detailResponse).get("status").asText()).isEqualTo("POSTED");
		PublicPoolFact pool = publicPool(fixture.rawMaterialId());
		assertThat(pool.valuationState()).isEqualTo("VALUED");
		assertDecimal(pool.quantity(), "5.000000");
		assertDecimal(pool.amount(), "55.00");
	}

	@Test
	void provisionalValuationAdjustmentRevaluesPublicPoolAndProjectLayerByDelta() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession approver = createInventoryUserAndLogin("valuation-revalue-approver-", "VAL_REVALUE_",
				"暂估调整审批", List.of("platform:approval:view", "platform:todo:view", "platform:approval:approve",
						"inventory:valuation:view", "inventory:valuation-adjustment:post-approve"));
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "10.000000",
				"8.000000", InventoryQualityStatus.QUALIFIED, "023-暂估调整公共库存");
		long projectId = insertProject("023-暂估项目");
		long layerId = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				layerId, "5.000000", "50.00", "10.000000");

		ResponseEntity<String> createdResponse = exchange(HttpMethod.POST,
				"/api/admin/inventory/valuation-adjustments",
				provisionalRevaluationPayload(fixture.rawMaterialId(), "12.00", projectId, layerId, "20.00"),
				admin);
		assertOk(createdResponse);
		JsonNode created = data(createdResponse);
		long adjustmentId = created.get("id").longValue();
		ResponseEntity<String> submittedResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/valuation-adjustments/" + adjustmentId + "/submit",
				Map.of("version", created.get("version").longValue(), "reason", "暂估差额审批",
						"idempotencyKey", "VAL-REVALUE-SUBMIT-" + adjustmentId),
				admin);
		assertOk(submittedResponse);
		JsonNode task = firstItem(get("/api/admin/approval-tasks?scope=TODO", approver));
		ResponseEntity<String> approvedResponse = exchange(HttpMethod.POST,
				"/api/admin/approval-tasks/" + task.get("taskId").longValue() + "/approve",
				Map.of("version", task.get("version").longValue(), "comment", "同意暂估调整",
						"idempotencyKey", "VAL-REVALUE-APPROVE-" + adjustmentId),
				approver);
		assertOk(approvedResponse);

		PublicPoolFact pool = publicPool(fixture.rawMaterialId());
		assertDecimal(pool.amount(), "92.00");
		assertDecimal(pool.averageUnitCost(), "9.200000");
		assertDecimal(projectLayerRemainingAmount(layerId), "70.00");
		assertThat(valueMovementCount("VALUATION_ADJUSTMENT", adjustmentId, "PROVISIONAL_REVALUATION")).isEqualTo(2);
	}

	@Test
	void warehouseTransferFrontendRoutesUseRealStateVersionAndActions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		Map<String, Object> createdBody = warehouseTransferPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1.000000",
				"023 前端调拨创建");
		ResponseEntity<String> createdResponse = exchange(HttpMethod.POST,
				"/api/admin/inventory/warehouse-transfers", createdBody, admin);
		assertOk(createdResponse);
		JsonNode created = data(createdResponse);
		long transferId = created.get("id").longValue();
		assertThat(created.get("status").asText()).isEqualTo("DRAFT");
		assertAvailableActions(created, "UPDATE", "POST", "CANCEL");

		ResponseEntity<String> listResponse = get("/api/admin/inventory/warehouse-transfers?status=DRAFT", admin);
		assertOk(listResponse);
		JsonNode listed = itemById(listResponse, transferId);
		assertThat(listed.get("documentNo").asText()).isEqualTo(created.get("documentNo").asText());
		assertAvailableActions(listed, "UPDATE");

		Map<String, Object> updatedBody = warehouseTransferPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				"023 前端调拨更新");
		updatedBody.put("version", created.get("version").longValue());
		ResponseEntity<String> updatedResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transferId, updatedBody, admin);
		assertOk(updatedResponse);
		JsonNode updated = data(updatedResponse);
		assertThat(updated.get("reason").asText()).isEqualTo("023 前端调拨更新");
		assertDecimal(updated.get("lines").get(0), "quantity", "2.000000");

		ResponseEntity<String> staleUpdate = exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transferId, updatedBody, admin);
		assertError(staleUpdate, HttpStatus.CONFLICT, "CONFLICT");

		ResponseEntity<String> cancelledResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transferId + "/cancel",
				Map.of("version", updated.get("version").longValue(), "reason", "取消前端调拨",
						"idempotencyKey", "TRF-CANCEL-" + transferId),
				admin);
		assertOk(cancelledResponse);
		JsonNode cancelled = data(cancelledResponse);
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertAvailableActionsEmpty(cancelled);
	}

	@Test
	void ownershipConversionFrontendRoutesSubmitWithdrawCancelAndUseApprovalState() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		long projectId = insertProject("023 所有权转换项目");
		Map<String, Object> createdBody = ownershipConversionPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId, "1.000000",
				"023 所有权转换创建");
		ResponseEntity<String> createdResponse = exchange(HttpMethod.POST,
				"/api/admin/inventory/ownership-conversions", createdBody, admin);
		assertOk(createdResponse);
		JsonNode created = data(createdResponse);
		long conversionId = created.get("id").longValue();
		assertAvailableActions(created, "SUBMIT_APPROVAL");

		ResponseEntity<String> listResponse = get("/api/admin/inventory/ownership-conversions?status=DRAFT", admin);
		assertOk(listResponse);
		assertAvailableActions(itemById(listResponse, conversionId), "UPDATE");
		assertOk(get("/api/admin/inventory/ownership-conversions/" + conversionId, admin));

		Map<String, Object> updatedBody = ownershipConversionPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId, "2.000000",
				"023 所有权转换更新");
		updatedBody.put("version", created.get("version").longValue());
		JsonNode updated = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversionId, updatedBody, admin));
		assertThat(updated.get("reason").asText()).isEqualTo("023 所有权转换更新");
		assertDecimal(updated.get("lines").get(0), "quantity", "2.000000");

		ResponseEntity<String> submittedResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversionId + "/submit-approval",
				Map.of("version", updated.get("version").longValue(), "reason", "提交所有权转换审批",
						"idempotencyKey", "OWN-SUBMIT-" + conversionId),
				admin);
		assertOk(submittedResponse);
		JsonNode submitted = data(submittedResponse);
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(submitted.get("approvalSummary").get("status").asText()).isEqualTo("SUBMITTED");
		assertAvailableActions(submitted, "WITHDRAW");

		ResponseEntity<String> withdrawnResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversionId + "/withdraw",
				Map.of("version", submitted.get("version").longValue(), "reason", "撤回所有权转换审批",
						"idempotencyKey", "OWN-WITHDRAW-" + conversionId),
				admin);
		assertOk(withdrawnResponse);
		JsonNode withdrawn = data(withdrawnResponse);
		assertThat(withdrawn.get("status").asText()).isEqualTo("DRAFT");
		assertThat(withdrawn.get("approvalSummary").get("status").asText()).isEqualTo("WITHDRAWN");

		JsonNode cancelCandidate = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload(fixture.rawWarehouseId(), fixture.finishedWarehouseId(),
						fixture.rawMaterialId(), fixture.kgUnitId(), projectId, "1.000000", "023 所有权转换取消"),
				admin));
		ResponseEntity<String> cancelledResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + cancelCandidate.get("id").longValue() + "/cancel",
				Map.of("version", cancelCandidate.get("version").longValue(), "reason", "取消所有权转换",
						"idempotencyKey", "OWN-CANCEL-" + cancelCandidate.get("id").longValue()),
				admin);
		assertOk(cancelledResponse);
		assertThat(data(cancelledResponse).get("status").asText()).isEqualTo("CANCELLED");
	}

	@Test
	void ownershipConversionUsesActualSourceValueAndProjectTransferPreservesLayer() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession approver = createInventoryUserAndLogin("inventory-own-approver-", "INV_OWN_APPR_",
				"所有权转换审批", List.of("platform:approval:view", "platform:todo:view", "platform:approval:approve",
						"inventory:balance:view", "inventory:ownership-conversion:post-approve"));
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long projectId = insertProject("023 所有权价值项目");
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				"8.000000", InventoryQualityStatus.QUALIFIED, "023 所有权来源公共库存");

		Map<String, Object> conversionBody = ownershipConversionPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId, "1.000000",
				"023 所有权转换不得信任客户端单价");
		@SuppressWarnings("unchecked")
		Map<String, Object> conversionLine = (Map<String, Object>) ((List<?>) conversionBody.get("lines")).get(0);
		conversionLine.put("sourceUnitCost", "99.000000");
		JsonNode createdConversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				conversionBody, admin));
		long conversionId = createdConversion.get("id").longValue();
		long conversionLineId = createdConversion.get("lines").get(0).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/ownership-conversions/" + conversionId
				+ "/submit-approval",
				Map.of("version", createdConversion.get("version").longValue(), "reason", "提交所有权价值审批",
						"idempotencyKey", "OWN-ACTUAL-SUBMIT-" + conversionId),
				admin));
		JsonNode task = firstItem(get("/api/admin/approval-tasks?scope=TODO", approver));
		assertOk(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + task.get("taskId").longValue()
				+ "/approve",
				Map.of("version", task.get("version").longValue(), "comment", "同意所有权价值转换",
						"idempotencyKey", "OWN-ACTUAL-APPROVE-" + conversionId),
				approver));

		ValueMovementFact sourceValue = valueMovement("OWNERSHIP_CONVERSION", conversionId, conversionLineId * 10 + 1);
		ValueMovementFact targetValue = valueMovement("OWNERSHIP_CONVERSION", conversionId, conversionLineId * 10 + 2);
		assertDecimal(sourceValue.inventoryAmount(), "8.00");
		assertDecimal(targetValue.inventoryAmount(), "8.00");
		assertDecimal(publicPool(fixture.rawMaterialId()).amount(), "8.00");

		long transferProjectId = insertProject("023 项目调拨成本层项目");
		long layerId = insertProjectCostLayer(transferProjectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), transferProjectId,
				layerId, "5.000000", "50.00", "10.000000");
		Map<String, Object> transferBody = warehouseTransferPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				"023 项目库存调拨保留成本层");
		@SuppressWarnings("unchecked")
		Map<String, Object> transferLine = (Map<String, Object>) ((List<?>) transferBody.get("lines")).get(0);
		transferLine.put("ownershipType", "PROJECT");
		transferLine.put("projectId", transferProjectId);
		transferLine.put("sourceCostLayerId", layerId);
		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers", transferBody,
				admin));
		ResponseEntity<String> postedTransfer = exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				Map.of("version", transfer.get("version").longValue(), "reason", "过账项目调拨",
						"idempotencyKey", "TRF-PROJECT-POST-" + transfer.get("id").longValue()),
				admin);
		assertOk(postedTransfer);
		Map<String, Object> targetBalance = this.jdbcTemplate.queryForMap("""
				select quantity_on_hand, inventory_amount, cost_layer_id
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and ownership_type = 'PROJECT'
				and project_id = ?
				and quality_status = 'QUALIFIED'
				""", fixture.finishedWarehouseId(), fixture.rawMaterialId(), transferProjectId);
		assertDecimal((BigDecimal) targetBalance.get("quantity_on_hand"), "2.000000");
		assertDecimal((BigDecimal) targetBalance.get("inventory_amount"), "20.00");
		assertThat(targetBalance.get("cost_layer_id")).isEqualTo(layerId);
	}

	@Test
	void projectWarehouseTransferPersistsExplicitCostLayerAndTraceFilters() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long projectId = insertProject("023 调拨显式成本层项目");
		long firstLayerId = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "1.000000", "8.00",
				"8.000000");
		long selectedLayerId = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				selectedLayerId, "5.000000", "50.00", "10.000000");

		Map<String, Object> transferBody = warehouseTransferPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				"023 调拨显式成本层");
		@SuppressWarnings("unchecked")
		Map<String, Object> transferLine = (Map<String, Object>) ((List<?>) transferBody.get("lines")).get(0);
		transferLine.put("ownershipType", "PROJECT");
		transferLine.put("projectId", projectId);
		transferLine.put("sourceCostLayerId", selectedLayerId);
		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers", transferBody,
				admin));
		assertThat(transfer.get("lines").get(0).get("sourceCostLayerId").longValue()).isEqualTo(selectedLayerId);
		assertThat(transfer.get("lines").get(0).get("sourceCostLayerId").longValue()).isNotEqualTo(firstLayerId);

		JsonNode posted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				Map.of("version", transfer.get("version").longValue(), "reason", "过账显式成本层调拨",
						"idempotencyKey", "TRF-EXPLICIT-LAYER-" + transfer.get("id").longValue()),
				admin));
		long transferId = posted.get("id").longValue();
		long lineId = posted.get("lines").get(0).get("id").longValue();
		ValueMovementFact outboundValue = valueMovement("WAREHOUSE_TRANSFER", transferId, lineId * 10 + 1);
		assertDecimal(outboundValue.inventoryAmount(), "20.00");
		Map<String, Object> sourceBalance = projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				projectId, selectedLayerId);
		Map<String, Object> targetBalance = projectBalance(fixture.finishedWarehouseId(), fixture.rawMaterialId(),
				projectId, selectedLayerId);
		assertDecimal((BigDecimal) sourceBalance.get("quantity_on_hand"), "3.000000");
		assertDecimal((BigDecimal) sourceBalance.get("inventory_amount"), "30.00");
		assertDecimal((BigDecimal) targetBalance.get("quantity_on_hand"), "2.000000");
		assertDecimal((BigDecimal) targetBalance.get("inventory_amount"), "20.00");
		assertThat(targetBalance.get("cost_layer_id")).isEqualTo(selectedLayerId);
		assertThat(projectLayerRemainingAmount(selectedLayerId)).isEqualByComparingTo("50.00");

		ResponseEntity<String> costLayers = get("/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&costLayerId=" + selectedLayerId + "&sourceType=STAGE023_TEST&status=ACTIVE", admin);
		assertOk(costLayers);
		assertThat(data(costLayers).get("total").longValue()).isOne();
		assertThat(firstItem(costLayers).get("id").longValue()).isEqualTo(selectedLayerId);

		long sourceId = 3_700_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 3_800_000L + SEQUENCE.incrementAndGet();
		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, fixture.rawWarehouseId(),
				fixture.finishedMaterialId(), fixture.eachUnitId(), new BigDecimal("1.000000"),
				InventoryQualityStatus.QUALIFIED, "MOVEMENT_FILTER_TEST", sourceId, sourceLineId, LocalDate.now(),
				"流水筛选测试", "流水筛选测试", "tester"));
		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, fixture.rawWarehouseId(),
				fixture.finishedMaterialId(), fixture.eachUnitId(), new BigDecimal("1.000000"),
				InventoryQualityStatus.QUALIFIED, "MOVEMENT_FILTER_TEST", sourceId + 1, sourceLineId + 1,
				LocalDate.now(), "流水筛选测试", "流水筛选测试", "tester"));
		ResponseEntity<String> movements = get("/api/admin/inventory/movements?sourceType=MOVEMENT_FILTER_TEST"
				+ "&sourceId=" + sourceId + "&sourceLineId=" + sourceLineId, admin);
		assertOk(movements);
		assertThat(data(movements).get("total").longValue()).isOne();
		JsonNode movement = firstItem(movements);
		assertThat(movement.get("sourceType").asText()).isEqualTo("MOVEMENT_FILTER_TEST");
		assertThat(movement.get("sourceId").longValue()).isEqualTo(sourceId);
		assertThat(movement.get("sourceLineId").longValue()).isEqualTo(sourceLineId);
	}

	@Test
	void 同仓同项目同追踪维度多个项目成本层必须独立余额候选调拨和质量转换() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long projectId = insertProject("023 多成本层独立余额项目");
		long layerA = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		long layerB = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "7.000000", "84.00",
				"12.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				layerA, "5.000000", "50.00", "10.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				layerB, "7.000000", "84.00", "12.000000");

		ResponseEntity<String> balances = get("/api/admin/inventory/balances?ownershipType=PROJECT&projectId="
				+ projectId + "&warehouseId=" + fixture.rawWarehouseId() + "&materialId="
				+ fixture.rawMaterialId() + "&qualityStatus=QUALIFIED&onlyPositive=true&page=1&pageSize=20",
				admin);
		assertOk(balances);
		assertThat(data(balances).get("total").longValue()).isEqualTo(2);
		assertThat(costLayerIds(data(balances).get("items"))).containsExactlyInAnyOrder(layerA, layerB);
		assertThat(this.stage023AdminService.costLayers(null, null, projectId, fixture.rawMaterialId(),
				fixture.rawWarehouseId(), null, null, null, null, "ACTIVE", null, 1, 20, backendOperator()).total())
			.isEqualTo(2);

		ResponseEntity<String> candidates = get("/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&warehouseId=" + fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId()
				+ "&status=ACTIVE&page=1&pageSize=20", admin);
		assertOk(candidates);
		assertThat(data(candidates).get("total").longValue()).isEqualTo(2);
		assertThat(costLayerIds(data(candidates).get("items"))).containsExactlyInAnyOrder(layerA, layerB);

		Map<String, Object> transferBody = warehouseTransferPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				"023 多层项目库存只调拨层A");
		@SuppressWarnings("unchecked")
		Map<String, Object> transferLine = (Map<String, Object>) ((List<?>) transferBody.get("lines")).get(0);
		transferLine.put("ownershipType", "PROJECT");
		transferLine.put("projectId", projectId);
		transferLine.put("sourceCostLayerId", layerA);
		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers", transferBody,
				admin));
		assertOk(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				Map.of("version", transfer.get("version").longValue(), "reason", "过账多层项目库存调拨",
						"idempotencyKey", "TRF-MULTI-LAYER-" + transfer.get("id").longValue()),
				admin));

		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), projectId,
				layerA).get("quantity_on_hand"), "3.000000");
		assertDecimal((BigDecimal) projectBalance(fixture.finishedWarehouseId(), fixture.rawMaterialId(), projectId,
				layerA).get("quantity_on_hand"), "2.000000");
		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), projectId,
				layerB).get("quantity_on_hand"), "7.000000");
		assertDecimal(projectLayerRemainingAmount(layerA), "50.00");
		assertDecimal(projectLayerRemainingAmount(layerB), "84.00");

		this.qualityAdminService.freeze(new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "3.000000", "023 多层项目库存冻结层B",
				null, "PROJECT", projectId, layerB, null), backendOperator(), request());

		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), projectId,
				layerA).get("quantity_on_hand"), "3.000000");
		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), projectId,
				layerB).get("quantity_on_hand"), "4.000000");
		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), projectId,
				layerB, InventoryQualityStatus.FROZEN).get("quantity_on_hand"), "3.000000");
		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), projectId,
				layerB, InventoryQualityStatus.FROZEN).get("inventory_amount"), "36.00");
		assertDecimal(projectLayerRemainingAmount(layerB), "84.00");
	}

	@Test
	void 项目预留占用必须绑定成本层并隔离公共库存和其他项目层() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "4.000000",
				"8.000000", InventoryQualityStatus.QUALIFIED, "023 公共预留库存");
		long projectId = insertProject("023 成本层预留项目");
		long layerA = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		long layerB = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "7.000000", "84.00",
				"12.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				layerA, "5.000000", "50.00", "10.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				layerB, "7.000000", "84.00", "12.000000");

		long publicReservationId = reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "2.000000", "PUBLIC", null, null);
		long layerAReservationId = reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "3.000000", "PROJECT", projectId, layerA);

		assertThat(reservationCostLayerId(publicReservationId)).isNull();
		assertThat(reservationCostLayerId(layerAReservationId)).isEqualTo(layerA);
		assertDecimal(lockedQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), "PUBLIC", null, null),
				"2.000000");
		assertDecimal(lockedQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), "PROJECT", projectId, layerA),
				"3.000000");
		assertDecimal(lockedQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), "PROJECT", projectId, layerB),
				"0.000000");

		JsonNode publicBalance = firstItem(get("/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin));
		assertDecimal(publicBalance, "reservedQuantity", "2.000000");
		assertDecimal(publicBalance, "availableQuantity", "2.000000");
		JsonNode projectBalances = data(get("/api/admin/inventory/balances?ownershipType=PROJECT&projectId="
				+ projectId + "&warehouseId=" + fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId()
				+ "&qualityStatus=QUALIFIED&page=1&pageSize=20", admin)).get("items");
		JsonNode layerABalance = balanceItemByCostLayer(projectBalances, layerA);
		JsonNode layerBBalance = balanceItemByCostLayer(projectBalances, layerB);
		assertDecimal(layerABalance, "reservedQuantity", "3.000000");
		assertDecimal(layerABalance, "availableQuantity", "2.000000");
		assertDecimal(layerBBalance, "reservedQuantity", "0.000000");
		assertDecimal(layerBBalance, "availableQuantity", "7.000000");

		Map<String, Object> layerATransfer = projectWarehouseTransferBody(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId, layerA,
				"3.000000", "023 层A预留后不足调拨");
		JsonNode layerATransferDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				layerATransfer, admin));
		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + layerATransferDraft.get("id").longValue() + "/post",
				Map.of("version", layerATransferDraft.get("version").longValue(), "reason", "层A预留不足",
						"idempotencyKey", "TRF-LAYER-A-RESERVED-" + layerATransferDraft.get("id").longValue()),
				admin), HttpStatus.CONFLICT, "INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE");

		Map<String, Object> layerBTransfer = projectWarehouseTransferBody(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId, layerB,
				"7.000000", "023 层B不受层A预留影响");
		JsonNode layerBTransferDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				layerBTransfer, admin));
		assertOk(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + layerBTransferDraft.get("id").longValue() + "/post",
				Map.of("version", layerBTransferDraft.get("version").longValue(), "reason", "层B全量调拨",
						"idempotencyKey", "TRF-LAYER-B-FREE-" + layerBTransferDraft.get("id").longValue()),
				admin));
		assertDecimal((BigDecimal) projectBalance(fixture.finishedWarehouseId(), fixture.rawMaterialId(), projectId,
				layerB).get("quantity_on_hand"), "7.000000");

		this.inventoryAvailabilityService.consumeBySourceLine(InventoryReservationType.RESERVATION, "STAGE023_TEST",
				reservationSourceLineId(layerAReservationId), new BigDecimal("1.000000"), backendOperator(),
				request());
		assertDecimal(lockedQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), "PROJECT", projectId, layerA),
				"2.000000");
		this.inventoryAvailabilityService.releaseBySourceLine(InventoryReservationType.RESERVATION, "STAGE023_TEST",
				reservationSourceLineId(layerAReservationId), backendOperator(), request());
		assertDecimal(lockedQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), "PROJECT", projectId, layerA),
				"0.000000");
	}

	@Test
	void 项目预留缺失或错误成本层必须稳定拒绝且不锁定余额() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long projectId = insertProject("023 预留成本层校验项目");
		long otherProjectId = insertProject("023 预留成本层其他项目");
		long layerA = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		long otherLayer = insertProjectCostLayer(otherProjectId, fixture.rawMaterialId(), "2.000000", "30.00",
				"15.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				layerA, "5.000000", "50.00", "10.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), otherProjectId,
				otherLayer, "2.000000", "30.00", "15.000000");

		assertThatThrownBy(() -> reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "1.000000", "PROJECT", projectId, null))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		assertThatThrownBy(() -> reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "1.000000", "PROJECT", projectId, otherLayer))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		assertThatThrownBy(() -> reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "1.000000", "PUBLIC", projectId, layerA))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.VALIDATION_ERROR);
		assertDecimal(lockedQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), "PROJECT", projectId, layerA),
				"0.000000");
	}

	@Test
	void 预留消费与释放必须按余额父预留子预留顺序获取锁() throws Exception {
		String availabilitySource = Files.readString(Path.of(
				"src/main/java/com/qherp/api/system/inventory/InventoryAvailabilityService.java"));
		String trackedConsume = methodBody(availabilitySource, "public boolean consumeTrackedBySourceLine");
		assertAppearsInOrder(trackedConsume, "findActiveParentReservation(", "lockReservationRootLatch(",
				"lockExactReservationBalance(", "lockActiveParentReservation(", "lockActiveChildReservation(");
		assertThat(trackedConsume).doesNotContain("reserveFromWarehouse(");

		String exactConsume = methodBody(availabilitySource, "public boolean consumeBySourceLine");
		assertAppearsInOrder(exactConsume, "findActiveReservation(", "lockExactReservationBalance(",
				"lockActiveReservation(");

		String reserveFromWarehouse = methodBody(availabilitySource, "public long reserveFromWarehouse");
		assertAppearsInOrder(reserveFromWarehouse, "validateReservationCommand(command)",
				"if (command.parentReservationId() != null)", "lockReservationSourceLatches(command)",
				"validateReservationIdentity(command)", "availableQuantity");

		String releaseBySource = methodBody(availabilitySource, "public void releaseBySource");
		assertAppearsInOrder(releaseBySource, "lockReservationSourceDocumentLatch(sourceType, sourceId)",
				"releaseReservationRootCandidatesBySource(", "lockReservationRootLatches(",
				"releaseReservationCandidatesBySource(",
				"lockReleaseReservationBalances(", "lockActiveReservationsBySource(");

		String releaseBySourceLine = methodBody(availabilitySource, "public void releaseBySourceLine");
		assertAppearsInOrder(releaseBySourceLine, "lockReservationSourceLineLatch(sourceType, sourceLineId)",
				"releaseReservationRootCandidatesBySourceLine(", "lockReservationRootLatches(",
				"releaseReservationCandidatesBySourceLine(",
				"lockReleaseReservationBalances(", "lockActiveReservationsBySourceLine(");
		String sourceLatchOrder = methodBody(availabilitySource, "private void lockReservationSourceLatches(");
		assertAppearsInOrder(sourceLatchOrder, "lockReservationSourceDocumentLatch(command.sourceType(), command.sourceId())",
				"lockReservationSourceLineLatch(command.sourceType(), command.sourceLineId())");
		String sourceLatch = methodBody(availabilitySource, "private void lockReservationSourceLatch(");
		assertThat(sourceLatch).contains("pg_advisory_xact_lock(?, hashtext(cast(? as text)))");
		assertThat(sourceLatch).doesNotContain("hashCode(");
		assertAppearsInOrder(methodBody(availabilitySource,
				"private Optional<BalanceLock> lockQualifiedBalance(Long warehouseId"),
				"order by id", "for update");
		assertAppearsInOrder(methodBody(availabilitySource, "private BigDecimal aggregateQualifiedQuantityForUpdate("),
				"order by id", "for update");
		assertReservationLockOrder(methodBody(availabilitySource,
				"private BigDecimal activeExactLockedQuantityForUpdate("));
		assertReservationLockOrder(methodBody(availabilitySource,
				"private BigDecimal activeLockedQuantityForUpdate(Long warehouseId"));
		assertReservationLockOrder(methodBody(availabilitySource,
				"private List<ReservationLock> lockActiveReservationsBySource("));
		assertReservationLockOrder(methodBody(availabilitySource,
				"private List<ReservationLock> lockActiveReservationsBySourceLine("));
		assertAppearsInOrder(methodBody(availabilitySource, "private BigDecimal activeChildQuantityForUpdate("),
				"order by id", "for update");

		String postingSource = Files.readString(Path.of(
				"src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java"));
		String posting = methodBody(postingSource, "public PostingResult post");
		assertAppearsInOrder(posting, "lockAggregateQualifiedBalanceSnapshotIfRequired(", "lockedBalance(",
				"assertQualifiedOutboundAvailable(");
		String directAssert = methodBody(postingSource, "public void assertQualifiedOutboundAvailable");
		assertAppearsInOrder(directAssert, "lockAggregateQualifiedBalanceSnapshotIfRequired(",
				"exactQualifiedQuantityForUpdate(", "assertQualifiedOutboundAvailable(");
		String privateAssert = methodBody(postingSource, "private void assertQualifiedOutboundAvailable");
		assertAppearsInOrder(privateAssert, "activeAggregateParentUnallocatedQuantityForUpdate(",
				"activeLockedQuantityForUpdate(", "activeTrackedExactLockedQuantityForUpdate(");
		assertThat(privateAssert).doesNotContain("aggregateSnapshot == null");
		assertThat(privateAssert).doesNotContain("lockAggregateQualifiedBalanceSnapshot(");
		assertThat(privateAssert).doesNotContain("aggregateQualifiedQuantityForUpdate(");
		assertAppearsInOrder(methodBody(postingSource,
				"private AggregateQualifiedBalanceSnapshot lockAggregateQualifiedBalanceSnapshot("),
				"order by id", "for update");
		assertAppearsInOrder(methodBody(postingSource,
				"private BigDecimal aggregateQualifiedQuantityForUpdate("),
				"order by id", "for update");
		assertReservationLockOrder(methodBody(postingSource,
				"boolean excludeSource, String sourceType"));
		assertReservationLockOrder(methodBody(postingSource,
				"private BigDecimal activeTrackedExactLockedQuantityForUpdate("));
		assertAppearsInOrder(methodBody(postingSource,
				"private BigDecimal activeAggregateParentUnallocatedQuantityForUpdate("),
				"order by id", "for update");
		assertAppearsInOrder(methodBody(postingSource, "private BigDecimal parentUnallocatedQuantity("),
				"order by id", "for update");
	}

	@Test
	void 多端点过账必须在首次余额锁前按稳定库存端点加事务锁() throws Exception {
		String postingSource = Files.readString(Path.of(
				"src/main/java/com/qherp/api/system/inventory/InventoryPostingService.java"));
		String scopeLock = methodBody(postingSource, "public void lockPostingScopes(");
		assertThat(scopeLock).contains("POSTING_SCOPE_LOCK_NAMESPACE");
		assertThat(scopeLock).contains("TransactionSynchronizationManager.isActualTransactionActive()");
		assertAppearsInOrder(postingSource, "@Transactional(propagation = Propagation.MANDATORY)",
				"public void lockPostingScopes(");
		assertAppearsInOrder(scopeLock, "postingScopeLockKeys(scopes)", ".distinct()", ".sorted()",
				"pg_advisory_xact_lock(?, ?)");
		String scopeKeys = methodBody(postingSource, "private List<Integer> postingScopeLockKeys(");
		assertThat(scopeKeys).contains("hashtext(cast(? as text))");
		assertThat(scopeKeys).doesNotContain("hashCode(");

		String qualityTransfer = methodBody(postingSource,
				"String operatorName, Long batchId, Long serialId, ValuationContext requestedContext)");
		assertAppearsInOrder(qualityTransfer, "lockPostingScopes(List.of(",
				"new PostingScope(warehouseId, materialId)", "qualityTransferSourceContext(",
				"PostingResult fromResult = post(");

		String stage023Source = Files.readString(Path.of(
				"src/main/java/com/qherp/api/system/inventory/InventoryStage023AdminService.java"));
		String warehouseTransferPost = methodBody(stage023Source,
				"public Map<String, Object> postWarehouseTransfer(");
		assertAppearsInOrder(warehouseTransferPost, "List<TransferLine> lines = transferLines(id)",
				"lockWarehouseTransferPostingScopes(lines)", "for (TransferLine line : lines)",
				"this.inventoryPostingService.post(");
		assertThat(methodBody(stage023Source, "private void lockWarehouseTransferPostingScopes("))
			.contains("new InventoryPostingService.PostingScope(line.sourceWarehouseId(), line.materialId())")
			.contains("new InventoryPostingService.PostingScope(line.targetWarehouseId(), line.materialId())")
			.contains("this.inventoryPostingService.lockPostingScopes(scopes)");

		String ownershipConversionPost = methodBody(stage023Source,
				"public void postOwnershipConversionFromApproval(");
		assertAppearsInOrder(ownershipConversionPost, "List<OwnershipLine> lines = ownershipLines(id)",
				"lockOwnershipConversionPostingScopes(lines)", "resolveOwnershipConversionSourceLayers(lines)",
				"for (OwnershipLine line : lines)",
				"this.inventoryPostingService.post(");
		assertThat(methodBody(stage023Source, "private void lockOwnershipConversionPostingScopes("))
			.contains("new InventoryPostingService.PostingScope(line.sourceWarehouseId(), line.materialId())")
			.contains("new InventoryPostingService.PostingScope(line.targetWarehouseId(), line.materialId())")
			.contains("this.inventoryPostingService.lockPostingScopes(scopes)");
	}

	@Test
	void 质量冻结解冻入口必须先拿过账端点锁再做可用量校验和过账() throws Exception {
		String qualitySource = Files.readString(Path.of(
				"src/main/java/com/qherp/api/system/quality/QualityAdminService.java"));
		String qualityTransfer = methodBody(qualitySource,
				"private QualityStatusTransferResponse transferQualityStatus(");
		assertAppearsInOrder(qualityTransfer, "this.inventoryPostingService.lockPostingScopes(List.of(",
				"new InventoryPostingService.PostingScope(request.warehouseId(), request.materialId())",
				"assertTrackedFreezeAvailable(");
		assertAppearsInOrder(qualityTransfer, "this.inventoryPostingService.lockPostingScopes(List.of(",
				"assertQualityFreezeAvailable(");
		assertAppearsInOrder(qualityTransfer, "this.inventoryPostingService.lockPostingScopes(List.of(",
				"this.inventoryPostingService.transferQualityStatus(");
	}

	@Test
	void 批次追踪父级聚合预留不写具体批次锁但必须保护剩余总量() {
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long batchA = insertTrackedBatchStock(fixture, source, "B-AGG-A-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "3.000000");
		long batchB = insertTrackedBatchStock(fixture, source, "B-AGG-B-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "4.000000");
		long parentId = reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
				"5.000000", "PUBLIC", null, null, InventoryQualityStatus.QUALIFIED, null, null, null);

		assertReservationIdentity(parentId, null, null, null, "ACTIVE", "5.000000", "0.000000");
		assertDecimal(lockedQuantityByBatch(fixture.rawWarehouseId(), fixture.rawMaterialId(), batchA), "0.000000");
		assertDecimal(lockedQuantityByBatch(fixture.rawWarehouseId(), fixture.rawMaterialId(), batchB), "0.000000");

		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.SALES_SHIPMENT, InventoryDirection.OUT, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("2.000000"),
				InventoryQualityStatus.QUALIFIED, "STAGE023_AGG_OUT", nextSourceId(), nextSourceLineId(),
				LocalDate.now(), "聚合预留总量足够时允许批次出库", null, "tester", false, batchA, null));

		assertThatThrownBy(() -> this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.SALES_SHIPMENT, InventoryDirection.OUT, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("1.000000"),
				InventoryQualityStatus.QUALIFIED, "STAGE023_AGG_OUT", nextSourceId(), nextSourceLineId(),
				LocalDate.now(), "聚合预留总量不足时拒绝批次出库", null, "tester", false, batchB, null)))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE);
		assertThatThrownBy(() -> this.qualityAdminService.freeze(new QualityAdminService.QualityStatusTransferRequest(
				fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "1.000000",
				"聚合预留总量不足时拒绝批次冻结", null, "PUBLIC", null, null,
				List.of(new InventoryTrackingService.TrackingAllocationRequest(batchB, null, null, null,
						new BigDecimal("1.000000"), "FROZEN", null))),
				backendOperator(), request()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.INVENTORY_TRACKING_NOT_AVAILABLE);
	}

	@Test
	void 销售批次发货必须创建并消费精确子预留且扣减父级聚合未分配量() {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.finishedMaterialId());
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.finishedMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture.finishedMaterialId(), fixture.eachUnitId(),
				fixture.finishedWarehouseId());
		long batchId = insertTrackedBatchStock(fixture.finishedWarehouseId(), fixture.finishedMaterialId(),
				fixture.eachUnitId(), source, "B-SALES-CHILD-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "5.000000");
		insertPublicValuationPool(fixture.finishedMaterialId(), "5.000000", "50.00", "10.000000");
		long customerId = insertCustomer("023 批次父子预留客户");
		SalesAdminService.SalesOrderDetailResponse order = this.salesAdminService.createOrder(
				new SalesAdminService.SalesOrderRequest(customerId, LocalDate.now(), LocalDate.now().plusDays(3),
						"023 批次父级预留销售订单", null, null, null,
						List.of(new SalesAdminService.SalesOrderLineRequest(1, fixture.finishedMaterialId(),
								fixture.eachUnitId(), new BigDecimal("5.000000"), new BigDecimal("3.000000"),
								fixture.finishedWarehouseId(), LocalDate.now().plusDays(3), "批次父级预留行"))),
				backendOperator(), request());
		SalesAdminService.SalesOrderDetailResponse confirmed = this.salesAdminService.confirmOrder(order.id(),
				backendOperator(), request());
		Long orderLineId = confirmed.lines().getFirst().id();
		long parentId = parentReservationId(InventoryAvailabilityService.SALES_ORDER_SOURCE, orderLineId);

		assertReservationIdentity(parentId, null, null, null, "ACTIVE", "5.000000", "0.000000");
		assertDecimal(lockedQuantityByBatch(fixture.finishedWarehouseId(), fixture.finishedMaterialId(), batchId),
				"0.000000");

		SalesAdminService.SalesShipmentDetailResponse shipment = this.salesAdminService.createShipment(confirmed.id(),
				new SalesAdminService.SalesShipmentRequest(fixture.finishedWarehouseId(), LocalDate.now(),
						"023 批次子预留销售发货",
						List.of(new SalesAdminService.SalesShipmentLineRequest(1, orderLineId,
								fixture.finishedMaterialId(), fixture.eachUnitId(), new BigDecimal("2.000000"),
								"批次子预留行",
								List.of(new InventoryTrackingService.TrackingAllocationRequest(batchId, null, null,
										null, new BigDecimal("2.000000"), null, null))))),
				backendOperator(), request());
		this.salesAdminService.postShipment(shipment.id(), backendOperator(), request());

		long childId = childReservationId(parentId, batchId, null);
		assertReservationIdentity(parentId, null, null, null, "ACTIVE", "5.000000", "2.000000");
		assertReservationIdentity(childId, parentId, batchId, null, "CONSUMED", "2.000000", "2.000000");
		assertDecimal(lockedQuantityByBatch(fixture.finishedWarehouseId(), fixture.finishedMaterialId(), batchId),
				"0.000000");
	}

	@Test
	void 生产批次领料必须先释放父级聚合预留再按分配消费子预留() {
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long batchId = insertTrackedBatchStock(fixture, source, "B-MFG-CHILD-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "3.000000");
		ProductionFixture production = productionFixture(fixture);
		this.jdbcTemplate.update("update mfg_work_order set status = 'DRAFT', released_by = null, released_at = null where id = ?",
				production.workOrderId());
		CurrentUser operator = backendOperator();
		ProductionAdminService.WorkOrderDetailResponse released = this.productionAdminService
			.releaseWorkOrder(production.workOrderId(), operator, request());
		Long workOrderMaterialId = released.materials().getFirst().id();
		long parentId = parentReservationId(InventoryAvailabilityService.PRODUCTION_WORK_ORDER_SOURCE,
				workOrderMaterialId);

		assertReservationIdentity(parentId, null, null, null, "ACTIVE", "3.000000", "0.000000");
		assertDecimal(lockedQuantityByBatch(fixture.rawWarehouseId(), fixture.rawMaterialId(), batchId), "0.000000");

		ProductionAdminService.MaterialIssueDetailResponse issue = this.productionAdminService.createMaterialIssue(
				production.workOrderId(),
				new ProductionAdminService.MaterialIssueRequest(LocalDate.now(), "023 批次子预留生产领料", null,
						List.of(new ProductionAdminService.MaterialIssueLineRequest(1, workOrderMaterialId,
								fixture.rawWarehouseId(), new BigDecimal("2.000000"), "生产批次子预留",
								List.of(new InventoryTrackingService.TrackingAllocationRequest(batchId, null, null,
										null, new BigDecimal("2.000000"), null, null))))),
				operator, request());
		this.productionAdminService.postMaterialIssue(production.workOrderId(), issue.id(), operator, request());

		long childId = childReservationId(parentId, batchId, null);
		assertReservationIdentity(parentId, null, null, null, "ACTIVE", "3.000000", "2.000000");
		assertReservationIdentity(childId, parentId, batchId, null, "CONSUMED", "2.000000", "2.000000");
		assertDecimal(lockedQuantityByBatch(fixture.rawWarehouseId(), fixture.rawMaterialId(), batchId), "0.000000");
	}

	@Test
	void 序列精确子预留数量必须为一且公共项目聚合预留互相隔离() {
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'SERIAL' where id = ?",
				fixture.semiMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture.semiMaterialId(), fixture.eachUnitId(),
				fixture.rawWarehouseId());
		long serialId = insertTrackedSerial(fixture, source, "SN-CHILD-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "IN_STOCK", true);
		long publicParentId = reserveInventory(fixture.rawWarehouseId(), fixture.semiMaterialId(),
				fixture.eachUnitId(), "1.000000", "PUBLIC", null, null, InventoryQualityStatus.QUALIFIED, null, null,
				null);
		long projectId = insertProject("023 序列项目预留隔离");
		long layerId = insertProjectCostLayer(projectId, fixture.semiMaterialId(), "1.000000", "15.00",
				"15.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.semiMaterialId(), fixture.eachUnitId(), projectId,
				layerId, "1.000000", "15.00", "15.000000");
		long projectParentId = reserveInventory(fixture.rawWarehouseId(), fixture.semiMaterialId(),
				fixture.eachUnitId(), "1.000000", "PROJECT", projectId, layerId, InventoryQualityStatus.QUALIFIED,
				null, null, null);

		assertThatThrownBy(() -> reserveInventory(fixture.rawWarehouseId(), fixture.semiMaterialId(),
				fixture.eachUnitId(), "2.000000", "PUBLIC", null, null, InventoryQualityStatus.QUALIFIED, null,
				serialId, publicParentId))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		long childId = reserveInventory(fixture.rawWarehouseId(), fixture.semiMaterialId(), fixture.eachUnitId(),
				"1.000000", "PUBLIC", null, null, InventoryQualityStatus.QUALIFIED, null, serialId,
				publicParentId);
		assertReservationIdentity(childId, publicParentId, null, serialId, "ACTIVE", "1.000000", "0.000000");
		assertReservationIdentity(projectParentId, null, null, null, "ACTIVE", "1.000000", "0.000000");
	}

	@Test
	void 项目质量转换显式成本层必须校验存在性和项目归属() throws Exception {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long sourceProjectId = insertProject("023 质量转换源项目");
		long otherProjectId = insertProject("023 质量转换其他项目");
		long sourceLayer = insertProjectCostLayer(sourceProjectId, fixture.rawMaterialId(), "2.000000", "20.00",
				"10.000000");
		long otherLayer = insertProjectCostLayer(otherProjectId, fixture.rawMaterialId(), "1.000000", "30.00",
				"30.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), sourceProjectId,
				sourceLayer, "2.000000", "20.00", "10.000000");

		assertThatThrownBy(() -> this.qualityAdminService.freeze(
				new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
						fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "1.000000",
						"不存在成本层不得冻结", null, "PROJECT", sourceProjectId, 9_999_999_999L, null),
				backendOperator(), request()))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT));
		assertThatThrownBy(() -> this.qualityAdminService.freeze(
				new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
						fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "1.000000",
						"其他项目成本层不得冻结", null, "PROJECT", sourceProjectId, otherLayer, null),
				backendOperator(), request()))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT));
	}

	@Test
	void 项目质量冻结解冻必须按成本层校验锁定量且先校验成本层身份() throws Exception {
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long sourceProjectId = insertProject("023 质量冻结预留源项目");
		long otherProjectId = insertProject("023 质量冻结预留其他项目");
		long sourceLayer = insertProjectCostLayer(sourceProjectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		long otherLayer = insertProjectCostLayer(otherProjectId, fixture.rawMaterialId(), "2.000000", "30.00",
				"15.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), sourceProjectId,
				sourceLayer, "5.000000", "50.00", "10.000000");

		assertThatThrownBy(() -> this.qualityAdminService.freeze(
				new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
						fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "1.000000",
						"不存在成本层不得冻结", null, "PROJECT", sourceProjectId, 9_999_999_999L, null),
				backendOperator(), request()))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT));
		assertThatThrownBy(() -> this.qualityAdminService.freeze(
				new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
						fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "1.000000",
						"其他项目成本层不得冻结", null, "PROJECT", sourceProjectId, otherLayer, null),
				backendOperator(), request()))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT));

		this.qualityAdminService.freeze(new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "1.000000",
				"无预留项目层可质量冻结", null, "PROJECT", sourceProjectId, sourceLayer, null), backendOperator(),
				request());
		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), sourceProjectId,
				sourceLayer).get("quantity_on_hand"), "4.000000");
		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), sourceProjectId,
				sourceLayer, InventoryQualityStatus.FROZEN).get("quantity_on_hand"), "1.000000");

		this.qualityAdminService.unfreeze(new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "1.000000",
				"无预留项目层可质量解冻", null, "PROJECT", sourceProjectId, sourceLayer, null), backendOperator(),
				request());
		assertDecimal((BigDecimal) projectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), sourceProjectId,
				sourceLayer).get("quantity_on_hand"), "5.000000");

		reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000", "PROJECT",
				sourceProjectId, sourceLayer);
		assertThatThrownBy(() -> this.qualityAdminService.freeze(
				new QualityAdminService.QualityStatusTransferRequest(fixture.rawWarehouseId(),
						fixture.rawMaterialId(), fixture.kgUnitId(), LocalDate.now(), "4.000000",
						"锁定量不足不得冻结", null, "PROJECT", sourceProjectId, sourceLayer, null),
				backendOperator(), request()))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE));
		assertDecimal(lockedQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), "PROJECT", sourceProjectId,
				sourceLayer), "2.000000");
	}

	@Test
	void controlledInventoryDocumentsUseAllowedActionsKeywordPeriodGuardAndCostMasking() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession viewer = createInventoryUserAndLogin("inventory-controlled-viewer-", "INV_CTRL_VIEW_",
				"受控单据查看", List.of("inventory:ownership-conversion:view", "inventory:balance:view"));
		InventoryFixture fixture = fixture();
		long projectId = insertProject("023 受控单据脱敏项目");
		Map<String, Object> conversionBody = ownershipConversionPayload(fixture.rawWarehouseId(),
				fixture.finishedWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId, "1.000000",
				"023 受控单据 keyword 脱敏");
		@SuppressWarnings("unchecked")
		Map<String, Object> conversionLine = (Map<String, Object>) ((List<?>) conversionBody.get("lines")).get(0);
		conversionLine.put("sourceUnitCost", "7.000000");
		JsonNode created = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				conversionBody, admin));
		assertThat(created.has("allowedActions")).isTrue();
		assertThat(created.has("availableActions")).isFalse();

		JsonNode hidden = data(get("/api/admin/inventory/ownership-conversions/" + created.get("id").longValue(),
				viewer));
		assertThat(hidden.get("costVisible").booleanValue()).isFalse();
		assertThat(hidden.get("lines").get(0).has("sourceUnitCost")).isFalse();
		assertThat(hidden.get("lines").get(0).has("sourceCostLayerId")).isFalse();
		assertThat(hidden.get("allowedActions").size()).isZero();

		ResponseEntity<String> emptyKeyword = get(
				"/api/admin/inventory/ownership-conversions?keyword=023-%E4%B8%8D%E5%AD%98%E5%9C%A8",
				admin);
		assertOk(emptyKeyword);
		assertThat(data(emptyKeyword).get("total").longValue()).isZero();

		LocalDate lockedDate = LocalDate.of(2090, 8, 5);
		lockPeriod(lockedDate);
		assertError(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				withBusinessDate(warehouseTransferPayload(fixture.rawWarehouseId(), fixture.finishedWarehouseId(),
						fixture.rawMaterialId(), fixture.kgUnitId(), "1.000000", "023 锁定期间调拨创建"), lockedDate),
				admin), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	@Test
	void stocktakeRangeLockBlocksOverlapsAndUnifiedPostingButAllowsStocktakeVariance() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "5.000000",
				InventoryQualityStatus.QUALIFIED);
		JsonNode firstDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 盘点范围锁一"), admin));
		JsonNode firstCounting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + firstDraft.get("id").longValue() + "/start",
				Map.of("version", firstDraft.get("version").longValue(), "reason", "开始盘点范围锁一",
						"idempotencyKey", "STK-LOCK-START-" + firstDraft.get("id").longValue()),
				admin));
		JsonNode secondDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 盘点范围锁二"), admin));
		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + secondDraft.get("id").longValue() + "/start",
				Map.of("version", secondDraft.get("version").longValue(), "reason", "开始盘点范围锁二",
						"idempotencyKey", "STK-LOCK-START-" + secondDraft.get("id").longValue()),
				admin), HttpStatus.CONFLICT, "INVENTORY_STOCKTAKE_RANGE_LOCKED");

		assertThatThrownBy(() -> this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_DECREASE, InventoryDirection.OUT, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("1.000000"),
				InventoryQualityStatus.QUALIFIED, "INVENTORY_DOCUMENT", 2_900_000L + SEQUENCE.incrementAndGet(),
				2_910_000L + SEQUENCE.incrementAndGet(), LocalDate.now(), "023 盘点锁禁止普通过账", null, "tester")))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.INVENTORY_STOCKTAKE_RANGE_LOCKED);

		JsonNode counted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + firstDraft.get("id").longValue() + "/lines",
				stocktakeLineUpdatePayload(firstCounting,
						stocktakeLines(firstDraft.get("id").longValue(), 1, 20, admin), true), admin));
		JsonNode reconciled = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + firstDraft.get("id").longValue() + "/reconcile",
				Map.of("version", counted.get("version").longValue(), "reason", "确认盘差",
						"idempotencyKey", "STK-LOCK-RECON-" + firstDraft.get("id").longValue()),
				admin));
		assertThat(reconciled.get("status").asText()).isEqualTo("RECONCILED");
	}

	@Test
	void 盘点详情不返回无界行且分页行接口返回估值要求并脱敏成本() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "5.000000",
				"8.000000", InventoryQualityStatus.QUALIFIED, "023 盘点均价库存");

		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 分页盘点"), admin));
		long stocktakeId = draft.get("id").longValue();
		JsonNode counting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/start",
				Map.of("version", draft.get("version").longValue(), "reason", "开始分页盘点",
						"idempotencyKey", "STK-PAGE-START-" + stocktakeId),
				admin));

		assertThat(counting.has("lines")).isFalse();
		assertThat(counting.get("lineSummary").get("totalLines").longValue()).isEqualTo(1L);
		JsonNode firstPage = stocktakeLines(stocktakeId, 1, 1, admin);
		assertThat(firstPage.get("total").longValue()).isEqualTo(1L);
		JsonNode line = firstPage.get("items").get(0);
		assertThat(line.get("valuationRequirement").get("mode").asText()).isEqualTo("NONE");

		ResponseEntity<String> countResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeLineUpdatePayload(counting, firstPage, true), admin);
		assertOk(countResponse);
		JsonNode counted = data(countResponse);
		JsonNode valuedLine = stocktakeLines(stocktakeId, 1, 1, admin).get("items").get(0);
		assertThat(valuedLine.get("valuationRequirement").get("mode").asText())
			.isEqualTo("AUTO_PUBLIC_AVERAGE");
		assertDecimal(new BigDecimal(valuedLine.get("valuationRequirement").get("unitCost").asText()),
				"8.000000");
		assertThat(valuedLine.get("varianceUnitCost").isNull()).isTrue();

		AuthenticatedSession stocktakeViewer = createStocktakeViewerWithoutValuationAndLogin();
		JsonNode maskedLine = stocktakeLines(stocktakeId, 1, 1, stocktakeViewer).get("items").get(0);
		assertThat(maskedLine.get("valuationRequirement").get("mode").asText())
			.isEqualTo("AUTO_PUBLIC_AVERAGE");
		assertThat(maskedLine.has("costLayerId")).isFalse();
		assertThat(maskedLine.has("varianceUnitCost")).isFalse();
		assertThat(maskedLine.get("valuationRequirement").has("unitCost")).isFalse();
		assertThat(counted.has("lines")).isFalse();
	}

	@Test
	void 盘点跨页行更新必须保留未提交页并区分零单价和空单价() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				InventoryQualityStatus.QUALIFIED);
		postInventory(fixture.rawWarehouseId(), fixture.auxiliaryMaterialId(), fixture.meterUnitId(), "3.000000",
				InventoryQualityStatus.QUALIFIED);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("scopeType", "WAREHOUSE");
		body.put("warehouseId", fixture.rawWarehouseId());
		body.put("reason", "023 跨页盘点");
		body.put("idempotencyKey", "STK-CROSS-PAGE-" + SEQUENCE.incrementAndGet());
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes", body, admin));
		long stocktakeId = draft.get("id").longValue();
		JsonNode counting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/start",
				Map.of("version", draft.get("version").longValue(), "reason", "开始跨页盘点",
						"idempotencyKey", "STK-CROSS-START-" + stocktakeId),
				admin));
		JsonNode firstPage = stocktakeLines(stocktakeId, 1, 1, admin);
		JsonNode secondPage = stocktakeLines(stocktakeId, 2, 1, admin);

		JsonNode firstLine = firstPage.get("items").get(0);
		BigDecimal countedQuantity = new BigDecimal(firstLine.get("bookQuantity").asText()).add(BigDecimal.ONE);
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("id", firstLine.get("id").longValue());
		line.put("version", firstLine.get("version").longValue());
		line.put("countedQuantity", countedQuantity.toPlainString());
		line.put("varianceUnitCost", "0.000000");
		line.put("varianceReason", "跨页零单价保留");
		Map<String, Object> update = new LinkedHashMap<>();
		update.put("version", counting.get("version").longValue());
		update.put("lines", List.of(line));
		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines", update,
				admin));

		JsonNode updatedFirstLine = stocktakeLines(stocktakeId, 1, 1, admin).get("items").get(0);
		JsonNode untouchedSecondLine = stocktakeLines(stocktakeId, 2, 1, admin).get("items").get(0);
		assertThat(updatedFirstLine.get("varianceUnitCost").asText()).isEqualTo("0.000000");
		assertThat(untouchedSecondLine.get("id").longValue()).isEqualTo(secondPage.get("items").get(0).get("id")
			.longValue());
		assertThat(untouchedSecondLine.get("countedQuantity").isNull()).isTrue();
		assertThat(untouchedSecondLine.get("varianceUnitCost").isNull()).isTrue();
	}

	@Test
	void 盘点显式成本写入必须具备成本权限且有权限用户可写() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		insertPublicBalanceWithoutAverage(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
				"4.000000");
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 显式成本权限盘点"), admin));
		long stocktakeId = draft.get("id").longValue();
		JsonNode counting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/start",
				Map.of("version", draft.get("version").longValue(), "reason", "开始显式成本权限盘点",
						"idempotencyKey", "STK-COST-PERM-START-" + stocktakeId),
				admin));
		JsonNode linePage = stocktakeLines(stocktakeId, 1, 20, admin);
		Map<String, Object> payload = stocktakeLineUpdatePayload(counting, linePage, true, "9.000000",
				"显式成本写入");
		AuthenticatedSession updaterWithoutCost = createInventoryUserAndLogin("stocktake-updater-", "STK_UPD_",
				"盘点更新无成本", List.of("inventory:stocktake:view", "inventory:stocktake:update"));

		assertError(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines", payload,
				updaterWithoutCost), HttpStatus.FORBIDDEN, "INVENTORY_COST_PERMISSION_REQUIRED");

		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines", payload,
				admin));
		JsonNode updatedLine = stocktakeLines(stocktakeId, 1, 20, admin).get("items").get(0);
		assertThat(updatedLine.get("varianceUnitCost").asText()).isEqualTo("9.000000");
	}

	@Test
	void 无成本权限更新盘点数量不得用空成本清空已有正差异单价() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		insertPublicBalanceWithoutAverage(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
				"4.000000");
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 空成本权限盘点"), admin));
		long stocktakeId = draft.get("id").longValue();
		JsonNode counting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/start",
				Map.of("version", draft.get("version").longValue(), "reason", "开始空成本权限盘点",
						"idempotencyKey", "STK-NULL-COST-START-" + stocktakeId),
				admin));
		JsonNode linePage = stocktakeLines(stocktakeId, 1, 20, admin);
		JsonNode priced = data(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeLineUpdatePayload(counting, linePage, true, "9.000000", "初始显式成本"), admin));
		JsonNode pricedLine = stocktakeLines(stocktakeId, 1, 20, admin).get("items").get(0);
		assertThat(pricedLine.get("varianceUnitCost").asText()).isEqualTo("9.000000");
		AuthenticatedSession updaterWithoutCost = createInventoryUserAndLogin("stocktake-null-cost-", "STK_NULL_",
				"盘点更新空成本", List.of("inventory:stocktake:view", "inventory:stocktake:update"));

		JsonNode nullCostUpdate = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeSingleLineUpdatePayload(priced, pricedLine, "6.000000", true, null, "无成本权限保留空成本"),
				updaterWithoutCost));
		JsonNode afterNullCost = stocktakeLines(stocktakeId, 1, 20, admin).get("items").get(0);
		assertThat(afterNullCost.get("varianceUnitCost").asText()).isEqualTo("9.000000");

		JsonNode missingCostUpdate = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeSingleLineUpdatePayload(nullCostUpdate, afterNullCost, "7.000000", false, null,
						"无成本权限缺失成本"),
				updaterWithoutCost));
		JsonNode afterMissingCost = stocktakeLines(stocktakeId, 1, 20, admin).get("items").get(0);
		assertThat(afterMissingCost.get("varianceUnitCost").asText()).isEqualTo("9.000000");

		assertError(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeSingleLineUpdatePayload(missingCostUpdate, afterMissingCost, "8.000000", true,
						"11.000000", "无成本权限尝试改成本"),
				updaterWithoutCost), HttpStatus.FORBIDDEN, "INVENTORY_COST_PERMISSION_REQUIRED");

		JsonNode cleared = data(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeSingleLineUpdatePayload(missingCostUpdate, afterMissingCost, "8.000000", true, null,
						"有成本权限清空成本"),
				admin));
		JsonNode afterCleared = stocktakeLines(stocktakeId, 1, 20, admin).get("items").get(0);
		assertThat(afterCleared.get("varianceUnitCost").isNull()).isTrue();

		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeSingleLineUpdatePayload(cleared, afterCleared, "9.000000", true, "12.000000",
						"有成本权限重写成本"),
				admin));
		JsonNode afterRewrite = stocktakeLines(stocktakeId, 1, 20, admin).get("items").get(0);
		assertThat(afterRewrite.get("varianceUnitCost").asText()).isEqualTo("12.000000");
	}

	@Test
	void 公共池数量为零的历史均价不得自动作为盘盈成本() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		insertPublicBalanceWithPoolAverage(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
				"4.000000", "0.000000", "0.00", "0.000000");
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 零数量均价盘点"), admin));
		long stocktakeId = draft.get("id").longValue();
		JsonNode counting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/start",
				Map.of("version", draft.get("version").longValue(), "reason", "开始零数量均价盘点",
						"idempotencyKey", "STK-ZERO-AVG-START-" + stocktakeId),
				admin));
		JsonNode linePage = stocktakeLines(stocktakeId, 1, 20, admin);
		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeLineUpdatePayload(counting, linePage, true), admin));

		JsonNode countedLine = stocktakeLines(stocktakeId, 1, 20, admin).get("items").get(0);
		assertThat(countedLine.get("valuationRequirement").get("mode").asText()).isEqualTo("EXPLICIT_UNIT_COST");
		JsonNode counted = data(get("/api/admin/inventory/stocktakes/" + stocktakeId, admin));
		assertError(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/reconcile",
				Map.of("version", counted.get("version").longValue(), "reason", "确认零数量均价盘点",
						"idempotencyKey", "STK-ZERO-AVG-RECON-" + stocktakeId),
				admin), HttpStatus.BAD_REQUEST, "INVENTORY_VALUATION_UNIT_COST_REQUIRED");
	}

	@Test
	void 公共盘盈必须按均价自动或显式单价完成估值() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		postValuedInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "5.000000",
				"8.000000", InventoryQualityStatus.QUALIFIED, "023 公共均价盘盈");

		JsonNode autoDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 公共均价盘盈盘点"), admin));
		long autoStocktakeId = autoDraft.get("id").longValue();
		JsonNode autoCounting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + autoStocktakeId + "/start",
				Map.of("version", autoDraft.get("version").longValue(), "reason", "开始公共均价盘点",
						"idempotencyKey", "STK-PUBLIC-AUTO-START-" + autoStocktakeId),
				admin));
		JsonNode autoCounted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + autoStocktakeId + "/lines",
				stocktakeLineUpdatePayload(autoCounting,
						stocktakeLines(autoStocktakeId, 1, 20, admin), true), admin));
		JsonNode autoReconciled = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + autoStocktakeId + "/reconcile",
				Map.of("version", autoCounted.get("version").longValue(), "reason", "确认公共均价盘盈",
						"idempotencyKey", "STK-PUBLIC-AUTO-RECON-" + autoStocktakeId),
				admin));
		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + autoStocktakeId + "/submit-approval",
				Map.of("version", autoReconciled.get("version").longValue(), "reason", "提交公共均价盘盈",
						"idempotencyKey", "STK-PUBLIC-AUTO-SUBMIT-" + autoStocktakeId),
				admin));
		this.stage023AdminService.postStocktakeFromApproval(autoStocktakeId, backendOperator());
		assertDecimal(stocktakeInboundUnitCost(autoStocktakeId), "8.000000");

		InventoryFixture noAverageFixture = fixture();
		markMaterialValued(noAverageFixture.rawMaterialId());
		insertPublicBalanceWithoutAverage(noAverageFixture.rawWarehouseId(), noAverageFixture.rawMaterialId(),
				noAverageFixture.kgUnitId(), "4.000000");
		JsonNode manualDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(noAverageFixture.rawWarehouseId(), noAverageFixture.rawMaterialId(),
						"023 公共无均价盘盈盘点"),
				admin));
		long manualStocktakeId = manualDraft.get("id").longValue();
		JsonNode manualCounting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + manualStocktakeId + "/start",
				Map.of("version", manualDraft.get("version").longValue(), "reason", "开始公共无均价盘点",
						"idempotencyKey", "STK-PUBLIC-MANUAL-START-" + manualStocktakeId),
				admin));
		JsonNode manualCounted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + manualStocktakeId + "/lines",
				stocktakeLineUpdatePayload(manualCounting,
						stocktakeLines(manualStocktakeId, 1, 20, admin), true), admin));
		assertError(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + manualStocktakeId + "/reconcile",
				Map.of("version", manualCounted.get("version").longValue(), "reason", "缺少公共无均价单价",
						"idempotencyKey", "STK-PUBLIC-MANUAL-RECON-MISSING-" + manualStocktakeId),
				admin), HttpStatus.BAD_REQUEST, "INVENTORY_VALUATION_UNIT_COST_REQUIRED");
	}

	@Test
	void 项目盘盈必须校验单价原因附件并创建真实项目成本层() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		long projectId = insertProject("023 项目盘盈项目");
		long sourceLayerId = insertProjectCostLayer(projectId, fixture.rawMaterialId(), "5.000000", "50.00",
				"10.000000");
		insertProjectBalance(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), projectId,
				sourceLayerId, "5.000000", "50.00", "10.000000");
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 项目盘盈盘点"), admin));
		long stocktakeId = draft.get("id").longValue();
		JsonNode counting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/start",
				Map.of("version", draft.get("version").longValue(), "reason", "开始项目盘盈",
						"idempotencyKey", "STK-PROJECT-START-" + stocktakeId),
				admin));
		JsonNode page = stocktakeLines(stocktakeId, 1, 20, admin);
		JsonNode stocktakeLine = page.get("items").get(0);
		JsonNode missingCost = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktakeId + "/lines",
				stocktakeLineUpdatePayload(counting, page, true), admin));
		assertError(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/reconcile",
				Map.of("version", missingCost.get("version").longValue(), "reason", "缺少项目盘盈单价",
						"idempotencyKey", "STK-PROJECT-RECON-MISSING-COST-" + stocktakeId),
				admin), HttpStatus.BAD_REQUEST, "INVENTORY_VALUATION_UNIT_COST_REQUIRED");

		JsonNode withCost = data(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId
				+ "/lines", stocktakeLineUpdatePayload(missingCost,
						stocktakeLines(stocktakeId, 1, 20, admin), true, "12.000000", null),
				admin));
		assertError(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/reconcile",
				Map.of("version", withCost.get("version").longValue(), "reason", "缺少项目盘盈原因",
						"idempotencyKey", "STK-PROJECT-RECON-MISSING-REASON-" + stocktakeId),
				admin), HttpStatus.BAD_REQUEST, "INVENTORY_STOCKTAKE_VARIANCE_REASON_REQUIRED");

		JsonNode withReason = data(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId
				+ "/lines", stocktakeLineUpdatePayload(withCost, stocktakeLines(stocktakeId, 1, 20, admin), true,
						"12.000000", "项目盘盈实物复核"),
				admin));
		JsonNode reconciled = data(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId
				+ "/reconcile", Map.of("version", withReason.get("version").longValue(), "reason", "确认项目盘盈",
						"idempotencyKey", "STK-PROJECT-RECON-" + stocktakeId),
				admin));
		assertError(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId + "/submit-approval",
				Map.of("version", reconciled.get("version").longValue(), "reason", "缺少项目盘盈证据",
						"idempotencyKey", "STK-PROJECT-SUBMIT-MISSING-EVIDENCE-" + stocktakeId),
				admin), HttpStatus.BAD_REQUEST, "INVENTORY_STOCKTAKE_EVIDENCE_REQUIRED");

		long attachmentId = insertStocktakeEvidence(stocktakeId, "项目盘盈证据");
		JsonNode submitted = data(exchange(HttpMethod.PUT, "/api/admin/inventory/stocktakes/" + stocktakeId
				+ "/submit-approval", Map.of("version", reconciled.get("version").longValue(), "reason", "提交项目盘盈",
						"idempotencyKey", "STK-PROJECT-SUBMIT-" + stocktakeId),
				admin));
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		this.jdbcTemplate.update("update platform_business_attachment set status = 'DELETED' where id = ?",
				attachmentId);
		assertThatThrownBy(() -> this.stage023AdminService.postStocktakeFromApproval(stocktakeId, backendOperator()))
			.isInstanceOf(BusinessException.class)
			.satisfies((exception) -> assertThat(((BusinessException) exception).errorCode().name())
				.isEqualTo("INVENTORY_STOCKTAKE_EVIDENCE_REQUIRED"));

		insertStocktakeEvidence(stocktakeId, "项目盘盈补充证据");
		this.stage023AdminService.postStocktakeFromApproval(stocktakeId, backendOperator());
		assertThat(data(get("/api/admin/inventory/stocktakes/" + stocktakeId, admin)).get("status").asText())
			.isEqualTo("POSTED");
		BigDecimal varianceQuantity = BigDecimal.ONE.setScale(6);
		Map<String, Object> layer = stocktakeProjectLayer(stocktakeId, stocktakeLine.get("id").longValue());
		assertDecimal((BigDecimal) layer.get("original_quantity"), varianceQuantity.toPlainString());
		assertDecimal((BigDecimal) layer.get("unit_cost"), "12.000000");
		assertDecimal((BigDecimal) layer.get("original_amount"), "12.00");
	}

	@Test
	void nonValuedMaterialsOnlyWriteQuantityMovementWithoutValueMovement() {
		InventoryFixture fixture = fixture();
		long sourceId = 2_920_000L + SEQUENCE.incrementAndGet();
		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, fixture.rawWarehouseId(),
				fixture.auxiliaryMaterialId(), fixture.meterUnitId(), new BigDecimal("3.000000"),
				InventoryQualityStatus.QUALIFIED, "INVENTORY_DOCUMENT", sourceId,
				2_930_000L + SEQUENCE.incrementAndGet(), LocalDate.now(), "023 非计价物料只记数量", null, "tester"));

		Long valueCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_value_movement
				where source_type = 'INVENTORY_DOCUMENT'
				and source_id = ?
				""", Long.class, sourceId);
		assertThat(valueCount).isZero();
	}

	@Test
	void stocktakeFrontendRoutesReconcileSubmitCompleteZeroVarianceAndCancel() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "5.000000",
				InventoryQualityStatus.QUALIFIED);

		JsonNode zeroDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 零差异盘点"), admin));
		long zeroStocktakeId = zeroDraft.get("id").longValue();
		assertAvailableActions(zeroDraft, "START");
		assertOk(get("/api/admin/inventory/stocktakes?status=DRAFT", admin));
		assertOk(get("/api/admin/inventory/stocktakes/" + zeroStocktakeId, admin));

		JsonNode zeroCounting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + zeroStocktakeId + "/start",
				Map.of("version", zeroDraft.get("version").longValue(), "reason", "开始零差异盘点",
						"idempotencyKey", "STK-START-" + zeroStocktakeId),
				admin));
		JsonNode zeroCounted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + zeroStocktakeId + "/lines",
				stocktakeLineUpdatePayload(zeroCounting, stocktakeLines(zeroStocktakeId, 1, 20, admin), false),
				admin));
		JsonNode zeroReconciled = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + zeroStocktakeId + "/reconcile",
				Map.of("version", zeroCounted.get("version").longValue(), "reason", "确认零差异",
						"idempotencyKey", "STK-RECON-" + zeroStocktakeId),
				admin));
		assertThat(zeroReconciled.get("status").asText()).isEqualTo("RECONCILED");
		ResponseEntity<String> completedResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + zeroStocktakeId + "/complete-zero-variance",
				Map.of("version", zeroReconciled.get("version").longValue(), "reason", "结束零差异盘点",
						"idempotencyKey", "STK-ZERO-" + zeroStocktakeId),
				admin);
		assertOk(completedResponse);
		assertThat(data(completedResponse).get("status").asText()).isEqualTo("POSTED");

		JsonNode varianceDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.rawMaterialId(), "023 盘差审批盘点"), admin));
		long varianceStocktakeId = varianceDraft.get("id").longValue();
		JsonNode varianceCounting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + varianceStocktakeId + "/start",
				Map.of("version", varianceDraft.get("version").longValue(), "reason", "开始盘差盘点",
						"idempotencyKey", "STK-START-" + varianceStocktakeId),
				admin));
		JsonNode varianceCounted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + varianceStocktakeId + "/lines",
				stocktakeLineUpdatePayload(varianceCounting, stocktakeLines(varianceStocktakeId, 1, 20, admin),
						true),
				admin));
		JsonNode varianceReconciled = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + varianceStocktakeId + "/reconcile",
				Map.of("version", varianceCounted.get("version").longValue(), "reason", "确认盘差",
						"idempotencyKey", "STK-RECON-" + varianceStocktakeId),
				admin));
		ResponseEntity<String> submittedResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + varianceStocktakeId + "/submit-approval",
				Map.of("version", varianceReconciled.get("version").longValue(), "reason", "提交盘差审批",
						"idempotencyKey", "STK-SUBMIT-" + varianceStocktakeId),
				admin);
		assertOk(submittedResponse);
		assertThat(data(submittedResponse).get("status").asText()).isEqualTo("SUBMITTED");

		JsonNode cancelCandidate = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.finishedWarehouseId(), fixture.finishedMaterialId(), "023 盘点取消"),
				admin));
		ResponseEntity<String> cancelledResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + cancelCandidate.get("id").longValue() + "/cancel",
				Map.of("version", cancelCandidate.get("version").longValue(), "reason", "取消盘点",
						"idempotencyKey", "STK-CANCEL-" + cancelCandidate.get("id").longValue()),
				admin);
		assertOk(cancelledResponse);
		assertThat(data(cancelledResponse).get("status").asText()).isEqualTo("CANCELLED");
	}

	@Test
	void valuationAdjustmentFrontendSubmitApprovalAndWithdrawRoutesUseDocumentVersion() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		markMaterialValued(fixture.rawMaterialId());
		insertLegacyUnvaluedPublicStock(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
				"2.000000");
		JsonNode created = data(exchange(HttpMethod.POST, "/api/admin/inventory/valuation-adjustments",
				valuationAdjustmentPayload(fixture.rawMaterialId(), "2.000000", "6.000000", "12.00"), admin));
		long adjustmentId = created.get("id").longValue();

		ResponseEntity<String> submittedResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/valuation-adjustments/" + adjustmentId + "/submit-approval",
				Map.of("version", created.get("version").longValue(), "reason", "提交估值调整审批",
						"idempotencyKey", "VAL-SUBMIT-APPROVAL-" + adjustmentId),
				admin);
		assertOk(submittedResponse);
		JsonNode submitted = data(submittedResponse);
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		assertAvailableActions(submitted, "WITHDRAW");

		ResponseEntity<String> withdrawnResponse = exchange(HttpMethod.PUT,
				"/api/admin/inventory/valuation-adjustments/" + adjustmentId + "/withdraw",
				Map.of("version", submitted.get("version").longValue(), "reason", "撤回估值调整审批",
						"idempotencyKey", "VAL-WITHDRAW-" + adjustmentId),
				admin);
		assertOk(withdrawnResponse);
		JsonNode withdrawn = data(withdrawnResponse);
		assertThat(withdrawn.get("status").asText()).isEqualTo("DRAFT");
		assertThat(withdrawn.get("approvalSummary").get("status").asText()).isEqualTo("WITHDRAWN");
		assertAvailableActions(withdrawn, "SUBMIT_APPROVAL");
	}

	@Test
	void inventoryBalanceSeparatesQualityStatusAndAvailableQuantityUsesQualifiedOnly() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();

		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "10.000000",
				InventoryQualityStatus.QUALIFIED);
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "3.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				InventoryQualityStatus.REJECTED);

		ResponseEntity<String> balances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin);
		assertOk(balances);
		JsonNode balance = firstItem(balances);
		assertDecimal(balance, "quantityOnHand", "15.000000");
		assertDecimal(balance, "availableQuantity", "10.000000");
		assertDecimal(balance, "pendingInspectionQuantity", "3.000000");
		assertDecimal(balance, "qualifiedQuantity", "10.000000");
		assertDecimal(balance, "rejectedQuantity", "2.000000");
		assertDecimal(balance, "frozenQuantity", "0.000000");

		ResponseEntity<String> pendingBalances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId()
				+ "&qualityStatus=PENDING_INSPECTION", admin);
		assertOk(pendingBalances);
		JsonNode pendingBalance = firstItem(pendingBalances);
		assertThat(pendingBalance.get("qualityStatus").asText()).isEqualTo("PENDING_INSPECTION");
		assertThat(pendingBalance.get("qualityStatusName").asText()).isEqualTo("待检");
		assertDecimal(pendingBalance, "quantityOnHand", "3.000000");
		assertDecimal(pendingBalance, "availableQuantity", "0.000000");

		ResponseEntity<String> pendingMovements = get("/api/admin/inventory/movements?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId()
				+ "&qualityStatus=PENDING_INSPECTION", admin);
		assertOk(pendingMovements);
		JsonNode pendingMovement = firstItem(pendingMovements);
		assertThat(pendingMovement.get("qualityStatus").asText()).isEqualTo("PENDING_INSPECTION");
		assertThat(pendingMovement.get("qualityStatusName").asText()).isEqualTo("待检");
	}

	@Test
	void inventoryBalanceSubtractsActiveReservationsAndReservationLedgerIsQueryable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();

		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "10.000000",
				InventoryQualityStatus.QUALIFIED);
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "3.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				InventoryQualityStatus.FROZEN);
		long reservationId = insertReservation(fixture, "RESERVATION", "SALES_ORDER", 91_000L, 91_001L,
				"SO-018-001", "4.000000");
		insertReservation(fixture, "OCCUPATION", "PRODUCTION_WORK_ORDER", 92_000L, 92_001L, "WO-018-001",
				"1.000000");

		ResponseEntity<String> balances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin);
		assertOk(balances);
		JsonNode balance = firstItem(balances);
		assertDecimal(balance, "bookQuantity", "15.000000");
		assertDecimal(balance, "qualifiedQuantity", "10.000000");
		assertDecimal(balance, "reservedQuantity", "4.000000");
		assertDecimal(balance, "occupiedQuantity", "1.000000");
		assertDecimal(balance, "lockedQuantity", "5.000000");
		assertDecimal(balance, "availableQuantity", "5.000000");
		assertDecimal(balance, "availableToPromiseQuantity", "5.000000");
		assertDecimal(balance, "netRequirementShortageQuantity", "0.000000");
		assertThat(balance.has("netRequirementQuantity")).isFalse();
		assertDecimal(balance, "frozenQuantity", "2.000000");

		ResponseEntity<String> allReservations = get("/api/admin/inventory/reservations?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin);
		assertOk(allReservations);
		assertThat(items(allReservations)).hasSize(2);

		ResponseEntity<String> reservations = get("/api/admin/inventory/reservations?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId()
				+ "&reservationType=RESERVATION", admin);
		assertOk(reservations);
		assertThat(items(reservations)).hasSize(1);
		JsonNode reservation = firstItem(reservations);
		assertThat(reservation.get("id").longValue()).isEqualTo(reservationId);
		assertThat(reservation.get("reservationType").asText()).isEqualTo("RESERVATION");
		assertThat(reservation.get("sourceType").asText()).isEqualTo("SALES_ORDER");
		assertThat(reservation.get("sourceTypeName").asText()).isEqualTo("销售订单");
		assertDecimal(reservation, "remainingQuantity", "4.000000");

		ResponseEntity<String> filteredBySourceLine = get("/api/admin/inventory/reservations?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId() + "&sourceLineId=91001"
				+ "&businessDateFrom=" + LocalDate.now() + "&businessDateTo=" + LocalDate.now(), admin);
		assertOk(filteredBySourceLine);
		assertThat(items(filteredBySourceLine)).hasSize(1);

		ResponseEntity<String> detail = get("/api/admin/inventory/reservations/" + reservationId, admin);
		assertOk(detail);
		assertDecimal(data(detail), "remainingQuantity", "4.000000");
		assertThat(data(detail).get("sourceSummary").get("sourceType").asText()).isEqualTo("SALES_ORDER");
		assertThat(data(detail).get("auditRecords").isArray()).isTrue();
	}

	@Test
	void existingInventoryFactsRemainUntrackedAfterBatchSerialMigration() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();

		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "6.000000",
				InventoryQualityStatus.QUALIFIED);
		long reservationId = insertReservation(fixture, "RESERVATION", "SALES_ORDER", 93_000L, 93_001L,
				"SO-019-001", "2.000000");

		assertThat(this.jdbcTemplate.queryForObject("select tracking_method from mst_material where id = ?",
				String.class, fixture.rawMaterialId())).isEqualTo("NONE");
		assertTrackingColumnsAreNull("""
				select batch_id, serial_id
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				""", fixture.rawWarehouseId(), fixture.rawMaterialId());
		assertTrackingColumnsAreNull("""
				select batch_id, serial_id
				from inv_stock_movement
				where warehouse_id = ?
				and material_id = ?
				order by id desc
				limit 1
				""", fixture.rawWarehouseId(), fixture.rawMaterialId());
		assertTrackingColumnsAreNull("""
				select batch_id, serial_id
				from inv_stock_reservation
				where id = ?
				""", reservationId);

		ResponseEntity<String> balances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin);
		assertOk(balances);
		assertDecimal(firstItem(balances), "quantityOnHand", "6.000000");
	}

	@Test
	void trackingFiltersReturnBatchSerialBalanceMovementAndTraceContracts() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		TrackedInventoryFixture tracked = insertTrackedInventoryFacts(fixture);

		ResponseEntity<String> defaultBalances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin);
		assertOk(defaultBalances);
		JsonNode defaultBalance = firstItem(defaultBalances);
		assertThat(defaultBalance.get("trackingMethod").asText()).isEqualTo("BATCH");
		assertThat(defaultBalance.get("batchId").isNull()).isTrue();
		assertThat(defaultBalance.get("serialId").isNull()).isTrue();
		assertDecimal(defaultBalance, "quantityOnHand", "8.000000");

		ResponseEntity<String> batchBalances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&trackingMethod=BATCH&batchNo=" + tracked.batchNo(), admin);
		assertOk(batchBalances);
		JsonNode batchBalance = firstItem(batchBalances);
		assertThat(batchBalance.get("trackingMethod").asText()).isEqualTo("BATCH");
		assertThat(batchBalance.get("trackingMethodName").asText()).isEqualTo("批次管理");
		assertThat(batchBalance.get("batchId").longValue()).isEqualTo(tracked.batchId());
		assertThat(batchBalance.get("batchNo").asText()).isEqualTo(tracked.batchNo());
		assertThat(batchBalance.get("serialId").isNull()).isTrue();
		assertDecimal(batchBalance, "traceableQuantity", "8.000000");
		assertDecimal(batchBalance, "availableQuantity", "8.000000");

		ResponseEntity<String> serialBalances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&trackingMethod=SERIAL&serialId=" + tracked.serialId(), admin);
		assertOk(serialBalances);
		JsonNode serialBalance = firstItem(serialBalances);
		assertThat(serialBalance.get("trackingMethod").asText()).isEqualTo("SERIAL");
		assertThat(serialBalance.get("serialId").longValue()).isEqualTo(tracked.serialId());
		assertThat(serialBalance.get("serialNo").asText()).isEqualTo(tracked.serialNo());
		assertDecimal(serialBalance, "traceableQuantity", "1.000000");

		ResponseEntity<String> movements = get("/api/admin/inventory/movements?trackingMethod=BATCH&batchId="
				+ tracked.batchId(), admin);
		assertOk(movements);
		JsonNode movement = firstItem(movements);
		assertThat(movement.get("trackingMethod").asText()).isEqualTo("BATCH");
		assertThat(movement.get("batchId").longValue()).isEqualTo(tracked.batchId());
		assertThat(movement.get("batchNo").asText()).isEqualTo(tracked.batchNo());
		assertThat(movement.has("targetDocumentNo")).isTrue();
	}

	@Test
	void batchSerialListsAndTraceEndpointsUseSeededPermissionsAndReturnFoundationalData() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		TrackedInventoryFixture tracked = insertTrackedInventoryFacts(fixture);

		ResponseEntity<String> batches = get("/api/admin/inventory/batches?batchNo=" + tracked.batchNo(), admin);
		assertOk(batches);
		JsonNode batch = firstItem(batches);
		assertThat(batch.get("id").longValue()).isEqualTo(tracked.batchId());
		assertThat(batch.get("batchNo").asText()).isEqualTo(tracked.batchNo());
		assertDecimal(batch, "quantityOnHand", "8.000000");
		assertDecimal(batch, "availableQuantity", "8.000000");
		assertThat(batch.get("qualityStatusSummary").isArray()).isTrue();

		ResponseEntity<String> batchDetail = get("/api/admin/inventory/batches/" + tracked.batchId(), admin);
		assertOk(batchDetail);
		assertThat(data(batchDetail).get("batchNo").asText()).isEqualTo(tracked.batchNo());

		ResponseEntity<String> serials = get("/api/admin/inventory/serials?serialNo=" + tracked.serialNo(), admin);
		assertOk(serials);
		JsonNode serial = firstItem(serials);
		assertThat(serial.get("id").longValue()).isEqualTo(tracked.serialId());
		assertThat(serial.get("serialNo").asText()).isEqualTo(tracked.serialNo());
		assertThat(serial.get("stockStatus").asText()).isEqualTo("IN_STOCK");

		ResponseEntity<String> serialDetail = get("/api/admin/inventory/serials/" + tracked.serialId(), admin);
		assertOk(serialDetail);
		assertThat(data(serialDetail).get("serialNo").asText()).isEqualTo(tracked.serialNo());

		ResponseEntity<String> batchTrace = get("/api/admin/inventory/traces/batches/" + tracked.batchId(), admin);
		assertOk(batchTrace);
		JsonNode batchTraceData = data(batchTrace);
		assertThat(batchTraceData.get("subject").get("trackingMethod").asText()).isEqualTo("BATCH");
		assertThat(batchTraceData.get("subject").get("batchNo").asText()).isEqualTo(tracked.batchNo());
		assertThat(batchTraceData.get("currentBalances").isArray()).isTrue();
		assertThat(batchTraceData.get("movements").isArray()).isTrue();

		ResponseEntity<String> serialTrace = get("/api/admin/inventory/traces/serials/" + tracked.serialId(), admin);
		assertOk(serialTrace);
		assertThat(data(serialTrace).get("subject").get("serialNo").asText()).isEqualTo(tracked.serialNo());

		AuthenticatedSession noInventory = createNoInventoryUserAndLogin();
		assertForbidden(get("/api/admin/inventory/batches?batchNo=" + tracked.batchNo(), noInventory));
		assertForbidden(get("/api/admin/inventory/traces/batches/" + tracked.batchId(), noInventory));
		assertError(get("/api/admin/inventory/traces/batches/999999999", admin), HttpStatus.NOT_FOUND,
				"INVENTORY_TRACKING_NOT_FOUND");
		assertError(get("/api/admin/inventory/traces/serials/999999999", admin), HttpStatus.NOT_FOUND,
				"INVENTORY_TRACKING_NOT_FOUND");
	}

	@Test
	void batchAndSerialCandidateListsExposeAvailabilityReasonAndBusinessSourceDocumentNo() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		this.jdbcTemplate.update("update mst_material set tracking_method = 'SERIAL' where id = ?",
				fixture.semiMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long frozenBatchId = insertTrackedBatchStock(fixture, source, "B-CAND-FROZEN-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.FROZEN, "3.000000");
		long availableSerialId = insertTrackedSerial(fixture, source, "SN-CAND-OK-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "IN_STOCK", true);
		long outboundSerialId = insertTrackedSerial(fixture, source,
				"SN-CAND-OUT-" + SEQUENCE.incrementAndGet(), InventoryQualityStatus.QUALIFIED, "OUTBOUND", false);

		ResponseEntity<String> batches = get("/api/admin/inventory/batches?batchNo=B-CAND-FROZEN&onlyAvailable=false",
				admin);
		assertOk(batches);
		JsonNode batch = firstItem(batches);
		assertThat(batch.get("id").longValue()).isEqualTo(frozenBatchId);
		assertThat(batch.get("warehouseId").longValue()).isEqualTo(fixture.rawWarehouseId());
		assertThat(batch.get("warehouseName").asText()).isEqualTo("原料仓");
		assertThat(batch.get("qualityStatus").asText()).isEqualTo(InventoryQualityStatus.FROZEN.name());
		assertThat(batch.get("qualityStatusName").asText()).isEqualTo(InventoryQualityStatus.FROZEN.displayName());
		assertThat(batch.get("stockStatus").asText()).isEqualTo("UNAVAILABLE");
		assertDecimal(batch, "availableQuantity", "0.000000");
		assertThat(batch.get("selectable").booleanValue()).isFalse();
		assertThat(batch.get("disabledReasonCode").asText()).isEqualTo("INVENTORY_TRACKING_NOT_AVAILABLE");
		assertThat(batch.get("disabledReason").asText()).isEqualTo("非可用质量状态");
		assertThat(batch.get("sourceDocumentNo").asText()).isEqualTo(source.receiptNo());
		assertThat(data(get("/api/admin/inventory/batches?batchNo=B-CAND-FROZEN&onlyAvailable=true", admin))
			.get("items")
			.size()).isZero();

		ResponseEntity<String> serials = get("/api/admin/inventory/serials?serialNo=SN-CAND-OK&onlyAvailable=false",
				admin);
		assertOk(serials);
		JsonNode serial = firstItem(serials);
		assertThat(serial.get("id").longValue()).isEqualTo(availableSerialId);
		assertThat(serial.get("warehouseId").longValue()).isEqualTo(fixture.rawWarehouseId());
		assertThat(serial.get("warehouseName").asText()).isEqualTo("原料仓");
		assertThat(serial.get("qualityStatus").asText()).isEqualTo(InventoryQualityStatus.QUALIFIED.name());
		assertThat(serial.get("qualityStatusName").asText()).isEqualTo(InventoryQualityStatus.QUALIFIED.displayName());
		assertThat(serial.get("stockStatus").asText()).isEqualTo("IN_STOCK");
		assertThat(serial.get("stockStatusName").asText()).isEqualTo("在库");
		assertDecimal(serial, "availableQuantity", "1.000000");
		assertThat(serial.get("selectable").booleanValue()).isTrue();
		assertThat(serial.get("disabledReasonCode").isNull()).isTrue();
		assertThat(serial.get("disabledReason").isNull()).isTrue();
		assertThat(serial.get("sourceDocumentNo").asText()).isEqualTo(source.receiptNo());

		JsonNode outboundSerial = firstItem(get(
				"/api/admin/inventory/serials?serialNo=SN-CAND-OUT&onlyAvailable=false", admin));
		assertThat(outboundSerial.get("id").longValue()).isEqualTo(outboundSerialId);
		assertDecimal(outboundSerial, "availableQuantity", "0.000000");
		assertThat(outboundSerial.get("selectable").booleanValue()).isFalse();
		assertThat(outboundSerial.get("disabledReasonCode").asText()).isEqualTo("INVENTORY_TRACKING_NOT_AVAILABLE");
		assertThat(outboundSerial.get("disabledReason").asText()).isEqualTo("序列号不在库");
		assertThat(data(get("/api/admin/inventory/serials?serialNo=SN-CAND-OUT&onlyAvailable=true", admin))
			.get("items")
			.size()).isZero();
	}

	@Test
	void batchCandidateWithMultipleQualityStatusesKeepsTopLevelQualityStatusNull() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long batchId = insertTrackedBatchStock(fixture, source, "B-CAND-MULTI-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "2.000000");
		insertBatchBalance(fixture, batchId, InventoryQualityStatus.PENDING_INSPECTION, "3.000000");

		ResponseEntity<String> batches = get("/api/admin/inventory/batches?batchNo=B-CAND-MULTI&onlyAvailable=false",
				admin);
		assertOk(batches);
		JsonNode batch = firstItem(batches);
		assertThat(batch.get("id").longValue()).isEqualTo(batchId);
		assertThat(batch.get("qualityStatus").isNull()).isTrue();
		assertThat(batch.get("qualityStatusName").isNull()).isTrue();
		assertThat(batch.get("qualityStatusSummary")).hasSize(2);
		assertThat(batch.get("qualityStatusSummary").toString()).contains("QUALIFIED", "PENDING_INSPECTION");
		assertDecimal(batch, "quantityOnHand", "5.000000");
		assertDecimal(batch, "availableQuantity", "2.000000");
	}

	@Test
	void traceMasksSourceCoordinatesAndCollectsRestrictedSummaryWhenSourcePermissionMissing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long batchId = insertTrackedBatchStock(fixture, source, "B-TRACE-PERM-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "2.000000");

		JsonNode adminTrace = data(get("/api/admin/inventory/traces/batches/" + batchId, admin));
		assertThat(adminTrace.get("subject").get("sourceId").longValue()).isEqualTo(source.receiptId());
		assertThat(adminTrace.get("subject").get("sourceLineId").longValue()).isEqualTo(source.receiptLineId());
		assertThat(adminTrace.get("subject").get("sourceDocumentNo").asText()).isEqualTo(source.receiptNo());
		JsonNode adminSource = adminTrace.get("sourceRecords").get(0);
		assertThat(adminSource.get("documentType").asText()).isEqualTo("PURCHASE_RECEIPT");
		assertThat(adminSource.get("documentId").longValue()).isEqualTo(source.receiptId());
		assertThat(adminSource.get("lineId").longValue()).isEqualTo(source.receiptLineId());
		assertThat(adminSource.get("documentNo").asText()).isEqualTo(source.receiptNo());
		assertThat(adminSource.get("permissionRestricted").booleanValue()).isFalse();

		AuthenticatedSession restricted = createInventoryUserAndLogin("inventory-trace-restricted-",
				"INV_TRACE_RESTRICTED_", "追溯受限", List.of("inventory:trace:view"));
		JsonNode restrictedTrace = data(get("/api/admin/inventory/traces/batches/" + batchId, restricted));
		assertThat(restrictedTrace.get("subject").get("sourceId").isNull()).isTrue();
		assertThat(restrictedTrace.get("subject").get("sourceLineId").isNull()).isTrue();
		assertThat(restrictedTrace.get("subject").get("sourceDocumentNo").isNull()).isTrue();
		JsonNode restrictedSource = restrictedTrace.get("sourceRecords").get(0);
		assertThat(restrictedSource.get("documentType").asText()).isEqualTo("PURCHASE_RECEIPT");
		assertThat(restrictedSource.get("documentId").isNull()).isTrue();
		assertThat(restrictedSource.get("lineId").isNull()).isTrue();
		assertThat(restrictedSource.get("documentNo").isNull()).isTrue();
		assertThat(restrictedSource.get("permissionRestricted").booleanValue()).isTrue();
		JsonNode restrictedMovement = restrictedTrace.get("movements").get(0);
		assertThat(restrictedMovement.get("documentId").isNull()).isTrue();
		assertThat(restrictedMovement.get("lineId").isNull()).isTrue();
		assertThat(restrictedMovement.get("documentNo").isNull()).isTrue();
		assertThat(restrictedMovement.get("permissionRestricted").booleanValue()).isTrue();
		assertThat(restrictedTrace.get("restrictedSources").size()).isEqualTo(2);
		assertThat(restrictedTrace.get("restrictedSources").get(0).get("permissionRestricted").booleanValue()).isTrue();
	}

	@Test
	void traceMasksSalesOrderAndProductionWorkOrderReservationsWhenSourcePermissionMissing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long batchId = insertTrackedBatchStock(fixture, source, "B-TRACE-RESV-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "5.000000");
		long salesReservationId = insertReservation(fixture, "RESERVATION", "SALES_ORDER",
				1_810_000L + SEQUENCE.incrementAndGet(), 1_811_000L + SEQUENCE.incrementAndGet(), "SO-TRACE-RESV",
				"1.000000");
		long productionReservationId = insertReservation(fixture, "OCCUPATION", "PRODUCTION_WORK_ORDER",
				1_820_000L + SEQUENCE.incrementAndGet(), 1_821_000L + SEQUENCE.incrementAndGet(), "WO-TRACE-RESV",
				"1.000000");
		this.jdbcTemplate.update("update inv_stock_reservation set batch_id = ? where id in (?, ?)", batchId,
				salesReservationId, productionReservationId);

		AuthenticatedSession restricted = createInventoryUserAndLogin("inventory-trace-reservation-restricted-",
				"INV_TRACE_RESERVATION_RESTRICTED_", "追溯预留受限", List.of("inventory:trace:view",
						"procurement:receipt:view"));
		JsonNode trace = data(get("/api/admin/inventory/traces/batches/" + batchId, restricted));

		assertThat(trace.get("activeReservations").size()).isEqualTo(2);
		for (JsonNode reservation : trace.get("activeReservations")) {
			assertThat(reservation.get("documentType").asText())
				.isIn("SALES_ORDER", "PRODUCTION_WORK_ORDER");
			assertThat(reservation.get("documentId").isNull()).isTrue();
			assertThat(reservation.get("lineId").isNull()).isTrue();
			assertThat(reservation.get("documentNo").isNull()).isTrue();
			assertThat(reservation.get("permissionRestricted").booleanValue()).isTrue();
		}
		assertThat(trace.get("restrictedSources").toString()).contains("SALES_ORDER", "PRODUCTION_WORK_ORDER");
	}

	@Test
	void traceKeepsQualityStatusTransferOnlyInQualityEvents() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long batchId = insertTrackedBatchStock(fixture, source, "B-TRACE-QT-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "5.000000");
		long sourceId = 1_830_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 1_831_000L + SEQUENCE.incrementAndGet();
		long outMovementId = insertTrackedMovement(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "1.000000", batchId, null, "QUALITY_STATUS_TRANSFER", sourceId, sourceLineId,
				InventoryQualityStatus.QUALIFIED);
		this.jdbcTemplate.update("""
				update inv_stock_movement
				set movement_type = 'QUALITY_STATUS_TRANSFER', direction = 'OUT'
				where id = ?
				""", outMovementId);
		long inMovementId = insertTrackedMovement(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "1.000000", batchId, null, "QUALITY_STATUS_TRANSFER", sourceId,
				sourceLineId + 1, InventoryQualityStatus.PENDING_INSPECTION);
		this.jdbcTemplate.update("""
				update inv_stock_movement
				set movement_type = 'QUALITY_STATUS_TRANSFER', direction = 'IN'
				where id = ?
				""", inMovementId);

		JsonNode trace = data(get("/api/admin/inventory/traces/batches/" + batchId, admin));

		assertThat(trace.get("qualityEvents").size()).isEqualTo(2);
		assertThat(trace.get("qualityEvents").toString()).contains("QUALITY_STATUS_TRANSFER");
		assertThat(trace.get("outboundRecords").toString()).doesNotContain("QUALITY_STATUS_TRANSFER");
	}

	@Test
	void postingServiceRejectsNonQualifiedOrdinaryOutbound() {
		InventoryFixture fixture = fixture();
		long sourceId = 850_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 860_000L + SEQUENCE.incrementAndGet();

		InventoryPostingService.PostingRequest request = new InventoryPostingService.PostingRequest(
				InventoryMovementType.SALES_SHIPMENT, InventoryDirection.OUT, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("1.000000"),
				InventoryQualityStatus.PENDING_INSPECTION, "SALES_SHIPMENT", sourceId, sourceLineId,
				LocalDate.of(2026, 7, 10), "销售出库", null, "admin");

		assertThatThrownBy(() -> this.inventoryPostingService.post(request))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_NON_QUALIFIED_NOT_AVAILABLE));
		assertThat(movementCountBySource("SALES_SHIPMENT", sourceLineId)).isZero();
	}

	@Test
	void postingServiceReturnsQualityBalanceErrorWhenQualifiedStockIsInsufficientButOtherStatusesExist() {
		InventoryFixture fixture = fixture();
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "5.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		long sourceId = 865_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 866_000L + SEQUENCE.incrementAndGet();

		assertThatThrownBy(() -> this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.SALES_SHIPMENT,
						InventoryDirection.OUT, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						new BigDecimal("1.000000"), InventoryQualityStatus.QUALIFIED, "SALES_SHIPMENT", sourceId,
						sourceLineId, LocalDate.of(2026, 7, 10), "销售出库", null, "admin")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH));
		assertThat(movementCountBySource("SALES_SHIPMENT", sourceLineId)).isZero();
	}

	@Test
	void postingServiceTransfersQualityStatusWithoutChangingTotalQuantity() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "5.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		long sourceId = 870_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 880_000L + SEQUENCE.incrementAndGet();

		this.inventoryPostingService.transferQualityStatus(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), InventoryQualityStatus.PENDING_INSPECTION, InventoryQualityStatus.QUALIFIED,
				new BigDecimal("2.000000"), "QUALITY_STATUS_TRANSFER", sourceId, sourceLineId, LocalDate.now(),
				"质量状态转换", "待检转合格", "tester");

		ResponseEntity<String> balances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.rawMaterialId(), admin);
		assertOk(balances);
		JsonNode balance = firstItem(balances);
		assertDecimal(balance, "quantityOnHand", "5.000000");
		assertDecimal(balance, "availableQuantity", "2.000000");
		assertDecimal(balance, "pendingInspectionQuantity", "3.000000");
		assertDecimal(balance, "qualifiedQuantity", "2.000000");
		assertThat(movementCountBySource("QUALITY_STATUS_TRANSFER", sourceLineId * 10
				+ InventoryQualityStatus.PENDING_INSPECTION.ordinal())).isOne();
		assertThat(movementCountBySource("QUALITY_STATUS_TRANSFER", sourceLineId * 10
				+ InventoryQualityStatus.QUALIFIED.ordinal())).isOne();
	}

	@Test
	void postingServiceRejectsUnsupportedQualityStatusTransfer() {
		InventoryFixture fixture = fixture();
		postInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1.000000",
				InventoryQualityStatus.REJECTED);
		long sourceId = 885_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 886_000L + SEQUENCE.incrementAndGet();

		assertThatThrownBy(() -> this.inventoryPostingService.transferQualityStatus(fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), InventoryQualityStatus.REJECTED,
				InventoryQualityStatus.QUALIFIED, new BigDecimal("1.000000"), "QUALITY_STATUS_TRANSFER", sourceId,
				sourceLineId, LocalDate.now(), "质量状态转换", "不合格转合格", "tester"))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.QUALITY_STATUS_TRANSITION_INVALID));
		assertThat(movementCountBySource("QUALITY_STATUS_TRANSFER", sourceLineId * 10
				+ InventoryQualityStatus.REJECTED.ordinal())).isZero();
		assertThat(movementCountBySource("QUALITY_STATUS_TRANSFER", sourceLineId * 10
				+ InventoryQualityStatus.QUALIFIED.ordinal())).isZero();
	}

	@Test
	void postingServiceRejectsDuplicateSourceLineAndKeepsBalanceUnchanged() {
		InventoryFixture fixture = fixture();
		long sourceId = 900_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 910_000L + SEQUENCE.incrementAndGet();

		InventoryPostingService.PostingResult result = this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.ADJUSTMENT_INCREASE,
						InventoryDirection.IN, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						new BigDecimal("7.000000"), "INVENTORY_DOCUMENT", sourceId, sourceLineId, LocalDate.now(),
						"共享过账测试", "首次过账", "tester"));

		assertDecimal(result.beforeQuantity(), "0");
		assertDecimal(result.afterQuantity(), "7.000000");
		assertDecimal(balanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId()), "7.000000");
		assertThat(movementCountBySource("INVENTORY_DOCUMENT", sourceLineId)).isOne();

		assertThatThrownBy(() -> this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.ADJUSTMENT_INCREASE,
						InventoryDirection.IN, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						new BigDecimal("2.000000"), "INVENTORY_DOCUMENT", sourceId, sourceLineId, LocalDate.now(),
						"共享过账重复测试", "重复过账", "tester")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.INVENTORY_MOVEMENT_SOURCE_DUPLICATED));
		assertDecimal(balanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId()), "7.000000");
		assertThat(movementCountBySource("INVENTORY_DOCUMENT", sourceLineId)).isOne();
	}

	@Test
	void postingServiceMapsSalesShipmentSourceErrors() {
		InventoryFixture fixture = fixture();
		long stockSourceId = 920_000L + SEQUENCE.incrementAndGet();
		long stockSourceLineId = 930_000L + SEQUENCE.incrementAndGet();

		assertThatThrownBy(() -> this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.ADJUSTMENT_DECREASE,
						InventoryDirection.OUT, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						new BigDecimal("1.000000"), "SALES_SHIPMENT", stockSourceId, stockSourceLineId,
						LocalDate.now(), "销售出库库存不足测试", "库存不足", "tester")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode().code()).isEqualTo("SALES_STOCK_NOT_ENOUGH"));
		assertThat(movementCountBySource("SALES_SHIPMENT", stockSourceLineId)).isZero();

		long duplicateSourceId = 940_000L + SEQUENCE.incrementAndGet();
		long duplicateSourceLineId = 950_000L + SEQUENCE.incrementAndGet();
		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("2.000000"), "SALES_SHIPMENT",
				duplicateSourceId, duplicateSourceLineId, LocalDate.now(), "销售出库来源重复测试", "首次过账", "tester"));

		assertThatThrownBy(() -> this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.ADJUSTMENT_INCREASE,
						InventoryDirection.IN, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						new BigDecimal("1.000000"), "SALES_SHIPMENT", duplicateSourceId, duplicateSourceLineId,
						LocalDate.now(), "销售出库来源重复测试", "重复过账", "tester")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode().code())
						.isEqualTo("SALES_MOVEMENT_SOURCE_DUPLICATED"));
		assertThat(movementCountBySource("SALES_SHIPMENT", duplicateSourceLineId)).isOne();
	}

	@Test
	void postingServiceSupportsSalesShipmentMovementTypeAndMovementNoPrefix() {
		InventoryFixture fixture = fixture();
		long openingSourceId = 960_000L + SEQUENCE.incrementAndGet();
		long openingSourceLineId = 970_000L + SEQUENCE.incrementAndGet();
		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, fixture.finishedWarehouseId(),
				fixture.finishedMaterialId(), fixture.eachUnitId(), new BigDecimal("6.000000"), "INVENTORY_DOCUMENT",
				openingSourceId, openingSourceLineId, LocalDate.now(), "销售出库前置库存", "前置库存", "tester"));

		InventoryMovementType salesShipment = InventoryMovementType.valueOf("SALES_SHIPMENT");
		long salesSourceId = 980_000L + SEQUENCE.incrementAndGet();
		long salesSourceLineId = 990_000L + SEQUENCE.incrementAndGet();
		InventoryPostingService.PostingResult result = this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(salesShipment, InventoryDirection.OUT,
						fixture.finishedWarehouseId(), fixture.finishedMaterialId(), fixture.eachUnitId(),
						new BigDecimal("2.500000"), "SALES_SHIPMENT", salesSourceId, salesSourceLineId,
						LocalDate.now(), "销售出库", "销售出库过账", "tester"));

		assertDecimal(result.beforeQuantity(), "6.000000");
		assertDecimal(result.afterQuantity(), "3.500000");
		assertDecimal(balanceQuantity(fixture.finishedWarehouseId(), fixture.finishedMaterialId()), "3.500000");
		assertThat(movementNoBySource("SALES_SHIPMENT", salesSourceLineId)).startsWith("SAL-SHP-MOV-");
		MovementRow movement = movementForSource("SALES_SHIPMENT", salesSourceLineId);
		assertThat(movement.movementType()).isEqualTo("SALES_SHIPMENT");
		assertThat(movement.direction()).isEqualTo("OUT");
		assertDecimal(movement.quantity(), "2.500000");
	}

	@Test
	void postingServiceKeepsExistingSourceErrorMappings() {
		InventoryFixture fixture = fixture();
		long procurementSourceId = 1_000_000L + SEQUENCE.incrementAndGet();
		long procurementSourceLineId = 1_010_000L + SEQUENCE.incrementAndGet();
		this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.PURCHASE_RECEIPT, InventoryDirection.IN, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("1.000000"), "PURCHASE_RECEIPT",
				procurementSourceId, procurementSourceLineId, LocalDate.now(), "采购来源重复基线", "首次过账", "tester"));

		assertThatThrownBy(() -> this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.PURCHASE_RECEIPT,
						InventoryDirection.IN, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						new BigDecimal("1.000000"), "PURCHASE_RECEIPT", procurementSourceId,
						procurementSourceLineId, LocalDate.now(), "采购来源重复基线", "重复过账", "tester")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.PROCUREMENT_MOVEMENT_SOURCE_DUPLICATED));

		long productionSourceId = 1_020_000L + SEQUENCE.incrementAndGet();
		long productionSourceLineId = 1_030_000L + SEQUENCE.incrementAndGet();
		assertThatThrownBy(() -> this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.PRODUCTION_ISSUE,
						InventoryDirection.OUT, fixture.finishedWarehouseId(), fixture.semiMaterialId(),
						fixture.eachUnitId(), new BigDecimal("1.000000"), "PRODUCTION_MATERIAL_ISSUE", productionSourceId,
						productionSourceLineId, LocalDate.now(), "生产库存不足基线", "库存不足", "tester")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode())
						.isEqualTo(ApiErrorCode.PRODUCTION_STOCK_NOT_ENOUGH));

		long inventorySourceId = 1_040_000L + SEQUENCE.incrementAndGet();
		long inventorySourceLineId = 1_050_000L + SEQUENCE.incrementAndGet();
		assertThatThrownBy(() -> this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.ADJUSTMENT_DECREASE,
						InventoryDirection.OUT, fixture.rawWarehouseId(), fixture.auxiliaryMaterialId(),
						fixture.meterUnitId(), new BigDecimal("1.000000"), "INVENTORY_DOCUMENT", inventorySourceId,
						inventorySourceLineId, LocalDate.now(), "库存不足基线", "库存不足", "tester")))
			.isInstanceOfSatisfying(BusinessException.class,
					(exception) -> assertThat(exception.errorCode()).isEqualTo(ApiErrorCode.INVENTORY_STOCK_NOT_ENOUGH));
	}

	@Test
	void adjustmentDocumentsIncreaseAndDecreaseStock() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		createAndPostOpening(admin, fixture, "20.000000");

		long increaseDocumentId = createDocumentId(admin,
				adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"5.000000", "INCREASE", "库存调增"));
		assertOk(postDocument(admin, increaseDocumentId));
		MovementRow increase = movementForDocument(increaseDocumentId);
		assertThat(increase.movementType()).isEqualTo("ADJUSTMENT_INCREASE");
		assertThat(increase.direction()).isEqualTo("IN");
		assertThat(increase.sourceLineId()).isEqualTo(lineIdForDocument(increaseDocumentId));
		assertDecimal(increase.beforeQuantity(), "20.000000");
		assertDecimal(increase.quantity(), "5.000000");
		assertDecimal(increase.afterQuantity(), "25.000000");
		assertDecimal(balanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId()), "25.000000");

		long decreaseDocumentId = createDocumentId(admin,
				adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"3.000000", "DECREASE", "库存调减"));
		assertOk(postDocument(admin, decreaseDocumentId));
		MovementRow decrease = movementForDocument(decreaseDocumentId);
		assertThat(decrease.movementType()).isEqualTo("ADJUSTMENT_DECREASE");
		assertThat(decrease.direction()).isEqualTo("OUT");
		assertThat(decrease.sourceLineId()).isEqualTo(lineIdForDocument(decreaseDocumentId));
		assertDecimal(decrease.beforeQuantity(), "25.000000");
		assertDecimal(decrease.quantity(), "3.000000");
		assertDecimal(decrease.afterQuantity(), "22.000000");
		assertDecimal(balanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId()), "22.000000");
	}

	@Test
	void invalidDocumentRequestsReturnControlledInventoryErrors() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();

		assertError(createDocument(admin, documentPayload("OPENING", "空明细", null, List.of())), HttpStatus.BAD_REQUEST,
				"INVENTORY_DOCUMENT_EMPTY_LINES");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "0",
						"数量为零", null, null)), HttpStatus.BAD_REQUEST, "INVENTORY_QUANTITY_INVALID");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "-1",
						"数量为负", null, null)), HttpStatus.BAD_REQUEST, "INVENTORY_QUANTITY_INVALID");

		List<Map<String, Object>> duplicateLines = List.of(
				line(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1", null, null),
				line(2, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2", null, null));
		assertError(createDocument(admin, documentPayload("OPENING", "重复明细", null, duplicateLines)),
				HttpStatus.CONFLICT, "INVENTORY_DOCUMENT_DUPLICATE_LINE");

		assertError(createDocument(admin,
				openingPayload(fixture, fixture.disabledWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"1", "停用仓库", null, null)), HttpStatus.BAD_REQUEST, "INVENTORY_WAREHOUSE_INVALID");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.disabledMaterialId(), fixture.kgUnitId(),
						"1", "停用物料", null, null)), HttpStatus.BAD_REQUEST, "INVENTORY_MATERIAL_INVALID");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.eachUnitId(), "1",
						"非基本单位", null, null)), HttpStatus.BAD_REQUEST, "INVENTORY_UNIT_INVALID");
		assertError(createDocument(admin,
				documentPayload("ADJUSTMENT", "调整缺少方向", null,
						List.of(line(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1",
								null, null)))),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
	}

	@Test
	void queryFiltersAndPaginationReturnFilteredInventoryData() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		LocalDate businessDate = LocalDate.now();
		long rawOpeningId = createAndPostOpening(admin, fixture, fixture.rawMaterialId(), fixture.kgUnitId(),
				"6.000000", "FILTER_OPEN_RAW_" + fixture.rawMaterialId(), businessDate);
		createAndPostOpening(admin, fixture, fixture.semiMaterialId(), fixture.eachUnitId(), "4.000000",
				"FILTER_OPEN_SEMI_" + fixture.semiMaterialId(), businessDate);
		createAndPostOpening(admin, fixture, fixture.finishedWarehouseId(), fixture.finishedMaterialId(),
				fixture.eachUnitId(), "2.000000", "FILTER_OPEN_FIN_" + fixture.finishedMaterialId(), businessDate);
		String rawMaterialCode = materialCode(fixture.rawMaterialId());
		String adjustmentReason = "FILTER_ADJ_" + rawOpeningId;
		long adjustmentId = createDocumentId(admin, withBusinessDate(
				adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"1.000000", "INCREASE", adjustmentReason),
				businessDate));
		assertOk(postDocument(admin, adjustmentId));

		ResponseEntity<String> keywordBalances = get("/api/admin/inventory/balances?keyword=" + rawMaterialCode,
				admin);
		assertOk(keywordBalances);
		assertThat(items(keywordBalances)).isNotEmpty().allSatisfy((item) -> {
			assertThat(item.get("materialCode").asText()).isEqualTo(rawMaterialCode);
			assertThat(item.get("materialId").longValue()).isEqualTo(fixture.rawMaterialId());
		});

		ResponseEntity<String> materialTypeBalances = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&materialType=RAW_MATERIAL", admin);
		assertOk(materialTypeBalances);
		assertThat(items(materialTypeBalances)).hasSize(1).allSatisfy((item) -> {
			assertThat(item.get("materialId").longValue()).isEqualTo(fixture.rawMaterialId());
			assertThat(item.get("materialType").asText()).isEqualTo("RAW_MATERIAL");
		});

		ResponseEntity<String> firstBalancePage = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&onlyPositive=true&page=1&pageSize=1", admin);
		ResponseEntity<String> secondBalancePage = get("/api/admin/inventory/balances?warehouseId="
				+ fixture.rawWarehouseId() + "&onlyPositive=true&page=2&pageSize=1", admin);
		assertOk(firstBalancePage);
		assertOk(secondBalancePage);
		assertThat(data(firstBalancePage).get("pageSize").intValue()).isOne();
		assertThat(data(firstBalancePage).get("total").longValue()).isEqualTo(2L);
		assertThat(items(firstBalancePage)).hasSize(1)
			.allSatisfy((item) -> assertThat(decimal(item, "quantityOnHand").compareTo(BigDecimal.ZERO)).isPositive());
		assertThat(items(secondBalancePage)).hasSize(1)
			.allSatisfy((item) -> assertThat(decimal(item, "quantityOnHand").compareTo(BigDecimal.ZERO)).isPositive());
		assertThat(firstItem(firstBalancePage).get("id").longValue())
			.isNotEqualTo(firstItem(secondBalancePage).get("id").longValue());

		ResponseEntity<String> movements = get("/api/admin/inventory/movements?materialId=" + fixture.rawMaterialId()
				+ "&movementType=ADJUSTMENT_INCREASE&direction=IN&dateFrom=" + businessDate + "&dateTo="
				+ businessDate, admin);
		assertOk(movements);
		assertThat(items(movements)).hasSize(1).allSatisfy((item) -> {
			assertThat(item.get("sourceId").longValue()).isEqualTo(adjustmentId);
			assertThat(item.get("movementType").asText()).isEqualTo("ADJUSTMENT_INCREASE");
			assertThat(item.get("direction").asText()).isEqualTo("IN");
			assertThat(item.get("businessDate").asText()).isEqualTo(businessDate.toString());
		});

		ResponseEntity<String> documents = get("/api/admin/inventory/documents?documentType=ADJUSTMENT&status=POSTED"
				+ "&dateFrom=" + businessDate + "&dateTo=" + businessDate + "&keyword=" + adjustmentReason, admin);
		assertOk(documents);
		assertThat(items(documents)).hasSize(1).allSatisfy((item) -> {
			assertThat(item.get("id").longValue()).isEqualTo(adjustmentId);
			assertThat(item.get("documentType").asText()).isEqualTo("ADJUSTMENT");
			assertThat(item.get("status").asText()).isEqualTo("POSTED");
			assertThat(item.get("reason").asText()).isEqualTo(adjustmentReason);
		});
	}

	@Test
	void contractErrorsReturnInventoryCodes() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();

		assertError(get("/api/admin/inventory/documents/999999999", admin), HttpStatus.NOT_FOUND,
				"INVENTORY_DOCUMENT_NOT_FOUND");
		assertError(createDocument(admin,
				documentPayload("INVALID_TYPE", "非法类型", null,
						List.of(line(1, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1",
								null, null)))),
				HttpStatus.BAD_REQUEST, "INVENTORY_DOCUMENT_TYPE_INVALID");
	}

	@Test
	void postingStateErrorsKeepStockAndMovementsConsistent() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		long openingDocumentId = createAndPostOpening(admin, fixture, "5.000000");

		long duplicateOpeningId = createDocumentId(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1",
						"重复期初", null, null));
		assertError(postDocument(admin, duplicateOpeningId), HttpStatus.CONFLICT, "INVENTORY_OPENING_EXISTS");

		long overdrawDocumentId = createDocumentId(admin,
				adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"10.000000", "DECREASE", "超额调减"));
		BigDecimal beforeQuantity = balanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId());
		long beforeMovementCount = movementCount(fixture.rawWarehouseId(), fixture.rawMaterialId());
		assertError(postDocument(admin, overdrawDocumentId), HttpStatus.CONFLICT, "INVENTORY_STOCK_NOT_ENOUGH");
		assertThat(balanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId()).compareTo(beforeQuantity))
			.isZero();
		assertThat(movementCount(fixture.rawWarehouseId(), fixture.rawMaterialId())).isEqualTo(beforeMovementCount);

		assertError(updateDocument(admin, openingDocumentId,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "6",
						"更新已过账", null, null)), HttpStatus.CONFLICT, "INVENTORY_DOCUMENT_POSTED_IMMUTABLE");
		assertError(postDocument(admin, openingDocumentId), HttpStatus.CONFLICT, "INVENTORY_DUPLICATE_POST");
	}

	@Test
	void authenticationAuthorizationAndCsrfAreEnforced() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		long postedDocumentId = createAndPostOpening(admin, fixture, "8.000000");
		long draftDocumentId = createDocumentId(admin,
				adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"1.000000", "INCREASE", "草稿调整"));
		AuthenticatedSession reader = createReadOnlyUserAndLogin();
		AuthenticatedSession noInventoryUser = createNoInventoryUserAndLogin();

		assertUnauthorized(get("/api/admin/inventory/balances", null));
		assertUnauthorized(get("/api/admin/inventory/movements", null));
		assertUnauthorized(get("/api/admin/inventory/documents", null));
		assertUnauthorized(get("/api/admin/inventory/documents/" + postedDocumentId, null));

		assertOk(get("/api/admin/inventory/balances", reader));
		assertOk(get("/api/admin/inventory/reservations", reader));
		assertOk(get("/api/admin/inventory/movements", reader));
		assertOk(get("/api/admin/inventory/documents", reader));
		assertOk(get("/api/admin/inventory/documents/" + postedDocumentId, reader));

		Map<String, Object> adjustment = adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "1", "INCREASE", "只读写入");
		assertForbidden(createDocument(reader, adjustment));
		assertForbidden(updateDocument(reader, draftDocumentId, adjustment));
		assertForbidden(postDocument(reader, draftDocumentId));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/inventory/documents", adjustment, admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.PUT, "/api/admin/inventory/documents/" + draftDocumentId,
				adjustment, admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.PUT,
				"/api/admin/inventory/documents/" + draftDocumentId + "/post", null, admin));

		assertForbidden(get("/api/admin/inventory/balances", noInventoryUser));
		assertForbidden(get("/api/admin/inventory/reservations", noInventoryUser));
		assertForbidden(get("/api/admin/inventory/movements", noInventoryUser));
		assertForbidden(get("/api/admin/inventory/documents", noInventoryUser));
		assertForbidden(get("/api/admin/inventory/documents/" + postedDocumentId, noInventoryUser));
		assertForbidden(createDocument(noInventoryUser, adjustment));
		assertForbidden(updateDocument(noInventoryUser, draftDocumentId, adjustment));
		assertForbidden(postDocument(noInventoryUser, draftDocumentId));
	}

	@Test
	void concurrentDecreasePostingKeepsBalanceNonNegative() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		createAndPostOpening(admin, fixture, "5.000000");
		long firstDocumentId = createDocumentId(admin,
				adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"4.000000", "DECREASE", "并发调减一"));
		long secondDocumentId = createDocumentId(admin,
				adjustmentPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"4.000000", "DECREASE", "并发调减二"));
		AuthenticatedSession firstSession = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession secondSession = login("admin", ADMIN_PASSWORD);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<ResponseEntity<String>> first = executorService
				.submit(() -> postAfterStart(firstDocumentId, firstSession, start));
			Future<ResponseEntity<String>> second = executorService
				.submit(() -> postAfterStart(secondDocumentId, secondSession, start));
			start.countDown();
			List<ResponseEntity<String>> responses = List.of(response(first), response(second));

			long successCount = responses.stream().filter((response) -> response.getStatusCode() == HttpStatus.OK).count();
			long failureCount = responses.stream().filter((response) -> response.getStatusCode().isError()).count();
			assertThat(successCount).isOne();
			assertThat(failureCount).isOne();
			ResponseEntity<String> failed = responses.stream()
				.filter((response) -> response.getStatusCode().isError())
				.findFirst()
				.orElseThrow();
			assertThat(code(failed)).isEqualTo("INVENTORY_STOCK_NOT_ENOUGH");
			assertDecimal(balanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId()), "1.000000");
			assertThat(decreaseMovementCount(firstDocumentId, secondDocumentId)).isOne();
		}
		finally {
			executorService.shutdownNow();
		}
	}

	@Test
	void validatesPrecisionLengthsAndGeneratesUniqueDocumentNumbersQuickly() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();

		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"1.0000001", "超精度数量", null, null)), HttpStatus.BAD_REQUEST,
				"INVENTORY_QUANTITY_INVALID");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
						"1000000000000", "超整数位数量", null, null)), HttpStatus.BAD_REQUEST,
				"INVENTORY_QUANTITY_INVALID");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1",
						"x".repeat(201), null, null)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1",
						"超长主表备注", "x".repeat(501), null)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		assertError(createDocument(admin,
				openingPayload(fixture, fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "1",
						"超长明细备注", null, "x".repeat(501))), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		List<String> documentNos = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ResponseEntity<String> response = createDocument(admin,
					openingPayload(fixture, fixture.finishedWarehouseId(), fixture.finishedMaterialId(),
							fixture.eachUnitId(), "1", "快速创建" + i, null, null));
			assertOk(response);
			documentNos.add(data(response).get("documentNo").asText());
		}
		assertThat(documentNos).hasSize(20).doesNotHaveDuplicates();
		assertThat(documentNos).allMatch((documentNo) -> documentNo.startsWith("INV-OPEN-"));
	}

	private ResponseEntity<String> postAfterStart(long documentId, AuthenticatedSession session, CountDownLatch start)
			throws InterruptedException {
		start.await();
		return postDocument(session, documentId);
	}

	private ResponseEntity<String> response(Future<ResponseEntity<String>> future)
			throws InterruptedException, ExecutionException, TimeoutException {
		return future.get(10, TimeUnit.SECONDS);
	}

	private InventoryPostingService.PostingResult postBatchOutbound(InventoryFixture fixture, long batchId,
			String reason) {
		return this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.SALES_SHIPMENT, InventoryDirection.OUT, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal("2.000000"),
				InventoryQualityStatus.QUALIFIED, "STAGE023_BATCH_CONCURRENT", nextSourceId(), nextSourceLineId(),
				LocalDate.now(), reason, null, "tester", false, batchId, null));
	}

	@Test
	void lockedPeriodRejectsInventoryDocumentPost() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = fixture();
		LocalDate date = LocalDate.of(2090, 7, 10);
		long id = createDocumentId(admin, withBusinessDate(openingPayload(fixture, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), "1", "期间锁定测试", null, null), date));
		lockPeriod(date);
		assertError(postDocument(admin, id), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at) values (?, ?, ?, ?, 'LOCKED', now(), now())",
				"LOCK-" + date, "锁定期间", date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
	}

	private long createAndPostOpening(AuthenticatedSession admin, InventoryFixture fixture, String quantity)
			throws Exception {
		return createAndPostOpening(admin, fixture, fixture.rawMaterialId(), fixture.kgUnitId(), quantity, "期初库存",
				LocalDate.now());
	}

	private long createAndPostOpening(AuthenticatedSession admin, InventoryFixture fixture, long materialId, long unitId,
			String quantity, String reason, LocalDate businessDate) throws Exception {
		return createAndPostOpening(admin, fixture, fixture.rawWarehouseId(), materialId, unitId, quantity, reason,
				businessDate);
	}

	private long createAndPostOpening(AuthenticatedSession admin, InventoryFixture fixture, long warehouseId,
			long materialId, long unitId, String quantity, String reason, LocalDate businessDate) throws Exception {
		long documentId = createDocumentId(admin,
				withBusinessDate(openingPayload(fixture, warehouseId, materialId, unitId, quantity, reason, null, null),
						businessDate));
		assertOk(postDocument(admin, documentId));
		return documentId;
	}

	private InventoryPostingService.PostingResult postInventory(long warehouseId, long materialId, long unitId,
			String quantity, InventoryQualityStatus qualityStatus) {
		long sourceId = 1_200_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 1_210_000L + SEQUENCE.incrementAndGet();
		return this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, warehouseId, materialId, unitId,
				new BigDecimal(quantity), qualityStatus, "INVENTORY_DOCUMENT", sourceId, sourceLineId, LocalDate.now(),
				"质量状态库存测试", "质量状态库存测试", "tester"));
	}

	private long createDocumentId(AuthenticatedSession session, Map<String, Object> payload) throws Exception {
		ResponseEntity<String> response = createDocument(session, payload);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private Map<String, Object> openingPayload(InventoryFixture fixture, long warehouseId, long materialId, Long unitId,
			String quantity, String reason, String remark, String lineRemark) {
		return documentPayload("OPENING", reason, remark,
				List.of(line(1, warehouseId, materialId, unitId, quantity, null, lineRemark)));
	}

	private Map<String, Object> adjustmentPayload(InventoryFixture fixture, long warehouseId, long materialId,
			Long unitId, String quantity, String adjustmentDirection, String reason) {
		return documentPayload("ADJUSTMENT", reason, null,
				List.of(line(1, warehouseId, materialId, unitId, quantity, adjustmentDirection, null)));
	}

	private Map<String, Object> documentPayload(String documentType, String reason, String remark,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("documentType", documentType);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("reason", reason);
		if (remark != null) {
			payload.put("remark", remark);
		}
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> withBusinessDate(Map<String, Object> payload, LocalDate businessDate) {
		payload.put("businessDate", businessDate.toString());
		return payload;
	}

	private Map<String, Object> line(int lineNo, long warehouseId, long materialId, Long unitId, String quantity,
			String adjustmentDirection, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("lineNo", lineNo);
		payload.put("warehouseId", warehouseId);
		payload.put("materialId", materialId);
		if (unitId != null) {
			payload.put("unitId", unitId);
		}
		payload.put("quantity", new BigDecimal(quantity));
		if (adjustmentDirection != null) {
			payload.put("adjustmentDirection", adjustmentDirection);
		}
		if (remark != null) {
			payload.put("remark", remark);
		}
		return payload;
	}

	private Map<String, Object> pricedLine(int lineNo, long warehouseId, long materialId, Long unitId,
			String quantity, String adjustmentDirection, String unitPrice) {
		Map<String, Object> payload = line(lineNo, warehouseId, materialId, unitId, quantity, adjustmentDirection,
				null);
		if (unitPrice != null) {
			payload.put("unitPrice", unitPrice);
		}
		return payload;
	}

	private InventoryFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long eachUnitId = insertUnit("INV_EACH_" + suffix, "个");
		long kgUnitId = insertUnit("INV_KG_" + suffix, "千克");
		long meterUnitId = insertUnit("INV_M_" + suffix, "米");
		long categoryId = insertMaterialCategory("INV_CAT_" + suffix);
		long rawWarehouseId = insertWarehouse("INV_RAW_" + suffix, "原料仓", "ENABLED");
		long finishedWarehouseId = insertWarehouse("INV_FIN_" + suffix, "成品仓", "ENABLED");
		long disabledWarehouseId = insertWarehouse("INV_OFF_" + suffix, "停用仓", "DISABLED");
		long finishedMaterialId = insertMaterial("INV_FG_" + suffix, "成品 A", "FINISHED_GOOD", "SELF_MADE",
				categoryId, eachUnitId, "ENABLED");
		long semiMaterialId = insertMaterial("INV_SEMI_" + suffix, "半成品 B", "SEMI_FINISHED", "SELF_MADE",
				categoryId, eachUnitId, "ENABLED");
		long rawMaterialId = insertMaterial("INV_RAW_M_" + suffix, "原材料 X", "RAW_MATERIAL", "PURCHASED",
				categoryId, kgUnitId, "ENABLED");
		long auxiliaryMaterialId = insertMaterial("INV_AUX_" + suffix, "辅料 Z", "AUXILIARY", "PURCHASED",
				categoryId, meterUnitId, "ENABLED");
		long disabledMaterialId = insertMaterial("INV_OFF_M_" + suffix, "停用物料 T", "RAW_MATERIAL", "PURCHASED",
				categoryId, kgUnitId, "DISABLED");
		return new InventoryFixture(eachUnitId, kgUnitId, meterUnitId, categoryId, rawWarehouseId, finishedWarehouseId,
				disabledWarehouseId, finishedMaterialId, semiMaterialId, rawMaterialId, auxiliaryMaterialId,
				disabledMaterialId);
	}

	private long insertUnit(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertWarehouse(String code, String name, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, status);
	}

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "库存分类" + code);
	}

	private long insertMaterial(String code, String name, String materialType, String sourceType, long categoryId,
			long unitId, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, material_type, source_type, category_id, unit_id, status,
					created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, materialType, sourceType, categoryId, unitId, status);
	}

	private void markMaterialValued(long materialId) {
		this.jdbcTemplate.update("""
				update mst_material
				set cost_category = 'DIRECT_MATERIAL',
				    inventory_valuation_category = 'VALUATED_MATERIAL',
				    inventory_value_enabled = true,
				    project_cost_enabled = true,
				    cost_remark = '023 库存估值测试'
				where id = ?
				""", materialId);
	}

	private PurchaseReceiptFact createPostedPurchaseReceipt(InventoryFixture fixture, long supplierId, String quantity,
			String unitPrice, String remark) {
		CurrentUser operator = backendOperator();
		long orderId = insertConfirmedPurchaseOrder(fixture, supplierId, quantity, unitPrice, remark);
		ProcurementAdminService.PurchaseReceiptDetailResponse receipt = this.procurementAdminService.createReceipt(
				orderId,
				new ProcurementAdminService.PurchaseReceiptRequest(fixture.rawWarehouseId(), LocalDate.now(), remark,
						List.of(new ProcurementAdminService.PurchaseReceiptLineRequest(1,
								purchaseOrderLineId(orderId), fixture.rawMaterialId(), fixture.kgUnitId(),
								new BigDecimal(quantity), remark, List.of()))),
				operator, request());
		ProcurementAdminService.PurchaseReceiptDetailResponse posted = this.procurementAdminService
			.postReceipt(receipt.id(), operator, request());
		return new PurchaseReceiptFact(posted.id(), posted.receiptNo(), posted.supplierId(),
				posted.lines().getFirst().id(), posted.lines().getFirst().orderLineId(), new BigDecimal(quantity),
				new BigDecimal(unitPrice));
	}

	private long insertConfirmedPurchaseOrder(InventoryFixture fixture, long supplierId, String quantity,
			String unitPrice, String remark) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal orderQuantity = new BigDecimal(quantity);
		BigDecimal price = new BigDecimal(unitPrice);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "INV-PO-" + suffix, supplierId, LocalDate.now(), LocalDate.now(), remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, expected_arrival_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, 0, ?, 0, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.rawMaterialId(), fixture.kgUnitId(), orderQuantity, price, price, price,
				orderQuantity.multiply(price), orderQuantity.multiply(price), LocalDate.now(), remark);
		this.jdbcTemplate.update("""
				insert into proc_purchase_order_schedule (
					order_line_id, line_no, planned_date, planned_quantity, received_quantity, status,
					created_at, updated_at
				)
				values (?, 1, ?, ?, 0, 'PLANNED', now(), now())
				""", orderLineId, LocalDate.now(), orderQuantity);
		return orderId;
	}

	private long purchaseOrderLineId(long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from proc_purchase_order_line
				where order_id = ?
				and line_no = 1
				""", Long.class, orderId);
	}

	private void insertConfirmedPayable(PurchaseReceiptFact receipt, String totalAmount) {
		BigDecimal amount = new BigDecimal(totalAmount);
		long payableId = this.jdbcTemplate.queryForObject("""
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, paid_amount, unpaid_amount, status, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, 0, ?, 'CONFIRMED', '023 采购退货原价测试',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "AP-023-" + SEQUENCE.incrementAndGet(), receipt.supplierId(), receipt.receiptId(),
				receipt.receiptNo(), LocalDate.now(), LocalDate.now().plusDays(30), amount, amount);
		this.jdbcTemplate.update("""
				insert into fin_payable_source (
					payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, 1, ?)
				""", payableId, receipt.receiptId(), receipt.receiptNo(), receipt.receiptLineId(), amount);
	}

	private InventoryPostingService.PostingResult postValuedInventory(long warehouseId, long materialId, long unitId,
			String quantity, String unitPrice, InventoryQualityStatus qualityStatus, String reason) {
		long sourceId = 2_700_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 2_710_000L + SEQUENCE.incrementAndGet();
		return this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
				InventoryMovementType.ADJUSTMENT_INCREASE, InventoryDirection.IN, warehouseId, materialId, unitId,
				new BigDecimal(quantity), qualityStatus, "INVENTORY_DOCUMENT", sourceId, sourceLineId,
				LocalDate.now(), reason, reason, "tester", null, null, new BigDecimal(unitPrice)));
	}

	private ProductionFixture productionFixture(InventoryFixture fixture) {
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
					effective_from, created_by, created_at, updated_by, updated_at, enabled_by, enabled_at
				)
				values (?, ?, ?, ?, 1.000000, ?, 'ENABLED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "BOM-023-" + SEQUENCE.incrementAndGet(), fixture.finishedMaterialId(),
				"V" + SEQUENCE.incrementAndGet(), "023 生产估值 BOM", fixture.eachUnitId(), LocalDate.now());
		long bomItemId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom_item (
					bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, business_unit_id,
					business_quantity, base_unit_id, base_quantity, quantity_basis, created_at, updated_at
				)
				values (?, 1, ?, ?, 3.000000, 0, ?, 3.000000, ?, 3.000000, 'BASE_UNIT', now(), now())
				returning id
				""", Long.class, bomId, fixture.rawMaterialId(), fixture.kgUnitId(), fixture.kgUnitId(),
				fixture.kgUnitId());
		long workOrderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity,
					qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark,
					created_by, created_at, updated_by, updated_at, released_by, released_at
				)
				values (?, ?, ?, 1.000000, 0, 0, 0, 0, ?, ?, ?, ?, 'RELEASED', '023 估值工单',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "WO-023-" + SEQUENCE.incrementAndGet(), fixture.finishedMaterialId(), bomId,
				fixture.rawWarehouseId(), fixture.finishedWarehouseId(), LocalDate.now(), LocalDate.now().plusDays(1));
		long workOrderMaterialId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order_material (
					work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity, issued_quantity,
					loss_rate, remark, created_at, updated_at, business_unit_id, business_quantity,
					base_unit_id, base_required_quantity, quantity_basis
				)
				values (?, 1, ?, ?, ?, 3.000000, 0, 0, '023 估值原料', now(), now(), ?, 3.000000, ?,
					3.000000, 'BASE_UNIT')
				returning id
				""", Long.class, workOrderId, bomItemId, fixture.rawMaterialId(), fixture.kgUnitId(),
				fixture.kgUnitId(), fixture.kgUnitId());
		return new ProductionFixture(workOrderId, workOrderMaterialId);
	}

	private CurrentUser backendOperator() {
		return userWithPermissions("inventory:valuation:view", "inventory:valuation-adjustment:create",
				"inventory:valuation-adjustment:update", "inventory:valuation-adjustment:submit",
				"inventory:valuation-adjustment:cancel", "inventory:balance:view", "platform:approval:view",
				"platform:attachment:view", "platform:attachment:download", "platform:attachment:delete");
	}

	private CurrentUser userWithPermissions(String... permissions) {
		return new CurrentUser(1L, "admin", "admin", SystemUserStatus.ENABLED, List.of(), List.of(),
				List.of(permissions));
	}

	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("POST");
		request.setRequestURI("/api/admin/test");
		return request;
	}

	private long insertSupplier(String name) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "SUP-023-" + suffix, name + suffix);
	}

	private long insertCustomer(String name) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "CUS-023-" + suffix, name + suffix);
	}

	private long insertProject(String name) {
		int suffix = SEQUENCE.incrementAndGet();
		long customerId = insertCustomer(name + "客户");
		long ownerId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 'ACTIVE', 10000.00, 5000.00, 'test', now(), 'test', now())
				returning id
				""", Long.class, "PRJ-023-" + suffix, name + suffix, customerId, ownerId, LocalDate.now(),
				LocalDate.now().plusDays(30));
	}

	private long insertProjectCostLayer(long projectId, long materialId, String quantity, String amount,
			String unitCost) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_project_cost_layer (
					project_id, material_id, source_type, source_id, source_line_id, original_quantity,
					original_amount, remaining_quantity, remaining_amount, unit_cost, status
				)
				values (?, ?, 'STAGE023_TEST', ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
				returning id
				""", Long.class, projectId, materialId, 3_100_000L + SEQUENCE.incrementAndGet(),
				3_200_000L + SEQUENCE.incrementAndGet(), new BigDecimal(quantity), new BigDecimal(amount),
				new BigDecimal(quantity), new BigDecimal(amount), new BigDecimal(unitCost));
	}

	private void insertProjectBalance(long warehouseId, long materialId, long unitId, long projectId, long layerId,
			String quantity, String amount, String unitCost) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, project_id, valuation_state, inventory_amount, average_unit_cost,
					cost_layer_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, 'QUALIFIED', 'PROJECT', ?, 'VALUED', ?, ?, ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity), projectId, new BigDecimal(amount),
				new BigDecimal(unitCost), layerId);
	}

	private PublicPoolFact publicPool(long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select quantity, amount, average_unit_cost, valuation_state
				from inv_public_valuation_pool
				where material_id = ?
				""", (rs, rowNum) -> new PublicPoolFact(rs.getBigDecimal("quantity"),
				rs.getBigDecimal("amount"), rs.getBigDecimal("average_unit_cost"),
				rs.getString("valuation_state")), materialId);
	}

	private void insertPublicValuationPool(long materialId, String quantity, String amount, String averageUnitCost) {
		this.jdbcTemplate.update("""
				insert into inv_public_valuation_pool (material_id, quantity, amount, average_unit_cost, valuation_state)
				values (?, ?, ?, ?, 'VALUED')
				on conflict (material_id) do update
				set quantity = excluded.quantity,
				    amount = excluded.amount,
				    average_unit_cost = excluded.average_unit_cost,
				    valuation_state = excluded.valuation_state
				""", materialId, new BigDecimal(quantity), new BigDecimal(amount), new BigDecimal(averageUnitCost));
	}

	private ValueMovementFact valueMovement(String sourceType, long sourceId, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select unit_cost, inventory_amount, valuation_method
				from inv_value_movement
				where source_type = ?
				and source_id = ?
				and source_line_id = ?
				order by id desc
				limit 1
				""", (rs, rowNum) -> new ValueMovementFact(rs.getBigDecimal("unit_cost"),
				rs.getBigDecimal("inventory_amount"), rs.getString("valuation_method")), sourceType, sourceId,
				sourceLineId);
	}

	private long valueMovementCount(String sourceType, long sourceId, String valuationMethod) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_value_movement
				where source_type = ?
				and source_id = ?
				and valuation_method = ?
				""", Long.class, sourceType, sourceId, valuationMethod);
	}

	private BigDecimal projectLayerRemainingAmount(long layerId) {
		return this.jdbcTemplate.queryForObject("""
				select remaining_amount
				from inv_project_cost_layer
				where id = ?
				""", BigDecimal.class, layerId);
	}

	private Map<String, Object> projectBalance(long warehouseId, long materialId, long projectId, long costLayerId) {
		return projectBalance(warehouseId, materialId, projectId, costLayerId, InventoryQualityStatus.QUALIFIED);
	}

	private Map<String, Object> projectBalance(long warehouseId, long materialId, long projectId, long costLayerId,
			InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForMap("""
				select quantity_on_hand, inventory_amount, cost_layer_id
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and ownership_type = 'PROJECT'
				and project_id = ?
				and quality_status = ?
				and cost_layer_id = ?
				""", warehouseId, materialId, projectId, qualityStatus.name(), costLayerId);
	}

	private List<Long> costLayerIds(JsonNode items) {
		List<Long> ids = new ArrayList<>();
		for (JsonNode item : items) {
			if (item.hasNonNull("costLayerId")) {
				ids.add(item.get("costLayerId").longValue());
			}
			else if (item.hasNonNull("id")) {
				ids.add(item.get("id").longValue());
			}
		}
		return ids;
	}

	private JsonNode balanceItemByCostLayer(JsonNode items, long costLayerId) {
		for (JsonNode item : items) {
			if (item.hasNonNull("costLayerId") && item.get("costLayerId").longValue() == costLayerId) {
				return item;
			}
		}
		throw new AssertionError("未找到成本层余额：" + costLayerId + "，实际：" + items);
	}

	private long reserveInventory(long warehouseId, long materialId, long unitId, String quantity, String ownershipType,
			Long projectId, Long costLayerId) {
		return reserveInventory(warehouseId, materialId, unitId, quantity, ownershipType, projectId, costLayerId,
				InventoryQualityStatus.QUALIFIED, null, null, null);
	}

	private long reserveInventory(long warehouseId, long materialId, long unitId, String quantity, String ownershipType,
			Long projectId, Long costLayerId, InventoryQualityStatus qualityStatus, Long batchId, Long serialId,
			Long parentReservationId) {
		long sourceId = 4_000_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 4_100_000L + SEQUENCE.incrementAndGet();
		return this.inventoryAvailabilityService.reserveFromWarehouse(
				new InventoryAvailabilityService.ReservationCommand(InventoryReservationType.RESERVATION, warehouseId,
						materialId, unitId, new BigDecimal(quantity), "STAGE023_TEST", sourceId, sourceLineId,
						"RSV-023-" + sourceLineId, LocalDate.now(), "023 成本层预留", null, ownershipType, projectId,
						costLayerId, qualityStatus, batchId, serialId, parentReservationId),
				backendOperator(), request());
	}

	private Long reservationCostLayerId(long reservationId) {
		return this.jdbcTemplate.queryForObject("""
				select cost_layer_id
				from inv_stock_reservation
				where id = ?
				""", Long.class, reservationId);
	}

	private long reservationSourceLineId(long reservationId) {
		return this.jdbcTemplate.queryForObject("""
				select source_line_id
				from inv_stock_reservation
				where id = ?
				""", Long.class, reservationId);
	}

	private long parentReservationId(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from inv_stock_reservation
				where source_type = ?
				  and source_line_id = ?
				  and parent_reservation_id is null
				  and batch_id is null
				  and serial_id is null
				""", Long.class, sourceType, sourceLineId);
	}

	private long childReservationId(long parentReservationId, Long batchId, Long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from inv_stock_reservation
				where parent_reservation_id = ?
				  and batch_id is not distinct from ?
				  and serial_id is not distinct from ?
				""", Long.class, parentReservationId, batchId, serialId);
	}

	private void assertReservationIdentity(long reservationId, Long parentReservationId, Long batchId, Long serialId,
			String status, String quantity, String consumedQuantity) {
		Map<String, Object> row = this.jdbcTemplate.queryForMap("""
				select parent_reservation_id, batch_id, serial_id, status, quantity, consumed_quantity
				from inv_stock_reservation
				where id = ?
				""", reservationId);
		assertThat(row.get("parent_reservation_id")).isEqualTo(parentReservationId);
		assertThat(row.get("batch_id")).isEqualTo(batchId);
		assertThat(row.get("serial_id")).isEqualTo(serialId);
		assertThat(row.get("status")).isEqualTo(status);
		assertDecimal((BigDecimal) row.get("quantity"), quantity);
		assertDecimal((BigDecimal) row.get("consumed_quantity"), consumedQuantity);
	}

	private BigDecimal lockedQuantity(long warehouseId, long materialId, String ownershipType, Long projectId,
			Long costLayerId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(locked_quantity), 0)
				from inv_stock_balance
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and ownership_type = ?
				  and project_id is not distinct from ?
				  and cost_layer_id is not distinct from ?
				""", BigDecimal.class, warehouseId, materialId, ownershipType, projectId, costLayerId);
	}

	private BigDecimal lockedQuantityByBatch(long warehouseId, long materialId, long batchId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(locked_quantity), 0)
				from inv_stock_balance
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and batch_id = ?
				""", BigDecimal.class, warehouseId, materialId, batchId);
	}

	private long nextSourceId() {
		return 7_000_000L + SEQUENCE.incrementAndGet();
	}

	private long nextSourceLineId() {
		return 7_100_000L + SEQUENCE.incrementAndGet();
	}

	private Map<String, Object> projectWarehouseTransferBody(long sourceWarehouseId, long targetWarehouseId,
			long materialId, long unitId, long projectId, long costLayerId, String quantity, String reason) {
		Map<String, Object> body = warehouseTransferPayload(sourceWarehouseId, targetWarehouseId, materialId, unitId,
				quantity, reason);
		@SuppressWarnings("unchecked")
		Map<String, Object> line = (Map<String, Object>) ((List<?>) body.get("lines")).get(0);
		line.put("ownershipType", "PROJECT");
		line.put("projectId", projectId);
		line.put("sourceCostLayerId", costLayerId);
		return body;
	}

	private CostRecordFact materialIssueCostRecord(long issueLineId) {
		return this.jdbcTemplate.queryForObject("""
				select unit_price, amount, basis_type
				from mfg_cost_record
				where source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
				and source_line_id = ?
				and cost_type = 'MATERIAL'
				""", (rs, rowNum) -> new CostRecordFact(rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("amount"), rs.getString("basis_type")), issueLineId);
	}

	private BigDecimal balanceAmount(long warehouseId, long materialId, InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForObject("""
				select inventory_amount
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				""", BigDecimal.class, warehouseId, materialId, qualityStatus.name());
	}

	private void insertLegacyUnvaluedPublicStock(long warehouseId, long materialId, long unitId, String quantity) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, valuation_state, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, 'QUALIFIED', 'PUBLIC', 'LEGACY_UNVALUED', now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity));
		this.jdbcTemplate.update("""
				insert into inv_public_valuation_pool (
					material_id, quantity, amount, average_unit_cost, valuation_state
				)
				values (?, ?, null, null, 'LEGACY_UNVALUED')
				""", materialId, new BigDecimal(quantity));
	}

	private void insertPublicBalanceWithoutAverage(long warehouseId, long materialId, long unitId, String quantity) {
		long poolId = this.jdbcTemplate.queryForObject("""
				insert into inv_public_valuation_pool (
					material_id, quantity, amount, average_unit_cost, valuation_state
				)
				values (?, ?, null, null, 'VALUED')
				returning id
				""", Long.class, materialId, new BigDecimal(quantity));
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, valuation_state, public_pool_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, 'QUALIFIED', 'PUBLIC', 'VALUED', ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity), poolId);
	}

	private void insertPublicBalanceWithPoolAverage(long warehouseId, long materialId, long unitId,
			String balanceQuantity, String poolQuantity, String amount, String averageUnitCost) {
		long poolId = this.jdbcTemplate.queryForObject("""
				insert into inv_public_valuation_pool (
					material_id, quantity, amount, average_unit_cost, valuation_state
				)
				values (?, ?, ?, ?, 'VALUED')
				returning id
				""", Long.class, materialId, new BigDecimal(poolQuantity), new BigDecimal(amount),
				new BigDecimal(averageUnitCost));
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					ownership_type, valuation_state, inventory_amount, average_unit_cost, public_pool_id,
					created_at, updated_at
				)
				values (?, ?, ?, ?, 0, 'QUALIFIED', 'PUBLIC', 'VALUED', ?, ?, ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(balanceQuantity), new BigDecimal(amount),
				new BigDecimal(averageUnitCost), poolId);
	}

	private BigDecimal stocktakeInboundUnitCost(long stocktakeId) {
		return this.jdbcTemplate.queryForObject("""
				select unit_cost
				from inv_value_movement
				where source_type = 'STOCKTAKE'
				  and source_id = ?
				  and quantity > 0
				order by id desc
				limit 1
				""", BigDecimal.class, stocktakeId);
	}

	private Map<String, Object> stocktakeProjectLayer(long stocktakeId, long stocktakeLineId) {
		return this.jdbcTemplate.queryForMap("""
				select original_quantity, original_amount, unit_cost, remaining_quantity, remaining_amount
				from inv_project_cost_layer
				where source_type = 'STOCKTAKE'
				  and source_id = ?
				  and source_line_id = ?
				order by id desc
				limit 1
				""", stocktakeId, stocktakeLineId);
	}

	private long insertStocktakeEvidence(long stocktakeId, String description) {
		int suffix = SEQUENCE.incrementAndGet();
		long fileId = this.jdbcTemplate.queryForObject("""
				insert into platform_file_object (
					bucket, object_key, original_filename, content_type, size_bytes, sha256, etag,
					file_usage, status, created_by_user_id, created_by_username, created_at
				)
				values ('qherp-test-private', ?, ?, 'text/plain', 12, ?, 'etag-test',
					'ATTACHMENT', 'AVAILABLE', 1, 'admin', now())
				returning id
				""", Long.class, "stocktake-evidence/" + suffix, "stocktake-evidence-" + suffix + ".txt",
				("%064d").formatted(suffix));
		return this.jdbcTemplate.queryForObject("""
				insert into platform_business_attachment (
					object_type, object_id, file_id, description, status, created_by_user_id,
					created_by_username, created_at
				)
				values ('INVENTORY_STOCKTAKE', ?, ?, ?, 'AVAILABLE', 1, 'admin', now())
				returning id
				""", Long.class, stocktakeId, fileId, description);
	}

	private Map<String, Object> warehouseTransferPayload(long sourceWarehouseId, long targetWarehouseId,
			long materialId, long unitId, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("sourceWarehouseId", sourceWarehouseId);
		line.put("targetWarehouseId", targetWarehouseId);
		line.put("ownershipType", "PUBLIC");
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("qualityStatus", "QUALIFIED");
		line.put("quantity", quantity);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", reason);
		body.put("idempotencyKey", "TRF-" + SEQUENCE.incrementAndGet());
		body.put("lines", List.of(line));
		return body;
	}

	private Map<String, Object> ownershipConversionPayload(long sourceWarehouseId, long targetWarehouseId,
			long materialId, long unitId, long projectId, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("sourceOwnershipType", "PUBLIC");
		line.put("targetOwnershipType", "PROJECT");
		line.put("targetProjectId", projectId);
		line.put("sourceWarehouseId", sourceWarehouseId);
		line.put("targetWarehouseId", targetWarehouseId);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("qualityStatus", "QUALIFIED");
		line.put("quantity", quantity);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", reason);
		body.put("idempotencyKey", "OWN-" + SEQUENCE.incrementAndGet());
		body.put("lines", List.of(line));
		return body;
	}

	private Map<String, Object> stocktakePayload(long warehouseId, long materialId, String reason) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("scopeType", "WAREHOUSE");
		body.put("warehouseId", warehouseId);
		body.put("materialId", materialId);
		body.put("reason", reason);
		body.put("idempotencyKey", "STK-" + SEQUENCE.incrementAndGet());
		return body;
	}

	private JsonNode stocktakeLines(long stocktakeId, int page, int pageSize, AuthenticatedSession session)
			throws Exception {
		ResponseEntity<String> response = get("/api/admin/inventory/stocktakes/" + stocktakeId + "/lines?page="
				+ page + "&pageSize=" + pageSize, session);
		assertOk(response);
		return data(response);
	}

	private Map<String, Object> stocktakeLineUpdatePayload(JsonNode stocktakeDetail, JsonNode linePage,
			boolean addVariance) {
		return stocktakeLineUpdatePayload(stocktakeDetail, linePage, addVariance, null, null);
	}

	private Map<String, Object> stocktakeLineUpdatePayload(JsonNode stocktakeDetail, JsonNode linePage,
			boolean addVariance, String varianceUnitCost, String varianceReason) {
		List<Map<String, Object>> lines = new ArrayList<>();
		for (JsonNode lineNode : linePage.get("items")) {
			BigDecimal countedQuantity = new BigDecimal(lineNode.get("bookQuantity").asText());
			if (addVariance && lines.isEmpty()) {
				countedQuantity = countedQuantity.add(BigDecimal.ONE);
			}
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("id", lineNode.get("id").longValue());
			line.put("version", lineNode.get("version").longValue());
			line.put("countedQuantity", countedQuantity.toPlainString());
			if (varianceUnitCost != null) {
				line.put("varianceUnitCost", varianceUnitCost);
			}
			if (varianceReason != null) {
				line.put("varianceReason", varianceReason);
			}
			lines.add(line);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", stocktakeDetail.get("version").longValue());
		body.put("lines", lines);
		return body;
	}

	private Map<String, Object> stocktakeSingleLineUpdatePayload(JsonNode stocktakeDetail, JsonNode lineNode,
			String countedQuantity, boolean includeVarianceUnitCost, String varianceUnitCost, String varianceReason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("id", lineNode.get("id").longValue());
		line.put("version", lineNode.get("version").longValue());
		line.put("countedQuantity", countedQuantity);
		if (includeVarianceUnitCost) {
			line.put("varianceUnitCost", varianceUnitCost);
		}
		if (varianceReason != null) {
			line.put("varianceReason", varianceReason);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", stocktakeDetail.get("version").longValue());
		body.put("lines", List.of(line));
		return body;
	}

	private Map<String, Object> valuationAdjustmentPayload(long materialId, String quantity, String unitCost,
			String amount) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("ownershipType", "PUBLIC");
		line.put("materialId", materialId);
		line.put("quantity", quantity);
		line.put("unitCost", unitCost);
		line.put("adjustmentAmount", amount);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("adjustmentType", "LEGACY_OPENING");
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", "023 历史库存期初估值");
		body.put("idempotencyKey", "VAL-ADJ-" + SEQUENCE.incrementAndGet());
		body.put("lines", List.of(line));
		return body;
	}

	private Map<String, Object> provisionalRevaluationPayload(long materialId, String publicDelta, long projectId,
			long costLayerId, String projectDelta) {
		Map<String, Object> publicLine = new LinkedHashMap<>();
		publicLine.put("lineNo", 1);
		publicLine.put("ownershipType", "PUBLIC");
		publicLine.put("materialId", materialId);
		publicLine.put("adjustmentAmount", publicDelta);
		Map<String, Object> projectLine = new LinkedHashMap<>();
		projectLine.put("lineNo", 2);
		projectLine.put("ownershipType", "PROJECT");
		projectLine.put("projectId", projectId);
		projectLine.put("materialId", materialId);
		projectLine.put("adjustmentAmount", projectDelta);
		projectLine.put("costLayerId", costLayerId);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("adjustmentType", "PROVISIONAL_REVALUATION");
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", "023 暂估差额调整");
		body.put("idempotencyKey", "VAL-REVALUE-" + SEQUENCE.incrementAndGet());
		body.put("lines", List.of(publicLine, projectLine));
		return body;
	}

	private Map<String, Object> completionReceiptPayload(long warehouseId, String quantity, String provisionalUnitCost) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("receiptWarehouseId", warehouseId);
		body.put("quantity", quantity);
		body.put("provisionalUnitCost", provisionalUnitCost);
		body.put("remark", "023 完工暂估");
		return body;
	}

	private AuthenticatedSession createReadOnlyUserAndLogin() {
		return createInventoryUserAndLogin("inventory-reader-", "INV_READER_", "库存只读", List.of("inventory:balance:view",
				"inventory:availability:view", "inventory:reservation:view", "inventory:movement:view",
				"inventory:document:view"));
	}

	private AuthenticatedSession createStocktakeViewerWithoutValuationAndLogin() {
		return createInventoryUserAndLogin("stocktake-reader-", "STK_READER_", "盘点只读",
				List.of("inventory:stocktake:view"));
	}

	private AuthenticatedSession createNoInventoryUserAndLogin() {
		return createInventoryUserAndLogin("inventory-no-permission-", "INV_NONE_", "库存无权限", List.of());
	}

	private AuthenticatedSession createInventoryUserAndLogin(String usernamePrefix, String rolePrefix,
			String displayName, List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, displayName, displayName + "测试角色");
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), displayName);
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

	private long insertReservation(InventoryFixture fixture, String reservationType, String sourceType, long sourceId,
			long sourceLineId, String sourceDocumentNo, String quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id, quality_status,
					quantity, released_quantity, consumed_quantity, source_type, source_id, source_line_id,
					source_document_no, business_date, reason, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ACTIVE', ?, ?, ?, 'QUALIFIED', ?, 0, 0, ?, ?, ?, ?, ?, ?, 'tester', now(), 'tester',
					now())
				returning id
				""", Long.class, "RSV-TEST-" + SEQUENCE.incrementAndGet(), reservationType, fixture.rawWarehouseId(),
				fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal(quantity), sourceType, sourceId,
				sourceLineId, sourceDocumentNo, LocalDate.now(), "018 库存预留测试");
	}

	private PurchaseReceiptSource insertPurchaseReceiptSource(InventoryFixture fixture) {
		return insertPurchaseReceiptSource(fixture.rawMaterialId(), fixture.kgUnitId(), fixture.rawWarehouseId());
	}

	private PurchaseReceiptSource insertPurchaseReceiptSource(long materialId, long unitId, long warehouseId) {
		int suffix = SEQUENCE.incrementAndGet();
		long supplierId = this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'tester', now(), 'tester', now())
				returning id
				""", Long.class, "INV_SUP_" + suffix, "追溯供应商" + suffix);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, status, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, 'RECEIVED', 'tester', now(), 'tester', now())
				returning id
				""", Long.class, "PO-TRACE-" + suffix, supplierId, LocalDate.now());
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, 5.000000, 5.000000, 10.000000, 0, 10.000000, 10.000000, 50.00, 50.00,
					now(), now())
				returning id
				""", Long.class, orderId, materialId, unitId);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, created_by, created_at,
					updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', 'tester', now(), 'tester', now(), 'tester', now())
				returning id
				""", Long.class, "PR-TRACE-" + suffix, orderId, supplierId, warehouseId,
				LocalDate.now());
		long receiptLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
					created_at, updated_at
				)
				values (?, 1, ?, ?, ?, 5.000000, 0.000000, 5.000000, 5.000000, 0.000000, 5.000000, now(), now())
				returning id
				""", Long.class, receiptId, orderLineId, materialId, unitId);
		return new PurchaseReceiptSource(receiptId, receiptLineId, "PR-TRACE-" + suffix);
	}

	private long insertTrackedBatchStock(InventoryFixture fixture, PurchaseReceiptSource source, String batchNo,
			InventoryQualityStatus qualityStatus, String quantity) {
		return insertTrackedBatchStock(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), source,
				batchNo, qualityStatus, quantity);
	}

	private long insertTrackedBatchStock(long warehouseId, long materialId, long unitId, PurchaseReceiptSource source,
			String batchNo, InventoryQualityStatus qualityStatus, String quantity) {
		long batchId = this.jdbcTemplate.queryForObject("""
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, '候选和追溯测试', 'tester', now(), 'tester', now())
				returning id
				""", Long.class, materialId, batchNo, source.receiptId(), source.receiptLineId(),
				LocalDate.now());
		insertTrackedMovement(warehouseId, materialId, unitId, quantity, batchId, null, "PURCHASE_RECEIPT",
				source.receiptId(), source.receiptLineId(), qualityStatus);
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					batch_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, ?, ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity), qualityStatus.name(), batchId);
		return batchId;
	}

	private void insertBatchBalance(InventoryFixture fixture, long batchId, InventoryQualityStatus qualityStatus,
			String quantity) {
		insertTrackedMovement(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), quantity,
				batchId, null, "PURCHASE_RECEIPT", 1_840_000L + SEQUENCE.incrementAndGet(),
				1_841_000L + SEQUENCE.incrementAndGet(), qualityStatus);
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					batch_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, ?, ?, now(), now())
				""", fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), new BigDecimal(quantity),
				qualityStatus.name(), batchId);
	}

	private long insertTrackedSerial(InventoryFixture fixture, PurchaseReceiptSource source, String serialNo,
			InventoryQualityStatus qualityStatus, String stockStatus, boolean withBalance) {
		long serialId = this.jdbcTemplate.queryForObject("""
				insert into inv_serial (
					material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status,
					stock_status, business_date, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, '候选序列测试', 'tester', now(), 'tester', now())
				returning id
				""", Long.class, fixture.semiMaterialId(), serialNo, source.receiptId(), source.receiptLineId(),
				fixture.rawWarehouseId(), qualityStatus.name(), stockStatus, LocalDate.now());
		if (withBalance) {
			insertTrackedMovement(fixture.rawWarehouseId(), fixture.semiMaterialId(), fixture.eachUnitId(),
					"1.000000", null, serialId, "PURCHASE_RECEIPT", source.receiptId(), source.receiptLineId(),
					qualityStatus);
			this.jdbcTemplate.update("""
					insert into inv_stock_balance (
						warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
						serial_id, created_at, updated_at
					)
					values (?, ?, ?, 1.000000, 0, ?, ?, now(), now())
					""", fixture.rawWarehouseId(), fixture.semiMaterialId(), fixture.eachUnitId(),
					qualityStatus.name(), serialId);
		}
		return serialId;
	}

	private TrackedInventoryFixture insertTrackedInventoryFacts(InventoryFixture fixture) {
		int suffix = SEQUENCE.incrementAndGet();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		this.jdbcTemplate.update("update mst_material set tracking_method = 'SERIAL' where id = ?",
				fixture.semiMaterialId());
		long batchId = this.jdbcTemplate.queryForObject("""
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, '批次追踪测试', 'tester', now(), 'tester', now())
				returning id
				""", Long.class, fixture.rawMaterialId(), "B-TRACE-" + suffix, 2_000_000L + suffix,
				2_100_000L + suffix, LocalDate.now());
		long batchMovementId = insertTrackedMovement(fixture.rawWarehouseId(), fixture.rawMaterialId(),
				fixture.kgUnitId(), "8.000000", batchId, null, "TRACK_BATCH", 2_200_000L + suffix);
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					batch_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, 'QUALIFIED', ?, now(), now())
				""", fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(),
				new BigDecimal("8.000000"), batchId);

		long serialId = this.jdbcTemplate.queryForObject("""
				insert into inv_serial (
					material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status,
					stock_status, business_date, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'PRODUCTION_COMPLETION', ?, ?, ?, 'QUALIFIED', 'IN_STOCK', ?, '序列追踪测试',
					'tester', now(), 'tester', now())
				returning id
				""", Long.class, fixture.semiMaterialId(), "SN-TRACE-" + suffix, 2_300_000L + suffix,
				2_400_000L + suffix, fixture.rawWarehouseId(), LocalDate.now());
		long serialMovementId = insertTrackedMovement(fixture.rawWarehouseId(), fixture.semiMaterialId(),
				fixture.eachUnitId(), "1.000000", null, serialId, "TRACK_SERIAL", 2_500_000L + suffix);
		this.jdbcTemplate.update("update inv_serial set last_movement_id = ? where id = ?", serialMovementId,
				serialId);
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					serial_id, created_at, updated_at
				)
				values (?, ?, ?, 1, 0, 'QUALIFIED', ?, now(), now())
				""", fixture.rawWarehouseId(), fixture.semiMaterialId(), fixture.eachUnitId(), serialId);
		return new TrackedInventoryFixture(batchId, "B-TRACE-" + suffix, batchMovementId, serialId,
				"SN-TRACE-" + suffix, serialMovementId);
	}

	private long insertTrackedMovement(long warehouseId, long materialId, long unitId, String quantity, Long batchId,
			Long serialId, String sourceType, long sourceLineId) {
		return insertTrackedMovement(warehouseId, materialId, unitId, quantity, batchId, serialId, sourceType,
				sourceLineId, sourceLineId, InventoryQualityStatus.QUALIFIED);
	}

	private long insertTrackedMovement(long warehouseId, long materialId, long unitId, String quantity, Long batchId,
			Long serialId, String sourceType, long sourceId, long sourceLineId, InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason,
					remark, operator_name, occurred_at, quality_status, batch_id, serial_id
				)
				values (?, 'PURCHASE_RECEIPT', 'IN', ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, '追踪接口测试',
					'追踪接口测试', 'tester', now(), ?, ?, ?)
				returning id
				""", Long.class, "MV-TRACE-" + SEQUENCE.incrementAndGet(), warehouseId, materialId, unitId,
				new BigDecimal(quantity), new BigDecimal(quantity), sourceType, sourceId, sourceLineId,
				LocalDate.now(), qualityStatus.name(), batchId, serialId);
	}

	private MovementRow movementForDocument(long documentId) {
		return this.jdbcTemplate.queryForObject("""
				select movement_type, direction, source_line_id, quantity, before_quantity, after_quantity
				from inv_stock_movement
				where source_id = ?
				order by id desc
				limit 1
				""",
				(rs, rowNum) -> new MovementRow(rs.getString("movement_type"), rs.getString("direction"),
						rs.getLong("source_line_id"), rs.getBigDecimal("quantity"),
						rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity")),
				documentId);
	}

	private long lineIdForDocument(long documentId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from inv_inventory_document_line
				where document_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, documentId);
	}

	private String materialCode(long materialId) {
		return this.jdbcTemplate.queryForObject("select code from mst_material where id = ?", String.class, materialId);
	}

	private BigDecimal balanceQuantity(long warehouseId, long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				""", BigDecimal.class, warehouseId, materialId);
	}

	private BigDecimal batchBalanceQuantity(long warehouseId, long materialId, long batchId) {
		return this.jdbcTemplate.queryForObject("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id = ?
				""", BigDecimal.class, warehouseId, materialId, batchId);
	}

	private long movementCount(long warehouseId, long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where warehouse_id = ?
				and material_id = ?
				""", Long.class, warehouseId, materialId);
	}

	private long movementCountBySource(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", Long.class, sourceType, sourceLineId);
	}

	private String movementNoBySource(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select movement_no
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", String.class, sourceType, sourceLineId);
	}

	private MovementRow movementForSource(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select movement_type, direction, source_line_id, quantity, before_quantity, after_quantity
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""",
				(rs, rowNum) -> new MovementRow(rs.getString("movement_type"), rs.getString("direction"),
						rs.getLong("source_line_id"), rs.getBigDecimal("quantity"),
						rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity")),
				sourceType, sourceLineId);
	}

	private long decreaseMovementCount(long firstDocumentId, long secondDocumentId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_id in (?, ?)
				and movement_type = 'ADJUSTMENT_DECREASE'
				""", Long.class, firstDocumentId, secondDocumentId);
	}

	private void assertAuditLog(String action, long documentId) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = 'INVENTORY_DOCUMENT'
				and target_id = ?
				""", Long.class, action, Long.toString(documentId))).as(action).isOne();
	}

	private ResponseEntity<String> createDocument(AuthenticatedSession session, Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/inventory/documents", body, session);
	}

	private ResponseEntity<String> updateDocument(AuthenticatedSession session, long documentId,
			Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + documentId, body, session);
	}

	private ResponseEntity<String> postDocument(AuthenticatedSession session, long documentId) {
		return exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + documentId + "/post", null, session);
	}

	private ResponseEntity<String> postDocument(AuthenticatedSession session, long documentId, long version) {
		return exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + documentId + "/post",
				Map.of("version", version), session);
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

	private JsonNode itemById(ResponseEntity<String> response, long id) throws Exception {
		JsonNode items = data(response).get("items");
		for (JsonNode item : items) {
			if (item.get("id").longValue() == id) {
				return item;
			}
		}
		throw new AssertionError("未在分页结果中找到 id=" + id);
	}

	private void assertAvailableActions(JsonNode node, String... actions) {
		JsonNode availableActions = node.get("allowedActions");
		assertThat(availableActions).as("allowedActions").isNotNull();
		assertThat(availableActions.toString()).contains(actions);
	}

	private String methodBody(String source, String startMarker) {
		int start = source.indexOf(startMarker);
		assertThat(start).as("必须找到方法起点: %s", startMarker).isGreaterThanOrEqualTo(0);
		int openBrace = source.indexOf('{', start + startMarker.length());
		assertThat(openBrace).as("必须找到方法左花括号: %s", startMarker).isGreaterThan(start);
		int depth = 0;
		for (int index = openBrace; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			}
			else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(start, index + 1);
				}
			}
		}
		throw new AssertionError("必须找到方法右花括号: " + startMarker);
	}

	private void assertAppearsInOrder(String body, String... markers) {
		int previous = -1;
		for (String marker : markers) {
			int current = body.indexOf(marker, previous + 1);
			assertThat(current).as("必须找到锁序标记: %s", marker).isGreaterThanOrEqualTo(0);
			assertThat(current).as("锁序标记顺序错误: %s", marker).isGreaterThan(previous);
			previous = current;
		}
	}

	private void assertReservationLockOrder(String body) {
		assertAppearsInOrder(body, "order by case when parent_reservation_id is null then 0 else 1 end",
				"coalesce(parent_reservation_id, id), id", "for update");
	}

	@Test
	void 两个批次普通出库并发必须按统一余额顺序完成且不死锁() throws Exception {
		InventoryFixture fixture = fixture();
		this.jdbcTemplate.update("update mst_material set tracking_method = 'BATCH' where id = ?",
				fixture.rawMaterialId());
		PurchaseReceiptSource source = insertPurchaseReceiptSource(fixture);
		long batchA = insertTrackedBatchStock(fixture, source, "B-POST-ORDER-A-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "4.000000");
		long batchB = insertTrackedBatchStock(fixture, source, "B-POST-ORDER-B-" + SEQUENCE.incrementAndGet(),
				InventoryQualityStatus.QUALIFIED, "4.000000");
		reserveInventory(fixture.rawWarehouseId(), fixture.rawMaterialId(), fixture.kgUnitId(), "2.000000",
				"PUBLIC", null, null, InventoryQualityStatus.QUALIFIED, null, null, null);

		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<InventoryPostingService.PostingResult> first = executorService.submit(() -> {
				start.await(10, TimeUnit.SECONDS);
				return postBatchOutbound(fixture, batchA, "并发批次 A");
			});
			Future<InventoryPostingService.PostingResult> second = executorService.submit(() -> {
				start.await(10, TimeUnit.SECONDS);
				return postBatchOutbound(fixture, batchB, "并发批次 B");
			});
			start.countDown();

			assertThat(first.get(15, TimeUnit.SECONDS).afterQuantity()).isEqualByComparingTo("2.000000");
			assertThat(second.get(15, TimeUnit.SECONDS).afterQuantity()).isEqualByComparingTo("2.000000");
			assertDecimal(batchBalanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), batchA),
					"2.000000");
			assertDecimal(batchBalanceQuantity(fixture.rawWarehouseId(), fixture.rawMaterialId(), batchB),
					"2.000000");
		}
		finally {
			executorService.shutdownNow();
		}
	}

	private void assertAvailableActionsEmpty(JsonNode node) {
		JsonNode availableActions = node.get("allowedActions");
		assertThat(availableActions).as("allowedActions").isNotNull();
		assertThat(availableActions.size()).isZero();
	}

	private List<JsonNode> items(ResponseEntity<String> response) throws Exception {
		JsonNode items = data(response).get("items");
		List<JsonNode> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			result.add(items.get(i));
		}
		return result;
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
	}

	private void assertTrackingColumnsAreNull(String sql, Object... args) {
		Map<String, Object> row = this.jdbcTemplate.queryForMap(sql, args);
		assertThat(row.get("batch_id")).isNull();
		assertThat(row.get("serial_id")).isNull();
	}

	private void assertUnauthorized(ResponseEntity<String> response) throws Exception {
		assertError(response, HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");
	}

	private void assertForbidden(ResponseEntity<String> response) throws Exception {
		assertError(response, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	private BigDecimal decimal(JsonNode node, String field) {
		return new BigDecimal(node.get(field).asText());
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertDecimal(decimal(node, field), expected);
	}

	private void assertDecimal(BigDecimal actual, String expected) {
		assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
	}

	private record PurchaseReceiptFact(long receiptId, String receiptNo, long supplierId, long receiptLineId,
			long orderLineId, BigDecimal quantity, BigDecimal unitPrice) {
	}

	private record ProductionFixture(long workOrderId, long workOrderMaterialId) {
	}

	private record PublicPoolFact(BigDecimal quantity, BigDecimal amount, BigDecimal averageUnitCost,
			String valuationState) {
	}

	private record ValueMovementFact(BigDecimal unitCost, BigDecimal inventoryAmount, String valuationMethod) {
	}

	private record CostRecordFact(BigDecimal unitPrice, BigDecimal amount, String basisType) {
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

	private record InventoryFixture(long eachUnitId, long kgUnitId, long meterUnitId, long categoryId,
			long rawWarehouseId, long finishedWarehouseId, long disabledWarehouseId, long finishedMaterialId,
			long semiMaterialId, long rawMaterialId, long auxiliaryMaterialId, long disabledMaterialId) {
	}

	private record TrackedInventoryFixture(long batchId, String batchNo, long batchMovementId, long serialId,
			String serialNo, long serialMovementId) {
	}

	private record PurchaseReceiptSource(long receiptId, long receiptLineId, String receiptNo) {
	}

	private record MovementRow(String movementType, String direction, long sourceLineId, BigDecimal quantity,
			BigDecimal beforeQuantity, BigDecimal afterQuantity) {
	}

}
