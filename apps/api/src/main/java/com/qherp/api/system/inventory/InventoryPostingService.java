package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryPostingService {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger MOVEMENT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final InventoryValuationService inventoryValuationService;

	public InventoryPostingService(JdbcTemplate jdbcTemplate, InventoryValuationService inventoryValuationService) {
		this.jdbcTemplate = jdbcTemplate;
		this.inventoryValuationService = inventoryValuationService;
	}

	@Transactional
	public PostingResult post(PostingRequest request) {
		validateRequest(request);
		requireNoActiveStocktakeLock(request);
		validateOutboundQualityStatus(request);
		OffsetDateTime now = OffsetDateTime.now();
		ValuationContext valuationContext = request.valuationContextOrDefault();
		BalanceRow balance = lockedBalance(request.warehouseId(), request.materialId(), request.unitId(),
				request.qualityStatus(), request.batchId(), request.serialId(), valuationContext.ownershipType(),
				valuationContext.projectId(), balanceIdentityCostLayerId(valuationContext), now);
		BigDecimal beforeQuantity = balance.quantityOnHand();
		BigDecimal afterQuantity = request.direction() == InventoryDirection.IN ? beforeQuantity.add(request.quantity())
				: beforeQuantity.subtract(request.quantity());
		if (afterQuantity.compareTo(ZERO) < 0) {
			ApiErrorCode errorCode = stockNotEnoughErrorCode(request, request.quantity());
			if (errorCode == ApiErrorCode.INVENTORY_STOCK_NOT_ENOUGH && "PUBLIC".equals(valuationContext.ownershipType())
					&& materialValueEnabled(request.materialId())) {
				throw new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT);
			}
			throw new BusinessException(errorCode);
		}
		if (request.direction() == InventoryDirection.OUT && request.qualityStatus() == InventoryQualityStatus.QUALIFIED) {
			assertQualifiedOutboundAvailable(request.warehouseId(), request.materialId(), request.quantity(),
					request.batchId(), request.serialId(), valuationContext,
					ApiErrorCode.INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE, beforeQuantity,
					request.consumedReservation(), request.reservationSourceType(), request.reservationSourceLineId());
		}
		Long movementId;
		try {
			movementId = this.jdbcTemplate.queryForObject("""
					insert into inv_stock_movement (
						movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
						before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
						reason, remark, operator_name, occurred_at, quality_status, batch_id, serial_id,
						ownership_type, project_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, movementNo(request), request.movementType().name(), request.direction().name(),
					request.warehouseId(), request.materialId(), request.unitId(), request.quantity(), beforeQuantity,
					afterQuantity, request.sourceType(), request.sourceId(), request.sourceLineId(),
					request.businessDate(), request.reason(), blankToNull(request.remark()), request.operatorName(),
					now, request.qualityStatus().name(), request.batchId(), request.serialId(),
					valuationContext.ownershipType(), valuationContext.projectId());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateMovementException(exception, request.sourceType());
		}
		String movementNo = this.jdbcTemplate.queryForObject("select movement_no from inv_stock_movement where id = ?",
				String.class, movementId);
		InventoryValuationService.ValuationResult valuationResult = this.inventoryValuationService.apply(request,
				movementId, movementNo, beforeQuantity, afterQuantity, now);
		BalanceValue balanceValue = balanceValue(request, valuationContext, valuationResult, afterQuantity);
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set quantity_on_hand = ?, unit_id = ?, valuation_state = ?, inventory_amount = ?,
				    average_unit_cost = ?, cost_layer_id = ?, public_pool_id = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", afterQuantity, request.unitId(), valuationResult.valuationState(), balanceValue.inventoryAmount(),
				balanceValue.averageUnitCost(), balanceValue.costLayerId(), balanceValue.publicPoolId(), now,
				balance.id());
		if ("PUBLIC".equals(valuationContext.ownershipType())
				&& amountValuedState(valuationResult.valuationState())) {
			this.inventoryValuationService.synchronizePublicBalanceValues(request.materialId());
		}
		return new PostingResult(beforeQuantity, afterQuantity, movementId, valuationResult.unitCost(),
				valuationResult.inventoryAmount(), valuationResult.valuationMethod(), valuationResult.costLayerId(),
				valuationResult.valueMovementId());
	}

	@Transactional
	public QualityTransferResult transferQualityStatus(Long warehouseId, Long materialId, Long unitId,
			InventoryQualityStatus fromStatus, InventoryQualityStatus toStatus, BigDecimal quantity, String sourceType,
			Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName) {
		return transferQualityStatus(warehouseId, materialId, unitId, fromStatus, toStatus, quantity, sourceType,
				sourceId, sourceLineId, businessDate, reason, remark, operatorName, null, null);
	}

	@Transactional
	public QualityTransferResult transferQualityStatus(Long warehouseId, Long materialId, Long unitId,
			InventoryQualityStatus fromStatus, InventoryQualityStatus toStatus, BigDecimal quantity, String sourceType,
			Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, Long batchId, Long serialId) {
		return transferQualityStatus(warehouseId, materialId, unitId, fromStatus, toStatus, quantity, sourceType,
				sourceId, sourceLineId, businessDate, reason, remark, operatorName, batchId, serialId, null);
	}

	@Transactional
	public QualityTransferResult transferQualityStatus(Long warehouseId, Long materialId, Long unitId,
			InventoryQualityStatus fromStatus, InventoryQualityStatus toStatus, BigDecimal quantity, String sourceType,
			Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, Long batchId, Long serialId, ValuationContext requestedContext) {
		if (!allowedQualityStatusTransfer(fromStatus, toStatus)) {
			throw new BusinessException(ApiErrorCode.QUALITY_STATUS_TRANSITION_INVALID);
		}
		Long fromSourceLineId = transferSourceLineId(sourceLineId, fromStatus);
		Long toSourceLineId = transferSourceLineId(sourceLineId, toStatus);
		ValuationContext sourceContext = qualityTransferSourceContext(warehouseId, materialId, fromStatus, batchId,
				serialId, quantity, requestedContext);
		PostingResult fromResult = post(new PostingRequest(InventoryMovementType.QUALITY_STATUS_TRANSFER,
				InventoryDirection.OUT, warehouseId, materialId, unitId, quantity, fromStatus, sourceType, sourceId,
				fromSourceLineId, businessDate, reason, remark, operatorName, false, batchId, serialId,
				sourceContext));
		ValuationContext transferContext = new ValuationContext(sourceContext.ownershipType(),
				sourceContext.projectId(), fromResult.unitCost(), fromResult.costLayerId(),
				fromResult.valueMovementId(), fromResult.inventoryAmount());
		PostingResult toResult = post(new PostingRequest(InventoryMovementType.QUALITY_STATUS_TRANSFER,
				InventoryDirection.IN, warehouseId, materialId, unitId, quantity, toStatus, sourceType, sourceId,
				toSourceLineId, businessDate, reason, remark, operatorName, false, batchId, serialId,
				transferContext));
		return new QualityTransferResult(fromResult.beforeQuantity(), fromResult.afterQuantity(),
				toResult.beforeQuantity(), toResult.afterQuantity(), fromResult.movementId(), toResult.movementId());
	}

	@Transactional
	public void assertQualifiedOutboundAvailable(Long warehouseId, Long materialId, BigDecimal quantity, Long batchId,
			Long serialId, ValuationContext valuationContext, ApiErrorCode errorCode) {
		ValuationContext context = valuationContext == null ? ValuationContext.publicStock(null) : valuationContext;
		BigDecimal beforeQuantity = exactQualifiedQuantityForUpdate(warehouseId, materialId, batchId, serialId,
				context.ownershipType(), context.projectId(), balanceIdentityCostLayerId(context));
		assertQualifiedOutboundAvailable(warehouseId, materialId, quantity, batchId, serialId, context, errorCode,
				beforeQuantity, false, null, null);
	}

	private void assertQualifiedOutboundAvailable(Long warehouseId, Long materialId, BigDecimal quantity, Long batchId,
			Long serialId, ValuationContext valuationContext, ApiErrorCode errorCode, BigDecimal beforeQuantity,
			boolean consumedReservation, String sourceType, Long sourceLineId) {
		BigDecimal exactLockedQuantity = activeLockedQuantityForUpdate(warehouseId, materialId, batchId, serialId,
				valuationContext.ownershipType(), valuationContext.projectId(),
				balanceIdentityCostLayerId(valuationContext), consumedReservation, sourceType, sourceLineId);
		if (beforeQuantity.subtract(exactLockedQuantity).compareTo(quantity) < 0) {
			throw new BusinessException(errorCode);
		}
		if (consumedReservation) {
			return;
		}
		if (trackingMethod(materialId) == InventoryTrackingMethod.NONE) {
			return;
		}
		BigDecimal aggregateQuantity = aggregateQualifiedQuantityForUpdate(warehouseId, materialId,
				valuationContext.ownershipType(), valuationContext.projectId(), balanceIdentityCostLayerId(valuationContext));
		BigDecimal exactTrackedLockedQuantity = activeTrackedExactLockedQuantityForUpdate(warehouseId, materialId,
				valuationContext.ownershipType(), valuationContext.projectId(), balanceIdentityCostLayerId(valuationContext));
		BigDecimal parentUnallocatedQuantity = activeAggregateParentUnallocatedQuantityForUpdate(warehouseId, materialId,
				valuationContext.ownershipType(), valuationContext.projectId(), balanceIdentityCostLayerId(valuationContext));
		BigDecimal availableAfterThisOutbound = aggregateQuantity.subtract(quantity).subtract(exactTrackedLockedQuantity);
		if (availableAfterThisOutbound.compareTo(parentUnallocatedQuantity) < 0) {
			throw new BusinessException(errorCode);
		}
	}

	private BalanceRow lockedBalance(Long warehouseId, Long materialId, Long unitId, InventoryQualityStatus qualityStatus,
			Long batchId, Long serialId, String ownershipType, Long projectId, Long costLayerId, OffsetDateTime now) {
		Optional<BalanceRow> balance = lockBalance(warehouseId, materialId, qualityStatus, batchId, serialId,
				ownershipType, projectId, costLayerId);
		if (balance.isEmpty()) {
			try {
				this.jdbcTemplate.update("""
						insert into inv_stock_balance (
							warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at,
							quality_status, batch_id, serial_id, ownership_type, project_id, cost_layer_id
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", warehouseId, materialId, unitId, ZERO, ZERO, now, now, qualityStatus.name(), batchId,
						serialId, ownershipType, projectId, costLayerId);
			}
			catch (DuplicateKeyException exception) {
				if (!containsConstraint(exception, "uk_inv_stock_balance_warehouse_material_quality")
						&& !containsConstraint(exception, "uk_inv_stock_balance_untracked")
						&& !containsConstraint(exception, "uk_inv_stock_balance_batch")
						&& !containsConstraint(exception, "uk_inv_stock_balance_serial")) {
					throw exception;
				}
			}
			balance = lockBalance(warehouseId, materialId, qualityStatus, batchId, serialId, ownershipType, projectId,
					costLayerId);
		}
		return balance.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
	}

	private Optional<BalanceRow> lockBalance(Long warehouseId, Long materialId, InventoryQualityStatus qualityStatus,
			Long batchId, Long serialId, String ownershipType, Long projectId, Long costLayerId) {
		return this.jdbcTemplate
			.query("""
					select id, quantity_on_hand
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
					""", (rs, rowNum) -> new BalanceRow(rs.getLong("id"), rs.getBigDecimal("quantity_on_hand")),
					warehouseId, materialId, qualityStatus.name(), batchId, serialId, ownershipType, projectId,
					costLayerId)
			.stream()
			.findFirst();
	}

	private BigDecimal exactQualifiedQuantityForUpdate(Long warehouseId, Long materialId, Long batchId, Long serialId,
			String ownershipType, Long projectId, Long costLayerId) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity_on_hand"), warehouseId, materialId, batchId,
				serialId, ownershipType, projectId, costLayerId)
			.stream()
			.findFirst()
			.orElse(ZERO);
	}

	private BigDecimal aggregateQualifiedQuantityForUpdate(Long warehouseId, Long materialId, String ownershipType,
			Long projectId, Long costLayerId) {
		return this.jdbcTemplate.query("""
				select quantity_on_hand
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity_on_hand"), warehouseId, materialId, ownershipType,
				projectId, costLayerId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal activeLockedQuantityForUpdate(Long warehouseId, Long materialId, Long batchId, Long serialId,
			String ownershipType, Long projectId, Long costLayerId) {
		return activeLockedQuantityForUpdate(warehouseId, materialId, batchId, serialId, ownershipType, projectId,
				costLayerId, false, null, null);
	}

	private BigDecimal activeLockedQuantityForUpdate(Long warehouseId, Long materialId, Long batchId, Long serialId,
			String ownershipType, Long projectId, Long costLayerId, boolean excludeSource, String sourceType,
			Long sourceLineId) {
		return this.jdbcTemplate.query("""
				select quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				and status = 'ACTIVE'
				and (
					? = false
					or source_type <> ?
					or source_line_id is distinct from ?
				)
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity")
					.subtract(rs.getBigDecimal("released_quantity"))
					.subtract(rs.getBigDecimal("consumed_quantity")), warehouseId, materialId, batchId, serialId,
				ownershipType, projectId, costLayerId, excludeSource, sourceType, sourceLineId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal activeTrackedExactLockedQuantityForUpdate(Long warehouseId, Long materialId, String ownershipType,
			Long projectId, Long costLayerId) {
		return this.jdbcTemplate.query("""
				select quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and (batch_id is not null or serial_id is not null)
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				and status = 'ACTIVE'
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity")
				.subtract(rs.getBigDecimal("released_quantity"))
				.subtract(rs.getBigDecimal("consumed_quantity")), warehouseId, materialId, ownershipType, projectId,
				costLayerId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal activeAggregateParentUnallocatedQuantityForUpdate(Long warehouseId, Long materialId,
			String ownershipType, Long projectId, Long costLayerId) {
		return this.jdbcTemplate.query("""
				select id, quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id is null
				and serial_id is null
				and parent_reservation_id is null
				and ownership_type = ?
				and project_id is not distinct from ?
				and cost_layer_id is not distinct from ?
				and status = 'ACTIVE'
				for update
				""", (rs, rowNum) -> parentUnallocatedQuantity(rs.getLong("id"), rs.getBigDecimal("quantity")
				.subtract(rs.getBigDecimal("released_quantity"))
				.subtract(rs.getBigDecimal("consumed_quantity"))), warehouseId, materialId, ownershipType, projectId,
				costLayerId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
	}

	private BigDecimal parentUnallocatedQuantity(Long parentReservationId, BigDecimal parentActiveQuantity) {
		BigDecimal childActiveQuantity = this.jdbcTemplate.query("""
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
		BigDecimal unallocatedQuantity = parentActiveQuantity.subtract(childActiveQuantity);
		return unallocatedQuantity.compareTo(ZERO) < 0 ? ZERO : unallocatedQuantity;
	}

	private InventoryTrackingMethod trackingMethod(Long materialId) {
		String trackingMethod = this.jdbcTemplate.queryForObject("""
				select tracking_method
				from mst_material
				where id = ?
				""", String.class, materialId);
		return InventoryTrackingMethod.valueOf(trackingMethod);
	}

	private void validateRequest(PostingRequest request) {
		if (request == null || request.movementType() == null || request.direction() == null
				|| request.warehouseId() == null || request.materialId() == null || request.unitId() == null
				|| request.quantity() == null || request.quantity().compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.qualityStatus() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_QUALITY_STATUS_REQUIRED);
		}
		if (!hasText(request.sourceType()) || request.sourceId() == null || request.sourceLineId() == null
				|| request.businessDate() == null || !hasText(request.reason()) || !hasText(request.operatorName())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateOutboundQualityStatus(PostingRequest request) {
		if (request.direction() == InventoryDirection.OUT && ordinaryOutboundSource(request.sourceType())
				&& request.qualityStatus() != InventoryQualityStatus.QUALIFIED) {
			throw new BusinessException(ApiErrorCode.INVENTORY_NON_QUALIFIED_NOT_AVAILABLE);
		}
	}

	private BusinessException duplicateMovementException(DuplicateKeyException exception, String sourceType) {
		if (containsConstraint(exception, "uk_inv_stock_movement_source")) {
			return new BusinessException(sourceDuplicatedErrorCode(sourceType));
		}
		if (containsConstraint(exception, "uk_inv_stock_movement_opening_once")) {
			return new BusinessException(ApiErrorCode.INVENTORY_OPENING_EXISTS);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private ApiErrorCode stockNotEnoughErrorCode(PostingRequest request, BigDecimal requestedQuantity) {
		if (request.movementType() == InventoryMovementType.QUALITY_STATUS_TRANSFER
				|| request.qualityStatus() != InventoryQualityStatus.QUALIFIED) {
			return ApiErrorCode.INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH;
		}
		if (request.direction() == InventoryDirection.OUT && ordinaryOutboundSource(request.sourceType())
				&& totalQuantityOnHand(request.warehouseId(), request.materialId()).compareTo(requestedQuantity) >= 0) {
			return ApiErrorCode.INVENTORY_QUALITY_STATUS_BALANCE_NOT_ENOUGH;
		}
		String sourceType = request.sourceType();
		if (salesShipmentSource(sourceType)) {
			return ApiErrorCode.SALES_STOCK_NOT_ENOUGH;
		}
		return productionSource(sourceType) ? ApiErrorCode.PRODUCTION_STOCK_NOT_ENOUGH
				: ApiErrorCode.INVENTORY_STOCK_NOT_ENOUGH;
	}

	private ApiErrorCode sourceDuplicatedErrorCode(String sourceType) {
		if (salesShipmentSource(sourceType)) {
			return ApiErrorCode.SALES_MOVEMENT_SOURCE_DUPLICATED;
		}
		if (procurementSource(sourceType)) {
			return ApiErrorCode.PROCUREMENT_MOVEMENT_SOURCE_DUPLICATED;
		}
		return productionSource(sourceType) ? ApiErrorCode.PRODUCTION_MOVEMENT_SOURCE_DUPLICATED
				: ApiErrorCode.INVENTORY_MOVEMENT_SOURCE_DUPLICATED;
	}

	private boolean productionSource(String sourceType) {
		return sourceType != null && sourceType.startsWith("PRODUCTION_");
	}

	private boolean procurementSource(String sourceType) {
		return "PURCHASE_RECEIPT".equals(sourceType);
	}

	private boolean salesShipmentSource(String sourceType) {
		return "SALES_SHIPMENT".equals(sourceType);
	}

	private boolean ordinaryOutboundSource(String sourceType) {
		return "SALES_SHIPMENT".equals(sourceType) || "PRODUCTION_MATERIAL_ISSUE".equals(sourceType)
				|| "PRODUCTION_MATERIAL_SUPPLEMENT".equals(sourceType);
	}

	private boolean allowedQualityStatusTransfer(InventoryQualityStatus fromStatus, InventoryQualityStatus toStatus) {
		return (fromStatus == InventoryQualityStatus.PENDING_INSPECTION && toStatus == InventoryQualityStatus.QUALIFIED)
				|| (fromStatus == InventoryQualityStatus.PENDING_INSPECTION
						&& toStatus == InventoryQualityStatus.REJECTED)
				|| (fromStatus == InventoryQualityStatus.PENDING_INSPECTION
						&& toStatus == InventoryQualityStatus.FROZEN)
				|| (fromStatus == InventoryQualityStatus.QUALIFIED && toStatus == InventoryQualityStatus.FROZEN)
				|| (fromStatus == InventoryQualityStatus.FROZEN && toStatus == InventoryQualityStatus.QUALIFIED);
	}

	private BigDecimal totalQuantityOnHand(Long warehouseId, Long materialId) {
		BigDecimal total = this.jdbcTemplate.queryForObject("""
				select coalesce(sum(quantity_on_hand), 0)
				from inv_stock_balance
				where warehouse_id = ?
				and material_id = ?
				and ownership_type = 'PUBLIC'
				""", BigDecimal.class, warehouseId, materialId);
		return total == null ? ZERO : total;
	}

	private void requireNoActiveStocktakeLock(PostingRequest request) {
		if ("STOCKTAKE".equals(request.sourceType())) {
			return;
		}
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from inv_stocktake_range_lock
				where released_at is null
				and (warehouse_id is null or warehouse_id = ?)
				and (material_id is null or material_id = ?)
				""", Long.class, request.warehouseId(), request.materialId());
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_STOCKTAKE_RANGE_LOCKED);
		}
	}

	private boolean materialValueEnabled(Long materialId) {
		Boolean enabled = this.jdbcTemplate.queryForObject("""
				select coalesce(inventory_value_enabled, false)
				from mst_material
				where id = ?
				""", Boolean.class, materialId);
		return Boolean.TRUE.equals(enabled);
	}

	private boolean amountValuedState(String valuationState) {
		return "VALUED".equals(valuationState) || "MANUAL_PROVISIONAL".equals(valuationState)
				|| "CURRENT_AVERAGE_PROVISIONAL".equals(valuationState);
	}

	private BalanceValue balanceValue(PostingRequest request, ValuationContext context,
			InventoryValuationService.ValuationResult valuationResult, BigDecimal afterQuantity) {
		if ("NON_VALUED".equals(valuationResult.valuationState())) {
			return new BalanceValue(null, null, null, null);
		}
		if ("PUBLIC".equals(context.ownershipType())) {
			PublicPoolSnapshot pool = publicPoolSnapshot(request.materialId());
			BigDecimal amount = afterQuantity.multiply(pool.averageUnitCost()).setScale(2,
					java.math.RoundingMode.HALF_UP);
			return new BalanceValue(amount, pool.averageUnitCost(), null, pool.id());
		}
		BigDecimal unitCost = valuationResult.unitCost() == null ? context.unitPrice() : valuationResult.unitCost();
		BigDecimal projectAmount = unitCost == null ? null
				: afterQuantity.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP);
		return new BalanceValue(projectAmount, unitCost,
				valuationResult.costLayerId() == null ? context.costLayerId() : valuationResult.costLayerId(), null);
	}

	private Long balanceIdentityCostLayerId(ValuationContext context) {
		return "PROJECT".equals(context.ownershipType()) ? context.costLayerId() : null;
	}

	private ValuationContext qualityTransferSourceContext(Long warehouseId, Long materialId,
			InventoryQualityStatus qualityStatus, Long batchId, Long serialId, BigDecimal quantity,
			ValuationContext requestedContext) {
		List<Object> args = new java.util.ArrayList<>();
		StringBuilder where = new StringBuilder("""
				where warehouse_id = ?
				and material_id = ?
				and quality_status = ?
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and quantity_on_hand >= ?
				""");
		args.add(warehouseId);
		args.add(materialId);
		args.add(qualityStatus.name());
		args.add(batchId);
		args.add(serialId);
		args.add(quantity);
		if (requestedContext != null && hasText(requestedContext.ownershipType())) {
			where.append(" and ownership_type = ?");
			args.add(requestedContext.ownershipType());
			if ("PROJECT".equals(requestedContext.ownershipType())) {
				if (requestedContext.projectId() == null) {
					throw new BusinessException(ApiErrorCode.INVENTORY_OWNERSHIP_PROJECT_MISMATCH);
				}
				where.append(" and project_id = ?");
				args.add(requestedContext.projectId());
				if (requestedContext.costLayerId() != null) {
					where.append(" and cost_layer_id = ?");
					args.add(requestedContext.costLayerId());
				}
			}
			else if ("PUBLIC".equals(requestedContext.ownershipType())) {
				where.append(" and project_id is null");
			}
			else {
				throw new BusinessException(ApiErrorCode.INVENTORY_OWNERSHIP_PROJECT_MISMATCH);
			}
		}
		List<ValuationContext> candidates = this.jdbcTemplate.query("""
				select ownership_type, project_id, cost_layer_id
				from inv_stock_balance
				%s
				order by id
				""".formatted(where), (rs, rowNum) -> new ValuationContext(rs.getString("ownership_type"),
				nullableLong(rs, "project_id"), null, nullableLong(rs, "cost_layer_id"), null), args.toArray());
		if (candidates.isEmpty()) {
			if (requestedContext != null && hasText(requestedContext.ownershipType())) {
				if ("PROJECT".equals(requestedContext.ownershipType()) && requestedContext.costLayerId() != null) {
					throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
				}
				return requestedContext;
			}
			return ValuationContext.publicStock(null);
		}
		if (candidates.size() > 1) {
			throw new BusinessException(ApiErrorCode.INVENTORY_OWNERSHIP_PROJECT_MISMATCH);
		}
		ValuationContext candidate = candidates.getFirst();
		if ("PROJECT".equals(candidate.ownershipType()) && candidate.costLayerId() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		if (requestedContext != null && "PROJECT".equals(requestedContext.ownershipType())
				&& requestedContext.costLayerId() == null) {
			return candidate;
		}
		return candidate;
	}

	private PublicPoolSnapshot publicPoolSnapshot(Long materialId) {
		return this.jdbcTemplate.query("""
				select id, average_unit_cost
				from inv_public_valuation_pool
				where material_id = ?
				""", (rs, rowNum) -> new PublicPoolSnapshot(rs.getLong("id"), rs.getBigDecimal("average_unit_cost")),
				materialId)
			.stream()
			.findFirst()
			.orElse(new PublicPoolSnapshot(null, null));
	}

	private String movementNo(PostingRequest request) {
		String prefix = switch (request.movementType()) {
			case PRODUCTION_ISSUE -> "MFG-ISS-MOV";
			case PRODUCTION_RECEIPT -> "MFG-RCP-MOV";
			case PURCHASE_RECEIPT -> "PROC-RCP-MOV";
			case SALES_SHIPMENT -> "SAL-SHP-MOV";
			case QUALITY_STATUS_TRANSFER -> "INV-QST-MOV";
			default -> "INV-MOV";
		};
		int sequence = Math.floorMod(MOVEMENT_NO_SEQUENCE.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + request.sourceLineId() + "-"
				+ String.format("%03d", sequence);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private Long transferSourceLineId(Long sourceLineId, InventoryQualityStatus qualityStatus) {
		if (sourceLineId == null || qualityStatus == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return Math.addExact(Math.multiplyExact(sourceLineId, 10L), qualityStatus.ordinal());
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
			Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus, String sourceType,
			Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, boolean consumedReservation, Long batchId, Long serialId,
			ValuationContext valuationContext, String consumedReservationSourceType,
			Long consumedReservationSourceLineId) {

		public PostingRequest {
			if (valuationContext == null) {
				valuationContext = ValuationContext.publicStock(null);
			}
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, boolean consumedReservation, Long batchId, Long serialId,
				ValuationContext valuationContext) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, consumedReservation, batchId,
					serialId, valuationContext, null, null);
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, boolean consumedReservation, Long batchId, Long serialId,
				String consumedReservationSourceType, Long consumedReservationSourceLineId) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, consumedReservation, batchId,
					serialId, ValuationContext.publicStock(null), consumedReservationSourceType,
					consumedReservationSourceLineId);
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, false, null, null,
					ValuationContext.publicStock(null));
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, Long batchId, Long serialId) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, false, batchId, serialId,
					ValuationContext.publicStock(null));
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, boolean consumedReservation, Long batchId, Long serialId) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, consumedReservation, batchId,
					serialId, ValuationContext.publicStock(null));
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, Long batchId, Long serialId, BigDecimal unitPrice) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, false, batchId, serialId,
					ValuationContext.publicStock(unitPrice));
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, boolean consumedReservation) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, consumedReservation, null,
					null, ValuationContext.publicStock(null));
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, String sourceType, Long sourceId, Long sourceLineId,
				LocalDate businessDate, String reason, String remark, String operatorName) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, InventoryQualityStatus.QUALIFIED,
					sourceType, sourceId, sourceLineId, businessDate, reason, remark, operatorName, false, null, null,
					ValuationContext.publicStock(null));
		}

		public ValuationContext valuationContextOrDefault() {
			return this.valuationContext == null ? ValuationContext.publicStock(null) : this.valuationContext;
		}

		private String reservationSourceType() {
			return hasText(this.consumedReservationSourceType) ? this.consumedReservationSourceType : this.sourceType;
		}

		private Long reservationSourceLineId() {
			return this.consumedReservationSourceLineId == null ? this.sourceLineId : this.consumedReservationSourceLineId;
		}
	}

	public record ValuationContext(String ownershipType, Long projectId, BigDecimal unitPrice, Long costLayerId,
			Long originalValueMovementId, BigDecimal inventoryAmount) {

		public ValuationContext(String ownershipType, Long projectId, BigDecimal unitPrice, Long costLayerId,
				Long originalValueMovementId) {
			this(ownershipType, projectId, unitPrice, costLayerId, originalValueMovementId, null);
		}

		public ValuationContext {
			if (!hasText(ownershipType)) {
				ownershipType = "PUBLIC";
			}
			if ("PUBLIC".equals(ownershipType)) {
				projectId = null;
			}
		}

		public static ValuationContext publicStock(BigDecimal unitPrice) {
			return new ValuationContext("PUBLIC", null, unitPrice, null, null, null);
		}
	}

	public record PostingResult(BigDecimal beforeQuantity, BigDecimal afterQuantity, Long movementId,
			BigDecimal unitCost, BigDecimal inventoryAmount, String valuationMethod, Long costLayerId,
			Long valueMovementId) {

		public PostingResult(BigDecimal beforeQuantity, BigDecimal afterQuantity) {
			this(beforeQuantity, afterQuantity, null, null, null, null, null, null);
		}

		public PostingResult(BigDecimal beforeQuantity, BigDecimal afterQuantity, Long movementId) {
			this(beforeQuantity, afterQuantity, movementId, null, null, null, null, null);
		}
	}

	public record QualityTransferResult(BigDecimal fromBeforeQuantity, BigDecimal fromAfterQuantity,
			BigDecimal toBeforeQuantity, BigDecimal toAfterQuantity, Long fromMovementId, Long toMovementId) {

		public QualityTransferResult(BigDecimal fromBeforeQuantity, BigDecimal fromAfterQuantity,
				BigDecimal toBeforeQuantity, BigDecimal toAfterQuantity) {
			this(fromBeforeQuantity, fromAfterQuantity, toBeforeQuantity, toAfterQuantity, null, null);
		}
	}

	private record BalanceRow(Long id, BigDecimal quantityOnHand) {
	}

	private record BalanceValue(BigDecimal inventoryAmount, BigDecimal averageUnitCost, Long costLayerId,
			Long publicPoolId) {
	}

	private record PublicPoolSnapshot(Long id, BigDecimal averageUnitCost) {
	}

}
