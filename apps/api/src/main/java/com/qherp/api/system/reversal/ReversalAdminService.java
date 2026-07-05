package com.qherp.api.system.reversal;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.finance.PayableStatus;
import com.qherp.api.system.finance.ReceivableStatus;
import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReversalAdminService {

	private static final String SALES_RETURN_TARGET = "SALES_RETURN";

	private static final String SALES_SHIPMENT_SOURCE = "SALES_SHIPMENT";

	private static final String SALES_SHIPMENT_LINE_SOURCE = "SALES_SHIPMENT_LINE";

	private static final String SALES_RETURN_SOURCE = "SALES_RETURN";

	private static final String PURCHASE_RETURN_TARGET = "PURCHASE_RETURN";

	private static final String PURCHASE_RECEIPT_SOURCE = "PURCHASE_RECEIPT";

	private static final String PURCHASE_RECEIPT_LINE_SOURCE = "PURCHASE_RECEIPT_LINE";

	private static final String PURCHASE_RETURN_SOURCE = "PURCHASE_RETURN";

	private static final String PRODUCTION_MATERIAL_ISSUE_SOURCE = "PRODUCTION_MATERIAL_ISSUE";

	private static final String PRODUCTION_MATERIAL_ISSUE_LINE_SOURCE = "PRODUCTION_MATERIAL_ISSUE_LINE";

	private static final String PRODUCTION_WORK_ORDER_SOURCE = "PRODUCTION_WORK_ORDER";

	private static final String PRODUCTION_MATERIAL_RETURN_TARGET = "PRODUCTION_MATERIAL_RETURN";

	private static final String PRODUCTION_MATERIAL_RETURN_SOURCE = "PRODUCTION_MATERIAL_RETURN";

	private static final String PRODUCTION_MATERIAL_SUPPLEMENT_TARGET = "PRODUCTION_MATERIAL_SUPPLEMENT";

	private static final String PRODUCTION_MATERIAL_SUPPLEMENT_SOURCE = "PRODUCTION_MATERIAL_SUPPLEMENT";

	private static final String SETTLEMENT_ADJUSTMENT_SOURCE = "SETTLEMENT_ADJUSTMENT";

	private static final String RESTRICTED_MESSAGE = "来源无查看权限";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger SALES_RETURN_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger PURCHASE_RETURN_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger MATERIAL_RETURN_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger MATERIAL_SUPPLEMENT_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger COST_RECORD_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger SETTLEMENT_ADJUSTMENT_NO_SEQUENCE = new AtomicInteger();

	private static final int MAX_NO_ATTEMPTS = 3;

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final InventoryPostingService inventoryPostingService;

	public ReversalAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
	}

	public PageResponse<Object> emptyPage(int page, int pageSize) {
		return PageResponse.of(List.of(), page, pageSize, 0);
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesReturnSourceResponse> salesReturnSources(String keyword, Long customerId,
			Long warehouseId, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize, CurrentUser currentUser) {
		if (!canViewSalesShipment(currentUser)) {
			return PageResponse.of(List.of(), page, limit(pageSize), 0);
		}
		QueryParts queryParts = salesReturnSourceQuery(keyword, customerId, warehouseId, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment sh
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<SalesReturnSourceResponse> sources = this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.customer_id, c.name as customer_name, sh.warehouse_id,
				       w.name as warehouse_name, sh.business_date, sh.status
				from sal_sales_shipment sh
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				%s
				order by sh.business_date desc, sh.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapSalesReturnSource(rs), args.toArray());
		return PageResponse.of(sources, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesReturnSummaryResponse> salesReturns(String keyword, Long customerId, Long warehouseId,
			String status, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize, CurrentUser currentUser) {
		QueryParts queryParts = salesReturnQuery(keyword, customerId, warehouseId, status, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<SalesReturnSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no, sh.business_date as source_date,
				       sh.status as source_status, r.business_date, r.status,
				       coalesce((select sum(l.quantity) from sal_sales_return_line l where l.return_id = r.id), 0) as total_quantity,
				       r.total_amount, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapSalesReturnSummary(rs, currentUser),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public SalesReturnDetailResponse salesReturn(Long id, CurrentUser currentUser) {
		SalesReturnRow row = salesReturnRow(id).orElseThrow(this::sourceNotFoundException);
		return salesReturnDetail(row, currentUser);
	}

	@Transactional
	public SalesReturnDetailResponse createSalesReturn(SalesReturnRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedSalesReturn validated = validateSalesReturnCreate(request);
		ShipmentRow shipment = lockShipment(validated.sourceShipmentId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		Optional<SalesReturnRow> existing = existingSalesReturn(validated.sourceShipmentId(),
				validated.clientRequestId());
		if (existing.isPresent()) {
			return existingSalesReturnDetail(existing.get(), validated, operator);
		}
		List<ValidatedSalesReturnLine> lines = validateSalesReturnLines(shipment, validated.lines());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertSalesReturnWithRetry(shipment, validated, totalAmount(lines),
					operator.username(), now);
			insertSalesReturnLines(created.id(), lines, now);
			this.auditService.record(operator, "SALES_RETURN_CREATE", SALES_RETURN_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return salesReturn(created.id(), operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public SalesReturnDetailResponse updateSalesReturn(Long id, SalesReturnRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		SalesReturnRow current = lockSalesReturn(id).orElseThrow(this::sourceNotFoundException);
		if (current.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(current);
		List<SalesReturnLineRow> currentLines = lockSalesReturnLines(id);
		ValidatedSalesReturn validated = validateSalesReturnUpdate(current, request);
		ShipmentRow shipment = lockShipment(current.sourceShipmentId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<ValidatedSalesReturnLine> lines = validateSalesReturnLines(shipment, validated.lines(), currentLines);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update sal_sales_return
					set business_date = ?, total_amount = ?, client_request_id = ?, remark = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					""", validated.businessDate(), totalAmount(lines), blankToNull(validated.clientRequestId()),
					blankToNull(validated.remark()), operator.username(), now, id);
			this.jdbcTemplate.update("delete from sal_sales_return_line where return_id = ?", id);
			insertSalesReturnLines(id, lines, now);
			this.auditService.record(operator, "SALES_RETURN_UPDATE", SALES_RETURN_TARGET, id, current.returnNo(),
					servletRequest);
			return salesReturn(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public SalesReturnDetailResponse postSalesReturn(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		SalesReturnRow salesReturn = lockSalesReturn(id).orElseThrow(this::sourceNotFoundException);
		if (salesReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(salesReturn);
		ShipmentRow shipment = lockShipment(salesReturn.sourceShipmentId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(shipment.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<SalesReturnLineRow> lines = lockSalesReturnLines(id);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_INVALID);
		}
		ReceivableRow receivable = lockReceivableForShipment(shipment.id()).orElseThrow(this::sourceNotFoundException);
		requireAdjustableReceivable(receivable);
		BigDecimal totalAmount = totalAmountFromRows(lines);
		if (totalAmount.compareTo(receivable.unreceivedAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_AMOUNT_EXCEEDS_AVAILABLE);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			for (SalesReturnLineRow line : lines) {
				ShipmentLineRow sourceLine = lockShipmentLine(shipment.id(), line.sourceShipmentLineId())
					.orElseThrow(this::sourceNotFoundException);
				validateLineStillReturnable(sourceLine, line);
				InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
						new InventoryPostingService.PostingRequest(InventoryMovementType.SALES_RETURN_IN,
								InventoryDirection.IN, salesReturn.warehouseId(), line.materialId(), line.unitId(),
								line.quantity(), SALES_RETURN_SOURCE, salesReturn.id(), line.id(),
								salesReturn.businessDate(), "销售退货入库", line.reason(), operator.username()));
				Long movementId = movementId(SALES_RETURN_SOURCE, line.id());
				this.jdbcTemplate.update("""
						update sal_sales_return_line
						set stock_movement_id = ?, updated_at = ?
						where id = ?
						""", movementId, now, line.id());
				insertReversalLink(shipment, sourceLine, salesReturn, line, operator.username(), now);
			}
			Long adjustmentId = insertPostedReceivableAdjustment(salesReturn, receivable, totalAmount, operator.username(),
					now);
			applyReceivableAdjustment(receivable, totalAmount, operator.username(), now);
			this.jdbcTemplate.update("""
					update sal_sales_return
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", ReversalDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
			this.auditService.record(operator, "SALES_RETURN_POST", SALES_RETURN_TARGET, id, salesReturn.returnNo(),
					servletRequest);
			return salesReturn(id, operator).withSettlementAdjustmentId(adjustmentId);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public SalesReturnDetailResponse cancelSalesReturn(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		SalesReturnRow salesReturn = lockSalesReturn(id).orElseThrow(this::sourceNotFoundException);
		if (salesReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(salesReturn);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_sales_return
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", ReversalDocumentStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_RETURN_CANCEL", SALES_RETURN_TARGET, id, salesReturn.returnNo(),
				servletRequest);
		return salesReturn(id, operator);
	}

	@Transactional(readOnly = true)
	public PageResponse<PurchaseReturnSourceResponse> purchaseReturnSources(String keyword, Long supplierId,
			Long warehouseId, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize, CurrentUser currentUser) {
		if (!canViewPurchaseReceipt(currentUser)) {
			return PageResponse.of(List.of(), page, limit(pageSize), 0);
		}
		QueryParts queryParts = purchaseReturnSourceQuery(keyword, supplierId, warehouseId, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_receipt pr
				join mst_supplier s on s.id = pr.supplier_id
				join mst_warehouse w on w.id = pr.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<PurchaseReturnSourceResponse> sources = this.jdbcTemplate.query("""
				select pr.id, pr.receipt_no, pr.supplier_id, s.name as supplier_name, pr.warehouse_id,
				       w.name as warehouse_name, pr.business_date, pr.status
				from proc_purchase_receipt pr
				join mst_supplier s on s.id = pr.supplier_id
				join mst_warehouse w on w.id = pr.warehouse_id
				%s
				order by pr.business_date desc, pr.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapPurchaseReturnSource(rs), args.toArray());
		return PageResponse.of(sources, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<PurchaseReturnSummaryResponse> purchaseReturns(String keyword, Long supplierId,
			Long warehouseId, String status, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize,
			CurrentUser currentUser) {
		QueryParts queryParts = purchaseReturnQuery(keyword, supplierId, warehouseId, status, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_return r
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<PurchaseReturnSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.return_no, r.supplier_id, s.name as supplier_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_receipt_id, r.source_receipt_no,
				       pr.business_date as source_date, pr.status as source_status, r.business_date, r.status,
				       coalesce((select sum(l.quantity) from proc_purchase_return_line l where l.return_id = r.id), 0) as total_quantity,
				       r.total_amount, r.created_at, r.updated_at
				from proc_purchase_return r
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				join proc_purchase_receipt pr on pr.id = r.source_receipt_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapPurchaseReturnSummary(rs, currentUser),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PurchaseReturnDetailResponse purchaseReturn(Long id, CurrentUser currentUser) {
		PurchaseReturnRow row = purchaseReturnRow(id).orElseThrow(this::sourceNotFoundException);
		return purchaseReturnDetail(row, currentUser);
	}

	@Transactional
	public PurchaseReturnDetailResponse createPurchaseReturn(PurchaseReturnRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedPurchaseReturn validated = validatePurchaseReturnCreate(request);
		ReceiptRow receipt = lockReceipt(validated.sourceReceiptId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		Optional<PurchaseReturnRow> existing = existingPurchaseReturn(validated.sourceReceiptId(),
				validated.clientRequestId());
		if (existing.isPresent()) {
			return existingPurchaseReturnDetail(existing.get(), validated, operator);
		}
		List<ValidatedPurchaseReturnLine> lines = validatePurchaseReturnLines(receipt, validated.lines());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertPurchaseReturnWithRetry(receipt, validated, totalPurchaseAmount(lines),
					operator.username(), now);
			insertPurchaseReturnLines(created.id(), lines, now);
			this.auditService.record(operator, "PURCHASE_RETURN_CREATE", PURCHASE_RETURN_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return purchaseReturn(created.id(), operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public PurchaseReturnDetailResponse updatePurchaseReturn(Long id, PurchaseReturnRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		PurchaseReturnRow current = lockPurchaseReturn(id).orElseThrow(this::sourceNotFoundException);
		if (current.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(current);
		List<PurchaseReturnLineRow> currentLines = lockPurchaseReturnLines(id);
		ValidatedPurchaseReturn validated = validatePurchaseReturnUpdate(current, request);
		ReceiptRow receipt = lockReceipt(current.sourceReceiptId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<ValidatedPurchaseReturnLine> lines = validatePurchaseReturnLines(receipt, validated.lines(), currentLines);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update proc_purchase_return
					set business_date = ?, total_amount = ?, client_request_id = ?, remark = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					""", validated.businessDate(), totalPurchaseAmount(lines), blankToNull(validated.clientRequestId()),
					blankToNull(validated.remark()), operator.username(), now, id);
			this.jdbcTemplate.update("delete from proc_purchase_return_line where return_id = ?", id);
			insertPurchaseReturnLines(id, lines, now);
			this.auditService.record(operator, "PURCHASE_RETURN_UPDATE", PURCHASE_RETURN_TARGET, id,
					current.returnNo(), servletRequest);
			return purchaseReturn(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public PurchaseReturnDetailResponse postPurchaseReturn(Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PurchaseReturnRow purchaseReturn = lockPurchaseReturn(id).orElseThrow(this::sourceNotFoundException);
		if (purchaseReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(purchaseReturn);
		ReceiptRow receipt = lockReceipt(purchaseReturn.sourceReceiptId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(receipt.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<PurchaseReturnLineRow> lines = lockPurchaseReturnLines(id);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_INVALID);
		}
		PayableRow payable = lockPayableForReceipt(receipt.id()).orElseThrow(this::sourceNotFoundException);
		requireAdjustablePayable(payable);
		BigDecimal totalAmount = totalPurchaseAmountFromRows(lines);
		if (totalAmount.compareTo(payable.unpaidAmount()) > 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_AMOUNT_EXCEEDS_AVAILABLE);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			for (PurchaseReturnLineRow line : lines) {
				ReceiptLineRow sourceLine = lockReceiptLine(receipt.id(), line.sourceReceiptLineId())
					.orElseThrow(this::sourceNotFoundException);
				validatePurchaseLineStillReturnable(sourceLine, line);
				if (lockedStockQuantity(purchaseReturn.warehouseId(), line.materialId()).compareTo(line.quantity()) < 0) {
					throw new BusinessException(ApiErrorCode.REVERSAL_STOCK_INSUFFICIENT);
				}
				this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
						InventoryMovementType.PURCHASE_RETURN_OUT, InventoryDirection.OUT,
						purchaseReturn.warehouseId(), line.materialId(), line.unitId(), line.quantity(),
						PURCHASE_RETURN_SOURCE, purchaseReturn.id(), line.id(), purchaseReturn.businessDate(),
						"采购退货出库", line.reason(), operator.username()));
				Long movementId = movementId(PURCHASE_RETURN_SOURCE, line.id());
				this.jdbcTemplate.update("""
						update proc_purchase_return_line
						set stock_movement_id = ?, updated_at = ?
						where id = ?
						""", movementId, now, line.id());
				insertPurchaseReversalLink(receipt, sourceLine, purchaseReturn, line, operator.username(), now);
			}
			Long adjustmentId = insertPostedPayableAdjustment(purchaseReturn, payable, totalAmount, operator.username(),
					now);
			applyPayableAdjustment(payable, totalAmount, operator.username(), now);
			this.jdbcTemplate.update("""
					update proc_purchase_return
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", ReversalDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
			this.auditService.record(operator, "PURCHASE_RETURN_POST", PURCHASE_RETURN_TARGET, id,
					purchaseReturn.returnNo(), servletRequest);
			return purchaseReturn(id, operator).withSettlementAdjustmentId(adjustmentId);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public PurchaseReturnDetailResponse cancelPurchaseReturn(Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PurchaseReturnRow purchaseReturn = lockPurchaseReturn(id).orElseThrow(this::sourceNotFoundException);
		if (purchaseReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(purchaseReturn);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_purchase_return
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", ReversalDocumentStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "PURCHASE_RETURN_CANCEL", PURCHASE_RETURN_TARGET, id,
				purchaseReturn.returnNo(), servletRequest);
		return purchaseReturn(id, operator);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductionMaterialReturnSourceResponse> materialReturnSources(String keyword, Long workOrderId,
			Long warehouseId, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize,
			CurrentUser currentUser) {
		if (!canViewProductionIssue(currentUser)) {
			return PageResponse.of(List.of(), page, limit(pageSize), 0);
		}
		QueryParts queryParts = materialReturnSourceQuery(keyword, workOrderId, warehouseId, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_material_issue i
				join mfg_work_order wo on wo.id = i.work_order_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<ProductionMaterialReturnSourceResponse> sources = this.jdbcTemplate.query("""
				select i.id, i.issue_no, i.work_order_id, wo.work_order_no, fl.warehouse_id,
				       w.name as warehouse_name, i.business_date, i.status
				from mfg_material_issue i
				join mfg_work_order wo on wo.id = i.work_order_id
				join lateral (
					select l.warehouse_id
					from mfg_material_issue_line l
					where l.issue_id = i.id
					order by l.line_no asc, l.id asc
					limit 1
				) fl on true
				join mst_warehouse w on w.id = fl.warehouse_id
				%s
				order by i.business_date desc, i.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapMaterialReturnSource(rs), args.toArray());
		return PageResponse.of(sources, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductionMaterialReturnSummaryResponse> materialReturns(String keyword, Long workOrderId,
			Long warehouseId, String status, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize,
			CurrentUser currentUser) {
		QueryParts queryParts = materialReturnQuery(keyword, workOrderId, warehouseId, status, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_material_return r
				join mfg_work_order wo on wo.id = r.work_order_id
				join mfg_material_issue i on i.id = r.source_issue_id
				join mst_warehouse w on w.id = r.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<ProductionMaterialReturnSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.return_no, r.work_order_id, wo.work_order_no, r.warehouse_id,
				       w.name as warehouse_name, r.source_issue_id, i.issue_no, i.business_date as source_date,
				       i.status as source_status, r.business_date, r.status,
				       coalesce((select sum(l.quantity) from mfg_material_return_line l where l.return_id = r.id), 0) as total_quantity,
				       coalesce((
				           select sum(l.quantity * coalesce(cr.unit_price, source_cost.unit_price, 0))
				           from mfg_material_return_line l
				           left join mfg_cost_record cr on cr.id = l.cost_record_id
				           left join lateral (
				               select crs.unit_price
				               from mfg_cost_record crs
				               where crs.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
				               and crs.source_line_id = l.source_issue_line_id
				               and crs.cost_type = 'MATERIAL'
				               and crs.status = 'ACTIVE'
				               order by crs.id desc
				               limit 1
				           ) source_cost on true
				           where l.return_id = r.id
				       ), 0) as total_amount,
				       r.created_at, r.updated_at
				from mfg_material_return r
				join mfg_work_order wo on wo.id = r.work_order_id
				join mfg_material_issue i on i.id = r.source_issue_id
				join mst_warehouse w on w.id = r.warehouse_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapMaterialReturnSummary(rs, currentUser),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public ProductionMaterialReturnDetailResponse materialReturn(Long id, CurrentUser currentUser) {
		MaterialReturnRow row = materialReturnRow(id).orElseThrow(this::sourceNotFoundException);
		return materialReturnDetail(row, currentUser);
	}

	@Transactional
	public ProductionMaterialReturnDetailResponse createMaterialReturn(ProductionMaterialReturnRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		ValidatedMaterialReturn validated = validateMaterialReturnCreate(request);
		ProductionIssueRow issue = lockProductionIssue(validated.sourceIssueId())
			.orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(issue.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		Optional<MaterialReturnRow> existing = existingMaterialReturn(validated.sourceIssueId(),
				validated.clientRequestId());
		if (existing.isPresent()) {
			return existingMaterialReturnDetail(existing.get(), validated, operator);
		}
		List<ValidatedMaterialReturnLine> lines = validateMaterialReturnLines(issue, validated.lines());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertMaterialReturnWithRetry(issue, validated, lines.get(0).warehouseId(),
					operator.username(), now);
			insertMaterialReturnLines(created.id(), lines, now);
			this.auditService.record(operator, "PRODUCTION_MATERIAL_RETURN_CREATE",
					PRODUCTION_MATERIAL_RETURN_TARGET, created.id(), created.documentNo(), servletRequest);
			return materialReturn(created.id(), operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public ProductionMaterialReturnDetailResponse updateMaterialReturn(Long id, ProductionMaterialReturnRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		MaterialReturnRow current = lockMaterialReturn(id).orElseThrow(this::sourceNotFoundException);
		if (current.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(current);
		List<MaterialReturnLineRow> currentLines = lockMaterialReturnLines(id);
		ValidatedMaterialReturn validated = validateMaterialReturnUpdate(current, request);
		ProductionIssueRow issue = lockProductionIssue(current.sourceIssueId()).orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(issue.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<ValidatedMaterialReturnLine> lines = validateMaterialReturnLines(issue, validated.lines(), currentLines);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update mfg_material_return
					set warehouse_id = ?, business_date = ?, client_request_id = ?, remark = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					""", lines.get(0).warehouseId(), validated.businessDate(), blankToNull(validated.clientRequestId()),
					blankToNull(validated.remark()), operator.username(), now, id);
			this.jdbcTemplate.update("delete from mfg_material_return_line where return_id = ?", id);
			insertMaterialReturnLines(id, lines, now);
			this.auditService.record(operator, "PRODUCTION_MATERIAL_RETURN_UPDATE",
					PRODUCTION_MATERIAL_RETURN_TARGET, id, current.returnNo(), servletRequest);
			return materialReturn(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public ProductionMaterialReturnDetailResponse postMaterialReturn(Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialReturnRow materialReturn = lockMaterialReturn(id).orElseThrow(this::sourceNotFoundException);
		if (materialReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(materialReturn);
		ProductionIssueRow issue = lockProductionIssue(materialReturn.sourceIssueId())
			.orElseThrow(this::sourceNotFoundException);
		if (!"POSTED".equals(issue.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		List<MaterialReturnLineRow> lines = lockMaterialReturnLines(id);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			for (MaterialReturnLineRow line : lines) {
				ProductionIssueLineRow sourceLine = lockProductionIssueLine(issue.id(), line.sourceIssueLineId())
					.orElseThrow(this::sourceNotFoundException);
				validateMaterialReturnLineStillReturnable(sourceLine, line);
				this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
						InventoryMovementType.PRODUCTION_MATERIAL_RETURN_IN, InventoryDirection.IN,
						line.warehouseId(), line.materialId(), line.unitId(), line.quantity(),
						PRODUCTION_MATERIAL_RETURN_SOURCE, materialReturn.id(), line.id(),
						materialReturn.businessDate(), "生产退料入库", line.reason(), operator.username()));
				Long movementId = movementId(PRODUCTION_MATERIAL_RETURN_SOURCE, line.id());
				BigDecimal amount = money(line.quantity().multiply(line.unitPrice()));
				Long costRecordId = insertProductionCostRecord(PRODUCTION_MATERIAL_RETURN_SOURCE,
						materialReturn.returnNo(), materialReturn.id(), line.id(), issue.workOrderId(),
						issue.productMaterialId(), line.workOrderMaterialId(), line.materialId(), line.unitId(),
						line.quantity(), line.unitPrice(), amount, materialReturn.businessDate(), "生产退料成本影响",
						operator.username(), now);
				this.jdbcTemplate.update("""
						update mfg_material_return_line
						set stock_movement_id = ?, cost_record_id = ?, updated_at = ?
						where id = ?
						""", movementId, costRecordId, now, line.id());
				insertMaterialReturnReversalLink(issue, sourceLine, materialReturn, line, amount, operator.username(),
						now);
			}
			this.jdbcTemplate.update("""
					update mfg_material_return
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", ReversalDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
			this.auditService.record(operator, "PRODUCTION_MATERIAL_RETURN_POST",
					PRODUCTION_MATERIAL_RETURN_TARGET, id, materialReturn.returnNo(), servletRequest);
			return materialReturn(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public ProductionMaterialReturnDetailResponse cancelMaterialReturn(Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialReturnRow materialReturn = lockMaterialReturn(id).orElseThrow(this::sourceNotFoundException);
		if (materialReturn.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(materialReturn);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_material_return
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", ReversalDocumentStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "PRODUCTION_MATERIAL_RETURN_CANCEL", PRODUCTION_MATERIAL_RETURN_TARGET,
				id, materialReturn.returnNo(), servletRequest);
		return materialReturn(id, operator);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductionMaterialSupplementSourceResponse> materialSupplementSources(String keyword,
			Long workOrderId, Long warehouseId, int page, int pageSize, CurrentUser currentUser) {
		if (!canViewProductionWorkOrder(currentUser)) {
			return PageResponse.of(List.of(), page, limit(pageSize), 0);
		}
		QueryParts queryParts = materialSupplementSourceQuery(keyword, workOrderId, warehouseId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_work_order wo
				join mst_warehouse w on w.status = 'ENABLED'
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<ProductionMaterialSupplementSourceResponse> sources = this.jdbcTemplate.query("""
				select wo.id, wo.work_order_no, wo.status as work_order_status, w.id as warehouse_id,
				       w.name as warehouse_name
				from mfg_work_order wo
				join mst_warehouse w on w.status = 'ENABLED'
				%s
				order by wo.updated_at desc, wo.id desc, w.id asc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapMaterialSupplementSource(rs), args.toArray());
		return PageResponse.of(sources, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductionMaterialSupplementSummaryResponse> materialSupplements(String keyword,
			Long workOrderId, Long warehouseId, String status, LocalDate dateFrom, LocalDate dateTo, int page,
			int pageSize, CurrentUser currentUser) {
		QueryParts queryParts = materialSupplementQuery(keyword, workOrderId, warehouseId, status, dateFrom, dateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_material_supplement s
				join mfg_work_order wo on wo.id = s.work_order_id
				join mst_warehouse w on w.id = s.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<ProductionMaterialSupplementSummaryResponse> items = this.jdbcTemplate.query("""
				select s.id, s.supplement_no, s.work_order_id, wo.work_order_no, wo.status as source_status,
				       s.warehouse_id, w.name as warehouse_name, s.business_date, s.status,
				       coalesce((select sum(l.quantity) from mfg_material_supplement_line l where l.supplement_id = s.id), 0) as total_quantity,
				       coalesce((
				           select sum(l.quantity * coalesce(cr.unit_price, source_cost.unit_price, 0))
				           from mfg_material_supplement_line l
				           left join mfg_cost_record cr on cr.id = l.cost_record_id
				           left join lateral (
				               select crs.unit_price
				               from mfg_cost_record crs
				               where crs.work_order_material_id = l.work_order_material_id
				               and crs.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
				               and crs.cost_type = 'MATERIAL'
				               and crs.status = 'ACTIVE'
				               and crs.unit_price is not null
				               order by crs.id desc
				               limit 1
				           ) source_cost on true
				           where l.supplement_id = s.id
				       ), 0) as total_amount,
				       s.created_at, s.updated_at
				from mfg_material_supplement s
				join mfg_work_order wo on wo.id = s.work_order_id
				join mst_warehouse w on w.id = s.warehouse_id
				%s
				order by s.updated_at desc, s.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapMaterialSupplementSummary(rs, currentUser),
				args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public ProductionMaterialSupplementDetailResponse materialSupplement(Long id, CurrentUser currentUser) {
		MaterialSupplementRow row = materialSupplementRow(id).orElseThrow(this::sourceNotFoundException);
		return materialSupplementDetail(row, currentUser);
	}

	@Transactional
	public ProductionMaterialSupplementDetailResponse createMaterialSupplement(
			ProductionMaterialSupplementRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		ValidatedMaterialSupplement validated = validateMaterialSupplementCreate(request);
		ProductionWorkOrderRow workOrder = lockProductionWorkOrder(validated.workOrderId())
			.orElseThrow(this::sourceNotFoundException);
		requireActiveProductionWorkOrder(workOrder);
		validateWarehouse(validated.warehouseId());
		Optional<MaterialSupplementRow> existing = existingMaterialSupplement(validated.workOrderId(),
				validated.clientRequestId());
		if (existing.isPresent()) {
			return existingMaterialSupplementDetail(existing.get(), validated, operator);
		}
		List<ValidatedMaterialSupplementLine> lines = validateMaterialSupplementLines(workOrder,
				validated.warehouseId(), validated.lines());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertMaterialSupplementWithRetry(workOrder, validated, operator.username(), now);
			insertMaterialSupplementLines(created.id(), lines, now);
			this.auditService.record(operator, "PRODUCTION_MATERIAL_SUPPLEMENT_CREATE",
					PRODUCTION_MATERIAL_SUPPLEMENT_TARGET, created.id(), created.documentNo(), servletRequest);
			return materialSupplement(created.id(), operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public ProductionMaterialSupplementDetailResponse updateMaterialSupplement(Long id,
			ProductionMaterialSupplementRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		MaterialSupplementRow current = lockMaterialSupplement(id).orElseThrow(this::sourceNotFoundException);
		if (current.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(current);
		List<MaterialSupplementLineRow> currentLines = lockMaterialSupplementLines(id);
		ValidatedMaterialSupplement validated = validateMaterialSupplementUpdate(current, request);
		ProductionWorkOrderRow workOrder = lockProductionWorkOrder(current.workOrderId())
			.orElseThrow(this::sourceNotFoundException);
		requireActiveProductionWorkOrder(workOrder);
		validateWarehouse(validated.warehouseId());
		List<ValidatedMaterialSupplementLine> lines = validateMaterialSupplementLines(workOrder,
				validated.warehouseId(), validated.lines(), currentLines);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update mfg_material_supplement
					set warehouse_id = ?, business_date = ?, client_request_id = ?, remark = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					""", validated.warehouseId(), validated.businessDate(), blankToNull(validated.clientRequestId()),
					blankToNull(validated.remark()), operator.username(), now, id);
			this.jdbcTemplate.update("delete from mfg_material_supplement_line where supplement_id = ?", id);
			insertMaterialSupplementLines(id, lines, now);
			this.auditService.record(operator, "PRODUCTION_MATERIAL_SUPPLEMENT_UPDATE",
					PRODUCTION_MATERIAL_SUPPLEMENT_TARGET, id, current.supplementNo(), servletRequest);
			return materialSupplement(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public ProductionMaterialSupplementDetailResponse postMaterialSupplement(Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialSupplementRow supplement = lockMaterialSupplement(id).orElseThrow(this::sourceNotFoundException);
		if (supplement.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(supplement);
		ProductionWorkOrderRow workOrder = lockProductionWorkOrder(supplement.workOrderId())
			.orElseThrow(this::sourceNotFoundException);
		requireActiveProductionWorkOrder(workOrder);
		List<MaterialSupplementLineRow> lines = lockMaterialSupplementLines(id);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			for (MaterialSupplementLineRow line : lines) {
				ProductionWorkOrderMaterialRow sourceLine = lockProductionWorkOrderMaterial(workOrder.id(),
						line.workOrderMaterialId()).orElseThrow(this::sourceNotFoundException);
				validateMaterialSupplementLineStillValid(sourceLine, line);
				if (lockedStockQuantity(supplement.warehouseId(), line.materialId()).compareTo(line.quantity()) < 0) {
					throw new BusinessException(ApiErrorCode.REVERSAL_STOCK_INSUFFICIENT);
				}
				this.inventoryPostingService.post(new InventoryPostingService.PostingRequest(
						InventoryMovementType.PRODUCTION_MATERIAL_SUPPLEMENT_OUT, InventoryDirection.OUT,
						supplement.warehouseId(), line.materialId(), line.unitId(), line.quantity(),
						PRODUCTION_MATERIAL_SUPPLEMENT_SOURCE, supplement.id(), line.id(),
						supplement.businessDate(), "生产补料出库", line.reason(), operator.username()));
				Long movementId = movementId(PRODUCTION_MATERIAL_SUPPLEMENT_SOURCE, line.id());
				BigDecimal amount = money(line.quantity().multiply(line.unitPrice()));
				Long costRecordId = insertProductionCostRecord(PRODUCTION_MATERIAL_SUPPLEMENT_SOURCE,
						supplement.supplementNo(), supplement.id(), line.id(), workOrder.id(),
						workOrder.productMaterialId(), line.workOrderMaterialId(), line.materialId(), line.unitId(),
						line.quantity(), line.unitPrice(), amount, supplement.businessDate(), "生产补料成本影响",
						operator.username(), now);
				this.jdbcTemplate.update("""
						update mfg_material_supplement_line
						set stock_movement_id = ?, cost_record_id = ?, updated_at = ?
						where id = ?
						""", movementId, costRecordId, now, line.id());
				insertMaterialSupplementReversalLink(workOrder, sourceLine, supplement, line, amount,
						operator.username(), now);
			}
			this.jdbcTemplate.update("""
					update mfg_material_supplement
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", ReversalDocumentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
			this.auditService.record(operator, "PRODUCTION_MATERIAL_SUPPLEMENT_POST",
					PRODUCTION_MATERIAL_SUPPLEMENT_TARGET, id, supplement.supplementNo(), servletRequest);
			return materialSupplement(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateReversalException(exception);
		}
	}

	@Transactional
	public ProductionMaterialSupplementDetailResponse cancelMaterialSupplement(Long id, CurrentUser operator,
			HttpServletRequest servletRequest) {
		MaterialSupplementRow supplement = lockMaterialSupplement(id).orElseThrow(this::sourceNotFoundException);
		if (supplement.status() == ReversalDocumentStatus.POSTED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_POSTED_IMMUTABLE);
		}
		requireDraft(supplement);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_material_supplement
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", ReversalDocumentStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "PRODUCTION_MATERIAL_SUPPLEMENT_CANCEL",
				PRODUCTION_MATERIAL_SUPPLEMENT_TARGET, id, supplement.supplementNo(), servletRequest);
		return materialSupplement(id, operator);
	}

	@Transactional(readOnly = true)
	public List<ReversalTraceRecord> traces(String sourceType, Long sourceId, Long sourceLineId, String direction,
			CurrentUser currentUser) {
		if (!hasText(sourceType) || sourceId == null) {
			return List.of();
		}
		if (isProductionTraceType(sourceType)) {
			List<ProductionTraceLinkRow> links;
			List<ReversalTraceRecord> traces = new ArrayList<>();
			if (!"REVERSE_TO_SOURCE".equals(direction)) {
				links = productionSourceTraceLinks(sourceType, sourceId, sourceLineId);
				traces.addAll(productionTraceRecords(links, currentUser, "SOURCE_TO_REVERSE"));
			}
			if (!"SOURCE_TO_REVERSE".equals(direction)) {
				links = productionReverseTraceLinks(sourceType, sourceId, sourceLineId);
				traces.addAll(productionTraceRecords(links, currentUser, "REVERSE_TO_SOURCE"));
			}
			return traces;
		}
		if (isPurchaseTraceType(sourceType)) {
			List<PurchaseTraceLinkRow> links;
			List<ReversalTraceRecord> traces = new ArrayList<>();
			if (!"REVERSE_TO_SOURCE".equals(direction)) {
				links = purchaseSourceTraceLinks(sourceType, sourceId, sourceLineId);
				traces.addAll(purchaseTraceRecords(links, currentUser, "SOURCE_TO_REVERSE"));
			}
			if (!"SOURCE_TO_REVERSE".equals(direction)) {
				links = purchaseReverseTraceLinks(sourceType, sourceId, sourceLineId);
				traces.addAll(purchaseTraceRecords(links, currentUser, "REVERSE_TO_SOURCE"));
			}
			return traces;
		}
		List<TraceLinkRow> links = new ArrayList<>();
		List<ReversalTraceRecord> traces = new ArrayList<>();
		if (!"REVERSE_TO_SOURCE".equals(direction)) {
			links = sourceTraceLinks(sourceType, sourceId, sourceLineId);
			traces.addAll(traceRecords(links, currentUser, "SOURCE_TO_REVERSE"));
		}
		if (!"SOURCE_TO_REVERSE".equals(direction)) {
			links = reverseTraceLinks(sourceType, sourceId, sourceLineId);
			traces.addAll(traceRecords(links, currentUser, "REVERSE_TO_SOURCE"));
		}
		return traces;
	}

	public Object sourceNotFound() {
		throw sourceNotFoundException();
	}

	private SalesReturnDetailResponse salesReturnDetail(SalesReturnRow row, CurrentUser currentUser) {
		boolean canViewSource = canViewSalesShipment(currentUser);
		List<ReversalDocumentLine> lines = salesReturnLines(row.id(), row.sourceShipmentId(), row.sourceShipmentNo(),
				canViewSource);
		List<ReversalTraceRecord> traces = traceRecords(reverseTraceLinks(SALES_RETURN_SOURCE, row.id(), null),
				currentUser, "SOURCE_TO_REVERSE");
		return new SalesReturnDetailResponse(row.id(), row.returnNo(), row.customerId(), row.customerName(),
				row.warehouseId(), row.warehouseName(), row.businessDate(), row.status().name(),
				quantity(totalQuantity(lines)), amount(row.totalAmount()), sourceView(SALES_SHIPMENT_SOURCE,
						row.sourceShipmentId(), null, row.sourceShipmentNo(), null, row.sourceBusinessDate(),
						row.sourceStatus(), null, null, canViewSource, "sales-shipment-detail",
						Map.of("id", row.sourceShipmentId()), null),
				row.createdAt(), row.updatedAt(), row.clientRequestId(), row.remark(), lines, traces);
	}

	private PurchaseReturnDetailResponse purchaseReturnDetail(PurchaseReturnRow row, CurrentUser currentUser) {
		boolean canViewSource = canViewPurchaseReceipt(currentUser);
		List<ReversalDocumentLine> lines = purchaseReturnLines(row.id(), row.sourceReceiptId(), row.sourceReceiptNo(),
				canViewSource);
		List<ReversalTraceRecord> traces = purchaseTraceRecords(
				purchaseReverseTraceLinks(PURCHASE_RETURN_SOURCE, row.id(), null), currentUser, "SOURCE_TO_REVERSE");
		return new PurchaseReturnDetailResponse(row.id(), row.returnNo(), row.supplierId(), row.supplierName(),
				row.warehouseId(), row.warehouseName(), row.businessDate(), row.status().name(),
				quantity(totalQuantity(lines)), amount(row.totalAmount()), sourceView(PURCHASE_RECEIPT_SOURCE,
						row.sourceReceiptId(), null, row.sourceReceiptNo(), null, row.sourceBusinessDate(),
						row.sourceStatus(), null, null, canViewSource, "procurement-receipt-detail",
						Map.of("id", row.sourceReceiptId()), null),
				row.createdAt(), row.updatedAt(), row.clientRequestId(), row.remark(), lines, traces);
	}

	private ProductionMaterialReturnDetailResponse materialReturnDetail(MaterialReturnRow row,
			CurrentUser currentUser) {
		boolean canViewSource = canViewProductionIssue(currentUser);
		List<ReversalDocumentLine> lines = materialReturnLines(row.id(), row.sourceIssueId(), row.sourceIssueNo(),
				row.workOrderId(), canViewSource);
		List<ReversalTraceRecord> traces = productionTraceRecords(
				productionReverseTraceLinks(PRODUCTION_MATERIAL_RETURN_SOURCE, row.id(), null), currentUser,
				"SOURCE_TO_REVERSE");
		return new ProductionMaterialReturnDetailResponse(row.id(), row.returnNo(),
				canViewSource ? row.workOrderId() : null, canViewSource ? row.workOrderNo() : null,
				row.warehouseId(), row.warehouseName(), row.businessDate(), row.status().name(),
				quantity(totalQuantity(lines)), amount(totalLineAmount(lines)),
				sourceView(PRODUCTION_MATERIAL_ISSUE_SOURCE, row.sourceIssueId(), null, row.sourceIssueNo(), null,
						row.sourceBusinessDate(), row.sourceStatus(), null, null, canViewSource,
						"production-work-order-material-issues", Map.of("id", row.workOrderId()),
						Map.of("issueId", row.sourceIssueId())),
				row.createdAt(), row.updatedAt(), row.clientRequestId(), row.remark(), lines, traces);
	}

	private ProductionMaterialSupplementDetailResponse materialSupplementDetail(MaterialSupplementRow row,
			CurrentUser currentUser) {
		boolean canViewSource = canViewProductionWorkOrder(currentUser);
		List<ReversalDocumentLine> lines = materialSupplementLines(row.id(), row.workOrderId(), row.workOrderNo(),
				canViewSource);
		List<ReversalTraceRecord> traces = productionTraceRecords(
				productionReverseTraceLinks(PRODUCTION_MATERIAL_SUPPLEMENT_SOURCE, row.id(), null), currentUser,
				"SOURCE_TO_REVERSE");
		return new ProductionMaterialSupplementDetailResponse(row.id(), row.supplementNo(),
				canViewSource ? row.workOrderId() : null, canViewSource ? row.workOrderNo() : null,
				row.warehouseId(), row.warehouseName(), row.businessDate(), row.status().name(),
				quantity(totalQuantity(lines)), amount(totalLineAmount(lines)),
				sourceView(PRODUCTION_WORK_ORDER_SOURCE, row.workOrderId(), null, row.workOrderNo(), null,
						row.businessDate(), row.sourceStatus(), null, null, canViewSource,
						"production-work-order-detail", Map.of("id", row.workOrderId()), null),
				row.createdAt(), row.updatedAt(), row.clientRequestId(), row.remark(), lines, traces);
	}

	private SalesReturnSourceResponse mapSalesReturnSource(ResultSet rs) throws SQLException {
		Long shipmentId = rs.getLong("id");
		List<SalesReturnSourceLineResponse> lines = salesReturnSourceLines(shipmentId);
		return new SalesReturnSourceResponse(shipmentId, rs.getString("shipment_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), lines);
	}

	private PurchaseReturnSourceResponse mapPurchaseReturnSource(ResultSet rs) throws SQLException {
		Long receiptId = rs.getLong("id");
		List<PurchaseReturnSourceLineResponse> lines = purchaseReturnSourceLines(receiptId);
		return new PurchaseReturnSourceResponse(receiptId, rs.getString("receipt_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), lines);
	}

	private ProductionMaterialReturnSourceResponse mapMaterialReturnSource(ResultSet rs) throws SQLException {
		Long issueId = rs.getLong("id");
		List<ProductionMaterialReturnSourceLineResponse> lines = materialReturnSourceLines(issueId);
		return new ProductionMaterialReturnSourceResponse(issueId, rs.getString("issue_no"),
				rs.getLong("work_order_id"), rs.getString("work_order_no"), rs.getLong("warehouse_id"),
				rs.getString("warehouse_name"), rs.getObject("business_date", LocalDate.class),
				rs.getString("status"), lines);
	}

	private ProductionMaterialSupplementSourceResponse mapMaterialSupplementSource(ResultSet rs) throws SQLException {
		Long workOrderId = rs.getLong("id");
		Long warehouseId = rs.getLong("warehouse_id");
		List<ProductionMaterialSupplementSourceLineResponse> materials = materialSupplementSourceLines(workOrderId,
				warehouseId);
		return new ProductionMaterialSupplementSourceResponse(workOrderId, rs.getString("work_order_no"),
				rs.getString("work_order_status"), warehouseId, rs.getString("warehouse_name"), materials);
	}

	private SalesReturnSummaryResponse mapSalesReturnSummary(ResultSet rs, CurrentUser currentUser) throws SQLException {
		boolean canViewSource = canViewSalesShipment(currentUser);
		Long sourceShipmentId = rs.getLong("source_shipment_id");
		return new SalesReturnSummaryResponse(rs.getLong("id"), rs.getString("return_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				quantity(rs.getBigDecimal("total_quantity")), amount(rs.getBigDecimal("total_amount")),
				sourceView(SALES_SHIPMENT_SOURCE, sourceShipmentId, null, rs.getString("source_shipment_no"), null,
						rs.getObject("source_date", LocalDate.class), rs.getString("source_status"), null, null,
						canViewSource, "sales-shipment-detail", Map.of("id", sourceShipmentId), null),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private ProductionMaterialReturnSummaryResponse mapMaterialReturnSummary(ResultSet rs, CurrentUser currentUser)
			throws SQLException {
		boolean canViewSource = canViewProductionIssue(currentUser);
		Long sourceIssueId = rs.getLong("source_issue_id");
		Long workOrderId = rs.getLong("work_order_id");
		return new ProductionMaterialReturnSummaryResponse(rs.getLong("id"), rs.getString("return_no"),
				canViewSource ? workOrderId : null, canViewSource ? rs.getString("work_order_no") : null,
				rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				quantity(rs.getBigDecimal("total_quantity")), amount(rs.getBigDecimal("total_amount")),
				sourceView(PRODUCTION_MATERIAL_ISSUE_SOURCE, sourceIssueId, null, rs.getString("issue_no"), null,
						rs.getObject("source_date", LocalDate.class), rs.getString("source_status"), null, null,
						canViewSource, "production-work-order-material-issues", Map.of("id", workOrderId),
						Map.of("issueId", sourceIssueId)),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private ProductionMaterialSupplementSummaryResponse mapMaterialSupplementSummary(ResultSet rs,
			CurrentUser currentUser) throws SQLException {
		boolean canViewSource = canViewProductionWorkOrder(currentUser);
		Long workOrderId = rs.getLong("work_order_id");
		return new ProductionMaterialSupplementSummaryResponse(rs.getLong("id"), rs.getString("supplement_no"),
				canViewSource ? workOrderId : null, canViewSource ? rs.getString("work_order_no") : null,
				rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				quantity(rs.getBigDecimal("total_quantity")), amount(rs.getBigDecimal("total_amount")),
				sourceView(PRODUCTION_WORK_ORDER_SOURCE, workOrderId, null, rs.getString("work_order_no"), null,
						rs.getObject("business_date", LocalDate.class), rs.getString("source_status"), null, null,
						canViewSource, "production-work-order-detail", Map.of("id", workOrderId), null),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private PurchaseReturnSummaryResponse mapPurchaseReturnSummary(ResultSet rs, CurrentUser currentUser)
			throws SQLException {
		boolean canViewSource = canViewPurchaseReceipt(currentUser);
		Long sourceReceiptId = rs.getLong("source_receipt_id");
		return new PurchaseReturnSummaryResponse(rs.getLong("id"), rs.getString("return_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"),
				quantity(rs.getBigDecimal("total_quantity")), amount(rs.getBigDecimal("total_amount")),
				sourceView(PURCHASE_RECEIPT_SOURCE, sourceReceiptId, null, rs.getString("source_receipt_no"), null,
						rs.getObject("source_date", LocalDate.class), rs.getString("source_status"), null, null,
						canViewSource, "procurement-receipt-detail", Map.of("id", sourceReceiptId), null),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private List<SalesReturnSourceLineResponse> salesReturnSourceLines(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.order_line_id, sl.line_no, sl.material_id, m.code as material_code,
				       m.name as material_name, sl.unit_id, u.name as unit_name, sl.quantity,
				       coalesce((
				           select sum(rl.quantity)
				           from sal_sales_return_line rl
				           join sal_sales_return r on r.id = rl.return_id
				           where rl.source_shipment_line_id = sl.id
				           and r.status = 'POSTED'
				       ), 0) as returned_quantity,
				       ol.unit_price
				from sal_sales_shipment_line sl
				join sal_sales_order_line ol on ol.id = sl.order_line_id
				join mst_material m on m.id = sl.material_id
				join mst_unit u on u.id = sl.unit_id
				where sl.shipment_id = ?
				order by sl.line_no asc, sl.id asc
				""", (rs, rowNum) -> {
			BigDecimal shipped = rs.getBigDecimal("quantity");
			BigDecimal returned = rs.getBigDecimal("returned_quantity");
			BigDecimal returnable = shipped.subtract(returned);
			return new SalesReturnSourceLineResponse(rs.getLong("id"), rs.getLong("order_line_id"),
					rs.getInt("line_no"), rs.getLong("material_id"), rs.getString("material_code"),
					rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
					quantity(shipped), quantity(returned), quantity(returnable), quantity(rs.getBigDecimal("unit_price")),
					amount(returnable.multiply(rs.getBigDecimal("unit_price"))));
		}, shipmentId).stream().filter((line) -> new BigDecimal(line.returnableQuantity()).compareTo(ZERO) > 0)
			.toList();
	}

	private List<PurchaseReturnSourceLineResponse> purchaseReturnSourceLines(Long receiptId) {
		return this.jdbcTemplate.query("""
				select prl.id, prl.order_line_id, prl.line_no, prl.material_id, m.code as material_code,
				       m.name as material_name, prl.unit_id, u.name as unit_name, prl.quantity,
				       coalesce((
				           select sum(rl.quantity)
				           from proc_purchase_return_line rl
				           join proc_purchase_return r on r.id = rl.return_id
				           where rl.source_receipt_line_id = prl.id
				           and r.status = 'POSTED'
				       ), 0) as returned_quantity,
				       coalesce(sb.quantity_on_hand, 0) as available_stock_quantity,
				       pol.unit_price
				from proc_purchase_receipt_line prl
				join proc_purchase_order_line pol on pol.id = prl.order_line_id
				join proc_purchase_receipt pr on pr.id = prl.receipt_id
				join mst_material m on m.id = prl.material_id
				join mst_unit u on u.id = prl.unit_id
				left join inv_stock_balance sb on sb.warehouse_id = pr.warehouse_id
					and sb.material_id = prl.material_id
				where prl.receipt_id = ?
				order by prl.line_no asc, prl.id asc
				""", (rs, rowNum) -> {
			BigDecimal received = rs.getBigDecimal("quantity");
			BigDecimal returned = rs.getBigDecimal("returned_quantity");
			BigDecimal returnable = received.subtract(returned);
			return new PurchaseReturnSourceLineResponse(rs.getLong("id"), rs.getLong("order_line_id"),
					rs.getInt("line_no"), rs.getLong("material_id"), rs.getString("material_code"),
					rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
					quantity(received), quantity(returned), quantity(returnable),
					quantity(rs.getBigDecimal("available_stock_quantity")), quantity(rs.getBigDecimal("unit_price")),
					amount(returnable.multiply(rs.getBigDecimal("unit_price"))));
		}, receiptId).stream().filter((line) -> new BigDecimal(line.returnableQuantity()).compareTo(ZERO) > 0)
			.toList();
	}

	private List<ProductionMaterialReturnSourceLineResponse> materialReturnSourceLines(Long issueId) {
		return this.jdbcTemplate.query("""
				select l.id, l.work_order_material_id, l.line_no, l.warehouse_id, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.quantity,
				       coalesce((
				           select sum(rl.quantity)
				           from mfg_material_return_line rl
				           join mfg_material_return r on r.id = rl.return_id
				           where rl.source_issue_line_id = l.id
				           and r.status = 'POSTED'
				       ), 0) as returned_quantity,
				       coalesce((
				           select cr.unit_price
				           from mfg_cost_record cr
				           where cr.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
				           and cr.source_line_id = l.id
				           and cr.cost_type = 'MATERIAL'
				           and cr.status = 'ACTIVE'
				           order by cr.id desc
				           limit 1
				       ), 0) as unit_price
				from mfg_material_issue_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.issue_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> {
			BigDecimal issued = rs.getBigDecimal("quantity");
			BigDecimal returned = rs.getBigDecimal("returned_quantity");
			BigDecimal returnable = issued.subtract(returned);
			BigDecimal unitPrice = rs.getBigDecimal("unit_price");
			return new ProductionMaterialReturnSourceLineResponse(rs.getLong("id"),
					rs.getLong("work_order_material_id"), rs.getInt("line_no"), rs.getLong("warehouse_id"),
					rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
					rs.getLong("unit_id"), rs.getString("unit_name"), quantity(issued), quantity(returned),
					quantity(returnable), quantity(unitPrice), amount(returnable.multiply(unitPrice)));
		}, issueId).stream().filter((line) -> new BigDecimal(line.returnableQuantity()).compareTo(ZERO) > 0)
			.toList();
	}

	private List<ProductionMaterialSupplementSourceLineResponse> materialSupplementSourceLines(Long workOrderId,
			Long warehouseId) {
		return this.jdbcTemplate.query("""
				select wom.id, wom.line_no, wom.material_id, m.code as material_code, m.name as material_name,
				       wom.unit_id, u.name as unit_name, wom.required_quantity, wom.issued_quantity,
				       coalesce((
				           select sum(sl.quantity)
				           from mfg_material_supplement_line sl
				           join mfg_material_supplement s on s.id = sl.supplement_id
				           where sl.work_order_material_id = wom.id
				           and s.status = 'POSTED'
				       ), 0) as supplemented_quantity,
				       coalesce(sb.quantity_on_hand, 0) as available_stock_quantity,
				       coalesce((
				           select cr.unit_price
				           from mfg_cost_record cr
				           where cr.work_order_material_id = wom.id
				           and cr.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
				           and cr.cost_type = 'MATERIAL'
				           and cr.status = 'ACTIVE'
				           and cr.unit_price is not null
				           order by cr.id desc
				           limit 1
				       ), 0) as unit_price
				from mfg_work_order_material wom
				join mst_material m on m.id = wom.material_id
				join mst_unit u on u.id = wom.unit_id
				left join inv_stock_balance sb on sb.warehouse_id = ?
					and sb.material_id = wom.material_id
				where wom.work_order_id = ?
				order by wom.line_no asc, wom.id asc
				""", (rs, rowNum) -> new ProductionMaterialSupplementSourceLineResponse(rs.getLong("id"),
				rs.getInt("line_no"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				quantity(rs.getBigDecimal("required_quantity")), quantity(rs.getBigDecimal("issued_quantity")),
				quantity(rs.getBigDecimal("supplemented_quantity")),
				quantity(rs.getBigDecimal("available_stock_quantity")), quantity(rs.getBigDecimal("unit_price"))),
				warehouseId, workOrderId);
	}

	private List<ReversalDocumentLine> salesReturnLines(Long returnId, Long sourceShipmentId, String sourceShipmentNo,
			boolean canViewSource) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.source_shipment_line_id, l.sales_order_line_id, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.returned_quantity_before, l.returnable_quantity_before, l.quantity, l.unit_price,
				       l.amount, l.reason, l.stock_movement_id, ssl.line_no as source_line_no,
				       sh.business_date as source_business_date, sh.status as source_status
				from sal_sales_return_line l
				join sal_sales_shipment_line ssl on ssl.id = l.source_shipment_line_id
				join sal_sales_shipment sh on sh.id = ssl.shipment_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.return_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new ReversalDocumentLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("source_shipment_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				quantity(rs.getBigDecimal("returned_quantity_before")),
				quantity(rs.getBigDecimal("returnable_quantity_before")), quantity(rs.getBigDecimal("quantity")),
				quantity(rs.getBigDecimal("unit_price")), amount(rs.getBigDecimal("amount")), rs.getString("reason"),
				nullableLong(rs, "stock_movement_id"), null,
				sourceView(SALES_SHIPMENT_LINE_SOURCE, sourceShipmentId, rs.getLong("source_shipment_line_id"),
						sourceShipmentNo, rs.getInt("source_line_no"),
						rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
						quantity(rs.getBigDecimal("quantity")), amount(rs.getBigDecimal("amount")), canViewSource,
						"sales-shipment-detail", Map.of("id", sourceShipmentId),
						Map.of("lineId", rs.getLong("source_shipment_line_id")))),
				returnId);
	}

	private List<ReversalDocumentLine> materialReturnLines(Long returnId, Long sourceIssueId, String sourceIssueNo,
			Long workOrderId, boolean canViewSource) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.source_issue_line_id, l.work_order_material_id, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.returned_quantity_before, l.returnable_quantity_before, l.quantity,
				       coalesce(cr.unit_price, source_cost.unit_price, 0) as unit_price,
				       coalesce(cr.amount, l.quantity * coalesce(source_cost.unit_price, 0)) as amount,
				       l.reason, l.stock_movement_id, l.cost_record_id, il.line_no as source_line_no,
				       i.business_date as source_business_date, i.status as source_status
				from mfg_material_return_line l
				join mfg_material_issue_line il on il.id = l.source_issue_line_id
				join mfg_material_issue i on i.id = il.issue_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				left join mfg_cost_record cr on cr.id = l.cost_record_id
				left join lateral (
					select crs.unit_price
					from mfg_cost_record crs
					where crs.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
					and crs.source_line_id = l.source_issue_line_id
					and crs.cost_type = 'MATERIAL'
					and crs.status = 'ACTIVE'
					order by crs.id desc
					limit 1
				) source_cost on true
				where l.return_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new ReversalDocumentLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("source_issue_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				quantity(rs.getBigDecimal("returned_quantity_before")),
				quantity(rs.getBigDecimal("returnable_quantity_before")), quantity(rs.getBigDecimal("quantity")),
				quantity(rs.getBigDecimal("unit_price")), amount(rs.getBigDecimal("amount")), rs.getString("reason"),
				nullableLong(rs, "stock_movement_id"), nullableLong(rs, "cost_record_id"),
				sourceView(PRODUCTION_MATERIAL_ISSUE_LINE_SOURCE, sourceIssueId, rs.getLong("source_issue_line_id"),
						sourceIssueNo, rs.getInt("source_line_no"),
						rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
						quantity(rs.getBigDecimal("quantity")), amount(rs.getBigDecimal("amount")), canViewSource,
						"production-work-order-material-issues", Map.of("id", workOrderId),
						Map.of("issueId", sourceIssueId, "lineId", rs.getLong("source_issue_line_id")))),
				returnId);
	}

	private List<ReversalDocumentLine> materialSupplementLines(Long supplementId, Long workOrderId,
			String workOrderNo, boolean canViewSource) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.work_order_material_id, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.issued_quantity_before, l.available_stock_quantity_before, l.quantity,
				       coalesce(cr.unit_price, source_cost.unit_price, 0) as unit_price,
				       coalesce(cr.amount, l.quantity * coalesce(source_cost.unit_price, 0)) as amount,
				       l.reason, l.stock_movement_id, l.cost_record_id, wom.line_no as source_line_no,
				       wo.planned_start_date as source_business_date, wo.status as source_status
				from mfg_material_supplement_line l
				join mfg_work_order_material wom on wom.id = l.work_order_material_id
				join mfg_work_order wo on wo.id = wom.work_order_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				left join mfg_cost_record cr on cr.id = l.cost_record_id
				left join lateral (
					select crs.unit_price
					from mfg_cost_record crs
					where crs.work_order_material_id = l.work_order_material_id
					and crs.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
					and crs.cost_type = 'MATERIAL'
					and crs.status = 'ACTIVE'
					and crs.unit_price is not null
					order by crs.id desc
					limit 1
				) source_cost on true
				where l.supplement_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new ReversalDocumentLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("work_order_material_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				quantity(rs.getBigDecimal("issued_quantity_before")),
				quantity(rs.getBigDecimal("available_stock_quantity_before")), quantity(rs.getBigDecimal("quantity")),
				quantity(rs.getBigDecimal("unit_price")), amount(rs.getBigDecimal("amount")), rs.getString("reason"),
				nullableLong(rs, "stock_movement_id"), nullableLong(rs, "cost_record_id"),
				sourceView(PRODUCTION_WORK_ORDER_SOURCE, workOrderId, rs.getLong("work_order_material_id"),
						workOrderNo, rs.getInt("source_line_no"),
						rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
						quantity(rs.getBigDecimal("quantity")), amount(rs.getBigDecimal("amount")), canViewSource,
						"production-work-order-detail", Map.of("id", workOrderId),
						Map.of("lineId", rs.getLong("work_order_material_id")))),
				supplementId);
	}

	private List<ReversalDocumentLine> purchaseReturnLines(Long returnId, Long sourceReceiptId, String sourceReceiptNo,
			boolean canViewSource) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.source_receipt_line_id, l.purchase_order_line_id, l.material_id,
				       m.code as material_code, m.name as material_name, l.unit_id, u.name as unit_name,
				       l.returned_quantity_before, l.returnable_quantity_before, l.quantity, l.unit_price,
				       l.amount, l.reason, l.stock_movement_id, prl.line_no as source_line_no,
				       pr.business_date as source_business_date, pr.status as source_status
				from proc_purchase_return_line l
				join proc_purchase_receipt_line prl on prl.id = l.source_receipt_line_id
				join proc_purchase_receipt pr on pr.id = prl.receipt_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.return_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new ReversalDocumentLine(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("source_receipt_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				quantity(rs.getBigDecimal("returned_quantity_before")),
				quantity(rs.getBigDecimal("returnable_quantity_before")), quantity(rs.getBigDecimal("quantity")),
				quantity(rs.getBigDecimal("unit_price")), amount(rs.getBigDecimal("amount")), rs.getString("reason"),
				nullableLong(rs, "stock_movement_id"), null,
				sourceView(PURCHASE_RECEIPT_LINE_SOURCE, sourceReceiptId, rs.getLong("source_receipt_line_id"),
						sourceReceiptNo, rs.getInt("source_line_no"),
						rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
						quantity(rs.getBigDecimal("quantity")), amount(rs.getBigDecimal("amount")), canViewSource,
						"procurement-receipt-detail", Map.of("id", sourceReceiptId),
						Map.of("lineId", rs.getLong("source_receipt_line_id")))),
				returnId);
	}

	private QueryParts salesReturnSourceQuery(String keyword, Long customerId, Long warehouseId, LocalDate dateFrom,
			LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("sh.status = 'POSTED'");
		conditions.add("""
				exists (
					select 1
					from sal_sales_shipment_line slx
					where slx.shipment_id = sh.id
					and slx.quantity > coalesce((
						select sum(rl.quantity)
						from sal_sales_return_line rl
						join sal_sales_return r on r.id = rl.return_id
						where rl.source_shipment_line_id = slx.id
						and r.status = 'POSTED'
					), 0)
				)
				""");
		if (hasText(keyword)) {
			conditions.add("""
					(sh.shipment_no ilike ? or c.name ilike ? or exists (
						select 1
						from sal_sales_shipment_line sl
						join mst_material m on m.id = sl.material_id
						where sl.shipment_id = sh.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
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
		if (warehouseId != null) {
			conditions.add("sh.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (dateFrom != null) {
			conditions.add("sh.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("sh.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts salesReturnQuery(String keyword, Long customerId, Long warehouseId, String status,
			LocalDate dateFrom, LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(r.return_no ilike ? or r.source_shipment_no ilike ? or c.name ilike ? or r.remark ilike ?
					or exists (
						select 1
						from sal_sales_return_line rl
						join mst_material m on m.id = rl.material_id
						where rl.return_id = r.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
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
		if (warehouseId != null) {
			conditions.add("r.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (hasText(status)) {
			conditions.add("r.status = ?");
			args.add(parseReversalStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("r.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("r.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts purchaseReturnSourceQuery(String keyword, Long supplierId, Long warehouseId, LocalDate dateFrom,
			LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("pr.status = 'POSTED'");
		conditions.add("""
				exists (
					select 1
					from proc_purchase_receipt_line prlx
					where prlx.receipt_id = pr.id
					and prlx.quantity > coalesce((
						select sum(rl.quantity)
						from proc_purchase_return_line rl
						join proc_purchase_return r on r.id = rl.return_id
						where rl.source_receipt_line_id = prlx.id
						and r.status = 'POSTED'
					), 0)
				)
				""");
		if (hasText(keyword)) {
			conditions.add("""
					(pr.receipt_no ilike ? or s.name ilike ? or exists (
						select 1
						from proc_purchase_receipt_line prl
						join mst_material m on m.id = prl.material_id
						where prl.receipt_id = pr.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
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
		if (warehouseId != null) {
			conditions.add("pr.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (dateFrom != null) {
			conditions.add("pr.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("pr.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts purchaseReturnQuery(String keyword, Long supplierId, Long warehouseId, String status,
			LocalDate dateFrom, LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(r.return_no ilike ? or r.source_receipt_no ilike ? or s.name ilike ? or r.remark ilike ?
					or exists (
						select 1
						from proc_purchase_return_line rl
						join mst_material m on m.id = rl.material_id
						where rl.return_id = r.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (supplierId != null) {
			conditions.add("r.supplier_id = ?");
			args.add(supplierId);
		}
		if (warehouseId != null) {
			conditions.add("r.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (hasText(status)) {
			conditions.add("r.status = ?");
			args.add(parseReversalStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("r.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("r.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts materialReturnSourceQuery(String keyword, Long workOrderId, Long warehouseId,
			LocalDate dateFrom, LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("i.status = 'POSTED'");
		conditions.add("""
				exists (
					select 1
					from mfg_material_issue_line lx
					where lx.issue_id = i.id
					%s
					and lx.quantity > coalesce((
						select sum(rl.quantity)
						from mfg_material_return_line rl
						join mfg_material_return r on r.id = rl.return_id
						where rl.source_issue_line_id = lx.id
						and r.status = 'POSTED'
					), 0)
				)
				""".formatted(warehouseId == null ? "" : "and lx.warehouse_id = ?"));
		if (warehouseId != null) {
			args.add(warehouseId);
		}
		if (hasText(keyword)) {
			conditions.add("""
					(i.issue_no ilike ? or wo.work_order_no ilike ? or exists (
						select 1
						from mfg_material_issue_line l
						join mst_material m on m.id = l.material_id
						where l.issue_id = i.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (workOrderId != null) {
			conditions.add("i.work_order_id = ?");
			args.add(workOrderId);
		}
		if (dateFrom != null) {
			conditions.add("i.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("i.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts materialReturnQuery(String keyword, Long workOrderId, Long warehouseId, String status,
			LocalDate dateFrom, LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(r.return_no ilike ? or i.issue_no ilike ? or wo.work_order_no ilike ? or r.remark ilike ?
					or exists (
						select 1
						from mfg_material_return_line rl
						join mst_material m on m.id = rl.material_id
						where rl.return_id = r.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (workOrderId != null) {
			conditions.add("r.work_order_id = ?");
			args.add(workOrderId);
		}
		if (warehouseId != null) {
			conditions.add("r.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (hasText(status)) {
			conditions.add("r.status = ?");
			args.add(parseReversalStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("r.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("r.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private QueryParts materialSupplementSourceQuery(String keyword, Long workOrderId, Long warehouseId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("wo.status in ('RELEASED', 'IN_PROGRESS')");
		conditions.add("exists (select 1 from mfg_work_order_material wom where wom.work_order_id = wo.id)");
		if (hasText(keyword)) {
			conditions.add("""
					(wo.work_order_no ilike ? or exists (
						select 1
						from mfg_work_order_material wom
						join mst_material m on m.id = wom.material_id
						where wom.work_order_id = wo.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (workOrderId != null) {
			conditions.add("wo.id = ?");
			args.add(workOrderId);
		}
		if (warehouseId != null) {
			conditions.add("w.id = ?");
			args.add(warehouseId);
		}
		return where(conditions, args);
	}

	private QueryParts materialSupplementQuery(String keyword, Long workOrderId, Long warehouseId, String status,
			LocalDate dateFrom, LocalDate dateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(s.supplement_no ilike ? or wo.work_order_no ilike ? or s.remark ilike ?
					or exists (
						select 1
						from mfg_material_supplement_line sl
						join mst_material m on m.id = sl.material_id
						where sl.supplement_id = s.id
						and (m.code ilike ? or m.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (workOrderId != null) {
			conditions.add("s.work_order_id = ?");
			args.add(workOrderId);
		}
		if (warehouseId != null) {
			conditions.add("s.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (hasText(status)) {
			conditions.add("s.status = ?");
			args.add(parseReversalStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("s.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("s.business_date <= ?");
			args.add(dateTo);
		}
		return where(conditions, args);
	}

	private ValidatedSalesReturn validateSalesReturnCreate(SalesReturnRequest request) {
		if (request == null || request.sourceShipmentId() == null || request.businessDate() == null
				|| request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		if (hasText(request.clientRequestId()) && request.clientRequestId().length() > 64) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.remark() != null && request.remark().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new ValidatedSalesReturn(request.sourceShipmentId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private ValidatedSalesReturn validateSalesReturnUpdate(SalesReturnRow current, SalesReturnRequest request) {
		if (request == null || request.businessDate() == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		if (request.sourceShipmentId() != null && !current.sourceShipmentId().equals(request.sourceShipmentId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		if (hasText(request.clientRequestId()) && request.clientRequestId().length() > 64) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.remark() != null && request.remark().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new ValidatedSalesReturn(current.sourceShipmentId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private List<ValidatedSalesReturnLine> validateSalesReturnLines(ShipmentRow shipment,
			List<SalesReturnLineRequest> requests) {
		return validateSalesReturnLines(shipment, requests, List.of());
	}

	private List<ValidatedSalesReturnLine> validateSalesReturnLines(ShipmentRow shipment,
			List<SalesReturnLineRequest> requests, List<SalesReturnLineRow> currentLines) {
		Map<Long, SalesReturnLineRow> currentLineById = new LinkedHashMap<>();
		for (SalesReturnLineRow line : currentLines) {
			currentLineById.put(line.id(), line);
		}
		Set<Long> sourceLineIds = new HashSet<>();
		List<ValidatedSalesReturnLine> lines = new ArrayList<>();
		int lineNo = 1;
		for (SalesReturnLineRequest request : requests) {
			Long sourceShipmentLineId = resolveSalesSourceShipmentLineId(request, currentLineById);
			if (sourceShipmentLineId == null || !sourceLineIds.add(sourceShipmentLineId)) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			BigDecimal quantity = validateQuantity(request.quantity());
			ShipmentLineRow sourceLine = shipmentLine(shipment.id(), sourceShipmentLineId)
				.orElseThrow(this::sourceNotFoundException);
			BigDecimal returnedQuantity = postedReturnedQuantity(sourceLine.id());
			BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
			if (quantity.compareTo(returnableQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
			}
			String reason = request.reason();
			if (reason != null && reason.length() > 200) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			BigDecimal amount = money(quantity.multiply(sourceLine.unitPrice()));
			lines.add(new ValidatedSalesReturnLine(lineNo++, sourceLine.id(), sourceLine.orderLineId(),
					sourceLine.materialId(), sourceLine.unitId(), returnedQuantity, returnableQuantity, quantity,
					sourceLine.unitPrice(), amount, blankToNull(reason)));
		}
		return lines;
	}

	private Long resolveSalesSourceShipmentLineId(SalesReturnLineRequest request,
			Map<Long, SalesReturnLineRow> currentLineById) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		Long sourceShipmentLineId = request.sourceShipmentLineId();
		if (request.id() != null) {
			SalesReturnLineRow currentLine = currentLineById.get(request.id());
			if (currentLine == null) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			if (sourceShipmentLineId != null && !sourceShipmentLineId.equals(currentLine.sourceShipmentLineId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			sourceShipmentLineId = currentLine.sourceShipmentLineId();
		}
		return sourceShipmentLineId;
	}

	private void validateLineStillReturnable(ShipmentLineRow sourceLine, SalesReturnLineRow line) {
		if (!sourceLine.materialId().equals(line.materialId()) || !sourceLine.unitId().equals(line.unitId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		BigDecimal returnedQuantity = postedReturnedQuantity(sourceLine.id());
		BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
		if (line.quantity().compareTo(returnableQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
		}
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_INVALID);
		}
		return value;
	}

	private void validateReversalCommonFields(String clientRequestId, String remark) {
		if (hasText(clientRequestId) && clientRequestId.length() > 64) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (remark != null && remark.length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private String validateReason(String reason) {
		if (reason != null && reason.length() > 200) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return blankToNull(reason);
	}

	private CreatedDocument insertSalesReturnWithRetry(ShipmentRow shipment, ValidatedSalesReturn salesReturn,
			BigDecimal totalAmount, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String returnNo = nextNo("SR", SALES_RETURN_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into sal_sales_return (
							return_no, customer_id, source_shipment_id, source_shipment_no, warehouse_id,
							business_date, status, total_amount, client_request_id, remark, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, returnNo, shipment.customerId(), shipment.id(), shipment.shipmentNo(),
						shipment.warehouseId(), salesReturn.businessDate(), ReversalDocumentStatus.DRAFT.name(),
						totalAmount, blankToNull(salesReturn.clientRequestId()), blankToNull(salesReturn.remark()),
						operator, now, operator, now);
				return new CreatedDocument(id, returnNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_sal_sales_return_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertSalesReturnLines(Long returnId, List<ValidatedSalesReturnLine> lines, OffsetDateTime now) {
		for (ValidatedSalesReturnLine line : lines) {
			this.jdbcTemplate.update("""
					insert into sal_sales_return_line (
						return_id, source_shipment_line_id, sales_order_line_id, material_id, unit_id, line_no,
						returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
						created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", returnId, line.sourceShipmentLineId(), line.salesOrderLineId(), line.materialId(),
					line.unitId(), line.lineNo(), line.returnedQuantityBefore(), line.returnableQuantityBefore(),
					line.quantity(), line.unitPrice(), line.amount(), blankToNull(line.reason()), now, now);
		}
	}

	private Long insertPostedReceivableAdjustment(SalesReturnRow salesReturn, ReceivableRow receivable,
			BigDecimal amount, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String adjustmentNo = nextNo("ADJ", SETTLEMENT_ADJUSTMENT_NO_SEQUENCE);
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into fin_settlement_adjustment (
							adjustment_no, settlement_side, adjustment_type, source_type, source_id, target_id,
							business_date, amount, status, remark, created_by, created_at, updated_by, updated_at,
							posted_by, posted_at
						)
						values (?, 'RECEIVABLE', 'RETURN_OFFSET', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, adjustmentNo, SALES_RETURN_SOURCE, salesReturn.id(), receivable.id(),
						salesReturn.businessDate(), amount, ReversalDocumentStatus.POSTED.name(), "销售退货冲减应收",
						operator, now, operator, now, operator, now);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_fin_settlement_adjustment_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void applyReceivableAdjustment(ReceivableRow receivable, BigDecimal amount, String operator,
			OffsetDateTime now) {
		BigDecimal adjustedAmount = money(receivable.adjustedAmount().add(amount));
		BigDecimal unreceivedAmount = money(receivable.unreceivedAmount().subtract(amount));
		ReceivableStatus nextStatus = unreceivedAmount.compareTo(ZERO) == 0 ? ReceivableStatus.RECEIVED
				: ReceivableStatus.PARTIALLY_RECEIVED;
		this.jdbcTemplate.update("""
				update fin_receivable
				set adjusted_amount = ?, unreceived_amount = ?, status = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", adjustedAmount, unreceivedAmount, nextStatus.name(), operator, now, receivable.id());
	}

	private void insertReversalLink(ShipmentRow shipment, ShipmentLineRow sourceLine, SalesReturnRow salesReturn,
			SalesReturnLineRow returnLine, String operator, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into biz_reversal_link (
					source_type, source_id, source_line_id, reverse_type, reverse_id, reverse_line_id, business_date,
					quantity, amount, created_by, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", SALES_SHIPMENT_SOURCE, shipment.id(), sourceLine.id(), SALES_RETURN_SOURCE, salesReturn.id(),
				returnLine.id(), salesReturn.businessDate(), returnLine.quantity(), returnLine.amount(), operator, now);
	}

	private ValidatedPurchaseReturn validatePurchaseReturnCreate(PurchaseReturnRequest request) {
		if (request == null || request.sourceReceiptId() == null || request.businessDate() == null
				|| request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		if (hasText(request.clientRequestId()) && request.clientRequestId().length() > 64) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.remark() != null && request.remark().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new ValidatedPurchaseReturn(request.sourceReceiptId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private ValidatedPurchaseReturn validatePurchaseReturnUpdate(PurchaseReturnRow current, PurchaseReturnRequest request) {
		if (request == null || request.businessDate() == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		if (request.sourceReceiptId() != null && !current.sourceReceiptId().equals(request.sourceReceiptId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		if (hasText(request.clientRequestId()) && request.clientRequestId().length() > 64) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.remark() != null && request.remark().length() > 500) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return new ValidatedPurchaseReturn(current.sourceReceiptId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private List<ValidatedPurchaseReturnLine> validatePurchaseReturnLines(ReceiptRow receipt,
			List<PurchaseReturnLineRequest> requests) {
		return validatePurchaseReturnLines(receipt, requests, List.of());
	}

	private List<ValidatedPurchaseReturnLine> validatePurchaseReturnLines(ReceiptRow receipt,
			List<PurchaseReturnLineRequest> requests, List<PurchaseReturnLineRow> currentLines) {
		Map<Long, PurchaseReturnLineRow> currentLineById = new LinkedHashMap<>();
		for (PurchaseReturnLineRow line : currentLines) {
			currentLineById.put(line.id(), line);
		}
		Set<Long> sourceLineIds = new HashSet<>();
		List<ValidatedPurchaseReturnLine> lines = new ArrayList<>();
		int lineNo = 1;
		for (PurchaseReturnLineRequest request : requests) {
			Long sourceReceiptLineId = resolvePurchaseSourceReceiptLineId(request, currentLineById);
			if (sourceReceiptLineId == null || !sourceLineIds.add(sourceReceiptLineId)) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			BigDecimal quantity = validateQuantity(request.quantity());
			ReceiptLineRow sourceLine = receiptLine(receipt.id(), sourceReceiptLineId)
				.orElseThrow(this::sourceNotFoundException);
			BigDecimal returnedQuantity = postedPurchaseReturnedQuantity(sourceLine.id());
			BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
			if (quantity.compareTo(returnableQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
			}
			String reason = request.reason();
			if (reason != null && reason.length() > 200) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			BigDecimal amount = money(quantity.multiply(sourceLine.unitPrice()));
			lines.add(new ValidatedPurchaseReturnLine(lineNo++, sourceLine.id(), sourceLine.orderLineId(),
					sourceLine.materialId(), sourceLine.unitId(), returnedQuantity, returnableQuantity, quantity,
					sourceLine.unitPrice(), amount, blankToNull(reason)));
		}
		return lines;
	}

	private Long resolvePurchaseSourceReceiptLineId(PurchaseReturnLineRequest request,
			Map<Long, PurchaseReturnLineRow> currentLineById) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		Long sourceReceiptLineId = request.sourceReceiptLineId();
		if (request.id() != null) {
			PurchaseReturnLineRow currentLine = currentLineById.get(request.id());
			if (currentLine == null) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			if (sourceReceiptLineId != null && !sourceReceiptLineId.equals(currentLine.sourceReceiptLineId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			sourceReceiptLineId = currentLine.sourceReceiptLineId();
		}
		return sourceReceiptLineId;
	}

	private void validatePurchaseLineStillReturnable(ReceiptLineRow sourceLine, PurchaseReturnLineRow line) {
		if (!sourceLine.materialId().equals(line.materialId()) || !sourceLine.unitId().equals(line.unitId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		BigDecimal returnedQuantity = postedPurchaseReturnedQuantity(sourceLine.id());
		BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
		if (line.quantity().compareTo(returnableQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
		}
	}

	private CreatedDocument insertPurchaseReturnWithRetry(ReceiptRow receipt, ValidatedPurchaseReturn purchaseReturn,
			BigDecimal totalAmount, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String returnNo = nextNo("PR", PURCHASE_RETURN_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into proc_purchase_return (
							return_no, supplier_id, source_receipt_id, source_receipt_no, warehouse_id,
							business_date, status, total_amount, client_request_id, remark, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, returnNo, receipt.supplierId(), receipt.id(), receipt.receiptNo(),
						receipt.warehouseId(), purchaseReturn.businessDate(), ReversalDocumentStatus.DRAFT.name(),
						totalAmount, blankToNull(purchaseReturn.clientRequestId()), blankToNull(purchaseReturn.remark()),
						operator, now, operator, now);
				return new CreatedDocument(id, returnNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_proc_purchase_return_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertPurchaseReturnLines(Long returnId, List<ValidatedPurchaseReturnLine> lines, OffsetDateTime now) {
		for (ValidatedPurchaseReturnLine line : lines) {
			this.jdbcTemplate.update("""
					insert into proc_purchase_return_line (
						return_id, source_receipt_line_id, purchase_order_line_id, material_id, unit_id, line_no,
						returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
						created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", returnId, line.sourceReceiptLineId(), line.purchaseOrderLineId(), line.materialId(),
					line.unitId(), line.lineNo(), line.returnedQuantityBefore(), line.returnableQuantityBefore(),
					line.quantity(), line.unitPrice(), line.amount(), blankToNull(line.reason()), now, now);
		}
	}

	private Long insertPostedPayableAdjustment(PurchaseReturnRow purchaseReturn, PayableRow payable,
			BigDecimal amount, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String adjustmentNo = nextNo("ADJ", SETTLEMENT_ADJUSTMENT_NO_SEQUENCE);
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into fin_settlement_adjustment (
							adjustment_no, settlement_side, adjustment_type, source_type, source_id, target_id,
							business_date, amount, status, remark, created_by, created_at, updated_by, updated_at,
							posted_by, posted_at
						)
						values (?, 'PAYABLE', 'RETURN_OFFSET', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, adjustmentNo, PURCHASE_RETURN_SOURCE, purchaseReturn.id(), payable.id(),
						purchaseReturn.businessDate(), amount, ReversalDocumentStatus.POSTED.name(), "采购退货冲减应付",
						operator, now, operator, now, operator, now);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_fin_settlement_adjustment_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void applyPayableAdjustment(PayableRow payable, BigDecimal amount, String operator, OffsetDateTime now) {
		BigDecimal adjustedAmount = money(payable.adjustedAmount().add(amount));
		BigDecimal unpaidAmount = money(payable.unpaidAmount().subtract(amount));
		PayableStatus nextStatus = unpaidAmount.compareTo(ZERO) == 0 ? PayableStatus.PAID
				: PayableStatus.PARTIALLY_PAID;
		this.jdbcTemplate.update("""
				update fin_payable
				set adjusted_amount = ?, unpaid_amount = ?, status = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", adjustedAmount, unpaidAmount, nextStatus.name(), operator, now, payable.id());
	}

	private void insertPurchaseReversalLink(ReceiptRow receipt, ReceiptLineRow sourceLine,
			PurchaseReturnRow purchaseReturn, PurchaseReturnLineRow returnLine, String operator, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into biz_reversal_link (
					source_type, source_id, source_line_id, reverse_type, reverse_id, reverse_line_id, business_date,
					quantity, amount, created_by, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", PURCHASE_RECEIPT_SOURCE, receipt.id(), sourceLine.id(), PURCHASE_RETURN_SOURCE,
				purchaseReturn.id(), returnLine.id(), purchaseReturn.businessDate(), returnLine.quantity(),
				returnLine.amount(), operator, now);
	}

	private ValidatedMaterialReturn validateMaterialReturnCreate(ProductionMaterialReturnRequest request) {
		if (request == null || request.sourceIssueId() == null || request.businessDate() == null
				|| request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		validateReversalCommonFields(request.clientRequestId(), request.remark());
		return new ValidatedMaterialReturn(request.sourceIssueId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private ValidatedMaterialReturn validateMaterialReturnUpdate(MaterialReturnRow current,
			ProductionMaterialReturnRequest request) {
		if (request == null || request.businessDate() == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		if (request.sourceIssueId() != null && !current.sourceIssueId().equals(request.sourceIssueId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		validateReversalCommonFields(request.clientRequestId(), request.remark());
		return new ValidatedMaterialReturn(current.sourceIssueId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private List<ValidatedMaterialReturnLine> validateMaterialReturnLines(ProductionIssueRow issue,
			List<ProductionMaterialReturnLineRequest> requests) {
		return validateMaterialReturnLines(issue, requests, List.of());
	}

	private List<ValidatedMaterialReturnLine> validateMaterialReturnLines(ProductionIssueRow issue,
			List<ProductionMaterialReturnLineRequest> requests, List<MaterialReturnLineRow> currentLines) {
		Map<Long, MaterialReturnLineRow> currentLineById = new LinkedHashMap<>();
		for (MaterialReturnLineRow line : currentLines) {
			currentLineById.put(line.id(), line);
		}
		Set<Long> sourceLineIds = new HashSet<>();
		Long warehouseId = null;
		List<ValidatedMaterialReturnLine> lines = new ArrayList<>();
		int lineNo = 1;
		for (ProductionMaterialReturnLineRequest request : requests) {
			Long sourceIssueLineId = resolveMaterialReturnSourceLineId(request, currentLineById);
			if (sourceIssueLineId == null || !sourceLineIds.add(sourceIssueLineId)) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			BigDecimal quantity = validateQuantity(request.quantity());
			ProductionIssueLineRow sourceLine = productionIssueLine(issue.id(), sourceIssueLineId)
				.orElseThrow(this::sourceNotFoundException);
			if (warehouseId == null) {
				warehouseId = sourceLine.warehouseId();
			}
			else if (!warehouseId.equals(sourceLine.warehouseId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			BigDecimal returnedQuantity = postedMaterialReturnedQuantity(sourceLine.id());
			BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
			if (quantity.compareTo(returnableQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
			}
			String reason = validateReason(request.reason());
			BigDecimal unitPrice = materialIssueUnitPrice(sourceLine.id(), sourceLine.workOrderMaterialId());
			BigDecimal amount = money(quantity.multiply(unitPrice));
			lines.add(new ValidatedMaterialReturnLine(lineNo++, sourceLine.id(), sourceLine.workOrderMaterialId(),
					sourceLine.warehouseId(), sourceLine.materialId(), sourceLine.unitId(), returnedQuantity,
					returnableQuantity, quantity, unitPrice, amount, reason));
		}
		return lines;
	}

	private Long resolveMaterialReturnSourceLineId(ProductionMaterialReturnLineRequest request,
			Map<Long, MaterialReturnLineRow> currentLineById) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		Long sourceIssueLineId = request.sourceIssueLineId();
		if (request.id() != null) {
			MaterialReturnLineRow currentLine = currentLineById.get(request.id());
			if (currentLine == null) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			if (sourceIssueLineId != null && !sourceIssueLineId.equals(currentLine.sourceIssueLineId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			sourceIssueLineId = currentLine.sourceIssueLineId();
		}
		return sourceIssueLineId;
	}

	private void validateMaterialReturnLineStillReturnable(ProductionIssueLineRow sourceLine,
			MaterialReturnLineRow line) {
		if (!sourceLine.workOrderMaterialId().equals(line.workOrderMaterialId())
				|| !sourceLine.warehouseId().equals(line.warehouseId()) || !sourceLine.materialId().equals(line.materialId())
				|| !sourceLine.unitId().equals(line.unitId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		BigDecimal returnedQuantity = postedMaterialReturnedQuantity(sourceLine.id());
		BigDecimal returnableQuantity = sourceLine.quantity().subtract(returnedQuantity);
		if (line.quantity().compareTo(returnableQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_QUANTITY_EXCEEDS_AVAILABLE);
		}
	}

	private CreatedDocument insertMaterialReturnWithRetry(ProductionIssueRow issue,
			ValidatedMaterialReturn materialReturn, Long warehouseId, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String returnNo = nextNo("MR", MATERIAL_RETURN_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_material_return (
							return_no, work_order_id, source_issue_id, warehouse_id, business_date, status,
							client_request_id, remark, created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, returnNo, issue.workOrderId(), issue.id(), warehouseId,
						materialReturn.businessDate(), ReversalDocumentStatus.DRAFT.name(),
						blankToNull(materialReturn.clientRequestId()), blankToNull(materialReturn.remark()), operator,
						now, operator, now);
				return new CreatedDocument(id, returnNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_material_return_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertMaterialReturnLines(Long returnId, List<ValidatedMaterialReturnLine> lines,
			OffsetDateTime now) {
		for (ValidatedMaterialReturnLine line : lines) {
			this.jdbcTemplate.update("""
					insert into mfg_material_return_line (
						return_id, source_issue_line_id, work_order_material_id, material_id, unit_id, line_no,
						returned_quantity_before, returnable_quantity_before, quantity, reason, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", returnId, line.sourceIssueLineId(), line.workOrderMaterialId(), line.materialId(),
					line.unitId(), line.lineNo(), line.returnedQuantityBefore(), line.returnableQuantityBefore(),
					line.quantity(), blankToNull(line.reason()), now, now);
		}
	}

	private ValidatedMaterialSupplement validateMaterialSupplementCreate(ProductionMaterialSupplementRequest request) {
		if (request == null || request.workOrderId() == null || request.warehouseId() == null
				|| request.businessDate() == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		validateReversalCommonFields(request.clientRequestId(), request.remark());
		return new ValidatedMaterialSupplement(request.workOrderId(), request.warehouseId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private ValidatedMaterialSupplement validateMaterialSupplementUpdate(MaterialSupplementRow current,
			ProductionMaterialSupplementRequest request) {
		if (request == null || request.businessDate() == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
		if (request.workOrderId() != null && !current.workOrderId().equals(request.workOrderId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		if (request.warehouseId() != null && !current.warehouseId().equals(request.warehouseId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
		validateReversalCommonFields(request.clientRequestId(), request.remark());
		return new ValidatedMaterialSupplement(current.workOrderId(), current.warehouseId(), request.businessDate(),
				blankToNull(request.clientRequestId()), blankToNull(request.remark()), request.lines());
	}

	private List<ValidatedMaterialSupplementLine> validateMaterialSupplementLines(ProductionWorkOrderRow workOrder,
			Long warehouseId, List<ProductionMaterialSupplementLineRequest> requests) {
		return validateMaterialSupplementLines(workOrder, warehouseId, requests, List.of());
	}

	private List<ValidatedMaterialSupplementLine> validateMaterialSupplementLines(ProductionWorkOrderRow workOrder,
			Long warehouseId, List<ProductionMaterialSupplementLineRequest> requests,
			List<MaterialSupplementLineRow> currentLines) {
		Map<Long, MaterialSupplementLineRow> currentLineById = new LinkedHashMap<>();
		for (MaterialSupplementLineRow line : currentLines) {
			currentLineById.put(line.id(), line);
		}
		Set<Long> workOrderMaterialIds = new HashSet<>();
		List<ValidatedMaterialSupplementLine> lines = new ArrayList<>();
		int lineNo = 1;
		for (ProductionMaterialSupplementLineRequest request : requests) {
			Long workOrderMaterialId = resolveMaterialSupplementWorkOrderMaterialId(request, currentLineById);
			if (workOrderMaterialId == null || !workOrderMaterialIds.add(workOrderMaterialId)) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			BigDecimal quantity = validateQuantity(request.quantity());
			ProductionWorkOrderMaterialRow sourceLine = productionWorkOrderMaterial(workOrder.id(), workOrderMaterialId)
				.orElseThrow(this::sourceNotFoundException);
			String reason = validateReason(request.reason());
			BigDecimal availableStockQuantity = stockQuantity(warehouseId, sourceLine.materialId());
			BigDecimal supplementedQuantity = postedMaterialSupplementedQuantity(sourceLine.id());
			BigDecimal unitPrice = materialIssueUnitPrice(null, sourceLine.id());
			BigDecimal amount = money(quantity.multiply(unitPrice));
			lines.add(new ValidatedMaterialSupplementLine(lineNo++, sourceLine.id(), sourceLine.materialId(),
					sourceLine.unitId(), sourceLine.issuedQuantity(), supplementedQuantity, availableStockQuantity,
					quantity, unitPrice, amount, reason));
		}
		return lines;
	}

	private Long resolveMaterialSupplementWorkOrderMaterialId(ProductionMaterialSupplementLineRequest request,
			Map<Long, MaterialSupplementLineRow> currentLineById) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		Long workOrderMaterialId = request.workOrderMaterialId();
		if (request.id() != null) {
			MaterialSupplementLineRow currentLine = currentLineById.get(request.id());
			if (currentLine == null) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			if (workOrderMaterialId != null && !workOrderMaterialId.equals(currentLine.workOrderMaterialId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
			}
			workOrderMaterialId = currentLine.workOrderMaterialId();
		}
		return workOrderMaterialId;
	}

	private void validateMaterialSupplementLineStillValid(ProductionWorkOrderMaterialRow sourceLine,
			MaterialSupplementLineRow line) {
		if (!sourceLine.materialId().equals(line.materialId()) || !sourceLine.unitId().equals(line.unitId())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
	}

	private CreatedDocument insertMaterialSupplementWithRetry(ProductionWorkOrderRow workOrder,
			ValidatedMaterialSupplement supplement, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String supplementNo = nextNo("MS", MATERIAL_SUPPLEMENT_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_material_supplement (
							supplement_no, work_order_id, warehouse_id, business_date, status, client_request_id,
							remark, created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, supplementNo, workOrder.id(), supplement.warehouseId(),
						supplement.businessDate(), ReversalDocumentStatus.DRAFT.name(),
						blankToNull(supplement.clientRequestId()), blankToNull(supplement.remark()), operator, now,
						operator, now);
				return new CreatedDocument(id, supplementNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_material_supplement_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertMaterialSupplementLines(Long supplementId, List<ValidatedMaterialSupplementLine> lines,
			OffsetDateTime now) {
		for (ValidatedMaterialSupplementLine line : lines) {
			this.jdbcTemplate.update("""
					insert into mfg_material_supplement_line (
						supplement_id, work_order_material_id, material_id, unit_id, line_no,
						issued_quantity_before, supplemented_quantity_before, available_stock_quantity_before,
						quantity, reason, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", supplementId, line.workOrderMaterialId(), line.materialId(), line.unitId(), line.lineNo(),
					line.issuedQuantityBefore(), line.supplementedQuantityBefore(),
					line.availableStockQuantityBefore(), line.quantity(), blankToNull(line.reason()), now, now);
		}
	}

	private Long insertProductionCostRecord(String sourceDocumentType, String sourceDocumentNo, Long sourceDocumentId,
			Long sourceLineId, Long workOrderId, Long productMaterialId, Long workOrderMaterialId, Long materialId,
			Long unitId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, LocalDate businessDate,
			String remark, String operator, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String recordNo = nextNo("COST", COST_RECORD_NO_SEQUENCE);
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into mfg_cost_record (
							record_no, work_order_id, product_material_id, cost_type, source_type,
							source_document_type, source_document_no, source_document_id, source_line_id,
							work_order_material_id, material_id, unit_id, quantity, unit_price, amount, basis_type,
							business_date, status, remark, recorded_by, recorded_at, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, 'MATERIAL', 'AUTO_PRODUCTION', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
							'MANUAL_UNIT_PRICE_QUANTITY', ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, recordNo, workOrderId, productMaterialId, sourceDocumentType,
						sourceDocumentNo, sourceDocumentId, sourceLineId, workOrderMaterialId, materialId, unitId,
						quantity, unitPrice, amount, businessDate, remark, operator, now, operator, now, operator, now);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_cost_record_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertMaterialReturnReversalLink(ProductionIssueRow issue, ProductionIssueLineRow sourceLine,
			MaterialReturnRow materialReturn, MaterialReturnLineRow returnLine, BigDecimal amount, String operator,
			OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into biz_reversal_link (
					source_type, source_id, source_line_id, reverse_type, reverse_id, reverse_line_id, business_date,
					quantity, amount, created_by, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", PRODUCTION_MATERIAL_ISSUE_SOURCE, issue.id(), sourceLine.id(),
				PRODUCTION_MATERIAL_RETURN_SOURCE, materialReturn.id(), returnLine.id(), materialReturn.businessDate(),
				returnLine.quantity(), positiveAmountOrNull(amount), operator, now);
	}

	private void insertMaterialSupplementReversalLink(ProductionWorkOrderRow workOrder,
			ProductionWorkOrderMaterialRow sourceLine, MaterialSupplementRow supplement,
			MaterialSupplementLineRow supplementLine, BigDecimal amount, String operator, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into biz_reversal_link (
					source_type, source_id, source_line_id, reverse_type, reverse_id, reverse_line_id, business_date,
					quantity, amount, created_by, created_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", PRODUCTION_WORK_ORDER_SOURCE, workOrder.id(), sourceLine.id(),
				PRODUCTION_MATERIAL_SUPPLEMENT_SOURCE, supplement.id(), supplementLine.id(),
				supplement.businessDate(), supplementLine.quantity(), positiveAmountOrNull(amount), operator, now);
	}

	private SalesReturnDetailResponse existingSalesReturnDetail(SalesReturnRow existing, ValidatedSalesReturn request,
			CurrentUser operator) {
		if (existing.status() == ReversalDocumentStatus.CANCELLED
				|| !sameSalesReturnCoreLines(existing.id(), request.lines())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		return salesReturnDetail(existing, operator);
	}

	private PurchaseReturnDetailResponse existingPurchaseReturnDetail(PurchaseReturnRow existing,
			ValidatedPurchaseReturn request, CurrentUser operator) {
		if (existing.status() == ReversalDocumentStatus.CANCELLED
				|| !samePurchaseReturnCoreLines(existing.id(), request.lines())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		return purchaseReturnDetail(existing, operator);
	}

	private ProductionMaterialReturnDetailResponse existingMaterialReturnDetail(MaterialReturnRow existing,
			ValidatedMaterialReturn request, CurrentUser operator) {
		if (existing.status() == ReversalDocumentStatus.CANCELLED
				|| !sameMaterialReturnCoreLines(existing.id(), request.lines())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		return materialReturnDetail(existing, operator);
	}

	private ProductionMaterialSupplementDetailResponse existingMaterialSupplementDetail(MaterialSupplementRow existing,
			ValidatedMaterialSupplement request, CurrentUser operator) {
		if (existing.status() == ReversalDocumentStatus.CANCELLED
				|| !sameMaterialSupplementCoreLines(existing.id(), request.lines())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		return materialSupplementDetail(existing, operator);
	}

	private boolean sameSalesReturnCoreLines(Long existingReturnId, List<SalesReturnLineRequest> requestLines) {
		Map<Long, BigDecimal> existingLines = new LinkedHashMap<>();
		for (SalesReturnLineRow line : lockSalesReturnLines(existingReturnId)) {
			existingLines.put(line.sourceShipmentLineId(), line.quantity());
		}
		Map<Long, BigDecimal> requestedLines = requestedCoreLineQuantities(requestLines);
		if (!existingLines.keySet().equals(requestedLines.keySet())) {
			return false;
		}
		for (Map.Entry<Long, BigDecimal> entry : existingLines.entrySet()) {
			if (entry.getValue().compareTo(requestedLines.get(entry.getKey())) != 0) {
				return false;
			}
		}
		return true;
	}

	private boolean samePurchaseReturnCoreLines(Long existingReturnId, List<PurchaseReturnLineRequest> requestLines) {
		Map<Long, BigDecimal> existingLines = new LinkedHashMap<>();
		for (PurchaseReturnLineRow line : lockPurchaseReturnLines(existingReturnId)) {
			existingLines.put(line.sourceReceiptLineId(), line.quantity());
		}
		Map<Long, BigDecimal> requestedLines = requestedPurchaseCoreLineQuantities(requestLines);
		if (!existingLines.keySet().equals(requestedLines.keySet())) {
			return false;
		}
		for (Map.Entry<Long, BigDecimal> entry : existingLines.entrySet()) {
			if (entry.getValue().compareTo(requestedLines.get(entry.getKey())) != 0) {
				return false;
			}
		}
		return true;
	}

	private boolean sameMaterialReturnCoreLines(Long existingReturnId,
			List<ProductionMaterialReturnLineRequest> requestLines) {
		Map<Long, BigDecimal> existingLines = new LinkedHashMap<>();
		for (MaterialReturnLineRow line : lockMaterialReturnLines(existingReturnId)) {
			existingLines.put(line.sourceIssueLineId(), line.quantity());
		}
		Map<Long, BigDecimal> requestedLines = requestedMaterialReturnCoreLineQuantities(requestLines);
		if (!existingLines.keySet().equals(requestedLines.keySet())) {
			return false;
		}
		for (Map.Entry<Long, BigDecimal> entry : existingLines.entrySet()) {
			if (entry.getValue().compareTo(requestedLines.get(entry.getKey())) != 0) {
				return false;
			}
		}
		return true;
	}

	private boolean sameMaterialSupplementCoreLines(Long existingSupplementId,
			List<ProductionMaterialSupplementLineRequest> requestLines) {
		Map<Long, BigDecimal> existingLines = new LinkedHashMap<>();
		for (MaterialSupplementLineRow line : lockMaterialSupplementLines(existingSupplementId)) {
			existingLines.put(line.workOrderMaterialId(), line.quantity());
		}
		Map<Long, BigDecimal> requestedLines = requestedMaterialSupplementCoreLineQuantities(requestLines);
		if (!existingLines.keySet().equals(requestedLines.keySet())) {
			return false;
		}
		for (Map.Entry<Long, BigDecimal> entry : existingLines.entrySet()) {
			if (entry.getValue().compareTo(requestedLines.get(entry.getKey())) != 0) {
				return false;
			}
		}
		return true;
	}

	private Map<Long, BigDecimal> requestedCoreLineQuantities(List<SalesReturnLineRequest> requestLines) {
		Map<Long, BigDecimal> lines = new LinkedHashMap<>();
		for (SalesReturnLineRequest line : requestLines) {
			if (line == null || line.sourceShipmentLineId() == null || lines.containsKey(line.sourceShipmentLineId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			lines.put(line.sourceShipmentLineId(), validateQuantity(line.quantity()));
		}
		return lines;
	}

	private Map<Long, BigDecimal> requestedPurchaseCoreLineQuantities(List<PurchaseReturnLineRequest> requestLines) {
		Map<Long, BigDecimal> lines = new LinkedHashMap<>();
		for (PurchaseReturnLineRequest line : requestLines) {
			if (line == null || line.sourceReceiptLineId() == null || lines.containsKey(line.sourceReceiptLineId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			lines.put(line.sourceReceiptLineId(), validateQuantity(line.quantity()));
		}
		return lines;
	}

	private Map<Long, BigDecimal> requestedMaterialReturnCoreLineQuantities(
			List<ProductionMaterialReturnLineRequest> requestLines) {
		Map<Long, BigDecimal> lines = new LinkedHashMap<>();
		for (ProductionMaterialReturnLineRequest line : requestLines) {
			if (line == null || line.sourceIssueLineId() == null || lines.containsKey(line.sourceIssueLineId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			lines.put(line.sourceIssueLineId(), validateQuantity(line.quantity()));
		}
		return lines;
	}

	private Map<Long, BigDecimal> requestedMaterialSupplementCoreLineQuantities(
			List<ProductionMaterialSupplementLineRequest> requestLines) {
		Map<Long, BigDecimal> lines = new LinkedHashMap<>();
		for (ProductionMaterialSupplementLineRequest line : requestLines) {
			if (line == null || line.workOrderMaterialId() == null
					|| lines.containsKey(line.workOrderMaterialId())) {
				throw new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
			}
			lines.put(line.workOrderMaterialId(), validateQuantity(line.quantity()));
		}
		return lines;
	}

	private Optional<SalesReturnRow> existingSalesReturn(Long sourceShipmentId, String clientRequestId) {
		if (!hasText(clientRequestId)) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no,
				       sh.business_date as source_business_date, sh.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				where r.source_shipment_id = ?
				and r.client_request_id = ?
				for update of r
				""", this::mapSalesReturnRow, sourceShipmentId, clientRequestId).stream().findFirst();
	}

	private Optional<PurchaseReturnRow> existingPurchaseReturn(Long sourceReceiptId, String clientRequestId) {
		if (!hasText(clientRequestId)) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.supplier_id, s.name as supplier_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_receipt_id, r.source_receipt_no,
				       pr.business_date as source_business_date, pr.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from proc_purchase_return r
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				join proc_purchase_receipt pr on pr.id = r.source_receipt_id
				where r.source_receipt_id = ?
				and r.client_request_id = ?
				for update of r
				""", this::mapPurchaseReturnRow, sourceReceiptId, clientRequestId).stream().findFirst();
	}

	private Optional<MaterialReturnRow> existingMaterialReturn(Long sourceIssueId, String clientRequestId) {
		if (!hasText(clientRequestId)) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.work_order_id, wo.work_order_no, r.source_issue_id, i.issue_no,
				       i.business_date as source_business_date, i.status as source_status, r.warehouse_id,
				       w.name as warehouse_name, r.business_date, r.status, r.client_request_id, r.remark,
				       r.created_at, r.updated_at
				from mfg_material_return r
				join mfg_work_order wo on wo.id = r.work_order_id
				join mfg_material_issue i on i.id = r.source_issue_id
				join mst_warehouse w on w.id = r.warehouse_id
				where r.source_issue_id = ?
				and r.client_request_id = ?
				for update of r
				""", this::mapMaterialReturnRow, sourceIssueId, clientRequestId).stream().findFirst();
	}

	private Optional<MaterialSupplementRow> existingMaterialSupplement(Long workOrderId, String clientRequestId) {
		if (!hasText(clientRequestId)) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select s.id, s.supplement_no, s.work_order_id, wo.work_order_no, wo.status as source_status,
				       s.warehouse_id, w.name as warehouse_name, s.business_date, s.status, s.client_request_id,
				       s.remark, s.created_at, s.updated_at
				from mfg_material_supplement s
				join mfg_work_order wo on wo.id = s.work_order_id
				join mst_warehouse w on w.id = s.warehouse_id
				where s.work_order_id = ?
				and s.client_request_id = ?
				for update of s
				""", this::mapMaterialSupplementRow, workOrderId, clientRequestId).stream().findFirst();
	}

	private Optional<SalesReturnRow> salesReturnRow(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no,
				       sh.business_date as source_business_date, sh.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				where r.id = ?
				""", this::mapSalesReturnRow, id).stream().findFirst();
	}

	private Optional<PurchaseReturnRow> purchaseReturnRow(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.supplier_id, s.name as supplier_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_receipt_id, r.source_receipt_no,
				       pr.business_date as source_business_date, pr.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from proc_purchase_return r
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				join proc_purchase_receipt pr on pr.id = r.source_receipt_id
				where r.id = ?
				""", this::mapPurchaseReturnRow, id).stream().findFirst();
	}

	private Optional<MaterialReturnRow> materialReturnRow(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.work_order_id, wo.work_order_no, r.source_issue_id, i.issue_no,
				       i.business_date as source_business_date, i.status as source_status, r.warehouse_id,
				       w.name as warehouse_name, r.business_date, r.status, r.client_request_id, r.remark,
				       r.created_at, r.updated_at
				from mfg_material_return r
				join mfg_work_order wo on wo.id = r.work_order_id
				join mfg_material_issue i on i.id = r.source_issue_id
				join mst_warehouse w on w.id = r.warehouse_id
				where r.id = ?
				""", this::mapMaterialReturnRow, id).stream().findFirst();
	}

	private Optional<MaterialSupplementRow> materialSupplementRow(Long id) {
		return this.jdbcTemplate.query("""
				select s.id, s.supplement_no, s.work_order_id, wo.work_order_no, wo.status as source_status,
				       s.warehouse_id, w.name as warehouse_name, s.business_date, s.status, s.client_request_id,
				       s.remark, s.created_at, s.updated_at
				from mfg_material_supplement s
				join mfg_work_order wo on wo.id = s.work_order_id
				join mst_warehouse w on w.id = s.warehouse_id
				where s.id = ?
				""", this::mapMaterialSupplementRow, id).stream().findFirst();
	}

	private Optional<SalesReturnRow> lockSalesReturn(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.customer_id, c.name as customer_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_shipment_id, r.source_shipment_no,
				       sh.business_date as source_business_date, sh.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from sal_sales_return r
				join mst_customer c on c.id = r.customer_id
				join mst_warehouse w on w.id = r.warehouse_id
				join sal_sales_shipment sh on sh.id = r.source_shipment_id
				where r.id = ?
				for update
				""", this::mapSalesReturnRow, id).stream().findFirst();
	}

	private Optional<PurchaseReturnRow> lockPurchaseReturn(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.supplier_id, s.name as supplier_name, r.warehouse_id,
				       w.name as warehouse_name, r.source_receipt_id, r.source_receipt_no,
				       pr.business_date as source_business_date, pr.status as source_status, r.business_date,
				       r.status, r.total_amount, r.client_request_id, r.remark, r.created_at, r.updated_at
				from proc_purchase_return r
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				join proc_purchase_receipt pr on pr.id = r.source_receipt_id
				where r.id = ?
				for update
				""", this::mapPurchaseReturnRow, id).stream().findFirst();
	}

	private Optional<MaterialReturnRow> lockMaterialReturn(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.return_no, r.work_order_id, wo.work_order_no, r.source_issue_id, i.issue_no,
				       i.business_date as source_business_date, i.status as source_status, r.warehouse_id,
				       w.name as warehouse_name, r.business_date, r.status, r.client_request_id, r.remark,
				       r.created_at, r.updated_at
				from mfg_material_return r
				join mfg_work_order wo on wo.id = r.work_order_id
				join mfg_material_issue i on i.id = r.source_issue_id
				join mst_warehouse w on w.id = r.warehouse_id
				where r.id = ?
				for update
				""", this::mapMaterialReturnRow, id).stream().findFirst();
	}

	private Optional<MaterialSupplementRow> lockMaterialSupplement(Long id) {
		return this.jdbcTemplate.query("""
				select s.id, s.supplement_no, s.work_order_id, wo.work_order_no, wo.status as source_status,
				       s.warehouse_id, w.name as warehouse_name, s.business_date, s.status, s.client_request_id,
				       s.remark, s.created_at, s.updated_at
				from mfg_material_supplement s
				join mfg_work_order wo on wo.id = s.work_order_id
				join mst_warehouse w on w.id = s.warehouse_id
				where s.id = ?
				for update
				""", this::mapMaterialSupplementRow, id).stream().findFirst();
	}

	private List<SalesReturnLineRow> lockSalesReturnLines(Long returnId) {
		return this.jdbcTemplate.query("""
				select id, return_id, source_shipment_line_id, sales_order_line_id, material_id, unit_id, line_no,
				       returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
				       stock_movement_id
				from sal_sales_return_line
				where return_id = ?
				order by line_no asc, id asc
				for update
				""", this::mapSalesReturnLineRow, returnId);
	}

	private List<PurchaseReturnLineRow> lockPurchaseReturnLines(Long returnId) {
		return this.jdbcTemplate.query("""
				select id, return_id, source_receipt_line_id, purchase_order_line_id, material_id, unit_id, line_no,
				       returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason,
				       stock_movement_id
				from proc_purchase_return_line
				where return_id = ?
				order by line_no asc, id asc
				for update
				""", this::mapPurchaseReturnLineRow, returnId);
	}

	private List<MaterialReturnLineRow> lockMaterialReturnLines(Long returnId) {
		return this.jdbcTemplate.query("""
				select l.id, l.return_id, l.source_issue_line_id, l.work_order_material_id, il.warehouse_id,
				       l.material_id, l.unit_id, l.line_no, l.returned_quantity_before,
				       l.returnable_quantity_before, l.quantity,
				       coalesce(cr.unit_price, source_cost.unit_price, 0) as unit_price,
				       coalesce(cr.amount, l.quantity * coalesce(source_cost.unit_price, 0)) as amount,
				       l.reason, l.stock_movement_id, l.cost_record_id
				from mfg_material_return_line l
				join mfg_material_issue_line il on il.id = l.source_issue_line_id
				left join mfg_cost_record cr on cr.id = l.cost_record_id
				left join lateral (
					select crs.unit_price
					from mfg_cost_record crs
					where crs.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
					and crs.source_line_id = l.source_issue_line_id
					and crs.cost_type = 'MATERIAL'
					and crs.status = 'ACTIVE'
					order by crs.id desc
					limit 1
				) source_cost on true
				where l.return_id = ?
				order by l.line_no asc, l.id asc
				for update of l
				""", this::mapMaterialReturnLineRow, returnId);
	}

	private List<MaterialSupplementLineRow> lockMaterialSupplementLines(Long supplementId) {
		return this.jdbcTemplate.query("""
				select l.id, l.supplement_id, l.work_order_material_id, l.material_id, l.unit_id, l.line_no,
				       l.issued_quantity_before, l.supplemented_quantity_before,
				       l.available_stock_quantity_before, l.quantity,
				       coalesce(cr.unit_price, source_cost.unit_price, 0) as unit_price,
				       coalesce(cr.amount, l.quantity * coalesce(source_cost.unit_price, 0)) as amount,
				       l.reason, l.stock_movement_id, l.cost_record_id
				from mfg_material_supplement_line l
				left join mfg_cost_record cr on cr.id = l.cost_record_id
				left join lateral (
					select crs.unit_price
					from mfg_cost_record crs
					where crs.work_order_material_id = l.work_order_material_id
					and crs.source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
					and crs.cost_type = 'MATERIAL'
					and crs.status = 'ACTIVE'
					and crs.unit_price is not null
					order by crs.id desc
					limit 1
				) source_cost on true
				where l.supplement_id = ?
				order by l.line_no asc, l.id asc
				for update of l
				""", this::mapMaterialSupplementLineRow, supplementId);
	}

	private Optional<ShipmentRow> lockShipment(Long id) {
		return this.jdbcTemplate.query("""
				select id, shipment_no, order_id, customer_id, warehouse_id, business_date, status
				from sal_sales_shipment
				where id = ?
				for update
				""", this::mapShipmentRow, id).stream().findFirst();
	}

	private Optional<ReceiptRow> lockReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select id, receipt_no, order_id, supplier_id, warehouse_id, business_date, status
				from proc_purchase_receipt
				where id = ?
				for update
				""", this::mapReceiptRow, id).stream().findFirst();
	}

	private Optional<ShipmentLineRow> shipmentLine(Long shipmentId, Long lineId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.shipment_id, sl.line_no, sl.order_line_id, sl.material_id, sl.unit_id,
				       sl.quantity, ol.unit_price
				from sal_sales_shipment_line sl
				join sal_sales_order_line ol on ol.id = sl.order_line_id
				where sl.shipment_id = ?
				and sl.id = ?
				""", this::mapShipmentLineRow, shipmentId, lineId).stream().findFirst();
	}

	private Optional<ReceiptLineRow> receiptLine(Long receiptId, Long lineId) {
		return this.jdbcTemplate.query("""
				select prl.id, prl.receipt_id, prl.line_no, prl.order_line_id, prl.material_id, prl.unit_id,
				       prl.quantity, pol.unit_price
				from proc_purchase_receipt_line prl
				join proc_purchase_order_line pol on pol.id = prl.order_line_id
				where prl.receipt_id = ?
				and prl.id = ?
				""", this::mapReceiptLineRow, receiptId, lineId).stream().findFirst();
	}

	private Optional<ShipmentLineRow> lockShipmentLine(Long shipmentId, Long lineId) {
		return this.jdbcTemplate.query("""
				select sl.id, sl.shipment_id, sl.line_no, sl.order_line_id, sl.material_id, sl.unit_id,
				       sl.quantity, ol.unit_price
				from sal_sales_shipment_line sl
				join sal_sales_order_line ol on ol.id = sl.order_line_id
				where sl.shipment_id = ?
				and sl.id = ?
				for update
				""", this::mapShipmentLineRow, shipmentId, lineId).stream().findFirst();
	}

	private Optional<ReceiptLineRow> lockReceiptLine(Long receiptId, Long lineId) {
		return this.jdbcTemplate.query("""
				select prl.id, prl.receipt_id, prl.line_no, prl.order_line_id, prl.material_id, prl.unit_id,
				       prl.quantity, pol.unit_price
				from proc_purchase_receipt_line prl
				join proc_purchase_order_line pol on pol.id = prl.order_line_id
				where prl.receipt_id = ?
				and prl.id = ?
				for update
				""", this::mapReceiptLineRow, receiptId, lineId).stream().findFirst();
	}

	private Optional<ProductionIssueRow> lockProductionIssue(Long id) {
		return this.jdbcTemplate.query("""
				select i.id, i.issue_no, i.work_order_id, wo.work_order_no, wo.product_material_id,
				       i.business_date, i.status
				from mfg_material_issue i
				join mfg_work_order wo on wo.id = i.work_order_id
				where i.id = ?
				for update
				""", this::mapProductionIssueRow, id).stream().findFirst();
	}

	private Optional<ProductionWorkOrderRow> lockProductionWorkOrder(Long id) {
		return this.jdbcTemplate.query("""
				select id, work_order_no, product_material_id, status
				from mfg_work_order
				where id = ?
				for update
				""", this::mapProductionWorkOrderRow, id).stream().findFirst();
	}

	private Optional<ProductionIssueLineRow> productionIssueLine(Long issueId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity
				from mfg_material_issue_line
				where issue_id = ?
				and id = ?
				""", this::mapProductionIssueLineRow, issueId, lineId).stream().findFirst();
	}

	private Optional<ProductionIssueLineRow> lockProductionIssueLine(Long issueId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity
				from mfg_material_issue_line
				where issue_id = ?
				and id = ?
				for update
				""", this::mapProductionIssueLineRow, issueId, lineId).stream().findFirst();
	}

	private Optional<ProductionWorkOrderMaterialRow> productionWorkOrderMaterial(Long workOrderId,
			Long workOrderMaterialId) {
		return this.jdbcTemplate.query("""
				select id, work_order_id, line_no, material_id, unit_id, required_quantity, issued_quantity
				from mfg_work_order_material
				where work_order_id = ?
				and id = ?
				""", this::mapProductionWorkOrderMaterialRow, workOrderId, workOrderMaterialId).stream().findFirst();
	}

	private Optional<ProductionWorkOrderMaterialRow> lockProductionWorkOrderMaterial(Long workOrderId,
			Long workOrderMaterialId) {
		return this.jdbcTemplate.query("""
				select id, work_order_id, line_no, material_id, unit_id, required_quantity, issued_quantity
				from mfg_work_order_material
				where work_order_id = ?
				and id = ?
				for update
				""", this::mapProductionWorkOrderMaterialRow, workOrderId, workOrderMaterialId).stream().findFirst();
	}

	private void validateWarehouse(Long warehouseId) {
		Integer count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mst_warehouse
				where id = ?
				and status = 'ENABLED'
				""", Integer.class, warehouseId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
		}
	}

	private Optional<ReceivableRow> lockReceivableForShipment(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select id, receivable_no, source_id, total_amount, received_amount, adjusted_amount,
				       unreceived_amount, status
				from fin_receivable
				where source_type = ?
				and source_id = ?
				for update
				""", this::mapReceivableRow, SALES_SHIPMENT_SOURCE, shipmentId).stream().findFirst();
	}

	private Optional<PayableRow> lockPayableForReceipt(Long receiptId) {
		return this.jdbcTemplate.query("""
				select id, payable_no, source_id, total_amount, paid_amount, adjusted_amount, unpaid_amount, status
				from fin_payable
				where source_type = ?
				and source_id = ?
				for update
				""", this::mapPayableRow, PURCHASE_RECEIPT_SOURCE, receiptId).stream().findFirst();
	}

	private BigDecimal postedReturnedQuantity(Long shipmentLineId) {
		BigDecimal returned = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(rl.quantity), 0)
				from sal_sales_return_line rl
				join sal_sales_return r on r.id = rl.return_id
				where rl.source_shipment_line_id = ?
				and r.status = ?
				""", BigDecimal.class, shipmentLineId, ReversalDocumentStatus.POSTED.name());
		return returned == null ? ZERO : returned;
	}

	private BigDecimal postedPurchaseReturnedQuantity(Long receiptLineId) {
		BigDecimal returned = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(rl.quantity), 0)
				from proc_purchase_return_line rl
				join proc_purchase_return r on r.id = rl.return_id
				where rl.source_receipt_line_id = ?
				and r.status = ?
				""", BigDecimal.class, receiptLineId, ReversalDocumentStatus.POSTED.name());
		return returned == null ? ZERO : returned;
	}

	private BigDecimal postedMaterialReturnedQuantity(Long issueLineId) {
		BigDecimal returned = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(rl.quantity), 0)
				from mfg_material_return_line rl
				join mfg_material_return r on r.id = rl.return_id
				where rl.source_issue_line_id = ?
				and r.status = ?
				""", BigDecimal.class, issueLineId, ReversalDocumentStatus.POSTED.name());
		return returned == null ? ZERO : returned;
	}

	private BigDecimal postedMaterialSupplementedQuantity(Long workOrderMaterialId) {
		BigDecimal supplemented = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(sl.quantity), 0)
				from mfg_material_supplement_line sl
				join mfg_material_supplement s on s.id = sl.supplement_id
				where sl.work_order_material_id = ?
				and s.status = ?
				""", BigDecimal.class, workOrderMaterialId, ReversalDocumentStatus.POSTED.name());
		return supplemented == null ? ZERO : supplemented;
	}

	private BigDecimal lockedStockQuantity(Long warehouseId, Long materialId) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity_on_hand"), warehouseId, materialId)
			.stream()
			.findFirst()
			.orElse(ZERO);
	}

	private BigDecimal stockQuantity(Long warehouseId, Long materialId) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				""", (rs, rowNum) -> rs.getBigDecimal("quantity_on_hand"), warehouseId, materialId)
			.stream()
			.findFirst()
			.orElse(ZERO);
	}

	private BigDecimal materialIssueUnitPrice(Long issueLineId, Long workOrderMaterialId) {
		List<BigDecimal> prices;
		if (issueLineId != null) {
			prices = this.jdbcTemplate.query("""
					select unit_price
					from mfg_cost_record
					where source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
					and source_line_id = ?
					and cost_type = 'MATERIAL'
					and status = 'ACTIVE'
					and unit_price is not null
					order by id desc
					limit 1
					""", (rs, rowNum) -> rs.getBigDecimal("unit_price"), issueLineId);
			if (!prices.isEmpty()) {
				return prices.get(0);
			}
		}
		prices = this.jdbcTemplate.query("""
				select unit_price
				from mfg_cost_record
				where source_document_type = 'PRODUCTION_MATERIAL_ISSUE'
				and work_order_material_id = ?
				and cost_type = 'MATERIAL'
				and status = 'ACTIVE'
				and unit_price is not null
				order by id desc
				limit 1
				""", (rs, rowNum) -> rs.getBigDecimal("unit_price"), workOrderMaterialId);
		return prices.isEmpty() ? ZERO : prices.get(0);
	}

	private Long movementId(String sourceType, Long sourceLineId) {
		return this.jdbcTemplate.queryForObject("""
				select id
				from inv_stock_movement
				where source_type = ?
				and source_line_id = ?
				""", Long.class, sourceType, sourceLineId);
	}

	private List<TraceLinkRow> reverseTraceLinks(String reverseType, Long reverseId, Long reverseLineId) {
		List<Object> args = new ArrayList<>();
		args.add(reverseType);
		args.add(reverseId);
		String condition = "";
		if (reverseLineId != null) {
			condition = "and bl.reverse_line_id = ?";
			args.add(reverseLineId);
		}
		return traceLinks("""
				where bl.reverse_type = ?
				and bl.reverse_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<TraceLinkRow> sourceTraceLinks(String sourceType, Long sourceId, Long sourceLineId) {
		List<Object> args = new ArrayList<>();
		String normalizedType = SALES_SHIPMENT_LINE_SOURCE.equals(sourceType) ? SALES_SHIPMENT_SOURCE : sourceType;
		args.add(normalizedType);
		args.add(sourceId);
		String condition = "";
		if (sourceLineId != null) {
			condition = "and bl.source_line_id = ?";
			args.add(sourceLineId);
		}
		return traceLinks("""
				where bl.source_type = ?
				and bl.source_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<TraceLinkRow> traceLinks(String where, List<Object> args) {
		return this.jdbcTemplate.query("""
				select bl.source_type, bl.source_id, bl.source_line_id, bl.reverse_type, bl.reverse_id,
				       bl.reverse_line_id, bl.business_date, bl.quantity, bl.amount,
				       sh.shipment_no, sh.business_date as shipment_date, sh.status as shipment_status,
				       ssl.line_no as shipment_line_no,
				       sr.return_no, sr.business_date as return_date, sr.status as return_status,
				       srl.line_no as return_line_no, srl.stock_movement_id,
				       fsa.id as settlement_adjustment_id
				from biz_reversal_link bl
				join sal_sales_shipment sh on sh.id = bl.source_id
				join sal_sales_shipment_line ssl on ssl.id = bl.source_line_id
				join sal_sales_return sr on sr.id = bl.reverse_id
				join sal_sales_return_line srl on srl.id = bl.reverse_line_id
				left join fin_settlement_adjustment fsa on fsa.source_type = 'SALES_RETURN'
					and fsa.source_id = sr.id
					and fsa.status = 'POSTED'
				%s
				order by bl.business_date asc, bl.id asc
				""".formatted(where), this::mapTraceLinkRow, args.toArray());
	}

	private List<ReversalTraceRecord> traceRecords(List<TraceLinkRow> links, CurrentUser currentUser,
			String direction) {
		boolean canViewShipment = canViewSalesShipment(currentUser);
		boolean canViewReturn = canViewSalesReturn(currentUser);
		return links.stream()
			.map((link) -> {
				Map<String, Object> source = sourceView(SALES_SHIPMENT_LINE_SOURCE, link.sourceId(),
						link.sourceLineId(), link.shipmentNo(), link.shipmentLineNo(), link.shipmentDate(),
						link.shipmentStatus(), quantity(link.quantity()), amount(link.amount()), canViewShipment,
						"sales-shipment-detail", Map.of("id", link.sourceId()), Map.of("lineId", link.sourceLineId()));
				Map<String, Object> reverse = sourceView(SALES_RETURN_SOURCE, link.reverseId(), link.reverseLineId(),
						link.returnNo(), link.returnLineNo(), link.returnDate(), link.returnStatus(),
						quantity(link.quantity()), amount(link.amount()), canViewReturn, "sales-return-detail",
						Map.of("id", link.reverseId()), Map.of("lineId", link.reverseLineId()));
				boolean restricted = !canViewShipment || !canViewReturn;
				return new ReversalTraceRecord(traceKey(link), direction, source, reverse,
						restricted ? null : link.stockMovementId(), restricted ? null : link.settlementAdjustmentId(),
						null, restricted ? null : link.businessDate(), restricted ? null : quantity(link.quantity()),
						restricted ? null : amount(link.amount()), restricted ? null : link.returnStatus(),
						!restricted, restricted, restricted ? RESTRICTED_MESSAGE : null,
						restricted ? null : "sales-return-detail",
						restricted ? null : Map.of("id", link.reverseId()),
						restricted ? null : Map.of("lineId", link.reverseLineId()));
			})
			.toList();
	}

	private List<PurchaseTraceLinkRow> purchaseReverseTraceLinks(String reverseType, Long reverseId,
			Long reverseLineId) {
		List<Object> args = new ArrayList<>();
		args.add(reverseType);
		args.add(reverseId);
		String condition = "";
		if (reverseLineId != null) {
			condition = "and bl.reverse_line_id = ?";
			args.add(reverseLineId);
		}
		return purchaseTraceLinks("""
				where bl.reverse_type = ?
				and bl.reverse_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<PurchaseTraceLinkRow> purchaseSourceTraceLinks(String sourceType, Long sourceId, Long sourceLineId) {
		List<Object> args = new ArrayList<>();
		String normalizedType = PURCHASE_RECEIPT_LINE_SOURCE.equals(sourceType) ? PURCHASE_RECEIPT_SOURCE : sourceType;
		args.add(normalizedType);
		args.add(sourceId);
		String condition = "";
		if (sourceLineId != null) {
			condition = "and bl.source_line_id = ?";
			args.add(sourceLineId);
		}
		return purchaseTraceLinks("""
				where bl.source_type = ?
				and bl.source_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<PurchaseTraceLinkRow> purchaseTraceLinks(String where, List<Object> args) {
		return this.jdbcTemplate.query("""
				select bl.source_type, bl.source_id, bl.source_line_id, bl.reverse_type, bl.reverse_id,
				       bl.reverse_line_id, bl.business_date, bl.quantity, bl.amount,
				       pr.receipt_no, pr.business_date as receipt_date, pr.status as receipt_status,
				       prl.line_no as receipt_line_no,
				       rr.return_no, rr.business_date as return_date, rr.status as return_status,
				       rrl.line_no as return_line_no, rrl.stock_movement_id,
				       fsa.id as settlement_adjustment_id
				from biz_reversal_link bl
				join proc_purchase_receipt pr on pr.id = bl.source_id
				join proc_purchase_receipt_line prl on prl.id = bl.source_line_id
				join proc_purchase_return rr on rr.id = bl.reverse_id
				join proc_purchase_return_line rrl on rrl.id = bl.reverse_line_id
				left join fin_settlement_adjustment fsa on fsa.source_type = 'PURCHASE_RETURN'
					and fsa.source_id = rr.id
					and fsa.status = 'POSTED'
				%s
				order by bl.business_date asc, bl.id asc
				""".formatted(where), this::mapPurchaseTraceLinkRow, args.toArray());
	}

	private List<ReversalTraceRecord> purchaseTraceRecords(List<PurchaseTraceLinkRow> links, CurrentUser currentUser,
			String direction) {
		boolean canViewReceipt = canViewPurchaseReceipt(currentUser);
		boolean canViewReturn = canViewPurchaseReturn(currentUser);
		return links.stream()
			.map((link) -> {
				Map<String, Object> source = sourceView(PURCHASE_RECEIPT_LINE_SOURCE, link.sourceId(),
						link.sourceLineId(), link.receiptNo(), link.receiptLineNo(), link.receiptDate(),
						link.receiptStatus(), quantity(link.quantity()), amount(link.amount()), canViewReceipt,
						"procurement-receipt-detail", Map.of("id", link.sourceId()),
						Map.of("lineId", link.sourceLineId()));
				Map<String, Object> reverse = sourceView(PURCHASE_RETURN_SOURCE, link.reverseId(),
						link.reverseLineId(), link.returnNo(), link.returnLineNo(), link.returnDate(),
						link.returnStatus(), quantity(link.quantity()), amount(link.amount()), canViewReturn,
						"procurement-return-detail", Map.of("id", link.reverseId()),
						Map.of("lineId", link.reverseLineId()));
				boolean restricted = !canViewReceipt || !canViewReturn;
				return new ReversalTraceRecord(traceKey(link), direction, source, reverse,
						restricted ? null : link.stockMovementId(), restricted ? null : link.settlementAdjustmentId(),
						null, restricted ? null : link.businessDate(), restricted ? null : quantity(link.quantity()),
						restricted ? null : amount(link.amount()), restricted ? null : link.returnStatus(),
						!restricted, restricted, restricted ? RESTRICTED_MESSAGE : null,
						restricted ? null : "procurement-return-detail",
						restricted ? null : Map.of("id", link.reverseId()),
						restricted ? null : Map.of("lineId", link.reverseLineId()));
			})
			.toList();
	}

	private List<ProductionTraceLinkRow> productionReverseTraceLinks(String reverseType, Long reverseId,
			Long reverseLineId) {
		List<Object> args = new ArrayList<>();
		args.add(reverseType);
		args.add(reverseId);
		String condition = "";
		if (reverseLineId != null) {
			condition = "and bl.reverse_line_id = ?";
			args.add(reverseLineId);
		}
		return productionTraceLinks("""
				where bl.reverse_type = ?
				and bl.reverse_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<ProductionTraceLinkRow> productionSourceTraceLinks(String sourceType, Long sourceId,
			Long sourceLineId) {
		List<Object> args = new ArrayList<>();
		String normalizedType = PRODUCTION_MATERIAL_ISSUE_LINE_SOURCE.equals(sourceType)
				? PRODUCTION_MATERIAL_ISSUE_SOURCE : sourceType;
		args.add(normalizedType);
		args.add(sourceId);
		String condition = "";
		if (sourceLineId != null) {
			condition = "and bl.source_line_id = ?";
			args.add(sourceLineId);
		}
		return productionTraceLinks("""
				where bl.source_type = ?
				and bl.source_id = ?
				%s
				""".formatted(condition), args);
	}

	private List<ProductionTraceLinkRow> productionTraceLinks(String where, List<Object> args) {
		List<ProductionTraceLinkRow> links = new ArrayList<>();
		links.addAll(this.jdbcTemplate.query("""
				select bl.source_type, bl.source_id, bl.source_line_id, bl.reverse_type, bl.reverse_id,
				       bl.reverse_line_id, bl.business_date, bl.quantity, bl.amount,
				       i.issue_no as source_no, i.business_date as source_date, i.status as source_status,
				       il.line_no as source_line_no, i.work_order_id,
				       r.return_no as reverse_no, r.business_date as reverse_date, r.status as reverse_status,
				       rl.line_no as reverse_line_no, rl.stock_movement_id, rl.cost_record_id
				from biz_reversal_link bl
				join mfg_material_issue i on i.id = bl.source_id
				join mfg_material_issue_line il on il.id = bl.source_line_id
				join mfg_material_return r on r.id = bl.reverse_id
				join mfg_material_return_line rl on rl.id = bl.reverse_line_id
				%s
				and bl.reverse_type = 'PRODUCTION_MATERIAL_RETURN'
				order by bl.business_date asc, bl.id asc
				""".formatted(where), this::mapProductionTraceLinkRow, args.toArray()));
		links.addAll(this.jdbcTemplate.query("""
				select bl.source_type, bl.source_id, bl.source_line_id, bl.reverse_type, bl.reverse_id,
				       bl.reverse_line_id, bl.business_date, bl.quantity, bl.amount,
				       wo.work_order_no as source_no, s.business_date as source_date, wo.status as source_status,
				       wom.line_no as source_line_no, wo.id as work_order_id,
				       s.supplement_no as reverse_no, s.business_date as reverse_date, s.status as reverse_status,
				       sl.line_no as reverse_line_no, sl.stock_movement_id, sl.cost_record_id
				from biz_reversal_link bl
				join mfg_work_order wo on wo.id = bl.source_id
				join mfg_work_order_material wom on wom.id = bl.source_line_id
				join mfg_material_supplement s on s.id = bl.reverse_id
				join mfg_material_supplement_line sl on sl.id = bl.reverse_line_id
				%s
				and bl.reverse_type = 'PRODUCTION_MATERIAL_SUPPLEMENT'
				order by bl.business_date asc, bl.id asc
				""".formatted(where), this::mapProductionTraceLinkRow, args.toArray()));
		return links;
	}

	private List<ReversalTraceRecord> productionTraceRecords(List<ProductionTraceLinkRow> links,
			CurrentUser currentUser, String direction) {
		return links.stream()
			.map((link) -> {
				boolean returnTrace = PRODUCTION_MATERIAL_RETURN_SOURCE.equals(link.reverseType());
				boolean canViewSource = returnTrace ? canViewProductionIssue(currentUser)
						: canViewProductionWorkOrder(currentUser);
				boolean canViewReverse = returnTrace ? canViewMaterialReturn(currentUser)
						: canViewMaterialSupplement(currentUser);
				String sourceType = returnTrace ? PRODUCTION_MATERIAL_ISSUE_LINE_SOURCE : PRODUCTION_WORK_ORDER_SOURCE;
				String sourceRoute = returnTrace ? "production-work-order-material-issues"
						: "production-work-order-detail";
				Map<String, Object> sourceParams = Map.of("id", link.workOrderId());
				Map<String, Object> sourceQuery = returnTrace
						? Map.of("issueId", link.sourceId(), "lineId", link.sourceLineId())
						: Map.of("lineId", link.sourceLineId());
				Map<String, Object> source = sourceView(sourceType, link.sourceId(), link.sourceLineId(),
						link.sourceNo(), link.sourceLineNo(), link.sourceDate(), link.sourceStatus(),
						quantity(link.quantity()), amount(link.amount()), canViewSource, sourceRoute, sourceParams,
						sourceQuery);
				String reverseRoute = returnTrace ? "production-material-return-detail"
						: "production-material-supplement-detail";
				Map<String, Object> reverse = sourceView(link.reverseType(), link.reverseId(), link.reverseLineId(),
						link.reverseNo(), link.reverseLineNo(), link.reverseDate(), link.reverseStatus(),
						quantity(link.quantity()), amount(link.amount()), canViewReverse, reverseRoute,
						Map.of("id", link.reverseId()), Map.of("lineId", link.reverseLineId()));
				boolean restricted = !canViewSource || !canViewReverse;
				return new ReversalTraceRecord(traceKey(link), direction, source, reverse,
						restricted ? null : link.stockMovementId(), null, restricted ? null : link.costRecordId(),
						restricted ? null : link.businessDate(), restricted ? null : quantity(link.quantity()),
						restricted ? null : amount(link.amount()), restricted ? null : link.reverseStatus(),
						!restricted, restricted, restricted ? RESTRICTED_MESSAGE : null,
						restricted ? null : reverseRoute, restricted ? null : Map.of("id", link.reverseId()),
						restricted ? null : Map.of("lineId", link.reverseLineId()));
			})
			.toList();
	}

	private String traceKey(TraceLinkRow link) {
		return link.sourceType() + ":" + link.sourceId() + ":" + link.sourceLineId() + ":" + link.reverseType() + ":"
				+ link.reverseId() + ":" + link.reverseLineId();
	}

	private String traceKey(PurchaseTraceLinkRow link) {
		return link.sourceType() + ":" + link.sourceId() + ":" + link.sourceLineId() + ":" + link.reverseType() + ":"
				+ link.reverseId() + ":" + link.reverseLineId();
	}

	private String traceKey(ProductionTraceLinkRow link) {
		return link.sourceType() + ":" + link.sourceId() + ":" + link.sourceLineId() + ":" + link.reverseType() + ":"
				+ link.reverseId() + ":" + link.reverseLineId();
	}

	private Map<String, Object> sourceView(String sourceType, Long sourceId, Long sourceLineId, String sourceNo,
			Integer lineNo, LocalDate businessDate, String status, String quantity, String amount, boolean canViewSource,
			String resourceRouteName, Map<String, Object> resourceRouteParams, Map<String, Object> resourceRouteQuery) {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("sourceType", sourceType);
		source.put("canViewSource", canViewSource);
		source.put("restricted", !canViewSource);
		if (!canViewSource) {
			source.put("restrictedMessage", RESTRICTED_MESSAGE);
			return source;
		}
		source.put("sourceId", sourceId);
		if (sourceLineId != null) {
			source.put("sourceLineId", sourceLineId);
		}
		source.put("sourceNo", sourceNo);
		if (lineNo != null) {
			source.put("lineNo", lineNo);
		}
		source.put("businessDate", businessDate);
		source.put("status", status);
		if (quantity != null) {
			source.put("quantity", quantity);
		}
		if (amount != null) {
			source.put("amount", amount);
		}
		source.put("resourceRouteName", resourceRouteName);
		if (resourceRouteParams != null) {
			source.put("resourceRouteParams", resourceRouteParams);
		}
		if (resourceRouteQuery != null) {
			source.put("resourceRouteQuery", resourceRouteQuery);
		}
		return source;
	}

	private SalesReturnRow mapSalesReturnRow(ResultSet rs, int rowNum) throws SQLException {
		return new SalesReturnRow(rs.getLong("id"), rs.getString("return_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getLong("source_shipment_id"), rs.getString("source_shipment_no"),
				rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
				rs.getObject("business_date", LocalDate.class), ReversalDocumentStatus.valueOf(rs.getString("status")),
				rs.getBigDecimal("total_amount"), rs.getString("client_request_id"), rs.getString("remark"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private SalesReturnLineRow mapSalesReturnLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new SalesReturnLineRow(rs.getLong("id"), rs.getLong("return_id"),
				rs.getLong("source_shipment_line_id"), nullableLong(rs, "sales_order_line_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getInt("line_no"),
				rs.getBigDecimal("returned_quantity_before"), rs.getBigDecimal("returnable_quantity_before"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"),
				rs.getString("reason"), nullableLong(rs, "stock_movement_id"));
	}

	private PurchaseReturnRow mapPurchaseReturnRow(ResultSet rs, int rowNum) throws SQLException {
		return new PurchaseReturnRow(rs.getLong("id"), rs.getString("return_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getLong("source_receipt_id"), rs.getString("source_receipt_no"),
				rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
				rs.getObject("business_date", LocalDate.class), ReversalDocumentStatus.valueOf(rs.getString("status")),
				rs.getBigDecimal("total_amount"), rs.getString("client_request_id"), rs.getString("remark"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private PurchaseReturnLineRow mapPurchaseReturnLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new PurchaseReturnLineRow(rs.getLong("id"), rs.getLong("return_id"),
				rs.getLong("source_receipt_line_id"), nullableLong(rs, "purchase_order_line_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getInt("line_no"),
				rs.getBigDecimal("returned_quantity_before"), rs.getBigDecimal("returnable_quantity_before"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"),
				rs.getString("reason"), nullableLong(rs, "stock_movement_id"));
	}

	private MaterialReturnRow mapMaterialReturnRow(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialReturnRow(rs.getLong("id"), rs.getString("return_no"), rs.getLong("work_order_id"),
				rs.getString("work_order_no"), rs.getLong("source_issue_id"), rs.getString("issue_no"),
				rs.getObject("source_business_date", LocalDate.class), rs.getString("source_status"),
				rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), ReversalDocumentStatus.valueOf(rs.getString("status")),
				rs.getString("client_request_id"), rs.getString("remark"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private MaterialSupplementRow mapMaterialSupplementRow(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialSupplementRow(rs.getLong("id"), rs.getString("supplement_no"),
				rs.getLong("work_order_id"), rs.getString("work_order_no"), rs.getString("source_status"),
				rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), ReversalDocumentStatus.valueOf(rs.getString("status")),
				rs.getString("client_request_id"), rs.getString("remark"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
	}

	private MaterialReturnLineRow mapMaterialReturnLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialReturnLineRow(rs.getLong("id"), rs.getLong("return_id"),
				rs.getLong("source_issue_line_id"), rs.getLong("work_order_material_id"), rs.getLong("warehouse_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getInt("line_no"),
				rs.getBigDecimal("returned_quantity_before"), rs.getBigDecimal("returnable_quantity_before"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), money(rs.getBigDecimal("amount")),
				rs.getString("reason"), nullableLong(rs, "stock_movement_id"), nullableLong(rs, "cost_record_id"));
	}

	private MaterialSupplementLineRow mapMaterialSupplementLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialSupplementLineRow(rs.getLong("id"), rs.getLong("supplement_id"),
				rs.getLong("work_order_material_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getInt("line_no"), rs.getBigDecimal("issued_quantity_before"),
				rs.getBigDecimal("supplemented_quantity_before"), rs.getBigDecimal("available_stock_quantity_before"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), money(rs.getBigDecimal("amount")),
				rs.getString("reason"), nullableLong(rs, "stock_movement_id"), nullableLong(rs, "cost_record_id"));
	}

	private ShipmentRow mapShipmentRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentRow(rs.getLong("id"), rs.getString("shipment_no"), rs.getLong("order_id"),
				rs.getLong("customer_id"), rs.getLong("warehouse_id"), rs.getObject("business_date", LocalDate.class),
				rs.getString("status"));
	}

	private ReceiptRow mapReceiptRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptRow(rs.getLong("id"), rs.getString("receipt_no"), rs.getLong("order_id"),
				rs.getLong("supplier_id"), rs.getLong("warehouse_id"), rs.getObject("business_date", LocalDate.class),
				rs.getString("status"));
	}

	private ShipmentLineRow mapShipmentLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentLineRow(rs.getLong("id"), rs.getLong("shipment_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"));
	}

	private ReceiptLineRow mapReceiptLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptLineRow(rs.getLong("id"), rs.getLong("receipt_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"));
	}

	private ReceivableRow mapReceivableRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceivableRow(rs.getLong("id"), rs.getString("receivable_no"), rs.getLong("source_id"),
				rs.getBigDecimal("total_amount"), rs.getBigDecimal("received_amount"),
				rs.getBigDecimal("adjusted_amount"), rs.getBigDecimal("unreceived_amount"),
				ReceivableStatus.valueOf(rs.getString("status")));
	}

	private PayableRow mapPayableRow(ResultSet rs, int rowNum) throws SQLException {
		return new PayableRow(rs.getLong("id"), rs.getString("payable_no"), rs.getLong("source_id"),
				rs.getBigDecimal("total_amount"), rs.getBigDecimal("paid_amount"), rs.getBigDecimal("adjusted_amount"),
				rs.getBigDecimal("unpaid_amount"), PayableStatus.valueOf(rs.getString("status")));
	}

	private ProductionIssueRow mapProductionIssueRow(ResultSet rs, int rowNum) throws SQLException {
		return new ProductionIssueRow(rs.getLong("id"), rs.getString("issue_no"), rs.getLong("work_order_id"),
				rs.getString("work_order_no"), rs.getLong("product_material_id"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"));
	}

	private ProductionWorkOrderRow mapProductionWorkOrderRow(ResultSet rs, int rowNum) throws SQLException {
		return new ProductionWorkOrderRow(rs.getLong("id"), rs.getString("work_order_no"),
				rs.getLong("product_material_id"), rs.getString("status"));
	}

	private ProductionIssueLineRow mapProductionIssueLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ProductionIssueLineRow(rs.getLong("id"), rs.getLong("issue_id"),
				rs.getLong("work_order_material_id"), rs.getInt("line_no"), rs.getLong("warehouse_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"));
	}

	private ProductionWorkOrderMaterialRow mapProductionWorkOrderMaterialRow(ResultSet rs, int rowNum)
			throws SQLException {
		return new ProductionWorkOrderMaterialRow(rs.getLong("id"), rs.getLong("work_order_id"),
				rs.getInt("line_no"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("required_quantity"), rs.getBigDecimal("issued_quantity"));
	}

	private TraceLinkRow mapTraceLinkRow(ResultSet rs, int rowNum) throws SQLException {
		return new TraceLinkRow(rs.getString("source_type"), rs.getLong("source_id"), rs.getLong("source_line_id"),
				rs.getString("reverse_type"), rs.getLong("reverse_id"), rs.getLong("reverse_line_id"),
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("amount"), rs.getString("shipment_no"),
				rs.getObject("shipment_date", LocalDate.class), rs.getString("shipment_status"),
				rs.getInt("shipment_line_no"), rs.getString("return_no"), rs.getObject("return_date", LocalDate.class),
				rs.getString("return_status"), rs.getInt("return_line_no"), nullableLong(rs, "stock_movement_id"),
				nullableLong(rs, "settlement_adjustment_id"));
	}

	private PurchaseTraceLinkRow mapPurchaseTraceLinkRow(ResultSet rs, int rowNum) throws SQLException {
		return new PurchaseTraceLinkRow(rs.getString("source_type"), rs.getLong("source_id"),
				rs.getLong("source_line_id"), rs.getString("reverse_type"), rs.getLong("reverse_id"),
				rs.getLong("reverse_line_id"), rs.getObject("business_date", LocalDate.class),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("amount"), rs.getString("receipt_no"),
				rs.getObject("receipt_date", LocalDate.class), rs.getString("receipt_status"),
				rs.getInt("receipt_line_no"), rs.getString("return_no"), rs.getObject("return_date", LocalDate.class),
				rs.getString("return_status"), rs.getInt("return_line_no"), nullableLong(rs, "stock_movement_id"),
				nullableLong(rs, "settlement_adjustment_id"));
	}

	private ProductionTraceLinkRow mapProductionTraceLinkRow(ResultSet rs, int rowNum) throws SQLException {
		return new ProductionTraceLinkRow(rs.getString("source_type"), rs.getLong("source_id"),
				rs.getLong("source_line_id"), rs.getString("reverse_type"), rs.getLong("reverse_id"),
				rs.getLong("reverse_line_id"), rs.getObject("business_date", LocalDate.class),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("amount"), rs.getString("source_no"),
				rs.getObject("source_date", LocalDate.class), rs.getString("source_status"),
				rs.getInt("source_line_no"), rs.getLong("work_order_id"), rs.getString("reverse_no"),
				rs.getObject("reverse_date", LocalDate.class), rs.getString("reverse_status"),
				rs.getInt("reverse_line_no"), nullableLong(rs, "stock_movement_id"),
				nullableLong(rs, "cost_record_id"));
	}

	private void requireDraft(SalesReturnRow salesReturn) {
		if (salesReturn.status() != ReversalDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.REVERSAL_STATUS_NOT_ALLOWED);
		}
	}

	private void requireDraft(PurchaseReturnRow purchaseReturn) {
		if (purchaseReturn.status() != ReversalDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.REVERSAL_STATUS_NOT_ALLOWED);
		}
	}

	private void requireDraft(MaterialReturnRow materialReturn) {
		if (materialReturn.status() != ReversalDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.REVERSAL_STATUS_NOT_ALLOWED);
		}
	}

	private void requireDraft(MaterialSupplementRow supplement) {
		if (supplement.status() != ReversalDocumentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.REVERSAL_STATUS_NOT_ALLOWED);
		}
	}

	private void requireActiveProductionWorkOrder(ProductionWorkOrderRow workOrder) {
		if (!"RELEASED".equals(workOrder.status()) && !"IN_PROGRESS".equals(workOrder.status())) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
	}

	private void requireAdjustableReceivable(ReceivableRow receivable) {
		if (receivable.status() != ReceivableStatus.CONFIRMED
				&& receivable.status() != ReceivableStatus.PARTIALLY_RECEIVED) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
	}

	private void requireAdjustablePayable(PayableRow payable) {
		if (payable.status() != PayableStatus.CONFIRMED && payable.status() != PayableStatus.PARTIALLY_PAID) {
			throw new BusinessException(ApiErrorCode.REVERSAL_SOURCE_STATUS_INVALID);
		}
	}

	private ReversalDocumentStatus parseReversalStatus(String value) {
		try {
			return ReversalDocumentStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.REVERSAL_STATUS_NOT_ALLOWED);
		}
	}

	private BusinessException duplicateReversalException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_sal_sales_return_client_request")
				|| containsConstraint(exception, "uk_sal_sales_return_line_source")
				|| containsConstraint(exception, "uk_proc_purchase_return_client_request")
				|| containsConstraint(exception, "uk_proc_purchase_return_line_source")
				|| containsConstraint(exception, "uk_mfg_material_return_client_request")
				|| containsConstraint(exception, "uk_mfg_material_return_line_source")
				|| containsConstraint(exception, "uk_mfg_material_supplement_client_request")
				|| containsConstraint(exception, "uk_mfg_material_supplement_line_material")
				|| containsConstraint(exception, "uk_mfg_cost_record_source_line")
				|| containsConstraint(exception, "uk_biz_reversal_link_reverse_line")
				|| containsConstraint(exception, "uk_biz_reversal_link_source_reverse")) {
			return new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		if (containsConstraint(exception, "uk_inv_stock_movement_source")) {
			return new BusinessException(ApiErrorCode.REVERSAL_DUPLICATED);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private BusinessException sourceNotFoundException() {
		return new BusinessException(ApiErrorCode.REVERSAL_SOURCE_NOT_FOUND);
	}

	private QueryParts where(List<String> conditions, List<Object> args) {
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		return new QueryParts(where, args);
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

	private BigDecimal totalAmount(List<ValidatedSalesReturnLine> lines) {
		return money(lines.stream().map(ValidatedSalesReturnLine::amount).reduce(ZERO, BigDecimal::add));
	}

	private BigDecimal totalPurchaseAmount(List<ValidatedPurchaseReturnLine> lines) {
		return money(lines.stream().map(ValidatedPurchaseReturnLine::amount).reduce(ZERO, BigDecimal::add));
	}

	private BigDecimal totalAmountFromRows(List<SalesReturnLineRow> lines) {
		return money(lines.stream().map(SalesReturnLineRow::amount).reduce(ZERO, BigDecimal::add));
	}

	private BigDecimal totalPurchaseAmountFromRows(List<PurchaseReturnLineRow> lines) {
		return money(lines.stream().map(PurchaseReturnLineRow::amount).reduce(ZERO, BigDecimal::add));
	}

	private BigDecimal totalQuantity(List<ReversalDocumentLine> lines) {
		return lines.stream().map((line) -> new BigDecimal(line.quantity())).reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal totalLineAmount(List<ReversalDocumentLine> lines) {
		return money(lines.stream().map((line) -> new BigDecimal(line.amount())).reduce(ZERO, BigDecimal::add));
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal positiveAmountOrNull(BigDecimal value) {
		return value == null || value.compareTo(ZERO) <= 0 ? null : money(value);
	}

	private String amount(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String quantity(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

	private long integerDigits(BigDecimal value) {
		return Math.max(0L, (long) value.precision() - value.scale());
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private boolean canViewSalesShipment(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("sales:shipment:view");
	}

	private boolean canViewSalesReturn(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("sales:return:view");
	}

	private boolean canViewPurchaseReceipt(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("procurement:receipt:view");
	}

	private boolean canViewPurchaseReturn(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("procurement:return:view");
	}

	private boolean canViewProductionIssue(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("production:issue:view");
	}

	private boolean canViewProductionWorkOrder(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("production:work-order:view");
	}

	private boolean canViewMaterialReturn(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("production:material-return:view");
	}

	private boolean canViewMaterialSupplement(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("production:material-supplement:view");
	}

	private boolean isPurchaseTraceType(String sourceType) {
		return PURCHASE_RECEIPT_SOURCE.equals(sourceType) || PURCHASE_RECEIPT_LINE_SOURCE.equals(sourceType)
				|| PURCHASE_RETURN_SOURCE.equals(sourceType);
	}

	private boolean isProductionTraceType(String sourceType) {
		return PRODUCTION_MATERIAL_ISSUE_SOURCE.equals(sourceType)
				|| PRODUCTION_MATERIAL_ISSUE_LINE_SOURCE.equals(sourceType)
				|| PRODUCTION_WORK_ORDER_SOURCE.equals(sourceType)
				|| PRODUCTION_MATERIAL_RETURN_SOURCE.equals(sourceType)
				|| PRODUCTION_MATERIAL_SUPPLEMENT_SOURCE.equals(sourceType);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static long offset(int page, int pageSize) {
		long normalizedPage = Math.max((long) page, 1L);
		return (normalizedPage - 1L) * limit(pageSize);
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record SalesReturnRequest(Long sourceShipmentId, @NotNull LocalDate businessDate,
			String clientRequestId, String remark, @Valid List<SalesReturnLineRequest> lines) {
	}

	public record SalesReturnLineRequest(Long id, Long sourceShipmentLineId, @NotNull BigDecimal quantity,
			String reason) {
	}

	public record PurchaseReturnRequest(Long sourceReceiptId, @NotNull LocalDate businessDate,
			String clientRequestId, String remark, @Valid List<PurchaseReturnLineRequest> lines) {
	}

	public record PurchaseReturnLineRequest(Long id, Long sourceReceiptLineId, @NotNull BigDecimal quantity,
			String reason) {
	}

	public record ProductionMaterialReturnRequest(Long sourceIssueId, @NotNull LocalDate businessDate,
			String clientRequestId, String remark, @Valid List<ProductionMaterialReturnLineRequest> lines) {
	}

	public record ProductionMaterialReturnLineRequest(Long id, Long sourceIssueLineId,
			@NotNull BigDecimal quantity, String reason) {
	}

	public record ProductionMaterialSupplementRequest(Long workOrderId, Long warehouseId,
			@NotNull LocalDate businessDate, String clientRequestId, String remark,
			@Valid List<ProductionMaterialSupplementLineRequest> lines) {
	}

	public record ProductionMaterialSupplementLineRequest(Long id, Long workOrderMaterialId,
			@NotNull BigDecimal quantity, String reason) {
	}

	public record SalesReturnSourceResponse(Long shipmentId, String shipmentNo, Long customerId, String customerName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			List<SalesReturnSourceLineResponse> lines) {
	}

	public record PurchaseReturnSourceResponse(Long receiptId, String receiptNo, Long supplierId, String supplierName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			List<PurchaseReturnSourceLineResponse> lines) {
	}

	public record ProductionMaterialReturnSourceResponse(Long issueId, String issueNo, Long workOrderId,
			String workOrderNo, Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			List<ProductionMaterialReturnSourceLineResponse> lines) {
	}

	public record ProductionMaterialSupplementSourceResponse(Long workOrderId, String workOrderNo,
			String workOrderStatus, Long warehouseId, String warehouseName,
			List<ProductionMaterialSupplementSourceLineResponse> materials) {
	}

	public record SalesReturnSourceLineResponse(Long shipmentLineId, Long salesOrderLineId, Integer lineNo,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			String shippedQuantity, String returnedQuantity, String returnableQuantity, String unitPrice,
			String returnableAmount) {
	}

	public record PurchaseReturnSourceLineResponse(Long receiptLineId, Long purchaseOrderLineId, Integer lineNo,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			String receivedQuantity, String returnedQuantity, String returnableQuantity, String availableStockQuantity,
			String unitPrice, String returnableAmount) {
	}

	public record ProductionMaterialReturnSourceLineResponse(Long issueLineId, Long workOrderMaterialId,
			Integer lineNo, Long warehouseId, Long materialId, String materialCode, String materialName, Long unitId,
			String unitName, String issuedQuantity, String returnedQuantity, String returnableQuantity,
			String unitPrice, String returnableAmount) {
	}

	public record ProductionMaterialSupplementSourceLineResponse(Long workOrderMaterialId, Integer lineNo,
			Long materialId, String materialCode, String materialName, Long unitId, String unitName,
			String plannedQuantity, String issuedQuantity, String supplementedQuantity, String availableStockQuantity,
			String unitPrice) {
	}

	public record SalesReturnSummaryResponse(Long id, String returnNo, Long customerId, String customerName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status, String totalQuantity,
			String totalAmount, Map<String, Object> source, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record PurchaseReturnSummaryResponse(Long id, String returnNo, Long supplierId, String supplierName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status, String totalQuantity,
			String totalAmount, Map<String, Object> source, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record ProductionMaterialReturnSummaryResponse(Long id, String returnNo, Long workOrderId,
			String workOrderNo, Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			String totalQuantity, String totalAmount, Map<String, Object> source, OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	public record ProductionMaterialSupplementSummaryResponse(Long id, String supplementNo, Long workOrderId,
			String workOrderNo, Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			String totalQuantity, String totalAmount, Map<String, Object> source, OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	public record SalesReturnDetailResponse(Long id, String returnNo, Long customerId, String customerName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status, String totalQuantity,
			String totalAmount, Map<String, Object> source, OffsetDateTime createdAt, OffsetDateTime updatedAt,
			String clientRequestId, String remark, List<ReversalDocumentLine> lines,
			List<ReversalTraceRecord> traces) {

		private SalesReturnDetailResponse withSettlementAdjustmentId(Long settlementAdjustmentId) {
			if (settlementAdjustmentId == null || this.traces.isEmpty()) {
				return this;
			}
			List<ReversalTraceRecord> updatedTraces = this.traces.stream()
				.map((trace) -> new ReversalTraceRecord(trace.traceKey(), trace.direction(), trace.source(),
						trace.reverse(), trace.inventoryMovementId(),
						trace.restricted() ? trace.settlementAdjustmentId() : settlementAdjustmentId,
						trace.costRecordId(), trace.businessDate(), trace.quantity(), trace.amount(), trace.status(),
						trace.canViewResource(), trace.restricted(), trace.restrictedMessage(),
						trace.resourceRouteName(), trace.resourceRouteParams(), trace.resourceRouteQuery()))
				.toList();
			return new SalesReturnDetailResponse(this.id, this.returnNo, this.customerId, this.customerName,
					this.warehouseId, this.warehouseName, this.businessDate, this.status, this.totalQuantity,
					this.totalAmount, this.source, this.createdAt, this.updatedAt, this.clientRequestId, this.remark,
					this.lines, updatedTraces);
		}
	}

	public record PurchaseReturnDetailResponse(Long id, String returnNo, Long supplierId, String supplierName,
			Long warehouseId, String warehouseName, LocalDate businessDate, String status, String totalQuantity,
			String totalAmount, Map<String, Object> source, OffsetDateTime createdAt, OffsetDateTime updatedAt,
			String clientRequestId, String remark, List<ReversalDocumentLine> lines,
			List<ReversalTraceRecord> traces) {

		private PurchaseReturnDetailResponse withSettlementAdjustmentId(Long settlementAdjustmentId) {
			if (settlementAdjustmentId == null || this.traces.isEmpty()) {
				return this;
			}
			List<ReversalTraceRecord> updatedTraces = this.traces.stream()
				.map((trace) -> new ReversalTraceRecord(trace.traceKey(), trace.direction(), trace.source(),
						trace.reverse(), trace.inventoryMovementId(),
						trace.restricted() ? trace.settlementAdjustmentId() : settlementAdjustmentId,
						trace.costRecordId(), trace.businessDate(), trace.quantity(), trace.amount(), trace.status(),
						trace.canViewResource(), trace.restricted(), trace.restrictedMessage(),
						trace.resourceRouteName(), trace.resourceRouteParams(), trace.resourceRouteQuery()))
				.toList();
			return new PurchaseReturnDetailResponse(this.id, this.returnNo, this.supplierId, this.supplierName,
					this.warehouseId, this.warehouseName, this.businessDate, this.status, this.totalQuantity,
					this.totalAmount, this.source, this.createdAt, this.updatedAt, this.clientRequestId, this.remark,
					this.lines, updatedTraces);
		}
	}

	public record ProductionMaterialReturnDetailResponse(Long id, String returnNo, Long workOrderId,
			String workOrderNo, Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			String totalQuantity, String totalAmount, Map<String, Object> source, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, String clientRequestId, String remark, List<ReversalDocumentLine> lines,
			List<ReversalTraceRecord> traces) {
	}

	public record ProductionMaterialSupplementDetailResponse(Long id, String supplementNo, Long workOrderId,
			String workOrderNo, Long warehouseId, String warehouseName, LocalDate businessDate, String status,
			String totalQuantity, String totalAmount, Map<String, Object> source, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, String clientRequestId, String remark, List<ReversalDocumentLine> lines,
			List<ReversalTraceRecord> traces) {
	}

	public record ReversalDocumentLine(Long id, Integer lineNo, Long sourceLineId, Long materialId,
			String materialCode, String materialName, Long unitId, String unitName, String returnedQuantityBefore,
			String returnableQuantityBefore, String quantity, String unitPrice, String amount, String reason,
			Long stockMovementId, Long costRecordId, Map<String, Object> source) {
	}

	public static final class ReversalTraceRecord extends LinkedHashMap<String, Object> {

		private final String traceKey;

		private final String direction;

		private final Map<String, Object> source;

		private final Map<String, Object> reverse;

		private final Long inventoryMovementId;

		private final Long settlementAdjustmentId;

		private final Long costRecordId;

		private final LocalDate businessDate;

		private final String quantity;

		private final String amount;

		private final String status;

		private final boolean canViewResource;

		private final boolean restricted;

		private final String restrictedMessage;

		private final String resourceRouteName;

		private final Object resourceRouteParams;

		private final Object resourceRouteQuery;

		public ReversalTraceRecord(String traceKey, String direction, Map<String, Object> source,
				Map<String, Object> reverse, Long inventoryMovementId, Long settlementAdjustmentId, Long costRecordId,
				LocalDate businessDate, String quantity, String amount, String status, boolean canViewResource,
				boolean restricted, String restrictedMessage, String resourceRouteName, Object resourceRouteParams,
				Object resourceRouteQuery) {
			this.traceKey = traceKey;
			this.direction = direction;
			this.source = source;
			this.reverse = reverse;
			this.inventoryMovementId = inventoryMovementId;
			this.settlementAdjustmentId = settlementAdjustmentId;
			this.costRecordId = costRecordId;
			this.businessDate = businessDate;
			this.quantity = quantity;
			this.amount = amount;
			this.status = status;
			this.canViewResource = canViewResource;
			this.restricted = restricted;
			this.restrictedMessage = restrictedMessage;
			this.resourceRouteName = resourceRouteName;
			this.resourceRouteParams = resourceRouteParams;
			this.resourceRouteQuery = resourceRouteQuery;
			put("traceKey", traceKey);
			put("direction", direction);
			put("source", source);
			put("reverse", reverse);
			putIfPresent("inventoryMovementId", inventoryMovementId);
			putIfPresent("settlementAdjustmentId", settlementAdjustmentId);
			putIfPresent("costRecordId", costRecordId);
			putIfPresent("businessDate", businessDate);
			putIfPresent("quantity", quantity);
			putIfPresent("amount", amount);
			putIfPresent("status", status);
			put("canViewResource", canViewResource);
			put("restricted", restricted);
			putIfPresent("restrictedMessage", restrictedMessage);
			putIfPresent("resourceRouteName", resourceRouteName);
			putIfPresent("resourceRouteParams", resourceRouteParams);
			putIfPresent("resourceRouteQuery", resourceRouteQuery);
		}

		private void putIfPresent(String key, Object value) {
			if (value != null) {
				put(key, value);
			}
		}

		public String traceKey() {
			return this.traceKey;
		}

		public String direction() {
			return this.direction;
		}

		public Map<String, Object> source() {
			return this.source;
		}

		public Map<String, Object> reverse() {
			return this.reverse;
		}

		public Long inventoryMovementId() {
			return this.inventoryMovementId;
		}

		public Long settlementAdjustmentId() {
			return this.settlementAdjustmentId;
		}

		public Long costRecordId() {
			return this.costRecordId;
		}

		public LocalDate businessDate() {
			return this.businessDate;
		}

		public String quantity() {
			return this.quantity;
		}

		public String amount() {
			return this.amount;
		}

		public String status() {
			return this.status;
		}

		public boolean canViewResource() {
			return this.canViewResource;
		}

		public boolean restricted() {
			return this.restricted;
		}

		public String restrictedMessage() {
			return this.restrictedMessage;
		}

		public String resourceRouteName() {
			return this.resourceRouteName;
		}

		public Object resourceRouteParams() {
			return this.resourceRouteParams;
		}

		public Object resourceRouteQuery() {
			return this.resourceRouteQuery;
		}
	}

	private record ValidatedSalesReturn(Long sourceShipmentId, LocalDate businessDate, String clientRequestId,
			String remark, List<SalesReturnLineRequest> lines) {
	}

	private record ValidatedPurchaseReturn(Long sourceReceiptId, LocalDate businessDate, String clientRequestId,
			String remark, List<PurchaseReturnLineRequest> lines) {
	}

	private record ValidatedMaterialReturn(Long sourceIssueId, LocalDate businessDate, String clientRequestId,
			String remark, List<ProductionMaterialReturnLineRequest> lines) {
	}

	private record ValidatedMaterialSupplement(Long workOrderId, Long warehouseId, LocalDate businessDate,
			String clientRequestId, String remark, List<ProductionMaterialSupplementLineRequest> lines) {
	}

	private record ValidatedSalesReturnLine(Integer lineNo, Long sourceShipmentLineId, Long salesOrderLineId,
			Long materialId, Long unitId, BigDecimal returnedQuantityBefore, BigDecimal returnableQuantityBefore,
			BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, String reason) {
	}

	private record ValidatedPurchaseReturnLine(Integer lineNo, Long sourceReceiptLineId, Long purchaseOrderLineId,
			Long materialId, Long unitId, BigDecimal returnedQuantityBefore, BigDecimal returnableQuantityBefore,
			BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, String reason) {
	}

	private record ValidatedMaterialReturnLine(Integer lineNo, Long sourceIssueLineId, Long workOrderMaterialId,
			Long warehouseId, Long materialId, Long unitId, BigDecimal returnedQuantityBefore,
			BigDecimal returnableQuantityBefore, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
			String reason) {
	}

	private record ValidatedMaterialSupplementLine(Integer lineNo, Long workOrderMaterialId, Long materialId,
			Long unitId, BigDecimal issuedQuantityBefore, BigDecimal supplementedQuantityBefore,
			BigDecimal availableStockQuantityBefore, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
			String reason) {
	}

	private record SalesReturnRow(Long id, String returnNo, Long customerId, String customerName, Long warehouseId,
			String warehouseName, Long sourceShipmentId, String sourceShipmentNo, LocalDate sourceBusinessDate,
			String sourceStatus, LocalDate businessDate, ReversalDocumentStatus status, BigDecimal totalAmount,
			String clientRequestId, String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record PurchaseReturnRow(Long id, String returnNo, Long supplierId, String supplierName, Long warehouseId,
			String warehouseName, Long sourceReceiptId, String sourceReceiptNo, LocalDate sourceBusinessDate,
			String sourceStatus, LocalDate businessDate, ReversalDocumentStatus status, BigDecimal totalAmount,
			String clientRequestId, String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record MaterialReturnRow(Long id, String returnNo, Long workOrderId, String workOrderNo,
			Long sourceIssueId, String sourceIssueNo, LocalDate sourceBusinessDate, String sourceStatus,
			Long warehouseId, String warehouseName, LocalDate businessDate, ReversalDocumentStatus status,
			String clientRequestId, String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	private record MaterialSupplementRow(Long id, String supplementNo, Long workOrderId, String workOrderNo,
			String sourceStatus, Long warehouseId, String warehouseName, LocalDate businessDate,
			ReversalDocumentStatus status, String clientRequestId, String remark, OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	private record SalesReturnLineRow(Long id, Long returnId, Long sourceShipmentLineId, Long salesOrderLineId,
			Long materialId, Long unitId, Integer lineNo, BigDecimal returnedQuantityBefore,
			BigDecimal returnableQuantityBefore, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
			String reason, Long stockMovementId) {
	}

	private record PurchaseReturnLineRow(Long id, Long returnId, Long sourceReceiptLineId, Long purchaseOrderLineId,
			Long materialId, Long unitId, Integer lineNo, BigDecimal returnedQuantityBefore,
			BigDecimal returnableQuantityBefore, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
			String reason, Long stockMovementId) {
	}

	private record MaterialReturnLineRow(Long id, Long returnId, Long sourceIssueLineId, Long workOrderMaterialId,
			Long warehouseId, Long materialId, Long unitId, Integer lineNo, BigDecimal returnedQuantityBefore,
			BigDecimal returnableQuantityBefore, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
			String reason, Long stockMovementId, Long costRecordId) {
	}

	private record MaterialSupplementLineRow(Long id, Long supplementId, Long workOrderMaterialId, Long materialId,
			Long unitId, Integer lineNo, BigDecimal issuedQuantityBefore, BigDecimal supplementedQuantityBefore,
			BigDecimal availableStockQuantityBefore, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
			String reason, Long stockMovementId, Long costRecordId) {
	}

	private record ShipmentRow(Long id, String shipmentNo, Long orderId, Long customerId, Long warehouseId,
			LocalDate businessDate, String status) {
	}

	private record ReceiptRow(Long id, String receiptNo, Long orderId, Long supplierId, Long warehouseId,
			LocalDate businessDate, String status) {
	}

	private record ShipmentLineRow(Long id, Long shipmentId, Integer lineNo, Long orderLineId, Long materialId,
			Long unitId, BigDecimal quantity, BigDecimal unitPrice) {
	}

	private record ReceiptLineRow(Long id, Long receiptId, Integer lineNo, Long orderLineId, Long materialId,
			Long unitId, BigDecimal quantity, BigDecimal unitPrice) {
	}

	private record ProductionIssueRow(Long id, String issueNo, Long workOrderId, String workOrderNo,
			Long productMaterialId, LocalDate businessDate, String status) {
	}

	private record ProductionWorkOrderRow(Long id, String workOrderNo, Long productMaterialId, String status) {
	}

	private record ProductionIssueLineRow(Long id, Long issueId, Long workOrderMaterialId, Integer lineNo,
			Long warehouseId, Long materialId, Long unitId, BigDecimal quantity) {
	}

	private record ProductionWorkOrderMaterialRow(Long id, Long workOrderId, Integer lineNo, Long materialId,
			Long unitId, BigDecimal requiredQuantity, BigDecimal issuedQuantity) {
	}

	private record ReceivableRow(Long id, String receivableNo, Long sourceId, BigDecimal totalAmount,
			BigDecimal receivedAmount, BigDecimal adjustedAmount, BigDecimal unreceivedAmount,
			ReceivableStatus status) {
	}

	private record PayableRow(Long id, String payableNo, Long sourceId, BigDecimal totalAmount, BigDecimal paidAmount,
			BigDecimal adjustedAmount, BigDecimal unpaidAmount, PayableStatus status) {
	}

	private record TraceLinkRow(String sourceType, Long sourceId, Long sourceLineId, String reverseType,
			Long reverseId, Long reverseLineId, LocalDate businessDate, BigDecimal quantity, BigDecimal amount,
			String shipmentNo, LocalDate shipmentDate, String shipmentStatus, Integer shipmentLineNo, String returnNo,
			LocalDate returnDate, String returnStatus, Integer returnLineNo, Long stockMovementId,
			Long settlementAdjustmentId) {
	}

	private record PurchaseTraceLinkRow(String sourceType, Long sourceId, Long sourceLineId, String reverseType,
			Long reverseId, Long reverseLineId, LocalDate businessDate, BigDecimal quantity, BigDecimal amount,
			String receiptNo, LocalDate receiptDate, String receiptStatus, Integer receiptLineNo, String returnNo,
			LocalDate returnDate, String returnStatus, Integer returnLineNo, Long stockMovementId,
			Long settlementAdjustmentId) {
	}

	private record ProductionTraceLinkRow(String sourceType, Long sourceId, Long sourceLineId, String reverseType,
			Long reverseId, Long reverseLineId, LocalDate businessDate, BigDecimal quantity, BigDecimal amount,
			String sourceNo, LocalDate sourceDate, String sourceStatus, Integer sourceLineNo, Long workOrderId,
			String reverseNo, LocalDate reverseDate, String reverseStatus, Integer reverseLineNo,
			Long stockMovementId, Long costRecordId) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
