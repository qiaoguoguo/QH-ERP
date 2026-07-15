package com.qherp.api.system.procurement;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.platform.PlatformDocumentTaskWorker;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=procurement-stage024-backend",
				"qherp.platform.task.worker.enabled=false"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProcurementStage024BackendFlowTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Container
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
		registry.add("qherp.storage.s3.bucket", () -> "qherp-test-private-stage024-procurement");
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
	private PlatformDocumentTaskWorker documentTaskWorker;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void approvedProjectRequisitionCreatesProjectOrderAndReceiptCostLayer() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();

		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(), "5.000000")));
		assertThat(requisition.get("status").asText()).isEqualTo("DRAFT");
		long requisitionId = requisition.get("id").longValue();
		long requisitionLineId = requisition.get("lines").get(0).get("id").longValue();

		JsonNode submitted = data(post(admin,
				"/api/admin/procurement/requisitions/" + requisitionId + "/submit-approval",
				Map.of("version", requisition.get("version").longValue(), "reason", "项目专采请购审批",
						"idempotencyKey", "req-submit-" + SEQUENCE.incrementAndGet())));
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");

		JsonNode approved = data(approveLatestTask("procurement:requisition:approve"));
		assertThat(approved.get("sceneCode").asText()).isEqualTo("PROCUREMENT_REQUISITION_APPROVAL");
		assertThat(status("proc_purchase_requisition", requisitionId)).isEqualTo("APPROVED");

		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisitionLineId, fixture.materialId(),
						fixture.unitId(), "5.000000", "12.500000")));
		assertThat(order.get("purchaseMode").asText()).isEqualTo("PROJECT");
		assertThat(order.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(order.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(order.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(order.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(order.get("projectName").asText()).startsWith("024项目");
		long orderId = order.get("id").longValue();
		long orderLineId = order.get("lines").get(0).get("id").longValue();
		assertThat(order.get("lines").get(0).get("requisitionLineId").longValue()).isEqualTo(requisitionLineId);
		assertThat(order.get("lines").get(0).get("sourceRequisitionLineId").longValue()).isEqualTo(requisitionLineId);

		JsonNode confirmed = data(put(admin, "/api/admin/procurement/orders/" + orderId + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"s24-confirm-" + SEQUENCE.incrementAndGet())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

		JsonNode receipt = data(post(admin, "/api/admin/procurement/orders/" + orderId + "/receipts",
				receiptPayload(fixture.warehouseId(), orderLineId, fixture.materialId(), fixture.unitId(), "5.000000")));
		long receiptId = receipt.get("id").longValue();
		long receiptLineId = receipt.get("lines").get(0).get("id").longValue();
		assertThat(receipt.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(receipt.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(receipt.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(receipt.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(receipt.get("projectName").asText()).startsWith("024项目");
		assertThat(receipt.get("costVisible").asBoolean()).isTrue();
		assertThat(receipt.get("allowedActions").toString()).contains("POST");
		assertThat(receipt.get("totalQuantity").isTextual()).isTrue();
		assertThat(receipt.get("lines").get(0).get("quantity").isTextual()).isTrue();
		assertThat(receipt.get("lines").get(0).get("taxExcludedUnitPrice").isTextual()).isTrue();
		assertThat(receipt.get("lines").get(0).get("scheduleId").isNumber()).isTrue();
		assertThat(receipt.get("lines").get(0).get("scheduleSeq").asInt()).isEqualTo(1);
		assertThat(receipt.get("lines").get(0).get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(receipt.get("lines").get(0).get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(receipt.get("lines").get(0).get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(receipt.get("lines").get(0).get("projectCode").asText()).startsWith("S24-P-");
		assertThat(receipt.get("lines").get(0).get("projectName").asText()).startsWith("024项目");
		assertThat(receipt.get("lines").get(0).get("valuationState").asText()).isEqualTo("NOT_POSTED");
		assertThat(receipt.get("lines").get(0).get("valuationStateName").asText()).isEqualTo("未过账");

		JsonNode posted = data(put(admin, "/api/admin/procurement/receipts/" + receiptId + "/post",
				Map.of("version", receipt.get("version").longValue(), "idempotencyKey",
						"s24-receipt-post-" + SEQUENCE.incrementAndGet())));
		assertThat(posted.get("status").asText()).isEqualTo("POSTED");
		assertThat(posted.get("allowedActions").toString()).doesNotContain("POST");
		assertThat(posted.get("valuationState").asText()).isEqualTo("VALUED");
		assertThat(posted.get("valuationStateName").asText()).isEqualTo("已估值");
		assertThat(posted.get("taxExcludedAmount").isTextual()).isTrue();
		assertThat(posted.get("lines").get(0).get("costVisible").asBoolean()).isTrue();
		assertThat(posted.get("lines").get(0).get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(posted.get("lines").get(0).get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(posted.get("lines").get(0).get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(posted.get("lines").get(0).get("projectCode").asText()).startsWith("S24-P-");
		assertThat(posted.get("lines").get(0).get("projectName").asText()).startsWith("024项目");
		assertThat(posted.get("lines").get(0).get("valuationState").asText()).isEqualTo("VALUED");
		assertThat(posted.get("lines").get(0).get("valuationStateName").asText()).isEqualTo("已估值");
		assertThat(posted.get("lines").get(0).get("costLayerNo").asText()).isNotBlank();
		assertThat(posted.get("lines").get(0).get("valueMovementNo").asText()).isNotBlank();
		assertThat(posted.get("inventoryMovements").get(0).get("quantity").isTextual()).isTrue();

		AuthenticatedSession receiptViewer = createUserAndLogin("s24-receipt-viewer-", "S24_RECEIPT_VIEW_",
				List.of("procurement:receipt:view"));
		JsonNode restrictedReceipt = data(get(receiptViewer, "/api/admin/procurement/receipts/" + receiptId));
		assertThat(restrictedReceipt.get("costVisible").asBoolean()).isFalse();
		assertThat(restrictedReceipt.get("taxExcludedAmount").isNull()).isTrue();
		assertThat(restrictedReceipt.get("lines").get(0).get("costVisible").asBoolean()).isFalse();
		assertThat(restrictedReceipt.get("lines").get(0).get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(restrictedReceipt.get("lines").get(0).get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(restrictedReceipt.get("lines").get(0).get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(restrictedReceipt.get("lines").get(0).get("projectCode").asText()).startsWith("S24-P-");
		assertThat(restrictedReceipt.get("lines").get(0).get("projectName").asText()).startsWith("024项目");
		assertThat(restrictedReceipt.get("lines").get(0).get("valuationState").asText()).isEqualTo("VALUED");
		assertThat(restrictedReceipt.get("lines").get(0).get("valuationStateName").asText()).isEqualTo("已估值");
		assertThat(restrictedReceipt.get("lines").get(0).get("taxExcludedUnitPrice").isNull()).isTrue();
		assertThat(restrictedReceipt.get("lines").get(0).get("taxExcludedAmount").isNull()).isTrue();
		assertThat(restrictedReceipt.get("lines").get(0).get("costLayerNo").isNull()).isTrue();
		assertThat(restrictedReceipt.get("lines").get(0).get("valueMovementNo").isNull()).isTrue();
		for (JsonNode movementNode : restrictedReceipt.get("inventoryMovements")) {
			assertMissingOrNull(movementNode, "id");
			assertMissingOrNull(movementNode, "movementNo");
			assertThat(movementNode.get("quantity").isTextual()).isTrue();
		}
		JsonNode restrictedReceiptPage = data(get(receiptViewer,
				"/api/admin/procurement/receipts?orderId=" + orderId + "&page=1&pageSize=10"));
		JsonNode restrictedReceiptSummary = findPageItem(restrictedReceiptPage, "id", receiptId);
		assertThat(restrictedReceiptSummary.get("costVisible").asBoolean()).isFalse();
		assertThat(restrictedReceiptSummary.get("taxExcludedAmount").isNull()).isTrue();

		assertThat(projectCostLayerCount(fixture.projectId(), fixture.materialId(), receiptId, receiptLineId)).isOne();
		ValueMovement movement = valueMovement("PURCHASE_RECEIPT", receiptId, receiptLineId);
		assertThat(movement.ownershipType()).isEqualTo("PROJECT");
		assertThat(movement.projectId()).isEqualTo(fixture.projectId());
		assertDecimal(movement.quantity(), "5.000000");
		assertDecimal(movement.inventoryAmount(), "62.50");
	}

	@Test
	void businessActionIdempotencyReturnsExistingResultBeforeVersionCheck() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();

		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(), "3.000000")));
		long requisitionId = requisition.get("id").longValue();
		long requisitionLineId = requisition.get("lines").get(0).get("id").longValue();
		data(post(admin, "/api/admin/procurement/requisitions/" + requisitionId + "/submit-approval",
				Map.of("version", requisition.get("version").longValue(), "reason", "项目专采请购审批",
						"idempotencyKey", "req-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:requisition:approve"));

		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisitionLineId, fixture.materialId(),
						fixture.unitId(), "3.000000", "10.000000")));
		assertError(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue() + "/confirm",
				Map.of("version", order.get("version").longValue() + 1, "idempotencyKey",
						"s24-stale-confirm-" + SEQUENCE.incrementAndGet())),
				HttpStatus.CONFLICT, "VERSION_CONFLICT");
		JsonNode confirmed = data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()
				+ "/confirm", Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"s24-confirm-" + SEQUENCE.incrementAndGet())));
		Map<String, Object> immutableProjectUpdate = new LinkedHashMap<>(projectOrderPayload(fixture.supplierId(),
				fixture.projectId(), requisitionLineId, fixture.materialId(), fixture.unitId(), "3.000000", "10.000000"));
		immutableProjectUpdate.put("version", confirmed.get("version").longValue());
		immutableProjectUpdate.put("purchaseMode", "PUBLIC");
		immutableProjectUpdate.put("projectId", null);
		assertError(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue(), immutableProjectUpdate),
				HttpStatus.CONFLICT, "PROCUREMENT_ORDER_PROJECT_IMMUTABLE");

		long orderLineId = confirmed.get("lines").get(0).get("id").longValue();
		JsonNode receipt = data(post(admin, "/api/admin/procurement/orders/" + confirmed.get("id").longValue()
				+ "/receipts", receiptPayload(fixture.warehouseId(), orderLineId, fixture.materialId(),
						fixture.unitId(), "3.000000")));
		Map<String, Object> postBody = Map.of("version", receipt.get("version").longValue(), "reason", "首次过账",
				"idempotencyKey", "s24-receipt-post-" + receipt.get("id").longValue());
		JsonNode firstPost = data(put(admin, "/api/admin/procurement/receipts/" + receipt.get("id").longValue()
				+ "/post", postBody));
		JsonNode retryPost = data(put(admin, "/api/admin/procurement/receipts/" + receipt.get("id").longValue()
				+ "/post", postBody));

		assertThat(retryPost.get("id").longValue()).isEqualTo(firstPost.get("id").longValue());
		assertThat(countRows("inv_stock_movement", "source_type = 'PURCHASE_RECEIPT' and source_id = ?",
				firstPost.get("id").longValue())).isOne();
		assertThat(countRows("inv_value_movement", "source_type = 'PURCHASE_RECEIPT' and source_id = ?",
				firstPost.get("id").longValue())).isOne();
		assertThat(countRows("inv_project_cost_layer",
				"project_id = ? and material_id = ? and source_type = 'PURCHASE_RECEIPT' and source_id = ? and source_line_id = ?",
				fixture.projectId(), fixture.materialId(), firstPost.get("id").longValue(),
				firstPost.get("lines").get(0).get("id").longValue())).isOne();
	}

	@Test
	void projectPurchaseReturnReversesOriginalProjectCostLayerIdempotently() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(), "5.000000")));
		long requisitionId = requisition.get("id").longValue();
		long requisitionLineId = requisition.get("lines").get(0).get("id").longValue();
		data(post(admin, "/api/admin/procurement/requisitions/" + requisitionId + "/submit-approval",
				Map.of("version", requisition.get("version").longValue(), "reason", "项目专采请购审批",
						"idempotencyKey", "req-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:requisition:approve"));

		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisitionLineId, fixture.materialId(),
						fixture.unitId(), "5.000000", "12.500000")));
		JsonNode confirmed = data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()
				+ "/confirm", Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"s24-confirm-" + SEQUENCE.incrementAndGet())));
		JsonNode receipt = data(post(admin, "/api/admin/procurement/orders/" + confirmed.get("id").longValue()
				+ "/receipts", receiptPayload(fixture.warehouseId(), confirmed.get("lines").get(0).get("id")
					.longValue(), fixture.materialId(), fixture.unitId(), "5.000000")));
		JsonNode postedReceipt = data(put(admin, "/api/admin/procurement/receipts/" + receipt.get("id").longValue()
				+ "/post", Map.of("version", receipt.get("version").longValue(), "reason", "项目入库",
						"idempotencyKey", "s24-receipt-post-" + SEQUENCE.incrementAndGet())));
		long receiptLineId = postedReceipt.get("lines").get(0).get("id").longValue();
		assertDecimal(projectLayerRemainingAmount(receiptLineId), "62.50");
		JsonNode returnSources = data(get(admin, "/api/admin/procurement/return-sources?supplierId="
				+ fixture.supplierId() + "&warehouseId=" + fixture.warehouseId() + "&page=1&pageSize=10"));
		JsonNode returnSource = findPageItem(returnSources, "receiptId", postedReceipt.get("id").longValue());
		assertThat(returnSource.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(returnSource.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(returnSource.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(returnSource.get("originalCostLayerNo").asText()).isNotBlank();
		assertThat(returnSource.get("originalValueMovementNo").asText()).isNotBlank();
		assertThat(returnSource.get("lines").get(0).get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(returnSource.get("lines").get(0).get("originalCostLayerNo").asText()).isNotBlank();
		assertThat(returnSource.get("lines").get(0).get("originalValueMovementNo").asText()).isNotBlank();

		Map<String, Object> purchaseReturnPayload = purchaseReturnPayload(postedReceipt.get("id").longValue(),
				receiptLineId, "2.000000", "s24-return-" + SEQUENCE.incrementAndGet());
		JsonNode purchaseReturn = data(post(admin, "/api/admin/procurement/returns", purchaseReturnPayload));
		JsonNode duplicatePurchaseReturn = data(post(admin, "/api/admin/procurement/returns", purchaseReturnPayload));
		assertThat(duplicatePurchaseReturn.get("id").longValue()).isEqualTo(purchaseReturn.get("id").longValue());
		assertThat(purchaseReturn.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(purchaseReturn.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(purchaseReturn.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(purchaseReturn.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(purchaseReturn.get("originalCostLayerNo").asText()).isNotBlank();
		assertThat(purchaseReturn.get("originalValueMovementNo").asText()).isNotBlank();
		assertThat(purchaseReturn.get("source").get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(purchaseReturn.get("source").get("originalCostLayerNo").asText()).isNotBlank();
		assertThat(purchaseReturn.get("lines").get(0).get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(purchaseReturn.get("lines").get(0).get("originalCostLayerNo").asText()).isNotBlank();
		assertThat(purchaseReturn.get("lines").get(0).get("originalValueMovementNo").asText()).isNotBlank();
		Map<String, Object> actionBody = Map.of("version", purchaseReturn.get("version").longValue(),
				"reason", "项目退货", "idempotencyKey", "s24-return-post-" + purchaseReturn.get("id").longValue());

		JsonNode postedReturn = data(put(admin, "/api/admin/procurement/returns/" + purchaseReturn.get("id")
			.longValue() + "/post", actionBody));
		JsonNode retryReturn = data(put(admin, "/api/admin/procurement/returns/" + purchaseReturn.get("id")
			.longValue() + "/post", actionBody));

		assertThat(retryReturn.get("id").longValue()).isEqualTo(postedReturn.get("id").longValue());
		assertThat(postedReturn.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(postedReturn.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(postedReturn.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(postedReturn.get("originalCostLayerNo").asText()).isNotBlank();
		assertThat(postedReturn.get("originalValueMovementNo").asText()).isNotBlank();
		assertDecimal(projectLayerRemainingAmount(receiptLineId), "37.50");
		assertThat(countRows("inv_stock_movement", "source_type = 'PURCHASE_RETURN' and source_id = ?",
				postedReturn.get("id").longValue())).isOne();
		assertThat(countRows("inv_value_movement", "source_type = 'PURCHASE_RETURN' and source_id = ?",
				postedReturn.get("id").longValue())).isOne();

		AuthenticatedSession returnViewer = createUserAndLogin("s24-return-viewer-", "S24_RETURN_VIEW_",
				List.of("procurement:receipt:view", "procurement:return:view", "procurement:return:create",
						"business:reversal:view"));
		JsonNode restrictedSources = data(get(returnViewer, "/api/admin/procurement/return-sources?supplierId="
				+ fixture.supplierId() + "&warehouseId=" + fixture.warehouseId() + "&page=1&pageSize=10"));
		JsonNode restrictedSource = findPageItem(restrictedSources, "receiptId", postedReceipt.get("id").longValue());
		assertThat(restrictedSource.get("costVisible").asBoolean()).isFalse();
		assertMissingOrNull(restrictedSource, "originalCostLayerNo");
		assertMissingOrNull(restrictedSource, "originalValueMovementNo");
		assertThat(restrictedSource.get("lines").get(0).get("costVisible").asBoolean()).isFalse();
		assertMissingOrNull(restrictedSource.get("lines").get(0), "unitPrice");
		assertMissingOrNull(restrictedSource.get("lines").get(0), "returnableAmount");
		assertMissingOrNull(restrictedSource.get("lines").get(0), "originalCostLayerNo");
		assertMissingOrNull(restrictedSource.get("lines").get(0), "originalValueMovementNo");

		JsonNode restrictedReturns = data(get(returnViewer,
				"/api/admin/procurement/returns?supplierId=" + fixture.supplierId() + "&page=1&pageSize=10"));
		JsonNode restrictedReturnSummary = findPageItem(restrictedReturns, "id", postedReturn.get("id").longValue());
		assertThat(restrictedReturnSummary.get("costVisible").asBoolean()).isFalse();
		assertMissingOrNull(restrictedReturnSummary, "totalAmount");
		assertMissingOrNull(restrictedReturnSummary, "originalCostLayerNo");
		assertMissingOrNull(restrictedReturnSummary, "originalValueMovementNo");
		assertMissingOrNull(restrictedReturnSummary.get("source"), "amount");
		assertMissingOrNull(restrictedReturnSummary.get("source"), "originalCostLayerNo");
		assertMissingOrNull(restrictedReturnSummary.get("source"), "originalValueMovementNo");

		JsonNode restrictedReturn = data(get(returnViewer, "/api/admin/procurement/returns/"
				+ postedReturn.get("id").longValue()));
		assertThat(restrictedReturn.get("costVisible").asBoolean()).isFalse();
		assertMissingOrNull(restrictedReturn, "totalAmount");
		assertMissingOrNull(restrictedReturn, "originalCostLayerNo");
		assertMissingOrNull(restrictedReturn, "originalValueMovementNo");
		assertMissingOrNull(restrictedReturn.get("source"), "amount");
		assertMissingOrNull(restrictedReturn.get("source"), "originalCostLayerNo");
		assertMissingOrNull(restrictedReturn.get("source"), "originalValueMovementNo");
		assertMissingOrNull(restrictedReturn.get("lines").get(0), "unitPrice");
		assertMissingOrNull(restrictedReturn.get("lines").get(0), "amount");
		assertMissingOrNull(restrictedReturn.get("lines").get(0).get("source"), "amount");
		assertMissingOrNull(restrictedReturn.get("lines").get(0), "originalCostLayerNo");
		assertMissingOrNull(restrictedReturn.get("lines").get(0), "originalValueMovementNo");
		assertMissingOrNull(restrictedReturn.get("lines").get(0), "stockMovementId");
		JsonNode restrictedTrace = restrictedReturn.get("traces").get(0);
		assertMissingOrNull(restrictedTrace, "inventoryMovementId");
		assertMissingOrNull(restrictedTrace, "amount");
		assertMissingOrNull(restrictedTrace.get("source"), "amount");
		assertMissingOrNull(restrictedTrace.get("reverse"), "amount");
		assertThat(restrictedTrace.get("direction").asText()).isEqualTo("SOURCE_TO_REVERSE");
		assertThat(restrictedTrace.get("effectType").asText()).isEqualTo("PURCHASE_RETURN_OUTBOUND");
		assertThat(restrictedTrace.get("resourceType").asText()).isEqualTo("PURCHASE_RETURN_LINE");
		assertThat(restrictedTrace.get("warehouseId").longValue()).isEqualTo(fixture.warehouseId());
		assertThat(restrictedTrace.hasNonNull("warehouseName")).isTrue();
		assertThat(restrictedTrace.get("materialId").longValue()).isEqualTo(fixture.materialId());
		assertThat(restrictedTrace.hasNonNull("materialCode")).isTrue();
		assertThat(restrictedTrace.hasNonNull("materialName")).isTrue();
		assertThat(restrictedTrace.get("source").hasNonNull("sourceNo")).isTrue();
		assertThat(restrictedTrace.get("reverse").hasNonNull("sourceNo")).isTrue();
		assertThat(restrictedTrace.get("quantity").isTextual()).isTrue();
	}

	@Test
	void projectOrderCreateAndUpdateRejectInactiveProjectAtActionTime() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(), "4.000000")));
		long requisitionId = requisition.get("id").longValue();
		long requisitionLineId = requisition.get("lines").get(0).get("id").longValue();
		data(post(admin, "/api/admin/procurement/requisitions/" + requisitionId + "/submit-approval",
				Map.of("version", requisition.get("version").longValue(), "reason", "项目专采请购审批",
						"idempotencyKey", "req-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:requisition:approve"));

		this.jdbcTemplate.update("update sal_project set status = 'CLOSED' where id = ?", fixture.projectId());
		assertError(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisitionLineId,
						fixture.materialId(), fixture.unitId(), "1.000000", "10.000000")),
				HttpStatus.CONFLICT, "PROCUREMENT_PROJECT_STATUS_INVALID");

		this.jdbcTemplate.update("update sal_project set status = 'ACTIVE' where id = ?", fixture.projectId());
		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisitionLineId,
						fixture.materialId(), fixture.unitId(), "1.000000", "10.000000")));
		Map<String, Object> updatePayload = new LinkedHashMap<>(projectOrderPayload(fixture.supplierId(),
				fixture.projectId(), requisitionLineId, fixture.materialId(), fixture.unitId(), "1.000000",
				"10.000000"));
		updatePayload.put("version", order.get("version").longValue());
		this.jdbcTemplate.update("update sal_project set status = 'CLOSED' where id = ?", fixture.projectId());
		assertError(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue(), updatePayload),
				HttpStatus.CONFLICT, "PROCUREMENT_PROJECT_STATUS_INVALID");
	}

	@Test
	void orderHeaderPriceSourceIsMixedWhenLinesUseDifferentSources() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		JsonNode firstRequisition = approvedRequisition(admin, fixture, "2.000000");
		JsonNode secondRequisition = approvedRequisition(admin, fixture, "1.000000");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("supplierId", fixture.supplierId());
		body.put("orderDate", LocalDate.now().toString());
		body.put("purchaseMode", "PROJECT");
		body.put("projectId", fixture.projectId());
		body.put("currency", "CNY");
		body.put("remark", "024 混合价格来源聚合");
		body.put("lines", List.of(
				orderLine(1, fixture.materialId(), fixture.unitId(), firstRequisition.get("lineId").longValue(),
						"2.000000", "10.000000", "REQUISITION_APPROVED"),
				orderLine(2, fixture.materialId(), fixture.unitId(), secondRequisition.get("lineId").longValue(),
						"1.000000", "11.000000", "QUOTE_SELECTION")));

		JsonNode order = data(post(admin, "/api/admin/procurement/orders", body));
		assertThat(order.get("priceSourceType").asText()).isEqualTo("MIXED");
		assertThat(order.get("lines").get(0).get("priceSourceType").asText()).isEqualTo("REQUISITION_APPROVED");
		assertThat(order.get("lines").get(1).get("priceSourceType").asText()).isEqualTo("QUOTE_SELECTION");
	}

	@Test
	void approvalAndProcurementActionsAreIdempotentBeforeVersionAndStateChecks() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();

		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				frontendRequisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(),
						"4.000000", "024 幂等请购")));
		Map<String, Object> submitRequest = Map.of("version", requisition.get("version").longValue(), "reason",
				"提交请购审批", "idempotencyKey", "s24-req-submit-idem-" + SEQUENCE.incrementAndGet());
		JsonNode firstSubmit = data(post(admin, "/api/admin/procurement/requisitions/"
				+ requisition.get("id").longValue() + "/submit-approval", submitRequest));
		JsonNode retrySubmit = data(post(admin, "/api/admin/procurement/requisitions/"
				+ requisition.get("id").longValue() + "/submit-approval", submitRequest));
		assertThat(retrySubmit.get("id").longValue()).isEqualTo(firstSubmit.get("id").longValue());
		JsonNode submittedRequisition = data(get(admin, "/api/admin/procurement/requisitions/"
				+ requisition.get("id").longValue()));
		assertThat(submittedRequisition.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(submittedRequisition.get("version").longValue())
			.isGreaterThan(requisition.get("version").longValue());
		assertError(post(admin, "/api/admin/procurement/requisitions/" + requisition.get("id").longValue()
				+ "/submit-approval", Map.of("version", requisition.get("version").longValue(), "reason",
						"另一提交", "idempotencyKey", "s24-req-submit-stale-" + SEQUENCE.incrementAndGet())),
				HttpStatus.CONFLICT, "VERSION_CONFLICT");
		data(approveLatestTask("procurement:requisition:approve"));
		JsonNode approvedRequisition = data(get(admin, "/api/admin/procurement/requisitions/"
				+ requisition.get("id").longValue()));
		long sourceRequisitionLineId = approvedRequisition.get("lines").get(0).get("id").longValue();

		JsonNode cancelRequisition = data(post(admin, "/api/admin/procurement/requisitions",
				frontendRequisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(),
						"1.000000", "024 取消幂等请购")));
		Map<String, Object> cancelRequisitionRequest = Map.of("version", cancelRequisition.get("version").longValue(),
				"idempotencyKey", "s24-req-cancel-idem-" + SEQUENCE.incrementAndGet());
		JsonNode cancelledRequisition = data(put(admin, "/api/admin/procurement/requisitions/"
				+ cancelRequisition.get("id").longValue() + "/cancel", cancelRequisitionRequest));
		JsonNode retryCancelledRequisition = data(put(admin, "/api/admin/procurement/requisitions/"
				+ cancelRequisition.get("id").longValue() + "/cancel", cancelRequisitionRequest));
		assertThat(retryCancelledRequisition.get("version").longValue())
			.isEqualTo(cancelledRequisition.get("version").longValue());
		assertError(put(admin, "/api/admin/procurement/requisitions/" + cancelRequisition.get("id").longValue()
				+ "/cancel", Map.of("version", cancelRequisition.get("version").longValue(), "idempotencyKey",
						"s24-req-cancel-stale-" + SEQUENCE.incrementAndGet())), HttpStatus.CONFLICT,
				"VERSION_CONFLICT");

		JsonNode closeRequisition = approvedRequisitionDetail(admin, fixture, "1.000000");
		Map<String, Object> closeRequisitionRequest = Map.of("version", closeRequisition.get("version").longValue(),
				"reason", "幂等关闭", "idempotencyKey", "s24-req-close-idem-" + SEQUENCE.incrementAndGet());
		JsonNode closedRequisition = data(put(admin, "/api/admin/procurement/requisitions/"
				+ closeRequisition.get("id").longValue() + "/close", closeRequisitionRequest));
		JsonNode retryClosedRequisition = data(put(admin, "/api/admin/procurement/requisitions/"
				+ closeRequisition.get("id").longValue() + "/close", closeRequisitionRequest));
		assertThat(retryClosedRequisition.get("version").longValue())
			.isEqualTo(closedRequisition.get("version").longValue());
		assertError(put(admin, "/api/admin/procurement/requisitions/" + closeRequisition.get("id").longValue()
				+ "/close", Map.of("version", closeRequisition.get("version").longValue(), "reason",
						"陈旧关闭", "idempotencyKey", "s24-req-close-stale-" + SEQUENCE.incrementAndGet())),
				HttpStatus.CONFLICT, "VERSION_CONFLICT");

		JsonNode inquiry = data(post(admin, "/api/admin/procurement/inquiries",
				frontendInquiryPayload("PROJECT", fixture.projectId(), sourceRequisitionLineId, fixture.materialId(),
						fixture.unitId(), fixture.supplierId(), "3.000000", "024 幂等询价")));
		Map<String, Object> releaseRequest = Map.of("version", inquiry.get("version").longValue(), "idempotencyKey",
				"s24-inquiry-release-idem-" + SEQUENCE.incrementAndGet());
		JsonNode releasedInquiry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ inquiry.get("id").longValue() + "/release", releaseRequest));
		JsonNode retryReleasedInquiry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ inquiry.get("id").longValue() + "/release", releaseRequest));
		assertThat(retryReleasedInquiry.get("version").longValue())
			.isEqualTo(releasedInquiry.get("version").longValue());
		assertError(put(admin, "/api/admin/procurement/inquiries/" + inquiry.get("id").longValue()
				+ "/release", Map.of("version", inquiry.get("version").longValue(), "idempotencyKey",
						"s24-inquiry-release-stale-" + SEQUENCE.incrementAndGet())), HttpStatus.CONFLICT,
				"VERSION_CONFLICT");

		JsonNode quoteToCancel = data(post(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/quotes",
				frontendQuotePayload(fixture.supplierId(), fixture.materialId(), "3.000000", "12.000000",
						"13.560000")));
		Map<String, Object> cancelQuoteRequest = Map.of("version", quoteToCancel.get("version").longValue(),
				"idempotencyKey", "s24-quote-cancel-idem-" + SEQUENCE.incrementAndGet());
		JsonNode cancelledQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/quotes/" + quoteToCancel.get("id").longValue()
				+ "/cancel", cancelQuoteRequest));
		JsonNode retryCancelledQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/quotes/" + quoteToCancel.get("id").longValue()
				+ "/cancel", cancelQuoteRequest));
		assertThat(retryCancelledQuote.get("version").longValue()).isEqualTo(cancelledQuote.get("version").longValue());

		JsonNode lowQuote = data(post(admin, "/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue()
				+ "/quotes", frontendQuotePayload(fixture.supplierId(), fixture.materialId(), "3.000000",
						"9.000000", "10.170000")));
		JsonNode highQuote = data(post(admin, "/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue()
				+ "/quotes", frontendQuotePayload(fixture.supplierId(), fixture.materialId(), "3.000000",
						"11.000000", "12.430000")));
		Map<String, Object> completeRequest = Map.of("version", releasedInquiry.get("version").longValue(),
				"idempotencyKey", "s24-inquiry-complete-idem-" + SEQUENCE.incrementAndGet());
		JsonNode completedInquiry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/complete", completeRequest));
		JsonNode retryCompletedInquiry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/complete", completeRequest));
		assertThat(retryCompletedInquiry.get("version").longValue())
			.isEqualTo(completedInquiry.get("version").longValue());

		Map<String, Object> selectRequest = Map.of("version", lowQuote.get("version").longValue(), "reason",
				"选择最低报价", "idempotencyKey", "s24-quote-select-idem-" + SEQUENCE.incrementAndGet());
		JsonNode selectedQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ completedInquiry.get("id").longValue() + "/quotes/" + lowQuote.get("id").longValue()
				+ "/select", selectRequest));
		JsonNode retrySelectedQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ completedInquiry.get("id").longValue() + "/quotes/" + lowQuote.get("id").longValue()
				+ "/select", selectRequest));
		assertThat(retrySelectedQuote.get("version").longValue()).isEqualTo(selectedQuote.get("version").longValue());
		JsonNode rejectedQuote = data(get(admin, "/api/admin/procurement/inquiries/"
				+ completedInquiry.get("id").longValue() + "/quotes/" + highQuote.get("id").longValue()));
		assertThat(selectedQuote.get("version").longValue()).isGreaterThan(lowQuote.get("version").longValue());
		assertThat(rejectedQuote.get("version").longValue()).isGreaterThan(highQuote.get("version").longValue());
		assertError(put(admin, "/api/admin/procurement/inquiries/" + completedInquiry.get("id").longValue()
				+ "/quotes/" + lowQuote.get("id").longValue() + "/select",
				Map.of("version", lowQuote.get("version").longValue(), "reason", "陈旧选择", "idempotencyKey",
						"s24-quote-select-stale-" + SEQUENCE.incrementAndGet())), HttpStatus.CONFLICT,
				"VERSION_CONFLICT");
	}

	@Test
	void activeAgreementPriorityRequiresExceptionApprovalAndOverlapBlocksActivation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();

		JsonNode activeAgreement = activatePriceAgreement(admin, fixture, "8.000000", "9.040000");
		assertThat(activeAgreement.get("status").asText()).isEqualTo("ACTIVE");

		JsonNode requisition = approvedRequisitionDetail(admin, fixture, "2.000000");
		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisition.get("lines").get(0)
					.get("id").longValue(), fixture.materialId(), fixture.unitId(), "2.000000", "10.000000")));
		assertError(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue() + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"s24-confirm-agreement-deviation-" + SEQUENCE.incrementAndGet())), HttpStatus.CONFLICT,
				"PROCUREMENT_ORDER_AGREEMENT_DEVIATION");
		Map<String, Object> exceptionSubmit = Map.of("version", order.get("version").longValue(), "reason",
				"存在有效协议但本单未引用，走例外确认", "idempotencyKey",
				"s24-order-exception-idem-" + SEQUENCE.incrementAndGet());
		JsonNode firstExceptionSubmit = data(post(admin, "/api/admin/procurement/orders/"
				+ order.get("id").longValue() + "/submit-exception", exceptionSubmit));
		JsonNode retryExceptionSubmit = data(post(admin, "/api/admin/procurement/orders/"
				+ order.get("id").longValue() + "/submit-exception", exceptionSubmit));
		assertThat(retryExceptionSubmit.get("id").longValue()).isEqualTo(firstExceptionSubmit.get("id").longValue());
		data(approveLatestTask("procurement:order:exception-approve"));
		JsonNode confirmedOrder = data(get(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()));
		assertThat(confirmedOrder.get("status").asText()).isEqualTo("CONFIRMED");

		JsonNode overlapAgreement = data(post(admin, "/api/admin/procurement/price-agreements",
				frontendPriceAgreementPayload("PROJECT", fixture.projectId(), fixture.supplierId(),
						fixture.materialId(), fixture.unitId(), "8.000000", "9.040000")));
		data(post(admin, "/api/admin/procurement/price-agreements/" + overlapAgreement.get("id").longValue()
				+ "/submit-activation", Map.of("version", overlapAgreement.get("version").longValue(),
						"reason", "重叠协议提交", "idempotencyKey",
						"s24-agreement-overlap-submit-" + SEQUENCE.incrementAndGet())));
		ApprovalTaskHandle approvalTask = latestApprovalTask("procurement:price-agreement:approve");
		assertError(post(approvalTask.approver(), "/api/admin/approval-tasks/" + approvalTask.taskId()
				+ "/approve", Map.of("version", approvalTask.version(), "comment", "审批重叠协议",
						"idempotencyKey", "s24-approve-overlap-" + SEQUENCE.incrementAndGet())),
				HttpStatus.CONFLICT, "PROCUREMENT_PRICE_AGREEMENT_OVERLAP");
	}

	@Test
	void scheduleExportForOrderConsumesOrderScopeAndScheduleFilters() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture firstFixture = fixture();
		Fixture secondFixture = fixture();
		JsonNode firstOrder = confirmedProjectOrder(admin, firstFixture, "2.000000", "10.000000");
		JsonNode secondOrder = confirmedProjectOrder(admin, secondFixture, "1.000000", "10.000000");

		ResponseEntity<String> exportResponse = exchange(admin, HttpMethod.POST, "/api/admin/export-tasks",
				Map.of("taskType", "PROCUREMENT_SCHEDULE_EXPORT", "objectType", "PROCUREMENT_ORDER",
						"objectId", firstOrder.get("id").longValue(), "filters",
						Map.of("status", "PLANNED", "expectedDateFrom",
								LocalDate.now().plusDays(9).toString(), "expectedDateTo",
								LocalDate.now().plusDays(11).toString())),
				Map.of("Idempotency-Key", "s24-schedule-export-scope-" + firstOrder.get("id").longValue()));
		JsonNode exportTask = data(exportResponse);
		JsonNode succeeded = processTaskUntilStatus(admin, exportTask.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).isEqualTo("SUCCEEDED");
		ResponseEntity<byte[]> downloaded = downloadBytes(admin,
				"/api/admin/document-tasks/" + exportTask.get("id").longValue() + "/download");
		String cells = String.join("|", xlsxTextCells(downloaded.getBody()));
		assertThat(cells).contains(firstOrder.get("orderNo").asText());
		assertThat(cells).doesNotContain(secondOrder.get("orderNo").asText());
	}

	@Test
	void procurementDocumentTasksCreatePrintAndExportDownloadableResults() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();
		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(), "2.000000")));
		data(post(admin, "/api/admin/procurement/requisitions/" + requisition.get("id").longValue()
				+ "/submit-approval", Map.of("version", requisition.get("version").longValue(), "reason",
						"项目专采请购审批", "idempotencyKey", "req-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:requisition:approve"));
		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisition.get("lines").get(0)
					.get("id").longValue(), fixture.materialId(), fixture.unitId(), "2.000000", "10.000000")));
		JsonNode confirmed = data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()
				+ "/confirm", Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"s24-confirm-" + SEQUENCE.incrementAndGet())));

		JsonNode printTask = data(exchange(admin, HttpMethod.POST, "/api/admin/print-tasks",
				Map.of("objectType", "PROCUREMENT_ORDER", "objectId", confirmed.get("id").longValue(),
						"templateCode", "PROCUREMENT_ORDER_V1"),
				Map.of("Idempotency-Key", "s24-order-print-" + confirmed.get("id").longValue())));
		assertThat(printTask.get("taskType").asText()).isEqualTo("PROCUREMENT_ORDER_PRINT");
		JsonNode printed = processTaskUntilStatus(admin, printTask.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(printed.get("status").asText()).as(printed.toString()).isEqualTo("SUCCEEDED");
		assertThat(printed.get("availableActions").toString()).contains("DOWNLOAD");
		ResponseEntity<byte[]> printedFile = downloadBytes(admin,
				"/api/admin/document-tasks/" + printTask.get("id").longValue() + "/download");
		assertThat(printedFile.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(printedFile.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
		assertThat(printedFile.getBody()[0]).isEqualTo((byte) '%');

		List<String> exportTypes = List.of("PROCUREMENT_REQUISITION_EXPORT", "PROCUREMENT_INQUIRY_EXPORT",
				"PROCUREMENT_QUOTE_EXPORT", "PROCUREMENT_PRICE_AGREEMENT_EXPORT", "PROCUREMENT_ORDER_EXPORT",
				"PROCUREMENT_SCHEDULE_EXPORT", "PROCUREMENT_SUPPLY_EXPORT");
		for (String taskType : exportTypes) {
			ResponseEntity<String> exportResponse = exchange(admin, HttpMethod.POST, "/api/admin/export-tasks",
					Map.of("taskType", taskType, "filters", Map.of("keyword", confirmed.get("orderNo").asText()),
							"objectType", "PROCUREMENT_ORDER", "objectId", confirmed.get("id").longValue()),
					Map.of("Idempotency-Key", "s24-" + taskType.toLowerCase() + "-" + confirmed.get("id").longValue()));
			JsonNode exportTask = data(exportResponse);
			assertThat(exportTask.get("taskType").asText()).isEqualTo(taskType);
			JsonNode succeeded = processTaskUntilStatus(admin, exportTask.get("id").longValue(), "SUCCEEDED", 8);
			assertThat(succeeded.get("availableActions").toString()).contains("DOWNLOAD");
			ResponseEntity<byte[]> downloaded = downloadBytes(admin,
					"/api/admin/document-tasks/" + exportTask.get("id").longValue() + "/download");
			assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(downloaded.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
			assertThat(downloaded.getBody()[0]).isEqualTo((byte) 'P');
			assertThat(downloaded.getBody()[1]).isEqualTo((byte) 'K');
		}
	}

	@Test
	void procurementFrozenFrontendResourceEndpointsExposeListsUpdatesActionsSchedulesAndSupplies() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Fixture fixture = fixture();

		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				frontendRequisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(),
						"4.000000", "024 前端请购")));
		assertThat(requisition.get("allowedActions").toString()).contains("SUBMIT_APPROVAL");
		assertProjectContext(requisition, fixture, "请购详情");
		assertProjectContext(requisition.get("lines").get(0), fixture, "请购行");
		assertThat(requisition.get("lines").get(0).get("quantity").isTextual()).isTrue();
		JsonNode requisitionList = data(get(admin, "/api/admin/procurement/requisitions?procurementMode=PROJECT"
				+ "&projectId=" + fixture.projectId() + "&status=DRAFT&page=1&pageSize=10"));
		assertPageContains(requisitionList, requisition.get("id").longValue());
		assertProjectContext(findPageItem(requisitionList, "id", requisition.get("id").longValue()), fixture,
				"请购列表");
		JsonNode requisitionUpdated = data(put(admin, "/api/admin/procurement/requisitions/"
				+ requisition.get("id").longValue(), frontendRequisitionUpdatePayload("PROJECT",
						fixture.projectId(), fixture.materialId(), fixture.unitId(), "4.000000",
						"024 前端请购更新", requisition.get("version").longValue())));
		assertProjectContext(requisitionUpdated, fixture, "请购更新详情");
		assertProjectContext(requisitionUpdated.get("lines").get(0), fixture, "请购更新行");
		assertThat(requisitionUpdated.get("allowedActions").toString()).contains("CANCEL");
		JsonNode cancelledRequisition = data(put(admin, "/api/admin/procurement/requisitions/"
				+ requisitionUpdated.get("id").longValue() + "/cancel", Map.of("version",
						requisitionUpdated.get("version").longValue(), "idempotencyKey",
						"s24-req-cancel-" + SEQUENCE.incrementAndGet())));
		assertThat(cancelledRequisition.get("status").asText()).isEqualTo("CANCELLED");

		JsonNode closableRequisition = approvedRequisitionDetail(admin, fixture, "1.000000");
		JsonNode closedRequisition = data(put(admin, "/api/admin/procurement/requisitions/"
				+ closableRequisition.get("id").longValue() + "/close", Map.of("version",
						closableRequisition.get("version").longValue(), "reason", "本次采购需求关闭",
						"idempotencyKey", "s24-req-close-" + SEQUENCE.incrementAndGet())));
		assertThat(closedRequisition.get("status").asText()).isEqualTo("CLOSED");

		JsonNode sourceRequisition = approvedRequisitionDetail(admin, fixture, "3.000000");
		long sourceRequisitionLineId = sourceRequisition.get("lines").get(0).get("id").longValue();
		JsonNode inquiry = data(post(admin, "/api/admin/procurement/inquiries",
				frontendInquiryPayload("PROJECT", fixture.projectId(), sourceRequisitionLineId, fixture.materialId(),
						fixture.unitId(), fixture.supplierId(), "3.000000", "024 冻结询价")));
		assertThat(inquiry.get("allowedActions").toString()).contains("RELEASE");
		assertSourcingProjectLineContext(inquiry.get("lines").get(0), fixture);
		JsonNode inquiryList = data(get(admin, "/api/admin/procurement/inquiries?procurementMode=PROJECT&projectId="
				+ fixture.projectId() + "&status=DRAFT&page=1&pageSize=10"));
		assertPageContains(inquiryList, inquiry.get("id").longValue());
		JsonNode inquiryDetail = data(get(admin, "/api/admin/procurement/inquiries/" + inquiry.get("id").longValue()));
		assertSourcingProjectLineContext(inquiryDetail.get("lines").get(0), fixture);
		JsonNode inquiryUpdated = data(put(admin, "/api/admin/procurement/inquiries/" + inquiry.get("id").longValue(),
				frontendInquiryUpdatePayload("PROJECT", fixture.projectId(), sourceRequisitionLineId,
						fixture.materialId(), fixture.unitId(), fixture.supplierId(), "3.000000", "024 冻结询价更新",
						inquiry.get("version").longValue())));
		assertSourcingProjectLineContext(inquiryUpdated.get("lines").get(0), fixture);
		JsonNode releasedInquiry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ inquiryUpdated.get("id").longValue() + "/release", Map.of("version",
						inquiryUpdated.get("version").longValue(), "idempotencyKey",
						"s24-inquiry-release-" + SEQUENCE.incrementAndGet())));
		assertSourcingProjectLineContext(releasedInquiry.get("lines").get(0), fixture);

		JsonNode quote = data(post(admin, "/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue()
				+ "/quotes", frontendQuotePayload(fixture.supplierId(), fixture.materialId(), "3.000000",
						"10.000000", "11.300000")));
		assertThat(quote.get("allowedActions").toString()).contains("UPDATE");
		assertThat(quote.get("quantity").isTextual()).isTrue();
		assertQuoteProjectContext(quote, fixture);
		JsonNode quoteList = data(get(admin, "/api/admin/procurement/inquiries/" + releasedInquiry.get("id")
			.longValue() + "/quotes?status=VALID&page=1&pageSize=10"));
		assertPageContains(quoteList, quote.get("id").longValue());
		assertQuoteProjectContext(findPageItem(quoteList, "id", quote.get("id").longValue()), fixture);
		JsonNode quoteUpdated = data(put(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/quotes/" + quote.get("id").longValue(),
				frontendQuoteUpdatePayload(fixture.supplierId(), fixture.materialId(), "3.000000",
						"9.900000", "11.187000", quote.get("version").longValue())));
		assertQuoteProjectContext(quoteUpdated, fixture);
		JsonNode quoteToCancel = data(post(admin, "/api/admin/procurement/inquiries/" + releasedInquiry.get("id")
			.longValue() + "/quotes", frontendQuotePayload(fixture.supplierId(), fixture.materialId(),
					"3.000000", "12.000000", "13.560000")));
		JsonNode cancelledQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/quotes/" + quoteToCancel.get("id").longValue()
				+ "/cancel", Map.of("version", quoteToCancel.get("version").longValue(), "idempotencyKey",
						"s24-quote-cancel-" + SEQUENCE.incrementAndGet())));
		assertThat(cancelledQuote.get("status").asText()).isEqualTo("CANCELLED");
		JsonNode completedInquiry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/complete", Map.of("version",
						releasedInquiry.get("version").longValue(), "idempotencyKey",
						"s24-inquiry-complete-" + SEQUENCE.incrementAndGet())));
		JsonNode selectedQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ completedInquiry.get("id").longValue() + "/quotes/" + quoteUpdated.get("id").longValue()
				+ "/select", Map.of("version", quoteUpdated.get("version").longValue(), "reason",
						"选择最低有效报价", "idempotencyKey", "s24-quote-select-" + SEQUENCE.incrementAndGet())));
		assertThat(selectedQuote.get("status").asText()).isEqualTo("SELECTED");
		String selectedQuoteNo = selectedQuote.get("quoteNo").asText();
		long selectedQuoteLineId = selectedQuote.get("lines").get(0).get("id").longValue();

		JsonNode quoteOrder = data(post(admin, "/api/admin/procurement/orders",
				projectQuoteOrderPayload(fixture.supplierId(), fixture.projectId(), sourceRequisitionLineId,
						selectedQuoteLineId, fixture.materialId(), fixture.unitId(), "1.000000", "9.900000")));
		assertQuotePriceSourceNo(quoteOrder.get("lines").get(0), selectedQuoteNo);
		JsonNode confirmedQuoteOrder = data(put(admin, "/api/admin/procurement/orders/"
				+ quoteOrder.get("id").longValue() + "/confirm", Map.of("version",
						quoteOrder.get("version").longValue(), "idempotencyKey",
						"s24-quote-order-confirm-" + SEQUENCE.incrementAndGet())));
		assertQuotePriceSourceNo(confirmedQuoteOrder.get("lines").get(0), selectedQuoteNo);
		JsonNode quoteSupplies = data(get(admin, "/api/admin/procurement/effective-supplies?projectId="
				+ fixture.projectId() + "&materialId=" + fixture.materialId()
				+ "&procurementMode=PROJECT&countedOnly=true&status=CONFIRMED&page=1&pageSize=10"));
		JsonNode quoteSupply = findPageItem(quoteSupplies, "orderId", confirmedQuoteOrder.get("id").longValue());
		assertQuotePriceSourceNo(quoteSupply, selectedQuoteNo);

		JsonNode cancellableInquiry = data(post(admin, "/api/admin/procurement/inquiries",
				frontendInquiryPayload("PROJECT", fixture.projectId(), sourceRequisitionLineId, fixture.materialId(),
						fixture.unitId(), fixture.supplierId(), "1.000000", "024 取消询价")));
		JsonNode cancelledInquiry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ cancellableInquiry.get("id").longValue() + "/cancel", Map.of("version",
						cancellableInquiry.get("version").longValue(), "idempotencyKey",
						"s24-inquiry-cancel-" + SEQUENCE.incrementAndGet())));
		assertThat(cancelledInquiry.get("status").asText()).isEqualTo("CANCELLED");

		JsonNode agreement = data(post(admin, "/api/admin/procurement/price-agreements",
				frontendPriceAgreementPayload("PROJECT", fixture.projectId(), fixture.supplierId(),
						fixture.materialId(), fixture.unitId(), "8.000000", "9.040000")));
		assertThat(agreement.get("allowedActions").toString()).contains("SUBMIT");
		assertProjectContext(agreement, fixture, "价格协议详情");
		assertProjectContext(agreement.get("lines").get(0), fixture, "价格协议行");
		assertThat(agreement.get("taxExcludedUnitPrice").isTextual()).isTrue();
		JsonNode agreementList = data(get(admin, "/api/admin/procurement/price-agreements?procurementMode=PROJECT"
				+ "&projectId=" + fixture.projectId() + "&status=DRAFT&page=1&pageSize=10"));
		assertPageContains(agreementList, agreement.get("id").longValue());
		assertProjectContext(findPageItem(agreementList, "id", agreement.get("id").longValue()), fixture,
				"价格协议列表");
		JsonNode agreementUpdated = data(put(admin, "/api/admin/procurement/price-agreements/"
				+ agreement.get("id").longValue(), frontendPriceAgreementUpdatePayload("PROJECT",
						fixture.projectId(), fixture.supplierId(), fixture.materialId(), fixture.unitId(),
						"7.500000", "8.475000", agreement.get("version").longValue())));
		assertProjectContext(agreementUpdated, fixture, "价格协议更新详情");
		assertProjectContext(agreementUpdated.get("lines").get(0), fixture, "价格协议更新行");
		JsonNode cancelledAgreement = data(put(admin, "/api/admin/procurement/price-agreements/"
				+ agreementUpdated.get("id").longValue() + "/cancel", Map.of("version",
						agreementUpdated.get("version").longValue(), "idempotencyKey",
						"s24-agreement-cancel-" + SEQUENCE.incrementAndGet())));
		assertThat(cancelledAgreement.get("status").asText()).isEqualTo("CANCELLED");

		JsonNode agreementToDisable = data(post(admin, "/api/admin/procurement/price-agreements",
				frontendPriceAgreementPayload("PROJECT", fixture.projectId(), fixture.supplierId(),
						fixture.materialId(), fixture.unitId(), "8.000000", "9.040000")));
		data(post(admin, "/api/admin/procurement/price-agreements/" + agreementToDisable.get("id").longValue()
				+ "/submit-activation", Map.of("version", agreementToDisable.get("version").longValue(),
						"reason", "激活项目协议价", "idempotencyKey",
						"s24-agreement-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:price-agreement:approve"));
		JsonNode activeAgreement = data(get(admin, "/api/admin/procurement/price-agreements/"
				+ agreementToDisable.get("id").longValue()));
		JsonNode disabledAgreement = data(put(admin, "/api/admin/procurement/price-agreements/"
				+ activeAgreement.get("id").longValue() + "/disable", Map.of("version",
						activeAgreement.get("version").longValue(), "reason", "停止使用协议价",
						"idempotencyKey", "s24-agreement-disable-" + SEQUENCE.incrementAndGet())));
		assertThat(disabledAgreement.get("status").asText()).isEqualTo("DISABLED");

		JsonNode requisitionOrderSource = approvedRequisitionDetail(admin, fixture, "3.000000");
		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(),
						requisitionOrderSource.get("lines").get(0).get("id").longValue(),
						fixture.materialId(), fixture.unitId(), "3.000000", "10.000000")));
		assertOrderLineTaxAndSource(order.get("lines").get(0));
		JsonNode confirmed = data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()
				+ "/confirm", Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"s24-confirm-" + SEQUENCE.incrementAndGet())));
		assertOrderLineTaxAndSource(confirmed.get("lines").get(0));
		JsonNode schedules = data(get(admin, "/api/admin/procurement/orders/" + confirmed.get("id").longValue()
				+ "/schedules?status=PLANNED&page=1&pageSize=10"));
		assertThat(schedules.get("page").asInt()).isEqualTo(1);
		assertThat(schedules.get("pageSize").asInt()).isEqualTo(10);
		JsonNode schedule = schedules.get("items").get(0);
		assertThat(schedule.get("allowedActions").toString()).contains("UPDATE");
		assertThat(schedule.get("plannedQuantity").isTextual()).isTrue();
		JsonNode updatedSchedule = data(put(admin, "/api/admin/procurement/orders/" + confirmed.get("id").longValue()
				+ "/schedules/" + schedule.get("id").longValue(), Map.of("version",
						schedule.get("version").longValue(), "scheduleSeq", schedule.get("scheduleSeq").intValue(),
						"expectedArrivalDate", LocalDate.now().plusDays(15).toString(),
						"plannedQuantity", "3.000000", "remark", "单计划调整")));
		assertThat(updatedSchedule.get("expectedArrivalDate").asText()).isEqualTo(LocalDate.now().plusDays(15).toString());

		JsonNode supplies = data(get(admin, "/api/admin/procurement/effective-supplies?projectId="
				+ fixture.projectId() + "&materialId=" + fixture.materialId()
				+ "&procurementMode=PROJECT&countedOnly=true&status=CONFIRMED&page=1&pageSize=10"));
		assertThat(supplies.get("total").asLong()).isPositive();
		JsonNode supply = findPageItem(supplies, "orderId", confirmed.get("id").longValue());
		assertThat(supply.get("remainingQuantity").isTextual()).isTrue();
		assertProjectContext(supply, fixture, "有效供给");
		assertThat(supply.get("priceSourceType").asText()).isEqualTo("REQUISITION_APPROVED");
		assertThat(supply.hasNonNull("sourceNo") || supply.hasNonNull("priceSourceNo"))
			.as("有效供给必须返回真实价格来源单号").isTrue();

		JsonNode closedSchedule = data(put(admin, "/api/admin/procurement/orders/"
				+ confirmed.get("id").longValue() + "/schedules/" + updatedSchedule.get("id").longValue()
				+ "/close", Map.of("version", updatedSchedule.get("version").longValue(), "reason",
						"到货计划取消", "idempotencyKey", "s24-schedule-close-" + SEQUENCE.incrementAndGet())));
		assertThat(closedSchedule.get("status").asText()).isEqualTo("CLOSED");
	}

	private Map<String, Object> requisitionPayload(String purchaseMode, Long projectId, long materialId, long unitId,
			String quantity) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("purchaseMode", purchaseMode);
		body.put("projectId", projectId);
		body.put("requiredDate", LocalDate.now().plusDays(7).toString());
		body.put("purpose", "024 项目专采请购");
		body.put("lines", List.of(Map.of("lineNo", 1, "materialId", materialId, "unitId", unitId, "quantity",
				quantity, "requiredDate", LocalDate.now().plusDays(7).toString(), "purpose", "项目物料")));
		return body;
	}

	private Map<String, Object> frontendRequisitionPayload(String procurementMode, Long projectId, long materialId,
			long unitId, String quantity, String title) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("procurementMode", procurementMode);
		body.put("projectId", projectId);
		body.put("title", title);
		body.put("requiredDate", LocalDate.now().plusDays(7).toString());
		body.put("remark", title + "备注");
		body.put("lines", List.of(Map.of("lineNo", 1, "procurementMode", procurementMode, "projectId", projectId,
				"materialId", materialId, "unitId", unitId, "quantity", quantity, "requiredDate",
				LocalDate.now().plusDays(7).toString(), "purpose", title)));
		return body;
	}

	private Map<String, Object> frontendRequisitionUpdatePayload(String procurementMode, Long projectId, long materialId,
			long unitId, String quantity, String title, long version) {
		Map<String, Object> body = frontendRequisitionPayload(procurementMode, projectId, materialId, unitId, quantity,
				title);
		body.put("version", version);
		return body;
	}

	private Map<String, Object> frontendInquiryPayload(String procurementMode, Long projectId, long requisitionLineId,
			long materialId, long unitId, long supplierId, String quantity, String title) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("procurementMode", procurementMode);
		body.put("projectId", projectId);
		body.put("title", title);
		body.put("supplierIds", List.of(supplierId));
		body.put("remark", title + "备注");
		body.put("lines", List.of(Map.of("lineNo", 1, "requisitionLineId", requisitionLineId, "materialId",
				materialId, "unitId", unitId, "quantity", quantity, "requiredDate",
				LocalDate.now().plusDays(8).toString(), "remark", title)));
		return body;
	}

	private Map<String, Object> frontendInquiryUpdatePayload(String procurementMode, Long projectId,
			long requisitionLineId, long materialId, long unitId, long supplierId, String quantity, String title,
			long version) {
		Map<String, Object> body = frontendInquiryPayload(procurementMode, projectId, requisitionLineId, materialId,
				unitId, supplierId, quantity, title);
		body.put("version", version);
		return body;
	}

	private Map<String, Object> frontendQuotePayload(long supplierId, long materialId, String quantity,
			String taxExcludedUnitPrice, String taxIncludedUnitPrice) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("supplierId", supplierId);
		body.put("materialId", materialId);
		body.put("quantity", quantity);
		body.put("taxRate", "0.130000");
		body.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		body.put("taxIncludedUnitPrice", taxIncludedUnitPrice);
		body.put("taxExcludedAmount", "0.00");
		body.put("taxIncludedAmount", "0.00");
		body.put("currency", "CNY");
		body.put("validFrom", LocalDate.now().toString());
		body.put("validTo", LocalDate.now().plusDays(30).toString());
		body.put("deliveryDate", LocalDate.now().plusDays(12).toString());
		body.put("remark", "024 报价");
		return body;
	}

	private Map<String, Object> frontendQuoteUpdatePayload(long supplierId, long materialId, String quantity,
			String taxExcludedUnitPrice, String taxIncludedUnitPrice, long version) {
		Map<String, Object> body = frontendQuotePayload(supplierId, materialId, quantity, taxExcludedUnitPrice,
				taxIncludedUnitPrice);
		body.put("version", version);
		return body;
	}

	private Map<String, Object> frontendPriceAgreementPayload(String procurementMode, Long projectId, long supplierId,
			long materialId, long unitId, String taxExcludedUnitPrice, String taxIncludedUnitPrice) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("procurementMode", procurementMode);
		body.put("projectId", projectId);
		body.put("supplierId", supplierId);
		body.put("materialId", materialId);
		body.put("unitId", unitId);
		body.put("taxRate", "0.130000");
		body.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		body.put("taxIncludedUnitPrice", taxIncludedUnitPrice);
		body.put("currency", "CNY");
		body.put("minPurchaseQuantity", "0.000000");
		body.put("validFrom", LocalDate.now().toString());
		body.put("validTo", LocalDate.now().plusDays(30).toString());
		body.put("remark", "024 协议价");
		return body;
	}

	private Map<String, Object> frontendPriceAgreementUpdatePayload(String procurementMode, Long projectId,
			long supplierId, long materialId, long unitId, String taxExcludedUnitPrice, String taxIncludedUnitPrice,
			long version) {
		Map<String, Object> body = frontendPriceAgreementPayload(procurementMode, projectId, supplierId, materialId,
				unitId, taxExcludedUnitPrice, taxIncludedUnitPrice);
		body.put("version", version);
		return body;
	}

	private Map<String, Object> projectOrderPayload(long supplierId, long projectId, long requisitionLineId,
			long materialId, long unitId, String quantity, String unitPrice) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("supplierId", supplierId);
		body.put("orderDate", LocalDate.now().toString());
		body.put("purchaseMode", "PROJECT");
		body.put("projectId", projectId);
		body.put("currency", "CNY");
		body.put("remark", "024 项目采购订单");
		body.put("lines", List.of(Map.of("lineNo", 1, "materialId", materialId, "unitId", unitId, "quantity",
				quantity, "unitPrice", unitPrice, "taxRate", "0.130000", "taxIncludedUnitPrice", "14.125000",
				"sourceRequisitionLineId", requisitionLineId, "schedules",
				List.of(Map.of("lineNo", 1, "plannedDate", LocalDate.now().plusDays(10).toString(),
						"plannedQuantity", quantity)))));
		return body;
	}

	private Map<String, Object> projectQuoteOrderPayload(long supplierId, long projectId, long requisitionLineId,
			long quoteLineId, long materialId, long unitId, String quantity, String unitPrice) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("supplierId", supplierId);
		body.put("orderDate", LocalDate.now().toString());
		body.put("purchaseMode", "PROJECT");
		body.put("projectId", projectId);
		body.put("currency", "CNY");
		body.put("remark", "024 报价来源项目采购订单");
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("quantity", quantity);
		line.put("unitPrice", unitPrice);
		line.put("taxRate", "0.130000");
		line.put("taxIncludedUnitPrice", "11.187000");
		line.put("sourceRequisitionLineId", requisitionLineId);
		line.put("sourceQuoteLineId", quoteLineId);
		line.put("priceSourceType", "QUOTE_SELECTION");
		line.put("schedules", List.of(Map.of("lineNo", 1, "plannedDate",
				LocalDate.now().plusDays(10).toString(), "plannedQuantity", quantity)));
		body.put("lines", List.of(line));
		return body;
	}

	private Map<String, Object> orderLine(int lineNo, long materialId, long unitId, long requisitionLineId,
			String quantity, String unitPrice, String priceSourceType) {
		return Map.of("lineNo", lineNo, "materialId", materialId, "unitId", unitId, "quantity", quantity,
				"unitPrice", unitPrice, "taxRate", "0.130000", "taxIncludedUnitPrice", "12.430000",
				"sourceRequisitionLineId", requisitionLineId, "priceSourceType", priceSourceType, "schedules",
				List.of(Map.of("lineNo", 1, "plannedDate", LocalDate.now().plusDays(10 + lineNo).toString(),
						"plannedQuantity", quantity)));
	}

	private JsonNode approvedRequisition(AuthenticatedSession admin, Fixture fixture, String quantity) throws Exception {
		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(), quantity)));
		data(post(admin, "/api/admin/procurement/requisitions/" + requisition.get("id").longValue()
				+ "/submit-approval", Map.of("version", requisition.get("version").longValue(), "reason",
						"项目专采请购审批", "idempotencyKey", "req-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:requisition:approve"));
		Map<String, JsonNode> result = Map.of("lineId", requisition.get("lines").get(0).get("id"));
		return this.objectMapper.valueToTree(result);
	}

	private JsonNode approvedRequisitionDetail(AuthenticatedSession admin, Fixture fixture, String quantity)
			throws Exception {
		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				frontendRequisitionPayload("PROJECT", fixture.projectId(), fixture.materialId(), fixture.unitId(),
						quantity, "024 已批请购")));
		data(post(admin, "/api/admin/procurement/requisitions/" + requisition.get("id").longValue()
				+ "/submit-approval", Map.of("version", requisition.get("version").longValue(), "reason",
						"项目专采请购审批", "idempotencyKey", "req-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:requisition:approve"));
		return data(get(admin, "/api/admin/procurement/requisitions/" + requisition.get("id").longValue()));
	}

	private JsonNode activatePriceAgreement(AuthenticatedSession admin, Fixture fixture, String taxExcludedUnitPrice,
			String taxIncludedUnitPrice) throws Exception {
		JsonNode agreement = data(post(admin, "/api/admin/procurement/price-agreements",
				frontendPriceAgreementPayload("PROJECT", fixture.projectId(), fixture.supplierId(),
						fixture.materialId(), fixture.unitId(), taxExcludedUnitPrice, taxIncludedUnitPrice)));
		data(post(admin, "/api/admin/procurement/price-agreements/" + agreement.get("id").longValue()
				+ "/submit-activation", Map.of("version", agreement.get("version").longValue(), "reason",
						"激活项目协议价", "idempotencyKey",
						"s24-agreement-submit-" + SEQUENCE.incrementAndGet())));
		data(approveLatestTask("procurement:price-agreement:approve"));
		return data(get(admin, "/api/admin/procurement/price-agreements/" + agreement.get("id").longValue()));
	}

	private JsonNode confirmedProjectOrder(AuthenticatedSession admin, Fixture fixture, String quantity, String unitPrice)
			throws Exception {
		JsonNode requisition = approvedRequisitionDetail(admin, fixture, quantity);
		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderPayload(fixture.supplierId(), fixture.projectId(), requisition.get("lines").get(0)
					.get("id").longValue(), fixture.materialId(), fixture.unitId(), quantity, unitPrice)));
		return data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue() + "/confirm",
				Map.of("version", order.get("version").longValue(), "idempotencyKey",
						"s24-confirm-" + SEQUENCE.incrementAndGet())));
	}

	private Map<String, Object> receiptPayload(long warehouseId, long orderLineId, long materialId, long unitId,
			String quantity) {
		return Map.of("warehouseId", warehouseId, "businessDate", LocalDate.now().toString(), "remark", "024 项目入库",
				"lines", List.of(Map.of("lineNo", 1, "orderLineId", orderLineId, "materialId", materialId, "unitId",
						unitId, "quantity", quantity, "remark", "项目入库")));
	}

	private Map<String, Object> purchaseReturnPayload(long receiptId, long receiptLineId, String quantity,
			String clientRequestId) {
		return Map.of("sourceReceiptId", receiptId, "businessDate", LocalDate.now().toString(),
				"clientRequestId", clientRequestId, "remark", "024 项目采购退货",
				"lines", List.of(Map.of("lineNo", 1, "sourceReceiptLineId", receiptLineId,
						"quantity", quantity, "reason", "按原入库退回")));
	}

	private ResponseEntity<String> approveLatestTask(String permissionCode) throws Exception {
		ApprovalTaskHandle task = latestApprovalTask(permissionCode);
		return post(task.approver(), "/api/admin/approval-tasks/" + task.taskId() + "/approve",
				Map.of("version", task.version(), "comment", "同意 024 请购",
						"idempotencyKey", "s24-approve-" + SEQUENCE.incrementAndGet()));
	}

	private ApprovalTaskHandle latestApprovalTask(String permissionCode) throws Exception {
		List<String> permissions = new java.util.ArrayList<>(List.of("platform:approval:view", "platform:todo:view",
				"platform:message:view", "procurement:requisition:view", permissionCode));
		if ("procurement:price-agreement:approve".equals(permissionCode)) {
			permissions.add("procurement:price-agreement:view");
		}
		if ("procurement:order:exception-approve".equals(permissionCode)) {
			permissions.add("procurement:order:view");
		}
		AuthenticatedSession approver = createUserAndLogin("s24-approver-", "S24_APPROVER_", permissions);
		JsonNode todo = data(get(approver, "/api/admin/approval-tasks?scope=TODO&page=1&pageSize=1"));
		assertThat(todo.get("total").longValue()).isPositive();
		JsonNode task = todo.get("items").get(0);
		return new ApprovalTaskHandle(approver, task.get("taskId").longValue(), task.get("version").longValue());
	}

	private Fixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("S24-U-" + suffix);
		long warehouseId = insertWarehouse("S24-W-" + suffix);
		long supplierId = insertSupplier("S24-S-" + suffix);
		long customerId = insertCustomer("S24-C-" + suffix);
		long projectId = insertActiveProject("S24-P-" + suffix, customerId, userId("admin"));
		long categoryId = insertMaterialCategory("S24-CAT-" + suffix);
		long materialId = insertMaterial("S24-M-" + suffix, categoryId, unitId);
		return new Fixture(unitId, warehouseId, supplierId, projectId, materialId);
	}

	private long insertUnit(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "024单位" + code);
	}

	private long insertWarehouse(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "024仓库" + code);
	}

	private long insertSupplier(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "024供应商" + code);
	}

	private long insertCustomer(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "024客户" + code);
	}

	private long insertActiveProject(String code, long customerId, long ownerUserId) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date, status,
					target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at,
					activated_by, activated_at
				)
				values (?, ?, ?, ?, ?, ?, 'ACTIVE', 1000.00, 600.00, '024项目', 'test', now(), 'test', now(),
					'test', now())
				returning id
				""", Long.class, code, "024项目" + code, customerId, ownerUserId, LocalDate.now(),
				LocalDate.now().plusDays(30));
	}

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "024分类" + code);
	}

	private long insertMaterial(String code, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (
					code, name, specification, material_type, source_type, tracking_method, category_id, unit_id,
					status, cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '024规格', 'RAW_MATERIAL', 'PURCHASED', 'NONE', ?, ?, 'ENABLED',
					'DIRECT_MATERIAL', 'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, "024物料" + code, categoryId, unitId);
	}

	private long userId(String username) {
		return this.jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
	}

	private long projectCostLayerCount(long projectId, long materialId, long receiptId, long receiptLineId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_project_cost_layer
				where project_id = ?
				and material_id = ?
				and source_type = 'PURCHASE_RECEIPT'
				and source_id = ?
				and source_line_id = ?
				and original_quantity = 5.000000
				and original_amount = 62.50
				""", Long.class, projectId, materialId, receiptId, receiptLineId);
	}

	private BigDecimal projectLayerRemainingAmount(long receiptLineId) {
		return this.jdbcTemplate.queryForObject("""
				select remaining_amount
				from inv_project_cost_layer
				where source_type = 'PURCHASE_RECEIPT'
				and source_line_id = ?
				order by id desc
				limit 1
				""", BigDecimal.class, receiptLineId);
	}

	private ValueMovement valueMovement(String sourceType, long sourceId, long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select ownership_type, project_id, quantity, inventory_amount
				from inv_value_movement
				where source_type = ?
				and source_id = ?
				and source_line_id = ?
				""", (rs, rowNum) -> new ValueMovement(rs.getString("ownership_type"), rs.getLong("project_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("inventory_amount")), sourceType, sourceId, sourceLineId);
	}

	private String status(String tableName, long id) {
		return this.jdbcTemplate.queryForObject("select status from " + tableName + " where id = ?", String.class,
				id);
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
				""", Long.class, rolePrefix + suffix, "024审批角色" + suffix, "024审批角色" + suffix);
		this.jdbcTemplate.update("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				""", username, this.passwordEncoder.encode(ADMIN_PASSWORD), "024审批人" + suffix);
		long userId = userId(username);
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

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				String.class);
	}

	private ResponseEntity<String> post(AuthenticatedSession session, String path, Object body) {
		return exchange(session, HttpMethod.POST, path, body);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Object body) {
		return exchange(session, HttpMethod.PUT, path, body);
	}

	private ResponseEntity<String> exchange(AuthenticatedSession session, HttpMethod method, String path, Object body) {
		return this.restTemplate.exchange(path, method, entity(body, session.sessionCookie(), session.csrfSession()),
				String.class);
	}

	private ResponseEntity<String> exchange(AuthenticatedSession session, HttpMethod method, String path, Object body,
			Map<String, String> headers) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add(HttpHeaders.COOKIE, session.sessionCookie());
		httpHeaders.add(session.csrfSession().headerName(), session.csrfSession().token());
		headers.forEach(httpHeaders::add);
		return this.restTemplate.exchange(path, method, new HttpEntity<>(body, httpHeaders), String.class);
	}

	private JsonNode processTaskUntilStatus(AuthenticatedSession session, long taskId, String status, int maxAttempts)
			throws Exception {
		for (int i = 0; i < maxAttempts; i++) {
			JsonNode current = data(get(session, "/api/admin/document-tasks/" + taskId));
			if (status.equals(current.get("status").asText())) {
				return current;
			}
			this.documentTaskWorker.processAvailableOnce();
			Thread.sleep(200);
		}
		return data(get(session, "/api/admin/document-tasks/" + taskId));
	}

	private ResponseEntity<byte[]> downloadBytes(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session.sessionCookie(), session.csrfSession()), byte[].class);
	}

	private List<String> xlsxTextCells(byte[] bytes) throws Exception {
		DataFormatter formatter = new DataFormatter();
		List<String> values = new java.util.ArrayList<>();
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			for (Sheet sheet : workbook) {
				for (Row row : sheet) {
					row.forEach((cell) -> {
						String value = formatter.formatCellValue(cell);
						if (value != null && !value.isBlank()) {
							values.add(value);
						}
					});
				}
			}
		}
		return values;
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
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo("OK");
		return this.objectMapper.readTree(response.getBody()).get("data");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(this.objectMapper.readTree(response.getBody()).get("code").asText()).isEqualTo(code);
	}

	private void assertPageContains(JsonNode page, long expectedId) {
		assertThat(page.get("items").isArray()).isTrue();
		assertThat(page.get("total").longValue()).isPositive();
		assertThat(page.get("page").intValue()).isEqualTo(1);
		assertThat(page.get("pageSize").intValue()).isPositive();
		boolean found = false;
		for (JsonNode item : page.get("items")) {
			found = found || item.get("id").longValue() == expectedId;
		}
		assertThat(found).isTrue();
	}

	private JsonNode findPageItem(JsonNode page, String idField, long expectedId) {
		assertThat(page.get("items").isArray()).isTrue();
		for (JsonNode item : page.get("items")) {
			if (item.has(idField) && item.get(idField).longValue() == expectedId) {
				return item;
			}
		}
		throw new AssertionError("未找到分页项目 " + idField + "=" + expectedId + ": " + page);
	}

	private void assertQuoteProjectContext(JsonNode quote, Fixture fixture) {
		assertThat(quote.hasNonNull("procurementMode")).as("报价候选必须返回采购模式").isTrue();
		assertThat(quote.hasNonNull("ownershipType")).as("报价候选必须返回所有权模式").isTrue();
		assertThat(quote.hasNonNull("projectId")).as("报价候选必须返回项目 ID").isTrue();
		assertThat(quote.hasNonNull("projectCode")).as("报价候选必须返回项目编号").isTrue();
		assertThat(quote.hasNonNull("projectName")).as("报价候选必须返回项目名称").isTrue();
		assertThat(quote.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(quote.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(quote.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(quote.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(quote.get("projectName").asText()).startsWith("024项目");
		JsonNode firstLine = quote.get("lines").get(0);
		assertThat(firstLine.hasNonNull("procurementMode")).as("报价行必须返回采购模式").isTrue();
		assertThat(firstLine.hasNonNull("ownershipType")).as("报价行必须返回所有权模式").isTrue();
		assertThat(firstLine.hasNonNull("projectId")).as("报价行必须返回项目 ID").isTrue();
		assertThat(firstLine.hasNonNull("projectCode")).as("报价行必须返回项目编号").isTrue();
		assertThat(firstLine.hasNonNull("projectName")).as("报价行必须返回项目名称").isTrue();
		assertThat(firstLine.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(firstLine.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(firstLine.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(firstLine.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(firstLine.get("projectName").asText()).startsWith("024项目");
	}

	private void assertSourcingProjectLineContext(JsonNode line, Fixture fixture) {
		assertThat(line.hasNonNull("procurementMode")).as("询价行必须返回采购模式").isTrue();
		assertThat(line.hasNonNull("ownershipType")).as("询价行必须返回所有权模式").isTrue();
		assertThat(line.hasNonNull("projectId")).as("询价行必须返回项目 ID").isTrue();
		assertThat(line.hasNonNull("projectCode")).as("询价行必须返回项目编号").isTrue();
		assertThat(line.hasNonNull("projectName")).as("询价行必须返回项目名称").isTrue();
		assertThat(line.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(line.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(line.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(line.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(line.get("projectName").asText()).startsWith("024项目");
	}

	private void assertProjectContext(JsonNode node, Fixture fixture, String label) {
		assertThat(node.hasNonNull("procurementMode")).as(label + "必须返回采购模式").isTrue();
		assertThat(node.hasNonNull("ownershipType")).as(label + "必须返回所有权模式").isTrue();
		assertThat(node.hasNonNull("projectId")).as(label + "必须返回项目 ID").isTrue();
		assertThat(node.hasNonNull("projectCode")).as(label + "必须返回项目编号").isTrue();
		assertThat(node.hasNonNull("projectName")).as(label + "必须返回项目名称").isTrue();
		assertThat(node.get("procurementMode").asText()).isEqualTo("PROJECT");
		assertThat(node.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(node.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(node.get("projectCode").asText()).startsWith("S24-P-");
		assertThat(node.get("projectName").asText()).startsWith("024项目");
	}

	private void assertOrderLineTaxAndSource(JsonNode line) {
		assertThat(line.get("taxExcludedUnitPrice").isTextual()).isTrue();
		assertThat(line.get("taxIncludedUnitPrice").isTextual()).isTrue();
		assertThat(line.get("taxRate").isTextual()).isTrue();
		assertThat(line.get("currency").asText()).isEqualTo("CNY");
		assertThat(line.get("priceSourceType").asText()).isEqualTo("REQUISITION_APPROVED");
		assertThat(line.hasNonNull("sourceNo") || line.hasNonNull("priceSourceNo"))
			.as("订单行必须返回价格来源单号").isTrue();
	}

	private void assertQuotePriceSourceNo(JsonNode node, String quoteNo) {
		assertThat(node.get("priceSourceType").asText()).isEqualTo("QUOTE_SELECTION");
		assertThat(node.hasNonNull("sourceNo")).as("报价来源必须返回 sourceNo").isTrue();
		assertThat(node.hasNonNull("priceSourceNo")).as("报价来源必须返回 priceSourceNo").isTrue();
		assertThat(node.get("sourceNo").asText()).isEqualTo(quoteNo);
		assertThat(node.get("priceSourceNo").asText()).isEqualTo(quoteNo);
		assertThat(node.get("sourceNo").asText()).startsWith("PQT-");
		assertThat(node.get("sourceNo").asText()).doesNotStartWith("PRQ-");
	}

	private long countRows(String tableName, String where, Object... args) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + where, Long.class,
				args);
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

	private void assertDecimal(BigDecimal actual, String expected) {
		assertThat(actual).isNotNull();
		assertThat(actual.compareTo(new BigDecimal(expected))).isZero();
	}

	private void assertMissingOrNull(JsonNode node, String fieldName) {
		assertThat(!node.has(fieldName) || node.get(fieldName).isNull()).as(fieldName + " 应被脱敏").isTrue();
	}

	private record CsrfSession(String sessionCookie, String token, String headerName) {
	}

	private record AuthenticatedSession(String sessionCookie, CsrfSession csrfSession) {
	}

	private record ApprovalTaskHandle(AuthenticatedSession approver, long taskId, long version) {
	}

	private record Fixture(long unitId, long warehouseId, long supplierId, long projectId, long materialId) {
	}

	private record ValueMovement(String ownershipType, long projectId, BigDecimal quantity, BigDecimal inventoryAmount) {
	}

}
