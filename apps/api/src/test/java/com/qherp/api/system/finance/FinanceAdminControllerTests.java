package com.qherp.api.system.finance;

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
import org.springframework.http.MediaType;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=finance-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinanceAdminControllerTests extends PostgresIntegrationTest {

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
	void receivableSourcesOnlyReturnPostedUngeneratedShipmentsAndRequireCreatePermission() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long postedShipmentId = createPostedShipment(fixture, "候选已过账", "2.000000", "10.500000");
		long draftShipmentId = createDraftShipment(fixture, "候选草稿", "1.000000", "8.000000");

		ResponseEntity<String> sources = get("/api/admin/finance/receivable-sources?settlementGenerated=false",
				admin);
		assertOk(sources);
		assertThat(itemIds(sources, "sourceId")).contains(postedShipmentId).doesNotContain(draftShipmentId);

		ResponseEntity<String> generated = createReceivable(admin, postedShipmentId, "候选过滤");
		assertOk(generated);
		ResponseEntity<String> filtered = get("/api/admin/finance/receivable-sources?settlementGenerated=false",
				admin);
		assertOk(filtered);
		assertThat(itemIds(filtered, "sourceId")).doesNotContain(postedShipmentId);

		AuthenticatedSession createOnly = createFinanceUserAndLogin("finance-source", "FIN_SRC_",
				List.of("finance:receivable:create"));
		assertOk(get("/api/admin/finance/receivable-sources?settlementGenerated=false", createOnly));
		AuthenticatedSession viewOnly = createFinanceUserAndLogin("finance-source-view", "FIN_SRC_VIEW_",
				List.of("finance:receivable:view"));
		assertForbidden(get("/api/admin/finance/receivable-sources", viewOnly));
	}

	@Test
	void lockedPeriodRejectsReceivableCreationFromHistoricalShipment() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		LocalDate date = LocalDate.of(2095, 7, 10);
		long shipmentId = createPostedShipment(fixture, "期间锁定应收来源", "1.000000", "10.000000");
		this.jdbcTemplate.update("update sal_sales_shipment set business_date = ? where id = ?", date, shipmentId);
		lockPeriod(date);
		assertError(createReceivable(admin, shipmentId, "期间锁定应收"), HttpStatus.CONFLICT,
				"BUSINESS_PERIOD_LOCKED");
	}

	@Test
	void lockedPeriodRejectsReceivableAndPayableUpdates() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2097, 7, 10);

		SalesFixture salesFixture = fixture();
		long shipmentId = createPostedShipment(salesFixture, "期间锁定应收更新来源", "1.000000", "10.000000");
		this.jdbcTemplate.update("update sal_sales_shipment set business_date = ? where id = ?", date, shipmentId);
		long receivableId = data(createReceivable(admin, shipmentId, "期间锁定应收更新")).get("id").longValue();

		ProcurementFixture procurementFixture = procurementFixture();
		long receiptId = createPostedPurchaseReceipt(procurementFixture, "期间锁定应付更新来源", "1.000000",
				"10.000000");
		this.jdbcTemplate.update("update proc_purchase_receipt set business_date = ? where id = ?", date, receiptId);
		long payableId = data(createPayable(admin, receiptId, "期间锁定应付更新")).get("id").longValue();

		lockPeriod(date);
		assertError(updateReceivable(admin, receivableId,
				Map.of("dueDate", date.plusDays(10).toString(), "remark", "期间锁定应收更新")),
				HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertError(updatePayable(admin, payableId,
				Map.of("dueDate", date.plusDays(10).toString(), "remark", "期间锁定应付更新")),
				HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	@Test
	void lockedPeriodRejectsReceivableAndPayableClosing() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2098, 7, 10);

		SalesFixture salesFixture = fixture();
		long shipmentId = createPostedShipment(salesFixture, "期间锁定应收关闭来源", "1.000000", "10.000000");
		this.jdbcTemplate.update("update sal_sales_shipment set business_date = ? where id = ?", date, shipmentId);
		long receivableId = data(createReceivable(admin, shipmentId, "期间锁定应收关闭")).get("id").longValue();
		assertOk(confirmReceivable(admin, receivableId));

		ProcurementFixture procurementFixture = procurementFixture();
		long receiptId = createPostedPurchaseReceipt(procurementFixture, "期间锁定应付关闭来源", "1.000000",
				"10.000000");
		this.jdbcTemplate.update("update proc_purchase_receipt set business_date = ? where id = ?", date, receiptId);
		long payableId = data(createPayable(admin, receiptId, "期间锁定应付关闭")).get("id").longValue();
		assertOk(confirmPayable(admin, payableId));

		lockPeriod(date);
		assertError(closeReceivable(admin, receivableId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
		assertError(closePayable(admin, payableId), HttpStatus.CONFLICT, "BUSINESS_PERIOD_LOCKED");
	}

	@Test
	void payableSourcesOnlyReturnPostedUngeneratedReceiptsAndRequireCreatePermission() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long postedReceiptId = createPostedPurchaseReceipt(fixture, "应付候选已过账", "2.000000", "10.500000");
		long draftReceiptId = createDraftPurchaseReceipt(fixture, "应付候选草稿", "1.000000", "8.000000");

		ResponseEntity<String> sources = get("/api/admin/finance/payable-sources?settlementGenerated=false", admin);
		assertOk(sources);
		assertThat(itemIds(sources, "sourceId")).contains(postedReceiptId).doesNotContain(draftReceiptId);

		ResponseEntity<String> generated = createPayable(admin, postedReceiptId, "候选过滤");
		assertOk(generated);
		ResponseEntity<String> filtered = get("/api/admin/finance/payable-sources?settlementGenerated=false", admin);
		assertOk(filtered);
		assertThat(itemIds(filtered, "sourceId")).doesNotContain(postedReceiptId);

		AuthenticatedSession createOnly = createFinanceUserAndLogin("finance-pay-source", "FIN_PAY_SRC_",
				List.of("finance:payable:create"));
		assertOk(get("/api/admin/finance/payable-sources?settlementGenerated=false", createOnly));
		AuthenticatedSession viewOnly = createFinanceUserAndLogin("finance-pay-source-view", "FIN_PAY_SRC_VIEW_",
				List.of("finance:payable:view"));
		assertForbidden(get("/api/admin/finance/payable-sources", viewOnly));
	}

	@Test
	void adminCanRunReceivableAndReceiptLifecycle() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long shipmentId = createPostedShipment(fixture, "应收主路径", "2.000000", "10.500000");

		ResponseEntity<String> created = createReceivable(admin, shipmentId, "生成应收");
		assertOk(created);
		JsonNode receivable = data(created);
		long receivableId = receivable.get("id").longValue();
		assertThat(receivable.get("status").asText()).isEqualTo("DRAFT");
		assertDecimal(receivable, "totalAmount", "21.00");
		assertDecimal(receivable, "receivedAmount", "0.00");
		assertDecimal(receivable, "unreceivedAmount", "21.00");
		assertThat(receivable.get("sources").size()).isOne();
		assertDecimal(receivable.get("sources").get(0), "sourceAmount", "21.00");

		ResponseEntity<String> updated = updateReceivable(admin, receivableId,
				Map.of("dueDate", LocalDate.now().plusDays(10).toString(), "remark", "更新应收备注"));
		assertOk(updated);
		assertThat(data(updated).get("remark").asText()).isEqualTo("更新应收备注");

		assertOk(confirmReceivable(admin, receivableId));
		JsonNode confirmed = data(getReceivable(admin, receivableId));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

		ResponseEntity<String> receiptDraft = createReceipt(admin, receivableId,
				receiptPayload("8.00", "BANK_TRANSFER", "部分收款"));
		assertOk(receiptDraft);
		long receiptId = data(receiptDraft).get("id").longValue();
		assertThat(data(receiptDraft).get("status").asText()).isEqualTo("DRAFT");
		JsonNode beforePost = data(getReceivable(admin, receivableId));
		assertDecimal(beforePost, "receivedAmount", "0.00");
		assertDecimal(beforePost, "unreceivedAmount", "21.00");

		assertOk(updateReceipt(admin, receiptId, receiptPayload("9.00", "CASH", "更新收款")));
		ResponseEntity<String> postedReceipt = postReceipt(admin, receiptId);
		assertOk(postedReceipt);
		JsonNode postedReceiptData = data(postedReceipt);
		assertThat(postedReceiptData.get("status").asText()).isEqualTo("POSTED");
		assertThat(postedReceiptData.get("allocations").size()).isOne();
		assertDecimal(postedReceiptData.get("allocations").get(0), "allocatedAmount", "9.00");

		JsonNode partial = data(getReceivable(admin, receivableId));
		assertThat(partial.get("status").asText()).isEqualTo("PARTIALLY_RECEIVED");
		assertDecimal(partial, "receivedAmount", "9.00");
		assertDecimal(partial, "unreceivedAmount", "12.00");
		assertThat(partial.get("receipts").size()).isOne();

		long finalReceiptId = data(createReceipt(admin, receivableId,
				receiptPayload("12.00", "BANK_TRANSFER", "收清"))).get("id").longValue();
		assertOk(postReceipt(admin, finalReceiptId));
		JsonNode received = data(getReceivable(admin, receivableId));
		assertThat(received.get("status").asText()).isEqualTo("RECEIVED");
		assertDecimal(received, "receivedAmount", "21.00");
		assertDecimal(received, "unreceivedAmount", "0.00");

		assertItemsContain(get("/api/admin/finance/receivables?keyword=" + received.get("receivableNo").asText(),
				admin), receivableId);
		assertItemsContain(get("/api/admin/finance/receivables?keyword=" + received.get("salesOrderNo").asText(),
				admin), receivableId);
		assertItemsContain(get("/api/admin/finance/receipts?receivableId=" + receivableId, admin), receiptId);
		assertAuditLog("FINANCE_RECEIVABLE_CREATE", "FINANCE_RECEIVABLE", receivableId);
		assertAuditLog("FINANCE_RECEIVABLE_CONFIRM", "FINANCE_RECEIVABLE", receivableId);
		assertAuditLog("FINANCE_RECEIPT_CREATE", "FINANCE_RECEIPT", receiptId);
		assertAuditLog("FINANCE_RECEIPT_UPDATE", "FINANCE_RECEIPT", receiptId);
		assertAuditLog("FINANCE_RECEIPT_POST", "FINANCE_RECEIPT", receiptId);
	}

	@Test
	void adminCanRunPayableAndPaymentLifecycle() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long receiptId = createPostedPurchaseReceipt(fixture, "应付主路径", "2.000000", "10.500000");

		ResponseEntity<String> created = createPayable(admin, receiptId, "生成应付");
		assertOk(created);
		JsonNode payable = data(created);
		long payableId = payable.get("id").longValue();
		assertThat(payable.get("status").asText()).isEqualTo("DRAFT");
		assertDecimal(payable, "totalAmount", "21.00");
		assertDecimal(payable, "paidAmount", "0.00");
		assertDecimal(payable, "unpaidAmount", "21.00");
		assertThat(payable.get("sources").size()).isOne();
		assertDecimal(payable.get("sources").get(0), "sourceAmount", "21.00");

		ResponseEntity<String> updated = updatePayable(admin, payableId,
				Map.of("dueDate", LocalDate.now().plusDays(10).toString(), "remark", "更新应付备注"));
		assertOk(updated);
		assertThat(data(updated).get("remark").asText()).isEqualTo("更新应付备注");

		assertOk(confirmPayable(admin, payableId));
		JsonNode confirmed = data(getPayable(admin, payableId));
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

		ResponseEntity<String> paymentDraft = createPayment(admin, payableId,
				paymentPayload("8.00", "BANK_TRANSFER", "部分付款"));
		assertOk(paymentDraft);
		long paymentId = data(paymentDraft).get("id").longValue();
		assertThat(data(paymentDraft).get("status").asText()).isEqualTo("DRAFT");
		JsonNode beforePost = data(getPayable(admin, payableId));
		assertDecimal(beforePost, "paidAmount", "0.00");
		assertDecimal(beforePost, "unpaidAmount", "21.00");

		assertOk(updatePayment(admin, paymentId, paymentPayload("9.00", "CASH", "更新付款")));
		ResponseEntity<String> postedPayment = postPayment(admin, paymentId);
		assertOk(postedPayment);
		JsonNode postedPaymentData = data(postedPayment);
		assertThat(postedPaymentData.get("status").asText()).isEqualTo("POSTED");
		assertThat(postedPaymentData.get("allocations").size()).isOne();
		assertDecimal(postedPaymentData.get("allocations").get(0), "allocatedAmount", "9.00");

		JsonNode partial = data(getPayable(admin, payableId));
		assertThat(partial.get("status").asText()).isEqualTo("PARTIALLY_PAID");
		assertDecimal(partial, "paidAmount", "9.00");
		assertDecimal(partial, "unpaidAmount", "12.00");
		assertThat(partial.get("payments").size()).isOne();

		long finalPaymentId = data(createPayment(admin, payableId,
				paymentPayload("12.00", "BANK_TRANSFER", "付清"))).get("id").longValue();
		assertOk(postPayment(admin, finalPaymentId));
		JsonNode paid = data(getPayable(admin, payableId));
		assertThat(paid.get("status").asText()).isEqualTo("PAID");
		assertDecimal(paid, "paidAmount", "21.00");
		assertDecimal(paid, "unpaidAmount", "0.00");

		assertItemsContain(get("/api/admin/finance/payables?keyword=" + paid.get("payableNo").asText(), admin),
				payableId);
		assertItemsContain(get("/api/admin/finance/payables?keyword=" + paid.get("purchaseOrderNo").asText(), admin),
				payableId);
		assertItemsContain(get("/api/admin/finance/payments?payableId=" + payableId, admin), paymentId);
		assertAuditLog("FINANCE_PAYABLE_CREATE", "FINANCE_PAYABLE", payableId);
		assertAuditLog("FINANCE_PAYABLE_CONFIRM", "FINANCE_PAYABLE", payableId);
		assertAuditLog("FINANCE_PAYMENT_CREATE", "FINANCE_PAYMENT", paymentId);
		assertAuditLog("FINANCE_PAYMENT_UPDATE", "FINANCE_PAYMENT", paymentId);
		assertAuditLog("FINANCE_PAYMENT_POST", "FINANCE_PAYMENT", paymentId);
	}

	@Test
	void cancelledDraftReceiptRemainsQueryableWithAllocation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long shipmentId = createPostedShipment(fixture, "取消收款可追溯", "1.000000", "10.000000");
		long receivableId = data(createReceivable(admin, shipmentId, "取消收款应收")).get("id").longValue();
		assertOk(confirmReceivable(admin, receivableId));

		long receiptId = data(createReceipt(admin, receivableId,
				receiptPayload("4.00", "CASH", "待取消收款"))).get("id").longValue();
		assertOk(cancelReceipt(admin, receiptId));

		ResponseEntity<String> cancelledDetail = getReceipt(admin, receiptId);
		assertOk(cancelledDetail);
		JsonNode cancelled = data(cancelledDetail);
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelled.get("receivableId").longValue()).isEqualTo(receivableId);
		assertThat(cancelled.get("allocations").size()).isOne();
		assertThat(cancelled.get("allocations").get(0).get("receivableId").longValue()).isEqualTo(receivableId);
		assertDecimal(cancelled.get("allocations").get(0), "allocatedAmount", "4.00");

		assertItemsContain(get("/api/admin/finance/receipts?status=CANCELLED", admin), receiptId);

		JsonNode receivable = data(getReceivable(admin, receivableId));
		assertThat(receivable.get("status").asText()).isEqualTo("CONFIRMED");
		assertDecimal(receivable, "receivedAmount", "0.00");
		assertDecimal(receivable, "unreceivedAmount", "10.00");
		assertThat(receivable.get("receipts").size()).isZero();
	}

	@Test
	void cancelledDraftPaymentRemainsQueryableWithAllocation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long receiptId = createPostedPurchaseReceipt(fixture, "取消付款可追溯", "1.000000", "10.000000");
		long payableId = data(createPayable(admin, receiptId, "取消付款应付")).get("id").longValue();
		assertOk(confirmPayable(admin, payableId));

		long paymentId = data(createPayment(admin, payableId,
				paymentPayload("4.00", "CASH", "待取消付款"))).get("id").longValue();
		assertOk(cancelPayment(admin, paymentId));

		ResponseEntity<String> cancelledDetail = getPayment(admin, paymentId);
		assertOk(cancelledDetail);
		JsonNode cancelled = data(cancelledDetail);
		assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
		assertThat(cancelled.get("payableId").longValue()).isEqualTo(payableId);
		assertThat(cancelled.get("allocations").size()).isOne();
		assertThat(cancelled.get("allocations").get(0).get("payableId").longValue()).isEqualTo(payableId);
		assertDecimal(cancelled.get("allocations").get(0), "allocatedAmount", "4.00");

		assertItemsContain(get("/api/admin/finance/payments?status=CANCELLED", admin), paymentId);

		JsonNode payable = data(getPayable(admin, payableId));
		assertThat(payable.get("status").asText()).isEqualTo("CONFIRMED");
		assertDecimal(payable, "paidAmount", "0.00");
		assertDecimal(payable, "unpaidAmount", "10.00");
		assertThat(payable.get("payments").size()).isZero();
	}

	@Test
	void receivableAndReceiptErrorsAreControlledAndDoNotLeavePartialWrites() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long draftShipmentId = createDraftShipment(fixture, "未过账来源", "1.000000", "10.000000");
		assertError(createReceivable(admin, draftShipmentId, "未过账"), HttpStatus.CONFLICT,
				"FINANCE_SOURCE_STATUS_INVALID");
		assertError(createReceivable(admin, 999_999_999L, "不存在"), HttpStatus.NOT_FOUND, "FINANCE_SOURCE_NOT_FOUND");

		long shipmentId = createPostedShipment(fixture, "重复来源", "1.000000", "10.000000");
		long receivableId = data(createReceivable(admin, shipmentId, "首次生成")).get("id").longValue();
		long sourceCountBefore = receivableSourceCount();
		assertError(createReceivable(admin, shipmentId, "重复生成"), HttpStatus.CONFLICT,
				"FINANCE_SOURCE_DUPLICATED");
		assertThat(receivableSourceCount()).isEqualTo(sourceCountBefore);

		assertOk(confirmReceivable(admin, receivableId));
		assertError(updateReceivable(admin, receivableId, Map.of("dueDate", LocalDate.now().toString())),
				HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");
		assertError(createReceipt(admin, receivableId, receiptPayload("0.00", "CASH", "零金额")), HttpStatus.BAD_REQUEST,
				"FINANCE_AMOUNT_INVALID");
		assertError(createReceipt(admin, receivableId, receiptPayload("-1.00", "CASH", "负金额")),
				HttpStatus.BAD_REQUEST, "FINANCE_AMOUNT_INVALID");
		assertError(createReceipt(admin, receivableId, receiptPayload("1.001", "CASH", "超精度")),
				HttpStatus.BAD_REQUEST, "FINANCE_AMOUNT_INVALID");
		assertError(createReceipt(admin, receivableId, receiptPayload("11.00", "CASH", "超额")),
				HttpStatus.CONFLICT, "FINANCE_ALLOCATION_EXCEEDS_BALANCE");

		long receiptId = data(createReceipt(admin, receivableId, receiptPayload("10.00", "CASH", "收清"))).get("id")
			.longValue();
		assertOk(postReceipt(admin, receiptId));
		assertError(postReceipt(admin, receiptId), HttpStatus.CONFLICT, "FINANCE_POSTED_IMMUTABLE");
		assertError(updateReceipt(admin, receiptId, receiptPayload("1.00", "CASH", "已过账编辑")),
				HttpStatus.CONFLICT, "FINANCE_POSTED_IMMUTABLE");
		assertError(cancelReceipt(admin, receiptId), HttpStatus.CONFLICT, "FINANCE_POSTED_IMMUTABLE");
		assertError(createReceipt(admin, receivableId, receiptPayload("1.00", "CASH", "已收清继续")),
				HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");

		long closeShipmentId = createPostedShipment(fixture, "关闭后收款", "2.000000", "10.000000");
		long closeReceivableId = data(createReceivable(admin, closeShipmentId, "关闭应收")).get("id").longValue();
		assertOk(confirmReceivable(admin, closeReceivableId));
		long closeReceiptId = data(createReceipt(admin, closeReceivableId,
				receiptPayload("5.00", "CASH", "关闭前部分"))).get("id").longValue();
		assertOk(postReceipt(admin, closeReceiptId));
		assertOk(closeReceivable(admin, closeReceivableId));
		assertError(createReceipt(admin, closeReceivableId, receiptPayload("1.00", "CASH", "关闭后")),
				HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");

		long cancelDraftId = data(createReceivable(admin,
				createPostedShipment(fixture, "草稿取消", "1.000000", "6.000000"), "草稿取消")).get("id").longValue();
		assertOk(cancelReceivable(admin, cancelDraftId));
		long cancelConfirmedId = data(createReceivable(admin,
				createPostedShipment(fixture, "确认取消", "1.000000", "7.000000"), "确认取消")).get("id").longValue();
		assertOk(confirmReceivable(admin, cancelConfirmedId));
		assertOk(cancelReceivable(admin, cancelConfirmedId));
		assertError(cancelReceivable(admin, closeReceivableId), HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");
	}

	@Test
	void payableAndPaymentErrorsAreControlledAndDoNotLeavePartialWrites() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long draftReceiptId = createDraftPurchaseReceipt(fixture, "应付未过账来源", "1.000000", "10.000000");
		assertError(createPayable(admin, draftReceiptId, "未过账"), HttpStatus.CONFLICT,
				"FINANCE_SOURCE_STATUS_INVALID");
		assertError(createPayable(admin, 999_999_999L, "不存在"), HttpStatus.NOT_FOUND, "FINANCE_SOURCE_NOT_FOUND");

		long receiptId = createPostedPurchaseReceipt(fixture, "应付重复来源", "1.000000", "10.000000");
		long payableId = data(createPayable(admin, receiptId, "首次生成")).get("id").longValue();
		long sourceCountBefore = payableSourceCount();
		assertError(createPayable(admin, receiptId, "重复生成"), HttpStatus.CONFLICT,
				"FINANCE_SOURCE_DUPLICATED");
		assertThat(payableSourceCount()).isEqualTo(sourceCountBefore);

		assertOk(confirmPayable(admin, payableId));
		assertError(updatePayable(admin, payableId, Map.of("dueDate", LocalDate.now().toString())),
				HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");
		assertError(createPayment(admin, payableId, paymentPayload("0.00", "CASH", "零金额")), HttpStatus.BAD_REQUEST,
				"FINANCE_AMOUNT_INVALID");
		assertError(createPayment(admin, payableId, paymentPayload("-1.00", "CASH", "负金额")),
				HttpStatus.BAD_REQUEST, "FINANCE_AMOUNT_INVALID");
		assertError(createPayment(admin, payableId, paymentPayload("1.001", "CASH", "超精度")),
				HttpStatus.BAD_REQUEST, "FINANCE_AMOUNT_INVALID");
		assertError(createPayment(admin, payableId, paymentPayload("11.00", "CASH", "超额")),
				HttpStatus.CONFLICT, "FINANCE_ALLOCATION_EXCEEDS_BALANCE");

		long paymentId = data(createPayment(admin, payableId, paymentPayload("10.00", "CASH", "付清"))).get("id")
			.longValue();
		assertOk(postPayment(admin, paymentId));
		assertError(postPayment(admin, paymentId), HttpStatus.CONFLICT, "FINANCE_POSTED_IMMUTABLE");
		assertError(updatePayment(admin, paymentId, paymentPayload("1.00", "CASH", "已过账编辑")),
				HttpStatus.CONFLICT, "FINANCE_POSTED_IMMUTABLE");
		assertError(cancelPayment(admin, paymentId), HttpStatus.CONFLICT, "FINANCE_POSTED_IMMUTABLE");
		assertError(createPayment(admin, payableId, paymentPayload("1.00", "CASH", "已付清继续")),
				HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");

		long closeReceiptId = createPostedPurchaseReceipt(fixture, "应付关闭后付款", "2.000000", "10.000000");
		long closePayableId = data(createPayable(admin, closeReceiptId, "关闭应付")).get("id").longValue();
		assertOk(confirmPayable(admin, closePayableId));
		long closePaymentId = data(createPayment(admin, closePayableId,
				paymentPayload("5.00", "CASH", "关闭前部分"))).get("id").longValue();
		assertOk(postPayment(admin, closePaymentId));
		assertOk(closePayable(admin, closePayableId));
		assertError(createPayment(admin, closePayableId, paymentPayload("1.00", "CASH", "关闭后")),
				HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");

		long cancelDraftId = data(createPayable(admin,
				createPostedPurchaseReceipt(fixture, "应付草稿取消", "1.000000", "6.000000"), "草稿取消")).get("id")
			.longValue();
		assertOk(cancelPayable(admin, cancelDraftId));
		long cancelConfirmedId = data(createPayable(admin,
				createPostedPurchaseReceipt(fixture, "应付确认取消", "1.000000", "7.000000"), "确认取消")).get("id")
			.longValue();
		assertOk(confirmPayable(admin, cancelConfirmedId));
		assertOk(cancelPayable(admin, cancelConfirmedId));
		assertError(cancelPayable(admin, closePayableId), HttpStatus.CONFLICT, "FINANCE_STATUS_NOT_ALLOWED");
	}

	@Test
	void permissionsAuthenticationCsrfAndAuditBoundariesAreEnforced() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long shipmentId = createPostedShipment(fixture, "权限路径", "1.000000", "10.000000");
		ProcurementFixture procurementFixture = procurementFixture();
		long purchaseReceiptId = createPostedPurchaseReceipt(procurementFixture, "权限采购路径", "1.000000",
				"10.000000");

		assertUnauthorized(get("/api/admin/finance/receivables", null));
		AuthenticatedSession none = createFinanceUserAndLogin("finance-none", "FIN_NONE_", List.of());
		assertForbidden(get("/api/admin/finance/receivables", none));
		assertForbidden(get("/api/admin/finance/payables", none));
		AuthenticatedSession salesReadOnly = createFinanceUserAndLogin("finance-sales-read", "FIN_SALES_READ_",
				List.of("finance:receivable:view", "finance:receipt:view"));
		assertOk(get("/api/admin/finance/receivables", salesReadOnly));
		assertForbidden(createReceivable(salesReadOnly, shipmentId, "只读生成"));
		assertForbidden(get("/api/admin/finance/payables", salesReadOnly));

		AuthenticatedSession procurementReadOnly = createFinanceUserAndLogin("finance-proc-read", "FIN_PROC_READ_",
				List.of("finance:payable:view", "finance:payment:view"));
		assertOk(get("/api/admin/finance/payables", procurementReadOnly));
		assertForbidden(createPayable(procurementReadOnly, purchaseReceiptId, "只读生成应付"));
		assertForbidden(get("/api/admin/finance/receivables", procurementReadOnly));

		AuthenticatedSession financeRole = createFinanceUserAndLogin("finance-full", "FIN_FULL_",
				List.of("finance:receivable:view", "finance:receivable:create", "finance:receivable:update",
						"finance:receivable:confirm", "finance:receivable:cancel", "finance:receivable:close",
						"finance:receipt:view", "finance:receipt:create", "finance:receipt:update",
						"finance:receipt:post", "finance:receipt:cancel", "finance:payable:view",
						"finance:payable:create", "finance:payable:update", "finance:payable:confirm",
						"finance:payable:cancel", "finance:payable:close", "finance:payment:view",
						"finance:payment:create", "finance:payment:update", "finance:payment:post",
						"finance:payment:cancel"));
		ResponseEntity<String> created = createReceivable(financeRole, shipmentId, "财务角色生成");
		assertOk(created);
		ResponseEntity<String> payableCreated = createPayable(financeRole, purchaseReceiptId, "财务角色生成应付");
		assertOk(payableCreated);

		long csrfShipmentId = createPostedShipment(fixture, "CSRF", "1.000000", "8.000000");
		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/finance/receivables",
				receivablePayload(csrfShipmentId, "缺 CSRF"), admin));
		long csrfReceiptId = createPostedPurchaseReceipt(procurementFixture, "应付 CSRF", "1.000000", "8.000000");
		assertForbidden(exchangeWithoutCsrf(HttpMethod.POST, "/api/admin/finance/payables",
				payablePayload(csrfReceiptId, "缺 CSRF"), admin));
	}

	private long createPostedShipment(SalesFixture fixture, String remark, String quantity, String unitPrice) {
		return createShipment(fixture, remark, quantity, unitPrice, "POSTED");
	}

	private long createDraftShipment(SalesFixture fixture, String remark, String quantity, String unitPrice) {
		return createShipment(fixture, remark, quantity, unitPrice, "DRAFT");
	}

	private long createPostedPurchaseReceipt(ProcurementFixture fixture, String remark, String quantity,
			String unitPrice) {
		return createPurchaseReceipt(fixture, remark, quantity, unitPrice, "POSTED");
	}

	private long createDraftPurchaseReceipt(ProcurementFixture fixture, String remark, String quantity,
			String unitPrice) {
		return createPurchaseReceipt(fixture, remark, quantity, unitPrice, "DRAFT");
	}

	private long createShipment(SalesFixture fixture, String remark, String quantity, String unitPrice,
			String shipmentStatus) {
		int suffix = SEQUENCE.incrementAndGet();
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "FIN-SO-" + suffix, fixture.customerId(), LocalDate.now(),
				LocalDate.now().plusDays(3), remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.materialId(), fixture.unitId(), new BigDecimal(quantity),
				"POSTED".equals(shipmentStatus) ? new BigDecimal(quantity) : BigDecimal.ZERO,
				new BigDecimal(unitPrice), LocalDate.now().plusDays(3), remark);
		long shipmentId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), ?, case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, "FIN-SH-" + suffix, orderId, fixture.customerId(), fixture.warehouseId(),
				LocalDate.now(), shipmentStatus, remark, "POSTED".equals(shipmentStatus) ? "test" : null,
				shipmentStatus);
		this.jdbcTemplate.update("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, ?, now(), now())
				""", shipmentId, orderLineId, fixture.materialId(), fixture.unitId(), new BigDecimal(quantity),
				new BigDecimal(quantity), new BigDecimal(quantity), remark);
		return shipmentId;
	}

	private long createPurchaseReceipt(ProcurementFixture fixture, String remark, String quantity, String unitPrice,
			String receiptStatus) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal orderedQuantity = new BigDecimal(quantity);
		BigDecimal receivedQuantity = "POSTED".equals(receiptStatus) ? orderedQuantity : BigDecimal.ZERO;
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "FIN-PO-" + suffix, fixture.supplierId(), LocalDate.now(),
				LocalDate.now().plusDays(3), "POSTED".equals(receiptStatus) ? "RECEIVED" : "CONFIRMED", remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					expected_arrival_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.materialId(), fixture.unitId(), orderedQuantity, receivedQuantity,
				new BigDecimal(unitPrice), LocalDate.now().plusDays(3), remark);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), ?, case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, "FIN-PR-" + suffix, orderId, fixture.supplierId(), fixture.warehouseId(),
				LocalDate.now(), receiptStatus, remark, "POSTED".equals(receiptStatus) ? "test" : null,
				receiptStatus);
		this.jdbcTemplate.update("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, ?, now(), now())
				""", receiptId, orderLineId, fixture.materialId(), fixture.unitId(), orderedQuantity, orderedQuantity,
				orderedQuantity, remark);
		return receiptId;
	}

	private SalesFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("FIN_UNIT_" + suffix, "财务单位" + suffix);
		long warehouseId = insertWarehouse("FIN_WH_" + suffix, "财务销售仓" + suffix);
		long customerId = insertCustomer("FIN_CUS_" + suffix, "财务客户" + suffix);
		long categoryId = insertMaterialCategory("FIN_CAT_" + suffix);
		long materialId = insertMaterial("FIN_MAT_" + suffix, "财务成品" + suffix, categoryId, unitId);
		return new SalesFixture(unitId, warehouseId, customerId, categoryId, materialId);
	}

	private ProcurementFixture procurementFixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("FIN_PU_UNIT_" + suffix, "财务采购单位" + suffix);
		long warehouseId = insertWarehouse("FIN_PU_WH_" + suffix, "财务采购仓" + suffix);
		long supplierId = insertSupplier("FIN_SUP_" + suffix, "财务供应商" + suffix);
		long categoryId = insertMaterialCategory("FIN_PU_CAT_" + suffix);
		long materialId = insertPurchasedMaterial("FIN_PU_MAT_" + suffix, "财务采购物料" + suffix, categoryId,
				unitId);
		return new ProcurementFixture(unitId, warehouseId, supplierId, categoryId, materialId);
	}

	private long insertUnit(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_unit (code, name, precision_scale, status, sort_order, created_by, created_at, updated_by,
					updated_at)
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
				""", Long.class, code, "财务分类" + code);
	}

	private long insertPurchasedMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, created_by, created_at, updated_by, updated_at)
				values (?, ?, '财务采购规格', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private long insertMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, created_by, created_at, updated_by, updated_at)
				values (?, ?, '财务规格', 'FINISHED_GOOD', 'SELF_MADE', ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private AuthenticatedSession createFinanceUserAndLogin(String usernamePrefix, String rolePrefix,
			List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, rolePrefix + suffix, "财务测试角色" + suffix, "财务测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "财务测试用户" + suffix);
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

	private ResponseEntity<String> createReceivable(AuthenticatedSession session, long sourceId, String remark) {
		return exchange(HttpMethod.POST, "/api/admin/finance/receivables", receivablePayload(sourceId, remark),
				session);
	}

	private Map<String, Object> receivablePayload(long sourceId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceType", "SALES_SHIPMENT");
		payload.put("sourceId", sourceId);
		payload.put("dueDate", LocalDate.now().plusDays(7).toString());
		payload.put("remark", remark);
		return payload;
	}

	private ResponseEntity<String> createPayable(AuthenticatedSession session, long sourceId, String remark) {
		return exchange(HttpMethod.POST, "/api/admin/finance/payables", payablePayload(sourceId, remark), session);
	}

	private Map<String, Object> payablePayload(long sourceId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceType", "PURCHASE_RECEIPT");
		payload.put("sourceId", sourceId);
		payload.put("dueDate", LocalDate.now().plusDays(7).toString());
		payload.put("remark", remark);
		return payload;
	}

	private ResponseEntity<String> getReceivable(AuthenticatedSession session, long id) {
		return get("/api/admin/finance/receivables/" + id, session);
	}

	private ResponseEntity<String> getPayable(AuthenticatedSession session, long id) {
		return get("/api/admin/finance/payables/" + id, session);
	}

	private ResponseEntity<String> updateReceivable(AuthenticatedSession session, long id, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receivables/" + id, body, session);
	}

	private ResponseEntity<String> updatePayable(AuthenticatedSession session, long id, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payables/" + id, body, session);
	}

	private ResponseEntity<String> confirmReceivable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receivables/" + id + "/confirm", null, session);
	}

	private ResponseEntity<String> confirmPayable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payables/" + id + "/confirm", null, session);
	}

	private ResponseEntity<String> cancelReceivable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receivables/" + id + "/cancel", null, session);
	}

	private ResponseEntity<String> cancelPayable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payables/" + id + "/cancel", null, session);
	}

	private ResponseEntity<String> closeReceivable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receivables/" + id + "/close", null, session);
	}

	private ResponseEntity<String> closePayable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payables/" + id + "/close", null, session);
	}

	private ResponseEntity<String> createReceipt(AuthenticatedSession session, long receivableId,
			Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/finance/receivables/" + receivableId + "/receipts", body,
				session);
	}

	private ResponseEntity<String> createPayment(AuthenticatedSession session, long payableId,
			Map<String, Object> body) {
		return exchange(HttpMethod.POST, "/api/admin/finance/payables/" + payableId + "/payments", body, session);
	}

	private ResponseEntity<String> getReceipt(AuthenticatedSession session, long id) {
		return get("/api/admin/finance/receipts/" + id, session);
	}

	private ResponseEntity<String> getPayment(AuthenticatedSession session, long id) {
		return get("/api/admin/finance/payments/" + id, session);
	}

	private ResponseEntity<String> updateReceipt(AuthenticatedSession session, long id, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receipts/" + id, body, session);
	}

	private ResponseEntity<String> updatePayment(AuthenticatedSession session, long id, Map<String, Object> body) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payments/" + id, body, session);
	}

	private ResponseEntity<String> postReceipt(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receipts/" + id + "/post", null, session);
	}

	private ResponseEntity<String> postPayment(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payments/" + id + "/post", null, session);
	}

	private ResponseEntity<String> cancelReceipt(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receipts/" + id + "/cancel", null, session);
	}

	private ResponseEntity<String> cancelPayment(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payments/" + id + "/cancel", null, session);
	}

	private Map<String, Object> receiptPayload(String amount, String method, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("receiptDate", LocalDate.now().toString());
		payload.put("amount", amount);
		payload.put("method", method);
		payload.put("remark", remark);
		return payload;
	}

	private Map<String, Object> paymentPayload(String amount, String method, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("paymentDate", LocalDate.now().toString());
		payload.put("amount", amount);
		payload.put("method", method);
		payload.put("remark", remark);
		return payload;
	}

	private void lockPeriod(LocalDate date) {
		this.jdbcTemplate.update("insert into biz_business_period (period_code, period_name, start_date, end_date, status, created_at, updated_at) values (?, ?, ?, ?, 'LOCKED', now(), now())",
				"LOCK-FIN-" + date, "锁定期间", date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
	}

	private long receivableSourceCount() {
		return this.jdbcTemplate.queryForObject("select count(*) from fin_receivable_source", Long.class);
	}

	private long payableSourceCount() {
		return this.jdbcTemplate.queryForObject("select count(*) from fin_payable_source", Long.class);
	}

	private void assertAuditLog(String action, String targetType, long targetId) {
		assertThat(this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_audit_log
				where action = ?
				and target_type = ?
				and target_id = ?
				""", Long.class, action, targetType, Long.toString(targetId))).as(action).isOne();
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
		headers.setContentType(MediaType.APPLICATION_JSON);
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

	private List<Long> itemIds(ResponseEntity<String> response, String field) throws Exception {
		JsonNode items = data(response).get("items");
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			ids.add(items.get(i).get(field).longValue());
		}
		return ids;
	}

	private void assertItemsContain(ResponseEntity<String> response, long id) throws Exception {
		assertOk(response);
		assertThat(itemIds(response, "id")).contains(id);
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
		assertThat(decimal(node, field).compareTo(new BigDecimal(expected))).isZero();
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

	private record SalesFixture(long unitId, long warehouseId, long customerId, long categoryId, long materialId) {
	}

	private record ProcurementFixture(long unitId, long warehouseId, long supplierId, long categoryId,
			long materialId) {
	}

}
