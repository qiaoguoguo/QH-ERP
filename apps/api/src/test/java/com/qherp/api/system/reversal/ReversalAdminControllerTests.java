package com.qherp.api.system.reversal;

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
		assertDecimal(movement.beforeQuantity(), "7.000000");
		assertDecimal(movement.afterQuantity(), "10.000000");
		assertDecimal(balanceQuantity(fixture.warehouseId(), fixture.materialId()), "10.000000");

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
		assertRestrictedSource(detail.get("lines").get(0).get("source"), "SALES_SHIPMENT_LINE");
		assertRestrictedSource(detail.get("traces").get(0).get("source"), "SALES_SHIPMENT_LINE");
		assertThat(detail.get("traces").get(0).get("reverse").get("sourceId").longValue()).isEqualTo(returnId);

		JsonNode listItem = data(get("/api/admin/sales/returns", restricted)).get("items").get(0);
		assertRestrictedSource(listItem.get("source"), "SALES_SHIPMENT");

		JsonNode trace = data(get("/api/admin/reversal-traces?sourceType=SALES_RETURN&sourceId=" + returnId,
				restricted)).get(0);
		assertRestrictedSource(trace.get("source"), "SALES_SHIPMENT_LINE");
		assertThat(trace.get("restricted").booleanValue()).isTrue();
	}

	@Test
	void adminCanQueryStableEmptyReversalSkeletons() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		for (String path : List.of("/api/admin/sales/return-sources", "/api/admin/sales/returns",
				"/api/admin/procurement/return-sources", "/api/admin/procurement/returns",
				"/api/admin/production/material-return-sources", "/api/admin/production/material-returns",
				"/api/admin/production/material-supplement-sources",
				"/api/admin/production/material-supplements",
				"/api/admin/finance/settlement-adjustment-sources",
				"/api/admin/finance/settlement-adjustments")) {
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
		assertError(put("/api/admin/procurement/returns/999/post", Map.of(), admin), HttpStatus.NOT_FOUND,
				"REVERSAL_SOURCE_NOT_FOUND");
		assertError(get("/api/admin/production/material-returns/999", admin), HttpStatus.NOT_FOUND,
				"REVERSAL_SOURCE_NOT_FOUND");
		assertError(put("/api/admin/production/material-supplements/999/cancel", Map.of(), admin),
				HttpStatus.NOT_FOUND, "REVERSAL_SOURCE_NOT_FOUND");
		assertError(post("/api/admin/finance/settlement-adjustments",
				Map.of("settlementSide", "RECEIVABLE", "sourceType", "SALES_RETURN", "sourceId", 1,
						"targetId", 1, "amount", "1.00"),
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
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, now(), now())
				on conflict (warehouse_id, material_id)
				do update set unit_id = excluded.unit_id, quantity_on_hand = excluded.quantity_on_hand,
					updated_at = now(), version = inv_stock_balance.version + 1
				""", warehouseId, materialId, unitId, new BigDecimal(quantity));
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

	private long createSalesReturn(AuthenticatedSession admin, long shipmentId, long shipmentLineId, String quantity)
			throws Exception {
		ResponseEntity<String> response = post("/api/admin/sales/returns",
				salesReturnPayload(shipmentId, "sales-return-" + SEQUENCE.incrementAndGet(),
						List.of(returnLine(shipmentLineId, quantity, "测试退货"))),
				admin);
		assertOk(response);
		return data(response).get("id").longValue();
	}

	private BigDecimal balanceQuantity(long warehouseId, long materialId) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				""", (rs, rowNum) -> rs.getBigDecimal("quantity_on_hand"), warehouseId, materialId)
			.stream()
			.findFirst()
			.orElse(BigDecimal.ZERO);
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

	private ReceivableAmounts receivableAmounts(long receivableId) {
		return this.jdbcTemplate.queryForObject("""
				select adjusted_amount, unreceived_amount, status
				from fin_receivable
				where id = ?
				""", (rs, rowNum) -> new ReceivableAmounts(rs.getBigDecimal("adjusted_amount"),
				rs.getBigDecimal("unreceived_amount"), rs.getString("status")), receivableId);
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

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	private void assertEmptyPage(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = data(response);
		assertThat(data.get("items").isArray()).isTrue();
		assertThat(data.get("items").size()).isZero();
		assertThat(data.get("page").intValue()).isOne();
		assertThat(data.get("pageSize").intValue()).isEqualTo(20);
		assertThat(data.get("total").longValue()).isZero();
		assertThat(data.get("totalPages").intValue()).isZero();
	}

	private void assertOk(ResponseEntity<String> response) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

	private record PostedSalesShipment(long shipmentId, String shipmentNo, long firstShipmentLineId,
			long secondShipmentLineId, Long receivableId) {
	}

	private record MovementRow(String movementType, String direction, long sourceId, long sourceLineId,
			BigDecimal quantity, BigDecimal beforeQuantity, BigDecimal afterQuantity) {
	}

	private record ReceivableAmounts(BigDecimal adjustedAmount, BigDecimal unreceivedAmount, String status) {
	}

	private record ReversalLink(String sourceType, long sourceId, long sourceLineId, BigDecimal quantity,
			BigDecimal amount) {
	}

}
