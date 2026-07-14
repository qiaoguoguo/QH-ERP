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
import java.util.Objects;
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
		if (command.parentReservationId() != null) {
			return reserveChildFromWarehouse(command, operator, request);
		}
		validateReservationIdentity(command);
		OffsetDateTime now = OffsetDateTime.now();
		boolean rowLocking = rowLockingReservation(command);
		BigDecimal availableQuantity = rowLocking ? exactAvailableQuantityForUpdate(command)
				: aggregateAvailableQuantityForUpdate(command);
		if (availableQuantity.compareTo(command.quantity()) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_AVAILABLE_NOT_ENOUGH);
		}
		return insertReservation(command, rowLocking, operator, request, now);
	}

	private long reserveChildFromWarehouse(ReservationCommand command, CurrentUser operator,
			HttpServletRequest request) {
		ReservationLock parentSnapshot = findReservationById(command.parentReservationId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.VALIDATION_ERROR));
		command = inheritParentReservationIdentity(parentSnapshot, command);
		validateReservationIdentity(command);
		BigDecimal availableQuantity = lockExactReservationBalance(command);
		if (availableQuantity.compareTo(command.quantity()) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_AVAILABLE_NOT_ENOUGH);
		}
		ReservationLock parent = lockReservationById(command.parentReservationId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
		assertParentReservationStillMatches(parentSnapshot, parent, command);
		assertParentReservationAvailable(parent, command.quantity());
		return insertReservation(command, true, operator, request, OffsetDateTime.now());
	}

	private long insertReservation(ReservationCommand command, boolean rowLocking, CurrentUser operator,
			HttpServletRequest request, OffsetDateTime now) {
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into inv_stock_reservation (
						reservation_no, reservation_type, status, warehouse_id, material_id, unit_id, quality_status,
						quantity, released_quantity, consumed_quantity, source_type, source_id, source_line_id,
						source_document_no, business_date, reason, remark, created_by, created_at, updated_by,
						updated_at, ownership_type, project_id, cost_layer_id, batch_id, serial_id,
						parent_reservation_id
					)
					values (?, ?, 'ACTIVE', ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, reservationNo(command.reservationType()), command.reservationType().name(),
					command.warehouseId(), command.materialId(), command.unitId(), command.qualityStatus().name(),
					command.quantity(), command.sourceType(), command.sourceId(), command.sourceLineId(),
					command.sourceDocumentNo(), command.businessDate(), command.reason(), blankToNull(command.remark()),
					operator.username(), now, operator.username(), now, command.ownershipType(), command.projectId(),
					command.costLayerId(), command.batchId(), command.serialId(), command.parentReservationId());
			if (rowLocking) {
				adjustLockedQuantity(command.warehouseId(), command.materialId(), command.qualityStatus(),
						command.batchId(), command.serialId(), command.ownershipType(), command.projectId(),
						command.costLayerId(), command.quantity(), now);
			}
			this.auditService.record(operator, createAction(command.reservationType()), TARGET_TYPE, id,
					command.sourceDocumentNo(), request);
			return id;
		}
		catch (DuplicateKeyException exception) {
			if (containsConstraint(exception, "uk_inv_stock_reservation_active_source")
					|| containsConstraint(exception, "uk_inv_stock_reservation_active_source_untracked")
					|| containsConstraint(exception, "uk_inv_stock_reservation_active_source_batch")
					|| containsConstraint(exception, "uk_inv_stock_reservation_active_source_serial")
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
		Optional<ReservationLock> snapshot = findActiveReservation(reservationType, sourceType, sourceLineId);
		if (snapshot.isEmpty()) {
			return false;
		}
		ReservationLock expected = snapshot.get();
		if (rowLockingReservation(expected)) {
			lockExactReservationBalance(expected);
		}
		Optional<ReservationLock> reservation = lockActiveReservation(reservationType, sourceType, sourceLineId);
		if (reservation.isEmpty()) {
			return false;
		}
		ReservationLock current = reservation.get();
		assertReservationStillMatches(expected, current);
		consumeReservation(current, quantity, operator, request, false);
		return true;
	}

	@Transactional
	public boolean consumeTrackedBySourceLine(InventoryReservationType reservationType, String sourceType,
			Long sourceLineId, BigDecimal quantity, Long batchId, Long serialId, CurrentUser operator,
			HttpServletRequest request) {
		validateQuantity(quantity);
		Optional<ReservationLock> parentReservation = findActiveParentReservation(reservationType, sourceType,
				sourceLineId);
		if (parentReservation.isEmpty()) {
			return false;
		}
		ReservationLock parentSnapshot = parentReservation.get();
		InventoryTrackingMethod trackingMethod = trackingMethod(parentSnapshot.materialId());
		if (trackingMethod == InventoryTrackingMethod.NONE) {
			return consumeBySourceLine(reservationType, sourceType, sourceLineId, quantity, operator, request);
		}
		validateTrackedChildIdentity(trackingMethod, quantity, batchId, serialId);
		ReservationCommand childCommand = ReservationCommand.childOf(parentSnapshot, quantity, batchId, serialId);
		BigDecimal availableQuantity = lockExactReservationBalance(childCommand);
		ReservationLock parent = lockActiveParentReservation(reservationType, sourceType, sourceLineId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
		assertParentReservationStillMatches(parentSnapshot, parent, childCommand);
		Optional<ReservationLock> child = lockActiveChildReservation(parent.id(), batchId, serialId);
		ReservationLock lockedChild = child.orElseGet(() -> {
			if (availableQuantity.compareTo(quantity) < 0) {
				throw new BusinessException(ApiErrorCode.INVENTORY_AVAILABLE_NOT_ENOUGH);
			}
			assertParentReservationAvailable(parent, quantity);
			Long childId = insertReservation(childCommand, true, operator, request, OffsetDateTime.now());
			return lockActiveChildReservation(parent.id(), batchId, serialId)
				.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
		});
		consumeChildReservation(lockedChild, parent, quantity, operator, request);
		return true;
	}

	@Transactional
	public void releaseBySource(InventoryReservationType reservationType, String sourceType, Long sourceId,
			CurrentUser operator, HttpServletRequest request) {
		List<ReservationLock> candidates = releaseReservationCandidatesBySource(reservationType, sourceType, sourceId);
		lockReleaseReservationBalances(candidates);
		List<ReservationLock> reservations = lockActiveReservationsBySource(reservationType, sourceType, sourceId);
		releaseReservations(reservations, operator, request);
	}

	@Transactional
	public void releaseBySourceLine(InventoryReservationType reservationType, String sourceType, Long sourceLineId,
			CurrentUser operator, HttpServletRequest request) {
		List<ReservationLock> candidates = releaseReservationCandidatesBySourceLine(reservationType, sourceType,
				sourceLineId);
		lockReleaseReservationBalances(candidates);
		List<ReservationLock> reservations = lockActiveReservationsBySourceLine(reservationType, sourceType,
				sourceLineId);
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
			if (rowLockingReservation(reservation)) {
				adjustLockedQuantity(reservation.warehouseId(), reservation.materialId(), reservation.qualityStatus(),
						reservation.batchId(), reservation.serialId(), reservation.ownershipType(),
						reservation.projectId(), reservation.costLayerId(), activeQuantity.negate(), now);
			}
			this.auditService.record(operator, releaseAction(reservation.reservationType()), TARGET_TYPE,
					reservation.id(), reservation.sourceDocumentNo(), request);
		}
	}

	private Optional<ReservationLock> lockActiveReservation(InventoryReservationType reservationType, String sourceType,
			Long sourceLineId) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
					       consumed_quantity, source_type, source_id, source_line_id, source_document_no,
					       business_date, reason, remark, quality_status, batch_id, serial_id,
					       parent_reservation_id, ownership_type, project_id, cost_layer_id
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
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("released_quantity"), rs.getBigDecimal("consumed_quantity"),
				rs.getString("source_type"), rs.getLong("source_id"), rs.getLong("source_line_id"),
				rs.getString("source_document_no"), rs.getObject("business_date", LocalDate.class),
				rs.getString("reason"), rs.getString("remark"),
				InventoryQualityStatus.valueOf(rs.getString("quality_status")), nullableLong(rs, "batch_id"),
				nullableLong(rs, "serial_id"), nullableLong(rs, "parent_reservation_id"),
				rs.getString("ownership_type"), nullableLong(rs, "project_id"), nullableLong(rs, "cost_layer_id"));
	}

	private List<WarehouseAvailability> warehouseCandidates(ReservationCommand command) {
		if (rowLockingReservation(command)) {
			return this.jdbcTemplate.query("""
					select sb.warehouse_id,
					       sb.quantity_on_hand - coalesce(r.locked_quantity, 0) as available_quantity
					from inv_stock_balance sb
					join mst_warehouse w on w.id = sb.warehouse_id
					left join (
						select warehouse_id, material_id, quality_status, batch_id, serial_id, ownership_type,
						       project_id, cost_layer_id,
						       sum(quantity - released_quantity - consumed_quantity) as locked_quantity
						from inv_stock_reservation
						where status = 'ACTIVE'
						group by warehouse_id, material_id, quality_status, batch_id, serial_id, ownership_type,
						         project_id, cost_layer_id
					) r on r.warehouse_id = sb.warehouse_id and r.material_id = sb.material_id
						and r.quality_status = sb.quality_status
						and r.batch_id is not distinct from sb.batch_id
						and r.serial_id is not distinct from sb.serial_id
						and r.ownership_type = sb.ownership_type
						and r.project_id is not distinct from sb.project_id
						and r.cost_layer_id is not distinct from sb.cost_layer_id
					where sb.material_id = ?
					and sb.quality_status = ?
					and sb.batch_id is not distinct from ?
					and sb.serial_id is not distinct from ?
					and sb.ownership_type = ?
					and sb.project_id is not distinct from ?
					and sb.cost_layer_id is not distinct from ?
					and w.status = 'ENABLED'
					order by available_quantity desc, sb.updated_at desc, sb.id desc
					""", (rs, rowNum) -> new WarehouseAvailability(rs.getLong("warehouse_id"),
					rs.getBigDecimal("available_quantity")), command.materialId(), command.qualityStatus().name(),
					command.batchId(), command.serialId(), command.ownershipType(), command.projectId(),
					command.costLayerId());
		}
		return this.jdbcTemplate.query("""
				select sb.warehouse_id,
				       sum(sb.quantity_on_hand) - coalesce(r.locked_quantity, 0) as available_quantity
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
				group by sb.warehouse_id, r.locked_quantity
				order by available_quantity desc, max(sb.updated_at) desc, sb.warehouse_id desc
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

	private Optional<ReservationLock> findActiveReservation(InventoryReservationType reservationType, String sourceType,
			Long sourceLineId) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
					       consumed_quantity, source_type, source_id, source_line_id, source_document_no,
					       business_date, reason, remark, quality_status, batch_id, serial_id,
					       parent_reservation_id, ownership_type, project_id, cost_layer_id
					from inv_stock_reservation
					where reservation_type = ?
					and source_type = ?
					and source_line_id = ?
					and status = 'ACTIVE'
					""", this::mapReservationLock, reservationType.name(), sourceType, sourceLineId)
			.stream()
			.findFirst();
	}

	private List<ReservationLock> releaseReservationCandidatesBySource(InventoryReservationType reservationType,
			String sourceType, Long sourceId) {
		return this.jdbcTemplate.query("""
				select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
				       consumed_quantity, source_type, source_id, source_line_id, source_document_no, business_date,
				       reason, remark, quality_status, batch_id, serial_id, parent_reservation_id,
				       ownership_type, project_id, cost_layer_id
				from inv_stock_reservation
				where reservation_type = ?
				and source_type = ?
				and source_id = ?
				and status = 'ACTIVE'
				order by warehouse_id, material_id, quality_status, batch_id nulls first, serial_id nulls first,
				         ownership_type, project_id nulls first, cost_layer_id nulls first, id
				""", this::mapReservationLock, reservationType.name(), sourceType, sourceId);
	}

	private List<ReservationLock> releaseReservationCandidatesBySourceLine(InventoryReservationType reservationType,
			String sourceType, Long sourceLineId) {
		return this.jdbcTemplate.query("""
				select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
				       consumed_quantity, source_type, source_id, source_line_id, source_document_no, business_date,
				       reason, remark, quality_status, batch_id, serial_id, parent_reservation_id,
				       ownership_type, project_id, cost_layer_id
				from inv_stock_reservation
				where reservation_type = ?
				and source_type = ?
				and source_line_id = ?
				and status = 'ACTIVE'
				order by warehouse_id, material_id, quality_status, batch_id nulls first, serial_id nulls first,
				         ownership_type, project_id nulls first, cost_layer_id nulls first, id
				""", this::mapReservationLock, reservationType.name(), sourceType, sourceLineId);
	}

	private void lockReleaseReservationBalances(List<ReservationLock> reservations) {
		for (ReservationLock reservation : reservations) {
			if (rowLockingReservation(reservation)) {
				lockExactReservationBalance(reservation);
			}
		}
	}

	private List<ReservationLock> lockActiveReservationsBySource(InventoryReservationType reservationType,
			String sourceType, Long sourceId) {
		return this.jdbcTemplate.query("""
				select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
				       consumed_quantity, source_type, source_id, source_line_id, source_document_no, business_date,
				       reason, remark, quality_status, batch_id, serial_id, parent_reservation_id,
				       ownership_type, project_id, cost_layer_id
				from inv_stock_reservation
				where reservation_type = ?
				and source_type = ?
				and source_id = ?
				and status = 'ACTIVE'
				order by case when parent_reservation_id is null then 0 else 1 end,
				         parent_reservation_id nulls first, id
				for update
				""", this::mapReservationLock, reservationType.name(), sourceType, sourceId);
	}

	private List<ReservationLock> lockActiveReservationsBySourceLine(InventoryReservationType reservationType,
			String sourceType, Long sourceLineId) {
		return this.jdbcTemplate.query("""
				select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
				       consumed_quantity, source_type, source_id, source_line_id, source_document_no, business_date,
				       reason, remark, quality_status, batch_id, serial_id, parent_reservation_id,
				       ownership_type, project_id, cost_layer_id
				from inv_stock_reservation
				where reservation_type = ?
				and source_type = ?
				and source_line_id = ?
				and status = 'ACTIVE'
				order by case when parent_reservation_id is null then 0 else 1 end,
				         parent_reservation_id nulls first, id
				for update
				""", this::mapReservationLock, reservationType.name(), sourceType, sourceLineId);
	}

	private Optional<ReservationLock> lockActiveParentReservation(InventoryReservationType reservationType,
			String sourceType, Long sourceLineId) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
					       consumed_quantity, source_type, source_id, source_line_id, source_document_no,
					       business_date, reason, remark, quality_status, batch_id, serial_id,
					       parent_reservation_id, ownership_type, project_id, cost_layer_id
					from inv_stock_reservation
					where reservation_type = ?
					and source_type = ?
					and source_line_id = ?
					and parent_reservation_id is null
					and batch_id is null
					and serial_id is null
					and status = 'ACTIVE'
					for update
					""", this::mapReservationLock, reservationType.name(), sourceType, sourceLineId)
			.stream()
			.findFirst();
	}

	private Optional<ReservationLock> findActiveParentReservation(InventoryReservationType reservationType,
			String sourceType, Long sourceLineId) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
					       consumed_quantity, source_type, source_id, source_line_id, source_document_no,
					       business_date, reason, remark, quality_status, batch_id, serial_id,
					       parent_reservation_id, ownership_type, project_id, cost_layer_id
					from inv_stock_reservation
					where reservation_type = ?
					and source_type = ?
					and source_line_id = ?
					and parent_reservation_id is null
					and batch_id is null
					and serial_id is null
					and status = 'ACTIVE'
					""", this::mapReservationLock, reservationType.name(), sourceType, sourceLineId)
			.stream()
			.findFirst();
	}

	private Optional<ReservationLock> lockActiveChildReservation(Long parentReservationId, Long batchId, Long serialId) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
					       consumed_quantity, source_type, source_id, source_line_id, source_document_no,
					       business_date, reason, remark, quality_status, batch_id, serial_id,
					       parent_reservation_id, ownership_type, project_id, cost_layer_id
					from inv_stock_reservation
					where parent_reservation_id = ?
					and batch_id is not distinct from ?
					and serial_id is not distinct from ?
					and status = 'ACTIVE'
					for update
					""", this::mapReservationLock, parentReservationId, batchId, serialId)
			.stream()
			.findFirst();
	}

	private Optional<ReservationLock> findReservationById(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
					       consumed_quantity, source_type, source_id, source_line_id, source_document_no,
					       business_date, reason, remark, quality_status, batch_id, serial_id,
					       parent_reservation_id, ownership_type, project_id, cost_layer_id
					from inv_stock_reservation
					where id = ?
					""", this::mapReservationLock, id)
			.stream()
			.findFirst();
	}

	private Optional<ReservationLock> lockReservationById(Long id) {
		return this.jdbcTemplate
			.query("""
					select id, reservation_type, warehouse_id, material_id, unit_id, quantity, released_quantity,
					       consumed_quantity, source_type, source_id, source_line_id, source_document_no,
					       business_date, reason, remark, quality_status, batch_id, serial_id,
					       parent_reservation_id, ownership_type, project_id, cost_layer_id
					from inv_stock_reservation
					where id = ?
					for update
					""", this::mapReservationLock, id)
			.stream()
			.findFirst();
	}

	private void consumeReservation(ReservationLock current, BigDecimal quantity, CurrentUser operator,
			HttpServletRequest request, boolean consumeParent) {
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
		if (rowLockingReservation(current)) {
			adjustLockedQuantity(current.warehouseId(), current.materialId(), current.qualityStatus(),
					current.batchId(), current.serialId(), current.ownershipType(), current.projectId(),
					current.costLayerId(), quantity.negate(), now);
		}
		if (consumeParent && current.parentReservationId() != null) {
			consumeParentReservation(current.parentReservationId(), quantity, operator, now);
		}
		this.auditService.record(operator, consumeAction(current.reservationType()), TARGET_TYPE, current.id(),
				current.sourceDocumentNo(), request);
	}

	private void consumeChildReservation(ReservationLock child, ReservationLock parent, BigDecimal quantity,
			CurrentUser operator, HttpServletRequest request) {
		BigDecimal activeQuantity = child.activeQuantity();
		if (activeQuantity.compareTo(quantity) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		BigDecimal consumedQuantity = child.consumedQuantity().add(quantity);
		InventoryReservationStatus nextStatus = activeQuantity.compareTo(quantity) == 0
				? InventoryReservationStatus.CONSUMED : InventoryReservationStatus.ACTIVE;
		this.jdbcTemplate.update("""
				update inv_stock_reservation
				set status = ?, consumed_quantity = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", nextStatus.name(), consumedQuantity, operator.username(), now, child.id());
		if (rowLockingReservation(child)) {
			adjustLockedQuantity(child.warehouseId(), child.materialId(), child.qualityStatus(), child.batchId(),
					child.serialId(), child.ownershipType(), child.projectId(), child.costLayerId(), quantity.negate(),
					now);
		}
		consumeLockedParentReservation(parent, quantity, operator, now);
		this.auditService.record(operator, consumeAction(child.reservationType()), TARGET_TYPE, child.id(),
				child.sourceDocumentNo(), request);
	}

	private void consumeParentReservation(Long parentReservationId, BigDecimal quantity, CurrentUser operator,
			OffsetDateTime now) {
		ReservationLock parent = lockReservationById(parentReservationId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
		consumeLockedParentReservation(parent, quantity, operator, now);
	}

	private void consumeLockedParentReservation(ReservationLock parent, BigDecimal quantity, CurrentUser operator,
			OffsetDateTime now) {
		BigDecimal activeQuantity = parent.activeQuantity();
		if (activeQuantity.compareTo(quantity) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_STATUS_INVALID);
		}
		BigDecimal consumedQuantity = parent.consumedQuantity().add(quantity);
		InventoryReservationStatus nextStatus = activeQuantity.compareTo(quantity) == 0
				? InventoryReservationStatus.CONSUMED : InventoryReservationStatus.ACTIVE;
		this.jdbcTemplate.update("""
				update inv_stock_reservation
				set status = ?, consumed_quantity = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", nextStatus.name(), consumedQuantity, operator.username(), now, parent.id());
	}

	private BigDecimal exactAvailableQuantityForUpdate(ReservationCommand command) {
		BigDecimal quantityOnHand = exactQualifiedQuantityForUpdate(command);
		return quantityOnHand.subtract(activeExactLockedQuantityForUpdate(command));
	}

	private BigDecimal lockExactReservationBalance(ReservationCommand command) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand - locked_quantity as available_quantity
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("available_quantity"), command.warehouseId(),
				command.materialId(), command.qualityStatus().name(), command.batchId(), command.serialId(),
				command.ownershipType(), command.projectId(), command.costLayerId())
			.stream()
			.findFirst()
			.orElse(ZERO);
	}

	private BigDecimal lockExactReservationBalance(ReservationLock reservation) {
		return lockExactReservationBalance(ReservationCommand.childOf(reservation, reservation.activeQuantity(),
				reservation.batchId(), reservation.serialId()));
	}

	private BigDecimal aggregateAvailableQuantityForUpdate(ReservationCommand command) {
		BigDecimal quantityOnHand = aggregateQualifiedQuantityForUpdate(command);
		return quantityOnHand.subtract(activeLockedQuantityForUpdate(command.warehouseId(), command.materialId(),
				command.ownershipType(), command.projectId(), command.costLayerId()));
	}

	private BigDecimal activeLockedPublicQuantityForUpdate(Long warehouseId, Long materialId) {
		return activeLockedQuantityForUpdate(warehouseId, materialId, "PUBLIC", null, null);
	}

	private BigDecimal exactQualifiedQuantityForUpdate(ReservationCommand command) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity_on_hand"), command.warehouseId(),
				command.materialId(), command.qualityStatus().name(), command.batchId(), command.serialId(),
				command.ownershipType(), command.projectId(), command.costLayerId())
			.stream()
			.findFirst()
			.orElse(ZERO);
	}

	private BigDecimal aggregateQualifiedQuantityForUpdate(ReservationCommand command) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity_on_hand"), command.warehouseId(),
				command.materialId(), command.qualityStatus().name(), command.ownershipType(), command.projectId(),
				command.costLayerId())
			.stream()
			.reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal activeExactLockedQuantityForUpdate(ReservationCommand command) {
		return this.jdbcTemplate.query("""
				select quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				and status = 'ACTIVE'
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity")
				.subtract(rs.getBigDecimal("released_quantity"))
				.subtract(rs.getBigDecimal("consumed_quantity")), command.warehouseId(), command.materialId(),
				command.qualityStatus().name(), command.batchId(), command.serialId(), command.ownershipType(),
				command.projectId(), command.costLayerId())
			.stream()
			.reduce(ZERO, BigDecimal::add);
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

	private void adjustLockedQuantity(Long warehouseId, Long materialId, InventoryQualityStatus qualityStatus,
			Long batchId, Long serialId, String ownershipType, Long projectId, Long costLayerId, BigDecimal delta,
			OffsetDateTime now) {
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set locked_quantity = greatest(0, locked_quantity + ?), updated_at = ?, version = version + 1
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				""", delta, now, warehouseId, materialId, qualityStatus.name(), batchId, serialId, ownershipType,
				projectId, costLayerId);
	}

	private ReservationCommand inheritParentReservationIdentity(ReservationLock parent, ReservationCommand command) {
		if (parent.parentReservationId() != null || parent.batchId() != null || parent.serialId() != null
				|| parent.activeQuantity().compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_STATUS_INVALID);
		}
		validateTrackedChildIdentity(trackingMethod(parent.materialId()), command.quantity(), command.batchId(),
				command.serialId());
		return new ReservationCommand(parent.reservationType(), parent.warehouseId(), parent.materialId(),
				parent.unitId(), command.quantity(), parent.sourceType(), parent.sourceId(), parent.sourceLineId(),
				parent.sourceDocumentNo(), parent.businessDate(), parent.reason(), parent.remark(),
				parent.ownershipType(), parent.projectId(), parent.costLayerId(), parent.qualityStatus(),
				command.batchId(), command.serialId(), parent.id());
	}

	private void assertParentReservationStillMatches(ReservationLock expected, ReservationLock actual,
			ReservationCommand command) {
		assertReservationStillMatches(expected, actual);
		if (actual.parentReservationId() != null || actual.batchId() != null || actual.serialId() != null
				|| !Objects.equals(actual.id(), command.parentReservationId())
				|| actual.activeQuantity().compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_STATUS_INVALID);
		}
	}

	private void assertReservationStillMatches(ReservationLock expected, ReservationLock actual) {
		if (!Objects.equals(expected.id(), actual.id())
				|| expected.reservationType() != actual.reservationType()
				|| !Objects.equals(expected.warehouseId(), actual.warehouseId())
				|| !Objects.equals(expected.materialId(), actual.materialId())
				|| !Objects.equals(expected.unitId(), actual.unitId())
				|| !Objects.equals(expected.sourceType(), actual.sourceType())
				|| !Objects.equals(expected.sourceId(), actual.sourceId())
				|| !Objects.equals(expected.sourceLineId(), actual.sourceLineId())
				|| expected.qualityStatus() != actual.qualityStatus()
				|| !Objects.equals(expected.batchId(), actual.batchId())
				|| !Objects.equals(expected.serialId(), actual.serialId())
				|| !Objects.equals(expected.parentReservationId(), actual.parentReservationId())
				|| !Objects.equals(expected.ownershipType(), actual.ownershipType())
				|| !Objects.equals(expected.projectId(), actual.projectId())
				|| !Objects.equals(expected.costLayerId(), actual.costLayerId())) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	private void assertParentReservationAvailable(ReservationLock parent, BigDecimal quantity) {
		BigDecimal childActiveQuantity = activeChildQuantityForUpdate(parent.id());
		BigDecimal parentUnallocatedQuantity = parent.activeQuantity().subtract(childActiveQuantity);
		if (parentUnallocatedQuantity.compareTo(quantity) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_RESERVATION_STATUS_INVALID);
		}
	}

	private BigDecimal activeChildQuantityForUpdate(Long parentReservationId) {
		return this.jdbcTemplate.query("""
				select quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where parent_reservation_id = ?
				and status = 'ACTIVE'
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity")
				.subtract(rs.getBigDecimal("released_quantity"))
				.subtract(rs.getBigDecimal("consumed_quantity")), parentReservationId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
	}

	private boolean rowLockingReservation(ReservationCommand command) {
		if (command.parentReservationId() != null) {
			return true;
		}
		InventoryTrackingMethod trackingMethod = trackingMethod(command.materialId());
		return trackingMethod == InventoryTrackingMethod.NONE || command.batchId() != null || command.serialId() != null;
	}

	private boolean rowLockingReservation(ReservationLock reservation) {
		if (reservation.parentReservationId() != null) {
			return true;
		}
		InventoryTrackingMethod trackingMethod = trackingMethod(reservation.materialId());
		return trackingMethod == InventoryTrackingMethod.NONE || reservation.batchId() != null
				|| reservation.serialId() != null;
	}

	private void validateTrackedChildIdentity(InventoryTrackingMethod trackingMethod, BigDecimal quantity, Long batchId,
			Long serialId) {
		if (trackingMethod == InventoryTrackingMethod.NONE) {
			if (batchId != null || serialId != null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return;
		}
		if (trackingMethod == InventoryTrackingMethod.BATCH) {
			if (batchId == null || serialId != null) {
				throw new BusinessException(ApiErrorCode.INVENTORY_BATCH_REQUIRED);
			}
			return;
		}
		if (serialId == null || batchId != null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_SERIAL_REQUIRED);
		}
		if (quantity == null || quantity.compareTo(BigDecimal.ONE) != 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUANTITY_INVALID);
		}
	}

	private void validateReservationTrackingIdentity(ReservationCommand command) {
		if (command.qualityStatus() != InventoryQualityStatus.QUALIFIED) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUALITY_STATUS_REQUIRED);
		}
		InventoryTrackingMethod trackingMethod = trackingMethod(command.materialId());
		if (trackingMethod == InventoryTrackingMethod.NONE) {
			if (command.batchId() != null || command.serialId() != null || command.parentReservationId() != null) {
				throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
			}
			return;
		}
		if (command.parentReservationId() == null && command.batchId() == null && command.serialId() == null) {
			return;
		}
		validateTrackedChildIdentity(trackingMethod, command.quantity(), command.batchId(), command.serialId());
		if (command.parentReservationId() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private InventoryTrackingMethod trackingMethod(Long materialId) {
		String trackingMethod = this.jdbcTemplate.queryForObject("""
				select tracking_method
				from mst_material
				where id = ?
				""", String.class, materialId);
		return InventoryTrackingMethod.valueOf(trackingMethod);
	}

	private boolean qualifiedBalanceExists(ReservationCommand command) {
		if (rowLockingReservation(command)) {
			return this.jdbcTemplate.queryForObject("""
					select count(*)
					from inv_stock_balance
					where warehouse_id = ?
					and material_id = ?
					and quality_status = ?
					and batch_id is not distinct from ?
					and serial_id is not distinct from ?
					and ownership_type = ?
					and project_id is not distinct from ?
					and cost_layer_id is not distinct from ?
					""", Long.class, command.warehouseId(), command.materialId(), command.qualityStatus().name(),
					command.batchId(), command.serialId(), command.ownershipType(), command.projectId(),
					command.costLayerId()) > 0;
		}
		return this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				""", Long.class, command.warehouseId(), command.materialId(), command.qualityStatus().name(),
				command.ownershipType(), command.projectId(), command.costLayerId()) > 0;
	}

	private void validateReservationCommand(ReservationCommand command) {
		if (command == null || command.reservationType() == null || command.warehouseId() == null
				|| command.materialId() == null || command.unitId() == null || command.sourceId() == null
				|| command.sourceLineId() == null || command.businessDate() == null
				|| command.qualityStatus() == null || !hasText(command.sourceType()) || !hasText(command.sourceDocumentNo())
				|| !hasText(command.reason()) || !hasText(command.ownershipType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateQuantity(command.quantity());
		validateReservationTrackingIdentity(command);
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
		boolean balanceExists = qualifiedBalanceExists(command);
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
			Long projectId, Long costLayerId, InventoryQualityStatus qualityStatus, Long batchId, Long serialId,
			Long parentReservationId) {

		public ReservationCommand withWarehouseId(Long warehouseId) {
			return new ReservationCommand(this.reservationType, warehouseId, this.materialId, this.unitId,
					this.quantity, this.sourceType, this.sourceId, this.sourceLineId, this.sourceDocumentNo,
					this.businessDate, this.reason, this.remark, this.ownershipType, this.projectId,
					this.costLayerId, this.qualityStatus, this.batchId, this.serialId, this.parentReservationId);
		}

		private static ReservationCommand childOf(ReservationLock parent, BigDecimal quantity, Long batchId,
				Long serialId) {
			return new ReservationCommand(parent.reservationType(), parent.warehouseId(), parent.materialId(),
					parent.unitId(), quantity, parent.sourceType(), parent.sourceId(), parent.sourceLineId(),
					parent.sourceDocumentNo(), parent.businessDate(), parent.reason(), parent.remark(),
					parent.ownershipType(), parent.projectId(), parent.costLayerId(), parent.qualityStatus(), batchId,
					serialId, parent.id());
		}
	}

	private record BalanceLock(Long id, BigDecimal quantityOnHand) {
	}

	private record WarehouseAvailability(Long warehouseId, BigDecimal availableQuantity) {
	}

	private record ReservationLock(Long id, InventoryReservationType reservationType, Long warehouseId, Long materialId,
			Long unitId, BigDecimal quantity, BigDecimal releasedQuantity, BigDecimal consumedQuantity,
			String sourceType, Long sourceId, Long sourceLineId, String sourceDocumentNo, LocalDate businessDate,
			String reason, String remark, InventoryQualityStatus qualityStatus, Long batchId, Long serialId,
			Long parentReservationId, String ownershipType, Long projectId, Long costLayerId) {

		BigDecimal activeQuantity() {
			return this.quantity.subtract(this.releasedQuantity).subtract(this.consumedQuantity);
		}
	}

}
