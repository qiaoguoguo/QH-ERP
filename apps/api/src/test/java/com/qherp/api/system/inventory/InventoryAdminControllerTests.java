package com.qherp.api.system.inventory;

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

		assertAuditLog("INVENTORY_DOCUMENT_CREATE", documentId);
		assertAuditLog("INVENTORY_DOCUMENT_UPDATE", documentId);
		assertAuditLog("INVENTORY_DOCUMENT_POST", documentId);
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

	private AuthenticatedSession createReadOnlyUserAndLogin() {
		return createInventoryUserAndLogin("inventory-reader-", "INV_READER_", "库存只读", List.of("inventory:balance:view",
				"inventory:movement:view", "inventory:document:view"));
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

	private long movementCount(long warehouseId, long materialId) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_movement
				where warehouse_id = ?
				and material_id = ?
				""", Long.class, warehouseId, materialId);
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
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
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

	private record InventoryFixture(long eachUnitId, long kgUnitId, long meterUnitId, long categoryId,
			long rawWarehouseId, long finishedWarehouseId, long disabledWarehouseId, long finishedMaterialId,
			long semiMaterialId, long rawMaterialId, long auxiliaryMaterialId, long disabledMaterialId) {
	}

	private record MovementRow(String movementType, String direction, long sourceLineId, BigDecimal quantity,
			BigDecimal beforeQuantity, BigDecimal afterQuantity) {
	}

}
