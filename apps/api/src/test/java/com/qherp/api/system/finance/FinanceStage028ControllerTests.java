package com.qherp.api.system.finance;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=finance-stage-028")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinanceStage028ControllerTests extends PostgresIntegrationTest {

	private static final String ADMIN_PASSWORD = "Qherp@2026!";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final List<String> STAGE_028_TABLES = List.of("fin_sales_invoice", "fin_sales_invoice_line",
			"fin_sales_invoice_receivable_link", "fin_purchase_invoice", "fin_purchase_invoice_line",
			"fin_purchase_invoice_match_difference", "fin_purchase_invoice_payable_link", "fin_expense",
			"fin_expense_line", "fin_receipt_balance", "fin_payment_balance", "fin_settlement_allocation",
			"fin_settlement_allocation_line", "fin_voucher_draft", "fin_voucher_draft_line");

	private static final List<String> STAGE_028_PERMISSION_CODES = List.of("finance:sales-invoice:view",
			"finance:sales-invoice:create", "finance:sales-invoice:update", "finance:sales-invoice:confirm",
			"finance:sales-invoice:cancel", "finance:purchase-invoice:view", "finance:purchase-invoice:create",
			"finance:purchase-invoice:update", "finance:purchase-invoice:match",
			"finance:purchase-invoice:confirm", "finance:purchase-invoice:cancel", "finance:expense:view",
			"finance:expense:create", "finance:expense:update", "finance:expense:confirm",
			"finance:expense:cancel", "finance:advance-receipt:view", "finance:advance-receipt:create",
			"finance:advance-receipt:update", "finance:advance-receipt:post",
			"finance:advance-receipt:cancel", "finance:prepayment:view", "finance:prepayment:create",
			"finance:prepayment:update", "finance:prepayment:post", "finance:prepayment:cancel",
			"finance:settlement-allocation:view", "finance:settlement-allocation:create",
			"finance:settlement-allocation:update", "finance:settlement-allocation:post",
			"finance:settlement-allocation:cancel", "finance:voucher-draft:view",
			"finance:voucher-draft:generate", "finance:voucher-draft:ready",
			"finance:voucher-draft:cancel", "finance:settlement-sensitive:view",
			"finance:settlement-sensitive:update");

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void v30MigrationAddsForwardTablesPermissionsAndMultiTargetIndexes() {
		assertThat(existingTableCount(STAGE_028_TABLES)).isEqualTo(STAGE_028_TABLES.size());
		assertThat(permissionCount(STAGE_028_PERMISSION_CODES)).isEqualTo(STAGE_028_PERMISSION_CODES.size());
		assertThat(systemAdminPermissionCount(STAGE_028_PERMISSION_CODES)).isEqualTo(STAGE_028_PERMISSION_CODES.size());
		assertThat(constraintExists("fin_receipt_allocation", "uk_fin_receipt_allocation_receipt")).isFalse();
		assertThat(constraintExists("fin_payment_allocation", "uk_fin_payment_allocation_payment")).isFalse();
		assertThat(indexExists("uk_fin_receipt_allocation_receipt_target")).isTrue();
		assertThat(indexExists("uk_fin_payment_allocation_payment_target")).isTrue();
	}

	@Test
	void salesInvoiceConfirmBindsExistingReceivableAndKeepsSettlementTaxSensitive() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		insertCustomerSettlementTax(fixture.customerId(), "销售抬头", "91310000SECRET028");
		long shipmentId = createPostedShipment(fixture, "028 销售发票来源", "2.000000", "10.000000");
		long receivableId = data(createReceivable(admin, shipmentId, "历史应收")).get("id").longValue();
		assertOk(confirmReceivable(admin, receivableId));
		long beforeReceivableCount = countReceivablesBySource(shipmentId);

		long invoiceId = data(createSalesInvoice(admin, salesInvoicePayload(shipmentId, "SI-EXT-"))).get("id")
			.longValue();
		ResponseEntity<String> confirmed = exchange(HttpMethod.PUT,
				"/api/admin/finance/sales-invoices/" + invoiceId + "/confirm",
				actionPayload(0, "SI-CONFIRM-"), admin);

		assertOk(confirmed);
		JsonNode invoice = data(confirmed);
		assertThat(invoice.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(invoice.get("linkedReceivableId").longValue()).isEqualTo(receivableId);
		assertDecimal(invoice, "taxIncludedAmount", "20.00");
		assertThat(countReceivablesBySource(shipmentId)).isEqualTo(beforeReceivableCount);
		assertThat(linkCount("fin_sales_invoice_receivable_link", "sales_invoice_id", invoiceId, "receivable_id",
				receivableId)).isOne();

		AuthenticatedSession restricted = createFinanceUserAndLogin("finance-si-view", "FIN_SI_VIEW_",
				List.of("finance:sales-invoice:view"));
		ResponseEntity<String> restrictedDetail = get("/api/admin/finance/sales-invoices/" + invoiceId, restricted);
		assertOk(restrictedDetail);
		JsonNode snapshot = data(restrictedDetail).get("partySettlementSnapshot");
		assertThat(snapshot.get("taxNo").asText()).isEqualTo("******");
		assertThat(data(restrictedDetail).get("restrictedReasons").toString())
			.contains("finance:settlement-sensitive:view");

		assertError(createSalesInvoice(admin, salesInvoicePayload(shipmentId, "SI-DUP-")), HttpStatus.CONFLICT,
				"FINANCE_SOURCE_OVER_INVOICED");
	}

	@Test
	void purchaseInvoiceRequiresZeroToleranceMatchAndCreatesSingleSourcePayable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long receiptId = createPostedPurchaseReceipt(fixture, "028 标准采购发票来源", "2.000000", "8.000000");

		long invoiceId = data(createPurchaseInvoice(admin,
				purchaseInvoicePayload("STANDARD_PURCHASE", "PURCHASE_RECEIPT", receiptId, "2.000000", "8.000000")))
			.get("id")
			.longValue();
		ResponseEntity<String> confirmed = exchange(HttpMethod.PUT,
				"/api/admin/finance/purchase-invoices/" + invoiceId + "/confirm",
				actionPayload(0, "PI-CONFIRM-"), admin);

		assertOk(confirmed);
		JsonNode invoice = data(confirmed);
		assertThat(invoice.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(invoice.get("matchStatus").asText()).isEqualTo("MATCHED");
		long payableId = invoice.get("linkedPayableId").longValue();
		assertThat(countPayablesBySource("PURCHASE_RECEIPT", receiptId)).isOne();
		assertThat(linkCount("fin_purchase_invoice_payable_link", "purchase_invoice_id", invoiceId, "payable_id",
				payableId)).isOne();

		long exceptionReceiptId = createPostedPurchaseReceipt(fixture, "028 标准采购价差", "1.000000", "8.000000");
		long exceptionInvoiceId = data(createPurchaseInvoice(admin,
				purchaseInvoicePayload("STANDARD_PURCHASE", "PURCHASE_RECEIPT", exceptionReceiptId, "1.000000",
						"8.010000")))
			.get("id")
			.longValue();
		ResponseEntity<String> matched = exchange(HttpMethod.PUT,
				"/api/admin/finance/purchase-invoices/" + exceptionInvoiceId + "/match",
				actionPayload(0, "PI-MATCH-"), admin);
		assertOk(matched);
		assertThat(data(matched).get("matchStatus").asText()).isEqualTo("EXCEPTION");
		assertThat(data(matched).get("matchDifferences").size()).isPositive();
		assertError(exchange(HttpMethod.PUT,
				"/api/admin/finance/purchase-invoices/" + exceptionInvoiceId + "/confirm",
				actionPayload(1, "PI-EX-CONFIRM-"), admin),
				HttpStatus.CONFLICT, "FINANCE_MATCH_EXCEPTION");
	}

	@Test
	void outsourcingSettlementCreatesSupplierPayableWithoutInventoryValueRewrite() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long outsourcingReceiptId = createPostedOutsourcingReceipt(fixture, "028 外协结算来源", "3.000000",
				"12.000000");
		long beforeValueMovements = tableCount("inv_value_movement");

		long invoiceId = data(createPurchaseInvoice(admin,
				purchaseInvoicePayload("OUTSOURCING", "OUTSOURCING_RECEIPT", outsourcingReceiptId, "3.000000",
						"12.000000")))
			.get("id")
			.longValue();
		ResponseEntity<String> confirmed = exchange(HttpMethod.PUT,
				"/api/admin/finance/purchase-invoices/" + invoiceId + "/confirm",
				actionPayload(0, "PI-OUT-CONFIRM-"), admin);

		assertOk(confirmed);
		JsonNode invoice = data(confirmed);
		assertThat(invoice.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(invoice.get("settlementKind").asText()).isEqualTo("OUTSOURCING");
		assertThat(invoice.get("matchStatus").asText()).isEqualTo("NOT_APPLICABLE");
		assertThat(countPayablesBySource("OUTSOURCING_SETTLEMENT", invoiceId)).isOne();
		assertThat(tableCount("inv_value_movement")).isEqualTo(beforeValueMovements);
	}

	@Test
	void projectAndPublicSupplierExpensesCreateOnlyPayableNotFormalCost() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		ProcurementFixture supplierFixture = procurementFixture();
		long projectId = createProject(salesFixture.customerId(), "028 项目费用");
		long beforeCostRecords = tableCount("mfg_cost_record");
		long beforeValueMovements = tableCount("inv_value_movement");

		long projectExpenseId = data(createExpense(admin,
				expensePayload(supplierFixture.supplierId(), "PROJECT", projectId, "项目服务费", "120.00")))
			.get("id")
			.longValue();
		ResponseEntity<String> confirmedProjectExpense = exchange(HttpMethod.PUT,
				"/api/admin/finance/expenses/" + projectExpenseId + "/confirm",
				actionPayload(0, "EXP-PROJECT-CONFIRM-"), admin);
		assertOk(confirmedProjectExpense);
		JsonNode projectExpense = data(confirmedProjectExpense);
		assertThat(projectExpense.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(projectExpense.get("ownershipType").asText()).isEqualTo("PROJECT");
		assertThat(projectExpense.get("projectId").longValue()).isEqualTo(projectId);
		assertThat(countPayablesBySource("EXPENSE", projectExpenseId)).isOne();

		long publicExpenseId = data(createExpense(admin,
				expensePayload(supplierFixture.supplierId(), "PUBLIC", null, "公共服务费", "60.00")))
			.get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/expenses/" + publicExpenseId + "/confirm",
				actionPayload(0, "EXP-PUBLIC-CONFIRM-"), admin));
		assertThat(countPayablesBySource("EXPENSE", publicExpenseId)).isOne();
		assertThat(tableCount("mfg_cost_record")).isEqualTo(beforeCostRecords);
		assertThat(tableCount("inv_value_movement")).isEqualTo(beforeValueMovements);
	}

	@Test
	void advanceReceiptPrepaymentAndWorkbenchAllocateOneCashDocumentToMultipleTargets() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		long firstReceivable = confirmedReceivable(admin, salesFixture, "028 预收核销一", "1.000000", "10.000000");
		long secondReceivable = confirmedReceivable(admin, salesFixture, "028 预收核销二", "1.000000", "20.000000");

		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "30.00", "028 预收款"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + advanceReceiptId + "/post",
				actionPayload(0, "ADV-POST-"), admin));
		long receivableAllocationId = data(createSettlementAllocation(admin,
				receivableAllocationPayload(advanceReceiptId, firstReceivable, secondReceivable))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT,
				"/api/admin/finance/settlement-workbench/allocations/" + receivableAllocationId + "/post",
				actionPayload(0, "ALR-POST-"), admin));

		assertThat(tableCount("fin_receipt_allocation", "receipt_id", advanceReceiptId)).isEqualTo(2);
		assertDecimal("select amount from fin_receipt where id = ?", advanceReceiptId, "30.00");
		assertDecimal("select available_amount from fin_receipt_balance where receipt_id = ?", advanceReceiptId,
				"0.00");
		assertDecimal("select unreceived_amount from fin_receivable where id = ?", firstReceivable, "0.00");
		assertDecimal("select unreceived_amount from fin_receivable where id = ?", secondReceivable, "0.00");

		ProcurementFixture procurementFixture = procurementFixture();
		long firstPayable = confirmedPayable(admin, procurementFixture, "028 预付核销一", "1.000000", "11.000000");
		long secondPayable = confirmedPayable(admin, procurementFixture, "028 预付核销二", "1.000000",
				"19.000000");
		long prepaymentId = data(createPrepayment(admin,
				prepaymentPayload(procurementFixture.supplierId(), "30.00", "028 预付款"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/" + prepaymentId + "/post",
				actionPayload(0, "PRE-POST-"), admin));
		long payableAllocationId = data(createSettlementAllocation(admin,
				payableAllocationPayload(prepaymentId, firstPayable, secondPayable))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT,
				"/api/admin/finance/settlement-workbench/allocations/" + payableAllocationId + "/post",
				actionPayload(0, "ALP-POST-"), admin));

		assertThat(tableCount("fin_payment_allocation", "payment_id", prepaymentId)).isEqualTo(2);
		assertDecimal("select amount from fin_payment where id = ?", prepaymentId, "30.00");
		assertDecimal("select available_amount from fin_payment_balance where payment_id = ?", prepaymentId,
				"0.00");
		assertDecimal("select unpaid_amount from fin_payable where id = ?", firstPayable, "0.00");
		assertDecimal("select unpaid_amount from fin_payable where id = ?", secondPayable, "0.00");
	}

	@Test
	void settlementFundActionsRequireAllocationCreatePermission() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "12.00", "028 资金池动作权限"))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + advanceReceiptId + "/post",
				actionPayload(0, "ADV-PERM-POST-"), admin));

		AuthenticatedSession viewer = createFinanceUserAndLogin("finance-settle-view", "FIN_SETTLE_VIEW_",
				List.of("finance:settlement-allocation:view"));
		ResponseEntity<String> funds = get("/api/admin/finance/settlement-workbench/funds?direction=CUSTOMER&partnerId="
				+ salesFixture.customerId(), viewer);

		assertOk(funds);
		JsonNode items = data(funds).get("items");
		assertThat(items).hasSizeGreaterThanOrEqualTo(1);
		assertThat(items.get(0).get("allowedActions")).isEmpty();
	}

	@Test
	void voucherDraftGenerationIsIdempotentAndNeverCreatesFormalVoucherFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long shipmentId = createPostedShipment(fixture, "028 凭证草稿销售来源", "1.000000", "15.000000");
		long invoiceId = data(createSalesInvoice(admin, salesInvoicePayload(shipmentId, "SI-VD-"))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/" + invoiceId + "/confirm",
				actionPayload(0, "SI-VD-CONFIRM-"), admin));

		Map<String, Object> request = Map.of("sourceType", "SALES_INVOICE", "sourceId", invoiceId, "version", 1,
				"idempotencyKey", "VD-" + SEQUENCE.incrementAndGet());
		ResponseEntity<String> generated = exchange(HttpMethod.POST, "/api/admin/finance/voucher-drafts/generate",
				request, admin);
		ResponseEntity<String> generatedAgain = exchange(HttpMethod.POST, "/api/admin/finance/voucher-drafts/generate",
				request, admin);

		assertOk(generated);
		assertOk(generatedAgain);
		JsonNode draft = data(generated);
		assertThat(data(generatedAgain).get("id").longValue()).isEqualTo(draft.get("id").longValue());
		assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.get("sourceType").asText()).isEqualTo("SALES_INVOICE");
		assertThat(draft.get("formalVoucherNo").isNull()).isTrue();
		assertThat(draft.get("postingStatus").isNull()).isTrue();
		assertDecimal(draft, "debitAmount", "15.00");
		assertDecimal(draft, "creditAmount", "15.00");
		assertThat(draft.get("lines").size()).isEqualTo(2);
		assertThat(draft.get("lines").get(0).has("accountCode")).isFalse();

		ResponseEntity<String> ready = exchange(HttpMethod.PUT,
				"/api/admin/finance/voucher-drafts/" + draft.get("id").longValue() + "/ready",
				actionPayload(0, "VD-READY-"), admin);
		assertOk(ready);
		assertThat(data(ready).get("status").asText()).isEqualTo("READY");
		assertThat(data(ready).get("postingStatus").isNull()).isTrue();
	}

	@Test
	void stage028ServiceRejectsNullCurrentUserFailClosed() throws Exception {
		Object service = this.applicationContext.getBean("financeStage028Service");
		Method method = service.getClass()
			.getMethod("salesInvoices", String.class, String.class, CurrentUser.class, int.class, int.class);

		assertThatThrownBy(() -> method.invoke(service, null, null, null, 1, 20)).satisfies((exception) -> {
			Throwable actual = exception instanceof InvocationTargetException invocationTargetException
					? invocationTargetException.getCause() : exception;
			assertThat(actual).isInstanceOf(BusinessException.class);
			assertThat(((BusinessException) actual).errorCode()).isEqualTo(ApiErrorCode.AUTH_FORBIDDEN);
		});
	}

	@Test
	void stage028MutationsRejectStaleVersionWhenProvided() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		long shipmentId = createPostedShipment(salesFixture, "028 stale version", "1.000000", "10.000000");
		long shipmentLineId = salesShipmentLineId(shipmentId);
		long salesInvoiceId = data(createSalesInvoice(admin,
				frontendSalesInvoicePayload(salesFixture.customerId(), shipmentLineId, "stale version"))).get("id")
			.longValue();
		Map<String, Object> staleUpdate = new LinkedHashMap<>(frontendSalesInvoicePayload(salesFixture.customerId(),
				shipmentLineId, "stale update"));
		staleUpdate.put("version", 99);

		assertError(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/" + salesInvoiceId, staleUpdate,
				admin), HttpStatus.CONFLICT, "FINANCE_CONCURRENT_MODIFICATION");
		assertError(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/" + salesInvoiceId + "/cancel",
				Map.of("version", 99, "idempotencyKey", "SI-STALE-CANCEL-" + SEQUENCE.incrementAndGet()), admin),
				HttpStatus.CONFLICT, "FINANCE_CONCURRENT_MODIFICATION");
	}

	@Test
	void frontendFacingRoutesSupportDraftLifecycleCandidatesPoolsAndVoucherCancel() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		ProcurementFixture procurementFixture = procurementFixture();

		long shipmentId = createPostedShipment(salesFixture, "028 前端销售候选", "1.000000", "10.000000");
		long shipmentLineId = salesShipmentLineId(shipmentId);
		assertOk(get("/api/admin/finance/sales-invoices/candidates?page=1&pageSize=10", admin));
		long salesInvoiceId = data(createSalesInvoice(admin,
				frontendSalesInvoicePayload(salesFixture.customerId(), shipmentLineId, "前端销售草稿"))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/" + salesInvoiceId,
				frontendSalesInvoicePayload(salesFixture.customerId(), shipmentLineId, "前端销售更新"), admin));
		ResponseEntity<String> cancelledSalesInvoice = exchange(HttpMethod.PUT,
				"/api/admin/finance/sales-invoices/" + salesInvoiceId + "/cancel",
				Map.of("version", 1, "idempotencyKey", "SI-CANCEL-" + SEQUENCE.incrementAndGet()), admin);
		assertOk(cancelledSalesInvoice);
		assertThat(data(cancelledSalesInvoice).get("status").asText()).isEqualTo("CANCELLED");

		long receiptId = createPostedPurchaseReceipt(procurementFixture, "028 前端采购候选", "1.000000",
				"9.000000");
		long receiptLineId = sourceLineId("PURCHASE_RECEIPT", receiptId);
		assertOk(get("/api/admin/finance/purchase-invoices/candidates?page=1&pageSize=10", admin));
		long purchaseInvoiceId = data(createPurchaseInvoice(admin,
				frontendPurchaseInvoicePayload(procurementFixture.supplierId(), receiptLineId, "前端采购草稿"))).get("id")
			.longValue();
		assertOk(get("/api/admin/finance/purchase-invoices/" + purchaseInvoiceId + "/matching", admin));
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/purchase-invoices/" + purchaseInvoiceId,
				frontendPurchaseInvoicePayload(procurementFixture.supplierId(), receiptLineId, "前端采购更新"), admin));
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/purchase-invoices/" + purchaseInvoiceId + "/cancel",
				Map.of("version", 1, "idempotencyKey", "PI-CANCEL-" + SEQUENCE.incrementAndGet()), admin));

		assertOk(get("/api/admin/finance/expenses/categories?page=1&pageSize=10", admin));
		assertOk(get("/api/admin/finance/expenses/source-candidates?page=1&pageSize=10", admin));
		long expenseId = data(createExpense(admin,
				frontendExpensePayload(procurementFixture.supplierId(), "前端费用草稿", "25.00"))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/expenses/" + expenseId,
				frontendExpensePayload(procurementFixture.supplierId(), "前端费用更新", "30.00"), admin));
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/expenses/" + expenseId + "/cancel",
				Map.of("version", 1, "idempotencyKey", "EXP-CANCEL-" + SEQUENCE.incrementAndGet()), admin));

		assertOk(get("/api/admin/finance/advance-receipts?page=1&pageSize=10", admin));
		long draftAdvanceId = data(createAdvanceReceipt(admin,
				frontendAdvanceFundPayload(salesFixture.customerId(), null, "前端预收草稿", "8.00"))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + draftAdvanceId,
				frontendAdvanceFundPayload(salesFixture.customerId(), null, "前端预收更新", "9.00"), admin));
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + draftAdvanceId + "/cancel",
				Map.of("version", 1, "idempotencyKey", "ADV-CANCEL-" + SEQUENCE.incrementAndGet()), admin));
		assertOk(get("/api/admin/finance/prepayments?page=1&pageSize=10", admin));
		long draftPrepaymentId = data(createPrepayment(admin,
				frontendAdvanceFundPayload(null, procurementFixture.supplierId(), "前端预付草稿", "7.00"))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/" + draftPrepaymentId,
				frontendAdvanceFundPayload(null, procurementFixture.supplierId(), "前端预付更新", "6.00"), admin));
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/" + draftPrepaymentId + "/cancel",
				Map.of("version", 1, "idempotencyKey", "PRE-CANCEL-" + SEQUENCE.incrementAndGet()), admin));

		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "10.00", "028 前端核销资金"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + advanceReceiptId + "/post",
				actionPayload(0, "ADV-FRONT-POST-"), admin));
		long receivableId = confirmedReceivable(admin, salesFixture, "028 前端核销目标", "1.000000", "10.000000");
		assertOk(get("/api/admin/finance/settlement-workbench/funds?direction=CUSTOMER&partnerId="
				+ salesFixture.customerId() + "&page=1&pageSize=10", admin));
		assertOk(get("/api/admin/finance/settlement-workbench/targets?direction=CUSTOMER&partnerId="
				+ salesFixture.customerId() + "&page=1&pageSize=10", admin));
		long allocationId = data(createSettlementAllocation(admin,
				frontendSettlementPayload(salesFixture.customerId(), advanceReceiptId, receivableId))).get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/settlement-workbench/allocations/" + allocationId
				+ "/cancel", Map.of("version", 0, "idempotencyKey", "ALC-CANCEL-" + SEQUENCE.incrementAndGet()),
				admin));

		long voucherShipmentId = createPostedShipment(salesFixture, "028 前端凭证来源", "1.000000", "12.000000");
		long voucherInvoiceId = data(createSalesInvoice(admin, salesInvoicePayload(voucherShipmentId, "SI-FV-")))
			.get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/" + voucherInvoiceId + "/confirm",
				actionPayload(0, "SI-FRONT-CONFIRM-"), admin));
		long draftId = data(exchange(HttpMethod.POST, "/api/admin/finance/voucher-drafts/generate",
				Map.of("sourceType", "SALES_INVOICE", "sourceId", voucherInvoiceId, "version", 1, "idempotencyKey",
						"VD-FRONT-" + SEQUENCE.incrementAndGet()), admin)).get("id").longValue();
		assertOk(get("/api/admin/finance/voucher-drafts?page=1&pageSize=10", admin));
		ResponseEntity<String> cancelledDraft = exchange(HttpMethod.PUT,
				"/api/admin/finance/voucher-drafts/" + draftId + "/cancel",
				Map.of("version", 0, "idempotencyKey", "VD-CANCEL-" + SEQUENCE.incrementAndGet()), admin);
		assertOk(cancelledDraft);
		assertThat(data(cancelledDraft).get("status").asText()).isEqualTo("CANCELLED");
	}

	@Test
	void salesInvoicesSupportPartialMultipleInvoicesPerSourceLineAndRejectCumulativeOverInvoice() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long shipmentId = createPostedShipment(fixture, "028 部分多次开票", "5.000000", "10.000000");
		long sourceLineId = salesShipmentLineId(shipmentId);

		ResponseEntity<String> first = createSalesInvoice(admin,
				partialSalesInvoicePayload(fixture.customerId(), sourceLineId, "2.000000", "SI-PART-A-"));
		assertOk(first);
		assertJsonDecimalString(data(first), "taxIncludedAmount", "20.00");

		ResponseEntity<String> second = createSalesInvoice(admin,
				partialSalesInvoicePayload(fixture.customerId(), sourceLineId, "2.000000", "SI-PART-B-"));
		assertOk(second);
		assertJsonDecimalString(data(second), "taxIncludedAmount", "20.00");

		assertError(createSalesInvoice(admin,
				partialSalesInvoicePayload(fixture.customerId(), sourceLineId, "2.000000", "SI-PART-C-")),
				HttpStatus.CONFLICT, "FINANCE_SOURCE_OVER_INVOICED");
	}

	@Test
	void partialSalesInvoicesExposeNetCandidatesAndReuseFullShipmentReceivable() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long shipmentId = createPostedShipment(fixture, "028 销售净候选", "5.000000", "10.000000");
		long sourceLineId = salesShipmentLineId(shipmentId);
		String shipmentNo = this.jdbcTemplate.queryForObject("select shipment_no from sal_sales_shipment where id = ?",
				String.class, shipmentId);
		createPostedShipment(fixture, "028 销售候选筛选干扰", "1.000000", "9.000000");

		ResponseEntity<String> first = createSalesInvoice(admin,
				partialSalesInvoicePayload(fixture.customerId(), sourceLineId, "2.000000", "SI-NET-A-"));
		assertOk(first);
		JsonNode candidates = data(get("/api/admin/finance/sales-invoices/candidates?sourceId=" + shipmentId
				+ "&page=1&pageSize=10", admin));
		assertThat(candidates.get("total").longValue()).isEqualTo(1);
		JsonNode candidate = findItemByLong(candidates.get("items"), "sourceLineId", sourceLineId);
		assertJsonDecimalString(candidate, "availableQuantity", "3.000000");
		assertJsonDecimalString(candidate, "invoiceQuantity", "3.000000");
		assertJsonDecimalString(candidate, "totalAmount", "30.00");
		assertThat(candidate.get("sourceNo").asText()).isEqualTo(shipmentNo);

		JsonNode confirmedFirst = data(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/"
				+ data(first).get("id").longValue() + "/confirm", actionPayload(0, "SI-NET-A-CONFIRM-"), admin));
		long receivableId = confirmedFirst.get("linkedReceivableId").longValue();
		assertDecimal("select total_amount from fin_receivable where id = ?", receivableId, "50.00");
		assertDecimal("select unreceived_amount from fin_receivable where id = ?", receivableId, "50.00");

		ResponseEntity<String> second = createSalesInvoice(admin,
				partialSalesInvoicePayload(fixture.customerId(), sourceLineId, "3.000000", "SI-NET-B-"));
		assertOk(second);
		JsonNode confirmedSecond = data(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/"
				+ data(second).get("id").longValue() + "/confirm", actionPayload(0, "SI-NET-B-CONFIRM-"), admin));
		assertThat(confirmedSecond.get("linkedReceivableId").longValue()).isEqualTo(receivableId);
		assertDecimal("select total_amount from fin_receivable where id = ?", receivableId, "50.00");
		assertThat(tableCount("fin_receivable", "source_id", shipmentId)).isOne();
	}

	@Test
	void purchasePartialInvoicesUseNetCandidatesAndConfirmAgainstCumulativeFacts() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long receiptId = createPostedPurchaseReceipt(fixture, "028 采购净候选", "5.000000", "8.000000");
		long sourceLineId = sourceLineId("PURCHASE_RECEIPT", receiptId);
		createPostedPurchaseReceipt(fixture, "028 采购候选筛选干扰", "1.000000", "7.000000");

		ResponseEntity<String> first = createPurchaseInvoice(admin,
				purchaseInvoicePayloadWithLines("STANDARD_PURCHASE", "PURCHASE_RECEIPT", receiptId,
						List.of(linePayload(sourceLineId, "2.000000", "8.000000"))));
		assertOk(first);
		assertThat(data(first).get("matchStatus").asText()).isEqualTo("MATCHED");
		JsonNode candidates = data(get("/api/admin/finance/purchase-invoices/candidates?sourceType=PURCHASE_RECEIPT"
				+ "&sourceId=" + receiptId + "&page=1&pageSize=10", admin));
		assertThat(candidates.get("total").longValue()).isEqualTo(1);
		JsonNode candidate = findItemByLong(candidates.get("items"), "sourceLineId", sourceLineId);
		assertJsonDecimalString(candidate, "availableQuantity", "3.000000");
		assertJsonDecimalString(candidate, "invoiceQuantity", "3.000000");
		assertJsonDecimalString(candidate, "totalAmount", "24.00");

		JsonNode confirmedFirst = data(exchange(HttpMethod.PUT, "/api/admin/finance/purchase-invoices/"
				+ data(first).get("id").longValue() + "/confirm", actionPayload(0, "PI-NET-A-CONFIRM-"), admin));
		long payableId = confirmedFirst.get("linkedPayableId").longValue();
		assertDecimal("select total_amount from fin_payable where id = ?", payableId, "40.00");
		assertDecimal("select unpaid_amount from fin_payable where id = ?", payableId, "40.00");

		ResponseEntity<String> second = createPurchaseInvoice(admin,
				purchaseInvoicePayloadWithLines("STANDARD_PURCHASE", "PURCHASE_RECEIPT", receiptId,
						List.of(linePayload(sourceLineId, "3.000000", "8.000000"))));
		assertOk(second);
		assertThat(data(second).get("matchStatus").asText()).isEqualTo("MATCHED");
		JsonNode confirmedSecond = data(exchange(HttpMethod.PUT, "/api/admin/finance/purchase-invoices/"
				+ data(second).get("id").longValue() + "/confirm", actionPayload(0, "PI-NET-B-CONFIRM-"), admin));
		assertThat(confirmedSecond.get("linkedPayableId").longValue()).isEqualTo(payableId);
		assertThat(tableCount("fin_payable", "source_id", receiptId)).isOne();

		assertError(createPurchaseInvoice(admin,
				purchaseInvoicePayloadWithLines("STANDARD_PURCHASE", "PURCHASE_RECEIPT", receiptId,
						List.of(linePayload(sourceLineId, "1.000000", "8.000000")))),
				HttpStatus.CONFLICT, "FINANCE_SOURCE_OVER_INVOICED");
	}

	@Test
	void purchaseInvoiceZeroToleranceComparesCompleteLineSetAndAllAmounts() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();

		long taxReceiptId = createPostedPurchaseReceipt(fixture, "028 三单税率差异", "1.000000", "8.000000");
		ResponseEntity<String> taxMismatchInvoice = createPurchaseInvoice(admin,
				purchaseInvoicePayloadWithLines("STANDARD_PURCHASE", "PURCHASE_RECEIPT", taxReceiptId,
						List.of(linePayloadWithAmounts(sourceLineId("PURCHASE_RECEIPT", taxReceiptId), "1.000000",
								"0.130000", "7.080000", "8.000000", "7.08", "0.92", "8.00"))));
		assertOk(taxMismatchInvoice);
		assertThat(data(taxMismatchInvoice).get("matchStatus").asText()).isEqualTo("EXCEPTION");
		assertThat(data(taxMismatchInvoice).get("matchDifferences").toString()).contains("TAX_RATE")
			.contains("TAX_EXCLUDED_AMOUNT")
			.contains("TAX_AMOUNT");
	}

	@Test
	void settlementAllocationEnforcesOwnershipVersionsAndV11AdjustedBalances() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		ProcurementFixture procurementFixture = procurementFixture();
		long projectId = createProject(salesFixture.customerId(), "028 核销项目");

		long projectExpenseId = data(createExpense(admin,
				expensePayload(procurementFixture.supplierId(), "PROJECT", projectId, "028 项目费用应付", "30.00")))
			.get("id")
			.longValue();
		JsonNode confirmedExpense = data(exchange(HttpMethod.PUT, "/api/admin/finance/expenses/" + projectExpenseId
				+ "/confirm", actionPayload(0, "EXP-PROJ-CONFIRM-"), admin));
		long projectPayableId = confirmedExpense.get("linkedPayableId").longValue();

		long publicPrepaymentId = data(createPrepayment(admin,
				prepaymentPayload(procurementFixture.supplierId(), "30.00", "028 公共预付款"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/" + publicPrepaymentId + "/post",
				actionPayload(0, "PRE-PUBLIC-POST-"), admin));
		assertError(createSettlementAllocation(admin,
				payableAllocationPayload(publicPrepaymentId, projectPayableId, "30.00", 1, 0)),
				HttpStatus.CONFLICT, "FINANCE_CROSS_PARTY_OR_PROJECT");

		long projectPrepaymentId = data(createPrepayment(admin,
				prepaymentPayload(procurementFixture.supplierId(), "PROJECT", projectId, "30.00", "028 项目预付款")))
			.get("id")
			.longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/" + projectPrepaymentId + "/post",
				actionPayload(0, "PRE-PROJ-POST-"), admin));
		assertError(createSettlementAllocation(admin,
				payableAllocationPayload(projectPrepaymentId, projectPayableId, "30.00", 99, 0)),
				HttpStatus.CONFLICT, "FINANCE_CONCURRENT_MODIFICATION");

		long receivableId = confirmedReceivable(admin, salesFixture, "028 V11 冲减余额", "1.000000", "100.000000");
		this.jdbcTemplate.update("""
				update fin_receivable
				set adjusted_amount = 20.00, unreceived_amount = 80.00
				where id = ?
				""", receivableId);
		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "80.00", "028 V11 后余额资金"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + advanceReceiptId + "/post",
				actionPayload(0, "ADV-V11-POST-"), admin));
		long allocationId = data(createSettlementAllocation(admin,
				receivableAllocationPayload(advanceReceiptId, receivableId, "80.00", 1, 1))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/settlement-workbench/allocations/" + allocationId
				+ "/post", actionPayload(0, "ALC-V11-POST-"), admin));
		assertDecimal("select received_amount from fin_receivable where id = ?", receivableId, "80.00");
		assertDecimal("select adjusted_amount from fin_receivable where id = ?", receivableId, "20.00");
		assertDecimal("select unreceived_amount from fin_receivable where id = ?", receivableId, "0.00");
	}

	@Test
	void stage028WriteActionsRequireVersionAndStableIdempotencyFingerprint() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long missingMetadataShipmentId = createPostedShipment(fixture, "028 幂等缺参", "1.000000", "10.000000");
		long missingMetadataLineId = salesShipmentLineId(missingMetadataShipmentId);
		Map<String, Object> missingVersion = partialSalesInvoicePayload(fixture.customerId(), missingMetadataLineId,
				"1.000000", "SI-META-MISS-");
		missingVersion.remove("version");
		assertError(createSalesInvoice(admin, missingVersion), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

		long shipmentId = createPostedShipment(fixture, "028 幂等冲突", "2.000000", "10.000000");
		long sourceLineId = salesShipmentLineId(shipmentId);
		Map<String, Object> payload = partialSalesInvoicePayload(fixture.customerId(), sourceLineId, "1.000000",
				"SI-IDEMP-");
		ResponseEntity<String> created = createSalesInvoice(admin, payload);
		assertOk(created);
		ResponseEntity<String> replayed = createSalesInvoice(admin, payload);
		assertOk(replayed);
		assertThat(data(replayed).get("id").longValue()).isEqualTo(data(created).get("id").longValue());

		Map<String, Object> conflictPayload = new LinkedHashMap<>(payload);
		conflictPayload.put("remark", "同键不同载荷");
		assertError(createSalesInvoice(admin, conflictPayload), HttpStatus.CONFLICT, "FINANCE_IDEMPOTENCY_CONFLICT");
		assertError(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/" + data(created).get("id").longValue()
				+ "/confirm", Map.of("idempotencyKey", "SI-CONFIRM-MISSING-VERSION"), admin),
				HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
	}

	@Test
	void updateActionsReplaySameIdempotencyKeyAndRejectFingerprintConflict() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		ProcurementFixture procurementFixture = procurementFixture();

		long shipmentId = createPostedShipment(salesFixture, "028 更新幂等销售", "2.000000", "10.000000");
		long shipmentLineId = salesShipmentLineId(shipmentId);
		long salesInvoiceId = data(createSalesInvoice(admin,
				partialSalesInvoicePayload(salesFixture.customerId(), shipmentLineId, "1.000000", "SI-UPD-")))
			.get("id")
			.longValue();
		Map<String, Object> salesUpdate = partialSalesInvoicePayload(salesFixture.customerId(), shipmentLineId,
				"1.000000", "SI-UPD-REPLAY-");
		salesUpdate.put("idempotencyKey", "SI-UPD-STABLE-" + SEQUENCE.incrementAndGet());
		ResponseEntity<String> updatedSalesResponse = exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/"
				+ salesInvoiceId, salesUpdate, admin);
		assertOk(updatedSalesResponse);
		ResponseEntity<String> replayedSalesResponse = exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/"
				+ salesInvoiceId, salesUpdate, admin);
		assertOk(replayedSalesResponse);
		JsonNode updatedSales = data(updatedSalesResponse);
		JsonNode replayedSales = data(replayedSalesResponse);
		assertThat(replayedSales.get("version").longValue()).isEqualTo(updatedSales.get("version").longValue());
		Map<String, Object> salesConflict = new LinkedHashMap<>(salesUpdate);
		salesConflict.put("remark", "同键不同销售更新");
		assertError(exchange(HttpMethod.PUT, "/api/admin/finance/sales-invoices/" + salesInvoiceId, salesConflict,
				admin), HttpStatus.CONFLICT, "FINANCE_IDEMPOTENCY_CONFLICT");

		long receiptId = createPostedPurchaseReceipt(procurementFixture, "028 更新幂等采购", "1.000000", "8.000000");
		long receiptLineId = sourceLineId("PURCHASE_RECEIPT", receiptId);
		long purchaseInvoiceId = data(createPurchaseInvoice(admin,
				purchaseInvoicePayloadWithLines("STANDARD_PURCHASE", "PURCHASE_RECEIPT", receiptId,
						List.of(linePayload(receiptLineId, "1.000000", "8.000000"))))).get("id").longValue();
		Map<String, Object> purchaseUpdate = purchaseInvoicePayloadWithLines("STANDARD_PURCHASE", "PURCHASE_RECEIPT",
				receiptId, List.of(linePayload(receiptLineId, "1.000000", "8.000000")));
		purchaseUpdate.put("idempotencyKey", "PI-UPD-STABLE-" + SEQUENCE.incrementAndGet());
		ResponseEntity<String> updatedPurchaseResponse = exchange(HttpMethod.PUT, "/api/admin/finance/purchase-invoices/"
				+ purchaseInvoiceId, purchaseUpdate, admin);
		assertOk(updatedPurchaseResponse);
		ResponseEntity<String> replayedPurchaseResponse = exchange(HttpMethod.PUT, "/api/admin/finance/purchase-invoices/"
				+ purchaseInvoiceId, purchaseUpdate, admin);
		assertOk(replayedPurchaseResponse);
		JsonNode updatedPurchase = data(updatedPurchaseResponse);
		JsonNode replayedPurchase = data(replayedPurchaseResponse);
		assertThat(replayedPurchase.get("version").longValue()).isEqualTo(updatedPurchase.get("version").longValue());

		long expenseId = data(createExpense(admin,
				expensePayload(procurementFixture.supplierId(), "PUBLIC", null, "028 更新幂等费用", "18.00"))).get("id")
			.longValue();
		Map<String, Object> expenseUpdate = expensePayload(procurementFixture.supplierId(), "PUBLIC", null,
				"028 更新幂等费用改", "18.00");
		expenseUpdate.put("idempotencyKey", "EXP-UPD-STABLE-" + SEQUENCE.incrementAndGet());
		ResponseEntity<String> updatedExpenseResponse = exchange(HttpMethod.PUT, "/api/admin/finance/expenses/"
				+ expenseId, expenseUpdate, admin);
		assertOk(updatedExpenseResponse);
		ResponseEntity<String> replayedExpenseResponse = exchange(HttpMethod.PUT, "/api/admin/finance/expenses/"
				+ expenseId, expenseUpdate, admin);
		assertOk(replayedExpenseResponse);
		JsonNode updatedExpense = data(updatedExpenseResponse);
		JsonNode replayedExpense = data(replayedExpenseResponse);
		assertThat(replayedExpense.get("version").longValue()).isEqualTo(updatedExpense.get("version").longValue());

		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "12.00", "028 更新幂等预收"))).get("id").longValue();
		Map<String, Object> advanceUpdate = advanceReceiptPayload(salesFixture.customerId(), "12.00", "028 预收更新");
		advanceUpdate.put("idempotencyKey", "ADV-UPD-STABLE-" + SEQUENCE.incrementAndGet());
		ResponseEntity<String> updatedAdvanceResponse = exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/"
				+ advanceReceiptId, advanceUpdate, admin);
		assertOk(updatedAdvanceResponse);
		ResponseEntity<String> replayedAdvanceResponse = exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/"
				+ advanceReceiptId, advanceUpdate, admin);
		assertOk(replayedAdvanceResponse);
		JsonNode updatedAdvance = data(updatedAdvanceResponse);
		JsonNode replayedAdvance = data(replayedAdvanceResponse);
		assertThat(replayedAdvance.get("version").longValue()).isEqualTo(updatedAdvance.get("version").longValue());

		long prepaymentId = data(createPrepayment(admin,
				prepaymentPayload(procurementFixture.supplierId(), "13.00", "028 更新幂等预付"))).get("id").longValue();
		Map<String, Object> prepaymentUpdate = prepaymentPayload(procurementFixture.supplierId(), "13.00", "028 预付更新");
		prepaymentUpdate.put("idempotencyKey", "PRE-UPD-STABLE-" + SEQUENCE.incrementAndGet());
		ResponseEntity<String> updatedPrepaymentResponse = exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/"
				+ prepaymentId, prepaymentUpdate, admin);
		assertOk(updatedPrepaymentResponse);
		ResponseEntity<String> replayedPrepaymentResponse = exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/"
				+ prepaymentId, prepaymentUpdate, admin);
		assertOk(replayedPrepaymentResponse);
		JsonNode updatedPrepayment = data(updatedPrepaymentResponse);
		JsonNode replayedPrepayment = data(replayedPrepaymentResponse);
		assertThat(replayedPrepayment.get("version").longValue()).isEqualTo(updatedPrepayment.get("version").longValue());
	}

	@Test
	void expensesValidatePurchaseAndOutsourcingSourcesOnServiceSide() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		long receiptId = createPostedPurchaseReceipt(fixture, "028 费用来源", "1.000000", "20.000000");

		Map<String, Object> tampered = expensePayloadWithSource(fixture.supplierId(), "PUBLIC", null,
				"PURCHASE_RECEIPT", receiptId, "FAKE-SOURCE", "999.00");
		assertError(createExpense(admin, tampered), HttpStatus.CONFLICT, "FINANCE_SOURCE_MISMATCH");

		Map<String, Object> trusted = expensePayloadWithSource(fixture.supplierId(), "PUBLIC", null,
				"PURCHASE_RECEIPT", receiptId, "IGNORED-BY-SERVICE", "20.00");
		ResponseEntity<String> created = createExpense(admin, trusted);
		assertOk(created);
		JsonNode line = data(created).get("lines").get(0);
		assertThat(line.get("sourceNo").asText()).startsWith("028-PR-");
		assertJsonDecimalString(line, "taxIncludedAmount", "20.00");
	}

	@Test
	void legacyFinanceViewsIncludeStage028PayablesAndDeduplicateMultiTargetCash() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		ProcurementFixture procurementFixture = procurementFixture();

		long expenseId = data(createExpense(admin,
				expensePayload(procurementFixture.supplierId(), "PUBLIC", null, "028 旧应付可见", "18.00")))
			.get("id")
			.longValue();
		long payableId = data(exchange(HttpMethod.PUT, "/api/admin/finance/expenses/" + expenseId + "/confirm",
				actionPayload(0, "EXP-LEGACY-CONFIRM-"), admin)).get("linkedPayableId").longValue();
		JsonNode payables = data(get("/api/admin/finance/payables?supplierId=" + procurementFixture.supplierId(),
				admin)).get("items");
		assertThat(payables.toString()).contains("\"id\":" + payableId);

		long firstPayable = confirmedPayable(admin, procurementFixture, "028 旧付款去重一", "1.000000", "11.000000");
		long secondPayable = confirmedPayable(admin, procurementFixture, "028 旧付款去重二", "1.000000", "19.000000");
		long prepaymentId = data(createPrepayment(admin,
				prepaymentPayload(procurementFixture.supplierId(), "30.00", "028 旧付款去重资金"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/prepayments/" + prepaymentId + "/post",
				actionPayload(0, "PRE-LEGACY-POST-"), admin));
		long allocationId = data(createSettlementAllocation(admin,
				payableAllocationPayload(prepaymentId, firstPayable, secondPayable))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/settlement-workbench/allocations/" + allocationId
				+ "/post", actionPayload(0, "ALC-LEGACY-POST-"), admin));
		JsonNode payments = data(get("/api/admin/finance/payments?supplierId=" + procurementFixture.supplierId(),
				admin)).get("items");
		String paymentNo = this.jdbcTemplate.queryForObject("select payment_no from fin_payment where id = ?",
				String.class, prepaymentId);
		int matchingPaymentRows = 0;
		for (JsonNode payment : payments) {
			if (paymentNo.equals(payment.get("paymentNo").asText())) {
				matchingPaymentRows++;
			}
		}
		assertThat(matchingPaymentRows).isOne();
	}

	@Test
	void historicalPostedCashCanGenerateVoucherWithoutCreatingAdvanceBalance() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture fixture = fixture();
		long receiptId = insertLegacyPostedReceipt(fixture.customerId(), "028-HIST-RC-", "17.00", 4);

		JsonNode advances = data(get("/api/admin/finance/advance-receipts?keyword=028-HIST-RC-&page=1&pageSize=10",
				admin));
		assertThat(advances.get("items")).isEmpty();
		ResponseEntity<String> generated = exchange(HttpMethod.POST, "/api/admin/finance/voucher-drafts/generate",
				voucherGeneratePayload("RECEIPT", receiptId, 4, "VD-HIST-RC-"), admin);
		assertOk(generated);
		JsonNode draft = data(generated);
		assertThat(draft.get("sourceNo").asText()).startsWith("028-HIST-RC-");
		assertThat(draft.get("sourceSummary").asText()).contains("收款");
		assertThat(draft.get("generationVersion").longValue()).isEqualTo(4);
		assertThat(tableCount("fin_receipt_balance", "receipt_id", receiptId)).isZero();
	}

	@Test
	void settlementAllocationDetailReturnsActionTruthAndSummaryFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		long receivableId = confirmedReceivable(admin, salesFixture, "028 核销详情", "1.000000", "10.000000");
		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "10.00", "028 核销详情资金"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + advanceReceiptId + "/post",
				actionPayload(0, "ADV-DETAIL-POST-"), admin));
		long allocationId = data(createSettlementAllocation(admin,
				receivableAllocationPayload(advanceReceiptId, receivableId, "10.00", 1, 1))).get("id").longValue();

		JsonNode detail = data(get("/api/admin/finance/settlement-workbench/allocations/" + allocationId, admin));
		assertThat(detail.has("allowedActions")).isTrue();
		assertThat(detail.get("allowedActions").toString()).contains("POST").contains("CANCEL");
		assertThat(detail.has("restrictedReasons")).isTrue();
		assertThat(detail.get("restrictedReasons").isArray()).isTrue();
		assertThat(detail.get("fundNo").asText()).startsWith("ADR-");
		assertThat(detail.get("partnerName").asText()).contains("028 客户");
		assertThat(detail.get("ownershipType").asText()).isEqualTo("PUBLIC");
		assertThat(detail.get("projectName").isNull()).isTrue();
		assertThat(detail.get("summary").asText()).contains("ADR-").contains("1 个目标");
		assertThat(detail.get("version").longValue()).isEqualTo(0);
	}

	@Test
	void settlementAllocationListSupportsVoucherSourceCandidates() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		long receivableId = confirmedReceivable(admin, salesFixture, "028 核销候选", "1.000000", "10.000000");
		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "10.00", "028 核销候选资金"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + advanceReceiptId + "/post",
				actionPayload(0, "ADV-ALC-LIST-POST-"), admin));
		long allocationId = data(createSettlementAllocation(admin,
				receivableAllocationPayload(advanceReceiptId, receivableId, "10.00", 1, 1))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/settlement-workbench/allocations/" + allocationId
				+ "/post", actionPayload(0, "ALC-LIST-POST-"), admin));

		ResponseEntity<String> listResponse = get("/api/admin/finance/settlement-workbench/allocations"
				+ "?direction=CUSTOMER&status=POSTED&keyword=ALC-&page=1&pageSize=10", admin);
		assertOk(listResponse);
		JsonNode page = data(listResponse);
		assertThat(page.get("total").longValue()).isGreaterThanOrEqualTo(1);
		JsonNode item = findItemByLong(page.get("items"), "id", allocationId);
		assertThat(item.get("allocationNo").asText()).startsWith("ALC-");
		assertThat(item.get("status").asText()).isEqualTo("POSTED");
		assertThat(item.get("version").longValue()).isEqualTo(1);
		assertJsonDecimalString(item, "totalAmount", "10.00");
		assertJsonDecimalString(item, "amount", "10.00");
		assertThat(item.get("partnerName").asText()).contains("028 客户");
		assertThat(item.get("fundNo").asText()).startsWith("ADR-");
		assertThat(item.get("ownershipType").asText()).isEqualTo("PUBLIC");
		assertThat(item.get("projectName").isNull()).isTrue();
		assertThat(item.get("summary").asText()).contains("ADR-").contains("1 个目标");
	}

	@Test
	void voucherDraftsCoverPostedCashAndSettlementSourcesWithStableBoundaryFields() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		SalesFixture salesFixture = fixture();
		long receivableId = confirmedReceivable(admin, salesFixture, "028 凭证资金来源", "1.000000", "10.000000");
		long advanceReceiptId = data(createAdvanceReceipt(admin,
				advanceReceiptPayload(salesFixture.customerId(), "10.00", "028 凭证收款来源"))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/advance-receipts/" + advanceReceiptId + "/post",
				actionPayload(0, "ADV-VD-POST-"), admin));
		this.jdbcTemplate.update("update fin_receipt set version = 7 where id = ?", advanceReceiptId);
		long allocationId = data(createSettlementAllocation(admin,
				receivableAllocationPayload(advanceReceiptId, receivableId, "10.00", 7, 1))).get("id").longValue();
		assertOk(exchange(HttpMethod.PUT, "/api/admin/finance/settlement-workbench/allocations/" + allocationId
				+ "/post", actionPayload(0, "ALC-VD-POST-"), admin));

		ResponseEntity<String> receiptDraft = exchange(HttpMethod.POST, "/api/admin/finance/voucher-drafts/generate",
				voucherGeneratePayload("RECEIPT", advanceReceiptId, 7, "VD-RECEIPT-"), admin);
		assertOk(receiptDraft);
		assertThat(data(receiptDraft).get("sourceNo").asText()).startsWith("ADR-");
		assertThat(data(receiptDraft).get("sourceSummary").asText()).contains("收款");
		assertThat(data(receiptDraft).get("generationVersion").longValue()).isEqualTo(7);
		assertThat(data(receiptDraft).get("allowedActions").toString()).contains("READY");
		assertJsonDecimalString(data(receiptDraft), "debitAmount", "10.00");
		assertJsonDecimalString(data(receiptDraft), "creditAmount", "10.00");
		assertJsonDecimalString(data(receiptDraft), "debitTotal", "10.00");
		assertJsonDecimalString(data(receiptDraft), "creditTotal", "10.00");
		assertThat(data(receiptDraft).get("balanced").asBoolean()).isTrue();
		assertJsonDecimalString(data(receiptDraft).get("lines").get(0), "amount", "10.00");

		ResponseEntity<String> allocationDraft = exchange(HttpMethod.POST, "/api/admin/finance/voucher-drafts/generate",
				voucherGeneratePayload("SETTLEMENT_ALLOCATION", allocationId, 1, "VD-ALC-"), admin);
		assertOk(allocationDraft);
		assertThat(data(allocationDraft).get("sourceNo").asText()).startsWith("ALC-");
		assertThat(data(allocationDraft).get("sourceSummary").asText()).contains("核销");
		JsonNode filtered = data(get("/api/admin/finance/voucher-drafts?sourceType=RECEIPT&sourceId="
				+ advanceReceiptId + "&page=1&pageSize=10", admin));
		assertThat(filtered.get("total").longValue()).isEqualTo(1);
		assertThat(filtered.get("items").get(0).get("sourceType").asText()).isEqualTo("RECEIPT");
	}

	@Test
	void crossSourceCandidatesAreMergedThenSortedBeforePagination() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		ProcurementFixture fixture = procurementFixture();
		createPostedPurchaseReceipt(fixture, "028 候选采购", "1.000000", "5.000000");
		createPostedOutsourcingReceipt(fixture, "028 候选外协", "1.000000", "6.000000");

		JsonNode purchasePage2 = data(get("/api/admin/finance/purchase-invoices/candidates?page=2&pageSize=1",
				admin));
		assertThat(purchasePage2.get("total").longValue()).isGreaterThanOrEqualTo(2);
		assertThat(purchasePage2.get("items")).hasSize(1);
		assertThat(purchasePage2.get("items").get(0).get("sourceType").asText())
			.isIn("PURCHASE_RECEIPT", "OUTSOURCING_RECEIPT");

		JsonNode expensePage2 = data(get("/api/admin/finance/expenses/source-candidates?page=2&pageSize=1", admin));
		assertThat(expensePage2.get("total").longValue()).isGreaterThanOrEqualTo(2);
		assertThat(expensePage2.get("items")).hasSize(1);
	}

	private ResponseEntity<String> createSalesInvoice(AuthenticatedSession session, Map<String, Object> payload) {
		return exchange(HttpMethod.POST, "/api/admin/finance/sales-invoices", payload, session);
	}

	private ResponseEntity<String> createPurchaseInvoice(AuthenticatedSession session, Map<String, Object> payload) {
		return exchange(HttpMethod.POST, "/api/admin/finance/purchase-invoices", payload, session);
	}

	private ResponseEntity<String> createExpense(AuthenticatedSession session, Map<String, Object> payload) {
		return exchange(HttpMethod.POST, "/api/admin/finance/expenses", payload, session);
	}

	private ResponseEntity<String> createAdvanceReceipt(AuthenticatedSession session, Map<String, Object> payload) {
		return exchange(HttpMethod.POST, "/api/admin/finance/advance-receipts", payload, session);
	}

	private ResponseEntity<String> createPrepayment(AuthenticatedSession session, Map<String, Object> payload) {
		return exchange(HttpMethod.POST, "/api/admin/finance/prepayments", payload, session);
	}

	private ResponseEntity<String> createSettlementAllocation(AuthenticatedSession session,
			Map<String, Object> payload) {
		return exchange(HttpMethod.POST, "/api/admin/finance/settlement-workbench/allocations", payload, session);
	}

	private Map<String, Object> salesInvoicePayload(long shipmentId, String externalPrefix) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceType", "SALES_SHIPMENT");
		payload.put("sourceId", shipmentId);
		payload.put("invoiceDate", LocalDate.now().toString());
		payload.put("dueDate", LocalDate.now().plusDays(30).toString());
		payload.put("invoiceType", "SPECIAL_VAT");
		payload.put("externalInvoiceNo", externalPrefix + SEQUENCE.incrementAndGet());
		payload.put("idempotencyKey", externalPrefix + "IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", "028 销售发票");
		return payload;
	}

	private Map<String, Object> frontendSalesInvoicePayload(long customerId, long sourceLineId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("ownershipType", "PUBLIC");
		payload.put("invoiceDate", LocalDate.now().toString());
		payload.put("invoiceType", "NONE");
		payload.put("externalInvoiceNo", "SI-FRONT-" + SEQUENCE.incrementAndGet());
		payload.put("idempotencyKey", "SI-FRONT-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", remark);
		payload.put("sourceLines", List.of(Map.of("sourceLineId", sourceLineId, "invoiceQuantity", "1.000000")));
		return payload;
	}

	private Map<String, Object> partialSalesInvoicePayload(long customerId, long sourceLineId, String quantity,
			String keyPrefix) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("ownershipType", "PUBLIC");
		payload.put("invoiceDate", LocalDate.now().toString());
		payload.put("invoiceType", "NONE");
		payload.put("externalInvoiceNo", keyPrefix + SEQUENCE.incrementAndGet());
		payload.put("idempotencyKey", keyPrefix + "IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", "028 部分开票");
		payload.put("sourceLines", List.of(Map.of("sourceLineId", sourceLineId, "invoiceQuantity", quantity)));
		return payload;
	}

	private Map<String, Object> purchaseInvoicePayload(String settlementKind, String sourceType, long sourceId,
			String quantity, String unitPrice) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("settlementKind", settlementKind);
		payload.put("sourceType", sourceType);
		payload.put("sourceId", sourceId);
		payload.put("invoiceDate", LocalDate.now().toString());
		payload.put("dueDate", LocalDate.now().plusDays(30).toString());
		payload.put("invoiceType", "SPECIAL_VAT");
		payload.put("supplierInvoiceNo", "PI-EXT-" + SEQUENCE.incrementAndGet());
		payload.put("idempotencyKey", "PI-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", "028 采购发票");
		payload.put("lines", List.of(linePayload(sourceLineId(sourceType, sourceId), quantity, unitPrice)));
		return payload;
	}

	private Map<String, Object> purchaseInvoicePayloadWithLines(String settlementKind, String sourceType, long sourceId,
			List<Map<String, Object>> lines) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("settlementKind", settlementKind);
		payload.put("sourceType", sourceType);
		payload.put("sourceId", sourceId);
		payload.put("invoiceDate", LocalDate.now().toString());
		payload.put("dueDate", LocalDate.now().plusDays(30).toString());
		payload.put("invoiceType", "SPECIAL_VAT");
		payload.put("supplierInvoiceNo", "PI-ZERO-" + SEQUENCE.incrementAndGet());
		payload.put("idempotencyKey", "PI-ZERO-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", "028 三单零容差");
		payload.put("lines", lines);
		return payload;
	}

	private Map<String, Object> frontendPurchaseInvoicePayload(long supplierId, long receiptLineId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("sourceType", "PURCHASE_RECEIPT");
		payload.put("ownershipType", "PUBLIC");
		payload.put("invoiceDate", LocalDate.now().toString());
		payload.put("invoiceType", "NONE");
		payload.put("externalInvoiceNo", "PI-FRONT-" + SEQUENCE.incrementAndGet());
		payload.put("idempotencyKey", "PI-FRONT-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", remark);
		payload.put("sourceLines", List.of(Map.of("receiptLineId", receiptLineId, "invoiceQuantity", "1.000000",
				"taxRate", "0.000000")));
		return payload;
	}

	private Map<String, Object> linePayload(long sourceLineId, String quantity, String unitPrice) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue).setScale(2);
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("sourceLineId", sourceLineId);
		line.put("quantity", quantity);
		line.put("taxRate", "0.000000");
		line.put("taxExcludedUnitPrice", unitPrice);
		line.put("taxIncludedUnitPrice", unitPrice);
		line.put("taxExcludedAmount", amount.toPlainString());
		line.put("taxAmount", "0.00");
		line.put("taxIncludedAmount", amount.toPlainString());
		return line;
	}

	private Map<String, Object> linePayloadWithAmounts(long sourceLineId, String quantity, String taxRate,
			String taxExcludedUnitPrice, String taxIncludedUnitPrice, String taxExcludedAmount, String taxAmount,
			String taxIncludedAmount) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("sourceLineId", sourceLineId);
		line.put("quantity", quantity);
		line.put("taxRate", taxRate);
		line.put("taxExcludedUnitPrice", taxExcludedUnitPrice);
		line.put("taxIncludedUnitPrice", taxIncludedUnitPrice);
		line.put("taxExcludedAmount", taxExcludedAmount);
		line.put("taxAmount", taxAmount);
		line.put("taxIncludedAmount", taxIncludedAmount);
		return line;
	}

	private Map<String, Object> expensePayload(long supplierId, String ownershipType, Long projectId, String remark,
			String amount) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("ownershipType", ownershipType);
		payload.put("projectId", projectId);
		payload.put("expenseDate", LocalDate.now().toString());
		payload.put("dueDate", LocalDate.now().plusDays(30).toString());
		payload.put("invoiceType", "GENERAL_VAT");
		payload.put("idempotencyKey", "EXP-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", remark);
		payload.put("lines", List.of(Map.of("expenseCategory", "SERVICE", "description", remark, "taxRate",
				"0.000000", "taxExcludedAmount", amount, "taxAmount", "0.00", "taxIncludedAmount", amount)));
		return payload;
	}

	private Map<String, Object> expensePayloadWithSource(long supplierId, String ownershipType, Long projectId,
			String sourceType, long sourceId, String sourceNo, String amount) {
		Map<String, Object> payload = expensePayload(supplierId, ownershipType, projectId, "028 来源费用", amount);
		payload.put("lines", List.of(Map.of("expenseCategory", "SERVICE", "description", "028 来源费用",
				"sourceType", sourceType, "sourceId", sourceId, "sourceNo", sourceNo, "taxRate", "0.000000",
				"taxExcludedAmount", amount, "taxAmount", "0.00", "taxIncludedAmount", amount)));
		return payload;
	}

	private Map<String, Object> frontendExpensePayload(long supplierId, String remark, String amount) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("ownershipType", "PUBLIC");
		payload.put("categoryId", 1);
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("idempotencyKey", "EXP-FRONT-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", remark);
		payload.put("lines", List.of(Map.of("categoryId", 1, "pretaxAmount", amount, "taxRate", "0.000000",
				"taxAmount", "0.00", "totalAmount", amount)));
		return payload;
	}

	private Map<String, Object> advanceReceiptPayload(long customerId, String amount, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customerId", customerId);
		payload.put("ownershipType", "PUBLIC");
		payload.put("receiptDate", LocalDate.now().toString());
		payload.put("method", "BANK_TRANSFER");
		payload.put("amount", amount);
		payload.put("idempotencyKey", "ADV-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", remark);
		return payload;
	}

	private Map<String, Object> prepaymentPayload(long supplierId, String amount, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("supplierId", supplierId);
		payload.put("ownershipType", "PUBLIC");
		payload.put("paymentDate", LocalDate.now().toString());
		payload.put("method", "BANK_TRANSFER");
		payload.put("amount", amount);
		payload.put("idempotencyKey", "PRE-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", remark);
		return payload;
	}

	private Map<String, Object> prepaymentPayload(long supplierId, String ownershipType, Long projectId, String amount,
			String remark) {
		Map<String, Object> payload = prepaymentPayload(supplierId, amount, remark);
		payload.put("ownershipType", ownershipType);
		payload.put("projectId", projectId);
		return payload;
	}

	private Map<String, Object> frontendAdvanceFundPayload(Long customerId, Long supplierId, String remark,
			String amount) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("partnerId", customerId == null ? supplierId : customerId);
		payload.put("customerId", customerId);
		payload.put("supplierId", supplierId);
		payload.put("ownershipType", "PUBLIC");
		payload.put("businessDate", LocalDate.now().toString());
		payload.put("method", "BANK_TRANSFER");
		payload.put("amount", amount);
		payload.put("allocations", List.of());
		payload.put("idempotencyKey", "FUND-FRONT-IDEMP-" + SEQUENCE.incrementAndGet());
		payload.put("version", 0);
		payload.put("remark", remark);
		return payload;
	}

	private Map<String, Object> receivableAllocationPayload(long receiptId, long firstReceivable,
			long secondReceivable) {
		return Map.of("settlementSide", "RECEIVABLE", "cashSourceType", "RECEIPT", "cashSourceId", receiptId,
				"businessDate", LocalDate.now().toString(), "idempotencyKey",
				"ALR-IDEMP-" + SEQUENCE.incrementAndGet(), "version", 0, "funds",
				List.of(Map.of("fundType", "ADVANCE_RECEIPT", "fundId", receiptId, "version", 1, "amount",
						"30.00")),
				"lines",
				List.of(Map.of("targetType", "RECEIVABLE", "targetId", firstReceivable, "amount", "10.00"),
						Map.of("targetType", "RECEIVABLE", "targetId", secondReceivable, "amount", "20.00")),
				"targets",
				List.of(Map.of("targetType", "RECEIVABLE", "targetId", firstReceivable, "version", 1, "amount",
						"10.00"),
						Map.of("targetType", "RECEIVABLE", "targetId", secondReceivable, "version", 1,
								"amount", "20.00")));
	}

	private Map<String, Object> receivableAllocationPayload(long receiptId, long receivableId, String amount,
			long receiptVersion, long receivableVersion) {
		return Map.of("settlementSide", "RECEIVABLE", "cashSourceType", "RECEIPT", "cashSourceId", receiptId,
				"businessDate", LocalDate.now().toString(), "idempotencyKey",
				"ALR-IDEMP-" + SEQUENCE.incrementAndGet(), "version", 0, "funds",
				List.of(Map.of("fundType", "ADVANCE_RECEIPT", "fundId", receiptId, "version", receiptVersion,
						"amount", amount)),
				"lines", List.of(Map.of("targetType", "RECEIVABLE", "targetId", receivableId, "amount", amount)),
				"targets", List.of(Map.of("targetType", "RECEIVABLE", "targetId", receivableId, "version",
						receivableVersion, "amount", amount)));
	}

	private Map<String, Object> payableAllocationPayload(long paymentId, long firstPayable, long secondPayable) {
		return Map.of("settlementSide", "PAYABLE", "cashSourceType", "PAYMENT", "cashSourceId", paymentId,
				"businessDate", LocalDate.now().toString(), "idempotencyKey",
				"ALP-IDEMP-" + SEQUENCE.incrementAndGet(), "version", 0, "funds",
				List.of(Map.of("fundType", "PREPAYMENT", "fundId", paymentId, "version", 1, "amount", "30.00")),
				"lines",
				List.of(Map.of("targetType", "PAYABLE", "targetId", firstPayable, "amount", "11.00"),
						Map.of("targetType", "PAYABLE", "targetId", secondPayable, "amount", "19.00")),
				"targets",
				List.of(Map.of("targetType", "PAYABLE", "targetId", firstPayable, "version", 1, "amount",
						"11.00"),
						Map.of("targetType", "PAYABLE", "targetId", secondPayable, "version", 1,
								"amount", "19.00")));
	}

	private Map<String, Object> payableAllocationPayload(long paymentId, long payableId, String amount,
			long paymentVersion, long payableVersion) {
		return Map.of("settlementSide", "PAYABLE", "cashSourceType", "PAYMENT", "cashSourceId", paymentId,
				"businessDate", LocalDate.now().toString(), "idempotencyKey",
				"ALP-IDEMP-" + SEQUENCE.incrementAndGet(), "version", 0, "funds",
				List.of(Map.of("fundType", "PREPAYMENT", "fundId", paymentId, "version", paymentVersion, "amount",
						amount)),
				"lines", List.of(Map.of("targetType", "PAYABLE", "targetId", payableId, "amount", amount)),
				"targets", List.of(Map.of("targetType", "PAYABLE", "targetId", payableId, "version",
						payableVersion, "amount", amount)));
	}

	private Map<String, Object> frontendSettlementPayload(long customerId, long receiptId, long receivableId) {
		return Map.of("direction", "CUSTOMER", "partnerId", customerId, "ownershipType", "PUBLIC", "funds",
				List.of(Map.of("fundType", "ADVANCE_RECEIPT", "fundId", receiptId, "version", 1, "amount",
						"10.00")),
				"targets", List.of(Map.of("targetType", "RECEIVABLE", "targetId", receivableId, "version", 1,
						"amount", "10.00")),
				"idempotencyKey", "ALC-FRONT-IDEMP-" + SEQUENCE.incrementAndGet(), "version", 0);
	}

	private Map<String, Object> actionPayload(long version, String keyPrefix) {
		return Map.of("version", version, "idempotencyKey", keyPrefix + SEQUENCE.incrementAndGet());
	}

	private Map<String, Object> voucherGeneratePayload(String sourceType, long sourceId, long version,
			String keyPrefix) {
		return Map.of("sourceType", sourceType, "sourceId", sourceId, "version", version, "idempotencyKey",
				keyPrefix + SEQUENCE.incrementAndGet());
	}

	private JsonNode findItemByLong(JsonNode items, String field, long expected) {
		for (JsonNode item : items) {
			if (item.has(field) && item.get(field).longValue() == expected) {
				return item;
			}
		}
		throw new AssertionError("未找到 " + field + "=" + expected + " 的候选行：" + items);
	}

	private long confirmedReceivable(AuthenticatedSession admin, SalesFixture fixture, String remark, String quantity,
			String unitPrice) throws Exception {
		long shipmentId = createPostedShipment(fixture, remark, quantity, unitPrice);
		long receivableId = data(createReceivable(admin, shipmentId, remark)).get("id").longValue();
		assertOk(confirmReceivable(admin, receivableId));
		return receivableId;
	}

	private long confirmedPayable(AuthenticatedSession admin, ProcurementFixture fixture, String remark,
			String quantity, String unitPrice) throws Exception {
		long receiptId = createPostedPurchaseReceipt(fixture, remark, quantity, unitPrice);
		long payableId = data(createPayable(admin, receiptId, remark)).get("id").longValue();
		assertOk(confirmPayable(admin, payableId));
		return payableId;
	}

	private long createPostedShipment(SalesFixture fixture, String remark, String quantity, String unitPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-SO-" + suffix, fixture.customerId(), LocalDate.now(),
				LocalDate.now().plusDays(3), remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 0, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.materialId(), fixture.unitId(), quantityValue, quantityValue,
				unitPriceValue, LocalDate.now().plusDays(3), remark, unitPriceValue, unitPriceValue, amount, amount);
		long shipmentId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-SH-" + suffix, orderId, fixture.customerId(), fixture.warehouseId(),
				LocalDate.now(), remark);
		this.jdbcTemplate.update("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
					tax_excluded_amount, tax_amount, tax_included_amount, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, ?, 0, ?, ?, ?, 0, ?, now(), now())
				""", shipmentId, orderLineId, fixture.materialId(), fixture.unitId(), quantityValue, quantityValue,
				quantityValue, remark, unitPriceValue, unitPriceValue, amount, amount);
		return shipmentId;
	}

	private long createPostedPurchaseReceipt(ProcurementFixture fixture, String remark, String quantity,
			String unitPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal orderedQuantity = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = orderedQuantity.multiply(unitPriceValue);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark, purchase_mode,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'RECEIVED', ?, 'PUBLIC', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-PO-" + suffix, fixture.supplierId(), LocalDate.now(),
				LocalDate.now().plusDays(3), remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, expected_arrival_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.materialId(), fixture.unitId(), orderedQuantity, orderedQuantity,
				unitPriceValue, unitPriceValue, unitPriceValue, amount, amount, LocalDate.now().plusDays(3), remark);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-PR-" + suffix, orderId, fixture.supplierId(), fixture.warehouseId(),
				LocalDate.now(), remark);
		this.jdbcTemplate.update("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, purchase_mode, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, ?, 'PUBLIC', now(), now())
				""", receiptId, orderLineId, fixture.materialId(), fixture.unitId(), orderedQuantity,
				orderedQuantity, orderedQuantity, remark);
		return receiptId;
	}

	private long createPostedPurchaseReceiptWithTwoLines(ProcurementFixture fixture, String remark,
			String firstQuantity, String firstUnitPrice, String secondQuantity, String secondUnitPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal firstQuantityValue = new BigDecimal(firstQuantity);
		BigDecimal firstUnitPriceValue = new BigDecimal(firstUnitPrice);
		BigDecimal secondQuantityValue = new BigDecimal(secondQuantity);
		BigDecimal secondUnitPriceValue = new BigDecimal(secondUnitPrice);
		BigDecimal firstAmount = firstQuantityValue.multiply(firstUnitPriceValue);
		BigDecimal secondAmount = secondQuantityValue.multiply(secondUnitPriceValue);
		long secondMaterialId = insertPurchasedMaterial("028_PU_MAT2_" + suffix, "028 采购物料二" + suffix,
				fixture.categoryId(), fixture.unitId());
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark, purchase_mode,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'RECEIVED', ?, 'PUBLIC', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-PO2-" + suffix, fixture.supplierId(), LocalDate.now(),
				LocalDate.now().plusDays(3), remark);
		long firstOrderLineId = insertPurchaseOrderLine(orderId, 1, fixture.materialId(), fixture.unitId(),
				firstQuantityValue, firstUnitPriceValue, firstAmount, remark);
		long secondOrderLineId = insertPurchaseOrderLine(orderId, 2, secondMaterialId, fixture.unitId(),
				secondQuantityValue, secondUnitPriceValue, secondAmount, remark);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-PR2-" + suffix, orderId, fixture.supplierId(), fixture.warehouseId(),
				LocalDate.now(), remark);
		insertPurchaseReceiptLine(receiptId, 1, firstOrderLineId, fixture.materialId(), fixture.unitId(),
				firstQuantityValue, remark);
		insertPurchaseReceiptLine(receiptId, 2, secondOrderLineId, secondMaterialId, fixture.unitId(),
				secondQuantityValue, remark);
		return receiptId;
	}

	private long insertPurchaseOrderLine(long orderId, int lineNo, long materialId, long unitId, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal amount, String remark) {
		return this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, expected_arrival_date, remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, lineNo, materialId, unitId, quantity, quantity, unitPrice, unitPrice,
				unitPrice, amount, amount, LocalDate.now().plusDays(3), remark);
	}

	private void insertPurchaseReceiptLine(long receiptId, int lineNo, long orderLineId, long materialId, long unitId,
			BigDecimal quantity, String remark) {
		this.jdbcTemplate.update("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, purchase_mode, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 0, ?, ?, null, null, ?, 'PUBLIC', now(), now())
				""", receiptId, lineNo, orderLineId, materialId, unitId, quantity, quantity, quantity, remark);
	}

	private long createPostedOutsourcingReceipt(ProcurementFixture fixture, String remark, String quantity,
			String unitCost) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitCostValue = new BigDecimal(unitCost);
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_order (
					outsourcing_order_no, supplier_id, product_material_id, planned_quantity, received_quantity,
					receipt_warehouse_id, planned_receipt_date, status, ownership_type, provisional_unit_cost,
					created_by, created_at, updated_by, updated_at, released_by, released_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', 'PUBLIC', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-OSO-" + suffix, fixture.supplierId(), fixture.materialId(), quantityValue,
				quantityValue, fixture.warehouseId(), LocalDate.now(), unitCostValue);
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into mfg_outsourcing_receipt (
					receipt_no, outsourcing_order_id, status, business_date, receipt_warehouse_id, quantity,
					rejected_quantity, provisional_unit_cost, unit_cost, valuation_state, ownership_type,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, 'POSTED', ?, ?, ?, 0, ?, ?, 'VALUED', 'PUBLIC',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-OSR-" + suffix, orderId, LocalDate.now(), fixture.warehouseId(), quantityValue,
				unitCostValue, unitCostValue);
		this.jdbcTemplate.update("""
				insert into mfg_outsourcing_receipt_line (
					receipt_id, line_no, accepted_quantity, rejected_quantity, provisional_unit_cost, unit_cost,
					created_at, updated_at
				)
				values (?, 1, ?, 0, ?, ?, now(), now())
				""", receiptId, quantityValue, unitCostValue, unitCostValue);
		return receiptId;
	}

	private SalesFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("028_UNIT_" + suffix, "028 单位" + suffix);
		long warehouseId = insertWarehouse("028_WH_" + suffix, "028 仓库" + suffix);
		long customerId = insertCustomer("028_CUS_" + suffix, "028 客户" + suffix);
		long categoryId = insertMaterialCategory("028_CAT_" + suffix);
		long materialId = insertMaterial("028_MAT_" + suffix, "028 成品" + suffix, categoryId, unitId);
		return new SalesFixture(unitId, warehouseId, customerId, categoryId, materialId);
	}

	private ProcurementFixture procurementFixture() {
		int suffix = SEQUENCE.incrementAndGet();
		long unitId = insertUnit("028_PU_UNIT_" + suffix, "028 采购单位" + suffix);
		long warehouseId = insertWarehouse("028_PU_WH_" + suffix, "028 采购仓" + suffix);
		long supplierId = insertSupplier("028_SUP_" + suffix, "028 供应商" + suffix);
		long categoryId = insertMaterialCategory("028_PU_CAT_" + suffix);
		long materialId = insertPurchasedMaterial("028_PU_MAT_" + suffix, "028 采购物料" + suffix, categoryId,
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
				""", Long.class, code, "028 分类" + code);
	}

	private long insertPurchasedMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at)
				values (?, ?, '028 采购规格', 'RAW_MATERIAL', 'PURCHASED', ?, ?, 'ENABLED',
					'DIRECT_MATERIAL', 'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private long insertMaterial(String code, String name, long categoryId, long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, cost_category, inventory_valuation_category, inventory_value_enabled, project_cost_enabled,
					created_by, created_at, updated_by, updated_at)
				values (?, ?, '028 规格', 'FINISHED_GOOD', 'SELF_MADE', ?, ?, 'ENABLED',
					'FINISHED_GOOD', 'VALUATED_MATERIAL', true, true, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, categoryId, unitId);
	}

	private long createProject(long customerId, String name) {
		int suffix = SEQUENCE.incrementAndGet();
		Long ownerId = this.jdbcTemplate.queryForObject("select id from sys_user where username = 'admin'",
				Long.class);
		return this.jdbcTemplate.queryForObject("""
				insert into sal_project (
					project_no, name, customer_id, owner_user_id, planned_start_date, planned_finish_date,
					status, target_revenue, target_cost, remark, created_by, created_at, updated_by, updated_at,
					activated_by, activated_at
				)
				values (?, ?, ?, ?, ?, ?, 'ACTIVE', 0, 0, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "028-PROJ-" + suffix, name + suffix, customerId, ownerId, LocalDate.now(),
				LocalDate.now().plusMonths(1), name);
	}

	private void insertCustomerSettlementTax(long customerId, String invoiceTitle, String taxNo) {
		this.jdbcTemplate.update("""
				insert into mst_customer_settlement_tax (
					customer_id, invoice_title, tax_no, registered_address, registered_phone, bank_name,
					bank_account, default_tax_rate, invoice_type, settlement_method, payment_term_days,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, '上海', '021-00000000', '测试银行', '622200000000', 0.13, 'SPECIAL_VAT',
					'MONTHLY', 30, 'test', now(), 'test', now())
				on conflict (customer_id) do update
				set invoice_title = excluded.invoice_title, tax_no = excluded.tax_no, updated_at = now()
				""", customerId, invoiceTitle, taxNo);
	}

	private long sourceLineId(String sourceType, long sourceId) {
		String tableName = switch (sourceType) {
			case "PURCHASE_RECEIPT" -> "proc_purchase_receipt_line";
			case "OUTSOURCING_RECEIPT" -> "mfg_outsourcing_receipt_line";
			default -> throw new IllegalArgumentException(sourceType);
		};
		String parentColumn = switch (sourceType) {
			case "PURCHASE_RECEIPT" -> "receipt_id";
			case "OUTSOURCING_RECEIPT" -> "receipt_id";
			default -> throw new IllegalArgumentException(sourceType);
		};
		return this.jdbcTemplate.queryForObject("select id from " + tableName + " where " + parentColumn + " = ?",
				Long.class, sourceId);
	}

	private List<Long> purchaseReceiptLineIds(long receiptId) {
		return this.jdbcTemplate.queryForList("""
				select id
				from proc_purchase_receipt_line
				where receipt_id = ?
				order by line_no asc
				""", Long.class, receiptId);
	}

	private long salesShipmentLineId(long shipmentId) {
		return this.jdbcTemplate.queryForObject("select id from sal_sales_shipment_line where shipment_id = ?",
				Long.class, shipmentId);
	}

	private long insertLegacyPostedReceipt(long customerId, String prefix, String amount, long version) {
		return this.jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status, remark, created_by, created_at,
					updated_by, updated_at, posted_by, posted_at, version
				)
				values (?, ?, ?, ?, 'BANK_TRANSFER', 'POSTED', '028 历史收款', 'test', now(), 'test', now(),
					'test', now(), ?)
				returning id
				""", Long.class, prefix + SEQUENCE.incrementAndGet(), customerId, LocalDate.now(),
				new BigDecimal(amount), version);
	}

	private ResponseEntity<String> createReceivable(AuthenticatedSession session, long sourceId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceType", "SALES_SHIPMENT");
		payload.put("sourceId", sourceId);
		payload.put("dueDate", LocalDate.now().plusDays(30).toString());
		payload.put("remark", remark);
		return exchange(HttpMethod.POST, "/api/admin/finance/receivables", payload, session);
	}

	private ResponseEntity<String> createPayable(AuthenticatedSession session, long sourceId, String remark) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sourceType", "PURCHASE_RECEIPT");
		payload.put("sourceId", sourceId);
		payload.put("dueDate", LocalDate.now().plusDays(30).toString());
		payload.put("remark", remark);
		return exchange(HttpMethod.POST, "/api/admin/finance/payables", payload, session);
	}

	private ResponseEntity<String> confirmReceivable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/receivables/" + id + "/confirm", null, session);
	}

	private ResponseEntity<String> confirmPayable(AuthenticatedSession session, long id) {
		return exchange(HttpMethod.PUT, "/api/admin/finance/payables/" + id + "/confirm", null, session);
	}

	private long existingTableCount(List<String> tableNames) {
		String[] names = tableNames.stream().map((name) -> "public." + name).toArray(String[]::new);
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from unnest(?::text[]) as t(name)
				where to_regclass(t.name) is not null
				""", Long.class, (Object) names);
		return count == null ? 0 : count;
	}

	private long permissionCount(List<String> permissionCodes) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_permission
				where code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private long systemAdminPermissionCount(List<String> permissionCodes) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sys_role r
				join sys_role_permission rp on rp.role_id = r.id
				join sys_permission p on p.id = rp.permission_id
				where r.code = 'SYSTEM_ADMIN'
				and p.code = any (?::text[])
				""", Long.class, (Object) permissionCodes.toArray(String[]::new));
		return count == null ? 0 : count;
	}

	private boolean constraintExists(String tableName, String constraintName) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from pg_constraint c
					join pg_class t on t.oid = c.conrelid
					where t.relname = ?
					and c.conname = ?
				)
				""", Boolean.class, tableName, constraintName);
		return Boolean.TRUE.equals(exists);
	}

	private boolean indexExists(String indexName) {
		Boolean exists = this.jdbcTemplate.queryForObject("select to_regclass(?) is not null", Boolean.class,
				"public." + indexName);
		return Boolean.TRUE.equals(exists);
	}

	private long countReceivablesBySource(long sourceId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_receivable
				where source_type = 'SALES_SHIPMENT'
				and source_id = ?
				""", Long.class, sourceId);
		return count == null ? 0 : count;
	}

	private long countPayablesBySource(String sourceType, long sourceId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_payable
				where source_type = ?
				and source_id = ?
				""", Long.class, sourceType, sourceId);
		return count == null ? 0 : count;
	}

	private long linkCount(String tableName, String firstColumn, long firstId, String secondColumn, long secondId) {
		Long count = this.jdbcTemplate.queryForObject(
				"select count(*) from " + tableName + " where " + firstColumn + " = ? and " + secondColumn + " = ?",
				Long.class, firstId, secondId);
		return count == null ? 0 : count;
	}

	private long tableCount(String tableName) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
		return count == null ? 0 : count;
	}

	private long tableCount(String tableName, String columnName, long value) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + columnName
				+ " = ?", Long.class, value);
		return count == null ? 0 : count;
	}

	private void assertDecimal(String sql, long id, String expected) {
		BigDecimal actual = this.jdbcTemplate.queryForObject(sql, BigDecimal.class, id);
		assertThat(actual.compareTo(new BigDecimal(expected))).isZero();
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
				""", Long.class, rolePrefix + suffix, "028 财务测试角色" + suffix, "028 财务测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "028 财务测试用户" + suffix);
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

	private void assertDecimal(JsonNode node, String field, String expected) {
		assertThat(new BigDecimal(node.get(field).asText()).compareTo(new BigDecimal(expected))).isZero();
	}

	private void assertJsonDecimalString(JsonNode node, String field, String expected) {
		assertThat(node.get(field).isTextual()).isTrue();
		assertDecimal(node, field, expected);
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
