package com.qherp.api.system.sales;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.platform.PlatformDocumentTaskWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=sales-stage025-backend",
				"qherp.platform.task.worker.enabled=false"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SalesStage025BackendFlowTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@org.testcontainers.junit.jupiter.Container
	static final GenericContainer<?> minio = new GenericContainer<>(
			DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z"))
		.withEnv("MINIO_ROOT_USER", "qherpminio")
		.withEnv("MINIO_ROOT_PASSWORD", "qherpminio123")
		.withCommand("server /data --console-address :9001")
		.withExposedPorts(9000)
		.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).withStartupTimeout(Duration.ofSeconds(90)));

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("qherp.storage.s3.endpoint", () -> "http://" + minio.getHost() + ":"
				+ minio.getMappedPort(9000));
		registry.add("qherp.storage.s3.region", () -> "us-east-1");
		registry.add("qherp.storage.s3.bucket", () -> "qherp-test-private-stage025-sales");
		registry.add("qherp.storage.s3.access-key", () -> "qherpminio");
		registry.add("qherp.storage.s3.secret-key", () -> "qherpminio123");
		registry.add("qherp.storage.s3.path-style", () -> "true");
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PlatformDocumentTaskWorker documentTaskWorker;

	@Test
	void quoteApprovalAndIdempotentConversionCreatesQuoteSourcedOrderSnapshot() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();

		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", quotePayload(fixture.customerId(),
				List.of(quoteLine(1, fixture.finishedMaterialId(), fixture.unitId(), "3.000000", "120.000000",
						"0.130000")))));
		assertThat(quote.get("status").asText()).isEqualTo("DRAFT");
		assertThat(quote.get("customerId").longValue()).isEqualTo(fixture.customerId());
		assertThat(quote.get("currency").asText()).isEqualTo("CNY");
		assertThat(quote.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("SUBMIT_APPROVAL"));
		JsonNode quoteLine = firstLine(quote);
		assertThat(quoteLine.get("priceSourceType").asText()).isEqualTo("QUOTE");
		assertThat(quoteLine.get("taxExcludedUnitPrice").asText()).isEqualTo("120.000000");
		assertThat(quoteLine.get("taxRate").asText()).isEqualTo("0.130000");

		String submitKey = "S25-QUOTE-SUBMIT-" + quote.get("id").longValue();
		JsonNode submitted = data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()
				+ "/submit-approval", Map.of("version", quote.get("version").longValue(), "reason", "报价审批",
						"idempotencyKey", submitKey)));
		JsonNode submittedAgain = data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()
				+ "/submit-approval", Map.of("version", quote.get("version").longValue(), "reason", "报价审批",
						"idempotencyKey", submitKey)));
		assertThat(submittedAgain.get("id").longValue()).isEqualTo(submitted.get("id").longValue());

		AuthenticatedSession approver = createUserAndLogin("s25-quote-approver-", "S25_QUOTE_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "sales:quote:view",
						"sales:quote:approve"));
		ApprovalTaskHandle task = latestApprovalTask("sales:quote:approve");
		data(post(approver, "/api/admin/approval-tasks/" + task.taskId() + "/approve",
				Map.of("version", task.version(), "comment", "同意报价", "idempotencyKey",
						"S25-QUOTE-APPROVE-" + task.taskId())));

		JsonNode approvedQuote = data(get(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()));
		assertThat(approvedQuote.get("status").asText()).isEqualTo("APPROVED");

		String convertKey = "S25-QUOTE-CONVERT-ORDER-" + quote.get("id").longValue();
		JsonNode order = data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()
				+ "/convert-order", Map.of("version", approvedQuote.get("version").longValue(),
						"idempotencyKey", convertKey)));
		JsonNode orderAgain = data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()
				+ "/convert-order", Map.of("version", approvedQuote.get("version").longValue(),
						"idempotencyKey", convertKey)));
		assertThat(orderAgain.get("id").longValue()).isEqualTo(order.get("id").longValue());
		assertThat(order.get("status").asText()).isEqualTo("DRAFT");
		assertThat(order.get("priceSourceType").asText()).isEqualTo("QUOTE");
		JsonNode orderLine = firstLine(order);
		assertThat(orderLine.get("quoteLineId").longValue()).isEqualTo(quoteLine.get("id").longValue());
		assertThat(orderLine.get("priceSourceType").asText()).isEqualTo("QUOTE");
		assertThat(orderLine.get("sourceNo").asText()).isEqualTo(approvedQuote.get("quoteNo").asText());
		assertThat(orderLine.get("taxExcludedUnitPrice").asText()).isEqualTo("120.000000");
		assertThat(orderLine.get("taxIncludedUnitPrice").asText()).isEqualTo("135.600000");

		Map<String, Object> updatePayload = salesOrderUpdatePayload(order, List.of(
				salesOrderUpdateLine(orderLine, fixture.warehouseId(), orderLine.get("taxExcludedUnitPrice").asText()),
				Map.of("lineNo", 2, "materialId", fixture.finishedMaterialId(), "unitId", fixture.unitId(),
						"quantity", "1.000000", "unitPrice", "80.000000", "reservationWarehouseId",
						fixture.warehouseId(), "expectedShipDate", LocalDate.now().plusDays(10).toString(),
						"remark", "新增手工行")));
		JsonNode updatedOrder = data(put(admin, "/api/admin/sales/orders/" + order.get("id").longValue(),
				updatePayload));
		JsonNode updatedQuoteLine = firstLine(updatedOrder);
		assertThat(updatedQuoteLine.get("priceSourceType").asText()).isEqualTo("QUOTE");
		assertThat(updatedQuoteLine.get("sourceQuoteLineId").longValue()).isEqualTo(quoteLine.get("id").longValue());
		assertThat(updatedQuoteLine.get("sourceNo").asText()).isEqualTo(approvedQuote.get("quoteNo").asText());
		assertThat(updatedQuoteLine.get("reservationWarehouseId").longValue()).isEqualTo(fixture.warehouseId());
		assertThat(updatedQuoteLine.get("taxExcludedUnitPrice").asText()).isEqualTo("120.000000");
		assertThat(updatedQuoteLine.get("taxIncludedUnitPrice").asText()).isEqualTo("135.600000");
		JsonNode manualLine = updatedOrder.get("lines").get(1);
		assertThat(manualLine.get("priceSourceType").asText()).isEqualTo("MANUAL");
		assertThat(manualLine.get("sourceQuoteLineId").isNull()).isTrue();

		Map<String, Object> changedSourcePayload = salesOrderUpdatePayload(updatedOrder, List.of(
				salesOrderUpdateLine(updatedQuoteLine, fixture.warehouseId(), "121.000000"),
				salesOrderUpdateLine(manualLine, fixture.warehouseId(), manualLine.get("taxExcludedUnitPrice").asText())));
		assertError(put(admin, "/api/admin/sales/orders/" + updatedOrder.get("id").longValue(),
				changedSourcePayload), HttpStatus.CONFLICT, "SALES_ORDER_CHANGE_SOURCE_IMMUTABLE");

		ensureCreditProfile(admin, fixture.customerId());
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "4.000000");
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + updatedOrder.get("id").longValue()
				+ "/confirm", Map.of("version", updatedOrder.get("version").longValue(), "idempotencyKey",
						"S25-QUOTE-ORDER-CONFIRM-" + updatedOrder.get("id").longValue())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		JsonNode confirmedQuoteLine = firstLine(confirmed);
		assertThat(confirmedQuoteLine.get("priceSourceType").asText()).isEqualTo("QUOTE");
		assertThat(confirmedQuoteLine.get("sourceQuoteLineId").longValue()).isEqualTo(quoteLine.get("id").longValue());
	}

	@Test
	void quoteDraftUpdateCancelPendingActionsAndConvertContractUseFrozenEndpoints() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long projectId = insertProject("S25_PRJ_CONTRACT_" + SEQUENCE.incrementAndGet(), fixture.customerId(),
				"ACTIVE");

		Map<String, Object> updatePayload = quotePayload(fixture.customerId(),
				List.of(quoteLine(1, fixture.finishedMaterialId(), fixture.unitId(), "5.000000", "90.000000",
						"0.130000")));
		updatePayload.put("projectId", projectId);
		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", updatePayload));
		updatePayload.put("version", quote.get("version").longValue());
		updatePayload.put("remark", "025 更新销售报价");
		JsonNode updated = data(put(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue(),
				updatePayload));
		assertThat(updated.get("version").longValue()).isGreaterThan(quote.get("version").longValue());
		assertThat(updated.get("remark").asText()).isEqualTo("025 更新销售报价");
		assertTextualDecimal(firstLine(updated), "taxExcludedUnitPrice", "90.000000");

		JsonNode cancelQuote = data(post(admin, "/api/admin/sales/quotes",
				quotePayload(fixture.customerId(), List.of(quoteLine(1, fixture.finishedMaterialId(),
						fixture.unitId(), "1.000000", "50.000000", "0.130000")))));
		Map<String, Object> cancelPayload = actionBody(cancelQuote, "客户取消报价",
				"S25-QUOTE-CANCEL-" + cancelQuote.get("id").longValue());
		JsonNode cancelled = data(post(admin, "/api/admin/sales/quotes/" + cancelQuote.get("id").longValue()
				+ "/cancel", cancelPayload));
		JsonNode cancelledAgain = data(post(admin, "/api/admin/sales/quotes/" + cancelQuote.get("id").longValue()
				+ "/cancel", cancelPayload));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelledAgain.get("id").longValue()).isEqualTo(cancelled.get("id").longValue());

		String submitKey = "S25-QUOTE-PENDING-SUBMIT-" + updated.get("id").longValue();
		data(post(admin, "/api/admin/sales/quotes/" + updated.get("id").longValue() + "/submit-approval",
				Map.of("version", updated.get("version").longValue(), "reason", "报价审批",
						"idempotencyKey", submitKey)));
		JsonNode pending = data(get(admin, "/api/admin/sales/quotes/" + updated.get("id").longValue()));
		assertThat(pending.get("status").asText()).isEqualTo("DRAFT");
		assertThat(pending.get("approvalStatus").asText()).isEqualTo("SUBMITTED");
		assertThat(pending.get("allowedActions").toString()).doesNotContain("UPDATE")
			.doesNotContain("SUBMIT_APPROVAL")
			.doesNotContain("CANCEL");

		AuthenticatedSession approver = createUserAndLogin("s25-contract-quote-approver-",
				"S25_CONTRACT_QUOTE_APPROVER_", List.of("platform:approval:view", "platform:todo:view",
						"sales:quote:view", "sales:quote:approve"));
		ApprovalTaskHandle task = latestApprovalTask("sales:quote:approve");
		data(post(approver, "/api/admin/approval-tasks/" + task.taskId() + "/approve",
				Map.of("version", task.version(), "comment", "同意报价", "idempotencyKey",
						"S25-QUOTE-CONTRACT-APPROVE-" + task.taskId())));
		JsonNode approved = data(get(admin, "/api/admin/sales/quotes/" + updated.get("id").longValue()));
		assertThat(approved.get("approvalStatus").asText()).isEqualTo("APPROVED");
		JsonNode contract = data(post(admin, "/api/admin/sales/quotes/" + approved.get("id").longValue()
				+ "/convert-contract", Map.of("version", approved.get("version").longValue(),
						"projectId", projectId, "contractType", "MAIN", "idempotencyKey",
						"S25-QUOTE-CONVERT-CONTRACT-" + approved.get("id").longValue())));
		assertThat(contract.get("contractNo").asText()).startsWith("SC");
		Long sourceQuoteId = this.jdbcTemplate.queryForObject("""
				select source_quote_id
				from sal_project_contract
				where id = ?
				""", Long.class, contract.get("id").longValue());
		assertThat(sourceQuoteId).isEqualTo(approved.get("id").longValue());
		JsonNode converted = data(get(admin, "/api/admin/sales/quotes/" + approved.get("id").longValue()));
		assertThat(converted.get("status").asText()).isEqualTo("CONVERTED");
		assertError(post(admin, "/api/admin/sales/quotes/" + approved.get("id").longValue() + "/convert-order",
				Map.of("version", converted.get("version").longValue(), "idempotencyKey",
						"S25-QUOTE-SECOND-CONVERT-" + approved.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_QUOTE_STATUS_INVALID");
		assertAttachmentObjectAccepted("SALES_QUOTE", approved.get("id").longValue());

		Map<String, Object> expiredPayload = quotePayload(fixture.customerId(), List.of(quoteLine(1,
				fixture.finishedMaterialId(), fixture.unitId(), "1.000000", "70.000000", "0.130000")));
		expiredPayload.put("quoteDate", LocalDate.now().minusDays(2).toString());
		expiredPayload.put("validUntil", LocalDate.now().minusDays(1).toString());
		JsonNode expiredQuote = data(post(admin, "/api/admin/sales/quotes", expiredPayload));
		data(post(admin, "/api/admin/sales/quotes/" + expiredQuote.get("id").longValue() + "/submit-approval",
				Map.of("version", expiredQuote.get("version").longValue(), "reason", "过期报价审批",
						"idempotencyKey", "S25-QUOTE-EXPIRED-SUBMIT-" + expiredQuote.get("id").longValue())));
		ApprovalTaskHandle expiredTask = latestApprovalTask("sales:quote:approve");
		data(post(approver, "/api/admin/approval-tasks/" + expiredTask.taskId() + "/approve",
				Map.of("version", expiredTask.version(), "comment", "同意过期报价", "idempotencyKey",
						"S25-QUOTE-EXPIRED-APPROVE-" + expiredTask.taskId())));
		JsonNode expired = data(get(admin, "/api/admin/sales/quotes/" + expiredQuote.get("id").longValue()));
		assertThat(expired.get("allowedActions").toString()).doesNotContain("CONVERT_ORDER")
			.doesNotContain("CONVERT_CONTRACT");
		assertError(post(admin, "/api/admin/sales/quotes/" + expired.get("id").longValue() + "/convert-order",
				Map.of("version", expired.get("version").longValue(), "idempotencyKey",
						"S25-QUOTE-EXPIRED-CONVERT-" + expired.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_QUOTE_STATUS_INVALID");
	}

	@Test
	void deliveryPlanShipmentPostAndReceivableUseShipmentSnapshot() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "2.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		long orderLineId = firstLine(order).get("id").longValue();
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), orderLineId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "5.000000");

		JsonNode plans = data(get(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans"));
		JsonNode plan = plans.get("items").get(0);
		assertThat(plan.get("orderLineId").longValue()).isEqualTo(orderLineId);
		assertThat(plan.get("plannedQuantity").asText()).isEqualTo("2.000000");
		assertThat(plan.get("status").asText()).isEqualTo("PLANNED");

		String confirmKey = "S25-ORDER-CONFIRM-" + orderId;
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey", confirmKey)));
		JsonNode confirmedAgain = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey", confirmKey)));
		assertThat(confirmedAgain.get("id").longValue()).isEqualTo(confirmed.get("id").longValue());
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(confirmed.get("taxExcludedAmount").asText()).isEqualTo("200.00");
		assertThat(confirmed.get("taxAmount").asText()).isEqualTo("26.00");
		assertThat(confirmed.get("taxIncludedAmount").asText()).isEqualTo("226.00");
		assertSalesOrderReservation(orderId, orderLineId, fixture.warehouseId(), fixture.finishedMaterialId(),
				fixture.unitId(), "PUBLIC", null, "2.000000", "0.000000", "0.000000");
		assertThat(confirmed.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("CREATE_SHIPMENT"));

		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + orderId + "/shipments",
				shipmentPayload(fixture.warehouseId(),
						List.of(shipmentLine(1, orderLineId, plan.get("id").longValue(), fixture.finishedMaterialId(),
								fixture.unitId(), "2.000000")))));
		long shipmentId = shipment.get("id").longValue();
		JsonNode shipmentLine = firstLine(shipment);
		assertThat(shipmentLine.get("deliveryPlanId").longValue()).isEqualTo(plan.get("id").longValue());
		assertThat(shipmentLine.get("taxExcludedAmount").asText()).isEqualTo("200.00");

		long shipmentVersion = this.jdbcTemplate.queryForObject("select version from sal_sales_shipment where id = ?",
				Long.class, shipmentId);
		JsonNode posted = data(put(admin, "/api/admin/sales/shipments/" + shipmentId + "/post",
				Map.of("version", shipmentVersion, "reason", "客户要求提前交付", "idempotencyKey",
						"S25-SHIPMENT-POST-" + shipmentId)));
		assertThat(posted.get("status").asText()).isEqualTo("POSTED");
		assertSalesOrderReservation(orderId, orderLineId, fixture.warehouseId(), fixture.finishedMaterialId(),
				fixture.unitId(), "PUBLIC", null, "2.000000", "0.000000", "2.000000");
		assertTextualDecimal(posted, "totalQuantity", "2.000000");
		JsonNode postedLine = firstLine(posted);
		assertTextualDecimal(postedLine, "orderedQuantity", "2.000000");
		assertTextualDecimal(postedLine, "quantity", "2.000000");
		assertTextualDecimal(postedLine, "beforeQuantity");
		assertTextualDecimal(postedLine, "afterQuantity");
		assertTextualDecimal(postedLine, "quantityOnHand");
		assertTextualDecimal(postedLine, "availableQuantity");
		assertTextualDecimal(postedLine, "maxSelectableQuantity");
		JsonNode postedMovement = posted.get("inventoryMovements").get(0);
		assertTextualDecimal(postedMovement, "quantity", "2.000000");
		assertTextualDecimal(postedMovement, "beforeQuantity");
		assertTextualDecimal(postedMovement, "afterQuantity");
		String postedLineAmount = firstLine(posted).get("taxIncludedAmount").asText();
		assertThat(data(get(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans"))
			.get("items")
			.get(0)
			.get("status")
			.asText()).isEqualTo("SHIPPED");

		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set unit_price = 999.000000, tax_excluded_unit_price = 999.000000,
				    tax_included_unit_price = 999.000000, tax_excluded_amount = 1998.00,
				    tax_included_amount = 1998.00
				where id = ?
				""", orderLineId);
		JsonNode receivable = data(post(admin, "/api/admin/finance/receivables",
				Map.of("sourceType", "SALES_SHIPMENT", "sourceId", shipmentId, "dueDate",
						LocalDate.now().plusDays(30).toString(), "remark", "025 出库快照应收")));
		receivable = data(put(admin, "/api/admin/finance/receivables/" + receivable.get("id").longValue() + "/confirm",
				Map.of()));
		assertThat(receivable.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(new BigDecimal(receivable.get("totalAmount").asText()).compareTo(new BigDecimal(postedLineAmount)))
			.isZero();
		JsonNode sources = data(get(admin, "/api/admin/finance/receivables/" + receivable.get("id").longValue()
				+ "/sources?page=1&pageSize=20"));
		assertThat(sources.get("items")).hasSize(1);
		assertThat(new BigDecimal(sources.get("items").get(0).get("sourceAmount").asText())
			.compareTo(new BigDecimal(postedLineAmount))).isZero();

		JsonNode salesReturn = data(post(admin, "/api/admin/sales/returns",
				salesReturnPayload(shipmentId, shipmentLine.get("id").longValue(), "1.000000")));
		assertThat(salesReturn.hasNonNull("version")).isTrue();
		assertThat(salesReturn.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("POST"));
		assertThat(salesReturn.has("actionDisabledReason")).isTrue();
		JsonNode salesReturnDetail = data(get(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue()));
		assertThat(salesReturnDetail.hasNonNull("version")).isTrue();
		assertThat(salesReturnDetail.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("POST"));
		JsonNode salesReturnLine = firstLine(salesReturnDetail);
		assertThat(salesReturnLine.get("unitPrice").asText()).isEqualTo("113.000000");
		assertThat(salesReturnLine.get("amount").asText()).isEqualTo("113.00");
		JsonNode salesReturnList = data(get(admin, "/api/admin/sales/returns?customerId=" + fixture.customerId()
				+ "&page=1&pageSize=20"));
		JsonNode listedSalesReturn = findItemById(salesReturnList, salesReturn.get("id").longValue());
		assertThat(listedSalesReturn.hasNonNull("version")).isTrue();
		assertThat(listedSalesReturn.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("POST"));
		assertError(post(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue() + "/post",
				Map.of()), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		String returnPostKey = "S25-SALES-RETURN-POST-" + salesReturn.get("id").longValue();
		Map<String, Object> postReturnAction = actionBody(salesReturnDetail, "客户退货", returnPostKey);
		JsonNode postedReturn = data(post(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue()
				+ "/post", postReturnAction));
		JsonNode postedReturnTrace = postedReturn.get("traces").get(0);
		assertThat(postedReturnTrace.get("warehouseId").longValue()).isEqualTo(fixture.warehouseId());
		assertThat(postedReturnTrace.get("warehouseName").asText()).isNotBlank();
		assertThat(postedReturnTrace.get("materialId").longValue()).isEqualTo(fixture.finishedMaterialId());
		assertThat(postedReturnTrace.get("materialCode").asText()).isNotBlank();
		assertThat(postedReturnTrace.get("materialName").asText()).isNotBlank();
		assertThat(postedReturnTrace.get("quantity").asText()).isEqualTo("1.000000");
		assertThat(postedReturnTrace.has("costRecordId")).isFalse();
		assertThat(postedReturnTrace.has("valueMovementId")).isFalse();
		assertThat(postedReturnTrace.has("movementNo")).isFalse();
		assertThat(postedReturnTrace.has("valuationState")).isFalse();
		assertThat(postedReturnTrace.has("valuationStateName")).isFalse();
		assertThat(postedReturnTrace.has("valuationAmount")).isFalse();
		assertThat(postedReturnTrace.has("valuationUnitCost")).isFalse();
		JsonNode postedReturnAgain = data(post(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue()
				+ "/post", postReturnAction));
		assertThat(postedReturnAgain.get("id").longValue()).isEqualTo(postedReturn.get("id").longValue());
		assertError(post(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue() + "/post",
				Map.of("version", salesReturnDetail.get("version").longValue(), "reason", "不同原因",
						"idempotencyKey", returnPostKey)),
				HttpStatus.CONFLICT, "SALES_ACTION_IDEMPOTENCY_CONFLICT");

		JsonNode cancellableReturn = data(post(admin, "/api/admin/sales/returns",
				salesReturnPayload(shipmentId, shipmentLine.get("id").longValue(), "0.500000")));
		assertError(put(admin, "/api/admin/sales/returns/" + cancellableReturn.get("id").longValue() + "/cancel",
				Map.of()), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		Map<String, Object> cancelReturnAction = actionBody(cancellableReturn, "客户取消退货",
				"S25-SALES-RETURN-CANCEL-" + cancellableReturn.get("id").longValue());
		JsonNode cancelledReturn = data(post(admin, "/api/admin/sales/returns/"
				+ cancellableReturn.get("id").longValue() + "/cancel", cancelReturnAction));
		JsonNode cancelledReturnAgain = data(post(admin, "/api/admin/sales/returns/"
				+ cancellableReturn.get("id").longValue() + "/cancel", cancelReturnAction));
		assertThat(cancelledReturn.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelledReturnAgain.get("id").longValue()).isEqualTo(cancelledReturn.get("id").longValue());
	}

	@Test
	void effectiveDemandsReturnConfirmedOrderLineAsCountedDemand() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "4.000000", "80.000000", "0.130000");
		long orderId = order.get("id").longValue();
		data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-DEMAND-CONFIRM-" + orderId)));

		JsonNode page = data(get(admin, "/api/admin/sales/effective-demands?customerId=" + fixture.customerId()
				+ "&countedOnly=true&page=1&pageSize=20"));
		assertThat(page.get("items").size()).isGreaterThan(0);
		JsonNode demand = page.get("items").get(0);
		assertThat(demand.get("orderId").longValue()).isEqualTo(orderId);
		assertThat(demand.get("sourceType").asText()).isEqualTo("SALES_ORDER");
		assertThat(demand.get("countedAsEffectiveDemand").booleanValue()).isTrue();
		assertThat(demand.get("openDemandQuantity").asText()).isEqualTo("4.000000");

		JsonNode diagnosticPage = data(get(admin, "/api/admin/sales/effective-demands?customerId="
				+ fixture.customerId() + "&countedOnly=false&page=1&pageSize=20"));
		assertThat(diagnosticPage.get("items").size()).isGreaterThan(0);
		assertThat(diagnosticPage.toString()).contains("\"countedAsEffectiveDemand\":true");

		JsonNode quotePage = data(get(admin, "/api/admin/sales/quotes?page=1&pageSize=20"));
		assertThat(quotePage.get("items").size()).isGreaterThan(0);
		assertThat(quotePage.get("items").get(0).has("allowedActions")).isTrue();
		assertThat(quotePage.get("items").get(0).has("actionDisabledReason")).isTrue();

		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", quotePayload(fixture.customerId(),
				List.of(quoteLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000", "20.000000",
						"0.130000")))));
		data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue() + "/submit-approval",
				Map.of("version", quote.get("version").longValue(), "reason", "报价审批", "idempotencyKey",
						"S25-DOC-QUOTE-SUBMIT-" + quote.get("id").longValue())));
		AuthenticatedSession approver = createUserAndLogin("s25-doc-quote-approver-", "S25_DOC_QUOTE_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "sales:quote:view",
						"sales:quote:approve"));
		ApprovalTaskHandle task = latestApprovalTask("sales:quote:approve");
		data(post(approver, "/api/admin/approval-tasks/" + task.taskId() + "/approve",
				Map.of("version", task.version(), "comment", "同意报价", "idempotencyKey",
						"S25-DOC-QUOTE-APPROVE-" + task.taskId())));
		JsonNode approvedQuote = data(get(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()));
		assertThat(approvedQuote.get("status").asText()).isEqualTo("APPROVED");
		JsonNode quotePrintTask = data(postWithIdempotency(admin, "/api/admin/print-tasks",
				Map.of("objectType", "SALES_QUOTE", "objectId", approvedQuote.get("id").longValue(),
						"templateCode", "SALES_QUOTE_V1"),
				"S25-QUOTE-PRINT-" + approvedQuote.get("id").longValue()));
		assertThat(quotePrintTask.get("taskType").asText()).isEqualTo("SALES_QUOTE_PRINT");
		assertThat(quotePrintTask.get("objectType").asText()).isEqualTo("SALES_QUOTE");
		assertThat(quotePrintTask.get("objectId").longValue()).isEqualTo(approvedQuote.get("id").longValue());

		JsonNode demandExportTask = data(postWithIdempotency(admin, "/api/admin/export-tasks",
				Map.of("taskType", "SALES_EFFECTIVE_DEMAND_EXPORT",
						"filters", Map.of("customerId", fixture.customerId(), "countedOnly", true)),
				"S25-DEMAND-EXPORT-" + fixture.customerId()));
		assertThat(demandExportTask.get("taskType").asText()).isEqualTo("SALES_EFFECTIVE_DEMAND_EXPORT");

		assertThat(this.documentTaskWorker.processAvailableOnce()).isTrue();
		assertThat(this.documentTaskWorker.processAvailableOnce()).isTrue();
		JsonNode finishedPrintTask = data(get(admin, "/api/admin/document-tasks/" + quotePrintTask.get("id").longValue()));
		assertThat(finishedPrintTask.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(finishedPrintTask.get("availableActions").toString()).contains("DOWNLOAD");
		JsonNode finishedExportTask = data(get(admin, "/api/admin/document-tasks/" + demandExportTask.get("id").longValue()));
		assertThat(finishedExportTask.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(finishedExportTask.get("availableActions").toString()).contains("DOWNLOAD");
	}

	@Test
	void creditProfilesDemandOrderChangePlanAndCloseContractsHaveRealEndpoints() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "6.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		long orderLineId = firstLine(order).get("id").longValue();

		JsonNode draftDemand = data(get(admin, "/api/admin/sales/effective-demands?customerId="
				+ fixture.customerId() + "&page=1&pageSize=20"));
		assertThat(draftDemand.get("items")).isEmpty();
		JsonNode diagnosticDemand = data(get(admin, "/api/admin/sales/effective-demands?customerId="
				+ fixture.customerId() + "&countedOnly=false&page=1&pageSize=20"));
		assertThat(diagnosticDemand.get("items").size()).isGreaterThan(0);
		assertThat(diagnosticDemand.get("items").get(0).get("countedAsEffectiveDemand").booleanValue()).isFalse();

		JsonNode profile = data(post(admin, "/api/admin/sales/credit-profiles",
				Map.of("customerId", fixture.customerId(), "creditLimit", "100000.00", "frozen", false,
						"blockOverdue", false, "remark", "025 信用档案")));
		JsonNode profileList = data(get(admin, "/api/admin/sales/credit-profiles?customerId="
				+ fixture.customerId() + "&page=1&pageSize=20"));
		assertThat(profileList.get("items").size()).isEqualTo(1);
		JsonNode profileDetail = data(get(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId()));
		assertThat(profileDetail.get("id").longValue()).isEqualTo(profile.get("id").longValue());
		JsonNode updatedProfile = data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				Map.of("customerId", fixture.customerId(), "creditLimit", "120000.00", "frozen", false,
						"blockOverdue", false, "remark", "025 信用档案更新",
						"version", profileDetail.get("version").longValue())));
		assertThat(updatedProfile.get("creditLimit").asText()).isEqualTo("120000.00");

		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-CONTRACTS-CONFIRM-" + orderId)));
		JsonNode replaced = data(put(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans",
				Map.of("version", confirmed.get("version").longValue(), "reason", "拆分交付计划",
						"idempotencyKey", "S25-PLAN-REPLACE-" + orderId,
						"lines", List.of(
								Map.of("orderLineId", orderLineId, "planDate", LocalDate.now().plusDays(5).toString(),
										"quantity", "2.000000", "remark", "首批"),
								Map.of("orderLineId", orderLineId, "planDate", LocalDate.now().plusDays(12).toString(),
										"quantity", "4.000000", "remark", "尾批")))));
		assertThat(replaced.get("lines").size()).isEqualTo(2);
		assertThat(replaced.get("lines").get(0).get("allowedActions").toString()).contains("CLOSE");
		assertThat(replaced.get("lines").get(0).has("actionDisabledReason")).isTrue();
		assertThat(replaced.get("lines").get(0).get("actionDisabledReason").isNull()).isTrue();

		JsonNode listedPlans = data(get(admin, "/api/admin/sales/delivery-plans?orderId=" + orderId
				+ "&countedOnly=true&page=1&pageSize=20"));
		assertThat(listedPlans.get("items").size()).isEqualTo(2);
		JsonNode firstPlan = listedPlans.get("items").get(0);
		assertThat(firstPlan.has("actionDisabledReason")).isTrue();
		assertThat(firstPlan.get("actionDisabledReason").isNull()).isTrue();
		JsonNode closedPlan = data(put(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans/"
				+ firstPlan.get("id").longValue() + "/close", Map.of("version", firstPlan.get("version").longValue(),
						"reason", "客户取消首批", "idempotencyKey", "S25-PLAN-CLOSE-"
								+ firstPlan.get("id").longValue())));
		assertThat(closedPlan.get("status").asText()).isEqualTo("CLOSED");
		assertThat(closedPlan.has("actionDisabledReason")).isTrue();
		assertThat(closedPlan.get("actionDisabledReason").asText()).isNotBlank();

		JsonNode latestOrder = data(get(admin, "/api/admin/sales/orders/" + orderId));
		Map<String, Object> changePayload = orderChangePayload(latestOrder, "7.000000", "105.000000", "0.130000",
				LocalDate.now().plusDays(20));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderId + "/changes", changePayload));
		assertThat(change.has("actionDisabledReason")).isTrue();
		assertThat(change.get("actionDisabledReason").isNull()).isTrue();
		JsonNode changeDetail = data(get(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()));
		assertThat(changeDetail.has("actionDisabledReason")).isTrue();
		assertThat(changeDetail.get("actionDisabledReason").isNull()).isTrue();
		JsonNode changeList = data(get(admin, "/api/admin/sales/orders/" + orderId
				+ "/changes?status=DRAFT&page=1&pageSize=20"));
		JsonNode listedChange = findItemById(changeList, change.get("id").longValue());
		assertThat(listedChange.get("allowedActions").toString()).contains("CANCEL");
		assertThat(listedChange.has("actionDisabledReason")).isTrue();
		assertThat(listedChange.get("actionDisabledReason").isNull()).isTrue();
		Map<String, Object> updateChangePayload = orderChangePayload(latestOrder, "8.000000", "106.000000",
				"0.130000", LocalDate.now().plusDays(21));
		updateChangePayload.put("version", change.get("version").longValue());
		updateChangePayload.put("idempotencyKey", "S25-ORDER-CHANGE-UPDATE-" + change.get("id").longValue());
		JsonNode updatedChange = data(put(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue(),
				updateChangePayload));
		assertTextualDecimal(firstLine(updatedChange), "newQuantity", "8.000000");
		JsonNode cancelledChange = data(post(admin, "/api/admin/sales/order-changes/"
				+ updatedChange.get("id").longValue() + "/cancel", actionBody(updatedChange, "不再调整订单",
						"S25-ORDER-CHANGE-CANCEL-" + updatedChange.get("id").longValue())));
		assertThat(cancelledChange.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelledChange.has("actionDisabledReason")).isTrue();
		assertThat(cancelledChange.get("actionDisabledReason").asText()).isNotBlank();

		assertError(put(admin, "/api/admin/sales/orders/" + orderId + "/close",
				Map.of("version", latestOrder.get("version").longValue(), "reason", "仍有开放计划",
						"idempotencyKey", "S25-OPEN-PLAN-CLOSE-" + orderId)),
				HttpStatus.CONFLICT, "SALES_ORDER_CLOSE_BLOCKED");
	}

	@Test
	void creditProfileGuardsWriteLogsAndOrderCancelUsesVersionedIdempotency() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrderWithoutCredit(admin, fixture, "3.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		long orderLineId = firstLine(order).get("id").longValue();
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), orderLineId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "3.000000");

		assertError(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-CREDIT-MISSING-CONFIRM-" + orderId)),
				HttpStatus.CONFLICT, "SALES_CREDIT_PROFILE_MISSING");

		JsonNode frozenProfile = data(post(admin, "/api/admin/sales/credit-profiles",
				Map.of("customerId", fixture.customerId(), "creditLimit", "100000.00", "frozen", true,
						"blockOverdue", false, "remark", "冻结信用档案")));
		assertError(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-CREDIT-FROZEN-CONFIRM-" + orderId)),
				HttpStatus.CONFLICT, "SALES_CREDIT_FROZEN");

		JsonNode activeProfile = data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				Map.of("customerId", fixture.customerId(), "creditLimit", "100000.00", "frozen", false,
						"blockOverdue", false, "remark", "恢复信用档案",
						"version", frozenProfile.get("version").longValue())));
		assertThat(activeProfile.get("frozen").booleanValue()).isFalse();

		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-CREDIT-PASS-CONFIRM-" + orderId)));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		Long logCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_credit_check_log
				where source_type = 'SALES_ORDER'
				and source_id = ?
				and check_result in ('PASSED', 'BLOCKED')
				""", Long.class, orderId);
		assertThat(logCount).isGreaterThanOrEqualTo(3);

		assertError(put(admin, "/api/admin/sales/orders/" + orderId + "/cancel", Map.of()),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		Map<String, Object> cancelPayload = actionBody(confirmed, "客户取消未出库订单",
				"S25-ORDER-CANCEL-" + orderId);
		JsonNode cancelled = data(put(admin, "/api/admin/sales/orders/" + orderId + "/cancel", cancelPayload));
		JsonNode cancelledAgain = data(put(admin, "/api/admin/sales/orders/" + orderId + "/cancel", cancelPayload));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelledAgain.get("id").longValue()).isEqualTo(cancelled.get("id").longValue());
		assertThat(activeSalesOrderReservationCount(orderId)).isZero();
	}

	@Test
	void creditOverrideOrdersRemainExposedAndOrderChangeCreditIsRecheckedAtSubmitAndApply() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrderWithoutCredit(admin, fixture, "2.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();

		JsonNode profile = data(post(admin, "/api/admin/sales/credit-profiles",
				Map.of("customerId", fixture.customerId(), "creditLimit", "1.00", "frozen", false,
						"blockOverdue", false, "remark", "低额度信用档案")));
		JsonNode override = data(post(admin, "/api/admin/sales/orders/" + orderId + "/submit-credit-override",
				Map.of("version", order.get("version").longValue(), "reason", "额度例外确认订单",
						"idempotencyKey", "S25-ORDER-CREDIT-OVERRIDE-" + orderId)));
		assertThat(override.get("sceneCode").asText()).isEqualTo("SALES_ORDER_CREDIT_OVERRIDE");
		AuthenticatedSession creditApprover = createUserAndLogin("s25-credit-approver-", "S25_CREDIT_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "sales:order:view",
						"sales:order-change:view", "sales:credit:override-approve"));
		ApprovalTaskHandle orderCreditTask = latestApprovalTask("sales:credit:override-approve");
		JsonNode confirmed = data(post(creditApprover, "/api/admin/approval-tasks/" + orderCreditTask.taskId()
				+ "/approve", Map.of("version", orderCreditTask.version(), "comment", "同意订单信用例外",
						"idempotencyKey", "S25-ORDER-CREDIT-APPROVE-" + orderCreditTask.taskId())));
		assertThat(data(get(admin, "/api/admin/sales/orders/" + orderId)).get("status").asText())
			.isEqualTo("CONFIRMED");

		JsonNode exposure = data(get(admin, "/api/admin/sales/customers/" + fixture.customerId()
				+ "/credit-exposure"));
		assertTextualDecimal(exposure, "orderCommitmentAmount", "226.00");
		assertTextualDecimal(exposure, "usedCredit", "226.00");

		JsonNode latestOrder = data(get(admin, "/api/admin/sales/orders/" + orderId));
		Map<String, Object> changePayload = orderChangePayload(latestOrder, "3.000000", "100.000000",
				"0.130000", LocalDate.now().plusDays(12));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderId + "/changes", changePayload));
		JsonNode frozenProfile = data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				Map.of("customerId", fixture.customerId(), "creditLimit", "1.00", "frozen", true,
						"blockOverdue", false, "remark", "冻结信用档案",
						"version", profile.get("version").longValue())));
		assertError(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", Map.of("version", change.get("version").longValue(), "reason", "增量信用冻结",
						"idempotencyKey", "S25-CHANGE-CREDIT-FROZEN-" + change.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CREDIT_FROZEN");

		JsonNode activeProfile = data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				Map.of("customerId", fixture.customerId(), "creditLimit", "1.00", "frozen", false,
						"blockOverdue", false, "remark", "恢复信用档案",
						"version", frozenProfile.get("version").longValue())));
		JsonNode changeOverride = data(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", Map.of("version", change.get("version").longValue(), "reason", "增量信用例外",
						"idempotencyKey", "S25-CHANGE-CREDIT-OVERRIDE-" + change.get("id").longValue())));
		assertThat(changeOverride.get("sceneCode").asText()).isEqualTo("SALES_ORDER_CHANGE_CREDIT_OVERRIDE");

		data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				Map.of("customerId", fixture.customerId(), "creditLimit", "1.00", "frozen", true,
						"blockOverdue", false, "remark", "审批前再次冻结",
						"version", activeProfile.get("version").longValue())));
		ApprovalTaskHandle changeCreditTask = latestApprovalTask("sales:credit:override-approve");
		assertError(post(creditApprover, "/api/admin/approval-tasks/" + changeCreditTask.taskId() + "/approve",
				Map.of("version", changeCreditTask.version(), "comment", "冻结后不得绕过",
						"idempotencyKey", "S25-CHANGE-CREDIT-APPROVE-" + changeOverride.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CREDIT_FROZEN");
		Long changeLogCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_credit_check_log
				where source_type = 'SALES_ORDER_CHANGE'
				and source_id = ?
				""", Long.class, change.get("id").longValue());
		assertThat(changeLogCount).isGreaterThanOrEqualTo(2);
	}

	@Test
	void creditWriteGatesUseThreePartExposureForUnsettledShipmentsAndReceivables() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode shippedOrder = createApprovedQuoteOrder(admin, fixture, "2.000000", "100.000000", "0.130000");
		long shippedOrderId = shippedOrder.get("id").longValue();
		JsonNode shippedOrderLine = firstLine(shippedOrder);
		long shippedOrderLineId = shippedOrderLine.get("id").longValue();
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), shippedOrderLineId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "5.000000");
		data(put(admin, "/api/admin/sales/orders/" + shippedOrderId + "/confirm",
				Map.of("version", shippedOrder.get("version").longValue(), "idempotencyKey",
						"S25-CREDIT-THREE-PART-CONFIRM-" + shippedOrderId)));
		JsonNode plan = data(get(admin, "/api/admin/sales/orders/" + shippedOrderId + "/delivery-plans"))
			.get("items")
			.get(0);
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + shippedOrderId + "/shipments",
				shipmentPayload(fixture.warehouseId(),
						List.of(shipmentLine(1, shippedOrderLineId, plan.get("id").longValue(),
								fixture.finishedMaterialId(), fixture.unitId(), "2.000000")))));
		long shipmentId = shipment.get("id").longValue();
		data(put(admin, "/api/admin/sales/shipments/" + shipmentId + "/post",
				Map.of("version", shipment.get("version").longValue(), "reason", "形成未结出库占用",
						"idempotencyKey", "S25-CREDIT-THREE-PART-SHIP-" + shipmentId)));

		JsonNode exposureWithShipment = data(get(admin, "/api/admin/sales/customers/" + fixture.customerId()
				+ "/credit-exposure"));
		assertTextualDecimal(exposureWithShipment, "unsettledShipmentAmount", "226.00");
		assertTextualDecimal(exposureWithShipment, "usedCredit", "226.00");
		JsonNode profile = data(get(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId()));
		JsonNode lowLimitProfile = data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				Map.of("customerId", fixture.customerId(), "creditLimit", "300.00", "frozen", false,
						"blockOverdue", false, "remark", "未结出库占用低额度",
						"version", profile.get("version").longValue())));
		JsonNode blockedOrder = createApprovedQuoteOrderWithoutCredit(admin, fixture, "1.000000", "100.000000",
				"0.130000");
		assertError(put(admin, "/api/admin/sales/orders/" + blockedOrder.get("id").longValue() + "/confirm",
				Map.of("version", blockedOrder.get("version").longValue(), "idempotencyKey",
						"S25-CREDIT-UNSETTLED-BLOCK-" + blockedOrder.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CREDIT_LIMIT_EXCEEDED");

		JsonNode receivable = data(post(admin, "/api/admin/finance/receivables",
				Map.of("sourceType", "SALES_SHIPMENT", "sourceId", shipmentId, "dueDate",
						LocalDate.now().plusDays(30).toString(), "remark", "025 信用三段应收")));
		data(put(admin, "/api/admin/finance/receivables/" + receivable.get("id").longValue() + "/confirm",
				Map.of()));
		JsonNode exposureWithReceivable = data(get(admin, "/api/admin/sales/customers/" + fixture.customerId()
				+ "/credit-exposure"));
		assertTextualDecimal(exposureWithReceivable, "unsettledShipmentAmount", "0.00");
		assertTextualDecimal(exposureWithReceivable, "receivableOutstandingAmount", "226.00");
		assertTextualDecimal(exposureWithReceivable, "usedCredit", "226.00");

		JsonNode increasedLimitProfile = data(put(admin, "/api/admin/sales/credit-profiles/"
				+ fixture.customerId(), Map.of("customerId", fixture.customerId(), "creditLimit", "400.00",
						"frozen", false, "blockOverdue", false, "remark", "允许订单但阻断变更",
						"version", lowLimitProfile.get("version").longValue())));
		assertThat(increasedLimitProfile.get("version").longValue()).isGreaterThan(lowLimitProfile.get("version")
			.longValue());
		JsonNode changeOrder = createApprovedQuoteOrderWithoutCredit(admin, fixture, "1.000000", "100.000000",
				"0.130000");
		long changeOrderId = changeOrder.get("id").longValue();
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), firstLine(changeOrder).get("id").longValue());
		JsonNode confirmedChangeOrder = data(put(admin, "/api/admin/sales/orders/" + changeOrderId + "/confirm",
				Map.of("version", changeOrder.get("version").longValue(), "idempotencyKey",
						"S25-CREDIT-RECEIVABLE-CONFIRM-" + changeOrderId)));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + changeOrderId + "/changes",
				orderChangePayload(confirmedChangeOrder, "2.000000", "100.000000", "0.130000",
						LocalDate.now().plusDays(12))));
		JsonNode changeCreditOverride = data(post(admin, "/api/admin/sales/order-changes/"
				+ change.get("id").longValue() + "/submit-approval", Map.of("version",
						change.get("version").longValue(), "reason", "未清应收占用阻断普通变更审批",
						"idempotencyKey", "S25-CREDIT-RECEIVABLE-CHANGE-" + change.get("id").longValue())));
		assertThat(changeCreditOverride.get("sceneCode").asText()).isEqualTo("SALES_ORDER_CHANGE_CREDIT_OVERRIDE");
	}

	@Test
	void orderCloseAndProjectFulfillmentCloseBlockPendingBusinessAndExposeCanonicalResponse() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "2.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		long orderLineId = firstLine(order).get("id").longValue();
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-PENDING-CLOSE-CONFIRM-" + orderId)));
		JsonNode plan = data(get(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans"))
			.get("items")
			.get(0);
		data(put(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans/"
				+ plan.get("id").longValue() + "/close", Map.of("version", plan.get("version").longValue(),
						"reason", "关闭计划以隔离待处理变更门禁", "idempotencyKey",
						"S25-PENDING-CLOSE-PLAN-" + plan.get("id").longValue())));
		JsonNode latestOrder = data(get(admin, "/api/admin/sales/orders/" + orderId));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderId + "/changes",
				orderChangePayload(latestOrder, "3.000000", "100.000000", "0.130000",
						LocalDate.now().plusDays(20))));
		assertThat(change.get("status").asText()).isEqualTo("DRAFT");
		assertAttachmentObjectAccepted("SALES_ORDER_CHANGE", change.get("id").longValue());
		assertError(put(admin, "/api/admin/sales/orders/" + orderId + "/close",
				Map.of("version", latestOrder.get("version").longValue(), "reason", "仍有待处理变更",
						"idempotencyKey", "S25-PENDING-CHANGE-CLOSE-" + orderId)),
				HttpStatus.CONFLICT, "SALES_ORDER_CLOSE_BLOCKED");

		long projectId = insertProject("S25_PRJ_FULFILL_" + SEQUENCE.incrementAndGet(), fixture.customerId(),
				"ACTIVE");
		Map<String, Object> quotePayload = quotePayload(fixture.customerId(),
				List.of(quoteLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000",
						"100.000000", "0.130000")));
		quotePayload.put("projectId", projectId);
		data(post(admin, "/api/admin/sales/quotes", quotePayload));
		AuthenticatedSession fulfillmentViewer = createUserAndLogin("s25-fulfillment-viewer-",
				"S25_FULFILLMENT_VIEWER_", List.of("sales:fulfillment:view"));
		JsonNode restricted = data(get(fulfillmentViewer, "/api/admin/sales-projects/" + projectId
				+ "/fulfillment"));
		assertThat(restricted.has("status")).as(restricted.toString()).isTrue();
		assertThat(restricted.get("status").asText()).isEqualTo("OPEN");
		assertThat(restricted.get("contractRestricted").booleanValue()).isTrue();
		assertThat(restricted.get("contractEffectiveAmount").isNull()).isTrue();
		assertThat(restricted.get("creditRestricted").booleanValue()).isTrue();
		assertThat(restricted.get("allowedActions").isEmpty()).isTrue();

		JsonNode fulfillment = data(get(admin, "/api/admin/sales-projects/" + projectId + "/fulfillment"));
		assertThat(fulfillment.get("blockReasons").toString()).contains("PENDING_QUOTE_CONVERSION");
		assertThat(fulfillment.get("allowedActions").toString()).doesNotContain("CLOSE");
		assertError(post(admin, "/api/admin/sales-projects/" + projectId + "/close-sales-fulfillment",
				Map.of("version", fulfillment.get("version").longValue(), "reason", "仍有报价待转换",
						"idempotencyKey", "S25-FULFILLMENT-CLOSE-" + projectId)),
				HttpStatus.CONFLICT, "PROJECT_HAS_OPEN_BUSINESS");
	}

	@Test
	void projectFulfillmentCloseOnlyAllowsShippedWhenLegacyCompatibilityFlagIsTrue() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long projectId = insertProject("S25_PRJ_LEGACY_SHIPPED_" + SEQUENCE.incrementAndGet(),
				fixture.customerId(), "ACTIVE");
		long orderId = insertProjectSalesOrder(projectId, fixture.customerId(), "SHIPPED", false);
		insertLegacySalesOrderLine(orderId, fixture.finishedMaterialId(), fixture.unitId(), "1.000000", "3200.000000");

		JsonNode orderDetail = data(get(admin, "/api/admin/sales/orders/" + orderId));
		assertThat(orderDetail.get("taxIncludedAmount").asText()).isEqualTo("3200.00");
		JsonNode orderSummary = findItemById(data(get(admin, "/api/admin/sales/orders?projectId="
				+ projectId + "&page=1&pageSize=20")), orderId);
		assertThat(orderSummary.get("taxIncludedAmount").asText()).isEqualTo("3200.00");

		JsonNode blocked = data(get(admin, "/api/admin/sales-projects/" + projectId + "/fulfillment"));
		JsonNode blockedOrder = blocked.get("salesOrders").get(0);
		assertThat(blockedOrder.get("taxIncludedAmount").asText()).isEqualTo("3200.00");
		assertThat(blocked.get("orderTaxIncludedAmount").asText()).isEqualTo("3200.00");
		assertThat(blocked.get("legacyDeliveryPlanCompatible").asBoolean()).isFalse();
		assertThat(blocked.get("blockReasons").toString()).contains("NON_TERMINAL_ORDER");
		assertThat(blocked.get("allowedActions").toString()).doesNotContain("CLOSE");
		assertError(post(admin, "/api/admin/sales-projects/" + projectId + "/close-sales-fulfillment",
				Map.of("version", blocked.get("version").longValue(), "reason", "新订单 SHIPPED 不可关闭项目销售履约",
						"idempotencyKey", "S25-FULFILLMENT-SHIPPED-BLOCK-" + projectId)),
				HttpStatus.CONFLICT, "PROJECT_HAS_OPEN_BUSINESS");

		this.jdbcTemplate.update("""
				update sal_sales_order
				set sales_fulfillment_compatible = true
				where id = ?
				""", orderId);
		JsonNode compatible = data(get(admin, "/api/admin/sales-projects/" + projectId + "/fulfillment"));
		assertThat(compatible.get("legacyDeliveryPlanCompatible").asBoolean()).isTrue();
		assertThat(compatible.get("blockReasons").toString()).doesNotContain("NON_TERMINAL_ORDER");
	}

	@Test
	void salesAttachmentsSupportQuoteOrderChangeAndProjectWithBusinessPermissions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", quotePayload(fixture.customerId(),
				List.of(quoteLine(1, fixture.finishedMaterialId(), fixture.unitId(), "1.000000", "100.000000",
						"0.130000")))));
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "1.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-ATTACHMENT-ORDER-CONFIRM-" + orderId)));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderId + "/changes",
				orderChangePayload(confirmed, "2.000000", "100.000000", "0.130000",
						LocalDate.now().plusDays(12))));
		long projectId = insertProject("S25_PRJ_ATTACHMENT_" + SEQUENCE.incrementAndGet(), fixture.customerId(),
				"ACTIVE");

		assertAttachmentRoundTrip(admin, "SALES_QUOTE", quote.get("id").longValue());
		assertAttachmentRoundTrip(admin, "SALES_ORDER_CHANGE", change.get("id").longValue());
		assertAttachmentRoundTrip(admin, "SALES_PROJECT", projectId);

		AuthenticatedSession projectViewer = createUserAndLogin("s25-project-attachment-viewer-",
				"S25_PROJECT_ATTACHMENT_VIEWER_", List.of("platform:attachment:view", "platform:attachment:upload",
						"platform:attachment:download", "platform:attachment:delete", "sales:project:view"));
		assertError(uploadAttachment(projectViewer, "SALES_PROJECT", projectId, "blocked.txt",
				"blocked".getBytes(StandardCharsets.UTF_8)), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		AuthenticatedSession noQuoteView = createUserAndLogin("s25-quote-attachment-no-view-",
				"S25_QUOTE_ATTACHMENT_NO_VIEW_", List.of("platform:attachment:view"));
		assertError(get(noQuoteView, "/api/admin/attachments?objectType=SALES_QUOTE&objectId="
				+ quote.get("id").longValue()), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	void manualOrderAllowsSameMaterialWhenLineCommercialSnapshotDiffers() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		ensureCreditProfile(admin, fixture.customerId());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", fixture.customerId());
		payload.put("orderDate", LocalDate.now().toString());
		payload.put("expectedShipDate", LocalDate.now().plusDays(10).toString());
		payload.put("remark", "同物料不同税价交期");
		payload.put("lines", List.of(
				Map.of("lineNo", 1, "materialId", fixture.finishedMaterialId(), "unitId", fixture.unitId(),
						"quantity", "1.000000", "unitPrice", "100.000000",
						"reservationWarehouseId", fixture.warehouseId(),
						"expectedShipDate", LocalDate.now().plusDays(5).toString()),
				Map.of("lineNo", 2, "materialId", fixture.finishedMaterialId(), "unitId", fixture.unitId(),
						"quantity", "2.000000", "unitPrice", "120.000000",
						"reservationWarehouseId", fixture.warehouseId(),
						"expectedShipDate", LocalDate.now().plusDays(10).toString())));
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "3.000000");
		JsonNode order = data(post(admin, "/api/admin/sales/orders", payload));
		assertThat(order.get("lines").size()).isEqualTo(2);
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + order.get("id").longValue()
				+ "/confirm", Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-SAME-MATERIAL-CONFIRM-" + order.get("id").longValue())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(confirmed.get("lines").size()).isEqualTo(2);
	}

	@Test
	void effectiveDemandKeepsPositiveShipmentOpenQuantityAfterSalesReturn() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "8.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		JsonNode orderLine = firstLine(order);
		long orderLineId = orderLine.get("id").longValue();
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), orderLineId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "8.000000");
		data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-DEMAND-RETURN-CONFIRM-" + orderId)));

		JsonNode plan = data(get(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans"))
			.get("items")
			.get(0);
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + orderId + "/shipments",
				shipmentPayload(fixture.warehouseId(),
						List.of(shipmentLine(1, orderLineId, plan.get("id").longValue(), fixture.finishedMaterialId(),
								fixture.unitId(), "3.000000")))));
		long shipmentId = shipment.get("id").longValue();
		JsonNode shipmentLine = firstLine(shipment);
		long shipmentVersion = this.jdbcTemplate.queryForObject("select version from sal_sales_shipment where id = ?",
				Long.class, shipmentId);
		data(put(admin, "/api/admin/sales/shipments/" + shipmentId + "/post",
				Map.of("version", shipmentVersion, "reason", "025 有效需求出库", "idempotencyKey",
						"S25-DEMAND-RETURN-SHIPMENT-POST-" + shipmentId)));

		JsonNode receivable = data(post(admin, "/api/admin/finance/receivables",
				Map.of("sourceType", "SALES_SHIPMENT", "sourceId", shipmentId, "dueDate",
						LocalDate.now().plusDays(30).toString(), "remark", "025 有效需求退货应收")));
		receivable = data(put(admin, "/api/admin/finance/receivables/" + receivable.get("id").longValue()
				+ "/confirm", Map.of()));
		assertThat(receivable.get("status").asText()).isEqualTo("CONFIRMED");

		JsonNode salesReturn = data(post(admin, "/api/admin/sales/returns",
				salesReturnPayload(shipmentId, shipmentLine.get("id").longValue(), "1.000000")));
		JsonNode salesReturnDetail = data(get(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue()));
		data(post(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue() + "/post",
				actionBody(salesReturnDetail, "025 有效需求销售退货",
						"S25-DEMAND-RETURN-POST-" + salesReturn.get("id").longValue())));

		JsonNode demandPage = data(get(admin, "/api/admin/sales/effective-demands?customerId="
				+ fixture.customerId() + "&countedOnly=true&page=1&pageSize=20"));
		JsonNode demand = demandPage.get("items").get(0);
		assertThat(demand.get("orderId").longValue()).isEqualTo(orderId);
		assertTextualDecimal(demand, "orderQuantity", "8.000000");
		assertTextualDecimal(demand, "shippedQuantity", "3.000000");
		assertTextualDecimal(demand, "returnedQuantity", "1.000000");
		assertTextualDecimal(demand, "netQuantity", "2.000000");
		assertTextualDecimal(demand, "openDemandQuantity", "5.000000");
		assertTextualDecimal(demand, "openQuantity", "5.000000");
	}

	@Test
	void earlyShipmentPostRequiresActionReasonBeforeInventoryPosting() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "2.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		long orderLineId = firstLine(order).get("id").longValue();
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), orderLineId);
		data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-EARLY-CONFIRM-" + orderId)));
		JsonNode plan = data(get(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans"))
			.get("items")
			.get(0);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "5.000000");
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + orderId + "/shipments",
				shipmentPayload(fixture.warehouseId(), LocalDate.now(),
						List.of(shipmentLine(1, orderLineId, plan.get("id").longValue(),
								fixture.finishedMaterialId(), fixture.unitId(), "1.000000")))));

		assertError(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				Map.of("version", shipment.get("version").longValue(), "reason", "",
						"idempotencyKey", "S25-EARLY-POST-" + shipment.get("id").longValue())),
				HttpStatus.BAD_REQUEST, "SALES_SHIPMENT_EARLY_REASON_REQUIRED");
		this.jdbcTemplate.update("""
				update sal_sales_delivery_plan
				set planned_date = current_date
				where id = ?
				""", plan.get("id").longValue());
		JsonNode nonEarlyPosted = data(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue()
				+ "/post", Map.of("version", shipment.get("version").longValue(), "reason", "",
						"idempotencyKey", "S25-NON-EARLY-POST-" + shipment.get("id").longValue())));
		assertThat(nonEarlyPosted.get("status").asText()).isEqualTo("POSTED");
		assertTextualDecimal(nonEarlyPosted, "totalQuantity", "1.000000");
		assertTextualDecimal(firstLine(nonEarlyPosted), "quantity", "1.000000");
	}

	@Test
	void orderChangeApprovalAndShortCloseUseApprovalContracts() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "10.000000", "50.000000", "0.130000");
		long orderId = order.get("id").longValue();
		long orderLineId = firstLine(order).get("id").longValue();
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), orderLineId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "20.000000");
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-CHANGE-CONFIRM-" + orderId)));
		JsonNode plan = data(get(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans"))
			.get("items")
			.get(0);
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + orderId + "/shipments",
				shipmentPayload(fixture.warehouseId(),
						List.of(shipmentLine(1, orderLineId, plan.get("id").longValue(), fixture.finishedMaterialId(),
								fixture.unitId(), "4.000000")))));
		data(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				Map.of("version", shipment.get("version").longValue(), "reason", "首批出库",
						"idempotencyKey", "S25-CHANGE-POST-" + shipment.get("id").longValue())));

		JsonNode latestOrder = data(get(admin, "/api/admin/sales/orders/" + orderId));
		Map<String, Object> changePayload = orderChangePayload(latestOrder, "12.000000", "60.000000", "0.130000",
				LocalDate.now().plusDays(12));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderId + "/changes", changePayload));
		JsonNode sameCreate = data(post(admin, "/api/admin/sales/orders/" + orderId + "/changes", changePayload));
		assertThat(sameCreate.get("id").longValue()).isEqualTo(change.get("id").longValue());
		assertError(post(admin, "/api/admin/sales/orders/" + orderId + "/changes",
				orderChangePayload(latestOrder, "13.000000", "60.000000", "0.130000",
						LocalDate.now().plusDays(12))),
				HttpStatus.CONFLICT, "SALES_ACTION_IDEMPOTENCY_CONFLICT");
		assertThat(change.get("status").asText()).isEqualTo("DRAFT");
		JsonNode submitted = data(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", Map.of("version", change.get("version").longValue(), "reason", "调整订单",
						"idempotencyKey", "S25-CHANGE-SUBMIT-" + change.get("id").longValue())));
		JsonNode submittedAgain = data(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", Map.of("version", change.get("version").longValue(), "reason", "调整订单",
						"idempotencyKey", "S25-CHANGE-SUBMIT-" + change.get("id").longValue())));
		assertThat(submittedAgain.get("id").longValue()).isEqualTo(submitted.get("id").longValue());
		assertError(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", Map.of("version", change.get("version").longValue(), "reason", "陈旧版本",
						"idempotencyKey", "S25-CHANGE-STALE-" + change.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CONCURRENT_MODIFICATION");

		AuthenticatedSession changeApprover = createUserAndLogin("s25-change-approver-", "S25_CHANGE_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "sales:order-change:view",
						"sales:order-change:approve"));
		ApprovalTaskHandle changeTask = latestApprovalTask("sales:order-change:approve");
		data(post(changeApprover, "/api/admin/approval-tasks/" + changeTask.taskId() + "/approve",
				Map.of("version", changeTask.version(), "comment", "同意变更", "idempotencyKey",
						"S25-CHANGE-APPROVE-" + submitted.get("id").longValue())));
		JsonNode applied = data(get(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()));
		assertThat(applied.get("status").asText()).isEqualTo("APPLIED");
		JsonNode changedOrder = data(get(admin, "/api/admin/sales/orders/" + orderId));
		assertTextualDecimal(changedOrder, "totalQuantity", "12.000000");
		assertTextualDecimal(changedOrder, "shippedQuantity", "4.000000");
		assertTextualDecimal(changedOrder, "remainingQuantity", "8.000000");
		assertTextualDecimal(firstLine(changedOrder), "quantity", "12.000000");
		assertTextualDecimal(firstLine(changedOrder), "shippedQuantity", "4.000000");
		assertTextualDecimal(firstLine(changedOrder), "remainingQuantity", "8.000000");
		assertTextualDecimal(firstLine(changedOrder), "unitPrice", "60.000000");
		assertOrderChangeReservationRows(orderId, orderLineId);
		assertActiveSalesOrderReservation(orderId, orderLineId, fixture.warehouseId(), fixture.finishedMaterialId(),
				fixture.unitId(), "PUBLIC", null, "8.000000", "0.000000", "0.000000");
		JsonNode listedOrder = findItemById(data(get(admin, "/api/admin/sales/orders?customerId="
				+ fixture.customerId() + "&page=1&pageSize=20")), orderId);
		assertTextualDecimal(listedOrder, "totalQuantity", "12.000000");
		assertTextualDecimal(listedOrder, "shippedQuantity", "4.000000");
		assertTextualDecimal(listedOrder, "remainingQuantity", "8.000000");

		assertError(put(admin, "/api/admin/sales/orders/" + orderId + "/close",
				Map.of("version", changedOrder.get("version").longValue(), "reason", "欠量直接关闭",
						"idempotencyKey", "S25-SHORT-CLOSE-DIRECT-" + orderId)),
				HttpStatus.CONFLICT, "SALES_ORDER_SHORT_CLOSE_APPROVAL_REQUIRED");
		JsonNode shortClose = data(post(admin, "/api/admin/sales/orders/" + orderId + "/submit-short-close",
				Map.of("version", changedOrder.get("version").longValue(), "reason", "客户确认短交",
						"idempotencyKey", "S25-SHORT-CLOSE-SUBMIT-" + orderId)));
		assertThat(shortClose.get("sceneCode").asText()).isEqualTo("SALES_ORDER_SHORT_CLOSE");
		AuthenticatedSession shortApprover = createUserAndLogin("s25-short-approver-", "S25_SHORT_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "sales:order:view",
						"sales:order:short-close-approve"));
		ApprovalTaskHandle shortTask = latestApprovalTask("sales:order:short-close-approve");
		data(post(shortApprover, "/api/admin/approval-tasks/" + shortTask.taskId() + "/approve",
				Map.of("version", shortTask.version(), "comment", "同意短交", "idempotencyKey",
						"S25-SHORT-CLOSE-APPROVE-" + shortClose.get("id").longValue())));
		JsonNode closedOrder = data(get(admin, "/api/admin/sales/orders/" + orderId));
		assertThat(closedOrder.get("status").asText()).isEqualTo("CLOSED");
		assertThat(activeSalesOrderReservationCount(orderId)).isZero();
	}

	@Test
	void orderDetailAllowsDeliveryPlanMaintenanceOnlyForConfirmedAndPartiallyShippedOrders() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		JsonNode order = createApprovedQuoteOrder(admin, fixture, "8.000000", "100.000000", "0.130000");
		long orderId = order.get("id").longValue();
		JsonNode orderLine = firstLine(order);
		long orderLineId = orderLine.get("id").longValue();
		assertThat(order.get("status").asText()).isEqualTo("DRAFT");
		assertThat(order.get("allowedActions").toString()).doesNotContain("UPDATE_DELIVERY_PLAN");
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set reservation_warehouse_id = ?
				where id = ?
				""", fixture.warehouseId(), orderLineId);
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), "8.000000");

		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"S25-ORDER-DELIVERY-PLAN-ACTION-CONFIRM-" + orderId)));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(confirmed.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("UPDATE_DELIVERY_PLAN"));

		JsonNode plan = data(get(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans"))
			.get("items")
			.get(0);
		JsonNode firstShipment = data(post(admin, "/api/admin/sales/orders/" + orderId + "/shipments",
				shipmentPayload(fixture.warehouseId(),
						List.of(shipmentLine(1, orderLineId, plan.get("id").longValue(),
								fixture.finishedMaterialId(), fixture.unitId(), "3.000000")))));
		long firstShipmentId = firstShipment.get("id").longValue();
		long firstShipmentVersion = this.jdbcTemplate.queryForObject(
				"select version from sal_sales_shipment where id = ?", Long.class, firstShipmentId);
		data(put(admin, "/api/admin/sales/shipments/" + firstShipmentId + "/post",
				Map.of("version", firstShipmentVersion, "reason", "025 部分出库",
						"idempotencyKey", "S25-ORDER-DELIVERY-PLAN-ACTION-PARTIAL-" + firstShipmentId)));
		JsonNode partiallyShipped = data(get(admin, "/api/admin/sales/orders/" + orderId));
		assertThat(partiallyShipped.get("status").asText()).isEqualTo("PARTIALLY_SHIPPED");
		assertThat(partiallyShipped.get("allowedActions")).anySatisfy((action) -> assertThat(action.asText())
			.isEqualTo("UPDATE_DELIVERY_PLAN"));

		JsonNode secondShipment = data(post(admin, "/api/admin/sales/orders/" + orderId + "/shipments",
				shipmentPayload(fixture.warehouseId(),
						List.of(shipmentLine(1, orderLineId, plan.get("id").longValue(),
								fixture.finishedMaterialId(), fixture.unitId(), "5.000000")))));
		long secondShipmentId = secondShipment.get("id").longValue();
		long secondShipmentVersion = this.jdbcTemplate.queryForObject(
				"select version from sal_sales_shipment where id = ?", Long.class, secondShipmentId);
		data(put(admin, "/api/admin/sales/shipments/" + secondShipmentId + "/post",
				Map.of("version", secondShipmentVersion, "reason", "025 全部出库",
						"idempotencyKey", "S25-ORDER-DELIVERY-PLAN-ACTION-SHIPPED-" + secondShipmentId)));
		JsonNode shipped = data(get(admin, "/api/admin/sales/orders/" + orderId));
		assertThat(shipped.get("status").asText()).isEqualTo("SHIPPED");
		assertThat(shipped.get("allowedActions").toString()).doesNotContain("UPDATE_DELIVERY_PLAN");
	}

	private Map<String, Object> quotePayload(long customerId, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("quoteDate", LocalDate.now().toString());
		payload.put("validUntil", LocalDate.now().plusDays(30).toString());
		payload.put("currency", "CNY");
		payload.put("remark", "025 销售报价");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> quoteLine(int lineNo, long materialId, long unitId, String quantity,
			String taxExcludedUnitPrice, String taxRate) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("quantity", quantity);
		line.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		line.put("taxRate", taxRate);
		line.put("requiredDate", LocalDate.now().plusDays(7).toString());
		return line;
	}

	private Map<String, Object> shipmentPayload(long warehouseId, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", warehouseId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("remark", "025 销售出库");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> shipmentPayload(long warehouseId, LocalDate businessDate,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", warehouseId);
		payload.put("businessDate", businessDate.toString());
		payload.put("remark", "025 销售出库");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> shipmentLine(int lineNo, long orderLineId, long deliveryPlanId, long materialId,
			long unitId, String quantity) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("orderLineId", orderLineId);
		line.put("deliveryPlanId", deliveryPlanId);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("quantity", quantity);
		return line;
	}

	private Map<String, Object> salesOrderUpdatePayload(JsonNode order, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", order.get("customerId").longValue());
		payload.put("orderDate", order.get("orderDate").asText());
		if (order.has("expectedShipDate") && !order.get("expectedShipDate").isNull()) {
			payload.put("expectedShipDate", order.get("expectedShipDate").asText());
		}
		if (order.has("remark") && !order.get("remark").isNull()) {
			payload.put("remark", order.get("remark").asText());
		}
		if (order.has("projectId") && !order.get("projectId").isNull()) {
			payload.put("projectId", order.get("projectId").longValue());
		}
		if (order.has("contractId") && !order.get("contractId").isNull()) {
			payload.put("contractId", order.get("contractId").longValue());
		}
		payload.put("version", order.get("version").longValue());
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> salesOrderUpdateLine(JsonNode line, long reservationWarehouseId, String unitPrice) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("lineNo", line.get("lineNo").intValue());
		payload.put("materialId", line.get("materialId").longValue());
		payload.put("unitId", line.get("unitId").longValue());
		payload.put("quantity", line.get("quantity").asText());
		payload.put("unitPrice", unitPrice);
		payload.put("reservationWarehouseId", reservationWarehouseId);
		if (line.has("expectedShipDate") && !line.get("expectedShipDate").isNull()) {
			payload.put("expectedShipDate", line.get("expectedShipDate").asText());
		}
		if (line.has("remark") && !line.get("remark").isNull()) {
			payload.put("remark", line.get("remark").asText());
		}
		return payload;
	}

	private Map<String, Object> salesReturnPayload(long shipmentId, long shipmentLineId, String quantity) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceShipmentId", shipmentId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("clientRequestId", "S25-SALES-RETURN-" + shipmentId + "-" + quantity);
		payload.put("remark", "025 销售退货");
		payload.put("lines", List.of(Map.of("sourceShipmentLineId", shipmentLineId, "quantity", quantity,
				"reason", "客户退货")));
		return payload;
	}

	private Map<String, Object> actionBody(JsonNode object, String reason, String idempotencyKey) {
		return Map.of("version", object.get("version").longValue(), "reason", reason, "idempotencyKey",
				idempotencyKey);
	}

	private Map<String, Object> orderChangePayload(JsonNode order, String targetQuantity, String taxExcludedUnitPrice,
			String taxRate, LocalDate promisedDate) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("orderVersion", order.get("version").longValue());
		payload.put("reason", "调整未交付数量税价交期");
		payload.put("idempotencyKey", "S25-ORDER-CHANGE-CREATE-" + order.get("id").longValue());
		payload.put("lines", List.of(orderChangeLine(firstLine(order), targetQuantity, taxExcludedUnitPrice, taxRate,
				promisedDate)));
		return payload;
	}

	private Map<String, Object> orderChangeLine(JsonNode orderLine, String targetQuantity,
			String taxExcludedUnitPrice, String taxRate, LocalDate promisedDate) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("orderLineId", orderLine.get("id").longValue());
		line.put("targetQuantity", targetQuantity);
		line.put("taxRate", taxRate);
		line.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		line.put("promisedDate", promisedDate.toString());
		return line;
	}

	private JsonNode createApprovedQuoteOrder(AuthenticatedSession admin, SalesFixture fixture, String quantity,
			String taxExcludedUnitPrice, String taxRate) throws Exception {
		ensureCreditProfile(admin, fixture.customerId());
		return createApprovedQuoteOrderWithoutCredit(admin, fixture, quantity, taxExcludedUnitPrice, taxRate);
	}

	private JsonNode createApprovedQuoteOrderWithoutCredit(AuthenticatedSession admin, SalesFixture fixture,
			String quantity, String taxExcludedUnitPrice, String taxRate) throws Exception {
		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", quotePayload(fixture.customerId(),
				List.of(quoteLine(1, fixture.finishedMaterialId(), fixture.unitId(), quantity, taxExcludedUnitPrice,
						taxRate)))));
		data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue() + "/submit-approval",
				Map.of("version", quote.get("version").longValue(), "reason", "报价审批", "idempotencyKey",
						"S25-QUOTE-SUBMIT-" + quote.get("id").longValue())));
		AuthenticatedSession approver = createUserAndLogin("s25-quote-approver-", "S25_QUOTE_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "sales:quote:view",
						"sales:quote:approve"));
		ApprovalTaskHandle task = latestApprovalTask("sales:quote:approve");
		data(post(approver, "/api/admin/approval-tasks/" + task.taskId() + "/approve",
				Map.of("version", task.version(), "comment", "同意报价", "idempotencyKey",
						"S25-QUOTE-APPROVE-" + task.taskId())));
		JsonNode approvedQuote = data(get(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()));
		JsonNode order = data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue() + "/convert-order",
				Map.of("version", approvedQuote.get("version").longValue(), "idempotencyKey",
						"S25-QUOTE-CONVERT-ORDER-" + quote.get("id").longValue())));
		long orderId = order.get("id").longValue();
		for (JsonNode line : order.get("lines")) {
			this.jdbcTemplate.update("""
					update sal_sales_order_line
					set reservation_warehouse_id = ?
					where id = ?
					""", fixture.warehouseId(), line.get("id").longValue());
		}
		seedStock(fixture.warehouseId(), fixture.finishedMaterialId(), fixture.unitId(), quantity);
		return data(get(admin, "/api/admin/sales/orders/" + orderId));
	}

	private void ensureCreditProfile(AuthenticatedSession admin, long customerId) throws Exception {
		data(post(admin, "/api/admin/sales/credit-profiles",
				Map.of("customerId", customerId, "creditLimit", "1000000.00", "frozen", false,
						"blockOverdue", false, "remark", "025 默认信用档案")));
	}

	private void seedStock(long warehouseId, long materialId, long unitId, String quantity) {
		int updated = this.jdbcTemplate.update("""
				update inv_stock_balance
				set quantity_on_hand = quantity_on_hand + ?, updated_at = now()
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and ownership_type = 'PUBLIC'
				  and project_id is null
				  and cost_layer_id is null
				  and batch_id is null
				  and serial_id is null
				""", new BigDecimal(quantity), warehouseId, materialId);
		if (updated == 0) {
			this.jdbcTemplate.update("""
					insert into inv_stock_balance (
						warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at,
						quality_status
					)
					values (?, ?, ?, ?, 0, now(), now(), 'QUALIFIED')
					""", warehouseId, materialId, unitId, new BigDecimal(quantity));
		}
	}

	private void assertSalesOrderReservation(long orderId, long orderLineId, long warehouseId, long materialId,
			long unitId, String ownershipType, Long projectId, String quantity, String releasedQuantity,
			String consumedQuantity) {
		Map<String, Object> reservation = this.jdbcTemplate.queryForMap("""
				select warehouse_id, material_id, unit_id, ownership_type, project_id, quantity,
				       released_quantity, consumed_quantity
				from inv_stock_reservation
				where reservation_type = 'RESERVATION'
				  and source_type = 'SALES_ORDER'
				  and source_id = ?
				  and source_line_id = ?
				order by id
				limit 1
				""", orderId, orderLineId);
		assertThat(reservation.get("warehouse_id")).isEqualTo(warehouseId);
		assertThat(reservation.get("material_id")).isEqualTo(materialId);
		assertThat(reservation.get("unit_id")).isEqualTo(unitId);
		assertThat(reservation.get("ownership_type")).isEqualTo(ownershipType);
		assertThat(reservation.get("project_id")).isEqualTo(projectId);
		assertThat((BigDecimal) reservation.get("quantity")).isEqualByComparingTo(quantity);
		assertThat((BigDecimal) reservation.get("released_quantity")).isEqualByComparingTo(releasedQuantity);
		assertThat((BigDecimal) reservation.get("consumed_quantity")).isEqualByComparingTo(consumedQuantity);
	}

	private void assertActiveSalesOrderReservation(long orderId, long orderLineId, long warehouseId, long materialId,
			long unitId, String ownershipType, Long projectId, String quantity, String releasedQuantity,
			String consumedQuantity) {
		Map<String, Object> reservation = this.jdbcTemplate.queryForMap("""
				select warehouse_id, material_id, unit_id, ownership_type, project_id, quantity,
				       released_quantity, consumed_quantity
				from inv_stock_reservation
				where reservation_type = 'RESERVATION'
				  and source_type = 'SALES_ORDER'
				  and source_id = ?
				  and source_line_id = ?
				  and status = 'ACTIVE'
				  and quantity > released_quantity + consumed_quantity
				order by id desc
				limit 1
				""", orderId, orderLineId);
		assertThat(reservation.get("warehouse_id")).isEqualTo(warehouseId);
		assertThat(reservation.get("material_id")).isEqualTo(materialId);
		assertThat(reservation.get("unit_id")).isEqualTo(unitId);
		assertThat(reservation.get("ownership_type")).isEqualTo(ownershipType);
		assertThat(reservation.get("project_id")).isEqualTo(projectId);
		assertThat((BigDecimal) reservation.get("quantity")).isEqualByComparingTo(quantity);
		assertThat((BigDecimal) reservation.get("released_quantity")).isEqualByComparingTo(releasedQuantity);
		assertThat((BigDecimal) reservation.get("consumed_quantity")).isEqualByComparingTo(consumedQuantity);
	}

	private void assertOrderChangeReservationRows(long orderId, long orderLineId) {
		List<Map<String, Object>> reservations = this.jdbcTemplate.queryForList("""
				select status, quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where reservation_type = 'RESERVATION'
				  and source_type = 'SALES_ORDER'
				  and source_id = ?
				  and source_line_id = ?
				order by id
				""", orderId, orderLineId);
		assertThat(reservations).hasSize(2);
		assertThat(reservations.get(0).get("status")).isEqualTo("RELEASED");
		assertThat((BigDecimal) reservations.get(0).get("quantity")).isEqualByComparingTo("10.000000");
		assertThat((BigDecimal) reservations.get(0).get("released_quantity")).isEqualByComparingTo("6.000000");
		assertThat((BigDecimal) reservations.get(0).get("consumed_quantity")).isEqualByComparingTo("4.000000");
		assertThat(reservations.get(1).get("status")).isEqualTo("ACTIVE");
		assertThat((BigDecimal) reservations.get(1).get("quantity")).isEqualByComparingTo("8.000000");
		assertThat((BigDecimal) reservations.get(1).get("released_quantity")).isEqualByComparingTo("0.000000");
		assertThat((BigDecimal) reservations.get(1).get("consumed_quantity")).isEqualByComparingTo("0.000000");
	}

	private long activeSalesOrderReservationCount(long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_reservation
				where reservation_type = 'RESERVATION'
				  and source_type = 'SALES_ORDER'
				  and source_id = ?
				  and quantity > released_quantity + consumed_quantity
				""", Long.class, orderId);
	}

	private SalesFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("S25_UNIT_" + suffix, "025销售单位" + suffix);
		long warehouseId = insertWarehouse("S25_WH_" + suffix, "025销售仓" + suffix);
		long customerId = insertCustomer("S25_CUS_" + suffix, "025销售客户" + suffix);
		long categoryId = insertMaterialCategory("S25_CAT_" + suffix);
		long finishedMaterialId = insertMaterial("S25_FG_" + suffix, "025销售成品" + suffix, categoryId, unitId);
		return new SalesFixture(unitId, warehouseId, customerId, finishedMaterialId);
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

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at,
					updated_by, updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "025销售分类" + code);
	}

	private long insertMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, material_type, source_type, category_id, unit_id, status,
					created_by, created_at, updated_by, updated_at)
				values (?, ?, 'FINISHED_GOOD', 'SELF_MADE', ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private long insertProject(String code, long customerId, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date, status,
					target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at, activated_by,
					activated_at
				)
				values (?, ?, ?, (select id from sys_user where username = 'admin'), current_date,
					current_date + interval '60 day', ?, 100000.00, 50000.00, ?, 'test', now(), 'test', now(),
					case when ? = 'ACTIVE' then 'test' end, case when ? = 'ACTIVE' then now() end)
				returning id
				""", Long.class, code, code + "项目", customerId, status, code + "项目", status, status);
	}

	private long insertProjectSalesOrder(long projectId, long customerId, String status, boolean compatible) {
		int suffix = SEQUENCE.incrementAndGet();
		long contractId = this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (
					contract_no, external_contract_no, project_id, contract_type, name, signed_date,
					effective_start_date, effective_end_date, amount, status, remark, created_by, created_at,
					updated_by, updated_at, activated_by, activated_at
				)
				values (?, ?, ?, 'MAIN', ?, current_date, current_date, current_date + interval '60 day',
					1000.00, 'EFFECTIVE', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "S25-LEGACY-CONTRACT-" + suffix, "EXT-S25-LEGACY-" + suffix, projectId,
				"025 项目履约历史兼容合同", "025 项目履约历史兼容合同");
		return this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, project_id, contract_id, order_date, expected_ship_date, status, remark, created_by,
					created_at, updated_by, updated_at, confirmed_by, confirmed_at, currency, price_mode,
					tax_excluded_amount, tax_amount, tax_included_amount, sales_fulfillment_compatible
				)
				values (?, ?, ?, ?, current_date, current_date + interval '7 day', ?, ?, 'test', now(), 'test', now(),
					'test', now(), 'CNY', 'TAX_INCLUDED', 0, 0, 0, ?)
				returning id
				""", Long.class, "S25-SHIP-" + suffix, customerId, projectId, contractId, status,
				"025 项目履约历史兼容订单", compatible);
	}

	private void insertLegacySalesOrderLine(long orderId, long materialId, long unitId, String quantity,
			String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue).setScale(2, java.math.RoundingMode.HALF_UP);
		this.jdbcTemplate.update("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, reservation_warehouse_id, remark, price_source_type, currency, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
					tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, current_date + interval '7 day', null,
					'025 项目履约历史兼容订单行', 'LEGACY_MANUAL', 'CNY', 0, ?, ?, ?, 0, ?, now(), now())
				""", orderId, materialId, unitId, quantityValue, quantityValue, unitPriceValue,
				unitPriceValue, unitPriceValue, amount, amount);
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
				""", Long.class, rolePrefix + suffix, "025销售测试角色" + suffix, "025销售测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "025销售测试用户" + suffix);
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

	private ApprovalTaskHandle latestApprovalTask(String permissionCode) {
		return this.jdbcTemplate.query("""
				select id, version
				from platform_approval_task
				where candidate_permission_code = ?
				order by id desc
				limit 1
				""", (rs, rowNum) -> new ApprovalTaskHandle(rs.getLong("id"), rs.getLong("version")),
				permissionCode).stream().findFirst().orElseThrow();
	}

	private AuthenticatedSession login(String username, String password) {
		CsrfSession csrf = csrfSession();
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				entity(Map.of("username", username, "password", password), csrf.sessionCookie(), csrf, null),
				String.class);
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

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session.sessionCookie(), null, null), String.class);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.POST,
				entity(body, session.sessionCookie(), session.csrfSession(), null), String.class);
	}

	private ResponseEntity<String> postWithIdempotency(AuthenticatedSession session, String path, Object body,
			String idempotencyKey) {
		return this.restTemplate.exchange(path, HttpMethod.POST,
				entity(body, session.sessionCookie(), session.csrfSession(), idempotencyKey), String.class);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Object body) {
		return this.restTemplate.exchange(path, HttpMethod.PUT,
				entity(body, session.sessionCookie(), session.csrfSession(), null), String.class);
	}

	private ResponseEntity<String> uploadAttachment(AuthenticatedSession session, String objectType, long objectId,
			String filename, byte[] content) {
		LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("objectType", objectType);
		body.add("objectId", String.valueOf(objectId));
		body.add("description", "025 附件");
		body.add("file", new ByteArrayResource(content) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.add(HttpHeaders.COOKIE, session.sessionCookie());
		headers.add(session.csrfSession().headerName(), session.csrfSession().token());
		return this.restTemplate.exchange("/api/admin/attachments", HttpMethod.POST, new HttpEntity<>(body, headers),
				String.class);
	}

	private ResponseEntity<byte[]> downloadBytes(AuthenticatedSession session, String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, session.sessionCookie());
		return this.restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), byte[].class);
	}

	private HttpEntity<Object> entity(Object body, String cookie, CsrfSession csrf, String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (csrf != null) {
			headers.add(csrf.headerName(), csrf.token());
		}
		if (idempotencyKey != null) {
			headers.add("Idempotency-Key", idempotencyKey);
		}
		return new HttpEntity<>(body, headers);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode body = this.objectMapper.readTree(response.getBody());
		assertThat(body.get("code").asText()).as(response.getBody()).isEqualTo("OK");
		return body.get("data");
	}

	private JsonNode assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		JsonNode body = this.objectMapper.readTree(response.getBody());
		assertThat(body.get("code").asText()).isEqualTo(code);
		return body;
	}

	private JsonNode firstLine(JsonNode detail) {
		JsonNode lines = detail.get("lines");
		assertThat(lines).isNotNull();
		assertThat(lines.size()).isGreaterThan(0);
		return lines.get(0);
	}

	private JsonNode findItemById(JsonNode page, long id) {
		for (JsonNode item : page.get("items")) {
			if (item.get("id").longValue() == id) {
				return item;
			}
		}
		throw new AssertionError("未找到 ID=" + id + " 的分页记录：" + page);
	}

	private void assertTextualDecimal(JsonNode node, String field, String expected) {
		assertThat(node.has(field)).as("缺少十进制字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isTextual()).as(field + " 必须是 JSON 字符串，实际响应：" + node).isTrue();
		assertThat(new BigDecimal(node.get(field).asText()).compareTo(new BigDecimal(expected))).isZero();
	}

	private void assertTextualDecimal(JsonNode node, String field) {
		assertThat(node.has(field)).as("缺少十进制字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isTextual()).as(field + " 必须是 JSON 字符串，实际响应：" + node).isTrue();
		assertThat(new BigDecimal(node.get(field).asText())).isNotNull();
	}

	private void assertAttachmentObjectAccepted(String objectType, long objectId) {
		int suffix = SEQUENCE.incrementAndGet();
		long fileId = this.jdbcTemplate.queryForObject("""
				insert into platform_file_object (
					bucket, object_key, original_filename, content_type, size_bytes, sha256, file_usage, status,
					created_by_user_id, created_by_username
				)
				values ('qherp-test-private-stage025-sales', ?, ?, 'text/plain', 1, ?, 'ATTACHMENT', 'AVAILABLE',
					(select id from sys_user where username = 'admin'), 'admin')
				returning id
				""", Long.class, "stage025/" + objectType + "/" + objectId + "/" + suffix + ".txt",
				objectType + "-" + objectId + ".txt", "0".repeat(64));
		long attachmentId = this.jdbcTemplate.queryForObject("""
				insert into platform_business_attachment (
					object_type, object_id, file_id, description, status, created_by_user_id, created_by_username
				)
				values (?, ?, ?, '025 附件对象约束回归', 'AVAILABLE',
					(select id from sys_user where username = 'admin'), 'admin')
				returning id
				""", Long.class, objectType, objectId, fileId);
		assertThat(attachmentId).isPositive();
	}

	private void assertAttachmentRoundTrip(AuthenticatedSession admin, String objectType, long objectId)
			throws Exception {
		String contentText = "025 " + objectType + " attachment " + objectId + " " + SEQUENCE.incrementAndGet();
		byte[] content = contentText.getBytes(StandardCharsets.UTF_8);
		JsonNode uploaded = data(uploadAttachment(admin, objectType, objectId, objectType.toLowerCase() + ".txt",
				content));
		assertThat(uploaded.get("objectType").asText()).isEqualTo(objectType);
		assertThat(uploaded.toString()).doesNotContain("objectKey", "storageKey");
		JsonNode listed = data(get(admin, "/api/admin/attachments?objectType=" + objectType + "&objectId="
				+ objectId));
		JsonNode listedItem = findItemById(listed, uploaded.get("id").longValue());
		assertThat(listedItem.get("availableActions").toString()).contains("DOWNLOAD", "DELETE");
		assertThat(listedItem.toString()).doesNotContain("objectKey", "storageKey");
		ResponseEntity<byte[]> downloaded = downloadBytes(admin, "/api/admin/attachments/"
				+ uploaded.get("id").longValue() + "/download");
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(downloaded.getBody()).isEqualTo(content);
		JsonNode deleted = data(put(admin, "/api/admin/attachments/" + uploaded.get("id").longValue()
				+ "/delete", Map.of("version", uploaded.get("version").longValue(), "reason", "025 附件删除")));
		assertThat(deleted.get("status").asText()).isEqualTo("DELETED");
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

	private record SalesFixture(long unitId, long warehouseId, long customerId, long finishedMaterialId) {
	}

	private record ApprovalTaskHandle(long taskId, long version) {
	}

}
