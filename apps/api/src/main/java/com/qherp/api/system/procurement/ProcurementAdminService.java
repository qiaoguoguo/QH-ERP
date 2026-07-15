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
import com.qherp.api.system.platform.PlatformApprovalService;
import com.qherp.api.system.quality.QualityAdminService;
import com.qherp.api.system.quality.QualityInspectionSourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Lazy;
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
import java.util.function.Supplier;

@Service
public class ProcurementAdminService {

	private static final String ORDER_TARGET = "PROCUREMENT_ORDER";

	private static final String RECEIPT_TARGET = "PROCUREMENT_RECEIPT";

	private static final String RECEIPT_SOURCE_TYPE = "PURCHASE_RECEIPT";

	private static final String SCHEDULE_TARGET = "PROCUREMENT_ORDER_SCHEDULE";

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

	private final ProcurementRequisitionService requisitionService;

	private final PlatformApprovalService approvalService;

	private final ProcurementActionIdempotencyService actionIdempotencyService;

	public ProcurementAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			InventoryPostingService inventoryPostingService, BusinessPeriodGuard businessPeriodGuard,
			QualityAdminService qualityAdminService, InventoryTrackingService inventoryTrackingService,
			ProcurementRequisitionService requisitionService, @Lazy PlatformApprovalService approvalService,
			ProcurementActionIdempotencyService actionIdempotencyService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.inventoryPostingService = inventoryPostingService;
		this.inventoryTrackingService = inventoryTrackingService;
		this.businessPeriodGuard = businessPeriodGuard;
		this.qualityAdminService = qualityAdminService;
		this.requisitionService = requisitionService;
		this.approvalService = approvalService;
		this.actionIdempotencyService = actionIdempotencyService;
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
				       o.order_date, o.expected_arrival_date, o.status, o.purchase_mode, o.project_id,
				       p.project_no as project_code, p.name as project_name, o.currency,
				       (select count(*) from proc_purchase_order_line l where l.order_id = o.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as total_quantity,
				       coalesce((select sum(l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as received_quantity,
				       coalesce((select sum(l.quantity - l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as remaining_quantity,
				       case when o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				            then coalesce((select sum(l.quantity - l.received_quantity)
				                           from proc_purchase_order_line l where l.order_id = o.id), 0)
				            else 0 end as in_transit_quantity,
				       o.remark, o.created_by, o.created_at, o.updated_at, o.confirmed_by, o.confirmed_at,
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at, o.version
				from proc_purchase_order o
				join mst_supplier s on s.id = o.supplier_id
				left join sal_project p on p.id = o.project_id
				%s
				order by o.updated_at desc, o.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapOrderSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PurchaseOrderDetailResponse order(Long id) {
		return order(id, null);
	}

	@Transactional(readOnly = true)
	public PurchaseOrderDetailResponse order(Long id, CurrentUser currentUser) {
		PurchaseOrderSummaryResponse summary = orderSummary(id).orElseThrow(this::orderNotFound);
		OrderExceptionState exceptionState = orderExceptionState(id);
		List<String> allowedActions = summary.allowedActions();
		if ("DRAFT".equals(summary.status()) && exceptionState.exceptionApprovalRequired()) {
			allowedActions = List.of("UPDATE", "SUBMIT_EXCEPTION", "CANCEL");
		}
		return new PurchaseOrderDetailResponse(summary.id(), summary.orderNo(), summary.supplierId(),
				summary.supplierCode(), summary.supplierName(), summary.orderDate(), summary.expectedArrivalDate(),
				summary.status(), summary.purchaseMode(), summary.procurementMode(), summary.ownershipType(),
				aggregatePriceSourceType(id),
				exceptionState.exceptionApprovalRequired(), exceptionState.exceptionApprovalInstanceId(),
				exceptionState.exceptionApprovalStatus(), exceptionState.exceptionReason(), summary.projectId(),
				summary.projectCode(), summary.projectName(), summary.currency(), summary.version(), summary.lineCount(),
				summary.totalQuantity(),
				summary.receivedQuantity(),
				summary.remainingQuantity(), summary.inTransitQuantity(), summary.inTransitStatus(),
				summary.inTransitStatusName(), summary.remark(), summary.createdByName(), summary.createdAt(),
				summary.updatedAt(), summary.confirmedByName(), summary.confirmedAt(), summary.cancelledByName(),
				summary.cancelledAt(), summary.closedByName(), summary.closedAt(), allowedActions,
				orderLines(id), orderReceipts(id, currentUser));
	}

	@Transactional
	public PurchaseOrderDetailResponse createOrder(PurchaseOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedOrder order = validateOrderRequest(request, operator);
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CREATE, "PURCHASE_ORDER", null);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertOrderWithRetry(order, operator.username(), now);
			insertOrderLines(created.id(), order.lines(), now);
			this.auditService.record(operator, "PROCUREMENT_ORDER_CREATE", ORDER_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return order(created.id(), operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseOrderDetailResponse updateOrder(Long id, PurchaseOrderRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		OrderRow current = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(current.version(), request.version());
		if (current.status() != PurchaseOrderStatus.DRAFT) {
			rejectProjectOwnershipChange(current, request);
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
		ValidatedOrder order = validateOrderRequest(request, operator);
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
			return order(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseOrderDetailResponse confirmOrder(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentOrderAction("CONFIRM", id, request, operator, () -> {
			OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
			requireVersion(order.version(), request.version());
			if (order.status() != PurchaseOrderStatus.DRAFT) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
			}
			if (requiresExceptionApproval(order)) {
				throw exceptionApprovalRequired(order);
			}
			confirmLockedOrder(order, operator, servletRequest);
			return order(id, operator);
		});
	}

	@Transactional
	public PurchaseOrderDetailResponse confirmOrder(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		return confirmOrder(id, new VersionedActionRequest(order.version(), "内部兼容确认",
				"internal-confirm-" + id + "-" + order.version()), operator, servletRequest);
	}

	@Transactional
	public PlatformApprovalService.ApprovalInstanceRecord submitException(Long id,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PlatformApprovalService.ApprovalInstanceRecord idempotent = this.approvalService
			.idempotentSubmitResult("PROCUREMENT_ORDER_EXCEPTION_CONFIRM", id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		if (order.status() != PurchaseOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
		requireVersion(order.version(), request.version());
		if (!requiresExceptionApproval(order)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_SOURCE_INVALID);
		}
		validateOrderForConfirmation(order);
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService
			.submitProcurementOrderException(id, request, operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update proc_purchase_order
				set exception_approval_instance_id = ?, exception_reason = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				returning version
				""", Long.class, approval.id(), blankToNull(request.reason()), operator.username(),
				OffsetDateTime.now(), id);
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		this.auditService.record(operator, "PROCUREMENT_ORDER_EXCEPTION_SUBMIT", ORDER_TARGET, id, order.orderNo(),
				servletRequest);
		return this.approvalService.get(approval.id(), operator);
	}

	@Transactional
	public void confirmOrderFromExceptionApproval(Long id, Long expectedVersion, CurrentUser operator,
			HttpServletRequest servletRequest) {
		OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
		requireVersion(order.version(), expectedVersion);
		if (order.status() != PurchaseOrderStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
		}
		if (!requiresExceptionApproval(order)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_SOURCE_INVALID);
		}
		confirmLockedOrder(order, operator, servletRequest);
	}

	@Transactional
	public void reopenOrderAfterExceptionApprovalTerminal(Long id, CurrentUser operator) {
		this.jdbcTemplate.update("""
				update proc_purchase_order
				set exception_approval_instance_id = null, updated_by = ?, updated_at = ?
				where id = ?
				and status = 'DRAFT'
				""", operator.username(), OffsetDateTime.now(), id);
	}

	@Transactional
	public PurchaseOrderDetailResponse cancelOrder(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentOrderAction("CANCEL", id, request, operator, () -> {
			OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
			requireVersion(order.version(), request.version());
			if (order.status() != PurchaseOrderStatus.DRAFT
					&& !(order.status() == PurchaseOrderStatus.CONFIRMED && !hasPostedReceipts(id))) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
			}
			this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CANCEL,
					"PURCHASE_ORDER", id);
			OffsetDateTime now = OffsetDateTime.now();
			this.jdbcTemplate.update("""
					update proc_purchase_order
					set status = ?, cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", PurchaseOrderStatus.CANCELLED.name(), operator.username(), now, operator.username(), now,
					id);
			this.auditService.record(operator, "PROCUREMENT_ORDER_CANCEL", ORDER_TARGET, id, order.orderNo(),
					servletRequest);
			return order(id, operator);
		});
	}

	@Transactional
	public PurchaseOrderDetailResponse closeOrder(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentOrderAction("CLOSE", id, request, operator, () -> {
			OrderRow order = lockOrder(id).orElseThrow(this::orderNotFound);
			requireVersion(order.version(), request.version());
			if (!hasText(request.reason())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (order.status() != PurchaseOrderStatus.CONFIRMED
					&& order.status() != PurchaseOrderStatus.PARTIALLY_RECEIVED
					&& order.status() != PurchaseOrderStatus.RECEIVED) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
			}
			this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CLOSE,
					"PURCHASE_ORDER", id);
			OffsetDateTime now = OffsetDateTime.now();
			this.jdbcTemplate.update("""
					update proc_purchase_order
					set status = ?, close_reason = ?, closed_by = ?, closed_at = ?, updated_by = ?, updated_at = ?,
					    version = version + 1
					where id = ?
					""", PurchaseOrderStatus.CLOSED.name(), blankToNull(request.reason()), operator.username(), now,
					operator.username(), now, id);
			this.auditService.record(operator, "PROCUREMENT_ORDER_CLOSE", ORDER_TARGET, id, order.orderNo(),
					servletRequest);
			return order(id, operator);
		});
	}

	@Transactional(readOnly = true)
	public PurchaseOrderScheduleListResponse orderSchedules(Long orderId) {
		orderSummary(orderId).orElseThrow(this::orderNotFound);
		List<PurchaseOrderScheduleResponse> items = scheduleResponses(orderId, null, null, null);
		return schedulePage(items, 1, Math.max(items.size(), 1));
	}

	@Transactional(readOnly = true)
	public PurchaseOrderScheduleListResponse orderSchedules(Long orderId, String status, LocalDate expectedDateFrom,
			LocalDate expectedDateTo, int page, int pageSize) {
		orderSummary(orderId).orElseThrow(this::orderNotFound);
		return schedulePage(scheduleResponses(orderId, status, expectedDateFrom, expectedDateTo), page, pageSize);
	}

	@Transactional
	public PurchaseOrderScheduleListResponse updateOrderSchedules(Long orderId, PurchaseOrderScheduleUpdateRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return idempotentScheduleListAction("UPDATE", orderId, request.version(), null, request.idempotencyKey(),
				operator, () -> {
					OrderRow order = lockOrder(orderId).orElseThrow(this::orderNotFound);
					requireVersion(order.version(), request.version());
					if (order.status() != PurchaseOrderStatus.DRAFT && order.status() != PurchaseOrderStatus.CONFIRMED
							&& order.status() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
						throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_STATUS_INVALID);
					}
					List<ValidatedScheduleUpdate> schedules = validateScheduleUpdate(orderId, request.lines());
					if (hasReceivedSchedule(orderId)) {
						throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
					}
					this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.UPDATE,
							"PURCHASE_ORDER", orderId);
					OffsetDateTime now = OffsetDateTime.now();
					this.jdbcTemplate.update("""
							delete from proc_purchase_order_schedule
							where order_line_id in (
								select id from proc_purchase_order_line where order_id = ?
							)
							""", orderId);
					for (ValidatedScheduleUpdate schedule : schedules) {
						this.jdbcTemplate.update("""
								insert into proc_purchase_order_schedule (
									order_line_id, line_no, planned_date, planned_quantity, received_quantity, status,
									remark, created_at, updated_at
								)
								values (?, ?, ?, ?, 0, 'PLANNED', ?, ?, ?)
								""", schedule.orderLineId(), schedule.lineNo(), schedule.plannedDate(),
								schedule.plannedQuantity(), blankToNull(schedule.remark()), now, now);
					}
					this.jdbcTemplate.update("""
							update proc_purchase_order
							set updated_by = ?, updated_at = ?, version = version + 1
							where id = ?
							""", operator.username(), now, orderId);
					this.auditService.record(operator, "PROCUREMENT_ORDER_SCHEDULE_UPDATE", ORDER_TARGET, orderId,
							order.orderNo(), servletRequest);
					return orderSchedules(orderId);
				});
	}

	@Transactional
	public PurchaseOrderScheduleResponse updateOrderSchedule(Long orderId, Long scheduleId,
			PurchaseOrderScheduleSingleUpdateRequest request, CurrentUser operator, HttpServletRequest servletRequest) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ScheduleRow schedule = lockSchedule(orderId, scheduleId).orElseThrow(this::orderNotFound);
		requireVersion(schedule.version(), request.version());
		if (!"PLANNED".equals(schedule.status()) && !"PARTIALLY_RECEIVED".equals(schedule.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
		}
		if (request.lineNo() == null || request.plannedDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		BigDecimal plannedQuantity = validateQuantity(request.plannedQuantity());
		if (plannedQuantity.compareTo(schedule.receivedQuantity()) < 0) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
		}
		this.jdbcTemplate.update("""
				update proc_purchase_order_schedule
				set line_no = ?, planned_date = ?, planned_quantity = ?, remark = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", request.lineNo(), request.plannedDate(), plannedQuantity, blankToNull(request.remark()),
				OffsetDateTime.now(), scheduleId);
		this.auditService.record(operator, "PROCUREMENT_ORDER_SCHEDULE_UPDATE", SCHEDULE_TARGET, scheduleId,
				"schedule:" + scheduleId, servletRequest);
		return scheduleResponse(scheduleId).orElseThrow(this::orderNotFound);
	}

	@Transactional
	public PurchaseOrderScheduleResponse closeOrderSchedule(Long orderId, Long scheduleId, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return idempotentScheduleAction("CLOSE", scheduleId, request, operator, () -> {
			ScheduleRow schedule = lockSchedule(orderId, scheduleId).orElseThrow(this::orderNotFound);
			requireVersion(schedule.version(), request.version());
			if (!hasText(request.reason())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			if (!"PLANNED".equals(schedule.status()) && !"PARTIALLY_RECEIVED".equals(schedule.status())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
			}
			OffsetDateTime now = OffsetDateTime.now();
			this.jdbcTemplate.update("""
					update proc_purchase_order_schedule
					set status = 'CLOSED', closed_reason = ?, updated_at = ?, version = version + 1
					where id = ?
					""", blankToNull(request.reason()), now, scheduleId);
			this.auditService.record(operator, "PROCUREMENT_ORDER_SCHEDULE_CLOSE", SCHEDULE_TARGET, scheduleId,
					"schedule:" + scheduleId, servletRequest);
			return scheduleResponse(scheduleId).orElseThrow(this::orderNotFound);
		});
	}

	@Transactional(readOnly = true)
	public PageResponse<PurchaseReceiptSummaryResponse> receipts(String keyword, Long supplierId, Long warehouseId,
			String status, LocalDate dateFrom, LocalDate dateTo, Long orderId, int page, int pageSize) {
		return receipts(keyword, supplierId, warehouseId, status, dateFrom, dateTo, orderId, page, pageSize, null);
	}

	@Transactional(readOnly = true)
	public PageResponse<PurchaseReceiptSummaryResponse> receipts(String keyword, Long supplierId, Long warehouseId,
			String status, LocalDate dateFrom, LocalDate dateTo, Long orderId, int page, int pageSize,
			CurrentUser currentUser) {
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
				       o.purchase_mode, o.project_id, p.project_no as project_code, p.name as project_name,
				       %s as valuation_state,
				       coalesce((
				           select sum(round(l.quantity * ol.tax_excluded_unit_price, 2))
				           from proc_purchase_receipt_line l
				           join proc_purchase_order_line ol on ol.id = l.order_line_id
				           where l.receipt_id = r.id
				       ), 0) as tax_excluded_amount,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at, r.version
				from proc_purchase_receipt r
				join proc_purchase_order o on o.id = r.order_id
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				left join sal_project p on p.id = o.project_id
				%s
				order by r.updated_at desc, r.id desc
				limit ? offset ?
				""".formatted(receiptValuationStateSql("r"), queryParts.where()),
				(rs, rowNum) -> mapReceiptSummary(rs, rowNum, currentUser), args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PurchaseReceiptDetailResponse receipt(Long id) {
		return receipt(id, null);
	}

	@Transactional(readOnly = true)
	public PurchaseReceiptDetailResponse receipt(Long id, CurrentUser currentUser) {
		PurchaseReceiptSummaryResponse summary = receiptSummary(id, currentUser).orElseThrow(this::receiptNotFound);
		PurchaseOrderSummaryResponse orderSummary = orderSummary(summary.orderId()).orElseThrow(this::orderNotFound);
		List<String> allowedActions = receiptAllowedActions(summary.status(), currentUser);
		return new PurchaseReceiptDetailResponse(summary.id(), summary.receiptNo(), summary.orderId(),
				summary.orderNo(), summary.supplierId(), summary.supplierName(), summary.warehouseId(),
				summary.warehouseName(), summary.businessDate(), summary.status(), summary.version(), summary.lineCount(),
				summary.totalQuantity(), summary.procurementMode(), summary.ownershipType(), summary.projectId(),
				summary.projectCode(), summary.projectName(), summary.valuationState(), summary.valuationStateName(),
				summary.costVisible(), summary.taxExcludedAmount(), summary.remark(), summary.createdByName(), summary.createdAt(),
				summary.updatedAt(), summary.postedByName(), summary.postedAt(), receiptLines(id, summary.costVisible()),
				allowedActions, orderSummary,
				receiptInventoryMovements(id, summary.costVisible()));
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
			return receipt(created.id(), operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseReceiptDetailResponse updateReceipt(Long id, PurchaseReceiptRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ReceiptRow current = lockReceipt(id).orElseThrow(this::receiptNotFound);
		requireVersion(current.version(), request.version());
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
			return receipt(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
	}

	@Transactional
	public PurchaseReceiptDetailResponse postReceipt(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentReceiptAction("POST", id, request, operator, () -> {
			try {
			ReceiptRow receipt = lockReceipt(id).orElseThrow(this::receiptNotFound);
			requireVersion(receipt.version(), request.version());
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
			return receipt(id, operator);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateProcurementException(exception);
		}
		});
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
		applyScheduleReceiptProgress(orderLine, line, now);
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
							receipt.id(), line.id(), receipt.businessDate(), "采购入库", line.remark(), operatorName,
							false, null, null, valuationContext(orderLine)));
			beforeQuantity = posting.beforeQuantity();
			afterQuantity = posting.afterQuantity();
			updateReceiptLineValuation(line.id(), orderLine, posting);
		}
		else {
			for (InventoryTrackingService.ResolvedTrackingAllocation allocation : trackingAllocations) {
				InventoryPostingService.PostingResult posting = this.inventoryPostingService.post(
						new InventoryPostingService.PostingRequest(InventoryMovementType.PURCHASE_RECEIPT,
								InventoryDirection.IN, receipt.warehouseId(), line.materialId(), line.unitId(),
								allocation.quantity(), InventoryQualityStatus.PENDING_INSPECTION, RECEIPT_SOURCE_TYPE,
								receipt.id(), line.id(), receipt.businessDate(), "采购入库", line.remark(),
								operatorName, false, allocation.batchId(), allocation.serialId(),
								valuationContext(orderLine)));
				this.inventoryTrackingService.attachMovement(allocation.allocationId(), posting.movementId());
				this.inventoryTrackingService.markInboundPosted(allocation, receipt.warehouseId(),
						InventoryQualityStatus.PENDING_INSPECTION, posting.movementId(), operatorName);
				beforeQuantity = beforeQuantity.add(posting.beforeQuantity());
				afterQuantity = afterQuantity.add(posting.afterQuantity());
				updateReceiptLineValuation(line.id(), orderLine, posting);
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

	private void applyScheduleReceiptProgress(OrderLineRow orderLine, ReceiptLineRow line, OffsetDateTime now) {
		Long scheduleId = line.scheduleId() == null ? earliestReceivableScheduleId(orderLine.id()) : line.scheduleId();
		if (scheduleId == null) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE);
		}
		ScheduleRow schedule = lockScheduleByOrderLine(orderLine.id(), scheduleId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID));
		if (!"PLANNED".equals(schedule.status()) && !"PARTIALLY_RECEIVED".equals(schedule.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE);
		}
		BigDecimal remainingQuantity = schedule.plannedQuantity().subtract(schedule.receivedQuantity());
		if (line.quantity().compareTo(remainingQuantity) > 0) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE);
		}
		BigDecimal receivedQuantity = schedule.receivedQuantity().add(line.quantity());
		String status = receivedQuantity.compareTo(schedule.plannedQuantity()) >= 0 ? "RECEIVED" : "PARTIALLY_RECEIVED";
		this.jdbcTemplate.update("""
				update proc_purchase_order_schedule
				set received_quantity = ?, status = ?, updated_at = ?, version = version + 1
				where id = ?
				""", receivedQuantity, status, now, schedule.id());
		if (line.scheduleId() == null) {
			this.jdbcTemplate.update("""
					update proc_purchase_receipt_line
					set schedule_id = ?, updated_at = ?
					where id = ?
					""", schedule.id(), now, line.id());
		}
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

	private ValidatedOrder validateOrderRequest(PurchaseOrderRequest request, CurrentUser operator) {
		if (request == null || request.orderDate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		SupplierRef supplier = validateEnabledSupplier(request.supplierId());
		PurchaseMode purchaseMode = parsePurchaseMode(request.purchaseMode());
		Long projectId = purchaseMode == PurchaseMode.PROJECT ? request.projectId() : null;
		if (purchaseMode == PurchaseMode.PROJECT) {
			if (projectId == null) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_PROJECT_MISMATCH);
			}
			validateActiveProject(projectId);
		}
		if (!"CNY".equals(request.currency() == null ? "CNY" : request.currency())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_CURRENCY_UNSUPPORTED);
		}
		String remark = validateOptionalText(request.remark(), 500);
		String publicDirectReason = validateOptionalText(request.publicDirectReason(), 200);
		String exceptionReason = validateOptionalText(request.exceptionReason(), 200);
		if (request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<String> lineKeys = new HashSet<>();
		for (PurchaseOrderLineRequest line : request.lines()) {
			if (line == null || line.lineNo() == null || line.lineNo() <= 0 || line.materialId() == null
					|| !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			String lineKey = line.materialId() + "|" + line.sourceRequisitionLineId() + "|" + line.sourceQuoteLineId()
					+ "|" + line.priceAgreementLineId();
			if (!lineKeys.add(lineKey)) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_DUPLICATE_LINE);
			}
		}
		List<ValidatedOrderLine> lines = new ArrayList<>();
		for (PurchaseOrderLineRequest line : request.lines()) {
			MaterialRef material = validatePurchasableMaterial(line.materialId());
			Long unitId = validateUnit(line.unitId(), material);
			BigDecimal quantity = validateQuantity(line.quantity());
			BigDecimal unitPrice = validateUnitPrice(line.unitPrice() == null ? line.taxExcludedUnitPrice() : line.unitPrice());
			Long sourceRequisitionLineId = line.sourceRequisitionLineId();
			if (purchaseMode == PurchaseMode.PROJECT) {
				if (sourceRequisitionLineId == null) {
					throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_REQUISITION_REQUIRED);
				}
				ProcurementRequisitionService.RequisitionLineSource source = this.requisitionService
					.sourceLine(sourceRequisitionLineId);
				if (source.status() != PurchaseRequisitionStatus.APPROVED
						&& source.status() != PurchaseRequisitionStatus.PARTIALLY_ORDERED) {
					throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_REQUISITION_REQUIRED);
				}
				if (source.purchaseMode() != PurchaseMode.PROJECT || source.projectId() == null
						|| !source.projectId().equals(projectId) || !source.materialId().equals(material.id())
						|| !source.unitId().equals(unitId)) {
					throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_PROJECT_MISMATCH);
				}
				if (source.orderedQuantity().add(quantity).compareTo(source.quantity()) > 0) {
					throw new BusinessException(ApiErrorCode.PROCUREMENT_QUANTITY_INVALID);
				}
			}
			List<ValidatedSchedule> schedules = validateSchedules(line.schedules(), quantity, line.expectedArrivalDate());
			BigDecimal taxRate = line.taxRate() == null ? ZERO : validateUnitPrice(line.taxRate());
			BigDecimal taxExcludedUnitPrice = line.taxExcludedUnitPrice() == null ? unitPrice
					: validateUnitPrice(line.taxExcludedUnitPrice());
			BigDecimal taxIncludedUnitPrice = line.taxIncludedUnitPrice() == null ? unitPrice
					: validateUnitPrice(line.taxIncludedUnitPrice());
			String requestedPriceSourceType = line.priceSourceType() == null ? request.priceSourceType()
					: line.priceSourceType();
			lines.add(new ValidatedOrderLine(line.lineNo(), material.id(), unitId, quantity, unitPrice,
					line.expectedArrivalDate(), validateOptionalText(line.remark(), 500), sourceRequisitionLineId,
					line.sourceQuoteLineId(), line.priceAgreementLineId(), taxRate, taxExcludedUnitPrice,
					taxIncludedUnitPrice, requestedPriceSourceType, schedules));
		}
		boolean hasRequisitionSource = lines.stream().anyMatch((line) -> line.sourceRequisitionLineId() != null);
		boolean hasQuotedOrAgreementSource = lines.stream()
			.anyMatch((line) -> line.sourceQuoteLineId() != null || line.priceAgreementLineId() != null);
		if (purchaseMode == PurchaseMode.PUBLIC && !hasRequisitionSource && !hasQuotedOrAgreementSource) {
			if (!operator.permissions().contains("procurement:order:public-direct")) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_PUBLIC_DIRECT_PERMISSION_REQUIRED);
			}
			if (!hasText(publicDirectReason)) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_PUBLIC_DIRECT_REASON_REQUIRED);
			}
		}
		return new ValidatedOrder(supplier, request.orderDate(), request.expectedArrivalDate(), purchaseMode,
				projectId, request.currency() == null ? "CNY" : request.currency(), publicDirectReason,
				exceptionReason, remark, lines);
	}

	private void validateActiveProject(Long projectId) {
		List<String> statuses = this.jdbcTemplate.query("select status from sal_project where id = ?",
				(rs, rowNum) -> rs.getString("status"), projectId);
		if (statuses.isEmpty() || !"ACTIVE".equals(statuses.getFirst())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PROJECT_STATUS_INVALID);
		}
	}

	private void rejectProjectOwnershipChange(OrderRow current, PurchaseOrderRequest request) {
		if (request == null) {
			return;
		}
		PurchaseMode requestedMode = hasText(request.purchaseMode()) ? parsePurchaseMode(request.purchaseMode())
				: current.purchaseMode();
		Long requestedProjectId = requestedMode == PurchaseMode.PROJECT ? request.projectId() : null;
		boolean projectChanged = current.projectId() == null ? requestedProjectId != null
				: !current.projectId().equals(requestedProjectId);
		if (requestedMode != current.purchaseMode() || projectChanged) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_PROJECT_IMMUTABLE);
		}
	}

	private InventoryPostingService.ValuationContext valuationContext(OrderLineRow orderLine) {
		if (orderLine.purchaseMode() == PurchaseMode.PROJECT) {
			return new InventoryPostingService.ValuationContext("PROJECT", orderLine.projectId(), orderLine.unitPrice(),
					null, null);
		}
		return InventoryPostingService.ValuationContext.publicStock(orderLine.unitPrice());
	}

	private void updateReceiptLineValuation(Long receiptLineId, OrderLineRow orderLine,
			InventoryPostingService.PostingResult posting) {
		this.jdbcTemplate.update("""
				update proc_purchase_receipt_line
				set purchase_mode = ?, project_id = ?, cost_layer_id = coalesce(?, cost_layer_id),
				    value_movement_id = coalesce(?, value_movement_id)
				where id = ?
				""", orderLine.purchaseMode().name(), orderLine.projectId(), posting.costLayerId(),
				posting.valueMovementId(), receiptLineId);
	}

	private List<ValidatedSchedule> validateSchedules(List<PurchaseOrderScheduleRequest> requests,
			BigDecimal lineQuantity, LocalDate fallbackDate) {
		if (requests == null || requests.isEmpty()) {
			return List.of(new ValidatedSchedule(1, fallbackDate, lineQuantity, null));
		}
		Set<Integer> lineNos = new HashSet<>();
		BigDecimal total = ZERO;
		List<ValidatedSchedule> schedules = new ArrayList<>();
		for (PurchaseOrderScheduleRequest schedule : requests) {
			if (schedule == null || schedule.lineNo() == null || schedule.lineNo() <= 0
					|| !lineNos.add(schedule.lineNo()) || schedule.plannedDate() == null) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
			}
			BigDecimal quantity = validateQuantity(schedule.plannedQuantity());
			total = total.add(quantity);
			schedules.add(new ValidatedSchedule(schedule.lineNo(), schedule.plannedDate(), quantity,
					validateOptionalText(schedule.remark(), 500)));
		}
		if (total.compareTo(lineQuantity) != 0) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
		}
		return schedules;
	}

	private List<ValidatedScheduleUpdate> validateScheduleUpdate(Long orderId,
			List<PurchaseOrderScheduleUpdateLineRequest> requests) {
		if (requests == null || requests.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
		}
		List<OrderLineRow> orderLines = orderLineRowsForValidation(orderId);
		Set<Long> orderLineIds = new HashSet<>();
		for (OrderLineRow line : orderLines) {
			orderLineIds.add(line.id());
		}
		Set<Long> requestedLineIds = new HashSet<>();
		Set<String> scheduleKeys = new HashSet<>();
		List<ValidatedScheduleUpdate> schedules = new ArrayList<>();
		for (PurchaseOrderScheduleUpdateLineRequest request : requests) {
			if (request == null || request.orderLineId() == null || !orderLineIds.contains(request.orderLineId())
					|| request.lineNo() == null || request.lineNo() <= 0 || request.plannedDate() == null
					|| !scheduleKeys.add(request.orderLineId() + "|" + request.lineNo())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
			}
			requestedLineIds.add(request.orderLineId());
			schedules.add(new ValidatedScheduleUpdate(request.orderLineId(), request.lineNo(), request.plannedDate(),
					validateQuantity(request.plannedQuantity()), validateOptionalText(request.remark(), 500)));
		}
		if (!requestedLineIds.equals(orderLineIds)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
		}
		for (OrderLineRow orderLine : orderLines) {
			BigDecimal total = schedules.stream()
				.filter((schedule) -> schedule.orderLineId().equals(orderLine.id()))
				.map(ValidatedScheduleUpdate::plannedQuantity)
				.reduce(ZERO, BigDecimal::add);
			if (total.compareTo(orderLine.quantity()) != 0) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_SCHEDULE_INVALID);
			}
		}
		return schedules;
	}

	private void validateOrderForConfirmation(OrderRow order) {
		validateEnabledSupplier(order.supplierId());
		List<OrderLineRow> lines = orderLineRowsForValidation(order.id());
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_EMPTY_LINES);
		}
		Set<Integer> lineNos = new HashSet<>();
		Set<String> lineKeys = new HashSet<>();
		for (OrderLineRow line : lines) {
			if (line.lineNo() == null || line.lineNo() <= 0 || !lineNos.add(line.lineNo())) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			MaterialRef material = validatePurchasableMaterial(line.materialId());
			String lineKey = material.id() + "|" + line.sourceRequisitionLineId() + "|" + line.sourceQuoteLineId()
					+ "|" + line.priceAgreementLineId();
			if (!lineKeys.add(lineKey)) {
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
			Long scheduleId = line.scheduleId();
			if (scheduleId == null) {
				scheduleId = earliestReceivableScheduleId(orderLine.id());
				if (scheduleId == null) {
					throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE);
				}
				ScheduleRow schedule = scheduleByOrderLine(orderLine.id(), scheduleId)
					.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE));
				if (quantity.compareTo(schedule.plannedQuantity().subtract(schedule.receivedQuantity())) > 0) {
					throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_EXCEEDS_SCHEDULE);
				}
			}
			else if (!scheduleBelongsToOrderLine(orderLine.id(), scheduleId)) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID);
			}
			lines.add(new ValidatedReceiptLine(line.lineNo(), orderLine.id(), orderLine.materialId(),
					orderLine.unitId(), orderLine.quantity(), orderLine.receivedQuantity(), remainingQuantity,
					scheduleId, quantity, orderLine.purchaseMode(), orderLine.projectId(),
					validateOptionalText(line.remark(), 500), line.trackingAllocations()));
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

	@Transactional
	public PurchaseReceiptDetailResponse postReceipt(Long id, CurrentUser operator, HttpServletRequest servletRequest) {
		ReceiptRow receipt = lockReceipt(id).orElseThrow(this::receiptNotFound);
		return postReceipt(id, new VersionedActionRequest(receipt.version(), "内部兼容过账",
				"internal-receipt-post-" + id + "-" + receipt.version()), operator, servletRequest);
	}

	private void confirmLockedOrder(OrderRow order, CurrentUser operator, HttpServletRequest servletRequest) {
		this.businessPeriodGuard.assertWritable(order.orderDate(), BusinessPeriodOperation.CONFIRM, "PURCHASE_ORDER",
				order.id());
		validateOrderForConfirmation(order);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_purchase_order
				set status = ?, confirmed_by = ?, confirmed_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", PurchaseOrderStatus.CONFIRMED.name(), operator.username(), now, operator.username(), now,
				order.id());
		for (OrderLineRow line : orderLineRowsForValidation(order.id())) {
			if (line.sourceRequisitionLineId() != null) {
				this.requisitionService.addOrderedQuantity(line.sourceRequisitionLineId(), line.quantity(), operator);
			}
		}
		this.auditService.record(operator, "PROCUREMENT_ORDER_CONFIRM", ORDER_TARGET, order.id(), order.orderNo(),
				servletRequest);
	}

	private boolean requiresExceptionApproval(OrderRow order) {
		return requiresExceptionApproval(order.id());
	}

	private boolean requiresExceptionApproval(Long orderId) {
		return isPublicDirectOrder(orderId) || hasNonLowestQuoteSelection(orderId)
				|| hasActiveAgreementViolation(orderId);
	}

	private BusinessException exceptionApprovalRequired(OrderRow order) {
		if (isPublicDirectOrder(order.id())) {
			return new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_PUBLIC_DIRECT_APPROVAL_REQUIRED);
		}
		if (hasActiveAgreementViolation(order.id())) {
			return new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_AGREEMENT_DEVIATION);
		}
		return new BusinessException(ApiErrorCode.PROCUREMENT_ORDER_EXCEPTION_APPROVAL_REQUIRED);
	}

	private boolean isPublicDirectOrder(Long orderId) {
		Boolean result = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from proc_purchase_order o
					left join proc_purchase_order_line l on l.order_id = o.id
					where o.id = ?
					and (o.public_direct_reason is not null or l.price_source_type = 'PUBLIC_DIRECT')
				)
				""", Boolean.class, orderId);
		return Boolean.TRUE.equals(result);
	}

	private boolean hasNonLowestQuoteSelection(Long orderId) {
		Boolean result = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from proc_purchase_order_line l
					join proc_supplier_quote_line ql on ql.id = l.source_quote_line_id
					where l.order_id = ?
					and l.price_source_type = 'QUOTE_SELECTION'
					and ql.tax_excluded_unit_price > (
						select min(other.tax_excluded_unit_price)
						from proc_supplier_quote_line other
						where other.inquiry_line_id = ql.inquiry_line_id
					)
				)
				""", Boolean.class, orderId);
		return Boolean.TRUE.equals(result);
	}

	private boolean hasActiveAgreementViolation(Long orderId) {
		Boolean result = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from proc_purchase_order_line l
					join proc_purchase_order o on o.id = l.order_id
					where o.id = ?
					and (
						exists (
							select 1
							from proc_price_agreement active_agreement
							join proc_price_agreement_line active_line
							  on active_line.agreement_id = active_agreement.id
							 and active_line.material_id = l.material_id
							where active_agreement.status = 'ACTIVE'
							and active_agreement.supplier_id = o.supplier_id
							and active_agreement.purchase_mode = o.purchase_mode
							and coalesce(active_agreement.project_id, 0) = coalesce(o.project_id, 0)
							and active_agreement.valid_from <= o.order_date
							and active_agreement.valid_to >= o.order_date
							and (
								l.price_agreement_line_id is null
								or l.price_agreement_line_id <> active_line.id
								or l.price_source_type <> 'AGREEMENT'
								or l.tax_rate <> active_line.tax_rate
								or l.tax_excluded_unit_price <> active_line.tax_excluded_unit_price
							)
						)
						or (
							l.price_agreement_line_id is not null
							and not exists (
								select 1
								from proc_price_agreement referenced_agreement
								join proc_price_agreement_line referenced_line
								  on referenced_line.agreement_id = referenced_agreement.id
								where referenced_line.id = l.price_agreement_line_id
								and referenced_agreement.status = 'ACTIVE'
								and referenced_agreement.supplier_id = o.supplier_id
								and referenced_agreement.purchase_mode = o.purchase_mode
								and coalesce(referenced_agreement.project_id, 0) = coalesce(o.project_id, 0)
								and referenced_agreement.valid_from <= o.order_date
								and referenced_agreement.valid_to >= o.order_date
							)
						)
					)
				)
				""", Boolean.class, orderId);
		return Boolean.TRUE.equals(result);
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private PurchaseOrderDetailResponse idempotentOrderAction(String action, Long id, VersionedActionRequest request,
			CurrentUser operator, Supplier<PurchaseOrderDetailResponse> callback) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		String fingerprint = this.actionIdempotencyService.fingerprint(action, ORDER_TARGET, id,
				actionRequest.version(), actionRequest.reason());
		Optional<ProcurementActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService
			.existing(action, ORDER_TARGET, id, actionRequest.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return order(existing.get().resultResourceId(), operator);
		}
		PurchaseOrderDetailResponse result = callback.get();
		this.actionIdempotencyService.record(action, ORDER_TARGET, id, actionRequest.version(),
				actionRequest.idempotencyKey(), fingerprint, ORDER_TARGET, result.id(), result.version(), operator);
		return result;
	}

	private PurchaseReceiptDetailResponse idempotentReceiptAction(String action, Long id, VersionedActionRequest request,
			CurrentUser operator, Supplier<PurchaseReceiptDetailResponse> callback) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		String fingerprint = this.actionIdempotencyService.fingerprint(action, RECEIPT_TARGET, id,
				actionRequest.version(), actionRequest.reason());
		Optional<ProcurementActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService
			.existing(action, RECEIPT_TARGET, id, actionRequest.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return receipt(existing.get().resultResourceId(), operator);
		}
		PurchaseReceiptDetailResponse result = callback.get();
		this.actionIdempotencyService.record(action, RECEIPT_TARGET, id, actionRequest.version(),
				actionRequest.idempotencyKey(), fingerprint, RECEIPT_TARGET, result.id(), result.version(), operator);
		return result;
	}

	private PurchaseOrderScheduleListResponse idempotentScheduleListAction(String action, Long orderId, Long version,
			String reason, String idempotencyKey, CurrentUser operator, Supplier<PurchaseOrderScheduleListResponse> callback) {
		String fingerprint = this.actionIdempotencyService.fingerprint(action, ORDER_TARGET, orderId, version, reason);
		Optional<ProcurementActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService
			.existing(action, ORDER_TARGET, orderId, idempotencyKey, fingerprint, operator);
		if (existing.isPresent()) {
			return orderSchedules(existing.get().resultResourceId());
		}
		PurchaseOrderScheduleListResponse result = callback.get();
		Long resultVersion = orderSummary(orderId).orElseThrow(this::orderNotFound).version();
		this.actionIdempotencyService.record(action, ORDER_TARGET, orderId, version, idempotencyKey, fingerprint,
				ORDER_TARGET, orderId, resultVersion, operator);
		return result;
	}

	private PurchaseOrderScheduleResponse idempotentScheduleAction(String action, Long scheduleId,
			VersionedActionRequest request, CurrentUser operator, Supplier<PurchaseOrderScheduleResponse> callback) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		String fingerprint = this.actionIdempotencyService.fingerprint(action, SCHEDULE_TARGET, scheduleId,
				actionRequest.version(), actionRequest.reason());
		Optional<ProcurementActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService
			.existing(action, SCHEDULE_TARGET, scheduleId, actionRequest.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return scheduleResponse(existing.get().resultResourceId()).orElseThrow(this::orderNotFound);
		}
		PurchaseOrderScheduleResponse result = callback.get();
		this.actionIdempotencyService.record(action, SCHEDULE_TARGET, scheduleId, actionRequest.version(),
				actionRequest.idempotencyKey(), fingerprint, SCHEDULE_TARGET, result.id(), result.version(), operator);
		return result;
	}

	private VersionedActionRequest requireActionRequest(VersionedActionRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return request;
	}

	private CreatedDocument insertOrderWithRetry(ValidatedOrder order, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String orderNo = nextNo("PO", ORDER_NO_SEQUENCE);
			try {
					Long id = this.jdbcTemplate.queryForObject("""
							insert into proc_purchase_order (
							order_no, supplier_id, order_date, expected_arrival_date, status, purchase_mode,
							project_id, currency, public_direct_reason, exception_reason, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, orderNo, order.supplier().id(), order.orderDate(), order.expectedArrivalDate(),
						PurchaseOrderStatus.DRAFT.name(), order.purchaseMode().name(), order.projectId(),
						order.currency(), blankToNull(order.publicDirectReason()), blankToNull(order.exceptionReason()),
						blankToNull(order.remark()), operatorName, now, operatorName, now);
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
			BigDecimal taxExcludedAmount = line.quantity().multiply(line.taxExcludedUnitPrice()).setScale(2,
					java.math.RoundingMode.HALF_UP);
			BigDecimal taxIncludedAmount = line.quantity().multiply(line.taxIncludedUnitPrice()).setScale(2,
					java.math.RoundingMode.HALF_UP);
			Long lineId = this.jdbcTemplate.queryForObject("""
					insert into proc_purchase_order_line (
						order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price,
						expected_arrival_date, remark, source_requisition_line_id, source_quote_line_id,
						price_agreement_line_id, price_source_type, tax_rate, tax_excluded_unit_price,
						tax_included_unit_price, tax_excluded_amount, tax_included_amount, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, orderId, line.lineNo(), line.materialId(), line.unitId(), line.quantity(), ZERO,
					line.unitPrice(), line.expectedArrivalDate(), blankToNull(line.remark()),
					line.sourceRequisitionLineId(), line.sourceQuoteLineId(), line.priceAgreementLineId(),
					line.priceSourceType(), line.taxRate(), line.taxExcludedUnitPrice(), line.taxIncludedUnitPrice(),
					taxExcludedAmount, taxIncludedAmount, now, now);
			insertSchedules(lineId, line.schedules(), now);
		}
	}

	private void insertSchedules(Long orderLineId, List<ValidatedSchedule> schedules, OffsetDateTime now) {
		for (ValidatedSchedule schedule : schedules) {
			this.jdbcTemplate.update("""
					insert into proc_purchase_order_schedule (
						order_line_id, line_no, planned_date, planned_quantity, received_quantity, status, remark,
						created_at, updated_at
					)
					values (?, ?, ?, ?, 0, 'PLANNED', ?, ?, ?)
					""", orderLineId, schedule.lineNo(), schedule.plannedDate(), schedule.plannedQuantity(),
					blankToNull(schedule.remark()), now, now);
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
						received_quantity_before, remaining_quantity_before, schedule_id, quantity, remark,
						purchase_mode, project_id, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, receiptId, line.lineNo(), line.orderLineId(), line.materialId(), line.unitId(),
					line.orderedQuantity(), line.receivedQuantityBefore(), line.remainingQuantityBefore(),
					line.scheduleId(), line.quantity(), blankToNull(line.remark()), line.purchaseMode().name(),
					line.projectId(), now, now);
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
				       o.order_date, o.expected_arrival_date, o.status, o.purchase_mode, o.project_id,
				       p.project_no as project_code, p.name as project_name, o.currency,
				       (select count(*) from proc_purchase_order_line l where l.order_id = o.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as total_quantity,
				       coalesce((select sum(l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as received_quantity,
				       coalesce((select sum(l.quantity - l.received_quantity) from proc_purchase_order_line l where l.order_id = o.id), 0) as remaining_quantity,
				       case when o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				            then coalesce((select sum(l.quantity - l.received_quantity)
				                           from proc_purchase_order_line l where l.order_id = o.id), 0)
				            else 0 end as in_transit_quantity,
				       o.remark, o.created_by, o.created_at, o.updated_at, o.confirmed_by, o.confirmed_at,
				       o.cancelled_by, o.cancelled_at, o.closed_by, o.closed_at, o.version
				from proc_purchase_order o
				join mst_supplier s on s.id = o.supplier_id
				left join sal_project p on p.id = o.project_id
				where o.id = ?
				""", this::mapOrderSummary, id).stream().findFirst();
	}

	private OrderExceptionState orderExceptionState(Long id) {
		return this.jdbcTemplate.query("""
				select o.exception_approval_instance_id, ai.status as exception_approval_status, o.exception_reason
				from proc_purchase_order o
				left join platform_approval_instance ai on ai.id = o.exception_approval_instance_id
				where o.id = ?
				""", (rs, rowNum) -> new OrderExceptionState(requiresExceptionApproval(id),
				nullableLong(rs, "exception_approval_instance_id"), rs.getString("exception_approval_status"),
				rs.getString("exception_reason")), id)
			.stream()
			.findFirst()
			.orElseThrow(this::orderNotFound);
	}

	private Optional<PurchaseReceiptSummaryResponse> receiptSummary(Long id) {
		return receiptSummary(id, null);
	}

	private Optional<PurchaseReceiptSummaryResponse> receiptSummary(Long id, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.order_id, o.order_no, r.supplier_id, s.name as supplier_name,
				       r.warehouse_id, w.name as warehouse_name, r.business_date, r.status,
				       (select count(*) from proc_purchase_receipt_line l where l.receipt_id = r.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_receipt_line l where l.receipt_id = r.id), 0) as total_quantity,
				       o.purchase_mode, o.project_id, p.project_no as project_code, p.name as project_name,
				       %s as valuation_state,
				       coalesce((
				           select sum(round(l.quantity * ol.tax_excluded_unit_price, 2))
				           from proc_purchase_receipt_line l
				           join proc_purchase_order_line ol on ol.id = l.order_line_id
				           where l.receipt_id = r.id
				       ), 0) as tax_excluded_amount,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at, r.version
				from proc_purchase_receipt r
				join proc_purchase_order o on o.id = r.order_id
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				left join sal_project p on p.id = o.project_id
				where r.id = ?
				""".formatted(receiptValuationStateSql("r")),
				(rs, rowNum) -> mapReceiptSummary(rs, rowNum, currentUser), id).stream().findFirst();
	}

	private Optional<OrderRow> lockOrder(Long id) {
		return this.jdbcTemplate.query("""
				select id, order_no, supplier_id, order_date, expected_arrival_date, status, purchase_mode,
				       project_id, currency, remark, version
				from proc_purchase_order
				where id = ?
				for update
				""", this::mapOrderRow, id).stream().findFirst();
	}

	private Optional<ReceiptRow> lockReceipt(Long id) {
		return this.jdbcTemplate.query("""
				select id, receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark, version
				from proc_purchase_receipt
				where id = ?
				for update
				""", this::mapReceiptRow, id).stream().findFirst();
	}

	private Optional<OrderLineRow> orderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select l.id, l.order_id, l.line_no, l.material_id, l.unit_id, l.quantity, l.received_quantity,
				       l.unit_price, l.expected_arrival_date, l.remark, l.source_requisition_line_id,
				       l.source_quote_line_id, l.price_agreement_line_id, l.tax_rate, l.tax_excluded_unit_price,
				       l.tax_included_unit_price, l.price_source_type, o.purchase_mode, o.project_id
				from proc_purchase_order_line l
				join proc_purchase_order o on o.id = l.order_id
				where l.order_id = ?
				and l.id = ?
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private Optional<OrderLineRow> lockOrderLine(Long orderId, Long lineId) {
		return this.jdbcTemplate.query("""
				select l.id, l.order_id, l.line_no, l.material_id, l.unit_id, l.quantity, l.received_quantity,
				       l.unit_price, l.expected_arrival_date, l.remark, l.source_requisition_line_id,
				       l.source_quote_line_id, l.price_agreement_line_id, l.tax_rate, l.tax_excluded_unit_price,
				       l.tax_included_unit_price, l.price_source_type, o.purchase_mode, o.project_id
				from proc_purchase_order_line l
				join proc_purchase_order o on o.id = l.order_id
				where l.order_id = ?
				and l.id = ?
				for update
				""", this::mapOrderLineRow, orderId, lineId).stream().findFirst();
	}

	private List<OrderLineRow> orderLineRowsForValidation(Long orderId) {
		return this.jdbcTemplate.query("""
				select l.id, l.order_id, l.line_no, l.material_id, l.unit_id, l.quantity, l.received_quantity,
				       l.unit_price, l.expected_arrival_date, l.remark, l.source_requisition_line_id,
				       l.source_quote_line_id, l.price_agreement_line_id, l.tax_rate, l.tax_excluded_unit_price,
				       l.tax_included_unit_price, l.price_source_type, o.purchase_mode, o.project_id
				from proc_purchase_order_line l
				join proc_purchase_order o on o.id = l.order_id
				where l.order_id = ?
				order by line_no asc, id asc
				""", this::mapOrderLineRow, orderId);
	}

	private List<ReceiptLineRow> receiptLineRows(Long receiptId) {
		return this.jdbcTemplate.query("""
				select id, receipt_id, line_no, order_line_id, material_id, unit_id, schedule_id, ordered_quantity,
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
				       l.unit_price, l.expected_arrival_date, l.remark, l.source_requisition_line_id,
				       l.source_quote_line_id, l.price_agreement_line_id, l.tax_rate, l.tax_excluded_unit_price,
				       round(l.quantity * l.tax_excluded_unit_price, 2) as tax_excluded_amount,
				       l.tax_included_unit_price, l.price_source_type, o.purchase_mode, o.project_id,
				       o.currency, p.project_no as project_code, p.name as project_name,
				       case
				           when l.price_source_type in ('QUOTE_SELECTION', 'SUPPLIER_QUOTE', 'QUOTE') then sq.quote_no
				           when l.price_source_type in ('AGREEMENT', 'PRICE_AGREEMENT') then pa.agreement_no
				           when l.price_source_type in ('REQUISITION_APPROVED', 'REQUISITION') then rq.requisition_no
				           else null
				       end as price_source_no
				from proc_purchase_order_line l
				join proc_purchase_order o on o.id = l.order_id
				left join sal_project p on p.id = o.project_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				left join proc_purchase_requisition_line rl on rl.id = l.source_requisition_line_id
				left join proc_purchase_requisition rq on rq.id = rl.requisition_id
				left join proc_supplier_quote_line ql on ql.id = l.source_quote_line_id
				left join proc_supplier_quote sq on sq.id = ql.quote_id
				left join proc_price_agreement_line pal on pal.id = l.price_agreement_line_id
				left join proc_price_agreement pa on pa.id = pal.agreement_id
				where l.order_id = ?
				order by l.line_no asc, l.id asc
				""", (rs, rowNum) -> {
			BigDecimal inTransitQuantity = rs.getBigDecimal("in_transit_quantity");
			LocalDate expectedArrivalDate = rs.getObject("expected_arrival_date", LocalDate.class);
			InTransitStatus inTransitStatus = inTransitStatus(inTransitQuantity, expectedArrivalDate);
			PurchaseMode mode = PurchaseMode.valueOf(rs.getString("purchase_mode"));
			Long requisitionLineId = nullableLong(rs, "source_requisition_line_id");
			Long quoteLineId = nullableLong(rs, "source_quote_line_id");
			Long agreementLineId = nullableLong(rs, "price_agreement_line_id");
			return new PurchaseOrderLineResponse(rs.getLong("id"), rs.getInt("line_no"),
					rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
					rs.getString("material_spec"), rs.getLong("unit_id"), rs.getString("unit_name"),
					decimalString(rs.getBigDecimal("quantity")), decimalString(rs.getBigDecimal("received_quantity")),
					decimalString(rs.getBigDecimal("remaining_quantity")), decimalString(inTransitQuantity),
					inTransitStatus.code(), inTransitStatus.displayName(), decimalString(rs.getBigDecimal("unit_price")),
					expectedArrivalDate, mode, mode.name(), nullableLong(rs, "project_id"),
					rs.getString("project_code"), rs.getString("project_name"), rs.getString("remark"),
					requisitionLineId, quoteLineId, agreementLineId, requisitionLineId, quoteLineId,
					rs.getString("price_source_type"), rs.getString("price_source_no"),
					rs.getString("price_source_no"), rs.getString("currency"), decimalString(rs.getBigDecimal("tax_rate")),
					decimalString(rs.getBigDecimal("tax_excluded_unit_price")),
					decimalString(rs.getBigDecimal("tax_excluded_amount")),
					decimalString(rs.getBigDecimal("tax_included_unit_price")));
		}, orderId);
	}

	private List<PurchaseReceiptSummaryResponse> orderReceipts(Long orderId) {
		return orderReceipts(orderId, null);
	}

	private List<PurchaseReceiptSummaryResponse> orderReceipts(Long orderId, CurrentUser currentUser) {
		return this.jdbcTemplate.query("""
				select r.id, r.receipt_no, r.order_id, o.order_no, r.supplier_id, s.name as supplier_name,
				       r.warehouse_id, w.name as warehouse_name, r.business_date, r.status,
				       (select count(*) from proc_purchase_receipt_line l where l.receipt_id = r.id) as line_count,
				       coalesce((select sum(l.quantity) from proc_purchase_receipt_line l where l.receipt_id = r.id), 0) as total_quantity,
				       o.purchase_mode, o.project_id, p.project_no as project_code, p.name as project_name,
				       %s as valuation_state,
				       coalesce((
				           select sum(round(l.quantity * ol.tax_excluded_unit_price, 2))
				           from proc_purchase_receipt_line l
				           join proc_purchase_order_line ol on ol.id = l.order_line_id
				           where l.receipt_id = r.id
				       ), 0) as tax_excluded_amount,
				       r.remark, r.created_by, r.created_at, r.updated_at, r.posted_by, r.posted_at, r.version
				from proc_purchase_receipt r
				join proc_purchase_order o on o.id = r.order_id
				join mst_supplier s on s.id = r.supplier_id
				join mst_warehouse w on w.id = r.warehouse_id
				left join sal_project p on p.id = o.project_id
				where r.order_id = ?
				order by r.updated_at desc, r.id desc
				""".formatted(receiptValuationStateSql("r")),
				(rs, rowNum) -> mapReceiptSummary(rs, rowNum, currentUser), orderId);
	}

	private List<PurchaseReceiptLineResponse> receiptLines(Long receiptId) {
		return receiptLines(receiptId, false);
	}

	private List<PurchaseReceiptLineResponse> receiptLines(Long receiptId, boolean costVisible) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.order_line_id, l.material_id, m.code as material_code,
				       m.name as material_name, m.tracking_method, l.unit_id, u.name as unit_name, l.schedule_id,
				       s.line_no as schedule_seq, l.cost_layer_id, l.value_movement_id, vm.movement_no as value_movement_no,
				       o.purchase_mode, o.project_id, p.project_no as project_code, p.name as project_name,
				       case when r.status = 'POSTED' then coalesce(vm.valuation_state, 'VALUED') else 'NOT_POSTED' end as valuation_state,
				       l.ordered_quantity,
				       l.received_quantity_before, l.remaining_quantity_before, l.quantity, l.before_quantity,
				       l.after_quantity, ol.tax_excluded_unit_price,
				       round(l.quantity * ol.tax_excluded_unit_price, 2) as tax_excluded_amount,
				       case when o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				            then ol.quantity - ol.received_quantity else 0 end as in_transit_quantity,
				       ol.expected_arrival_date, l.remark
				from proc_purchase_receipt_line l
				join proc_purchase_order_line ol on ol.id = l.order_line_id
				left join proc_purchase_order_schedule s on s.id = l.schedule_id
				left join inv_value_movement vm on vm.id = l.value_movement_id
				join proc_purchase_order o on o.id = ol.order_id
				join proc_purchase_receipt r on r.id = l.receipt_id
				left join sal_project p on p.id = o.project_id
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
					rs.getLong("unit_id"), rs.getString("unit_name"), nullableLong(rs, "schedule_id"),
					nullableInteger(rs, "schedule_seq"),
					costVisible ? nullableLong(rs, "cost_layer_id") : null,
					costVisible ? costLayerNo(nullableLong(rs, "cost_layer_id")) : null,
					costVisible ? rs.getString("value_movement_no") : null, costVisible,
					PurchaseMode.valueOf(rs.getString("purchase_mode")), rs.getString("purchase_mode"),
					nullableLong(rs, "project_id"), rs.getString("project_code"), rs.getString("project_name"),
					rs.getString("valuation_state"), valuationStateName(rs.getString("valuation_state")),
					decimalString(rs.getBigDecimal("ordered_quantity")),
					decimalString(rs.getBigDecimal("received_quantity_before")),
					decimalString(rs.getBigDecimal("remaining_quantity_before")),
					decimalString(rs.getBigDecimal("quantity")),
					costVisible ? decimalString(rs.getBigDecimal("tax_excluded_unit_price")) : null,
					costVisible ? decimalString(rs.getBigDecimal("tax_excluded_amount")) : null,
					decimalString(rs.getBigDecimal("before_quantity")), decimalString(rs.getBigDecimal("after_quantity")),
					decimalString(inTransitQuantity),
					inTransitStatus.code(), inTransitStatus.displayName(), rs.getString("remark"),
					this.inventoryTrackingService.allocationResponses(RECEIPT_SOURCE_TYPE, receiptId,
							rs.getLong("id")));
		},
				receiptId);
	}

	private List<PurchaseReceiptInventoryMovementResponse> receiptInventoryMovements(Long receiptId, boolean costVisible) {
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
				(rs, rowNum) -> new PurchaseReceiptInventoryMovementResponse(costVisible ? rs.getLong("id") : null,
						costVisible ? rs.getString("movement_no") : null, rs.getString("movement_type"), rs.getString("direction"),
						rs.getString("warehouse_name"), rs.getString("material_code"), rs.getString("material_name"),
						decimalString(rs.getBigDecimal("quantity")), decimalString(rs.getBigDecimal("before_quantity")),
						decimalString(rs.getBigDecimal("after_quantity")), rs.getObject("business_date", LocalDate.class),
						rs.getString("operator_name"), rs.getObject("occurred_at", OffsetDateTime.class),
						costVisible ? nullableLong(rs, "batch_id") : null, rs.getString("batch_no"),
						costVisible ? nullableLong(rs, "serial_id") : null,
						rs.getString("serial_no")),
				RECEIPT_SOURCE_TYPE, receiptId);
	}

	private PurchaseOrderScheduleListResponse schedulePage(List<PurchaseOrderScheduleResponse> allItems, int page,
			int pageSize) {
		int safePage = Math.max(page, 1);
		int safePageSize = limit(pageSize);
		int fromIndex = Math.min((safePage - 1) * safePageSize, allItems.size());
		int toIndex = Math.min(fromIndex + safePageSize, allItems.size());
		List<PurchaseOrderScheduleResponse> items = allItems.subList(fromIndex, toIndex);
		int totalPages = (int) Math.ceil((double) allItems.size() / safePageSize);
		return new PurchaseOrderScheduleListResponse(items, safePage, safePageSize, allItems.size(), totalPages);
	}

	private List<PurchaseOrderScheduleResponse> scheduleResponses(Long orderId, String status, LocalDate expectedDateFrom,
			LocalDate expectedDateTo) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("l.order_id = ?");
		args.add(orderId);
		if (hasText(status)) {
			conditions.add("s.status = ?");
			args.add(status.trim());
		}
		if (expectedDateFrom != null) {
			conditions.add("s.planned_date >= ?");
			args.add(expectedDateFrom);
		}
		if (expectedDateTo != null) {
			conditions.add("s.planned_date <= ?");
			args.add(expectedDateTo);
		}
		String where = "where " + String.join(" and ", conditions);
		return this.jdbcTemplate.query("""
				select s.id, o.id as order_id, o.order_no, s.order_line_id, l.material_id,
				       m.code as material_code, m.name as material_name, s.line_no, s.planned_date, s.planned_quantity, s.received_quantity,
				       s.planned_quantity - s.received_quantity as remaining_quantity, s.status, s.version,
				       s.remark, s.closed_reason
				from proc_purchase_order_schedule s
				join proc_purchase_order_line l on l.id = s.order_line_id
				join proc_purchase_order o on o.id = l.order_id
				join mst_material m on m.id = l.material_id
				%s
				order by l.line_no asc, s.line_no asc, s.id asc
				""".formatted(where), this::mapScheduleResponse, args.toArray());
	}

	private Optional<PurchaseOrderScheduleResponse> scheduleResponse(Long scheduleId) {
		return this.jdbcTemplate.query("""
				select s.id, o.id as order_id, o.order_no, s.order_line_id, l.material_id,
				       m.code as material_code, m.name as material_name, s.line_no, s.planned_date, s.planned_quantity, s.received_quantity,
				       s.planned_quantity - s.received_quantity as remaining_quantity, s.status, s.version,
				       s.remark, s.closed_reason
				from proc_purchase_order_schedule s
				join proc_purchase_order_line l on l.id = s.order_line_id
				join proc_purchase_order o on o.id = l.order_id
				join mst_material m on m.id = l.material_id
				where s.id = ?
				""", this::mapScheduleResponse, scheduleId).stream().findFirst();
	}

	private Optional<ScheduleRow> lockSchedule(Long orderId, Long scheduleId) {
		return this.jdbcTemplate.query("""
				select s.id, s.order_line_id, s.line_no, s.planned_date, s.planned_quantity, s.received_quantity,
				       s.status, s.version, s.remark, s.closed_reason
				from proc_purchase_order_schedule s
				join proc_purchase_order_line l on l.id = s.order_line_id
				where l.order_id = ?
				and s.id = ?
				for update
				""", this::mapScheduleRow, orderId, scheduleId).stream().findFirst();
	}

	private Optional<ScheduleRow> lockScheduleByOrderLine(Long orderLineId, Long scheduleId) {
		return this.jdbcTemplate.query("""
				select id, order_line_id, line_no, planned_date, planned_quantity, received_quantity,
				       status, version, remark, closed_reason
				from proc_purchase_order_schedule
				where order_line_id = ?
				and id = ?
				for update
				""", this::mapScheduleRow, orderLineId, scheduleId).stream().findFirst();
	}

	private Optional<ScheduleRow> scheduleByOrderLine(Long orderLineId, Long scheduleId) {
		return this.jdbcTemplate.query("""
				select id, order_line_id, line_no, planned_date, planned_quantity, received_quantity,
				       status, version, remark, closed_reason
				from proc_purchase_order_schedule
				where order_line_id = ?
				and id = ?
				""", this::mapScheduleRow, orderLineId, scheduleId).stream().findFirst();
	}

	private Long earliestReceivableScheduleId(Long orderLineId) {
		return this.jdbcTemplate.query("""
				select id
				from proc_purchase_order_schedule
				where order_line_id = ?
				and status in ('PLANNED', 'PARTIALLY_RECEIVED')
				and planned_quantity > received_quantity
				order by planned_date asc, line_no asc, id asc
				limit 1
				""", (rs, rowNum) -> rs.getLong("id"), orderLineId).stream().findFirst().orElse(null);
	}

	private boolean scheduleBelongsToOrderLine(Long orderLineId, Long scheduleId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from proc_purchase_order_schedule
					where order_line_id = ?
					and id = ?
					and status in ('PLANNED', 'PARTIALLY_RECEIVED')
				)
				""", Boolean.class, orderLineId, scheduleId);
		return Boolean.TRUE.equals(exists);
	}

	private boolean hasReceivedSchedule(Long orderId) {
		Boolean exists = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from proc_purchase_order_schedule s
					join proc_purchase_order_line l on l.id = s.order_line_id
					where l.order_id = ?
					and s.received_quantity > 0
				)
				""", Boolean.class, orderId);
		return Boolean.TRUE.equals(exists);
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

	private String aggregatePriceSourceType(Long orderId) {
		List<String> sourceTypes = this.jdbcTemplate.query("""
				select price_source_type
				from proc_purchase_order_line
				where order_id = ?
				group by price_source_type
				order by price_source_type
				""", (rs, rowNum) -> rs.getString("price_source_type"), orderId)
			.stream()
			.toList();
		if (sourceTypes.isEmpty()) {
			return "MANUAL";
		}
		return sourceTypes.size() == 1 ? sourceTypes.getFirst() : "MIXED";
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
		PurchaseMode mode = PurchaseMode.valueOf(rs.getString("purchase_mode"));
		return new PurchaseOrderSummaryResponse(rs.getLong("id"), rs.getString("order_no"),
				rs.getLong("supplier_id"), rs.getString("supplier_code"), rs.getString("supplier_name"),
				rs.getObject("order_date", LocalDate.class), expectedArrivalDate,
				rs.getString("status"), mode, mode, mode.name(), nullableLong(rs, "project_id"),
				rs.getString("project_code"), rs.getString("project_name"), rs.getString("currency"), rs.getLong("version"),
				rs.getInt("line_count"), decimalString(rs.getBigDecimal("total_quantity")),
				decimalString(rs.getBigDecimal("received_quantity")), decimalString(rs.getBigDecimal("remaining_quantity")),
				decimalString(inTransitQuantity),
				inTransitStatus.code(), inTransitStatus.displayName(), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("confirmed_by"),
				rs.getObject("confirmed_at", OffsetDateTime.class), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class), orderAllowedActions(rs.getString("status")));
	}

	private PurchaseOrderScheduleResponse mapScheduleResponse(ResultSet rs, int rowNum) throws SQLException {
		String status = rs.getString("status");
		return new PurchaseOrderScheduleResponse(rs.getLong("id"), rs.getLong("order_id"), rs.getLong("order_line_id"),
				rs.getString("order_no"), rs.getLong("material_id"), rs.getString("material_code"),
				rs.getString("material_name"), rs.getInt("line_no"), rs.getInt("line_no"),
				rs.getObject("planned_date", LocalDate.class), rs.getObject("planned_date", LocalDate.class),
				decimalString(rs.getBigDecimal("planned_quantity")), decimalString(rs.getBigDecimal("received_quantity")),
				decimalString(rs.getBigDecimal("remaining_quantity")), status, rs.getLong("version"),
				rs.getString("remark"), rs.getString("closed_reason"), scheduleAllowedActions(status));
	}

	private ScheduleRow mapScheduleRow(ResultSet rs, int rowNum) throws SQLException {
		return new ScheduleRow(rs.getLong("id"), rs.getLong("order_line_id"), rs.getInt("line_no"),
				rs.getObject("planned_date", LocalDate.class), rs.getBigDecimal("planned_quantity"),
				rs.getBigDecimal("received_quantity"), rs.getString("status"), rs.getLong("version"),
				rs.getString("remark"), rs.getString("closed_reason"));
	}

	private PurchaseReceiptSummaryResponse mapReceiptSummary(ResultSet rs, int rowNum) throws SQLException {
		return mapReceiptSummary(rs, rowNum, null);
	}

	private PurchaseReceiptSummaryResponse mapReceiptSummary(ResultSet rs, int rowNum, CurrentUser currentUser)
			throws SQLException {
		String status = rs.getString("status");
		PurchaseMode mode = PurchaseMode.valueOf(rs.getString("purchase_mode"));
		boolean costVisible = canViewValuation(currentUser);
		String valuationState = rs.getString("valuation_state");
		return new PurchaseReceiptSummaryResponse(rs.getLong("id"), rs.getString("receipt_no"),
				rs.getLong("order_id"), rs.getString("order_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
				rs.getObject("business_date", LocalDate.class), status, rs.getLong("version"),
				rs.getInt("line_count"), decimalString(rs.getBigDecimal("total_quantity")), mode, mode.name(),
				nullableLong(rs, "project_id"), rs.getString("project_code"), rs.getString("project_name"),
				valuationState, valuationStateName(valuationState), costVisible,
				costVisible ? decimalString(rs.getBigDecimal("tax_excluded_amount")) : null,
				rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("posted_by"), rs.getObject("posted_at", OffsetDateTime.class), receiptAllowedActions(status));
	}

	private OrderRow mapOrderRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getLong("supplier_id"),
				rs.getObject("order_date", LocalDate.class), rs.getObject("expected_arrival_date", LocalDate.class),
				PurchaseOrderStatus.valueOf(rs.getString("status")),
				PurchaseMode.valueOf(rs.getString("purchase_mode")), nullableLong(rs, "project_id"),
				rs.getString("currency"), rs.getString("remark"), rs.getLong("version"));
	}

	private ReceiptRow mapReceiptRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptRow(rs.getLong("id"), rs.getString("receipt_no"), rs.getLong("order_id"),
				rs.getLong("supplier_id"), rs.getLong("warehouse_id"), rs.getObject("business_date", LocalDate.class),
				PurchaseReceiptStatus.valueOf(rs.getString("status")), rs.getString("remark"), rs.getLong("version"));
	}

	private OrderLineRow mapOrderLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new OrderLineRow(rs.getLong("id"), rs.getLong("order_id"), rs.getInt("line_no"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("received_quantity"), rs.getBigDecimal("unit_price"),
				rs.getObject("expected_arrival_date", LocalDate.class), rs.getString("remark"),
				nullableLong(rs, "source_requisition_line_id"), nullableLong(rs, "source_quote_line_id"),
				nullableLong(rs, "price_agreement_line_id"), rs.getBigDecimal("tax_rate"),
				rs.getBigDecimal("tax_excluded_unit_price"), rs.getBigDecimal("tax_included_unit_price"),
				rs.getString("price_source_type"), PurchaseMode.valueOf(rs.getString("purchase_mode")),
				nullableLong(rs, "project_id"));
	}

	private ReceiptLineRow mapReceiptLineRow(ResultSet rs, int rowNum) throws SQLException {
		return new ReceiptLineRow(rs.getLong("id"), rs.getLong("receipt_id"), rs.getInt("line_no"),
				rs.getLong("order_line_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				nullableLong(rs, "schedule_id"), rs.getBigDecimal("ordered_quantity"), rs.getBigDecimal("received_quantity_before"),
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

	private PurchaseMode parsePurchaseMode(String value) {
		try {
			return value == null ? PurchaseMode.PUBLIC : PurchaseMode.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
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

	private List<String> scheduleAllowedActions(String status) {
		return switch (status) {
			case "PLANNED", "PARTIALLY_RECEIVED" -> List.of("UPDATE", "CLOSE");
			case "RECEIVED", "CLOSED" -> List.of();
			default -> List.of();
		};
	}

	private List<String> receiptAllowedActions(String status) {
		return switch (status) {
			case "DRAFT" -> List.of("UPDATE", "POST");
			case "POSTED" -> List.of();
			default -> List.of();
		};
	}

	private List<String> receiptAllowedActions(String status, CurrentUser currentUser) {
		List<String> actions = receiptAllowedActions(status);
		if (currentUser == null) {
			return actions;
		}
		return actions.stream().filter((action) -> switch (action) {
			case "UPDATE" -> currentUser.permissions().contains("procurement:receipt:update");
			case "POST" -> currentUser.permissions().contains("procurement:receipt:post");
			default -> true;
		}).toList();
	}

	private boolean canViewValuation(CurrentUser currentUser) {
		return currentUser != null && currentUser.permissions().contains("inventory:valuation:view");
	}

	private String receiptValuationStateSql(String receiptAlias) {
		return """
				case when %s.status = 'POSTED' then coalesce((
				    select min(vm.valuation_state)
				    from inv_value_movement vm
				    where vm.source_type = 'PURCHASE_RECEIPT'
				    and vm.source_id = %s.id
				), 'VALUED') else 'NOT_POSTED' end
				""".formatted(receiptAlias, receiptAlias);
	}

	private String valuationStateName(String state) {
		if (state == null) {
			return null;
		}
		return switch (state) {
			case "VALUED" -> "已估值";
			case "LEGACY_UNVALUED" -> "历史未估值";
			case "NON_VALUED" -> "无需计价";
			case "MANUAL_PROVISIONAL" -> "手工暂估";
			case "CURRENT_AVERAGE_PROVISIONAL" -> "当前平均暂估";
			case "NOT_POSTED" -> "未过账";
			default -> state;
		};
	}

	private String costLayerNo(Long costLayerId) {
		return costLayerId == null ? null : "PCL-" + costLayerId;
	}

	private List<String> orderAllowedActions(String status) {
		return switch (status) {
			case "DRAFT" -> List.of("UPDATE", "CONFIRM", "CANCEL");
			case "CONFIRMED", "PARTIALLY_RECEIVED" -> List.of("CREATE_RECEIPT", "UPDATE_SCHEDULES", "CLOSE", "PRINT");
			case "RECEIVED", "CLOSED" -> List.of("PRINT");
			case "CANCELLED" -> List.of();
			default -> List.of();
		};
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String decimalString(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public static final class PurchaseOrderLineRequest {

		@NotNull
		public Integer lineNo;

		@NotNull
		public Long materialId;

		public Long unitId;

		@NotNull
		public BigDecimal quantity;

		public BigDecimal unitPrice;

		public LocalDate expectedArrivalDate;

		public String remark;

		public Long sourceRequisitionLineId;

		public Long requisitionLineId;

		public Long sourceQuoteLineId;

		public Long quoteLineId;

		public Long priceSelectionId;

		public Long priceAgreementLineId;

		public String priceSourceType;

		public BigDecimal taxRate;

		public BigDecimal taxExcludedUnitPrice;

		public BigDecimal taxIncludedUnitPrice;

		@Valid
		public List<PurchaseOrderScheduleRequest> schedules;

		public PurchaseOrderLineRequest() {
		}

		public PurchaseOrderLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, Long unitId,
				@NotNull BigDecimal quantity, BigDecimal unitPrice, LocalDate expectedArrivalDate, String remark,
				Long sourceRequisitionLineId, Long requisitionLineId, Long sourceQuoteLineId, Long quoteLineId,
				Long priceSelectionId, Long priceAgreementLineId, String priceSourceType, BigDecimal taxRate,
				BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice,
				List<PurchaseOrderScheduleRequest> schedules) {
			this.lineNo = lineNo;
			this.materialId = materialId;
			this.unitId = unitId;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.expectedArrivalDate = expectedArrivalDate;
			this.remark = remark;
			this.sourceRequisitionLineId = sourceRequisitionLineId;
			this.requisitionLineId = requisitionLineId;
			this.sourceQuoteLineId = sourceQuoteLineId;
			this.quoteLineId = quoteLineId;
			this.priceSelectionId = priceSelectionId;
			this.priceAgreementLineId = priceAgreementLineId;
			this.priceSourceType = priceSourceType;
			this.taxRate = taxRate;
			this.taxExcludedUnitPrice = taxExcludedUnitPrice;
			this.taxIncludedUnitPrice = taxIncludedUnitPrice;
			this.schedules = schedules;
		}

		public PurchaseOrderLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, Long unitId,
				@NotNull BigDecimal quantity, @NotNull BigDecimal unitPrice, LocalDate expectedArrivalDate,
				String remark) {
			this(lineNo, materialId, unitId, quantity, unitPrice, expectedArrivalDate, remark, null, null, null, null,
					null, null, null, null, null, null, null);
		}

		public void setLineNo(Integer lineNo) {
			this.lineNo = lineNo;
		}

		public void setMaterialId(Long materialId) {
			this.materialId = materialId;
		}

		public void setUnitId(Long unitId) {
			this.unitId = unitId;
		}

		public void setQuantity(BigDecimal quantity) {
			this.quantity = quantity;
		}

		public void setUnitPrice(BigDecimal unitPrice) {
			this.unitPrice = unitPrice;
		}

		public void setExpectedArrivalDate(LocalDate expectedArrivalDate) {
			this.expectedArrivalDate = expectedArrivalDate;
		}

		public void setRemark(String remark) {
			this.remark = remark;
		}

		public void setSourceRequisitionLineId(Long sourceRequisitionLineId) {
			this.sourceRequisitionLineId = sourceRequisitionLineId;
		}

		public void setRequisitionLineId(Long requisitionLineId) {
			this.requisitionLineId = requisitionLineId;
		}

		public void setSourceQuoteLineId(Long sourceQuoteLineId) {
			this.sourceQuoteLineId = sourceQuoteLineId;
		}

		public void setQuoteLineId(Long quoteLineId) {
			this.quoteLineId = quoteLineId;
		}

		public void setPriceSelectionId(Long priceSelectionId) {
			this.priceSelectionId = priceSelectionId;
		}

		public void setPriceAgreementLineId(Long priceAgreementLineId) {
			this.priceAgreementLineId = priceAgreementLineId;
		}

		public void setPriceSourceType(String priceSourceType) {
			this.priceSourceType = priceSourceType;
		}

		public void setTaxRate(BigDecimal taxRate) {
			this.taxRate = taxRate;
		}

		public void setTaxExcludedUnitPrice(BigDecimal taxExcludedUnitPrice) {
			this.taxExcludedUnitPrice = taxExcludedUnitPrice;
		}

		public void setTaxIncludedUnitPrice(BigDecimal taxIncludedUnitPrice) {
			this.taxIncludedUnitPrice = taxIncludedUnitPrice;
		}

		public void setSchedules(List<PurchaseOrderScheduleRequest> schedules) {
			this.schedules = schedules;
		}

		public Integer lineNo() {
			return this.lineNo;
		}

		public Long materialId() {
			return this.materialId;
		}

		public Long unitId() {
			return this.unitId;
		}

		public BigDecimal quantity() {
			return this.quantity;
		}

		public BigDecimal unitPrice() {
			return this.unitPrice;
		}

		public LocalDate expectedArrivalDate() {
			return this.expectedArrivalDate;
		}

		public String remark() {
			return this.remark;
		}

		public Long sourceRequisitionLineId() {
			return this.sourceRequisitionLineId == null ? this.requisitionLineId : this.sourceRequisitionLineId;
		}

		public Long sourceQuoteLineId() {
			return this.sourceQuoteLineId == null ? this.quoteLineId : this.sourceQuoteLineId;
		}

		public Long priceSelectionId() {
			return this.priceSelectionId;
		}

		public Long priceAgreementLineId() {
			return this.priceAgreementLineId;
		}

		public String priceSourceType() {
			return this.priceSourceType;
		}

		public BigDecimal taxRate() {
			return this.taxRate;
		}

		public BigDecimal taxExcludedUnitPrice() {
			return this.taxExcludedUnitPrice;
		}

		public BigDecimal taxIncludedUnitPrice() {
			return this.taxIncludedUnitPrice;
		}

		public List<PurchaseOrderScheduleRequest> schedules() {
			return this.schedules;
		}
	}

	public record PurchaseOrderScheduleRequest(Integer lineNo, Integer scheduleNo, @NotNull LocalDate plannedDate,
			@NotNull BigDecimal plannedQuantity, String remark) {

		@Override
		public Integer lineNo() {
			return lineNo == null ? scheduleNo : lineNo;
		}
	}

	public record PurchaseOrderScheduleUpdateLineRequest(@NotNull Long orderLineId, Integer lineNo, Integer scheduleNo,
			@NotNull LocalDate plannedDate, @NotNull BigDecimal plannedQuantity, String remark) {

		@Override
		public Integer lineNo() {
			return lineNo == null ? scheduleNo : lineNo;
		}
	}

	public record PurchaseOrderScheduleUpdateRequest(Long version, String idempotencyKey,
			@Valid List<PurchaseOrderScheduleUpdateLineRequest> lines) {
	}

	public record PurchaseOrderScheduleSingleUpdateRequest(Long version, Integer lineNo, Integer scheduleSeq,
			LocalDate plannedDate, LocalDate expectedArrivalDate, BigDecimal plannedQuantity, String remark) {

		@Override
		public Integer lineNo() {
			return lineNo == null ? scheduleSeq : lineNo;
		}

		@Override
		public LocalDate plannedDate() {
			return plannedDate == null ? expectedArrivalDate : plannedDate;
		}
	}

	public record PurchaseOrderRequest(@NotNull Long supplierId, @NotNull LocalDate orderDate,
			LocalDate expectedArrivalDate, String remark, String purchaseMode, String procurementMode,
			String ownershipType, Long projectId, String currency, String publicDirectReason, String directPurchaseReason,
			String exceptionReason, String priceSourceType, Long version, String idempotencyKey,
			@Valid List<PurchaseOrderLineRequest> lines) {

		public PurchaseOrderRequest(@NotNull Long supplierId, @NotNull LocalDate orderDate,
				LocalDate expectedArrivalDate, String remark, @Valid List<PurchaseOrderLineRequest> lines) {
			this(supplierId, orderDate, expectedArrivalDate, remark, null, null, null, null, null, null, null, null,
					null, null, null, lines);
		}

		@Override
		public String purchaseMode() {
			if (purchaseMode != null && !purchaseMode.isBlank()) {
				return purchaseMode;
			}
			if (procurementMode != null && !procurementMode.isBlank()) {
				return procurementMode;
			}
			return ownershipType;
		}

		@Override
		public String publicDirectReason() {
			return publicDirectReason == null ? directPurchaseReason : publicDirectReason;
		}
	}

	public record PurchaseReceiptLineRequest(@NotNull Integer lineNo, @NotNull Long orderLineId, Long materialId,
			Long unitId, Long scheduleId, @NotNull BigDecimal quantity, String remark,
			@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {

		public PurchaseReceiptLineRequest(@NotNull Integer lineNo, @NotNull Long orderLineId, Long materialId,
				Long unitId, @NotNull BigDecimal quantity, String remark,
				@Valid List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
			this(lineNo, orderLineId, materialId, unitId, null, quantity, remark, trackingAllocations);
		}
	}

	public record PurchaseReceiptRequest(@NotNull Long warehouseId, @NotNull LocalDate businessDate, String remark,
			Long version, String idempotencyKey, @Valid List<PurchaseReceiptLineRequest> lines) {

		public PurchaseReceiptRequest(@NotNull Long warehouseId, @NotNull LocalDate businessDate, String remark,
				@Valid List<PurchaseReceiptLineRequest> lines) {
			this(warehouseId, businessDate, remark, null, null, lines);
		}
	}

	public record VersionedActionRequest(Long version, String reason, String idempotencyKey) {
	}

	public record PurchaseOrderSummaryResponse(Long id, String orderNo, Long supplierId, String supplierCode,
			String supplierName, LocalDate orderDate, LocalDate expectedArrivalDate, String status,
			PurchaseMode purchaseMode, PurchaseMode procurementMode, String ownershipType, Long projectId,
			String projectCode, String projectName, String currency, Long version, int lineCount, String totalQuantity,
			String receivedQuantity, String remainingQuantity,
			String inTransitQuantity, String inTransitStatus, String inTransitStatusName, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt, List<String> allowedActions) {
	}

	public record PurchaseOrderLineResponse(Long id, Integer lineNo, Long materialId, String materialCode,
			String materialName, String materialSpec, Long unitId, String unitName, String quantity,
			String receivedQuantity, String remainingQuantity, String inTransitQuantity,
			String inTransitStatus, String inTransitStatusName, String unitPrice, LocalDate expectedArrivalDate,
			PurchaseMode procurementMode, String ownershipType, Long projectId, String projectCode, String projectName,
			String remark, Long requisitionLineId, Long quoteLineId, Long priceAgreementLineId,
			Long sourceRequisitionLineId, Long sourceQuoteLineId,
			String priceSourceType, String sourceNo, String priceSourceNo, String currency, String taxRate,
			String taxExcludedUnitPrice, String taxExcludedAmount, String taxIncludedUnitPrice) {
	}

	public record PurchaseOrderDetailResponse(Long id, String orderNo, Long supplierId, String supplierCode,
			String supplierName, LocalDate orderDate, LocalDate expectedArrivalDate, String status,
			PurchaseMode purchaseMode, PurchaseMode procurementMode, String ownershipType, String priceSourceType,
			Boolean exceptionApprovalRequired, Long exceptionApprovalInstanceId, String exceptionApprovalStatus,
			String exceptionReason, Long projectId, String projectCode, String projectName, String currency,
			Long version, int lineCount, String totalQuantity,
			String receivedQuantity, String remainingQuantity,
			String inTransitQuantity, String inTransitStatus, String inTransitStatusName, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String confirmedByName,
			OffsetDateTime confirmedAt, String cancelledByName, OffsetDateTime cancelledAt, String closedByName,
			OffsetDateTime closedAt, List<String> allowedActions, List<PurchaseOrderLineResponse> lines,
			List<PurchaseReceiptSummaryResponse> receipts) {
	}

	public record PurchaseReceiptSummaryResponse(Long id, String receiptNo, Long orderId, String orderNo,
			Long supplierId, String supplierName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, Long version, int lineCount, String totalQuantity, PurchaseMode procurementMode,
			String ownershipType, Long projectId, String projectCode, String projectName, String valuationState,
			String valuationStateName, Boolean costVisible, String taxExcludedAmount, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			List<String> allowedActions) {
	}

	public record PurchaseReceiptLineResponse(Long id, Integer lineNo, Long orderLineId, Long materialId,
			String materialCode, String materialName, String trackingMethod, String trackingMethodName, Long unitId,
			String unitName, Long scheduleId, Integer scheduleSeq, Long costLayerId, String costLayerNo,
			String valueMovementNo, Boolean costVisible, PurchaseMode procurementMode, String ownershipType,
			Long projectId, String projectCode, String projectName, String valuationState, String valuationStateName,
			String orderedQuantity,
			String receivedQuantityBefore,
			String remainingQuantityBefore, String quantity, String taxExcludedUnitPrice,
			String taxExcludedAmount, String beforeQuantity,
			String afterQuantity, String inTransitQuantity, String inTransitStatus,
			String inTransitStatusName, String remark,
			List<InventoryTrackingService.TrackingAllocationResponse> trackingAllocations) {
	}

	public record PurchaseReceiptInventoryMovementResponse(Long id, String movementNo, String movementType,
			String direction, String warehouseName, String materialCode, String materialName, String quantity,
			String beforeQuantity, String afterQuantity, LocalDate businessDate, String operatorName,
			OffsetDateTime occurredAt, Long batchId, String batchNo, Long serialId, String serialNo) {
	}

	public record PurchaseReceiptDetailResponse(Long id, String receiptNo, Long orderId, String orderNo,
			Long supplierId, String supplierName, Long warehouseId, String warehouseName, LocalDate businessDate,
			String status, Long version, int lineCount, String totalQuantity, PurchaseMode procurementMode,
			String ownershipType, Long projectId, String projectCode, String projectName, String valuationState,
			String valuationStateName, Boolean costVisible, String taxExcludedAmount, String remark, String createdByName,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, String postedByName, OffsetDateTime postedAt,
			List<PurchaseReceiptLineResponse> lines, List<String> allowedActions,
			PurchaseOrderSummaryResponse orderSummary, List<PurchaseReceiptInventoryMovementResponse> inventoryMovements) {
	}

	public record PurchaseOrderScheduleListResponse(List<PurchaseOrderScheduleResponse> items, int page, int pageSize,
			long total, int totalPages) {
	}

	public record PurchaseOrderScheduleResponse(Long id, Long orderId, Long orderLineId, String orderNo,
			Long materialId, String materialCode, String materialName, Integer lineNo, Integer scheduleSeq,
			LocalDate expectedArrivalDate, LocalDate plannedDate, String plannedQuantity, String receivedQuantity,
			String remainingQuantity, String status, Long version, String remark, String closeReason,
			List<String> allowedActions) {

		public String closedReason() {
			return closeReason;
		}
	}

	private record ValidatedOrder(SupplierRef supplier, LocalDate orderDate, LocalDate expectedArrivalDate,
			PurchaseMode purchaseMode, Long projectId, String currency, String publicDirectReason,
			String exceptionReason, String remark, List<ValidatedOrderLine> lines) {
	}

	private record ValidatedOrderLine(Integer lineNo, Long materialId, Long unitId, BigDecimal quantity,
			BigDecimal unitPrice, LocalDate expectedArrivalDate, String remark, Long sourceRequisitionLineId,
			Long sourceQuoteLineId, Long priceAgreementLineId, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			BigDecimal taxIncludedUnitPrice, String requestedPriceSourceType, List<ValidatedSchedule> schedules) {

		private String priceSourceType() {
			if (requestedPriceSourceType != null && !requestedPriceSourceType.isBlank()) {
				return normalizePriceSourceType(requestedPriceSourceType);
			}
			if (this.priceAgreementLineId != null) {
				return "AGREEMENT";
			}
			if (this.sourceQuoteLineId != null) {
				return "QUOTE_SELECTION";
			}
			if (this.sourceRequisitionLineId != null) {
				return "REQUISITION_APPROVED";
			}
			return "MANUAL";
		}

		private static String normalizePriceSourceType(String value) {
			String normalized = value.trim().toUpperCase();
			return switch (normalized) {
				case "LOWEST_QUOTE", "NON_LOWEST_QUOTE", "QUOTE_SELECTION" -> "QUOTE_SELECTION";
				case "PRICE_AGREEMENT", "AGREEMENT" -> "AGREEMENT";
				case "REQUISITION_APPROVED", "PUBLIC_DIRECT", "MANUAL" -> normalized;
				default -> throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_SOURCE_INVALID);
			};
		}
	}

	private record ValidatedSchedule(Integer lineNo, LocalDate plannedDate, BigDecimal plannedQuantity, String remark) {
	}

	private record ValidatedReceipt(Long warehouseId, LocalDate businessDate, String remark,
			List<ValidatedReceiptLine> lines) {
	}

	private record ValidatedReceiptLine(Integer lineNo, Long orderLineId, Long materialId, Long unitId,
			BigDecimal orderedQuantity, BigDecimal receivedQuantityBefore, BigDecimal remainingQuantityBefore,
			Long scheduleId, BigDecimal quantity, PurchaseMode purchaseMode, Long projectId, String remark,
			List<InventoryTrackingService.TrackingAllocationRequest> trackingAllocations) {
	}

	private record ValidatedScheduleUpdate(Long orderLineId, Integer lineNo, LocalDate plannedDate,
			BigDecimal plannedQuantity, String remark) {
	}

	private record OrderRow(Long id, String orderNo, Long supplierId, LocalDate orderDate,
			LocalDate expectedArrivalDate, PurchaseOrderStatus status, PurchaseMode purchaseMode, Long projectId,
			String currency, String remark, Long version) {
	}

	private record OrderExceptionState(boolean exceptionApprovalRequired, Long exceptionApprovalInstanceId,
			String exceptionApprovalStatus, String exceptionReason) {
	}

	private record OrderLineRow(Long id, Long orderId, Integer lineNo, Long materialId, Long unitId,
			BigDecimal quantity, BigDecimal receivedQuantity, BigDecimal unitPrice, LocalDate expectedArrivalDate,
			String remark, Long sourceRequisitionLineId, Long sourceQuoteLineId, Long priceAgreementLineId,
			BigDecimal taxRate, BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice,
			String priceSourceType, PurchaseMode purchaseMode, Long projectId) {
	}

	private record ReceiptRow(Long id, String receiptNo, Long orderId, Long supplierId, Long warehouseId,
			LocalDate businessDate, PurchaseReceiptStatus status, String remark, Long version) {
	}

	private record ReceiptLineRow(Long id, Long receiptId, Integer lineNo, Long orderLineId, Long materialId,
			Long unitId, Long scheduleId, BigDecimal orderedQuantity, BigDecimal receivedQuantityBefore,
			BigDecimal remainingQuantityBefore, BigDecimal quantity, BigDecimal beforeQuantity,
			BigDecimal afterQuantity, String remark) {
	}

	private record ScheduleRow(Long id, Long orderLineId, Integer lineNo, LocalDate plannedDate,
			BigDecimal plannedQuantity, BigDecimal receivedQuantity, String status, Long version, String remark,
			String closedReason) {
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
