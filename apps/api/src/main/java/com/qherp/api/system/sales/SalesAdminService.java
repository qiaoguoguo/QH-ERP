package com.qherp.api.system.sales;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.inventory.InventoryDirection;
import com.qherp.api.system.inventory.InventoryMovementType;
import com.qherp.api.system.inventory.InventoryPostingService;
import com.qherp.api.system.inventory.InventoryQualityStatus;
import com.qherp.api.system.period.BusinessPeriodGuard;
import com.qherp.api.system.period.BusinessPeriodOperation;
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
public class SalesAdminService {

	private static final String ORDER_TARGET = "SALES_ORDER";

	private static final String SHIPMENT_TARGET = "SALES_SHIPMENT";

	private static final String SHIPMENT_SOURCE_TYPE = "SALES_SHIPMENT";

	private static final Set<String> SELLABLE_MATERIAL_TYPES = Set.of("FINISHED_GOOD", "SEMI_FINISHED");

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_NO_ATTEMPTS = 3;

	private static final AtomicInteger ORDER_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger SHIPMENT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final InventoryPostingService inventoryPostingService;

	private final BusinessPeriodGuard businessPeriodGuard;

	public SalesAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService, BusinessPeriodGuard businessPeriodGuard) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
		this.businessPeriodGuard = businessPeriodGuard;
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesOrderSummaryResponse> orders(String keyword, Long customerId, String status,
			LocalDate dateFrom, LocalDate dateTo, LocalDate expectedDateFrom, LocalDate expectedDateTo, int page,
			int pageSize) {
		QueryParts queryParts = orderQueryParts(keyword, customerId, status, dateFrom, dateTo, expectedDateFrom,
				expectedDateTo);
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
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
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
				summary.cancelledAt(), summary.closedByName(), summary.closedAt(), orderLines(id), orderShipments(id));
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
			this.auditService.record(operator, "SALES_ORDER_CREATE", ORDER_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return order(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateSalesException(exception);
		}
	}

	@Transactional
	public SalesOrderDetailResponse updateOrder(Long id, SalesOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		OrderRow current = lockOrder(id).orElseThrow(this::orderNotFound);
		if (current.status() != SalesOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		ValidatedOrder order = validateOrderRequest(request);
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.UPDATE, "SALES_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update sal_sales_order
					set customer_id = ?, order_date = ?, expected_ship_date = ?, remark = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", order.customer().id(), order.orderDate(), order.expectedShipDate(),
					blankToNull(order.remark()), operator.username(), now, id);
			this.jdbcTemplate.update("delete from sal_sales_order_line where order_id = ?", id);
			insertOrderLines(id, order.lines(), now);
			this.auditService.record(operator, "SALES_ORDER_UPDATE", ORDER_TARGET, id, current.orderNo(),
					servletRequest);
			return order(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateSalesException(exception);
		}
	}

	@Transactional
	public SalesOrderDetailResponse confirmOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		if (order.status() != SalesOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CONFIRM, "SALES_ORDER", id);
		validateOrderForConfirmation(order);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_sales_order
				set status = ?, confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", SalesOrderStatus.CONFIRMED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_ORDER_CONFIRM", ORDER_TARGET, id, order.orderNo(), servletRequest);
		return order(id);
	}

	@Transactional
	public SalesOrderDetailResponse cancelOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
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
		this.auditService.record(operator, "SALES_ORDER_CANCEL", ORDER_TARGET, id, order.orderNo(), servletRequest);
		return order(id);
	}

	@Transactional
	public SalesOrderDetailResponse closeOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		if (order.status() != SalesOrderStatus.CONFIRMED && order.status() != SalesOrderStatus.PARTIALLY_SHIPPED
				&& order.status() != SalesOrderStatus.SHIPPED) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_STATUS_INVALID);
		}
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CLOSE, "SALES_ORDER", id);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_sales_order
				set status = ?, closed_by = ?, closed_at = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", SalesOrderStatus.CLOSED.name(), operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_ORDER_CLOSE", ORDER_TARGET, id, order.orderNo(), servletRequest);
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
				       sh.remark, sh.created_by, sh.created_at, sh.updated_at, sh.posted_by, sh.posted_at
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
				summary.updatedAt(), summary.postedByName(), summary.postedAt(), shipmentLines(id),
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
			this.jdbcTemplate.update("delete from sal_sales_shipment_line where shipment_id = ?", id);
			insertShipmentLines(id, shipment.lines(), now);
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
		try {
			ShipmentRow shipment = lockShipment(id).orElseThrow(this::shipmentNotFound);
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
			OffsetDateTime now = OffsetDateTime.now();
			for (ShipmentLineRow line : lines) {
				postShipmentLine(order.id(), shipment, line, operator.username(), now);
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

	private void postShipmentLine(Long orderId, ShipmentRow shipment, ShipmentLineRow line, String operatorName,
			OffsetDateTime now) {
		OrderLineRow orderLine = lockOrderLine(orderId, line.orderLineId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_SHIPMENT_LINE_SOURCE_INVALID));
		if (!line.materialId().equals(orderLine.materialId()) || !line.unitId().equals(orderLine.unitId())) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_LINE_SOURCE_INVALID);
		}
		validateShipmentOrderLineMasterData(orderLine);
		BigDecimal remainingQuantity = orderLine.quantity().subtract(orderLine.shippedQuantity());
		if (line.quantity().compareTo(remainingQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EXCEEDS_ORDER);
		}
		InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
				new InventoryPostingService.PostingRequest(InventoryMovementType.SALES_SHIPMENT,
						InventoryDirection.OUT, shipment.warehouseId(), line.materialId(), line.unitId(),
						line.quantity(), InventoryQualityStatus.QUALIFIED, SHIPMENT_SOURCE_TYPE, shipment.id(),
						line.id(), shipment.businessDate(), "销售出库", line.remark(), operatorName));
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
	}

	private ValidatedOrder validateOrderRequest(SalesOrderRequest request) {
		if (request == null || request.orderDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		CustomerRef customer = validateEnabledCustomer(request.customerId());
		String remark = validateOptionalText(request.remark(), 500);
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> materialIds = new HashSet<>();
		List<ValidatedOrderLine> lines = new ArrayList<>();
		for (SalesOrderLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validateSellableMaterial(line.materialId());
			if (!materialIds.add(material.id())) {
				throw new BusinessException(ApiErrorCode.SALES_ORDER_DUPLICATE_LINE);
			}
			Long unitId = validateUnit(line.unitId(), material);
			BigDecimal quantity = validateQuantity(line.quantity());
			BigDecimal unitPrice = validateUnitPrice(line.unitPrice());
			lines.add(new ValidatedOrderLine(line.lineNo(), material.id(), unitId, quantity, unitPrice,
					line.expectedShipDate(), validateOptionalText(line.remark(), 500)));
		}
		return new ValidatedOrder(customer, request.orderDate(), request.expectedShipDate(), remark, lines);
	}

	private void validateOrderForConfirmation(OrderRow order) {
		validateEnabledCustomer(order.customerId());
		List<OrderLineRow> lines = orderLineRowsForValidation(order.id());
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<Long> materialIds = new HashSet<>();
		for (OrderLineRow line : lines) {
			if (line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validateSellableMaterial(line.materialId());
			if (!materialIds.add(material.id())) {
				throw new BusinessException(ApiErrorCode.SALES_ORDER_DUPLICATE_LINE);
			}
			validateUnit(line.unitId(), material);
			validateQuantity(line.quantity());
			validateUnitPrice(line.unitPrice());
		}
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
			BigDecimal quantity = validateQuantity(line.quantity());
			BigDecimal remainingQuantity = orderLine.quantity().subtract(orderLine.shippedQuantity());
			if (quantity.compareTo(remainingQuantity) > 0) {
				throw new BusinessException(ApiErrorCode.SALES_SHIPMENT_EXCEEDS_ORDER);
			}
			lines.add(new ValidatedShipmentLine(line.lineNo(), orderLine.id(), orderLine.materialId(),
					orderLine.unitId(), orderLine.quantity(), orderLine.shippedQuantity(), remainingQuantity, quantity,
					validateOptionalText(line.remark(), 500)));
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

	private CreatedDocument insertOrderWithRetry(ValidatedOrder order, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String orderNo = nextNo("SO", ORDER_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into sal_sales_order (
							order_no, customer_id, order_date, expected_ship_date, status, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, orderNo, order.customer().id(), order.orderDate(), order.expectedShipDate(),
						SalesOrderStatus.DRAFT.name(), blankToNull(order.remark()), operatorName, now, operatorName,
						now);
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
						expected_ship_date, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", orderId, line.lineNo(), line.materialId(), line.unitId(), line.quantity(), ZERO,
					line.unitPrice(), line.expectedShipDate(), blankToNull(line.remark()), now, now);
		}
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
						shipped_quantity_before, remaining_quantity_before, quantity, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", shipmentId, line.lineNo(), line.orderLineId(), line.materialId(), line.unitId(),
					line.orderedQuantity(), line.shippedQuantityBefore(), line.remainingQuantityBefore(),
					line.quantity(), blankToNull(line.remark()), now, now);
		}
	}

	private QueryParts orderQueryParts(String keyword, Long customerId, String status, LocalDate dateFrom,
			LocalDate dateTo, LocalDate expectedDateFrom, LocalDate expectedDateTo) {
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
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				where o.id = ?
				""", this::mapOrderSummary, id).stream().findFirst();
	}

	private Optional<SalesShipmentSummaryResponse> shipmentSummary(Long id) {
		return this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.order_id, o.order_no, sh.customer_id, c.name as customer_name,
				       sh.warehouse_id, w.name as warehouse_name, sh.business_date, sh.status,
				       (select count(*) from sal_sales_shipment_line l where l.shipment_id = sh.id) as line_count,
				       coalesce((select sum(l.quantity) from sal_sales_shipment_line l where l.shipment_id = sh.id), 0) as total_quantity,
				       sh.remark, sh.created_by, sh.created_at, sh.updated_at, sh.posted_by, sh.posted_at
				from sal_sales_shipment sh
				join sal_sales_order o on o.id = sh.order_id
				join mst_customer c on c.id = sh.customer_id
				join mst_warehouse w on w.id = sh.warehouse_id
				where sh.id = ?
				""", this::mapShipmentSummary, id).stream().findFirst();
	}

	private Optional<OrderRow> lockOrder(Long id) {
		return this.jdbcTemplate.query("""
				select id, order_no, customer_id, order_date, expected_ship_date, status, remark
				from sal_sales_order
				where id = ?
				for update
				""", this::mapOrderRow, id).stream().findFirst();
	}

	private Optional<ShipmentRow> lockShipment(Long id) {
		return this.jdbcTemplate.query("""
				select id, shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark
				from sal_sales_shipment
				where id = ?
				for update
				""", this::mapShipmentRow, id).stream().findFirst();
	}

	private Optional<OrderLineRow> orderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
				       expected_ship_date, remark
				from sal_sales_order_line
				where order_id = ?
				and id = ?
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private Optional<OrderLineRow> lockOrderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
				       expected_ship_date, remark
				from sal_sales_order_line
				where order_id = ?
				and id = ?
				for update
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private List<OrderLineRow> orderLineRowsForValidation(Long orderId) {
		return this.jdbcTemplate.query("""
				select id, order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
				       expected_ship_date, remark
				from sal_sales_order_line
				where order_id = ?
				order by line_no asc, id asc
				""", this::mapOrderLineRow, orderId);
	}

	private List<ShipmentLineRow> shipmentLineRows(Long shipmentId) {
		return this.jdbcTemplate.query("""
				select id, shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity,
				       shipped_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity,
				       remark
				from sal_sales_shipment_line
				where shipment_id = ?
				order by line_no asc, id asc
				""", this::mapShipmentLineRow, shipmentId);
	}

	private List<SalesOrderLineResponse> orderLines(Long orderId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.material_id, m.code as material_code, m.name as material_name,
				       m.specification as material_spec, l.unit_id, u.name as unit_name, l.quantity,
				       l.shipped_quantity, (l.quantity - l.shipped_quantity) as remaining_quantity,
				       l.unit_price, l.expected_ship_date, l.remark
				from sal_sales_order_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.order_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new SalesOrderLineResponse(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getString("material_spec"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getBigDecimal("quantity"), rs.getBigDecimal("shipped_quantity"),
				rs.getBigDecimal("remaining_quantity"), rs.getBigDecimal("unit_price"),
				rs.getObject("expected_ship_date", LocalDate.class), rs.getString("remark")), orderId);
	}

	private List<SalesShipmentSummaryResponse> orderShipments(Long orderId) {
		return this.jdbcTemplate.query("""
				select sh.id, sh.shipment_no, sh.order_id, o.order_no, sh.customer_id, c.name as customer_name,
				       sh.warehouse_id, w.name as warehouse_name, sh.business_date, sh.status,
				       (select count(*) from sal_sales_shipment_line l where l.shipment_id = sh.id) as line_count,
				       coalesce((select sum(l.quantity) from sal_sales_shipment_line l where l.shipment_id = sh.id), 0) as total_quantity,
				       sh.remark, sh.created_by, sh.created_at, sh.updated_at, sh.posted_by, sh.posted_at
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
				       l.after_quantity, l.remark
				from sal_sales_shipment_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.shipment_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> new SalesShipmentLineResponse(rs.getLong("id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getLong("unit_id"), rs.getString("unit_name"),
				rs.getBigDecimal("ordered_quantity"), rs.getBigDecimal("shipped_quantity_before"),
				rs.getBigDecimal("remaining_quantity_before"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"), rs.getString("remark")),
				shipmentId);
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
						rs.getBigDecimal("quantity"), rs.getBigDecimal("before_quantity"),
						rs.getBigDecimal("after_quantity"), rs.getObject("business_date", LocalDate.class),
						rs.getString("operator_name"), rs.getObject("occurred_at", OffsetDateTime.class)),
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

	private SalesOrderStatus shippedOrderStatus(Long orderId) {
		QuantityTotals totals = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity), 0) as total_quantity,
				       coalesce(sum(shipped_quantity), 0) as shipped_quantity
				from sal_sales_order_line
				where order_id = ?
				""", (rs, rowNum) -> new QuantityTotals(rs.getBigDecimal("total_quantity"),
				rs.getBigDecimal("shipped_quantity")), orderId);
		if (totals != null && totals.shippedQuantity().compareTo(ZERO) > 0
				&& totals.shippedQuantity().compareTo(totals.totalQuantity()) >= 0) {
			return SalesOrderStatus.SHIPPED;
		}
		return SalesOrderStatus.PARTIALLY_SHIPPED;
	}

	private SalesOrderSummaryResponse mapOrderSummary(ResultSet rs, int rowNum) throws SQLException {
		return new SalesOrderSummaryResponse(rs.getLong("id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getString("customer_code"), rs.getString("customer_name"),
				rs.getObject("order_date", LocalDate.class), rs.getObject("expected_ship_date", LocalDate.class),
				rs.getString("status"), rs.getInt("line_count"), rs.getBigDecimal("total_quantity"),
				rs.getBigDecimal("shipped_quantity"), rs.getBigDecimal("remaining_quantity"), rs.getString("remark"),
				rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("confirmed_by"),
				rs.getObject("confirmed_at", OffsetDateTime.class), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class));
	}

	private SalesShipmentSummaryResponse mapShipmentSummary(ResultSet rs, int rowNum) throws SQLException {
		return new SalesShipmentSummaryResponse(rs.getLong("id"), rs.getString("shipment_no"),
				rs.getLong("order_id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), rs.getString("status"), rs.getInt("line_count"),
				rs.getBigDecimal("total_quantity"), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class));
	}

	private OrderRow mapOrderRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getObject("order_date", LocalDate.class), rs.getObject("expected_ship_date", LocalDate.class),
				SalesOrderStatus.valueOf(rs.getString("status")), rs.getString("remark"));
	}

	private ShipmentRow mapShipmentRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentRow(rs.getLong("id"), rs.getString("shipment_no"), rs.getLong("order_id"),
				rs.getLong("customer_id"), rs.getLong("warehouse_id"), rs.getObject("business_date", LocalDate.class),
				SalesShipmentStatus.valueOf(rs.getString("status")), rs.getString("remark"));
	}

	private OrderLineRow mapOrderLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderLineRow(rs.getLong("id"), rs.getLong("order_id"), rs.getInt("line_no"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("shipped_quantity"), rs.getBigDecimal("unit_price"),
				rs.getObject("expected_ship_date", LocalDate.class), rs.getString("remark"));
	}

	private ShipmentLineRow mapShipmentLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShipmentLineRow(rs.getLong("id"), rs.getLong("shipment_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("ordered_quantity"), rs.getBigDecimal("shipped_quantity_before"),
				rs.getBigDecimal("remaining_quantity_before"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"), rs.getString("remark"));
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

	public record SalesOrderLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, Long unitId,
			@NotNull BigDecimal quantity, @NotNull BigDecimal unitPrice, LocalDate expectedShipDate, String remark) {
	}

	public record SalesOrderRequest(@NotNull Long customerId, @NotNull LocalDate orderDate,
			LocalDate expectedShipDate, String remark, @Valid List<SalesOrderLineRequest> lines) {
	}

	public record SalesShipmentLineRequest(@NotNull Integer lineNo, @NotNull Long orderLineId, Long materialId,
			Long unitId, @NotNull BigDecimal quantity, String remark) {
	}

	public record SalesShipmentRequest(@NotNull Long warehouseId, @NotNull LocalDate businessDate, String remark,
			@Valid List<SalesShipmentLineRequest> lines) {
	}

	public record SalesOrderSummaryResponse(Long id, String orderNo, Long customerId, String customerCode,
			String customerName, LocalDate orderDate, LocalDate expectedShipDate, String status, int lineCount,
			BigDecimal totalQuantity, BigDecimal shippedQuantity, BigDecimal remainingQuantity, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt) {
	}

	public record SalesOrderLineResponse(Long id, Integer lineNo, Long materialId, String materialCode,
			String materialName, String materialSpec, Long unitId, String unitName, BigDecimal quantity,
			BigDecimal shippedQuantity, BigDecimal remainingQuantity, BigDecimal unitPrice, LocalDate expectedShipDate,
			String remark) {
	}

	public record SalesOrderDetailResponse(Long id, String orderNo, Long customerId, String customerCode,
			String customerName, LocalDate orderDate, LocalDate expectedShipDate, String status, int lineCount,
			BigDecimal totalQuantity, BigDecimal shippedQuantity, BigDecimal remainingQuantity, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt, List<SalesOrderLineResponse> lines, List<SalesShipmentSummaryResponse> shipments) {
	}

	public record SalesShipmentSummaryResponse(Long id, String shipmentNo, Long orderId, String orderNo,
			Long customerId, String customerName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, int lineCount, BigDecimal totalQuantity, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt) {
	}

	public record SalesShipmentLineResponse(Long id, Integer lineNo, Long orderLineId, Long materialId,
			String materialCode, String materialName, Long unitId, String unitName, BigDecimal orderedQuantity,
			BigDecimal shippedQuantityBefore, BigDecimal remainingQuantityBefore, BigDecimal quantity,
			BigDecimal beforeQuantity, BigDecimal afterQuantity, String remark) {
	}

	public record SalesShipmentInventoryMovementResponse(Long id, String movementNo, String movementType,
			String direction, String warehouseName, String materialCode, String materialName, BigDecimal quantity,
			BigDecimal beforeQuantity, BigDecimal afterQuantity, LocalDate businessDate, String operatorName,
			OffsetDateTime occurredAt) {
	}

	public record SalesShipmentDetailResponse(Long id, String shipmentNo, Long orderId, String orderNo,
			Long customerId, String customerName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, int lineCount, BigDecimal totalQuantity, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			List<SalesShipmentLineResponse> lines, SalesOrderSummaryResponse orderSummary,
			List<SalesShipmentInventoryMovementResponse> inventoryMovements) {
	}

	private record ValidatedOrder(CustomerRef customer, LocalDate orderDate, LocalDate expectedShipDate,
			String remark, List<ValidatedOrderLine> lines) {
	}

	private record ValidatedOrderLine(Integer lineNo, Long materialId, Long unitId, BigDecimal quantity,
			BigDecimal unitPrice, LocalDate expectedShipDate, String remark) {
	}

	private record ValidatedShipment(Long warehouseId, LocalDate businessDate, String remark,
			List<ValidatedShipmentLine> lines) {
	}

	private record ValidatedShipmentLine(Integer lineNo, Long orderLineId, Long materialId, Long unitId,
			BigDecimal orderedQuantity, BigDecimal shippedQuantityBefore, BigDecimal remainingQuantityBefore,
			BigDecimal quantity, String remark) {
	}

	private record OrderRow(Long id, String orderNo, Long customerId, LocalDate orderDate, LocalDate expectedShipDate,
			SalesOrderStatus status, String remark) {
	}

	private record OrderLineRow(Long id, Long orderId, Integer lineNo, Long materialId, Long unitId,
			BigDecimal quantity, BigDecimal shippedQuantity, BigDecimal unitPrice, LocalDate expectedShipDate,
			String remark) {
	}

	private record ShipmentRow(Long id, String shipmentNo, Long orderId, Long customerId, Long warehouseId,
			LocalDate businessDate, SalesShipmentStatus status, String remark) {
	}

	private record ShipmentLineRow(Long id, Long shipmentId, Integer lineNo, Long orderLineId, Long materialId,
			Long unitId, BigDecimal orderedQuantity, BigDecimal shippedQuantityBefore,
			BigDecimal remainingQuantityBefore, BigDecimal quantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, String remark) {
	}

	private record CustomerRef(Long id, String code, String name, String status) {
	}

	private record MaterialRef(Long id, String code, String name, Long unitId, String materialType, String status) {
	}

	private record QuantityTotals(BigDecimal totalQuantity, BigDecimal shippedQuantity) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
