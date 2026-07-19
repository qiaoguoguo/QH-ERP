package com.qherp.api.system.periodclose;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.qherp.api.system.periodclose.PeriodCloseSupport.fingerprint;

@Service
class PeriodCloseCheckService {

	private final JdbcTemplate jdbcTemplate;

	PeriodCloseCheckService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	PeriodCloseCheckResult evaluate(PeriodClosePeriodRow period) {
		String inventoryFingerprint = inventoryFingerprint(period);
		String wipFingerprint = wipFingerprint(period);
		String projectCostFingerprint = projectCostFingerprint(period);
		String reportFingerprint = reportFingerprint(period);
		String sourceFingerprint = fingerprint(inventoryFingerprint, wipFingerprint, projectCostFingerprint,
				reportFingerprint);
		List<PeriodCloseCheckItemDraft> items = new ArrayList<>();
		if (inventoryUnvaluedCount() > 0) {
			items.add(blocking("INVENTORY", "INVENTORY_UNVALUED_SOURCE", "存在未完成估值的库存来源",
					"截止日存在启用库存计价但未完成估值的库存余额或价值来源。",
					"先完成 023 库存计价、价值调整或来源修复后再执行月结。"));
		}
		if (inventoryOpenProcessCount(period.endDate()) > 0) {
			items.add(inventoryOpenProcessItem(period.endDate()));
		}
		for (PeriodCloseProjectActivity activity : projectActivities(period)) {
			PeriodCloseProjectCostState state = projectCostState(activity.projectId(), period.endDate());
			if (state == null) {
				items.add(blocking("PROJECT_COST", "PROJECT_COST_MISSING_AT_CUTOFF", "缺少项目成本截止日核算",
						"项目存在期间内成本来源活动，但没有截止日精确等于期间结束日的有效项目成本运行。",
						"按 029 项目成本口径在期间结束日生成并保留有效运行。", "PROJECT", activity.projectId(),
						activity.projectNo(), route("/cost/project-costs", "projectNo", activity.projectNo())));
				continue;
			}
			if (!state.current()) {
				items.add(blocking("PROJECT_COST", "PROJECT_COST_STALE", "项目成本来源不是当前版本",
						"项目成本运行不是当前有效版本，关闭时不能作为冻结基线。",
						"重新检查 029 项目成本来源并保留当前版本。"));
			}
			if (state.blockingVarianceCount() > 0) {
				items.add(blocking("PROJECT_COST", "PROJECT_COST_BLOCKING_VARIANCE", "项目成本存在开放阻断差异",
						"项目成本运行存在未解决的 BLOCKING 差异或来源断链。",
						"先处理 029 项目成本差异，再重新执行月结检查。"));
			}
			if ("INCOMPLETE".equals(state.completenessStatus()) && state.blockingVarianceCount() == 0) {
				items.add(warning("PROJECT_COST", "PROJECT_HAS_WIP", "项目成本包含未完工或暂估口径",
						"项目成本运行完整性为 INCOMPLETE，但未发现开放阻断差异。",
						"确认 WIP 或合法暂估仍可作为本期经营快照基线。"));
			}
		}
		if (wipWarningCount(period.endDate()) > 0) {
			items.add(warning("WIP", "PROJECT_HAS_WIP", "期间存在在制生产",
					"截止日仍存在未完成生产工单或在制数量，本状态允许月结但会进入快照说明。",
					"确认在制状态真实、可解释后继续关闭。"));
		}
		if (previousClosedSnapshotCount(period) == 0) {
			items.add(warning("PERIOD", "NO_PREVIOUS_SNAPSHOT", "没有上一期间快照",
					"本期间没有可引用的上一业务月结快照，将从既有不可变业务事实生成首期快照。",
					"确认当前为首期或历史期间不补快照后继续关闭。"));
		}
		if (activityCount(period) == 0) {
			items.add(warning("REPORT", "NO_ACTIVITY", "期间没有业务发生",
					"本期间库存、生产、成本或往来核心来源为空，快照分区可能为空。",
					"确认该期间确无业务或仅用于初始化后继续关闭。"));
		}
		int blockingCount = (int) items.stream().filter((item) -> "BLOCKING".equals(item.severity())).count();
		int warningCount = (int) items.stream().filter((item) -> "WARNING".equals(item.severity())).count();
		PeriodCloseStatus status = blockingCount > 0 ? PeriodCloseStatus.BLOCKED : PeriodCloseStatus.READY;
		return new PeriodCloseCheckResult(status, sourceFingerprint, inventoryFingerprint, wipFingerprint,
				projectCostFingerprint, reportFingerprint, blockingCount, warningCount, items);
	}

