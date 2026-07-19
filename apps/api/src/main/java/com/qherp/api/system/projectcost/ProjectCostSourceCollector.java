package com.qherp.api.system.projectcost;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
public class ProjectCostSourceCollector {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

	private static final BigDecimal ONE = BigDecimal.ONE.setScale(10, RoundingMode.HALF_UP);

	private final JdbcTemplate jdbcTemplate;

	public ProjectCostSourceCollector(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public CollectionResult collect(Long projectId, LocalDate cutoffDate) {
		List<SourceLineDraft> sources = new ArrayList<>();
		List<VarianceDraft> variances = new ArrayList<>();
		collectMaterialValueMovements(projectId, cutoffDate, sources, variances);
		collectOutsourcingMaterialIssues(projectId, cutoffDate, sources, variances);
		collectCostRecords(projectId, cutoffDate, sources, variances);
		collectOutsourcing(projectId, cutoffDate, sources, variances);
		collectProjectExpenses(projectId, cutoffDate, sources);
		collectConfirmedAdjustments(projectId, cutoffDate, sources);
		RevenueSummary revenue = revenue(projectId, cutoffDate);
		return new CollectionResult(sources, variances, revenue, fingerprint(sources));
	}

	private void collectMaterialValueMovements(Long projectId, LocalDate cutoffDate, List<SourceLineDraft> sources,
			List<VarianceDraft> variances) {
		collectProductionValueMovement("""
				select vm.id, vm.source_type, vm.source_id, vm.source_line_id, vm.movement_no, vm.quantity,
				       vm.unit_cost, vm.inventory_amount, vm.valuation_state, vm.business_date,
				       wo.product_material_id, wo.planned_quantity,
				       coalesce((
					       select sum(r.quantity)
					       from mfg_completion_receipt r
					       where r.work_order_id = wo.id
					       and r.status = 'POSTED'
					       and r.business_date <= ?
				       ), 0) as finished_quantity
				from inv_value_movement vm
				join mfg_material_issue_line l on l.id = vm.source_line_id
				join mfg_material_issue h on h.id = l.issue_id
				join mfg_work_order wo on wo.id = h.work_order_id
				where vm.source_type = 'PRODUCTION_MATERIAL_ISSUE'
				and h.status = 'POSTED'
				and wo.project_id = ?
				and vm.business_date <= ?
				""", projectId, cutoffDate, BigDecimal.ONE, "ACTUAL", sources, variances);
		collectProductionValueMovement("""
				select vm.id, vm.source_type, vm.source_id, vm.source_line_id, vm.movement_no, vm.quantity,
				       vm.unit_cost, vm.inventory_amount, vm.valuation_state, vm.business_date,
				       wo.product_material_id, wo.planned_quantity,
				       coalesce((
					       select sum(r.quantity)
					       from mfg_completion_receipt r
					       where r.work_order_id = wo.id
					       and r.status = 'POSTED'
					       and r.business_date <= ?
				       ), 0) as finished_quantity
				from inv_value_movement vm
				join mfg_material_return_line l on l.id = vm.source_line_id
				join mfg_material_return h on h.id = l.return_id
				join mfg_work_order wo on wo.id = h.work_order_id
				where vm.source_type = 'PRODUCTION_MATERIAL_RETURN'
				and h.status = 'POSTED'
				and wo.project_id = ?
				and vm.business_date <= ?
				""", projectId, cutoffDate, BigDecimal.ONE.negate(), "ADJUSTED", sources, variances);
		collectProductionValueMovement("""
				select vm.id, vm.source_type, vm.source_id, vm.source_line_id, vm.movement_no, vm.quantity,
				       vm.unit_cost, vm.inventory_amount, vm.valuation_state, vm.business_date,
				       wo.product_material_id, wo.planned_quantity,
				       coalesce((
					       select sum(r.quantity)
					       from mfg_completion_receipt r
					       where r.work_order_id = wo.id
					       and r.status = 'POSTED'
					       and r.business_date <= ?
				       ), 0) as finished_quantity
				from inv_value_movement vm
				join mfg_material_supplement_line l on l.id = vm.source_line_id
				join mfg_material_supplement h on h.id = l.supplement_id
				join mfg_work_order wo on wo.id = h.work_order_id
				where vm.source_type = 'PRODUCTION_MATERIAL_SUPPLEMENT'
				and h.status = 'POSTED'
				and wo.project_id = ?
				and vm.business_date <= ?
				""", projectId, cutoffDate, BigDecimal.ONE, "ADJUSTED", sources, variances);
	}

	private void collectProductionValueMovement(String sql, Long projectId, LocalDate cutoffDate, BigDecimal sign,
			String sourceStatus, List<SourceLineDraft> sources, List<VarianceDraft> variances) {
		List<ValueMovementRow> rows = this.jdbcTemplate.query(sql, this::mapValueMovement, cutoffDate, projectId,
				cutoffDate);
		for (ValueMovementRow row : rows) {
			if (!"VALUED".equals(row.valuationState()) || row.inventoryAmount() == null) {
				variances.add(new VarianceDraft(projectId, "SOURCE_UNVALUED", "BLOCKING", "OPEN", false, null,
						"库存价值来源未完成估值", row.sourceType(), row.sourceId(), row.sourceLineId(), "MATERIAL"));
				continue;
			}
			BigDecimal amount = scale(row.inventoryAmount()).multiply(sign).setScale(2, RoundingMode.HALF_UP);
			addStagedSource(sources, projectId, "MATERIAL", row.sourceType(), row.sourceId(), row.sourceLineId(),
					row.movementNo(), sourceStatus, row.businessDate(), row.quantity(), row.unitCost(), amount,
					new StageContext(row.productMaterialId(), row.plannedQuantity(), row.finishedQuantity(), cutoffDate));
		}
	}

	private void collectOutsourcingMaterialIssues(Long projectId, LocalDate cutoffDate, List<SourceLineDraft> sources,
			List<VarianceDraft> variances) {
		List<ValueMovementRow> rows = this.jdbcTemplate.query("""
				select vm.id, vm.source_type, vm.source_id, vm.source_line_id, vm.movement_no, vm.quantity,
				       vm.unit_cost, vm.inventory_amount, vm.valuation_state, vm.business_date,
				       o.product_material_id, o.planned_quantity,
				       coalesce((
					       select sum(rl.accepted_quantity)
					       from mfg_outsourcing_receipt_line rl
					       join mfg_outsourcing_receipt r on r.id = rl.receipt_id
					       where r.outsourcing_order_id = o.id
					       and r.status = 'POSTED'
					       and r.business_date <= ?
				       ), 0) as finished_quantity
				from inv_value_movement vm
				join mfg_outsourcing_issue_line l on l.id = vm.source_line_id
				join mfg_outsourcing_issue h on h.id = l.issue_id
				join mfg_outsourcing_order o on o.id = h.outsourcing_order_id
				where vm.source_type = 'PRODUCTION_OUTSOURCING_ISSUE'
				and h.status = 'POSTED'
				and o.project_id = ?
				and vm.business_date <= ?
				""", this::mapValueMovement, cutoffDate, projectId, cutoffDate);
		for (ValueMovementRow row : rows) {
			if (!"VALUED".equals(row.valuationState()) || row.inventoryAmount() == null) {
				variances.add(new VarianceDraft(projectId, "SOURCE_UNVALUED", "BLOCKING", "OPEN", false, null,
						"外协发料库存价值未完成估值", row.sourceType(), row.sourceId(), row.sourceLineId(), "MATERIAL"));
				continue;
			}
			BigDecimal amount = scale(row.inventoryAmount());
			addStagedSource(sources, projectId, "MATERIAL", row.sourceType(), row.sourceId(), row.sourceLineId(),
					row.movementNo(), "ACTUAL", row.businessDate(), row.quantity(), row.unitCost(), amount,
					new StageContext(row.productMaterialId(), row.plannedQuantity(), row.finishedQuantity(), cutoffDate));
		}
	}

	private void collectCostRecords(Long projectId, LocalDate cutoffDate, List<SourceLineDraft> sources,
			List<VarianceDraft> variances) {
		List<CostRecordRow> rows = this.jdbcTemplate.query("""
				select cr.id, cr.record_no, cr.cost_type, cr.source_type, cr.source_document_type,
				       cr.source_document_id, cr.source_line_id, cr.quantity, cr.unit_price, cr.amount,
				       cr.basis_type, cr.business_date, wo.product_material_id, wo.planned_quantity,
				       coalesce((
					       select sum(r.quantity)
					       from mfg_completion_receipt r
					       where r.work_order_id = wo.id
					       and r.status = 'POSTED'
					       and r.business_date <= ?
				       ), 0) as finished_quantity
				from mfg_cost_record cr
				join mfg_work_order wo on wo.id = cr.work_order_id
				where wo.project_id = ?
				and cr.status = 'ACTIVE'
				and cr.business_date <= ?
				order by cr.id
				""", this::mapCostRecord, cutoffDate, projectId, cutoffDate);
		for (CostRecordRow row : rows) {
			if ("MATERIAL".equals(row.costType()) && "AUTO_PRODUCTION".equals(row.sourceType())
					&& "PRODUCTION_MATERIAL_ISSUE".equals(row.sourceDocumentType())) {
				continue;
			}
			String category = switch (row.costType()) {
				case "LABOR" -> "LABOR";
				case "MANUFACTURING_OVERHEAD" -> "MANUFACTURING_OVERHEAD";
				case "OTHER" -> "PROJECT_EXPENSE";
				default -> null;
			};
			if (category == null) {
				continue;
			}
			if (row.amount() == null || "SOURCE_QUANTITY_ONLY".equals(row.basisType())) {
				variances.add(new VarianceDraft(projectId, "SOURCE_UNPRICED", "BLOCKING", "OPEN", false, null,
						row.costType() + " 来源未定价", "MFG_COST_RECORD", row.id(), row.sourceLineId(), category));
				sources.add(new SourceLineDraft(projectId, category,
						"PROJECT_EXPENSE".equals(category) ? "DIRECT_PROJECT" : "WIP",
						"PROJECT_EXPENSE".equals(category) ? "PROJECT_DIRECT" : "SOURCE_TO_WIP",
						"MFG_COST_RECORD", row.id(), row.sourceLineId(), row.recordNo(), "UNPRICED",
						row.businessDate(), row.quantity(), row.unitPrice(), null, ZERO, false));
				continue;
			}
			BigDecimal amount = scale(row.amount());
			if ("PROJECT_EXPENSE".equals(category)) {
				sources.add(new SourceLineDraft(projectId, category, "DIRECT_PROJECT", "PROJECT_DIRECT",
						"MFG_COST_RECORD", row.id(), row.sourceLineId(), row.recordNo(), "ACTUAL",
						row.businessDate(), row.quantity(), row.unitPrice(), amount, amount, false));
			}
			else {
				addStagedSource(sources, projectId, category, "MFG_COST_RECORD", row.id(), row.sourceLineId(),
						row.recordNo(), "ACTUAL", row.businessDate(), row.quantity(), row.unitPrice(), amount,
						new StageContext(row.productMaterialId(), row.plannedQuantity(), row.finishedQuantity(),
								cutoffDate));
			}
		}
	}

	private void collectOutsourcing(Long projectId, LocalDate cutoffDate, List<SourceLineDraft> sources,
			List<VarianceDraft> variances) {
		List<OutsourcingReceiptRow> rows = this.jdbcTemplate.query("""
				select rl.id as receipt_line_id, r.id as receipt_id, r.receipt_no, r.business_date,
				       r.outsourcing_order_id, o.product_material_id, o.planned_quantity,
				       rl.accepted_quantity, rl.provisional_unit_cost
				from mfg_outsourcing_receipt_line rl
				join mfg_outsourcing_receipt r on r.id = rl.receipt_id
				join mfg_outsourcing_order o on o.id = r.outsourcing_order_id
				where r.status = 'POSTED'
				and o.project_id = ?
				and r.business_date <= ?
				order by rl.id
				""", this::mapOutsourcingReceipt, projectId, cutoffDate);
		for (OutsourcingReceiptRow row : rows) {
			BigDecimal provisional = row.provisionalUnitCost() == null ? null
					: scale(row.acceptedQuantity().multiply(row.provisionalUnitCost()));
			BigDecimal actual = actualOutsourcingAmount(row.receiptLineId());
			if (actual == null && provisional == null) {
				variances.add(new VarianceDraft(projectId, "SOURCE_UNPRICED", "BLOCKING", "OPEN", false, null,
						"外协收货没有实际结算且缺少合法暂估", "OUTSOURCING_RECEIPT", row.receiptId(),
						row.receiptLineId(), "OUTSOURCING"));
				addStagedSource(sources, projectId, "OUTSOURCING", "OUTSOURCING_RECEIPT", row.receiptId(),
						row.receiptLineId(), row.receiptNo(), "UNPRICED", row.businessDate(),
						row.acceptedQuantity(), row.provisionalUnitCost(), null,
						new StageContext(row.productMaterialId(), row.plannedQuantity(), row.acceptedQuantity(),
								cutoffDate));
				continue;
			}
			BigDecimal amount = actual == null ? provisional : actual;
			addStagedSource(sources, projectId, "OUTSOURCING", "OUTSOURCING_RECEIPT", row.receiptId(),
					row.receiptLineId(), row.receiptNo(), actual == null ? "PROVISIONAL" : "ACTUAL",
					row.businessDate(), row.acceptedQuantity(), row.provisionalUnitCost(), amount,
					new StageContext(row.productMaterialId(), row.plannedQuantity(), row.acceptedQuantity(), cutoffDate));
			if (actual == null) {
				variances.add(new VarianceDraft(projectId, "OUTSOURCING_PROVISIONAL", "WARNING", "OPEN", false,
						amount, "外协成本仍为暂估", "OUTSOURCING_RECEIPT", row.receiptId(), row.receiptLineId(),
						"OUTSOURCING"));
			}
			else if (provisional != null && provisional.compareTo(actual) != 0) {
				variances.add(new VarianceDraft(projectId, "OUTSOURCING_ACTUAL_VARIANCE", "WARNING", "OPEN",
						false, actual.subtract(provisional).abs().setScale(2, RoundingMode.HALF_UP),
						"外协实际与暂估存在差异", "OUTSOURCING_RECEIPT", row.receiptId(), row.receiptLineId(),
						"OUTSOURCING"));
			}
		}
	}

	private void collectProjectExpenses(Long projectId, LocalDate cutoffDate, List<SourceLineDraft> sources) {
		List<ExpenseLineRow> rows = this.jdbcTemplate.query("""
				select l.id as line_id, e.id as expense_id, e.expense_no, e.expense_date, l.expense_category,
				       l.tax_excluded_amount
				from fin_expense_line l
				join fin_expense e on e.id = l.expense_id
				where e.status = 'CONFIRMED'
				and e.ownership_type = 'PROJECT'
				and e.project_id = ?
				and e.expense_date <= ?
				order by l.id
				""", this::mapExpenseLine, projectId, cutoffDate);
		for (ExpenseLineRow row : rows) {
			String category = normalizeExpenseCategory(row.expenseCategory());
			BigDecimal amount = scale(row.taxExcludedAmount());
			sources.add(new SourceLineDraft(projectId, category, "DIRECT_PROJECT", "PROJECT_DIRECT",
					"FIN_EXPENSE", row.expenseId(), row.lineId(), row.expenseNo(), "ACTUAL", row.expenseDate(),
					null, null, amount, amount, false));
		}
	}

	private void collectConfirmedAdjustments(Long projectId, LocalDate cutoffDate, List<SourceLineDraft> sources) {
		List<AdjustmentLineRow> rows = this.jdbcTemplate.query("""
				select l.id as line_id, a.id as adjustment_id, a.adjustment_no, a.business_date, l.cost_category,
				       l.cost_stage, l.direction, l.amount
				from prj_cost_adjustment_line l
				join prj_cost_adjustment a on a.id = l.adjustment_id
				where a.status = 'CONFIRMED'
				and l.project_id = ?
				and a.business_date <= ?
				order by l.id
				""", this::mapAdjustmentLine, projectId, cutoffDate);
		for (AdjustmentLineRow row : rows) {
			BigDecimal amount = scale(row.amount());
			if ("DECREASE".equals(row.direction())) {
				amount = amount.negate();
			}
			sources.add(new SourceLineDraft(projectId, row.costCategory(), row.costStage(), "PROJECT_ADJUSTMENT",
					"PROJECT_COST_ADJUSTMENT", row.adjustmentId(), row.lineId(), row.adjustmentNo(), "ADJUSTED",
					row.businessDate(), null, null, amount, amount, false));
		}
	}

	private void addStagedSource(List<SourceLineDraft> sources, Long projectId, String category, String sourceType,
			Long sourceId, Long sourceLineId, String sourceNo, String sourceStatus, LocalDate businessDate,
			BigDecimal quantity, BigDecimal unitCost, BigDecimal sourceAmount, StageContext context) {
		BigDecimal amount = sourceAmount == null ? ZERO : scale(sourceAmount);
		StageAmounts staged = stageAmounts(projectId, amount, context);
		addStageSource(sources, projectId, category, "WIP", "SOURCE_TO_WIP", sourceType, sourceId, sourceLineId,
				sourceNo, sourceStatus, businessDate, quantity, unitCost, sourceAmount, staged.wip());
		addStageSource(sources, projectId, category, "FINISHED", "WIP_TO_FINISHED", sourceType, sourceId,
				sourceLineId, sourceNo, sourceStatus, businessDate, quantity, unitCost, sourceAmount, staged.finished());
		addStageSource(sources, projectId, category, "DELIVERED", "FINISHED_TO_DELIVERED", sourceType, sourceId,
				sourceLineId, sourceNo, sourceStatus, businessDate, quantity, unitCost, sourceAmount, staged.delivered());
	}

	private void addStageSource(List<SourceLineDraft> sources, Long projectId, String category, String stage,
			String entryType, String sourceType, Long sourceId, Long sourceLineId, String sourceNo,
			String sourceStatus, LocalDate businessDate, BigDecimal quantity, BigDecimal unitCost,
			BigDecimal sourceAmount, BigDecimal calculatedAmount) {
		if (calculatedAmount.compareTo(BigDecimal.ZERO) == 0 && sourceAmount != null) {
			return;
		}
		sources.add(new SourceLineDraft(projectId, category, stage, entryType, sourceType, sourceId, sourceLineId,
				sourceNo, sourceStatus, businessDate, quantity, unitCost,
				sourceAmount == null ? null : scale(sourceAmount), calculatedAmount, false));
	}

	private StageAmounts stageAmounts(Long projectId, BigDecimal amount, StageContext context) {
		BigDecimal finishRatio = ratio(context.finishedQuantity(), context.plannedQuantity());
		BigDecimal deliveryRatio = ratio(netDeliveredQuantity(projectId, context.productMaterialId(), context.cutoffDate()),
				totalFinishedQuantity(projectId, context.productMaterialId(), context.cutoffDate()));
		BigDecimal finishedPortion = amount.multiply(finishRatio).setScale(2, RoundingMode.HALF_UP);
		BigDecimal delivered = finishedPortion.multiply(deliveryRatio).setScale(2, RoundingMode.HALF_UP);
		BigDecimal finished = finishedPortion.subtract(delivered).setScale(2, RoundingMode.HALF_UP);
		BigDecimal wip = amount.subtract(finishedPortion).setScale(2, RoundingMode.HALF_UP);
		return new StageAmounts(wip, finished, delivered);
	}

	private RevenueSummary revenue(Long projectId, LocalDate cutoffDate) {
		BigDecimal shipmentRevenue = scale(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(sl.tax_excluded_amount), 0)
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				where so.project_id = ?
				and sh.status = 'POSTED'
				and sh.business_date <= ?
				""", BigDecimal.class, projectId, cutoffDate));
		BigDecimal returnRevenue = scale(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(round(rl.quantity * sl.tax_excluded_unit_price, 2)), 0)
				from sal_sales_return_line rl
				join sal_sales_return r on r.id = rl.return_id
				join sal_sales_shipment_line sl on sl.id = rl.source_shipment_line_id
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				where so.project_id = ?
				and r.status = 'POSTED'
				and r.business_date <= ?
				""", BigDecimal.class, projectId, cutoffDate));
		BigDecimal invoiceRevenue = scale(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(tax_excluded_amount), 0)
				from fin_sales_invoice
				where project_id = ?
				and status = 'CONFIRMED'
				and invoice_date <= ?
				""", BigDecimal.class, projectId, cutoffDate));
		BigDecimal targetRevenue = scale(this.jdbcTemplate.queryForObject("""
				select target_revenue
				from sal_project
				where id = ?
				""", BigDecimal.class, projectId));
		return new RevenueSummary(shipmentRevenue.subtract(returnRevenue).max(ZERO).setScale(2, RoundingMode.HALF_UP),
				invoiceRevenue, targetRevenue);
	}

	private BigDecimal netDeliveredQuantity(Long projectId, Long productMaterialId, LocalDate cutoffDate) {
		if (productMaterialId == null) {
			return ZERO;
		}
		BigDecimal shipped = nullToZero(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(sl.quantity), 0)
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				where so.project_id = ?
				and sl.material_id = ?
				and sh.status = 'POSTED'
				and sh.business_date <= ?
				""", BigDecimal.class, projectId, productMaterialId, cutoffDate));
		BigDecimal returned = nullToZero(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(rl.quantity), 0)
				from sal_sales_return_line rl
				join sal_sales_return r on r.id = rl.return_id
				join sal_sales_shipment_line sl on sl.id = rl.source_shipment_line_id
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				where so.project_id = ?
				and sl.material_id = ?
				and r.status = 'POSTED'
				and r.business_date <= ?
				""", BigDecimal.class, projectId, productMaterialId, cutoffDate));
		return shipped.subtract(returned).max(BigDecimal.ZERO);
	}

	private BigDecimal totalFinishedQuantity(Long projectId, Long productMaterialId, LocalDate cutoffDate) {
		if (productMaterialId == null) {
			return ZERO;
		}
		BigDecimal completed = nullToZero(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(r.quantity), 0)
				from mfg_completion_receipt r
				join mfg_work_order wo on wo.id = r.work_order_id
				where wo.project_id = ?
				and wo.product_material_id = ?
				and r.status = 'POSTED'
				and r.business_date <= ?
				""", BigDecimal.class, projectId, productMaterialId, cutoffDate));
		BigDecimal outsourced = nullToZero(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(rl.accepted_quantity), 0)
				from mfg_outsourcing_receipt_line rl
				join mfg_outsourcing_receipt r on r.id = rl.receipt_id
				join mfg_outsourcing_order o on o.id = r.outsourcing_order_id
				where o.project_id = ?
				and o.product_material_id = ?
				and r.status = 'POSTED'
				and r.business_date <= ?
				""", BigDecimal.class, projectId, productMaterialId, cutoffDate));
		return completed.add(outsourced);
	}

	private BigDecimal actualOutsourcingAmount(Long receiptLineId) {
		BigDecimal amount = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(l.tax_excluded_amount), 0)
				from fin_purchase_invoice_line l
				join fin_purchase_invoice i on i.id = l.purchase_invoice_id
				where i.settlement_kind = 'OUTSOURCING'
				and i.status = 'CONFIRMED'
				and l.source_line_id = ?
				""", BigDecimal.class, receiptLineId);
		amount = scale(amount);
		return amount.compareTo(BigDecimal.ZERO) == 0 ? null : amount;
	}

	private String normalizeExpenseCategory(String category) {
		if ("MANUFACTURING_OVERHEAD".equals(category)) {
			return "MANUFACTURING_OVERHEAD";
		}
		return "PROJECT_EXPENSE";
	}

	private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
		BigDecimal denominatorValue = nullToZero(denominator);
		if (denominatorValue.compareTo(BigDecimal.ZERO) <= 0) {
			return ZERO;
		}
		BigDecimal value = nullToZero(numerator).divide(denominatorValue, 10, RoundingMode.HALF_UP);
		if (value.compareTo(BigDecimal.ZERO) < 0) {
			return ZERO;
		}
		return value.compareTo(ONE) > 0 ? ONE : value;
	}

	private String fingerprint(List<SourceLineDraft> sources) {
		String joined = sources.stream()
			.sorted(Comparator.comparing(SourceLineDraft::sourceType)
				.thenComparing(SourceLineDraft::sourceId)
				.thenComparing((source) -> source.sourceLineId() == null ? 0L : source.sourceLineId())
				.thenComparing(SourceLineDraft::costCategory)
				.thenComparing(SourceLineDraft::entryType)
				.thenComparing(SourceLineDraft::costStage))
			.map((source) -> String.join("|", source.sourceType(), source.sourceId().toString(),
					source.sourceLineId() == null ? "" : source.sourceLineId().toString(), source.costCategory(),
					source.entryType(), source.costStage(), plain(source.calculatedAmount()), plain(source.sourceAmount()),
					source.sourceStatus() == null ? "" : source.sourceStatus()))
			.reduce("", (left, right) -> left + "\n" + right);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private ValueMovementRow mapValueMovement(ResultSet rs, int rowNum) throws SQLException {
		return new ValueMovementRow(rs.getLong("id"), rs.getString("source_type"), rs.getLong("source_id"),
				rs.getLong("source_line_id"), rs.getString("movement_no"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("unit_cost"), rs.getBigDecimal("inventory_amount"), rs.getString("valuation_state"),
				rs.getObject("business_date", LocalDate.class), rs.getLong("product_material_id"),
				rs.getBigDecimal("planned_quantity"), rs.getBigDecimal("finished_quantity"));
	}

	private CostRecordRow mapCostRecord(ResultSet rs, int rowNum) throws SQLException {
		return new CostRecordRow(rs.getLong("id"), rs.getString("record_no"), rs.getString("cost_type"),
				rs.getString("source_type"), rs.getString("source_document_type"), nullableLong(rs, "source_document_id"),
				nullableLong(rs, "source_line_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("amount"), rs.getString("basis_type"), rs.getObject("business_date", LocalDate.class),
				rs.getLong("product_material_id"), rs.getBigDecimal("planned_quantity"),
				rs.getBigDecimal("finished_quantity"));
	}

	private OutsourcingReceiptRow mapOutsourcingReceipt(ResultSet rs, int rowNum) throws SQLException {
		return new OutsourcingReceiptRow(rs.getLong("receipt_line_id"), rs.getLong("receipt_id"),
				rs.getString("receipt_no"), rs.getObject("business_date", LocalDate.class),
				rs.getLong("outsourcing_order_id"), rs.getLong("product_material_id"),
				rs.getBigDecimal("planned_quantity"), rs.getBigDecimal("accepted_quantity"),
				rs.getBigDecimal("provisional_unit_cost"));
	}

	private ExpenseLineRow mapExpenseLine(ResultSet rs, int rowNum) throws SQLException {
		return new ExpenseLineRow(rs.getLong("line_id"), rs.getLong("expense_id"), rs.getString("expense_no"),
				rs.getObject("expense_date", LocalDate.class), rs.getString("expense_category"),
				rs.getBigDecimal("tax_excluded_amount"));
	}

	private AdjustmentLineRow mapAdjustmentLine(ResultSet rs, int rowNum) throws SQLException {
		return new AdjustmentLineRow(rs.getLong("line_id"), rs.getLong("adjustment_id"),
				rs.getString("adjustment_no"), rs.getObject("business_date", LocalDate.class),
				rs.getString("cost_category"), rs.getString("cost_stage"), rs.getString("direction"),
				rs.getBigDecimal("amount"));
	}

	private static BigDecimal scale(BigDecimal value) {
		return nullToZero(value).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal nullToZero(BigDecimal value) {
		return value == null ? ZERO : value;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static String plain(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString();
	}

	public record CollectionResult(List<SourceLineDraft> sources, List<VarianceDraft> variances,
			RevenueSummary revenue, String sourceFingerprint) {
	}

	public record SourceLineDraft(Long projectId, String costCategory, String costStage, String entryType,
			String sourceType, Long sourceId, Long sourceLineId, String sourceNo, String sourceStatus,
			LocalDate businessDate, BigDecimal quantity, BigDecimal unitCost, BigDecimal sourceAmount,
			BigDecimal calculatedAmount, boolean sourceRestricted) {
	}

	public record VarianceDraft(Long projectId, String varianceType, String severity, String status,
			boolean sourceRestricted, BigDecimal varianceAmount, String description, String sourceType, Long sourceId,
			Long sourceLineId, String costCategory) {
	}

	public record RevenueSummary(BigDecimal shipmentRevenue, BigDecimal invoiceRevenue, BigDecimal targetRevenue) {
	}

	private record StageContext(Long productMaterialId, BigDecimal plannedQuantity, BigDecimal finishedQuantity,
			LocalDate cutoffDate) {
	}

	private record StageAmounts(BigDecimal wip, BigDecimal finished, BigDecimal delivered) {
	}

	private record ValueMovementRow(Long id, String sourceType, Long sourceId, Long sourceLineId, String movementNo,
			BigDecimal quantity, BigDecimal unitCost, BigDecimal inventoryAmount, String valuationState,
			LocalDate businessDate, Long productMaterialId, BigDecimal plannedQuantity, BigDecimal finishedQuantity) {
	}

	private record CostRecordRow(Long id, String recordNo, String costType, String sourceType,
			String sourceDocumentType, Long sourceDocumentId, Long sourceLineId, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal amount, String basisType, LocalDate businessDate, Long productMaterialId,
			BigDecimal plannedQuantity, BigDecimal finishedQuantity) {
	}

	private record OutsourcingReceiptRow(Long receiptLineId, Long receiptId, String receiptNo, LocalDate businessDate,
			Long outsourcingOrderId, Long productMaterialId, BigDecimal plannedQuantity, BigDecimal acceptedQuantity,
			BigDecimal provisionalUnitCost) {
	}

	private record ExpenseLineRow(Long lineId, Long expenseId, String expenseNo, LocalDate expenseDate,
			String expenseCategory, BigDecimal taxExcludedAmount) {
	}

	private record AdjustmentLineRow(Long lineId, Long adjustmentId, String adjustmentNo, LocalDate businessDate,
			String costCategory, String costStage, String direction, BigDecimal amount) {
	}

}
