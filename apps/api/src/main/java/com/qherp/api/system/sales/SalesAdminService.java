package com.qherp.api.system.sales;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryAvailabilityService;
import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.inventory.InventoryReservationType;
import com.qherp.api.system.inventory.InventoryTrackingMethod;
import com.qherp.api.system.inventory.InventoryTrackingService;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
import com.qherp.api.system.salesproject.SalesOrderProjectLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class SalesAdminService {

	private static final String ORDER_TARGET = "SALES_ORDER";

	private static final String SHIPMENT_TARGET = "SALES_SHIPMENT";

	private static final String SHIPMENT_SOURCE_TYPE = "SALES_SHIPMENT";

	private static final Set<String> SELLABLE_MATERIAL_TYPES = Set.of("FINISHED_GOOD", "SEMI_FINISHED");

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final String QUALIFIED_BALANCE_NOT_ENOUGH = "QUALIFIED_BALANCE_NOT_ENOUGH";

	private static final String QUALIFIED_BALANCE_NOT_ENOUGH_MESSAGE = "合格可用库存不足";

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_NO_ATTEMPTS = 3;

	private static final AtomicInteger ORDER_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger SHIPMENT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final InventoryPostingService inventoryPostingService;

	private final InventoryAvailabilityService inventoryAvailabilityService;

	private final InventoryTrackingService inventoryTrackingService;

	private final BusinessPeriodGuard businessPeriodGuard;

	private final SalesOrderProjectLinkService salesOrderProjectLinkService;

	private final TransactionTemplate creditLogTransactionTemplate;

	public SalesAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService, InventoryAvailabilityService inventoryAvailabilityService,
			InventoryTrackingService inventoryTrackingService, BusinessPeriodGuard businessPeriodGuard,
			SalesOrderProjectLinkService salesOrderProjectLinkService,
			PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
		this.inventoryAvailabilityService = inventoryAvailabilityService;
		this.inventoryTrackingService = inventoryTrackingService;
		this.businessPeriodGuard = businessPeriodGuard;
		this.salesOrderProjectLinkService = salesOrderProjectLinkService;
		this.creditLogTransactionTemplate = new TransactionTemplate(transactionManager);
		this.creditLogTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesOrderSummaryResponse> orders(String keyword, Long customerId, String status,
			LocalDate dateFrom, LocalDate dateTo, LocalDate expectedDateFrom, LocalDate expectedDateTo, Long projectId,
			Long contractId, Boolean projectLinked, int page, int pageSize) {
		QueryParts queryParts = orderQueryParts(keyword, customerId, status, dateFrom, dateTo, expectedDateFrom,
				expectedDateTo, projectId, contractId, projectLinked);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<SalesOrderSummaryResponse> items = this.jdbcTemplate.query("""
				select o.id, o.order_no, o.customer_id, c.code as customer_code, c.name as customer_name,
				       o.order_date, o.expected_ship_date, o.status,
				       (select count(*) from sal_sales_order_line l where l.order_id = o.id) as line_count,
				       coalesce((select sum(l.quantity) from sal_sales_order_line l where l.order_id = o.id), 0) as total_quantity,
				       coalesce((select sum(l.shipped_quantity) from sal_sales_order_line l where l.order_id = o.id), 0) as shipped_quantity,
				       coalesce((select sum(l.quantity - l.shipped_quantity) from sal_sales_order_line l where l.order_id = o.id), 0) as remaining_quantity,
				       o.remark, o.created_by, o.created_at, o.updated_at, o.confirmed_by, o.confirmed_at,
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at, o.version,
				       o.project_id, o.contract_id, p.project_no, p.name as project_name,
				       pc.contract_no, pc.external_contract_no, o.currency,
				       coalesce((select sum(l.tax_excluded_amount) from sal_sales_order_line l where l.order_id = o.id),
					       o.tax_excluded_amount, 0) as tax_excluded_amount,
				       coalesce((select sum(l.tax_amount) from sal_sales_order_line l where l.order_id = o.id),
					       o.tax_amount, 0) as tax_amount,
				       coalesce((select sum(l.tax_included_amount) from sal_sales_order_line l where l.order_id = o.id),
					       o.tax_included_amount, 0) as tax_included_amount,
				       o.source_quote_id, o.source_quote_no, o.source_quote_version
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				left join sal_project p on p.id = o.project_id
				left join sal_project_contract pc on pc.id = o.contract_id
				%s
				order by o.updated_at desc, o.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapOrderSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public SalesOrderDetailResponse order(Long id) {
		SalesOrderSummaryResponse summary = orderSummary(id).orElseThrow(this::orderNotFound);
		return new SalesOrderDetailResponse(summary.id(), summary.orderNo(), summary.customerId(),
				summary.customerCode(), summary.customerName(), summary.orderDate(), summary.expectedShipDate(),
				summary.status(), summary.lineCount(), summary.totalQuantity(), summary.shippedQuantity(),
				summary.remainingQuantity(), summary.remark(), summary.createdByName(), summary.createdAt(),
				summary.updatedAt(), summary.confirmedByName(), summary.confirmedAt(), summary.cancelledByName(),
				summary.cancelledAt(), summary.closedByName(), summary.closedAt(), summary.version(),
				summary.projectId(), summary.projectNo(), summary.projectName(), summary.contractId(),
				summary.contractNo(), summary.externalContractNo(), summary.currency(), summary.taxExcludedAmount(),
				summary.taxAmount(), summary.taxIncludedAmount(), summary.sourceQuoteId(), summary.sourceQuoteNo(),
				summary.sourceQuoteVersion(), summary.allowedActions(), summary.actionDisabledReason(),
				orderLines(id), orderShipments(id));
	}

	@Transactional
	public SalesOrderDetailResponse createOrder(SalesOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedOrder order = validateOrderRequest(request);
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CREATE, "SALES_ORDER", null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertOrderWithRetry(order, operator.username(), now);
			insertOrderLines(created.id(), order.lines(), now);
			refreshOrderTaxTotals(created.id());
			insertDefaultDeliveryPlans(created.id(), operator.username(), now);
			this.auditService.record(operator, "SALES_ORDER_CREATE", ORDER_TARGET, created.id(),
					created.documentNo(), servletRequest);
			this.salesOrderProjectLinkService.recordProjectLinkAudit(operator, created.documentNo(), null,
					order.projectLink(), servletRequest);
			return order(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateSalesException(exception);
		}
	}

	@Transactional
	public SalesOrderDetailResponse updateOrder(Long id, SalesOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		OrderRow snapshot = orderRow(id).orElseThrow(this::orderNotFound);
		this.salesOrderProjectLinkService.lockOrderLinkTargets(snapshot.projectId(), snapshot.contractId(),
				request == null ? null : request.projectId(), request == null ? null : request.contractId());
		OrderRow current = lockOrder(id).orElseThrow(this::orderNotFound);
		if (current.status() != SalesOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		if (request != null && request.version() != null && request.version() != current.version()) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CONCURRENT_MODIFICATION);
		}
		List<OrderLineRow> currentLines = orderLineRowsForValidation(id);
		ValidatedOrder order = validateOrderRequest(request, currentLines);
		SalesOrderProjectLinkService.ProjectLink oldLink = this.salesOrderProjectLinkService
			.findLink(current.projectId(), current.contractId());
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.UPDATE, "SALES_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update sal_sales_order
					set customer_id = ?, order_date = ?, expected_ship_date = ?, remark = ?,
					    project_id = ?, contract_id = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", order.customer().id(), order.orderDate(), order.expectedShipDate(),
					blankToNull(order.remark()), order.projectLink() == null ? null : order.projectLink().projectId(),
					order.projectLink() == null ? null : order.projectLink().contractId(), operator.username(), now, id);
			this.jdbcTemplate.update("delete from sal_sales_order_line where order_id = ?", id);
			insertOrderLines(id, order.lines(), now);
			refreshOrderTaxTotals(id);
			insertDefaultDeliveryPlans(id, operator.username(), now);
			this.auditService.record(operator, "SALES_ORDER_UPDATE", ORDER_TARGET, id, current.orderNo(),
					servletRequest);
			this.salesOrderProjectLinkService.recordProjectLinkAudit(operator, current.orderNo(), oldLink,
					order.projectLink(), servletRequest);
			return order(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateSalesException(exception);
		}
	}

	@Transactional
	public SalesOrderDetailResponse confirmOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = orderRow(id).orElseThrow(this::orderNotFound);
		return confirmOrder(id,
				new VersionedActionRequest(order.version(), "内部兼容确认",
						"internal-sales-order-confirm-" + id + "-" + order.version()),
				operator, servletRequest);
	}

	@Transactional
	public SalesOrderDetailResponse confirmOrder(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		return idempotentOrderAction("CONFIRM", id, actionRequest, operator,
				() -> confirmOrderInternal(id, actionRequest.version(), operator, servletRequest));
	}

	private SalesOrderDetailResponse confirmOrderInternal(Long id, Long expectedVersion, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return confirmOrderInternal(id, expectedVersion, null, false, operator, servletRequest);
	}

	@Transactional
	public SalesOrderDetailResponse confirmOrderFromCreditOverride(Long id, Long expectedVersion,
			Long approvalInstanceId, CurrentUser operator, HttpServletRequest servletRequest) {
		return confirmOrderInternal(id, expectedVersion, approvalInstanceId, true, operator, servletRequest);
	}

	private SalesOrderDetailResponse confirmOrderInternal(Long id, Long expectedVersion, Long approvalInstanceId,
			boolean creditOverridden, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow snapshot = orderRow(id).orElseThrow(this::orderNotFound);
		this.salesOrderProjectLinkService.lockOrderLinkTargets(snapshot.projectId(), snapshot.contractId(), null, null);
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(order.version(), expectedVersion);
		if (order.status() != SalesOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CONFIRM, "SALES_ORDER", id);
		validateOrderForConfirmation(order);
		this.salesOrderProjectLinkService.validateForConfirm(order.customerId(), order.projectId(), order.contractId());
		OffsetDateTime now = OffsetDateTime.now();
		refreshOrderTaxTotals(id);
		enforceCreditLimit(order, creditOverridden, operator);
		reserveSalesOrder(order, operator, servletRequest);
		this.jdbcTemplate.update("""
				update sal_sales_order
				set status = ?, confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?,
				    credit_override_approval_instance_id = coalesce(?, credit_override_approval_instance_id),
				    version = version + 1
				where id = ?
				""", SalesOrderStatus.CONFIRMED.name(), operator.username(), now, operator.username(), now,
				approvalInstanceId, id);
		writeOrderSnapshot(id, now);
		this.auditService.record(operator, "SALES_ORDER_CONFIRM", ORDER_TARGET, id, order.orderNo(), servletRequest);
		return order(id);
	}

	@Transactional
	public SalesOrderDetailResponse cancelOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		return cancelOrder(id,
				new VersionedActionRequest(order.version(), "内部兼容取消",
						"internal-sales-order-cancel-" + id + "-" + order.version()),
				operator, servletRequest);
	}

	@Transactional
	public SalesOrderDetailResponse cancelOrder(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		if (!hasText(actionRequest.reason()) || actionRequest.reason().length() > 200) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return idempotentOrderAction("CANCEL", id, actionRequest, operator,
				() -> cancelOrderInternal(id, actionRequest.version(), actionRequest.reason(), operator,
						servletRequest));
	}

	private SalesOrderDetailResponse cancelOrderInternal(Long id, Long expectedVersion, String reason,
			CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(order.version(), expectedVersion);
		if (order.status() != SalesOrderStatus.DRAFT
				&& !(order.status() == SalesOrderStatus.CONFIRMED && !hasPostedShipments(id))) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CANCEL, "SALES_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_sales_order
				set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", SalesOrderStatus.CANCELLED.name(), operator.username(), now, operator.username(), now, id);
		this.inventoryAvailabilityService.releaseBySource(InventoryReservationType.RESERVATION,
				InventoryAvailabilityService.SALES_ORDER_SOURCE, id, operator, servletRequest);
		this.auditService.record(operator, "SALES_ORDER_CANCEL", ORDER_TARGET, id, order.orderNo(), servletRequest);
		return order(id);
	}

	@Transactional
	public SalesOrderDetailResponse closeOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		return closeOrder(id,
				new VersionedActionRequest(order.version(), "内部兼容关闭",
						"internal-sales-order-close-" + id + "-" + order.version()),
				operator, servletRequest);
	}

	@Transactional
	public SalesOrderDetailResponse closeOrder(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		return idempotentOrderAction("CLOSE", id, actionRequest, operator,
				() -> closeOrderInternal(id, actionRequest.version(), actionRequest.reason(), false, operator,
						servletRequest));
	}

	@Transactional
	public SalesOrderDetailResponse closeOrderFromShortCloseApproval(Long id, Long expectedVersion,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return closeOrderInternal(id, expectedVersion, "短交审批通过", true, operator, servletRequest);
	}

	private SalesOrderDetailResponse closeOrderInternal(Long id, Long expectedVersion, String reason,
			boolean shortCloseApproved, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(order.version(), expectedVersion);
		if (order.status() != SalesOrderStatus.CONFIRMED && order.status() != SalesOrderStatus.PARTIALLY_SHIPPED
				&& order.status() != SalesOrderStatus.SHIPPED) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		QuantityTotals totals = orderQuantityTotals(id);
		boolean partiallyDeliveredWithOpenQuantity = totals.shippedQuantity().compareTo(ZERO) > 0
				&& totals.shippedQuantity().compareTo(totals.totalQuantity()) < 0;
		if (partiallyDeliveredWithOpenQuantity && !shortCloseApproved) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_SHORT_CLOSE_APPROVAL_REQUIRED);
		}
		if (!shortCloseApproved && hasOpenDeliveryPlans(id)) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CLOSE_BLOCKED);
		}
		if (!shortCloseApproved && (hasDraftShipments(id) || hasPendingOrderChanges(id)
				|| hasPendingOrderApproval(id))) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CLOSE_BLOCKED);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CLOSE, "SALES_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		closeOpenDeliveryPlans(id, reason, operator.username(), now);
		this.jdbcTemplate.update("""
				update sal_sales_order
				set status = ?, close_reason = ?, closed_by = ?, closed_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", SalesOrderStatus.CLOSED.name(), blankToNull(reason), operator.username(), now,
				operator.username(), now, id);
		this.inventoryAvailabilityService.releaseBySource(InventoryReservationType.RESERVATION,
				InventoryAvailabilityService.SALES_ORDER_SOURCE, id, operator, servletRequest);
		this.auditService.record(operator, shortCloseApproved ? "SALES_ORDER_SHORT_CLOSE" : "SALES_ORDER_CLOSE",
				ORDER_TARGET, id, order.orderNo(), servletRequest);
		return order(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesShipmentSummaryResponse> shipments(String keyword, Long customerId, Long warehouseId,
			String status, LocalDate dateFrom, LocalDate dateTo, Long orderId, int page, int pageSize) {
		QueryParts queryParts = shipmentQueryParts(keyword, customerId, warehouseId, status, dateFrom, dateTo, orderId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = paginationArgs(queryParts, pageSize, page);
		List<SalesShipmentSummaryResponse> items = this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.order_id, o.order_no, sh.customer_id, c.name as customer_name,
				       sh.warehouse_id, w.name as warehouse_name, sh.business_date, sh.status,
				       (select count(*) from sal_sales_shipment_line l where l.shipment_id = sh.id) as line_count,
				       coalesce((select sum(l.quantity) from sal_sales_shipment_line l where l.shipment_id = sh.id), 0) as total_quantity,
				       sh.remark, sh.created_by, sh.created_at, sh.updated_at, sh.posted_by, sh.posted_at,
				       sh.version
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				%s
				order by sh.updated_at desc, sh.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapShipmentSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public SalesShipmentDetailResponse shipment(Long id) {
		SalesShipmentSummaryResponse summary = shipmentSummary(id).orElseThrow(this::shipmentNotFound);
		return new SalesShipmentDetailResponse(summary.id(), summary.shipmentNo(), summary.orderId(),
				summary.orderNo(), summary.customerId(), summary.customerName(), summary.warehouseId(),
				summary.warehouseName(), summary.businessDate(), summary.status(), summary.lineCount(),
				summary.totalQuantity(), summary.remark(), summary.createdByName(), summary.createdAt(),
				summary.updatedAt(), summary.postedByName(), summary.postedAt(), summary.version(), shipmentLines(id),
				orderSummary(summary.orderId()).orElseThrow(this::orderNotFound), shipmentInventoryMovements(id));
	}

	@Transactional
	public SalesShipmentDetailResponse createShipment(Long orderId, SalesShipmentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
		requireShippableOrder(order);
		ValidatedShipment shipment = validateShipmentRequest(order, request);
		this.businessPeriodGuard.assertWritable(shipment.businessDate(), BusinessPeriodOperation.CREATE, SHIPMENT_SOURCE_TYPE, null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertShipmentWithRetry(order, shipment, operator.username(), now);
			insertShipmentLines(created.id(), shipment.lines(), now);
			prepareShipmentAllocations(created.id(), shipment, operator.username());
			this.auditService.record(operator, "SALES_SHIPMENT_CREATE", SHIPMENT_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return shipment(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateSalesException(exception);
		}
	}

	@Transactional
	public SalesShipmentDetailResponse updateShipment(Long id, SalesShipmentRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ShipmentRow current = lockShipment(id).orElseThrow(this::shipmentNotFound);
		if (current.status() != SalesShipmentStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_POSTED_IMMUTABLE);
		}
		OrderRow order = lockOrder(current.orderId()).orElseThrow(this::orderNotFound);
		requireShippableOrder(order);
		ValidatedShipment shipment = validateShipmentRequest(order, request);
		this.businessPeriodGuard.assertWritable(shipment.businessDate(), BusinessPeriodOperation.UPDATE, SHIPMENT_SOURCE_TYPE, id);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update sal_sales_shipment
					set warehouse_id = ?, business_date = ?, remark = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", shipment.warehouseId(), shipment.businessDate(), blankToNull(shipment.remark()),
					operator.username(), now, id);
			this.inventoryTrackingService.deleteDraftDocumentTracking(SHIPMENT_SOURCE_TYPE, id);
			this.jdbcTemplate.update("delete from sal_sales_shipment_line where shipment_id = ?", id);
			insertShipmentLines(id, shipment.lines(), now);
			prepareShipmentAllocations(id, shipment, operator.username());
			this.auditService.record(operator, "SALES_SHIPMENT_UPDATE", SHIPMENT_TARGET, id, current.shipmentNo(),
					servletRequest);
			return shipment(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateSalesException(exception);
		}
	}

	@Transactional
	public SalesShipmentDetailResponse postShipment(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		ShipmentRow shipment = shipmentRow(id).orElseThrow(this::shipmentNotFound);
		return postShipment(id,
				new VersionedActionRequest(shipment.version(), "内部兼容过账",
						"internal-sales-shipment-post-" + id + "-" + shipment.version()),
				operator, servletRequest);
	}

	@Transactional
	public SalesShipmentDetailResponse postShipment(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		return idempotentShipmentAction("POST", id, actionRequest, operator,
				() -> postShipmentInternal(id, actionRequest.version(), actionRequest.reason(), operator,
						servletRequest));
	}

	private SalesShipmentDetailResponse postShipmentInternal(Long id, Long expectedVersion, String reason,
			CurrentUser operator, HttpServletRequest servletRequest) {
		try {
			ShipmentRow shipment = lockShipment(id).orElseThrow(this::shipmentNotFound);
			requireVersion(shipment.version(), expectedVersion);
			if (shipment.status() != SalesShipmentStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.SALES_DUPLICATE_POST);
			}
			this.businessPeriodGuard.assertWritable(shipment.businessDate(), BusinessPeriodOperation.POST, SHIPMENT_SOURCE_TYPE, id);
			OrderRow order = lockOrder(shipment.orderId()).orElseThrow(this::orderNotFound);
			requireShippableOrder(order);
			validateEnabledWarehouse(shipment.warehouseId());
			List<ShipmentLineRow> lines = shipmentLineRows(id);
			if (lines.isEmpty()) {
				throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EMPTY_LINES);
			}
			validateEarlyShipmentReason(shipment, lines, reason);
			OffsetDateTime now = OffsetDateTime.now();
			for (ShipmentLineRow line : lines) {
				postShipmentLine(order.id(), shipment, line, operator, servletRequest, now);
			}
			SalesOrderStatus nextOrderStatus = shippedOrderStatus(order.id());
			this.jdbcTemplate.update("""
					update sal_sales_shipment
					set status = ?, posted_by = ?, posted_at = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", SalesShipmentStatus.POSTED.name(), operator.username(), now, operator.username(), now, id);
			this.jdbcTemplate.update("""
					update sal_sales_order
					set status = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", nextOrderStatus.name(), operator.username(), now, order.id());
			this.auditService.record(operator, "SALES_SHIPMENT_POST", SHIPMENT_TARGET, id, shipment.shipmentNo(),
					servletRequest);
			return shipment(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateSalesException(exception);
		}
	}

	private void validateEarlyShipmentReason(ShipmentRow shipment, List<ShipmentLineRow> lines, String reason) {
		boolean earlyShipment = lines.stream()
			.anyMatch((line) -> line.deliveryPlanPlannedDate() != null
					&& shipment.businessDate().isBefore(line.deliveryPlanPlannedDate()));
		if (earlyShipment && !hasText(reason)) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EARLY_REASON_REQUIRED);
		}
	}

	private void postShipmentLine(Long orderId, ShipmentRow shipment, ShipmentLineRow line, CurrentUser operator,
			HttpServletRequest servletRequest, OffsetDateTime now) {
		OrderLineRow orderLine = lockOrderLine(orderId, line.orderLineId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_SHIPMENT_LINE_SOURCE_INVALID));
		if (!line.materialId().equals(orderLine.materialId()) || !line.unitId().equals(orderLine.unitId())) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_LINE_SOURCE_INVALID);
		}
		validateShipmentOrderLineMasterData(orderLine);
		if (orderLine.reservationWarehouseId() == null
				|| !orderLine.reservationWarehouseId().equals(shipment.warehouseId())) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_RESERVATION_WAREHOUSE_MISMATCH);
		}
		BigDecimal remainingQuantity = orderLine.quantity().subtract(orderLine.shippedQuantity());
		if (line.quantity().compareTo(remainingQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EXCEEDS_ORDER);
		}
		List<InventoryTrackingService.ResolvedTrackingAllocation> allocations = this.inventoryTrackingService
			.resolveStoredOutboundAllocations(SHIPMENT_SOURCE_TYPE, shipment.id(), line.id(), shipment.warehouseId(),
					line.materialId(), line.unitId(), line.quantity(), "trackingAllocations");
		InventoryPostingService.PostingResult posting = null;
		for (InventoryTrackingService.ResolvedTrackingAllocation allocation : allocations) {
			boolean consumedReservation = consumeShipmentReservation(orderLine.id(), allocation, operator,
					servletRequest);
			InventoryPostingService.PostingResult current = this.inventoryPostingService.post(
					new InventoryPostingService.PostingRequest(InventoryMovementType.SALES_SHIPMENT,
							InventoryDirection.OUT, shipment.warehouseId(), line.materialId(), line.unitId(),
							allocation.quantity(), InventoryQualityStatus.QUALIFIED, SHIPMENT_SOURCE_TYPE,
							shipment.id(), line.id(), shipment.businessDate(), "销售出库", line.remark(),
							operator.username(), consumedReservation, allocation.batchId(), allocation.serialId(),
							InventoryAvailabilityService.SALES_ORDER_SOURCE, orderLine.id()));
			this.inventoryTrackingService.attachMovement(allocation.allocationId(), current.movementId());
			this.inventoryTrackingService.markOutboundPosted(allocation, current.movementId(), operator.username());
			if (posting == null) {
				posting = current;
			}
			else {
				posting = new InventoryPostingService.PostingResult(posting.beforeQuantity(), current.afterQuantity(),
						current.movementId());
			}
		}
		if (posting == null) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
		this.jdbcTemplate.update("""
				update sal_sales_shipment_line
				set ordered_quantity = ?, shipped_quantity_before = ?, remaining_quantity_before = ?,
				    before_quantity = ?, after_quantity = ?, updated_at = ?
				where id = ?
				""", orderLine.quantity(), orderLine.shippedQuantity(), remainingQuantity, posting.beforeQuantity(),
				posting.afterQuantity(), now, line.id());
		this.jdbcTemplate.update("""
				update sal_sales_order_line
				set shipped_quantity = shipped_quantity + ?, updated_at = ?, version = version + 1
				where id = ?
				""", line.quantity(), now, orderLine.id());
		this.jdbcTemplate.update("""
				update sal_sales_delivery_plan
				set shipped_quantity = shipped_quantity + ?,
				    status = case
				        when shipped_quantity + ? >= planned_quantity then 'SHIPPED'
				        else 'PARTIALLY_SHIPPED'
				    end,
				    updated_at = ?, version = version + 1
				where id = ?
				""", line.quantity(), line.quantity(), now, line.deliveryPlanId());
	}

	private boolean consumeShipmentReservation(Long orderLineId,
			InventoryTrackingService.ResolvedTrackingAllocation allocation, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (allocation.batchId() != null || allocation.serialId() != null) {
			return this.inventoryAvailabilityService.consumeTrackedBySourceLine(InventoryReservationType.RESERVATION,
					InventoryAvailabilityService.SALES_ORDER_SOURCE, orderLineId, allocation.quantity(),
					allocation.batchId(), allocation.serialId(), operator, servletRequest);
		}
		return this.inventoryAvailabilityService.consumeBySourceLine(InventoryReservationType.RESERVATION,
				InventoryAvailabilityService.SALES_ORDER_SOURCE, orderLineId, allocation.quantity(), operator,
				servletRequest);
	}

	private ValidatedOrder validateOrderRequest(SalesOrderRequest request) {
		return validateOrderRequest(request, List.of());
	}

	private ValidatedOrder validateOrderRequest(SalesOrderRequest request, List<OrderLineRow> existingLines) {
		if (request == null || request.orderDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		CustomerRef customer = validateEnabledCustomer(request.customerId());
		String remark = validateOptionalText(request.remark(), 500);
		SalesOrderProjectLinkService.ProjectLink projectLink = this.salesOrderProjectLinkService
			.validateForDraftSave(customer.id(), request.projectId(), request.contractId());
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_EMPTY_LINES);
		}
		Map<Integer, OrderLineRow> existingLineByLineNo = new HashMap<>();
		for (OrderLineRow existingLine : existingLines) {
			existingLineByLineNo.put(existingLine.lineNo(), existingLine);
		}
		Set<Integer> lineNos = new HashSet<>();
		List<ValidatedOrderLine> lines = new ArrayList<>();
		for (SalesOrderLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validateSellableMaterial(line.materialId());
			Long unitId = validateUnit(line.unitId(), material);
			BigDecimal quantity = validateQuantity(line.quantity());
			BigDecimal unitPrice = validateUnitPrice(line.unitPrice());
			if (line.reservationWarehouseId() != null) {
				validateEnabledWarehouse(line.reservationWarehouseId());
			}
			OrderLineSourceSnapshot source = resolveOrderLineSource(existingLineByLineNo.get(line.lineNo()),
					material.id(), unitId, quantity, unitPrice);
			lines.add(new ValidatedOrderLine(line.lineNo(), material.id(), unitId, quantity, unitPrice,
					line.reservationWarehouseId(), line.expectedShipDate(), validateOptionalText(line.remark(), 500),
					source.sourceQuoteLineId(), source.priceSourceType(), source.sourceNo(), source.currency(),
					source.taxRate(), source.taxExcludedUnitPrice(), source.taxIncludedUnitPrice(),
					source.taxExcludedAmount(), source.taxAmount(), source.taxIncludedAmount()));
		}
		return new ValidatedOrder(customer, request.orderDate(), request.expectedShipDate(), remark, projectLink, lines);
	}

	private OrderLineSourceSnapshot resolveOrderLineSource(OrderLineRow existingLine, Long materialId, Long unitId,
			BigDecimal quantity, BigDecimal unitPrice) {
		if (existingLine != null && "QUOTE".equals(existingLine.priceSourceType())
				&& existingLine.sourceQuoteLineId() != null) {
			if (!existingLine.materialId().equals(materialId) || !existingLine.unitId().equals(unitId)
					|| existingLine.taxExcludedUnitPrice() == null || existingLine.taxIncludedUnitPrice() == null
					|| existingLine.taxRate() == null || !hasText(existingLine.currency())
					|| unitPrice.compareTo(existingLine.taxExcludedUnitPrice()) != 0) {
				throw new BusinessException(ApiErrorCode.SALES_ORDER_CHANGE_SOURCE_IMMUTABLE);
			}
			BigDecimal taxExcludedAmount = money(quantity.multiply(existingLine.taxExcludedUnitPrice()));
			BigDecimal taxIncludedAmount = money(quantity.multiply(existingLine.taxIncludedUnitPrice()));
			BigDecimal taxAmount = taxIncludedAmount.subtract(taxExcludedAmount);
			return new OrderLineSourceSnapshot(existingLine.sourceQuoteLineId(), "QUOTE", existingLine.sourceNo(),
					existingLine.currency(), existingLine.taxRate(), existingLine.taxExcludedUnitPrice(),
					existingLine.taxIncludedUnitPrice(), taxExcludedAmount, taxAmount, taxIncludedAmount);
		}
		BigDecimal lineAmount = money(quantity.multiply(unitPrice));
		return new OrderLineSourceSnapshot(null, "MANUAL", null, "CNY", ZERO, unitPrice, unitPrice, lineAmount,
				ZERO, lineAmount);
	}

	private void validateOrderForConfirmation(OrderRow order) {
		validateEnabledCustomer(order.customerId());
		List<OrderLineRow> lines = orderLineRowsForValidation(order.id());
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		for (OrderLineRow line : lines) {
			if (line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validateSellableMaterial(line.materialId());
			validateUnit(line.unitId(), material);
			validateQuantity(line.quantity());
			validateUnitPrice(line.unitPrice());
			if (line.reservationWarehouseId() == null) {
				throw new BusinessException(ApiErrorCode.SALES_RESERVATION_WAREHOUSE_REQUIRED);
			}
			validateEnabledWarehouse(line.reservationWarehouseId());
		}
	}

	private void reserveSalesOrder(OrderRow order, CurrentUser operator, HttpServletRequest servletRequest) {
		for (OrderLineRow line : orderLineRowsForValidation(order.id())) {
			BigDecimal remainingQuantity = line.quantity().subtract(line.shippedQuantity());
			if (remainingQuantity.compareTo(ZERO) <= 0) {
				continue;
			}
			this.inventoryAvailabilityService.reserveFromWarehouse(
					new InventoryAvailabilityService.ReservationCommand(InventoryReservationType.RESERVATION,
							line.reservationWarehouseId(), line.materialId(), line.unitId(), remainingQuantity,
							InventoryAvailabilityService.SALES_ORDER_SOURCE, order.id(), line.id(), order.orderNo(),
							order.orderDate(), "销售订单确认预留", null, "PUBLIC", null, null,
							InventoryQualityStatus.QUALIFIED, null, null, null),
					operator, servletRequest);
		}
	}

	private void enforceCreditLimit(OrderRow order, boolean creditOverridden, CurrentUser operator) {
		CreditProfile profile = lockCreditProfile(order.customerId());
		BigDecimal usedCredit = customerUsedCreditAmount(order.customerId());
		BigDecimal orderAmount = orderTaxIncludedAmount(order.id());
		if (profile == null) {
			recordCreditCheck(order, "BLOCKED", null, usedCredit, orderAmount, "信用档案缺失", operator);
			if (creditOverridden) {
				return;
			}
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_PROFILE_MISSING);
		}
		if (!"ACTIVE".equals(profile.status()) || profile.frozen()) {
			recordCreditCheck(order, "BLOCKED", profile.creditLimit(), usedCredit, orderAmount, "信用冻结或停用",
					operator);
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_FROZEN);
		}
		if (profile.overdueBlocked()) {
			recordCreditCheck(order, "BLOCKED", profile.creditLimit(), usedCredit, orderAmount, "逾期信用阻断",
					operator);
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_BLOCKED);
		}
		if (creditOverridden) {
			recordCreditCheck(order, "OVERRIDDEN", profile.creditLimit(), usedCredit, orderAmount, "信用例外已批准",
					operator);
			return;
		}
		if (usedCredit.add(orderAmount).compareTo(profile.creditLimit()) > 0) {
			recordCreditCheck(order, "BLOCKED", profile.creditLimit(), usedCredit, orderAmount, "信用额度不足",
					operator);
			throw new BusinessException(ApiErrorCode.SALES_CREDIT_LIMIT_EXCEEDED);
		}
		recordCreditCheck(order, "PASSED", profile.creditLimit(), usedCredit, orderAmount, "信用检查通过", operator);
	}

	private CreditProfile lockCreditProfile(Long customerId) {
		return this.jdbcTemplate.query("""
				select id, customer_id, credit_limit, status, frozen, overdue_blocked
				from sal_customer_credit_profile
				where customer_id = ?
				for update
				""", (rs, rowNum) -> new CreditProfile(rs.getLong("id"), rs.getLong("customer_id"),
				rs.getBigDecimal("credit_limit"), rs.getString("status"), rs.getBoolean("frozen"),
				rs.getBoolean("overdue_blocked")), customerId)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private void recordCreditCheck(OrderRow order, String result, BigDecimal creditLimit, BigDecimal usedCredit,
			BigDecimal newAmount, String reason, CurrentUser operator) {
		this.creditLogTransactionTemplate.executeWithoutResult((status) -> this.jdbcTemplate.update("""
				insert into sal_credit_check_log (
					customer_id, source_type, source_id, source_no, check_result, credit_limit, used_credit,
					new_amount, reason, created_by, created_at
				)
				values (?, 'SALES_ORDER', ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", order.customerId(), order.id(), order.orderNo(), result, creditLimit, usedCredit, newAmount,
				reason, operator.username(), OffsetDateTime.now()));
	}

	private BigDecimal customerOrderCommitmentAmount(Long customerId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(sum((l.quantity - l.shipped_quantity) * l.tax_included_unit_price), 0)
				from sal_sales_order o
				join sal_sales_order_line l on l.order_id = o.id
				where o.customer_id = ?
				and o.status in ('CONFIRMED', 'PARTIALLY_SHIPPED')
				""", BigDecimal.class, customerId);
		return money(value == null ? ZERO : value);
	}

	private BigDecimal customerUsedCreditAmount(Long customerId) {
		return money(customerOrderCommitmentAmount(customerId)
			.add(customerUnsettledShipmentAmount(customerId))
			.add(customerReceivableOutstandingAmount(customerId)));
	}

	private BigDecimal customerUnsettledShipmentAmount(Long customerId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(sl.tax_included_amount), 0)
				from sal_sales_shipment sh
				join sal_sales_shipment_line sl on sl.shipment_id = sh.id
				where sh.customer_id = ?
				and sh.status = 'POSTED'
				and not exists (
					select 1
					from fin_receivable_source rs
					join fin_receivable r on r.id = rs.receivable_id
					where rs.source_type = 'SALES_SHIPMENT'
					and rs.source_line_id = sl.id
					and r.status <> 'CANCELLED'
				)
				""", BigDecimal.class, customerId);
		return money(value == null ? ZERO : value);
	}

	private BigDecimal customerReceivableOutstandingAmount(Long customerId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(unreceived_amount), 0)
				from fin_receivable
				where customer_id = ?
				and status not in ('CANCELLED', 'RECEIVED')
				""", BigDecimal.class, customerId);
		return money(value == null ? ZERO : value);
	}

	private BigDecimal orderTaxIncludedAmount(Long orderId) {
		BigDecimal value = this.jdbcTemplate.queryForObject("""
				select coalesce(tax_included_amount, 0)
				from sal_sales_order
				where id = ?
				""", BigDecimal.class, orderId);
		return money(value == null ? ZERO : value);
	}

	private ValidatedShipment validateShipmentRequest(OrderRow order, SalesShipmentRequest request) {
		if (request == null || request.businessDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateEnabledWarehouse(request.warehouseId());
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> orderLineIds = new HashSet<>();
		List<ValidatedShipmentLine> lines = new ArrayList<>();
		for (SalesShipmentLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (line.orderLineId() == null || !orderLineIds.add(line.orderLineId())) {
				throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_DUPLICATE_LINE);
			}
			OrderLineRow orderLine = orderLine(order.id(), line.orderLineId())
				.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_SHIPMENT_LINE_SOURCE_INVALID));
			if ((line.materialId() != null && !line.materialId().equals(orderLine.materialId()))
					|| (line.unitId() != null && !line.unitId().equals(orderLine.unitId()))) {
				throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_LINE_SOURCE_INVALID);
			}
			validateShipmentOrderLineMasterData(orderLine);
			if (orderLine.reservationWarehouseId() == null
					|| !orderLine.reservationWarehouseId().equals(request.warehouseId())) {
				throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_RESERVATION_WAREHOUSE_MISMATCH);
			}
			BigDecimal quantity = validateQuantity(line.quantity());
			BigDecimal remainingQuantity = orderLine.quantity().subtract(orderLine.shippedQuantity());
			if (quantity.compareTo(remainingQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EXCEEDS_ORDER);
			}
			DeliveryPlanRow deliveryPlan = deliveryPlan(order.id(), orderLine.id(), line.deliveryPlanId())
				.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_SHIPMENT_LINE_SOURCE_INVALID));
			BigDecimal planRemainingQuantity = deliveryPlan.plannedQuantity().subtract(deliveryPlan.shippedQuantity());
			if (quantity.compareTo(planRemainingQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EXCEEDS_ORDER);
			}
			BigDecimal taxExcludedAmount = money(quantity.multiply(orderLine.taxExcludedUnitPrice()));
			BigDecimal taxIncludedAmount = money(quantity.multiply(orderLine.taxIncludedUnitPrice()));
			BigDecimal taxAmount = taxIncludedAmount.subtract(taxExcludedAmount);
			lines.add(new ValidatedShipmentLine(line.lineNo(), orderLine.id(), orderLine.materialId(),
				orderLine.unitId(), orderLine.quantity(), orderLine.shippedQuantity(), remainingQuantity, quantity,
				deliveryPlan.id(), orderLine.priceSourceType(), orderLine.sourceNo(), orderLine.currency(),
				orderLine.taxRate(), orderLine.taxExcludedUnitPrice(), orderLine.taxIncludedUnitPrice(),
				taxExcludedAmount, taxAmount, taxIncludedAmount, validateOptionalText(line.remark(), 500),
				line.trackingAllocations() == null ? List.of() : line.trackingAllocations()));
		}
		return new ValidatedShipment(request.warehouseId(), request.businessDate(), validateOptionalText(request.remark(),
				500), lines);
	}

	private void validateShipmentOrderLineMasterData(OrderLineRow orderLine) {
		MaterialRef material = validateSellableMaterial(orderLine.materialId());
		validateUnit(orderLine.unitId(), material);
	}

	private CustomerRef validateEnabledCustomer(Long customerId) {
		if (customerId == null) {
			throw new BusinessException(ApiErrorCode.SALES_CUSTOMER_INVALID);
		}
		CustomerRef customer = customerRef(customerId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_CUSTOMER_INVALID));
		if (!"ENABLED".equals(customer.status())) {
			throw new BusinessException(ApiErrorCode.SALES_CUSTOMER_INVALID);
		}
		return customer;
	}

	private void validateEnabledWarehouse(Long warehouseId) {
		if (warehouseId == null) {
			throw new BusinessException(ApiErrorCode.SALES_WAREHOUSE_INVALID);
		}
		String status = this.jdbcTemplate.query("select status from mst_warehouse where id = ?",
				(rs, rowNum) -> rs.getString("status"), warehouseId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status)) {
			throw new BusinessException(ApiErrorCode.SALES_WAREHOUSE_INVALID);
		}
	}

	private MaterialRef validateSellableMaterial(Long materialId) {
		if (materialId == null) {
			throw new BusinessException(ApiErrorCode.SALES_MATERIAL_INVALID);
		}
		MaterialRef material = materialRef(materialId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_MATERIAL_INVALID));
		if (!"ENABLED".equals(material.status())) {
			throw new BusinessException(ApiErrorCode.SALES_MATERIAL_INVALID);
		}
		if (!SELLABLE_MATERIAL_TYPES.contains(material.materialType())) {
			throw new BusinessException(ApiErrorCode.SALES_MATERIAL_NOT_SELLABLE);
		}
		return material;
	}

	private Long validateUnit(Long requestedUnitId, MaterialRef material) {
		Long unitId = requestedUnitId == null ? material.unitId() : requestedUnitId;
		String status = this.jdbcTemplate.query("select status from mst_unit where id = ?",
				(rs, rowNum) -> rs.getString("status"), unitId).stream().findFirst().orElse(null);
		if (!"ENABLED".equals(status) || !unitId.equals(material.unitId())) {
			throw new BusinessException(ApiErrorCode.SALES_UNIT_INVALID);
		}
		return unitId;
	}

	private BigDecimal validateQuantity(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) <= 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.SALES_QUANTITY_INVALID);
		}
		return value;
	}

	private BigDecimal validateUnitPrice(BigDecimal value) {
		if (value == null || value.compareTo(ZERO) < 0 || value.scale() > 6 || integerDigits(value) > 12L) {
			throw new BusinessException(ApiErrorCode.SALES_UNIT_PRICE_INVALID);
		}
		return value;
	}

	private String validateOptionalText(String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return value;
	}

	private void requireShippableOrder(OrderRow order) {
		if (order.status() != SalesOrderStatus.CONFIRMED && order.status() != SalesOrderStatus.PARTIALLY_SHIPPED) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
	}

	private List<String> orderAllowedActions(String status) {
		if (SalesOrderStatus.DRAFT.name().equals(status)) {
			return List.of("UPDATE", "CONFIRM", "CANCEL");
		}
		if (SalesOrderStatus.CONFIRMED.name().equals(status)
				|| SalesOrderStatus.PARTIALLY_SHIPPED.name().equals(status)) {
			return List.of("CREATE_CHANGE", "CREATE_SHIPMENT", "UPDATE_DELIVERY_PLAN", "SUBMIT_SHORT_CLOSE");
		}
		return List.of();
	}

	private String orderActionDisabledReason(String status) {
		return orderAllowedActions(status).isEmpty() ? ApiErrorCode.SALES_ORDER_STATUS_INVALID.message() : null;
	}

	private CreatedDocument insertOrderWithRetry(ValidatedOrder order, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String orderNo = nextNo("SO", ORDER_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into sal_sales_order (
							order_no, customer_id, order_date, expected_ship_date, status, remark, project_id, contract_id,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, orderNo, order.customer().id(), order.orderDate(), order.expectedShipDate(),
						SalesOrderStatus.DRAFT.name(), blankToNull(order.remark()),
						order.projectLink() == null ? null : order.projectLink().projectId(),
						order.projectLink() == null ? null : order.projectLink().contractId(), operatorName, now,
						operatorName, now);
				return new CreatedDocument(id, orderNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_sal_sales_order_no") && attempt < MAX_NO_ATTEMPTS) {
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
					insert into sal_sales_order_line (
						order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
						expected_ship_date, reservation_warehouse_id, remark, source_quote_line_id,
						price_source_type, source_no, currency, tax_rate,
						tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
						tax_included_amount, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", orderId, line.lineNo(), line.materialId(), line.unitId(), line.quantity(), ZERO,
					line.unitPrice(), line.expectedShipDate(), line.reservationWarehouseId(),
					blankToNull(line.remark()), line.sourceQuoteLineId(), line.priceSourceType(), line.sourceNo(),
					line.currency(), line.taxRate(), line.taxExcludedUnitPrice(), line.taxIncludedUnitPrice(),
					line.taxExcludedAmount(), line.taxAmount(), line.taxIncludedAmount(), now, now);
		}
	}

	private void insertDefaultDeliveryPlans(Long orderId, String operatorName, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into sal_sales_delivery_plan (
					order_id, order_line_id, line_no, planned_date, planned_quantity, shipped_quantity,
					status, created_by, created_at, updated_by, updated_at
				)
				select l.order_id, l.id, l.line_no, coalesce(l.expected_ship_date, o.expected_ship_date, o.order_date),
				       l.quantity, 0, 'PLANNED', ?, ?, ?, ?
				from sal_sales_order_line l
				join sal_sales_order o on o.id = l.order_id
				where l.order_id = ?
				on conflict (order_line_id, line_no) do nothing
				""", operatorName, now, operatorName, now, orderId);
	}

	private CreatedDocument insertShipmentWithRetry(OrderRow order, ValidatedShipment shipment, String operatorName,
			OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String shipmentNo = nextNo("SS", SHIPMENT_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into sal_sales_shipment (
							shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, shipmentNo, order.id(), order.customerId(), shipment.warehouseId(),
						shipment.businessDate(), SalesShipmentStatus.DRAFT.name(), blankToNull(shipment.remark()),
						operatorName, now, operatorName, now);
				return new CreatedDocument(id, shipmentNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_sal_sales_shipment_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertShipmentLines(Long shipmentId, List<ValidatedShipmentLine> lines, OffsetDateTime now) {
		for (ValidatedShipmentLine line : lines) {
			this.jdbcTemplate.update("""
					insert into sal_sales_shipment_line (
						shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
						shipped_quantity_before, remaining_quantity_before, quantity, delivery_plan_id,
						price_source_type, source_no, currency, tax_rate, tax_excluded_unit_price,
						tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount,
						remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", shipmentId, line.lineNo(), line.orderLineId(), line.materialId(), line.unitId(),
					line.orderedQuantity(), line.shippedQuantityBefore(), line.remainingQuantityBefore(),
					line.quantity(), line.deliveryPlanId(), line.priceSourceType(), line.sourceNo(), line.currency(),
					line.taxRate(), line.taxExcludedUnitPrice(), line.taxIncludedUnitPrice(),
					line.taxExcludedAmount(), line.taxAmount(), line.taxIncludedAmount(), blankToNull(line.remark()),
					now, now);
		}
	}

	private void prepareShipmentAllocations(Long shipmentId, ValidatedShipment shipment, String operatorName) {
		List<ShipmentLineRow> rows = shipmentLineRows(shipmentId);
		for (int i = 0; i < shipment.lines().size(); i++) {
			ValidatedShipmentLine line = shipment.lines().get(i);
			ShipmentLineRow row = rows.stream()
				.filter((candidate) -> candidate.orderLineId().equals(line.orderLineId()))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
			this.inventoryTrackingService.prepareOutboundAllocations(SHIPMENT_SOURCE_TYPE, shipmentId, row.id(),
					shipment.warehouseId(), line.materialId(), line.unitId(), line.quantity(),
					line.trackingAllocations(), operatorName, "lines[" + i + "].trackingAllocations");
		}
	}

	private QueryParts orderQueryParts(String keyword, Long customerId, String status, LocalDate dateFrom,
			LocalDate dateTo, LocalDate expectedDateFrom, LocalDate expectedDateTo, Long projectId, Long contractId,
			Boolean projectLinked) {
		if (projectLinked != null && (projectId != null || contractId != null)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(o.order_no ilike ? or c.code ilike ? or c.name ilike ? or o.remark ilike ?
					or exists (
						select 1
						from sal_sales_order_line kl
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
		if (customerId != null) {
			conditions.add("o.customer_id = ?");
			args.add(customerId);
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
			conditions.add("o.expected_ship_date >= ?");
			args.add(expectedDateFrom);
		}
		if (expectedDateTo != null) {
			conditions.add("o.expected_ship_date <= ?");
			args.add(expectedDateTo);
		}
		if (projectId != null) {
			conditions.add("o.project_id = ?");
			args.add(projectId);
		}
		if (contractId != null) {
			conditions.add("o.contract_id = ?");
			args.add(contractId);
		}
		if (projectLinked != null) {
			conditions.add(projectLinked ? "o.project_id is not null" : "o.project_id is null");
		}
		return where(conditions, args);
	}

	private QueryParts shipmentQueryParts(String keyword, Long customerId, Long warehouseId, String status,
			LocalDate dateFrom, LocalDate dateTo, Long orderId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("""
					(sh.shipment_no ilike ? or o.order_no ilike ? or c.code ilike ? or c.name ilike ? or sh.remark ilike ?
					or exists (
						select 1
						from sal_sales_shipment_line kl
						join mst_material km on km.id = kl.material_id
						where kl.shipment_id = sh.id
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
		if (customerId != null) {
			conditions.add("sh.customer_id = ?");
			args.add(customerId);
		}
		if (warehouseId != null) {
			conditions.add("sh.warehouse_id = ?");
			args.add(warehouseId);
		}
		if (hasText(status)) {
			conditions.add("sh.status = ?");
			args.add(parseShipmentStatus(status).name());
		}
		if (dateFrom != null) {
			conditions.add("sh.business_date >= ?");
			args.add(dateFrom);
		}
		if (dateTo != null) {
			conditions.add("sh.business_date <= ?");
			args.add(dateTo);
		}
		if (orderId != null) {
			conditions.add("sh.order_id = ?");
			args.add(orderId);
		}
		return where(conditions, args);
	}

	private Optional<SalesOrderSummaryResponse> orderSummary(Long id) {
		return this.jdbcTemplate.query("""
				select o.id, o.order_no, o.customer_id, c.code as customer_code, c.name as customer_name,
				       o.order_date, o.expected_ship_date, o.status,
				       (select count(*) from sal_sales_order_line l where l.order_id = o.id) as line_count,
				       coalesce((select sum(l.quantity) from sal_sales_order_line l where l.order_id = o.id), 0) as total_quantity,
				       coalesce((select sum(l.shipped_quantity) from sal_sales_order_line l where l.order_id = o.id), 0) as shipped_quantity,
				       coalesce((select sum(l.quantity - l.shipped_quantity) from sal_sales_order_line l where l.order_id = o.id), 0) as remaining_quantity,
				       o.remark, o.created_by, o.created_at, o.updated_at, o.confirmed_by, o.confirmed_at,
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at, o.version,
				       o.project_id, o.contract_id, p.project_no, p.name as project_name,
				       pc.contract_no, pc.external_contract_no, o.currency,
				       coalesce((select sum(l.tax_excluded_amount) from sal_sales_order_line l where l.order_id = o.id),
					       o.tax_excluded_amount, 0) as tax_excluded_amount,
				       coalesce((select sum(l.tax_amount) from sal_sales_order_line l where l.order_id = o.id),
					       o.tax_amount, 0) as tax_amount,
				       coalesce((select sum(l.tax_included_amount) from sal_sales_order_line l where l.order_id = o.id),
					       o.tax_included_amount, 0) as tax_included_amount,
				       o.source_quote_id, o.source_quote_no, o.source_quote_version
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				left join sal_project p on p.id = o.project_id
				left join sal_project_contract pc on pc.id = o.contract_id
				where o.id = ?
				""", this::mapOrderSummary, id).stream().findFirst();
	}

	private Optional<SalesShipmentSummaryResponse> shipmentSummary(Long id) {
		return this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.order_id, o.order_no, sh.customer_id, c.name as customer_name,
				       sh.warehouse_id, w.name as warehouse_name, sh.business_date, sh.status,
				       (select count(*) from sal_sales_shipment_line l where l.shipment_id = sh.id) as line_count,
				       coalesce((select sum(l.quantity) from sal_sales_shipment_line l where l.shipment_id = sh.id), 0) as total_quantity,
				       sh.remark, sh.created_by, sh.created_at, sh.updated_at, sh.posted_by, sh.posted_at,
				       sh.version
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				where sh.id = ?
				""", this::mapShipmentSummary, id).stream().findFirst();
	}

	private Optional<OrderRow> lockOrder(Long id) {
		return this.jdbcTemplate.query("""
				select id, order_no, customer_id, order_date, expected_ship_date, status, remark, version,
				       project_id, contract_id
				from sal_sales_order
				where id = ?
				for update
				""", this::mapOrderRow, id).stream().findFirst();
	}

	private Optional<OrderRow> orderRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, order_no, customer_id, order_date, expected_ship_date, status, remark, version,
				       project_id, contract_id
				from sal_sales_order
				where id = ?
				""", this::mapOrderRow, id).stream().findFirst();
	}

	private Optional<ShipmentRow> lockShipment(Long id) {
		return this.jdbcTemplate.query("""
				select id, shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark, version
				from sal_sales_shipment
				where id = ?
				for update
				""", this::mapShipmentRow, id).stream().findFirst();
	}

	private Optional<ShipmentRow> shipmentRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark, version
				from sal_sales_shipment
				where id = ?
				""", this::mapShipmentRow, id).stream().findFirst();
	}

	private Optional<OrderLineRow> orderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
				       expected_ship_date, reservation_warehouse_id, remark, source_quote_line_id,
				       price_source_type, source_no, currency, tax_rate, tax_excluded_unit_price,
				       tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount
				from sal_sales_order_line
				where order_id = ?
				and id = ?
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private Optional<OrderLineRow> lockOrderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
				       expected_ship_date, reservation_warehouse_id, remark, source_quote_line_id,
				       price_source_type, source_no, currency, tax_rate, tax_excluded_unit_price,
				       tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount
				from sal_sales_order_line
				where order_id = ?
				and id = ?
				for update
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private Optional<DeliveryPlanRow> deliveryPlan(Long orderId, Long orderLineId, Long deliveryPlanId) {
		if (deliveryPlanId == null) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select id, order_id, order_line_id, planned_quantity, shipped_quantity, status
				from sal_sales_delivery_plan
				where id = ?
				and order_id = ?
				and order_line_id = ?
				and status in ('PLANNED', 'PARTIALLY_SHIPPED')
				""", (rs, rowNum) -> new DeliveryPlanRow(rs.getLong("id"), rs.getLong("order_id"),
				rs.getLong("order_line_id"), rs.getBigDecimal("planned_quantity"),
				rs.getBigDecimal("shipped_quantity"), rs.getString("status")), deliveryPlanId, orderId, orderLineId)
			.stream()
			.findFirst();
	}

	private List<OrderLineRow> orderLineRowsForValidation(Long orderId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
				       expected_ship_date, reservation_warehouse_id, remark, source_quote_line_id,
				       price_source_type, source_no, currency, tax_rate, tax_excluded_unit_price,
				       tax_included_unit_price, tax_excluded_amount, tax_amount, tax_included_amount
				from sal_sales_order_line
				where order_id = ?
				order by line_no asc, id asc
				""", this::mapOrderLineRow, orderId);
	}

	private List<ShipmentLineRow> shipmentLineRows(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select l.id, l.shipment_id, l.line_no, l.order_line_id, l.material_id, l.unit_id, l.ordered_quantity,
			       l.shipped_quantity_before, l.remaining_quantity_before, l.quantity, l.before_quantity, l.after_quantity,
			       l.delivery_plan_id, l.price_source_type, l.source_no, l.currency, l.tax_rate, l.tax_excluded_unit_price,
			       l.tax_included_unit_price, l.tax_excluded_amount, l.tax_amount, l.tax_included_amount, l.remark,
			       p.planned_date as delivery_plan_planned_date
				from sal_sales_shipment_line l
				left join sal_sales_delivery_plan p on p.id = l.delivery_plan_id
				where l.shipment_id = ?
				order by l.line_no asc, l.id asc
				""", this::mapShipmentLineRow, shipmentId);
	}

	private List<SalesOrderLineResponse> orderLines(Long orderId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.material_id, m.code as material_code, m.name as material_name,
				       m.specification as material_spec, l.unit_id, u.name as unit_name, l.quantity,
				       l.shipped_quantity, (l.quantity - l.shipped_quantity) as remaining_quantity,
				       l.unit_price, l.expected_ship_date, l.reservation_warehouse_id,
				       rw.name as reservation_warehouse_name, l.remark, l.source_quote_line_id,
				       l.price_source_type, l.source_no, l.currency, l.tax_rate,
				       l.tax_excluded_unit_price, l.tax_included_unit_price,
				       l.tax_excluded_amount, l.tax_amount, l.tax_included_amount,
				       coalesce(stock.quantity_on_hand, 0.000000) as qualified_quantity_on_hand,
				       coalesce(locked.reserved_quantity, 0.000000) as reserved_quantity,
				       coalesce(locked.occupied_quantity, 0.000000) as occupied_quantity,
				       coalesce(own.own_quantity, 0.000000) as own_reserved_quantity
				from sal_sales_order_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				left join mst_warehouse rw on rw.id = l.reservation_warehouse_id
				left join (
					select warehouse_id, material_id, quality_status,
					       sum(quantity_on_hand) as quantity_on_hand,
					       sum(locked_quantity) as locked_quantity
					from inv_stock_balance
					where quality_status = 'QUALIFIED'
					group by warehouse_id, material_id, quality_status
				) stock on stock.warehouse_id = l.reservation_warehouse_id
					and stock.material_id = l.material_id
					and stock.quality_status = 'QUALIFIED'
				left join (
					select warehouse_id, material_id,
					       sum(case when reservation_type = 'RESERVATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as reserved_quantity,
					       sum(case when reservation_type = 'OCCUPATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as occupied_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by warehouse_id, material_id
				) locked on locked.warehouse_id = l.reservation_warehouse_id and locked.material_id = l.material_id
				left join (
					select source_line_id, sum(quantity - released_quantity - consumed_quantity) as own_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and reservation_type = 'RESERVATION'
					and source_type = 'SALES_ORDER'
					group by source_line_id
				) own on own.source_line_id = l.id
				where l.order_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> {
			BigDecimal remainingQuantity = rs.getBigDecimal("remaining_quantity");
			BigDecimal quantityOnHand = rs.getBigDecimal("qualified_quantity_on_hand");
			BigDecimal ownReservedQuantity = rs.getBigDecimal("own_reserved_quantity");
			BigDecimal reservedQuantity = rs.getBigDecimal("reserved_quantity").subtract(ownReservedQuantity).max(ZERO);
			BigDecimal occupiedQuantity = rs.getBigDecimal("occupied_quantity");
			BigDecimal availableQuantity = quantityOnHand.subtract(reservedQuantity).subtract(occupiedQuantity);
			BigDecimal maxSelectableQuantity = maxSelectableQuantity(remainingQuantity, availableQuantity);
			boolean missingReservationWarehouse = rs.getObject("reservation_warehouse_id", Long.class) == null;
			boolean selectable = !missingReservationWarehouse && maxSelectableQuantity.compareTo(ZERO) > 0;
			String disabledReasonCode = missingReservationWarehouse ? ApiErrorCode.SALES_RESERVATION_WAREHOUSE_REQUIRED.code()
					: selectable ? null : QUALIFIED_BALANCE_NOT_ENOUGH;
			String disabledReason = missingReservationWarehouse ? ApiErrorCode.SALES_RESERVATION_WAREHOUSE_REQUIRED.message()
					: selectable ? null : QUALIFIED_BALANCE_NOT_ENOUGH_MESSAGE;
			return new SalesOrderLineResponse(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("material_id"),
					rs.getString("material_code"), rs.getString("material_name"), rs.getString("material_spec"),
					rs.getLong("unit_id"), rs.getString("unit_name"), quantityString(rs.getBigDecimal("quantity")),
					quantityString(rs.getBigDecimal("shipped_quantity")), quantityString(remainingQuantity),
					moneyString(rs.getBigDecimal("unit_price"), 6),
					rs.getString("price_source_type"), rs.getObject("source_quote_line_id", Long.class),
					rs.getString("source_no"), rs.getString("currency"), moneyString(rs.getBigDecimal("tax_rate"), 6),
					moneyString(rs.getBigDecimal("tax_excluded_unit_price"), 6),
					moneyString(rs.getBigDecimal("tax_included_unit_price"), 6),
					moneyString(rs.getBigDecimal("tax_excluded_amount")), moneyString(rs.getBigDecimal("tax_amount")),
					moneyString(rs.getBigDecimal("tax_included_amount")), rs.getObject("expected_ship_date",
							LocalDate.class),
					rs.getObject("reservation_warehouse_id", Long.class), rs.getString("reservation_warehouse_name"),
					rs.getString("remark"),
					InventoryQualityStatus.QUALIFIED.name(), InventoryQualityStatus.QUALIFIED.displayName(),
					quantityString(quantityOnHand), quantityString(reservedQuantity), quantityString(occupiedQuantity),
					quantityString(availableQuantity), quantityString(availableQuantity),
					selectable, disabledReasonCode, disabledReason, quantityString(maxSelectableQuantity));
		}, orderId);
	}

	private List<SalesShipmentSummaryResponse> orderShipments(Long orderId) {
		return this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.order_id, o.order_no, sh.customer_id, c.name as customer_name,
				       sh.warehouse_id, w.name as warehouse_name, sh.business_date, sh.status,
				       (select count(*) from sal_sales_shipment_line l where l.shipment_id = sh.id) as line_count,
				       coalesce((select sum(l.quantity) from sal_sales_shipment_line l where l.shipment_id = sh.id), 0) as total_quantity,
				       sh.remark, sh.created_by, sh.created_at, sh.updated_at, sh.posted_by, sh.posted_at,
				       sh.version
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				where sh.order_id = ?
				order by sh.updated_at desc, sh.id desc
				""", this::mapShipmentSummary, orderId);
	}

	private List<SalesShipmentLineResponse> shipmentLines(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.order_line_id, l.material_id, m.code as material_code,
				       m.name as material_name, l.unit_id, u.name as unit_name, l.ordered_quantity,
				       l.shipped_quantity_before, l.remaining_quantity_before, l.quantity, l.before_quantity,
				       l.after_quantity, sol.reservation_warehouse_id, rw.name as reservation_warehouse_name,
				       l.delivery_plan_id, l.price_source_type, l.source_no, l.currency, l.tax_rate,
				       l.tax_excluded_unit_price, l.tax_included_unit_price, l.tax_excluded_amount,
				       l.tax_amount, l.tax_included_amount, l.remark, m.tracking_method,
				       coalesce(sb.quantity_on_hand, 0.000000) as qualified_quantity_on_hand,
				       coalesce(locked.reserved_quantity, 0.000000) as reserved_quantity,
				       coalesce(locked.occupied_quantity, 0.000000) as occupied_quantity,
				       coalesce(own.own_quantity, 0.000000) as own_reserved_quantity
				from sal_sales_shipment_line l
				join sal_sales_shipment sh on sh.id = l.shipment_id
				join sal_sales_order_line sol on sol.id = l.order_line_id
				left join mst_warehouse rw on rw.id = sol.reservation_warehouse_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				left join (
					select warehouse_id, material_id, quality_status,
					       sum(quantity_on_hand) as quantity_on_hand,
					       sum(locked_quantity) as locked_quantity
					from inv_stock_balance
					where quality_status = 'QUALIFIED'
					group by warehouse_id, material_id, quality_status
				) sb on sb.warehouse_id = sh.warehouse_id
					and sb.material_id = l.material_id
					and sb.quality_status = 'QUALIFIED'
				left join (
					select warehouse_id, material_id,
					       sum(case when reservation_type = 'RESERVATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as reserved_quantity,
					       sum(case when reservation_type = 'OCCUPATION'
					                then quantity - released_quantity - consumed_quantity else 0 end) as occupied_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by warehouse_id, material_id
				) locked on locked.warehouse_id = sh.warehouse_id and locked.material_id = l.material_id
				left join (
					select source_line_id, sum(quantity - released_quantity - consumed_quantity) as own_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and reservation_type = 'RESERVATION'
					and source_type = 'SALES_ORDER'
					group by source_line_id
				) own on own.source_line_id = l.order_line_id
				where l.shipment_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> {
			BigDecimal quantity = rs.getBigDecimal("quantity");
			BigDecimal quantityOnHand = rs.getBigDecimal("qualified_quantity_on_hand");
			BigDecimal ownReservedQuantity = rs.getBigDecimal("own_reserved_quantity");
			BigDecimal reservedQuantity = rs.getBigDecimal("reserved_quantity").subtract(ownReservedQuantity).max(ZERO);
			BigDecimal occupiedQuantity = rs.getBigDecimal("occupied_quantity");
			BigDecimal availableQuantity = quantityOnHand.subtract(reservedQuantity).subtract(occupiedQuantity);
			BigDecimal maxSelectableQuantity = maxSelectableQuantity(quantity, availableQuantity);
			boolean selectable = maxSelectableQuantity.compareTo(ZERO) > 0;
			InventoryTrackingMethod trackingMethod = InventoryTrackingMethod.valueOf(rs.getString("tracking_method"));
			Long lineId = rs.getLong("id");
			return new SalesShipmentLineResponse(rs.getLong("id"), rs.getInt("line_no"),
					rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
					rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
					quantityString(rs.getBigDecimal("ordered_quantity")),
					quantityString(rs.getBigDecimal("shipped_quantity_before")),
					quantityString(rs.getBigDecimal("remaining_quantity_before")), quantityString(quantity),
					quantityString(rs.getBigDecimal("before_quantity")),
					quantityString(rs.getBigDecimal("after_quantity")),
					rs.getObject("reservation_warehouse_id", Long.class), rs.getString("reservation_warehouse_name"),
					rs.getObject("delivery_plan_id", Long.class),
					rs.getString("price_source_type"), rs.getString("source_no"), rs.getString("currency"),
					moneyString(rs.getBigDecimal("tax_rate"), 6),
					moneyString(rs.getBigDecimal("tax_excluded_unit_price"), 6),
					moneyString(rs.getBigDecimal("tax_included_unit_price"), 6),
					moneyString(rs.getBigDecimal("tax_excluded_amount")),
					moneyString(rs.getBigDecimal("tax_amount")),
					moneyString(rs.getBigDecimal("tax_included_amount")), rs.getString("remark"),
					InventoryQualityStatus.QUALIFIED.name(),
					InventoryQualityStatus.QUALIFIED.displayName(), trackingMethod.name(),
					trackingMethod.displayName(), this.inventoryTrackingService.allocationResponses(
							SHIPMENT_SOURCE_TYPE, shipmentId, lineId),
					quantityString(quantityOnHand), quantityString(reservedQuantity),
					quantityString(occupiedQuantity), quantityString(availableQuantity),
					quantityString(availableQuantity), selectable, selectable ? null : QUALIFIED_BALANCE_NOT_ENOUGH,
					selectable ? null : QUALIFIED_BALANCE_NOT_ENOUGH_MESSAGE, quantityString(maxSelectableQuantity));
		}, shipmentId);
	}

	private List<SalesShipmentInventoryMovementResponse> shipmentInventoryMovements(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select sm.id, sm.movement_no, sm.movement_type, sm.direction, w.name as warehouse_name,
				       m.code as material_code, m.name as material_name, sm.quantity, sm.before_quantity,
				       sm.after_quantity, sm.business_date, sm.operator_name, sm.occurred_at
				from inv_stock_movement sm
				join sal_sales_shipment_line sl on sl.id = sm.source_line_id
				join mst_warehouse w on w.id = sm.warehouse_id
				join mst_material m on m.id = sm.material_id
				where sm.source_type = ?
				and sm.source_id = ?
				order by sm.occurred_at asc, sl.line_no asc, sm.id asc
				""",
				(rs, rowNum) -> new SalesShipmentInventoryMovementResponse(rs.getLong("id"),
						rs.getString("movement_no"), rs.getString("movement_type"), rs.getString("direction"),
						rs.getString("warehouse_name"), rs.getString("material_code"), rs.getString("material_name"),
						quantityString(rs.getBigDecimal("quantity")),
						quantityString(rs.getBigDecimal("before_quantity")),
						quantityString(rs.getBigDecimal("after_quantity")),
						rs.getObject("business_date", LocalDate.class), rs.getString("operator_name"),
						rs.getObject("occurred_at", OffsetDateTime.class)),
				SHIPMENT_SOURCE_TYPE, shipmentId);
	}

	private Optional<CustomerRef> customerRef(Long customerId) {
		return this.jdbcTemplate.query("""
				select id, code, name, status
				from mst_customer
				where id = ?
				""", (rs, rowNum) -> new CustomerRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getString("status")), customerId).stream().findFirst();
	}

	private Optional<MaterialRef> materialRef(Long materialId) {
		return this.jdbcTemplate.query("""
				select id, code, name, unit_id, material_type, status
				from mst_material
				where id = ?
				""", (rs, rowNum) -> new MaterialRef(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
				rs.getLong("unit_id"), rs.getString("material_type"), rs.getString("status")), materialId)
			.stream()
			.findFirst();
	}

	private boolean hasPostedShipments(Long orderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment
				where order_id = ?
				and status = ?
				""", Long.class, orderId, SalesShipmentStatus.POSTED.name());
		return count != null && count > 0;
	}

	private boolean hasOpenDeliveryPlans(Long orderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_delivery_plan
				where order_id = ?
				and status in ('PLANNED', 'PARTIALLY_SHIPPED')
				and planned_quantity > shipped_quantity
				""", Long.class, orderId);
		return count != null && count > 0;
	}

	private boolean hasDraftShipments(Long orderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_shipment
				where order_id = ?
				and status = 'DRAFT'
				""", Long.class, orderId);
		return count != null && count > 0;
	}

	private boolean hasPendingOrderChanges(Long orderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order_change c
				left join platform_approval_instance ai on ai.id = c.approval_instance_id
				where c.order_id = ?
				and (
					c.status = 'DRAFT'
					or ai.status = 'SUBMITTED'
				)
				""", Long.class, orderId);
		return count != null && count > 0;
	}

	private boolean hasPendingOrderApproval(Long orderId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from platform_approval_instance
				where business_object_type = 'SALES_ORDER'
				and business_object_id = ?
				and scene_code in ('SALES_ORDER_CREDIT_OVERRIDE', 'SALES_ORDER_SHORT_CLOSE')
				and status = 'SUBMITTED'
				""", Long.class, orderId);
		return count != null && count > 0;
	}

	private void refreshOrderTaxTotals(Long orderId) {
		this.jdbcTemplate.update("""
				update sal_sales_order o
				set tax_excluded_amount = totals.tax_excluded_amount,
				    tax_amount = totals.tax_amount,
				    tax_included_amount = totals.tax_included_amount
				from (
					select order_id,
					       coalesce(sum(tax_excluded_amount), 0) as tax_excluded_amount,
					       coalesce(sum(tax_amount), 0) as tax_amount,
					       coalesce(sum(tax_included_amount), 0) as tax_included_amount
					from sal_sales_order_line
					where order_id = ?
					group by order_id
				) totals
				where o.id = totals.order_id
				""", orderId);
	}

	private void writeOrderSnapshot(Long orderId, OffsetDateTime now) {
		Long snapshotId = this.jdbcTemplate.query("""
				insert into sal_sales_order_snapshot (
					order_id, order_no, customer_id, customer_code, customer_name, project_id, project_no,
					project_name, contract_id, contract_no, external_contract_no, source_quote_id,
					source_quote_no, source_quote_version, currency, tax_excluded_amount, tax_amount,
					tax_included_amount, snapshot_at, created_at
				)
				select o.id, o.order_no, o.customer_id, c.code, c.name, o.project_id, p.project_no,
				       p.name, o.contract_id, pc.contract_no, pc.external_contract_no, o.source_quote_id,
				       o.source_quote_no, o.source_quote_version, o.currency, o.tax_excluded_amount,
				       o.tax_amount, o.tax_included_amount, ?, ?
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				left join sal_project p on p.id = o.project_id
				left join sal_project_contract pc on pc.id = o.contract_id
				where o.id = ?
				on conflict (order_id) do nothing
				returning id
				""", (rs, rowNum) -> rs.getLong("id"), now, now, orderId).stream().findFirst().orElse(null);
		if (snapshotId == null) {
			snapshotId = this.jdbcTemplate.queryForObject("""
					select id from sal_sales_order_snapshot where order_id = ?
					""", Long.class, orderId);
		}
		this.jdbcTemplate.update("""
				insert into sal_sales_order_line_snapshot (
					order_snapshot_id, order_id, order_line_id, line_no, material_id, material_code, material_name,
					unit_id, unit_name, quantity, price_source_type, source_quote_line_id, source_no, currency,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
					tax_included_amount, created_at
				)
				select ?, l.order_id, l.id, l.line_no, l.material_id, m.code, m.name, l.unit_id, u.name,
				       l.quantity, l.price_source_type, l.source_quote_line_id, l.source_no, l.currency,
				       l.tax_rate, l.tax_excluded_unit_price, l.tax_included_unit_price, l.tax_excluded_amount,
				       l.tax_amount, l.tax_included_amount, ?
				from sal_sales_order_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.order_id = ?
				on conflict (order_line_id) do nothing
				""", snapshotId, now, orderId);
		this.jdbcTemplate.update("""
				update sal_sales_order
				set confirmed_snapshot_at = coalesce(confirmed_snapshot_at, ?)
				where id = ?
				""", now, orderId);
	}

	private SalesOrderDetailResponse idempotentOrderAction(String action, Long id, VersionedActionRequest request,
			CurrentUser operator, Supplier<SalesOrderDetailResponse> callback) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		String fingerprint = actionFingerprint(action, ORDER_TARGET, id, actionRequest.version(),
				actionRequest.reason());
		Optional<ExistingAction> existing = existingAction(action, ORDER_TARGET, id, actionRequest.idempotencyKey(),
				fingerprint, operator);
		if (existing.isPresent()) {
			return order(existing.get().resultResourceId());
		}
		SalesOrderDetailResponse result = callback.get();
		recordActionIdempotency(action, ORDER_TARGET, id, actionRequest.version(), actionRequest.idempotencyKey(),
				fingerprint, ORDER_TARGET, result.id(), result.version(), operator);
		return result;
	}

	private SalesShipmentDetailResponse idempotentShipmentAction(String action, Long id, VersionedActionRequest request,
			CurrentUser operator, Supplier<SalesShipmentDetailResponse> callback) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		String fingerprint = actionFingerprint(action, SHIPMENT_TARGET, id, actionRequest.version(),
				actionRequest.reason());
		Optional<ExistingAction> existing = existingAction(action, SHIPMENT_TARGET, id, actionRequest.idempotencyKey(),
				fingerprint, operator);
		if (existing.isPresent()) {
			return shipment(existing.get().resultResourceId());
		}
		SalesShipmentDetailResponse result = callback.get();
		recordActionIdempotency(action, SHIPMENT_TARGET, id, actionRequest.version(), actionRequest.idempotencyKey(),
				fingerprint, SHIPMENT_TARGET, result.id(), result.version(), operator);
		return result;
	}

	private Optional<ExistingAction> existingAction(String action, String resourceType, Long resourceId,
			String idempotencyKey, String fingerprint, CurrentUser operator) {
		validateIdempotencyKey(idempotencyKey);
		return this.jdbcTemplate.query("""
				select result_resource_type, result_resource_id, result_version, request_fingerprint
				from sal_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = ?
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingAction(rs.getString("result_resource_type"),
				rs.getLong("result_resource_id"), rs.getLong("result_version"), rs.getString("request_fingerprint")),
				operator.id(), action, resourceType, resourceId, idempotencyKey.trim()).stream().findFirst().map((record) -> {
					if (!record.requestFingerprint().equals(fingerprint)) {
						throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
					}
					return record;
				});
	}

	private void recordActionIdempotency(String action, String resourceType, Long resourceId, Long resourceVersion,
			String idempotencyKey, String fingerprint, String resultResourceType, Long resultResourceId,
			Long resultVersion, CurrentUser operator) {
		validateIdempotencyKey(idempotencyKey);
		try {
			this.jdbcTemplate.update("""
					insert into sal_action_idempotency (
						operator_user_id, action, resource_type, resource_id, resource_version, idempotency_key,
						request_fingerprint, result_resource_type, result_resource_id, result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), action, resourceType, resourceId, resourceVersion, idempotencyKey.trim(),
					fingerprint, resultResourceType, resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
	}

	private VersionedActionRequest requireActionRequest(VersionedActionRequest request) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return request;
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private String actionFingerprint(String action, String resourceType, Long resourceId, Long version, String reason) {
		return sha256(action + "|" + resourceType + "|" + resourceId + "|" + version + "|" + nullToBlank(reason));
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (!hasText(idempotencyKey) || idempotencyKey.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private SalesOrderStatus shippedOrderStatus(Long orderId) {
		QuantityTotals totals = orderQuantityTotals(orderId);
		if (totals != null && totals.shippedQuantity().compareTo(ZERO) > 0
				&& totals.shippedQuantity().compareTo(totals.totalQuantity()) >= 0) {
			return SalesOrderStatus.SHIPPED;
		}
		return SalesOrderStatus.PARTIALLY_SHIPPED;
	}

	private QuantityTotals orderQuantityTotals(Long orderId) {
		return this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity), 0) as total_quantity,
				       coalesce(sum(shipped_quantity), 0) as shipped_quantity
				from sal_sales_order_line
				where order_id = ?
				""", (rs, rowNum) -> new QuantityTotals(rs.getBigDecimal("total_quantity"),
				rs.getBigDecimal("shipped_quantity")), orderId);
	}

	private void closeOpenDeliveryPlans(Long orderId, String reason, String operatorName, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				update sal_sales_delivery_plan
				set status = 'CLOSED',
				    close_reason = coalesce(?, close_reason),
				    updated_by = ?,
				    updated_at = ?,
				    version = version + 1
				where order_id = ?
				and status in ('PLANNED', 'PARTIALLY_SHIPPED')
				""", blankToNull(reason), operatorName, now, orderId);
	}

	private SalesOrderSummaryResponse mapOrderSummary(ResultSet rs, int rowNum) throws SQLException {
		return new SalesOrderSummaryResponse(rs.getLong("id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getString("customer_code"), rs.getString("customer_name"),
				rs.getObject("order_date", LocalDate.class), rs.getObject("expected_ship_date", LocalDate.class),
				rs.getString("status"), rs.getInt("line_count"), quantityString(rs.getBigDecimal("total_quantity")),
				quantityString(rs.getBigDecimal("shipped_quantity")),
				quantityString(rs.getBigDecimal("remaining_quantity")), rs.getString("remark"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("confirmed_by"),
				rs.getObject("confirmed_at", OffsetDateTime.class), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class), rs.getLong("version"),
				rs.getObject("project_id", Long.class), rs.getString("project_no"), rs.getString("project_name"),
				rs.getObject("contract_id", Long.class), rs.getString("contract_no"),
				rs.getString("external_contract_no"), rs.getString("currency"),
				moneyString(rs.getBigDecimal("tax_excluded_amount")), moneyString(rs.getBigDecimal("tax_amount")),
				moneyString(rs.getBigDecimal("tax_included_amount")), rs.getObject("source_quote_id", Long.class),
				rs.getString("source_quote_no"), rs.getObject("source_quote_version", Long.class),
				orderAllowedActions(rs.getString("status")), orderActionDisabledReason(rs.getString("status")));
	}

	private SalesShipmentSummaryResponse mapShipmentSummary(ResultSet rs, int rowNum) throws SQLException {
		return new SalesShipmentSummaryResponse(rs.getLong("id"), rs.getString("shipment_no"),
				rs.getLong("order_id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getInt("line_count"),
				quantityString(rs.getBigDecimal("total_quantity")), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private OrderRow mapOrderRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getObject("order_date", LocalDate.class), rs.getObject("expected_ship_date", LocalDate.class),
				SalesOrderStatus.valueOf(rs.getString("status")), rs.getString("remark"), rs.getLong("version"),
				rs.getObject("project_id", Long.class), rs.getObject("contract_id", Long.class));
	}

	private ShipmentRow mapShipmentRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentRow(rs.getLong("id"), rs.getString("shipment_no"), rs.getLong("order_id"),
				rs.getLong("customer_id"), rs.getLong("warehouse_id"), rs.getObject("business_date", LocalDate.class),
				SalesShipmentStatus.valueOf(rs.getString("status")), rs.getString("remark"), rs.getLong("version"));
	}

	private OrderLineRow mapOrderLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderLineRow(rs.getLong("id"), rs.getLong("order_id"), rs.getInt("line_no"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("shipped_quantity"), rs.getBigDecimal("unit_price"),
				rs.getObject("expected_ship_date", LocalDate.class),
				rs.getObject("reservation_warehouse_id", Long.class), rs.getString("remark"),
				rs.getObject("source_quote_line_id", Long.class), rs.getString("price_source_type"),
				rs.getString("source_no"), rs.getString("currency"), rs.getBigDecimal("tax_rate"),
				rs.getBigDecimal("tax_excluded_unit_price"), rs.getBigDecimal("tax_included_unit_price"),
				rs.getBigDecimal("tax_excluded_amount"), rs.getBigDecimal("tax_amount"),
				rs.getBigDecimal("tax_included_amount"));
	}

	private ShipmentLineRow mapShipmentLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentLineRow(rs.getLong("id"), rs.getLong("shipment_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("ordered_quantity"), rs.getBigDecimal("shipped_quantity_before"),
				rs.getBigDecimal("remaining_quantity_before"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
				rs.getObject("delivery_plan_id", Long.class), rs.getString("price_source_type"),
				rs.getString("source_no"), rs.getString("currency"), rs.getBigDecimal("tax_rate"),
				rs.getBigDecimal("tax_excluded_unit_price"), rs.getBigDecimal("tax_included_unit_price"),
				rs.getBigDecimal("tax_excluded_amount"), rs.getBigDecimal("tax_amount"),
				rs.getBigDecimal("tax_included_amount"), rs.getString("remark"),
				rs.getObject("delivery_plan_planned_date", LocalDate.class));
	}

	private BusinessException duplicateSalesException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_sal_sales_order_line_material")) {
			return new BusinessException(ApiErrorCode.SALES_ORDER_DUPLICATE_LINE);
		}
		if (containsConstraint(exception, "uk_sal_sales_shipment_line_order_line")) {
			return new BusinessException(ApiErrorCode.SALES_SHIPMENT_DUPLICATE_LINE);
		}
		if (containsConstraint(exception, "uk_inv_stock_movement_source")) {
			return new BusinessException(ApiErrorCode.SALES_MOVEMENT_SOURCE_DUPLICATED);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private SalesOrderStatus parseOrderStatus(String value) {
		try {
			return SalesOrderStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
	}

	private SalesShipmentStatus parseShipmentStatus(String value) {
		try {
			return SalesShipmentStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_STATUS_INVALID);
		}
	}

	private BusinessException orderNotFound() {
		return new BusinessException(ApiErrorCode.SALES_ORDER_NOT_FOUND);
	}

	private BusinessException shipmentNotFound() {
		return new BusinessException(ApiErrorCode.SALES_SHIPMENT_NOT_FOUND);
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

	private BigDecimal maxSelectableQuantity(BigDecimal requestedQuantity, BigDecimal availableQuantity) {
		BigDecimal normalizedRequested = requestedQuantity == null || requestedQuantity.compareTo(ZERO) < 0 ? ZERO
				: requestedQuantity;
		BigDecimal normalizedAvailable = availableQuantity == null || availableQuantity.compareTo(ZERO) < 0 ? ZERO
				: availableQuantity;
		return normalizedRequested.min(normalizedAvailable);
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private String moneyString(BigDecimal value) {
		return moneyString(value, 2);
	}

	private String moneyString(BigDecimal value, int scale) {
		return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
	}

	private String quantityString(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
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

	private static String nullToBlank(String value) {
		return value == null ? "" : value.trim();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public record SalesOrderLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, Long unitId,
			@NotNull BigDecimal quantity, @NotNull BigDecimal unitPrice, Long reservationWarehouseId,
			LocalDate expectedShipDate, String remark) {
	}

	public record SalesOrderRequest(@NotNull Long customerId, @NotNull LocalDate orderDate,
			LocalDate expectedShipDate, String remark, Long projectId, Long contractId, Long version,
			@Valid List<SalesOrderLineRequest> lines) {
	}

	public record SalesShipmentLineRequest(@NotNull Integer lineNo, @NotNull Long orderLineId, Long deliveryPlanId,
			Long materialId, Long unitId, @NotNull BigDecimal quantity, String earlyDeliveryReason, String remark,
			@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {

		public SalesShipmentLineRequest(Integer lineNo, Long orderLineId, Long materialId, Long unitId,
				BigDecimal quantity, String remark, List<?> trackingAllocations) {
			this(lineNo, orderLineId, null, materialId, unitId, quantity, null, remark,
					castTrackingAllocations(trackingAllocations));
		}

		@SuppressWarnings("unchecked")
		private static List<InventoryTrackingService.TrackingAllocationRequest> castTrackingAllocations(
				List<?> trackingAllocations) {
			return (List<InventoryTrackingService.TrackingAllocationRequest>) trackingAllocations;
		}
	}

	public record SalesShipmentRequest(@NotNull Long warehouseId, @NotNull LocalDate businessDate, String remark,
			@Valid List<SalesShipmentLineRequest> lines) {
	}

	public record VersionedActionRequest(@NotNull Long version, String reason, @NotNull String idempotencyKey) {
	}

	public record SalesOrderSummaryResponse(Long id, String orderNo, Long customerId, String customerCode,
			String customerName, LocalDate orderDate, LocalDate expectedShipDate, String status, int lineCount,
			String totalQuantity, String shippedQuantity, String remainingQuantity, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt, Long version, Long projectId, String projectNo, String projectName,
			Long contractId, String contractNo, String externalContractNo, String currency,
			String taxExcludedAmount, String taxAmount, String taxIncludedAmount, Long sourceQuoteId,
			String sourceQuoteNo, Long sourceQuoteVersion, List<String> allowedActions,
			String actionDisabledReason) {
	}

	public record SalesOrderLineResponse(Long id, Integer lineNo, Long materialId, String materialCode,
			String materialName, String materialSpec, Long unitId, String unitName, String quantity,
			String shippedQuantity, String remainingQuantity, String unitPrice, String priceSourceType,
			Long sourceQuoteLineId, String sourceNo, String currency, String taxRate, String taxExcludedUnitPrice,
			String taxIncludedUnitPrice, String taxExcludedAmount, String taxAmount, String taxIncludedAmount,
			LocalDate expectedShipDate, Long reservationWarehouseId, String reservationWarehouseName, String remark,
			String qualityStatus, String qualityStatusName, String quantityOnHand, String reservedQuantity,
			String occupiedQuantity, String availableQuantity, String availableToPromiseQuantity,
			boolean selectable, String disabledReasonCode, String disabledReason, String maxSelectableQuantity) {
	}

	public record SalesOrderDetailResponse(Long id, String orderNo, Long customerId, String customerCode,
			String customerName, LocalDate orderDate, LocalDate expectedShipDate, String status, int lineCount,
			String totalQuantity, String shippedQuantity, String remainingQuantity, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt, Long version, Long projectId, String projectNo, String projectName,
			Long contractId, String contractNo, String externalContractNo, String currency,
			String taxExcludedAmount, String taxAmount, String taxIncludedAmount, Long sourceQuoteId,
			String sourceQuoteNo, Long sourceQuoteVersion, List<String> allowedActions,
			String actionDisabledReason, List<SalesOrderLineResponse> lines,
			List<SalesShipmentSummaryResponse> shipments) {
	}

	public record SalesShipmentSummaryResponse(Long id, String shipmentNo, Long orderId, String orderNo,
			Long customerId, String customerName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, int lineCount, String totalQuantity, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			Long version) {
	}

	public record SalesShipmentLineResponse(Long id, Integer lineNo, Long orderLineId, Long materialId,
			String materialCode, String materialName, Long unitId, String unitName, String orderedQuantity,
			String shippedQuantityBefore, String remainingQuantityBefore, String quantity,
			String beforeQuantity, String afterQuantity, Long reservationWarehouseId,
			String reservationWarehouseName, Long deliveryPlanId, String priceSourceType, String sourceNo,
			String currency, String taxRate, String taxExcludedUnitPrice, String taxIncludedUnitPrice,
			String taxExcludedAmount, String taxAmount, String taxIncludedAmount, String remark,
			String qualityStatus, String qualityStatusName, String trackingMethod, String trackingMethodName,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations, String quantityOnHand,
			String reservedQuantity, String occupiedQuantity, String availableQuantity,
			String availableToPromiseQuantity, boolean selectable, String disabledReasonCode,
			String disabledReason, String maxSelectableQuantity) {
	}

	public record SalesShipmentInventoryMovementResponse(Long id, String movementNo, String movementType,
			String direction, String warehouseName, String materialCode, String materialName, String quantity,
			String beforeQuantity, String afterQuantity, LocalDate businessDate, String operatorName,
			OffsetDateTime occurredAt) {
	}

	public record SalesShipmentDetailResponse(Long id, String shipmentNo, Long orderId, String orderNo,
			Long customerId, String customerName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, int lineCount, String totalQuantity, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			Long version,
			List<SalesShipmentLineResponse> lines, SalesOrderSummaryResponse orderSummary,
			List<SalesShipmentInventoryMovementResponse> inventoryMovements) {
	}

	private record ValidatedOrder(CustomerRef customer, LocalDate orderDate, LocalDate expectedShipDate,
			String remark, SalesOrderProjectLinkService.ProjectLink projectLink, List<ValidatedOrderLine> lines) {
	}

	private record ValidatedOrderLine(Integer lineNo, Long materialId, Long unitId, BigDecimal quantity,
			BigDecimal unitPrice, Long reservationWarehouseId, LocalDate expectedShipDate, String remark,
			Long sourceQuoteLineId, String priceSourceType, String sourceNo, String currency, BigDecimal taxRate,
			BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount) {
	}

	private record OrderLineSourceSnapshot(Long sourceQuoteLineId, String priceSourceType, String sourceNo,
			String currency, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice,
			BigDecimal taxExcludedAmount, BigDecimal taxAmount, BigDecimal taxIncludedAmount) {
	}

	private record ValidatedShipment(Long warehouseId, LocalDate businessDate, String remark,
			List<ValidatedShipmentLine> lines) {
	}

	private record ValidatedShipmentLine(Integer lineNo, Long orderLineId, Long materialId, Long unitId,
			BigDecimal orderedQuantity, BigDecimal shippedQuantityBefore, BigDecimal remainingQuantityBefore,
			BigDecimal quantity, Long deliveryPlanId, String priceSourceType, String sourceNo, String currency,
			BigDecimal taxRate, BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice,
			BigDecimal taxExcludedAmount, BigDecimal taxAmount, BigDecimal taxIncludedAmount, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	private record DeliveryPlanRow(Long id, Long orderId, Long orderLineId, BigDecimal plannedQuantity,
			BigDecimal shippedQuantity, String status) {
	}

	private record OrderRow(Long id, String orderNo, Long customerId, LocalDate orderDate, LocalDate expectedShipDate,
			SalesOrderStatus status, String remark, long version, Long projectId, Long contractId) {
	}

	private record OrderLineRow(Long id, Long orderId, Integer lineNo, Long materialId, Long unitId,
			BigDecimal quantity, BigDecimal shippedQuantity, BigDecimal unitPrice, LocalDate expectedShipDate,
			Long reservationWarehouseId, String remark, Long sourceQuoteLineId, String priceSourceType,
			String sourceNo, String currency, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount, BigDecimal taxAmount,
			BigDecimal taxIncludedAmount) {
	}

	private record ShipmentRow(Long id, String shipmentNo, Long orderId, Long customerId, Long warehouseId,
			LocalDate businessDate, SalesShipmentStatus status, String remark, Long version) {
	}

	private record ShipmentLineRow(Long id, Long shipmentId, Integer lineNo, Long orderLineId, Long materialId,
			Long unitId, BigDecimal orderedQuantity, BigDecimal shippedQuantityBefore,
			BigDecimal remainingQuantityBefore, BigDecimal quantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, Long deliveryPlanId, String priceSourceType, String sourceNo, String currency,
			BigDecimal taxRate, BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice,
			BigDecimal taxExcludedAmount, BigDecimal taxAmount, BigDecimal taxIncludedAmount, String remark,
			LocalDate deliveryPlanPlannedDate) {
	}

	private record CustomerRef(Long id, String code, String name, String status) {
	}

	private record CreditProfile(Long id, Long customerId, BigDecimal creditLimit, String status, boolean frozen,
			boolean overdueBlocked) {
	}

	private record MaterialRef(Long id, String code, String name, Long unitId, String materialType, String status) {
	}

	private record QuantityTotals(BigDecimal totalQuantity, BigDecimal shippedQuantity) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

	private record ExistingAction(String resultResourceType, Long resultResourceId, Long resultVersion,
			String requestFingerprint) {
	}

}
