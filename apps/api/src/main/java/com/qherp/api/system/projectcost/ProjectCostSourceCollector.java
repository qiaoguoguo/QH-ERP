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

	private final JdbcTemplate jdbcTemplate;

	public ProjectCostSourceCollector(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public CollectionResult collect(Long projectId, LocalDate cutoffDate) {
		boolean delivered = deliveredQuantity(projectId, cutoffDate).compareTo(BigDecimal.ZERO) > 0;
		boolean finished = delivered || finishedQuantity(projectId, cutoffDate).compareTo(BigDecimal.ZERO) > 0;
		String ordinaryStage = delivered ? "DELIVERED" : (finished ? "FINISHED" : "WIP");
		List<SourceLineDraft> sources = new ArrayList<>();
		List<VarianceDraft> variances = new ArrayList<>();
		collectMaterialValueMovements(projectId, cutoffDate, ordinaryStage, sources, variances);
		collectCostRecords(projectId, cutoffDate, ordinaryStage, sources, variances);
		collectOutsourcing(projectId, cutoffDate, ordinaryStage, sources, variances);
		collectProjectExpenses(projectId, cutoffDate, sources);
		collectConfirmedAdjustments(projectId, cutoffDate, sources);
		RevenueSummary revenue = revenue(projectId, cutoffDate);
		return new CollectionResult(sources, variances, revenue, fingerprint(sources));
	}

	private void collectMaterialValueMovements(Long projectId, LocalDate cutoffDate, String stage,
			List<SourceLineDraft> sources, List<VarianceDraft> variances) {
		collectProductionValueMovement("""
				select vm.id, vm.source_type, vm.source_id, vm.source_line_id, vm.movement_no, vm.direction,
				       vm.quantity, vm.unit_cost, vm.inventory_amount, vm.valuation_state, vm.business_date
				from inv_value_movement vm
				join mfg_material_issue_line l on l.id = vm.source_line_id
				join mfg_material_issue h on h.id = l.issue_id
				join mfg_work_order wo on wo.id = h.work_order_id
				where vm.source_type = 'PRODUCTION_MATERIAL_ISSUE'
				and h.status = 'POSTED'
				and wo.project_id = ?
				and vm.business_date <= ?
				""", projectId, cutoffDate, stage, BigDecimal.ONE, sources, variances);
		collectProductionValueMovement("""
				select vm.id, vm.source_type, vm.source_id, vm.source_line_id, vm.movement_no, vm.direction,
				       vm.quantity, vm.unit_cost, vm.inventory_amount, vm.valuation_state, vm.business_date
				from inv_value_movement vm
				join mfg_material_return_line l on l.id = vm.source_line_id
				join mfg_material_return h on h.id = l.return_id
				join mfg_work_order wo on wo.id = h.work_order_id
				where vm.source_type = 'PRODUCTION_MATERIAL_RETURN'
				and h.status = 'POSTED'
				and wo.project_id = ?
				and vm.business_date <= ?
				""", projectId, cutoffDate, stage, BigDecimal.ONE.negate(), sources, variances);
		collectProductionValueMovement("""
				select vm.id, vm.source_type, vm.source_id, vm.source_line_id, vm.movement_no, vm.direction,
				       vm.quantity, vm.unit_cost, vm.inventory_amount, vm.valuation_state, vm.business_date
				from inv_value_movement vm
				join mfg_material_supplement_line l on l.id = vm.source_line_id
				join mfg_material_supplement h on h.id = l.supplement_id
				join mfg_work_order wo on wo.id = h.work_order_id
				where vm.source_type = 'PRODUCTION_MATERIAL_SUPPLEMENT'
				and h.status = 'POSTED'
				and wo.project_id = ?
				and vm.business_date <= ?
				""", projectId, cutoffDate, stage, BigDecimal.ONE, sources, variances);
	}

	private void collectProductionValueMovement(String sql, Long projectId, LocalDate cutoffDate, String stage,
			BigDecimal sign, List<SourceLineDraft> sources, List<VarianceDraft> variances) {
		List<ValueMovementRow> rows = this.jdbcTemplate.query(sql, this::mapValueMovement, projectId, cutoffDate);
		for (ValueMovementRow row : rows) {
			if (!"VALUED".equals(row.valuationState()) || row.inventoryAmount() == null) {
				variances.add(new VarianceDraft(projectId, "SOURCE_UNVALUED", "ERROR", "OPEN", false, null,
						"库存价值来源未完成估值", row.sourceType(), row.sourceId(), row.sourceLineId(), "MATERIAL"));
				continue;
			}
			BigDecimal amount = scale(row.inventoryAmount()).multiply(sign).setScale(2, RoundingMode.HALF_UP);
			sources.add(new SourceLineDraft(projectId, "MATERIAL", stage, "SOURCE_TO_WIP", row.sourceType(),
					row.sourceId(), row.sourceLineId(), row.movementNo(), row.valuationState(), row.businessDate(),
					row.quantity(), row.unitCost(), amount, amount, false));
		}
	}

	private void collectCostRecords(Long projectId, LocalDate cutoffDate, String stage, List<SourceLineDraft> sources,
			List<VarianceDraft> variances) {
		List<CostRecordRow> rows = this.jdbcTemplate.query("""
				select cr.id, cr.record_no, cr.cost_type, cr.source_type, cr.source_document_type,
				       cr.source_document_id, cr.source_line_id, cr.quantity, cr.unit_price, cr.amount,
				       cr.basis_type, cr.business_date
				from mfg_cost_record cr
				join mfg_work_order wo on wo.id = cr.work_order_id
				where wo.project_id = ?
				and cr.status = 'ACTIVE'
				and cr.business_date <= ?
				order by cr.id
				""", this::mapCostRecord, projectId, cutoffDate);
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
				variances.add(new VarianceDraft(projectId, "SOURCE_UNPRICED", "ERROR", "OPEN", false, null,
						row.costType() + " 来源未定价", "MFG_COST_RECORD", row.id(), row.sourceLineId(), category));
				sources.add(new SourceLineDraft(projectId, category,
						"PROJECT_EXPENSE".equals(category) ? "DIRECT_PROJECT" : stage,
						"PROJECT_EXPENSE".equals(category) ? "PROJECT_DIRECT" : "SOURCE_TO_WIP", "MFG_COST_RECORD",
						row.id(), row.sourceLineId(), row.recordNo(), row.basisType(), row.businessDate(),
						row.quantity(), row.unitPrice(), null, ZERO, false));
				continue;
			}
			BigDecimal amount = scale(row.amount());
			sources.add(new SourceLineDraft(projectId, category,
					"PROJECT_EXPENSE".equals(category) ? "DIRECT_PROJECT" : stage,
					"PROJECT_EXPENSE".equals(category) ? "PROJECT_DIRECT" : "SOURCE_TO_WIP", "MFG_COST_RECORD",
					row.id(), row.sourceLineId(), row.recordNo(), row.basisType(), row.businessDate(), row.quantity(),
					row.unitPrice(), amount, amount, false));
		}
	}

	private void collectOutsourcing(Long projectId, LocalDate cutoffDate, String stage, List<SourceLineDraft> sources,
			List<VarianceDraft> variances) {
		List<OutsourcingReceiptRow> rows = this.jdbcTemplate.query("""
				select rl.id as receipt_line_id, r.id as receipt_id, r.receipt_no, r.business_date,
				       r.outsourcing_order_id, rl.accepted_quantity, rl.provisional_unit_cost
				from mfg_outsourcing_receipt_line rl
				join mfg_outsourcing_receipt r on r.id = rl.receipt_id
				join mfg_outsourcing_order o on o.id = r.outsourcing_order_id
				where r.status = 'POSTED'
				and o.project_id = ?
				and r.business_date <= ?
				order by rl.id
				""", this::mapOutsourcingReceipt, projectId, cutoffDate);
		for (OutsourcingReceiptRow row : rows) {
			BigDecimal provisional = scale(row.acceptedQuantity().multiply(nullToZero(row.provisionalUnitCost())));
			BigDecimal actual = actualOutsourcingAmount(row.receiptLineId());
			BigDecimal amount = actual == null ? provisional : actual;
			sources.add(new SourceLineDraft(projectId, "OUTSOURCING", stage, "SOURCE_TO_WIP",
					"OUTSOURCING_RECEIPT", row.receiptId(), row.receiptLineId(), row.receiptNo(),
					actual == null ? "PROVISIONAL" : "ACTUAL", row.businessDate(), row.acceptedQuantity(),
					row.provisionalUnitCost(), amount, amount, false));
			if (actual == null) {
				variances.add(new VarianceDraft(projectId, "OUTSOURCING_PROVISIONAL", "WARNING", "OPEN", false,
						amount, "外协成本仍为暂估", "OUTSOURCING_RECEIPT", row.receiptId(), row.receiptLineId(),
						"OUTSOURCING"));
			}
			else if (provisional.compareTo(actual) != 0) {
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
					"FIN_EXPENSE", row.expenseId(), row.lineId(), row.expenseNo(), "CONFIRMED",
					row.expenseDate(), null, null, amount, amount, false));
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
					"PROJECT_COST_ADJUSTMENT", row.adjustmentId(), row.lineId(), row.adjustmentNo(), "CONFIRMED",
					row.businessDate(), null, null, amount, amount, false));
		}
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
		return new RevenueSummary(shipmentRevenue, invoiceRevenue, targetRevenue);
	}

	private BigDecimal deliveredQuantity(Long projectId, LocalDate cutoffDate) {
		return nullToZero(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(sl.quantity), 0)
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				where so.project_id = ?
				and sh.status = 'POSTED'
				and sh.business_date <= ?
				""", BigDecimal.class, projectId, cutoffDate));
	}

	private BigDecimal finishedQuantity(Long projectId, LocalDate cutoffDate) {
		BigDecimal completed = nullToZero(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(r.quantity), 0)
				from mfg_completion_receipt r
				join mfg_work_order wo on wo.id = r.work_order_id
				where wo.project_id = ?
				and r.status = 'POSTED'
				and r.business_date <= ?
				""", BigDecimal.class, projectId, cutoffDate));
		BigDecimal outsourced = nullToZero(this.jdbcTemplate.queryForObject("""
				select coalesce(sum(r.quantity), 0)
				from mfg_outsourcing_receipt r
				join mfg_outsourcing_order o on o.id = r.outsourcing_order_id
				where o.project_id = ?
				and r.status = 'POSTED'
				and r.business_date <= ?
				""", BigDecimal.class, projectId, cutoffDate));
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

	private String fingerprint(List<SourceLineDraft> sources) {
		String joined = sources.stream()
			.sorted(Comparator.comparing(SourceLineDraft::sourceType)
				.thenComparing(SourceLineDraft::sourceId)
				.thenComparing((source) -> source.sourceLineId() == null ? 0L : source.sourceLineId())
				.thenComparing(SourceLineDraft::costCategory)
				.thenComparing(SourceLineDraft::entryType))
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
				rs.getLong("source_line_id"), rs.getString("movement_no"), rs.getString("direction"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("inventory_amount"),
				rs.getString("valuation_state"), rs.getObject("business_date", LocalDate.class));
	}

	private CostRecordRow mapCostRecord(ResultSet rs, int rowNum) throws SQLException {
		return new CostRecordRow(rs.getLong("id"), rs.getString("record_no"), rs.getString("cost_type"),
				rs.getString("source_type"), rs.getString("source_document_type"), nullableLong(rs, "source_document_id"),
				nullableLong(rs, "source_line_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("amount"), rs.getString("basis_type"), rs.getObject("business_date", LocalDate.class));
	}

	private OutsourcingReceiptRow mapOutsourcingReceipt(ResultSet rs, int rowNum) throws SQLException {
		return new OutsourcingReceiptRow(rs.getLong("receipt_line_id"), rs.getLong("receipt_id"),
				rs.getString("receipt_no"), rs.getObject("business_date", LocalDate.class),
				rs.getLong("outsourcing_order_id"), rs.getBigDecimal("accepted_quantity"),
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

	private record ValueMovementRow(Long id, String sourceType, Long sourceId, Long sourceLineId, String movementNo,
			String direction, BigDecimal quantity, BigDecimal unitCost, BigDecimal inventoryAmount,
			String valuationState, LocalDate businessDate) {
	}

	private record CostRecordRow(Long id, String recordNo, String costType, String sourceType,
			String sourceDocumentType, Long sourceDocumentId, Long sourceLineId, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal amount, String basisType, LocalDate businessDate) {
	}

	private record OutsourcingReceiptRow(Long receiptLineId, Long receiptId, String receiptNo, LocalDate businessDate,
			Long outsourcingOrderId, BigDecimal acceptedQuantity, BigDecimal provisionalUnitCost) {
	}

	private record ExpenseLineRow(Long lineId, Long expenseId, String expenseNo, LocalDate expenseDate,
			String expenseCategory, BigDecimal taxExcludedAmount) {
	}

	private record AdjustmentLineRow(Long lineId, Long adjustmentId, String adjustmentNo, LocalDate businessDate,
			String costCategory, String costStage, String direction, BigDecimal amount) {
	}

}
