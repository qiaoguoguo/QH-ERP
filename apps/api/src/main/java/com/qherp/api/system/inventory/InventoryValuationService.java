package com.qherp.api.system.inventory;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryValuationService {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private final JdbcTemplate jdbcTemplate;

	public InventoryValuationService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public ValuationResult apply(InventoryPostingService.PostingRequest request, Long stockMovementId,
			String movementNo, BigDecimal beforeQuantity, BigDecimal afterQuantity, OffsetDateTime now) {
		InventoryPostingService.ValuationContext context = request.valuationContextOrDefault();
		MaterialValuation material = materialValuation(request.materialId());
		if (!material.inventoryValueEnabled()) {
			Long valueMovementId = insertValueMovement(request, stockMovementId, movementNo, null, null, "NON_VALUED", "NON_VALUED",
					null, null);
			return new ValuationResult("NON_VALUED", null, null, "NON_VALUED", null, valueMovementId);
		}
		if ("PROJECT".equals(context.ownershipType())) {
			if (context.projectId() == null) {
				throw new BusinessException(ApiErrorCode.INVENTORY_OWNERSHIP_PROJECT_MISMATCH);
			}
			if (request.direction() == InventoryDirection.IN) {
				return applyProjectInbound(request, stockMovementId, movementNo, context);
			}
			return applyProjectOutbound(request, stockMovementId, movementNo, context);
		}
		if (!"PUBLIC".equals(context.ownershipType())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_OWNERSHIP_PROJECT_MISMATCH);
		}
		PublicPool pool = lockOrCreatePublicPool(request.materialId(), now);
		if ("LEGACY_UNVALUED".equals(pool.valuationState())) {
			throw new BusinessException(ApiErrorCode.INVENTORY_HISTORICAL_UNVALUED_COST_BLOCKED);
		}
		if (request.direction() == InventoryDirection.IN) {
			return applyPublicInbound(request, stockMovementId, movementNo, pool, context, now);
		}
		return applyPublicOutbound(request, stockMovementId, movementNo, pool, context, beforeQuantity,
				afterQuantity, now);
	}

	private ValuationResult applyPublicInbound(InventoryPostingService.PostingRequest request, Long stockMovementId,
			String movementNo, PublicPool pool, InventoryPostingService.ValuationContext context, OffsetDateTime now) {
		BigDecimal unitPrice = context.unitPrice();
		if (unitPrice == null || unitPrice.compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_VALUATION_UNIT_COST_REQUIRED);
		}
		BigDecimal inboundAmount = request.quantity().multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
		BigDecimal newQuantity = pool.quantity().add(request.quantity()).setScale(6, RoundingMode.UNNECESSARY);
		BigDecimal currentAmount = pool.amount() == null ? ZERO : pool.amount();
		BigDecimal newAmount = currentAmount.add(inboundAmount).setScale(2, RoundingMode.HALF_UP);
		BigDecimal averageUnitCost = newQuantity.compareTo(ZERO) == 0 ? ZERO
				: newAmount.divide(newQuantity, 6, RoundingMode.HALF_UP);
		String valuationState = publicInboundValuationState(request, pool, unitPrice);
		updatePublicPool(pool.id(), newQuantity, newAmount, averageUnitCost, valuationState, now);
		String valuationMethod = context.originalValueMovementId() == null ? "SOURCE_UNIT_PRICE" : "ORIGINAL_VALUE_REVERSAL";
		Long valueMovementId = insertValueMovement(request, stockMovementId, movementNo, unitPrice.setScale(6, RoundingMode.HALF_UP),
				inboundAmount, valuationMethod, valuationState, null, context.originalValueMovementId());
		return new ValuationResult(valuationState, unitPrice.setScale(6, RoundingMode.HALF_UP), inboundAmount,
				valuationMethod, null, valueMovementId);
	}

	private String publicInboundValuationState(InventoryPostingService.PostingRequest request, PublicPool pool,
			BigDecimal unitPrice) {
		if (request.movementType() != InventoryMovementType.PRODUCTION_RECEIPT) {
			return "VALUED";
		}
		if (pool.quantity().compareTo(ZERO) > 0 && pool.averageUnitCost() != null
				&& unitPrice.setScale(6, RoundingMode.HALF_UP)
					.compareTo(pool.averageUnitCost().setScale(6, RoundingMode.HALF_UP)) == 0) {
			return "CURRENT_AVERAGE_PROVISIONAL";
		}
		return "MANUAL_PROVISIONAL";
	}

	private ValuationResult applyPublicOutbound(InventoryPostingService.PostingRequest request, Long stockMovementId,
			String movementNo, PublicPool pool, InventoryPostingService.ValuationContext context,
			BigDecimal beforeQuantity, BigDecimal afterQuantity, OffsetDateTime now) {
		if (pool.amount() == null || pool.averageUnitCost() == null
				|| pool.quantity().compareTo(request.quantity()) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT);
		}
		boolean originalReversal = context.originalValueMovementId() != null && context.unitPrice() != null;
		BigDecimal unitCost = (originalReversal ? context.unitPrice() : pool.averageUnitCost()).setScale(6,
				RoundingMode.HALF_UP);
		BigDecimal outboundAmount;
		BigDecimal newAmount;
		if (afterQuantity.compareTo(ZERO) == 0 || pool.quantity().subtract(request.quantity()).compareTo(ZERO) == 0) {
			outboundAmount = pool.amount().setScale(2, RoundingMode.HALF_UP);
			newAmount = ZERO.setScale(2, RoundingMode.UNNECESSARY);
		}
		else {
			outboundAmount = request.quantity().multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
			if (pool.amount().compareTo(outboundAmount) < 0) {
				throw new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT);
			}
			newAmount = pool.amount().subtract(outboundAmount).setScale(2, RoundingMode.HALF_UP);
		}
		BigDecimal newQuantity = pool.quantity().subtract(request.quantity()).setScale(6, RoundingMode.UNNECESSARY);
		BigDecimal averageUnitCost = newQuantity.compareTo(ZERO) == 0 ? ZERO.setScale(6, RoundingMode.UNNECESSARY)
				: newAmount.divide(newQuantity, 6, RoundingMode.HALF_UP);
		updatePublicPool(pool.id(), newQuantity, newAmount, averageUnitCost, "VALUED", now);
		String valuationMethod = originalReversal ? "ORIGINAL_VALUE_REVERSAL" : "MOVING_WEIGHTED_AVERAGE";
		Long valueMovementId = insertValueMovement(request, stockMovementId, movementNo, unitCost, outboundAmount,
				valuationMethod, "VALUED", null, context.originalValueMovementId());
		return new ValuationResult("VALUED", unitCost, outboundAmount, valuationMethod, null, valueMovementId);
	}

	private ValuationResult applyProjectInbound(InventoryPostingService.PostingRequest request, Long stockMovementId,
			String movementNo, InventoryPostingService.ValuationContext context) {
		BigDecimal unitPrice = context.unitPrice();
		if (unitPrice == null || unitPrice.compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_VALUATION_UNIT_COST_REQUIRED);
		}
		BigDecimal unitCost = unitPrice.setScale(6, RoundingMode.HALF_UP);
		BigDecimal amount = request.quantity().multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
		Long layerId = this.jdbcTemplate.queryForObject("""
				insert into inv_project_cost_layer (
					project_id, material_id, source_type, source_id, source_line_id, parent_layer_id,
					batch_id, serial_id, original_quantity, original_amount, remaining_quantity,
					remaining_amount, unit_cost, status
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
				returning id
				""", Long.class, context.projectId(), request.materialId(), request.sourceType(), request.sourceId(),
				request.sourceLineId(), context.costLayerId(), request.batchId(), request.serialId(),
				request.quantity(), amount, request.quantity(), amount, unitCost);
		Long valueMovementId = insertValueMovement(request, stockMovementId, movementNo, unitCost, amount,
				"PROJECT_ACTUAL_LAYER", "VALUED", layerId, context.originalValueMovementId());
		return new ValuationResult("VALUED", unitCost, amount, "PROJECT_ACTUAL_LAYER", layerId, valueMovementId);
	}

	private ValuationResult applyProjectOutbound(InventoryPostingService.PostingRequest request, Long stockMovementId,
			String movementNo, InventoryPostingService.ValuationContext context) {
		if (context.costLayerId() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		ProjectLayer layer = lockProjectLayer(context.costLayerId());
		if (!layer.projectId().equals(context.projectId()) || !layer.materialId().equals(request.materialId())
				|| layer.remainingQuantity().compareTo(request.quantity()) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		BigDecimal amount;
		BigDecimal newRemainingAmount;
		if (layer.remainingQuantity().subtract(request.quantity()).compareTo(ZERO) == 0) {
			amount = layer.remainingAmount().setScale(2, RoundingMode.HALF_UP);
			newRemainingAmount = ZERO.setScale(2, RoundingMode.UNNECESSARY);
		}
		else {
			amount = request.quantity().multiply(layer.unitCost()).setScale(2, RoundingMode.HALF_UP);
			if (layer.remainingAmount().compareTo(amount) < 0) {
				throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
			}
			newRemainingAmount = layer.remainingAmount().subtract(amount).setScale(2, RoundingMode.HALF_UP);
		}
		BigDecimal newRemainingQuantity = layer.remainingQuantity()
			.subtract(request.quantity())
			.setScale(6, RoundingMode.UNNECESSARY);
		this.jdbcTemplate.update("""
				update inv_project_cost_layer
				set remaining_quantity = ?, remaining_amount = ?,
				    status = case when ? = 0 then 'EXHAUSTED' else 'ACTIVE' end,
				    updated_at = now(), version = version + 1
				where id = ?
				""", newRemainingQuantity, newRemainingAmount, newRemainingQuantity, layer.id());
		Long valueMovementId = insertValueMovement(request, stockMovementId, movementNo, layer.unitCost(), amount,
				"PROJECT_ACTUAL_LAYER", "VALUED", layer.id(), context.originalValueMovementId());
		this.jdbcTemplate.update("""
				insert into inv_cost_layer_allocation (value_movement_id, cost_layer_id, quantity, inventory_amount)
				values (?, ?, ?, ?)
				""", valueMovementId, layer.id(), request.quantity(), amount);
		return new ValuationResult("VALUED", layer.unitCost(), amount, "PROJECT_ACTUAL_LAYER", layer.id(),
				valueMovementId);
	}

	private PublicPool lockOrCreatePublicPool(Long materialId, OffsetDateTime now) {
		Optional<PublicPool> existing = lockPublicPool(materialId);
		if (existing.isPresent()) {
			return existing.get();
		}
		this.jdbcTemplate.update("""
				insert into inv_public_valuation_pool (
					material_id, quantity, amount, average_unit_cost, valuation_state, created_at, updated_at
				)
				values (?, 0, 0, 0, 'VALUED', ?, ?)
				on conflict (material_id) do nothing
				""", materialId, now, now);
		return lockPublicPool(materialId).orElseThrow(() -> new BusinessException(ApiErrorCode.CONFLICT));
	}

	private Optional<PublicPool> lockPublicPool(Long materialId) {
		return this.jdbcTemplate.query("""
				select id, quantity, amount, average_unit_cost, valuation_state
				from inv_public_valuation_pool
				where material_id = ?
				for update
				""", (rs, rowNum) -> new PublicPool(rs.getLong("id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("amount"), rs.getBigDecimal("average_unit_cost"),
				rs.getString("valuation_state")), materialId).stream().findFirst();
	}

	private void updatePublicPool(Long id, BigDecimal quantity, BigDecimal amount, BigDecimal averageUnitCost,
			String valuationState, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				update inv_public_valuation_pool
				set quantity = ?, amount = ?, average_unit_cost = ?, valuation_state = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", quantity, amount, averageUnitCost, valuationState, now, id);
	}

	public void synchronizePublicBalanceValues(Long materialId) {
		PublicPool pool = lockPublicPool(materialId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT));
		List<PublicBalance> balances = this.jdbcTemplate.query("""
				select id, quantity_on_hand
				from inv_stock_balance
				where material_id = ?
				and ownership_type = 'PUBLIC'
				order by id
				""", (rs, rowNum) -> new PublicBalance(rs.getLong("id"), rs.getBigDecimal("quantity_on_hand")),
				materialId);
		if (balances.isEmpty()) {
			return;
		}
		if (!amountValuedState(pool.valuationState()) || pool.averageUnitCost() == null || pool.amount() == null) {
			for (PublicBalance balance : balances) {
				this.jdbcTemplate.update("""
						update inv_stock_balance
						set inventory_amount = null, average_unit_cost = null, public_pool_id = ?,
						    valuation_state = ?
						where id = ?
						""", pool.id(), pool.valuationState(), balance.id());
			}
			return;
		}
		List<BigDecimal> amounts = new ArrayList<>(balances.size());
		BigDecimal assigned = ZERO.setScale(2);
		int residualIndex = -1;
		for (int i = 0; i < balances.size(); i++) {
			PublicBalance balance = balances.get(i);
			BigDecimal amount = balance.quantity()
				.multiply(pool.averageUnitCost())
				.setScale(2, RoundingMode.HALF_UP);
			amounts.add(amount);
			assigned = assigned.add(amount).setScale(2, RoundingMode.HALF_UP);
			if (balance.quantity().compareTo(ZERO) > 0) {
				residualIndex = i;
			}
		}
		if (residualIndex < 0) {
			residualIndex = balances.size() - 1;
		}
		BigDecimal difference = pool.amount().setScale(2, RoundingMode.HALF_UP).subtract(assigned).setScale(2,
				RoundingMode.HALF_UP);
		if (difference.compareTo(ZERO) > 0) {
			amounts.set(residualIndex, amounts.get(residualIndex).add(difference).setScale(2, RoundingMode.HALF_UP));
		}
		else if (difference.compareTo(ZERO) < 0) {
			BigDecimal remainingReduction = difference.abs();
			for (int i = residualIndex; i >= 0 && remainingReduction.compareTo(ZERO) > 0; i--) {
				BigDecimal current = amounts.get(i);
				BigDecimal reduction = current.min(remainingReduction).setScale(2, RoundingMode.HALF_UP);
				amounts.set(i, current.subtract(reduction).setScale(2, RoundingMode.HALF_UP));
				remainingReduction = remainingReduction.subtract(reduction).setScale(2, RoundingMode.HALF_UP);
			}
		}
		for (int i = 0; i < balances.size(); i++) {
			this.jdbcTemplate.update("""
					update inv_stock_balance
					set inventory_amount = ?, average_unit_cost = ?, public_pool_id = ?,
					    valuation_state = ?
					where id = ?
					""", amounts.get(i), pool.averageUnitCost(), pool.id(), pool.valuationState(),
					balances.get(i).id());
		}
	}

	public Optional<BigDecimal> currentPublicAverageUnitCost(Long materialId) {
		return this.jdbcTemplate.query("""
				select average_unit_cost, valuation_state, quantity
				from inv_public_valuation_pool
				where material_id = ?
				""", (rs, rowNum) -> {
			String state = rs.getString("valuation_state");
			BigDecimal quantity = rs.getBigDecimal("quantity");
			BigDecimal average = rs.getBigDecimal("average_unit_cost");
			return amountValuedState(state) && quantity != null && quantity.compareTo(ZERO) > 0 ? average : null;
		}, materialId).stream().filter((value) -> value != null).findFirst();
	}

	public ValuationResult applyLegacyPublicOpeningAdjustment(Long materialId, BigDecimal quantity,
			BigDecimal unitCost, BigDecimal adjustmentAmount, String sourceType, Long sourceId, Long sourceLineId,
			LocalDate businessDate, String reason, String operatorName, OffsetDateTime now) {
		if (materialId == null || quantity == null || quantity.compareTo(ZERO) <= 0 || adjustmentAmount == null
				|| adjustmentAmount.compareTo(ZERO) < 0 || sourceId == null || sourceLineId == null
				|| businessDate == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		MaterialValuation material = materialValuation(materialId);
		if (!material.inventoryValueEnabled()) {
			throw new BusinessException(ApiErrorCode.INVENTORY_VALUATION_UNIT_COST_REQUIRED);
		}
		PublicPool pool = lockPublicPool(materialId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT));
		if (!"LEGACY_UNVALUED".equals(pool.valuationState()) || pool.quantity().compareTo(quantity) != 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_HISTORICAL_UNVALUED_COST_BLOCKED);
		}
		BigDecimal averageUnitCost = adjustmentAmount.divide(quantity, 6, RoundingMode.HALF_UP);
		if (unitCost != null && unitCost.setScale(6, RoundingMode.HALF_UP).compareTo(averageUnitCost) != 0) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PublicAdjustmentBalance balance = publicAdjustmentBalance(materialId);
		Long stockMovementId = this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at, quality_status, batch_id, serial_id,
					ownership_type, project_id
				)
				values (?, 'VALUATION_ADJUSTMENT', 'IN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?,
					'PUBLIC', null)
				returning id
				""", Long.class, valuationAdjustmentMovementNo(sourceId, sourceLineId), balance.warehouseId(),
				materialId, balance.unitId(), quantity, balance.quantity(), balance.quantity(), sourceType, sourceId,
				sourceLineId, businessDate, reason, operatorName, now, balance.qualityStatus(), balance.batchId(),
				balance.serialId());
		updatePublicPool(pool.id(), quantity.setScale(6, RoundingMode.UNNECESSARY),
				adjustmentAmount.setScale(2, RoundingMode.HALF_UP), averageUnitCost, "VALUED", now);
		InventoryPostingService.PostingRequest request = new InventoryPostingService.PostingRequest(
				InventoryMovementType.VALUATION_ADJUSTMENT, InventoryDirection.IN, balance.warehouseId(), materialId,
				balance.unitId(), quantity, InventoryQualityStatus.valueOf(balance.qualityStatus()), sourceType,
				sourceId, sourceLineId, businessDate, reason, null, operatorName, balance.batchId(), balance.serialId(),
				averageUnitCost);
		Long valueMovementId = insertValueMovement(request, stockMovementId,
				valuationAdjustmentMovementNo(sourceId, sourceLineId), averageUnitCost,
				adjustmentAmount.setScale(2, RoundingMode.HALF_UP), "LEGACY_OPENING_VALUATION", "VALUED", null, null);
		synchronizePublicBalanceValues(materialId);
		return new ValuationResult("VALUED", averageUnitCost, adjustmentAmount.setScale(2, RoundingMode.HALF_UP),
				"LEGACY_OPENING_VALUATION", null, valueMovementId);
	}

	public ValuationResult applyPublicProvisionalRevaluationAdjustment(Long materialId, BigDecimal unitCost,
			BigDecimal adjustmentAmount, String sourceType, Long sourceId, Long sourceLineId, LocalDate businessDate,
			String reason, String operatorName, OffsetDateTime now) {
		if (materialId == null || adjustmentAmount == null || sourceId == null || sourceLineId == null
				|| businessDate == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PublicPool pool = lockPublicPool(materialId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT));
		if (!amountValuedState(pool.valuationState()) || pool.quantity().compareTo(ZERO) <= 0
				|| pool.amount() == null) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT);
		}
		BigDecimal delta = adjustmentDelta(pool.quantity(), pool.amount(), unitCost, adjustmentAmount);
		BigDecimal newAmount = pool.amount().add(delta).setScale(2, RoundingMode.HALF_UP);
		if (newAmount.compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT);
		}
		BigDecimal newUnitCost = newAmount.divide(pool.quantity(), 6, RoundingMode.HALF_UP);
		PublicAdjustmentBalance balance = publicAdjustmentBalance(materialId);
		InventoryDirection direction = delta.compareTo(ZERO) >= 0 ? InventoryDirection.IN : InventoryDirection.OUT;
		Long stockMovementId = insertValuationAdjustmentStockMovement(direction, balance.warehouseId(), materialId,
				balance.unitId(), pool.quantity(), balance.quantity(), sourceType, sourceId, sourceLineId,
				businessDate, reason, operatorName, now, balance.qualityStatus(), balance.batchId(),
				balance.serialId(), "PUBLIC", null);
		updatePublicPool(pool.id(), pool.quantity(), newAmount, newUnitCost, "VALUED", now);
		InventoryPostingService.PostingRequest request = new InventoryPostingService.PostingRequest(
				InventoryMovementType.VALUATION_ADJUSTMENT, direction, balance.warehouseId(), materialId,
				balance.unitId(), pool.quantity(), InventoryQualityStatus.valueOf(balance.qualityStatus()),
				sourceType, sourceId, sourceLineId, businessDate, reason, null, operatorName, balance.batchId(),
				balance.serialId(), newUnitCost);
		Long valueMovementId = insertValueMovement(request, stockMovementId,
				valuationAdjustmentMovementNo(sourceId, sourceLineId), newUnitCost,
				delta.abs().setScale(2, RoundingMode.HALF_UP), "PROVISIONAL_REVALUATION", "VALUED", null, null);
		synchronizePublicBalanceValues(materialId);
		return new ValuationResult("VALUED", newUnitCost, delta.abs().setScale(2, RoundingMode.HALF_UP),
				"PROVISIONAL_REVALUATION", null, valueMovementId);
	}

	public ValuationResult applyProjectProvisionalRevaluationAdjustment(Long projectId, Long materialId,
			Long costLayerId, BigDecimal unitCost, BigDecimal adjustmentAmount, String sourceType, Long sourceId,
			Long sourceLineId, LocalDate businessDate, String reason, String operatorName, OffsetDateTime now) {
		if (projectId == null || materialId == null || costLayerId == null || adjustmentAmount == null
				|| sourceId == null || sourceLineId == null || businessDate == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		ProjectLayer layer = lockProjectLayer(costLayerId);
		if (!layer.projectId().equals(projectId) || !layer.materialId().equals(materialId)
				|| layer.remainingQuantity().compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		BigDecimal delta = adjustmentDelta(layer.remainingQuantity(), layer.remainingAmount(), unitCost,
				adjustmentAmount);
		BigDecimal newAmount = layer.remainingAmount().add(delta).setScale(2, RoundingMode.HALF_UP);
		if (newAmount.compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT);
		}
		BigDecimal newUnitCost = newAmount.divide(layer.remainingQuantity(), 6, RoundingMode.HALF_UP);
		PublicAdjustmentBalance balance = projectAdjustmentBalance(projectId, materialId, costLayerId);
		InventoryDirection direction = delta.compareTo(ZERO) >= 0 ? InventoryDirection.IN : InventoryDirection.OUT;
		Long stockMovementId = insertValuationAdjustmentStockMovement(direction, balance.warehouseId(), materialId,
				balance.unitId(), layer.remainingQuantity(), balance.quantity(), sourceType, sourceId, sourceLineId,
				businessDate, reason, operatorName, now, balance.qualityStatus(), balance.batchId(),
				balance.serialId(), "PROJECT", projectId);
		this.jdbcTemplate.update("""
				update inv_project_cost_layer
				set remaining_amount = ?, unit_cost = ?, updated_at = now(), version = version + 1
				where id = ?
				""", newAmount, newUnitCost, layer.id());
		InventoryPostingService.PostingRequest request = new InventoryPostingService.PostingRequest(
				InventoryMovementType.VALUATION_ADJUSTMENT, direction, balance.warehouseId(), materialId,
				balance.unitId(), layer.remainingQuantity(), InventoryQualityStatus.valueOf(balance.qualityStatus()),
				sourceType, sourceId, sourceLineId, businessDate, reason, null, operatorName, false,
				balance.batchId(), balance.serialId(),
				new InventoryPostingService.ValuationContext("PROJECT", projectId, newUnitCost, costLayerId, null));
		Long valueMovementId = insertValueMovement(request, stockMovementId,
				valuationAdjustmentMovementNo(sourceId, sourceLineId), newUnitCost,
				delta.abs().setScale(2, RoundingMode.HALF_UP), "PROVISIONAL_REVALUATION", "VALUED", costLayerId,
				null);
		return new ValuationResult("VALUED", newUnitCost, delta.abs().setScale(2, RoundingMode.HALF_UP),
				"PROVISIONAL_REVALUATION", costLayerId, valueMovementId);
	}

	private static boolean amountValuedState(String valuationState) {
		return "VALUED".equals(valuationState) || "MANUAL_PROVISIONAL".equals(valuationState)
				|| "CURRENT_AVERAGE_PROVISIONAL".equals(valuationState);
	}

	private PublicAdjustmentBalance publicAdjustmentBalance(Long materialId) {
		return this.jdbcTemplate.query("""
				select warehouse_id, unit_id, quality_status, batch_id, serial_id, quantity_on_hand
				from inv_stock_balance
				where material_id = ?
				and ownership_type = 'PUBLIC'
				and quantity_on_hand > 0
				order by id
				limit 1
				for update
				""", (rs, rowNum) -> new PublicAdjustmentBalance(rs.getLong("warehouse_id"), rs.getLong("unit_id"),
				rs.getString("quality_status"), nullableLong(rs, "batch_id"), nullableLong(rs, "serial_id"),
				rs.getBigDecimal("quantity_on_hand")), materialId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_PUBLIC_POOL_INSUFFICIENT));
	}

	private PublicAdjustmentBalance projectAdjustmentBalance(Long projectId, Long materialId, Long costLayerId) {
		return this.jdbcTemplate.query("""
				select warehouse_id, unit_id, quality_status, batch_id, serial_id, quantity_on_hand
				from inv_stock_balance
				where material_id = ?
				and ownership_type = 'PROJECT'
				and project_id = ?
				and cost_layer_id = ?
				and quantity_on_hand > 0
				order by id
				limit 1
				for update
				""", (rs, rowNum) -> new PublicAdjustmentBalance(rs.getLong("warehouse_id"), rs.getLong("unit_id"),
				rs.getString("quality_status"), nullableLong(rs, "batch_id"), nullableLong(rs, "serial_id"),
				rs.getBigDecimal("quantity_on_hand")), materialId, projectId, costLayerId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT));
	}

	private Long insertValuationAdjustmentStockMovement(InventoryDirection direction, Long warehouseId,
			Long materialId, Long unitId, BigDecimal quantity, BigDecimal balanceQuantity, String sourceType,
			Long sourceId, Long sourceLineId, LocalDate businessDate, String reason, String operatorName,
			OffsetDateTime now, String qualityStatus, Long batchId, Long serialId, String ownershipType,
			Long projectId) {
		return this.jdbcTemplate.queryForObject("""
				insert into inv_stock_movement (
					movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity,
					before_quantity, after_quantity, source_type, source_id, source_line_id, business_date,
					reason, remark, operator_name, occurred_at, quality_status, batch_id, serial_id,
					ownership_type, project_id
				)
				values (?, 'VALUATION_ADJUSTMENT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, valuationAdjustmentMovementNo(sourceId, sourceLineId), direction.name(), warehouseId,
				materialId, unitId, quantity, balanceQuantity, balanceQuantity, sourceType, sourceId, sourceLineId,
				businessDate, reason, operatorName, now, qualityStatus, batchId, serialId, ownershipType, projectId);
	}

	private BigDecimal adjustmentDelta(BigDecimal quantity, BigDecimal currentAmount, BigDecimal unitCost,
			BigDecimal adjustmentAmount) {
		if (unitCost != null) {
			BigDecimal targetAmount = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
			return targetAmount.subtract(currentAmount).setScale(2, RoundingMode.HALF_UP);
		}
		return adjustmentAmount.setScale(2, RoundingMode.HALF_UP);
	}

	private static String valuationAdjustmentMovementNo(Long sourceId, Long sourceLineId) {
		return "INV-VAL-MOV-" + sourceId + "-" + sourceLineId;
	}

	private Long insertValueMovement(InventoryPostingService.PostingRequest request, Long stockMovementId,
			String movementNo, BigDecimal unitCost, BigDecimal inventoryAmount, String valuationMethod,
			String valuationState, Long costLayerId, Long originalValueMovementId) {
		Long valueMovementId = this.jdbcTemplate.queryForObject("""
				insert into inv_value_movement (
					stock_movement_id, movement_no, movement_type, direction, warehouse_id, material_id,
					ownership_type, project_id, cost_layer_id, quantity, unit_cost, inventory_amount,
					valuation_method, valuation_state, original_value_movement_id, source_type, source_id,
					source_line_id, business_date
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, stockMovementId, movementNo, request.movementType().name(), request.direction().name(),
				request.warehouseId(), request.materialId(), request.valuationContextOrDefault().ownershipType(),
				request.valuationContextOrDefault().projectId(), costLayerId, request.quantity(), unitCost,
				inventoryAmount, valuationMethod, valuationState, originalValueMovementId, request.sourceType(),
				request.sourceId(), request.sourceLineId(), request.businessDate());
		this.jdbcTemplate.update("""
				update inv_stock_movement
				set valuation_state = ?, valuation_method = ?, unit_cost = ?, inventory_amount = ?,
				    value_movement_id = ?, cost_layer_id = ?
				where id = ?
				""", valuationState, valuationMethod, unitCost, inventoryAmount, valueMovementId, costLayerId,
				stockMovementId);
		return valueMovementId;
	}

	private ProjectLayer lockProjectLayer(Long layerId) {
		return this.jdbcTemplate.query("""
				select id, project_id, material_id, remaining_quantity, remaining_amount, unit_cost
				from inv_project_cost_layer
				where id = ?
				for update
				""", (rs, rowNum) -> new ProjectLayer(rs.getLong("id"), rs.getLong("project_id"),
				rs.getLong("material_id"), rs.getBigDecimal("remaining_quantity"),
				rs.getBigDecimal("remaining_amount"), rs.getBigDecimal("unit_cost")), layerId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.INVENTORY_PROJECT_COST_LAYER_INSUFFICIENT));
	}

	private MaterialValuation materialValuation(Long materialId) {
		return this.jdbcTemplate.query("""
				select inventory_value_enabled, inventory_valuation_category
				from mst_material
				where id = ?
				""", (rs, rowNum) -> new MaterialValuation(rs.getBoolean("inventory_value_enabled"),
				rs.getString("inventory_valuation_category")), materialId).stream().findFirst()
			.orElse(new MaterialValuation(false, "UNCLASSIFIED"));
	}

	private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record ValuationResult(String valuationState, BigDecimal unitCost, BigDecimal inventoryAmount,
			String valuationMethod, Long costLayerId, Long valueMovementId) {
	}

	private record MaterialValuation(boolean inventoryValueEnabled, String inventoryValuationCategory) {
	}

	private record PublicPool(Long id, BigDecimal quantity, BigDecimal amount, BigDecimal averageUnitCost,
			String valuationState) {
	}

	private record ProjectLayer(Long id, Long projectId, Long materialId, BigDecimal remainingQuantity,
			BigDecimal remainingAmount, BigDecimal unitCost) {
	}

	private record PublicBalance(Long id, BigDecimal quantity) {
	}

	private record PublicAdjustmentBalance(Long warehouseId, Long unitId, String qualityStatus, Long batchId,
			Long serialId, BigDecimal quantity) {
	}

}
