package com.qherp.api.system.finance;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FinanceAdminService {

	private static final String RECEIVABLE_TARGET = "FINANCE_RECEIVABLE";

	private static final String RECEIPT_TARGET = "FINANCE_RECEIPT";

	private static final String PAYABLE_TARGET = "FINANCE_PAYABLE";

	private static final String PAYMENT_TARGET = "FINANCE_PAYMENT";

	private static final String SALES_SHIPMENT_SOURCE = "SALES_SHIPMENT";

	private static final String PURCHASE_RECEIPT_SOURCE = "PURCHASE_RECEIPT";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger RECEIVABLE_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger RECEIPT_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger PAYABLE_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger PAYMENT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final BusinessPeriodGuard businessPeriodGuard;

	public FinanceAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			BusinessPeriodGuard businessPeriodGuard) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.businessPeriodGuard = businessPeriodGuard;
	}

	@Transactional(readOnly = true)
	public PageResponse<ReceivableCandidateSourceResponse> receivableSources(String keyword, Long customerId,
			LocalDate dateFrom, LocalDate dateTo, Boolean settlementGenerated, int page, int pageSize) {
		QueryParts queryParts = receivableSourceQueryParts(keyword, customerId, dateFrom, dateTo, settlementGenerated);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<ReceivableCandidateSourceResponse> items = this.jdbcTemplate.query("""
				select sh.id as source_id, sh.shipment_no as source_no, o.id as sales_order_id,
				       o.order_no as sales_order_no, sh.customer_id, c.code as customer_code, c.name as customer_name,
				       sh.business_date,
				       coalesce(sum(sl.tax_included_amount), 0) as total_amount,
				       count(sl.id) as line_count,
				       exists (
				       	select 1
				       	from fin_receivable_source frs
				       	where frs.source_type = ?
				       	and frs.source_id = sh.id
				       ) as settlement_generated
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				join sal_sales_shipment_line sl on sl.shipment_id = sh.id
				%s
				group by sh.id, sh.shipment_no, o.id, o.order_no, sh.customer_id, c.code, c.name, sh.business_date
				order by sh.business_date desc, sh.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapCandidateSource, sourceArgs(args).toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<ReceivableSummaryResponse> receivables(String keyword, Long customerId, String status,
			LocalDate dateFrom, LocalDate dateTo, LocalDate dueDateFrom, LocalDate dueDateTo, String sourceNo,
			int page, int pageSize) {
		QueryParts queryParts = receivableQueryParts(keyword, customerId, status, dateFrom, dateTo, dueDateFrom,
				dueDateTo, sourceNo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_receivable r
				join mst_customer c on c.id = r.customer_id
				join sal_sales_shipment sh on sh.id = r.source_id
				join sal_sales_order so on so.id = sh.order_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<ReceivableSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.receivable_no, r.customer_id, c.code as customer_code, c.name as customer_name,
				       r.source_type, r.source_id, r.source_no, so.id as sales_order_id, so.order_no as sales_order_no,
				       r.business_date, r.due_date, r.total_amount, r.received_amount, r.unreceived_amount,
				       r.status, r.remark, r.created_by, r.created_at, r.updated_at, r.confirmed_by, r.confirmed_at,
				       r.cancelled_by, r.cancelled_at, r.closed_by, r.closed_at
				from fin_receivable r
				join mst_customer c on c.id = r.customer_id
				join sal_sales_shipment sh on sh.id = r.source_id
				join sal_sales_order so on so.id = sh.order_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapReceivableSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public ReceivableDetailResponse receivable(Long id) {
		ReceivableSummaryResponse summary = receivableSummary(id).orElseThrow(this::receivableNotFound);
		return new ReceivableDetailResponse(summary.id(), summary.receivableNo(), summary.customerId(),
				summary.customerCode(), summary.customerName(), summary.sourceType(), summary.sourceId(),
				summary.sourceNo(), summary.salesOrderId(), summary.salesOrderNo(), summary.businessDate(),
				summary.dueDate(), summary.totalAmount(), summary.receivedAmount(), summary.unreceivedAmount(),
				summary.status(), summary.remark(), summary.createdByName(), summary.createdAt(), summary.updatedAt(),
				summary.confirmedByName(), summary.confirmedAt(), summary.cancelledByName(), summary.cancelledAt(),
				summary.closedByName(), summary.closedAt(), receivableSources(id), receivableReceipts(id), List.of());
	}

	@Transactional(readOnly = true)
	public PageResponse<ReceivableSourceRecord> receivableSources(Long id, int page, int pageSize) {
		receivableSummary(id).orElseThrow(this::receivableNotFound);
		List<ReceivableSourceRecord> all = receivableSources(id);
		int safePageSize = limit(pageSize);
		int from = Math.min(offset(page, safePageSize), all.size());
		int to = Math.min(from + safePageSize, all.size());
		return PageResponse.of(all.subList(from, to), page, safePageSize, all.size());
	}

	@Transactional
	public ReceivableDetailResponse createReceivable(ReceivableCreateRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.sourceId() == null || request.dueDate() == null
				|| !SALES_SHIPMENT_SOURCE.equals(request.sourceType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ShipmentSource shipment = lockShipment(request.sourceId()).orElseThrow(this::sourceNotFound);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		if (sourceGenerated(request.sourceId())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_DUPLICATED);
		}
		List<ShipmentSourceLine> lines = shipmentSourceLines(request.sourceId());
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		BigDecimal totalAmount = money(lines.stream()
			.map(ShipmentSourceLine::sourceAmount)
			.reduce(ZERO, BigDecimal::add));
		if (totalAmount.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		this.businessPeriodGuard.assertWritable(shipment.businessDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_RECEIVABLE", null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertReceivableWithRetry(shipment, request.dueDate(), totalAmount,
					blankToNull(request.remark()), operator.username(), now);
			insertReceivableSources(created.id(), shipment, lines);
			this.auditService.record(operator, "FINANCE_RECEIVABLE_CREATE", RECEIVABLE_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return receivable(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateFinanceException(exception);
		}
	}

	@Transactional
	public ReceivableDetailResponse updateReceivable(Long id, ReceivableUpdateRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ReceivableRow receivable = lockReceivable(id).orElseThrow(this::receivableNotFound);
		if (receivable.status() != ReceivableStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		if (request == null || request.dueDate() == null) {
			throw new BusinessException(ApiErrorCode.FINANCE_DUE_DATE_INVALID);
		}
		this.businessPeriodGuard.assertWritable(receivable.businessDate(), BusinessPeriodOperation.UPDATE,
				"FINANCE_RECEIVABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receivable
				set due_date = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", request.dueDate(), blankToNull(request.remark()), operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_RECEIVABLE_UPDATE", RECEIVABLE_TARGET, id,
				receivable.receivableNo(), servletRequest);
		return receivable(id);
	}

	@Transactional
	public ReceivableDetailResponse confirmReceivable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		ReceivableRow receivable = lockReceivable(id).orElseThrow(this::receivableNotFound);
		if (receivable.status() != ReceivableStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		ShipmentSource shipment = lockShipment(receivable.sourceId()).orElseThrow(this::sourceNotFound);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(receivable.businessDate(), BusinessPeriodOperation.CONFIRM,
				"FINANCE_RECEIVABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receivable
				set status = ?, confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ReceivableStatus.CONFIRMED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_RECEIVABLE_CONFIRM", RECEIVABLE_TARGET, id,
				receivable.receivableNo(), servletRequest);
		return receivable(id);
	}

	@Transactional
	public ReceivableDetailResponse cancelReceivable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		ReceivableRow receivable = lockReceivable(id).orElseThrow(this::receivableNotFound);
		if (receivable.status() != ReceivableStatus.DRAFT
				&& !(receivable.status() == ReceivableStatus.CONFIRMED && postedReceiptCount(id) == 0)) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(receivable.businessDate(), BusinessPeriodOperation.CANCEL,
				"FINANCE_RECEIVABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receivable
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ReceivableStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_RECEIVABLE_CANCEL", RECEIVABLE_TARGET, id,
				receivable.receivableNo(), servletRequest);
		return receivable(id);
	}

	@Transactional
	public ReceivableDetailResponse closeReceivable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		ReceivableRow receivable = lockReceivable(id).orElseThrow(this::receivableNotFound);
		if (receivable.status() != ReceivableStatus.CONFIRMED
				&& receivable.status() != ReceivableStatus.PARTIALLY_RECEIVED) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(receivable.businessDate(), BusinessPeriodOperation.CLOSE,
				"FINANCE_RECEIVABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receivable
				set status = ?, closed_by = ?, closed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ReceivableStatus.CLOSED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_RECEIVABLE_CLOSE", RECEIVABLE_TARGET, id,
				receivable.receivableNo(), servletRequest);
		return receivable(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<ReceiptSummaryResponse> receipts(String keyword, Long customerId, String status,
			LocalDate dateFrom, LocalDate dateTo, Long receivableId, int page, int pageSize) {
		QueryParts queryParts = receiptQueryParts(keyword, customerId, status, dateFrom, dateTo, receivableId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(distinct p.id)
				from fin_receipt p
				join mst_customer c on c.id = p.customer_id
				left join fin_receipt_allocation a on a.receipt_id = p.id
				left join fin_receivable r on r.id = a.receivable_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<ReceiptSummaryResponse> items = this.jdbcTemplate.query("""
				select p.id, p.receipt_no,
				       case when count(distinct r.id) = 1 then min(r.id) else null end as receivable_id,
				       case when count(distinct r.id) = 1 then min(r.receivable_no) else '多目标核销' end as receivable_no,
				       p.customer_id, c.name as customer_name,
				       p.receipt_date, p.amount, p.method, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.posted_by, p.posted_at
				from fin_receipt p
				join mst_customer c on c.id = p.customer_id
				left join fin_receipt_allocation a on a.receipt_id = p.id
				left join fin_receivable r on r.id = a.receivable_id
				%s
				group by p.id, p.receipt_no, p.customer_id, c.name, p.receipt_date, p.amount, p.method,
				         p.status, p.remark, p.created_by, p.created_at, p.updated_at, p.posted_by, p.posted_at
				order by p.updated_at desc, p.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapReceiptSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public ReceiptDetailResponse createReceipt(Long receivableId, ReceiptRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ReceivableRow receivable = lockReceivable(receivableId).orElseThrow(this::receivableNotFound);
		requireReceiptable(receivable);
		ValidatedReceipt receipt = validateReceipt(request, receivable);
		this.businessPeriodGuard.assertWritable(receipt.receiptDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_RECEIPT", null);
		OffsetDateTime now = OffsetDateTime.now();
		CreatedDocument created = insertReceiptWithRetry(receivable, receipt, operator.username(), now);
		this.jdbcTemplate.update("""
				insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
				values (?, ?, ?)
				""", created.id(), receivableId, receipt.amount());
		this.auditService.record(operator, "FINANCE_RECEIPT_CREATE", RECEIPT_TARGET, created.id(),
				created.documentNo(), servletRequest);
		return receipt(created.id());
	}

	@Transactional(readOnly = true)
	public ReceiptDetailResponse receipt(Long id) {
		ReceiptSummaryResponse summary = receiptSummary(id).orElseThrow(this::receiptNotFound);
		return new ReceiptDetailResponse(summary.id(), summary.receiptNo(), summary.receivableId(),
				summary.receivableNo(), summary.customerId(), summary.customerName(), summary.receiptDate(),
				summary.amount(), summary.method(), summary.status(), summary.remark(), summary.createdByName(),
				summary.createdAt(), summary.updatedAt(), summary.postedByName(), summary.postedAt(),
				receiptAllocations(id));
	}

	@Transactional
	public ReceiptDetailResponse updateReceipt(Long id, ReceiptRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ReceiptRow receipt = lockReceipt(id).orElseThrow(this::receiptNotFound);
		if (receipt.status() != ReceiptStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_POSTED_IMMUTABLE);
		}
		ReceiptAllocationRow allocation = lockReceiptAllocation(id).orElseThrow(this::receiptNotFound);
		ReceivableRow receivable = lockReceivable(allocation.receivableId()).orElseThrow(this::receivableNotFound);
		requireReceiptable(receivable);
		ValidatedReceipt validated = validateReceipt(request, receivable);
		this.businessPeriodGuard.assertWritable(validated.receiptDate(), BusinessPeriodOperation.UPDATE,
				"FINANCE_RECEIPT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receipt
				set receipt_date = ?, amount = ?, method = ?, remark = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", validated.receiptDate(), validated.amount(), validated.method(), blankToNull(validated.remark()),
				operator.username(), now, id);
		this.jdbcTemplate.update("update fin_receipt_allocation set allocated_amount = ? where receipt_id = ?",
				validated.amount(), id);
		this.auditService.record(operator, "FINANCE_RECEIPT_UPDATE", RECEIPT_TARGET, id, receipt.receiptNo(),
				servletRequest);
		return receipt(id);
	}

	@Transactional
	public ReceiptDetailResponse postReceipt(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		ReceiptRow receipt = lockReceipt(id).orElseThrow(this::receiptNotFound);
		if (receipt.status() != ReceiptStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_POSTED_IMMUTABLE);
		}
		ReceiptAllocationRow allocation = lockReceiptAllocation(id).orElseThrow(this::receiptNotFound);
		if (receipt.amount().compareTo(allocation.allocatedAmount()) != 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		ReceivableRow receivable = lockReceivable(allocation.receivableId()).orElseThrow(this::receivableNotFound);
		requireReceiptable(receivable);
		this.businessPeriodGuard.assertWritable(receipt.receiptDate(), BusinessPeriodOperation.POST,
				"FINANCE_RECEIPT", id);
		if (receipt.amount().compareTo(receivable.unreceivedAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_ALLOCATION_EXCEEDS_BALANCE);
		}
		BigDecimal receivedAmount = money(receivable.receivedAmount().add(receipt.amount()));
		BigDecimal unreceivedAmount = money(receivable.totalAmount().subtract(receivedAmount));
		ReceivableStatus nextStatus = unreceivedAmount.compareTo(ZERO) == 0 ? ReceivableStatus.RECEIVED
				: ReceivableStatus.PARTIALLY_RECEIVED;
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receipt
				set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", ReceiptStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
		this.jdbcTemplate.update("""
				update fin_receivable
				set received_amount = ?, unreceived_amount = ?, status = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", receivedAmount, unreceivedAmount, nextStatus.name(), operator.username(), now, receivable.id());
		this.auditService.record(operator, "FINANCE_RECEIPT_POST", RECEIPT_TARGET, id, receipt.receiptNo(),
				servletRequest);
		return receipt(id);
	}

	@Transactional
	public ReceiptDetailResponse cancelReceipt(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		ReceiptRow receipt = lockReceipt(id).orElseThrow(this::receiptNotFound);
		if (receipt.status() != ReceiptStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_POSTED_IMMUTABLE);
		}
		this.businessPeriodGuard.assertWritable(receipt.receiptDate(), BusinessPeriodOperation.CANCEL,
				"FINANCE_RECEIPT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receipt
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", ReceiptStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_RECEIPT_CANCEL", RECEIPT_TARGET, id, receipt.receiptNo(),
				servletRequest);
		return receipt(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<PayableCandidateSourceResponse> payableSources(String keyword, Long supplierId,
			LocalDate dateFrom, LocalDate dateTo, Boolean settlementGenerated, int page, int pageSize) {
		QueryParts queryParts = payableSourceQueryParts(keyword, supplierId, dateFrom, dateTo, settlementGenerated);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_receipt pr
				join proc_purchase_order po on po.id = pr.order_id
				join mst_supplier s on s.id = pr.supplier_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<PayableCandidateSourceResponse> items = this.jdbcTemplate.query("""
				select pr.id as source_id, pr.receipt_no as source_no, po.id as purchase_order_id,
				       po.order_no as purchase_order_no, pr.supplier_id, s.code as supplier_code,
				       s.name as supplier_name, pr.business_date,
				       coalesce(sum(pl.quantity * ol.unit_price), 0) as total_amount,
				       count(pl.id) as line_count,
				       exists (
				       	select 1
				       	from fin_payable_source fps
				       	where fps.source_type = ?
				       	and fps.source_id = pr.id
				       ) as settlement_generated
				from proc_purchase_receipt pr
				join proc_purchase_order po on po.id = pr.order_id
				join mst_supplier s on s.id = pr.supplier_id
				join proc_purchase_receipt_line pl on pl.receipt_id = pr.id
				join proc_purchase_order_line ol on ol.id = pl.order_line_id
				%s
				group by pr.id, pr.receipt_no, po.id, po.order_no, pr.supplier_id, s.code, s.name, pr.business_date
				order by pr.business_date desc, pr.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapPayableCandidateSource, sourceArgs(PURCHASE_RECEIPT_SOURCE,
				args).toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<PayableSummaryResponse> payables(String keyword, Long supplierId, String status,
			LocalDate dateFrom, LocalDate dateTo, LocalDate dueDateFrom, LocalDate dueDateTo, String sourceNo,
			int page, int pageSize) {
		QueryParts queryParts = payableQueryParts(keyword, supplierId, status, dateFrom, dateTo, dueDateFrom,
				dueDateTo, sourceNo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_payable p
				join mst_supplier s on s.id = p.supplier_id
				left join proc_purchase_receipt pr on p.source_type = 'PURCHASE_RECEIPT' and pr.id = p.source_id
				left join proc_purchase_order po on po.id = pr.order_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<PayableSummaryResponse> items = this.jdbcTemplate.query("""
				select p.id, p.payable_no, p.supplier_id, s.code as supplier_code, s.name as supplier_name,
				       p.source_type, p.source_id, p.source_no, po.id as purchase_order_id,
				       po.order_no as purchase_order_no, p.business_date, p.due_date, p.total_amount,
				       p.paid_amount, p.unpaid_amount, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.confirmed_by, p.confirmed_at, p.cancelled_by, p.cancelled_at,
				       p.closed_by, p.closed_at
				from fin_payable p
				join mst_supplier s on s.id = p.supplier_id
				left join proc_purchase_receipt pr on p.source_type = 'PURCHASE_RECEIPT' and pr.id = p.source_id
				left join proc_purchase_order po on po.id = pr.order_id
				%s
				order by p.updated_at desc, p.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapPayableSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PayableDetailResponse payable(Long id) {
		PayableSummaryResponse summary = payableSummary(id).orElseThrow(this::payableNotFound);
		return new PayableDetailResponse(summary.id(), summary.payableNo(), summary.supplierId(),
				summary.supplierCode(), summary.supplierName(), summary.sourceType(), summary.sourceId(),
				summary.sourceNo(), summary.purchaseOrderId(), summary.purchaseOrderNo(), summary.businessDate(),
				summary.dueDate(), summary.totalAmount(), summary.paidAmount(), summary.unpaidAmount(),
				summary.status(), summary.remark(), summary.createdByName(), summary.createdAt(), summary.updatedAt(),
				summary.confirmedByName(), summary.confirmedAt(), summary.cancelledByName(), summary.cancelledAt(),
				summary.closedByName(), summary.closedAt(), payableSources(id), payablePayments(id), List.of());
	}

	@Transactional
	public PayableDetailResponse createPayable(PayableCreateRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.sourceId() == null || request.dueDate() == null
				|| !PURCHASE_RECEIPT_SOURCE.equals(request.sourceType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PurchaseReceiptSource receipt = lockPurchaseReceipt(request.sourceId()).orElseThrow(this::sourceNotFound);
		if (!"POSTED".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		if (payableSourceGenerated(request.sourceId())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_DUPLICATED);
		}
		List<PurchaseReceiptSourceLine> lines = purchaseReceiptSourceLines(request.sourceId());
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		BigDecimal totalAmount = money(lines.stream()
			.map(PurchaseReceiptSourceLine::sourceAmount)
			.reduce(ZERO, BigDecimal::add));
		if (totalAmount.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_PAYABLE", null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertPayableWithRetry(receipt, request.dueDate(), totalAmount,
					blankToNull(request.remark()), operator.username(), now);
			insertPayableSources(created.id(), receipt, lines);
			this.auditService.record(operator, "FINANCE_PAYABLE_CREATE", PAYABLE_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return payable(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateFinanceException(exception);
		}
	}

	@Transactional
	public PayableDetailResponse updatePayable(Long id, PayableUpdateRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PayableRow payable = lockPayable(id).orElseThrow(this::payableNotFound);
		if (payable.status() != PayableStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		if (request == null || request.dueDate() == null) {
			throw new BusinessException(ApiErrorCode.FINANCE_DUE_DATE_INVALID);
		}
		this.businessPeriodGuard.assertWritable(payable.businessDate(), BusinessPeriodOperation.UPDATE,
				"FINANCE_PAYABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payable
				set due_date = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", request.dueDate(), blankToNull(request.remark()), operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_PAYABLE_UPDATE", PAYABLE_TARGET, id, payable.payableNo(),
				servletRequest);
		return payable(id);
	}

	@Transactional
	public PayableDetailResponse confirmPayable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		PayableRow payable = lockPayable(id).orElseThrow(this::payableNotFound);
		if (payable.status() != PayableStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		PurchaseReceiptSource receipt = lockPurchaseReceipt(payable.sourceId()).orElseThrow(this::sourceNotFound);
		if (!"POSTED".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(payable.businessDate(), BusinessPeriodOperation.CONFIRM,
				"FINANCE_PAYABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payable
				set status = ?, confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", PayableStatus.CONFIRMED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_PAYABLE_CONFIRM", PAYABLE_TARGET, id, payable.payableNo(),
				servletRequest);
		return payable(id);
	}

	@Transactional
	public PayableDetailResponse cancelPayable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		PayableRow payable = lockPayable(id).orElseThrow(this::payableNotFound);
		if (payable.status() != PayableStatus.DRAFT
				&& !(payable.status() == PayableStatus.CONFIRMED && postedPaymentCount(id) == 0)) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(payable.businessDate(), BusinessPeriodOperation.CANCEL,
				"FINANCE_PAYABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payable
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", PayableStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_PAYABLE_CANCEL", PAYABLE_TARGET, id, payable.payableNo(),
				servletRequest);
		return payable(id);
	}

	@Transactional
	public PayableDetailResponse closePayable(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		PayableRow payable = lockPayable(id).orElseThrow(this::payableNotFound);
		if (payable.status() != PayableStatus.CONFIRMED && payable.status() != PayableStatus.PARTIALLY_PAID) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(payable.businessDate(), BusinessPeriodOperation.CLOSE,
				"FINANCE_PAYABLE", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payable
				set status = ?, closed_by = ?, closed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", PayableStatus.CLOSED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_PAYABLE_CLOSE", PAYABLE_TARGET, id, payable.payableNo(),
				servletRequest);
		return payable(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<PaymentSummaryResponse> payments(String keyword, Long supplierId, String status,
			LocalDate dateFrom, LocalDate dateTo, Long payableId, int page, int pageSize) {
		QueryParts queryParts = paymentQueryParts(keyword, supplierId, status, dateFrom, dateTo, payableId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(distinct p.id)
				from fin_payment p
				join mst_supplier s on s.id = p.supplier_id
				left join fin_payment_allocation a on a.payment_id = p.id
				left join fin_payable py on py.id = a.payable_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<PaymentSummaryResponse> items = this.jdbcTemplate.query("""
				select p.id, p.payment_no,
				       case when count(distinct py.id) = 1 then min(py.id) else null end as payable_id,
				       case when count(distinct py.id) = 1 then min(py.payable_no) else '多目标核销' end as payable_no,
				       p.supplier_id, s.name as supplier_name,
				       p.payment_date, p.amount, p.method, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.posted_by, p.posted_at
				from fin_payment p
				join mst_supplier s on s.id = p.supplier_id
				left join fin_payment_allocation a on a.payment_id = p.id
				left join fin_payable py on py.id = a.payable_id
				%s
				group by p.id, p.payment_no, p.supplier_id, s.name, p.payment_date, p.amount, p.method,
				         p.status, p.remark, p.created_by, p.created_at, p.updated_at, p.posted_by, p.posted_at
				order by p.updated_at desc, p.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapPaymentSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public PaymentDetailResponse createPayment(Long payableId, PaymentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PayableRow payable = lockPayable(payableId).orElseThrow(this::payableNotFound);
		requirePayable(payable);
		ValidatedPayment payment = validatePayment(request, payable);
		this.businessPeriodGuard.assertWritable(payment.paymentDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_PAYMENT", null);
		OffsetDateTime now = OffsetDateTime.now();
		CreatedDocument created = insertPaymentWithRetry(payable, payment, operator.username(), now);
		this.jdbcTemplate.update("""
				insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
				values (?, ?, ?)
				""", created.id(), payableId, payment.amount());
		this.auditService.record(operator, "FINANCE_PAYMENT_CREATE", PAYMENT_TARGET, created.id(),
				created.documentNo(), servletRequest);
		return payment(created.id());
	}

	@Transactional(readOnly = true)
	public PaymentDetailResponse payment(Long id) {
		PaymentSummaryResponse summary = paymentSummary(id).orElseThrow(this::paymentNotFound);
		return new PaymentDetailResponse(summary.id(), summary.paymentNo(), summary.payableId(),
				summary.payableNo(), summary.supplierId(), summary.supplierName(), summary.paymentDate(),
				summary.amount(), summary.method(), summary.status(), summary.remark(), summary.createdByName(),
				summary.createdAt(), summary.updatedAt(), summary.postedByName(), summary.postedAt(),
				paymentAllocations(id));
	}

	@Transactional
	public PaymentDetailResponse updatePayment(Long id, PaymentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PaymentRow payment = lockPayment(id).orElseThrow(this::paymentNotFound);
		if (payment.status() != PaymentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_POSTED_IMMUTABLE);
		}
		PaymentAllocationRow allocation = lockPaymentAllocation(id).orElseThrow(this::paymentNotFound);
		PayableRow payable = lockPayable(allocation.payableId()).orElseThrow(this::payableNotFound);
		requirePayable(payable);
		ValidatedPayment validated = validatePayment(request, payable);
		this.businessPeriodGuard.assertWritable(validated.paymentDate(), BusinessPeriodOperation.UPDATE,
				"FINANCE_PAYMENT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payment
				set payment_date = ?, amount = ?, method = ?, remark = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", validated.paymentDate(), validated.amount(), validated.method(), blankToNull(validated.remark()),
				operator.username(), now, id);
		this.jdbcTemplate.update("update fin_payment_allocation set allocated_amount = ? where payment_id = ?",
				validated.amount(), id);
		this.auditService.record(operator, "FINANCE_PAYMENT_UPDATE", PAYMENT_TARGET, id, payment.paymentNo(),
				servletRequest);
		return payment(id);
	}

	@Transactional
	public PaymentDetailResponse postPayment(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		PaymentRow payment = lockPayment(id).orElseThrow(this::paymentNotFound);
		if (payment.status() != PaymentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_POSTED_IMMUTABLE);
		}
		PaymentAllocationRow allocation = lockPaymentAllocation(id).orElseThrow(this::paymentNotFound);
		if (payment.amount().compareTo(allocation.allocatedAmount()) != 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		PayableRow payable = lockPayable(allocation.payableId()).orElseThrow(this::payableNotFound);
		requirePayable(payable);
		this.businessPeriodGuard.assertWritable(payment.paymentDate(), BusinessPeriodOperation.POST,
				"FINANCE_PAYMENT", id);
		if (payment.amount().compareTo(payable.unpaidAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_ALLOCATION_EXCEEDS_BALANCE);
		}
		BigDecimal paidAmount = money(payable.paidAmount().add(payment.amount()));
		BigDecimal unpaidAmount = money(payable.totalAmount().subtract(paidAmount));
		PayableStatus nextStatus = unpaidAmount.compareTo(ZERO) == 0 ? PayableStatus.PAID
				: PayableStatus.PARTIALLY_PAID;
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payment
				set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", PaymentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
		this.jdbcTemplate.update("""
				update fin_payable
				set paid_amount = ?, unpaid_amount = ?, status = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", paidAmount, unpaidAmount, nextStatus.name(), operator.username(), now, payable.id());
		this.auditService.record(operator, "FINANCE_PAYMENT_POST", PAYMENT_TARGET, id, payment.paymentNo(),
				servletRequest);
		return payment(id);
	}

	@Transactional
	public PaymentDetailResponse cancelPayment(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		PaymentRow payment = lockPayment(id).orElseThrow(this::paymentNotFound);
		if (payment.status() != PaymentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.FINANCE_POSTED_IMMUTABLE);
		}
		this.businessPeriodGuard.assertWritable(payment.paymentDate(), BusinessPeriodOperation.CANCEL,
				"FINANCE_PAYMENT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payment
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", PaymentStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "FINANCE_PAYMENT_CANCEL", PAYMENT_TARGET, id, payment.paymentNo(),
				servletRequest);
		return payment(id);
	}

	private QueryParts receivableSourceQueryParts(String keyword, Long customerId, LocalDate dateFrom, LocalDate dateTo,
			Boolean settlementGenerated) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("sh.status = 'POSTED'");
		if (hasText(keyword)) {
			conditions.add("(sh.shipment_no ilike ? or o.order_no ilike ? or c.code ilike ? or c.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (customerId != null) {
			conditions.add("sh.customer_id = ?");
			args.add(customerId);
		}
		if (dateFrom != null) {
			conditions.add("sh.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("sh.business_date <= ?");
			args.add(dateTo);
		}
		if (Boolean.FALSE.equals(settlementGenerated)) {
			conditions.add("""
					not exists (
						select 1
						from fin_receivable_source frs
						where frs.source_type = 'SALES_SHIPMENT'
						and frs.source_id = sh.id
					)
					""");
		}
		return where(conditions, args);
	}

	private QueryParts receivableQueryParts(String keyword, Long customerId, String status, LocalDate dateFrom,
			LocalDate dateTo, LocalDate dueDateFrom, LocalDate dueDateTo, String sourceNo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(r.receivable_no ilike ? or c.code ilike ? or c.name ilike ? or r.source_no ilike ?
					or so.order_no ilike ?)
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (customerId != null) {
			conditions.add("r.customer_id = ?");
			args.add(customerId);
		}
		if (hasText(status)) {
			parseReceivableStatus(status);
			conditions.add("r.status = ?");
			args.add(status);
		}
		if (dateFrom != null) {
			conditions.add("r.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("r.business_date <= ?");
			args.add(dateTo);
		}
		if (dueDateFrom != null) {
			conditions.add("r.due_date >= ?");
			args.add(dueDateFrom);
		}
		if (dueDateTo != null) {
			conditions.add("r.due_date <= ?");
			args.add(dueDateTo);
		}
		if (hasText(sourceNo)) {
			conditions.add("r.source_no ilike ?");
			args.add("%" + sourceNo + "%");
		}
		return where(conditions, args);
	}

	private QueryParts receiptQueryParts(String keyword, Long customerId, String status, LocalDate dateFrom,
			LocalDate dateTo, Long receivableId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(p.receipt_no ilike ? or r.receivable_no ilike ? or c.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (customerId != null) {
			conditions.add("p.customer_id = ?");
			args.add(customerId);
		}
		if (hasText(status)) {
			parseReceiptStatus(status);
			conditions.add("p.status = ?");
			args.add(status);
		}
		if (dateFrom != null) {
			conditions.add("p.receipt_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("p.receipt_date <= ?");
			args.add(dateTo);
		}
		if (receivableId != null) {
			conditions.add("a.receivable_id = ?");
			args.add(receivableId);
		}
		return where(conditions, args);
	}

	private QueryParts payableSourceQueryParts(String keyword, Long supplierId, LocalDate dateFrom, LocalDate dateTo,
			Boolean settlementGenerated) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("pr.status = 'POSTED'");
		if (hasText(keyword)) {
			conditions.add("(pr.receipt_no ilike ? or po.order_no ilike ? or s.code ilike ? or s.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (supplierId != null) {
			conditions.add("pr.supplier_id = ?");
			args.add(supplierId);
		}
		if (dateFrom != null) {
			conditions.add("pr.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("pr.business_date <= ?");
			args.add(dateTo);
		}
		if (Boolean.FALSE.equals(settlementGenerated)) {
			conditions.add("""
					not exists (
						select 1
						from fin_payable_source fps
						where fps.source_type = 'PURCHASE_RECEIPT'
						and fps.source_id = pr.id
					)
					""");
		}
		return where(conditions, args);
	}

	private QueryParts payableQueryParts(String keyword, Long supplierId, String status, LocalDate dateFrom,
			LocalDate dateTo, LocalDate dueDateFrom, LocalDate dueDateTo, String sourceNo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(p.payable_no ilike ? or s.code ilike ? or s.name ilike ? or p.source_no ilike ?
					or po.order_no ilike ?)
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (supplierId != null) {
			conditions.add("p.supplier_id = ?");
			args.add(supplierId);
		}
		if (hasText(status)) {
			parsePayableStatus(status);
			conditions.add("p.status = ?");
			args.add(status);
		}
		if (dateFrom != null) {
			conditions.add("p.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("p.business_date <= ?");
			args.add(dateTo);
		}
		if (dueDateFrom != null) {
			conditions.add("p.due_date >= ?");
			args.add(dueDateFrom);
		}
		if (dueDateTo != null) {
			conditions.add("p.due_date <= ?");
			args.add(dueDateTo);
		}
		if (hasText(sourceNo)) {
			conditions.add("p.source_no ilike ?");
			args.add("%" + sourceNo + "%");
		}
		return where(conditions, args);
	}

	private QueryParts paymentQueryParts(String keyword, Long supplierId, String status, LocalDate dateFrom,
			LocalDate dateTo, Long payableId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(p.payment_no ilike ? or py.payable_no ilike ? or s.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (supplierId != null) {
			conditions.add("p.supplier_id = ?");
			args.add(supplierId);
		}
		if (hasText(status)) {
			parsePaymentStatus(status);
			conditions.add("p.status = ?");
			args.add(status);
		}
		if (dateFrom != null) {
			conditions.add("p.payment_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("p.payment_date <= ?");
			args.add(dateTo);
		}
		if (payableId != null) {
			conditions.add("a.payable_id = ?");
			args.add(payableId);
		}
		return where(conditions, args);
	}

	private CreatedDocument insertReceivableWithRetry(ShipmentSource shipment, LocalDate dueDate, BigDecimal amount,
			String remark, String operator, OffsetDateTime now) {
		for (int attempt = 0; attempt < 3; attempt++) {
			String receivableNo = nextNo("AR", RECEIVABLE_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into fin_receivable (
							receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
							total_amount, received_amount, unreceived_amount, status, remark, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, receivableNo, shipment.customerId(), SALES_SHIPMENT_SOURCE, shipment.id(),
						shipment.shipmentNo(), shipment.businessDate(), dueDate, amount, amount,
						ReceivableStatus.DRAFT.name(), remark, operator, now, operator, now);
				return new CreatedDocument(id, receivableNo);
			}
			catch (DuplicateKeyException exception) {
				if (attempt == 2 || !containsConstraint(exception, "uk_fin_receivable_no")) {
					throw exception;
				}
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertReceivableSources(Long receivableId, ShipmentSource shipment, List<ShipmentSourceLine> lines) {
		for (ShipmentSourceLine line : lines) {
			this.jdbcTemplate.update("""
					insert into fin_receivable_source (
						receivable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
					)
					values (?, ?, ?, ?, ?, ?, ?)
					""", receivableId, SALES_SHIPMENT_SOURCE, shipment.id(), shipment.shipmentNo(), line.id(),
					line.lineNo(), money(line.sourceAmount()));
		}
	}

	private CreatedDocument insertReceiptWithRetry(ReceivableRow receivable, ValidatedReceipt receipt, String operator,
			OffsetDateTime now) {
		for (int attempt = 0; attempt < 3; attempt++) {
			String receiptNo = nextNo("RCPT", RECEIPT_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into fin_receipt (
							receipt_no, customer_id, receipt_date, amount, method, status, remark, created_by,
							created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, receiptNo, receivable.customerId(), receipt.receiptDate(), receipt.amount(),
						receipt.method(), ReceiptStatus.DRAFT.name(), blankToNull(receipt.remark()), operator, now,
						operator, now);
				return new CreatedDocument(id, receiptNo);
			}
			catch (DuplicateKeyException exception) {
				if (attempt == 2 || !containsConstraint(exception, "uk_fin_receipt_no")) {
					throw exception;
				}
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private CreatedDocument insertPayableWithRetry(PurchaseReceiptSource receipt, LocalDate dueDate, BigDecimal amount,
			String remark, String operator, OffsetDateTime now) {
		for (int attempt = 0; attempt < 3; attempt++) {
			String payableNo = nextNo("AP", PAYABLE_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into fin_payable (
							payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
							total_amount, paid_amount, unpaid_amount, status, remark, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, payableNo, receipt.supplierId(), PURCHASE_RECEIPT_SOURCE, receipt.id(),
						receipt.receiptNo(), receipt.businessDate(), dueDate, amount, amount,
						PayableStatus.DRAFT.name(), remark, operator, now, operator, now);
				return new CreatedDocument(id, payableNo);
			}
			catch (DuplicateKeyException exception) {
				if (attempt == 2 || !containsConstraint(exception, "uk_fin_payable_no")) {
					throw exception;
				}
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertPayableSources(Long payableId, PurchaseReceiptSource receipt,
			List<PurchaseReceiptSourceLine> lines) {
		for (PurchaseReceiptSourceLine line : lines) {
			this.jdbcTemplate.update("""
					insert into fin_payable_source (
						payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
					)
					values (?, ?, ?, ?, ?, ?, ?)
					""", payableId, PURCHASE_RECEIPT_SOURCE, receipt.id(), receipt.receiptNo(), line.id(),
					line.lineNo(), money(line.sourceAmount()));
		}
	}

	private CreatedDocument insertPaymentWithRetry(PayableRow payable, ValidatedPayment payment, String operator,
			OffsetDateTime now) {
		for (int attempt = 0; attempt < 3; attempt++) {
			String paymentNo = nextNo("PAY", PAYMENT_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into fin_payment (
							payment_no, supplier_id, payment_date, amount, method, status, remark, created_by,
							created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, paymentNo, payable.supplierId(), payment.paymentDate(), payment.amount(),
						payment.method(), PaymentStatus.DRAFT.name(), blankToNull(payment.remark()), operator, now,
						operator, now);
				return new CreatedDocument(id, paymentNo);
			}
			catch (DuplicateKeyException exception) {
				if (attempt == 2 || !containsConstraint(exception, "uk_fin_payment_no")) {
					throw exception;
				}
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private Optional<ShipmentSource> lockShipment(Long id) {
		return this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.order_id, o.order_no, sh.customer_id, c.code as customer_code,
				       c.name as customer_name, sh.business_date, sh.status
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				where sh.id = ?
				for update
				""", this::mapShipmentSource, id).stream().findFirst();
	}

	private Optional<ReceivableRow> lockReceivable(Long id) {
		return this.jdbcTemplate.query("""
				select id, receivable_no, customer_id, source_id, business_date, total_amount, received_amount,
				       unreceived_amount, status
				from fin_receivable
				where id = ?
				for update
				""", this::mapReceivableRow, id).stream().findFirst();
	}

	private Optional<ReceiptRow> lockReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select id, receipt_no, customer_id, receipt_date, amount, method, status, remark, created_by,
				       created_at
				from fin_receipt
				where id = ?
				for update
				""", this::mapReceiptRow, id).stream().findFirst();
	}

	private Optional<ReceiptAllocationRow> lockReceiptAllocation(Long receiptId) {
		return this.jdbcTemplate.query("""
				select id, receipt_id, receivable_id, allocated_amount
				from fin_receipt_allocation
				where receipt_id = ?
				for update
				""", (rs, rowNum) -> new ReceiptAllocationRow(rs.getLong("id"), rs.getLong("receipt_id"),
				rs.getLong("receivable_id"), rs.getBigDecimal("allocated_amount")), receiptId).stream().findFirst();
	}

	private Optional<PurchaseReceiptSource> lockPurchaseReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select pr.id, pr.receipt_no, pr.order_id, po.order_no, pr.supplier_id, s.code as supplier_code,
				       s.name as supplier_name, pr.business_date, pr.status
				from proc_purchase_receipt pr
				join proc_purchase_order po on po.id = pr.order_id
				join mst_supplier s on s.id = pr.supplier_id
				where pr.id = ?
				for update
				""", this::mapPurchaseReceiptSource, id).stream().findFirst();
	}

	private Optional<PayableRow> lockPayable(Long id) {
		return this.jdbcTemplate.query("""
				select id, payable_no, supplier_id, source_id, business_date, total_amount, paid_amount,
				       unpaid_amount, status
				from fin_payable
				where id = ?
				for update
				""", this::mapPayableRow, id).stream().findFirst();
	}

	private Optional<PaymentRow> lockPayment(Long id) {
		return this.jdbcTemplate.query("""
				select id, payment_no, supplier_id, payment_date, amount, method, status, remark, created_by,
				       created_at
				from fin_payment
				where id = ?
				for update
				""", this::mapPaymentRow, id).stream().findFirst();
	}

	private Optional<PaymentAllocationRow> lockPaymentAllocation(Long paymentId) {
		return this.jdbcTemplate.query("""
				select id, payment_id, payable_id, allocated_amount
				from fin_payment_allocation
				where payment_id = ?
				for update
				""", (rs, rowNum) -> new PaymentAllocationRow(rs.getLong("id"), rs.getLong("payment_id"),
				rs.getLong("payable_id"), rs.getBigDecimal("allocated_amount")), paymentId).stream().findFirst();
	}

	private List<ShipmentSourceLine> shipmentSourceLines(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.line_no, sl.order_line_id, sl.material_id, m.code as material_code,
				       m.name as material_name, sl.unit_id, u.name as unit_name, sl.quantity,
				       sl.tax_included_unit_price as unit_price, sl.tax_included_amount as source_amount
				from sal_sales_shipment_line sl
				join mst_material m on m.id = sl.material_id
				join mst_unit u on u.id = sl.unit_id
				where sl.shipment_id = ?
				order by sl.line_no asc, sl.id asc
				""", this::mapShipmentSourceLine, shipmentId);
	}

	private List<PurchaseReceiptSourceLine> purchaseReceiptSourceLines(Long receiptId) {
		return this.jdbcTemplate.query("""
				select pl.id, pl.line_no, pl.order_line_id, pl.material_id, m.code as material_code,
				       m.name as material_name, pl.unit_id, u.name as unit_name, pl.quantity, ol.unit_price,
				       (pl.quantity * ol.unit_price) as source_amount
				from proc_purchase_receipt_line pl
				join proc_purchase_order_line ol on ol.id = pl.order_line_id
				join mst_material m on m.id = pl.material_id
				join mst_unit u on u.id = pl.unit_id
				where pl.receipt_id = ?
				order by pl.line_no asc, pl.id asc
				""", this::mapPurchaseReceiptSourceLine, receiptId);
	}

	private Optional<ReceivableSummaryResponse> receivableSummary(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.receivable_no, r.customer_id, c.code as customer_code, c.name as customer_name,
				       r.source_type, r.source_id, r.source_no, so.id as sales_order_id, so.order_no as sales_order_no,
				       r.business_date, r.due_date, r.total_amount, r.received_amount, r.unreceived_amount,
				       r.status, r.remark, r.created_by, r.created_at, r.updated_at, r.confirmed_by, r.confirmed_at,
				       r.cancelled_by, r.cancelled_at, r.closed_by, r.closed_at
				from fin_receivable r
				join mst_customer c on c.id = r.customer_id
				join sal_sales_shipment sh on sh.id = r.source_id
				join sal_sales_order so on so.id = sh.order_id
				where r.id = ?
				""", this::mapReceivableSummary, id).stream().findFirst();
	}

	private Optional<ReceiptSummaryResponse> receiptSummary(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.receipt_no,
				       case when count(distinct r.id) = 1 then min(r.id) else null end as receivable_id,
				       case when count(distinct r.id) = 1 then min(r.receivable_no) else '多目标核销' end as receivable_no,
				       p.customer_id, c.name as customer_name,
				       p.receipt_date, p.amount, p.method, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.posted_by, p.posted_at
				from fin_receipt p
				join mst_customer c on c.id = p.customer_id
				left join fin_receipt_allocation a on a.receipt_id = p.id
				left join fin_receivable r on r.id = a.receivable_id
				where p.id = ?
				group by p.id, p.receipt_no, p.customer_id, c.name, p.receipt_date, p.amount, p.method,
				         p.status, p.remark, p.created_by, p.created_at, p.updated_at, p.posted_by, p.posted_at
				""", this::mapReceiptSummary, id).stream().findFirst();
	}

	private Optional<PayableSummaryResponse> payableSummary(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.payable_no, p.supplier_id, s.code as supplier_code, s.name as supplier_name,
				       p.source_type, p.source_id, p.source_no, po.id as purchase_order_id,
				       po.order_no as purchase_order_no, p.business_date, p.due_date, p.total_amount,
				       p.paid_amount, p.unpaid_amount, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.confirmed_by, p.confirmed_at, p.cancelled_by, p.cancelled_at,
				       p.closed_by, p.closed_at
				from fin_payable p
				join mst_supplier s on s.id = p.supplier_id
				left join proc_purchase_receipt pr on p.source_type = 'PURCHASE_RECEIPT' and pr.id = p.source_id
				left join proc_purchase_order po on po.id = pr.order_id
				where p.id = ?
				""", this::mapPayableSummary, id).stream().findFirst();
	}

	private Optional<PaymentSummaryResponse> paymentSummary(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.payment_no,
				       case when count(distinct py.id) = 1 then min(py.id) else null end as payable_id,
				       case when count(distinct py.id) = 1 then min(py.payable_no) else '多目标核销' end as payable_no,
				       p.supplier_id, s.name as supplier_name,
				       p.payment_date, p.amount, p.method, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.posted_by, p.posted_at
				from fin_payment p
				join mst_supplier s on s.id = p.supplier_id
				left join fin_payment_allocation a on a.payment_id = p.id
				left join fin_payable py on py.id = a.payable_id
				where p.id = ?
				group by p.id, p.payment_no, p.supplier_id, s.name, p.payment_date, p.amount, p.method,
				         p.status, p.remark, p.created_by, p.created_at, p.updated_at, p.posted_by, p.posted_at
				""", this::mapPaymentSummary, id).stream().findFirst();
	}

	private List<ReceivableSourceRecord> receivableSources(Long receivableId) {
		return this.jdbcTemplate.query("""
				select s.id, s.source_type, s.source_id, s.source_no, s.source_line_id, s.source_line_no,
				       sh.business_date as source_business_date, so.id as sales_order_id, so.order_no as sales_order_no,
				       sl.order_line_id as sales_order_line_id, sl.material_id, m.code as material_code,
				       m.name as material_name,
				       u.name as unit_name, sl.quantity, sl.tax_included_unit_price as unit_price, s.source_amount
				from fin_receivable_source s
				join sal_sales_shipment sh on sh.id = s.source_id
				join sal_sales_order so on so.id = sh.order_id
				join sal_sales_shipment_line sl on sl.id = s.source_line_id
				join mst_material m on m.id = sl.material_id
				join mst_unit u on u.id = sl.unit_id
				where s.receivable_id = ?
				order by s.source_line_no asc, s.id asc
				""", (rs, rowNum) -> new ReceivableSourceRecord(rs.getLong("id"), rs.getString("source_type"),
				rs.getLong("source_id"), rs.getString("source_no"), rs.getLong("source_line_id"),
				rs.getInt("source_line_no"), rs.getObject("source_business_date", LocalDate.class),
				rs.getLong("sales_order_id"), rs.getString("sales_order_no"), rs.getLong("sales_order_line_id"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getString("unit_name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("source_amount")), receivableId);
	}

	private List<PayableSourceRecord> payableSources(Long payableId) {
		return this.jdbcTemplate.query("""
				select s.id, s.source_type, s.source_id, s.source_no, s.source_line_id, s.source_line_no,
				       pr.business_date as source_business_date, po.id as purchase_order_id,
				       po.order_no as purchase_order_no, ol.id as purchase_order_line_id, pl.material_id,
				       m.code as material_code, m.name as material_name, u.name as unit_name, pl.quantity,
				       ol.unit_price, s.source_amount
				from fin_payable_source s
				join proc_purchase_receipt pr on pr.id = s.source_id
				join proc_purchase_order po on po.id = pr.order_id
				join proc_purchase_receipt_line pl on pl.id = s.source_line_id
				join proc_purchase_order_line ol on ol.id = pl.order_line_id
				join mst_material m on m.id = pl.material_id
				join mst_unit u on u.id = pl.unit_id
				where s.payable_id = ?
				order by s.source_line_no asc, s.id asc
				""", (rs, rowNum) -> new PayableSourceRecord(rs.getLong("id"), rs.getString("source_type"),
				rs.getLong("source_id"), rs.getString("source_no"), rs.getLong("source_line_id"),
				rs.getInt("source_line_no"), rs.getObject("source_business_date", LocalDate.class),
				rs.getLong("purchase_order_id"), rs.getString("purchase_order_no"),
				rs.getLong("purchase_order_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getString("unit_name"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("source_amount")), payableId);
	}

	private List<ReceiptSummaryResponse> receivableReceipts(Long receivableId) {
		return this.jdbcTemplate.query("""
				select p.id, p.receipt_no, a.receivable_id, r.receivable_no, p.customer_id, c.name as customer_name,
				       p.receipt_date, p.amount, p.method, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.posted_by, p.posted_at
				from fin_receipt p
				join fin_receipt_allocation a on a.receipt_id = p.id
				join fin_receivable r on r.id = a.receivable_id
				join mst_customer c on c.id = p.customer_id
				where a.receivable_id = ?
				and p.status <> ?
				order by p.created_at asc, p.id asc
				""", this::mapReceiptSummary, receivableId, ReceiptStatus.CANCELLED.name());
	}

	private List<PaymentSummaryResponse> payablePayments(Long payableId) {
		return this.jdbcTemplate.query("""
				select p.id, p.payment_no, a.payable_id, py.payable_no, p.supplier_id, s.name as supplier_name,
				       p.payment_date, p.amount, p.method, p.status, p.remark, p.created_by, p.created_at,
				       p.updated_at, p.posted_by, p.posted_at
				from fin_payment p
				join fin_payment_allocation a on a.payment_id = p.id
				join fin_payable py on py.id = a.payable_id
				join mst_supplier s on s.id = p.supplier_id
				where a.payable_id = ?
				and p.status <> ?
				order by p.created_at asc, p.id asc
				""", this::mapPaymentSummary, payableId, PaymentStatus.CANCELLED.name());
	}

	private List<ReceiptAllocationRecord> receiptAllocations(Long receiptId) {
		return this.jdbcTemplate.query("""
				select a.id, a.receipt_id, p.receipt_no, a.receivable_id, r.receivable_no, r.customer_id,
				       c.name as customer_name, a.allocated_amount
				from fin_receipt_allocation a
				join fin_receipt p on p.id = a.receipt_id
				join fin_receivable r on r.id = a.receivable_id
				join mst_customer c on c.id = r.customer_id
				where a.receipt_id = ?
				order by a.id asc
				""", (rs, rowNum) -> new ReceiptAllocationRecord(rs.getLong("id"), rs.getLong("receipt_id"),
				rs.getString("receipt_no"), rs.getLong("receivable_id"), rs.getString("receivable_no"),
				rs.getLong("customer_id"), rs.getString("customer_name"), rs.getBigDecimal("allocated_amount")),
				receiptId);
	}

	private List<PaymentAllocationRecord> paymentAllocations(Long paymentId) {
		return this.jdbcTemplate.query("""
				select a.id, a.payment_id, p.payment_no, a.payable_id, py.payable_no, py.supplier_id,
				       s.name as supplier_name, a.allocated_amount
				from fin_payment_allocation a
				join fin_payment p on p.id = a.payment_id
				join fin_payable py on py.id = a.payable_id
				join mst_supplier s on s.id = py.supplier_id
				where a.payment_id = ?
				order by a.id asc
				""", (rs, rowNum) -> new PaymentAllocationRecord(rs.getLong("id"), rs.getLong("payment_id"),
				rs.getString("payment_no"), rs.getLong("payable_id"), rs.getString("payable_no"),
				rs.getLong("supplier_id"), rs.getString("supplier_name"), rs.getBigDecimal("allocated_amount")),
				paymentId);
	}

	private boolean sourceGenerated(Long sourceId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_receivable_source
				where source_type = ?
				and source_id = ?
				""", Long.class, SALES_SHIPMENT_SOURCE, sourceId);
		return count != null && count > 0;
	}

	private boolean payableSourceGenerated(Long sourceId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_payable_source
				where source_type = ?
				and source_id = ?
				""", Long.class, PURCHASE_RECEIPT_SOURCE, sourceId);
		return count != null && count > 0;
	}

	private long postedReceiptCount(Long receivableId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_receipt p
				join fin_receipt_allocation a on a.receipt_id = p.id
				where a.receivable_id = ?
				and p.status = ?
				""", Long.class, receivableId, ReceiptStatus.POSTED.name());
		return count == null ? 0 : count;
	}

	private long postedPaymentCount(Long payableId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_payment p
				join fin_payment_allocation a on a.payment_id = p.id
				where a.payable_id = ?
				and p.status = ?
				""", Long.class, payableId, PaymentStatus.POSTED.name());
		return count == null ? 0 : count;
	}

	private ValidatedReceipt validateReceipt(ReceiptRequest request, ReceivableRow receivable) {
		if (request == null || request.receiptDate() == null || !hasText(request.method())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		BigDecimal amount = validateMoney(request.amount());
		if (amount.compareTo(receivable.unreceivedAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_ALLOCATION_EXCEEDS_BALANCE);
		}
		String method = validateOptionalText(request.method(), 32);
		String remark = validateOptionalText(request.remark(), 500);
		return new ValidatedReceipt(request.receiptDate(), amount, method, remark);
	}

	private ValidatedPayment validatePayment(PaymentRequest request, PayableRow payable) {
		if (request == null || request.paymentDate() == null || !hasText(request.method())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		BigDecimal amount = validateMoney(request.amount());
		if (amount.compareTo(payable.unpaidAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_ALLOCATION_EXCEEDS_BALANCE);
		}
		String method = validateOptionalText(request.method(), 32);
		String remark = validateOptionalText(request.remark(), 500);
		return new ValidatedPayment(request.paymentDate(), amount, method, remark);
	}

	private void requireReceiptable(ReceivableRow receivable) {
		if (receivable.status() != ReceivableStatus.CONFIRMED
				&& receivable.status() != ReceivableStatus.PARTIALLY_RECEIVED) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
	}

	private void requirePayable(PayableRow payable) {
		if (payable.status() != PayableStatus.CONFIRMED && payable.status() != PayableStatus.PARTIALLY_PAID) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
	}

	private BigDecimal validateMoney(BigDecimal value) {
		if (value == null || value.scale() > 2 || value.compareTo(ZERO) <= 0 || integerDigits(value) > 16) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		return money(value);
	}

	private String validateOptionalText(String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private ReceivableCandidateSourceResponse mapCandidateSource(ResultSet rs, int rowNum) throws SQLException {
		return new ReceivableCandidateSourceResponse(SALES_SHIPMENT_SOURCE, rs.getLong("source_id"),
				rs.getString("source_no"), rs.getLong("sales_order_id"), rs.getString("sales_order_no"),
				rs.getLong("customer_id"), rs.getString("customer_code"), rs.getString("customer_name"),
				rs.getObject("business_date", LocalDate.class), money(rs.getBigDecimal("total_amount")),
				rs.getInt("line_count"), rs.getBoolean("settlement_generated"));
	}

	private PayableCandidateSourceResponse mapPayableCandidateSource(ResultSet rs, int rowNum) throws SQLException {
		return new PayableCandidateSourceResponse(PURCHASE_RECEIPT_SOURCE, rs.getLong("source_id"),
				rs.getString("source_no"), rs.getLong("purchase_order_id"), rs.getString("purchase_order_no"),
				rs.getLong("supplier_id"), rs.getString("supplier_code"), rs.getString("supplier_name"),
				rs.getObject("business_date", LocalDate.class), money(rs.getBigDecimal("total_amount")),
				rs.getInt("line_count"), rs.getBoolean("settlement_generated"));
	}

	private ReceivableSummaryResponse mapReceivableSummary(ResultSet rs, int rowNum) throws SQLException {
		return new ReceivableSummaryResponse(rs.getLong("id"), rs.getString("receivable_no"),
				rs.getLong("customer_id"), rs.getString("customer_code"), rs.getString("customer_name"),
				rs.getString("source_type"), rs.getLong("source_id"), rs.getString("source_no"),
				rs.getLong("sales_order_id"), rs.getString("sales_order_no"),
				rs.getObject("business_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
				rs.getBigDecimal("total_amount"), rs.getBigDecimal("received_amount"),
				rs.getBigDecimal("unreceived_amount"), rs.getString("status"), rs.getString("remark"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("confirmed_by"),
				rs.getObject("confirmed_at", OffsetDateTime.class), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class));
	}

	private PayableSummaryResponse mapPayableSummary(ResultSet rs, int rowNum) throws SQLException {
		return new PayableSummaryResponse(rs.getLong("id"), rs.getString("payable_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_code"), rs.getString("supplier_name"), rs.getString("source_type"),
				rs.getLong("source_id"), rs.getString("source_no"), rs.getLong("purchase_order_id"),
				rs.getString("purchase_order_no"), rs.getObject("business_date", LocalDate.class),
				rs.getObject("due_date", LocalDate.class), rs.getBigDecimal("total_amount"),
				rs.getBigDecimal("paid_amount"), rs.getBigDecimal("unpaid_amount"), rs.getString("status"),
				rs.getString("remark"), rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("confirmed_by"),
				rs.getObject("confirmed_at", OffsetDateTime.class), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class));
	}

	private ReceiptSummaryResponse mapReceiptSummary(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptSummaryResponse(rs.getLong("id"), rs.getString("receipt_no"),
				nullableLong(rs, "receivable_id"), rs.getString("receivable_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getObject("receipt_date", LocalDate.class),
				rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("status"), rs.getString("remark"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("posted_by"),
				rs.getObject("posted_at", OffsetDateTime.class));
	}

	private PaymentSummaryResponse mapPaymentSummary(ResultSet rs, int rowNum) throws SQLException {
		return new PaymentSummaryResponse(rs.getLong("id"), rs.getString("payment_no"), nullableLong(rs, "payable_id"),
				rs.getString("payable_no"), rs.getLong("supplier_id"), rs.getString("supplier_name"),
				rs.getObject("payment_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getString("method"),
				rs.getString("status"), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class));
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private ShipmentSource mapShipmentSource(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentSource(rs.getLong("id"), rs.getString("shipment_no"), rs.getLong("order_id"),
				rs.getString("order_no"), rs.getLong("customer_id"), rs.getString("customer_code"),
				rs.getString("customer_name"), rs.getObject("business_date", LocalDate.class), rs.getString("status"));
	}

	private PurchaseReceiptSource mapPurchaseReceiptSource(ResultSet rs, int rowNum) throws SQLException {
		return new PurchaseReceiptSource(rs.getLong("id"), rs.getString("receipt_no"), rs.getLong("order_id"),
				rs.getString("order_no"), rs.getLong("supplier_id"), rs.getString("supplier_code"),
				rs.getString("supplier_name"), rs.getObject("business_date", LocalDate.class), rs.getString("status"));
	}

	private ShipmentSourceLine mapShipmentSourceLine(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentSourceLine(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("order_line_id"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getLong("unit_id"), rs.getString("unit_name"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("source_amount"));
	}

	private PurchaseReceiptSourceLine mapPurchaseReceiptSourceLine(ResultSet rs, int rowNum) throws SQLException {
		return new PurchaseReceiptSourceLine(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("order_line_id"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getLong("unit_id"), rs.getString("unit_name"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("source_amount"));
	}

	private ReceivableRow mapReceivableRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceivableRow(rs.getLong("id"), rs.getString("receivable_no"), rs.getLong("customer_id"),
				rs.getLong("source_id"), rs.getObject("business_date", LocalDate.class),
				rs.getBigDecimal("total_amount"), rs.getBigDecimal("received_amount"),
				rs.getBigDecimal("unreceived_amount"), ReceivableStatus.valueOf(rs.getString("status")));
	}

	private PayableRow mapPayableRow(ResultSet rs, int rowNum) throws SQLException {
		return new PayableRow(rs.getLong("id"), rs.getString("payable_no"), rs.getLong("supplier_id"),
				rs.getLong("source_id"), rs.getObject("business_date", LocalDate.class),
				rs.getBigDecimal("total_amount"), rs.getBigDecimal("paid_amount"),
				rs.getBigDecimal("unpaid_amount"), PayableStatus.valueOf(rs.getString("status")));
	}

	private ReceiptRow mapReceiptRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptRow(rs.getLong("id"), rs.getString("receipt_no"), rs.getLong("customer_id"),
				rs.getObject("receipt_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getString("method"),
				ReceiptStatus.valueOf(rs.getString("status")), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class));
	}

	private PaymentRow mapPaymentRow(ResultSet rs, int rowNum) throws SQLException {
		return new PaymentRow(rs.getLong("id"), rs.getString("payment_no"), rs.getLong("supplier_id"),
				rs.getObject("payment_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getString("method"),
				PaymentStatus.valueOf(rs.getString("status")), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class));
	}

	private BusinessException duplicateFinanceException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_fin_receivable_source_line")) {
			return new BusinessException(ApiErrorCode.FINANCE_SOURCE_DUPLICATED);
		}
		if (containsConstraint(exception, "uk_fin_payable_source_line")) {
			return new BusinessException(ApiErrorCode.FINANCE_SOURCE_DUPLICATED);
		}
		if (containsConstraint(exception, "uk_fin_receipt_allocation_receipt")) {
			return new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		if (containsConstraint(exception, "uk_fin_payment_allocation_payment")) {
			return new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private ReceivableStatus parseReceivableStatus(String value) {
		try {
			return ReceivableStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
	}

	private ReceiptStatus parseReceiptStatus(String value) {
		try {
			return ReceiptStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
	}

	private PayableStatus parsePayableStatus(String value) {
		try {
			return PayableStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
	}

	private PaymentStatus parsePaymentStatus(String value) {
		try {
			return PaymentStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
	}

	private BusinessException receivableNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_RECEIVABLE_NOT_FOUND);
	}

	private BusinessException receiptNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_RECEIPT_NOT_FOUND);
	}

	private BusinessException payableNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_PAYABLE_NOT_FOUND);
	}

	private BusinessException paymentNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_PAYMENT_NOT_FOUND);
	}

	private BusinessException sourceNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_SOURCE_NOT_FOUND);
	}

	private QueryParts where(List<String> conditions, List<Object> args) {
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
	}

	private List<Object> sourceArgs(List<Object> queryAndPaginationArgs) {
		List<Object> args = new ArrayList<>();
		args.add(SALES_SHIPMENT_SOURCE);
		args.addAll(queryAndPaginationArgs);
		return args;
	}

	private List<Object> sourceArgs(String sourceType, List<Object> queryAndPaginationArgs) {
		List<Object> args = new ArrayList<>();
		args.add(sourceType);
		args.addAll(queryAndPaginationArgs);
		return args;
	}

	private List<Object> paginationArgs(QueryParts queryParts, int pageSize, int page) {
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		return args;
	}

	private String nextNo(String prefix, AtomicInteger sequence) {
		int value = Math.floorMod(sequence.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private long integerDigits(BigDecimal value) {
		return Math.max(0L, (long) value.precision() - value.scale());
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record ReceivableCreateRequest(@NotNull String sourceType, @NotNull Long sourceId,
			@NotNull LocalDate dueDate, String remark) {
	}

	public record ReceivableUpdateRequest(@NotNull LocalDate dueDate, String remark) {
	}

	public record ReceiptRequest(@NotNull LocalDate receiptDate, @NotNull BigDecimal amount, @NotNull String method,
			String remark) {
	}

	public record PayableCreateRequest(@NotNull String sourceType, @NotNull Long sourceId, @NotNull LocalDate dueDate,
			String remark) {
	}

	public record PayableUpdateRequest(@NotNull LocalDate dueDate, String remark) {
	}

	public record PaymentRequest(@NotNull LocalDate paymentDate, @NotNull BigDecimal amount, @NotNull String method,
			String remark) {
	}

	public record ReceivableCandidateSourceResponse(String sourceType, Long sourceId, String sourceNo,
			Long salesOrderId, String salesOrderNo, Long customerId, String customerCode, String customerName,
			LocalDate businessDate, BigDecimal totalAmount, int lineCount, boolean settlementGenerated) {
	}

	public record PayableCandidateSourceResponse(String sourceType, Long sourceId, String sourceNo,
			Long purchaseOrderId, String purchaseOrderNo, Long supplierId, String supplierCode, String supplierName,
			LocalDate businessDate, BigDecimal totalAmount, int lineCount, boolean settlementGenerated) {
	}

	public record ReceivableSummaryResponse(Long id, String receivableNo, Long customerId, String customerCode,
			String customerName, String sourceType, Long sourceId, String sourceNo, Long salesOrderId,
			String salesOrderNo, LocalDate businessDate, LocalDate dueDate, BigDecimal totalAmount,
			BigDecimal receivedAmount, BigDecimal unreceivedAmount, String status, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt) {
	}

	public record ReceivableDetailResponse(Long id, String receivableNo, Long customerId, String customerCode,
			String customerName, String sourceType, Long sourceId, String sourceNo, Long salesOrderId,
			String salesOrderNo, LocalDate businessDate, LocalDate dueDate, BigDecimal totalAmount,
			BigDecimal receivedAmount, BigDecimal unreceivedAmount, String status, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt, List<ReceivableSourceRecord> sources, List<ReceiptSummaryResponse> receipts,
			List<String> auditSummary) {
	}

	public record ReceivableSourceRecord(Long id, String sourceType, Long sourceId, String sourceNo,
			Long sourceLineId, Integer sourceLineNo, LocalDate sourceBusinessDate, Long salesOrderId,
			String salesOrderNo, Long salesOrderLineId, Long materialId, String materialCode, String materialName,
			String unitName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal sourceAmount) {
	}

	public record ReceiptSummaryResponse(Long id, String receiptNo, Long receivableId, String receivableNo,
			Long customerId, String customerName, LocalDate receiptDate, BigDecimal amount, String method,
			String status, String remark, String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt,
			String postedByName, OffsetDateTime postedAt) {
	}

	public record ReceiptDetailResponse(Long id, String receiptNo, Long receivableId, String receivableNo,
			Long customerId, String customerName, LocalDate receiptDate, BigDecimal amount, String method,
			String status, String remark, String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt,
			String postedByName, OffsetDateTime postedAt, List<ReceiptAllocationRecord> allocations) {
	}

	public record ReceiptAllocationRecord(Long id, Long receiptId, String receiptNo, Long receivableId,
			String receivableNo, Long customerId, String customerName, BigDecimal allocatedAmount) {
	}

	public record PayableSummaryResponse(Long id, String payableNo, Long supplierId, String supplierCode,
			String supplierName, String sourceType, Long sourceId, String sourceNo, Long purchaseOrderId,
			String purchaseOrderNo, LocalDate businessDate, LocalDate dueDate, BigDecimal totalAmount,
			BigDecimal paidAmount, BigDecimal unpaidAmount, String status, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName, OffsetDateTime confirmedAt,
			String cancelledByName, OffsetDateTime cancelledAt, String closedByName, OffsetDateTime closedAt) {
	}

	public record PayableDetailResponse(Long id, String payableNo, Long supplierId, String supplierCode,
			String supplierName, String sourceType, Long sourceId, String sourceNo, Long purchaseOrderId,
			String purchaseOrderNo, LocalDate businessDate, LocalDate dueDate, BigDecimal totalAmount,
			BigDecimal paidAmount, BigDecimal unpaidAmount, String status, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName, OffsetDateTime confirmedAt,
			String cancelledByName, OffsetDateTime cancelledAt, String closedByName, OffsetDateTime closedAt,
			List<PayableSourceRecord> sources, List<PaymentSummaryResponse> payments, List<String> auditSummary) {
	}

	public record PayableSourceRecord(Long id, String sourceType, Long sourceId, String sourceNo, Long sourceLineId,
			Integer sourceLineNo, LocalDate sourceBusinessDate, Long purchaseOrderId, String purchaseOrderNo,
			Long purchaseOrderLineId, Long materialId, String materialCode, String materialName, String unitName,
			BigDecimal quantity, BigDecimal unitPrice, BigDecimal sourceAmount) {
	}

	public record PaymentSummaryResponse(Long id, String paymentNo, Long payableId, String payableNo, Long supplierId,
			String supplierName, LocalDate paymentDate, BigDecimal amount, String method, String status, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName,
			OffsetDateTime postedAt) {
	}

	public record PaymentDetailResponse(Long id, String paymentNo, Long payableId, String payableNo, Long supplierId,
			String supplierName, LocalDate paymentDate, BigDecimal amount, String method, String status, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName,
			OffsetDateTime postedAt, List<PaymentAllocationRecord> allocations) {
	}

	public record PaymentAllocationRecord(Long id, Long paymentId, String paymentNo, Long payableId,
			String payableNo, Long supplierId, String supplierName, BigDecimal allocatedAmount) {
	}

	private record ShipmentSource(Long id, String shipmentNo, Long orderId, String orderNo, Long customerId,
			String customerCode, String customerName, LocalDate businessDate, String status) {
	}

	private record PurchaseReceiptSource(Long id, String receiptNo, Long orderId, String orderNo, Long supplierId,
			String supplierCode, String supplierName, LocalDate businessDate, String status) {
	}

	private record ShipmentSourceLine(Long id, Integer lineNo, Long orderLineId, Long materialId, String materialCode,
			String materialName, Long unitId, String unitName, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal sourceAmount) {
	}

	private record PurchaseReceiptSourceLine(Long id, Integer lineNo, Long orderLineId, Long materialId,
			String materialCode, String materialName, Long unitId, String unitName, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal sourceAmount) {
	}

	private record ReceivableRow(Long id, String receivableNo, Long customerId, Long sourceId, LocalDate businessDate,
			BigDecimal totalAmount, BigDecimal receivedAmount, BigDecimal unreceivedAmount, ReceivableStatus status) {
	}

	private record PayableRow(Long id, String payableNo, Long supplierId, Long sourceId, LocalDate businessDate,
			BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal unpaidAmount, PayableStatus status) {
	}

	private record ReceiptRow(Long id, String receiptNo, Long customerId, LocalDate receiptDate, BigDecimal amount,
			String method, ReceiptStatus status, String remark, String createdBy, OffsetDateTime createdAt) {
	}

	private record PaymentRow(Long id, String paymentNo, Long supplierId, LocalDate paymentDate, BigDecimal amount,
			String method, PaymentStatus status, String remark, String createdBy, OffsetDateTime createdAt) {
	}

	private record ReceiptAllocationRow(Long id, Long receiptId, Long receivableId, BigDecimal allocatedAmount) {
	}

	private record PaymentAllocationRow(Long id, Long paymentId, Long payableId, BigDecimal allocatedAmount) {
	}

	private record ValidatedReceipt(LocalDate receiptDate, BigDecimal amount, String method, String remark) {
	}

	private record ValidatedPayment(LocalDate paymentDate, BigDecimal amount, String method, String remark) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
