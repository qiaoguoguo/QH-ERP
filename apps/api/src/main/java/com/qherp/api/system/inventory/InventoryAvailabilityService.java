package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryAvailabilityService {

	public static final String SALES_ORDER_SOURCE = "SALES_ORDER";

	public static final String PRODUCTION_WORK_ORDER_SOURCE = "PRODUCTION_WORK_ORDER";

	private static final String TARGET_TYPE = "INVENTORY_RESERVATION";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger RESERVATION_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public InventoryAvailabilityService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public BigDecimal availableQualifiedQuantity(Long warehouseId, Long materialId) {
		BigDecimal qualified = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and ownership_type = 'PUBLIC'
				and project_id is null
				and cost_layer_id is null
				""", BigDecimal.class, warehouseId, materialId);
		return nullToZero(qualified).subtract(activeLockedQuantity(warehouseId, materialId, null));
	}

	@Transactional(readOnly = true)
	public BigDecimal activeLockedQuantity(Long warehouseId, Long materialId, InventoryReservationType type) {
		String typeCondition = type == null ? "" : " and reservation_type = ?";
		Object[] args = type == null ? new Object[] { warehouseId, materialId }
				: new Object[] { warehouseId, materialId, type.name() };
		BigDecimal quantity = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity - released_quantity - consumed_quantity), 0)
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and ownership_type = 'PUBLIC'
				and project_id is null
				and cost_layer_id is null
				and status = 'ACTIVE'
				%s
				""".formatted(typeCondition), BigDecimal.class, args);
		return nullToZero(quantity);
	}

	@Transactional
	public Optional<Long> tryReserveFromWarehouse(ReservationCommand command, CurrentUser operator,
			HttpServletRequest request) {
		try {
			return Optional.of(reserveFromWarehouse(command, operator, request));
		}
		catch (BusinessException exception) {
			if (exception.errorCode() == ApiErrorCode.INVENTORY_AVAILABLE_NOT_ENOUGH
					|| exception.errorCode() == ApiErrorCode.INVENTORY_ATP_NOT_ENOUGH) {
				return Optional.empty();
			}
			throw exception;
		}
	}

	@Transactional
	public Optional<Long> tryReserveFromAnyWarehouse(ReservationCommand command, CurrentUser operator,
			HttpServletRequest request) {
		validateReservationCommand(command);
		for (WarehouseAvailability candidate : warehouseCandidates(command)) {
			if (candidate.availableQuantity().compareTo(command.quantity()) >= 0) {
				ReservationCommand warehouseCommand = command.withWarehouseId(candidate.warehouseId());
				return tryReserveFromWarehouse(warehouseCommand, operator, request);
			}
		}
		return Optional.empty();
	}

	@Transactional
	public long reserveFromWarehouse(ReservationCommand command, CurrentUser operator, HttpServletRequest request) {
		validateReservationCommand(command);
		validateReservationIdentity(command);
		OffsetDateTime now = OffsetDateTime.now();
		BalanceLock balance = lockQualifiedBalance(command)
			.orElse(new BalanceLock(null, ZERO));
		BigDecimal availableQuantity = balance.quantityOnHand()
			.subtract(activeLockedQuantityForUpdate(command));
		if (availableQuantity.compareTo(command.quantity()) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_AVAILABLE_NOT_ENOUGH);
		}
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into inv_stock_reservation (
						reservation_no, reservation_type, status, warehouse_id, material_id, unit_id, quality_status,
						quantity, released_quantity, consumed_quantity, source_type, source_id, source_line_id,
						source_document_no, business_date, reason, remark, created_by, created_at, updated_by,
						updated_at, ownership_type, project_id, cost_layer_id
					)
					values (?, ?, 'ACTIVE', ?, ?, ?, 'QUALIFIED', ?, 0, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, reservationNo(command.reservationType()), command.reservationType().name(),
					command.warehouseId(), command.materialId(), command.unitId(), command.quantity(),
					command.sourceType(), command.sourceId(), command.sourceLineId(), command.sourceDocumentNo(),
					command.businessDate(), command.reason(), blankToNull(command.remark()), operator.username(), now,
					operator.username(), now, command.ownershipType(), command.projectId(), command.costLayerId());
			adjustLockedQuantity(command.warehouseId(), command.materialId(), command.ownershipType(),
					command.projectId(), command.costLayerId(), command.quantity(), now);
			this.auditService.record(operator, createAction(command.reservationType()), TARGET_TYPE, id,
					command.sourceDocumentNo(), request);
			return id;
		}
		catch (DuplicateKeyException exception) {
			if (containsConstraint(exception, "uk_inv_stock_reservation_active_source")
					|| containsConstraint(exception, "uk_inv_stock_reservation_no")) {
				throw new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_SOURCE_DUPLICATED);
			}
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional
	public boolean consumeBySourceLine(InventoryReservationType reservationType, String sourceType, Long sourceLineId,
			BigDecimal quantity, CurrentUser operator, HttpServletRequest request) {
		validateQuantity(quantity);
		Optional<ReservationLock> reservation = lockActiveReservation(reservationType, sourceType, sourceLineId);
		if (reservation.isEmpty()) {
			return false;
		}
		ReservationLock current = reservation.get();
		BigDecimal activeQuantity = current.activeQuantity();
		if (activeQuantity.compareTo(quantity) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		BigDecimal consumedQuantity = current.consumedQuantity().add(quantity);
		InventoryReservationStatus nextStatus = activeQuantity.compareTo(quantity) == 0
				? InventoryReservationStatus.CONSUMED : InventoryReservationStatus.ACTIVE;
		this.jdbcTemplate.update("""
				update inv_stock_reservation
				set status = ?, consumed_quantity = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", nextStatus.name(), consumedQuantity, operator.username(), now, current.id());
		adjustLockedQuantity(current.warehouseId(), current.materialId(), current.ownershipType(), current.projectId(),
				current.costLayerId(), quantity.negate(), now);
		this.auditService.record(operator, consumeAction(reservationType), TARGET_TYPE, current.id(),
				current.sourceDocumentNo(), request);
		return true;
	}

	@Transactional
	public void releaseBySource(InventoryReservationType reservationType, String sourceType, Long sourceId,
			CurrentUser operator, HttpServletRequest request) {
		List<ReservationLock> reservations = this.jdbcTemplate.query("""
				select id, reservation_type, warehouse_id, material_id, quantity, released_quantity,
				       consumed_quantity, source_document_no, ownership_type, project_id, cost_layer_id
				from inv_stock_reservation
				where reservation_type = ?
				and source_type = ?
				and source_id = ?
				and status = 'ACTIVE'
				for update
				""", this::mapReservationLock, reservationType.name(), sourceType, sourceId);
		releaseReservations(reservations, operator, request);
	}

	@Transactional
	public void releaseBySourceLine(InventoryReservationType reservationType, String sourceType, Long sourceLineId,
			CurrentUser operator, HttpServletRequest request) {
		List<ReservationLock> reservations = this.jdbcTemplate.query("""
				select id, reservation_type, warehouse_id, material_id, quantity, released_quantity,
				       consumed_quantity, source_document_no, ownership_type, project_id, cost_layer_id
				from inv_stock_reservation
				where reservation_type = ?
				and source_type = ?
				and source_line_id = ?
				and status = 'ACTIVE'
				for update
				""", this::mapReservationLock, reservationType.name(), sourceType, sourceLineId);
		releaseReservations(reservations, operator, request);
	}

	@Transactional
	public void assertQualifiedAvailable(Long warehouseId, Long materialId, BigDecimal quantity,
			ApiErrorCode errorCode) {
		validateQuantity(quantity);
		BalanceLock balance = lockQualifiedPublicBalance(warehouseId, materialId).orElse(new BalanceLock(null, ZERO));
		BigDecimal availableQuantity = balance.quantityOnHand()
			.subtract(activeLockedPublicQuantityForUpdate(warehouseId, materialId));
		if (availableQuantity.compareTo(quantity) < 0) {
			throw new BusinessException(errorCode);
		}
	}

	@Transactional(readOnly = true)
	public BigDecimal materialInTransitQuantity(Long materialId) {
		BigDecimal quantity = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(pol.quantity - pol.received_quantity), 0)
				from proc_purchase_order_line pol
				join proc_purchase_order po on po.id = pol.order_id
				where pol.material_id = ?
				and po.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
				""", BigDecimal.class, materialId);
		return nullToZero(quantity);
	}

	private void releaseReservations(List<ReservationLock> reservations, CurrentUser operator,
			HttpServletRequest request) {
		if (reservations.isEmpty()) {
			return;
		}
		OffsetDateTime now = OffsetDateTime.now();
		for (ReservationLock reservation : reservations) {
			BigDecimal activeQuantity = reservation.activeQuantity();
			if (activeQuantity.compareTo(ZERO) <= 0) {
				continue;
			}
			BigDecimal releasedQuantity = reservation.releasedQuantity().add(activeQuantity);
			this.jdbcTemplate.update("""
					update inv_stock_reservation
					set status = 'RELEASED', released_quantity = ?, released_by = ?, released_at = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", releasedQuantity, operator.username(), now, operator.username(), now, reservation.id());
			adjustLockedQuantity(reservation.warehouseId(), reservation.materialId(), reservation.ownershipType(),
					reservation.projectId(), reservation.costLayerId(), activeQuantity.negate(), now);
			this.auditService.record(operator, releaseAction(reservation.reservationType()), TARGET_TYPE,
					reservation.id(), reservation.sourceDocumentNo(), request);
		}
	}

	private Optional<ReservationLock> lockActiveReservation(InventoryReservationType reservationType, String sourceType,
			Long sourceLineId) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, quantity, released_quantity,
					       consumed_quantity, source_document_no, ownership_type, project_id, cost_layer_id
					from inv_stock_reservation
					where reservation_type = ?
					and source_type = ?
					and source_line_id = ?
					and status = 'ACTIVE'
					for update
					""", this::mapReservationLock, reservationType.name(), sourceType, sourceLineId)
			.stream()
			.findFirst();
	}

	private ReservationLock mapReservationLock(ResultSet rs, int rowNum) throws SQLException {
		return new ReservationLock(rs.getLong("id"),
				InventoryReservationType.valueOf(rs.getString("reservation_type")), rs.getLong("warehouse_id"),
				rs.getLong("material_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("released_quantity"),
				rs.getBigDecimal("consumed_quantity"), rs.getString("source_document_no"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), nullableLong(rs, "cost_layer_id"));
	}

	private List<WarehouseAvailability> warehouseCandidates(ReservationCommand command) {
		return this.jdbcTemplate.query("""
				select sb.warehouse_id,
				       sb.quantity_on_hand - coalesce(r.locked_quantity, 0) as available_quantity
				from inv_stock_balance sb
				join mst_warehouse w on w.id = sb.warehouse_id
				left join (
					select warehouse_id, material_id, ownership_type, project_id, cost_layer_id,
					       sum(quantity - released_quantity - consumed_quantity) as locked_quantity
					from inv_stock_reservation
					where status = 'ACTIVE'
					and quality_status = 'QUALIFIED'
					group by warehouse_id, material_id, ownership_type, project_id, cost_layer_id
				) r on r.warehouse_id = sb.warehouse_id and r.material_id = sb.material_id
					and r.ownership_type = sb.ownership_type
					and r.project_id is not distinct from sb.project_id
					and r.cost_layer_id is not distinct from sb.cost_layer_id
				where sb.material_id = ?
				and sb.quality_status = 'QUALIFIED'
				and sb.ownership_type = ?
				and sb.project_id is not distinct from ?
				and sb.cost_layer_id is not distinct from ?
				and w.status = 'ENABLED'
				order by available_quantity desc, sb.updated_at desc, sb.id desc
				""", (rs, rowNum) -> new WarehouseAvailability(rs.getLong("warehouse_id"),
				rs.getBigDecimal("available_quantity")), command.materialId(), command.ownershipType(),
				command.projectId(), command.costLayerId());
	}

	private Optional<BalanceLock> lockQualifiedBalance(ReservationCommand command) {
		return lockQualifiedBalance(command.warehouseId(), command.materialId(), command.ownershipType(),
				command.projectId(), command.costLayerId());
	}

	private Optional<BalanceLock> lockQualifiedPublicBalance(Long warehouseId, Long materialId) {
		return lockQualifiedBalance(warehouseId, materialId, "PUBLIC", null, null);
	}

	private Optional<BalanceLock> lockQualifiedBalance(Long warehouseId, Long materialId, String ownershipType,
			Long projectId, Long costLayerId) {
		return this.jdbcTemplate
			.query("""
				select id, quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				for update
				""", (rs, rowNum) -> new BalanceLock(rs.getLong("id"), rs.getBigDecimal("quantity_on_hand")),
					warehouseId, materialId, ownershipType, projectId, costLayerId)
			.stream()
			.findFirst();
	}

	private BigDecimal activeLockedQuantityForUpdate(ReservationCommand command) {
		return activeLockedQuantityForUpdate(command.warehouseId(), command.materialId(), command.ownershipType(),
				command.projectId(), command.costLayerId());
	}

	private BigDecimal activeLockedPublicQuantityForUpdate(Long warehouseId, Long materialId) {
		return activeLockedQuantityForUpdate(warehouseId, materialId, "PUBLIC", null, null);
	}

	private BigDecimal activeLockedQuantityForUpdate(Long warehouseId, Long materialId, String ownershipType,
			Long projectId, Long costLayerId) {
		return this.jdbcTemplate.query("""
				select quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				and status = 'ACTIVE'
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity")
					.subtract(rs.getBigDecimal("released_quantity"))
					.subtract(rs.getBigDecimal("consumed_quantity")), warehouseId, materialId, ownershipType,
				projectId, costLayerId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
	}

	private void adjustLockedQuantity(Long warehouseId, Long materialId, String ownershipType, Long projectId,
			Long costLayerId, BigDecimal delta, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set locked_quantity = greatest(0, locked_quantity + ?), updated_at = ?, version = version + 1
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				""", delta, now, warehouseId, materialId, ownershipType, projectId, costLayerId);
	}

	private void validateReservationCommand(ReservationCommand command) {
		if (command == null || command.reservationType() == null || command.warehouseId() == null
				|| command.materialId() == null || command.unitId() == null || command.sourceId() == null
				|| command.sourceLineId() == null || command.businessDate() == null
				|| !hasText(command.sourceType()) || !hasText(command.sourceDocumentNo())
				|| !hasText(command.reason()) || !hasText(command.ownershipType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateQuantity(command.quantity());
		if ("PUBLIC".equals(command.ownershipType())) {
			if (command.projectId() != null || command.costLayerId() != null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return;
		}
		if (!"PROJECT".equals(command.ownershipType()) || command.projectId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (command.costLayerId() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
	}

	private void validateReservationIdentity(ReservationCommand command) {
		if (!"PROJECT".equals(command.ownershipType())) {
			return;
		}
		Long layerCount = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_project_cost_layer
				where id = ?
				  and project_id = ?
				  and material_id = ?
				  and status = 'ACTIVE'
				""", Long.class, command.costLayerId(), command.projectId(), command.materialId());
		if (layerCount == null || layerCount != 1L) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		boolean balanceExists = lockQualifiedBalance(command).isPresent();
		if (!balanceExists) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
	}

	private void validateQuantity(BigDecimal quantity) {
		if (quantity == null || quantity.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		}
	}

	private String reservationNo(InventoryReservationType type) {
		String prefix = type == InventoryReservationType.OCCUPATION ? "INV-OCC" : "INV-RSV";
		int sequence = Math.floorMod(RESERVATION_NO_SEQUENCE.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", sequence);
	}

	private String createAction(InventoryReservationType type) {
		return type == InventoryReservationType.OCCUPATION ? "INVENTORY_OCCUPATION_CREATE"
				: "INVENTORY_RESERVATION_CREATE";
	}

	private String releaseAction(InventoryReservationType type) {
		return type == InventoryReservationType.OCCUPATION ? "INVENTORY_OCCUPATION_RELEASE"
				: "INVENTORY_RESERVATION_RELEASE";
	}

	private String consumeAction(InventoryReservationType type) {
		return type == InventoryReservationType.OCCUPATION ? "INVENTORY_OCCUPATION_CONSUME"
				: "INVENTORY_RESERVATION_CONSUME";
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private static BigDecimal nullToZero(BigDecimal value) {
		return value == null ? ZERO : value;
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record ReservationCommand(InventoryReservationType reservationType, Long warehouseId, Long materialId,
			Long unitId, BigDecimal quantity, String sourceType, Long sourceId, Long sourceLineId,
			String sourceDocumentNo, LocalDate businessDate, String reason, String remark, String ownershipType,
			Long projectId, Long costLayerId) {

		public ReservationCommand withWarehouseId(Long warehouseId) {
			return new ReservationCommand(this.reservationType, warehouseId, this.materialId, this.unitId,
					this.quantity, this.sourceType, this.sourceId, this.sourceLineId, this.sourceDocumentNo,
					this.businessDate, this.reason, this.remark, this.ownershipType, this.projectId,
					this.costLayerId);
		}
	}

	private record BalanceLock(Long id, BigDecimal quantityOnHand) {
	}

	private record WarehouseAvailability(Long warehouseId, BigDecimal availableQuantity) {
	}

	private record ReservationLock(Long id, InventoryReservationType reservationType, Long warehouseId, Long materialId,
			BigDecimal quantity, BigDecimal releasedQuantity, BigDecimal consumedQuantity, String sourceDocumentNo,
			String ownershipType, Long projectId, Long costLayerId) {

		BigDecimal activeQuantity() {
			return this.quantity.subtract(this.releasedQuantity).subtract(this.consumedQuantity);
		}
	}

}
