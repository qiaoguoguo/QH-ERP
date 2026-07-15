package com.qherp.api.system.stage024;

import com.qherp.api.support.PostgresIntegrationTest;
import com.qherp.api.system.platform.PlatformDocumentTaskWorker;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"qherp.test.context=stage024-procurement-project-sourcing",
				"qherp.platform.task.worker.enabled=false"
		})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage024ProcurementProjectSourcingTests extends PostgresIntegrationTest {

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
		registry.add("qherp.storage.s3.bucket", () -> "qherp-test-private");
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
	void 项目请购询价选价和固定审批必须保护项目专采入口() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_REQ_");

		assertError(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload(fixture, "PROJECT", fixture.inactiveProjectId(), "5.000000")),
				HttpStatus.BAD_REQUEST, "PROCUREMENT_REQUISITION_PROJECT_INVALID");
		assertError(post(admin, "/api/admin/procurement/orders",
				projectOrderWithoutRequisitionPayload(fixture)), HttpStatus.CONFLICT,
				"PROCUREMENT_ORDER_REQUISITION_REQUIRED");

		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload(fixture, "PROJECT", fixture.projectId(), "10.000000")));
		long requisitionId = requisition.get("id").longValue();
		long requisitionLineId = requisition.get("lines").get(0).get("id").longValue();
		assertThat(requisition.get("status").asText()).isEqualTo("DRAFT");
		assertThat(requisition.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(requisition.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertDecimal(requisition.get("lines").get(0), "requestedQuantity", "10.000000");

		JsonNode submitted = data(post(admin,
				"/api/admin/procurement/requisitions/" + requisitionId + "/submit-approval",
				approvalSubmitBody(requisition, "项目请购资料齐备", "s24-req-submit-" + requisitionId)));
		assertThat(submitted.get("sceneCode").asText()).isEqualTo("PROCUREMENT_REQUISITION_APPROVAL");
		assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
		long approvalId = submitted.get("id").longValue();
		long taskId = pendingTaskId(approvalId);
		assertError(post(admin, "/api/admin/approval-tasks/" + taskId + "/approve",
				approvalActionBody(taskId, "提交人不可自批", "s24-req-self-" + taskId)),
				HttpStatus.FORBIDDEN, "APPROVAL_SELF_ACTION_FORBIDDEN");

		AuthenticatedSession hiddenCandidate = createUserAndLogin("stage024-hidden-approver", "S24_HIDDEN_APPROVER",
				List.of("platform:approval:view", "platform:todo:view", "procurement:requisition:approve"));
		JsonNode hiddenTodos = data(get(hiddenCandidate, "/api/admin/approval-tasks?scope=TODO&page=1&pageSize=20"));
		assertThat(hiddenTodos.get("total").longValue()).isZero();
		assertThat(hiddenTodos.get("items")).isEmpty();
		assertError(get(hiddenCandidate, "/api/admin/approvals/" + approvalId), HttpStatus.FORBIDDEN,
				"AUTH_FORBIDDEN");

		AuthenticatedSession approver = createUserAndLogin("stage024-requisition-approver", "S24_REQ_APPROVER",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"procurement:requisition:view", "procurement:requisition:approve"));
		JsonNode approverTask = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(approverTask) + "/approve",
				approvalActionBody(taskActionId(approverTask), "同意项目请购", "s24-req-approve-" + taskId)));
		JsonNode approvedRequisition = data(get(admin, "/api/admin/procurement/requisitions/" + requisitionId));
		assertThat(approvedRequisition.get("status").asText()).isEqualTo("APPROVED");

		JsonNode inquiry = data(post(admin, "/api/admin/procurement/inquiries",
				inquiryPayload(fixture, requisitionId, requisitionLineId)));
		long inquiryId = inquiry.get("id").longValue();
		long inquiryLineId = inquiry.get("lines").get(0).get("id").longValue();
		assertThat(inquiry.get("procurementMode").asText()).isEqualTo("PROJECT");
		JsonNode releasedInquiry = data(put(admin, "/api/admin/procurement/inquiries/" + inquiryId + "/release",
				actionBody(inquiry, "发布项目询价", "s24-inquiry-release-" + inquiryId)));

		JsonNode lowQuote = data(post(admin, "/api/admin/procurement/inquiries/" + inquiryId + "/quotes",
				quotePayload(fixture.supplierId(), inquiryLineId, "9.000000", "0.130000")));
		JsonNode highQuote = data(post(admin, "/api/admin/procurement/inquiries/" + inquiryId + "/quotes",
				quotePayload(fixture.secondSupplierId(), inquiryLineId, "11.000000", "0.130000")));
		data(put(admin, "/api/admin/procurement/inquiries/" + inquiryId + "/complete",
				actionBody(releasedInquiry, "报价收集完成", "s24-inquiry-complete-" + inquiryId)));
		JsonNode selectedQuote = data(put(admin,
				"/api/admin/procurement/inquiries/" + inquiryId + "/quotes/" + highQuote.get("id").longValue()
						+ "/select",
				Map.of("version", highQuote.get("version").longValue(), "reason", "交期满足项目节点，需走例外审批",
						"idempotencyKey", "s24-award-" + inquiryId)));
		assertThat(selectedQuote.get("id").longValue()).isEqualTo(highQuote.get("id").longValue());
		assertThat(selectedQuote.get("status").asText()).isEqualTo("SELECTED");
		assertThat(status("proc_supplier_quote", lowQuote.get("id").longValue())).isEqualTo("REJECTED");

		JsonNode projectOrder = data(post(admin, "/api/admin/procurement/orders",
				projectOrderFromSelectionPayload(fixture, requisitionLineId, selectedQuote)));
		assertThat(projectOrder.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(projectOrder.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertThat(projectOrder.get("priceSourceType").asText()).isEqualTo("QUOTE_SELECTION");
		assertError(put(admin, "/api/admin/procurement/orders/" + projectOrder.get("id").longValue() + "/confirm",
				actionBody(projectOrder, "非最低报价未批直接确认", "s24-project-order-confirm-blocked")),
				HttpStatus.CONFLICT, "PROCUREMENT_ORDER_EXCEPTION_APPROVAL_REQUIRED");
	}

	@Test
	void 公共直采协议偏离例外审批和采购订单打印必须复用固定平台契约() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_PUBLIC_");

		JsonNode agreement = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementPayload(fixture, "PUBLIC", null, "8.000000", "0.130000")));
		JsonNode agreementApproval = data(post(admin,
				"/api/admin/procurement/price-agreements/" + agreement.get("id").longValue()
						+ "/submit-activation",
				approvalSubmitBody(agreement, "公共协议价格生效", "s24-agreement-submit-" + agreement.get("id").longValue())));
		AuthenticatedSession agreementApprover = createUserAndLogin("stage024-agreement-approver",
				"S24_AGREEMENT_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "procurement:price-agreement:view",
						"procurement:price-agreement:approve"));
		JsonNode agreementTask = firstTask(agreementApprover);
		data(post(agreementApprover, "/api/admin/approval-tasks/" + taskActionId(agreementTask) + "/approve",
				approvalActionBody(taskActionId(agreementTask), "同意协议生效",
						"s24-agreement-approve-" + agreementApproval.get("id").longValue())));
		JsonNode activeAgreement = data(get(admin,
				"/api/admin/procurement/price-agreements/" + agreement.get("id").longValue()));

		JsonNode deviatedOrder = data(post(admin, "/api/admin/procurement/orders",
				publicOrderPayload(fixture, "AGREEMENT", "9.500000", "协议价偏离测试",
						activeAgreement.get("lines").get(0).get("id").longValue(), null)));
		assertErrorIn(put(admin, "/api/admin/procurement/orders/" + deviatedOrder.get("id").longValue() + "/confirm",
				actionBody(deviatedOrder, "协议偏离未批", "s24-agreement-deviation-blocked")), HttpStatus.CONFLICT,
				List.of("PROCUREMENT_ORDER_AGREEMENT_DEVIATION", "PROCUREMENT_ORDER_EXCEPTION_APPROVAL_REQUIRED"));

		JsonNode publicDirectOrder = data(post(admin, "/api/admin/procurement/orders",
				publicOrderPayload(fixture, "PUBLIC_DIRECT", "8.000000", "设备急修公共直采",
						null, "生产线停机，需公共直采备件")));
		assertThat(publicDirectOrder.get("ownershipType").asText()).isEqualTo("PUBLIC");
		assertThat(publicDirectOrder.get("projectId").isNull()).isTrue();
		assertError(put(admin, "/api/admin/procurement/orders/" + publicDirectOrder.get("id").longValue() + "/confirm",
				actionBody(publicDirectOrder, "公共直采未批", "s24-public-direct-confirm-blocked")),
				HttpStatus.CONFLICT, "PROCUREMENT_ORDER_PUBLIC_DIRECT_APPROVAL_REQUIRED");

		JsonNode exceptionApproval = data(post(admin,
				"/api/admin/procurement/orders/" + publicDirectOrder.get("id").longValue() + "/submit-exception",
				approvalSubmitBody(publicDirectOrder, "公共直采原因属实",
						"s24-order-exception-submit-" + publicDirectOrder.get("id").longValue())));
		assertThat(exceptionApproval.get("sceneCode").asText()).isEqualTo("PROCUREMENT_ORDER_EXCEPTION_CONFIRM");
		AuthenticatedSession exceptionApprover = createUserAndLogin("stage024-exception-approver",
				"S24_EXCEPTION_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "procurement:order:view", "procurement:order:exception-approve"));
		JsonNode exceptionTask = firstTask(exceptionApprover);
		data(post(exceptionApprover, "/api/admin/approval-tasks/" + taskActionId(exceptionTask) + "/approve",
				approvalActionBody(taskActionId(exceptionTask), "同意公共直采确认",
						"s24-order-exception-approve-" + exceptionApproval.get("id").longValue())));

		JsonNode confirmed = data(get(admin,
				"/api/admin/procurement/orders/" + publicDirectOrder.get("id").longValue()));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(confirmed.get("ownershipType").asText()).isEqualTo("PUBLIC");
		assertThat(confirmed.get("projectId").isNull()).isTrue();
		assertError(put(admin, "/api/admin/procurement/orders/" + confirmed.get("id").longValue(),
				orderProjectMutationPayload(confirmed, fixture.projectId())), HttpStatus.CONFLICT,
				"PROCUREMENT_ORDER_PROJECT_IMMUTABLE");

		JsonNode printTask = data(postWithIdempotencyKey(admin, "/api/admin/print-tasks",
				Map.of("objectType", "PROCUREMENT_ORDER", "objectId", confirmed.get("id").longValue(),
						"templateCode", "PROCUREMENT_ORDER_V1"),
				"s24-order-print-" + confirmed.get("id").longValue()));
		assertThat(printTask.get("taskType").asText()).isEqualTo("PROCUREMENT_ORDER_PRINT");
		assertThat(printTask.get("status").asText()).isEqualTo("QUEUED");
	}

	@Test
	void 到货计划入库估值供给结案和采购退货必须隔离项目公共来源() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FLOW_");
		JsonNode projectOrder = createConfirmedProjectOrder(admin, fixture, "100.000000", "10.000000");
		long orderId = projectOrder.get("id").longValue();
		long orderLineId = projectOrder.get("lines").get(0).get("id").longValue();

		JsonNode schedules = data(put(admin, "/api/admin/procurement/orders/" + orderId + "/schedules",
				Map.of("version", projectOrder.get("version").longValue(),
						"lines", List.of(scheduleLine(orderLineId, 1, "40.000000", LocalDate.now().plusDays(3)),
								scheduleLine(orderLineId, 2, "60.000000", LocalDate.now().plusDays(7))),
						"idempotencyKey", "s24-schedule-" + orderId)));
		assertThat(schedules.get("items")).hasSize(2);
		long firstScheduleId = schedules.get("items").get(0).get("id").longValue();

		JsonNode overReceipt = data(post(admin, "/api/admin/procurement/orders/" + orderId + "/receipts",
				receiptPayload(fixture, orderLineId, firstScheduleId, "40.000001", LocalDate.now())));
		assertError(put(admin, "/api/admin/procurement/receipts/" + overReceipt.get("id").longValue() + "/post",
				actionBody(overReceipt, "零超收拒绝", "s24-overreceipt-post")), HttpStatus.CONFLICT,
				"PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE");

		JsonNode receipt = data(post(admin, "/api/admin/procurement/orders/" + orderId + "/receipts",
				receiptPayload(fixture, orderLineId, firstScheduleId, "40.000000", LocalDate.now())));
		JsonNode postedReceipt = data(put(admin, "/api/admin/procurement/receipts/" + receipt.get("id").longValue()
				+ "/post", actionBody(receipt, "项目专采部分入库", "s24-project-receipt-post")));
		long receiptLineId = postedReceipt.get("lines").get(0).get("id").longValue();
		assertThat(postedReceipt.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(postedReceipt.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertDecimal(postedReceipt.get("lines").get(0), "taxExcludedAmount", "400.00");
		assertThat(countRows("inv_project_cost_layer", "source_type = 'PURCHASE_RECEIPT' and source_line_id = ?",
				receiptLineId)).isOne();
		assertDecimal(projectLayerRemainingAmount(receiptLineId), "400.00");
		assertThat(countRows("inv_public_valuation_pool", "material_id = ?", fixture.materialId())).isZero();

		JsonNode supply = data(get(admin, "/api/admin/procurement/effective-supplies?projectId="
				+ fixture.projectId() + "&materialId=" + fixture.materialId() + "&page=1&pageSize=20"));
		assertThat(supply.get("items")).isNotEmpty();
		assertThat(supply.get("items").get(0).get("countedAsEffectiveSupply").booleanValue()).isTrue();
		assertDecimal(supply.get("items").get(0), "remainingQuantity", "60.000000");

		JsonNode closedSchedule = data(put(admin,
				"/api/admin/procurement/orders/" + orderId + "/schedules/" + schedules.get("items").get(1).get("id")
					.longValue() + "/close",
				Map.of("version", schedules.get("items").get(1).get("version").longValue(),
						"reason", "项目需求取消", "idempotencyKey", "s24-schedule-close-" + orderId)));
		assertThat(closedSchedule.get("status").asText()).isEqualTo("CLOSED");
		JsonNode orderBeforeClose = data(get(admin, "/api/admin/procurement/orders/" + orderId));
		JsonNode closedOrder = data(put(admin, "/api/admin/procurement/orders/" + orderId + "/close",
				Map.of("version", orderBeforeClose.get("version").longValue(), "reason", "未收数量项目需求取消",
						"idempotencyKey", "s24-order-close-" + orderId)));
		assertThat(closedOrder.get("status").asText()).isEqualTo("CLOSED");
		JsonNode supplyAfterClose = data(get(admin, "/api/admin/procurement/effective-supplies?projectId="
				+ fixture.projectId() + "&materialId=" + fixture.materialId() + "&page=1&pageSize=20"));
		assertThat(supplyAfterClose.get("items")).allSatisfy((item) -> assertThat(
				item.get("countedAsEffectiveSupply").booleanValue()).isFalse());

		JsonNode returnSource = data(get(admin, "/api/admin/procurement/return-sources?keyword="
				+ postedReceipt.get("receiptNo").asText()));
		assertThat(returnSource.get("items")).isNotEmpty();
		JsonNode purchaseReturn = data(post(admin, "/api/admin/procurement/returns",
				purchaseReturnPayload(postedReceipt.get("id").longValue(), receiptLineId, "20.000000",
						"s24-return-" + receiptLineId)));
		JsonNode sameReturn = data(post(admin, "/api/admin/procurement/returns",
				purchaseReturnPayload(postedReceipt.get("id").longValue(), receiptLineId, "20.000000",
						"s24-return-" + receiptLineId)));
		assertThat(sameReturn.get("id").longValue()).isEqualTo(purchaseReturn.get("id").longValue());
		JsonNode postedReturn = data(put(admin, "/api/admin/procurement/returns/"
				+ purchaseReturn.get("id").longValue() + "/post",
				actionBody(purchaseReturn, "项目专采按原成本层退货", "s24-return-post-" + purchaseReturn.get("id")
					.longValue())));
		assertThat(postedReturn.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(postedReturn.get("projectId").longValue()).isEqualTo(fixture.projectId());
		assertDecimal(projectLayerRemainingAmount(receiptLineId), "200.00");
		assertThat(status("proc_purchase_order", orderId)).isEqualTo("CLOSED");
	}

	@Test
	void 锁定期间并发幂等和成本权限必须保护过账与价值字段() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_GUARD_");
		JsonNode projectOrder = createConfirmedProjectOrder(admin, fixture, "10.000000", "12.000000");
		long orderId = projectOrder.get("id").longValue();
		long orderLineId = projectOrder.get("lines").get(0).get("id").longValue();
		LocalDate lockedDate = LocalDate.of(2096, 7, 10);
		lockPeriod(lockedDate);

		assertError(post(admin, "/api/admin/procurement/orders/" + orderId + "/receipts",
				receiptPayload(fixture, orderLineId, null, "2.000000", lockedDate)),
				HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");

		JsonNode receipt = data(post(admin, "/api/admin/procurement/orders/" + orderId + "/receipts",
				receiptPayload(fixture, orderLineId, null, "2.000000", LocalDate.now())));
		JsonNode firstPost = data(put(admin, "/api/admin/procurement/receipts/" + receipt.get("id").longValue()
				+ "/post", actionBody(receipt, "第一次过账", "s24-receipt-idempotent-" + receipt.get("id").longValue())));
		JsonNode retryPost = data(put(admin, "/api/admin/procurement/receipts/" + receipt.get("id").longValue()
				+ "/post", actionBody(receipt, "第一次过账", "s24-receipt-idempotent-" + receipt.get("id").longValue())));
		assertThat(retryPost.get("id").longValue()).isEqualTo(firstPost.get("id").longValue());
		assertThat(countRows("inv_stock_movement", "source_type = 'PURCHASE_RECEIPT' and source_id = ?",
				firstPost.get("id").longValue())).isOne();
		assertThat(countRows("inv_value_movement", "source_type = 'PURCHASE_RECEIPT' and source_id = ?",
				firstPost.get("id").longValue())).isOne();

		AuthenticatedSession noCost = createUserAndLogin("stage024-no-cost", "S24_NO_COST",
				List.of("procurement:supply:view", "procurement:receipt:view", "inventory:balance:view"));
		JsonNode restrictedSupply = data(get(noCost, "/api/admin/procurement/effective-supplies?projectId="
				+ fixture.projectId() + "&materialId=" + fixture.materialId() + "&page=1&pageSize=20"));
		assertThat(restrictedSupply.toString()).doesNotContain("unitCost", "inventoryAmount", "remainingAmount",
				"valueMovementAmount", "costLayerAmount");
	}

	@Test
	void 桌面采购基础资源必须覆盖列表修改取消结案选择和动作集契约() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_RESOURCE_");

		JsonNode draftRequisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionResourcePayload(fixture, "PROJECT", fixture.projectId(), "8.000000")));
		assertThat(draftRequisition.get("status").asText()).isEqualTo("DRAFT");
		assertAllowedActions(draftRequisition, "UPDATE", "SUBMIT", "CANCEL");
		assertTextualDecimal(draftRequisition, "totalQuantity");
		assertTextualDecimal(draftRequisition.get("lines").get(0), "quantity");
		JsonNode requisitionList = data(get(admin,
				"/api/admin/procurement/requisitions?procurementMode=PROJECT&status=DRAFT&page=1&pageSize=10"));
		assertPage(requisitionList);
		assertAllowedActions(firstItem(requisitionList), "UPDATE", "SUBMIT", "CANCEL");
		JsonNode updatedRequisition = data(put(admin,
				"/api/admin/procurement/requisitions/" + draftRequisition.get("id").longValue(),
				requisitionResourceUpdatePayload(fixture, draftRequisition, "9.000000")));
		assertThat(updatedRequisition.get("status").asText()).isEqualTo("DRAFT");
		assertTextualDecimal(updatedRequisition.get("lines").get(0), "quantity");
		JsonNode cancelledRequisition = data(put(admin,
				"/api/admin/procurement/requisitions/" + updatedRequisition.get("id").longValue() + "/cancel",
				versionActionBody(updatedRequisition, "s24-resource-req-cancel")));
		assertThat(cancelledRequisition.get("status").asText()).isEqualTo("CANCELLED");
		assertAllowedActionsPresent(cancelledRequisition);

		JsonNode approvedRequisition = createApprovedRequisition(admin, fixture, "10.000000");
		assertAllowedActions(approvedRequisition, "CREATE_INQUIRY", "CREATE_ORDER", "CLOSE");
		JsonNode closeOnlyRequisition = createApprovedRequisition(admin, fixture, "3.000000");
		JsonNode closedRequisition = data(put(admin,
				"/api/admin/procurement/requisitions/" + closeOnlyRequisition.get("id").longValue() + "/close",
				actionBody(closeOnlyRequisition, "未转数量不再采购", "s24-resource-req-close")));
		assertThat(closedRequisition.get("status").asText()).isEqualTo("CLOSED");
		assertAllowedActionsPresent(closedRequisition);

		long requisitionId = approvedRequisition.get("id").longValue();
		long requisitionLineId = approvedRequisition.get("lines").get(0).get("id").longValue();
		JsonNode draftInquiry = data(post(admin, "/api/admin/procurement/inquiries",
				inquiryResourcePayload(fixture, requisitionId, requisitionLineId, "10.000000")));
		assertThat(draftInquiry.get("status").asText()).isEqualTo("DRAFT");
		assertAllowedActions(draftInquiry, "UPDATE", "RELEASE", "CANCEL");
		assertTextualDecimal(draftInquiry.get("lines").get(0), "quantity");
		JsonNode inquiryList = data(get(admin,
				"/api/admin/procurement/inquiries?procurementMode=PROJECT&status=DRAFT&page=1&pageSize=10"));
		assertPage(inquiryList);
		assertAllowedActions(firstItem(inquiryList), "UPDATE", "RELEASE", "CANCEL");
		JsonNode updatedInquiry = data(put(admin,
				"/api/admin/procurement/inquiries/" + draftInquiry.get("id").longValue(),
				inquiryResourceUpdatePayload(fixture, draftInquiry, requisitionId, requisitionLineId, "10.000000")));
		assertThat(updatedInquiry.get("status").asText()).isEqualTo("DRAFT");
		JsonNode inquiryToCancelRequisition = createApprovedRequisition(admin, fixture, "2.000000");
		JsonNode inquiryToCancel = data(post(admin, "/api/admin/procurement/inquiries",
				inquiryResourcePayload(fixture, inquiryToCancelRequisition.get("id").longValue(),
						inquiryToCancelRequisition.get("lines").get(0).get("id").longValue(), "2.000000")));
		JsonNode cancelledInquiry = data(put(admin,
				"/api/admin/procurement/inquiries/" + inquiryToCancel.get("id").longValue() + "/cancel",
				versionActionBody(inquiryToCancel, "s24-resource-inquiry-cancel")));
		assertThat(cancelledInquiry.get("status").asText()).isEqualTo("CANCELLED");
		assertAllowedActionsPresent(cancelledInquiry);

		JsonNode releasedInquiry = data(put(admin,
				"/api/admin/procurement/inquiries/" + updatedInquiry.get("id").longValue() + "/release",
				actionBody(updatedInquiry, "发布询价", "s24-resource-inquiry-release")));
		JsonNode quoteToSelect = data(post(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue() + "/quotes",
				supplierQuoteResourcePayload(fixture.supplierId(), fixture.materialId(), "10.000000", "9.000000",
						"0.130000")));
		JsonNode quoteToCancel = data(post(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue() + "/quotes",
				supplierQuoteResourcePayload(fixture.secondSupplierId(), fixture.materialId(), "10.000000",
						"10.000000", "0.130000")));
		JsonNode quoteList = data(get(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue()
						+ "/quotes?status=VALID&page=1&pageSize=20"));
		assertPage(quoteList);
		assertTextualDecimal(firstItem(quoteList), "quantity");
		assertTextualDecimal(firstItem(quoteList), "taxRate");
		assertTextualDecimal(firstItem(quoteList), "taxExcludedUnitPrice");
		assertTextualDecimal(firstItem(quoteList), "taxExcludedAmount");
		assertAllowedActionsPresent(firstItem(quoteList));
		JsonNode updatedQuote = data(put(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue() + "/quotes/"
						+ quoteToSelect.get("id").longValue(),
				supplierQuoteResourceUpdatePayload(fixture.supplierId(), quoteToSelect, "8.500000")));
		assertTextualDecimal(updatedQuote, "taxExcludedUnitPrice");
		JsonNode cancelledQuote = data(put(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue() + "/quotes/"
						+ quoteToCancel.get("id").longValue() + "/cancel",
				versionActionBody(quoteToCancel, "s24-resource-quote-cancel")));
		assertThat(cancelledQuote.get("status").asText()).isEqualTo("CANCELLED");
		JsonNode completedInquiry = data(put(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue() + "/complete",
				actionBody(releasedInquiry, "报价收集完成", "s24-resource-inquiry-complete")));
		assertThat(completedInquiry.get("status").asText()).isEqualTo("COMPLETED");
		JsonNode selectedQuote = data(put(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue() + "/quotes/"
						+ updatedQuote.get("id").longValue() + "/select",
				Map.of("version", updatedQuote.get("version").longValue(), "reason", "最低有效报价",
						"selectedQuantity", "10.000000", "idempotencyKey", "s24-resource-quote-select")));
		assertThat(selectedQuote.get("status").asText()).isEqualTo("SELECTED");
		assertAllowedActionsPresent(selectedQuote);

		JsonNode draftAgreement = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementResourcePayload(fixture, "PROJECT", fixture.projectId(), "8.000000", "0.130000")));
		assertThat(draftAgreement.get("status").asText()).isEqualTo("DRAFT");
		assertAllowedActions(draftAgreement, "UPDATE", "SUBMIT", "CANCEL");
		assertTextualDecimal(draftAgreement, "taxRate");
		assertTextualDecimal(draftAgreement, "taxExcludedUnitPrice");
		JsonNode agreementList = data(get(admin,
				"/api/admin/procurement/price-agreements?procurementMode=PROJECT&status=DRAFT&page=1&pageSize=10"));
		assertPage(agreementList);
		assertAllowedActions(firstItem(agreementList), "UPDATE", "SUBMIT", "CANCEL");
		JsonNode updatedAgreement = data(put(admin,
				"/api/admin/procurement/price-agreements/" + draftAgreement.get("id").longValue(),
				priceAgreementResourceUpdatePayload(fixture, draftAgreement, "7.500000")));
		assertTextualDecimal(updatedAgreement, "taxExcludedUnitPrice");
		JsonNode agreementToCancel = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementResourcePayload(fixture, "PUBLIC", null, "11.000000", "0.130000")));
		JsonNode cancelledAgreement = data(put(admin,
				"/api/admin/procurement/price-agreements/" + agreementToCancel.get("id").longValue() + "/cancel",
				versionActionBody(agreementToCancel, "s24-resource-agreement-cancel")));
		assertThat(cancelledAgreement.get("status").asText()).isEqualTo("CANCELLED");
		JsonNode agreementApproval = data(post(admin,
				"/api/admin/procurement/price-agreements/" + updatedAgreement.get("id").longValue()
						+ "/submit-activation",
				approvalSubmitBody(updatedAgreement, "激活价格协议", "s24-resource-agreement-submit")));
		AuthenticatedSession agreementApprover = createUserAndLogin("stage024-resource-agreement-approver",
				"S24_RESOURCE_AGREEMENT_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "procurement:price-agreement:view",
						"procurement:price-agreement:approve"));
		JsonNode agreementTask = firstTask(agreementApprover);
		data(post(agreementApprover, "/api/admin/approval-tasks/" + taskActionId(agreementTask) + "/approve",
				approvalActionBody(taskActionId(agreementTask), "同意激活",
						"s24-resource-agreement-approve-" + agreementApproval.get("id").longValue())));
		JsonNode activeAgreement = data(get(admin,
				"/api/admin/procurement/price-agreements/" + updatedAgreement.get("id").longValue()));
		assertThat(activeAgreement.get("status").asText()).isEqualTo("ACTIVE");
		assertAllowedActions(activeAgreement, "DISABLE");
		JsonNode disabledAgreement = data(put(admin,
				"/api/admin/procurement/price-agreements/" + activeAgreement.get("id").longValue() + "/disable",
				actionBody(activeAgreement, "协议到期停用", "s24-resource-agreement-disable")));
		assertThat(disabledAgreement.get("status").asText()).isEqualTo("DISABLED");
		assertAllowedActionsPresent(disabledAgreement);
	}

	@Test
	void 项目询价和报价候选行级响应必须保留原项目身份() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_SOURCING_PROJECT_");
		JsonNode requisition = createApprovedRequisition(admin, fixture, "4.000000");
		long requisitionId = requisition.get("id").longValue();
		long requisitionLineId = requisition.get("lines").get(0).get("id").longValue();

		JsonNode inquiry = data(post(admin, "/api/admin/procurement/inquiries",
				inquiryResourcePayload(fixture, requisitionId, requisitionLineId, "4.000000")));
		JsonNode inquiryLine = inquiry.get("lines").get(0);
		assertJsonLong(inquiryLine, "requisitionLineId", requisitionLineId);
		assertSourcingProjectLineFields(inquiryLine, fixture.projectId());
		JsonNode inquiryDetail = data(get(admin, "/api/admin/procurement/inquiries/"
				+ inquiry.get("id").longValue()));
		JsonNode inquiryDetailLine = inquiryDetail.get("lines").get(0);
		assertJsonLong(inquiryDetailLine, "requisitionLineId", requisitionLineId);
		assertSourcingProjectLineFields(inquiryDetailLine, fixture.projectId());

		JsonNode released = data(put(admin, "/api/admin/procurement/inquiries/"
				+ inquiry.get("id").longValue() + "/release",
				actionBody(inquiry, "发布项目询价行级项目身份", "s24-sourcing-project-release-"
						+ inquiry.get("id").longValue())));
		long inquiryLineId = released.get("lines").get(0).get("id").longValue();
		JsonNode quote = data(post(admin, "/api/admin/procurement/inquiries/"
				+ released.get("id").longValue() + "/quotes",
				supplierQuoteResourcePayload(fixture.supplierId(), fixture.materialId(), "4.000000",
						"9.000000", "0.130000")));
		JsonNode quoteLine = quote.get("lines").get(0);
		assertJsonLong(quoteLine, "inquiryLineId", inquiryLineId);
		assertSourcingProjectLineFields(quoteLine, fixture.projectId());
		JsonNode quoteListItem = firstItem(data(get(admin, "/api/admin/procurement/inquiries/"
				+ released.get("id").longValue() + "/quotes?status=VALID&page=1&pageSize=20")));
		JsonNode quoteListLine = quoteListItem.get("lines").get(0);
		assertJsonLong(quoteListLine, "inquiryLineId", inquiryLineId);
		assertSourcingProjectLineFields(quoteListLine, fixture.projectId());
		JsonNode quoteDetail = data(get(admin, "/api/admin/procurement/inquiries/"
				+ released.get("id").longValue() + "/quotes/" + quote.get("id").longValue()));
		JsonNode quoteDetailLine = quoteDetail.get("lines").get(0);
		assertJsonLong(quoteDetailLine, "inquiryLineId", inquiryLineId);
		assertSourcingProjectLineFields(quoteDetailLine, fixture.projectId());
	}

	@Test
	void 供应商报价导入必须验证确认原子性并在下载时重新鉴权() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_QIMPORT_");
		JsonNode approvedRequisition = createApprovedRequisition(admin, fixture, "12.000000");
		long requisitionId = approvedRequisition.get("id").longValue();
		long requisitionLineId = approvedRequisition.get("lines").get(0).get("id").longValue();
		JsonNode inquiry = data(post(admin, "/api/admin/procurement/inquiries",
				inquiryResourcePayload(fixture, requisitionId, requisitionLineId, "12.000000")));
		JsonNode releasedInquiry = data(put(admin,
				"/api/admin/procurement/inquiries/" + inquiry.get("id").longValue() + "/release",
				actionBody(inquiry, "发布报价导入询价", "s24-quote-import-release-" + inquiry.get("id").longValue())));
		long inquiryId = releasedInquiry.get("id").longValue();
		long inquiryLineId = releasedInquiry.get("lines").get(0).get("id").longValue();

		JsonNode task = data(uploadImport(admin, "/api/admin/procurement/inquiries/" + inquiryId + "/quote-imports",
				"quotes.xlsx", supplierQuoteImportWorkbook(fixture.supplierId(), inquiryLineId, "12.000000",
						"7.000000", "7.910000"), "s24-quote-import-" + inquiryId));
		assertThat(task.get("taskType").asText()).isEqualTo("PROCUREMENT_QUOTE_IMPORT");
		assertThat(task.get("stage").asText()).isEqualTo("VALIDATE");
		assertThat(task.get("status").asText()).isEqualTo("QUEUED");

		JsonNode ready = processTaskUntilStatus(admin, task.get("id").longValue(), "READY_TO_COMMIT", 8);
		assertThat(ready.get("status").asText()).as(ready.toString()).isEqualTo("READY_TO_COMMIT");
		assertThat(ready.get("availableActions").toString()).contains("CONFIRM", "CANCEL");
		assertThat(countRows("proc_supplier_quote", "inquiry_id = ?", inquiryId)).isZero();

		AuthenticatedSession downloadDenied = createUserAndLogin("stage024-quote-download-denied",
				"S24_QUOTE_DL_DENIED", List.of("platform:document-task:view", "platform:document-task:view-all",
						"platform:document-task:download", "procurement:inquiry:view"));
		assertError(get(downloadDenied, "/api/admin/document-tasks/" + task.get("id").longValue() + "/download"),
				HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");

		JsonNode commitQueued = data(postWithIdempotencyKey(admin,
				"/api/admin/imports/" + task.get("id").longValue() + "/confirm",
				Map.of("version", ready.get("version").longValue()), "s24-quote-import-confirm-" + inquiryId));
		assertThat(commitQueued.get("stage").asText()).isEqualTo("COMMIT");
		assertThat(commitQueued.get("status").asText()).isEqualTo("QUEUED");
		JsonNode succeeded = processTaskUntilStatus(admin, task.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).as(succeeded.toString()).isEqualTo("SUCCEEDED");
		assertThat(countRows("proc_supplier_quote", "inquiry_id = ?", inquiryId)).isOne();
		JsonNode importedQuotes = data(get(admin, "/api/admin/procurement/inquiries/" + inquiryId
				+ "/quotes?status=VALID&page=1&pageSize=20"));
		assertPage(importedQuotes);
		assertThat(importedQuotes.get("total").longValue()).isOne();
		assertTextualDecimal(firstItem(importedQuotes), "quantity");
		assertTextualDecimal(firstItem(importedQuotes), "taxExcludedUnitPrice");
		assertAllowedActionsPresent(firstItem(importedQuotes));

		JsonNode invalidTask = data(uploadImport(admin,
				"/api/admin/procurement/inquiries/" + inquiryId + "/quote-imports", "quotes-invalid.xlsx",
				supplierQuoteImportWorkbook(fixture.secondSupplierId(), inquiryLineId, "0.000000", "8.000000",
						"9.040000"),
				"s24-quote-import-invalid-" + inquiryId));
		JsonNode failed = processTaskUntilStatus(admin, invalidTask.get("id").longValue(), "VALIDATION_FAILED", 8);
		assertThat(failed.get("status").asText()).as(failed.toString()).isEqualTo("VALIDATION_FAILED");
		assertThat(failed.get("availableActions").toString()).contains("ERRORS");
		assertThat(failed.get("failedRows").intValue()).isPositive();
		assertThat(countRows("proc_supplier_quote", "inquiry_id = ?", inquiryId)).isOne();
	}

	@Test
	void 到货计划和有效供给必须覆盖分页筛选单计划更新和桌面动作契约() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_SCHEDULE_");
		JsonNode projectOrder = createConfirmedProjectOrder(admin, fixture, "30.000000", "20.000000");
		long orderId = projectOrder.get("id").longValue();
		long orderLineId = projectOrder.get("lines").get(0).get("id").longValue();

		JsonNode orderDetail = data(get(admin, "/api/admin/procurement/orders/" + orderId));
		assertAllowedActions(orderDetail, "CREATE_RECEIPT", "UPDATE_SCHEDULES", "CLOSE", "PRINT");
		assertTextualDecimal(orderDetail, "totalQuantity");
		assertTextualDecimal(orderDetail, "remainingQuantity");
		assertTextualDecimal(orderDetail.get("lines").get(0), "quantity");
		assertTextualDecimal(orderDetail.get("lines").get(0), "unitPrice");
		assertTextualDecimal(orderDetail.get("lines").get(0), "taxRate");
		assertTextualDecimal(orderDetail.get("lines").get(0), "taxExcludedAmount");

		JsonNode replacedSchedules = data(put(admin, "/api/admin/procurement/orders/" + orderId + "/schedules",
				Map.of("version", orderDetail.get("version").longValue(), "idempotencyKey",
						"s24-schedule-resource-replace-" + orderId, "lines", List.of(
								scheduleLine(orderLineId, 1, "10.000000", LocalDate.now().plusDays(4)),
								scheduleLine(orderLineId, 2, "20.000000", LocalDate.now().plusDays(9))))));
		assertPage(replacedSchedules);
		assertThat(replacedSchedules.get("items")).hasSize(2);
		assertAllowedActions(firstItem(replacedSchedules), "CLOSE");
		assertTextualDecimal(firstItem(replacedSchedules), "plannedQuantity");
		assertTextualDecimal(firstItem(replacedSchedules), "remainingQuantity");

		JsonNode scheduleList = data(get(admin,
				"/api/admin/procurement/orders/" + orderId + "/schedules?status=PLANNED&page=1&pageSize=20"));
		assertPage(scheduleList);
		for (int index = 0; index < scheduleList.get("items").size(); index++) {
			JsonNode schedule = scheduleList.get("items").get(index);
			assertThat(schedule.get("status").asText()).isEqualTo("PLANNED");
			assertAllowedActions(schedule, "CLOSE");
			assertTextualDecimal(schedule, "plannedQuantity");
			assertTextualDecimal(schedule, "remainingQuantity");
		}

		JsonNode schedule = firstItem(scheduleList);
		JsonNode updatedSchedule = data(put(admin,
				"/api/admin/procurement/orders/" + orderId + "/schedules/" + schedule.get("id").longValue(),
				scheduleUpdatePayload(schedule, "12.000000")));
		assertThat(updatedSchedule.get("status").asText()).isEqualTo("PLANNED");
		assertAllowedActions(updatedSchedule, "CLOSE");
		assertTextualDecimal(updatedSchedule, "plannedQuantity");

		JsonNode projectSupply = data(get(admin,
				"/api/admin/procurement/effective-supplies?materialId=" + fixture.materialId()
						+ "&procurementMode=PROJECT&countedOnly=true&status=CONFIRMED&page=1&pageSize=20"));
		assertPage(projectSupply);
		assertThat(projectSupply.get("total").longValue()).isGreaterThan(0);
		for (int index = 0; index < projectSupply.get("items").size(); index++) {
			JsonNode supply = projectSupply.get("items").get(index);
			assertThat(supply.get("procurementMode").asText()).isEqualTo("PROJECT");
			assertThat(supply.get("countedAsEffectiveSupply").booleanValue()).isTrue();
			assertThat(supply.get("status").asText()).isEqualTo("CONFIRMED");
			assertAllowedActionsPresent(supply);
			assertTextualDecimal(supply, "remainingQuantity");
		}
		JsonNode publicSupply = data(get(admin,
				"/api/admin/procurement/effective-supplies?materialId=" + fixture.materialId()
						+ "&procurementMode=PUBLIC&countedOnly=true&page=1&pageSize=20"));
		assertPage(publicSupply);
		assertThat(publicSupply.get("total").longValue()).isZero();
		JsonNode closedBeforeClose = data(get(admin,
				"/api/admin/procurement/effective-supplies?materialId=" + fixture.materialId()
						+ "&procurementMode=PROJECT&countedOnly=true&status=CLOSED&page=1&pageSize=20"));
		assertPage(closedBeforeClose);
		assertThat(closedBeforeClose.get("total").longValue()).isZero();

		JsonNode closedSchedule = data(put(admin,
				"/api/admin/procurement/orders/" + orderId + "/schedules/" + updatedSchedule.get("id").longValue()
						+ "/close",
				actionBody(updatedSchedule, "计划调整关闭", "s24-schedule-resource-close")));
		assertThat(closedSchedule.get("status").asText()).isEqualTo("CLOSED");
		JsonNode closedUncounted = data(get(admin,
				"/api/admin/procurement/effective-supplies?materialId=" + fixture.materialId()
						+ "&procurementMode=PROJECT&countedOnly=false&status=CLOSED&page=1&pageSize=20"));
		assertPage(closedUncounted);
		assertThat(closedUncounted.get("total").longValue()).isGreaterThan(0);
		for (int index = 0; index < closedUncounted.get("items").size(); index++) {
			JsonNode supply = closedUncounted.get("items").get(index);
			assertThat(supply.get("countedAsEffectiveSupply").booleanValue()).isFalse();
			assertThat(supply.get("status").asText()).isEqualTo("CLOSED");
		}
		JsonNode closedCounted = data(get(admin,
				"/api/admin/procurement/effective-supplies?materialId=" + fixture.materialId()
						+ "&procurementMode=PROJECT&countedOnly=true&status=CLOSED&page=1&pageSize=20"));
		assertPage(closedCounted);
		assertThat(closedCounted.get("total").longValue()).isZero();
	}

	@Test
	void 同订单同物料不同批准来源可确认且重复来源组合必须拒绝() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_SOURCE_");
		JsonNode firstRequisition = createApprovedRequisition(admin, fixture, "2.000000");
		JsonNode secondRequisition = createApprovedRequisition(admin, fixture, "3.000000");
		long firstLineId = firstRequisition.get("lines").get(0).get("id").longValue();
		long secondLineId = secondRequisition.get("lines").get(0).get("id").longValue();

		Map<String, Object> orderPayload = orderHeader(fixture.supplierId(), "PROJECT", fixture.projectId(),
				"024 同物料不同来源允许");
		orderPayload.put("priceSourceType", "REQUISITION_APPROVED");
		orderPayload.put("lines", List.of(
				orderLine(fixture, 1, firstLineId, null, null, null, "2.000000", "10.000000"),
				orderLine(fixture, 2, secondLineId, null, null, null, "3.000000", "11.000000")));
		JsonNode order = data(post(admin, "/api/admin/procurement/orders", orderPayload));
		assertThat(order.get("lines")).hasSize(2);
		JsonNode confirmed = data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()
				+ "/confirm", actionBody(order, "不同批准来源同物料确认", "s24-source-order-confirm-"
						+ order.get("id").longValue())));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

		Map<String, Object> duplicatePayload = orderHeader(fixture.supplierId(), "PROJECT", fixture.projectId(),
				"024 真正重复来源拒绝");
		duplicatePayload.put("priceSourceType", "REQUISITION_APPROVED");
		duplicatePayload.put("lines", List.of(
				orderLine(fixture, 1, firstLineId, null, null, null, "1.000000", "10.000000"),
				orderLine(fixture, 2, firstLineId, null, null, null, "1.000000", "10.000000")));
		assertError(post(admin, "/api/admin/procurement/orders", duplicatePayload), HttpStatus.CONFLICT,
				"PROCUREMENT_ORDER_DUPLICATE_LINE");
	}

	@Test
	void 适用协议未引用或偏离价格必须走订单例外审批() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_AGREEMENT_");
		JsonNode activeAgreement = activatePriceAgreement(admin, fixture, "PROJECT", fixture.projectId(),
				"8.000000", LocalDate.now(), LocalDate.now().plusDays(20));
		long agreementLineId = activeAgreement.get("lines").get(0).get("id").longValue();

		JsonNode requisitionWithoutAgreement = createApprovedRequisition(admin, fixture, "2.000000");
		Map<String, Object> missingAgreementPayload = orderHeader(fixture.supplierId(), "PROJECT",
				fixture.projectId(), "024 有适用协议但未引用");
		missingAgreementPayload.put("priceSourceType", "REQUISITION_APPROVED");
		missingAgreementPayload.put("lines", List.of(orderLine(fixture, 1,
				requisitionWithoutAgreement.get("lines").get(0).get("id").longValue(), null, null, null,
				"2.000000", "8.000000")));
		JsonNode missingAgreementOrder = data(post(admin, "/api/admin/procurement/orders",
				missingAgreementPayload));
		assertErrorIn(put(admin, "/api/admin/procurement/orders/" + missingAgreementOrder.get("id").longValue()
				+ "/confirm", actionBody(missingAgreementOrder, "适用协议未引用不得直确",
						"s24-missing-agreement-confirm")), HttpStatus.CONFLICT,
				List.of("PROCUREMENT_ORDER_AGREEMENT_DEVIATION", "PROCUREMENT_ORDER_EXCEPTION_APPROVAL_REQUIRED"));

		JsonNode requisitionWithAgreement = createApprovedRequisition(admin, fixture, "2.000000");
		Map<String, Object> deviatedPayload = orderHeader(fixture.supplierId(), "PROJECT", fixture.projectId(),
				"024 协议价格偏离");
		deviatedPayload.put("priceSourceType", "AGREEMENT");
		deviatedPayload.put("lines", List.of(orderLine(fixture, 1,
				requisitionWithAgreement.get("lines").get(0).get("id").longValue(), null, null, agreementLineId,
				"2.000000", "9.000000")));
		JsonNode deviatedOrder = data(post(admin, "/api/admin/procurement/orders", deviatedPayload));
		assertErrorIn(put(admin, "/api/admin/procurement/orders/" + deviatedOrder.get("id").longValue()
				+ "/confirm", actionBody(deviatedOrder, "协议偏离不得直确", "s24-deviated-agreement-confirm")),
				HttpStatus.CONFLICT,
				List.of("PROCUREMENT_ORDER_AGREEMENT_DEVIATION", "PROCUREMENT_ORDER_EXCEPTION_APPROVAL_REQUIRED"));
	}

	@Test
	void 价格协议激活必须拒绝重叠且允许非重叠() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_OVERLAP_");
		LocalDate today = LocalDate.now();
		JsonNode activeAgreement = activatePriceAgreement(admin, fixture, "PROJECT", fixture.projectId(),
				"8.000000", today, today.plusDays(10));

		JsonNode overlapping = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementPayload(fixture, "PROJECT", fixture.projectId(), "8.500000", "0.130000",
						today.plusDays(5), today.plusDays(15))));
		JsonNode overlappingApproval = data(post(admin, "/api/admin/procurement/price-agreements/"
				+ overlapping.get("id").longValue() + "/submit-activation",
				approvalSubmitBody(overlapping, "重叠协议不得激活", "s24-overlap-submit-"
						+ overlapping.get("id").longValue())));
		AuthenticatedSession overlapApprover = createUserAndLogin("stage024-overlap-approver",
				"S24_FIX_OVERLAP_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "procurement:price-agreement:view",
						"procurement:price-agreement:approve"));
		JsonNode overlapTask = firstTask(overlapApprover);
		assertError(post(overlapApprover, "/api/admin/approval-tasks/" + taskActionId(overlapTask) + "/approve",
				approvalActionBody(taskActionId(overlapTask), "拒绝重叠激活",
						"s24-overlap-approve-" + overlappingApproval.get("id").longValue())),
				HttpStatus.CONFLICT, "PROCUREMENT_PRICE_AGREEMENT_OVERLAP");

		JsonNode later = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementPayload(fixture, "PROJECT", fixture.projectId(), "8.800000", "0.130000",
						today.plusDays(11), today.plusDays(30))));
		JsonNode laterApproval = data(post(admin, "/api/admin/procurement/price-agreements/"
				+ later.get("id").longValue() + "/submit-activation",
				approvalSubmitBody(later, "非重叠协议可激活", "s24-later-submit-" + later.get("id").longValue())));
		AuthenticatedSession laterApprover = createUserAndLogin("stage024-later-approver",
				"S24_FIX_LATER_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "procurement:price-agreement:view",
						"procurement:price-agreement:approve"));
		JsonNode laterTask = firstTask(laterApprover);
		data(post(laterApprover, "/api/admin/approval-tasks/" + taskActionId(laterTask) + "/approve",
				approvalActionBody(taskActionId(laterTask), "同意非重叠激活",
						"s24-later-approve-" + laterApproval.get("id").longValue())));
		JsonNode activatedLater = data(get(admin, "/api/admin/procurement/price-agreements/"
				+ later.get("id").longValue()));
		assertThat(activatedLater.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(activeAgreement.get("status").asText()).isEqualTo("ACTIVE");
	}

	@Test
	void 审批提交同键重试必须返回同一实例且陈旧版本不同键冲突() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_APPROVAL_");

		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload(fixture, "PROJECT", fixture.projectId(), "4.000000")));
		assertApprovalSubmitRetry(admin, "/api/admin/procurement/requisitions/" + requisition.get("id").longValue()
				+ "/submit-approval", "/api/admin/procurement/requisitions/" + requisition.get("id").longValue(),
				requisition, "请购审批幂等", "s24-approval-requisition-" + requisition.get("id").longValue());

		JsonNode agreement = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementPayload(fixture, "PROJECT", fixture.projectId(), "8.000000", "0.130000")));
		assertApprovalSubmitRetry(admin, "/api/admin/procurement/price-agreements/" + agreement.get("id").longValue()
				+ "/submit-activation", "/api/admin/procurement/price-agreements/" + agreement.get("id").longValue(),
				agreement, "协议激活幂等", "s24-approval-agreement-" + agreement.get("id").longValue());

		JsonNode publicDirectOrder = data(post(admin, "/api/admin/procurement/orders",
				publicOrderPayload(fixture, "PUBLIC_DIRECT", "8.000000", "024 公共直采例外幂等",
						null, "设备抢修公共直采")));
		assertApprovalSubmitRetry(admin, "/api/admin/procurement/orders/" + publicDirectOrder.get("id").longValue()
				+ "/submit-exception", "/api/admin/procurement/orders/" + publicDirectOrder.get("id").longValue(),
				publicDirectOrder, "订单例外幂等", "s24-approval-order-" + publicDirectOrder.get("id").longValue());
	}

	@Test
	void 代表性状态动作同键幂等且报价选择必须推进报价版本() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_ACTION_");

		JsonNode cancellableRequisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionResourcePayload(fixture, "PROJECT", fixture.projectId(), "1.000000")));
		Map<String, Object> requisitionCancel = actionBody(cancellableRequisition, "取消请购",
				"s24-action-req-cancel-" + cancellableRequisition.get("id").longValue());
		JsonNode cancelledRequisition = data(put(admin, "/api/admin/procurement/requisitions/"
				+ cancellableRequisition.get("id").longValue() + "/cancel", requisitionCancel));
		JsonNode requisitionRetry = data(put(admin, "/api/admin/procurement/requisitions/"
				+ cancellableRequisition.get("id").longValue() + "/cancel", requisitionCancel));
		assertThat(requisitionRetry.get("id").longValue()).isEqualTo(cancelledRequisition.get("id").longValue());
		assertThat(requisitionRetry.get("status").asText()).isEqualTo("CANCELLED");

		JsonNode approvedRequisition = createApprovedRequisition(admin, fixture, "6.000000");
		long requisitionId = approvedRequisition.get("id").longValue();
		long requisitionLineId = approvedRequisition.get("lines").get(0).get("id").longValue();
		JsonNode inquiry = data(post(admin, "/api/admin/procurement/inquiries",
				inquiryResourcePayload(fixture, requisitionId, requisitionLineId, "6.000000")));
		Map<String, Object> releaseBody = actionBody(inquiry, "发布询价",
				"s24-action-inquiry-release-" + inquiry.get("id").longValue());
		JsonNode released = data(put(admin, "/api/admin/procurement/inquiries/" + inquiry.get("id").longValue()
				+ "/release", releaseBody));
		JsonNode releaseRetry = data(put(admin, "/api/admin/procurement/inquiries/" + inquiry.get("id").longValue()
				+ "/release", releaseBody));
		assertThat(releaseRetry.get("id").longValue()).isEqualTo(released.get("id").longValue());
		assertThat(releaseRetry.get("status").asText()).isEqualTo("RELEASED");

		JsonNode quote = data(post(admin, "/api/admin/procurement/inquiries/" + released.get("id").longValue()
				+ "/quotes", supplierQuoteResourcePayload(fixture.supplierId(), fixture.materialId(),
						"6.000000", "9.000000", "0.130000")));
		JsonNode quoteToCancel = data(post(admin, "/api/admin/procurement/inquiries/" + released.get("id")
			.longValue() + "/quotes", supplierQuoteResourcePayload(fixture.secondSupplierId(), fixture.materialId(),
					"6.000000", "10.000000", "0.130000")));
		Map<String, Object> quoteCancel = actionBody(quoteToCancel, "取消报价",
				"s24-action-quote-cancel-" + quoteToCancel.get("id").longValue());
		JsonNode cancelledQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ released.get("id").longValue() + "/quotes/" + quoteToCancel.get("id").longValue()
				+ "/cancel", quoteCancel));
		JsonNode quoteCancelRetry = data(put(admin, "/api/admin/procurement/inquiries/"
				+ released.get("id").longValue() + "/quotes/" + quoteToCancel.get("id").longValue()
				+ "/cancel", quoteCancel));
		assertThat(quoteCancelRetry.get("id").longValue()).isEqualTo(cancelledQuote.get("id").longValue());
		assertThat(quoteCancelRetry.get("status").asText()).isEqualTo("CANCELLED");

		JsonNode completed = data(put(admin, "/api/admin/procurement/inquiries/" + released.get("id").longValue()
				+ "/complete", actionBody(released, "报价完成", "s24-action-inquiry-complete-"
						+ released.get("id").longValue())));
		JsonNode selected = data(put(admin, "/api/admin/procurement/inquiries/" + completed.get("id").longValue()
				+ "/quotes/" + quote.get("id").longValue() + "/select",
				actionBody(quote, "选择报价", "s24-action-quote-select-" + quote.get("id").longValue())));
		assertThat(selected.get("status").asText()).isEqualTo("SELECTED");
		assertThat(selected.get("version").longValue()).isGreaterThan(quote.get("version").longValue());

		JsonNode agreement = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementPayload(fixture, "PROJECT", fixture.projectId(), "8.000000", "0.130000")));
		Map<String, Object> agreementCancel = actionBody(agreement, "取消协议",
				"s24-action-agreement-cancel-" + agreement.get("id").longValue());
		JsonNode cancelledAgreement = data(put(admin, "/api/admin/procurement/price-agreements/"
				+ agreement.get("id").longValue() + "/cancel", agreementCancel));
		JsonNode agreementRetry = data(put(admin, "/api/admin/procurement/price-agreements/"
				+ agreement.get("id").longValue() + "/cancel", agreementCancel));
		assertThat(agreementRetry.get("id").longValue()).isEqualTo(cancelledAgreement.get("id").longValue());
		assertThat(agreementRetry.get("status").asText()).isEqualTo("CANCELLED");
	}

	@Test
	void 入库响应十进制动作集权限变化和审批例外状态字段必须满足桌面硬契约() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_RECEIPT_");
		JsonNode order = createConfirmedProjectOrder(admin, fixture, "5.000000", "12.000000");
		JsonNode receipt = data(post(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()
				+ "/receipts", receiptPayload(fixture, order.get("lines").get(0).get("id").longValue(), null,
						"2.000000", LocalDate.now())));
		assertTextualDecimal(receipt, "totalQuantity");
		assertTextualDecimal(receipt.get("lines").get(0), "quantity");
		assertTextualDecimal(receipt.get("lines").get(0), "taxExcludedAmount");
		assertAllowedActions(receipt, "UPDATE", "POST");

		AuthenticatedSession receiptViewer = createUserAndLogin("stage024-receipt-viewer",
				"S24_FIX_RECEIPT_VIEWER", List.of("procurement:receipt:view"));
		JsonNode restrictedReceipt = data(get(receiptViewer, "/api/admin/procurement/receipts/"
				+ receipt.get("id").longValue()));
		assertAllowedActionsAbsent(restrictedReceipt, "UPDATE", "POST");

		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload(fixture, "PROJECT", fixture.projectId(), "3.000000")));
		data(post(admin, "/api/admin/procurement/requisitions/" + requisition.get("id").longValue()
				+ "/submit-approval", approvalSubmitBody(requisition, "审批字段区分业务状态",
						"s24-status-requisition-" + requisition.get("id").longValue())));
		JsonNode submittedRequisition = data(get(admin, "/api/admin/procurement/requisitions/"
				+ requisition.get("id").longValue()));
		assertThat(submittedRequisition.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(submittedRequisition.has("approvalStatus")).as(submittedRequisition.toString()).isTrue();
		assertThat(submittedRequisition.get("approvalStatus").asText()).isEqualTo("SUBMITTED");

		JsonNode publicDirectOrder = data(post(admin, "/api/admin/procurement/orders",
				publicOrderPayload(fixture, "PUBLIC_DIRECT", "8.000000", "024 例外状态字段",
						null, "设备抢修公共直采")));
		data(post(admin, "/api/admin/procurement/orders/" + publicDirectOrder.get("id").longValue()
				+ "/submit-exception", approvalSubmitBody(publicDirectOrder, "例外字段区分业务状态",
						"s24-status-order-" + publicDirectOrder.get("id").longValue())));
		JsonNode orderWithException = data(get(admin, "/api/admin/procurement/orders/"
				+ publicDirectOrder.get("id").longValue()));
		assertThat(orderWithException.get("status").asText()).isEqualTo("DRAFT");
		assertThat(orderWithException.has("exceptionApprovalStatus")).as(orderWithException.toString()).isTrue();
		assertThat(orderWithException.get("exceptionApprovalStatus").asText()).isEqualTo("SUBMITTED");
	}

	@Test
	void 到货计划导出必须限定指定订单与筛选范围() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_EXPORT_");
		JsonNode firstOrder = createConfirmedProjectOrder(admin, fixture, "4.000000", "10.000000");
		JsonNode secondOrder = createConfirmedProjectOrder(admin, fixture, "4.000000", "10.000000");

		JsonNode exportTask = data(postWithIdempotencyKey(admin, "/api/admin/export-tasks",
				Map.of("taskType", "PROCUREMENT_SCHEDULE_EXPORT", "objectType", "PROCUREMENT_ORDER",
						"objectId", firstOrder.get("id").longValue(), "filters", Map.of("status", "PLANNED")),
				"s24-schedule-export-scope-" + firstOrder.get("id").longValue()));
		JsonNode succeeded = processTaskUntilStatus(admin, exportTask.get("id").longValue(), "SUCCEEDED", 8);
		assertThat(succeeded.get("status").asText()).as(succeeded.toString()).isEqualTo("SUCCEEDED");
		ResponseEntity<byte[]> downloaded = downloadBytes(admin,
				"/api/admin/document-tasks/" + exportTask.get("id").longValue() + "/download");
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		String exportedText = workbookText(downloaded.getBody());
		assertThat(exportedText).contains(firstOrder.get("orderNo").asText());
		assertThat(exportedText).doesNotContain(secondOrder.get("orderNo").asText());
	}

	@Test
	void 采购订单列表详情和编辑回读必须保留项目展示与行级来源字段() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_ORDER_FIELDS_");

		JsonNode quoteRequisition = createApprovedRequisition(admin, fixture, "2.000000");
		long quoteRequisitionId = quoteRequisition.get("id").longValue();
		long quoteRequisitionLineId = quoteRequisition.get("lines").get(0).get("id").longValue();
		JsonNode inquiry = data(post(admin, "/api/admin/procurement/inquiries",
				inquiryResourcePayload(fixture, quoteRequisitionId, quoteRequisitionLineId, "2.000000")));
		JsonNode releasedInquiry = data(put(admin,
				"/api/admin/procurement/inquiries/" + inquiry.get("id").longValue() + "/release",
				actionBody(inquiry, "发布项目询价", "s24-order-fields-inquiry-release-"
						+ inquiry.get("id").longValue())));
		JsonNode quote = data(post(admin, "/api/admin/procurement/inquiries/"
				+ releasedInquiry.get("id").longValue() + "/quotes",
				supplierQuoteResourcePayload(fixture.supplierId(), fixture.materialId(), "2.000000",
						"9.000000", "0.130000")));
		JsonNode completedInquiry = data(put(admin,
				"/api/admin/procurement/inquiries/" + releasedInquiry.get("id").longValue() + "/complete",
				actionBody(releasedInquiry, "报价完成", "s24-order-fields-inquiry-complete-"
						+ releasedInquiry.get("id").longValue())));
		JsonNode selectedQuote = data(put(admin, "/api/admin/procurement/inquiries/"
				+ completedInquiry.get("id").longValue() + "/quotes/" + quote.get("id").longValue()
				+ "/select", actionBody(quote, "选择报价", "s24-order-fields-quote-select-"
						+ quote.get("id").longValue())));
		long quoteLineId = selectedQuote.has("selectedQuoteLineId")
				? selectedQuote.get("selectedQuoteLineId").longValue()
				: selectedQuote.get("lines").get(0).get("id").longValue();

		JsonNode agreement = activatePriceAgreement(admin, fixture, "PROJECT", fixture.projectId(),
				"8.000000", LocalDate.now(), LocalDate.now().plusDays(20));
		long agreementLineId = agreement.get("lines").get(0).get("id").longValue();
		JsonNode agreementRequisition = createApprovedRequisition(admin, fixture, "3.000000");
		long agreementRequisitionLineId = agreementRequisition.get("lines").get(0).get("id").longValue();

		Map<String, Object> quoteLine = orderLine(fixture, 1, quoteRequisitionLineId,
				selectedQuote.get("id").longValue(), quoteLineId, null, "2.000000", "9.000000");
		quoteLine.put("priceSourceType", "QUOTE_SELECTION");
		Map<String, Object> agreementLine = orderLine(fixture, 2, agreementRequisitionLineId, null, null,
				agreementLineId, "3.000000", "8.000000");
		agreementLine.put("priceSourceType", "AGREEMENT");
		Map<String, Object> orderPayload = orderHeader(fixture.supplierId(), "PROJECT", fixture.projectId(),
				"024 订单字段契约");
		orderPayload.put("lines", List.of(quoteLine, agreementLine));
		JsonNode order = data(post(admin, "/api/admin/procurement/orders", orderPayload));
		assertProjectOwnershipFields(order, fixture.projectId());
		assertOrderLineSourceFields(lineByNo(order.get("lines"), 1), quoteRequisitionLineId, quoteLineId, null);
		assertOrderLineSourceFields(lineByNo(order.get("lines"), 2), agreementRequisitionLineId, null,
				agreementLineId);

		JsonNode listed = firstItem(data(get(admin, "/api/admin/procurement/orders?keyword="
				+ order.get("orderNo").asText() + "&page=1&pageSize=10")));
		assertProjectOwnershipFields(listed, fixture.projectId());

		JsonNode detail = data(get(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()));
		assertProjectOwnershipFields(detail, fixture.projectId());
		assertOrderLineSourceFields(lineByNo(detail.get("lines"), 1), quoteRequisitionLineId, quoteLineId, null);
		assertOrderLineSourceFields(lineByNo(detail.get("lines"), 2), agreementRequisitionLineId, null,
				agreementLineId);

		Map<String, Object> updatePayload = orderHeader(fixture.supplierId(), "PROJECT", fixture.projectId(),
				"024 订单字段编辑回读");
		updatePayload.put("version", detail.get("version").longValue());
		updatePayload.put("lines", List.of(quoteLine, agreementLine));
		JsonNode updated = data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue(),
				updatePayload));
		JsonNode reread = data(get(admin, "/api/admin/procurement/orders/" + updated.get("id").longValue()));
		assertProjectOwnershipFields(reread, fixture.projectId());
		assertOrderLineSourceFields(lineByNo(reread.get("lines"), 1), quoteRequisitionLineId, quoteLineId, null);
		assertOrderLineSourceFields(lineByNo(reread.get("lines"), 2), agreementRequisitionLineId, null,
				agreementLineId);
	}

	@Test
	void 采购入库列表详情和明细成本字段必须按成本权限展示或脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PostedProjectReceipt posted = createPostedProjectReceipt(admin, "S24_FIX_RECEIPT_FIELDS_",
				"5.000000", "12.000000");

		JsonNode adminListItem = firstItem(data(get(admin, "/api/admin/procurement/receipts?keyword="
				+ posted.receipt().get("receiptNo").asText() + "&page=1&pageSize=10")));
		assertReceiptProjectFields(adminListItem, posted.fixture().projectId(), true);
		assertTextualDecimal(adminListItem, "taxExcludedAmount");
		JsonNode adminDetail = data(get(admin, "/api/admin/procurement/receipts/"
				+ posted.receipt().get("id").longValue()));
		assertReceiptProjectFields(adminDetail, posted.fixture().projectId(), true);
		assertTextualDecimal(adminDetail, "taxExcludedAmount");
		JsonNode adminLine = adminDetail.get("lines").get(0);
		assertReceiptProjectFields(adminLine, posted.fixture().projectId(), true);
		assertTextualDecimal(adminLine, "taxExcludedAmount");
		assertNonBlankText(adminLine, "costLayerNo");
		assertThat(adminLine.get("valueMovementNo").asText()).isEqualTo(receiptLineValueMovementNo(
				posted.receiptLineId()));

		AuthenticatedSession noCost = createUserAndLogin("stage024-receipt-fields-no-cost",
				"S24_RECEIPT_FIELDS_NO_COST", List.of("procurement:receipt:view"));
		JsonNode restrictedListItem = firstItem(data(get(noCost, "/api/admin/procurement/receipts?keyword="
				+ posted.receipt().get("receiptNo").asText() + "&page=1&pageSize=10")));
		assertReceiptProjectFields(restrictedListItem, posted.fixture().projectId(), false);
		assertCostFieldsHidden(restrictedListItem, "taxExcludedAmount", "unitCost", "costAmount", "costLayerId",
				"costLayerNo", "valueMovementId", "valueMovementNo");
		JsonNode restrictedDetail = data(get(noCost, "/api/admin/procurement/receipts/"
				+ posted.receipt().get("id").longValue()));
		assertReceiptProjectFields(restrictedDetail, posted.fixture().projectId(), false);
		assertCostFieldsHidden(restrictedDetail, "taxExcludedAmount", "unitCost", "costAmount", "costLayerId",
				"costLayerNo", "valueMovementId", "valueMovementNo");
		JsonNode restrictedLine = restrictedDetail.get("lines").get(0);
		assertReceiptProjectFields(restrictedLine, posted.fixture().projectId(), false);
		assertCostFieldsHidden(restrictedLine, "taxExcludedAmount", "unitCost", "costAmount", "costLayerId",
				"costLayerNo", "valueMovementId", "valueMovementNo");
	}

	@Test
	void 无成本权限采购入库详情追溯流水必须隐藏内部编号和金额成本字段() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PostedProjectReceipt posted = createPostedProjectReceipt(admin, "S24_FIX_RECEIPT_TRACE_MASK_",
				"5.000000", "12.000000");

		JsonNode adminDetail = data(get(admin, "/api/admin/procurement/receipts/"
				+ posted.receipt().get("id").longValue()));
		assertReceiptProjectFields(adminDetail, posted.fixture().projectId(), true);
		assertReceiptProjectFields(adminDetail.get("lines").get(0), posted.fixture().projectId(), true);
		assertNonBlankText(adminDetail.get("lines").get(0), "valueMovementNo");
		assertTraceCollectionVisible(adminDetail, "inventoryMovements");

		AuthenticatedSession noCost = createUserAndLogin("stage024-receipt-trace-mask-no-cost",
				"S24_RECEIPT_TRACE_MASK_NO_COST", List.of("procurement:receipt:view"));
		JsonNode restrictedDetail = data(get(noCost, "/api/admin/procurement/receipts/"
				+ posted.receipt().get("id").longValue()));
		assertReceiptProjectFields(restrictedDetail, posted.fixture().projectId(), false);
		assertCostFieldsHidden(restrictedDetail, "taxExcludedAmount", "unitCost", "costAmount", "costLayerId",
				"costLayerNo", "valueMovementId", "valueMovementNo");
		JsonNode restrictedLine = restrictedDetail.get("lines").get(0);
		assertReceiptProjectFields(restrictedLine, posted.fixture().projectId(), false);
		assertCostFieldsHidden(restrictedLine, "taxExcludedAmount", "unitCost", "costAmount", "costLayerId",
				"costLayerNo", "valueMovementId", "valueMovementNo");
		assertTraceCollectionHiddenOrSanitized(restrictedDetail, "inventoryMovements");
		assertTraceCollectionHiddenOrSanitized(restrictedDetail, "valueMovements");
		assertTraceCollectionHiddenOrSanitized(restrictedDetail, "valueMovementTraces");
	}

	@Test
	void 采购退货列表详情候选和明细必须保留原项目成本追溯并按权限脱敏() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PostedProjectReceipt posted = createPostedProjectReceipt(admin, "S24_FIX_RETURN_FIELDS_",
				"6.000000", "10.000000");
		String originalValueMovementNo = receiptLineValueMovementNo(posted.receiptLineId());

		JsonNode source = firstItem(data(get(admin, "/api/admin/procurement/return-sources?keyword="
				+ posted.receipt().get("receiptNo").asText() + "&page=1&pageSize=10")));
		assertReturnCostSourceFields(source, posted.fixture().projectId(), true, originalValueMovementNo);
		assertReturnCostSourceFields(source.get("lines").get(0), posted.fixture().projectId(), true,
				originalValueMovementNo);

		JsonNode purchaseReturn = data(post(admin, "/api/admin/procurement/returns",
				purchaseReturnPayload(posted.receipt().get("id").longValue(), posted.receiptLineId(),
						"2.000000", "s24-return-fields-" + posted.receiptLineId())));
		JsonNode postedReturn = data(put(admin, "/api/admin/procurement/returns/"
				+ purchaseReturn.get("id").longValue() + "/post",
				actionBody(purchaseReturn, "项目专采退货追溯字段", "s24-return-fields-post-"
						+ purchaseReturn.get("id").longValue())));
		JsonNode returnListItem = firstItem(data(get(admin, "/api/admin/procurement/returns?keyword="
				+ postedReturn.get("returnNo").asText() + "&page=1&pageSize=10")));
		assertReturnCostSourceFields(returnListItem, posted.fixture().projectId(), true, originalValueMovementNo);
		JsonNode returnDetail = data(get(admin, "/api/admin/procurement/returns/"
				+ postedReturn.get("id").longValue()));
		assertReturnCostSourceFields(returnDetail, posted.fixture().projectId(), true, originalValueMovementNo);
		JsonNode returnLine = returnDetail.get("lines").get(0);
		assertReturnCostSourceFields(returnLine, posted.fixture().projectId(), true, originalValueMovementNo);
		assertReturnCostSourceFields(returnLine.get("source"), posted.fixture().projectId(), true,
				originalValueMovementNo);

		AuthenticatedSession noCost = createUserAndLogin("stage024-return-fields-no-cost",
				"S24_RETURN_FIELDS_NO_COST", List.of("procurement:return:view", "procurement:return:create",
						"procurement:receipt:view"));
		JsonNode restrictedSource = firstItem(data(get(noCost, "/api/admin/procurement/return-sources?keyword="
				+ posted.receipt().get("receiptNo").asText() + "&page=1&pageSize=10")));
		assertReturnCostSourceFields(restrictedSource, posted.fixture().projectId(), false, null);
		assertReturnCostSourceFields(restrictedSource.get("lines").get(0), posted.fixture().projectId(), false,
				null);
		JsonNode restrictedListItem = firstItem(data(get(noCost, "/api/admin/procurement/returns?keyword="
				+ postedReturn.get("returnNo").asText() + "&page=1&pageSize=10")));
		assertReturnCostSourceFields(restrictedListItem, posted.fixture().projectId(), false, null);
		JsonNode restrictedDetail = data(get(noCost, "/api/admin/procurement/returns/"
				+ postedReturn.get("id").longValue()));
		assertReturnCostSourceFields(restrictedDetail, posted.fixture().projectId(), false, null);
		JsonNode restrictedLine = restrictedDetail.get("lines").get(0);
		assertReturnCostSourceFields(restrictedLine, posted.fixture().projectId(), false, null);
		assertReturnCostSourceFields(restrictedLine.get("source"), posted.fixture().projectId(), false, null);
	}

	@Test
	void 项目转为非ACTIVE后项目专采订单创建和更新必须重新拒绝() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = createFixture("S24_FIX_PROJECT_STATUS_");
		JsonNode createRequisition = createApprovedRequisition(admin, fixture, "2.000000");
		JsonNode updateRequisition = createApprovedRequisition(admin, fixture, "3.000000");
		long createRequisitionLineId = createRequisition.get("lines").get(0).get("id").longValue();
		long updateRequisitionLineId = updateRequisition.get("lines").get(0).get("id").longValue();
		JsonNode draftOrder = data(post(admin, "/api/admin/procurement/orders",
				projectOrderFromApprovedRequisitionPayload(fixture, updateRequisitionLineId, "3.000000",
						"10.000000")));

		setProjectStatus(fixture.projectId(), "CLOSED");
		assertError(post(admin, "/api/admin/procurement/orders",
				projectOrderFromApprovedRequisitionPayload(fixture, createRequisitionLineId, "2.000000",
						"10.000000")), HttpStatus.CONFLICT, "PROCUREMENT_PROJECT_STATUS_INVALID");

		JsonNode rereadDraft = data(get(admin, "/api/admin/procurement/orders/" + draftOrder.get("id").longValue()));
		Map<String, Object> updatePayload = projectOrderFromApprovedRequisitionPayload(fixture,
				updateRequisitionLineId, "3.000000", "11.000000");
		updatePayload.put("version", rereadDraft.get("version").longValue());
		updatePayload.put("remark", "024 非ACTIVE项目订单更新拒绝");
		assertError(put(admin, "/api/admin/procurement/orders/" + draftOrder.get("id").longValue(), updatePayload),
				HttpStatus.CONFLICT, "PROCUREMENT_PROJECT_STATUS_INVALID");
	}

	@Test
	void 无成本权限采购退货候选列表详情明细来源和追溯必须隐藏金额成本层和值流水() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		PostedProjectReceipt posted = createPostedProjectReceipt(admin, "S24_FIX_RETURN_MASK_",
				"6.000000", "10.000000");
		String originalValueMovementNo = receiptLineValueMovementNo(posted.receiptLineId());

		JsonNode adminSource = firstItem(data(get(admin, "/api/admin/procurement/return-sources?keyword="
				+ posted.receipt().get("receiptNo").asText() + "&page=1&pageSize=10")));
		assertReturnCostSourceFields(adminSource, posted.fixture().projectId(), true, originalValueMovementNo);
		assertReturnSensitiveCostsVisible(adminSource.get("lines").get(0), "unitPrice", "returnableAmount",
				"originalCostLayerNo", "originalValueMovementNo");

		JsonNode purchaseReturn = data(post(admin, "/api/admin/procurement/returns",
				purchaseReturnPayload(posted.receipt().get("id").longValue(), posted.receiptLineId(),
						"2.000000", "s24-return-mask-" + posted.receiptLineId())));
		JsonNode postedReturn = data(put(admin, "/api/admin/procurement/returns/"
				+ purchaseReturn.get("id").longValue() + "/post",
				actionBody(purchaseReturn, "项目专采退货成本脱敏", "s24-return-mask-post-"
						+ purchaseReturn.get("id").longValue())));
		assertReturnSensitiveCostsVisible(postedReturn, "totalAmount", "originalCostLayerNo",
				"originalValueMovementNo");
		assertReturnSensitiveCostsVisible(postedReturn.get("lines").get(0), "unitPrice", "amount",
				"originalCostLayerNo", "originalValueMovementNo");
		assertReturnSensitiveCostsVisible(postedReturn.get("lines").get(0).get("source"), "amount",
				"originalCostLayerNo", "originalValueMovementNo");
		JsonNode adminTrace = data(get(admin, "/api/admin/reversal-traces?sourceType=PURCHASE_RETURN&sourceId="
				+ postedReturn.get("id").longValue())).get(0);
		assertReturnSensitiveCostsVisible(adminTrace, "amount");

		AuthenticatedSession noCost = createUserAndLogin("stage024-return-mask-no-cost",
				"S24_RETURN_MASK_NO_COST", List.of("procurement:return:view", "procurement:return:create",
						"procurement:receipt:view", "business:reversal:view"));
		JsonNode restrictedSource = firstItem(data(get(noCost, "/api/admin/procurement/return-sources?keyword="
				+ posted.receipt().get("receiptNo").asText() + "&page=1&pageSize=10")));
		assertReturnProjectStateAndQuantityReadable(restrictedSource, posted.fixture().projectId(), "status");
		assertReturnSensitiveCostsHidden(restrictedSource, "unitPrice", "returnableAmount", "totalAmount", "amount",
				"costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo", "originalCostLayerId",
				"originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo", "sourceCostLayerId",
				"sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
		JsonNode restrictedSourceLine = restrictedSource.get("lines").get(0);
		assertReturnProjectStateAndQuantityReadable(restrictedSourceLine, posted.fixture().projectId(),
				"returnableQuantity");
		assertReturnSensitiveCostsHidden(restrictedSourceLine, "unitPrice", "returnableAmount", "totalAmount",
				"amount", "costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo",
				"originalCostLayerId", "originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo",
				"sourceCostLayerId", "sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");

		JsonNode restrictedListItem = firstItem(data(get(noCost, "/api/admin/procurement/returns?keyword="
				+ postedReturn.get("returnNo").asText() + "&page=1&pageSize=10")));
		assertReturnProjectStateAndQuantityReadable(restrictedListItem, posted.fixture().projectId(), "status");
		assertReturnSensitiveCostsHidden(restrictedListItem, "unitPrice", "returnableAmount", "totalAmount",
				"amount", "costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo",
				"originalCostLayerId", "originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo",
				"sourceCostLayerId", "sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
		JsonNode restrictedDetail = data(get(noCost, "/api/admin/procurement/returns/"
				+ postedReturn.get("id").longValue()));
		assertReturnProjectStateAndQuantityReadable(restrictedDetail, posted.fixture().projectId(), "status");
		assertReturnSensitiveCostsHidden(restrictedDetail, "unitPrice", "returnableAmount", "totalAmount", "amount",
				"costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo", "originalCostLayerId",
				"originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo", "sourceCostLayerId",
				"sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
		JsonNode restrictedLine = restrictedDetail.get("lines").get(0);
		assertReturnProjectStateAndQuantityReadable(restrictedLine, posted.fixture().projectId(), "quantity");
		assertReturnSensitiveCostsHidden(restrictedLine, "unitPrice", "returnableAmount", "totalAmount", "amount",
				"costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo", "originalCostLayerId",
				"originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo", "sourceCostLayerId",
				"sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
		assertReturnProjectStateAndQuantityReadable(restrictedLine.get("source"), posted.fixture().projectId(),
				"quantity");
		assertReturnSensitiveCostsHidden(restrictedLine.get("source"), "unitPrice", "returnableAmount",
				"totalAmount", "amount", "costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo",
				"originalCostLayerId", "originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo",
				"sourceCostLayerId", "sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
		JsonNode restrictedTrace = data(get(noCost, "/api/admin/reversal-traces?sourceType=PURCHASE_RETURN&sourceId="
				+ postedReturn.get("id").longValue())).get(0);
		assertReturnSensitiveCostsHidden(restrictedTrace, "unitPrice", "returnableAmount", "totalAmount", "amount",
				"costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo", "originalCostLayerId",
				"originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo", "sourceCostLayerId",
				"sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
		assertReturnSensitiveCostsHidden(restrictedTrace.get("source"), "unitPrice", "returnableAmount",
				"totalAmount", "amount", "costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo",
				"originalCostLayerId", "originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo",
				"sourceCostLayerId", "sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
		assertReturnSensitiveCostsHidden(restrictedTrace.get("reverse"), "unitPrice", "returnableAmount",
				"totalAmount", "amount", "costLayerId", "costLayerNo", "valueMovementId", "valueMovementNo",
				"originalCostLayerId", "originalCostLayerNo", "originalValueMovementId", "originalValueMovementNo",
				"sourceCostLayerId", "sourceCostLayerNo", "sourceValueMovementId", "sourceValueMovementNo");
	}

	private JsonNode createConfirmedProjectOrder(AuthenticatedSession admin, ProcurementFixture fixture,
			String quantity, String taxExcludedUnitPrice) throws Exception {
		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionPayload(fixture, "PROJECT", fixture.projectId(), quantity)));
		long requisitionId = requisition.get("id").longValue();
		long requisitionLineId = requisition.get("lines").get(0).get("id").longValue();
		JsonNode submitted = data(post(admin,
				"/api/admin/procurement/requisitions/" + requisitionId + "/submit-approval",
				approvalSubmitBody(requisition, "项目请购审批", "s24-helper-req-submit-" + requisitionId)));
		AuthenticatedSession approver = createUserAndLogin("stage024-helper-approver", "S24_HELPER_APPROVER",
				List.of("platform:approval:view", "platform:todo:view", "platform:message:view",
						"procurement:requisition:view", "procurement:requisition:approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意", "s24-helper-approve-" + submitted.get("id").longValue())));
		JsonNode order = data(post(admin, "/api/admin/procurement/orders",
				projectOrderFromApprovedRequisitionPayload(fixture, requisitionLineId, quantity,
						taxExcludedUnitPrice)));
		return data(put(admin, "/api/admin/procurement/orders/" + order.get("id").longValue() + "/confirm",
				actionBody(order, "严格承接已批准请购确认", "s24-helper-order-confirm-" + order.get("id").longValue())));
	}

	private PostedProjectReceipt createPostedProjectReceipt(AuthenticatedSession admin, String prefix,
			String quantity, String taxExcludedUnitPrice) throws Exception {
		ProcurementFixture fixture = createFixture(prefix);
		JsonNode order = createConfirmedProjectOrder(admin, fixture, quantity, taxExcludedUnitPrice);
		JsonNode receipt = data(post(admin, "/api/admin/procurement/orders/" + order.get("id").longValue()
				+ "/receipts", receiptPayload(fixture, order.get("lines").get(0).get("id").longValue(), null,
						quantity, LocalDate.now())));
		JsonNode postedReceipt = data(put(admin, "/api/admin/procurement/receipts/"
				+ receipt.get("id").longValue() + "/post", actionBody(receipt, "项目入库字段契约",
						"s24-helper-posted-receipt-" + receipt.get("id").longValue())));
		return new PostedProjectReceipt(fixture, order, postedReceipt,
				postedReceipt.get("lines").get(0).get("id").longValue());
	}

	private JsonNode createApprovedRequisition(AuthenticatedSession admin, ProcurementFixture fixture, String quantity)
			throws Exception {
		JsonNode requisition = data(post(admin, "/api/admin/procurement/requisitions",
				requisitionResourcePayload(fixture, "PROJECT", fixture.projectId(), quantity)));
		long requisitionId = requisition.get("id").longValue();
		JsonNode submitted = data(post(admin,
				"/api/admin/procurement/requisitions/" + requisitionId + "/submit-approval",
				approvalSubmitBody(requisition, "项目请购审批", "s24-resource-req-submit-" + requisitionId)));
		AuthenticatedSession approver = createUserAndLogin("stage024-resource-requisition-approver",
				"S24_RESOURCE_REQ_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "procurement:requisition:view", "procurement:requisition:approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意请购",
						"s24-resource-req-approve-" + submitted.get("id").longValue())));
		return data(get(admin, "/api/admin/procurement/requisitions/" + requisitionId));
	}

	private JsonNode activatePriceAgreement(AuthenticatedSession admin, ProcurementFixture fixture,
			String procurementMode, Long projectId, String taxExcludedUnitPrice, LocalDate validFrom, LocalDate validTo)
			throws Exception {
		JsonNode agreement = data(post(admin, "/api/admin/procurement/price-agreements",
				priceAgreementPayload(fixture, procurementMode, projectId, taxExcludedUnitPrice, "0.130000",
						validFrom, validTo)));
		JsonNode approval = data(post(admin, "/api/admin/procurement/price-agreements/"
				+ agreement.get("id").longValue() + "/submit-activation",
				approvalSubmitBody(agreement, "激活价格协议", "s24-helper-agreement-submit-"
						+ agreement.get("id").longValue())));
		AuthenticatedSession approver = createUserAndLogin("stage024-helper-agreement-approver",
				"S24_HELPER_AGREEMENT_APPROVER", List.of("platform:approval:view", "platform:todo:view",
						"platform:message:view", "procurement:price-agreement:view",
						"procurement:price-agreement:approve"));
		JsonNode task = firstTask(approver);
		data(post(approver, "/api/admin/approval-tasks/" + taskActionId(task) + "/approve",
				approvalActionBody(taskActionId(task), "同意激活协议",
						"s24-helper-agreement-approve-" + approval.get("id").longValue())));
		return data(get(admin, "/api/admin/procurement/price-agreements/" + agreement.get("id").longValue()));
	}

	private ProcurementFixture createFixture(String prefix) {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit(prefix + "UNIT_" + suffix);
		long warehouseId = insertWarehouse(prefix + "WH_" + suffix);
		long supplierId = insertSupplier(prefix + "SUP_A_" + suffix);
		long secondSupplierId = insertSupplier(prefix + "SUP_B_" + suffix);
		long categoryId = insertMaterialCategory(prefix + "CAT_" + suffix);
		long materialId = insertMaterial(prefix + "MAT_" + suffix, categoryId, unitId, true);
		long projectId = insertProject(prefix + "PRJ_ACTIVE_" + suffix, "ACTIVE");
		long inactiveProjectId = insertProject(prefix + "PRJ_DRAFT_" + suffix, "DRAFT");
		return new ProcurementFixture(unitId, warehouseId, supplierId, secondSupplierId, categoryId, materialId,
				projectId, inactiveProjectId);
	}

	private Map<String, Object> requisitionResourcePayload(ProcurementFixture fixture, String procurementMode,
			Long projectId, String quantity) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("procurementMode", procurementMode);
		payload.put("projectId", projectId);
		payload.put("title", "024 桌面资源请购");
		payload.put("requiredDate", LocalDate.now().plusDays(10).toString());
		payload.put("remark", "024 桌面资源契约");
		payload.put("lines", List.of(requisitionResourceLine(fixture, procurementMode, projectId, quantity)));
		return payload;
	}

	private Map<String, Object> requisitionResourceUpdatePayload(ProcurementFixture fixture, JsonNode requisition,
			String quantity) {
		Map<String, Object> payload = requisitionResourcePayload(fixture, "PROJECT", fixture.projectId(), quantity);
		payload.put("version", requisition.get("version").longValue());
		payload.put("title", "024 桌面资源请购调整");
		return payload;
	}

	private Map<String, Object> requisitionResourceLine(ProcurementFixture fixture, String procurementMode,
			Long projectId, String quantity) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("procurementMode", procurementMode);
		line.put("projectId", projectId);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("quantity", quantity);
		line.put("requiredDate", LocalDate.now().plusDays(10).toString());
		line.put("purpose", "024 桌面资源物料");
		line.put("suggestedSupplierId", fixture.supplierId());
		line.put("taxRate", "0.130000");
		return line;
	}

	private Map<String, Object> inquiryResourcePayload(ProcurementFixture fixture, long requisitionId,
			long requisitionLineId, String quantity) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("procurementMode", "PROJECT");
		payload.put("projectId", fixture.projectId());
		payload.put("title", "024 桌面资源询价");
		payload.put("supplierIds", List.of(fixture.supplierId(), fixture.secondSupplierId()));
		payload.put("remark", "024 桌面资源询价");
		payload.put("lines", List.of(inquiryResourceLine(fixture, requisitionId, requisitionLineId, quantity)));
		return payload;
	}

	private Map<String, Object> inquiryResourceUpdatePayload(ProcurementFixture fixture, JsonNode inquiry,
			long requisitionId, long requisitionLineId, String quantity) {
		Map<String, Object> payload = inquiryResourcePayload(fixture, requisitionId, requisitionLineId, quantity);
		payload.put("version", inquiry.get("version").longValue());
		payload.put("title", "024 桌面资源询价调整");
		return payload;
	}

	private Map<String, Object> inquiryResourceLine(ProcurementFixture fixture, long requisitionId,
			long requisitionLineId, String quantity) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("requisitionId", requisitionId);
		line.put("requisitionLineId", requisitionLineId);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("quantity", quantity);
		line.put("requiredDate", LocalDate.now().plusDays(10).toString());
		line.put("remark", "024 桌面资源询价行");
		return line;
	}

	private Map<String, Object> supplierQuoteResourcePayload(long supplierId, long materialId, String quantity,
			String taxExcludedUnitPrice, String taxRate) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("materialId", materialId);
		payload.put("quantity", quantity);
		payload.put("taxRate", taxRate);
		payload.put("taxIncludedUnitPrice", taxIncluded(taxExcludedUnitPrice, taxRate));
		payload.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		payload.put("taxIncludedAmount", amount(quantity, taxIncluded(taxExcludedUnitPrice, taxRate)));
		payload.put("taxExcludedAmount", amount(quantity, taxExcludedUnitPrice));
		payload.put("currency", "CNY");
		payload.put("minPurchaseQuantity", "1.000000");
		payload.put("deliveryDate", LocalDate.now().plusDays(8).toString());
		payload.put("validFrom", LocalDate.now().toString());
		payload.put("validTo", LocalDate.now().plusDays(30).toString());
		payload.put("remark", "024 桌面资源报价");
		return payload;
	}

	private Map<String, Object> supplierQuoteResourceUpdatePayload(long supplierId, JsonNode quote,
			String taxExcludedUnitPrice) {
		Map<String, Object> payload = supplierQuoteResourcePayload(supplierId, quote.get("materialId").longValue(),
				quote.get("quantity").asText(), taxExcludedUnitPrice, quote.get("taxRate").asText());
		payload.put("version", quote.get("version").longValue());
		payload.put("remark", "024 桌面资源报价调整");
		return payload;
	}

	private byte[] supplierQuoteImportWorkbook(long supplierId, long inquiryLineId, String quantity,
			String taxExcludedUnitPrice, String taxIncludedUnitPrice) throws Exception {
		return supplierQuoteImportWorkbookRows(List.<String[]>of(new String[] { Long.toString(supplierId),
				LocalDate.now().toString(), LocalDate.now().plusDays(30).toString(), Long.toString(inquiryLineId),
				quantity, "0.130000", taxExcludedUnitPrice, taxIncludedUnitPrice,
				LocalDate.now().plusDays(8).toString(), "024 报价导入" }));
	}

	private byte[] supplierQuoteImportWorkbookRows(List<String[]> rows) throws Exception {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("quotes");
			Row header = sheet.createRow(0);
			String[] headers = { "supplierId", "validFrom", "validTo", "inquiryLineId", "quantity", "taxRate",
					"taxExcludedUnitPrice", "taxIncludedUnitPrice", "deliveryDate", "remark" };
			for (int index = 0; index < headers.length; index++) {
				header.createCell(index).setCellValue(headers[index]);
			}
			for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
				Row row = sheet.createRow(rowIndex + 1);
				String[] values = rows.get(rowIndex);
				for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
					row.createCell(cellIndex).setCellValue(values[cellIndex]);
				}
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private Map<String, Object> priceAgreementResourcePayload(ProcurementFixture fixture, String procurementMode,
			Long projectId, String taxExcludedUnitPrice, String taxRate) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("procurementMode", procurementMode);
		payload.put("projectId", projectId);
		payload.put("supplierId", fixture.supplierId());
		payload.put("materialId", fixture.materialId());
		payload.put("taxRate", taxRate);
		payload.put("taxIncludedUnitPrice", taxIncluded(taxExcludedUnitPrice, taxRate));
		payload.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		payload.put("currency", "CNY");
		payload.put("minPurchaseQuantity", "1.000000");
		payload.put("validFrom", LocalDate.now().toString());
		payload.put("validTo", LocalDate.now().plusMonths(2).toString());
		payload.put("remark", "024 桌面资源价格协议");
		return payload;
	}

	private Map<String, Object> priceAgreementResourceUpdatePayload(ProcurementFixture fixture, JsonNode agreement,
			String taxExcludedUnitPrice) {
		Map<String, Object> payload = priceAgreementResourcePayload(fixture,
				agreement.get("procurementMode").asText(), agreement.get("projectId").isNull() ? null
						: agreement.get("projectId").longValue(), taxExcludedUnitPrice,
				agreement.get("taxRate").asText());
		payload.put("version", agreement.get("version").longValue());
		payload.put("remark", "024 桌面资源价格协议调整");
		return payload;
	}

	private Map<String, Object> requisitionPayload(ProcurementFixture fixture, String procurementMode, Long projectId,
			String quantity) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("procurementMode", procurementMode);
		payload.put("ownershipType", procurementMode);
		payload.put("projectId", projectId);
		payload.put("requiredDate", LocalDate.now().plusDays(10).toString());
		payload.put("purpose", "024 项目专采请购");
		payload.put("suggestedSupplierId", fixture.supplierId());
		payload.put("idempotencyKey", "s24-req-" + SEQUENCE.incrementAndGet());
		payload.put("lines", List.of(requisitionLine(fixture, procurementMode, projectId, quantity)));
		return payload;
	}

	private Map<String, Object> requisitionLine(ProcurementFixture fixture, String procurementMode, Long projectId,
			String quantity) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("procurementMode", procurementMode);
		line.put("ownershipType", procurementMode);
		line.put("projectId", projectId);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("requestedQuantity", quantity);
		line.put("requiredDate", LocalDate.now().plusDays(10).toString());
		line.put("purpose", "024 项目专采物料");
		line.put("suggestedSupplierId", fixture.supplierId());
		return line;
	}

	private Map<String, Object> inquiryPayload(ProcurementFixture fixture, long requisitionId, long requisitionLineId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("title", "024 项目专采询价");
		payload.put("procurementMode", "PROJECT");
		payload.put("ownershipType", "PROJECT");
		payload.put("projectId", fixture.projectId());
		payload.put("quotationDeadline", LocalDate.now().plusDays(3).toString());
		payload.put("compareBasis", "TAX_EXCLUDED");
		payload.put("currency", "CNY");
		payload.put("idempotencyKey", "s24-inquiry-" + SEQUENCE.incrementAndGet());
		payload.put("lines", List.of(Map.of("lineNo", 1, "requisitionId", requisitionId,
				"requisitionLineId", requisitionLineId, "materialId", fixture.materialId(), "unitId",
				fixture.unitId(), "quantity", "10.000000", "requiredDate", LocalDate.now().plusDays(10).toString())));
		return payload;
	}

	private Map<String, Object> quotePayload(long supplierId, long inquiryLineId, String taxExcludedUnitPrice,
			String taxRate) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("currency", "CNY");
		payload.put("validFrom", LocalDate.now().toString());
		payload.put("validTo", LocalDate.now().plusDays(30).toString());
		payload.put("idempotencyKey", "s24-quote-" + SEQUENCE.incrementAndGet());
		payload.put("lines", List.of(Map.of("lineNo", 1, "inquiryLineId", inquiryLineId,
				"quantity", "10.000000", "taxRate", taxRate, "taxExcludedUnitPrice", taxExcludedUnitPrice,
				"taxIncludedUnitPrice", taxIncluded(taxExcludedUnitPrice, taxRate), "deliveryDate",
				LocalDate.now().plusDays(8).toString(), "currency", "CNY")));
		return payload;
	}

	private Map<String, Object> priceAgreementPayload(ProcurementFixture fixture, String procurementMode, Long projectId,
			String taxExcludedUnitPrice, String taxRate) {
		return priceAgreementPayload(fixture, procurementMode, projectId, taxExcludedUnitPrice, taxRate,
				LocalDate.now(), LocalDate.now().plusMonths(2));
	}

	private Map<String, Object> priceAgreementPayload(ProcurementFixture fixture, String procurementMode, Long projectId,
			String taxExcludedUnitPrice, String taxRate, LocalDate validFrom, LocalDate validTo) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", fixture.supplierId());
		payload.put("procurementMode", procurementMode);
		payload.put("ownershipType", procurementMode);
		payload.put("projectId", projectId);
		payload.put("currency", "CNY");
		payload.put("validFrom", validFrom.toString());
		payload.put("validTo", validTo.toString());
		payload.put("idempotencyKey", "s24-agreement-" + SEQUENCE.incrementAndGet());
		payload.put("lines", List.of(Map.of("lineNo", 1, "materialId", fixture.materialId(),
				"unitId", fixture.unitId(), "minimumQuantity", "1.000000", "taxRate", taxRate,
				"taxExcludedUnitPrice", taxExcludedUnitPrice, "taxIncludedUnitPrice",
				taxIncluded(taxExcludedUnitPrice, taxRate), "currency", "CNY")));
		return payload;
	}

	private Map<String, Object> projectOrderWithoutRequisitionPayload(ProcurementFixture fixture) {
		return projectOrderBasePayload(fixture, null, null, null, "5.000000", "10.000000");
	}

	private Map<String, Object> projectOrderFromSelectionPayload(ProcurementFixture fixture, long requisitionLineId,
			JsonNode selection) {
		long quoteLineId = selection.has("selectedQuoteLineId") ? selection.get("selectedQuoteLineId").longValue()
				: selection.get("lines").get(0).get("id").longValue();
		return projectOrderBasePayload(fixture, requisitionLineId, selection.get("id").longValue(),
				quoteLineId, "10.000000", "11.000000");
	}

	private Map<String, Object> projectOrderFromApprovedRequisitionPayload(ProcurementFixture fixture,
			long requisitionLineId, String quantity, String taxExcludedUnitPrice) {
		return projectOrderBasePayload(fixture, requisitionLineId, null, null, quantity, taxExcludedUnitPrice);
	}

	private Map<String, Object> projectOrderBasePayload(ProcurementFixture fixture, Long requisitionLineId,
			Long priceSelectionId, Long quoteLineId, String quantity, String taxExcludedUnitPrice) {
		Map<String, Object> payload = orderHeader(fixture.supplierId(), "PROJECT", fixture.projectId(),
				"024 项目专采采购订单");
		payload.put("priceSourceType", priceSelectionId == null ? "REQUISITION_APPROVED" : "QUOTE_SELECTION");
		payload.put("lines", List.of(orderLine(fixture, requisitionLineId, priceSelectionId, quoteLineId, null,
				quantity, taxExcludedUnitPrice)));
		return payload;
	}

	private Map<String, Object> publicOrderPayload(ProcurementFixture fixture, String priceSourceType,
			String taxExcludedUnitPrice, String remark, Long agreementLineId, String directPurchaseReason) {
		Map<String, Object> payload = orderHeader(fixture.supplierId(), "PUBLIC", null, remark);
		payload.put("priceSourceType", priceSourceType);
		payload.put("directPurchaseReason", directPurchaseReason);
		payload.put("lines", List.of(orderLine(fixture, null, null, null, agreementLineId, "5.000000",
				taxExcludedUnitPrice)));
		return payload;
	}

	private Map<String, Object> orderHeader(long supplierId, String procurementMode, Long projectId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("orderDate", LocalDate.now().toString());
		payload.put("expectedArrivalDate", LocalDate.now().plusDays(7).toString());
		payload.put("procurementMode", procurementMode);
		payload.put("ownershipType", procurementMode);
		payload.put("projectId", projectId);
		payload.put("currency", "CNY");
		payload.put("remark", remark);
		payload.put("idempotencyKey", "s24-order-" + SEQUENCE.incrementAndGet());
		return payload;
	}

	private Map<String, Object> orderLine(ProcurementFixture fixture, Long requisitionLineId, Long priceSelectionId,
			Long quoteLineId, Long agreementLineId, String quantity, String taxExcludedUnitPrice) {
		return orderLine(fixture, 1, requisitionLineId, priceSelectionId, quoteLineId, agreementLineId, quantity,
				taxExcludedUnitPrice);
	}

	private Map<String, Object> orderLine(ProcurementFixture fixture, int lineNo, Long requisitionLineId,
			Long priceSelectionId, Long quoteLineId, Long agreementLineId, String quantity,
			String taxExcludedUnitPrice) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", lineNo);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("quantity", quantity);
		line.put("taxRate", "0.130000");
		line.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		line.put("taxIncludedUnitPrice", taxIncluded(taxExcludedUnitPrice, "0.130000"));
		line.put("requisitionLineId", requisitionLineId);
		line.put("priceSelectionId", priceSelectionId);
		line.put("quoteLineId", quoteLineId);
		line.put("priceAgreementLineId", agreementLineId);
		line.put("expectedArrivalDate", LocalDate.now().plusDays(7).toString());
		line.put("schedules", List.of(Map.of("scheduleNo", 1, "plannedDate", LocalDate.now().plusDays(7).toString(),
				"plannedQuantity", quantity)));
		return line;
	}

	private Map<String, Object> orderProjectMutationPayload(JsonNode order, long projectId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", order.get("supplierId").longValue());
		payload.put("orderDate", order.get("orderDate").asText());
		payload.put("expectedArrivalDate", order.get("expectedArrivalDate").asText());
		payload.put("procurementMode", "PROJECT");
		payload.put("ownershipType", "PROJECT");
		payload.put("projectId", projectId);
		payload.put("version", order.get("version").longValue());
		payload.put("lines", List.of());
		return payload;
	}

	private Map<String, Object> scheduleLine(long orderLineId, int scheduleNo, String quantity, LocalDate plannedDate) {
		return Map.of("orderLineId", orderLineId, "scheduleNo", scheduleNo, "plannedDate", plannedDate.toString(),
				"plannedQuantity", quantity);
	}

	private Map<String, Object> scheduleUpdatePayload(JsonNode schedule, String plannedQuantity) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("scheduleSeq", schedule.get("scheduleSeq").intValue());
		payload.put("expectedArrivalDate", schedule.get("expectedArrivalDate").asText());
		payload.put("plannedQuantity", plannedQuantity);
		payload.put("remark", "024 单计划更新");
		payload.put("version", schedule.get("version").longValue());
		return payload;
	}

	private Map<String, Object> receiptPayload(ProcurementFixture fixture, long orderLineId, Long scheduleId,
			String quantity, LocalDate businessDate) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("warehouseId", fixture.warehouseId());
		payload.put("businessDate", businessDate.toString());
		payload.put("remark", "024 采购入库");
		payload.put("idempotencyKey", "s24-receipt-" + SEQUENCE.incrementAndGet());
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("lineNo", 1);
		line.put("orderLineId", orderLineId);
		line.put("scheduleId", scheduleId);
		line.put("materialId", fixture.materialId());
		line.put("unitId", fixture.unitId());
		line.put("quantity", quantity);
		line.put("remark", "024 入库行");
		payload.put("lines", List.of(line));
		return payload;
	}

	private Map<String, Object> purchaseReturnPayload(long receiptId, long receiptLineId, String quantity,
			String clientRequestId) {
		return Map.of("sourceReceiptId", receiptId, "businessDate", LocalDate.now().toString(),
				"clientRequestId", clientRequestId, "remark", "024 项目专采退货",
				"lines", List.of(Map.of("lineNo", 1, "sourceReceiptLineId", receiptLineId,
						"quantity", quantity, "reason", "按原入库来源退回")));
	}

	private Map<String, Object> approvalSubmitBody(JsonNode businessObject, String reason, String idempotencyKey) {
		return Map.of("version", businessObject.get("version").longValue(), "reason", reason,
				"idempotencyKey", idempotencyKey);
	}

	private void assertApprovalSubmitRetry(AuthenticatedSession admin, String submitPath, String getPath,
			JsonNode businessObject, String reason, String idempotencyKey) throws Exception {
		Map<String, Object> submitBody = approvalSubmitBody(businessObject, reason, idempotencyKey);
		JsonNode firstApproval = data(post(admin, submitPath, submitBody));
		JsonNode afterSubmit = data(get(admin, getPath));
		assertThat(afterSubmit.get("version").longValue()).as(afterSubmit.toString())
			.isGreaterThan(businessObject.get("version").longValue());
		JsonNode retryApproval = data(post(admin, submitPath, submitBody));
		assertThat(retryApproval.get("id").longValue()).isEqualTo(firstApproval.get("id").longValue());
		assertError(post(admin, submitPath, approvalSubmitBody(businessObject, reason,
				idempotencyKey + "-stale-other")), HttpStatus.CONFLICT, "VERSION_CONFLICT");
	}

	private Map<String, Object> approvalActionBody(long taskId, String comment, String idempotencyKey) {
		return Map.of("version", taskVersion(taskId), "comment", comment, "idempotencyKey", idempotencyKey);
	}

	private Map<String, Object> actionBody(JsonNode businessObject, String reason, String idempotencyKey) {
		Map<String, Object> body = new LinkedHashMap<>();
		if (businessObject.has("version")) {
			body.put("version", businessObject.get("version").longValue());
		}
		body.put("reason", reason);
		body.put("idempotencyKey", idempotencyKey);
		return body;
	}

	private Map<String, Object> versionActionBody(JsonNode businessObject, String idempotencyKey) {
		return Map.of("version", businessObject.get("version").longValue(), "idempotencyKey", idempotencyKey);
	}

	private String taxIncluded(String taxExcluded, String taxRate) {
		return new BigDecimal(taxExcluded).multiply(BigDecimal.ONE.add(new BigDecimal(taxRate)))
			.setScale(6, java.math.RoundingMode.HALF_UP)
			.toPlainString();
	}

	private String amount(String quantity, String unitPrice) {
		return new BigDecimal(quantity).multiply(new BigDecimal(unitPrice))
			.setScale(6, java.math.RoundingMode.HALF_UP)
			.toPlainString();
	}

	private long insertUnit(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at, updated_by,
					updated_at)
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

	private long insertSupplier(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_supplier (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "供应商");
	}

	private long insertMaterialCategory(String code) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "分类");
	}

	private long insertMaterial(String code, long categoryId, long unitId, boolean valued) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (
					code, name, specification, material_type, source_type, category_id, unit_id, status,
					cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, '024规格', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED',
					'DIRECT_MATERIAL', ?, ?, ?, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "物料", categoryId, unitId,
				valued ? "VALUATED_MATERIAL" : "NON_VALUATED_CONSUMABLE", valued, valued);
	}

	private long insertProject(String code, String status) {
		long customerId = this.jdbcTemplate.queryForObject("""
				insert into mst_customer (code, name, status, created_by, created_at, updated_by, updated_at)
				values (?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code + "_CUS", code + "客户");
		long ownerUserId = userId("admin");
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, current_date, current_date + interval '30 day', ?, 100000.00, 60000.00,
					'test', now(), 'test', now())
				returning id
				""", Long.class, code, code + "项目", customerId, ownerUserId, status);
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
				""", Long.class, rolePrefix + suffix, "024 测试角色" + suffix, "024 测试角色");
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

	private long pendingTaskId(long approvalId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from platform_approval_task
				where instance_id = ?
				  and status = 'PENDING'
				order by id
				limit 1
				""", Long.class, approvalId);
	}

	private long taskVersion(long taskId) {
		return this.jdbcTemplate.queryForObject("select version from platform_approval_task where id = ?",
				Long.class, taskId);
	}

	private JsonNode processTaskUntilStatus(AuthenticatedSession session, long taskId, String status, int maxAttempts)
			throws Exception {
		JsonNode task = data(get(session, "/api/admin/document-tasks/" + taskId));
		for (int index = 0; index < maxAttempts && !status.equals(task.get("status").asText()); index++) {
			if (!this.documentTaskWorker.processAvailableOnce()) {
				Thread.sleep(1100);
			}
			task = data(get(session, "/api/admin/document-tasks/" + taskId));
		}
		return task;
	}

	private ResponseEntity<byte[]> downloadBytes(AuthenticatedSession session, String path) {
		return this.restTemplate.exchange(path, HttpMethod.GET,
				entity(null, session.sessionCookie(), session.csrfSession()), byte[].class);
	}

	private String workbookText(byte[] content) throws Exception {
		assertThat(content).isNotNull();
		DataFormatter formatter = new DataFormatter();
		StringBuilder builder = new StringBuilder();
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
				Sheet sheet = workbook.getSheetAt(sheetIndex);
				for (Row row : sheet) {
					for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
						builder.append(formatter.formatCellValue(row.getCell(cellIndex))).append('\t');
					}
					builder.append('\n');
				}
			}
		}
		return builder.toString();
	}

	private long userId(String username) {
		return this.jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("""
				insert into biz_business_period (
					period_code, period_name, start_date, end_date, status, created_at, updated_at
				)
				values (?, ?, ?, ?, 'LOCKED', now(), now())
				""", "S24L" + SEQUENCE.incrementAndGet(), "024 锁定期间",
				date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
	}

	private void setProjectStatus(long projectId, String status) {
		assertThat(this.jdbcTemplate.update("""
				update sal_project
				set status = ?, updated_at = now(), version = version + 1
				where id = ?
				""", status, projectId)).isOne();
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

	private long countRows(String tableName, String whereSql, Object... args) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereSql,
				Long.class, args);
	}

	private String status(String tableName, long id) {
		return this.jdbcTemplate.queryForObject("select status from " + tableName + " where id = ?",
				String.class, id);
	}

	private String projectCode(long projectId) {
		return this.jdbcTemplate.queryForObject("select project_no from sal_project where id = ?",
				String.class, projectId);
	}

	private String projectName(long projectId) {
		return this.jdbcTemplate.queryForObject("select name from sal_project where id = ?",
				String.class, projectId);
	}

	private String receiptLineValueMovementNo(long receiptLineId) {
		return this.jdbcTemplate.queryForObject("""
				select vm.movement_no
				from proc_purchase_receipt_line prl
				join inv_value_movement vm on vm.id = prl.value_movement_id
				where prl.id = ?
				""", String.class, receiptLineId);
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

	private ResponseEntity<String> uploadImport(AuthenticatedSession session, String path, String filename,
			byte[] content, String idempotencyKey) {
		org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(content) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, session.sessionCookie());
		headers.add(session.csrfSession().headerName(), session.csrfSession().token());
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
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

	private void assertErrorIn(ResponseEntity<String> response, HttpStatus status, List<String> codes)
			throws Exception {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(codes).as(response.getBody()).contains(code(response));
	}

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertThat(node.has(field)).as("缺少字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(new BigDecimal(node.get(field).asText()).compareTo(new BigDecimal(expected)))
			.as(field + " 实际值 " + node.get(field).asText())
			.isZero();
	}

	private void assertTextualDecimal(JsonNode node, String field) {
		assertThat(node.has(field)).as("缺少十进制字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isTextual()).as(field + " 必须是 JSON 字符串，实际响应：" + node).isTrue();
		assertThat(new BigDecimal(node.get(field).asText())).as(field + " 必须是可解析十进制字符串").isNotNull();
	}

	private void assertPage(JsonNode page) {
		assertThat(page.has("items")).as("分页响应缺少 items，实际响应：" + page).isTrue();
		assertThat(page.get("items").isArray()).as("items 必须是数组，实际响应：" + page).isTrue();
		assertThat(page.has("total")).as("分页响应缺少 total，实际响应：" + page).isTrue();
		assertThat(page.has("page")).as("分页响应缺少 page，实际响应：" + page).isTrue();
		assertThat(page.has("pageSize")).as("分页响应缺少 pageSize，实际响应：" + page).isTrue();
	}

	private JsonNode firstItem(JsonNode page) {
		assertPage(page);
		assertThat(page.get("items")).as("分页响应必须至少有一条记录，实际响应：" + page).isNotEmpty();
		return page.get("items").get(0);
	}

	private JsonNode lineByNo(JsonNode lines, int lineNo) {
		assertThat(lines).as("响应缺少 lines").isNotNull();
		assertThat(lines.isArray()).as("lines 必须是数组，实际响应：" + lines).isTrue();
		for (int index = 0; index < lines.size(); index++) {
			JsonNode line = lines.get(index);
			if (line.has("lineNo") && line.get("lineNo").asInt() == lineNo) {
				return line;
			}
		}
		throw new AssertionError("未找到行号 " + lineNo + "，实际响应：" + lines);
	}

	private void assertProjectOwnershipFields(JsonNode node, long projectId) {
		assertJsonText(node, "procurementMode", "PROJECT");
		assertJsonText(node, "ownershipType", "PROJECT");
		assertJsonLong(node, "projectId", projectId);
		assertJsonText(node, "projectCode", projectCode(projectId));
		assertJsonText(node, "projectName", projectName(projectId));
	}

	private void assertSourcingProjectLineFields(JsonNode node, long projectId) {
		assertJsonText(node, "procurementMode", "PROJECT");
		assertJsonLong(node, "projectId", projectId);
		assertJsonText(node, "projectCode", projectCode(projectId));
		assertJsonText(node, "projectName", projectName(projectId));
	}

	private void assertReceiptProjectFields(JsonNode node, long projectId, boolean costVisible) {
		assertProjectOwnershipFields(node, projectId);
		assertNonBlankText(node, "valuationState");
		assertThat(node.has("costVisible")).as("缺少 costVisible，实际响应：" + node).isTrue();
		assertThat(node.get("costVisible").booleanValue()).as("costVisible 不符合权限，实际响应：" + node)
			.isEqualTo(costVisible);
	}

	private void assertOrderLineSourceFields(JsonNode line, long requisitionLineId, Long quoteLineId,
			Long priceAgreementLineId) {
		assertJsonLong(line, "requisitionLineId", requisitionLineId);
		if (quoteLineId == null) {
			assertNullOrMissing(line, "quoteLineId");
		}
		else {
			assertJsonLong(line, "quoteLineId", quoteLineId);
		}
		if (priceAgreementLineId == null) {
			assertNullOrMissing(line, "priceAgreementLineId");
		}
		else {
			assertJsonLong(line, "priceAgreementLineId", priceAgreementLineId);
		}
	}

	private void assertReturnCostSourceFields(JsonNode node, long projectId, boolean costVisible,
			String expectedOriginalValueMovementNo) {
		assertJsonText(node, "procurementMode", "PROJECT");
		assertJsonLong(node, "projectId", projectId);
		assertJsonText(node, "projectCode", projectCode(projectId));
		assertJsonText(node, "projectName", projectName(projectId));
		assertThat(node.has("costVisible")).as("缺少 costVisible，实际响应：" + node).isTrue();
		assertThat(node.get("costVisible").booleanValue()).as("costVisible 不符合权限，实际响应：" + node)
			.isEqualTo(costVisible);
		if (costVisible) {
			assertNonBlankText(node, "originalCostLayerNo");
			assertNonBlankText(node, "originalValueMovementNo");
			assertThat(node.get("originalValueMovementNo").asText()).isEqualTo(expectedOriginalValueMovementNo);
		}
		else {
			assertNullOrMissing(node, "originalCostLayerNo");
			assertNullOrMissing(node, "originalValueMovementNo");
		}
	}

	private void assertReturnProjectStateAndQuantityReadable(JsonNode node, long projectId, String quantityField) {
		assertJsonText(node, "procurementMode", "PROJECT");
		assertJsonLong(node, "projectId", projectId);
		assertJsonText(node, "projectCode", projectCode(projectId));
		assertJsonText(node, "projectName", projectName(projectId));
		assertNonBlankText(node, quantityField);
		if (node.has("status")) {
			assertNonBlankText(node, "status");
		}
	}

	private void assertReturnSensitiveCostsVisible(JsonNode node, String... fields) {
		for (String field : fields) {
			assertNonBlankText(node, field);
		}
	}

	private void assertReturnSensitiveCostsHidden(JsonNode node, String... fields) {
		for (String field : fields) {
			assertNullOrMissing(node, field);
		}
	}

	private void assertTraceCollectionVisible(JsonNode node, String field) {
		assertThat(node.has(field)).as("缺少追溯集合 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isArray()).as(field + " 必须是数组，实际响应：" + node).isTrue();
		assertThat(node.get(field)).as(field + " 管理员必须能看到真实流水，实际响应：" + node).isNotEmpty();
		JsonNode trace = node.get(field).get(0);
		assertJsonLong(trace, "id", trace.get("id").longValue());
		assertNonBlankText(trace, "movementNo");
		assertTextualDecimal(trace, "quantity");
	}

	private void assertTraceCollectionHiddenOrSanitized(JsonNode node, String field) {
		if (!node.has(field) || node.get(field).isNull()) {
			return;
		}
		assertThat(node.get(field).isArray()).as(field + " 必须是数组或隐藏，实际响应：" + node).isTrue();
		for (JsonNode trace : node.get(field)) {
			assertCostFieldsHidden(trace, "id", "movementId", "movementNo", "stockMovementId", "stockMovementNo",
					"valueMovementId", "valueMovementNo", "costLayerId", "costLayerNo", "unitPrice", "unitCost",
					"taxExcludedAmount", "costAmount", "amount", "beforeAmount", "afterAmount");
		}
	}

	private void assertJsonText(JsonNode node, String field, String expected) {
		assertThat(node.has(field)).as("缺少字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isTextual()).as(field + " 必须是字符串，实际响应：" + node).isTrue();
		assertThat(node.get(field).asText()).as(field + " 实际响应：" + node).isEqualTo(expected);
	}

	private void assertJsonLong(JsonNode node, String field, long expected) {
		assertThat(node.has(field)).as("缺少字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isIntegralNumber()).as(field + " 必须是整数，实际响应：" + node).isTrue();
		assertThat(node.get(field).longValue()).as(field + " 实际响应：" + node).isEqualTo(expected);
	}

	private void assertNonBlankText(JsonNode node, String field) {
		assertThat(node.has(field)).as("缺少字段 " + field + "，实际响应：" + node).isTrue();
		assertThat(node.get(field).isTextual()).as(field + " 必须是字符串，实际响应：" + node).isTrue();
		assertThat(node.get(field).asText()).as(field + " 不得为空，实际响应：" + node).isNotBlank();
	}

	private void assertNullOrMissing(JsonNode node, String field) {
		assertThat(!node.has(field) || node.get(field).isNull())
			.as(field + " 必须为空或不可见，实际响应：" + node)
			.isTrue();
	}

	private void assertCostFieldsHidden(JsonNode node, String... fields) {
		for (String field : fields) {
			assertNullOrMissing(node, field);
		}
	}

	private void assertAllowedActionsPresent(JsonNode node) {
		assertThat(node.has("allowedActions")).as("缺少 allowedActions，实际响应：" + node).isTrue();
		assertThat(node.get("allowedActions").isArray()).as("allowedActions 必须是数组，实际响应：" + node).isTrue();
	}

	private void assertAllowedActions(JsonNode node, String... actions) {
		assertAllowedActionsPresent(node);
		List<String> actual = new ArrayList<>();
		for (int index = 0; index < node.get("allowedActions").size(); index++) {
			JsonNode action = node.get("allowedActions").get(index);
			actual.add(action.asText());
		}
		assertThat(actual).as("allowedActions 不完整，实际响应：" + node).contains(actions);
	}

	private void assertAllowedActionsAbsent(JsonNode node, String... actions) {
		assertAllowedActionsPresent(node);
		List<String> actual = new ArrayList<>();
		for (int index = 0; index < node.get("allowedActions").size(); index++) {
			JsonNode action = node.get("allowedActions").get(index);
			actual.add(action.asText());
		}
		assertThat(actual).as("allowedActions 不应包含无权限动作，实际响应：" + node).doesNotContain(actions);
	}

	private void assertDecimal(BigDecimal actual, String expected) {
		assertThat(actual).isNotNull();
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

	private record PostedProjectReceipt(ProcurementFixture fixture, JsonNode order, JsonNode receipt,
			long receiptLineId) {
	}

	private record ProcurementFixture(long unitId, long warehouseId, long supplierId, long secondSupplierId,
			long categoryId, long materialId, long projectId, long inactiveProjectId) {
	}

}
