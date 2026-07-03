package com.qherp.api.system.cost;

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
		properties = "qherp.test.context=cost-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CostAdminControllerTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void materialIssuePostingCreatesMaterialCostRecord() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		CostFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "30.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());

		long issueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("成本领料",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"4.000000"))));
		assertOk(postMaterialIssue(admin, workOrderId, issueId));

		JsonNode materialCost = firstItem(
				get("/api/admin/cost/records?workOrderId=" + workOrderId + "&costType=MATERIAL", admin));
		assertThat(materialCost.get("costType").asText()).isEqualTo("MATERIAL");
		assertThat(materialCost.get("sourceType").asText()).isEqualTo("AUTO_PRODUCTION");
		assertThat(materialCost.get("sourceDocumentType").asText()).isEqualTo("PRODUCTION_MATERIAL_ISSUE");
		assertThat(materialCost.get("sourceDocumentId").longValue()).isEqualTo(issueId);
		assertThat(materialCost.get("sourceLineId").isNull()).isFalse();
		assertThat(materialCost.get("materialId").longValue()).isEqualTo(fixture.rawMaterialId());
		assertDecimal(materialCost, "quantity", "4.000000");
		assertThat(materialCost.get("amount").isNull()).isTrue();
		assertThat(auditCount("MFG_COST_RECORD_AUTO_CREATE", "MFG_COST_RECORD")).isGreaterThanOrEqualTo(1L);
	}

	@Test
	void workReportPostingCreatesLaborQuantityCostRecord() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		CostFixture fixture = fixture(admin);
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");

		long reportId = createReportId(admin, workOrderId, workReportPayload("3.000000", "1.000000"));
		assertOk(postReport(admin, workOrderId, reportId));

		JsonNode laborCost = firstItem(
				get("/api/admin/cost/records?workOrderId=" + workOrderId + "&costType=LABOR", admin));
		assertThat(laborCost.get("costType").asText()).isEqualTo("LABOR");
		assertThat(laborCost.get("sourceType").asText()).isEqualTo("AUTO_PRODUCTION");
		assertThat(laborCost.get("sourceDocumentType").asText()).isEqualTo("PRODUCTION_WORK_REPORT");
		assertThat(laborCost.get("sourceDocumentId").longValue()).isEqualTo(reportId);
		assertThat(laborCost.get("sourceLineId").isNull()).isTrue();
		assertDecimal(laborCost, "quantity", "4.000000");
		assertThat(laborCost.get("amount").isNull()).isTrue();
	}

	@Test
	void completionReceiptAppearsInWorkOrderCostSummary() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		CostFixture fixture = fixture(admin);
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");
		long reportId = createReportId(admin, workOrderId, workReportPayload("5.000000", "0.000000"));
		assertOk(postReport(admin, workOrderId, reportId));

		long receiptId = createCompletionReceiptId(admin, workOrderId,
				completionReceiptPayload(fixture.receiptWarehouseId(), "5.000000"));
		assertOk(postCompletionReceipt(admin, workOrderId, receiptId));

		ResponseEntity<String> summaryResponse = get("/api/admin/cost/work-orders/" + workOrderId + "/summary",
				admin);
		assertOk(summaryResponse);
		JsonNode summary = data(summaryResponse);
		assertThat(summary.get("formalAccounting").booleanValue()).isFalse();
		assertThat(summary.get("outputTraces").size()).isOne();
		JsonNode outputTrace = summary.get("outputTraces").get(0);
		assertThat(outputTrace.get("receiptId").longValue()).isEqualTo(receiptId);
		assertDecimal(outputTrace, "quantity", "5.000000");
	}

	@Test
	void manualCostRecordCanBeCreatedListedDetailedAndSummarized() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		CostFixture fixture = fixture(admin);
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");

		ResponseEntity<String> created = createCostRecord(admin, manualCostPayload(workOrderId,
				"MANUFACTURING_OVERHEAD", "MANUAL_AMOUNT", null, null, "120.500000", "制造费用记录"));
		assertOk(created);
		JsonNode cost = data(created);
		long costRecordId = cost.get("id").longValue();
		assertThat(cost.get("sourceType").asText()).isEqualTo("MANUAL_ENTRY");
		assertThat(cost.get("sourceDocumentType").asText()).isEqualTo("MANUAL_COST_RECORD");
		assertDecimal(cost, "amount", "120.500000");

		JsonNode detail = data(get("/api/admin/cost/records/" + costRecordId, admin));
		assertThat(detail.get("workOrderId").longValue()).isEqualTo(workOrderId);
		assertThat(detail.get("productMaterialId").longValue()).isEqualTo(fixture.productMaterialId());
		assertThat(detail.get("recordedByName").asText()).isEqualTo("admin");

		JsonNode listed = firstItem(get("/api/admin/cost/records?keyword=制造费用记录&workOrderId=" + workOrderId,
				admin));
		assertThat(listed.get("id").longValue()).isEqualTo(costRecordId);

		JsonNode summary = data(get("/api/admin/cost/work-orders/" + workOrderId + "/summary", admin));
		assertThat(summary.get("records").size()).isOne();
		assertThat(summary.get("amountSummaries").size()).isOne();
		JsonNode amountSummary = summary.get("amountSummaries").get(0);
		assertThat(amountSummary.get("costType").asText()).isEqualTo("MANUFACTURING_OVERHEAD");
		assertDecimal(amountSummary, "amount", "120.500000");
		assertThat(auditCount("MFG_COST_RECORD_CREATE", "MFG_COST_RECORD")).isGreaterThanOrEqualTo(1L);
	}

	@Test
	void manualUnitPriceQuantityAmountOverflowReturnsCostAmountInvalid() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		CostFixture fixture = fixture(admin);
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");
		String maxValidDecimal = "999999999999.999999";
		Map<String, Object> overflowingPayload = manualCostPayload(workOrderId, "OTHER",
				"MANUAL_UNIT_PRICE_QUANTITY", maxValidDecimal, maxValidDecimal, null, "乘积金额溢出");

		assertError(createCostRecord(admin, overflowingPayload), HttpStatus.BAD_REQUEST, "COST_AMOUNT_INVALID");

		ResponseEntity<String> created = createCostRecord(admin,
				manualCostPayload(workOrderId, "OTHER", "MANUAL_AMOUNT", null, null, "1.000000", "正常手工记录"));
		assertOk(created);
		long costRecordId = data(created).get("id").longValue();

		assertError(updateCostRecord(admin, costRecordId, overflowingPayload), HttpStatus.BAD_REQUEST,
				"COST_AMOUNT_INVALID");
	}

	@Test
	void duplicateAutomaticSourceReturnsCostSourceDuplicatedAndRollsBackPosting() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		CostFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "30.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());
		long issueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("重复成本来源",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"1.000000"))));
		long issueLineId = materialIssueLineId(issueId);
		insertDuplicateMaterialCost(issueId, issueLineId);

		BigDecimal beforeStock = balanceQuantity(fixture.issueWarehouseId(), fixture.rawMaterialId());
		ResponseEntity<String> duplicate = postMaterialIssue(admin, workOrderId, issueId);

		assertError(duplicate, HttpStatus.CONFLICT, "COST_SOURCE_DUPLICATED");
		assertDecimal(balanceQuantity(fixture.issueWarehouseId(), fixture.rawMaterialId()), beforeStock.toPlainString());
		String issueStatus = this.jdbcTemplate.queryForObject("select status from mfg_material_issue where id = ?",
				String.class, issueId);
		assertThat(issueStatus).isEqualTo("DRAFT");
	}

	@Test
	void automaticCostRecordCannotBeUpdated() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		CostFixture fixture = fixture(admin);
		createOpeningStock(admin, fixture.issueWarehouseId(), fixture.rawMaterialId(), "30.000000");
		long workOrderId = createAndReleaseWorkOrder(admin, fixture, "5.000000");
		JsonNode rawRequirement = workOrderMaterial(data(getWorkOrder(admin, workOrderId)), fixture.rawMaterialId());
		long issueId = createMaterialIssueId(admin, workOrderId, materialIssuePayload("自动记录不可改",
				List.of(materialIssueLine(1, rawRequirement.get("id").longValue(), fixture.issueWarehouseId(),
						"1.000000"))));
		assertOk(postMaterialIssue(admin, workOrderId, issueId));
		long autoCostId = firstItem(get("/api/admin/cost/records?workOrderId=" + workOrderId + "&costType=MATERIAL",
				admin)).get("id").longValue();

		ResponseEntity<String> update = updateCostRecord(admin, autoCostId, manualCostPayload(workOrderId,
				"MATERIAL", "MANUAL_AMOUNT", null, null, "10.000000", "尝试修改自动记录"));

		assertError(update, HttpStatus.CONFLICT, "COST_GENERATED_RECORD_IMMUTABLE");
	}

	private CostFixture fixture(AuthenticatedSession admin) throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		String suffix = "COST_" + sequence;
		long unitId = createUnit(admin, "COST_UNIT_" + suffix, "成本单位" + suffix);
		long issueWarehouseId = createWarehouse(admin, "COST_ISSUE_" + suffix, "成本领料仓" + suffix);
		long receiptWarehouseId = createWarehouse(admin, "COST_RECEIPT_" + suffix, "成本入库仓" + suffix);
		long categoryId = createCategory(admin, "COST_CAT_" + suffix, "成本分类" + suffix);
		long productMaterialId = createMaterial(admin, "COST_PRODUCT_" + suffix, "成本成品" + suffix,
				"FINISHED_GOOD", "SELF_MADE", categoryId, unitId);
		long rawMaterialId = createMaterial(admin, "COST_RAW_" + suffix, "成本原料" + suffix, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId);
		long auxiliaryMaterialId = createMaterial(admin, "COST_AUX_" + suffix, "成本辅料" + suffix, "AUXILIARY",
				"PURCHASED", categoryId, unitId);
		long bomId = createBom(admin, productMaterialId, unitId, rawMaterialId, auxiliaryMaterialId);
		return new CostFixture(unitId, issueWarehouseId, receiptWarehouseId, categoryId, productMaterialId,
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
			String sourceType, long categoryId, long unitId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", code);
		body.put("name", name);
		body.put("specification", "测试规格");
		body.put("materialType", materialType);
		body.put("sourceType", sourceType);
		body.put("categoryId", categoryId);
		body.put("unitId", unitId);
		body.put("status", "ENABLED");
		body.put("safeStock", "0");
		body.put("sortOrder", 10);
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/master/materials", body, admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private long createBom(AuthenticatedSession admin, long productId, long unitId, long rawMaterialId,
			long auxiliaryId) throws Exception {
		int sequence = SEQUENCE.incrementAndGet();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("bomCode", "COST_BOM_" + sequence);
		body.put("parentMaterialId", productId);
		body.put("versionCode", "V" + sequence);
		body.put("name", "成本 BOM " + sequence);
		body.put("baseQuantity", "1.000000");
		body.put("baseUnitId", unitId);
		body.put("effectiveFrom", LocalDate.now().toString());
		body.put("items", List.of(bomItem(1, rawMaterialId, unitId, "2.000000"),
				bomItem(2, auxiliaryId, unitId, "1.000000")));
		ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/admin/boms", body, admin);
		assertOk(response);
		long bomId = data(response).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/boms/" + bomId + "/enable", null, admin));
		return bomId;
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
		body.put("reason", "成本测试期初");
		body.put("remark", "成本归集测试");
		body.put("lines", List.of(line));
		ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/admin/inventory/documents", body, admin);
		assertOk(created);
		long documentId = data(created).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/inventory/documents/" + documentId + "/post", null, admin));
		return documentId;
	}

	private long createAndReleaseWorkOrder(AuthenticatedSession admin, CostFixture fixture, String plannedQuantity)
			throws Exception {
		ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/admin/production/work-orders",
				workOrderPayload(fixture.productMaterialId(), fixture.bomId(), fixture.issueWarehouseId(),
						fixture.receiptWarehouseId(), plannedQuantity),
				admin);
		assertOk(created);
		long workOrderId = data(created).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/production/work-orders/" + workOrderId + "/release", null,
				admin));
		return workOrderId;
	}

	private Map<String, Object> workOrderPayload(long productId, long bomId, long issueWarehouseId,
			long receiptWarehouseId, String plannedQuantity) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("productMaterialId", productId);
		body.put("bomId", bomId);
		body.put("plannedQuantity", plannedQuantity);
		body.put("issueWarehouseId", issueWarehouseId);
		body.put("receiptWarehouseId", receiptWarehouseId);
		body.put("plannedStartDate", LocalDate.now().toString());
		body.put("plannedFinishDate", LocalDate.now().plusDays(1).toString());
		body.put("remark", "成本测试工单");
		return body;
	}

	private ResponseEntity<String> getWorkOrder(AuthenticatedSession session, long workOrderId) {
		return get("/api/admin/production/work-orders/" + workOrderId, session);
	}

	private long createMaterialIssueId(AuthenticatedSession session, long workOrderId, Map<String, Object> body)
			throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/production/work-orders/" + workOrderId + "/material-issues", body, session);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private ResponseEntity<String> postMaterialIssue(AuthenticatedSession session, long workOrderId, long issueId) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/material-issues/" + issueId + "/post", null,
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
		line.put("remark", "成本领料行");
		return line;
	}

	private long createReportId(AuthenticatedSession session, long workOrderId, Map<String, Object> body)
			throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/production/work-orders/" + workOrderId + "/reports", body, session);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private ResponseEntity<String> postReport(AuthenticatedSession session, long workOrderId, long reportId) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/reports/" + reportId + "/post", null, session);
	}

	private Map<String, Object> workReportPayload(String qualifiedQuantity, String defectiveQuantity) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("qualifiedQuantity", qualifiedQuantity);
		body.put("defectiveQuantity", defectiveQuantity);
		body.put("reporterName", "成本报工员");
		body.put("remark", "成本报工测试");
		return body;
	}

	private long createCompletionReceiptId(AuthenticatedSession session, long workOrderId, Map<String, Object> body)
			throws Exception {
		ResponseEntity<String> response = exchange(HttpMethod.POST,
				"/api/admin/production/work-orders/" + workOrderId + "/completion-receipts", body, session);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private ResponseEntity<String> postCompletionReceipt(AuthenticatedSession session, long workOrderId,
			long receiptId) {
		return exchange(HttpMethod.PUT,
				"/api/admin/production/work-orders/" + workOrderId + "/completion-receipts/" + receiptId + "/post",
				null, session);
	}

	private Map<String, Object> completionReceiptPayload(long receiptWarehouseId, String quantity) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("businessDate", LocalDate.now().toString());
		body.put("receiptWarehouseId", receiptWarehouseId);
		body.put("quantity", quantity);
		body.put("remark", "成本完工入库测试");
		return body;
	}

	private ResponseEntity<String> createCostRecord(AuthenticatedSession session, Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/cost/records", body, session);
	}

	private ResponseEntity<String> updateCostRecord(AuthenticatedSession session, long id, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/cost/records/" + id, body, session);
	}

	private Map<String, Object> manualCostPayload(long workOrderId, String costType, String basisType,
			String quantity, String unitPrice, String amount, String remark) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("workOrderId", workOrderId);
		body.put("costType", costType);
		body.put("basisType", basisType);
		body.put("businessDate", LocalDate.now().toString());
		if (quantity != null) {
			body.put("quantity", quantity);
		}
		if (unitPrice != null) {
			body.put("unitPrice", unitPrice);
		}
		if (amount != null) {
			body.put("amount", amount);
		}
		body.put("sourceDocumentNo", remark);
		body.put("remark", remark);
		return body;
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

	private long materialIssueLineId(long issueId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from mfg_material_issue_line
				where issue_id = ?
				order by id asc
				limit 1
				""", Long.class, issueId);
	}

	private void insertDuplicateMaterialCost(long issueId, long issueLineId) {
		this.jdbcTemplate.update("""
				insert into mfg_cost_record (
					record_no, work_order_id, product_material_id, cost_type, source_type, source_document_type,
					source_document_no, source_document_id, source_line_id, work_order_material_id, material_id,
					unit_id, quantity, basis_type, business_date, status, remark, recorded_by, recorded_at,
					created_by, created_at, updated_by, updated_at
				)
				select ?, i.work_order_id, wo.product_material_id, 'MATERIAL', 'AUTO_PRODUCTION',
				       'PRODUCTION_MATERIAL_ISSUE', i.issue_no, i.id, l.id, l.work_order_material_id,
				       l.material_id, l.unit_id, l.quantity, 'SOURCE_QUANTITY_ONLY', i.business_date,
				       'ACTIVE', '重复来源预置', 'test', now(), 'test', now(), 'test', now()
				from mfg_material_issue i
				join mfg_work_order wo on wo.id = i.work_order_id
				join mfg_material_issue_line l on l.issue_id = i.id
				where i.id = ?
				and l.id = ?
				""", "COST-DUP-" + SEQUENCE.incrementAndGet(), issueId, issueLineId);
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

	private long auditCount(String action, String targetType) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = ?
				""", Long.class, action, targetType);
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
		assertOk(response);
		JsonNode items = data(response).get("items");
		assertThat(items.size()).isGreaterThan(0);
		return items.get(0);
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

	private record CostFixture(long unitId, long issueWarehouseId, long receiptWarehouseId, long categoryId,
			long productMaterialId, long rawMaterialId, long auxiliaryMaterialId, long bomId) {
	}

}
