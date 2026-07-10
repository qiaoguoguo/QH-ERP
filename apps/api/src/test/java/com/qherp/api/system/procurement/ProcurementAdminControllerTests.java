package com.qherp.api.system.procurement;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=procurement-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProcurementAdminControllerTests extends PostgresIntegrationTest {

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
	void adminCanRunPurchaseOrderAndReceiptLifecycle() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = fixture();

		ResponseEntity<String> created = createOrder(admin,
				orderPayload(fixture.supplierId(), "采购订单创建", List.of(orderLine(1, fixture.materialId(), null,
						"5.000000", "10.500000", "创建行"))));
		assertOk(created);
		JsonNode createdData = data(created);
		long orderId = createdData.get("id").longValue();
		assertThat(createdData.get("status").asText()).isEqualTo("DRAFT");
		assertThat(createdData.get("lineCount").intValue()).isOne();
		assertDecimal(createdData, "totalQuantity", "5.000000");
		assertDecimal(createdData, "receivedQuantity", "0");
		JsonNode createdLine = firstLine(createdData);
		assertThat(createdLine.get("unitId").longValue()).isEqualTo(fixture.unitId());
		assertDecimal(createdLine, "unitPrice", "10.500000");

		ResponseEntity<String> updated = updateOrder(admin, orderId,
				orderPayload(fixture.supplierId(), "采购订单更新", List.of(orderLine(1, fixture.materialId(),
						fixture.unitId(), "6.000000", "12.250000", "更新行"))));
		assertOk(updated);
		JsonNode updatedLine = firstLine(data(updated));
		long orderLineId = updatedLine.get("id").longValue();
		assertDecimal(updatedLine, "quantity", "6.000000");
		assertDecimal(updatedLine, "remainingQuantity", "6.000000");

		ResponseEntity<String> confirmed = confirmOrder(admin, orderId);
		assertOk(confirmed);
		assertThat(data(confirmed).get("status").asText()).isEqualTo("CONFIRMED");

		ResponseEntity<String> receiptCreated = createReceipt(admin, orderId,
				receiptPayload(fixture.warehouseId(), "采购入库创建",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "2.000000",
								"创建入库行"))));
		assertOk(receiptCreated);
		long receiptId = data(receiptCreated).get("id").longValue();
		JsonNode createdReceiptLine = firstLine(data(receiptCreated));
		assertDecimal(createdReceiptLine, "receivedQuantityBefore", "0");
		assertDecimal(createdReceiptLine, "remainingQuantityBefore", "6.000000");

		ResponseEntity<String> receiptUpdated = updateReceipt(admin, receiptId,
				receiptPayload(fixture.warehouseId(), "采购入库更新",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "3.000000",
								"更新入库行"))));
		assertOk(receiptUpdated);
		assertDecimal(firstLine(data(receiptUpdated)), "quantity", "3.000000");

		ResponseEntity<String> posted = postReceipt(admin, receiptId);
		assertOk(posted);
		JsonNode postedData = data(posted);
		JsonNode postedLine = firstLine(postedData);
		long receiptLineId = postedLine.get("id").longValue();
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertDecimal(postedLine, "beforeQuantity", "0");
		assertDecimal(postedLine, "afterQuantity", "3.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), "3.000000");

		MovementRow movement = movementForSource("PURCHASE_RECEIPT", receiptLineId);
		assertThat(movement.movementType()).isEqualTo("PURCHASE_RECEIPT");
		assertThat(movement.direction()).isEqualTo("IN");
		assertThat(movement.sourceId()).isEqualTo(receiptId);
		assertDecimal(movement.quantity(), "3.000000");
		assertDecimal(movement.beforeQuantity(), "0");
		assertDecimal(movement.afterQuantity(), "3.000000");
		assertThat(movementQualityStatus("PURCHASE_RECEIPT", receiptLineId))
			.isEqualTo(InventoryQualityStatus.PENDING_INSPECTION.name());
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "3.000000");
		assertThat(qualityInspectionCount("PURCHASE_RECEIPT", receiptId, receiptLineId, "PENDING")).isOne();

		JsonNode receiptDetail = data(getReceipt(admin, receiptId));
		JsonNode inventoryMovements = receiptDetail.get("inventoryMovements");
		assertThat(inventoryMovements).isNotNull();
		assertThat(inventoryMovements.size()).isOne();
		JsonNode tracedMovement = inventoryMovements.get(0);
		assertThat(tracedMovement.get("id").longValue()).isPositive();
		assertThat(tracedMovement.get("movementNo").asText()).isNotBlank();
		assertThat(tracedMovement.get("movementType").asText()).isEqualTo("PURCHASE_RECEIPT");
		assertThat(tracedMovement.get("direction").asText()).isEqualTo("IN");
		assertThat(tracedMovement.get("warehouseName").asText()).isEqualTo(warehouseName(fixture.warehouseId()));
		assertThat(tracedMovement.get("materialCode").asText()).isEqualTo(materialCode(fixture.materialId()));
		assertThat(tracedMovement.get("materialName").asText()).isEqualTo(materialName(fixture.materialId()));
		assertDecimal(tracedMovement, "quantity", "3.000000");
		assertDecimal(tracedMovement, "beforeQuantity", "0");
		assertDecimal(tracedMovement, "afterQuantity", "3.000000");
		assertThat(tracedMovement.get("businessDate").asText()).isEqualTo(LocalDate.now().toString());
		assertThat(tracedMovement.get("operatorName").asText()).isEqualTo("admin");
		assertThat(tracedMovement.get("occurredAt").asText()).isNotBlank();

		JsonNode partiallyReceived = data(getOrder(admin, orderId));
		assertThat(partiallyReceived.get("status").asText()).isEqualTo("PARTIALLY_RECEIVED");
		assertDecimal(partiallyReceived, "receivedQuantity", "3.000000");
		assertDecimal(partiallyReceived, "remainingQuantity", "3.000000");

		long secondReceiptId = createReceiptId(admin, orderId,
				receiptPayload(fixture.warehouseId(), "采购入库完成",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "3.000000",
								"完成入库行"))));
		assertOk(postReceipt(admin, secondReceiptId));
		JsonNode received = data(getOrder(admin, orderId));
		assertThat(received.get("status").asText()).isEqualTo("RECEIVED");
		assertDecimal(received, "receivedQuantity", "6.000000");
		assertDecimal(received, "remainingQuantity", "0.000000");

		ResponseEntity<String> closed = closeOrder(admin, orderId);
		assertOk(closed);
		assertThat(data(closed).get("status").asText()).isEqualTo("CLOSED");

		assertAuditLog("PROCUREMENT_ORDER_CREATE", "PROCUREMENT_ORDER", orderId);
		assertAuditLog("PROCUREMENT_ORDER_UPDATE", "PROCUREMENT_ORDER", orderId);
		assertAuditLog("PROCUREMENT_ORDER_CONFIRM", "PROCUREMENT_ORDER", orderId);
		assertAuditLog("PROCUREMENT_ORDER_CLOSE", "PROCUREMENT_ORDER", orderId);
		assertAuditLog("PROCUREMENT_RECEIPT_CREATE", "PROCUREMENT_RECEIPT", receiptId);
		assertAuditLog("PROCUREMENT_RECEIPT_UPDATE", "PROCUREMENT_RECEIPT", receiptId);
		assertAuditLog("PROCUREMENT_RECEIPT_POST", "PROCUREMENT_RECEIPT", receiptId);
	}

	@Test
	void stateErrorsAndDuplicatePostingKeepProcurementAndInventoryConsistent() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = fixture();

		long draftOrderId = createOrderId(admin,
				orderPayload(fixture.supplierId(), "草稿取消",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "2.000000", "1.000000", null))));
		assertOk(cancelOrder(admin, draftOrderId));

		long confirmedCancelId = createAndConfirmOrder(admin, fixture, "确认后取消", "2.000000");
		assertOk(cancelOrder(admin, confirmedCancelId));

		long orderId = createAndConfirmOrder(admin, fixture, "状态错误", "5.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		assertError(updateOrder(admin, orderId,
				orderPayload(fixture.supplierId(), "已确认不可更新",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "5.000000", "1.000000", null)))),
				HttpStatus.CONFLICT, "PROCUREMENT_ORDER_STATUS_INVALID");

		long draftOnlyOrderId = createOrderId(admin,
				orderPayload(fixture.supplierId(), "草稿不可入库",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1.000000", "1.000000", null))));
		assertError(createReceipt(admin, draftOnlyOrderId,
				receiptPayload(fixture.warehouseId(), "草稿入库",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "1.000000",
								null)))),
				HttpStatus.CONFLICT, "PROCUREMENT_ORDER_STATUS_INVALID");

		long receiptId = createReceiptId(admin, orderId,
				receiptPayload(fixture.warehouseId(), "首次入库",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "2.000000",
								null))));
		assertOk(postReceipt(admin, receiptId));
		long receiptLineId = receiptLineId(receiptId);
		BigDecimal balanceBeforeDuplicate = balanceQuantity(fixture.warehouseId(), fixture.materialId());
		long movementCountBeforeDuplicate = movementCountBySource("PURCHASE_RECEIPT", receiptLineId);
		assertErrorIn(postReceipt(admin, receiptId), HttpStatus.CONFLICT,
				List.of("PROCUREMENT_DUPLICATE_POST", "PROCUREMENT_MOVEMENT_SOURCE_DUPLICATED"));
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), balanceBeforeDuplicate.toPlainString());
		assertThat(movementCountBySource("PURCHASE_RECEIPT", receiptLineId)).isEqualTo(movementCountBeforeDuplicate);
		assertError(updateReceipt(admin, receiptId,
				receiptPayload(fixture.warehouseId(), "已过账不可更新",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "1.000000",
								null)))),
				HttpStatus.CONFLICT, "PROCUREMENT_RECEIPT_POSTED_IMMUTABLE");
		assertError(cancelOrder(admin, orderId), HttpStatus.CONFLICT, "PROCUREMENT_ORDER_STATUS_INVALID");

		long exceededOrderId = createAndConfirmOrder(admin, fixture, "超额入库", "1.000000");
		long exceededOrderLineId = firstLine(data(getOrder(admin, exceededOrderId))).get("id").longValue();
		assertError(createReceipt(admin, exceededOrderId,
				receiptPayload(fixture.warehouseId(), "超额入库",
						List.of(receiptLine(1, exceededOrderLineId, fixture.materialId(), fixture.unitId(),
								"1.000001", null)))),
				HttpStatus.CONFLICT, "PROCUREMENT_RECEIPT_EXCEEDS_ORDER");
	}

	@Test
	void invalidMasterDataAndPayloadsReturnControlledProcurementErrors() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = fixture();

		assertError(createOrder(admin,
				orderPayload(fixture.disabledSupplierId(), "停用供应商",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1.000000", "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_SUPPLIER_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.supplierId(), "停用物料",
						List.of(orderLine(1, fixture.disabledMaterialId(), fixture.unitId(), "1.000000", "1.000000",
								null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_MATERIAL_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.supplierId(), "自制物料不可采购",
						List.of(orderLine(1, fixture.selfMadeMaterialId(), fixture.unitId(), "1.000000", "1.000000",
								null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_MATERIAL_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.supplierId(), "单位错误",
						List.of(orderLine(1, fixture.materialId(), fixture.otherUnitId(), "1.000000", "1.000000",
								null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_UNIT_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.supplierId(), "重复物料",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1.000000", "1.000000", null),
								orderLine(2, fixture.materialId(), fixture.unitId(), "2.000000", "1.000000", null)))),
				HttpStatus.CONFLICT, "PROCUREMENT_ORDER_DUPLICATE_LINE");
		assertError(createOrder(admin,
				orderPayload(fixture.supplierId(), "数量为零",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "0", "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_QUANTITY_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.supplierId(), "单价为负",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1.000000", "-0.000001",
								null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_UNIT_PRICE_INVALID");
		assertError(createOrder(admin, orderPayload(fixture.supplierId(), "空明细", List.of())),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_ORDER_EMPTY_LINES");

		long confirmSupplierOrderId = createOrderId(admin,
				orderPayload(fixture.supplierId(), "确认时供应商停用",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1.000000", "1.000000", null))));
		disableSupplier(fixture.supplierId());
		assertError(confirmOrder(admin, confirmSupplierOrderId), HttpStatus.BAD_REQUEST,
				"PROCUREMENT_SUPPLIER_INVALID");

		ProcurementFixture receiptFixture = fixture();
		long orderId = createAndConfirmOrder(admin, receiptFixture, "入库校验", "3.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		long otherOrderId = createAndConfirmOrder(admin, receiptFixture, "其他订单", "1.000000");
		long otherOrderLineId = firstLine(data(getOrder(admin, otherOrderId))).get("id").longValue();

		assertError(createReceipt(admin, orderId,
				receiptPayload(receiptFixture.disabledWarehouseId(), "停用仓库",
						List.of(receiptLine(1, orderLineId, receiptFixture.materialId(), receiptFixture.unitId(),
								"1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_WAREHOUSE_INVALID");
		assertError(createReceipt(admin, orderId,
				receiptPayload(receiptFixture.warehouseId(), "来源订单行不匹配",
						List.of(receiptLine(1, otherOrderLineId, receiptFixture.materialId(),
								receiptFixture.unitId(), "1.000000", null)))),
				HttpStatus.CONFLICT, "PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID");
		assertError(createReceipt(admin, orderId,
				receiptPayload(receiptFixture.warehouseId(), "重复来源行",
						List.of(receiptLine(1, orderLineId, receiptFixture.materialId(), receiptFixture.unitId(),
								"1.000000", null),
								receiptLine(2, orderLineId, receiptFixture.materialId(), receiptFixture.unitId(),
										"1.000000", null)))),
				HttpStatus.CONFLICT, "PROCUREMENT_RECEIPT_DUPLICATE_LINE");
		assertError(createReceipt(admin, orderId, receiptPayload(receiptFixture.warehouseId(), "空入库明细", List.of())),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_RECEIPT_EMPTY_LINES");

		long postWarehouseOrderId = createAndConfirmOrder(admin, receiptFixture, "过账仓库停用", "1.000000");
		long postWarehouseLineId = firstLine(data(getOrder(admin, postWarehouseOrderId))).get("id").longValue();
		long postWarehouseReceiptId = createReceiptId(admin, postWarehouseOrderId,
				receiptPayload(receiptFixture.warehouseId(), "过账前停用仓库",
						List.of(receiptLine(1, postWarehouseLineId, receiptFixture.materialId(),
								receiptFixture.unitId(), "1.000000", null))));
		disableWarehouse(receiptFixture.warehouseId());
		assertError(postReceipt(admin, postWarehouseReceiptId), HttpStatus.BAD_REQUEST,
				"PROCUREMENT_WAREHOUSE_INVALID");
	}

	@Test
	void receiptCreateUpdateAndPostRequireCurrentMaterialAndUnitValidity() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		ProcurementFixture createMaterialFixture = fixture();
		long createMaterialOrderId = createAndConfirmOrder(admin, createMaterialFixture, "入库创建物料停用",
				"1.000000");
		long createMaterialLineId = firstLine(data(getOrder(admin, createMaterialOrderId))).get("id").longValue();
		disableMaterial(createMaterialFixture.materialId());
		assertError(createReceipt(admin, createMaterialOrderId,
				receiptPayload(createMaterialFixture.warehouseId(), "物料停用后创建入库",
						List.of(receiptLine(1, createMaterialLineId, createMaterialFixture.materialId(),
								createMaterialFixture.unitId(), "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_MATERIAL_INVALID");

		ProcurementFixture createSourceFixture = fixture();
		long createSourceOrderId = createAndConfirmOrder(admin, createSourceFixture, "入库创建物料来源变更",
				"1.000000");
		long createSourceLineId = firstLine(data(getOrder(admin, createSourceOrderId))).get("id").longValue();
		changeMaterialSource(createSourceFixture.materialId(), "SELF_MADE");
		assertError(createReceipt(admin, createSourceOrderId,
				receiptPayload(createSourceFixture.warehouseId(), "物料来源变更后创建入库",
						List.of(receiptLine(1, createSourceLineId, createSourceFixture.materialId(),
								createSourceFixture.unitId(), "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_MATERIAL_INVALID");

		ProcurementFixture createUnitFixture = fixture();
		long createUnitOrderId = createAndConfirmOrder(admin, createUnitFixture, "入库创建物料基本单位变更",
				"1.000000");
		long createUnitLineId = firstLine(data(getOrder(admin, createUnitOrderId))).get("id").longValue();
		changeMaterialUnit(createUnitFixture.materialId(), createUnitFixture.otherUnitId());
		assertError(createReceipt(admin, createUnitOrderId,
				receiptPayload(createUnitFixture.warehouseId(), "物料基本单位变更后创建入库",
						List.of(receiptLine(1, createUnitLineId, createUnitFixture.materialId(),
								createUnitFixture.unitId(), "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_UNIT_INVALID");

		ProcurementFixture updateMaterialFixture = fixture();
		long updateMaterialOrderId = createAndConfirmOrder(admin, updateMaterialFixture, "入库更新物料停用",
				"1.000000");
		long updateMaterialLineId = firstLine(data(getOrder(admin, updateMaterialOrderId))).get("id").longValue();
		long updateMaterialReceiptId = createReceiptId(admin, updateMaterialOrderId,
				receiptPayload(updateMaterialFixture.warehouseId(), "更新前有效入库",
						List.of(receiptLine(1, updateMaterialLineId, updateMaterialFixture.materialId(),
								updateMaterialFixture.unitId(), "1.000000", null))));
		disableMaterial(updateMaterialFixture.materialId());
		assertError(updateReceipt(admin, updateMaterialReceiptId,
				receiptPayload(updateMaterialFixture.warehouseId(), "物料停用后更新入库",
						List.of(receiptLine(1, updateMaterialLineId, updateMaterialFixture.materialId(),
								updateMaterialFixture.unitId(), "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_MATERIAL_INVALID");

		ProcurementFixture updateUnitFixture = fixture();
		long updateUnitOrderId = createAndConfirmOrder(admin, updateUnitFixture, "入库更新单位停用", "1.000000");
		long updateUnitLineId = firstLine(data(getOrder(admin, updateUnitOrderId))).get("id").longValue();
		long updateUnitReceiptId = createReceiptId(admin, updateUnitOrderId,
				receiptPayload(updateUnitFixture.warehouseId(), "更新前单位有效入库",
						List.of(receiptLine(1, updateUnitLineId, updateUnitFixture.materialId(),
								updateUnitFixture.unitId(), "1.000000", null))));
		disableUnit(updateUnitFixture.unitId());
		assertError(updateReceipt(admin, updateUnitReceiptId,
				receiptPayload(updateUnitFixture.warehouseId(), "单位停用后更新入库",
						List.of(receiptLine(1, updateUnitLineId, updateUnitFixture.materialId(),
								updateUnitFixture.unitId(), "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_UNIT_INVALID");

		ProcurementFixture postMaterialFixture = fixture();
		long postMaterialOrderId = createAndConfirmOrder(admin, postMaterialFixture, "入库过账物料停用",
				"1.000000");
		long postMaterialLineId = firstLine(data(getOrder(admin, postMaterialOrderId))).get("id").longValue();
		long postMaterialReceiptId = createReceiptId(admin, postMaterialOrderId,
				receiptPayload(postMaterialFixture.warehouseId(), "过账前物料有效入库",
						List.of(receiptLine(1, postMaterialLineId, postMaterialFixture.materialId(),
								postMaterialFixture.unitId(), "1.000000", null))));
		long postMaterialReceiptLineId = receiptLineId(postMaterialReceiptId);
		disableMaterial(postMaterialFixture.materialId());
		assertPostReceiptRejectedWithoutSideEffects(admin, postMaterialReceiptId, postMaterialReceiptLineId,
				postMaterialLineId, postMaterialFixture.warehouseId(), postMaterialFixture.materialId(),
				"PROCUREMENT_MATERIAL_INVALID");

		ProcurementFixture postUnitFixture = fixture();
		long postUnitOrderId = createAndConfirmOrder(admin, postUnitFixture, "入库过账单位停用", "1.000000");
		long postUnitLineId = firstLine(data(getOrder(admin, postUnitOrderId))).get("id").longValue();
		long postUnitReceiptId = createReceiptId(admin, postUnitOrderId,
				receiptPayload(postUnitFixture.warehouseId(), "过账前单位有效入库",
						List.of(receiptLine(1, postUnitLineId, postUnitFixture.materialId(), postUnitFixture.unitId(),
								"1.000000", null))));
		long postUnitReceiptLineId = receiptLineId(postUnitReceiptId);
		disableUnit(postUnitFixture.unitId());
		assertPostReceiptRejectedWithoutSideEffects(admin, postUnitReceiptId, postUnitReceiptLineId, postUnitLineId,
				postUnitFixture.warehouseId(), postUnitFixture.materialId(), "PROCUREMENT_UNIT_INVALID");
	}

	@Test
	void listKeywordsMatchMaterialAndSupplierFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = fixture();
		long orderId = createAndConfirmOrder(admin, fixture, "列表关键词", "2.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		long receiptId = createReceiptId(admin, orderId,
				receiptPayload(fixture.warehouseId(), "列表关键词入库",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "1.000000",
								null))));

		assertItemsContain(get(keywordPath("/api/admin/procurement/orders", materialCode(fixture.materialId())),
				admin), orderId);
		assertItemsContain(get(keywordPath("/api/admin/procurement/orders", materialName(fixture.materialId())),
				admin), orderId);
		assertItemsContain(get(keywordPath("/api/admin/procurement/receipts", materialCode(fixture.materialId())),
				admin), receiptId);
		assertItemsContain(get(keywordPath("/api/admin/procurement/receipts", materialName(fixture.materialId())),
				admin), receiptId);
		assertItemsContain(get(keywordPath("/api/admin/procurement/receipts", supplierCode(fixture.supplierId())),
				admin), receiptId);
	}

	@Test
	void authenticationAuthorizationAndCsrfAreEnforced() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = fixture();
		long orderId = createAndConfirmOrder(admin, fixture, "权限订单", "2.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		long receiptId = createReceiptId(admin, orderId,
				receiptPayload(fixture.warehouseId(), "权限入库",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "1.000000",
								null))));

		assertUnauthorized(get("/api/admin/procurement/orders", null));
		assertUnauthorized(get("/api/admin/procurement/orders/" + orderId, null));
		assertUnauthorized(get("/api/admin/procurement/receipts", null));
		assertUnauthorized(get("/api/admin/procurement/receipts/" + receiptId, null));

		AuthenticatedSession reader = createProcurementUserAndLogin("proc-reader-", "PROC_READER_",
				List.of("procurement:order:view", "procurement:receipt:view"));
		assertOk(get("/api/admin/procurement/orders", reader));
		assertOk(get("/api/admin/procurement/orders/" + orderId, reader));
		assertOk(get("/api/admin/procurement/receipts", reader));
		assertOk(get("/api/admin/procurement/receipts/" + receiptId, reader));
		assertForbidden(createOrder(reader,
				orderPayload(fixture.supplierId(), "只读创建",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1.000000", "1.000000", null)))));
		assertForbidden(confirmOrder(reader, orderId));
		assertForbidden(createReceipt(reader, orderId,
				receiptPayload(fixture.warehouseId(), "只读入库",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "1.000000",
								null)))));
		assertForbidden(postReceipt(reader, receiptId));

		AuthenticatedSession noProcurement = createProcurementUserAndLogin("proc-none-", "PROC_NONE_", List.of());
		assertForbidden(get("/api/admin/procurement/orders", noProcurement));
		assertForbidden(get("/api/admin/procurement/receipts", noProcurement));

		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/procurement/orders",
				orderPayload(fixture.supplierId(), "无 CSRF 创建",
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1.000000", "1.000000", null))),
				admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.PUT, "/api/admin/procurement/orders/" + orderId + "/confirm",
				null, admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/procurement/orders/" + orderId + "/receipts",
				receiptPayload(fixture.warehouseId(), "无 CSRF 入库",
						List.of(receiptLine(1, orderLineId, fixture.materialId(), fixture.unitId(), "1.000000",
								null))),
				admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.PUT, "/api/admin/procurement/receipts/" + receiptId + "/post",
				null, admin));
	}

	@Test
	void lockedPeriodRejectsPurchaseOrderConfirmationCancellationAndClosing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = fixture();
		LocalDate date = LocalDate.of(2091, 7, 10);
		Map<String, Object> confirmPayload = new LinkedHashMap<>(orderPayload(fixture.supplierId(), "期间锁定确认测试",
				List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1", "1", null))));
		confirmPayload.put("orderDate", date.toString());
		long confirmId = createOrderId(admin, confirmPayload);
		Map<String, Object> cancelPayload = new LinkedHashMap<>(orderPayload(fixture.supplierId(), "期间锁定取消测试",
				List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1", "1", null))));
		cancelPayload.put("orderDate", date.toString());
		long cancelId = createOrderId(admin, cancelPayload);
		Map<String, Object> closePayload = new LinkedHashMap<>(orderPayload(fixture.supplierId(), "期间锁定关闭测试",
				List.of(orderLine(1, fixture.materialId(), fixture.unitId(), "1", "1", null))));
		closePayload.put("orderDate", date.toString());
		long closeId = createOrderId(admin, closePayload);
		assertOk(confirmOrder(admin, closeId));
		lockPeriod(date);
		assertError(confirmOrder(admin, confirmId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertError(cancelOrder(admin, cancelId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertError(closeOrder(admin, closeId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at) values (?, ?, ?, ?, 'LOCKED', now(), now())",
				"LOCK-" + date, "锁定期间", date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
	}

	private long createAndConfirmOrder(AuthenticatedSession session, ProcurementFixture fixture, String remark,
			String quantity) throws Exception {
		long orderId = createOrderId(session,
				orderPayload(fixture.supplierId(), remark,
						List.of(orderLine(1, fixture.materialId(), fixture.unitId(), quantity, "1.000000", null))));
		assertOk(confirmOrder(session, orderId));
		return orderId;
	}

	private long createOrderId(AuthenticatedSession session, Map<String, Object> payload) throws Exception {
		ResponseEntity<String> response = createOrder(session, payload);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createReceiptId(AuthenticatedSession session, long orderId, Map<String, Object> payload)
			throws Exception {
		ResponseEntity<String> response = createReceipt(session, orderId, payload);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private Map<String, Object> orderPayload(long supplierId, String remark, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("orderDate", LocalDate.now().toString());
		payload.put("expectedArrivalDate", LocalDate.now().plusDays(3).toString());
		payload.put("remark", remark);
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> orderLine(int lineNo, long materialId, Long unitId, String quantity, String unitPrice,
			String remark) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("materialId", materialId);
		if (unitId != null) {
			line.put("unitId", unitId);
		}
		line.put("quantity", quantity);
		line.put("unitPrice", unitPrice);
		line.put("expectedArrivalDate", LocalDate.now().plusDays(3).toString());
		if (remark != null) {
			line.put("remark", remark);
		}
		return line;
	}

	private Map<String, Object> receiptPayload(long warehouseId, String remark, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", warehouseId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("remark", remark);
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> receiptLine(int lineNo, long orderLineId, long materialId, long unitId, String quantity,
			String remark) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("orderLineId", orderLineId);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("quantity", quantity);
		if (remark != null) {
			line.put("remark", remark);
		}
		return line;
	}

	private ProcurementFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("PROC_UNIT_" + suffix, "采购单位" + suffix);
		long otherUnitId = insertUnit("PROC_OTHER_UNIT_" + suffix, "采购其他单位" + suffix);
		long warehouseId = insertWarehouse("PROC_WH_" + suffix, "采购仓" + suffix, "ENABLED");
		long disabledWarehouseId = insertWarehouse("PROC_OFF_WH_" + suffix, "停用采购仓" + suffix, "DISABLED");
		long supplierId = insertSupplier("PROC_SUP_" + suffix, "采购供应商" + suffix, "ENABLED");
		long disabledSupplierId = insertSupplier("PROC_OFF_SUP_" + suffix, "停用供应商" + suffix, "DISABLED");
		long categoryId = insertMaterialCategory("PROC_CAT_" + suffix);
		long materialId = insertMaterial("PROC_MAT_" + suffix, "采购物料" + suffix, "RAW_MATERIAL", "PURCHASED",
				categoryId, unitId, "ENABLED");
		long secondMaterialId = insertMaterial("PROC_MAT2_" + suffix, "采购物料二" + suffix, "AUXILIARY",
				"PURCHASED", categoryId, unitId, "ENABLED");
		long disabledMaterialId = insertMaterial("PROC_OFF_MAT_" + suffix, "停用采购物料" + suffix, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId, "DISABLED");
		long selfMadeMaterialId = insertMaterial("PROC_SELF_" + suffix, "自制物料" + suffix, "FINISHED_GOOD",
				"SELF_MADE", categoryId, unitId, "ENABLED");
		return new ProcurementFixture(unitId, otherUnitId, warehouseId, disabledWarehouseId, supplierId,
				disabledSupplierId, categoryId, materialId, secondMaterialId, disabledMaterialId, selfMadeMaterialId);
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

	private long insertSupplier(String code, String name, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, status);
	}

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "采购分类" + code);
	}

	private long insertMaterial(String code, String name, String materialType, String sourceType, long categoryId,
			long unitId, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, "采购规格", materialType, sourceType, categoryId, unitId, status);
	}

	private void disableSupplier(long supplierId) {
		this.jdbcTemplate.update("update mst_supplier set status = 'DISABLED', updated_at = now() where id = ?",
				supplierId);
	}

	private void disableMaterial(long materialId) {
		this.jdbcTemplate.update("update mst_material set status = 'DISABLED', updated_at = now() where id = ?",
				materialId);
	}

	private void changeMaterialSource(long materialId, String sourceType) {
		this.jdbcTemplate.update("update mst_material set source_type = ?, updated_at = now() where id = ?",
				sourceType, materialId);
	}

	private void changeMaterialUnit(long materialId, long unitId) {
		this.jdbcTemplate.update("update mst_material set unit_id = ?, updated_at = now() where id = ?", unitId,
				materialId);
	}

	private void disableUnit(long unitId) {
		this.jdbcTemplate.update("update mst_unit set status = 'DISABLED', updated_at = now() where id = ?", unitId);
	}

	private void disableWarehouse(long warehouseId) {
		this.jdbcTemplate.update("update mst_warehouse set status = 'DISABLED', updated_at = now() where id = ?",
				warehouseId);
	}

	private BigDecimal balanceQuantity(long warehouseId, long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				""", BigDecimal.class, warehouseId, materialId);
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

	private String movementQualityStatus(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select quality_status
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", String.class, sourceType, sourceLineId);
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

	private MovementRow movementForSource(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select movement_type, direction, source_id, source_line_id, quantity, before_quantity, after_quantity
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""",
				(rs, rowNum) -> new MovementRow(rs.getString("movement_type"), rs.getString("direction"),
						rs.getLong("source_id"), rs.getLong("source_line_id"), rs.getBigDecimal("quantity"),
						rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity")),
				sourceType, sourceLineId);
	}

	private long movementCountBySource(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", Long.class, sourceType, sourceLineId);
	}

	private BigDecimal orderLineReceivedQuantity(long orderLineId) {
		return this.jdbcTemplate.queryForObject("""
				select received_quantity
				from proc_purchase_order_line
				where id = ?
				""", BigDecimal.class, orderLineId);
	}

	private String receiptStatus(long receiptId) {
		return this.jdbcTemplate.queryForObject("""
				select status
				from proc_purchase_receipt
				where id = ?
				""", String.class, receiptId);
	}

	private long receiptLineId(long receiptId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from proc_purchase_receipt_line
				where receipt_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, receiptId);
	}

	private String materialCode(long materialId) {
		return this.jdbcTemplate.queryForObject("select code from mst_material where id = ?", String.class,
				materialId);
	}

	private String materialName(long materialId) {
		return this.jdbcTemplate.queryForObject("select name from mst_material where id = ?", String.class,
				materialId);
	}

	private String warehouseName(long warehouseId) {
		return this.jdbcTemplate.queryForObject("select name from mst_warehouse where id = ?", String.class,
				warehouseId);
	}

	private String supplierCode(long supplierId) {
		return this.jdbcTemplate.queryForObject("select code from mst_supplier where id = ?", String.class,
				supplierId);
	}

	private void assertPostReceiptRejectedWithoutSideEffects(AuthenticatedSession admin, long receiptId,
			long receiptLineId, long orderLineId, long warehouseId, long materialId, String expectedCode)
			throws Exception {
		BigDecimal balanceBefore = balanceQuantity(warehouseId, materialId);
		long movementCountBefore = movementCountBySource("PURCHASE_RECEIPT", receiptLineId);
		BigDecimal receivedBefore = orderLineReceivedQuantity(orderLineId);
		String statusBefore = receiptStatus(receiptId);
		assertError(postReceipt(admin, receiptId), HttpStatus.BAD_REQUEST, expectedCode);
		assertDecimal(balanceQuantity(warehouseId, materialId), balanceBefore.toPlainString());
		assertThat(movementCountBySource("PURCHASE_RECEIPT", receiptLineId)).isEqualTo(movementCountBefore);
		assertDecimal(orderLineReceivedQuantity(orderLineId), receivedBefore.toPlainString());
		assertThat(receiptStatus(receiptId)).isEqualTo(statusBefore);
	}

	private void assertAuditLog(String action, String targetType, long targetId) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				""", Long.class, action, targetType, Long.toString(targetId))).as(action).isOne();
	}

	private AuthenticatedSession createProcurementUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "采购测试角色" + suffix, "采购测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "采购测试用户" + suffix);
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

	private ResponseEntity<String> createOrder(AuthenticatedSession session, Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/procurement/orders", body, session);
	}

	private ResponseEntity<String> updateOrder(AuthenticatedSession session, long orderId, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/procurement/orders/" + orderId, body, session);
	}

	private ResponseEntity<String> getOrder(AuthenticatedSession session, long orderId) {
		return get("/api/admin/procurement/orders/" + orderId, session);
	}

	private ResponseEntity<String> confirmOrder(AuthenticatedSession session, long orderId) {
		return exchange(HttpMethod.PUT, "/api/admin/procurement/orders/" + orderId + "/confirm", null, session);
	}

	private ResponseEntity<String> cancelOrder(AuthenticatedSession session, long orderId) {
		return exchange(HttpMethod.PUT, "/api/admin/procurement/orders/" + orderId + "/cancel", null, session);
	}

	private ResponseEntity<String> closeOrder(AuthenticatedSession session, long orderId) {
		return exchange(HttpMethod.PUT, "/api/admin/procurement/orders/" + orderId + "/close", null, session);
	}

	private ResponseEntity<String> createReceipt(AuthenticatedSession session, long orderId, Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/procurement/orders/" + orderId + "/receipts", body, session);
	}

	private ResponseEntity<String> getReceipt(AuthenticatedSession session, long receiptId) {
		return get("/api/admin/procurement/receipts/" + receiptId, session);
	}

	private ResponseEntity<String> updateReceipt(AuthenticatedSession session, long receiptId,
			Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/procurement/receipts/" + receiptId, body, session);
	}

	private ResponseEntity<String> postReceipt(AuthenticatedSession session, long receiptId) {
		return exchange(HttpMethod.PUT, "/api/admin/procurement/receipts/" + receiptId + "/post", null, session);
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

	private JsonNode firstLine(JsonNode detail) {
		JsonNode lines = detail.get("lines");
		assertThat(lines.size()).isGreaterThan(0);
		return lines.get(0);
	}

	private String keywordPath(String path, String keyword) {
		return path + "?keyword=" + keyword;
	}

	private List<JsonNode> items(ResponseEntity<String> response) throws Exception {
		JsonNode items = data(response).get("items");
		List<JsonNode> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			result.add(items.get(i));
		}
		return result;
	}

	private void assertItemsContain(ResponseEntity<String> response, long id) throws Exception {
		assertOk(response);
		assertThat(items(response)).anySatisfy((item) -> assertThat(item.get("id").longValue()).isEqualTo(id));
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

	private void assertErrorIn(ResponseEntity<String> response, HttpStatus status, List<String> codes) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(codes).contains(code(response));
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

	private record ProcurementFixture(long unitId, long otherUnitId, long warehouseId, long disabledWarehouseId,
			long supplierId, long disabledSupplierId, long categoryId, long materialId, long secondMaterialId,
			long disabledMaterialId, long selfMadeMaterialId) {
	}

	private record MovementRow(String movementType, String direction, long sourceId, long sourceLineId,
			BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity) {
	}

}
