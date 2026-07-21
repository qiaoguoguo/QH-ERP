package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ReceivablePayableItemResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ReceivablePayableSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
class ReceivablePayableReportQueryService extends ReportingStage033QuerySupport {

	ReceivablePayableReportQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		super(jdbcTemplate, objectMapper);
	}

	Object receivablePayable(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, true);
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			return snapshot(query);
		}
		return live(query);
	}

	Object captureSnapshot(String periodCode, LocalDate dateFrom, LocalDate dateTo) {
		return live(captureQuery(periodCode, dateFrom, dateTo));
	}

	ReceivablePayableSummaryResponse summaryForOverview(OperatingFinanceQuery query) {
		List<ReceivablePayableRow> rows = receivablePayableRows(query);
		return summaryResponse(summary(query, rows), query, receivablePayableAmountVisible(),
				receivablePayableSourceVisible());
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> receivablePayableTraces(
			MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseTraceQuery(parameters);
		TraceKeyParts parts = traceKeyParts(query.traceKey(), "receivable-payable");
		if (!receivablePayableSourceVisible()) {
			return emptyTracePage(query);
		}
		if ("RECEIVABLE".equals(parts.type())) {
			return tracePage(receivableTraces(parts.sourceId(), query), query);
		}
		if ("PAYABLE".equals(parts.type())) {
			return tracePage(payableTraces(parts.sourceId(), query), query);
		}
		throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
	}

	private ReportingAdminService.ReportPageResponse<Object> live(OperatingFinanceQuery query) {
		List<ReceivablePayableRow> rows = receivablePayableRows(query);
		ReceivablePayableSummary summary = summary(query, rows);
		boolean amountVisible = receivablePayableAmountVisible();
		boolean sourceVisible = receivablePayableSourceVisible();
		ReceivablePayableSummaryResponse response = summaryResponse(summary, query, amountVisible, sourceVisible);
		List<ReceivablePayableItemResponse> items = rows.stream()
			.map((row) -> item(row, amountVisible, sourceVisible))
			.toList();
		return pageOf(response, items, query);
	}

	private ReportingAdminService.ReportPageResponse<Object> snapshot(OperatingFinanceQuery query) {
		boolean amountVisible = receivablePayableAmountVisible();
		boolean sourceVisible = receivablePayableSourceVisible();
		Set<String> amountFields = Set.of("receivableAmount", "payableAmount", "advanceReceiptAmount",
				"prepaymentAmount", "settledAmount", "balanceAmount", "notDueAmount", "aging1To30Amount",
				"aging31To60Amount", "aging61To90Amount", "agingOver90Amount", "overdueAmount");
		return snapshotPage("RECEIVABLE_PAYABLE", query, (item) -> matchesCommonSnapshotFilters(item, query),
				(items) -> snapshotSummaryMap(items, query, amountVisible, sourceVisible,
						Map.of("receivableAmount", "receivableAmount", "payableAmount", "payableAmount",
								"advanceReceiptAmount", "advanceReceiptAmount", "prepaymentAmount",
								"prepaymentAmount", "balanceAmount", "balanceAmount", "overdueAmount",
								"overdueAmount"),
						Set.of()),
				(item) -> snapshotItemMap(item, amountVisible, sourceVisible, amountFields));
	}

	private List<ReceivablePayableRow> receivablePayableRows(OperatingFinanceQuery query) {
		List<ReceivablePayableRow> rows = new ArrayList<>();
		rows.addAll(receivableRows(query));
		rows.addAll(payableRows(query));
		rows.sort(Comparator.comparing(ReceivablePayableRow::partyType)
			.thenComparing(ReceivablePayableRow::partyName)
			.thenComparing(ReceivablePayableRow::sourceNo));
		return rows;
	}

	private ReceivablePayableSummaryResponse summaryResponse(ReceivablePayableSummary summary,
			OperatingFinanceQuery query, boolean amountVisible, boolean sourceVisible) {
		return new ReceivablePayableSummaryResponse(visibleAmount(summary.receivableAmount(), amountVisible),
				visibleAmount(summary.payableAmount(), amountVisible),
				visibleAmount(summary.advanceReceiptAmount(), amountVisible),
				visibleAmount(summary.prepaymentAmount(), amountVisible),
				visibleAmount(summary.balanceAmount(), amountVisible),
				visibleAmount(summary.overdueAmount(), amountVisible), sourceVisible ? summary.sourceCount() : 0,
				query.analysisMode(), freshnessStatus(query));
	}

	private ReceivablePayableItemResponse item(ReceivablePayableRow row, boolean amountVisible,
			boolean sourceVisible) {
		return new ReceivablePayableItemResponse(row.projectId(), row.partyType(), row.partyName(), row.projectNo(),
				row.sourceType(), sourceVisible ? row.sourceNo() : null,
				visibleAmount(row.receivableAmount(), amountVisible),
				visibleAmount(row.payableAmount(), amountVisible),
				visibleAmount(row.advanceReceiptAmount(), amountVisible),
				visibleAmount(row.prepaymentAmount(), amountVisible),
				visibleAmount(row.settledAmount(), amountVisible), visibleAmount(row.balanceAmount(), amountVisible),
				visibleAmount(row.notDueAmount(), amountVisible),
				visibleAmount(row.aging1To30Amount(), amountVisible),
				visibleAmount(row.aging31To60Amount(), amountVisible),
				visibleAmount(row.aging61To90Amount(), amountVisible),
				visibleAmount(row.agingOver90Amount(), amountVisible),
				visibleAmount(row.overdueAmount(), amountVisible), row.agingBucket(),
				sourceVisible ? row.sourceCount() : 0, sourceVisible ? row.traceKey() : null);
	}

	private List<ReceivablePayableRow> receivableRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				with receipt_counts as (
					select ra.receivable_id,
					       coalesce(sum(ra.allocated_amount), 0) as allocated_amount,
					       count(distinct rc.id) as receipt_count
					from fin_receipt_allocation ra
					join fin_receipt rc on rc.id = ra.receipt_id
					where rc.status = 'POSTED'
					and rc.receipt_date <= ?
					group by ra.receivable_id
				),
				allocation_counts as (
					select sal.target_id as receivable_id,
					       count(distinct sa.id) as allocation_count
					from fin_settlement_allocation_line sal
					join fin_settlement_allocation sa on sa.id = sal.allocation_id
					where sal.target_type = 'RECEIVABLE'
					and sa.status = 'POSTED'
					and sa.business_date <= ?
					group by sal.target_id
				),
				advance_amounts as (
					select rb.customer_id, rb.project_id,
					       coalesce(sum(rb.available_amount), 0) as advance_receipt_amount,
					       count(distinct rb.receipt_id) as advance_count
					from fin_receipt_balance rb
					join fin_receipt rc on rc.id = rb.receipt_id
					where rb.status = 'POSTED'
					and rc.status = 'POSTED'
					and rc.receipt_date <= ?
					group by rb.customer_id, rb.project_id
				)
				select r.id as source_id, r.receivable_no as source_no, customer.name as party_name,
				       so.project_id, project.project_no, r.business_date, r.due_date, r.status,
				       r.received_amount as settled_amount, r.unreceived_amount as open_amount,
				       coalesce(ad.advance_receipt_amount, 0) as advance_receipt_amount,
				       (1 + coalesce(rc.receipt_count, 0) + coalesce(ac.allocation_count, 0)
				          + coalesce(ad.advance_count, 0)) as source_count
				from fin_receivable r
				join mst_customer customer on customer.id = r.customer_id
				join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
				join sal_sales_order so on so.id = sh.order_id
				left join sal_project project on project.id = so.project_id
				left join receipt_counts rc on rc.receivable_id = r.id
				left join allocation_counts ac on ac.receivable_id = r.id
				left join advance_amounts ad on ad.customer_id = r.customer_id
					and coalesce(ad.project_id, 0) = coalesce(so.project_id, 0)
				where r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
				and r.business_date <= ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(query.dateTo());
		args.add(query.dateTo());
		args.add(query.dateTo());
		args.add(query.dateTo());
		appendPartyFilters(sql, args, query, "so", "customer", "project", "r.receivable_no");
		sql.append(" order by customer.name, r.due_date, r.receivable_no, r.id");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> receivableRow(rs, query.dateTo()), args.toArray());
	}

	private ReceivablePayableRow receivableRow(ResultSet rs, LocalDate dateTo) throws SQLException {
		BigDecimal openAmount = zero(rs.getBigDecimal("open_amount"));
		BigDecimal settledAmount = zero(rs.getBigDecimal("settled_amount"));
		BucketAmounts buckets = bucket(openAmount, rs.getObject("due_date", LocalDate.class), dateTo);
		long sourceId = rs.getLong("source_id");
		return new ReceivablePayableRow(rs.getObject("project_id", Long.class), "CUSTOMER",
				rs.getString("party_name"), rs.getString("project_no"), "RECEIVABLE", rs.getString("source_no"),
				openAmount, BigDecimal.ZERO,
				zero(rs.getBigDecimal("advance_receipt_amount")), BigDecimal.ZERO, settledAmount,
				openAmount.subtract(zero(rs.getBigDecimal("advance_receipt_amount"))), buckets.notDue(),
				buckets.days1To30(), buckets.days31To60(), buckets.days61To90(), buckets.daysOver90(),
				buckets.overdue(), buckets.bucketName(), rs.getInt("source_count"),
				"receivable-payable:RECEIVABLE:" + sourceId);
	}

	private List<ReceivablePayableRow> payableRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				with payment_counts as (
					select pa.payable_id,
					       coalesce(sum(pa.allocated_amount), 0) as allocated_amount,
					       count(distinct pm.id) as payment_count
					from fin_payment_allocation pa
					join fin_payment pm on pm.id = pa.payment_id
					where pm.status = 'POSTED'
					and pm.payment_date <= ?
					group by pa.payable_id
				),
				prepayment_amounts as (
					select pb.supplier_id, pb.project_id,
					       coalesce(sum(pb.available_amount), 0) as prepayment_amount,
					       count(distinct pb.payment_id) as prepayment_count
					from fin_payment_balance pb
					join fin_payment pm on pm.id = pb.payment_id
					where pb.status = 'POSTED'
					and pm.status = 'POSTED'
					and pm.payment_date <= ?
					group by pb.supplier_id, pb.project_id
				)
				select py.id as source_id, py.payable_no as source_no, supplier.name as party_name,
				       po.project_id, project.project_no, py.business_date, py.due_date, py.status,
				       py.paid_amount as settled_amount, py.unpaid_amount as open_amount,
				       coalesce(pp.prepayment_amount, 0) as prepayment_amount,
				       (1 + coalesce(pc.payment_count, 0) + coalesce(pp.prepayment_count, 0)) as source_count
				from fin_payable py
				join mst_supplier supplier on supplier.id = py.supplier_id
				left join proc_purchase_receipt pr on pr.id = py.source_id and py.source_type = 'PURCHASE_RECEIPT'
				left join proc_purchase_order po on po.id = pr.order_id
				left join sal_project project on project.id = po.project_id
				left join payment_counts pc on pc.payable_id = py.id
				left join prepayment_amounts pp on pp.supplier_id = py.supplier_id
					and coalesce(pp.project_id, 0) = coalesce(po.project_id, 0)
				where py.status in ('CONFIRMED', 'PARTIALLY_PAID', 'PAID', 'CLOSED')
				and py.business_date <= ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(query.dateTo());
		args.add(query.dateTo());
		args.add(query.dateTo());
		appendPartyFilters(sql, args, query, "po", "supplier", "project", "py.payable_no");
		sql.append(" order by supplier.name, py.due_date, py.payable_no, py.id");
		return this.jdbcTemplate.query(sql.toString(), (rs, rowNum) -> payableRow(rs, query.dateTo()), args.toArray());
	}

	private ReceivablePayableRow payableRow(ResultSet rs, LocalDate dateTo) throws SQLException {
		BigDecimal openAmount = zero(rs.getBigDecimal("open_amount"));
		BigDecimal settledAmount = zero(rs.getBigDecimal("settled_amount"));
		BucketAmounts buckets = bucket(openAmount, rs.getObject("due_date", LocalDate.class), dateTo);
		long sourceId = rs.getLong("source_id");
		return new ReceivablePayableRow(rs.getObject("project_id", Long.class), "SUPPLIER",
				rs.getString("party_name"), rs.getString("project_no"), "PAYABLE", rs.getString("source_no"),
				BigDecimal.ZERO, openAmount, BigDecimal.ZERO,
				zero(rs.getBigDecimal("prepayment_amount")), settledAmount,
				openAmount.subtract(zero(rs.getBigDecimal("prepayment_amount"))), buckets.notDue(),
				buckets.days1To30(), buckets.days31To60(), buckets.days61To90(), buckets.daysOver90(),
				buckets.overdue(), buckets.bucketName(), rs.getInt("source_count"),
				"receivable-payable:PAYABLE:" + sourceId);
	}

	private void appendPartyFilters(StringBuilder sql, List<Object> args, OperatingFinanceQuery query,
			String projectSourceAlias, String partyAlias, String projectAlias, String sourceNoExpression) {
		if (query.projectId() != null) {
			sql.append(" and ").append(projectSourceAlias).append(".project_id = ?");
			args.add(query.projectId());
		}
		if (hasText(query.keyword())) {
			sql.append(" and (lower(").append(sourceNoExpression).append(") like ? or lower(")
				.append(partyAlias).append(".name) like ? or lower(").append(partyAlias)
				.append(".code) like ? or lower(coalesce(").append(projectAlias)
				.append(".project_no, '')) like ? or lower(coalesce(").append(projectAlias)
				.append(".name, '')) like ?)");
			String like = like(query.keyword());
			for (int index = 0; index < 5; index++) {
				args.add(like);
			}
		}
	}

	private ReceivablePayableSummary summary(OperatingFinanceQuery query, List<ReceivablePayableRow> rows) {
		BigDecimal receivableAmount = rows.stream()
			.map(ReceivablePayableRow::receivableAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal payableAmount = rows.stream()
			.map(ReceivablePayableRow::payableAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal overdueAmount = rows.stream()
			.filter((row) -> "CUSTOMER".equals(row.partyType()))
			.map(ReceivablePayableRow::overdueAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal advanceReceiptAmount = advanceReceiptSummary(query);
		BigDecimal prepaymentAmount = prepaymentSummary(query);
		int sourceCount = rows.stream().mapToInt(ReceivablePayableRow::sourceCount).sum()
				+ countAdvanceReceipts(query) + countPrepayments(query);
		return new ReceivablePayableSummary(receivableAmount, payableAmount, advanceReceiptAmount, prepaymentAmount,
				receivableAmount.subtract(payableAmount), overdueAmount, sourceCount);
	}

	private BigDecimal advanceReceiptSummary(OperatingFinanceQuery query) {
		return sumBalance("""
				select coalesce(sum(rb.available_amount), 0)
				from fin_receipt_balance rb
				join fin_receipt rc on rc.id = rb.receipt_id
				left join sal_project project on project.id = rb.project_id
				join mst_customer party on party.id = rb.customer_id
				where rb.status = 'POSTED'
				and rc.status = 'POSTED'
				and rc.receipt_date <= ?
				""", query, "rb", "rc.receipt_no", "party", "project");
	}

	private BigDecimal prepaymentSummary(OperatingFinanceQuery query) {
		return sumBalance("""
				select coalesce(sum(pb.available_amount), 0)
				from fin_payment_balance pb
				join fin_payment pm on pm.id = pb.payment_id
				left join sal_project project on project.id = pb.project_id
				join mst_supplier party on party.id = pb.supplier_id
				where pb.status = 'POSTED'
				and pm.status = 'POSTED'
				and pm.payment_date <= ?
				""", query, "pb", "pm.payment_no", "party", "project");
	}

	private int countAdvanceReceipts(OperatingFinanceQuery query) {
		Long count = countBalance("""
				select count(*)
				from fin_receipt_balance rb
				join fin_receipt rc on rc.id = rb.receipt_id
				left join sal_project project on project.id = rb.project_id
				join mst_customer party on party.id = rb.customer_id
				where rb.status = 'POSTED'
				and rc.status = 'POSTED'
				and rc.receipt_date <= ?
				""", query, "rb", "rc.receipt_no", "party", "project");
		return count == null ? 0 : count.intValue();
	}

	private int countPrepayments(OperatingFinanceQuery query) {
		Long count = countBalance("""
				select count(*)
				from fin_payment_balance pb
				join fin_payment pm on pm.id = pb.payment_id
				left join sal_project project on project.id = pb.project_id
				join mst_supplier party on party.id = pb.supplier_id
				where pb.status = 'POSTED'
				and pm.status = 'POSTED'
				and pm.payment_date <= ?
				""", query, "pb", "pm.payment_no", "party", "project");
		return count == null ? 0 : count.intValue();
	}

	private BigDecimal sumBalance(String baseSql, OperatingFinanceQuery query, String balanceAlias,
			String sourceNoExpression, String partyAlias, String projectAlias) {
		StringBuilder sql = new StringBuilder(baseSql);
		List<Object> args = new ArrayList<>();
		args.add(query.dateTo());
		appendBalanceFilters(sql, args, query, balanceAlias, sourceNoExpression, partyAlias, projectAlias);
		BigDecimal value = this.jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, args.toArray());
		return value == null ? BigDecimal.ZERO : value;
	}

	private Long countBalance(String baseSql, OperatingFinanceQuery query, String balanceAlias,
			String sourceNoExpression, String partyAlias, String projectAlias) {
		StringBuilder sql = new StringBuilder(baseSql);
		List<Object> args = new ArrayList<>();
		args.add(query.dateTo());
		appendBalanceFilters(sql, args, query, balanceAlias, sourceNoExpression, partyAlias, projectAlias);
		return this.jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
	}

	private void appendBalanceFilters(StringBuilder sql, List<Object> args, OperatingFinanceQuery query,
			String balanceAlias, String sourceNoExpression, String partyAlias, String projectAlias) {
		if (query.projectId() != null) {
			sql.append(" and ").append(balanceAlias).append(".project_id = ?");
			args.add(query.projectId());
		}
		if (hasText(query.keyword())) {
			sql.append(" and (lower(").append(sourceNoExpression).append(") like ? or lower(")
				.append(partyAlias).append(".name) like ? or lower(").append(partyAlias)
				.append(".code) like ? or lower(coalesce(").append(projectAlias)
				.append(".project_no, '')) like ? or lower(coalesce(").append(projectAlias)
				.append(".name, '')) like ?)");
			String like = like(query.keyword());
			for (int index = 0; index < 5; index++) {
				args.add(like);
			}
		}
	}

	private BucketAmounts bucket(BigDecimal amount, LocalDate dueDate, LocalDate dateTo) {
		if (dueDate == null || dueDate.isAfter(dateTo)) {
			return new BucketAmounts(amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, "NOT_DUE");
		}
		long days = ChronoUnit.DAYS.between(dueDate, dateTo);
		if (days <= 30) {
			return new BucketAmounts(BigDecimal.ZERO, amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount,
					"DAYS_1_30");
		}
		if (days <= 60) {
			return new BucketAmounts(BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO, BigDecimal.ZERO, amount,
					"DAYS_31_60");
		}
		if (days <= 90) {
			return new BucketAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO, amount,
					"DAYS_61_90");
		}
		return new BucketAmounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount, amount,
				"DAYS_OVER_90");
	}

	private List<ReportingAdminService.TraceSourceResponse> receivableTraces(long receivableId,
			OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select id, receivable_no, business_date, status, unreceived_amount
				from fin_receivable
				where id = ?
				""", (rs, rowNum) -> trace("RECEIVABLE", rs.getLong("id"), rs.getString("receivable_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("unreceived_amount"), "finance:receivable:view", "finance-receivable-detail",
				routeParams("id", rs.getLong("id"))), receivableId));
		traces.addAll(this.jdbcTemplate.query("""
				select rc.id, rc.receipt_no, rc.receipt_date, rc.status, ra.allocated_amount
				from fin_receipt_allocation ra
				join fin_receipt rc on rc.id = ra.receipt_id
				where ra.receivable_id = ?
				and rc.status = 'POSTED'
				and rc.receipt_date <= ?
				order by rc.receipt_date, rc.id
				""", (rs, rowNum) -> trace("RECEIPT", rs.getLong("id"), rs.getString("receipt_no"), null,
				rs.getObject("receipt_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("allocated_amount"), "finance:receipt:view", "finance-receipt-detail",
				routeParams("id", rs.getLong("id"))), receivableId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select sa.id, sa.allocation_no, sa.business_date, sa.status, sal.amount
				from fin_settlement_allocation_line sal
				join fin_settlement_allocation sa on sa.id = sal.allocation_id
				where sal.target_type = 'RECEIVABLE'
				and sal.target_id = ?
				and sa.status = 'POSTED'
				and sa.business_date <= ?
				order by sa.business_date, sa.id
				""", (rs, rowNum) -> trace("SETTLEMENT_ALLOCATION", rs.getLong("id"), rs.getString("allocation_no"),
				null, rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("amount"), "finance:settlement-allocation:view", "finance-settlement-allocation-detail",
				routeParams("id", rs.getLong("id"))), receivableId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select rc.id, rc.receipt_no, rc.receipt_date, rb.status, rb.available_amount
				from fin_receivable r
				join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
				join sal_sales_order so on so.id = sh.order_id
				join fin_receipt_balance rb on rb.customer_id = r.customer_id
					and coalesce(rb.project_id, 0) = coalesce(so.project_id, 0)
				join fin_receipt rc on rc.id = rb.receipt_id
				where r.id = ?
				and rb.status = 'POSTED'
				and rc.status = 'POSTED'
				and rc.receipt_date <= ?
				order by rc.receipt_date, rc.id
				""", (rs, rowNum) -> trace("ADVANCE_RECEIPT", rs.getLong("id"), rs.getString("receipt_no"), null,
				rs.getObject("receipt_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("available_amount"), "finance:receipt:view", "finance-receipt-detail",
				routeParams("id", rs.getLong("id"))), receivableId, query.dateTo()));
		if (traces.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

	private List<ReportingAdminService.TraceSourceResponse> payableTraces(long payableId, OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select id, payable_no, business_date, status, unpaid_amount
				from fin_payable
				where id = ?
				""", (rs, rowNum) -> trace("PAYABLE", rs.getLong("id"), rs.getString("payable_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("unpaid_amount"), "finance:payable:view", "finance-payable-detail",
				routeParams("id", rs.getLong("id"))), payableId));
		traces.addAll(this.jdbcTemplate.query("""
				select pm.id, pm.payment_no, pm.payment_date, pm.status, pa.allocated_amount
				from fin_payment_allocation pa
				join fin_payment pm on pm.id = pa.payment_id
				where pa.payable_id = ?
				and pm.status = 'POSTED'
				and pm.payment_date <= ?
				order by pm.payment_date, pm.id
				""", (rs, rowNum) -> trace("PAYMENT", rs.getLong("id"), rs.getString("payment_no"), null,
				rs.getObject("payment_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("allocated_amount"), "finance:payment:view", "finance-payment-detail",
				routeParams("id", rs.getLong("id"))), payableId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select pr.id, pr.receipt_no, pr.business_date, pr.status, py.unpaid_amount
				from fin_payable py
				join proc_purchase_receipt pr on pr.id = py.source_id and py.source_type = 'PURCHASE_RECEIPT'
				where py.id = ?
				and pr.status = 'POSTED'
				and pr.business_date <= ?
				""", (rs, rowNum) -> trace("PURCHASE_RECEIPT", rs.getLong("id"), rs.getString("receipt_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("unpaid_amount"), "procurement:receipt:view", "procurement-receipt-detail",
				routeParams("id", rs.getLong("id"))), payableId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select pm.id, pm.payment_no, pm.payment_date, pb.status, pb.available_amount
				from fin_payable py
				join proc_purchase_receipt pr on pr.id = py.source_id and py.source_type = 'PURCHASE_RECEIPT'
				join proc_purchase_order po on po.id = pr.order_id
				join fin_payment_balance pb on pb.supplier_id = py.supplier_id
					and coalesce(pb.project_id, 0) = coalesce(po.project_id, 0)
				join fin_payment pm on pm.id = pb.payment_id
				where py.id = ?
				and pb.status = 'POSTED'
				and pm.status = 'POSTED'
				and pm.payment_date <= ?
				order by pm.payment_date, pm.id
				""", (rs, rowNum) -> trace("PREPAYMENT", rs.getLong("id"), rs.getString("payment_no"), null,
				rs.getObject("payment_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("available_amount"), "finance:payment:view", "finance-payment-detail",
				routeParams("id", rs.getLong("id"))), payableId, query.dateTo()));
		if (traces.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

	private String like(String keyword) {
		return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
	}

	private record ReceivablePayableRow(Long projectId, String partyType, String partyName, String projectNo,
			String sourceType, String sourceNo, BigDecimal receivableAmount, BigDecimal payableAmount,
			BigDecimal advanceReceiptAmount, BigDecimal prepaymentAmount, BigDecimal settledAmount,
			BigDecimal balanceAmount, BigDecimal notDueAmount, BigDecimal aging1To30Amount,
			BigDecimal aging31To60Amount, BigDecimal aging61To90Amount, BigDecimal agingOver90Amount,
			BigDecimal overdueAmount, String agingBucket, int sourceCount, String traceKey) {
	}

	private record ReceivablePayableSummary(BigDecimal receivableAmount, BigDecimal payableAmount,
			BigDecimal advanceReceiptAmount, BigDecimal prepaymentAmount, BigDecimal balanceAmount,
			BigDecimal overdueAmount, int sourceCount) {
	}

	private record BucketAmounts(BigDecimal notDue, BigDecimal days1To30, BigDecimal days31To60,
			BigDecimal days61To90, BigDecimal daysOver90, BigDecimal overdue, String bucketName) {
	}

}
