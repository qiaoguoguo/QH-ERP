package com.qherp.api.system.reversal;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=reversal-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReversalAdminControllerTests extends PostgresIntegrationTest {

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
	void salesReturnLifecyclePostsInventoryReceivableAdjustmentAndTraceLinks() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "5.000000", "10.000000",
				"3.000000", "20.000000", "CONFIRMED", "110.00", "0.00", "110.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "7.000000");
		seedStock(fixture.warehouseId(), fixture.secondMaterialId(), fixture.unitId(), "2.000000");

		ResponseEntity<String> sources = get("/api/admin/sales/return-sources?keyword=" + shipment.shipmentNo(),
				admin);
		assertOk(sources);
		JsonNode source = data(sources).get("items").get(0);
		assertThat(source.get("shipmentId").longValue()).isEqualTo(shipment.shipmentId());
		assertThat(source.get("status").asText()).isEqualTo("POSTED");
		assertDecimalText(source.get("lines").get(0), "shippedQuantity", "5.000000");
		assertDecimalText(source.get("lines").get(0), "returnedQuantity", "0");
		assertDecimalText(source.get("lines").get(0), "returnableQuantity", "5.000000");
		assertDecimalText(source.get("lines").get(0), "returnableAmount", "50.00");

		ResponseEntity<String> created = post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), "sales-return-lifecycle-" + SEQUENCE.incrementAndGet(),
						List.of(returnLine(shipment.firstShipmentLineId(), "2.000000", "客户退回"))),
				admin);
		assertOk(created);
		JsonNode draft = data(created);
		long returnId = draft.get("id").longValue();
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("source").get("sourceId").longValue()).isEqualTo(shipment.shipmentId());
		assertDecimalText(draft, "totalQuantity", "2.000000");
		assertDecimalText(draft, "totalAmount", "20.00");

		ResponseEntity<String> updated = put("/api/admin/sales/returns/" + returnId,
				salesReturnPayload(shipment.shipmentId(), null,
						List.of(returnLine(shipment.firstShipmentLineId(), "3.000000", "客户退回调整"))),
				admin);
		assertOk(updated);
		JsonNode updatedLine = data(updated).get("lines").get(0);
		assertDecimalText(updatedLine, "quantity", "3.000000");
		assertDecimalText(updatedLine, "amount", "30.00");

		ResponseEntity<String> cancelDraft = post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), "sales-return-cancel-" + SEQUENCE.incrementAndGet(),
						List.of(returnLine(shipment.secondShipmentLineId(), "1.000000", "取消草稿"))),
				admin);
		assertOk(cancelDraft);
		long cancelledId = data(cancelDraft).get("id").longValue();
		assertOk(put("/api/admin/sales/returns/" + cancelledId + "/cancel", Map.of(), admin));
		assertThat(data(get("/api/admin/sales/returns/" + cancelledId, admin)).get("status").asText())
			.isEqualTo("CANCELLED");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.secondMaterialId()), "2.000000");

		ResponseEntity<String> posted = put("/api/admin/sales/returns/" + returnId + "/post", Map.of(), admin);
		assertOk(posted);
		JsonNode postedData = data(posted);
		JsonNode postedLine = postedData.get("lines").get(0);
		long returnLineId = postedLine.get("id").longValue();
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertDecimalText(postedData, "totalAmount", "30.00");
		assertDecimalText(postedLine, "returnedQuantityBefore", "0");
		assertDecimalText(postedLine, "returnableQuantityBefore", "5.000000");
		assertThat(postedLine.get("stockMovementId").isNumber()).isTrue();

		MovementRow movement = movementForSource("SALES_RETURN", returnLineId);
		assertThat(movement.movementType()).isEqualTo("SALES_RETURN_IN");
		assertThat(movement.direction()).isEqualTo("IN");
		assertThat(movement.sourceId()).isEqualTo(returnId);
		assertDecimal(movement.quantity(), "3.000000");
		assertDecimal(movement.beforeQuantity(), "0.000000");
		assertDecimal(movement.afterQuantity(), "3.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), "10.000000");
		assertThat(movementQualityStatus("SALES_RETURN", returnLineId))
			.isEqualTo(InventoryQualityStatus.PENDING_INSPECTION.name());
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "3.000000");
		assertThat(qualityInspectionCount("SALES_RETURN", returnId, returnLineId, "PENDING")).isOne();

		ReceivableAmounts receivable = receivableAmounts(shipment.receivableId());
		assertDecimal(receivable.adjustedAmount(), "30.00");
		assertDecimal(receivable.unreceivedAmount(), "80.00");
		assertThat(receivable.status()).isEqualTo("PARTIALLY_RECEIVED");

		ReversalLink link = reversalLink("SALES_RETURN", returnId, returnLineId);
		assertThat(link.sourceType()).isEqualTo("SALES_SHIPMENT");
		assertThat(link.sourceId()).isEqualTo(shipment.shipmentId());
		assertThat(link.sourceLineId()).isEqualTo(shipment.firstShipmentLineId());
		assertDecimal(link.quantity(), "3.000000");
		assertDecimal(link.amount(), "30.00");

		JsonNode detail = data(get("/api/admin/sales/returns/" + returnId, admin));
		assertThat(detail.get("traces").size()).isOne();
		assertThat(detail.get("traces").get(0).get("traceKey").asText()).contains("SALES_RETURN");
		JsonNode listItem = data(get("/api/admin/sales/returns?keyword=" + postedData.get("returnNo").asText(),
				admin)).get("items").get(0);
		assertThat(listItem.get("id").longValue()).isEqualTo(returnId);
		assertDecimalText(listItem, "totalQuantity", "3.000000");
	}

	@Test
	void salesReturnSupportsPartialAndFullReturnAndBlocksInvalidOperations() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "5.000000", "10.000000",
				"3.000000", "20.000000", "CONFIRMED", "110.00", "0.00", "110.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "7.000000");
		seedStock(fixture.warehouseId(), fixture.secondMaterialId(), fixture.unitId(), "2.000000");

		long firstReturnId = createSalesReturn(admin, shipment.shipmentId(), shipment.firstShipmentLineId(),
				"2.000000");
		assertOk(put("/api/admin/sales/returns/" + firstReturnId + "/post", Map.of(), admin));
		JsonNode sourceAfterPartial = data(get("/api/admin/sales/return-sources?keyword=" + shipment.shipmentNo(),
				admin)).get("items").get(0);
		assertDecimalText(sourceAfterPartial.get("lines").get(0), "returnedQuantity", "2.000000");
		assertDecimalText(sourceAfterPartial.get("lines").get(0), "returnableQuantity", "3.000000");

		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), null,
						List.of(returnLine(shipment.firstShipmentLineId(), "3.000001", "超退"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_QUANTITY_EXCEEDS_AVAILABLE");
		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), null,
						List.of(returnLine(shipment.firstShipmentLineId(), "0.000000", "非法数量"))),
				admin), HttpStatus.BAD_REQUEST, "REVERSAL_QUANTITY_INVALID");

		long fullReturnId = createSalesReturn(admin, shipment.shipmentId(), shipment.firstShipmentLineId(),
				"3.000000");
		ResponseEntity<String> fullPosted = put("/api/admin/sales/returns/" + fullReturnId + "/post", Map.of(),
				admin);
		assertOk(fullPosted);
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), "12.000000");

		long secondLineReturnId = createSalesReturn(admin, shipment.shipmentId(), shipment.secondShipmentLineId(),
				"3.000000");
		assertOk(put("/api/admin/sales/returns/" + secondLineReturnId + "/post", Map.of(), admin));
		ReceivableAmounts receivable = receivableAmounts(shipment.receivableId());
		assertDecimal(receivable.adjustedAmount(), "110.00");
		assertDecimal(receivable.unreceivedAmount(), "0.00");
		assertThat(receivable.status()).isEqualTo("RECEIVED");
		assertThat(data(get("/api/admin/sales/return-sources?keyword=" + shipment.shipmentNo(), admin))
			.get("items")
			.size()).isZero();

		assertError(put("/api/admin/sales/returns/" + fullReturnId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");
		assertError(put("/api/admin/sales/returns/" + fullReturnId,
				salesReturnPayload(shipment.shipmentId(), null,
						List.of(returnLine(shipment.firstShipmentLineId(), "1.000000", "已过账不可改"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");
		assertError(put("/api/admin/sales/returns/" + fullReturnId + "/cancel", Map.of(), admin),
				HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");

		PostedSalesShipment draftShipment = createShipmentWithoutReceivable(fixture, "DRAFT");
		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(draftShipment.shipmentId(), null,
						List.of(returnLine(draftShipment.firstShipmentLineId(), "1.000000", "未过账来源"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_SOURCE_STATUS_INVALID");
		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(999999999L, null,
						List.of(returnLine(shipment.firstShipmentLineId(), "1.000000", "来源不存在"))),
				admin), HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
	}

	@Test
	void lockedPeriodRejectsSalesReturnPosting() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "2.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "40.00", "0.00", "40.00");
		LocalDate date = LocalDate.of(2096, 7, 10);
		Map<String, Object> payload = salesReturnPayload(shipment.shipmentId(),
				"sales-return-locked-" + SEQUENCE.incrementAndGet(),
				List.of(returnLine(shipment.firstShipmentLineId(), "1.000000", "期间锁定退货")));
		payload.put("businessDate", date.toString());
		ResponseEntity<String> created = post("/api/admin/sales/returns", payload, admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();
		lockPeriod(date);
		assertError(put("/api/admin/sales/returns/" + returnId + "/post", Map.of(), admin), HttpStatus.CONFLICT,
				"BUSINESS_PERIOD_LOCKED");
	}

	@Test
	void salesReturnClientRequestIdIdempotencyRequiresMatchingCoreLines() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "5.000000", "10.000000",
				"3.000000", "20.000000", "CONFIRMED", "110.00", "0.00", "110.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "1.000000");
		seedStock(fixture.warehouseId(), fixture.secondMaterialId(), fixture.unitId(), "1.000000");

		String clientRequestId = "sales-return-idempotent-" + SEQUENCE.incrementAndGet();
		Map<String, Object> originalPayload = salesReturnPayload(shipment.shipmentId(), clientRequestId,
				List.of(returnLine(shipment.firstShipmentLineId(), "1.000000", "幂等退货")));
		ResponseEntity<String> created = post("/api/admin/sales/returns", originalPayload, admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();

		Map<String, Object> sameCorePayload = salesReturnPayload(shipment.shipmentId(), clientRequestId,
				List.of(returnLine(shipment.firstShipmentLineId(), "1.000000", "幂等退货")));
		sameCorePayload.put("businessDate", LocalDate.now().minusDays(1).toString());
		sameCorePayload.put("remark", "备注变化不影响幂等核心字段");
		ResponseEntity<String> sameCoreRetry = post("/api/admin/sales/returns", sameCorePayload, admin);
		assertOk(sameCoreRetry);
		assertThat(data(sameCoreRetry).get("id").longValue()).isEqualTo(returnId);
		assertThat(data(sameCoreRetry).get("status").asText()).isEqualTo("DRAFT");

		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), clientRequestId,
						List.of(returnLine(shipment.firstShipmentLineId(), "2.000000", "数量不一致"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_DUPLICATED");
		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), clientRequestId,
						List.of(returnLine(shipment.secondShipmentLineId(), "1.000000", "来源行不一致"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_DUPLICATED");

		assertOk(put("/api/admin/sales/returns/" + returnId + "/post", Map.of(), admin));
		ResponseEntity<String> postedRetry = post("/api/admin/sales/returns", originalPayload, admin);
		assertOk(postedRetry);
		assertThat(data(postedRetry).get("id").longValue()).isEqualTo(returnId);
		assertThat(data(postedRetry).get("status").asText()).isEqualTo("POSTED");

		String cancelledClientRequestId = "sales-return-cancelled-idempotent-" + SEQUENCE.incrementAndGet();
		Map<String, Object> cancelledPayload = salesReturnPayload(shipment.shipmentId(), cancelledClientRequestId,
				List.of(returnLine(shipment.secondShipmentLineId(), "1.000000", "取消后重试")));
		ResponseEntity<String> cancelledDraft = post("/api/admin/sales/returns", cancelledPayload, admin);
		assertOk(cancelledDraft);
		long cancelledId = data(cancelledDraft).get("id").longValue();
		assertOk(put("/api/admin/sales/returns/" + cancelledId + "/cancel", Map.of(), admin));
		assertError(post("/api/admin/sales/returns", cancelledPayload, admin), HttpStatus.CONFLICT,
				"REVERSAL_DUPLICATED");
	}

	@Test
	void salesReturnMasksSourceFieldsWhenSourcePermissionMissing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "2.000000", "15.000000",
				"1.000000", "20.000000", "CONFIRMED", "50.00", "0.00", "50.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "1.000000");
		long returnId = createSalesReturn(admin, shipment.shipmentId(), shipment.firstShipmentLineId(), "1.000000");
		assertOk(put("/api/admin/sales/returns/" + returnId + "/post", Map.of(), admin));

		AuthenticatedSession restricted = createUserAndLoginWithPermissions("sales-return-restricted",
				List.of("sales:return:view", "business:reversal:view"));
		JsonNode detail = data(get("/api/admin/sales/returns/" + returnId, restricted));
		assertRestrictedSource(detail.get("source"), "SALES_SHIPMENT");
		assertRestrictedDocumentLine(detail.get("lines").get(0));
		assertRestrictedSource(detail.get("lines").get(0).get("source"), "SALES_SHIPMENT_LINE");
		assertRestrictedSource(detail.get("traces").get(0).get("source"), "SALES_SHIPMENT_LINE");
		assertRestrictedTraceRecord(detail.get("traces").get(0));
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(detail.get("traces").get(0), "SALES_SHIPMENT",
				shipment.shipmentId(), shipment.firstShipmentLineId());
		assertThat(detail.get("traces").get(0).get("reverse").get("sourceId").longValue()).isEqualTo(returnId);

		JsonNode listItem = data(get("/api/admin/sales/returns", restricted)).get("items").get(0);
		assertRestrictedSource(listItem.get("source"), "SALES_SHIPMENT");

		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=" + returnId,
				restricted)).get(0);
		assertRestrictedSource(trace.get("source"), "SALES_SHIPMENT_LINE");
		assertRestrictedTraceRecord(trace);
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(trace, "SALES_SHIPMENT", shipment.shipmentId(),
				shipment.firstShipmentLineId());
		assertThat(trace.get("restricted").booleanValue()).isTrue();

		JsonNode reverseDirectionTrace = data(get(
				"/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=" + returnId
						+ "&direction=REVERSE_TO_SOURCE",
				admin)).get(0);
		assertThat(reverseDirectionTrace.get("direction").asText()).isEqualTo("REVERSE_TO_SOURCE");
	}

	@Test
	void salesReturnDraftUpdateAllowsRestrictedSourceLineIdsAndRejectsMismatchedSourceLine() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "5.000000", "10.000000",
				"3.000000", "20.000000", "CONFIRMED", "110.00", "0.00", "110.00");
		long returnId = createSalesReturn(admin, shipment.shipmentId(), shipment.firstShipmentLineId(), "1.000000");
		long returnLineId = data(get("/api/admin/sales/returns/" + returnId, admin))
			.get("lines")
			.get(0)
			.get("id")
			.longValue();

		AuthenticatedSession restricted = createUserAndLoginWithPermissions("sales-return-update-restricted",
				List.of("sales:return:view", "sales:return:update"));
		ResponseEntity<String> updated = put("/api/admin/sales/returns/" + returnId,
				salesReturnUpdatePayload(List.of(returnLineById(returnLineId, "2.000000", "受限来源编辑保存"))),
				restricted);
		assertOk(updated);
		JsonNode updatedData = data(updated);
		assertRestrictedSource(updatedData.get("source"), "SALES_SHIPMENT");
		assertRestrictedDocumentLine(updatedData.get("lines").get(0));
		assertRestrictedSource(updatedData.get("lines").get(0).get("source"), "SALES_SHIPMENT_LINE");
		assertDecimalText(updatedData.get("lines").get(0), "quantity", "2.000000");
		assertDecimalText(updatedData.get("lines").get(0), "amount", "20.00");

		assertError(put("/api/admin/sales/returns/" + returnId,
				salesReturnUpdatePayload(List.of(returnLineByIdAndSourceLine(returnLineId,
						shipment.secondShipmentLineId(), "1.000000", "行ID与来源行不匹配"))),
				restricted), HttpStatus.CONFLICT, "REVERSAL_SOURCE_STATUS_INVALID");
	}

	@Test
	void salesReturnPaginationHandlesExtremePageWithoutNegativeOffset() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		ResponseEntity<String> response = get("/api/admin/sales/returns?page=2147483647&pageSize=100", admin);
		assertOk(response);
		JsonNode data = data(response);
		assertThat(data.get("items").isArray()).isTrue();
		assertThat(data.get("items").size()).isZero();
		assertThat(data.get("page").intValue()).isEqualTo(Integer.MAX_VALUE);
		assertThat(data.get("pageSize").intValue()).isEqualTo(100);
	}

	@Test
	void procurementReturnLifecyclePostsInventoryPayableAdjustmentAndTraceLinks() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "5.000000", "10.000000",
				"3.000000", "20.000000", "CONFIRMED", "110.00", "0.00", "110.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "7.000000");
		seedStock(fixture.warehouseId(), fixture.secondMaterialId(), fixture.unitId(), "2.000000");

		ResponseEntity<String> sources = get("/api/admin/procurement/return-sources?keyword=" + receipt.receiptNo(),
				admin);
		assertOk(sources);
		JsonNode source = data(sources).get("items").get(0);
		assertThat(source.get("receiptId").longValue()).isEqualTo(receipt.receiptId());
		assertThat(source.get("status").asText()).isEqualTo("POSTED");
		assertDecimalText(source.get("lines").get(0), "receivedQuantity", "5.000000");
		assertDecimalText(source.get("lines").get(0), "returnedQuantity", "0");
		assertDecimalText(source.get("lines").get(0), "returnableQuantity", "5.000000");
		assertDecimalText(source.get("lines").get(0), "availableStockQuantity", "7.000000");
		assertDecimalText(source.get("lines").get(0), "returnableAmount", "50.00");

		ResponseEntity<String> created = post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), "purchase-return-lifecycle-" + SEQUENCE.incrementAndGet(),
						List.of(purchaseReturnLine(receipt.firstReceiptLineId(), "2.000000", "供应商退货"))),
				admin);
		assertOk(created);
		JsonNode draft = data(created);
		long returnId = draft.get("id").longValue();
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("source").get("sourceId").longValue()).isEqualTo(receipt.receiptId());
		assertDecimalText(draft, "totalQuantity", "2.000000");
		assertDecimalText(draft, "totalAmount", "20.00");

		ResponseEntity<String> updated = put("/api/admin/procurement/returns/" + returnId,
				withPurchaseReturnVersion(admin, returnId, purchaseReturnPayload(receipt.receiptId(), null,
						List.of(purchaseReturnLine(receipt.firstReceiptLineId(), "3.000000", "退货调整")))),
				admin);
		assertOk(updated);
		JsonNode updatedLine = data(updated).get("lines").get(0);
		assertDecimalText(updatedLine, "quantity", "3.000000");
		assertDecimalText(updatedLine, "amount", "30.00");

		ResponseEntity<String> cancelDraft = post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), "purchase-return-cancel-" + SEQUENCE.incrementAndGet(),
						List.of(purchaseReturnLine(receipt.secondReceiptLineId(), "1.000000", "取消草稿"))),
				admin);
		assertOk(cancelDraft);
		long cancelledId = data(cancelDraft).get("id").longValue();
		assertOk(cancelPurchaseReturn(admin, cancelledId));
		assertThat(data(get("/api/admin/procurement/returns/" + cancelledId, admin)).get("status").asText())
			.isEqualTo("CANCELLED");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.secondMaterialId()), "2.000000");

		ResponseEntity<String> posted = postPurchaseReturn(admin, returnId);
		assertOk(posted);
		JsonNode postedData = data(posted);
		JsonNode postedLine = postedData.get("lines").get(0);
		long returnLineId = postedLine.get("id").longValue();
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertDecimalText(postedData, "totalAmount", "30.00");
		assertDecimalText(postedLine, "returnedQuantityBefore", "0");
		assertDecimalText(postedLine, "returnableQuantityBefore", "5.000000");
		assertThat(postedLine.get("stockMovementId").isNumber()).isTrue();

		MovementRow movement = movementForSource("PURCHASE_RETURN", returnLineId);
		assertThat(movement.movementType()).isEqualTo("PURCHASE_RETURN_OUT");
		assertThat(movement.direction()).isEqualTo("OUT");
		assertThat(movement.sourceId()).isEqualTo(returnId);
		assertDecimal(movement.quantity(), "3.000000");
		assertDecimal(movement.beforeQuantity(), "7.000000");
		assertDecimal(movement.afterQuantity(), "4.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), "4.000000");

		PayableAmounts payable = payableAmounts(receipt.payableId());
		assertDecimal(payable.adjustedAmount(), "30.00");
		assertDecimal(payable.unpaidAmount(), "80.00");
		assertThat(payable.status()).isEqualTo("PARTIALLY_PAID");

		ReversalLink link = reversalLink("PURCHASE_RETURN", returnId, returnLineId);
		assertThat(link.sourceType()).isEqualTo("PURCHASE_RECEIPT");
		assertThat(link.sourceId()).isEqualTo(receipt.receiptId());
		assertThat(link.sourceLineId()).isEqualTo(receipt.firstReceiptLineId());
		assertDecimal(link.quantity(), "3.000000");
		assertDecimal(link.amount(), "30.00");

		JsonNode detail = data(get("/api/admin/procurement/returns/" + returnId, admin));
		assertThat(detail.get("traces").size()).isOne();
		assertThat(detail.get("traces").get(0).get("traceKey").asText()).contains("PURCHASE_RETURN");
		JsonNode listItem = data(get("/api/admin/procurement/returns?keyword=" + postedData.get("returnNo").asText(),
				admin)).get("items").get(0);
		assertThat(listItem.get("id").longValue()).isEqualTo(returnId);
		assertDecimalText(listItem, "totalQuantity", "3.000000");
	}

	@Test
	void purchaseReturnBatchAllocationPostsTrackingOutboundMovement() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "5.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "70.00", "0.00", "70.00");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-PUR-BATCH-" + SEQUENCE.incrementAndGet(), "5.000000");

		Map<String, Object> line = purchaseReturnLine(receipt.firstReceiptLineId(), "2.000000", "批次采购退货");
		line.put("trackingAllocations", List.of(batchAllocation(batch.batchId(), "2.000000")));
		ResponseEntity<String> created = post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), "purchase-return-batch-" + SEQUENCE.incrementAndGet(),
						List.of(line)),
				admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();

		ResponseEntity<String> posted = postPurchaseReturn(admin, returnId);
		assertOk(posted);
		JsonNode postedLine = data(posted).get("lines").get(0);
		long returnLineId = postedLine.get("id").longValue();

		assertThat(postedLine.get("trackingAllocations").get(0).get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), batch.batchId()),
				"3.000000");
		assertThat(trackingAllocationMovementCount("PURCHASE_RETURN", returnId, returnLineId)).isOne();
		assertThat(trackedMovementCount("PURCHASE_RETURN", returnLineId, batch.batchId())).isOne();
	}

	@Test
	void purchaseReturnSourceLinesAggregateTrackedBalancesAndPostMultipleBatches() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "5.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "70.00", "0.00", "70.00");
		TrackedBatch firstBatch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-PUR-AGG-A-" + SEQUENCE.incrementAndGet(), "2.000000");
		TrackedBatch secondBatch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-PUR-AGG-B-" + SEQUENCE.incrementAndGet(), "3.000000");

		JsonNode source = data(get("/api/admin/procurement/return-sources?keyword=" + receipt.receiptNo(), admin))
			.get("items")
			.get(0);
		assertThat(source.get("lines").size()).isEqualTo(2);
		JsonNode candidate = purchaseReturnCandidate(source, receipt.firstReceiptLineId(),
				InventoryQualityStatus.QUALIFIED.name());
		assertDecimalText(candidate, "quantityOnHand", "5.000000");
		assertDecimalText(candidate, "availableQuantity", "5.000000");

		Map<String, Object> insufficientLine = purchaseReturnLine(receipt.firstReceiptLineId(), "5.000000",
				"批次超可用采购退货");
		insufficientLine.put("trackingAllocations", List.of(batchAllocation(firstBatch.batchId(), "3.000000"),
				batchAllocation(secondBatch.batchId(), "2.000000")));
		assertError(post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), "purchase-return-over-batch-"
						+ SEQUENCE.incrementAndGet(), List.of(insufficientLine)),
				admin), HttpStatus.CONFLICT, "INVENTORY_TRACKING_STOCK_NOT_ENOUGH");

		Map<String, Object> line = purchaseReturnLine(receipt.firstReceiptLineId(), "5.000000", "多批采购退货");
		line.put("trackingAllocations", List.of(batchAllocation(firstBatch.batchId(), "2.000000"),
				batchAllocation(secondBatch.batchId(), "3.000000")));
		ResponseEntity<String> created = post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), "purchase-return-multi-batch-"
						+ SEQUENCE.incrementAndGet(), List.of(line)),
				admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();

		ResponseEntity<String> posted = postPurchaseReturn(admin, returnId);
		assertOk(posted);
		long returnLineId = data(posted).get("lines").get(0).get("id").longValue();
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), firstBatch.batchId()),
				"0.000000");
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), secondBatch.batchId()),
				"0.000000");
		assertThat(trackingAllocationMovementCount("PURCHASE_RETURN", returnId, returnLineId)).isEqualTo(2);
	}

	@Test
	void purchaseReturnBatchAllocationUsesRequestedNonFrozenQualityStatus() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "5.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "70.00", "0.00", "70.00");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-PUR-PENDING-BATCH-" + SEQUENCE.incrementAndGet(), "2.000000",
				InventoryQualityStatus.PENDING_INSPECTION);

		Map<String, Object> line = purchaseReturnLine(receipt.firstReceiptLineId(), "2.000000", "待检批次采购退货",
				InventoryQualityStatus.PENDING_INSPECTION);
		line.put("trackingAllocations", List.of(batchAllocation(batch.batchId(), "2.000000")));
		ResponseEntity<String> created = post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), "purchase-return-pending-batch-"
						+ SEQUENCE.incrementAndGet(), List.of(line)),
				admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();

		ResponseEntity<String> posted = postPurchaseReturn(admin, returnId);
		assertOk(posted);
		long returnLineId = data(posted).get("lines").get(0).get("id").longValue();
		assertThat(movementQualityStatus("PURCHASE_RETURN", returnLineId))
			.isEqualTo(InventoryQualityStatus.PENDING_INSPECTION.name());
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), batch.batchId(),
				InventoryQualityStatus.PENDING_INSPECTION), "0.000000");
		assertThat(trackingAllocationMovementCount("PURCHASE_RETURN", returnId, returnLineId)).isOne();
	}

	@Test
	void salesReturnInheritsSourceBatchAllocationAndRejectsTampering() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "5.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "70.00", "0.00", "70.00");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-SALES-RETURN-BATCH-" + SEQUENCE.incrementAndGet(), "5.000000");
		long sourceAllocationId = insertTrackedOutboundSource("SALES_SHIPMENT", shipment.shipmentId(),
				shipment.firstShipmentLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				batch.batchId(), "5.000000", "SALES_SHIPMENT");

		ResponseEntity<String> created = post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), "sales-return-tracking-" + SEQUENCE.incrementAndGet(),
						List.of(returnLine(shipment.firstShipmentLineId(), "2.000000", "批次销售退货",
								List.of(sourceInheritedBatchAllocation(sourceAllocationId, batch.batchId(),
										"2.000000"))))),
				admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();

		ResponseEntity<String> posted = put("/api/admin/sales/returns/" + returnId + "/post", Map.of(), admin);
		assertOk(posted);
		JsonNode postedLine = data(posted).get("lines").get(0);
		long returnLineId = postedLine.get("id").longValue();

		assertThat(postedLine.get("trackingAllocations").get(0).get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), batch.batchId(),
				InventoryQualityStatus.PENDING_INSPECTION), "2.000000");
		assertThat(trackingAllocationMovementCount("SALES_RETURN", returnId, returnLineId)).isOne();
		assertThat(trackedMovementCount("SALES_RETURN", returnLineId, batch.batchId())).isOne();

		TrackedBatch tamperedBatch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-SALES-TAMPER-BATCH-" + SEQUENCE.incrementAndGet(), "1.000000");
		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), null,
						List.of(returnLine(shipment.firstShipmentLineId(), "1.000000", "篡改批次",
								List.of(sourceInheritedBatchAllocation(sourceAllocationId, tamperedBatch.batchId(),
										"1.000000"))))),
				admin), HttpStatus.CONFLICT, "INVENTORY_TRACKING_SOURCE_MISMATCH");
	}

	@Test
	void salesReturnSourceIncludesRemainingTrackingAllocationsForSourceInheritance() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "5.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "70.00", "0.00", "70.00");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-SALES-SRC-BATCH-" + SEQUENCE.incrementAndGet(), "5.000000");
		long sourceAllocationId = insertTrackedOutboundSource("SALES_SHIPMENT", shipment.shipmentId(),
				shipment.firstShipmentLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				batch.batchId(), "5.000000", "SALES_SHIPMENT");

		JsonNode source = firstItem(get("/api/admin/sales/return-sources?keyword=" + shipment.shipmentNo(), admin));
		JsonNode sourceLine = source.get("lines").get(0);
		assertThat(sourceLine.has("trackingAllocations")).isTrue();
		JsonNode candidateAllocation = sourceLine.get("trackingAllocations").get(0);
		assertThat(candidateAllocation.get("sourceAllocationId").longValue()).isEqualTo(sourceAllocationId);
		assertThat(candidateAllocation.get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(candidateAllocation.get("quantity").decimalValue(), "5.000000");

		ResponseEntity<String> created = post("/api/admin/sales/returns",
				salesReturnPayload(shipment.shipmentId(), "sales-return-source-candidate-"
						+ SEQUENCE.incrementAndGet(), List.of(returnLine(shipment.firstShipmentLineId(), "2.000000",
								"批次销售退货", List.of(sourceInheritedBatchAllocation(
										candidateAllocation.get("sourceAllocationId").longValue(),
										candidateAllocation.get("batchId").longValue(), "2.000000"))))),
				admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();
		assertOk(put("/api/admin/sales/returns/" + returnId + "/post", Map.of(), admin));

		JsonNode sourceAfterPartial = firstItem(get(
				"/api/admin/sales/return-sources?keyword=" + shipment.shipmentNo(), admin));
		JsonNode remainingAllocation = sourceAfterPartial.get("lines").get(0).get("trackingAllocations").get(0);
		assertThat(remainingAllocation.get("sourceAllocationId").longValue()).isEqualTo(sourceAllocationId);
		assertDecimal(remainingAllocation.get("quantity").decimalValue(), "3.000000");
	}

	@Test
	void salesReturnRechecksSourceBatchRemainingAtPostWhenTwoDraftsUseSameSourceAllocation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "5.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "70.00", "0.00", "70.00");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-SALES-POST-RECHECK-" + SEQUENCE.incrementAndGet(), "1.000000");
		long sourceAllocationId = insertTrackedOutboundSource("SALES_SHIPMENT", shipment.shipmentId(),
				shipment.firstShipmentLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				batch.batchId(), "1.000000", "SALES_SHIPMENT");

		long firstReturnId = createSalesReturnWithTracking(admin, shipment.shipmentId(), shipment.firstShipmentLineId(),
				sourceAllocationId, batch.batchId(), "sales-return-recheck-a-" + SEQUENCE.incrementAndGet());
		long secondReturnId = createSalesReturnWithTracking(admin, shipment.shipmentId(), shipment.firstShipmentLineId(),
				sourceAllocationId, batch.batchId(), "sales-return-recheck-b-" + SEQUENCE.incrementAndGet());
		long secondReturnLineId = firstSalesReturnLineId(secondReturnId);

		assertOk(put("/api/admin/sales/returns/" + firstReturnId + "/post", Map.of(), admin));
		assertError(put("/api/admin/sales/returns/" + secondReturnId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "INVENTORY_TRACKING_SOURCE_MISMATCH");
		assertThat(movementCountBySource("SALES_RETURN", secondReturnLineId)).isZero();
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), batch.batchId(),
				InventoryQualityStatus.PENDING_INSPECTION), "1.000000");
	}

	@Test
	void salesReturnRechecksSourceSerialRemainingAtPostWhenTwoDraftsUseSameSourceAllocation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		setTrackingMethod(fixture.materialId(), "SERIAL");
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "2.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "40.00", "0.00", "40.00");
		TrackedSerial serial = seedSerialStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-SALES-RETURN-SERIAL-" + SEQUENCE.incrementAndGet());
		long sourceAllocationId = insertTrackedOutboundSerialSource("SALES_SHIPMENT", shipment.shipmentId(),
				shipment.firstShipmentLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				serial.serialId(), "SALES_SHIPMENT");

		long firstReturnId = createSalesReturnWithSerialTracking(admin, shipment.shipmentId(),
				shipment.firstShipmentLineId(), sourceAllocationId, serial.serialId(),
				"sales-return-serial-recheck-a-" + SEQUENCE.incrementAndGet());
		long secondReturnId = createSalesReturnWithSerialTracking(admin, shipment.shipmentId(),
				shipment.firstShipmentLineId(), sourceAllocationId, serial.serialId(),
				"sales-return-serial-recheck-b-" + SEQUENCE.incrementAndGet());
		long secondReturnLineId = firstSalesReturnLineId(secondReturnId);

		assertOk(put("/api/admin/sales/returns/" + firstReturnId + "/post", Map.of(), admin));
		assertError(put("/api/admin/sales/returns/" + secondReturnId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "INVENTORY_TRACKING_SOURCE_MISMATCH");
		assertThat(movementCountBySource("SALES_RETURN", secondReturnLineId)).isZero();
		assertDecimal(serialTrackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), serial.serialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "1.000000");
	}

	@Test
	void productionMaterialReturnInheritsSourceBatchAllocation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "5.000000", "10.000000");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-MAT-RETURN-BATCH-" + SEQUENCE.incrementAndGet(), "5.000000");
		long sourceAllocationId = insertTrackedOutboundSource("PRODUCTION_MATERIAL_ISSUE", issue.issueId(),
				issue.issueLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(), batch.batchId(),
				"5.000000", "PRODUCTION_ISSUE");

		ResponseEntity<String> created = post("/api/admin/production/material-returns",
				materialReturnPayload(issue.issueId(), "material-return-tracking-" + SEQUENCE.incrementAndGet(),
						List.of(materialReturnLine(issue.issueLineId(), "2.000000", "批次生产退料",
								List.of(sourceInheritedBatchAllocation(sourceAllocationId, batch.batchId(),
										"2.000000"))))),
				admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();

		ResponseEntity<String> posted = put("/api/admin/production/material-returns/" + returnId + "/post",
				Map.of(), admin);
		assertOk(posted);
		JsonNode postedLine = data(posted).get("lines").get(0);
		long returnLineId = postedLine.get("id").longValue();

		assertThat(postedLine.get("trackingAllocations").get(0).get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), batch.batchId(),
				InventoryQualityStatus.PENDING_INSPECTION), "2.000000");
		assertThat(trackingAllocationMovementCount("PRODUCTION_MATERIAL_RETURN", returnId, returnLineId)).isOne();
		assertThat(trackedMovementCount("PRODUCTION_MATERIAL_RETURN", returnLineId, batch.batchId())).isOne();
	}

	@Test
	void productionMaterialReturnSourceIncludesRemainingTrackingAllocationsForSourceInheritance()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "5.000000", "10.000000");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-MAT-SRC-BATCH-" + SEQUENCE.incrementAndGet(), "5.000000");
		long sourceAllocationId = insertTrackedOutboundSource("PRODUCTION_MATERIAL_ISSUE", issue.issueId(),
				issue.issueLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(), batch.batchId(),
				"5.000000", "PRODUCTION_ISSUE");

		JsonNode source = firstItem(get("/api/admin/production/material-return-sources?keyword=" + issue.issueNo(),
				admin));
		JsonNode sourceLine = source.get("lines").get(0);
		assertThat(sourceLine.has("trackingAllocations")).isTrue();
		JsonNode candidateAllocation = sourceLine.get("trackingAllocations").get(0);
		assertThat(candidateAllocation.get("sourceAllocationId").longValue()).isEqualTo(sourceAllocationId);
		assertThat(candidateAllocation.get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(candidateAllocation.get("quantity").decimalValue(), "5.000000");

		ResponseEntity<String> created = post("/api/admin/production/material-returns",
				materialReturnPayload(issue.issueId(), "material-return-source-candidate-"
						+ SEQUENCE.incrementAndGet(), List.of(materialReturnLine(issue.issueLineId(), "2.000000",
								"批次生产退料", List.of(sourceInheritedBatchAllocation(
										candidateAllocation.get("sourceAllocationId").longValue(),
										candidateAllocation.get("batchId").longValue(), "2.000000"))))),
				admin);
		assertOk(created);
		long returnId = data(created).get("id").longValue();
		assertOk(put("/api/admin/production/material-returns/" + returnId + "/post", Map.of(), admin));

		JsonNode sourceAfterPartial = firstItem(get(
				"/api/admin/production/material-return-sources?keyword=" + issue.issueNo(), admin));
		JsonNode remainingAllocation = sourceAfterPartial.get("lines").get(0).get("trackingAllocations").get(0);
		assertThat(remainingAllocation.get("sourceAllocationId").longValue()).isEqualTo(sourceAllocationId);
		assertDecimal(remainingAllocation.get("quantity").decimalValue(), "3.000000");
	}

	@Test
	void productionMaterialReturnRechecksSourceBatchRemainingAtPostWhenTwoDraftsUseSameSourceAllocation()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "5.000000", "10.000000");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-MAT-POST-RECHECK-" + SEQUENCE.incrementAndGet(), "1.000000");
		long sourceAllocationId = insertTrackedOutboundSource("PRODUCTION_MATERIAL_ISSUE", issue.issueId(),
				issue.issueLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(), batch.batchId(),
				"1.000000", "PRODUCTION_ISSUE");

		long firstReturnId = createMaterialReturnWithTracking(admin, issue.issueId(), issue.issueLineId(),
				sourceAllocationId, batch.batchId(), "material-return-recheck-a-" + SEQUENCE.incrementAndGet());
		long secondReturnId = createMaterialReturnWithTracking(admin, issue.issueId(), issue.issueLineId(),
				sourceAllocationId, batch.batchId(), "material-return-recheck-b-" + SEQUENCE.incrementAndGet());
		long secondReturnLineId = firstMaterialReturnLineId(secondReturnId);

		assertOk(put("/api/admin/production/material-returns/" + firstReturnId + "/post", Map.of(), admin));
		assertError(put("/api/admin/production/material-returns/" + secondReturnId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "INVENTORY_TRACKING_SOURCE_MISMATCH");
		assertThat(movementCountBySource("PRODUCTION_MATERIAL_RETURN", secondReturnLineId)).isZero();
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), batch.batchId(),
				InventoryQualityStatus.PENDING_INSPECTION), "1.000000");
	}

	@Test
	void productionMaterialReturnRechecksSourceSerialRemainingAtPostWhenTwoDraftsUseSameSourceAllocation()
			throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		setTrackingMethod(fixture.materialId(), "SERIAL");
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "2.000000", "10.000000");
		TrackedSerial serial = seedSerialStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-MAT-RETURN-SERIAL-" + SEQUENCE.incrementAndGet());
		long sourceAllocationId = insertTrackedOutboundSerialSource("PRODUCTION_MATERIAL_ISSUE", issue.issueId(),
				issue.issueLineId(), fixture.warehouseId(), fixture.materialId(), fixture.unitId(), serial.serialId(),
				"PRODUCTION_ISSUE");

		long firstReturnId = createMaterialReturnWithSerialTracking(admin, issue.issueId(), issue.issueLineId(),
				sourceAllocationId, serial.serialId(), "material-return-serial-recheck-a-" + SEQUENCE.incrementAndGet());
		long secondReturnId = createMaterialReturnWithSerialTracking(admin, issue.issueId(), issue.issueLineId(),
				sourceAllocationId, serial.serialId(), "material-return-serial-recheck-b-" + SEQUENCE.incrementAndGet());
		long secondReturnLineId = firstMaterialReturnLineId(secondReturnId);

		assertOk(put("/api/admin/production/material-returns/" + firstReturnId + "/post", Map.of(), admin));
		assertError(put("/api/admin/production/material-returns/" + secondReturnId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "INVENTORY_TRACKING_SOURCE_MISMATCH");
		assertThat(movementCountBySource("PRODUCTION_MATERIAL_RETURN", secondReturnLineId)).isZero();
		assertDecimal(serialTrackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), serial.serialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "1.000000");
	}

	@Test
	void procurementReturnSupportsPartialFullStockAndInvalidOperationRules() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "5.000000", "10.000000",
				"3.000000", "20.000000", "CONFIRMED", "110.00", "0.00", "110.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "5.000000");
		seedStock(fixture.warehouseId(), fixture.secondMaterialId(), fixture.unitId(), "3.000000");

		long firstReturnId = createPurchaseReturn(admin, receipt.receiptId(), receipt.firstReceiptLineId(),
				"2.000000");
		assertOk(postPurchaseReturn(admin, firstReturnId));
		JsonNode sourceAfterPartial = data(get("/api/admin/procurement/return-sources?keyword=" + receipt.receiptNo(),
				admin)).get("items").get(0);
		assertDecimalText(sourceAfterPartial.get("lines").get(0), "returnedQuantity", "2.000000");
		assertDecimalText(sourceAfterPartial.get("lines").get(0), "returnableQuantity", "3.000000");
		assertDecimalText(sourceAfterPartial.get("lines").get(0), "availableStockQuantity", "3.000000");

		assertError(post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), null,
						List.of(purchaseReturnLine(receipt.firstReceiptLineId(), "3.000001", "超退"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_QUANTITY_EXCEEDS_AVAILABLE");
		assertError(post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), null,
						List.of(purchaseReturnLine(receipt.firstReceiptLineId(), "0.000000", "非法数量"))),
				admin), HttpStatus.BAD_REQUEST, "REVERSAL_QUANTITY_INVALID");
		long stockBlockedId = createPurchaseReturn(admin, receipt.receiptId(), receipt.firstReceiptLineId(),
				"3.000000");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "1.000000");
		assertError(postPurchaseReturn(admin, stockBlockedId),
				HttpStatus.CONFLICT, "REVERSAL_STOCK_INSUFFICIENT");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "3.000000");
		assertOk(postPurchaseReturn(admin, stockBlockedId));

		long secondLineReturnId = createPurchaseReturn(admin, receipt.receiptId(), receipt.secondReceiptLineId(),
				"3.000000");
		assertOk(postPurchaseReturn(admin, secondLineReturnId));
		PayableAmounts payable = payableAmounts(receipt.payableId());
		assertDecimal(payable.adjustedAmount(), "110.00");
		assertDecimal(payable.unpaidAmount(), "0.00");
		assertThat(payable.status()).isEqualTo("PAID");
		assertThat(data(get("/api/admin/procurement/return-sources?keyword=" + receipt.receiptNo(), admin))
			.get("items")
			.size()).isZero();

		assertError(postPurchaseReturn(admin, stockBlockedId),
				HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");
		assertError(put("/api/admin/procurement/returns/" + stockBlockedId,
				withPurchaseReturnVersion(admin, stockBlockedId, purchaseReturnPayload(receipt.receiptId(), null,
						List.of(purchaseReturnLine(receipt.firstReceiptLineId(), "1.000000", "已过账不可改")))),
				admin), HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");
		assertError(cancelPurchaseReturn(admin, stockBlockedId),
				HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");

		PostedPurchaseReceipt draftReceipt = createReceiptWithoutPayable(fixture, "DRAFT");
		assertError(post("/api/admin/procurement/returns",
				purchaseReturnPayload(draftReceipt.receiptId(), null,
						List.of(purchaseReturnLine(draftReceipt.firstReceiptLineId(), "1.000000", "未过账来源"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_SOURCE_STATUS_INVALID");
		assertError(post("/api/admin/procurement/returns",
				purchaseReturnPayload(999999999L, null,
						List.of(purchaseReturnLine(receipt.firstReceiptLineId(), "1.000000", "来源不存在"))),
				admin), HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
	}

	@Test
	void procurementReturnSourcesExposeQualityStatusCandidates() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "12.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "140.00", "0.00", "140.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "5.000000",
				InventoryQualityStatus.QUALIFIED);
		insertReservation(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "RESERVATION",
				"SALES_ORDER", "4.000000");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "2.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "3.000000",
				InventoryQualityStatus.REJECTED);
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "4.000000",
				InventoryQualityStatus.FROZEN);

		ResponseEntity<String> response = get("/api/admin/procurement/return-sources?keyword=" + receipt.receiptNo(),
				admin);

		assertOk(response);
		JsonNode source = data(response).get("items").get(0);
		JsonNode qualified = purchaseReturnCandidate(source, receipt.firstReceiptLineId(), "QUALIFIED");
		assertThat(qualified.get("qualityStatusName").asText()).isEqualTo("合格");
		assertDecimalText(qualified, "quantityOnHand", "5.000000");
		assertDecimalText(qualified, "availableStockQuantity", "1.000000");
		assertDecimalText(qualified, "availableQuantity", "1.000000");
		assertThat(qualified.get("selectable").booleanValue()).isTrue();
		assertThat(qualified.get("disabledReasonCode").isNull()).isTrue();
		assertThat(qualified.get("disabledReason").isNull()).isTrue();
		assertDecimalText(qualified, "maxSelectableQuantity", "1.000000");

		JsonNode pending = purchaseReturnCandidate(source, receipt.firstReceiptLineId(), "PENDING_INSPECTION");
		assertThat(pending.get("qualityStatusName").asText()).isEqualTo("待检");
		assertDecimalText(pending, "quantityOnHand", "2.000000");
		assertDecimalText(pending, "availableStockQuantity", "2.000000");
		assertDecimalText(pending, "availableQuantity", "0.000000");
		assertThat(pending.get("selectable").booleanValue()).isTrue();
		assertDecimalText(pending, "maxSelectableQuantity", "2.000000");

		JsonNode rejected = purchaseReturnCandidate(source, receipt.firstReceiptLineId(), "REJECTED");
		assertThat(rejected.get("qualityStatusName").asText()).isEqualTo("不合格");
		assertDecimalText(rejected, "quantityOnHand", "3.000000");
		assertDecimalText(rejected, "availableStockQuantity", "3.000000");
		assertDecimalText(rejected, "availableQuantity", "0.000000");
		assertThat(rejected.get("selectable").booleanValue()).isTrue();
		assertDecimalText(rejected, "maxSelectableQuantity", "3.000000");

		JsonNode frozen = purchaseReturnCandidate(source, receipt.firstReceiptLineId(), "FROZEN");
		assertThat(frozen.get("qualityStatusName").asText()).isEqualTo("冻结");
		assertDecimalText(frozen, "quantityOnHand", "4.000000");
		assertDecimalText(frozen, "availableStockQuantity", "4.000000");
		assertDecimalText(frozen, "availableQuantity", "0.000000");
		assertThat(frozen.get("selectable").booleanValue()).isFalse();
		assertThat(frozen.get("disabledReasonCode").asText()).isEqualTo("QUALITY_STATUS_FROZEN_NOT_RETURNABLE");
		assertThat(frozen.get("disabledReason").asText()).isEqualTo("冻结库存不可采购退货");
		assertDecimalText(frozen, "maxSelectableQuantity", "0.000000");
	}

	@Test
	void procurementReturnPostsExplicitPendingAndRejectedQualityStatusAndRejectsFrozenBypass() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "5.000000", "10.000000",
				"1.000000", "20.000000", "CONFIRMED", "70.00", "0.00", "70.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "2.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "3.000000",
				InventoryQualityStatus.REJECTED);
		seedStock(fixture.warehouseId(), fixture.secondMaterialId(), fixture.unitId(), "1.000000",
				InventoryQualityStatus.FROZEN);

		long pendingReturnId = createPurchaseReturn(admin, receipt.receiptId(), receipt.firstReceiptLineId(),
				"2.000000", InventoryQualityStatus.PENDING_INSPECTION);
		assertOk(postPurchaseReturn(admin, pendingReturnId));
		long pendingReturnLineId = firstPurchaseReturnLineId(pendingReturnId);
		assertThat(movementQualityStatus("PURCHASE_RETURN", pendingReturnLineId))
			.isEqualTo(InventoryQualityStatus.PENDING_INSPECTION.name());
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "0.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(),
				InventoryQualityStatus.REJECTED), "3.000000");

		long rejectedReturnId = createPurchaseReturn(admin, receipt.receiptId(), receipt.firstReceiptLineId(),
				"3.000000", InventoryQualityStatus.REJECTED);
		assertOk(postPurchaseReturn(admin, rejectedReturnId));
		long rejectedReturnLineId = firstPurchaseReturnLineId(rejectedReturnId);
		assertThat(movementQualityStatus("PURCHASE_RETURN", rejectedReturnLineId))
			.isEqualTo(InventoryQualityStatus.REJECTED.name());
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(),
				InventoryQualityStatus.REJECTED), "0.000000");

		long returnCountBeforeFrozenBypass = purchaseReturnCount(receipt.receiptId());
		assertError(post("/api/admin/procurement/returns",
				purchaseReturnPayload(receipt.receiptId(), "purchase-return-frozen-" + SEQUENCE.incrementAndGet(),
						List.of(purchaseReturnLine(receipt.secondReceiptLineId(), "1.000000", "冻结绕过",
								InventoryQualityStatus.FROZEN))),
				admin), HttpStatus.CONFLICT, "QUALITY_STATUS_TRANSITION_INVALID");
		assertThat(purchaseReturnCount(receipt.receiptId())).isEqualTo(returnCountBeforeFrozenBypass);
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.secondMaterialId(),
				InventoryQualityStatus.FROZEN), "1.000000");
	}

	@Test
	void procurementReturnMasksSourceFieldsWhenSourcePermissionMissing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "2.000000", "15.000000",
				"1.000000", "20.000000", "CONFIRMED", "50.00", "0.00", "50.00");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "2.000000");
		long returnId = createPurchaseReturn(admin, receipt.receiptId(), receipt.firstReceiptLineId(), "1.000000");
		assertOk(postPurchaseReturn(admin, returnId));

		AuthenticatedSession restricted = createUserAndLoginWithPermissions("purchase-return-restricted",
				List.of("procurement:return:view", "business:reversal:view"));
		JsonNode detail = data(get("/api/admin/procurement/returns/" + returnId, restricted));
		assertRestrictedSource(detail.get("source"), "PURCHASE_RECEIPT");
		assertRestrictedDocumentLine(detail.get("lines").get(0));
		assertRestrictedSource(detail.get("lines").get(0).get("source"), "PURCHASE_RECEIPT_LINE");
		assertRestrictedSource(detail.get("traces").get(0).get("source"), "PURCHASE_RECEIPT_LINE");
		assertRestrictedTraceRecord(detail.get("traces").get(0));
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(detail.get("traces").get(0), "PURCHASE_RECEIPT",
				receipt.receiptId(), receipt.firstReceiptLineId());
		assertThat(detail.get("traces").get(0).get("reverse").get("sourceId").longValue()).isEqualTo(returnId);

		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=PURCHASE_RETURN&sourceId=" + returnId,
				restricted)).get(0);
		assertRestrictedSource(trace.get("source"), "PURCHASE_RECEIPT_LINE");
		assertRestrictedTraceRecord(trace);
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(trace, "PURCHASE_RECEIPT", receipt.receiptId(),
				receipt.firstReceiptLineId());
	}

	@Test
	void procurementReturnDraftUpdateAllowsRestrictedSourceLineIdsAndRejectsForeignLineId() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "5.000000", "10.000000",
				"3.000000", "20.000000", "CONFIRMED", "110.00", "0.00", "110.00");
		long returnId = createPurchaseReturn(admin, receipt.receiptId(), receipt.firstReceiptLineId(), "1.000000");
		long returnLineId = data(get("/api/admin/procurement/returns/" + returnId, admin))
			.get("lines")
			.get(0)
			.get("id")
			.longValue();
		long otherReturnId = createPurchaseReturn(admin, receipt.receiptId(), receipt.secondReceiptLineId(),
				"1.000000");
		long otherReturnLineId = data(get("/api/admin/procurement/returns/" + otherReturnId, admin))
			.get("lines")
			.get(0)
			.get("id")
			.longValue();

		AuthenticatedSession restricted = createUserAndLoginWithPermissions("purchase-return-update-restricted",
				List.of("procurement:return:view", "procurement:return:update"));
		ResponseEntity<String> updated = put("/api/admin/procurement/returns/" + returnId,
				withPurchaseReturnVersion(restricted, returnId, purchaseReturnUpdatePayload(
						List.of(purchaseReturnLineById(returnLineId, "2.000000", "受限来源编辑保存")))),
				restricted);
		assertOk(updated);
		JsonNode updatedData = data(updated);
		assertRestrictedSource(updatedData.get("source"), "PURCHASE_RECEIPT");
		assertRestrictedDocumentLine(updatedData.get("lines").get(0));
		assertRestrictedSource(updatedData.get("lines").get(0).get("source"), "PURCHASE_RECEIPT_LINE");
		assertDecimalText(updatedData.get("lines").get(0), "quantity", "2.000000");
		assertThat(!updatedData.get("lines").get(0).has("amount")
				|| updatedData.get("lines").get(0).get("amount").isNull()).isTrue();

		assertError(put("/api/admin/procurement/returns/" + returnId,
				withPurchaseReturnVersion(restricted, returnId, purchaseReturnUpdatePayload(
						List.of(purchaseReturnLineById(otherReturnLineId, "1.000000", "其他草稿行")))),
				restricted), HttpStatus.CONFLICT, "REVERSAL_SOURCE_STATUS_INVALID");
	}

	@Test
	void productionMaterialReturnLifecyclePostsInventoryCostAndTraceLinks() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "12.000000", "10.000000");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "0.000000");

		ResponseEntity<String> sources = get(
				"/api/admin/production/material-return-sources?keyword=" + issue.issueNo(), admin);
		assertOk(sources);
		JsonNode source = data(sources).get("items").get(0);
		assertThat(source.get("issueId").longValue()).isEqualTo(issue.issueId());
		assertThat(source.get("workOrderId").longValue()).isEqualTo(issue.workOrderId());
		assertThat(source.get("status").asText()).isEqualTo("POSTED");
		assertDecimalText(source.get("lines").get(0), "issuedQuantity", "12.000000");
		assertDecimalText(source.get("lines").get(0), "returnedQuantity", "0");
		assertDecimalText(source.get("lines").get(0), "returnableQuantity", "12.000000");

		ResponseEntity<String> created = post("/api/admin/production/material-returns",
				materialReturnPayload(issue.issueId(), "material-return-lifecycle-" + SEQUENCE.incrementAndGet(),
						List.of(materialReturnLine(issue.issueLineId(), "3.000000", "余料退回"))),
				admin);
		assertOk(created);
		JsonNode draft = data(created);
		long returnId = draft.get("id").longValue();
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("source").get("sourceId").longValue()).isEqualTo(issue.issueId());
		assertDecimalText(draft, "totalQuantity", "3.000000");

		ResponseEntity<String> updated = put("/api/admin/production/material-returns/" + returnId,
				materialReturnPayload(issue.issueId(), null,
						List.of(materialReturnLine(issue.issueLineId(), "4.000000", "退料调整"))),
				admin);
		assertOk(updated);
		JsonNode updatedLine = data(updated).get("lines").get(0);
		assertDecimalText(updatedLine, "quantity", "4.000000");
		assertDecimalText(updatedLine, "amount", "40.00");

		ResponseEntity<String> posted = put("/api/admin/production/material-returns/" + returnId + "/post",
				Map.of(), admin);
		assertOk(posted);
		JsonNode postedData = data(posted);
		JsonNode postedLine = postedData.get("lines").get(0);
		long returnLineId = postedLine.get("id").longValue();
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertDecimalText(postedLine, "returnedQuantityBefore", "0");
		assertDecimalText(postedLine, "returnableQuantityBefore", "12.000000");
		assertThat(postedLine.get("stockMovementId").isNumber()).isTrue();
		assertThat(postedLine.get("costRecordId").isNumber()).isTrue();

		MovementRow movement = movementForSource("PRODUCTION_MATERIAL_RETURN", returnLineId);
		assertThat(movement.movementType()).isEqualTo("PRODUCTION_MATERIAL_RETURN_IN");
		assertThat(movement.direction()).isEqualTo("IN");
		assertThat(movement.sourceId()).isEqualTo(returnId);
		assertDecimal(movement.quantity(), "4.000000");
		assertDecimal(movement.beforeQuantity(), "0.000000");
		assertDecimal(movement.afterQuantity(), "4.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), "4.000000");
		assertThat(movementQualityStatus("PRODUCTION_MATERIAL_RETURN", returnLineId))
			.isEqualTo(InventoryQualityStatus.PENDING_INSPECTION.name());
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "4.000000");
		assertThat(qualityInspectionCount("PRODUCTION_RETURN", returnId, returnLineId, "PENDING")).isOne();

		CostRecordRow costRecord = costRecord("PRODUCTION_MATERIAL_RETURN", returnLineId);
		assertThat(costRecord.status()).isEqualTo("ACTIVE");
		assertDecimal(costRecord.quantity(), "4.000000");
		assertDecimal(costRecord.unitPrice(), "10.000000");
		assertDecimal(costRecord.amount(), "40.00");

		ReversalLink link = reversalLink("PRODUCTION_MATERIAL_RETURN", returnId, returnLineId);
		assertThat(link.sourceType()).isEqualTo("PRODUCTION_MATERIAL_ISSUE");
		assertThat(link.sourceId()).isEqualTo(issue.issueId());
		assertThat(link.sourceLineId()).isEqualTo(issue.issueLineId());
		assertDecimal(link.quantity(), "4.000000");
		assertDecimal(link.amount(), "40.00");

		JsonNode detail = data(get("/api/admin/production/material-returns/" + returnId, admin));
		assertThat(detail.get("traces").size()).isOne();
		assertThat(detail.get("traces").get(0).get("traceKey").asText()).contains("PRODUCTION_MATERIAL_RETURN");
		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=PRODUCTION_MATERIAL_RETURN&sourceId="
				+ returnId + "&direction=REVERSE_TO_SOURCE", admin)).get(0);
		assertThat(trace.get("direction").asText()).isEqualTo("REVERSE_TO_SOURCE");
		JsonNode listItem = data(get("/api/admin/production/material-returns?keyword="
				+ postedData.get("returnNo").asText(), admin)).get("items").get(0);
		assertThat(listItem.get("id").longValue()).isEqualTo(returnId);
		assertDecimalText(listItem, "totalQuantity", "4.000000");
	}

	@Test
	void productionMaterialReturnBlocksInvalidOperationsAndSupportsRestrictedDraftUpdate() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "2.000000", "10.000000");

		assertError(post("/api/admin/production/material-returns",
				materialReturnPayload(issue.issueId(), null,
						List.of(materialReturnLine(issue.issueLineId(), "2.000001", "超退"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_QUANTITY_EXCEEDS_AVAILABLE");
		assertError(post("/api/admin/production/material-returns",
				materialReturnPayload(issue.issueId(), null,
						List.of(materialReturnLine(issue.issueLineId(), "0.000000", "非法数量"))),
				admin), HttpStatus.BAD_REQUEST, "REVERSAL_QUANTITY_INVALID");

		long cancelledId = createMaterialReturn(admin, issue.issueId(), issue.issueLineId(), "1.000000");
		assertOk(put("/api/admin/production/material-returns/" + cancelledId + "/cancel", Map.of(), admin));
		assertThat(data(get("/api/admin/production/material-returns/" + cancelledId, admin)).get("status").asText())
			.isEqualTo("CANCELLED");
		assertError(put("/api/admin/production/material-returns/" + cancelledId + "/cancel", Map.of(), admin),
				HttpStatus.CONFLICT, "REVERSAL_STATUS_NOT_ALLOWED");

		ProductionReversalFixture draftFixture = productionReversalFixture();
		PostedMaterialIssue draftIssue = createMaterialIssueWithCost(draftFixture, "DRAFT", "1.000000", "10.000000");
		assertError(post("/api/admin/production/material-returns",
				materialReturnPayload(draftIssue.issueId(), null,
						List.of(materialReturnLine(draftIssue.issueLineId(), "1.000000", "未过账来源"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_SOURCE_STATUS_INVALID");

		long returnId = createMaterialReturn(admin, issue.issueId(), issue.issueLineId(), "1.000000");
		long returnLineId = data(get("/api/admin/production/material-returns/" + returnId, admin))
			.get("lines")
			.get(0)
			.get("id")
			.longValue();
		AuthenticatedSession restricted = createUserAndLoginWithPermissions("material-return-update-restricted",
				List.of("production:material-return:view", "production:material-return:update",
						"business:reversal:view"));
		ResponseEntity<String> updated = put("/api/admin/production/material-returns/" + returnId,
				materialReturnUpdatePayload(List.of(materialReturnLineById(returnLineId, "2.000000", "受限来源编辑保存"))),
				restricted);
		assertOk(updated);
		JsonNode updatedData = data(updated);
		assertRestrictedSource(updatedData.get("source"), "PRODUCTION_MATERIAL_ISSUE");
		assertRestrictedDocumentLine(updatedData.get("lines").get(0));
		assertRestrictedSource(updatedData.get("lines").get(0).get("source"), "PRODUCTION_MATERIAL_ISSUE_LINE");
		assertDecimalText(updatedData.get("lines").get(0), "quantity", "2.000000");
		assertDecimalText(updatedData.get("lines").get(0), "amount", "20.00");

		assertOk(put("/api/admin/production/material-returns/" + returnId + "/post", Map.of(), admin));
		JsonNode restrictedPosted = data(get("/api/admin/production/material-returns/" + returnId, restricted));
		assertRestrictedDocumentLine(restrictedPosted.get("lines").get(0));
		assertRestrictedTraceRecord(restrictedPosted.get("traces").get(0));
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(restrictedPosted.get("traces").get(0),
				"PRODUCTION_MATERIAL_ISSUE", issue.issueId(), issue.issueLineId());
		JsonNode restrictedTrace = data(get("/api/admin/reversal-traces?sourceType=PRODUCTION_MATERIAL_RETURN&sourceId="
				+ returnId, restricted)).get(0);
		assertRestrictedTraceRecord(restrictedTrace);
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(restrictedTrace, "PRODUCTION_MATERIAL_ISSUE",
				issue.issueId(), issue.issueLineId());
		assertError(put("/api/admin/production/material-returns/" + returnId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");
		assertError(put("/api/admin/production/material-returns/" + returnId,
				materialReturnPayload(issue.issueId(), null,
						List.of(materialReturnLine(issue.issueLineId(), "1.000000", "已过账不可改"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");
	}

	@Test
	void productionMaterialSupplementLifecyclePostsInventoryCostAndTraceLinks() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "12.000000", "10.000000");
		seedValuedPublicStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "5.000000",
				"10.000000");

		ResponseEntity<String> sources = get("/api/admin/production/material-supplement-sources?keyword="
				+ issue.workOrderNo() + "&warehouseId=" + fixture.warehouseId(), admin);
		assertOk(sources);
		JsonNode source = data(sources).get("items").get(0);
		assertThat(source.get("workOrderId").longValue()).isEqualTo(issue.workOrderId());
		assertThat(source.get("workOrderStatus").asText()).isEqualTo("RELEASED");
		JsonNode material = source.get("materials").get(0);
		assertThat(material.get("workOrderMaterialId").longValue()).isEqualTo(issue.workOrderMaterialId());
		assertDecimalText(material, "issuedQuantity", "12.000000");
		assertDecimalText(material, "supplementedQuantity", "0");
		assertDecimalText(material, "availableStockQuantity", "5.000000");
		assertThat(material.get("qualityStatus").asText()).isEqualTo(InventoryQualityStatus.QUALIFIED.name());
		assertThat(material.get("qualityStatusName").asText()).isEqualTo("合格");
		assertDecimalText(material, "quantityOnHand", "5.000000");
		assertDecimalText(material, "availableQuantity", "5.000000");
		assertThat(material.get("selectable").booleanValue()).isTrue();
		assertThat(material.get("disabledReasonCode").isNull()).isTrue();
		assertThat(material.get("disabledReason").isNull()).isTrue();
		assertDecimalText(material, "maxSelectableQuantity", "5.000000");

		ResponseEntity<String> created = post("/api/admin/production/material-supplements",
				materialSupplementPayload(issue.workOrderId(), fixture.warehouseId(),
						"material-supplement-lifecycle-" + SEQUENCE.incrementAndGet(),
						List.of(materialSupplementLine(issue.workOrderMaterialId(), "2.000000", "损耗补料"))),
				admin);
		assertOk(created);
		JsonNode draft = data(created);
		long supplementId = draft.get("id").longValue();
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("source").get("sourceId").longValue()).isEqualTo(issue.workOrderId());
		assertDecimalText(draft, "totalQuantity", "2.000000");

		ResponseEntity<String> updated = put("/api/admin/production/material-supplements/" + supplementId,
				materialSupplementPayload(issue.workOrderId(), fixture.warehouseId(), null,
						List.of(materialSupplementLine(issue.workOrderMaterialId(), "3.000000", "补料调整"))),
				admin);
		assertOk(updated);
		JsonNode updatedLine = data(updated).get("lines").get(0);
		assertDecimalText(updatedLine, "quantity", "3.000000");
		assertDecimalText(updatedLine, "amount", "30.00");

		ResponseEntity<String> posted = put("/api/admin/production/material-supplements/" + supplementId + "/post",
				Map.of(), admin);
		assertOk(posted);
		JsonNode postedData = data(posted);
		JsonNode postedLine = postedData.get("lines").get(0);
		long supplementLineId = postedLine.get("id").longValue();
		long stockMovementId = postedLine.get("stockMovementId").longValue();
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertThat(postedLine.get("stockMovementId").isNumber()).isTrue();
		assertThat(postedLine.get("costRecordId").isNumber()).isTrue();

		MovementRow movement = movementForSource("PRODUCTION_MATERIAL_SUPPLEMENT", supplementLineId);
		assertThat(movement.movementType()).isEqualTo("PRODUCTION_MATERIAL_SUPPLEMENT_OUT");
		assertThat(movement.direction()).isEqualTo("OUT");
		assertThat(movement.sourceId()).isEqualTo(supplementId);
		assertDecimal(movement.quantity(), "3.000000");
		assertDecimal(movement.beforeQuantity(), "5.000000");
		assertDecimal(movement.afterQuantity(), "2.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), "2.000000");

		ValueMovementRow valueMovement = valueMovementForSource("PRODUCTION_MATERIAL_SUPPLEMENT", supplementId,
				supplementLineId);
		assertThat(valueMovement.stockMovementId()).isEqualTo(stockMovementId);
		assertThat(valueMovement.movementType()).isEqualTo("PRODUCTION_MATERIAL_SUPPLEMENT_OUT");
		assertThat(valueMovement.direction()).isEqualTo("OUT");
		assertDecimal(valueMovement.quantity(), "3.000000");
		assertDecimal(valueMovement.unitCost(), "10.000000");
		assertDecimal(valueMovement.inventoryAmount(), "30.00");
		assertThat(valueMovement.valuationMethod()).isEqualTo("MOVING_WEIGHTED_AVERAGE");
		assertThat(valueMovement.valuationState()).isEqualTo("VALUED");
		assertThat(valueMovement.sourceId()).isEqualTo(supplementId);
		assertThat(valueMovement.sourceLineId()).isEqualTo(supplementLineId);

		CostRecordRow costRecord = costRecord("PRODUCTION_MATERIAL_SUPPLEMENT", supplementLineId);
		assertThat(costRecord.status()).isEqualTo("ACTIVE");
		assertDecimal(costRecord.quantity(), "3.000000");
		assertDecimal(costRecord.unitPrice(), "10.000000");
		assertDecimal(costRecord.amount(), "30.00");

		ReversalLink link = reversalLink("PRODUCTION_MATERIAL_SUPPLEMENT", supplementId, supplementLineId);
		assertThat(link.sourceType()).isEqualTo("PRODUCTION_WORK_ORDER");
		assertThat(link.sourceId()).isEqualTo(issue.workOrderId());
		assertThat(link.sourceLineId()).isEqualTo(issue.workOrderMaterialId());
		assertDecimal(link.quantity(), "3.000000");
		assertDecimal(link.amount(), "30.00");

		JsonNode detail = data(get("/api/admin/production/material-supplements/" + supplementId, admin));
		assertThat(detail.get("traces").size()).isOne();
		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=PRODUCTION_MATERIAL_SUPPLEMENT&sourceId="
				+ supplementId + "&direction=REVERSE_TO_SOURCE", admin)).get(0);
		assertThat(trace.get("direction").asText()).isEqualTo("REVERSE_TO_SOURCE");

		long stockBlockedId = createMaterialSupplement(admin, issue.workOrderId(), fixture.warehouseId(),
				issue.workOrderMaterialId(), "3.000000");
		assertError(put("/api/admin/production/material-supplements/" + stockBlockedId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH");
	}

	@Test
	void productionMaterialSupplementBatchAllocationPostsTrackingOutboundMovement() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		setTrackingMethod(fixture.materialId(), "BATCH");
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "6.000000", "10.000000");
		TrackedBatch batch = seedBatchStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(),
				"REV-SUPPLEMENT-BATCH-" + SEQUENCE.incrementAndGet(), "4.000000");

		Map<String, Object> line = materialSupplementLine(issue.workOrderMaterialId(), "2.000000", "批次生产补料",
				List.of(batchAllocation(batch.batchId(), "2.000000")));
		ResponseEntity<String> created = post("/api/admin/production/material-supplements",
				materialSupplementPayload(issue.workOrderId(), fixture.warehouseId(),
						"material-supplement-batch-" + SEQUENCE.incrementAndGet(), List.of(line)),
				admin);
		assertOk(created);
		long supplementId = data(created).get("id").longValue();

		ResponseEntity<String> posted = put("/api/admin/production/material-supplements/" + supplementId + "/post",
				Map.of(), admin);
		assertOk(posted);
		JsonNode postedLine = data(posted).get("lines").get(0);
		long supplementLineId = postedLine.get("id").longValue();

		assertThat(postedLine.get("trackingAllocations").get(0).get("batchId").longValue()).isEqualTo(batch.batchId());
		assertDecimal(trackingBalanceQuantity(fixture.warehouseId(), fixture.materialId(), batch.batchId()),
				"2.000000");
		assertThat(trackingAllocationMovementCount("PRODUCTION_MATERIAL_SUPPLEMENT", supplementId,
				supplementLineId)).isOne();
		assertThat(trackedMovementCount("PRODUCTION_MATERIAL_SUPPLEMENT", supplementLineId, batch.batchId())).isOne();
	}

	@Test
	void productionMaterialSupplementRejectsWhenOnlyNonQualifiedStockCanCoverRequestedQuantity() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "6.000000", "10.000000");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "1.000000",
				InventoryQualityStatus.QUALIFIED);
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "2.000000",
				InventoryQualityStatus.PENDING_INSPECTION);
		long supplementId = createMaterialSupplement(admin, issue.workOrderId(), fixture.warehouseId(),
				issue.workOrderMaterialId(), "3.000000");
		long supplementLineId = firstMaterialSupplementLineId(supplementId);

		assertError(put("/api/admin/production/material-supplements/" + supplementId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(), InventoryQualityStatus.QUALIFIED),
				"1.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId(),
				InventoryQualityStatus.PENDING_INSPECTION), "2.000000");
		assertThat(movementCountBySource("PRODUCTION_MATERIAL_SUPPLEMENT", supplementLineId)).isZero();
	}

	@Test
	void productionMaterialSupplementSourcesExposeUnavailableQualifiedCandidateFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "6.000000", "10.000000");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "2.000000",
				InventoryQualityStatus.PENDING_INSPECTION);

		ResponseEntity<String> sources = get("/api/admin/production/material-supplement-sources?keyword="
				+ issue.workOrderNo() + "&warehouseId=" + fixture.warehouseId(), admin);

		assertOk(sources);
		JsonNode source = data(sources).get("items").get(0);
		JsonNode material = productionSupplementMaterial(source, issue.workOrderMaterialId());
		assertThat(material.get("qualityStatus").asText()).isEqualTo(InventoryQualityStatus.QUALIFIED.name());
		assertThat(material.get("qualityStatusName").asText()).isEqualTo("合格");
		assertDecimalText(material, "quantityOnHand", "0.000000");
		assertDecimalText(material, "availableStockQuantity", "0.000000");
		assertDecimalText(material, "availableQuantity", "0.000000");
		assertThat(material.get("selectable").booleanValue()).isFalse();
		assertThat(material.get("disabledReasonCode").asText()).isEqualTo("QUALIFIED_BALANCE_NOT_ENOUGH");
		assertThat(material.get("disabledReason").asText()).isEqualTo("合格可用库存不足");
		assertDecimalText(material, "maxSelectableQuantity", "0.000000");
	}

	@Test
	void productionMaterialSupplementBlocksInvalidOperationsAndMasksRestrictedSource() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture fixture = productionReversalFixture();
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(fixture, "6.000000", "10.000000");
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "3.000000");

		ProductionReversalFixture draftFixture = productionReversalFixture();
		PostedMaterialIssue draftWorkOrder = createMaterialIssueWithCost(draftFixture, "DRAFT", "1.000000",
				"10.000000");
		assertError(post("/api/admin/production/material-supplements",
				materialSupplementPayload(draftWorkOrder.workOrderId(), draftFixture.warehouseId(), null,
						List.of(materialSupplementLine(draftWorkOrder.workOrderMaterialId(), "1.000000", "草稿工单"))),
				admin), HttpStatus.CONFLICT, "REVERSAL_SOURCE_STATUS_INVALID");

		long cancelledId = createMaterialSupplement(admin, issue.workOrderId(), fixture.warehouseId(),
				issue.workOrderMaterialId(), "1.000000");
		assertOk(put("/api/admin/production/material-supplements/" + cancelledId + "/cancel", Map.of(), admin));
		assertThat(data(get("/api/admin/production/material-supplements/" + cancelledId, admin)).get("status").asText())
			.isEqualTo("CANCELLED");
		assertError(put("/api/admin/production/material-supplements/" + cancelledId + "/cancel", Map.of(), admin),
				HttpStatus.CONFLICT, "REVERSAL_STATUS_NOT_ALLOWED");

		long supplementId = createMaterialSupplement(admin, issue.workOrderId(), fixture.warehouseId(),
				issue.workOrderMaterialId(), "1.000000");
		assertOk(put("/api/admin/production/material-supplements/" + supplementId + "/post", Map.of(), admin));
		assertError(put("/api/admin/production/material-supplements/" + supplementId + "/cancel", Map.of(), admin),
				HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");

		AuthenticatedSession restricted = createUserAndLoginWithPermissions("material-supplement-restricted",
				List.of("production:material-supplement:view", "business:reversal:view"));
		JsonNode detail = data(get("/api/admin/production/material-supplements/" + supplementId, restricted));
		assertRestrictedSource(detail.get("source"), "PRODUCTION_WORK_ORDER");
		assertRestrictedDocumentLine(detail.get("lines").get(0));
		assertRestrictedSource(detail.get("lines").get(0).get("source"), "PRODUCTION_WORK_ORDER");
		assertRestrictedSource(detail.get("traces").get(0).get("source"), "PRODUCTION_WORK_ORDER");
		assertRestrictedTraceRecord(detail.get("traces").get(0));
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(detail.get("traces").get(0), "PRODUCTION_WORK_ORDER",
				issue.workOrderId(), issue.workOrderMaterialId());
		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=PRODUCTION_MATERIAL_SUPPLEMENT&sourceId="
				+ supplementId, restricted)).get(0);
		assertRestrictedTraceRecord(trace);
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(trace, "PRODUCTION_WORK_ORDER", issue.workOrderId(),
				issue.workOrderMaterialId());
	}

	@Test
	void settlementAdjustmentReceivableLifecyclePostsLedgerAndTrace() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "20.000000", "10.000000",
				"30.000000", "10.000000", "PARTIALLY_RECEIVED", "500.00", "100.00", "400.00");
		PostedReceipt receipt = createPostedReceiptForReceivable(fixture.customerId(), shipment.receivableId(),
				"100.00");
		PostedSalesShipment settledShipment = createPostedShipmentWithReceivable(fixture, "1.000000", "100.000000",
				"1.000000", "100.000000", "RECEIVED", "200.00", "200.00", "0.00");
		createPostedReceiptForReceivable(fixture.customerId(), settledShipment.receivableId(), "200.00");

		JsonNode sources = data(get(
				"/api/admin/finance/settlement-adjustment-sources?settlementSide=RECEIVABLE&sourceType=RECEIPT&keyword="
						+ receipt.receiptNo(),
				admin));
		assertThat(sources.get("items").size()).isOne();
		JsonNode source = sources.get("items").get(0);
		assertThat(source.get("sourceType").asText()).isEqualTo("RECEIPT");
		assertThat(source.get("sourceId").longValue()).isEqualTo(receipt.receiptId());
		assertThat(source.get("targetId").longValue()).isEqualTo(shipment.receivableId());
		assertDecimalText(source, "originalAmount", "500.00");
		assertDecimalText(source, "adjustedAmount", "0.00");
		assertDecimalText(source, "adjustableAmount", "400.00");

		ResponseEntity<String> created = post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "REFUND", "RECEIPT", receipt.receiptId(),
						shipment.receivableId(), "60.00", "settlement-ar-" + SEQUENCE.incrementAndGet()),
				admin);
		assertOk(created);
		JsonNode draft = data(created);
		long adjustmentId = draft.get("id").longValue();
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("source").get("sourceId").longValue()).isEqualTo(receipt.receiptId());
		assertDecimalText(draft, "amount", "60.00");
		assertDecimalText(draft, "targetAdjustableAmountBefore", "400.00");

		ResponseEntity<String> updated = put("/api/admin/finance/settlement-adjustments/" + adjustmentId,
				settlementAdjustmentUpdatePayload("REFUND", "70.00", "调整客户退款冲减"), admin);
		assertOk(updated);
		assertDecimalText(data(updated), "amount", "70.00");

		ResponseEntity<String> cancelledDraft = post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "PAYMENT_OFFSET", "RECEIPT", receipt.receiptId(),
						shipment.receivableId(), "20.00", "settlement-ar-cancel-" + SEQUENCE.incrementAndGet()),
				admin);
		assertOk(cancelledDraft);
		long cancelledId = data(cancelledDraft).get("id").longValue();
		assertOk(put("/api/admin/finance/settlement-adjustments/" + cancelledId + "/cancel", Map.of(), admin));
		assertThat(data(get("/api/admin/finance/settlement-adjustments/" + cancelledId, admin)).get("status").asText())
			.isEqualTo("CANCELLED");

		ResponseEntity<String> posted = put("/api/admin/finance/settlement-adjustments/" + adjustmentId + "/post",
				Map.of(), admin);
		assertOk(posted);
		JsonNode postedData = data(posted);
		assertThat(postedData.get("status").asText()).isEqualTo("POSTED");
		assertDecimalText(postedData, "targetRemainingAmountAfterPost", "330.00");
		assertThat(postedData.get("targetStatusAfterPost").asText()).isEqualTo("PARTIALLY_RECEIVED");

		ReceivableAmounts receivable = receivableAmounts(shipment.receivableId());
		assertDecimal(receivable.adjustedAmount(), "70.00");
		assertDecimal(receivable.unreceivedAmount(), "330.00");
		assertThat(receivable.status()).isEqualTo("PARTIALLY_RECEIVED");

		ReversalLink link = reversalLink("SETTLEMENT_ADJUSTMENT", adjustmentId, 0L);
		assertThat(link.sourceType()).isEqualTo("RECEIPT");
		assertThat(link.sourceId()).isEqualTo(receipt.receiptId());
		assertThat(link.sourceLineId()).isZero();
		assertDecimal(link.amount(), "70.00");

		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=SETTLEMENT_ADJUSTMENT&sourceId="
				+ adjustmentId + "&direction=REVERSE_TO_SOURCE", admin)).get(0);
		assertThat(trace.get("direction").asText()).isEqualTo("REVERSE_TO_SOURCE");
		assertThat(trace.get("source").get("sourceType").asText()).isEqualTo("RECEIPT");
		assertThat(trace.get("reverse").get("sourceType").asText()).isEqualTo("SETTLEMENT_ADJUSTMENT");

		JsonNode listItem = data(get("/api/admin/finance/settlement-adjustments?keyword="
				+ postedData.get("adjustmentNo").asText(), admin)).get("items").get(0);
		assertThat(listItem.get("id").longValue()).isEqualTo(adjustmentId);
		assertDecimalText(listItem, "amount", "70.00");

		assertError(put("/api/admin/finance/settlement-adjustments/" + adjustmentId + "/post", Map.of(), admin),
				HttpStatus.CONFLICT, "REVERSAL_POSTED_IMMUTABLE");
		assertError(put("/api/admin/finance/settlement-adjustments/" + adjustmentId,
				settlementAdjustmentUpdatePayload("REFUND", "10.00", "已过账不可改"), admin), HttpStatus.CONFLICT,
				"REVERSAL_POSTED_IMMUTABLE");
		assertError(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "REFUND", "RECEIPT", receipt.receiptId(),
						shipment.receivableId(), "331.00", "settlement-ar-excess-" + SEQUENCE.incrementAndGet()),
				admin), HttpStatus.CONFLICT, "REVERSAL_AMOUNT_EXCEEDS_AVAILABLE");
	}

	@Test
	void settlementAdjustmentPayableRefundPostsLedgerAndBlocksExcess() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PurchaseReturnFixture fixture = purchaseReturnFixture();
		PostedPurchaseReceipt receipt = createPostedReceiptWithPayable(fixture, "20.000000", "10.000000",
				"10.000000", "10.000000", "PARTIALLY_PAID", "300.00", "80.00", "220.00");
		PostedPayment payment = createPostedPaymentForPayable(fixture.supplierId(), receipt.payableId(), "80.00");

		JsonNode sources = data(get(
				"/api/admin/finance/settlement-adjustment-sources?settlementSide=PAYABLE&sourceType=PAYMENT&keyword="
						+ payment.paymentNo(),
				admin));
		assertThat(sources.get("items").size()).isOne();
		JsonNode source = sources.get("items").get(0);
		assertThat(source.get("sourceType").asText()).isEqualTo("PAYMENT");
		assertThat(source.get("targetId").longValue()).isEqualTo(receipt.payableId());
		assertDecimalText(source, "adjustableAmount", "220.00");

		ResponseEntity<String> created = post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("PAYABLE", "REFUND", "PAYMENT", payment.paymentId(), receipt.payableId(),
						"40.00", "settlement-ap-" + SEQUENCE.incrementAndGet()),
				admin);
		assertOk(created);
		long adjustmentId = data(created).get("id").longValue();
		ResponseEntity<String> posted = put("/api/admin/finance/settlement-adjustments/" + adjustmentId + "/post",
				Map.of(), admin);
		assertOk(posted);
		JsonNode postedData = data(posted);
		assertDecimalText(postedData, "targetRemainingAmountAfterPost", "180.00");
		assertThat(postedData.get("targetStatusAfterPost").asText()).isEqualTo("PARTIALLY_PAID");

		PayableAmounts payable = payableAmounts(receipt.payableId());
		assertDecimal(payable.adjustedAmount(), "40.00");
		assertDecimal(payable.unpaidAmount(), "180.00");
		assertThat(payable.status()).isEqualTo("PARTIALLY_PAID");

		assertError(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("PAYABLE", "PAYMENT_OFFSET", "PAYMENT", payment.paymentId(),
						receipt.payableId(), "181.00", "settlement-ap-excess-" + SEQUENCE.incrementAndGet()),
				admin), HttpStatus.CONFLICT, "REVERSAL_AMOUNT_EXCEEDS_AVAILABLE");
	}

	@Test
	void settlementAdjustmentClientRequestIdRequiresMatchingCoreAndDoesNotReuseCancelled() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "10.000000", "10.000000",
				"10.000000", "10.000000", "CONFIRMED", "200.00", "0.00", "200.00");
		PostedReceipt receipt = createPostedReceiptForReceivable(fixture.customerId(), shipment.receivableId(),
				"50.00");

		String clientRequestId = "settlement-idempotent-" + SEQUENCE.incrementAndGet();
		Map<String, Object> payload = settlementAdjustmentPayload("RECEIVABLE", "PAYMENT_OFFSET", "RECEIPT",
				receipt.receiptId(), shipment.receivableId(), "30.00", clientRequestId);
		JsonNode first = data(post("/api/admin/finance/settlement-adjustments", payload, admin));
		JsonNode repeated = data(post("/api/admin/finance/settlement-adjustments", payload, admin));
		assertThat(repeated.get("id").longValue()).isEqualTo(first.get("id").longValue());

		assertError(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "PAYMENT_OFFSET", "RECEIPT", receipt.receiptId(),
						shipment.receivableId(), "31.00", clientRequestId),
				admin), HttpStatus.CONFLICT, "REVERSAL_DUPLICATED");

		String cancelledClientRequestId = "settlement-cancelled-" + SEQUENCE.incrementAndGet();
		JsonNode cancelled = data(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "PAYMENT_OFFSET", "RECEIPT", receipt.receiptId(),
						shipment.receivableId(), "20.00", cancelledClientRequestId),
				admin));
		assertOk(put("/api/admin/finance/settlement-adjustments/" + cancelled.get("id").longValue() + "/cancel",
				Map.of(), admin));
		assertError(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "PAYMENT_OFFSET", "RECEIPT", receipt.receiptId(),
						shipment.receivableId(), "20.00", cancelledClientRequestId),
				admin), HttpStatus.CONFLICT, "REVERSAL_DUPLICATED");
	}

	@Test
	void settlementAdjustmentRestrictedSourceAndTraceAreRedacted() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesReturnFixture fixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(fixture, "10.000000", "10.000000",
				"10.000000", "10.000000", "CONFIRMED", "200.00", "0.00", "200.00");
		PostedReceipt receipt = createPostedReceiptForReceivable(fixture.customerId(), shipment.receivableId(),
				"50.00");
		JsonNode created = data(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "REFUND", "RECEIPT", receipt.receiptId(),
						shipment.receivableId(), "50.00", "settlement-restricted-" + SEQUENCE.incrementAndGet()),
				admin));
		long adjustmentId = created.get("id").longValue();
		assertOk(put("/api/admin/finance/settlement-adjustments/" + adjustmentId + "/post", Map.of(), admin));

		AuthenticatedSession restricted = createUserAndLoginWithPermissions("settlement-restricted",
				List.of("finance:settlement-adjustment:view", "business:reversal:view"));
		JsonNode detail = data(get("/api/admin/finance/settlement-adjustments/" + adjustmentId, restricted));
		assertRestrictedSource(detail.get("source"), "RECEIPT");
		JsonNode detailTrace = detail.get("traces").get(0);
		assertRestrictedSource(detailTrace.get("source"), "RECEIPT");
		assertRestrictedTraceRecord(detailTrace);
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(detailTrace, "RECEIPT", receipt.receiptId(), 0L);

		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=SETTLEMENT_ADJUSTMENT&sourceId="
				+ adjustmentId, restricted)).get(0);
		assertRestrictedTraceRecord(trace);
		assertRestrictedTraceKeyDoesNotContainSourceCoordinates(trace, "RECEIPT", receipt.receiptId(), 0L);
	}

	@Test
	void settlementAdjustmentRejectsProductionSourcesAndDoesNotMutateLedger() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProductionReversalFixture productionFixture = productionReversalFixture();
		PostedMaterialIssue issue = createPostedMaterialIssueWithCost(productionFixture, "8.000000", "10.000000");
		long materialReturnId = createMaterialReturn(admin, issue.issueId(), issue.issueLineId(), "1.000000");
		assertOk(put("/api/admin/production/material-returns/" + materialReturnId + "/post", Map.of(), admin));
		seedStock(productionFixture.warehouseId(), productionFixture.materialId(), productionFixture.unitId(),
				"5.000000");
		long materialSupplementId = createMaterialSupplement(admin, issue.workOrderId(),
				productionFixture.warehouseId(), issue.workOrderMaterialId(), "1.000000");
		assertOk(put("/api/admin/production/material-supplements/" + materialSupplementId + "/post", Map.of(),
				admin));

		SalesReturnFixture salesFixture = salesReturnFixture();
		PostedSalesShipment shipment = createPostedShipmentWithReceivable(salesFixture, "5.000000", "10.000000",
				"5.000000", "10.000000", "CONFIRMED", "100.00", "0.00", "100.00");
		ReceivableAmounts before = receivableAmounts(shipment.receivableId());

		assertError(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "RETURN_OFFSET", "PRODUCTION_MATERIAL_RETURN",
						materialReturnId, shipment.receivableId(), "10.00",
						"settlement-prod-return-" + SEQUENCE.incrementAndGet()),
				admin), HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
		assertError(post("/api/admin/finance/settlement-adjustments",
				settlementAdjustmentPayload("RECEIVABLE", "RETURN_OFFSET", "PRODUCTION_MATERIAL_SUPPLEMENT",
						materialSupplementId, shipment.receivableId(), "10.00",
						"settlement-prod-supplement-" + SEQUENCE.incrementAndGet()),
				admin), HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");

		ReceivableAmounts after = receivableAmounts(shipment.receivableId());
		assertDecimal(after.adjustedAmount(), before.adjustedAmount().toPlainString());
		assertDecimal(after.unreceivedAmount(), before.unreceivedAmount().toPlainString());
		assertThat(after.status()).isEqualTo(before.status());
		assertSettlementAdjustmentCount("PRODUCTION_MATERIAL_RETURN", materialReturnId, 0);
		assertSettlementAdjustmentCount("PRODUCTION_MATERIAL_SUPPLEMENT", materialSupplementId, 0);
	}

	@Test
	void adminCanQueryStableEmptyReversalSkeletons() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		for (String path : List.of("/api/admin/sales/return-sources?keyword=NO_SUCH_SOURCE",
				"/api/admin/sales/returns?keyword=NO_SUCH_RETURN",
				"/api/admin/procurement/return-sources?keyword=NO_SUCH_SOURCE",
				"/api/admin/procurement/returns?keyword=NO_SUCH_RETURN",
				"/api/admin/production/material-return-sources?keyword=NO_SUCH_SOURCE",
				"/api/admin/production/material-returns?keyword=NO_SUCH_RETURN",
				"/api/admin/production/material-supplement-sources?keyword=NO_SUCH_SOURCE",
				"/api/admin/production/material-supplements?keyword=NO_SUCH_RETURN",
				"/api/admin/finance/settlement-adjustment-sources?keyword=NO_SUCH_SOURCE",
				"/api/admin/finance/settlement-adjustments?keyword=NO_SUCH_RETURN")) {
			assertEmptyPage(get(path, admin));
		}

		ResponseEntity<String> traces = get("/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=1",
				admin);
		assertThat(traces.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(data(traces).isArray()).isTrue();
		assertThat(data(traces).size()).isZero();
	}

	@Test
	void reversalEndpointsRequireAuthenticationAndModulePermission() throws Exception {
		ResponseEntity<String> unauthenticated = this.restTemplate.getForEntity("/api/admin/sales/returns",
				String.class);
		assertError(unauthenticated, HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");

		AuthenticatedSession noPermission = createUserAndLogin("reversal-no-permission", List.of());
		assertError(get("/api/admin/sales/returns", noPermission), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		assertError(get("/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=1", noPermission),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	void writeAndDetailSkeletonsReturnControlledReversalErrors() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		assertError(post("/api/admin/sales/returns",
				salesReturnPayload(999999999L, null, List.of(returnLine(1L, "1.000000", "来源不存在"))), admin),
				HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
		assertError(put("/api/admin/procurement/returns/999/post",
				purchaseReturnActionBody(0L, "采购退货不存在"), admin), HttpStatus.NOT_FOUND,
						"REVERSAL_SOURCE_NOT_FOUND");
		assertError(get("/api/admin/production/material-returns/999", admin), HttpStatus.NOT_FOUND,
				"REVERSAL_SOURCE_NOT_FOUND");
		assertError(put("/api/admin/production/material-supplements/999/cancel", Map.of(), admin),
				HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
		assertError(post("/api/admin/finance/settlement-adjustments",
				Map.of("settlementSide", "RECEIVABLE", "sourceType", "SALES_RETURN", "sourceId", 1,
						"targetId", 999999999, "businessDate", LocalDate.now().toString(), "amount", "1.00"),
				admin), HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
	}

	private AuthenticatedSession createUserAndLogin(String username, List<Long> roleIds) throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/users",
				Map.of("username", username, "displayName", username, "initialPassword", ADMIN_PASSWORD, "status",
						"ENABLED", "roleIds", roleIds),
				admin);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return login(username, ADMIN_PASSWORD);
	}

	private AuthenticatedSession createUserAndLoginWithPermissions(String usernamePrefix, List<String> permissions)
			throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, "REV_ROLE_" + suffix, "反向测试角色" + suffix, "反向测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "反向测试用户" + suffix);
		this.jdbcTemplate.update("""
				insert into sys_user_role (user_id, role_id, created_by, created_at)
				values (?, ?, 'test', now())
				""", userId, roleId);
		for (String permission : permissions) {
			this.jdbcTemplate.update("""
					insert into sys_role_permission (role_id, permission_id, created_by, created_at)
					select ?, id, 'test', now()
					from sys_permission
					where code = ?
					""", roleId, permission);
		}
		return login(username, ADMIN_PASSWORD);
	}

	private SalesReturnFixture salesReturnFixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("REV_UNIT_" + suffix, "反向单位" + suffix);
		long warehouseId = insertWarehouse("REV_WH_" + suffix, "反向仓" + suffix);
		long customerId = insertCustomer("REV_CUS_" + suffix, "反向客户" + suffix);
		long categoryId = insertMaterialCategory("REV_CAT_" + suffix);
		long materialId = insertMaterial("REV_MAT_" + suffix, "退货成品" + suffix, categoryId, unitId);
		long secondMaterialId = insertMaterial("REV_MAT_B_" + suffix, "退货成品B" + suffix, categoryId, unitId);
		return new SalesReturnFixture(unitId, warehouseId, customerId, categoryId, materialId, secondMaterialId);
	}

	private PurchaseReturnFixture purchaseReturnFixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("REV_PUR_UNIT_" + suffix, "采购退货单位" + suffix);
		long warehouseId = insertWarehouse("REV_PUR_WH_" + suffix, "采购退货仓" + suffix);
		long supplierId = insertSupplier("REV_SUP_" + suffix, "反向供应商" + suffix);
		long categoryId = insertMaterialCategory("REV_PUR_CAT_" + suffix);
		long materialId = insertMaterial("REV_PUR_MAT_" + suffix, "采购退货物料" + suffix, categoryId, unitId);
		long secondMaterialId = insertMaterial("REV_PUR_MAT_B_" + suffix, "采购退货物料B" + suffix, categoryId,
				unitId);
		return new PurchaseReturnFixture(unitId, warehouseId, supplierId, categoryId, materialId, secondMaterialId);
	}

	private ProductionReversalFixture productionReversalFixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("REV_MFG_UNIT_" + suffix, "生产反向单位" + suffix);
		long warehouseId = insertWarehouse("REV_MFG_WH_" + suffix, "生产反向仓" + suffix);
		long categoryId = insertMaterialCategory("REV_MFG_CAT_" + suffix);
		long productMaterialId = insertMaterial("REV_MFG_PRODUCT_" + suffix, "生产反向成品" + suffix, categoryId,
				unitId);
		long materialId = insertMaterial("REV_MFG_MAT_" + suffix, "生产反向物料" + suffix, categoryId, unitId);
		return new ProductionReversalFixture(unitId, warehouseId, categoryId, productMaterialId, materialId);
	}

	private PostedMaterialIssue createPostedMaterialIssueWithCost(ProductionReversalFixture fixture,
			String issuedQuantity, String unitPrice) {
		return createMaterialIssueWithCost(fixture, "POSTED", issuedQuantity, unitPrice);
	}

	private PostedMaterialIssue createMaterialIssueWithCost(ProductionReversalFixture fixture, String issueStatus,
			String issuedQuantity, String unitPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
					effective_from, enabled_by, enabled_at, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 1, ?, 'ENABLED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "REV-BOM-" + suffix, fixture.productMaterialId(), "V" + suffix,
				"生产反向BOM" + suffix, fixture.unitId(), LocalDate.now().minusDays(1));
		long bomItemId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom_item (
					bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, remark,
					business_unit_id, business_quantity, base_unit_id, base_quantity, quantity_basis,
					created_at, updated_at
				)
				values (?, 1, ?, ?, 1, 0, '生产反向BOM明细', ?, 1, ?, 1, 'BASE_UNIT', now(), now())
				returning id
				""", Long.class, bomId, fixture.materialId(), fixture.unitId(), fixture.unitId(),
				fixture.unitId());
		String workOrderStatus = "POSTED".equals(issueStatus) ? "RELEASED" : issueStatus;
		String workOrderNo = "REV-WO-" + suffix;
		long workOrderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark,
					created_by, created_at, updated_by, updated_at, released_by, released_at
				)
				values (?, ?, ?, 10, ?, ?, ?, ?, ?, '生产反向工单', 'test', now(), 'test', now(), ?,
					case when ? in ('RELEASED', 'IN_PROGRESS') then now() else null end)
				returning id
				""", Long.class, workOrderNo, fixture.productMaterialId(), bomId, fixture.warehouseId(),
				fixture.warehouseId(), LocalDate.now(), LocalDate.now().plusDays(7), workOrderStatus,
				"RELEASED".equals(workOrderStatus) || "IN_PROGRESS".equals(workOrderStatus) ? "test" : null,
				workOrderStatus);
		long workOrderMaterialId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order_material (
					work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity, issued_quantity,
					loss_rate, remark, business_unit_id, business_quantity, base_unit_id,
					base_required_quantity, quantity_basis, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, 12, ?, 0, '生产反向工单用料', ?, 12, ?, 12, 'BASE_UNIT', now(), now())
				returning id
				""", Long.class, workOrderId, bomItemId, fixture.materialId(), fixture.unitId(),
				"POSTED".equals(issueStatus) ? new BigDecimal(issuedQuantity) : BigDecimal.ZERO, fixture.unitId(),
				fixture.unitId());
		String issueNo = "REV-ISS-" + suffix;
		long issueId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue (
					issue_no, work_order_id, status, business_date, reason, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, '生产反向领料', '生产反向领料', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, issueNo, workOrderId, issueStatus, LocalDate.now(),
				"POSTED".equals(issueStatus) ? "test" : null, issueStatus);
		long issueLineId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue_line (
					issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, remark, created_at, updated_at
				)
				values (?, ?, 1, ?, ?, ?, ?, null, null, '生产反向领料行', now(), now())
				returning id
				""", Long.class, issueId, workOrderMaterialId, fixture.warehouseId(), fixture.materialId(),
				fixture.unitId(), new BigDecimal(issuedQuantity));
		if ("POSTED".equals(issueStatus)) {
			insertProductionIssueCostRecord(fixture, workOrderId, workOrderNo, workOrderMaterialId, issueId, issueNo,
					issueLineId, issuedQuantity, unitPrice);
		}
		return new PostedMaterialIssue(workOrderId, workOrderNo, workOrderMaterialId, issueId, issueNo, issueLineId);
	}

	private void insertProductionIssueCostRecord(ProductionReversalFixture fixture, long workOrderId,
			String workOrderNo, long workOrderMaterialId, long issueId, String issueNo, long issueLineId,
			String quantity, String unitPrice) {
		BigDecimal qty = new BigDecimal(quantity);
		BigDecimal price = new BigDecimal(unitPrice);
		this.jdbcTemplate.update("""
				insert into mfg_cost_record (
					record_no, work_order_id, product_material_id, cost_type, source_type, source_document_type,
					source_document_no, source_document_id, source_line_id, work_order_material_id, material_id,
					unit_id, quantity, unit_price, amount, basis_type, business_date, status, remark,
					recorded_by, recorded_at, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, 'MATERIAL', 'AUTO_PRODUCTION', 'PRODUCTION_MATERIAL_ISSUE', ?, ?, ?, ?, ?, ?, ?,
					?, ?, 'MANUAL_UNIT_PRICE_QUANTITY', ?, 'ACTIVE', ?, 'test', now(), 'test', now(), 'test', now())
				""", "REV-COST-" + SEQUENCE.incrementAndGet(), workOrderId, fixture.productMaterialId(), issueNo,
				issueId, issueLineId, workOrderMaterialId, fixture.materialId(), fixture.unitId(), qty, price,
				money(qty.multiply(price)), LocalDate.now(), "生产领料成本 " + workOrderNo);
	}

	private PostedSalesShipment createPostedShipmentWithReceivable(SalesReturnFixture fixture, String firstQuantity,
			String firstPrice, String secondQuantity, String secondPrice, String receivableStatus, String totalAmount,
			String receivedAmount, String unreceivedAmount) {
		PostedSalesShipment shipment = createShipmentWithoutReceivable(fixture, "POSTED", firstQuantity, firstPrice,
				secondQuantity, secondPrice);
		long receivableId = this.jdbcTemplate.queryForObject("""
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?, ?, ?, ?, '销售退货测试应收', 'test', now(), 'test',
					now(), 'test', now())
				returning id
				""", Long.class, "REV-AR-" + SEQUENCE.incrementAndGet(), fixture.customerId(), shipment.shipmentId(),
				shipment.shipmentNo(), LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal(totalAmount),
				new BigDecimal(receivedAmount), new BigDecimal(unreceivedAmount), receivableStatus);
		this.jdbcTemplate.update("""
				insert into fin_receivable_source (
					receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'SALES_SHIPMENT', ?, ?, ?, 1, ?),
				       (?, 'SALES_SHIPMENT', ?, ?, ?, 2, ?)
				""", receivableId, shipment.shipmentId(), shipment.shipmentNo(), shipment.firstShipmentLineId(),
				money(new BigDecimal(firstQuantity).multiply(new BigDecimal(firstPrice))), receivableId,
				shipment.shipmentId(), shipment.shipmentNo(), shipment.secondShipmentLineId(),
				money(new BigDecimal(secondQuantity).multiply(new BigDecimal(secondPrice))));
		return new PostedSalesShipment(shipment.shipmentId(), shipment.shipmentNo(), shipment.firstShipmentLineId(),
				shipment.secondShipmentLineId(), receivableId);
	}

	private PostedSalesShipment createShipmentWithoutReceivable(SalesReturnFixture fixture, String status) {
		return createShipmentWithoutReceivable(fixture, status, "2.000000", "10.000000", "1.000000", "20.000000");
	}

	private PostedSalesShipment createShipmentWithoutReceivable(SalesReturnFixture fixture, String status,
			String firstQuantity, String firstPrice, String secondQuantity, String secondPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal firstQty = new BigDecimal(firstQuantity);
		BigDecimal secondQty = new BigDecimal(secondQuantity);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, ?, '销售退货测试订单', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "REV-SO-" + suffix, fixture.customerId(), LocalDate.now(),
				LocalDate.now().plusDays(3), "POSTED".equals(status) ? "SHIPPED" : "CONFIRMED");
		long firstOrderLineId = insertSalesOrderLine(orderId, 1, fixture.materialId(), fixture.unitId(), firstQty,
				"POSTED".equals(status) ? firstQty : BigDecimal.ZERO, firstPrice);
		long secondOrderLineId = insertSalesOrderLine(orderId, 2, fixture.secondMaterialId(), fixture.unitId(),
				secondQty, "POSTED".equals(status) ? secondQty : BigDecimal.ZERO, secondPrice);
		String shipmentNo = "REV-SH-" + suffix;
		long shipmentId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, '销售退货测试出库', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, shipmentNo, orderId, fixture.customerId(), fixture.warehouseId(), LocalDate.now(),
				status, "POSTED".equals(status) ? "test" : null, status);
		long firstShipmentLineId = insertSalesShipmentLine(shipmentId, 1, firstOrderLineId, fixture.materialId(),
				fixture.unitId(), firstQty);
		long secondShipmentLineId = insertSalesShipmentLine(shipmentId, 2, secondOrderLineId,
				fixture.secondMaterialId(), fixture.unitId(), secondQty);
		return new PostedSalesShipment(shipmentId, shipmentNo, firstShipmentLineId, secondShipmentLineId, null);
	}

	private long insertSalesOrderLine(long orderId, int lineNo, long materialId, long unitId, BigDecimal quantity,
			BigDecimal shippedQuantity, String unitPrice) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, '销售退货测试订单行', now(), now())
				returning id
				""", Long.class, orderId, lineNo, materialId, unitId, quantity, shippedQuantity,
				new BigDecimal(unitPrice), LocalDate.now().plusDays(3));
	}

	private long insertSalesShipmentLine(long shipmentId, int lineNo, long orderLineId, long materialId, long unitId,
			BigDecimal quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
					remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 0, ?, ?, null, null, '销售退货测试出库行', now(), now())
				returning id
				""", Long.class, shipmentId, lineNo, orderLineId, materialId, unitId, quantity, quantity, quantity);
	}

	private PostedPurchaseReceipt createPostedReceiptWithPayable(PurchaseReturnFixture fixture, String firstQuantity,
			String firstPrice, String secondQuantity, String secondPrice, String payableStatus, String totalAmount,
			String paidAmount, String unpaidAmount) {
		PostedPurchaseReceipt receipt = createReceiptWithoutPayable(fixture, "POSTED", firstQuantity, firstPrice,
				secondQuantity, secondPrice);
		long payableId = this.jdbcTemplate.queryForObject("""
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, paid_amount, adjusted_amount, unpaid_amount, status, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, 0, ?, ?, '采购退货测试应付', 'test', now(), 'test',
					now(), 'test', now())
				returning id
				""", Long.class, "REV-AP-" + SEQUENCE.incrementAndGet(), fixture.supplierId(), receipt.receiptId(),
				receipt.receiptNo(), LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal(totalAmount),
				new BigDecimal(paidAmount), new BigDecimal(unpaidAmount), payableStatus);
		this.jdbcTemplate.update("""
				insert into fin_payable_source (
					payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, 1, ?),
				       (?, 'PURCHASE_RECEIPT', ?, ?, ?, 2, ?)
				""", payableId, receipt.receiptId(), receipt.receiptNo(), receipt.firstReceiptLineId(),
				money(new BigDecimal(firstQuantity).multiply(new BigDecimal(firstPrice))), payableId,
				receipt.receiptId(), receipt.receiptNo(), receipt.secondReceiptLineId(),
				money(new BigDecimal(secondQuantity).multiply(new BigDecimal(secondPrice))));
		return new PostedPurchaseReceipt(receipt.receiptId(), receipt.receiptNo(), receipt.firstReceiptLineId(),
				receipt.secondReceiptLineId(), payableId);
	}

	private PostedPurchaseReceipt createReceiptWithoutPayable(PurchaseReturnFixture fixture, String status) {
		return createReceiptWithoutPayable(fixture, status, "2.000000", "10.000000", "1.000000", "20.000000");
	}

	private PostedPurchaseReceipt createReceiptWithoutPayable(PurchaseReturnFixture fixture, String status,
			String firstQuantity, String firstPrice, String secondQuantity, String secondPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal firstQty = new BigDecimal(firstQuantity);
		BigDecimal secondQty = new BigDecimal(secondQuantity);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, ?, '采购退货测试订单', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "REV-PO-" + suffix, fixture.supplierId(), LocalDate.now(),
				LocalDate.now().plusDays(3), "POSTED".equals(status) ? "RECEIVED" : "CONFIRMED");
		long firstOrderLineId = insertPurchaseOrderLine(orderId, 1, fixture.materialId(), fixture.unitId(), firstQty,
				"POSTED".equals(status) ? firstQty : BigDecimal.ZERO, firstPrice);
		long secondOrderLineId = insertPurchaseOrderLine(orderId, 2, fixture.secondMaterialId(), fixture.unitId(),
				secondQty, "POSTED".equals(status) ? secondQty : BigDecimal.ZERO, secondPrice);
		String receiptNo = "REV-PR-" + suffix;
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, '采购退货测试入库', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, receiptNo, orderId, fixture.supplierId(), fixture.warehouseId(), LocalDate.now(),
				status, "POSTED".equals(status) ? "test" : null, status);
		long firstReceiptLineId = insertPurchaseReceiptLine(receiptId, 1, firstOrderLineId, fixture.materialId(),
				fixture.unitId(), firstQty);
		long secondReceiptLineId = insertPurchaseReceiptLine(receiptId, 2, secondOrderLineId,
				fixture.secondMaterialId(), fixture.unitId(), secondQty);
		return new PostedPurchaseReceipt(receiptId, receiptNo, firstReceiptLineId, secondReceiptLineId, null);
	}

	private long insertPurchaseOrderLine(long orderId, int lineNo, long materialId, long unitId, BigDecimal quantity,
			BigDecimal receivedQuantity, String unitPrice) {
		return this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, expected_arrival_date, remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, '采购退货测试订单行', now(), now())
				returning id
				""", Long.class, orderId, lineNo, materialId, unitId, quantity, receivedQuantity,
				new BigDecimal(unitPrice), new BigDecimal(unitPrice), new BigDecimal(unitPrice),
				quantity.multiply(new BigDecimal(unitPrice)), quantity.multiply(new BigDecimal(unitPrice)),
				LocalDate.now().plusDays(3));
	}

	private long insertPurchaseReceiptLine(long receiptId, int lineNo, long orderLineId, long materialId, long unitId,
			BigDecimal quantity) {
		return this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
					remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 0, ?, ?, null, null, '采购退货测试入库行', now(), now())
				returning id
				""", Long.class, receiptId, lineNo, orderLineId, materialId, unitId, quantity, quantity, quantity);
	}

	private long insertUnit(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at,
					updated_by, updated_at)
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

	private long insertCustomer(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertSupplier(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "反向分类" + code);
	}

	private long insertMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, created_by, created_at, updated_by, updated_at)
				values (?, ?, '反向规格', 'FINISHED_GOOD', 'SELF_MADE', ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private void seedStock(long warehouseId, long materialId, long unitId, String quantity) {
		seedStock(warehouseId, materialId, unitId, quantity, InventoryQualityStatus.QUALIFIED);
	}

	private void seedStock(long warehouseId, long materialId, long unitId, String quantity,
			InventoryQualityStatus qualityStatus) {
		BigDecimal stockQuantity = new BigDecimal(quantity);
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
				""", unitId, stockQuantity, warehouseId, materialId, qualityStatus.name());
		if (updated == 0) {
			this.jdbcTemplate.update("""
					insert into inv_stock_balance (
						warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at,
						updated_at, quality_status, ownership_type, valuation_state
					)
					values (?, ?, ?, ?, 0, now(), now(), ?, 'PUBLIC', 'NON_VALUED')
					""", warehouseId, materialId, unitId, stockQuantity, qualityStatus.name());
		}
	}

	private void seedValuedPublicStock(long warehouseId, long materialId, long unitId, String quantity,
			String averageUnitCost) {
		BigDecimal stockQuantity = new BigDecimal(quantity);
		BigDecimal unitCost = new BigDecimal(averageUnitCost);
		BigDecimal amount = money(stockQuantity.multiply(unitCost));
		this.jdbcTemplate.update("""
				update mst_material
				set inventory_valuation_category = 'VALUATED_MATERIAL',
				    inventory_value_enabled = true,
				    updated_at = now()
				where id = ?
				""", materialId);
		this.jdbcTemplate.update("""
				insert into inv_public_valuation_pool (
					material_id, quantity, amount, average_unit_cost, valuation_state, created_at, updated_at
				)
				values (?, ?, ?, ?, 'VALUED', now(), now())
				on conflict (material_id) do update
				set quantity = excluded.quantity,
				    amount = excluded.amount,
				    average_unit_cost = excluded.average_unit_cost,
				    valuation_state = 'VALUED',
				    updated_at = now(),
				    version = inv_public_valuation_pool.version + 1
				""", materialId, stockQuantity, amount, unitCost);
		seedStock(warehouseId, materialId, unitId, quantity);
		Long poolId = this.jdbcTemplate.queryForObject("""
				select id
				from inv_public_valuation_pool
				where material_id = ?
				""", Long.class, materialId);
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set valuation_state = 'VALUED',
				    inventory_amount = ?,
				    average_unit_cost = ?,
				    public_pool_id = ?,
				    updated_at = now(),
				    version = version + 1
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = ?
				  and batch_id is null
				  and serial_id is null
				  and ownership_type = 'PUBLIC'
				  and project_id is null
				  and cost_layer_id is null
				""", amount, unitCost, poolId, warehouseId, materialId, InventoryQualityStatus.QUALIFIED.name());
	}

	private void setTrackingMethod(long materialId, String trackingMethod) {
		this.jdbcTemplate.update("update mst_material set tracking_method = ?, updated_at = now() where id = ?",
				trackingMethod, materialId);
	}

	private TrackedBatch seedBatchStock(long warehouseId, long materialId, long unitId, String batchNo,
			String quantity) {
		return seedBatchStock(warehouseId, materialId, unitId, batchNo, quantity, InventoryQualityStatus.QUALIFIED);
	}

	private TrackedBatch seedBatchStock(long warehouseId, long materialId, long unitId, String batchNo,
			String quantity, InventoryQualityStatus qualityStatus) {
		long batchId = this.jdbcTemplate.queryForObject("""
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'TEST', ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, materialId, batchNo, 7_300_000L + SEQUENCE.incrementAndGet(),
				7_310_000L + SEQUENCE.incrementAndGet(), LocalDate.now());
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, quantity_on_hand, locked_quantity,
					batch_id, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 0, ?, now(), now())
				""", warehouseId, materialId, unitId, qualityStatus.name(), new BigDecimal(quantity), batchId);
		return new TrackedBatch(batchId, batchNo);
	}

	private TrackedSerial seedSerialStock(long warehouseId, long materialId, long unitId, String serialNo) {
		long serialId = this.jdbcTemplate.queryForObject("""
				insert into inv_serial (
					material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status,
					stock_status, business_date, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'TEST', ?, ?, ?, 'QUALIFIED', 'IN_STOCK', ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, materialId, serialNo, 7_320_000L + SEQUENCE.incrementAndGet(),
				7_330_000L + SEQUENCE.incrementAndGet(), warehouseId, LocalDate.now());
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, quantity_on_hand, locked_quantity,
					serial_id, created_at, updated_at
				)
				values (?, ?, ?, 'QUALIFIED', 1.000000, 0, ?, now(), now())
				""", warehouseId, materialId, unitId, serialId);
		return new TrackedSerial(serialId, serialNo);
	}

	private Map<String, Object> batchAllocation(long batchId, String quantity) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("batchId", batchId);
		allocation.put("quantity", quantity);
		return allocation;
	}

	private Map<String, Object> sourceInheritedBatchAllocation(long sourceAllocationId, long batchId,
			String quantity) {
		Map<String, Object> allocation = batchAllocation(batchId, quantity);
		allocation.put("sourceAllocationId", sourceAllocationId);
		return allocation;
	}

	private Map<String, Object> sourceInheritedSerialAllocation(long sourceAllocationId, long serialId) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("serialId", serialId);
		allocation.put("quantity", "1.000000");
		allocation.put("sourceAllocationId", sourceAllocationId);
		return allocation;
	}

	private long insertTrackedOutboundSource(String documentType, long documentId, long documentLineId,
			long warehouseId, long materialId, long unitId, long batchId, String quantity, String movementType) {
		BigDecimal qty = new BigDecimal(quantity);
		BigDecimal before = trackingBalanceQuantity(warehouseId, materialId, batchId);
		BigDecimal after = before.subtract(qty);
		long movementId = this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at, quality_status, batch_id
				)
				values (?, ?, 'OUT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'test', now(), 'QUALIFIED', ?)
				returning id
				""", Long.class, "REV-TRK-MOV-" + SEQUENCE.incrementAndGet(), movementType, warehouseId, materialId,
				unitId, qty, before, after, documentType, documentId, documentLineId, LocalDate.now(), "测试追踪出库",
				"测试追踪来源", batchId);
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set quantity_on_hand = ?, updated_at = now(), version = version + 1
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id = ?
				""", after, warehouseId, materialId, batchId);
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_tracking_allocation (
					allocation_type, document_type, document_id, document_line_id, source_type, source_id,
					source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity,
					movement_id, created_by, created_at, updated_by, updated_at
				)
				values ('OUTBOUND', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUALIFIED', ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, documentType, documentId, documentLineId, documentType, documentId, documentLineId,
				warehouseId, materialId, unitId, batchId, qty, movementId);
	}

	private long insertTrackedOutboundSerialSource(String documentType, long documentId, long documentLineId,
			long warehouseId, long materialId, long unitId, long serialId, String movementType) {
		BigDecimal before = serialTrackingBalanceQuantity(warehouseId, materialId, serialId,
				InventoryQualityStatus.QUALIFIED);
		long movementId = this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at, quality_status, serial_id
				)
				values (?, ?, 'OUT', ?, ?, ?, 1.000000, ?, ?, ?, ?, ?, ?, ?, ?, 'test', now(), 'QUALIFIED', ?)
				returning id
				""", Long.class, "REV-TRK-SN-MOV-" + SEQUENCE.incrementAndGet(), movementType, warehouseId,
				materialId, unitId, before, before.subtract(BigDecimal.ONE), documentType, documentId, documentLineId,
				LocalDate.now(), "测试追踪序列出库", "测试追踪序列来源", serialId);
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set quantity_on_hand = 0, updated_at = now(), version = version + 1
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and serial_id = ?
				""", warehouseId, materialId, serialId);
		this.jdbcTemplate.update("""
				update inv_serial
				set stock_status = 'OUTBOUND', last_movement_id = ?, updated_at = now(), version = version + 1
				where id = ?
				""", movementId, serialId);
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_tracking_allocation (
					allocation_type, document_type, document_id, document_line_id, source_type, source_id,
					source_line_id, warehouse_id, material_id, unit_id, quality_status, serial_id, quantity,
					movement_id, created_by, created_at, updated_by, updated_at
				)
				values ('OUTBOUND', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUALIFIED', ?, 1.000000, ?, 'test', now(), 'test',
					now())
				returning id
				""", Long.class, documentType, documentId, documentLineId, documentType, documentId, documentLineId,
				warehouseId, materialId, unitId, serialId, movementId);
	}

	private long insertReservation(long warehouseId, long materialId, long unitId, String reservationType,
			String sourceType, String quantity) {
		long sourceId = 8_100_000L + SEQUENCE.incrementAndGet();
		long sourceLineId = 8_110_000L + SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id, quality_status,
					quantity, released_quantity, consumed_quantity, source_type, source_id, source_line_id,
					source_document_no, business_date, reason, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'ACTIVE', ?, ?, ?, 'QUALIFIED', ?, 0, 0, ?, ?, ?, ?, ?, ?, 'tester', now(), 'tester',
					now())
				returning id
				""", Long.class, "REV-RSV-" + SEQUENCE.incrementAndGet(), reservationType, warehouseId, materialId,
				unitId, new BigDecimal(quantity), sourceType, sourceId, sourceLineId, sourceType + "-" + sourceId,
				LocalDate.now(), "反向来源候选预留约束测试");
	}

	private Map<String, Object> salesReturnPayload(long sourceShipmentId, String clientRequestId,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceShipmentId", sourceShipmentId);
		payload.put("businessDate", LocalDate.now().toString());
		if (clientRequestId != null) {
			payload.put("clientRequestId", clientRequestId);
		}
		payload.put("remark", "销售退货测试");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> returnLine(long sourceShipmentLineId, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("sourceShipmentLineId", sourceShipmentLineId);
		line.put("quantity", quantity);
		line.put("reason", reason);
		return line;
	}

	private Map<String, Object> returnLine(long sourceShipmentLineId, String quantity, String reason,
			List<Map<String, Object>> trackingAllocations) {
		Map<String, Object> line = returnLine(sourceShipmentLineId, quantity, reason);
		line.put("trackingAllocations", trackingAllocations);
		return line;
	}

	private Map<String, Object> salesReturnUpdatePayload(List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("remark", "销售退货受限编辑测试");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> returnLineById(long id, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("id", id);
		line.put("quantity", quantity);
		line.put("reason", reason);
		return line;
	}

	private Map<String, Object> returnLineByIdAndSourceLine(long id, long sourceShipmentLineId, String quantity,
			String reason) {
		Map<String, Object> line = returnLineById(id, quantity, reason);
		line.put("sourceShipmentLineId", sourceShipmentLineId);
		return line;
	}

	private Map<String, Object> purchaseReturnPayload(long sourceReceiptId, String clientRequestId,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceReceiptId", sourceReceiptId);
		payload.put("businessDate", LocalDate.now().toString());
		if (clientRequestId != null) {
			payload.put("clientRequestId", clientRequestId);
		}
		payload.put("remark", "采购退货测试");
		payload.put("lines", lines);
		return payload;
	}

	private ResponseEntity<String> postPurchaseReturn(AuthenticatedSession session, long returnId) throws Exception {
		return put("/api/admin/procurement/returns/" + returnId + "/post",
				purchaseReturnActionBody(session, returnId, "过账采购退货"), session);
	}

	private ResponseEntity<String> cancelPurchaseReturn(AuthenticatedSession session, long returnId) throws Exception {
		return put("/api/admin/procurement/returns/" + returnId + "/cancel",
				purchaseReturnActionBody(session, returnId, "取消采购退货"), session);
	}

	private Map<String, Object> purchaseReturnActionBody(AuthenticatedSession session, long returnId, String reason)
			throws Exception {
		return purchaseReturnActionBody(currentPurchaseReturnVersion(session, returnId), reason);
	}

	private Map<String, Object> purchaseReturnActionBody(long version, String reason) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("version", version);
		payload.put("reason", reason);
		payload.put("idempotencyKey", "purchase-return-action-" + SEQUENCE.incrementAndGet());
		return payload;
	}

	private Map<String, Object> withPurchaseReturnVersion(AuthenticatedSession session, long returnId,
			Map<String, Object> payload) throws Exception {
		Map<String, Object> versioned = new LinkedHashMap<>(payload);
		versioned.put("version", currentPurchaseReturnVersion(session, returnId));
		return versioned;
	}

	private long currentPurchaseReturnVersion(AuthenticatedSession session, long returnId) throws Exception {
		return data(get("/api/admin/procurement/returns/" + returnId, session)).get("version").longValue();
	}

	private Map<String, Object> purchaseReturnLine(long sourceReceiptLineId, String quantity, String reason) {
		return purchaseReturnLine(sourceReceiptLineId, quantity, reason, InventoryQualityStatus.QUALIFIED);
	}

	private Map<String, Object> purchaseReturnLine(long sourceReceiptLineId, String quantity, String reason,
			InventoryQualityStatus qualityStatus) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("sourceReceiptLineId", sourceReceiptLineId);
		line.put("quantity", quantity);
		line.put("reason", reason);
		line.put("qualityStatus", qualityStatus.name());
		return line;
	}

	private Map<String, Object> purchaseReturnUpdatePayload(List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("remark", "采购退货受限编辑测试");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> purchaseReturnLineById(long id, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("id", id);
		line.put("quantity", quantity);
		line.put("reason", reason);
		line.put("qualityStatus", "QUALIFIED");
		return line;
	}

	private Map<String, Object> settlementAdjustmentPayload(String settlementSide, String adjustmentType,
			String sourceType, long sourceId, long targetId, String amount, String clientRequestId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("settlementSide", settlementSide);
		payload.put("adjustmentType", adjustmentType);
		payload.put("sourceType", sourceType);
		payload.put("sourceId", sourceId);
		payload.put("targetId", targetId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("amount", amount);
		if (clientRequestId != null) {
			payload.put("clientRequestId", clientRequestId);
		}
		payload.put("remark", "往来冲减测试");
		return payload;
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at) values (?, ?, ?, ?, 'LOCKED', now(), now())",
				"LOCK-REV-" + date, "锁定期间", date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
	}

	private Map<String, Object> settlementAdjustmentUpdatePayload(String adjustmentType, String amount, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("adjustmentType", adjustmentType);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("amount", amount);
		payload.put("remark", remark);
		return payload;
	}

	private Map<String, Object> materialReturnPayload(long sourceIssueId, String clientRequestId,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceIssueId", sourceIssueId);
		payload.put("businessDate", LocalDate.now().toString());
		if (clientRequestId != null) {
			payload.put("clientRequestId", clientRequestId);
		}
		payload.put("remark", "生产退料测试");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> materialReturnUpdatePayload(List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("remark", "生产退料受限编辑测试");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> materialReturnLine(long sourceIssueLineId, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("sourceIssueLineId", sourceIssueLineId);
		line.put("quantity", quantity);
		line.put("reason", reason);
		return line;
	}

	private Map<String, Object> materialReturnLine(long sourceIssueLineId, String quantity, String reason,
			List<Map<String, Object>> trackingAllocations) {
		Map<String, Object> line = materialReturnLine(sourceIssueLineId, quantity, reason);
		line.put("trackingAllocations", trackingAllocations);
		return line;
	}

	private Map<String, Object> materialReturnLineById(long id, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("id", id);
		line.put("quantity", quantity);
		line.put("reason", reason);
		return line;
	}

	private Map<String, Object> materialSupplementPayload(long workOrderId, long warehouseId, String clientRequestId,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("workOrderId", workOrderId);
		payload.put("warehouseId", warehouseId);
		payload.put("businessDate", LocalDate.now().toString());
		if (clientRequestId != null) {
			payload.put("clientRequestId", clientRequestId);
		}
		payload.put("remark", "生产补料测试");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> materialSupplementLine(long workOrderMaterialId, String quantity, String reason) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("workOrderMaterialId", workOrderMaterialId);
		line.put("quantity", quantity);
		line.put("reason", reason);
		return line;
	}

	private Map<String, Object> materialSupplementLine(long workOrderMaterialId, String quantity, String reason,
			List<Map<String, Object>> trackingAllocations) {
		Map<String, Object> line = materialSupplementLine(workOrderMaterialId, quantity, reason);
		line.put("trackingAllocations", trackingAllocations);
		return line;
	}

	private long createSalesReturn(AuthenticatedSession admin, long shipmentId, long shipmentLineId, String quantity)
			throws Exception {
		ResponseEntity<String> response = post("/api/admin/sales/returns",
				salesReturnPayload(shipmentId, "sales-return-" + SEQUENCE.incrementAndGet(),
						List.of(returnLine(shipmentLineId, quantity, "测试退货"))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createSalesReturnWithTracking(AuthenticatedSession admin, long shipmentId, long shipmentLineId,
			long sourceAllocationId, long batchId, String clientRequestId) throws Exception {
		ResponseEntity<String> response = post("/api/admin/sales/returns",
				salesReturnPayload(shipmentId, clientRequestId,
						List.of(returnLine(shipmentLineId, "1.000000", "来源身份重查",
								List.of(sourceInheritedBatchAllocation(sourceAllocationId, batchId, "1.000000"))))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createPurchaseReturn(AuthenticatedSession admin, long receiptId, long receiptLineId, String quantity)
			throws Exception {
		return createPurchaseReturn(admin, receiptId, receiptLineId, quantity, InventoryQualityStatus.QUALIFIED);
	}

	private long createPurchaseReturn(AuthenticatedSession admin, long receiptId, long receiptLineId, String quantity,
			InventoryQualityStatus qualityStatus) throws Exception {
		ResponseEntity<String> response = post("/api/admin/procurement/returns",
				purchaseReturnPayload(receiptId, "purchase-return-" + SEQUENCE.incrementAndGet(),
						List.of(purchaseReturnLine(receiptLineId, quantity, "测试采购退货", qualityStatus))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private PostedReceipt createPostedReceiptForReceivable(long customerId, long receivableId, String amount) {
		int suffix = SEQUENCE.incrementAndGet();
		String receiptNo = "REV-RCPT-" + suffix;
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status, remark, created_by, created_at,
					updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'BANK_TRANSFER', 'POSTED', '往来冲减测试收款', 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, receiptNo, customerId, LocalDate.now(), new BigDecimal(amount));
		this.jdbcTemplate.update("""
				insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
				values (?, ?, ?)
				""", receiptId, receivableId, new BigDecimal(amount));
		return new PostedReceipt(receiptId, receiptNo);
	}

	private PostedPayment createPostedPaymentForPayable(long supplierId, long payableId, String amount) {
		int suffix = SEQUENCE.incrementAndGet();
		String paymentNo = "REV-PAY-" + suffix;
		long paymentId = this.jdbcTemplate.queryForObject("""
				insert into fin_payment (
					payment_no, supplier_id, payment_date, amount, method, status, remark, created_by, created_at,
					updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'BANK_TRANSFER', 'POSTED', '往来冲减测试付款', 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, paymentNo, supplierId, LocalDate.now(), new BigDecimal(amount));
		this.jdbcTemplate.update("""
				insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
				values (?, ?, ?)
				""", paymentId, payableId, new BigDecimal(amount));
		return new PostedPayment(paymentId, paymentNo);
	}

	private long createMaterialReturn(AuthenticatedSession admin, long issueId, long issueLineId, String quantity)
			throws Exception {
		ResponseEntity<String> response = post("/api/admin/production/material-returns",
				materialReturnPayload(issueId, "material-return-" + SEQUENCE.incrementAndGet(),
						List.of(materialReturnLine(issueLineId, quantity, "测试生产退料"))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createSalesReturnWithSerialTracking(AuthenticatedSession admin, long shipmentId, long shipmentLineId,
			long sourceAllocationId, long serialId, String clientRequestId) throws Exception {
		ResponseEntity<String> response = post("/api/admin/sales/returns",
				salesReturnPayload(shipmentId, clientRequestId,
						List.of(returnLine(shipmentLineId, "1.000000", "来源序列身份重查",
								List.of(sourceInheritedSerialAllocation(sourceAllocationId, serialId))))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createMaterialReturnWithTracking(AuthenticatedSession admin, long issueId, long issueLineId,
			long sourceAllocationId, long batchId, String clientRequestId) throws Exception {
		ResponseEntity<String> response = post("/api/admin/production/material-returns",
				materialReturnPayload(issueId, clientRequestId,
						List.of(materialReturnLine(issueLineId, "1.000000", "来源身份重查",
								List.of(sourceInheritedBatchAllocation(sourceAllocationId, batchId, "1.000000"))))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createMaterialReturnWithSerialTracking(AuthenticatedSession admin, long issueId, long issueLineId,
			long sourceAllocationId, long serialId, String clientRequestId) throws Exception {
		ResponseEntity<String> response = post("/api/admin/production/material-returns",
				materialReturnPayload(issueId, clientRequestId,
						List.of(materialReturnLine(issueLineId, "1.000000", "来源序列身份重查",
								List.of(sourceInheritedSerialAllocation(sourceAllocationId, serialId))))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createMaterialSupplement(AuthenticatedSession admin, long workOrderId, long warehouseId,
			long workOrderMaterialId, String quantity) throws Exception {
		ResponseEntity<String> response = post("/api/admin/production/material-supplements",
				materialSupplementPayload(workOrderId, warehouseId, "material-supplement-" + SEQUENCE.incrementAndGet(),
						List.of(materialSupplementLine(workOrderMaterialId, quantity, "测试生产补料"))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
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

	private BigDecimal trackingBalanceQuantity(long warehouseId, long materialId, long batchId) {
		return trackingBalanceQuantity(warehouseId, materialId, batchId, InventoryQualityStatus.QUALIFIED);
	}

	private BigDecimal trackingBalanceQuantity(long warehouseId, long materialId, long batchId,
			InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id = ?
				""", BigDecimal.class, warehouseId, materialId, qualityStatus.name(), batchId);
	}

	private BigDecimal serialTrackingBalanceQuantity(long warehouseId, long materialId, long serialId,
			InventoryQualityStatus qualityStatus) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and serial_id = ?
				""", BigDecimal.class, warehouseId, materialId, qualityStatus.name(), serialId);
	}

	private String movementQualityStatus(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select quality_status
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", String.class, sourceType, sourceLineId);
	}

	private long movementCountBySource(String sourceType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", Long.class, sourceType, sourceLineId);
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

	private long purchaseReturnCount(long receiptId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_return
				where source_receipt_id = ?
				""", Long.class, receiptId);
	}

	private long firstPurchaseReturnLineId(long returnId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from proc_purchase_return_line
				where return_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, returnId);
	}

	private long firstSalesReturnLineId(long returnId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from sal_sales_return_line
				where return_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, returnId);
	}

	private long firstMaterialReturnLineId(long returnId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from mfg_material_return_line
				where return_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, returnId);
	}

	private long firstMaterialSupplementLineId(long supplementId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from mfg_material_supplement_line
				where supplement_id = ?
				order by line_no asc, id asc
				limit 1
				""", Long.class, supplementId);
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

	private ValueMovementRow valueMovementForSource(String sourceType, long sourceId, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select stock_movement_id, movement_type, direction, source_id, source_line_id, quantity, unit_cost,
				       inventory_amount, valuation_method, valuation_state
				from inv_value_movement
				where source_type = ?
				and source_id = ?
				and source_line_id = ?
				""", (rs, rowNum) -> new ValueMovementRow(rs.getLong("stock_movement_id"),
				rs.getString("movement_type"), rs.getString("direction"), rs.getLong("source_id"),
				rs.getLong("source_line_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost"),
				rs.getBigDecimal("inventory_amount"), rs.getString("valuation_method"),
				rs.getString("valuation_state")), sourceType, sourceId, sourceLineId);
	}

	private ReceivableAmounts receivableAmounts(long receivableId) {
		return this.jdbcTemplate.queryForObject("""
				select adjusted_amount, unreceived_amount, status
				from fin_receivable
				where id = ?
				""", (rs, rowNum) -> new ReceivableAmounts(rs.getBigDecimal("adjusted_amount"),
				rs.getBigDecimal("unreceived_amount"), rs.getString("status")), receivableId);
	}

	private PayableAmounts payableAmounts(long payableId) {
		return this.jdbcTemplate.queryForObject("""
				select adjusted_amount, unpaid_amount, status
				from fin_payable
				where id = ?
				""", (rs, rowNum) -> new PayableAmounts(rs.getBigDecimal("adjusted_amount"),
				rs.getBigDecimal("unpaid_amount"), rs.getString("status")), payableId);
	}

	private void assertSettlementAdjustmentCount(String sourceType, long sourceId, long expected) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_settlement_adjustment
				where source_type = ?
				and source_id = ?
				""", Long.class, sourceType, sourceId);
		assertThat(count).isEqualTo(expected);
	}

	private ReversalLink reversalLink(String reverseType, long reverseId, long reverseLineId) {
		return this.jdbcTemplate.queryForObject("""
				select source_type, source_id, source_line_id, quantity, amount
				from biz_reversal_link
				where reverse_type = ?
				and reverse_id = ?
				and reverse_line_id = ?
				""", (rs, rowNum) -> new ReversalLink(rs.getString("source_type"), rs.getLong("source_id"),
				rs.getLong("source_line_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("amount")), reverseType,
				reverseId, reverseLineId);
	}

	private CostRecordRow costRecord(String sourceDocumentType, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select source_document_type, source_line_id, quantity, unit_price, amount, status
				from mfg_cost_record
				where source_document_type = ?
				and source_line_id = ?
				""", (rs, rowNum) -> new CostRecordRow(rs.getString("source_document_type"),
				rs.getLong("source_line_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("amount"), rs.getString("status")), sourceDocumentType, sourceLineId);
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	private void assertEmptyPage(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		assertThat(data.get("items").isArray()).isTrue();
		assertThat(data.get("items").size()).isZero();
		assertThat(data.get("page").intValue()).isOne();
		assertThat(data.get("pageSize").intValue()).isEqualTo(20);
		assertThat(data.get("total").longValue()).isZero();
		assertThat(data.get("totalPages").intValue()).isZero();
	}

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo("OK");
	}

	private void assertDecimalText(JsonNode node, String field, String expected) {
		assertThat(node.get(field).isTextual()).as(field + " should be returned as string").isTrue();
		assertDecimal(new BigDecimal(node.get(field).asText()), expected);
	}

	private void assertDecimal(BigDecimal actual, String expected) {
		assertThat(actual).isNotNull();
		assertThat(actual.compareTo(new BigDecimal(expected))).isZero();
	}

	private void assertRestrictedSource(JsonNode source, String sourceType) {
		assertThat(source.get("sourceType").asText()).isEqualTo(sourceType);
		assertThat(source.get("canViewSource").booleanValue()).isFalse();
		assertThat(source.get("restricted").booleanValue()).isTrue();
		assertThat(source.get("restrictedMessage").asText()).isEqualTo("来源无查看权限");
		assertThat(source.has("sourceId")).isFalse();
		assertThat(source.has("sourceNo")).isFalse();
		assertThat(source.has("sourceLineId")).isFalse();
		assertThat(source.has("quantity")).isFalse();
		assertThat(source.has("amount")).isFalse();
		assertThat(source.has("businessDate")).isFalse();
		assertThat(source.has("status")).isFalse();
		assertThat(source.has("resourceRouteName")).isFalse();
		assertThat(source.has("resourceRouteParams")).isFalse();
		assertThat(source.has("resourceRouteQuery")).isFalse();
	}

	private void assertRestrictedTraceRecord(JsonNode trace) {
		assertThat(trace.get("canViewResource").booleanValue()).isFalse();
		assertThat(trace.get("restricted").booleanValue()).isTrue();
		assertThat(trace.get("restrictedMessage").asText()).isEqualTo("来源无查看权限");
		assertThat(trace.has("inventoryMovementId")).isFalse();
		assertThat(trace.has("settlementAdjustmentId")).isFalse();
		assertThat(trace.has("businessDate")).isFalse();
		assertThat(trace.has("quantity")).isFalse();
		assertThat(trace.has("amount")).isFalse();
		assertThat(trace.has("status")).isFalse();
		assertThat(trace.has("resourceRouteName")).isFalse();
		assertThat(trace.has("resourceRouteParams")).isFalse();
		assertThat(trace.has("resourceRouteQuery")).isFalse();
	}

	private void assertRestrictedDocumentLine(JsonNode line) {
		assertThat(!line.has("sourceLineId") || line.get("sourceLineId").isNull()).isTrue();
	}

	private void assertRestrictedTraceKeyDoesNotContainSourceCoordinates(JsonNode trace, String sourceType,
			long sourceId, long sourceLineId) {
		assertThat(trace.get("traceKey").asText()).doesNotContain(sourceType + ":" + sourceId + ":" + sourceLineId);
	}

	private AuthenticatedSession login(String username, String password) throws Exception {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return new AuthenticatedSession(sessionCookie(response), csrf);
	}

	private CsrfSession csrfSession() throws Exception {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/api/auth/csrf", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
	}

	private ResponseEntity<String> get(String path, AuthenticatedSession session) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				String.class);
	}

	private ResponseEntity<String> post(String path, Object body, AuthenticatedSession session) {
		return exchange(HttpMethod.POST, path, body, session);
	}

	private ResponseEntity<String> put(String path, Object body, AuthenticatedSession session) {
		return exchange(HttpMethod.PUT, path, body, session);
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
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private JsonNode firstItem(ResponseEntity<String> response) throws Exception {
		JsonNode items = data(response).get("items");
		assertThat(items.size()).isGreaterThan(0);
		return items.get(0);
	}

	private JsonNode purchaseReturnCandidate(JsonNode source, long receiptLineId, String qualityStatus) {
		for (JsonNode line : source.get("lines")) {
			if (line.get("receiptLineId").longValue() == receiptLineId && line.hasNonNull("qualityStatus")
					&& qualityStatus.equals(line.get("qualityStatus").asText())) {
				return line;
			}
		}
		throw new AssertionError("采购退货候选缺少质量状态: " + qualityStatus);
	}

	private JsonNode productionSupplementMaterial(JsonNode source, long workOrderMaterialId) {
		for (JsonNode line : source.get("materials")) {
			if (line.get("workOrderMaterialId").longValue() == workOrderMaterialId) {
				return line;
			}
		}
		throw new AssertionError("生产补料候选缺少工单用料: " + workOrderMaterialId);
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo(code);
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

	private record SalesReturnFixture(long unitId, long warehouseId, long customerId, long categoryId, long materialId,
			long secondMaterialId) {
	}

	private record PurchaseReturnFixture(long unitId, long warehouseId, long supplierId, long categoryId,
			long materialId, long secondMaterialId) {
	}

	private record ProductionReversalFixture(long unitId, long warehouseId, long categoryId, long productMaterialId,
			long materialId) {
	}

	private record PostedSalesShipment(long shipmentId, String shipmentNo, long firstShipmentLineId,
			long secondShipmentLineId, Long receivableId) {
	}

	private record PostedPurchaseReceipt(long receiptId, String receiptNo, long firstReceiptLineId,
			long secondReceiptLineId, Long payableId) {
	}

	private record PostedReceipt(long receiptId, String receiptNo) {
	}

	private record PostedPayment(long paymentId, String paymentNo) {
	}

	private record PostedMaterialIssue(long workOrderId, String workOrderNo, long workOrderMaterialId, long issueId,
			String issueNo, long issueLineId) {
	}

	private record MovementRow(String movementType, String direction, long sourceId, long sourceLineId,
			BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity) {
	}

	private record ValueMovementRow(long stockMovementId, String movementType, String direction, long sourceId,
			long sourceLineId, BigDecimal quantity, BigDecimal unitCost, BigDecimal inventoryAmount,
			String valuationMethod, String valuationState) {
	}

	private record ReceivableAmounts(BigDecimal adjustedAmount, BigDecimal unreceivedAmount, String status) {
	}

	private record PayableAmounts(BigDecimal adjustedAmount, BigDecimal unpaidAmount, String status) {
	}

	private record ReversalLink(String sourceType, long sourceId, long sourceLineId, BigDecimal quantity,
			BigDecimal amount) {
	}

	private record CostRecordRow(String sourceDocumentType, long sourceLineId, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal amount, String status) {
	}

	private record TrackedBatch(long batchId, String batchNo) {
	}

	private record TrackedSerial(long serialId, String serialNo) {
	}

}