	private String inventoryFingerprint(PeriodClosePeriodRow period) {
		return fingerprint(
				scalar("""
						select coalesce(string_agg(concat_ws('|', b.id, b.warehouse_id, b.material_id,
							b.quality_status, b.ownership_type, coalesce(b.project_id, 0), coalesce(b.batch_id, 0),
							coalesce(b.serial_id, 0), coalesce(b.cost_layer_id, 0), b.quantity_on_hand,
							b.locked_quantity, b.valuation_state, coalesce(b.average_unit_cost, 0),
							coalesce(b.inventory_amount, 0), b.version), E'\\n' order by b.id), '')
						from inv_stock_balance b
						"""),
				scalar("""
						select coalesce(string_agg(concat_ws('|', id, movement_no, movement_type, direction,
							warehouse_id, material_id, quality_status, ownership_type, coalesce(project_id, 0),
							coalesce(batch_id, 0), coalesce(serial_id, 0), coalesce(cost_layer_id, 0),
							quantity, business_date), E'\\n' order by id), '')
						from inv_stock_movement
						where business_date <= ?
						""", period.endDate()));
	}

	private String wipFingerprint(PeriodClosePeriodRow period) {
		return fingerprint(
				scalar("""
						select coalesce(string_agg(concat_ws('|', id, work_order_no, status, ownership_type,
							coalesce(project_id, 0), product_material_id, planned_quantity, reported_quantity,
							qualified_quantity, defective_quantity, received_quantity, version), E'\\n' order by id), '')
						from mfg_work_order
						"""),
				scalar("""
						select coalesce(string_agg(concat_ws('|', id, record_no, work_order_id, cost_type,
							source_type, coalesce(amount, 0), business_date, status, version), E'\\n' order by id), '')
						from mfg_cost_record
						where business_date <= ?
						""", period.endDate()));
	}

	private String projectCostFingerprint(PeriodClosePeriodRow period) {
		return fingerprint(
				scalar("""
						select coalesce(string_agg(concat_ws('|', id, project_id, calculation_no, cutoff_date, status,
							is_current, source_fingerprint, project_cost_total, wip_cost, finished_cost,
							delivered_cost, direct_project_cost, shipment_revenue, coalesce(shipment_gross_margin, 0),
							margin_completeness, version), E'\\n' order by id), '')
						from prj_cost_calculation
						where cutoff_date <= ?
						""", period.endDate()),
				scalar("""
						select coalesce(string_agg(concat_ws('|', id, calculation_id, project_id, variance_type,
							severity, status, coalesce(variance_amount, 0), coalesce(source_type, ''),
							coalesce(source_id, 0), coalesce(source_line_id, 0)), E'\\n' order by id), '')
						from prj_cost_variance
						"""));
	}

	private String reportFingerprint(PeriodClosePeriodRow period) {
		return fingerprint(
				scalar("select count(*) from sal_sales_order where order_date <= ?", period.endDate()),
				scalar("select count(*) from proc_purchase_order where order_date <= ?", period.endDate()),
				scalar("select count(*) from fin_receivable where business_date <= ?", period.endDate()),
				scalar("select count(*) from fin_payable where business_date <= ?", period.endDate()));
	}

