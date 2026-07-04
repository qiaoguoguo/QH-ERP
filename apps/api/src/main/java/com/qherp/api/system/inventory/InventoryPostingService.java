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
		OffsetDateTime now = OffsetDateTime.now();
		BalanceRow balance = lockedBalance(request.warehouseId(), request.materialId(), request.unitId(), now);
		BigDecimal beforeQuantity = balance.quantityOnHand();
		BigDecimal afterQuantity = request.direction() == InventoryDirection.IN ? beforeQuantity.add(request.quantity())
				: beforeQuantity.subtract(request.quantity());
		if (afterQuantity.compareTo(ZERO) < 0) {
			throw new BusinessException(stockNotEnoughErrorCode(request.sourceType()));
		}
		try {
			this.jdbcTemplate.update("""
					insert into inv_stock_movement (
						movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
						before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
						reason, remark, operator_name, occurred_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", movementNo(request), request.movementType().name(), request.direction().name(),
					request.warehouseId(), request.materialId(), request.unitId(), request.quantity(), beforeQuantity,
					afterQuantity, request.sourceType(), request.sourceId(), request.sourceLineId(),
					request.businessDate(), request.reason(), blankToNull(request.remark()), request.operatorName(),
					now);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateMovementException(exception, request.sourceType());
		}
		this.jdbcTemplate.update("""
				update inv_stock_balance
				set quantity_on_hand = ?, unit_id = ?, updated_at = ?, version = version + 1
				where id = ?
				""", afterQuantity, request.unitId(), now, balance.id());
		return new PostingResult(beforeQuantity, afterQuantity);
	}

	private BalanceRow lockedBalance(Long warehouseId, Long materialId, Long unitId, OffsetDateTime now) {
		Optional<BalanceRow> balance = lockBalance(warehouseId, materialId);
		if (balance.isEmpty()) {
			try {
				this.jdbcTemplate.update("""
						insert into inv_stock_balance (
							warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?)
						""", warehouseId, materialId, unitId, ZERO, ZERO, now, now);
			}
			catch (DuplicateKeyException exception) {
				if (!containsConstraint(exception, "uk_inv_stock_balance_warehouse_material")) {
					throw exception;
				}
			}
			balance = lockBalance(warehouseId, materialId);
		}
		return balance.orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
	}

	private Optional<BalanceRow> lockBalance(Long warehouseId, Long materialId) {
		return this.jdbcTemplate
			.query("""
					select id, quantity_on_hand
					from inv_stock_balance
					where warehouse_id = ?
					and material_id = ?
					for update
					""", (rs, rowNum) -> new BalanceRow(rs.getLong("id"), rs.getBigDecimal("quantity_on_hand")),
					warehouseId, materialId)
			.stream()
			.findFirst();
	}

	private void validateRequest(PostingRequest request) {
		if (request == null || request.movementType() == null || request.direction() == null
				|| request.warehouseId() == null || request.materialId() == null || request.unitId() == null
				|| request.quantity() == null || request.quantity().compareTo(ZERO) <= 0
				|| !hasText(request.sourceType()) || request.sourceId() == null || request.sourceLineId() == null
				|| request.businessDate() == null || !hasText(request.reason()) || !hasText(request.operatorName())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
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

	private ApiErrorCode stockNotEnoughErrorCode(String sourceType) {
		return productionSource(sourceType) ? ApiErrorCode.PRODUCTION_STOCK_NOT_ENOUGH
				: ApiErrorCode.INVENTORY_STOCK_NOT_ENOUGH;
	}

	private ApiErrorCode sourceDuplicatedErrorCode(String sourceType) {
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

	private String movementNo(PostingRequest request) {
		String prefix = switch (request.movementType()) {
			case PRODUCTION_ISSUE -> "MFG-ISS-MOV";
			case PRODUCTION_RECEIPT -> "MFG-RCP-MOV";
			case PURCHASE_RECEIPT -> "PROC-RCP-MOV";
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

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record PostingRequest(InventoryMovementType movementType, InventoryDirection direction, Long warehouseId,
			Long materialId, Long unitId, BigDecimal quantity, String sourceType, Long sourceId, Long sourceLineId,
			LocalDate businessDate, String reason, String remark, String operatorName) {
	}

	public record PostingResult(BigDecimal beforeQuantity, BigDecimal afterQuantity) {
	}

	private record BalanceRow(Long id, BigDecimal quantityOnHand) {
	}

}
