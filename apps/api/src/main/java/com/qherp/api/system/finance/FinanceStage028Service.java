package com.qherp.api.system.finance;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FinanceStage028Service {

	private static final String SALES_SHIPMENT = "SALES_SHIPMENT";

	private static final String PURCHASE_RECEIPT = "PURCHASE_RECEIPT";

	private static final String OUTSOURCING_RECEIPT = "OUTSOURCING_RECEIPT";

	private static final String OUTSOURCING_SETTLEMENT = "OUTSOURCING_SETTLEMENT";

	private static final String EXPENSE = "EXPENSE";

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger SALES_INVOICE_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger PURCHASE_INVOICE_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger EXPENSE_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger ADVANCE_RECEIPT_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger PREPAYMENT_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger ALLOCATION_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger VOUCHER_DRAFT_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final BusinessPeriodGuard businessPeriodGuard;

	public FinanceStage028Service(JdbcTemplate jdbcTemplate, AuditService auditService,
			BusinessPeriodGuard businessPeriodGuard) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.businessPeriodGuard = businessPeriodGuard;
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> salesInvoices(String keyword, String status, CurrentUser currentUser,
			int page, int pageSize) {
		requireUser(currentUser, "finance:sales-invoice:view");
		return page("fin_sales_invoice", "invoice_no", keyword, status, page, pageSize,
				(id) -> salesInvoice(id, currentUser));
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> salesInvoiceCandidates(String keyword, Long sourceId, Long customerId,
			String ownershipType, Long projectId, CurrentUser currentUser, int page, int pageSize) {
		requireUser(currentUser, "finance:sales-invoice:view");
		List<Object> args = new ArrayList<>();
		String where = " where sh.status = 'POSTED' and sl.quantity > coalesce(inv.invoiced_quantity, 0)";
		if (hasText(keyword)) {
			where += " and (sh.shipment_no ilike ? or so.order_no ilike ? or c.name ilike ?)";
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (sourceId != null) {
			where += " and sh.id = ?";
			args.add(sourceId);
		}
		if (customerId != null) {
			where += " and sh.customer_id = ?";
			args.add(customerId);
		}
		if (hasText(ownershipType)) {
			where += " and coalesce(case when so.project_id is null then 'PUBLIC' else 'PROJECT' end, 'PUBLIC') = ?";
			args.add(ownershipType);
		}
		if (projectId != null) {
			where += " and so.project_id = ?";
			args.add(projectId);
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				left join (
					select sil.source_line_id, sum(sil.quantity) as invoiced_quantity
					from fin_sales_invoice_line sil
					join fin_sales_invoice si on si.id = sil.sales_invoice_id
					where si.status <> 'CANCELLED'
					group by sil.source_line_id
				) inv on inv.source_line_id = sl.id
				""" + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = normalizeRows(this.jdbcTemplate.queryForList("""
				select sh.id as source_id, sh.customer_id, sl.id as source_line_id, sh.shipment_no as source_no,
				       sl.line_no, sl.material_id, m.code as material_code,
				       m.name as material_name, u.name as unit_name,
				       coalesce(inv.invoiced_quantity, 0) as invoiced_quantity,
				       sl.quantity - coalesce(inv.invoiced_quantity, 0) as available_quantity,
				       sl.quantity - coalesce(inv.invoiced_quantity, 0) as invoice_quantity,
				       sl.tax_excluded_unit_price as pretax_unit_price, sl.tax_included_unit_price,
				       sl.tax_rate,
				       round((sl.quantity - coalesce(inv.invoiced_quantity, 0)) * sl.tax_excluded_unit_price, 2)
				           as pretax_amount,
				       round((sl.quantity - coalesce(inv.invoiced_quantity, 0))
				           * (sl.tax_included_unit_price - sl.tax_excluded_unit_price), 2) as tax_amount,
				       round((sl.quantity - coalesce(inv.invoiced_quantity, 0)) * sl.tax_included_unit_price, 2)
				           as total_amount
				from sal_sales_shipment_line sl
				join sal_sales_shipment sh on sh.id = sl.shipment_id
				join sal_sales_order so on so.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				join mst_material m on m.id = sl.material_id
				join mst_unit u on u.id = sl.unit_id
				left join (
					select sil.source_line_id, sum(sil.quantity) as invoiced_quantity
					from fin_sales_invoice_line sil
					join fin_sales_invoice si on si.id = sil.sales_invoice_id
					where si.status <> 'CANCELLED'
					group by sil.source_line_id
				) inv on inv.source_line_id = sl.id
				""" + where + " " + """
				order by sh.business_date desc, sh.id desc, sl.line_no asc
				limit ? offset ?
				""", pageArgs.toArray()));
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> createSalesInvoice(SalesInvoiceCreateRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:sales-invoice:create");
		if (request == null || request.invoiceDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String sourceType = salesSourceType(request);
		Long sourceId = salesSourceId(request);
		LocalDate dueDate = defaultDueDate(request.dueDate(), request.invoiceDate());
		if (sourceId == null || !SALES_SHIPMENT.equals(sourceType)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireMutationMetadata(request.version(), request.idempotencyKey());
		String requestFingerprint = fingerprint("SALES_INVOICE_CREATE", request);
		Optional<Long> idempotent = idempotentDocument("fin_sales_invoice", currentUser.username(),
				request.idempotencyKey(), requestFingerprint);
		if (idempotent.isPresent()) {
			return salesInvoice(idempotent.get(), currentUser);
		}
		ShipmentSource source = lockShipment(sourceId).orElseThrow(this::sourceNotFound);
		validateSalesRequestSource(request, source);
		if (!"POSTED".equals(source.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		List<SalesSourceLine> lines = salesSourceLines(source.id(), request.sourceLines(), null);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		DocumentAmounts amounts = salesAmounts(lines);
		this.businessPeriodGuard.assertWritable(request.invoiceDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_SALES_INVOICE", null);
		PartySnapshot party = customerSnapshot(source.customerId());
		String invoiceNo = nextNo("SI", SALES_INVOICE_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long invoiceId = this.jdbcTemplate.queryForObject("""
					insert into fin_sales_invoice (
						invoice_no, customer_id, ownership_type, project_id, source_type, source_id, source_no,
						invoice_date, due_date, external_invoice_no, invoice_type, tax_excluded_amount, tax_amount,
						tax_included_amount, status, idempotency_key, request_fingerprint, party_snapshot, source_snapshot, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as varchar), ?, ?, ?, ?, 'DRAFT', cast(? as varchar), ?,
						jsonb_build_object('invoiceTitle', cast(? as varchar), 'taxNo', cast(? as varchar),
							'bankName', cast(? as varchar), 'bankAccount', cast(? as varchar)),
						jsonb_build_object('sourceType', cast(? as varchar), 'sourceId', ?, 'sourceNo', cast(? as varchar)),
						cast(? as varchar), ?, ?, ?, ?)
					returning id
					""", Long.class, invoiceNo, source.customerId(), source.ownershipType(), source.projectId(),
					SALES_SHIPMENT, source.id(), source.shipmentNo(), request.invoiceDate(), dueDate,
					blankToNull(request.externalInvoiceNo()), invoiceType(request.invoiceType(), party.invoiceType()),
					amounts.taxExcludedAmount(), amounts.taxAmount(), amounts.taxIncludedAmount(),
					blankToNull(request.idempotencyKey()), requestFingerprint, party.invoiceTitle(), party.taxNo(),
					party.bankName(), party.bankAccount(), SALES_SHIPMENT, source.id(), source.shipmentNo(),
					blankToNull(request.remark()), currentUser.username(), now, currentUser.username(), now);
			insertSalesInvoiceLines(invoiceId, source, lines, now);
			this.auditService.record(currentUser, "FINANCE_SALES_INVOICE_CREATE", "FINANCE_SALES_INVOICE", invoiceId,
					invoiceNo, servletRequest);
			return salesInvoice(invoiceId, currentUser);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateFinanceException(exception);
		}
	}

	@Transactional
	public Map<String, Object> updateSalesInvoice(Long id, SalesInvoiceCreateRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:sales-invoice:update");
		if (request == null || request.invoiceDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ActionRequestContext action = mutationRequest(currentUser, "UPDATE", "FINANCE_SALES_INVOICE", id,
				request.version(), request.idempotencyKey(), fingerprint("SALES_INVOICE_UPDATE", id, request));
		if (action.resultResourceId() != null) {
			return salesInvoice(action.resultResourceId(), currentUser);
		}
		InvoiceRow invoice = lockSalesInvoice(id).orElseThrow(this::invoiceNotFound);
		assertVersion(action.version(), invoice.version());
		if (!"DRAFT".equals(invoice.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		String sourceType = salesSourceType(request);
		Long sourceId = salesSourceId(request);
		LocalDate dueDate = defaultDueDate(request.dueDate(), request.invoiceDate());
		if (sourceId == null || !SALES_SHIPMENT.equals(sourceType)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ShipmentSource source = lockShipment(sourceId).orElseThrow(this::sourceNotFound);
		validateSalesRequestSource(request, source);
		if (!"POSTED".equals(source.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		List<SalesSourceLine> lines = salesSourceLines(source.id(), request.sourceLines(), id);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		DocumentAmounts amounts = salesAmounts(lines);
		this.businessPeriodGuard.assertWritable(request.invoiceDate(), BusinessPeriodOperation.UPDATE,
				"FINANCE_SALES_INVOICE", id);
		PartySnapshot party = customerSnapshot(source.customerId());
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("delete from fin_sales_invoice_line where sales_invoice_id = ?", id);
		this.jdbcTemplate.update("""
				update fin_sales_invoice
				set customer_id = ?, ownership_type = ?, project_id = ?, source_type = ?, source_id = ?, source_no = ?,
				    invoice_date = ?, due_date = ?, external_invoice_no = cast(? as varchar), invoice_type = ?,
				    tax_excluded_amount = ?, tax_amount = ?, tax_included_amount = ?,
				    party_snapshot = jsonb_build_object('invoiceTitle', cast(? as varchar), 'taxNo', cast(? as varchar),
					    'bankName', cast(? as varchar), 'bankAccount', cast(? as varchar)),
				    source_snapshot = jsonb_build_object('sourceType', cast(? as varchar), 'sourceId', ?, 'sourceNo',
					    cast(? as varchar)),
				    remark = cast(? as varchar), updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", source.customerId(), source.ownershipType(), source.projectId(), SALES_SHIPMENT, source.id(),
				source.shipmentNo(), request.invoiceDate(), dueDate, blankToNull(request.externalInvoiceNo()),
				invoiceType(request.invoiceType(), party.invoiceType()), amounts.taxExcludedAmount(), amounts.taxAmount(),
				amounts.taxIncludedAmount(), party.invoiceTitle(), party.taxNo(), party.bankName(), party.bankAccount(),
				SALES_SHIPMENT, source.id(), source.shipmentNo(), blankToNull(request.remark()), currentUser.username(),
				now, id);
		insertSalesInvoiceLines(id, source, lines, now);
		this.auditService.record(currentUser, "FINANCE_SALES_INVOICE_UPDATE", "FINANCE_SALES_INVOICE", id,
				invoice.documentNo(), servletRequest);
		recordAction(action, "FINANCE_SALES_INVOICE", id, invoice.version() + 1, currentUser);
		return salesInvoice(id, currentUser);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> salesInvoice(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:sales-invoice:view");
		InvoiceRow row = salesInvoiceRow(id).orElseThrow(this::invoiceNotFound);
		Map<String, Object> response = invoiceMap(row);
		response.put("linkedReceivableId", row.linkedReceivableId());
		response.put("partySettlementSnapshot", partySnapshot(row.partyId(), true,
				hasPermission(currentUser, "finance:settlement-sensitive:view")));
		response.put("restrictedReasons", restrictedReasons(currentUser));
		response.put("lines", salesInvoiceLines(id));
		response.put("allowedActions", allowedInvoiceActions(row.status(), currentUser, "finance:sales-invoice"));
		return response;
	}

	@Transactional
	public Map<String, Object> confirmSalesInvoice(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:sales-invoice:confirm");
		ActionRequestContext action = actionRequest(currentUser, "CONFIRM", "FINANCE_SALES_INVOICE", id, request);
		if (action.resultResourceId() != null) {
			return salesInvoice(action.resultResourceId(), currentUser);
		}
		InvoiceRow invoice = lockSalesInvoice(id).orElseThrow(this::invoiceNotFound);
		assertVersion(action.version(), invoice.version());
		if (!"DRAFT".equals(invoice.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(invoice.businessDate(), BusinessPeriodOperation.CONFIRM,
				"FINANCE_SALES_INVOICE", id);
		ShipmentSource source = lockShipment(invoice.sourceId()).orElseThrow(this::sourceNotFound);
		if (!"POSTED".equals(source.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		Long receivableId = existingReceivableForShipment(invoice.sourceId()).orElseGet(() -> createReceivable(invoice,
				source, currentUser.username()));
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_sales_invoice
				set status = 'CONFIRMED', linked_receivable_id = ?, confirmed_by = ?, confirmed_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", receivableId, currentUser.username(), now, currentUser.username(), now, id);
		this.jdbcTemplate.update("""
				insert into fin_sales_invoice_receivable_link (
					sales_invoice_id, receivable_id, link_mode, created_by, created_at
				)
				values (?, ?, ?, ?, ?)
				on conflict (sales_invoice_id) do nothing
				""", id, receivableId, existingReceivableForShipment(invoice.sourceId()).isPresent() ? "BIND_EXISTING"
				: "GENERATE_NEW", currentUser.username(), now);
		this.auditService.record(currentUser, "FINANCE_SALES_INVOICE_CONFIRM", "FINANCE_SALES_INVOICE", id,
				invoice.documentNo(), servletRequest);
		recordAction(action, "FINANCE_SALES_INVOICE", id, invoice.version() + 1, currentUser);
		return salesInvoice(id, currentUser);
	}

	@Transactional
	public Map<String, Object> cancelSalesInvoice(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:sales-invoice:cancel");
		ActionRequestContext action = actionRequest(currentUser, "CANCEL", "FINANCE_SALES_INVOICE", id, request);
		if (action.resultResourceId() != null) {
			return salesInvoice(action.resultResourceId(), currentUser);
		}
		InvoiceRow invoice = lockSalesInvoice(id).orElseThrow(this::invoiceNotFound);
		assertVersion(action.version(), invoice.version());
		if (!"DRAFT".equals(invoice.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_sales_invoice
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_SALES_INVOICE_CANCEL", "FINANCE_SALES_INVOICE", id,
				invoice.documentNo(), servletRequest);
		recordAction(action, "FINANCE_SALES_INVOICE", id, invoice.version() + 1, currentUser);
		return salesInvoice(id, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> purchaseInvoices(String keyword, String status, CurrentUser currentUser,
			int page, int pageSize) {
		requireUser(currentUser, "finance:purchase-invoice:view");
		return page("fin_purchase_invoice", "invoice_no", keyword, status, page, pageSize,
				(id) -> purchaseInvoice(id, currentUser));
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> purchaseInvoiceCandidates(String keyword, String sourceType,
			Long sourceId, Long supplierId, String ownershipType, Long projectId, CurrentUser currentUser, int page,
			int pageSize) {
		requireUser(currentUser, "finance:purchase-invoice:view");
		List<Map<String, Object>> items = new ArrayList<>();
		if (!hasText(sourceType) || PURCHASE_RECEIPT.equals(sourceType)) {
			items.addAll(purchaseReceiptCandidates(keyword, sourceId, supplierId, ownershipType, projectId));
		}
		if (!hasText(sourceType) || OUTSOURCING_RECEIPT.equals(sourceType)) {
			items.addAll(outsourcingReceiptCandidates(keyword, sourceId, supplierId, ownershipType, projectId));
		}
		items.sort(candidateComparator());
		int safePageSize = limit(pageSize);
		int from = Math.min(offset(page, safePageSize), items.size());
		int to = Math.min(from + safePageSize, items.size());
		return PageResponse.of(items.subList(from, to), page, safePageSize, items.size());
	}

	@Transactional
	public Map<String, Object> createPurchaseInvoice(PurchaseInvoiceCreateRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:purchase-invoice:create");
		if (request == null || request.invoiceDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireMutationMetadata(request.version(), request.idempotencyKey());
		String requestFingerprint = fingerprint("PURCHASE_INVOICE_CREATE", request);
		Optional<Long> idempotent = idempotentDocument("fin_purchase_invoice", currentUser.username(),
				request.idempotencyKey(), requestFingerprint);
		if (idempotent.isPresent()) {
			return purchaseInvoice(idempotent.get(), currentUser);
		}
		String settlementKind = purchaseSettlementKind(request);
		String sourceType = purchaseSourceType(request, settlementKind);
		Long sourceId = purchaseSourceId(request, sourceType);
		LocalDate dueDate = defaultDueDate(request.dueDate(), request.invoiceDate());
		if (sourceId == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PurchaseSource source = purchaseSource(settlementKind, sourceType, sourceId);
		validatePurchaseRequestSource(request, source);
		if (!"POSTED".equals(source.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		List<PurchaseSourceLine> sourceLines = purchaseSourceLines(source);
		List<ValidatedInvoiceLine> requestLines = validateInvoiceLines(purchaseRequestLines(request, source, sourceLines),
				sourceLines, null);
		MatchResult matchResult = matchResult(source.settlementKind(), sourceLines, requestLines);
		DocumentAmounts amounts = invoiceLineAmounts(requestLines);
		this.businessPeriodGuard.assertWritable(request.invoiceDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_PURCHASE_INVOICE", null);
		PartySnapshot party = supplierSnapshot(source.supplierId());
		String invoiceNo = nextNo("PI", PURCHASE_INVOICE_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long invoiceId = this.jdbcTemplate.queryForObject("""
					insert into fin_purchase_invoice (
						invoice_no, supplier_id, settlement_kind, ownership_type, project_id, source_type, source_id,
						source_no, invoice_date, due_date, supplier_invoice_no, invoice_type, match_status,
						tax_excluded_amount, tax_amount, tax_included_amount, status, idempotency_key, request_fingerprint,
						party_snapshot, source_snapshot, remark, created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as varchar), ?, ?, ?, ?, ?, 'DRAFT',
						cast(? as varchar), ?,
						jsonb_build_object('invoiceTitle', cast(? as varchar), 'taxNo', cast(? as varchar),
							'bankName', cast(? as varchar), 'bankAccount', cast(? as varchar)),
						jsonb_build_object('sourceType', cast(? as varchar), 'sourceId', ?, 'sourceNo', cast(? as varchar)),
						cast(? as varchar), ?, ?, ?, ?)
					returning id
					""", Long.class, invoiceNo, source.supplierId(), source.settlementKind(), source.ownershipType(),
					source.projectId(), source.sourceType(), source.sourceId(), source.sourceNo(),
					request.invoiceDate(), dueDate, blankToNull(purchaseExternalInvoiceNo(request)),
					invoiceType(request.invoiceType(), party.invoiceType()), matchResult.status(),
					amounts.taxExcludedAmount(), amounts.taxAmount(), amounts.taxIncludedAmount(),
					blankToNull(request.idempotencyKey()), requestFingerprint, party.invoiceTitle(), party.taxNo(),
					party.bankName(), party.bankAccount(), source.sourceType(), source.sourceId(), source.sourceNo(),
					blankToNull(request.remark()), currentUser.username(), now, currentUser.username(), now);
			insertPurchaseInvoiceLines(invoiceId, source, sourceLines, requestLines, matchResult, now);
			insertMatchDifferences(invoiceId, matchResult.differences(), now);
			this.auditService.record(currentUser, "FINANCE_PURCHASE_INVOICE_CREATE", "FINANCE_PURCHASE_INVOICE",
					invoiceId, invoiceNo, servletRequest);
			return purchaseInvoice(invoiceId, currentUser);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateFinanceException(exception);
		}
	}

	@Transactional
	public Map<String, Object> updatePurchaseInvoice(Long id, PurchaseInvoiceCreateRequest request,
			CurrentUser currentUser, HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:purchase-invoice:update");
		if (request == null || request.invoiceDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ActionRequestContext action = mutationRequest(currentUser, "UPDATE", "FINANCE_PURCHASE_INVOICE", id,
				request.version(), request.idempotencyKey(), fingerprint("PURCHASE_INVOICE_UPDATE", id, request));
		if (action.resultResourceId() != null) {
			return purchaseInvoice(action.resultResourceId(), currentUser);
		}
		InvoiceRow invoice = lockPurchaseInvoice(id).orElseThrow(this::invoiceNotFound);
		assertVersion(action.version(), invoice.version());
		if (!"DRAFT".equals(invoice.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		String settlementKind = purchaseSettlementKind(request);
		String sourceType = purchaseSourceType(request, settlementKind);
		Long sourceId = purchaseSourceId(request, sourceType);
		LocalDate dueDate = defaultDueDate(request.dueDate(), request.invoiceDate());
		if (sourceId == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PurchaseSource source = purchaseSource(settlementKind, sourceType, sourceId);
		validatePurchaseRequestSource(request, source);
		if (!"POSTED".equals(source.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_STATUS_INVALID);
		}
		List<PurchaseSourceLine> sourceLines = purchaseSourceLines(source);
		List<ValidatedInvoiceLine> requestLines = validateInvoiceLines(purchaseRequestLines(request, source, sourceLines),
				sourceLines, id);
		MatchResult matchResult = matchResult(source.settlementKind(), sourceLines, requestLines);
		DocumentAmounts amounts = invoiceLineAmounts(requestLines);
		this.businessPeriodGuard.assertWritable(request.invoiceDate(), BusinessPeriodOperation.UPDATE,
				"FINANCE_PURCHASE_INVOICE", id);
		PartySnapshot party = supplierSnapshot(source.supplierId());
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("delete from fin_purchase_invoice_match_difference where purchase_invoice_id = ?", id);
		this.jdbcTemplate.update("delete from fin_purchase_invoice_line where purchase_invoice_id = ?", id);
		this.jdbcTemplate.update("""
				update fin_purchase_invoice
				set supplier_id = ?, settlement_kind = ?, ownership_type = ?, project_id = ?, source_type = ?,
				    source_id = ?, source_no = ?, invoice_date = ?, due_date = ?, supplier_invoice_no = cast(? as varchar),
				    invoice_type = ?, match_status = ?, tax_excluded_amount = ?, tax_amount = ?,
				    tax_included_amount = ?,
				    party_snapshot = jsonb_build_object('invoiceTitle', cast(? as varchar), 'taxNo', cast(? as varchar),
					    'bankName', cast(? as varchar), 'bankAccount', cast(? as varchar)),
				    source_snapshot = jsonb_build_object('sourceType', cast(? as varchar), 'sourceId', ?, 'sourceNo',
					    cast(? as varchar)),
				    remark = cast(? as varchar), matched_by = null, matched_at = null, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", source.supplierId(), source.settlementKind(), source.ownershipType(), source.projectId(),
				source.sourceType(), source.sourceId(), source.sourceNo(), request.invoiceDate(), dueDate,
				blankToNull(purchaseExternalInvoiceNo(request)), invoiceType(request.invoiceType(), party.invoiceType()),
				matchResult.status(), amounts.taxExcludedAmount(), amounts.taxAmount(), amounts.taxIncludedAmount(),
				party.invoiceTitle(), party.taxNo(), party.bankName(), party.bankAccount(), source.sourceType(),
				source.sourceId(), source.sourceNo(), blankToNull(request.remark()), currentUser.username(), now, id);
		insertPurchaseInvoiceLines(id, source, sourceLines, requestLines, matchResult, now);
		insertMatchDifferences(id, matchResult.differences(), now);
		this.auditService.record(currentUser, "FINANCE_PURCHASE_INVOICE_UPDATE", "FINANCE_PURCHASE_INVOICE", id,
				invoice.documentNo(), servletRequest);
		recordAction(action, "FINANCE_PURCHASE_INVOICE", id, invoice.version() + 1, currentUser);
		return purchaseInvoice(id, currentUser);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> purchaseInvoice(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:purchase-invoice:view");
		InvoiceRow row = purchaseInvoiceRow(id).orElseThrow(this::invoiceNotFound);
		Map<String, Object> response = invoiceMap(row);
		response.put("settlementKind", row.settlementKind());
		response.put("matchStatus", row.matchStatus());
		response.put("linkedPayableId", row.linkedPayableId());
		response.put("partySettlementSnapshot", partySnapshot(row.partyId(), false,
				hasPermission(currentUser, "finance:settlement-sensitive:view")));
		response.put("restrictedReasons", restrictedReasons(currentUser));
		response.put("lines", purchaseInvoiceLines(id));
		response.put("matchDifferences", matchDifferences(id));
		response.put("allowedActions", allowedInvoiceActions(row.status(), currentUser, "finance:purchase-invoice"));
		return response;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> purchaseInvoiceMatching(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:purchase-invoice:view");
		InvoiceRow row = purchaseInvoiceRow(id).orElseThrow(this::invoiceNotFound);
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("status", row.matchStatus());
		response.put("differences", matchDifferences(id));
		return response;
	}

	@Transactional
	public Map<String, Object> matchPurchaseInvoice(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:purchase-invoice:match");
		ActionRequestContext action = actionRequest(currentUser, "MATCH", "FINANCE_PURCHASE_INVOICE", id, request);
		if (action.resultResourceId() != null) {
			return purchaseInvoice(action.resultResourceId(), currentUser);
		}
		InvoiceRow invoice = lockPurchaseInvoice(id).orElseThrow(this::invoiceNotFound);
		assertVersion(action.version(), invoice.version());
		if (!"DRAFT".equals(invoice.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		if ("OUTSOURCING".equals(invoice.settlementKind())) {
			recordAction(action, "FINANCE_PURCHASE_INVOICE", id, invoice.version(), currentUser);
			return purchaseInvoice(id, currentUser);
		}
		rematchPurchaseInvoice(id, invoice, currentUser.username());
		this.auditService.record(currentUser, "FINANCE_PURCHASE_INVOICE_MATCH", "FINANCE_PURCHASE_INVOICE", id,
				invoice.documentNo(), servletRequest);
		recordAction(action, "FINANCE_PURCHASE_INVOICE", id, invoice.version() + 1, currentUser);
		return purchaseInvoice(id, currentUser);
	}

	@Transactional
	public Map<String, Object> confirmPurchaseInvoice(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:purchase-invoice:confirm");
		ActionRequestContext action = actionRequest(currentUser, "CONFIRM", "FINANCE_PURCHASE_INVOICE", id, request);
		if (action.resultResourceId() != null) {
			return purchaseInvoice(action.resultResourceId(), currentUser);
		}
		InvoiceRow invoice = lockPurchaseInvoice(id).orElseThrow(this::invoiceNotFound);
		assertVersion(action.version(), invoice.version());
		if (!"DRAFT".equals(invoice.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		if ("STANDARD_PURCHASE".equals(invoice.settlementKind()) && !"MATCHED".equals(invoice.matchStatus())) {
			if ("UNMATCHED".equals(invoice.matchStatus())) {
				rematchPurchaseInvoice(id, invoice, currentUser.username());
				invoice = lockPurchaseInvoice(id).orElseThrow(this::invoiceNotFound);
			}
			if (!"MATCHED".equals(invoice.matchStatus())) {
				throw new BusinessException(ApiErrorCode.FINANCE_MATCH_EXCEPTION);
			}
		}
		this.businessPeriodGuard.assertWritable(invoice.businessDate(), BusinessPeriodOperation.CONFIRM,
				"FINANCE_PURCHASE_INVOICE", id);
		boolean boundExisting = false;
		Long payableId;
		if ("STANDARD_PURCHASE".equals(invoice.settlementKind())) {
			Optional<Long> existingPayable = existingPayable(PURCHASE_RECEIPT, invoice.sourceId());
			boundExisting = existingPayable.isPresent();
			if (existingPayable.isPresent()) {
				payableId = existingPayable.get();
			}
			else {
				payableId = createStandardPayable(invoice, currentUser.username());
			}
		}
		else {
			payableId = createStandalonePayable(invoice, OUTSOURCING_SETTLEMENT, invoice.id(), invoice.documentNo(),
					currentUser.username());
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_purchase_invoice
				set status = 'CONFIRMED', linked_payable_id = ?, confirmed_by = ?, confirmed_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", payableId, currentUser.username(), now, currentUser.username(), now, id);
		this.jdbcTemplate.update("""
				insert into fin_purchase_invoice_payable_link (
					purchase_invoice_id, payable_id, link_mode, created_by, created_at
				)
				values (?, ?, ?, ?, ?)
				on conflict (purchase_invoice_id) do nothing
				""", id, payableId, boundExisting ? "BIND_EXISTING" : "GENERATE_NEW", currentUser.username(), now);
		this.auditService.record(currentUser, "FINANCE_PURCHASE_INVOICE_CONFIRM", "FINANCE_PURCHASE_INVOICE", id,
				invoice.documentNo(), servletRequest);
		recordAction(action, "FINANCE_PURCHASE_INVOICE", id, invoice.version() + 1, currentUser);
		return purchaseInvoice(id, currentUser);
	}

	@Transactional
	public Map<String, Object> cancelPurchaseInvoice(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:purchase-invoice:cancel");
		ActionRequestContext action = actionRequest(currentUser, "CANCEL", "FINANCE_PURCHASE_INVOICE", id, request);
		if (action.resultResourceId() != null) {
			return purchaseInvoice(action.resultResourceId(), currentUser);
		}
		InvoiceRow invoice = lockPurchaseInvoice(id).orElseThrow(this::invoiceNotFound);
		assertVersion(action.version(), invoice.version());
		if (!"DRAFT".equals(invoice.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_purchase_invoice
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_PURCHASE_INVOICE_CANCEL", "FINANCE_PURCHASE_INVOICE", id,
				invoice.documentNo(), servletRequest);
		recordAction(action, "FINANCE_PURCHASE_INVOICE", id, invoice.version() + 1, currentUser);
		return purchaseInvoice(id, currentUser);
	}

	private void rematchPurchaseInvoice(Long id, InvoiceRow invoice, String operator) {
		PurchaseSource source = purchaseSource(invoice.settlementKind(), invoice.sourceType(), invoice.sourceId());
		List<PurchaseSourceLine> sourceLines = purchaseSourceLines(source);
		List<ValidatedInvoiceLine> invoiceLines = purchaseInvoiceLineRows(id);
		MatchResult result = matchResult(source.settlementKind(), sourceLines, invoiceLines);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("delete from fin_purchase_invoice_match_difference where purchase_invoice_id = ?", id);
		insertMatchDifferences(id, result.differences(), now);
		this.jdbcTemplate.update("""
				update fin_purchase_invoice
				set match_status = ?, matched_by = ?, matched_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", result.status(), operator, now, operator, now, id);
		this.jdbcTemplate.update("update fin_purchase_invoice_line set match_status = ? where purchase_invoice_id = ?",
				result.status(), id);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> expenses(String keyword, String status, CurrentUser currentUser, int page,
			int pageSize) {
		requireUser(currentUser, "finance:expense:view");
		return page("fin_expense", "expense_no", keyword, status, page, pageSize, (id) -> expense(id, currentUser));
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> expenseCategories(String keyword, CurrentUser currentUser, int page,
			int pageSize) {
		requireUser(currentUser, "finance:expense:view");
		List<Map<String, Object>> categories = List.of(categoryMap(1L, "SERVICE", "服务费"),
				categoryMap(2L, "FREIGHT", "运费"), categoryMap(3L, "OUTSOURCING_SERVICE", "外协服务费"),
				categoryMap(4L, "OTHER", "其他费用"));
		String filter = hasText(keyword) ? keyword.trim() : null;
		List<Map<String, Object>> items = categories.stream()
			.filter((category) -> filter == null || category.get("name").toString().contains(filter)
					|| category.get("code").toString().contains(filter))
			.skip(offset(page, pageSize))
			.limit(limit(pageSize))
			.toList();
		return PageResponse.of(items, page, limit(pageSize), categories.size());
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> expenseSourceCandidates(String keyword, String sourceType,
			CurrentUser currentUser, int page, int pageSize) {
		requireUser(currentUser, "finance:expense:view");
		List<Map<String, Object>> items = new ArrayList<>();
		if (!hasText(sourceType) || PURCHASE_RECEIPT.equals(sourceType)) {
			items.addAll(purchaseReceiptCandidates(keyword, null, null, null, null));
		}
		if (!hasText(sourceType) || OUTSOURCING_RECEIPT.equals(sourceType)) {
			items.addAll(outsourcingReceiptCandidates(keyword, null, null, null, null));
		}
		items.sort(candidateComparator());
		int safePageSize = limit(pageSize);
		int from = Math.min(offset(page, safePageSize), items.size());
		int to = Math.min(from + safePageSize, items.size());
		return PageResponse.of(items.subList(from, to), page, safePageSize, items.size());
	}

	@Transactional
	public Map<String, Object> createExpense(ExpenseCreateRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:expense:create");
		if (request == null || request.supplierId() == null || expenseBusinessDate(request) == null
				|| request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireMutationMetadata(request.version(), request.idempotencyKey());
		String requestFingerprint = fingerprint("EXPENSE_CREATE", request);
		Optional<Long> idempotent = idempotentDocument("fin_expense", currentUser.username(), request.idempotencyKey(),
				requestFingerprint);
		if (idempotent.isPresent()) {
			return expense(idempotent.get(), currentUser);
		}
		SupplierRow supplier = supplier(request.supplierId()).orElseThrow(this::sourceNotFound);
		validateOwnership(request.ownershipType(), request.projectId(), true);
		List<ValidatedExpenseLine> lines = validateExpenseLines(request.lines(), supplier.id(),
				ownershipType(request.ownershipType()), request.projectId());
		DocumentAmounts amounts = expenseAmounts(lines);
		LocalDate businessDate = expenseBusinessDate(request);
		LocalDate dueDate = defaultDueDate(request.dueDate(), businessDate);
		this.businessPeriodGuard.assertWritable(businessDate, BusinessPeriodOperation.CREATE, "FINANCE_EXPENSE", null);
		PartySnapshot party = supplierSnapshot(supplier.id());
		String expenseNo = nextNo("EXP", EXPENSE_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		Long expenseId = this.jdbcTemplate.queryForObject("""
				insert into fin_expense (
					expense_no, supplier_id, ownership_type, project_id, expense_date, due_date, invoice_type,
					tax_excluded_amount, tax_amount, tax_included_amount, status, idempotency_key, request_fingerprint, party_snapshot,
					source_snapshot, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', cast(? as varchar), ?,
					jsonb_build_object('invoiceTitle', cast(? as varchar), 'taxNo', cast(? as varchar),
						'bankName', cast(? as varchar), 'bankAccount', cast(? as varchar)),
					jsonb_build_object('sourceType', 'EXPENSE'), cast(? as varchar), ?, ?, ?, ?)
				returning id
				""", Long.class, expenseNo, supplier.id(), ownershipType(request.ownershipType()),
				request.projectId(), businessDate, dueDate, invoiceType(request.invoiceType(),
						party.invoiceType()), amounts.taxExcludedAmount(), amounts.taxAmount(),
				amounts.taxIncludedAmount(), blankToNull(request.idempotencyKey()), requestFingerprint,
				party.invoiceTitle(),
				party.taxNo(), party.bankName(), party.bankAccount(), blankToNull(request.remark()),
				currentUser.username(), now, currentUser.username(), now);
		insertExpenseLines(expenseId, lines, now);
		this.auditService.record(currentUser, "FINANCE_EXPENSE_CREATE", "FINANCE_EXPENSE", expenseId, expenseNo,
				servletRequest);
		return expense(expenseId, currentUser);
	}

	@Transactional
	public Map<String, Object> updateExpense(Long id, ExpenseCreateRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:expense:update");
		if (request == null || request.supplierId() == null || expenseBusinessDate(request) == null
				|| request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ActionRequestContext action = mutationRequest(currentUser, "UPDATE", "FINANCE_EXPENSE", id, request.version(),
				request.idempotencyKey(), fingerprint("EXPENSE_UPDATE", id, request));
		if (action.resultResourceId() != null) {
			return expense(action.resultResourceId(), currentUser);
		}
		ExpenseRow expense = lockExpense(id).orElseThrow(this::expenseNotFound);
		assertVersion(action.version(), expense.version());
		if (!"DRAFT".equals(expense.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		SupplierRow supplier = supplier(request.supplierId()).orElseThrow(this::sourceNotFound);
		validateOwnership(request.ownershipType(), request.projectId(), true);
		List<ValidatedExpenseLine> lines = validateExpenseLines(request.lines(), supplier.id(),
				ownershipType(request.ownershipType()), request.projectId());
		DocumentAmounts amounts = expenseAmounts(lines);
		LocalDate businessDate = expenseBusinessDate(request);
		LocalDate dueDate = defaultDueDate(request.dueDate(), businessDate);
		this.businessPeriodGuard.assertWritable(businessDate, BusinessPeriodOperation.UPDATE, "FINANCE_EXPENSE", id);
		PartySnapshot party = supplierSnapshot(supplier.id());
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("delete from fin_expense_line where expense_id = ?", id);
		this.jdbcTemplate.update("""
				update fin_expense
				set supplier_id = ?, ownership_type = ?, project_id = ?, expense_date = ?, due_date = ?,
				    invoice_type = ?, tax_excluded_amount = ?, tax_amount = ?, tax_included_amount = ?,
				    party_snapshot = jsonb_build_object('invoiceTitle', cast(? as varchar), 'taxNo', cast(? as varchar),
					    'bankName', cast(? as varchar), 'bankAccount', cast(? as varchar)),
				    source_snapshot = jsonb_build_object('sourceType', 'EXPENSE'), remark = cast(? as varchar),
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", supplier.id(), ownershipType(request.ownershipType()), request.projectId(), businessDate, dueDate,
				invoiceType(request.invoiceType(), party.invoiceType()), amounts.taxExcludedAmount(), amounts.taxAmount(),
				amounts.taxIncludedAmount(), party.invoiceTitle(), party.taxNo(), party.bankName(), party.bankAccount(),
				blankToNull(request.remark()), currentUser.username(), now, id);
		insertExpenseLines(id, lines, now);
		this.auditService.record(currentUser, "FINANCE_EXPENSE_UPDATE", "FINANCE_EXPENSE", id, expense.documentNo(),
				servletRequest);
		recordAction(action, "FINANCE_EXPENSE", id, expense.version() + 1, currentUser);
		return expense(id, currentUser);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> expense(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:expense:view");
		ExpenseRow row = expenseRow(id).orElseThrow(this::expenseNotFound);
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put("expenseNo", row.documentNo());
		response.put("supplierId", row.partyId());
		response.put("ownershipType", row.ownershipType());
		response.put("projectId", row.projectId());
		response.put("expenseDate", row.businessDate());
		response.put("dueDate", row.dueDate());
		response.put("invoiceType", row.invoiceType());
		response.put("taxExcludedAmount", decimalString(row.taxExcludedAmount()));
		response.put("taxAmount", decimalString(row.taxAmount()));
		response.put("taxIncludedAmount", decimalString(row.taxIncludedAmount()));
		response.put("pretaxAmount", decimalString(row.taxExcludedAmount()));
		response.put("totalAmount", decimalString(row.taxIncludedAmount()));
		response.put("settlementStatus", row.linkedPayableId() == null ? "UNLINKED" : "UNSETTLED");
		response.put("unsettledAmount", decimalString(row.taxIncludedAmount()));
		response.put("status", row.status());
		response.put("linkedPayableId", row.linkedPayableId());
		response.put("version", row.version());
		response.put("partySettlementSnapshot", partySnapshot(row.partyId(), false,
				hasPermission(currentUser, "finance:settlement-sensitive:view")));
		response.put("restrictedReasons", restrictedReasons(currentUser));
		response.put("lines", expenseLines(id));
		response.put("allowedActions", allowedInvoiceActions(row.status(), currentUser, "finance:expense"));
		return response;
	}

	@Transactional
	public Map<String, Object> confirmExpense(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:expense:confirm");
		ActionRequestContext action = actionRequest(currentUser, "CONFIRM", "FINANCE_EXPENSE", id, request);
		if (action.resultResourceId() != null) {
			return expense(action.resultResourceId(), currentUser);
		}
		ExpenseRow expense = lockExpense(id).orElseThrow(this::expenseNotFound);
		assertVersion(action.version(), expense.version());
		if (!"DRAFT".equals(expense.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(expense.businessDate(), BusinessPeriodOperation.CONFIRM,
				"FINANCE_EXPENSE", id);
		Long payableId = createStandalonePayable(expense, EXPENSE, expense.id(), expense.documentNo(),
				currentUser.username());
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_expense
				set status = 'CONFIRMED', linked_payable_id = ?, confirmed_by = ?, confirmed_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", payableId, currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_EXPENSE_CONFIRM", "FINANCE_EXPENSE", id,
				expense.documentNo(), servletRequest);
		recordAction(action, "FINANCE_EXPENSE", id, expense.version() + 1, currentUser);
		return expense(id, currentUser);
	}

	@Transactional
	public Map<String, Object> cancelExpense(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:expense:cancel");
		ActionRequestContext action = actionRequest(currentUser, "CANCEL", "FINANCE_EXPENSE", id, request);
		if (action.resultResourceId() != null) {
			return expense(action.resultResourceId(), currentUser);
		}
		ExpenseRow expense = lockExpense(id).orElseThrow(this::expenseNotFound);
		assertVersion(action.version(), expense.version());
		if (!"DRAFT".equals(expense.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_expense
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_EXPENSE_CANCEL", "FINANCE_EXPENSE", id, expense.documentNo(),
				servletRequest);
		recordAction(action, "FINANCE_EXPENSE", id, expense.version() + 1, currentUser);
		return expense(id, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> advanceReceipts(String keyword, CurrentUser currentUser, int page,
			int pageSize) {
		requireUser(currentUser, "finance:advance-receipt:view");
		return cashPage("fin_receipt", "receipt_no", true, keyword, currentUser, page, pageSize);
	}

	@Transactional
	public Map<String, Object> createAdvanceReceipt(AdvanceReceiptRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:advance-receipt:create");
		Long customerId = advanceCustomerId(request);
		LocalDate receiptDate = advanceReceiptDate(request);
		if (request == null || customerId == null || receiptDate == null || request.amount() == null
				|| !hasText(request.method())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireMutationMetadata(request.version(), request.idempotencyKey());
		String requestFingerprint = fingerprint("ADVANCE_RECEIPT_CREATE", request);
		Optional<Long> idempotent = idempotentCashDocument("fin_receipt", currentUser.username(),
				request.idempotencyKey(), requestFingerprint);
		if (idempotent.isPresent()) {
			return advanceReceipt(idempotent.get(), currentUser);
		}
		CustomerRow customer = customer(customerId).orElseThrow(this::sourceNotFound);
		validateOwnership(request.ownershipType(), request.projectId(), true);
		BigDecimal amount = validateMoney(request.amount());
		this.businessPeriodGuard.assertWritable(receiptDate, BusinessPeriodOperation.CREATE,
				"FINANCE_ADVANCE_RECEIPT", null);
		String receiptNo = nextNo("ADR", ADVANCE_RECEIPT_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		Long receiptId = this.jdbcTemplate.queryForObject("""
				insert into fin_receipt (
					receipt_no, customer_id, receipt_date, amount, method, status, remark, created_by, created_at,
					updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)
				returning id
				""", Long.class, receiptNo, customer.id(), receiptDate, amount,
				validateOptionalText(request.method(), 32), blankToNull(request.remark()), currentUser.username(), now,
				currentUser.username(), now);
		this.jdbcTemplate.update("""
				insert into fin_receipt_balance (
					receipt_id, customer_id, ownership_type, project_id, original_amount, allocated_amount,
					available_amount, status, updated_at
				)
				values (?, ?, ?, ?, ?, 0, ?, 'DRAFT', ?)
				""", receiptId, customer.id(), ownershipType(request.ownershipType()), request.projectId(), amount,
				amount, now);
		insertCashIdempotency("RECEIPT", receiptId, currentUser.username(), request.idempotencyKey(),
				requestFingerprint);
		this.auditService.record(currentUser, "FINANCE_ADVANCE_RECEIPT_CREATE", "FINANCE_RECEIPT", receiptId,
				receiptNo, servletRequest);
		return advanceReceipt(receiptId, currentUser);
	}

	@Transactional
	public Map<String, Object> updateAdvanceReceipt(Long id, AdvanceReceiptRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:advance-receipt:update");
		Long customerId = advanceCustomerId(request);
		LocalDate receiptDate = advanceReceiptDate(request);
		if (request == null || customerId == null || receiptDate == null || request.amount() == null
				|| !hasText(request.method())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ActionRequestContext action = mutationRequest(currentUser, "UPDATE", "FINANCE_ADVANCE_RECEIPT", id,
				request.version(), request.idempotencyKey(), fingerprint("ADVANCE_RECEIPT_UPDATE", id, request));
		if (action.resultResourceId() != null) {
			return advanceReceipt(action.resultResourceId(), currentUser);
		}
		CashRow row = lockAdvanceReceipt(id).orElseThrow(this::receiptNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		CustomerRow customer = customer(customerId).orElseThrow(this::sourceNotFound);
		validateOwnership(request.ownershipType(), request.projectId(), true);
		BigDecimal amount = validateMoney(request.amount());
		this.businessPeriodGuard.assertWritable(receiptDate, BusinessPeriodOperation.UPDATE,
				"FINANCE_ADVANCE_RECEIPT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receipt
				set customer_id = ?, receipt_date = ?, amount = ?, method = ?, remark = cast(? as varchar),
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", customer.id(), receiptDate, amount, validateOptionalText(request.method(), 32),
				blankToNull(request.remark()), currentUser.username(), now, id);
		this.jdbcTemplate.update("""
				update fin_receipt_balance
				set customer_id = ?, ownership_type = ?, project_id = ?, original_amount = ?, available_amount = ?,
				    updated_at = ?
				where receipt_id = ?
				""", customer.id(), ownershipType(request.ownershipType()), request.projectId(), amount, amount, now,
				id);
		this.auditService.record(currentUser, "FINANCE_ADVANCE_RECEIPT_UPDATE", "FINANCE_RECEIPT", id,
				row.documentNo(), servletRequest);
		recordAction(action, "FINANCE_ADVANCE_RECEIPT", id, row.version() + 1, currentUser);
		return advanceReceipt(id, currentUser);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> advanceReceipt(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:advance-receipt:view");
		CashRow row = advanceReceiptRow(id).orElseThrow(this::receiptNotFound);
		return cashMap(row, true, currentUser);
	}

	@Transactional
	public Map<String, Object> postAdvanceReceipt(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:advance-receipt:post");
		ActionRequestContext action = actionRequest(currentUser, "POST", "FINANCE_RECEIPT", id, request);
		if (action.resultResourceId() != null) {
			return advanceReceipt(action.resultResourceId(), currentUser);
		}
		CashRow row = lockAdvanceReceipt(id).orElseThrow(this::receiptNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(row.businessDate(), BusinessPeriodOperation.POST,
				"FINANCE_ADVANCE_RECEIPT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receipt
				set status = 'POSTED', posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.jdbcTemplate.update("update fin_receipt_balance set status = 'POSTED', updated_at = ? where receipt_id = ?",
				now, id);
		this.auditService.record(currentUser, "FINANCE_ADVANCE_RECEIPT_POST", "FINANCE_RECEIPT", id,
				row.documentNo(), servletRequest);
		recordAction(action, "FINANCE_RECEIPT", id, row.version() + 1, currentUser);
		return advanceReceipt(id, currentUser);
	}

	@Transactional
	public Map<String, Object> cancelAdvanceReceipt(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:advance-receipt:cancel");
		ActionRequestContext action = actionRequest(currentUser, "CANCEL", "FINANCE_RECEIPT", id, request);
		if (action.resultResourceId() != null) {
			return advanceReceipt(action.resultResourceId(), currentUser);
		}
		CashRow row = lockAdvanceReceipt(id).orElseThrow(this::receiptNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_receipt
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.jdbcTemplate.update("update fin_receipt_balance set status = 'CANCELLED', updated_at = ? where receipt_id = ?",
				now, id);
		this.auditService.record(currentUser, "FINANCE_ADVANCE_RECEIPT_CANCEL", "FINANCE_RECEIPT", id,
				row.documentNo(), servletRequest);
		recordAction(action, "FINANCE_RECEIPT", id, row.version() + 1, currentUser);
		return advanceReceipt(id, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> prepayments(String keyword, CurrentUser currentUser, int page,
			int pageSize) {
		requireUser(currentUser, "finance:prepayment:view");
		return cashPage("fin_payment", "payment_no", false, keyword, currentUser, page, pageSize);
	}

	@Transactional
	public Map<String, Object> createPrepayment(PrepaymentRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:prepayment:create");
		Long supplierId = prepaymentSupplierId(request);
		LocalDate paymentDate = prepaymentDate(request);
		if (request == null || supplierId == null || paymentDate == null || request.amount() == null
				|| !hasText(request.method())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireMutationMetadata(request.version(), request.idempotencyKey());
		String requestFingerprint = fingerprint("PREPAYMENT_CREATE", request);
		Optional<Long> idempotent = idempotentCashDocument("fin_payment", currentUser.username(),
				request.idempotencyKey(), requestFingerprint);
		if (idempotent.isPresent()) {
			return prepayment(idempotent.get(), currentUser);
		}
		SupplierRow supplier = supplier(supplierId).orElseThrow(this::sourceNotFound);
		validateOwnership(request.ownershipType(), request.projectId(), true);
		BigDecimal amount = validateMoney(request.amount());
		this.businessPeriodGuard.assertWritable(paymentDate, BusinessPeriodOperation.CREATE, "FINANCE_PREPAYMENT",
				null);
		String paymentNo = nextNo("PRE", PREPAYMENT_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		Long paymentId = this.jdbcTemplate.queryForObject("""
				insert into fin_payment (
					payment_no, supplier_id, payment_date, amount, method, status, remark, created_by, created_at,
					updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)
				returning id
				""", Long.class, paymentNo, supplier.id(), paymentDate, amount,
				validateOptionalText(request.method(), 32), blankToNull(request.remark()), currentUser.username(), now,
				currentUser.username(), now);
		this.jdbcTemplate.update("""
				insert into fin_payment_balance (
					payment_id, supplier_id, ownership_type, project_id, original_amount, allocated_amount,
					available_amount, status, updated_at
				)
				values (?, ?, ?, ?, ?, 0, ?, 'DRAFT', ?)
				""", paymentId, supplier.id(), ownershipType(request.ownershipType()), request.projectId(), amount,
				amount, now);
		insertCashIdempotency("PAYMENT", paymentId, currentUser.username(), request.idempotencyKey(),
				requestFingerprint);
		this.auditService.record(currentUser, "FINANCE_PREPAYMENT_CREATE", "FINANCE_PAYMENT", paymentId, paymentNo,
				servletRequest);
		return prepayment(paymentId, currentUser);
	}

	@Transactional
	public Map<String, Object> updatePrepayment(Long id, PrepaymentRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:prepayment:update");
		Long supplierId = prepaymentSupplierId(request);
		LocalDate paymentDate = prepaymentDate(request);
		if (request == null || supplierId == null || paymentDate == null || request.amount() == null
				|| !hasText(request.method())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ActionRequestContext action = mutationRequest(currentUser, "UPDATE", "FINANCE_PREPAYMENT", id,
				request.version(), request.idempotencyKey(), fingerprint("PREPAYMENT_UPDATE", id, request));
		if (action.resultResourceId() != null) {
			return prepayment(action.resultResourceId(), currentUser);
		}
		CashRow row = lockPrepayment(id).orElseThrow(this::paymentNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		SupplierRow supplier = supplier(supplierId).orElseThrow(this::sourceNotFound);
		validateOwnership(request.ownershipType(), request.projectId(), true);
		BigDecimal amount = validateMoney(request.amount());
		this.businessPeriodGuard.assertWritable(paymentDate, BusinessPeriodOperation.UPDATE, "FINANCE_PREPAYMENT", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payment
				set supplier_id = ?, payment_date = ?, amount = ?, method = ?, remark = cast(? as varchar),
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", supplier.id(), paymentDate, amount, validateOptionalText(request.method(), 32),
				blankToNull(request.remark()), currentUser.username(), now, id);
		this.jdbcTemplate.update("""
				update fin_payment_balance
				set supplier_id = ?, ownership_type = ?, project_id = ?, original_amount = ?, available_amount = ?,
				    updated_at = ?
				where payment_id = ?
				""", supplier.id(), ownershipType(request.ownershipType()), request.projectId(), amount, amount, now,
				id);
		this.auditService.record(currentUser, "FINANCE_PREPAYMENT_UPDATE", "FINANCE_PAYMENT", id, row.documentNo(),
				servletRequest);
		recordAction(action, "FINANCE_PREPAYMENT", id, row.version() + 1, currentUser);
		return prepayment(id, currentUser);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> prepayment(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:prepayment:view");
		CashRow row = prepaymentRow(id).orElseThrow(this::paymentNotFound);
		return cashMap(row, false, currentUser);
	}

	@Transactional
	public Map<String, Object> postPrepayment(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:prepayment:post");
		ActionRequestContext action = actionRequest(currentUser, "POST", "FINANCE_PAYMENT", id, request);
		if (action.resultResourceId() != null) {
			return prepayment(action.resultResourceId(), currentUser);
		}
		CashRow row = lockPrepayment(id).orElseThrow(this::paymentNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(row.businessDate(), BusinessPeriodOperation.POST, "FINANCE_PREPAYMENT",
				id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payment
				set status = 'POSTED', posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.jdbcTemplate.update("update fin_payment_balance set status = 'POSTED', updated_at = ? where payment_id = ?",
				now, id);
		this.auditService.record(currentUser, "FINANCE_PREPAYMENT_POST", "FINANCE_PAYMENT", id, row.documentNo(),
				servletRequest);
		recordAction(action, "FINANCE_PAYMENT", id, row.version() + 1, currentUser);
		return prepayment(id, currentUser);
	}

	@Transactional
	public Map<String, Object> cancelPrepayment(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:prepayment:cancel");
		ActionRequestContext action = actionRequest(currentUser, "CANCEL", "FINANCE_PAYMENT", id, request);
		if (action.resultResourceId() != null) {
			return prepayment(action.resultResourceId(), currentUser);
		}
		CashRow row = lockPrepayment(id).orElseThrow(this::paymentNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_payment
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.jdbcTemplate.update("update fin_payment_balance set status = 'CANCELLED', updated_at = ? where payment_id = ?",
				now, id);
		this.auditService.record(currentUser, "FINANCE_PREPAYMENT_CANCEL", "FINANCE_PAYMENT", id, row.documentNo(),
				servletRequest);
		recordAction(action, "FINANCE_PAYMENT", id, row.version() + 1, currentUser);
		return prepayment(id, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> settlementFunds(String direction, Long partnerId, CurrentUser currentUser,
			int page, int pageSize) {
		requireUser(currentUser, "finance:settlement-allocation:view");
		if ("CUSTOMER".equals(direction)) {
			return receiptFunds(partnerId, currentUser, page, pageSize);
		}
		if ("SUPPLIER".equals(direction)) {
			return paymentFunds(partnerId, currentUser, page, pageSize);
		}
		throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> settlementTargets(String direction, Long partnerId,
			CurrentUser currentUser, int page, int pageSize) {
		requireUser(currentUser, "finance:settlement-allocation:view");
		if ("CUSTOMER".equals(direction)) {
			return receivableTargets(partnerId, page, pageSize);
		}
		if ("SUPPLIER".equals(direction)) {
			return payableTargets(partnerId, page, pageSize);
		}
		throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
	}

	@Transactional
	public Map<String, Object> createSettlementAllocation(SettlementAllocationRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:settlement-allocation:create");
		request = normalizeSettlementRequest(request);
		if (request == null || request.cashSourceId() == null || request.businessDate() == null || request.lines() == null
				|| request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireMutationMetadata(request.version(), request.idempotencyKey());
		String requestFingerprint = fingerprint("SETTLEMENT_ALLOCATION_CREATE", request);
		Optional<Long> idempotent = idempotentDocument("fin_settlement_allocation", currentUser.username(),
				request.idempotencyKey(), requestFingerprint);
		if (idempotent.isPresent()) {
			return settlementAllocation(idempotent.get(), currentUser);
		}
		SettlementDraft draft = validateSettlementDraft(request, true);
		this.businessPeriodGuard.assertWritable(request.businessDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_SETTLEMENT_ALLOCATION", null);
		String allocationNo = nextNo("ALC", ALLOCATION_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		Long allocationId = this.jdbcTemplate.queryForObject("""
				insert into fin_settlement_allocation (
					allocation_no, settlement_side, cash_source_type, cash_source_id, party_id, ownership_type,
					project_id, business_date, total_amount, status, idempotency_key, request_fingerprint, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, allocationNo, draft.settlementSide(), draft.cashSourceType(), draft.cashSourceId(),
				draft.partyId(), draft.ownershipType(), draft.projectId(), request.businessDate(),
				draft.totalAmount(), blankToNull(request.idempotencyKey()), requestFingerprint,
				blankToNull(request.remark()), currentUser.username(), now, currentUser.username(), now);
		int lineNo = 1;
		for (SettlementLine line : draft.lines()) {
			this.jdbcTemplate.update("""
					insert into fin_settlement_allocation_line (
						allocation_id, line_no, target_type, target_id, amount, created_at
					)
					values (?, ?, ?, ?, ?, ?)
					""", allocationId, lineNo++, line.targetType(), line.targetId(), line.amount(), now);
		}
		this.auditService.record(currentUser, "FINANCE_SETTLEMENT_ALLOCATION_CREATE",
				"FINANCE_SETTLEMENT_ALLOCATION", allocationId, allocationNo, servletRequest);
		return settlementAllocation(allocationId, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> settlementAllocations(String keyword, String direction, String status,
			Long partnerId, String ownershipType, Long projectId, CurrentUser currentUser, int page, int pageSize) {
		requireUser(currentUser, "finance:settlement-allocation:view");
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(direction)) {
			conditions.add("a.settlement_side = ?");
			args.add(settlementSide(direction));
		}
		if (hasText(status)) {
			conditions.add("a.status = ?");
			args.add(status);
		}
		if (partnerId != null) {
			conditions.add("a.party_id = ?");
			args.add(partnerId);
		}
		if (hasText(ownershipType)) {
			conditions.add("a.ownership_type = ?");
			args.add(ownershipType(ownershipType));
		}
		if (projectId != null) {
			conditions.add("a.project_id = ?");
			args.add(projectId);
		}
		if (hasText(keyword)) {
			conditions.add("""
					(a.allocation_no ilike ? or r.receipt_no ilike ? or p.payment_no ilike ?
					 or c.name ilike ? or s.name ilike ?)
					""");
			String like = "%" + keyword + "%";
			for (int i = 0; i < 5; i++) {
				args.add(like);
			}
		}
		String from = """
				from fin_settlement_allocation a
				left join fin_receipt r on a.cash_source_type = 'RECEIPT' and r.id = a.cash_source_id
				left join fin_payment p on a.cash_source_type = 'PAYMENT' and p.id = a.cash_source_id
				left join mst_customer c on a.settlement_side = 'RECEIVABLE' and c.id = a.party_id
				left join mst_supplier s on a.settlement_side = 'PAYABLE' and s.id = a.party_id
				""";
		String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) " + from + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Long> ids = this.jdbcTemplate.queryForList("select a.id " + from + where
				+ " order by a.business_date desc, a.id desc limit ? offset ?", Long.class, pageArgs.toArray());
		List<Map<String, Object>> items = ids.stream().map((id) -> settlementAllocation(id, currentUser)).toList();
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> settlementAllocation(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:settlement-allocation:view");
		SettlementAllocationRow row = settlementAllocationRow(id).orElseThrow(this::allocationNotFound);
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put("allocationNo", row.documentNo());
		response.put("settlementSide", row.settlementSide());
		response.put("direction", settlementDirection(row.settlementSide()));
		response.put("cashSourceType", row.cashSourceType());
		response.put("cashSourceId", row.cashSourceId());
		response.put("fundNo", cashSourceNo(row.cashSourceType(), row.cashSourceId()));
		response.put("partyId", row.partyId());
		response.put("partnerName", settlementPartyName(row.settlementSide(), row.partyId()));
		response.put("ownershipType", row.ownershipType());
		response.put("projectId", row.projectId());
		response.put("projectName", projectName(row.projectId()));
		response.put("businessDate", row.businessDate());
		response.put("totalAmount", decimalString(row.totalAmount()));
		response.put("amount", decimalString(row.totalAmount()));
		response.put("summary", settlementSummary(row));
		response.put("status", row.status());
		response.put("version", row.version());
		response.put("allowedActions", allowedSettlementActions(row.status(), currentUser));
		response.put("restrictedReasons", settlementRestrictedReasons(row.status()));
		response.put("lines", settlementAllocationLines(id));
		return response;
	}

	@Transactional
	public Map<String, Object> postSettlementAllocation(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:settlement-allocation:post");
		ActionRequestContext action = actionRequest(currentUser, "POST", "FINANCE_SETTLEMENT_ALLOCATION", id, request);
		if (action.resultResourceId() != null) {
			return settlementAllocation(action.resultResourceId(), currentUser);
		}
		SettlementAllocationRow row = lockSettlementAllocation(id).orElseThrow(this::allocationNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		this.businessPeriodGuard.assertWritable(row.businessDate(), BusinessPeriodOperation.POST,
				"FINANCE_SETTLEMENT_ALLOCATION", id);
		List<SettlementLine> lines = settlementLineRows(id);
		CashBalance balance = lockCashBalance(row.settlementSide(), row.cashSourceId());
		if (!"POSTED".equals(balance.status()) || row.totalAmount().compareTo(balance.availableAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_SETTLEMENT_BALANCE_INSUFFICIENT);
		}
		OffsetDateTime now = OffsetDateTime.now();
		for (SettlementLine line : lines) {
			if ("RECEIVABLE".equals(row.settlementSide())) {
				ReceivableBalance target = lockReceivableBalance(line.targetId()).orElseThrow(this::receivableNotFound);
				if (!target.customerId().equals(row.partyId()) || !target.ownershipType().equals(row.ownershipType())
						|| !sameProject(target.projectId(), row.projectId())
						|| line.amount().compareTo(target.openAmount()) > 0) {
					throw new BusinessException(ApiErrorCode.FINANCE_CROSS_PARTY_OR_PROJECT);
				}
				this.jdbcTemplate.update("""
						insert into fin_receipt_allocation (receipt_id, receivable_id, allocated_amount)
						values (?, ?, ?)
						""", row.cashSourceId(), line.targetId(), line.amount());
				BigDecimal received = money(target.settledAmount().add(line.amount()));
				BigDecimal open = money(target.totalAmount().subtract(target.adjustedAmount()).subtract(received));
				String status = open.compareTo(ZERO) == 0 ? "RECEIVED" : "PARTIALLY_RECEIVED";
				this.jdbcTemplate.update("""
						update fin_receivable
						set received_amount = ?, unreceived_amount = ?, status = ?, updated_by = ?, updated_at = ?,
						    version = version + 1
						where id = ?
						""", received, open, status, currentUser.username(), now, target.id());
			}
			else {
				PayableBalance target = lockPayableBalance(line.targetId()).orElseThrow(this::payableNotFound);
				if (!target.supplierId().equals(row.partyId()) || !target.ownershipType().equals(row.ownershipType())
						|| !sameProject(target.projectId(), row.projectId())
						|| line.amount().compareTo(target.openAmount()) > 0) {
					throw new BusinessException(ApiErrorCode.FINANCE_CROSS_PARTY_OR_PROJECT);
				}
				this.jdbcTemplate.update("""
						insert into fin_payment_allocation (payment_id, payable_id, allocated_amount)
						values (?, ?, ?)
						""", row.cashSourceId(), line.targetId(), line.amount());
				BigDecimal paid = money(target.settledAmount().add(line.amount()));
				BigDecimal open = money(target.totalAmount().subtract(target.adjustedAmount()).subtract(paid));
				String status = open.compareTo(ZERO) == 0 ? "PAID" : "PARTIALLY_PAID";
				this.jdbcTemplate.update("""
						update fin_payable
						set paid_amount = ?, unpaid_amount = ?, status = ?, updated_by = ?, updated_at = ?,
						    version = version + 1
						where id = ?
						""", paid, open, status, currentUser.username(), now, target.id());
			}
		}
		BigDecimal allocated = money(balance.allocatedAmount().add(row.totalAmount()));
		BigDecimal available = money(balance.originalAmount().subtract(allocated));
		String balanceTable = "RECEIVABLE".equals(row.settlementSide()) ? "fin_receipt_balance" : "fin_payment_balance";
		String idColumn = "RECEIVABLE".equals(row.settlementSide()) ? "receipt_id" : "payment_id";
		this.jdbcTemplate.update("update " + balanceTable
				+ " set allocated_amount = ?, available_amount = ?, updated_at = ? where " + idColumn + " = ?",
				allocated, available, now, row.cashSourceId());
		this.jdbcTemplate.update("""
				update fin_settlement_allocation
				set status = 'POSTED', posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_SETTLEMENT_ALLOCATION_POST",
				"FINANCE_SETTLEMENT_ALLOCATION", id, row.documentNo(), servletRequest);
		recordAction(action, "FINANCE_SETTLEMENT_ALLOCATION", id, row.version() + 1, currentUser);
		return settlementAllocation(id, currentUser);
	}

	@Transactional
	public Map<String, Object> cancelSettlementAllocation(Long id, FinanceActionRequest request,
			CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:settlement-allocation:cancel");
		ActionRequestContext action = actionRequest(currentUser, "CANCEL", "FINANCE_SETTLEMENT_ALLOCATION", id, request);
		if (action.resultResourceId() != null) {
			return settlementAllocation(action.resultResourceId(), currentUser);
		}
		SettlementAllocationRow row = lockSettlementAllocation(id).orElseThrow(this::allocationNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_settlement_allocation
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_SETTLEMENT_ALLOCATION_CANCEL",
				"FINANCE_SETTLEMENT_ALLOCATION", id, row.documentNo(), servletRequest);
		recordAction(action, "FINANCE_SETTLEMENT_ALLOCATION", id, row.version() + 1, currentUser);
		return settlementAllocation(id, currentUser);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> voucherDrafts(String status, String sourceType, Long sourceId,
			String keyword, CurrentUser currentUser, int page, int pageSize) {
		requireUser(currentUser, "finance:voucher-draft:view");
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(status)) {
			conditions.add("v.status = ?");
			args.add(status);
		}
		if (hasText(sourceType)) {
			conditions.add("v.source_type = ?");
			args.add(sourceType);
		}
		if (sourceId != null) {
			conditions.add("v.source_id = ?");
			args.add(sourceId);
		}
		if (hasText(keyword)) {
			conditions.add("""
					(v.draft_no ilike ? or v.summary ilike ? or v.party_name ilike ?
					 or si.invoice_no ilike ? or pi.invoice_no ilike ? or e.expense_no ilike ?
					 or r.receipt_no ilike ? or p.payment_no ilike ? or a.allocation_no ilike ?)
					""");
			String like = "%" + keyword + "%";
			for (int i = 0; i < 9; i++) {
				args.add(like);
			}
		}
		String from = """
				from fin_voucher_draft v
				left join fin_sales_invoice si on v.source_type = 'SALES_INVOICE' and si.id = v.source_id
				left join fin_purchase_invoice pi on v.source_type = 'PURCHASE_INVOICE' and pi.id = v.source_id
				left join fin_expense e on v.source_type = 'EXPENSE' and e.id = v.source_id
				left join fin_receipt r on v.source_type = 'RECEIPT' and r.id = v.source_id
				left join fin_payment p on v.source_type = 'PAYMENT' and p.id = v.source_id
				left join fin_settlement_allocation a
					on v.source_type = 'SETTLEMENT_ALLOCATION' and a.id = v.source_id
				""";
		String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) " + from + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Long> ids = this.jdbcTemplate.queryForList("select v.id " + from + where
				+ " order by v.business_date desc, v.id desc limit ? offset ?", Long.class, pageArgs.toArray());
		List<Map<String, Object>> items = ids.stream().map((id) -> voucherDraft(id, currentUser)).toList();
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional
	public Map<String, Object> generateVoucherDraft(VoucherDraftGenerateRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:voucher-draft:generate");
		if (request == null || !hasText(request.sourceType()) || request.sourceId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		requireActionMetadata(request.version(), request.idempotencyKey());
		String requestFingerprint = fingerprint("VOUCHER_DRAFT_GENERATE", request);
		Optional<Long> idempotent = idempotentDocument("fin_voucher_draft", currentUser.username(),
				request.idempotencyKey(), requestFingerprint);
		if (idempotent.isPresent()) {
			return voucherDraft(idempotent.get(), currentUser);
		}
		Optional<Long> existingSource = existingVoucherDraft(request.sourceType(), request.sourceId());
		if (existingSource.isPresent()) {
			return voucherDraft(existingSource.get(), currentUser);
		}
		VoucherSource source = voucherSource(request.sourceType(), request.sourceId());
		assertVersion(request.version(), source.version());
		this.businessPeriodGuard.assertWritable(source.businessDate(), BusinessPeriodOperation.CREATE,
				"FINANCE_VOUCHER_DRAFT", null);
		String draftNo = nextNo("VD", VOUCHER_DRAFT_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		Long draftId = this.jdbcTemplate.queryForObject("""
				insert into fin_voucher_draft (
					draft_no, source_type, source_id, status, business_date, summary, party_type, party_id,
					party_name, ownership_type, project_id, debit_amount, credit_amount, generation_version,
					idempotency_key, request_fingerprint, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, draftNo, source.sourceType(), source.sourceId(), source.businessDate(),
				source.summary(), source.partyType(), source.partyId(), source.partyName(), source.ownershipType(),
				source.projectId(), source.amount(), source.amount(), source.version(),
				blankToNull(request.idempotencyKey()), requestFingerprint, currentUser.username(), now,
				currentUser.username(), now);
		this.jdbcTemplate.update("""
				insert into fin_voucher_draft_line (
					draft_id, line_no, direction, business_category, amount, source_type, source_id, created_at
				)
				values (?, 1, 'DEBIT', ?, ?, ?, ?, ?),
				       (?, 2, 'CREDIT', ?, ?, ?, ?, ?)
				""", draftId, source.debitCategory(), source.amount(), source.sourceType(), source.sourceId(), now,
				draftId, source.creditCategory(), source.amount(), source.sourceType(), source.sourceId(), now);
		this.auditService.record(currentUser, "FINANCE_VOUCHER_DRAFT_GENERATE", "FINANCE_VOUCHER_DRAFT", draftId,
				draftNo, servletRequest);
		return voucherDraft(draftId, currentUser);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> voucherDraft(Long id, CurrentUser currentUser) {
		requireUser(currentUser, "finance:voucher-draft:view");
		VoucherDraftRow row = voucherDraftRow(id).orElseThrow(this::voucherDraftNotFound);
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put("draftNo", row.documentNo());
		response.put("sourceType", row.sourceType());
		response.put("sourceId", row.sourceId());
		response.put("status", row.status());
		response.put("businessDate", row.businessDate());
		response.put("summary", row.summary());
		response.put("partyType", row.partyType());
		response.put("partyId", row.partyId());
		response.put("partyName", row.partyName());
		response.put("ownershipType", row.ownershipType());
		response.put("projectId", row.projectId());
		response.put("sourceNo", voucherSourceNo(row.sourceType(), row.sourceId()));
		response.put("sourceSummary", row.summary());
		response.put("generationVersion", row.generationVersion());
		response.put("debitAmount", decimalString(row.debitAmount()));
		response.put("creditAmount", decimalString(row.creditAmount()));
		response.put("debitTotal", decimalString(row.debitAmount()));
		response.put("creditTotal", decimalString(row.creditAmount()));
		response.put("balanced", row.debitAmount().compareTo(row.creditAmount()) == 0);
		response.put("formalVoucherNo", row.formalVoucherNo());
		response.put("postingStatus", row.postingStatus());
		response.put("version", row.version());
		response.put("allowedActions", allowedVoucherActions(row.status(), currentUser));
		response.put("restrictedReasons", List.of("INFORMAL_VOUCHER_DRAFT"));
		response.put("lines", voucherDraftLines(id));
		return response;
	}

	@Transactional
	public Map<String, Object> readyVoucherDraft(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:voucher-draft:ready");
		ActionRequestContext action = actionRequest(currentUser, "READY", "FINANCE_VOUCHER_DRAFT", id, request);
		if (action.resultResourceId() != null) {
			return voucherDraft(action.resultResourceId(), currentUser);
		}
		VoucherDraftRow row = lockVoucherDraft(id).orElseThrow(this::voucherDraftNotFound);
		assertVersion(action.version(), row.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_voucher_draft
				set status = 'READY', ready_by = ?, ready_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_VOUCHER_DRAFT_READY", "FINANCE_VOUCHER_DRAFT", id,
				row.documentNo(), servletRequest);
		recordAction(action, "FINANCE_VOUCHER_DRAFT", id, row.version() + 1, currentUser);
		return voucherDraft(id, currentUser);
	}

	@Transactional
	public Map<String, Object> cancelVoucherDraft(Long id, FinanceActionRequest request, CurrentUser currentUser,
			HttpServletRequest servletRequest) {
		requireUser(currentUser, "finance:voucher-draft:cancel");
		ActionRequestContext action = actionRequest(currentUser, "CANCEL", "FINANCE_VOUCHER_DRAFT", id, request);
		if (action.resultResourceId() != null) {
			return voucherDraft(action.resultResourceId(), currentUser);
		}
		VoucherDraftRow row = lockVoucherDraft(id).orElseThrow(this::voucherDraftNotFound);
		assertVersion(action.version(), row.version());
		if (!List.of("DRAFT", "READY").contains(row.status())) {
			throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update fin_voucher_draft
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", currentUser.username(), now, currentUser.username(), now, id);
		this.auditService.record(currentUser, "FINANCE_VOUCHER_DRAFT_CANCEL", "FINANCE_VOUCHER_DRAFT", id,
				row.documentNo(), servletRequest);
		recordAction(action, "FINANCE_VOUCHER_DRAFT", id, row.version() + 1, currentUser);
		return voucherDraft(id, currentUser);
	}

	private PageResponse<Map<String, Object>> page(String tableName, String numberColumn, String keyword,
			String status, int page, int pageSize, DetailResolver resolver) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add(numberColumn + " ilike ?");
			args.add("%" + keyword + "%");
		}
		if (hasText(status)) {
			conditions.add("status = ?");
			args.add(status);
		}
		String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("select count(*) from " + tableName + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Long> ids = this.jdbcTemplate.queryForList("select id from " + tableName + where
				+ " order by updated_at desc, id desc limit ? offset ?", Long.class, pageArgs.toArray());
		List<Map<String, Object>> items = ids.stream().map(resolver::resolve).toList();
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	private PageResponse<Map<String, Object>> cashPage(String tableName, String numberColumn, boolean receivableSide,
			String keyword, CurrentUser currentUser, int page, int pageSize) {
		String balanceTable = receivableSide ? "fin_receipt_balance" : "fin_payment_balance";
		String documentAlias = receivableSide ? "r" : "p";
		String balanceJoinColumn = receivableSide ? "receipt_id" : "payment_id";
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add(documentAlias + "." + numberColumn + " ilike ?");
			args.add("%" + keyword + "%");
		}
		String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
		String from = " from " + balanceTable + " b join " + tableName + " " + documentAlias + " on "
				+ documentAlias + ".id = b." + balanceJoinColumn;
		Long total = this.jdbcTemplate.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Long> ids = this.jdbcTemplate.queryForList("select " + documentAlias + ".id" + from + where
				+ " order by " + documentAlias + ".updated_at desc, " + documentAlias + ".id desc limit ? offset ?",
				Long.class, pageArgs.toArray());
		List<Map<String, Object>> items = ids.stream()
			.map((id) -> receivableSide ? advanceReceipt(id, currentUser) : prepayment(id, currentUser))
			.toList();
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	private List<Map<String, Object>> purchaseReceiptCandidates(String keyword, Long sourceId, Long supplierId,
			String ownershipType, Long projectId) {
		List<Object> args = new ArrayList<>();
		String where = " where pr.status = 'POSTED' and pl.quantity > coalesce(inv.invoiced_quantity, 0)";
		if (hasText(keyword)) {
			where += " and (pr.receipt_no ilike ? or po.order_no ilike ? or s.name ilike ?)";
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (sourceId != null) {
			where += " and pr.id = ?";
			args.add(sourceId);
		}
		if (supplierId != null) {
			where += " and pr.supplier_id = ?";
			args.add(supplierId);
		}
		if (hasText(ownershipType)) {
			where += " and po.purchase_mode = ?";
			args.add(ownershipType);
		}
		if (projectId != null) {
			where += " and po.project_id = ?";
			args.add(projectId);
		}
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select 'PURCHASE_RECEIPT' as source_type, pr.receipt_no as source_no, pr.id as source_id,
				       pl.id as source_line_id, pl.id as receipt_line_id, pl.line_no, s.name as supplier_name,
				       po.order_no as summary, pr.business_date, m.code as material_code, m.name as material_name,
				       u.name as unit_name,
				       coalesce(inv.invoiced_quantity, 0) as invoiced_quantity,
				       pl.quantity - coalesce(inv.invoiced_quantity, 0) as available_quantity,
				       pl.quantity - coalesce(inv.invoiced_quantity, 0) as invoice_quantity,
				       ol.tax_excluded_unit_price as pretax_unit_price, ol.tax_rate,
				       round((pl.quantity - coalesce(inv.invoiced_quantity, 0)) * ol.tax_excluded_unit_price, 2)
				           as pretax_amount,
				       round((pl.quantity - coalesce(inv.invoiced_quantity, 0))
				           * (ol.tax_included_unit_price - ol.tax_excluded_unit_price), 2) as tax_amount,
				       round((pl.quantity - coalesce(inv.invoiced_quantity, 0)) * ol.tax_included_unit_price, 2)
				           as total_amount
				from proc_purchase_receipt_line pl
				join proc_purchase_receipt pr on pr.id = pl.receipt_id
				join proc_purchase_order po on po.id = pr.order_id
				join proc_purchase_order_line ol on ol.id = pl.order_line_id
				join mst_supplier s on s.id = pr.supplier_id
				join mst_material m on m.id = pl.material_id
				join mst_unit u on u.id = pl.unit_id
				left join (
					select pil.source_line_id, sum(pil.quantity) as invoiced_quantity
					from fin_purchase_invoice_line pil
					join fin_purchase_invoice pi on pi.id = pil.purchase_invoice_id
					where pi.status <> 'CANCELLED'
					and pi.source_type = 'PURCHASE_RECEIPT'
					group by pil.source_line_id
				) inv on inv.source_line_id = pl.id
				""" + where + " " + """
				order by pr.business_date desc, pr.id desc, pl.line_no asc
				""", args.toArray()));
	}

	private List<Map<String, Object>> outsourcingReceiptCandidates(String keyword, Long sourceId, Long supplierId,
			String ownershipType, Long projectId) {
		List<Object> args = new ArrayList<>();
		String where = " where r.status = 'POSTED' and rl.accepted_quantity > coalesce(inv.invoiced_quantity, 0)";
		if (hasText(keyword)) {
			where += " and (r.receipt_no ilike ? or o.outsourcing_order_no ilike ? or s.name ilike ?)";
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (sourceId != null) {
			where += " and r.id = ?";
			args.add(sourceId);
		}
		if (supplierId != null) {
			where += " and o.supplier_id = ?";
			args.add(supplierId);
		}
		if (hasText(ownershipType)) {
			where += " and r.ownership_type = ?";
			args.add(ownershipType);
		}
		if (projectId != null) {
			where += " and r.project_id = ?";
			args.add(projectId);
		}
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select 'OUTSOURCING_RECEIPT' as source_type, r.receipt_no as source_no, r.id as source_id,
				       rl.id as source_line_id, rl.id as outsourcing_receipt_line_id, rl.line_no,
				       s.name as supplier_name, o.outsourcing_order_no as summary, r.business_date,
				       m.code as material_code,
				       m.name as material_name, u.name as unit_name,
				       coalesce(inv.invoiced_quantity, 0) as invoiced_quantity,
				       rl.accepted_quantity - coalesce(inv.invoiced_quantity, 0) as available_quantity,
				       rl.accepted_quantity - coalesce(inv.invoiced_quantity, 0) as invoice_quantity,
				       coalesce(rl.unit_cost, rl.provisional_unit_cost, r.unit_cost, r.provisional_unit_cost, 0)
					       as pretax_unit_price,
				       0::numeric as tax_rate,
				       round((rl.accepted_quantity - coalesce(inv.invoiced_quantity, 0)) * coalesce(rl.unit_cost,
					       rl.provisional_unit_cost, r.unit_cost, r.provisional_unit_cost, 0), 2) as pretax_amount,
				       0::numeric as tax_amount,
				       round((rl.accepted_quantity - coalesce(inv.invoiced_quantity, 0)) * coalesce(rl.unit_cost,
					       rl.provisional_unit_cost, r.unit_cost, r.provisional_unit_cost, 0), 2) as total_amount
				from mfg_outsourcing_receipt_line rl
				join mfg_outsourcing_receipt r on r.id = rl.receipt_id
				join mfg_outsourcing_order o on o.id = r.outsourcing_order_id
				join mst_supplier s on s.id = o.supplier_id
				join mst_material m on m.id = o.product_material_id
				join mst_unit u on u.id = m.unit_id
				left join (
					select pil.source_line_id, sum(pil.quantity) as invoiced_quantity
					from fin_purchase_invoice_line pil
					join fin_purchase_invoice pi on pi.id = pil.purchase_invoice_id
					where pi.status <> 'CANCELLED'
					and pi.source_type = 'OUTSOURCING_RECEIPT'
					group by pil.source_line_id
				) inv on inv.source_line_id = rl.id
				""" + where + " " + """
				order by r.business_date desc, r.id desc, rl.line_no asc
				""", args.toArray()));
	}

	private PageResponse<Map<String, Object>> receiptFunds(Long partnerId, CurrentUser currentUser, int page,
			int pageSize) {
		List<Object> args = new ArrayList<>();
		String where = " where b.status = 'POSTED' and b.available_amount > 0";
		if (partnerId != null) {
			where += " and b.customer_id = ?";
			args.add(partnerId);
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_receipt_balance b
				join fin_receipt r on r.id = b.receipt_id
				""" + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = normalizeRows(this.jdbcTemplate.queryForList("""
				select r.id, r.receipt_no as advance_no, r.receipt_no as fund_no, c.name as partner_name,
				       b.ownership_type, p.name as project_name, r.receipt_date as business_date, r.amount,
				       b.allocated_amount, b.available_amount,
				       case when b.available_amount = b.original_amount then 'AVAILABLE'
				            when b.available_amount = 0 then 'APPLIED'
				            else 'PARTIALLY_APPLIED' end as status,
				       r.version
				from fin_receipt_balance b
				join fin_receipt r on r.id = b.receipt_id
				join mst_customer c on c.id = b.customer_id
				left join sal_project p on p.id = b.project_id
				""" + where + " " + """
				order by r.receipt_date desc, r.id desc
				limit ? offset ?
				""", pageArgs.toArray()));
		List<String> allowedActions = settlementFundActions(currentUser);
		items.forEach((item) -> item.put("allowedActions", allowedActions));
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	private PageResponse<Map<String, Object>> paymentFunds(Long partnerId, CurrentUser currentUser, int page,
			int pageSize) {
		List<Object> args = new ArrayList<>();
		String where = " where b.status = 'POSTED' and b.available_amount > 0";
		if (partnerId != null) {
			where += " and b.supplier_id = ?";
			args.add(partnerId);
		}
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_payment_balance b
				join fin_payment pmt on pmt.id = b.payment_id
				""" + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = normalizeRows(this.jdbcTemplate.queryForList("""
				select pmt.id, pmt.payment_no as advance_no, pmt.payment_no as fund_no, s.name as partner_name,
				       b.ownership_type, p.name as project_name, pmt.payment_date as business_date, pmt.amount,
				       b.allocated_amount, b.available_amount,
				       case when b.available_amount = b.original_amount then 'AVAILABLE'
				            when b.available_amount = 0 then 'APPLIED'
				            else 'PARTIALLY_APPLIED' end as status,
				       pmt.version
				from fin_payment_balance b
				join fin_payment pmt on pmt.id = b.payment_id
				join mst_supplier s on s.id = b.supplier_id
				left join sal_project p on p.id = b.project_id
				""" + where + " " + """
				order by pmt.payment_date desc, pmt.id desc
				limit ? offset ?
				""", pageArgs.toArray()));
		List<String> allowedActions = settlementFundActions(currentUser);
		items.forEach((item) -> item.put("allowedActions", allowedActions));
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	private PageResponse<Map<String, Object>> receivableTargets(Long partnerId, int page, int pageSize) {
		List<Object> args = new ArrayList<>();
		String where = " where r.status in ('CONFIRMED', 'PARTIALLY_RECEIVED') and r.unreceived_amount > 0";
		if (partnerId != null) {
			where += " and r.customer_id = ?";
			args.add(partnerId);
		}
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_receivable r" + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = normalizeRows(this.jdbcTemplate.queryForList("""
				select 'RECEIVABLE' as target_type, r.id as target_id, r.receivable_no as target_no,
				       r.total_amount as original_amount, r.received_amount as settled_amount,
				       r.adjusted_amount, 0::numeric as allocated_amount, r.unreceived_amount as unsettled_amount,
				       r.status, r.source_no as source_summary, r.version
				from fin_receivable r
				""" + where + " " + """
				order by r.business_date desc, r.id desc
				limit ? offset ?
				""", pageArgs.toArray()));
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	private PageResponse<Map<String, Object>> payableTargets(Long partnerId, int page, int pageSize) {
		List<Object> args = new ArrayList<>();
		String where = " where p.status in ('CONFIRMED', 'PARTIALLY_PAID') and p.unpaid_amount > 0";
		if (partnerId != null) {
			where += " and p.supplier_id = ?";
			args.add(partnerId);
		}
		Long total = this.jdbcTemplate.queryForObject("select count(*) from fin_payable p" + where, Long.class,
				args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = normalizeRows(this.jdbcTemplate.queryForList("""
				select 'PAYABLE' as target_type, p.id as target_id, p.payable_no as target_no,
				       p.total_amount as original_amount, p.paid_amount as settled_amount, p.adjusted_amount,
				       0::numeric as allocated_amount, p.unpaid_amount as unsettled_amount, p.status,
				       p.source_no as source_summary, p.version
				from fin_payable p
				""" + where + " " + """
				order by p.business_date desc, p.id desc
				limit ? offset ?
				""", pageArgs.toArray()));
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	private Comparator<Map<String, Object>> candidateComparator() {
		return (left, right) -> {
			int date = Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())
				.compare(candidateDate(left), candidateDate(right));
			if (date != 0) {
				return date;
			}
			int id = Long.compare(candidateLong(right, "sourceId"), candidateLong(left, "sourceId"));
			if (id != 0) {
				return id;
			}
			return Integer.compare(candidateInt(left, "lineNo"), candidateInt(right, "lineNo"));
		};
	}

	private LocalDate candidateDate(Map<String, Object> item) {
		Object value = item.get("businessDate");
		if (value instanceof LocalDate date) {
			return date;
		}
		if (value instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		if (value instanceof String text && hasText(text)) {
			return LocalDate.parse(text);
		}
		return null;
	}

	private long candidateLong(Map<String, Object> item, String key) {
		Object value = item.get(key);
		return value instanceof Number number ? number.longValue() : 0L;
	}

	private int candidateInt(Map<String, Object> item, String key) {
		Object value = item.get(key);
		return value instanceof Number number ? number.intValue() : 0;
	}

	private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
		return rows.stream().map(this::normalizeRow).toList();
	}

	private Map<String, Object> normalizeRow(Map<String, Object> row) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : row.entrySet()) {
			result.put(camel(entry.getKey()), jsonValue(entry.getValue()));
		}
		return result;
	}

	private Object jsonValue(Object value) {
		if (value instanceof BigDecimal decimal) {
			return decimalString(decimal);
		}
		return value;
	}

	private String camel(String key) {
		StringBuilder builder = new StringBuilder();
		boolean upperNext = false;
		for (char ch : key.toCharArray()) {
			if (ch == '_') {
				upperNext = true;
				continue;
			}
			builder.append(upperNext ? Character.toUpperCase(ch) : ch);
			upperNext = false;
		}
		return builder.toString();
	}

	private Map<String, Object> categoryMap(Long id, String code, String name) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", id);
		map.put("code", code);
		map.put("name", name);
		map.put("status", "ENABLED");
		return map;
	}

	private void insertSalesInvoiceLines(Long invoiceId, ShipmentSource source, List<SalesSourceLine> lines,
			OffsetDateTime now) {
		for (SalesSourceLine line : lines) {
			this.jdbcTemplate.update("""
					insert into fin_sales_invoice_line (
						sales_invoice_id, line_no, source_line_id, sales_order_id, sales_order_line_id,
						material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
						tax_excluded_amount, tax_amount, tax_included_amount, source_snapshot, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
						jsonb_build_object('sourceNo', ?, 'sourceLineNo', ?), ?, ?)
					""", invoiceId, line.lineNo(), line.id(), source.orderId(), line.orderLineId(),
					line.materialId(), line.unitId(), line.quantity(), line.taxRate(), line.taxExcludedUnitPrice(),
					line.taxIncludedUnitPrice(), line.taxExcludedAmount(), line.taxAmount(),
					line.taxIncludedAmount(), source.shipmentNo(), line.lineNo(), now, now);
		}
	}

	private void insertPurchaseInvoiceLines(Long invoiceId, PurchaseSource source, List<PurchaseSourceLine> sourceLines,
			List<ValidatedInvoiceLine> requestLines, MatchResult matchResult, OffsetDateTime now) {
		Map<Long, PurchaseSourceLine> sourceLineById = sourceLineById(sourceLines);
		for (int index = 0; index < requestLines.size(); index++) {
			ValidatedInvoiceLine requestLine = requestLines.get(index);
			PurchaseSourceLine sourceLine = sourceLineById.get(requestLine.sourceLineId());
			this.jdbcTemplate.update("""
					insert into fin_purchase_invoice_line (
						purchase_invoice_id, line_no, source_line_id, purchase_order_id, purchase_order_line_id,
						outsourcing_order_id, material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price,
						tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount,
						match_status, source_snapshot, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
						jsonb_build_object('sourceNo', ?, 'sourceLineNo', ?), ?, ?)
					""", invoiceId, index + 1, requestLine.sourceLineId(), sourceLine.purchaseOrderId(),
					sourceLine.purchaseOrderLineId(), sourceLine.outsourcingOrderId(), sourceLine.materialId(),
					sourceLine.unitId(), requestLine.quantity(), requestLine.taxRate(),
					requestLine.taxExcludedUnitPrice(), requestLine.taxIncludedUnitPrice(),
					requestLine.taxExcludedAmount(), requestLine.taxAmount(), requestLine.taxIncludedAmount(),
					lineMatchStatus(matchResult, source.settlementKind()), source.sourceNo(), sourceLine.lineNo(), now,
					now);
		}
	}

	private void insertMatchDifferences(Long invoiceId, List<MatchDifference> differences, OffsetDateTime now) {
		for (MatchDifference difference : differences) {
			this.jdbcTemplate.update("""
					insert into fin_purchase_invoice_match_difference (
						purchase_invoice_id, purchase_invoice_line_id, difference_type, expected_value, actual_value,
						message, created_at
					)
					values (?, ?, ?, ?, ?, ?, ?)
					""", invoiceId, difference.lineId(), difference.differenceType(), difference.expectedValue(),
					difference.actualValue(), difference.message(), now);
		}
	}

	private void insertExpenseLines(Long expenseId, List<ValidatedExpenseLine> lines, OffsetDateTime now) {
		int lineNo = 1;
		for (ValidatedExpenseLine line : lines) {
			this.jdbcTemplate.update("""
					insert into fin_expense_line (
						expense_id, line_no, expense_category, description, source_type, source_id, source_no,
						tax_rate, tax_excluded_amount, tax_amount, tax_included_amount, source_snapshot, created_at,
						updated_at
					)
					values (?, ?, ?, cast(? as varchar), cast(? as varchar), ?, cast(? as varchar), ?, ?, ?, ?,
						jsonb_build_object('sourceType', cast(? as varchar)), ?, ?)
					""", expenseId, lineNo++, line.expenseCategory(), blankToNull(line.description()),
					blankToNull(line.sourceType()), line.sourceId(), blankToNull(line.sourceNo()), line.taxRate(),
					line.taxExcludedAmount(), line.taxAmount(), line.taxIncludedAmount(),
					blankToNull(line.sourceType()), now, now);
		}
	}

	private Long createReceivable(InvoiceRow invoice, ShipmentSource source, String operator) {
		String receivableNo = nextNo("AR", SALES_INVOICE_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		List<SalesSourceLine> sourceLines = salesSourceLines(source.id());
		DocumentAmounts sourceAmounts = salesAmounts(sourceLines);
		Long receivableId = this.jdbcTemplate.queryForObject("""
				insert into fin_receivable (
					receivable_no, customer_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, received_amount, unreceived_amount, status, remark, created_by, created_at,
					updated_by, updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?, 0, ?, 'CONFIRMED', ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, receivableNo, source.customerId(), source.id(), source.shipmentNo(),
				invoice.businessDate(), invoice.dueDate(), sourceAmounts.taxIncludedAmount(),
				sourceAmounts.taxIncludedAmount(),
				"由销售发票 " + invoice.documentNo() + " 生成", operator, now, operator, now, operator, now);
		for (SalesSourceLine line : sourceLines) {
			this.jdbcTemplate.update("""
					insert into fin_receivable_source (
						receivable_id, source_type, source_id, source_no, source_line_id, source_line_no,
						source_amount
					)
					values (?, 'SALES_SHIPMENT', ?, ?, ?, ?, ?)
					""", receivableId, source.id(), source.shipmentNo(), line.id(), line.lineNo(),
					line.taxIncludedAmount());
		}
		return receivableId;
	}

	private Long createStandardPayable(InvoiceRow invoice, String operator) {
		PurchaseSource source = purchaseSource(invoice.settlementKind(), invoice.sourceType(), invoice.sourceId());
		DocumentAmounts sourceAmounts = purchaseSourceAmounts(source);
		FinancePayableSource payableSource = new SourcePayable(invoice.id(), invoice.documentNo(), source.supplierId(),
				source.ownershipType(), source.projectId(), invoice.businessDate(), invoice.dueDate(),
				sourceAmounts.taxIncludedAmount());
		Long payableId = createStandalonePayable(payableSource, PURCHASE_RECEIPT, source.sourceId(), source.sourceNo(),
				operator);
		for (PurchaseSourceLine line : purchaseSourceLines(source)) {
			this.jdbcTemplate.update("""
					insert into fin_payable_source (
						payable_id, source_type, source_id, source_no, source_line_id, source_line_no, source_amount
					)
					values (?, 'PURCHASE_RECEIPT', ?, ?, ?, ?, ?)
					""", payableId, source.sourceId(), source.sourceNo(), line.id(), line.lineNo(),
					line.taxIncludedAmount());
		}
		return payableId;
	}

	private Long createStandalonePayable(FinancePayableSource source, String sourceType, Long sourceId,
			String sourceNo, String operator) {
		String payableNo = nextNo("AP", PURCHASE_INVOICE_SEQUENCE);
		OffsetDateTime now = OffsetDateTime.now();
		return this.jdbcTemplate.queryForObject("""
				insert into fin_payable (
					payable_no, supplier_id, source_type, source_id, source_no, business_date, due_date,
					total_amount, paid_amount, unpaid_amount, status, remark, created_by, created_at, updated_by,
					updated_at, confirmed_by, confirmed_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'CONFIRMED', ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, payableNo, source.partyId(), sourceType, sourceId, sourceNo, source.businessDate(),
				source.dueDate(), source.taxIncludedAmount(), source.taxIncludedAmount(),
				"由 028 财务对象 " + source.documentNo() + " 生成", operator, now, operator, now, operator, now);
	}

	private Optional<ShipmentSource> lockShipment(Long id) {
		return this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.order_id, so.order_no, sh.customer_id, c.name as customer_name,
				       sh.business_date, sh.status, coalesce(case when so.project_id is null then 'PUBLIC' else 'PROJECT' end, 'PUBLIC') as ownership_type,
				       so.project_id
				from sal_sales_shipment sh
				join sal_sales_order so on so.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				where sh.id = ?
				for update
				""", this::mapShipmentSource, id).stream().findFirst();
	}

	private List<SalesSourceLine> salesSourceLines(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.line_no, sl.order_line_id, sl.material_id, sl.unit_id, sl.quantity, sl.tax_rate,
				       sl.tax_excluded_unit_price, sl.tax_included_unit_price, sl.tax_excluded_amount,
				       sl.tax_amount, sl.tax_included_amount
				from sal_sales_shipment_line sl
				where sl.shipment_id = ?
				order by sl.line_no asc, sl.id asc
				""", this::mapSalesSourceLine, shipmentId);
	}

	private List<SalesSourceLine> salesSourceLines(Long shipmentId, List<SalesInvoiceSourceLineRequest> requests,
			Long ignoredInvoiceId) {
		List<SalesSourceLine> sourceLines = salesSourceLines(shipmentId);
		if (requests == null || requests.isEmpty()) {
			List<SalesSourceLine> remaining = new ArrayList<>();
			for (SalesSourceLine sourceLine : sourceLines) {
				SalesSourceLine line = salesLineForQuantity(sourceLine,
						remainingQuantity(sourceLine.quantity(), invoicedSalesQuantity(sourceLine.id(), ignoredInvoiceId)));
				if (line.quantity().compareTo(BigDecimal.ZERO) > 0) {
					remaining.add(line);
				}
			}
			if (remaining.isEmpty()) {
				throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_OVER_INVOICED);
			}
			return remaining;
		}
		Map<Long, SalesSourceLine> sourceLineById = new LinkedHashMap<>();
		for (SalesSourceLine sourceLine : sourceLines) {
			sourceLineById.put(sourceLine.id(), sourceLine);
		}
		List<SalesSourceLine> result = new ArrayList<>();
		Set<Long> requestedLineIds = new HashSet<>();
		for (SalesInvoiceSourceLineRequest request : requests) {
			if (request == null || request.sourceLineId() == null || !sourceLineById.containsKey(request.sourceLineId())
					|| !requestedLineIds.add(request.sourceLineId())) {
				throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
			}
			SalesSourceLine sourceLine = sourceLineById.get(request.sourceLineId());
			BigDecimal remainingQuantity = remainingQuantity(sourceLine.quantity(),
					invoicedSalesQuantity(sourceLine.id(), ignoredInvoiceId));
			BigDecimal quantity = request.invoiceQuantity() == null ? remainingQuantity
					: validateQuantity(request.invoiceQuantity());
			if (quantity.compareTo(remainingQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_OVER_INVOICED);
			}
			result.add(salesLineForQuantity(sourceLine, quantity));
		}
		return result;
	}

	private SalesSourceLine salesLineForQuantity(SalesSourceLine sourceLine, BigDecimal quantity) {
		BigDecimal normalizedQuantity = quantity.setScale(6, RoundingMode.HALF_UP);
		BigDecimal taxExcludedAmount = money(normalizedQuantity.multiply(sourceLine.taxExcludedUnitPrice()));
		BigDecimal taxAmount = money(normalizedQuantity.multiply(sourceLine.taxIncludedUnitPrice()
			.subtract(sourceLine.taxExcludedUnitPrice())));
		BigDecimal taxIncludedAmount = money(taxExcludedAmount.add(taxAmount));
		return new SalesSourceLine(sourceLine.id(), sourceLine.lineNo(), sourceLine.orderLineId(),
				sourceLine.materialId(), sourceLine.unitId(), normalizedQuantity, sourceLine.taxRate(),
				sourceLine.taxExcludedUnitPrice(), sourceLine.taxIncludedUnitPrice(), taxExcludedAmount,
				taxAmount, taxIncludedAmount);
	}

	private BigDecimal remainingQuantity(BigDecimal sourceQuantity, BigDecimal usedQuantity) {
		BigDecimal remaining = sourceQuantity.subtract(usedQuantity).setScale(6, RoundingMode.HALF_UP);
		return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
				: remaining;
	}

	private BigDecimal invoicedSalesQuantity(Long sourceLineId, Long ignoredInvoiceId) {
		String sql = """
				select coalesce(sum(l.quantity), 0)
				from fin_sales_invoice_line l
				join fin_sales_invoice i on i.id = l.sales_invoice_id
				where l.source_line_id = ?
				and i.status <> 'CANCELLED'
				""";
		List<Object> args = new ArrayList<>();
		args.add(sourceLineId);
		if (ignoredInvoiceId != null) {
			sql += " and i.id <> ?";
			args.add(ignoredInvoiceId);
		}
		BigDecimal quantity = this.jdbcTemplate.queryForObject(sql, BigDecimal.class, args.toArray());
		return quantity == null ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
				: quantity.setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal invoicedPurchaseQuantity(Long sourceLineId, Long ignoredInvoiceId) {
		String sql = """
				select coalesce(sum(l.quantity), 0)
				from fin_purchase_invoice_line l
				join fin_purchase_invoice i on i.id = l.purchase_invoice_id
				where l.source_line_id = ?
				and i.status <> 'CANCELLED'
				""";
		List<Object> args = new ArrayList<>();
		args.add(sourceLineId);
		if (ignoredInvoiceId != null) {
			sql += " and i.id <> ?";
			args.add(ignoredInvoiceId);
		}
		BigDecimal quantity = this.jdbcTemplate.queryForObject(sql, BigDecimal.class, args.toArray());
		return quantity == null ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
				: quantity.setScale(6, RoundingMode.HALF_UP);
	}

	private PurchaseSource purchaseSource(String settlementKind, String sourceType, Long sourceId) {
		if ("STANDARD_PURCHASE".equals(settlementKind) && PURCHASE_RECEIPT.equals(sourceType)) {
			return lockPurchaseReceipt(sourceId).orElseThrow(this::sourceNotFound);
		}
		if ("OUTSOURCING".equals(settlementKind) && OUTSOURCING_RECEIPT.equals(sourceType)) {
			return lockOutsourcingReceipt(sourceId).orElseThrow(this::sourceNotFound);
		}
		throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
	}

	private Optional<PurchaseSource> lockPurchaseReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select pr.id as source_id, pr.receipt_no as source_no, pr.supplier_id, s.name as supplier_name,
				       pr.business_date, pr.status, po.id as purchase_order_id, po.order_no as purchase_order_no,
				       po.purchase_mode as ownership_type, po.project_id
				from proc_purchase_receipt pr
				join proc_purchase_order po on po.id = pr.order_id
				join mst_supplier s on s.id = pr.supplier_id
				where pr.id = ?
				for update
				""", (rs, rowNum) -> new PurchaseSource("STANDARD_PURCHASE", PURCHASE_RECEIPT, rs.getLong("source_id"),
				rs.getString("source_no"), rs.getLong("supplier_id"), rs.getString("supplier_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				ownershipType(rs.getString("ownership_type")), nullableLong(rs, "project_id")), id).stream()
			.findFirst();
	}

	private Optional<PurchaseSource> lockOutsourcingReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select r.id as source_id, r.receipt_no as source_no, o.supplier_id, s.name as supplier_name,
				       r.business_date, r.status, o.ownership_type, o.project_id
				from mfg_outsourcing_receipt r
				join mfg_outsourcing_order o on o.id = r.outsourcing_order_id
				join mst_supplier s on s.id = o.supplier_id
				where r.id = ?
				for update
				""", (rs, rowNum) -> new PurchaseSource("OUTSOURCING", OUTSOURCING_RECEIPT,
				rs.getLong("source_id"), rs.getString("source_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				ownershipType(rs.getString("ownership_type")), nullableLong(rs, "project_id")), id).stream()
			.findFirst();
	}

	private List<PurchaseSourceLine> purchaseSourceLines(PurchaseSource source) {
		if (PURCHASE_RECEIPT.equals(source.sourceType())) {
			return this.jdbcTemplate.query("""
					select pl.id, pl.line_no, po.id as purchase_order_id, ol.id as purchase_order_line_id,
					       null::bigint as outsourcing_order_id, pl.material_id, pl.unit_id, pl.quantity,
					       ol.tax_rate, ol.tax_excluded_unit_price, ol.tax_included_unit_price,
					       round(pl.quantity * ol.tax_excluded_unit_price, 2) as tax_excluded_amount,
					       round(pl.quantity * (ol.tax_included_unit_price - ol.tax_excluded_unit_price), 2) as tax_amount,
					       round(pl.quantity * ol.tax_included_unit_price, 2) as tax_included_amount
					from proc_purchase_receipt_line pl
					join proc_purchase_order_line ol on ol.id = pl.order_line_id
					join proc_purchase_order po on po.id = ol.order_id
					where pl.receipt_id = ?
					order by pl.line_no asc, pl.id asc
					""", this::mapPurchaseSourceLine, source.sourceId());
		}
		return this.jdbcTemplate.query("""
				select rl.id, rl.line_no, null::bigint as purchase_order_id, null::bigint as purchase_order_line_id,
				       o.id as outsourcing_order_id, o.product_material_id as material_id, m.unit_id,
				       rl.accepted_quantity as quantity, 0::numeric as tax_rate,
				       coalesce(rl.unit_cost, rl.provisional_unit_cost, r.unit_cost, r.provisional_unit_cost, 0) as tax_excluded_unit_price,
				       coalesce(rl.unit_cost, rl.provisional_unit_cost, r.unit_cost, r.provisional_unit_cost, 0) as tax_included_unit_price,
				       round(rl.accepted_quantity * coalesce(rl.unit_cost, rl.provisional_unit_cost, r.unit_cost, r.provisional_unit_cost, 0), 2) as tax_excluded_amount,
				       0::numeric as tax_amount,
				       round(rl.accepted_quantity * coalesce(rl.unit_cost, rl.provisional_unit_cost, r.unit_cost, r.provisional_unit_cost, 0), 2) as tax_included_amount
				from mfg_outsourcing_receipt_line rl
				join mfg_outsourcing_receipt r on r.id = rl.receipt_id
				join mfg_outsourcing_order o on o.id = r.outsourcing_order_id
				join mst_material m on m.id = o.product_material_id
				where rl.receipt_id = ?
				order by rl.line_no asc, rl.id asc
				""", this::mapPurchaseSourceLine, source.sourceId());
	}

	private Optional<InvoiceRow> salesInvoiceRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, invoice_no as document_no, customer_id as party_id, ownership_type, project_id,
				       source_type, source_id, source_no, invoice_date as business_date, due_date, invoice_type,
				       null::varchar as settlement_kind, null::varchar as match_status, tax_excluded_amount,
				       tax_amount, tax_included_amount, status, linked_receivable_id, null::bigint as linked_payable_id,
				       version
				from fin_sales_invoice
				where id = ?
				""", this::mapInvoiceRow, id).stream().findFirst();
	}

	private Optional<InvoiceRow> lockSalesInvoice(Long id) {
		return this.jdbcTemplate.query("""
				select id, invoice_no as document_no, customer_id as party_id, ownership_type, project_id,
				       source_type, source_id, source_no, invoice_date as business_date, due_date, invoice_type,
				       null::varchar as settlement_kind, null::varchar as match_status, tax_excluded_amount,
				       tax_amount, tax_included_amount, status, linked_receivable_id, null::bigint as linked_payable_id,
				       version
				from fin_sales_invoice
				where id = ?
				for update
				""", this::mapInvoiceRow, id).stream().findFirst();
	}

	private Optional<InvoiceRow> purchaseInvoiceRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, invoice_no as document_no, supplier_id as party_id, ownership_type, project_id,
				       source_type, source_id, source_no, invoice_date as business_date, due_date, invoice_type,
				       settlement_kind, match_status, tax_excluded_amount, tax_amount, tax_included_amount,
				       status, null::bigint as linked_receivable_id, linked_payable_id, version
				from fin_purchase_invoice
				where id = ?
				""", this::mapInvoiceRow, id).stream().findFirst();
	}

	private Optional<InvoiceRow> lockPurchaseInvoice(Long id) {
		return this.jdbcTemplate.query("""
				select id, invoice_no as document_no, supplier_id as party_id, ownership_type, project_id,
				       source_type, source_id, source_no, invoice_date as business_date, due_date, invoice_type,
				       settlement_kind, match_status, tax_excluded_amount, tax_amount, tax_included_amount,
				       status, null::bigint as linked_receivable_id, linked_payable_id, version
				from fin_purchase_invoice
				where id = ?
				for update
				""", this::mapInvoiceRow, id).stream().findFirst();
	}

	private Optional<ExpenseRow> expenseRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, expense_no as document_no, supplier_id as party_id, ownership_type, project_id,
				       expense_date as business_date, due_date, invoice_type, tax_excluded_amount, tax_amount,
				       tax_included_amount, status, linked_payable_id, version
				from fin_expense
				where id = ?
				""", this::mapExpenseRow, id).stream().findFirst();
	}

	private Optional<ExpenseRow> lockExpense(Long id) {
		return this.jdbcTemplate.query("""
				select id, expense_no as document_no, supplier_id as party_id, ownership_type, project_id,
				       expense_date as business_date, due_date, invoice_type, tax_excluded_amount, tax_amount,
				       tax_included_amount, status, linked_payable_id, version
				from fin_expense
				where id = ?
				for update
				""", this::mapExpenseRow, id).stream().findFirst();
	}

	private Optional<CashRow> advanceReceiptRow(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no as document_no, r.customer_id as party_id,
				       coalesce(b.ownership_type, 'PUBLIC') as ownership_type, b.project_id,
				       r.receipt_date as business_date, r.amount, r.method, r.status,
				       coalesce(b.original_amount, r.amount) as original_amount,
				       coalesce(b.allocated_amount, 0) as allocated_amount,
				       coalesce(b.available_amount, r.amount) as available_amount, r.version
				from fin_receipt r
				left join fin_receipt_balance b on b.receipt_id = r.id
				where r.id = ?
				""", this::mapCashRow, id).stream().findFirst();
	}

	private Optional<CashRow> lockAdvanceReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no as document_no, r.customer_id as party_id, b.ownership_type, b.project_id,
				       r.receipt_date as business_date, r.amount, r.method, r.status, b.original_amount,
				       b.allocated_amount, b.available_amount, r.version
				from fin_receipt r
				join fin_receipt_balance b on b.receipt_id = r.id
				where r.id = ?
				for update
				""", this::mapCashRow, id).stream().findFirst();
	}

	private Optional<CashRow> prepaymentRow(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.payment_no as document_no, p.supplier_id as party_id,
				       coalesce(b.ownership_type, 'PUBLIC') as ownership_type, b.project_id,
				       p.payment_date as business_date, p.amount, p.method, p.status,
				       coalesce(b.original_amount, p.amount) as original_amount,
				       coalesce(b.allocated_amount, 0) as allocated_amount,
				       coalesce(b.available_amount, p.amount) as available_amount, p.version
				from fin_payment p
				left join fin_payment_balance b on b.payment_id = p.id
				where p.id = ?
				""", this::mapCashRow, id).stream().findFirst();
	}

	private Optional<CashRow> lockPrepayment(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.payment_no as document_no, p.supplier_id as party_id, b.ownership_type, b.project_id,
				       p.payment_date as business_date, p.amount, p.method, p.status, b.original_amount,
				       b.allocated_amount, b.available_amount, p.version
				from fin_payment p
				join fin_payment_balance b on b.payment_id = p.id
				where p.id = ?
				for update
				""", this::mapCashRow, id).stream().findFirst();
	}

	private Optional<SettlementAllocationRow> settlementAllocationRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, allocation_no as document_no, settlement_side, cash_source_type, cash_source_id,
				       party_id, ownership_type, project_id, business_date, total_amount, status, version
				from fin_settlement_allocation
				where id = ?
				""", this::mapSettlementAllocationRow, id).stream().findFirst();
	}

	private Optional<SettlementAllocationRow> lockSettlementAllocation(Long id) {
		return this.jdbcTemplate.query("""
				select id, allocation_no as document_no, settlement_side, cash_source_type, cash_source_id,
				       party_id, ownership_type, project_id, business_date, total_amount, status, version
				from fin_settlement_allocation
				where id = ?
				for update
				""", this::mapSettlementAllocationRow, id).stream().findFirst();
	}

	private Optional<VoucherDraftRow> voucherDraftRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, draft_no as document_no, source_type, source_id, status, business_date, summary,
				       party_type, party_id, party_name, ownership_type, project_id, debit_amount, credit_amount,
				       generation_version, formal_voucher_no, posting_status, version
				from fin_voucher_draft
				where id = ?
				""", this::mapVoucherDraftRow, id).stream().findFirst();
	}

	private Optional<VoucherDraftRow> lockVoucherDraft(Long id) {
		return this.jdbcTemplate.query("""
				select id, draft_no as document_no, source_type, source_id, status, business_date, summary,
				       party_type, party_id, party_name, ownership_type, project_id, debit_amount, credit_amount,
				       generation_version, formal_voucher_no, posting_status, version
				from fin_voucher_draft
				where id = ?
				for update
				""", this::mapVoucherDraftRow, id).stream().findFirst();
	}

	private List<Map<String, Object>> salesInvoiceLines(Long invoiceId) {
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select id, line_no, source_line_id, sales_order_id, sales_order_line_id, material_id, unit_id,
				       quantity, tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
				       tax_amount, tax_included_amount
				from fin_sales_invoice_line
				where sales_invoice_id = ?
				order by line_no asc
				""", invoiceId));
	}

	private List<SalesInvoiceLineRow> salesInvoiceLineRows(Long invoiceId) {
		return this.jdbcTemplate.query("""
				select id, line_no, source_line_id, tax_included_amount
				from fin_sales_invoice_line
				where sales_invoice_id = ?
				order by line_no asc
				""", (rs, rowNum) -> new SalesInvoiceLineRow(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("source_line_id"), rs.getBigDecimal("tax_included_amount")), invoiceId);
	}

	private List<Map<String, Object>> purchaseInvoiceLines(Long invoiceId) {
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select id, line_no, source_line_id, purchase_order_id, purchase_order_line_id,
				       outsourcing_order_id, material_id, unit_id, quantity, tax_rate, tax_excluded_unit_price,
				       tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount, match_status
				from fin_purchase_invoice_line
				where purchase_invoice_id = ?
				order by line_no asc
				""", invoiceId));
	}

	private List<ValidatedInvoiceLine> purchaseInvoiceLineRows(Long invoiceId) {
		return this.jdbcTemplate.query("""
				select source_line_id, quantity, tax_rate, tax_excluded_unit_price, tax_included_unit_price,
				       tax_excluded_amount, tax_amount, tax_included_amount
				from fin_purchase_invoice_line
				where purchase_invoice_id = ?
				order by line_no asc
				""", (rs, rowNum) -> new ValidatedInvoiceLine(rs.getLong("source_line_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("tax_rate"), rs.getBigDecimal("tax_excluded_unit_price"),
				rs.getBigDecimal("tax_included_unit_price"), rs.getBigDecimal("tax_excluded_amount"),
				rs.getBigDecimal("tax_amount"), rs.getBigDecimal("tax_included_amount")), invoiceId);
	}

	private List<PurchaseInvoiceLineRow> purchaseInvoiceLineRowsForSources(Long invoiceId) {
		return this.jdbcTemplate.query("""
				select id, line_no, source_line_id, tax_included_amount
				from fin_purchase_invoice_line
				where purchase_invoice_id = ?
				order by line_no asc
				""", (rs, rowNum) -> new PurchaseInvoiceLineRow(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("source_line_id"), rs.getBigDecimal("tax_included_amount")), invoiceId);
	}

	private List<Map<String, Object>> matchDifferences(Long invoiceId) {
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select id, purchase_invoice_line_id, difference_type, expected_value, actual_value, message
				from fin_purchase_invoice_match_difference
				where purchase_invoice_id = ?
				order by id asc
				""", invoiceId));
	}

	private List<Map<String, Object>> expenseLines(Long expenseId) {
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select id, line_no, expense_category, description, source_type, source_id, source_no, tax_rate,
				       tax_excluded_amount, tax_amount, tax_included_amount
				from fin_expense_line
				where expense_id = ?
				order by line_no asc
				""", expenseId));
	}

	private List<Map<String, Object>> settlementAllocationLines(Long allocationId) {
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select id, line_no, target_type, target_id, amount
				from fin_settlement_allocation_line
				where allocation_id = ?
				order by line_no asc
				""", allocationId));
	}

	private List<SettlementLine> settlementLineRows(Long allocationId) {
		return this.jdbcTemplate.query("""
				select target_type, target_id, amount
				from fin_settlement_allocation_line
				where allocation_id = ?
				order by line_no asc
				""", (rs, rowNum) -> new SettlementLine(rs.getString("target_type"), rs.getLong("target_id"),
				rs.getBigDecimal("amount")), allocationId);
	}

	private List<Map<String, Object>> voucherDraftLines(Long draftId) {
		return normalizeRows(this.jdbcTemplate.queryForList("""
				select id, line_no, direction, business_category, amount, source_type, source_id
				from fin_voucher_draft_line
				where draft_id = ?
				order by line_no asc
				""", draftId));
	}

	private SettlementDraft validateSettlementDraft(SettlementAllocationRequest request, boolean requireAvailable) {
		String side = request.settlementSide();
		if (!("RECEIVABLE".equals(side) || "PAYABLE".equals(side))) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if ("RECEIVABLE".equals(side) && !"RECEIPT".equals(request.cashSourceType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if ("PAYABLE".equals(side) && !"PAYMENT".equals(request.cashSourceType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		CashBalance balance = lockCashBalance(side, request.cashSourceId());
		validateFundVersion(request, balance);
		List<SettlementLine> lines = new ArrayList<>();
		BigDecimal total = ZERO;
		for (SettlementAllocationLineRequest lineRequest : request.lines()) {
			if (lineRequest == null || lineRequest.targetId() == null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			String targetType = lineRequest.targetType();
			if ("RECEIVABLE".equals(side) && !"RECEIVABLE".equals(targetType)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if ("PAYABLE".equals(side) && !"PAYABLE".equals(targetType)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			BigDecimal amount = validateMoney(lineRequest.amount());
			lines.add(new SettlementLine(targetType, lineRequest.targetId(), amount));
			total = money(total.add(amount));
		}
		if (requireAvailable && (!"POSTED".equals(balance.status()) || total.compareTo(balance.availableAmount()) > 0)) {
			throw new BusinessException(ApiErrorCode.FINANCE_SETTLEMENT_BALANCE_INSUFFICIENT);
		}
		for (SettlementLine line : lines) {
			if ("RECEIVABLE".equals(side)) {
				ReceivableBalance target = lockReceivableBalance(line.targetId()).orElseThrow(this::receivableNotFound);
				validateTargetVersion(request, line.targetType(), line.targetId(), target.version());
				if (!target.customerId().equals(balance.partyId())
						|| !target.ownershipType().equals(balance.ownershipType())
						|| !sameProject(target.projectId(), balance.projectId())
						|| line.amount().compareTo(target.openAmount()) > 0) {
					throw new BusinessException(ApiErrorCode.FINANCE_CROSS_PARTY_OR_PROJECT);
				}
			}
			else {
				PayableBalance target = lockPayableBalance(line.targetId()).orElseThrow(this::payableNotFound);
				validateTargetVersion(request, line.targetType(), line.targetId(), target.version());
				if (!target.supplierId().equals(balance.partyId())
						|| !target.ownershipType().equals(balance.ownershipType())
						|| !sameProject(target.projectId(), balance.projectId())
						|| line.amount().compareTo(target.openAmount()) > 0) {
					throw new BusinessException(ApiErrorCode.FINANCE_CROSS_PARTY_OR_PROJECT);
				}
			}
		}
		return new SettlementDraft(side, request.cashSourceType(), request.cashSourceId(), balance.partyId(),
				balance.ownershipType(), balance.projectId(), total, lines);
	}

	private void validateFundVersion(SettlementAllocationRequest request, CashBalance balance) {
		if (request.funds() == null || request.funds().size() != 1) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		SettlementFundRequest fund = request.funds().get(0);
		if (fund == null || fund.fundId() == null || fund.version() == null
				|| !fund.fundId().equals(request.cashSourceId())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (!fund.version().equals(balance.version())) {
			throw new BusinessException(ApiErrorCode.FINANCE_CONCURRENT_MODIFICATION);
		}
	}

	private void validateTargetVersion(SettlementAllocationRequest request, String targetType, Long targetId,
			Long version) {
		if (request.targets() == null || request.targets().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		Optional<SettlementTargetRequest> target = request.targets()
			.stream()
			.filter((item) -> item != null && targetType.equals(item.targetType()) && targetId.equals(item.targetId()))
			.findFirst();
		if (target.isEmpty() || target.get().version() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (!target.get().version().equals(version)) {
			throw new BusinessException(ApiErrorCode.FINANCE_CONCURRENT_MODIFICATION);
		}
	}

	private CashBalance lockCashBalance(String settlementSide, Long cashSourceId) {
		if ("RECEIVABLE".equals(settlementSide)) {
			return this.jdbcTemplate.query("""
					select b.receipt_id as id, b.customer_id as party_id, b.ownership_type, b.project_id,
					       b.original_amount, b.allocated_amount, b.available_amount, b.status, r.version
					from fin_receipt_balance b
					join fin_receipt r on r.id = b.receipt_id
					where b.receipt_id = ?
					for update
					""", this::mapCashBalance, cashSourceId).stream().findFirst().orElseThrow(this::receiptNotFound);
		}
		return this.jdbcTemplate.query("""
				select b.payment_id as id, b.supplier_id as party_id, b.ownership_type, b.project_id,
				       b.original_amount, b.allocated_amount, b.available_amount, b.status, p.version
				from fin_payment_balance b
				join fin_payment p on p.id = b.payment_id
				where b.payment_id = ?
				for update
				""", this::mapCashBalance, cashSourceId).stream().findFirst().orElseThrow(this::paymentNotFound);
	}

	private Optional<ReceivableBalance> lockReceivableBalance(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.customer_id,
				       coalesce(case when so.project_id is null then 'PUBLIC' else 'PROJECT' end, 'PUBLIC') as ownership_type,
				       so.project_id, r.total_amount, r.received_amount, r.adjusted_amount, r.unreceived_amount,
				       r.status, r.version
				from fin_receivable r
				left join sal_sales_shipment sh on r.source_type = 'SALES_SHIPMENT' and sh.id = r.source_id
				left join sal_sales_order so on so.id = sh.order_id
				where r.id = ?
				for update of r
				""", (rs, rowNum) -> new ReceivableBalance(rs.getLong("id"), rs.getLong("customer_id"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), rs.getBigDecimal("total_amount"),
				rs.getBigDecimal("received_amount"), rs.getBigDecimal("adjusted_amount"),
				rs.getBigDecimal("unreceived_amount"), rs.getString("status"), rs.getLong("version")), id).stream()
			.findFirst();
	}

	private Optional<PayableBalance> lockPayableBalance(Long id) {
		return this.jdbcTemplate.query("""
				select p.id, p.supplier_id,
				       coalesce(case
				           when p.source_type = 'PURCHASE_RECEIPT' then po.purchase_mode
				           when p.source_type = 'EXPENSE' then e.ownership_type
				           when p.source_type = 'OUTSOURCING_SETTLEMENT' then pi.ownership_type
				           else 'PUBLIC' end, 'PUBLIC') as ownership_type,
				       case
				           when p.source_type = 'PURCHASE_RECEIPT' then po.project_id
				           when p.source_type = 'EXPENSE' then e.project_id
				           when p.source_type = 'OUTSOURCING_SETTLEMENT' then pi.project_id
				           else null end as project_id,
				       p.total_amount, p.paid_amount, p.adjusted_amount, p.unpaid_amount, p.status, p.version
				from fin_payable p
				left join proc_purchase_receipt pr on p.source_type = 'PURCHASE_RECEIPT' and pr.id = p.source_id
				left join proc_purchase_order po on po.id = pr.order_id
				left join fin_expense e on p.source_type = 'EXPENSE' and e.id = p.source_id
				left join fin_purchase_invoice pi on p.source_type = 'OUTSOURCING_SETTLEMENT' and pi.id = p.source_id
				where p.id = ?
				for update of p
				""", (rs, rowNum) -> new PayableBalance(rs.getLong("id"), rs.getLong("supplier_id"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), rs.getBigDecimal("total_amount"),
				rs.getBigDecimal("paid_amount"), rs.getBigDecimal("adjusted_amount"), rs.getBigDecimal("unpaid_amount"),
				rs.getString("status"), rs.getLong("version")), id).stream().findFirst();
	}

	private VoucherSource voucherSource(String sourceType, Long sourceId) {
		if ("SALES_INVOICE".equals(sourceType)) {
			InvoiceRow invoice = salesInvoiceRow(sourceId).orElseThrow(this::invoiceNotFound);
			if (!"CONFIRMED".equals(invoice.status())) {
				throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
			}
			String customerName = customer(invoice.partyId()).map(CustomerRow::name).orElse(null);
			return new VoucherSource("SALES_INVOICE", invoice.id(), invoice.businessDate(), "销售发票 "
					+ invoice.documentNo(), "CUSTOMER", invoice.partyId(), customerName, invoice.ownershipType(),
					invoice.projectId(), invoice.taxIncludedAmount(), "RECEIVABLE_DRAFT", "SALES_INCOME_DRAFT",
					invoice.version(), invoice.documentNo());
		}
		if ("PURCHASE_INVOICE".equals(sourceType)) {
			InvoiceRow invoice = purchaseInvoiceRow(sourceId).orElseThrow(this::invoiceNotFound);
			if (!"CONFIRMED".equals(invoice.status())) {
				throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
			}
			String supplierName = supplier(invoice.partyId()).map(SupplierRow::name).orElse(null);
			return new VoucherSource("PURCHASE_INVOICE", invoice.id(), invoice.businessDate(), "采购发票 "
					+ invoice.documentNo(), "SUPPLIER", invoice.partyId(), supplierName, invoice.ownershipType(),
					invoice.projectId(), invoice.taxIncludedAmount(), "PURCHASE_SETTLEMENT_DRAFT",
					"PAYABLE_DRAFT", invoice.version(), invoice.documentNo());
		}
		if ("EXPENSE".equals(sourceType)) {
			ExpenseRow expense = expenseRow(sourceId).orElseThrow(this::expenseNotFound);
			if (!"CONFIRMED".equals(expense.status())) {
				throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
			}
			String supplierName = supplier(expense.partyId()).map(SupplierRow::name).orElse(null);
			return new VoucherSource("EXPENSE", expense.id(), expense.businessDate(), "费用单 "
					+ expense.documentNo(), "SUPPLIER", expense.partyId(), supplierName, expense.ownershipType(),
					expense.projectId(), expense.taxIncludedAmount(), "EXPENSE_DRAFT", "PAYABLE_DRAFT",
					expense.version(), expense.documentNo());
		}
		if ("RECEIPT".equals(sourceType)) {
			CashRow receipt = advanceReceiptRow(sourceId).orElseThrow(this::receiptNotFound);
			if (!"POSTED".equals(receipt.status())) {
				throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
			}
			String customerName = customer(receipt.partyId()).map(CustomerRow::name).orElse(null);
			return new VoucherSource("RECEIPT", receipt.id(), receipt.businessDate(), "收款 " + receipt.documentNo(),
					"CUSTOMER", receipt.partyId(), customerName, receipt.ownershipType(), receipt.projectId(),
					receipt.amount(), "CASH_DRAFT", "ADVANCE_RECEIPT_DRAFT", receipt.version(), receipt.documentNo());
		}
		if ("PAYMENT".equals(sourceType)) {
			CashRow payment = prepaymentRow(sourceId).orElseThrow(this::paymentNotFound);
			if (!"POSTED".equals(payment.status())) {
				throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
			}
			String supplierName = supplier(payment.partyId()).map(SupplierRow::name).orElse(null);
			return new VoucherSource("PAYMENT", payment.id(), payment.businessDate(), "付款 " + payment.documentNo(),
					"SUPPLIER", payment.partyId(), supplierName, payment.ownershipType(), payment.projectId(),
					payment.amount(), "PREPAYMENT_DRAFT", "CASH_DRAFT", payment.version(), payment.documentNo());
		}
		if ("SETTLEMENT_ALLOCATION".equals(sourceType)) {
			SettlementAllocationRow allocation = settlementAllocationRow(sourceId).orElseThrow(this::allocationNotFound);
			if (!"POSTED".equals(allocation.status())) {
				throw new BusinessException(ApiErrorCode.FINANCE_STATUS_NOT_ALLOWED);
			}
			boolean receivableSide = "RECEIVABLE".equals(allocation.settlementSide());
			String partyName = receivableSide ? customer(allocation.partyId()).map(CustomerRow::name).orElse(null)
					: supplier(allocation.partyId()).map(SupplierRow::name).orElse(null);
			return new VoucherSource("SETTLEMENT_ALLOCATION", allocation.id(), allocation.businessDate(),
					"核销 " + allocation.documentNo(), receivableSide ? "CUSTOMER" : "SUPPLIER", allocation.partyId(),
					partyName, allocation.ownershipType(), allocation.projectId(), allocation.totalAmount(),
					"SETTLEMENT_CLEARING_DRAFT", "SETTLEMENT_CLEARING_DRAFT", allocation.version(),
					allocation.documentNo());
		}
		throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
	}

	private Optional<Long> existingVoucherDraft(String sourceType, Long sourceId) {
		return this.jdbcTemplate.queryForList("""
				select id
				from fin_voucher_draft
				where source_type = ?
				and source_id = ?
				""", Long.class, sourceType, sourceId).stream().findFirst();
	}

	private String voucherSourceNo(String sourceType, Long sourceId) {
		return switch (sourceType) {
			case "SALES_INVOICE" -> salesInvoiceRow(sourceId).map(InvoiceRow::documentNo).orElse(null);
			case "PURCHASE_INVOICE" -> purchaseInvoiceRow(sourceId).map(InvoiceRow::documentNo).orElse(null);
			case "EXPENSE" -> expenseRow(sourceId).map(ExpenseRow::documentNo).orElse(null);
			case "RECEIPT" -> advanceReceiptRow(sourceId).map(CashRow::documentNo).orElse(null);
			case "PAYMENT" -> prepaymentRow(sourceId).map(CashRow::documentNo).orElse(null);
			case "SETTLEMENT_ALLOCATION" -> settlementAllocationRow(sourceId).map(SettlementAllocationRow::documentNo)
				.orElse(null);
			default -> null;
		};
	}

	private Optional<Long> existingReceivableForShipment(Long shipmentId) {
		return this.jdbcTemplate.queryForList("""
				select id
				from fin_receivable
				where source_type = 'SALES_SHIPMENT'
				and source_id = ?
				and status <> 'CANCELLED'
				order by id asc
				limit 1
				""", Long.class, shipmentId).stream().findFirst();
	}

	private Optional<Long> existingPayable(String sourceType, Long sourceId) {
		return this.jdbcTemplate.queryForList("""
				select id
				from fin_payable
				where source_type = ?
				and source_id = ?
				and status <> 'CANCELLED'
				order by id asc
				limit 1
				""", Long.class, sourceType, sourceId).stream().findFirst();
	}

	private boolean activeDocumentExists(String tableName, String sourceType, Long sourceId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from " + tableName
				+ " where source_type = ? and source_id = ? and status <> 'CANCELLED'", Long.class, sourceType,
				sourceId);
		return count != null && count > 0;
	}

	private Optional<Long> idempotentDocument(String tableName, String operator, String key, String requestFingerprint) {
		validateIdempotencyKey(key);
		return this.jdbcTemplate.query("select id, request_fingerprint from " + tableName
				+ " where created_by = ? and idempotency_key = ? order by id asc limit 1",
				(rs, rowNum) -> new IdempotentDocument(rs.getLong("id"), rs.getString("request_fingerprint")),
				operator, key.trim()).stream().findFirst().map((document) -> {
					if (!requestFingerprint.equals(document.requestFingerprint())) {
						throw new BusinessException(ApiErrorCode.FINANCE_IDEMPOTENCY_CONFLICT);
					}
					return document.id();
				});
	}

	private Optional<Long> idempotentCashDocument(String tableName, String operator, String key,
			String requestFingerprint) {
		validateIdempotencyKey(key);
		String docType = "fin_receipt".equals(tableName) ? "RECEIPT" : "PAYMENT";
		return this.jdbcTemplate.query("""
				select document_id, request_fingerprint
				from fin_cash_idempotency
				where document_type = ?
				and created_by = ?
				and idempotency_key = ?
				order by document_id asc
				limit 1
				""", (rs, rowNum) -> new IdempotentDocument(rs.getLong("document_id"),
				rs.getString("request_fingerprint")), docType, operator, key.trim()).stream().findFirst()
			.map((document) -> {
				if (!requestFingerprint.equals(document.requestFingerprint())) {
					throw new BusinessException(ApiErrorCode.FINANCE_IDEMPOTENCY_CONFLICT);
				}
				return document.id();
			});
	}

	private void insertCashIdempotency(String documentType, Long documentId, String operator, String key,
			String requestFingerprint) {
		validateIdempotencyKey(key);
		this.jdbcTemplate.update("""
				insert into fin_cash_idempotency (
					document_type, document_id, created_by, idempotency_key, request_fingerprint, created_at
				)
				values (?, ?, ?, ?, ?, now())
				""", documentType, documentId, operator, key.trim(), requestFingerprint);
	}

	private ActionRequestContext actionRequest(CurrentUser currentUser, String action, String resourceType,
			Long resourceId, FinanceActionRequest request) {
		requireActionMetadata(request == null ? null : request.version(),
				request == null ? null : request.idempotencyKey());
		String idempotencyKey = request.idempotencyKey().trim();
		String requestFingerprint = fingerprint(action, resourceType, resourceId, request.version(), request.reason());
		return actionRequest(currentUser, action, resourceType, resourceId, request.version(), idempotencyKey,
				requestFingerprint);
	}

	private ActionRequestContext mutationRequest(CurrentUser currentUser, String action, String resourceType,
			Long resourceId, Long version, String idempotencyKey, String requestFingerprint) {
		requireMutationMetadata(version, idempotencyKey);
		return actionRequest(currentUser, action, resourceType, resourceId, version, idempotencyKey.trim(),
				requestFingerprint);
	}

	private ActionRequestContext actionRequest(CurrentUser currentUser, String action, String resourceType,
			Long resourceId, Long version, String idempotencyKey, String requestFingerprint) {
		Optional<ActionRecord> existing = this.jdbcTemplate.query("""
				select result_resource_type, result_resource_id, result_version, request_fingerprint
				from fin_stage028_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ActionRecord(rs.getString("result_resource_type"),
				rs.getLong("result_resource_id"), rs.getLong("result_version"), rs.getString("request_fingerprint")),
				currentUser.id(), action, resourceType, resourceId == null ? 0L : resourceId, idempotencyKey).stream()
			.findFirst();
		if (existing.isPresent()) {
			ActionRecord record = existing.get();
			if (!requestFingerprint.equals(record.requestFingerprint())) {
				throw new BusinessException(ApiErrorCode.FINANCE_IDEMPOTENCY_CONFLICT);
			}
			return new ActionRequestContext(action, resourceType, resourceId, version, idempotencyKey,
					requestFingerprint, record.resultResourceType(), record.resultResourceId(), record.resultVersion());
		}
		return new ActionRequestContext(action, resourceType, resourceId, version, idempotencyKey, requestFingerprint,
				null, null, null);
	}

	private void recordAction(ActionRequestContext action, String resultResourceType, Long resultResourceId,
			Long resultVersion, CurrentUser currentUser) {
		try {
			this.jdbcTemplate.update("""
					insert into fin_stage028_action_idempotency (
						operator_user_id, operator_username, action, resource_type, resource_id, resource_version,
						idempotency_key, request_fingerprint, result_resource_type, result_resource_id, result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", currentUser.id(), currentUser.username(), action.action(), action.resourceType(),
					action.resourceId() == null ? 0L : action.resourceId(), action.version(), action.idempotencyKey(),
					action.requestFingerprint(), resultResourceType, resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.FINANCE_IDEMPOTENCY_CONFLICT);
		}
	}

	private PartySnapshot customerSnapshot(Long customerId) {
		return this.jdbcTemplate.query("""
				select coalesce(t.invoice_title, c.name) as invoice_title, t.tax_no, t.bank_name, t.bank_account,
				       t.invoice_type, t.default_tax_rate
				from mst_customer c
				left join mst_customer_settlement_tax t on t.customer_id = c.id
				where c.id = ?
				""", this::mapPartySnapshot, customerId).stream().findFirst().orElse(new PartySnapshot(null, null,
				null, null, null, null));
	}

	private PartySnapshot supplierSnapshot(Long supplierId) {
		return this.jdbcTemplate.query("""
				select coalesce(t.invoice_title, s.name) as invoice_title, t.tax_no, t.bank_name, t.bank_account,
				       t.invoice_type, t.default_tax_rate
				from mst_supplier s
				left join mst_supplier_settlement_tax t on t.supplier_id = s.id
				where s.id = ?
				""", this::mapPartySnapshot, supplierId).stream().findFirst().orElse(new PartySnapshot(null, null,
				null, null, null, null));
	}

	private Map<String, Object> partySnapshot(Long partyId, boolean customer, boolean sensitiveVisible) {
		PartySnapshot snapshot = customer ? customerSnapshot(partyId) : supplierSnapshot(partyId);
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("invoiceTitle", snapshot.invoiceTitle());
		map.put("taxNo", sensitiveVisible ? snapshot.taxNo() : "******");
		map.put("bankName", snapshot.bankName());
		map.put("bankAccount", sensitiveVisible ? snapshot.bankAccount() : "******");
		map.put("invoiceType", snapshot.invoiceType());
		map.put("defaultTaxRate", snapshot.defaultTaxRate());
		return map;
	}

	private List<String> restrictedReasons(CurrentUser currentUser) {
		return hasPermission(currentUser, "finance:settlement-sensitive:view") ? List.of()
				: List.of("finance:settlement-sensitive:view");
	}

	private Optional<CustomerRow> customer(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, name, status
				from mst_customer
				where id = ?
				""", (rs, rowNum) -> new CustomerRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getString("status")), id).stream().findFirst();
	}

	private Optional<SupplierRow> supplier(Long id) {
		return this.jdbcTemplate.query("""
				select id, code, name, status
				from mst_supplier
				where id = ?
				""", (rs, rowNum) -> new SupplierRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getString("status")), id).stream().findFirst();
	}

	private DocumentAmounts salesAmounts(List<SalesSourceLine> lines) {
		return new DocumentAmounts(money(lines.stream().map(SalesSourceLine::taxExcludedAmount).reduce(ZERO,
				BigDecimal::add)), money(lines.stream().map(SalesSourceLine::taxAmount).reduce(ZERO, BigDecimal::add)),
				money(lines.stream().map(SalesSourceLine::taxIncludedAmount).reduce(ZERO, BigDecimal::add)));
	}

	private DocumentAmounts invoiceLineAmounts(List<ValidatedInvoiceLine> lines) {
		return new DocumentAmounts(money(lines.stream().map(ValidatedInvoiceLine::taxExcludedAmount).reduce(ZERO,
				BigDecimal::add)), money(lines.stream().map(ValidatedInvoiceLine::taxAmount).reduce(ZERO,
				BigDecimal::add)), money(lines.stream().map(ValidatedInvoiceLine::taxIncludedAmount).reduce(ZERO,
				BigDecimal::add)));
	}

	private DocumentAmounts purchaseSourceAmounts(PurchaseSource source) {
		List<PurchaseSourceLine> lines = purchaseSourceLines(source);
		return new DocumentAmounts(money(lines.stream().map(PurchaseSourceLine::taxExcludedAmount).reduce(ZERO,
				BigDecimal::add)), money(lines.stream().map(PurchaseSourceLine::taxAmount).reduce(ZERO,
				BigDecimal::add)), money(lines.stream().map(PurchaseSourceLine::taxIncludedAmount).reduce(ZERO,
				BigDecimal::add)));
	}

	private DocumentAmounts expenseAmounts(List<ValidatedExpenseLine> lines) {
		return new DocumentAmounts(money(lines.stream().map(ValidatedExpenseLine::taxExcludedAmount).reduce(ZERO,
				BigDecimal::add)), money(lines.stream().map(ValidatedExpenseLine::taxAmount).reduce(ZERO,
				BigDecimal::add)), money(lines.stream().map(ValidatedExpenseLine::taxIncludedAmount).reduce(ZERO,
				BigDecimal::add)));
	}

	private List<ValidatedInvoiceLine> validateInvoiceLines(List<InvoiceLineRequest> requests,
			List<PurchaseSourceLine> sourceLines, Long ignoredInvoiceId) {
		Map<Long, PurchaseSourceLine> sourceLineById = sourceLineById(sourceLines);
		List<ValidatedInvoiceLine> result = new ArrayList<>();
		Set<Long> requestedLineIds = new HashSet<>();
		for (InvoiceLineRequest request : requests) {
			if (request == null || request.sourceLineId() == null || !sourceLineById.containsKey(request.sourceLineId())
					|| !requestedLineIds.add(request.sourceLineId())) {
				throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
			}
			ValidatedInvoiceLine line = new ValidatedInvoiceLine(request.sourceLineId(), validateQuantity(request.quantity()),
					validateRate(request.taxRate()), validateUnitPrice(request.taxExcludedUnitPrice()),
					validateUnitPrice(request.taxIncludedUnitPrice()), validateMoney(request.taxExcludedAmount()),
					money(nonNull(request.taxAmount())), validateMoney(request.taxIncludedAmount()));
			if (line.taxExcludedAmount().add(line.taxAmount()).compareTo(line.taxIncludedAmount()) != 0) {
				throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
			}
			PurchaseSourceLine sourceLine = sourceLineById.get(line.sourceLineId());
			BigDecimal alreadyInvoiced = invoicedPurchaseQuantity(line.sourceLineId(), ignoredInvoiceId);
			if (alreadyInvoiced.add(line.quantity()).compareTo(sourceLine.quantity()) > 0) {
				throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_OVER_INVOICED);
			}
			result.add(line);
		}
		return result;
	}

	private List<ValidatedExpenseLine> validateExpenseLines(List<ExpenseLineRequest> requests, Long supplierId,
			String ownershipType, Long projectId) {
		List<ValidatedExpenseLine> result = new ArrayList<>();
		for (ExpenseLineRequest request : requests) {
			if (request == null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			String expenseCategory = hasText(request.expenseCategory()) ? request.expenseCategory()
					: expenseCategoryCode(request.categoryId());
			BigDecimal taxExcludedAmount = request.taxExcludedAmount() == null ? request.pretaxAmount()
					: request.taxExcludedAmount();
			BigDecimal taxIncludedAmount = request.taxIncludedAmount() == null ? request.totalAmount()
					: request.taxIncludedAmount();
			if (!hasText(expenseCategory)) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			String sourceType = validateOptionalText(request.sourceType(), 64);
			String sourceNo = validateOptionalText(request.sourceNo(), 64);
			BigDecimal normalizedTaxIncludedAmount = validateMoney(taxIncludedAmount);
			if (hasText(sourceType)) {
				if (request.sourceId() == null
						|| !(PURCHASE_RECEIPT.equals(sourceType) || OUTSOURCING_RECEIPT.equals(sourceType))) {
					throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
				}
				PurchaseSource source = purchaseSource(PURCHASE_RECEIPT.equals(sourceType) ? "STANDARD_PURCHASE"
						: "OUTSOURCING", sourceType, request.sourceId());
				if (!"POSTED".equals(source.status()) || !source.supplierId().equals(supplierId)
						|| !source.ownershipType().equals(ownershipType)
						|| !sameProject(source.projectId(), projectId)) {
					throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
				}
				BigDecimal sourceAmount = money(purchaseSourceLines(source).stream()
					.map(PurchaseSourceLine::taxIncludedAmount)
					.reduce(ZERO, BigDecimal::add));
				if (normalizedTaxIncludedAmount.compareTo(sourceAmount) > 0) {
					throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
				}
				sourceNo = source.sourceNo();
			}
			ValidatedExpenseLine line = new ValidatedExpenseLine(validateOptionalText(expenseCategory, 64),
					validateOptionalText(request.description(), 255), sourceType, request.sourceId(), sourceNo,
					validateRate(request.taxRate()), validateMoney(taxExcludedAmount), money(nonNull(request.taxAmount())),
					normalizedTaxIncludedAmount);
			if (line.taxExcludedAmount().add(line.taxAmount()).compareTo(line.taxIncludedAmount()) != 0) {
				throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
			}
			result.add(line);
		}
		return result;
	}

	private String expenseCategoryCode(Long categoryId) {
		if (categoryId == null) {
			return null;
		}
		return switch (categoryId.intValue()) {
			case 1 -> "SERVICE";
			case 2 -> "FREIGHT";
			case 3 -> "OUTSOURCING_SERVICE";
			case 4 -> "OTHER";
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private MatchResult matchResult(String settlementKind, List<PurchaseSourceLine> sourceLines,
			List<ValidatedInvoiceLine> invoiceLines) {
		if ("OUTSOURCING".equals(settlementKind)) {
			return new MatchResult("NOT_APPLICABLE", List.of());
		}
		Map<Long, PurchaseSourceLine> sourceLineById = sourceLineById(sourceLines);
		List<MatchDifference> differences = new ArrayList<>();
		Set<Long> matchedSourceLines = new HashSet<>();
		for (ValidatedInvoiceLine line : invoiceLines) {
			PurchaseSourceLine sourceLine = sourceLineById.get(line.sourceLineId());
			if (sourceLine == null) {
				differences.add(new MatchDifference(null, "EXTRA_SOURCE_LINE", "不存在", line.sourceLineId().toString(),
						"标准采购发票包含非当前来源行"));
				continue;
			}
			if (!matchedSourceLines.add(line.sourceLineId())) {
				differences.add(new MatchDifference(null, "DUPLICATE_SOURCE_LINE", "唯一", line.sourceLineId().toString(),
						"标准采购发票来源行重复"));
				continue;
			}
			InvoiceLineRequest expected = invoiceLineRequest(sourceLine, line.quantity(), sourceLine.taxRate());
			addDifference(differences, sourceLine.id(), "TAX_RATE", expected.taxRate(), line.taxRate());
			addDifference(differences, sourceLine.id(), "TAX_EXCLUDED_UNIT_PRICE", expected.taxExcludedUnitPrice(),
					line.taxExcludedUnitPrice());
			addDifference(differences, sourceLine.id(), "TAX_INCLUDED_UNIT_PRICE", expected.taxIncludedUnitPrice(),
					line.taxIncludedUnitPrice());
			addDifference(differences, sourceLine.id(), "TAX_EXCLUDED_AMOUNT", expected.taxExcludedAmount(),
					line.taxExcludedAmount());
			addDifference(differences, sourceLine.id(), "TAX_AMOUNT", expected.taxAmount(), line.taxAmount());
			addDifference(differences, sourceLine.id(), "TAX_INCLUDED_AMOUNT", expected.taxIncludedAmount(),
					line.taxIncludedAmount());
		}
		return new MatchResult(differences.isEmpty() ? "MATCHED" : "EXCEPTION", differences);
	}

	private void addDifference(List<MatchDifference> differences, Long sourceLineId, String type, BigDecimal expected,
			BigDecimal actual) {
		if (expected.compareTo(actual) == 0) {
			return;
		}
		differences.add(new MatchDifference(null, type, expected.toPlainString(), actual.toPlainString(),
				"标准采购发票零容差匹配失败：" + type));
	}

	private Map<Long, PurchaseSourceLine> sourceLineById(List<PurchaseSourceLine> sourceLines) {
		Map<Long, PurchaseSourceLine> result = new LinkedHashMap<>();
		for (PurchaseSourceLine sourceLine : sourceLines) {
			result.put(sourceLine.id(), sourceLine);
		}
		return result;
	}

	private String salesSourceType(SalesInvoiceCreateRequest request) {
		return hasText(request.sourceType()) ? request.sourceType() : SALES_SHIPMENT;
	}

	private Long salesSourceId(SalesInvoiceCreateRequest request) {
		if (request.sourceId() != null) {
			return request.sourceId();
		}
		if (request.sourceLines() == null || request.sourceLines().isEmpty()) {
			return null;
		}
		Long sourceLineId = request.sourceLines().get(0).sourceLineId();
		if (sourceLineId == null) {
			return null;
		}
		return this.jdbcTemplate.queryForList("""
				select shipment_id
				from sal_sales_shipment_line
				where id = ?
				""", Long.class, sourceLineId).stream().findFirst().orElse(null);
	}

	private LocalDate defaultDueDate(LocalDate dueDate, LocalDate businessDate) {
		return dueDate == null ? businessDate : dueDate;
	}

	private void validateSalesRequestSource(SalesInvoiceCreateRequest request, ShipmentSource source) {
		if (request.customerId() != null && !request.customerId().equals(source.customerId())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
		}
		if (hasText(request.ownershipType()) && !ownershipType(request.ownershipType()).equals(source.ownershipType())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
		}
		if (request.projectId() != null && !request.projectId().equals(source.projectId())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
		}
	}

	private String purchaseSettlementKind(PurchaseInvoiceCreateRequest request) {
		if (hasText(request.settlementKind())) {
			return request.settlementKind();
		}
		if (OUTSOURCING_RECEIPT.equals(request.sourceType())) {
			return "OUTSOURCING";
		}
		return "STANDARD_PURCHASE";
	}

	private String purchaseSourceType(PurchaseInvoiceCreateRequest request, String settlementKind) {
		if (hasText(request.sourceType())) {
			return request.sourceType();
		}
		return "OUTSOURCING".equals(settlementKind) ? OUTSOURCING_RECEIPT : PURCHASE_RECEIPT;
	}

	private Long purchaseSourceId(PurchaseInvoiceCreateRequest request, String sourceType) {
		if (request.sourceId() != null) {
			return request.sourceId();
		}
		if (request.sourceLines() == null || request.sourceLines().isEmpty()) {
			return null;
		}
		Long sourceLineId = purchaseRequestSourceLineId(request.sourceLines().get(0));
		if (sourceLineId == null) {
			return null;
		}
		String tableName = PURCHASE_RECEIPT.equals(sourceType) ? "proc_purchase_receipt_line"
				: "mfg_outsourcing_receipt_line";
		return this.jdbcTemplate.queryForList("select receipt_id from " + tableName + " where id = ?", Long.class,
				sourceLineId).stream().findFirst().orElse(null);
	}

	private void validatePurchaseRequestSource(PurchaseInvoiceCreateRequest request, PurchaseSource source) {
		if (request.supplierId() != null && !request.supplierId().equals(source.supplierId())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
		}
		if (hasText(request.ownershipType()) && !ownershipType(request.ownershipType()).equals(source.ownershipType())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
		}
		if (request.projectId() != null && !request.projectId().equals(source.projectId())) {
			throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
		}
	}

	private List<InvoiceLineRequest> purchaseRequestLines(PurchaseInvoiceCreateRequest request, PurchaseSource source,
			List<PurchaseSourceLine> sourceLines) {
		if (request.lines() != null && !request.lines().isEmpty()) {
			return request.lines();
		}
		if (request.sourceLines() == null || request.sourceLines().isEmpty()) {
			return sourceLines.stream().map((line) -> invoiceLineRequest(line, line.quantity(), line.taxRate()))
				.toList();
		}
		Map<Long, PurchaseSourceLine> sourceLineById = sourceLineById(sourceLines);
		List<InvoiceLineRequest> result = new ArrayList<>();
		for (PurchaseInvoiceSourceLineRequest requestLine : request.sourceLines()) {
			Long sourceLineId = purchaseRequestSourceLineId(requestLine);
			if (sourceLineId == null || !sourceLineById.containsKey(sourceLineId)) {
				throw new BusinessException(ApiErrorCode.FINANCE_SOURCE_MISMATCH);
			}
			PurchaseSourceLine sourceLine = sourceLineById.get(sourceLineId);
			BigDecimal quantity = requestLine.invoiceQuantity() == null ? sourceLine.quantity()
					: validateQuantity(requestLine.invoiceQuantity());
			BigDecimal taxRate = requestLine.taxRate() == null ? sourceLine.taxRate() : validateRate(requestLine.taxRate());
			result.add(invoiceLineRequest(sourceLine, quantity, taxRate));
		}
		return result;
	}

	private InvoiceLineRequest invoiceLineRequest(PurchaseSourceLine sourceLine, BigDecimal quantity, BigDecimal taxRate) {
		if (quantity.compareTo(sourceLine.quantity()) > 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		BigDecimal taxExcludedAmount = money(quantity.multiply(sourceLine.taxExcludedUnitPrice()));
		BigDecimal taxAmount = money(quantity.multiply(sourceLine.taxIncludedUnitPrice()
			.subtract(sourceLine.taxExcludedUnitPrice())));
		BigDecimal taxIncludedAmount = money(taxExcludedAmount.add(taxAmount));
		return new InvoiceLineRequest(sourceLine.id(), quantity, taxRate, sourceLine.taxExcludedUnitPrice(),
				sourceLine.taxIncludedUnitPrice(), taxExcludedAmount, taxAmount, taxIncludedAmount);
	}

	private Long purchaseRequestSourceLineId(PurchaseInvoiceSourceLineRequest request) {
		if (request == null) {
			return null;
		}
		if (request.sourceLineId() != null) {
			return request.sourceLineId();
		}
		if (request.receiptLineId() != null) {
			return request.receiptLineId();
		}
		if (request.outsourcingReceiptLineId() != null) {
			return request.outsourcingReceiptLineId();
		}
		return request.orderLineId();
	}

	private String purchaseExternalInvoiceNo(PurchaseInvoiceCreateRequest request) {
		return hasText(request.supplierInvoiceNo()) ? request.supplierInvoiceNo() : request.externalInvoiceNo();
	}

	private LocalDate expenseBusinessDate(ExpenseCreateRequest request) {
		return request.expenseDate() == null ? request.businessDate() : request.expenseDate();
	}

	private Long advanceCustomerId(AdvanceReceiptRequest request) {
		if (request == null) {
			return null;
		}
		return request.customerId() == null ? request.partnerId() : request.customerId();
	}

	private LocalDate advanceReceiptDate(AdvanceReceiptRequest request) {
		if (request == null) {
			return null;
		}
		return request.receiptDate() == null ? request.businessDate() : request.receiptDate();
	}

	private Long prepaymentSupplierId(PrepaymentRequest request) {
		if (request == null) {
			return null;
		}
		return request.supplierId() == null ? request.partnerId() : request.supplierId();
	}

	private LocalDate prepaymentDate(PrepaymentRequest request) {
		if (request == null) {
			return null;
		}
		return request.paymentDate() == null ? request.businessDate() : request.paymentDate();
	}

	private SettlementAllocationRequest normalizeSettlementRequest(SettlementAllocationRequest request) {
		if (request == null || hasText(request.settlementSide())) {
			return request;
		}
		if (!hasText(request.direction()) || request.funds() == null || request.funds().size() != 1
				|| request.targets() == null || request.targets().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String side;
		String cashSourceType;
		String targetType;
		if ("CUSTOMER".equals(request.direction())) {
			side = "RECEIVABLE";
			cashSourceType = "RECEIPT";
			targetType = "RECEIVABLE";
		}
		else if ("SUPPLIER".equals(request.direction())) {
			side = "PAYABLE";
			cashSourceType = "PAYMENT";
			targetType = "PAYABLE";
		}
		else {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		SettlementFundRequest fund = request.funds().get(0);
		if (fund == null || fund.fundId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		List<SettlementAllocationLineRequest> lines = new ArrayList<>();
		for (SettlementTargetRequest target : request.targets()) {
			if (target == null || target.targetId() == null || target.amount() == null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			String normalizedTargetType = hasText(target.targetType()) ? target.targetType() : targetType;
			lines.add(new SettlementAllocationLineRequest(normalizedTargetType, target.targetId(), target.amount()));
		}
		LocalDate businessDate = request.businessDate() == null ? LocalDate.now() : request.businessDate();
		return new SettlementAllocationRequest(side, cashSourceType, fund.fundId(), businessDate,
				request.idempotencyKey(), request.remark(), request.version(), lines, request.direction(), request.partnerId(),
				request.ownershipType(), request.projectId(), request.funds(), request.targets());
	}

	private boolean activeDocumentExistsExcept(String tableName, String sourceType, Long sourceId, Long ignoredId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from " + tableName
				+ " where source_type = ? and source_id = ? and status <> 'CANCELLED' and id <> ?", Long.class,
				sourceType, sourceId, ignoredId);
		return count != null && count > 0;
	}

	private Map<String, Object> invoiceMap(InvoiceRow row) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put("invoiceNo", row.documentNo());
		response.put("partyId", row.partyId());
		response.put("ownershipType", row.ownershipType());
		response.put("projectId", row.projectId());
		response.put("sourceType", row.sourceType());
		response.put("sourceId", row.sourceId());
		response.put("sourceNo", row.sourceNo());
		response.put("invoiceDate", row.businessDate());
		response.put("dueDate", row.dueDate());
		response.put("invoiceType", row.invoiceType());
		response.put("taxExcludedAmount", decimalString(row.taxExcludedAmount()));
		response.put("taxAmount", decimalString(row.taxAmount()));
		response.put("taxIncludedAmount", decimalString(row.taxIncludedAmount()));
		response.put("pretaxAmount", decimalString(row.taxExcludedAmount()));
		response.put("totalAmount", decimalString(row.taxIncludedAmount()));
		response.put("settlementStatus", row.linkedReceivableId() != null || row.linkedPayableId() != null
				? "UNSETTLED" : "UNLINKED");
		response.put("unsettledAmount", decimalString(row.taxIncludedAmount()));
		response.put("status", row.status());
		response.put("version", row.version());
		return response;
	}

	private Map<String, Object> cashMap(CashRow row, boolean receivableSide, CurrentUser currentUser) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put(receivableSide ? "receiptNo" : "paymentNo", row.documentNo());
		response.put(receivableSide ? "advanceNo" : "prepaymentNo", row.documentNo());
		response.put("fundNo", row.documentNo());
		response.put(receivableSide ? "customerId" : "supplierId", row.partyId());
		response.put("partnerId", row.partyId());
		response.put("ownershipType", row.ownershipType());
		response.put("projectId", row.projectId());
		response.put(receivableSide ? "receiptDate" : "paymentDate", row.businessDate());
		response.put("businessDate", row.businessDate());
		response.put("amount", decimalString(row.amount()));
		response.put("method", row.method());
		response.put("status", row.status());
		response.put("originalAmount", decimalString(row.originalAmount()));
		response.put("allocatedAmount", decimalString(row.allocatedAmount()));
		response.put("availableAmount", decimalString(row.availableAmount()));
		response.put("version", row.version());
		response.put("allowedActions", allowedCashActions(row.status(), receivableSide, currentUser));
		return response;
	}

	private List<String> allowedInvoiceActions(String status, CurrentUser currentUser, String permissionPrefix) {
		if (!"DRAFT".equals(status)) {
			return List.of();
		}
		List<String> actions = new ArrayList<>();
		if (hasPermission(currentUser, permissionPrefix + ":update")) {
			actions.add("UPDATE");
		}
		if (hasPermission(currentUser, permissionPrefix + ":confirm")) {
			actions.add("CONFIRM");
		}
		if (hasPermission(currentUser, permissionPrefix + ":cancel")) {
			actions.add("CANCEL");
		}
		if (hasPermission(currentUser, permissionPrefix + ":match")) {
			actions.add("MATCH");
		}
		return actions;
	}

	private List<String> allowedCashActions(String status, boolean receivableSide, CurrentUser currentUser) {
		if (!"DRAFT".equals(status)) {
			return List.of();
		}
		String prefix = receivableSide ? "finance:advance-receipt" : "finance:prepayment";
		List<String> actions = new ArrayList<>();
		if (hasPermission(currentUser, prefix + ":update")) {
			actions.add("UPDATE");
		}
		if (hasPermission(currentUser, prefix + ":post")) {
			actions.add("POST");
		}
		if (hasPermission(currentUser, prefix + ":cancel")) {
			actions.add("CANCEL");
		}
		return actions;
	}

	private List<String> allowedVoucherActions(String status, CurrentUser currentUser) {
		if ("DRAFT".equals(status)) {
			List<String> actions = new ArrayList<>();
			if (hasPermission(currentUser, "finance:voucher-draft:ready")) {
				actions.add("READY");
			}
			if (hasPermission(currentUser, "finance:voucher-draft:cancel")) {
				actions.add("CANCEL");
			}
			return actions;
		}
		if ("READY".equals(status) && hasPermission(currentUser, "finance:voucher-draft:cancel")) {
			return List.of("CANCEL");
		}
		return List.of();
	}

	private List<String> allowedSettlementActions(String status, CurrentUser currentUser) {
		if (!"DRAFT".equals(status)) {
			return List.of();
		}
		List<String> actions = new ArrayList<>();
		if (hasPermission(currentUser, "finance:settlement-allocation:update")) {
			actions.add("UPDATE");
		}
		if (hasPermission(currentUser, "finance:settlement-allocation:post")) {
			actions.add("POST");
		}
		if (hasPermission(currentUser, "finance:settlement-allocation:cancel")) {
			actions.add("CANCEL");
		}
		return actions;
	}

	private List<String> settlementRestrictedReasons(String status) {
		return "DRAFT".equals(status) ? List.of() : List.of("STATUS_LOCKED");
	}

	private String settlementSummary(SettlementAllocationRow row) {
		String fundNo = cashSourceNo(row.cashSourceType(), row.cashSourceId());
		int lineCount = settlementAllocationLineCount(row.id());
		String sourceNo = fundNo == null ? row.documentNo() : fundNo;
		return sourceNo + " 核销 " + lineCount + " 个目标";
	}

	private int settlementAllocationLineCount(Long allocationId) {
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from fin_settlement_allocation_line
				where allocation_id = ?
				""", Integer.class, allocationId);
		return count == null ? 0 : count;
	}

	private String cashSourceNo(String sourceType, Long sourceId) {
		if ("RECEIPT".equals(sourceType)) {
			return advanceReceiptRow(sourceId).map(CashRow::documentNo).orElse(null);
		}
		if ("PAYMENT".equals(sourceType)) {
			return prepaymentRow(sourceId).map(CashRow::documentNo).orElse(null);
		}
		return null;
	}

	private String settlementPartyName(String settlementSide, Long partyId) {
		if ("RECEIVABLE".equals(settlementSide)) {
			return customer(partyId).map(CustomerRow::name).orElse(null);
		}
		return supplier(partyId).map(SupplierRow::name).orElse(null);
	}

	private String projectName(Long projectId) {
		if (projectId == null) {
			return null;
		}
		return this.jdbcTemplate.queryForList("""
				select name
				from sal_project
				where id = ?
				""", String.class, projectId).stream().findFirst().orElse(null);
	}

	private String settlementSide(String direction) {
		return switch (direction) {
			case "CUSTOMER", "RECEIVABLE" -> "RECEIVABLE";
			case "SUPPLIER", "PAYABLE" -> "PAYABLE";
			default -> throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		};
	}

	private String settlementDirection(String settlementSide) {
		return "RECEIVABLE".equals(settlementSide) ? "CUSTOMER" : "SUPPLIER";
	}

	private List<String> settlementFundActions(CurrentUser currentUser) {
		return hasPermission(currentUser, "finance:settlement-allocation:create") ? List.of("ALLOCATE") : List.of();
	}

	private String lineMatchStatus(MatchResult result, String settlementKind) {
		if ("OUTSOURCING".equals(settlementKind)) {
			return "NOT_APPLICABLE";
		}
		return result.status();
	}

	private String invoiceType(String requested, String defaultType) {
		String value = hasText(requested) ? requested : defaultType;
		if (!hasText(value)) {
			return "NONE";
		}
		if (!List.of("GENERAL_VAT", "SPECIAL_VAT", "NONE").contains(value)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private void validateOwnership(String ownershipType, Long projectId, boolean projectAllowed) {
		String ownership = ownershipType(ownershipType);
		if ("PROJECT".equals(ownership) && projectId == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if ("PUBLIC".equals(ownership) && projectId != null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if ("PROJECT".equals(ownership) && !projectAllowed) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String ownershipType(String value) {
		if (!hasText(value)) {
			return "PUBLIC";
		}
		if (!List.of("PUBLIC", "PROJECT").contains(value)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private boolean sameProject(Long left, Long right) {
		return left == null ? right == null : left.equals(right);
	}

	private BigDecimal validateMoney(BigDecimal value) {
		if (value == null || value.scale() > 2 || value.compareTo(ZERO) <= 0 || integerDigits(value) > 16) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		return money(value);
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null || value.scale() > 6 || value.compareTo(BigDecimal.ZERO) <= 0 || integerDigits(value) > 12) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal validateRate(BigDecimal value) {
		BigDecimal rate = value == null ? BigDecimal.ZERO : value;
		if (rate.scale() > 6 || rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		return rate.setScale(6, RoundingMode.HALF_UP);
	}

	private BigDecimal validateUnitPrice(BigDecimal value) {
		if (value == null || value.scale() > 6 || value.compareTo(BigDecimal.ZERO) < 0 || integerDigits(value) > 12) {
			throw new BusinessException(ApiErrorCode.FINANCE_AMOUNT_INVALID);
		}
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private String validateOptionalText(String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private BigDecimal nonNull(BigDecimal value) {
		return value == null ? ZERO : value;
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private String decimalString(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

	private long integerDigits(BigDecimal value) {
		return Math.max(0L, (long) value.precision() - value.scale());
	}

	private String nextNo(String prefix, AtomicInteger sequence) {
		int value = Math.floorMod(sequence.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private void requireUser(CurrentUser currentUser, String permissionCode) {
		if (currentUser == null || !hasPermission(currentUser, permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void assertVersion(Long expectedVersion, Long actualVersion) {
		if (expectedVersion == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (!expectedVersion.equals(actualVersion)) {
			throw new BusinessException(ApiErrorCode.FINANCE_CONCURRENT_MODIFICATION);
		}
	}

	private void requireMutationMetadata(Long version, String idempotencyKey) {
		if (version == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateIdempotencyKey(idempotencyKey);
	}

	private void requireActionMetadata(Long version, String idempotencyKey) {
		if (version == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateIdempotencyKey(idempotencyKey);
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (!hasText(idempotencyKey) || idempotencyKey.trim().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String fingerprint(String action, Object... parts) {
		StringBuilder builder = new StringBuilder(action);
		for (Object part : parts) {
			builder.append('|').append(fingerprintPart(part));
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(builder.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private String fingerprintPart(Object part) {
		if (part == null) {
			return "";
		}
		if (part instanceof BigDecimal decimal) {
			return decimal.stripTrailingZeros().toPlainString();
		}
		if (part instanceof Iterable<?> iterable) {
			StringBuilder builder = new StringBuilder("[");
			for (Object value : iterable) {
				builder.append(fingerprintPart(value)).append(',');
			}
			return builder.append(']').toString();
		}
		return part.toString().trim();
	}

	private boolean hasPermission(CurrentUser currentUser, String permissionCode) {
		return currentUser != null && currentUser.permissions().contains(permissionCode);
	}

	private BusinessException duplicateFinanceException(DuplicateKeyException exception) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		if (message != null && (message.contains("idempotency") || message.contains("fin_cash_idempotency"))) {
			return new BusinessException(ApiErrorCode.FINANCE_IDEMPOTENCY_CONFLICT);
		}
		if (message != null && (message.contains("source") || message.contains("link"))) {
			return new BusinessException(ApiErrorCode.FINANCE_SOURCE_DUPLICATED);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private BusinessException invoiceNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_INVOICE_NOT_FOUND);
	}

	private BusinessException expenseNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_EXPENSE_NOT_FOUND);
	}

	private BusinessException allocationNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_SETTLEMENT_ALLOCATION_NOT_FOUND);
	}

	private BusinessException voucherDraftNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_VOUCHER_DRAFT_NOT_FOUND);
	}

	private BusinessException sourceNotFound() {
		return new BusinessException(ApiErrorCode.FINANCE_SOURCE_NOT_FOUND);
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

	private ShipmentSource mapShipmentSource(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentSource(rs.getLong("id"), rs.getString("shipment_no"), rs.getLong("order_id"),
				rs.getString("order_no"), rs.getLong("customer_id"), rs.getString("customer_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				ownershipType(rs.getString("ownership_type")), nullableLong(rs, "project_id"));
	}

	private SalesSourceLine mapSalesSourceLine(ResultSet rs, int rowNum) throws SQLException {
		return new SalesSourceLine(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("order_line_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("tax_rate"), rs.getBigDecimal("tax_excluded_unit_price"),
				rs.getBigDecimal("tax_included_unit_price"), rs.getBigDecimal("tax_excluded_amount"),
				rs.getBigDecimal("tax_amount"), rs.getBigDecimal("tax_included_amount"));
	}

	private PurchaseSourceLine mapPurchaseSourceLine(ResultSet rs, int rowNum) throws SQLException {
		return new PurchaseSourceLine(rs.getLong("id"), rs.getInt("line_no"), nullableLong(rs, "purchase_order_id"),
				nullableLong(rs, "purchase_order_line_id"), nullableLong(rs, "outsourcing_order_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("tax_rate"), rs.getBigDecimal("tax_excluded_unit_price"),
				rs.getBigDecimal("tax_included_unit_price"), rs.getBigDecimal("tax_excluded_amount"),
				rs.getBigDecimal("tax_amount"), rs.getBigDecimal("tax_included_amount"));
	}

	private InvoiceRow mapInvoiceRow(ResultSet rs, int rowNum) throws SQLException {
		return new InvoiceRow(rs.getLong("id"), rs.getString("document_no"), rs.getLong("party_id"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), rs.getString("source_type"),
				rs.getLong("source_id"), rs.getString("source_no"), rs.getObject("business_date", LocalDate.class),
				rs.getObject("due_date", LocalDate.class), rs.getString("invoice_type"),
				rs.getString("settlement_kind"), rs.getString("match_status"), rs.getBigDecimal("tax_excluded_amount"),
				rs.getBigDecimal("tax_amount"), rs.getBigDecimal("tax_included_amount"), rs.getString("status"),
				nullableLong(rs, "linked_receivable_id"), nullableLong(rs, "linked_payable_id"),
				rs.getLong("version"));
	}

	private ExpenseRow mapExpenseRow(ResultSet rs, int rowNum) throws SQLException {
		return new ExpenseRow(rs.getLong("id"), rs.getString("document_no"), rs.getLong("party_id"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				rs.getObject("business_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
				rs.getString("invoice_type"), rs.getBigDecimal("tax_excluded_amount"),
				rs.getBigDecimal("tax_amount"), rs.getBigDecimal("tax_included_amount"), rs.getString("status"),
				nullableLong(rs, "linked_payable_id"), rs.getLong("version"));
	}

	private CashRow mapCashRow(ResultSet rs, int rowNum) throws SQLException {
		return new CashRow(rs.getLong("id"), rs.getString("document_no"), rs.getLong("party_id"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getString("method"),
				rs.getString("status"), rs.getBigDecimal("original_amount"), rs.getBigDecimal("allocated_amount"),
				rs.getBigDecimal("available_amount"), rs.getLong("version"));
	}

	private SettlementAllocationRow mapSettlementAllocationRow(ResultSet rs, int rowNum) throws SQLException {
		return new SettlementAllocationRow(rs.getLong("id"), rs.getString("document_no"),
				rs.getString("settlement_side"), rs.getString("cash_source_type"), rs.getLong("cash_source_id"),
				rs.getLong("party_id"), rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("total_amount"),
				rs.getString("status"), rs.getLong("version"));
	}

	private VoucherDraftRow mapVoucherDraftRow(ResultSet rs, int rowNum) throws SQLException {
		return new VoucherDraftRow(rs.getLong("id"), rs.getString("document_no"), rs.getString("source_type"),
				rs.getLong("source_id"), rs.getString("status"), rs.getObject("business_date", LocalDate.class),
				rs.getString("summary"), rs.getString("party_type"), nullableLong(rs, "party_id"),
				rs.getString("party_name"), rs.getString("ownership_type"), nullableLong(rs, "project_id"),
				rs.getBigDecimal("debit_amount"), rs.getBigDecimal("credit_amount"),
				rs.getLong("generation_version"), rs.getString("formal_voucher_no"),
				rs.getString("posting_status"), rs.getLong("version"));
	}

	private CashBalance mapCashBalance(ResultSet rs, int rowNum) throws SQLException {
		return new CashBalance(rs.getLong("id"), rs.getLong("party_id"), rs.getString("ownership_type"),
				nullableLong(rs, "project_id"), rs.getBigDecimal("original_amount"),
				rs.getBigDecimal("allocated_amount"), rs.getBigDecimal("available_amount"), rs.getString("status"),
				rs.getLong("version"));
	}

	private PartySnapshot mapPartySnapshot(ResultSet rs, int rowNum) throws SQLException {
		return new PartySnapshot(rs.getString("invoice_title"), rs.getString("tax_no"), rs.getString("bank_name"),
				rs.getString("bank_account"), rs.getString("invoice_type"), rs.getBigDecimal("default_tax_rate"));
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
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

	@FunctionalInterface
	private interface DetailResolver {
		Map<String, Object> resolve(Long id);
	}

	private interface FinancePayableSource {
		Long id();

		String documentNo();

		Long partyId();

		String ownershipType();

		Long projectId();

		LocalDate businessDate();

		LocalDate dueDate();

		BigDecimal taxIncludedAmount();
	}

	public record SalesInvoiceCreateRequest(String sourceType, Long sourceId, Long customerId, String ownershipType,
			Long projectId, LocalDate invoiceDate, LocalDate dueDate, String invoiceType, String externalInvoiceNo,
			String idempotencyKey, String remark, Long version, List<SalesInvoiceSourceLineRequest> sourceLines) {
	}

	public record SalesInvoiceSourceLineRequest(Long sourceLineId, BigDecimal invoiceQuantity) {
	}

	public record PurchaseInvoiceCreateRequest(String settlementKind, String sourceType, Long sourceId, Long supplierId,
			String ownershipType, Long projectId, LocalDate invoiceDate, LocalDate dueDate, String invoiceType,
			String supplierInvoiceNo, String externalInvoiceNo, String idempotencyKey, String remark, Long version,
			List<InvoiceLineRequest> lines, List<PurchaseInvoiceSourceLineRequest> sourceLines) {
	}

	public record PurchaseInvoiceSourceLineRequest(Long sourceLineId, Long receiptLineId,
			Long outsourcingReceiptLineId, Long orderLineId, BigDecimal invoiceQuantity, BigDecimal taxRate) {
	}

	public record InvoiceLineRequest(Long sourceLineId, BigDecimal quantity, BigDecimal taxRate,
			BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount) {
	}

	public record ExpenseCreateRequest(Long supplierId, String ownershipType, Long projectId, Long categoryId,
			LocalDate expenseDate, LocalDate businessDate, LocalDate dueDate, String invoiceType, String idempotencyKey,
			String remark, Long version, List<ExpenseLineRequest> lines) {
	}

	public record ExpenseLineRequest(String expenseCategory, Long categoryId, String description, String sourceType,
			Long sourceId, String sourceNo, BigDecimal taxRate, BigDecimal taxExcludedAmount,
			BigDecimal pretaxAmount, BigDecimal taxAmount, BigDecimal taxIncludedAmount, BigDecimal totalAmount) {
	}

	public record AdvanceReceiptRequest(Long customerId, Long partnerId, String ownershipType, Long projectId,
			LocalDate receiptDate, LocalDate businessDate, BigDecimal amount, String method, String idempotencyKey,
			String remark, Long version) {
	}

	public record PrepaymentRequest(Long supplierId, Long partnerId, String ownershipType, Long projectId,
			LocalDate paymentDate, LocalDate businessDate, BigDecimal amount, String method, String idempotencyKey,
			String remark, Long version) {
	}

	public record SettlementAllocationRequest(String settlementSide, String cashSourceType, Long cashSourceId,
			LocalDate businessDate, String idempotencyKey, String remark, Long version,
			List<SettlementAllocationLineRequest> lines, String direction, Long partnerId, String ownershipType,
			Long projectId, List<SettlementFundRequest> funds, List<SettlementTargetRequest> targets) {
	}

	public record SettlementAllocationLineRequest(String targetType, Long targetId, BigDecimal amount) {
	}

	public record SettlementFundRequest(String fundType, Long fundId, BigDecimal amount, Long version) {
	}

	public record SettlementTargetRequest(String targetType, Long targetId, BigDecimal amount, Long version) {
	}

	public record VoucherDraftGenerateRequest(String sourceType, Long sourceId, Long version, String idempotencyKey) {
	}

	public record FinanceActionRequest(Long version, String idempotencyKey, String reason) {
	}

	private record ShipmentSource(Long id, String shipmentNo, Long orderId, String orderNo, Long customerId,
			String customerName, LocalDate businessDate, String status, String ownershipType, Long projectId) {
	}

	private record SalesSourceLine(Long id, Integer lineNo, Long orderLineId, Long materialId, Long unitId,
			BigDecimal quantity, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount, BigDecimal taxAmount,
			BigDecimal taxIncludedAmount) {
	}

	private record PurchaseSource(String settlementKind, String sourceType, Long sourceId, String sourceNo,
			Long supplierId, String supplierName, LocalDate businessDate, String status, String ownershipType,
			Long projectId) {
	}

	private record PurchaseSourceLine(Long id, Integer lineNo, Long purchaseOrderId, Long purchaseOrderLineId,
			Long outsourcingOrderId, Long materialId, Long unitId, BigDecimal quantity, BigDecimal taxRate,
			BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount) {
	}

	private record InvoiceRow(Long id, String documentNo, Long partyId, String ownershipType, Long projectId,
			String sourceType, Long sourceId, String sourceNo, LocalDate businessDate, LocalDate dueDate,
			String invoiceType, String settlementKind, String matchStatus, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount, String status, Long linkedReceivableId,
			Long linkedPayableId, Long version) implements FinancePayableSource {
	}

	private record SourcePayable(Long id, String documentNo, Long partyId, String ownershipType, Long projectId,
			LocalDate businessDate, LocalDate dueDate, BigDecimal taxIncludedAmount) implements FinancePayableSource {
	}

	private record ExpenseRow(Long id, String documentNo, Long partyId, String ownershipType, Long projectId,
			LocalDate businessDate, LocalDate dueDate, String invoiceType, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount, String status, Long linkedPayableId,
			Long version) implements FinancePayableSource {
	}

	private record CashRow(Long id, String documentNo, Long partyId, String ownershipType, Long projectId,
			LocalDate businessDate, BigDecimal amount, String method, String status, BigDecimal originalAmount,
			BigDecimal allocatedAmount, BigDecimal availableAmount, Long version) {
	}

	private record SettlementAllocationRow(Long id, String documentNo, String settlementSide, String cashSourceType,
			Long cashSourceId, Long partyId, String ownershipType, Long projectId, LocalDate businessDate,
			BigDecimal totalAmount, String status, Long version) {
	}

	private record VoucherDraftRow(Long id, String documentNo, String sourceType, Long sourceId, String status,
			LocalDate businessDate, String summary, String partyType, Long partyId, String partyName,
			String ownershipType, Long projectId, BigDecimal debitAmount, BigDecimal creditAmount,
			Long generationVersion, String formalVoucherNo, String postingStatus, Long version) {
	}

	private record SalesInvoiceLineRow(Long id, Integer lineNo, Long sourceLineId, BigDecimal taxIncludedAmount) {
	}

	private record PurchaseInvoiceLineRow(Long id, Integer lineNo, Long sourceLineId, BigDecimal taxIncludedAmount) {
	}

	private record ValidatedInvoiceLine(Long sourceLineId, BigDecimal quantity, BigDecimal taxRate,
			BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount) {
	}

	private record ValidatedExpenseLine(String expenseCategory, String description, String sourceType, Long sourceId,
			String sourceNo, BigDecimal taxRate, BigDecimal taxExcludedAmount, BigDecimal taxAmount,
			BigDecimal taxIncludedAmount) {
	}

	private record MatchResult(String status, List<MatchDifference> differences) {
	}

	private record MatchDifference(Long lineId, String differenceType, String expectedValue, String actualValue,
			String message) {
	}

	private record DocumentAmounts(BigDecimal taxExcludedAmount, BigDecimal taxAmount, BigDecimal taxIncludedAmount) {
	}

	private record PartySnapshot(String invoiceTitle, String taxNo, String bankName, String bankAccount,
			String invoiceType, BigDecimal defaultTaxRate) {
	}

	private record CustomerRow(Long id, String code, String name, String status) {
	}

	private record SupplierRow(Long id, String code, String name, String status) {
	}

	private record SettlementDraft(String settlementSide, String cashSourceType, Long cashSourceId, Long partyId,
			String ownershipType, Long projectId, BigDecimal totalAmount, List<SettlementLine> lines) {
	}

	private record SettlementLine(String targetType, Long targetId, BigDecimal amount) {
	}

	private record CashBalance(Long id, Long partyId, String ownershipType, Long projectId, BigDecimal originalAmount,
			BigDecimal allocatedAmount, BigDecimal availableAmount, String status, Long version) {
	}

	private record ReceivableBalance(Long id, Long customerId, String ownershipType, Long projectId,
			BigDecimal totalAmount, BigDecimal settledAmount, BigDecimal adjustedAmount, BigDecimal openAmount,
			String status, Long version) {
	}

	private record PayableBalance(Long id, Long supplierId, String ownershipType, Long projectId,
			BigDecimal totalAmount, BigDecimal settledAmount, BigDecimal adjustedAmount, BigDecimal openAmount,
			String status, Long version) {
	}

	private record VoucherSource(String sourceType, Long sourceId, LocalDate businessDate, String summary,
			String partyType, Long partyId, String partyName, String ownershipType, Long projectId, BigDecimal amount,
			String debitCategory, String creditCategory, Long version, String sourceNo) {
	}

	private record IdempotentDocument(Long id, String requestFingerprint) {
	}

	private record ActionRequestContext(String action, String resourceType, Long resourceId, Long version,
			String idempotencyKey, String requestFingerprint, String resultResourceType, Long resultResourceId,
			Long resultVersion) {
	}

	private record ActionRecord(String resultResourceType, Long resultResourceId, Long resultVersion,
			String requestFingerprint) {
	}

}
