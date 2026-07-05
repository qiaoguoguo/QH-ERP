package com.qherp.api.system.reporting;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "qherp.test.context=reporting-admin")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportingAdminControllerTests extends PostgresIntegrationTest {

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
	void adminCanQueryStableEmptyReportSkeletons() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		String emptyPeriod = "?dateFrom=2026-01-01&dateTo=2026-01-31";

		ResponseEntity<String> overview = get("/api/admin/reports/overview" + emptyPeriod, admin);
		assertOk(overview);
		JsonNode overviewData = data(overview);
		assertThat(overviewData.get("salesShipmentAmount").asText()).isEqualTo("0.00");
		assertThat(overviewData.get("purchaseReceiptAmount").asText()).isEqualTo("0.00");
		assertThat(overviewData.get("inventoryInQuantity").asText()).isEqualTo("0.000");
		assertThat(overviewData.get("exceptionCount").intValue()).isZero();
		assertThat(overviewData.get("formalAccounting").booleanValue()).isFalse();

		JsonNode emptySales = data(get("/api/admin/reports/sales-summary" + emptyPeriod, admin));
		assertThat(emptySales.get("summary").get("salesOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(emptySales.get("summary").get("salesReturnAmount").asText()).isEqualTo("0.00");
		assertThat(emptySales.get("summary").get("salesNetAmount").asText()).isEqualTo("0.00");
		assertThat(emptySales.get("summary").get("salesOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(emptySales.get("summary").get("salesReturnQuantity").asText()).isEqualTo("0.000");
		assertThat(emptySales.get("summary").get("salesNetQuantity").asText()).isEqualTo("0.000");

		JsonNode emptyProcurement = data(get("/api/admin/reports/procurement-summary" + emptyPeriod, admin));
		assertThat(emptyProcurement.get("summary").get("purchaseOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(emptyProcurement.get("summary").get("purchaseReturnAmount").asText()).isEqualTo("0.00");
		assertThat(emptyProcurement.get("summary").get("purchaseNetAmount").asText()).isEqualTo("0.00");
		assertThat(emptyProcurement.get("summary").get("purchaseOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyProcurement.get("summary").get("purchaseReturnQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyProcurement.get("summary").get("purchaseNetQuantity").asText()).isEqualTo("0.000");

		JsonNode emptyInventory = data(get("/api/admin/reports/inventory-stock-flow" + emptyPeriod, admin));
		assertThat(emptyInventory.get("summary").get("inboundOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyInventory.get("summary").get("inboundReverseQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyInventory.get("summary").get("inboundNetQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyInventory.get("summary").get("outboundOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyInventory.get("summary").get("outboundReverseQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyInventory.get("summary").get("outboundNetQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyInventory.get("summary").get("inventoryNetChangeQuantity").asText()).isEqualTo("0.000");

		JsonNode emptyProduction = data(get("/api/admin/reports/production-execution" + emptyPeriod, admin));
		assertThat(emptyProduction.get("summary").get("issuedOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyProduction.get("summary").get("materialReturnQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyProduction.get("summary").get("materialSupplementQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyProduction.get("summary").get("issuedNetQuantity").asText()).isEqualTo("0.000");
		assertThat(emptyProduction.get("summary").get("completedQuantity").asText()).isEqualTo("0.000");

		JsonNode emptyCost = data(get("/api/admin/reports/cost-collection" + emptyPeriod, admin));
		assertThat(emptyCost.get("summary").get("materialOriginalCost").asText()).isEqualTo("0.00");
		assertThat(emptyCost.get("summary").get("materialReturnCost").asText()).isEqualTo("0.00");
		assertThat(emptyCost.get("summary").get("materialSupplementCost").asText()).isEqualTo("0.00");
		assertThat(emptyCost.get("summary").get("materialNetCost").asText()).isEqualTo("0.00");
		assertThat(emptyCost.get("summary").get("totalNetCost").asText()).isEqualTo("0.00");

		JsonNode emptySettlement = data(get("/api/admin/reports/settlement-summary" + emptyPeriod, admin));
		assertThat(emptySettlement.get("summary").get("receivableOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(emptySettlement.get("summary").get("receivableAdjustmentAmount").asText()).isEqualTo("0.00");
		assertThat(emptySettlement.get("summary").get("receivableNetAmount").asText()).isEqualTo("0.00");
		assertThat(emptySettlement.get("summary").get("payableOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(emptySettlement.get("summary").get("payableAdjustmentAmount").asText()).isEqualTo("0.00");
		assertThat(emptySettlement.get("summary").get("payableNetAmount").asText()).isEqualTo("0.00");
		assertThat(emptySettlement.get("summary").get("settlementRemainingAmount").asText()).isEqualTo("0.00");

		for (String path : List.of("/api/admin/reports/sales-summary", "/api/admin/reports/procurement-summary",
				"/api/admin/reports/inventory-stock-flow", "/api/admin/reports/production-execution",
				"/api/admin/reports/cost-collection", "/api/admin/reports/settlement-summary",
				"/api/admin/reports/exceptions")) {
			assertEmptyReportPage(get(path + emptyPeriod, admin));
		}

	}

	@Test
	void businessReportsExposeReversalNetFieldsAndTraceSources() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 24);
		ReportingFixture fixture = fixture();

		SalesShipmentFixture shipment = createSalesShipment(fixture, date, "POSTED", "10.000000", "20.000000",
				"销售净额");
		ReceivableFixture receivable = createReceivable(shipment, "200.00", "50.00", "150.00",
				"PARTIALLY_RECEIVED");
		updateReceivableAdjustment(receivable.receivableId(), "40.00", "110.00", "PARTIALLY_RECEIVED");
		long receiptId = createReceipt(fixture.customerId(), receivable.receivableId(), date, "50.00", "POSTED");
		ReturnLineFixture salesReturn = createPostedSalesReturn(fixture, shipment, date, "2.000000", "20.000000");
		long receivableAdjustmentId = createSettlementAdjustment("RECEIVABLE", "RETURN_OFFSET", "SALES_RETURN",
				salesReturn.documentId(), receivable.receivableId(), date, "40.00");

		PurchaseReceiptFixture receipt = createPurchaseReceipt(fixture, date, "POSTED", "8.000000", "10.000000",
				"采购净额");
		PayableFixture payable = createPayable(receipt, "80.00", "20.00", "60.00", "PARTIALLY_PAID");
		updatePayableAdjustment(payable.payableId(), "30.00", "30.00", "PARTIALLY_PAID");
		createPayment(fixture.supplierId(), payable.payableId(), date, "20.00", "POSTED");
		ReturnLineFixture purchaseReturn = createPostedPurchaseReturn(fixture, receipt, date, "3.000000",
				"10.000000");
		createSettlementAdjustment("PAYABLE", "RETURN_OFFSET", "PURCHASE_RETURN", purchaseReturn.documentId(),
				payable.payableId(), date, "30.00");

		ProductionFixture production = createProductionWorkOrder(fixture, date, "IN_PROGRESS", "12.000000",
				"生产净额");
		MaterialIssueFixture issue = createMaterialIssue(fixture, production, date, "POSTED", "6.000000");
		ReturnLineFixture materialReturn = createPostedMaterialReturn(fixture, production, issue, date,
				"2.000000");
		ReturnLineFixture materialSupplement = createPostedMaterialSupplement(fixture, production, date,
				"3.000000");
		createCompletionReceipt(fixture, production, date, "POSTED", "7.000000");
		createCostRecord(fixture, production, date, "MATERIAL", "PRODUCTION_MATERIAL_ISSUE", issue.issueNo(),
				issue.issueId(), issue.issueLineId(), production.workOrderMaterialId(), fixture.rawMaterialId(),
				"6.000000", "10.000000", "60.000000", "MANUAL_UNIT_PRICE_QUANTITY", "ACTIVE");
		CostRecordFixture materialReturnCost = createCostRecord(fixture, production, date, "MATERIAL",
				"PRODUCTION_MATERIAL_RETURN", materialReturn.documentNo(), materialReturn.documentId(),
				materialReturn.lineId(), production.workOrderMaterialId(), fixture.rawMaterialId(), "2.000000",
				"10.000000", "20.000000", "MANUAL_UNIT_PRICE_QUANTITY", "ACTIVE");
		createCostRecord(fixture, production, date, "MATERIAL", "PRODUCTION_MATERIAL_SUPPLEMENT",
				materialSupplement.documentNo(), materialSupplement.documentId(), materialSupplement.lineId(),
				production.workOrderMaterialId(), fixture.rawMaterialId(), "3.000000", "10.000000", "30.000000",
				"MANUAL_UNIT_PRICE_QUANTITY", "ACTIVE");
		createCostRecord(fixture, production, date, "LABOR", "PRODUCTION_WORK_REPORT", "人工净额", null, null,
				null, null, null, null, "15.000000", "MANUAL_AMOUNT", "ACTIVE");

		insertStockMovement(fixture, fixture.rawMaterialId(), date, "PURCHASE_RECEIPT", "IN", "8.000000",
				"PURCHASE_RECEIPT", receipt.receiptId(), receipt.receiptLineId(), "采购入库原发生");
		insertStockMovement(fixture, fixture.finishedMaterialId(), date, "SALES_SHIPMENT", "OUT", "10.000000",
				"SALES_SHIPMENT", shipment.shipmentId(), shipment.shipmentLineId(), "销售出库原发生");
		insertStockMovement(fixture, fixture.finishedMaterialId(), date, "SALES_RETURN_IN", "IN", "2.000000",
				"SALES_RETURN", salesReturn.documentId(), salesReturn.lineId(), "销售退货反向入库");
		insertStockMovement(fixture, fixture.rawMaterialId(), date, "PRODUCTION_MATERIAL_RETURN_IN", "IN",
				"2.000000", "PRODUCTION_MATERIAL_RETURN", materialReturn.documentId(), materialReturn.lineId(),
				"生产退料反向入库");
		insertStockMovement(fixture, fixture.rawMaterialId(), date, "PURCHASE_RETURN_OUT", "OUT", "3.000000",
				"PURCHASE_RETURN", purchaseReturn.documentId(), purchaseReturn.lineId(), "采购退货反向出库");
		insertStockMovement(fixture, fixture.rawMaterialId(), date, "PRODUCTION_MATERIAL_SUPPLEMENT_OUT", "OUT",
				"3.000000", "PRODUCTION_MATERIAL_SUPPLEMENT", materialSupplement.documentId(),
				materialSupplement.lineId(), "生产补料反向出库");

		String period = "?dateFrom=" + date + "&dateTo=" + date;
		JsonNode sales = data(get("/api/admin/reports/sales-summary" + period + "&keyword=" + shipment.shipmentNo(),
				admin));
		assertThat(sales.get("summary").get("salesOriginalAmount").asText()).isEqualTo("200.00");
		assertThat(sales.get("summary").get("salesReturnAmount").asText()).isEqualTo("40.00");
		assertThat(sales.get("summary").get("salesNetAmount").asText()).isEqualTo("160.00");
		assertThat(sales.get("summary").get("salesOriginalQuantity").asText()).isEqualTo("10.000");
		assertThat(sales.get("summary").get("salesReturnQuantity").asText()).isEqualTo("2.000");
		assertThat(sales.get("summary").get("salesNetQuantity").asText()).isEqualTo("8.000");
		JsonNode salesItem = sales.get("items").get(0);
		assertThat(salesItem.get("salesReturnAmount").asText()).isEqualTo("40.00");
		assertThat(salesItem.get("salesNetQuantity").asText()).isEqualTo("8.000");
		JsonNode salesTrace = data(get("/api/admin/reports/sales-summary/traces?traceKey="
				+ salesItem.get("traceKey").asText() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(salesTrace, "SALES_RETURN").get("resourceRouteName").asText())
			.isEqualTo("sales-return-detail");

		JsonNode procurement = data(get(
				"/api/admin/reports/procurement-summary" + period + "&keyword=" + receipt.receiptNo(), admin));
		assertThat(procurement.get("summary").get("purchaseOriginalAmount").asText()).isEqualTo("80.00");
		assertThat(procurement.get("summary").get("purchaseReturnAmount").asText()).isEqualTo("30.00");
		assertThat(procurement.get("summary").get("purchaseNetAmount").asText()).isEqualTo("50.00");
		assertThat(procurement.get("summary").get("purchaseOriginalQuantity").asText()).isEqualTo("8.000");
		assertThat(procurement.get("summary").get("purchaseReturnQuantity").asText()).isEqualTo("3.000");
		assertThat(procurement.get("summary").get("purchaseNetQuantity").asText()).isEqualTo("5.000");
		JsonNode procurementItem = procurement.get("items").get(0);
		JsonNode procurementTrace = data(get("/api/admin/reports/procurement-summary/traces?traceKey="
				+ procurementItem.get("traceKey").asText() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(procurementTrace, "PURCHASE_RETURN").get("resourceRouteName").asText())
			.isEqualTo("procurement-return-detail");

		JsonNode inventory = data(get("/api/admin/reports/inventory-stock-flow" + period + "&warehouseId="
				+ fixture.warehouseId(), admin));
		assertThat(inventory.get("summary").get("inboundOriginalQuantity").asText()).isEqualTo("8.000");
		assertThat(inventory.get("summary").get("inboundReverseQuantity").asText()).isEqualTo("4.000");
		assertThat(inventory.get("summary").get("inboundNetQuantity").asText()).isEqualTo("12.000");
		assertThat(inventory.get("summary").get("outboundOriginalQuantity").asText()).isEqualTo("10.000");
		assertThat(inventory.get("summary").get("outboundReverseQuantity").asText()).isEqualTo("6.000");
		assertThat(inventory.get("summary").get("outboundNetQuantity").asText()).isEqualTo("16.000");
		assertThat(inventory.get("summary").get("inventoryNetChangeQuantity").asText()).isEqualTo("-4.000");

		JsonNode productionReport = data(get("/api/admin/reports/production-execution" + period
				+ "&workOrderId=" + production.workOrderId(), admin));
		assertThat(productionReport.get("summary").get("issuedOriginalQuantity").asText()).isEqualTo("6.000");
		assertThat(productionReport.get("summary").get("materialReturnQuantity").asText()).isEqualTo("2.000");
		assertThat(productionReport.get("summary").get("materialSupplementQuantity").asText()).isEqualTo("3.000");
		assertThat(productionReport.get("summary").get("issuedNetQuantity").asText()).isEqualTo("7.000");
		assertThat(productionReport.get("summary").get("completedQuantity").asText()).isEqualTo("7.000");
		JsonNode productionTrace = data(get("/api/admin/reports/production-execution/traces?traceKey=production-execution:WORK_ORDER:"
				+ production.workOrderId() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(productionTrace, "PRODUCTION_MATERIAL_RETURN")
			.get("resourceRouteName")
			.asText()).isEqualTo("production-material-return-detail");
		assertThat(firstItemWithSourceType(productionTrace, "PRODUCTION_MATERIAL_SUPPLEMENT")
			.get("resourceRouteName")
			.asText()).isEqualTo("production-material-supplement-detail");

		JsonNode cost = data(get("/api/admin/reports/cost-collection" + period + "&workOrderId="
				+ production.workOrderId(), admin));
		assertThat(cost.get("summary").get("materialOriginalCost").asText()).isEqualTo("60.00");
		assertThat(cost.get("summary").get("materialReturnCost").asText()).isEqualTo("20.00");
		assertThat(cost.get("summary").get("materialSupplementCost").asText()).isEqualTo("30.00");
		assertThat(cost.get("summary").get("materialNetCost").asText()).isEqualTo("70.00");
		assertThat(cost.get("summary").get("totalNetCost").asText()).isEqualTo("85.00");
		JsonNode returnCostTrace = data(get("/api/admin/reports/cost-collection/traces?traceKey=cost-collection:COST_RECORD:"
				+ materialReturnCost.costRecordId() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(returnCostTrace, "PRODUCTION_MATERIAL_RETURN")
			.get("resourceRouteName")
			.asText()).isEqualTo("production-material-return-detail");

		JsonNode settlement = data(get("/api/admin/reports/settlement-summary" + period + "&customerId="
				+ fixture.customerId(), admin));
		assertThat(settlement.get("summary").get("receivableOriginalAmount").asText()).isEqualTo("200.00");
		assertThat(settlement.get("summary").get("receivableAdjustmentAmount").asText()).isEqualTo("40.00");
		assertThat(settlement.get("summary").get("receivableNetAmount").asText()).isEqualTo("160.00");
		assertThat(settlement.get("summary").get("settlementRemainingAmount").asText()).isEqualTo("110.00");
		JsonNode receivableTrace = data(get("/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:RECEIVABLE:"
				+ receivable.receivableId() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(receivableTrace, "SETTLEMENT_ADJUSTMENT").get("sourceId").longValue())
			.isEqualTo(receivableAdjustmentId);
		JsonNode adjustmentTrace = data(get("/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:SETTLEMENT_ADJUSTMENT:"
				+ receivableAdjustmentId + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(adjustmentTrace, "SETTLEMENT_ADJUSTMENT")
			.get("resourceRouteName")
			.asText()).isEqualTo("finance-settlement-adjustment-detail");

		JsonNode payableSettlement = data(get("/api/admin/reports/settlement-summary" + period + "&supplierId="
				+ fixture.supplierId(), admin));
		assertThat(payableSettlement.get("summary").get("payableOriginalAmount").asText()).isEqualTo("80.00");
		assertThat(payableSettlement.get("summary").get("payableAdjustmentAmount").asText()).isEqualTo("30.00");
		assertThat(payableSettlement.get("summary").get("payableNetAmount").asText()).isEqualTo("50.00");
		assertThat(payableSettlement.get("summary").get("settlementRemainingAmount").asText()).isEqualTo("30.00");

		assertError(get(
				"/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:SETTLEMENT_ADJUSTMENT:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertThat(receiptId).isPositive();
	}

	@Test
	void crossPeriodReversalFactsUseOwnBusinessDateInReports() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate originalDate = LocalDate.of(2026, 6, 25);
		LocalDate reverseDate = LocalDate.of(2026, 7, 5);
		String reversePeriod = "?dateFrom=2026-07-01&dateTo=2026-07-31";
		ReportingFixture fixture = fixture();

		SalesShipmentFixture shipment = createSalesShipment(fixture, originalDate, "POSTED", "10.000000",
				"20.000000", "跨期销售原单");
		ReceivableFixture receivable = createReceivable(shipment, "200.00", "50.00", "150.00",
				"PARTIALLY_RECEIVED");
		ReturnLineFixture salesReturn = createPostedSalesReturn(fixture, shipment, reverseDate, "2.000000",
				"20.000000");
		updateReceivableAdjustment(receivable.receivableId(), "40.00", "110.00", "PARTIALLY_RECEIVED");
		long receivableAdjustmentId = createSettlementAdjustment("RECEIVABLE", "RETURN_OFFSET", "SALES_RETURN",
				salesReturn.documentId(), receivable.receivableId(), reverseDate, "40.00");

		PurchaseReceiptFixture receipt = createPurchaseReceipt(fixture, originalDate, "POSTED", "8.000000",
				"10.000000", "跨期采购原单");
		PayableFixture payable = createPayable(receipt, "80.00", "20.00", "60.00", "PARTIALLY_PAID");
		ReturnLineFixture purchaseReturn = createPostedPurchaseReturn(fixture, receipt, reverseDate, "3.000000",
				"10.000000");
		updatePayableAdjustment(payable.payableId(), "30.00", "30.00", "PARTIALLY_PAID");
		long payableAdjustmentId = createSettlementAdjustment("PAYABLE", "RETURN_OFFSET", "PURCHASE_RETURN",
				purchaseReturn.documentId(), payable.payableId(), reverseDate, "30.00");

		ProductionFixture production = createProductionWorkOrder(fixture, originalDate, "IN_PROGRESS",
				"12.000000", "跨期生产原单");
		MaterialIssueFixture issue = createMaterialIssue(fixture, production, originalDate, "POSTED", "6.000000");
		ReturnLineFixture materialReturn = createPostedMaterialReturn(fixture, production, issue, reverseDate,
				"2.000000");
		ReturnLineFixture materialSupplement = createPostedMaterialSupplement(fixture, production, reverseDate,
				"3.000000");
		createCostRecord(fixture, production, reverseDate, "MATERIAL", "PRODUCTION_MATERIAL_RETURN",
				materialReturn.documentNo(), materialReturn.documentId(), materialReturn.lineId(),
				production.workOrderMaterialId(), fixture.rawMaterialId(), "2.000000", "10.000000", "20.000000",
				"MANUAL_UNIT_PRICE_QUANTITY", "ACTIVE");
		createCostRecord(fixture, production, reverseDate, "MATERIAL", "PRODUCTION_MATERIAL_SUPPLEMENT",
				materialSupplement.documentNo(), materialSupplement.documentId(), materialSupplement.lineId(),
				production.workOrderMaterialId(), fixture.rawMaterialId(), "3.000000", "10.000000", "30.000000",
				"MANUAL_UNIT_PRICE_QUANTITY", "ACTIVE");

		insertStockMovement(fixture, fixture.finishedMaterialId(), reverseDate, "SALES_RETURN_IN", "IN",
				"2.000000", "SALES_RETURN", salesReturn.documentId(), salesReturn.lineId(), "跨期销售退货入库");
		insertStockMovement(fixture, fixture.rawMaterialId(), reverseDate, "PRODUCTION_MATERIAL_RETURN_IN", "IN",
				"2.000000", "PRODUCTION_MATERIAL_RETURN", materialReturn.documentId(), materialReturn.lineId(),
				"跨期生产退料入库");
		insertStockMovement(fixture, fixture.rawMaterialId(), reverseDate, "PURCHASE_RETURN_OUT", "OUT",
				"3.000000", "PURCHASE_RETURN", purchaseReturn.documentId(), purchaseReturn.lineId(),
				"跨期采购退货出库");
		insertStockMovement(fixture, fixture.rawMaterialId(), reverseDate, "PRODUCTION_MATERIAL_SUPPLEMENT_OUT",
				"OUT", "3.000000", "PRODUCTION_MATERIAL_SUPPLEMENT", materialSupplement.documentId(),
				materialSupplement.lineId(), "跨期生产补料出库");

		JsonNode sales = data(get("/api/admin/reports/sales-summary" + reversePeriod + "&customerId="
				+ fixture.customerId() + "&materialId=" + fixture.finishedMaterialId(), admin));
		assertThat(sales.get("summary").get("salesOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(sales.get("summary").get("salesReturnAmount").asText()).isEqualTo("40.00");
		assertThat(sales.get("summary").get("salesNetAmount").asText()).isEqualTo("-40.00");
		assertThat(sales.get("summary").get("salesOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(sales.get("summary").get("salesReturnQuantity").asText()).isEqualTo("2.000");
		assertThat(sales.get("summary").get("salesNetQuantity").asText()).isEqualTo("-2.000");
		JsonNode salesReturnItem = firstItemWithSourceType(sales, "SALES_RETURN");
		assertThat(salesReturnItem.get("sourceId").longValue()).isEqualTo(salesReturn.documentId());
		JsonNode salesReturnTrace = data(get("/api/admin/reports/sales-summary/traces?traceKey="
				+ salesReturnItem.get("traceKey").asText() + "&dateFrom=2026-07-01&dateTo=2026-07-31", admin));
		assertThat(firstItemWithSourceType(salesReturnTrace, "SALES_RETURN").get("resourceRouteName").asText())
			.isEqualTo("sales-return-detail");

		JsonNode procurement = data(get("/api/admin/reports/procurement-summary" + reversePeriod + "&supplierId="
				+ fixture.supplierId() + "&materialId=" + fixture.rawMaterialId(), admin));
		assertThat(procurement.get("summary").get("purchaseOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(procurement.get("summary").get("purchaseReturnAmount").asText()).isEqualTo("30.00");
		assertThat(procurement.get("summary").get("purchaseNetAmount").asText()).isEqualTo("-30.00");
		assertThat(procurement.get("summary").get("purchaseOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(procurement.get("summary").get("purchaseReturnQuantity").asText()).isEqualTo("3.000");
		assertThat(procurement.get("summary").get("purchaseNetQuantity").asText()).isEqualTo("-3.000");
		JsonNode purchaseReturnItem = firstItemWithSourceType(procurement, "PURCHASE_RETURN");
		assertThat(purchaseReturnItem.get("sourceId").longValue()).isEqualTo(purchaseReturn.documentId());
		JsonNode purchaseReturnTrace = data(get("/api/admin/reports/procurement-summary/traces?traceKey="
				+ purchaseReturnItem.get("traceKey").asText() + "&dateFrom=2026-07-01&dateTo=2026-07-31", admin));
		assertThat(firstItemWithSourceType(purchaseReturnTrace, "PURCHASE_RETURN").get("resourceRouteName").asText())
			.isEqualTo("procurement-return-detail");

		JsonNode inventory = data(get("/api/admin/reports/inventory-stock-flow" + reversePeriod + "&warehouseId="
				+ fixture.warehouseId(), admin));
		assertThat(inventory.get("summary").get("inboundOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(inventory.get("summary").get("inboundReverseQuantity").asText()).isEqualTo("4.000");
		assertThat(inventory.get("summary").get("outboundOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(inventory.get("summary").get("outboundReverseQuantity").asText()).isEqualTo("6.000");
		assertThat(inventory.get("summary").get("inventoryNetChangeQuantity").asText()).isEqualTo("-2.000");

		JsonNode productionReport = data(get("/api/admin/reports/production-execution" + reversePeriod
				+ "&workOrderId=" + production.workOrderId(), admin));
		assertThat(productionReport.get("summary").get("plannedQuantity").asText()).isEqualTo("0.000");
		assertThat(productionReport.get("summary").get("issuedOriginalQuantity").asText()).isEqualTo("0.000");
		assertThat(productionReport.get("summary").get("materialReturnQuantity").asText()).isEqualTo("2.000");
		assertThat(productionReport.get("summary").get("materialSupplementQuantity").asText()).isEqualTo("3.000");
		assertThat(productionReport.get("summary").get("issuedNetQuantity").asText()).isEqualTo("1.000");
		JsonNode productionTrace = data(get(
				"/api/admin/reports/production-execution/traces?traceKey=production-execution:WORK_ORDER:"
						+ production.workOrderId() + "&dateFrom=2026-07-01&dateTo=2026-07-31",
				admin));
		assertThat(firstItemWithSourceType(productionTrace, "PRODUCTION_MATERIAL_RETURN")
			.get("resourceRouteName")
			.asText()).isEqualTo("production-material-return-detail");
		assertThat(firstItemWithSourceType(productionTrace, "PRODUCTION_MATERIAL_SUPPLEMENT")
			.get("resourceRouteName")
			.asText()).isEqualTo("production-material-supplement-detail");

		JsonNode cost = data(get("/api/admin/reports/cost-collection" + reversePeriod + "&workOrderId="
				+ production.workOrderId(), admin));
		assertThat(cost.get("summary").get("materialOriginalCost").asText()).isEqualTo("0.00");
		assertThat(cost.get("summary").get("materialReturnCost").asText()).isEqualTo("20.00");
		assertThat(cost.get("summary").get("materialSupplementCost").asText()).isEqualTo("30.00");
		assertThat(cost.get("summary").get("materialNetCost").asText()).isEqualTo("10.00");
		assertThat(cost.get("summary").get("totalNetCost").asText()).isEqualTo("10.00");

		JsonNode settlement = data(get("/api/admin/reports/settlement-summary" + reversePeriod + "&customerId="
				+ fixture.customerId(), admin));
		assertThat(settlement.get("summary").get("receivableOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(settlement.get("summary").get("receivableAdjustmentAmount").asText()).isEqualTo("40.00");
		assertThat(settlement.get("summary").get("receivableNetAmount").asText()).isEqualTo("-40.00");
		JsonNode receivableAdjustmentItem = firstItemWithText(settlement, "traceKey",
				"settlement-summary:SETTLEMENT_ADJUSTMENT:" + receivableAdjustmentId);
		assertThat(receivableAdjustmentItem.get("sourceType").asText()).isEqualTo("SETTLEMENT_ADJUSTMENT");

		JsonNode payableSettlement = data(get("/api/admin/reports/settlement-summary" + reversePeriod
				+ "&supplierId=" + fixture.supplierId(), admin));
		assertThat(payableSettlement.get("summary").get("payableOriginalAmount").asText()).isEqualTo("0.00");
		assertThat(payableSettlement.get("summary").get("payableAdjustmentAmount").asText()).isEqualTo("30.00");
		assertThat(payableSettlement.get("summary").get("payableNetAmount").asText()).isEqualTo("-30.00");
		JsonNode payableAdjustmentItem = firstItemWithText(payableSettlement, "traceKey",
				"settlement-summary:SETTLEMENT_ADJUSTMENT:" + payableAdjustmentId);
		assertThat(payableAdjustmentItem.get("sourceType").asText()).isEqualTo("SETTLEMENT_ADJUSTMENT");
	}

	@Test
	void reportEndpointsRequireAuthenticationAndReportPermission() throws Exception {
		ResponseEntity<String> unauthenticated = this.restTemplate.getForEntity("/api/admin/reports/overview",
				String.class);
		assertError(unauthenticated, HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED");

		AuthenticatedSession noReport = createUserAndLogin("report-no-permission", List.of());
		assertError(get("/api/admin/reports/overview", noReport), HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN");
	}

	@Test
	void reportQueryValidationReturnsControlledErrors() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate today = LocalDate.now();

		assertError(get("/api/admin/reports/overview?dateFrom=" + today + "&dateTo=" + today.minusDays(1), admin),
				HttpStatus.BAD_REQUEST, "REPORT_DATE_RANGE_INVALID");
		assertError(get("/api/admin/reports/overview?dateFrom=" + today.minusMonths(13) + "&dateTo=" + today,
				admin), HttpStatus.BAD_REQUEST, "REPORT_DATE_RANGE_INVALID");
		assertError(get(
				"/api/admin/reports/inventory-stock-flow/traces?traceKey=inventory-stock-flow:1:1&dateFrom=2026-06-02&dateTo=2026-06-01",
				admin), HttpStatus.BAD_REQUEST, "REPORT_DATE_RANGE_INVALID");
		assertError(get("/api/admin/reports/sales-summary?page=0", admin), HttpStatus.BAD_REQUEST,
				"REPORT_PARAMETER_INVALID");
		assertError(get("/api/admin/reports/sales-summary?pageSize=101", admin), HttpStatus.BAD_REQUEST,
				"REPORT_PARAMETER_INVALID");
		assertError(get("/api/admin/reports/sales-summary?status=INVALID", admin), HttpStatus.BAD_REQUEST,
				"REPORT_PARAMETER_INVALID");
		assertError(get("/api/admin/reports/exceptions?type=INVALID", admin), HttpStatus.BAD_REQUEST,
				"REPORT_PARAMETER_INVALID");
		assertError(get("/api/admin/reports/sales-summary/traces?traceKey=bad-key", admin), HttpStatus.BAD_REQUEST,
				"REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/sales-summary/traces?traceKey=sales-summary:SALES_SHIPMENT:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/procurement-summary/traces?traceKey=procurement-summary:PURCHASE_RECEIPT:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:RECEIVABLE:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:PAYABLE:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:RECEIPT:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:PAYMENT:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/inventory-stock-flow/traces?traceKey=inventory-stock-flow:999999999:999999998",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/production-execution/traces?traceKey=production-execution:WORK_ORDER:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/cost-collection/traces?traceKey=cost-collection:WORK_ORDER:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/cost-collection/traces?traceKey=cost-collection:COST_RECORD:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/exceptions/traces?traceKey=exceptions:INVENTORY_SHORTAGE:INVENTORY_MOVEMENT:999999999",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
		assertError(get(
				"/api/admin/reports/exceptions/traces?traceKey=exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:999999999:999999998",
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
	}

	@Test
	void extremelyLargePageReturnsEmptyPageWithoutOverflow() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		ResponseEntity<String> response = get("/api/admin/reports/sales-summary?page=2147483647&pageSize=100",
				admin);

		assertOk(response);
		JsonNode data = data(response);
		assertThat(data.get("summary")).isNotNull();
		assertThat(data.get("items").size()).isZero();
		assertThat(data.get("page").intValue()).isEqualTo(Integer.MAX_VALUE);
		assertThat(data.get("pageSize").intValue()).isEqualTo(100);
	}

	@Test
	void unrelatedStatusParameterIsIgnoredForReportsThatDoNotSupportStatus() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);

		assertOk(get("/api/admin/reports/overview?status=IGNORED", admin));
		assertEmptyReportPage(get("/api/admin/reports/inventory-stock-flow?status=IGNORED", admin));
		assertEmptyReportPage(get("/api/admin/reports/exceptions?status=IGNORED", admin));
	}

	@Test
	void salesAndProcurementReportsAggregatePostedDocumentsAndTraceAmounts() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate salesDate = LocalDate.of(2026, 6, 11);
		LocalDate procurementDate = LocalDate.of(2026, 6, 12);
		ReportingFixture fixture = fixture();

		SalesShipmentFixture postedShipment = createSalesShipment(fixture, salesDate, "POSTED", "3.000000",
				"120.000000", "销售报表已过账");
		createSalesShipment(fixture, salesDate, "DRAFT", "5.000000", "100.000000", "销售报表草稿");
		createReceivable(postedShipment, "360.00", "100.00", "260.00", "PARTIALLY_RECEIVED");

		JsonNode sales = data(get("/api/admin/reports/sales-summary?dateFrom=" + salesDate + "&dateTo=" + salesDate
				+ "&customerId=" + fixture.customerId() + "&materialId=" + fixture.finishedMaterialId()
				+ "&keyword=" + postedShipment.shipmentNo(), admin));
		assertThat(sales.get("summary").get("shipmentQuantity").asText()).isEqualTo("3.000");
		assertThat(sales.get("summary").get("shipmentAmount").asText()).isEqualTo("360.00");
		assertThat(sales.get("summary").get("receivableAmount").asText()).isEqualTo("360.00");
		assertThat(sales.get("summary").get("receivedAmount").asText()).isEqualTo("100.00");
		assertThat(sales.get("summary").get("unreceivedAmount").asText()).isEqualTo("260.00");
		assertThat(sales.get("summary").get("sourceCount").intValue()).isOne();
		assertThat(sales.get("items").size()).isOne();
		JsonNode salesItem = sales.get("items").get(0);
		assertThat(salesItem.get("sourceType").asText()).isEqualTo("SALES_SHIPMENT");
		assertThat(salesItem.get("sourceId").longValue()).isEqualTo(postedShipment.shipmentId());
		assertThat(salesItem.get("sourceNo").asText()).isEqualTo(postedShipment.shipmentNo());
		assertThat(salesItem.get("quantity").asText()).isEqualTo("3.000");
		assertThat(salesItem.get("amount").asText()).isEqualTo("360.00");
		assertThat(salesItem.get("traceKey").asText())
			.isEqualTo("sales-summary:SALES_SHIPMENT:" + postedShipment.shipmentId());

		JsonNode salesTrace = data(get("/api/admin/reports/sales-summary/traces?traceKey="
				+ salesItem.get("traceKey").asText(), admin));
		assertThat(salesTrace.get("items").size()).isOne();
		assertThat(salesTrace.get("items").get(0).get("quantity").asText()).isEqualTo("3.000");
		assertThat(salesTrace.get("items").get(0).get("amount").asText()).isEqualTo("360.00");

		JsonNode salesOverview = data(get(
				"/api/admin/reports/overview?dateFrom=" + salesDate + "&dateTo=" + salesDate, admin));
		assertThat(salesOverview.get("salesShipmentAmount").asText()).isEqualTo("360.00");

		PurchaseReceiptFixture postedReceipt = createPurchaseReceipt(fixture, procurementDate, "POSTED",
				"4.000000", "50.000000", "采购报表已过账");
		createPurchaseReceipt(fixture, procurementDate, "DRAFT", "2.000000", "70.000000", "采购报表草稿");
		createPayable(postedReceipt, "200.00", "80.00", "120.00", "PARTIALLY_PAID");

		JsonNode procurement = data(get("/api/admin/reports/procurement-summary?dateFrom=" + procurementDate
				+ "&dateTo=" + procurementDate + "&supplierId=" + fixture.supplierId() + "&materialId="
				+ fixture.rawMaterialId() + "&keyword=" + postedReceipt.receiptNo(), admin));
		assertThat(procurement.get("summary").get("receiptQuantity").asText()).isEqualTo("4.000");
		assertThat(procurement.get("summary").get("receiptAmount").asText()).isEqualTo("200.00");
		assertThat(procurement.get("summary").get("payableAmount").asText()).isEqualTo("200.00");
		assertThat(procurement.get("summary").get("paidAmount").asText()).isEqualTo("80.00");
		assertThat(procurement.get("summary").get("unpaidAmount").asText()).isEqualTo("120.00");
		assertThat(procurement.get("summary").get("sourceCount").intValue()).isOne();
		assertThat(procurement.get("items").size()).isOne();
		JsonNode procurementItem = procurement.get("items").get(0);
		assertThat(procurementItem.get("sourceType").asText()).isEqualTo("PURCHASE_RECEIPT");
		assertThat(procurementItem.get("sourceId").longValue()).isEqualTo(postedReceipt.receiptId());
		assertThat(procurementItem.get("sourceNo").asText()).isEqualTo(postedReceipt.receiptNo());
		assertThat(procurementItem.get("quantity").asText()).isEqualTo("4.000");
		assertThat(procurementItem.get("amount").asText()).isEqualTo("200.00");
		assertThat(procurementItem.get("traceKey").asText())
			.isEqualTo("procurement-summary:PURCHASE_RECEIPT:" + postedReceipt.receiptId());

		JsonNode procurementTrace = data(get("/api/admin/reports/procurement-summary/traces?traceKey="
				+ procurementItem.get("traceKey").asText(), admin));
		assertThat(procurementTrace.get("items").size()).isOne();
		assertThat(procurementTrace.get("items").get(0).get("quantity").asText()).isEqualTo("4.000");
		assertThat(procurementTrace.get("items").get(0).get("amount").asText()).isEqualTo("200.00");

		JsonNode procurementOverview = data(get(
				"/api/admin/reports/overview?dateFrom=" + procurementDate + "&dateTo=" + procurementDate, admin));
		assertThat(procurementOverview.get("purchaseReceiptAmount").asText()).isEqualTo("200.00");
	}

	@Test
	void salesAndProcurementLineFinancialAmountsUseSourceLineAllocation() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 15);
		ReportingFixture fixture = fixture();

		SalesShipmentFixture shipment = createSalesShipment(fixture, date, "POSTED", "1.000000", "100.000000",
				"销售多行分摊");
		SalesShipmentLineFixture secondShipmentLine = addSalesShipmentLine(fixture, shipment, 2,
				fixture.rawMaterialId(), "3.000000", "100.000000", "销售多行分摊二行");
		ReceivableFixture receivable = createReceivable(shipment, "400.00", "100.00", "300.00",
				"PARTIALLY_RECEIVED");
		replaceReceivableSourceAmount(receivable.receivableId(), shipment.shipmentLineId(), "100.00");
		addReceivableSource(receivable.receivableId(), shipment, secondShipmentLine.shipmentLineId(), 2, "300.00");

		JsonNode sales = data(get("/api/admin/reports/sales-summary?dateFrom=" + date + "&dateTo=" + date
				+ "&keyword=" + shipment.shipmentNo(), admin));
		assertThat(sales.get("summary").get("shipmentAmount").asText()).isEqualTo("400.00");
		assertThat(sales.get("summary").get("receivableAmount").asText()).isEqualTo("400.00");
		assertThat(sales.get("summary").get("receivedAmount").asText()).isEqualTo("100.00");
		assertThat(sales.get("summary").get("unreceivedAmount").asText()).isEqualTo("300.00");
		assertThat(sales.get("items").size()).isEqualTo(2);
		JsonNode firstSalesLine = sales.get("items").get(0);
		JsonNode secondSalesLine = sales.get("items").get(1);
		assertThat(firstSalesLine.get("amount").asText()).isEqualTo("100.00");
		assertThat(firstSalesLine.get("receivableAmount").asText()).isEqualTo("100.00");
		assertThat(firstSalesLine.get("receivedAmount").asText()).isEqualTo("25.00");
		assertThat(firstSalesLine.get("unreceivedAmount").asText()).isEqualTo("75.00");
		assertThat(secondSalesLine.get("amount").asText()).isEqualTo("300.00");
		assertThat(secondSalesLine.get("receivableAmount").asText()).isEqualTo("300.00");
		assertThat(secondSalesLine.get("receivedAmount").asText()).isEqualTo("75.00");
		assertThat(secondSalesLine.get("unreceivedAmount").asText()).isEqualTo("225.00");

		JsonNode filteredSales = data(get("/api/admin/reports/sales-summary?dateFrom=" + date + "&dateTo="
				+ date + "&keyword=" + shipment.shipmentNo() + "&materialId=" + fixture.finishedMaterialId(),
				admin));
		assertThat(filteredSales.get("items").size()).isOne();
		assertThat(filteredSales.get("summary").get("shipmentAmount").asText()).isEqualTo("100.00");
		assertThat(filteredSales.get("summary").get("receivableAmount").asText()).isEqualTo("100.00");
		assertThat(filteredSales.get("summary").get("receivedAmount").asText()).isEqualTo("25.00");
		assertThat(filteredSales.get("summary").get("unreceivedAmount").asText()).isEqualTo("75.00");

		PurchaseReceiptFixture receipt = createPurchaseReceipt(fixture, date, "POSTED", "1.000000", "50.000000",
				"采购多行分摊");
		PurchaseReceiptLineFixture secondReceiptLine = addPurchaseReceiptLine(fixture, receipt, 2,
				fixture.finishedMaterialId(), "3.000000", "50.000000", "采购多行分摊二行");
		PayableFixture payable = createPayable(receipt, "200.00", "80.00", "120.00", "PARTIALLY_PAID");
		replacePayableSourceAmount(payable.payableId(), receipt.receiptLineId(), "50.00");
		addPayableSource(payable.payableId(), receipt, secondReceiptLine.receiptLineId(), 2, "150.00");

		JsonNode procurement = data(get("/api/admin/reports/procurement-summary?dateFrom=" + date + "&dateTo="
				+ date + "&keyword=" + receipt.receiptNo(), admin));
		assertThat(procurement.get("summary").get("receiptAmount").asText()).isEqualTo("200.00");
		assertThat(procurement.get("summary").get("payableAmount").asText()).isEqualTo("200.00");
		assertThat(procurement.get("summary").get("paidAmount").asText()).isEqualTo("80.00");
		assertThat(procurement.get("summary").get("unpaidAmount").asText()).isEqualTo("120.00");
		assertThat(procurement.get("items").size()).isEqualTo(2);
		JsonNode firstReceiptLine = procurement.get("items").get(0);
		JsonNode secondReceiptLineItem = procurement.get("items").get(1);
		assertThat(firstReceiptLine.get("amount").asText()).isEqualTo("50.00");
		assertThat(firstReceiptLine.get("payableAmount").asText()).isEqualTo("50.00");
		assertThat(firstReceiptLine.get("paidAmount").asText()).isEqualTo("20.00");
		assertThat(firstReceiptLine.get("unpaidAmount").asText()).isEqualTo("30.00");
		assertThat(secondReceiptLineItem.get("amount").asText()).isEqualTo("150.00");
		assertThat(secondReceiptLineItem.get("payableAmount").asText()).isEqualTo("150.00");
		assertThat(secondReceiptLineItem.get("paidAmount").asText()).isEqualTo("60.00");
		assertThat(secondReceiptLineItem.get("unpaidAmount").asText()).isEqualTo("90.00");

		JsonNode filteredProcurement = data(get("/api/admin/reports/procurement-summary?dateFrom=" + date
				+ "&dateTo=" + date + "&keyword=" + receipt.receiptNo() + "&materialId=" + fixture.rawMaterialId(),
				admin));
		assertThat(filteredProcurement.get("items").size()).isOne();
		assertThat(filteredProcurement.get("summary").get("receiptAmount").asText()).isEqualTo("50.00");
		assertThat(filteredProcurement.get("summary").get("payableAmount").asText()).isEqualTo("50.00");
		assertThat(filteredProcurement.get("summary").get("paidAmount").asText()).isEqualTo("20.00");
		assertThat(filteredProcurement.get("summary").get("unpaidAmount").asText()).isEqualTo("30.00");
	}

	@Test
	void settlementReportAggregatesActiveSettlementDocumentsAndOverviewBalances() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 13);
		ReportingFixture fixture = fixture();
		SalesShipmentFixture shipment = createSalesShipment(fixture, date, "POSTED", "5.000000", "100.000000",
				"往来应收来源");
		PurchaseReceiptFixture receipt = createPurchaseReceipt(fixture, date, "POSTED", "3.000000", "100.000000",
				"往来应付来源");
		ReceivableFixture receivable = createReceivable(shipment, "500.00", "150.00", "350.00",
				"PARTIALLY_RECEIVED");
		PayableFixture payable = createPayable(receipt, "300.00", "100.00", "200.00", "PARTIALLY_PAID");
		createReceipt(fixture.customerId(), receivable.receivableId(), date, "150.00", "POSTED");
		createReceipt(fixture.customerId(), receivable.receivableId(), date, "77.00", "DRAFT");
		createPayment(fixture.supplierId(), payable.payableId(), date, "100.00", "POSTED");
		createPayment(fixture.supplierId(), payable.payableId(), date, "88.00", "CANCELLED");

		JsonNode settlement = data(get("/api/admin/reports/settlement-summary?dateFrom=" + date + "&dateTo="
				+ date + "&keyword=" + receivable.receivableNo(), admin));
		assertThat(settlement.get("summary").get("receivableAmount").asText()).isEqualTo("500.00");
		assertThat(settlement.get("summary").get("receivedAmount").asText()).isEqualTo("150.00");
		assertThat(settlement.get("summary").get("unreceivedAmount").asText()).isEqualTo("350.00");
		assertThat(settlement.get("summary").get("payableAmount").asText()).isEqualTo("0.00");
		assertThat(settlement.get("summary").get("paidAmount").asText()).isEqualTo("0.00");
		assertThat(settlement.get("summary").get("unpaidAmount").asText()).isEqualTo("0.00");
		assertThat(settlement.get("items").size()).isOne();
		JsonNode settlementItem = settlement.get("items").get(0);
		assertThat(settlementItem.get("settlementType").asText()).isEqualTo("RECEIVABLE");
		assertThat(settlementItem.get("sourceId").longValue()).isEqualTo(receivable.receivableId());
		assertThat(settlementItem.get("totalAmount").asText()).isEqualTo("500.00");
		assertThat(settlementItem.get("settledAmount").asText()).isEqualTo("150.00");
		assertThat(settlementItem.get("unsettledAmount").asText()).isEqualTo("350.00");
		assertThat(settlementItem.get("traceKey").asText())
			.isEqualTo("settlement-summary:RECEIVABLE:" + receivable.receivableId());

		JsonNode payableSettlement = data(get("/api/admin/reports/settlement-summary?dateFrom=" + date
				+ "&dateTo=" + date + "&supplierId=" + fixture.supplierId(), admin));
		assertThat(payableSettlement.get("summary").get("payableAmount").asText()).isEqualTo("300.00");
		assertThat(payableSettlement.get("summary").get("paidAmount").asText()).isEqualTo("100.00");
		assertThat(payableSettlement.get("summary").get("unpaidAmount").asText()).isEqualTo("200.00");

		JsonNode trace = data(get("/api/admin/reports/settlement-summary/traces?traceKey="
				+ settlementItem.get("traceKey").asText(), admin));
		assertThat(trace.get("items").size()).isEqualTo(3);
		assertThat(trace.get("items").get(0).get("sourceType").asText()).isEqualTo("RECEIVABLE");
		assertThat(trace.get("items").get(0).get("amount").asText()).isEqualTo("500.00");
		assertThat(trace.get("items").get(1).get("sourceType").asText()).isEqualTo("RECEIPT");
		assertThat(trace.get("items").get(1).get("amount").asText()).isEqualTo("150.00");
		JsonNode salesShipmentTrace = firstItemWithSourceType(trace, "SALES_SHIPMENT");
		assertThat(salesShipmentTrace.get("sourceLineId").longValue()).isEqualTo(shipment.shipmentLineId());
		assertThat(salesShipmentTrace.get("amount").asText()).isEqualTo("500.00");

		JsonNode payableTrace = data(get("/api/admin/reports/settlement-summary/traces?traceKey=settlement-summary:PAYABLE:"
				+ payable.payableId(), admin));
		assertThat(payableTrace.get("items").size()).isEqualTo(3);
		JsonNode purchaseReceiptTrace = firstItemWithSourceType(payableTrace, "PURCHASE_RECEIPT");
		assertThat(purchaseReceiptTrace.get("sourceLineId").longValue()).isEqualTo(receipt.receiptLineId());
		assertThat(purchaseReceiptTrace.get("amount").asText()).isEqualTo("300.00");

		JsonNode overview = data(get("/api/admin/reports/overview?dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(overview.get("receivableBalance").asText()).isEqualTo("350.00");
		assertThat(overview.get("payableBalance").asText()).isEqualTo("200.00");
		assertThat(overview.get("receivedAmount").asText()).isEqualTo("150.00");
		assertThat(overview.get("paidAmount").asText()).isEqualTo("100.00");
	}

	@Test
	void traceRowsAreMaskedWhenUserLacksSourcePermission() throws Exception {
		LocalDate date = LocalDate.of(2026, 6, 14);
		ReportingFixture fixture = fixture();
		SalesShipmentFixture shipment = createSalesShipment(fixture, date, "POSTED", "2.000000", "60.000000",
				"脱敏来源");
		AuthenticatedSession reportOnly = createUserAndLogin("report-sales-only", List.of("report:sales:view"));

		JsonNode sales = data(get("/api/admin/reports/sales-summary?dateFrom=" + date + "&dateTo=" + date,
				reportOnly));
		assertThat(sales.get("items").size()).isOne();

		JsonNode trace = data(get("/api/admin/reports/sales-summary/traces?traceKey=sales-summary:SALES_SHIPMENT:"
				+ shipment.shipmentId(), reportOnly));
		assertThat(trace.get("items").size()).isOne();
		JsonNode masked = trace.get("items").get(0);
		assertThat(masked.get("sourceType").asText()).isEqualTo("SALES_SHIPMENT");
		assertThat(masked.get("sourceId").isNull()).isTrue();
		assertThat(masked.get("sourceNo").isNull()).isTrue();
		assertThat(masked.get("sourceLineId").isNull()).isTrue();
		assertThat(masked.get("businessDate").isNull()).isTrue();
		assertThat(masked.get("status").isNull()).isTrue();
		assertThat(masked.get("quantity").isNull()).isTrue();
		assertThat(masked.get("amount").isNull()).isTrue();
		assertThat(masked.get("resourceRouteName").isNull()).isTrue();
		assertThat(masked.get("resourceRouteParams").isNull()).isTrue();
		assertThat(masked.get("resourceRouteQuery").isNull()).isTrue();
		assertThat(masked.get("canViewResource").booleanValue()).isFalse();
		assertThat(masked.get("restricted").booleanValue()).isTrue();
		assertThat(masked.get("restrictedMessage").asText()).isEqualTo("当前账号没有查看来源详情的权限");
	}

	@Test
	void inventoryStockFlowAggregatesMovementsAndTraces() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 16);
		ReportingFixture fixture = fixture();
		insertStockMovement(fixture, date.minusDays(2), "OPENING", "IN", "10.000000", "库存期初");
		insertStockMovement(fixture, date, "PURCHASE_RECEIPT", "IN", "5.000000", "采购入库");
		insertStockMovement(fixture, date, "SALES_SHIPMENT", "OUT", "2.000000", "销售出库");
		insertStockMovement(fixture, date, "ADJUSTMENT_INCREASE", "IN", "1.000000", "盘盈调整");
		insertStockMovement(fixture, date, "ADJUSTMENT_DECREASE", "OUT", "0.500000", "盘亏调整");

		ResponseEntity<String> response = get("/api/admin/reports/inventory-stock-flow?dateFrom=" + date
				+ "&dateTo=" + date + "&warehouseId=" + fixture.warehouseId() + "&materialId="
				+ fixture.finishedMaterialId(), admin);
		assertOk(response);
		JsonNode report = data(response);
		assertThat(report.get("summary")).as(report.toPrettyString()).isNotNull();

		assertThat(report.get("summary").get("openingQuantity").asText()).isEqualTo("10.000");
		assertThat(report.get("summary").get("inQuantity").asText()).isEqualTo("5.000");
		assertThat(report.get("summary").get("outQuantity").asText()).isEqualTo("2.000");
		assertThat(report.get("summary").get("adjustQuantity").asText()).isEqualTo("0.500");
		assertThat(report.get("summary").get("closingQuantity").asText()).isEqualTo("13.500");
		assertThat(report.get("summary").get("sourceCount").intValue()).isEqualTo(4);
		assertThat(report.get("items").size()).isOne();
		JsonNode item = report.get("items").get(0);
		assertThat(item.get("warehouseId").longValue()).isEqualTo(fixture.warehouseId());
		assertThat(item.get("materialId").longValue()).isEqualTo(fixture.finishedMaterialId());
		assertThat(item.get("openingQuantity").asText()).isEqualTo("10.000");
		assertThat(item.get("inQuantity").asText()).isEqualTo("5.000");
		assertThat(item.get("outQuantity").asText()).isEqualTo("2.000");
		assertThat(item.get("adjustQuantity").asText()).isEqualTo("0.500");
		assertThat(item.get("closingQuantity").asText()).isEqualTo("13.500");
		assertThat(item.get("traceKey").asText())
			.isEqualTo("inventory-stock-flow:" + fixture.warehouseId() + ":" + fixture.finishedMaterialId());

		JsonNode trace = data(get("/api/admin/reports/inventory-stock-flow/traces?traceKey="
				+ item.get("traceKey").asText() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(trace.get("items").size()).isEqualTo(4);
		JsonNode movementTrace = trace.get("items").get(0);
		assertThat(movementTrace.get("sourceType").asText()).isEqualTo("INVENTORY_MOVEMENT");
		assertThat(movementTrace.get("resourceRouteName").asText()).isEqualTo("inventory-movements");
		assertThat(movementTrace.get("resourceRouteParams").size()).isZero();
		assertThat(movementTrace.get("resourceRouteQuery").get("sourceId").longValue())
			.isEqualTo(movementTrace.get("sourceId").longValue());

		JsonNode overview = data(get("/api/admin/reports/overview?dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(overview.get("inventoryInQuantity").asText()).isEqualTo("5.000");
		assertThat(overview.get("inventoryOutQuantity").asText()).isEqualTo("2.000");
		assertThat(overview.get("formalAccounting").booleanValue()).isFalse();
	}

	@Test
	void productionExecutionAggregatesPostedDocumentsAndTraces() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 17);
		ReportingFixture fixture = fixture();
		ProductionFixture production = createProductionWorkOrder(fixture, date, "IN_PROGRESS", "10.000000",
				"生产执行");
		createProductionWorkOrder(fixture, date, "DRAFT", "99.000000", "生产草稿排除");
		createProductionWorkOrder(fixture, date, "CANCELLED", "88.000000", "生产取消排除");
		createMaterialIssue(fixture, production, date, "POSTED", "4.000000");
		createMaterialIssue(fixture, production, date, "DRAFT", "9.000000");
		createWorkReport(production, date, "POSTED", "6.000000", "1.000000");
		createWorkReport(production, date, "DRAFT", "9.000000", "1.000000");
		createCompletionReceipt(fixture, production, date, "POSTED", "5.000000");
		createCompletionReceipt(fixture, production, date, "DRAFT", "8.000000");

		JsonNode report = data(get("/api/admin/reports/production-execution?dateFrom=" + date + "&dateTo="
				+ date + "&workOrderId=" + production.workOrderId() + "&materialId="
				+ fixture.finishedMaterialId() + "&keyword=" + production.workOrderNo(), admin));

		assertThat(report.get("summary").get("workOrderCount").intValue()).isOne();
		assertThat(report.get("summary").get("plannedQuantity").asText()).isEqualTo("10.000");
		assertThat(report.get("summary").get("issuedQuantity").asText()).isEqualTo("4.000");
		assertThat(report.get("summary").get("reportedQuantity").asText()).isEqualTo("7.000");
		assertThat(report.get("summary").get("qualifiedQuantity").asText()).isEqualTo("6.000");
		assertThat(report.get("summary").get("defectiveQuantity").asText()).isEqualTo("1.000");
		assertThat(report.get("summary").get("completionReceiptQuantity").asText()).isEqualTo("5.000");
		assertThat(report.get("summary").get("completionRate").asText()).isEqualTo("50.00");
		assertThat(report.get("summary").get("sourceCount").intValue()).isEqualTo(4);
		assertThat(report.get("items").size()).isOne();
		JsonNode item = report.get("items").get(0);
		assertThat(item.get("workOrderId").longValue()).isEqualTo(production.workOrderId());
		assertThat(item.get("plannedQuantity").asText()).isEqualTo("10.000");
		assertThat(item.get("completionReceiptQuantity").asText()).isEqualTo("5.000");
		assertThat(item.get("traceKey").asText())
			.isEqualTo("production-execution:WORK_ORDER:" + production.workOrderId());

		JsonNode trace = data(get("/api/admin/reports/production-execution/traces?traceKey="
				+ item.get("traceKey").asText() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(trace.get("items").size()).isEqualTo(4);
		assertThat(firstItemWithSourceType(trace, "PRODUCTION_WORK_ORDER").get("sourceId").longValue())
			.isEqualTo(production.workOrderId());
		assertThat(firstItemWithSourceType(trace, "PRODUCTION_MATERIAL_ISSUE").get("quantity").asText())
			.isEqualTo("4.000");
		assertThat(firstItemWithSourceType(trace, "PRODUCTION_WORK_REPORT").get("quantity").asText())
			.isEqualTo("7.000");
		assertThat(firstItemWithSourceType(trace, "PRODUCTION_COMPLETION_RECEIPT").get("quantity").asText())
			.isEqualTo("5.000");

		JsonNode overview = data(get("/api/admin/reports/overview?dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(overview.get("productionPlannedQuantity").asText()).isEqualTo("10.000");
		assertThat(overview.get("productionCompletedQuantity").asText()).isEqualTo("5.000");
	}

	@Test
	void productionExecutionStatusFilterDoesNotIncludeDraftOrCancelledWorkOrders() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 20);
		ReportingFixture fixture = fixture();
		createProductionWorkOrder(fixture, date, "DRAFT", "99.000000", "显式草稿不进经营汇总");
		createProductionWorkOrder(fixture, date, "CANCELLED", "88.000000", "显式取消不进经营汇总");

		for (String status : List.of("DRAFT", "CANCELLED")) {
			JsonNode report = data(get("/api/admin/reports/production-execution?dateFrom=" + date
					+ "&dateTo=" + date + "&materialId=" + fixture.finishedMaterialId() + "&status=" + status,
					admin));

			assertThat(report.get("summary").get("workOrderCount").intValue()).isZero();
			assertThat(report.get("summary").get("plannedQuantity").asText()).isEqualTo("0.000");
			assertThat(report.get("summary").get("completionReceiptQuantity").asText()).isEqualTo("0.000");
			assertThat(report.get("summary").get("sourceCount").intValue()).isZero();
			assertThat(report.get("items").size()).isZero();
		}
	}

	@Test
	void costCollectionAggregatesActiveRecordsAndTraces() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 18);
		ReportingFixture fixture = fixture();
		ProductionFixture production = createProductionWorkOrder(fixture, date, "COMPLETED", "10.000000",
				"成本归集");
		MaterialIssueFixture issue = createMaterialIssue(fixture, production, date, "POSTED", "4.000000");
		WorkReportFixture reportSource = createWorkReport(production, date, "POSTED", "6.000000", "1.000000");
		CompletionReceiptFixture receipt = createCompletionReceipt(fixture, production, date, "POSTED", "5.000000");
		CostRecordFixture materialCost = createCostRecord(fixture, production, date, "MATERIAL",
				"PRODUCTION_MATERIAL_ISSUE", issue.issueNo(), issue.issueId(), issue.issueLineId(),
				production.workOrderMaterialId(), fixture.rawMaterialId(), "4.000000", "25.000000", "100.000000",
				"MANUAL_UNIT_PRICE_QUANTITY", "ACTIVE");
		createCostRecord(fixture, production, date, "LABOR", "PRODUCTION_WORK_REPORT", reportSource.reportNo(),
				reportSource.reportId(), null, null, null, null, null, "60.000000", "MANUAL_AMOUNT", "ACTIVE");
		createCostRecord(fixture, production, date, "MANUFACTURING_OVERHEAD", "PRODUCTION_COMPLETION_RECEIPT",
				receipt.receiptNo(), receipt.receiptId(), null, null, null, null, null, "30.000000",
				"MANUAL_AMOUNT", "ACTIVE");
		createCostRecord(fixture, production, date, "OTHER", "MANUAL_COST_RECORD", "手工成本", null, null, null,
				null, null, null, "10.000000", "MANUAL_AMOUNT", "ACTIVE");
		createCostRecord(fixture, production, date, "OTHER", "MANUAL_COST_RECORD", "作废成本", null, null, null,
				null, null, null, "999.000000", "MANUAL_AMOUNT", "VOIDED");

		JsonNode report = data(get("/api/admin/reports/cost-collection?dateFrom=" + date + "&dateTo=" + date
				+ "&workOrderId=" + production.workOrderId() + "&materialId=" + fixture.finishedMaterialId()
				+ "&keyword=" + production.workOrderNo(), admin));

		assertThat(report.get("summary").get("materialCostAmount").asText()).isEqualTo("100.00");
		assertThat(report.get("summary").get("laborCostAmount").asText()).isEqualTo("60.00");
		assertThat(report.get("summary").get("manufacturingOverheadAmount").asText()).isEqualTo("30.00");
		assertThat(report.get("summary").get("otherCostAmount").asText()).isEqualTo("10.00");
		assertThat(report.get("summary").get("totalCostAmount").asText()).isEqualTo("200.00");
		assertThat(report.get("summary").get("sourceCount").intValue()).isEqualTo(4);
		assertThat(report.get("summary").get("formalAccounting").booleanValue()).isFalse();
		assertThat(report.get("items").size()).isEqualTo(4);
		JsonNode materialItem = firstItemWithText(report, "costType", "MATERIAL");
		assertThat(materialItem.get("costRecordId").longValue()).isEqualTo(materialCost.costRecordId());
		assertThat(materialItem.get("amount").asText()).isEqualTo("100.00");
		assertThat(materialItem.get("formalAccounting").booleanValue()).isFalse();
		assertThat(materialItem.get("traceKey").asText())
			.isEqualTo("cost-collection:COST_RECORD:" + materialCost.costRecordId());
		assertThat(materialItem.get("voucherNo")).isNull();
		assertThat(materialItem.get("accountCode")).isNull();

		JsonNode costTrace = data(get("/api/admin/reports/cost-collection/traces?traceKey="
				+ materialItem.get("traceKey").asText() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(costTrace, "COST_RECORD").get("amount").asText()).isEqualTo("100.00");
		assertThat(firstItemWithSourceType(costTrace, "PRODUCTION_WORK_ORDER").get("sourceId").longValue())
			.isEqualTo(production.workOrderId());
		assertThat(firstItemWithSourceType(costTrace, "PRODUCTION_MATERIAL_ISSUE").get("sourceId").longValue())
			.isEqualTo(issue.issueId());

		JsonNode workOrderTrace = data(get("/api/admin/reports/cost-collection/traces?traceKey=cost-collection:WORK_ORDER:"
				+ production.workOrderId() + "&dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(firstItemWithSourceType(workOrderTrace, "COST_RECORD")).isNotNull();
		assertThat(firstItemWithSourceType(workOrderTrace, "PRODUCTION_WORK_ORDER").get("sourceId").longValue())
			.isEqualTo(production.workOrderId());

		JsonNode overview = data(get("/api/admin/reports/overview?dateFrom=" + date + "&dateTo=" + date, admin));
		assertThat(overview.get("costAmount").asText()).isEqualTo("200.00");
		assertThat(overview.get("formalAccounting").booleanValue()).isFalse();
	}

	@Test
	void costCollectionStatusFilterDoesNotIncludeVoidedRecords() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate date = LocalDate.of(2026, 6, 21);
		ReportingFixture fixture = fixture();
		ProductionFixture production = createProductionWorkOrder(fixture, date, "COMPLETED", "10.000000",
				"作废成本不进经营汇总");
		createCostRecord(fixture, production, date, "OTHER", "MANUAL_COST_RECORD", "作废成本", null, null, null,
				null, null, null, "999.000000", "MANUAL_AMOUNT", "VOIDED");

		JsonNode report = data(get("/api/admin/reports/cost-collection?dateFrom=" + date + "&dateTo=" + date
				+ "&workOrderId=" + production.workOrderId() + "&materialId=" + fixture.finishedMaterialId()
				+ "&status=VOIDED", admin));

		assertThat(report.get("summary").get("materialCostAmount").asText()).isEqualTo("0.00");
		assertThat(report.get("summary").get("laborCostAmount").asText()).isEqualTo("0.00");
		assertThat(report.get("summary").get("manufacturingOverheadAmount").asText()).isEqualTo("0.00");
		assertThat(report.get("summary").get("otherCostAmount").asText()).isEqualTo("0.00");
		assertThat(report.get("summary").get("totalCostAmount").asText()).isEqualTo("0.00");
		assertThat(report.get("summary").get("sourceCount").intValue()).isZero();
		assertThat(report.get("summary").get("formalAccounting").booleanValue()).isFalse();
		assertThat(report.get("items").size()).isZero();
	}

	@Test
	void inventoryProductionAndCostTraceRowsAreMaskedWhenUserLacksSourcePermission() throws Exception {
		LocalDate date = LocalDate.of(2026, 6, 19);
		ReportingFixture fixture = fixture();
		insertStockMovement(fixture, date, "PURCHASE_RECEIPT", "IN", "5.000000", "脱敏库存");
		ProductionFixture production = createProductionWorkOrder(fixture, date, "IN_PROGRESS", "10.000000",
				"脱敏生产");
		MaterialIssueFixture issue = createMaterialIssue(fixture, production, date, "POSTED", "4.000000");
		CostRecordFixture cost = createCostRecord(fixture, production, date, "MATERIAL",
				"PRODUCTION_MATERIAL_ISSUE", issue.issueNo(), issue.issueId(), issue.issueLineId(),
				production.workOrderMaterialId(), fixture.rawMaterialId(), "4.000000", "25.000000", "100.000000",
				"MANUAL_UNIT_PRICE_QUANTITY", "ACTIVE");
		AuthenticatedSession reportOnly = createUserAndLogin("report-inventory-production-cost-only",
				List.of("report:inventory:view", "report:production:view", "report:cost:view"));

		JsonNode inventoryTrace = data(get("/api/admin/reports/inventory-stock-flow/traces?traceKey=inventory-stock-flow:"
				+ fixture.warehouseId() + ":" + fixture.finishedMaterialId() + "&dateFrom=" + date + "&dateTo="
				+ date, reportOnly));
		assertThat(inventoryTrace.get("items").size()).isOne();
		assertMaskedTraceRow(inventoryTrace.get("items").get(0), "INVENTORY_MOVEMENT");

		JsonNode productionTrace = data(get("/api/admin/reports/production-execution/traces?traceKey=production-execution:WORK_ORDER:"
				+ production.workOrderId() + "&dateFrom=" + date + "&dateTo=" + date, reportOnly));
		assertMaskedTraceRow(firstItemWithSourceType(productionTrace, "PRODUCTION_WORK_ORDER"),
				"PRODUCTION_WORK_ORDER");

		JsonNode costTrace = data(get("/api/admin/reports/cost-collection/traces?traceKey=cost-collection:COST_RECORD:"
				+ cost.costRecordId() + "&dateFrom=" + date + "&dateTo=" + date, reportOnly));
		assertMaskedTraceRow(firstItemWithSourceType(costTrace, "COST_RECORD"), "COST_RECORD");
	}

	@Test
	void exceptionsReportAggregatesSourcesFiltersAndTraces() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate today = LocalDate.now();
		LocalDate dateFrom = today.minusDays(6);
		LocalDate dateTo = today.plusDays(7);
		LocalDate overdueDate = today.minusDays(3);
		ReportingFixture fixture = fixture();

		SalesOrderFixture overdueSales = createSalesOrder(fixture, overdueDate.minusDays(1), overdueDate,
				"CONFIRMED", "5.000000", "1.000000", "120.000000", "销售逾期异常");
		insertStockBalance(fixture, fixture.finishedMaterialId(), "100.000000");
		PurchaseOrderFixture overduePurchase = createPurchaseOrder(fixture, overdueDate.minusDays(1), overdueDate,
				"CONFIRMED", "4.000000", "0.000000", "80.000000", "采购逾期异常");
		createProductionWorkOrder(fixture, today.minusDays(1), "RELEASED", "6.000000", "库存不足异常");
		ProductionFixture overdueProduction = createProductionWorkOrder(fixture, overdueDate.minusDays(4),
				"IN_PROGRESS", "7.000000", "生产逾期异常");
		ProductionFixture missingCost = createProductionWorkOrder(fixture, today.minusDays(2), "IN_PROGRESS",
				"3.000000", "成本缺失异常");
		createMaterialIssue(fixture, missingCost, today.minusDays(1), "POSTED", "1.000000");
		SalesShipmentFixture receivableShipment = createSalesShipment(fixture, today.minusDays(20), "POSTED",
				"1.000000", "100.000000", "应收逾期异常");
		ReceivableFixture overdueReceivable = createReceivable(receivableShipment, "100.00", "0.00", "100.00",
				"CONFIRMED");
		PurchaseReceiptFixture payableReceipt = createPurchaseReceipt(fixture, today.minusDays(15), "POSTED",
				"1.000000", "50.000000", "应付临期异常");
		PayableFixture dueSoonPayable = createPayable(payableReceipt, "50.00", "0.00", "50.00", "CONFIRMED");

		String period = "?dateFrom=" + dateFrom + "&dateTo=" + dateTo;
		String reportFilter = period + "&keyword=" + fixture.marker();
		JsonNode report = data(get("/api/admin/reports/exceptions" + reportFilter, admin));

		assertThat(report.get("summary").get("exceptionCount").intValue()).isEqualTo(7);
		assertThat(report.get("summary").get("criticalCount").intValue()).isEqualTo(4);
		assertThat(report.get("summary").get("warningCount").intValue()).isEqualTo(3);
		assertThat(report.get("summary").get("countsByType").get("SALES_DELIVERY_OVERDUE").intValue()).isOne();
		assertThat(report.get("summary").get("countsByType").get("PROCUREMENT_RECEIPT_OVERDUE").intValue())
			.isOne();
		assertThat(report.get("summary").get("countsByType").get("INVENTORY_SHORTAGE").intValue()).isOne();
		assertThat(report.get("summary").get("countsByType").get("PRODUCTION_OVERDUE").intValue()).isOne();
		assertThat(report.get("summary").get("countsByType").get("COST_MISSING").intValue()).isOne();
		assertThat(report.get("summary").get("countsByType").get("RECEIVABLE_OVERDUE").intValue()).isOne();
		assertThat(report.get("summary").get("countsByType").get("PAYABLE_DUE_SOON").intValue()).isOne();
		assertThat(report.get("items").size()).isEqualTo(7);
		assertThat(report.get("total").intValue()).isEqualTo(7);

		JsonNode inventoryShortage = firstExceptionWithType(report, "INVENTORY_SHORTAGE");
		String inventoryTraceKey = "exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:" + fixture.warehouseId() + ":"
				+ fixture.rawMaterialId();
		assertThat(inventoryShortage.get("sourceType").asText()).isEqualTo("INVENTORY_BALANCE");
		assertThat(inventoryShortage.get("traceKey").asText()).isEqualTo(inventoryTraceKey);
		assertThat(inventoryShortage.get("canViewResource").booleanValue()).isTrue();

		JsonNode filtered = data(get("/api/admin/reports/exceptions" + reportFilter + "&type=INVENTORY_SHORTAGE",
				admin));
		assertThat(filtered.get("summary").get("exceptionCount").intValue()).isOne();
		assertThat(filtered.get("summary").get("countsByType").get("INVENTORY_SHORTAGE").intValue()).isOne();
		assertThat(filtered.get("items").size()).isOne();

		JsonNode salesTrace = data(get("/api/admin/reports/exceptions/traces?traceKey="
				+ firstExceptionWithType(report, "SALES_DELIVERY_OVERDUE").get("traceKey").asText() + "&dateFrom="
				+ dateFrom + "&dateTo=" + dateTo, admin));
		JsonNode salesSource = firstItemWithSourceType(salesTrace, "SALES_ORDER");
		assertThat(salesSource.get("sourceId").longValue()).isEqualTo(overdueSales.orderId());
		assertThat(salesSource.get("resourceRouteName").asText()).isEqualTo("sales-order-detail");

		JsonNode purchaseTrace = data(get("/api/admin/reports/exceptions/traces?traceKey="
				+ firstExceptionWithType(report, "PROCUREMENT_RECEIPT_OVERDUE").get("traceKey").asText()
				+ "&dateFrom=" + dateFrom + "&dateTo=" + dateTo, admin));
		assertThat(firstItemWithSourceType(purchaseTrace, "PURCHASE_ORDER").get("sourceId").longValue())
			.isEqualTo(overduePurchase.orderId());

		JsonNode inventoryTrace = data(get("/api/admin/reports/exceptions/traces?traceKey=" + inventoryTraceKey
				+ "&dateFrom=" + dateFrom + "&dateTo=" + dateTo, admin));
		JsonNode inventorySource = firstItemWithSourceType(inventoryTrace, "INVENTORY_BALANCE");
		assertThat(inventorySource.get("resourceRouteName").asText()).isEqualTo("inventory-balances");
		assertThat(inventorySource.get("resourceRouteQuery").get("warehouseId").longValue())
			.isEqualTo(fixture.warehouseId());
		assertThat(inventorySource.get("resourceRouteQuery").get("materialId").longValue())
			.isEqualTo(fixture.rawMaterialId());

		JsonNode productionTrace = data(get("/api/admin/reports/exceptions/traces?traceKey="
				+ firstExceptionWithType(report, "PRODUCTION_OVERDUE").get("traceKey").asText() + "&dateFrom="
				+ dateFrom + "&dateTo=" + dateTo, admin));
		assertThat(firstItemWithSourceType(productionTrace, "PRODUCTION_WORK_ORDER").get("sourceId").longValue())
			.isEqualTo(overdueProduction.workOrderId());

		JsonNode costTrace = data(get("/api/admin/reports/exceptions/traces?traceKey="
				+ firstExceptionWithType(report, "COST_MISSING").get("traceKey").asText() + "&dateFrom=" + dateFrom
				+ "&dateTo=" + dateTo, admin));
		assertThat(firstItemWithSourceType(costTrace, "PRODUCTION_WORK_ORDER").get("sourceId").longValue())
			.isEqualTo(missingCost.workOrderId());

		JsonNode receivableTrace = data(get("/api/admin/reports/exceptions/traces?traceKey="
				+ firstExceptionWithType(report, "RECEIVABLE_OVERDUE").get("traceKey").asText() + "&dateFrom="
				+ dateFrom + "&dateTo=" + dateTo, admin));
		assertThat(firstItemWithSourceType(receivableTrace, "RECEIVABLE").get("sourceId").longValue())
			.isEqualTo(overdueReceivable.receivableId());

		JsonNode payableTrace = data(get("/api/admin/reports/exceptions/traces?traceKey="
				+ firstExceptionWithType(report, "PAYABLE_DUE_SOON").get("traceKey").asText() + "&dateFrom="
				+ dateFrom + "&dateTo=" + dateTo, admin));
		assertThat(firstItemWithSourceType(payableTrace, "PAYABLE").get("sourceId").longValue())
			.isEqualTo(dueSoonPayable.payableId());

		JsonNode overview = data(get("/api/admin/reports/overview" + reportFilter, admin));
		assertThat(overview.get("exceptionCount").intValue()).isEqualTo(7);
		assertThat(overview.get("formalAccounting").booleanValue()).isFalse();

		assertError(get(
				"/api/admin/reports/exceptions/traces?traceKey=exceptions:SALES_DELIVERY_OVERDUE:PAYABLE:"
						+ dueSoonPayable.payableId() + "&dateFrom=" + dateFrom + "&dateTo=" + dateTo,
				admin), HttpStatus.BAD_REQUEST, "REPORT_TRACE_KEY_INVALID");
	}

	@Test
	void exceptionsListAndTraceAreMaskedWhenUserLacksSourcePermission() throws Exception {
		LocalDate today = LocalDate.now();
		LocalDate dateFrom = today.minusDays(3);
		LocalDate dateTo = today.plusDays(1);
		LocalDate overdueDate = today.minusDays(2);
		ReportingFixture fixture = fixture();
		SalesOrderFixture overdueSales = createSalesOrder(fixture, overdueDate.minusDays(1), overdueDate,
				"CONFIRMED", "2.000000", "0.000000", "90.000000", "异常脱敏销售");
		insertStockBalance(fixture, fixture.finishedMaterialId(), "10.000000");
		AuthenticatedSession reportOnly = createUserAndLogin("report-exception-only",
				List.of("report:exception:view"));

		JsonNode report = data(get("/api/admin/reports/exceptions?dateFrom=" + dateFrom + "&dateTo=" + dateTo
				+ "&keyword=" + fixture.marker(), reportOnly));
		JsonNode item = firstExceptionWithType(report, "SALES_DELIVERY_OVERDUE");
		assertMaskedExceptionItem(item, "SALES_DELIVERY_OVERDUE");

		JsonNode trace = data(get("/api/admin/reports/exceptions/traces?traceKey=exceptions:SALES_DELIVERY_OVERDUE:SALES_ORDER:"
				+ overdueSales.orderId() + "&dateFrom=" + dateFrom + "&dateTo=" + dateTo, reportOnly));
		assertMaskedTraceRow(firstItemWithSourceType(trace, "SALES_ORDER"), "SALES_ORDER");
	}

	@Test
	void inventoryShortageIsReportedForSalesDemandWithoutStockBalanceOrMovement() throws Exception {
		AuthenticatedSession admin = login("admin", ADMIN_PASSWORD);
		LocalDate today = LocalDate.now();
		LocalDate dateFrom = today.minusDays(3);
		LocalDate dateTo = today.plusDays(1);
		LocalDate overdueDate = today.minusDays(1);
		ReportingFixture fixture = fixture();
		createSalesOrder(fixture, overdueDate.minusDays(1), overdueDate, "CONFIRMED", "2.000000", "0.000000",
				"90.000000", "无余额销售需求库存不足");

		String traceKey = "exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:" + fixture.warehouseId() + ":"
				+ fixture.finishedMaterialId();
		JsonNode report = data(get("/api/admin/reports/exceptions?dateFrom=" + dateFrom + "&dateTo=" + dateTo
				+ "&type=INVENTORY_SHORTAGE&keyword=" + fixture.marker(), admin));

		assertThat(report.get("summary").get("exceptionCount").intValue()).isOne();
		JsonNode item = firstExceptionWithType(report, "INVENTORY_SHORTAGE");
		assertThat(item.get("sourceType").asText()).isEqualTo("INVENTORY_BALANCE");
		assertThat(item.get("traceKey").asText()).isEqualTo(traceKey);

		JsonNode trace = data(get("/api/admin/reports/exceptions/traces?traceKey=" + traceKey + "&dateFrom="
				+ dateFrom + "&dateTo=" + dateTo, admin));
		assertThat(firstItemWithSourceType(trace, "INVENTORY_BALANCE").get("resourceRouteQuery").get("warehouseId")
			.longValue()).isEqualTo(fixture.warehouseId());
	}

	private void assertEmptyReportPage(ResponseEntity<String> response) throws Exception {
		assertOk(response);
		JsonNode data = data(response);
		assertThat(data.get("summary")).isNotNull();
		assertThat(data.get("items").size()).isZero();
		assertThat(data.get("page").intValue()).isOne();
		assertThat(data.get("pageSize").intValue()).isEqualTo(20);
		assertThat(data.get("total").longValue()).isZero();
		assertThat(data.get("totalPages").intValue()).isZero();
	}

	private void assertEmptyPage(ResponseEntity<String> response) throws Exception {
		assertOk(response);
		JsonNode data = data(response);
		assertThat(data.get("items").size()).isZero();
		assertThat(data.get("page").intValue()).isOne();
		assertThat(data.get("pageSize").intValue()).isEqualTo(20);
		assertThat(data.get("total").longValue()).isZero();
		assertThat(data.get("totalPages").intValue()).isZero();
	}

	private JsonNode firstItemWithSourceType(JsonNode page, String sourceType) {
		for (JsonNode item : page.get("items")) {
			if (sourceType.equals(item.get("sourceType").asText())) {
				return item;
			}
		}
		throw new AssertionError("缺少追溯来源类型: " + sourceType);
	}

	private JsonNode firstItemWithText(JsonNode page, String field, String value) {
		for (JsonNode item : page.get("items")) {
			if (value.equals(item.get(field).asText())) {
				return item;
			}
		}
		throw new AssertionError("缺少字段 " + field + "=" + value + " 的报表行");
	}

	private JsonNode firstExceptionWithType(JsonNode page, String exceptionType) {
		for (JsonNode item : page.get("items")) {
			if (exceptionType.equals(item.get("exceptionType").asText())) {
				return item;
			}
		}
		throw new AssertionError("缺少异常类型: " + exceptionType);
	}

	private void assertMaskedTraceRow(JsonNode masked, String sourceType) {
		assertThat(masked.get("sourceType").asText()).isEqualTo(sourceType);
		assertThat(masked.get("sourceId").isNull()).isTrue();
		assertThat(masked.get("sourceNo").isNull()).isTrue();
		assertThat(masked.get("sourceLineId").isNull()).isTrue();
		assertThat(masked.get("businessDate").isNull()).isTrue();
		assertThat(masked.get("status").isNull()).isTrue();
		assertThat(masked.get("quantity").isNull()).isTrue();
		assertThat(masked.get("amount").isNull()).isTrue();
		assertThat(masked.get("resourceRouteName").isNull()).isTrue();
		assertThat(masked.get("resourceRouteParams").isNull()).isTrue();
		assertThat(masked.get("resourceRouteQuery").isNull()).isTrue();
		assertThat(masked.get("canViewResource").booleanValue()).isFalse();
		assertThat(masked.get("restricted").booleanValue()).isTrue();
		assertThat(masked.get("restrictedMessage").asText()).isEqualTo("当前账号没有查看来源详情的权限");
	}

	private void assertMaskedExceptionItem(JsonNode masked, String exceptionType) {
		assertThat(masked.get("exceptionType").asText()).isEqualTo(exceptionType);
		assertThat(masked.get("severity").asText()).isNotBlank();
		assertThat(masked.get("description").asText()).isNotBlank();
		assertThat(masked.get("sourceCount").intValue()).isGreaterThan(0);
		assertThat(masked.get("sourceId").isNull()).isTrue();
		assertThat(masked.get("sourceNo").isNull()).isTrue();
		assertThat(masked.get("businessDate").isNull()).isTrue();
		assertThat(masked.get("objectName").isNull()).isTrue();
		assertThat(masked.get("traceKey").isNull()).isTrue();
		assertThat(masked.get("canViewResource").booleanValue()).isFalse();
	}

	private ReportingFixture fixture() {
		int suffix = SEQUENCE.incrementAndGet();
		String marker = "RPTMARK-" + suffix + "-";
		long unitId = insertUnit("RPT_UNIT_" + suffix, "报表单位" + marker);
		long warehouseId = insertWarehouse("RPT_WH_" + suffix, "报表仓库" + marker);
		long customerId = insertCustomer("RPT_CUS_" + suffix, "报表客户" + marker);
		long supplierId = insertSupplier("RPT_SUP_" + suffix, "报表供应商" + marker);
		long categoryId = insertMaterialCategory("RPT_CAT_" + suffix, "报表分类" + marker);
		long finishedMaterialId = insertMaterial("RPT_FIN_" + suffix, "报表成品" + marker, "FINISHED_GOOD",
				"SELF_MADE", categoryId, unitId);
		long rawMaterialId = insertMaterial("RPT_RAW_" + suffix, "报表原料" + marker, "RAW_MATERIAL",
				"PURCHASED", categoryId, unitId);
		return new ReportingFixture(unitId, warehouseId, customerId, supplierId, finishedMaterialId, rawMaterialId,
				marker);
	}

	private SalesShipmentFixture createSalesShipment(ReportingFixture fixture, LocalDate businessDate, String status,
			String quantity, String unitPrice, String remark) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal shippedQuantity = "POSTED".equals(status) ? quantityValue : BigDecimal.ZERO;
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "RPT-SO-" + suffix, fixture.customerId(), businessDate, businessDate.plusDays(3),
				remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.finishedMaterialId(), fixture.unitId(), quantityValue,
				shippedQuantity, new BigDecimal(unitPrice), businessDate.plusDays(3), remark);
		String shipmentNo = "RPT-SH-" + suffix;
		long shipmentId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment (
					shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), ?, case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, shipmentNo, orderId, fixture.customerId(), fixture.warehouseId(), businessDate,
				status, remark, "POSTED".equals(status) ? "test" : null, status);
		long shipmentLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, ?, now(), now())
				returning id
				""", Long.class, shipmentId, orderLineId, fixture.finishedMaterialId(), fixture.unitId(),
				quantityValue, quantityValue, quantityValue, remark);
		return new SalesShipmentFixture(shipmentId, shipmentNo, shipmentLineId, orderId, orderLineId,
				fixture.customerId(), fixture.finishedMaterialId(), businessDate);
	}

	private SalesOrderFixture createSalesOrder(ReportingFixture fixture, LocalDate orderDate,
			LocalDate expectedShipDate, String status, String quantity, String shippedQuantity, String unitPrice,
			String remark) {
		int suffix = SEQUENCE.incrementAndGet();
		String orderNo = "RPT-SO-" + suffix;
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order (
					order_no, customer_id, order_date, expected_ship_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, orderNo, fixture.customerId(), orderDate, expectedShipDate, status, remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.finishedMaterialId(), fixture.unitId(), new BigDecimal(quantity),
				new BigDecimal(shippedQuantity), new BigDecimal(unitPrice), expectedShipDate, remark);
		return new SalesOrderFixture(orderId, orderNo, orderLineId);
	}

	private SalesShipmentLineFixture addSalesShipmentLine(ReportingFixture fixture, SalesShipmentFixture shipment,
			int lineNo, long materialId, String quantity, String unitPrice, String remark) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_order_line (
					order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
					expected_ship_date, remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, shipment.orderId(), lineNo, materialId, fixture.unitId(), quantityValue,
				quantityValue, new BigDecimal(unitPrice), shipment.businessDate().plusDays(3), remark);
		long shipmentLineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_shipment_line (
					shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					shipped_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 0, ?, ?, null, null, ?, now(), now())
				returning id
				""", Long.class, shipment.shipmentId(), lineNo, orderLineId, materialId, fixture.unitId(),
				quantityValue, quantityValue, quantityValue, remark);
		return new SalesShipmentLineFixture(shipmentLineId, orderLineId);
	}

	private PurchaseReceiptFixture createPurchaseReceipt(ReportingFixture fixture, LocalDate businessDate,
			String status, String quantity, String unitPrice, String remark) {
		int suffix = SEQUENCE.incrementAndGet();
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal receivedQuantity = "POSTED".equals(status) ? quantityValue : BigDecimal.ZERO;
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, 'CONFIRMED', ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "RPT-PO-" + suffix, fixture.supplierId(), businessDate,
				businessDate.plusDays(3), remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					expected_arrival_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.rawMaterialId(), fixture.unitId(), quantityValue,
				receivedQuantity, new BigDecimal(unitPrice), businessDate.plusDays(3), remark);
		String receiptNo = "RPT-PR-" + suffix;
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt (
					receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), ?, case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, receiptNo, orderId, fixture.supplierId(), fixture.warehouseId(), businessDate,
				status, remark, "POSTED".equals(status) ? "test" : null, status);
		long receiptLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, ?, ?, null, null, ?, now(), now())
				returning id
				""", Long.class, receiptId, orderLineId, fixture.rawMaterialId(), fixture.unitId(), quantityValue,
				quantityValue, quantityValue, remark);
		return new PurchaseReceiptFixture(receiptId, receiptNo, receiptLineId, orderId, orderLineId,
				fixture.supplierId(), fixture.rawMaterialId(), businessDate);
	}

	private PurchaseOrderFixture createPurchaseOrder(ReportingFixture fixture, LocalDate orderDate,
			LocalDate expectedArrivalDate, String status, String quantity, String receivedQuantity, String unitPrice,
			String remark) {
		int suffix = SEQUENCE.incrementAndGet();
		String orderNo = "RPT-PO-" + suffix;
		long orderId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order (
					order_no, supplier_id, order_date, expected_arrival_date, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, orderNo, fixture.supplierId(), orderDate, expectedArrivalDate, status, remark);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					expected_arrival_date, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, orderId, fixture.rawMaterialId(), fixture.unitId(), new BigDecimal(quantity),
				new BigDecimal(receivedQuantity), new BigDecimal(unitPrice), expectedArrivalDate, remark);
		return new PurchaseOrderFixture(orderId, orderNo, orderLineId);
	}

	private PurchaseReceiptLineFixture addPurchaseReceiptLine(ReportingFixture fixture, PurchaseReceiptFixture receipt,
			int lineNo, long materialId, String quantity, String unitPrice, String remark) {
		BigDecimal quantityValue = new BigDecimal(quantity);
		long orderLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_order_line (
					order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
					expected_arrival_date, remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
				returning id
				""", Long.class, receipt.orderId(), lineNo, materialId, fixture.unitId(), quantityValue,
				quantityValue, new BigDecimal(unitPrice), receipt.businessDate().plusDays(3), remark);
		long receiptLineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_receipt_line (
					receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
					received_quantity_before, remaining_quantity_before, quantity, before_quantity,
					after_quantity, remark, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, 0, ?, ?, null, null, ?, now(), now())
				returning id
				""", Long.class, receipt.receiptId(), lineNo, orderLineId, materialId, fixture.unitId(),
				quantityValue, quantityValue, quantityValue, remark);
		return new PurchaseReceiptLineFixture(receiptLineId, orderLineId);
	}

	private ReceivableFixture createReceivable(SalesShipmentFixture shipment, String totalAmount,
			String receivedAmount, String unreceivedAmount, String status) {
		int suffix = SEQUENCE.incrementAndGet();
		String receivableNo = "RPT-AR-" + suffix;
		long receivableId = this.jdbcTemplate.queryForObject("""
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?, ?, ?, ?, '报表应收',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, receivableNo, shipment.customerId(), shipment.shipmentId(), shipment.shipmentNo(),
				shipment.businessDate(), shipment.businessDate().plusDays(15), new BigDecimal(totalAmount),
				new BigDecimal(receivedAmount), new BigDecimal(unreceivedAmount), status);
		this.jdbcTemplate.update("""
				insert into fin_receivable_source (
					receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'SALES_SHIPMENT', ?, ?, ?, 1, ?)
				""", receivableId, shipment.shipmentId(), shipment.shipmentNo(), shipment.shipmentLineId(),
				new BigDecimal(totalAmount));
		return new ReceivableFixture(receivableId, receivableNo);
	}

	private void replaceReceivableSourceAmount(long receivableId, long sourceLineId, String sourceAmount) {
		this.jdbcTemplate.update("""
				update fin_receivable_source
				set source_amount = ?
				where receivable_id = ?
				and source_line_id = ?
				""", new BigDecimal(sourceAmount), receivableId, sourceLineId);
	}

	private void addReceivableSource(long receivableId, SalesShipmentFixture shipment, long sourceLineId,
			int sourceLineNo, String sourceAmount) {
		this.jdbcTemplate.update("""
				insert into fin_receivable_source (
					receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?)
				""", receivableId, shipment.shipmentId(), shipment.shipmentNo(), sourceLineId, sourceLineNo,
				new BigDecimal(sourceAmount));
	}

	private PayableFixture createPayable(PurchaseReceiptFixture receipt, String totalAmount, String paidAmount,
			String unpaidAmount, String status) {
		int suffix = SEQUENCE.incrementAndGet();
		String payableNo = "RPT-AP-" + suffix;
		long payableId = this.jdbcTemplate.queryForObject("""
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, paid_amount, unpaid_amount, status, remark,
					created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?, ?, ?, ?, '报表应付',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, payableNo, receipt.supplierId(), receipt.receiptId(), receipt.receiptNo(),
				receipt.businessDate(), receipt.businessDate().plusDays(15), new BigDecimal(totalAmount),
				new BigDecimal(paidAmount), new BigDecimal(unpaidAmount), status);
		this.jdbcTemplate.update("""
				insert into fin_payable_source (
					payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, 1, ?)
				""", payableId, receipt.receiptId(), receipt.receiptNo(), receipt.receiptLineId(),
				new BigDecimal(totalAmount));
		return new PayableFixture(payableId, payableNo);
	}

	private void replacePayableSourceAmount(long payableId, long sourceLineId, String sourceAmount) {
		this.jdbcTemplate.update("""
				update fin_payable_source
				set source_amount = ?
				where payable_id = ?
				and source_line_id = ?
				""", new BigDecimal(sourceAmount), payableId, sourceLineId);
	}

	private void addPayableSource(long payableId, PurchaseReceiptFixture receipt, long sourceLineId,
			int sourceLineNo, String sourceAmount) {
		this.jdbcTemplate.update("""
				insert into fin_payable_source (
					payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
				)
				values (?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?)
				""", payableId, receipt.receiptId(), receipt.receiptNo(), sourceLineId, sourceLineNo,
				new BigDecimal(sourceAmount));
	}

	private long createReceipt(long customerId, long receivableId, LocalDate receiptDate, String amount,
			String status) {
		int suffix = SEQUENCE.incrementAndGet();
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'BANK', ?, '报表收款', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, "RPT-REC-" + suffix, customerId, receiptDate, new BigDecimal(amount), status,
				"POSTED".equals(status) ? "test" : null, status);
		this.jdbcTemplate.update("""
				insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
				values (?, ?, ?)
				""", receiptId, receivableId, new BigDecimal(amount));
		return receiptId;
	}

	private long createPayment(long supplierId, long payableId, LocalDate paymentDate, String amount, String status) {
		int suffix = SEQUENCE.incrementAndGet();
		long paymentId = this.jdbcTemplate.queryForObject("""
				insert into fin_payment (
					payment_no, supplier_id, payment_date, amount, method, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at, cancelled_by, cancelled_at
				)
				values (?, ?, ?, ?, 'BANK', ?, '报表付款', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end, ?,
					case when ? = 'CANCELLED' then now() else null end)
				returning id
				""", Long.class, "RPT-PAY-" + suffix, supplierId, paymentDate, new BigDecimal(amount), status,
				"POSTED".equals(status) ? "test" : null, status, "CANCELLED".equals(status) ? "test" : null,
				status);
		this.jdbcTemplate.update("""
				insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
				values (?, ?, ?)
		""", paymentId, payableId, new BigDecimal(amount));
		return paymentId;
	}

	private long insertStockMovement(ReportingFixture fixture, LocalDate businessDate, String movementType,
			String direction, String quantity, String reason) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 100, 100, ?, ?, ?, ?, ?, ?, 'test', now())
				returning id
				""", Long.class, "RPT-MOV-" + suffix, movementType, direction, fixture.warehouseId(),
				fixture.finishedMaterialId(), fixture.unitId(), new BigDecimal(quantity), movementType, suffix,
				suffix, businessDate, reason, reason);
	}

	private void insertStockBalance(ReportingFixture fixture, long materialId, String quantity) {
		this.jdbcTemplate.update("""
				insert into inv_stock_balance (
					warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at
				)
				values (?, ?, ?, ?, 0, now(), now())
				""", fixture.warehouseId(), materialId, fixture.unitId(), new BigDecimal(quantity));
	}

	private ProductionFixture createProductionWorkOrder(ReportingFixture fixture, LocalDate businessDate,
			String status, String plannedQuantity, String remark) {
		int suffix = SEQUENCE.incrementAndGet();
		long bomId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom (
					bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 1, ?, 'DRAFT', 'test', now(), 'test', now())
				returning id
				""", Long.class, "RPT-BOM-" + suffix, fixture.finishedMaterialId(), "V" + suffix, "报表BOM" + suffix,
				fixture.unitId());
		long bomItemId = this.jdbcTemplate.queryForObject("""
				insert into mfg_bom_item (
					bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, 1, 0, ?, now(), now())
				returning id
				""", Long.class, bomId, fixture.rawMaterialId(), fixture.unitId(), remark);
		String workOrderNo = "RPT-WO-" + suffix;
		long workOrderId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order (
					work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity,
					qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
					receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark,
					created_by, created_at, updated_by, updated_at, released_by, released_at
				)
				values (?, ?, ?, ?, 0, 0, 0, 0, ?, ?, ?, ?, ?, ?, 'test', now(), 'test', now(), ?,
					case when ? in ('RELEASED', 'IN_PROGRESS', 'COMPLETED') then now() else null end)
				returning id
				""", Long.class, workOrderNo, fixture.finishedMaterialId(), bomId, new BigDecimal(plannedQuantity),
				fixture.warehouseId(), fixture.warehouseId(), businessDate, businessDate.plusDays(3), status, remark,
				List.of("RELEASED", "IN_PROGRESS", "COMPLETED").contains(status) ? "test" : null, status);
		long workOrderMaterialId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_order_material (
					work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity,
					issued_quantity, loss_rate, remark, created_at, updated_at
				)
				values (?, 1, ?, ?, ?, ?, 0, 0, ?, now(), now())
				returning id
				""", Long.class, workOrderId, bomItemId, fixture.rawMaterialId(), fixture.unitId(),
				new BigDecimal(plannedQuantity), remark);
		return new ProductionFixture(workOrderId, workOrderNo, workOrderMaterialId, fixture.finishedMaterialId(),
				businessDate);
	}

	private MaterialIssueFixture createMaterialIssue(ReportingFixture fixture, ProductionFixture production,
			LocalDate businessDate, String status, String quantity) {
		int suffix = SEQUENCE.incrementAndGet();
		String issueNo = "RPT-ISS-" + suffix;
		long issueId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue (
					issue_no, work_order_id, status, business_date, reason, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, '生产领料', '生产领料', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, issueNo, production.workOrderId(), status, businessDate,
				"POSTED".equals(status) ? "test" : null, status);
		long issueLineId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_issue_line (
					issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, remark, created_at, updated_at
				)
				values (?, ?, 1, ?, ?, ?, ?, 100, 96, '生产领料', now(), now())
				returning id
				""", Long.class, issueId, production.workOrderMaterialId(), fixture.warehouseId(),
				fixture.rawMaterialId(), fixture.unitId(), new BigDecimal(quantity));
		return new MaterialIssueFixture(issueId, issueNo, issueLineId);
	}

	private WorkReportFixture createWorkReport(ProductionFixture production, LocalDate businessDate, String status,
			String qualifiedQuantity, String defectiveQuantity) {
		int suffix = SEQUENCE.incrementAndGet();
		String reportNo = "RPT-WR-" + suffix;
		long reportId = this.jdbcTemplate.queryForObject("""
				insert into mfg_work_report (
					report_no, work_order_id, status, business_date, qualified_quantity, defective_quantity,
					reporter_name, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, '测试报工', '生产报工', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, reportNo, production.workOrderId(), status, businessDate,
				new BigDecimal(qualifiedQuantity), new BigDecimal(defectiveQuantity),
				"POSTED".equals(status) ? "test" : null, status);
		return new WorkReportFixture(reportId, reportNo);
	}

	private CompletionReceiptFixture createCompletionReceipt(ReportingFixture fixture, ProductionFixture production,
			LocalDate businessDate, String status, String quantity) {
		int suffix = SEQUENCE.incrementAndGet();
		String receiptNo = "RPT-CR-" + suffix;
		long receiptId = this.jdbcTemplate.queryForObject("""
				insert into mfg_completion_receipt (
					receipt_no, work_order_id, status, business_date, receipt_warehouse_id, quantity,
					before_quantity, after_quantity, remark, created_by, created_at, updated_by, updated_at,
					posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, 0, ?, '完工入库', 'test', now(), 'test', now(), ?,
					case when ? = 'POSTED' then now() else null end)
				returning id
				""", Long.class, receiptNo, production.workOrderId(), status, businessDate, fixture.warehouseId(),
				new BigDecimal(quantity), new BigDecimal(quantity), "POSTED".equals(status) ? "test" : null, status);
		return new CompletionReceiptFixture(receiptId, receiptNo);
	}

	private CostRecordFixture createCostRecord(ReportingFixture fixture, ProductionFixture production,
			LocalDate businessDate, String costType, String sourceDocumentType, String sourceDocumentNo,
			Long sourceDocumentId, Long sourceLineId, Long workOrderMaterialId, Long materialId, String quantity,
			String unitPrice, String amount, String basisType, String status) {
		int suffix = SEQUENCE.incrementAndGet();
		String sourceType = "MANUAL_COST_RECORD".equals(sourceDocumentType) ? "MANUAL_ENTRY" : "AUTO_PRODUCTION";
		Long unitId = materialId == null ? null : fixture.unitId();
		long costRecordId = this.jdbcTemplate.queryForObject("""
				insert into mfg_cost_record (
					record_no, work_order_id, product_material_id, cost_type, source_type, source_document_type,
					source_document_no, source_document_id, source_line_id, work_order_material_id, material_id,
					unit_id, quantity, unit_price, amount, basis_type, business_date, status, remark,
					recorded_by, recorded_at, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '报表成本',
					'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "RPT-COST-" + suffix, production.workOrderId(), production.productMaterialId(),
				costType, sourceType, sourceDocumentType, sourceDocumentNo, sourceDocumentId, sourceLineId,
				workOrderMaterialId, materialId, unitId, decimalOrNull(quantity), decimalOrNull(unitPrice),
				decimalOrNull(amount), basisType, businessDate, status);
		return new CostRecordFixture(costRecordId);
	}

	private ReturnLineFixture createPostedSalesReturn(ReportingFixture fixture, SalesShipmentFixture shipment,
			LocalDate businessDate, String quantity, String unitPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		String returnNo = "RPT-SR-" + suffix;
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue);
		long returnId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_return (
					return_no, customer_id, source_shipment_id, source_shipment_no, warehouse_id, business_date,
					status, total_amount, remark, created_by, created_at, updated_by, updated_at, posted_by,
					posted_at
				)
				values (?, ?, ?, ?, ?, ?, 'POSTED', ?, '报表销售退货', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, returnNo, shipment.customerId(), shipment.shipmentId(), shipment.shipmentNo(),
				fixture.warehouseId(), businessDate, amount);
		long lineId = this.jdbcTemplate.queryForObject("""
				insert into sal_sales_return_line (
					return_id, source_shipment_line_id, sales_order_line_id, material_id, unit_id, line_no,
					returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
					created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 1, 0, ?, ?, ?, ?, '报表销售退货', now(), now())
				returning id
				""", Long.class, returnId, shipment.shipmentLineId(), shipment.orderLineId(),
				fixture.finishedMaterialId(), fixture.unitId(), quantityValue, quantityValue, unitPriceValue, amount);
		return new ReturnLineFixture(returnId, returnNo, lineId);
	}

	private ReturnLineFixture createPostedPurchaseReturn(ReportingFixture fixture, PurchaseReceiptFixture receipt,
			LocalDate businessDate, String quantity, String unitPrice) {
		int suffix = SEQUENCE.incrementAndGet();
		String returnNo = "RPT-PRTN-" + suffix;
		BigDecimal quantityValue = new BigDecimal(quantity);
		BigDecimal unitPriceValue = new BigDecimal(unitPrice);
		BigDecimal amount = quantityValue.multiply(unitPriceValue);
		long returnId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_return (
					return_no, supplier_id, source_receipt_id, source_receipt_no, warehouse_id, business_date,
					status, total_amount, remark, created_by, created_at, updated_by, updated_at, posted_by,
					posted_at
				)
				values (?, ?, ?, ?, ?, ?, 'POSTED', ?, '报表采购退货', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, returnNo, receipt.supplierId(), receipt.receiptId(), receipt.receiptNo(),
				fixture.warehouseId(), businessDate, amount);
		long lineId = this.jdbcTemplate.queryForObject("""
				insert into proc_purchase_return_line (
					return_id, source_receipt_line_id, purchase_order_line_id, material_id, unit_id, line_no,
					returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
					created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 1, 0, ?, ?, ?, ?, '报表采购退货', now(), now())
				returning id
				""", Long.class, returnId, receipt.receiptLineId(), receipt.orderLineId(), fixture.rawMaterialId(),
				fixture.unitId(), quantityValue, quantityValue, unitPriceValue, amount);
		return new ReturnLineFixture(returnId, returnNo, lineId);
	}

	private ReturnLineFixture createPostedMaterialReturn(ReportingFixture fixture, ProductionFixture production,
			MaterialIssueFixture issue, LocalDate businessDate, String quantity) {
		int suffix = SEQUENCE.incrementAndGet();
		String returnNo = "RPT-MR-" + suffix;
		BigDecimal quantityValue = new BigDecimal(quantity);
		long returnId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_return (
					return_no, work_order_id, source_issue_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, 'POSTED', '报表生产退料', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, returnNo, production.workOrderId(), issue.issueId(), fixture.warehouseId(),
				businessDate);
		long lineId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_return_line (
					return_id, source_issue_line_id, work_order_material_id, material_id, unit_id, line_no,
					returned_quantity_before, returnable_quantity_before, quantity, reason, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 1, 0, ?, ?, '报表生产退料', now(), now())
				returning id
				""", Long.class, returnId, issue.issueLineId(), production.workOrderMaterialId(),
				fixture.rawMaterialId(), fixture.unitId(), quantityValue, quantityValue);
		return new ReturnLineFixture(returnId, returnNo, lineId);
	}

	private ReturnLineFixture createPostedMaterialSupplement(ReportingFixture fixture, ProductionFixture production,
			LocalDate businessDate, String quantity) {
		int suffix = SEQUENCE.incrementAndGet();
		String supplementNo = "RPT-MS-" + suffix;
		BigDecimal quantityValue = new BigDecimal(quantity);
		long supplementId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_supplement (
					supplement_no, work_order_id, warehouse_id, business_date, status, remark,
					created_by, created_at, updated_by, updated_at, posted_by, posted_at
				)
				values (?, ?, ?, ?, 'POSTED', '报表生产补料', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, supplementNo, production.workOrderId(), fixture.warehouseId(), businessDate);
		long lineId = this.jdbcTemplate.queryForObject("""
				insert into mfg_material_supplement_line (
					supplement_id, work_order_material_id, material_id, unit_id, line_no, issued_quantity_before,
					supplemented_quantity_before, available_stock_quantity_before, quantity, reason, created_at,
					updated_at
				)
				values (?, ?, ?, ?, 1, 0, 0, 100, ?, '报表生产补料', now(), now())
				returning id
				""", Long.class, supplementId, production.workOrderMaterialId(), fixture.rawMaterialId(),
				fixture.unitId(), quantityValue);
		return new ReturnLineFixture(supplementId, supplementNo, lineId);
	}

	private void updateReceivableAdjustment(long receivableId, String adjustedAmount, String unreceivedAmount,
			String status) {
		this.jdbcTemplate.update("""
				update fin_receivable
				set adjusted_amount = ?, unreceived_amount = ?, status = ?, updated_by = 'test', updated_at = now()
				where id = ?
				""", new BigDecimal(adjustedAmount), new BigDecimal(unreceivedAmount), status, receivableId);
	}

	private void updatePayableAdjustment(long payableId, String adjustedAmount, String unpaidAmount, String status) {
		this.jdbcTemplate.update("""
				update fin_payable
				set adjusted_amount = ?, unpaid_amount = ?, status = ?, updated_by = 'test', updated_at = now()
				where id = ?
				""", new BigDecimal(adjustedAmount), new BigDecimal(unpaidAmount), status, payableId);
	}

	private long createSettlementAdjustment(String settlementSide, String adjustmentType, String sourceType,
			long sourceId, long targetId, LocalDate businessDate, String amount) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into fin_settlement_adjustment (
					adjustment_no, settlement_side, adjustment_type, source_type, source_id, target_id,
					business_date, amount, status, remark, created_by, created_at, updated_by, updated_at,
					posted_by, posted_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, 'POSTED', '报表往来冲减', 'test', now(), 'test', now(), 'test', now())
				returning id
				""", Long.class, "RPT-ADJ-" + suffix, settlementSide, adjustmentType, sourceType, sourceId,
				targetId, businessDate, new BigDecimal(amount));
	}

	private long insertStockMovement(ReportingFixture fixture, long materialId, LocalDate businessDate,
			String movementType, String direction, String quantity, String sourceType, long sourceId,
			long sourceLineId, String reason) {
		int suffix = SEQUENCE.incrementAndGet();
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at
				)
				values (?, ?, ?, ?, ?, ?, ?, 100, 100, ?, ?, ?, ?, ?, ?, 'test', now())
				returning id
				""", Long.class, "RPT-MOV-" + suffix, movementType, direction, fixture.warehouseId(), materialId,
				fixture.unitId(), new BigDecimal(quantity), sourceType, sourceId, sourceLineId, businessDate, reason,
				reason);
	}

	private BigDecimal decimalOrNull(String value) {
		return value == null ? null : new BigDecimal(value);
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

	private long insertMaterialCategory(String code, String name) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material_category (code, name, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name);
	}

	private long insertMaterial(String code, String name, String materialType, String sourceType, long categoryId,
			long unitId) {
		return this.jdbcTemplate.queryForObject("""
				insert into mst_material (code, name, specification, material_type, source_type, category_id, unit_id,
					status, created_by, created_at, updated_by, updated_at)
				values (?, ?, '报表规格', ?, ?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, code, name, materialType, sourceType, categoryId, unitId);
	}

	private AuthenticatedSession createUserAndLogin(String usernamePrefix, List<String> permissionCodes) {
		int suffix = SEQUENCE.incrementAndGet();
		String username = usernamePrefix + suffix;
		long roleId = this.jdbcTemplate.queryForObject("""
				insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 0, 'test', now(), 'test', now())
				returning id
				""", Long.class, "REPORT_ROLE_" + suffix, "报表测试角色" + suffix, "报表测试角色" + suffix);
		long userId = this.jdbcTemplate.queryForObject("""
				insert into sys_user (username, password_hash, display_name, status, created_by, created_at, updated_by,
					updated_at)
				values (?, ?, ?, 'ENABLED', 'test', now(), 'test', now())
				returning id
				""", Long.class, username, this.passwordEncoder.encode(ADMIN_PASSWORD), "报表测试用户" + suffix);
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
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(code(response)).isEqualTo("OK");
	}

	private void assertError(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(code(response)).isEqualTo(code);
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

	private record ReportingFixture(long unitId, long warehouseId, long customerId, long supplierId,
			long finishedMaterialId, long rawMaterialId, String marker) {
	}

	private record SalesShipmentFixture(long shipmentId, String shipmentNo, long shipmentLineId, long orderId,
			long orderLineId, long customerId, long materialId, LocalDate businessDate) {
	}

	private record SalesShipmentLineFixture(long shipmentLineId, long orderLineId) {
	}

	private record SalesOrderFixture(long orderId, String orderNo, long orderLineId) {
	}

	private record PurchaseReceiptFixture(long receiptId, String receiptNo, long receiptLineId, long orderId,
			long orderLineId, long supplierId, long materialId, LocalDate businessDate) {
	}

	private record PurchaseReceiptLineFixture(long receiptLineId, long orderLineId) {
	}

	private record PurchaseOrderFixture(long orderId, String orderNo, long orderLineId) {
	}

	private record ReceivableFixture(long receivableId, String receivableNo) {
	}

	private record PayableFixture(long payableId, String payableNo) {
	}

	private record ProductionFixture(long workOrderId, String workOrderNo, long workOrderMaterialId,
			long productMaterialId, LocalDate plannedStartDate) {
	}

	private record MaterialIssueFixture(long issueId, String issueNo, long issueLineId) {
	}

	private record WorkReportFixture(long reportId, String reportNo) {
	}

	private record CompletionReceiptFixture(long receiptId, String receiptNo) {
	}

	private record CostRecordFixture(long costRecordId) {
	}

	private record ReturnLineFixture(long documentId, String documentNo, long lineId) {
	}

}
