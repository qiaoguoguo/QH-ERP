package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class ReportingAdminService {

	private static final int DEFAULT_PAGE = 1;

	private static final int DEFAULT_PAGE_SIZE = 20;

	private static final int MAX_PAGE_SIZE = 100;

	private static final String ZERO_AMOUNT = "0.00";

	private static final String ZERO_QUANTITY = "0.000";

	private static final String ZERO_PERCENT = "0.00";

	private static final String RESTRICTED_MESSAGE = "当前账号没有查看来源详情的权限";

	private static final Set<String> SALES_STATUSES = Set.of("DRAFT", "CONFIRMED", "PARTIALLY_SHIPPED",
			"SHIPPED", "CLOSED", "CANCELLED", "POSTED");

	private static final Set<String> PROCUREMENT_STATUSES = Set.of("DRAFT", "CONFIRMED", "PARTIALLY_RECEIVED",
			"RECEIVED", "CLOSED", "CANCELLED", "POSTED");

	private static final Set<String> PRODUCTION_STATUSES = Set.of("DRAFT", "RELEASED", "IN_PROGRESS",
			"COMPLETED", "CANCELLED", "POSTED");

	private static final Set<String> COST_STATUSES = Set.of("ACTIVE", "VOIDED");

	private static final Set<String> SETTLEMENT_STATUSES = Set.of("DRAFT", "CONFIRMED", "PARTIALLY_RECEIVED",
			"RECEIVED", "PARTIALLY_PAID", "PAID", "CLOSED", "CANCELLED", "POSTED");

	private static final List<String> EXCEPTION_TYPE_ORDER = List.of("SALES_DELIVERY_OVERDUE",
			"PROCUREMENT_RECEIPT_OVERDUE", "INVENTORY_SHORTAGE", "PRODUCTION_OVERDUE", "COST_MISSING",
			"RECEIVABLE_OVERDUE", "PAYABLE_DUE_SOON");

	private static final Set<String> EXCEPTION_TYPES = Set.copyOf(EXCEPTION_TYPE_ORDER);

	private static final Set<String> CRITICAL_EXCEPTION_TYPES = Set.of("INVENTORY_SHORTAGE", "PRODUCTION_OVERDUE",
			"COST_MISSING", "RECEIVABLE_OVERDUE");

	private static final Set<String> ACTIVE_RECEIVABLE_STATUSES = Set.of("CONFIRMED", "PARTIALLY_RECEIVED",
			"RECEIVED", "CLOSED");

	private static final Set<String> ACTIVE_PAYABLE_STATUSES = Set.of("CONFIRMED", "PARTIALLY_PAID", "PAID",
			"CLOSED");

	private final JdbcTemplate jdbcTemplate;

	public ReportingAdminService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public OverviewResponse overview(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, Set.of(), Set.of(), false);
		return new OverviewResponse(new PeriodResponse(query.dateFrom(), query.dateTo()),
				amount(salesShipmentAmount(query)), amount(purchaseReceiptAmount(query)),
				quantity(inventoryInQuantity(query)), quantity(inventoryOutQuantity(query)),
				quantity(productionPlannedQuantity(query)), quantity(productionCompletedQuantity(query)),
				amount(costAmount(query)), amount(receivableBalance(query)),
				amount(payableBalance(query)), amount(receivedAmount(query)), amount(paidAmount(query)),
				exceptionRows(query, null).size(), false);
	}

	public ReportPageResponse<Object> salesSummary(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, SALES_STATUSES, Set.of("customerId", "materialId"), true);
		List<SalesRow> rows = salesRows(query);
		List<SalesSummaryItemResponse> items = rows.stream().map((row) -> toSalesItem(row, query)).toList();
		SalesFinancialTotals financialTotals = salesLineFinancialTotals(rows);
		SalesReversalTotals reversalTotals = salesLineReversalTotals(rows, query);
		BigDecimal originalQuantity = rows.stream()
			.filter(this::isSalesShipmentRow)
			.map(SalesRow::quantity)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal originalAmount = rows.stream()
			.filter(this::isSalesShipmentRow)
			.map(SalesRow::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		SalesSummaryResponse summary = new SalesSummaryResponse(
				quantity(originalQuantity), amount(originalAmount),
				amount(financialTotals.receivableAmount()), amount(financialTotals.receivedAmount()),
				amount(financialTotals.unreceivedAmount()),
				(int) rows.stream().map(SalesRow::sourceId).distinct().count(), amount(originalAmount),
				amount(reversalTotals.returnAmount()), amount(originalAmount.subtract(reversalTotals.returnAmount())),
				quantity(originalQuantity), quantity(reversalTotals.returnQuantity()),
				quantity(originalQuantity.subtract(reversalTotals.returnQuantity())));
		return pageOf(summary, items, query);
	}

	public PageResponse<TraceSourceResponse> salesTraces(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseTraceQuery(parameters);
		validateTraceKey(query.traceKey(), "sales-summary", Set.of("SALES_SHIPMENT", "SALES_RETURN"), 3);
		String[] parts = query.traceKey().split(":", -1);
		String sourceType = parts[1];
		long sourceId = Long.parseLong(parts[2]);
		List<TraceSourceResponse> items = new ArrayList<>();
		if ("SALES_SHIPMENT".equals(sourceType)) {
			items.addAll(salesTraceRows(sourceId).stream()
				.map((row) -> trace(row, "sales:shipment:view", "sales-shipment-detail",
						Map.of("id", row.sourceId()), null))
				.toList());
			items.addAll(salesReturnTraceRows(sourceId, query).stream()
				.map((row) -> trace(row, "sales:return:view", "sales-return-detail",
						Map.of("id", row.sourceId()), null))
				.toList());
		}
		else {
			items.addAll(salesReturnTraceRowsByReturnId(sourceId, query).stream()
				.map((row) -> trace(row, "sales:return:view", "sales-return-detail",
						Map.of("id", row.sourceId()), null))
				.toList());
		}
		requireTraceRows(items);
		return PageResponse.of(pageItems(items, query), query.page(), query.pageSize(), items.size());
	}

	public ReportPageResponse<Object> procurementSummary(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, PROCUREMENT_STATUSES, Set.of("supplierId", "materialId"),
				true);
		List<ProcurementRow> rows = procurementRows(query);
		List<ProcurementSummaryItemResponse> items = rows.stream().map((row) -> toProcurementItem(row, query))
			.toList();
		ProcurementFinancialTotals financialTotals = procurementLineFinancialTotals(rows);
		ProcurementReversalTotals reversalTotals = procurementLineReversalTotals(rows, query);
		BigDecimal originalQuantity = rows.stream()
			.filter(this::isPurchaseReceiptRow)
			.map(ProcurementRow::quantity)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal originalAmount = rows.stream()
			.filter(this::isPurchaseReceiptRow)
			.map(ProcurementRow::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		ProcurementSummaryResponse summary = new ProcurementSummaryResponse(
				quantity(originalQuantity), amount(originalAmount),
				amount(financialTotals.payableAmount()), amount(financialTotals.paidAmount()),
				amount(financialTotals.unpaidAmount()),
				(int) rows.stream().map(ProcurementRow::sourceId).distinct().count(), amount(originalAmount),
				amount(reversalTotals.returnAmount()), amount(originalAmount.subtract(reversalTotals.returnAmount())),
				quantity(originalQuantity), quantity(reversalTotals.returnQuantity()),
				quantity(originalQuantity.subtract(reversalTotals.returnQuantity())));
		return pageOf(summary, items, query);
	}

	public PageResponse<TraceSourceResponse> procurementTraces(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseTraceQuery(parameters);
		validateTraceKey(query.traceKey(), "procurement-summary", Set.of("PURCHASE_RECEIPT", "PURCHASE_RETURN"), 3);
		String[] parts = query.traceKey().split(":", -1);
		String sourceType = parts[1];
		long sourceId = Long.parseLong(parts[2]);
		List<TraceSourceResponse> items = new ArrayList<>();
		if ("PURCHASE_RECEIPT".equals(sourceType)) {
			items.addAll(procurementTraceRows(sourceId).stream()
				.map((row) -> trace(row, "procurement:receipt:view", "procurement-receipt-detail",
						Map.of("id", row.sourceId()), null))
				.toList());
			items.addAll(procurementReturnTraceRows(sourceId, query).stream()
				.map((row) -> trace(row, "procurement:return:view", "procurement-return-detail",
						Map.of("id", row.sourceId()), null))
				.toList());
		}
		else {
			items.addAll(procurementReturnTraceRowsByReturnId(sourceId, query).stream()
				.map((row) -> trace(row, "procurement:return:view", "procurement-return-detail",
						Map.of("id", row.sourceId()), null))
				.toList());
		}
		requireTraceRows(items);
		return PageResponse.of(pageItems(items, query), query.page(), query.pageSize(), items.size());
	}

	public ReportPageResponse<Object> inventoryStockFlow(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, Set.of(), Set.of("warehouseId", "materialId"), true);
		List<InventoryStockFlowRow> rows = inventoryStockFlowRows(query);
		List<InventoryStockFlowItemResponse> items = rows.stream().map(this::toInventoryStockFlowItem).toList();
		InventoryStockFlowSummaryResponse summary = new InventoryStockFlowSummaryResponse(
				quantity(rows.stream().map(InventoryStockFlowRow::openingQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(rows.stream().map(InventoryStockFlowRow::inQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(rows.stream().map(InventoryStockFlowRow::outQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(rows.stream().map(InventoryStockFlowRow::adjustQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(rows.stream().map(InventoryStockFlowRow::closingQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				rows.stream().mapToInt(InventoryStockFlowRow::sourceCount).sum(),
				quantity(rows.stream()
					.map(InventoryStockFlowRow::inboundOriginalQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(InventoryStockFlowRow::inboundReverseQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(InventoryStockFlowRow::inboundNetQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(InventoryStockFlowRow::outboundOriginalQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(InventoryStockFlowRow::outboundReverseQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(InventoryStockFlowRow::outboundNetQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(InventoryStockFlowRow::inventoryNetChangeQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)));
		return pageOf(summary, items, query);
	}

	public PageResponse<TraceSourceResponse> inventoryStockFlowTraces(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseTraceQuery(parameters);
		String traceKey = query.traceKey();
		if (traceKey == null) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		String[] parts = traceKey.split(":", -1);
		if (parts.length != 3 || !"inventory-stock-flow".equals(parts[0]) || !positiveLong(parts[1])
				|| !positiveLong(parts[2])) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		long warehouseId = Long.parseLong(parts[1]);
		long materialId = Long.parseLong(parts[2]);
		List<TraceSourceResponse> items = inventoryTraceRows(warehouseId, materialId, query).stream()
			.map((row) -> trace(row, "inventory:movement:view", "inventory-movements", Map.of(),
					Map.of("sourceId", row.sourceId())))
			.toList();
		requireTraceRows(items);
		return PageResponse.of(pageItems(items, query), query.page(), query.pageSize(), items.size());
	}

	public ReportPageResponse<Object> productionExecution(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, PRODUCTION_STATUSES, Set.of("workOrderId", "materialId"),
				true);
		List<ProductionExecutionRow> rows = productionExecutionRows(query);
		List<ProductionExecutionItemResponse> items = rows.stream().map(this::toProductionExecutionItem).toList();
		BigDecimal plannedQuantity = rows.stream()
			.map(ProductionExecutionRow::plannedQuantity)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal completionQuantity = rows.stream()
			.map(ProductionExecutionRow::completionReceiptQuantity)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		ProductionExecutionSummaryResponse summary = new ProductionExecutionSummaryResponse(rows.size(),
				quantity(plannedQuantity),
				quantity(rows.stream().map(ProductionExecutionRow::issuedQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(rows.stream().map(ProductionExecutionRow::reportedQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(rows.stream().map(ProductionExecutionRow::qualifiedQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(rows.stream().map(ProductionExecutionRow::defectiveQuantity).reduce(BigDecimal.ZERO,
						BigDecimal::add)),
				quantity(completionQuantity), percentage(completionQuantity, plannedQuantity),
				rows.stream().mapToInt(ProductionExecutionRow::sourceCount).sum(),
				quantity(rows.stream()
					.map(ProductionExecutionRow::issuedOriginalQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(ProductionExecutionRow::materialReturnQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(ProductionExecutionRow::materialSupplementQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(rows.stream()
					.map(ProductionExecutionRow::issuedNetQuantity)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				quantity(completionQuantity));
		return pageOf(summary, items, query);
	}

	public PageResponse<TraceSourceResponse> productionExecutionTraces(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseTraceQuery(parameters);
		validateTraceKey(query.traceKey(), "production-execution", Set.of("WORK_ORDER"), 3);
		long workOrderId = Long.parseLong(query.traceKey().split(":", -1)[2]);
		List<TraceSourceResponse> items = productionTraceRows(workOrderId, query);
		requireTraceRows(items);
		return PageResponse.of(pageItems(items, query), query.page(), query.pageSize(), items.size());
	}

	public ReportPageResponse<Object> costCollection(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, COST_STATUSES, Set.of("workOrderId", "materialId"), true);
		List<CostCollectionRow> rows = costCollectionRows(query);
		List<CostCollectionItemResponse> items = rows.stream().map(this::toCostCollectionItem).toList();
		BigDecimal materialAmount = costAmount(rows, "MATERIAL");
		BigDecimal laborAmount = costAmount(rows, "LABOR");
		BigDecimal overheadAmount = costAmount(rows, "MANUFACTURING_OVERHEAD");
		BigDecimal otherAmount = costAmount(rows, "OTHER");
		BigDecimal materialOriginalCost = materialCostAmount(rows, "PRODUCTION_MATERIAL_ISSUE");
		BigDecimal materialReturnCost = materialCostAmount(rows, "PRODUCTION_MATERIAL_RETURN");
		BigDecimal materialSupplementCost = materialCostAmount(rows, "PRODUCTION_MATERIAL_SUPPLEMENT");
		BigDecimal materialNetCost = materialOriginalCost.subtract(materialReturnCost).add(materialSupplementCost);
		BigDecimal totalNetCost = materialNetCost.add(laborAmount).add(overheadAmount).add(otherAmount);
		CostCollectionSummaryResponse summary = new CostCollectionSummaryResponse(amount(materialAmount),
				amount(laborAmount), amount(overheadAmount), amount(otherAmount),
				amount(materialAmount.add(laborAmount).add(overheadAmount).add(otherAmount)), rows.size(), false,
				amount(materialOriginalCost), amount(materialReturnCost), amount(materialSupplementCost),
				amount(materialNetCost), amount(totalNetCost));
		return pageOf(summary, items, query);
	}

	public PageResponse<TraceSourceResponse> costCollectionTraces(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseTraceQuery(parameters);
		validateTraceKey(query.traceKey(), "cost-collection", Set.of("WORK_ORDER", "COST_RECORD"), 3);
		String[] parts = query.traceKey().split(":", -1);
		long sourceId = Long.parseLong(parts[2]);
		List<TraceSourceResponse> items = switch (parts[1]) {
			case "WORK_ORDER" -> costWorkOrderTraceRows(sourceId, query);
			case "COST_RECORD" -> costRecordTraceRows(sourceId, query);
			default -> List.of();
		};
		requireTraceRows(items);
		return PageResponse.of(pageItems(items, query), query.page(), query.pageSize(), items.size());
	}

	public ReportPageResponse<Object> settlementSummary(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, SETTLEMENT_STATUSES, Set.of("customerId", "supplierId"),
				true);
		List<SettlementRow> rows = settlementRows(query);
		List<SettlementSummaryItemResponse> items = rows.stream().map(this::toSettlementItem).toList();
		BigDecimal receivableOriginalAmount = rows.stream()
			.filter((row) -> "RECEIVABLE".equals(row.sourceType()))
			.map(SettlementRow::totalAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal receivableAdjustmentAmount = receivableAdjustmentAmount(query);
		BigDecimal payableOriginalAmount = rows.stream()
			.filter((row) -> "PAYABLE".equals(row.sourceType()))
			.map(SettlementRow::totalAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal payableAdjustmentAmount = payableAdjustmentAmount(query);
		BigDecimal settlementRemainingAmount = rows.stream()
			.filter((row) -> "RECEIVABLE".equals(row.sourceType()) || "PAYABLE".equals(row.sourceType()))
			.map(SettlementRow::unsettledAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		SettlementSummaryResponse summary = new SettlementSummaryResponse(
				amount(rows.stream()
					.filter((row) -> "RECEIVABLE".equals(row.sourceType()))
					.map(SettlementRow::totalAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				amount(rows.stream()
					.filter((row) -> "RECEIVABLE".equals(row.sourceType()))
					.map(SettlementRow::settledAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				amount(rows.stream()
					.filter((row) -> "RECEIVABLE".equals(row.sourceType()))
					.map(SettlementRow::unsettledAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				amount(rows.stream()
					.filter((row) -> "PAYABLE".equals(row.sourceType()))
					.map(SettlementRow::totalAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				amount(rows.stream()
					.filter((row) -> "PAYABLE".equals(row.sourceType()))
					.map(SettlementRow::settledAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				amount(rows.stream()
					.filter((row) -> "PAYABLE".equals(row.sourceType()))
					.map(SettlementRow::unsettledAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add)),
				rows.size(), amount(receivableOriginalAmount), amount(receivableAdjustmentAmount),
				amount(receivableOriginalAmount.subtract(receivableAdjustmentAmount)), amount(payableOriginalAmount),
				amount(payableAdjustmentAmount), amount(payableOriginalAmount.subtract(payableAdjustmentAmount)),
				amount(settlementRemainingAmount));
		return pageOf(summary, items, query);
	}

	public PageResponse<TraceSourceResponse> settlementSummaryTraces(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseTraceQuery(parameters);
		validateTraceKey(query.traceKey(), "settlement-summary", Set.of("RECEIVABLE", "PAYABLE", "RECEIPT",
				"PAYMENT", "SETTLEMENT_ADJUSTMENT"), 3);
		String[] parts = query.traceKey().split(":", -1);
		String sourceType = parts[1];
		long sourceId = Long.parseLong(parts[2]);
		List<TraceSourceResponse> items = switch (sourceType) {
			case "RECEIVABLE" -> receivableTraceRows(sourceId);
			case "PAYABLE" -> payableTraceRows(sourceId);
			case "RECEIPT" -> receiptTraceRows(sourceId);
			case "PAYMENT" -> paymentTraceRows(sourceId);
			case "SETTLEMENT_ADJUSTMENT" -> settlementAdjustmentTraceRows(sourceId);
			default -> List.of();
		};
		requireTraceRows(items);
		return PageResponse.of(pageItems(items, query), query.page(), query.pageSize(), items.size());
	}

	public ReportPageResponse<Object> exceptions(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseReportQuery(parameters, Set.of(), Set.of(), true);
		String type = first(parameters, "type");
		if (hasText(type) && !EXCEPTION_TYPES.contains(type)) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
		List<ExceptionRow> rows = exceptionRows(query, hasText(type) ? type : null);
		ExceptionSummaryResponse summary = exceptionSummary(rows);
		List<ExceptionItemResponse> items = rows.stream().map(this::toExceptionItem).toList();
		return pageOf(summary, items, query);
	}

	public PageResponse<TraceSourceResponse> exceptionTraces(MultiValueMap<String, String> parameters) {
		ReportQuery query = parseTraceQuery(parameters);
		String traceKey = query.traceKey();
		validateExceptionTraceKey(traceKey);
		String exceptionType = traceKey.split(":", -1)[1];
		List<TraceSourceResponse> items = exceptionRows(query, exceptionType).stream()
			.filter((row) -> traceKey.equals(row.traceKey()))
			.map(this::toExceptionTrace)
			.toList();
		requireTraceRows(items);
		return PageResponse.of(pageItems(items, query), query.page(), query.pageSize(), items.size());
	}

	private List<ExceptionRow> exceptionRows(ReportQuery query, String type) {
		List<ExceptionRow> rows = new ArrayList<>();
		if (type == null || "SALES_DELIVERY_OVERDUE".equals(type)) {
			rows.addAll(salesDeliveryOverdueRows(query));
		}
		if (type == null || "PROCUREMENT_RECEIPT_OVERDUE".equals(type)) {
			rows.addAll(procurementReceiptOverdueRows(query));
		}
		if (type == null || "INVENTORY_SHORTAGE".equals(type)) {
			rows.addAll(inventoryShortageRows(query));
		}
		if (type == null || "PRODUCTION_OVERDUE".equals(type)) {
			rows.addAll(productionOverdueRows(query));
		}
		if (type == null || "COST_MISSING".equals(type)) {
			rows.addAll(costMissingRows(query));
		}
		if (type == null || "RECEIVABLE_OVERDUE".equals(type)) {
			rows.addAll(receivableOverdueRows(query));
		}
		if (type == null || "PAYABLE_DUE_SOON".equals(type)) {
			rows.addAll(payableDueSoonRows(query));
		}
		return rows.stream()
			.filter((row) -> matchesExceptionKeyword(row, query.keyword()))
			.sorted(Comparator.comparingInt((ExceptionRow row) -> "CRITICAL".equals(row.severity()) ? 0 : 1)
				.thenComparing(ExceptionRow::businessDate, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(ExceptionRow::exceptionType)
				.thenComparing((row) -> row.sourceNo() == null ? "" : row.sourceNo()))
			.toList();
	}

	private List<ExceptionRow> salesDeliveryOverdueRows(ReportQuery query) {
		LocalDate cutoff = reportCutoff(query);
		if (!query.dateFrom().isBefore(cutoff)) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				select so.id source_id, so.order_no source_no,
					min(coalesce(sol.expected_ship_date, so.expected_ship_date, so.order_date)) business_date,
					(c.name || ' / ' || string_agg(distinct m.name, ', ' order by m.name)) object_name,
					count(sol.id) source_count,
					sum(sol.quantity - sol.shipped_quantity) quantity,
					sum((sol.quantity - sol.shipped_quantity) * sol.unit_price) amount,
					so.status
				from sal_sales_order so
				join sal_sales_order_line sol on sol.order_id = so.id
				join mst_customer c on c.id = so.customer_id
				join mst_material m on m.id = sol.material_id
				where so.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
				and (sol.quantity - sol.shipped_quantity) > 0
				and coalesce(sol.expected_ship_date, so.expected_ship_date, so.order_date) >= ?
				and coalesce(sol.expected_ship_date, so.expected_ship_date, so.order_date) < ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), cutoff));
		appendKeyword(sql, args, query.keyword(), "so.order_no", "c.name", "m.code", "m.name", "so.remark");
		sql.append("""

				group by so.id, so.order_no, c.name, so.status
				order by business_date, so.id
				""");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> exceptionRow(
				"SALES_DELIVERY_OVERDUE", "SALES_ORDER", rs.getLong("source_id"), rs.getString("source_no"),
				null, null, rs.getObject("business_date", LocalDate.class), rs.getString("object_name"),
				"销售订单存在逾期未发数量", rs.getInt("source_count"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("amount"), rs.getString("status"), "sales:order:view", "sales-order-detail",
				Map.of("id", rs.getLong("source_id")), null,
				"exceptions:SALES_DELIVERY_OVERDUE:SALES_ORDER:" + rs.getLong("source_id")), args.toArray());
	}

	private List<ExceptionRow> procurementReceiptOverdueRows(ReportQuery query) {
		LocalDate cutoff = reportCutoff(query);
		if (!query.dateFrom().isBefore(cutoff)) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				select po.id source_id, po.order_no source_no,
					min(coalesce(pol.expected_arrival_date, po.expected_arrival_date, po.order_date)) business_date,
					(s.name || ' / ' || string_agg(distinct m.name, ', ' order by m.name)) object_name,
					count(pol.id) source_count,
					sum(pol.quantity - pol.received_quantity) quantity,
					sum((pol.quantity - pol.received_quantity) * pol.unit_price) amount,
					po.status
				from proc_purchase_order po
				join proc_purchase_order_line pol on pol.order_id = po.id
				join mst_supplier s on s.id = po.supplier_id
				join mst_material m on m.id = pol.material_id
				where po.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				and (pol.quantity - pol.received_quantity) > 0
				and coalesce(pol.expected_arrival_date, po.expected_arrival_date, po.order_date) >= ?
				and coalesce(pol.expected_arrival_date, po.expected_arrival_date, po.order_date) < ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), cutoff));
		appendKeyword(sql, args, query.keyword(), "po.order_no", "s.name", "m.code", "m.name", "po.remark");
		sql.append("""

				group by po.id, po.order_no, s.name, po.status
				order by business_date, po.id
				""");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> exceptionRow(
				"PROCUREMENT_RECEIPT_OVERDUE", "PURCHASE_ORDER", rs.getLong("source_id"),
				rs.getString("source_no"), null, null, rs.getObject("business_date", LocalDate.class),
				rs.getString("object_name"), "采购订单存在逾期未收数量", rs.getInt("source_count"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("amount"), rs.getString("status"),
				"procurement:order:view", "procurement-order-detail", Map.of("id", rs.getLong("source_id")), null,
				"exceptions:PROCUREMENT_RECEIPT_OVERDUE:PURCHASE_ORDER:" + rs.getLong("source_id")),
				args.toArray());
	}

	private List<ExceptionRow> inventoryShortageRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				with sales_demand as (
					select sol.reservation_warehouse_id warehouse_id, sol.material_id,
						sum(sol.quantity - sol.shipped_quantity) demand_quantity,
						count(distinct so.id) source_count
					from sal_sales_order so
					join sal_sales_order_line sol on sol.order_id = so.id
					where so.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
					and (sol.quantity - sol.shipped_quantity) > 0
					and coalesce(sol.expected_ship_date, so.expected_ship_date, so.order_date) between ? and ?
					and sol.reservation_warehouse_id is not null
					group by sol.reservation_warehouse_id, sol.material_id
				),
				issue_totals as (
					select mil.work_order_material_id, coalesce(sum(mil.quantity), 0) issued_quantity
					from mfg_material_issue mi
					join mfg_material_issue_line mil on mil.issue_id = mi.id
					where mi.status = 'POSTED'
					group by mil.work_order_material_id
				),
				production_demand as (
					select wo.issue_warehouse_id warehouse_id, wom.material_id,
						sum(wom.required_quantity - coalesce(i.issued_quantity, 0)) demand_quantity,
						count(distinct wo.id) source_count
					from mfg_work_order wo
					join mfg_work_order_material wom on wom.work_order_id = wo.id
					left join issue_totals i on i.work_order_material_id = wom.id
					where wo.status in ('RELEASED', 'IN_PROGRESS')
					and wo.planned_start_date between ? and ?
					and (wom.required_quantity - coalesce(i.issued_quantity, 0)) > 0
					group by wo.issue_warehouse_id, wom.material_id
				),
				demand as (
					select warehouse_id, material_id, sum(demand_quantity) demand_quantity,
						sum(source_count)::integer source_count
					from (
						select warehouse_id, material_id, demand_quantity, source_count from sales_demand
						union all
						select warehouse_id, material_id, demand_quantity, source_count from production_demand
					) source
					group by warehouse_id, material_id
				)
				select d.warehouse_id, w.code warehouse_code, w.name warehouse_name,
					d.material_id, m.code material_code, m.name material_name,
					greatest(coalesce(sb.quantity_on_hand, 0) - coalesce(r.locked_quantity, 0), 0) quantity_on_hand,
					d.demand_quantity,
					d.demand_quantity
						- greatest(coalesce(sb.quantity_on_hand, 0) - coalesce(r.locked_quantity, 0), 0)
						shortage_quantity,
					d.source_count
				from demand d
				join mst_warehouse w on w.id = d.warehouse_id
				join mst_material m on m.id = d.material_id
				left join inv_stock_balance sb on sb.warehouse_id = d.warehouse_id
					and sb.material_id = d.material_id
					and sb.quality_status = 'QUALIFIED'
				left join (
					select warehouse_id, material_id,
					       sum(quantity - released_quantity - consumed_quantity) locked_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by warehouse_id, material_id
				) r on r.warehouse_id = d.warehouse_id and r.material_id = d.material_id
				where w.status = 'ENABLED'
				and m.status = 'ENABLED'
				and greatest(coalesce(sb.quantity_on_hand, 0) - coalesce(r.locked_quantity, 0), 0)
					< d.demand_quantity
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo(), query.dateFrom(),
				query.dateTo()));
		appendKeyword(sql, args, query.keyword(), "w.code", "w.name", "m.code", "m.name");
		sql.append(" order by w.name, m.name");
		LocalDate businessDate = reportCutoff(query);
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
			long warehouseId = rs.getLong("warehouse_id");
			long materialId = rs.getLong("material_id");
			String sourceNo = rs.getString("warehouse_code") + "/" + rs.getString("material_code");
			String objectName = rs.getString("warehouse_name") + " / " + rs.getString("material_name");
			return exceptionRow("INVENTORY_SHORTAGE", "INVENTORY_BALANCE", null, sourceNo, warehouseId, materialId,
					businessDate, objectName, "库存余额低于已确认需求", rs.getInt("source_count"),
					rs.getBigDecimal("shortage_quantity"), null, "SHORTAGE", "inventory:balance:view",
					"inventory-balances", Map.of(), Map.of("warehouseId", warehouseId, "materialId", materialId),
					"exceptions:INVENTORY_SHORTAGE:INVENTORY_BALANCE:" + warehouseId + ":" + materialId);
		}, args.toArray());
	}

	private List<ExceptionRow> productionOverdueRows(ReportQuery query) {
		LocalDate cutoff = reportCutoff(query);
		if (!query.dateFrom().isBefore(cutoff)) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				with receipt_totals as (
					select work_order_id, coalesce(sum(quantity), 0) completion_quantity
					from mfg_completion_receipt
					where status = 'POSTED'
					and business_date <= ?
					group by work_order_id
				)
				select wo.id source_id, wo.work_order_no source_no, wo.planned_finish_date business_date,
					m.name object_name, wo.planned_quantity - coalesce(r.completion_quantity, 0) quantity,
					wo.status
				from mfg_work_order wo
				join mst_material m on m.id = wo.product_material_id
				left join receipt_totals r on r.work_order_id = wo.id
				where wo.status in ('RELEASED', 'IN_PROGRESS')
				and coalesce(r.completion_quantity, 0) < wo.planned_quantity
				and wo.planned_finish_date >= ?
				and wo.planned_finish_date < ?
				""");
		List<Object> args = new ArrayList<>(List.of(cutoff, query.dateFrom(), cutoff));
		appendKeyword(sql, args, query.keyword(), "wo.work_order_no", "m.code", "m.name", "wo.remark");
		sql.append(" order by wo.planned_finish_date, wo.id");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> exceptionRow("PRODUCTION_OVERDUE",
				"PRODUCTION_WORK_ORDER", rs.getLong("source_id"), rs.getString("source_no"), null, null,
				rs.getObject("business_date", LocalDate.class), rs.getString("object_name"), "生产工单逾期未完工", 1,
				rs.getBigDecimal("quantity"), null, rs.getString("status"), "production:work-order:view",
				"production-work-order-detail", Map.of("id", rs.getLong("source_id")), null,
				"exceptions:PRODUCTION_OVERDUE:PRODUCTION_WORK_ORDER:" + rs.getLong("source_id")), args.toArray());
	}

	private List<ExceptionRow> costMissingRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				with posted_sources as (
					select work_order_id, max(business_date) business_date, count(*)::integer source_count
					from (
						select work_order_id, business_date from mfg_material_issue
						where status = 'POSTED' and business_date between ? and ?
						union all
						select work_order_id, business_date from mfg_work_report
						where status = 'POSTED' and business_date between ? and ?
						union all
						select work_order_id, business_date from mfg_completion_receipt
						where status = 'POSTED' and business_date between ? and ?
					) source
					group by work_order_id
				)
				select wo.id source_id, wo.work_order_no source_no, ps.business_date,
					m.name object_name, wo.planned_quantity quantity, wo.status, ps.source_count
				from posted_sources ps
				join mfg_work_order wo on wo.id = ps.work_order_id
				join mst_material m on m.id = wo.product_material_id
				where not exists (
					select 1
					from mfg_cost_record cr
					where cr.work_order_id = wo.id
					and cr.status = 'ACTIVE'
				)
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo(), query.dateFrom(),
				query.dateTo(), query.dateFrom(), query.dateTo()));
		appendKeyword(sql, args, query.keyword(), "wo.work_order_no", "m.code", "m.name", "wo.remark");
		sql.append(" order by ps.business_date, wo.id");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> exceptionRow("COST_MISSING",
				"PRODUCTION_WORK_ORDER", rs.getLong("source_id"), rs.getString("source_no"), null, null,
				rs.getObject("business_date", LocalDate.class), rs.getString("object_name"), "生产工单存在业务执行但未归集成本",
				rs.getInt("source_count"), rs.getBigDecimal("quantity"), null, rs.getString("status"),
				"production:work-order:view", "production-work-order-detail", Map.of("id", rs.getLong("source_id")),
				null, "exceptions:COST_MISSING:PRODUCTION_WORK_ORDER:" + rs.getLong("source_id")), args.toArray());
	}

	private List<ExceptionRow> receivableOverdueRows(ReportQuery query) {
		LocalDate cutoff = reportCutoff(query);
		if (!query.dateFrom().isBefore(cutoff)) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				select r.id source_id, r.receivable_no source_no, r.due_date business_date,
					c.name object_name, r.unreceived_amount amount, r.status
				from fin_receivable r
				join mst_customer c on c.id = r.customer_id
				where r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				and r.unreceived_amount > 0
				and r.due_date >= ?
				and r.due_date < ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), cutoff));
		appendKeyword(sql, args, query.keyword(), "r.receivable_no", "r.source_no", "c.name", "r.remark");
		sql.append(" order by r.due_date, r.id");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> exceptionRow("RECEIVABLE_OVERDUE",
				"RECEIVABLE", rs.getLong("source_id"), rs.getString("source_no"), null, null,
				rs.getObject("business_date", LocalDate.class), rs.getString("object_name"), "应收款项已逾期未收", 1,
				null, rs.getBigDecimal("amount"), rs.getString("status"), "finance:receivable:view",
				"finance-receivable-detail", Map.of("id", rs.getLong("source_id")), null,
				"exceptions:RECEIVABLE_OVERDUE:RECEIVABLE:" + rs.getLong("source_id")), args.toArray());
	}

	private List<ExceptionRow> payableDueSoonRows(ReportQuery query) {
		LocalDate today = LocalDate.now();
		LocalDate dueSoonTo = today.plusDays(7);
		LocalDate dateFrom = query.dateFrom().isAfter(today) ? query.dateFrom() : today;
		LocalDate dateTo = query.dateTo().isBefore(dueSoonTo) ? query.dateTo() : dueSoonTo;
		if (dateFrom.isAfter(dateTo)) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				select p.id source_id, p.payable_no source_no, p.due_date business_date,
					s.name object_name, p.unpaid_amount amount, p.status
				from fin_payable p
				join mst_supplier s on s.id = p.supplier_id
				where p.status in ('CONFIRMED', 'PARTIALLY_PAID')
				and p.unpaid_amount > 0
				and p.due_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(dateFrom, dateTo));
		appendKeyword(sql, args, query.keyword(), "p.payable_no", "p.source_no", "s.name", "p.remark");
		sql.append(" order by p.due_date, p.id");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> exceptionRow("PAYABLE_DUE_SOON",
				"PAYABLE", rs.getLong("source_id"), rs.getString("source_no"), null, null,
				rs.getObject("business_date", LocalDate.class), rs.getString("object_name"), "应付款项即将到期", 1, null,
				rs.getBigDecimal("amount"), rs.getString("status"), "finance:payable:view",
				"finance-payable-detail", Map.of("id", rs.getLong("source_id")), null,
				"exceptions:PAYABLE_DUE_SOON:PAYABLE:" + rs.getLong("source_id")), args.toArray());
	}

	private List<SalesRow> salesRows(ReportQuery query) {
		List<SalesRow> rows = new ArrayList<>();
		rows.addAll(salesShipmentRows(query));
		rows.addAll(salesReturnOnlyRows(query));
		return rows.stream()
			.sorted(Comparator.comparing(SalesRow::businessDate, Comparator.reverseOrder())
				.thenComparing(SalesRow::sourceId, Comparator.reverseOrder())
				.thenComparing(SalesRow::sourceLineId))
			.toList();
	}

	private List<SalesRow> salesShipmentRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select sh.id source_id, sh.shipment_no source_no, sh.business_date, sh.status,
					ssl.id source_line_id, so.id sales_order_id, so.order_no sales_order_no,
					sh.customer_id, c.name customer_name, ssl.material_id, m.name material_name,
					ssl.quantity, sol.unit_price, ssl.quantity * sol.unit_price amount
				from sal_sales_shipment sh
				join sal_sales_shipment_line ssl on ssl.shipment_id = sh.id
				join sal_sales_order so on so.id = sh.order_id
				join sal_sales_order_line sol on sol.id = ssl.order_line_id
				join mst_customer c on c.id = sh.customer_id
				join mst_material m on m.id = ssl.material_id
				where sh.status = 'POSTED'
				and sh.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (hasText(query.status())) {
			sql.append(" and sh.status = ?");
			args.add(query.status());
		}
		if (query.customerId() != null) {
			sql.append(" and sh.customer_id = ?");
			args.add(query.customerId());
		}
		if (query.materialId() != null) {
			sql.append(" and ssl.material_id = ?");
			args.add(query.materialId());
		}
		appendKeyword(sql, args, query.keyword(), "sh.shipment_no", "so.order_no", "c.name", "m.name");
		sql.append(" order by sh.business_date desc, sh.id desc, ssl.line_no");
		return this.jdbcTemplate.query(sql.toString(), this::salesRow, args.toArray());
	}

	private List<SalesRow> salesReturnOnlyRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select sr.id source_id, sr.return_no source_no, sr.business_date, sr.status,
					srl.id source_line_id, so.id sales_order_id, so.order_no sales_order_no,
					sr.customer_id, c.name customer_name, srl.material_id, m.name material_name,
					srl.quantity, srl.unit_price, srl.amount
				from sal_sales_return sr
				join sal_sales_return_line srl on srl.return_id = sr.id
				join sal_sales_shipment sh on sh.id = sr.source_shipment_id
				join sal_sales_order so on so.id = sh.order_id
				join mst_customer c on c.id = sr.customer_id
				join mst_material m on m.id = srl.material_id
				where sr.status = 'POSTED'
				and sr.business_date between ? and ?
				and not (sh.status = 'POSTED' and sh.business_date between ? and ?)
				""");
		List<Object> args = new ArrayList<>(
				List.of(query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo()));
		if (hasText(query.status())) {
			sql.append(" and sr.status = ?");
			args.add(query.status());
		}
		if (query.customerId() != null) {
			sql.append(" and sr.customer_id = ?");
			args.add(query.customerId());
		}
		if (query.materialId() != null) {
			sql.append(" and srl.material_id = ?");
			args.add(query.materialId());
		}
		appendKeyword(sql, args, query.keyword(), "sr.return_no", "sh.shipment_no", "so.order_no", "c.name",
				"m.name");
		sql.append(" order by sr.business_date desc, sr.id desc, srl.line_no");
		return this.jdbcTemplate.query(sql.toString(), this::salesReturnRow, args.toArray());
	}

	private List<ProcurementRow> procurementRows(ReportQuery query) {
		List<ProcurementRow> rows = new ArrayList<>();
		rows.addAll(purchaseReceiptRows(query));
		rows.addAll(purchaseReturnOnlyRows(query));
		return rows.stream()
			.sorted(Comparator.comparing(ProcurementRow::businessDate, Comparator.reverseOrder())
				.thenComparing(ProcurementRow::sourceId, Comparator.reverseOrder())
				.thenComparing(ProcurementRow::sourceLineId))
			.toList();
	}

	private List<ProcurementRow> purchaseReceiptRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select pr.id source_id, pr.receipt_no source_no, pr.business_date, pr.status,
					prl.id source_line_id, po.id purchase_order_id, po.order_no purchase_order_no,
					pr.supplier_id, s.name supplier_name, prl.material_id, m.name material_name,
					prl.quantity, pol.unit_price, prl.quantity * pol.unit_price amount
				from proc_purchase_receipt pr
				join proc_purchase_receipt_line prl on prl.receipt_id = pr.id
				join proc_purchase_order po on po.id = pr.order_id
				join proc_purchase_order_line pol on pol.id = prl.order_line_id
				join mst_supplier s on s.id = pr.supplier_id
				join mst_material m on m.id = prl.material_id
				where pr.status = 'POSTED'
				and pr.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (hasText(query.status())) {
			sql.append(" and pr.status = ?");
			args.add(query.status());
		}
		if (query.supplierId() != null) {
			sql.append(" and pr.supplier_id = ?");
			args.add(query.supplierId());
		}
		if (query.materialId() != null) {
			sql.append(" and prl.material_id = ?");
			args.add(query.materialId());
		}
		appendKeyword(sql, args, query.keyword(), "pr.receipt_no", "po.order_no", "s.name", "m.name");
		sql.append(" order by pr.business_date desc, pr.id desc, prl.line_no");
		return this.jdbcTemplate.query(sql.toString(), this::procurementRow, args.toArray());
	}

	private List<ProcurementRow> purchaseReturnOnlyRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select pr.id source_id, pr.return_no source_no, pr.business_date, pr.status,
					prl.id source_line_id, po.id purchase_order_id, po.order_no purchase_order_no,
					pr.supplier_id, s.name supplier_name, prl.material_id, m.name material_name,
					prl.quantity, prl.unit_price, prl.amount
				from proc_purchase_return pr
				join proc_purchase_return_line prl on prl.return_id = pr.id
				join proc_purchase_receipt receipt on receipt.id = pr.source_receipt_id
				join proc_purchase_order po on po.id = receipt.order_id
				join mst_supplier s on s.id = pr.supplier_id
				join mst_material m on m.id = prl.material_id
				where pr.status = 'POSTED'
				and pr.business_date between ? and ?
				and not (receipt.status = 'POSTED' and receipt.business_date between ? and ?)
				""");
		List<Object> args = new ArrayList<>(
				List.of(query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo()));
		if (hasText(query.status())) {
			sql.append(" and pr.status = ?");
			args.add(query.status());
		}
		if (query.supplierId() != null) {
			sql.append(" and pr.supplier_id = ?");
			args.add(query.supplierId());
		}
		if (query.materialId() != null) {
			sql.append(" and prl.material_id = ?");
			args.add(query.materialId());
		}
		appendKeyword(sql, args, query.keyword(), "pr.return_no", "receipt.receipt_no", "po.order_no", "s.name",
				"m.name");
		sql.append(" order by pr.business_date desc, pr.id desc, prl.line_no");
		return this.jdbcTemplate.query(sql.toString(), this::purchaseReturnRow, args.toArray());
	}

	private List<InventoryStockFlowRow> inventoryStockFlowRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select sm.warehouse_id, w.name warehouse_name, sm.material_id, m.name material_name,
					coalesce(sum(case when sm.business_date < ?
						then case when sm.direction = 'IN' then sm.quantity else -sm.quantity end
						else 0 end), 0) opening_quantity,
					coalesce(sum(case when sm.business_date between ? and ?
						and sm.movement_type not in ('ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE')
						and sm.direction = 'IN' then sm.quantity else 0 end), 0) in_quantity,
					coalesce(sum(case when sm.business_date between ? and ?
						and sm.movement_type not in ('ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE')
						and sm.direction = 'OUT' then sm.quantity else 0 end), 0) out_quantity,
					coalesce(sum(case when sm.business_date between ? and ?
						and sm.movement_type = 'ADJUSTMENT_INCREASE' then sm.quantity
						when sm.business_date between ? and ?
						and sm.movement_type = 'ADJUSTMENT_DECREASE' then -sm.quantity
						else 0 end), 0) adjust_quantity,
					coalesce(sum(case when sm.business_date between ? and ?
						and sm.direction = 'IN'
						and sm.movement_type not in ('ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE',
							'SALES_RETURN_IN', 'PRODUCTION_MATERIAL_RETURN_IN')
						then sm.quantity else 0 end), 0) inbound_original_quantity,
					coalesce(sum(case when sm.business_date between ? and ?
						and sm.movement_type in ('SALES_RETURN_IN', 'PRODUCTION_MATERIAL_RETURN_IN')
						then sm.quantity else 0 end), 0) inbound_reverse_quantity,
					coalesce(sum(case when sm.business_date between ? and ?
						and sm.direction = 'OUT'
						and sm.movement_type not in ('ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE',
							'PURCHASE_RETURN_OUT', 'PRODUCTION_MATERIAL_SUPPLEMENT_OUT')
						then sm.quantity else 0 end), 0) outbound_original_quantity,
					coalesce(sum(case when sm.business_date between ? and ?
						and sm.movement_type in ('PURCHASE_RETURN_OUT', 'PRODUCTION_MATERIAL_SUPPLEMENT_OUT')
						then sm.quantity else 0 end), 0) outbound_reverse_quantity,
					count(*) filter (where sm.business_date between ? and ?) source_count
				from inv_stock_movement sm
				join mst_warehouse w on w.id = sm.warehouse_id
				join mst_material m on m.id = sm.material_id
				where sm.business_date <= ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateFrom(), query.dateTo(),
				query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo(),
				query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo(),
				query.dateFrom(), query.dateTo(),
				query.dateFrom(), query.dateTo(), query.dateTo()));
		if (query.warehouseId() != null) {
			sql.append(" and sm.warehouse_id = ?");
			args.add(query.warehouseId());
		}
		if (query.materialId() != null) {
			sql.append(" and sm.material_id = ?");
			args.add(query.materialId());
		}
		appendKeyword(sql, args, query.keyword(), "w.code", "w.name", "m.code", "m.name");
		sql.append("""

				group by sm.warehouse_id, w.name, sm.material_id, m.name
				having count(*) filter (where sm.business_date between ? and ?) > 0
				order by w.name, m.name
				""");
		args.add(query.dateFrom());
		args.add(query.dateTo());
		return this.jdbcTemplate.query(sql.toString(), this::inventoryStockFlowRow, args.toArray());
	}

	private List<ProductionExecutionRow> productionExecutionRows(ReportQuery query) {
		if (hasText(query.status()) && Set.of("DRAFT", "CANCELLED").contains(query.status())) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				with issue_totals as (
					select mi.work_order_id, coalesce(sum(mil.quantity), 0) issued_quantity,
						count(distinct mi.id) issue_count
					from mfg_material_issue mi
					join mfg_material_issue_line mil on mil.issue_id = mi.id
					where mi.status = 'POSTED'
					and mi.business_date between ? and ?
					group by mi.work_order_id
				),
				report_totals as (
					select wr.work_order_id, coalesce(sum(wr.qualified_quantity + wr.defective_quantity), 0) reported_quantity,
						coalesce(sum(wr.qualified_quantity), 0) qualified_quantity,
						coalesce(sum(wr.defective_quantity), 0) defective_quantity,
						count(*) report_count
					from mfg_work_report wr
					where wr.status = 'POSTED'
					and wr.business_date between ? and ?
					group by wr.work_order_id
				),
				receipt_totals as (
					select cr.work_order_id, coalesce(sum(cr.quantity), 0) completion_receipt_quantity,
						count(*) receipt_count
					from mfg_completion_receipt cr
					where cr.status = 'POSTED'
					and cr.business_date between ? and ?
					group by cr.work_order_id
				),
				material_return_totals as (
					select mr.work_order_id, coalesce(sum(mrl.quantity), 0) material_return_quantity,
						count(distinct mr.id) material_return_count
					from mfg_material_return mr
					join mfg_material_return_line mrl on mrl.return_id = mr.id
					where mr.status = 'POSTED'
					and mr.business_date between ? and ?
					group by mr.work_order_id
				),
				material_supplement_totals as (
					select ms.work_order_id, coalesce(sum(msl.quantity), 0) material_supplement_quantity,
						count(distinct ms.id) material_supplement_count
					from mfg_material_supplement ms
					join mfg_material_supplement_line msl on msl.supplement_id = ms.id
					where ms.status = 'POSTED'
					and ms.business_date between ? and ?
					group by ms.work_order_id
				)
				select wo.id work_order_id, wo.work_order_no, wo.product_material_id, m.name product_material_name,
					case when wo.planned_start_date between ? and ? then wo.planned_quantity else 0 end planned_quantity,
					coalesce(i.issued_quantity, 0) issued_quantity,
					coalesce(i.issued_quantity, 0) issued_original_quantity,
					coalesce(mr.material_return_quantity, 0) material_return_quantity,
					coalesce(ms.material_supplement_quantity, 0) material_supplement_quantity,
					coalesce(r.reported_quantity, 0) reported_quantity,
					coalesce(r.qualified_quantity, 0) qualified_quantity,
					coalesce(r.defective_quantity, 0) defective_quantity,
					coalesce(c.completion_receipt_quantity, 0) completion_receipt_quantity,
					wo.status, wo.planned_start_date, wo.planned_finish_date,
					1 + coalesce(i.issue_count, 0) + coalesce(r.report_count, 0) + coalesce(c.receipt_count, 0)
						+ coalesce(mr.material_return_count, 0) + coalesce(ms.material_supplement_count, 0) source_count
				from mfg_work_order wo
				join mst_material m on m.id = wo.product_material_id
				left join issue_totals i on i.work_order_id = wo.id
				left join report_totals r on r.work_order_id = wo.id
				left join receipt_totals c on c.work_order_id = wo.id
				left join material_return_totals mr on mr.work_order_id = wo.id
				left join material_supplement_totals ms on ms.work_order_id = wo.id
				where (wo.planned_start_date between ? and ?
					or i.work_order_id is not null
					or r.work_order_id is not null
					or c.work_order_id is not null
					or mr.work_order_id is not null
					or ms.work_order_id is not null)
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo(), query.dateFrom(),
				query.dateTo(), query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo(),
				query.dateFrom(), query.dateTo(), query.dateFrom(), query.dateTo(), query.dateFrom(),
				query.dateTo()));
		if (hasText(query.status())) {
			sql.append(" and wo.status = ?");
			args.add(query.status());
		}
		else {
			sql.append(" and wo.status not in ('DRAFT', 'CANCELLED')");
		}
		if (query.workOrderId() != null) {
			sql.append(" and wo.id = ?");
			args.add(query.workOrderId());
		}
		if (query.materialId() != null) {
			sql.append(" and wo.product_material_id = ?");
			args.add(query.materialId());
		}
		appendKeyword(sql, args, query.keyword(), "wo.work_order_no", "m.code", "m.name");
		sql.append(" order by wo.planned_start_date desc, wo.id desc");
		return this.jdbcTemplate.query(sql.toString(), this::productionExecutionRow, args.toArray());
	}

	private List<CostCollectionRow> costCollectionRows(ReportQuery query) {
		if ("VOIDED".equals(query.status())) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				select cr.id cost_record_id, cr.record_no, cr.work_order_id, wo.work_order_no,
					cr.product_material_id, m.name product_material_name, cr.cost_type, cr.source_type,
					cr.source_document_type, cr.source_document_no, cr.source_document_id, cr.source_line_id,
					cr.business_date, cr.quantity, cr.unit_price, cr.amount, cr.basis_type, cr.status
				from mfg_cost_record cr
				join mfg_work_order wo on wo.id = cr.work_order_id
				join mst_material m on m.id = cr.product_material_id
				where cr.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (hasText(query.status())) {
			sql.append(" and cr.status = ?");
			args.add(query.status());
		}
		else {
			sql.append(" and cr.status = 'ACTIVE'");
		}
		if (query.workOrderId() != null) {
			sql.append(" and cr.work_order_id = ?");
			args.add(query.workOrderId());
		}
		if (query.materialId() != null) {
			sql.append(" and cr.product_material_id = ?");
			args.add(query.materialId());
		}
		appendKeyword(sql, args, query.keyword(), "cr.record_no", "wo.work_order_no", "m.code", "m.name",
				"cr.source_document_no");
		sql.append(" order by cr.business_date desc, cr.id desc");
		return this.jdbcTemplate.query(sql.toString(), this::costCollectionRow, args.toArray());
	}

	private List<SettlementRow> settlementRows(ReportQuery query) {
		List<SettlementRow> rows = new ArrayList<>();
		if (query.supplierId() == null) {
			rows.addAll(receivableRows(query));
			rows.addAll(receivableAdjustmentRows(query));
		}
		if (query.customerId() == null) {
			rows.addAll(payableRows(query));
			rows.addAll(payableAdjustmentRows(query));
		}
		return rows.stream()
			.sorted(Comparator.comparing(SettlementRow::businessDate, Comparator.reverseOrder())
				.thenComparing(SettlementRow::sourceId, Comparator.reverseOrder()))
			.toList();
	}

	private List<SettlementRow> receivableRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select r.id, r.receivable_no, r.customer_id party_id, c.name party_name, r.business_date,
					r.due_date, r.total_amount, r.received_amount, r.adjusted_amount, r.unreceived_amount, r.status
				from fin_receivable r
				join mst_customer c on c.id = r.customer_id
				where r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
				and r.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (query.customerId() != null) {
			sql.append(" and r.customer_id = ?");
			args.add(query.customerId());
		}
		if (hasText(query.status())) {
			sql.append(" and r.status = ?");
			args.add(query.status());
		}
		appendKeyword(sql, args, query.keyword(), "r.receivable_no", "r.source_no", "c.name");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> settlementRow(rs, "RECEIVABLE",
				rs.getBigDecimal("received_amount"), rs.getBigDecimal("unreceived_amount")),
				args.toArray());
	}

	private List<SettlementRow> payableRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select p.id, p.payable_no, p.supplier_id party_id, s.name party_name, p.business_date,
					p.due_date, p.total_amount, p.paid_amount, p.adjusted_amount, p.unpaid_amount, p.status
				from fin_payable p
				join mst_supplier s on s.id = p.supplier_id
				where p.status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
				and p.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (query.supplierId() != null) {
			sql.append(" and p.supplier_id = ?");
			args.add(query.supplierId());
		}
		if (hasText(query.status())) {
			sql.append(" and p.status = ?");
			args.add(query.status());
		}
		appendKeyword(sql, args, query.keyword(), "p.payable_no", "p.source_no", "s.name");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> settlementRow(rs, "PAYABLE",
				rs.getBigDecimal("paid_amount"), rs.getBigDecimal("unpaid_amount")),
				args.toArray());
	}

	private List<SettlementRow> receivableAdjustmentRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select fsa.id, fsa.adjustment_no, r.customer_id party_id, c.name party_name, fsa.business_date,
					r.due_date, r.unreceived_amount remaining_amount, fsa.amount adjusted_amount, fsa.status
				from fin_settlement_adjustment fsa
				join fin_receivable r on r.id = fsa.target_id
				join mst_customer c on c.id = r.customer_id
				where fsa.status = 'POSTED'
				and fsa.settlement_side = 'RECEIVABLE'
				and fsa.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (query.customerId() != null) {
			sql.append(" and r.customer_id = ?");
			args.add(query.customerId());
		}
		if (hasText(query.status())) {
			sql.append(" and r.status = ?");
			args.add(query.status());
		}
		appendKeyword(sql, args, query.keyword(), "fsa.adjustment_no", "r.receivable_no", "r.source_no",
				"c.name");
		return this.jdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> settlementAdjustmentRow(rs, "RECEIVABLE", "CUSTOMER"), args.toArray());
	}

	private List<SettlementRow> payableAdjustmentRows(ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select fsa.id, fsa.adjustment_no, p.supplier_id party_id, s.name party_name, fsa.business_date,
					p.due_date, p.unpaid_amount remaining_amount, fsa.amount adjusted_amount, fsa.status
				from fin_settlement_adjustment fsa
				join fin_payable p on p.id = fsa.target_id
				join mst_supplier s on s.id = p.supplier_id
				where fsa.status = 'POSTED'
				and fsa.settlement_side = 'PAYABLE'
				and fsa.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (query.supplierId() != null) {
			sql.append(" and p.supplier_id = ?");
			args.add(query.supplierId());
		}
		if (hasText(query.status())) {
			sql.append(" and p.status = ?");
			args.add(query.status());
		}
		appendKeyword(sql, args, query.keyword(), "fsa.adjustment_no", "p.payable_no", "p.source_no", "s.name");
		return this.jdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> settlementAdjustmentRow(rs, "PAYABLE", "SUPPLIER"), args.toArray());
	}

	private BigDecimal receivableAdjustmentAmount(ReportQuery query) {
		if (query.supplierId() != null) {
			return BigDecimal.ZERO;
		}
		StringBuilder sql = new StringBuilder("""
				select coalesce(sum(fsa.amount), 0)
				from fin_settlement_adjustment fsa
				join fin_receivable r on r.id = fsa.target_id
				join mst_customer c on c.id = r.customer_id
				where fsa.status = 'POSTED'
				and fsa.settlement_side = 'RECEIVABLE'
				and fsa.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (query.customerId() != null) {
			sql.append(" and r.customer_id = ?");
			args.add(query.customerId());
		}
		if (hasText(query.status())) {
			sql.append(" and r.status = ?");
			args.add(query.status());
		}
		appendKeyword(sql, args, query.keyword(), "fsa.adjustment_no", "r.receivable_no", "r.source_no",
				"c.name");
		return sum(sql.toString(), args.toArray());
	}

	private BigDecimal payableAdjustmentAmount(ReportQuery query) {
		if (query.customerId() != null) {
			return BigDecimal.ZERO;
		}
		StringBuilder sql = new StringBuilder("""
				select coalesce(sum(fsa.amount), 0)
				from fin_settlement_adjustment fsa
				join fin_payable p on p.id = fsa.target_id
				join mst_supplier s on s.id = p.supplier_id
				where fsa.status = 'POSTED'
				and fsa.settlement_side = 'PAYABLE'
				and fsa.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(query.dateFrom(), query.dateTo()));
		if (query.supplierId() != null) {
			sql.append(" and p.supplier_id = ?");
			args.add(query.supplierId());
		}
		if (hasText(query.status())) {
			sql.append(" and p.status = ?");
			args.add(query.status());
		}
		appendKeyword(sql, args, query.keyword(), "fsa.adjustment_no", "p.payable_no", "p.source_no", "s.name");
		return sum(sql.toString(), args.toArray());
	}

	private List<TraceSourceRow> salesTraceRows(long shipmentId) {
		return this.jdbcTemplate.query("""
				select 'SALES_SHIPMENT' source_type, sh.id source_id, sh.shipment_no source_no,
					ssl.id source_line_id, sh.business_date, sh.status, ssl.quantity,
					ssl.quantity * sol.unit_price amount
				from sal_sales_shipment sh
				join sal_sales_shipment_line ssl on ssl.shipment_id = sh.id
				join sal_sales_order_line sol on sol.id = ssl.order_line_id
				where sh.status = 'POSTED'
				and sh.id = ?
				order by ssl.line_no
				""", this::traceSourceRow, shipmentId);
	}

	private List<TraceSourceRow> salesReturnTraceRows(long shipmentId, ReportQuery query) {
		return this.jdbcTemplate.query("""
				select 'SALES_RETURN' source_type, sr.id source_id, sr.return_no source_no,
					srl.id source_line_id, sr.business_date, sr.status, srl.quantity, srl.amount
				from sal_sales_return sr
				join sal_sales_return_line srl on srl.return_id = sr.id
				where sr.status = 'POSTED'
				and sr.source_shipment_id = ?
				and sr.business_date between ? and ?
				order by sr.business_date, sr.id, srl.line_no
				""", this::traceSourceRow, shipmentId, query.dateFrom(), query.dateTo());
	}

	private List<TraceSourceRow> salesReturnTraceRowsByReturnId(long returnId, ReportQuery query) {
		return this.jdbcTemplate.query("""
				select 'SALES_RETURN' source_type, sr.id source_id, sr.return_no source_no,
					srl.id source_line_id, sr.business_date, sr.status, srl.quantity, srl.amount
				from sal_sales_return sr
				join sal_sales_return_line srl on srl.return_id = sr.id
				where sr.status = 'POSTED'
				and sr.id = ?
				and sr.business_date between ? and ?
				order by sr.business_date, sr.id, srl.line_no
				""", this::traceSourceRow, returnId, query.dateFrom(), query.dateTo());
	}

	private List<TraceSourceRow> procurementTraceRows(long receiptId) {
		return this.jdbcTemplate.query("""
				select 'PURCHASE_RECEIPT' source_type, pr.id source_id, pr.receipt_no source_no,
					prl.id source_line_id, pr.business_date, pr.status, prl.quantity,
					prl.quantity * pol.unit_price amount
				from proc_purchase_receipt pr
				join proc_purchase_receipt_line prl on prl.receipt_id = pr.id
				join proc_purchase_order_line pol on pol.id = prl.order_line_id
				where pr.status = 'POSTED'
				and pr.id = ?
				order by prl.line_no
				""", this::traceSourceRow, receiptId);
	}

	private List<TraceSourceRow> procurementReturnTraceRows(long receiptId, ReportQuery query) {
		return this.jdbcTemplate.query("""
				select 'PURCHASE_RETURN' source_type, pr.id source_id, pr.return_no source_no,
					prl.id source_line_id, pr.business_date, pr.status, prl.quantity, prl.amount
				from proc_purchase_return pr
				join proc_purchase_return_line prl on prl.return_id = pr.id
				where pr.status = 'POSTED'
				and pr.source_receipt_id = ?
				and pr.business_date between ? and ?
				order by pr.business_date, pr.id, prl.line_no
				""", this::traceSourceRow, receiptId, query.dateFrom(), query.dateTo());
	}

	private List<TraceSourceRow> procurementReturnTraceRowsByReturnId(long returnId, ReportQuery query) {
		return this.jdbcTemplate.query("""
				select 'PURCHASE_RETURN' source_type, pr.id source_id, pr.return_no source_no,
					prl.id source_line_id, pr.business_date, pr.status, prl.quantity, prl.amount
				from proc_purchase_return pr
				join proc_purchase_return_line prl on prl.return_id = pr.id
				where pr.status = 'POSTED'
				and pr.id = ?
				and pr.business_date between ? and ?
				order by pr.business_date, pr.id, prl.line_no
				""", this::traceSourceRow, returnId, query.dateFrom(), query.dateTo());
	}

	private List<TraceSourceRow> inventoryTraceRows(long warehouseId, long materialId, ReportQuery query) {
		return this.jdbcTemplate.query("""
				select 'INVENTORY_MOVEMENT' source_type, sm.id source_id, sm.movement_no source_no,
					sm.source_line_id, sm.business_date, sm.movement_type status, sm.quantity,
					null::numeric amount
				from inv_stock_movement sm
				where sm.warehouse_id = ?
				and sm.material_id = ?
				and sm.business_date between ? and ?
				order by sm.business_date, sm.id
				""", this::traceSourceRow, warehouseId, materialId, query.dateFrom(), query.dateTo());
	}

	private List<TraceSourceResponse> productionTraceRows(long workOrderId, ReportQuery query) {
		List<TraceSourceResponse> rows = new ArrayList<>();
		rows.addAll(this.jdbcTemplate.query("""
				select 'PRODUCTION_WORK_ORDER' source_type, wo.id source_id, wo.work_order_no source_no,
					null::bigint source_line_id, wo.planned_start_date business_date, wo.status,
					wo.planned_quantity quantity, null::numeric amount
				from mfg_work_order wo
				where wo.id = ?
				and wo.status not in ('DRAFT', 'CANCELLED')
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "production:work-order:view",
				"production-work-order-detail", Map.of("id", rs.getLong("source_id")), null), workOrderId));
		rows.addAll(productionMaterialIssueTraceRows(workOrderId, null, null, query));
		rows.addAll(productionMaterialReturnTraceRows(workOrderId, null, null, query));
		rows.addAll(productionMaterialSupplementTraceRows(workOrderId, null, null, query));
		rows.addAll(productionWorkReportTraceRows(workOrderId, null, query));
		rows.addAll(productionCompletionReceiptTraceRows(workOrderId, null, query));
		return rows;
	}

	private List<TraceSourceResponse> productionMaterialIssueTraceRows(long workOrderId, Long issueId,
			Long issueLineId, ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select 'PRODUCTION_MATERIAL_ISSUE' source_type, mi.id source_id, mi.issue_no source_no,
					mil.id source_line_id, mi.business_date, mi.status, mil.quantity, null::numeric amount
				from mfg_material_issue mi
				join mfg_material_issue_line mil on mil.issue_id = mi.id
				where mi.status = 'POSTED'
				and mi.work_order_id = ?
				and mi.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(workOrderId, query.dateFrom(), query.dateTo()));
		if (issueId != null) {
			sql.append(" and mi.id = ?");
			args.add(issueId);
		}
		if (issueLineId != null) {
			sql.append(" and mil.id = ?");
			args.add(issueLineId);
		}
		sql.append(" order by mi.business_date, mi.id, mil.line_no");
		return this.jdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "production:issue:view",
						"production-work-order-material-issues", Map.of("id", workOrderId), null),
				args.toArray());
	}

	private List<TraceSourceResponse> productionMaterialReturnTraceRows(long workOrderId, Long returnId,
			Long returnLineId, ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select 'PRODUCTION_MATERIAL_RETURN' source_type, mr.id source_id, mr.return_no source_no,
					mrl.id source_line_id, mr.business_date, mr.status, mrl.quantity, null::numeric amount
				from mfg_material_return mr
				join mfg_material_return_line mrl on mrl.return_id = mr.id
				where mr.status = 'POSTED'
				and mr.work_order_id = ?
				and mr.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(workOrderId, query.dateFrom(), query.dateTo()));
		if (returnId != null) {
			sql.append(" and mr.id = ?");
			args.add(returnId);
		}
		if (returnLineId != null) {
			sql.append(" and mrl.id = ?");
			args.add(returnLineId);
		}
		sql.append(" order by mr.business_date, mr.id, mrl.line_no");
		return this.jdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "production:material-return:view",
						"production-material-return-detail", Map.of("id", rs.getLong("source_id")), null),
				args.toArray());
	}

	private List<TraceSourceResponse> productionMaterialSupplementTraceRows(long workOrderId, Long supplementId,
			Long supplementLineId, ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select 'PRODUCTION_MATERIAL_SUPPLEMENT' source_type, ms.id source_id, ms.supplement_no source_no,
					msl.id source_line_id, ms.business_date, ms.status, msl.quantity, null::numeric amount
				from mfg_material_supplement ms
				join mfg_material_supplement_line msl on msl.supplement_id = ms.id
				where ms.status = 'POSTED'
				and ms.work_order_id = ?
				and ms.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(workOrderId, query.dateFrom(), query.dateTo()));
		if (supplementId != null) {
			sql.append(" and ms.id = ?");
			args.add(supplementId);
		}
		if (supplementLineId != null) {
			sql.append(" and msl.id = ?");
			args.add(supplementLineId);
		}
		sql.append(" order by ms.business_date, ms.id, msl.line_no");
		return this.jdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "production:material-supplement:view",
						"production-material-supplement-detail", Map.of("id", rs.getLong("source_id")), null),
				args.toArray());
	}

	private List<TraceSourceResponse> productionWorkReportTraceRows(long workOrderId, Long reportId,
			ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select 'PRODUCTION_WORK_REPORT' source_type, wr.id source_id, wr.report_no source_no,
					null::bigint source_line_id, wr.business_date, wr.status,
					wr.qualified_quantity + wr.defective_quantity quantity, null::numeric amount
				from mfg_work_report wr
				where wr.status = 'POSTED'
				and wr.work_order_id = ?
				and wr.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(workOrderId, query.dateFrom(), query.dateTo()));
		if (reportId != null) {
			sql.append(" and wr.id = ?");
			args.add(reportId);
		}
		sql.append(" order by wr.business_date, wr.id");
		return this.jdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "production:report:view",
						"production-work-order-reports", Map.of("id", workOrderId), null),
				args.toArray());
	}

	private List<TraceSourceResponse> productionCompletionReceiptTraceRows(long workOrderId, Long receiptId,
			ReportQuery query) {
		StringBuilder sql = new StringBuilder("""
				select 'PRODUCTION_COMPLETION_RECEIPT' source_type, cr.id source_id, cr.receipt_no source_no,
					null::bigint source_line_id, cr.business_date, cr.status, cr.quantity, null::numeric amount
				from mfg_completion_receipt cr
				where cr.status = 'POSTED'
				and cr.work_order_id = ?
				and cr.business_date between ? and ?
				""");
		List<Object> args = new ArrayList<>(List.of(workOrderId, query.dateFrom(), query.dateTo()));
		if (receiptId != null) {
			sql.append(" and cr.id = ?");
			args.add(receiptId);
		}
		sql.append(" order by cr.business_date, cr.id");
		return this.jdbcTemplate.query(sql.toString(),
				(rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "production:receipt:view",
						"production-work-order-completion-receipts", Map.of("id", workOrderId), null),
				args.toArray());
	}

	private List<TraceSourceResponse> costRecordTraceRows(long costRecordId, ReportQuery query) {
		List<CostTraceContext> contexts = costTraceContexts("""
				select cr.id cost_record_id, cr.record_no, cr.work_order_id, cr.source_document_type,
					cr.source_document_id, cr.source_line_id, cr.business_date, cr.status, cr.quantity, cr.amount
				from mfg_cost_record cr
				where cr.id = ?
				and cr.status = 'ACTIVE'
				and cr.business_date between ? and ?
				""", costRecordId, query.dateFrom(), query.dateTo());
		if (contexts.isEmpty()) {
			return List.of();
		}
		return costTraceRows(contexts, query);
	}

	private List<TraceSourceResponse> costWorkOrderTraceRows(long workOrderId, ReportQuery query) {
		List<CostTraceContext> contexts = costTraceContexts("""
				select cr.id cost_record_id, cr.record_no, cr.work_order_id, cr.source_document_type,
					cr.source_document_id, cr.source_line_id, cr.business_date, cr.status, cr.quantity, cr.amount
				from mfg_cost_record cr
				where cr.work_order_id = ?
				and cr.status = 'ACTIVE'
				and cr.business_date between ? and ?
				order by cr.business_date, cr.id
				""", workOrderId, query.dateFrom(), query.dateTo());
		if (contexts.isEmpty()) {
			return List.of();
		}
		return costTraceRows(contexts, query);
	}

	private List<TraceSourceResponse> costTraceRows(List<CostTraceContext> contexts, ReportQuery query) {
		List<TraceSourceResponse> rows = new ArrayList<>();
		for (CostTraceContext context : contexts) {
			rows.add(trace(new TraceSourceRow("COST_RECORD", context.costRecordId(), context.recordNo(),
					context.sourceLineId(), context.businessDate(), context.status(), context.quantity(),
					context.amount()), "cost:record:view", "cost-record-detail",
					Map.of("id", context.costRecordId()), null));
		}
		long workOrderId = contexts.get(0).workOrderId();
		rows.addAll(productionTraceRows(workOrderId, query).stream()
			.filter((row) -> "PRODUCTION_WORK_ORDER".equals(row.sourceType()))
			.toList());
		for (CostTraceContext context : contexts) {
			rows.addAll(costProductionSourceRows(context, query));
		}
		return rows;
	}

	private List<CostTraceContext> costTraceContexts(String sql, Object... args) {
		return this.jdbcTemplate.query(sql, (rs, rowNum) -> new CostTraceContext(rs.getLong("cost_record_id"),
				rs.getString("record_no"), rs.getLong("work_order_id"), rs.getString("source_document_type"),
				nullableLong(rs, "source_document_id"), nullableLong(rs, "source_line_id"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("amount")), args);
	}

	private List<TraceSourceResponse> costProductionSourceRows(CostTraceContext context, ReportQuery query) {
		if (context.sourceDocumentId() == null) {
			return List.of();
		}
		return switch (context.sourceDocumentType()) {
			case "PRODUCTION_MATERIAL_ISSUE" -> productionMaterialIssueTraceRows(context.workOrderId(),
					context.sourceDocumentId(), context.sourceLineId(), query);
			case "PRODUCTION_MATERIAL_RETURN" -> productionMaterialReturnTraceRows(context.workOrderId(),
					context.sourceDocumentId(), context.sourceLineId(), query);
			case "PRODUCTION_MATERIAL_SUPPLEMENT" -> productionMaterialSupplementTraceRows(context.workOrderId(),
					context.sourceDocumentId(), context.sourceLineId(), query);
			case "PRODUCTION_WORK_REPORT" -> productionWorkReportTraceRows(context.workOrderId(),
					context.sourceDocumentId(), query);
			case "PRODUCTION_COMPLETION_RECEIPT" -> productionCompletionReceiptTraceRows(context.workOrderId(),
					context.sourceDocumentId(), query);
			default -> List.of();
		};
	}

	private List<TraceSourceResponse> receivableTraceRows(long receivableId) {
		List<TraceSourceResponse> rows = new ArrayList<>();
		rows.addAll(this.jdbcTemplate.query("""
				select 'RECEIVABLE' source_type, r.id source_id, r.receivable_no source_no,
					null::bigint source_line_id, r.business_date, r.status, null::numeric quantity,
					r.total_amount amount
				from fin_receivable r
				where r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
				and r.id = ?
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:receivable:view",
				"finance-receivable-detail", Map.of("id", rs.getLong("source_id")), null), receivableId));
		rows.addAll(this.jdbcTemplate.query("""
				select 'RECEIPT' source_type, rc.id source_id, rc.receipt_no source_no,
					null::bigint source_line_id, rc.receipt_date business_date, rc.status, null::numeric quantity,
					ra.allocated_amount amount
				from fin_receipt rc
				join fin_receipt_allocation ra on ra.receipt_id = rc.id
				where rc.status = 'POSTED'
				and ra.receivable_id = ?
				order by rc.receipt_date, rc.id
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:receipt:view",
				"finance-receipt-detail", Map.of("id", rs.getLong("source_id")), null), receivableId));
		rows.addAll(this.jdbcTemplate.query("""
				select 'SETTLEMENT_ADJUSTMENT' source_type, fsa.id source_id, fsa.adjustment_no source_no,
					null::bigint source_line_id, fsa.business_date, fsa.status, null::numeric quantity,
					fsa.amount amount
				from fin_settlement_adjustment fsa
				where fsa.status = 'POSTED'
				and fsa.settlement_side = 'RECEIVABLE'
				and fsa.target_id = ?
				order by fsa.business_date, fsa.id
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:settlement-adjustment:view",
				"finance-settlement-adjustment-detail", Map.of("id", rs.getLong("source_id")), null),
				receivableId));
		rows.addAll(this.jdbcTemplate.query("""
				select 'SALES_SHIPMENT' source_type, sh.id source_id, sh.shipment_no source_no,
					rs.source_line_id, sh.business_date, sh.status, ssl.quantity, rs.source_amount amount
				from fin_receivable_source rs
				join sal_sales_shipment sh on sh.id = rs.source_id
				join sal_sales_shipment_line ssl on ssl.id = rs.source_line_id
				where rs.receivable_id = ?
				and rs.source_type = 'SALES_SHIPMENT'
				and sh.status = 'POSTED'
				order by rs.source_line_no, rs.source_line_id
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "sales:shipment:view",
				"sales-shipment-detail", Map.of("id", rs.getLong("source_id")), null), receivableId));
		return rows;
	}

	private List<TraceSourceResponse> payableTraceRows(long payableId) {
		List<TraceSourceResponse> rows = new ArrayList<>();
		rows.addAll(this.jdbcTemplate.query("""
				select 'PAYABLE' source_type, p.id source_id, p.payable_no source_no,
					null::bigint source_line_id, p.business_date, p.status, null::numeric quantity,
					p.total_amount amount
				from fin_payable p
				where p.status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
				and p.id = ?
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:payable:view",
				"finance-payable-detail", Map.of("id", rs.getLong("source_id")), null), payableId));
		rows.addAll(this.jdbcTemplate.query("""
				select 'PAYMENT' source_type, pm.id source_id, pm.payment_no source_no,
					null::bigint source_line_id, pm.payment_date business_date, pm.status, null::numeric quantity,
					pa.allocated_amount amount
				from fin_payment pm
				join fin_payment_allocation pa on pa.payment_id = pm.id
				where pm.status = 'POSTED'
				and pa.payable_id = ?
				order by pm.payment_date, pm.id
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:payment:view",
				"finance-payment-detail", Map.of("id", rs.getLong("source_id")), null), payableId));
		rows.addAll(this.jdbcTemplate.query("""
				select 'SETTLEMENT_ADJUSTMENT' source_type, fsa.id source_id, fsa.adjustment_no source_no,
					null::bigint source_line_id, fsa.business_date, fsa.status, null::numeric quantity,
					fsa.amount amount
				from fin_settlement_adjustment fsa
				where fsa.status = 'POSTED'
				and fsa.settlement_side = 'PAYABLE'
				and fsa.target_id = ?
				order by fsa.business_date, fsa.id
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:settlement-adjustment:view",
				"finance-settlement-adjustment-detail", Map.of("id", rs.getLong("source_id")), null), payableId));
		rows.addAll(this.jdbcTemplate.query("""
				select 'PURCHASE_RECEIPT' source_type, pr.id source_id, pr.receipt_no source_no,
					ps.source_line_id, pr.business_date, pr.status, prl.quantity, ps.source_amount amount
				from fin_payable_source ps
				join proc_purchase_receipt pr on pr.id = ps.source_id
				join proc_purchase_receipt_line prl on prl.id = ps.source_line_id
				where ps.payable_id = ?
				and ps.source_type = 'PURCHASE_RECEIPT'
				and pr.status = 'POSTED'
				order by ps.source_line_no, ps.source_line_id
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "procurement:receipt:view",
				"procurement-receipt-detail", Map.of("id", rs.getLong("source_id")), null), payableId));
		return rows;
	}

	private List<TraceSourceResponse> receiptTraceRows(long receiptId) {
		return this.jdbcTemplate.query("""
				select 'RECEIPT' source_type, rc.id source_id, rc.receipt_no source_no,
					null::bigint source_line_id, rc.receipt_date business_date, rc.status, null::numeric quantity,
					rc.amount amount
				from fin_receipt rc
				where rc.status = 'POSTED'
				and rc.id = ?
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:receipt:view",
				"finance-receipt-detail", Map.of("id", rs.getLong("source_id")), null), receiptId);
	}

	private List<TraceSourceResponse> paymentTraceRows(long paymentId) {
		return this.jdbcTemplate.query("""
				select 'PAYMENT' source_type, pm.id source_id, pm.payment_no source_no,
					null::bigint source_line_id, pm.payment_date business_date, pm.status, null::numeric quantity,
					pm.amount amount
				from fin_payment pm
				where pm.status = 'POSTED'
				and pm.id = ?
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:payment:view",
				"finance-payment-detail", Map.of("id", rs.getLong("source_id")), null), paymentId);
	}

	private List<TraceSourceResponse> settlementAdjustmentTraceRows(long adjustmentId) {
		return this.jdbcTemplate.query("""
				select 'SETTLEMENT_ADJUSTMENT' source_type, fsa.id source_id, fsa.adjustment_no source_no,
					null::bigint source_line_id, fsa.business_date, fsa.status, null::numeric quantity,
					fsa.amount amount
				from fin_settlement_adjustment fsa
				where fsa.status = 'POSTED'
				and fsa.id = ?
				""", (rs, rowNum) -> trace(traceSourceRow(rs, rowNum), "finance:settlement-adjustment:view",
				"finance-settlement-adjustment-detail", Map.of("id", rs.getLong("source_id")), null),
				adjustmentId);
	}

	private BigDecimal salesShipmentAmount(ReportQuery query) {
		return sum("""
				select coalesce(sum(ssl.quantity * sol.unit_price), 0)
				from sal_sales_shipment sh
				join sal_sales_shipment_line ssl on ssl.shipment_id = sh.id
				join sal_sales_order_line sol on sol.id = ssl.order_line_id
				where sh.status = 'POSTED'
				and sh.business_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal purchaseReceiptAmount(ReportQuery query) {
		return sum("""
				select coalesce(sum(prl.quantity * pol.unit_price), 0)
				from proc_purchase_receipt pr
				join proc_purchase_receipt_line prl on prl.receipt_id = pr.id
				join proc_purchase_order_line pol on pol.id = prl.order_line_id
				where pr.status = 'POSTED'
				and pr.business_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal receivableBalance(ReportQuery query) {
		return sum("""
				select coalesce(sum(unreceived_amount), 0)
				from fin_receivable
				where status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
				and business_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal payableBalance(ReportQuery query) {
		return sum("""
				select coalesce(sum(unpaid_amount), 0)
				from fin_payable
				where status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
				and business_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal receivedAmount(ReportQuery query) {
		return sum("""
				select coalesce(sum(amount), 0)
				from fin_receipt
				where status = 'POSTED'
				and receipt_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal paidAmount(ReportQuery query) {
		return sum("""
				select coalesce(sum(amount), 0)
				from fin_payment
				where status = 'POSTED'
				and payment_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal inventoryInQuantity(ReportQuery query) {
		return sum("""
				select coalesce(sum(quantity), 0)
				from inv_stock_movement
				where business_date between ? and ?
				and direction = 'IN'
				and movement_type not in ('ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE')
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal inventoryOutQuantity(ReportQuery query) {
		return sum("""
				select coalesce(sum(quantity), 0)
				from inv_stock_movement
				where business_date between ? and ?
				and direction = 'OUT'
				and movement_type not in ('ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE')
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal productionPlannedQuantity(ReportQuery query) {
		return sum("""
				select coalesce(sum(planned_quantity), 0)
				from mfg_work_order
				where planned_start_date between ? and ?
				and status not in ('DRAFT', 'CANCELLED')
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal productionCompletedQuantity(ReportQuery query) {
		return sum("""
				select coalesce(sum(quantity), 0)
				from mfg_completion_receipt
				where status = 'POSTED'
				and business_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private BigDecimal costAmount(ReportQuery query) {
		return sum("""
				select coalesce(sum(amount), 0)
				from mfg_cost_record
				where status = 'ACTIVE'
				and business_date between ? and ?
				""", query.dateFrom(), query.dateTo());
	}

	private SalesSummaryItemResponse toSalesItem(SalesRow row, ReportQuery query) {
		boolean originalRow = isSalesShipmentRow(row);
		SalesFinancialTotals totals = originalRow ? salesLineFinancialTotals(row.sourceId(), row.sourceLineId())
				: new SalesFinancialTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		SalesReversalTotals reversalTotals = salesLineReversalTotals(row, query);
		BigDecimal originalAmount = originalRow ? row.amount() : BigDecimal.ZERO;
		BigDecimal originalQuantity = originalRow ? row.quantity() : BigDecimal.ZERO;
		String traceKey = originalRow ? "sales-summary:SALES_SHIPMENT:" + row.sourceId()
				: "sales-summary:SALES_RETURN:" + row.sourceId();
		return new SalesSummaryItemResponse(row.sourceType(), row.sourceId(), row.sourceNo(), row.salesOrderId(),
				row.salesOrderNo(), row.customerId(), row.customerName(), row.materialId(), row.materialName(),
				row.businessDate(), quantity(row.quantity()), amount(row.unitPrice()), amount(row.amount()),
				amount(totals.receivableAmount()), amount(totals.receivedAmount()),
				amount(totals.unreceivedAmount()), 1, traceKey, amount(originalAmount),
				amount(reversalTotals.returnAmount()), amount(originalAmount.subtract(reversalTotals.returnAmount())),
				quantity(originalQuantity), quantity(reversalTotals.returnQuantity()),
				quantity(originalQuantity.subtract(reversalTotals.returnQuantity())));
	}

	private ProcurementSummaryItemResponse toProcurementItem(ProcurementRow row, ReportQuery query) {
		boolean originalRow = isPurchaseReceiptRow(row);
		ProcurementFinancialTotals totals = originalRow
				? procurementLineFinancialTotals(row.sourceId(), row.sourceLineId())
				: new ProcurementFinancialTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		ProcurementReversalTotals reversalTotals = procurementLineReversalTotals(row, query);
		BigDecimal originalAmount = originalRow ? row.amount() : BigDecimal.ZERO;
		BigDecimal originalQuantity = originalRow ? row.quantity() : BigDecimal.ZERO;
		String traceKey = originalRow ? "procurement-summary:PURCHASE_RECEIPT:" + row.sourceId()
				: "procurement-summary:PURCHASE_RETURN:" + row.sourceId();
		return new ProcurementSummaryItemResponse(row.sourceType(), row.sourceId(), row.sourceNo(),
				row.purchaseOrderId(), row.purchaseOrderNo(), row.supplierId(), row.supplierName(), row.materialId(),
				row.materialName(), row.businessDate(), quantity(row.quantity()), amount(row.unitPrice()),
				amount(row.amount()), amount(totals.payableAmount()), amount(totals.paidAmount()),
				amount(totals.unpaidAmount()), 1, traceKey, amount(originalAmount),
				amount(reversalTotals.returnAmount()), amount(originalAmount.subtract(reversalTotals.returnAmount())),
				quantity(originalQuantity), quantity(reversalTotals.returnQuantity()),
				quantity(originalQuantity.subtract(reversalTotals.returnQuantity())));
	}

	private InventoryStockFlowItemResponse toInventoryStockFlowItem(InventoryStockFlowRow row) {
		return new InventoryStockFlowItemResponse(row.warehouseId(), row.warehouseName(), row.materialId(),
				row.materialName(), quantity(row.openingQuantity()), quantity(row.inQuantity()),
				quantity(row.outQuantity()), quantity(row.adjustQuantity()), quantity(row.closingQuantity()),
				row.sourceCount(), "inventory-stock-flow:" + row.warehouseId() + ":" + row.materialId(),
				quantity(row.inboundOriginalQuantity()), quantity(row.inboundReverseQuantity()),
				quantity(row.inboundNetQuantity()), quantity(row.outboundOriginalQuantity()),
				quantity(row.outboundReverseQuantity()), quantity(row.outboundNetQuantity()),
				quantity(row.inventoryNetChangeQuantity()));
	}

	private ProductionExecutionItemResponse toProductionExecutionItem(ProductionExecutionRow row) {
		return new ProductionExecutionItemResponse(row.workOrderId(), row.workOrderNo(), row.productMaterialId(),
				row.productMaterialName(), quantity(row.plannedQuantity()), quantity(row.issuedQuantity()),
				quantity(row.reportedQuantity()), quantity(row.qualifiedQuantity()),
				quantity(row.defectiveQuantity()), quantity(row.completionReceiptQuantity()),
				percentage(row.completionReceiptQuantity(), row.plannedQuantity()), row.status(),
				row.plannedStartDate(), row.plannedFinishDate(), row.sourceCount(),
				"production-execution:WORK_ORDER:" + row.workOrderId(), quantity(row.issuedOriginalQuantity()),
				quantity(row.materialReturnQuantity()), quantity(row.materialSupplementQuantity()),
				quantity(row.issuedNetQuantity()), quantity(row.completionReceiptQuantity()));
	}

	private CostCollectionItemResponse toCostCollectionItem(CostCollectionRow row) {
		return new CostCollectionItemResponse(row.costRecordId(), row.recordNo(), row.workOrderId(),
				row.workOrderNo(), row.productMaterialId(), row.productMaterialName(), row.costType(),
				row.sourceType(), row.sourceDocumentType(), row.sourceDocumentId(), row.sourceDocumentNo(),
				row.businessDate(), row.quantity() == null ? null : quantity(row.quantity()),
				row.unitPrice() == null ? null : amount(row.unitPrice()), amount(row.amount()), row.basisType(),
				false, 1, "cost-collection:COST_RECORD:" + row.costRecordId(), amount(materialOriginalCost(row)),
				amount(materialReturnCost(row)), amount(materialSupplementCost(row)), amount(materialNetCost(row)),
				amount(totalNetCost(row)));
	}

	private SettlementSummaryItemResponse toSettlementItem(SettlementRow row) {
		BigDecimal periodAdjustmentAmount = "SETTLEMENT_ADJUSTMENT".equals(row.sourceType()) ? row.adjustedAmount()
				: BigDecimal.ZERO;
		BigDecimal netAmount = row.totalAmount().subtract(periodAdjustmentAmount);
		return new SettlementSummaryItemResponse(row.settlementType(), row.sourceType(), row.sourceId(), row.sourceNo(),
				row.partyType(), row.partyId(), row.partyName(), row.businessDate(), row.dueDate(),
				amount(row.totalAmount()), amount(row.settledAmount()), amount(row.unsettledAmount()),
				row.overdueDays(), row.agingBucket(), row.status(), row.sourceCount(), row.traceKey(),
				amount("RECEIVABLE".equals(row.settlementType()) ? row.totalAmount() : BigDecimal.ZERO),
				amount("RECEIVABLE".equals(row.settlementType()) ? periodAdjustmentAmount : BigDecimal.ZERO),
				amount("RECEIVABLE".equals(row.settlementType()) ? netAmount : BigDecimal.ZERO),
				amount("PAYABLE".equals(row.settlementType()) ? row.totalAmount() : BigDecimal.ZERO),
				amount("PAYABLE".equals(row.settlementType()) ? periodAdjustmentAmount : BigDecimal.ZERO),
				amount("PAYABLE".equals(row.settlementType()) ? netAmount : BigDecimal.ZERO),
				amount(row.unsettledAmount()));
	}

	private ExceptionRow exceptionRow(String exceptionType, String sourceType, Long sourceId, String sourceNo,
			Long warehouseId, Long materialId, LocalDate businessDate, String objectName, String description,
			int sourceCount, BigDecimal quantity, BigDecimal amount, String status, String permissionCode,
			String routeName, Map<String, Object> routeParams, Map<String, Object> routeQuery, String traceKey) {
		String severity = CRITICAL_EXCEPTION_TYPES.contains(exceptionType) ? "CRITICAL" : "WARNING";
		return new ExceptionRow(exceptionType, severity, sourceType, sourceId, sourceNo, warehouseId, materialId,
				businessDate, objectName, description, sourceCount, quantity, amount, status, permissionCode,
				routeName, routeParams, routeQuery, traceKey);
	}

	private ExceptionSummaryResponse exceptionSummary(List<ExceptionRow> rows) {
		Map<String, Integer> countsByType = emptyExceptionCountsByType();
		for (ExceptionRow row : rows) {
			countsByType.computeIfPresent(row.exceptionType(), (ignored, count) -> count + 1);
		}
		int criticalCount = (int) rows.stream().filter((row) -> "CRITICAL".equals(row.severity())).count();
		int warningCount = rows.size() - criticalCount;
		return new ExceptionSummaryResponse(rows.size(), criticalCount, warningCount, countsByType);
	}

	private Map<String, Integer> emptyExceptionCountsByType() {
		Map<String, Integer> countsByType = new java.util.LinkedHashMap<>();
		for (String exceptionType : EXCEPTION_TYPE_ORDER) {
			countsByType.put(exceptionType, 0);
		}
		return countsByType;
	}

	private ExceptionItemResponse toExceptionItem(ExceptionRow row) {
		boolean canViewResource = currentUserPermissions().contains(row.permissionCode());
		if (!canViewResource) {
			return new ExceptionItemResponse(row.exceptionType(), row.severity(), row.sourceType(), null, null, null,
					null, row.description(), row.sourceCount(), false, null);
		}
		return new ExceptionItemResponse(row.exceptionType(), row.severity(), row.sourceType(), row.sourceId(),
				row.sourceNo(), row.businessDate(), row.objectName(), row.description(), row.sourceCount(), true,
				row.traceKey());
	}

	private TraceSourceResponse toExceptionTrace(ExceptionRow row) {
		TraceSourceRow source = new TraceSourceRow(row.sourceType(), row.sourceId(), row.sourceNo(), null,
				row.businessDate(), row.status(), row.quantity(), row.amount());
		return trace(source, row.permissionCode(), row.routeName(), row.routeParams(), row.routeQuery());
	}

	private LocalDate reportCutoff(ReportQuery query) {
		LocalDate today = LocalDate.now();
		return query.dateTo().isBefore(today) ? query.dateTo() : today;
	}

	private boolean matchesExceptionKeyword(ExceptionRow row, String keyword) {
		if (!hasText(keyword)) {
			return true;
		}
		String normalized = keyword.toLowerCase();
		return contains(row.exceptionType(), normalized) || contains(row.severity(), normalized)
				|| contains(row.sourceType(), normalized) || contains(row.sourceNo(), normalized)
				|| contains(row.objectName(), normalized) || contains(row.description(), normalized);
	}

	private boolean contains(String value, String normalizedKeyword) {
		return value != null && value.toLowerCase().contains(normalizedKeyword);
	}

	private void validateExceptionTraceKey(String traceKey) {
		if (traceKey == null) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		String[] parts = traceKey.split(":", -1);
		if (parts.length < 4 || !"exceptions".equals(parts[0]) || !EXCEPTION_TYPES.contains(parts[1])
				|| !isTraceSourceType(parts[2])) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		String exceptionType = parts[1];
		String sourceType = parts[2];
		if ("INVENTORY_BALANCE".equals(sourceType)) {
			if (parts.length != 5 || !"INVENTORY_SHORTAGE".equals(exceptionType) || !positiveLong(parts[3])
					|| !positiveLong(parts[4])) {
				throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
			}
			return;
		}
		if (parts.length != 4 || !positiveLong(parts[3]) || !validExceptionSourceType(exceptionType, sourceType)) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
	}

	private boolean validExceptionSourceType(String exceptionType, String sourceType) {
		return switch (exceptionType) {
			case "SALES_DELIVERY_OVERDUE" -> "SALES_ORDER".equals(sourceType);
			case "PROCUREMENT_RECEIPT_OVERDUE" -> "PURCHASE_ORDER".equals(sourceType);
			case "INVENTORY_SHORTAGE" -> "INVENTORY_BALANCE".equals(sourceType);
			case "PRODUCTION_OVERDUE", "COST_MISSING" -> "PRODUCTION_WORK_ORDER".equals(sourceType);
			case "RECEIVABLE_OVERDUE" -> "RECEIVABLE".equals(sourceType);
			case "PAYABLE_DUE_SOON" -> "PAYABLE".equals(sourceType);
			default -> false;
		};
	}

	private boolean isSalesShipmentRow(SalesRow row) {
		return "SALES_SHIPMENT".equals(row.sourceType());
	}

	private boolean isPurchaseReceiptRow(ProcurementRow row) {
		return "PURCHASE_RECEIPT".equals(row.sourceType());
	}

	private SalesReversalTotals salesLineReversalTotals(List<SalesRow> rows, ReportQuery query) {
		BigDecimal returnQuantity = BigDecimal.ZERO;
		BigDecimal returnAmount = BigDecimal.ZERO;
		for (SalesRow row : rows) {
			SalesReversalTotals totals = salesLineReversalTotals(row, query);
			returnQuantity = returnQuantity.add(totals.returnQuantity());
			returnAmount = returnAmount.add(totals.returnAmount());
		}
		return new SalesReversalTotals(returnQuantity, returnAmount);
	}

	private SalesReversalTotals salesLineReversalTotals(SalesRow row, ReportQuery query) {
		if ("SALES_RETURN".equals(row.sourceType())) {
			return new SalesReversalTotals(row.quantity(), row.amount());
		}
		return salesLineReversalTotals(row.sourceId(), row.sourceLineId(), query);
	}

	private SalesReversalTotals salesLineReversalTotals(Long shipmentId, Long shipmentLineId, ReportQuery query) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(srl.quantity), 0) return_quantity,
					coalesce(sum(srl.amount), 0) return_amount
				from sal_sales_return sr
				join sal_sales_return_line srl on srl.return_id = sr.id
				where sr.status = 'POSTED'
				and sr.source_shipment_id = ?
				and srl.source_shipment_line_id = ?
				and sr.business_date between ? and ?
				""", (rs, rowNum) -> new SalesReversalTotals(rs.getBigDecimal("return_quantity"),
				rs.getBigDecimal("return_amount")), shipmentId, shipmentLineId, query.dateFrom(), query.dateTo());
	}

	private ProcurementReversalTotals procurementLineReversalTotals(List<ProcurementRow> rows, ReportQuery query) {
		BigDecimal returnQuantity = BigDecimal.ZERO;
		BigDecimal returnAmount = BigDecimal.ZERO;
		for (ProcurementRow row : rows) {
			ProcurementReversalTotals totals = procurementLineReversalTotals(row, query);
			returnQuantity = returnQuantity.add(totals.returnQuantity());
			returnAmount = returnAmount.add(totals.returnAmount());
		}
		return new ProcurementReversalTotals(returnQuantity, returnAmount);
	}

	private ProcurementReversalTotals procurementLineReversalTotals(ProcurementRow row, ReportQuery query) {
		if ("PURCHASE_RETURN".equals(row.sourceType())) {
			return new ProcurementReversalTotals(row.quantity(), row.amount());
		}
		return procurementLineReversalTotals(row.sourceId(), row.sourceLineId(), query);
	}

	private ProcurementReversalTotals procurementLineReversalTotals(Long receiptId, Long receiptLineId,
			ReportQuery query) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(prl.quantity), 0) return_quantity,
					coalesce(sum(prl.amount), 0) return_amount
				from proc_purchase_return pr
				join proc_purchase_return_line prl on prl.return_id = pr.id
				where pr.status = 'POSTED'
				and pr.source_receipt_id = ?
				and prl.source_receipt_line_id = ?
				and pr.business_date between ? and ?
				""", (rs, rowNum) -> new ProcurementReversalTotals(rs.getBigDecimal("return_quantity"),
				rs.getBigDecimal("return_amount")), receiptId, receiptLineId, query.dateFrom(), query.dateTo());
	}

	private SalesFinancialTotals salesLineFinancialTotals(List<SalesRow> rows) {
		BigDecimal receivableAmount = BigDecimal.ZERO;
		BigDecimal receivedAmount = BigDecimal.ZERO;
		BigDecimal unreceivedAmount = BigDecimal.ZERO;
		for (SalesRow row : rows) {
			if (!isSalesShipmentRow(row)) {
				continue;
			}
			SalesFinancialTotals totals = salesLineFinancialTotals(row.sourceId(), row.sourceLineId());
			receivableAmount = receivableAmount.add(totals.receivableAmount());
			receivedAmount = receivedAmount.add(totals.receivedAmount());
			unreceivedAmount = unreceivedAmount.add(totals.unreceivedAmount());
		}
		return new SalesFinancialTotals(receivableAmount, receivedAmount, unreceivedAmount);
	}

	private SalesFinancialTotals salesLineFinancialTotals(Long shipmentId, Long shipmentLineId) {
		FinancialTotals totals = this.jdbcTemplate.queryForObject("""
				with allocated as (
					select r.id receivable_id, rs.source_line_no, rs.source_line_id, rs.source_amount,
						r.received_amount, r.unreceived_amount,
						case when r.total_amount = 0 then 0 else round(r.received_amount * rs.source_amount / r.total_amount, 2) end received_share,
						case when r.total_amount = 0 then 0 else round(r.unreceived_amount * rs.source_amount / r.total_amount, 2) end unreceived_share,
						row_number() over (partition by r.id order by rs.source_line_no, rs.source_line_id) line_index,
						count(*) over (partition by r.id) line_count
					from fin_receivable_source rs
					join fin_receivable r on r.id = rs.receivable_id
					where rs.source_type = 'SALES_SHIPMENT'
					and rs.source_id = ?
					and r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
				),
				adjusted as (
					select source_line_id, source_amount,
						case when line_index = line_count
							then received_amount - coalesce(sum(received_share) over (
								partition by receivable_id order by source_line_no, source_line_id rows between unbounded preceding and 1 preceding
							), 0)
							else received_share
						end settled_amount,
						case when line_index = line_count
							then unreceived_amount - coalesce(sum(unreceived_share) over (
								partition by receivable_id order by source_line_no, source_line_id rows between unbounded preceding and 1 preceding
							), 0)
							else unreceived_share
						end unsettled_amount
					from allocated
				)
				select coalesce(sum(source_amount), 0) total_amount,
					coalesce(sum(settled_amount), 0) settled_amount,
					coalesce(sum(unsettled_amount), 0) unsettled_amount
				from adjusted
				where source_line_id = ?
				""", this::financialTotalsRow, shipmentId, shipmentLineId);
		return new SalesFinancialTotals(totals.totalAmount(), totals.settledAmount(), totals.unsettledAmount());
	}

	private ProcurementFinancialTotals procurementLineFinancialTotals(List<ProcurementRow> rows) {
		BigDecimal payableAmount = BigDecimal.ZERO;
		BigDecimal paidAmount = BigDecimal.ZERO;
		BigDecimal unpaidAmount = BigDecimal.ZERO;
		for (ProcurementRow row : rows) {
			if (!isPurchaseReceiptRow(row)) {
				continue;
			}
			ProcurementFinancialTotals totals = procurementLineFinancialTotals(row.sourceId(), row.sourceLineId());
			payableAmount = payableAmount.add(totals.payableAmount());
			paidAmount = paidAmount.add(totals.paidAmount());
			unpaidAmount = unpaidAmount.add(totals.unpaidAmount());
		}
		return new ProcurementFinancialTotals(payableAmount, paidAmount, unpaidAmount);
	}

	private ProcurementFinancialTotals procurementLineFinancialTotals(Long receiptId, Long receiptLineId) {
		FinancialTotals totals = this.jdbcTemplate.queryForObject("""
				with allocated as (
					select p.id payable_id, ps.source_line_no, ps.source_line_id, ps.source_amount,
						p.paid_amount, p.unpaid_amount,
						case when p.total_amount = 0 then 0 else round(p.paid_amount * ps.source_amount / p.total_amount, 2) end paid_share,
						case when p.total_amount = 0 then 0 else round(p.unpaid_amount * ps.source_amount / p.total_amount, 2) end unpaid_share,
						row_number() over (partition by p.id order by ps.source_line_no, ps.source_line_id) line_index,
						count(*) over (partition by p.id) line_count
					from fin_payable_source ps
					join fin_payable p on p.id = ps.payable_id
					where ps.source_type = 'PURCHASE_RECEIPT'
					and ps.source_id = ?
					and p.status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
				),
				adjusted as (
					select source_line_id, source_amount,
						case when line_index = line_count
							then paid_amount - coalesce(sum(paid_share) over (
								partition by payable_id order by source_line_no, source_line_id rows between unbounded preceding and 1 preceding
							), 0)
							else paid_share
						end settled_amount,
						case when line_index = line_count
							then unpaid_amount - coalesce(sum(unpaid_share) over (
								partition by payable_id order by source_line_no, source_line_id rows between unbounded preceding and 1 preceding
							), 0)
							else unpaid_share
						end unsettled_amount
					from allocated
				)
				select coalesce(sum(source_amount), 0) total_amount,
					coalesce(sum(settled_amount), 0) settled_amount,
					coalesce(sum(unsettled_amount), 0) unsettled_amount
				from adjusted
				where source_line_id = ?
				""", this::financialTotalsRow, receiptId, receiptLineId);
		return new ProcurementFinancialTotals(totals.totalAmount(), totals.settledAmount(), totals.unsettledAmount());
	}

	private SalesFinancialTotals salesFinancialTotals(List<Long> shipmentIds) {
		if (shipmentIds.isEmpty()) {
			return new SalesFinancialTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		}
		return financialTotals("""
				select coalesce(sum(total_amount), 0) total_amount,
					coalesce(sum(received_amount), 0) settled_amount,
					coalesce(sum(unreceived_amount), 0) unsettled_amount
				from fin_receivable
				where source_type = 'SALES_SHIPMENT'
				and status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
				and source_id in (%s)
				""", shipmentIds, (totals) -> new SalesFinancialTotals(totals.totalAmount(),
				totals.settledAmount(), totals.unsettledAmount()));
	}

	private ProcurementFinancialTotals procurementFinancialTotals(List<Long> receiptIds) {
		if (receiptIds.isEmpty()) {
			return new ProcurementFinancialTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		}
		return financialTotals("""
				select coalesce(sum(total_amount), 0) total_amount,
					coalesce(sum(paid_amount), 0) settled_amount,
					coalesce(sum(unpaid_amount), 0) unsettled_amount
				from fin_payable
				where source_type = 'PURCHASE_RECEIPT'
				and status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
				and source_id in (%s)
				""", receiptIds, (totals) -> new ProcurementFinancialTotals(totals.totalAmount(),
				totals.settledAmount(), totals.unsettledAmount()));
	}

	private BigDecimal costAmount(List<CostCollectionRow> rows, String costType) {
		return rows.stream()
			.filter((row) -> costType.equals(row.costType()))
			.map((row) -> row.amount() == null ? BigDecimal.ZERO : row.amount())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal materialCostAmount(List<CostCollectionRow> rows, String sourceDocumentType) {
		return rows.stream()
			.filter((row) -> "MATERIAL".equals(row.costType()))
			.filter((row) -> sourceDocumentType.equals(row.sourceDocumentType()))
			.map((row) -> row.amount() == null ? BigDecimal.ZERO : row.amount())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal materialOriginalCost(CostCollectionRow row) {
		return materialCost(row, "PRODUCTION_MATERIAL_ISSUE");
	}

	private BigDecimal materialReturnCost(CostCollectionRow row) {
		return materialCost(row, "PRODUCTION_MATERIAL_RETURN");
	}

	private BigDecimal materialSupplementCost(CostCollectionRow row) {
		return materialCost(row, "PRODUCTION_MATERIAL_SUPPLEMENT");
	}

	private BigDecimal materialNetCost(CostCollectionRow row) {
		return materialOriginalCost(row).subtract(materialReturnCost(row)).add(materialSupplementCost(row));
	}

	private BigDecimal totalNetCost(CostCollectionRow row) {
		if ("MATERIAL".equals(row.costType())) {
			return materialNetCost(row);
		}
		return row.amount() == null ? BigDecimal.ZERO : row.amount();
	}

	private BigDecimal materialCost(CostCollectionRow row, String sourceDocumentType) {
		if (!"MATERIAL".equals(row.costType()) || !sourceDocumentType.equals(row.sourceDocumentType())) {
			return BigDecimal.ZERO;
		}
		return row.amount() == null ? BigDecimal.ZERO : row.amount();
	}

	private <T> T financialTotals(String sql, List<Long> ids, Function<FinancialTotals, T> mapper) {
		String placeholders = String.join(",", ids.stream().map((ignored) -> "?").toList());
		FinancialTotals totals = this.jdbcTemplate.queryForObject(sql.formatted(placeholders),
				this::financialTotalsRow,
				ids.toArray());
		return mapper.apply(totals);
	}

	private FinancialTotals financialTotalsRow(ResultSet rs, int rowNum) throws SQLException {
		return new FinancialTotals(rs.getBigDecimal("total_amount"), rs.getBigDecimal("settled_amount"),
				rs.getBigDecimal("unsettled_amount"));
	}

	private void requireTraceRows(List<TraceSourceResponse> items) {
		if (items.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
	}

	private TraceSourceResponse trace(TraceSourceRow row, String permissionCode, String routeName,
			Map<String, Object> routeParams, Map<String, Object> routeQuery) {
		boolean canView = currentUserPermissions().contains(permissionCode);
		if (!canView) {
			return new TraceSourceResponse(row.sourceType(), null, null, null, null, null, null, null, null, null,
					null, false, true, RESTRICTED_MESSAGE);
		}
		return new TraceSourceResponse(row.sourceType(), row.sourceId(), row.sourceNo(), row.sourceLineId(),
				row.businessDate(), row.status(), row.quantity() == null ? null : quantity(row.quantity()),
				row.amount() == null ? null : amount(row.amount()), routeName, routeParams, routeQuery, true, false,
				null);
	}

	private List<String> currentUserPermissions() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
			return currentUser.permissions();
		}
		return List.of();
	}

	private ReportPageResponse<Object> emptyReportPage(Object summary, ReportQuery query) {
		return new ReportPageResponse<>(summary, List.of(), query.page(), query.pageSize(), 0, 0);
	}

	private PageResponse<TraceSourceResponse> emptyTracePage(ReportQuery query) {
		return PageResponse.of(List.of(), query.page(), query.pageSize(), 0);
	}

	private <T> ReportPageResponse<Object> pageOf(Object summary, List<T> items, ReportQuery query) {
		int total = items.size();
		int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / query.pageSize());
		List<Object> pageItems = pageItems(items, query).stream().map(Object.class::cast).toList();
		return new ReportPageResponse<>(summary, pageItems, query.page(), query.pageSize(), total, totalPages);
	}

	private <T> List<T> pageItems(List<T> items, ReportQuery query) {
		long offset = ((long) query.page() - 1) * query.pageSize();
		if (offset >= items.size()) {
			return List.of();
		}
		int start = (int) offset;
		int end = (int) Math.min(offset + query.pageSize(), (long) items.size());
		return items.subList(start, end);
	}

	private ReportQuery parseTraceQuery(MultiValueMap<String, String> parameters) {
		LocalDate today = LocalDate.now();
		LocalDate defaultFrom = today.withDayOfMonth(1);
		LocalDate defaultTo = today.withDayOfMonth(today.lengthOfMonth());
		LocalDate dateFrom = parseDate(parameters, "dateFrom", defaultFrom);
		LocalDate dateTo = parseDate(parameters, "dateTo", defaultTo);
		validateDateRange(dateFrom, dateTo);
		int page = parsePage(parameters, "page", DEFAULT_PAGE);
		int pageSize = parsePage(parameters, "pageSize", DEFAULT_PAGE_SIZE);
		validatePage(page, pageSize);
		String traceKey = first(parameters, "traceKey");
		if (!hasText(traceKey)) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return new ReportQuery(dateFrom, dateTo, page, pageSize, traceKey, null, null, null, null, null, null, null);
	}

	private ReportQuery parseReportQuery(MultiValueMap<String, String> parameters, Set<String> allowedStatuses,
			Set<String> numericParameterNames, boolean paged) {
		LocalDate today = LocalDate.now();
		LocalDate defaultFrom = today.withDayOfMonth(1);
		LocalDate defaultTo = today.withDayOfMonth(today.lengthOfMonth());
		LocalDate dateFrom = parseDate(parameters, "dateFrom", defaultFrom);
		LocalDate dateTo = parseDate(parameters, "dateTo", defaultTo);
		validateDateRange(dateFrom, dateTo);

		Long customerId = numericParameterNames.contains("customerId") ? parseOptionalPositiveLong(parameters,
				"customerId") : null;
		Long supplierId = numericParameterNames.contains("supplierId") ? parseOptionalPositiveLong(parameters,
				"supplierId") : null;
		Long materialId = numericParameterNames.contains("materialId") ? parseOptionalPositiveLong(parameters,
				"materialId") : null;
		Long warehouseId = numericParameterNames.contains("warehouseId") ? parseOptionalPositiveLong(parameters,
				"warehouseId") : null;
		Long workOrderId = numericParameterNames.contains("workOrderId") ? parseOptionalPositiveLong(parameters,
				"workOrderId") : null;

		String status = first(parameters, "status");
		if (hasText(status) && !allowedStatuses.isEmpty() && !allowedStatuses.contains(status)) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}

		int page = DEFAULT_PAGE;
		int pageSize = DEFAULT_PAGE_SIZE;
		if (paged) {
			page = parsePage(parameters, "page", DEFAULT_PAGE);
			pageSize = parsePage(parameters, "pageSize", DEFAULT_PAGE_SIZE);
			validatePage(page, pageSize);
		}
		return new ReportQuery(dateFrom, dateTo, page, pageSize, null, customerId, supplierId, materialId,
				warehouseId, workOrderId, hasText(status) ? status : null, first(parameters, "keyword"));
	}

	private LocalDate parseDate(MultiValueMap<String, String> parameters, String name, LocalDate defaultValue) {
		String value = first(parameters, name);
		if (!hasText(value)) {
			return defaultValue;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
	}

	private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
		if (dateFrom.isAfter(dateTo) || dateFrom.plusMonths(12).isBefore(dateTo)) {
			throw new BusinessException(ApiErrorCode.REPORT_DATE_RANGE_INVALID);
		}
	}

	private int parsePage(MultiValueMap<String, String> parameters, String name, int defaultValue) {
		String value = first(parameters, name);
		if (!hasText(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
	}

	private void validatePage(int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
	}

	private Long parseOptionalPositiveLong(MultiValueMap<String, String> parameters, String name) {
		String value = first(parameters, name);
		if (!hasText(value)) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 1) {
				throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
			}
			return parsed;
		}
		catch (NumberFormatException exception) {
			throw new BusinessException(ApiErrorCode.REPORT_PARAMETER_INVALID);
		}
	}

	private void validateTraceKey(String traceKey, String prefix, Set<String> allowedSourceTypes, int length) {
		if (traceKey == null) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		String[] parts = traceKey.split(":", -1);
		if (parts.length != length || !prefix.equals(parts[0]) || !allowedSourceTypes.contains(parts[1])
				|| !positiveLong(parts[2])) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
	}

	private void appendKeyword(StringBuilder sql, List<Object> args, String keyword, String... columns) {
		if (!hasText(keyword)) {
			return;
		}
		String like = "%" + keyword.toLowerCase() + "%";
		sql.append(" and (");
		for (int i = 0; i < columns.length; i++) {
			if (i > 0) {
				sql.append(" or ");
			}
			sql.append("lower(").append(columns[i]).append(") like ?");
			args.add(like);
		}
		sql.append(")");
	}

	private BigDecimal sum(String sql, Object... args) {
		BigDecimal value = this.jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
		return value == null ? BigDecimal.ZERO : value;
	}

	private String quantity(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP).toPlainString();
	}

	private String amount(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String percentage(BigDecimal numerator, BigDecimal denominator) {
		if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
			return ZERO_PERCENT;
		}
		return (numerator == null ? BigDecimal.ZERO : numerator).multiply(BigDecimal.valueOf(100))
			.divide(denominator, 2, RoundingMode.HALF_UP)
			.toPlainString();
	}

	private boolean positiveLong(String value) {
		if (!hasText(value)) {
			return false;
		}
		try {
			return Long.parseLong(value) > 0;
		}
		catch (NumberFormatException exception) {
			return false;
		}
	}

	private boolean isTraceSourceType(String value) {
		try {
			ReportTraceSourceType.valueOf(value);
			return true;
		}
		catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private String first(MultiValueMap<String, String> parameters, String name) {
		return parameters == null ? null : parameters.getFirst(name);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private SalesRow salesRow(ResultSet rs, int rowNum) throws SQLException {
		return new SalesRow("SALES_SHIPMENT", rs.getLong("source_id"), rs.getString("source_no"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getLong("source_line_id"),
				rs.getLong("sales_order_id"), rs.getString("sales_order_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("material_id"), rs.getString("material_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"));
	}

	private SalesRow salesReturnRow(ResultSet rs, int rowNum) throws SQLException {
		return new SalesRow("SALES_RETURN", rs.getLong("source_id"), rs.getString("source_no"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getLong("source_line_id"),
				rs.getLong("sales_order_id"), rs.getString("sales_order_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("material_id"), rs.getString("material_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"));
	}

	private ProcurementRow procurementRow(ResultSet rs, int rowNum) throws SQLException {
		return new ProcurementRow("PURCHASE_RECEIPT", rs.getLong("source_id"), rs.getString("source_no"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getLong("source_line_id"),
				rs.getLong("purchase_order_id"), rs.getString("purchase_order_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getLong("material_id"), rs.getString("material_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"));
	}

	private ProcurementRow purchaseReturnRow(ResultSet rs, int rowNum) throws SQLException {
		return new ProcurementRow("PURCHASE_RETURN", rs.getLong("source_id"), rs.getString("source_no"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getLong("source_line_id"),
				rs.getLong("purchase_order_id"), rs.getString("purchase_order_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getLong("material_id"), rs.getString("material_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"));
	}

	private InventoryStockFlowRow inventoryStockFlowRow(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal openingQuantity = rs.getBigDecimal("opening_quantity");
		BigDecimal inQuantity = rs.getBigDecimal("in_quantity");
		BigDecimal outQuantity = rs.getBigDecimal("out_quantity");
		BigDecimal adjustQuantity = rs.getBigDecimal("adjust_quantity");
		BigDecimal inboundOriginalQuantity = rs.getBigDecimal("inbound_original_quantity");
		BigDecimal inboundReverseQuantity = rs.getBigDecimal("inbound_reverse_quantity");
		BigDecimal outboundOriginalQuantity = rs.getBigDecimal("outbound_original_quantity");
		BigDecimal outboundReverseQuantity = rs.getBigDecimal("outbound_reverse_quantity");
		BigDecimal inboundNetQuantity = inboundOriginalQuantity.add(inboundReverseQuantity);
		BigDecimal outboundNetQuantity = outboundOriginalQuantity.add(outboundReverseQuantity);
		return new InventoryStockFlowRow(rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getLong("material_id"), rs.getString("material_name"), openingQuantity, inQuantity, outQuantity,
				adjustQuantity, openingQuantity.add(inQuantity).subtract(outQuantity).add(adjustQuantity),
				rs.getInt("source_count"), inboundOriginalQuantity, inboundReverseQuantity, inboundNetQuantity,
				outboundOriginalQuantity, outboundReverseQuantity, outboundNetQuantity,
				inboundNetQuantity.subtract(outboundNetQuantity).add(adjustQuantity));
	}

	private ProductionExecutionRow productionExecutionRow(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal issuedOriginalQuantity = rs.getBigDecimal("issued_original_quantity");
		BigDecimal materialReturnQuantity = rs.getBigDecimal("material_return_quantity");
		BigDecimal materialSupplementQuantity = rs.getBigDecimal("material_supplement_quantity");
		return new ProductionExecutionRow(rs.getLong("work_order_id"), rs.getString("work_order_no"),
				rs.getLong("product_material_id"), rs.getString("product_material_name"),
				rs.getBigDecimal("planned_quantity"), rs.getBigDecimal("issued_quantity"),
				rs.getBigDecimal("reported_quantity"), rs.getBigDecimal("qualified_quantity"),
				rs.getBigDecimal("defective_quantity"), rs.getBigDecimal("completion_receipt_quantity"),
				rs.getString("status"), rs.getObject("planned_start_date", LocalDate.class),
				rs.getObject("planned_finish_date", LocalDate.class), rs.getInt("source_count"),
				issuedOriginalQuantity, materialReturnQuantity, materialSupplementQuantity,
				issuedOriginalQuantity.subtract(materialReturnQuantity).add(materialSupplementQuantity));
	}

	private CostCollectionRow costCollectionRow(ResultSet rs, int rowNum) throws SQLException {
		return new CostCollectionRow(rs.getLong("cost_record_id"), rs.getString("record_no"),
				rs.getLong("work_order_id"), rs.getString("work_order_no"), rs.getLong("product_material_id"),
				rs.getString("product_material_name"), rs.getString("cost_type"), rs.getString("source_type"),
				rs.getString("source_document_type"), rs.getString("source_document_no"),
				nullableLong(rs, "source_document_id"), nullableLong(rs, "source_line_id"),
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"), rs.getString("basis_type"),
				rs.getString("status"));
	}

	private SettlementRow settlementRow(ResultSet rs, String settlementType,
			BigDecimal settledAmount, BigDecimal unsettledAmount) throws SQLException {
		String sourceNo = settlementType.equals("RECEIVABLE") ? rs.getString("receivable_no")
				: rs.getString("payable_no");
		String partyType = settlementType.equals("RECEIVABLE") ? "CUSTOMER" : "SUPPLIER";
		String traceKey = "settlement-summary:" + settlementType + ":" + rs.getLong("id");
		return new SettlementRow(settlementType, settlementType, rs.getLong("id"), sourceNo, partyType,
				rs.getLong("party_id"), rs.getString("party_name"), rs.getObject("business_date", LocalDate.class),
				rs.getObject("due_date", LocalDate.class), rs.getBigDecimal("total_amount"), settledAmount,
				unsettledAmount, rs.getBigDecimal("adjusted_amount"), 0, "NOT_DUE", rs.getString("status"), 1,
				traceKey);
	}

	private SettlementRow settlementAdjustmentRow(ResultSet rs, String settlementType, String partyType)
			throws SQLException {
		String traceKey = "settlement-summary:SETTLEMENT_ADJUSTMENT:" + rs.getLong("id");
		return new SettlementRow(settlementType, "SETTLEMENT_ADJUSTMENT", rs.getLong("id"),
				rs.getString("adjustment_no"), partyType, rs.getLong("party_id"), rs.getString("party_name"),
				rs.getObject("business_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
				BigDecimal.ZERO, BigDecimal.ZERO, rs.getBigDecimal("remaining_amount"),
				rs.getBigDecimal("adjusted_amount"), 0, "NOT_DUE", rs.getString("status"), 1, traceKey);
	}

	private TraceSourceRow traceSourceRow(ResultSet rs, int rowNum) throws SQLException {
		Long sourceLineId = nullableLong(rs, "source_line_id");
		return new TraceSourceRow(rs.getString("source_type"), rs.getLong("source_id"), rs.getString("source_no"),
				sourceLineId, rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("amount"));
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private record ReportQuery(LocalDate dateFrom, LocalDate dateTo, int page, int pageSize, String traceKey,
			Long customerId, Long supplierId, Long materialId, Long warehouseId, Long workOrderId, String status,
			String keyword) {
	}

	private record SalesRow(String sourceType, Long sourceId, String sourceNo, LocalDate businessDate, String status,
			Long sourceLineId, Long salesOrderId, String salesOrderNo, Long customerId, String customerName,
			Long materialId, String materialName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
	}

	private record ProcurementRow(String sourceType, Long sourceId, String sourceNo, LocalDate businessDate,
			String status, Long sourceLineId, Long purchaseOrderId, String purchaseOrderNo, Long supplierId,
			String supplierName, Long materialId, String materialName, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal amount) {
	}

	private record InventoryStockFlowRow(Long warehouseId, String warehouseName, Long materialId, String materialName,
			BigDecimal openingQuantity, BigDecimal inQuantity, BigDecimal outQuantity, BigDecimal adjustQuantity,
			BigDecimal closingQuantity, int sourceCount, BigDecimal inboundOriginalQuantity,
			BigDecimal inboundReverseQuantity, BigDecimal inboundNetQuantity, BigDecimal outboundOriginalQuantity,
			BigDecimal outboundReverseQuantity, BigDecimal outboundNetQuantity, BigDecimal inventoryNetChangeQuantity) {
	}

	private record ProductionExecutionRow(Long workOrderId, String workOrderNo, Long productMaterialId,
			String productMaterialName, BigDecimal plannedQuantity, BigDecimal issuedQuantity,
			BigDecimal reportedQuantity, BigDecimal qualifiedQuantity, BigDecimal defectiveQuantity,
			BigDecimal completionReceiptQuantity, String status, LocalDate plannedStartDate,
			LocalDate plannedFinishDate, int sourceCount, BigDecimal issuedOriginalQuantity,
			BigDecimal materialReturnQuantity, BigDecimal materialSupplementQuantity, BigDecimal issuedNetQuantity) {
	}

	private record CostCollectionRow(Long costRecordId, String recordNo, Long workOrderId, String workOrderNo,
			Long productMaterialId, String productMaterialName, String costType, String sourceType,
			String sourceDocumentType, String sourceDocumentNo, Long sourceDocumentId, Long sourceLineId,
			LocalDate businessDate, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, String basisType,
			String status) {
	}

	private record SettlementRow(String settlementType, String sourceType, Long sourceId, String sourceNo, String partyType,
			Long partyId, String partyName, LocalDate businessDate, LocalDate dueDate, BigDecimal totalAmount,
			BigDecimal settledAmount, BigDecimal unsettledAmount, BigDecimal adjustedAmount, int overdueDays,
			String agingBucket, String status, int sourceCount, String traceKey) {
	}

	private record ExceptionRow(String exceptionType, String severity, String sourceType, Long sourceId,
			String sourceNo, Long warehouseId, Long materialId, LocalDate businessDate, String objectName,
			String description, int sourceCount, BigDecimal quantity, BigDecimal amount, String status,
			String permissionCode, String routeName, Map<String, Object> routeParams,
			Map<String, Object> routeQuery, String traceKey) {
	}

	private record TraceSourceRow(String sourceType, Long sourceId, String sourceNo, Long sourceLineId,
			LocalDate businessDate, String status, BigDecimal quantity, BigDecimal amount) {
	}

	private record CostTraceContext(Long costRecordId, String recordNo, Long workOrderId, String sourceDocumentType,
			Long sourceDocumentId, Long sourceLineId, LocalDate businessDate, String status, BigDecimal quantity,
			BigDecimal amount) {
	}

	private record FinancialTotals(BigDecimal totalAmount, BigDecimal settledAmount, BigDecimal unsettledAmount) {
	}

	private record SalesFinancialTotals(BigDecimal receivableAmount, BigDecimal receivedAmount,
			BigDecimal unreceivedAmount) {
	}

	private record ProcurementFinancialTotals(BigDecimal payableAmount, BigDecimal paidAmount,
			BigDecimal unpaidAmount) {
	}

	private record SalesReversalTotals(BigDecimal returnQuantity, BigDecimal returnAmount) {
	}

	private record ProcurementReversalTotals(BigDecimal returnQuantity, BigDecimal returnAmount) {
	}

	public record PeriodResponse(LocalDate dateFrom, LocalDate dateTo) {
	}

	public record OverviewResponse(PeriodResponse period, String salesShipmentAmount, String purchaseReceiptAmount,
			String inventoryInQuantity, String inventoryOutQuantity, String productionPlannedQuantity,
			String productionCompletedQuantity, String costAmount, String receivableBalance, String payableBalance,
			String receivedAmount, String paidAmount, int exceptionCount, boolean formalAccounting) {
	}

	public record ReportPageResponse<T>(Object summary, List<T> items, int page, int pageSize, long total,
			int totalPages) {

		public ReportPageResponse {
			items = items == null ? List.of() : List.copyOf(items);
		}

	}

	public record SalesSummaryResponse(String shipmentQuantity, String shipmentAmount, String receivableAmount,
			String receivedAmount, String unreceivedAmount, int sourceCount, String salesOriginalAmount,
			String salesReturnAmount, String salesNetAmount, String salesOriginalQuantity,
			String salesReturnQuantity, String salesNetQuantity) {
	}

	public record SalesSummaryItemResponse(String sourceType, Long sourceId, String sourceNo, Long salesOrderId,
			String salesOrderNo, Long customerId, String customerName, Long materialId, String materialName,
			LocalDate businessDate, String quantity, String unitPrice, String amount, String receivableAmount,
			String receivedAmount, String unreceivedAmount, int sourceCount, String traceKey,
			String salesOriginalAmount, String salesReturnAmount, String salesNetAmount,
			String salesOriginalQuantity, String salesReturnQuantity, String salesNetQuantity) {
	}

	public record ProcurementSummaryResponse(String receiptQuantity, String receiptAmount, String payableAmount,
			String paidAmount, String unpaidAmount, int sourceCount, String purchaseOriginalAmount,
			String purchaseReturnAmount, String purchaseNetAmount, String purchaseOriginalQuantity,
			String purchaseReturnQuantity, String purchaseNetQuantity) {
	}

	public record ProcurementSummaryItemResponse(String sourceType, Long sourceId, String sourceNo,
			Long purchaseOrderId, String purchaseOrderNo, Long supplierId, String supplierName, Long materialId,
			String materialName, LocalDate businessDate, String quantity, String unitPrice, String amount,
			String payableAmount, String paidAmount, String unpaidAmount, int sourceCount, String traceKey,
			String purchaseOriginalAmount, String purchaseReturnAmount, String purchaseNetAmount,
			String purchaseOriginalQuantity, String purchaseReturnQuantity, String purchaseNetQuantity) {
	}

	public record InventoryStockFlowSummaryResponse(String openingQuantity, String inQuantity, String outQuantity,
			String adjustQuantity, String closingQuantity, int sourceCount, String inboundOriginalQuantity,
			String inboundReverseQuantity, String inboundNetQuantity, String outboundOriginalQuantity,
			String outboundReverseQuantity, String outboundNetQuantity, String inventoryNetChangeQuantity) {
	}

	public record InventoryStockFlowItemResponse(Long warehouseId, String warehouseName, Long materialId,
			String materialName, String openingQuantity, String inQuantity, String outQuantity, String adjustQuantity,
			String closingQuantity, int sourceCount, String traceKey, String inboundOriginalQuantity,
			String inboundReverseQuantity, String inboundNetQuantity, String outboundOriginalQuantity,
			String outboundReverseQuantity, String outboundNetQuantity, String inventoryNetChangeQuantity) {
	}

	public record ProductionExecutionSummaryResponse(int workOrderCount, String plannedQuantity,
			String issuedQuantity, String reportedQuantity, String qualifiedQuantity, String defectiveQuantity,
			String completionReceiptQuantity, String completionRate, int sourceCount, String issuedOriginalQuantity,
			String materialReturnQuantity, String materialSupplementQuantity, String issuedNetQuantity,
			String completedQuantity) {
	}

	public record ProductionExecutionItemResponse(Long workOrderId, String workOrderNo, Long productMaterialId,
			String productMaterialName, String plannedQuantity, String issuedQuantity, String reportedQuantity,
			String qualifiedQuantity, String defectiveQuantity, String completionReceiptQuantity,
			String completionRate, String status, LocalDate plannedStartDate, LocalDate plannedFinishDate,
			int sourceCount, String traceKey, String issuedOriginalQuantity, String materialReturnQuantity,
			String materialSupplementQuantity, String issuedNetQuantity, String completedQuantity) {
	}

	public record CostCollectionSummaryResponse(String materialCostAmount, String laborCostAmount,
			String manufacturingOverheadAmount, String otherCostAmount, String totalCostAmount, int sourceCount,
			boolean formalAccounting, String materialOriginalCost, String materialReturnCost,
			String materialSupplementCost, String materialNetCost, String totalNetCost) {
	}

	public record CostCollectionItemResponse(Long costRecordId, String recordNo, Long workOrderId,
			String workOrderNo, Long productMaterialId, String productMaterialName, String costType,
			String sourceType, String sourceDocumentType, Long sourceDocumentId, String sourceDocumentNo,
			LocalDate businessDate, String quantity, String unitPrice, String amount, String basisType,
			boolean formalAccounting, int sourceCount, String traceKey, String materialOriginalCost,
			String materialReturnCost, String materialSupplementCost, String materialNetCost, String totalNetCost) {
	}

	public record SettlementSummaryResponse(String receivableAmount, String receivedAmount,
			String unreceivedAmount, String payableAmount, String paidAmount, String unpaidAmount, int sourceCount,
			String receivableOriginalAmount, String receivableAdjustmentAmount, String receivableNetAmount,
			String payableOriginalAmount, String payableAdjustmentAmount, String payableNetAmount,
			String settlementRemainingAmount) {
	}

	public record SettlementSummaryItemResponse(String settlementType, String sourceType, Long sourceId,
			String sourceNo, String partyType, Long partyId, String partyName, LocalDate businessDate,
			LocalDate dueDate, String totalAmount, String settledAmount, String unsettledAmount, int overdueDays,
			String agingBucket, String status, int sourceCount, String traceKey, String receivableOriginalAmount,
			String receivableAdjustmentAmount, String receivableNetAmount, String payableOriginalAmount,
			String payableAdjustmentAmount, String payableNetAmount, String settlementRemainingAmount) {
	}

	public record ExceptionSummaryResponse(int exceptionCount, int criticalCount, int warningCount,
			Map<String, Integer> countsByType) {
	}

	public record ExceptionItemResponse(String exceptionType, String severity, String sourceType, Long sourceId,
			String sourceNo, LocalDate businessDate, String objectName, String description, int sourceCount,
			boolean canViewResource, String traceKey) {
	}

	public record TraceSourceResponse(String sourceType, Long sourceId, String sourceNo, Long sourceLineId,
			LocalDate businessDate, String status, String quantity, String amount, String resourceRouteName,
			Map<String, Object> resourceRouteParams, Map<String, Object> resourceRouteQuery, boolean canViewResource,
			boolean restricted, String restrictedMessage) {
	}

}
