package com.qherp.api.system.procurement;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.inventory.InventoryTrackingMethod;
import com.qherp.api.system.inventory.InventoryTrackingService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import com.qherp.api.system.quality.QualityAdminService;
import com.qherp.api.system.quality.QualityInspectionSourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ProcurementAdminService {

	private static final String ORDER_TARGET = "PROCUREMENT_ORDER";

	private static final String RECEIPT_TARGET = "PROCUREMENT_RECEIPT";

	private static final String RECEIPT_SOURCE_TYPE = "PURCHASE_RECEIPT";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_NO_ATTEMPTS = 3;

	private static final AtomicInteger ORDER_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger RECEIPT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final InventoryPostingService inventoryPostingService;

	private final InventoryTrackingService inventoryTrackingService;

	private final BusinessPeriodGuard businessPeriodGuard;

	private final QualityAdminService qualityAdminService;

	public ProcurementAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService, BusinessPeriodGuard businessPeriodGuard,
			QualityAdminService qualityAdminService, InventoryTrackingService inventoryTrackingService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
		this.inventoryTrackingService = inventoryTrackingService;
		this.businessPeriodGuard = businessPeriodGuard;
		this.qualityAdminService = qualityAdminService;
	}

	@Transactional(readOnly = true)
	public PageResponse<PurchaseOrderSummaryResponse> orders(String keyword, Long supplierId, String status,
			LocalDate dateFrom, LocalDate dateTo, LocalDate expectedDateFrom, LocalDate expectedDateTo, int page,
			int pageSize) {
		QueryParts queryParts = orderQueryParts(keyword, supplierId, status, dateFrom, dateTo, expectedDateFrom,
				expectedDateTo);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_order o
				join mst_supplier s on s.id = o.supplier_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<PurchaseOrderSummaryResponse> items = this.jdbcTemplate.query("""
				select o.id, o.order_no, o.supplier_id, s.code as supplier_code, s.name as supplier_name,
				       o.order_date, o.expected_arrival_date, o.status,
				       (select count(*) from proc_purchase_order_line l where l.order_id = o.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as total_quantity,
				       coalesce((select sum(l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as received_quantity,
				       coalesce((select sum(l.quantity - l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as remaining_quantity,
				       case when o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				            then coalesce((select sum(l.quantity - l.received_quantity)
				                           from proc_purchase_order_line l where l.order_id = o.id), 0)
				            else 0 end as in_transit_quantity,
				       o.remark, o.created_by, o.created_at, o.updated_at, o.confirmed_by, o.confirmed_at,
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at
				from proc_purchase_order o
				join mst_supplier s on s.id = o.supplier_id
				%s
				order by o.updated_at desc, o.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapOrderSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PurchaseOrderDetailResponse order(Long id) {
		PurchaseOrderSummaryResponse summary = orderSummary(id).orElseThrow(this::orderNotFound);
		return new PurchaseOrderDetailResponse(summary.id(), summary.orderNo(), summary.supplierId(),
				summary.supplierCode(), summary.supplierName(), summary.orderDate(), summary.expectedArrivalDate(),
				summary.status(), summary.lineCount(), summary.totalQuantity(), summary.receivedQuantity(),
				summary.remainingQuantity(), summary.inTransitQuantity(), summary.inTransitStatus(),
				summary.inTransitStatusName(), summary.remark(), summary.createdByName(), summary.createdAt(),
				summary.updatedAt(), summary.confirmedByName(), summary.confirmedAt(), summary.cancelledByName(),
				summary.cancelledAt(), summary.closedByName(), summary.closedAt(), orderLines(id), orderReceipts(id));
	}

	@Transactional
	public PurchaseOrderDetailResponse createOrder(PurchaseOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedOrder order = validateOrderRequest(request);
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CREATE, "PURCHASE_ORDER", null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertOrderWithRetry(order, operator.username(), now);
			insertOrderLines(created.id(), order.lines(), now);
			this.auditService.record(operator, "PROCUREMENT_ORDER_CREATE", ORDER_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return order(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseOrderDetailResponse updateOrder(Long id, PurchaseOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		OrderRow current = lockOrder(id).orElseThrow(this::orderNotFound);
		if (current.status() != PurchaseOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
		ValidatedOrder order = validateOrderRequest(request);
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.UPDATE, "PURCHASE_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update proc_purchase_order
					set supplier_id = ?, order_date = ?, expected_arrival_date = ?, remark = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", order.supplier().id(), order.orderDate(), order.expectedArrivalDate(),
					blankToNull(order.remark()), operator.username(), now, id);
			this.jdbcTemplate.update("delete from proc_purchase_order_line where order_id = ?", id);
			insertOrderLines(id, order.lines(), now);
			this.auditService.record(operator, "PROCUREMENT_ORDER_UPDATE", ORDER_TARGET, id, current.orderNo(),
					servletRequest);
			return order(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseOrderDetailResponse confirmOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		if (order.status() != PurchaseOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CONFIRM, "PURCHASE_ORDER", id);
		validateOrderForConfirmation(order);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_purchase_order
				set status = ?, confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", PurchaseOrderStatus.CONFIRMED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "PROCUREMENT_ORDER_CONFIRM", ORDER_TARGET, id, order.orderNo(),
				servletRequest);
		return order(id);
	}

	@Transactional
	public PurchaseOrderDetailResponse cancelOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		if (order.status() != PurchaseOrderStatus.DRAFT
				&& !(order.status() == PurchaseOrderStatus.CONFIRMED && !hasPostedReceipts(id))) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CANCEL, "PURCHASE_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_purchase_order
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", PurchaseOrderStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "PROCUREMENT_ORDER_CANCEL", ORDER_TARGET, id, order.orderNo(),
				servletRequest);
		return order(id);
	}

	@Transactional
	public PurchaseOrderDetailResponse closeOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		if (order.status() != PurchaseOrderStatus.CONFIRMED
				&& order.status() != PurchaseOrderStatus.PARTIALLY_RECEIVED
				&& order.status() != PurchaseOrderStatus.RECEIVED) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CLOSE, "PURCHASE_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_purchase_order
				set status = ?, closed_by = ?, closed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", PurchaseOrderStatus.CLOSED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "PROCUREMENT_ORDER_CLOSE", ORDER_TARGET, id, order.orderNo(),
				servletRequest);
		return order(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<PurchaseReceiptSummaryResponse> receipts(String keyword, Long supplierId, Long warehouseId,
			String status, LocalDate dateFrom, LocalDate dateTo, Long orderId, int page, int pageSize) {
		QueryParts queryParts = receiptQueryParts(keyword, supplierId, warehouseId, status, dateFrom, dateTo, orderId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_receipt r
				join proc_purchase_order o on o.id = r.order_id
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<PurchaseReceiptSummaryResponse> items = this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.order_id, o.order_no, r.supplier_id, s.name as supplier_name,
				       r.warehouse_id, w.name as warehouse_name, r.business_date, r.status,
				       (select count(*) from proc_purchase_receipt_line l where l.receipt_id = r.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_receipt_line l where l.receipt_id = r.id), 0) as total_quantity,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at
				from proc_purchase_receipt r
				join proc_purchase_order o on o.id = r.order_id
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapReceiptSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PurchaseReceiptDetailResponse receipt(Long id) {
		PurchaseReceiptSummaryResponse summary = receiptSummary(id).orElseThrow(this::receiptNotFound);
		return new PurchaseReceiptDetailResponse(summary.id(), summary.receiptNo(), summary.orderId(),
				summary.orderNo(), summary.supplierId(), summary.supplierName(), summary.warehouseId(),
				summary.warehouseName(), summary.businessDate(), summary.status(), summary.lineCount(),
				summary.totalQuantity(), summary.remark(), summary.createdByName(), summary.createdAt(),
				summary.updatedAt(), summary.postedByName(), summary.postedAt(), receiptLines(id),
				orderSummary(summary.orderId()).orElseThrow(this::orderNotFound), receiptInventoryMovements(id));
	}

	@Transactional
	public PurchaseReceiptDetailResponse createReceipt(Long orderId, PurchaseReceiptRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		requireReceivableOrder(order);
		ValidatedReceipt receipt = validateReceiptRequest(order, request);
		this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.CREATE, RECEIPT_SOURCE_TYPE, null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertReceiptWithRetry(order, receipt, operator.username(), now);
			insertReceiptLines(created.id(), receipt, now, operator.username());
			this.auditService.record(operator, "PROCUREMENT_RECEIPT_CREATE", RECEIPT_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return receipt(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseReceiptDetailResponse updateReceipt(Long id, PurchaseReceiptRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ReceiptRow current = lockReceipt(id).orElseThrow(this::receiptNotFound);
		if (current.status() != PurchaseReceiptStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_POSTED_IMMUTABLE);
		}
		OrderRow order = lockOrder(current.orderId()).orElseThrow(this::orderNotFound);
		requireReceivableOrder(order);
		ValidatedReceipt receipt = validateReceiptRequest(order, request);
		this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.UPDATE, RECEIPT_SOURCE_TYPE, id);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update proc_purchase_receipt
					set warehouse_id = ?, business_date = ?, remark = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", receipt.warehouseId(), receipt.businessDate(), blankToNull(receipt.remark()),
					operator.username(), now, id);
		this.inventoryTrackingService.deleteDraftDocumentTracking(RECEIPT_SOURCE_TYPE, id);
			this.jdbcTemplate.update("delete from proc_purchase_receipt_line where receipt_id = ?", id);
			insertReceiptLines(id, receipt, now, operator.username());
			this.auditService.record(operator, "PROCUREMENT_RECEIPT_UPDATE", RECEIPT_TARGET, id,
					current.receiptNo(), servletRequest);
			return receipt(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseReceiptDetailResponse postReceipt(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		try {
			ReceiptRow receipt = lockReceipt(id).orElseThrow(this::receiptNotFound);
			if (receipt.status() != PurchaseReceiptStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_DUPLICATE_POST);
			}
			this.businessPeriodGuard.assertWritable(receipt.businessDate(), BusinessPeriodOperation.POST, RECEIPT_SOURCE_TYPE, id);
			OrderRow order = lockOrder(receipt.orderId()).orElseThrow(this::orderNotFound);
			requireReceivableOrder(order);
			validateEnabledWarehouse(receipt.warehouseId());
			List<ReceiptLineRow> lines = receiptLineRows(id);
			if (lines.isEmpty()) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EMPTY_LINES);
			}
			OffsetDateTime now = OffsetDateTime.now();
			for (ReceiptLineRow line : lines) {
				postReceiptLine(order.id(), receipt, line, operator.username(), now);
			}
			PurchaseOrderStatus nextOrderStatus = receivedOrderStatus(order.id());
			this.jdbcTemplate.update("""
					update proc_purchase_receipt
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", PurchaseReceiptStatus.POSTED.name(), operator.username(), now, operator.username(), now,
					id);
			this.jdbcTemplate.update("""
					update proc_purchase_order
					set status = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", nextOrderStatus.name(), operator.username(), now, order.id());
			this.auditService.record(operator, "PROCUREMENT_RECEIPT_POST", RECEIPT_TARGET, id, receipt.receiptNo(),
					servletRequest);
			return receipt(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	private void postReceiptLine(Long orderId, ReceiptRow receipt, ReceiptLineRow line, String operatorName,
			OffsetDateTime now) {
		OrderLineRow orderLine = lockOrderLine(orderId, line.orderLineId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID));
		if (!line.materialId().equals(orderLine.materialId()) || !line.unitId().equals(orderLine.unitId())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID);
		}
		validateReceiptOrderLineMasterData(orderLine);
		BigDecimal remainingQuantity = orderLine.quantity().subtract(orderLine.receivedQuantity());
		if (line.quantity().compareTo(remainingQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_ORDER);
		}
		List<InventoryTrackingService.ResolvedTrackingAllocation> trackingAllocations = this.inventoryTrackingService
			.storedAllocations(RECEIPT_SOURCE_TYPE, receipt.id(), line.id());
		BigDecimal beforeQuantity = ZERO;
		BigDecimal afterQuantity = ZERO;
		if (trackingAllocations.isEmpty()) {
			assertUntrackedInboundMaterial(line.materialId());
			InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
					new InventoryPostingService.PostingRequest(InventoryMovementType.PURCHASE_RECEIPT,
							InventoryDirection.IN, receipt.warehouseId(), line.materialId(), line.unitId(),
							line.quantity(), InventoryQualityStatus.PENDING_INSPECTION, RECEIPT_SOURCE_TYPE,
							receipt.id(), line.id(), receipt.businessDate(), "采购入库", line.remark(), operatorName));
			beforeQuantity = posting.beforeQuantity();
			afterQuantity = posting.afterQuantity();
		}
		else {
			for (InventoryTrackingService.ResolvedTrackingAllocation allocation : trackingAllocations) {
				InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
						new InventoryPostingService.PostingRequest(InventoryMovementType.PURCHASE_RECEIPT,
								InventoryDirection.IN, receipt.warehouseId(), line.materialId(), line.unitId(),
								allocation.quantity(), InventoryQualityStatus.PENDING_INSPECTION, RECEIPT_SOURCE_TYPE,
								receipt.id(), line.id(), receipt.businessDate(), "采购入库", line.remark(),
								operatorName, allocation.batchId(), allocation.serialId()));
				this.inventoryTrackingService.attachMovement(allocation.allocationId(), posting.movementId());
				this.inventoryTrackingService.markInboundPosted(allocation, receipt.warehouseId(),
						InventoryQualityStatus.PENDING_INSPECTION, posting.movementId(), operatorName);
				beforeQuantity = beforeQuantity.add(posting.beforeQuantity());
				afterQuantity = afterQuantity.add(posting.afterQuantity());
			}
		}
		this.qualityAdminService.createPendingInspection(QualityInspectionSourceType.PURCHASE_RECEIPT, receipt.id(),
				line.id(), receipt.warehouseId(), line.materialId(), line.unitId(), receipt.businessDate(),
				line.quantity(), operatorName);
		this.jdbcTemplate.update("""
				update proc_purchase_receipt_line
				set ordered_quantity = ?, received_quantity_before = ?, remaining_quantity_before = ?,
				    before_quantity = ?, after_quantity = ?, updated_at = ?
				where id = ?
				""", orderLine.quantity(), orderLine.receivedQuantity(), remainingQuantity, beforeQuantity,
				afterQuantity, now, line.id());
		this.jdbcTemplate.update("""
				update proc_purchase_order_line
				set received_quantity = received_quantity + ?, updated_at = ?, version = version + 1
				where id = ?
				""", line.quantity(), now, orderLine.id());
	}

	private void assertUntrackedInboundMaterial(Long materialId) {
		InventoryTrackingMethod trackingMethod = this.inventoryTrackingService.trackingMethod(materialId);
		if (trackingMethod == InventoryTrackingMethod.BATCH) {
			throw new BusinessException(ApiErrorCode.INVENTORY_BATCH_REQUIRED);
		}
		if (trackingMethod == InventoryTrackingMethod.SERIAL) {
			throw new BusinessException(ApiErrorCode.INVENTORY_SERIAL_REQUIRED);
		}
	}

	private ValidatedOrder validateOrderRequest(PurchaseOrderRequest request) {
		if (request == null || request.orderDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		SupplierRef supplier = validateEnabledSupplier(request.supplierId());
		String remark = validateOptionalText(request.remark(), 500);
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> materialIds = new HashSet<>();
		List<ValidatedOrderLine> lines = new ArrayList<>();
		for (PurchaseOrderLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validatePurchasableMaterial(line.materialId());
			if (!materialIds.add(material.id())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_DUPLICATE_LINE);
			}
			Long unitId = validateUnit(line.unitId(), material);
			BigDecimal quantity = validateQuantity(line.quantity());
			BigDecimal unitPrice = validateUnitPrice(line.unitPrice());
			lines.add(new ValidatedOrderLine(line.lineNo(), material.id(), unitId, quantity, unitPrice,
					line.expectedArrivalDate(), validateOptionalText(line.remark(), 500)));
		}
		return new ValidatedOrder(supplier, request.orderDate(), request.expectedArrivalDate(), remark, lines);
	}

	private void validateOrderForConfirmation(OrderRow order) {
		validateEnabledSupplier(order.supplierId());
		List<OrderLineRow> lines = orderLineRowsForValidation(order.id());
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> materialIds = new HashSet<>();
		for (OrderLineRow line : lines) {
			if (line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validatePurchasableMaterial(line.materialId());
			if (!materialIds.add(material.id())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_DUPLICATE_LINE);
			}
			validateUnit(line.unitId(), material);
			validateQuantity(line.quantity());
			validateUnitPrice(line.unitPrice());
		}
	}

	private ValidatedReceipt validateReceiptRequest(OrderRow order, PurchaseReceiptRequest request) {
		if (request == null || request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateEnabledWarehouse(request.warehouseId());
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> orderLineIds = new HashSet<>();
		List<ValidatedReceiptLine> lines = new ArrayList<>();
		for (PurchaseReceiptLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (line.orderLineId() == null || !orderLineIds.add(line.orderLineId())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_DUPLICATE_LINE);
			}
			OrderLineRow orderLine = orderLine(order.id(), line.orderLineId())
				.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID));
			if ((line.materialId() != null && !line.materialId().equals(orderLine.materialId()))
					|| (line.unitId() != null && !line.unitId().equals(orderLine.unitId()))) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID);
			}
			validateReceiptOrderLineMasterData(orderLine);
			BigDecimal quantity = validateQuantity(line.quantity());
			BigDecimal remainingQuantity = orderLine.quantity().subtract(orderLine.receivedQuantity());
			if (quantity.compareTo(remainingQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_ORDER);
			}
			lines.add(new ValidatedReceiptLine(line.lineNo(), orderLine.id(), orderLine.materialId(),
					orderLine.unitId(), orderLine.quantity(), orderLine.receivedQuantity(), remainingQuantity,
					quantity, validateOptionalText(line.remark(), 500), line.trackingAllocations()));
		}
		return new ValidatedReceipt(request.warehouseId(), request.businessDate(), validateOptionalText(request.remark(),
				500), lines);
	}

	private void validateReceiptOrderLineMasterData(OrderLineRow orderLine) {
		MaterialRef material = validatePurchasableMaterial(orderLine.materialId());
		validateUnit(orderLine.unitId(), material);
	}

	private SupplierRef validateEnabledSupplier(Long supplierId) {
		if (supplierId == null) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SUPPLIER_INVALID);
		}
		SupplierRef supplier = supplierRef(supplierId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_SUPPLIER_INVALID));
		if (!"ENABLED".equals(supplier.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SUPPLIER_INVALID);
		}
		return supplier;
	}

	private void validateEnabledWarehouse(Long warehouseId) {
		if (warehouseId == null) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_WAREHOUSE_INVALID);
		}
		String status = this.jdbcTemplate.query("select status from mst_warehouse where id = ?",
				(rs, rowNum) -> rs.getString("status"), warehouseId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_WAREHOUSE_INVALID);
		}
	}

	private MaterialRef validatePurchasableMaterial(Long materialId) {
		if (materialId == null) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_MATERIAL_INVALID);
		}
		MaterialRef material = materialRef(materialId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_MATERIAL_INVALID));
		if (!"ENABLED".equals(material.status()) || !"PURCHASED".equals(material.sourceType())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_MATERIAL_INVALID);
		}
		return material;
	}

	private Long validateUnit(Long requestedUnitId, MaterialRef material) {
		Long unitId = requestedUnitId == null ? material.unitId() : requestedUnitId;
		String status = this.jdbcTemplate.query("select status from mst_unit where id = ?",
				(rs, rowNum) -> rs.getString("status"), unitId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status) || !unitId.equals(material.unitId())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_UNIT_INVALID);
		}
		return unitId;
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUANTITY_INVALID);
		}
		return value;
	}

	private BigDecimal validateUnitPrice(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_UNIT_PRICE_INVALID);
		}
		return value;
	}

	private String validateOptionalText(String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private void requireReceivableOrder(OrderRow order) {
		if (order.status() != PurchaseOrderStatus.CONFIRMED
				&& order.status() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
	}

	private CreatedDocument insertOrderWithRetry(ValidatedOrder order, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String orderNo = nextNo("PO", ORDER_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into proc_purchase_order (
							order_no, supplier_id, order_date, expected_arrival_date, status, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, orderNo, order.supplier().id(), order.orderDate(),
						order.expectedArrivalDate(), PurchaseOrderStatus.DRAFT.name(), blankToNull(order.remark()),
						operatorName, now, operatorName, now);
				return new CreatedDocument(id, orderNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_proc_purchase_order_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertOrderLines(Long orderId, List<ValidatedOrderLine> lines, OffsetDateTime now) {
		for (ValidatedOrderLine line : lines) {
			this.jdbcTemplate.update("""
					insert into proc_purchase_order_line (
						order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
						expected_arrival_date, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", orderId, line.lineNo(), line.materialId(), line.unitId(), line.quantity(), ZERO,
					line.unitPrice(), line.expectedArrivalDate(), blankToNull(line.remark()), now, now);
		}
	}

	private CreatedDocument insertReceiptWithRetry(OrderRow order, ValidatedReceipt receipt, String operatorName,
			OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String receiptNo = nextNo("PR", RECEIPT_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into proc_purchase_receipt (
							receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, receiptNo, order.id(), order.supplierId(), receipt.warehouseId(),
						receipt.businessDate(), PurchaseReceiptStatus.DRAFT.name(), blankToNull(receipt.remark()),
						operatorName, now, operatorName, now);
				return new CreatedDocument(id, receiptNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_proc_purchase_receipt_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertReceiptLines(Long receiptId, ValidatedReceipt receipt, OffsetDateTime now,
			String operatorName) {
		for (int i = 0; i < receipt.lines().size(); i++) {
			ValidatedReceiptLine line = receipt.lines().get(i);
			Long lineId = this.jdbcTemplate.queryForObject("""
					insert into proc_purchase_receipt_line (
						receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
						received_quantity_before, remaining_quantity_before, quantity, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, receiptId, line.lineNo(), line.orderLineId(), line.materialId(), line.unitId(),
					line.orderedQuantity(), line.receivedQuantityBefore(), line.remainingQuantityBefore(),
					line.quantity(), blankToNull(line.remark()), now, now);
			this.inventoryTrackingService.prepareInboundAllocations(RECEIPT_SOURCE_TYPE, receiptId, lineId,
					receipt.warehouseId(), line.materialId(), line.unitId(), line.quantity(),
					InventoryQualityStatus.PENDING_INSPECTION, receipt.businessDate(), line.trackingAllocations(),
					operatorName, "lines[" + i + "].trackingAllocations");
		}
	}

	private QueryParts orderQueryParts(String keyword, Long supplierId, String status, LocalDate dateFrom,
			LocalDate dateTo, LocalDate expectedDateFrom, LocalDate expectedDateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(o.order_no ilike ? or s.code ilike ? or s.name ilike ? or o.remark ilike ?
					or exists (
						select 1
						from proc_purchase_order_line kl
						join mst_material km on km.id = kl.material_id
						where kl.order_id = o.id
						and (km.code ilike ? or km.name ilike ?)
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
			conditions.add("o.supplier_id = ?");
			args.add(supplierId);
		}
		if (hasText(status)) {
			conditions.add("o.status = ?");
			args.add(parseOrderStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("o.order_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("o.order_date <= ?");
			args.add(dateTo);
		}
		if (expectedDateFrom != null) {
			conditions.add("o.expected_arrival_date >= ?");
			args.add(expectedDateFrom);
		}
		if (expectedDateTo != null) {
			conditions.add("o.expected_arrival_date <= ?");
			args.add(expectedDateTo);
		}
		return where(conditions, args);
	}

	private QueryParts receiptQueryParts(String keyword, Long supplierId, Long warehouseId, String status,
			LocalDate dateFrom, LocalDate dateTo, Long orderId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(r.receipt_no ilike ? or o.order_no ilike ? or s.code ilike ? or s.name ilike ? or r.remark ilike ?
					or exists (
						select 1
						from proc_purchase_receipt_line kl
						join mst_material km on km.id = kl.material_id
						where kl.receipt_id = r.id
						and (km.code ilike ? or km.name ilike ?)
					))
					""");
			String like = "%" + keyword + "%";
			args.add(like);
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
			args.add(parseReceiptStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("r.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("r.business_date <= ?");
			args.add(dateTo);
		}
		if (orderId != null) {
			conditions.add("r.order_id = ?");
			args.add(orderId);
		}
		return where(conditions, args);
	}

	private Optional<PurchaseOrderSummaryResponse> orderSummary(Long id) {
		return this.jdbcTemplate.query("""
				select o.id, o.order_no, o.supplier_id, s.code as supplier_code, s.name as supplier_name,
				       o.order_date, o.expected_arrival_date, o.status,
				       (select count(*) from proc_purchase_order_line l where l.order_id = o.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as total_quantity,
				       coalesce((select sum(l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as received_quantity,
				       coalesce((select sum(l.quantity - l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as remaining_quantity,
				       case when o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				            then coalesce((select sum(l.quantity - l.received_quantity)
				                           from proc_purchase_order_line l where l.order_id = o.id), 0)
				            else 0 end as in_transit_quantity,
				       o.remark, o.created_by, o.created_at, o.updated_at, o.confirmed_by, o.confirmed_at,
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at
				from proc_purchase_order o
				join mst_supplier s on s.id = o.supplier_id
				where o.id = ?
				""", this::mapOrderSummary, id).stream().findFirst();
	}

	private Optional<PurchaseReceiptSummaryResponse> receiptSummary(Long id) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.order_id, o.order_no, r.supplier_id, s.name as supplier_name,
				       r.warehouse_id, w.name as warehouse_name, r.business_date, r.status,
				       (select count(*) from proc_purchase_receipt_line l where l.receipt_id = r.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_receipt_line l where l.receipt_id = r.id), 0) as total_quantity,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at
				from proc_purchase_receipt r
				join proc_purchase_order o on o.id = r.order_id
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				where r.id = ?
				""", this::mapReceiptSummary, id).stream().findFirst();
	}

	private Optional<OrderRow> lockOrder(Long id) {
		return this.jdbcTemplate.query("""
				select id, order_no, supplier_id, order_date, expected_arrival_date, status, remark
				from proc_purchase_order
				where id = ?
				for update
				""", this::mapOrderRow, id).stream().findFirst();
	}

	private Optional<ReceiptRow> lockReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select id, receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark
				from proc_purchase_receipt
				where id = ?
				for update
				""", this::mapReceiptRow, id).stream().findFirst();
	}

	private Optional<OrderLineRow> orderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
				       expected_arrival_date, remark
				from proc_purchase_order_line
				where order_id = ?
				and id = ?
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private Optional<OrderLineRow> lockOrderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
				       expected_arrival_date, remark
				from proc_purchase_order_line
				where order_id = ?
				and id = ?
				for update
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private List<OrderLineRow> orderLineRowsForValidation(Long orderId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
				       expected_arrival_date, remark
				from proc_purchase_order_line
				where order_id = ?
				order by line_no asc, id asc
				""", this::mapOrderLineRow, orderId);
	}

	private List<ReceiptLineRow> receiptLineRows(Long receiptId) {
		return this.jdbcTemplate.query("""
				select id, receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
				       received_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
				       remark
				from proc_purchase_receipt_line
				where receipt_id = ?
				order by line_no asc, id asc
				""", this::mapReceiptLineRow, receiptId);
	}

	private List<PurchaseOrderLineResponse> orderLines(Long orderId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.material_id, m.code as material_code, m.name as material_name,
				       m.specification as material_spec, l.unit_id, u.name as unit_name, l.quantity,
				       l.received_quantity, (l.quantity - l.received_quantity) as remaining_quantity,
				       case when o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				            then l.quantity - l.received_quantity else 0 end as in_transit_quantity,
				       l.unit_price, l.expected_arrival_date, l.remark
				from proc_purchase_order_line l
				join proc_purchase_order o on o.id = l.order_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.order_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> {
			BigDecimal inTransitQuantity = rs.getBigDecimal("in_transit_quantity");
			LocalDate expectedArrivalDate = rs.getObject("expected_arrival_date", LocalDate.class);
			InTransitStatus inTransitStatus = inTransitStatus(inTransitQuantity, expectedArrivalDate);
			return new PurchaseOrderLineResponse(rs.getLong("id"), rs.getInt("line_no"),
					rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
					rs.getString("material_spec"), rs.getLong("unit_id"), rs.getString("unit_name"),
					rs.getBigDecimal("quantity"), rs.getBigDecimal("received_quantity"),
					rs.getBigDecimal("remaining_quantity"), inTransitQuantity, inTransitStatus.code(),
					inTransitStatus.displayName(), rs.getBigDecimal("unit_price"), expectedArrivalDate,
					rs.getString("remark"));
		}, orderId);
	}

	private List<PurchaseReceiptSummaryResponse> orderReceipts(Long orderId) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.order_id, o.order_no, r.supplier_id, s.name as supplier_name,
				       r.warehouse_id, w.name as warehouse_name, r.business_date, r.status,
				       (select count(*) from proc_purchase_receipt_line l where l.receipt_id = r.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_receipt_line l where l.receipt_id = r.id), 0) as total_quantity,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at
				from proc_purchase_receipt r
				join proc_purchase_order o on o.id = r.order_id
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				where r.order_id = ?
				order by r.updated_at desc, r.id desc
				""", this::mapReceiptSummary, orderId);
	}

	private List<PurchaseReceiptLineResponse> receiptLines(Long receiptId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.order_line_id, l.material_id, m.code as material_code,
				       m.name as material_name, m.tracking_method, l.unit_id, u.name as unit_name, l.ordered_quantity,
				       l.received_quantity_before, l.remaining_quantity_before, l.quantity, l.before_quantity,
				       l.after_quantity,
				       case when o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				            then ol.quantity - ol.received_quantity else 0 end as in_transit_quantity,
				       ol.expected_arrival_date, l.remark
				from proc_purchase_receipt_line l
				join proc_purchase_order_line ol on ol.id = l.order_line_id
				join proc_purchase_order o on o.id = ol.order_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.receipt_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> {
			BigDecimal inTransitQuantity = rs.getBigDecimal("in_transit_quantity");
			InTransitStatus inTransitStatus = inTransitStatus(inTransitQuantity,
					rs.getObject("expected_arrival_date", LocalDate.class));
			return new PurchaseReceiptLineResponse(rs.getLong("id"), rs.getInt("line_no"),
					rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
					rs.getString("material_name"), rs.getString("tracking_method"),
					InventoryTrackingMethod.valueOf(rs.getString("tracking_method")).displayName(),
					rs.getLong("unit_id"), rs.getString("unit_name"),
					rs.getBigDecimal("ordered_quantity"), rs.getBigDecimal("received_quantity_before"),
					rs.getBigDecimal("remaining_quantity_before"), rs.getBigDecimal("quantity"),
					rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"), inTransitQuantity,
					inTransitStatus.code(), inTransitStatus.displayName(), rs.getString("remark"),
					this.inventoryTrackingService.allocationResponses(RECEIPT_SOURCE_TYPE, receiptId,
							rs.getLong("id")));
		},
				receiptId);
	}

	private List<PurchaseReceiptInventoryMovementResponse> receiptInventoryMovements(Long receiptId) {
		return this.jdbcTemplate.query("""
				select sm.id, sm.movement_no, sm.movement_type, sm.direction, w.name as warehouse_name,
				       m.code as material_code, m.name as material_name, sm.quantity, sm.before_quantity,
				       sm.after_quantity, sm.business_date, sm.operator_name, sm.occurred_at,
				       sm.batch_id, b.batch_no, sm.serial_id, s.serial_no
				from inv_stock_movement sm
				join proc_purchase_receipt_line rl on rl.id = sm.source_line_id
				join mst_warehouse w on w.id = sm.warehouse_id
				join mst_material m on m.id = sm.material_id
				left join inv_batch b on b.id = sm.batch_id
				left join inv_serial s on s.id = sm.serial_id
				where sm.source_type = ?
				and sm.source_id = ?
				order by sm.occurred_at asc, rl.line_no asc, sm.id asc
				""",
				(rs, rowNum) -> new PurchaseReceiptInventoryMovementResponse(rs.getLong("id"),
						rs.getString("movement_no"), rs.getString("movement_type"), rs.getString("direction"),
						rs.getString("warehouse_name"), rs.getString("material_code"), rs.getString("material_name"),
						rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"),
						rs.getBigDecimal("after_quantity"), rs.getObject("business_date", LocalDate.class),
						rs.getString("operator_name"), rs.getObject("occurred_at", OffsetDateTime.class),
						nullableLong(rs, "batch_id"), rs.getString("batch_no"), nullableLong(rs, "serial_id"),
						rs.getString("serial_no")),
				RECEIPT_SOURCE_TYPE, receiptId);
	}

	private Optional<SupplierRef> supplierRef(Long supplierId) {
		return this.jdbcTemplate.query("""
				select id, code, name, status
				from mst_supplier
				where id = ?
				""", (rs, rowNum) -> new SupplierRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getString("status")), supplierId).stream().findFirst();
	}

	private Optional<MaterialRef> materialRef(Long materialId) {
		return this.jdbcTemplate.query("""
				select id, code, name, unit_id, source_type, status
				from mst_material
				where id = ?
				""", (rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getLong("unit_id"), rs.getString("source_type"), rs.getString("status")), materialId)
			.stream()
			.findFirst();
	}

	private boolean hasPostedReceipts(Long orderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_receipt
				where order_id = ?
				and status = ?
				""", Long.class, orderId, PurchaseReceiptStatus.POSTED.name());
		return count != null && count > 0;
	}

	private PurchaseOrderStatus receivedOrderStatus(Long orderId) {
		QuantityTotals totals = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity), 0) as total_quantity,
				       coalesce(sum(received_quantity), 0) as received_quantity
				from proc_purchase_order_line
				where order_id = ?
				""", (rs, rowNum) -> new QuantityTotals(rs.getBigDecimal("total_quantity"),
				rs.getBigDecimal("received_quantity")), orderId);
		if (totals != null && totals.receivedQuantity().compareTo(ZERO) > 0
				&& totals.receivedQuantity().compareTo(totals.totalQuantity()) >= 0) {
			return PurchaseOrderStatus.RECEIVED;
		}
		return PurchaseOrderStatus.PARTIALLY_RECEIVED;
	}

	private PurchaseOrderSummaryResponse mapOrderSummary(ResultSet rs, int rowNum) throws SQLException {
		BigDecimal inTransitQuantity = rs.getBigDecimal("in_transit_quantity");
		LocalDate expectedArrivalDate = rs.getObject("expected_arrival_date", LocalDate.class);
		InTransitStatus inTransitStatus = inTransitStatus(inTransitQuantity, expectedArrivalDate);
		return new PurchaseOrderSummaryResponse(rs.getLong("id"), rs.getString("order_no"),
				rs.getLong("supplier_id"), rs.getString("supplier_code"), rs.getString("supplier_name"),
				rs.getObject("order_date", LocalDate.class), expectedArrivalDate,
				rs.getString("status"), rs.getInt("line_count"), rs.getBigDecimal("total_quantity"),
				rs.getBigDecimal("received_quantity"), rs.getBigDecimal("remaining_quantity"), inTransitQuantity,
				inTransitStatus.code(), inTransitStatus.displayName(), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("confirmed_by"),
				rs.getObject("confirmed_at", OffsetDateTime.class), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class));
	}

	private PurchaseReceiptSummaryResponse mapReceiptSummary(ResultSet rs, int rowNum) throws SQLException {
		return new PurchaseReceiptSummaryResponse(rs.getLong("id"), rs.getString("receipt_no"),
				rs.getLong("order_id"), rs.getString("order_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getInt("line_count"),
				rs.getBigDecimal("total_quantity"), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class));
	}

	private OrderRow mapOrderRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getLong("supplier_id"),
				rs.getObject("order_date", LocalDate.class), rs.getObject("expected_arrival_date", LocalDate.class),
				PurchaseOrderStatus.valueOf(rs.getString("status")), rs.getString("remark"));
	}

	private ReceiptRow mapReceiptRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptRow(rs.getLong("id"), rs.getString("receipt_no"), rs.getLong("order_id"),
				rs.getLong("supplier_id"), rs.getLong("warehouse_id"), rs.getObject("business_date", LocalDate.class),
				PurchaseReceiptStatus.valueOf(rs.getString("status")), rs.getString("remark"));
	}

	private OrderLineRow mapOrderLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderLineRow(rs.getLong("id"), rs.getLong("order_id"), rs.getInt("line_no"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("received_quantity"), rs.getBigDecimal("unit_price"),
				rs.getObject("expected_arrival_date", LocalDate.class), rs.getString("remark"));
	}

	private ReceiptLineRow mapReceiptLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptLineRow(rs.getLong("id"), rs.getLong("receipt_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("ordered_quantity"), rs.getBigDecimal("received_quantity_before"),
				rs.getBigDecimal("remaining_quantity_before"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"), rs.getString("remark"));
	}

	private BusinessException duplicateProcurementException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_proc_purchase_order_line_material")) {
			return new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_DUPLICATE_LINE);
		}
		if (containsConstraint(exception, "uk_proc_purchase_receipt_line_order_line")) {
			return new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_DUPLICATE_LINE);
		}
		if (containsConstraint(exception, "uk_inv_stock_movement_source")) {
			return new BusinessException(ApiErrorCode.PROCUREMENT_MOVEMENT_SOURCE_DUPLICATED);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private PurchaseOrderStatus parseOrderStatus(String value) {
		try {
			return PurchaseOrderStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
	}

	private PurchaseReceiptStatus parseReceiptStatus(String value) {
		try {
			return PurchaseReceiptStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_STATUS_INVALID);
		}
	}

	private BusinessException orderNotFound() {
		return new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_NOT_FOUND);
	}

	private BusinessException receiptNotFound() {
		return new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_NOT_FOUND);
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

	private long integerDigits(BigDecimal value) {
		return Math.max(0L, (long) value.precision() - value.scale());
	}

	private InTransitStatus inTransitStatus(BigDecimal inTransitQuantity, LocalDate expectedArrivalDate) {
		if (inTransitQuantity == null || inTransitQuantity.compareTo(ZERO) <= 0 || expectedArrivalDate == null) {
			return InTransitStatus.NOT_COUNTED;
		}
		LocalDate today = LocalDate.now();
		if (expectedArrivalDate.isBefore(today)) {
			return InTransitStatus.OVERDUE;
		}
		if (!expectedArrivalDate.isAfter(today.plusDays(2))) {
			return InTransitStatus.DUE_SOON;
		}
		return InTransitStatus.NORMAL;
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

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record PurchaseOrderLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, Long unitId,
			@NotNull BigDecimal quantity, @NotNull BigDecimal unitPrice, LocalDate expectedArrivalDate, String remark) {
	}

	public record PurchaseOrderRequest(@NotNull Long supplierId, @NotNull LocalDate orderDate,
			LocalDate expectedArrivalDate, String remark, @Valid List<PurchaseOrderLineRequest> lines) {
	}

	public record PurchaseReceiptLineRequest(@NotNull Integer lineNo, @NotNull Long orderLineId, Long materialId,
			Long unitId, @NotNull BigDecimal quantity, String remark,
			@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	public record PurchaseReceiptRequest(@NotNull Long warehouseId, @NotNull LocalDate businessDate, String remark,
			@Valid List<PurchaseReceiptLineRequest> lines) {
	}

	public record PurchaseOrderSummaryResponse(Long id, String orderNo, Long supplierId, String supplierCode,
			String supplierName, LocalDate orderDate, LocalDate expectedArrivalDate, String status, int lineCount,
			BigDecimal totalQuantity, BigDecimal receivedQuantity, BigDecimal remainingQuantity,
			BigDecimal inTransitQuantity, String inTransitStatus, String inTransitStatusName, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt) {
	}

	public record PurchaseOrderLineResponse(Long id, Integer lineNo, Long materialId, String materialCode,
			String materialName, String materialSpec, Long unitId, String unitName, BigDecimal quantity,
			BigDecimal receivedQuantity, BigDecimal remainingQuantity, BigDecimal inTransitQuantity,
			String inTransitStatus, String inTransitStatusName, BigDecimal unitPrice, LocalDate expectedArrivalDate,
			String remark) {
	}

	public record PurchaseOrderDetailResponse(Long id, String orderNo, Long supplierId, String supplierCode,
			String supplierName, LocalDate orderDate, LocalDate expectedArrivalDate, String status, int lineCount,
			BigDecimal totalQuantity, BigDecimal receivedQuantity, BigDecimal remainingQuantity,
			BigDecimal inTransitQuantity, String inTransitStatus, String inTransitStatusName, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt, List<PurchaseOrderLineResponse> lines, List<PurchaseReceiptSummaryResponse> receipts) {
	}

	public record PurchaseReceiptSummaryResponse(Long id, String receiptNo, Long orderId, String orderNo,
			Long supplierId, String supplierName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, int lineCount, BigDecimal totalQuantity, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt) {
	}

	public record PurchaseReceiptLineResponse(Long id, Integer lineNo, Long orderLineId, Long materialId,
			String materialCode, String materialName, String trackingMethod, String trackingMethodName, Long unitId,
			String unitName, BigDecimal orderedQuantity, BigDecimal receivedQuantityBefore,
			BigDecimal remainingQuantityBefore, BigDecimal quantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, BigDecimal inTransitQuantity, String inTransitStatus,
			String inTransitStatusName, String remark,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations) {
	}

	public record PurchaseReceiptInventoryMovementResponse(Long id, String movementNo, String movementType,
			String direction, String warehouseName, String materialCode, String materialName, BigDecimal quantity,
			BigDecimal beforeQuantity, BigDecimal afterQuantity, LocalDate businessDate, String operatorName,
			OffsetDateTime occurredAt, Long batchId, String batchNo, Long serialId, String serialNo) {
	}

	public record PurchaseReceiptDetailResponse(Long id, String receiptNo, Long orderId, String orderNo,
			Long supplierId, String supplierName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, int lineCount, BigDecimal totalQuantity, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			List<PurchaseReceiptLineResponse> lines, PurchaseOrderSummaryResponse orderSummary,
			List<PurchaseReceiptInventoryMovementResponse> inventoryMovements) {
	}

	private record ValidatedOrder(SupplierRef supplier, LocalDate orderDate, LocalDate expectedArrivalDate,
			String remark, List<ValidatedOrderLine> lines) {
	}

	private record ValidatedOrderLine(Integer lineNo, Long materialId, Long unitId, BigDecimal quantity,
			BigDecimal unitPrice, LocalDate expectedArrivalDate, String remark) {
	}

	private record ValidatedReceipt(Long warehouseId, LocalDate businessDate, String remark,
			List<ValidatedReceiptLine> lines) {
	}

	private record ValidatedReceiptLine(Integer lineNo, Long orderLineId, Long materialId, Long unitId,
			BigDecimal orderedQuantity, BigDecimal receivedQuantityBefore, BigDecimal remainingQuantityBefore,
			BigDecimal quantity, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	private record OrderRow(Long id, String orderNo, Long supplierId, LocalDate orderDate,
			LocalDate expectedArrivalDate, PurchaseOrderStatus status, String remark) {
	}

	private record OrderLineRow(Long id, Long orderId, Integer lineNo, Long materialId, Long unitId,
			BigDecimal quantity, BigDecimal receivedQuantity, BigDecimal unitPrice, LocalDate expectedArrivalDate,
			String remark) {
	}

	private record ReceiptRow(Long id, String receiptNo, Long orderId, Long supplierId, Long warehouseId,
			LocalDate businessDate, PurchaseReceiptStatus status, String remark) {
	}

	private record ReceiptLineRow(Long id, Long receiptId, Integer lineNo, Long orderLineId, Long materialId,
			Long unitId, BigDecimal orderedQuantity, BigDecimal receivedQuantityBefore,
			BigDecimal remainingQuantityBefore, BigDecimal quantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, String remark) {
	}

	private record SupplierRef(Long id, String code, String name, String status) {
	}

	private record MaterialRef(Long id, String code, String name, Long unitId, String sourceType, String status) {
	}

	private record QuantityTotals(BigDecimal totalQuantity, BigDecimal receivedQuantity) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private enum InTransitStatus {

		NOT_COUNTED("不计在途"),

		NORMAL("正常"),

		DUE_SOON("临近"),

		OVERDUE("逾期");

		private final String displayName;

		InTransitStatus(String displayName) {
			this.displayName = displayName;
		}

		String code() {
			return name();
		}

		String displayName() {
			return this.displayName;
		}

	}

	private record QueryParts(String where, List<Object> args) {
	}

}
