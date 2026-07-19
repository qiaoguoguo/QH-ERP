package com.qherp.api.system.periodclose;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.reporting.ReportingAdminService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;

import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static com.qherp.api.system.periodclose.PeriodCloseSupport.REPORT_CODES;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.REPORT_PERMISSIONS;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.SCHEMA_VERSION;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.amount;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.fingerprint;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.hasPermission;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.normalizeReportCode;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.nullableLong;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.pageSize;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.quantity;
import static com.qherp.api.system.periodclose.PeriodCloseSupport.unitCost;

@Service
class PeriodCloseSnapshotService {

	private final JdbcTemplate jdbcTemplate;

	private final PeriodCloseRepository repository;

	private final ReportingAdminService reportingAdminService;

	private final ObjectMapper objectMapper;

	PeriodCloseSnapshotService(JdbcTemplate jdbcTemplate, PeriodCloseRepository repository,
			ReportingAdminService reportingAdminService, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.repository = repository;
		this.reportingAdminService = reportingAdminService;
		this.objectMapper = objectMapper;
	}

	long createSnapshot(PeriodCloseRunRow run, PeriodClosePeriodRow period, PeriodCloseCheckResult result,
			CurrentUser currentUser) {
		PeriodCloseRunRow latestRun = this.repository.findRun(run.id());
		if (latestRun.latestCheckRunId() == null) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_CHECK_REQUIRED);
		}
		Long snapshotId = this.jdbcTemplate.queryForObject("""
				insert into biz_period_snapshot (
					run_id, period_id, revision_no, schema_version, source_check_run_id, source_fingerprint,
					inventory_fingerprint, wip_fingerprint, project_cost_fingerprint, report_fingerprint,
					generated_by, generated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, run.id(), period.id(), run.revisionNo(), SCHEMA_VERSION,
				latestRun.latestCheckRunId(), result.sourceFingerprint(), result.inventoryFingerprint(),
				result.wipFingerprint(), result.projectCostFingerprint(), result.reportFingerprint(),
				currentUser.username(), OffsetDateTime.now());
		writeInventorySnapshots(snapshotId, period);
		writeInventorySummary(snapshotId);
		writeWipSnapshots(snapshotId, period);
		writeProjectCostSnapshots(snapshotId, period);
		writeReportSnapshots(snapshotId, period);
		Long reportCount = this.jdbcTemplate.queryForObject(
				"select count(*) from biz_period_report_snapshot where snapshot_id = ?", Long.class, snapshotId);
		if (reportCount == null || reportCount != REPORT_CODES.size()) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
		return snapshotId;
	}

	@Transactional(readOnly = true)
	PeriodCloseService.SnapshotOverviewResponse snapshot(Long runId, CurrentUser currentUser) {
		PeriodCloseRunRow run = this.repository.findRun(runId);
		Long snapshotId = requireSnapshot(run);
		PeriodCloseSnapshotRow snapshot = this.repository.findSnapshot(snapshotId);
		PeriodClosePeriodRow period = this.repository.findPeriod(run.periodId());
		Long inventoryCount = count("biz_period_inventory_snapshot", snapshotId);
		Long wipCount = count("biz_period_wip_snapshot", snapshotId);
		Long projectCostCount = count("biz_period_project_cost_snapshot", snapshotId);
		Long reportCount = count("biz_period_report_snapshot", snapshotId);
		boolean inventoryAmountVisible = hasPermission(currentUser, "inventory:valuation:view");
		boolean projectCostAmountVisible = hasPermission(currentUser, "cost:project-cost:amount-view");
		List<PeriodCloseService.SnapshotPartitionResponse> partitions = List.of(
				new PeriodCloseService.SnapshotPartitionResponse("INVENTORY", "库存", inventoryCount,
						snapshot.inventoryFingerprint(), inventoryAmountVisible, true,
						inventoryAmountVisible ? null : "缺少库存金额权限"),
				new PeriodCloseService.SnapshotPartitionResponse("WIP", "在制", wipCount, snapshot.wipFingerprint(),
						projectCostAmountVisible, true, projectCostAmountVisible ? null : "缺少项目成本金额权限"),
				new PeriodCloseService.SnapshotPartitionResponse("PROJECT_COST", "项目成本", projectCostCount,
						snapshot.projectCostFingerprint(), projectCostAmountVisible, true,
						projectCostAmountVisible ? null : "缺少项目成本金额权限"),
				new PeriodCloseService.SnapshotPartitionResponse("REPORTS", "业务报表", reportCount,
						snapshot.reportFingerprint(), true, true, null));
		return new PeriodCloseService.SnapshotOverviewResponse(run.id(), run.periodId(), run.revisionNo(),
				run.status().name(), snapshotId, period.periodCode(), period.periodName(), period.startDate(),
				period.endDate(), snapshot.sourceFingerprint(), snapshot.generatedBy(), snapshot.generatedAt(),
				snapshot.sourceCheckRunId(), reportCodes(snapshotId), partitions, inventoryCount, wipCount,
				projectCostCount, inventoryAmountVisible, projectCostAmountVisible, true, "业务月结快照为只读冻结基线");
	}

	@Transactional(readOnly = true)
	PageResponse<PeriodCloseService.InventorySnapshotResponse> inventorySnapshot(Long runId, int page, int pageSize,
			CurrentUser currentUser) {
		Long snapshotId = requireSnapshot(this.repository.findRun(runId));
		int safePage = Math.max(page, 1);
		int safePageSize = pageSize(pageSize);
		boolean amountVisible = hasPermission(currentUser, "inventory:valuation:view");
		long total = this.jdbcTemplate.queryForObject(
				"select count(*) from biz_period_inventory_snapshot where snapshot_id = ?", Long.class, snapshotId);
		List<PeriodCloseService.InventorySnapshotResponse> items = this.jdbcTemplate.query("""
				select id, warehouse_id, warehouse_name, material_id, material_code, material_name,
				       quality_status, ownership_type, project_id, project_no, batch_id, serial_id,
				       cost_layer_id, ending_quantity, locked_quantity, available_quantity, valuation_state,
				       unit_cost, ending_amount, in_quantity, out_quantity, adjustment_quantity, fingerprint
				from biz_period_inventory_snapshot
				where snapshot_id = ?
				order by warehouse_id nulls last, material_id nulls last, id
				limit ? offset ?
				""", (rs, rowNum) -> mapInventorySnapshot(rs, amountVisible), snapshotId, safePageSize,
				(long) (safePage - 1) * safePageSize);
		return PageResponse.of(items, safePage, safePageSize, total);
	}

	@Transactional(readOnly = true)
	PageResponse<PeriodCloseService.WipSnapshotResponse> wipSnapshot(Long runId, int page, int pageSize,
			CurrentUser currentUser) {
		Long snapshotId = requireSnapshot(this.repository.findRun(runId));
		int safePage = Math.max(page, 1);
		int safePageSize = pageSize(pageSize);
		boolean amountVisible = hasPermission(currentUser, "cost:project-cost:amount-view");
		long total = this.jdbcTemplate.queryForObject(
				"select count(*) from biz_period_wip_snapshot where snapshot_id = ?", Long.class, snapshotId);
		List<PeriodCloseService.WipSnapshotResponse> items = this.jdbcTemplate.query("""
				select id, project_id, project_no, work_order_id, work_order_no, product_material_id,
				       product_material_code, product_material_name, status, planned_quantity, issued_quantity,
				       reported_quantity, qualified_quantity, completed_quantity, wip_quantity, wip_cost, fingerprint
				from biz_period_wip_snapshot
				where snapshot_id = ?
				order by work_order_id nulls last, id
				limit ? offset ?
				""", (rs, rowNum) -> mapWipSnapshot(rs, amountVisible), snapshotId, safePageSize,
				(long) (safePage - 1) * safePageSize);
		return PageResponse.of(items, safePage, safePageSize, total);
	}

	@Transactional(readOnly = true)
	PageResponse<PeriodCloseService.ProjectCostSnapshotResponse> projectCostSnapshot(Long runId, int page,
			int pageSize, CurrentUser currentUser) {
		Long snapshotId = requireSnapshot(this.repository.findRun(runId));
		int safePage = Math.max(page, 1);
		int safePageSize = pageSize(pageSize);
		boolean amountVisible = hasPermission(currentUser, "cost:project-cost:amount-view");
		long total = this.jdbcTemplate.queryForObject(
				"select count(*) from biz_period_project_cost_snapshot where snapshot_id = ?", Long.class, snapshotId);
		List<PeriodCloseService.ProjectCostSnapshotResponse> items = this.jdbcTemplate.query("""
				select id, project_id, project_no, project_name, calculation_id, calculation_no, source_fingerprint,
				       freshness_status, completeness_status, project_cost_total, wip_cost, finished_cost,
				       delivered_cost, direct_project_cost, shipment_revenue, shipment_gross_margin,
				       blocking_variance_count, warning_variance_count, fingerprint
				from biz_period_project_cost_snapshot
				where snapshot_id = ?
				order by project_id, id
				limit ? offset ?
				""", (rs, rowNum) -> mapProjectCostSnapshot(rs, amountVisible), snapshotId, safePageSize,
				(long) (safePage - 1) * safePageSize);
		return PageResponse.of(items, safePage, safePageSize, total);
	}

	@Transactional(readOnly = true)
	PeriodCloseService.ReportSnapshotResponse reportSnapshot(Long runId, String reportCode, CurrentUser currentUser) {
		Long snapshotId = requireSnapshot(this.repository.findRun(runId));
		String normalized = normalizeReportCode(reportCode);
		String permission = REPORT_PERMISSIONS.get(normalized);
		if (permission == null) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
		if (!hasPermission(currentUser, permission)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		List<PeriodCloseService.ReportSnapshotResponse> rows = this.jdbcTemplate.query("""
				select report_code, schema_version, result_json::text as result_json, source_count, fingerprint,
				       created_at
				from biz_period_report_snapshot
				where snapshot_id = ?
				and report_code = ?
				""", (rs, rowNum) -> new PeriodCloseService.ReportSnapshotResponse(rs.getString("report_code"),
				reportName(rs.getString("report_code")), rs.getInt("schema_version"),
				readJson(rs.getString("result_json")), rs.getInt("source_count"), rs.getString("fingerprint"),
				rs.getObject("created_at", OffsetDateTime.class), true, true, null),
				snapshotId, normalized);
		if (rows.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
		return rows.getFirst();
	}

	private void writeInventorySnapshots(long snapshotId, PeriodClosePeriodRow period) {
		this.jdbcTemplate.update("""
				insert into biz_period_inventory_snapshot (
					snapshot_id, warehouse_id, warehouse_name, material_id, material_code, material_name,
					quality_status, ownership_type, project_id, project_no, batch_id, serial_id, cost_layer_id,
					ending_quantity, locked_quantity, available_quantity, valuation_state, unit_cost,
					ending_amount, in_quantity, out_quantity, adjustment_quantity, fingerprint
				)
				select ?, b.warehouse_id, w.name, b.material_id, m.code, m.name, b.quality_status, b.ownership_type,
				       b.project_id, p.project_no, b.batch_id, b.serial_id, b.cost_layer_id,
				       b.quantity_on_hand, b.locked_quantity, b.quantity_on_hand - b.locked_quantity,
				       b.valuation_state, b.average_unit_cost, b.inventory_amount,
				       coalesce(mv.in_quantity, 0), coalesce(mv.out_quantity, 0),
				       coalesce(mv.adjustment_quantity, 0),
				       md5(concat_ws('|', b.id, b.warehouse_id, b.material_id, b.quality_status,
				           b.ownership_type, coalesce(b.project_id, 0), coalesce(b.batch_id, 0),
				           coalesce(b.serial_id, 0), coalesce(b.cost_layer_id, 0), b.quantity_on_hand,
				           b.locked_quantity, coalesce(b.inventory_amount, 0), b.valuation_state))
				from inv_stock_balance b
				join mst_warehouse w on w.id = b.warehouse_id
				join mst_material m on m.id = b.material_id
				left join sal_project p on p.id = b.project_id
				left join (
					select warehouse_id, material_id, quality_status, ownership_type, project_id, batch_id,
					       serial_id, cost_layer_id,
					       sum(case when direction = 'IN' then quantity else 0 end) as in_quantity,
					       sum(case when direction = 'OUT' then quantity else 0 end) as out_quantity,
					       sum(case when movement_type like 'ADJUSTMENT%%' or movement_type = 'VALUATION_ADJUSTMENT'
					                then quantity else 0 end) as adjustment_quantity
					from inv_stock_movement
					where business_date between ? and ?
					group by warehouse_id, material_id, quality_status, ownership_type, project_id, batch_id,
					         serial_id, cost_layer_id
				) mv on mv.warehouse_id = b.warehouse_id
					and mv.material_id = b.material_id
					and mv.quality_status = b.quality_status
					and mv.ownership_type = b.ownership_type
					and mv.project_id is not distinct from b.project_id
					and mv.batch_id is not distinct from b.batch_id
					and mv.serial_id is not distinct from b.serial_id
					and mv.cost_layer_id is not distinct from b.cost_layer_id
				order by b.id
				""", snapshotId, period.startDate(), period.endDate());
	}

	private void writeInventorySummary(long snapshotId) {
		this.jdbcTemplate.update("""
				insert into biz_period_inventory_summary (
					snapshot_id, summary_type, quantity, amount, item_count, risk_count, fingerprint
				)
				select ?, 'BALANCE_TOTAL', coalesce(sum(b.quantity_on_hand), 0),
				       coalesce(sum(coalesce(b.inventory_amount, 0)), 0), count(*),
				       count(*) filter (
				       where b.quantity_on_hand > 0
				       and coalesce(m.inventory_value_enabled, false)
				       and b.valuation_state <> 'VALUED'
				       ),
				       md5(concat_ws('|', count(*), coalesce(sum(b.quantity_on_hand), 0),
				           coalesce(sum(coalesce(b.inventory_amount, 0)), 0)))
				from inv_stock_balance b
				join mst_material m on m.id = b.material_id
				""", snapshotId);
	}

	private void writeWipSnapshots(long snapshotId, PeriodClosePeriodRow period) {
		this.jdbcTemplate.update("""
				insert into biz_period_wip_snapshot (
					snapshot_id, project_id, project_no, work_order_id, work_order_no, product_material_id,
					product_material_code, product_material_name, status, planned_quantity, issued_quantity,
					reported_quantity, qualified_quantity, completed_quantity, wip_quantity, wip_cost, fingerprint
				)
				select ?, wo.project_id, p.project_no, wo.id, wo.work_order_no, wo.product_material_id,
				       m.code, m.name, wo.status, wo.planned_quantity,
				       coalesce(sum(wom.issued_quantity), 0), wo.reported_quantity, wo.qualified_quantity,
				       wo.received_quantity, greatest(wo.planned_quantity - wo.received_quantity, 0),
				       coalesce(costs.amount, 0),
				       md5(concat_ws('|', wo.id, wo.status, wo.planned_quantity, wo.reported_quantity,
				           wo.qualified_quantity, wo.received_quantity, coalesce(costs.amount, 0)))
				from mfg_work_order wo
				join mst_material m on m.id = wo.product_material_id
				left join sal_project p on p.id = wo.project_id
				left join mfg_work_order_material wom on wom.work_order_id = wo.id
				left join (
					select work_order_id, coalesce(sum(coalesce(amount, 0)), 0) as amount
					from mfg_cost_record
					where status = 'ACTIVE'
					and business_date <= ?
					group by work_order_id
				) costs on costs.work_order_id = wo.id
				where wo.status in ('RELEASED', 'IN_PROGRESS')
				or (wo.status = 'COMPLETED' and wo.completed_at::date >= ?)
				group by wo.id, p.project_no, m.code, m.name, costs.amount
				order by wo.id
				""", snapshotId, period.endDate(), period.startDate());
	}

	private void writeProjectCostSnapshots(long snapshotId, PeriodClosePeriodRow period) {
		this.jdbcTemplate.update("""
				insert into biz_period_project_cost_snapshot (
					snapshot_id, project_id, project_no, project_name, calculation_id, calculation_no,
					source_fingerprint, freshness_status, completeness_status, project_cost_total, wip_cost,
					finished_cost, delivered_cost, direct_project_cost, shipment_revenue, shipment_gross_margin,
					blocking_variance_count, warning_variance_count, fingerprint
				)
				select ?, c.project_id, p.project_no, p.name, c.id, c.calculation_no, c.source_fingerprint,
				       case when c.is_current then 'CURRENT' else 'STALE' end,
				       c.margin_completeness, c.project_cost_total, c.wip_cost, c.finished_cost,
				       c.delivered_cost, c.direct_project_cost, c.shipment_revenue, c.shipment_gross_margin,
				       coalesce(v.blocking_count, 0), coalesce(v.warning_count, 0),
				       md5(concat_ws('|', c.id, c.source_fingerprint, c.is_current,
				           c.margin_completeness, c.project_cost_total, c.wip_cost, c.finished_cost,
				           c.delivered_cost, c.direct_project_cost, c.shipment_revenue,
				           coalesce(c.shipment_gross_margin, 0), coalesce(v.blocking_count, 0),
				           coalesce(v.warning_count, 0)))
				from prj_cost_calculation c
				join sal_project p on p.id = c.project_id
				left join (
					select calculation_id,
					       count(*) filter (where severity = 'BLOCKING' and status = 'OPEN') as blocking_count,
					       count(*) filter (where severity = 'WARNING' and status = 'OPEN') as warning_count
					from prj_cost_variance
					group by calculation_id
				) v on v.calculation_id = c.id
				where c.cutoff_date = ?
				and c.status in ('CALCULATED', 'CONFIRMED')
				and c.is_current = true
				order by c.project_id, c.id
				""", snapshotId, period.endDate());
	}

	private void writeReportSnapshots(long snapshotId, PeriodClosePeriodRow period) {
		for (String reportCode : REPORT_CODES) {
			Object result = reportResult(reportCode, period);
			String resultJson = toJson(result);
			int sourceCount = sourceCount(result);
			String reportFingerprint = fingerprint(reportCode, resultJson);
			this.jdbcTemplate.update("""
					insert into biz_period_report_snapshot (
						snapshot_id, report_code, schema_version, result_json, source_count, fingerprint
					)
					values (?, ?, ?, cast(? as jsonb), ?, ?)
					""", snapshotId, reportCode, SCHEMA_VERSION, resultJson, sourceCount, reportFingerprint);
		}
	}

	private Object reportResult(String reportCode, PeriodClosePeriodRow period) {
		LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
		parameters.add("dateFrom", period.startDate().toString());
		parameters.add("dateTo", period.endDate().toString());
		parameters.add("page", "1");
		parameters.add("pageSize", "100");
		return switch (reportCode) {
			case "OVERVIEW" -> this.reportingAdminService.overview(parameters);
			case "SALES_SUMMARY" -> this.reportingAdminService.salesSummary(parameters);
			case "PROCUREMENT_SUMMARY" -> this.reportingAdminService.procurementSummary(parameters);
			case "INVENTORY_STOCK_FLOW" -> this.reportingAdminService.inventoryStockFlow(parameters);
			case "PRODUCTION_EXECUTION" -> this.reportingAdminService.productionExecution(parameters);
			case "COST_COLLECTION" -> this.reportingAdminService.costCollection(parameters);
			case "SETTLEMENT_SUMMARY" -> this.reportingAdminService.settlementSummary(parameters);
			case "EXCEPTIONS" -> this.reportingAdminService.exceptions(parameters);
			default -> throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		};
	}

	private int sourceCount(Object result) {
		if (result instanceof ReportingAdminService.ReportPageResponse<?> page) {
			return Math.toIntExact(page.total());
		}
		return 0;
	}

	private String toJson(Object value) {
		try {
			return this.objectMapper.writeValueAsString(value);
		}
		catch (Exception exception) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
	}

	private Long requireSnapshot(PeriodCloseRunRow run) {
		if (run.snapshotId() == null) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
		return run.snapshotId();
	}

	private PeriodCloseService.InventorySnapshotResponse mapInventorySnapshot(ResultSet rs, boolean amountVisible)
			throws SQLException {
		return new PeriodCloseService.InventorySnapshotResponse(rs.getLong("id"), nullableLong(rs, "warehouse_id"),
				rs.getString("warehouse_name"), nullableLong(rs, "material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getString("quality_status"), rs.getString("ownership_type"),
				nullableLong(rs, "project_id"), rs.getString("project_no"), nullableLong(rs, "batch_id"),
				nullableLong(rs, "serial_id"), nullableLong(rs, "cost_layer_id"),
				quantity(rs.getBigDecimal("ending_quantity")), quantity(rs.getBigDecimal("locked_quantity")),
				quantity(rs.getBigDecimal("available_quantity")), rs.getString("valuation_state"),
				amountVisible ? unitCost(rs.getBigDecimal("unit_cost")) : null,
				amountVisible ? amount(rs.getBigDecimal("ending_amount")) : null,
				quantity(rs.getBigDecimal("in_quantity")), quantity(rs.getBigDecimal("out_quantity")),
				quantity(rs.getBigDecimal("adjustment_quantity")), amountVisible, true,
				amountVisible ? null : "缺少库存金额权限", rs.getString("fingerprint"));
	}

	private PeriodCloseService.WipSnapshotResponse mapWipSnapshot(ResultSet rs, boolean amountVisible)
			throws SQLException {
		return new PeriodCloseService.WipSnapshotResponse(rs.getLong("id"), nullableLong(rs, "project_id"),
				rs.getString("project_no"), nullableLong(rs, "work_order_id"), rs.getString("work_order_no"),
				nullableLong(rs, "product_material_id"), rs.getString("product_material_code"),
				rs.getString("product_material_name"), rs.getString("status"),
				quantity(rs.getBigDecimal("planned_quantity")), quantity(rs.getBigDecimal("issued_quantity")),
				quantity(rs.getBigDecimal("reported_quantity")), quantity(rs.getBigDecimal("qualified_quantity")),
				quantity(rs.getBigDecimal("completed_quantity")), quantity(rs.getBigDecimal("wip_quantity")),
				amountVisible ? amount(rs.getBigDecimal("wip_cost")) : null, amountVisible, true,
				amountVisible ? null : "缺少项目成本金额权限", rs.getString("fingerprint"));
	}

	private PeriodCloseService.ProjectCostSnapshotResponse mapProjectCostSnapshot(ResultSet rs, boolean amountVisible)
			throws SQLException {
		return new PeriodCloseService.ProjectCostSnapshotResponse(rs.getLong("id"), rs.getLong("project_id"),
				rs.getString("project_no"), rs.getString("project_name"), rs.getLong("calculation_id"),
				rs.getString("calculation_no"), rs.getString("source_fingerprint"), rs.getString("freshness_status"),
				rs.getString("completeness_status"), amountVisible ? amount(rs.getBigDecimal("project_cost_total")) : null,
				amountVisible ? amount(rs.getBigDecimal("wip_cost")) : null,
				amountVisible ? amount(rs.getBigDecimal("finished_cost")) : null,
				amountVisible ? amount(rs.getBigDecimal("delivered_cost")) : null,
				amountVisible ? amount(rs.getBigDecimal("direct_project_cost")) : null,
				amountVisible ? amount(rs.getBigDecimal("shipment_revenue")) : null,
				amountVisible ? amount(rs.getBigDecimal("shipment_gross_margin")) : null,
				rs.getInt("blocking_variance_count"), rs.getInt("warning_variance_count"), amountVisible, true,
				amountVisible ? null : "缺少项目成本金额权限", rs.getString("fingerprint"));
	}

	private List<String> reportCodes(Long snapshotId) {
		return this.jdbcTemplate.queryForList("""
				select report_code
				from biz_period_report_snapshot
				where snapshot_id = ?
				order by array_position(array[
					'OVERVIEW', 'SALES_SUMMARY', 'PROCUREMENT_SUMMARY', 'INVENTORY_STOCK_FLOW',
					'PRODUCTION_EXECUTION', 'COST_COLLECTION', 'SETTLEMENT_SUMMARY', 'EXCEPTIONS'
				], report_code)
				""", String.class, snapshotId);
	}

	private Long count(String tableName, Long snapshotId) {
		return this.jdbcTemplate.queryForObject("select count(*) from " + tableName + " where snapshot_id = ?",
				Long.class, snapshotId);
	}

	private tools.jackson.databind.JsonNode readJson(String json) {
		try {
			return this.objectMapper.readTree(json);
		}
		catch (Exception exception) {
			throw new BusinessException(ApiErrorCode.PERIOD_CLOSE_SNAPSHOT_INCOMPLETE);
		}
	}

	private String reportName(String reportCode) {
		return Map.of(
				"OVERVIEW", "经营概览",
				"SALES_SUMMARY", "销售汇总",
				"PROCUREMENT_SUMMARY", "采购汇总",
				"INVENTORY_STOCK_FLOW", "库存收发存",
				"PRODUCTION_EXECUTION", "生产执行",
				"COST_COLLECTION", "成本归集",
				"SETTLEMENT_SUMMARY", "结算汇总",
				"EXCEPTIONS", "异常清单").getOrDefault(reportCode, reportCode);
	}

}
