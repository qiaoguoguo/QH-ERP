package com.qherp.api.system.reporting;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ContractCollectionItemResponse;
import com.qherp.api.system.reporting.ReportingStage033Service.ContractCollectionSummaryResponse;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
class ContractCollectionReportQueryService extends ReportingStage033QuerySupport {

	ContractCollectionReportQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		super(jdbcTemplate, objectMapper);
	}

	Object contractCollections(MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseQuery(parameters, true);
		if (BUSINESS_SNAPSHOT.equals(query.analysisMode())) {
			return snapshot(query);
		}
		return live(query);
	}

	Object captureSnapshot(String periodCode, LocalDate dateFrom, LocalDate dateTo) {
		return live(captureQuery(periodCode, dateFrom, dateTo));
	}

	ContractCollectionSummaryResponse summaryForOverview(OperatingFinanceQuery query) {
		List<ContractCollectionRow> rows = contractRows(query);
		ContractCollectionSummary summary = rows.stream().reduce(ContractCollectionSummary.empty(),
				ContractCollectionSummary::plus, ContractCollectionSummary::plus);
		return summaryResponse(summary, query, contractCollectionAmountVisible(), contractCollectionSourceVisible());
	}

	PageResponse<ReportingAdminService.TraceSourceResponse> contractCollectionTraces(
			MultiValueMap<String, String> parameters) {
		OperatingFinanceQuery query = parseTraceQuery(parameters);
		TraceKeyParts parts = traceKeyParts(query.traceKey(), "contract-collection");
		if (!"CONTRACT".equals(parts.type())) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		if (!contractCollectionSourceVisible()) {
			return emptyTracePage(query);
		}
		return tracePage(contractTraces(parts.sourceId(), query), query);
	}

	private ReportingAdminService.ReportPageResponse<Object> live(OperatingFinanceQuery query) {
		List<ContractCollectionRow> rows = contractRows(query);
		ContractCollectionSummary summary = rows.stream().reduce(ContractCollectionSummary.empty(),
				ContractCollectionSummary::plus, ContractCollectionSummary::plus);
		boolean amountVisible = contractCollectionAmountVisible();
		boolean sourceVisible = contractCollectionSourceVisible();
		ContractCollectionSummaryResponse response = summaryResponse(summary, query, amountVisible, sourceVisible);
		List<ContractCollectionItemResponse> items = rows.stream()
			.map((row) -> item(row, amountVisible, sourceVisible))
			.toList();
		return pageOf(response, items, query);
	}

	private ReportingAdminService.ReportPageResponse<Object> snapshot(OperatingFinanceQuery query) {
		boolean amountVisible = contractCollectionAmountVisible();
		boolean sourceVisible = contractCollectionSourceVisible();
		Set<String> amountFields = Set.of("contractAmount", "invoiceAmount", "receivedAmount", "allocatedAmount",
				"unreceivedAmount", "advanceReceiptAmount", "overdueAmount", "collectionRate");
		return snapshotPage("CONTRACT_COLLECTION", query, (item) -> matchesCommonSnapshotFilters(item, query),
				(items) -> snapshotSummaryMap(items, query, amountVisible, sourceVisible,
						Map.of("contractAmount", "contractAmount", "invoiceAmount", "invoiceAmount",
								"receivedAmount", "receivedAmount", "unreceivedAmount", "unreceivedAmount",
								"overdueAmount", "overdueAmount", "advanceReceiptAmount",
								"advanceReceiptAmount"),
						Set.of()),
				(item) -> snapshotItemMap(item, amountVisible, sourceVisible, amountFields));
	}

	private ContractCollectionSummaryResponse summaryResponse(ContractCollectionSummary summary,
			OperatingFinanceQuery query, boolean amountVisible, boolean sourceVisible) {
		return new ContractCollectionSummaryResponse(visibleAmount(summary.contractAmount(), amountVisible),
				visibleAmount(summary.invoiceAmount(), amountVisible), visibleAmount(summary.receivedAmount(), amountVisible),
				visibleAmount(summary.unreceivedAmount(), amountVisible), visibleAmount(summary.overdueAmount(), amountVisible),
				visibleAmount(summary.advanceReceiptAmount(), amountVisible), sourceVisible ? summary.sourceCount() : 0,
				query.analysisMode(), freshnessStatus(query));
	}

	private ContractCollectionItemResponse item(ContractCollectionRow row, boolean amountVisible,
			boolean sourceVisible) {
		return new ContractCollectionItemResponse(row.projectId(), row.projectNo(),
				sourceVisible ? row.contractId() : null, sourceVisible ? row.contractNo() : null, row.customerName(),
				visibleAmount(row.contractAmount(), amountVisible), visibleAmount(row.invoiceAmount(), amountVisible),
				visibleAmount(row.receivedAmount(), amountVisible), visibleAmount(row.allocatedAmount(), amountVisible),
				visibleAmount(row.unreceivedAmount(), amountVisible),
				visibleAmount(row.advanceReceiptAmount(), amountVisible),
				visibleAmount(row.overdueAmount(), amountVisible),
				percentageIfVisible(row.receivedAmount(), row.contractAmount(), amountVisible), row.status(),
				sourceVisible ? row.sourceCount() : 0,
				sourceVisible ? "contract-collection:CONTRACT:" + row.contractId() : null,
				sourceVisible ? row.invoiceNos() : null, sourceVisible ? row.receiptNos() : null,
				sourceVisible ? row.receivableNos() : null);
	}

	private List<ContractCollectionRow> contractRows(OperatingFinanceQuery query) {
		StringBuilder sql = new StringBuilder("""
				with invoice_amounts as (
					select so.contract_id,
					       coalesce(sum(si.tax_included_amount), 0) as invoice_amount,
					       count(distinct si.id) as invoice_count,
					       string_agg(distinct si.invoice_no, ',') as invoice_nos
					from fin_sales_invoice si
					join sal_sales_shipment sh on sh.id = si.source_id and si.source_type = 'SALES_SHIPMENT'
					join sal_sales_order so on so.id = sh.order_id
					where si.status = 'CONFIRMED'
					and si.invoice_date <= ?
					and (cast(? as text) is null or lower(si.invoice_no) like ? or lower(si.source_no) like ?)
					group by so.contract_id
				),
				receivable_amounts as (
					select so.contract_id,
					       coalesce(sum(r.received_amount), 0) as received_amount,
					       coalesce(sum(r.unreceived_amount), 0) as unreceived_amount,
					       coalesce(sum(case when r.due_date <= ? then r.unreceived_amount else 0 end), 0) as overdue_amount,
					       count(distinct r.id) as receivable_count,
					       string_agg(distinct r.receivable_no, ',') as receivable_nos
					from fin_receivable r
					join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
					join sal_sales_order so on so.id = sh.order_id
					where r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
					and r.business_date <= ?
					and (cast(? as text) is null or lower(r.receivable_no) like ? or lower(r.source_no) like ?)
					group by so.contract_id
				),
				receipt_amounts as (
					select so.contract_id,
					       coalesce(sum(ra.allocated_amount), 0) as allocated_amount,
					       count(distinct rc.id) as receipt_count,
					       string_agg(distinct rc.receipt_no, ',') as receipt_nos
					from fin_receipt_allocation ra
					join fin_receipt rc on rc.id = ra.receipt_id
					join fin_receivable r on r.id = ra.receivable_id
					join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
					join sal_sales_order so on so.id = sh.order_id
					where rc.status = 'POSTED'
					and rc.receipt_date <= ?
					and (cast(? as text) is null or lower(rc.receipt_no) like ?)
					group by so.contract_id
				),
				advance_amounts as (
					select rb.project_id,
					       rb.customer_id,
					       coalesce(sum(rb.available_amount), 0) as advance_receipt_amount,
					       count(distinct rb.receipt_id) as advance_count
					from fin_receipt_balance rb
					join fin_receipt rc on rc.id = rb.receipt_id
					where rb.status = 'POSTED'
					and rc.status = 'POSTED'
					and rc.receipt_date <= ?
					and (cast(? as text) is null or lower(rc.receipt_no) like ?)
					group by rb.project_id, rb.customer_id
				)
				select c.id as contract_id, c.contract_no, c.contract_type, c.project_id, p.project_no,
				       customer.name as customer_name, c.amount as contract_amount,
				       coalesce(i.invoice_amount, 0) as invoice_amount,
				       coalesce(r.received_amount, 0) as received_amount,
				       coalesce(r.unreceived_amount, 0) as unreceived_amount,
				       coalesce(r.overdue_amount, 0) as overdue_amount,
				       coalesce(ra.allocated_amount, 0) as allocated_amount,
				       case when c.contract_type = 'MAIN' then coalesce(ad.advance_receipt_amount, 0) else 0 end as advance_receipt_amount,
				       i.invoice_nos,
				       r.receivable_nos,
				       ra.receipt_nos,
				       (1 + coalesce(i.invoice_count, 0) + coalesce(r.receivable_count, 0)
				          + coalesce(ra.receipt_count, 0)
				          + case when c.contract_type = 'MAIN' then coalesce(ad.advance_count, 0) else 0 end) as source_count
				from sal_project_contract c
				join sal_project p on p.id = c.project_id
				join mst_customer customer on customer.id = p.customer_id
				left join invoice_amounts i on i.contract_id = c.id
				left join receivable_amounts r on r.contract_id = c.id
				left join receipt_amounts ra on ra.contract_id = c.id
				left join advance_amounts ad on ad.project_id = c.project_id and ad.customer_id = p.customer_id
				where c.status in ('EFFECTIVE', 'CLOSED')
				and c.signed_date <= ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(query.dateTo());
		addKeywordArgs(args, query, 2);
		args.add(query.dateTo());
		args.add(query.dateTo());
		addKeywordArgs(args, query, 2);
		args.add(query.dateTo());
		addKeywordArgs(args, query, 1);
		args.add(query.dateTo());
		addKeywordArgs(args, query, 1);
		args.add(query.dateTo());
		if (query.projectId() != null) {
			sql.append(" and c.project_id = ?");
			args.add(query.projectId());
		}
		if (query.contractId() != null) {
			sql.append(" and c.id = ?");
			args.add(query.contractId());
		}
		if (hasText(query.keyword())) {
			sql.append("""
					 and (
						lower(c.contract_no) like ?
						or lower(p.project_no) like ?
						or lower(p.name) like ?
						or lower(customer.name) like ?
						or exists (
							select 1
							from fin_sales_invoice si
							join sal_sales_shipment sh on sh.id = si.source_id and si.source_type = 'SALES_SHIPMENT'
							join sal_sales_order so on so.id = sh.order_id
							where so.contract_id = c.id
							and lower(si.invoice_no) like ?
						)
						or exists (
							select 1
							from fin_receivable fr
							join sal_sales_shipment sh on sh.id = fr.source_id and fr.source_type = 'SALES_SHIPMENT'
							join sal_sales_order so on so.id = sh.order_id
							where so.contract_id = c.id
							and lower(fr.receivable_no) like ?
						)
					)
					""");
			String like = like(query.keyword());
			for (int index = 0; index < 6; index++) {
				args.add(like);
			}
		}
		sql.append(" order by p.project_no, c.contract_no, c.id");
		return this.jdbcTemplate.query(sql.toString(), this::contractRow, args.toArray());
	}

	private ContractCollectionRow contractRow(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal contractAmount = zero(rs.getBigDecimal("contract_amount"));
		BigDecimal receivedAmount = zero(rs.getBigDecimal("received_amount"));
		BigDecimal unreceivedAmount = zero(rs.getBigDecimal("unreceived_amount"));
		BigDecimal overdueAmount = zero(rs.getBigDecimal("overdue_amount"));
		String status = overdueAmount.compareTo(BigDecimal.ZERO) > 0 ? "OVERDUE"
				: unreceivedAmount.compareTo(BigDecimal.ZERO) > 0 ? "UNRECEIVED" : "COLLECTED";
		return new ContractCollectionRow(rs.getLong("contract_id"), rs.getLong("project_id"),
				rs.getString("project_no"), rs.getString("contract_no"), rs.getString("customer_name"),
				contractAmount, zero(rs.getBigDecimal("invoice_amount")), receivedAmount,
				zero(rs.getBigDecimal("allocated_amount")), unreceivedAmount,
				zero(rs.getBigDecimal("advance_receipt_amount")), overdueAmount, status, rs.getInt("source_count"),
				rs.getString("invoice_nos"), rs.getString("receipt_nos"), rs.getString("receivable_nos"));
	}

	private List<ReportingAdminService.TraceSourceResponse> contractTraces(long contractId,
			OperatingFinanceQuery query) {
		List<ReportingAdminService.TraceSourceResponse> traces = new ArrayList<>();
		traces.addAll(this.jdbcTemplate.query("""
				select c.id, c.contract_no, c.signed_date, c.status, c.amount
				from sal_project_contract c
				where c.id = ?
				""", (rs, rowNum) -> trace("SALES_PROJECT_CONTRACT", rs.getLong("id"), rs.getString("contract_no"),
				null, rs.getObject("signed_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("amount"), "sales:contract:view", "sales-project-contract-detail",
				routeParams("id", rs.getLong("id"))), contractId));
		traces.addAll(this.jdbcTemplate.query("""
				select si.id, si.invoice_no, si.invoice_date, si.status, si.tax_included_amount
				from fin_sales_invoice si
				join sal_sales_shipment sh on sh.id = si.source_id and si.source_type = 'SALES_SHIPMENT'
				join sal_sales_order so on so.id = sh.order_id
				where so.contract_id = ?
				and si.status = 'CONFIRMED'
				and si.invoice_date <= ?
				order by si.invoice_date, si.id
				""", (rs, rowNum) -> trace("SALES_INVOICE", rs.getLong("id"), rs.getString("invoice_no"), null,
				rs.getObject("invoice_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("tax_included_amount"), "finance:sales-invoice:view", "finance-sales-invoice-detail",
				routeParams("id", rs.getLong("id"))), contractId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select r.id, r.receivable_no, r.business_date, r.status, r.unreceived_amount
				from fin_receivable r
				join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
				join sal_sales_order so on so.id = sh.order_id
				where so.contract_id = ?
				and r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')
				and r.business_date <= ?
				order by r.business_date, r.id
				""", (rs, rowNum) -> trace("RECEIVABLE", rs.getLong("id"), rs.getString("receivable_no"), null,
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("unreceived_amount"), "finance:receivable:view", "finance-receivable-detail",
				routeParams("id", rs.getLong("id"))), contractId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select rc.id, rc.receipt_no, rc.receipt_date, rc.status, ra.allocated_amount
				from fin_receipt_allocation ra
				join fin_receipt rc on rc.id = ra.receipt_id
				join fin_receivable r on r.id = ra.receivable_id
				join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
				join sal_sales_order so on so.id = sh.order_id
				where so.contract_id = ?
				and rc.status = 'POSTED'
				and rc.receipt_date <= ?
				order by rc.receipt_date, rc.id
				""", (rs, rowNum) -> trace("RECEIPT", rs.getLong("id"), rs.getString("receipt_no"), null,
				rs.getObject("receipt_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("allocated_amount"), "finance:receipt:view", "finance-receipt-detail",
				routeParams("id", rs.getLong("id"))), contractId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select sa.id, sa.allocation_no, sa.business_date, sa.status, sal.amount
				from fin_settlement_allocation_line sal
				join fin_settlement_allocation sa on sa.id = sal.allocation_id
				join fin_receivable r on r.id = sal.target_id and sal.target_type = 'RECEIVABLE'
				join sal_sales_shipment sh on sh.id = r.source_id and r.source_type = 'SALES_SHIPMENT'
				join sal_sales_order so on so.id = sh.order_id
				where so.contract_id = ?
				and sa.status = 'POSTED'
				and sa.business_date <= ?
				order by sa.business_date, sa.id
				""", (rs, rowNum) -> trace("SETTLEMENT_ALLOCATION", rs.getLong("id"), rs.getString("allocation_no"),
				null, rs.getObject("business_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("amount"), "finance:settlement-allocation:view", "finance-settlement-allocation-detail",
				routeParams("id", rs.getLong("id"))), contractId, query.dateTo()));
		traces.addAll(this.jdbcTemplate.query("""
				select rc.id, rc.receipt_no, rc.receipt_date, rb.status, rb.available_amount
				from sal_project_contract c
				join sal_project p on p.id = c.project_id
				join fin_receipt_balance rb on rb.project_id = c.project_id and rb.customer_id = p.customer_id
				join fin_receipt rc on rc.id = rb.receipt_id
				where c.id = ?
				and c.contract_type = 'MAIN'
				and rb.status = 'POSTED'
				and rc.status = 'POSTED'
				and rc.receipt_date <= ?
				order by rc.receipt_date, rc.id
				""", (rs, rowNum) -> trace("ADVANCE_RECEIPT", rs.getLong("id"), rs.getString("receipt_no"), null,
				rs.getObject("receipt_date", LocalDate.class), rs.getString("status"), null,
				rs.getBigDecimal("available_amount"), "finance:receipt:view", "finance-receipt-detail",
				routeParams("id", rs.getLong("id"))), contractId, query.dateTo()));
		if (traces.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REPORT_TRACE_KEY_INVALID);
		}
		return traces;
	}

	private String like(String keyword) {
		return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
	}

	private void addKeywordArgs(List<Object> args, OperatingFinanceQuery query, int likeCount) {
		String value = hasText(query.keyword()) ? like(query.keyword()) : null;
		args.add(value);
		for (int index = 0; index < likeCount; index++) {
			args.add(value);
		}
	}

	private record ContractCollectionRow(Long contractId, Long projectId, String projectNo, String contractNo,
			String customerName, BigDecimal contractAmount, BigDecimal invoiceAmount, BigDecimal receivedAmount,
			BigDecimal allocatedAmount, BigDecimal unreceivedAmount, BigDecimal advanceReceiptAmount,
			BigDecimal overdueAmount, String status, int sourceCount, String invoiceNos, String receiptNos,
			String receivableNos) {
	}

	private record ContractCollectionSummary(BigDecimal contractAmount, BigDecimal invoiceAmount,
			BigDecimal receivedAmount, BigDecimal unreceivedAmount, BigDecimal overdueAmount,
			BigDecimal advanceReceiptAmount, int sourceCount) {

		static ContractCollectionSummary empty() {
			return new ContractCollectionSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, BigDecimal.ZERO, 0);
		}

		ContractCollectionSummary plus(ContractCollectionRow row) {
			return new ContractCollectionSummary(this.contractAmount.add(row.contractAmount()),
					this.invoiceAmount.add(row.invoiceAmount()), this.receivedAmount.add(row.receivedAmount()),
					this.unreceivedAmount.add(row.unreceivedAmount()), this.overdueAmount.add(row.overdueAmount()),
					this.advanceReceiptAmount.add(row.advanceReceiptAmount()), this.sourceCount + row.sourceCount());
		}

		ContractCollectionSummary plus(ContractCollectionSummary other) {
			return new ContractCollectionSummary(this.contractAmount.add(other.contractAmount),
					this.invoiceAmount.add(other.invoiceAmount), this.receivedAmount.add(other.receivedAmount),
					this.unreceivedAmount.add(other.unreceivedAmount), this.overdueAmount.add(other.overdueAmount),
					this.advanceReceiptAmount.add(other.advanceReceiptAmount), this.sourceCount + other.sourceCount);
		}

	}

}
