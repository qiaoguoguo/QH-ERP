package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.InventoryCapitalItemResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.InventoryCapitalSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
class InventoryCapitalReportQueryService extends ReportingStage033QuerySupport {

	InventoryCapitalReportQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		super(jdbcTemplate, objectMapper);
	}

	Object inventoryCapital(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, true);
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			return snapshot(query);
		}
		return live(query);
	}

	Object captureSnapshot(String periodCode, LocalDate dateFrom, LocalDate dateTo) {
		return live(captureQuery(periodCode, dateFrom, dateTo));
	}

	InventoryCapitalSummaryResponse summaryForOverview(OperatingFinanceQuery query) {
		List<InventoryCapitalRow> rows = inventoryRows(query);
		InventoryCapitalSummary summary = rows.stream().reduce(InventoryCapitalSummary.empty(),
				InventoryCapitalSummary::plus, InventoryCapitalSummary::plus);
		BigDecimal snapshotAmount = latestSnapshotAmount(query);
		BigDecimal differenceAmount = snapshotAmount == null ? null : summary.amount().subtract(snapshotAmount);
		return summaryResponse(summary, snapshotAmount, differenceAmount, query, inventoryCapitalAmountVisible(),
				inventoryCapitalSourceVisible());
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> inventoryCapitalTraces(
			MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseTraceQuery(parameters);
		TraceKeyParts parts = traceKeyParts(query.traceKey(), "inventory-capital");
		if (!"BALANCE".equals(parts.type())) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		if (!inventoryCapitalSourceVisible()) {
			return emptyTracePage(query);
		}
		return tracePage(balanceTraces(parts.sourceId(), query), query);
	}

	private ReportingAdminService.ReportPageResponse<Object> live(OperatingFinanceQuery query) {
		List<InventoryCapitalRow> rows = inventoryRows(query);
		InventoryCapitalSummary summary = rows.stream().reduce(InventoryCapitalSummary.empty(),
				InventoryCapitalSummary::plus, InventoryCapitalSummary::plus);
		BigDecimal snapshotAmount = latestSnapshotAmount(query);
		BigDecimal differenceAmount = snapshotAmount == null ? null : summary.amount().subtract(snapshotAmount);
		boolean amountVisible = inventoryCapitalAmountVisible();
		boolean sourceVisible = inventoryCapitalSourceVisible();
		InventoryCapitalSummaryResponse response = summaryResponse(summary, snapshotAmount, differenceAmount, query,
				amountVisible, sourceVisible);
		List<InventoryCapitalItemResponse> items = rows.stream()
			.map((row) -> item(row, amountVisible, sourceVisible, query))
			.toList();
		return pageOf(response, items, query);
	}

	private ReportingAdminService.ReportPageResponse<Object> snapshot(OperatingFinanceQuery query) {
		boolean amountVisible = inventoryCapitalAmountVisible();
		boolean sourceVisible = inventoryCapitalSourceVisible();
		Set<String> amountFields = Set.of("amount", "knownValuationAmount", "snapshotAmount", "differenceAmount");
		return snapshotPage("INVENTORY_CAPITAL", query, (item) -> matchesCommonSnapshotFilters(item, query),
				(items) -> snapshotSummaryMap(items, query, amountVisible, sourceVisible,
						Map.of("quantity", "quantity", "amount", "amount", "knownValuationAmount", "amount",
								"snapshotAmount", "snapshotAmount", "differenceAmount", "differenceAmount",
								"riskQuantity", "riskQuantity", "unknownValuationQuantity",
								"unknownValuationQuantity"),
						Set.of("quantity", "riskQuantity", "unknownValuationQuantity")),
				(item) -> snapshotItemMap(item, amountVisible, sourceVisible, amountFields));
	}

	private InventoryCapitalSummaryResponse summaryResponse(InventoryCapitalSummary summary, BigDecimal snapshotAmount,
			BigDecimal differenceAmount, OperatingFinanceQuery query, boolean amountVisible, boolean sourceVisible) {
		return new InventoryCapitalSummaryResponse(quantity(summary.quantity()),
				visibleAmount(summary.amount(), amountVisible), visibleNullableAmount(snapshotAmount, amountVisible),
				visibleNullableAmount(differenceAmount, amountVisible), quantity(summary.riskQuantity()),
				sourceVisible ? summary.sourceCount() : 0, query.analysisMode(), freshnessStatus(query),
				visibleAmount(summary.amount(), amountVisible),
				quantity(summary.unknownValuationQuantity()),
				summary.sourceCount() == 0 ? UNAVAILABLE
						: summary.unknownValuationQuantity().compareTo(BigDecimal.ZERO) > 0 ? "INCOMPLETE"
								: "COMPLETE");
	}

	private InventoryCapitalItemResponse item(InventoryCapitalRow row, boolean amountVisible, boolean sourceVisible,
			OperatingFinanceQuery query) {
		return new InventoryCapitalItemResponse(row.projectId(), row.ownerType(), row.projectNo(), row.warehouseName(),
				row.materialName(), row.qualityStatus(), row.freezeStatus(), row.valuationStatus(),
				quantity(row.quantity()), visibleNullableAmount(row.amount(), amountVisible), null, null,
				quantity(row.riskQuantity()), freshnessStatus(query), sourceVisible ? row.sourceCount() : 0,
				sourceVisible ? "inventory-capital:BALANCE:" + row.balanceId() : null,
				row.amount() == null ? quantity(row.quantity()) : quantity(BigDecimal.ZERO),
				row.amount() == null ? "INCOMPLETE" : "COMPLETE");
	}

	private List<InventoryCapitalRow> inventoryRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				with movement_counts as (
					select mv.source_id as balance_id, count(*) as movement_count
					from inv_stock_movement mv
					where mv.source_type = 'INVENTORY_BALANCE'
					and mv.business_date <= ?
					group by mv.source_id
				)
				select b.id as balance_id, b.ownership_type, b.project_id, project.project_no, warehouse.name as warehouse_name,
				       warehouse.code as warehouse_code, material.name as material_name, material.code as material_code,
				       b.quality_status, b.locked_quantity, b.quantity_on_hand, b.valuation_state,
				       case when b.valuation_state = 'VALUED' then b.inventory_amount else null end as amount,
				       coalesce(mv.movement_count, 0) as movement_count
				from inv_stock_balance b
				join mst_warehouse warehouse on warehouse.id = b.warehouse_id
				join mst_material material on material.id = b.material_id
				left join sal_project project on project.id = b.project_id
				left join movement_counts mv on mv.balance_id = b.id
				where b.quantity_on_hand > 0
				""");
		List<Object> args = new ArrayList<>();
		args.add(query.dateTo());
		if (query.projectId() != null) {
			sql.append(" and b.project_id = ?");
			args.add(query.projectId());
		}
		if (hasText(query.basis())) {
			sql.append(" and upper(b.ownership_type) = ?");
			args.add(query.basis().trim().toUpperCase(Locale.ROOT));
		}
		if (hasText(query.keyword())) {
			sql.append("""
					 and (
						lower(material.name) like ?
						or lower(material.code) like ?
						or lower(warehouse.name) like ?
						or lower(warehouse.code) like ?
						or lower(coalesce(project.project_no, '')) like ?
						or lower(coalesce(project.name, '')) like ?
					)
					""");
			String like = like(query.keyword());
			for (int index = 0; index < 6; index++) {
				args.add(like);
			}
		}
		sql.append("""
				 order by b.ownership_type desc, project.project_no nulls last, warehouse.code,
				          case when b.valuation_state = 'VALUED' then 0 else 1 end,
				          material.code, b.id
				""");
		return this.jdbcTemplate.query(sql.toString(), this::inventoryRow, args.toArray());
	}

	private InventoryCapitalRow inventoryRow(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal lockedQuantity = zero(rs.getBigDecimal("locked_quantity"));
		BigDecimal quantity = zero(rs.getBigDecimal("quantity_on_hand"));
		String valuationState = rs.getString("valuation_state");
		BigDecimal riskQuantity = lockedQuantity;
		if (!"VALUED".equals(valuationState)) {
			riskQuantity = riskQuantity.add(quantity);
		}
		return new InventoryCapitalRow(rs.getLong("balance_id"), rs.getObject("project_id", Long.class),
				rs.getString("ownership_type"), rs.getString("project_no"), rs.getString("warehouse_name"),
				rs.getString("material_name"), rs.getString("quality_status"),
				lockedQuantity.compareTo(BigDecimal.ZERO) > 0 ? "LOCKED" : "AVAILABLE", valuationState, quantity,
				rs.getBigDecimal("amount"), riskQuantity,
				1 + rs.getInt("movement_count"));
	}

	private BigDecimal latestSnapshotAmount(OperatingFinanceQuery query) {
		try {
			JsonNode snapshot = snapshotResult(query.periodCode(), "INVENTORY_CAPITAL");
			JsonNode amount = snapshot.path("summary").path("amount");
			return amount.isMissingNode() || amount.isNull() || !hasText(amount.asText()) ? null : decimal(amount.asText());
		}
		catch (BusinessException exception) {
			return null;
		}
	}

	private List<ReportingAdminService.TraceSourceResponse> balanceTraces(long balanceId, OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select b.id, material.code as material_code, b.updated_at::date as business_date,
				       b.valuation_state, b.quantity_on_hand,
				       case when b.valuation_state = 'VALUED' then b.inventory_amount else null end as amount
				from inv_stock_balance b
				join mst_material material on material.id = b.material_id
				where b.id = ?
				""", (rs, rowNum) -> trace("INVENTORY_BALANCE", rs.getLong("id"), rs.getString("material_code"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("valuation_state"),
				rs.getBigDecimal("quantity_on_hand"), rs.getBigDecimal("amount"), "inventory:balance:view",
				"inventory-balance-detail", routeParams("id", rs.getLong("id"))), balanceId));
		traces.addAll(this.jdbcTemplate.query("""
				select mv.id, mv.movement_no, mv.business_date, mv.valuation_state, mv.quantity, mv.inventory_amount
				from inv_stock_movement mv
				where mv.source_type = 'INVENTORY_BALANCE'
				and mv.source_id = ?
				and mv.business_date <= ?
				order by mv.business_date, mv.id
				""", (rs, rowNum) -> trace("INVENTORY_MOVEMENT", rs.getLong("id"), rs.getString("movement_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("valuation_state"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("inventory_amount"), "inventory:movement:view",
				"inventory-movement-detail", routeParams("id", rs.getLong("id"))), balanceId, query.dateTo()));
		if (traces.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

	private String like(String keyword) {
		return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
	}

	private record InventoryCapitalRow(long balanceId, Long projectId, String ownerType, String projectNo,
			String warehouseName, String materialName, String qualityStatus, String freezeStatus,
			String valuationStatus, BigDecimal quantity, BigDecimal amount, BigDecimal riskQuantity, int sourceCount) {
	}

	private record InventoryCapitalSummary(BigDecimal quantity, BigDecimal amount, BigDecimal riskQuantity,
			BigDecimal unknownValuationQuantity, int sourceCount) {

		static InventoryCapitalSummary empty() {
			return new InventoryCapitalSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
		}

		InventoryCapitalSummary plus(InventoryCapitalRow row) {
			return new InventoryCapitalSummary(this.quantity.add(row.quantity()),
					this.amount.add(row.amount() == null ? BigDecimal.ZERO : row.amount()),
					this.riskQuantity.add(row.riskQuantity()),
					this.unknownValuationQuantity.add(row.amount() == null ? row.quantity() : BigDecimal.ZERO),
					this.sourceCount + row.sourceCount());
		}

		InventoryCapitalSummary plus(InventoryCapitalSummary other) {
			return new InventoryCapitalSummary(this.quantity.add(other.quantity), this.amount.add(other.amount),
					this.riskQuantity.add(other.riskQuantity),
					this.unknownValuationQuantity.add(other.unknownValuationQuantity),
					this.sourceCount + other.sourceCount);
		}

	}

}
