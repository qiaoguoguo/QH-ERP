package com.qherp.api.system.stage023;

import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=stage023-inventory-valuation",
				"qherp.platform.task.worker.enabled=false"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage023InventoryValuationIntegrationTests extends PostgresIntegrationTest {

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
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void 公共库存移动平均和尾差吸收保持数量流水成本池一致() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_MWA_");

		JsonNode firstIn = data(createDocument(admin, documentPayload("OPENING", "023 公共池首笔入库",
				List.of(line(1, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
						"100.000000", null, "10.000000")))));
		postDocument(admin, firstIn);
		JsonNode secondIn = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 公共池第二笔入库",
				List.of(line(1, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
						"50.000000", "INCREASE", "13.000000")))));
		postDocument(admin, secondIn);

		JsonNode afterInbound = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.valuedMaterialId() + "&onlyPositive=true"));
		assertThat(afterInbound.get("costVisible").booleanValue()).isTrue();
		assertThat(afterInbound.get("ownershipType").asText()).isEqualTo("PUBLIC");
		assertThat(afterInbound.get("projectId").isNull()).isTrue();
		assertDecimal(afterInbound, "quantityOnHand", "150.000000");
		assertDecimal(afterInbound, "inventoryAmount", "1650.00");
		assertDecimal(afterInbound, "averageUnitCost", "11.000000");
		assertThat(afterInbound.get("costLayerCount").longValue()).isZero();

		JsonNode out = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 公共池出库",
				List.of(line(1, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
						"80.000000", "DECREASE", null)))));
		postDocument(admin, out);

		JsonNode afterOutbound = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.valuedMaterialId() + "&onlyPositive=true"));
		assertDecimal(afterOutbound, "quantityOnHand", "70.000000");
		assertDecimal(afterOutbound, "inventoryAmount", "770.00");
		assertDecimal(afterOutbound, "averageUnitCost", "11.000000");
		JsonNode outboundMovement = firstItem(get(admin, "/api/admin/inventory/movements?materialId="
				+ fixture.valuedMaterialId() + "&movementType=ADJUSTMENT_DECREASE"));
		assertThat(outboundMovement.get("valuationMethod").asText()).isEqualTo("MOVING_WEIGHTED_AVERAGE");
		assertDecimal(outboundMovement, "unitCost", "11.000000");
		assertDecimal(outboundMovement, "inventoryAmount", "880.00");

		InventoryFixture tailFixture = createFixture("S23_TAIL_");
		JsonNode tailOpening = data(createDocument(admin, documentPayload("OPENING", "023 公共池尾差入库",
				List.of(line(1, tailFixture.rawWarehouseId(), tailFixture.valuedMaterialId(), tailFixture.unitId(),
						"3.000000", null, "3.333333")))));
		postDocument(admin, tailOpening);
		JsonNode tailFirstOut = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 公共池尾差首次出库",
				List.of(line(1, tailFixture.rawWarehouseId(), tailFixture.valuedMaterialId(), tailFixture.unitId(),
						"1.000000", "DECREASE", null)))));
		postDocument(admin, tailFirstOut);
		JsonNode tailFinalOut = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 公共池尾差出清",
				List.of(line(1, tailFixture.rawWarehouseId(), tailFixture.valuedMaterialId(), tailFixture.unitId(),
						"2.000000", "DECREASE", null)))));
		postDocument(admin, tailFinalOut);

		JsonNode tailBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ tailFixture.valuedMaterialId() + "&includeZero=true"));
		assertDecimal(tailBalance, "quantityOnHand", "0.000000");
		assertDecimal(tailBalance, "inventoryAmount", "0.00");
		assertDecimal(firstValueMovement(tailFixture.valuedMaterialId(), "ADJUSTMENT_DECREASE"), "inventoryAmount",
				"3.33");
		assertDecimal(lastValueMovement(tailFixture.valuedMaterialId(), "ADJUSTMENT_DECREASE"), "inventoryAmount",
				"6.67");
	}

	@Test
	void 项目实际成本层退料和所有权隔离不得污染公共池或其他项目() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_LAYER_");
		long projectAId = insertProject("S23_LAYER_P1_");
		long projectBId = insertProject("S23_LAYER_P2_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"15.000000", "12.000000");

		JsonNode conversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("公共转项目建立两层", List.of(
						ownershipLine(1, "PUBLIC", null, "PROJECT", projectAId, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "10.000000",
								"12.000000", null),
						ownershipLine(2, "PUBLIC", null, "PROJECT", projectAId, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "5.000000",
								"15.000000", null))),
				admin));
		JsonNode posted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue() + "/submit",
				Map.of("version", conversion.get("version").longValue(), "reason", "023 所有权转换审批",
						"idempotencyKey", "s23-conv-submit-" + conversion.get("id").longValue()),
				admin));
		assertThat(posted.get("approvalSummary").get("sceneCode").asText())
			.isEqualTo("INVENTORY_OWNERSHIP_CONVERSION_POST");
		approveLatestTask("inventory:ownership-conversion:post-approve");

		JsonNode layers = data(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectAId
				+ "&materialId=" + fixture.valuedMaterialId() + "&page=1&pageSize=20"));
		assertThat(layers.get("total").longValue()).isEqualTo(2);
		JsonNode layerA = findLayer(layers.get("items"), "12.000000");
		assertDecimal(layerA, "remainingQuantity", "10.000000");
		assertDecimal(layerA, "remainingAmount", "120.00");

		JsonNode issue = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("项目层 A 退回公共池", List.of(
						ownershipLine(1, "PROJECT", projectAId, "PUBLIC", null, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "4.000000",
								null, layerA.get("id").longValue()))),
				admin));
		JsonNode issueSubmitted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + issue.get("id").longValue() + "/submit",
				Map.of("version", issue.get("version").longValue(), "reason", "023 项目退料审批",
						"idempotencyKey", "s23-return-submit-" + issue.get("id").longValue()),
				admin));
		assertThat(issueSubmitted.get("approvalSummary").get("sceneCode").asText())
			.isEqualTo("INVENTORY_OWNERSHIP_CONVERSION_POST");
		approveLatestTask("inventory:ownership-conversion:post-approve");

		JsonNode projectABalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PROJECT&projectId="
				+ projectAId + "&materialId=" + fixture.valuedMaterialId()));
		assertDecimal(projectABalance, "quantityOnHand", "11.000000");
		assertDecimal(projectABalance, "inventoryAmount", "147.00");
		JsonNode projectBBalances = data(get(admin, "/api/admin/inventory/balances?ownershipType=PROJECT&projectId="
				+ projectBId + "&materialId=" + fixture.valuedMaterialId()));
		assertThat(projectBBalances.get("total").longValue()).isZero();
		JsonNode publicBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.valuedMaterialId()));
		assertDecimal(publicBalance, "quantityOnHand", "4.000000");
		assertDecimal(publicBalance, "inventoryAmount", "48.00");
	}

	@Test
	void 仓库调拨保留所有权质量追踪并保持企业总价值不变() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_TRF_");
		long targetWarehouseId = createWarehouse(admin, "S23_TRF_TO_" + SEQUENCE.incrementAndGet());
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"100.000000", "10.000000");
		long batchId = insertBatch(fixture.valuedMaterialId(), "S23_TRF_BATCH_" + SEQUENCE.incrementAndGet());
		assignLatestBalanceToBatch(fixture.rawWarehouseId(), fixture.valuedMaterialId(), batchId);

		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				warehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, fixture.valuedMaterialId(),
						fixture.unitId(), batchId, "30.000000"),
				admin));
		JsonNode posted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				Map.of("version", transfer.get("version").longValue(), "idempotencyKey",
						"s23-transfer-post-" + transfer.get("id").longValue()),
				admin));
		assertThat(posted.get("status").asText()).isEqualTo("POSTED");

		JsonNode fromBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.valuedMaterialId() + "&batchId=" + batchId));
		JsonNode toBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId="
				+ targetWarehouseId + "&materialId=" + fixture.valuedMaterialId() + "&batchId=" + batchId));
		assertDecimal(fromBalance, "quantityOnHand", "70.000000");
		assertDecimal(fromBalance, "inventoryAmount", "700.00");
		assertDecimal(toBalance, "quantityOnHand", "30.000000");
		assertDecimal(toBalance, "inventoryAmount", "300.00");
		assertThat(toBalance.get("batchId").longValue()).isEqualTo(batchId);
		assertDecimal(reconciliationValue(fixture.valuedMaterialId()), "totalInventoryAmount", "1000.00");
	}

	@Test
	void 盘点范围锁和盘差审批必须原子过账并释放锁() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_STK_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"10.000000", "9.000000");

		JsonNode stocktake = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				Map.of("businessDate", LocalDate.now().toString(), "scopeType", "WAREHOUSE",
						"warehouseId", fixture.rawWarehouseId(), "reason", "023 盘点锁测试",
						"idempotencyKey", "s23-stocktake-" + SEQUENCE.incrementAndGet()),
				admin));
		JsonNode counting = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktake.get("id").longValue() + "/start",
				Map.of("version", stocktake.get("version").longValue()), admin));
		assertThat(counting.get("status").asText()).isEqualTo("COUNTING");

		ResponseEntity<String> lockedTransfer = exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				warehouseTransferPayload(fixture.rawWarehouseId(), createWarehouse(admin, "S23_STK_TO_"
						+ SEQUENCE.incrementAndGet()), fixture.valuedMaterialId(), fixture.unitId(), null,
						"1.000000"),
				admin);
		assertError(lockedTransfer, HttpStatus.CONFLICT, "INVENTORY_STOCKTAKE_RANGE_LOCKED");

		JsonNode line = counting.get("lines").get(0);
		JsonNode updated = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktake.get("id").longValue() + "/lines",
				Map.of("version", counting.get("version").longValue(), "lines", List.of(Map.of("id",
						line.get("id").longValue(), "version", line.get("version").longValue(), "countedQuantity",
						"8.000000"))),
				admin));
		JsonNode confirmed = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktake.get("id").longValue() + "/confirm-variance",
				Map.of("version", updated.get("version").longValue()), admin));
		JsonNode submitted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + stocktake.get("id").longValue() + "/submit",
				Map.of("version", confirmed.get("version").longValue(), "reason", "023 盘亏审批",
						"idempotencyKey", "s23-stocktake-submit-" + stocktake.get("id").longValue()),
				admin));
		assertThat(submitted.get("approvalSummary").get("sceneCode").asText())
			.isEqualTo("INVENTORY_STOCKTAKE_VARIANCE_POST");
		approveLatestTask("inventory:stocktake:variance-approve");

		JsonNode balance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.valuedMaterialId()));
		assertDecimal(balance, "quantityOnHand", "8.000000");
		assertDecimal(balance, "inventoryAmount", "72.00");
		assertThat(countRows("inv_stocktake_range_lock", "stocktake_id = ? and released_at is null",
				stocktake.get("id").longValue())).isZero();
	}

	@Test
	void 成本权限脱敏历史未估值和非计价物料不得伪造金额() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PERM_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"5.000000", "8.000000");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.nonValuedMaterialId(), fixture.unitId(),
				"6.000000", null);

		AuthenticatedSession quantityOnly = createUserAndLogin("stage023-quantity-only-", "S23_QTY_",
				List.of("inventory:balance:view", "inventory:movement:view"));
		JsonNode restrictedBalance = firstItem(get(quantityOnly, "/api/admin/inventory/balances?materialId="
				+ fixture.valuedMaterialId()));
		assertThat(restrictedBalance.get("costVisible").booleanValue()).isFalse();
		assertThat(restrictedBalance.get("inventoryAmount").isNull()).isTrue();
		assertThat(restrictedBalance.get("averageUnitCost").isNull()).isTrue();
		assertThat(restrictedBalance.has("costLayerAmount")).isFalse();

		JsonNode nonValued = firstItem(get(admin, "/api/admin/inventory/balances?materialId="
				+ fixture.nonValuedMaterialId()));
		assertThat(nonValued.get("valuationState").asText()).isEqualTo("NON_VALUED");
		assertThat(nonValued.get("inventoryAmount").isNull()).isTrue();
		assertThat(nonValued.get("averageUnitCost").isNull()).isTrue();
		JsonNode movements = data(get(admin, "/api/admin/inventory/movements?materialId="
				+ fixture.nonValuedMaterialId()));
		assertThat(movements.get("items").get(0).get("inventoryAmount").isNull()).isTrue();
	}

	@Test
	void 并发出库必须只有一个成功且不会产生负库存负金额或重复价值流水() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_CONC_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"10.000000", "10.000000");
		JsonNode first = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 并发出库一",
				List.of(line(1, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "7.000000",
						"DECREASE", null)))));
		JsonNode second = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 并发出库二",
				List.of(line(1, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "7.000000",
						"DECREASE", null)))));

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Callable<ResponseEntity<String>>> calls = List.of(
				() -> postDocumentConcurrently(admin, first, start),
				() -> postDocumentConcurrently(admin, second, start));
		List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
		for (Callable<ResponseEntity<String>> call : calls) {
			futures.add(executor.submit(call));
		}
		start.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
		List<ResponseEntity<String>> responses = new ArrayList<>();
		for (Future<ResponseEntity<String>> future : futures) {
			responses.add(future.get());
		}
		long successCount = responses.stream().filter((response) -> response.getStatusCode() == HttpStatus.OK).count();
		long conflictCount = responses.stream().filter((response) -> response.getStatusCode() == HttpStatus.CONFLICT)
			.count();
		assertThat(successCount).isOne();
		assertThat(conflictCount).isOne();
		ResponseEntity<String> conflict = responses.stream()
			.filter((response) -> response.getStatusCode() == HttpStatus.CONFLICT)
			.findFirst()
			.orElseThrow();
		assertThat(code(conflict)).isEqualTo("INVENTORY_PUBLIC_POOL_INSUFFICIENT");

		JsonNode balance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.valuedMaterialId()));
		assertDecimal(balance, "quantityOnHand", "3.000000");
		assertDecimal(balance, "inventoryAmount", "30.00");
		assertThat(countRows("inv_value_movement",
				"material_id = ? and movement_type = 'ADJUSTMENT_DECREASE' and source_type = 'INVENTORY_DOCUMENT'",
				fixture.valuedMaterialId())).isOne();
	}

	@Test
	void 前端受控库存单据路径必须由后端真实映射支持() {
		List<RequiredRoute> requiredRoutes = List.of(
				route(RequestMethod.GET, "/api/admin/inventory/warehouse-transfers"),
				route(RequestMethod.GET, "/api/admin/inventory/warehouse-transfers/{id}"),
				route(RequestMethod.POST, "/api/admin/inventory/warehouse-transfers"),
				route(RequestMethod.PUT, "/api/admin/inventory/warehouse-transfers/{id}"),
				route(RequestMethod.PUT, "/api/admin/inventory/warehouse-transfers/{id}/post"),
				route(RequestMethod.PUT, "/api/admin/inventory/warehouse-transfers/{id}/cancel"),
				route(RequestMethod.GET, "/api/admin/inventory/ownership-conversions"),
				route(RequestMethod.GET, "/api/admin/inventory/ownership-conversions/{id}"),
				route(RequestMethod.POST, "/api/admin/inventory/ownership-conversions"),
				route(RequestMethod.PUT, "/api/admin/inventory/ownership-conversions/{id}"),
				route(RequestMethod.PUT, "/api/admin/inventory/ownership-conversions/{id}/submit-approval"),
				route(RequestMethod.PUT, "/api/admin/inventory/ownership-conversions/{id}/withdraw"),
				route(RequestMethod.PUT, "/api/admin/inventory/ownership-conversions/{id}/cancel"),
				route(RequestMethod.GET, "/api/admin/inventory/stocktakes"),
				route(RequestMethod.GET, "/api/admin/inventory/stocktakes/{id}"),
				route(RequestMethod.POST, "/api/admin/inventory/stocktakes"),
				route(RequestMethod.PUT, "/api/admin/inventory/stocktakes/{id}/start"),
				route(RequestMethod.PUT, "/api/admin/inventory/stocktakes/{id}/lines"),
				route(RequestMethod.PUT, "/api/admin/inventory/stocktakes/{id}/reconcile"),
				route(RequestMethod.PUT, "/api/admin/inventory/stocktakes/{id}/submit-approval"),
				route(RequestMethod.PUT, "/api/admin/inventory/stocktakes/{id}/complete-zero-variance"),
				route(RequestMethod.PUT, "/api/admin/inventory/stocktakes/{id}/cancel"),
				route(RequestMethod.GET, "/api/admin/inventory/valuation-adjustments"),
				route(RequestMethod.GET, "/api/admin/inventory/valuation-adjustments/{id}"),
				route(RequestMethod.POST, "/api/admin/inventory/valuation-adjustments"),
				route(RequestMethod.PUT, "/api/admin/inventory/valuation-adjustments/{id}"),
				route(RequestMethod.PUT, "/api/admin/inventory/valuation-adjustments/{id}/submit-approval"),
				route(RequestMethod.PUT, "/api/admin/inventory/valuation-adjustments/{id}/withdraw"),
				route(RequestMethod.PUT, "/api/admin/inventory/valuation-adjustments/{id}/cancel"));
		List<String> missingRoutes = new ArrayList<>();
		for (RequiredRoute requiredRoute : requiredRoutes) {
			if (!hasRoute(requiredRoute.method(), requiredRoute.path())) {
				missingRoutes.add(requiredRoute.method().name() + " " + requiredRoute.path());
			}
		}
		assertThat(missingRoutes).as("前端已声明的 023 受控库存单据路径必须全部由后端真实控制器支持")
			.isEmpty();
	}

	private JsonNode firstValueMovement(long materialId, String movementType) throws Exception {
		return data(get(login("admin", ADMIN_PASSWORD), "/api/admin/inventory/movements?materialId=" + materialId
				+ "&movementType=" + movementType + "&page=1&pageSize=20")).get("items").get(0);
	}

	private void seedQuantityWithoutVersion(AuthenticatedSession session, long warehouseId, long materialId, long unitId,
			String quantity, String unitPrice) throws Exception {
		String direction = quantity.startsWith("-") ? "DECREASE" : "INCREASE";
		Map<String, Object> document = documentPayload("ADJUSTMENT", "023 测试种子",
				List.of(line(1, warehouseId, materialId, unitId, quantity.replace("-", ""), direction, unitPrice)));
		JsonNode created = data(createDocument(session, document));
		data(exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + created.get("id").longValue() + "/post",
				null, session));
	}

	private ResponseEntity<String> postDocumentConcurrently(AuthenticatedSession session, JsonNode document,
			CountDownLatch start) throws Exception {
		start.await(10, TimeUnit.SECONDS);
		Object body = document.has("version") ? Map.of("version", document.get("version").longValue()) : null;
		return exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + document.get("id").longValue() + "/post",
				body, session);
	}

	private Map<String, Object> ownershipConversionPayload(String reason, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("reason", reason);
		payload.put("idempotencyKey", "s23-conversion-" + SEQUENCE.incrementAndGet());
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> ownershipLine(int lineNo, String sourceOwnershipType, Long sourceProjectId,
			String targetOwnershipType, Long targetProjectId, long sourceWarehouseId, long targetWarehouseId,
			long materialId, long unitId, String quantity, String sourceUnitCost, Long sourceCostLayerId) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("sourceOwnershipType", sourceOwnershipType);
		line.put("sourceProjectId", sourceProjectId);
		line.put("targetOwnershipType", targetOwnershipType);
		line.put("targetProjectId", targetProjectId);
		line.put("sourceWarehouseId", sourceWarehouseId);
		line.put("targetWarehouseId", targetWarehouseId);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("qualityStatus", "QUALIFIED");
		line.put("quantity", quantity);
		if (sourceUnitCost != null) {
			line.put("sourceUnitCost", sourceUnitCost);
		}
		if (sourceCostLayerId != null) {
			line.put("sourceCostLayerId", sourceCostLayerId);
		}
		return line;
	}

	private Map<String, Object> warehouseTransferPayload(long sourceWarehouseId, long targetWarehouseId,
			long materialId, long unitId, Long batchId, String quantity) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("reason", "023 调拨测试");
		payload.put("idempotencyKey", "s23-transfer-" + SEQUENCE.incrementAndGet());
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("sourceWarehouseId", sourceWarehouseId);
		line.put("targetWarehouseId", targetWarehouseId);
		line.put("ownershipType", "PUBLIC");
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("qualityStatus", "QUALIFIED");
		if (batchId != null) {
			line.put("batchId", batchId);
		}
		line.put("quantity", quantity);
		payload.put("lines", List.of(line));
		return payload;
	}

	private JsonNode reconciliationValue(long materialId) throws Exception {
		return data(get(login("admin", ADMIN_PASSWORD), "/api/admin/inventory/reconciliations?materialId=" + materialId));
	}

	private JsonNode findLayer(JsonNode layers, String unitCost) {
		for (JsonNode layer : layers) {
			if (unitCost.equals(layer.get("unitCost").asText())) {
				return layer;
			}
		}
		throw new AssertionError("未找到成本层单价 " + unitCost + "，实际：" + layers);
	}

	private long insertProject(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, prefix + "CUS_" + suffix, prefix + "客户");
		long userId = userId("admin");
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, current_date, current_date + interval '30 day', 'ACTIVE', 100000.00, 60000.00,
					'test', now(), 'test', now())
				returning id
				""", Long.class, prefix + "PRJ_" + suffix, prefix + "项目", customerId, userId);
	}

	private long userId(String username) {
		return this.jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
	}

	private long insertBatch(long materialId, String batchNo) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_batch (
					material_id, batch_no, source_type, source_id, source_line_id, business_date, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'STAGE023_TEST', 1, 1, current_date, '023 调拨批次', 'test', now(), 'test', now())
				returning id
				""", Long.class, materialId, batchNo);
	}

	private void assignLatestBalanceToBatch(long warehouseId, long materialId, long batchId) {
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set batch_id = ?, updated_at = now()
				where id = (
					select id
					from inv_stock_balance
					where warehouse_id = ?
					  and material_id = ?
					order by id desc
					limit 1
				)
				""", batchId, warehouseId, materialId);
		this.jdbcTemplate.update("""
				update inv_stock_movement
				set batch_id = ?
				where id = (
					select id
					from inv_stock_movement
					where warehouse_id = ?
					  and material_id = ?
					order by id desc
					limit 1
				)
				""", batchId, warehouseId, materialId);
	}

	private void approveLatestTask(String approvePermission) throws Exception {
		AuthenticatedSession approver = createUserAndLogin("stage023-approver-", "S23_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"inventory:balance:view", "inventory:movement:view", "inventory:valuation:view",
						approvePermission));
		JsonNode todo = data(get(approver, "/api/admin/approval-tasks?scope=TODO&page=1&pageSize=1"));
		assertThat(todo.get("total").longValue()).isOne();
		JsonNode task = todo.get("items").get(0);
		data(exchange(HttpMethod.POST, "/api/admin/approval-tasks/" + task.get("taskId").longValue() + "/approve",
				Map.of("version", task.get("version").longValue(), "comment", "023 独立测试同意",
						"idempotencyKey", "s23-approve-" + task.get("taskId").longValue()),
				approver));
	}

	private long countRows(String tableName, String whereSql, Object... args) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereSql,
				Long.class, args);
	}

	private RequiredRoute route(RequestMethod method, String path) {
		return new RequiredRoute(method, path);
	}

	private boolean hasRoute(RequestMethod method, String path) {
		return this.handlerMapping.getHandlerMethods().keySet().stream().anyMatch((mapping) -> {
			var methods = mapping.getMethodsCondition().getMethods();
			var pathCondition = mapping.getPathPatternsCondition();
			return (methods.isEmpty() || methods.contains(method))
					&& pathCondition != null
					&& pathCondition.getPatternValues().contains(path);
		});
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(code(response)).as(response.getBody()).isEqualTo(code);
	}

	private JsonNode lastValueMovement(long materialId, String movementType) throws Exception {
		JsonNode items = data(get(login("admin", ADMIN_PASSWORD), "/api/admin/inventory/movements?materialId="
				+ materialId + "&movementType=" + movementType + "&page=1&pageSize=20")).get("items");
		return items.get(items.size() - 1);
	}

	private InventoryFixture createFixture(String prefix) throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		long unitId = createUnit(admin, prefix + "UNIT_" + suffix);
		long categoryId = createCategory(admin, prefix + "CAT_" + suffix);
		long warehouseId = createWarehouse(admin, prefix + "WH_" + suffix);
		long valuedMaterialId = createMaterial(admin, prefix + "MAT_" + suffix, categoryId, unitId,
				"VALUATED_MATERIAL", true);
		long nonValuedMaterialId = createMaterial(admin, prefix + "NVAL_" + suffix, categoryId, unitId,
				"NON_VALUATED_CONSUMABLE", false);
		return new InventoryFixture(unitId, categoryId, warehouseId, valuedMaterialId, nonValuedMaterialId);
	}

	private long createUnit(AuthenticatedSession session, String code) throws Exception {
		return data(exchange(HttpMethod.POST, "/api/admin/master/units",
				Map.of("code", code, "name", code + "单位", "precisionScale", 6, "sortOrder", 10, "status",
						"ENABLED"),
				session)).get("id").longValue();
	}

	private long createCategory(AuthenticatedSession session, String code) throws Exception {
		return data(exchange(HttpMethod.POST, "/api/admin/master/material-categories",
				Map.of("code", code, "name", code + "分类", "status", "ENABLED", "sortOrder", 10), session))
			.get("id")
			.longValue();
	}

	private long createWarehouse(AuthenticatedSession session, String code) throws Exception {
		return data(exchange(HttpMethod.POST, "/api/admin/master/warehouses",
				Map.of("code", code, "name", code + "仓库", "warehouseType", "NORMAL", "status", "ENABLED"),
				session)).get("id").longValue();
	}

	private long createMaterial(AuthenticatedSession session, String code, long categoryId, long unitId,
			String valuationCategory, boolean valued) throws Exception {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("code", code);
		payload.put("name", code + "物料");
		payload.put("specification", "S");
		payload.put("materialType", "RAW_MATERIAL");
		payload.put("sourceType", "PURCHASED");
		payload.put("categoryId", categoryId);
		payload.put("unitId", unitId);
		payload.put("status", "ENABLED");
		payload.put("costCategory", "DIRECT_MATERIAL");
		payload.put("inventoryValuationCategory", valuationCategory);
		payload.put("inventoryValueEnabled", valued);
		payload.put("projectCostEnabled", valued);
		payload.put("costRemark", "023 独立集成测试");
		return data(exchange(HttpMethod.POST, "/api/admin/master/materials", payload, session)).get("id").longValue();
	}

	private Map<String, Object> documentPayload(String documentType, String reason, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("documentType", documentType);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("reason", reason);
		payload.put("idempotencyKey", "stage023-doc-" + SEQUENCE.incrementAndGet());
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> line(int lineNo, long warehouseId, long materialId, long unitId, String quantity,
			String adjustmentDirection, String unitPrice) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("lineNo", lineNo);
		payload.put("warehouseId", warehouseId);
		payload.put("materialId", materialId);
		payload.put("unitId", unitId);
		payload.put("quantity", quantity);
		if (adjustmentDirection != null) {
			payload.put("adjustmentDirection", adjustmentDirection);
		}
		if (unitPrice != null) {
			payload.put("unitPrice", unitPrice);
		}
		return payload;
	}

	private JsonNode postDocument(AuthenticatedSession session, JsonNode document) throws Exception {
		assertThat(document.get("version")).as("023 单据动作必须返回并携带版本，实际响应：" + document).isNotNull();
		long documentId = document.get("id").longValue();
		long version = document.get("version").longValue();
		return data(exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + documentId + "/post",
				Map.of("version", version), session));
	}

	private ResponseEntity<String> createDocument(AuthenticatedSession session, Map<String, Object> payload) {
		return exchange(HttpMethod.POST, "/api/admin/inventory/documents", payload, session);
	}

	private ResponseEntity<String> get(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET, entity(null, session.sessionCookie(), null),
				String.class);
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

	private JsonNode firstItem(ResponseEntity<String> response) throws Exception {
		JsonNode items = data(response).get("items");
		assertThat(items).isNotNull();
		assertThat(items.size()).as(response.getBody()).isPositive();
		return items.get(0);
	}

	private JsonNode data(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode body = this.objectMapper.readTree(response.getBody());
		assertThat(body.get("code").asText()).as(response.getBody()).isEqualTo("OK");
		return body.get("data");
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertThat(node.has(field)).as("缺少字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(new BigDecimal(node.get(field).asText()).compareTo(new BigDecimal(expected)))
			.as(field + " 实际值 " + node.get(field).asText())
			.isZero();
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
		JsonNode data = this.objectMapper.readTree(response.getBody()).get("data");
		return new CsrfSession(sessionCookie(response), data.get("token").asText(), data.get("headerName").asText());
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) throws Exception {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "023 测试角色" + suffix, "023 测试角色");
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

	private record InventoryFixture(long unitId, long categoryId, long rawWarehouseId, long valuedMaterialId,
			long nonValuedMaterialId) {
	}

	private record RequiredRoute(RequestMethod method, String path) {
	}

}
