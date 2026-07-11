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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryPostingService {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger MOVEMENT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	public InventoryPostingService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public PostingResult post(PostingRequest request) {
		validateRequest(request);
		validateOutboundQualityStatus(request);
		OffsetDateTime now = OffsetDateTime.now();
		BalanceRow balance = lockedBalance(request.warehouseId(), request.materialId(), request.unitId(),
				request.qualityStatus(), request.batchId(), request.serialId(), now);
		BigDecimal beforeQuantity = balance.quantityOnHand();
		BigDecimal afterQuantity = request.direction() == InventoryDirection.IN ? beforeQuantity.add(request.quantity())
				: beforeQuantity.subtract(request.quantity());
		if (afterQuantity.compareTo(ZERO) < 0) {
			throw new BusinessException(stockNotEnoughErrorCode(request, request.quantity()));
		}
		if (request.direction() == InventoryDirection.OUT && request.qualityStatus() == InventoryQualityStatus.QUALIFIED
				&& !request.consumedReservation()) {
			BigDecimal availableQuantity = beforeQuantity
				.subtract(activeLockedQuantityForUpdate(request.warehouseId(), request.materialId(), request.batchId(),
						request.serialId()));
			if (availableQuantity.compareTo(request.quantity()) < 0) {
				throw new BusinessException(ApiErrorCode.INVENTORY_RESERVED_OR_OCCUPIED_NOT_AVAILABLE);
			}
		}
		Long movementId;
		try {
			movementId = this.jdbcTemplate.queryForObject("""
					insert into inv_stock_movement (
						movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
						before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
						reason, remark, operator_name, occurred_at, quality_status, batch_id, serial_id
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, movementNo(request), request.movementType().name(), request.direction().name(),
					request.warehouseId(), request.materialId(), request.unitId(), request.quantity(), beforeQuantity,
					afterQuantity, request.sourceType(), request.sourceId(), request.sourceLineId(),
					request.businessDate(), request.reason(), blankToNull(request.remark()), request.operatorName(),
					now, request.qualityStatus().name(), request.batchId(), request.serialId());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateMovementException(exception, request.sourceType());
		}
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set quantity_on_hand = ?, unit_id = ?, updated_at = ?, version = version + 1
				where id = ?
				""", afterQuantity, request.unitId(), now, balance.id());
		return new PostingResult(beforeQuantity, afterQuantity, movementId);
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
		if (!allowedQualityStatusTransfer(fromStatus, toStatus)) {
			throw new BusinessException(ApiErrorCode.QUALITY_STATUS_TRANSITION_INVALID);
		}
		Long fromSourceLineId = transferSourceLineId(sourceLineId, fromStatus);
		Long toSourceLineId = transferSourceLineId(sourceLineId, toStatus);
		PostingResult fromResult = post(new PostingRequest(InventoryMovementType.QUALITY_STATUS_TRANSFER,
				InventoryDirection.OUT, warehouseId, materialId, unitId, quantity, fromStatus, sourceType, sourceId,
				fromSourceLineId, businessDate, reason, remark, operatorName, batchId, serialId));
		PostingResult toResult = post(new PostingRequest(InventoryMovementType.QUALITY_STATUS_TRANSFER,
				InventoryDirection.IN, warehouseId, materialId, unitId, quantity, toStatus, sourceType, sourceId,
				toSourceLineId, businessDate, reason, remark, operatorName, batchId, serialId));
		return new QualityTransferResult(fromResult.beforeQuantity(), fromResult.afterQuantity(),
				toResult.beforeQuantity(), toResult.afterQuantity(), fromResult.movementId(), toResult.movementId());
	}

	private BalanceRow lockedBalance(Long warehouseId, Long materialId, Long unitId, InventoryQualityStatus qualityStatus,
			Long batchId, Long serialId, OffsetDateTime now) {
		Optional<BalanceRow> balance = lockBalance(warehouseId, materialId, qualityStatus, batchId, serialId);
		if (balance.isEmpty()) {
			try {
				this.jdbcTemplate.update("""
						insert into inv_stock_balance (
							warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at,
							quality_status, batch_id, serial_id
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", warehouseId, materialId, unitId, ZERO, ZERO, now, now, qualityStatus.name(), batchId,
						serialId);
			}
			catch (DuplicateKeyException exception) {
				if (!containsConstraint(exception, "uk_inv_stock_balance_warehouse_material_quality")
						&& !containsConstraint(exception, "uk_inv_stock_balance_untracked")
						&& !containsConstraint(exception, "uk_inv_stock_balance_batch")
						&& !containsConstraint(exception, "uk_inv_stock_balance_serial")) {
					throw exception;
				}
			}
			balance = lockBalance(warehouseId, materialId, qualityStatus, batchId, serialId);
		}
		return balance.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
	}

	private Optional<BalanceRow> lockBalance(Long warehouseId, Long materialId, InventoryQualityStatus qualityStatus,
			Long batchId, Long serialId) {
		return this.jdbcTemplate
			.query("""
					select id, quantity_on_hand
					from inv_stock_balance
					where warehouse_id = ?
					and material_id = ?
					and quality_status = ?
					and batch_id is not distinct from ?
					and serial_id is not distinct from ?
					for update
					""", (rs, rowNum) -> new BalanceRow(rs.getLong("id"), rs.getBigDecimal("quantity_on_hand")),
					warehouseId, materialId, qualityStatus.name(), batchId, serialId)
			.stream()
			.findFirst();
	}

	private BigDecimal activeLockedQuantityForUpdate(Long warehouseId, Long materialId, Long batchId, Long serialId) {
		return this.jdbcTemplate.query("""
				select quantity, released_quantity, consumed_quantity
				from inv_stock_reservation
				where warehouse_id = ?
				and material_id = ?
				and quality_status = 'QUALIFIED'
				and batch_id is not distinct from ?
				and serial_id is not distinct from ?
				and status = 'ACTIVE'
				for update
				""", (rs, rowNum) -> rs.getBigDecimal("quantity")
					.subtract(rs.getBigDecimal("released_quantity"))
					.subtract(rs.getBigDecimal("consumed_quantity")), warehouseId, materialId, batchId, serialId)
			.stream()
			.reduce(ZERO, BigDecimal::add);
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
				""", BigDecimal.class, warehouseId, materialId);
		return total == null ? ZERO : total;
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

	public record PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
			Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus, String sourceType,
			Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String remark,
			String operatorName, boolean consumedReservation, Long batchId, Long serialId) {

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, false, null, null);
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, Long batchId, Long serialId) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, false, batchId, serialId);
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, InventoryQualityStatus qualityStatus,
				String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate, String reason,
				String remark, String operatorName, boolean consumedReservation) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, qualityStatus, sourceType,
					sourceId, sourceLineId, businessDate, reason, remark, operatorName, consumedReservation, null,
					null);
		}

		public PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
				Long materialId, Long unitId, BigDecimal quantity, String sourceType, Long sourceId, Long sourceLineId,
				LocalDate businessDate, String reason, String remark, String operatorName) {
			this(movementType, direction, warehouseId, materialId, unitId, quantity, InventoryQualityStatus.QUALIFIED,
					sourceType, sourceId, sourceLineId, businessDate, reason, remark, operatorName, false, null, null);
		}
	}

	public record PostingResult(BigDecimal beforeQuantity, BigDecimal afterQuantity, Long movementId) {

		public PostingResult(BigDecimal beforeQuantity, BigDecimal afterQuantity) {
			this(beforeQuantity, afterQuantity, null);
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

}