	private long inventoryUnvaluedCount() {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_balance b
				join mst_material m on m.id = b.material_id
				where b.quantity_on_hand > 0
				and coalesce(m.inventory_value_enabled, false)
				and b.valuation_state <> 'VALUED'
				""", Long.class);
	}

	private long inventoryOpenProcessCount(LocalDate endDate) {
		return this.jdbcTemplate.queryForObject("""
				select
					(select count(*) from inv_stocktake where business_date <= ?
						and status in ('COUNTING', 'RECONCILED', 'SUBMITTED'))
					+ (select count(*) from inv_valuation_adjustment where business_date <= ?
						and status = 'SUBMITTED')
					+ (select count(*) from inv_ownership_conversion where business_date <= ?
						and status = 'SUBMITTED')
					+ (select count(*) from inv_stocktake_range_lock where released_at is null)
				""", Long.class, endDate, endDate, endDate);
	}

	private List<PeriodCloseProjectActivity> projectActivities(PeriodClosePeriodRow period) {
		return this.jdbcTemplate.query("""
				select distinct p.project_id, sp.project_no
				from (
					select project_id
					from inv_value_movement
					where project_id is not null
					and business_date between ? and ?
					union
					select wo.project_id
					from mfg_cost_record cr
					join mfg_work_order wo on wo.id = cr.work_order_id
					where wo.project_id is not null
					and cr.business_date between ? and ?
					union
					select project_id
					from mfg_work_order
					where project_id is not null
					and coalesce(planned_start_date, planned_finish_date, ?::date) <= ?
					and coalesce(planned_finish_date, planned_start_date, ?::date) >= ?
				) p
				join sal_project sp on sp.id = p.project_id
				where p.project_id is not null
				order by p.project_id
				""", (rs, rowNum) -> new PeriodCloseProjectActivity(rs.getLong("project_id"),
				rs.getString("project_no")), period.startDate(),
				period.endDate(), period.startDate(), period.endDate(), period.startDate(), period.endDate(),
				period.endDate(), period.startDate());
	}

	private PeriodCloseProjectCostState projectCostState(Long projectId, LocalDate cutoffDate) {
		List<PeriodCloseProjectCostState> rows = this.jdbcTemplate.query("""
				select c.id, c.is_current, c.margin_completeness,
				       coalesce(v.blocking_count, 0) as blocking_count
				from prj_cost_calculation c
				left join (
					select calculation_id, count(*) as blocking_count
					from prj_cost_variance
					where severity = 'BLOCKING'
					and status = 'OPEN'
					group by calculation_id
				) v on v.calculation_id = c.id
				where c.project_id = ?
				and c.cutoff_date = ?
				and c.status in ('CALCULATED', 'CONFIRMED')
				order by c.id desc
				limit 1
				""", (rs, rowNum) -> new PeriodCloseProjectCostState(rs.getLong("id"), rs.getBoolean("is_current"),
				rs.getString("margin_completeness"), rs.getLong("blocking_count")), projectId, cutoffDate);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private long wipWarningCount(LocalDate endDate) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_work_order
				where status in ('RELEASED', 'IN_PROGRESS')
				and coalesce(planned_start_date, planned_finish_date, ?::date) <= ?
				""", Long.class, endDate, endDate);
	}

	private long previousClosedSnapshotCount(PeriodClosePeriodRow period) {
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from biz_period_close_run r
				join biz_business_period p on p.id = r.period_id
				where r.status = 'CLOSED'
				and r.snapshot_id is not null
				and p.end_date < ?
				""", Long.class, period.startDate());
	}

	private long activityCount(PeriodClosePeriodRow period) {
		return this.jdbcTemplate.queryForObject("""
				select
					(select count(*) from inv_stock_movement where business_date between ? and ?)
					+ (select count(*) from mfg_cost_record where business_date between ? and ?)
					+ (select count(*) from sal_sales_order where order_date between ? and ?)
					+ (select count(*) from proc_purchase_order where order_date between ? and ?)
					+ (select count(*) from fin_receivable where business_date between ? and ?)
					+ (select count(*) from fin_payable where business_date between ? and ?)
				""", Long.class, period.startDate(), period.endDate(), period.startDate(), period.endDate(),
				period.startDate(), period.endDate(), period.startDate(), period.endDate(), period.startDate(),
				period.endDate(), period.startDate(), period.endDate());
	}

	private String scalar(String sql, Object... args) {
		Object value = this.jdbcTemplate.queryForObject(sql, Object.class, args);
		return String.valueOf(value);
	}

	private PeriodCloseCheckItemDraft blocking(String domain, String code, String title, String description,
			String suggestion) {
		return new PeriodCloseCheckItemDraft(domain, code, "BLOCKING", false, null, null, null, title, description,
				suggestion, "{}");
	}

	private PeriodCloseCheckItemDraft blocking(String domain, String code, String title, String description,
			String suggestion, String objectType, Long objectId, String objectNo, String sourceRouteJson) {
		return new PeriodCloseCheckItemDraft(domain, code, "BLOCKING", false, objectType, objectId, objectNo, title,
				description, suggestion, sourceRouteJson);
	}

	private PeriodCloseCheckItemDraft warning(String domain, String code, String title, String description,
			String suggestion) {
		return new PeriodCloseCheckItemDraft(domain, code, "WARNING", false, null, null, null, title, description,
				suggestion, "{}");
	}

	private PeriodCloseCheckItemDraft inventoryOpenProcessItem(LocalDate endDate) {
		List<PeriodCloseCheckItemDraft> stocktakes = this.jdbcTemplate.query("""
				select id, stocktake_no
				from inv_stocktake
				where business_date <= ?
				and status in ('COUNTING', 'RECONCILED', 'SUBMITTED')
				order by business_date, id
				limit 1
				""", (rs, rowNum) -> blocking("INVENTORY", "INVENTORY_PROCESS_OPEN", "存在未终态库存流程",
				"截止日及以前存在盘点、估值调整、所有权转换或盘点范围锁等未终态流程。",
				"先完成、取消或释放相关库存流程后再执行月结。", "INVENTORY_STOCKTAKE", rs.getLong("id"),
				rs.getString("stocktake_no"), route("/inventory/stocktakes", "stocktakeNo",
						rs.getString("stocktake_no"))), endDate);
		if (!stocktakes.isEmpty()) {
			return stocktakes.getFirst();
		}
		return blocking("INVENTORY", "INVENTORY_PROCESS_OPEN", "存在未终态库存流程",
				"截止日及以前存在盘点、估值调整、所有权转换或盘点范围锁等未终态流程。",
				"先完成、取消或释放相关库存流程后再执行月结。");
	}

	private String route(String path, String key, String value) {
		return "{\"path\":\"" + json(path) + "\",\"query\":{\"" + json(key) + "\":\"" + json(value) + "\"}}";
	}

	private String json(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
