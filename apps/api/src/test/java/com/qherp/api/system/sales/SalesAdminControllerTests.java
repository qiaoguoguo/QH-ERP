package com.qherp.api.system.sales;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
		properties = "qherp.test.context=sales-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SalesAdminControllerTests extends PostgresIntegrationTest {

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

	private Long defaultReservationWarehouseId;

	@Test
	void adminCanRunSalesOrderAndShipmentLifecycle() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);

		ResponseEntity<String> created = createOrder(admin,
				orderPayload(fixture.customerId(), "销售订单创建",
						List.of(orderLine(1, fixture.finishedMaterialId(), null, "5.000000", "10.500000", "成品行"),
								orderLine(2, fixture.semiFinishedMaterialId(), fixture.unitId(), "4.000000",
										"8.125000", "半成品行"))));
		assertOk(created);
		JsonNode createdData = data(created);
		long orderId = createdData.get("id").longValue();
		assertThat(createdData.get("status").asText()).isEqualTo("DRAFT");
		assertThat(createdData.get("lineCount").intValue()).isEqualTo(2);
		assertDecimal(createdData, "totalQuantity", "9.000000");
		assertDecimal(createdData, "shippedQuantity", "0");
		assertDecimal(createdData, "remainingQuantity", "9.000000");
		assertThat(firstLine(createdData).get("unitId").longValue()).isEqualTo(fixture.unitId());
		assertDecimal(firstLine(createdData), "unitPrice", "10.500000");

		ResponseEntity<String> updated = updateOrder(admin, orderId,
				orderPayload(fixture.customerId(), "销售订单更新",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "6.000000",
								"12.345678", "更新成品行"),
								orderLine(2, fixture.semiFinishedMaterialId(), fixture.unitId(), "4.000000",
										"0.000000", "更新半成品行"))));
		assertOk(updated);
		JsonNode updatedData = data(updated);
		JsonNode finishedLine = firstLine(updatedData);
		JsonNode semiFinishedLine = updatedData.get("lines").get(1);
		long finishedOrderLineId = finishedLine.get("id").longValue();
		long semiFinishedOrderLineId = semiFinishedLine.get("id").longValue();
		assertDecimal(finishedLine, "quantity", "6.000000");
		assertDecimal(finishedLine, "unitPrice", "12.345678");
		assertDecimal(semiFinishedLine, "unitPrice", "0.000000");

		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "10.000000");
		seedStock(fixture.warehouseId(), fixture.semiFinishedMaterialId(), fixture.unitId(), "10.000000");
		assertOk(confirmOrder(admin, orderId));

		long draftCancelId = createOrderId(admin,
				orderPayload(fixture.customerId(), "草稿取消",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))));
		assertOk(cancelOrder(admin, draftCancelId));

		long confirmedCancelId = createAndConfirmOrder(admin, fixture, "确认后取消", "1.000000");
		assertOk(cancelOrder(admin, confirmedCancelId));

		ResponseEntity<String> shipmentCreated = createShipment(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "销售出库创建",
						List.of(shipmentLine(1, finishedOrderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", "首次出库行"))));
		assertOk(shipmentCreated);
		long shipmentId = data(shipmentCreated).get("id").longValue();
		assertThat(data(shipmentCreated).get("status").asText()).isEqualTo("DRAFT");

		ResponseEntity<String> shipmentUpdated = updateShipment(admin, shipmentId,
				shipmentPayload(fixture.warehouseId(), "销售出库更新",
						List.of(shipmentLine(1, finishedOrderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"2.000000", "更新出库行"))));
		assertOk(shipmentUpdated);
		assertDecimal(firstLine(data(shipmentUpdated)), "quantity", "2.000000");

		ResponseEntity<String> posted = postShipment(admin, shipmentId);
		assertOk(posted);
		JsonNode postedData = data(posted);
		JsonNode postedLine = firstLine(postedData);
		long shipmentLineId = postedLine.get("id").longValue();
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertDecimal(postedLine, "beforeQuantity", "10.000000");
		assertDecimal(postedLine, "afterQuantity", "8.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId()), "8.000000");

		MovementRow movement = movementForSource("SALES_SHIPMENT", shipmentLineId);
		assertThat(movement.movementType()).isEqualTo("SALES_SHIPMENT");
		assertThat(movement.direction()).isEqualTo("OUT");
		assertThat(movement.sourceId()).isEqualTo(shipmentId);
		assertDecimal(movement.quantity(), "2.000000");
		assertDecimal(movement.beforeQuantity(), "10.000000");
		assertDecimal(movement.afterQuantity(), "8.000000");

		JsonNode shipmentDetail = data(getShipment(admin, shipmentId));
		JsonNode inventoryMovements = shipmentDetail.get("inventoryMovements");
		assertThat(inventoryMovements).isNotNull();
		assertThat(inventoryMovements.size()).isOne();
		assertThat(inventoryMovements.get(0).get("movementType").asText()).isEqualTo("SALES_SHIPMENT");
		assertThat(inventoryMovements.get(0).get("direction").asText()).isEqualTo("OUT");

		JsonNode partiallyShipped = data(getOrder(admin, orderId));
		assertThat(partiallyShipped.get("status").asText()).isEqualTo("PARTIALLY_SHIPPED");
		assertDecimal(partiallyShipped, "shippedQuantity", "2.000000");
		assertDecimal(partiallyShipped, "remainingQuantity", "8.000000");

		long secondShipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "销售出库完成",
						List.of(shipmentLine(1, finishedOrderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"4.000000", "成品完成出库"),
								shipmentLine(2, semiFinishedOrderLineId, fixture.semiFinishedMaterialId(),
										fixture.unitId(), "4.000000", "半成品完成出库"))));
		assertOk(postShipment(admin, secondShipmentId));
		JsonNode shipped = data(getOrder(admin, orderId));
		assertThat(shipped.get("status").asText()).isEqualTo("SHIPPED");
		assertDecimal(shipped, "shippedQuantity", "10.000000");
		assertDecimal(shipped, "remainingQuantity", "0.000000");

		ResponseEntity<String> closed = closeOrder(admin, orderId);
		assertOk(closed);
		assertThat(data(closed).get("status").asText()).isEqualTo("CLOSED");

		assertAuditLog("SALES_ORDER_CREATE", "SALES_ORDER", orderId);
		assertAuditLog("SALES_ORDER_UPDATE", "SALES_ORDER", orderId);
		assertAuditLog("SALES_ORDER_CONFIRM", "SALES_ORDER", orderId);
		assertAuditLog("SALES_ORDER_CLOSE", "SALES_ORDER", orderId);
		assertAuditLog("SALES_ORDER_CANCEL", "SALES_ORDER", draftCancelId);
		assertAuditLog("SALES_SHIPMENT_CREATE", "SALES_SHIPMENT", shipmentId);
		assertAuditLog("SALES_SHIPMENT_UPDATE", "SALES_SHIPMENT", shipmentId);
		assertAuditLog("SALES_SHIPMENT_POST", "SALES_SHIPMENT", shipmentId);
	}

	@Test
	void salesOrderConfirmationReservesAndShipmentConsumesQualifiedStock() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "10.000000");

		ResponseEntity<String> created = createOrder(admin,
				orderPayload(fixture.customerId(), "018 销售预留",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "6.000000",
								"1.000000", null))));
		assertOk(created);
		long orderId = data(created).get("id").longValue();
		long orderLineId = firstLine(data(created)).get("id").longValue();
		assertOk(confirmOrder(admin, orderId));
		JsonNode confirmedLine = firstLine(data(getOrder(admin, orderId)));
		assertThat(confirmedLine.get("reservationWarehouseId").longValue()).isEqualTo(fixture.warehouseId());
		assertThat(confirmedLine.get("reservationWarehouseName").asText()).isNotBlank();
		assertDecimal(confirmedLine, "reservedQuantity", "0.000000");
		assertDecimal(confirmedLine, "occupiedQuantity", "0.000000");
		assertDecimal(confirmedLine, "availableToPromiseQuantity", "10.000000");
		assertDecimal(confirmedLine, "maxSelectableQuantity", "6.000000");

		JsonNode reservedBalance = firstItem(get("/api/admin/inventory/balances?warehouseId="
				+ fixture.warehouseId() + "&materialId=" + fixture.finishedMaterialId(), admin));
		assertDecimal(reservedBalance, "bookQuantity", "10.000000");
		assertDecimal(reservedBalance, "reservedQuantity", "6.000000");
		assertDecimal(reservedBalance, "availableQuantity", "4.000000");
		JsonNode reservation = firstItem(get("/api/admin/inventory/reservations?sourceType=SALES_ORDER&sourceId="
				+ orderId, admin));
		assertThat(reservation.get("sourceLineId").longValue()).isEqualTo(orderLineId);
		assertDecimal(reservation, "remainingQuantity", "6.000000");

		long shipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "018 销售预留消耗",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"2.000000", null))));
		JsonNode shipmentLine = firstLine(data(getShipment(admin, shipmentId)));
		assertDecimal(shipmentLine, "reservedQuantity", "0.000000");
		assertDecimal(shipmentLine, "occupiedQuantity", "0.000000");
		assertDecimal(shipmentLine, "availableToPromiseQuantity", "10.000000");
		assertDecimal(shipmentLine, "maxSelectableQuantity", "2.000000");
		assertOk(postShipment(admin, shipmentId));

		JsonNode consumedBalance = firstItem(get("/api/admin/inventory/balances?warehouseId="
				+ fixture.warehouseId() + "&materialId=" + fixture.finishedMaterialId(), admin));
		assertDecimal(consumedBalance, "bookQuantity", "8.000000");
		assertDecimal(consumedBalance, "reservedQuantity", "4.000000");
		assertDecimal(consumedBalance, "availableQuantity", "4.000000");
	}

	@Test
	void salesOrderLinesAggregateTrackedBalancesWithoutDuplicatingSourceLine() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		setTrackingMethod(fixture.finishedMaterialId(), "BATCH");
		seedBatchStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(),
				"SAL-AGG-A-" + SEQUENCE.incrementAndGet(), "2.000000");
		seedBatchStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(),
				"SAL-AGG-B-" + SEQUENCE.incrementAndGet(), "3.000000");

		ResponseEntity<String> created = createOrder(admin,
				orderPayload(fixture.customerId(), "销售多批余额聚合",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "4.000000",
								"1.000000", null))));
		assertOk(created);
		long orderId = data(created).get("id").longValue();
		JsonNode detail = data(getOrder(admin, orderId));

		assertThat(detail.get("lines").size()).isEqualTo(1);
		JsonNode line = firstLine(detail);
		assertDecimal(line, "quantityOnHand", "5.000000");
		assertDecimal(line, "availableQuantity", "5.000000");
		assertDecimal(line, "maxSelectableQuantity", "4.000000");
	}

	@Test
	void salesShipmentBatchAllocationPostsTrackingMovementAndConsumesReservation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		setTrackingMethod(fixture.finishedMaterialId(), "BATCH");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(),
				"SAL-BATCH-" + SEQUENCE.incrementAndGet(), "5.000000");

		ResponseEntity<String> created = createOrder(admin,
				orderPayload(fixture.customerId(), "销售批次出库",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "3.000000",
								"10.000000", "批次出库行"))));
		assertOk(created);
		long orderId = data(created).get("id").longValue();
		long orderLineId = firstLine(data(created)).get("id").longValue();
		assertOk(confirmOrder(admin, orderId));

		Map<String, Object> line = shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
				"3.000000", "批次出库");
		line.put("trackingAllocations", List.of(trackingAllocation(batch.batchId(), "3.000000")));
		long shipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "批次出库", List.of(line)));

		ResponseEntity<String> posted = postShipment(admin, shipmentId);
		assertOk(posted);
		JsonNode postedLine = firstLine(data(posted));
		long shipmentLineId = postedLine.get("id").longValue();

		assertThat(postedLine.get("trackingAllocations").get(0).get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId(), batch.batchId()),
				"2.000000");
		assertThat(trackingAllocationMovementCount("SALES_SHIPMENT", shipmentId, shipmentLineId)).isOne();
		assertThat(trackedMovementCount("SALES_SHIPMENT", shipmentLineId, batch.batchId())).isOne();
		JsonNode consumedBalance = firstItem(get("/api/admin/inventory/balances?warehouseId="
				+ fixture.warehouseId() + "&materialId=" + fixture.finishedMaterialId(), admin));
		assertDecimal(consumedBalance, "reservedQuantity", "0.000000");
	}

	@Test
	void concurrentSalesShipmentsSelectingSameSerialAllowOnlyOnePostWithoutPartialSideEffects() throws Exception {
		AuthenticatedSession firstAdmin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession secondAdmin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(firstAdmin);
		setTrackingMethod(fixture.finishedMaterialId(), "SERIAL");
		TrackedSerial targetSerial = seedSerialStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(),
				"SAL-SN-RACE-" + SEQUENCE.incrementAndGet());
		TrackedSerial spareSerial = seedSerialStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(),
				"SAL-SN-SPARE-" + SEQUENCE.incrementAndGet());

		long firstOrderId = createOrderId(firstAdmin,
				orderPayload(fixture.customerId(), "销售序列并发一",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"10.000000", "序列并发出库"))));
		long firstOrderLineId = firstLine(data(getOrder(firstAdmin, firstOrderId))).get("id").longValue();
		assertOk(confirmOrder(firstAdmin, firstOrderId));
		long secondOrderId = createOrderId(secondAdmin,
				orderPayload(fixture.customerId(), "销售序列并发二",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"10.000000", "序列并发出库"))));
		long secondOrderLineId = firstLine(data(getOrder(secondAdmin, secondOrderId))).get("id").longValue();
		assertOk(confirmOrder(secondAdmin, secondOrderId));

		long firstShipmentId = createSerialShipment(firstAdmin, fixture, firstOrderId, firstOrderLineId,
				targetSerial.serialId(),
				"销售序列并发一");
		long secondShipmentId = createSerialShipment(secondAdmin, fixture, secondOrderId, secondOrderLineId,
				targetSerial.serialId(),
				"销售序列并发二");
		long firstShipmentLineId = shipmentLineId(firstShipmentId);
		long secondShipmentLineId = shipmentLineId(secondShipmentId);

		ExecutorService executorService = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<ResponseEntity<String>> first = executorService.submit(() -> postShipmentAfterStart(firstShipmentId,
					firstAdmin, start));
			Future<ResponseEntity<String>> second = executorService.submit(() -> postShipmentAfterStart(secondShipmentId,
					secondAdmin, start));
			start.countDown();

			ResponseEntity<String> firstResponse = first.get(20, TimeUnit.SECONDS);
			ResponseEntity<String> secondResponse = second.get(20, TimeUnit.SECONDS);
			List<String> codes = List.of(code(firstResponse), code(secondResponse));
			assertThat(codes).contains("OK");
			assertThat(codes).contains("INVENTORY_TRACKING_NOT_AVAILABLE");
		}
		finally {
			executorService.shutdownNow();
		}

		assertDecimal(serialBalanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId(), targetSerial.serialId()),
				"0.000000");
		assertThat(serialStockStatus(targetSerial.serialId())).isEqualTo("OUTBOUND");
		assertDecimal(serialBalanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId(), spareSerial.serialId()),
				"1.000000");
		assertThat(serialStockStatus(spareSerial.serialId())).isEqualTo("IN_STOCK");
		assertThat(serialMovementCount("SALES_SHIPMENT", targetSerial.serialId())).isOne();
		assertThat(trackingAllocationMovementCount("SALES_SHIPMENT", firstShipmentId, firstShipmentLineId)
				+ trackingAllocationMovementCount("SALES_SHIPMENT", secondShipmentId, secondShipmentLineId)).isOne();
	}

	@Test
	void confirmedSalesOrderCancellationReleasesReservation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "10.000000");
		long orderId = createAndConfirmOrder(admin, fixture, "018 销售取消释放预留", "5.000000");

		JsonNode reservedBalance = firstItem(get("/api/admin/inventory/balances?warehouseId="
				+ fixture.warehouseId() + "&materialId=" + fixture.finishedMaterialId(), admin));
		assertDecimal(reservedBalance, "reservedQuantity", "5.000000");
		assertDecimal(reservedBalance, "availableQuantity", "5.000000");

		assertOk(cancelOrder(admin, orderId));

		JsonNode releasedBalance = firstItem(get("/api/admin/inventory/balances?warehouseId="
				+ fixture.warehouseId() + "&materialId=" + fixture.finishedMaterialId(), admin));
		assertDecimal(releasedBalance, "reservedQuantity", "0.000000");
		assertDecimal(releasedBalance, "availableQuantity", "10.000000");
	}

	@Test
	void salesOrderConfirmationRequiresReservationWarehouseAndSelectedWarehouseStock() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "6.000000");

		long missingWarehouseOrderId = createOrderId(admin,
				orderPayload(fixture.customerId(), "缺少预留仓库",
						List.of(orderLineWithoutReservationWarehouse(1, fixture.finishedMaterialId(),
								fixture.unitId(), "6.000000", "1.000000", null))));
		assertError(confirmOrder(admin, missingWarehouseOrderId), HttpStatus.BAD_REQUEST,
				"SALES_RESERVATION_WAREHOUSE_REQUIRED");

		long inTransitOnlyOrderId = createOrderId(admin,
				orderPayload(fixture.customerId(), "只有在途不能确认",
						List.of(orderLine(1, fixture.semiFinishedMaterialId(), fixture.unitId(), "6.000000",
								"1.000000", null))));
		insertPurchaseInTransit(fixture.semiFinishedMaterialId(), fixture.unitId(), "6.000000");
		assertError(confirmOrder(admin, inTransitOnlyOrderId), HttpStatus.CONFLICT,
				"INVENTORY_AVAILABLE_NOT_ENOUGH");
	}

	@Test
	void salesShipmentWarehouseMustMatchReservedWarehouse() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		long otherWarehouseId = insertWarehouse("SAL_OTHER_WH_" + SEQUENCE.incrementAndGet(), "销售其他仓", "ENABLED");
		long orderId = createAndConfirmOrder(admin, fixture, "出库仓库必须等于预留仓库", "2.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();

		assertError(createShipment(admin, orderId,
				shipmentPayload(otherWarehouseId, "跨预留仓出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null)))),
				HttpStatus.CONFLICT, "SALES_SHIPMENT_RESERVATION_WAREHOUSE_MISMATCH");
	}

	@Test
	void invalidOrderPayloadsReturnControlledSalesErrors() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);

		assertError(createOrder(admin,
				orderPayload(fixture.disabledCustomerId(), "停用客户",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_CUSTOMER_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "停用物料",
						List.of(orderLine(1, fixture.disabledMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_MATERIAL_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "原材料不可销售",
						List.of(orderLine(1, fixture.rawMaterialId(), fixture.unitId(), "1.000000", "1.000000",
								null)))),
				HttpStatus.BAD_REQUEST, "SALES_MATERIAL_NOT_SELLABLE");
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "辅料不可销售",
						List.of(orderLine(1, fixture.auxiliaryMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_MATERIAL_NOT_SELLABLE");
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "单位错误",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.otherUnitId(), "1.000000",
								"1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_UNIT_INVALID");
		ResponseEntity<String> sameMaterialOrder = createOrder(admin,
				orderPayload(fixture.customerId(), "同物料多行",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", "同物料第一行"),
								orderLine(2, fixture.finishedMaterialId(), fixture.unitId(), "2.000000",
										"1.000000", "同物料第二行"))));
		assertOk(sameMaterialOrder);
		assertThat(data(sameMaterialOrder).get("lines").size()).isEqualTo(2);
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "空明细", List.of())), HttpStatus.BAD_REQUEST,
				"SALES_ORDER_EMPTY_LINES");
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "数量为负",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "-0.000001",
								"1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_QUANTITY_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "单价为负",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"-0.000001", null)))),
				HttpStatus.BAD_REQUEST, "SALES_UNIT_PRICE_INVALID");
		assertError(createOrder(admin,
				orderPayload(fixture.customerId(), "单价精度超限",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.0000001", null)))),
				HttpStatus.BAD_REQUEST, "SALES_UNIT_PRICE_INVALID");

		String invalidPriceJson = """
				{
				  "customerId": %d,
				  "orderDate": "%s",
				  "lines": [
				    {
				      "lineNo": 1,
				      "materialId": %d,
				      "unitId": %d,
				      "quantity": "1.000000",
				      "unitPrice": "not-a-decimal"
				    }
				  ]
				}
				""".formatted(fixture.customerId(), LocalDate.now(), fixture.finishedMaterialId(), fixture.unitId());
		assertError(exchangeJson(HttpMethod.POST, "/api/admin/sales/orders", invalidPriceJson, admin),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		long confirmCustomerOrderId = createOrderId(admin,
				orderPayload(fixture.customerId(), "确认时客户停用",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))));
		disableCustomer(fixture.customerId());
		assertError(confirmOrder(admin, confirmCustomerOrderId), HttpStatus.BAD_REQUEST, "SALES_CUSTOMER_INVALID");
	}

	@Test
	void shipmentErrorsKeepSalesAndInventoryConsistent() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);

		long draftOrderId = createOrderId(admin,
				orderPayload(fixture.customerId(), "草稿不可出库",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))));
		long unrelatedOrderLineId = createConfirmedOrderLine(admin, fixture, "来源行参照", "1.000000");
		assertError(createShipment(admin, draftOrderId,
				shipmentPayload(fixture.warehouseId(), "草稿出库",
						List.of(shipmentLine(1, unrelatedOrderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null)))),
				HttpStatus.CONFLICT, "SALES_ORDER_STATUS_INVALID");

		long orderId = createAndConfirmOrder(admin, fixture, "出库错误", "5.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		long otherOrderLineId = createConfirmedOrderLine(admin, fixture, "其他订单", "1.000000");

		assertError(createShipment(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "来源订单行不匹配",
						List.of(shipmentLine(1, otherOrderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null)))),
				HttpStatus.CONFLICT, "SALES_SHIPMENT_LINE_SOURCE_INVALID");
		assertError(createShipment(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "重复来源行",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null),
								shipmentLine(2, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
										"1.000000", null)))),
				HttpStatus.CONFLICT, "SALES_SHIPMENT_DUPLICATE_LINE");
		assertError(createShipment(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "超订单未出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"5.000001", null)))),
				HttpStatus.CONFLICT, "SALES_SHIPMENT_EXCEEDS_ORDER");
		assertError(createShipment(admin, orderId, shipmentPayload(fixture.warehouseId(), "空出库明细", List.of())),
				HttpStatus.BAD_REQUEST, "SALES_SHIPMENT_EMPTY_LINES");

		long shipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "库存不足",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"4.000000", null))));
		long shipmentLineId = shipmentLineId(shipmentId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "1.000000");
		assertPostShipmentRejectedWithoutSideEffects(admin, shipmentId, shipmentLineId, orderLineId,
				fixture.warehouseId(), fixture.finishedMaterialId(), HttpStatus.CONFLICT, "SALES_STOCK_NOT_ENOUGH");

		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "10.000000");
		assertOk(postShipment(admin, shipmentId));
		BigDecimal balanceBeforeDuplicate = balanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId());
		long movementCountBeforeDuplicate = movementCountBySource("SALES_SHIPMENT", shipmentLineId);
		assertErrorIn(postShipment(admin, shipmentId), HttpStatus.CONFLICT,
				List.of("SALES_DUPLICATE_POST", "SALES_MOVEMENT_SOURCE_DUPLICATED"));
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId()),
				balanceBeforeDuplicate.toPlainString());
		assertThat(movementCountBySource("SALES_SHIPMENT", shipmentLineId)).isEqualTo(movementCountBeforeDuplicate);
		assertError(updateShipment(admin, shipmentId,
				shipmentPayload(fixture.warehouseId(), "已过账不可更新",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null)))),
				HttpStatus.CONFLICT, "SALES_SHIPMENT_POSTED_IMMUTABLE");
	}

	@Test
	void shipmentRejectsWhenOnlyNonQualifiedStockCanCoverRequestedQuantity() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		long orderId = createAndConfirmOrder(admin, fixture, "合格库存不足", "4.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		long shipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "合格库存不足出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"4.000000", "总库存足够但合格不足"))));
		long shipmentLineId = shipmentLineId(shipmentId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
				InventoryQualityStatus.QUALIFIED);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "3.000000",
				InventoryQualityStatus.PENDING_INSPECTION);

		assertPostShipmentRejectedWithoutSideEffects(admin, shipmentId, shipmentLineId, orderLineId,
				fixture.warehouseId(), fixture.finishedMaterialId(), HttpStatus.CONFLICT,
				"INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId(),
				InventoryQualityStatus.QUALIFIED), "1.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.finishedMaterialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "3.000000");
	}

	@Test
	void salesShipmentSourceLinesExposeQualifiedCandidateFieldsWhenOnlyPendingStockExists() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		long orderId = createAndConfirmOrder(admin, fixture, "候选字段合格不足", "4.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		setQualifiedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "0.000000");
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "4.000000",
				InventoryQualityStatus.PENDING_INSPECTION);

		JsonNode sourceLine = firstLine(data(getOrder(admin, orderId)));
		assertSalesQualifiedCandidateUnavailable(sourceLine);

		long shipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "出库候选字段",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"4.000000", "只存在待检库存"))));

		JsonNode shipmentLine = firstLine(data(getShipment(admin, shipmentId)));
		assertSalesQualifiedCandidateUnavailable(shipmentLine);
	}

	@Test
	void shipmentRequiresCurrentWarehouseMaterialAndUnitValidityButIgnoresDisabledCustomerAfterConfirm()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		SalesFixture customerFixture = fixtureWithCredit(admin);
		long customerOrderId = createAndConfirmOrder(admin, customerFixture, "客户后续停用", "1.000000");
		long customerOrderLineId = firstLine(data(getOrder(admin, customerOrderId))).get("id").longValue();
		disableCustomer(customerFixture.customerId());
		seedStock(customerFixture.warehouseId(), customerFixture.finishedMaterialId(), customerFixture.unitId(),
				"2.000000");
		long customerShipmentId = createShipmentId(admin, customerOrderId,
				shipmentPayload(customerFixture.warehouseId(), "客户停用后创建出库",
						List.of(shipmentLine(1, customerOrderLineId, customerFixture.finishedMaterialId(),
								customerFixture.unitId(), "1.000000", null))));
		assertOk(postShipment(admin, customerShipmentId));

		SalesFixture warehouseFixture = fixtureWithCredit(admin);
		long warehouseOrderId = createAndConfirmOrder(admin, warehouseFixture, "仓库停用", "1.000000");
		long warehouseOrderLineId = firstLine(data(getOrder(admin, warehouseOrderId))).get("id").longValue();
		assertError(createShipment(admin, warehouseOrderId,
				shipmentPayload(warehouseFixture.disabledWarehouseId(), "停用仓库",
						List.of(shipmentLine(1, warehouseOrderLineId, warehouseFixture.finishedMaterialId(),
								warehouseFixture.unitId(), "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_WAREHOUSE_INVALID");

		SalesFixture materialFixture = fixtureWithCredit(admin);
		long materialOrderId = createAndConfirmOrder(admin, materialFixture, "物料停用", "1.000000");
		long materialOrderLineId = firstLine(data(getOrder(admin, materialOrderId))).get("id").longValue();
		disableMaterial(materialFixture.finishedMaterialId());
		assertError(createShipment(admin, materialOrderId,
				shipmentPayload(materialFixture.warehouseId(), "物料停用后创建出库",
						List.of(shipmentLine(1, materialOrderLineId, materialFixture.finishedMaterialId(),
								materialFixture.unitId(), "1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_MATERIAL_INVALID");

		SalesFixture unitFixture = fixtureWithCredit(admin);
		long unitOrderId = createAndConfirmOrder(admin, unitFixture, "单位停用", "1.000000");
		long unitOrderLineId = firstLine(data(getOrder(admin, unitOrderId))).get("id").longValue();
		long unitShipmentId = createShipmentId(admin, unitOrderId,
				shipmentPayload(unitFixture.warehouseId(), "单位停用前创建出库",
						List.of(shipmentLine(1, unitOrderLineId, unitFixture.finishedMaterialId(),
								unitFixture.unitId(), "1.000000", null))));
		long unitShipmentLineId = shipmentLineId(unitShipmentId);
		seedStock(unitFixture.warehouseId(), unitFixture.finishedMaterialId(), unitFixture.unitId(), "2.000000");
		disableUnit(unitFixture.unitId());
		assertPostShipmentRejectedWithoutSideEffects(admin, unitShipmentId, unitShipmentLineId, unitOrderLineId,
				unitFixture.warehouseId(), unitFixture.finishedMaterialId(), HttpStatus.BAD_REQUEST,
				"SALES_UNIT_INVALID");
	}

	@Test
	void listKeywordsMatchMaterialAndCustomerFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		long orderId = createAndConfirmOrder(admin, fixture, "列表关键词", "2.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		long shipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "列表关键词出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null))));

		assertItemsContain(get(keywordPath("/api/admin/sales/orders", materialCode(fixture.finishedMaterialId())),
				admin), orderId);
		assertItemsContain(get(keywordPath("/api/admin/sales/orders", materialName(fixture.finishedMaterialId())),
				admin), orderId);
		assertItemsContain(get(keywordPath("/api/admin/sales/shipments", materialCode(fixture.finishedMaterialId())),
				admin), shipmentId);
		assertItemsContain(get(keywordPath("/api/admin/sales/shipments", materialName(fixture.finishedMaterialId())),
				admin), shipmentId);
		assertItemsContain(get(keywordPath("/api/admin/sales/shipments", customerCode(fixture.customerId())), admin),
				shipmentId);
	}

	@Test
	void salesOrderProjectContractLinkIsPersistedFilteredAuditedAndHistoricalOrdersRemainNull() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		long projectId = insertActiveProject(fixture.customerId(), "销售订单关联项目");
		long contractId = insertEffectiveMainContract(projectId, "销售订单关联合同");

		ResponseEntity<String> linked = createOrder(admin,
				orderPayloadWithProject(fixture.customerId(), "销售订单关联项目合同", projectId, contractId,
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "2.000000",
								"10.000000", null))));
		assertOk(linked);
		JsonNode linkedData = data(linked);
		long linkedOrderId = linkedData.get("id").longValue();
		assertThat(linkedData.get("projectId").longValue()).isEqualTo(projectId);
		assertThat(linkedData.get("contractId").longValue()).isEqualTo(contractId);
		assertThat(linkedData.get("projectNo").asText()).startsWith("SPC-SO-P-");
		assertThat(linkedData.get("contractNo").asText()).startsWith("SPC-SO-C-");
		String orderNo = linkedData.get("orderNo").asText();
		String firstLink = linkedData.get("projectNo").asText() + "/" + linkedData.get("contractNo").asText();
		assertProjectLinkAudit("SALES_ORDER_PROJECT_LINK", projectId, orderNo, "未关联", firstLink);

		assertItemsContain(get("/api/admin/sales/orders?projectLinked=true", admin), linkedOrderId);
		assertItemsContain(get("/api/admin/sales/orders?projectId=" + projectId, admin), linkedOrderId);
		assertError(get("/api/admin/sales/orders?projectLinked=true&projectId=" + projectId, admin),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		long secondProjectId = insertActiveProject(fixture.customerId(), "销售订单切换项目");
		long secondContractId = insertEffectiveMainContract(secondProjectId, "销售订单切换合同");
		Map<String, Object> switchPayload = orderPayloadWithProject(fixture.customerId(), "销售订单切换项目合同",
				secondProjectId, secondContractId,
				List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "2.000000", "10.000000",
						null)));
		switchPayload.put("version", linkedData.get("version").longValue());
		JsonNode switchedData = data(updateOrder(admin, linkedOrderId, switchPayload));
		String secondLink = switchedData.get("projectNo").asText() + "/" + switchedData.get("contractNo").asText();
		assertThat(switchedData.get("projectId").longValue()).isEqualTo(secondProjectId);
		assertThat(switchedData.get("contractId").longValue()).isEqualTo(secondContractId);
		assertProjectLinkAudit("SALES_ORDER_PROJECT_UNLINK", projectId, orderNo, firstLink, secondLink);
		assertProjectLinkAudit("SALES_ORDER_PROJECT_LINK", secondProjectId, orderNo, firstLink, secondLink);

		Map<String, Object> unlinkPayload = orderPayload(fixture.customerId(), "销售订单解除项目合同",
				List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "2.000000", "10.000000",
						null)));
		unlinkPayload.put("version", switchedData.get("version").longValue());
		JsonNode unlinkedData = data(updateOrder(admin, linkedOrderId, unlinkPayload));
		assertThat(unlinkedData.get("projectId").isNull()).isTrue();
		assertThat(unlinkedData.get("contractId").isNull()).isTrue();
		assertProjectLinkAudit("SALES_ORDER_PROJECT_UNLINK", secondProjectId, orderNo, secondLink, "未关联");

		long historicalOrderId = createOrderId(admin,
				orderPayload(fixture.customerId(), "历史无项目订单",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))));
		JsonNode historical = data(getOrder(admin, historicalOrderId));
		assertThat(historical.get("projectId").isNull()).isTrue();
		assertThat(historical.get("contractId").isNull()).isTrue();
		assertItemsContain(get("/api/admin/sales/orders?projectLinked=false", admin), historicalOrderId);
		ensureQualifiedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "3.000000");
		assertOk(confirmOrder(admin, historicalOrderId));
		long historicalOrderLineId = firstLine(data(getOrder(admin, historicalOrderId))).get("id").longValue();
		long historicalShipmentId = createShipmentId(admin, historicalOrderId,
				shipmentPayload(fixture.warehouseId(), "历史无项目订单出库",
						List.of(shipmentLine(1, historicalOrderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null))));
		assertOk(postShipment(admin, historicalShipmentId));
		JsonNode historicalShipment = data(getShipment(admin, historicalShipmentId));
		assertThat(historicalShipment.get("orderSummary").get("projectId").isNull()).isTrue();
		assertThat(historicalShipment.get("orderSummary").get("contractId").isNull()).isTrue();
		assertItemsContain(get("/api/admin/sales/orders?projectLinked=false", admin), historicalOrderId);

		assertOk(confirmOrder(admin, linkedOrderId));
		assertAuditLog("SALES_ORDER_PROJECT_LINK", "SALES_PROJECT", projectId);
	}

	@Test
	void salesOrderProjectContractPairAndConfirmRevalidationRejectWithoutPartialWrites() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		long projectId = insertActiveProject(fixture.customerId(), "确认再校验项目");
		long contractId = insertEffectiveMainContract(projectId, "确认再校验合同");

		assertError(createOrder(admin,
				orderPayloadWithProject(fixture.customerId(), "只传项目", projectId, null,
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null)))),
				HttpStatus.BAD_REQUEST, "SALES_ORDER_PROJECT_PAIR_REQUIRED");

		long orderId = createOrderId(admin,
				orderPayloadWithProject(fixture.customerId(), "确认前合同失效", projectId, contractId,
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))));
		ensureQualifiedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "1.000000");
		this.jdbcTemplate.update("""
				update sal_project_contract
				set status = 'CLOSED', closed_by = 'test', closed_at = now(), close_reason = '并发关闭',
					updated_at = now(), version = version + 1
				where id = ?
				""", contractId);

		assertError(confirmOrder(admin, orderId), HttpStatus.CONFLICT, "SALES_ORDER_CONTRACT_INVALID");
		assertThat(orderStatus(orderId)).isEqualTo("DRAFT");
		assertThat(reservationCount(orderId)).isZero();
		assertThat(auditCount("SALES_ORDER_CONFIRM", "SALES_ORDER", orderId)).isZero();
	}

	@Test
	void concurrentDraftOrderProjectAssociationAllowsOnlyOneVersionedUpdateWithoutPartialWrites() throws Exception {
		AuthenticatedSession firstAdmin = login("admin", ADMIN_PASSWORD);
		AuthenticatedSession secondAdmin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(firstAdmin);
		long firstProjectId = insertActiveProject(fixture.customerId(), "并发关联项目一");
		long firstContractId = insertEffectiveMainContract(firstProjectId, "并发关联合同一");
		long secondProjectId = insertActiveProject(fixture.customerId(), "并发关联项目二");
		long secondContractId = insertEffectiveMainContract(secondProjectId, "并发关联合同二");
		long orderId = createOrderId(firstAdmin,
				orderPayload(fixture.customerId(), "并发关联草稿订单",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))));
		long version = data(getOrder(firstAdmin, orderId)).get("version").longValue();

		Map<String, Object> firstUpdate = orderPayloadWithProject(fixture.customerId(), "并发关联草稿订单一",
				firstProjectId, firstContractId,
				List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000", "1.000000", null)));
		firstUpdate.put("version", version);
		Map<String, Object> secondUpdate = orderPayloadWithProject(fixture.customerId(), "并发关联草稿订单二",
				secondProjectId, secondContractId,
				List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000", "1.000000", null)));
		secondUpdate.put("version", version);

		ExecutorService executorService = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<ResponseEntity<String>> first = executorService
				.submit(() -> updateOrderAfterStart(firstAdmin, orderId, firstUpdate, start));
			Future<ResponseEntity<String>> second = executorService
				.submit(() -> updateOrderAfterStart(secondAdmin, orderId, secondUpdate, start));
			start.countDown();

			ResponseEntity<String> firstResponse = first.get(20, TimeUnit.SECONDS);
			ResponseEntity<String> secondResponse = second.get(20, TimeUnit.SECONDS);
			List<String> codes = List.of(code(firstResponse), code(secondResponse));
			assertThat(codes).contains("OK");
			assertThat(codes).contains("SALES_ORDER_CONCURRENT_MODIFICATION");
		}
		finally {
			executorService.shutdownNow();
		}

		JsonNode latest = data(getOrder(firstAdmin, orderId));
		assertThat(latest.get("projectId").longValue()).isIn(firstProjectId, secondProjectId);
		assertThat(latest.get("contractId").longValue()).isIn(firstContractId, secondContractId);
		assertThat(orderLineCount(orderId)).isOne();
	}

	@Test
	void authenticationAuthorizationAndCsrfAreEnforced() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		long orderId = createAndConfirmOrder(admin, fixture, "权限订单", "2.000000");
		long orderLineId = firstLine(data(getOrder(admin, orderId))).get("id").longValue();
		long shipmentId = createShipmentId(admin, orderId,
				shipmentPayload(fixture.warehouseId(), "权限出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null))));

		assertUnauthorized(get("/api/admin/sales/orders", null));
		assertUnauthorized(get("/api/admin/sales/orders/" + orderId, null));
		assertUnauthorized(get("/api/admin/sales/shipments", null));
		assertUnauthorized(get("/api/admin/sales/shipments/" + shipmentId, null));

		AuthenticatedSession reader = createSalesUserAndLogin("sales-reader-", "SALES_READER_",
				List.of("sales:order:view", "sales:shipment:view"));
		assertOk(get("/api/admin/sales/orders", reader));
		assertOk(get("/api/admin/sales/orders/" + orderId, reader));
		assertOk(get("/api/admin/sales/shipments", reader));
		assertOk(get("/api/admin/sales/shipments/" + shipmentId, reader));
		assertForbidden(createOrder(reader,
				orderPayload(fixture.customerId(), "只读创建",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null)))));
		assertForbidden(confirmOrder(reader, orderId));
		assertForbidden(createShipment(reader, orderId,
				shipmentPayload(fixture.warehouseId(), "只读出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null)))));
		assertForbidden(postShipment(reader, shipmentId));

		AuthenticatedSession salesUser = createSalesUserAndLogin("sales-order-", "SALES_ORDER_",
				List.of("sales:order:view", "sales:order:create", "sales:order:update", "sales:order:confirm",
						"sales:order:cancel", "sales:order:close", "sales:shipment:view"));
		long salesUserOrderId = createOrderId(salesUser,
				orderPayload(fixture.customerId(), "销售员创建",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))));
		ensureQualifiedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "1.000000");
		assertOk(confirmOrder(salesUser, salesUserOrderId));
		assertForbidden(createShipment(salesUser, orderId,
				shipmentPayload(fixture.warehouseId(), "销售员不可出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null)))));

		AuthenticatedSession warehouseUser = createSalesUserAndLogin("sales-warehouse-", "SALES_WH_",
				List.of("sales:order:view", "sales:shipment:view", "sales:shipment:create",
						"sales:shipment:update", "sales:shipment:post"));
		assertForbidden(confirmOrder(warehouseUser, orderId));
		long warehouseShipmentId = createShipmentId(warehouseUser, orderId,
				shipmentPayload(fixture.warehouseId(), "仓库角色出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null))));
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "3.000000");
		assertOk(postShipment(warehouseUser, warehouseShipmentId));

		AuthenticatedSession noSales = createSalesUserAndLogin("sales-none-", "SALES_NONE_", List.of());
		assertForbidden(get("/api/admin/sales/orders", noSales));
		assertForbidden(get("/api/admin/sales/shipments", noSales));

		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/sales/orders",
				orderPayload(fixture.customerId(), "无 CSRF 创建",
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
								"1.000000", null))),
				admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.PUT, "/api/admin/sales/orders/" + orderId + "/confirm",
				actionBody(data(getOrder(admin, orderId)), "无 CSRF 确认", "sales-no-csrf-confirm-" + orderId),
				admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/sales/orders/" + orderId + "/shipments",
				shipmentPayload(fixture.warehouseId(), "无 CSRF 出库",
						List.of(shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
								"1.000000", null))),
				admin));
		assertForbidden(exchangeWithoutCsrf(HttpMethod.PUT, "/api/admin/sales/shipments/" + shipmentId + "/post",
				actionBody(data(getShipment(admin, shipmentId)), "无 CSRF 过账",
						"sales-no-csrf-shipment-post-" + shipmentId),
				admin));
	}

	@Test
	void lockedPeriodRejectsSalesOrderConfirmationCancellationAndClosing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixtureWithCredit(admin);
		LocalDate date = LocalDate.of(2092, 7, 10);
		Map<String, Object> confirmPayload = new LinkedHashMap<>(orderPayload(fixture.customerId(), "期间锁定确认测试",
				List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1", "1", null))));
		confirmPayload.put("orderDate", date.toString());
		long confirmId = createOrderId(admin, confirmPayload);
		Map<String, Object> cancelPayload = new LinkedHashMap<>(orderPayload(fixture.customerId(), "期间锁定取消测试",
				List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1", "1", null))));
		cancelPayload.put("orderDate", date.toString());
		long cancelId = createOrderId(admin, cancelPayload);
		Map<String, Object> closePayload = new LinkedHashMap<>(orderPayload(fixture.customerId(), "期间锁定关闭测试",
				List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1", "1", null))));
		closePayload.put("orderDate", date.toString());
		long closeId = createOrderId(admin, closePayload);
		ensureQualifiedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "1.000000");
		assertOk(confirmOrder(admin, closeId));
		closeFirstDeliveryPlan(admin, closeId);
		lockPeriod(date);
		assertError(confirmOrder(admin, confirmId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertError(cancelOrder(admin, cancelId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertError(closeOrder(admin, closeId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at) values (?, ?, ?, ?, 'LOCKED', now(), now())",
				"LOCK-" + date, "锁定期间", date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
	}

	private long createAndConfirmOrder(AuthenticatedSession session, SalesFixture fixture, String remark,
			String quantity) throws Exception {
		ensureQualifiedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), quantity);
		long orderId = createOrderId(session,
				orderPayload(fixture.customerId(), remark,
						List.of(orderLine(1, fixture.finishedMaterialId(), fixture.unitId(), quantity, "1.000000",
								null))));
		assertOk(confirmOrder(session, orderId));
		return orderId;
	}

	private long createConfirmedOrderLine(AuthenticatedSession session, SalesFixture fixture, String remark,
			String quantity) throws Exception {
		long orderId = createAndConfirmOrder(session, fixture, remark, quantity);
		return firstLine(data(getOrder(session, orderId))).get("id").longValue();
	}

	private long createOrderId(AuthenticatedSession session, Map<String, Object> payload) throws Exception {
		ResponseEntity<String> response = createOrder(session, payload);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createShipmentId(AuthenticatedSession session, long orderId, Map<String, Object> payload)
			throws Exception {
		ResponseEntity<String> response = createShipment(session, orderId, payload);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private Map<String, Object> orderPayload(long customerId, String remark, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("orderDate", LocalDate.now().toString());
		payload.put("expectedShipDate", LocalDate.now().plusDays(3).toString());
		payload.put("remark", remark);
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> orderPayloadWithProject(long customerId, String remark, Long projectId,
			Long contractId, List<Map<String, Object>> lines) {
		Map<String, Object> payload = orderPayload(customerId, remark, lines);
		if (projectId != null) {
			payload.put("projectId", projectId);
		}
		if (contractId != null) {
			payload.put("contractId", contractId);
		}
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
		line.put("taxRate", "0.000000");
		line.put("taxExcludedUnitPrice", unitPrice);
		line.put("taxIncludedUnitPrice", unitPrice);
		line.put("taxExcludedAmount", amount(quantity, unitPrice));
		line.put("taxAmount", "0.00");
		line.put("taxIncludedAmount", amount(quantity, unitPrice));
		line.put("priceSourceType", "MANUAL");
		if (this.defaultReservationWarehouseId != null) {
			line.put("reservationWarehouseId", this.defaultReservationWarehouseId);
		}
		line.put("expectedShipDate", LocalDate.now().plusDays(3).toString());
		if (remark != null) {
			line.put("remark", remark);
		}
		return line;
	}

	private Map<String, Object> orderLineWithoutReservationWarehouse(int lineNo, long materialId, Long unitId,
			String quantity, String unitPrice, String remark) {
		Map<String, Object> line = orderLine(lineNo, materialId, unitId, quantity, unitPrice, remark);
		line.remove("reservationWarehouseId");
		return line;
	}

	private Map<String, Object> shipmentPayload(long warehouseId, String remark, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", warehouseId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("remark", remark);
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> shipmentLine(int lineNo, long orderLineId, long materialId, long unitId,
			String quantity, String remark) {
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

	private SalesFixture fixtureWithCredit(AuthenticatedSession admin) throws Exception {
		SalesFixture fixture = fixture();
		ensureCreditProfile(admin, fixture.customerId());
		return fixture;
	}

	private SalesFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("SAL_UNIT_" + suffix, "销售单位" + suffix);
		long otherUnitId = insertUnit("SAL_OTHER_UNIT_" + suffix, "销售其他单位" + suffix);
		long warehouseId = insertWarehouse("SAL_WH_" + suffix, "销售仓" + suffix, "ENABLED");
		long disabledWarehouseId = insertWarehouse("SAL_OFF_WH_" + suffix, "停用销售仓" + suffix, "DISABLED");
		this.defaultReservationWarehouseId = warehouseId;
		long customerId = insertCustomer("SAL_CUS_" + suffix, "销售客户" + suffix, "ENABLED");
		long disabledCustomerId = insertCustomer("SAL_OFF_CUS_" + suffix, "停用客户" + suffix, "DISABLED");
		long categoryId = insertMaterialCategory("SAL_CAT_" + suffix);
		long finishedMaterialId = insertMaterial("SAL_FG_" + suffix, "销售成品" + suffix, "FINISHED_GOOD",
				"SELF_MADE", categoryId, unitId, "ENABLED");
		long semiFinishedMaterialId = insertMaterial("SAL_SF_" + suffix, "销售半成品" + suffix, "SEMI_FINISHED",
				"SELF_MADE", categoryId, unitId, "ENABLED");
		long rawMaterialId = insertMaterial("SAL_RAW_" + suffix, "销售原材料" + suffix, "RAW_MATERIAL", "PURCHASED",
				categoryId, unitId, "ENABLED");
		long auxiliaryMaterialId = insertMaterial("SAL_AUX_" + suffix, "销售辅料" + suffix, "AUXILIARY",
				"PURCHASED", categoryId, unitId, "ENABLED");
		long disabledMaterialId = insertMaterial("SAL_OFF_MAT_" + suffix, "停用销售物料" + suffix, "FINISHED_GOOD",
				"SELF_MADE", categoryId, unitId, "DISABLED");
		return new SalesFixture(unitId, otherUnitId, warehouseId, disabledWarehouseId, customerId, disabledCustomerId,
				categoryId, finishedMaterialId, semiFinishedMaterialId, rawMaterialId, auxiliaryMaterialId,
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

	private void insertPurchaseInTransit(long materialId, long unitId, String quantity) {
		int suffix = SEQUENCE.incrementAndGet();
		long supplierId = this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, "SAL_PO_SUP_" + suffix, "销售测试供应商" + suffix);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "SAL_PO_" + suffix, supplierId, LocalDate.now(), LocalDate.now().plusDays(5),
				"销售确认在途参考");
		this.jdbcTemplate.update("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, expected_arrival_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, 0, 1, 0, 1, 1, ?, ?, ?, ?, now(), now())
				""", orderId, materialId, unitId, new BigDecimal(quantity), new BigDecimal(quantity),
				new BigDecimal(quantity), LocalDate.now().plusDays(5), "销售确认在途参考");
	}

	private long insertCustomer(String code, String name, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
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
				""", Long.class, code, "销售分类" + code);
	}

	private long insertMaterial(String code, String name, String materialType, String sourceType, long categoryId,
			long unitId, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, "销售规格", materialType, sourceType, categoryId, unitId, status);
	}

	private void seedStock(long warehouseId, long materialId, long unitId, String quantity) {
		seedStock(warehouseId, materialId, unitId, quantity, InventoryQualityStatus.QUALIFIED);
	}

	private void ensureQualifiedStock(long warehouseId, long materialId, long unitId, String quantity) {
		BigDecimal target = new BigDecimal(quantity);
		BigDecimal current = balanceQuantity(warehouseId, materialId, InventoryQualityStatus.QUALIFIED);
		BigDecimal activeLocked = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity - released_quantity - consumed_quantity), 0)
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and status = 'ACTIVE'
				""", BigDecimal.class, warehouseId, materialId);
		BigDecimal requiredOnHand = target.add(activeLocked);
		if (current.compareTo(requiredOnHand) < 0) {
			seedStock(warehouseId, materialId, unitId, requiredOnHand.toPlainString());
		}
	}

	private void setQualifiedStock(long warehouseId, long materialId, long unitId, String quantity) {
		int updated = this.jdbcTemplate.update("""
				update inv_stock_balance
				set unit_id = ?, quantity_on_hand = ?, updated_at = now(), version = version + 1
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id is null
				and serial_id is null
				and ownership_type = 'PUBLIC'
				and project_id is null
				and cost_layer_id is null
				""", unitId, new BigDecimal(quantity), warehouseId, materialId);
		if (updated == 0) {
			this.jdbcTemplate.update("""
					insert into inv_stock_balance (warehouse_id, material_id, unit_id, quality_status, quantity_on_hand,
						locked_quantity, created_at, updated_at)
					values (?, ?, ?, 'QUALIFIED', ?, 0, now(), now())
					""", warehouseId, materialId, unitId, new BigDecimal(quantity));
		}
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
				and ownership_type = 'PUBLIC'
				and project_id is null
				and cost_layer_id is null
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

	private void disableCustomer(long customerId) {
		this.jdbcTemplate.update("update mst_customer set status = 'DISABLED', updated_at = now() where id = ?",
				customerId);
	}

	private void disableMaterial(long materialId) {
		this.jdbcTemplate.update("update mst_material set status = 'DISABLED', updated_at = now() where id = ?",
				materialId);
	}

	private void disableUnit(long unitId) {
		this.jdbcTemplate.update("update mst_unit set status = 'DISABLED', updated_at = now() where id = ?", unitId);
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
				""", Long.class, materialId, batchNo, 7_100_000L + SEQUENCE.incrementAndGet(),
				7_110_000L + SEQUENCE.incrementAndGet(), LocalDate.now());
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, quantity_on_hand, locked_quantity,
					batch_id, created_at, updated_at
				)
				values (?, ?, ?, 'QUALIFIED', ?, 0, ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity), batchId);
		return new TrackedBatch(batchId, batchNo);
	}

	private Map<String, Object> trackingAllocation(long batchId, String quantity) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("batchId", batchId);
		allocation.put("quantity", quantity);
		return allocation;
	}

	private Map<String, Object> serialAllocation(long serialId) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("serialId", serialId);
		allocation.put("quantity", "1.000000");
		return allocation;
	}

	private long createSerialShipment(AuthenticatedSession session, SalesFixture fixture, long orderId,
			long orderLineId, long serialId, String remark) throws Exception {
		Map<String, Object> line = shipmentLine(1, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
				"1.000000", remark);
		line.put("trackingAllocations", List.of(serialAllocation(serialId)));
		return createShipmentId(session, orderId, shipmentPayload(fixture.warehouseId(), remark, List.of(line)));
	}

	private ResponseEntity<String> postShipmentAfterStart(long shipmentId, AuthenticatedSession session,
			CountDownLatch start) throws Exception {
		start.await(10, TimeUnit.SECONDS);
		return postShipment(session, shipmentId);
	}

	private BigDecimal trackingBalanceQuantity(long warehouseId, long materialId, long batchId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id = ?
				""", BigDecimal.class, warehouseId, materialId, batchId);
	}

	private TrackedSerial seedSerialStock(long warehouseId, long materialId, long unitId, String serialNo) {
		long serialId = this.jdbcTemplate.queryForObject("""
				insert into inv_serial (
					material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status,
					stock_status, business_date, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'TEST', ?, ?, ?, 'QUALIFIED', 'IN_STOCK', ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, materialId, serialNo, 7_120_000L + SEQUENCE.incrementAndGet(),
				7_130_000L + SEQUENCE.incrementAndGet(), warehouseId, LocalDate.now());
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, quantity_on_hand, locked_quantity,
					serial_id, created_at, updated_at
				)
				values (?, ?, ?, 'QUALIFIED', 1.000000, 0, ?, now(), now())
				""", warehouseId, materialId, unitId, serialId);
		return new TrackedSerial(serialId, serialNo);
	}

	private BigDecimal serialBalanceQuantity(long warehouseId, long materialId, long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and serial_id = ?
				""", BigDecimal.class, warehouseId, materialId, serialId);
	}

	private String serialStockStatus(long serialId) {
		return this.jdbcTemplate.queryForObject("select stock_status from inv_serial where id = ?", String.class,
				serialId);
	}

	private long serialMovementCount(String sourceType, long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and serial_id = ?
				""", Long.class, sourceType, serialId);
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

	private BigDecimal orderLineShippedQuantity(long orderLineId) {
		return this.jdbcTemplate.queryForObject("""
				select shipped_quantity
				from sal_sales_order_line
				where id = ?
				""", BigDecimal.class, orderLineId);
	}

	private String shipmentStatus(long shipmentId) {
		return this.jdbcTemplate.queryForObject("""
				select status
				from sal_sales_shipment
				where id = ?
				""", String.class, shipmentId);
	}

	private long shipmentLineId(long shipmentId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from sal_sales_shipment_line
				where shipment_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, shipmentId);
	}

	private String materialCode(long materialId) {
		return this.jdbcTemplate.queryForObject("select code from mst_material where id = ?", String.class,
				materialId);
	}

	private String materialName(long materialId) {
		return this.jdbcTemplate.queryForObject("select name from mst_material where id = ?", String.class,
				materialId);
	}

	private String customerCode(long customerId) {
		return this.jdbcTemplate.queryForObject("select code from mst_customer where id = ?", String.class,
				customerId);
	}

	private long insertActiveProject(long customerId, String name) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date, status,
					target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at, activated_by,
					activated_at
				)
				values (?, ?, ?, (select id from sys_user where username = 'admin'), ?, ?, 'ACTIVE',
					0, 0, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "SPC-SO-P-" + suffix, name, customerId, LocalDate.now(),
				LocalDate.now().plusDays(30), name);
	}

	private long insertEffectiveMainContract(long projectId, String name) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (
					contract_no, external_contract_no, project_id, contract_type, main_contract_id, name, signed_date,
					effective_start_date, effective_end_date, amount, status, remark, created_by, created_at,
					updated_by, updated_at, activated_by, activated_at
				)
				values (?, ?, ?, 'MAIN', null, ?, ?, ?, ?, 1000.00, 'EFFECTIVE', ?, 'test', now(),
					'test', now(), 'test', now())
				returning id
				""", Long.class, "SPC-SO-C-" + suffix, "SPC-SO-EXT-" + suffix, projectId, name,
				LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(30), name);
	}

	private String orderStatus(long orderId) {
		return this.jdbcTemplate.queryForObject("select status from sal_sales_order where id = ?", String.class,
				orderId);
	}

	private long reservationCount(long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_reservation
				where source_type = 'SALES_ORDER'
				and source_id = ?
				""", Long.class, orderId);
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

	private int orderLineCount(long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order_line
				where order_id = ?
				""", Integer.class, orderId);
	}

	private ResponseEntity<String> updateOrderAfterStart(AuthenticatedSession session, long orderId,
			Map<String, Object> body, CountDownLatch start) throws Exception {
		start.await(10, TimeUnit.SECONDS);
		return updateOrder(session, orderId, body);
	}

	private void assertPostShipmentRejectedWithoutSideEffects(AuthenticatedSession admin, long shipmentId,
			long shipmentLineId, long orderLineId, long warehouseId, long materialId, HttpStatus status,
			String expectedCode) throws Exception {
		BigDecimal balanceBefore = balanceQuantity(warehouseId, materialId);
		long movementCountBefore = movementCountBySource("SALES_SHIPMENT", shipmentLineId);
		BigDecimal shippedBefore = orderLineShippedQuantity(orderLineId);
		String statusBefore = shipmentStatus(shipmentId);
		assertError(postShipment(admin, shipmentId), status, expectedCode);
		assertDecimal(balanceQuantity(warehouseId, materialId), balanceBefore.toPlainString());
		assertThat(movementCountBySource("SALES_SHIPMENT", shipmentLineId)).isEqualTo(movementCountBefore);
		assertDecimal(orderLineShippedQuantity(orderLineId), shippedBefore.toPlainString());
		assertThat(shipmentStatus(shipmentId)).isEqualTo(statusBefore);
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

	private void assertProjectLinkAudit(String action, long targetId, String orderNo, String oldLink, String newLink) {
		AuditRow audit = latestAudit(action, "SALES_PROJECT", targetId);
		assertThat(audit.operatorUsername()).isEqualTo("admin");
		assertThat(audit.action()).isEqualTo(action);
		assertThat(audit.targetType()).isEqualTo("SALES_PROJECT");
		assertThat(audit.targetId()).isEqualTo(String.valueOf(targetId));
		assertThat(audit.targetSummary()).contains("订单 " + orderNo);
		assertThat(audit.targetSummary()).contains(oldLink + " -> " + newLink);
		assertThat(audit.createdAt()).isNotNull();
	}

	private AuditRow latestAudit(String action, String targetType, long targetId) {
		return this.jdbcTemplate.queryForObject("""
				select operator_username, action, target_type, target_id, target_summary, created_at
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				order by id desc
				limit 1
				""", (rs, rowNum) -> new AuditRow(rs.getString("operator_username"), rs.getString("action"),
				rs.getString("target_type"), rs.getString("target_id"), rs.getString("target_summary"),
				rs.getObject("created_at", OffsetDateTime.class)), action, targetType, String.valueOf(targetId));
	}

	private AuthenticatedSession createSalesUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "销售测试角色" + suffix, "销售测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "销售测试用户" + suffix);
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
		return exchange(HttpMethod.POST, "/api/admin/sales/orders", body, session);
	}

	private ResponseEntity<String> updateOrder(AuthenticatedSession session, long orderId, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/sales/orders/" + orderId, body, session);
	}

	private ResponseEntity<String> getOrder(AuthenticatedSession session, long orderId) {
		return get("/api/admin/sales/orders/" + orderId, session);
	}

	private ResponseEntity<String> confirmOrder(AuthenticatedSession session, long orderId) throws Exception {
		JsonNode order = data(getOrder(session, orderId));
		return exchange(HttpMethod.PUT, "/api/admin/sales/orders/" + orderId + "/confirm",
				actionBody(order, "销售订单确认", "sales-order-confirm-" + orderId), session);
	}

	private ResponseEntity<String> cancelOrder(AuthenticatedSession session, long orderId) throws Exception {
		JsonNode order = data(getOrder(session, orderId));
		return exchange(HttpMethod.PUT, "/api/admin/sales/orders/" + orderId + "/cancel",
				actionBody(order, "销售订单取消", "sales-order-cancel-" + orderId), session);
	}

	private ResponseEntity<String> closeOrder(AuthenticatedSession session, long orderId) throws Exception {
		JsonNode order = data(getOrder(session, orderId));
		return exchange(HttpMethod.PUT, "/api/admin/sales/orders/" + orderId + "/close",
				actionBody(order, "销售订单关闭", "sales-order-close-" + orderId), session);
	}

	private void closeFirstDeliveryPlan(AuthenticatedSession session, long orderId) throws Exception {
		JsonNode plan = data(get("/api/admin/sales/orders/" + orderId + "/delivery-plans", session))
			.get("items")
			.get(0);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", plan.get("version").longValue());
		body.put("reason", "期间锁定关闭测试先关闭交付计划");
		body.put("idempotencyKey", "sales-delivery-plan-close-" + orderId + "-" + plan.get("id").longValue());
		assertOk(exchange(HttpMethod.PUT,
				"/api/admin/sales/orders/" + orderId + "/delivery-plans/" + plan.get("id").longValue() + "/close",
				body, session));
	}

	private ResponseEntity<String> createShipment(AuthenticatedSession session, long orderId,
			Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/sales/orders/" + orderId + "/shipments",
				withDeliveryPlanIds(orderId, body), session);
	}

	private ResponseEntity<String> getShipment(AuthenticatedSession session, long shipmentId) {
		return get("/api/admin/sales/shipments/" + shipmentId, session);
	}

	private ResponseEntity<String> updateShipment(AuthenticatedSession session, long shipmentId,
			Map<String, Object> body) {
		Long orderId = this.jdbcTemplate.queryForObject("""
				select order_id
				from sal_sales_shipment
				where id = ?
				""", Long.class, shipmentId);
		return exchange(HttpMethod.PUT, "/api/admin/sales/shipments/" + shipmentId,
				withDeliveryPlanIds(orderId, body), session);
	}

	private ResponseEntity<String> postShipment(AuthenticatedSession session, long shipmentId) throws Exception {
		JsonNode shipment = data(getShipment(session, shipmentId));
		return exchange(HttpMethod.PUT, "/api/admin/sales/shipments/" + shipmentId + "/post",
				actionBody(shipment, "销售出库过账", "sales-shipment-post-" + shipmentId), session);
	}

	private void ensureCreditProfile(AuthenticatedSession admin, long customerId) throws Exception {
		ResponseEntity<String> existing = get("/api/admin/sales/credit-profiles/" + customerId, admin);
		if (existing.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
			return;
		}
		assertThat(existing.getStatusCode()).as(existing.getBody()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(code(existing)).as(existing.getBody()).isEqualTo("SALES_CREDIT_BLOCKED");
		assertOk(exchange(HttpMethod.POST, "/api/admin/sales/credit-profiles",
				creditProfilePayload(customerId), admin));
	}

	private Map<String, Object> creditProfilePayload(long customerId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("creditLimit", "1000000.00");
		payload.put("frozen", false);
		payload.put("blockOverdue", false);
		payload.put("reviewDate", LocalDate.now().toString());
		payload.put("remark", "025 销售旧测试信用档案");
		return payload;
	}

	private Map<String, Object> actionBody(JsonNode object, String reason, String keyPrefix) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", object.get("version").longValue());
		body.put("reason", reason);
		body.put("idempotencyKey", keyPrefix + "-" + object.get("version").longValue());
		return body;
	}

	private String amount(String quantity, String unitPrice) {
		return new BigDecimal(quantity).multiply(new BigDecimal(unitPrice))
			.setScale(2, java.math.RoundingMode.HALF_UP)
			.toPlainString();
	}

	private Map<String, Object> withDeliveryPlanIds(Long orderId, Map<String, Object> body) {
		if (body == null || orderId == null || !(body.get("lines") instanceof List<?> lines)) {
			return body;
		}
		for (Object item : lines) {
			if (item instanceof Map<?, ?> rawLine) {
				@SuppressWarnings("unchecked")
				Map<String, Object> line = (Map<String, Object>) rawLine;
				if (line.get("deliveryPlanId") == null && line.get("orderLineId") != null) {
					deliveryPlanId(orderId, ((Number) line.get("orderLineId")).longValue())
						.ifPresent((planId) -> line.put("deliveryPlanId", planId));
				}
			}
		}
		return body;
	}

	private java.util.Optional<Long> deliveryPlanId(Long orderId, Long orderLineId) {
		return this.jdbcTemplate.query("""
				select id
				from sal_sales_delivery_plan
				where order_id = ?
				  and order_line_id = ?
				order by line_no asc, id asc
				limit 1
				""", (rs, rowNum) -> rs.getLong("id"), orderId, orderLineId).stream().findFirst();
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

	private ResponseEntity<String> exchangeJson(HttpMethod method, String path, String body,
			AuthenticatedSession session) {
		return this.restTemplate.exchange(path, method, rawJsonEntity(body, session.sessionCookie(),
				session.csrfSession()), String.class);
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

	private HttpEntity<String> rawJsonEntity(String body, String cookie, CsrfSession csrf) {
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

	private JsonNode firstLine(JsonNode detail) {
		JsonNode lines = detail.get("lines");
		assertThat(lines.size()).isGreaterThan(0);
		return lines.get(0);
	}

	private JsonNode firstItem(ResponseEntity<String> response) throws Exception {
		JsonNode items = data(response).get("items");
		assertThat(items.size()).isGreaterThan(0);
		return items.get(0);
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

	private void assertErrorIn(ResponseEntity<String> response, HttpStatus status, List<String> codes)
			throws Exception {
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

	private void assertSalesQualifiedCandidateUnavailable(JsonNode line) {
		assertThat(line.get("qualityStatus").asText()).isEqualTo(InventoryQualityStatus.QUALIFIED.name());
		assertThat(line.get("qualityStatusName").asText()).isEqualTo("合格");
		assertDecimal(line, "quantityOnHand", "0.000000");
		assertDecimal(line, "availableQuantity", "0.000000");
		assertThat(line.get("selectable").booleanValue()).isFalse();
		assertThat(line.get("disabledReasonCode").asText()).isEqualTo("QUALIFIED_BALANCE_NOT_ENOUGH");
		assertThat(line.get("disabledReason").asText()).isEqualTo("合格可用库存不足");
		assertDecimal(line, "maxSelectableQuantity", "0.000000");
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

	private record AuditRow(String operatorUsername, String action, String targetType, String targetId,
			String targetSummary, OffsetDateTime createdAt) {
	}

	private record SalesFixture(long unitId, long otherUnitId, long warehouseId, long disabledWarehouseId,
			long customerId, long disabledCustomerId, long categoryId, long finishedMaterialId,
			long semiFinishedMaterialId, long rawMaterialId, long auxiliaryMaterialId, long disabledMaterialId) {
	}

	private record MovementRow(String movementType, String direction, long sourceId, long sourceLineId,
			BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity) {
	}

	private record TrackedBatch(long batchId, String batchNo) {
	}

	private record TrackedSerial(long serialId, String serialNo) {
	}

}
