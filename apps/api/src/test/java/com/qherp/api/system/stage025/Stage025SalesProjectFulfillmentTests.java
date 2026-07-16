package com.qherp.api.system.stage025;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=stage025-sales-project-fulfillment",
				"qherp.platform.task.worker.enabled=false"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage025SalesProjectFulfillmentTests extends PostgresIntegrationTest {

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
	void 报价审批转换手工订单和商业快照必须形成可信销售来源链() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_QUOTE_");

		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", projectQuotePayload(fixture,
				"12.000000", "100.000000")));
		assertThat(quote.get("status").asText()).isEqualTo("DRAFT");
		assertAllowedActions(quote, "SUBMIT_APPROVAL", "CANCEL");
		assertTextualDecimal(firstLine(quote), "taxIncludedUnitPrice");
		assertTextualDecimal(firstLine(quote), "taxExcludedAmount");

		JsonNode approval = data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(quote, "报价税价完整",
						"s25-quote-submit-" + quote.get("id").longValue())));
		assertThat(approval.get("sceneCode").asText()).isEqualTo("SALES_QUOTE_APPROVAL");
		assertThat(approval.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(candidatePermission(approval.get("id").longValue())).isEqualTo("sales:quote:approve");

		AuthenticatedSession approver = createUserAndLogin("stage025-quote-approver-", "S25_QUOTE_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"sales:quote:view", "sales:quote:approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意报价", "s25-quote-approve-"
						+ approval.get("id").longValue())));
		JsonNode approvedQuote = data(get(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()));
		assertThat(approvedQuote.get("status").asText()).isEqualTo("APPROVED");
		assertAllowedActions(approvedQuote, "CONVERT_ORDER", "CONVERT_CONTRACT", "PRINT", "EXPORT");

		JsonNode converted = data(post(admin, "/api/admin/sales/quotes/" + approvedQuote.get("id").longValue()
				+ "/convert-order", convertOrderBody(approvedQuote, fixture.projectId(), fixture.contractId(),
						"s25-quote-convert-order-" + approvedQuote.get("id").longValue())));
		JsonNode convertedAgain = data(post(admin, "/api/admin/sales/quotes/" + approvedQuote.get("id").longValue()
				+ "/convert-order", convertOrderBody(approvedQuote, fixture.projectId(), fixture.contractId(),
						"s25-quote-convert-order-" + approvedQuote.get("id").longValue())));
		assertThat(convertedAgain.get("id").longValue()).isEqualTo(converted.get("id").longValue());
		assertJsonLong(converted, "projectId", fixture.projectId());
		assertJsonLong(converted, "contractId", fixture.contractId());
		assertJsonLong(converted, "sourceQuoteId", approvedQuote.get("id").longValue());
		assertJsonText(firstLine(converted), "priceSourceType", "QUOTE");
		assertJsonLong(firstLine(converted), "sourceQuoteLineId", firstLine(approvedQuote).get("id").longValue());

		ensureCreditProfile(admin, fixture.customerId());
		converted = prepareConvertedOrderForConfirmation(admin, converted, fixture);
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + converted.get("id").longValue()
				+ "/confirm", actionBody(converted, "项目订单确认", "s25-order-confirm-"
						+ converted.get("id").longValue())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertAllowedActions(confirmed, "CREATE_CHANGE", "CREATE_SHIPMENT", "SUBMIT_SHORT_CLOSE");
		assertTextualDecimal(confirmed, "taxIncludedAmount");
		assertTextualDecimal(firstLine(confirmed), "taxRate");
		assertJsonText(firstLine(confirmed), "currency", "CNY");
		assertThat(countRows("sal_sales_order_snapshot", "order_id = ?", confirmed.get("id").longValue())).isOne();
		assertThat(countRows("sal_sales_order_line_snapshot", "order_line_id = ?",
				firstLine(confirmed).get("id").longValue())).isOne();

		JsonNode manual = data(post(admin, "/api/admin/sales/orders",
				manualOrderPayload(fixture, "5.000000", "88.000000")));
		assertJsonText(firstLine(manual), "priceSourceType", "MANUAL");
		assertNullOrMissing(manual, "sourceQuoteId");
		assertNullOrMissing(firstLine(manual), "sourceQuoteLineId");
		ensurePublicQualifiedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "5.000000");
		JsonNode manualConfirmed = data(put(admin, "/api/admin/sales/orders/" + manual.get("id").longValue()
				+ "/confirm", actionBody(manual, "手工订单确认", "s25-manual-confirm-"
						+ manual.get("id").longValue())));
		assertThat(manualConfirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(manualConfirmed.toString()).contains("MANUAL");
	}

	@Test
	void 报价更新取消转合同过期和审批中动作必须走冻结契约() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_QUOTE_DIFF_");
		long contractProjectId = insertProject("S25_QUOTE_CONTRACT_PRJ_" + SEQUENCE.incrementAndGet(),
				fixture.customerId(), "ACTIVE");

		Map<String, Object> updatePayload = projectQuotePayload(fixture, "5.000000", "90.000000");
		updatePayload.put("projectId", contractProjectId);
		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", updatePayload));
		updatePayload.put("version", quote.get("version").longValue());
		updatePayload.put("remark", "025 报价集中整改更新");
		JsonNode updated = data(put(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue(),
				updatePayload));
		assertThat(updated.get("version").longValue()).isGreaterThan(quote.get("version").longValue());
		assertThat(updated.get("remark").asText()).isEqualTo("025 报价集中整改更新");
		assertTextualDecimal(firstLine(updated), "taxIncludedUnitPrice");

		JsonNode cancelQuote = data(post(admin, "/api/admin/sales/quotes",
				projectQuotePayload(fixture, "1.000000", "50.000000")));
		Map<String, Object> cancelPayload = actionBody(cancelQuote, "客户取消报价",
				"s25-quote-cancel-" + cancelQuote.get("id").longValue());
		JsonNode cancelled = data(post(admin, "/api/admin/sales/quotes/" + cancelQuote.get("id").longValue()
				+ "/cancel", cancelPayload));
		JsonNode cancelledAgain = data(post(admin, "/api/admin/sales/quotes/" + cancelQuote.get("id").longValue()
				+ "/cancel", cancelPayload));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelledAgain.get("id").longValue()).isEqualTo(cancelled.get("id").longValue());

		JsonNode submitted = data(post(admin, "/api/admin/sales/quotes/" + updated.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(updated, "报价审批中动作收敛",
						"s25-quote-diff-submit-" + updated.get("id").longValue())));
		assertThat(submitted.get("sceneCode").asText()).isEqualTo("SALES_QUOTE_APPROVAL");
		JsonNode pending = data(get(admin, "/api/admin/sales/quotes/" + updated.get("id").longValue()));
		assertThat(pending.get("status").asText()).isEqualTo("DRAFT");
		assertAllowedActionsAbsent(pending, "UPDATE", "SUBMIT_APPROVAL", "CANCEL");

		AuthenticatedSession approver = createUserAndLogin("stage025-quote-diff-approver-",
				"S25_QUOTE_DIFF_APPROVER_", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "sales:quote:view", "sales:quote:approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意报价", "s25-quote-diff-approve-"
						+ submitted.get("id").longValue())));
		JsonNode approved = data(get(admin, "/api/admin/sales/quotes/" + updated.get("id").longValue()));
		JsonNode contract = data(post(admin, "/api/admin/sales/quotes/" + approved.get("id").longValue()
				+ "/convert-contract", convertContractBody(approved, contractProjectId, "MAIN", null,
						"s25-quote-convert-contract-" + approved.get("id").longValue())));
		JsonNode contractAgain = data(post(admin, "/api/admin/sales/quotes/" + approved.get("id").longValue()
				+ "/convert-contract", convertContractBody(approved, contractProjectId, "MAIN", null,
						"s25-quote-convert-contract-" + approved.get("id").longValue())));
		assertThat(contract.get("contractNo").asText()).startsWith("SC");
		assertThat(contractAgain.get("id").longValue()).isEqualTo(contract.get("id").longValue());
		JsonNode converted = data(get(admin, "/api/admin/sales/quotes/" + approved.get("id").longValue()));
		assertThat(converted.get("status").asText()).isEqualTo("CONVERTED");
		assertError(post(admin, "/api/admin/sales/quotes/" + converted.get("id").longValue() + "/convert-order",
				convertOrderBody(converted, fixture.projectId(), fixture.contractId(),
						"s25-quote-second-convert-" + converted.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_QUOTE_STATUS_INVALID");
		assertAttachmentObjectAccepted("SALES_QUOTE", approved.get("id").longValue());

		Map<String, Object> expiredPayload = projectQuotePayload(fixture, "1.000000", "70.000000");
		expiredPayload.put("quoteDate", LocalDate.now().minusDays(2).toString());
		expiredPayload.put("validTo", LocalDate.now().minusDays(1).toString());
		expiredPayload.put("validUntil", LocalDate.now().minusDays(1).toString());
		JsonNode expiredQuote = data(post(admin, "/api/admin/sales/quotes", expiredPayload));
		JsonNode expiredSubmit = data(post(admin, "/api/admin/sales/quotes/" + expiredQuote.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(expiredQuote, "过期报价审批",
						"s25-quote-expired-submit-" + expiredQuote.get("id").longValue())));
		JsonNode expiredTask = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(expiredTask) + "/approve",
				approvalActionBody(taskActionId(expiredTask), "同意过期报价",
						"s25-quote-expired-approve-" + expiredSubmit.get("id").longValue())));
		JsonNode expired = data(get(admin, "/api/admin/sales/quotes/" + expiredQuote.get("id").longValue()));
		assertAllowedActionsAbsent(expired, "CONVERT_ORDER", "CONVERT_CONTRACT");
		assertError(post(admin, "/api/admin/sales/quotes/" + expired.get("id").longValue() + "/convert-order",
				convertOrderBody(expired, fixture.projectId(), fixture.contractId(),
						"s25-quote-expired-convert-" + expired.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_QUOTE_STATUS_INVALID");
	}

	@Test
	void 订单变更审批状态幂等和已发快照必须只影响未交付部分() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_CHANGE_");
		JsonNode order = createConfirmedManualOrder(admin, fixture, "10.000000", "50.000000",
				"s25-change-order");
		JsonNode plan = firstItem(data(get(admin, "/api/admin/sales/delivery-plans?orderId="
				+ order.get("id").longValue() + "&page=1&pageSize=20")));
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "20.000000");
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + order.get("id").longValue()
				+ "/shipments", shipmentPayload(fixture, plan.get("id").longValue(),
						firstLine(order).get("id").longValue(), "4.000000", LocalDate.now())));
		JsonNode postedShipment = data(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue()
				+ "/post", actionBody(shipment, "首批出库", "s25-change-shipment-post-"
						+ shipment.get("id").longValue())));
		String postedLineAmount = firstLine(postedShipment).get("taxIncludedAmount").asText();
		JsonNode orderAfterShipment = data(get(admin, "/api/admin/sales/orders/" + order.get("id").longValue()));

		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderAfterShipment.get("id").longValue()
				+ "/changes", orderChangePayload(orderAfterShipment, "12.000000", "60.000000",
						LocalDate.now().plusDays(12))));
		JsonNode sameChange = data(post(admin, "/api/admin/sales/orders/" + orderAfterShipment.get("id").longValue()
				+ "/changes", orderChangePayload(orderAfterShipment, "12.000000", "60.000000",
						LocalDate.now().plusDays(12))));
		assertThat(sameChange.get("id").longValue()).isEqualTo(change.get("id").longValue());
		assertError(post(admin, "/api/admin/sales/orders/" + orderAfterShipment.get("id").longValue()
				+ "/changes", orderChangePayload(orderAfterShipment, "13.000000", "60.000000",
						LocalDate.now().plusDays(12))),
				HttpStatus.CONFLICT, "SALES_ACTION_IDEMPOTENCY_CONFLICT");
		assertThat(change.get("status").asText()).isEqualTo("DRAFT");
		JsonNode submitted = data(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(change, "调整未交付部分税价",
						"s25-change-submit-" + change.get("id").longValue())));
		JsonNode submittedAgain = data(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(change, "调整未交付部分税价",
						"s25-change-submit-" + change.get("id").longValue())));
		assertThat(submittedAgain.get("id").longValue()).isEqualTo(submitted.get("id").longValue());
		JsonNode pendingChange = data(get(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()));
		assertThat(pendingChange.get("status").asText()).isEqualTo("DRAFT");
		assertThat(pendingChange.get("approvalStatus").asText()).isIn("SUBMITTED", "PENDING");
		assertAllowedActionsAbsent(pendingChange, "UPDATE", "SUBMIT_APPROVAL", "CANCEL");
		assertError(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(change, "陈旧版本不同键", "s25-change-stale-"
						+ change.get("id").longValue())), HttpStatus.CONFLICT, "SALES_CONCURRENT_MODIFICATION");

		AuthenticatedSession approver = createUserAndLogin("stage025-change-approver-", "S25_CHANGE_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"sales:order-change:view", "sales:order-change:approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意变更", "s25-change-approve-"
						+ submitted.get("id").longValue())));
		JsonNode applied = data(get(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()));
		assertThat(applied.get("status").asText()).isEqualTo("APPLIED");
		JsonNode changedOrder = data(get(admin, "/api/admin/sales/orders/" + order.get("id").longValue()));
		assertDecimal(firstLine(changedOrder), "quantity", "12.000000");
		assertDecimal(firstLine(changedOrder), "shippedQuantity", "4.000000");
		JsonNode postedShipmentDetail = data(get(admin, "/api/admin/sales/shipments/"
				+ postedShipment.get("id").longValue()));
		assertThat(firstLine(postedShipmentDetail).get("taxIncludedAmount").asText()).isEqualTo(postedLineAmount);
	}

	@Test
	void 交付计划出库退货和应收必须使用出库行快照且不由退货重开需求() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_DELIVERY_");
		JsonNode order = createConfirmedManualOrder(admin, fixture, "8.000000", "30.000000",
				"s25-delivery-order");
		JsonNode plans = data(get(admin, "/api/admin/sales/delivery-plans?orderId=" + order.get("id").longValue()
				+ "&countedOnly=false&page=1&pageSize=20"));
		assertPage(plans);
		JsonNode plan = firstItem(plans);
		assertJsonLong(plan, "orderId", order.get("id").longValue());
		assertAllowedActionsPresent(plan);
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "20.000000");
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + order.get("id").longValue()
				+ "/shipments", shipmentPayload(fixture, plan.get("id").longValue(),
						firstLine(order).get("id").longValue(), "3.000000", LocalDate.now().minusDays(1))));
		assertError(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				actionBody(shipment, "", "s25-early-no-reason-" + shipment.get("id").longValue())),
				HttpStatus.BAD_REQUEST, "SALES_SHIPMENT_EARLY_REASON_REQUIRED");
		JsonNode posted = data(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue()
				+ "/post", actionBody(shipment, "客户要求提前交付", "s25-delivery-post-"
						+ shipment.get("id").longValue())));
		assertJsonLong(firstLine(posted), "deliveryPlanId", plan.get("id").longValue());
		assertTextualDecimal(firstLine(posted), "taxIncludedAmount");

		JsonNode receivable = data(post(admin, "/api/admin/finance/receivables",
				receivablePayload(posted.get("id").longValue(), "销售出库快照生成应收")));
		JsonNode confirmedReceivable = data(put(admin, "/api/admin/finance/receivables/"
				+ receivable.get("id").longValue() + "/confirm", null));
		assertThat(confirmedReceivable.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(new BigDecimal(firstItem(data(get(admin, "/api/admin/finance/receivables/"
				+ confirmedReceivable.get("id").longValue() + "/sources?page=1&pageSize=20")))
			.get("sourceAmount")
			.asText()).compareTo(new BigDecimal(firstLine(posted).get("taxIncludedAmount").asText()))).isZero();

		JsonNode returnSource = firstItem(data(get(admin, "/api/admin/sales/return-sources?keyword="
				+ posted.get("shipmentNo").asText() + "&page=1&pageSize=10")));
		JsonNode salesReturn = data(post(admin, "/api/admin/sales/returns",
				salesReturnPayload(posted.get("id").longValue(), firstLine(posted).get("id").longValue(),
						"1.000000")));
		JsonNode returnDetail = data(get(admin, "/api/admin/sales/returns/" + salesReturn.get("id").longValue()));
		assertThat(returnDetail.hasNonNull("version"))
			.as("销售退货公开详情必须返回 version，客户端才能按 025 副作用契约构造过账动作，实际响应：" + returnDetail)
			.isTrue();
		JsonNode postedReturn = data(put(admin, "/api/admin/sales/returns/" + returnDetail.get("id").longValue()
				+ "/post", actionBody(returnDetail, "客户退货", "s25-sales-return-post-"
						+ returnDetail.get("id").longValue())));
		assertJsonLong(firstLine(postedReturn), "sourceLineId", firstLine(posted).get("id").longValue());
		assertThat(firstLine(postedReturn).get("source").get("sourceType").asText()).isEqualTo("SALES_SHIPMENT_LINE");
		assertJsonLong(firstLine(postedReturn).get("source"), "sourceLineId", firstLine(posted).get("id").longValue());
		assertJsonLong(firstLine(returnSource), "shipmentLineId", firstLine(posted).get("id").longValue());
		assertThat(firstLine(returnSource).get("returnableQuantity").asText()).isNotBlank();

		JsonNode effective = data(get(admin, "/api/admin/sales/effective-demands?orderId="
				+ order.get("id").longValue() + "&countedOnly=true&page=1&pageSize=20"));
		JsonNode effectiveLine = firstItem(effective);
		assertDecimal(effectiveLine, "shippedQuantity", "3.000000");
		assertDecimal(effectiveLine, "returnedQuantity", "1.000000");
		assertDecimal(effectiveLine, "openDemandQuantity", "5.000000");
	}

	@Test
	void 三段信用例外审批权限和并发确认必须互斥且无空窗() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_CREDIT_");
		JsonNode profile = data(post(admin, "/api/admin/sales/credit-profiles",
				creditProfilePayload(fixture.customerId(), "500.00", false, false)));
		assertDecimal(profile, "creditLimit", "500.00");
		JsonNode order = createConfirmedManualOrder(admin, fixture, "4.000000", "50.000000",
				"s25-credit-order");
		JsonNode exposure = data(get(admin, "/api/admin/sales/customers/" + fixture.customerId()
				+ "/credit-exposure"));
		assertTextualDecimal(exposure, "orderCommitmentAmount");
		assertTextualDecimal(exposure, "unsettledShipmentAmount");
		assertTextualDecimal(exposure, "receivableOutstandingAmount");

		JsonNode overLimitOrder = data(post(admin, "/api/admin/sales/orders",
				manualOrderPayload(fixture, "30.000000", "50.000000")));
		ensurePublicQualifiedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "30.000000");
		assertError(put(admin, "/api/admin/sales/orders/" + overLimitOrder.get("id").longValue()
				+ "/confirm", actionBody(overLimitOrder, "超限直接确认", "s25-credit-over-direct")),
				HttpStatus.CONFLICT, "SALES_CREDIT_LIMIT_EXCEEDED");
		JsonNode override = data(post(admin, "/api/admin/sales/orders/" + overLimitOrder.get("id").longValue()
				+ "/submit-credit-override", approvalSubmitBody(overLimitOrder, "客户授信已线下确认",
						"s25-credit-override-" + overLimitOrder.get("id").longValue())));
		assertThat(candidatePermission(override.get("id").longValue())).isEqualTo("sales:credit:override-approve");
		AuthenticatedSession hiddenApprover = createUserAndLogin("stage025-credit-hidden-", "S25_CREDIT_HIDDEN_",
				List.of("platform:approval:view", "platform:todo:view", "sales:credit:override-approve"));
		assertThat(data(get(hiddenApprover, "/api/admin/approval-tasks?scope=TODO&page=1&pageSize=20"))
				.get("total").longValue()).isZero();

		AuthenticatedSession approver = createUserAndLogin("stage025-credit-approver-", "S25_CREDIT_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"sales:order:view", "sales:order-change:view", "sales:credit:view",
						"sales:credit:override-approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意信用例外", "s25-credit-approve-"
						+ override.get("id").longValue())));
		JsonNode confirmed = data(get(admin, "/api/admin/sales/orders/" + overLimitOrder.get("id").longValue()));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		JsonNode exposureAfterOverride = data(get(admin, "/api/admin/sales/customers/" + fixture.customerId()
				+ "/credit-exposure"));
		assertThat(new BigDecimal(exposureAfterOverride.get("usedCredit").asText())).isGreaterThan(BigDecimal.ZERO);
		JsonNode profileForConcurrency = data(get(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId()));
		data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				creditProfilePayload(fixture.customerId(), "2000.00", false, false,
						profileForConcurrency.get("version").longValue())));

		JsonNode firstConcurrent = data(post(admin, "/api/admin/sales/orders",
				manualOrderPayload(fixture, "2.000000", "100.000000")));
		JsonNode secondConcurrent = data(post(admin, "/api/admin/sales/orders",
				manualOrderPayload(fixture, "2.000000", "100.000000")));
		ensurePublicQualifiedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "4.000000");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Future<ResponseEntity<String>> first = executor.submit(() -> {
			start.await(5, TimeUnit.SECONDS);
			return put(admin, "/api/admin/sales/orders/" + firstConcurrent.get("id").longValue() + "/confirm",
					actionBody(firstConcurrent, "并发占用一", "s25-credit-concurrent-a"));
		});
		Future<ResponseEntity<String>> second = executor.submit(() -> {
			start.await(5, TimeUnit.SECONDS);
			return put(admin, "/api/admin/sales/orders/" + secondConcurrent.get("id").longValue() + "/confirm",
					actionBody(secondConcurrent, "并发占用二", "s25-credit-concurrent-b"));
		});
		start.countDown();
		List<Integer> statuses = List.of(first.get().getStatusCode().value(), second.get().getStatusCode().value());
		executor.shutdownNow();
		assertThat(statuses).contains(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());

		JsonNode profileForChange = data(get(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId()));
		data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				creditProfilePayload(fixture.customerId(), "1800.00", false, false,
						profileForChange.get("version").longValue())));
		JsonNode orderForCreditChange = data(get(admin, "/api/admin/sales/orders/"
				+ confirmed.get("id").longValue()));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderForCreditChange.get("id").longValue()
				+ "/changes", orderChangePayload(orderForCreditChange, "31.000000", "50.000000",
						LocalDate.now().plusDays(15), "s25-credit-change-create-"
								+ orderForCreditChange.get("id").longValue())));
		JsonNode changeOverride = data(post(admin, "/api/admin/sales/order-changes/" + change.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(change, "变更增加信用例外",
						"s25-credit-change-submit-" + change.get("id").longValue())));
		assertThat(changeOverride.get("sceneCode").asText()).isEqualTo("SALES_ORDER_CHANGE_CREDIT_OVERRIDE");
		JsonNode currentProfile = data(get(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId()));
		data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				creditProfilePayload(fixture.customerId(), "500.00", true, false,
						currentProfile.get("version").longValue())));
		JsonNode changeTask = firstTask(approver);
		assertError(post(approver, "/api/admin/approval-tasks/" + taskActionId(changeTask) + "/approve",
				approvalActionBody(taskActionId(changeTask), "冻结后不得通过",
						"s25-credit-change-approve-" + changeOverride.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CREDIT_FROZEN");
		assertThat(countRows("sal_credit_check_log", "source_type = 'SALES_ORDER_CHANGE' and source_id = ?",
				change.get("id").longValue())).isGreaterThanOrEqualTo(2);
	}

	@Test
	void 信用缺档冻结逾期停用和订单取消必须强制公开门禁与幂等() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_CREDIT_GUARD_");

		JsonNode missingProfileOrder = data(post(admin, "/api/admin/sales/orders",
				manualOrderPayload(fixture, "2.000000", "100.000000")));
		assertError(put(admin, "/api/admin/sales/orders/" + missingProfileOrder.get("id").longValue()
				+ "/confirm", actionBody(missingProfileOrder, "缺档确认",
						"s25-credit-missing-confirm-" + missingProfileOrder.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CREDIT_PROFILE_MISSING");

		JsonNode frozenProfile = data(post(admin, "/api/admin/sales/credit-profiles",
				creditProfilePayload(fixture.customerId(), "100000.00", true, false)));
		assertError(put(admin, "/api/admin/sales/orders/" + missingProfileOrder.get("id").longValue()
				+ "/confirm", actionBody(missingProfileOrder, "冻结确认",
						"s25-credit-frozen-confirm-" + missingProfileOrder.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CREDIT_FROZEN");

		JsonNode overdueProfile = data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				creditProfilePayload(fixture.customerId(), "100000.00", false, true,
						frozenProfile.get("version").longValue())));
		assertError(put(admin, "/api/admin/sales/orders/" + missingProfileOrder.get("id").longValue()
				+ "/confirm", actionBody(missingProfileOrder, "逾期确认",
						"s25-credit-overdue-confirm-" + missingProfileOrder.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_CREDIT_BLOCKED");

		JsonNode activeProfile = data(put(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId(),
				creditProfilePayload(fixture.customerId(), "100000.00", false, false,
						overdueProfile.get("version").longValue())));
		assertThat(activeProfile.get("frozen").booleanValue()).isFalse();
		ensurePublicQualifiedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "2.000000");
		JsonNode confirmed = data(put(admin, "/api/admin/sales/orders/" + missingProfileOrder.get("id").longValue()
				+ "/confirm", actionBody(missingProfileOrder, "信用恢复确认",
						"s25-credit-pass-confirm-" + missingProfileOrder.get("id").longValue())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(countRows("sal_credit_check_log", "source_type = 'SALES_ORDER' and source_id = ?",
				confirmed.get("id").longValue())).isGreaterThanOrEqualTo(4);

		Map<String, Object> disabledPayload = manualOrderPayload(fixture, "1.000000", "10.000000");
		disabledPayload.put("customerId", insertCustomer("S25_DISABLED_CUS_" + SEQUENCE.incrementAndGet(),
				"DISABLED"));
		disabledPayload.remove("projectId");
		disabledPayload.remove("contractId");
		assertError(post(admin, "/api/admin/sales/orders", disabledPayload),
				HttpStatus.BAD_REQUEST, "SALES_CUSTOMER_INVALID");

		assertError(put(admin, "/api/admin/sales/orders/" + confirmed.get("id").longValue() + "/cancel", Map.of()),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		Map<String, Object> cancelPayload = actionBody(confirmed, "客户取消未出库订单",
				"s25-order-cancel-" + confirmed.get("id").longValue());
		JsonNode cancelled = data(put(admin, "/api/admin/sales/orders/" + confirmed.get("id").longValue()
				+ "/cancel", cancelPayload));
		JsonNode cancelledAgain = data(put(admin, "/api/admin/sales/orders/" + confirmed.get("id").longValue()
				+ "/cancel", cancelPayload));
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelledAgain.get("id").longValue()).isEqualTo(cancelled.get("id").longValue());
		assertError(put(admin, "/api/admin/sales/orders/" + confirmed.get("id").longValue() + "/cancel",
				actionBody(confirmed.get("version").longValue(), "同键异载荷",
						"s25-order-cancel-" + confirmed.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_ACTION_IDEMPOTENCY_CONFLICT");
		assertError(put(admin, "/api/admin/sales/orders/" + confirmed.get("id").longValue() + "/cancel",
				actionBody(confirmed.get("version").longValue(), "异键陈旧版本",
						"s25-order-cancel-stale-" + confirmed.get("id").longValue())),
				HttpStatus.CONFLICT, "VERSION_CONFLICT");
	}

	@Test
	void 短交关闭项目履约关闭和历史SHIPPED兼容必须移出有效需求但不伪造025事实() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_CLOSE_");
		JsonNode order = createConfirmedManualOrder(admin, fixture, "10.000000", "40.000000",
				"s25-close-order");
		JsonNode plan = firstItem(data(get(admin, "/api/admin/sales/delivery-plans?orderId="
				+ order.get("id").longValue() + "&page=1&pageSize=20")));
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "20.000000");
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + order.get("id").longValue()
				+ "/shipments", shipmentPayload(fixture, plan.get("id").longValue(),
						firstLine(order).get("id").longValue(), "6.000000", LocalDate.now())));
		data(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				actionBody(shipment, "部分出库", "s25-close-post-" + shipment.get("id").longValue())));
		JsonNode orderAfterShipment = data(get(admin, "/api/admin/sales/orders/" + order.get("id").longValue()));
		assertError(put(admin, "/api/admin/sales/orders/" + orderAfterShipment.get("id").longValue() + "/close",
				actionBody(orderAfterShipment, "欠量直接关闭", "s25-close-without-approval")),
				HttpStatus.CONFLICT, "SALES_ORDER_SHORT_CLOSE_APPROVAL_REQUIRED");

		JsonNode shortCloseCandidate = data(get(admin, "/api/admin/sales/orders/"
				+ orderAfterShipment.get("id").longValue()));
		JsonNode shortClose = data(post(admin, "/api/admin/sales/orders/" + orderAfterShipment.get("id").longValue()
				+ "/submit-short-close", approvalSubmitBody(shortCloseCandidate, "客户确认短交",
						"s25-short-close-submit-" + orderAfterShipment.get("id").longValue())));
		assertThat(shortClose.get("sceneCode").asText()).isEqualTo("SALES_ORDER_SHORT_CLOSE");
		assertThat(candidatePermission(shortClose.get("id").longValue())).isEqualTo("sales:order:short-close-approve");
		AuthenticatedSession approver = createUserAndLogin("stage025-short-approver-", "S25_SHORT_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"sales:order:view", "sales:order:short-close-approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意短交", "s25-short-close-approve-"
						+ shortClose.get("id").longValue())));
		JsonNode closedOrder = data(get(admin, "/api/admin/sales/orders/" + order.get("id").longValue()));
		assertThat(closedOrder.get("status").asText()).isEqualTo("CLOSED");
		assertThat(data(get(admin, "/api/admin/sales/effective-demands?orderId=" + order.get("id").longValue()
				+ "&countedOnly=true&page=1&pageSize=20")).get("total").longValue()).isZero();

		JsonNode fulfillment = data(get(admin, "/api/admin/sales-projects/" + fixture.projectId()
				+ "/fulfillment"));
		assertThat(fulfillment.get("closeBlocked").booleanValue()).isFalse();
		JsonNode closedFulfillment = data(post(admin, "/api/admin/sales-projects/" + fixture.projectId()
				+ "/close-sales-fulfillment", actionBody(fulfillment, "销售履约完成",
						"s25-project-fulfillment-close-" + fixture.projectId())));
		assertThat(closedFulfillment.get("salesFulfillmentStatus").asText()).isEqualTo("CLOSED");

		long legacyOrderId = insertLegacyShippedOrder(fixture);
		JsonNode legacyFulfillment = data(get(admin, "/api/admin/sales-projects/" + fixture.projectId()
				+ "/fulfillment?includeLegacy=true"));
		assertThat(legacyFulfillment.toString()).contains(Long.toString(legacyOrderId),
				"legacyDeliveryPlanCompatible");
		assertThat(countRows("sal_sales_order_snapshot", "order_id = ?", legacyOrderId)).isZero();
	}

	@Test
	void 订单变更列表更新取消和交付计划关闭必须阻断旧口径回归() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_CHANGE_PLAN_");
		JsonNode order = createConfirmedManualOrder(admin, fixture, "6.000000", "100.000000",
				"s25-change-plan-order");
		long orderId = order.get("id").longValue();
		JsonNode orderLine = firstLine(order);

		JsonNode replaced = data(put(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans",
				deliveryPlanReplacePayload(order, List.of(
						deliveryPlanReplaceLine(orderLine, "2.000000", LocalDate.now().plusDays(5), "首批"),
						deliveryPlanReplaceLine(orderLine, "4.000000", LocalDate.now().plusDays(12), "尾批")),
						"s25-plan-replace-" + orderId)));
		assertThat(replaced.get("lines").size()).isEqualTo(2);
		JsonNode listedPlans = data(get(admin, "/api/admin/sales/delivery-plans?orderId=" + orderId
				+ "&countedOnly=true&page=1&pageSize=20"));
		assertThat(listedPlans.get("items").size()).isEqualTo(2);
		assertThat(sumDecimal(listedPlans, "plannedQuantity")).isEqualByComparingTo(new BigDecimal("6.000000"));
		JsonNode closedPlan = data(put(admin, "/api/admin/sales/orders/" + orderId + "/delivery-plans/"
				+ firstItem(listedPlans).get("id").longValue() + "/close",
				actionBody(firstItem(listedPlans), "客户取消首批",
						"s25-plan-close-" + firstItem(listedPlans).get("id").longValue())));
		assertThat(closedPlan.get("status").asText()).isEqualTo("CLOSED");
		assertThat(data(get(admin, "/api/admin/sales/delivery-plans?orderId=" + orderId
				+ "&countedOnly=true&page=1&pageSize=20")).get("items").size()).isEqualTo(1);

		JsonNode latestOrder = data(get(admin, "/api/admin/sales/orders/" + orderId));
		JsonNode change = data(post(admin, "/api/admin/sales/orders/" + orderId + "/changes",
				orderChangePayload(latestOrder, "7.000000", "105.000000", LocalDate.now().plusDays(20),
						"s25-change-list-create-" + orderId)));
		JsonNode changeList = data(get(admin, "/api/admin/sales/orders/" + orderId
				+ "/changes?status=DRAFT&page=1&pageSize=20"));
		assertAllowedActions(findItemById(changeList, change.get("id").longValue()), "UPDATE", "CANCEL",
				"SUBMIT_APPROVAL");
		assertAttachmentObjectAccepted("SALES_ORDER_CHANGE", change.get("id").longValue());

		Map<String, Object> updateChange = orderChangePayload(latestOrder, "8.000000", "106.000000",
				LocalDate.now().plusDays(21), "s25-change-update-" + change.get("id").longValue());
		updateChange.remove("orderVersion");
		updateChange.put("version", change.get("version").longValue());
		JsonNode updatedChange = data(put(admin, "/api/admin/sales/order-changes/"
				+ change.get("id").longValue(), updateChange));
		assertDecimal(firstLine(updatedChange), "newQuantity", "8.000000");
		JsonNode cancelledChange = data(post(admin, "/api/admin/sales/order-changes/"
				+ updatedChange.get("id").longValue() + "/cancel",
				actionBody(updatedChange, "取消订单变更",
						"s25-change-cancel-" + updatedChange.get("id").longValue())));
		assertThat(cancelledChange.get("status").asText()).isEqualTo("CANCELLED");

		JsonNode postedOrder = createConfirmedManualOrder(admin, fixture, "10.000000", "50.000000",
				"s25-change-below-order");
		JsonNode plan = firstItem(data(get(admin, "/api/admin/sales/delivery-plans?orderId="
				+ postedOrder.get("id").longValue() + "&page=1&pageSize=20")));
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "20.000000");
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + postedOrder.get("id").longValue()
				+ "/shipments", shipmentPayload(fixture, plan.get("id").longValue(),
						firstLine(postedOrder).get("id").longValue(), "4.000000", LocalDate.now())));
		data(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue() + "/post",
				actionBody(shipment, "部分出库", "s25-change-below-post-" + shipment.get("id").longValue())));
		JsonNode afterShipment = data(get(admin, "/api/admin/sales/orders/" + postedOrder.get("id").longValue()));
		assertError(post(admin, "/api/admin/sales/orders/" + postedOrder.get("id").longValue() + "/changes",
				orderChangePayload(afterShipment, "3.000000", "50.000000", LocalDate.now().plusDays(12),
						"s25-change-below-create-" + postedOrder.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_ORDER_CHANGE_BELOW_SHIPPED");

		JsonNode sourceOrder = createConfirmedManualOrder(admin, fixture, "2.000000", "50.000000",
				"s25-change-source-order");
		JsonNode targetOrder = createConfirmedManualOrder(admin, fixture, "2.000000", "50.000000",
				"s25-change-target-order");
		assertError(post(admin, "/api/admin/sales/orders/" + targetOrder.get("id").longValue() + "/changes",
				orderChangePayload(targetOrder, firstLine(sourceOrder), "3.000000", "50.000000",
						LocalDate.now().plusDays(12), "s25-change-immutable-"
								+ targetOrder.get("id").longValue())),
				HttpStatus.CONFLICT, "SALES_ORDER_CHANGE_SOURCE_IMMUTABLE");
	}

	@Test
	void 有效需求默认计入项目关闭阻断非提前出库和权限脱敏必须可验证() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_DEMAND_DIFF_");
		JsonNode order = createConfirmedManualOrder(admin, fixture, "8.000000", "40.000000",
				"s25-demand-diff-order");
		JsonNode defaultDemand = data(get(admin, "/api/admin/sales/effective-demands?orderId="
				+ order.get("id").longValue() + "&page=1&pageSize=20"));
		assertAllEffectiveDemandCounted(defaultDemand);
		assertDecimal(firstItem(defaultDemand), "openDemandQuantity", "8.000000");

		JsonNode fulfillmentBeforeClose = data(get(admin, "/api/admin/sales-projects/" + fixture.projectId()
				+ "/fulfillment"));
		assertThat(fulfillmentBeforeClose.get("closeBlocked").booleanValue()).isTrue();
		assertThat(fulfillmentBeforeClose.get("blockReasons").toString()).contains("NON_TERMINAL_ORDER");
		assertAllowedActionsAbsent(fulfillmentBeforeClose, "CLOSE");
		assertError(post(admin, "/api/admin/sales-projects/" + fixture.projectId() + "/close-sales-fulfillment",
				actionBody(fulfillmentBeforeClose, "仍有未终态订单",
						"s25-project-close-blocked-" + fixture.projectId())),
				HttpStatus.CONFLICT, "PROJECT_HAS_OPEN_BUSINESS");

		JsonNode plan = firstItem(data(get(admin, "/api/admin/sales/delivery-plans?orderId="
				+ order.get("id").longValue() + "&page=1&pageSize=20")));
		this.jdbcTemplate.update("update sal_sales_delivery_plan set planned_date = current_date where id = ?",
				plan.get("id").longValue());
		seedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), "20.000000");
		JsonNode shipment = data(post(admin, "/api/admin/sales/orders/" + order.get("id").longValue()
				+ "/shipments", shipmentPayload(fixture, plan.get("id").longValue(),
						firstLine(order).get("id").longValue(), "3.000000", LocalDate.now())));
		JsonNode posted = data(put(admin, "/api/admin/sales/shipments/" + shipment.get("id").longValue()
				+ "/post", actionBody(shipment, "", "s25-non-early-post-" + shipment.get("id").longValue())));
		assertThat(posted.get("status").asText()).isEqualTo("POSTED");
		assertTextualDecimal(posted, "totalQuantity");
		assertTextualDecimal(firstLine(posted), "quantity");
		assertTextualDecimal(firstLine(posted), "taxIncludedAmount");

		JsonNode diagnostic = data(get(admin, "/api/admin/sales/effective-demands?orderId="
				+ order.get("id").longValue() + "&countedOnly=false&page=1&pageSize=20"));
		assertThat(diagnostic.get("items")).isNotEmpty();
		assertThat(sumDecimal(diagnostic, "openDemandQuantity")).isEqualByComparingTo(new BigDecimal("5.000000"));

		AuthenticatedSession restricted = createUserAndLogin("stage025-restricted-fulfillment-",
				"S25_RESTRICTED_FULFILLMENT_", List.of("sales:fulfillment:view"));
		JsonNode restrictedFulfillment = data(get(restricted, "/api/admin/sales-projects/" + fixture.projectId()
				+ "/fulfillment"));
		assertThat(restrictedFulfillment.get("contractRestricted").booleanValue()).isTrue();
		assertNullOrMissing(restrictedFulfillment, "contractEffectiveAmount");
		assertThat(restrictedFulfillment.get("creditRestricted").booleanValue()).isTrue();
		assertNullOrMissing(restrictedFulfillment, "creditLimit");

		JsonNode profile = data(get(admin, "/api/admin/sales/credit-profiles/" + fixture.customerId()));
		assertThat(profile.get("customerId").longValue()).isEqualTo(fixture.customerId());
	}

	@Test
	void 有效需求权限脱敏文档任务和列表动作必须满足026与桌面契约() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		Sales025Fixture fixture = createFixture("S25_DEMAND_");
		JsonNode order = createConfirmedManualOrder(admin, fixture, "7.000000", "70.000000",
				"s25-demand-order");
		JsonNode counted = data(get(admin, "/api/admin/sales/effective-demands?projectId=" + fixture.projectId()
				+ "&countedOnly=true&page=1&pageSize=20"));
		JsonNode effectiveLine = firstItem(counted);
		assertThat(effectiveLine.get("countedAsEffectiveDemand").booleanValue()).isTrue();
		assertNullOrMissing(effectiveLine, "excludedReasonCode");
		assertTextualDecimal(effectiveLine, "openDemandQuantity");

		JsonNode diagnostic = data(get(admin, "/api/admin/sales/effective-demands?projectId=" + fixture.projectId()
				+ "&countedOnly=false&page=1&pageSize=20"));
		assertThat(diagnostic.get("items")).isNotEmpty();
		assertThat(diagnostic.toString()).contains("countedAsEffectiveDemand");

		JsonNode quotePage = data(get(admin, "/api/admin/sales/quotes?page=1&pageSize=20"));
		assertPage(quotePage);
		if (!quotePage.get("items").isEmpty()) {
			assertAllowedActionsPresent(quotePage.get("items").get(0));
			assertThat(quotePage.get("items").get(0).has("actionDisabledReason")).isTrue();
		}
		JsonNode orderPage = data(get(admin, "/api/admin/sales/orders?page=1&pageSize=20"));
		assertAllowedActionsPresent(firstItem(orderPage));
		assertThat(firstItem(orderPage).has("actionDisabledReason")).isTrue();

		AuthenticatedSession noDemand = createUserAndLogin("stage025-no-demand-", "S25_NO_DEMAND_",
				List.of("sales:order:view"));
		assertError(get(noDemand, "/api/admin/sales/effective-demands?page=1&pageSize=20"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
		AuthenticatedSession noCredit = createUserAndLogin("stage025-no-credit-", "S25_NO_CREDIT_",
				List.of("sales:fulfillment:view", "sales:order:view", "sales:contract:view"));
		JsonNode restrictedFulfillment = data(get(noCredit, "/api/admin/sales-projects/" + fixture.projectId()
				+ "/fulfillment"));
		assertThat(restrictedFulfillment.get("creditRestricted").booleanValue()).isTrue();
		assertNullOrMissing(restrictedFulfillment, "creditLimit");
		assertNullOrMissing(restrictedFulfillment, "usedCredit");

		JsonNode quote = createApprovedQuote(admin, fixture, "3.000000", "80.000000", "s25-demand-quote");
		JsonNode printTask = data(postWithIdempotencyKey(admin, "/api/admin/print-tasks",
				printTaskPayload("SALES_QUOTE", quote.get("id").longValue(), "SALES_QUOTE_V1"),
				"s25-sales-quote-print-" + quote.get("id").longValue()));
		assertThat(printTask.get("taskType").asText()).isEqualTo("SALES_QUOTE_PRINT");
		assertThat(printTask.get("objectType").asText()).isEqualTo("SALES_QUOTE");
		assertThat(printTask.get("objectId").longValue()).isEqualTo(quote.get("id").longValue());
		JsonNode exportTask = data(postWithIdempotencyKey(admin, "/api/admin/export-tasks",
				documentTaskPayload("SALES_EFFECTIVE_DEMAND_EXPORT", null, null,
						Map.of("projectId", fixture.projectId(), "countedOnly", true)),
				"s25-effective-demand-export-" + fixture.projectId()));
		assertThat(exportTask.get("taskType").asText()).isEqualTo("SALES_EFFECTIVE_DEMAND_EXPORT");
		assertThat(this.jdbcTemplate.queryForObject("""
				select request_payload -> 'filters' ->> 'countedOnly'
				from platform_document_task
				where id = ?
				""", String.class, exportTask.get("id").longValue())).isEqualTo("true");
	}

	private JsonNode createConfirmedManualOrder(AuthenticatedSession admin, Sales025Fixture fixture,
			String quantity, String unitPrice, String keyPrefix) throws Exception {
		ensureCreditProfile(admin, fixture.customerId());
		JsonNode order = data(post(admin, "/api/admin/sales/orders", manualOrderPayload(fixture, quantity, unitPrice)));
		ensurePublicQualifiedStock(fixture.warehouseId(), fixture.materialId(), fixture.unitId(), quantity);
		return data(put(admin, "/api/admin/sales/orders/" + order.get("id").longValue() + "/confirm",
				actionBody(order, "确认" + keyPrefix, keyPrefix + "-confirm-" + order.get("id").longValue())));
	}

	private JsonNode prepareConvertedOrderForConfirmation(AuthenticatedSession admin, JsonNode order,
			Sales025Fixture fixture) throws Exception {
		JsonNode originalLine = firstLine(order);
		Long expectedQuoteLineId = originalLine.get("sourceQuoteLineId").longValue();
		JsonNode updated = data(put(admin, "/api/admin/sales/orders/" + order.get("id").longValue(),
				orderUpdatePayloadWithReservation(order, fixture.warehouseId())));
		assertJsonText(firstLine(updated), "priceSourceType", "QUOTE");
		assertJsonLong(firstLine(updated), "sourceQuoteLineId", expectedQuoteLineId);
		for (JsonNode line : updated.get("lines")) {
			ensurePublicQualifiedStock(fixture.warehouseId(), line.get("materialId").longValue(),
					line.get("unitId").longValue(), line.get("quantity").asText());
		}
		return updated;
	}

	private Map<String, Object> orderUpdatePayloadWithReservation(JsonNode order, long reservationWarehouseId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", order.get("customerId").longValue());
		payload.put("projectId", nullableLong(order, "projectId"));
		payload.put("contractId", nullableLong(order, "contractId"));
		payload.put("orderDate", order.get("orderDate").asText());
		payload.put("expectedShipDate", nullableText(order, "expectedShipDate"));
		payload.put("remark", nullableText(order, "remark"));
		payload.put("version", order.get("version").longValue());
		List<Map<String, Object>> lines = new ArrayList<>();
		for (JsonNode line : order.get("lines")) {
			Map<String, Object> linePayload = new LinkedHashMap<>();
			linePayload.put("lineNo", line.get("lineNo").intValue());
			linePayload.put("materialId", line.get("materialId").longValue());
			linePayload.put("unitId", line.get("unitId").longValue());
			linePayload.put("quantity", line.get("quantity").asText());
			linePayload.put("unitPrice", line.hasNonNull("taxExcludedUnitPrice")
					? line.get("taxExcludedUnitPrice").asText() : line.get("unitPrice").asText());
			linePayload.put("reservationWarehouseId", reservationWarehouseId);
			linePayload.put("expectedShipDate", nullableText(line, "expectedShipDate"));
			linePayload.put("remark", nullableText(line, "remark"));
			lines.add(linePayload);
		}
		payload.put("lines", lines);
		return payload;
	}

	private void ensureCreditProfile(AuthenticatedSession admin, long customerId) throws Exception {
		ResponseEntity<String> existing = get(admin, "/api/admin/sales/credit-profiles/" + customerId);
		if (existing.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
			return;
		}
		assertThat(existing.getStatusCode()).as(existing.getBody()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(code(existing)).as(existing.getBody()).isEqualTo("SALES_CREDIT_BLOCKED");
		data(post(admin, "/api/admin/sales/credit-profiles",
				creditProfilePayload(customerId, "1000000.00", false, false)));
	}

	private JsonNode createApprovedQuote(AuthenticatedSession admin, Sales025Fixture fixture, String quantity,
			String unitPrice, String keyPrefix) throws Exception {
		JsonNode quote = data(post(admin, "/api/admin/sales/quotes", projectQuotePayload(fixture, quantity, unitPrice)));
		JsonNode approval = data(post(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(quote, "报价审批", keyPrefix + "-submit-"
						+ quote.get("id").longValue())));
		AuthenticatedSession approver = createUserAndLogin("stage025-doc-quote-approver-", "S25_DOC_QUOTE_APPROVER_",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"sales:quote:view", "sales:quote:approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意报价打印", keyPrefix + "-approve-"
						+ approval.get("id").longValue())));
		return data(get(admin, "/api/admin/sales/quotes/" + quote.get("id").longValue()));
	}

	private Map<String, Object> projectQuotePayload(Sales025Fixture fixture, String quantity, String unitPrice) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", fixture.customerId());
		payload.put("projectId", fixture.projectId());
		payload.put("quoteDate", LocalDate.now().toString());
		payload.put("validFrom", LocalDate.now().toString());
		payload.put("validTo", LocalDate.now().plusDays(20).toString());
		payload.put("currency", "CNY");
		payload.put("priceMode", "TAX_INCLUDED");
		payload.put("defaultTaxRate", "0.130000");
		payload.put("settlementMethod", "BANK_TRANSFER");
		payload.put("paymentTermDays", 30);
		payload.put("collectionTerms", "验收后30天");
		payload.put("remark", "025 项目报价");
		payload.put("lines", List.of(quoteLine(fixture, 1, quantity, unitPrice, LocalDate.now().plusDays(7))));
		return payload;
	}

	private Map<String, Object> quoteLine(Sales025Fixture fixture, int lineNo, String quantity, String unitPrice,
			LocalDate promisedDate) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("quantity", quantity);
		line.put("unitPrice", unitPrice);
		line.put("taxRate", "0.130000");
		line.put("taxIncludedUnitPrice", unitPrice);
		line.put("taxExcludedUnitPrice", divide(unitPrice, "1.130000"));
		line.put("taxIncludedAmount", amount(quantity, unitPrice));
		line.put("taxExcludedAmount", amount(quantity, divide(unitPrice, "1.130000")));
		line.put("promisedDate", promisedDate.toString());
		line.put("remark", "025 报价行");
		return line;
	}

	private Map<String, Object> convertOrderBody(JsonNode quote, long projectId, long contractId,
			String idempotencyKey) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", quote.get("version").longValue());
		body.put("projectId", projectId);
		body.put("contractId", contractId);
		body.put("idempotencyKey", idempotencyKey);
		body.put("reason", "报价转项目订单");
		return body;
	}

	private Map<String, Object> convertContractBody(JsonNode quote, long projectId, String contractType,
			Long mainContractId, String idempotencyKey) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", quote.get("version").longValue());
		body.put("projectId", projectId);
		body.put("contractType", contractType);
		if (mainContractId != null) {
			body.put("mainContractId", mainContractId);
		}
		body.put("idempotencyKey", idempotencyKey);
		return body;
	}

	private Map<String, Object> manualOrderPayload(Sales025Fixture fixture, String quantity, String unitPrice) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", fixture.customerId());
		payload.put("projectId", fixture.projectId());
		payload.put("contractId", fixture.contractId());
		payload.put("orderDate", LocalDate.now().toString());
		payload.put("expectedShipDate", LocalDate.now().plusDays(10).toString());
		payload.put("currency", "CNY");
		payload.put("priceMode", "TAX_INCLUDED");
		payload.put("settlementMethod", "BANK_TRANSFER");
		payload.put("paymentTermDays", 30);
		payload.put("remark", "025 手工销售订单");
		payload.put("lines", List.of(orderLine(fixture, 1, quantity, unitPrice, LocalDate.now().plusDays(10))));
		return payload;
	}

	private Map<String, Object> orderLine(Sales025Fixture fixture, int lineNo, String quantity, String unitPrice,
			LocalDate promisedDate) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("quantity", quantity);
		line.put("unitPrice", unitPrice);
		line.put("taxRate", "0.130000");
		line.put("taxIncludedUnitPrice", unitPrice);
		line.put("taxExcludedUnitPrice", divide(unitPrice, "1.130000"));
		line.put("taxIncludedAmount", amount(quantity, unitPrice));
		line.put("taxExcludedAmount", amount(quantity, divide(unitPrice, "1.130000")));
		line.put("priceSourceType", "MANUAL");
		line.put("reservationWarehouseId", fixture.warehouseId());
		line.put("expectedShipDate", promisedDate.toString());
		line.put("deliveryPlans", List.of(deliveryPlanLine(1, quantity, promisedDate)));
		line.put("remark", "025 手工订单行");
		return line;
	}

	private Map<String, Object> deliveryPlanLine(int lineNo, String quantity, LocalDate promisedDate) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("quantity", quantity);
		line.put("promisedDate", promisedDate.toString());
		line.put("remark", "025 交付计划");
		return line;
	}

	private Map<String, Object> orderChangePayload(JsonNode order, String targetQuantity, String unitPrice,
			LocalDate promisedDate) {
		return orderChangePayload(order, targetQuantity, unitPrice, promisedDate,
				"s25-order-change-create-" + order.get("id").longValue());
	}

	private Map<String, Object> orderChangePayload(JsonNode order, String targetQuantity, String unitPrice,
			LocalDate promisedDate, String idempotencyKey) {
		return orderChangePayload(order, firstLine(order), targetQuantity, unitPrice, promisedDate, idempotencyKey);
	}

	private Map<String, Object> orderChangePayload(JsonNode order, JsonNode orderLine, String targetQuantity,
			String unitPrice, LocalDate promisedDate, String idempotencyKey) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("orderVersion", order.get("version").longValue());
		payload.put("reason", "调整未交付数量税价交期");
		payload.put("idempotencyKey", idempotencyKey);
		payload.put("lines", List.of(orderChangeLine(orderLine, targetQuantity, unitPrice, promisedDate)));
		return payload;
	}

	private Map<String, Object> orderChangeLine(JsonNode orderLine, String targetQuantity, String unitPrice,
			LocalDate promisedDate) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("orderLineId", orderLine.get("id").longValue());
		line.put("targetQuantity", targetQuantity);
		line.put("taxRate", "0.130000");
		line.put("taxIncludedUnitPrice", unitPrice);
		line.put("taxExcludedUnitPrice", divide(unitPrice, "1.130000"));
		line.put("taxIncludedAmount", amount(targetQuantity, unitPrice));
		line.put("taxExcludedAmount", amount(targetQuantity, divide(unitPrice, "1.130000")));
		line.put("promisedDate", promisedDate.toString());
		return line;
	}

	private Map<String, Object> deliveryPlanReplacePayload(JsonNode order, List<Map<String, Object>> lines,
			String idempotencyKey) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("version", order.get("version").longValue());
		payload.put("reason", "重排交付计划");
		payload.put("idempotencyKey", idempotencyKey);
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> deliveryPlanReplaceLine(JsonNode orderLine, String quantity, LocalDate plannedDate,
			String remark) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("orderLineId", orderLine.get("id").longValue());
		line.put("planDate", plannedDate.toString());
		line.put("quantity", quantity);
		line.put("remark", remark);
		return line;
	}

	private Map<String, Object> shipmentPayload(Sales025Fixture fixture, long planId, long orderLineId,
			String quantity, LocalDate businessDate) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", fixture.warehouseId());
		payload.put("businessDate", businessDate.toString());
		payload.put("remark", "025 销售出库");
		payload.put("lines", List.of(shipmentLine(fixture, planId, orderLineId, quantity)));
		return payload;
	}

	private Map<String, Object> shipmentLine(Sales025Fixture fixture, long planId, long orderLineId,
			String quantity) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("deliveryPlanId", planId);
		line.put("orderLineId", orderLineId);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("quantity", quantity);
		line.put("earlyDeliveryReason", "客户要求提前交付");
		line.put("remark", "025 出库行");
		return line;
	}

	private Map<String, Object> salesReturnPayload(long shipmentId, long shipmentLineId, String quantity) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceShipmentId", shipmentId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("reason", "025 销售退货");
		payload.put("lines", List.of(Map.of(
				"lineNo", 1,
				"sourceShipmentLineId", shipmentLineId,
				"quantity", quantity,
				"reason", "025 销售退货行")));
		return payload;
	}

	private Map<String, Object> receivablePayload(long shipmentId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceType", "SALES_SHIPMENT");
		payload.put("sourceId", shipmentId);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("dueDate", LocalDate.now().plusDays(30).toString());
		payload.put("remark", remark);
		return payload;
	}

	private Map<String, Object> creditProfilePayload(long customerId, String creditLimit, boolean frozen,
			boolean blockOverdue) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("creditLimit", creditLimit);
		payload.put("frozen", frozen);
		payload.put("blockOverdue", blockOverdue);
		payload.put("reviewDate", LocalDate.now().toString());
		payload.put("remark", "025 信用档案");
		return payload;
	}

	private Map<String, Object> creditProfilePayload(long customerId, String creditLimit, boolean frozen,
			boolean blockOverdue, long version) {
		Map<String, Object> payload = creditProfilePayload(customerId, creditLimit, frozen, blockOverdue);
		payload.put("version", version);
		return payload;
	}

	private Map<String, Object> documentTaskPayload(String taskType, String objectType, Long objectId,
			Map<String, Object> filters) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("taskType", taskType);
		if (objectType != null) {
			payload.put("objectType", objectType);
		}
		if (objectId != null) {
			payload.put("objectId", objectId);
		}
		payload.put("filters", filters);
		return payload;
	}

	private Map<String, Object> printTaskPayload(String objectType, long objectId, String templateCode) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("objectType", objectType);
		payload.put("objectId", objectId);
		payload.put("templateCode", templateCode);
		return payload;
	}

	private Map<String, Object> actionBody(JsonNode object, String reason, String idempotencyKey) {
		return actionBody(object.get("version").longValue(), reason, idempotencyKey);
	}

	private Map<String, Object> actionBody(long version, String reason, String idempotencyKey) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("version", version);
		body.put("reason", reason);
		body.put("idempotencyKey", idempotencyKey);
		return body;
	}

	private Map<String, Object> approvalSubmitBody(JsonNode object, String reason, String idempotencyKey) {
		return actionBody(object, reason, idempotencyKey);
	}

	private Map<String, Object> approvalActionBody(long taskId, String comment, String idempotencyKey) {
		return Map.of("version", taskVersion(taskId), "comment", comment, "idempotencyKey", idempotencyKey);
	}

	private Sales025Fixture createFixture(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit(prefix + "UNIT_" + suffix);
		long warehouseId = insertWarehouse(prefix + "WH_" + suffix);
		long customerId = insertCustomer(prefix + "CUS_" + suffix, "ENABLED");
		long otherCustomerId = insertCustomer(prefix + "CUS_OTHER_" + suffix, "ENABLED");
		long categoryId = insertMaterialCategory(prefix + "CAT_" + suffix);
		long materialId = insertMaterial(prefix + "MAT_" + suffix, categoryId, unitId);
		long secondMaterialId = insertMaterial(prefix + "MAT_B_" + suffix, categoryId, unitId);
		long projectId = insertProject(prefix + "PRJ_" + suffix, customerId, "ACTIVE");
		long inactiveProjectId = insertProject(prefix + "PRJ_INACTIVE_" + suffix, customerId, "DRAFT");
		long otherProjectId = insertProject(prefix + "PRJ_OTHER_" + suffix, otherCustomerId, "ACTIVE");
		long contractId = insertContract(prefix + "CON_" + suffix, projectId, "MAIN", null, "EFFECTIVE",
				"100000.00");
		long supplementContractId = insertContract(prefix + "SUP_" + suffix, projectId, "SUPPLEMENT", contractId,
				"EFFECTIVE", "5000.00");
		long otherContractId = insertContract(prefix + "CON_OTHER_" + suffix, otherProjectId, "MAIN", null,
				"EFFECTIVE", "100000.00");
		return new Sales025Fixture(unitId, warehouseId, customerId, otherCustomerId, categoryId, materialId,
				secondMaterialId, projectId, inactiveProjectId, otherProjectId, contractId, supplementContractId,
				otherContractId);
	}

	private long insertUnit(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (
					code, name, precision_scale, status, sort_order, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, 6, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "单位");
	}

	private long insertWarehouse(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_warehouse (code, name, warehouse_type, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'NORMAL', 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "仓库");
	}

	private long insertCustomer(String code, String status) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "客户", status);
	}

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "分类");
	}

	private long insertMaterial(String code, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '025规格', 'FINISHED_GOOD', 'SELF_MADE', ?, ?, 'ENABLED',
					'DIRECT_MATERIAL', 'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "成品", categoryId, unitId);
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

	private long insertContract(String code, long projectId, String contractType, Long mainContractId, String status,
			String amount) {
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project_contract (
					contract_no, external_contract_no, project_id, contract_type, main_contract_id, name,
					signed_date, effective_start_date, effective_end_date, amount, status, remark,
					created_by, created_at, updated_by, updated_at, activated_by, activated_at
				)
				values (?, ?, ?, ?, ?, ?, current_date, current_date, current_date + interval '90 day',
					?, ?, ?, 'test', now(), 'test', now(),
					case when ? = 'EFFECTIVE' then 'test' end, case when ? = 'EFFECTIVE' then now() end)
				returning id
				""", Long.class, code, "EXT-" + code, projectId, contractType, mainContractId, code + "合同",
				new BigDecimal(amount), status, code + "合同", status, status);
	}

	private void seedStock(long warehouseId, long materialId, long unitId, String quantity) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitCost = new BigDecimal("10.000000");
		BigDecimal amount = quantityValue.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP);
		long publicPoolId = this.jdbcTemplate.queryForObject("""
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
				returning id
				""", Long.class, materialId, quantityValue, amount, unitCost);
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quality_status, ownership_type, project_id, quantity_on_hand,
					locked_quantity, valuation_state, inventory_amount, average_unit_cost, public_pool_id,
					created_at, updated_at
				)
				values (?, ?, ?, 'QUALIFIED', 'PUBLIC', null, ?, 0, 'VALUED', ?, ?, ?, now(), now())
				on conflict do nothing
				""", warehouseId, materialId, unitId, quantityValue, amount, unitCost, publicPoolId);
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set quantity_on_hand = ?,
				    valuation_state = 'VALUED',
				    inventory_amount = ?,
				    average_unit_cost = ?,
				    public_pool_id = ?,
				    updated_at = now()
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and ownership_type = 'PUBLIC'
				  and project_id is null
				""", quantityValue, amount, unitCost, publicPoolId, warehouseId, materialId);
	}

	private void ensurePublicQualifiedStock(long warehouseId, long materialId, long unitId,
			String requiredAvailableQuantity) {
		BigDecimal requiredAvailable = new BigDecimal(requiredAvailableQuantity);
		BigDecimal currentOnHand = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and ownership_type = 'PUBLIC'
				  and project_id is null
				  and batch_id is null
				  and serial_id is null
				""", BigDecimal.class, warehouseId, materialId);
		BigDecimal activeReserved = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity - released_quantity - consumed_quantity), 0)
				from inv_stock_reservation
				where warehouse_id = ?
				  and material_id = ?
				  and quality_status = 'QUALIFIED'
				  and ownership_type = 'PUBLIC'
				  and project_id is null
				  and reservation_type = 'RESERVATION'
				  and status = 'ACTIVE'
				""", BigDecimal.class, warehouseId, materialId);
		BigDecimal requiredOnHand = nullToZero(activeReserved).add(requiredAvailable);
		if (nullToZero(currentOnHand).compareTo(requiredOnHand) < 0) {
			seedStock(warehouseId, materialId, unitId, requiredOnHand.toPlainString());
		}
	}

	private BigDecimal nullToZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private Long nullableLong(JsonNode node, String field) {
		return node.has(field) && !node.get(field).isNull() ? node.get(field).longValue() : null;
	}

	private String nullableText(JsonNode node, String field) {
		return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
	}

	private long insertLegacyShippedOrder(Sales025Fixture fixture) {
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark, project_id, contract_id,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, current_date - interval '30 day', current_date - interval '20 day', 'SHIPPED',
					'025历史SHIPPED兼容', ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "S25_LEG_SO_" + SEQUENCE.incrementAndGet(), fixture.customerId(),
				fixture.projectId(), fixture.contractId());
		this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, created_at, updated_at, currency, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
					tax_included_amount, price_source_type
				)
				values (?, 1, ?, ?, 2.000000, 2.000000, 10.000000, current_date - interval '20 day',
					'025历史订单行', now(), now(), 'CNY', 0.000000, 10.000000, 10.000000, 20.00, 0.00,
					20.00, 'LEGACY_MANUAL')
				returning id
				""", Long.class, orderId, fixture.materialId(), fixture.unitId());
		return orderId;
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
				""", Long.class, rolePrefix + suffix, "025 测试角色" + suffix, "025 测试角色");
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

	private JsonNode firstTask(AuthenticatedSession session) throws Exception {
		JsonNode page = data(get(session, "/api/admin/approval-tasks?scope=TODO&page=1&pageSize=20"));
		assertThat(page.get("items")).isNotEmpty();
		return page.get("items").get(0);
	}

	private long taskActionId(JsonNode task) {
		return task.has("taskId") ? task.get("taskId").longValue() : task.get("id").longValue();
	}

	private long taskVersion(long taskId) {
		return this.jdbcTemplate.queryForObject("select version from platform_approval_task where id = ?",
				Long.class, taskId);
	}

	private String candidatePermission(long approvalId) {
		return this.jdbcTemplate.queryForObject("""
				select t.candidate_permission_code
				from platform_approval_task t
				where t.instance_id = ?
				order by t.id
				limit 1
				""", String.class, approvalId);
	}

	private long countRows(String tableName, String whereSql, Object... args) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereSql,
				Long.class, args);
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
			JsonNode data = this.objectMapper.readTree(response.getBody()).get("data");
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

	private ResponseEntity<String> postWithIdempotencyKey(AuthenticatedSession session, String path, Object body,
			String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, session.sessionCookie());
		headers.add(session.csrfSession().headerName(), session.csrfSession().token());
		headers.add("Idempotency-Key", idempotencyKey);
		return this.restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> put(AuthenticatedSession session, String path, Object body) {
		return exchange(session, HttpMethod.PUT, path, body);
	}

	private ResponseEntity<String> exchange(AuthenticatedSession session, HttpMethod method, String path, Object body) {
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
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		JsonNode body = this.objectMapper.readTree(response.getBody());
		assertThat(body.get("code").asText()).as(response.getBody()).isEqualTo("OK");
		return body.get("data");
	}

	private String code(ResponseEntity<String> response) throws Exception {
		return this.objectMapper.readTree(response.getBody()).get("code").asText();
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(code(response)).as(response.getBody()).isEqualTo(code);
	}

	private void assertTextualDecimal(JsonNode node, String field) {
		assertThat(node.has(field)).as("缺少十进制字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isTextual()).as(field + " 必须是 JSON 字符串，实际响应：" + node).isTrue();
		assertThat(new BigDecimal(node.get(field).asText())).as(field + " 必须可解析").isNotNull();
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertTextualDecimal(node, field);
		assertThat(new BigDecimal(node.get(field).asText()).compareTo(new BigDecimal(expected))).isZero();
	}

	private void assertJsonText(JsonNode node, String field, String expected) {
		assertThat(node.has(field)).as("缺少字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).asText()).as(field + " 实际响应：" + node).isEqualTo(expected);
	}

	private void assertJsonLong(JsonNode node, String field, long expected) {
		assertThat(node.has(field)).as("缺少字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).longValue()).as(field + " 实际响应：" + node).isEqualTo(expected);
	}

	private void assertNullOrMissing(JsonNode node, String field) {
		assertThat(!node.has(field) || node.get(field).isNull())
			.as(field + " 必须为空或不可见，实际响应：" + node)
			.isTrue();
	}

	private JsonNode firstLine(JsonNode node) {
		assertThat(node.has("lines")).as("响应缺少 lines：" + node).isTrue();
		assertThat(node.get("lines")).as("lines 不能为空：" + node).isNotEmpty();
		return node.get("lines").get(0);
	}

	private JsonNode firstItem(JsonNode page) {
		assertPage(page);
		assertThat(page.get("items")).as("分页响应必须至少有一条记录：" + page).isNotEmpty();
		return page.get("items").get(0);
	}

	private JsonNode findItemById(JsonNode page, long id) {
		assertPage(page);
		for (JsonNode item : page.get("items")) {
			if (item.has("id") && item.get("id").longValue() == id) {
				return item;
			}
		}
		throw new AssertionError("分页响应未找到 id=" + id + "，实际响应：" + page);
	}

	private void assertAllEffectiveDemandCounted(JsonNode page) {
		assertPage(page);
		assertThat(page.get("items")).as("有效需求默认 countedOnly=true 必须返回计入项：" + page).isNotEmpty();
		for (JsonNode item : page.get("items")) {
			assertThat(item.get("countedAsEffectiveDemand").booleanValue()).as("默认 countedOnly=true 不得返回排除项："
					+ item).isTrue();
			assertNullOrMissing(item, "excludedReasonCode");
		}
	}

	private BigDecimal sumDecimal(JsonNode page, String field) {
		assertPage(page);
		BigDecimal total = BigDecimal.ZERO;
		for (JsonNode item : page.get("items")) {
			assertTextualDecimal(item, field);
			total = total.add(new BigDecimal(item.get(field).asText()));
		}
		return total;
	}

	private void assertPage(JsonNode page) {
		assertThat(page.has("items")).as("分页响应缺少 items：" + page).isTrue();
		assertThat(page.has("total")).as("分页响应缺少 total：" + page).isTrue();
		assertThat(page.has("page")).as("分页响应缺少 page：" + page).isTrue();
		assertThat(page.has("pageSize")).as("分页响应缺少 pageSize：" + page).isTrue();
	}

	private void assertAllowedActionsPresent(JsonNode node) {
		assertThat(node.has("allowedActions")).as("缺少 allowedActions，实际响应：" + node).isTrue();
		assertThat(node.get("allowedActions").isArray()).as("allowedActions 必须是数组，实际响应：" + node).isTrue();
	}

	private void assertAllowedActions(JsonNode node, String... actions) {
		assertAllowedActionsPresent(node);
		assertThat(actionList(node)).contains(actions);
	}

	private void assertAllowedActionsAbsent(JsonNode node, String... actions) {
		assertAllowedActionsPresent(node);
		assertThat(actionList(node)).doesNotContain(actions);
	}

	private List<String> actionList(JsonNode node) {
		List<String> actual = new ArrayList<>();
		for (JsonNode action : node.get("allowedActions")) {
			actual.add(action.asText());
		}
		return actual;
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

	private String divide(String value, String divisor) {
		return new BigDecimal(value).divide(new BigDecimal(divisor), 6, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	private String amount(String quantity, String price) {
		return new BigDecimal(quantity).multiply(new BigDecimal(price)).setScale(2, java.math.RoundingMode.HALF_UP)
			.toPlainString();
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

	private record Sales025Fixture(
			long unitId,
			long warehouseId,
			long customerId,
			long otherCustomerId,
			long categoryId,
			long materialId,
			long secondMaterialId,
			long projectId,
			long inactiveProjectId,
			long otherProjectId,
			long contractId,
			long supplementContractId,
			long otherContractId) {
	}

}
