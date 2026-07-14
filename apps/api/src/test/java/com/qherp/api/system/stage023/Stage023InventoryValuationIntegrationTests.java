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

import org.assertj.core.api.SoftAssertions;

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

		assertProjectBalanceTotal(admin, projectAId, fixture.valuedMaterialId(), "11.000000", "132.00");
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
	void 跨仓公共池单仓出清不得清空全池且最终全池出清才吸收尾差() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_POOL_");
		long targetWarehouseId = createWarehouse(admin, "S23_POOL_TO_" + SEQUENCE.incrementAndGet());
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"3.000000", "3.333333");

		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				warehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, fixture.valuedMaterialId(),
						fixture.unitId(), null, "1.000000"),
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				actionBody(transfer, "跨仓调拨"), admin));

		JsonNode sourceOut = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 单仓出清但全池未出清",
				List.of(line(1, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
						"2.000000", "DECREASE", null)))));
		postDocument(admin, sourceOut);

		assertDecimal(reconciliationValue(fixture.valuedMaterialId()), "totalInventoryAmount", "3.33");
		JsonNode targetBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId="
				+ targetWarehouseId + "&materialId=" + fixture.valuedMaterialId()));
		assertDecimal(targetBalance, "quantityOnHand", "1.000000");
		assertDecimal(targetBalance, "inventoryAmount", "3.33");

		JsonNode finalOut = data(createDocument(admin, documentPayload("ADJUSTMENT", "023 全池最终出清吸收尾差",
				List.of(line(1, targetWarehouseId, fixture.valuedMaterialId(), fixture.unitId(), "1.000000",
						"DECREASE", null)))));
		postDocument(admin, finalOut);

		assertDecimal(reconciliationValue(fixture.valuedMaterialId()), "totalInventoryAmount", "0.00");
		assertDecimal(firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.valuedMaterialId() + "&includeZero=true")), "quantityOnHand", "0.000000");
	}

	@Test
	void 公共转项目不得使用客户端成本覆盖公共池真实平均价() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_OWN_COST_");
		long projectId = insertProject("S23_OWN_COST_P_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"10.000000", "10.000000");

		JsonNode conversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("客户端伪造成本不得生效", List.of(
						ownershipLine(1, "PUBLIC", null, "PROJECT", projectId, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "2.000000",
								"999.000000", null))),
				admin));
		JsonNode submitted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue() + "/submit",
				actionBody(conversion, "提交客户端伪造成本转换"), admin));
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		approveLatestTask("inventory:ownership-conversion:post-approve");

		JsonNode layer = firstItem(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&materialId=" + fixture.valuedMaterialId()));
		assertDecimal(layer, "unitCost", "10.000000");
		assertDecimal(layer, "remainingAmount", "20.00");
		JsonNode publicBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&materialId="
				+ fixture.valuedMaterialId()));
		assertDecimal(publicBalance, "inventoryAmount", "80.00");
	}

	@Test
	void 项目仓库调拨必须携带并保留实际成本层价值() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PROJECT_TRF_");
		long projectId = insertProject("S23_PROJECT_TRF_P_");
		long targetWarehouseId = createWarehouse(admin, "S23_PROJECT_TRF_TO_" + SEQUENCE.incrementAndGet());
		JsonNode layer = createProjectLayerFromPublic(admin, fixture, projectId, "4.000000", "12.000000");

		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				projectWarehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, projectId,
						fixture.valuedMaterialId(), fixture.unitId(), "1.000000", layer.get("id").longValue()),
				admin));
		ResponseEntity<String> posted = exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				actionBody(transfer, "项目库存调拨保留层"), admin);
		assertThat(posted.getStatusCode()).as(posted.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode targetBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PROJECT&projectId="
				+ projectId + "&warehouseId=" + targetWarehouseId + "&materialId=" + fixture.valuedMaterialId()));
		assertDecimal(targetBalance, "inventoryAmount", "12.00");
		assertThat(targetBalance.get("costLayerId").longValue()).isEqualTo(layer.get("id").longValue());
		assertDecimal(reconciliationValue(fixture.valuedMaterialId()), "totalInventoryAmount", "48.00");
	}

	@Test
	void 锁定期间必须拒绝仓库调拨直接过账() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PERIOD_TRF_");
		LocalDate date = lockedBusinessDate();
		long targetWarehouseId = createWarehouse(admin, "S23_PERIOD_TRF_TO_" + SEQUENCE.incrementAndGet());
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"3.000000", "10.000000");
		Map<String, Object> payload = warehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId,
				fixture.valuedMaterialId(), fixture.unitId(), null, "1.000000");
		payload.put("businessDate", date.toString());
		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers", payload, admin));

		lockPeriod(date);

		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				actionBody(transfer, "锁定期间调拨"), admin), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	@Test
	void 锁定期间必须拒绝所有权转换审批过账并回滚审批动作() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PERIOD_OWN_");
		LocalDate date = lockedBusinessDate();
		long projectId = insertProject("S23_PERIOD_OWN_P_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"3.000000", "10.000000");
		Map<String, Object> payload = ownershipConversionPayload("锁定期间所有权转换", List.of(
				ownershipLine(1, "PUBLIC", null, "PROJECT", projectId, fixture.rawWarehouseId(),
						fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "1.000000",
						null, null)));
		payload.put("businessDate", date.toString());
		JsonNode conversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions", payload,
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue() + "/submit",
				actionBody(conversion, "锁定期间所有权转换审批"), admin));

		lockPeriod(date);

		ResponseEntity<String> approved = actOnLatestTask("inventory:ownership-conversion:post-approve",
				"approve", "锁定期间不得通过");
		assertError(approved, HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertThat(status("inv_ownership_conversion", conversion.get("id").longValue())).isEqualTo("SUBMITTED");
		assertThat(countRows("inv_project_cost_layer", "project_id = ?", projectId)).isZero();
	}

	@Test
	void 锁定期间必须拒绝盘差审批过账并保留原盘点状态() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PERIOD_STK_");
		LocalDate date = lockedBusinessDate();
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"5.000000", "6.000000");
		JsonNode submitted = submitVarianceStocktake(admin, fixture, date, "4.000000");

		lockPeriod(date);

		ResponseEntity<String> approved = actOnLatestTask("inventory:stocktake:variance-approve", "approve",
				"锁定期间不得通过盘差");
		assertError(approved, HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertThat(status("inv_stocktake", submitted.get("id").longValue())).isEqualTo("SUBMITTED");
		assertThat(countRows("inv_value_movement", "source_type = 'STOCKTAKE' and source_id = ?",
				submitted.get("id").longValue())).isZero();
	}

	@Test
	void 锁定期间必须拒绝估值调整审批过账并保留历史未估值池() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PERIOD_VAL_");
		LocalDate date = lockedBusinessDate();
		insertLegacyUnvaluedPublicStock(fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"2.000000");
		Map<String, Object> payload = valuationAdjustmentPayload(fixture.valuedMaterialId(), "2.000000",
				"8.000000", "16.00");
		payload.put("businessDate", date.toString());
		JsonNode adjustment = data(exchange(HttpMethod.POST, "/api/admin/inventory/valuation-adjustments", payload,
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/valuation-adjustments/" + adjustment.get("id").longValue() + "/submit",
				actionBody(adjustment, "锁定期间估值调整审批"), admin));

		lockPeriod(date);

		ResponseEntity<String> approved = actOnLatestTask("inventory:valuation-adjustment:post-approve",
				"approve", "锁定期间不得通过估值");
		assertError(approved, HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertThat(status("inv_valuation_adjustment", adjustment.get("id").longValue())).isEqualTo("SUBMITTED");
		assertThat(publicPoolState(fixture.valuedMaterialId())).isEqualTo("LEGACY_UNVALUED");
	}

	@Test
	void 审批驳回必须回写业务状态并释放盘点范围锁() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture ownershipFixture = createFixture("S23_REJECT_OWN_");
		long projectId = insertProject("S23_REJECT_OWN_P_");
		seedQuantityWithoutVersion(admin, ownershipFixture.rawWarehouseId(), ownershipFixture.valuedMaterialId(),
				ownershipFixture.unitId(), "2.000000", "10.000000");
		JsonNode ownership = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("所有权转换驳回", List.of(
						ownershipLine(1, "PUBLIC", null, "PROJECT", projectId, ownershipFixture.rawWarehouseId(),
								ownershipFixture.rawWarehouseId(), ownershipFixture.valuedMaterialId(),
								ownershipFixture.unitId(), "1.000000", null, null))),
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + ownership.get("id").longValue() + "/submit",
				actionBody(ownership, "提交后驳回所有权转换"), admin));
		data(actOnLatestTask("inventory:ownership-conversion:post-approve", "reject", "驳回所有权转换"));

		InventoryFixture stocktakeFixture = createFixture("S23_REJECT_STK_");
		seedQuantityWithoutVersion(admin, stocktakeFixture.rawWarehouseId(), stocktakeFixture.valuedMaterialId(),
				stocktakeFixture.unitId(), "5.000000", "6.000000");
		JsonNode stocktake = submitVarianceStocktake(admin, stocktakeFixture, LocalDate.now(), "4.000000");
		data(actOnLatestTask("inventory:stocktake:variance-approve", "reject", "驳回盘差"));

		InventoryFixture valuationFixture = createFixture("S23_REJECT_VAL_");
		insertLegacyUnvaluedPublicStock(valuationFixture.rawWarehouseId(), valuationFixture.valuedMaterialId(),
				valuationFixture.unitId(), "2.000000");
		JsonNode adjustment = data(exchange(HttpMethod.POST, "/api/admin/inventory/valuation-adjustments",
				valuationAdjustmentPayload(valuationFixture.valuedMaterialId(), "2.000000", "8.000000", "16.00"),
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/valuation-adjustments/" + adjustment.get("id").longValue() + "/submit",
				actionBody(adjustment, "提交后驳回估值"), admin));
		data(actOnLatestTask("inventory:valuation-adjustment:post-approve", "reject", "驳回估值"));

		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(status("inv_ownership_conversion", ownership.get("id").longValue()))
				.as("所有权转换驳回后应回到可修订草稿")
				.isEqualTo("DRAFT");
			softly.assertThat(status("inv_stocktake", stocktake.get("id").longValue()))
				.as("盘差驳回后不得继续停留 SUBMITTED")
				.isNotEqualTo("SUBMITTED");
			softly.assertThat(countRows("inv_stocktake_range_lock", "stocktake_id = ? and released_at is null",
					stocktake.get("id").longValue()))
				.as("盘差驳回后必须释放盘点锁")
				.isZero();
			softly.assertThat(status("inv_valuation_adjustment", adjustment.get("id").longValue()))
				.as("估值调整驳回后应回到可修订草稿")
				.isEqualTo("DRAFT");
		});
	}

	@Test
	void 无成本权限读取受控单据详情不得泄露行级成本字段() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_DETAIL_MASK_");
		long projectId = insertProject("S23_DETAIL_MASK_P_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"2.000000", "10.000000");
		JsonNode conversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("详情脱敏所有权转换", List.of(
						ownershipLine(1, "PUBLIC", null, "PROJECT", projectId, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "1.000000",
								"888.000000", null))),
				admin));
		insertLegacyUnvaluedPublicStock(fixture.rawWarehouseId(), fixture.nonValuedMaterialId(), fixture.unitId(),
				"1.000000");
		JsonNode adjustment = data(exchange(HttpMethod.POST, "/api/admin/inventory/valuation-adjustments",
				valuationAdjustmentPayload(fixture.nonValuedMaterialId(), "1.000000", "5.000000", "5.00"),
				admin));
		AuthenticatedSession noValuation = createUserAndLogin("stage023-no-valuation-", "S23_NOVAL_",
				List.of("inventory:balance:view", "inventory:movement:view", "inventory:ownership-conversion:view",
						"inventory:valuation-adjustment:view"));

		JsonNode restrictedConversion = data(get(noValuation,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue()));
		JsonNode restrictedAdjustment = data(get(noValuation,
				"/api/admin/inventory/valuation-adjustments/" + adjustment.get("id").longValue()));

		assertThat(restrictedConversion.get("lines").get(0).has("sourceUnitCost"))
			.as("无 inventory:valuation:view 时所有权转换详情不得返回 sourceUnitCost")
			.isFalse();
		assertThat(restrictedAdjustment.get("lines").get(0).has("unitCost"))
			.as("无 inventory:valuation:view 时估值详情不得返回 unitCost")
			.isFalse();
		assertThat(restrictedAdjustment.get("lines").get(0).has("adjustmentAmount"))
			.as("无 inventory:valuation:view 时估值详情不得返回 adjustmentAmount")
			.isFalse();
	}

	@Test
	void 重复或重叠盘点范围锁必须被拒绝() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_LOCK_DUP_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"2.000000", "10.000000");
		JsonNode first = startStocktake(admin, fixture, LocalDate.now());
		JsonNode secondDraft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(), LocalDate.now(), "重复范围盘点"),
				admin));

		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + secondDraft.get("id").longValue() + "/start",
				actionBody(secondDraft, "重复范围盘点开始"), admin), HttpStatus.CONFLICT,
				"INVENTORY_STOCKTAKE_RANGE_LOCKED");
		assertThat(countRows("inv_stocktake_range_lock", "stocktake_id = ? and released_at is null",
				first.get("id").longValue())).isOne();
	}

	@Test
	void 已提交所有权转换在盘点期间审批必须失败且原子回滚() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_APPROVAL_LOCK_");
		long projectId = insertProject("S23_APPROVAL_LOCK_P_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"3.000000", "10.000000");
		JsonNode conversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("盘点期间审批转换", List.of(
						ownershipLine(1, "PUBLIC", null, "PROJECT", projectId, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "1.000000",
								null, null))),
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue() + "/submit",
				actionBody(conversion, "提交后再开启盘点锁"), admin));
		startStocktake(admin, fixture, LocalDate.now());

		ResponseEntity<String> approved = actOnLatestTask("inventory:ownership-conversion:post-approve",
				"approve", "盘点期间不得通过");
		assertError(approved, HttpStatus.CONFLICT, "INVENTORY_STOCKTAKE_RANGE_LOCKED");
		assertThat(status("inv_ownership_conversion", conversion.get("id").longValue())).isEqualTo("SUBMITTED");
		assertThat(countRows("inv_project_cost_layer", "project_id = ?", projectId)).isZero();
	}

	@Test
	void allowedActions必须按权限状态和盘差计算() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_ACTION_");
		long targetWarehouseId = createWarehouse(admin, "S23_ACTION_TO_" + SEQUENCE.incrementAndGet());
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"3.000000", "10.000000");
		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				warehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, fixture.valuedMaterialId(),
						fixture.unitId(), null, "1.000000"),
				admin));
		AuthenticatedSession viewOnly = createUserAndLogin("stage023-action-view-", "S23_ACTION_VIEW_",
				List.of("inventory:warehouse-transfer:view", "inventory:stocktake:view", "inventory:balance:view"));
		JsonNode viewOnlyTransfer = data(get(viewOnly,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue()));

		InventoryFixture zeroFixture = createFixture("S23_ACTION_ZERO_");
		seedQuantityWithoutVersion(admin, zeroFixture.rawWarehouseId(), zeroFixture.valuedMaterialId(),
				zeroFixture.unitId(), "2.000000", "10.000000");
		JsonNode zeroReconciled = reconcileStocktake(admin, zeroFixture, LocalDate.now(), "2.000000");

		InventoryFixture varianceFixture = createFixture("S23_ACTION_VAR_");
		seedQuantityWithoutVersion(admin, varianceFixture.rawWarehouseId(), varianceFixture.valuedMaterialId(),
				varianceFixture.unitId(), "2.000000", "10.000000");
		JsonNode varianceReconciled = reconcileStocktake(admin, varianceFixture, LocalDate.now(), "1.000000");

		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(actions(viewOnlyTransfer))
				.as("只有 view 权限的用户不得看到变更动作")
				.doesNotContain("UPDATE", "POST", "CANCEL");
			softly.assertThat(actions(zeroReconciled))
				.as("零盘差不得展示提交审批")
				.doesNotContain("SUBMIT_APPROVAL");
			softly.assertThat(actions(zeroReconciled))
				.as("零盘差应展示直接完成")
				.contains("COMPLETE_ZERO_VARIANCE");
			softly.assertThat(actions(varianceReconciled))
				.as("有盘差不得展示零差异完成")
				.doesNotContain("COMPLETE_ZERO_VARIANCE");
			softly.assertThat(actions(varianceReconciled))
				.as("有盘差应展示提交审批")
				.contains("SUBMIT_APPROVAL");
		});
	}

	@Test
	void 项目库存质量转换必须保留项目层价值() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture projectFixture = createFixture("S23_QUALITY_PROJECT_");
		long projectId = insertProject("S23_QUALITY_PROJECT_P_");
		JsonNode layer = createProjectLayerFromPublic(admin, projectFixture, projectId, "2.000000", "10.000000");
		Map<String, Object> freeze = qualityTransferPayload(projectFixture.rawWarehouseId(),
				projectFixture.valuedMaterialId(), projectFixture.unitId(), "1.000000", "项目库存冻结");
		freeze.put("ownershipType", "PROJECT");
		freeze.put("projectId", projectId);
		freeze.put("costLayerId", layer.get("id").longValue());

		ResponseEntity<String> frozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				freeze, admin);
		assertThat(frozen.getStatusCode()).as(frozen.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode frozenBalance = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PROJECT&projectId="
				+ projectId + "&materialId=" + projectFixture.valuedMaterialId() + "&qualityStatus=FROZEN"));
		assertDecimal(frozenBalance, "inventoryAmount", "10.00");
		assertThat(frozenBalance.get("costLayerId").longValue()).isEqualTo(layer.get("id").longValue());
	}

	@Test
	void 非计价物料不生成伪零金额价值流水() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture nonValuedFixture = createFixture("S23_NON_VALUE_MOVE_");
		JsonNode nonValuedOpening = data(createDocument(admin, documentPayload("OPENING", "非计价物料数量入库",
				List.of(line(1, nonValuedFixture.rawWarehouseId(), nonValuedFixture.nonValuedMaterialId(),
						nonValuedFixture.unitId(), "1.000000", null, null)))));
		postDocument(admin, nonValuedOpening);
		assertThat(countRows("inv_value_movement", "material_id = ?", nonValuedFixture.nonValuedMaterialId()))
			.as("非计价物料不得生成伪零金额价值流水")
			.isZero();
	}

	@Test
	void 流水筛选必须返回价值追溯字段且十进制字段使用字符串契约() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_MOVEMENT_CONTRACT_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"4.000000", "2.500000");

		JsonNode movement = firstItem(get(admin, "/api/admin/inventory/movements?materialId="
				+ fixture.valuedMaterialId() + "&movementType=ADJUSTMENT_INCREASE&direction=IN&dateFrom="
				+ LocalDate.now() + "&dateTo=" + LocalDate.now()));

		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(movement.get("ownershipType").asText()).isEqualTo("PUBLIC");
			softly.assertThat(movement.has("valuationState")).isTrue();
			softly.assertThat(movement.has("valuationMethod")).isTrue();
			softly.assertThat(movement.has("originalValueMovementId")).isTrue();
			softly.assertThat(movement.get("quantity").isTextual()).as("quantity 必须是十进制字符串").isTrue();
			softly.assertThat(movement.get("unitCost").isTextual()).as("unitCost 必须是十进制字符串").isTrue();
			softly.assertThat(movement.get("inventoryAmount").isTextual()).as("inventoryAmount 必须是十进制字符串")
				.isTrue();
			softly.assertThat(movement.get("unitCost").asText()).isEqualTo("2.500000");
			softly.assertThat(movement.get("inventoryAmount").asText()).isEqualTo("10.00");
		});
	}

	@Test
	void 显式公共质量冻结不得消费同仓同料项目库存() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_QUALITY_PUBLIC_");
		long projectId = insertProject("S23_QUALITY_PUBLIC_P_");
		JsonNode projectLayer = createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "12.000000");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"5.000000", "10.000000");
		Map<String, Object> freeze = qualityTransferPayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "1.000000", "显式公共库存冻结");
		freeze.put("ownershipType", "PUBLIC");

		ResponseEntity<String> frozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				freeze, admin);

		assertThat(frozen.getStatusCode()).as(frozen.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode publicFrozen = firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId="
				+ fixture.rawWarehouseId() + "&materialId=" + fixture.valuedMaterialId()
				+ "&qualityStatus=FROZEN"));
		JsonNode projectQualified = firstItem(get(admin,
				"/api/admin/inventory/balances?ownershipType=PROJECT&projectId=" + projectId + "&warehouseId="
						+ fixture.rawWarehouseId() + "&materialId=" + fixture.valuedMaterialId()
						+ "&qualityStatus=QUALIFIED"));
		assertDecimal(publicFrozen, "quantityOnHand", "1.000000");
		assertDecimal(publicFrozen, "inventoryAmount", "10.00");
		assertDecimal(projectQualified, "quantityOnHand", "2.000000");
		assertDecimal(projectQualified, "inventoryAmount", "24.00");
		assertThat(projectQualified.get("costLayerId").longValue()).isEqualTo(projectLayer.get("id").longValue());
	}

	@Test
	void 同仓同料公共和项目库存并存时含糊质量冻结必须稳定拒绝() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_QUALITY_AMBIG_");
		long projectId = insertProject("S23_QUALITY_AMBIG_P_");
		createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "12.000000");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"5.000000", "10.000000");

		ResponseEntity<String> ambiguous = exchange(HttpMethod.POST,
				"/api/admin/inventory/quality-transfers/freeze",
				qualityTransferPayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
						"1.000000", "含糊所有权冻结"),
				admin);

		assertError(ambiguous, HttpStatus.CONFLICT, "INVENTORY_OWNERSHIP_PROJECT_MISMATCH");
	}

	@Test
	void 销售公共预留释放和出库不得污染同仓同料项目双成本层() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PUBLIC_RESERVE_");
		long projectId = insertProject("S23_PUBLIC_RESERVE_P_");
		markMaterialSellable(fixture.valuedMaterialId());
		createProjectLayerFromPublic(admin, fixture, projectId, "3.000000", "10.000000");
		createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "12.000000");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"10.000000", "11.000000");
		JsonNode layers = projectCostLayers(admin, projectId, fixture.valuedMaterialId());
		JsonNode layerA = findLayer(layers, "10.000000");
		JsonNode layerB = findLayer(layers, "12.000000");
		long customerId = insertCustomer("S23_PUBLIC_RESERVE_C_");

		JsonNode cancelOrder = createSalesOrder(admin, customerId, fixture.rawWarehouseId(),
				fixture.valuedMaterialId(), fixture.unitId(), "4.000000");
		long cancelOrderId = cancelOrder.get("id").longValue();
		data(exchange(HttpMethod.PUT, "/api/admin/sales/orders/" + cancelOrderId + "/confirm", null, admin));
		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(reservationOwnership(cancelOrderId))
			.as("销售公共预留必须显式保持 PUBLIC 且 projectId 为空")
			.isEqualTo("PUBLIC:NULL");
		assertBalanceQuantities(softly, "公共预留后 PUBLIC", publicBalance(admin, fixture.rawWarehouseId(),
				fixture.valuedMaterialId()), "10.000000", "4.000000", "4.000000", "0.000000", "6.000000");
		assertProjectLayerQuantities(softly, "公共预留后项目层A", admin, fixture.rawWarehouseId(), projectId,
				fixture.valuedMaterialId(), layerA.get("id").longValue(), "3.000000", "0.000000", "0.000000",
				"0.000000", "3.000000");
		assertProjectLayerQuantities(softly, "公共预留后项目层B", admin, fixture.rawWarehouseId(), projectId,
				fixture.valuedMaterialId(), layerB.get("id").longValue(), "2.000000", "0.000000", "0.000000",
				"0.000000", "2.000000");

		data(exchange(HttpMethod.PUT, "/api/admin/sales/orders/" + cancelOrderId + "/cancel", null, admin));
		assertBalanceQuantities(softly, "公共取消后 PUBLIC", publicBalance(admin, fixture.rawWarehouseId(),
				fixture.valuedMaterialId()), "10.000000", "0.000000", "0.000000", "0.000000", "10.000000");
		assertProjectLayerQuantities(softly, "公共取消后项目层A", admin, fixture.rawWarehouseId(), projectId,
				fixture.valuedMaterialId(), layerA.get("id").longValue(), "3.000000", "0.000000", "0.000000",
				"0.000000", "3.000000");
		assertProjectLayerQuantities(softly, "公共取消后项目层B", admin, fixture.rawWarehouseId(), projectId,
				fixture.valuedMaterialId(), layerB.get("id").longValue(), "2.000000", "0.000000", "0.000000",
				"0.000000", "2.000000");

		JsonNode shipOrder = createSalesOrder(admin, customerId, fixture.rawWarehouseId(),
				fixture.valuedMaterialId(), fixture.unitId(), "3.000000");
		long shipOrderId = shipOrder.get("id").longValue();
		JsonNode confirmed = data(exchange(HttpMethod.PUT, "/api/admin/sales/orders/" + shipOrderId + "/confirm",
				null, admin));
		long orderLineId = confirmed.get("lines").get(0).get("id").longValue();
		JsonNode shipment = data(exchange(HttpMethod.POST, "/api/admin/sales/orders/" + shipOrderId + "/shipments",
				salesShipmentPayload(fixture.rawWarehouseId(), orderLineId, fixture.valuedMaterialId(),
						fixture.unitId(), "2.000000"),
				admin));
		data(exchange(HttpMethod.PUT, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				null, admin));
		assertBalanceQuantities(softly, "公共出库后 PUBLIC", publicBalance(admin, fixture.rawWarehouseId(),
				fixture.valuedMaterialId()), "8.000000", "1.000000", "1.000000", "0.000000", "7.000000");
		assertProjectLayerQuantities(softly, "公共出库后项目层A", admin, fixture.rawWarehouseId(), projectId,
				fixture.valuedMaterialId(), layerA.get("id").longValue(), "3.000000", "0.000000", "0.000000",
				"0.000000", "3.000000");
		assertProjectLayerQuantities(softly, "公共出库后项目层B", admin, fixture.rawWarehouseId(), projectId,
				fixture.valuedMaterialId(), layerB.get("id").longValue(), "2.000000", "0.000000", "0.000000",
				"0.000000", "2.000000");
		softly.assertAll();
	}

	@Test
	void 批次销售必须先建父级聚合预留再按发货分配建立精确子预留() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_BATCH_PARENT_RSV_");
		markMaterialSellable(fixture.valuedMaterialId());
		markMaterialTracking(fixture.valuedMaterialId(), "BATCH");
		long batchAId = seedBatchStock(admin, fixture, "S23-BATCH-RSV-A-" + SEQUENCE.incrementAndGet(),
				"4.000000", "10.000000");
		long batchBId = seedBatchStock(admin, fixture, "S23-BATCH-RSV-B-" + SEQUENCE.incrementAndGet(),
				"4.000000", "10.000000");
		long customerId = insertCustomer("S23_BATCH_PARENT_RSV_C_");

		JsonNode order = createSalesOrder(admin, customerId, fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "3.000000");
		JsonNode confirmed = data(exchange(HttpMethod.PUT,
				"/api/admin/sales/orders/" + order.get("id").longValue() + "/confirm", null, admin));
		long orderLineId = confirmed.get("lines").get(0).get("id").longValue();
		long parentReservationId = parentReservationId("SALES_ORDER", orderLineId);
		JsonNode shipment = data(exchange(HttpMethod.POST,
				"/api/admin/sales/orders/" + order.get("id").longValue() + "/shipments",
				salesShipmentPayload(fixture.rawWarehouseId(), orderLineId, fixture.valuedMaterialId(),
						fixture.unitId(), "2.000000", List.of(trackingByBatch(batchAId, "2.000000"))),
				admin));

		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(reservationIdentity(parentReservationId))
				.as("父级聚合预留必须为空批次/序列且保持 PUBLIC 身份")
				.isEqualTo("PARENT:PUBLIC:NULL:NULL:NULL:NULL");
			softly.assertThat(reservationActiveQuantity(parentReservationId)).as("过账前父级未消费剩余量")
				.isEqualByComparingTo("3.000000");
			softly.assertThat(balanceLockedQuantity(fixture.rawWarehouseId(), fixture.valuedMaterialId(), batchAId,
					null, "PUBLIC", null, null)).as("聚合父级不得锁定批次A余额").isEqualByComparingTo("0.000000");
			softly.assertThat(balanceLockedQuantity(fixture.rawWarehouseId(), fixture.valuedMaterialId(), batchBId,
					null, "PUBLIC", null, null)).as("聚合父级不得锁定批次B余额").isEqualByComparingTo("0.000000");
			softly.assertThat(childReservationCount(parentReservationId)).as("发货草稿只保存追踪分配，过账前不创建子预留")
				.isZero();
		});

		long transferTargetWarehouseId = createWarehouse(admin, "S23_BATCH_PARENT_RSV_TO_" + SEQUENCE.incrementAndGet());
		JsonNode allowedTransfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				warehouseTransferPayload(fixture.rawWarehouseId(), transferTargetWarehouseId,
						fixture.valuedMaterialId(), fixture.unitId(), batchBId, "4.000000"),
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + allowedTransfer.get("id").longValue() + "/post",
				actionBody(allowedTransfer, "源仓总量仍覆盖父级聚合预留"), admin));
		JsonNode squeezedTransfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				warehouseTransferPayload(fixture.rawWarehouseId(), transferTargetWarehouseId,
						fixture.valuedMaterialId(), fixture.unitId(), batchAId, "2.000000"),
				admin));
		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + squeezedTransfer.get("id").longValue() + "/post",
				actionBody(squeezedTransfer, "父级聚合预留保护批次调拨"), admin), HttpStatus.CONFLICT,
				"INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE");

		data(exchange(HttpMethod.PUT, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				null, admin));
		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(reservationActiveQuantity(parentReservationId)).as("发货过账后父级未消费剩余量")
				.isEqualByComparingTo("1.000000");
			softly.assertThat(childReservationCount(parentReservationId)).as("发货过账必须创建精确子预留").isEqualTo(1L);
			softly.assertThat(childReservationIdentity(parentReservationId, batchAId, null))
				.as("批次子级必须继承父级源单和身份")
				.isEqualTo("CHILD:CONSUMED:SALES_ORDER:" + orderLineId + ":PUBLIC:NULL:NULL:" + batchAId
						+ ":NULL:2.000000:2.000000");
			softly.assertThat(activeChildQuantity(parentReservationId)).as("已消费子级不得残留活动锁定")
				.isEqualByComparingTo("0.000000");
		});
		assertDecimal(firstItem(get(admin, "/api/admin/inventory/balances?warehouseId=" + fixture.rawWarehouseId()
				+ "&materialId=" + fixture.valuedMaterialId() + "&batchId=" + batchAId)), "quantityOnHand",
				"2.000000");
		assertDecimal(firstItem(get(admin, "/api/admin/inventory/balances?warehouseId=" + fixture.rawWarehouseId()
				+ "&materialId=" + fixture.valuedMaterialId() + "&batchId=" + batchBId)), "quantityOnHand",
				"0.000000");
	}

	@Test
	void 序列销售父子预留必须要求显式序列且子预留数量固定为一() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_SERIAL_PARENT_RSV_");
		markMaterialSellable(fixture.valuedMaterialId());
		markMaterialTracking(fixture.valuedMaterialId(), "SERIAL");
		long serialAId = seedSerialStock(admin, fixture, "S23-SERIAL-RSV-A-" + SEQUENCE.incrementAndGet(),
				"10.000000");
		long serialBId = seedSerialStock(admin, fixture, "S23-SERIAL-RSV-B-" + SEQUENCE.incrementAndGet(),
				"10.000000");
		long customerId = insertCustomer("S23_SERIAL_PARENT_RSV_C_");

		JsonNode noAllocationOrder = createSalesOrder(admin, customerId, fixture.rawWarehouseId(),
				fixture.valuedMaterialId(), fixture.unitId(), "1.000000");
		JsonNode noAllocationConfirmed = data(exchange(HttpMethod.PUT,
				"/api/admin/sales/orders/" + noAllocationOrder.get("id").longValue() + "/confirm", null, admin));
		long noAllocationLineId = noAllocationConfirmed.get("lines").get(0).get("id").longValue();
		assertError(exchange(HttpMethod.POST,
				"/api/admin/sales/orders/" + noAllocationOrder.get("id").longValue() + "/shipments",
				salesShipmentPayload(fixture.rawWarehouseId(), noAllocationLineId, fixture.valuedMaterialId(),
						fixture.unitId(), "1.000000"),
				admin), HttpStatus.BAD_REQUEST, "INVENTORY_SERIAL_REQUIRED");

		JsonNode order = createSalesOrder(admin, customerId, fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "1.000000");
		JsonNode confirmed = data(exchange(HttpMethod.PUT,
				"/api/admin/sales/orders/" + order.get("id").longValue() + "/confirm", null, admin));
		long orderLineId = confirmed.get("lines").get(0).get("id").longValue();
		long parentReservationId = parentReservationId("SALES_ORDER", orderLineId);
		JsonNode shipment = data(exchange(HttpMethod.POST,
				"/api/admin/sales/orders/" + order.get("id").longValue() + "/shipments",
				salesShipmentPayload(fixture.rawWarehouseId(), orderLineId, fixture.valuedMaterialId(),
						fixture.unitId(), "1.000000", List.of(trackingBySerial(serialAId))),
				admin));
		data(exchange(HttpMethod.PUT, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				null, admin));

		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(reservationIdentity(parentReservationId))
				.as("序列父级聚合预留必须为空追踪身份")
				.isEqualTo("PARENT:PUBLIC:NULL:NULL:NULL:NULL");
			softly.assertThat(childReservationCount(parentReservationId)).as("序列发货必须创建精确子预留").isEqualTo(1L);
			softly.assertThat(childReservationIdentity(parentReservationId, null, serialAId))
				.as("序列子预留必须绑定唯一序列且数量为 1")
				.isEqualTo("CHILD:CONSUMED:SALES_ORDER:" + orderLineId + ":PUBLIC:NULL:NULL:NULL:" + serialAId
						+ ":1.000000:1.000000");
			softly.assertThat(childReservationCountForSerial(serialBId)).as("未选择序列不得产生子预留").isZero();
		});
	}

	@Test
	void 项目批次父级聚合预留必须保护调拨和质量冻结的完整身份总量() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PROJECT_BATCH_PARENT_");
		markMaterialTracking(fixture.valuedMaterialId(), "BATCH");
		long projectId = insertProject("S23_PROJECT_BATCH_PARENT_P_");
		long costLayerId = insertProjectCostLayer(projectId, fixture.valuedMaterialId(), "6.000000", "60.00",
				"10.000000");
		long batchAId = insertBatch(fixture.valuedMaterialId(), "S23-PROJ-BATCH-A-" + SEQUENCE.incrementAndGet());
		long batchBId = insertBatch(fixture.valuedMaterialId(), "S23-PROJ-BATCH-B-" + SEQUENCE.incrementAndGet());
		insertProjectBatchBalance(fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), projectId,
				costLayerId, batchAId, "3.000000", "30.00", "10.000000");
		insertProjectBatchBalance(fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), projectId,
				costLayerId, batchBId, "3.000000", "30.00", "10.000000");
		insertActiveParentReservation("STAGE023_PROJECT_BATCH_PARENT", 9_100_000L + SEQUENCE.incrementAndGet(),
				9_200_000L + SEQUENCE.incrementAndGet(), fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "5.000000", "PROJECT", projectId, costLayerId);
		long targetWarehouseId = createWarehouse(admin, "S23_PROJECT_BATCH_PARENT_TO_" + SEQUENCE.incrementAndGet());

		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				projectWarehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, projectId,
						fixture.valuedMaterialId(), fixture.unitId(), batchAId, "2.000000", costLayerId),
				admin));

		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				actionBody(transfer, "聚合预留保护调拨"), admin), HttpStatus.CONFLICT,
				"INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE");

		Map<String, Object> freeze = qualityTransferPayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "2.000000", "聚合预留保护质量冻结");
		freeze.put("ownershipType", "PROJECT");
		freeze.put("projectId", projectId);
		freeze.put("costLayerId", costLayerId);
		freeze.put("trackingAllocations", List.of(trackingByBatch(batchAId, "2.000000")));
		assertError(exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze", freeze, admin),
				HttpStatus.CONFLICT, "INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE");
	}

	@Test
	void 项目仓库调拨必须按指定成本层移动并保持项目层总价值() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_LAYER_TRANSFER_");
		long projectId = insertProject("S23_LAYER_TRANSFER_P_");
		long targetWarehouseId = createWarehouse(admin, "S23_LAYER_TRANSFER_TO_" + SEQUENCE.incrementAndGet());
		createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "10.000000");
		createProjectLayerFromPublic(admin, fixture, projectId, "3.000000", "20.000000");
		JsonNode layers = data(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&materialId=" + fixture.valuedMaterialId())).get("items");
		JsonNode tenCostLayer = findLayer(layers, "10.000000");
		JsonNode twentyCostLayer = findLayer(layers, "20.000000");

		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				projectWarehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, projectId,
						fixture.valuedMaterialId(), fixture.unitId(), "1.000000",
						twentyCostLayer.get("id").longValue()),
				admin));
		ResponseEntity<String> posted = exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				actionBody(transfer, "指定 20 元成本层调拨"), admin);

		assertThat(posted.getStatusCode()).as(posted.getBody()).isEqualTo(HttpStatus.OK);
		long transferId = transfer.get("id").longValue();
		long selectedCostLayerId = twentyCostLayer.get("id").longValue();
		assertProjectLayer(tenCostLayer.get("id").longValue(), "2.000000", "20.00");
		assertProjectLayer(selectedCostLayerId, "3.000000", "60.00");
		assertProjectWarehouseBalanceTotal(fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"4.000000");
		assertProjectWarehouseBalance(fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				selectedCostLayerId, "2.000000");
		assertProjectWarehouseBalance(targetWarehouseId, projectId, fixture.valuedMaterialId(), selectedCostLayerId,
				"1.000000");
		assertTransferValueMovement(transferId, "WAREHOUSE_TRANSFER_OUT", selectedCostLayerId, "1.000000", "20.00");
		assertTransferValueMovement(transferId, "WAREHOUSE_TRANSFER_IN", selectedCostLayerId, "1.000000", "20.00");
		assertDecimal(reconciliationValue(fixture.valuedMaterialId()), "totalInventoryAmount", "80.00");
	}

	@Test
	void 项目仓库调拨缺失成本层必须稳定拒绝() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture missingLayerFixture = createFixture("S23_LAYER_MISSING_");
		long projectId = insertProject("S23_LAYER_MISSING_P_");
		long targetWarehouseId = createWarehouse(admin, "S23_LAYER_MISSING_TO_" + SEQUENCE.incrementAndGet());
		createProjectLayerFromPublic(admin, missingLayerFixture, projectId, "2.000000", "10.000000");
		Map<String, Object> missingLayerPayload = warehouseTransferPayload(missingLayerFixture.rawWarehouseId(),
				targetWarehouseId, missingLayerFixture.valuedMaterialId(), missingLayerFixture.unitId(), null,
				"1.000000");
		@SuppressWarnings("unchecked")
		Map<String, Object> missingLayerLine = (Map<String, Object>) ((List<?>) missingLayerPayload.get("lines"))
			.get(0);
		missingLayerLine.put("ownershipType", "PROJECT");
		missingLayerLine.put("projectId", projectId);
		JsonNode missingLayerTransfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				missingLayerPayload, admin));

		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + missingLayerTransfer.get("id").longValue() + "/post",
				actionBody(missingLayerTransfer, "缺失成本层不得过账"), admin), HttpStatus.CONFLICT,
				"INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT");
	}

	@Test
	void 项目仓库调拨错误成本层必须稳定拒绝() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture wrongLayerFixture = createFixture("S23_LAYER_WRONG_");
		long sourceProjectId = insertProject("S23_LAYER_WRONG_P_");
		long otherProjectId = insertProject("S23_LAYER_WRONG_OTHER_P_");
		long wrongTargetWarehouseId = createWarehouse(admin, "S23_LAYER_WRONG_TO_" + SEQUENCE.incrementAndGet());
		createProjectLayerFromPublic(admin, wrongLayerFixture, sourceProjectId, "2.000000", "10.000000");
		JsonNode otherLayer = createProjectLayerFromPublic(admin, wrongLayerFixture, otherProjectId, "1.000000",
				"30.000000");
		JsonNode wrongLayerTransfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				projectWarehouseTransferPayload(wrongLayerFixture.rawWarehouseId(), wrongTargetWarehouseId,
						sourceProjectId, wrongLayerFixture.valuedMaterialId(), wrongLayerFixture.unitId(),
						"1.000000", otherLayer.get("id").longValue()),
				admin));

		assertError(exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + wrongLayerTransfer.get("id").longValue() + "/post",
				actionBody(wrongLayerTransfer, "错误成本层不得过账"), admin), HttpStatus.CONFLICT,
				"INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT");
	}

	@Test
	void 同仓同项目同物料多成本层必须在余额和候选中分别返回() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_MULTI_LAYER_BAL_");
		long projectId = insertProject("S23_MULTI_LAYER_BAL_P_");
		createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "10.000000");
		createProjectLayerFromPublic(admin, fixture, projectId, "3.000000", "20.000000");
		JsonNode layers = projectCostLayers(admin, projectId, fixture.valuedMaterialId());
		long layerAId = findLayer(layers, "10.000000").get("id").longValue();
		long layerBId = findLayer(layers, "20.000000").get("id").longValue();

		JsonNode balances = projectBalances(admin, fixture.rawWarehouseId(), projectId,
				fixture.valuedMaterialId(), "QUALIFIED");
		JsonNode candidates = data(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&materialId=" + fixture.valuedMaterialId() + "&warehouseId=" + fixture.rawWarehouseId()
				+ "&page=1&pageSize=20"));

		assertThat(balances.get("total").longValue()).as(balances.toString()).isEqualTo(2);
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerAId, "2.000000", "20.00");
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerBId, "3.000000", "60.00");
		assertThat(candidates.get("total").longValue()).as(candidates.toString()).isEqualTo(2);
		assertThat(layerIds(candidates.get("items"))).containsExactlyInAnyOrder(layerAId, layerBId);
		assertProjectLayer(layerAId, "2.000000", "20.00");
		assertProjectLayer(layerBId, "3.000000", "60.00");
	}

	@Test
	void 多成本层项目仓库调拨必须只移动指定层并保持项目总价值() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_MULTI_LAYER_TRF_");
		long projectId = insertProject("S23_MULTI_LAYER_TRF_P_");
		long targetWarehouseId = createWarehouse(admin, "S23_MULTI_LAYER_TRF_TO_" + SEQUENCE.incrementAndGet());
		createProjectLayerFromPublic(admin, fixture, projectId, "3.000000", "20.000000");
		createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "10.000000");
		JsonNode layers = projectCostLayers(admin, projectId, fixture.valuedMaterialId());
		long layerAId = findLayer(layers, "10.000000").get("id").longValue();
		long layerBId = findLayer(layers, "20.000000").get("id").longValue();

		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				projectWarehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, projectId,
						fixture.valuedMaterialId(), fixture.unitId(), "1.000000", layerAId),
				admin));
		ResponseEntity<String> posted = exchange(HttpMethod.PUT,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue() + "/post",
				actionBody(transfer, "只调拨 10 元成本层"), admin);

		assertThat(posted.getStatusCode()).as(posted.getBody()).isEqualTo(HttpStatus.OK);
		assertProjectLayer(layerAId, "2.000000", "20.00");
		assertProjectLayer(layerBId, "3.000000", "60.00");
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerAId, "1.000000", "10.00");
		assertProjectBalanceByLayer(admin, targetWarehouseId, projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerAId, "1.000000", "10.00");
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerBId, "3.000000", "60.00");
		assertTransferValueMovement(transfer.get("id").longValue(), "WAREHOUSE_TRANSFER_OUT", layerAId,
				"1.000000", "10.00");
		assertTransferValueMovement(transfer.get("id").longValue(), "WAREHOUSE_TRANSFER_IN", layerAId,
				"1.000000", "10.00");
		assertDecimal(reconciliationValue(fixture.valuedMaterialId()), "totalInventoryAmount", "80.00");
	}

	@Test
	void 多成本层项目质量冻结解冻必须只移动指定层() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_MULTI_LAYER_QUAL_");
		long projectId = insertProject("S23_MULTI_LAYER_QUAL_P_");
		createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "10.000000");
		createProjectLayerFromPublic(admin, fixture, projectId, "3.000000", "20.000000");
		JsonNode layers = projectCostLayers(admin, projectId, fixture.valuedMaterialId());
		long layerAId = findLayer(layers, "10.000000").get("id").longValue();
		long layerBId = findLayer(layers, "20.000000").get("id").longValue();

		Map<String, Object> freeze = qualityTransferPayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "1.000000", "只冻结 20 元成本层");
		freeze.put("ownershipType", "PROJECT");
		freeze.put("projectId", projectId);
		freeze.put("costLayerId", layerBId);
		ResponseEntity<String> frozen = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				freeze, admin);

		assertThat(frozen.getStatusCode()).as(frozen.getBody()).isEqualTo(HttpStatus.OK);
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerAId, "2.000000", "20.00");
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerBId, "2.000000", "40.00");
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"FROZEN", layerBId, "1.000000", "20.00");

		Map<String, Object> unfreeze = qualityTransferPayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "1.000000", "只解冻 20 元成本层");
		unfreeze.put("ownershipType", "PROJECT");
		unfreeze.put("projectId", projectId);
		unfreeze.put("costLayerId", layerBId);
		ResponseEntity<String> unfrozen = exchange(HttpMethod.POST,
				"/api/admin/inventory/quality-transfers/unfreeze", unfreeze, admin);

		assertThat(unfrozen.getStatusCode()).as(unfrozen.getBody()).isEqualTo(HttpStatus.OK);
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerAId, "2.000000", "20.00");
		assertProjectBalanceByLayer(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"QUALIFIED", layerBId, "3.000000", "60.00");
		assertProjectBalanceQuantityOrZero(admin, fixture.rawWarehouseId(), projectId, fixture.valuedMaterialId(),
				"FROZEN", layerBId, "0.000000");
		assertProjectLayer(layerAId, "2.000000", "20.00");
		assertProjectLayer(layerBId, "3.000000", "60.00");
		assertDecimal(reconciliationValue(fixture.valuedMaterialId()), "totalInventoryAmount", "80.00");
	}

	@Test
	void 项目质量冻结指定不存在成本层必须稳定拒绝且不能回退任意层() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_QUALITY_UNKNOWN_LAYER_");
		long projectId = insertProject("S23_QUALITY_UNKNOWN_LAYER_P_");
		createProjectLayerFromPublic(admin, fixture, projectId, "2.000000", "10.000000");
		long missingCostLayerId = 9_999_999_999L;
		Map<String, Object> freeze = qualityTransferPayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "1.000000", "不存在成本层不得冻结");
		freeze.put("ownershipType", "PROJECT");
		freeze.put("projectId", projectId);
		freeze.put("costLayerId", missingCostLayerId);

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				freeze, admin);

		assertError(response, HttpStatus.CONFLICT, "INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT");
	}

	@Test
	void 项目质量冻结指定其他项目成本层必须稳定拒绝且不能回退任意层() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_QUALITY_OTHER_LAYER_");
		long sourceProjectId = insertProject("S23_QUALITY_OTHER_LAYER_SRC_");
		long otherProjectId = insertProject("S23_QUALITY_OTHER_LAYER_OTH_");
		createProjectLayerFromPublic(admin, fixture, sourceProjectId, "2.000000", "10.000000");
		JsonNode otherLayer = createProjectLayerFromPublic(admin, fixture, otherProjectId, "1.000000",
				"30.000000");
		Map<String, Object> freeze = qualityTransferPayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(),
				fixture.unitId(), "1.000000", "其他项目成本层不得冻结");
		freeze.put("ownershipType", "PROJECT");
		freeze.put("projectId", sourceProjectId);
		freeze.put("costLayerId", otherLayer.get("id").longValue());

		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/inventory/quality-transfers/freeze",
				freeze, admin);

		assertError(response, HttpStatus.CONFLICT, "INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT");
	}

	@Test
	void 生产工单详情必须返回完工估值状态且按成本权限脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_PRODUCTION_DETAIL_");
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"5.000000", "9.000000");
		long workOrderId = createProductionWorkOrder(fixture);
		AuthenticatedSession noCost = createUserAndLogin("stage023-production-no-cost-", "S23_PROD_NOVAL_",
				List.of("production:work-order:view"));

		JsonNode detail = data(get(admin, "/api/admin/production/work-orders/" + workOrderId));
		JsonNode restricted = data(get(noCost, "/api/admin/production/work-orders/" + workOrderId));

		SoftAssertions.assertSoftly((softly) -> {
			softly.assertThat(detail.has("completionValuationState"))
				.as("生产工单详情必须直接给出完工估值状态")
				.isTrue();
			softly.assertThat(detail.has("requiresManualProvisionalUnitCost"))
				.as("生产工单详情必须直接给出是否需要手工暂估")
				.isTrue();
			softly.assertThat(detail.has("currentAverageUnitCost"))
				.as("生产工单详情必须直接给出当前公共平均成本")
				.isTrue();
			softly.assertThat(detail.has("costVisible")).as("生产工单详情必须返回成本可见标记").isTrue();
			if (detail.has("currentAverageUnitCost") && !detail.get("currentAverageUnitCost").isNull()) {
				softly.assertThat(detail.get("currentAverageUnitCost").asText()).isEqualTo("9.000000");
			}
			if (detail.has("costVisible")) {
				softly.assertThat(detail.get("costVisible").booleanValue()).isTrue();
			}
			softly.assertThat(restricted.has("costVisible")).as("无成本权限详情仍必须返回 costVisible=false").isTrue();
			if (restricted.has("costVisible")) {
				softly.assertThat(restricted.get("costVisible").booleanValue()).isFalse();
			}
			softly.assertThat(restricted.hasNonNull("currentAverageUnitCost"))
				.as("无 inventory:valuation:view 时不得泄露当前平均成本")
				.isFalse();
		});
	}

	@Test
	void 成本层必须按costLayerId精确筛选() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture layerFixture = createFixture("S23_FILTER_LAYER_");
		long projectId = insertProject("S23_FILTER_LAYER_P_");
		createProjectLayerFromPublic(admin, layerFixture, projectId, "2.000000", "10.000000");
		createProjectLayerFromPublic(admin, layerFixture, projectId, "3.000000", "20.000000");
		JsonNode layers = data(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&materialId=" + layerFixture.valuedMaterialId())).get("items");
		long expectedLayerId = findLayer(layers, "20.000000").get("id").longValue();

		JsonNode filteredLayers = data(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&materialId=" + layerFixture.valuedMaterialId() + "&costLayerId=" + expectedLayerId));

		assertThat(filteredLayers.get("total").longValue()).as(filteredLayers.toString()).isOne();
		assertThat(filteredLayers.get("items").get(0).get("id").longValue()).isEqualTo(expectedLayerId);
	}

	@Test
	void 库存流水必须按来源三元组精确筛选() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture movementFixture = createFixture("S23_FILTER_MOVEMENT_");
		JsonNode firstDocument = data(createDocument(admin, documentPayload("OPENING", "来源筛选第一笔",
				List.of(line(1, movementFixture.rawWarehouseId(), movementFixture.valuedMaterialId(),
						movementFixture.unitId(), "1.000000", null, "8.000000")))));
		JsonNode firstPosted = postDocument(admin, firstDocument);
		JsonNode secondDocument = data(createDocument(admin, documentPayload("ADJUSTMENT", "来源筛选第二笔",
				List.of(line(1, movementFixture.rawWarehouseId(), movementFixture.valuedMaterialId(),
						movementFixture.unitId(), "1.000000", "INCREASE", "9.000000")))));
		postDocument(admin, secondDocument);
		long firstDocumentId = firstPosted.get("id").longValue();
		long firstLineId = firstPosted.get("lines").get(0).get("id").longValue();
		JsonNode filteredMovements = data(get(admin, "/api/admin/inventory/movements?materialId="
				+ movementFixture.valuedMaterialId() + "&sourceType=INVENTORY_DOCUMENT&sourceId="
				+ firstDocumentId + "&sourceLineId=" + firstLineId));

		assertThat(filteredMovements.get("total").longValue()).as(filteredMovements.toString()).isOne();
		JsonNode movement = filteredMovements.get("items").get(0);
		assertThat(movement.get("sourceType").asText()).isEqualTo("INVENTORY_DOCUMENT");
		assertThat(movement.get("sourceId").longValue()).isEqualTo(firstDocumentId);
		assertThat(movement.get("sourceLineId").longValue()).isEqualTo(firstLineId);
	}

	@Test
	void 四类受控单据详情必须冻结展示字段且不得回归availableActions() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		InventoryFixture fixture = createFixture("S23_DETAIL_FREEZE_");
		long projectId = insertProject("S23_DETAIL_FREEZE_P_");
		long targetWarehouseId = createWarehouse(admin, "S23_DETAIL_FREEZE_TO_" + SEQUENCE.incrementAndGet());
		JsonNode layer = createProjectLayerFromPublic(admin, fixture, projectId, "3.000000", "10.000000");
		long costLayerId = layer.get("id").longValue();

		JsonNode transfer = data(exchange(HttpMethod.POST, "/api/admin/inventory/warehouse-transfers",
				projectWarehouseTransferPayload(fixture.rawWarehouseId(), targetWarehouseId, projectId,
						fixture.valuedMaterialId(), fixture.unitId(), "1.000000", costLayerId),
				admin));
		JsonNode conversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("详情冻结项目转公共", List.of(
						ownershipLine(1, "PROJECT", projectId, "PUBLIC", null, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), "1.000000",
								null, costLayerId))),
				admin));
		JsonNode stocktake = startStocktake(admin, fixture, LocalDate.now());
		JsonNode adjustment = data(exchange(HttpMethod.POST, "/api/admin/inventory/valuation-adjustments",
				projectValuationAdjustmentPayload(projectId, fixture.valuedMaterialId(), costLayerId,
						"11.000000", "3.00"),
				admin));
		AuthenticatedSession noCost = createUserAndLogin("stage023-detail-no-cost-", "S23_DETAIL_NOVAL_",
				List.of("inventory:warehouse-transfer:view", "inventory:ownership-conversion:view",
						"inventory:stocktake:view", "inventory:valuation-adjustment:view", "inventory:balance:view"));

		JsonNode transferDetail = data(get(admin,
				"/api/admin/inventory/warehouse-transfers/" + transfer.get("id").longValue()));
		JsonNode conversionDetail = data(get(admin,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue()));
		JsonNode stocktakeDetail = data(get(admin, "/api/admin/inventory/stocktakes/" + stocktake.get("id").longValue()));
		JsonNode adjustmentDetail = data(get(admin,
				"/api/admin/inventory/valuation-adjustments/" + adjustment.get("id").longValue()));
		JsonNode restrictedConversion = data(get(noCost,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue()));
		JsonNode restrictedAdjustment = data(get(noCost,
				"/api/admin/inventory/valuation-adjustments/" + adjustment.get("id").longValue()));

		JsonNode transferLine = transferDetail.get("lines").get(0);
		JsonNode conversionLine = conversionDetail.get("lines").get(0);
		JsonNode stocktakeProjectLine = findLineByProject(stocktakeDetail.get("lines"), projectId);
		JsonNode adjustmentLine = adjustmentDetail.get("lines").get(0);
		SoftAssertions.assertSoftly((softly) -> {
			assertOnlyAllowedActions(softly, transferDetail, "仓库调拨详情");
			assertOnlyAllowedActions(softly, conversionDetail, "所有权转换详情");
			assertOnlyAllowedActions(softly, stocktakeDetail, "盘点详情");
			assertOnlyAllowedActions(softly, adjustmentDetail, "估值调整详情");
			assertLineIdentity(softly, transferLine, "仓库调拨行", fixture.valuedMaterialId(), fixture.unitId(),
					projectId);
			softly.assertThat(transferLine.get("sourceCostLayerId").longValue())
				.as("项目仓库调拨详情必须冻结展示 sourceCostLayerId")
				.isEqualTo(costLayerId);
			assertLineIdentity(softly, conversionLine, "所有权转换行", fixture.valuedMaterialId(), fixture.unitId(),
					projectId);
			softly.assertThat(conversionLine.get("sourceCostLayerId").longValue()).isEqualTo(costLayerId);
			assertLineIdentity(softly, stocktakeProjectLine, "盘点项目行", fixture.valuedMaterialId(), fixture.unitId(),
					projectId);
			softly.assertThat(stocktakeProjectLine.has("costLayerId")).as("盘点项目行必须冻结展示成本层").isTrue();
			assertLineIdentity(softly, adjustmentLine, "估值调整行", fixture.valuedMaterialId(), null, projectId);
			softly.assertThat(adjustmentLine.get("costLayerId").longValue()).isEqualTo(costLayerId);
			softly.assertThat(restrictedConversion.get("lines").get(0).has("sourceUnitCost"))
				.as("无成本权限时所有权转换不得返回 sourceUnitCost")
				.isFalse();
			softly.assertThat(restrictedConversion.get("lines").get(0).has("sourceCostLayerId"))
				.as("无成本权限时所有权转换不得返回 sourceCostLayerId")
				.isFalse();
			softly.assertThat(restrictedAdjustment.get("lines").get(0).has("unitCost"))
				.as("无成本权限时估值调整不得返回 unitCost")
				.isFalse();
			softly.assertThat(restrictedAdjustment.get("lines").get(0).has("adjustmentAmount"))
				.as("无成本权限时估值调整不得返回 adjustmentAmount")
				.isFalse();
			softly.assertThat(restrictedAdjustment.get("lines").get(0).has("costLayerId"))
				.as("无成本权限时估值调整不得返回 costLayerId")
				.isFalse();
		});
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

	private void assertProjectLayer(long layerId, String remainingQuantity, String remainingAmount) {
		Map<String, Object> layer = this.jdbcTemplate.queryForMap("""
				select remaining_quantity, remaining_amount
				from inv_project_cost_layer
				where id = ?
				""", layerId);
		assertThat(((BigDecimal) layer.get("remaining_quantity")).compareTo(new BigDecimal(remainingQuantity)))
			.as("成本层 " + layerId + " 剩余数量")
			.isZero();
		assertThat(((BigDecimal) layer.get("remaining_amount")).compareTo(new BigDecimal(remainingAmount)))
			.as("成本层 " + layerId + " 剩余金额")
			.isZero();
	}

	private void assertProjectWarehouseBalance(long warehouseId, long projectId, long materialId, long costLayerId,
			String quantityOnHand) {
		Map<String, Object> balance = this.jdbcTemplate.queryForMap("""
				select ownership_type, project_id, quantity_on_hand, cost_layer_id
				from inv_stock_balance
				where warehouse_id = ?
				  and project_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and cost_layer_id = ?
				""", warehouseId, projectId, materialId, costLayerId);
		assertThat(balance.get("ownership_type")).as("仓库余额所有权").isEqualTo("PROJECT");
		assertThat(((Number) balance.get("project_id")).longValue()).as("仓库余额项目").isEqualTo(projectId);
		assertThat(((Number) balance.get("cost_layer_id")).longValue()).as("仓库余额成本层").isEqualTo(costLayerId);
		assertThat(((BigDecimal) balance.get("quantity_on_hand")).compareTo(new BigDecimal(quantityOnHand)))
			.as("仓库 " + warehouseId + " 项目余额数量")
			.isZero();
	}

	private void assertProjectWarehouseBalanceTotal(long warehouseId, long projectId, long materialId,
			String quantityOnHand) {
		BigDecimal actualQuantity = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where ownership_type = 'PROJECT'
				  and warehouse_id = ?
				  and project_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				""", BigDecimal.class, warehouseId, projectId, materialId);
		assertThat(actualQuantity.compareTo(new BigDecimal(quantityOnHand)))
			.as("仓库 " + warehouseId + " 项目分层余额总数量")
			.isZero();
	}

	private void assertProjectBalanceTotal(AuthenticatedSession admin, long projectId, long materialId,
			String quantityOnHand, String inventoryAmount) throws Exception {
		JsonNode items = data(get(admin, "/api/admin/inventory/balances?ownershipType=PROJECT&projectId="
				+ projectId + "&materialId=" + materialId)).get("items");
		assertThat(items).isNotNull();
		assertThat(items.size()).as("项目分层余额行数").isPositive();
		BigDecimal actualQuantity = BigDecimal.ZERO;
		BigDecimal actualAmount = BigDecimal.ZERO;
		for (JsonNode item : items) {
			actualQuantity = actualQuantity.add(new BigDecimal(item.get("quantityOnHand").asText()));
			actualAmount = actualAmount.add(new BigDecimal(item.get("inventoryAmount").asText()));
		}
		assertThat(actualQuantity.compareTo(new BigDecimal(quantityOnHand))).as("项目分层余额总数量").isZero();
		assertThat(actualAmount.compareTo(new BigDecimal(inventoryAmount))).as("项目分层余额总金额").isZero();
	}

	private JsonNode projectCostLayers(AuthenticatedSession admin, long projectId, long materialId) throws Exception {
		return data(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&materialId=" + materialId + "&page=1&pageSize=20")).get("items");
	}

	private JsonNode projectBalances(AuthenticatedSession admin, long warehouseId, long projectId, long materialId,
			String qualityStatus) throws Exception {
		return data(get(admin, "/api/admin/inventory/balances?ownershipType=PROJECT&projectId=" + projectId
				+ "&warehouseId=" + warehouseId + "&materialId=" + materialId + "&qualityStatus=" + qualityStatus
				+ "&includeZero=true&page=1&pageSize=20"));
	}

	private List<Long> layerIds(JsonNode items) {
		List<Long> result = new ArrayList<>();
		for (JsonNode item : items) {
			result.add(item.get("id").longValue());
		}
		return result;
	}

	private void assertProjectBalanceByLayer(AuthenticatedSession admin, long warehouseId, long projectId,
			long materialId, String qualityStatus, long costLayerId, String quantityOnHand, String inventoryAmount)
			throws Exception {
		JsonNode balance = projectBalanceByLayer(admin, warehouseId, projectId, materialId, qualityStatus,
				costLayerId);
		assertDecimal(balance, "quantityOnHand", quantityOnHand);
		assertDecimal(balance, "inventoryAmount", inventoryAmount);
		assertThat(balance.get("costLayerId").longValue()).as("余额成本层").isEqualTo(costLayerId);
	}

	private void assertProjectBalanceQuantityOrZero(AuthenticatedSession admin, long warehouseId, long projectId,
			long materialId, String qualityStatus, long costLayerId, String quantityOnHand) throws Exception {
		JsonNode balance = findProjectBalanceByLayer(admin, warehouseId, projectId, materialId, qualityStatus,
				costLayerId);
		if (balance == null) {
			assertThat(new BigDecimal(quantityOnHand).compareTo(BigDecimal.ZERO)).as("未找到成本层余额时只能期望零数量")
				.isZero();
			return;
		}
		assertDecimal(balance, "quantityOnHand", quantityOnHand);
	}

	private JsonNode projectBalanceByLayer(AuthenticatedSession admin, long warehouseId, long projectId,
			long materialId, String qualityStatus, long costLayerId) throws Exception {
		JsonNode balance = findProjectBalanceByLayer(admin, warehouseId, projectId, materialId, qualityStatus,
				costLayerId);
		assertThat(balance)
			.as("未找到仓库 %s 项目 %s 物料 %s 质量 %s 成本层 %s 的独立余额，实际：%s",
					warehouseId, projectId, materialId, qualityStatus, costLayerId,
					projectBalances(admin, warehouseId, projectId, materialId, qualityStatus))
			.isNotNull();
		return balance;
	}

	private JsonNode findProjectBalanceByLayer(AuthenticatedSession admin, long warehouseId, long projectId,
			long materialId, String qualityStatus, long costLayerId) throws Exception {
		JsonNode items = projectBalances(admin, warehouseId, projectId, materialId, qualityStatus).get("items");
		for (JsonNode item : items) {
			if (item.hasNonNull("costLayerId") && item.get("costLayerId").longValue() == costLayerId) {
				return item;
			}
		}
		return null;
	}

	private JsonNode publicBalance(AuthenticatedSession admin, long warehouseId, long materialId) throws Exception {
		return firstItem(get(admin, "/api/admin/inventory/balances?ownershipType=PUBLIC&warehouseId=" + warehouseId
				+ "&materialId=" + materialId + "&qualityStatus=QUALIFIED&includeZero=true"));
	}

	private void assertProjectLayerQuantities(SoftAssertions softly, String label, AuthenticatedSession admin,
			long warehouseId, long projectId, long materialId, long costLayerId, String quantityOnHand,
			String lockedQuantity, String reservedQuantity, String occupiedQuantity, String availableQuantity)
			throws Exception {
		JsonNode balance = projectBalanceByLayer(admin, warehouseId, projectId, materialId, "QUALIFIED",
				costLayerId);
		softly.assertThat(balance.get("costLayerId").longValue()).as(label + " 成本层").isEqualTo(costLayerId);
		assertBalanceQuantities(softly, label, balance, quantityOnHand, lockedQuantity, reservedQuantity,
				occupiedQuantity, availableQuantity);
	}

	private void assertBalanceQuantities(SoftAssertions softly, String label, JsonNode balance, String quantityOnHand,
			String lockedQuantity, String reservedQuantity, String occupiedQuantity, String availableQuantity) {
		assertDecimal(softly, balance, "quantityOnHand", quantityOnHand, label);
		assertDecimal(softly, balance, "lockedQuantity", lockedQuantity, label);
		assertDecimal(softly, balance, "reservedQuantity", reservedQuantity, label);
		assertDecimal(softly, balance, "occupiedQuantity", occupiedQuantity, label);
		assertDecimal(softly, balance, "availableQuantity", availableQuantity, label);
	}

	private void assertTransferValueMovement(long transferId, String movementType, long costLayerId, String quantity,
			String amount) {
		Map<String, Object> movement = this.jdbcTemplate.queryForMap("""
				select quantity, inventory_amount, cost_layer_id
				from inv_value_movement
				where source_type = 'WAREHOUSE_TRANSFER'
				  and source_id = ?
				  and movement_type = ?
				""", transferId, movementType);
		assertThat(((Number) movement.get("cost_layer_id")).longValue()).as(movementType + " 成本层")
			.isEqualTo(costLayerId);
		assertThat(((BigDecimal) movement.get("quantity")).compareTo(new BigDecimal(quantity)))
			.as(movementType + " 数量")
			.isZero();
		assertThat(((BigDecimal) movement.get("inventory_amount")).compareTo(new BigDecimal(amount)))
			.as(movementType + " 金额")
			.isZero();
	}

	private long createProductionWorkOrder(InventoryFixture fixture) {
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
					effective_from, created_by, created_at, updated_by, updated_at, enabled_by, enabled_at
				)
				values (?, ?, ?, ?, 1.000000, ?, 'ENABLED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "BOM-S23-PROD-" + SEQUENCE.incrementAndGet(), fixture.valuedMaterialId(),
				"V" + SEQUENCE.incrementAndGet(), "023 生产详情估值 BOM", fixture.unitId(), LocalDate.now());
		long bomItemId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom_item (
					bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, business_unit_id,
					business_quantity, base_unit_id, base_quantity, quantity_basis, created_at, updated_at
				)
				values (?, 1, ?, ?, 1.000000, 0, ?, 1.000000, ?, 1.000000, 'BASE_UNIT', now(), now())
				returning id
				""", Long.class, bomId, fixture.nonValuedMaterialId(), fixture.unitId(), fixture.unitId(),
				fixture.unitId());
		long workOrderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity,
					qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark,
					created_by, created_at, updated_by, updated_at, released_by, released_at
				)
				values (?, ?, ?, 1.000000, 0, 0, 0, 0, ?, ?, ?, ?, 'RELEASED', '023 生产详情估值',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "WO-S23-PROD-" + SEQUENCE.incrementAndGet(), fixture.valuedMaterialId(),
				bomId, fixture.rawWarehouseId(), fixture.rawWarehouseId(), LocalDate.now(),
				LocalDate.now().plusDays(1));
		this.jdbcTemplate.update("""
				insert into mfg_work_order_material (
					work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity, issued_quantity,
					loss_rate, remark, created_at, updated_at, business_unit_id, business_quantity,
					base_unit_id, base_required_quantity, quantity_basis
				)
				values (?, 1, ?, ?, ?, 1.000000, 0, 0, '023 生产详情原料', now(), now(), ?, 1.000000, ?,
					1.000000, 'BASE_UNIT')
				""", workOrderId, bomItemId, fixture.nonValuedMaterialId(), fixture.unitId(), fixture.unitId(),
				fixture.unitId());
		return workOrderId;
	}

	private Map<String, Object> projectValuationAdjustmentPayload(long projectId, long materialId, long costLayerId,
			String unitCost, String adjustmentAmount) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("ownershipType", "PROJECT");
		line.put("projectId", projectId);
		line.put("materialId", materialId);
		line.put("quantity", "1.000000");
		line.put("unitCost", unitCost);
		line.put("adjustmentAmount", adjustmentAmount);
		line.put("costLayerId", costLayerId);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("adjustmentType", "PROVISIONAL_REVALUATION");
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", "023 项目层暂估重估详情");
		body.put("idempotencyKey", "s23-project-valuation-" + SEQUENCE.incrementAndGet());
		body.put("lines", List.of(line));
		return body;
	}

	private JsonNode findLineByProject(JsonNode lines, long projectId) {
		for (JsonNode line : lines) {
			if (line.has("projectId") && !line.get("projectId").isNull()
					&& line.get("projectId").longValue() == projectId) {
				return line;
			}
			if (line.has("sourceProjectId") && !line.get("sourceProjectId").isNull()
					&& line.get("sourceProjectId").longValue() == projectId) {
				return line;
			}
			if (line.has("targetProjectId") && !line.get("targetProjectId").isNull()
					&& line.get("targetProjectId").longValue() == projectId) {
				return line;
			}
		}
		throw new AssertionError("未找到项目行 " + projectId + "，实际：" + lines);
	}

	private void assertOnlyAllowedActions(SoftAssertions softly, JsonNode detail, String label) {
		softly.assertThat(detail.has("allowedActions")).as(label + " 必须返回 allowedActions").isTrue();
		softly.assertThat(detail.has("availableActions")).as(label + " 不得回归 availableActions").isFalse();
	}

	private void assertLineIdentity(SoftAssertions softly, JsonNode line, String label, long materialId, Long unitId,
			long projectId) {
		softly.assertThat(line.get("materialId").longValue()).as(label + " 必须冻结物料").isEqualTo(materialId);
		if (unitId != null) {
			softly.assertThat(line.get("unitId").longValue()).as(label + " 必须冻结单位").isEqualTo(unitId);
		}
		boolean projectMatched = (line.has("projectId") && !line.get("projectId").isNull()
				&& line.get("projectId").longValue() == projectId)
				|| (line.has("sourceProjectId") && !line.get("sourceProjectId").isNull()
						&& line.get("sourceProjectId").longValue() == projectId)
				|| (line.has("targetProjectId") && !line.get("targetProjectId").isNull()
						&& line.get("targetProjectId").longValue() == projectId);
		softly.assertThat(projectMatched).as(label + " 必须冻结项目归属").isTrue();
	}

	private JsonNode createProjectLayerFromPublic(AuthenticatedSession admin, InventoryFixture fixture, long projectId,
			String quantity, String unitCost) throws Exception {
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				quantity, unitCost);
		JsonNode conversion = data(exchange(HttpMethod.POST, "/api/admin/inventory/ownership-conversions",
				ownershipConversionPayload("公共库存转项目层", List.of(
						ownershipLine(1, "PUBLIC", null, "PROJECT", projectId, fixture.rawWarehouseId(),
								fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(), quantity,
								null, null))),
				admin));
		data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/ownership-conversions/" + conversion.get("id").longValue() + "/submit",
				actionBody(conversion, "提交公共转项目"), admin));
		approveLatestTask("inventory:ownership-conversion:post-approve");
		return firstItem(get(admin, "/api/admin/inventory/cost-layers?projectId=" + projectId
				+ "&materialId=" + fixture.valuedMaterialId()));
	}

	private long insertProjectCostLayer(long projectId, long materialId, String quantity, String amount,
			String unitCost) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into inv_project_cost_layer (
					project_id, material_id, source_type, source_id, source_line_id, original_quantity,
					original_amount, remaining_quantity, remaining_amount, unit_cost, status, created_at, updated_at
				)
				values (?, ?, 'STAGE023_PARENT_RESERVATION', ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				returning id
				""", Long.class, projectId, materialId, 7_100_000L + suffix, 7_200_000L + suffix,
				new BigDecimal(quantity), new BigDecimal(amount), new BigDecimal(quantity), new BigDecimal(amount),
				new BigDecimal(unitCost));
	}

	private void insertProjectBatchBalance(long warehouseId, long materialId, long unitId, long projectId,
			long costLayerId, long batchId, String quantity, String amount, String unitCost) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, quality_status,
					batch_id, ownership_type, project_id, valuation_state, inventory_amount, average_unit_cost,
					cost_layer_id, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, 'QUALIFIED', ?, 'PROJECT', ?, 'VALUED', ?, ?, ?, now(), now())
				""", warehouseId, materialId, unitId, new BigDecimal(quantity), batchId, projectId,
				new BigDecimal(amount), new BigDecimal(unitCost), costLayerId);
	}

	private long insertActiveParentReservation(String sourceType, long sourceId, long sourceLineId, long warehouseId,
			long materialId, long unitId, String quantity, String ownershipType, Long projectId, Long costLayerId) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_reservation (
					reservation_no, reservation_type, status, warehouse_id, material_id, unit_id, quality_status,
					quantity, released_quantity, consumed_quantity, source_type, source_id, source_line_id,
					source_document_no, business_date, reason, created_by, created_at, updated_by, updated_at,
					ownership_type, project_id, cost_layer_id, parent_reservation_id, batch_id, serial_id
				)
				values (?, 'RESERVATION', 'ACTIVE', ?, ?, ?, 'QUALIFIED', ?, 0, 0, ?, ?, ?, ?,
					current_date, '023父级聚合预留夹具', 'test', now(), 'test', now(), ?, ?, ?, null, null, null)
				returning id
				""", Long.class, "S23-PARENT-RSV-" + SEQUENCE.incrementAndGet(), warehouseId, materialId, unitId,
				new BigDecimal(quantity), sourceType, sourceId, sourceLineId, sourceType + "-" + sourceLineId,
				ownershipType, projectId, costLayerId);
	}

	private Map<String, Object> actionBody(JsonNode document, String reason) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", document.get("version").longValue());
		body.put("reason", reason);
		body.put("idempotencyKey", "s23-action-" + SEQUENCE.incrementAndGet() + "-"
				+ document.get("id").longValue());
		return body;
	}

	private JsonNode startStocktake(AuthenticatedSession admin, InventoryFixture fixture, LocalDate businessDate)
			throws Exception {
		JsonNode draft = data(exchange(HttpMethod.POST, "/api/admin/inventory/stocktakes",
				stocktakePayload(fixture.rawWarehouseId(), fixture.valuedMaterialId(), businessDate, "023 盘点"),
				admin));
		return data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + draft.get("id").longValue() + "/start",
				actionBody(draft, "开始盘点"), admin));
	}

	private JsonNode reconcileStocktake(AuthenticatedSession admin, InventoryFixture fixture, LocalDate businessDate,
			String countedQuantity) throws Exception {
		JsonNode counting = startStocktake(admin, fixture, businessDate);
		JsonNode line = counting.get("lines").get(0);
		Map<String, Object> countedLine = new LinkedHashMap<>();
		countedLine.put("id", line.get("id").longValue());
		countedLine.put("version", line.get("version").longValue());
		countedLine.put("countedQuantity", countedQuantity);
		Map<String, Object> update = new LinkedHashMap<>();
		update.put("version", counting.get("version").longValue());
		update.put("lines", List.of(countedLine));
		JsonNode counted = data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + counting.get("id").longValue() + "/lines", update, admin));
		return data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + counting.get("id").longValue() + "/confirm-variance",
				actionBody(counted, "确认盘点差异"), admin));
	}

	private JsonNode submitVarianceStocktake(AuthenticatedSession admin, InventoryFixture fixture,
			LocalDate businessDate, String countedQuantity) throws Exception {
		JsonNode reconciled = reconcileStocktake(admin, fixture, businessDate, countedQuantity);
		return data(exchange(HttpMethod.PUT,
				"/api/admin/inventory/stocktakes/" + reconciled.get("id").longValue() + "/submit",
				actionBody(reconciled, "提交盘差审批"), admin));
	}

	private Map<String, Object> stocktakePayload(long warehouseId, long materialId, LocalDate businessDate,
			String reason) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", businessDate.toString());
		body.put("scopeType", "WAREHOUSE");
		body.put("warehouseId", warehouseId);
		body.put("materialId", materialId);
		body.put("reason", reason);
		body.put("idempotencyKey", "s23-stocktake-" + SEQUENCE.incrementAndGet());
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
		body.put("idempotencyKey", "s23-valuation-" + SEQUENCE.incrementAndGet());
		body.put("lines", List.of(line));
		return body;
	}

	private Map<String, Object> projectWarehouseTransferPayload(long sourceWarehouseId, long targetWarehouseId,
			long projectId, long materialId, long unitId, String quantity, long costLayerId) {
		Map<String, Object> payload = warehouseTransferPayload(sourceWarehouseId, targetWarehouseId, materialId,
				unitId, null, quantity);
		return projectWarehouseTransferPayload(payload, projectId, costLayerId);
	}

	private Map<String, Object> projectWarehouseTransferPayload(long sourceWarehouseId, long targetWarehouseId,
			long projectId, long materialId, long unitId, Long batchId, String quantity, long costLayerId) {
		Map<String, Object> payload = warehouseTransferPayload(sourceWarehouseId, targetWarehouseId, materialId,
				unitId, batchId, quantity);
		return projectWarehouseTransferPayload(payload, projectId, costLayerId);
	}

	private Map<String, Object> projectWarehouseTransferPayload(Map<String, Object> payload, long projectId,
			long costLayerId) {
		@SuppressWarnings("unchecked")
		Map<String, Object> line = (Map<String, Object>) ((List<?>) payload.get("lines")).get(0);
		line.put("ownershipType", "PROJECT");
		line.put("projectId", projectId);
		line.put("sourceCostLayerId", costLayerId);
		line.put("costLayerId", costLayerId);
		return payload;
	}

	private Map<String, Object> qualityTransferPayload(long warehouseId, long materialId, long unitId,
			String quantity, String reason) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("warehouseId", warehouseId);
		body.put("materialId", materialId);
		body.put("unitId", unitId);
		body.put("quantity", quantity);
		body.put("businessDate", LocalDate.now().toString());
		body.put("reason", reason);
		return body;
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

	private ResponseEntity<String> actOnLatestTask(String actionPermission, String action, String comment)
			throws Exception {
		AuthenticatedSession approver = createUserAndLogin("stage023-task-actor-", "S23_TASK_ACTOR_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"inventory:balance:view", "inventory:movement:view", "inventory:valuation:view",
						actionPermission));
		JsonNode todo = data(get(approver, "/api/admin/approval-tasks?scope=TODO&page=1&pageSize=1"));
		assertThat(todo.get("total").longValue()).isPositive();
		JsonNode task = todo.get("items").get(0);
		return exchange(HttpMethod.POST,
				"/api/admin/approval-tasks/" + task.get("taskId").longValue() + "/" + action,
				Map.of("version", task.get("version").longValue(), "comment", comment,
						"idempotencyKey", "s23-task-" + action + "-" + SEQUENCE.incrementAndGet()),
				approver);
	}

	private LocalDate lockedBusinessDate() {
		return LocalDate.of(2080 + SEQUENCE.incrementAndGet(), 7, 10);
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, created_at, updated_at
				)
				values (?, ?, ?, ?, 'LOCKED', now(), now())
				""", "S23-LOCK-" + SEQUENCE.incrementAndGet(), "023锁定期间", date.withDayOfMonth(1),
				date.withDayOfMonth(date.lengthOfMonth()));
	}

	private String status(String tableName, long id) {
		return this.jdbcTemplate.queryForObject("select status from " + tableName + " where id = ?",
				String.class, id);
	}

	private String publicPoolState(long materialId) {
		return this.jdbcTemplate.queryForObject(
				"select valuation_state from inv_public_valuation_pool where material_id = ?", String.class,
				materialId);
	}

	private List<String> actions(JsonNode node) {
		List<String> result = new ArrayList<>();
		JsonNode allowedActions = node.get("allowedActions");
		if (allowedActions != null) {
			for (JsonNode action : allowedActions) {
				result.add(action.asText());
			}
		}
		return result;
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

	private JsonNode createSalesOrder(AuthenticatedSession admin, long customerId, long warehouseId, long materialId,
			long unitId, String quantity) throws Exception {
		return data(exchange(HttpMethod.POST, "/api/admin/sales/orders",
				salesOrderPayload(customerId, List.of(salesOrderLine(1, warehouseId, materialId, unitId, quantity))),
				admin));
	}

	private Map<String, Object> salesOrderPayload(long customerId, List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("orderDate", LocalDate.now().toString());
		payload.put("expectedShipDate", LocalDate.now().plusDays(3).toString());
		payload.put("remark", "023 公共预留隔离测试");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> salesOrderLine(int lineNo, long warehouseId, long materialId, long unitId,
			String quantity) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("quantity", quantity);
		line.put("unitPrice", "1.000000");
		line.put("reservationWarehouseId", warehouseId);
		line.put("expectedShipDate", LocalDate.now().plusDays(3).toString());
		return line;
	}

	private Map<String, Object> salesShipmentPayload(long warehouseId, long orderLineId, long materialId, long unitId,
			String quantity) {
		return salesShipmentPayload(warehouseId, orderLineId, materialId, unitId, quantity, List.of());
	}

	private Map<String, Object> salesShipmentPayload(long warehouseId, long orderLineId, long materialId, long unitId,
			String quantity, List<Map<String, Object>> trackingAllocations) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", warehouseId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("remark", "023 公共预留出库隔离测试");
		payload.put("lines", List.of(salesShipmentLine(1, orderLineId, materialId, unitId, quantity,
				trackingAllocations)));
		return payload;
	}

	private Map<String, Object> salesShipmentLine(int lineNo, long orderLineId, long materialId, long unitId,
			String quantity) {
		return salesShipmentLine(lineNo, orderLineId, materialId, unitId, quantity, List.of());
	}

	private Map<String, Object> salesShipmentLine(int lineNo, long orderLineId, long materialId, long unitId,
			String quantity, List<Map<String, Object>> trackingAllocations) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("orderLineId", orderLineId);
		line.put("materialId", materialId);
		line.put("unitId", unitId);
		line.put("quantity", quantity);
		if (trackingAllocations != null && !trackingAllocations.isEmpty()) {
			line.put("trackingAllocations", trackingAllocations);
		}
		return line;
	}

	private String reservationOwnership(long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select ownership_type || ':' || coalesce(project_id::text, 'NULL')
				from inv_stock_reservation
				where source_type = 'SALES_ORDER'
				  and source_id = ?
				order by id
				limit 1
				""", String.class, orderId);
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
				order by id desc
				limit 1
				""", Long.class, sourceType, sourceLineId);
	}

	private String reservationIdentity(long reservationId) {
		return this.jdbcTemplate.queryForObject("""
				select case when parent_reservation_id is null then 'PARENT' else 'CHILD' end
					|| ':' || ownership_type
					|| ':' || coalesce(project_id::text, 'NULL')
					|| ':' || coalesce(cost_layer_id::text, 'NULL')
					|| ':' || coalesce(batch_id::text, 'NULL')
					|| ':' || coalesce(serial_id::text, 'NULL')
				from inv_stock_reservation
				where id = ?
				""", String.class, reservationId);
	}

	private String childReservationIdentity(long parentReservationId, Long batchId, Long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select 'CHILD'
					|| ':' || status
					|| ':' || source_type
					|| ':' || source_line_id::text
					|| ':' || ownership_type
					|| ':' || coalesce(project_id::text, 'NULL')
					|| ':' || coalesce(cost_layer_id::text, 'NULL')
					|| ':' || coalesce(batch_id::text, 'NULL')
					|| ':' || coalesce(serial_id::text, 'NULL')
					|| ':' || quantity::text
					|| ':' || consumed_quantity::text
				from inv_stock_reservation
				where parent_reservation_id = ?
				  and batch_id is not distinct from ?
				  and serial_id is not distinct from ?
				order by id desc
				limit 1
				""", String.class, parentReservationId, batchId, serialId);
	}

	private BigDecimal reservationActiveQuantity(long reservationId) {
		return this.jdbcTemplate.queryForObject("""
				select quantity - released_quantity - consumed_quantity
				from inv_stock_reservation
				where id = ?
				""", BigDecimal.class, reservationId);
	}

	private long childReservationCount(long parentReservationId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_reservation
				where parent_reservation_id = ?
				""", Long.class, parentReservationId);
	}

	private long childReservationCountForSerial(long serialId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_reservation
				where serial_id = ?
				  and parent_reservation_id is not null
				""", Long.class, serialId);
	}

	private BigDecimal activeChildQuantity(long parentReservationId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity - released_quantity - consumed_quantity), 0)
				from inv_stock_reservation
				where parent_reservation_id = ?
				  and status = 'ACTIVE'
				""", BigDecimal.class, parentReservationId);
	}

	private BigDecimal balanceLockedQuantity(long warehouseId, long materialId, Long batchId, Long serialId,
			String ownershipType, Long projectId, Long costLayerId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(locked_quantity), 0)
				from inv_stock_balance
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and batch_id is not distinct from ?
				  and serial_id is not distinct from ?
				  and ownership_type = ?
				  and project_id is not distinct from ?
				  and cost_layer_id is not distinct from ?
				""", BigDecimal.class, warehouseId, materialId, batchId, serialId, ownershipType, projectId,
				costLayerId);
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
		long customerId = insertCustomer(prefix + "CUS_");
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

	private long insertCustomer(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, prefix + suffix, prefix + "客户");
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

	private long seedBatchStock(AuthenticatedSession admin, InventoryFixture fixture, String batchNo, String quantity,
			String unitPrice) throws Exception {
		long batchId = insertBatch(fixture.valuedMaterialId(), batchNo);
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				quantity, unitPrice);
		assignLatestBalanceToBatch(fixture.rawWarehouseId(), fixture.valuedMaterialId(), batchId);
		return batchId;
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

	private long seedSerialStock(AuthenticatedSession admin, InventoryFixture fixture, String serialNo, String unitPrice)
			throws Exception {
		long serialId = insertSerial(fixture.valuedMaterialId(), serialNo, fixture.rawWarehouseId());
		seedQuantityWithoutVersion(admin, fixture.rawWarehouseId(), fixture.valuedMaterialId(), fixture.unitId(),
				"1.000000", unitPrice);
		assignLatestBalanceToSerial(fixture.rawWarehouseId(), fixture.valuedMaterialId(), serialId);
		return serialId;
	}

	private long insertSerial(long materialId, String serialNo, long warehouseId) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_serial (
					material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status,
					stock_status, business_date, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 'STAGE023_TEST', 1, 1, ?, 'QUALIFIED', 'IN_STOCK', current_date,
					'023 序列预留测试', 'test', now(), 'test', now())
				returning id
				""", Long.class, materialId, serialNo, warehouseId);
	}

	private void assignLatestBalanceToSerial(long warehouseId, long materialId, long serialId) {
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set serial_id = ?, updated_at = now()
				where id = (
					select id
					from inv_stock_balance
					where warehouse_id = ?
					  and material_id = ?
					  and batch_id is null
					  and serial_id is null
					order by id desc
					limit 1
				)
				""", serialId, warehouseId, materialId);
		this.jdbcTemplate.update("""
				update inv_stock_movement
				set serial_id = ?
				where id = (
					select id
					from inv_stock_movement
					where warehouse_id = ?
					  and material_id = ?
					  and batch_id is null
					  and serial_id is null
					order by id desc
					limit 1
				)
				""", serialId, warehouseId, materialId);
		this.jdbcTemplate.update("""
				update inv_serial
				set last_movement_id = (
					select id
					from inv_stock_movement
					where warehouse_id = ?
					  and material_id = ?
					  and serial_id = ?
					order by id desc
					limit 1
				),
				updated_at = now()
				where id = ?
				""", warehouseId, materialId, serialId, serialId);
	}

	private Map<String, Object> trackingByBatch(long batchId, String quantity) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("batchId", batchId);
		allocation.put("quantity", quantity);
		return allocation;
	}

	private Map<String, Object> trackingBySerial(long serialId) {
		Map<String, Object> allocation = new LinkedHashMap<>();
		allocation.put("serialId", serialId);
		allocation.put("quantity", "1.000000");
		return allocation;
	}

	private void approveLatestTask(String approvePermission) throws Exception {
		AuthenticatedSession approver = createUserAndLogin("stage023-approver-", "S23_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"inventory:balance:view", "inventory:movement:view", "inventory:valuation:view",
						approvePermission));
		JsonNode todo = data(get(approver, "/api/admin/approval-tasks?scope=TODO&page=1&pageSize=1"));
		assertThat(todo.get("total").longValue()).isPositive();
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

	private void markMaterialSellable(long materialId) {
		this.jdbcTemplate.update("""
				update mst_material
				set material_type = 'FINISHED_GOOD',
					source_type = 'SELF_MADE',
					updated_at = now()
				where id = ?
				""", materialId);
	}

	private void markMaterialTracking(long materialId, String trackingMethod) {
		this.jdbcTemplate.update("""
				update mst_material
				set tracking_method = ?,
					updated_at = now()
				where id = ?
				""", trackingMethod, materialId);
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

	private void assertDecimal(SoftAssertions softly, JsonNode node, String field, String expected, String label) {
		softly.assertThat(node.has(field)).as(label + " 缺少字段 " + field + "，实际响应：" + node).isTrue();
		if (node.has(field)) {
			softly.assertThat(new BigDecimal(node.get(field).asText()).compareTo(new BigDecimal(expected)))
				.as(label + " " + field + " 实际值 " + node.get(field).asText())
				.isZero();
		}
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
