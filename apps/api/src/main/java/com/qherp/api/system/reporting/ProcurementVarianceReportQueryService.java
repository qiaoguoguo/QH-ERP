package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ProcurementVarianceItemResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ProcurementVarianceSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
class ProcurementVarianceReportQueryService extends ReportingStage033QuerySupport {

	ProcurementVarianceReportQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		super(jdbcTemplate, objectMapper);
	}

	Object procurementVariances(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, true);
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			return snapshot(query);
		}
		return live(query);
	}

	Object captureSnapshot(String periodCode, LocalDate dateFrom, LocalDate dateTo) {
		return live(captureQuery(periodCode, dateFrom, dateTo));
	}

	ProcurementVarianceSummaryResponse summaryForOverview(OperatingFinanceQuery query) {
		ProcurementVarianceSummary summary = procurementRows(query).stream().reduce(ProcurementVarianceSummary.empty(),
				ProcurementVarianceSummary::plus, ProcurementVarianceSummary::plus);
		return summaryResponse(summary, query, procurementVarianceAmountVisible(), procurementVarianceSourceVisible());
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> procurementVarianceTraces(
			MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseTraceQuery(parameters);
		TraceKeyParts parts = traceKeyParts(query.traceKey(), "procurement-variance");
		if (!procurementVarianceSourceVisible()) {
			return emptyTracePage(query);
		}
		if ("ORDER".equals(parts.type())) {
			return tracePage(orderTraces(parts.sourceId(), query), query);
		}
		if ("OUTSOURCING".equals(parts.type())) {
			return tracePage(outsourcingTraces(parts.sourceId(), query), query);
		}
		throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
	}

	private ReportingAdminService.ReportPageResponse<Object> live(OperatingFinanceQuery query) {
		List<ProcurementVarianceRow> rows = procurementRows(query);
		ProcurementVarianceSummary summary = rows.stream().reduce(ProcurementVarianceSummary.empty(),
				ProcurementVarianceSummary::plus, ProcurementVarianceSummary::plus);
		boolean amountVisible = procurementVarianceAmountVisible();
		boolean sourceVisible = procurementVarianceSourceVisible();
		ProcurementVarianceSummaryResponse response = summaryResponse(summary, query, amountVisible, sourceVisible);
		List<ProcurementVarianceItemResponse> items = rows.stream()
			.map((row) -> item(row, amountVisible, sourceVisible))
			.toList();
		return pageOf(response, items, query);
	}

	private ReportingAdminService.ReportPageResponse<Object> snapshot(OperatingFinanceQuery query) {
		boolean amountVisible = procurementVarianceAmountVisible();
		boolean sourceVisible = procurementVarianceSourceVisible();
		Set<String> amountFields = Set.of("orderAmount", "receiptAmount", "invoiceAmount", "paidAmount",
				"unreceivedOrderAmount", "receivedUninvoicedAmount", "invoiceReceiptDifferenceAmount",
				"unpaidAmount", "matchVarianceAmount", "outsourcingUnsettledAmount");
		return snapshotPage("PROCUREMENT_VARIANCE", query, (item) -> matchesCommonSnapshotFilters(item, query),
				(items) -> snapshotSummaryMap(items, query, amountVisible, sourceVisible,
						Map.of("orderAmount", "orderAmount", "receiptAmount", "receiptAmount", "invoiceAmount",
								"invoiceAmount", "paidAmount", "paidAmount", "matchVarianceAmount",
								"matchVarianceAmount"),
						Set.of()),
				(item) -> snapshotItemMap(item, amountVisible, sourceVisible, amountFields));
	}

	private List<ProcurementVarianceRow> procurementRows(OperatingFinanceQuery query) {
		List<ProcurementVarianceRow> rows = new ArrayList<>();
		rows.addAll(purchaseOrderRows(query));
		rows.addAll(outsourcingRows(query));
		rows.sort(Comparator.comparing(ProcurementVarianceRow::basis).thenComparing(ProcurementVarianceRow::sourceNo));
		return rows;
	}

	private ProcurementVarianceSummaryResponse summaryResponse(ProcurementVarianceSummary summary,
			OperatingFinanceQuery query, boolean amountVisible, boolean sourceVisible) {
		return new ProcurementVarianceSummaryResponse(visibleAmount(summary.orderAmount(), amountVisible),
				visibleAmount(summary.receiptAmount(), amountVisible),
				visibleAmount(summary.invoiceAmount(), amountVisible), visibleAmount(summary.paidAmount(), amountVisible),
				visibleAmount(summary.matchVarianceAmount(), amountVisible), sourceVisible ? summary.sourceCount() : 0,
				query.analysisMode(), freshnessStatus(query));
	}

	private ProcurementVarianceItemResponse item(ProcurementVarianceRow row, boolean amountVisible,
			boolean sourceVisible) {
		return new ProcurementVarianceItemResponse(row.projectId(), sourceVisible ? row.sourceNo() : null,
				row.supplierName(), row.projectNo(), row.basis(), visibleAmount(row.orderAmount(), amountVisible),
				visibleAmount(row.receiptAmount(), amountVisible), visibleAmount(row.invoiceAmount(), amountVisible),
				visibleAmount(row.paidAmount(), amountVisible),
				visibleAmount(row.unreceivedOrderAmount(), amountVisible),
				visibleAmount(row.receivedUninvoicedAmount(), amountVisible),
				visibleAmount(row.invoiceReceiptDifferenceAmount(), amountVisible),
				visibleAmount(row.unpaidAmount(), amountVisible),
				visibleAmount(row.matchVarianceAmount(), amountVisible),
				visibleAmount(row.outsourcingUnsettledAmount(), amountVisible),
				amountVisible ? row.reconciliationStatus() : RESTRICTED, sourceVisible ? row.sourceCount() : 0,
				sourceVisible ? row.traceKey() : null, sourceVisible ? row.purchaseInvoiceNos() : null,
				sourceVisible ? row.paymentNos() : null);
	}

	private List<ProcurementVarianceRow> purchaseOrderRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				with order_totals as (
					select po.id as order_id,
					       coalesce(sum(pol.tax_included_amount), 0) as order_amount
					from proc_purchase_order po
					join proc_purchase_order_line pol on pol.order_id = po.id
					where po.status <> 'CANCELLED'
					group by po.id
				),
				receipt_totals as (
					select pr.order_id,
					       coalesce(sum(prl.quantity * pol.tax_included_unit_price), 0) as receipt_amount,
					       count(distinct pr.id) as receipt_count
					from proc_purchase_receipt pr
					join proc_purchase_receipt_line prl on prl.receipt_id = pr.id
					join proc_purchase_order_line pol on pol.id = prl.order_line_id
					where pr.status = 'POSTED'
					and pr.business_date <= ?
					group by pr.order_id
				),
				return_totals as (
					select pr.source_receipt_id, receipt.order_id,
					       coalesce(sum(prl.amount), 0) as return_amount,
					       count(distinct pr.id) as return_count
					from proc_purchase_return pr
					join proc_purchase_return_line prl on prl.return_id = pr.id
					join proc_purchase_receipt receipt on receipt.id = pr.source_receipt_id
					where pr.status = 'POSTED'
					and pr.business_date <= ?
					group by pr.source_receipt_id, receipt.order_id
				),
				invoice_totals as (
					select receipt.order_id,
					       coalesce(sum(pi.tax_included_amount), 0) as invoice_amount,
					       count(distinct pi.id) as invoice_count,
					       string_agg(distinct pi.invoice_no, ',') as invoice_nos
					from fin_purchase_invoice pi
					join proc_purchase_receipt receipt on receipt.id = pi.source_id and pi.source_type = 'PURCHASE_RECEIPT'
					where pi.status = 'CONFIRMED'
					and pi.invoice_date <= ?
					group by receipt.order_id
				),
				payable_totals as (
					select receipt.order_id,
					       coalesce(sum(py.unpaid_amount), 0) as unpaid_amount,
					       coalesce(sum(py.paid_amount), 0) as payable_paid_amount,
					       count(distinct py.id) as payable_count
					from fin_payable py
					join proc_purchase_receipt receipt on receipt.id = py.source_id and py.source_type = 'PURCHASE_RECEIPT'
					where py.status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
					and py.business_date <= ?
					group by receipt.order_id
				),
				payment_totals as (
					select receipt.order_id,
					       coalesce(sum(pa.allocated_amount), 0) as paid_amount,
					       count(distinct pm.id) as payment_count,
					       string_agg(distinct pm.payment_no, ',') as payment_nos
					from fin_payment_allocation pa
					join fin_payment pm on pm.id = pa.payment_id
					join fin_payable py on py.id = pa.payable_id
					join proc_purchase_receipt receipt on receipt.id = py.source_id and py.source_type = 'PURCHASE_RECEIPT'
					where pm.status = 'POSTED'
					and pm.payment_date <= ?
					group by receipt.order_id
				)
				select po.id as source_id, po.order_no as source_no, supplier.name as supplier_name,
				       po.project_id, project.project_no, po.purchase_mode as basis,
				       coalesce(ot.order_amount, 0) as order_amount,
				       greatest(coalesce(rt.receipt_amount, 0) - coalesce(rr.return_amount, 0), 0) as receipt_amount,
				       coalesce(it.invoice_amount, 0) as invoice_amount,
				       coalesce(pt.paid_amount, 0) as paid_amount,
				       coalesce(pay.unpaid_amount, 0) as unpaid_amount,
				       it.invoice_nos,
				       pt.payment_nos,
				       (1 + coalesce(rt.receipt_count, 0) + coalesce(rr.return_count, 0)
				          + coalesce(it.invoice_count, 0) + coalesce(pay.payable_count, 0)
				          + coalesce(pt.payment_count, 0)) as source_count
				from proc_purchase_order po
				join mst_supplier supplier on supplier.id = po.supplier_id
				left join sal_project project on project.id = po.project_id
				left join order_totals ot on ot.order_id = po.id
				left join receipt_totals rt on rt.order_id = po.id
				left join return_totals rr on rr.order_id = po.id
				left join invoice_totals it on it.order_id = po.id
				left join payable_totals pay on pay.order_id = po.id
				left join payment_totals pt on pt.order_id = po.id
				where po.status <> 'CANCELLED'
				and po.order_date <= ?
				""");
		List<Object> args = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			args.add(query.dateTo());
		}
		args.add(query.dateTo());
		appendPurchaseFilters(sql, args, query, "po", "supplier", "project", "po.order_no");
		sql.append(" order by po.order_no, po.id");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> purchaseOrderRow(rs), args.toArray());
	}

	private void appendPurchaseFilters(StringBuilder sql, List<Object> args, OperatingFinanceQuery query,
			String sourceAlias, String supplierAlias, String projectAlias, String sourceNoExpression) {
		if (query.projectId() != null) {
			sql.append(" and ").append(sourceAlias).append(".project_id = ?");
			args.add(query.projectId());
		}
		if (hasText(query.basis())) {
			sql.append(" and upper(").append(sourceAlias).append(".purchase_mode) = ?");
			args.add(query.basis().trim().toUpperCase(Locale.ROOT));
		}
		if (hasText(query.keyword())) {
			sql.append(" and (lower(").append(sourceNoExpression).append(") like ? or lower(")
				.append(supplierAlias).append(".name) like ?");
			if (projectAlias != null) {
				sql.append(" or lower(").append(projectAlias).append(".project_no) like ? or lower(")
					.append(projectAlias).append(".name) like ?");
			}
			sql.append(")");
			String like = like(query.keyword());
			args.add(like);
			args.add(like);
			if (projectAlias != null) {
				args.add(like);
				args.add(like);
			}
		}
	}

	private ProcurementVarianceRow purchaseOrderRow(ResultSet rs) throws SQLException {
		BigDecimal orderAmount = zero(rs.getBigDecimal("order_amount"));
		BigDecimal receiptAmount = zero(rs.getBigDecimal("receipt_amount"));
		BigDecimal invoiceAmount = zero(rs.getBigDecimal("invoice_amount"));
		BigDecimal paidAmount = zero(rs.getBigDecimal("paid_amount"));
		BigDecimal unreceivedOrder = orderAmount.subtract(receiptAmount).max(BigDecimal.ZERO);
		BigDecimal receivedUninvoiced = receiptAmount.subtract(invoiceAmount).max(BigDecimal.ZERO);
		BigDecimal invoiceReceiptDifference = invoiceAmount.subtract(receiptAmount);
		BigDecimal matchVariance = invoiceAmount.subtract(orderAmount).abs();
		String status = matchVariance.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "DIFFERENT";
		long sourceId = rs.getLong("source_id");
		Long projectId = rs.getObject("project_id", Long.class);
		return new ProcurementVarianceRow(sourceId, projectId, rs.getString("source_no"),
				rs.getString("supplier_name"), rs.getString("project_no"), rs.getString("basis"), orderAmount,
				receiptAmount, invoiceAmount, paidAmount, unreceivedOrder, receivedUninvoiced, invoiceReceiptDifference,
				zero(rs.getBigDecimal("unpaid_amount")), matchVariance, BigDecimal.ZERO, status,
				rs.getInt("source_count"), "procurement-variance:ORDER:" + sourceId, rs.getString("invoice_nos"),
				rs.getString("payment_nos"));
	}

	private List<ProcurementVarianceRow> outsourcingRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				with receipt_totals as (
					select orc.outsourcing_order_id,
					       coalesce(sum(orc.quantity * coalesce(orc.unit_cost, orc.provisional_unit_cost, oo.provisional_unit_cost, 0)), 0) as receipt_amount,
					       count(distinct orc.id) as receipt_count
					from mfg_outsourcing_receipt orc
					join mfg_outsourcing_order oo on oo.id = orc.outsourcing_order_id
					where orc.status = 'POSTED'
					and orc.business_date <= ?
					group by orc.outsourcing_order_id
				),
				invoice_totals as (
					select orc.outsourcing_order_id,
					       coalesce(sum(pi.tax_included_amount), 0) as invoice_amount,
					       count(distinct pi.id) as invoice_count,
					       string_agg(distinct pi.invoice_no, ',') as invoice_nos
					from fin_purchase_invoice pi
					join mfg_outsourcing_receipt orc on orc.id = pi.source_id and pi.source_type = 'OUTSOURCING_RECEIPT'
					where pi.status = 'CONFIRMED'
					and pi.invoice_date <= ?
					group by orc.outsourcing_order_id
				)
				select oo.id as source_id, oo.outsourcing_order_no as source_no, supplier.name as supplier_name,
				       oo.project_id, project.project_no, 'OUTSOURCING' as basis,
				       coalesce(oo.planned_quantity * oo.provisional_unit_cost, 0) as order_amount,
				       coalesce(rt.receipt_amount, 0) as receipt_amount,
				       coalesce(it.invoice_amount, 0) as invoice_amount,
				       it.invoice_nos,
				       (1 + coalesce(rt.receipt_count, 0) + coalesce(it.invoice_count, 0)) as source_count
				from mfg_outsourcing_order oo
				left join mst_supplier supplier on supplier.id = oo.supplier_id
				left join sal_project project on project.id = oo.project_id
				left join receipt_totals rt on rt.outsourcing_order_id = oo.id
				left join invoice_totals it on it.outsourcing_order_id = oo.id
				where oo.status <> 'CANCELLED'
				and oo.planned_receipt_date <= ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(query.dateTo());
		args.add(query.dateTo());
		args.add(query.dateTo());
		if (query.projectId() != null) {
			sql.append(" and oo.project_id = ?");
			args.add(query.projectId());
		}
		if (hasText(query.basis()) && !"OUTSOURCING".equalsIgnoreCase(query.basis())) {
			sql.append(" and 1 = 0");
		}
		if (hasText(query.keyword())) {
			sql.append("""
					 and (
						lower(oo.outsourcing_order_no) like ?
						or lower(coalesce(supplier.name, '')) like ?
						or lower(coalesce(project.project_no, '')) like ?
						or lower(coalesce(project.name, '')) like ?
					)
					""");
			String like = like(query.keyword());
			for (int index = 0; index < 4; index++) {
				args.add(like);
			}
		}
		sql.append(" order by oo.outsourcing_order_no, oo.id");
		return this.jdbcTemplate.query(sql.toString(), this::outsourcingRow, args.toArray());
	}

	private ProcurementVarianceRow outsourcingRow(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal orderAmount = zero(rs.getBigDecimal("order_amount"));
		BigDecimal receiptAmount = zero(rs.getBigDecimal("receipt_amount"));
		BigDecimal invoiceAmount = zero(rs.getBigDecimal("invoice_amount"));
		BigDecimal matchVariance = invoiceAmount.subtract(orderAmount).abs();
		BigDecimal unsettled = receiptAmount.subtract(invoiceAmount).max(BigDecimal.ZERO);
		String status = matchVariance.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "DIFFERENT";
		long sourceId = rs.getLong("source_id");
		Long projectId = rs.getObject("project_id", Long.class);
		return new ProcurementVarianceRow(sourceId, projectId, rs.getString("source_no"),
				rs.getString("supplier_name"), rs.getString("project_no"), "OUTSOURCING", orderAmount, receiptAmount,
				invoiceAmount, BigDecimal.ZERO, BigDecimal.ZERO,
				receiptAmount.subtract(invoiceAmount).max(BigDecimal.ZERO),
				invoiceAmount.subtract(receiptAmount), invoiceAmount, matchVariance, unsettled, status,
				rs.getInt("source_count"), "procurement-variance:OUTSOURCING:" + sourceId,
				rs.getString("invoice_nos"), null);
	}

	private List<ReportingAdminService.TraceSourceResponse> orderTraces(long orderId, OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select po.id, po.order_no, po.order_date, po.status, coalesce(sum(pol.tax_included_amount), 0) as amount
				from proc_purchase_order po
				left join proc_purchase_order_line pol on pol.order_id = po.id
				where po.id = ?
				group by po.id
				""", (rs, rowNum) -> trace("PROCUREMENT_ORDER", rs.getLong("id"), rs.getString("order_no"), null,
				rs.getObject("order_date", LocalDate.class), rs.getString("status"), null, rs.getBigDecimal("amount"),
				"procurement:order:view", "procurement-order-detail", routeParams("id", rs.getLong("id"))), orderId));
		traces.addAll(this.jdbcTemplate.query("""
				select pr.id, pr.receipt_no, pr.business_date, pr.status,
				       coalesce(sum(prl.quantity * pol.tax_included_unit_price), 0) as amount
				from proc_purchase_receipt pr
				join proc_purchase_receipt_line prl on prl.receipt_id = pr.id
				join proc_purchase_order_line pol on pol.id = prl.order_line_id
				where pr.order_id = ?
				and pr.status = 'POSTED'
				and pr.business_date <= ?
				group by pr.id
				order by pr.business_date, pr.id
				""", (rs, rowNum) -> trace("PURCHASE_RECEIPT", rs.getLong("id"), rs.getString("receipt_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("amount"), "procurement:receipt:view", "procurement-receipt-detail",
				routeParams("id", rs.getLong("id"))), orderId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select pr.id, pr.return_no, pr.business_date, pr.status, pr.total_amount
				from proc_purchase_return pr
				join proc_purchase_receipt receipt on receipt.id = pr.source_receipt_id
				where receipt.order_id = ?
				and pr.status = 'POSTED'
				and pr.business_date <= ?
				order by pr.business_date, pr.id
				""", (rs, rowNum) -> trace("PURCHASE_RETURN", rs.getLong("id"), rs.getString("return_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("total_amount"), "procurement:return:view", "procurement-return-detail",
				routeParams("id", rs.getLong("id"))), orderId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select pi.id, pi.invoice_no, pi.invoice_date, pi.status, pi.tax_included_amount
				from fin_purchase_invoice pi
				join proc_purchase_receipt receipt on receipt.id = pi.source_id and pi.source_type = 'PURCHASE_RECEIPT'
				where receipt.order_id = ?
				and pi.status = 'CONFIRMED'
				and pi.invoice_date <= ?
				order by pi.invoice_date, pi.id
				""", (rs, rowNum) -> trace("PURCHASE_INVOICE", rs.getLong("id"), rs.getString("invoice_no"), null,
				rs.getObject("invoice_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("tax_included_amount"), "finance:purchase-invoice:view",
				"finance-purchase-invoice-detail", routeParams("id", rs.getLong("id"))), orderId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select py.id, py.payable_no, py.business_date, py.status, py.unpaid_amount
				from fin_payable py
				join proc_purchase_receipt receipt on receipt.id = py.source_id and py.source_type = 'PURCHASE_RECEIPT'
				where receipt.order_id = ?
				and py.status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
				and py.business_date <= ?
				order by py.business_date, py.id
				""", (rs, rowNum) -> trace("PAYABLE", rs.getLong("id"), rs.getString("payable_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("unpaid_amount"), "finance:payable:view", "finance-payable-detail",
				routeParams("id", rs.getLong("id"))), orderId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select pm.id, pm.payment_no, pm.payment_date, pm.status, pa.allocated_amount
				from fin_payment_allocation pa
				join fin_payment pm on pm.id = pa.payment_id
				join fin_payable py on py.id = pa.payable_id
				join proc_purchase_receipt receipt on receipt.id = py.source_id and py.source_type = 'PURCHASE_RECEIPT'
				where receipt.order_id = ?
				and pm.status = 'POSTED'
				and pm.payment_date <= ?
				order by pm.payment_date, pm.id
				""", (rs, rowNum) -> trace("PAYMENT", rs.getLong("id"), rs.getString("payment_no"), null,
				rs.getObject("payment_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("allocated_amount"), "finance:payment:view", "finance-payment-detail",
				routeParams("id", rs.getLong("id"))), orderId, query.dateTo()));
		if (traces.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

	private List<ReportingAdminService.TraceSourceResponse> outsourcingTraces(long orderId, OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select id, outsourcing_order_no, planned_receipt_date, status,
				       coalesce(planned_quantity * provisional_unit_cost, 0) as amount
				from mfg_outsourcing_order
				where id = ?
				""", (rs, rowNum) -> trace("OUTSOURCING_ORDER", rs.getLong("id"), rs.getString("outsourcing_order_no"),
				null, rs.getObject("planned_receipt_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("amount"), "production:outsourcing:view", "production-outsourcing-order-detail",
				routeParams("id", rs.getLong("id"))), orderId));
		traces.addAll(this.jdbcTemplate.query("""
				select id, receipt_no, business_date, status,
				       coalesce(quantity * coalesce(unit_cost, provisional_unit_cost, 0), 0) as amount
				from mfg_outsourcing_receipt
				where outsourcing_order_id = ?
				and status = 'POSTED'
				and business_date <= ?
				order by business_date, id
				""", (rs, rowNum) -> trace("OUTSOURCING_RECEIPT", rs.getLong("id"), rs.getString("receipt_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("amount"), "production:outsourcing-receipt:view",
				"production-outsourcing-receipt-detail", routeParams("id", rs.getLong("id"))), orderId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select pi.id, pi.invoice_no, pi.invoice_date, pi.status, pi.tax_included_amount
				from fin_purchase_invoice pi
				join mfg_outsourcing_receipt receipt on receipt.id = pi.source_id and pi.source_type = 'OUTSOURCING_RECEIPT'
				where receipt.outsourcing_order_id = ?
				and pi.status = 'CONFIRMED'
				and pi.invoice_date <= ?
				order by pi.invoice_date, pi.id
				""", (rs, rowNum) -> trace("PURCHASE_INVOICE", rs.getLong("id"), rs.getString("invoice_no"), null,
				rs.getObject("invoice_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("tax_included_amount"), "finance:purchase-invoice:view",
				"finance-purchase-invoice-detail", routeParams("id", rs.getLong("id"))), orderId, query.dateTo()));
		if (traces.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

	private String like(String keyword) {
		return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
	}

	private record ProcurementVarianceRow(long sourceId, Long projectId, String sourceNo, String supplierName,
			String projectNo, String basis, BigDecimal orderAmount, BigDecimal receiptAmount, BigDecimal invoiceAmount,
			BigDecimal paidAmount, BigDecimal unreceivedOrderAmount, BigDecimal receivedUninvoicedAmount,
			BigDecimal invoiceReceiptDifferenceAmount, BigDecimal unpaidAmount, BigDecimal matchVarianceAmount,
			BigDecimal outsourcingUnsettledAmount, String reconciliationStatus, int sourceCount, String traceKey,
			String purchaseInvoiceNos, String paymentNos) {
	}

	private record ProcurementVarianceSummary(BigDecimal orderAmount, BigDecimal receiptAmount,
			BigDecimal invoiceAmount, BigDecimal paidAmount, BigDecimal matchVarianceAmount, int sourceCount) {

		static ProcurementVarianceSummary empty() {
			return new ProcurementVarianceSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, 0);
		}

		ProcurementVarianceSummary plus(ProcurementVarianceRow row) {
			return new ProcurementVarianceSummary(this.orderAmount.add(row.orderAmount()),
					this.receiptAmount.add(row.receiptAmount()), this.invoiceAmount.add(row.invoiceAmount()),
					this.paidAmount.add(row.paidAmount()), this.matchVarianceAmount.add(row.matchVarianceAmount()),
					this.sourceCount + row.sourceCount());
		}

		ProcurementVarianceSummary plus(ProcurementVarianceSummary other) {
			return new ProcurementVarianceSummary(this.orderAmount.add(other.orderAmount),
					this.receiptAmount.add(other.receiptAmount), this.invoiceAmount.add(other.invoiceAmount),
					this.paidAmount.add(other.paidAmount), this.matchVarianceAmount.add(other.matchVarianceAmount),
					this.sourceCount + other.sourceCount);
		}

	}

}
